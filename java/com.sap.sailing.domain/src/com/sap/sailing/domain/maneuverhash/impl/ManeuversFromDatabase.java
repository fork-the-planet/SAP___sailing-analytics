package com.sap.sailing.domain.maneuverhash.impl;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sap.sailing.domain.base.Competitor;
import com.sap.sailing.domain.maneuverhash.ManeuverCache;
import com.sap.sailing.domain.tracking.Maneuver;
import com.sap.sailing.domain.tracking.TrackedRace;
import com.sap.sse.util.SmartFutureCache.EmptyUpdateInterval;

/**
 * Stores a {@link TrackedRace}'s {@link Maneuver}s after they were loaded successfully from the persistent store.
 * This happens when a race was considered equal regarding its "fingerprint" to a version loaded previously with
 * all maneuvers computed and stored persistently. This saves the computational effort to compute the maneuvers
 * again (persistent caching).<p>
 * 
 * This class collaborates with {@link ManeuverCacheDelegate}.
 */
public class ManeuversFromDatabase implements ManeuverCache<Competitor, List<Maneuver>, EmptyUpdateInterval> {
    boolean suspended;
    private static final Logger logger = Logger.getLogger(ManeuversFromDatabase.class.getName());
    private final Map<Competitor, List<Maneuver>> maneuvers;

    public ManeuversFromDatabase(
             Map<Competitor, List<Maneuver>> maneuvers) {
        super();
        this.maneuvers = maneuvers;
    }

    public void resume() {
        logger.log(Level.WARNING, "Method should never be called");
    }

    public void suspend() {
        synchronized (this) {
            logger.finest("Suspended ManeuverFromDatabase");
            suspended = true;
        }    
    }

    public List<Maneuver> get(Competitor competitor, boolean waitForLatest) {
        return maneuvers.get(competitor);
    }

    @Override
    public void triggerUpdate(Competitor key, EmptyUpdateInterval updateInterval) {
      logger.log(Level.WARNING, "If Fingerprint matches, no Update should be triggered");
      // TODO change to smartFutureCache in Delegate
    }
}