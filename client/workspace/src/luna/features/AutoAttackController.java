package luna.features;

import luna.api.AutoAttackAccess;
import luna.api.AutoAttackSettings;
import luna.api.AutoPkPriorityAccess;
import luna.api.FarmSpotAccess;
import luna.api.LunaClock;
import luna.api.SpecialAttackAccess;

/** Owns automatic attack intent, targets and cadence independently of Farm. */
public final class AutoAttackController {
    public static final int MENU = -110;
    public static final int TOGGLE = -111;
    public static final int MONSTERS = -112;
    public static final int MONSTERS_AND_PLAYERS = -113;
    public static final int DELAY = -114;
    public static final int SPECIAL = -116;
    /**
     * ModUpLv "tan cong quai thuong" (default ON, like ModUpLv): with Special ON the ModUpLv
     * attack loop runs on its own, 20 nearest monsters of the whole map, every learned skill.
     * Auto đánh and Farm then leave the casting to it.
     */
    public static final int NORMAL_MONSTERS = -117;
    private static final int RANGE = 2000000;
    private static final int MOVEMENT_RESUME_MS = 100;
    /**
     * Real-time spacing between any two Special sends (30ms; ModUpLv sends at most one per
     * >=28ms frame). Hung's tempo loop runs 50-150 updates/s plus catch-up bursts.
     */
    public static final int SPECIAL_MIN_GAP_MS = 30;
    /** Real time without a send (while attacks are refused) before Farm may use its village scroll. */
    public static final int FARM_STUCK_MS = 15000;
    /** A local cast that keeps the player busy this long at the spot is cancelled. */
    public static final int FARM_BUSY_CANCEL_MS = 1500;
    /** Consecutive refused sends before the target is skipped for FARM_SKIP_MS. */
    public static final int FARM_REFUSALS = 3;
    public static final int FARM_SKIP_MS = 3000;
    private static final int CHOOSE_SENT = 0, CHOOSE_WAIT = 1, CHOOSE_REFUSED = 2;
    private final AutoAttackAccess access;
    private final AutoAttackSettings settings;
    private final LunaClock clock;
    private boolean enabled;
    private boolean special;
    private boolean normalMonsters = true;
    private boolean specialHasSent;
    private long lastSpecialSend;
    private boolean players;
    private int delay = 650;
    private int generation = 1;
    private Object target;
    private Object farmTarget;
    private Object farmSkip;
    private long farmSkipUntil;
    private long farmProgressAt = -1;
    private long farmBusySince = -1;
    private long farmLastTick = -1;
    private int farmRefusals;
    private int chooseOutcome = CHOOSE_REFUSED;
    private Object observedPlayer;
    private int observedMap = -1;
    private int observedZone = -1;
    private long lastAttempt;
    private boolean startingAttack;
    private boolean movementPaused;
    private boolean movementResumePending;
    private boolean movementResumeCast;
    private boolean movementWorldChanged;

    public AutoAttackController(AutoAttackAccess access, AutoAttackSettings settings, LunaClock clock) {
        this.access = access;
        this.settings = settings;
        this.clock = clock;
        int[] saved = new int[] {0, 650};
        try { settings.load(saved); } catch (Throwable ignored) { }
        if ((saved[0] == 0 || saved[0] == 1) && saved[1] >= 1 && saved[1] <= 1000) {
            players = saved[0] == 1;
            delay = saved[1];
        }
    }

    public synchronized boolean enabled() { return enabled; }
    public synchronized boolean specialEnabled() { return special; }
    public synchronized boolean specialActive() { return enabled && special && startingAttack; }
    public synchronized String specialLabel() {
        return special ? "Tấn công đặc biệt: [BẬT]" : "Tấn công đặc biệt: [TẮT]";
    }
    public synchronized String normalLabel() {
        return normalMonsters ? "Tấn công quái thường: [BẬT]" : "Tấn công quái thường: [TẮT]";
    }
    /** The ModUpLv attack loop owns every Special send (map-wide monsters). */
    public synchronized boolean normalMonstersActive() { return special && normalMonsters; }
    /** Dungeon automation: Auto đánh on monsters only, with Special and normal monsters. */
    public synchronized void engageMonsters() {
        if (players) { players = false; invalidateAttack(); save(); }
        if (!special || !normalMonsters) {
            if (!special) SkillCastStats.reset();
            special = true; normalMonsters = true; resetSpecialFarm(); invalidateAttack();
        }
        setEnabled(true);
    }
    public synchronized boolean specialSkillAllowed(int skill) {
        if (!special) return true;
        if (skill != 7 && skill != 8 && skill != 19 && skill != 20) return true;
        long now = clock.nowMillis();
        if (!specialHasSent) return true;
        if (now < lastSpecialSend) { lastSpecialSend = now; return false; }
        return now - lastSpecialSend > 600;
    }
    public synchronized void specialSent() {
        if (special) { lastSpecialSend = clock.nowMillis(); specialHasSent = true; }
    }
    /** Reserve the highest ready configured skill while its special gate expires. */
    public synchronized boolean chooseSpecial(SpecialAttackAccess view, boolean farm) {
        if (!special || (farm ? enabled : !specialActive())) { chooseOutcome = CHOOSE_REFUSED; return false; }
        chooseOutcome = CHOOSE_WAIT;
        // The ModUpLv loop sends; a second sender would only crowd its packets.
        if (normalMonsters) return false;
        if (specialHasSent) {
            long now = clock.nowMillis();
            if (now >= lastSpecialSend && now - lastSpecialSend < SPECIAL_MIN_GAP_MS) return false;
        }
        int bestSkill = -1;
        int bestSlot = -1;
        int count = view.specialSlotCount();
        for (int slot = 0; slot < count; slot++) {
            int skill = view.specialSkillAt(slot);
            if (skill < 0 || skill <= bestSkill || !view.specialSkillReady(skill)) continue;
            bestSkill = skill;
            bestSlot = slot;
        }
        if (bestSkill < 0 && view.specialSkillReady(0)) bestSkill = 0;
        if (bestSkill < 0 || !specialSkillAllowed(bestSkill)) return false;
        boolean sent = view.specialChoose(bestSlot, farm);
        chooseOutcome = sent ? CHOOSE_SENT : CHOOSE_REFUSED;
        return sent;
    }
    /**
     * Farm has reached its saved spot: stand still and attack like Auto đánh Special.
     * Returns false to leave the native Farm tick (legacy walking auto) in charge.
     */
    public synchronized boolean farmSpot(FarmSpotAccess view) {
        if (enabled || !special) return false;
        long now = clock.nowMillis();
        // Re-entering the spot (after death, a trip or Special OFF) starts a fresh stuck window.
        if (farmProgressAt < 0 || now < farmProgressAt || farmLastTick < 0 || now < farmLastTick
                || now - farmLastTick > 1000) farmProgressAt = now;
        farmLastTick = now;
        view.farmSpotHold();
        // A ghost always wins, like the native auto: Farm's captcha branch must see it at once.
        int count = view.entityCount();
        for (int i = 0; i < count; i++) {
            Object candidate = view.entityAt(i);
            if (view.farmSpotTargetValid(candidate) && view.farmSpotGhost(candidate)) {
                farmTarget = candidate; view.farmSpotSelect(candidate); farmProgress(view, now); return true;
            }
        }
        if (normalMonsters) {
            // The ModUpLv loop attacks; its sends, or nothing left to hit, are the progress.
            farmTarget = null;
            if (specialHasSent && now >= lastSpecialSend && now - lastSpecialSend < FARM_STUCK_MS) farmProgress(view, now);
            else if (farmSpotFind(view, now) == null) farmProgress(view, now);
            else farmKeepAlive(view, now);
            return true;
        }
        Object current = farmTarget;
        if (current != null && !farmSpotCurrent(view, current)) current = null;
        if (current == null) current = farmSpotFind(view, now);
        if (current != farmTarget) farmRefusals = 0;
        farmTarget = current;
        // Nothing to hit is waiting, not stuck.
        // Only a skipped (refused) monster left still counts toward the stuck window.
        if (current == null) {
            if (farmSkip != null) farmKeepAlive(view, now); else farmProgress(view, now);
            return true;
        }
        if (view.combatBusy()) {
            if (farmBusySince < 0 || now < farmBusySince) farmBusySince = now;
            else if (now - farmBusySince >= FARM_BUSY_CANCEL_MS) { view.farmSpotCancelCast(); farmBusySince = -1; }
            farmKeepAlive(view, now);
            return true;
        }
        farmBusySince = -1;
        chooseOutcome = CHOOSE_REFUSED;
        view.farmSpotAttack(current);
        if (chooseOutcome == CHOOSE_REFUSED) {
            // Out of range, filtered or rejected by the native chooser: try another monster.
            if (++farmRefusals >= FARM_REFUSALS) {
                farmSkip = current; farmSkipUntil = now + FARM_SKIP_MS;
                farmTarget = null; farmRefusals = 0;
            }
            farmKeepAlive(view, now);
        } else {
            // A send, or waiting on cooldown/MP/600ms gate/28ms spacing, is progress.
            if (chooseOutcome == CHOOSE_SENT) farmRefusals = 0;
            farmProgress(view, now);
        }
        return true;
    }

    private void farmProgress(FarmSpotAccess view, long now) {
        farmProgressAt = now;
        view.farmSpotActive();
    }

    /** Keeps Farm's tick-based counter at zero until FARM_STUCK_MS real time without progress. */
    private void farmKeepAlive(FarmSpotAccess view, long now) {
        if (now - farmProgressAt < FARM_STUCK_MS) view.farmSpotActive();
    }

    private Object farmSpotFind(FarmSpotAccess view, long now) {
        boolean skipping = farmSkip != null && now >= farmSkipUntil - FARM_SKIP_MS && now < farmSkipUntil;
        if (!skipping) farmSkip = null;
        Object best = null;
        int bestDistance = RANGE + 1;
        int count = view.entityCount();
        for (int i = 0; i < count; i++) {
            Object candidate = view.entityAt(i);
            if (!view.farmSpotTargetValid(candidate)) continue;
            if (candidate == farmSkip) continue;
            int distance = view.distanceTo(candidate);
            if (distance >= 0 && distance < bestDistance) { best = candidate; bestDistance = distance; }
        }
        return best;
    }

    private boolean farmSpotCurrent(FarmSpotAccess view, Object candidate) {
        if (!view.farmSpotTargetValid(candidate)) return false;
        int distance = view.distanceTo(candidate);
        if (distance < 0 || distance > RANGE) return false;
        int count = view.entityCount();
        for (int i = 0; i < count; i++) if (view.entityAt(i) == candidate) return true;
        return false;
    }

    public synchronized boolean playersEnabled() { return players; }
    public synchronized int delayMs() { return delay; }
    public synchronized int generation() { return generation; }
    public synchronized Object target() { return target; }
    public synchronized int captureGeneration() { return enabled && startingAttack ? generation : 0; }
    /** Captured by the native cast initializer while the controller starts a cast. */
    public synchronized int requestMinimum(int tag) {
        return tag > 0 && tag == generation && movementResumeCast
                ? MOVEMENT_RESUME_MS : delay;
    }
    public synchronized boolean captureMovementResume() {
        return enabled && startingAttack && movementResumePending;
    }
    public synchronized String statusText() {
        if (!enabled) return null;
        return players ? (access instanceof AutoPkPriorityAccess ? "Auto Đánh: PK [6]" : "Auto Đánh: Quái & PK [6]")
                : "Auto Đánh: Chỉ Quái [7]";
    }

    public synchronized boolean handleAction(int action) {
        if (action == MENU) { showMenu(); return true; }
        if (action == TOGGLE) { setEnabled(!enabled); showMenu(); return true; }
        if (action == MONSTERS || action == MONSTERS_AND_PLAYERS) {
            boolean next = action == MONSTERS_AND_PLAYERS;
            if (next != players) { players = next; invalidateAttack(); save(); }
            setEnabled(true);
            showMenu();
            return true;
        }
        if (action == DELAY) { access.showDelayInput(delay); return true; }
        if (action == SPECIAL) {
            special = !special;
            if (special) SkillCastStats.reset();
            resetSpecialFarm();
            invalidateAttack();
            showMenu();
            return true;
        }
        if (action == NORMAL_MONSTERS) {
            normalMonsters = !normalMonsters;
            // The map-wide list is a Special target mode, as in ModUpLv.
            if (normalMonsters && !special) { special = true; SkillCastStats.reset(); resetSpecialFarm(); }
            invalidateAttack();
            showMenu();
            return true;
        }
        return false;
    }

    private void resetSpecialFarm() {
        farmTarget = null;
        farmSkip = null;
        farmProgressAt = farmBusySince = farmLastTick = -1;
        farmRefusals = 0;
    }

    public synchronized boolean submitDelay(String input) {
        if (input == null) return false;
        String text = input.trim();
        int value = 0;
        if (text.length() == 0) return invalidDelay();
        for (int i = 0; i < text.length(); i++) {
            char digit = text.charAt(i);
            if (digit < '0' || digit > '9') return invalidDelay();
            value = value * 10 + digit - '0';
            if (value > 1000) return invalidDelay();
        }
        if (value < 1) return invalidDelay();
        if (delay != value) { delay = value; save(); }
        showMenu();
        return true;
    }

    private boolean invalidDelay() { access.notice("Delay phải là số nguyên từ 1 đến 1000 ms."); return false; }
    private void save() {
        try { settings.save(players, delay); }
        catch (Throwable failure) { access.notice("Không lưu được cài đặt Auto đánh. Cài đặt vẫn dùng trong phiên này."); }
    }
    private void showMenu() { access.showMenu(enabled, players, delay); }

    public synchronized void setEnabled(boolean next) {
        if (enabled == next) return;
        if (next) {
            // Native Farm cleanup must complete before the new owner is visible.
            access.stopFarm();
            invalidateAttack();
            enabled = true;
            observeWorld();
        } else {
            enabled = false;
            movementPaused = false;
            movementWorldChanged = false;
            invalidateAttack();
            observedPlayer = null;
            observedMap = observedZone = -1;
        }
    }

    private void invalidateAttack() {
        Object previous = target;
        target = null;
        generation++;
        if (generation <= 0) generation = 1;
        startingAttack = false;
        movementResumePending = false;
        movementResumeCast = false;
        lastAttempt = clock.nowMillis();
        access.cancelAttack(previous);
    }

    private void observeWorld() {
        if (access.ready()) {
            observedPlayer = access.player();
            observedMap = access.mapId();
            observedZone = access.zoneId();
        } else {
            observedPlayer = null;
            observedMap = observedZone = -1;
        }
    }

    /** Invalidates work from a transient native world failure while retaining intent. */
    public synchronized void suspend() {
        if (!enabled) return;
        try { invalidateAttack(); }
        finally {
            movementPaused = false;
            movementWorldChanged = false;
            observedPlayer = null;
            observedMap = observedZone = -1;
        }
    }

    /** Native movement input may arrive before a path or walking state exists. */
    public synchronized void beginMovement() {
        if (!enabled) return;
        if (!movementPaused) {
            movementPaused = true;
            movementWorldChanged = false;
            invalidateAttack();
        }
        lastAttempt = clock.nowMillis();
    }

    public synchronized void yieldToMovement() {
        if (enabled && access.movementRequested()) beginMovement();
    }

    public synchronized void tick() {
        if (!enabled) return;
        if (!access.ready()) {
            if (target != null || observedPlayer != null) invalidateAttack();
            observedPlayer = null;
            observedMap = observedZone = -1;
            movementPaused = false;
            movementWorldChanged = false;
            return;
        }
        if (observedPlayer != access.player() || observedMap != access.mapId() || observedZone != access.zoneId()) {
            boolean moving = movementPaused || access.movementRequested();
            invalidateAttack();
            observeWorld();
            movementPaused = moving;
            movementWorldChanged = moving;
            return;
        }
        if (access.movementRequested()) { beginMovement(); return; }
        // The ModUpLv loop attacks the whole map: Auto đánh stands by without a target.
        if (special && normalMonsters) { if (target != null) invalidateAttack(); return; }
        if (movementPaused) {
            boolean resumeInSameWorld = !movementWorldChanged;
            movementPaused = false;
            movementWorldChanged = false;
            invalidateAttack();
            observeWorld();
            movementResumePending = resumeInSameWorld;
            return;
        }
        if (players && access instanceof AutoPkPriorityAccess && !access.combatBusy()) {
            Object next = findTarget();
            if (next != target) { invalidateAttack(); target = next; }
        }
        if (target != null && (!currentTarget(target) || access.distanceTo(target) > RANGE)) invalidateAttack();
        if (target == null) target = findTarget();
        long now = clock.nowMillis();
        if (now < lastAttempt) { lastAttempt = now; return; }
        if (target == null || access.combatBusy()) return;
        long elapsed = now - lastAttempt;
        if (!special && (movementResumePending ? elapsed < MOVEMENT_RESUME_MS : elapsed <= delay)) return;
        movementResumeCast = movementResumePending;
        startingAttack = true;
        try {
            if (access.attack(target)) {
                lastAttempt = now;
                movementResumePending = false;
            }
        } finally { startingAttack = false; }
    }

    private Object findTarget() {
        Object best = null;
        int bestDistance = RANGE + 1;
        int bestPriority = 0;
        AutoPkPriorityAccess pk = players && access instanceof AutoPkPriorityAccess
                ? (AutoPkPriorityAccess)access : null;
        int count = access.entityCount();
        for (int i = 0; i < count; i++) {
            Object candidate = access.entityAt(i);
            if (!access.validTarget(candidate, players)) continue;
            int distance = access.distanceTo(candidate);
            int priority = pk != null ? pk.priorityRank(candidate) : 0;
            if (distance >= 0 && distance <= RANGE && (best == null || priority > bestPriority
                    || (priority == bestPriority && distance < bestDistance))) {
                best = candidate; bestDistance = distance; bestPriority = priority;
            }
        }
        return best;
    }

    private boolean currentTarget(Object candidate) {
        if (!access.validTarget(candidate, players)) return false;
        int count = access.entityCount();
        for (int i = 0; i < count; i++) if (access.entityAt(i) == candidate) return true;
        return false;
    }

    public synchronized boolean allowRequest(int tag, Object candidate) {
        if (tag == 0) return true;
        return enabled && !movementPaused && tag == generation && access.ready()
                && !access.movementRequested()
                && observedPlayer == access.player() && observedMap == access.mapId()
                && observedZone == access.zoneId() && candidate == target && currentTarget(candidate)
                && access.distanceTo(candidate) >= 0 && access.distanceTo(candidate) <= RANGE;
    }
}
