import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.microedition.rms.RecordStore;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.util.MemoryRecordStoreManager;

/** Runs the packaged EXP, gold, and inventory-info engines in all three X3 slots. */
public final class HungEconomyX3HostTest {
    private static final String[] SLOT = { "", "b.", "c." };
    private static final String INVENTORY_STORE = "hbs_inventory_info_v1";
    private static int checks;

    private HungEconomyX3HostTest() { }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 2, "candidate-x3.jar microemulator.jar");
        File candidate = new File(args[0]), micro = new File(args[1]);
        check(candidate.isFile() && micro.isFile(), "Candidate X3 and MicroEmulator exist");
        installMemoryRms();

        URLClassLoader loader = candidateLoader(candidate, micro);
        try {
            Class<?>[] exp = loadSlots(loader, "luna.features.ExpRateSupport");
            Class<?>[] gold = loadSlots(loader, "luna.features.GoldRateSupport");
            checkDistinctBuckets(exp, "bucketGain");
            checkDistinctBuckets(gold, "bucketGold");
            testExpIsolation(exp);
            testGoldIsolation(gold);
            for (int slot = 0; slot < 3; slot++) {
                testExp(exp[slot], slot);
                testGold(gold[slot], slot);
            }
            testInventoryInfo(candidate, micro, loader);
        } finally {
            loader.close();
        }
        System.out.println("HungEconomyX3HostTest PASS checks=" + checks);
    }

    private static void installMemoryRms() {
        Common emulator = new Common(null);
        MemoryRecordStoreManager records = new MemoryRecordStoreManager();
        records.init(emulator);
        emulator.setRecordStoreManager(records);
        MIDletBridge.setMicroEmulator(emulator);
    }

    private static URLClassLoader candidateLoader(File candidate, File micro) throws Exception {
        return new URLClassLoader(new URL[] { candidate.toURI().toURL(), micro.toURI().toURL() },
                HungEconomyX3HostTest.class.getClassLoader());
    }

    private static Class<?>[] loadSlots(ClassLoader loader, String name) throws Exception {
        Class<?>[] result = new Class<?>[3];
        for (int slot = 0; slot < 3; slot++) {
            result[slot] = Class.forName(SLOT[slot] + name, true, loader);
            check(result[slot].getClassLoader() == loader,
                    "Slot " + (slot + 1) + " " + name + " loads from the candidate");
        }
        return result;
    }

    private static void checkDistinctBuckets(Class<?>[] types, String fieldName) throws Exception {
        Object[] arrays = new Object[3];
        for (int slot = 0; slot < 3; slot++) {
            Field field = types[slot].getDeclaredField(fieldName);
            field.setAccessible(true);
            arrays[slot] = field.get(null);
            check(java.lang.reflect.Array.getLength(arrays[slot]) == 900,
                    "Slot " + (slot + 1) + " keeps its 900-second window");
        }
        check(arrays[0] != arrays[1] && arrays[1] != arrays[2] && arrays[0] != arrays[2],
                fieldName + " arrays are independent across X3 slots");
    }

    private static void testExpIsolation(Class<?>[] types) throws Exception {
        for (int slot = 0; slot < 3; slot++) {
            types[slot].getMethod("reset").invoke(null);
            expGain(types[slot], 10 * (slot + 1), 0L);
        }
        for (int slot = 0; slot < 3; slot++)
            equal(120L * (slot + 1), expRate(types[slot], 300000L), "EXP slot warmup " + slot);
        expGain(types[0], 50, 300000L);
        equal(720L, expRate(types[0], 300000L), "EXP update changes slot 1 only");
        equal(240L, expRate(types[1], 300000L), "EXP slot 2 remains isolated");
        equal(360L, expRate(types[2], 300000L), "EXP slot 3 remains isolated");
        types[1].getMethod("reset").invoke(null);
        equal(720L, expRate(types[0], 300000L), "EXP reset leaves slot 1 intact");
        equal(360L, expRate(types[2], 300000L), "EXP reset leaves slot 3 intact");
    }

    private static void testExp(Class<?> type, int slot) throws Exception {
        long scale = slot + 1L;
        type.getMethod("reset").invoke(null);
        expGain(type, 100 * (slot + 1), 0L);
        equal(-1L, expRate(type, 299999L), "EXP slot " + slot + " waits five minutes");
        expGain(type, 200 * (slot + 1), 300000L);
        equal(3600L * scale, expRate(type, 300000L), "EXP slot " + slot + " five-minute denominator");
        equal(1800L * scale, expRate(type, 600000L), "EXP slot " + slot + " ten-minute denominator");
        equal(800L * scale, expRate(type, 900000L), "EXP slot " + slot + " fifteen-minute denominator");
        equal(800L * scale, expRate(type, 1199999L), "EXP slot " + slot + " retains gains inside 900 seconds");
        equal(0L, expRate(type, 1200000L), "EXP slot " + slot + " expires gains outside 900 seconds");
        expGain(type, 50 * (slot + 1), 1200000L);
        equal(200L * scale, expRate(type, 1200000L), "EXP slot " + slot + " resumes without another warmup");
        equal(-1L, expRate(type, 1199999L), "EXP slot " + slot + " clock rollback resets its window");
        expGain(type, 60 * (slot + 1), 1199999L);
        equal(-1L, expRate(type, 1499998L), "EXP slot " + slot + " rollback starts a five-minute warmup");
        equal(720L * scale, expRate(type, 1499999L), "EXP slot " + slot + " resumes after rollback warmup");
    }

    private static long expRate(Class<?> type, long now) throws Exception {
        return ((Long)type.getMethod("rate", Long.TYPE).invoke(null, Long.valueOf(now))).longValue();
    }

    private static void expGain(Class<?> type, int amount, long now) throws Exception {
        type.getMethod("gain", Integer.TYPE, Long.TYPE).invoke(null,
                Integer.valueOf(amount), Long.valueOf(now));
    }

    private static void testGoldIsolation(Class<?>[] types) throws Exception {
        for (int slot = 0; slot < 3; slot++) {
            goldDisconnect(types[slot]);
            goldSnapshot(types[slot], "isolation", 100 + slot, 0L);
            goldBalance(types[slot], "isolation", 100 + slot, 1000L + slot * 100L, 0L);
            goldBalance(types[slot], "isolation", 100 + slot,
                    1010L + slot * 100L, 1000L);
        }
        for (int slot = 0; slot < 3; slot++) {
            equal(10L, goldLong(types[slot], "received"), "Gold slot " + slot + " baseline isolation");
            equal(120L, goldRate(types[slot], 301000L), "Gold slot " + slot + " warmup isolation");
        }
        goldBalance(types[0], "isolation", 100, 1110L, 301000L);
        equal(110L, goldLong(types[0], "received"), "Gold receipt changes slot 1 only");
        equal(10L, goldLong(types[1], "received"), "Gold slot 2 remains isolated");
        equal(10L, goldLong(types[2], "received"), "Gold slot 3 remains isolated");
        goldSnapshot(types[1], "new-identity", 101, 301001L);
        equal(-1L, goldLong(types[1], "current"), "Gold identity reset applies to slot 2 only");
        equal(1110L, goldLong(types[0], "current"), "Gold slot 1 survives slot 2 reset");
        equal(1210L, goldLong(types[2], "current"), "Gold slot 3 survives slot 2 reset");
    }

    private static void testGold(Class<?> type, int slot) throws Exception {
        int id = -32768 + slot;
        String server = "economy-" + slot;
        goldDisconnect(type);
        equal(-1L, goldLong(type, "current"), "Gold slot " + slot + " disconnect clears wallet");
        goldSnapshot(type, server, id, 0L);
        goldBalance(type, server, id, 1000L, 0L);
        equal(0L, goldLong(type, "received"), "Gold slot " + slot + " baseline is not a receipt");
        goldBalance(type, server, id, 900L, 1000L);
        equal(0L, goldLong(type, "received"), "Gold slot " + slot + " spending is ignored");
        goldBalance(type, server, id, 940L, 2000L);
        equal(40L, goldLong(type, "received"), "Gold slot " + slot + " counts positive receipts");
        equal(-1L, goldRate(type, 301999L), "Gold slot " + slot + " waits five minutes");
        goldBalance(type, server, id, 1000L, 302000L);
        equal(1200L, goldRate(type, 302000L), "Gold slot " + slot + " five-minute denominator");
        equal(600L, goldRate(type, 602000L), "Gold slot " + slot + " ten-minute denominator");
        equal(240L, goldRate(type, 902000L), "Gold slot " + slot + " fifteen-minute denominator");
        equal(240L, goldRate(type, 1201999L), "Gold slot " + slot + " retains gains inside 900 seconds");
        equal(0L, goldRate(type, 1202000L), "Gold slot " + slot + " expires gains outside 900 seconds");
        goldSnapshot(type, server, id, 1202000L);
        equal(100L, goldLong(type, "received"), "Gold slot " + slot + " same identity preserves lifetime total");
        goldBalance(type, server, id, 1030L, 1202000L);
        equal(130L, goldLong(type, "received"), "Gold slot " + slot + " idle resume adds one receipt");
        equal(120L, goldRate(type, 1202000L), "Gold slot " + slot + " resumes without warmup");

        goldBalance(type, "wrong-server", id, 5000L, 1202001L);
        equal(1030L, goldLong(type, "current"), "Gold slot " + slot + " ignores a mismatched server");
        goldSnapshot(type, "new-server", id, 1202001L);
        equal(-1L, goldLong(type, "current"), "Gold slot " + slot + " server change clears baseline");
        equal(-1L, goldLong(type, "received"), "Gold slot " + slot + " server change clears lifetime total");
        equal(-1L, goldRate(type, 1202001L), "Gold slot " + slot + " server change clears rate");
        goldBalance(type, "new-server", id, 500L, 1202002L);
        goldBalance(type, "new-server", id, 520L, 1202003L);
        equal(20L, goldLong(type, "received"), "Gold slot " + slot + " new identity starts clean");
        goldSnapshot(type, "new-server", id + 1, 1202004L);
        equal(-1L, goldLong(type, "current"), "Gold slot " + slot + " character change clears baseline");

        goldDisconnect(type);
        goldSnapshot(type, server, id, 0L);
        goldBalance(type, server, id, 100L, 0L);
        goldBalance(type, server, id, 150L, 1000L);
        equal(600L, goldRate(type, 301000L), "Gold slot " + slot + " rollback setup warms");
        equal(-1L, goldRate(type, 999L), "Gold slot " + slot + " clock rollback restarts rate");
        equal(150L, goldLong(type, "current"), "Gold slot " + slot + " rollback preserves wallet");
        equal(50L, goldLong(type, "received"), "Gold slot " + slot + " rollback preserves lifetime total");
        goldBalance(type, server, id, 150L, 1000L);
        goldBalance(type, server, id, 175L, 1001L);
        equal(-1L, goldRate(type, 301000L), "Gold slot " + slot + " new receipt starts rollback warmup");
        equal(300L, goldRate(type, 301001L), "Gold slot " + slot + " rollback rate restarts cleanly");
        equal(75L, goldLong(type, "received"), "Gold slot " + slot + " post-rollback receipt stays cumulative");
        goldDisconnect(type);
        goldBalance(type, server, id, 999L, 400000L);
        equal(-1L, goldLong(type, "current"), "Gold slot " + slot + " ignores stale post-disconnect balance");
    }

    private static long goldLong(Class<?> type, String method) throws Exception {
        return ((Long)type.getMethod(method).invoke(null)).longValue();
    }

    private static long goldRate(Class<?> type, long now) throws Exception {
        return ((Long)type.getMethod("rate", Long.TYPE).invoke(null, Long.valueOf(now))).longValue();
    }

    private static void goldSnapshot(Class<?> type, String server, int id, long now) throws Exception {
        type.getMethod("snapshot", String.class, Integer.TYPE, Long.TYPE).invoke(null,
                server, Integer.valueOf(id), Long.valueOf(now));
    }

    private static void goldBalance(Class<?> type, String server, int id, long gold, long now) throws Exception {
        type.getMethod("balance", String.class, Integer.TYPE, Long.TYPE, Long.TYPE).invoke(null,
                server, Integer.valueOf(id), Long.valueOf(gold), Long.valueOf(now));
    }

    private static void goldDisconnect(Class<?> type) throws Exception {
        type.getMethod("disconnect").invoke(null);
    }

    private static void testInventoryInfo(File candidate, File micro, ClassLoader loader) throws Exception {
        InventorySlot[] slots = new InventorySlot[3];
        for (int slot = 0; slot < 3; slot++) {
            slots[slot] = new InventorySlot(loader, slot);
            equal(1, mode(slots[slot].support), "Inventory slot " + slot + " defaults ON");
            check(slots[slot].nativeFlag.getBoolean(null), "Inventory slot " + slot + " native flag defaults ON");
        }
        for (int slot = 0; slot < 3; slot++) {
            int expected = 1;
            for (int step = 0; step <= slot; step++) {
                check(Boolean.TRUE.equals(slots[slot].action.invoke(slots[slot].actionTarget, -77, 0)),
                        "Inventory slot " + slot + " handles native cycle action");
                expected = (expected + 1) % 3;
                equal(expected, mode(slots[slot].support), "Inventory slot " + slot + " cycles state");
            }
        }
        int[] saved = { 2, 0, 1 };
        for (int slot = 0; slot < 3; slot++) {
            check(slots[slot].nativeFlag.getBoolean(null) == (saved[slot] == 1),
                    "Inventory slot " + slot + " applies its native row flag");
            equal(saved[slot], readInventoryRecord((char)('a' + slot) + INVENTORY_STORE),
                    "Inventory slot " + slot + " writes its own RMS prefix");
        }

        URLClassLoader restored = candidateLoader(candidate, micro);
        try {
            for (int slot = 0; slot < 3; slot++) {
                InventorySlot value = new InventorySlot(restored, slot);
                equal(saved[slot], mode(value.support), "Inventory slot " + slot + " restores from RMS");
                check(value.nativeFlag.getBoolean(null) == (saved[slot] == 1),
                        "Inventory slot " + slot + " restores its own native flag");
            }
        } finally {
            restored.close();
        }
    }

    private static final class InventorySlot {
        final Class<?> support, owner;
        final Field nativeFlag;
        final Method action;

        InventorySlot(ClassLoader loader, int slot) throws Exception {
            String prefix = SLOT[slot];
            support = Class.forName(prefix + "HungInventoryInfo", true, loader);
            owner = Class.forName(prefix + nativeName("lliiliii"), true, loader);
            nativeFlag = owner.getField(nativeName("lilliiilill"));
            Object module = owner.getConstructor().newInstance();
            action = owner.getMethod("onAction", Integer.TYPE, Integer.TYPE);
            actionTarget = module;
        }

        final Object actionTarget;
    }

    private static String nativeName(String suffix) {
        StringBuilder name = new StringBuilder();
        while (name.length() + suffix.length() < 100) name.append('i');
        return name.append(suffix).toString();
    }

    private static int mode(Class<?> support) throws Exception {
        return ((Integer)support.getMethod("mode").invoke(null)).intValue();
    }

    private static int readInventoryRecord(String physicalName) throws Exception {
        RecordStore store = RecordStore.openRecordStore(physicalName, false);
        try {
            check(store.getNumRecords() == 1, "Inventory RMS has one saved record: " + physicalName);
            byte[] value = store.getRecord(1);
            check(value.length == 1, "Inventory RMS record is one byte: " + physicalName);
            return value[0];
        } finally {
            store.closeRecordStore();
        }
    }

    private static void equal(long expected, long actual, String message) {
        check(expected == actual, message + ": expected " + expected + ", got " + actual);
    }
}
