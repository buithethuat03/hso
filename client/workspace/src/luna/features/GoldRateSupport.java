package luna.features;

/** Session wallet balance, received-gold total, and rolling gold/hour estimate. */
public final class GoldRateSupport {
    private static final long WARMUP_MS = 300000L;
    private static final long MAX_RATE_WINDOW_MS = 900000L;
    private static final long RATE_BUCKET_MS = 1000L;
    private static final int RATE_BUCKETS = 900;
    private static final long MILLIS_PER_HOUR = 3600000L;

    private static boolean active;
    private static String activeServer;
    private static int activeId;
    private static boolean hasWallet;
    private static long currentGold = -1L;
    private static long receivedGold;

    private static boolean hasClock;
    private static long lastClockMillis;
    private static boolean hasGain;
    private static long firstGainMillis;
    private static long windowGold;
    private static final long[] bucketGold = new long[RATE_BUCKETS];
    private static boolean hasBucketClock;
    private static long lastBucketSecond;
    private static int currentBucket;

    private GoldRateSupport() { }

    /** Starts a fresh measurement only when the active character identity changes. */
    public static synchronized void snapshot(String server, int id, long nowMillis) {
        if (!active || activeId != id || !sameServer(activeServer, server)) {
            active = true;
            activeServer = server;
            activeId = id;
            hasWallet = false;
            currentGold = -1L;
            receivedGold = 0L;
            clearRateWindow();
            hasClock = false;
        }
        observeClock(nowMillis);
    }

    /** Applies one observed wallet balance; the first observation is only a baseline. */
    public static synchronized void balance(String server, int id, long gold,
            long nowMillis) {
        if (!active || activeId != id || !sameServer(activeServer, server)) return;
        observeClock(nowMillis);

        if (!hasWallet) {
            hasWallet = true;
            currentGold = gold;
            return;
        }

        if (gold > currentGold) {
            long delta = positiveDifference(gold, currentGold);
            if (delta > Long.MAX_VALUE - windowGold) {
                clearRateWindow();
                advanceWindow(nowMillis / RATE_BUCKET_MS);
            }
            if (delta > Long.MAX_VALUE - receivedGold) {
                receivedGold = Long.MAX_VALUE;
            } else {
                receivedGold += delta;
            }
            if (!hasGain) {
                hasGain = true;
                firstGainMillis = nowMillis;
            }
            windowGold += delta;
            bucketGold[currentBucket] += delta;
        }
        currentGold = gold;
    }

    /** Ends the login session and clears its wallet baseline and measurements. */
    public static synchronized void disconnect() {
        active = false;
        activeServer = null;
        activeId = 0;
        hasWallet = false;
        currentGold = -1L;
        receivedGold = 0L;
        hasClock = false;
        lastClockMillis = 0L;
        clearRateWindow();
    }

    /** Returns the last observed wallet balance, or -1 until a wallet is known. */
    public static synchronized long current() {
        return hasWallet ? currentGold : -1L;
    }

    /** Returns positive gold received since login, or -1 until the wallet baseline exists. */
    public static synchronized long received() {
        return hasWallet ? receivedGold : -1L;
    }

    /** Returns the rolling gold/hour estimate, or -1 while its warmup is incomplete. */
    public static synchronized long rate(long nowMillis) {
        if (!active || !hasWallet) return -1L;
        observeClock(nowMillis);
        if (!hasGain) return -1L;

        long elapsed = nowMillis - firstGainMillis;
        if (elapsed < 0L) {
            clearRateWindow();
            advanceWindow(nowMillis / RATE_BUCKET_MS);
            return -1L;
        }
        if (elapsed < WARMUP_MS) return -1L;
        long denominator = elapsed < MAX_RATE_WINDOW_MS
                ? elapsed : MAX_RATE_WINDOW_MS;
        return roundedRate(windowGold, denominator);
    }

    /** Formats the current wallet, estimated hourly rate, and login total. */
    public static synchronized String info(long nowMillis) {
        long hourly = rate(nowMillis);
        StringBuffer result = new StringBuffer();
        result.append("Vàng hiện tại:\n")
                .append(ExpRateSupport.formatLong(current()))
                .append("\nVàng/giờ (ước tính):\n")
                .append(ExpRateSupport.formatLong(hourly))
                .append("\nVàng đã nhận từ khi đăng nhập:\n")
                .append(ExpRateSupport.formatLong(received()));
        return result.toString();
    }

    private static boolean sameServer(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static long positiveDifference(long greater, long lesser) {
        if (lesser < 0L && greater > Long.MAX_VALUE + lesser) {
            return Long.MAX_VALUE;
        }
        return greater - lesser;
    }

    private static long roundedRate(long earned, long elapsed) {
        long whole = earned / elapsed;
        long remainder = earned % elapsed;
        if (whole > Long.MAX_VALUE / MILLIS_PER_HOUR) return Long.MAX_VALUE;

        long result = whole * MILLIS_PER_HOUR;
        long fractionNumerator = remainder * MILLIS_PER_HOUR;
        long fraction = fractionNumerator / elapsed;
        long fractionRemainder = fractionNumerator % elapsed;
        if (fractionRemainder >= elapsed - fractionRemainder) fraction++;
        return result > Long.MAX_VALUE - fraction ? Long.MAX_VALUE : result + fraction;
    }

    private static void observeClock(long nowMillis) {
        if (hasClock && nowMillis < lastClockMillis) {
            // Keep the login total and wallet baseline; restart only the timed estimate.
            clearRateWindow();
        }
        hasClock = true;
        lastClockMillis = nowMillis;
        advanceWindow(nowMillis / RATE_BUCKET_MS);
    }

    private static void advanceWindow(long second) {
        if (!hasBucketClock) {
            hasBucketClock = true;
            lastBucketSecond = second;
            currentBucket = 0;
            return;
        }
        long elapsed = second - lastBucketSecond;
        if (elapsed <= 0L) return;
        if (elapsed >= RATE_BUCKETS) {
            for (int i = 0; i < RATE_BUCKETS; i++) bucketGold[i] = 0L;
            windowGold = 0L;
            currentBucket = 0;
        } else {
            for (int i = 0; i < (int)elapsed; i++) {
                if (++currentBucket == RATE_BUCKETS) currentBucket = 0;
                windowGold -= bucketGold[currentBucket];
                bucketGold[currentBucket] = 0L;
            }
        }
        lastBucketSecond = second;
    }

    private static void clearRateWindow() {
        hasGain = false;
        firstGainMillis = 0L;
        windowGold = 0L;
        for (int i = 0; i < RATE_BUCKETS; i++) bucketGold[i] = 0L;
        hasBucketClock = false;
        lastBucketSecond = 0L;
        currentBucket = 0;
    }
}
