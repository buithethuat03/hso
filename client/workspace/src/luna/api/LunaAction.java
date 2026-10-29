package luna.api;

/** Reusable command object passed from semantic controllers to the bridge. */
public final class LunaAction {

    public static final int NONE = 0;
    public static final int MOVE = 1;
    public static final int MENU_REPLY = 2;
    public static final int REVIVE = 3;
    public static final int TAP = 4;

    private int type;
    private int first;
    private int second;
    private int third;
    private String text;

    public LunaAction clear() {
        type = NONE;
        first = 0;
        second = 0;
        third = 0;
        text = null;
        return this;
    }

    public int getType() { return type; }
    public int getFirst() { return first; }
    public int getSecond() { return second; }
    public int getThird() { return third; }
    public String getText() { return text; }

    public LunaAction setType(int value) { type = value; return this; }
    public LunaAction setFirst(int value) { first = value; return this; }
    public LunaAction setSecond(int value) { second = value; return this; }
    public LunaAction setThird(int value) { third = value; return this; }
    public LunaAction setText(String value) { text = value; return this; }
}
