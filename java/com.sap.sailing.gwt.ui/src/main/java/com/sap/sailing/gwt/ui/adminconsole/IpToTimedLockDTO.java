package com.sap.sailing.gwt.ui.adminconsole;

import com.sap.sse.common.TimedLock;
import com.sap.sse.common.Named;

public class IpToTimedLockDTO implements Named {
    public final String ip;
    public final TimedLock timedLock;

    public IpToTimedLockDTO(final String ip, final TimedLock timedLock) {
        this.ip = ip;
        this.timedLock = timedLock;
    }

    @Override
    public String getName() {
        return "IpToTimedLockDTO";
    }

}
