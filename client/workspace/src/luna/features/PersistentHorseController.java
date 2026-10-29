package luna.features;

/**
 * Re-arms a persisted auto-horse setting whenever the player enters a world.
 *
 * <p>The native support keeps the setting in RMS, but its runtime flag can be
 * cleared while the login screen is rebuilt. This controller owns only the
 * lifecycle policy; the binary bridge performs the actual RMS read and mount
 * calls.</p>
 */
public final class PersistentHorseController {

    public static final int NONE = 0;
    public static final int ENABLE_AND_MOUNT = 1;
    public static final int RETRY_MOUNT = 2;
    /** The native menu already sent the item-use request. */
    public static final int EXTERNAL_ENABLE = 3;
    public static final long RETRY_MS = 2500L;
    /** Let the world, player and inventory finish settling before item use. */
    public static final long READY_SETTLE_MS = 5000L;
    public static final int MAX_TRANSIENT_FAILURES = 2;

    private boolean configLoaded;
    private boolean wasInGame;
    private boolean wasEnabled;
    private boolean worldNeedsMount = true;
    private boolean nativeTogglePending;
    private boolean mountFinished;
    private int transientFailures;
    private long nextAttemptAt;

    public boolean needsConfigLoad() {
        return !configLoaded;
    }

    public void markConfigLoaded() {
        configLoaded = true;
    }

    /**
     * The native Auto Horse menu sends the selected item itself.  Remember
     * that request so the lifecycle retry does not send the same item again.
     */
    public void observeNativeToggle(boolean enabled) {
        if (!enabled) {
            wasEnabled = false;
            nativeTogglePending = false;
            mountFinished = false;
            transientFailures = 0;
            nextAttemptAt = 0L;
            return;
        }
        nativeTogglePending = true;
    }

    /** Marks a successful/terminal mount result and stops further item use. */
    public void markMounted() {
        mountFinished = true;
        nextAttemptAt = 0L;
    }

    /** Allows a bounded retry for the explicit transient item-use failure. */
    public void reportTransientFailure(long now) {
        if (!mountFinished) return;
        if (transientFailures >= MAX_TRANSIENT_FAILURES) return;
        transientFailures++;
        mountFinished = false;
        nextAttemptAt = now + RETRY_MS;
    }

    /**
     * Returns a small bridge action. No allocation or binary access happens
     * here, so this state machine is safe to exercise in host tests.
     */
    public int tick(long now, boolean inGame, boolean ready, boolean enabled,
            boolean nativeEnabled) {
        if (!inGame) {
            wasInGame = false;
            wasEnabled = false;
            worldNeedsMount = true;
            nativeTogglePending = false;
            mountFinished = false;
            transientFailures = 0;
            nextAttemptAt = 0L;
            return NONE;
        }

        if (!wasInGame) {
            wasInGame = true;
        }

        if (!enabled) {
            wasEnabled = false;
            nativeTogglePending = false;
            mountFinished = false;
            transientFailures = 0;
            nextAttemptAt = 0L;
            return NONE;
        }

        // isPlayerInGame() can become true before the game screen, dialog and
        // inventory have settled.  Do not consume a horse item in that window.
        if (!ready) return NONE;

        if (nativeTogglePending) {
            nativeTogglePending = false;
            wasEnabled = true;
            worldNeedsMount = false;
            mountFinished = true;
            nextAttemptAt = 0L;
            return EXTERNAL_ENABLE;
        }

        // A persisted flag after a login/world edge needs one controlled mount
        // request. A native menu re-enable has already sent its request.
        if (!wasEnabled) {
            wasEnabled = true;
            if (!worldNeedsMount && nativeEnabled) {
                mountFinished = true;
                return EXTERNAL_ENABLE;
            }
            worldNeedsMount = false;
            mountFinished = false;
            transientFailures = 0;
            nextAttemptAt = now + READY_SETTLE_MS;
            return ENABLE_AND_MOUNT;
        }

        if (mountFinished) return NONE;
        if (now < nextAttemptAt) return NONE;
        mountFinished = true;
        nextAttemptAt = now + RETRY_MS;
        return RETRY_MOUNT;
    }

    /** Compatibility overload for small host callers that do not model native state. */
    public int tick(long now, boolean inGame, boolean ready, boolean enabled) {
        return tick(now, inGame, ready, enabled, false);
    }
}
