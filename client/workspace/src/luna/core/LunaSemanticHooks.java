package luna.core;

import luna.api.LunaAction;
import luna.api.LunaBridgeApi;
import luna.api.LunaEntityView;
import luna.api.LunaMenuView;
import luna.api.LunaPlayerView;
import luna.api.LunaShopView;
import luna.features.AutoTradeController;
import luna.features.N4Controller;

/**
 * Small semantic dispatcher called by the generated binary hook facade.
 *
 * The version-specific bridge can replace the no-op adapter when a feature
 * needs live snapshots. Keeping the dispatcher binary-free means hook wiring
 * never leaks obfuscated owners into feature classes.
 */
public final class LunaSemanticHooks {

    private static final LunaRuntime RUNTIME = new LunaRuntime(new EmptyBridge(),
            new SystemLunaClock());

    private LunaSemanticHooks() { }

    public static boolean handleMenuAction(int action, int param) {
        boolean handled = RUNTIME.autoTrade().handleMenuAction(action);
        handled = RUNTIME.n4().handleMenuAction(action) || handled;
        return handled;
    }

    public static void tick() {
        RUNTIME.tick();
    }

    /** Placeholder adapter used until a version-specific bridge is installed. */
    private static final class EmptyBridge implements LunaBridgeApi {
        public String baselineId() { return "semantic-host"; }
        public boolean isWorldReady() { return false; }
        public void readPlayer(LunaPlayerView out) { out.clear(); }
        public int entityCount() { return 0; }
        public boolean readEntity(int index, LunaEntityView out) { return false; }
        public void readMenu(LunaMenuView out) { out.clear(); }
        public void readShop(LunaShopView out) { out.clear(); }
        public void sendAction(LunaAction action) { }
    }
}
