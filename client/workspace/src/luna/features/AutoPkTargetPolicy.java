package luna.features;

/** Bounded, allocation-free ranking of eligible PK candidates in world pixels. */
public final class AutoPkTargetPolicy {
    public static final int RADIUS = 365;
    public static final int LIMIT = 20;
    private final Object[] targets = new Object[LIMIT];
    private final int[] ids = new int[LIMIT];
    private final long[] distances = new long[LIMIT];
    private final int[] hps = new int[LIMIT];
    private final boolean[] priority = new boolean[LIMIT];
    private int count;

    /** Returns -1 outside the circle. Subtract in long before squaring. */
    public static long distanceSquared(int originX, int originY, int x, int y) {
        long dx = (long)x - originX, dy = (long)y - originY;
        if (dx < -RADIUS || dx > RADIUS || dy < -RADIUS || dy > RADIUS) return -1L;
        long value = dx * dx + dy * dy;
        return value <= (long)RADIUS * RADIUS ? value : -1L;
    }

    public void clear() {
        for (int i = 0; i < count; i++) targets[i] = null;
        count = 0;
    }

    /**
     * Eligibility is supplied by the native PK/war filters before offering a candidate.
     * Order: priority group first, then lower hp, then nearer. Callers pass the same hp
     * (0) for every candidate to rank by distance only.
     */
    public void offer(Object target, int id, long distance, boolean level40Priority, int hp) {
        if (target == null || distance < 0L || distance > (long)RADIUS * RADIUS) return;
        for (int i = 0; i < count; i++) if (ids[i] == id) return;
        int at = 0;
        while (at < count) {
            if (level40Priority != priority[at]) {
                if (level40Priority) break;
            } else if (hp != hps[at]) {
                if (hp < hps[at]) break;
            } else if (distance < distances[at]) break;
            at++;
        }
        if (at >= LIMIT) return;
        int last = count < LIMIT ? count++ : LIMIT - 1;
        for (int i = last; i > at; i--) {
            targets[i] = targets[i - 1]; ids[i] = ids[i - 1];
            distances[i] = distances[i - 1]; hps[i] = hps[i - 1]; priority[i] = priority[i - 1];
        }
        targets[at] = target; ids[at] = id;
        distances[at] = distance; hps[at] = hp; priority[at] = level40Priority;
    }

    public int size() { return count; }
    public Object at(int index) { return targets[index]; }

    /** Smallest distance among the kept targets; with hp ordering the first one need not be the nearest. */
    public long nearestSquared() {
        long nearest = -1L;
        for (int i = 0; i < count; i++) if (nearest < 0L || distances[i] < nearest) nearest = distances[i];
        return nearest;
    }
}
