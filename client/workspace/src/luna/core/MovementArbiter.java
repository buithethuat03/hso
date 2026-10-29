package luna.core;

/**
 * Single ownership gate for the player's path command.
 *
 * A controller must acquire before submitting a path and release when its
 * movement phase ends. A higher-priority owner may preempt a lower-priority
 * owner; equal or lower priorities cannot overwrite the active path.
 */
public final class MovementArbiter {

    public static final int OWNER_NONE = 0;
    public static final int OWNER_AUTOTRADE = 10;
    public static final int OWNER_N4 = 20;
    public static final int OWNER_COMBAT = 30;
    public static final int OWNER_REVIVE = 40;

    private int owner = OWNER_NONE;
    private int[] path;
    private int pathLength;

    public boolean acquire(int requestedOwner) {
        if (requestedOwner == OWNER_NONE) return false;
        if (owner == OWNER_NONE || owner == requestedOwner
                || priority(requestedOwner) > priority(owner)) {
            owner = requestedOwner;
            path = null;
            pathLength = 0;
            return true;
        }
        return false;
    }

    public boolean submitPath(int requestedOwner, int[] pathData, int length) {
        if (owner != requestedOwner || pathData == null || length < 0
                || length > pathData.length) return false;
        path = pathData;
        pathLength = length;
        return true;
    }

    public boolean release(int requestedOwner) {
        if (owner != requestedOwner) return false;
        owner = OWNER_NONE;
        path = null;
        pathLength = 0;
        return true;
    }

    public int currentOwner() { return owner; }
    public boolean hasPath() { return path != null && pathLength > 0; }
    public int[] currentPath() { return path; }
    public int currentPathLength() { return pathLength; }

    private static int priority(int value) {
        return value;
    }
}
