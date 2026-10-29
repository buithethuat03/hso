package luna.features;

/**
 * Parses the Hung dungeon's server gate notices and holds one fixed stand tile.
 * Native notice access and movement writes stay in the binary bridge.
 */
public final class HungDungeonPositionController {
    public static final int DUNGEON_MAP = 48;
    public static final long GATE_HOLD_MS = 2000L;
    public static final int TILE_SIZE = 24;

    public static final int NO_DIRECTION = 0;
    public static final int WEST = 1;
    public static final int NORTH = 2;
    public static final int EAST = 3;
    public static final int SOUTH = 4;
    public static final int MULTIPLE = 5;

    private boolean active;
    private boolean hasTarget;
    private int direction = NO_DIRECTION;
    private int tileX = -1;
    private int tileY = -1;
    private long targetAt;

    /** Mark the dungeon active; repeated calls preserve the current wave aim. */
    public synchronized void enter() {
        if (active) return;
        clearTarget();
        active = true;
    }

    /** Begin a fresh dungeon run and discard any aim from the previous run. */
    public synchronized void enterDungeon() {
        clearTarget();
        active = true;
    }

    /** Leave the dungeon or disable its owner. */
    public synchronized void reset() {
        clearTarget();
        active = false;
    }

    /**
     * Observe a newly received server notice. Returns true only when a new stand tile
     * should be routed to; repeats and unknown notices never request another path.
     */
    public synchronized boolean notify(String text, int mapId, boolean enabled, long now) {
        if (!enabled || mapId != DUNGEON_MAP) {
            reset();
            return false;
        }
        return onNotice(text, now);
    }

    /** Consume raw server notice text after the native bridge has gated map and feature state. */
    public synchronized boolean onNotice(String text, long now) {
        if (!active) enter();

        int nextDirection = parseDirection(text);
        if (nextDirection == NO_DIRECTION) return false;

        int nextX = tileXFor(nextDirection);
        int nextY = tileYFor(nextDirection);
        if (hasTarget && nextX == tileX && nextY == tileY) {
            direction = nextDirection;
            return false;
        }
        if (hasTarget && now - targetAt < GATE_HOLD_MS) return false;

        direction = nextDirection;
        tileX = nextX;
        tileY = nextY;
        targetAt = now;
        hasTarget = true;
        return true;
    }

    public synchronized boolean isActive() { return active; }
    public synchronized boolean hasTarget() { return active && hasTarget; }
    public synchronized int direction() { return direction; }
    public synchronized int targetTileX() { return hasTarget() ? tileX : -1; }
    public synchronized int targetTileY() { return hasTarget() ? tileY : -1; }
    public synchronized int targetX() { return hasTarget() ? tileCenter(tileX) : -1; }
    public synchronized int targetY() { return hasTarget() ? tileCenter(tileY) : -1; }

    private void clearTarget() {
        hasTarget = false;
        direction = NO_DIRECTION;
        tileX = -1;
        tileY = -1;
        targetAt = 0L;
    }

    private static int parseDirection(String text) {
        if (text == null) return NO_DIRECTION;
        String lower = text.toLowerCase();
        // Require the server's gate announcement signature, not just chat words
        // mentioning a door and compass points.
        if (lower.indexOf("bầy quái vật sẽ xuất hiện ở cửa") < 0) return NO_DIRECTION;
        int found = 0;
        if (lower.indexOf("tây") >= 0) found |= 1;
        if (lower.indexOf("bắc") >= 0) found |= 2;
        if (lower.indexOf("đông") >= 0) found |= 4;
        if (lower.indexOf("nam") >= 0) found |= 8;
        if (found == 0) return NO_DIRECTION;
        if ((found & (found - 1)) != 0) return MULTIPLE;
        if (found == 1) return WEST;
        if (found == 2) return NORTH;
        if (found == 4) return EAST;
        return SOUTH;
    }

    private static int tileXFor(int direction) {
        if (direction == MULTIPLE) return 27;
        if (direction == WEST || direction == NORTH) return 20;
        if (direction == EAST) return 33;
        return 19;
    }

    private static int tileYFor(int direction) {
        if (direction == MULTIPLE) return 26;
        if (direction == WEST || direction == NORTH || direction == EAST) return 20;
        return 33;
    }

    private static int tileCenter(int tile) { return tile * TILE_SIZE + TILE_SIZE / 2; }
}
