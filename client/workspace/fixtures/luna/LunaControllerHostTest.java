import luna.api.LunaAction;
import luna.api.LunaBridgeApi;
import luna.api.LunaEntityView;
import luna.api.LunaMenuView;
import luna.api.LunaPlayerView;
import luna.api.LunaShopView;
import luna.core.FakeLunaClock;
import luna.core.MovementArbiter;
import luna.features.AutoTradeController;
import luna.features.N4Controller;
import luna.features.PersistentHorseController;
import luna.features.ReviveController;

/** Controller smoke test using only semantic fake bridge data. */
public final class LunaControllerHostTest {

    public static void main(String[] args) {
        FakeLunaClock clock = new FakeLunaClock(0L);
        FakeBridge bridge = new FakeBridge();
        ReviveController revive = new ReviveController(bridge, clock);
        revive.tick();
        check(bridge.lastAction == LunaAction.REVIVE, "dead player requests revive");
        clock.advance(500L);
        revive.tick();
        check(bridge.actionCount == 1, "revive retry waits for cooldown");
        clock.advance(500L);
        revive.tick();
        check(bridge.actionCount == 2, "revive retries after cooldown");

        MovementArbiter arbiter = new MovementArbiter();
        AutoTradeController trade = new AutoTradeController(bridge, clock, arbiter);
        N4Controller n4 = new N4Controller(bridge, clock, arbiter);
        check(trade.handleMenuAction(AutoTradeController.ACTION_BOOST),
                "trade menu reducer handles boost");
        check(!trade.isBoostEnabled(), "trade boost policy toggles semantically");
        trade.start();
        check(trade.state() == AutoTradeController.GOTO_START, "trade starts at route phase");
        check(trade.acquireMovement(), "trade acquires path ownership");
        check(n4.startAndAcquire(), "N4 preempts trade for combat");
        check(!trade.submitPath(new int[1], 1), "preempted trade cannot submit path");

        PersistentHorseController horse = new PersistentHorseController();
        check(horse.needsConfigLoad(), "horse config starts unloaded");
        horse.markConfigLoaded();
        check(horse.tick(0L, false, false, true) == PersistentHorseController.NONE,
                "horse does not mount on login screen");
        check(horse.tick(1000L, true, false, true, true) == PersistentHorseController.NONE,
                "horse waits while the first world screen is settling");
        check(horse.tick(1500L, true, true, true, true) == PersistentHorseController.ENABLE_AND_MOUNT,
                "horse re-arms on first world entry");
        check(horse.tick(4000L, true, true, true, true) == PersistentHorseController.NONE,
                "horse retry is rate limited");
        check(horse.tick(6500L, true, true, true, true) == PersistentHorseController.RETRY_MOUNT,
                "horse retry is available after cooldown");
        check(horse.tick(7000L, true, true, true, true) == PersistentHorseController.NONE,
                "horse does not retry after a completed mount request");
        horse.reportTransientFailure(7000L);
        check(horse.tick(9500L, true, true, true, true) == PersistentHorseController.RETRY_MOUNT,
                "horse retries an explicit transient item-use failure");
        horse.markMounted();
        check(horse.tick(10000L, true, true, true, true) == PersistentHorseController.NONE,
                "horse stops after the retry succeeds or reaches a terminal state");

        PersistentHorseController bounded = new PersistentHorseController();
        check(bounded.tick(0L, true, true, true, false)
                        == PersistentHorseController.ENABLE_AND_MOUNT,
                "bounded horse policy starts one delayed request");
        check(bounded.tick(5000L, true, true, true, true)
                        == PersistentHorseController.RETRY_MOUNT,
                "bounded horse policy sends its first request");
        bounded.reportTransientFailure(5000L);
        check(bounded.tick(7500L, true, true, true, true)
                        == PersistentHorseController.RETRY_MOUNT,
                "bounded horse policy permits the first transient retry");
        bounded.reportTransientFailure(7500L);
        check(bounded.tick(10000L, true, true, true, true)
                        == PersistentHorseController.RETRY_MOUNT,
                "bounded horse policy permits the second transient retry");
        bounded.reportTransientFailure(10000L);
        check(bounded.tick(12500L, true, true, true, true)
                        == PersistentHorseController.NONE,
                "bounded horse policy stops after two transient retries");

        check(horse.tick(10500L, false, false, true, false) == PersistentHorseController.NONE,
                "horse resets edge on logout");
        check(horse.tick(11000L, true, true, true, true) == PersistentHorseController.ENABLE_AND_MOUNT,
                "horse re-arms after a second login");

        PersistentHorseController firstOpen = new PersistentHorseController();
        check(firstOpen.needsConfigLoad(), "first open starts with RMS load pending");
        firstOpen.markConfigLoaded();
        check(firstOpen.tick(0L, true, true, true, true) == PersistentHorseController.ENABLE_AND_MOUNT,
                "horse mounts on the first game open");

        PersistentHorseController enableAfterReady = new PersistentHorseController();
        check(enableAfterReady.tick(0L, true, true, false, false) == PersistentHorseController.NONE,
                "disabled horse remains idle");
        check(enableAfterReady.tick(100L, true, true, true, true)
                        == PersistentHorseController.ENABLE_AND_MOUNT,
                "enabling horse after a stable screen starts a delayed mount");

        PersistentHorseController nativeMenu = new PersistentHorseController();
        nativeMenu.observeNativeToggle(true);
        check(nativeMenu.tick(0L, true, true, true, true)
                        == PersistentHorseController.EXTERNAL_ENABLE,
                "native horse menu request is not duplicated by lifecycle retry");

        System.out.println("OK - Luna controller host test");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeBridge implements LunaBridgeApi {
        int lastAction;
        int actionCount;
        String lastText;

        public String baselineId() { return "fixture"; }
        public boolean isWorldReady() { return true; }
        public void readPlayer(LunaPlayerView out) { out.clear(); out.setAlive(false); }
        public int entityCount() { return 0; }
        public boolean readEntity(int index, LunaEntityView out) { return false; }
        public void readMenu(LunaMenuView out) { out.clear(); }
        public void readShop(LunaShopView out) { out.clear(); }
        public void sendAction(LunaAction action) {
            lastAction = action.getType();
            lastText = action.getText();
            actionCount++;
        }
    }
}
