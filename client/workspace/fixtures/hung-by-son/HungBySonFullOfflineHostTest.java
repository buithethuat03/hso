import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Exercises release classes; only the outgoing packet queue is replaced with an in-memory sink. */
public final class HungBySonFullOfflineHostTest implements Opcodes {
    static ClassLoader loader;
    static sun.misc.Unsafe unsafe;
    static int checks;
    static final List<String> packets = new ArrayList<String>();
    static String n(String suffix) { StringBuilder s = new StringBuilder(); for (int i = suffix.length(); i < 100; i++) s.append('i'); return s.append(suffix).toString(); }
    static Class<?> type(String shortName) throws Exception { return Class.forName(shortName.startsWith("~") ? n(shortName.substring(1)) : shortName, false, loader); }
    static Field field(Class<?> c, String shortName) throws Exception {
        String name = shortName.startsWith("~") ? n(shortName.substring(1)) : shortName;
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(shortName);
    }
    static void set(Object object, String owner, String name, Object value) throws Exception { field(type(owner), name).set(object, value); }
    static Object get(Object object, String owner, String name) throws Exception { return field(type(owner), name).get(object); }
    static Object alloc(String owner) throws Exception { return unsafe.allocateInstance(type(owner)); }
    static void expect(String name, Object actual, Object expected) {
        checks++;
        if (!String.valueOf(expected).equals(String.valueOf(actual))) throw new AssertionError(name + ": " + actual + " != " + expected);
        System.out.println("PASS " + name);
    }
    static Object item(int id, int category, String name, int level, int subtype) throws Exception {
        Object item = alloc("~llilli"); set(item, "~llilli", "O", id); set(item, "~llilli", "~liiill", category);
        set(item, "~llilli", "~liiiiliii", name); set(item, "~llilli", "~llilil", (byte)level); set(item, "~llilli", "~llililil", (byte)subtype);
        return item;
    }
    static Object vector(Object... values) throws Exception {
        Object v = type("et").getConstructor().newInstance(); Method add = type("et").getMethod(n("l"), Object.class);
        for (Object value : values) add.invoke(v, value); return v;
    }
    public static void capture(Object message) throws Exception {
        byte op = field(message.getClass(), "a").getByte(message);
        byte[] payload = (byte[])message.getClass().getMethod(n("lliilil")).invoke(message);
        packets.add(op + ":" + Arrays.toString(payload));
    }
    static final class SinkLoader extends URLClassLoader {
        SinkLoader(File jar) throws Exception { super(new URL[]{jar.toURI().toURL()}, HungBySonFullOfflineHostTest.class.getClassLoader()); }
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.equals(n("lililili"))) return super.findClass(name);
            try {
                InputStream in = getResourceAsStream(name + ".class"); ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] block = new byte[8192]; for (int count; (count = in.read(block)) != -1;) data.write(block, 0, count); in.close();
                ClassNode c = new ClassNode(ASM9); new ClassReader(data.toByteArray()).accept(c, 0);
                int patched = 0;
                for (MethodNode m : c.methods) if (m.name.equals(n("lliiili")) && m.desc.equals("(L" + n("lllllil") + ";)V")) {
                    m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
                    m.instructions.add(new VarInsnNode(ALOAD, 1));
                    m.instructions.add(new MethodInsnNode(INVOKESTATIC, "HungBySonFullOfflineHostTest", "capture", "(Ljava/lang/Object;)V", false));
                    m.instructions.add(new InsnNode(RETURN)); patched++;
                }
                if (patched != 1) throw new AssertionError("Packet sink missing");
                ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(out); byte[] bytes = out.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (Exception ex) { throw new ClassNotFoundException(name, ex); }
        }
    }
    static boolean execute(Method method, String key, int... args) throws Exception {
        return (Boolean)method.invoke(null, key, 0, args.length == 0 ? null : args);
    }
    static void lastPacket(String name, String expected) { expect(name, packets.get(packets.size() - 1), expected); }

    public static void main(String[] args) throws Exception {
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true); unsafe = (sun.misc.Unsafe)uf.get(null);
        loader = new SinkLoader(new File(args[0]));
        int linked = 0;
        try (JarFile jar = new JarFile(args[0]); URLClassLoader clean = new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()}, HungBySonFullOfflineHostTest.class.getClassLoader())) {
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                String name = e.nextElement().getName(); if (!name.endsWith(".class")) continue;
                Class<?> c = Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false, clean);
                c.getDeclaredMethods(); c.getDeclaredFields(); c.getDeclaredConstructors(); linked++;
            }
        }
        int expectedClasses = args.length > 1 ? Integer.parseInt(args[1]) : 221;
        expect("all release classes link", linked, expectedClasses);
        Class<?> local = Class.forName("HungBySonOfflineLocal", true, loader);
        Method smart = local.getMethod("smartMode", int.class), getter = type("~lilllill").getMethod(n("lili"), String.class, int.class);
        Method verified = type("~lilllill").getMethod(n("liii"), String.class, int.class);
        Method raw = type("~lilllill").getMethod(n(""), String.class, int.class, int[].class);
        for (int level = -1; level <= 16; level++) {
            int expected = level >= 8 && level <= 10 ? 2 : 0;
            expect("smart current +" + level, smart.invoke(null, level), expected);
            expect("real numeric getter +" + level, type("~lilllill").getMethod(n(""), String.class, int.class, int.class).invoke(null, "ch.bua_theo_cap", level, -1), expected);
        }
        for (String key : HungBySonFullOfflinePatcher.KEYS) expect("local entitlement " + key, verified.invoke(null, key, 0), true);
        expect("unknown key not authorized", verified.invoke(null, "unknown.key", 0), false);
        expect("unknown key no remote fallback", getter.invoke(null, "unknown.key", 0), "local");
        expect("unknown execution sends nothing", raw.invoke(null, "unknown.key", 0, null), false);
        expect("unknown packet count", packets.size(), 0);
        for (String flag : HungBySonFullOfflinePatcher.FLAGS) expect("startup entitlement " + flag, get(null, "~lliiliil", "~" + flag), true);
        expect("license overlay unlocked", get(null, "~lliiliil", "~lilliililli"), false);
        String guide = (String)get(null, "~lliiliil", "~lilliliiili");
        // Releases before the ModUpLv Đập đồ port describe Auto Cường Hóa; the port replaces that paragraph.
        expect("local instructions supplied", guide.contains("+8, +9 hoặc +10") && guide.contains("Auto Cường Hóa")
                || guide.contains("MOD HungDZ > Đập đồ") && !guide.contains("Thêm vào DS"), true);
        type("~lliiliil").getMethod("run").invoke(alloc("~lliiliil"));
        type("~lliiliil").getMethod("update").invoke(null);
        expect("activation worker produces no packet", packets.size(), 0);

        Object player = alloc("~lliiil"); set(player, "~liiiliii", "aZ", 432); set(player, "~liiiliii", "ba", 528);
        set(null, "~liiliii", "~llilliill", player); Object map = alloc("cs"); set(map, "cs", "d", 1); set(null, "~liilllii", "~liiililiiii", map);
        Object weapon = item(100, 3, "Kiếm", 8, 0), clover3 = item(771, 7, "Cỏ 3 lá", 0, 11), clover4 = item(772, 7, "Cỏ 4 lá", 0, 11);
        Object wrong = item(773, 4, "Cỏ 4 lá", 0, 11), other = item(774, 7, "Cỏ 4 lá", 0, 9);
        set(null, "~llilli", "~liiillili", vector(wrong, other, weapon, clover3, clover4));
        expect("live three-leaf template", getter.invoke(null, "ch.ma_bua", 1), "val|771");
        expect("live four-leaf template", getter.invoke(null, "ch.ma_bua", 2), "val|772");
        expect("clover invalid mode", local.getMethod("cloverId", int.class).invoke(null, 0), -1);
        set(null, "~lliillii", "~liilliliili", true); set(null, "~lliillii", "~lillliiliil", 15); set(null, "~lliillii", "~lillliilili", 3);
        set(null, "~lllili", "~liiiliiii", (byte)0); set(null, "~lllili", "~liliiliil", false);
        Method moduleExec = type("~lliillii").getDeclaredMethod(n("l"), String.class, int.class, int[].class); moduleExec.setAccessible(true);
        set(map, "cs", "d", 2);
        expect("native NPC map gate", execute(moduleExec, "ch.chon_do", 100), false); set(map, "cs", "d", 1);
        set(null, "HungBySonOfflineLocal", "npcReady", true); // session setup is exercised by the dedicated NPC fixture
        expect("choose equipment", execute(moduleExec, "ch.chon_do", 100), true); lastPacket("native equipment payload", "67:[0, 0, 100, 3]");
        Object selected = alloc("~lililiii"); set(selected, "~llilli", "O", 100); set(selected, "~llilli", "~liiill", 3);
        set(null, "~lllili", "~liillllli", selected);
        expect("real-time pacing before clover", execute(moduleExec, "ch.chon_bua", 772), false);
        set(null, "HungBySonOfflineLocal", "lastActionAt", System.currentTimeMillis() - 501L);
        expect("choose clover", execute(moduleExec, "ch.chon_bua", 772), true); lastPacket("native clover payload", "67:[0, 3, 4, 7]");
        set(null, "HungBySonOfflineLocal", "lastActionAt", System.currentTimeMillis() - 501L);
        int before = packets.size();
        expect("wait for clover ack", execute(moduleExec, "ch.nang"), false); expect("no packet before clover ack", packets.size(), before);
        set(null, "~lllili", "~liliiliil", true);
        expect("enhance with gold", execute(moduleExec, "ch.nang"), true); lastPacket("native gold payload", "67:[2, 0, 0, 0]");
        before = packets.size(); expect("wait for game result", execute(moduleExec, "ch.chon_do", 100), false); expect("no repeated request", packets.size(), before);

        // Exercise the actual result-handler hook, without changing its parser or the local class.
        ByteArrayOutputStream data = new ByteArrayOutputStream(); DataOutputStream stream = new DataOutputStream(data);
        stream.writeByte(2); stream.writeByte(3); stream.writeUTF("Kết quả cường hóa"); stream.close();
        Object response = type("~lllllil").getConstructor(byte.class, byte[].class).newInstance((byte)67, data.toByteArray());
        Method receive = type("~lllllll").getDeclaredMethod(n("liiiili"), type("~lllllil")); receive.setAccessible(true); receive.invoke(null, response);
        expect("native response clears pending", get(null, "HungBySonOfflineLocal", "waiting"), false);
        expect("native animation remains active", get(null, "~lllili", "~liiiliiii"), (byte)1);
        expect("wait for native result animation", execute(moduleExec, "ch.chon_do", 100), false);
        expect("animation sends nothing", packets.size(), before);
        set(null, "~lllili", "~liiiliiii", (byte)0); set(weapon, "~llilli", "~llilil", (byte)11);
        set(null, "HungBySonOfflineLocal", "lastActionAt", System.currentTimeMillis() - 501L);
        expect("clear clover outside +8..10", execute(moduleExec, "ch.chon_do", 100), false); lastPacket("native deselect payload", "67:[1, 3, 4, 7]");
        set(null, "~lllili", "~liliiliil", false);
        set(null, "HungBySonOfflineLocal", "lastActionAt", System.currentTimeMillis() - 501L);
        expect("resume at +11 without clover", execute(moduleExec, "ch.chon_do", 100), true);
        expect("new selection requires fresh ack", get(null, "~lllili", "~liillllli"), null);
        set(null, "~lllili", "~liillllli", selected);
        set(null, "HungBySonOfflineLocal", "lastActionAt", System.currentTimeMillis() - 501L);
        expect("+11 gold enhancement", execute(moduleExec, "ch.nang"), true); lastPacket("still pays gold", "67:[2, 0, 0, 0]");
        set(null, "HungBySonOfflineLocal", "requestAt", System.currentTimeMillis() - 12001L);
        before = packets.size(); expect("unanswered request stops", execute(moduleExec, "ch.chon_do", 100), false);
        expect("timeout disables enhancement", get(null, "~lliillii", "~liilliliili"), false); expect("timeout no new request", packets.size(), before);
        set(null, "~lliillii", "~liilliliili", true); set(null, "HungBySonOfflineLocal", "waiting", true);
        type("~lliillii").getMethod(n("llilllll")).invoke(null);
        expect("native stop clears local pending", get(null, "HungBySonOfflineLocal", "waiting"), false);
        set(null, "~lliillii", "~liilliliili", true); set(null, "~llilli", "~liiillili", vector(weapon, clover3));
        expect("missing requested clover returns negative", getter.invoke(null, "ch.ma_bua", 2), "val|-1");
        expect("missing clover stops instead of changing mode", get(null, "~lliillii", "~liilliliili"), false);
        expect("disabled module sends nothing", execute(moduleExec, "ch.nang"), false);

        // Existing server list indices and the newly added endpoint remain in the published bytes.
        for (Field f : type("~llilill").getFields()) if (f.getType() == String[][].class) {
            String[][] rows = (String[][])f.get(null);
            if (rows.length == 10) { expect("Bạch Hổ hostname", Arrays.toString(rows[9]), "[Bạch Hổ, hs8.teamobi.com]"); }
        }
        System.out.println("ALL PASS checks=" + checks + " outgoing_packets_captured=" + packets.size());
        ((URLClassLoader)loader).close();
    }
}
