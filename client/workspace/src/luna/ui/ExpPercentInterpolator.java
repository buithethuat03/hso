package luna.ui;

/**
 * Interpolates the server's one-tenth-percent progress using clean same-level
 * cumulative EXP samples and an estimated total EXP for the level.
 */
public final class ExpPercentInterpolator {
    private static final int MAX_SAMPLES = 64;
    private static final int MAX_PROGRESS = 999;
    private static final double PROGRESS_SCALE = 100000.0d;

    private final double[] intervalScratch = new double[2];
    private boolean hasState;
    private long lastDenominator;
    private long lastTotal;
    private double feasibleLow;
    private double feasibleHigh;
    private int lastValue = -1;

    public ExpPercentInterpolator() { }

    /**
     * Returns the estimated progress in thousandths of a percent, or -1 when
     * the supplied segment cannot produce a finite, feasible interpolation.
     * Progress values are server tenths of a percent (0 through 999).
     */
    public static int estimate(int progress, long totalLow, long totalHigh,
            int[] sampleProgress, long[] sampleTotal, int count,
            long currentTotal) {
        double[] interval = new double[2];
        if (!calculateInterval(progress, totalLow, totalHigh, sampleProgress,
                sampleTotal, count, currentTotal, interval)) {
            return -1;
        }
        return roundInterval(progress, interval[0], interval[1]);
    }

    /** Clears phase carried forward from prior accepted EXP gains. */
    public void reset() {
        hasState = false;
        lastDenominator = 0L;
        lastTotal = 0L;
        feasibleLow = 0.0d;
        feasibleHigh = 0.0d;
        lastValue = -1;
    }

    /**
     * Updates the stateful phase estimate after an accepted positive EXP gain.
     * When the denominator is unchanged, the prior feasible interval moves
     * forward with EXP and is intersected with the current bounded sample set.
     */
    public int update(int progress, long totalLow, long totalHigh,
            int[] sampleProgress, long[] sampleTotal, int count,
            long currentTotal) {
        if (!calculateInterval(progress, totalLow, totalHigh, sampleProgress,
                sampleTotal, count, currentTotal, intervalScratch)) {
            reset();
            return -1;
        }

        long denominator = totalLow + (totalHigh - totalLow) / 2L;
        double nextLow = intervalScratch[0];
        double nextHigh = intervalScratch[1];

        if (hasState && denominator == lastDenominator
                && currentTotal >= lastTotal) {
            double shift = 0.0d;
            if (currentTotal > lastTotal) {
                long delta = currentTotal - lastTotal;
                shift = ((double)delta * PROGRESS_SCALE)
                        / (double)denominator;
            }
            if (shift == shift && shift >= 0.0d
                    && shift <= PROGRESS_SCALE) {
                double carriedLow = feasibleLow + shift;
                double carriedHigh = feasibleHigh + shift;
                if (carriedLow > nextLow) nextLow = carriedLow;
                if (carriedHigh < nextHigh) nextHigh = carriedHigh;
                if (nextLow >= nextHigh) {
                    reset();
                    return -1;
                }
            }
        }

        int rounded = roundInterval(progress, nextLow, nextHigh);
        if (rounded < 0) {
            reset();
            return -1;
        }
        hasState = true;
        lastDenominator = denominator;
        lastTotal = currentTotal;
        feasibleLow = nextLow;
        feasibleHigh = nextHigh;
        lastValue = rounded;
        return rounded;
    }

    /** Returns the latest value produced by update(), or -1 before one exists. */
    public int value() {
        return lastValue;
    }

    private static boolean calculateInterval(int progress, long totalLow,
            long totalHigh, int[] sampleProgress, long[] sampleTotal,
            int count, long currentTotal, double[] interval) {
        if (progress < 0 || progress > MAX_PROGRESS
                || totalLow <= 0L || totalHigh < totalLow
                || sampleProgress == null || sampleTotal == null
                || count < 2 || count > MAX_SAMPLES
                || count > sampleProgress.length || count > sampleTotal.length
                || currentTotal <= 0L) {
            return false;
        }

        long totalMidpoint = totalLow + (totalHigh - totalLow) / 2L;
        if (totalMidpoint <= 0L) return false;

        int previousProgress = -1;
        long previousTotal = -1L;
        for (int i = 0; i < count; i++) {
            int observedProgress = sampleProgress[i];
            long observedTotal = sampleTotal[i];
            if (observedProgress < 0 || observedProgress > MAX_PROGRESS
                    || observedTotal < 0L
                    || (i > 0 && (observedProgress < previousProgress
                    || observedTotal <= previousTotal))) {
                return false;
            }
            previousProgress = observedProgress;
            previousTotal = observedTotal;
        }

        // The segment must have gained EXP after its first clean sample. The
        // current total may equal the last sample because that sample can be
        // the current packet itself.
        if (currentTotal <= sampleTotal[0]
                || currentTotal < sampleTotal[count - 1]
                || progress < sampleProgress[count - 1]) {
            return false;
        }

        double bucketLow = (double)progress * 100.0d;
        double bucketHigh = bucketLow + 100.0d;
        double feasibleLow = bucketLow;
        double feasibleHigh = bucketHigh;
        double total = (double)totalMidpoint;

        for (int i = 0; i < count; i++) {
            long delta = currentTotal - sampleTotal[i];
            if (delta < 0L) return false;

            // Convert before multiplying so large cumulative totals cannot
            // overflow an integer intermediate.
            double shift = ((double)delta * PROGRESS_SCALE) / total;
            if (shift != shift || shift < 0.0d || shift > PROGRESS_SCALE) {
                return false;
            }
            double sampleLow = (double)sampleProgress[i] * 100.0d + shift;
            double sampleHigh = sampleLow + 100.0d;
            if (sampleLow != sampleLow || sampleHigh != sampleHigh) return false;

            if (sampleLow > feasibleLow) feasibleLow = sampleLow;
            if (sampleHigh < feasibleHigh) feasibleHigh = sampleHigh;
            if (feasibleLow >= feasibleHigh) return false;
        }

        interval[0] = feasibleLow;
        interval[1] = feasibleHigh;
        return true;
    }

    private static int roundInterval(int progress, double low, double high) {
        double bucketLow = (double)progress * 100.0d;
        double bucketHigh = bucketLow + 100.0d;
        if (low != low || high != high || low >= high
                || low < bucketLow || high > bucketHigh) return -1;

        double midpoint = low + (high - low) / 2.0d;
        if (midpoint != midpoint || midpoint < bucketLow || midpoint >= bucketHigh) {
            return -1;
        }

        int rounded = (int)Math.floor(midpoint + 0.5d);
        int bucketMinimum = progress * 100;
        int bucketMaximum = bucketMinimum + 99;
        if (rounded < bucketMinimum) rounded = bucketMinimum;
        if (rounded > bucketMaximum) rounded = bucketMaximum;
        if (rounded > 99999) rounded = 99999;
        return rounded;
    }

    /**
     * Formats a nonnegative thousandths-of-a-percent value with three decimal
     * places. The caller should handle the -1 estimate sentinel separately.
     */
    public static String format(int milliPercent) {
        return format(milliPercent, '.');
    }

    /** Keeps the native HUD decimal separator when adding fractional digits. */
    public static String format(int milliPercent, char separator) {
        if (milliPercent < 0) return null;
        int whole = milliPercent / 1000;
        int fraction = milliPercent % 1000;
        StringBuffer result = new StringBuffer();
        result.append(whole).append(separator);
        result.append((char)('0' + fraction / 100));
        result.append((char)('0' + (fraction / 10) % 10));
        result.append((char)('0' + fraction % 10));
        result.append('%');
        return result.toString();
    }
}
