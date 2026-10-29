package luna.api;

/** Version-specific client access; generated only after exact symbol verification. */
public interface BachHoAutomationBridge {
    void readCombat(AutoCombatSnapshot out);
    boolean isAutoAttackEnabled();
    boolean routeOwnsMovement();
    void setAutoAttackEnabled(boolean enabled);
    void pauseCombat();
    void setCombatOrigin(int x, int y);
    void holdPosition(int x, int y);
    void returnToMap(int map);
    void changeZone(int zone);
    boolean walkTo(int x, int y);
    void stopWalking();
    int targetX(Object target);
    int targetY(Object target);
    void showMenu(String title, String[] labels, int[] actions);
    void notice(String text);
    void loadPosition(int[] position);
    void savePosition(int[] position);
    boolean isWorldReady();
    void openMaterialMenu();
    String whiteMedalAction();
    void closeWhiteMedalDrop();
    void hideMaterialMenu();
    boolean finishMaterialDialog();
}
