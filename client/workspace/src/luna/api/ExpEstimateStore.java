package luna.api;

/** Small persistence boundary for the semantic EXP estimator. */
public interface ExpEstimateStore {
    /** Loads the shared level table; legacy payloads may be supplied during migration. */
    byte[] load();

    /** Atomically merges changed levels into the stored table, preserving other levels. */
    boolean save(byte[] data);
}
