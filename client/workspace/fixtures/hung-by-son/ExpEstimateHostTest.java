import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.util.Random;

import luna.api.ExpEstimateStore;
import luna.features.ExpEstimateSupport;

/** Deterministic host coverage for EXP bounds, cache lifecycle, and display math. */
public final class ExpEstimateHostTest {
    private static final int MAGIC = 0x45585031;
    private static int checks;

    public static void main(String[] args) throws Exception {
        testRandomizedBounds();
        testProgressThresholdAndDuplicateGains();
        testBoundedSampleWindowKeepsAnchor();
        testInconsistentStepDoesNotReuseSessionBounds();
        testPersistenceAndIndependentSessions();
        testSharedLevelCacheAndV1Migration();
        testIdentityIsolationAndLosses();
        testDirtySubsetMergeAndRetry();
        testLevelChangeLifecycle();
        testEnsureSessionRestoreLifecycle();
        testOverflowReset();
        testFormattingAndLongArithmetic();
        testInvalidRowsAndMergeConflict();
        testCorruptionAndFailedStore();
        testRecordBound();
        System.out.println("ExpEstimateHostTest PASS checks=" + checks);
    }

    private static void testRandomizedBounds() {
        Random random = new Random(20261012L);
        for (int mode = 0; mode < 2; mode++) {
            for (int run = 0; run < 40; run++) {
                long total = 20000000L + (long)random.nextInt(180000001);
                long start = total / 20L + (long)random.nextInt((int)(total / 10L));
                long earned = 0L;
                int level = 30 + run;
                MemoryStore memory = new MemoryStore();
                ExpEstimateSupport.setStore(memory);
                ExpEstimateSupport.snapshot("floor-or-round", run, level,
                        progress(start, total, mode));
                for (int packet = 0; packet < 400; packet++) {
                    int gain = 4000 + random.nextInt(24001);
                    if (start + earned + gain >= total * 9L / 10L) break;
                    earned += gain;
                    int q = progress(start + earned, total, mode);
                    ExpEstimateSupport.gain("floor-or-round", run, level, q, gain);
                    if (ExpEstimateSupport.isReady()) {
                        check(ExpEstimateSupport.estimateLow() <= total,
                                "true total stays above the conservative lower bound");
                        check(ExpEstimateSupport.estimateHigh() >= total,
                                "true total stays below the conservative upper bound");
                    }
                    if (ExpEstimateSupport.cleanProgressSpan() >= 50) break;
                }
                check(ExpEstimateSupport.isReady(), "random clean segment becomes ready");
                check(ExpEstimateSupport.estimateLow() <= total
                        && total <= ExpEstimateSupport.estimateHigh(),
                        "random floor/round total is contained");
            }
        }
        ExpEstimateSupport.disconnect();
    }

    private static void testProgressThresholdAndDuplicateGains() {
        MemoryStore memory = new MemoryStore();
        ExpEstimateSupport.setStore(memory);
        ExpEstimateSupport.snapshot("threshold", 11, 80, 100);
        for (int i = 1; i <= 9; i++) {
            ExpEstimateSupport.gain("threshold", 11, 80, 100 + i, 100000);
        }
        check(ExpEstimateSupport.cleanProgressSpan() == 9,
                "nine tenths of clean progress are tracked");
        check(!ExpEstimateSupport.isReady(), "estimate waits for one full percentage point");
        ExpEstimateSupport.gain("threshold", 11, 80, 110, 100000);
        check(ExpEstimateSupport.isReady(), "estimate becomes ready at ten tenths");
        check(ExpEstimateSupport.estimateLow() <= 100000000L
                && ExpEstimateSupport.estimateHigh() >= 100000000L,
                "threshold example contains its true total");

        ExpEstimateSupport.snapshot("duplicate", 12, 80, 200);
        ExpEstimateSupport.gain("duplicate", 12, 80, 200, 12345);
        ExpEstimateSupport.gain("duplicate", 12, 80, 200, 12345);
        check(ExpEstimateSupport.sampleCount() == 3,
                "equal progress and equal gain packets remain separate samples");
        check(ExpEstimateSupport.currentSessionTotal() == 24690L,
                "duplicate gain amounts are both counted");

        ExpEstimateSupport.gain("duplicate", 12, 80, 200, 0);
        check(ExpEstimateSupport.sampleCount() == 3
                && ExpEstimateSupport.currentSessionTotal() == 24690L,
                "zero-reward packet at unchanged progress is ignored without losing clean samples");
        ExpEstimateSupport.gain("duplicate", 12, 80, 201, 0);
        check(ExpEstimateSupport.sampleCount() == 1
                && ExpEstimateSupport.currentSessionTotal() == 0L,
                "zero gain with changed progress resets the unobserved segment");
        ExpEstimateSupport.disconnect();
    }

    private static void testBoundedSampleWindowKeepsAnchor() throws Exception {
        MemoryStore memory = new MemoryStore();
        ExpEstimateSupport.setStore(memory);
        ExpEstimateSupport.snapshot("window", 8, 81, 100);
        for (int i = 1; i <= 80; i++) {
            ExpEstimateSupport.gain("window", 8, 81, 100 + i, 100000);
        }
        Field progressField = ExpEstimateSupport.class.getDeclaredField("sampleProgress");
        progressField.setAccessible(true);
        int[] samples = (int[])progressField.get(null);
        check(ExpEstimateSupport.sampleCount() == 64,
                "live pairwise sample window stays bounded");
        check(samples[0] == 100 && samples[63] == 180,
                "bounded sample window retains its original anchor and newest sample");
        check(ExpEstimateSupport.isReady(),
                "pairwise estimate remains ready after the sample window rolls");
        ExpEstimateSupport.disconnect();
    }

    private static void testInconsistentStepDoesNotReuseSessionBounds() {
        MemoryStore memory = new MemoryStore();
        ExpEstimateSupport.setStore(memory);
        ExpEstimateSupport.snapshot("inconsistent", 4, 60, 100);
        for (int i = 1; i <= 10; i++) {
            ExpEstimateSupport.gain("inconsistent", 4, 60, 100 + i, 100000);
        }
        check(ExpEstimateSupport.isReady(), "clean segment reaches a bounded estimate");
        long cleanLow = ExpEstimateSupport.estimateLow();
        long cleanHigh = ExpEstimateSupport.estimateHigh();
        String cleanLine = ExpEstimateSupport.line(110);
        check(cleanLine == ExpEstimateSupport.line(110),
                "unchanged progress and bounds return the same line object");

        ExpEstimateSupport.gain("inconsistent", 4, 60, 900, 1);
        check(ExpEstimateSupport.currentSessionTotal() == 0L
                && ExpEstimateSupport.cleanProgressSpan() == 0,
                "a contradictory sample discards the entire live segment");
        check(ExpEstimateSupport.estimateLow() == cleanLow
                && ExpEstimateSupport.estimateHigh() == cleanHigh,
                "contradictory packet cannot alter the prior clean cached bounds");
        check(ExpEstimateSupport.line(900).startsWith("(")
                && ExpEstimateSupport.line(900).endsWith(")"),
                "only the prior clean cache remains displayable after reset");
        ExpEstimateSupport.disconnect();
    }

    private static void testPersistenceAndIndependentSessions() throws Exception {
        MemoryStore memory = new MemoryStore();
        ExpEstimateSupport.setStore(memory);
        learnSession("persist", 5, 70, 100000000L, 7000000L, 10000);
        long firstLow = ExpEstimateSupport.estimateLow();
        long firstHigh = ExpEstimateSupport.estimateHigh();
        check(memory.data != null && memory.saves > 0,
                "first ready estimate is saved immediately");
        ExpEstimateSupport.disconnect();

        ExpEstimateSupport.setStore(memory);
        ExpEstimateSupport.snapshot("persist", 5, 70,
                progress(31000000L, 100000000L, 0));
        check(ExpEstimateSupport.isReady(), "matching saved bounds load at login");
        check(ExpEstimateSupport.line(310) != null
                && ExpEstimateSupport.line(310).startsWith("(")
                && ExpEstimateSupport.line(310).endsWith(")"),
                "reloaded estimate renders in parentheses");
        learnFromCurrent("persist", 5, 70, 100000000L, 31000000L, 10000, 200);
        check(ExpEstimateSupport.estimateLow() >= firstLow,
                "independent clean session tightens or preserves lower bound");
        check(ExpEstimateSupport.estimateHigh() <= firstHigh,
                "independent clean session tightens or preserves upper bound");
        check(ExpEstimateSupport.estimateLow() <= 100000000L
                && ExpEstimateSupport.estimateHigh() >= 100000000L,
                "merged independent bounds retain true total");
        ExpEstimateSupport.disconnect();

        MemoryStore stale = new MemoryStore();
        stale.data = record("conflict", 8, 77, 230000000L, 270000000L, 30, 50);
        ExpEstimateSupport.setStore(stale);
        ExpEstimateSupport.snapshot("conflict", 8, 77, progress(4000000L, 100000000L, 0));
        check(ExpEstimateSupport.isReady(), "previous saved estimate is visible");
        learnFromCurrent("conflict", 8, 77, 100000000L, 4000000L, 10000, 60);
        check(ExpEstimateSupport.estimateHigh() < 200000000L,
                "a clean incompatible session replaces stale cached bounds");
        check(ExpEstimateSupport.estimateLow() <= 100000000L
                && ExpEstimateSupport.estimateHigh() >= 100000000L,
                "replacement keeps the fresh true total");
        ExpEstimateSupport.disconnect();
    }

    private static void testIdentityIsolationAndLosses() {
        MemoryStore memory = new MemoryStore();
        ExpEstimateSupport.setStore(memory);
        learnSession("server-a", 1, 20, 80000000L, 5000000L, 10000);
        check(ExpEstimateSupport.isReady(), "seed identity has an estimate");
        ExpEstimateSupport.snapshot("server-a", 2, 20, 50);
        check(ExpEstimateSupport.isReady(), "same-level estimate is shared across characters");
        check(ExpEstimateSupport.currentSessionTotal() == 0L,
                "identity change keeps the cache but starts a fresh live sum");
        ExpEstimateSupport.snapshot("server-b", 1, 20, 50);
        check(ExpEstimateSupport.isReady(), "same-level estimate is shared across servers");
        ExpEstimateSupport.snapshot("server-a", 1, 21, 50);
        check(!ExpEstimateSupport.isReady(), "level cache entries are isolated");

        ExpEstimateSupport.disconnect();
        ExpEstimateSupport.gain("server-a", 1, 20, 51, 500000);
        check(ExpEstimateSupport.currentSessionTotal() == 0L
                && ExpEstimateSupport.sampleCount() == 1,
                "first gain after disconnect only rebases progress");
        ExpEstimateSupport.gain("server-a", 1, 20, 52, 100000);
        check(ExpEstimateSupport.currentSessionTotal() == 100000L,
                "next clean gain starts a new live sum");

        ExpEstimateSupport.gain("server-a", 1, 20, 10, 100000);
        check(ExpEstimateSupport.currentSessionTotal() == 0L
                && ExpEstimateSupport.cleanProgressSpan() == 0,
                "progress loss discards the live segment");
        ExpEstimateSupport.gain("server-a", 1, 20, 11, -1);
        check(ExpEstimateSupport.currentSessionTotal() == 0L
                && ExpEstimateSupport.sampleCount() == 1,
                "negative gain rebases without contaminating samples");
        ExpEstimateSupport.gain("server-a", 1, 21, 20, 999999);
        check(ExpEstimateSupport.currentSessionTotal() == 0L,
                "level change skips the crossing packet");
        ExpEstimateSupport.gain("server-a", 1, 21, 1000, 1);
        check(ExpEstimateSupport.sampleCount() == 0,
                "invalid progress clears the live baseline");
        ExpEstimateSupport.disconnect();
    }

    private static void testFormattingAndLongArithmetic() throws Exception {
        MemoryStore memory = new MemoryStore();
        ExpEstimateSupport.setStore(memory);
        memory.data = record("format", 9, 5, 100000000L, 100000000L, 20, 30);
        ExpEstimateSupport.snapshot("format", 9, 5, 374);
        String initial = ExpEstimateSupport.line(374);
        check("(37.4M/100M)".equals(initial),
                "M formatting rounds to tenths, omits .0, and has no suffix");
        check(initial == ExpEstimateSupport.line(374),
                "repeated HUD paints reuse the cached line object");
        String nextProgress = ExpEstimateSupport.line(375);
        check(nextProgress != initial,
                "a different progress value builds a separately keyed line");

        memory.data = record("format", 9, 5, 200000000L, 200000000L, 20, 30);
        ExpEstimateSupport.snapshot("format", 9, 5, 374);
        check("(74.8M/200M)".equals(ExpEstimateSupport.line(374))
                && initial != ExpEstimateSupport.line(374),
                "changed estimate bounds invalidate the old line object");

        memory.data = record("format", 9, 5, 100000L, 100000L, 20, 30);
        ExpEstimateSupport.snapshot("format", 9, 5, 500);
        check("(50K/100K)".equals(ExpEstimateSupport.line(500)),
                "K formatting rounds to tenths and omits .0");

        memory.data = record("format", 9, 5, 1000000000L, 1000000000L, 20, 30);
        ExpEstimateSupport.snapshot("format", 9, 5, 500);
        check("(500M/1000M)".equals(ExpEstimateSupport.line(500)),
                "billions remain expressed as M");

        memory.data = record("format", 9, 5, 1000951L, 1000951L, 20, 30);
        ExpEstimateSupport.snapshot("format", 9, 5, 999);
        check("(1M/1M)".equals(ExpEstimateSupport.line(999)),
                "999.95K carries to 1M");

        memory.data = record("format", 9, 5, Long.MAX_VALUE,
                Long.MAX_VALUE, 20, 30);
        ExpEstimateSupport.snapshot("format", 9, 5, 999);
        String huge = ExpEstimateSupport.line(999);
        check(huge.startsWith("(") && huge.endsWith(")") && huge.indexOf('-') < 0,
                "progress multiplication and formatting remain safe near long limit");
        ExpEstimateSupport.disconnect();
    }

    private static void testOverflowReset() throws Exception {
        MemoryStore memory = new MemoryStore();
        ExpEstimateSupport.setStore(memory);
        ExpEstimateSupport.snapshot("overflow", 2, 9, 100);
        Field total = ExpEstimateSupport.class.getDeclaredField("sessionTotal");
        total.setAccessible(true);
        total.setLong(null, Long.MAX_VALUE - 5L);
        ExpEstimateSupport.gain("overflow", 2, 9, 100, 10);
        check(ExpEstimateSupport.currentSessionTotal() == 0L
                && ExpEstimateSupport.sampleCount() == 1,
                "cumulative EXP addition overflow resets the clean segment");

        ExpEstimateSupport.snapshot("overflow", 2, 9, 100);
        total.setLong(null, Long.MAX_VALUE / 1000L + 1L);
        ExpEstimateSupport.gain("overflow", 2, 9, 100, 1);
        check(ExpEstimateSupport.currentSessionTotal() == 0L
                && ExpEstimateSupport.sampleCount() == 1,
                "unrepresentable pair interval resets without wrapping arithmetic");
        ExpEstimateSupport.disconnect();
    }

    private static void testLevelChangeLifecycle() throws Exception {
        MemoryStore memory = new MemoryStore();
        memory.data = record("level-change", 14, 71,
                100000000L, 100000000L, 20, 30);
        ExpEstimateSupport.setStore(memory);
        ExpEstimateSupport.snapshot("level-change", 14, 70, 300);
        ExpEstimateSupport.gain("level-change", 14, 70, 301, 45678);
        long liveTotal = ExpEstimateSupport.currentSessionTotal();
        int liveSpan = ExpEstimateSupport.cleanProgressSpan();
        int loadsBeforeSameLevel = memory.loads;
        ExpEstimateSupport.levelChanged("level-change", 14, 70, 302);
        check(ExpEstimateSupport.currentSessionTotal() == liveTotal
                && ExpEstimateSupport.cleanProgressSpan() == liveSpan,
                "same-level notification preserves the clean live segment");
        check(memory.loads == loadsBeforeSameLevel,
                "same-level notification does not reload RMS");

        int loadsBeforeChange = memory.loads;
        ExpEstimateSupport.levelChanged("level-change", 14, 71, 40);
        check(memory.loads == loadsBeforeChange + 1,
                "changed active key loads its matching saved record once");
        check(ExpEstimateSupport.currentSessionTotal() == 0L
                && ExpEstimateSupport.sampleCount() == 1,
                "changed level starts a fresh baseline");
        check(ExpEstimateSupport.isReady()
                && ExpEstimateSupport.estimateLow() == 100000000L,
                "changed level displays only its matching cached bounds");

        ExpEstimateSupport.disconnect();
        int loadsWhileInactive = memory.loads;
        ExpEstimateSupport.levelChanged("level-change", 14, 72, 20);
        check(memory.loads == loadsWhileInactive && ExpEstimateSupport.sampleCount() == 0,
                "inactive level notification waits for the designated snapshot");
        ExpEstimateSupport.disconnect();
    }

    private static void testEnsureSessionRestoreLifecycle() throws Exception {
        MemoryStore memory = new MemoryStore();
        memory.data = record("restore", 31, 72,
                100000000L, 100000000L, 20, 40);
        ExpEstimateSupport.setStore(memory);

        int initialLoads = memory.loads;
        ExpEstimateSupport.snapshot("restore", 0, 72, 40);
        check(memory.loads == initialLoads + 1 && ExpEstimateSupport.isReady(),
                "partial login snapshot restores the shared level record even with ID zero");
        ExpEstimateSupport.levelChanged("restore", 31, 72, 40);
        check(memory.loads == initialLoads + 2 && ExpEstimateSupport.isReady(),
                "stable character identity starts a fresh session and keeps the level result");
        check(ExpEstimateSupport.sampleCount() == 1
                && ExpEstimateSupport.currentSessionTotal() == 0L,
                "restored key starts a fresh live baseline");

        ExpEstimateSupport.gain("restore", 31, 72, 41, 10000);
        long liveTotal = ExpEstimateSupport.currentSessionTotal();
        int loadsBeforeSteadyHud = memory.loads;
        ExpEstimateSupport.ensureSession("restore", 31, 72, 42);
        check(memory.loads == loadsBeforeSteadyHud
                && ExpEstimateSupport.currentSessionTotal() == liveTotal
                && ExpEstimateSupport.cleanProgressSpan() == 1,
                "steady same-key HUD ensure neither reloads RMS nor resets samples");

        ExpEstimateSupport.disconnect();
        int loadsBeforeRelogin = memory.loads;
        ExpEstimateSupport.ensureSession("restore", 31, 72, 41);
        check(memory.loads == loadsBeforeRelogin + 1 && ExpEstimateSupport.isReady(),
                "post-disconnect HUD ensure reloads the persisted estimate once");

        ExpEstimateSupport.disconnect();
        ExpEstimateSupport.gain("restore", 31, 72, 41, 50000);
        check(ExpEstimateSupport.currentSessionTotal() == 0L,
                "gain before a restored HUD snapshot only rebases");
        int loadsBeforeFirstPaint = memory.loads;
        ExpEstimateSupport.ensureSession("restore", 31, 72, 42);
        check(memory.loads == loadsBeforeFirstPaint
                && ExpEstimateSupport.currentSessionTotal() == 0L
                && ExpEstimateSupport.isReady(),
                "HUD ensure reuses the already restored level cache without resetting baseline");
        ExpEstimateSupport.gain("restore", 31, 72, 43, 100000);
        check(ExpEstimateSupport.currentSessionTotal() == 100000L,
                "positive gains resume only after ensured baseline is loaded");
        ExpEstimateSupport.disconnect();
    }

    private static void testCorruptionAndFailedStore() {
        MemoryStore memory = new MemoryStore();
        memory.data = new byte[] { 1, 2, 3, 4 };
        ExpEstimateSupport.setStore(memory);
        ExpEstimateSupport.snapshot("broken", 1, 10, 100);
        check("Đang tính toán...".equals(ExpEstimateSupport.line(100)),
                "corrupt persistence data falls back to calculating");

        memory.failSave = true;
        memory.failLoad = true;
        learnFromCurrent("broken", 1, 10, 100000000L, 10000000L, 10000, 20);
        check(ExpEstimateSupport.isReady(), "failed storage does not suppress live result");
        ExpEstimateSupport.disconnect();
        check(ExpEstimateSupport.isReady(), "disconnect flush failure is harmless");

        memory.failSave = false;
        memory.failLoad = false;
        ExpEstimateSupport.snapshot("broken", 1, 10,
                progress(12000000L, 100000000L, 0));
        check(ExpEstimateSupport.isReady(), "repeated storage operations remain safe");
        ExpEstimateSupport.disconnect();
    }

    private static void testSharedLevelCacheAndV1Migration() throws Exception {
        MemoryStore memory = new MemoryStore();
        memory.data = legacyRecords(
                new String[] { "old-server-a", "old-server-b", "other" },
                new int[] { 1, 22, 4 }, new int[] { 33, 33, 34 },
                new long[] { 90000000L, 100000000L, 50000000L },
                new long[] { 110000000L, 120000000L, 52000000L },
                new int[] { 20, 25, 18 }, new int[] { 30, 40, 20 });
        ExpEstimateSupport.setStore(memory);
        ExpEstimateSupport.snapshot("brand-new-server", 0, 33, 15);
        check(ExpEstimateSupport.isReady()
                && ExpEstimateSupport.estimateLow() == 100000000L
                && ExpEstimateSupport.estimateHigh() == 110000000L,
                "overlapping legacy rows migrate and intersect by level only");
        check(memory.data != null && memory.data[4] == 2 && memory.data[5] == 2,
                "first read rewrites the bounded legacy table as v2 level records");

        ExpEstimateSupport.snapshot("another-server", 9999, 33, 700);
        check(ExpEstimateSupport.isReady()
                && ExpEstimateSupport.estimateLow() == 100000000L,
                "migrated estimate displays for an unrelated server and character");
        ExpEstimateSupport.snapshot("another-server", 9999, 34, 700);
        check(ExpEstimateSupport.isReady()
                && ExpEstimateSupport.estimateLow() == 50000000L,
                "migration retains a second level independently");
        ExpEstimateSupport.snapshot("another-server", 9999, 35, 700);
        check(!ExpEstimateSupport.isReady(), "unseen levels remain calculating");
        ExpEstimateSupport.disconnect();
    }

    private static void testDirtySubsetMergeAndRetry() throws Exception {
        MemoryStore memory = new MemoryStore();
        memory.data = levelRecords(
                new int[] { 40, 41 },
                new long[] { 90000000L, 70000000L },
                new long[] { 110000000L, 73000000L },
                new int[] { 20, 20 }, new int[] { 30, 30 });
        ExpEstimateSupport.setStore(memory);
        ExpEstimateSupport.snapshot("s1", 1, 40, 50);
        check(ExpEstimateSupport.isReady(), "initial level cache loads before refinement");

        memory.failSave = true;
        learnFromCurrent("s1", 1, 40, 100000000L, 5000000L, 10000, 60);
        check(ExpEstimateSupport.isReady(), "failed save leaves refined estimate visible");
        int beforeRetry = memory.saves;

        // Simulate another X3 slot committing a third level while our local
        // level-40 refinement is still pending.
        memory.failSave = false;
        memory.data = levelRecords(
                new int[] { 40, 41, 42 },
                new long[] { 90000000L, 70000000L, 60000000L },
                new long[] { 110000000L, 73000000L, 62000000L },
                new int[] { 20, 20, 20 }, new int[] { 30, 30, 30 });
        setStaticLong("lastSaveAttempt", System.currentTimeMillis() - 6000L);
        ExpEstimateSupport.ensureSession("s1", 1, 40, 100);
        check(memory.saves > beforeRetry,
                "HUD ensure retries a throttled dirty save after its interval");
        int[] levels = decodedLevels(memory.data);
        check(contains(levels, 40) && contains(levels, 41) && contains(levels, 42),
                "dirty subset merge preserves unrelated levels committed by another slot");

        // A later failed load cannot erase a valid in-memory level cache.
        memory.failLoad = true;
        ExpEstimateSupport.snapshot("s2", 999, 40, 200);
        check(ExpEstimateSupport.isReady(), "transient load failure retains the memory cache");
        ExpEstimateSupport.disconnect();

        MemoryStore transientLoad = new MemoryStore();
        transientLoad.failLoad = true;
        ExpEstimateSupport.setStore(transientLoad);
        ExpEstimateSupport.snapshot("retry", 7, 53, 100);
        int failedLoadCount = transientLoad.loads;
        ExpEstimateSupport.ensureSession("retry", 7, 53, 101);
        check(transientLoad.loads == failedLoadCount,
                "failed loads are throttled during steady HUD paints");
        transientLoad.failLoad = false;
        transientLoad.data = levelRecord(53, 70000000L, 72000000L, 20, 30);
        setStaticLong("lastLoadAttempt", System.currentTimeMillis() - 6000L);
        ExpEstimateSupport.ensureSession("retry", 7, 53, 101);
        check(transientLoad.loads == failedLoadCount + 1
                && ExpEstimateSupport.isReady(),
                "missing level cache retries a transient load failure after the interval");
        ExpEstimateSupport.disconnect();
    }

    private static void testInvalidRowsAndMergeConflict() throws Exception {
        byte[] legacy = record("old", 12, 54,
                70000000L, 72000000L, 20, 30);
        check(ExpEstimateSupport.mergeStored(null, null) == null,
                "two absent payloads do not create an empty RMS record");
        byte[] migrated = ExpEstimateSupport.mergeStored(legacy, null);
        check(migrated != null && migrated[4] == 2 && migrated[5] == 1,
                "legacy-only RMS data migrates to v2 when the current blob is absent");
        MemoryStore legacyStore = new MemoryStore();
        legacyStore.data = migrated;
        ExpEstimateSupport.setStore(legacyStore);
        ExpEstimateSupport.snapshot("new", 0, 54, 10);
        check(ExpEstimateSupport.isReady(),
                "the migrated legacy-only record remains ready after reload");

        MemoryStore memory = new MemoryStore();
        memory.data = v2WithInvalidFirstRow();
        ExpEstimateSupport.setStore(memory);
        ExpEstimateSupport.snapshot("skip-invalid", 0, 52, 30);
        check(ExpEstimateSupport.isReady()
                && ExpEstimateSupport.estimateLow() == 80000000L,
                "a structurally complete invalid row is skipped while valid rows load");

        byte[] oldRow = levelRecord(52, 80000000L, 90000000L, 20, 30);
        byte[] conflictingRow = levelRecord(52, 120000000L, 130000000L, 25, 40);
        byte[] merged = ExpEstimateSupport.mergeStored(oldRow, conflictingRow);
        memory.data = merged;
        ExpEstimateSupport.snapshot("conflict", 800, 52, 30);
        check(ExpEstimateSupport.estimateLow() == 120000000L
                && ExpEstimateSupport.estimateHigh() == 130000000L,
                "pure store merge lets a clean disjoint incoming interval replace stale data");
        check(ExpEstimateSupport.mergeStored(oldRow, new byte[] { 1, 2, 3 }) == null,
                "broken incoming payload is rejected without wiping valid old rows");

        byte[] repaired = ExpEstimateSupport.mergeStored(
                new byte[] { 1, 2, 3 }, levelRecord(55,
                        60000000L, 62000000L, 20, 30));
        check(repaired != null && repaired[4] == 2 && repaired[5] == 1,
                "valid current data repairs a corrupt existing RMS blob");
        memory.data = repaired;
        ExpEstimateSupport.snapshot("repaired", 88, 55, 20);
        check(ExpEstimateSupport.isReady()
                && ExpEstimateSupport.estimateLow() == 60000000L,
                "repaired current data survives reload and remains usable");
        ExpEstimateSupport.disconnect();
    }

    private static void testRecordBound() throws Exception {
        MemoryStore memory = new MemoryStore();
        ExpEstimateSupport.setStore(memory);
        for (int i = 0; i < 256; i++) {
            String server = "endpoint-" + i;
            int level = i + 1;
            ExpEstimateSupport.snapshot(server, i, level, 0);
            ExpEstimateSupport.gain(server, i, level, 10, 1000000);
        }
        ExpEstimateSupport.disconnect();
        check(memory.data != null && (memory.data[5] & 255) == 255,
                "serialized shared level cache retains up to 255 levels");
    }

    private static void learnSession(String server, int id, int level,
            long total, long start, int gain) {
        ExpEstimateSupport.snapshot(server, id, level, progress(start, total, 0));
        learnFromCurrent(server, id, level, total, start, gain, 60);
    }

    private static void learnFromCurrent(String server, int id, int level,
            long total, long start, int gain, int spanTenths) {
        long earned = 0L;
        int initial = progress(start, total, 0);
        long requiredGain = ((long)spanTenths * total + 999L) / 1000L;
        int packets = (int)((requiredGain + gain - 1L) / gain) + 2;
        if (packets < 1) packets = 1;
        for (int i = 0; i < packets; i++) {
            earned += gain;
            if (start + earned >= total * 9L / 10L) break;
            int q = progress(start + earned, total, 0);
            ExpEstimateSupport.gain(server, id, level, q, gain);
            if (q - initial >= spanTenths) break;
        }
    }

    private static int progress(long earned, long total, int mode) {
        long scaled = earned * 1000L;
        if (mode == 1) scaled += total / 2L;
        return (int)(scaled / total);
    }

    private static byte[] record(String server, int id, int level,
            long low, long high, int samples, int span) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        out.writeByte(1);
        out.writeByte(1);
        out.writeUTF(server);
        out.writeInt(id);
        out.writeInt(level);
        out.writeLong(low);
        out.writeLong(high);
        out.writeShort(samples);
        out.writeShort(span);
        out.flush();
        return bytes.toByteArray();
    }

    private static byte[] legacyRecords(String[] servers, int[] ids, int[] levels,
            long[] lows, long[] highs, int[] samples, int[] spans) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        out.writeByte(1);
        out.writeByte(levels.length);
        for (int i = 0; i < levels.length; i++) {
            out.writeUTF(servers[i]);
            out.writeInt(ids[i]);
            out.writeInt(levels[i]);
            out.writeLong(lows[i]);
            out.writeLong(highs[i]);
            out.writeShort(samples[i]);
            out.writeShort(spans[i]);
        }
        out.flush();
        return bytes.toByteArray();
    }

    private static byte[] levelRecords(int[] levels, long[] lows, long[] highs,
            int[] samples, int[] spans) {
        byte[] merged = null;
        for (int i = 0; i < levels.length; i++) {
            merged = ExpEstimateSupport.mergeStored(merged,
                    levelRecord(levels[i], lows[i], highs[i], samples[i], spans[i]));
        }
        return merged;
    }

    private static byte[] levelRecord(int level, long low, long high,
            int samples, int span) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeByte(2);
            out.writeByte(1);
            out.writeInt(level);
            out.writeLong(low);
            out.writeLong(high);
            out.writeShort(samples);
            out.writeShort(span);
            out.flush();
            return bytes.toByteArray();
        } catch (Exception impossible) {
            throw new RuntimeException(impossible.toString());
        }
    }

    private static byte[] v2WithInvalidFirstRow() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        out.writeByte(2);
        out.writeByte(2);
        out.writeInt(51);
        out.writeLong(0L);
        out.writeLong(0L);
        out.writeShort(0);
        out.writeShort(0);
        out.writeInt(52);
        out.writeLong(80000000L);
        out.writeLong(90000000L);
        out.writeShort(20);
        out.writeShort(30);
        out.flush();
        return bytes.toByteArray();
    }

    private static int[] decodedLevels(byte[] data) {
        if (data == null || data.length < 6 || data[4] != 2) return new int[0];
        int count = data[5] & 255;
        int[] levels = new int[count];
        for (int i = 0; i < count; i++) {
            int offset = 6 + i * 24;
            if (offset + 4 > data.length) return new int[0];
            levels[i] = ((data[offset] & 255) << 24)
                    | ((data[offset + 1] & 255) << 16)
                    | ((data[offset + 2] & 255) << 8)
                    | (data[offset + 3] & 255);
        }
        return levels;
    }

    private static boolean contains(int[] values, int expected) {
        for (int i = 0; i < values.length; i++) if (values[i] == expected) return true;
        return false;
    }

    private static void setStaticLong(String name, long value) throws Exception {
        Field field = ExpEstimateSupport.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(null, value);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class MemoryStore implements ExpEstimateStore {
        byte[] data;
        int saves;
        int loads;
        boolean failSave;
        boolean failLoad;
        public byte[] load() {
            loads++;
            if (failLoad) throw new RuntimeException("simulated transient load failure");
            return data;
        }
        public boolean save(byte[] value) {
            saves++;
            if (failSave) return false;
            byte[] combined = ExpEstimateSupport.mergeStored(data, value);
            if (combined == null) return false;
            data = new byte[combined.length];
            System.arraycopy(combined, 0, data, 0, combined.length);
            return true;
        }
    }
}
