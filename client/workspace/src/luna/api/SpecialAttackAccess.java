package luna.api;

/** Native configured skill slots and ordinary eligibility, before the special delay. */
public interface SpecialAttackAccess {
    int specialSlotCount();
    int specialSkillAt(int slot);
    boolean specialSkillReady(int skill);
    boolean specialChoose(int slot, boolean farm);
}
