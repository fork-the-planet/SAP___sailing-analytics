package com.sap.sailing.domain.maneuverhash;

import com.sap.sailing.domain.tracking.TrackedRace;
import com.sap.sailing.domain.maneuverhash.impl.ManeuverRaceFingerprintFactoryImpl;
import org.json.simple.JSONObject;

public interface ManeuverRaceFingerprintFactory {
    ManeuverRaceFingerprintFactory INSTANCE = new ManeuverRaceFingerprintFactoryImpl();
    
    ManeuverRaceFingerprint createFingerprint (TrackedRace TrackedRace);
    
    ManeuverRaceFingerprint fromJson (JSONObject json);
}
