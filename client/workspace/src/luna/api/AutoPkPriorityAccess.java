package luna.api;

/** Optional Hung PK profile; other clients retain their existing mode policy. */
public interface AutoPkPriorityAccess {
    /** Higher ranks are chosen before nearer targets of a lower rank; equal ranks go nearer first; 0 is ordinary. */
    int priorityRank(Object target);
}
