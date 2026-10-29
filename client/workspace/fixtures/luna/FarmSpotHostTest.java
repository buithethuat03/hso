import luna.api.AutoAttackAccess;
import luna.api.AutoAttackSettings;
import luna.api.FarmSpotAccess;
import luna.api.SpecialAttackAccess;
import luna.core.FakeLunaClock;
import luna.features.AutoAttackController;
import luna.features.AutoAttackRuntime;
import java.util.ArrayList;
import java.util.List;

/** Host-side policy checks for Farm + Special standing at its saved spot. */
public final class FarmSpotHostTest {
    private static int checks;

    private static void check(boolean value, String text) {
        checks++;
        if (!value) throw new AssertionError(text);
    }

    private static final class Settings implements AutoAttackSettings {
        public void load(int[] values) { values[0] = 0; values[1] = 650; }
        public void save(boolean players, int delay) { }
    }

    private static final class Mob {
        final int distance; boolean alive = true, allowed = true, ghost, reachable = true;
        Mob(int distance) { this.distance = distance; }
    }

    /** Native view; farmSpotAttack runs the real Special chooser like the bridge does. */
    private static final class World implements AutoAttackAccess, FarmSpotAccess, SpecialAttackAccess {
        final List<Mob> mobs = new ArrayList<Mob>();
        boolean busy, skillReady = true;
        int holds, actives, attacks, sends, cancels;
        Object selected, lastAttack;

        public boolean ready() { return true; }
        public Object player() { return this; }
        public int mapId() { return 1; }
        public int zoneId() { return 1; }
        public int entityCount() { return mobs.size(); }
        public Object entityAt(int index) { return mobs.get(index); }
        public boolean validTarget(Object value, boolean players) { return false; }
        public int distanceTo(Object value) { return ((Mob)value).distance; }
        public boolean combatBusy() { return busy; }
        public boolean movementRequested() { return false; }
        public boolean attack(Object value) { return false; }
        public void cancelAttack(Object value) { }
        public void stopFarm() { }
        public void showMenu(boolean enabled, boolean players, int delay) { }
        public void showDelayInput(int delay) { }
        public void notice(String text) { }
        public boolean farmSpotTargetValid(Object value) { Mob mob = (Mob)value; return mob.alive && mob.allowed; }
        public boolean farmSpotGhost(Object value) { return ((Mob)value).ghost; }
        public void farmSpotHold() { holds++; }
        public void farmSpotSelect(Object value) { selected = value; }
        public boolean farmSpotAttack(Object value) {
            attacks++; selected = lastAttack = value;
            return AutoAttackRuntime.chooseSpecial(this, true);
        }
        public void farmSpotActive() { actives++; }
        public void farmSpotCancelCast() { cancels++; busy = false; }
        // One configured non-gated skill; out-of-range targets are refused natively.
        public int specialSlotCount() { return 1; }
        public int specialSkillAt(int slot) { return 3; }
        public boolean specialSkillReady(int skill) { return skill == 3 && skillReady; }
        public boolean specialChoose(int slot, boolean farm) {
            if (!((Mob)selected).reachable) return false;
            sends++; AutoAttackRuntime.specialSent(); return true;
        }
    }

    public static void main(String[] args) {
        World world = new World();
        FakeLunaClock clock = new FakeLunaClock(10000L);
        AutoAttackRuntime.installForTest(world, new Settings(), clock);
        // The Auto đánh/Farm Special path under test runs with Tấn công quái thường OFF (default ON).
        AutoAttackRuntime.handleAction(AutoAttackController.NORMAL_MONSTERS);
        Mob far = new Mob(300), near = new Mob(40), blocked = new Mob(5);
        blocked.allowed = false;
        world.mobs.add(far); world.mobs.add(near); world.mobs.add(blocked);

        check(!AutoAttackRuntime.farmSpot(world) && world.holds == 0 && world.attacks == 0,
                "Special OFF leaves the native Farm tick and legacy auto untouched");
        AutoAttackRuntime.handleAction(AutoAttackController.SPECIAL);
        check(AutoAttackRuntime.farmSpot(world), "Special ON takes over Farm at its saved spot");
        check(world.holds == 1, "The legacy walking auto is held every owned tick");
        check(world.lastAttack == near && world.sends == 1,
                "Attacks the nearest monster accepted by Farm's selection, not a filtered closer one");
        check(world.actives == 1, "A sent attack resets Farm's no-attack village-scroll counter");

        // Waiting (28ms spacing, cooldown or no MP) is not stuck.
        int actives = world.actives;
        AutoAttackRuntime.farmSpot(world);
        world.skillReady = false; clock.advance(100L);
        AutoAttackRuntime.farmSpot(world);
        check(world.sends == 1 && world.actives == actives + 2,
                "Waiting on spacing/cooldown/MP keeps resetting the counter");
        clock.advance(30000L);
        AutoAttackRuntime.farmSpot(world);
        check(world.actives == actives + 3, "Even 30s without MP is waiting, not a reason for the village scroll");
        world.skillReady = true;

        // Refused (out of range): try another monster after three refusals.
        near.reachable = false; clock.advance(100L);
        for (int i = 0; i < 3; i++) { clock.advance(50L); AutoAttackRuntime.farmSpot(world); }
        check(world.lastAttack == near, "Three refusals stay on the same target");
        clock.advance(50L); AutoAttackRuntime.farmSpot(world);
        check(world.lastAttack == far && world.sends == 2, "Then the refused monster is skipped and another one is hit");
        near.reachable = true;

        // Truly stuck: everything refused for FARM_STUCK_MS of real time.
        far.reachable = false; near.reachable = false;
        long start = clock.nowMillis();
        int lastActive = world.actives; long lastActiveAt = start;
        while (clock.nowMillis() - start < 20000L) {
            clock.advance(50L); AutoAttackRuntime.farmSpot(world);
            if (world.actives != lastActive) { lastActive = world.actives; lastActiveAt = clock.nowMillis(); }
        }
        check(lastActiveAt - start >= AutoAttackController.FARM_STUCK_MS - 100
                        && lastActiveAt - start < AutoAttackController.FARM_STUCK_MS + 100,
                "Only after ~15s of refusals does Farm get its native village-scroll counter back; at=" + (lastActiveAt - start));
        far.reachable = true; near.reachable = true;
        clock.advance(50L); AutoAttackRuntime.farmSpot(world);
        check(world.actives == lastActive + 1, "A successful send restores the keep-alive");

        // Re-entering the spot after a trip starts a fresh stuck window.
        far.reachable = false; near.reachable = false;
        clock.advance(60000L); actives = world.actives;
        AutoAttackRuntime.farmSpot(world);
        check(world.actives == actives + 1, "A refusal right after returning to the spot is still within a fresh window");
        far.reachable = true; near.reachable = true;

        // A stuck local cast (manual skill walking into range) is cancelled after 1.5s.
        world.busy = true; int cancels = world.cancels; int attacks = world.attacks;
        clock.advance(50L); AutoAttackRuntime.farmSpot(world);
        clock.advance(1000L); AutoAttackRuntime.farmSpot(world);
        check(world.cancels == cancels && world.attacks == attacks, "A short native cast is not overridden");
        clock.advance(600L); AutoAttackRuntime.farmSpot(world);
        check(world.cancels == cancels + 1 && !world.busy, "A cast busy for 1.5s is cancelled");

        Mob ghost = new Mob(900); ghost.ghost = true;
        world.mobs.add(ghost);
        attacks = world.attacks;
        clock.advance(50L); AutoAttackRuntime.farmSpot(world);
        check(world.selected == ghost && world.attacks == attacks,
                "A ghost is only selected so Farm's native captcha branch handles it");

        world.mobs.clear();
        actives = world.actives;
        clock.advance(50L);
        check(AutoAttackRuntime.farmSpot(world) && world.actives == actives + 1,
                "An empty spot waits in place instead of counting toward the village scroll");

        world.mobs.add(near);
        AutoAttackRuntime.handleAction(AutoAttackController.SPECIAL);
        int holds = world.holds;
        check(!AutoAttackRuntime.farmSpot(world) && world.holds == holds,
                "Special OFF hands the spot back to the native Farm tick");
        System.out.println("FarmSpotHostTest PASS checks=" + checks);
    }
}
