package luna.features;

import luna.api.LunaBridgeApi;
import luna.api.LunaClock;
import luna.api.LunaPlayerView;
import luna.core.MovementArbiter;

/** Semantic owner of the Auto Ngã 4 combat loop. */
public final class N4Controller {

    public static final int OFF = 0;
    public static final int HOME = 1;
    public static final int TALK = 2;
    public static final int WAIT = 3;
    public static final int MOVE = 4;
    public static final int HOLD = 5;
    public static final int LEAVE = 6;
    public static final int ACTION_SUBMENU = 10;
    public static final int ACTION_TOGGLE = -25;

    private final LunaBridgeApi bridge;
    private final LunaClock clock;
    private final MovementArbiter movement;
    private final LunaPlayerView player = new LunaPlayerView();
    private int state = OFF;
    private long startedAt;

    public N4Controller(LunaBridgeApi bridge, LunaClock clock,
            MovementArbiter movement) {
        this.bridge = bridge;
        this.clock = clock;
        this.movement = movement;
    }

    public void start() {
        state = HOME;
        startedAt = clock.nowMillis();
    }

    public boolean startAndAcquire() {
        start();
        return acquireMovement();
    }

    public void stop() {
        state = OFF;
        movement.release(MovementArbiter.OWNER_N4);
    }

    public void tick() {
        if (state == OFF || !bridge.isWorldReady()) return;
        bridge.readPlayer(player);
    }

    public boolean acquireMovement() {
        return state != OFF && movement.acquire(MovementArbiter.OWNER_N4);
    }

    public boolean submitPath(int[] path, int length) {
        return movement.submitPath(MovementArbiter.OWNER_N4, path, length);
    }

    public void releaseMovement() {
        movement.release(MovementArbiter.OWNER_N4);
    }

    public int state() { return state; }
    public long startedAt() { return startedAt; }
    public LunaPlayerView playerView() { return player; }

    public void setState(int nextState) {
        if (nextState < OFF || nextState > LEAVE) {
            throw new IllegalArgumentException("unknown N4 state: " + nextState);
        }
        state = nextState;
    }

    /** Semantic reducer for the Auto Ngã 4 menu hook. */
    public boolean handleMenuAction(int action) {
        if (action == ACTION_TOGGLE) {
            if (state == OFF) start(); else stop();
            return true;
        }
        return action == ACTION_SUBMENU;
    }
}
