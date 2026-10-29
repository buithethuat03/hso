package luna.core;

import luna.api.LunaClock;

/** Deterministic clock for host-side controller tests. */
public final class FakeLunaClock implements LunaClock {

    private long current;

    public FakeLunaClock() {
        this(0L);
    }

    public FakeLunaClock(long initialMillis) {
        current = initialMillis;
    }

    public long nowMillis() {
        return current;
    }

    public void setMillis(long value) {
        current = value;
    }

    public void advance(long deltaMillis) {
        if (deltaMillis < 0L) throw new IllegalArgumentException("clock cannot move backwards");
        current += deltaMillis;
    }
}
