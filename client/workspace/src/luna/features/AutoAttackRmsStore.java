package luna.features;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordEnumeration;
import luna.api.AutoAttackSettings;

/** A direct MIDP store outside native account/Farm cleanup namespaces. */
public final class AutoAttackRmsStore implements AutoAttackSettings {
    public static final String NAME = "hbs_auto_attack_v1";

    public void load(int[] settings) {
        settings[0] = 0;
        settings[1] = 650;
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(NAME, false);
            int record = firstRecord(store);
            if (record < 0) return;
            byte[] data = store.getRecord(record);
            if (data.length != 4 || data[0] != 1 || (data[1] != 0 && data[1] != 1)) return;
            int delay = ((data[2] & 255) << 8) | (data[3] & 255);
            if (delay < 1 || delay > 1000) return;
            settings[0] = data[1];
            settings[1] = delay;
        } catch (Exception ignored) {
            // A missing or unreadable record leaves the defaults intact.
        } finally {
            if (store != null) try { store.closeRecordStore(); } catch (Exception ignored) { }
        }
    }

    public void save(boolean players, int delay) {
        if (delay < 1 || delay > 1000) throw new IllegalArgumentException("delay");
        RecordStore store = null;
        byte[] data = new byte[] {1, (byte) (players ? 1 : 0), (byte) (delay >>> 8), (byte) delay};
        try {
            store = RecordStore.openRecordStore(NAME, true);
            int record = firstRecord(store);
            if (record < 0) store.addRecord(data, 0, data.length);
            else store.setRecord(record, data, 0, data.length);
        } catch (Exception failure) {
            throw new RuntimeException("Auto attack settings could not be saved");
        } finally {
            if (store != null) try { store.closeRecordStore(); } catch (Exception ignored) { }
        }
    }

    private static int firstRecord(RecordStore store) throws Exception {
        RecordEnumeration records = store.enumerateRecords(null, null, false);
        try { return records.hasNextElement() ? records.nextRecordId() : -1; }
        finally { records.destroy(); }
    }
}
