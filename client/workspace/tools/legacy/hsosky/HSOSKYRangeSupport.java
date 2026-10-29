/** Client-side experimental cast-range adjustment for melee classes. */
public final class HSOSKYRangeSupport {
    private static final int TEST_RANGE = 120;

    private HSOSKYRangeSupport() {
    }

    public static int adjustRange(int originalRange, int playerClass) {
        // 0 = Warrior, 1 = Assassin, 2 = Mage, 3 = Gunner.
        if ((playerClass == 0 || playerClass == 1) && originalRange < TEST_RANGE) {
            return TEST_RANGE;
        }
        return originalRange;
    }
}
