package luna.api;

/**
 * Stable semantic boundary between Luna features and the version-specific
 * binary client. Only bridge generation and hook patching know binary owners,
 * members, and descriptors.
 */
public interface LunaBridgeApi {

    /** Identifier of the binary baseline used to generate this bridge. */
    String baselineId();

    /** Whether the world/player snapshot is safe to consume this frame. */
    boolean isWorldReady();

    /** Fills a reusable player DTO. */
    void readPlayer(LunaPlayerView out);

    /** Returns the number of visible entities available for iteration. */
    int entityCount();

    /** Fills a reusable entity DTO; returns false for an invalid slot. */
    boolean readEntity(int index, LunaEntityView out);

    /** Fills a reusable menu DTO. */
    void readMenu(LunaMenuView out);

    /** Fills a reusable shop DTO. */
    void readShop(LunaShopView out);

    /** Sends a semantic action to the binary client. */
    void sendAction(LunaAction action);

}
