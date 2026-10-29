package luna.api;

/** Reusable mutable snapshot of one visible world entity. */
public final class LunaEntityView {

    private int id;
    private int kind;
    private int x;
    private int y;
    private int hp;
    private boolean visible;
    private boolean hostile;

    public void clear() {
        id = 0;
        kind = 0;
        x = 0;
        y = 0;
        hp = 0;
        visible = false;
        hostile = false;
    }

    public int getId() { return id; }
    public int getKind() { return kind; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getHp() { return hp; }
    public boolean isVisible() { return visible; }
    public boolean isHostile() { return hostile; }

    public LunaEntityView setId(int value) { id = value; return this; }
    public LunaEntityView setKind(int value) { kind = value; return this; }
    public LunaEntityView setX(int value) { x = value; return this; }
    public LunaEntityView setY(int value) { y = value; return this; }
    public LunaEntityView setHp(int value) { hp = value; return this; }
    public LunaEntityView setVisible(boolean value) { visible = value; return this; }
    public LunaEntityView setHostile(boolean value) { hostile = value; return this; }
}
