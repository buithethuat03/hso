import luna.api.AutoAttackAccess;
import luna.api.AutoAttackSettings;
import luna.core.FakeLunaClock;
import luna.features.AutoAttackController;
import luna.features.AutoAttackRuntime;
import java.lang.reflect.Field;

/** Host-side policy checks for the optional special-attack mode. */
public final class SpecialAttackHostTest {
    private static int checks;
    private static final int[] COOLDOWN_SKILLS = new int[] {7, 8, 19, 20, 6, 9};

    private static void check(boolean value, String text) {
        checks++;
        if (!value) throw new AssertionError(text);
    }

    private static final class Entity {
        int hp = 10;
        int distance;
        Entity(int distance) { this.distance = distance; }
    }

    private static final class Store implements AutoAttackSettings {
        int mode;
        int delay = 650;
        int saves;
        public void load(int[] values) { values[0] = mode; values[1] = delay; }
        public void save(boolean players, int value) {
            mode = players ? 1 : 0;
            delay = value;
            saves++;
        }
    }

    private static final class Access implements AutoAttackAccess {
        Object player = new Object();
        Entity[] entities = new Entity[0];
        boolean ready = true;
        boolean moving;
        boolean busy;
        int map = 1;
        int zone = 1;
        int attacks;
        int cancellations;
        int farmStops;
        int menuShows;
        int delayInputs;
        int lastTag;
        boolean activeDuringAttack;
        final boolean[] lastSkillAllowed = new boolean[COOLDOWN_SKILLS.length];
        Object lastTarget;
        AutoAttackController controller;

        public boolean ready() { return ready; }
        public Object player() { return player; }
        public int mapId() { return map; }
        public int zoneId() { return zone; }
        public int entityCount() { return entities.length; }
        public Object entityAt(int index) { return entities[index]; }
        public boolean validTarget(Object target, boolean players) {
            return target instanceof Entity && ((Entity)target).hp > 0;
        }
        public int distanceTo(Object target) { return ((Entity)target).distance; }
        public boolean combatBusy() { return busy; }
        public boolean movementRequested() { return moving; }
        public boolean attack(Object target) {
            attacks++;
            lastTarget = target;
            lastTag = controller.captureGeneration();
            activeDuringAttack = AutoAttackRuntime.specialActive();
            for (int i = 0; i < COOLDOWN_SKILLS.length; i++) {
                lastSkillAllowed[i] = AutoAttackRuntime.specialSkillAllowed(COOLDOWN_SKILLS[i]);
            }
            // Native code calls specialSent after every successful special-mode send.
            AutoAttackRuntime.specialSent();
            return true;
        }
        public void cancelAttack(Object target) { cancellations++; }
        public void stopFarm() { farmStops++; }
        public void showMenu(boolean enabled, boolean players, int delay) { menuShows++; }
        public void showDelayInput(int delay) { delayInputs++; }
        public void notice(String text) { }
    }

    private static final class Fixture {
        final FakeLunaClock clock = new FakeLunaClock();
        final Store store = new Store();
        final Access access = new Access();
        final AutoAttackController controller;
        Fixture() {
            AutoAttackRuntime.installForTest(access, store, clock);
            // The Auto đánh/Farm Special path under test runs with Tấn công quái thường OFF (default ON).
            AutoAttackRuntime.handleAction(AutoAttackController.NORMAL_MONSTERS);
            access.menuShows = 0;
            controller = installedRuntimeController();
            access.controller = controller;
        }
        void tick() { controller.tick(); }
        void advance(long amount) { clock.advance(amount); controller.tick(); }
    }

    private static AutoAttackController installedRuntimeController() {
        try {
            Field field = AutoAttackRuntime.class.getDeclaredField("controller");
            field.setAccessible(true);
            return (AutoAttackController)field.get(null);
        } catch (Exception failure) { throw new AssertionError(failure); }
    }

    private SpecialAttackHostTest() { }

    public static void main(String[] args) {
        defaultsAndToggle();
        invalidatesPendingAttack();
        cooldownAndDelayPolicy();
        targetAndMovementGates();
        System.out.println("SpecialAttackHostTest PASS (" + checks + " checks)");
    }

    private static void defaultsAndToggle() {
        Fixture f = new Fixture();
        check(AutoAttackController.SPECIAL == -116, "special action uses the reserved menu id");
        check(!f.controller.enabled() && !f.controller.specialEnabled(), "both automatic modes default OFF");
        check(!AutoAttackRuntime.specialEnabled() && !AutoAttackRuntime.specialActive(), "runtime reports OFF");
        check("Tấn công đặc biệt: [TẮT]".equals(AutoAttackRuntime.specialLabel()), "OFF label");
        check(f.store.saves == 0 && f.access.farmStops == 0, "defaults do not persist or claim Farm");

        check(f.controller.handleAction(AutoAttackController.SPECIAL), "special action is handled");
        check(f.controller.specialEnabled() && AutoAttackRuntime.specialEnabled(), "special mode toggles ON");
        check(!f.controller.enabled() && !AutoAttackRuntime.enabled(), "special mode does not enable auto attack");
        check("Tấn công đặc biệt: [BẬT]".equals(AutoAttackRuntime.specialLabel()), "ON label");
        check(f.store.saves == 0 && f.access.farmStops == 0, "special toggle is session-only and leaves Farm alone");
        check(f.access.menuShows == 1, "special toggle refreshes the native menu");
    }

    private static void invalidatesPendingAttack() {
        Fixture f = new Fixture();
        Entity target = new Entity(20);
        f.access.entities = new Entity[] {target};
        f.controller.setEnabled(true);
        f.advance(651);
        int oldTag = f.access.lastTag;
        int oldGeneration = f.controller.generation();
        check(f.access.attacks == 1 && oldTag > 0, "normal auto attack creates a tagged request");
        check(f.controller.allowRequest(oldTag, target), "pending request is initially valid");

        f.controller.handleAction(AutoAttackController.SPECIAL);
        check(f.controller.generation() != oldGeneration, "special toggle invalidates the attack generation");
        check(f.controller.target() == null && !f.controller.allowRequest(oldTag, target), "old pending cast is rejected");
        check(f.controller.enabled(), "toggling special leaves auto attack enabled");
    }

    private static void cooldownAndDelayPolicy() {
        Fixture f = new Fixture();
        Entity target = new Entity(20);
        f.access.entities = new Entity[] {target};
        check(f.controller.submitDelay("1000"), "long native delay is accepted");
        f.controller.handleAction(AutoAttackController.SPECIAL);
        f.controller.setEnabled(true);
        f.tick();
        check(f.access.attacks == 1 && f.access.activeDuringAttack, "special mode bypasses the configured delay during its cast");
        assertAllowed(f.access.lastSkillAllowed, true, "first send has no previous special-skill timestamp");

        f.advance(600);
        check(f.access.attacks == 2 && f.access.activeDuringAttack, "special mode continues without the native delay");
        assertAllowed(f.access.lastSkillAllowed, false, "600ms is still inside the special-skill cooldown");
        f.advance(600);
        check(f.access.attacks == 3, "a non-cooldown skill can still send during the cooldown");
        assertAllowed(f.access.lastSkillAllowed, false, "every successful special-mode send refreshes the cooldown timestamp");
        f.advance(601);
        check(f.access.attacks == 4, "special attack continues after the cooldown");
        assertAllowed(f.access.lastSkillAllowed, true, "all skills 7, 8, 19, and 20 are allowed after strictly more than 600ms");

        check(f.controller.handleAction(AutoAttackController.SPECIAL), "special mode toggles OFF");
        int before = f.access.attacks;
        f.tick();
        check(f.access.attacks == before, "OFF restores the configured 1000ms delay");
        f.advance(1000);
        check(f.access.attacks == before, "ordinary delay remains strict at exactly 1000ms");
        f.advance(1);
        check(f.access.attacks == before + 1 && !f.access.activeDuringAttack, "ordinary attack resumes at 1001ms");
    }

    private static void targetAndMovementGates() {
        Fixture f = new Fixture();
        f.controller.handleAction(AutoAttackController.SPECIAL);
        f.controller.setEnabled(true);
        f.tick();
        check(f.access.attacks == 0, "special mode still requires a valid target");

        Entity target = new Entity(10);
        f.access.entities = new Entity[] {target};
        f.access.moving = true;
        f.tick();
        check(f.access.attacks == 0, "special mode yields to native movement");
        f.access.moving = false;
        f.tick();
        check(f.access.attacks == 0, "movement recovery tick does not cast before the next controller pass");
        f.tick();
        check(f.access.attacks == 1, "special attack resumes after movement settles");

        target.hp = 0;
        f.tick();
        check(f.access.attacks == 1 && f.controller.target() == null, "dead target remains excluded in special mode");
    }

    private static void assertAllowed(boolean[] actual, boolean expected, String message) {
        int specialCount = 4;
        for (int i = 0; i < specialCount; i++) check(actual[i] == expected, message + " (skill " + COOLDOWN_SKILLS[i] + ")");
        for (int i = specialCount; i < actual.length; i++) check(actual[i], "other skill " + COOLDOWN_SKILLS[i] + " has no special cooldown");
    }
}
