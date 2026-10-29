package luna.ui;

/** Reusable semantic HUD state; rendering remains in the generated UI hook. */
public final class HudState {

    private String feature;
    private String phase;
    private boolean visible;

    public void clear() {
        feature = null;
        phase = null;
        visible = false;
    }

    public String getFeature() { return feature; }
    public String getPhase() { return phase; }
    public boolean isVisible() { return visible; }

    public HudState setFeature(String value) { feature = value; return this; }
    public HudState setPhase(String value) { phase = value; return this; }
    public HudState setVisible(boolean value) { visible = value; return this; }
}
