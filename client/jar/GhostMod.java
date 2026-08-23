import javax.microedition.lcdui.Image;
import java.util.Hashtable;

public class GhostMod implements Runnable {
    private static final String BUILD_TAG = "2026-05-18-post-login-medal-setup";
    private static final int KICKSTART_ENTER_PRESSES = 1;
    private static final int ENGINE_PATHFINDER_HARD_LIMIT = 126;
    private static final int PORTAL_APPROACH_RADIUS = 4;
    private static final int PORTAL_FINAL_PUSH_PATH_LENGTH = 32;
    private static final long PORTAL_FINAL_PUSH_INTERVAL_MS = 1500L;
    private static final long POST_LOGIN_OVERLAY_WATCH_MS = 60000L;
    private static final long POST_LOGIN_OVERLAY_POLL_MS = 500L;
    private static final long DEBUG_SNAPSHOT_INTERVAL_MS = 5000L;
    private static final long KICKSTART_MEDAL_DROP_DELAY_MS = 5000L;
    private static final long POST_MEDAL_AUTO_BUFF_DELAY_MS = 2500L;
    private static final int DEBUG_ENTITY_DUMP_LIMIT = 80;
    private static final short MEDAL_MATERIAL_ROOT_NPC = 125;
    private static final byte MEDAL_MATERIAL_ROOT_MENU = 125;
    private static final short MEDAL_WHITE_DROP_NPC = -125;
    private static final byte MEDAL_WHITE_DROP_MENU = -125;
    private static final byte MEDAL_WHITE_DROP_INDEX = 0;
    private static final long MEDAL_PACKET_DELAY_MS = 2500L;
    private static final long MEDAL_MENU_WAIT_MS = 2500L;
    private static final int AUTO_BUFF_REQUIRED_SLOTS = 3;
    private static int[] currentDigits;
    private static boolean processing = false;
    private static volatile boolean debugEnabled = false;
    private static boolean debugMonitorStarted = false;
    private static boolean debugSnapshotMonitorStarted = false;
    private static boolean debugLegendPrinted = false;
    private static int debugDumpSeq = 0;
    private static int debugSnapshotSeq = 0;
    private static volatile boolean menuTraceEnabled = false;
    private static boolean menuTraceMonitorStarted = false;
    private static boolean autoFeatureTraceMonitorStarted = false;
    private static boolean menuTraceLastActive = false;
    private static String menuTraceLastSignature = "";
    private static String autoFeatureTraceLastSignature = "";
    private static int menuTraceSeq = 0;
    private static int menuTraceInputSeq = 0;
    private static long menuTraceLastMoveLogMs = 0L;
    private static boolean debugHasPreviousMap = false;
    private static int debugLastMapId = Integer.MIN_VALUE;
    private static byte debugLastZone = Byte.MIN_VALUE;
    private static String debugLastMapName = null;
    private static int debugLastPlayerPixelX = -1;
    private static int debugLastPlayerPixelY = -1;
    private static int debugLastPlayerTileX = -1;
    private static int debugLastPlayerTileY = -1;
    private static PortalSnapshot[] debugLastPortals = new PortalSnapshot[0];
    private static final Hashtable savedPointsByCharacter = new Hashtable();
    private static boolean kickstartTriggered = false;
    private static long lastKickstartAttempt = 0L;
    private static boolean recoveryInProgress = false;
    private static String recoveryPendingCharacterName = null;
    private static String lastKnownCharacterName = null;
    private static String lastLiveCharacterName = null;
    private static boolean stateHadLiveMapContext = false;
    private static boolean autoAttackSuppressed = false;
    private static long lastRecoveryStartAttempt = 0L;
    private static boolean targetRefreshPending = true;
    private static boolean postLoginOverlayWatchActive = false;
    private static boolean postLoginSetupPending = false;
    private static volatile boolean medalDropWorkerRunning = false;
    private static volatile boolean autoBuffWorkerRunning = false;
    private static final RouteEdge[] ROUTE_GRAPH = buildRouteGraph();

    // === AUTO PET FEED ===
    private static final int  AUTO_FEED_HOUR          = 7;   // 7h sáng
    private static final int  AUTO_FEED_MAX_ITEMS     = 15;  // tối đa 15 món
    private static final long AUTO_FEED_DELAY_MS      = 2500L; // 2.5s giữa mỗi lần
    private static final int  AUTO_FEED_ITEM_GRADE    = 3;   // N=3 = tím
    private static volatile boolean autoPetFeedStarted = false;
    private static volatile long    autoPetFeedLastRunDay = -1L; // ngày đã chạy
    
    public static short cachedPetId = -1;
    public static String cachedPetName = "Unknown";
    public static et lastBwX = null;
    public static int lastBwXSize = -1;

    private static final class PortalSnapshot {
        final int idx;
        final String label;
        final int pixelX;
        final int pixelY;
        final int tileX;
        final int tileY;
        final int side;

        PortalSnapshot(int idx, String label, int pixelX, int pixelY, int tileX, int tileY, int side) {
            this.idx = idx;
            this.label = label;
            this.pixelX = pixelX;
            this.pixelY = pixelY;
            this.tileX = tileX;
            this.tileY = tileY;
            this.side = side;
        }
    }

    private static final class SavedPoint {
        final String characterName;
        final int mapId;
        final String mapName;
        final byte zone;
        final int pixelX;
        final int pixelY;
        final long savedAt;

        SavedPoint(String characterName, int mapId, String mapName, byte zone, int pixelX, int pixelY, long savedAt) {
            this.characterName = characterName;
            this.mapId = mapId;
            this.mapName = mapName;
            this.zone = zone;
            this.pixelX = pixelX;
            this.pixelY = pixelY;
            this.savedAt = savedAt;
        }
    }

    private static final class RouteEdge {
        final int fromMapId;
        final int toMapId;
        final int portalIndex;

        RouteEdge(int fromMapId, int toMapId, int portalIndex) {
            this.fromMapId = fromMapId;
            this.toMapId = toMapId;
            this.portalIndex = portalIndex;
        }
    }

    private static final class PathPlan {
        final int pathTargetX;
        final int pathTargetY;
        final short[] path;
        final boolean retargeted;

        PathPlan(int pathTargetX, int pathTargetY, short[] path, boolean retargeted) {
            this.pathTargetX = pathTargetX;
            this.pathTargetY = pathTargetY;
            this.path = path;
            this.retargeted = retargeted;
        }
    }

    public static void checkGhost(Image img) {
        if (img == null || processing) return;
        try {
            int[] digits = ghostOCR(img);
            if (digits != null && digits.length == 5) {
                currentDigits = digits;
                new Thread(new GhostMod()).start();
            }
        } catch (Throwable t) {
            System.out.println("====== GHOST MOD CRASH ======");
            t.printStackTrace();
        }
    }

    public void run() {
        processing = true;
        try {
            int[] digits = currentDigits;
            if (digits == null) return;
            
            System.out.println("Wait for Ghost State (cy=2)...");
            int timeout = 0;
            while (cn.i == null || cn.i.cy != 2) {
                Thread.sleep(200);
                timeout += 200;
                if (timeout > 5000) break;
            }
            
            Thread.sleep(1000);

            // First attempt: press all 5 keys
            pressGhostKeys(digits, 1);

            // Retry loop: every 20s check if ghost is still alive
            int retryCount = 1;
            while (true) {
                System.out.println("GHOST: Waiting 20s to verify kill...");
                Thread.sleep(20000);
                
                // Check if ghost is dead: cn.i is null, or cy is no longer 2
                if (cn.i == null || cn.i.cy != 2) {
                    System.out.println("GHOST: Confirmed DEAD! (cn.i=" + (cn.i == null ? "null" : "cy=" + cn.i.cy) + ")");
                    break;
                }
                
                // Ghost still alive - retry with the same answer
                retryCount++;
                System.out.println("GHOST: Still ALIVE! Retrying attempt #" + retryCount + "...");
                pressGhostKeys(digits, retryCount);
            }
            
            System.out.println("DONE GHOST!");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            processing = false;
        }
    }

    private void pressGhostKeys(int[] digits, int attempt) throws Exception {
        System.out.println("GHOST: Pressing keys (attempt #" + attempt + ")...");
        for (int gi = 0; gi < 5; gi++) {
            int idx = -1;
            switch (digits[gi]) {
                case 1: idx = 0; break;
                case 3: idx = 1; break;
                case 5: idx = 2; break;
                case 7: idx = 3; break;
                case 9: idx = 4; break;
            }
            if (idx >= 0) {
                System.out.print("Bam phim ");
                System.out.print(gi + 1);
                System.out.print(": ");
                System.out.println(digits[gi]);
                q.a().f((byte) idx);
                // 1.5s delay between keys
                Thread.sleep(1500); 
            }
        }
    }

    private static int[] ghostOCR(Image img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = new int[w * h];
        img.getRGB(pixels, 0, w, 0, 0, w, h);

        int[] bw = new int[w * h];
        int minB = 255;
        int maxB = 0;
        
        for (int i = 0; i < pixels.length; i++) {
            int a = (pixels[i] >> 24) & 0xFF;
            if (a < 20) continue;
            int r = (pixels[i] >> 16) & 0xFF;
            int g = (pixels[i] >> 8) & 0xFF;
            int b = pixels[i] & 0xFF;
            int br = (r + g + b) / 3;
            if (br < minB) minB = br;
            if (br > maxB) maxB = br;
        }
        
        int threshold = minB + ((maxB - minB) * 7 / 10);
        
        // Find overall text vertical bounds
        int topRow = -1, botRow = -1;
        for (int y = 0; y < h; y++) {
            boolean hasPixel = false;
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int a = (pixels[i] >> 24) & 0xFF;
                if (a >= 20) {
                    int r = (pixels[i] >> 16) & 0xFF;
                    int g = (pixels[i] >> 8) & 0xFF;
                    int b = pixels[i] & 0xFF;
                    int br = (r + g + b) / 3;
                    if (br >= threshold) {
                        bw[i] = 1;
                        hasPixel = true;
                    }
                }
            }
            if (hasPixel) {
                if (topRow == -1) topRow = y;
                botRow = y;
            }
        }

        System.out.println("====== GHOST OCR MATRIX ======");
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                System.out.print(bw[y * w + x] == 1 ? "XX" : "..");
            }
            System.out.println();
        }

        if (topRow == -1) return null;

        // Split into 5 digits horizontally
        int[] colSum = new int[w];
        for (int x = 0; x < w; x++) {
            for (int y = topRow; y <= botRow; y++) {
                colSum[x] += bw[y * w + x];
            }
        }

        int minCol = -1, maxCol = -1;
        for (int x = 0; x < w; x++) {
            if (colSum[x] > 0) {
                if (minCol == -1) minCol = x;
                maxCol = x;
            }
        }

        if (minCol == -1) return null;

        int[][] charBounds = new int[5][2];
        int charCount = 0;
        boolean inChar = false;
        for (int x = minCol; x <= maxCol; x++) {
            if (colSum[x] > 0 && !inChar) {
                if (charCount < 5) charBounds[charCount][0] = x;
                inChar = true;
            } else if (colSum[x] == 0 && inChar) {
                if (charCount < 5) charBounds[charCount][1] = x;
                charCount++;
                inChar = false;
            }
        }
        if (inChar && charCount < 5) {
            charBounds[charCount][1] = maxCol + 1;
            charCount++;
        }

        // If not 5 parts (meaning some digits are touching), split the whole block equally
        if (charCount != 5) {
            int textW = maxCol - minCol + 1;
            int charW = textW / 5;
            for (int i = 0; i < 5; i++) {
                charBounds[i][0] = minCol + (i * charW);
                charBounds[i][1] = (i == 4) ? (maxCol + 1) : (minCol + (i + 1) * charW);
            }
        }

        int[] result = new int[5];
        for (int i = 0; i < 5; i++) {
            int firstCount = 0;
            int lastCount = 0;
            int left = charBounds[i][0];
            int right = charBounds[i][1];
            
            for (int x = left; x < right; x++) {
                if (bw[topRow * w + x] == 1) firstCount++;
                if (bw[botRow * w + x] == 1) lastCount++;
            }
            
            result[i] = identifyDigit(firstCount * 2, lastCount * 2);
            System.out.println("Digit " + i + ": Top=" + (firstCount * 2) + ", Bot=" + (lastCount * 2) + " (raw:" + firstCount + "," + lastCount + ") -> " + result[i]);
        }
        
        System.out.print("Ket qua: ");
        for (int r : result) System.out.print(r);
        System.out.println();
        
        return result;
    }

    private static int identifyDigit(int top, int bot) {
        // 1: 4, 4
        // 3: 8, 8
        // 5: 10, 8
        // 7: 12, 4
        // 9: 8, 6
        if (top == 4 && bot == 4) return 1;
        if (top == 8 && bot == 8) return 3;
        if (top == 10 && bot == 8) return 5;
        if (top == 12 && bot == 4) return 7;
        if (top == 8 && bot == 6) return 9;
        
        // Fallback using closest match if exact match fails
        if (bot == 4) {
            return (top > 8) ? 7 : 1;
        }
        if (bot == 8) {
            return (top > 9) ? 5 : 3;
        }
        if (bot == 6) return 9;
        
        return 1; // Default
    }

    private static void startDebugMapMonitor() {
        if (debugMonitorStarted) return;
        debugMonitorStarted = true;

        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(250);

                        if (!debugEnabled) {
                            resetDebugMapState();
                            continue;
                        }
                        printDebugLegendIfNeeded();

                        String screenName = (fu.a != null) ? fu.a.getClass().getName() : "";
                        boolean isLoginScreen = screenName.equals("ea") || screenName.endsWith(".ea");
                        boolean isOutOfGameScreen = screenName.equals("x") || screenName.endsWith(".x") || screenName.equals("bs") || screenName.endsWith(".bs");

                        if (isLoginScreen || isOutOfGameScreen || fu.a == null || cn.g == null || fu.q == null) {
                            resetDebugMapState();
                            continue;
                        }

                        if (fu.a != fu.c) {
                            continue;
                        }

                        int currentMapId = fu.q.d;
                        byte currentZone = cs.b();
                        String currentMapName = resolveMapName(currentMapId);
                        int currentPixelX = cn.g.aZ;
                        int currentPixelY = cn.g.ba;
                        int currentTileX = toTile(currentPixelX);
                        int currentTileY = toTile(currentPixelY);

                        if (!debugHasPreviousMap || currentMapId != debugLastMapId || currentZone != debugLastZone) {
                            if (debugHasPreviousMap) {
                                logMapChange(currentMapId, currentZone, currentMapName);
                            }

                            PortalSnapshot[] portals = buildPortalSnapshots();
                            dumpCurrentMap(currentMapId, currentZone, currentMapName, currentPixelX, currentPixelY, currentTileX, currentTileY, portals);

                            debugLastMapId = currentMapId;
                            debugLastZone = currentZone;
                            debugLastMapName = currentMapName;
                            debugLastPlayerPixelX = currentPixelX;
                            debugLastPlayerPixelY = currentPixelY;
                            debugLastPlayerTileX = currentTileX;
                            debugLastPlayerTileY = currentTileY;
                            debugLastPortals = portals;
                            debugHasPreviousMap = true;
                        } else {
                            debugLastPlayerPixelX = currentPixelX;
                            debugLastPlayerPixelY = currentPixelY;
                            debugLastPlayerTileX = currentTileX;
                            debugLastPlayerTileY = currentTileY;
                        }
                    } catch (Throwable t) {
                        System.err.println("[GHOSTDBG] ERROR " + t.toString());
                    }
                }
            }
        }).start();
    }

    private static void resetDebugMapState() {
        debugHasPreviousMap = false;
        debugLastMapId = Integer.MIN_VALUE;
        debugLastZone = Byte.MIN_VALUE;
        debugLastMapName = null;
        debugLastPlayerPixelX = -1;
        debugLastPlayerPixelY = -1;
        debugLastPlayerTileX = -1;
        debugLastPlayerTileY = -1;
        debugLastPortals = new PortalSnapshot[0];
    }

    private static void printDebugLegendIfNeeded() {
        if (!debugLegendPrinted) {
            debugLegendPrinted = true;
            System.err.println("[GHOSTDBG] START legend: #=blocked .=walkable ~=special P=portal S=player *=player_on_portal");
        }
    }

    private static void startDebugSnapshotMonitor() {
        if (debugSnapshotMonitorStarted) return;
        debugSnapshotMonitorStarted = true;

        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(DEBUG_SNAPSHOT_INTERVAL_MS);
                        if (debugEnabled) {
                            dumpDebugSnapshot("periodic", false);
                        }
                    } catch (Throwable t) {
                        System.err.println("[GHOSTDBG] SNAPSHOT_ERROR " + t.toString());
                    }
                }
            }
        }).start();
    }

    private static void startMenuTraceMonitor() {
        if (menuTraceMonitorStarted) return;
        menuTraceMonitorStarted = true;

        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(100L);
                        if (!menuTraceEnabled) {
                            menuTraceLastActive = false;
                            menuTraceLastSignature = "";
                            continue;
                        }

                        boolean active = fu.p != null && fu.p.a;
                        if (active) {
                            String signature = buildMenuTraceSignature();
                            if (!menuTraceLastActive) {
                                logMenuTrace("open", true);
                            } else if (!signature.equals(menuTraceLastSignature)) {
                                logMenuTrace("update", true);
                            }
                            menuTraceLastSignature = signature;
                        } else if (menuTraceLastActive) {
                            System.err.println("[GHOSTDBG] MENU_CLOSE seq=" + (++menuTraceSeq)
                                    + " timeMs=" + System.currentTimeMillis());
                            menuTraceLastSignature = "";
                        }
                        menuTraceLastActive = active;
                    } catch (Throwable t) {
                        System.err.println("[GHOSTDBG] MENU_TRACE_ERROR " + t.toString());
                    }
                }
            }
        }).start();
    }

    private static void startAutoFeatureTraceMonitor() {
        if (autoFeatureTraceMonitorStarted) return;
        autoFeatureTraceMonitorStarted = true;

        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(150L);
                        if (!menuTraceEnabled && !debugEnabled) {
                            autoFeatureTraceLastSignature = "";
                            continue;
                        }

                        String signature = buildAutoFeatureSignature();
                        if (!signature.equals(autoFeatureTraceLastSignature)) {
                            logAutoFeatureState("change");
                            autoFeatureTraceLastSignature = signature;
                        }
                    } catch (Throwable t) {
                        System.err.println("[GHOSTDBG] AUTO_FEATURE_TRACE_ERROR " + t.toString());
                    }
                }
            }
        }).start();
    }

    private static void setMenuTraceEnabled(boolean enabled, String reason) {
        menuTraceEnabled = enabled;
        menuTraceLastActive = false;
        menuTraceLastSignature = "";
        autoFeatureTraceLastSignature = "";
        menuTraceLastMoveLogMs = 0L;
        System.err.println("[GHOSTDBG] MENU_TRACE_MODE enabled=" + enabled
                + " reason=\"" + sanitizeForLog(reason) + "\""
                + " build=\"" + BUILD_TAG + "\"");
        if (enabled) {
            logMenuTrace("enable", true);
            logAutoFeatureState("menu_trace_enable");
        }
    }

    public static void hookPointerEvent(String phase, int rawX, int rawY) {
        if (!menuTraceEnabled) return;
        try {
            long now = System.currentTimeMillis();
            if ("move".equals(phase) && now - menuTraceLastMoveLogMs < 150L) {
                return;
            }
            if ("move".equals(phase)) {
                menuTraceLastMoveLogMs = now;
            }

            int scale = getPointerScale();
            int x = scale > 0 ? rawX / scale : rawX;
            int y = scale > 0 ? rawY / scale : rawY;
            int hitIndex = findMenuIndexAt(x, y);
            String hitText = getCurrentMenuItemText(hitIndex);

            System.err.println("[GHOSTDBG] INPUT seq=" + (++menuTraceInputSeq)
                    + " type=pointer"
                    + " phase=\"" + sanitizeForLog(phase) + "\""
                    + " rawX=" + rawX
                    + " rawY=" + rawY
                    + " x=" + x
                    + " y=" + y
                    + " scale=" + scale
                    + " screen=\"" + getClassName(fu.a) + "\""
                    + " dialogS=" + (fu.s != null)
                    + " dialogT=" + (fu.t != null)
                    + " menu=" + (fu.p != null && fu.p.a)
                    + " menuSelected=" + getMenuSelectedIndex()
                    + " menuHit=" + hitIndex
                    + " menuHitText=\"" + sanitizeForLog(hitText) + "\"");

            if (fu.p != null && fu.p.a && ("down".equals(phase) || "up".equals(phase))) {
                logMenuTrace("pointer_" + phase, false);
            }
        } catch (Throwable t) {
            System.err.println("[GHOSTDBG] INPUT_ERROR type=pointer error=\"" + sanitizeForLog(t.toString()) + "\"");
        }
    }

    public static void hookKeyEvent(String phase, int keyCode) {
        int hotbarIndex = mapExtraHotbarKey(keyCode);
        if (hotbarIndex >= 0) {
            boolean down = "down".equals(phase);
            if (fu.al != null && hotbarIndex < fu.al.length) fu.al[hotbarIndex] = down;
            if (fu.am != null && hotbarIndex < fu.am.length) fu.am[hotbarIndex] = down;
        }
        if (!menuTraceEnabled) return;
        try {
            System.err.println("[GHOSTDBG] INPUT seq=" + (++menuTraceInputSeq)
                    + " type=key"
                    + " phase=\"" + sanitizeForLog(phase) + "\""
                    + " keyCode=" + keyCode
                    + " keyIndex=" + mapKeyCodeToIndex(keyCode)
                    + " screen=\"" + getClassName(fu.a) + "\""
                    + " dialogS=" + (fu.s != null)
                    + " dialogT=" + (fu.t != null)
                    + " menu=" + (fu.p != null && fu.p.a)
                    + " menuSelected=" + getMenuSelectedIndex()
                    + " selectedText=\"" + sanitizeForLog(getCurrentMenuItemText(getMenuSelectedIndex())) + "\"");
        } catch (Throwable t) {
            System.err.println("[GHOSTDBG] INPUT_ERROR type=key error=\"" + sanitizeForLog(t.toString()) + "\"");
        }
    }

    private static int mapExtraHotbarKey(int keyCode) {
        switch (keyCode) {
            case 70: case 102: return 30; // F -> slot 6
            case 71: case 103: return 31; // G -> slot 7
            case 72: case 104: return 32; // H -> slot 8
            case 74: case 106: return 33; // J -> slot 9
            case 75: case 107: return 34; // K -> slot 10
            default: return -1;
        }
    }

    public static void hookMenuCommand(short idNPC, byte idMenu, byte index) {
        String itemText = getCurrentMenuItemText(index);
        System.err.println("GhostMod: [HOOK] Packet -30 (MenuCommand) SENT! idNPC=" + idNPC
                + ", idMenu=" + idMenu
                + ", index=" + index
                + ", text=\"" + sanitizeForLog(itemText) + "\"");
        System.err.println("[GHOSTDBG] MENU_COMMAND seq=" + (++menuTraceSeq)
                + " idNPC=" + idNPC
                + " idMenu=" + idMenu
                + " index=" + index
                + " text=\"" + sanitizeForLog(itemText) + "\""
                + " replay=\"q.a().b((short)" + idNPC
                + ", (byte)" + idMenu
                + ", (byte)" + index + ");\"");
        if (menuTraceEnabled) {
            logMenuTrace("command_before_send", true);
        }
    }

    public static void hookScreenCommand(String owner, int command, int arg) {
        if (!menuTraceEnabled && !debugEnabled) return;
        try {
            System.err.println("[GHOSTDBG] SCREEN_COMMAND owner=\"" + sanitizeForLog(owner) + "\""
                    + " command=" + command
                    + " arg=" + arg
                    + " activeScreen=\"" + getClassName(fu.a) + "\""
                    + " dialogS=" + (fu.s != null)
                    + " dialogT=" + (fu.t != null)
                    + " menu=" + (fu.p != null && fu.p.a)
                    + " menuSelected=" + getMenuSelectedIndex()
                    + " selectedText=\"" + sanitizeForLog(getCurrentMenuItemText(getMenuSelectedIndex())) + "\"");
            logAutoFeatureState("screen_command_before_" + owner + "_" + command);
        } catch (Throwable t) {
            System.err.println("[GHOSTDBG] SCREEN_COMMAND_ERROR owner=\"" + sanitizeForLog(owner)
                    + "\" error=\"" + sanitizeForLog(t.toString()) + "\"");
        }
    }

    private static void logAutoFeatureState(String reason) {
        Object autoPick = bq.q;
        System.err.println("[GHOSTDBG] AUTO_FEATURE_STATE reason=\"" + sanitizeForLog(reason) + "\""
                + " autoAttackState=" + bq.o
                + " autoAttackPersistent=" + bq.Y
                + " autoAttackActive=" + isPersistentAutoAttackActive()
                + " autoPick=" + (autoPick != null)
                + " autoPickPendingOff=" + getAutoPickFlag(autoPick)
                + " autoPickMinGrade=" + getPrivateInt(autoPick, "a", Integer.MIN_VALUE)
                + " autoPickMode=" + getPrivateInt(autoPick, "c", Integer.MIN_VALUE)
                + " autoPickTarget=" + getPrivateInt(autoPick, "b", Integer.MIN_VALUE)
                + " autoPotion=" + bq.l
                + " autoBuffMode=" + bq.p
                + " autoBuffSlots=\"" + sanitizeForLog(formatAutoBuffSlots()) + "\""
                + " autoTargetPlayers=" + cf.J
                + " touchMode=" + cf.I
                + " bottomMenu=" + (fu.p != null && fu.p.a)
                + " dialogClassS=\"" + getClassName(fu.s) + "\""
                + " dialogClassT=\"" + getClassName(fu.t) + "\"");
    }

    private static String buildAutoFeatureSignature() {
        Object autoPick = bq.q;
        StringBuffer sb = new StringBuffer();
        sb.append("atk=").append(bq.o).append('/').append(bq.Y);
        sb.append("|pick=").append(autoPick != null);
        sb.append('|').append(getAutoPickFlag(autoPick));
        sb.append('|').append(getPrivateInt(autoPick, "a", Integer.MIN_VALUE));
        sb.append('|').append(getPrivateInt(autoPick, "c", Integer.MIN_VALUE));
        sb.append('|').append(getPrivateInt(autoPick, "b", Integer.MIN_VALUE));
        sb.append("|pot=").append(bq.l);
        sb.append("|buff=").append(bq.p);
        sb.append('|').append(formatAutoBuffSlots());
        sb.append("|cfJ=").append(cf.J);
        sb.append("|cfI=").append(cf.I);
        sb.append("|menu=").append(fu.p != null && fu.p.a);
        sb.append("|s=").append(getClassName(fu.s));
        sb.append("|t=").append(getClassName(fu.t));
        return sb.toString();
    }

    private static boolean getAutoPickFlag(Object autoPick) {
        Object value = getPrivateField(autoPick, "g");
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static String formatAutoBuffSlots() {
        if (ah.f == null) {
            return "null";
        }
        int count = getAutoBuffSlotCount();
        int limit = count;
        if (limit > ah.f.length) {
            limit = ah.f.length;
        }
        if (limit > 8) {
            limit = 8;
        }
        StringBuffer sb = new StringBuffer();
        sb.append("count=").append(count).append(" slots=[");
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append(';');
            }
            if (ah.f[i] == null || ah.f[i].length < 2) {
                sb.append(i).append(":missing");
            } else {
                sb.append(i)
                        .append(":skill=").append(ah.f[i][0])
                        .append(",on=").append(ah.f[i][1]);
            }
        }
        if (count > limit) {
            sb.append(";...");
        }
        sb.append(']');
        return sb.toString();
    }

    private static int getPointerScale() {
        try {
            return bx.b;
        } catch (Throwable t) {
            return 1;
        }
    }

    private static int mapKeyCodeToIndex(int keyCode) {
        switch (keyCode) {
            case -1: return 2;
            case -2: return 8;
            case -3: return 4;
            case -4: return 6;
            case -5:
            case 10: return 5;
            case 42: return 10;
            case 35: return 11;
            case -6: return 12;
            case -7: return 13;
        }
        if (keyCode >= 48 && keyCode <= 57) {
            return keyCode - 28;
        }
        return -1;
    }

    private static void logMenuTrace(String reason, boolean includeItems) {
        int seq = ++menuTraceSeq;
        fr menu = fu.p;
        if (menu == null) {
            System.err.println("[GHOSTDBG] MENU_TRACE seq=" + seq
                    + " reason=\"" + sanitizeForLog(reason) + "\""
                    + " active=false menu=null");
            return;
        }

        et items = getMenuItems(menu);
        int selected = getPrivateInt(menu, "h", -1);
        int idNPC = getPrivateInt(menu, "C", Integer.MIN_VALUE);
        int idMenu = getPrivateInt(menu, "B", Integer.MIN_VALUE);
        int dialogType = getPrivateInt(menu, "D", Integer.MIN_VALUE);
        String title = getPrivateString(menu, "v", "");

        System.err.println("[GHOSTDBG] MENU_TRACE seq=" + seq
                + " reason=\"" + sanitizeForLog(reason) + "\""
                + " active=" + menu.a
                + " class=\"" + getClassName(menu) + "\""
                + " title=\"" + sanitizeForLog(title) + "\""
                + " idNPC=" + idNPC
                + " idMenu=" + idMenu
                + " dialogType=" + dialogType
                + " frMode=" + fr.d
                + " selected=" + selected
                + " selectedText=\"" + sanitizeForLog(getMenuItemText(items, selected)) + "\""
                + " itemCount=" + safeEtSize(items)
                + " layoutX=" + getPrivateInt(menu, "j", -1)
                + " layoutY=" + getPrivateInt(menu, "k", -1)
                + " currentY=" + getPrivateInt(menu, "n", -1)
                + " width=" + getPrivateInt(menu, "O", -1)
                + " height=" + getPrivateInt(menu, "m", -1)
                + " itemHeight=" + getPrivateInt(menu, "l", -1)
                + " placement=" + getPrivateInt(menu, "t", -1)
                + " scroll=" + getPrivateStaticInt(fr.class, "q", 0)
                + " screenW=" + fu.X
                + " screenH=" + fu.Y);

        if (includeItems && items != null) {
            for (int i = 0; i < items.c(); i++) {
                logMenuTraceItem(seq, i, items.a(i));
            }
        }
    }

    private static void logMenuTraceItem(int seq, int index, Object item) {
        if (item instanceof bt) {
            bt cmd = (bt) item;
            System.err.println("[GHOSTDBG] MENU_ITEM seq=" + seq
                    + " idx=" + index
                    + " text=\"" + sanitizeForLog(cmd.a) + "\""
                    + " action=" + cmd.e
                    + " arg=" + cmd.f
                    + " buttonX=" + cmd.h
                    + " buttonY=" + cmd.i
                    + " selected=" + cmd.g);
        } else {
            System.err.println("[GHOSTDBG] MENU_ITEM seq=" + seq
                    + " idx=" + index
                    + " class=\"" + getClassName(item) + "\""
                    + " value=" + formatDebugValue(item));
        }
    }

    private static String buildMenuTraceSignature() {
        fr menu = fu.p;
        if (menu == null || !menu.a) {
            return "inactive";
        }
        et items = getMenuItems(menu);
        StringBuffer sb = new StringBuffer();
        sb.append("title=").append(getPrivateString(menu, "v", ""));
        sb.append("|idNPC=").append(getPrivateInt(menu, "C", Integer.MIN_VALUE));
        sb.append("|idMenu=").append(getPrivateInt(menu, "B", Integer.MIN_VALUE));
        sb.append("|selected=").append(getPrivateInt(menu, "h", -1));
        sb.append("|count=").append(safeEtSize(items));
        if (items != null) {
            for (int i = 0; i < items.c(); i++) {
                sb.append('|').append(i).append('=').append(getMenuItemText(items, i));
            }
        }
        return sb.toString();
    }

    private static int getMenuSelectedIndex() {
        if (fu.p == null) {
            return -1;
        }
        return getPrivateInt(fu.p, "h", -1);
    }

    private static et getMenuItems(Object menu) {
        Object items = getPrivateField(menu, "g");
        return items instanceof et ? (et) items : null;
    }

    private static String getCurrentMenuItemText(int index) {
        if (fu.p == null) {
            return "";
        }
        return getMenuItemText(getMenuItems(fu.p), index);
    }

    private static String getMenuItemText(et items, int index) {
        if (items == null || index < 0 || index >= items.c()) {
            return "";
        }
        Object item = items.a(index);
        if (item instanceof bt) {
            return ((bt) item).a;
        }
        return getClassName(item);
    }

    private static int findMenuIndexAt(int x, int y) {
        if (fu.p == null || !fu.p.a) {
            return -1;
        }
        et items = getMenuItems(fu.p);
        if (items == null || items.c() == 0) {
            return -1;
        }

        int layoutX = getPrivateInt(fu.p, "j", -1);
        int layoutY = getPrivateInt(fu.p, "k", -1);
        int width = getPrivateInt(fu.p, "O", -1);
        int height = getPrivateInt(fu.p, "m", -1);
        int itemHeight = getPrivateInt(fu.p, "l", -1);
        int scroll = getPrivateStaticInt(fr.class, "q", 0);

        if (itemHeight > 0
                && layoutX >= 0
                && layoutY >= 0
                && width > 0
                && height > 0
                && x >= layoutX
                && x <= layoutX + width
                && y >= layoutY
                && y <= layoutY + height) {
            int index = (scroll + y - layoutY) / itemHeight;
            if (index >= 0 && index < items.c()) {
                return index;
            }
        }

        for (int i = 0; i < items.c(); i++) {
            Object item = items.a(i);
            if (item instanceof bt) {
                bt cmd = (bt) item;
                if (cmd.h >= 0 && cmd.i >= 0
                        && x >= cmd.h - bt.j / 2
                        && x <= cmd.h + bt.j / 2
                        && y >= cmd.i - bt.k / 2
                        && y <= cmd.i + bt.k / 2) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Object getPrivateField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object getPrivateStaticField(Class clazz, String fieldName) {
        if (clazz == null) {
            return null;
        }
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int getPrivateInt(Object target, String fieldName, int fallback) {
        Object value = getPrivateField(target, fieldName);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static int getPrivateStaticInt(Class clazz, String fieldName, int fallback) {
        Object value = getPrivateStaticField(clazz, fieldName);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static String getPrivateString(Object target, String fieldName, String fallback) {
        Object value = getPrivateField(target, fieldName);
        return value instanceof String ? (String) value : fallback;
    }

    private static void setDebugEnabled(boolean enabled, String reason) {
        debugEnabled = enabled;
        resetDebugMapState();
        System.err.println("[GHOSTDBG] MODE enabled=" + enabled
                + " reason=\"" + sanitizeForLog(reason) + "\""
                + " build=\"" + BUILD_TAG + "\"");
        if (enabled) {
            printDebugLegendIfNeeded();
            dumpDebugSnapshot("enable", false);
        }
    }

    private static void logMapChange(int toMapId, byte toZone, String toMapName) {
        PortalSnapshot nearestPortal = findNearestPortal(debugLastPortals, debugLastPlayerPixelX, debugLastPlayerPixelY);
        int viaPortalIdx = -1;
        String viaPortalLabel = "";
        int viaTileX = -1;
        int viaTileY = -1;

        if (nearestPortal != null) {
            viaPortalIdx = nearestPortal.idx;
            viaPortalLabel = nearestPortal.label;
            viaTileX = nearestPortal.tileX;
            viaTileY = nearestPortal.tileY;
        }

        System.err.println("[GHOSTDBG] MAP_CHANGE fromId=" + debugLastMapId
                + " fromZone=" + debugLastZone
                + " fromName=\"" + sanitizeForLog(debugLastMapName) + "\""
                + " lastTileX=" + debugLastPlayerTileX
                + " lastTileY=" + debugLastPlayerTileY
                + " viaPortalIdx=" + viaPortalIdx
                + " viaPortalLabel=\"" + sanitizeForLog(viaPortalLabel) + "\""
                + " viaTileX=" + viaTileX
                + " viaTileY=" + viaTileY
                + " toId=" + toMapId
                + " toZone=" + toZone
                + " toName=\"" + sanitizeForLog(toMapName) + "\"");
    }

    private static void dumpCurrentMap(int mapId, byte zone, String mapName, int playerPixelX, int playerPixelY, int playerTileX, int playerTileY, PortalSnapshot[] portals) {
        debugDumpSeq++;
        int widthTiles = (fu.q != null) ? fu.q.e : -1;
        int heightTiles = (fu.q != null) ? fu.q.f : -1;

        System.err.println("[GHOSTDBG] MAP_BEGIN seq=" + debugDumpSeq);
        System.err.println("[GHOSTDBG] MAP_META mapId=" + mapId
                + " zone=" + zone
                + " mapName=\"" + sanitizeForLog(mapName) + "\""
                + " widthTiles=" + widthTiles
                + " heightTiles=" + heightTiles
                + " tileSize=" + cs.l);
        System.err.println("[GHOSTDBG] PLAYER pixelX=" + playerPixelX
                + " pixelY=" + playerPixelY
                + " tileX=" + playerTileX
                + " tileY=" + playerTileY);

        for (int i = 0; i < portals.length; i++) {
            PortalSnapshot portal = portals[i];
            System.err.println("[GHOSTDBG] PORTAL idx=" + portal.idx
                    + " label=\"" + sanitizeForLog(portal.label) + "\""
                    + " pixelX=" + portal.pixelX
                    + " pixelY=" + portal.pixelY
                    + " tileX=" + portal.tileX
                    + " tileY=" + portal.tileY
                    + " side=" + portal.side);
        }

        dumpWalkabilityGrid(portals, playerTileX, playerTileY);
        System.err.println("[GHOSTDBG] MAP_END seq=" + debugDumpSeq);
    }

    private static void dumpWalkabilityGrid(PortalSnapshot[] portals, int playerTileX, int playerTileY) {
        if (fu.q == null || fu.q.n == null) {
            return;
        }

        int width = fu.q.e;
        int height = fu.q.f;
        if (width <= 0 || height <= 0) {
            return;
        }

        char[] portalOverlay = new char[width * height];
        for (int i = 0; i < portals.length; i++) {
            PortalSnapshot portal = portals[i];
            if (portal.tileX >= 0 && portal.tileX < width && portal.tileY >= 0 && portal.tileY < height) {
                portalOverlay[portal.tileY * width + portal.tileX] = 'P';
            }
        }

        for (int y = 0; y < height; y++) {
            StringBuffer row = new StringBuffer(width);
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int cell = (idx < fu.q.n.length) ? fu.q.n[idx] : 1;
                char marker;

                if (cell == 2) {
                    marker = '~';
                } else if (cell == 1 || cell == -1) {
                    marker = '#';
                } else {
                    marker = '.';
                }

                if (portalOverlay[idx] == 'P') {
                    marker = 'P';
                }
                if (x == playerTileX && y == playerTileY) {
                    marker = (portalOverlay[idx] == 'P') ? '*' : 'S';
                }
                row.append(marker);
            }
            System.err.println("[GHOSTDBG] GRID_ROW y=" + y + " data=" + row.toString());
        }
    }

    private static PortalSnapshot[] buildPortalSnapshots() {
        if (cs.a == null) {
            return new PortalSnapshot[0];
        }

        int count = cs.a.c();
        if (count <= 0) {
            return new PortalSnapshot[0];
        }

        PortalSnapshot[] portals = new PortalSnapshot[count];
        for (int i = 0; i < count; i++) {
            eo portal = (eo) cs.a.a(i);
            if (portal == null) {
                portals[i] = new PortalSnapshot(i, "", -1, -1, -1, -1, -1);
            } else {
                portals[i] = new PortalSnapshot(
                        i,
                        portal.t,
                        portal.a,
                        portal.b,
                        toTile(portal.a),
                        toTile(portal.b),
                        portal.n
                );
            }
        }
        return portals;
    }

    private static PortalSnapshot findNearestPortal(PortalSnapshot[] portals, int pixelX, int pixelY) {
        if (portals == null || portals.length == 0 || pixelX < 0 || pixelY < 0) {
            return null;
        }

        PortalSnapshot nearest = null;
        long bestDistance = Long.MAX_VALUE;

        for (int i = 0; i < portals.length; i++) {
            PortalSnapshot portal = portals[i];
            if (portal == null || portal.pixelX < 0 || portal.pixelY < 0) {
                continue;
            }

            long dx = portal.pixelX - pixelX;
            long dy = portal.pixelY - pixelY;
            long distance = (dx * dx) + (dy * dy);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = portal;
            }
        }
        return nearest;
    }

    private static String resolveMapName(int mapId) {
        if (df.gE != null && mapId >= 0 && mapId < df.gE.length) {
            String mapName = df.gE[mapId];
            if (mapName != null && mapName.trim().length() > 0) {
                return mapName;
            }
        }
        return "Map " + mapId;
    }

    private static int toTile(int pixel) {
        if (cs.l <= 0 || pixel < 0) {
            return -1;
        }
        return pixel / cs.l;
    }

    private static String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('"', '\'');
    }

    private static void dumpDebugSnapshot(String reason, boolean includeFullFields) {
        int seq = ++debugSnapshotSeq;
        System.err.println("[GHOSTDBG] STATE_BEGIN seq=" + seq
                + " reason=\"" + sanitizeForLog(reason) + "\""
                + " full=" + includeFullFields
                + " timeMs=" + System.currentTimeMillis());
        try {
            dumpDebugUiState();
            dumpDebugMapState();
            dumpDebugModState();
            logFaState("PLAYER", -1, cn.g);
            logFaState("TARGET", -1, cn.i);
            dumpDebugPortalList();
            dumpDebugEntityList(DEBUG_ENTITY_DUMP_LIMIT);
            if (includeFullFields) {
                dumpPublicStaticFields("FU_STATIC", fu.class);
                dumpPublicStaticFields("CN_STATIC", cn.class);
                dumpPublicStaticFields("CS_STATIC", cs.class);
                dumpPublicStaticFields("BQ_STATIC", bq.class);
                dumpPublicObjectFields("PLAYER_FIELDS", cn.g);
                dumpPublicObjectFields("TARGET_FIELDS", cn.i);
            }
        } catch (Throwable t) {
            System.err.println("[GHOSTDBG] STATE_ERROR seq=" + seq + " error=" + t.toString());
        }
        System.err.println("[GHOSTDBG] STATE_END seq=" + seq);
    }

    private static void dumpDebugMapNow(String reason) {
        if (!isActiveMapContext()) {
            System.err.println("[GHOSTDBG] MAP_DUMP_SKIPPED reason=\"" + sanitizeForLog(reason) + "\" active=false");
            return;
        }

        PortalSnapshot[] portals = buildPortalSnapshots();
        dumpCurrentMap(fu.q.d, cs.b(), resolveMapName(fu.q.d),
                cn.g.aZ, cn.g.ba, toTile(cn.g.aZ), toTile(cn.g.ba), portals);
    }

    private static void dumpDebugUiState() {
        boolean hasDialog = fu.s != null || fu.t != null;
        boolean hasMenu = fu.p != null && fu.p.a;
        System.err.println("[GHOSTDBG] UI activeScreen=\"" + getClassName(fu.a) + "\""
                + " mapScreen=\"" + getClassName(fu.c) + "\""
                + " dialogS=" + (fu.s != null)
                + " dialogT=" + (fu.t != null)
                + " menu=" + hasMenu
                + " menuClass=\"" + getClassName(fu.p) + "\""
                + " screenW=" + fu.X
                + " screenH=" + fu.Y
                + " keyDownCount=" + countTrue(fu.al)
                + " keyPressedCount=" + countTrue(fu.am)
                + " hasDialog=" + hasDialog);
    }

    private static void dumpDebugMapState() {
        int mapId = (fu.q != null) ? fu.q.d : -1;
        int widthTiles = (fu.q != null) ? fu.q.e : -1;
        int heightTiles = (fu.q != null) ? fu.q.f : -1;
        System.err.println("[GHOSTDBG] MAP_STATE active=" + isActiveMapContext()
                + " mapId=" + mapId
                + " zone=" + cs.b()
                + " mapName=\"" + sanitizeForLog(resolveMapName(mapId)) + "\""
                + " widthTiles=" + widthTiles
                + " heightTiles=" + heightTiles
                + " tileSize=" + cs.l
                + " portals=" + safeEtSize(cs.a)
                + " entities=" + safeEtSize(cn.j)
                + " effects=" + safeEtSize(cn.m)
                + " weather=" + safeEtSize(cn.p));
    }

    private static void dumpDebugModState() {
        System.err.println("[GHOSTDBG] MOD_STATE build=\"" + BUILD_TAG + "\""
                + " debugEnabled=" + debugEnabled
                + " menuTraceEnabled=" + menuTraceEnabled
                + " recoveryInProgress=" + recoveryInProgress
                + " recoveryPendingCharacter=\"" + sanitizeForLog(recoveryPendingCharacterName) + "\""
                + " lastKnownCharacter=\"" + sanitizeForLog(lastKnownCharacterName) + "\""
                + " lastLiveCharacter=\"" + sanitizeForLog(lastLiveCharacterName) + "\""
                + " stateHadLiveMapContext=" + stateHadLiveMapContext
                + " autoAttackSuppressed=" + autoAttackSuppressed
                + " kickstartTriggered=" + kickstartTriggered
                + " autoBuffWorkerRunning=" + autoBuffWorkerRunning
                + " targetRefreshPending=" + targetRefreshPending
                + " postLoginOverlayWatchActive=" + postLoginOverlayWatchActive
                + " postLoginSetupPending=" + postLoginSetupPending
                + " savedPoints=" + savedPointsByCharacter.size());
        System.err.println("[GHOSTDBG] COMBAT_STATE autoState=" + bq.o
                + " autoPersistent=" + bq.Y
                + " autoActive=" + isPersistentAutoAttackActive()
                + " pathTargetX=" + bq.O
                + " pathTargetY=" + bq.P
                + " pathMode=" + bq.Q
                + " globalTarget=\"" + getClassName(cn.i) + "\"");
        logAutoFeatureState("snapshot");
    }

    private static void dumpDebugPortalList() {
        PortalSnapshot[] portals = buildPortalSnapshots();
        System.err.println("[GHOSTDBG] PORTALS_BEGIN count=" + portals.length);
        for (int i = 0; i < portals.length; i++) {
            PortalSnapshot portal = portals[i];
            System.err.println("[GHOSTDBG] PORTAL_STATE idx=" + portal.idx
                    + " label=\"" + sanitizeForLog(portal.label) + "\""
                    + " pixelX=" + portal.pixelX
                    + " pixelY=" + portal.pixelY
                    + " tileX=" + portal.tileX
                    + " tileY=" + portal.tileY
                    + " side=" + portal.side);
        }
        System.err.println("[GHOSTDBG] PORTALS_END count=" + portals.length);
    }

    private static void dumpDebugEntityList(int limit) {
        if (cn.j == null) {
            System.err.println("[GHOSTDBG] ENTITIES none");
            return;
        }

        int size = cn.j.c();
        int dumpCount = size < limit ? size : limit;
        System.err.println("[GHOSTDBG] ENTITIES_BEGIN total=" + size
                + " limit=" + limit
                + " summary=\"" + sanitizeForLog(buildEntitySummary()) + "\"");
        for (int i = 0; i < dumpCount; i++) {
            Object object = cn.j.a(i);
            if (object instanceof fa) {
                logFaState("ENTITY", i, (fa) object);
            } else {
                System.err.println("[GHOSTDBG] ENTITY idx=" + i
                        + " class=\"" + getClassName(object) + "\"");
            }
        }
        if (size > limit) {
            System.err.println("[GHOSTDBG] ENTITIES_TRUNCATED hidden=" + (size - limit));
        }
        System.err.println("[GHOSTDBG] ENTITIES_END total=" + size);
    }

    private static void logFaState(String tag, int idx, fa target) {
        if (target == null) {
            System.err.println("[GHOSTDBG] " + tag + " null");
            return;
        }

        int pathLength = (target.cO != null) ? target.cO.length : -1;
        int buffCount = (target.dg != null) ? target.dg.c() : -1;
        System.err.println("[GHOSTDBG] " + tag
                + (idx >= 0 ? " idx=" + idx : "")
                + " type=\"" + classifyEntity(target) + "\""
                + " class=\"" + target.getClass().getName() + "\""
                + " id=" + target.cu
                + " name=\"" + sanitizeForLog(target.cC) + "\""
                + " cv=" + target.cv
                + " cy=" + target.cy
                + " cG=" + target.cG
                + " cH=" + target.cH
                + " cI=" + target.cI
                + " cJ=" + target.cJ
                + " dm=" + target.dm
                + " pixelX=" + target.aZ
                + " pixelY=" + target.ba
                + " tileX=" + toTile(target.aZ)
                + " tileY=" + toTile(target.ba)
                + " targetX=" + target.bm
                + " targetY=" + target.bn
                + " pathLength=" + pathLength
                + " buffs=" + buffCount
                + " deadFlag=" + (target.cG == 4)
                + " hiddenFlag=" + target.cL
                + " bJ=" + target.bJ
                + " dz=" + target.dz);
    }

    private static String classifyEntity(fa target) {
        if (target == null) return "null";
        if (target == cn.g) return "self";
        if (target instanceof ba) return "board";
        if (target instanceof fv) return "drop";
        if (target instanceof cu || target instanceof cv || target instanceof s) return "special";
        if (target instanceof cc || target instanceof ac || target instanceof a) return "monster";
        if (target.cv == 0) return "player";
        return "object";
    }

    private static void dumpPublicStaticFields(String label, Class clazz) {
        java.lang.reflect.Field[] fields = clazz.getFields();
        System.err.println("[GHOSTDBG] " + label + "_BEGIN class=\"" + clazz.getName() + "\" count=" + fields.length);
        for (int i = 0; i < fields.length; i++) {
            java.lang.reflect.Field field = fields[i];
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                System.err.println("[GHOSTDBG] " + label
                        + " field=\"" + field.getName() + "\""
                        + " type=\"" + field.getType().getName() + "\""
                        + " value=" + formatDebugValue(field.get(null)));
            } catch (Throwable t) {
                System.err.println("[GHOSTDBG] " + label
                        + " field=\"" + field.getName() + "\""
                        + " error=\"" + sanitizeForLog(t.toString()) + "\"");
            }
        }
        System.err.println("[GHOSTDBG] " + label + "_END class=\"" + clazz.getName() + "\"");
    }

    private static void dumpPublicObjectFields(String label, Object object) {
        if (object == null) {
            System.err.println("[GHOSTDBG] " + label + " null");
            return;
        }

        Class clazz = object.getClass();
        java.lang.reflect.Field[] fields = clazz.getFields();
        System.err.println("[GHOSTDBG] " + label + "_BEGIN class=\"" + clazz.getName() + "\" count=" + fields.length);
        for (int i = 0; i < fields.length; i++) {
            java.lang.reflect.Field field = fields[i];
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                System.err.println("[GHOSTDBG] " + label
                        + " field=\"" + field.getName() + "\""
                        + " type=\"" + field.getType().getName() + "\""
                        + " value=" + formatDebugValue(field.get(object)));
            } catch (Throwable t) {
                System.err.println("[GHOSTDBG] " + label
                        + " field=\"" + field.getName() + "\""
                        + " error=\"" + sanitizeForLog(t.toString()) + "\"");
            }
        }
        System.err.println("[GHOSTDBG] " + label + "_END class=\"" + clazz.getName() + "\"");
    }

    private static String formatDebugValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + sanitizeForLog((String) value) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return String.valueOf(value);
        }
        if (value instanceof et) {
            return "et(size=" + ((et) value).c() + ")";
        }
        if (value instanceof fa) {
            fa target = (fa) value;
            return "fa(class=\"" + target.getClass().getName()
                    + "\",id=" + target.cu
                    + ",name=\"" + sanitizeForLog(target.cC)
                    + "\",x=" + target.aZ
                    + ",y=" + target.ba + ")";
        }

        Class clazz = value.getClass();
        if (clazz.isArray()) {
            return clazz.getComponentType().getName()
                    + "[" + java.lang.reflect.Array.getLength(value) + "]";
        }
        return "\"" + clazz.getName() + "\"";
    }

    private static String getClassName(Object object) {
        return object == null ? "null" : object.getClass().getName();
    }

    private static int safeEtSize(et vector) {
        return vector == null ? -1 : vector.c();
    }

    private static int countTrue(boolean[] values) {
        if (values == null) {
            return -1;
        }
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i]) {
                count++;
            }
        }
        return count;
    }

    private static RouteEdge[] buildRouteGraph() {
        return new RouteEdge[] {
                edge(33, 25, 0),
                edge(33, 34, 1),
                edge(34, 33, 0),
                edge(34, 20, 1),
                edge(20, 34, 0),
                edge(20, 23, 1),
                edge(23, 20, 0),
                edge(23, 24, 1),
                edge(24, 23, 2),
                edge(24, 22, 0),
                edge(22, 24, 0),
                edge(22, 42, 1),
                edge(42, 22, 0),
                edge(42, 43, 1),
                edge(43, 42, 0),
                edge(43, 44, 1),
                edge(44, 43, 0),
                edge(44, 45, 1),
                edge(45, 44, 0),
                edge(45, 52, 1),
                edge(52, 45, 0),
                edge(24, 19, 3),
                edge(19, 24, 0),
                edge(24, 21, 1),
                edge(21, 24, 0),
                edge(21, 37, 1),
                edge(37, 21, 0),
                edge(37, 38, 1),
                edge(38, 37, 0),
                edge(38, 39, 1),
                edge(39, 38, 0),
                edge(39, 40, 1),
                edge(40, 39, 0),
                edge(40, 41, 1),
                edge(41, 40, 0),
                edge(41, 51, 1),
                edge(51, 41, 0)
        };
    }

    private static RouteEdge edge(int fromMapId, int toMapId, int portalIndex) {
        return new RouteEdge(fromMapId, toMapId, portalIndex);
    }

    private static SavedPoint getSavedPoint(String characterName) {
        if (characterName == null || characterName.length() == 0) {
            return null;
        }
        return (SavedPoint) savedPointsByCharacter.get(characterName);
    }

    private static String getCurrentCharacterName() {
        return (cn.g != null) ? cn.g.cC : null;
    }

    private static boolean isActiveMapContext() {
        return fu.a != null && fu.c != null && fu.a == fu.c && cn.g != null && fu.q != null;
    }

    private static boolean isNearPosition(int targetX, int targetY, int tolerancePx) {
        if (cn.g == null) {
            return false;
        }
        return ak.e(cn.g.aZ - targetX) <= tolerancePx && ak.e(cn.g.ba - targetY) <= tolerancePx;
    }

    public static boolean interceptChatCommand(String message) {
        if (message == null) {
            return false;
        }

        String normalized = message.trim();
        if (normalized.length() == 0) {
            return false;
        }
        String lower = normalized.toLowerCase();
        if ("debug".equals(lower) || lower.startsWith("debug ")) {
            return handleDebugCommand(lower);
        }
        if ("save".equalsIgnoreCase(normalized)) {
            return handleSaveCommand();
        }
        if ("save clear".equalsIgnoreCase(normalized) || "unsave".equalsIgnoreCase(normalized)) {
            return handleClearSaveCommand();
        }
        if ("auto feed".equals(lower)) {
            return handleAutoFeedCommand();
        }
        if (lower.startsWith("feed")) {
            String[] parts = lower.split(" ");
            short forcePetId = -1;
            if (parts.length > 1) {
                try {
                    forcePetId = Short.parseShort(parts[1]);
                } catch (Exception e) {}
            }
            return handleFeedCommand(forcePetId);
        }
        if ("dump".equalsIgnoreCase(normalized)) {
            return handleDumpCommand();
        }
        return false;
    }

    private static boolean handleDumpCommand() {
        System.err.println("GhostMod: [Dump] Tying to dump inventory arrays...");
        new Thread(new Runnable() {
            public void run() {
                try {
                    System.err.println("=== DUMPING INVENTORY (bw.V) ===");
                    if (bw.V != null) {
                        for (int i=0; i<bw.V.c(); i++) {
                            bw item = (bw) bw.V.a(i);
                            if (item != null && item.u == 9) {
                                System.err.println("bw.V["+i+"] name=" + item.g + ", O=" + item.O + ", u=" + item.u + ", N=" + item.N + ", H=" + item.H);
                                if (item instanceof eb) {
                                    eb e = (eb)item;
                                    System.err.println("   -> is eb! ao=" + e.ao + ", an=" + e.an + ", am=" + e.am);
                                }
                            }
                        }
                    } else System.err.println("bw.V is null");

                    System.err.println("=== DUMPING GARDEN (bw.X) ===");
                    if (bw.X != null) {
                        for (int i=0; i<bw.X.c(); i++) {
                            bw item = (bw) bw.X.a(i);
                            if (item != null) {
                                System.err.println("bw.X["+i+"] name=" + item.g + ", O=" + item.O + ", u=" + item.u + ", N=" + item.N + ", H=" + item.H);
                                if (item instanceof eb) {
                                    eb e = (eb)item;
                                    System.err.println("   -> is eb! ao=" + e.ao + ", an=" + e.an + ", am=" + e.am);
                                }
                            }
                        }
                    } else System.err.println("bw.X is null");

                    System.err.println("=== DUMPING EQUIPMENT (bq.r) ===");
                    if (bq.r != null) {
                        for (int i=0; i<bq.r.length; i++) {
                            if (bq.r[i] != null) {
                                System.err.println("bq.r["+i+"] a=" + bq.r[i].a + ", b=" + bq.r[i].b + ", c=" + bq.r[i].c + ", d=" + bq.r[i].d);
                            }
                        }
                    } else System.err.println("bq.r is null");

                    System.err.println("=== DUMPING ENTITIES (cn.j) ===");
                    if (cn.j != null) {
                        for (int i=0; i<cn.j.c(); i++) {
                            Object obj = cn.j.a(i);
                            if (obj instanceof s) {
                                s e = (s)obj;
                                boolean isMine = (e.w == cn.g);
                                System.err.println("cn.j["+i+"] isMine=" + isMine + ", class=" + e.getClass().getName() + ", cu=" + e.cu + ", cv=" + e.cv + ", N=" + e.N + ", j=" + e.j + ", u=" + e.u + ", t=" + e.t);
                            }
                        }
                    } else System.err.println("cn.j is null");
                } catch(Exception e) { e.printStackTrace(); }
            }
        }).start();
        return true;
    }

    public static void hookFeedPacket(short s2, short s3, byte by2, byte by3) {
        System.err.println("GhostMod: [HOOK] Packet 45 (Feed) SENT! petId=" + s2 + ", itemId=" + s3 + ", itemType=" + by2 + ", slot=" + by3);
        if (s2 >= 0 && GhostMod.cachedPetId != s2) {
            GhostMod.cachedPetId = s2;
            GhostMod.cachedPetName = "Learned Pet (ID " + s2 + ")";
            System.err.println("GhostMod: [PetFeed] Da tu dong hoc ID pet dang trang bi: " + s2);
        }
    }

    public static void hookOpcode44(byte by2, short s2) {
        System.err.println("GhostMod: [HOOK] Packet 44 (Interact) SENT! by2=" + by2 + ", s2=" + s2);
    }

    public static void logOutgoingPacket(byte by2) {
        // Ignored packets to avoid spam: 1(ping?), 5(move?), etc. (Adjust as needed)
        if (by2 != 5 && by2 != 4 && by2 != -44 && by2 != 7 && by2 != 1) {
            System.err.println("GhostMod: [PACKET-HOOK] Sending opcode: " + by2);
        }
    }

    public static void hookOpcode55(byte by2, byte by3, byte[] byArray) {
        if (!menuTraceEnabled && !debugEnabled) return;
        try {
            System.err.println("[GHOSTDBG] OPCODE55 by2=" + by2
                    + " by3=" + by3
                    + " len=" + (byArray == null ? 0 : byArray.length)
                    + " bytes=" + formatByteArrayForLog(byArray)
                    + " replay=\"q.a().a((byte)" + by2
                    + ", (byte)" + by3
                    + ", " + formatByteArrayForReplay(byArray) + ");\"");
        } catch (Throwable t) {
            System.err.println("[GHOSTDBG] OPCODE55_ERROR " + sanitizeForLog(t.toString()));
        }
    }

    private static String formatByteArrayForLog(byte[] data) {
        if (data == null) {
            return "null";
        }
        StringBuffer sb = new StringBuffer();
        sb.append('[');
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(data[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private static String formatByteArrayForReplay(byte[] data) {
        if (data == null) {
            return "(byte[])null";
        }
        StringBuffer sb = new StringBuffer();
        sb.append("new byte[]{");
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("(byte)").append(data[i]);
        }
        sb.append('}');
        return sb.toString();
    }

    private static void requestAutoBuffEnable(final String reason, final long delayMs) {
        synchronized (GhostMod.class) {
            if (autoBuffWorkerRunning) {
                System.err.println("GhostMod: [AutoBuff] Request ignored - worker already running reason=" + reason);
                return;
            }
            autoBuffWorkerRunning = true;
        }

        System.err.println("GhostMod: [AutoBuff] Auto enable scheduled reason=" + reason
                + " delayMs=" + delayMs);
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (delayMs > 0L) {
                        Thread.sleep(delayMs);
                    }
                    runEnableAutoBuffSlots(reason);
                } catch (Throwable t) {
                    System.err.println("GhostMod: [AutoBuff] Error: " + t.toString());
                    t.printStackTrace();
                } finally {
                    synchronized (GhostMod.class) {
                        autoBuffWorkerRunning = false;
                    }
                }
            }
        }).start();
    }

    private static void runEnableAutoBuffSlots(String reason) throws Exception {
        waitForLiveCharacter(8000L);
        waitForAutoBuffConfig(8000L);
        Thread.sleep(300L);

        int slotCount = getAutoBuffSlotCount();
        if (ah.f == null || slotCount <= 0) {
            System.err.println("GhostMod: [AutoBuff] Config not available, skip enable reason=" + reason);
            return;
        }

        int limit = AUTO_BUFF_REQUIRED_SLOTS;
        if (limit > slotCount) {
            limit = slotCount;
        }
        if (limit > ah.f.length) {
            limit = ah.f.length;
        }

        boolean changed = false;
        boolean anyEnabled = false;
        int beforeBuffMode = bq.p;
        StringBuffer details = new StringBuffer();
        for (int slot = 0; slot < limit; slot++) {
            if (ah.f[slot] == null || ah.f[slot].length < 2) {
                details.append(" slot").append(slot).append("=missing");
                continue;
            }

            int skillIndex = ah.f[slot][0];
            int before = ah.f[slot][1];
            boolean available = isAutoBuffSkillAvailable(skillIndex);
            if (!available) {
                details.append(" slot").append(slot)
                        .append("(skill=").append(skillIndex)
                        .append(")=unavailable before=").append(before);
                continue;
            }

            if (before != 1) {
                ah.f[slot][1] = 1;
                changed = true;
            }
            anyEnabled = true;
            details.append(" slot").append(slot)
                    .append("(skill=").append(skillIndex)
                    .append(")=").append(before).append("->").append(ah.f[slot][1]);
        }

        if (anyEnabled) {
            bq.p = 1;
            if (beforeBuffMode != bq.p) {
                changed = true;
            }
        }

        if (changed) {
            co.b();
            System.err.println("GhostMod: [AutoBuff] Enabled first " + limit
                    + " buff slots reason=" + reason
                    + " detail:" + details.toString()
                    + " replay=\"co.b(); // opcode55 by2=0 by3=3 saves auto config\"");
        } else {
            System.err.println("GhostMod: [AutoBuff] Buff slots already enabled or unavailable reason=" + reason
                    + " detail:" + details.toString());
        }
    }

    private static boolean waitForAutoBuffConfig(long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (ah.f != null && getAutoBuffSlotCount() >= AUTO_BUFF_REQUIRED_SLOTS) {
                return true;
            }
            Thread.sleep(100L);
        }
        System.err.println("GhostMod: [AutoBuff] Auto buff config not fully loaded before timeout");
        return false;
    }

    private static int getAutoBuffSlotCount() {
        int count = ah.b;
        if (count < 0) {
            count += 256;
        }
        return count;
    }

    private static boolean isAutoBuffSkillAvailable(int skillIndex) {
        try {
            return bq.I == null || skillIndex < 0 || skillIndex >= bq.I.length || bq.I[skillIndex] > 0;
        } catch (Throwable t) {
            return true;
        }
    }

    private static void requestWhiteMedalDropClose(final String reason, final long delayMs) {
        synchronized (GhostMod.class) {
            if (medalDropWorkerRunning || autoBuffWorkerRunning) {
                System.err.println("GhostMod: [PostKickstart] Request ignored - worker already running reason=" + reason
                        + " medalRunning=" + medalDropWorkerRunning
                        + " autoBuffRunning=" + autoBuffWorkerRunning);
                return;
            }
            medalDropWorkerRunning = true;
            autoBuffWorkerRunning = true;
        }

        System.err.println("GhostMod: [PostKickstart] Medal close then auto buff scheduled reason=" + reason
                + " medalDelayMs=" + delayMs
                + " postMedalAutoBuffDelayMs=" + POST_MEDAL_AUTO_BUFF_DELAY_MS);
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (delayMs > 0L) {
                        Thread.sleep(delayMs);
                    }
                    try {
                        runCloseWhiteMedalDrop();
                    } catch (Throwable t) {
                        System.err.println("GhostMod: [MedalDrop] Error: " + t.toString());
                        t.printStackTrace();
                    }

                    System.err.println("GhostMod: [PostKickstart] Waiting " + POST_MEDAL_AUTO_BUFF_DELAY_MS
                            + "ms after medal before enabling auto buff");
                    Thread.sleep(POST_MEDAL_AUTO_BUFF_DELAY_MS);
                    runEnableAutoBuffSlots("after_medal_" + reason);
                } catch (Throwable t) {
                    System.err.println("GhostMod: [PostKickstart] Error: " + t.toString());
                    t.printStackTrace();
                } finally {
                    synchronized (GhostMod.class) {
                        medalDropWorkerRunning = false;
                        autoBuffWorkerRunning = false;
                    }
                }
            }
        }).start();
    }

    private static void runCloseWhiteMedalDrop() throws Exception {
        waitForLiveCharacter(5000L);
        Thread.sleep(300L);

        System.err.println("GhostMod: [MedalDrop] Step 1 replay q.a().b((short)125, (byte)125, (byte)0)");
        q.a().b(MEDAL_MATERIAL_ROOT_NPC, MEDAL_MATERIAL_ROOT_MENU, MEDAL_WHITE_DROP_INDEX);

        System.err.println("GhostMod: [MedalDrop] Waiting " + MEDAL_PACKET_DELAY_MS + "ms before selecting white medal action");
        Thread.sleep(MEDAL_PACKET_DELAY_MS);

        boolean submenuSeen = waitForMenuContext(MEDAL_WHITE_DROP_NPC, MEDAL_WHITE_DROP_MENU, MEDAL_MENU_WAIT_MS);
        System.err.println("GhostMod: [MedalDrop] Material submenu seen=" + submenuSeen);
        String actionText = submenuSeen ? getCurrentMenuItemText(MEDAL_WHITE_DROP_INDEX) : "";
        if (submenuSeen) {
            System.err.println("GhostMod: [MedalDrop] White medal action text=\"" + sanitizeForLog(actionText) + "\"");
        }
        if (isMedalDropOpenAction(actionText)) {
            if (fu.p != null) {
                fu.p.a = false;
            }
            System.err.println("GhostMod: [MedalDrop] White medal drop already appears closed, skip toggle");
            return;
        }

        System.err.println("GhostMod: [MedalDrop] Step 2 replay q.a().b((short)-125, (byte)-125, (byte)0)");
        q.a().b(MEDAL_WHITE_DROP_NPC, MEDAL_WHITE_DROP_MENU, MEDAL_WHITE_DROP_INDEX);
        if (fu.p != null) {
            fu.p.a = false;
        }

        dismissMedalDropDialogIfPresent();
        System.err.println("GhostMod: [MedalDrop] White medal material drop close command DONE");
    }

    private static boolean isMedalDropOpenAction(String actionText) {
        if (actionText == null) {
            return false;
        }
        String normalized = actionText.trim().toLowerCase();
        if (normalized.length() == 0) {
            return false;
        }
        return normalized.charAt(0) == 'm';
    }

    private static boolean waitForLiveCharacter(long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (cn.g != null) {
                return true;
            }
            Thread.sleep(100L);
        }
        System.err.println("GhostMod: [MedalDrop] Live character not detected before timeout, sending packet anyway");
        return false;
    }

    private static boolean waitForMenuContext(short idNPC, byte idMenu, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (fu.p != null && fu.p.a
                    && getPrivateInt(fu.p, "C", Integer.MIN_VALUE) == idNPC
                    && getPrivateInt(fu.p, "B", Integer.MIN_VALUE) == idMenu) {
                return true;
            }
            Thread.sleep(100L);
        }
        return false;
    }

    private static void dismissMedalDropDialogIfPresent() throws Exception {
        for (int i = 0; i < 12; i++) {
            Thread.sleep(250L);
            if (fu.s != null || fu.t != null) {
                System.err.println("GhostMod: [MedalDrop] Confirmation dialog detected, pressing OK");
                pressEnterWithLog("meday_white_drop", 220);
                Thread.sleep(350L);
                if (fu.s != null || fu.t != null) {
                    fu.s = null;
                    fu.t = null;
                    System.err.println("GhostMod: [MedalDrop] Confirmation dialog cleared directly");
                }
                return;
            }
        }
    }

    private static boolean handleAutoFeedCommand() {
        if (autoPetFeedStarted) {
            System.err.println("GhostMod: [PetFeed] Daily scheduler already active, chat command 'auto feed' ignored");
            return true;
        }

        System.err.println("GhostMod: [PetFeed] Chat command 'auto feed' received, scheduling daily feed at " + AUTO_FEED_HOUR + ":00");
        startAutoPetFeedScheduler();
        return true; // true = nuot lenh, khong gui len server
    }

    private static boolean handleFeedCommand(final short forcePetId) {
        System.err.println("GhostMod: [PetFeed] Chat command 'feed' received, will trigger in 5s...");
        new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(5000L);
                    System.err.println("GhostMod: [PetFeed] Chat trigger firing now");
                    runAutoPetFeed(forcePetId);
                } catch (Exception e) {
                    System.err.println("GhostMod: [PetFeed] Chat trigger error: " + e);
                }
            }
        }).start();
        return true; // true = nuot lenh, khong gui len server
    }

    private static boolean handleDebugCommand(String lowerCommand) {
        if ("debug".equals(lowerCommand) || "debug on".equals(lowerCommand)) {
            setDebugEnabled(true, "chat");
            return true;
        }
        if ("debug off".equals(lowerCommand)) {
            setDebugEnabled(false, "chat");
            return true;
        }
        if ("debug dump".equals(lowerCommand)) {
            dumpDebugSnapshot("chat_dump", false);
            return true;
        }
        if ("debug full".equals(lowerCommand)) {
            dumpDebugSnapshot("chat_full", true);
            return true;
        }
        if ("debug map".equals(lowerCommand)) {
            dumpDebugMapNow("chat_map");
            return true;
        }
        if ("debug menu".equals(lowerCommand) || "debug menu on".equals(lowerCommand)
                || "debug trace".equals(lowerCommand) || "debug trace on".equals(lowerCommand)) {
            setMenuTraceEnabled(true, "chat");
            return true;
        }
        if ("debug menu off".equals(lowerCommand) || "debug trace off".equals(lowerCommand)) {
            setMenuTraceEnabled(false, "chat");
            return true;
        }
        if ("debug menu dump".equals(lowerCommand) || "debug trace dump".equals(lowerCommand)) {
            logMenuTrace("chat_dump", true);
            return true;
        }

        System.err.println("GhostMod: DEBUG command ignored - supported commands: debug on, debug off, debug dump, debug full, debug map, debug menu on, debug menu off, debug menu dump");
        return true;
    }

    private static boolean handleSaveCommand() {
        if (!isActiveMapContext()) {
            System.err.println("GhostMod: SAVE ignored - not in active map context");
            return true;
        }

        String characterName = getCurrentCharacterName();
        if (characterName == null || characterName.length() == 0) {
            System.err.println("GhostMod: SAVE ignored - character name unavailable");
            return true;
        }

        SavedPoint save = new SavedPoint(
                characterName,
                fu.q.d,
                resolveMapName(fu.q.d),
                (byte) 0,
                cn.g.aZ,
                cn.g.ba,
                System.currentTimeMillis()
        );
        savedPointsByCharacter.put(characterName, save);
        recoveryPendingCharacterName = null;
        autoAttackSuppressed = false;
        lastKnownCharacterName = characterName;

        System.err.println("GhostMod: SAVE point char=\"" + sanitizeForLog(characterName)
                + "\" mapId=" + save.mapId
                + " mapName=\"" + sanitizeForLog(save.mapName) + "\""
                + " x=" + save.pixelX
                + " y=" + save.pixelY);
        return true;
    }

    private static boolean handleClearSaveCommand() {
        String characterName = getCurrentCharacterName();
        if (characterName == null || characterName.length() == 0) {
            characterName = lastKnownCharacterName;
        }
        if (characterName == null || characterName.length() == 0) {
            System.err.println("GhostMod: SAVE CLEAR ignored - character name unavailable");
            return true;
        }

        savedPointsByCharacter.remove(characterName);
        if (characterName.equals(recoveryPendingCharacterName)) {
            recoveryPendingCharacterName = null;
        }
        autoAttackSuppressed = false;
        System.err.println("GhostMod: SAVE cleared for char=\"" + sanitizeForLog(characterName) + "\"");
        return true;
    }

    private static void armRecoveryOnLogout(String characterName, String screenName) {
        armTargetRefresh("logout_" + sanitizeForLog(screenName));
        if (characterName == null || characterName.length() == 0) {
            return;
        }

        SavedPoint save = getSavedPoint(characterName);
        if (save == null) {
            recoveryPendingCharacterName = null;
            return;
        }

        recoveryPendingCharacterName = characterName;
        autoAttackSuppressed = false;
        System.err.println("GhostMod: Logout detected (" + screenName
                + ", name=" + sanitizeForLog(characterName)
                + ") - Recovery armed for mapId=" + save.mapId
                + " x=" + save.pixelX
                + " y=" + save.pixelY);
    }

    private static boolean shouldRecoverFor(String characterName) {
        return characterName != null
                && recoveryPendingCharacterName != null
                && characterName.equals(recoveryPendingCharacterName)
                && getSavedPoint(characterName) != null;
    }

    private static void maybeStartRecovery(final String characterName) {
        long now = System.currentTimeMillis();
        synchronized (GhostMod.class) {
            if (recoveryInProgress
                    || !shouldRecoverFor(characterName)
                    || now - lastRecoveryStartAttempt < 3000L) {
                return;
            }
            recoveryInProgress = true;
            lastRecoveryStartAttempt = now;
        }

        new Thread(new Runnable() {
            public void run() {
                runRecoveryWorker(characterName);
            }
        }).start();
    }

    private static void runRecoveryWorker(String characterName) {
        System.err.println("GhostMod: Recovery worker STARTED for char=\"" + sanitizeForLog(characterName) + "\"");
        try {
            SavedPoint save = getSavedPoint(characterName);
            if (save == null) {
                failRecovery(characterName, "missing save point");
                return;
            }

            if (!waitForRecoveryContext(characterName, 30000L)) {
                failRecovery(characterName, "no stable map context");
                return;
            }

            dismissPostLoginOverlayIfPresent("recovery_login");
            System.err.println("GhostMod: Recovery stabilization delay remaining 4s before movement...");
            Thread.sleep(4000L);

            for (int hop = 0; hop < 32; hop++) {
                if (!waitForRecoveryContext(characterName, 30000L)) {
                    failRecovery(characterName, "no stable map context");
                    return;
                }

                save = getSavedPoint(characterName);
                if (save == null) {
                    failRecovery(characterName, "save point removed");
                    return;
                }

                int currentMapId = fu.q.d;
                if (currentMapId == save.mapId) {
                    System.err.println("GhostMod: Recovery reached target mapId=" + save.mapId
                            + " - moving to saved point");
                    if (!startMoveToPixel(save.pixelX, save.pixelY, 160, false)) {
                        failRecovery(characterName, "cannot path to saved point");
                        return;
                    }
                    if (!waitForArrival(save.mapId, save.pixelX, save.pixelY, 18, 30000L)) {
                        failRecovery(characterName, "timed out moving to saved point");
                        return;
                    }
                    finishRecoverySuccess(characterName, save);
                    return;
                }

                RouteEdge nextEdge = findNextRouteEdge(currentMapId, save.mapId);
                if (nextEdge == null) {
                    failRecovery(characterName, "no route from mapId=" + currentMapId + " to mapId=" + save.mapId);
                    return;
                }

                eo portal = getPortalByIndex(nextEdge.portalIndex);
                if (portal == null) {
                    failRecovery(characterName, "portal idx=" + nextEdge.portalIndex + " missing in mapId=" + currentMapId);
                    return;
                }

                System.err.println("GhostMod: Recovery step from mapId=" + currentMapId
                        + " to mapId=" + nextEdge.toMapId
                        + " via portalIdx=" + nextEdge.portalIndex
                        + " label=\"" + sanitizeForLog(portal.t) + "\"");

                if (!startMoveToPortal(portal, 160)) {
                    failRecovery(characterName, "cannot path to portal idx=" + nextEdge.portalIndex);
                    return;
                }

                if (!waitForPortalMapChange(currentMapId, portal, 60000L)) {
                    failRecovery(characterName, "timed out waiting for map change from mapId=" + currentMapId);
                    return;
                }

                if (fu.q != null && fu.q.d != nextEdge.toMapId) {
                    System.err.println("GhostMod: Recovery map changed to unexpected mapId=" + fu.q.d
                            + " (expected " + nextEdge.toMapId + "), recomputing route");
                }

                Thread.sleep(1500L);
            }

            failRecovery(characterName, "too many route hops");
        } catch (Throwable t) {
            failRecovery(characterName, "exception " + t.toString());
        }
    }

    private static boolean waitForRecoveryContext(String characterName, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String currentCharacterName = getCurrentCharacterName();
            if (currentCharacterName != null && characterName != null && !characterName.equals(currentCharacterName)) {
                return false;
            }
            if (isActiveMapContext() && cn.g.cG != 4 && characterName.equals(currentCharacterName)) {
                return true;
            }
            Thread.sleep(250L);
        }
        return isActiveMapContext() && cn.g.cG != 4 && characterName.equals(getCurrentCharacterName());
    }

    private static void finishRecoverySuccess(String characterName, SavedPoint save) {
        synchronized (GhostMod.class) {
            recoveryInProgress = false;
            if (characterName.equals(recoveryPendingCharacterName)) {
                recoveryPendingCharacterName = null;
            }
            autoAttackSuppressed = false;
            kickstartTriggered = false;
        }

        System.err.println("GhostMod: Recovery COMPLETE char=\"" + sanitizeForLog(characterName)
                + "\" mapId=" + save.mapId
                + " x=" + save.pixelX
                + " y=" + save.pixelY);
        clearNonMonsterTarget("recovery_complete");
        autoAttackKickstart(true);
    }

    private static void failRecovery(String characterName, String reason) {
        synchronized (GhostMod.class) {
            recoveryInProgress = false;
            if (characterName != null && characterName.equals(recoveryPendingCharacterName)) {
                recoveryPendingCharacterName = null;
            }
            autoAttackSuppressed = true;
        }
        System.err.println("GhostMod: Recovery FAILED for char=\"" + sanitizeForLog(characterName)
                + "\" reason=" + reason);
    }

    private static RouteEdge findNextRouteEdge(int startMapId, int targetMapId) {
        if (startMapId == targetMapId) {
            return null;
        }
        if (startMapId < 0 || startMapId >= 256 || targetMapId < 0 || targetMapId >= 256) {
            return null;
        }

        boolean[] visited = new boolean[256];
        int[] previousMap = new int[256];
        int[] previousEdge = new int[256];
        int[] queue = new int[256];
        int i;
        for (i = 0; i < 256; i++) {
            previousMap[i] = -1;
            previousEdge[i] = -1;
        }

        int head = 0;
        int tail = 0;
        visited[startMapId] = true;
        queue[tail++] = startMapId;

        while (head < tail) {
            int currentMapId = queue[head++];
            if (currentMapId == targetMapId) {
                break;
            }

            for (i = 0; i < ROUTE_GRAPH.length; i++) {
                RouteEdge edge = ROUTE_GRAPH[i];
                if (edge.fromMapId == currentMapId
                        && edge.toMapId >= 0
                        && edge.toMapId < 256
                        && !visited[edge.toMapId]) {
                    visited[edge.toMapId] = true;
                    previousMap[edge.toMapId] = currentMapId;
                    previousEdge[edge.toMapId] = i;
                    queue[tail++] = edge.toMapId;
                }
            }
        }

        if (!visited[targetMapId]) {
            return null;
        }

        int cursor = targetMapId;
        int edgeIndex = previousEdge[cursor];
        while (previousMap[cursor] != -1 && previousMap[cursor] != startMapId) {
            cursor = previousMap[cursor];
            edgeIndex = previousEdge[cursor];
        }

        if (edgeIndex < 0 || edgeIndex >= ROUTE_GRAPH.length) {
            return null;
        }
        return ROUTE_GRAPH[edgeIndex];
    }

    private static eo getPortalByIndex(int portalIndex) {
        if (cs.a == null || portalIndex < 0 || portalIndex >= cs.a.c()) {
            return null;
        }
        return (eo) cs.a.a(portalIndex);
    }

    private static boolean startMoveToPortal(eo portal, int maxPathLength) {
        if (portal == null) {
            return false;
        }
        return startMoveToPixel(portal.a, portal.b, maxPathLength, true, portal);
    }

    private static boolean startMoveToPixel(int targetX, int targetY, int maxPathLength, boolean markPortal) {
        return startMoveToPixel(targetX, targetY, maxPathLength, markPortal, null);
    }

    private static boolean startMoveToPixel(int targetX, int targetY, int maxPathLength, boolean markPortal, eo portal) {
        if (!isActiveMapContext()) {
            return false;
        }

        if (markPortal) {
            bq.O = targetX;
            bq.P = targetY;
            bq.Q = 9;
        } else {
            bq.O = -1;
            bq.P = -1;
            bq.Q = 0;
        }

        if (isNearPosition(targetX, targetY, cs.l)) {
            if (markPortal) {
                return forcePortalStep(targetX, targetY);
            }
            return true;
        }

        cn.g.bg = cn.g.aZ;
        cn.g.bh = cn.g.ba;
        cn.g.bm = targetX;
        cn.g.bn = targetY;
        cn.g.cm = targetX;
        cn.g.cn = targetY;

        PathPlan pathPlan = markPortal
                ? buildPortalPathPlan(portal, targetX, targetY, maxPathLength)
                : buildDirectPathPlan(targetX, targetY, maxPathLength);

        if (pathPlan == null || pathPlan.path == null) {
            return isNearPosition(targetX, targetY, cs.l);
        }

        assignPlayerPath(pathPlan.path);

        if (pathPlan.retargeted) {
            System.err.println("GhostMod: Portal path retargeted finalX=" + targetX
                    + " finalY=" + targetY
                    + " pathX=" + pathPlan.pathTargetX
                    + " pathY=" + pathPlan.pathTargetY
                    + " pathTileX=" + (pathPlan.pathTargetX / cs.l)
                    + " pathTileY=" + (pathPlan.pathTargetY / cs.l));
        }
        System.err.println("GhostMod: Path assigned to x=" + targetX
                + " y=" + targetY
                + " pathTargetX=" + pathPlan.pathTargetX
                + " pathTargetY=" + pathPlan.pathTargetY
                + " pathLen=" + pathPlan.path.length
                + " markPortal=" + markPortal);
        return true;
    }

    private static void assignPlayerPath(short[] path) {
        if (cn.g.cO != null) {
            cn.g.cJ = 1;
        }
        cn.g.u();
        cn.g.cO = path;
        cn.g.cJ = 0;
        fu.T = false;
        if (bq.h <= 0 && bq.o == 1) {
            bq.o_();
        }
    }

    private static boolean forcePortalStep(int targetX, int targetY) {
        if (!isActiveMapContext() || cs.l <= 0) {
            return false;
        }

        bq.O = targetX;
        bq.P = targetY;
        bq.Q = 9;

        cn.g.bg = cn.g.aZ;
        cn.g.bh = cn.g.ba;
        cn.g.bm = targetX;
        cn.g.bn = targetY;
        cn.g.cm = targetX;
        cn.g.cn = targetY;

        short[] path = new short[] {(short) (((targetX / cs.l) << 8) + (targetY / cs.l))};
        assignPlayerPath(path);
        System.err.println("GhostMod: Portal forced step to x=" + targetX
                + " y=" + targetY
                + " tileX=" + (targetX / cs.l)
                + " tileY=" + (targetY / cs.l));
        return true;
    }

    private static PathPlan buildDirectPathPlan(int targetX, int targetY, int maxPathLength) {
        short[] path = buildPathToPixel(targetX, targetY, maxPathLength);
        if (!isUsablePath(path, maxPathLength)) {
            return null;
        }
        return new PathPlan(targetX, targetY, path, false);
    }

    private static PathPlan buildPortalPathPlan(eo portal, int targetX, int targetY, int maxPathLength) {
        short[] directPath = buildPathToPixel(targetX, targetY, maxPathLength);
        if (isUsablePath(directPath, maxPathLength)) {
            return new PathPlan(targetX, targetY, directPath, false);
        }

        int tileX = toTile(targetX);
        int tileY = toTile(targetY);
        PathPlan preferred = findPreferredPortalPath(portal, tileX, tileY, maxPathLength);
        if (preferred != null) {
            return preferred;
        }
        return findNearestPortalPath(tileX, tileY, maxPathLength);
    }

    private static PathPlan findPreferredPortalPath(eo portal, int tileX, int tileY, int maxPathLength) {
        int dirX = 0;
        int dirY = 0;
        if (portal != null) {
            if (portal.i < 0) {
                dirX = 1;
            } else if (portal.i > 0) {
                dirX = -1;
            }
            if (portal.j < 0) {
                dirY = 1;
            } else if (portal.j > 0) {
                dirY = -1;
            }
            if (dirX == 0 && dirY == 0) {
                if (portal.n == 0) {
                    dirX = 1;
                } else if (portal.n == 1) {
                    dirX = -1;
                }
            }
        }

        int[][] candidates;
        if (dirX != 0) {
            candidates = new int[][] {
                    {tileX + (dirX * 2), tileY + 1},
                    {tileX + (dirX * 2), tileY},
                    {tileX + dirX, tileY + 1},
                    {tileX + dirX, tileY},
                    {tileX + (dirX * 3), tileY + 1},
                    {tileX + (dirX * 2), tileY - 1},
                    {tileX, tileY + 1},
                    {tileX, tileY - 1}
            };
        } else if (dirY != 0) {
            candidates = new int[][] {
                    {tileX, tileY + (dirY * 2)},
                    {tileX + 1, tileY + (dirY * 2)},
                    {tileX - 1, tileY + (dirY * 2)},
                    {tileX, tileY + dirY},
                    {tileX + 1, tileY + dirY},
                    {tileX - 1, tileY + dirY},
                    {tileX + 2, tileY + dirY},
                    {tileX - 2, tileY + dirY}
            };
        } else {
            return null;
        }

        for (int i = 0; i < candidates.length; i++) {
            PathPlan plan = buildPortalCandidatePath(candidates[i][0], candidates[i][1], maxPathLength);
            if (plan != null) {
                return plan;
            }
        }
        return null;
    }

    private static PathPlan findNearestPortalPath(int tileX, int tileY, int maxPathLength) {
        PathPlan bestPlan = null;
        int bestScore = Integer.MAX_VALUE;
        for (int radius = 1; radius <= PORTAL_APPROACH_RADIUS; radius++) {
            for (int y = tileY - radius; y <= tileY + radius; y++) {
                for (int x = tileX - radius; x <= tileX + radius; x++) {
                    int distance = ak.e(x - tileX) + ak.e(y - tileY);
                    if (distance == 0 || distance > radius) {
                        continue;
                    }
                    PathPlan plan = buildPortalCandidatePath(x, y, maxPathLength);
                    if (plan == null) {
                        continue;
                    }
                    int score = (distance * 1000) + plan.path.length;
                    if (score < bestScore) {
                        bestScore = score;
                        bestPlan = plan;
                    }
                }
            }
            if (bestPlan != null) {
                return bestPlan;
            }
        }
        return null;
    }

    private static PathPlan buildPortalCandidatePath(int tileX, int tileY, int maxPathLength) {
        if (!isWalkableTile(tileX, tileY)) {
            return null;
        }
        int pathTargetX = tileX * cs.l + (cs.l / 2);
        int pathTargetY = tileY * cs.l + (cs.l / 2);
        short[] path = buildPathToTile(tileX, tileY, maxPathLength);
        if (!isUsablePath(path, maxPathLength)) {
            return null;
        }
        return new PathPlan(pathTargetX, pathTargetY, path, true);
    }

    private static short[] buildPathToPixel(int targetX, int targetY, int maxPathLength) {
        if (cs.l <= 0) {
            return null;
        }
        return buildPathToTile(targetX / cs.l, targetY / cs.l, maxPathLength);
    }

    private static short[] buildPathToTile(int targetTileX, int targetTileY, int maxPathLength) {
        short[] enginePath = null;
        try {
            enginePath = fu.c.a(targetTileX, targetTileY, cn.g.aZ / cs.l, cn.g.ba / cs.l, maxPathLength);
        } catch (Throwable t) {
        }
        if (isEnginePathUsable(enginePath, maxPathLength)) {
            return enginePath;
        }
        return buildGridPathToTile(targetTileX, targetTileY, maxPathLength);
    }

    private static short[] buildGridPathToTile(int targetTileX, int targetTileY, int maxPathLength) {
        if (!isActiveMapContext()
                || fu.q == null
                || fu.q.n == null
                || cs.l <= 0
                || maxPathLength <= 0) {
            return null;
        }

        int width = fu.q.e;
        int height = fu.q.f;
        int startTileX = cn.g.aZ / cs.l;
        int startTileY = cn.g.ba / cs.l;
        if (!isWalkableTile(startTileX, startTileY) || !isWalkableTile(targetTileX, targetTileY)) {
            return null;
        }

        int total = width * height;
        if (total <= 0) {
            return null;
        }

        int startIndex = (startTileY * width) + startTileX;
        int targetIndex = (targetTileY * width) + targetTileX;
        if (startIndex == targetIndex) {
            return new short[] {(short) ((startTileX << 8) + startTileY)};
        }

        int[] queue = new int[total];
        int[] previous = new int[total];
        int[] distance = new int[total];
        boolean[] visited = new boolean[total];
        for (int i = 0; i < total; i++) {
            previous[i] = -1;
            distance[i] = -1;
        }

        int head = 0;
        int tail = 0;
        queue[tail++] = startIndex;
        visited[startIndex] = true;
        distance[startIndex] = 0;

        while (head < tail) {
            int current = queue[head++];
            if (current == targetIndex) {
                break;
            }

            int currentDistance = distance[current];
            if (currentDistance + 1 >= maxPathLength) {
                continue;
            }

            int x = current % width;
            int y = current / width;
            tail = enqueuePathNeighbor(x + 1, y, width, height, current, currentDistance, queue, tail, visited, previous, distance);
            tail = enqueuePathNeighbor(x - 1, y, width, height, current, currentDistance, queue, tail, visited, previous, distance);
            tail = enqueuePathNeighbor(x, y + 1, width, height, current, currentDistance, queue, tail, visited, previous, distance);
            tail = enqueuePathNeighbor(x, y - 1, width, height, current, currentDistance, queue, tail, visited, previous, distance);
        }

        if (!visited[targetIndex]) {
            return null;
        }

        int pathLength = distance[targetIndex] + 1;
        if (pathLength <= 0 || pathLength > maxPathLength) {
            return null;
        }

        short[] path = new short[pathLength];
        int cursor = targetIndex;
        for (int i = pathLength - 1; i >= 0; i--) {
            int x = cursor % width;
            int y = cursor / width;
            path[i] = (short) ((x << 8) + y);
            cursor = previous[cursor];
        }

        System.err.println("GhostMod: Grid path fallback targetTileX=" + targetTileX
                + " targetTileY=" + targetTileY
                + " pathLen=" + path.length
                + " maxPathLength=" + maxPathLength);
        return path;
    }

    private static int enqueuePathNeighbor(int tileX, int tileY, int width, int height, int previousIndex, int previousDistance, int[] queue, int tail, boolean[] visited, int[] previous, int[] distance) {
        if (tileX < 0 || tileY < 0 || tileX >= width || tileY >= height) {
            return tail;
        }
        if (!isWalkableTile(tileX, tileY)) {
            return tail;
        }

        int index = (tileY * width) + tileX;
        if (visited[index]) {
            return tail;
        }

        visited[index] = true;
        previous[index] = previousIndex;
        distance[index] = previousDistance + 1;
        queue[tail++] = index;
        return tail;
    }

    private static boolean isEnginePathUsable(short[] path, int maxPathLength) {
        return path != null
                && path.length > 0
                && path.length <= maxPathLength
                && path.length <= ENGINE_PATHFINDER_HARD_LIMIT;
    }

    private static boolean isUsablePath(short[] path, int maxPathLength) {
        return path != null
                && path.length > 0
                && path.length <= maxPathLength;
    }

    private static boolean isWalkableTile(int tileX, int tileY) {
        if (fu.q == null || fu.q.n == null || tileX < 0 || tileY < 0 || tileX >= fu.q.e || tileY >= fu.q.f) {
            return false;
        }
        int idx = tileY * fu.q.e + tileX;
        if (idx < 0 || idx >= fu.q.n.length) {
            return false;
        }
        int cell = fu.q.n[idx];
        return cell != 1 && cell != -1;
    }

    private static boolean waitForArrival(int expectedMapId, int targetX, int targetY, int tolerancePx, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!isActiveMapContext()) {
                Thread.sleep(250L);
                continue;
            }
            if (fu.q.d != expectedMapId) {
                return false;
            }
            if (isNearPosition(targetX, targetY, tolerancePx) && cn.g.cO == null) {
                return true;
            }
            Thread.sleep(250L);
        }
        return isActiveMapContext() && fu.q.d == expectedMapId && isNearPosition(targetX, targetY, tolerancePx);
    }

    private static boolean waitForMapChange(int fromMapId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (fu.q != null && fu.q.d != fromMapId) {
                return true;
            }
            Thread.sleep(250L);
        }
        return fu.q != null && fu.q.d != fromMapId;
    }

    private static boolean waitForPortalMapChange(int fromMapId, eo portal, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long nextPushAt = System.currentTimeMillis() + PORTAL_FINAL_PUSH_INTERVAL_MS;
        int pushCount = 0;

        while (System.currentTimeMillis() < deadline) {
            if (fu.q != null && fu.q.d != fromMapId) {
                return true;
            }

            long now = System.currentTimeMillis();
            if (portal != null
                    && now >= nextPushAt
                    && isActiveMapContext()
                    && fu.q.d == fromMapId
                    && cn.g.cO == null
                    && cn.g.cG != 4) {
                pushCount++;
                System.err.println("GhostMod: Portal final push #" + pushCount
                        + " fromX=" + cn.g.aZ
                        + " fromY=" + cn.g.ba
                        + " fromTileX=" + (cn.g.aZ / cs.l)
                        + " fromTileY=" + (cn.g.ba / cs.l)
                        + " portalX=" + portal.a
                        + " portalY=" + portal.b
                        + " portalTileX=" + (portal.a / cs.l)
                        + " portalTileY=" + (portal.b / cs.l));

                if (!startMoveToPixel(portal.a, portal.b, PORTAL_FINAL_PUSH_PATH_LENGTH, true, null)) {
                    forcePortalStep(portal.a, portal.b);
                }
                nextPushAt = now + PORTAL_FINAL_PUSH_INTERVAL_MS;
            }

            Thread.sleep(250L);
        }
        return fu.q != null && fu.q.d != fromMapId;
    }

    private static byte normalizeZone(byte zone) {
        return zone > 0 ? zone : (byte) 1;
    }

    private static byte[] buildZoneFallbackOrder(byte savedZone) {
        savedZone = normalizeZone(savedZone);

        boolean[] seen = new boolean[128];
        byte[] scratch = new byte[128];
        int count = 0;

        if (savedZone > 0 && savedZone < seen.length) {
            scratch[count++] = savedZone;
            seen[savedZone] = true;
        }

        for (int zone = savedZone - 1; zone >= 1; zone--) {
            if (zone == 2) {
                continue;
            }
            if (!seen[zone]) {
                scratch[count++] = (byte) zone;
                seen[zone] = true;
            }
        }

        if (!seen[1]) {
            scratch[count++] = 1;
        }

        byte[] result = new byte[count];
        for (int i = 0; i < count; i++) {
            result[i] = scratch[i];
        }
        return result;
    }

    private static boolean recoverZoneForSavedPoint(String characterName, SavedPoint save, int expectedMapId) throws Exception {
        byte currentZone = normalizeZone(cs.b());
        byte desiredZone = normalizeZone(save.zone);
        if (currentZone == desiredZone) {
            System.err.println("GhostMod: Recovery zone already satisfied zone=" + currentZone);
            return true;
        }

        byte[] candidates = buildZoneFallbackOrder(desiredZone);
        for (int i = 0; i < candidates.length; i++) {
            byte candidateZone = candidates[i];
            if (trySwitchToZone(characterName, save, expectedMapId, candidateZone, desiredZone)) {
                return true;
            }
        }
        return false;
    }

    private static boolean trySwitchToZone(String characterName, SavedPoint save, int expectedMapId, byte candidateZone, byte desiredZone) throws Exception {
        candidateZone = normalizeZone(candidateZone);
        if (!isActiveMapContext() || fu.q.d != expectedMapId) {
            return false;
        }

        if (candidateZone == desiredZone && normalizeZone(cs.b()) == candidateZone) {
            System.err.println("GhostMod: Recovery zone already on desired zone=" + candidateZone);
            return true;
        }

        fa zoneBoard = findZoneBoardWithMovement(expectedMapId, save);
        if (zoneBoard == null) {
            System.err.println("GhostMod: Recovery zone board not found in mapId=" + expectedMapId
                    + " entities=" + buildEntitySummary());
            return false;
        }

        System.err.println("GhostMod: Recovery zone try desired=" + desiredZone
                + " candidate=" + candidateZone
                + " boardId=" + zoneBoard.cu
                + " boardName=\"" + sanitizeForLog(zoneBoard.cC) + "\"");

        if (!startMoveToPixel(zoneBoard.aZ, zoneBoard.ba, 160, false)) {
            System.err.println("GhostMod: Recovery zone move failed for candidate=" + candidateZone);
            return false;
        }

        if (!waitForArrival(expectedMapId, zoneBoard.aZ, zoneBoard.ba, cs.l * 2, 20000L)) {
            System.err.println("GhostMod: Recovery zone board arrival timeout for candidate=" + candidateZone);
            return false;
        }

        if (!openZoneMenu(zoneBoard, expectedMapId, 8000L)) {
            System.err.println("GhostMod: Recovery zone menu open timeout for candidate=" + candidateZone);
            return false;
        }

        int optionIndex = candidateZone - 1;
        if (optionIndex < 0 || optionIndex >= cs.v) {
            System.err.println("GhostMod: Recovery zone candidate unavailable desired=" + desiredZone
                    + " candidate=" + candidateZone
                    + " optionCount=" + cs.v);
            return false;
        }

        q.a().d((byte) optionIndex);
        if (!waitForZoneChange(characterName, expectedMapId, candidateZone, 15000L)) {
            System.err.println("GhostMod: Recovery zone switch timeout desired=" + desiredZone
                    + " candidate=" + candidateZone);
            return false;
        }

        cn.i = null;
        cs.v = 0;
        cs.o = null;
        fu.j();
        System.err.println("GhostMod: Recovery zone switch success actualZone=" + cs.b()
                + " desired=" + desiredZone
                + " selected=" + candidateZone);
        Thread.sleep(1500L);
        return true;
    }

    private static fa findNearestZoneBoard(long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        do {
            if (cn.g != null && cn.j != null) {
                fa nearest = null;
                long bestDistance = Long.MAX_VALUE;
                int size = cn.j.c();

                for (int i = 0; i < size; i++) {
                    Object object = cn.j.a(i);
                    if (!(object instanceof fa)) {
                        continue;
                    }

                    fa target = (fa) object;
                    if (target == null || !target.l_() || target.cG == 4 || target.cL || target.bJ) {
                        continue;
                    }

                    long dx = target.aZ - cn.g.aZ;
                    long dy = target.ba - cn.g.ba;
                    long distance = (dx * dx) + (dy * dy);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        nearest = target;
                    }
                }

                if (nearest != null) {
                    return nearest;
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            Thread.sleep(250L);
        } while (true);

        return null;
    }

    private static fa findZoneBoardWithMovement(int expectedMapId, SavedPoint save) throws Exception {
        fa zoneBoard = findNearestZoneBoard(1200L);
        if (zoneBoard != null) {
            return zoneBoard;
        }

        int[][] probes = buildZoneProbePoints(save);
        for (int i = 0; i < probes.length; i++) {
            int probeX = probes[i][0];
            int probeY = probes[i][1];
            if (probeX < 0 || probeY < 0) {
                continue;
            }

            if (!moveToZoneProbe(expectedMapId, probeX, probeY, probes[i][2])) {
                continue;
            }

            zoneBoard = findNearestZoneBoard(1800L);
            if (zoneBoard != null) {
                return zoneBoard;
            }
        }

        return null;
    }

    private static int[][] buildZoneProbePoints(SavedPoint save) {
        int centerX = (fu.q != null ? fu.q.e * cs.l / 2 : -1);
        int centerY = (fu.q != null ? fu.q.f * cs.l / 2 : -1);
        int[][] probes = new int[6][3];

        probes[0][0] = save.pixelX;
        probes[0][1] = save.pixelY;
        probes[0][2] = 1;

        probes[1][0] = centerX;
        probes[1][1] = centerY;
        probes[1][2] = 2;

        probes[2][0] = centerX;
        probes[2][1] = centerY > 0 ? centerY + (4 * cs.l) : -1;
        probes[2][2] = 3;

        probes[3][0] = centerX;
        probes[3][1] = centerY > 0 ? centerY - (4 * cs.l) : -1;
        probes[3][2] = 4;

        probes[4][0] = centerX > 0 ? centerX - (6 * cs.l) : -1;
        probes[4][1] = centerY;
        probes[4][2] = 5;

        probes[5][0] = centerX > 0 ? centerX + (6 * cs.l) : -1;
        probes[5][1] = centerY;
        probes[5][2] = 6;

        return probes;
    }

    private static boolean moveToZoneProbe(int expectedMapId, int probeX, int probeY, int probeId) throws Exception {
        if (!isActiveMapContext() || fu.q == null || fu.q.d != expectedMapId) {
            return false;
        }
        if (probeX < 0 || probeY < 0) {
            return false;
        }

        System.err.println("GhostMod: Recovery zone probe move id=" + probeId
                + " x=" + probeX
                + " y=" + probeY);
        if (!startMoveToPixel(probeX, probeY, 220, false)) {
            return false;
        }
        if (!waitForArrival(expectedMapId, probeX, probeY, cs.l * 2, 25000L)) {
            return false;
        }
        Thread.sleep(600L);
        return true;
    }

    private static boolean openZoneMenu(fa zoneBoard, int expectedMapId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long nextInteractAt = 0L;
        cs.v = 0;
        cs.o = null;

        while (System.currentTimeMillis() < deadline) {
            if (!isActiveMapContext() || fu.q.d != expectedMapId) {
                return false;
            }
            if (cs.v > 0) {
                return true;
            }

            long now = System.currentTimeMillis();
            if (now >= nextInteractAt) {
                cn.i = zoneBoard;
                q.a().d((byte) 0, (short) (zoneBoard.cu + 1000));
                nextInteractAt = now + 1200L;
            }
            Thread.sleep(250L);
        }

        return cs.v > 0;
    }

    private static boolean waitForZoneChange(String characterName, int expectedMapId, byte expectedZone, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        expectedZone = normalizeZone(expectedZone);

        while (System.currentTimeMillis() < deadline) {
            if (!isActiveMapContext()) {
                Thread.sleep(250L);
                continue;
            }
            if (fu.q.d != expectedMapId) {
                return false;
            }

            String currentCharacterName = getCurrentCharacterName();
            if (characterName != null
                    && currentCharacterName != null
                    && !characterName.equals(currentCharacterName)) {
                return false;
            }

            if (normalizeZone(cs.b()) == expectedZone) {
                return true;
            }
            Thread.sleep(250L);
        }

        return isActiveMapContext()
                && fu.q.d == expectedMapId
                && normalizeZone(cs.b()) == expectedZone;
    }

    private static boolean shouldSkipLoginKickstart(String characterName) {
        return !shouldRecoverFor(characterName) && fu.q != null && fu.q.d == 33;
    }

    private static boolean hasPostLoginOverlay() {
        return fu.s != null || fu.t != null || (fu.p != null && fu.p.a);
    }

    private static void maybeStartPostLoginOverlayWatch(final String initialCharacterName) {
        synchronized (GhostMod.class) {
            if (postLoginOverlayWatchActive) {
                return;
            }
            postLoginOverlayWatchActive = true;
        }

        new Thread(new Runnable() {
            public void run() {
                runPostLoginOverlayWatch(initialCharacterName);
            }
        }).start();
    }

    private static void runPostLoginOverlayWatch(String initialCharacterName) {
        long deadline = System.currentTimeMillis() + POST_LOGIN_OVERLAY_WATCH_MS;
        System.err.println("GhostMod: Post-login overlay watch STARTED char=\""
                + sanitizeForLog(initialCharacterName)
                + "\" durationMs=" + POST_LOGIN_OVERLAY_WATCH_MS);

        try {
            while (System.currentTimeMillis() < deadline) {
                if (fu.a == null || cn.g == null) {
                    System.err.println("GhostMod: Post-login overlay watch stopped - left map context");
                    return;
                }
                if (hasPostLoginOverlay()) {
                    handlePostLoginOverlayDetected(initialCharacterName);
                    return;
                }
                Thread.sleep(POST_LOGIN_OVERLAY_POLL_MS);
            }

            System.err.println("GhostMod: Post-login overlay watch FINISHED - no overlay detected");
        } catch (Throwable t) {
            System.err.println("GhostMod: Post-login overlay watch error: " + t.toString());
        } finally {
            synchronized (GhostMod.class) {
                postLoginOverlayWatchActive = false;
            }
        }
    }

    private static void handlePostLoginOverlayDetected(String initialCharacterName) throws Exception {
        String characterName = getCurrentCharacterName();
        if (characterName == null || characterName.length() == 0) {
            characterName = initialCharacterName;
        }
        if (characterName == null || characterName.length() == 0) {
            characterName = lastKnownCharacterName;
        }

        SavedPoint save = getSavedPoint(characterName);
        int currentMapId = (fu.q != null) ? fu.q.d : -1;
        int savedMapId = (save != null) ? save.mapId : -1;

        System.err.println("GhostMod: Post-login overlay DETECTED char=\""
                + sanitizeForLog(characterName)
                + "\" currentMapId=" + currentMapId
                + " savedMapId=" + savedMapId
                + " recoveryInProgress=" + recoveryInProgress);

        dismissPostLoginOverlayIfPresent("post_login_watch");

        if (!isActiveMapContext() || characterName == null || characterName.length() == 0) {
            return;
        }

        if (save != null && currentMapId != save.mapId) {
            System.err.println("GhostMod: Post-login overlay handled - current map differs from save, starting recovery");
            synchronized (GhostMod.class) {
                if (!recoveryInProgress) {
                    recoveryPendingCharacterName = characterName;
                    autoAttackSuppressed = false;
                    kickstartTriggered = false;
                    lastRecoveryStartAttempt = 0L;
                }
            }
            maybeStartRecovery(characterName);
            return;
        }

        if (save != null) {
            synchronized (GhostMod.class) {
                if (characterName.equals(recoveryPendingCharacterName) && !recoveryInProgress) {
                    recoveryPendingCharacterName = null;
                }
            }
            System.err.println("GhostMod: Post-login overlay handled - same map as save, forcing kickstart");
        } else {
            System.err.println("GhostMod: Post-login overlay handled - no save point, forcing kickstart");
        }
        forceAutoAttackKickstart("post_login_watch");
    }

    private static void forceAutoAttackKickstart(String reason) {
        synchronized (GhostMod.class) {
            if (recoveryInProgress) {
                return;
            }
            autoAttackSuppressed = false;
            kickstartTriggered = false;
            lastKickstartAttempt = 0L;
        }
        autoAttackKickstart(true, reason, true);
    }

    public static void startStateMonitor() {
        new Thread(new Runnable() {
            public void run() {
                System.err.println("GhostMod: State Monitor STARTED");
                while (true) {
                    try {
                        Thread.sleep(1000);

                        String screenName = (fu.a != null) ? fu.a.getClass().getName() : "";
                        boolean isLoginScreen = screenName.equals("ea") || screenName.endsWith(".ea");
                        boolean isOutOfGameScreen = screenName.equals("x") || screenName.endsWith(".x") || screenName.equals("bs") || screenName.endsWith(".bs");
                        boolean currentlyInMap = (fu.a != null && fu.c != null && fu.a == fu.c);
                        String characterName = getCurrentCharacterName();
                        boolean playerAlive = (cn.g != null && cn.g.cG != 4);
                        boolean logoutLike = isLoginScreen || isOutOfGameScreen || fu.a == null || cn.g == null;

                        if (currentlyInMap && playerAlive) {
                            boolean firstLiveMapContext = !stateHadLiveMapContext;
                            if (firstLiveMapContext) {
                                synchronized (GhostMod.class) {
                                    postLoginSetupPending = true;
                                }
                                maybeStartPostLoginOverlayWatch(characterName);
                            }
                            if (characterName != null && characterName.length() > 0) {
                                lastKnownCharacterName = characterName;
                                lastLiveCharacterName = characterName;
                            }
                            stateHadLiveMapContext = true;
                            if (shouldRecoverFor(characterName)) {
                                maybeStartRecovery(characterName);
                            } else if (!hasPostLoginOverlay()
                                    && !shouldSkipLoginKickstart(characterName)
                                    && !recoveryInProgress
                                    && !autoAttackSuppressed
                                    && !kickstartTriggered) {
                                autoAttackKickstart(true);
                            }
                        } else if (logoutLike) {
                            if (stateHadLiveMapContext) {
                                armRecoveryOnLogout(lastLiveCharacterName, screenName);
                            }
                            stateHadLiveMapContext = false;
                            autoAttackSuppressed = false;
                            postLoginSetupPending = false;
                            if (kickstartTriggered) {
                                System.err.println("GhostMod: Logout detected (" + screenName + ", name=" + characterName + ") - Resetting Kickstart");
                                kickstartTriggered = false;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("GhostMod: State Monitor Error: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    public static void startLifeMonitor() {
        new Thread(new Runnable() {
            public void run() {
                System.err.println("GhostMod: Life Monitor Thread STARTED (Revive Here Mode)");
                while (true) {
                    try {
                        Thread.sleep(3000);
                        if (cn.g != null) {
                            // cG == 4 means Dead
                            if (cn.g.cG == 4) {
                                System.err.println("GhostMod: Character DIED. Reviving in place in 5s...");
                                Thread.sleep(5000);
                                
                                // Step 1: Send Revival Packet  
                                q.a().b((byte) 1);
                                System.err.println("GhostMod: 'Revive Here' packet sent.");
                                
                                // Step 2: Wait for the confirm dialog and click Ok
                                // Dialog can be in fu.s OR fu.t — check BOTH
                                // Use PROVEN key simulation (both am + al arrays)
                                System.err.println("GhostMod: Waiting for confirm dialog (fu.s or fu.t)...");
                                for (int wait = 0; wait < 20; wait++) {
                                    Thread.sleep(250);
                                    if (fu.s != null || fu.t != null) {
                                        System.err.println("GhostMod: Dialog detected (s=" + (fu.s != null) + " t=" + (fu.t != null) + "). Clicking Ok...");
                                        Thread.sleep(500);
                                        // Set BOTH arrays — this was proven to work before
                                        fu.am[12] = true;
                                        fu.al[12] = true;
                                        Thread.sleep(200);
                                        fu.am[12] = false;
                                        fu.al[12] = false;
                                        Thread.sleep(500);
                                        // If dialog still there, try again
                                        if (fu.s != null || fu.t != null) {
                                            System.err.println("GhostMod: Dialog still showing, pressing Ok again...");
                                            fu.am[5] = true;
                                            fu.al[5] = true;
                                            Thread.sleep(200);
                                            fu.am[5] = false;
                                            fu.al[5] = false;
                                        }
                                        break;
                                    }
                                }
                                
                                // Step 3: Wait for the MENU (Item vs Gem selection)
                                System.err.println("GhostMod: Waiting for revival method menu (fu.p.a)...");
                                boolean menuFound = false;
                                for (int wait = 0; wait < 30; wait++) {
                                    Thread.sleep(250);
                                    if (fu.p != null && fu.p.a) {
                                        menuFound = true;
                                        break;
                                    }
                                    // Check if another dialog appeared and dismiss it
                                    if (fu.s != null || fu.t != null) {
                                        System.err.println("GhostMod: Extra dialog appeared, pressing Ok...");
                                        fu.am[12] = true;
                                        fu.al[12] = true;
                                        Thread.sleep(200);
                                        fu.am[12] = false;
                                        fu.al[12] = false;
                                    }
                                }
                                
                                // Step 4: Send packet directly for "Use Item" (index 0)
                                if (menuFound) {
                                    System.err.println("GhostMod: MENU detected! Sending packet for item 0...");
                                    try {
                                        java.lang.reflect.Field fieldB = fu.p.getClass().getDeclaredField("B");
                                        fieldB.setAccessible(true);
                                        int idMenu = fieldB.getInt(fu.p);
                                        
                                        java.lang.reflect.Field fieldC = fu.p.getClass().getDeclaredField("C");
                                        fieldC.setAccessible(true);
                                        int idNPC = fieldC.getInt(fu.p);
                                        
                                        System.err.println("GhostMod: idNPC=" + idNPC + ", idMenu=" + idMenu + " -> selecting index=0");
                                        q.a().b((short) idNPC, (byte) idMenu, (byte) 0);
                                        fu.p.a = false;
                                        System.err.println("GhostMod: USE ITEM packet sent! Reviving...");
                                    } catch (Exception e) {
                                        System.err.println("GhostMod: Reflection error: " + e);
                                    }
                                } else {
                                    System.err.println("GhostMod: No menu appeared after 7.5s.");
                                }
                                
                                Thread.sleep(3000);
                                fu.j(); // Clear residual UI
                                
                                synchronized(GhostMod.class) {
                                    kickstartTriggered = false;
                                }
                                
                                // Step 5: Resume combat after revive
                                System.err.println("GhostMod: Revived! Waiting 3s then restarting combat...");
                                Thread.sleep(3000);

                                if (recoveryInProgress || shouldRecoverFor(getCurrentCharacterName())) {
                                    System.err.println("GhostMod: Recovery is active, skip combat restart after revive.");
                                } else if (cn.g != null && cn.g.cG != 4 && fu.am != null) {
                                    ensurePersistentAutoAttackEnabled("revive_restart");
                                    clearNonMonsterTarget("revive_restart");
                                    if (acquireMonsterTarget()) {
                                        if (isPersistentAutoAttackActive()) {
                                            System.err.println("GhostMod: Auto Attack already active after revive, skip manual ENTER.");
                                        } else {
                                            Thread.sleep(200);
                                            for (int i = 0; i < KICKSTART_ENTER_PRESSES; i++) {
                                                if (!hasValidCombatTarget()) break;
                                                System.err.println("GhostMod: Attacking locked monster target (attempt " + (i + 1) + "/" + KICKSTART_ENTER_PRESSES + ")...");
                                                pressKey(5, 220);
                                                if (i + 1 < KICKSTART_ENTER_PRESSES) Thread.sleep(780);
                                            }
                                        }
                                        System.err.println("GhostMod: Combat resumed after revive!");
                                    } else {
                                        System.err.println("GhostMod: No valid monster target after revive, skip skill press.");
                                    }
                                } else {
                                    System.err.println("GhostMod: Still dead or not ready, will retry next cycle.");
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore thread errors, keep loop running
                    }
                }
            }
        }).start();
    }

    public static void initMod() {
        System.err.println("GhostMod: MOD LOADED build=" + BUILD_TAG + " (State-Based Detection STARTED)");
        startDebugMapMonitor();
        startDebugSnapshotMonitor();
        startMenuTraceMonitor();
        startAutoFeatureTraceMonitor();
        startStateMonitor();
        startLifeMonitor();
    }

    // =========================================================
    // AUTO PET FEED SCHEDULER
    // Mỗi ngày lúc 7h sáng: lấy 15 món đồ phẩm chất tím (N=3)
    // trong hành trang (bw.V) cho pet đầu tiên ăn, delay 2.5s/lần
    // sau đó tự tắt thông báo (fu.s = null, fu.t = null)
    // =========================================================
    private static void startAutoPetFeedScheduler() {
        if (autoPetFeedStarted) return;
        autoPetFeedStarted = true;
        new Thread(new Runnable() {
            public void run() {
                System.err.println("GhostMod: [PetFeed] Scheduler started, will run at " + AUTO_FEED_HOUR + ":00 daily");
                while (true) {
                    try {
                        Thread.sleep(2000L); // check mỗi 2 giây

                        // ===== THEO DÕI KHU VƯỜN (bw.X) ĐỂ LOG PET =====
                        if (bw.X != null && bw.X.c() > 0) {
                            if (bw.X != lastBwX || bw.X.c() != lastBwXSize) {
                                GhostMod.lastBwX = bw.X;
                                GhostMod.lastBwXSize = bw.X.c();
                                System.err.println("GhostMod: [Garden] Phat hien danh sach pet trong vuon (" + bw.X.c() + " pets):");
                                for (int i = 0; i < bw.X.c(); i++) {
                                    bw item = (bw) bw.X.a(i);
                                    if (item != null && item.u == 9 && item instanceof eb) {
                                        eb pet = (eb) item;
                                        System.err.println("  -> Pet [" + i + "]: name=" + pet.g + ", ID that (O)=" + pet.O + ", ao=" + pet.ao + ", an=" + pet.an + ", am=" + pet.am);
                                    }
                                }
                            }
                        }

                        // Lấy giờ hiện tại theo giờ Việt Nam (GMT+7)
                        long now = System.currentTimeMillis();
                        java.util.TimeZone tz = java.util.TimeZone.getTimeZone("GMT+07:00");
                        java.util.Calendar cal = java.util.Calendar.getInstance(tz);
                        int hour    = cal.get(java.util.Calendar.HOUR_OF_DAY);
                        int minute  = cal.get(java.util.Calendar.MINUTE);
                        // Tính "ngày" dạng số để tránh chạy 2 lần trong cùng 1 ngày
                        long todayKey = cal.get(java.util.Calendar.YEAR) * 10000L
                                      + cal.get(java.util.Calendar.DAY_OF_YEAR);

                        // Trigger: đúng giờ 7h (minute 0-2 để không bỏ lỡ nếu lag)
                        if (hour == AUTO_FEED_HOUR && minute <= 2 && todayKey != autoPetFeedLastRunDay) {
                            autoPetFeedLastRunDay = todayKey;
                            System.err.println("GhostMod: [PetFeed] Trigger at " + hour + ":" + minute + " → starting feed routine");
                            runAutoPetFeed((short)-1);
                        }
                    } catch (Throwable t) {
                        System.err.println("GhostMod: [PetFeed] Scheduler error: " + t);
                    }
                }
            }
        }).start();
    }

    private static void runAutoPetFeed(final short forcePetId) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    System.err.println("GhostMod: [PetFeed] Feed routine BEGIN");

                    // --- Bước 1: Chờ game đang trong map (có player + inventory) ---
                    int waitTimeout = 0;
                    while ((cn.g == null || bw.V == null || bw.X == null) && waitTimeout < 60000) {
                        Thread.sleep(1000L);
                        waitTimeout += 1000;
                    }
                    if (cn.g == null || bw.V == null || bw.X == null) {
                        System.err.println("GhostMod: [PetFeed] Timeout waiting for game state, abort.");
                        return;
                    }

                    // Lấy pet đang trang bị từ cn.j để lấy đặc điểm (N, j) khớp với (an, am) của item
                    short equippedAn = -1;
                    short equippedAm = -1;
                    if (cn.j != null) {
                        for (int pi = 0; pi < cn.j.c(); pi++) {
                            Object obj = cn.j.a(pi);
                            if (obj instanceof s) {
                                s petEntity = (s) obj;
                                if (petEntity.w == cn.g) {
                                    equippedAn = (short) petEntity.N;
                                    equippedAm = (short) petEntity.j;
                                    break;
                                }
                            }
                        }
                    }

                    eb targetPet = null;
                    short petId = -1;
                    String petName = "Unknown";

                    // Phải có data bw.X thì mới có ID thực của pet (O) để cho ăn
                    if (bw.X != null && bw.X.c() > 0) {
                        // Ưu tiên 1: Tìm pet trong kho có an, am khớp với pet đang trang bị
                        for (int pi = 0; pi < bw.X.c(); pi++) {
                            bw item = (bw) bw.X.a(pi);
                            if (item != null && item.u == 9 && item instanceof eb) {
                                eb petItem = (eb) item;
                                if (equippedAn != -1 && petItem.an == equippedAn && petItem.am == equippedAm) {
                                    targetPet = petItem;
                                    petId = (short) targetPet.O;
                                    petName = targetPet.g + " (Equipped)";
                                    break;
                                }
                            }
                        }
                        
                        // Ưu tiên 2: Không khớp hoặc không mang pet, lấy con đầu tiên trong kho
                        if (targetPet == null) {
                            for (int pi = 0; pi < bw.X.c(); pi++) {
                                bw item = (bw) bw.X.a(pi);
                                if (item != null && item.u == 9 && item instanceof eb) {
                                    targetPet = (eb) item;
                                    petId = (short) targetPet.O;
                                    petName = targetPet.g + " (Garden Pet)";
                                    break;
                                }
                            }
                        }

                        // Lưu vào cache để dùng cho các lần sau (khi ra ngoài map khác)
                        if (petId != -1) {
                            GhostMod.cachedPetId = petId;
                            GhostMod.cachedPetName = petName;
                            System.err.println("GhostMod: [PetFeed] Da cache ID pet: " + petId + " - " + petName);
                        }
                    }

                    if (forcePetId != -1) {
                        petId = forcePetId;
                        petName = "Forced Pet (ID " + forcePetId + ")";
                    } else if (GhostMod.cachedPetId != -1) {
                        // Nếu không có bw.X nhưng đã có cache, dùng luôn cache!
                        petId = GhostMod.cachedPetId;
                        petName = GhostMod.cachedPetName + " [CACHED]";
                    } else {
                        // Mặc định luôn là 5 (Cú) nếu chưa học được gì
                        petId = 5;
                        petName = "Default Owl (ID 5)";
                    }

                    System.err.println("GhostMod: [PetFeed] Using pet id=" + petId + " name=" + petName);

                    // --- Bước 3: Thu thập tối đa 15 item tím (N=3) từ bw.V ---
                    bw[] feedItems = new bw[AUTO_FEED_MAX_ITEMS];
                    int[] feedSlots = new int[AUTO_FEED_MAX_ITEMS];
                    int feedCount = 0;
                    for (int ii = 0; ii < bw.V.c() && feedCount < AUTO_FEED_MAX_ITEMS; ii++) {
                        bw inv = (bw) bw.V.a(ii);
                        if (inv != null && inv.N == AUTO_FEED_ITEM_GRADE && inv.u != 9) {
                            feedItems[feedCount] = inv;
                            feedSlots[feedCount] = ii;
                            feedCount++;
                        }
                    }
                    System.err.println("GhostMod: [PetFeed] Found " + feedCount + " purple items to feed");

                    if (feedCount == 0) {
                        System.err.println("GhostMod: [PetFeed] No purple items found, abort.");
                        return;
                    }

                    for (int fi = 0; fi < feedCount; fi++) {
                        bw itm = feedItems[fi];
                        int slot = feedSlots[fi];
                        System.err.println("GhostMod: [PetFeed] Feeding item [" + (fi+1) + "/" + feedCount + "]" +
                            " id=" + itm.O + " type=" + itm.u + " grade=" + itm.N + " slot=" + slot);

                        try {
                            System.err.println("GhostMod: [PetFeed] Gửi packet 44 (Interact) de mo khoa pet truoc khi an...");
                            q.a().c((byte) 2, petId);
                            Thread.sleep(300); // Đợi server xử lý tương tác
                        } catch (Exception e) {}

                        // Gửi packet opcode 45: petId, itemId, itemType, invType
                        // Note: The 4th argument is NOT the slot index! It is the inventory type! (1 = Character Inventory)
                        q.a().a(petId, (short) itm.O, (byte) itm.u, (byte) 1);

                        // Chờ 2.5s cho server phản hồi
                        try { Thread.sleep(AUTO_FEED_DELAY_MS); } catch (Exception e) {}

                        // Tắt thông báo (dismiss dialog / alert)
                        fu.s = null;
                        fu.t = null;
                        System.err.println("GhostMod: [PetFeed] Dismissed notification after item " + (fi+1));
                    }

                    System.err.println("GhostMod: [PetFeed] Feed routine DONE, fed " + feedCount + " items.");

                } catch (Throwable t) {
                    System.err.println("GhostMod: [PetFeed] Error during feed: " + t);
                    t.printStackTrace();
                }
            }
        }).start();
    }

    private static void pressKey(int keyIndex, int holdMs) throws Exception {
        fu.am[keyIndex] = true;
        fu.al[keyIndex] = true;
        Thread.sleep(holdMs);
        fu.am[keyIndex] = false;
        fu.al[keyIndex] = false;
    }

    private static void pressEnterWithLog(String reason, int holdMs) throws Exception {
        System.err.println("GhostMod: ENTER key DOWN reason=" + reason);
        fu.am[5] = true;
        fu.al[5] = true;
        Thread.sleep(holdMs);
        fu.am[5] = false;
        fu.al[5] = false;
        System.err.println("GhostMod: ENTER key UP reason=" + reason);
    }

    private static void ensurePersistentAutoAttackEnabled(String reason) {
        byte beforeState = bq.o;
        boolean beforePersistent = bq.Y;
        if (beforePersistent && beforeState >= 0) {
            System.err.println("GhostMod: Auto Attack already armed reason=" + reason
                    + " state=" + beforeState
                    + " persistent=" + beforePersistent);
            return;
        }
        bq.o = 0;
        bq.Y = true;
        System.err.println("GhostMod: Auto Attack armed reason=" + reason
                + " beforeState=" + beforeState
                + " beforePersistent=" + beforePersistent
                + " afterState=" + bq.o
                + " afterPersistent=" + bq.Y);
    }

    private static boolean isPersistentAutoAttackActive() {
        return bq.Y && bq.o == 1;
    }

    private static void armTargetRefresh(String reason) {
        synchronized (GhostMod.class) {
            targetRefreshPending = true;
        }
        System.err.println("GhostMod: Target refresh armed reason=" + reason);
    }

    private static void performInitialTargetRefreshIfNeeded(String reason) throws Exception {
        synchronized (GhostMod.class) {
            if (!targetRefreshPending) {
                return;
            }
            if (!isActiveMapContext() || cn.g == null) {
                return;
            }
            targetRefreshPending = false;
        }

        try {
            clearNonMonsterTarget("target_refresh_before_" + reason);
            System.err.println("GhostMod: Target refresh START reason=" + reason);
            cn.g.a(0, 0);
            Thread.sleep(350L);
            if (cn.i != null) {
                System.err.println("GhostMod: Target refresh RESULT class=" + cn.i.getClass().getName()
                        + " name=\"" + sanitizeForLog(cn.i.cC) + "\""
                        + " cv=" + cn.i.cv
                        + " cy=" + cn.i.cy);
            } else {
                System.err.println("GhostMod: Target refresh RESULT target=null");
            }
            clearNonMonsterTarget("target_refresh_after_" + reason);
        } catch (Throwable t) {
            synchronized (GhostMod.class) {
                targetRefreshPending = true;
            }
            throw new Exception("target refresh failed: " + t.toString());
        }
    }

    private static void clearNonMonsterTarget(String reason) {
        if (cn.i != null && !isLikelyMonster(cn.i)) {
            System.err.println("GhostMod: Clearing non-monster target reason=" + reason
                    + " targetClass=" + cn.i.getClass().getName()
                    + " targetName=\"" + sanitizeForLog(cn.i.cC) + "\""
                    + " cv=" + cn.i.cv);
            cn.i = null;
        }
    }

    private static boolean isLikelyMonster(fa target) {
        if (target == null) return false;
        if (target == cn.g) return false;
        if (!(target instanceof cc) && !(target instanceof ac) && !(target instanceof a)) return false;
        if (target.cv != 1) return false;
        if (target.cG == 4 || target.cL || target.bJ || target.dz) return false;
        if (target.x() || target.l_() || target.D() || target.B() || target.C() || target.q_() || target.w()) return false;
        return true;
    }

    private static boolean hasValidCombatTarget() {
        return isLikelyMonster(cn.i);
    }

    private static boolean acquireMonsterTarget() throws Exception {
        performInitialTargetRefreshIfNeeded("acquire");
        // Delay 1s between attempts to avoid lag and accidental UI interactions.
        for (int i = 0; i < 8; i++) {
            clearNonMonsterTarget("acquire_attempt_" + (i + 1));
            if (hasValidCombatTarget()) {
                System.err.println("GhostMod: Monster target acquired (attempt " + (i + 1) + ").");
                return true;
            }
            if (selectNearestMonster()) {
                if (hasValidCombatTarget()) {
                    System.err.println("GhostMod: Monster target acquired (attempt " + (i + 1) + ").");
                    return true;
                }
            }
            Thread.sleep(1000);
        }
        System.err.println("GhostMod: No valid monster target after 8 attempts. entities=" + buildEntitySummary());
        return false;
    }

    private static boolean selectNearestMonster() {
        try {
            if (cn.g == null || cn.j == null) return false;

            clearNonMonsterTarget("select_start");
            if (hasValidCombatTarget()) {
                return true;
            }

            cn.i = null;
            et objects = cn.j;
            int size = objects.c();
            fa nearest = null;
            long minDist = 9999999;

            for (int i = 0; i < size; i++) {
                Object o = objects.a(i);
                if (o instanceof fa) {
                    fa obj = (fa) o;
                    if (isLikelyMonster(obj)) {
                        long dx = obj.aZ - cn.g.aZ;
                        long dy = obj.ba - cn.g.ba;
                        long dist = (dx * dx) + (dy * dy);
                        if (dist < minDist) {
                            minDist = dist;
                            nearest = obj;
                        }
                    }
                }
            }

            if (nearest != null) {
                System.err.println("GhostMod: Locked on monster id=" + nearest.cu
                        + " name=\"" + sanitizeForLog(nearest.cC) + "\""
                        + " class=" + nearest.getClass().getName());
                nearest.dm = 5;
                cn.i = nearest;
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            System.err.println("GhostMod: Targeting Error: " + e.getMessage());
            return false;
        }
    }

    private static String buildEntitySummary() {
        if (cn.j == null) {
            return "none";
        }

        int total = 0;
        int monsters = 0;
        int boards = 0;
        int drops = 0;
        int specials = 0;
        int players = 0;

        for (int i = 0; i < cn.j.c(); i++) {
            Object object = cn.j.a(i);
            if (!(object instanceof fa)) {
                continue;
            }
            fa target = (fa) object;
            total++;
            if (target.cv == 0) {
                players++;
            } else if (target instanceof ba) {
                boards++;
            } else if (target instanceof fv || target instanceof cu || target instanceof cv || target instanceof s) {
                if (target instanceof fv) {
                    drops++;
                } else {
                    specials++;
                }
            } else if (target instanceof cc || target instanceof ac || target instanceof a) {
                monsters++;
            }
        }
        return "total=" + total
                + " monsters=" + monsters
                + " boards=" + boards
                + " drops=" + drops
                + " specials=" + specials
                + " players=" + players;
    }

    private static void dismissPostLoginOverlayIfPresent(String reason) throws Exception {
        Thread.sleep(1000L);
        boolean hasDialog = fu.s != null || fu.t != null;
        boolean hasMenu = fu.p != null && fu.p.a;
        System.err.println("GhostMod: Login ENTER dispatch reason=" + reason
                + " dialog=" + hasDialog
                + " menu=" + hasMenu
                + " screen=" + (fu.a != null ? fu.a.getClass().getName() : "null"));
        if (!hasDialog && !hasMenu) {
            System.err.println("GhostMod: Login overlay absent, skip ENTER reason=" + reason);
            return;
        }
        pressEnterWithLog("login_" + reason, 220);
        Thread.sleep(300L);
    }

    public static void autoAttackKickstart() {
        autoAttackKickstart(false);
    }

    private static void autoAttackKickstart(final boolean allowPostLoginSetup) {
        autoAttackKickstart(false, "auto", allowPostLoginSetup);
    }

    private static void autoAttackKickstart(final boolean force, final String reason, final boolean allowPostLoginSetup) {
        long now = System.currentTimeMillis();
        boolean usePostLoginSetup = false;
        synchronized(GhostMod.class) {
            if (recoveryInProgress
                    || autoAttackSuppressed
                    || (!force && shouldSkipLoginKickstart(getCurrentCharacterName()))
                    || shouldRecoverFor(getCurrentCharacterName())
                    || (!force && kickstartTriggered)
                    || (!force && (now - lastKickstartAttempt < 10000))) {
                return;
            }
            if (allowPostLoginSetup && postLoginSetupPending) {
                usePostLoginSetup = true;
                postLoginSetupPending = false;
            }
            kickstartTriggered = true;
            lastKickstartAttempt = now;
        }
        final boolean postLoginSetup = usePostLoginSetup;

        try {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        System.err.println("GhostMod: Login Kickstart Thread STARTED reason="
                                + reason
                                + " force=" + force
                                + " postLoginSetup=" + postLoginSetup
                                + " (Intelligent targeting)");
                        dismissPostLoginOverlayIfPresent("kickstart_" + reason);
                        System.err.println("GhostMod: Waiting 4.5s for stabilization...");
                        Thread.sleep(4500);
                        
                        if (cn.g != null && fu.am != null) {
                            ensurePersistentAutoAttackEnabled("kickstart");
                            clearNonMonsterTarget("kickstart_begin");
                            if (acquireMonsterTarget()) {
                                if (isPersistentAutoAttackActive()) {
                                    System.err.println("GhostMod: Auto Attack already active in kickstart, skip manual ENTER after target lock.");
                                } else {
                                    Thread.sleep(200);
                                    for (int i = 0; i < KICKSTART_ENTER_PRESSES; i++) {
                                        if (!hasValidCombatTarget()) break;
                                        System.err.println("GhostMod: Action -> Simulate ENTER [Index 5] after target lock (" + (i + 1) + "/" + KICKSTART_ENTER_PRESSES + ")...");
                                        pressKey(5, 220);
                                        if (i + 1 < KICKSTART_ENTER_PRESSES) Thread.sleep(780);
                                    }
                                }
                                System.err.println("GhostMod: Login Kickstart Logic FINISHED");
                            } else {
                                System.err.println("GhostMod: No valid monster target, skip skill press.");
                            }
                            if (postLoginSetup) {
                                requestWhiteMedalDropClose("kickstart_" + reason, KICKSTART_MEDAL_DROP_DELAY_MS);
                            } else {
                                System.err.println("GhostMod: Post-login medal/auto-buff setup skipped reason=" + reason);
                            }
                        } else {
                            System.err.println("GhostMod: ERROR - Player or Memory state missing");
                            synchronized(GhostMod.class) { kickstartTriggered = false; }
                        }
                    } catch (Exception e) {
                        System.err.println("GhostMod: Worker Error: " + e.getMessage());
                        synchronized(GhostMod.class) { kickstartTriggered = false; }
                    }
                }
            }).start();
        } catch (Throwable t) {
            System.err.println("GhostMod: Thread Error: " + t.getMessage());
            synchronized(GhostMod.class) { kickstartTriggered = false; }
        }
    }

    public static void saveAutoTarget() {}
    public static void saveAutoAttack() {}
    public static void loadSettings() {}

    /** Migrates the original 3 pages x 5 slots save into 3 pages x 10 slots. */
    public static byte[] expandHotbar(byte[] data) {
        final int pages = 3;
        final int oldSlots = 5;
        final int newSlots = 10;
        final int bytesPerSlot = 4;
        if (data == null || data.length != pages * oldSlots * bytesPerSlot) {
            return data;
        }

        byte[] expanded = new byte[pages * newSlots * bytesPerSlot];
        for (int page = 0; page < pages; page++) {
            int oldOffset = page * oldSlots * bytesPerSlot;
            int newOffset = page * newSlots * bytesPerSlot;
            System.arraycopy(data, oldOffset, expanded, newOffset, oldSlots * bytesPerSlot);
            for (int slot = oldSlots; slot < newSlots; slot++) {
                expanded[newOffset + slot * bytesPerSlot + 2] = (byte)-1;
            }
        }
        return expanded;
    }
}
