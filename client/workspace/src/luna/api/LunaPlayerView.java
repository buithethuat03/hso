package luna.api;

/**
 * Reusable mutable snapshot of the local player.
 *
 * The bridge fills one instance per frame. Controllers must not retain a
 * reference to a bridge-owned snapshot after the next read.
 */
public final class LunaPlayerView {

    private int mapId;
    private int x;
    private int y;
    private int hp;
    private int maxHp;
    private boolean alive;
    private boolean moving;

    public void clear() {
        mapId = 0;
        x = 0;
        y = 0;
        hp = 0;
        maxHp = 0;
        alive = false;
        moving = false;
    }

    public int getMapId() { return mapId; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public boolean isAlive() { return alive; }
    public boolean isMoving() { return moving; }

    public LunaPlayerView setMapId(int value) { mapId = value; return this; }
    public LunaPlayerView setX(int value) { x = value; return this; }
    public LunaPlayerView setY(int value) { y = value; return this; }
    public LunaPlayerView setHp(int value) { hp = value; return this; }
    public LunaPlayerView setMaxHp(int value) { maxHp = value; return this; }
    public LunaPlayerView setAlive(boolean value) { alive = value; return this; }
    public LunaPlayerView setMoving(boolean value) { moving = value; return this; }
}
