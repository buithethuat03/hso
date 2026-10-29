package luna.features;

/** One-shot state for waiting on a native return scroll during a sale trip. */
public final class SaleReturnScrollController {
    public static final int NONE = 0;
    public static final int WAITING = 1;
    public static final int ARRIVED = 2;
    public static final int FALLBACK = 3;
    public static final int CANCELLED = 4;

    public static final long WAIT_MS = 10000L;

    private static boolean isWaiting;
    private static long requestedAt;
    private static int destination;

    private SaleReturnScrollController() { }

    public static synchronized void begin(long now, int nativeDestination) {
        isWaiting = true;
        requestedAt = now;
        destination = nativeDestination;
    }

    public static synchronized int poll(long now, int currentMap, boolean active,
            int nativeSaleState) {
        if (!isWaiting) return NONE;

        if (!active || nativeSaleState != 1) {
            isWaiting = false;
            return CANCELLED;
        }
        if (isSaleVillage(currentMap)) {
            isWaiting = false;
            return ARRIVED;
        }
        if (now < requestedAt || now - requestedAt >= WAIT_MS) {
            isWaiting = false;
            return FALLBACK;
        }
        return WAITING;
    }

    public static synchronized void cancel() {
        isWaiting = false;
    }

    public static synchronized boolean waiting() {
        return isWaiting;
    }

    public static synchronized int fallbackDestination() {
        return destination;
    }

    public static boolean isSaleVillage(int map) {
        return map == 1 || map == 33 || map == 70;
    }
}
