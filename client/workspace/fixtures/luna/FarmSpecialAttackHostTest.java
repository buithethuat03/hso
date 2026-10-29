import luna.api.AutoAttackAccess;
import luna.api.AutoAttackSettings;
import luna.api.SpecialAttackAccess;
import luna.core.FakeLunaClock;
import luna.features.AutoAttackController;
import luna.features.AutoAttackRuntime;
import java.util.HashSet;
import java.util.Set;

/** Host-side policy checks for sharing Special mode with native Auto Farm. */
public final class FarmSpecialAttackHostTest {
    private static int checks;

    private static void check(boolean value, String text) {
        checks++;
        if (!value) throw new AssertionError(text);
    }

    private static final class Settings implements AutoAttackSettings {
        public void load(int[] values) { values[0] = 0; values[1] = 650; }
        public void save(boolean players, int delay) { }
    }

    private static final class World implements AutoAttackAccess {
        final Object player = new Object();
        final Object target = new Object();
        final Skills skills = new Skills();
        AutoAttackController controller;
        boolean ready = true;
        boolean chooseResult;
        int attacks;

        public boolean ready() { return ready; }
        public Object player() { return player; }
        public int mapId() { return 1; }
        public int zoneId() { return 1; }
        public int entityCount() { return 1; }
        public Object entityAt(int index) { return target; }
        public boolean validTarget(Object value, boolean players) { return value == target; }
        public int distanceTo(Object value) { return value == target ? 20 : -1; }
        public boolean combatBusy() { return false; }
        public boolean movementRequested() { return false; }
        public boolean attack(Object value) {
            attacks++;
            chooseResult = controller.chooseSpecial(skills, false);
            return true;
        }
        public void cancelAttack(Object value) { }
        public void stopFarm() { }
        public void showMenu(boolean enabled, boolean players, int delay) { }
        public void showDelayInput(int delay) { }
        public void notice(String text) { }
    }

    private static final class Skills implements SpecialAttackAccess {
        int[] hotbar = new int[0];
        final Set<Integer> ready = new HashSet<Integer>();
        int sends;
        int lastSkill = -1;
        int lastSlot = -2;
        boolean lastFarm;

        public int specialSlotCount() { return hotbar.length; }
        public int specialSkillAt(int slot) { return hotbar[slot]; }
        /** Models native learned/MP/cooldown eligibility, with no donor 600ms gate. */
        public boolean specialSkillReady(int skill) { return ready.contains(Integer.valueOf(skill)); }
        public boolean specialChoose(int slot, boolean farm) {
            lastSlot = slot;
            lastFarm = farm;
            lastSkill = slot < 0 ? 0 : hotbar[slot];
            sends++;
            AutoAttackRuntime.specialSent();
            return true;
        }

        void configure(int[] skills, int... eligible) {
            hotbar = skills;
            ready.clear();
            for (int skill : eligible) ready.add(Integer.valueOf(skill));
        }
    }

    private static final class Fixture {
        final FakeLunaClock clock = new FakeLunaClock();
        final World world = new World();
        final AutoAttackController controller;
        Fixture() {
            AutoAttackRuntime.installForTest(world, new Settings(), clock);
            // The Auto đánh/Farm Special path under test runs with Tấn công quái thường OFF (default ON).
            AutoAttackRuntime.handleAction(AutoAttackController.NORMAL_MONSTERS);
            // Runtime and direct controller calls must share one state instance.
            controller = installedController();
            world.controller = controller;
        }
        void enableSpecial() { controller.handleAction(AutoAttackController.SPECIAL); }
        void at(long time) { clock.setMillis(time); }
    }

    private static AutoAttackController installedController() {
        try {
            java.lang.reflect.Field field = AutoAttackRuntime.class.getDeclaredField("controller");
            field.setAccessible(true);
            return (AutoAttackController)field.get(null);
        } catch (Exception failure) { throw new AssertionError(failure); }
    }

    private FarmSpecialAttackHostTest() { }

    public static void main(String[] args) {
        priorityAndFallback();
        farmAndAutoOwnership();
        autoAndFarmShareOneClock();
        blockedWinnerWaitsThenCasts();
        allSpecialSkillsShareEverySendTimestamp();
        ordinarySendWhileDisabledDoesNotRefreshClock();
        System.out.println("FarmSpecialAttackHostTest PASS (" + checks + " checks)");
    }

    private static void priorityAndFallback() {
        Fixture f = new Fixture();
        f.enableSpecial();
        f.world.skills.configure(new int[]{7, 20, 8, 19}, 7, 8, 19, 20);
        check(AutoAttackRuntime.chooseSpecial(f.world.skills, true), "Runtime delegates Farm selection when shared Special mode is ON");
        check(f.world.skills.lastSkill == 20 && f.world.skills.lastSlot == 1,
                "The highest ready skill ID wins regardless of hotbar order");
        check(f.world.skills.lastFarm, "Farm keeps its native chooser context");

        f.world.skills.configure(new int[]{7, 8}, 0);
        f.at(30); // the next Special send keeps the 30ms spacing
        check(f.controller.chooseSpecial(f.world.skills, true), "Native default skill is tried when no hotbar skill is eligible");
        check(f.world.skills.lastSkill == 0 && f.world.skills.lastSlot == -1,
                "Default skill 0 uses slot -1 only when no eligible hotbar candidate exists");

        f.at(631);
        f.world.skills.configure(new int[]{7, 8}, 7, 0);
        check(f.controller.chooseSpecial(f.world.skills, true), "An eligible hotbar candidate remains preferred over the default");
        check(f.world.skills.lastSkill == 7 && f.world.skills.lastSlot == 0,
                "Default skill is not selected when an eligible hotbar skill exists");
    }

    private static void farmAndAutoOwnership() {
        Fixture f = new Fixture();
        f.world.skills.configure(new int[]{6}, 6);
        check(!f.controller.chooseSpecial(f.world.skills, true), "Farm is blocked while shared Special mode is OFF");
        f.enableSpecial();
        check(!f.controller.chooseSpecial(f.world.skills, false),
                "Auto Attack mode requires specialActive during its own attack call");
        check(f.controller.chooseSpecial(f.world.skills, true), "Farm can send while Auto Attack master is OFF");

        f.controller.setEnabled(true);
        check(!f.controller.chooseSpecial(f.world.skills, true),
                "Farm special selection yields whenever the Auto Attack master is ON");
        check(!f.controller.chooseSpecial(f.world.skills, false),
                "Auto special selection is rejected outside the controller-owned attack call");
        f.at(30);
        f.controller.tick();
        check(f.world.attacks == 1 && f.world.chooseResult,
                "Auto special selection succeeds inside its normal attack callback");
        check(!f.world.skills.lastFarm, "Auto keeps the native Auto chooser context");

        f.controller.setEnabled(false);
        f.at(60);
        check(f.controller.chooseSpecial(f.world.skills, true), "Farm resumes after Auto Attack is turned OFF");
    }

    private static void blockedWinnerWaitsThenCasts() {
        Fixture f = new Fixture();
        f.enableSpecial();
        f.world.skills.configure(new int[]{7}, 7);
        check(f.controller.chooseSpecial(f.world.skills, true), "Initial special skill is sent");
        int sends = f.world.skills.sends;

        f.world.skills.configure(new int[]{6, 20}, 6, 20);
        f.at(600);
        check(!f.controller.chooseSpecial(f.world.skills, true), "A winning cooldown skill waits through exactly 600ms");
        check(f.world.skills.sends == sends, "A lower ready skill is not used as fallback while the winner is gated");
        check(f.world.skills.lastSkill == 7, "The native-ready highest ID is selected before the shared time gate");

        f.at(601);
        check(f.controller.chooseSpecial(f.world.skills, true), "The blocked winner casts immediately after the strict 600ms gate");
        check(f.world.skills.sends == sends + 1 && f.world.skills.lastSkill == 20 && f.world.skills.lastSlot == 1,
                "Post-cooldown selection keeps the original highest-priority winner");
    }

    private static void autoAndFarmShareOneClock() {
        Fixture f = new Fixture();
        f.enableSpecial();
        f.world.skills.configure(new int[]{7}, 7);
        f.controller.setEnabled(true);
        f.controller.tick();
        check(f.world.attacks == 1 && f.world.skills.sends == 1 && !f.world.skills.lastFarm,
                "Auto's special send records the shared timestamp while its attack callback is active");

        f.controller.setEnabled(false);
        f.at(600);
        check(!f.controller.chooseSpecial(f.world.skills, true) && f.world.skills.sends == 1,
                "Farm observes Auto's send and waits at exactly 600ms after Auto is turned OFF");
        f.at(601);
        check(f.controller.chooseSpecial(f.world.skills, true) && f.world.skills.sends == 2
                        && f.world.skills.lastFarm,
                "Farm uses the same timestamp and casts just after Auto's 600ms gate");
    }

    private static void allSpecialSkillsShareEverySendTimestamp() {
        Fixture f = new Fixture();
        f.enableSpecial();
        f.world.skills.configure(new int[]{6}, 6);
        f.at(1000);
        check(f.controller.chooseSpecial(f.world.skills, true), "A non-special native skill also sends in special mode");
        assertCooldownState(f, false, 1600, "Any successful special-mode send updates all four gates");
        assertCooldownState(f, true, 1601, "All four cooldown skills unlock after strictly more than 600ms");

        f.world.skills.configure(new int[]{19}, 19);
        f.at(1601);
        check(f.controller.chooseSpecial(f.world.skills, true), "A second special send refreshes the shared timestamp");
        assertCooldownState(f, false, 2201, "A later successful send refreshes every skill's gate");
        assertCooldownState(f, true, 2202, "All four IDs reopen together after the refreshed gate expires");
    }

    private static void ordinarySendWhileDisabledDoesNotRefreshClock() {
        Fixture f = new Fixture();
        f.enableSpecial();
        f.world.skills.configure(new int[]{6}, 6);
        f.at(10);
        check(f.controller.chooseSpecial(f.world.skills, true), "An enabled Farm special send establishes the clock");
        f.controller.handleAction(AutoAttackController.SPECIAL);
        f.at(300);
        AutoAttackRuntime.specialSent();
        f.controller.handleAction(AutoAttackController.SPECIAL);
        f.at(611);
        check(AutoAttackRuntime.specialSkillAllowed(7),
                "A send while Special mode is OFF does not refresh the shared cooldown clock");
    }

    private static void assertCooldownState(Fixture f, boolean expected, long time, String message) {
        f.at(time);
        int[] skills = new int[]{7, 8, 19, 20};
        for (int skill : skills)
            check(AutoAttackRuntime.specialSkillAllowed(skill) == expected,
                    message + " (skill " + skill + " at " + time + "ms)");
    }
}
