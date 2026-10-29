import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Runs native tile lookup, actor collision, and route search on synthetic maps. */
public final class HungBySonNoclipCollisionHostTest implements Opcodes {
    static int checks;
    static void require(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static sun.misc.Unsafe unsafe() throws Exception { Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return (sun.misc.Unsafe)f.get(null); }
    static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    static Method method(Class<?> c, String name, Class<?>... types) throws Exception { Method m = c.getDeclaredMethod(name, types); m.setAccessible(true); return m; }
    static ClassNode cls(JarFile jar, String name) throws Exception {
        InputStream in = jar.getInputStream(jar.getJarEntry(name + ".class"));
        ByteArrayOutputStream out = new ByteArrayOutputStream(); try { byte[] b = new byte[8192]; for (int n; (n = in.read(b)) != -1;) out.write(b, 0, n); } finally { in.close(); }
        ClassNode c = new ClassNode(ASM9); new ClassReader(out.toByteArray()).accept(c, 0); return c;
    }
    static final class Loader extends URLClassLoader {
        final JarFile jar;
        Loader(File source) throws Exception { super(new URL[]{source.toURI().toURL()}, HungBySonNoclipCollisionHostTest.class.getClassLoader()); jar = new JarFile(source); }
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String internal = name.replace('.', '/');
            if (jar.getJarEntry(internal + ".class") == null) return super.findClass(name);
            try {
                ClassNode c = cls(jar, internal);
                // Remove startup-only resource/RMS work; all exercised native method bodies stay intact.
                for (MethodNode m : c.methods) if (m.name.equals("<clinit>")) {
                    m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null; m.instructions.add(new InsnNode(RETURN));
                }
                ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(out); byte[] data = out.toByteArray(); return defineClass(name, data, 0, data.length);
            } catch (Exception e) { throw new ClassNotFoundException(name, e); }
        }
        Class<?> type(String name) throws Exception {
            Class<?> c = Class.forName(name.replace('/', '.'), true, this);
            for (Field f : c.getDeclaredFields()) if (Modifier.isStatic(f.getModifiers()) && f.getType() == Random.class) {
                f.setAccessible(true); if (f.get(null) == null) f.set(null, new Random(0));
            }
            return c;
        }
        public void close() throws IOException { try { jar.close(); } finally { super.close(); } }
    }
    static final class Scene implements AutoCloseable {
        final Loader loader; final Object map, world; final Class<?> mapType, menuType, entityType;
        final Method query, route; final Field toggle; final Properties p;
        Scene(File jar, Properties symbols, boolean candidate) throws Exception {
            loader = new Loader(jar); p = symbols; mapType = loader.type("cs");
            map = unsafe().allocateInstance(mapType); menuType = loader.type(p.getProperty("noclip.menu.owner"));
            entityType = loader.type(p.getProperty("candidate.entity.owner"));
            Class<?> worldType = loader.type(p.getProperty("noclip.path.owner")); world = unsafe().allocateInstance(worldType);
            query = method(mapType, "a", int.class, int.class);
            route = method(worldType, p.getProperty("noclip.path.method"), int.class, int.class, int.class, int.class, int.class);
            toggle = candidate ? field(menuType, "codexNoclipEnabled") : null;
            if (candidate) require(!toggle.getBoolean(null), "fresh JVM toggle defaults OFF");
            field(mapType, "l").setInt(null, 24);
            field(mapType, "e").setInt(map, 7); field(mapType, "f").setInt(map, 7); field(mapType, "B").setInt(map, 49);
            field(mapType, "z").setInt(map, 144); field(mapType, "A").setInt(map, 144);
            field(mapType, "n").set(map, new int[49]); field(mapType, "p").set(map, new byte[7][7]);
            ClassNode mapClass = cls(loader.jar, "cs");
            for (MethodNode m : mapClass.methods) if (m.name.equals("a") && m.desc.equals("(II)I")) {
                for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof FieldInsnNode && i.getOpcode() == GETSTATIC) {
                    FieldInsnNode f = (FieldInsnNode)i;
                    if (!f.owner.equals("cs") && f.desc.equals("I")) field(loader.type(f.owner), f.name).setInt(null, 24);
                }
            }
            ClassNode worldClass = cls(loader.jar, worldType.getName().replace('.', '/'));
            for (MethodNode m : worldClass.methods) if (m.name.equals(p.getProperty("noclip.path.method")) && m.desc.equals("(IIIII)[S"))
                for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof FieldInsnNode && i.getOpcode() == GETSTATIC) {
                    FieldInsnNode f = (FieldInsnNode)i;
                    if (f.desc.equals("Lcs;")) field(loader.type(f.owner), f.name).set(null, map);
                }
            ClassNode actorClass = cls(loader.jar, entityType.getName().replace('.', '/'));
            for (MethodNode m : actorClass.methods) if (m.name.equals("a") && m.desc.equals("(Z)V"))
                for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof MethodInsnNode && i.getOpcode() == INVOKESTATIC) {
                    MethodInsnNode call = (MethodInsnNode)i;
                    if (loader.jar.getJarEntry(call.owner + ".class") != null) loader.type(call.owner);
                }
        }
        void enabled(boolean value) throws Exception { if (toggle != null) toggle.setBoolean(null, value); }
        int[] raw() throws Exception { return (int[])field(mapType, "n").get(map); }
        int query(int x, int y) throws Exception { return ((Integer)query.invoke(map, x, y)).intValue(); }
        void wall(int value, boolean vertical) throws Exception { Arrays.fill(raw(), 0); for (int n = 0; n < 7; n++) raw()[vertical ? n * 7 + 3 : 3 * 7 + n] = value; }
        short[] route() throws Exception { return (short[])route.invoke(world, 1, 3, 5, 3, 30); }
        int move(int x, int y, int dx, int dy) throws Exception {
            Object actor = unsafe().allocateInstance(entityType);
            field(entityType, "aZ").setInt(actor, x); field(entityType, "ba").setInt(actor, y);
            field(entityType, "bc").setInt(actor, dx); field(entityType, "bd").setInt(actor, dy);
            method(entityType, "a", boolean.class).invoke(actor, false);
            return field(entityType, dx != 0 ? "bc" : "bd").getInt(actor);
        }
        public void close() throws Exception { loader.close(); }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("before.jar candidate.jar noclip.properties");
        Properties p = new Properties(); InputStream in = new FileInputStream(args[2]); try { p.load(in); } finally { in.close(); }
        try (Scene before = new Scene(new File(args[0]), p, false); Scene after = new Scene(new File(args[1]), p, true)) {
            int[] kinds = {-1, 0, 1, 2, 3, 7};
            for (int kind : kinds) {
                Arrays.fill(before.raw(), kind); Arrays.fill(after.raw(), kind);
                for (int y = 12; y < 168; y += 24) for (int x = 12; x < 168; x += 24)
                    require(after.query(x, y) == before.query(x, y), "OFF preserves native cell query " + kind);
                after.enabled(true);
                require(after.query(60, 60) == (kind == 1 || kind == -1 ? 0 : kind), "ON bypasses only wall kinds " + kind);
                require(after.raw()[16] == kind, "raw collision is never rewritten");
                require(after.query(-1, 60) == 1 && after.query(168, 60) == 1 && after.query(60, -1) == 1 && after.query(60, 168) == 1,
                        "ON retains all four native boundaries");
                after.enabled(false); require(after.query(60, 60) == before.query(60, 60), "toggle OFF restores native query immediately");
            }
            for (int wall : new int[]{1, -1}) {
                before.wall(wall, true); after.wall(wall, true); int[] snapshot = after.raw().clone();
                require(Arrays.equals(before.route(), after.route()), "OFF preserves native route for wall " + wall);
                require(((byte[][])field(after.mapType, "p").get(after.map))[3][3] == -1, "OFF route grid contains wall");
                after.enabled(true); short[] openRoute = after.route();
                require(openRoute != null && !Arrays.equals(openRoute, before.route()), "ON creates route across solid wall " + wall);
                require(((byte[][])field(after.mapType, "p").get(after.map))[3][3] >= 0, "ON route grid opens wall");
                require(Arrays.equals(snapshot, after.raw()), "route search does not rewrite raw collisions");
                after.enabled(false); require(Arrays.equals(before.route(), after.route()), "OFF restores wall after previous ON search");
            }
            // Native directional collision resolves keys before the actor tick applies the displacement.
            for (int direction = 0; direction < 4; direction++) {
                boolean verticalWall = direction < 2; before.wall(1, verticalWall); after.wall(1, verticalWall);
                int x = direction == 0 ? 68 : direction == 1 ? 100 : 84;
                int y = direction == 2 ? 68 : direction == 3 ? 100 : 84;
                int dx = direction == 0 ? 6 : direction == 1 ? -6 : 0;
                int dy = direction == 2 ? 6 : direction == 3 ? -6 : 0;
                after.enabled(false); int original = before.move(x, y, dx, dy), off = after.move(x, y, dx, dy);
                require(original != (dx != 0 ? dx : dy), "baseline blocks requested key displacement at wall " + direction);
                require(off == original, "OFF native directional collision matches baseline " + direction);
                after.enabled(true); require(after.move(x, y, dx, dy) == (dx != 0 ? dx : dy), "ON retains key displacement through wall " + direction);
                after.enabled(false); require(after.move(x, y, dx, dy) == original, "OFF restores key collision " + direction);
            }
        }
        try (Scene restarted = new Scene(new File(args[1]), p, true)) { require(!restarted.toggle.getBoolean(null), "new launch resets Noclip to OFF"); }
        System.out.println("HungBySonNoclipCollisionHostTest PASS (" + checks + " checks)");
    }
}
