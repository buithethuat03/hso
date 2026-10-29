package luna.api;

/** Reusable mutable view of the currently open shop. */
public final class LunaShopView {

    private boolean open;
    private int itemCount;
    private String shopName;
    private String itemName;
    private int itemStock;
    private int itemPrice;

    public void clear() {
        open = false;
        itemCount = 0;
        shopName = null;
        itemName = null;
        itemStock = 0;
        itemPrice = 0;
    }

    public boolean isOpen() { return open; }
    public int getItemCount() { return itemCount; }
    public String getShopName() { return shopName; }
    public String getItemName() { return itemName; }
    public int getItemStock() { return itemStock; }
    public int getItemPrice() { return itemPrice; }

    public LunaShopView setOpen(boolean value) { open = value; return this; }
    public LunaShopView setItemCount(int value) { itemCount = value; return this; }
    public LunaShopView setShopName(String value) { shopName = value; return this; }
    public LunaShopView setItemName(String value) { itemName = value; return this; }
    public LunaShopView setItemStock(int value) { itemStock = value; return this; }
    public LunaShopView setItemPrice(int value) { itemPrice = value; return this; }
}
