import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * ModUpLv features in Hung_By_Son: Tấn công quái thường (map-wide Special targets), Auto Lửa
 * Thiêng and the Phó bản / mê cung menu. Compiles HungBySonModUpLv.java.in against the pinned
 * input (markers @~suffix@ = 100-character names) and adds five small hooks, each with an exact
 * inverse:
 *  - Special send: the native target builder call goes through HungBySonModUpLv.specialTargets;
 *  - Auto đánh menu: a "Tấn công quái thường" row (-117) after "Tấn công đặc biệt";
 *  - Cài Đặt Cấu Hình: HungBySonModUpLv.addConfigRows before "Reset Cấu Hình";
 *  - dialog text hook: HungBySonModUpLv.onDialog first in the mod dialog filter;
 *  - module loader: HungBySonModUpLv.register() last.
 * The Auto đánh controller and runtime gain the normal-monsters option and engageMonsters().
 */
public final class HungBySonModUpLvPatcher implements Opcodes {
    static final String INPUT = "4842A7CA88C337FB1E9E37DFE45CB85C684458E50B0B1E0148791D0F2ECA1F12";
    static final String CATALOG = "build/hung-battlefield-static-20261024-a/farm-special-attack.properties";
    static final String PORT = "HungBySonModUpLv", B = "luna/generated/HungAutoAttackAccess",
            CTRL = "luna/features/AutoAttackController", RT = "luna/features/AutoAttackRuntime";
    static final int NORMAL_ACTION = -117;
    static Properties p = new Properties();

    static void check(boolean value, String why) { if (!value) throw new IllegalStateException(why); }
    static String n(String suffix) {
        StringBuilder b = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) b.append('i');
        return b.append(suffix).toString();
    }
    static MethodNode find(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        return null;
    }
    static MethodNode method(ClassNode c, String name, String desc) {
        MethodNode m = find(c, name, desc); check(m != null, "Missing " + c.name + "." + name + desc); return m;
    }
    static AbstractInsnNode real(AbstractInsnNode n) { while (n != null && n.getOpcode() < 0) n = n.getNext(); return n; }
    static AbstractInsnNode back(AbstractInsnNode n) { while (n != null && n.getOpcode() < 0) n = n.getPrevious(); return n; }
    static boolean call(AbstractInsnNode n, int op, String owner, String name, String desc) {
        if (!(n instanceof MethodInsnNode) || n.getOpcode() != op) return false;
        MethodInsnNode m = (MethodInsnNode)n;
        return m.owner.equals(owner) && m.name.equals(name) && m.desc.equals(desc);
    }
    static byte[] write(ClassNode c) { ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(w); return w.toByteArray(); }
    /** Every method except the named ones keeps its canonical form; the named ones match after undo. */
    static void inverse(ClassNode before, ClassNode after, String what) {
        check(before.methods.size() == after.methods.size() && before.fields.size() == after.fields.size(), what + " topology");
        for (MethodNode m : before.methods)
            check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(after, m.name, m.desc))), what + " inverse " + m.name + m.desc);
    }

    static byte[] compilePort(File template, File work, File input, File support, String micro) throws Exception {
        String src = new String(Files.readAllBytes(template.toPath()), "UTF-8");
        StringBuilder out = new StringBuilder(src.length() * 3);
        for (int i = 0; i < src.length();) {
            if (src.charAt(i) == '@' && i + 1 < src.length() && src.charAt(i + 1) == '~') {
                int end = src.indexOf('@', i + 2);
                check(end > 0, "Unterminated marker at " + i);
                out.append(n(src.substring(i + 2, end)));
                i = end + 1;
            } else out.append(src.charAt(i++));
        }
        File java = new File(work, PORT + ".java");
        Writer w = new OutputStreamWriter(new FileOutputStream(java), "UTF-8");
        try { w.write(out.toString()); } finally { w.close(); }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        check(javac != null, "javac (JDK) required");
        String cp = support.getPath() + File.pathSeparator + input.getPath() + File.pathSeparator + micro;
        int rc = javac.run(null, null, null, "-encoding", "UTF-8", "--release", "7", "-nowarn", "-Xlint:-options",
                "-cp", cp, "-d", work.getPath(), java.getPath());
        check(rc == 0, PORT + " does not compile against the pinned input");
        File[] produced = work.listFiles();
        for (File f : produced) check(!f.getName().startsWith(PORT + "$"), PORT + " must be a single class");
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(new File(work, PORT + ".class").toPath())).accept(node, ClassReader.SKIP_FRAMES);
        for (MethodNode m : node.methods) for (AbstractInsnNode x : m.instructions) check(x.getOpcode() != INVOKEDYNAMIC, "invokedynamic");
        check(node.interfaces.equals(Collections.singletonList("IModModule")), PORT + " is an IModModule");
        node.version = V1_4;
        byte[] bytes = write(node);
        HungBySonExpInfoPack.screen(PORT + ".class", bytes);
        return bytes;
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 7, "input support template micro work output catalog+report-prefix");
        File input = new File(args[0]), support = new File(args[1]), template = new File(args[2]);
        String micro = args[3];
        File work = new File(args[4]), output = new File(args[5]);
        File catalogOut = new File(args[6] + ".properties"), report = new File(args[6] + "-report.txt");
        check(HungBySonExpInfoPack.hash(input).equals(INPUT), "Pinned Battlefield static release");
        check(!output.exists() && !catalogOut.exists() && !report.exists(), "Fresh outputs");
        check(work.isDirectory() || work.mkdirs(), "Work directory");
        InputStream cfg = new FileInputStream(CATALOG);
        try { p.load(cfg); } finally { cfg.close(); }
        check(INPUT.equals(p.getProperty("output.sha256")), "Catalog belongs to the pinned input");
        String E = p.getProperty("entity.owner"), P = p.getProperty("player.owner"), C = p.getProperty("cast.owner");
        check(E != null && P != null && C != null && E.equals(n("liiiliii")) && P.equals(n("lliiil")), "Native owners");
        Map<String,byte[]> before = HungBySonExpInfoPack.read(input);
        Map<String,byte[]> out = new LinkedHashMap<String,byte[]>(before);
        check(!before.containsKey(PORT + ".class"), "Fresh " + PORT);
        byte[] port = compilePort(template, work, input, support, micro);
        out.put(PORT + ".class", port);
        ClassNode portNode = HungBySonExpInfoPack.node(port);
        String targetsDesc = "(L" + P + ";IL" + E + ";)Let;";
        method(portNode, "specialTargets", targetsDesc);
        method(portNode, "addConfigRows", "(Let;)V");
        method(portNode, "onDialog", "(Ljava/lang/String;)Z");
        method(portNode, "register", "()V");

        // 1. Special send: one native target-builder call becomes specialTargets.
        String builderDesc = "(IL" + E + ";)Let;";
        ClassNode cast = HungBySonExpInfoPack.node(before.get(C + ".class")), castBefore = HungBySonExpInfoPack.node(before.get(C + ".class"));
        MethodNode send = method(cast, "codexSpecialSend", "()V");
        List<AbstractInsnNode> builders = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode x : send.instructions) if (call(x, INVOKEVIRTUAL, P, n(""), builderDesc)) builders.add(x);
        check(builders.size() == 1, "One native target builder in the Special send");
        send.instructions.set(builders.get(0), new MethodInsnNode(INVOKESTATIC, PORT, "specialTargets", targetsDesc, false));
        out.put(C + ".class", write(cast));
        ClassNode castBack = HungBySonExpInfoPack.node(out.get(C + ".class"));
        int swapped = 0;
        for (AbstractInsnNode x : method(castBack, "codexSpecialSend", "()V").instructions)
            if (call(x, INVOKESTATIC, PORT, "specialTargets", targetsDesc)) { ((MethodInsnNode)x).setOpcode(INVOKEVIRTUAL); ((MethodInsnNode)x).owner = P; ((MethodInsnNode)x).name = n(""); ((MethodInsnNode)x).desc = builderDesc; swapped++; }
        check(swapped == 1, "Special send hook");
        inverse(castBefore, castBack, "Cast");

        // 2. Auto đánh menu: clone the "Tấn công đặc biệt" row with the normal-monsters label and -117.
        ClassNode bridge = HungBySonExpInfoPack.node(before.get(B + ".class")), bridgeBefore = HungBySonExpInfoPack.node(before.get(B + ".class"));
        MethodNode menu = method(bridge, "showMenu", "(ZZI)V");
        AbstractInsnNode label = null;
        for (AbstractInsnNode x : menu.instructions) if (call(x, INVOKESTATIC, RT, "specialLabel", "()Ljava/lang/String;")) { check(label == null, "One special row"); label = x; }
        check(label != null, "Special row label");
        AbstractInsnNode dup = back(label.getPrevious()), alloc = back(dup.getPrevious()), list = back(alloc.getPrevious());
        check(dup.getOpcode() == DUP && alloc.getOpcode() == NEW && ((TypeInsnNode)alloc).desc.equals("bt") && list.getOpcode() == ALOAD, "Special row starts with ALOAD menu; NEW bt; DUP");
        AbstractInsnNode action = real(label.getNext());
        check(action.getOpcode() == BIPUSH && ((IntInsnNode)action).operand == -116, "Special row action -116");
        AbstractInsnNode screen = real(action.getNext()), init = real(screen.getNext()), add = real(init.getNext());
        check(screen.getOpcode() == GETSTATIC && init.getOpcode() == INVOKESPECIAL && ((MethodInsnNode)init).owner.equals("bt")
                && add.getOpcode() == INVOKEVIRTUAL && ((MethodInsnNode)add).owner.equals("et"), "Special row shape");
        InsnList row = new InsnList();
        row.add(list.clone(null)); row.add(new TypeInsnNode(NEW, "bt")); row.add(new InsnNode(DUP));
        row.add(new MethodInsnNode(INVOKESTATIC, RT, "normalLabel", "()Ljava/lang/String;", false));
        row.add(new IntInsnNode(BIPUSH, NORMAL_ACTION)); row.add(screen.clone(null)); row.add(init.clone(null)); row.add(add.clone(null));
        menu.instructions.insert(add, row);
        out.put(B + ".class", write(bridge));
        ClassNode bridgeBack = HungBySonExpInfoPack.node(out.get(B + ".class"));
        MethodNode menuBack = method(bridgeBack, "showMenu", "(ZZI)V");
        AbstractInsnNode normal = null;
        for (AbstractInsnNode x : menuBack.instructions) if (call(x, INVOKESTATIC, RT, "normalLabel", "()Ljava/lang/String;")) { check(normal == null, "One normal row"); normal = x; }
        check(normal != null, "Normal row present");
        AbstractInsnNode first = back(back(back(normal.getPrevious()).getPrevious()).getPrevious()), last = normal;
        for (int i = 0; i < 4; i++) last = real(last.getNext());
        check(((IntInsnNode)real(normal.getNext())).operand == NORMAL_ACTION, "Normal row action");
        for (AbstractInsnNode x = first; ; ) { AbstractInsnNode next = x.getNext(); menuBack.instructions.remove(x); if (x == last) break; x = next; }
        inverse(bridgeBefore, bridgeBack, "Bridge");

        // 3. Cài Đặt Cấu Hình: Auto Lửa Thiêng before "Reset Cấu Hình".
        String farm = n("lliiiiil");
        ClassNode config = HungBySonExpInfoPack.node(before.get(farm + ".class")), configBefore = HungBySonExpInfoPack.node(before.get(farm + ".class"));
        MethodNode configMenu = method(config, n("liliilll"), "()V");
        // "Reset Cấu Hình" is an XOR-encoded string; the row is identified by its action -41.
        AbstractInsnNode reset = null;
        for (AbstractInsnNode x : configMenu.instructions)
            if (x.getOpcode() == BIPUSH && ((IntInsnNode)x).operand == -41) {
                AbstractInsnNode s2 = real(x.getNext()), i2 = s2 == null ? null : real(s2.getNext());
                if (s2 != null && s2.getOpcode() == GETSTATIC && i2 != null && i2.getOpcode() == INVOKESPECIAL && ((MethodInsnNode)i2).owner.equals("bt")) {
                    check(reset == null, "One reset row"); reset = x;
                }
            }
        check(reset != null, "Reset row");
        AbstractInsnNode resetNew = reset.getPrevious();
        while (resetNew != null && !(resetNew.getOpcode() == NEW && ((TypeInsnNode)resetNew).desc.equals("bt"))) resetNew = resetNew.getPrevious();
        check(resetNew != null, "Reset row allocation");
        AbstractInsnNode resetDup = real(resetNew.getNext()), resetList = back(resetNew.getPrevious());
        check(resetDup.getOpcode() == DUP && resetList.getOpcode() == ALOAD, "Reset row shape");
        for (AbstractInsnNode x = resetNew.getNext(); x != reset; x = x.getNext())
            check(x.getOpcode() != NEW && !(x instanceof JumpInsnNode), "Reset row label is a plain string expression");
        InsnList fireRow = new InsnList();
        fireRow.add(resetList.clone(null));
        fireRow.add(new MethodInsnNode(INVOKESTATIC, PORT, "addConfigRows", "(Let;)V", false));
        configMenu.instructions.insertBefore(resetList, fireRow);
        out.put(farm + ".class", write(config));
        ClassNode configBack = HungBySonExpInfoPack.node(out.get(farm + ".class"));
        int configHooks = 0;
        for (AbstractInsnNode x : method(configBack, n("liliilll"), "()V").instructions.toArray())
            if (call(x, INVOKESTATIC, PORT, "addConfigRows", "(Let;)V")) {
                MethodNode m = method(configBack, n("liliilll"), "()V");
                m.instructions.remove(back(x.getPrevious())); m.instructions.remove(x); configHooks++;
            }
        check(configHooks == 1, "Config row hook");
        inverse(configBefore, configBack, "Config menu");

        // 4. Dialog texts: HungBySonModUpLv.onDialog first in the mod dialog filter.
        String mods = n("lillllll");
        ClassNode filter = HungBySonExpInfoPack.node(before.get(mods + ".class")), filterBefore = HungBySonExpInfoPack.node(before.get(mods + ".class"));
        MethodNode dialog = method(filter, n("lilll"), "(Ljava/lang/String;)Z");
        check((dialog.access & ACC_STATIC) != 0, "Static dialog filter");
        InsnList hook = new InsnList(); LabelNode keep = new LabelNode();
        hook.add(new VarInsnNode(ALOAD, 0));
        hook.add(new MethodInsnNode(INVOKESTATIC, PORT, "onDialog", "(Ljava/lang/String;)Z", false));
        hook.add(new JumpInsnNode(IFEQ, keep)); hook.add(new InsnNode(ICONST_1)); hook.add(new InsnNode(IRETURN)); hook.add(keep);
        dialog.instructions.insert(hook);
        out.put(mods + ".class", write(filter));
        ClassNode filterBack = HungBySonExpInfoPack.node(out.get(mods + ".class"));
        MethodNode dialogBack = method(filterBack, n("lilll"), "(Ljava/lang/String;)Z");
        AbstractInsnNode head = real(dialogBack.instructions.getFirst());
        int[] shape = {ALOAD, INVOKESTATIC, IFEQ, ICONST_1, IRETURN};
        for (int op : shape) { check(head.getOpcode() == op, "Dialog hook shape"); AbstractInsnNode next = real(head.getNext()); dialogBack.instructions.remove(head); head = next; }
        inverse(filterBefore, filterBack, "Dialog filter");

        // 5. Module loader: register last, so the menu row follows every other module.
        String loader = n("lliillll");
        ClassNode load = HungBySonExpInfoPack.node(before.get(loader + ".class")), loadBefore = HungBySonExpInfoPack.node(before.get(loader + ".class"));
        MethodNode ctor = method(load, "<init>", "()V");
        AbstractInsnNode ret = null;
        for (AbstractInsnNode x : ctor.instructions) if (x.getOpcode() == RETURN) { check(ret == null, "One loader return"); ret = x; }
        check(ret != null, "Loader return");
        ctor.instructions.insertBefore(ret, new MethodInsnNode(INVOKESTATIC, PORT, "register", "()V", false));
        out.put(loader + ".class", write(load));
        ClassNode loadBack = HungBySonExpInfoPack.node(out.get(loader + ".class"));
        int registers = 0;
        for (AbstractInsnNode x : method(loadBack, "<init>", "()V").instructions.toArray())
            if (call(x, INVOKESTATIC, PORT, "register", "()V")) { method(loadBack, "<init>", "()V").instructions.remove(x); registers++; }
        check(registers == 1, "Loader hook");
        inverse(loadBefore, loadBack, "Module loader");

        // 6. Controller and runtime: reviewed methods only.
        Set<String> ctrlChanged = new HashSet<String>(Arrays.asList("handleAction(I)Z"));
        Set<String> ctrlAdded = new HashSet<String>(Arrays.asList("normalLabel()Ljava/lang/String;", "normalMonstersActive()Z",
                "engageMonsters()V", "resetSpecialFarm()V"));
        replaceSupport(support, before, out, CTRL, ctrlChanged, ctrlAdded, new String[]{"normalMonsters", "NORMAL_MONSTERS"});
        Set<String> rtAdded = new HashSet<String>(Arrays.asList("normalLabel()Ljava/lang/String;", "normalMonstersActive()Z", "engageMonsters()V"));
        replaceSupport(support, before, out, RT, new HashSet<String>(), rtAdded, new String[0]);

        Set<String> allowed = new HashSet<String>(Arrays.asList(C + ".class", B + ".class", farm + ".class", mods + ".class",
                loader + ".class", CTRL + ".class", RT + ".class"));
        List<String> changed = new ArrayList<String>(); int classes = 0, resources = 0;
        for (String name : out.keySet()) {
            if (!before.containsKey(name)) { check(name.equals(PORT + ".class"), "Only " + PORT + " is added: " + name); changed.add(name); classes++; continue; }
            if (!Arrays.equals(before.get(name), out.get(name))) { check(allowed.contains(name), "Archive allowlist " + name); changed.add(name); }
            if (name.endsWith(".class")) {
                classes++;
                check(HungBySonExpInfoPack.node(before.get(name)).version == HungBySonExpInfoPack.node(out.get(name)).version, "Class version " + name);
            } else resources++;
        }
        check(changed.size() == 8 && out.size() == before.size() + 1, "Seven classes change and one is added: " + changed);
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String,byte[]> e : out.entrySet()) {
                JarEntry z = new JarEntry(e.getKey()); z.setTime(0); jar.putNextEntry(z); jar.write(e.getValue()); jar.closeEntry();
            }
        } finally { jar.close(); }
        p.setProperty("input.sha256", INPUT); p.setProperty("output.sha256", HungBySonExpInfoPack.hash(output));
        p.setProperty("modulv.port.owner", PORT); p.setProperty("normal.monsters.action", String.valueOf(NORMAL_ACTION));
        p.setProperty("normal.monsters.max", "20"); p.setProperty("normal.monsters.range", "20000000");
        p.setProperty("modulv.menu.action", "-87"); p.setProperty("modulv.fire.action", "-96");
        p.setProperty("changed.classes", String.join(",", changed)); p.setProperty("native.inverse", "PASS");
        OutputStream config2 = new FileOutputStream(catalogOut);
        try { p.store(config2, "ModUpLv port: normal monsters, Lửa Thiêng, Phó bản / mê cung"); } finally { config2.close(); }
        PrintWriter log = new PrintWriter(report, "UTF-8");
        log.println("PASS pinned input; five native hooks with exact inverses; " + PORT + " compiled against the input (class 48, CLDC screen)");
        log.println("CHANGED=" + changed); log.println("CLASSES=" + classes + " UNCHANGED_RESOURCES=" + resources);
        log.println("SHA256=" + p.getProperty("output.sha256")); log.close();
        System.out.println("MODUPLV_PORT_PATCH=PASS SHA256=" + p.getProperty("output.sha256"));
    }

    static void replaceSupport(File support, Map<String,byte[]> before, Map<String,byte[]> out, String owner, Set<String> changed,
            Set<String> added, String[] newFields) throws Exception {
        byte[] bytes = Files.readAllBytes(new File(support, owner + ".class").toPath());
        HungBySonExpInfoPack.screen(owner + ".class", bytes);
        ClassNode old = HungBySonExpInfoPack.node(before.get(owner + ".class")), now = HungBySonExpInfoPack.node(bytes);
        check(old.version == now.version, owner + " version");
        for (MethodNode m : old.methods) {
            MethodNode m2 = method(now, m.name, m.desc);
            if (!changed.contains(m.name + m.desc) && !m.name.equals("<clinit>"))
                check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(m2)), owner + " unrelated method " + m.name + m.desc);
        }
        for (MethodNode m : now.methods) if (find(old, m.name, m.desc) == null) check(added.contains(m.name + m.desc), owner + " unexpected method " + m.name + m.desc);
        check(now.methods.size() == old.methods.size() + added.size(), owner + " method count");
        Set<String> fields = new HashSet<String>(Arrays.asList(newFields));
        for (FieldNode f : now.fields) {
            boolean existed = false;
            for (FieldNode g : old.fields) if (g.name.equals(f.name) && g.desc.equals(f.desc)) existed = true;
            check(existed || fields.contains(f.name), owner + " unexpected field " + f.name);
        }
        out.put(owner + ".class", bytes);
    }
}
