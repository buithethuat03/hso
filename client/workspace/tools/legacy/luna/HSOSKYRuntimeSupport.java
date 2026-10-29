/** Runtime state shared by the instant-combat, remote-loot and no-clip hooks. */
public final class HSOSKYRuntimeSupport {
    public static final int ACTION_NONE = 0;
    public static final int ACTION_TELEPORT_ITEM = 1;
    public static final int ACTION_RETRY_PICKUP = 2;
    public static final int ACTION_RETURN_ORIGIN = 3;

    private static final long MOVE_MATCH_WINDOW_MS = 1500L;
    private static final int MOVE_TOLERANCE_PX = 24;
    private static final long CORRECTION_WINDOW_MS = 5000L;
    private static final int CORRECTIONS_TO_DISABLE = 3;
    private static final long KILL_MATCH_WINDOW_MS = 5000L;
    private static final int KILL_MATCH_DISTANCE_PX = 48;
    private static final long REMOTE_PICKUP_TIMEOUT_MS = 500L;
    private static final long TELEPORT_SETTLE_MS = 200L;
    private static final long PICKUP_RESULT_TIMEOUT_MS = 500L;

    private static int worldToken = Integer.MIN_VALUE;
    private static int worldZone = Integer.MIN_VALUE;
    private static boolean noClipEnabled = true;
    private static boolean requestedNoClipEnabled = true;

    private static final int[] moveX = new int[12];
    private static final int[] moveY = new int[12];
    private static final long[] moveAt = new long[12];
    private static int moveCursor;
    private static final long[] correctionAt = new long[CORRECTIONS_TO_DISABLE];
    private static int correctionCount;

    private static final int[] killId = new int[16];
    private static final int[] killX = new int[16];
    private static final int[] killY = new int[16];
    private static final long[] killAt = new long[16];
    private static int killCursor;

    private static final int[] ownDropId = new int[32];
    private static final int[] ownDropType = new int[32];
    private static final long[] ownDropAt = new long[32];
    private static int ownDropCursor;
    private static final int[] fallbackId = new int[128];
    private static final int[] fallbackType = new int[128];
    private static int fallbackCursor;

    private static int lootState;
    private static long lootStateAt;
    private static int lootId;
    private static int lootType;
    private static int lootItemX;
    private static int lootItemY;
    private static int lootOriginX;
    private static int lootOriginY;
    private static int lootWorldToken;
    private static int lootWorldZone;
    private static int actionX;
    private static int actionY;
    private static int actionId;
    private static int actionType;
    private static int loadedMapSerial;

    private HSOSKYRuntimeSupport() {
    }

    public static synchronized void onWorld(int token, int zone) {
        if (token == worldToken && zone == worldZone) {
            return;
        }
        worldToken = token;
        worldZone = zone;
        noClipEnabled = requestedNoClipEnabled;
        moveCursor = 0;
        correctionCount = 0;
        killCursor = 0;
        ownDropCursor = 0;
        fallbackCursor = 0;
        clearLoot();
        clear(moveAt);
        clear(correctionAt);
        clear(killAt);
        clear(ownDropAt);
        System.out.println("HSOSKY: NoClip " + (noClipEnabled ? "ON" : "OFF")
                + " for new map/zone");
    }

    /** Called by the map loader once for every map/zone load. */
    public static synchronized void onMapLoaded() {
        loadedMapSerial++;
        onWorld(loadedMapSerial, 0);
    }

    public static synchronized int getWorldToken() {
        return worldToken;
    }

    public static synchronized int getWorldZone() {
        return worldZone;
    }

    public static synchronized boolean isNoClipEnabled() {
        return noClipEnabled;
    }

    /** Set the user-selected no-clip mode; the choice survives map changes. */
    public static synchronized void setNoClipEnabled(boolean enabled) {
        requestedNoClipEnabled = enabled;
        noClipEnabled = enabled;
        correctionCount = 0;
        clear(correctionAt);
    }



    public static synchronized int adjustTile(int originalTile) {
        if (!noClipEnabled) {
            return originalTile;
        }
        if (originalTile == 1 || originalTile == -1) {
            return 0;
        }
        return originalTile;
    }

    public static synchronized void recordMove(int x, int y) {
        moveX[moveCursor] = x;
        moveY[moveCursor] = y;
        moveAt[moveCursor] = System.currentTimeMillis();
        moveCursor = (moveCursor + 1) % moveAt.length;
    }

    /** Returns true exactly when this update disables no-clip. */
    public static synchronized boolean onServerPosition(int x, int y) {
        if (!noClipEnabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean hasRecentMove = false;
        for (int i = 0; i < moveAt.length; i++) {
            long age = now - moveAt[i];
            if (moveAt[i] == 0L || age < 0L || age > MOVE_MATCH_WINDOW_MS) {
                continue;
            }
            hasRecentMove = true;
            if (distance(x, y, moveX[i], moveY[i]) <= MOVE_TOLERANCE_PX) {
                correctionCount = 0;
                clear(correctionAt);
                return false;
            }
        }
        if (!hasRecentMove) {
            return false;
        }

        // A correction during the fallback means the server did not accept the
        // teleport. Abort immediately; the per-item attempted marker prevents loops.
        if (lootState >= 2) {
            clearLoot();
        }

        if (correctionCount < correctionAt.length) {
            correctionAt[correctionCount++] = now;
        } else {
            correctionAt[0] = correctionAt[1];
            correctionAt[1] = correctionAt[2];
            correctionAt[2] = now;
        }
        if (correctionCount >= CORRECTIONS_TO_DISABLE
                && now - correctionAt[correctionCount - CORRECTIONS_TO_DISABLE] <= CORRECTION_WINDOW_MS) {
            noClipEnabled = false;
            clearLoot();
            System.out.println("HSOSKY: NoClip OFF (server corrected position)");
            return true;
        }
        return false;
    }

    public static synchronized void recordMonsterResult(int id, int x, int y, int remainingHp) {
        if (remainingHp > 0) {
            return;
        }
        killId[killCursor] = id;
        killX[killCursor] = x;
        killY[killCursor] = y;
        killAt[killCursor] = System.currentTimeMillis();
        killCursor = (killCursor + 1) % killAt.length;
    }

    public static synchronized void recordTargetResult(
            int id, int type, int x, int y, int remainingHp) {
        if (type == 1) {
            recordMonsterResult(id, x, y, remainingHp);
        }
    }

    public static synchronized void onDropCreated(int id, int type, int x, int y) {
        long now = System.currentTimeMillis();
        boolean own = false;
        for (int i = 0; i < killAt.length; i++) {
            long age = now - killAt[i];
            if (killAt[i] != 0L && age >= 0L && age <= KILL_MATCH_WINDOW_MS
                    && distance(x, y, killX[i], killY[i]) <= KILL_MATCH_DISTANCE_PX) {
                own = true;
                break;
            }
        }
        if (!own) {
            return;
        }
        ownDropId[ownDropCursor] = id;
        ownDropType[ownDropCursor] = type;
        ownDropAt[ownDropCursor] = now;
        ownDropCursor = (ownDropCursor + 1) % ownDropAt.length;
    }

    public static synchronized void notePickupAttempt(
            int id, int type, int itemX, int itemY, int playerX, int playerY) {
        // Stationary whole-map loot: no movement/teleport attempts
    }

    public static synchronized void onDropRemoved(int id, int type) {
        if (lootState == 0 || lootId != id || lootType != type) {
            return;
        }
        clearLoot();
    }

    public static synchronized int pollLootAction(
            int token, int zone, int playerX, int playerY, boolean playerAlive) {
        return ACTION_NONE;
    }

    public static synchronized int pollLootAction(int playerX, int playerY, boolean playerAlive) {
        return ACTION_NONE;
    }

    public static synchronized int getActionX() {
        return actionX;
    }

    public static synchronized int getActionY() {
        return actionY;
    }

    public static synchronized int getActionId() {
        return actionId;
    }

    public static synchronized int getActionType() {
        return actionType;
    }

    public static synchronized boolean isLootTeleportActive() {
        return false;
    }

    private static boolean isOwnDrop(int id, int type) {
        return true;
    }

    private static boolean wasFallbackAttempted(int id, int type) {
        for (int i = 0; i < fallbackId.length; i++) {
            if (fallbackId[i] == id && fallbackType[i] == type) {
                return true;
            }
        }
        return false;
    }

    private static void markFallbackAttempted(int id, int type) {
        fallbackId[fallbackCursor] = id;
        fallbackType[fallbackCursor] = type;
        fallbackCursor = (fallbackCursor + 1) % fallbackId.length;
    }

    private static void clearLoot() {
        lootState = 0;
        lootStateAt = 0L;
        lootId = 0;
        lootType = 0;
    }

    private static void clearLootKeepingAction() {
        lootState = 0;
        lootStateAt = 0L;
        lootId = 0;
        lootType = 0;
    }

    private static int distance(int x1, int y1, int x2, int y2) {
        long dx = (long) x1 - (long) x2;
        long dy = (long) y1 - (long) y2;
        long squared = dx * dx + dy * dy;
        if (squared <= 0L) {
            return 0;
        }
        return (int) Math.sqrt((double) squared);
    }

    private static void clear(long[] values) {
        for (int i = 0; i < values.length; i++) {
            values[i] = 0L;
        }
    }
}
