/** Runtime state for the server-response gated equipment auto-enhancer. */
public final class HSOSKYAutoEnhanceSupport {
    /**
     * Grace window after the server result packet before the inventory copy of the selected item
     * is trusted. The item update packet may land slightly after the result packet, so a stale read
     * would otherwise fire one extra request.
     */
    private static final long SETTLE_MS = 250L;

    /** Abort window when the server never answers an outstanding request. */
    private static final long STALL_MS = 6000L;

    private static boolean active;
    private static boolean menuOpen;
    private static boolean waiting;
    private static boolean resultReady;
    private static long requestStartedAt;
    private static long resultReadyAt;
    private static int target;

    private HSOSKYAutoEnhanceSupport() {
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isMenuOpen() {
        return menuOpen;
    }

    public static boolean isWaiting() {
        return waiting;
    }

    public static boolean isResultReady() {
        return resultReady;
    }

    public static int getTarget() {
        return target;
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
        resultReady = false;
        requestStartedAt = 0L;
        resultReadyAt = 0L;
    }

    public static void markWaiting() {
        if (active) {
            waiting = true;
            resultReady = false;
            requestStartedAt = System.currentTimeMillis();
            resultReadyAt = 0L;
        }
    }

    /** Called from the network thread when the server answers the outstanding request. */
    public static void markResultReady() {
        if (active && waiting && !resultReady) {
            resultReady = true;
            resultReadyAt = System.currentTimeMillis();
        }
    }

    /** True once the inventory is expected to carry the result of the answered request. */
    public static boolean isResultSettled() {
        return active && resultReady && System.currentTimeMillis() - resultReadyAt >= SETTLE_MS;
    }

    /** True when the server never answered; the caller must stop instead of sending again. */
    public static boolean isRequestStalled() {
        return active && waiting && !resultReady
                && System.currentTimeMillis() - requestStartedAt >= STALL_MS;
    }

    /**
     * Accept exactly one server response for the outstanding request.
     *
     * @return true when another gold enhancement request should be sent.
     */
    public static boolean completeResponse(int currentLevel) {
        if (!active || !waiting) {
            return false;
        }
        waiting = false;
        resultReady = false;
        requestStartedAt = 0L;
        resultReadyAt = 0L;
        if (currentLevel >= target) {
            stop();
            return false;
        }
        return true;
    }

    public static void stop() {
        active = false;
        menuOpen = false;
        waiting = false;
        resultReady = false;
        requestStartedAt = 0L;
        resultReadyAt = 0L;
        target = 0;
    }
}
