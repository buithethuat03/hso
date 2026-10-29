/**
 * Rebuilds the complete NPC-menu session required by celestial crafting.
 *
 * <p>A successful craft invalidates the server-side recipe. The client records
 * the genuine NPC-open request and every menu reply that led to network UI type
 * 19, then replays that full chain before allowing another create request.</p>
 */
public final class HsoChildCelestialRepeatSupport {
    private static final byte IDLE = 0;
    private static final byte SEND_NPC_REQUEST = 1;
    private static final byte WAIT_MENU = 2;
    private static final byte SEND_MENU_REPLY = 3;
    private static final byte WAIT_SCREEN = 4;

    private static final int MAX_MENU_STEPS = 8;
    private static final long RESULT_SETTLE_MS = 500L;
    private static final long MENU_GAP_MS = 1200L;
    private static final long RESPONSE_TIMEOUT_MS = 7000L;

    private static final short[] recentNpc = new short[MAX_MENU_STEPS];
    private static final byte[] recentMenu = new byte[MAX_MENU_STEPS];
    private static final byte[] recentRow = new byte[MAX_MENU_STEPS];
    private static byte recentNpcRequest;
    private static int recentCount;
    private static boolean recording;

    private static final short[] savedNpc = new short[MAX_MENU_STEPS];
    private static final byte[] savedMenu = new byte[MAX_MENU_STEPS];
    private static final byte[] savedRow = new byte[MAX_MENU_STEPS];
    private static byte savedNpcRequest;
    private static int savedCount;

    private static boolean hasRecipe;
    private static boolean replaying;
    private static byte state;
    private static int actionIndex;
    private static long dueAt;
    private static long deadlineAt;

    private HsoChildCelestialRepeatSupport() {
    }

    /** Records the genuine opcode-23 request that opens an NPC's root menu. */
    public static void observeNpcRequest(byte npcId) {
        if (replaying) {
            return;
        }
        recentNpcRequest = npcId;
        recentCount = 0;
        recording = true;
    }

    /** Records all genuine opcode -30 replies after the NPC was opened. */
    public static void observeMenu(short npcId, byte menuId, byte row) {
        if (replaying || !recording) {
            return;
        }
        if (recentCount >= MAX_MENU_STEPS) {
            recording = false;
            recentCount = 0;
            return;
        }
        recentNpc[recentCount] = npcId;
        recentMenu[recentCount] = menuId;
        recentRow[recentCount] = row;
        ++recentCount;
    }

    /** Called only for native mode 3, which is network UI type 19. */
    public static void onCraftScreenOpened() {
        if (state == WAIT_SCREEN) {
            finishAttempt();
            return;
        }
        if (state != IDLE || !recording || recentCount <= 0) {
            return;
        }
        savedNpcRequest = recentNpcRequest;
        savedCount = recentCount;
        for (int i = 0; i < savedCount; ++i) {
            savedNpc[i] = recentNpc[i];
            savedMenu[i] = recentMenu[i];
            savedRow[i] = recentRow[i];
        }
        recording = false;
        hasRecipe = true;
    }

    /** The successful -105 response has invalidated the server-side recipe. */
    public static void onCraftResult() {
        if (!hasRecipe || savedCount <= 0 || state != IDLE) {
            return;
        }
        actionIndex = 0;
        state = SEND_NPC_REQUEST;
        dueAt = System.currentTimeMillis() + RESULT_SETTLE_MS;
        deadlineAt = dueAt + RESPONSE_TIMEOUT_MS;
    }

    /** Suppresses each replayed menu and schedules its captured reply. */
    public static boolean onNpcMenu(short npcId, byte menuId) {
        if (state != WAIT_MENU || actionIndex >= savedCount
                || npcId != savedNpc[actionIndex]
                || menuId != savedMenu[actionIndex]) {
            return false;
        }
        state = SEND_MENU_REPLY;
        dueAt = System.currentTimeMillis() + MENU_GAP_MS;
        deadlineAt = dueAt + RESPONSE_TIMEOUT_MS;
        return true;
    }

    public static boolean isRearming() {
        return state != IDLE;
    }

    /** Runs from the still-visible crafting screen's native update method. */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (state == SEND_NPC_REQUEST && now >= dueAt) {
            state = WAIT_MENU;
            deadlineAt = now + RESPONSE_TIMEOUT_MS;
            if (!sendNpcRequest(savedNpcRequest)) {
                finishAttempt();
            }
            return;
        }
        if (state == SEND_MENU_REPLY && now >= dueAt) {
            int index = actionIndex++;
            state = actionIndex >= savedCount ? WAIT_SCREEN : WAIT_MENU;
            deadlineAt = now + RESPONSE_TIMEOUT_MS;
            if (!sendMenu(savedNpc[index], savedMenu[index], savedRow[index])) {
                finishAttempt();
            }
            return;
        }
        if ((state == WAIT_MENU || state == WAIT_SCREEN)
                && deadlineAt != 0L && now >= deadlineAt) {
            finishAttempt();
        }
    }

    /** Closing the screen must not leave a recipe armed for another UI. */
    public static void cancel() {
        finishAttempt();
        hasRecipe = false;
        recording = false;
        recentCount = 0;
        savedCount = 0;
    }

    private static void finishAttempt() {
        state = IDLE;
        actionIndex = 0;
        dueAt = 0L;
        deadlineAt = 0L;
    }

    private static boolean sendNpcRequest(byte npcId) {
        replaying = true;
        try {
            IIIllIIlllIlIIIllIllIIIlllIIlllIIlllIlIlIIlIIIllIIllIlllIIlllIIlIlllIIlIIlIIllIIlIIIlllIllIIlllIIlIllIIIllIIlIIlIIlIIllIIlIIlIIIllIIllIIlIIlIIlllIIlllIIIllIlIIllIllIIlllIlIIIlllIIllIIlllIIllIlllIIllIIIllIIllIIllIIIlllIlIIlllIIllIIllIIlll
                    .IIllIIlIllIIlIlllIIllIIIllIIIllIlIIlIIIllIIlllIIllIllIlIIlllIIlllIlIlIIlllIIlllIIIllIIllIIlIIIllIIIlllIIIllIllIIIlllIIllIllIlllIIIllIIIllIIlIlIIllIIlIIIlllIIllIIllIllIlllIIIlIIlIIIlIIllIlIIllIlllIlIIIllIIIlIIIllIIIlIlIllIIllIlIll()
                    .IIllIIlIllIIlIlllIIllIIIllIIIllIlIIlIIIllIIlllIIllIllIlIIlllIIlllIlIlIIlllIIlllIIIllIIllIIlIIIllIIIlllIIIllIllIIIlllIIllIllIlllIIIllIIIllIIlIlIIllIIlIIIlllIIllIIllIllIlllIIIlIIlIIIlIIllIlIIllIlllIlIIIllIIIlIIIllIIIlIlIllIIllIlIll(
                            npcId);
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            replaying = false;
        }
    }

    private static boolean sendMenu(short npcId, byte menuId, byte row) {
        replaying = true;
        try {
            IIIllIIlllIlIIIllIllIIIlllIIlllIIlllIlIlIIlIIIllIIllIlllIIlllIIlIlllIIlIIlIIllIIlIIIlllIllIIlllIIlIllIIIllIIlIIlIIlIIllIIlIIlIIIllIIllIIlIIlIIlllIIlllIIIllIlIIllIllIIlllIlIIIlllIIllIIlllIIllIlllIIllIIIllIIllIIllIIIlllIlIIlllIIllIIllIIlll
                    .IIllIIlIllIIlIlllIIllIIIllIIIllIlIIlIIIllIIlllIIllIllIlIIlllIIlllIlIlIIlllIIlllIIIllIIllIIlIIIllIIIlllIIIllIllIIIlllIIllIllIlllIIIllIIIllIIlIlIIllIIlIIIlllIIllIIllIllIlllIIIlIIlIIIlIIllIlIIllIlllIlIIIllIIIlIIIllIIIlIlIllIIllIlIll()
                    .IIllIllIIllIlIIlIlIIlllIllIIllIIllIlIIllIIIllIIIllIIllIIIlIllIIlIIlllIllIIlIllIllIIlIIlIlIIIlIllIIllIlllIllIIIllIIlIIlllIllIIlIIIlIIllIIllIIlIlIIlIllIIlllIlIIlllIIlIlIIlIIlIlllIIlIIIlIIllIllIlllIIlllIIIlIIllIIIlll(
                            npcId, menuId, row);
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            replaying = false;
        }
    }
}
