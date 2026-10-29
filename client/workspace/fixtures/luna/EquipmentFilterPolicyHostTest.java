import luna.features.EquipmentFilterPolicy;

/** Exhaustive host checks for native equipment type classification and filter predicates. */
public final class EquipmentFilterPolicyHostTest {
    private static int checks;

    private EquipmentFilterPolicyHostTest() { }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static int expectedGroup(int slot) {
        switch (slot) {
            case 8:
            case 9:
                return EquipmentFilterPolicy.STRENGTH;
            case 4:
            case 5:
                return EquipmentFilterPolicy.DEXTERITY;
            case 0:
            case 1:
            case 2:
            case 3:
            case 6:
                return EquipmentFilterPolicy.VITALITY;
            case 10:
            case 11:
                return EquipmentFilterPolicy.SPIRIT;
            default:
                return 0;
        }
    }

    private static int expectedGroup(int slot, int job) {
        return expectedGroup(slot);
    }

    public static void main(String[] args) {
        int[] jobs = {-1, 0, 1, 2, 3, 4, -2, Integer.MIN_VALUE, Integer.MAX_VALUE};
        int[] extraSlots = {-1, 15, Integer.MIN_VALUE, Integer.MAX_VALUE};
        int[] masks = {-1, Integer.MIN_VALUE, Integer.MAX_VALUE};
        int[] lockFlags = {-1, 0, 1, 2, Integer.MAX_VALUE};

        check(EquipmentFilterPolicy.STRENGTH == 1, "strength bit");
        check(EquipmentFilterPolicy.DEXTERITY == 2, "dexterity bit");
        check(EquipmentFilterPolicy.VITALITY == 4, "vitality bit");
        check(EquipmentFilterPolicy.SPIRIT == 8, "spirit bit");
        check(EquipmentFilterPolicy.ALL == 15, "all groups mask");
        // Native wear slots: type 6 -> slot 8 (boots); type 5 -> slot 4 (necklace).
        // Verified with enlarged 20px frames of the bundled screentab6.png, not item options.
        check(EquipmentFilterPolicy.groupFor(6, -1) == EquipmentFilterPolicy.VITALITY,
                "boots occupy the footwear slot and belong to vitality");
        check(EquipmentFilterPolicy.groupFor(5, -1) == EquipmentFilterPolicy.DEXTERITY,
                "necklace occupies the necklace slot and belongs to dexterity");
        check(EquipmentFilterPolicy.acceptsSale(true, false, 0, EquipmentFilterPolicy.VITALITY, 6, -1),
                "unlocked blue boots pass vitality-only when native quality passes");
        check(!EquipmentFilterPolicy.acceptsSale(true, false, 0, EquipmentFilterPolicy.VITALITY, 5, -1),
                "necklace does not pass vitality-only");

        for (int slot = 0; slot <= 14; slot++) {
            for (int j = 0; j < jobs.length; j++) {
                int expected = expectedGroup(slot, jobs[j]);
                check(EquipmentFilterPolicy.groupFor(slot, jobs[j]) == expected,
                        "group slot=" + slot + " job=" + jobs[j]);
            }
        }
        for (int i = 0; i < extraSlots.length; i++) {
            for (int j = 0; j < jobs.length; j++) {
                check(EquipmentFilterPolicy.groupFor(extraSlots[i], jobs[j]) == 0,
                        "unknown slot=" + extraSlots[i] + " job=" + jobs[j]);
            }
        }

        // All 4-bit masks against each type and job value; the job does not affect classification.
        for (int slot = 0; slot <= 14; slot++) {
            for (int j = 0; j < jobs.length; j++) {
                int group = expectedGroup(slot, jobs[j]);
                for (int mask = 0; mask <= EquipmentFilterPolicy.ALL; mask++) {
                    check(EquipmentFilterPolicy.accepts(mask, slot, jobs[j])
                                    == (group != 0 && (mask & group) != 0),
                            "accept mask=" + mask + " slot=" + slot + " job=" + jobs[j]);
                }
            }
        }
        for (int i = 0; i < extraSlots.length; i++) {
            for (int j = 0; j < jobs.length; j++) {
                for (int mask = 0; mask <= EquipmentFilterPolicy.ALL; mask++) {
                    check(!EquipmentFilterPolicy.accepts(mask, extraSlots[i], jobs[j]),
                            "unknown slot accepted");
                }
            }
        }

        // High or negative mask bits cannot select a group on their own.
        for (int slot = 0; slot <= 14; slot++) {
            for (int j = 0; j < jobs.length; j++) {
                int group = expectedGroup(slot, jobs[j]);
                for (int m = 0; m < masks.length; m++) {
                    check(EquipmentFilterPolicy.accepts(masks[m], slot, jobs[j])
                                    == (group != 0 && (masks[m] & group) != 0),
                            "extended mask=" + masks[m] + " slot=" + slot + " job=" + jobs[j]);
                }
            }
        }

        // Every quality/lock/mask/group combination for each native equipment type and job value.
        for (int slot = 0; slot <= 14; slot++) {
            for (int j = 0; j < jobs.length; j++) {
                int group = expectedGroup(slot, jobs[j]);
                for (int mask = 0; mask <= EquipmentFilterPolicy.ALL; mask++) {
                    for (int quality = 0; quality <= 1; quality++) {
                        for (int lockedOnly = 0; lockedOnly <= 1; lockedOnly++) {
                            for (int k = 0; k < lockFlags.length; k++) {
                                boolean expected = quality != 0
                                        && (lockedOnly == 0 || lockFlags[k] > 0)
                                        && group != 0 && (mask & group) != 0;
                                boolean actual = EquipmentFilterPolicy.acceptsSale(
                                        quality != 0, lockedOnly != 0, lockFlags[k], mask, slot, jobs[j]);
                                check(actual == expected,
                                        "sale quality=" + quality + " locked=" + lockedOnly
                                                + " lock=" + lockFlags[k] + " mask=" + mask
                                                + " slot=" + slot + " job=" + jobs[j]);
                            }
                        }
                    }
                }
            }
        }

        // Default ALL mask and unlocked-only policy preserve native quality decisions.
        for (int slot = 0; slot <= 14; slot++) {
            int group = expectedGroup(slot);
            if (group == 0) continue;
            for (int j = 0; j < jobs.length; j++) {
                int job = jobs[j];
                for (int quality = 0; quality <= 1; quality++) {
                    for (int lock = -1; lock <= 1; lock++) {
                        check(EquipmentFilterPolicy.acceptsSale(quality != 0, false, lock,
                                        EquipmentFilterPolicy.ALL, slot, job) == (quality != 0),
                                "default preserves quality slot=" + slot + " job=" + job);
                    }
                }
            }
        }
        check(!EquipmentFilterPolicy.accepts(0, 8, 0), "zero mask accepts nothing");
        check(EquipmentFilterPolicy.acceptsSale(true, true, 1, EquipmentFilterPolicy.ALL,
                8, Integer.MAX_VALUE), "positive lock retains universal equipment");
        check(!EquipmentFilterPolicy.acceptsSale(true, true, 0, EquipmentFilterPolicy.ALL,
                8, 0), "zero lock rejects in locked-only mode");

        System.out.println("EquipmentFilterPolicyHostTest PASS checks=" + checks);
    }
}
