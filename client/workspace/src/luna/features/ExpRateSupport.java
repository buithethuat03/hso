package luna.features;

/**
 * Fifteen-minute rolling EXP rate and level ETA helpers. The caller resets this state
 * at login, identity changes, and disconnect; estimator segment resets do not
 * affect it.
 */
public final class ExpRateSupport {
    private static final long MIN_RATE_WINDOW_MS = 300000L;
    private static final int RATE_BUCKETS = 900;
    private static final long RATE_BUCKET_MS = 1000L;
    private static final int MILLIS_PER_HOUR = 3600000;
    private static final String CALCULATING = "Đang tính toán...";

    private static boolean hasClock;
    private static long lastClockMillis;
    private static boolean hasGain;
    private static long firstGainMillis;
    private static long totalGain;
    // One second per bucket: current second plus the previous 899 seconds.
    // Fixed storage avoids retaining an object for every EXP packet on CLDC.
    private static final long[] bucketGain = new long[RATE_BUCKETS];
    private static boolean hasBucketClock;
    private static long lastBucketSecond;
    private static int currentBucket;

    /* Scratch used only by synchronized arithmetic entry points. */
    private static long scaledRemainder;

    private ExpRateSupport() { }

    /** Clears the farming-session rate after login, identity change, or disconnect. */
    public static synchronized void reset() {
        clearState();
    }

    /**
     * Adds one positive packet amount. Duplicate amounts count as separate
     * packets; zero and negative packets do not start or increase the rate.
     */
    public static synchronized void gain(int amount, long nowMillis) {
        observeClock(nowMillis);
        if (amount <= 0) return;

        if ((long)amount > Long.MAX_VALUE - totalGain) clearAt(nowMillis);
        if (!hasGain) {
            hasGain = true;
            firstGainMillis = nowMillis;
        }
        totalGain += (long)amount;
        bucketGain[currentBucket] += (long)amount;
    }

    /**
     * Returns the last 900 one-second buckets scaled to one hour, or -1 until
     * five minutes have elapsed since the session's first positive packet.
     * Before fifteen minutes, the denominator is elapsed time since that packet;
     * afterward it stays capped at fifteen minutes.
     */
    public static synchronized long rate(long nowMillis) {
        observeClock(nowMillis);
        if (!hasGain) return -1L;

        long elapsed = nowMillis - firstGainMillis;
        if (elapsed < 0L) {
            clearAt(nowMillis);
            return -1L;
        }
        if (elapsed < MIN_RATE_WINDOW_MS) return -1L;
        long elapsedForRate = elapsed < RATE_BUCKETS * RATE_BUCKET_MS
                ? elapsed : RATE_BUCKETS * RATE_BUCKET_MS;
        return roundedRate(totalGain, elapsedForRate);
    }

    /**
     * Estimates integer EXP remaining from a rounded total and current
     * progress. Fine progress is in thousandths of a percent (0..100000);
     * pass -1 to use native coarse progress q (0..999).
     */
    public static synchronized long remainingExp(long total,
            int fineMilliPercent, int coarseProgress) {
        if (total <= 0L) return -1L;

        int numerator = remainingNumerator(fineMilliPercent, coarseProgress);
        if (numerator < 0) return -1L;
        int denominator = fineMilliPercent == -1 ? 1000 : 100000;
        return scaledRounded(total, numerator, denominator);
    }

    /** Returns integer EXP as decimal text, or the requested missing-data text. */
    public static String formatLong(long value) {
        return value < 0L ? CALCULATING : formatWholeNumber(value);
    }

    /** Returns the rounded per-hour rate, including a valid rounded rate of zero. */
    public static String formatRate(long value) {
        return value < 0L ? CALCULATING : formatWholeNumber(value);
    }

    /** Returns the estimated level total as decimal text when it is positive. */
    public static String formatTotal(long value) {
        return value <= 0L ? CALCULATING : formatWholeNumber(value);
    }

    private static String formatWholeNumber(long value) {
        String digits = String.valueOf(value);
        StringBuffer result = new StringBuffer(digits.length()
                + (digits.length() - 1) / 3);
        for (int i = 0; i < digits.length(); i++) {
            result.append(digits.charAt(i));
            int digitsRemaining = digits.length() - i - 1;
            if (digitsRemaining > 0 && digitsRemaining % 3 == 0) {
                result.append('.');
            }
        }
        return result.toString();
    }

    /**
     * Formats a remaining-EXP ETA as at least two-digit hours and minutes.
     * Positive remaining EXP always rounds up to at least one minute.
     */
    public static synchronized String formatDuration(long remaining,
            long expPerHour) {
        if (remaining < 0L || expPerHour <= 0L) return CALCULATING;

        long minutes = durationMinutes(remaining, expPerHour);
        long hours = minutes / 60L;
        int minutePart = (int)(minutes % 60L);
        StringBuffer result = new StringBuffer();
        if (hours < 10L) result.append('0');
        result.append(hours).append(" giờ ");
        if (minutePart < 10) result.append('0');
        result.append(minutePart).append(" phút");
        return result.toString();
    }

    /**
     * Formats the level ETA after applying fine or coarse progress to the
     * level total. This keeps callers from accidentally using the full total.
     */
    public static synchronized String formatLevelDuration(long total,
            int fineMilliPercent, int coarseProgress, long expPerHour) {
        if (total <= 0L || expPerHour <= 0L) return CALCULATING;
        int numerator = remainingNumerator(fineMilliPercent, coarseProgress);
        if (numerator < 0) return CALCULATING;
        int denominator = fineMilliPercent == -1 ? 1000 : 100000;

        long wholeRemaining = scaledFloor(total, numerator, denominator);
        long fractionalRemaining = scaledRemainder;
        long minutes = durationMinutesForFraction(wholeRemaining,
                fractionalRemaining, denominator, expPerHour);
        return formatMinutes(minutes);
    }

    /** Returns a positive-EXP ETA in whole minutes rounded upward, or -1 if invalid. */
    public static synchronized long durationMinutes(long remaining,
            long expPerHour) {
        if (remaining < 0L || expPerHour <= 0L) return -1L;
        if (remaining == 0L) return 0L;

        long whole = remaining / expPerHour;
        long remainder = remaining % expPerHour;
        if (whole > Long.MAX_VALUE / 60L) return Long.MAX_VALUE;
        long minutes = whole * 60L;
        long fraction = multiplyDivide(remainder, 60, expPerHour);
        if (scaledRemainder != 0L) fraction++;
        if (minutes > Long.MAX_VALUE - fraction) return Long.MAX_VALUE;
        return minutes + fraction;
    }

    private static long durationMinutesForFraction(long wholeRemaining,
            long fractionalNumerator, int denominator, long expPerHour) {
        long wholeHours = wholeRemaining / expPerHour;
        if (wholeHours > Long.MAX_VALUE / 60L) return Long.MAX_VALUE;
        long minutes = wholeHours * 60L;
        long remainder = wholeRemaining % expPerHour;
        long fraction = multiplyDivide(remainder, 60, expPerHour);
        long timeRemainder = scaledRemainder;
        if (minutes > Long.MAX_VALUE - fraction) return Long.MAX_VALUE;
        minutes += fraction;

        long fractionalMinutesNumerator = fractionalNumerator * 60L;
        long additionalWholeMinutes = 0L;
        long minuteIndex = 1L;
        while (expPerHour <= Long.MAX_VALUE / minuteIndex) {
            long gap = expPerHour * minuteIndex - timeRemainder;
            if (gap > fractionalMinutesNumerator / denominator) break;
            additionalWholeMinutes++;
            minuteIndex++;
        }

        boolean hasFraction = false;
        if (additionalWholeMinutes == 0L) {
            hasFraction = timeRemainder != 0L || fractionalMinutesNumerator != 0L;
        } else {
            long gap = expPerHour * additionalWholeMinutes - timeRemainder;
            hasFraction = gap * (long)denominator != fractionalMinutesNumerator;
        }
        long extra = additionalWholeMinutes + (hasFraction ? 1L : 0L);
        if (minutes > Long.MAX_VALUE - extra) return Long.MAX_VALUE;
        return minutes + extra;
    }

    private static String formatMinutes(long minutes) {
        long hours = minutes / 60L;
        int minutePart = (int)(minutes % 60L);
        StringBuffer result = new StringBuffer();
        if (hours < 10L) result.append('0');
        result.append(hours).append(" giờ ");
        if (minutePart < 10) result.append('0');
        result.append(minutePart).append(" phút");
        return result.toString();
    }

    private static long roundedRate(long earned, long elapsed) {
        long whole = earned / elapsed;
        long remainder = earned % elapsed;
        if (whole > Long.MAX_VALUE / MILLIS_PER_HOUR) return Long.MAX_VALUE;

        long result = whole * MILLIS_PER_HOUR;
        long fraction = multiplyDivide(remainder, MILLIS_PER_HOUR, elapsed);
        if (scaledRemainder >= elapsed - scaledRemainder) fraction++;
        if (result > Long.MAX_VALUE - fraction) return Long.MAX_VALUE;
        return result + fraction;
    }

    private static long scaledRounded(long value, int numerator, int denominator) {
        long result = scaledFloor(value, numerator, denominator);
        if (scaledRemainder >= denominator - scaledRemainder) result++;
        return result;
    }

    private static long scaledFloor(long value, int numerator, int denominator) {
        long whole = value / denominator;
        long remainder = value % denominator;
        long result = whole * (long)numerator;
        long fraction = multiplyDivide(remainder, numerator, denominator);
        return result + fraction;
    }

    private static int remainingNumerator(int fineMilliPercent, int coarseProgress) {
        if (fineMilliPercent == -1) {
            if (coarseProgress < 0 || coarseProgress > 999) return -1;
            return 1000 - coarseProgress;
        }
        if (fineMilliPercent < 0 || fineMilliPercent > 100000) return -1;
        return 100000 - fineMilliPercent;
    }

    /**
     * Computes floor(value * multiplier / divisor) and retains the remainder.
     * Preconditions: 0 <= value < divisor and multiplier <= 3600000.
     */
    private static long multiplyDivide(long value, int multiplier, long divisor) {
        if (value == 0L || multiplier == 0) {
            scaledRemainder = 0L;
            return 0L;
        }

        long quotient = 0L;
        long remainder = 0L;
        int bit = 1;
        while (bit <= multiplier / 2) bit <<= 1;
        while (bit != 0) {
            int carry = 0;
            if (remainder >= divisor - remainder) {
                remainder -= divisor - remainder;
                carry++;
            } else {
                remainder += remainder;
            }
            if ((multiplier & bit) != 0) {
                if (remainder >= divisor - value) {
                    remainder -= divisor - value;
                    carry++;
                } else {
                    remainder += value;
                }
            }
            quotient = quotient * 2L + (long)carry;
            bit >>= 1;
        }
        scaledRemainder = remainder;
        return quotient;
    }

    private static void observeClock(long nowMillis) {
        if (hasClock && nowMillis < lastClockMillis) clearState();
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
            for (int i = 0; i < RATE_BUCKETS; i++) bucketGain[i] = 0L;
            totalGain = 0L;
            currentBucket = 0;
        } else {
            for (int i = 0; i < (int)elapsed; i++) {
                if (++currentBucket == RATE_BUCKETS) currentBucket = 0;
                totalGain -= bucketGain[currentBucket];
                bucketGain[currentBucket] = 0L;
            }
        }
        lastBucketSecond = second;
    }

    private static void clearAt(long nowMillis) {
        clearState();
        observeClock(nowMillis);
    }

    private static void clearState() {
        hasClock = false;
        lastClockMillis = 0L;
        hasGain = false;
        firstGainMillis = 0L;
        totalGain = 0L;
        for (int i = 0; i < RATE_BUCKETS; i++) bucketGain[i] = 0L;
        hasBucketClock = false;
        lastBucketSecond = 0L;
        currentBucket = 0;
        scaledRemainder = 0L;
    }
}
