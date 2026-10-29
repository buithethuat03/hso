package luna.features;

/** Pure stat-group policy for equipment filters. */
public final class EquipmentFilterPolicy {
    public static final int STRENGTH = 1;
    public static final int DEXTERITY = 2;
    public static final int VITALITY = 4;
    public static final int SPIRIT = 8;
    public static final int ALL = STRENGTH | DEXTERITY | VITALITY | SPIRIT;

    private EquipmentFilterPolicy() { }

    /** Returns the stat group for a native equipment type, or zero if the type is unknown. */
    public static int groupFor(int equipmentSlot, int equipmentJob) {
        switch (equipmentSlot) {
            case 8:
            case 9:
                return STRENGTH;
            case 4:
            case 5:
                return DEXTERITY;
            case 0:
            case 1:
            case 2:
            case 3:
            case 6:
                return VITALITY;
            case 10:
            case 11:
                return SPIRIT;
            default:
                return 0;
        }
    }

    public static boolean accepts(int mask, int equipmentSlot, int equipmentJob) {
        int group = groupFor(equipmentSlot, equipmentJob);
        return group != 0 && (mask & group) != 0;
    }

    public static boolean acceptsSale(boolean nativeQualityAccepted, boolean lockedOnly,
            int lockFlag, int mask, int equipmentSlot, int equipmentJob) {
        return nativeQualityAccepted && (!lockedOnly || lockFlag > 0)
                && accepts(mask, equipmentSlot, equipmentJob);
    }
}
