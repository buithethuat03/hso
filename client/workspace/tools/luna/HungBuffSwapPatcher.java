import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Auto thay đồ khi BUFF (docs/HUNG_BY_SON_BUFF_SWAP.md). HungBySonBuffSwap (compiled from
 * HungBySonBuffSwap.java.in against the pinned input) is added and three native classes are edited,
 * each edit with an exact inverse:
 *  - Tùy chọn tự đánh ~lliiiiil.~lilllili: HungBySonBuffSwap.addAttackRows(menu) right before the
 *    "<< Quay Lại" row (action -39);
 *  - opcode-40 buff answer ~lllllll.~liilll: HungBySonBuffSwap.onBuff(stream) right after
 *    HungBySonSkillStats.buff(stream), on the same message stream;
 *  - module loader: HungBySonBuffSwap.register() between HungBySonStamina and the ModUpLv port.
 */
public final class HungBuffSwapPatcher implements Opcodes {
    static final String INPUT = "14E2AD66697A67472C7FA90D1786822B98AF787598143C258EED748A7DD8D038";
    static final String CATALOG = "build/hung-stamina-20261027-a/auto-pk.properties";
    static final String TEMPLATE = "tools/luna/HungBySonBuffSwap.java.in";
    static final String PORT = "HungBySonBuffSwap", MODUPLV = "HungBySonModUpLv", STAMINA = "HungBySonStamina", STATS = "HungBySonSkillStats";
    static final String CONFIG = n("lliiiiil"), READER = n("lllllll"), LOADER = n("lliillll"), MESSAGE = n("lllllil");
    static final String MENU_DESC = "(Let;)V", STREAM_DESC = "(Ljava/io/DataInputStream;)V", ANSWER_DESC = "(L" + MESSAGE + ";)V";
    static final String BACK_LABEL = "<< Quay Lại";
    static final int BACK_ACTION = -39;

    static String n(String suffix) { return HungEnhancePortPatcher.n(suffix); }
    static void check(boolean b, String why) { HungEnhancePortPatcher.check(b, why); }
    static MethodNode method(ClassNode c, String name, String desc) { return HungEnhancePortPatcher.method(c, name, desc); }
    static AbstractInsnNode real(AbstractInsnNode x) { return HungEnhancePortPatcher.real(x); }
    static AbstractInsnNode back(AbstractInsnNode x) { return HungEnhancePortPatcher.back(x); }
    static boolean call(AbstractInsnNode x, int op, String owner, String name, String desc) { return HungEnhancePortPatcher.call(x, op, owner, name, desc); }
    static ClassNode copy(byte[] b) { return HungEnhancePortPatcher.copy(b); }
    static byte[] write(ClassNode c) { return HungEnhancePortPatcher.write(c); }
    static void same(ClassNode before, ClassNode after, String what) { HungEnhancePortPatcher.same(before, after, what); }
    static int intValue(AbstractInsnNode x) { return HungEnhancePortPatcher.intValue(x); }
    static boolean isInt(AbstractInsnNode x, int v) {
        int op = x == null ? -1 : x.getOpcode();
        return (op == BIPUSH || op == SIPUSH || op >= ICONST_M1 && op <= ICONST_5) && intValue(x) == v;
    }

    /** The XOR-encoded label whose key.d(byte[]) call is x. */
    static String decode(Method key, AbstractInsnNode x) throws Exception {
        AbstractInsnNode array = x.getPrevious();
        while (array != null && !(array.getOpcode() == NEWARRAY && ((IntInsnNode)array).operand == T_BYTE)) array = array.getPrevious();
        check(array != null, "Encoded label array");
        byte[] bytes = new byte[intValue(back(array.getPrevious()))];
        for (AbstractInsnNode y = array.getNext(); y != x; y = y.getNext())
            if (y.getOpcode() == BASTORE) { AbstractInsnNode v = back(y.getPrevious()), i = back(v.getPrevious()); bytes[intValue(i)] = (byte)intValue(v); }
        return (String)key.invoke(null, (Object)bytes);
    }

    /** 1. Tùy chọn tự đánh: the row goes on the menu local right before the "<< Quay Lại" row. */
    static byte[] attackMenu(byte[] bytes, Method key) throws Exception {
        ClassNode c = copy(bytes), before = copy(bytes);
        MethodNode build = method(c, n("lilllili"), "()V");
        AbstractInsnNode start = null;
        for (AbstractInsnNode x : build.instructions.toArray()) {
            if (!call(x, INVOKESTATIC, "key", "d", "([B)Ljava/lang/String;") || !isInt(real(x.getNext()), BACK_ACTION)) continue;
            check(start == null, "One -39 row");
            check(BACK_LABEL.equals(decode(key, x)), "The -39 row is " + BACK_LABEL);
            AbstractInsnNode row = x.getPrevious();
            while (row != null && !(row instanceof TypeInsnNode && row.getOpcode() == NEW && ((TypeInsnNode)row).desc.equals("bt"))) row = row.getPrevious();
            check(row != null, "Row construction");
            start = back(row.getPrevious());
            check(start instanceof VarInsnNode && start.getOpcode() == ALOAD, "Row is added to the menu local");
        }
        check(start != null, BACK_LABEL + " row");
        int menu = ((VarInsnNode)start).var;
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, menu));
        hook.add(new MethodInsnNode(INVOKESTATIC, PORT, "addAttackRows", MENU_DESC, false));
        build.instructions.insertBefore(start, hook);
        byte[] out = write(c);
        ClassNode backNode = copy(out);
        MethodNode buildBack = method(backNode, n("lilllili"), "()V");
        List<MethodInsnNode> mine = HungEnhancePortPatcher.calls(backNode, buildBack, PORT, "addAttackRows", MENU_DESC, 1);
        AbstractInsnNode load = back(mine.get(0).getPrevious()), next = real(mine.get(0).getNext());
        check(load.getOpcode() == ALOAD && ((VarInsnNode)load).var == menu && next.getOpcode() == ALOAD && ((VarInsnNode)next).var == menu
                && real(next.getNext()).getOpcode() == NEW, "Row hook sits right before the " + BACK_LABEL + " row");
        buildBack.instructions.remove(load); buildBack.instructions.remove(mine.get(0));
        same(before, backNode, "Tùy chọn tự đánh");
        return out;
    }

    /** 2. Opcode-40 answer: onBuff on the same stream right after HungBySonSkillStats.buff. */
    static byte[] answer(byte[] bytes) {
        ClassNode c = copy(bytes), before = copy(bytes);
        MethodNode m = method(c, n("liilll"), ANSWER_DESC);
        AbstractInsnNode load = real(m.instructions.getFirst()), getter = real(load.getNext()), stats = real(getter.getNext());
        check((m.access & ACC_STATIC) != 0 && load.getOpcode() == ALOAD && ((VarInsnNode)load).var == 0
                && getter instanceof MethodInsnNode && ((MethodInsnNode)getter).owner.equals(MESSAGE) && ((MethodInsnNode)getter).desc.equals("()Ljava/io/DataInputStream;")
                && call(stats, INVOKESTATIC, STATS, "buff", STREAM_DESC), "Buff answer starts with HungBySonSkillStats.buff(stream)");
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, 0));
        hook.add(getter.clone(null));
        hook.add(new MethodInsnNode(INVOKESTATIC, PORT, "onBuff", STREAM_DESC, false));
        m.instructions.insert(stats, hook);
        byte[] out = write(c);
        ClassNode backNode = copy(out);
        MethodNode mb = method(backNode, n("liilll"), ANSWER_DESC);
        List<MethodInsnNode> mine = HungEnhancePortPatcher.calls(backNode, mb, PORT, "onBuff", STREAM_DESC, 1);
        AbstractInsnNode g = back(mine.get(0).getPrevious()), a = back(g.getPrevious()), s = back(a.getPrevious());
        check(call(s, INVOKESTATIC, STATS, "buff", STREAM_DESC) && a.getOpcode() == ALOAD && g.getOpcode() == INVOKEVIRTUAL, "onBuff follows the stats hook");
        mb.instructions.remove(a); mb.instructions.remove(g); mb.instructions.remove(mine.get(0));
        same(before, backNode, "Opcode-40 answer");
        return out;
    }

    /** 3. Module loader: register between the stamina module and the ModUpLv port, which stays last. */
    static byte[] loader(byte[] bytes) {
        ClassNode load = copy(bytes), before = copy(bytes);
        MethodNode ctor = method(load, "<init>", "()V");
        List<MethodInsnNode> registers = HungEnhancePortPatcher.calls(load, ctor, MODUPLV, "register", "()V", 1);
        check(call(back(registers.get(0).getPrevious()), INVOKESTATIC, STAMINA, "register", "()V"), "Stamina registers right before ModUpLv");
        ctor.instructions.insertBefore(registers.get(0), new MethodInsnNode(INVOKESTATIC, PORT, "register", "()V", false));
        byte[] out = write(load);
        ClassNode backNode = copy(out);
        MethodNode ctorBack = method(backNode, "<init>", "()V");
        List<MethodInsnNode> mine = HungEnhancePortPatcher.calls(backNode, ctorBack, PORT, "register", "()V", 1);
        check(call(real(mine.get(0).getNext()), INVOKESTATIC, MODUPLV, "register", "()V")
                && call(back(mine.get(0).getPrevious()), INVOKESTATIC, STAMINA, "register", "()V"), "Registers between Stamina and the ModUpLv port");
        ctorBack.instructions.remove(mine.get(0));
        same(before, backNode, "Module loader");
        return out;
    }

    public static void main(String[] a) throws Exception {
        check(a.length == 6, "input support micro work output catalogPrefix");
        File input = new File(a[0]), support = new File(a[1]), work = new File(a[3]), output = new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)), "Pinned stamina input");
        check(!output.exists(), "Fresh output"); check(work.isDirectory() || work.mkdirs(), "Work directory");
        Properties p = new Properties(); InputStream cfg = new FileInputStream(CATALOG);
        try { p.load(cfg); } finally { cfg.close(); }
        check(INPUT.equals(p.getProperty("output.sha256")) && "true".equals(p.getProperty("stamina")), "Pinned source catalog");
        Map<String,byte[]> before = HungBySonExpInfoPack.read(input), out = new LinkedHashMap<String,byte[]>(before);
        check(!before.containsKey(PORT + ".class"), "Fresh " + PORT);

        byte[] port = HungEnhancePortPatcher.compileModule(work, input, support, a[2], TEMPLATE, PORT);
        ClassNode portNode = copy(port);
        for (String[] m : new String[][]{{"register", "()V"}, {"addAttackRows", MENU_DESC}, {"onBuff", STREAM_DESC}})
            check((method(portNode, m[0], m[1]).access & ACC_STATIC) != 0, "Static hook target " + m[0]);
        out.put(PORT + ".class", port);
        URLClassLoader keys = new URLClassLoader(new URL[]{input.toURI().toURL(), new File(a[2]).toURI().toURL()}, null);
        Method key = keys.loadClass("key").getDeclaredMethod("d", byte[].class); key.setAccessible(true);
        out.put(CONFIG + ".class", attackMenu(before.get(CONFIG + ".class"), key));
        out.put(READER + ".class", answer(before.get(READER + ".class")));
        out.put(LOADER + ".class", loader(before.get(LOADER + ".class")));

        Set<String> allowed = new HashSet<String>(Arrays.asList(CONFIG + ".class", READER + ".class", LOADER + ".class"));
        List<String> changed = new ArrayList<String>(); int classes = 0;
        for (String name : out.keySet()) {
            if (name.endsWith(".class")) classes++;
            if (!before.containsKey(name)) { check(name.equals(PORT + ".class"), "Only " + PORT + " is added: " + name); changed.add(name); continue; }
            if (Arrays.equals(before.get(name), out.get(name))) continue;
            check(allowed.contains(name), "Archive allowlist " + name);
            changed.add(name);
            check(copy(before.get(name)).version == copy(out.get(name)).version, "Class version " + name);
            String pool = new String(out.get(name), "ISO-8859-1"), prior = new String(before.get(name), "ISO-8859-1");
            for (String ref : new String[]{"java/lang/invoke/", "java/nio/", "java/time/", "java/awt/", "java/lang/reflect/", "addSuppressed"})
                check(pool.indexOf(ref) < 0 || prior.indexOf(ref) >= 0, "New non-CLDC reference " + ref + " in " + name);
        }
        check(changed.size() == 4 && out.size() == before.size() + 1, "Three classes change and one is added: " + changed);
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try { for (Map.Entry<String,byte[]> e : out.entrySet()) { JarEntry z = new JarEntry(e.getKey()); z.setTime(0); jar.putNextEntry(z); jar.write(e.getValue()); jar.closeEntry(); } }
        finally { jar.close(); }
        p.setProperty("input.sha256", INPUT); p.setProperty("output.sha256", HungBySonExpInfoPack.hash(output));
        p.setProperty("buff.swap", "true"); p.setProperty("buff.swap.owner", PORT);
        p.setProperty("auto.pk.class.count", String.valueOf(classes));
        StringBuilder list = new StringBuilder();
        for (String name : changed) list.append(list.length() == 0 ? "" : ",").append(name);
        p.setProperty("auto.pk.changed.classes", list.toString());
        OutputStream cat = new FileOutputStream(a[5] + ".properties");
        try { p.store(cat, "Auto thay do khi BUFF: Set 1 before a chosen buff, Set 2 after its opcode-40 answer"); } finally { cat.close(); }
        PrintWriter log = new PrintWriter(a[5] + "-report.txt", "UTF-8");
        try {
            log.println("PASS pinned input; Tùy chọn tự đánh row, opcode-40 answer hook and loader edits each with an exact inverse");
            log.println("PASS " + PORT + " compiled against the input (class 48, CLDC screen)");
            log.println("CHANGED=" + list); log.println("CLASSES=" + classes); log.println("SHA256=" + p.getProperty("output.sha256"));
        } finally { log.close(); }
        System.out.println("PASS buff swap " + p.getProperty("output.sha256"));
    }
}
