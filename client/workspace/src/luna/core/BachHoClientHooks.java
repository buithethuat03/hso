package luna.core;

import luna.api.BachHoAutomationBridge;
import luna.features.AutoCombatController;
import luna.features.LoginMaterialController;

/** Optional Bạch Hổ facade; all binary access lives in the generated adapter. */
public final class BachHoClientHooks {
    private static AutoCombatController combat;
    private static LoginMaterialController material;
    private BachHoClientHooks() { }

    private static boolean init() {
        if (combat != null) return true;
        try {
            BachHoAutomationBridge bridge = (BachHoAutomationBridge)
                    Class.forName("BachHoGeneratedAutomationBridge").newInstance();
            SystemLunaClock clock = new SystemLunaClock();
            combat = new AutoCombatController(bridge, clock, new MovementArbiter());
            material = new LoginMaterialController(bridge, clock);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    public static boolean handleMenuAction(int action, int param) {
        try { return init() && combat.handleMenuAction(action); }
        catch (Throwable ignored) { return false; }
    }

    public static void onLoginEntry() {
        try { if (init()) { combat.onLoginEntry(); material.onLoginEntry(); } }
        catch (Throwable ignored) { }
    }

    public static void tick() {
        try { if (init()) { material.tick(); combat.tick(); } }
        catch (Throwable ignored) { }
    }

    public static short[] filterPath(short[] path) {
        try { return init() && combat.ownsMovement() ? null : path; }
        catch (Throwable ignored) { return path; }
    }

    public static boolean allowTarget(Object target) {
        try { return !init() || combat.allowTarget(target); }
        catch (Throwable ignored) { return false; }
    }

    public static boolean ownsCombat() {
        try { return init() && combat.ownsMovement(); }
        catch (Throwable ignored) { return false; }
    }

    public static boolean shouldPauseNativeCombat() {
        try { return init() && combat.shouldPauseNativeCombat(); }
        catch (Throwable ignored) { return false; }
    }
}
