import luna.api.AutoAttackAccess;
import luna.api.AutoAttackSettings;
import luna.api.FarmSpotAccess;
import luna.api.SpecialAttackAccess;
import luna.core.FakeLunaClock;
import luna.features.AutoAttackController;
import luna.features.AutoAttackRuntime;
import luna.features.SkillCastStats;

/**
 * Auto đánh > Tấn công quái thường: ON by default (ModUpLv); with Tấn công đặc biệt the ModUpLv
 * attack loop sends, so Auto đánh and Farm leave the casting to it. Counters reset on Special ON.
 */
public final class NormalMonstersHostTest {
    private static int checks;

    private static void check(boolean value, String text) {
        checks++;
        if (!value) throw new AssertionError(text);
    }

    private static final class Settings implements AutoAttackSettings {
        int saves; boolean players;
        public void load(int[] values) { values[0] = 1; values[1] = 650; }
        public void save(boolean players, int delay) { saves++; this.players = players; }
    }

    private static final class Access implements AutoAttackAccess {
        int menus, stops, attacks; boolean ready;
        final Object monster = new Object(), player = new Object();
        public boolean ready() { return ready; }
        public Object player() { return player; }
        public int mapId() { return 1; }
        public int zoneId() { return 1; }
        public int entityCount() { return 1; }
        public Object entityAt(int index) { return monster; }
        public boolean validTarget(Object value, boolean players) { return value == monster; }
        public int distanceTo(Object value) { return 50; }
        public boolean combatBusy() { return false; }
        public boolean movementRequested() { return false; }
        public boolean attack(Object value) { attacks++; return true; }
        public void cancelAttack(Object value) { }
        public void stopFarm() { stops++; }
        public void showMenu(boolean enabled, boolean players, int delay) { menus++; }
        public void showDelayInput(int delay) { }
        public void notice(String text) { }
    }

    private static final class Skills implements SpecialAttackAccess {
        int chosen;
        public int specialSlotCount() { return 1; }
        public int specialSkillAt(int slot) { return 3; }
        public boolean specialSkillReady(int skill) { return true; }
        public boolean specialChoose(int slot, boolean farm) { chosen++; return true; }
    }

    private static final class Spot implements FarmSpotAccess {
        final Object monster = new Object();
        boolean empty; int holds, actives, attacks, selects;
        public int entityCount() { return empty ? 0 : 1; }
        public Object entityAt(int index) { return monster; }
        public int distanceTo(Object target) { return 40; }
        public boolean combatBusy() { return false; }
        public boolean farmSpotTargetValid(Object target) { return target == monster; }
        public boolean farmSpotGhost(Object target) { return false; }
        public void farmSpotHold() { holds++; }
        public void farmSpotSelect(Object target) { selects++; }
        public boolean farmSpotAttack(Object target) { attacks++; return true; }
        public void farmSpotActive() { actives++; }
        public void farmSpotCancelCast() { }
    }

    public static void main(String[] args) {
        Access access = new Access();
        Settings settings = new Settings();
        FakeLunaClock clock = new FakeLunaClock(1000L);
        AutoAttackRuntime.installForTest(access, settings, clock);
        check(AutoAttackController.NORMAL_MONSTERS == -117, "Menu action -117");
        check("Tấn công quái thường: [BẬT]".equals(AutoAttackRuntime.normalLabel()) && !AutoAttackRuntime.normalMonstersActive(),
                "Default ON like ModUpLv, idle until Tấn công đặc biệt");

        SkillCastStats.sent(5, 1000L);
        AutoAttackRuntime.handleAction(AutoAttackController.SPECIAL);
        check(AutoAttackRuntime.specialEnabled() && AutoAttackRuntime.normalMonstersActive() && !AutoAttackRuntime.enabled() && access.stops == 0,
                "Tấn công đặc biệt alone starts the ModUpLv loop; neither Auto đánh nor Farm is touched");
        check(SkillCastStats.casts(5) == 0, "Special ON resets the cast counters");

        int menus = access.menus;
        check(AutoAttackRuntime.handleAction(AutoAttackController.NORMAL_MONSTERS), "Handled");
        check("Tấn công quái thường: [TẮT]".equals(AutoAttackRuntime.normalLabel()) && AutoAttackRuntime.specialEnabled()
                && !AutoAttackRuntime.normalMonstersActive() && access.menus == menus + 1,
                "OFF keeps Special (Auto đánh/Farm send it again) and refreshes the menu");
        AutoAttackRuntime.handleAction(AutoAttackController.SPECIAL);
        SkillCastStats.sent(5, 1000L);
        AutoAttackRuntime.handleAction(AutoAttackController.NORMAL_MONSTERS);
        check(AutoAttackRuntime.specialEnabled() && AutoAttackRuntime.normalMonstersActive() && SkillCastStats.casts(5) == 0,
                "ON with Special OFF turns Special ON (its target mode) and resets the counters");
        AutoAttackRuntime.handleAction(AutoAttackController.SPECIAL);
        check(!AutoAttackRuntime.specialEnabled() && !AutoAttackRuntime.normalMonstersActive()
                && "Tấn công quái thường: [BẬT]".equals(AutoAttackRuntime.normalLabel()), "Special OFF suspends the loop; the option stays");
        AutoAttackRuntime.handleAction(AutoAttackController.SPECIAL);
        check(AutoAttackRuntime.normalMonstersActive(), "Special ON again resumes it");

        // Auto đánh stands by while the loop sends.
        access.ready = true;
        AutoAttackRuntime.handleAction(AutoAttackController.MONSTERS_AND_PLAYERS);
        clock.advance(2000L);
        AutoAttackRuntime.tick();
        AutoAttackRuntime.tick();
        check(AutoAttackRuntime.enabled() && access.attacks == 0 && AutoAttackRuntime.target() == null, "Auto đánh ON: no target, no cast of its own");
        Skills skills = new Skills();
        check(!AutoAttackRuntime.chooseSpecial(skills, false) && skills.chosen == 0, "No Auto đánh Special send beside the loop");
        AutoAttackRuntime.handleAction(AutoAttackController.NORMAL_MONSTERS);
        clock.advance(2000L);
        AutoAttackRuntime.tick();
        check(access.attacks == 1 && AutoAttackRuntime.target() == access.monster, "Normal monsters OFF: Auto đánh attacks again");
        AutoAttackRuntime.handleAction(AutoAttackController.NORMAL_MONSTERS);
        AutoAttackRuntime.handleAction(AutoAttackController.TOGGLE);

        // Farm at its spot: hold still, the loop's sends are the progress.
        check(!AutoAttackRuntime.enabled(), "Auto đánh OFF for Farm");
        check(!AutoAttackRuntime.chooseSpecial(skills, true) && skills.chosen == 0, "No Farm Special send beside the loop");
        Spot spot = new Spot();
        check(AutoAttackRuntime.farmSpot(spot) && spot.holds == 1 && spot.attacks == 0 && spot.selects == 0 && spot.actives == 1,
                "Farm spot: hold, no attack of its own; a fresh stay counts as progress");
        int alive = spot.actives;
        for (int i = 0; i < 29; i++) { clock.advance(500); AutoAttackRuntime.farmSpot(spot); }
        check(spot.actives == alive + 29 && spot.attacks == 0, "Monsters left and no send yet: Farm is kept alive inside the 15s stuck window");
        clock.advance(500);
        AutoAttackRuntime.farmSpot(spot);
        check(spot.actives == alive + 29, "15s without a send while monsters remain: Farm's own stuck handling may act");
        AutoAttackRuntime.specialSent();
        clock.advance(100);
        AutoAttackRuntime.farmSpot(spot);
        check(spot.actives == alive + 30, "A loop send is Farm progress");
        clock.advance(AutoAttackController.FARM_STUCK_MS + 5);
        spot.empty = true;
        AutoAttackRuntime.farmSpot(spot);
        check(spot.actives == alive + 31, "An empty spot is waiting, not stuck");

        AutoAttackRuntime.handleAction(AutoAttackController.SPECIAL);
        check(AutoAttackRuntime.playersEnabled(), "Mode 6 (monsters + PK) kept");
        int saves = settings.saves, stops = access.stops;
        menus = access.menus;
        SkillCastStats.sent(5, 1000L);
        AutoAttackRuntime.engageMonsters();
        check(AutoAttackRuntime.enabled() && !AutoAttackRuntime.playersEnabled() && AutoAttackRuntime.specialEnabled()
                && AutoAttackRuntime.normalMonstersActive() && SkillCastStats.casts(5) == 0,
                "Dungeon engage: Auto đánh on monsters only with Special + normal monsters; counters restart");
        check(settings.saves == saves + 1 && !settings.players && access.stops == stops + 1 && access.menus == menus,
                "Mode 7 is saved, Farm is stopped like any Auto đánh start, no menu pops up");
        saves = settings.saves; stops = access.stops;
        SkillCastStats.sent(5, 1000L);
        AutoAttackRuntime.engageMonsters();
        check(settings.saves == saves && access.stops == stops && AutoAttackRuntime.enabled() && SkillCastStats.casts(5) == 1,
                "Repeated engage changes nothing (counters kept)");
        System.out.println("NormalMonstersHostTest PASS checks=" + checks);
    }
}
