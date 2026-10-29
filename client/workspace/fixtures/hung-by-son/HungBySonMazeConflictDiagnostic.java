import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Reproduces the native Farm-off cleanup that collides with Auto Mê cung's native auto toggle.
 * Uses the existing binary test's probe loader and ready-world setup against a candidate JAR.
 */
public final class HungBySonMazeConflictDiagnostic {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static Object getStatic(Class<?> owner, String name) throws Exception {
        return field(owner, name).get(null);
    }

    private static void setStatic(Class<?> owner, String name, Object value) throws Exception {
        field(owner, name).set(null, value);
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try { return method.invoke(target, args); }
        catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new RuntimeException(cause);
        }
    }

    private static Object privateStatic(Class<?> owner, String name, Class<?>[] signature, Object... args) throws Exception {
        Method m = owner.getDeclaredMethod(name, signature);
        m.setAccessible(true);
        return invoke(m, null, args);
    }

    private static Object testStatic(String name) throws Exception {
        Class<?> test = Class.forName("HungBySonModUpLvBinaryTest");
        return getStatic(test, name);
    }

    private static boolean enabled(Class<?> runtime, String method) throws Exception {
        return ((Boolean)runtime.getMethod(method).invoke(null)).booleanValue();
    }

    private static String combatModuleFlag(File jar, String farmName, String farmEnabledField) throws Exception {
        JarFile archive = new JarFile(jar);
        try {
            ClassNode farm = new ClassNode();
            new ClassReader(archive.getInputStream(archive.getEntry(farmName.replace('.', '/') + ".class")))
                    .accept(farm, 0);
            MethodNode update = null;
            for (MethodNode m : farm.methods)
                if ("codexFarmUpdate".equals(m.name) && "()V".equals(m.desc)) update = m;
            if (update == null) throw new NoSuchMethodException("codexFarmUpdate");
            boolean foundFarmFlag = false;
            for (AbstractInsnNode n : update.instructions) {
                if (!(n instanceof FieldInsnNode) || n.getOpcode() != Opcodes.GETSTATIC) continue;
                FieldInsnNode f = (FieldInsnNode)n;
                if (!"Z".equals(f.desc)) continue;
                if (f.owner.replace('/', '.').equals(farmName) && f.name.equals(farmEnabledField)) {
                    foundFarmFlag = true;
                    continue;
                }
                if (foundFarmFlag) return f.owner.replace('/', '.') + "#" + f.name;
            }
            throw new IllegalStateException("Farm combat-module flag not found");
        } finally { archive.close(); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("candidate.jar auto-attack.properties port.properties");
        File candidate = new File(args[0]);
        Properties auto = (Properties)privateStatic(Class.forName("HungBySonModUpLvBinaryTest"), "load",
                new Class<?>[]{File.class}, new File(args[1]));
        Properties port = (Properties)privateStatic(Class.forName("HungBySonModUpLvBinaryTest"), "load",
                new Class<?>[]{File.class}, new File(args[2]));

        // Run the existing candidate-backed setup, then use its live private probe objects.
        privateStatic(Class.forName("HungBySonModUpLvBinaryTest"), "main",
                new Class<?>[]{String[].class}, (Object)new String[]{args[0], args[1], args[2]});
        ClassLoader loader = (ClassLoader)testStatic("loader");
        Class<?> playerType = (Class<?>)testStatic("playerType");
        Class<?> farmType = Class.forName(auto.getProperty("native.farm.enabled.owner").replace('/', '.'), true, loader);
        Class<?> runtime = Class.forName("luna.features.AutoAttackRuntime", true, loader);
        Object player = privateStatic(Class.forName("HungBySonModUpLvBinaryTest"), "player",
                new Class<?>[0]);

        // The prior dungeon test enabled Auto Attack; force every combat-related owner OFF.
        if (((String)runtime.getMethod("normalLabel").invoke(null)).indexOf("[BẬT]") >= 0)
            runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-117));
        if (enabled(runtime, "specialEnabled"))
            runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-116));
        if (enabled(runtime, "enabled"))
            runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-111));
        setStatic(farmType, auto.getProperty("native.farm.enabled.field"), Boolean.FALSE);
        String combatField = combatModuleFlag(candidate, farmType.getName(), auto.getProperty("native.farm.enabled.field"));
        Class<?> combatOwner = Class.forName(combatField.substring(0, combatField.indexOf('#')), true, loader);
        setStatic(combatOwner, combatField.substring(combatField.indexOf('#') + 1), Boolean.FALSE);
        Class<?> combatOptions = Class.forName(HungBySonModUpLvBinaryTest.n("lliiliil"), true, loader);
        setStatic(combatOptions, HungBySonModUpLvBinaryTest.n("lilliililll"), Boolean.TRUE);
        setStatic(combatOptions, HungBySonModUpLvBinaryTest.n("lilliililli"), Boolean.FALSE);
        Class<?> portType = (Class<?>)testStatic("port");
        setStatic(portType, "maze", Boolean.TRUE);
        privateStatic(Class.forName("HungBySonModUpLvBinaryTest"), "map",
                new Class<?>[]{Integer.TYPE}, Integer.valueOf(109));
        require(!enabled(runtime, "enabled") && !enabled(runtime, "specialEnabled")
                        && ((String)runtime.getMethod("normalLabel").invoke(null)).indexOf("[TẮT]") >= 0
                        && !field(farmType, auto.getProperty("native.farm.enabled.field")).getBoolean(null),
                "Auto Attack, Special, normal monsters, Farm, and the independent combat module are OFF");

        // A maze guard tick sets the selected target, enables native auto, and starts a local
        // untagged cast. Model that exact state before the next ordinary Farm update.
        Object guard = privateStatic(Class.forName("HungBySonModUpLvBinaryTest"), "monster",
                new Class<?>[]{Integer.TYPE, Integer.TYPE, Integer.TYPE}, Integer.valueOf(3), Integer.valueOf(30), Integer.valueOf(0));
        privateStatic(Class.forName("HungBySonModUpLvBinaryTest"), "world", new Class<?>[]{Object[].class}, (Object)new Object[]{guard});
        privateStatic(Class.forName("HungBySonModUpLvBinaryTest"), "at",
                new Class<?>[]{Object.class, Integer.TYPE, Integer.TYPE}, player, Integer.valueOf(0), Integer.valueOf(0));
        field(playerType, "cG").setInt(player, 0);
        privateStatic(portType, "mazeTick", new Class<?>[]{playerType, Integer.TYPE, Long.TYPE},
                player, Integer.valueOf(109), Long.valueOf(5000000L));
        Class<?> targetOwner = Class.forName(auto.getProperty("native.target.owner").replace('/', '.'), true, loader);
        require(field(portType, "maze").getBoolean(null)
                        && field(playerType, auto.getProperty("native.player.autoFlag.field")).getBoolean(null)
                        && getStatic(targetOwner, auto.getProperty("native.target.field")) == guard,
                "Actual Auto Mê cung guard tick enables native A and selects its map-109 guard");
        field(playerType, "cG").setInt(player, 2);
        field(playerType, "cA").setInt(player, 0);
        field(playerType, "cR").setInt(player, 2);

        Class<?> castType = Class.forName(auto.getProperty("cast.owner").replace('/', '.'), true, loader);
        Object cast = privateStatic(Class.forName("HungBySonModUpLvBinaryTest"), "allocate",
                new Class<?>[]{Class.class}, castType);
        field(playerType, "di").set(player, cast);
        field(castType, auto.getProperty("cast.actor.field")).set(cast, player);
        field(castType, auto.getProperty("cast.target.field")).set(cast, guard);
        field(castType, auto.getProperty("request.tag.field")).setInt(cast, 0);
        require(field(playerType, auto.getProperty("native.player.autoFlag.field")).getBoolean(null)
                        && field(playerType, "cG").getInt(player) == 2
                        && field(targetOwner, auto.getProperty("native.target.field")).get(null) == guard
                        && field(castType, auto.getProperty("request.tag.field")).getInt(cast) == 0,
                "Seeded maze state: native A on, target selected, local cG=2 tag-zero cast pending");

        int beforePackets = ((java.util.List<?>)getStatic(Class.forName("HungBySonAutoAttackBinaryTest"), "nativePackets")).size();
        Object farm = privateStatic(Class.forName("HungBySonModUpLvBinaryTest"), "allocate",
                new Class<?>[]{Class.class}, farmType);
        Method farmUpdate = farmType.getMethod("update");
        invoke(farmUpdate, farm);
        int afterPackets = ((java.util.List<?>)getStatic(Class.forName("HungBySonAutoAttackBinaryTest"), "nativePackets")).size();

        require(!field(playerType, auto.getProperty("native.player.autoFlag.field")).getBoolean(null),
                "Actual Farm-off update clears native A through its OFF cleanup branch");
        require(field(castType, auto.getProperty("request.tag.field")).getInt(cast) == -1,
                "Farm-off cleanup invalidates the untagged pending cast");
        require(field(playerType, "cG").getInt(player) == 0,
                "Farm-off cleanup releases the local attack state");
        require(field(targetOwner, auto.getProperty("native.target.field")).get(null) == null,
                "Farm-off cleanup clears the selected maze target");
        require(afterPackets == beforePackets, "No attack packet is enqueued during the Farm-off cleanup pass");
        privateStatic(portType, "mazeTick", new Class<?>[]{playerType, Integer.TYPE, Long.TYPE},
                player, Integer.valueOf(109), Long.valueOf(5000140L));
        require(field(portType, "maze").getBoolean(null)
                        && field(playerType, auto.getProperty("native.player.autoFlag.field")).getBoolean(null)
                        && getStatic(targetOwner, auto.getProperty("native.target.field")) == guard,
                "Next Auto Mê cung guard tick re-enables native A and reselects the same target");
        System.out.println("HungBySonMazeConflictDiagnostic PASS");
    }
}
