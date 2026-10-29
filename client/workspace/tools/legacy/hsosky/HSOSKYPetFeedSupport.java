/**
 * Template compiled against the unobfuscated client. The patcher remaps every
 * bw/et/q reference to the matching classes and members in HSOSKY.
 */
public final class HSOSKYPetFeedSupport implements Runnable {
    private static final long SCAN_INTERVAL_MS = 60000L;
    private static final long PET_INTERACT_DELAY_MS = 50L;
    private static final long BETWEEN_ITEMS_DELAY_MS = 500L;
    private static final long SERVER_RESPONSE_DELAY_MS = 1000L;
    private static final long DISMISS_KEY_HOLD_MS = 150L;
    private static volatile boolean started;
    private static volatile int feedMode = 3;
    private static volatile boolean autoPlayersEnabled;

    private HSOSKYPetFeedSupport() {
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        Thread worker = new Thread(new HSOSKYPetFeedSupport());
        worker.start();
    }

    public static boolean handleChat(String message) {
        if (message == null) {
            return false;
        }
        String command = message.trim();
        if ("4".equals(command)) {
            feedMode = 4;
            System.out.println("PetFeed: mode 4 (keep orange Gunner equipment)");
            return true;
        }
        if ("5".equals(command)) {
            feedMode = 5;
            System.out.println("PetFeed: mode 5 (feed all yellow + purple + orange)");
            return true;
        }
        if ("6".equals(command)) {
            autoPlayersEnabled = true;
            System.out.println("AutoCombat: monsters + hostile players");
            return true;
        }
        if ("7".equals(command)) {
            autoPlayersEnabled = false;
            System.out.println("AutoCombat: monsters only");
            return true;
        }
        if ("3".equals(command)) {
            feedMode = 3;
            System.out.println("PetFeed: mode 3 (yellow + purple)");
            return true;
        }
        if ("help".equalsIgnoreCase(command)) {
            fu.a("LỆNH MOD\n"
                    + "3: Pet ăn đồ Vàng và Tím (mặc định)\n"
                    + "4: Pet ăn thêm đồ Cam, giữ đồ Cam Xạ Thủ\n"
                    + "5: Pet ăn mọi đồ Vàng, Tím và Cam\n"
                    + "6: Tàn sát Quái và Người chơi đối địch\n"
                    + "7: Chỉ tàn sát Quái (mặc định)\n"
                    + "help: Hiện danh sách lệnh");
            return true;
        }
        return false;
    }

    public static boolean isAutoPlayersEnabled() {
        return autoPlayersEnabled;
    }

    public static boolean isAutoTargetType(int targetType) {
        return targetType == 1 || autoPlayersEnabled && targetType == 0;
    }

    public void run() {
        try {
            Thread.sleep(SCAN_INTERVAL_MS);
        } catch (Throwable ignored) {
        }
        while (true) {
            long cycleStartedAt = System.currentTimeMillis();
            try {
                scanAndFeedAll();
            } catch (Throwable ignored) {
                // Keep the scheduler alive across map/login transitions.
            }
            long remaining = SCAN_INTERVAL_MS - (System.currentTimeMillis() - cycleStartedAt);
            if (remaining < 1000L) {
                remaining = 1000L;
            }
            try {
                Thread.sleep(remaining);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void scanAndFeedAll() throws Exception {
        et inventory = bw.V;
        if (inventory == null || inventory.c() == 0) {
            return;
        }

        short petId = 5; // GhostMod's safe fallback (Owl) until garden data is available.
        et pets = bw.X;
        if (pets != null && pets.c() > 0) {
            bw pet = (bw) pets.a(0);
            if (pet != null && pet.u == 9) {
                petId = (short) pet.O;
            }
        }

        // Snapshot matching objects first because server replies can mutate inventory.
        bw[] matches = new bw[inventory.c()];
        int matchCount = 0;
        for (int slot = 0; slot < inventory.c(); slot++) {
            bw item = (bw) inventory.a(slot);
            if (item == null || item.u == 9 || item.N < 2 || item.N > 4) {
                continue;
            }
            if (feedMode == 3 && item.N == 4) {
                continue;
            }
            // Mode 4 preserves orange equipment dedicated to Gunner (class id 3).
            // Mode 5 deliberately bypasses this class protection.
            if (feedMode == 4 && item.N == 4 && item.u == 3 && item.M == 3) {
                continue;
            }

            matches[matchCount++] = item;
        }

        for (int index = 0; index < matchCount; index++) {
            bw item = matches[index];
            q.a().c((byte) 2, petId);
            Thread.sleep(PET_INTERACT_DELAY_MS);
            q.a().a(petId, (short) item.O, (byte) item.u, (byte) 1);
            if (index + 1 < matchCount) {
                Thread.sleep(BETWEEN_ITEMS_DELAY_MS);
            }
        }

        if (matchCount > 0) {
            Thread.sleep(SERVER_RESPONSE_DELAY_MS);
            // Press Enter after the server has had time to show the result dialog.
            fu.am[5] = true;
            fu.al[5] = true;
            Thread.sleep(DISMISS_KEY_HOLD_MS);
            fu.am[5] = false;
            fu.al[5] = false;
        }
    }
}
