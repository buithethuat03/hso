public final class HSOSKY10SkillSupport {
    private static final int PAGES = 3;
    private static final int OLD_SLOTS = 5;
    private static final int NEW_SLOTS = 10;
    private static final int SLOT_BYTES = 4;

    private HSOSKY10SkillSupport() {
    }

    public static byte[] expandHotbar(byte[] data) {
        if (data == null || data.length != PAGES * OLD_SLOTS * SLOT_BYTES) {
            return data;
        }

        byte[] expanded = new byte[PAGES * NEW_SLOTS * SLOT_BYTES];
        for (int page = 0; page < PAGES; page++) {
            int oldOffset = page * OLD_SLOTS * SLOT_BYTES;
            int newOffset = page * NEW_SLOTS * SLOT_BYTES;
            System.arraycopy(data, oldOffset, expanded, newOffset, OLD_SLOTS * SLOT_BYTES);

            for (int slot = OLD_SLOTS; slot < NEW_SLOTS; slot++) {
                // short id = 0, byte type = -1 (empty), byte subtype = 0
                expanded[newOffset + slot * SLOT_BYTES + 2] = (byte) -1;
            }
        }
        return expanded;
    }
}
