import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Binary regression for Auto Mê cung's short-lived combat ownership. It reuses
 * the established native probe setup, then exercises the actual module tick,
 * Farm cleanup branch, request serializer, toggle dispatch and lifecycle exits.
 */
public final class HungBySonMazeExclusiveBinaryTest {
    private static final String MOD_UP_LV = "HungBySonModUpLv";
    private static final String AUTO_TEST = "HungBySonAutoAttackBinaryTest";
    private static final String MOD_TEST = "HungBySonModUpLvBinaryTest";
    private static int checks;
    private static File candidate;
    private static Properties auto;
    private static Properties portCatalog;
    private static Properties mazeCatalog;
    private static ClassLoader loader;
    private static Class<?> port, playerType, entityType, farmType, runtime;
    private static Class<?> enhanceType, tradeType;
    private static Field enhanceFlag, tradeFlag;
    private static Method enhanceAction, tradeToggle;
    private static Object player, guard;
    private static Method setMaze, mazeOwnsCombat, blockMazeAction;

    private static void require(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }

    private static Properties load(File file) throws Exception {
        Properties p = new Properties();
        InputStream in = new FileInputStream(file);
        try { p.load(in); } finally { in.close(); }
        return p;
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new RuntimeException(cause);
        }
    }

    private static Object privateStatic(Class<?> owner, String name, Class<?>[] signature, Object... args)
            throws Exception {
        Method method = owner.getDeclaredMethod(name, signature);
        method.setAccessible(true);
        return invoke(method, null, args);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static Object staticValue(Class<?> owner, String name) throws Exception {
        return field(owner, name).get(null);
    }

    private static void setStatic(Class<?> owner, String name, Object value) throws Exception {
        field(owner, name).set(null, value);
    }

    private static void setNumber(Object target, Class<?> owner, String name, int value) throws Exception {
        Field f = field(owner, name);
        if (f.getType() == Byte.TYPE) f.setByte(target, (byte)value);
        else if (f.getType() == Short.TYPE) f.setShort(target, (short)value);
        else if (f.getType() == Integer.TYPE) f.setInt(target, value);
        else throw new IllegalArgumentException(owner.getName() + "." + name + " is " + f.getType());
    }

    private static boolean staticBoolean(Class<?> owner, String name) throws Exception {
        return field(owner, name).getBoolean(null);
    }

    private static Class<?> candidateClass(String name) throws Exception {
        return Class.forName(name.replace('/', '.'), true, loader);
    }

    private static String obf(String suffix) {
        StringBuilder name = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) name.append('i');
        return name.append(suffix).toString();
    }

    private static void map(int id) throws Exception {
        privateStatic(Class.forName(MOD_TEST), "map", new Class<?>[]{Integer.TYPE}, Integer.valueOf(id));
    }

    private static void world(Object... entities) throws Exception {
        privateStatic(Class.forName(MOD_TEST), "world", new Class<?>[]{Object[].class}, (Object)entities);
    }

    private static void at(Object entity, int x, int y) throws Exception {
        privateStatic(Class.forName(MOD_TEST), "at", new Class<?>[]{Object.class, Integer.TYPE, Integer.TYPE},
                entity, Integer.valueOf(x), Integer.valueOf(y));
    }

    private static void update(long now) throws Exception {
        setStatic(port, "testNow", Long.valueOf(now));
        port.getMethod("update").invoke(staticValue(port, "INSTANCE"));
    }

    private static void action(int code) throws Exception {
        Object result = invoke(port.getMethod("onAction", Integer.TYPE, Integer.TYPE), staticValue(port, "INSTANCE"),
                Integer.valueOf(code), Integer.valueOf(0));
        require(Boolean.TRUE.equals(result), "Native Maze module accepts action " + code);
    }

    private static boolean owns() throws Exception {
        return ((Boolean)mazeOwnsCombat.invoke(null)).booleanValue();
    }

    private static void resetRuntime() throws Exception {
        if (((Boolean)runtime.getMethod("enabled").invoke(null)).booleanValue())
            runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-111));
        if (((Boolean)runtime.getMethod("specialEnabled").invoke(null)).booleanValue())
            runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-116));
        String normal = (String)runtime.getMethod("normalLabel").invoke(null);
        if (normal.indexOf("[BẬT]") >= 0) runtime.getMethod("handleAction", Integer.TYPE)
                .invoke(null, Integer.valueOf(-117));
    }

    private static void activateOtherModules() throws Exception {
        resetRuntime();
        runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-111));
        runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-116));
        String normal = (String)runtime.getMethod("normalLabel").invoke(null);
        if (normal.indexOf("[BẬT]") < 0)
            runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-117));
        setStatic(farmType, auto.getProperty("native.farm.enabled.field"), Boolean.TRUE);
        CombatFlag combat = combatFlag(candidate, farmType.getName(), auto.getProperty("native.farm.enabled.field"));
        setStatic(combat.owner, combat.field, Boolean.TRUE);
        // These are independent ModUpLv options that share combat or movement ownership.
        for (String name : new String[]{"finish", "free", "gem", "both", "fog", "fire"})
            setStatic(port, name, Boolean.TRUE);
        require(((Boolean)runtime.getMethod("enabled").invoke(null)).booleanValue()
                        && ((Boolean)runtime.getMethod("specialEnabled").invoke(null)).booleanValue()
                        && ((String)runtime.getMethod("normalLabel").invoke(null)).indexOf("[BẬT]") >= 0
                        && staticBoolean(farmType, auto.getProperty("native.farm.enabled.field"))
                        && staticBoolean(combat.owner, combat.field),
                "Auto Attack, Special, normal combat, Farm and native combat are seeded ON before Maze enable");
    }

    private static final class CombatFlag {
        final Class<?> owner;
        final String field;
        CombatFlag(Class<?> owner, String field) { this.owner = owner; this.field = field; }
    }

    private static CombatFlag combatFlag(File jarFile, String farmName, String farmEnabledField) throws Exception {
        JarFile jar = new JarFile(jarFile);
        try {
            ClassNode farm = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry(farmName.replace('.', '/') + ".class")))
                    .accept(farm, 0);
            MethodNode update = null;
            for (MethodNode method : farm.methods)
                if ("codexFarmUpdate".equals(method.name) && "()V".equals(method.desc)) update = method;
            if (update == null) throw new NoSuchMethodException(farmName + ".codexFarmUpdate()V");
            boolean foundFarmFlag = false;
            for (AbstractInsnNode n : update.instructions) {
                if (!(n instanceof org.objectweb.asm.tree.FieldInsnNode) || n.getOpcode() != Opcodes.GETSTATIC) continue;
                org.objectweb.asm.tree.FieldInsnNode f = (org.objectweb.asm.tree.FieldInsnNode)n;
                if (!"Z".equals(f.desc)) continue;
                if (f.owner.replace('/', '.').equals(farmName) && f.name.equals(farmEnabledField)) {
                    foundFarmFlag = true;
                    continue;
                }
                if (foundFarmFlag) return new CombatFlag(candidateClass(f.owner), f.name);
            }
            throw new IllegalStateException("Farm combat-module flag not found");
        } finally { jar.close(); }
    }

    private static String staticBooleanWrite(File jarFile, String ownerName, String methodName, String descriptor)
            throws Exception {
        JarFile jar = new JarFile(jarFile);
        try {
            ClassNode owner = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry(ownerName.replace('.', '/') + ".class")))
                    .accept(owner, 0);
            for (MethodNode method : owner.methods) if (methodName.equals(method.name) && descriptor.equals(method.desc))
                for (AbstractInsnNode instruction : method.instructions.toArray())
                    if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode
                            && instruction.getOpcode() == Opcodes.PUTSTATIC) {
                        org.objectweb.asm.tree.FieldInsnNode f = (org.objectweb.asm.tree.FieldInsnNode)instruction;
                        if (owner.name.equals(f.owner) && "Z".equals(f.desc)) return f.name;
                    }
            throw new NoSuchFieldException(ownerName + "." + methodName + descriptor + " static boolean write");
        } finally { jar.close(); }
    }

    private static void prepareNativeCompetingModules() throws Exception {
        enhanceType = candidateClass(obf("lliillii"));
        enhanceFlag = field(enhanceType, obf("liilliliili"));
        enhanceAction = enhanceType.getDeclaredMethod("onAction", Integer.TYPE, Integer.TYPE);
        enhanceAction.setAccessible(true);
        if (!enhanceFlag.getBoolean(null)) {
            Object enhancer = privateStatic(Class.forName(MOD_TEST), "allocate",
                    new Class<?>[]{Class.class}, enhanceType);
            Object result = invoke(enhanceAction, enhancer, Integer.valueOf(-85), Integer.valueOf(0));
            require(Boolean.TRUE.equals(result) && enhanceFlag.getBoolean(null),
                    "Native enhancement action seeds its mode ON before Maze enable");
        }

        tradeType = candidateClass(obf("lliillil"));
        tradeToggle = tradeType.getDeclaredMethod(obf("ll"), Boolean.TYPE);
        tradeToggle.setAccessible(true);
        String tradeFlagName = staticBooleanWrite(candidate, tradeType.getName(), obf("ll"), "(Z)V");
        tradeFlag = field(tradeType, tradeFlagName);
        invoke(tradeToggle, null, Boolean.TRUE);
        require(tradeFlag.getBoolean(null), "Native trade/combat toggle is ON before Maze enable");
    }

    private static Object pendingRequest() throws Exception {
        Class<?> autoTest = Class.forName(AUTO_TEST);
        Class<?> probeType = Class.forName(AUTO_TEST + "$NativeProbeLoader");
        Method prepare = autoTest.getDeclaredMethod("prepareNativeRequest", probeType, File.class,
                Properties.class, Object.class, Object.class, Integer.TYPE, Byte.TYPE);
        prepare.setAccessible(true);
        Object spec = invoke(prepare, null, loader, candidate, auto, player, guard, Integer.valueOf(0), Byte.valueOf((byte)0));
        Class<?> castType = (Class<?>)get(spec, "castType");
        Object cast = get(spec, "cast");
        field(playerType, auto.getProperty("request.controller.field")).set(player, cast);
        setNumber(player, playerType, "cG", 2);
        setNumber(player, playerType, "cA", 0);
        setNumber(player, playerType, "cR", 2);
        return spec;
    }

    private static Object get(Object target, String name) throws Exception {
        return field(target.getClass(), name).get(target);
    }

    private static void configureRequestClock(long base) throws Exception {
        Class<?> autoTest = Class.forName(AUTO_TEST);
        Method setClock = autoTest.getDeclaredMethod("setNativeRequestClock", ClassLoader.class,
                Properties.class, Long.TYPE, Short.TYPE);
        setClock.setAccessible(true);
        invoke(setClock, null, loader, auto, Long.valueOf(base), Short.valueOf((short)0));
        // Untagged casts retain Farm's 650ms minimum. Send strictly beyond that floor.
        setStatic(autoTest, "nativeRequestNow", Long.valueOf(base + 651L));
        List<?> packets = (List<?>)staticValue(autoTest, "nativePackets");
        synchronized (packets) { packets.clear(); }
    }

    private static int attackPacketCount() throws Exception {
        List<?> packets = (List<?>)staticValue(Class.forName(AUTO_TEST), "nativePackets");
        synchronized (packets) {
            int count = 0;
            for (Object item : packets) {
                String value = String.valueOf(item);
                if (value.startsWith("6:[") || value.startsWith("9:[")) count++;
            }
            return count;
        }
    }

    private static boolean hasOpcode(int opcode) throws Exception {
        List<?> packets = (List<?>)staticValue(Class.forName(AUTO_TEST), "nativePackets");
        synchronized (packets) {
            for (Object item : packets) if (String.valueOf(item).startsWith(opcode + ":[")) return true;
            return false;
        }
    }

    private static void seedGuardWorld() throws Exception {
        // The route deliberately alternates door 3/1 across map visits. Reset only
        // the route fixture state so every lifecycle scenario exercises guard 3.
        privateStatic(port, "mazeReset", new Class<?>[0]);
        guard = privateStatic(Class.forName(MOD_TEST), "monster",
                new Class<?>[]{Integer.TYPE, Integer.TYPE, Integer.TYPE}, Integer.valueOf(3), Integer.valueOf(30), Integer.valueOf(0));
        at(player, 0, 0);
        setNumber(player, playerType, "cG", 0);
        setNumber(player, playerType, "cA", -1);
        setNumber(player, playerType, "bs", 100);
        world(guard);
        map(109);
    }

    private static void verifyActorReplacementRelease() throws Exception {
        action(-94);
        seedGuardWorld();
        setNumber(player, playerType, "cA", -1);
        update(9100000L);
        require(owns(), "Maze owns the original actor before actor replacement");
        Object spec = pendingRequest();
        Object oldCast = get(spec, "cast");
        Class<?> castType = (Class<?>)get(spec, "castType");
        Class<?> singletonOwner = candidateClass(auto.getProperty("native.player.singleton.owner"));
        String singletonField = auto.getProperty("native.player.singleton.field");
        Object replacement = privateStatic(Class.forName(MOD_TEST), "allocate",
                new Class<?>[]{Class.class}, playerType);
        setNumber(replacement, playerType, "cG", 4);
        setNumber(replacement, playerType, "cA", -1);
        field(singletonOwner, singletonField).set(null, replacement);
        update(9100200L);
        require(field(castType, auto.getProperty("request.tag.field")).getInt(oldCast) == -1
                        && field(replacement.getClass(), auto.getProperty("request.controller.field")).get(replacement) == null
                        && field(playerType, "cG").getInt(replacement) == 4,
                "Actor replacement cancels the detached original cast without mutating the replacement actor");
        action(-94);
        field(singletonOwner, singletonField).set(null, player);
    }

    private static void verifyAbiAndHooks() throws Exception {
        setMaze = port.getMethod("setMaze", Boolean.TYPE);
        mazeOwnsCombat = port.getMethod("mazeOwnsCombat");
        blockMazeAction = port.getMethod("blockMazeAction", Integer.TYPE);
        require(Modifier.isPublic(setMaze.getModifiers()) && Modifier.isStatic(setMaze.getModifiers())
                        && setMaze.getReturnType() == Void.TYPE
                        && Modifier.isPublic(mazeOwnsCombat.getModifiers()) && Modifier.isStatic(mazeOwnsCombat.getModifiers())
                        && mazeOwnsCombat.getReturnType() == Boolean.TYPE
                        && Modifier.isPublic(blockMazeAction.getModifiers()) && Modifier.isStatic(blockMazeAction.getModifiers())
                        && blockMazeAction.getReturnType() == Boolean.TYPE,
                "Candidate exposes the public Maze ownership ABI with exact static descriptors");
        require("true".equals(mazeCatalog.getProperty("maze.exclusive")), "Maze catalog marks the exclusive patch");
        require(hasCall(candidate, mazeCatalog.getProperty("maze.dispatch.owner"),
                        mazeCatalog.getProperty("maze.dispatch.method"), mazeCatalog.getProperty("maze.dispatch.descriptor"),
                        MOD_UP_LV, "blockMazeAction", "(I)Z"),
                "Native module dispatcher consumes conflicting Maze actions");
        require(hasCall(candidate, "luna/features/AutoAttackRuntime", "handleAction", "(I)Z",
                        MOD_UP_LV, "blockMazeAction", "(I)Z"),
                "Direct Auto Attack actions consume conflicting Maze actions");
        require(hasCall(candidate, auto.getProperty("native.locked.codexFarmUpdate.owner"), "codexFarmUpdate", "()V",
                        MOD_UP_LV, "mazeOwnsCombat", "()Z"),
                "Actual Farm OFF cleanup consults Maze combat ownership");
        require(hasCall(candidate, auto.getProperty("scan.owner"), auto.getProperty("scan.method"),
                        auto.getProperty("scan.descriptor"), MOD_UP_LV, "mazeOwnsCombat", "()Z")
                        && hasCall(candidate, auto.getProperty("native.locked.codexLocalPlayerUpdate.owner"),
                        auto.getProperty("native.locked.codexLocalPlayerUpdate.method"),
                        auto.getProperty("native.locked.codexLocalPlayerUpdate.descriptor"),
                        MOD_UP_LV, "mazeOwnsCombat", "()Z"),
                "Native scan and local update selection are gated while Maze owns its guard");
    }

    private static boolean hasCall(File jarFile, String owner, String method, String descriptor,
            String calledOwner, String calledName, String calledDescriptor) throws Exception {
        if (owner == null || method == null || descriptor == null) return false;
        JarFile jar = new JarFile(jarFile);
        try {
            ClassNode node = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry(owner + ".class"))).accept(node, 0);
            for (MethodNode m : node.methods) if (method.equals(m.name) && descriptor.equals(m.desc))
                for (AbstractInsnNode insn : m.instructions.toArray())
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode)insn;
                        if (calledOwner.equals(call.owner) && calledName.equals(call.name)
                                && calledDescriptor.equals(call.desc)) return true;
                    }
            return false;
        } finally { jar.close(); }
    }

    private static void verifyMazeEnableAndBlockedActions() throws Exception {
        activateOtherModules();
        prepareNativeCompetingModules();
        setStatic(port, "finish", Boolean.TRUE);
        action(-94);
        CombatFlag combat = combatFlag(candidate, farmType.getName(), auto.getProperty("native.farm.enabled.field"));
        require(!((Boolean)runtime.getMethod("enabled").invoke(null)).booleanValue()
                        && !((Boolean)runtime.getMethod("specialEnabled").invoke(null)).booleanValue()
                        && ("true".equals(auto.getProperty("auto.pk.upgrade"))
                            ? !((Boolean)runtime.getMethod("normalMonstersActive").invoke(null)).booleanValue()
                            : ((String)runtime.getMethod("normalLabel").invoke(null)).indexOf("[TẮT]") >= 0)
                        && !staticBoolean(farmType, auto.getProperty("native.farm.enabled.field"))
                        && !staticBoolean(combat.owner, combat.field)
                        && !staticBoolean(port, "finish") && !staticBoolean(port, "free")
                        && !staticBoolean(port, "gem") && !staticBoolean(port, "both")
                        && !staticBoolean(port, "fog") && !staticBoolean(port, "fire")
                        && !enhanceFlag.getBoolean(null) && !tradeFlag.getBoolean(null),
                "Enabling Maze turns off Auto Attack, Special, Farm, enhancement, trade and competing ModUpLv modes");

        seedGuardWorld();
        update(7000000L);
        Class<?> targetOwner = candidateClass(auto.getProperty("native.target.owner"));
        Object nativeTarget = staticValue(targetOwner, auto.getProperty("native.target.field"));
        Class<?> autoPlayerOwner = candidateClass(auto.getProperty("native.player.autoFlag.owner"));
        require(owns() && staticBoolean(port, "maze") && nativeTarget == guard
                        && staticBoolean(autoPlayerOwner, auto.getProperty("native.player.autoFlag.field")),
                "Actual map-109 guard tick claims its live nearby guard and enables native A");

        int[] blocked = new int[]{-40, -85, -98, -111, -112, -113, -116, -117, -89, -90, -91, -93, -95, -96};
        for (int i = 0; i < blocked.length; i++) {
            boolean consumed = ((Boolean)blockMazeAction.invoke(null, Integer.valueOf(blocked[i]))).booleanValue();
            require(consumed, "Maze guard consumes conflicting enable action " + blocked[i]);
        }
        require(!((Boolean)blockMazeAction.invoke(null, Integer.valueOf(-94))).booleanValue(),
                "Maze's own toggle remains available while its guard owns combat");

        int[] runtimeActions = new int[]{-111, -112, -113, -116, -117};
        for (int i = 0; i < runtimeActions.length; i++) {
            Object consumed = runtime.getMethod("handleAction", Integer.TYPE).invoke(null,
                    Integer.valueOf(runtimeActions[i]));
            require(Boolean.TRUE.equals(consumed), "Patched Auto Attack dispatcher consumes action " + runtimeActions[i]);
        }
        Class<?> dispatchOwner = candidateClass(mazeCatalog.getProperty("maze.dispatch.owner"));
        Method dispatch = dispatchOwner.getDeclaredMethod(mazeCatalog.getProperty("maze.dispatch.method"),
                Integer.TYPE, Integer.TYPE);
        dispatch.setAccessible(true);
        Object consumedFarm = dispatch.invoke(null, Integer.valueOf(-40), Integer.valueOf(0));
        require(Boolean.TRUE.equals(consumedFarm), "Patched native module dispatcher consumes Farm enable");
        Object farm = privateStatic(Class.forName(MOD_TEST), "allocate", new Class<?>[]{Class.class}, farmType);
        Method farmAction = farmType.getDeclaredMethod(auto.getProperty("native.farmAction.method"),
                Integer.TYPE, Integer.TYPE);
        farmAction.setAccessible(true);
        require(Boolean.TRUE.equals(invoke(farmAction, farm, Integer.valueOf(-40), Integer.valueOf(0)))
                        && !staticBoolean(farmType, auto.getProperty("native.farm.enabled.field")),
                "Direct Farm.onAction(-40) consumes an enable while Maze is active");
        Object enhancer = privateStatic(Class.forName(MOD_TEST), "allocate", new Class<?>[]{Class.class}, enhanceType);
        require(Boolean.TRUE.equals(invoke(enhanceAction, enhancer, Integer.valueOf(-85), Integer.valueOf(0)))
                        && !enhanceFlag.getBoolean(null),
                "Direct enhancement.onAction(-85) consumes an enable while Maze is active");
        invoke(tradeToggle, null, Boolean.TRUE);
        require(!tradeFlag.getBoolean(null), "Native trade ll(true) is consumed while Maze is active");
        require(!((Boolean)runtime.getMethod("enabled").invoke(null)).booleanValue()
                        && !((Boolean)runtime.getMethod("specialEnabled").invoke(null)).booleanValue()
                        && !staticBoolean(farmType, auto.getProperty("native.farm.enabled.field")),
                "Blocked menu actions leave the competing combat features OFF");
    }

    private static void verifyFarmPendingCastAndOpcode9() throws Exception {
        Object spec = pendingRequest();
        Object cast = get(spec, "cast");
        Class<?> castType = (Class<?>)get(spec, "castType");
        configureRequestClock(8000000L);
        int before = attackPacketCount();
        Object farm = privateStatic(Class.forName(MOD_TEST), "allocate", new Class<?>[]{Class.class}, farmType);
        invoke(farmType.getMethod("update"), farm);
        require(field(castType, auto.getProperty("request.tag.field")).getInt(cast) == 0
                        && field(playerType, "cG").getInt(player) == 2
                        && staticValue(candidateClass(auto.getProperty("native.target.owner")),
                                auto.getProperty("native.target.field")) == guard
                        && staticBoolean(candidateClass(auto.getProperty("native.player.autoFlag.owner")),
                                auto.getProperty("native.player.autoFlag.field")),
                "Farm OFF update preserves Maze's tag-zero pending cast, local attack state, target and native A");
        require(attackPacketCount() == before, "Farm OFF cleanup does not send an attack packet");

        Method request = castType.getMethod(auto.getProperty("hook.request.method"));
        invoke(request, cast);
        require(attackPacketCount() == before + 1 && hasOpcode(9),
                "Preserved real native monster request serializes one opcode-9 attack after Farm update");
    }

    private static void assertReleased(Object cast, Class<?> castType, String event) throws Exception {
        assertReleased(cast, castType, event, 0);
    }

    private static void assertReleased(Object cast, Class<?> castType, String event, int expectedLocalState)
            throws Exception {
        require(!owns()
                        && !staticBoolean(candidateClass(auto.getProperty("native.player.autoFlag.owner")),
                                auto.getProperty("native.player.autoFlag.field"))
                        && staticValue(candidateClass(auto.getProperty("native.target.owner")),
                                auto.getProperty("native.target.field")) == null
                        && field(castType, auto.getProperty("request.tag.field")).getInt(cast) == -1
                        && field(playerType, "cG").getInt(player) == expectedLocalState,
                event + " releases Maze ownership, native A, target and pending cast");
    }

    private static void reownAndSeed(long now) throws Exception {
        seedGuardWorld();
        setNumber(player, playerType, "cA", -1);
        update(now);
        require(owns(), "Map-109 guard can reacquire Maze ownership");
    }

    private static void verifyMapDeathDisconnectAndToggleCleanup() throws Exception {
        Object spec = pendingRequest();
        Object cast = get(spec, "cast");
        Class<?> castType = (Class<?>)get(spec, "castType");
        map(110);
        update(8000200L);
        require(staticBoolean(port, "maze"), "Map change preserves Maze intent");
        assertReleased(cast, castType, "Map change");
        require(!owns() && ((Boolean)blockMazeAction.invoke(null, Integer.valueOf(-111))).booleanValue(),
                "Leaving the guard map releases combat ownership while Maze intent still blocks competing modes");

        reownAndSeed(8000400L);
        spec = pendingRequest(); cast = get(spec, "cast"); castType = (Class<?>)get(spec, "castType");
        setNumber(player, playerType, "bs", 0);
        setNumber(player, playerType, "cG", 4);
        update(8000600L);
        assertReleased(cast, castType, "Player death", 4);
        setNumber(player, playerType, "bs", 100);

        reownAndSeed(8000800L);
        spec = pendingRequest(); cast = get(spec, "cast"); castType = (Class<?>)get(spec, "castType");
        Class<?> connectionOwner = candidateClass(auto.getProperty("native.connection.instance.owner"));
        Object connection = staticValue(connectionOwner, auto.getProperty("native.connection.instance.field"));
        field(connectionOwner, auto.getProperty("native.connection.connected.field")).setBoolean(connection, false);
        update(8001000L);
        assertReleased(cast, castType, "Disconnect");
        field(connectionOwner, auto.getProperty("native.connection.connected.field")).setBoolean(connection, true);

        reownAndSeed(8001200L);
        spec = pendingRequest(); cast = get(spec, "cast"); castType = (Class<?>)get(spec, "castType");
        action(-94);
        assertReleased(cast, castType, "Maze OFF toggle");
        require(!staticBoolean(port, "maze"), "Maze OFF clears its intent");
    }

    private static void verifyLegacyFarmCleanupOutsideOwnership() throws Exception {
        resetRuntime();
        require(!owns() && !staticBoolean(port, "maze"), "Legacy Farm cleanup runs outside Maze ownership");
        setStatic(farmType, auto.getProperty("native.farm.enabled.field"), Boolean.FALSE);
        CombatFlag combat = combatFlag(candidate, farmType.getName(), auto.getProperty("native.farm.enabled.field"));
        setStatic(combat.owner, combat.field, Boolean.FALSE);
        Object spec = pendingRequest();
        Object cast = get(spec, "cast");
        Class<?> castType = (Class<?>)get(spec, "castType");
        Class<?> targetOwner = candidateClass(auto.getProperty("native.target.owner"));
        Class<?> autoPlayerOwner = candidateClass(auto.getProperty("native.player.autoFlag.owner"));
        setStatic(targetOwner, auto.getProperty("native.target.field"), guard);
        setStatic(autoPlayerOwner, auto.getProperty("native.player.autoFlag.field"), Boolean.TRUE);
        configureRequestClock(9000000L);
        int before = attackPacketCount();
        Object farm = privateStatic(Class.forName(MOD_TEST), "allocate", new Class<?>[]{Class.class}, farmType);
        invoke(farmType.getMethod("update"), farm);
        require(field(castType, auto.getProperty("request.tag.field")).getInt(cast) == -1
                        && field(playerType, "cG").getInt(player) == 0
                        && !staticBoolean(autoPlayerOwner, auto.getProperty("native.player.autoFlag.field"))
                        && staticValue(targetOwner, auto.getProperty("native.target.field")) == null,
                "Ordinary Farm OFF cleanup still cancels and releases a non-Maze pending cast");
        require(attackPacketCount() == before, "Ordinary Farm OFF cleanup emits no attack packet");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4)
            throw new IllegalArgumentException("candidate.jar auto-attack.properties dungeon.properties [maze.properties]");
        candidate = new File(args[0]);
        auto = load(new File(args[1]));
        portCatalog = load(new File(args[2]));
        mazeCatalog = args.length == 4 ? load(new File(args[3])) : portCatalog;

        // Reuse the candidate-backed probe loader, ready world, native packet queues and symbols.
        privateStatic(Class.forName(MOD_TEST), "main", new Class<?>[]{String[].class},
                (Object)new String[]{args[0], args[1], args[2]});
        Class<?> modTest = Class.forName(MOD_TEST);
        loader = (ClassLoader)field(modTest, "loader").get(null);
        playerType = (Class<?>)field(modTest, "playerType").get(null);
        port = Class.forName(MOD_UP_LV, true, loader);
        entityType = candidateClass(auto.getProperty("native.entity.owner"));
        farmType = candidateClass(auto.getProperty("native.farm.enabled.owner"));
        runtime = candidateClass("luna/features/AutoAttackRuntime");
        player = privateStatic(modTest, "player", new Class<?>[0]);
        verifyAbiAndHooks();
        verifyMazeEnableAndBlockedActions();
        verifyFarmPendingCastAndOpcode9();
        verifyMapDeathDisconnectAndToggleCleanup();
        verifyLegacyFarmCleanupOutsideOwnership();
        verifyActorReplacementRelease();
        System.out.println("HungBySonMazeExclusiveBinaryTest PASS checks=" + checks);
    }
}
