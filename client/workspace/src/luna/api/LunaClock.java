package luna.api;

/**
 * Time source used by all Luna controllers.
 *
 * The game runtime supplies a monotonic implementation. Host tests use a fake
 * clock so state-machine tests do not depend on wall-clock timing.
 */
public interface LunaClock {

    /** Returns elapsed milliseconds from the runtime time source. */
    long nowMillis();
}
