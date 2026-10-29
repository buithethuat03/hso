import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import luna.api.ExpEstimateStore;
import luna.features.ExpEstimateSupport;
import luna.ui.ExpPercentInterpolator;

/** Event/cache/session coverage for fine EXP numerator using the estimator. */
public final class ExpPercentIntegrationHostTest {
    private static int checks;
    private static final int LEVEL_400M = 70;
    private static final int LEVEL_355M = 71;
    private static final long TOTAL_400M = 400000000L;
    private static final long TOTAL_355M = 355700000L;

    public static void main(String[] args) throws Exception {
        checkInterpolationLifecycle();
        check(Class.forName("luna.features.ExpEstimateSupport", true,
                ExpEstimateSupport.class.getClassLoader()) == ExpEstimateSupport.class,
                "support class lookup resolves the candidate implementation");
        check(Class.forName("luna.ui.ExpPercentInterpolator", true,
                ExpEstimateSupport.class.getClassLoader()) == ExpPercentInterpolator.class,
                "interpolator class lookup uses the same candidate loader");

        CountingStore store400 = new CountingStore(exactRecord(LEVEL_400M, TOTAL_400M));
        ExpEstimateSupport.setStore(store400);
        ExpEstimateSupport.snapshot("test", 1, LEVEL_400M, 320);
        check("32,0%".equals(ExpEstimateSupport.percentage(320)),
                "cached total falls back to the native percentage before fine data");
        check("(128M/400M)".equals(ExpEstimateSupport.line(320)),
                "cached 400M total uses native progress before fine data");
        check(ExpEstimateSupport.estimateLow() == TOTAL_400M
                && ExpEstimateSupport.estimateHigh() == TOTAL_400M,
                "cached 400M denominator is exact");
        int loadsBeforePaint = store400.loads;
        int savesBeforePaint = store400.saves;
        String coarseLine = ExpEstimateSupport.line(320);
        for (int i = 0; i < 20; i++) {
            check(coarseLine == ExpEstimateSupport.line(320),
                    "unchanged HUD paint reuses its cached line");
        }
        check(store400.loads == loadsBeforePaint && store400.saves == savesBeforePaint,
                "HUD paints do not perform new RMS work");
        ExpEstimateSupport.disconnect();

        ExpEstimateSupport.setStore(new CountingStore(exactRecord(LEVEL_400M, TOTAL_400M)));
        ExpEstimateSupport.snapshot("test", 1, LEVEL_400M, 0);
        feedTo(LEVEL_400M, TOTAL_400M, 128048000L);
        check("32,012%".equals(ExpEstimateSupport.percentage(320)),
                "live packet history resolves 32.012 percent while native progress stays 32.0");
        check("(128M/400M)".equals(ExpEstimateSupport.line(320)),
                "32.012 percent formats the cached 400M numerator as 128M");
        check(ExpEstimateSupport.estimateLow() == TOTAL_400M
                && ExpEstimateSupport.estimateHigh() == TOTAL_400M,
                "fine packets do not change the cached 400M denominator");

        String beforeCompactRound = ExpEstimateSupport.line(320);
        boolean crossedCompactRound = false;
        while (!crossedCompactRound) {
            long next = ExpEstimateSupport.currentSessionTotal() + 1000L;
            int nativeProgress = (int)(next * 1000L / TOTAL_400M);
            check(nativeProgress == 320,
                    "compact numerator updates before native progress leaves the same bucket");
            ExpEstimateSupport.gain("test", 1, LEVEL_400M, nativeProgress, 1000);
            String fine = ExpEstimateSupport.percentage(nativeProgress);
            String line = ExpEstimateSupport.line(nativeProgress);
            if (!beforeCompactRound.equals(line)) {
                check("(128.1M/400M)".equals(line),
                        "K/M rounding threshold updates the numerator line");
                check(parse(fine) >= 32013 && parse(fine) < 32100,
                        "line changes on fine progress without a native 0.1 percent update");
                crossedCompactRound = true;
            }
        }
        check(ExpEstimateSupport.estimateLow() == TOTAL_400M
                && ExpEstimateSupport.estimateHigh() == TOTAL_400M,
                "same-bucket numerator update leaves the denominator unchanged");
        feedTo(LEVEL_400M, TOTAL_400M, 128396000L);
        check("32,099%".equals(ExpEstimateSupport.percentage(320)),
                "continued same-bucket packets resolve 32.099 percent");
        check("(128.4M/400M)".equals(ExpEstimateSupport.line(320)),
                "32.099 percent updates the 400M numerator immediately");
        check(ExpEstimateSupport.estimateLow() == TOTAL_400M
                && ExpEstimateSupport.estimateHigh() == TOTAL_400M,
                "400M denominator remains stable through multiple same-bucket packets");
        ExpEstimateSupport.disconnect();

        CountingStore store355 = new CountingStore(exactRecord(LEVEL_355M, TOTAL_355M));
        ExpEstimateSupport.setStore(store355);
        ExpEstimateSupport.snapshot("test", 2, LEVEL_355M, 320);
        check("(113.8M/355.7M)".equals(ExpEstimateSupport.line(320)),
                "cached 355.7M total uses the native numerator before fine data");
        ExpEstimateSupport.disconnect();

        ExpEstimateSupport.setStore(new CountingStore(exactRecord(LEVEL_355M, TOTAL_355M)));
        ExpEstimateSupport.snapshot("test", 2, LEVEL_355M, 0);
        feedTo(LEVEL_355M, TOTAL_355M, 113866684L);
        check("32,012%".equals(ExpEstimateSupport.percentage(320)),
                "355.7M cached total also resolves the 32.012 percent phase");
        check("(113.9M/355.7M)".equals(ExpEstimateSupport.line(320)),
                "fine 32.012 percent drives the 355.7M numerator");
        check(ExpEstimateSupport.estimateLow() == TOTAL_355M
                && ExpEstimateSupport.estimateHigh() == TOTAL_355M,
                "fine numerator does not alter the 355.7M denominator");

        ExpEstimateSupport.gain("test", 2, LEVEL_355M, 320, -1);
        check("32,0%".equals(ExpEstimateSupport.percentage(320)),
                "loss resets the fine percentage to native progress");
        check("(113.8M/355.7M)".equals(ExpEstimateSupport.line(320)),
                "loss reset also restores the coarse numerator");
        ExpEstimateSupport.disconnect();
        check("32,0%".equals(ExpEstimateSupport.percentage(320)),
                "disconnect clears the fine interpolation state");
        System.out.println("ExpPercentIntegrationHostTest PASS checks=" + checks);
    }

    private static void checkInterpolationLifecycle() throws Exception {
        ExpEstimateSupport.setStore(null);
        ExpEstimateSupport.snapshot("test", 1, 70, 374);
        check("37,4%".equals(ExpEstimateSupport.percentage(374)), "unknown total stays coarse");
        ExpEstimateSupport.gain("test", 1, 70, 374, 1000);
        check("37,4%".equals(ExpEstimateSupport.percentage(374)), "gain alone cannot invent a total");
        ExpEstimateSupport.setStore(new CountingStore(exactRecord(70, 100000000L)));
        ExpEstimateSupport.snapshot("test", 1, 70, 374);
        check("37,4%".equals(ExpEstimateSupport.percentage(374)), "cached total has no saved phase");
        long actual = 37432100L;
        int previous = -1;
        for (int i = 0; i < 500; i++) {
            actual += 1000;
            int coarse = (int)(actual * 1000L / 100000000L);
            ExpEstimateSupport.gain("test", 1, 70, coarse, 1000);
            String value = ExpEstimateSupport.percentage(coarse);
            check(value.matches("[0-9]+,[0-9]{3}%"), "ready live run has three decimal digits");
            int fine = parse(value);
            check(fine >= coarse * 100 && fine < (coarse + 1) * 100, "fine stays inside server bucket");
            check(fine >= previous, "positive live progress never moves backwards");
            check(value == ExpEstimateSupport.percentage(coarse), "unchanged frame reuses cached percentage");
            if (i > 200) check(Math.abs(fine - actual / 1000L) <= 1L,
                    "small packets and exact total resolve the floor phase within one display unit");
            previous = fine;
        }
        int coarse = (int)(actual * 1000L / 100000000L);
        String beforeZero = ExpEstimateSupport.percentage(coarse);
        ExpEstimateSupport.gain("test", 1, 70, coarse, 0);
        check(beforeZero == ExpEstimateSupport.percentage(coarse), "zero gain does not move the display");
        ExpEstimateSupport.gain("test", 1, 70, coarse, -1);
        check(!ExpEstimateSupport.percentage(coarse).matches("[0-9]+,[0-9]{3}%"), "loss discards phase");
        ExpEstimateSupport.gain("test", 1, 70, coarse, 1000);
        check(ExpEstimateSupport.percentage(coarse).matches("[0-9]+,[0-9]{3}%"), "live gain restarts cached-level phase");
        ExpEstimateSupport.disconnect();
        check(!ExpEstimateSupport.percentage(coarse).matches("[0-9]+,[0-9]{3}%"), "disconnect discards fine cache");
        ExpEstimateSupport.snapshot("another", 2, 70, 100);
        check("10,0%".equals(ExpEstimateSupport.percentage(100)), "new identity cannot inherit old phase");
        ExpEstimateSupport.gain("another", 2, 70, 100, 1000);
        check(ExpEstimateSupport.percentage(100).matches("10,[0-9]{3}%"), "shared total works with new local samples");
        ExpEstimateSupport.levelChanged("another", 2, 71, 0);
        check("0,0%".equals(ExpEstimateSupport.percentage(0)), "new level discards total and phase");
        ExpEstimateSupport.gain("another", 2, 71, 1, 1000);
        check("0,1%".equals(ExpEstimateSupport.percentage(1)), "unlearned level stays coarse");
        check("0,0%".equals(ExpEstimateSupport.percentage(-1)), "invalid negative progress is bounded");
        check("99,9%".equals(ExpEstimateSupport.percentage(1000)), "invalid overflow progress is bounded");
        ExpEstimateSupport.disconnect();
    }

    private static void feedTo(int level, long total, long targetEarned) {
        while (ExpEstimateSupport.currentSessionTotal() < targetEarned) {
            long current = ExpEstimateSupport.currentSessionTotal();
            int gain = (int)Math.min(1000L, targetEarned - current);
            long next = current + gain;
            int nativeProgress = (int)(next * 1000L / total);
            ExpEstimateSupport.gain("test", level == LEVEL_400M ? 1 : 2,
                    level, nativeProgress, gain);
            ExpEstimateSupport.percentage(nativeProgress);
        }
        check(ExpEstimateSupport.currentSessionTotal() == targetEarned,
                "clean packet stream reaches the requested earned EXP");
    }

    private static byte[] exactRecord(int level, long total) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x45585031); out.writeByte(2); out.writeByte(1);
        out.writeInt(level); out.writeLong(total); out.writeLong(total);
        out.writeShort(20); out.writeShort(30); out.flush();
        return bytes.toByteArray();
    }
    private static int parse(String value) {
        return Integer.parseInt(value.substring(0, value.length() - 1).replace(",", ""));
    }
    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
    private static final class CountingStore implements ExpEstimateStore {
        final byte[] record;
        int loads;
        int saves;
        CountingStore(byte[] value) { record = value; }
        public byte[] load() { loads++; return record; }
        public boolean save(byte[] bytes) { saves++; return true; }
    }
}
