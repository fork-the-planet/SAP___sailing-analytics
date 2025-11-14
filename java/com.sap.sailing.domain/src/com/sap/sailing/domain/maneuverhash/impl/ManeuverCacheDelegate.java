package com.sap.sailing.domain.maneuverhash.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.logging.Logger;

import com.sap.sailing.domain.base.Competitor;
import com.sap.sailing.domain.maneuverhash.ManeuverCache;
import com.sap.sailing.domain.maneuverhash.ManeuverRaceFingerprint;
import com.sap.sailing.domain.maneuverhash.ManeuverRaceFingerprintFactory;
import com.sap.sailing.domain.maneuverhash.ManeuverRaceFingerprintRegistry;
import com.sap.sailing.domain.tracking.Maneuver;
import com.sap.sailing.domain.tracking.impl.DynamicTrackedRaceImpl;
import com.sap.sailing.domain.tracking.impl.TrackedRaceImpl;
import com.sap.sse.util.SmartFutureCache.EmptyUpdateInterval;

public class ManeuverCacheDelegate implements ManeuverCache<Competitor, List<Maneuver>, EmptyUpdateInterval> {

    private final TrackedRaceImpl race;
    private static final Logger logger = Logger.getLogger(ManeuverCacheDelegate.class.getName());
    private final ManeuverRaceFingerprintRegistry maneuverRaceFingerprintRegistry;
    Map<Competitor, List<Maneuver>> maneuvers = new HashMap<>();
    private ManeuverCache<Competitor, List<Maneuver>, EmptyUpdateInterval> cacheToUse;
    
    public ManeuverCacheDelegate(TrackedRaceImpl race,
            ManeuverRaceFingerprintRegistry maneuverRaceFingerprintRegistry) {
        super();
        this.race = race;
        this.maneuverRaceFingerprintRegistry = maneuverRaceFingerprintRegistry;
        this.cacheToUse = new ManeuverFromSmartFutureCache((DynamicTrackedRaceImpl) race); 
    }    
    
    @Override
    public void resume() {
        ManeuverRaceFingerprint fingerprint;
        race.getRace().getCourse().lockForRead(); 
        try {
            synchronized (this) {
                if (maneuverRaceFingerprintRegistry != null) {
                    logger.info("Compare maneuverfingerprints");
                    race.waitForAllRaceLogsAttached();
                    fingerprint = maneuverRaceFingerprintRegistry.getManeuverRaceFingerprint(race.getRaceIdentifier());
                } else {
                    fingerprint = null;
                }
                if (fingerprint != null && fingerprint.matches(race)) {
                    logger.info("maneuverfingerprints match");
                    maneuvers = maneuverRaceFingerprintRegistry.loadManeuvers(race, race.getRace().getCourse());
                    cacheToUse = new ManeuverFromDatabase( maneuvers);
                } else {
                    new Thread(()->{
                        logger.info("maneuverfingerprints do not match");
                        cacheToUse.resume();
                        for(Competitor competitor : race.getRace().getCompetitors()) {
                            
                            maneuvers.put(competitor, (List<Maneuver>) cacheToUse.get(competitor, true));
                        }
                        maneuverRaceFingerprintRegistry.storeManeuvers(race.getRaceIdentifier(), ManeuverRaceFingerprintFactory.INSTANCE.createFingerprint(race), maneuvers, race.getRace().getCourse());
                    }, "Waiting for mark passings for "+race.getName()+" after having resumed to store the results in registry")
                    .start();
                }
            }
        } finally {
            race.getRace().getCourse().unlockAfterRead();
        }
    }

    @Override
    public List<Maneuver> get(Competitor competitor, boolean waitForLatest) {
        race.getRace().getCourse().lockForRead(); 
        try {
            synchronized (this) {
          
                   return (List<Maneuver>) cacheToUse.get(competitor, waitForLatest);
                }
   
        } finally {
            race.getRace().getCourse().unlockAfterRead();
        }
    }

    @Override
    public void suspend() {
        race.getRace().getCourse().lockForRead(); 
        try {
            synchronized (this) {
              
                    cacheToUse.suspend();
            }
        } finally {
            race.getRace().getCourse().unlockAfterRead();
        }
    }

    @Override
    public void triggerUpdate(Competitor competitor, EmptyUpdateInterval updateInterval) {
        race.getRace().getCourse().lockForRead(); 
        try {
            synchronized (this) {
              
                    cacheToUse.triggerUpdate(competitor, updateInterval);
            }
        } finally {
            race.getRace().getCourse().unlockAfterRead();
        }
    }
}