package luna.api;

/** Native Farm view while it stands at its saved spot and attacks through Special. */
public interface FarmSpotAccess {
    int entityCount();
    Object entityAt(int index);
    int distanceTo(Object target);
    boolean combatBusy();
    /** Living monster accepted by Farm's own monster selection. */
    boolean farmSpotTargetValid(Object target);
    /** Ghost captcha monster; Farm's native captcha branch owns it. */
    boolean farmSpotGhost(Object target);
    /** Disarms the legacy walking auto without touching path or target. */
    void farmSpotHold();
    void farmSpotSelect(Object target);
    /** Selects the target and runs Farm's Special chooser; true when a packet was sent. */
    boolean farmSpotAttack(Object target);
    /** Resets Farm's no-attack counter that otherwise uses a village scroll. */
    void farmSpotActive();
    /** Cancels a stuck untagged local cast (e.g. a manual skill waiting to walk into range). */
    void farmSpotCancelCast();
}
