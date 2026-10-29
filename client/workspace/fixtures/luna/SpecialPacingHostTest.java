import luna.api.AutoAttackAccess;
import luna.api.AutoAttackSettings;
import luna.api.SpecialAttackAccess;
import luna.core.FakeLunaClock;
import luna.features.AutoAttackController;
import luna.features.AutoAttackRuntime;

/** Special sends keep a 30ms real-time spacing (ModUpLv: >=28ms frames) whatever the update rate. */
public final class SpecialPacingHostTest {
    private static int checks;
    private static void check(boolean value, String text) { checks++; if (!value) throw new AssertionError(text); }

    private static final class Settings implements AutoAttackSettings {
        public void load(int[] values) { values[0] = 0; values[1] = 650; }
        public void save(boolean players, int delay) { }
    }

    private static final class World implements AutoAttackAccess {
        public boolean ready() { return true; }
        public Object player() { return this; }
        public int mapId() { return 1; }
        public int zoneId() { return 1; }
        public int entityCount() { return 0; }
        public Object entityAt(int index) { return null; }
        public boolean validTarget(Object value, boolean players) { return false; }
        public int distanceTo(Object value) { return -1; }
        public boolean combatBusy() { return false; }
        public boolean movementRequested() { return false; }
        public boolean attack(Object value) { return false; }
        public void cancelAttack(Object value) { }
        public void stopFarm() { }
        public void showMenu(boolean enabled, boolean players, int delay) { }
        public void showDelayInput(int delay) { }
        public void notice(String text) { }
    }

    /** Three non-gated skills are always ready, so only the spacing limits the sends. */
    private static final class Skills implements SpecialAttackAccess {
        final int[] hotbar = {3, 5, 9};
        int sends;
        public int specialSlotCount() { return hotbar.length; }
        public int specialSkillAt(int slot) { return hotbar[slot]; }
        public boolean specialSkillReady(int skill) { return true; }
        public boolean specialChoose(int slot, boolean farm) { sends++; AutoAttackRuntime.specialSent(); return true; }
    }

    public static void main(String[] args) {
        FakeLunaClock clock = new FakeLunaClock(1000L);
        World world = new World();
        AutoAttackRuntime.installForTest(world, new Settings(), clock);
        // The Auto đánh/Farm Special path under test runs with Tấn công quái thường OFF (default ON).
        AutoAttackRuntime.handleAction(AutoAttackController.NORMAL_MONSTERS);
        Skills skills = new Skills();
        check(AutoAttackController.SPECIAL_MIN_GAP_MS == 30, "Gap is 30ms, just above ModUpLv's 28ms frame floor");
        AutoAttackRuntime.handleAction(AutoAttackController.SPECIAL);
        check(AutoAttackRuntime.chooseSpecial(skills, true) && skills.sends == 1, "First send is immediate");
        check(!AutoAttackRuntime.chooseSpecial(skills, true) && skills.sends == 1,
                "A catch-up update in the same millisecond cannot send again");
        clock.advance(29L);
        check(!AutoAttackRuntime.chooseSpecial(skills, true) && skills.sends == 1, "29ms is still too close");
        clock.advance(1L);
        check(AutoAttackRuntime.chooseSpecial(skills, true) && skills.sends == 2, "30ms allows the next send");
        // x3 tempo: an update every ~6.7ms for one real second sends at most once per 30ms.
        int start = skills.sends;
        for (int i = 0; i < 150; i++) { clock.advance(i % 3 == 0 ? 6L : 7L); AutoAttackRuntime.chooseSpecial(skills, true); }
        int sent = skills.sends - start;
        check(sent >= 25 && sent <= 36, "x3 update rate is limited to ModUpLv's cadence; sent=" + sent);
        System.out.println("SpecialPacingHostTest PASS checks=" + checks);
    }
}
