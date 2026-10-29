package luna.api;

/** Version-specific native access for the standalone Hung investigation build. */
public interface MonsterCacheAccess {
    long now();
    String serverKey();
    int mapId();
    int zoneId();
    boolean ready();
    Object find(int id);
    /** Reads the native dead/hidden state without changing HP or combat fields. */
    boolean dead(Object monster);
    /** Reads whether the native death animation has hidden this object. */
    boolean hidden(Object monster);
    /** Native removal flags, before the world consumes and deletes the object. */
    boolean retiring(Object monster);
    /** Retains a non-targetable corpse; optionally advances its death clip once. */
    void corpse(Object monster, boolean advanceFrame);
    int hp(Object monster);
    int id(Object monster);
    int type(Object monster);
    boolean autoEnabled();
    void clearAutoTarget(Object monster);
    /** Applies bounded combat HP; positive HP releases native death/quarantine
     * state without changing position or an already-live animation. */
    boolean applyHp(Object monster, int hp);
    Object restore(int template, int id, int x, int y);
    void pending(Object monster, boolean value);
    void discard(Object monster);
    void request(int id);
    byte[] load();
    boolean save(byte[] data);
    void hud(Object graphics, String status);
}
