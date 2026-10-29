import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Candidate-level Auto PK integration checks.  Uses the real native player predicate and attack
 * serializers through HungBySonAutoAttackBinaryTest's isolated native-world probe.
 */
public final class HungBySonAutoPkBinaryTest {
    private static final String HELPER = "HungBySonAutoAttackBinaryTest";
    private static final int ACTION_TOGGLE = -111, ACTION_MONSTERS = -112,
            ACTION_PK = -113, ACTION_SPECIAL = -116;
    private static final int SKILL = 8;
    private static int checks;
    private static ClassLoader loader;
    private static Class<?> port, stats, runtime, entityType, playerType;
    private static Object world, player;
    private static volatile long now = 1000000L;
    private static String portOwner = "HungBySonModUpLv";
    private static String statsOwner = "HungBySonSkillStats";
    /** Catalogued Auto PK radius in world pixels (the build sets auto.pk.radius). */
    private static int radius = 400;
    private static boolean serpentChecks;
    private static boolean hpOrderChecks;
    private static boolean targetLockChecks;
    private static boolean focusLinesChecks;
    private static Field nativeFocus;

    private HungBySonAutoPkBinaryTest() { }

    private static void require(boolean value, String text) {
        checks++;
        if (!value) throw new AssertionError(text);
        System.out.println("PASS " + text);
    }

    private static String n(String suffix) {
        StringBuilder value = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) value.append('i');
        return value.append(suffix).toString();
    }

    private static Object helper(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Class<?> host = Class.forName(HELPER);
        Method method = host.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return invoke(method, null, args);
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try { return method.invoke(target, args); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new IllegalStateException(cause);
        }
    }

    private static Object construct(Class<?> type, Class<?>[] parameterTypes, Object... args) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private static Object call(Object receiver, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = receiver.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return invoke(method, receiver, args);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field result = cursor.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static void setNumber(Object target, Class<?> owner, String name, long value) throws Exception {
        Field member = field(owner, name);
        Class<?> type = member.getType();
        if (type == Byte.TYPE) member.setByte(target, (byte)value);
        else if (type == Short.TYPE) member.setShort(target, (short)value);
        else if (type == Integer.TYPE) member.setInt(target, (int)value);
        else if (type == Long.TYPE) member.setLong(target, value);
        else throw new IllegalArgumentException(member.toString());
    }

    private static void setField(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field member = field(owner, name);
        if (member.getType() == Boolean.TYPE) member.setBoolean(target, ((Boolean)value).booleanValue());
        else if (member.getType() == Byte.TYPE) member.setByte(target, ((Byte)value).byteValue());
        else if (member.getType() == Short.TYPE) member.setShort(target, ((Short)value).shortValue());
        else if (member.getType() == Integer.TYPE) member.setInt(target, ((Integer)value).intValue());
        else if (member.getType() == Long.TYPE) member.setLong(target, ((Long)value).longValue());
        else member.set(target, value);
    }

    private static long number(Object target, Class<?> owner, String name) throws Exception {
        return ((Number)field(owner, name).get(target)).longValue();
    }

    private static Object newList(String title) throws Exception {
        return helper("newNativeList", new Class<?>[]{Class.class, String.class},
                loader.loadClass("et"), title);
    }

    private static int listSize(Object list) throws Exception {
        return ((Integer)helper("listSize", new Class<?>[]{Object.class}, list)).intValue();
    }

    private static Object listGet(Object list, int index) throws Exception {
        return helper("listGet", new Class<?>[]{Object.class, Integer.TYPE}, list, Integer.valueOf(index));
    }

    private static Properties load(File file) throws Exception {
        Properties result = new Properties();
        InputStream input = new FileInputStream(file);
        try { result.load(input); } finally { input.close(); }
        return result;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().length() == 0) throw new IllegalArgumentException("Missing catalog key " + key);
        return value.trim();
    }

    private static void setStatic(Class<?> owner, String name, Object value) throws Exception {
        field(owner, name).set(null, value);
    }

    private static Class<?> cls(String name) throws Exception {
        return loader.loadClass(name.replace('/', '.'));
    }

    private static Object nativeEntity(int id, byte kind, int x, int y, String name,
            int level, boolean hostile) throws Exception {
        Object entity = call(world, "entity",
                new Class<?>[]{Integer.TYPE, Byte.TYPE, Integer.TYPE, Integer.TYPE,
                        Integer.TYPE, Integer.TYPE, Integer.TYPE},
                Integer.valueOf(100), Byte.valueOf(kind), Integer.valueOf(2), Integer.valueOf(0),
                Integer.valueOf(hostile ? 1 : 0), Integer.valueOf(x), Integer.valueOf(y));
        setNumber(entity, entityType, "cu", id);
        setNumber(entity, entityType, "cv", kind);
        setNumber(entity, entityType, "aZ", x);
        setNumber(entity, entityType, "ba", y);
        setNumber(entity, entityType, "bz", level);
        setField(entity, entityType, "cC", name);
        return entity;
    }

    private static Object monster(int id, int x, int y) throws Exception {
        return nativeEntity(id, (byte)1, x, y, "Monster " + id, 1, false);
    }

    private static Object hostile(int id, String name, int level, int x, int y) throws Exception {
        return nativeEntity(id, (byte)0, x, y, name, level, true);
    }

    private static void replaceWorld(Object... values) throws Exception {
        call(world, "replaceEntities", new Class<?>[]{Object[].class}, (Object)values);
    }

    private static Object playerTargets() throws Exception {
        Method select = port.getDeclaredMethod("nearestPlayers", playerType);
        select.setAccessible(true);
        return invoke(select, null, player);
    }

    /** Each target row is the native (short id, byte kind) structure used by opcode 6/9. */
    private static int[] targetIds(Object targets) throws Exception {
        int[] result = new int[listSize(targets)];
        for (int i = 0; i < result.length; i++) {
            Object row = listGet(targets, i);
            int id = Integer.MIN_VALUE;
            for (Class<?> cursor = row.getClass(); cursor != null; cursor = cursor.getSuperclass()) {
                for (Field member : cursor.getDeclaredFields()) {
                    if (Modifier.isStatic(member.getModifiers()) || member.getType() != Short.TYPE) continue;
                    member.setAccessible(true);
                    int value = member.getShort(row);
                    if (value > 0) { id = value; break; }
                }
                if (id != Integer.MIN_VALUE) break;
            }
            if (id == Integer.MIN_VALUE) throw new AssertionError("Native target row has no positive short ID: " + row.getClass());
            result[i] = id;
        }
        return result;
    }

    private static void expectIds(int[] expected, Object targets, String message) throws Exception {
        int[] actual = targetIds(targets);
        require(Arrays.equals(expected, actual), message + " expected=" + Arrays.toString(expected)
                + " actual=" + Arrays.toString(actual));
    }

    private static int[] ids(int start, int count) {
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = start + i;
        return result;
    }

    private static void validNativeTarget(Object target, boolean expected, Object bridge, String message)
            throws Exception {
        Method valid = bridge.getClass().getMethod("validTarget", Object.class, Boolean.TYPE);
        require(Boolean.valueOf(expected).equals(valid.invoke(bridge, target, Boolean.TRUE)), message);
    }

    private static void setupSkills() throws Exception {
        Class<?> skillOwner = cls(n("lillilll"));
        Class<?> skillType = cls(n("liiiiill"));
        Class<?> levelType = cls(n("liiilill"));
        Object level = allocate(levelType);
        setNumber(level, levelType, n("lilllli"), 10);
        setNumber(level, levelType, "j", 5);
        setNumber(level, levelType, "g", 1200);
        Object levelArray = Array.newInstance(levelType, 1);
        Array.set(levelArray, 0, level);
        Object definition = allocate(skillType);
        setNumber(definition, skillType, "b", SKILL);
        setNumber(definition, skillType, "e", 0);
        field(skillType, n("lllillllil")).set(definition, levelArray);
        field(skillType, n("lllilllil")).set(definition, "Auto PK fixture");
        Object skillList = newList("Auto PK learned skills");
        Method add = skillList.getClass().getMethod(n("l"), Object.class);
        add.setAccessible(true);
        add.invoke(skillList, definition);
        setStatic(skillOwner, "a", skillList);

        Class<?> skills = cls(n("lliiil"));
        int[] learned = new int[21], bonus = new int[21];
        learned[SKILL] = 1;
        setStatic(skills, n("llilllii"), learned);
        setStatic(skills, n("llilllil"), bonus);
        Field cooldownField = field(skills, n("lliillli"));
        Class<?> cooldownType = cooldownField.getType().getComponentType();
        Object cooldownArray = Array.newInstance(cooldownType, 21);
        for (int i = 0; i < 21; i++) Array.set(cooldownArray, i, allocate(cooldownType));
        cooldownField.set(null, cooldownArray);
    }

    private static Object cooldown(int skill) throws Exception {
        Object array = field(cls(n("lliiil")), n("lliillli")).get(null);
        return Array.get(array, skill);
    }

    private static void cooldown(int skill, int value) throws Exception {
        setNumber(cooldown(skill), cooldown(skill).getClass(), "b", value);
    }

    private static int casts(int skill) throws Exception {
        return ((Integer)cls("luna.features.SkillCastStats").getMethod("casts", Integer.TYPE)
                .invoke(null, Integer.valueOf(skill))).intValue();
    }

    private static void at(long value) throws Exception {
        now = value;
        field(port, "testNow").setLong(null, value);
        field(stats, "testNow").setLong(null, value);
        Field hostClock = field(Class.forName(HELPER), "nativeRequestNow");
        hostClock.setLong(null, value);
    }

    private static void frame(long value) throws Exception {
        at(value);
        port.getMethod("frame").invoke(null);
    }

    @SuppressWarnings("unchecked")
    private static List<String> drainPackets() throws Exception {
        Field packetsField = field(Class.forName(HELPER), "nativePackets");
        List<String> packets = (List<String>)packetsField.get(null);
        synchronized (packets) {
            List<String> result = new ArrayList<String>(packets);
            packets.clear();
            return result;
        }
    }

    private static String attackPacket(int opcode, int skill, int... ids) {
        StringBuilder packet = new StringBuilder().append(opcode).append(":[")
                .append((byte)skill).append(", ").append((byte)ids.length);
        for (int id : ids) packet.append(", ").append((byte)(id >> 8)).append(", ").append((byte)id);
        return packet.append(']').toString();
    }

    private static void invokeAction(int action) throws Exception {
        Object result = runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(action));
        require(Boolean.TRUE.equals(result), "Auto Attack runtime accepts action " + action);
    }

    private static void setBattlefield(boolean enabled) throws Exception {
        Class<?> support = cls("luna.features.BattlefieldSupport");
        support.getMethod("setEnabled", Boolean.TYPE).invoke(null, Boolean.valueOf(enabled));
    }

    private static void seedExcludedName(Object victim) throws Exception {
        Class<?> support = cls("luna.features.BattlefieldSupport");
        support.getMethod("resetForTest").invoke(null);
        support.getMethod("setEnabled", Boolean.TYPE).invoke(null, Boolean.TRUE);
        Class<?> bridge = cls("luna.generated.BattlefieldBridge");
        Object access = bridge.getField("INSTANCE").get(null);
        Class<?> api = cls("luna.api.BattlefieldAccess");
        require(api.isInstance(access), "Candidate Battlefield singleton implements its native access API");
        int victimId = (int)number(victim, entityType, "cu");
        int attackerId = (int)number(player, entityType, "cu");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeShort(attackerId); output.writeByte(SKILL); output.writeByte(1);
        output.writeShort(victimId); output.writeInt(1); output.writeInt(0); output.writeByte(0);
        output.writeInt(100); output.writeInt(100); output.writeByte(0); output.writeInt(0);
        output.flush();
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        support.getMethod("onCombat", api, DataInputStream.class, Integer.TYPE)
                .invoke(null, access, input, Integer.valueOf(0));
        require(((Integer)support.getMethod("count").invoke(null)).intValue() == 1
                && Boolean.TRUE.equals(support.getMethod("excluded", String.class)
                        .invoke(null, (String)field(entityType, "cC").get(victim))),
                "Actual local final-hit processing records a non-level-40 PK name in the excluded FIFO");
    }

    private static Object entityWithNativeXFlag(Object bridge) throws Exception {
        Object target = hostile(399, "Native-x", 28, 40, 0);
        Method x = null;
        for (Class<?> cursor = entityType; cursor != null && x == null; cursor = cursor.getSuperclass()) {
            try { x = cursor.getDeclaredMethod("x"); x.setAccessible(true); }
            catch (NoSuchMethodException absent) { }
        }
        if (x == null) throw new NoSuchMethodException(entityType.getName() + ".x()Z");
        if (Boolean.TRUE.equals(invoke(x, target))) return target;

        @SuppressWarnings("unchecked")
        List<Field> fields = (List<Field>)helper("allFields", new Class<?>[]{Class.class}, entityType);
        for (Field member : fields) {
            if (Modifier.isStatic(member.getModifiers()) || member.getType().isArray()
                    || member.getName().equals("bs") || member.getName().equals("cG")
                    || member.getName().equals("dz") || member.getName().equals("cy")
                    || member.getName().equals("cL") || member.getName().equals("cu")
                    || member.getName().equals("cv") || member.getName().equals("aZ")
                    || member.getName().equals("ba")) continue;
            member.setAccessible(true);
            Object original = member.get(target);
            Object[] trials = trials(member.getType());
            for (Object trial : trials) {
                boolean keep = false;
                try {
                    setField(target, entityType, member.getName(), trial);
                    if (Boolean.TRUE.equals(invoke(x, target))) {
                        require(Boolean.TRUE.equals(bridge.getClass().getMethod("validTarget", Object.class,
                                Boolean.TYPE).invoke(bridge, target, Boolean.TRUE)) == false,
                                "A native x() rejection is exercised before testing the player-range selector");
                        keep = true;
                        return target;
                    }
                } catch (Exception ignored) { }
                finally { if (!keep) member.set(target, original); }
            }
        }
        throw new AssertionError("Could not configure a native player whose x() predicate is true");
    }

    private static Object[] trials(Class<?> type) {
        if (type == Boolean.TYPE) return new Object[]{Boolean.TRUE};
        if (type == Byte.TYPE) return new Object[]{Byte.valueOf((byte)1), Byte.valueOf((byte)2), Byte.valueOf((byte)3)};
        if (type == Short.TYPE) return new Object[]{Short.valueOf((short)1), Short.valueOf((short)2), Short.valueOf((short)3)};
        if (type == Integer.TYPE) return new Object[]{Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4)};
        if (type == Long.TYPE) return new Object[]{Long.valueOf(1L), Long.valueOf(2L), Long.valueOf(3L)};
        return new Object[0];
    }

    private static void verifyNativeSelection(Object bridge) throws Exception {
        setBattlefield(false);
        replaceWorld(player);
        expectIds(new int[0], playerTargets(), "Zero eligible players yield an empty native list");

        Object one = hostile(100, "One", 31, 100, 0);
        replaceWorld(player, one);
        expectIds(new int[]{100}, playerTargets(), "One native-valid player is returned");

        List<Object> exactly20 = new ArrayList<Object>();
        exactly20.add(player);
        for (int i = 0; i < 20; i++) exactly20.add(hostile(200 + i, "P" + i, 21, 20 + i * 10, 0));
        replaceWorld(exactly20.toArray());
        expectIds(ids(200, 20), playerTargets(), "Exactly 20 valid PK candidates are retained in nearest-first order");

        List<Object> many = new ArrayList<Object>();
        many.add(player);
        for (int i = 24; i >= 0; i--) many.add(hostile(300 + i, "Many" + i, 22, 25 + i * 11, 0));
        replaceWorld(many.toArray());
        expectIds(ids(300, 20), playerTargets(), "More than 20 candidates are sorted by native world distance then capped at 20");

        int outside = (int)Math.ceil(radius / Math.sqrt(2.0));
        while (2L * outside * outside <= (long)radius * radius) outside++;
        Object at399 = hostile(410, "Inside", 33, radius - 1, 0);
        Object at400 = hostile(411, "AtRadius", 33, radius, 0);
        Object at401 = hostile(412, "Outside", 33, radius + 1, 0);
        Object diagonal = hostile(413, "DiagonalBorder", 33, radius * 3 / 5, radius * 4 / 5);
        Object diagonalOutside = hostile(414, "DiagonalOutside", 33, outside, outside);
        replaceWorld(player, at401, diagonalOutside, at400, diagonal, at399);
        expectIds(new int[]{410, 411, 413}, playerTargets(),
                "Radius includes " + (radius - 1) + "px, exact " + radius + "px, and the " + (radius * 3 / 5) + "-"
                        + (radius * 4 / 5) + " diagonal, while excluding " + (radius + 1) + "px and " + outside + "-"
                        + outside + " outside");

        Object monster = monster(420, 10, 0);
        Object self = player;
        Object dead = hostile(421, "Dead", 29, 11, 0); setNumber(dead, entityType, "bs", 0);
        Object dz = hostile(422, "dz", 29, 12, 0); field(entityType, "dz").setBoolean(dz, true);
        Object removed = hostile(423, "cL", 29, 13, 0); field(entityType, "cL").setBoolean(removed, true);
        Object removedState = hostile(424, "RemovedState", 29, 14, 0); setNumber(removedState, entityType, "cG", 4);
        Object friendly = nativeEntity(425, (byte)0, 15, 0, "Friendly", 29, false);
        Object survivor = hostile(427, "Survivor", 29, 16, 0);
        replaceWorld(player, monster, dead, dz, removed, removedState, friendly, survivor);
        validNativeTarget(monster, false, bridge,
                "PK-only native boundary rejects monsters, including secondary native targets");
        validNativeTarget(dead, false, bridge, "Actual PK predicate rejects HP-zero players");
        validNativeTarget(removedState, false, bridge, "Actual PK predicate rejects native removed-state entities");
        validNativeTarget(friendly, false, bridge, "Actual PK predicate rejects native non-hostile players");
        expectIds(new int[]{427}, playerTargets(),
                "Auto PK never mixes monsters, self, dead/dz/cL/removed-state or native-invalid players");

        Object normal = hostile(430, "Normal", 33, 100, 0);
        Object level40 = hostile(431, "Level40", 40, radius, 0);
        replaceWorld(player, normal, level40);
        setBattlefield(false);
        expectIds(new int[]{430, 431}, playerTargets(), "Without Battlefield mode, PK targets stay nearest-first even at level 40");
        setBattlefield(true);
        expectIds(new int[]{431, 430}, playerTargets(), "Battlefield mode promotes eligible exact-level-40 players before nearer others");

        Object aligned=hostile(435,"Aligned",40,2000,0);
        field(entityType,"battlefieldPositionKnown").setBoolean(aligned,true);
        setNumber(aligned,entityType,"battlefieldX",200);setNumber(aligned,entityType,"battlefieldY",0);
        replaceWorld(player,aligned);
        expectIds(new int[]{435},playerTargets(),"Latest server position is aligned before the radius gate, including outside-to-inside movement");
        setNumber(aligned,entityType,"battlefieldX",radius+1);
        expectIds(new int[0],playerTargets(),"Latest server position outside "+radius+"px overrides an old inside position");

        Object beyond40 = hostile(432, "Far40", 40, radius + 1, 0);
        replaceWorld(player, normal, beyond40);
        expectIds(new int[]{430}, playerTargets(), "Level-40 at " + (radius + 1) + "px is filtered before Battlefield priority");

        Object victim = hostile(433, "Victim", 31, 25, 0);
        Object ordinary = hostile(434, "Ordinary", 31, 40, 0);
        replaceWorld(player, victim, ordinary);
        seedExcludedName(victim);
        expectIds(new int[]{434}, playerTargets(), "The actual excluded-name FIFO removes a killed PK target from the native list");
        setBattlefield(false);
    }

    private static void setupRuntime(Properties auto) throws Exception {
        String bridgeName = auto.getProperty("bridge.owner", auto.getProperty("native.menu.owner"));
        Class<?> bridgeType = cls(bridgeName);
        Object bridge = bridgeType.getDeclaredConstructor().newInstance();
        Class<?> accessType = cls("luna.api.AutoAttackAccess");
        Class<?> settingsType = cls("luna.api.AutoAttackSettings");
        Class<?> clockType = cls("luna.api.LunaClock");
        Class<?> nativeAccessType = Class.forName(HELPER + "$NativeAccess");
        world = construct(nativeAccessType, new Class<?>[]{ClassLoader.class, Object.class, Properties.class},
                loader, bridge, auto);
        call(world, "installReadyWorld", new Class<?>[0]);
        player = field(nativeAccessType, "player").get(world);
        entityType = cls(required(auto, "entity.owner"));
        playerType = cls(required(auto, "player.owner"));
        setNumber(player, entityType, "cu", 4242);
        setNumber(player, entityType, "cv", 0);
        setNumber(player, entityType, "aZ", 0);
        setNumber(player, entityType, "ba", 0);
        setNumber(player, entityType, "bu", 1000);
        setNumber(player, entityType, "bz", 1);
        setNumber(player, entityType, "cG", 0);
        setField(player, entityType, "cC", "Local PK");

        Object access = Proxy.newProxyInstance(loader, new Class<?>[]{accessType}, (InvocationHandler)world);
        final int[] saved = new int[]{0, 650};
        Object settings = Proxy.newProxyInstance(loader, new Class<?>[]{settingsType}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("load".equals(method.getName())) {
                    int[] target = (int[])args[0]; target[0] = saved[0]; target[1] = saved[1];
                } else if ("save".equals(method.getName())) {
                    saved[0] = ((Boolean)args[0]).booleanValue() ? 1 : 0;
                    saved[1] = ((Integer)args[1]).intValue();
                }
                return defaultValue(method.getReturnType());
            }
        });
        Object clock = Proxy.newProxyInstance(loader, new Class<?>[]{clockType}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("nowMillis".equals(method.getName())) return Long.valueOf(now);
                return defaultValue(method.getReturnType());
            }
        });
        runtime = cls("luna.features.AutoAttackRuntime");
        runtime.getMethod("installForTest", accessType, settingsType, clockType)
                .invoke(null, access, settings, clock);
        port = cls(portOwner);
        stats = cls(statsOwner);
        require(Boolean.TRUE.equals(bridgeType.getMethod("farmWorldReady").invoke(bridge)),
                "Native fixture has a connected, entered and living local player");
        setupSkills();
        Class<?> skillStats = cls("luna.features.SkillCastStats");
        skillStats.getMethod("setPaceMode", Integer.TYPE).invoke(null, Integer.valueOf(0));
        skillStats.getMethod("setRetry", Boolean.TYPE).invoke(null, Boolean.FALSE);
        skillStats.getMethod("reset").invoke(null);
        setStatic(port, "frameAt", Long.valueOf(0L));
        setStatic(port, "testNow", Long.valueOf(0L));
        setStatic(stats, "testNow", Long.valueOf(0L));
    }

    private static Object allocate(Class<?> type) throws Exception {
        return helper("unsafeAllocate", new Class<?>[]{Class.class}, type);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == Boolean.TYPE) return Boolean.FALSE;
        if (type == Byte.TYPE) return Byte.valueOf((byte)0);
        if (type == Short.TYPE) return Short.valueOf((short)0);
        if (type == Integer.TYPE) return Integer.valueOf(0);
        if (type == Long.TYPE) return Long.valueOf(0L);
        if (type == Float.TYPE) return Float.valueOf(0.0f);
        if (type == Double.TYPE) return Double.valueOf(0.0d);
        if (type == Character.TYPE) return Character.valueOf((char)0);
        return null;
    }

    private static void resetSkill(int skill) throws Exception {
        cooldown(skill, 0);
        setNumber(player, entityType, "bu", 1000);
    }

    private static void expectFramePacket(long time, int opcode, int[] targets, String message)
            throws Exception {
        resetSkill(SKILL);
        drainPackets();
        frame(time);
        List<String> packets = drainPackets();
        String expected = attackPacket(opcode, SKILL, targets);
        require(packets.contains(expected), message + " expected=" + expected + " packets=" + packets);
        require(casts(SKILL) == 1, "One native opcode-" + opcode + " send increments SkillCastStats once");
        require(number(player, entityType, "bu") == 990
                && number(cooldown(SKILL), cooldown(SKILL).getClass(), "b") == 1200
                && number(cooldown(SKILL), cooldown(SKILL).getClass(), "c") == 1200,
                "One opcode-" + opcode + " cast consumes MP and applies its level cooldown once");
        cls("luna.features.SkillCastStats").getMethod("reset").invoke(null);
    }

    private static void verifyModeTransitionAndPackets() throws Exception {
        setNumber(player, entityType, "aZ", 1000);
        setNumber(player, entityType, "ba", 1000);
        Object mob = monster(500, 1100, 1000);
        Object opponent = hostile(501, "Opponent", 35, 1000 + radius, 1000);
        Object outsideOpponent = hostile(502, "OutsideRadius", 35, 1001 + radius, 1000);
        replaceWorld(player, mob, opponent, outsideOpponent);
        invokeAction(ACTION_MONSTERS);
        invokeAction(ACTION_SPECIAL);
        require(Boolean.TRUE.equals(runtime.getMethod("normalMonstersActive").invoke(null))
                && ((String)runtime.getMethod("normalLabel").invoke(null)).contains("[BẬT]"),
                "The existing default normalMonstersActive path stays on for standalone Special");

        Field enteredField = field(Class.forName(HELPER), "nativeEnqueueEntered");
        Field releaseField = field(Class.forName(HELPER), "nativeEnqueueRelease");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        enteredField.set(null, entered); releaseField.set(null, release);
        final AtomicReference<Throwable> sendFailure = new AtomicReference<Throwable>();
        final AtomicReference<Throwable> modeFailure = new AtomicReference<Throwable>();
        final CountDownLatch modeStarted = new CountDownLatch(1);
        final CountDownLatch modeDone = new CountDownLatch(1);
        Thread sending = new Thread(new Runnable() {
            public void run() { try { frame(2000000L); } catch (Throwable failure) { sendFailure.set(failure); } }
        }, "Auto PK opcode-9 enqueue");
        Thread switching = new Thread(new Runnable() {
            public void run() {
                modeStarted.countDown();
                try { invokeAction(ACTION_PK); }
                catch (Throwable failure) { modeFailure.set(failure); }
                finally { modeDone.countDown(); }
            }
        }, "Auto PK mode handoff");
        try {
            drainPackets();
            sending.start();
            require(entered.await(8L, TimeUnit.SECONDS), "Monster Special reaches the native opcode-9 enqueue latch");
            switching.start();
            require(modeStarted.await(8L, TimeUnit.SECONDS), "PK mode transition starts while opcode 9 is in flight");
            require(!modeDone.await(150L, TimeUnit.MILLISECONDS),
                    "PK mode transition waits on requestLock while the current native send is enqueueing");
        } finally {
            release.countDown();
            sending.join(8000L);
            if (switching.isAlive()) switching.join(8000L);
            enteredField.set(null, null); releaseField.set(null, null);
        }
        if (sendFailure.get() != null) throw new AssertionError("Native monster attack failed", sendFailure.get());
        if (modeFailure.get() != null) throw new AssertionError("PK mode transition failed", modeFailure.get());
        require(!sending.isAlive() && !switching.isAlive(), "Both the attack frame and serialized mode action finish");
        List<String> oldMode = drainPackets();
        require(oldMode.size() == 1 && oldMode.get(0).startsWith("9:["),
                "The in-flight frame keeps its monster target and emits opcode 9 only: " + oldMode);
        require(number(player, entityType, "bu") == 990
                && number(cooldown(SKILL), cooldown(SKILL).getClass(), "b") == 1200
                && casts(SKILL) == 1,
                "The locked in-flight opcode-9 request consumes MP and records its cooldown exactly once");

        cls("luna.features.SkillCastStats").getMethod("reset").invoke(null);
        expectFramePacket(2000700L, 6, new int[]{501}, "Auto PK opcode 6 sends the exact-" + radius + "px player and excludes the " + (radius + 1) + "px player");

        invokeAction(ACTION_TOGGLE);
        require(!((Boolean)runtime.getMethod("enabled").invoke(null)).booleanValue()
                && ((Boolean)runtime.getMethod("specialEnabled").invoke(null)).booleanValue(),
                "Master OFF leaves standalone Special ON");
        cls("luna.features.SkillCastStats").getMethod("reset").invoke(null);
        expectFramePacket(2001400L, 6, new int[]{501}, "Standalone Special keeps Auto PK active with master OFF");

        List<Object> crowded=new ArrayList<Object>();crowded.add(player);crowded.add(mob);
        crowded.add(hostile(699,"Near ordinary",35,1001,1000));
        for(int i=24;i>=0;i--)crowded.add(hostile(700+i,"War"+i,40,1020+i*10,1000));
        replaceWorld(crowded.toArray());setBattlefield(true);
        cls("luna.features.SkillCastStats").getMethod("reset").invoke(null);
        expectFramePacket(2002100L,6,ids(700,20),"Opcode 6 contains exactly the nearest 20 level-40 players, ahead of a closer ordinary player");
        setBattlefield(false);replaceWorld(player,mob,opponent);

        invokeAction(ACTION_MONSTERS);
        cls("luna.features.SkillCastStats").getMethod("reset").invoke(null);
        expectFramePacket(2002800L, 9, new int[]{500}, "Switching back to Auto only quái restores opcode 9 and excludes players");
    }

    private static void verifyBuffOpcode40() throws Exception {
        Class<?> skillOwner = cls(n("lillilll"));
        Class<?> skillType = cls(n("liiiiill"));
        Class<?> levelType = cls(n("liiilill"));
        Object level = allocate(levelType);
        setNumber(level, levelType, n("lilllli"), 7);
        setNumber(level, levelType, "j", 1);
        setNumber(level, levelType, "g", 45000);
        Object levels = Array.newInstance(levelType, 1); Array.set(levels, 0, level);
        Object definition = allocate(skillType);
        setNumber(definition, skillType, "b", 14); setNumber(definition, skillType, "e", 1);
        field(skillType, n("lllillllil")).set(definition, levels);
        field(skillType, n("lllilllil")).set(definition, "Buff fixture");
        Object skills = field(cls(n("lillilll")), "a").get(null);
        Method add = skills.getClass().getMethod(n("l"), Object.class); add.setAccessible(true); add.invoke(skills, definition);
        int[] learned = (int[])field(cls(n("lliiil")), n("llilllii")).get(null); learned[14] = 1;
        Object cd = cooldown(14);
        setNumber(cd, cd.getClass(), "b", 0);
        setNumber(player, entityType, "bu", 1000);
        cls("luna.features.SkillCastStats").getMethod("reset").invoke(null);
        drainPackets();
        frame(2003500L);
        List<String> sent = drainPackets();
        int buffs = 0, attacks = 0;
        for (String packet : sent) {
            if (packet.startsWith("40:[")) buffs++;
            if (packet.startsWith("6:[") || packet.startsWith("9:[")) attacks++;
        }
        require(buffs == 1 && attacks == 0, "A learned buff still uses its single opcode-40 self/ally request: " + sent);
        require(number(player, entityType, "bu") == 993
                && number(cd, cd.getClass(), "b") == 45000 && casts(14) == 1,
                "Buff request spends MP, sets its 45s cooldown and records one cast exactly once");
    }

    private static void verifyNativeXReject(Object bridge) throws Exception {
        Object target = hostile(399, "Native-x", 28, 40, 0);
        require(Boolean.FALSE.equals(entityType.getMethod("x").invoke(target)),
                "Pinned player inherits native x() == false; no invented flag used to simulate it");
    }

    /** Records what the real painter draws; MicroEmulator's base Graphics needs no device. */
    public static final class RecordingGraphics extends javax.microedition.lcdui.Graphics {
        final List<int[]> lines = new ArrayList<int[]>();
        final List<int[]> arcs = new ArrayList<int[]>();
        int color = 0x123456;
        public void setColor(int value) { color = value & 0xFFFFFF; }
        public int getColor() { return color; }
        public void drawLine(int x1, int y1, int x2, int y2) { lines.add(new int[]{x1, y1, x2, y2, color}); }
        public void drawArc(int x, int y, int w, int h, int start, int arc) { arcs.add(new int[]{x, y, w, h, color}); }
    }

    private static RecordingGraphics paintRange(int scale) throws Exception {
        Method paint = null;
        for (Method m : port.getMethods()) if (m.getName().equals("paintPkRange") && m.getParameterTypes().length == 1) paint = m;
        require(paint != null, "Candidate exposes the world-range painter");
        Class<?> graphicsType = paint.getParameterTypes()[0];
        Object wrapper = allocate(graphicsType);
        RecordingGraphics g = new RecordingGraphics();
        boolean bound = false;
        for (Field f : graphicsType.getFields())
            if (f.getType() == javax.microedition.lcdui.Graphics.class) { f.set(wrapper, g); bound = true; break; }
        require(bound, "Native Graphics wrapper bound to the recording Graphics");
        setStatic(graphicsType, "b", Integer.valueOf(scale));
        invoke(paint, null, wrapper);
        require(g.color == 0x123456, "Painter restores the caller's color after lines and ring");
        return g;
    }

    /** Line end points (x2,y2,color) keyed by end point; every line starts at the scaled player feet. */
    private static void expectLines(RecordingGraphics g, int scale, int[][] expected, String message) throws Exception {
        int px = (int)number(player, entityType, "aZ") * scale, py = (int)number(player, entityType, "ba") * scale;
        boolean ok = g.lines.size() == expected.length;
        for (int i = 0; ok && i < expected.length; i++) {
            boolean found = false;
            for (int[] line : g.lines)
                if (line[0] == px && line[1] == py && line[2] == expected[i][0] * scale
                        && line[3] == expected[i][1] * scale && line[4] == expected[i][2]) found = true;
            ok = found;
        }
        StringBuilder actual = new StringBuilder();
        for (int[] line : g.lines) actual.append(Arrays.toString(line));
        require(ok, message + " (actual " + actual + ")");
    }

    private static boolean hasPaceMethod(String name) throws Exception {
        for (Method m : cls("luna.features.SkillCastStats").getMethods()) if (m.getName().equals(name)) return true;
        return false;
    }

    /** Adds Trọng kiếm/đòn 0 (skill 0) next to the fixture skill, both learned and ready. */
    private static void learnSkillZero() throws Exception {
        Class<?> skillOwner = cls(n("lillilll"));
        Class<?> skillType = cls(n("liiiiill"));
        Class<?> levelType = cls(n("liiilill"));
        Object level = allocate(levelType);
        setNumber(level, levelType, n("lilllli"), 10);
        setNumber(level, levelType, "j", 5);
        setNumber(level, levelType, "g", 1000);
        Object levelArray = Array.newInstance(levelType, 1);
        Array.set(levelArray, 0, level);
        Object definition = allocate(skillType);
        setNumber(definition, skillType, "b", 0);
        setNumber(definition, skillType, "e", 0);
        field(skillType, n("lllillllil")).set(definition, levelArray);
        field(skillType, n("lllilllil")).set(definition, "Đòn 0 fixture");
        Object skillList = field(skillOwner, "a").get(null);
        Method add = skillList.getClass().getMethod(n("l"), Object.class);
        add.setAccessible(true);
        add.invoke(skillList, definition);
        int[] learned = (int[])field(cls(n("lliiil")), n("llilllii")).get(null);
        learned[0] = 1;
    }

    /** The attack packets (opcode 6 or 9) of one frame, in send order. */
    private static List<String> attackFrame(long time) throws Exception {
        drainPackets();
        frame(time);
        List<String> out = new ArrayList<String>();
        for (String packet : drainPackets()) if (packet.startsWith("6:[") || packet.startsWith("9:[")) out.add(packet);
        return out;
    }

    /** "6:[skill, ..." with the skill byte replaced, to compare target lists of two sends. */
    private static String targetsOf(String packet) {
        int open = packet.indexOf('['), comma = packet.indexOf(',');
        return packet.substring(0, open + 1) + "*" + packet.substring(comma);
    }

    /**
     * Mode chiến trường + Auto PK + Tấn công đặc biệt under mode 8 (Auto; Đáp 400ms in the 56454EF0 /
     * 879B7F69 releases): đòn 0 and the next skill go in one frame to the same
     * players (opcode 6) or Xà nữ (opcode 9) in the same order (no target rotation in PK), and the
     * lock holds the following frame.
     */
    private static void verifyBattlefieldPacing() throws Exception {
        learnSkillZero();
        setBattlefield(true);
        invokeAction(ACTION_PK);
        if (!Boolean.TRUE.equals(runtime.getMethod("specialEnabled").invoke(null))) invokeAction(ACTION_SPECIAL);
        Class<?> counters = cls("luna.features.SkillCastStats");
        require(Boolean.TRUE.equals(runtime.getMethod("specialEnabled").invoke(null))
                && Boolean.TRUE.equals(runtime.getMethod("playersEnabled").invoke(null)), "Battlefield pacing: Auto PK with Tấn công đặc biệt ON");
        setNumber(player, entityType, "aZ", 1000);
        setNumber(player, entityType, "ba", 1000);
        int[] modes = {8};
        long t = 70000000L;
        for (int mode : modes) {
            counters.getMethod("setPaceMode", Integer.TYPE).invoke(null, Integer.valueOf(mode));
            String label = (String)counters.getMethod("paceLabel", Integer.TYPE).invoke(null, Integer.valueOf(mode));
            for (int round = 0; round < 2; round++, t += 5000) {
                boolean serpents = round == 1;
                Object a = hostile(711, "BfA", 35, 1040, 1000), b = hostile(712, "BfB", 33, 1080, 1000), mob = monster(713, 1010, 1000);
                if (serpents) replaceWorld(player, a, b, mob, serpent(714, "Xà nữ", 1060, 1000), serpent(715, "Xà nữ", 1090, 1000));
                else replaceWorld(player, a, b, mob);
                cooldown(0, 0); cooldown(SKILL, 0);
                setNumber(player, entityType, "bu", 1000);
                List<String> sent = attackFrame(t);
                String op = serpents ? "9:[" : "6:[";
                require(sent.size() == 2 && sent.get(0).startsWith(op + "0, ") && sent.get(1).startsWith(op + (byte)SKILL + ", "),
                        label + (serpents ? " Xà nữ" : " players") + ": đòn 0 then skill " + SKILL + " in one frame, opcode " + op.charAt(0) + " " + sent);
                require(targetsOf(sent.get(0)).equals(targetsOf(sent.get(1))),
                        label + (serpents ? " Xà nữ" : " players") + ": both sends keep the same target order (no rotation in PK) " + sent);
                require(serpents ? sent.get(0).equals(attackPacket(9, 0, 714, 715)) || sent.get(0).equals(attackPacket(9, 0, 715, 714))
                        : !sent.get(0).contains(", " + (byte)713),
                        label + (serpents ? ": Xà nữ only" : ": players only, the monster is ignored") + " " + sent.get(0));
                require(attackFrame(t + 100).isEmpty(), label + ": the next frame waits for the lock");
            }
        }
        counters.getMethod("setPaceMode", Integer.TYPE).invoke(null, Integer.valueOf(0));
        setBattlefield(false);
    }

    private static final int WHITE = 0xFFFFFF, SKY = 0x00BFFF, RING = 0x50FF00, RED = 0xFF0000, ORANGE = 0xFFA500;

    private static Object serpent(int id, String name, int x, int y) throws Exception {
        return nativeEntity(id, (byte)1, x, y, name, 50, false);
    }

    private static boolean pkSerpents() throws Exception {
        return field(port, "pkSerpents").getBoolean(null);
    }

    /** Battlefield ON: Xà nữ inside the radius are the only targets (opcode 9, red lines). */
    private static void verifyXanuPriority() throws Exception {
        invokeAction(ACTION_PK);
        require(Boolean.TRUE.equals(runtime.getMethod("specialEnabled").invoke(null)), "Special stays ON for the Xà nữ checks");
        setNumber(player, entityType, "aZ", 1000);
        setNumber(player, entityType, "ba", 1000);
        Object boss = serpent(980, "Xà nữ", 1100, 1000);
        Object edgeBoss = serpent(981, "  xà nữ ", 1000, 1000 + radius);
        Object farBoss = serpent(982, "Xà nữ", 1001 + radius, 1000);
        Object deadBoss = serpent(983, "Xà nữ", 1050, 1000); setNumber(deadBoss, entityType, "bs", 0);
        Object war40 = hostile(984, "SerpentWar40", 40, 1010, 1000);
        Object other = hostile(985, "SerpentOther", 33, 1020, 1000);
        Object mob = monster(986, 1005, 1000);
        replaceWorld(player, war40, other, mob, farBoss, deadBoss, edgeBoss, boss);

        setBattlefield(true);
        expectIds(new int[]{980, 981}, playerTargets(),
                "Battlefield ON: living Xà nữ inside " + radius + "px (trimmed, case-insensitive name) replace every player,"
                        + " nearest first; dead, out-of-range and ordinary monsters stay out");
        require(pkSerpents(), "Selector marks the Xà nữ list for the monster serializer");
        expectLines(paintRange(1), 1, new int[][]{{1100, 1000, RED}, {1000, 1000 + radius, RED}},
                "Only red lines to Xà nữ while one is in range; no player lines");
        expectFramePacket(2004200L, 9, new int[]{980, 981}, "Xà nữ send uses opcode 9 and carries only Xà nữ");

        setNumber(boss, entityType, "bs", 0);
        setNumber(edgeBoss, entityType, "ba", 1001 + radius);
        expectIds(new int[]{984, 985}, playerTargets(), "No Xà nữ left in range: level 40 first, then other players");
        require(!pkSerpents(), "Player list goes back to the PK serializer");
        expectLines(paintRange(1), 1, new int[][]{{1010, 1000, focusLinesChecks ? ORANGE : SKY}, {1020, 1000, WHITE}},
                "Without Xà nữ in range the player lines return with the attack lock highlighted");
        expectFramePacket(2004900L, 6, new int[]{984, 985}, "Without Xà nữ in range Auto PK sends opcode 6 to players");

        setNumber(boss, entityType, "bs", 100);
        setNumber(edgeBoss, entityType, "ba", 1000 + radius);
        setBattlefield(false);
        expectIds(new int[]{984, 985}, playerTargets(), "Battlefield OFF: Auto PK ignores Xà nữ in range");
        expectLines(paintRange(1), 1, new int[][]{{1010, 1000, WHITE}, {1020, 1000, WHITE}},
                "Battlefield OFF: no red line, players stay white");
        expectFramePacket(2005600L, 6, new int[]{984, 985}, "Battlefield OFF: Xà nữ never enters the opcode-6 list");

        invokeAction(ACTION_MONSTERS);
        replaceWorld(player);
    }

    /** Feet-to-feet lines follow the exact nearestPlayers() eligibility and the Battlefield priority. */
    private static void verifyPkLines() throws Exception {
        setNumber(player, entityType, "aZ", 500);
        setNumber(player, entityType, "ba", 500);
        Object normal = hostile(950, "LineNormal", 33, 600, 500);
        Object war40 = hostile(951, "LineWar40", 40, 500, 500 - radius);
        Object edge = hostile(952, "LineEdge", 33, 500 + radius * 3 / 5, 500 + radius * 4 / 5);
        Object far = hostile(953, "LineFar", 33, 501 + radius, 500);
        Object far40 = hostile(954, "LineFar40", 40, 500, 501 + radius);
        Object mob = monster(955, 520, 500);
        Object dead = hostile(956, "LineDead", 33, 530, 500); setNumber(dead, entityType, "bs", 0);
        Object friendly = nativeEntity(957, (byte)0, 540, 500, "LineFriendly", 33, false);
        Object victim = hostile(958, "LineVictim", 33, 550, 500);
        Object gone = hostile(959, "LineRemoved", 33, 560, 500); field(entityType, "cL").setBoolean(gone, true);

        invokeAction(ACTION_MONSTERS);
        replaceWorld(player, normal, war40, edge, far, far40, mob, dead, friendly, victim, gone);
        RecordingGraphics monsters = paintRange(1);
        require(monsters.lines.isEmpty() && monsters.arcs.isEmpty(), "Monster-only mode draws neither ring nor PK lines");

        invokeAction(ACTION_PK);
        setBattlefield(false);
        RecordingGraphics plain = paintRange(1);
        expectLines(plain, 1, new int[][]{{600, 500, WHITE}, {500, 500 - radius, WHITE},
                {500 + radius * 3 / 5, 500 + radius * 4 / 5, WHITE}, {550, 500, WHITE}},
                "Battlefield OFF: white feet-to-feet lines to every eligible player inside " + radius
                        + "px, level 40 included, none to far/monster/dead/non-hostile/removed entities");
        require(plain.arcs.size() == 1 && plain.arcs.get(0)[0] == 500 - radius && plain.arcs.get(0)[2] == radius * 2
                && plain.arcs.get(0)[4] == RING, "Ring keeps its color and is drawn with the " + radius + "px radius");
        require(plain.lines.size() > 0, "Lines are painted before the ring in the same pass");

        seedExcludedName(victim);
        RecordingGraphics war = paintRange(1);
        expectLines(war, 1, new int[][]{{600, 500, WHITE}, {500, 500 - radius, SKY},
                {500 + radius * 3 / 5, 500 + radius * 4 / 5, WHITE}},
                "Battlefield ON: level-40 line is sky blue, others white, and the excluded FIFO name gets no line");
        expectIds(new int[]{951, 950, 952}, playerTargets(), "Lines mark exactly the targets the PK selector returns");

        RecordingGraphics doubled = paintRange(2);
        expectLines(doubled, 2, new int[][]{{600, 500, WHITE}, {500, 500 - radius, SKY},
                {500 + radius * 3 / 5, 500 + radius * 4 / 5, WHITE}}, "Line ends follow the native world scale");

        Object aligned = hostile(960, "LineAligned", 40, 2000, 500);
        field(entityType, "battlefieldPositionKnown").setBoolean(aligned, true);
        setNumber(aligned, entityType, "battlefieldX", 700); setNumber(aligned, entityType, "battlefieldY", 500);
        replaceWorld(player, aligned, far);
        expectLines(paintRange(1), 1, new int[][]{{700, 500, SKY}},
                "Battlefield server position is aligned before the radius gate and used as the line end");

        List<Object> crowd = new ArrayList<Object>();
        crowd.add(player);
        for (int i = 0; i < 25; i++) crowd.add(hostile(970 + i, "Crowd" + i, 30, 400 + i * 4, 500 + i));
        replaceWorld(crowd.toArray());
        require(paintRange(1).lines.size() == 25, "Every eligible player inside the ring gets a line, not only the top 20");

        setBattlefield(false);
        Class<?> support = cls("luna.features.BattlefieldSupport");
        support.getMethod("resetForTest").invoke(null);
        invokeAction(ACTION_MONSTERS);
        invokeAction(ACTION_TOGGLE);
        require(!Boolean.TRUE.equals(runtime.getMethod("enabled").invoke(null)), "Line fixture leaves Auto OFF in monster mode");
        setNumber(player, entityType, "aZ", 0);
        setNumber(player, entityType, "ba", 0);
    }

    private static void verifyNormalPkController(Object bridge) throws Exception {
        Class<?> api=cls("luna.api.AutoAttackAccess"),settings=cls("luna.api.AutoAttackSettings"),clock=cls("luna.api.LunaClock");
        Object store=Proxy.newProxyInstance(loader,new Class<?>[]{settings},new InvocationHandler(){
            public Object invoke(Object p,Method m,Object[] a){
                if(m.getName().equals("load")){int[] v=(int[])a[0];v[0]=1;v[1]=650;}return null;
            }
        });
        Object time=Proxy.newProxyInstance(loader,new Class<?>[]{clock},new InvocationHandler(){
            public Object invoke(Object p,Method m,Object[] a){return Long.valueOf(now);}
        });
        now=4000000L;
        runtime.getMethod("installForTest",api,settings,clock).invoke(null,bridge,store,time);
        setNumber(player,entityType,"aZ",0);setNumber(player,entityType,"ba",0);
        setNumber(player,entityType,"cG",0);setNumber(player,entityType,"cA",-1);
        Object near=hostile(910,"Near",35,50,0),war=hostile(911,"War",40,radius,0),newWar=hostile(912,"NewWar",40,100,0);
        replaceWorld(player,monster(909,1,0),near,war);
        setBattlefield(true);invokeAction(ACTION_PK);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==war,"Special OFF: real PK bridge prioritizes level 40 and excludes the nearer monster");
        require("Auto Đánh: PK [6]".equals(runtime.getMethod("statusText").invoke(null)),"Real upgraded bridge exposes PK-only HUD");
        int generation=((Integer)runtime.getMethod("generation").invoke(null)).intValue();
        replaceWorld(player,near,war,newWar);setNumber(player,entityType,"cG",2);
        runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==war&&((Integer)runtime.getMethod("generation").invoke(null)).intValue()==generation,
                "Changing priority does not cancel an otherwise valid busy native cast");
        setNumber(player,entityType,"cG",0);runtime.getMethod("tick").invoke(null);
        if(targetLockChecks)require(runtime.getMethod("target").invoke(null)==war,"Cast finished: the locked level 40 is kept although a nearer equal-HP level 40 appeared");
        else require(runtime.getMethod("target").invoke(null)==newWar,"Cast finished: PK selector refreshes to the closest priority target");
        setBattlefield(false);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==near,"Special OFF, Battlefield OFF: PK returns to distance ordering");
        setNumber(near,entityType,"aZ",radius+1);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==newWar,"Retained player moving beyond "+radius+"px is dropped before another request");
        if(!serpentChecks)return;
        Object boss=serpent(915,"Xà nữ",200,0);setNumber(near,entityType,"aZ",50);
        replaceWorld(player,near,war,newWar,boss);
        setBattlefield(true);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==boss,"Special OFF, Battlefield ON: Xà nữ ranks above level 40 and nearer players");
        setNumber(boss,entityType,"aZ",radius+1);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==newWar,"Special OFF: Xà nữ outside "+radius+"px is not chosen; level 40 is next");
        setNumber(boss,entityType,"aZ",200);setBattlefield(false);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==near,"Special OFF, Battlefield OFF: Auto PK never targets Xà nữ");
        if(!hpOrderChecks)return;
        Object weak40=hostile(916,"WeakFar40",40,300,0);setNumber(weak40,entityType,"bs",10);
        setNumber(war,entityType,"bs",500);setNumber(newWar,entityType,"bs",500);setNumber(near,entityType,"bs",1);
        Object weakBoss=serpent(917,"Xà nữ",250,0),fullBoss=serpent(918,"Xà nữ",20,0);
        setNumber(weakBoss,entityType,"bs",4000);setNumber(fullBoss,entityType,"bs",5000000);
        replaceWorld(player,near,war,newWar,weak40);
        setBattlefield(true);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==weak40,"Special OFF, Battlefield ON: the lowest-HP level 40 beats nearer level 40 and a weaker ordinary player");
        setNumber(weak40,entityType,"bs",900);runtime.getMethod("tick").invoke(null);
        if(targetLockChecks){
            require(runtime.getMethod("target").invoke(null)==weak40,"Special OFF: the locked level 40 is kept after its HP rises above the others");
            setNumber(weak40,entityType,"aZ",radius+1);runtime.getMethod("tick").invoke(null);
        }
        require(runtime.getMethod("target").invoke(null)==newWar,"Special OFF: equal-HP level 40 falls back to the nearer one");
        replaceWorld(player,near,war,newWar,weak40,fullBoss,weakBoss);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==weakBoss,"Special OFF: the lower-HP Xà nữ beats a nearer full-HP Xà nữ and every player");
        setBattlefield(false);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==near,"Special OFF, Battlefield OFF: HP is ignored, nearest player again");
        if(!targetLockChecks)return;
        Object a=hostile(920,"LockA",33,60,0),b=hostile(921,"LockB",33,80,0),c=hostile(922,"LockC40",40,300,0);
        setNumber(a,entityType,"bs",300);setNumber(b,entityType,"bs",500);setNumber(c,entityType,"bs",999);
        replaceWorld(player,a,b);setBattlefield(true);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==a,"Special OFF lock: the lowest-HP player is chosen");
        setNumber(a,entityType,"bs",900);setNumber(b,entityType,"bs",100);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==a,"Special OFF lock: kept although another player now has lower HP");
        replaceWorld(player,a,b,c);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==c,"Special OFF lock: a level 40 in range takes over");
        replaceWorld(player,a,b);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==b,"Special OFF lock: the higher group gone, the lowest HP is chosen again");
        setNumber(b,entityType,"bs",0);runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null)==a,"Special OFF lock: the locked target died, the next one is chosen");
        setBattlefield(false);
    }

    /** Battlefield ON, Special loop: the first target stays first until it dies, leaves the radius or a higher group shows up. */
    private static void verifyTargetLock() throws Exception {
        setNumber(player, entityType, "aZ", 0);
        setNumber(player, entityType, "ba", 0);
        Object a = hostile(1500, "LockA", 33, 60, 0); setNumber(a, entityType, "bs", 300);
        Object b = hostile(1501, "LockB", 33, 80, 0); setNumber(b, entityType, "bs", 500);
        Object c = hostile(1502, "LockC40", 40, 300, 0); setNumber(c, entityType, "bs", 999);
        Object boss = serpent(1503, "Xà nữ", 100, 0); setNumber(boss, entityType, "bs", 5000);
        replaceWorld(player, a, b);
        setBattlefield(true);
        expectIds(new int[]{1500, 1501}, playerTargets(), "Lock: the lowest-HP player is chosen first");
        setNumber(a, entityType, "bs", 900); setNumber(b, entityType, "bs", 100);
        expectIds(new int[]{1500, 1501}, playerTargets(), "Lock: the locked player stays first although another one now has lower HP");
        expectIds(new int[]{1500, 1501}, playerTargets(), "Lock: it holds over repeated frames");
        replaceWorld(player, a, b, c);
        expectIds(new int[]{1502, 1500, 1501}, playerTargets(), "Lock: a level 40 in range takes the lock (the old lock still heads its group this frame)");
        expectIds(new int[]{1502, 1501, 1500}, playerTargets(), "Lock: next frame the lower group follows by HP");
        replaceWorld(player, a, b);
        expectIds(new int[]{1501, 1500}, playerTargets(), "Lock: the higher group gone, the lowest HP is chosen again");
        setNumber(b, entityType, "bs", 1000);
        expectIds(new int[]{1501, 1500}, playerTargets(), "Lock: the new lock holds after its HP rises");
        setNumber(b, entityType, "bs", 0);
        expectIds(new int[]{1500}, playerTargets(), "Lock: the locked player died, the next one is chosen");
        setNumber(b, entityType, "bs", 50); setNumber(a, entityType, "aZ", radius + 1);
        expectIds(new int[]{1501}, playerTargets(), "Lock: the locked player left the radius, the next one is chosen");
        setNumber(a, entityType, "aZ", 60); setNumber(a, entityType, "bs", 10);
        expectIds(new int[]{1501, 1500}, playerTargets(), "Lock: a returning lower-HP player does not take the lock back");
        setNumber(a, entityType, "bs", 900);
        replaceWorld(player, a, b, boss);
        expectIds(new int[]{1503}, playerTargets(), "Lock: Xà nữ in range takes the lock");
        require(pkSerpents(), "Locked Xà nữ list keeps the monster serializer");
        setNumber(boss, entityType, "bs", 0);
        expectIds(new int[]{1501, 1500}, playerTargets(), "Lock: Xà nữ dead, the lowest-HP player is chosen again");
        setBattlefield(false);
        expectIds(new int[]{1500, 1501}, playerTargets(), "Battlefield OFF: no lock, nearest first");
        require(field(port, "pkLock").get(null) == null, "Battlefield OFF clears the lock");
        replaceWorld(player);
    }

    /** Battlefield ON: inside each group (Xà nữ, level 40, others) lower HP first, nearer on equal HP. */
    private static void verifyHpOrder() throws Exception {
        invokeAction(ACTION_PK);
        setNumber(player, entityType, "aZ", 0);
        setNumber(player, entityType, "ba", 0);
        Object strong40 = hostile(1200, "HpStrong40", 40, 50, 0); setNumber(strong40, entityType, "bs", 900);
        Object weak40 = hostile(1201, "HpWeak40", 40, radius, 0); setNumber(weak40, entityType, "bs", 300);
        Object strongNear = hostile(1202, "HpStrongNear", 33, 20, 0); setNumber(strongNear, entityType, "bs", 5000);
        Object weakFar = hostile(1203, "HpWeakFar", 33, 300, 0); setNumber(weakFar, entityType, "bs", 40);
        Object tieNear = hostile(1204, "HpTieNear", 33, 100, 0); setNumber(tieNear, entityType, "bs", 700);
        Object tieFar = hostile(1205, "HpTieFar", 33, 200, 0); setNumber(tieFar, entityType, "bs", 700);
        replaceWorld(player, strongNear, tieFar, strong40, weakFar, weak40, tieNear);
        setBattlefield(true);
        expectIds(new int[]{1201, 1200, 1203, 1204, 1205, 1202}, playerTargets(),
                "Battlefield ON: level 40 first, lower HP first inside each group, nearer on equal HP");
        expectLines(paintRange(1), 1, new int[][]{{20, 0, WHITE}, {200, 0, WHITE}, {50, 0, SKY}, {300, 0, WHITE},
                {radius, 0, focusLinesChecks ? ORANGE : SKY}, {100, 0, WHITE}}, "HP order keeps base colours and highlights the focused player");
        setBattlefield(false);
        expectIds(new int[]{1202, 1200, 1204, 1205, 1203, 1201}, playerTargets(), "Battlefield OFF: HP is ignored, nearest first");

        List<Object> many = new ArrayList<Object>();
        many.add(player);
        for (int i = 0; i < 25; i++) {
            Object e = hostile(1300 + i, "HpMany" + i, 40, 20 + i * 10, 0);
            setNumber(e, entityType, "bs", 10000 - i * 100);
            many.add(e);
        }
        replaceWorld(many.toArray());
        setBattlefield(true);
        int[] lowest = new int[20];
        for (int i = 0; i < 20; i++) lowest[i] = 1324 - i;
        expectIds(lowest, playerTargets(), "More than 20 level 40: the 20 lowest HP are kept, lowest first, even when farther");
        setBattlefield(false);
        expectIds(ids(1300, 20), playerTargets(), "Battlefield OFF keeps the 20 nearest");

        Object fullBoss = serpent(1400, "Xà nữ", 30, 0); setNumber(fullBoss, entityType, "bs", 5000000);
        Object weakBoss = serpent(1401, "Xà nữ", radius, 0); setNumber(weakBoss, entityType, "bs", 120000);
        Object weakPlayer = hostile(1402, "HpWeakPlayer", 40, 10, 0); setNumber(weakPlayer, entityType, "bs", 1);
        replaceWorld(player, weakPlayer, fullBoss, weakBoss);
        setBattlefield(true);
        expectIds(new int[]{1401, 1400}, playerTargets(), "Xà nữ group: lower HP first even when farther; players stay out");
        require(pkSerpents(), "Xà nữ HP order keeps the monster serializer");
        setBattlefield(false);
        invokeAction(ACTION_MONSTERS);
        replaceWorld(player);
    }

    /** Executes the real painter with native focus and both attack paths; no synthetic colour helper. */
    private static void verifyFocusLines() throws Exception {
        invokeAction(ACTION_PK);
        if (Boolean.TRUE.equals(runtime.getMethod("specialEnabled").invoke(null))) invokeAction(ACTION_SPECIAL);
        setNumber(player, entityType, "aZ", 0); setNumber(player, entityType, "ba", 0);
        Object normal = hostile(1600, "FocusNormal", 33, 60, 0);
        Object war40 = hostile(1601, "FocusWar40", 40, 120, 0);
        Object boss = serpent(1602, "Xà nữ", 180, 0);
        replaceWorld(player, normal, war40);
        setBattlefield(true);
        nativeFocus.set(null, null);
        expectLines(paintRange(1), 1, new int[][]{{60, 0, WHITE}, {120, 0, SKY}}, "No focus keeps the normal player colours");
        nativeFocus.set(null, normal);
        expectLines(paintRange(1), 1, new int[][]{{60, 0, ORANGE}, {120, 0, SKY}}, "Native focused ordinary player is orange");
        nativeFocus.set(null, war40);
        expectLines(paintRange(2), 2, new int[][]{{60, 0, WHITE}, {120, 0, ORANGE}}, "Native focused level 40 is orange at world scale 2");
        setBattlefield(false);
        expectLines(paintRange(1), 1, new int[][]{{60, 0, WHITE}, {120, 0, WHITE}}, "Battlefield OFF ignores the focus highlight");
        setBattlefield(true);
        setNumber(war40, entityType, "bs", 0);
        expectLines(paintRange(1), 1, new int[][]{{60, 0, WHITE}}, "Dead focused player has no line");
        setNumber(war40, entityType, "bs", 100);
        setNumber(war40, entityType, "aZ", radius + 1);
        expectLines(paintRange(1), 1, new int[][]{{60, 0, WHITE}}, "Out-of-range focused player has no line");
        setNumber(war40, entityType, "aZ", 120);
        nativeFocus.set(null, normal);
        invokeAction(ACTION_SPECIAL);
        require(Boolean.TRUE.equals(runtime.getMethod("normalMonstersActive").invoke(null)), "Special focus check uses the independent attack loop");
        playerTargets();
        expectLines(paintRange(1), 1, new int[][]{{60, 0, WHITE}, {120, 0, ORANGE}}, "Special loop highlights its player lock instead of unrelated native focus");
        replaceWorld(player, normal, war40, boss);
        playerTargets();nativeFocus.set(null, boss);
        expectLines(paintRange(1), 1, new int[][]{{180, 0, RED}}, "Focused Xà nữ stays red in the Special path");
        invokeAction(ACTION_SPECIAL);
        expectLines(paintRange(1), 1, new int[][]{{180, 0, RED}}, "Native focused Xà nữ also stays red");
        replaceWorld(player, normal, war40);nativeFocus.set(null, normal);
        if (Boolean.TRUE.equals(runtime.getMethod("enabled").invoke(null))) invokeAction(ACTION_TOGGLE);
        expectLines(paintRange(1), 1, new int[][]{{60, 0, ORANGE}, {120, 0, SKY}}, "Manual native focus remains orange when Auto is OFF");
        nativeFocus.set(null, null);setBattlefield(false);replaceWorld(player);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("candidate.jar auto-attack.properties maze.properties");
        File candidate = new File(args[0]);
        if (!candidate.isFile()) throw new IllegalArgumentException("Missing candidate JAR: " + candidate);
        Properties auto = load(new File(args[1]));
        radius = Integer.parseInt(auto.getProperty("auto.pk.radius", "400").trim());
        serpentChecks = Boolean.parseBoolean(auto.getProperty("auto.pk.serpent", "false").trim());
        hpOrderChecks = Boolean.parseBoolean(auto.getProperty("auto.pk.hp.order", "false").trim());
        targetLockChecks = Boolean.parseBoolean(auto.getProperty("auto.pk.target.lock", "false").trim());
        focusLinesChecks = Boolean.parseBoolean(auto.getProperty("auto.pk.focus.lines", "false").trim());
        if (radius <= 0 || radius % 5 != 0) throw new IllegalArgumentException("Fixture geometry needs a positive radius divisible by 5");
        // The third catalog is the current ModUpLv/Farm port catalog passed by the build task.
        Properties maze = load(new File(args[2]));
        portOwner = maze.getProperty("copy.special.loop.owner", "HungBySonModUpLv").trim();
        statsOwner = maze.getProperty("copy.special.stats.owner", "HungBySonSkillStats").trim();
        Object probe = construct(Class.forName(HELPER + "$NativeProbeLoader"),
                new Class<?>[]{File.class, Properties.class}, candidate, auto);
        loader = (ClassLoader)probe;
        try {
            setupRuntime(auto);
            nativeFocus = field(cls(auto.getProperty("native.target.owner")), auto.getProperty("native.target.field"));
            Class<?> bridge = cls(auto.getProperty("bridge.owner", auto.getProperty("native.menu.owner")));
            Object nativeBridge = bridge.getDeclaredConstructor().newInstance();
            verifyNativeSelection(nativeBridge);
            verifyNativeXReject(nativeBridge);
            if (Boolean.parseBoolean(auto.getProperty("auto.pk.lines", "false").trim())) verifyPkLines();
            setBattlefield(false);
            replaceWorld(player, monster(500, 1100, 1000), hostile(501, "Opponent", 35, 1050, 1000));
            verifyModeTransitionAndPackets();
            verifyBuffOpcode40();
            if (serpentChecks) verifyXanuPriority();
            if (hpOrderChecks) verifyHpOrder();
            if (targetLockChecks) verifyTargetLock();
            verifyNormalPkController(nativeBridge);
            if (focusLinesChecks) verifyFocusLines();
            if (hasPaceMethod("answerMode")) verifyBattlefieldPacing();
            System.out.println("HungBySonAutoPkBinaryTest PASS checks=" + checks);
        } finally {
            try { ((URLClassLoader)loader).close(); } catch (Exception ignored) { }
        }
    }
}
