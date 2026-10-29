package luna.core;

import luna.api.LunaClock;

/** Production clock. Controllers only depend on the LunaClock interface. */
public final class SystemLunaClock implements LunaClock {

    public long nowMillis() {
        return System.currentTimeMillis();
    }
}
