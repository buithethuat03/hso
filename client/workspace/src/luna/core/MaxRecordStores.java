package luna.core;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/** The MAX suite owns only its prefixed native and mod records. */
public final class MaxRecordStores {
    private static final String PREFIX = "HSO_MAX_";

    private MaxRecordStores() { }

    private static String name(String value) {
        return value.startsWith(PREFIX) ? value : new StringBuffer(PREFIX).append(value).toString();
    }

    public static RecordStore openRecordStore(String value, boolean create) throws RecordStoreException {
        return RecordStore.openRecordStore(name(value), create);
    }

    public static void deleteRecordStore(String value) throws RecordStoreException {
        RecordStore.deleteRecordStore(name(value));
    }

    public static String[] listRecordStores() {
        String[] all = RecordStore.listRecordStores();
        if (all == null) return null;
        int count = 0;
        for (int i = 0; i < all.length; i++) if (all[i].startsWith(PREFIX)) count++;
        if (count == 0) return null;
        String[] result = new String[count];
        int next = 0;
        for (int i = 0; i < all.length; i++) if (all[i].startsWith(PREFIX)) {
            result[next++] = all[i].substring(PREFIX.length());
        }
        return result;
    }
}
