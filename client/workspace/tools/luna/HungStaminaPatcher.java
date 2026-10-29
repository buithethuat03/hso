import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Auto phục hồi thể lực (docs/HUNG_BY_SON_STAMINA.md). The Trang Bị heart number is ~lliiil.e; the
 * opcode-59 reply handler is its only writer after <clinit>, and an empty opcode-59 request asks for
 * it. HungBySonStamina (compiled from HungBySonStamina.java.in against the pinned input) is added and
 * four native classes are edited, each edit with an exact inverse:
 *  - config menu ~lliiiiil.~liliilll: HungBySonStamina.addConfigRows right after ModUpLv's rows
 *    (before "Reset Cấu Hình");
 *  - dialog filter ~lillllll.~lilll(String)Z: HungBySonStamina.onDialog first, true suppresses;
 *  - opcode-59 handler ~lllllll.~llilii: HungBySonStamina.onStamina after both fields are stored;
 *  - module loader: HungBySonStamina.register() right before HungBySonModUpLv.register().
 */
public final class HungStaminaPatcher implements Opcodes {
    static final String INPUT = "95D1C5CA51588B86FA305642B0416A73042E614EED9219ACC89EEB0D8665F7D1";
    static final String CATALOG = "build/hung-enhance-popup-20261027-a/auto-pk.properties";
    static final String TEMPLATE = "tools/luna/HungBySonStamina.java.in";
    static final String PORT = "HungBySonStamina", MODUPLV = "HungBySonModUpLv";
    static final String CONFIG = n("lliiiiil"), DISPATCH = n("lillllll"), HANDLERS = n("lllllll"), LOADER = n("lliillll"),
            PLAYER = n("lliiil"), MESSAGE = n("lllllil");
    static final String MENU_DESC = "(Let;)V", DIALOG_DESC = "(Ljava/lang/String;)Z", HANDLER_DESC = "(L" + MESSAGE + ";)V";

    static String n(String suffix) { return HungEnhancePortPatcher.n(suffix); }
    static void check(boolean b, String why) { HungEnhancePortPatcher.check(b, why); }
    static MethodNode method(ClassNode c, String name, String desc) { return HungEnhancePortPatcher.method(c, name, desc); }
    static AbstractInsnNode real(AbstractInsnNode x) { return HungEnhancePortPatcher.real(x); }
    static boolean call(AbstractInsnNode x, int op, String owner, String name, String desc) { return HungEnhancePortPatcher.call(x, op, owner, name, desc); }
    static ClassNode copy(byte[] b) { return HungEnhancePortPatcher.copy(b); }
    static byte[] write(ClassNode c) { return HungEnhancePortPatcher.write(c); }
    static void same(ClassNode before, ClassNode after, String what) { HungEnhancePortPatcher.same(before, after, what); }
    static List<AbstractInsnNode> code(MethodNode m) {
        List<AbstractInsnNode> out = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode x : m.instructions) if (x.getOpcode() >= 0) out.add(x);
        return out;
    }
    static boolean field(AbstractInsnNode x, int op, String owner, String name, String desc) {
        if (!(x instanceof FieldInsnNode) || x.getOpcode() != op) return false;
        FieldInsnNode f = (FieldInsnNode)x;
        return f.owner.equals(owner) && f.name.equals(name) && f.desc.equals(desc);
    }

    /** 1. Config menu: the stamina row follows ModUpLv's rows on the same menu local. */
    static byte[] config(byte[] bytes) {
        ClassNode c = copy(bytes), before = copy(bytes);
        MethodNode build = method(c, n("liliilll"), "()V");
        List<MethodInsnNode> rows = HungEnhancePortPatcher.calls(c, build, MODUPLV, "addConfigRows", MENU_DESC, 1);
        AbstractInsnNode load = rows.get(0).getPrevious();
        while (load != null && load.getOpcode() < 0) load = load.getPrevious();
        check(load instanceof VarInsnNode && load.getOpcode() == ALOAD, "Config rows take the menu local");
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, ((VarInsnNode)load).var));
        hook.add(new MethodInsnNode(INVOKESTATIC, PORT, "addConfigRows", MENU_DESC, false));
        build.instructions.insert(rows.get(0), hook);
        byte[] out = write(c);
        ClassNode back = copy(out);
        MethodNode buildBack = method(back, n("liliilll"), "()V");
        MethodInsnNode mine = HungEnhancePortPatcher.calls(back, buildBack, PORT, "addConfigRows", MENU_DESC, 1).get(0);
        AbstractInsnNode prior = mine.getPrevious();
        while (prior.getOpcode() < 0) prior = prior.getPrevious();
        AbstractInsnNode modUpLv = prior.getPrevious();
        while (modUpLv.getOpcode() < 0) modUpLv = modUpLv.getPrevious();
        check(call(modUpLv, INVOKESTATIC, MODUPLV, "addConfigRows", MENU_DESC), "Stamina row right after ModUpLv's rows");
        buildBack.instructions.remove(prior); buildBack.instructions.remove(mine);
        same(before, back, "Config menu");
        return out;
    }

    /** 2. Dialog filter: HungBySonStamina.onDialog(text) ahead of ModUpLv's filter. */
    static byte[] dialog(byte[] bytes) {
        ClassNode c = copy(bytes), before = copy(bytes);
        MethodNode filter = method(c, n("lilll"), DIALOG_DESC);
        List<AbstractInsnNode> head = code(filter);
        check(head.get(0).getOpcode() == ALOAD && ((VarInsnNode)head.get(0)).var == 0
                && call(head.get(1), INVOKESTATIC, MODUPLV, "onDialog", DIALOG_DESC), "Filter starts with ModUpLv.onDialog(text)");
        LabelNode open = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, 0));
        hook.add(new MethodInsnNode(INVOKESTATIC, PORT, "onDialog", DIALOG_DESC, false));
        hook.add(new JumpInsnNode(IFEQ, open));
        hook.add(new InsnNode(ICONST_1));
        hook.add(new InsnNode(IRETURN));
        hook.add(open);
        filter.instructions.insert(hook);
        byte[] out = write(c);
        ClassNode back = copy(out);
        MethodNode filterBack = method(back, n("lilll"), DIALOG_DESC);
        List<AbstractInsnNode> mine = code(filterBack);
        check(mine.get(0).getOpcode() == ALOAD && call(mine.get(1), INVOKESTATIC, PORT, "onDialog", DIALOG_DESC)
                && mine.get(2).getOpcode() == IFEQ && mine.get(3).getOpcode() == ICONST_1 && mine.get(4).getOpcode() == IRETURN
                && ((JumpInsnNode)mine.get(2)).label == firstLabelAfter(mine.get(4)), "Dialog hook shape");
        check(call(mine.get(6), INVOKESTATIC, MODUPLV, "onDialog", DIALOG_DESC), "ModUpLv filter follows");
        for (int i = 0; i < 5; i++) filterBack.instructions.remove(mine.get(i));
        same(before, back, "Dialog filter");
        return out;
    }

    static LabelNode firstLabelAfter(AbstractInsnNode x) {
        for (AbstractInsnNode y = x.getNext(); y != null; y = y.getNext()) {
            if (y instanceof LabelNode) return (LabelNode)y;
            if (y.getOpcode() >= 0) return null;
        }
        return null;
    }

    /** 3. Opcode-59 handler: the only writer of ~lliiil.e besides <clinit>; notify after both stores. */
    static byte[] handler(byte[] bytes, Map<String,byte[]> all) {
        int writers = 0;
        for (Map.Entry<String,byte[]> e : all.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            for (MethodNode m : copy(e.getValue()).methods) for (AbstractInsnNode x : m.instructions)
                if (field(x, PUTSTATIC, PLAYER, "e", "I")) {
                    check(m.name.equals("<clinit>") && e.getKey().equals(PLAYER + ".class")
                            || m.name.equals(n("llilii")) && m.desc.equals(HANDLER_DESC) && e.getKey().equals(HANDLERS + ".class"),
                            "Unexpected stamina writer " + e.getKey() + " " + m.name);
                    writers++;
                }
        }
        check(writers == 2, "Stamina has exactly two writers");
        ClassNode c = copy(bytes), before = copy(bytes);
        MethodNode m = method(c, n("llilii"), HANDLER_DESC);
        List<AbstractInsnNode> body = code(m);
        check(field(body.get(3), PUTSTATIC, PLAYER, "e", "I") && field(body.get(7), PUTSTATIC, PLAYER, "i", "I")
                && body.get(8).getOpcode() == RETURN, "Handler reads e then i and returns");
        m.instructions.insertBefore(body.get(8), new MethodInsnNode(INVOKESTATIC, PORT, "onStamina", "()V", false));
        byte[] out = write(c);
        ClassNode back = copy(out);
        MethodNode mb = method(back, n("llilii"), HANDLER_DESC);
        List<AbstractInsnNode> bb = code(mb);
        check(call(bb.get(8), INVOKESTATIC, PORT, "onStamina", "()V") && bb.get(9).getOpcode() == RETURN, "Notify sits before the return");
        int notifies = 0;
        for (AbstractInsnNode x : bb) if (call(x, INVOKESTATIC, PORT, "onStamina", "()V")) notifies++;
        check(notifies == 1, "One stamina notify");
        mb.instructions.remove(bb.get(8));
        same(before, back, "Opcode-59 handler");
        return out;
    }

    /** 4. Module loader: register right before the ModUpLv port, which stays last. */
    static byte[] loader(byte[] bytes) {
        ClassNode load = copy(bytes), before = copy(bytes);
        MethodNode ctor = method(load, "<init>", "()V");
        List<MethodInsnNode> registers = HungEnhancePortPatcher.calls(load, ctor, MODUPLV, "register", "()V", 1);
        ctor.instructions.insertBefore(registers.get(0), new MethodInsnNode(INVOKESTATIC, PORT, "register", "()V", false));
        byte[] out = write(load);
        ClassNode back = copy(out);
        MethodNode ctorBack = method(back, "<init>", "()V");
        List<MethodInsnNode> mine = HungEnhancePortPatcher.calls(back, ctorBack, PORT, "register", "()V", 1);
        check(call(real(mine.get(0).getNext()), INVOKESTATIC, MODUPLV, "register", "()V"), "Registers right before the ModUpLv port");
        ctorBack.instructions.remove(mine.get(0));
        same(before, back, "Module loader");
        return out;
    }

    public static void main(String[] a) throws Exception {
        check(a.length == 6, "input support micro work output catalogPrefix");
        File input = new File(a[0]), support = new File(a[1]), work = new File(a[3]), output = new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)), "Pinned enhance-popup input");
        check(!output.exists(), "Fresh output"); check(work.isDirectory() || work.mkdirs(), "Work directory");
        Properties p = new Properties(); InputStream cfg = new FileInputStream(CATALOG);
        try { p.load(cfg); } finally { cfg.close(); }
        check(INPUT.equals(p.getProperty("output.sha256")) && "true".equals(p.getProperty("enhance.popup")), "Pinned source catalog");
        Map<String,byte[]> before = HungBySonExpInfoPack.read(input), out = new LinkedHashMap<String,byte[]>(before);
        check(!before.containsKey(PORT + ".class"), "Fresh " + PORT);

        byte[] port = HungEnhancePortPatcher.compileModule(work, input, support, a[2], TEMPLATE, PORT);
        ClassNode portNode = copy(port);
        for (String[] m : new String[][]{{"register", "()V"}, {"addConfigRows", MENU_DESC}, {"onDialog", DIALOG_DESC}, {"onStamina", "()V"}})
            check((method(portNode, m[0], m[1]).access & ACC_STATIC) != 0, "Static hook target " + m[0]);
        out.put(PORT + ".class", port);
        out.put(CONFIG + ".class", config(before.get(CONFIG + ".class")));
        out.put(DISPATCH + ".class", dialog(before.get(DISPATCH + ".class")));
        out.put(HANDLERS + ".class", handler(before.get(HANDLERS + ".class"), before));
        out.put(LOADER + ".class", loader(before.get(LOADER + ".class")));

        Set<String> allowed = new HashSet<String>(Arrays.asList(CONFIG + ".class", DISPATCH + ".class", HANDLERS + ".class", LOADER + ".class"));
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
        check(changed.size() == 5 && out.size() == before.size() + 1, "Four classes change and one is added: " + changed);
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try { for (Map.Entry<String,byte[]> e : out.entrySet()) { JarEntry z = new JarEntry(e.getKey()); z.setTime(0); jar.putNextEntry(z); jar.write(e.getValue()); jar.closeEntry(); } }
        finally { jar.close(); }
        p.setProperty("input.sha256", INPUT); p.setProperty("output.sha256", HungBySonExpInfoPack.hash(output));
        p.setProperty("stamina", "true"); p.setProperty("stamina.owner", PORT);
        p.setProperty("auto.pk.class.count", String.valueOf(classes));
        StringBuilder list = new StringBuilder();
        for (String name : changed) list.append(list.length() == 0 ? "" : ",").append(name);
        p.setProperty("auto.pk.changed.classes", list.toString());
        OutputStream cat = new FileOutputStream(a[5] + ".properties");
        try { p.store(cat, "Auto phuc hoi the luc: opcode-59 poll and Com ga"); } finally { cat.close(); }
        PrintWriter log = new PrintWriter(a[5] + "-report.txt", "UTF-8");
        try {
            log.println("PASS pinned input; config row, dialog filter, opcode-59 notify and loader edits each with an exact inverse");
            log.println("PASS " + PORT + " compiled against the input (class 48, CLDC screen); stamina has two writers (<clinit>, opcode 59)");
            log.println("CHANGED=" + list); log.println("CLASSES=" + classes); log.println("SHA256=" + p.getProperty("output.sha256"));
        } finally { log.close(); }
        System.out.println("PASS stamina " + p.getProperty("output.sha256"));
    }
}
