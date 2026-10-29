import luna.api.AutoAttackAccess;
import luna.api.AutoAttackSettings;
import luna.core.FakeLunaClock;
import luna.features.AutoAttackController;
import luna.features.AutoAttackRuntime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Deterministic policy tests; binary tests separately exercise native adapters. */
public final class AutoAttackHostTest {
    private static int checks;
    private static void check(boolean value, String text) { checks++; if (!value) throw new AssertionError(text); }
    private static final class Entity {
        int type, distance, hp = 10;
        boolean hostile = true, excluded;
        Entity(int type, int distance) { this.type = type; this.distance = distance; }
    }
    private static final class Store implements AutoAttackSettings {
        int mode, delay = 650, saves;
        boolean fail;
        public void load(int[] settings) { settings[0] = mode; settings[1] = delay; }
        public void save(boolean players, int value) {
            if (fail) throw new IllegalStateException();
            mode = players ? 1 : 0; delay = value; saves++;
        }
    }
    private static final class Access implements AutoAttackAccess {
        Object me = new Object();
        Entity[] entities = new Entity[0];
        int map = 1, zone = 1, sends, cancels, farmStops, menus, inputs, notices;
        int farmSetting = 973, currentTag;
        boolean ready = true, busy, farm = true, castWorks = true, moving;
        boolean failReadyOnce;
        CountDownLatch enteredAttack, releaseAttack;
        Object sent;
        AutoAttackController controller;
        public boolean ready() {
            if (failReadyOnce) { failReadyOnce = false; throw new IllegalStateException("world transition"); }
            return ready;
        }
        public Object player() { return me; }
        public int mapId() { return map; }
        public int zoneId() { return zone; }
        public int entityCount() { return entities.length; }
        public Object entityAt(int index) { return entities[index]; }
        public boolean validTarget(Object target, boolean players) {
            if (!(target instanceof Entity) || target == me) return false;
            Entity e = (Entity) target;
            return !e.excluded && e.hp > 0 && (e.type == 1 || players && e.type == 0 && e.hostile);
        }
        public int distanceTo(Object target) { return ((Entity) target).distance; }
        public boolean combatBusy() { return busy; }
        public boolean movementRequested() { return moving; }
        public boolean attack(Object target) {
            if (enteredAttack != null) {
                enteredAttack.countDown();
                try { if (!releaseAttack.await(5, TimeUnit.SECONDS)) throw new AssertionError("attack release timeout"); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
            }
            currentTag = controller.captureGeneration();
            if (!castWorks) return false;
            sends++; sent = target; return true;
        }
        public void cancelAttack(Object target) { cancels++; }
        public void stopFarm() {
            check(!controller.enabled(), "Farm cleanup precedes ownership");
            farmStops++; farm = false;
        }
        public void showMenu(boolean enabled, boolean players, int delay) { menus++; }
        public void showDelayInput(int delay) { inputs++; }
        public void notice(String text) { notices++; }
    }
    private static final class Fixture {
        final FakeLunaClock clock = new FakeLunaClock();
        final Access access = new Access();
        final Store store = new Store();
        final AutoAttackController controller;
        Fixture() { controller = new AutoAttackController(access, store, clock); access.controller = controller; }
        void advance(long amount) { clock.advance(amount); controller.tick(); }
    }
    public static void main(String[] args) {
        defaultsAndInput(); modesAndHandoff(); targeting(); lifecycle(); movementPriority(); movementResumeBoundaries(); movementWorldTransitions(); boundaries(); persistenceAndFailures(); transientNativeFailure(); concurrentHandoff();
        System.out.println("AutoAttackHostTest PASS (" + checks + " checks)");
    }
    private static void defaultsAndInput() {
        Fixture f = new Fixture();
        check(!f.controller.enabled() && !f.controller.playersEnabled() && f.controller.delayMs() == 650, "defaults");
        check(f.controller.statusText() == null, "OFF has no HUD");
        check(!f.controller.handleAction(88), "unrelated action untouched");
        f.controller.handleAction(AutoAttackController.MENU);
        f.controller.handleAction(AutoAttackController.DELAY);
        check(f.access.menus == 1 && f.access.inputs == 1 && f.access.farm, "menu/input does not activate combat");
        String[] invalid = new String[] {null, "", " ", "-1", "0", "1001", "1.2", "+1", "ms", "99999999999999999999"};
        for (int i = 0; i < invalid.length; i++) {
            check(!f.controller.submitDelay(invalid[i]), "invalid input rejected");
            check(f.controller.delayMs() == 650 && f.store.saves == 0 && f.access.farm, "invalid input preserves state");
        }
        String[] valid = new String[] {"1", "650", "1000", " 001 "};
        int[] values = new int[] {1, 650, 1000, 1};
        for (int i = 0; i < valid.length; i++) {
            check(f.controller.submitDelay(valid[i]) && f.controller.delayMs() == values[i], "valid input accepted");
            check(!f.controller.enabled() && f.access.farm && f.access.farmSetting == 973, "delay edit retains ownership and other Farm settings");
        }
    }
    private static void modesAndHandoff() {
        Fixture f = new Fixture();
        f.controller.handleAction(AutoAttackController.MONSTERS_AND_PLAYERS);
        check(f.controller.enabled() && f.controller.playersEnabled() && !f.access.farm, "PK selection enables and stops Farm");
        check(f.store.mode == 1 && f.access.farmSetting == 973, "mode saved independently");
        check("Auto Đánh: Quái & PK [6]".equals(f.controller.statusText()), "PK HUD");
        int epoch = f.controller.generation();
        f.controller.handleAction(AutoAttackController.MONSTERS);
        check(f.controller.enabled() && !f.controller.playersEnabled() && f.controller.generation() != epoch, "mode change invalidates cast");
        check(f.access.farmStops == 1, "changing enabled mode does not run Farm cleanup");
        check("Auto Đánh: Chỉ Quái [7]".equals(f.controller.statusText()), "monster HUD");
        f.controller.setEnabled(false);
        check(!f.controller.enabled() && !f.access.farm, "OFF does not resume Farm");
        f.access.farm = true;
        epoch = f.controller.generation();
        check(!f.controller.allowRequest(epoch, new Entity(1, 1)), "disabled old requests rejected");
        check(f.controller.allowRequest(0, null), "native/manual request context unchanged");
    }
    private static void targeting() {
        Fixture f = new Fixture();
        Entity npc = new Entity(2, 0), player = new Entity(0, 1), far = new Entity(1, 900), first = new Entity(1, 30), tie = new Entity(1, 30);
        Entity dead = new Entity(1, 1); dead.hp = 0;
        Entity excluded = new Entity(1, 0); excluded.excluded = true;
        f.access.entities = new Entity[] {null, npc, player, dead, excluded, far, first, tie};
        f.controller.setEnabled(true); f.advance(651);
        check(f.access.sent == first, "nearest monster, first distance tie, exclusions");
        check(f.controller.allowRequest(f.access.currentTag, first), "current tagged request valid");
        Entity closer = new Entity(1, 1);
        f.access.entities = new Entity[] {first, closer}; f.advance(651);
        check(f.access.sent == first, "valid target retained");
        int old = f.access.currentTag;
        first.hp = 0; f.advance(651); f.advance(651);
        check(f.access.sent == closer && !f.controller.allowRequest(old, first), "dead target and old request invalidated");
        f.controller.handleAction(AutoAttackController.MONSTERS_AND_PLAYERS);
        player.hostile = false; f.access.entities = new Entity[] {player, closer}; f.advance(651);
        check(f.access.sent == closer, "friendly player excluded");
        f.access.entities = new Entity[] {player}; player.hostile = true; f.advance(651); f.advance(651);
        check(f.access.sent == player, "hostile PK selected");
        old = f.access.currentTag; f.controller.handleAction(AutoAttackController.MONSTERS);
        check(!f.controller.allowRequest(old, player), "PK to monsters rejects pending player request");
        Entity outside = new Entity(1, 2000001), boundary = new Entity(1, 2000000);
        f.access.entities = new Entity[] {outside, boundary}; f.advance(651);
        check(f.access.sent == boundary, "loaded-map range boundary");
        old = f.access.currentTag; f.access.entities = new Entity[] {new Entity(1, 2000000)};
        check(!f.controller.allowRequest(old, boundary), "replacement object cannot inherit a request");
    }
    private static void lifecycle() {
        Fixture f = new Fixture(); Entity e = new Entity(1, 1); f.access.entities = new Entity[] {e};
        f.controller.setEnabled(true); f.advance(651); int old = f.access.currentTag;
        f.access.ready = false; f.controller.tick();
        check(f.controller.enabled() && f.controller.target() == null && !f.controller.allowRequest(old, e), "disconnect pauses intent and clears target");
        int count = f.access.sends; f.advance(100000); check(f.access.sends == count, "not-ready does not send");
        f.access.ready = true; f.controller.tick(); f.advance(650); check(f.access.sends == count, "reentry resets minimum wait");
        f.advance(1); check(f.access.sends == count + 1, "reentry resumes safely");
        old = f.access.currentTag; f.access.zone++; f.controller.tick();
        check(!f.controller.allowRequest(old, e), "zone transition invalidates requests");
        f.advance(651); old = f.access.currentTag; f.access.me = new Object();
        check(!f.controller.allowRequest(old, e), "character replacement invalidates immediately");
        f.controller.tick(); f.advance(651); check(f.controller.target() == e, "new character selects fresh target");
        old = f.access.currentTag; e.distance = 2000001;
        check(!f.controller.allowRequest(old, e), "target leaving range rejected before next tick");
        e.distance = 1;
        f.access.busy = true; count = f.access.sends; f.advance(1000); check(f.access.sends == count, "busy animation does not cast twice");
    }
    private static void movementPriority() {
        Fixture f = new Fixture(); Entity target = new Entity(1, 1); f.access.entities = new Entity[] {target};
        f.controller.setEnabled(true); f.advance(651); int old = f.access.currentTag;
        f.access.busy = true; f.access.moving = true;
        check(!f.controller.allowRequest(old, target), "new movement rejects the old request before a game tick");
        int cancels = f.access.cancels; f.controller.yieldToMovement();
        check(f.controller.enabled() && f.controller.target() == null && f.access.cancels == cancels + 1,
                "pre-update movement yields a busy cast while retaining intent");
        int epoch = f.controller.generation(), attacks = f.access.sends;
        f.advance(5000); f.controller.yieldToMovement();
        check(f.access.sends == attacks && f.controller.generation() == epoch,
                "continuous movement cannot recast or repeatedly cancel native walking");
        f.access.moving = false; f.access.busy = false; f.controller.tick();
        f.advance(99); check(f.access.sends == attacks, "movement stop waits 99ms");
        f.advance(1); check(f.access.sends == attacks + 1, "idle character resumes auto attack at 100ms");
        old = f.access.currentTag; f.controller.beginMovement();
        check(f.controller.enabled() && !f.controller.allowRequest(old, target),
                "accepted touch movement cancels immediately before a path exists");
        f.controller.tick(); f.advance(100);
        check(f.access.sends == attacks + 2, "a blocked or finished touch path still resumes after 100ms");
        f.controller.setEnabled(false); epoch = f.controller.generation();
        f.access.moving = true; f.controller.yieldToMovement(); f.controller.beginMovement();
        check(f.controller.generation() == epoch && !f.controller.enabled(), "OFF movement leaves native combat alone");
    }
    private static void movementResumeBoundaries() {
        int[] delays = new int[] {1, 100, 650, 1000};
        for (int i = 0; i < delays.length; i++) {
            Fixture f = new Fixture(); Entity e = new Entity(1, 1);
            f.access.entities = new Entity[] {e};
            f.controller.submitDelay(Integer.toString(delays[i])); f.controller.setEnabled(true);
            f.advance(delays[i] + 1); int old = f.access.currentTag, count = f.access.sends;
            f.access.moving = true; f.controller.yieldToMovement(); f.advance(3000);
            f.access.moving = false; f.controller.tick();
            f.advance(99); check(f.access.sends == count, "resume d=" + delays[i] + " waits99");
            f.access.castWorks = false; f.advance(1);
            check(f.access.sends == count, "failed first resumed cast does not count");
            f.access.castWorks = true; f.controller.tick();
            check(f.access.sends == count + 1 && f.controller.requestMinimum(f.access.currentTag) == 100,
                    "first successful resumed cast and request use100 for d=" + delays[i]);
            check(!f.controller.allowRequest(old, e), "old generation remains invalid after resume");
            f.advance(delays[i]); check(f.access.sends == count + 1, "following cast waits configured delay");
            f.advance(1); check(f.access.sends == count + 2
                    && f.controller.requestMinimum(f.access.currentTag) == delays[i], "following request restores configured delay");
            f.controller.beginMovement(); f.controller.tick(); f.advance(99);
            f.controller.beginMovement(); f.controller.tick(); f.advance(99);
            check(f.access.sends == count + 2, "new movement resets the100ms resume window");
            f.advance(1); check(f.access.sends == count + 3, "second movement episode resumes at100ms");
            f.controller.beginMovement(); f.access.ready = false; f.controller.tick();
            f.access.ready = true; f.controller.tick();
            f.advance(delays[i]); check(f.access.sends == count + 3, "world invalidation discards resume exception");
            f.advance(1); check(f.access.sends == count + 4, "world reentry uses ordinary configured cadence");
        }
    }
    private static void movementWorldTransitions() {
        for (int change = 0; change < 3; change++) {
            Fixture f = new Fixture(); Entity e = new Entity(1, 1);
            f.access.entities = new Entity[] {e}; f.controller.submitDelay("1000");
            f.controller.setEnabled(true); f.advance(1001);
            int old = f.access.currentTag, count = f.access.sends;
            f.access.moving = true; f.controller.yieldToMovement();
            if (change == 0) f.access.map++;
            else if (change == 1) f.access.zone++;
            else f.access.me = new Object();
            f.advance(5000); f.advance(5000);
            check(f.access.sends == count && !f.controller.allowRequest(old, e), "ready world transition during movement invalidates old work");
            f.access.moving = false; f.controller.tick();
            f.advance(100); check(f.access.sends == count, "new world does not receive the100ms movement exception");
            f.advance(900); check(f.access.sends == count, "world transition preserves configured wait");
            f.advance(1); check(f.access.sends == count + 1 && f.controller.requestMinimum(f.access.currentTag) == 1000,
                    "world transition resumes with the ordinary configured cast floor");
        }
    }
    private static void boundaries() {
        int[] delays = new int[] {1, 650, 1000};
        for (int i = 0; i < delays.length; i++) {
            Fixture f = new Fixture(); f.access.entities = new Entity[] {new Entity(1, 1)};
            f.controller.submitDelay(Integer.toString(delays[i])); f.controller.setEnabled(true);
            f.advance(delays[i] - 1); check(f.access.sends == 0, "d-1 blocked");
            f.advance(1); check(f.access.sends == 0, "strict d blocked");
            f.advance(1); check(f.access.sends == 1 && f.access.currentTag > 0, "d+1 cast tagged");
            check(f.controller.captureGeneration() == 0, "manual context not tagged after attack returns");
            f.advance(delays[i]); check(f.access.sends == 1, "next d blocked"); f.advance(1); check(f.access.sends == 2, "next d+1");
        }
        Fixture f = new Fixture(); f.access.entities = new Entity[] {new Entity(1, 1)}; f.controller.setEnabled(true);
        f.access.castWorks = false; f.advance(651); check(f.access.sends == 0, "unavailable skill sends nothing");
        f.access.castWorks = true; f.controller.tick(); check(f.access.sends == 1, "skill ready can retry without phantom cast timestamp");
    }
    private static void persistenceAndFailures() {
        Access access = new Access(); Store store = new Store(); FakeLunaClock clock = new FakeLunaClock();
        store.mode = 1; store.delay = 1000;
        AutoAttackController controller = new AutoAttackController(access, store, clock); access.controller = controller;
        check(!controller.enabled() && controller.playersEnabled() && controller.delayMs() == 1000, "saved mode/delay reload but OFF");
        store.mode = 2; store.delay = 0; controller = new AutoAttackController(access, store, clock); access.controller = controller;
        check(!controller.playersEnabled() && controller.delayMs() == 650, "corrupt settings defaults");
        store.fail = true; check(controller.submitDelay("1") && controller.delayMs() == 1 && access.notices == 1, "save failure retains session setting and reports");
    }
    private static void transientNativeFailure() {
        Access access = new Access(); Store store = new Store(); FakeLunaClock clock = new FakeLunaClock();
        AutoAttackRuntime.installForTest(access, store, clock);
        try {
            java.lang.reflect.Field member = AutoAttackRuntime.class.getDeclaredField("controller");
            member.setAccessible(true); access.controller = (AutoAttackController) member.get(null);
        } catch (Exception failure) { throw new AssertionError(failure); }
        Entity entity = new Entity(1, 1); access.entities = new Entity[] {entity};
        access.controller.setEnabled(true); clock.advance(651); AutoAttackRuntime.tick();
        int old = access.currentTag; check(access.sends == 1 && old > 0, "runtime starts before transient failure");
        access.failReadyOnce = true; AutoAttackRuntime.tick();
        check(AutoAttackRuntime.enabled() && AutoAttackRuntime.target() == null
                && !AutoAttackRuntime.allowRequest(old, entity), "native tick failure pauses intent and invalidates cast");
        AutoAttackRuntime.tick(); clock.advance(650); AutoAttackRuntime.tick();
        check(access.sends == 1, "native failure reentry waits full delay");
        clock.advance(1); AutoAttackRuntime.tick();
        check(access.sends == 2, "native failure reentry resumes");
        old = access.currentTag; access.failReadyOnce = true;
        check(!AutoAttackRuntime.allowRequest(old, entity) && AutoAttackRuntime.enabled()
                && AutoAttackRuntime.target() == null, "request world failure cancels old work and retains intent");
        AutoAttackRuntime.beforeFarmEnable();
        check(!AutoAttackRuntime.enabled(), "explicit Farm handoff still stops paused intent");
    }
    private static void concurrentHandoff() {
        final Fixture f = new Fixture();
        final Entity entity = new Entity(1, 1);
        f.access.entities = new Entity[] {entity};
        f.controller.setEnabled(true); f.clock.advance(651);
        f.access.enteredAttack = new CountDownLatch(1); f.access.releaseAttack = new CountDownLatch(1);
        final Throwable[] errors = new Throwable[2];
        Thread game = new Thread(new Runnable() { public void run() { try { f.controller.tick(); } catch (Throwable failure) { errors[0] = failure; } } });
        final CountDownLatch switching = new CountDownLatch(1);
        Thread ui = new Thread(new Runnable() { public void run() { switching.countDown(); try { f.controller.setEnabled(false); } catch (Throwable failure) { errors[1] = failure; } } });
        try {
            game.start(); check(f.access.enteredAttack.await(5, TimeUnit.SECONDS), "cast entered on game thread");
            ui.start(); check(switching.await(5, TimeUnit.SECONDS), "handoff requested on UI thread");
            long deadline = System.currentTimeMillis() + 5000;
            while (ui.getState() != Thread.State.BLOCKED && ui.isAlive() && System.currentTimeMillis() < deadline) Thread.yield();
            check(ui.getState() == Thread.State.BLOCKED, "handoff waits for cast tagging transaction");
            f.access.releaseAttack.countDown(); game.join(5000); ui.join(5000);
            check(!game.isAlive() && !ui.isAlive() && errors[0] == null && errors[1] == null, "cross-thread handoff completes");
            check(f.access.currentTag > 0 && !f.controller.enabled()
                    && !f.controller.allowRequest(f.access.currentTag, entity), "old cast stays tagged and invalid after handoff");
        } catch (InterruptedException failure) { throw new AssertionError(failure); }
        finally { f.access.releaseAttack.countDown(); }
    }
}
