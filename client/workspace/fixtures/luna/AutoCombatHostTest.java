import luna.api.AutoCombatSnapshot;
import luna.api.BachHoAutomationBridge;
import luna.api.LunaClock;
import luna.core.MovementArbiter;
import luna.features.AutoCombatController;

/** Host fixture for saved-position auto combat and native-mode passthrough. */
public final class AutoCombatHostTest {

    public static void main(String[] args) {
        testNoPositionUsesNativeMode();
        testSaveLoadClearAndToggle();
        testReturnLeashAndActivePath();
        testMapZoneRoutingAndThrottle();
        testRouteOwnersKeepTheirPaths();
        testTargetGateAndRepeatedLogin();
        System.out.println("OK - auto combat host fixture");
    }

    private static void testNoPositionUsesNativeMode() {
        FakeBridge bridge = new FakeBridge();
        bridge.autoEnabled = true;
        bridge.live.map = 4;
        bridge.live.zone = 1;
        bridge.live.x = 120;
        bridge.live.y = 240;
        bridge.live.ready = true;
        bridge.nativePathVersion = 7;
        AutoCombatController controller = new AutoCombatController(
                bridge, new FakeClock(1000L), new MovementArbiter());

        controller.onLoginEntry();
        controller.tick();

        check(!controller.hasPosition(), "unset position remains absent");
        check(!controller.shouldPauseNativeCombat(), "ordinary mode never blocks native rearm");
        check(bridge.autoEnabled, "native auto-attack flag is preserved");
        check(bridge.holdCalls == 0 && bridge.originCalls == 0,
                "unset position does not snap or set a combat origin");
        check(bridge.mapCalls == 0 && bridge.zoneCalls == 0 && bridge.walkCalls == 0,
                "unset position does not route or change zones");
        check(bridge.pauseCalls == 0 && bridge.stopCalls == 0,
                "unset position leaves native combat and path alone");
        check(bridge.nativePathVersion == 7,
                "unset position leaves the native path unchanged");
        check(controller.allowTarget(new FakeTarget(10000, 10000)),
                "native target validation remains unrestricted without a saved position");
    }

    private static void testSaveLoadClearAndToggle() {
        FakeBridge bridge = new FakeBridge();
        bridge.live.ready = true;
        bridge.live.map = 18;
        bridge.live.zone = 2;
        bridge.live.x = 240;
        bridge.live.y = 456;
        AutoCombatController controller = new AutoCombatController(
                bridge, new FakeClock(1000L), new MovementArbiter());

        check(controller.handleMenuAction(AutoCombatController.SAVE_POSITION),
                "save action is handled");
        check(controller.hasPosition(), "save action stores a position");
        check(bridge.saved[0] == 18 && bridge.saved[1] == 240
                && bridge.saved[2] == 456 && bridge.saved[3] == 2,
                "RMS adapter receives map, pixel x/y, and zero-based zone");
        check(!bridge.autoEnabled, "saving a position does not change auto-attack state");

        check(controller.handleMenuAction(AutoCombatController.TOGGLE),
                "toggle action is handled");
        check(bridge.autoEnabled && bridge.enableCalls == 1,
                "toggle enables the existing native auto-attack setting");

        AutoCombatController restored = new AutoCombatController(
                bridge, new FakeClock(2000L), new MovementArbiter());
        check(restored.hasPosition() && restored.position(0) == 18
                && restored.position(1) == 240 && restored.position(2) == 456
                && restored.position(3) == 2,
                "new controller restores the persisted map/zone/pixel position");

        check(restored.handleMenuAction(AutoCombatController.CLEAR_POSITION),
                "clear action is handled");
        check(!restored.hasPosition() && bridge.saved[0] == -1,
                "clear action persists the unset sentinel");
        check(bridge.autoEnabled, "clearing coordinates preserves enabled native auto attack");

        check(restored.handleMenuAction(AutoCombatController.TOGGLE),
                "second toggle action is handled");
        check(!bridge.autoEnabled && bridge.disableCalls == 1,
                "toggle off uses the existing native auto-attack transition");
    }

    private static void testReturnLeashAndActivePath() {
        FakeBridge bridge = bridgeAt(3, 0, 131, 100, true);
        bridge.saved = new int[] {3, 100, 100, 0};
        MovementArbiter movement = new MovementArbiter();
        FakeClock clock = new FakeClock(1000L);
        AutoCombatController controller = new AutoCombatController(bridge, clock, movement);
        controller.onLoginEntry();
        settle(controller, clock);

        bridge.live.moving = true;
        controller.tick();
        check(controller.ownsMovement() && movement.currentOwner() == MovementArbiter.OWNER_COMBAT,
                "outside the 30px Manhattan leash, combat owns the return path");
        check(controller.shouldPauseNativeCombat(), "native rearm yields while returning");
        check(bridge.pauseCalls > 0 && bridge.walkCalls == 0 && bridge.holdCalls == 0,
                "return waits for an already active native path instead of replacing it");

        bridge.live.moving = false;
        clock.advance(500L);
        controller.tick();
        check(bridge.walkCalls == 1 && bridge.lastWalkX == 100 && bridge.lastWalkY == 100,
                "idle return path heads to the saved pixel coordinates");

        FakeBridge within = bridgeAt(3, 0, 130, 100, true);
        within.saved = new int[] {3, 100, 100, 0};
        FakeClock withinClock = new FakeClock(1000L);
        MovementArbiter withinMovement = new MovementArbiter();
        AutoCombatController withinController = new AutoCombatController(
                within, withinClock, withinMovement);
        withinController.onLoginEntry();
        settle(withinController, withinClock);
        withinController.tick();
        check(within.holdCalls == 1 && within.snappedX == 100 && within.snappedY == 100,
                "exactly 30px Manhattan drift snaps to the saved position");
        check(!withinController.shouldPauseNativeCombat(), "native rearm resumes at the saved spot");
        check(withinMovement.currentOwner() == MovementArbiter.OWNER_NONE,
                "combat releases the route lease after reaching the leash");
        check(within.originCalls == 1 && within.originX == 100 && within.originY == 100,
                "native combat origin is set to the saved position after the return");
    }

    private static void testMapZoneRoutingAndThrottle() {
        FakeBridge bridge = bridgeAt(9, 1, 800, 700, true);
        bridge.saved = new int[] {7, 240, 360, 2};
        FakeClock clock = new FakeClock(1000L);
        AutoCombatController controller = new AutoCombatController(
                bridge, clock, new MovementArbiter());
        controller.onLoginEntry();
        settle(controller, clock);
        controller.tick();
        check(bridge.mapCalls == 1 && bridge.lastMap == 7 && bridge.zoneCalls == 0,
                "different map routes back to the saved map first");

        bridge.live.map = 7;
        settle(controller, clock);
        controller.tick();
        check(bridge.zoneCalls == 1 && bridge.lastZone == 2,
                "same-map return requests the saved zero-based zone");
        clock.advance(2499L);
        controller.tick();
        check(bridge.zoneCalls == 1, "zone requests are throttled before 2500ms");
        clock.advance(1L);
        controller.tick();
        check(bridge.zoneCalls == 2, "zone request becomes available at 2500ms");
        check(bridge.holdCalls == 0, "combat does not snap while the saved zone is absent");
    }

    private static void testRouteOwnersKeepTheirPaths() {
        int[] owners = {MovementArbiter.OWNER_AUTOTRADE, MovementArbiter.OWNER_N4};
        for (int owner : owners) {
            FakeBridge bridge = bridgeAt(9, 1, 800, 700, true);
            bridge.saved = new int[] {7, 240, 360, 2};
            bridge.ownsRoute = true;
            FakeClock clock = new FakeClock(1000L);
            MovementArbiter movement = new MovementArbiter();
            check(movement.acquire(owner), "test route acquires movement");
            int[] routePath = new int[] {1, 2, 3};
            check(movement.submitPath(owner, routePath, routePath.length),
                    "test route installs its path");
            AutoCombatController controller = new AutoCombatController(bridge, clock, movement);
            controller.onLoginEntry();
            controller.tick();
            check(movement.currentOwner() == owner && movement.currentPath() == routePath,
                    "combat yields to trade/N4 without clearing the route path");
            check(bridge.stopCalls == 0 && bridge.mapCalls == 0 && bridge.walkCalls == 0,
                    "combat does not stop or replace another controller's movement");
            check(!controller.ownsMovement(), "combat does not claim movement during another route");
        }
    }

    private static void testTargetGateAndRepeatedLogin() {
        FakeBridge bridge = bridgeAt(5, 2, 240, 480, true);
        bridge.saved = new int[] {5, 240, 480, 2};
        FakeClock clock = new FakeClock(1000L);
        AutoCombatController controller = new AutoCombatController(
                bridge, clock, new MovementArbiter());
        controller.onLoginEntry();
        check(!controller.allowTarget(new FakeTarget(9000, 9000)),
                "native target gate is closed during return/settle");
        settle(controller, clock);
        controller.tick();
        check(controller.allowTarget(new FakeTarget(9000, 9000)),
                "at the saved spot, native validator may accept targets without an added range");

        controller.onLoginEntry();
        check(!controller.allowTarget(new FakeTarget(240, 480)),
                "relogin closes the target gate while returning to the saved spot");
        settle(controller, clock);
        controller.tick();
        check(bridge.holdCalls == 2 && bridge.snappedX == 240 && bridge.snappedY == 480,
                "same map/zone/coordinates re-arm at the saved spot after every login");

        bridge.live.zone = 1;
        check(!controller.allowTarget(new FakeTarget(240, 480)),
                "target gate remains closed on a different zone");
        bridge.autoEnabled = false;
        check(controller.allowTarget(new FakeTarget(240, 480)),
                "disabled native auto attack leaves ordinary target handling alone");
    }

    private static FakeBridge bridgeAt(int map, int zone, int x, int y, boolean ready) {
        FakeBridge bridge = new FakeBridge();
        bridge.autoEnabled = true;
        bridge.live.ready = ready;
        bridge.live.map = map;
        bridge.live.zone = zone;
        bridge.live.x = x;
        bridge.live.y = y;
        return bridge;
    }

    private static void settle(AutoCombatController controller, FakeClock clock) {
        for (int i = 0; i < 10; i++) {
            controller.tick();
            clock.advance(100L);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeClock implements LunaClock {
        private long now;

        FakeClock(long start) { now = start; }
        public long nowMillis() { return now; }
        void advance(long elapsed) { now += elapsed; }
    }

    private static final class FakeTarget {
        final int x;
        final int y;

        FakeTarget(int x, int y) { this.x = x; this.y = y; }
    }

    private static final class FakeBridge implements BachHoAutomationBridge {
        final AutoCombatSnapshot live = new AutoCombatSnapshot();
        int[] saved = new int[] {-1, 0, 0, 0};
        boolean autoEnabled;
        boolean ownsRoute;
        int enableCalls;
        int disableCalls;
        int pauseCalls;
        int holdCalls;
        int originCalls;
        int mapCalls;
        int zoneCalls;
        int walkCalls;
        int stopCalls;
        int noticeCalls;
        int menuCalls;
        int lastMap;
        int lastZone;
        int lastWalkX;
        int lastWalkY;
        int snappedX;
        int snappedY;
        int originX;
        int originY;
        int nativePathVersion;
        String menuTitle;
        String[] menuLabels;
        int[] menuActions;

        public void readCombat(AutoCombatSnapshot out) {
            out.ready = live.ready;
            out.map = live.map;
            out.zone = live.zone;
            out.x = live.x;
            out.y = live.y;
            out.moving = live.moving;
            out.fighting = live.fighting;
        }
        public boolean isAutoAttackEnabled() { return autoEnabled; }
        public boolean routeOwnsMovement() { return ownsRoute; }
        public void setAutoAttackEnabled(boolean enabled) {
            autoEnabled = enabled;
            if (enabled) enableCalls++; else disableCalls++;
        }
        public void pauseCombat() { pauseCalls++; }
        public void setCombatOrigin(int x, int y) {
            originCalls++;
            originX = x;
            originY = y;
        }
        public void holdPosition(int x, int y) {
            holdCalls++;
            snappedX = x;
            snappedY = y;
            live.x = x;
            live.y = y;
        }
        public void returnToMap(int map) { mapCalls++; lastMap = map; }
        public void changeZone(int zone) { zoneCalls++; lastZone = zone; }
        public boolean walkTo(int x, int y) {
            walkCalls++;
            lastWalkX = x;
            lastWalkY = y;
            return true;
        }
        public void stopWalking() { stopCalls++; nativePathVersion++; live.moving = false; }
        public int targetX(Object target) { return ((FakeTarget) target).x; }
        public int targetY(Object target) { return ((FakeTarget) target).y; }
        public void showMenu(String title, String[] labels, int[] actions) {
            menuCalls++;
            menuTitle = title;
            menuLabels = labels.clone();
            menuActions = actions.clone();
        }
        public void notice(String text) { noticeCalls++; }
        public void loadPosition(int[] position) {
            for (int i = 0; i < position.length; i++) position[i] = saved[i];
        }
        public void savePosition(int[] position) { saved = position.clone(); }
        public boolean isWorldReady() { return live.ready; }
        public void openMaterialMenu() { }
        public String whiteMedalAction() { return null; }
        public void closeWhiteMedalDrop() { }
        public void hideMaterialMenu() { }
        public boolean finishMaterialDialog() { return true; }
    }
}
