import luna.api.AutoCombatSnapshot;
import luna.api.BachHoAutomationBridge;
import luna.core.FakeLunaClock;
import luna.features.LoginMaterialController;

/** Deterministic host coverage for the post-login material toggle transaction. */
public final class LoginMaterialHostTest {

    public static void main(String[] args) {
        FakeLunaClock clock = new FakeLunaClock(0L);
        FakeBridge bridge = new FakeBridge();
        LoginMaterialController controller = new LoginMaterialController(bridge, clock);

        controller.onLoginEntry();
        controller.tick();
        check(bridge.openCalls == 0, "root menu waits for the five-second login delay");
        clock.setMillis(5000L);
        bridge.worldReady = false;
        controller.tick();
        check(bridge.openCalls == 0, "login work waits for the stable game screen");
        bridge.worldReady = true;
        controller.tick();
        check(bridge.openCalls == 1, "first stable tick sends one root-menu selection");
        check(controller.isPending(), "transaction remains pending while the material menu loads");

        bridge.action = "Mở rớt nguyên liệu mề đay trắng";
        clock.setMillis(7499L);
        controller.tick();
        check(bridge.actionReads == 0, "menu label is not read before the response delay");
        clock.setMillis(7500L);
        controller.tick();
        check(bridge.actionReads == 1, "material action is read after 2.5 seconds");
        check(bridge.closeCalls == 0, "Mở means drops are already off, so no toggle is sent");
        check(bridge.hideCalls == 1, "observed material menu is hidden after the no-op decision");
        check(!controller.isPending(), "known closed state finishes the transaction");

        bridge.action = " Tắt rớt nguyên liệu mề đay trắng";
        controller.onLoginEntry();
        clock.setMillis(12500L);
        controller.tick();
        check(bridge.openCalls == 2, "a later login starts a new transaction");
        clock.setMillis(15000L);
        controller.tick();
        check(bridge.closeCalls == 1, "Tắt means drops are on, so one close toggle is sent");
        check(bridge.hideCalls == 2, "menu is hidden after the close toggle");
        check(controller.isPending(), "close waits briefly for its matching confirmation");
        bridge.relevantDialog = true;
        controller.tick();
        check(bridge.confirmPresses == 1 && bridge.confirmKeyReleases == 0,
                "matching material confirmation receives one OK key press");
        check(controller.isPending(), "confirmation remains pending until its key is released");
        controller.tick();
        check(bridge.confirmKeyReleases == 1, "synthetic OK key is released on the next tick");
        check(!controller.isPending(), "accepted material confirmation finishes the transaction");
        bridge.relevantDialog = false;

        bridge.action = null;
        controller.onLoginEntry();
        clock.setMillis(20000L);
        controller.tick();
        clock.setMillis(22500L);
        controller.tick();
        check(bridge.openCalls == 3, "unread menu schedules a bounded retry");
        check(bridge.closeCalls == 1, "unknown state never sends a blind toggle");
        clock.setMillis(25000L);
        controller.tick();
        check(bridge.openCalls == 4, "retry opens the root menu once");
        clock.setMillis(27500L);
        controller.tick();
        check(bridge.actionReads == 4, "retry reads the action once");
        check(bridge.closeCalls == 1, "second unknown state also avoids a blind toggle");
        check(bridge.noticeCalls == 1, "failed state reads report one notice");
        check(!controller.isPending(), "controller stops after its single retry");

        bridge.action = "Tắt rớt nguyên liệu mề đay trắng";
        controller.onLoginEntry();
        clock.setMillis(32500L);
        controller.tick();
        clock.setMillis(35000L);
        controller.tick();
        check(bridge.closeCalls == 2, "later login sends one more close toggle");
        bridge.unrelatedDialog = true;
        controller.tick();
        check(bridge.confirmPresses == 1, "unrelated dialogs are never accepted");
        check(controller.isPending(), "the bounded dialog check remains pending before timeout");
        clock.setMillis(37999L);
        controller.tick();
        check(controller.isPending(), "dialog check waits only until its deadline");
        clock.setMillis(38000L);
        controller.tick();
        check(!controller.isPending(), "unrelated dialogs are left alone when the wait expires");
        check(bridge.confirmPresses == 1 && bridge.confirmKeyReleases == 1,
                "timeout does not send another key press or release");
        check(bridge.unrelatedDialogPreserved, "unrelated dialog remains untouched");

        System.out.println("OK - Login material host test");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeBridge implements BachHoAutomationBridge {
        boolean worldReady = true;
        String action;
        int openCalls;
        int actionReads;
        int closeCalls;
        int hideCalls;
        int noticeCalls;
        boolean relevantDialog;
        boolean unrelatedDialog;
        boolean materialKeyOwned;
        boolean unrelatedDialogPreserved;
        int confirmPresses;
        int confirmKeyReleases;

        public void readCombat(AutoCombatSnapshot out) { }
        public boolean isAutoAttackEnabled() { return false; }
        public boolean routeOwnsMovement() { return false; }
        public void setAutoAttackEnabled(boolean enabled) { }
        public void pauseCombat() { }
        public void setCombatOrigin(int x, int y) { }
        public void holdPosition(int x, int y) { }
        public void returnToMap(int map) { }
        public void changeZone(int zone) { }
        public boolean walkTo(int x, int y) { return false; }
        public void stopWalking() { }
        public int targetX(Object target) { return 0; }
        public int targetY(Object target) { return 0; }
        public void showMenu(String title, String[] labels, int[] actions) { }
        public void notice(String text) { noticeCalls++; }
        public void loadPosition(int[] position) { }
        public void savePosition(int[] position) { }
        public boolean isWorldReady() { return worldReady; }
        public void openMaterialMenu() { openCalls++; }
        public String whiteMedalAction() { actionReads++; return action; }
        public void closeWhiteMedalDrop() { closeCalls++; }
        public void hideMaterialMenu() { hideCalls++; }
        public boolean finishMaterialDialog() {
            if (materialKeyOwned) {
                materialKeyOwned = false;
                confirmKeyReleases++;
                return true;
            }
            if (relevantDialog) {
                materialKeyOwned = true;
                confirmPresses++;
                return false;
            }
            if (unrelatedDialog) unrelatedDialogPreserved = true;
            return false;
        }
    }
}
