package luna.api;

/** Native boundary for map-scoped automatic-monster preferences. */
public interface MonsterSelectionAccess {
    String serverKey();
    int mapId();
    boolean ready();
    int size();
    Object objectAt(int index);
    int type(Object monster);
    String name(Object monster);
    int level(Object monster);
    String templateName(int template);
    /** Historical template/level pairs for the current server/map, across learned zones. */
    int[] remembered();
    byte[] loadSelection();
    boolean saveSelection(byte[] data);
    void showChoices(String[] labels);
    void back();
    void message(String text);
    void changed();
}
