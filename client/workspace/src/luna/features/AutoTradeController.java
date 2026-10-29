package luna.features;

import luna.api.LunaBridgeApi;
import luna.api.LunaClock;
import luna.api.LunaPlayerView;
import luna.core.MovementArbiter;

/** Semantic owner of the Auto Đi Buôn state machine. */
public final class AutoTradeController {

    public static final int OFF = 0;
    public static final int GOTO_START = 1;
    public static final int SPAWN_PET = 2;
    public static final int TALK_DYLAN = 3;
    public static final int BUY = 4;
    public static final int HAUL = 5;
    public static final int TALK_GRAHAM = 6;
    public static final int RETURN_HOME = 7;
    public static final int BOOST_PET = 8;
    public static final int WAIT_STOCK = 9;
    public static final int DISMISS_PET = 10;
    public static final int DISMISS_COW = DISMISS_PET;
    public static final int ACTION_SUBMENU = 9;
    public static final int ACTION_TOGGLE = -22;
    public static final int ACTION_BOOST = -23;
    public static final int ACTION_NOCLIP = -24;

    /** Shared haul policy; kept here so the binary bridge cannot silently fork it. */
    public static final int HAUL_STEP_PIXELS = 60;
    public static final int PET_LOST_PIXELS = 80;
    public static final int PET_LOST_BOOST_PIXELS = 240;

    private final LunaBridgeApi bridge;
    private final LunaClock clock;
    private final MovementArbiter movement;
    private final LunaPlayerView player = new LunaPlayerView();
    private int state = OFF;
    private long startedAt;
    private boolean boostEnabled = true;
    private boolean noclipEnabled = true;

    public AutoTradeController(LunaBridgeApi bridge, LunaClock clock,
            MovementArbiter movement) {
        this.bridge = bridge;
        this.clock = clock;
        this.movement = movement;
    }

    public void start() {
        state = GOTO_START;
        startedAt = clock.nowMillis();
    }

    public void stop() {
        state = OFF;
        movement.release(MovementArbiter.OWNER_AUTOTRADE);
    }

    public void tick() {
        if (state == OFF || !bridge.isWorldReady()) return;
        bridge.readPlayer(player);
    }

    public boolean acquireMovement() {
        return state != OFF && movement.acquire(MovementArbiter.OWNER_AUTOTRADE);
    }

    public boolean submitPath(int[] path, int length) {
        return movement.submitPath(MovementArbiter.OWNER_AUTOTRADE, path, length);
    }

    public void releaseMovement() {
        movement.release(MovementArbiter.OWNER_AUTOTRADE);
    }

    public int state() { return state; }
    public long startedAt() { return startedAt; }
    public LunaPlayerView playerView() { return player; }

    public void setState(int nextState) {
        if (nextState < OFF || nextState > DISMISS_PET) {
            throw new IllegalArgumentException("unknown AutoTrade state: " + nextState);
        }
        state = nextState;
    }

    /** Semantic menu reducer used by the generated hook boundary. */
    public boolean handleMenuAction(int action) {
        if (action == ACTION_TOGGLE) {
            if (state == OFF) start(); else stop();
            return true;
        }
        if (action == ACTION_BOOST) {
            boostEnabled = !boostEnabled;
            return true;
        }
        if (action == ACTION_NOCLIP) {
            noclipEnabled = !noclipEnabled;
            movement.release(MovementArbiter.OWNER_AUTOTRADE);
            return true;
        }
        return action == ACTION_SUBMENU;
    }

    public boolean isBoostEnabled() { return boostEnabled; }
    public boolean isNoclipEnabled() { return noclipEnabled; }

    public static int petLostPixels(boolean boostEnabled) {
        return boostEnabled ? PET_LOST_BOOST_PIXELS : PET_LOST_PIXELS;
    }

    public static int haulTiles(int tilePixels, boolean boostEnabled) {
        if (tilePixels <= 0) return 1;
        int tiles = HAUL_STEP_PIXELS / tilePixels;
        if (!boostEnabled) tiles /= 2;
        return tiles < 1 ? 1 : tiles;
    }
}
