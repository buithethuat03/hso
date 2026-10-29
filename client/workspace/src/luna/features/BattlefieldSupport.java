package luna.features;

import java.io.DataInputStream;
import luna.api.BattlefieldAccess;

/** Session-only FIFO of player names killed by the local player's final hit. */
public final class BattlefieldSupport {
    private static final Object LOCK = new Object();
    private static final int FIFO_LIMIT = 10;
    private static final int MAX_VICTIMS = 64;
    private static final int MAX_EFFECTS = 32;
    private static final String EXCLUDED_NAME = "Xà nữ";

    private static final String[] FIFO = new String[FIFO_LIMIT];
    private static int fifoCount;
    private static boolean enabled;
    private static boolean hasPlayer;
    private static int playerId;
    private static String playerName = "";

    private BattlefieldSupport() {
    }

    public static boolean enabled() {
        synchronized (LOCK) { return enabled; }
    }

    public static void setEnabled(boolean value) {
        synchronized (LOCK) {
            enabled = value;
            if (!value) clearFifoLocked();
        }
    }

    public static void toggle() {
        synchronized (LOCK) {
            enabled = !enabled;
            if (!enabled) clearFifoLocked();
        }
    }

    public static String label() {
        synchronized (LOCK) {
            return enabled ? "Mode chiến trường: [BẬT]" : "Mode chiến trường: [TẮT]";
        }
    }

    /**
     * Observe an inbound combat event. The native event's target type is zero
     * for players; type one is the monster combat path. The input is restored
     * so the native receiver can consume the same packet afterward.
     */
    public static void onCombat(BattlefieldAccess access, DataInputStream input,
            int targetType) {
        if (access == null || input == null || targetType != 0
                || !input.markSupported()) return;

        synchronized (LOCK) {
            if (!enabled) return;
            input.mark(65536);
            try {
                int attackerId = input.readShort() & 65535;
                input.readByte(); // skill
                int count = input.readUnsignedByte();
                if (count <= 0 || count > MAX_VICTIMS) return;

                int[] ids = new int[count];
                int[] damages = new int[count];
                int[] health = new int[count];
                for (int i = 0; i < count; i++) {
                    ids[i] = input.readUnsignedShort();
                    damages[i] = input.readInt();
                    health[i] = input.readInt();
                    int effects = input.readUnsignedByte();
                    if (damages[i] < 0 || health[i] < 0 || effects > MAX_EFFECTS) return;
                    for (int j = 0; j < effects; j++) {
                        input.readByte();
                        input.readInt();
                    }
                }
                input.readInt(); // attacker HP
                input.readInt(); // attacker MP
                input.readByte(); // auxiliary type
                input.readInt(); // auxiliary damage
                if (input.available() != 0) return;

                Object player = access.player();
                if (player == null) return;
                int localId = access.id(player) & 65535;
                String localName = trim(access.name(player));
                if (localName.length() == 0) return;

                // A valid packet can reveal a new character session. Maps and
                // zones do not participate in this key, so they retain FIFO.
                observePlayerLocked(localId, localName);
                if (attackerId != localId) return;

                String[] pending = new String[count];
                int pendingCount = 0;
                for (int i = 0; i < count; i++) {
                    if (damages[i] <= 0 || health[i] != 0 || ids[i] == localId) continue;
                    Object victim = access.find(ids[i], targetType);
                    if (victim == null || (access.id(victim) & 65535) != ids[i]
                            || access.type(victim) != 0 || access.hp(victim) <= 0) continue;
                    int level = access.level(victim);
                    if (level <= 0 || level == 40) continue;
                    String name = trim(access.name(victim));
                    if (name.length() == 0 || EXCLUDED_NAME.equalsIgnoreCase(name)) continue;
                    boolean seen = false;
                    for (int j = 0; j < pendingCount; j++) {
                        if (pending[j].equals(name)) { seen = true; break; }
                    }
                    if (!seen) pending[pendingCount++] = name;
                }

                // Do not commit if the character changed during packet decode.
                Object currentPlayer = access.player();
                if (currentPlayer == null) return;
                int currentId = access.id(currentPlayer) & 65535;
                String currentName = trim(access.name(currentPlayer));
                if (currentId != localId || !currentName.equals(localName)) {
                    observePlayerLocked(currentId, currentName);
                    return;
                }
                for (int i = 0; i < pendingCount; i++) addLocked(pending[i]);
            } catch (Throwable ignored) {
                // Malformed or unresolved frames never partially update FIFO.
            } finally {
                try { input.reset(); } catch (Throwable ignored) { }
            }
        }
    }

    /** Track the current character at session entry; map/zone callbacks are ignored. */
    public static void onPlayerSession(BattlefieldAccess access) {
        if (access == null) return;
        try {
            Object player = access.player();
            if (player == null) return;
            int id = access.id(player) & 65535;
            String name = trim(access.name(player));
            synchronized (LOCK) { observePlayerLocked(id, name); }
        } catch (Throwable ignored) {
        }
    }

    /** Clear transient state when the game session exits. */
    public static void onSessionExit() {
        synchronized (LOCK) {
            enabled = false;
            hasPlayer = false;
            playerId = 0;
            playerName = "";
            clearFifoLocked();
        }
    }

    /** True when the enabled FIFO contains this exact trimmed name. */
    public static boolean excluded(String name) {
        String candidate = trim(name);
        if (candidate.length() == 0) return false;
        synchronized (LOCK) {
            if (!enabled) return false;
            for (int i = 0; i < fifoCount; i++) {
                if (FIFO[i].equals(candidate)) return true;
            }
            return false;
        }
    }

    public static int count() {
        synchronized (LOCK) { return fifoCount; }
    }

    public static String nameAt(int index) {
        synchronized (LOCK) {
            return index < 0 || index >= fifoCount ? null : FIFO[index];
        }
    }

    /** Clears static state for isolated host fixtures. */
    public static void resetForTest() {
        synchronized (LOCK) {
            enabled = false;
            hasPlayer = false;
            playerId = 0;
            playerName = "";
            clearFifoLocked();
        }
    }

    private static void observePlayerLocked(int id, String name) {
        if (!hasPlayer || playerId != id || !playerName.equals(name)) {
            clearFifoLocked();
            playerId = id;
            playerName = name;
            hasPlayer = true;
        }
    }

    private static void addLocked(String name) {
        for (int i = 0; i < fifoCount; i++) {
            if (FIFO[i].equals(name)) return;
        }
        if (fifoCount == FIFO_LIMIT) {
            for (int i = 1; i < FIFO_LIMIT; i++) FIFO[i - 1] = FIFO[i];
            FIFO[FIFO_LIMIT - 1] = null;
            fifoCount--;
        }
        FIFO[fifoCount++] = name;
    }

    private static void clearFifoLocked() {
        for (int i = 0; i < FIFO_LIMIT; i++) FIFO[i] = null;
        fifoCount = 0;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
