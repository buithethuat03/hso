package luna.api;

/** Only mode and cadence are persistent; enabled and combat state are session state. */
public interface AutoAttackSettings {
    void load(int[] settings);
    void save(boolean players, int delay);
}
