package com.sap.sse.landscape.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.sap.sse.common.Duration;
import com.sap.sse.common.TimePoint;
import com.sap.sse.common.Util.Pair;
import com.sap.sse.landscape.Release;
import com.sap.sse.landscape.ReleaseRepository;
import com.sap.sse.util.HttpUrlConnectionHelper;

/**
 * Can enumerate the {@link Release}s published by a GitHub repository and search releases whose name starts with a
 * specific prefix. This assumes a public GitHub repository where releases can be freely downloaded from
 * <code>https://github.com/{owner}/{repo}/releases/download/{release-name}</code>. The {@code api.github.com}'s
 * {@code /releases} end point delivers the releases in descending chronological order, so newest releases first. With
 * this, we can cache old results and try to get along with the harsh rate limit of only 60 requests per hour when used
 * without authentication.
 * <p>
 * 
 * Due to the harsh rate limits we restrict loading even of the first page to once every two minutes; multiple requests
 * within this duration will be answered from the cache. With this, a single instance of this class will typically
 * request <em>all</em> releases only once, cache all these releases, and then look for newer releases at most every two
 * minutes, thereby staying well within limits.
 * <p>
 * 
 * Enumerating the releases works through the inner class {@link ReleaseIterator}. If the last loading request for the
 * first page happened more than those two minutes ago, another such request will be made and the new, yet uncached
 * releases obtained from it will be added to the cache. Then, enumeration starts on the cache, delivering the newest
 * release first. When the oldest cached element has been delivered through the iterator, the next action depends on
 * whether or not the cache {@link #cacheContainsOldestRelease contains the oldest release} already. If so, no older
 * release can exist, and iteration ends. Otherwise, more requests for further paginated release documents are sent
 * until no more pages are found or releases older than the so far oldest release from the cache are found and added to
 * the cache. Iteration then continues on the cache again.
 * <p>
 * 
 * The class is thread-safe in that it allows multiple threads to obtain iterators on a single instance of this class.
 * The loading and caching of releases pages from GitHub, the invocation of the {@link #iterator()} method and the
 * {@link ReleaseIterator#hasNext()} and {@link ReleaseIterator#next()} methods all obtain this object's monitor
 * ({@code synchronized}). This may cause one iterator having to wait for another iterator's implicit loading actions.
 * <p>
 * 
 * @author Axel Uhl (d043530)
 */
public class GithubReleasesRepository extends AbstractReleaseRepository implements ReleaseRepository {
    private final static Logger logger = Logger.getLogger(GithubReleasesRepository.class.getName());
    private static final SimpleDateFormat isoDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
    private final static String GITHUB_API_BASE_URL = "https://api.github.com";
    private final static String GITHUB_BASE_URL = "https://github.com";
    private final String owner;
    private final String repositoryName;
    
    /**
     * The cache of releases as loaded from the GitHub web site. The cache is filled when iterating using a
     * {@link ReleaseIterator}, by loading paginated release records, converting them to {@link GithubRelease} objects
     * and storing them in this cache.
     * <p>
     * 
     * The cache does not guarantee to contain the newest releases, nor does it guarantee to go back all the way to the
     * oldest release. Its contents are contiguous in the sense of how the releases are returned by the GitHub API in
     * descending order of publication, from new to old. In other words, if there is a release cached that was published
     * at time point {@code t1} and another at a later time point {@code t2}, then the cache is guaranteed to contain
     * all releases published in the time range {@code [t1:t2]} (inclusive).
     * <p>
     * 
     * Should a {@link ReleaseIterator} have enumerated all releases back to the oldest one, the
     * {@link #cacheContainsOldestRelease} flag will be set to {@code true} which means that when an iteration has
     * reached the oldest release in the cache, iteration is complete, and no further page loading is necessary
     * to complete the iteration.
     */
    private final ConcurrentNavigableMap<TimePoint, Release> releasesByPublishingTimePoint;
    
    private boolean cacheContainsOldestRelease;
    
    private TimePoint lastFetchOfNewestReleases;
    
    private final static Duration RELOAD_NEWEST_RELEASES_AFTER_DURATION = Duration.ONE_MINUTE.times(2);
    
    /**
     * If {@link GithubReleasesRepository#lastFetchOfNewestReleases} is {@code null} or older than the
     * {@link GithubReleasesRepository#RELOAD_NEWEST_RELEASES_AFTER_DURATION}, the page with newest releases is actually
     * loaded. Otherwise, we assume that within the
     * {@link GithubReleasesRepository#RELOAD_NEWEST_RELEASES_AFTER_DURATION} interval changes are sufficiently
     * unlikely, so we will set the {@link #cachedReleasesIterator} to directly serve the current contents of the cache.
     * <p>
     * 
     * Always fetches the first page from the {@code /releases} end point and starts constructing and
     * {@link GithubReleasesRepository#releasesByPublishingTimePoint caching} releases, until a publishing time point
     * overlap with {@link GithubReleasesRepository#releasesByPublishingTimePoint} is found. Iteration then starts from
     * that cache. If the iterator has returned all elements from the cache going backwards in publishing history, and
     * {@link GithubReleasesRepository#cacheContainsOldestRelease} is {@code false}, indicating that the cache does not
     * go back to the "beginning of time," and still more elements are requested from this iterator, paginated release
     * documents need to get loaded again until we find even older releases than the oldest one from the cache. The
     * loaded elements will be added to the cache, and a new internal iterator is launched on the cache starting from
     * the then loaded element.
     * <p>
     * 
     * All releases found by loading a page are added to the
     * {@link GithubReleasesRepository#releasesByPublishingTimePoint} cache. If the page with the oldest sequence of
     * releases has been loaded (there is no next page then anymore), the
     * {@link GithubReleasesRepository#cacheContainsOldestRelease} flag is set to {@code true}.
     * 
     * @author Axel Uhl (d043530)
     *
     */
    private class ReleaseIterator implements Iterator<Release> {
        /**
         * Initialized to the URL for loading the first page of releases; each call to
         * {@link #loadNextPage(TimePoint)} changes this to the next page, or {@code null}
         * if the last page was loaded.
         */
        private String nextPageURL;

        /**
         * Takes precedence if not {@code null} and still having elements; enumerates the cached releases, starting from
         * the newest (last in the cache) to the oldest (first in the cache). When fully consumed, page loading has to
         * continue until releases published earlier than the oldest one from the
         * {@link GithubReleasesRepository#releasesByPublishingTimePoint cache} are found.
         */
        private Iterator<Release> cachedReleasesIterator;
        
        private ReleaseIterator() throws MalformedURLException, IOException, ParseException {
            synchronized (GithubReleasesRepository.this) {
                nextPageURL = getReleasesURL();
                final TimePoint now = TimePoint.now();
                if (lastFetchOfNewestReleases != null && lastFetchOfNewestReleases.until(now)
                        .compareTo(RELOAD_NEWEST_RELEASES_AFTER_DURATION) < 0) {
                    logger.fine(()->"No need to fetch page with newest releases; did that at "+lastFetchOfNewestReleases);
                    cachedReleasesIterator = releasesByPublishingTimePoint.descendingMap().values().iterator();
                } else {
                    logger.fine(()->"Need to fetch page with newest releases because last request was at "+
                            (lastFetchOfNewestReleases==null?"<never>":lastFetchOfNewestReleases));
                    cachedReleasesIterator = null;
                    while (nextPageURL != null && cachedReleasesIterator == null) {
                        lastFetchOfNewestReleases = now;
                        loadNextPage(/* olderThan */ null);
                    }
                }
            }
        }
        
        /**
         * Loads the page of releases referenced by {@link #nextPageURL}.
         * <p>
         * 
         * If {@code olderThan} is {@code null}, only the releases newer than the newest entry in the cache are loaded
         * into the cache, and {@link #cachedReleasesIterator} is set to the newest element in the cache if and only if
         * the cache was empty when this method was called, or the page contained a release not newer than the newest
         * release in the cache. This also means that if with {@code olderThan==null} the
         * {@link #cachedReleasesIterator} is {@code null} after this method returns, one or more calls will be required
         * to create an "overlap" with the cache before starting the iteration. This is required because we guarantee
         * the cache to be "contiguous" in terms of the releases that exist.
         * <p>
         * 
         * If {@code olderThan} is not {@code null}, only releases published before {@code olderThan} are added to the
         * cache, and {@link #cachedReleasesIterator} is set to the newest element added to the cache, or set to
         * {@code null} if no release was added to the cache by this call.
         * <p>
         * Precondition: {@link #nextPageURL} is not {@code null}; and the calling thread owns the object monitor of
         * the enclosing {@link GithubReleasesRepository} instance.
         * <p>
         * Postcondition: {@link GithubReleasesRepository#cacheContainsOldestRelease} is {@code true} if and only if
         * this invocation has loaded the last page of releases that exist
         * 
         * @param olderThan
         *            if {@code null}, releases newer than the newest release from the cache will be added to the cache,
         *            and the {@link #cachedReleasesIterator} will be set to the then newest cache element; if not
         *            {@code null}, only releases published before {@code olderThan} will be loaded, and
         *            {@link #cachedReleasesIterator} is then set to the newest of the older releases loaded, if any, or
         *            to {@code null} if no releases older than {@code olderThan} were found during this invocation.
         */
        private void loadNextPage(TimePoint olderThan) throws MalformedURLException, IOException, ParseException {
            assert Thread.holdsLock(GithubReleasesRepository.this);
            cachedReleasesIterator = null;
            logger.info("Requesting releases page "+nextPageURL+(olderThan==null?"":(" looking for releases older than "+olderThan)));
            final URLConnection connection = HttpUrlConnectionHelper.redirectConnection(new URL(nextPageURL));
            final InputStream index = (InputStream) connection.getContent();
            final String xRatelimitRemaining = connection.getHeaderField("x-ratelimit-remaining");
            if (xRatelimitRemaining != null && Integer.valueOf(xRatelimitRemaining) <= 0) {
                throw new RuntimeException("You hit the rate limit of "+connection.getHeaderField("x-ratelimit-limit"));
            }
            final String linkHeader = connection.getHeaderField("link");
            nextPageURL = getNextPageURL(linkHeader);
            logger.fine(()->nextPageURL==null?"This was the last page":("Next page will be "+nextPageURL));
            cacheContainsOldestRelease = cacheContainsOldestRelease || nextPageURL == null; // in this case we have seen and cached the last (oldest) page of releases
            final JSONArray releasesJson = (JSONArray) new JSONParser().parse(new InputStreamReader(index));
            boolean addedAtLeastOneReleaseToCache = false;
            final boolean cacheWasEmpty = releasesByPublishingTimePoint.isEmpty();
            for (final Object releaseObject : releasesJson) {
                final Pair<TimePoint, GithubRelease> publishedAtAndRelease = getPublishedAtAndReleaseFromJson((JSONObject) releaseObject);
                if (olderThan == null) { // looking for releases published after the newest cache entry
                    if (cacheWasEmpty || publishedAtAndRelease.getA().after(releasesByPublishingTimePoint.lastKey())) {
                        addedAtLeastOneReleaseToCache = true;
                        releasesByPublishingTimePoint.put(publishedAtAndRelease.getA(), publishedAtAndRelease.getB());
                    } else {
                        cachedReleasesIterator = releasesByPublishingTimePoint.descendingMap().values().iterator();
                    }
                } else { // looking for releases published before olderThan
                    if (publishedAtAndRelease.getA().before(olderThan)) {
                        addedAtLeastOneReleaseToCache = true;
                        releasesByPublishingTimePoint.put(publishedAtAndRelease.getA(), publishedAtAndRelease.getB());
                    }
                }
            }
            if (olderThan == null) {
                if (cacheWasEmpty) {
                    cachedReleasesIterator = releasesByPublishingTimePoint.descendingMap().values().iterator();
                }
            } else {
                if (addedAtLeastOneReleaseToCache) {
                    cachedReleasesIterator = releasesByPublishingTimePoint.descendingMap().tailMap(olderThan, /* inclusive */ false).values().iterator();
                }
            }
        }

        @Override
        public boolean hasNext() {
            synchronized (GithubReleasesRepository.this) {
                // - we're delivering from the cache and the cache has more elements, or
                // - we've reached the end of the cache but the cache doesn't contain the oldest release and we can load more pages
                return cachedReleasesIterator != null && cachedReleasesIterator.hasNext()
                    || !cacheContainsOldestRelease && nextPageURL != null;
            }
        }

        @Override
        public Release next() {
            synchronized (GithubReleasesRepository.this) {
                final Release result;
                if (cachedReleasesIterator != null && cachedReleasesIterator.hasNext()) {
                    result = getNextElementFromCacheIterator();
                } else {
                    if (cacheContainsOldestRelease) {
                        throw new NoSuchElementException();
                    } else {
                        while (nextPageURL != null && cachedReleasesIterator == null) {
                            try {
                                loadNextPage(/* olderThan */ releasesByPublishingTimePoint.firstKey());
                            } catch (IOException | ParseException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        if (cachedReleasesIterator == null || !cachedReleasesIterator.hasNext()) {
                            throw new NoSuchElementException();
                        } else {
                            result = getNextElementFromCacheIterator();
                        }
                    }
                }
                return result;
            }
        }

        private Release getNextElementFromCacheIterator() {
            final Release result;
            result = cachedReleasesIterator.next();
            if (!cachedReleasesIterator.hasNext()) {
                cachedReleasesIterator = null;
            }
            return result;
        }
    }
    
    public GithubReleasesRepository(String owner, String repositoryName, String defaultReleaseNamePrefix) {
        super(defaultReleaseNamePrefix);
        this.owner = owner;
        this.repositoryName = repositoryName;
        this.releasesByPublishingTimePoint = new ConcurrentSkipListMap<>();
        this.cacheContainsOldestRelease = false;
        this.lastFetchOfNewestReleases = null;
    }
    
    @Override
    public Release getLatestRelease(String releaseNamePrefix) {
        Release result = null;
        for (final Release release : this) { // invokes the iterator() method
            if (release.getBaseName().equals(releaseNamePrefix)) {
                result = release;
                break; // here we assume that releases are enumerated from newest to oldest
            }
        }
        return result;
    }

    private String getRepositoryPath() {
        return owner+"/"+repositoryName;
    }
    
    private String getReleasesURL() {
        return GITHUB_API_BASE_URL+"/repos/"+getRepositoryPath()+"/releases?per_page=100";
    }

    @Override
    public Release getRelease(String releaseName) {
        return new GithubRelease(releaseName, GITHUB_BASE_URL+"/"+getRepositoryPath()+"/releases/download/"+releaseName+"/"+releaseName+Release.ARCHIVE_EXTENSION,
                GITHUB_BASE_URL+"/"+getRepositoryPath()+"/releases/download/"+releaseName+"/"+Release.RELEASE_NOTES_FILE_NAME);
    }
    
    @Override
    public Iterator<Release> iterator() {
        try {
            return new ReleaseIterator();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Pair<TimePoint, GithubRelease> getPublishedAtAndReleaseFromJson(JSONObject releaseJson) {
        final String name = releaseJson.get("name").toString();
        final String publishedAtISO = releaseJson.get("published_at").toString();
        TimePoint publishedAt;
        try {
            publishedAt = TimePoint.of(isoDateTimeFormat.parse(publishedAtISO));
        } catch (java.text.ParseException e) {
            logger.warning("Couldn't read published_at time stamp for release "+name+": "+publishedAtISO);
            throw new RuntimeException(e);
        }
        String archiveDownloadURL = null;
        String releaseNotesURL = null;
        for (final Object archiveAsset : (JSONArray) releaseJson.get("assets")) {
            final JSONObject archiveAssetJson = (JSONObject) archiveAsset;
            if (archiveAssetJson.get("content_type").equals("application/x-tar")) {
                archiveDownloadURL = archiveAssetJson.get("browser_download_url").toString();
            } else if (archiveAssetJson.get("name").equals(Release.RELEASE_NOTES_FILE_NAME)) {
                releaseNotesURL = archiveAssetJson.get("browser_download_url").toString();
            }
        }
        final GithubRelease release = new GithubRelease(name, archiveDownloadURL, releaseNotesURL);
        return new Pair<>(publishedAt, release);
    }

    private static final Pattern nextPagePattern = Pattern.compile(".*<([^<]*)>; rel=\"next\".*");
    String getNextPageURL(String linkHeader) {
        final String result;
        final Matcher m = nextPagePattern.matcher(linkHeader);
        if (m.matches()) {
            result = m.group(1);
        } else {
            result = null;
        }
        return result;
    }
}
