package luna.features;

/**
 * Local-player movement pace in real time, matching ModUpLv: one native velocity step per
 * 28ms, whatever the game-speed tempo. The step itself (base 6 + mount bonus) stays native.
 * Leftover time carries to the next update so bunched tempo updates and slow frames keep the
 * average rate; the gate runs on every local update, moving or not, so standing still builds no
 * backlog. A backlog of STEP_MS * MAX_BACKLOG or more (paused updates, clock change) restarts it.
 */
public final class MovePace {
    public static final int STEP_MS = 28;
    static final int MAX_BACKLOG = 4;
    private static long paceAt;
    private static boolean started;

    private MovePace() { }

    /** Called by the native entity update for the local player; true applies its velocity. */
    public static boolean localStep() {
        return step(System.currentTimeMillis());
    }

    public static boolean step(long now) {
        long behind = now - paceAt;
        if (!started || behind < 0 || behind >= (long)STEP_MS * MAX_BACKLOG) {
            started = true;
            paceAt = now;
            return true;
        }
        if (behind < STEP_MS) return false;
        paceAt += STEP_MS;
        return true;
    }

    public static void resetForTest() {
        started = false;
        paceAt = 0;
    }
}
