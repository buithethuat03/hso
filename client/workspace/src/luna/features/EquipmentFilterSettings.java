package luna.features;

import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;

/** Independent saved choices for pet equipment and trash sales. */
public final class EquipmentFilterSettings {
    public static final String STORE = "hbs_equipment_filters_v1";
    private static boolean loaded;
    private static int petMask = 15;
    private static int saleMask = 15;
    private static boolean lockedOnly;

    private EquipmentFilterSettings() { }

    public static synchronized int mask(boolean sale) {
        load();
        return sale ? saleMask : petMask;
    }

    public static synchronized boolean lockedOnly() {
        load();
        return lockedOnly;
    }

    public static synchronized boolean toggleGroup(boolean sale, int bit) {
        load();
        if (bit != 1 && bit != 2 && bit != 4 && bit != 8) return false;
        if (sale) saleMask ^= bit;
        else petMask ^= bit;
        return save();
    }

    public static synchronized boolean toggleLockedOnly() {
        load();
        lockedOnly = !lockedOnly;
        return save();
    }

    public static boolean acceptsPet(int slot, int job) {
        return EquipmentFilterPolicy.accepts(mask(false), slot, job);
    }

    public static boolean acceptsSale(int lock, int slot, int job) {
        return EquipmentFilterPolicy.acceptsSale(true, lockedOnly(), lock, mask(true), slot, job);
    }

    public static String groupLabel(boolean sale, int bit) {
        String name = bit == 1 ? "Sức mạnh" : bit == 2 ? "Khéo léo" : bit == 4 ? "Thể lực" : "Tinh thần";
        return new StringBuffer(name).append((mask(sale) & bit) != 0 ? " [x]" : " [ ]").toString();
    }

    public static String lockedLabel() {
        return lockedOnly() ? "Chỉ bán đồ khóa [x]" : "Chỉ bán đồ khóa [ ]";
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE, false);
            int record = first(store);
            if (record < 0) return;
            byte[] data = store.getRecord(record);
            if (data.length != 4 || data[0] != 1 || data[1] < 0 || data[1] > 15
                    || data[2] < 0 || data[2] > 15 || (data[3] != 0 && data[3] != 1)) return;
            petMask = data[1];
            saleMask = data[2];
            lockedOnly = data[3] == 1;
        } catch (Exception ignored) {
            // Missing or invalid records preserve all four groups and allow unlocked sales.
        } finally {
            close(store);
        }
    }

    private static boolean save() {
        RecordStore store = null;
        try {
            byte[] data = new byte[] {1, (byte) petMask, (byte) saleMask, (byte) (lockedOnly ? 1 : 0)};
            store = RecordStore.openRecordStore(STORE, true);
            int record = first(store);
            if (record < 0) store.addRecord(data, 0, data.length);
            else store.setRecord(record, data, 0, data.length);
            return true;
        } catch (Exception failure) {
            return false;
        } finally {
            close(store);
        }
    }

    private static int first(RecordStore store) throws Exception {
        RecordEnumeration records = store.enumerateRecords(null, null, false);
        try { return records.hasNextElement() ? records.nextRecordId() : -1; }
        finally { records.destroy(); }
    }

    private static void close(RecordStore store) {
        if (store != null) try { store.closeRecordStore(); } catch (Exception ignored) { }
    }
}
