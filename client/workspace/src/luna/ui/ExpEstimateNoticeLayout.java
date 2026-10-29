package luna.ui;

/** Preserves the original native notice coordinates for the compact player panel. */
public final class ExpEstimateNoticeLayout {
    private ExpEstimateNoticeLayout() { }

    public static int baseY(int nativeY, boolean playerPanelVisible) {
        return nativeY;
    }
}
