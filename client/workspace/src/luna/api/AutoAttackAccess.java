package luna.api;

/** Version-specific game access for the independent automatic attack controller. */
public interface AutoAttackAccess {
    boolean ready();
    Object player();
    int mapId();
    int zoneId();
    int entityCount();
    Object entityAt(int index);
    boolean validTarget(Object target, boolean players);
    int distanceTo(Object target);
    boolean combatBusy();
    boolean movementRequested();
    boolean attack(Object target);
    void cancelAttack(Object target);
    void stopFarm();
    void showMenu(boolean enabled, boolean players, int delay);
    void showDelayInput(int delay);
    void notice(String text);
}
