package luna.api;

/** Reusable mutable view of the active NPC/mod menu. */
public final class LunaMenuView {

    private boolean open;
    private int menuId;
    private String title;
    private int optionCount;
    private int selectedIndex;

    public void clear() {
        open = false;
        menuId = 0;
        title = null;
        optionCount = 0;
        selectedIndex = -1;
    }

    public boolean isOpen() { return open; }
    public int getMenuId() { return menuId; }
    public String getTitle() { return title; }
    public int getOptionCount() { return optionCount; }
    public int getSelectedIndex() { return selectedIndex; }

    public LunaMenuView setOpen(boolean value) { open = value; return this; }
    public LunaMenuView setMenuId(int value) { menuId = value; return this; }
    public LunaMenuView setTitle(String value) { title = value; return this; }
    public LunaMenuView setOptionCount(int value) { optionCount = value; return this; }
    public LunaMenuView setSelectedIndex(int value) { selectedIndex = value; return this; }
}
