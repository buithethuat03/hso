package luna.features;

/** Identity-scoped ownership of a native door-guard cast; no native symbols or timers. */
public final class HungMazeCombatController {
    private Object player, target;
    private int map = -1, zone = -1;

    public synchronized void claim(Object actor, Object guard, int mapId, int zoneId) {
        player = actor;
        target = guard;
        map = mapId;
        zone = zoneId;
    }

    public synchronized boolean owns(Object actor, int mapId, int zoneId) {
        return player != null && target != null && actor == player && mapId == map && zoneId == zone;
    }

    public synchronized Object player() { return player; }
    public synchronized Object target() { return target; }

    public synchronized void release() {
        player = target = null;
        map = zone = -1;
    }
}
