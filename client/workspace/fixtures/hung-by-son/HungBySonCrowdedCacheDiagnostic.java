import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import luna.api.MonsterCacheAccess;
import luna.features.MonsterCacheSupport;

/** Read-only reproduction against the support loaded from the candidate JAR.
 * Reuses the cache host fixture's modeled native boundary; no live server or
 * actual renderer is involved. Assertions describe the observed bug, not the
 * desired behavior, so this diagnostic is not part of release acceptance.
 */
public final class HungBySonCrowdedCacheDiagnostic {
    private static final String FIXTURE = "HungBySonMonsterCacheHostTest$";

    private static Object create(String name, Class<?>[] types, Object... values) throws Exception {
        Constructor<?> constructor = Class.forName(FIXTURE + name).getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return constructor.newInstance(values);
    }

    private static Field field(Object object, String name) throws Exception {
        Field value = object.getClass().getDeclaredField(name);
        value.setAccessible(true);
        return value;
    }

    private static void set(Object object, String name, Object value) throws Exception {
        field(object, name).set(object, value);
    }

    private static int hp(Object monster) throws Exception {
        return field(monster, "hp").getInt(monster);
    }

    @SuppressWarnings("unchecked")
    private static Object learn(MonsterCacheAccess access, int id) throws Exception {
        Object monster = create("FakeMonster",
                new Class<?>[]{int.class, int.class, int.class, int.class, boolean.class},
                id, 5, 30, 40, false);
        set(monster, "hp", 50);
        set(monster, "maxHp", 100);
        ((Map<Integer, Object>)field(access, "monsters").get(access)).put(id, monster);
        MonsterCacheSupport.onObject(access, 1, 5, id, 30, 40, 5);
        MonsterCacheSupport.onDetail(access, id, 5, 30, 40, 50, 100);
        return monster;
    }

    private static MonsterCacheAccess fresh() throws Exception {
        MonsterCacheSupport.resetForTest();
        Object store = create("FakeStore", new Class<?>[0]);
        MonsterCacheAccess access = (MonsterCacheAccess)create("FakeAccess",
                new Class<?>[]{store.getClass(), String.class, int.class, int.class},
                store, "crowded-diagnostic", 40, 1);
        MonsterCacheSupport.onMap(access);
        return access;
    }

    private static void detail(MonsterCacheAccess access, Object monster, int id, int value)
            throws Exception {
        // Native packet-7 writes HP before calling the support observer.
        set(monster, "hp", value);
        MonsterCacheSupport.onDetail(access, id, 5, 30, 40, value, 100);
    }

    private static DataInputStream combat(int id, int hp) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(777); // attacker
        out.writeByte(19); // skill
        out.writeByte(1); // victim count
        out.writeShort(id);
        out.writeInt(13); // damage
        out.writeInt(hp);
        out.writeByte(0); // effects
        out.writeInt(1234); out.writeInt(567); out.writeByte(2); out.writeInt(8);
        out.flush();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        MonsterCacheAccess access = fresh();
        Object monster = learn(access, 41);
        detail(access, monster, 41, 0);
        require(MonsterCacheSupport.status().contains("K:1"), "death retains a corpse");
        MonsterCacheSupport.onCombatSnapshot(access, combat(41, 37));
        require(hp(monster) == 37, "combat snapshot successfully writes positive server HP");
        System.out.println("COMBAT_POSITIVE: HP=" + hp(monster) + "; "
                + MonsterCacheSupport.status().replace('\n', ' '));
        MonsterCacheSupport.onEntityUpdate(access, monster);
        require(hp(monster) == 0, "corpse hook overwrites positive combat HP");
        System.out.println("NEXT_WORLD_UPDATE: HP=" + hp(monster) + "; "
                + MonsterCacheSupport.status().replace('\n', ' '));
        detail(access, monster, 41, 37);
        MonsterCacheSupport.onEntityUpdate(access, monster);
        require(hp(monster) == 37 && MonsterCacheSupport.status().contains("K:0"),
                "valid detail releases corpse and preserves positive HP");
        System.out.println("VALID_DETAIL_CONTROL: HP=37; K=0");

        access = fresh();
        Object first = learn(access, 41);
        Object second = learn(access, 42);
        detail(access, first, 41, 0);
        detail(access, second, 42, 0);
        set(access, "now", 3000L);
        MonsterCacheSupport.tick(access); // first recovery is queued and requested
        set(access, "now", 3100L);
        MonsterCacheSupport.tick(access); // second recovery queued, global send gate still closed
        require(MonsterCacheSupport.status().contains("W:2"), "two recoveries queued");
        detail(access, second, 42, 37); // valid late/native reply for unsent queued recovery
        MonsterCacheSupport.onEntityUpdate(access, second);
        require(hp(second) == 0 && MonsterCacheSupport.status().contains("W:2"),
                "positive unsolicited detail cannot release unsent pending recovery");
        System.out.println("UNSENT_RECOVERY_POSITIVE_DETAIL: HP=0; W=2 (reply ignored)");
        System.out.println("DIAGNOSTIC_REPRO=PASS; candidate support with modeled native boundary");
    }
}
