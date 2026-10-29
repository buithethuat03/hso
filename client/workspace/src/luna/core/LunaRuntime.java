package luna.core;

import luna.api.LunaBridgeApi;
import luna.api.LunaClock;
import luna.features.AutoTradeController;
import luna.features.N4Controller;
import luna.features.ReviveController;

/** Shared lifecycle coordinator for semantic Luna controllers. */
public final class LunaRuntime {

    private final AutoTradeController autoTrade;
    private final N4Controller n4;
    private final ReviveController revive;

    public LunaRuntime(LunaBridgeApi bridge, LunaClock clock) {
        MovementArbiter movement = new MovementArbiter();
        autoTrade = new AutoTradeController(bridge, clock, movement);
        n4 = new N4Controller(bridge, clock, movement);
        revive = new ReviveController(bridge, clock);
    }

    /** Called once per client frame by the generated hook façade. */
    public void tick() {
        revive.tick();
        n4.tick();
        autoTrade.tick();
    }

    public AutoTradeController autoTrade() { return autoTrade; }
    public N4Controller n4() { return n4; }
    public ReviveController revive() { return revive; }
}
