package luna.features;

import luna.api.AutoAttackAccess;
import luna.api.AutoAttackSettings;
import luna.api.FarmSpotAccess;
import luna.api.LunaClock;
import luna.api.SpecialAttackAccess;
import luna.core.SystemLunaClock;

/** Small hook ABI; binary symbols remain in the generated adapter. */
public final class AutoAttackRuntime {
    private static volatile AutoAttackController controller;
    private static final Object fallbackLock = new Object();
    private AutoAttackRuntime() { }

    private static synchronized AutoAttackController get() {
        if (controller == null) {
            try {
                AutoAttackAccess access = (AutoAttackAccess) Class.forName("luna.generated.HungAutoAttackAccess").newInstance();
                controller = new AutoAttackController(access, new AutoAttackRmsStore(), new SystemLunaClock());
            } catch (Throwable ignored) { return null; }
        }
        return controller;
    }
    public static synchronized void installForTest(AutoAttackAccess access, AutoAttackSettings settings, LunaClock clock) {
        controller = new AutoAttackController(access, settings, clock);
    }
    public static boolean enabled() { AutoAttackController c = get(); return c != null && c.enabled(); }
    public static boolean specialEnabled() { AutoAttackController c = get(); return c != null && c.specialEnabled(); }
    public static boolean specialActive() { AutoAttackController c = get(); return c != null && c.specialActive(); }
    public static String specialLabel() { AutoAttackController c = get(); return c == null ? "Tấn công đặc biệt: [TẮT]" : c.specialLabel(); }
    public static String normalLabel() { AutoAttackController c = get(); return c == null ? "Tấn công quái thường: [TẮT]" : c.normalLabel(); }
    public static boolean normalMonstersActive() { AutoAttackController c = get(); return c != null && c.normalMonstersActive(); }
    public static void engageMonsters() { AutoAttackController c = get(); if (c != null) c.engageMonsters(); }
    public static boolean specialSkillAllowed(int skill) {
        AutoAttackController c = get(); return c != null && c.specialSkillAllowed(skill);
    }
    public static void specialSent() { AutoAttackController c = get(); if (c != null) c.specialSent(); }
    public static boolean chooseSpecial(SpecialAttackAccess view, boolean farm) {
        AutoAttackController c = get(); return c != null && c.chooseSpecial(view, farm);
    }
    /** Farm stand-at-spot tick; false leaves the native Farm tick unchanged. */
    public static boolean farmSpot(FarmSpotAccess view) {
        AutoAttackController c = get();
        if (c == null) return false;
        try { return c.farmSpot(view); } catch (Throwable ignored) { return false; }
    }
    public static boolean playersEnabled() { AutoAttackController c = get(); return c != null && c.playersEnabled(); }
    public static int delayMs() { AutoAttackController c = get(); return c == null ? 650 : c.delayMs(); }
    public static int generation() { AutoAttackController c = get(); return c == null ? 0 : c.generation(); }
    public static Object target() { AutoAttackController c = get(); return c == null ? null : c.target(); }
    public static String statusText() { AutoAttackController c = get(); return c == null ? null : c.statusText(); }
    public static int captureGeneration() { AutoAttackController c = get(); return c == null ? 0 : c.captureGeneration(); }
    public static boolean captureMovementResume() { AutoAttackController c = get(); return c != null && c.captureMovementResume(); }
    /** Native tagged requests hold this monitor through their actual enqueue. */
    public static Object requestLock() { AutoAttackController c = get(); return c == null ? fallbackLock : c; }
    public static int requestMinimum(int tag) {
        if (tag == 0) return 650;
        AutoAttackController c = get();
        return c == null ? 650 : c.requestMinimum(tag);
    }
    public static boolean allowRequest(int tag, Object target) {
        if (tag == 0) return true;
        AutoAttackController c = get();
        if (c == null) return false;
        try { return c.allowRequest(tag, target); }
        catch (Throwable ignored) { suspend(c); return false; }
    }
    public static boolean handleAction(int action) {
        AutoAttackController c = get();
        if (c == null) return false;
        try { return c.handleAction(action); }
        catch (Throwable ignored) { stop(c); return true; }
    }
    public static boolean submitDelay(String value) {
        AutoAttackController c = get();
        if (c == null) return false;
        try { return c.submitDelay(value); } catch (Throwable ignored) { return false; }
    }
    public static void beforeFarmEnable() {
        AutoAttackController c = get();
        if (c != null) stop(c);
    }
    public static void tick() {
        AutoAttackController c = get();
        if (c == null) return;
        try { c.tick(); } catch (Throwable ignored) { suspend(c); }
    }
    /** Runs before the native local-player update can block movement on a cast. */
    public static void movementTick() {
        AutoAttackController c = get();
        if (c == null) return;
        try { c.yieldToMovement(); } catch (Throwable ignored) { suspend(c); }
    }
    /** Called only from the native branch accepting a manual world-move command. */
    public static void manualMove() {
        AutoAttackController c = get();
        if (c == null) return;
        try { c.beginMovement(); } catch (Throwable ignored) { suspend(c); }
    }
    private static void suspend(AutoAttackController c) {
        try { c.suspend(); } catch (Throwable ignored) { }
    }
    private static void stop(AutoAttackController c) {
        try { c.setEnabled(false); } catch (Throwable ignored) { }
    }
}
