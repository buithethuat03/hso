package luna.features;

import luna.api.LunaAction;
import luna.api.LunaBridgeApi;
import luna.api.LunaClock;
import luna.api.LunaPlayerView;

/** Handles revive timing without inferring death from a single stale frame. */
public final class ReviveController {

    private static final long RETRY_DELAY_MS = 1000L;

    private final LunaBridgeApi bridge;
    private final LunaClock clock;
    private final LunaPlayerView player = new LunaPlayerView();
    private final LunaAction reviveAction = new LunaAction().setType(LunaAction.REVIVE);
    private long lastRequestAt = Long.MIN_VALUE;

    public ReviveController(LunaBridgeApi bridge, LunaClock clock) {
        this.bridge = bridge;
        this.clock = clock;
    }

    public void tick() {
        if (!bridge.isWorldReady()) return;
        bridge.readPlayer(player);
        if (player.isAlive()) return;
        long now = clock.nowMillis();
        if (lastRequestAt == Long.MIN_VALUE || now - lastRequestAt >= RETRY_DELAY_MS) {
            bridge.sendAction(reviveAction);
            lastRequestAt = now;
        }
    }

    public long lastRequestAt() { return lastRequestAt; }
}
