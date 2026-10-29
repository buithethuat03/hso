/** Runtime state for the 100 ms paced equipment auto-enhancer. */
public final class KOAutoEnhanceSupport {
    /** Pacing between requests. The loop does not wait for the server, exactly as requested. */
    private static final long STEP_MS = 100L;

    /**
     * Quiet window after the last request before the screen is handed back. Because requests are
     * fired every 100 ms without waiting, several are still in flight when the target level is
     * reached; their late result packets must be swallowed instead of driving the vanilla result
     * animation, which dereferences the selected item and wedges the screen once it is cleared.
     */
    private static final long DRAIN_MS = 1600L;

    /** Give up when no enhancement result packet has arrived for this long. */
    private static final long SILENT_MS = 8000L;

    /** Hard cap on requests per run so a target the server will never grant cannot drain gold. */
    private static final int MAX_REQUESTS = 500;

    private static boolean active;
    private static boolean menuOpen;
    private static boolean waiting;
    private static boolean draining;
    private static boolean resultSeen;
    private static long requestStartedAt;
    private static long runStartedAt;
    private static long lastPacketAt;
    private static int requestsSent;
    private static int target;

    private KOAutoEnhanceSupport() {
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isDraining() {
        return draining;
    }

    /** True while the mod owns the enhancement screen, either enhancing or draining. */
    public static boolean isBusy() {
        return active || draining;
    }

    public static boolean isMenuOpen() {
        return menuOpen;
    }

    public static boolean isWaiting() {
        return waiting;
    }

    public static int getTarget() {
        return target;
    }

    public static int getRequestsSent() {
        return requestsSent;
    }

    public static void openMenu() {
        if (!active) {
            menuOpen = true;
        }
    }

    public static void closeMenu() {
        menuOpen = false;
    }

    public static void start(int requestedTarget) {
        target = requestedTarget;
        active = true;
        menuOpen = false;
        waiting = false;
        draining = false;
        resultSeen = false;
        requestStartedAt = 0L;
        runStartedAt = System.currentTimeMillis();
        lastPacketAt = 0L;
        requestsSent = 0;
    }

    public static void markWaiting() {
        if (active) {
            waiting = true;
            resultSeen = false;
            requestStartedAt = System.currentTimeMillis();
            requestsSent++;
        }
    }

    /** Called from the network thread for every enhancement result packet. */
    public static void markResultReady() {
        lastPacketAt = System.currentTimeMillis();
        if (active && waiting) {
            resultSeen = true;
        }
    }

    /** True once the next request may be sent: the server answered, or 100 ms have elapsed. */
    public static boolean isStepDue() {
        return active && waiting
                && (resultSeen || System.currentTimeMillis() - requestStartedAt >= STEP_MS);
    }

    /**
     * True when the run must be given up: the server stopped answering, or it will not grant the
     * requested level. Without this an unpaired loop would keep firing ten requests per second.
     */
    public static boolean isAborted() {
        if (!active) {
            return false;
        }
        if (requestsSent >= MAX_REQUESTS) {
            return true;
        }
        long since = lastPacketAt == 0L ? runStartedAt : lastPacketAt;
        return System.currentTimeMillis() - since >= SILENT_MS;
    }

    /**
     * Accept one step of the loop.
     *
     * @return true when another gold enhancement request should be sent.
     */
    public static boolean completeResponse(int currentLevel) {
        if (!active || !waiting) {
            return false;
        }
        waiting = false;
        resultSeen = false;
        requestStartedAt = 0L;
        if (currentLevel >= target) {
            beginDrain();
            return false;
        }
        return true;
    }

    /** Stops sending but keeps suppressing the result animation until the queue goes quiet. */
    public static void beginDrain() {
        if (!active && !draining) {
            return;
        }
        active = false;
        menuOpen = false;
        waiting = false;
        resultSeen = false;
        requestStartedAt = 0L;
        target = 0;
        draining = true;
        lastPacketAt = System.currentTimeMillis();
    }

    public static boolean isDrainDone() {
        return draining && System.currentTimeMillis() - lastPacketAt >= DRAIN_MS;
    }

    public static void stop() {
        active = false;
        menuOpen = false;
        waiting = false;
        draining = false;
        resultSeen = false;
        requestStartedAt = 0L;
        runStartedAt = 0L;
        lastPacketAt = 0L;
        requestsSent = 0;
        target = 0;
    }
}
