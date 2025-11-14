package com.sap.sailing.domain.maneuverhash;


import org.json.simple.JSONObject;

import com.sap.sailing.domain.tracking.TrackedRace;

public interface ManeuverRaceFingerprint {
    
    /**
     * Returns a {@link JSONObject} of the hash values.
     */
    JSONObject toJson();

    /**
     * Incrementally computes the composite fingerprint of the {@code trackedRace} and compares to this fingerprint
     * component by component. The fingerprint components are computed in ascending order of computational complexity,
     * trying to fail early / fast.<p>
     * 
     * @return {@code true} if the {@code trackedRace} produces a fingerprint equal to this one if passed to
     *         {@link ManeuverRaceFingerprintFactory#createFingerprint(TrackedRace)}
     */
    boolean matches(TrackedRace trackedRace);
}
