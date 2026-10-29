import java.util.Random;

import luna.ui.ExpPercentInterpolator;

/** Deterministic host fixture for the CLDC-safe EXP percent interpolator. */
public final class ExpPercentInterpolatorHostTest {
    private static int assertions;

    private ExpPercentInterpolatorHostTest() { }

    public static void main(String[] args) {
        testFormatting();
        testUnavailableAndInvalidData();
        testFineMovementInsideCoarseBucket();
        testInfeasibleIntersection();
        testUpperBoundClamp();
        testExtremeLongTotals();
        testRandomFloorSimulation();
        testStatefulWindowPhase();
        testStateResetAndDenominatorChange();
        testAmbiguousRounding();
        System.out.println("ExpPercentInterpolatorHostTest: "
                + assertions + " assertions passed");
    }

    private static void testFormatting() {
        equal("0.000%", ExpPercentInterpolator.format(0), "zero format");
        equal("0.005%", ExpPercentInterpolator.format(5), "leading zeros");
        equal("37.412%", ExpPercentInterpolator.format(37412), "sample format");
        equal("99.999%", ExpPercentInterpolator.format(99999), "top bucket format");
        equal("100.000%", ExpPercentInterpolator.format(100000), "full format");
        check(ExpPercentInterpolator.format(-1) == null, "sentinel format");
    }

    private static void testUnavailableAndInvalidData() {
        int[] p = new int[] { 370, 370 };
        long[] t = new long[] { 0L, 50L };
        equal(-1, estimate(370, 100000L, 100000L, p, t, 1, 50L),
                "needs two samples");
        equal(-1, estimate(370, 100000L, 100000L, p, t, 2, 0L),
                "no positive current gain");
        equal(-1, estimate(370, 0L, 100000L, p, t, 2, 50L),
                "nonpositive total estimate");
        equal(-1, estimate(370, 100001L, 100000L, p, t, 2, 50L),
                "reversed total bounds");
        equal(-1, estimate(1000, 100000L, 100000L, p, t, 2, 50L),
                "invalid current coarse progress");
        equal(-1, estimate(370, 100000L, 100000L, null, t, 2, 50L),
                "null progress samples");
        equal(-1, estimate(370, 100000L, 100000L, p, null, 2, 50L),
                "null EXP samples");
        equal(-1, estimate(370, 100000L, 100000L, p, t, 3, 50L),
                "sample count exceeds arrays");

        int[] duplicateTotalProgress = new int[] { 370, 371, 371 };
        long[] duplicateTotal = new long[] { 0L, 50L, 50L };
        equal(-1, estimate(371, 100000L, 100000L,
                duplicateTotalProgress, duplicateTotal, 3, 50L),
                "zero-gain sample is unclean");

        int[] resetProgress = new int[] { 372, 371 };
        long[] resetTotals = new long[] { 0L, 50L };
        equal(-1, estimate(371, 100000L, 100000L,
                resetProgress, resetTotals, 2, 50L), "progress reset shape");

        int[] validProgress = new int[] { 370, 371 };
        long[] resetTotalsOnly = new long[] { 50L, 40L };
        equal(-1, estimate(371, 100000L, 100000L,
                validProgress, resetTotalsOnly, 2, 50L), "EXP reset shape");

        int[] outOfRangeProgress = new int[] { 370, 1000 };
        long[] increasingTotals = new long[] { 0L, 50L };
        equal(-1, estimate(999, 100000L, 100000L,
                outOfRangeProgress, increasingTotals, 2, 50L),
                "invalid sample coarse progress");

        int[] validSampleProgress = new int[] { 370, 370 };
        long[] negativeTotals = new long[] { 0L, -1L };
        equal(-1, estimate(370, 100000L, 100000L,
                validSampleProgress, negativeTotals, 2, 50L),
                "negative cumulative EXP");

        int[] manyProgress = new int[65];
        long[] manyTotals = new long[65];
        equal(-1, estimate(370, 100000L, 100000L,
                manyProgress, manyTotals, 65, 50L), "sample cap");
    }

    private static void testFineMovementInsideCoarseBucket() {
        int[] sampleProgress = new int[] { 370, 370 };
        long[] sampleTotal = new long[] { 0L, 50L };
        int value = estimate(370, 100000L, 100000L,
                sampleProgress, sampleTotal, 2, 50L);
        equal(37075, value, "interpolates movement hidden by coarse progress");
        equal("37.075%", ExpPercentInterpolator.format(value),
                "fine movement formatting");
    }

    private static void testInfeasibleIntersection() {
        int[] sampleProgress = new int[] { 0, 500 };
        long[] sampleTotal = new long[] { 0L, 50L };
        equal(-1, estimate(500, 100000L, 100000L,
                sampleProgress, sampleTotal, 2, 50L),
                "contradictory samples have no feasible intersection");
    }

    private static void testUpperBoundClamp() {
        int[] sampleProgress = new int[] { 999, 999 };
        long[] sampleTotal = new long[] { 0L, 99L };
        int value = estimate(999, 100000L, 100000L,
                sampleProgress, sampleTotal, 2, 99L);
        equal(99999, value, "100 percent rounded into final bucket");
        check(value <= 99999, "never exceeds 99999");
    }

    private static void testExtremeLongTotals() {
        long total = Long.MAX_VALUE - 100000L;
        long current = total / 2L;
        int[] sampleProgress = new int[] { 0, 500 };
        long[] sampleTotal = new long[] { 0L, current };
        int value = estimate(500, total - 1L, total + 1L,
                sampleProgress, sampleTotal, 2, current);
        check(value >= 50000 && value < 50100,
                "extreme long interpolation remains in coarse bucket: " + value);
    }

    private static void testRandomFloorSimulation() {
        Random random = new Random(0x48534fL);
        int cases = 500;
        for (int test = 0; test < cases; test++) {
            long total = 500000L + (long)random.nextInt(9000000);
            double start = random.nextDouble() * 70000.0d;
            int count = 2 + random.nextInt(8);
            int[] sampleProgress = new int[count];
            long[] sampleTotal = new long[count];
            sampleProgress[0] = (int)Math.floor(start / 100.0d);
            sampleTotal[0] = 0L;

            long cumulative = 0L;
            int currentProgress = sampleProgress[0];
            double currentExact = start;
            int maxStep = (int)(total / 100L);
            if (maxStep < 1) maxStep = 1;
            for (int i = 1; i < count; i++) {
                cumulative += 1L + (long)random.nextInt(maxStep);
                currentExact = start + ((double)cumulative * 100000.0d)
                        / (double)total;
                currentProgress = (int)Math.floor(currentExact / 100.0d);
                sampleTotal[i] = cumulative;
                sampleProgress[i] = currentProgress;
            }

            int estimate = estimate(currentProgress, total - 1000L,
                    total + 1000L, sampleProgress, sampleTotal, count, cumulative);
            check(estimate >= currentProgress * 100
                    && estimate < (currentProgress + 1) * 100,
                    "random floor case " + test + " stays in coarse bucket");
            check(Math.abs((double)estimate - currentExact) <= 51.0d,
                    "random floor case " + test + " stays near feasible value");
        }
    }

    private static void testStatefulWindowPhase() {
        long total = 100000000L;
        int[] sampleProgress = new int[64];
        long[] sampleTotal = new long[64];
        int count = 1;
        sampleProgress[0] = 374;
        sampleTotal[0] = 0L;
        ExpPercentInterpolator interpolator = new ExpPercentInterpolator();

        for (int event = 1; event <= 1200; event++) {
            long currentTotal = (long)event * 1000L;
            int exactPhase = 37412 + event;
            int progress = exactPhase / 100;
            if (count == sampleProgress.length) {
                for (int i = 1; i < count; i++) {
                    sampleProgress[i - 1] = sampleProgress[i];
                    sampleTotal[i - 1] = sampleTotal[i];
                }
                count--;
            }
            sampleProgress[count] = progress;
            sampleTotal[count] = currentTotal;
            count++;

            int value = interpolator.update(progress, total, total,
                    sampleProgress, sampleTotal, count, currentTotal);
            check(value >= 0, "stateful exact phase remains available at " + event);
            equal(value, interpolator.value(), "cached stateful value at " + event);
            if (event >= 88) {
                check(Math.abs(value - exactPhase) <= 1,
                        "stateful phase stays within one unit at " + event
                                + ": estimate=" + value + " exact=" + exactPhase);
            }
            if (event == 560) {
                int stateless = estimate(progress, total, total, sampleProgress,
                        sampleTotal, count, currentTotal);
                check(Math.abs(stateless - exactPhase) > 1,
                        "bounded samples alone lose phase after old crossing leaves window");
            }
        }
    }

    private static void testStateResetAndDenominatorChange() {
        int[] sampleProgress = new int[] { 370, 370 };
        long[] sampleTotal = new long[] { 0L, 50000L };
        ExpPercentInterpolator interpolator = new ExpPercentInterpolator();
        int original = estimate(370, 100000000L, 100000000L,
                sampleProgress, sampleTotal, 2, 50000L);
        equal(original, interpolator.update(370, 100000000L, 100000000L,
                sampleProgress, sampleTotal, 2, 50000L),
                "initial state matches stateless estimate");

        int changedTotal = estimate(370, 200000000L, 200000000L,
                sampleProgress, sampleTotal, 2, 50000L);
        equal(changedTotal, interpolator.update(370, 200000000L, 200000000L,
                sampleProgress, sampleTotal, 2, 50000L),
                "denominator change discards carried phase");

        interpolator.reset();
        equal(-1, interpolator.value(), "reset clears cached value");
        equal(original, interpolator.update(370, 100000000L, 100000000L,
                sampleProgress, sampleTotal, 2, 50000L),
                "reset starts a fresh interval");

        equal(-1, interpolator.update(369, 100000000L, 100000000L,
                sampleProgress, sampleTotal, 2, 50000L),
                "invalid reset shape falls back");
        equal(-1, interpolator.value(), "invalid update clears cached value");
    }

    private static void testAmbiguousRounding() {
        int[] sampleProgress = new int[] { 370, 370, 370 };
        long[] sampleTotal = new long[] { 0L, 1000L, 2000L };
        int value = estimate(370, 200000000L, 200000000L,
                sampleProgress, sampleTotal, 3, 2000L);
        check(value >= 37000 && value < 37100,
                "fractional-ratio midpoint remains in native bucket");
        equal(value, new ExpPercentInterpolator().update(370,
                200000000L, 200000000L, sampleProgress, sampleTotal, 3, 2000L),
                "stateful estimate rounds the same feasible interval");
    }

    private static int estimate(int progress, long low, long high,
            int[] sampleProgress, long[] sampleTotal, int count,
            long currentTotal) {
        return ExpPercentInterpolator.estimate(progress, low, high,
                sampleProgress, sampleTotal, count, currentTotal);
    }

    private static void equal(int expected, int actual, String name) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(name + ": expected " + expected + " but got " + actual);
        }
    }

    private static void equal(String expected, String actual, String name) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + " but got " + actual);
        }
    }

    private static void check(boolean condition, String name) {
        assertions++;
        if (!condition) throw new AssertionError(name);
    }
}
