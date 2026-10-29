package luna.api;

/** Native access used by the session-only battlefield kill filter. */
public interface BattlefieldAccess {
    Object player();
    /** Find an entity by native ID and entity-kind value. */
    Object find(int id, int type);
    int id(Object entity);
    /** Native entity kind (cv): player 0, monster 1, other kinds as reported. */
    int type(Object entity);
    int level(Object entity);
    int hp(Object entity);
    String name(Object entity);
}
