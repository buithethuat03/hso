import java.lang.reflect.Method;
import java.util.ArrayList;
import java.math.BigInteger;
import java.util.Random;

import luna.features.ExpRateSupport;

/** Deterministic host fixture for the CLDC-safe EXP rate and ETA helpers. */
public final class ExpRateHostTest {
    private static int assertions;

    private ExpRateHostTest() { }

    public static void main(String[] args) throws Exception {
        testWarmupAndRollingBoundaries();
        testIdleGapsAndNoActivity();
        testInvalidPacketsAndClockRollback();
        testHighVolumeAndRandomizedRollingOracle();
        testRoundedRateBigIntegerOracle();
        testRemainingExpAndEta();
        testWholeNumberFormatting();
        testBigIntegerOracleBoundaries();
        System.out.println("ExpRateHostTest: " + assertions + " assertions passed");
    }

    private static void testWarmupAndRollingBoundaries() {
        ExpRateSupport.reset();
        RollingOracle oracle = new RollingOracle();
        compareGain(oracle, 100, 0L, "first gain starts warmup");
        compareGain(oracle, 2, 1000L, "second packet enters next second bucket");
        compareRate(oracle, 299999L, "rate unavailable before five minutes");
        compareRate(oracle, 300000L, "five-minute warmup makes the rate available");
        equal(1224L, ExpRateSupport.rate(300000L),
                "initial rate includes all EXP received during warmup");
        compareRate(oracle, 300999L, "same second keeps its bucket");
        compareRate(oracle, 301000L, "recent packets remain in the fifteen-minute window");
        equal(1220L, ExpRateSupport.rate(301000L), "elapsed-time denominator grows after warmup");
        compareGain(oracle, 3, 301000L, "new gain enters the warmed rolling window");
        compareRate(oracle, 600000L, "elapsed-time denominator grows through fifteen minutes");
        equal(630L, ExpRateSupport.rate(600000L), "six-minute rate uses the six-minute denominator");
        compareRate(oracle, 900000L, "oldest second expires at the fifteen-minute boundary");
        equal(20L, ExpRateSupport.rate(900000L), "fifteen-minute rate uses a capped denominator");
        compareRate(oracle, 901000L, "next bucket expires after 900 seconds");
        equal(12L, ExpRateSupport.rate(901000L), "recent gain remains after older buckets expire");
        compareRate(oracle, 1201000L, "warmed empty window reports zero");
        equal(0L, ExpRateSupport.rate(1201000L), "empty fifteen-minute window is available zero");
    }

    private static void testIdleGapsAndNoActivity() {
        ExpRateSupport.reset();
        RollingOracle oracle = new RollingOracle();
        compareGain(oracle, 10, 0L, "initial packet");
        compareRate(oracle, 299999L, "idle session still waits for warmup");
        compareRate(oracle, 300000L, "initial packet remains during the fifteen-minute window");
        equal(120L, ExpRateSupport.rate(300000L), "warmup rate includes the initial packet");
        compareRate(oracle, 3600000L, "long idle session stays available at zero");
        equal(0L, ExpRateSupport.rate(3600000L), "no activity for an hour does not restore calculating text");
        compareGain(oracle, 3, 3600000L, "activity after a long idle adds to the current window");
        equal(12L, ExpRateSupport.rate(3600000L), "idle resume does not restart warmup");
    }

    private static void testInvalidPacketsAndClockRollback() {
        ExpRateSupport.reset();
        RollingOracle oracle = new RollingOracle();
        compareGain(oracle, 0, 0L, "zero packet does not start warmup");
        compareGain(oracle, -50, 1000L, "negative packet does not start warmup");
        compareRate(oracle, 300000L, "invalid packets leave the rate unavailable");
        compareGain(oracle, 4, 300000L, "first positive packet starts warmup");
        compareGain(oracle, 2, 301000L, "second positive packet enters the window");
        compareRate(oracle, 600000L, "positive packets are counted in their active buckets");
        equal(72L, ExpRateSupport.rate(600000L), "both five-minute packets remain in the longer window");
        compareRate(oracle, 1200000L, "the first packet expires at age 900 seconds");
        equal(8L, ExpRateSupport.rate(1200000L), "the later packet remains in the capped window");

        ExpRateSupport.reset();
        oracle.reset();
        compareGain(oracle, 5, 1000L, "rollback setup first packet");
        compareGain(oracle, 2, 2000L, "rollback setup retained packet");
        compareRate(oracle, 301000L, "rollback setup rate");
        compareRate(oracle, 300999L, "backward clock clears session and window");
        compareGain(oracle, 5, 300999L, "gain after rollback starts a new session");
        compareGain(oracle, 2, 301999L, "post-rollback packet enters the next fresh bucket");
        compareRate(oracle, 600999L, "post-rollback session warms independently");
        equal(84L, ExpRateSupport.rate(600999L), "rollback starts a fresh five-minute measurement");
        compareRate(oracle, 1200000L, "post-rollback oldest bucket expires at fifteen minutes");
        equal(8L, ExpRateSupport.rate(1200000L), "only the newer bucket remains after rollback");

        ExpRateSupport.reset();
        oracle.reset();
        compareGain(oracle, 100, 0L, "explicit reset setup");
        compareRate(oracle, 300000L, "explicit reset setup warms");
        ExpRateSupport.reset();
        oracle.reset();
        compareRate(oracle, 300000L, "explicit reset discards warmup and bucket history");
        compareGain(oracle, 7, 300000L, "gain after reset starts fresh warmup");
        compareRate(oracle, 599999L, "reset warmup remains incomplete");
    }

    private static void testHighVolumeAndRandomizedRollingOracle() {
        ExpRateSupport.reset();
        RollingOracle oracle = new RollingOracle();
        compareGain(oracle, 1, 0L, "high-volume setup starts warmup");
        for (int i = 0; i < 20000; i++) {
            int amount = 1 + (i % 7);
            oracle.gain(amount, 301000L);
            ExpRateSupport.gain(amount, 301000L);
        }
        compareRate(oracle, 301000L, "high-volume same-second packet bucket");
        compareRate(oracle, 901000L, "high-volume bucket remains inside the 900-second window");
        compareRate(oracle, 1201000L, "high-volume bucket expires after 900 bucket seconds");

        ExpRateSupport.reset();
        oracle.reset();
        Random random = new Random(0x48534f455850L);
        long now = 0L;
        for (int i = 0; i < 1800; i++) {
            if (i > 0) {
                long gap = random.nextInt(4000);
                if (i % 113 == 0) gap += 300000L + random.nextInt(2000);
                now += gap;
            }
            int kind = random.nextInt(12);
            int amount = kind == 0 ? 0
                    : kind == 1 ? -1 - random.nextInt(1000)
                    : 1 + random.nextInt(100000);
            compareGain(oracle, amount, now, "random chronological gain " + i);
            long queryTime = now + random.nextInt(2500);
            compareRate(oracle, queryTime, "random rolling query " + i);
            now = queryTime;
        }
        compareRate(oracle, now + 600000L, "random stream expires after a long inactive gap");
    }

    private static void testRoundedRateBigIntegerOracle() throws Exception {
        long[] totals = new long[] {
            0L, 1L, 2L, 999999L, 3000000L,
            Long.MAX_VALUE / 2L, Long.MAX_VALUE
        };
        for (int i = 0; i < totals.length; i++) {
            equal(rateOracle(totals[i], 300000L), invokeRoundedRate(totals[i], 300000L),
                    "roundedRate BigInteger boundary " + totals[i]);
        }

        Random random = new Random(0x5241544541524954L);
        for (int i = 0; i < 500; i++) {
            long earned = positiveLong(random);
            equal(rateOracle(earned, 300000L), invokeRoundedRate(earned, 300000L),
                    "roundedRate random case " + i);
        }
    }

    private static long invokeRoundedRate(long earned, long elapsed) throws Exception {
        Method method = ExpRateSupport.class.getDeclaredMethod(
                "roundedRate", Long.TYPE, Long.TYPE);
        method.setAccessible(true);
        return ((Long)method.invoke(null, Long.valueOf(earned), Long.valueOf(elapsed))).longValue();
    }

    private static void compareGain(RollingOracle oracle, int amount, long now, String message) {
        oracle.gain(amount, now);
        ExpRateSupport.gain(amount, now);
        equal(oracle.rateWithoutClockAdvance(now), ExpRateSupport.rate(now), message);
    }

    private static void compareRate(RollingOracle oracle, long now, String message) {
        equal(oracle.rate(now), ExpRateSupport.rate(now), message);
    }

    private static final class RollingOracle {
        private final ArrayList<Event> events = new ArrayList<Event>();
        private boolean hasClock;
        private long lastClockMillis;
        private boolean hasGain;
        private long firstGainMillis;

        private void reset() {
            events.clear();
            hasClock = false;
            lastClockMillis = 0L;
            hasGain = false;
            firstGainMillis = 0L;
        }

        private void gain(int amount, long now) {
            observeClock(now);
            if (amount <= 0) return;
            if (!hasGain) {
                hasGain = true;
                firstGainMillis = now;
            }
            events.add(new Event(now / 1000L, amount));
        }

        private long rate(long now) {
            observeClock(now);
            return rateWithoutClockAdvance(now);
        }

        private long rateWithoutClockAdvance(long now) {
            if (!hasGain) return -1L;
            long elapsed = now - firstGainMillis;
            if (elapsed < 0L) {
                reset();
                hasClock = true;
                lastClockMillis = now;
                return -1L;
            }
            if (elapsed < 300000L) return -1L;

            long bucket = now / 1000L;
            BigInteger sum = BigInteger.ZERO;
            for (int i = 0; i < events.size(); i++) {
                Event event = events.get(i);
                long age = bucket - event.bucketSecond;
                if (age >= 0L && age < 900L) {
                    sum = sum.add(BigInteger.valueOf(event.amount));
                }
            }
            long denominator = elapsed < 900000L ? elapsed : 900000L;
            return rateOracle(saturatedLong(sum), denominator);
        }

        private void observeClock(long now) {
            if (hasClock && now < lastClockMillis) reset();
            hasClock = true;
            lastClockMillis = now;
        }
    }

    private static final class Event {
        private final long bucketSecond;
        private final long amount;

        private Event(long bucketSecond, long amount) {
            this.bucketSecond = bucketSecond;
            this.amount = amount;
        }
    }

    private static void testRemainingExpAndEta() {
        equal(500L, ExpRateSupport.remainingExp(1000L, 50000, 0),
                "fine progress calculates remaining EXP");
        equal(250L, ExpRateSupport.remainingExp(1000L, 75000, 100),
                "fine progress takes precedence over coarse progress");
        equal(500L, ExpRateSupport.remainingExp(1000L, -1, 500),
                "native coarse fallback calculates remaining EXP");
        equal(1L, ExpRateSupport.remainingExp(1000L, -1, 999),
                "coarse fallback retains the last tenth-percent of EXP");
        equal(Long.MAX_VALUE, ExpRateSupport.remainingExp(Long.MAX_VALUE, -1, 0),
                "remaining EXP avoids multiplication overflow");
        equal(-1L, ExpRateSupport.remainingExp(0L, -1, 0), "zero total is unavailable");

        equal("01 giờ 00 phút", ExpRateSupport.formatLevelDuration(
                2000L, 50000, 0, 1000L), "ETA uses remaining half-level EXP");
        equal("00 giờ 01 phút", ExpRateSupport.formatLevelDuration(
                1L, 99999, 0, 1L), "positive fractional remainder rounds up to one minute");
        equal("00 giờ 01 phút", ExpRateSupport.formatDuration(1L, 120L),
                "positive remainder rounds up to one minute");
        equal("00 giờ 00 phút", ExpRateSupport.formatDuration(0L, 120L),
                "zero remaining EXP is zero time");
        equal("100 giờ 05 phút", ExpRateSupport.formatDuration(6005L, 60L),
                "hour field grows past two digits");
        equal("Đang tính toán...", ExpRateSupport.formatDuration(10L, 0L),
                "nonpositive rate uses requested missing text");
        equal("Đang tính toán...", ExpRateSupport.formatTotal(0L),
                "missing total uses requested missing text");
        equal("0", ExpRateSupport.formatRate(0L), "rounded zero rate remains available");
    }

    private static void testWholeNumberFormatting() {
        equal("0", ExpRateSupport.formatLong(0L), "zero whole EXP stays visible");
        equal("123", ExpRateSupport.formatLong(123L), "three-digit EXP has no separator");
        equal("1.234", ExpRateSupport.formatLong(1234L), "four-digit EXP uses a dot separator");
        equal("123.456.789", ExpRateSupport.formatLong(123456789L),
                "whole EXP groups each three digits");
        equal("9.223.372.036.854.775.807", ExpRateSupport.formatLong(Long.MAX_VALUE),
                "whole EXP supports the largest positive long");
        equal("12.000", ExpRateSupport.formatRate(12000L), "hourly rate groups digits");
        equal("0", ExpRateSupport.formatRate(0L), "zero hourly rate stays visible");
        equal("Đang tính toán...", ExpRateSupport.formatRate(-1L),
                "unavailable hourly rate keeps calculating text");
        equal("123.456.789", ExpRateSupport.formatTotal(123456789L),
                "level total groups each three digits");
        equal("9.223.372.036.854.775.807", ExpRateSupport.formatTotal(Long.MAX_VALUE),
                "level total supports the largest positive long");
        equal("Đang tính toán...", ExpRateSupport.formatTotal(0L),
                "missing total keeps calculating text");
    }

    private static void testBigIntegerOracleBoundaries() throws Exception {
        Random random = new Random(0x48534f455850L);
        long[] largeTotals = new long[] { 1L, 2L, 999L, 100000L,
                Long.MAX_VALUE - 1L, Long.MAX_VALUE };
        int[] fineValues = new int[] { 0, 1, 49999, 50000, 99999, 100000 };
        for (int i = 0; i < largeTotals.length; i++) {
            for (int j = 0; j < fineValues.length; j++) {
                long total = largeTotals[i];
                int fine = fineValues[j];
                equal(remainingOracle(total, fine, 0),
                        ExpRateSupport.remainingExp(total, fine, 999),
                        "fine remaining oracle total=" + total + " progress=" + fine);
            }
        }

        for (int i = 0; i < 240; i++) {
            long total = positiveLong(random);
            int fine = random.nextInt(100001);
            int coarse = random.nextInt(1000);
            equal(remainingOracle(total, fine, coarse),
                    ExpRateSupport.remainingExp(total, fine, coarse),
                    "random fine remaining oracle case " + i);
            equal(remainingOracle(total, -1, coarse),
                    ExpRateSupport.remainingExp(total, -1, coarse),
                    "random coarse remaining oracle case " + i);
        }

        long[] etaTotals = new long[] { 1L, 2L, 999999L,
                Long.MAX_VALUE - 1L, Long.MAX_VALUE };
        int[] etaProgress = new int[] { 0, 1, 49999, 99998, 99999, 100000 };
        long[] rates = new long[] { 1L, 2L, 59L, 60L, 120L,
                3600000L, Long.MAX_VALUE - 1L, Long.MAX_VALUE };
        for (int i = 0; i < etaTotals.length; i++) {
            for (int j = 0; j < etaProgress.length; j++) {
                for (int k = 0; k < rates.length; k++) {
                    long total = etaTotals[i];
                    int fine = etaProgress[j];
                    long rate = rates[k];
                    equal(etaOracle(total, fine, 0, rate),
                            ExpRateSupport.formatLevelDuration(total, fine, 777, rate),
                            "ETA boundary oracle total=" + total + " progress=" + fine
                                    + " rate=" + rate);
                }
            }
        }

        for (int i = 0; i < 240; i++) {
            long total = positiveLong(random);
            int fine = random.nextInt(100001);
            int coarse = random.nextInt(1000);
            long rate = positiveLong(random);
            equal(etaOracle(total, fine, coarse, rate),
                    ExpRateSupport.formatLevelDuration(total, fine, coarse, rate),
                    "random fine ETA oracle case " + i);
            equal(etaOracle(total, -1, coarse, rate),
                    ExpRateSupport.formatLevelDuration(total, -1, coarse, rate),
                    "random coarse ETA oracle case " + i);
        }

        equal(durationOracle(Long.MAX_VALUE, 1L),
                ExpRateSupport.formatDuration(Long.MAX_VALUE, 1L),
                "duration minute saturation at long maximum");
        equal(durationOracle(Long.MAX_VALUE, 1L),
                ExpRateSupport.formatLevelDuration(Long.MAX_VALUE, 0, 0, 1L),
                "level duration minute saturation at long maximum");
    }

    private static long rateOracle(long earned, long elapsed) {
        BigInteger numerator = BigInteger.valueOf(earned)
                .multiply(BigInteger.valueOf(3600000L));
        BigInteger denominator = BigInteger.valueOf(elapsed);
        BigInteger[] divided = numerator.divideAndRemainder(denominator);
        if (divided[1].shiftLeft(1).compareTo(denominator) >= 0) {
            divided[0] = divided[0].add(BigInteger.ONE);
        }
        return saturatedLong(divided[0]);
    }

    private static long remainingOracle(long total, int fine, int coarse) {
        if (total <= 0L) return -1L;
        int numerator;
        int denominator;
        if (fine == -1) {
            if (coarse < 0 || coarse > 999) return -1L;
            numerator = 1000 - coarse;
            denominator = 1000;
        } else {
            if (fine < 0 || fine > 100000) return -1L;
            numerator = 100000 - fine;
            denominator = 100000;
        }
        BigInteger value = BigInteger.valueOf(total)
                .multiply(BigInteger.valueOf(numerator));
        BigInteger divisor = BigInteger.valueOf(denominator);
        BigInteger[] divided = value.divideAndRemainder(divisor);
        if (divided[1].shiftLeft(1).compareTo(divisor) >= 0) {
            divided[0] = divided[0].add(BigInteger.ONE);
        }
        return saturatedLong(divided[0]);
    }

    private static String etaOracle(long total, int fine, int coarse, long rate) {
        if (total <= 0L || rate <= 0L) return "Đang tính toán...";
        int numerator;
        int denominator;
        if (fine == -1) {
            if (coarse < 0 || coarse > 999) return "Đang tính toán...";
            numerator = 1000 - coarse;
            denominator = 1000;
        } else {
            if (fine < 0 || fine > 100000) return "Đang tính toán...";
            numerator = 100000 - fine;
            denominator = 100000;
        }
        BigInteger n = BigInteger.valueOf(total)
                .multiply(BigInteger.valueOf(numerator))
                .multiply(BigInteger.valueOf(60L));
        BigInteger d = BigInteger.valueOf(denominator)
                .multiply(BigInteger.valueOf(rate));
        BigInteger[] divided = n.divideAndRemainder(d);
        if (divided[1].signum() != 0) divided[0] = divided[0].add(BigInteger.ONE);
        return formatMinutesOracle(saturatedLong(divided[0]));
    }

    private static String durationOracle(long remaining, long rate) {
        if (remaining < 0L || rate <= 0L) return "Đang tính toán...";
        BigInteger n = BigInteger.valueOf(remaining).multiply(BigInteger.valueOf(60L));
        BigInteger d = BigInteger.valueOf(rate);
        BigInteger[] divided = n.divideAndRemainder(d);
        if (divided[1].signum() != 0) divided[0] = divided[0].add(BigInteger.ONE);
        return formatMinutesOracle(saturatedLong(divided[0]));
    }

    private static long saturatedLong(BigInteger value) {
        BigInteger max = BigInteger.valueOf(Long.MAX_VALUE);
        return value.compareTo(max) > 0 ? Long.MAX_VALUE : value.longValue();
    }

    private static String formatMinutesOracle(long minutes) {
        long hours = minutes / 60L;
        long minutePart = minutes % 60L;
        return (hours < 10L ? "0" : "") + hours + " giờ "
                + (minutePart < 10L ? "0" : "") + minutePart + " phút";
    }

    private static long positiveLong(Random random) {
        long value = random.nextLong() & Long.MAX_VALUE;
        return value == 0L ? 1L : value;
    }

    private static void equal(long expected, long actual, String message) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void equal(String expected, String actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
