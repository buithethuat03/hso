import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * ModUpLv's Đập đồ replaces Hung's Auto Cường Hóa (docs/HUNG_BY_SON_ENHANCE.md). Hung only enhanced
 * after walking to the Làng Sói Trắng wizard and opening its NPC session; ModUpLv sends the opcode-67
 * requests wherever the character stands. HungBySonEnhance (compiled from HungBySonEnhance.java.in
 * against the pinned input) is added and the native code is rewired, each edit with an exact inverse:
 *  - item menu: "Thêm vào DS" (command 110) becomes "Đập đồ" (HungBySonEnhance.itemMenu), which keeps
 *    the S1/S2/S3 rows; command 110 queues through HungBySonEnhance.add;
 *  - MOD menu: the "Auto Cường Hóa >" row (-32) is relabelled "Đập đồ >" and opens HungBySonEnhance;
 *  - startup guide: the Auto Cường Hóa paragraph describes Đập đồ;
 *  - opcode-67 reply hook: HungBySonEnhance.onReply first in HungBySonOfflineLocal.onEnhancementReply;
 *  - module loader: HungBySonEnhance.register() right before HungBySonModUpLv.register(), so the port
 *    stays the last module (its "Phó bản / Mê cung >" row ends the MOD menu; Đập đồ adds no row).
 * The old module keeps its bytes; with no menu row and no queue button its flag can no longer be set.
 */
public final class HungEnhancePortPatcher implements Opcodes {
    static final String INPUT = "C9E5073D6B0978873D6A712C803027951D7E3CE8EDA289AF3803DFC51246C9F7";
    static final String CATALOG = "build/hung-fog-route-20261027-a/auto-pk.properties";
    static final String TEMPLATE = "tools/luna/HungBySonEnhance.java.in";
    static final String PORT = "HungBySonEnhance", OFFLINE = "HungBySonOfflineLocal", MODUPLV = "HungBySonModUpLv";
    static final String OLD = n("lliillii"), ITEM_SCREEN = n("liililli"), ITEM = n("llilli"), DISPATCH = n("lillllll"),
            MOD_MENU = n("lliillli"), LOADER = n("lliillll");
    static final String OLD_LABEL = "Auto Cường Hóa >", NEW_LABEL = "Đập đồ >";
    static final String GUIDE_HEAD = "Các tính năng MOD hoạt động cục bộ, không cần key hay máy chủ MOD.\n";
    static final String OLD_GUIDE_ENHANCE = "Auto Cường Hóa: trong túi đồ chọn trang bị > Thêm vào DS; mở MOD HungDZ > Auto Cường Hóa, "
            + "chọn cấp cộng và cỏ may mắn rồi bật. Tắt Auto Phó Bản khi dùng.\n"
            + "Thông minh: chỉ dùng cỏ 4 lá khi trang bị đang +8, +9 hoặc +10; các mức còn lại không dùng cỏ. "
            + "Thiếu loại cỏ đã chọn sẽ dừng. Đạt cấp mục tiêu thì chuyển món tiếp theo.\n";
    static final String NEW_GUIDE_ENHANCE = "Đập đồ: trong túi đồ chọn trang bị > Đập đồ; mở MOD HungDZ > Đập đồ, tăng cấp muốn đập, "
            + "chọn bảo hiểm cho từng cấp rồi bật Auto đập đồ. Đập được ở mọi nơi, không cần về làng.\n"
            + "Thiếu vật phẩm bảo hiểm đã chọn hoặc dưới 6000 vàng sẽ tắt. Đạt cấp mục tiêu thì chuyển món tiếp theo.\n";

    static String n(String suffix) {
        StringBuilder b = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) b.append('i');
        return b.append(suffix).toString();
    }
    static void check(boolean value, String why) { if (!value) throw new IllegalStateException(why); }
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
    static ClassNode copy(byte[] bytes) { return HungBySonExpInfoPack.node(bytes); }
    static void same(ClassNode before, ClassNode after, String what) {
        check(before.methods.size() == after.methods.size() && before.fields.size() == after.fields.size(), what + " topology");
        for (MethodNode m : before.methods)
            check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(after, m.name, m.desc))), what + " inverse " + m.name + m.desc);
    }
    /** Every static call owner.name+desc in the class: exactly `count`, all inside `in`. */
    static List<MethodInsnNode> calls(ClassNode c, MethodNode in, String owner, String name, String desc, int count) {
        List<MethodInsnNode> out = new ArrayList<MethodInsnNode>();
        for (MethodNode m : c.methods) for (AbstractInsnNode x : m.instructions)
            if (call(x, INVOKESTATIC, owner, name, desc)) { check(m == in, "Call " + name + " outside " + in.name); out.add((MethodInsnNode)x); }
        check(out.size() == count, c.name + " has " + out.size() + " calls to " + owner + "." + name);
        return out;
    }
    /** Retarget a native static call to the port; the inverse retarget restores the class exactly. */
    static byte[] retarget(byte[] bytes, String what, String methodName, String methodDesc,
            String owner, String name, String desc, String portName) {
        ClassNode c = copy(bytes), before = copy(bytes);
        MethodNode m = method(c, methodName, methodDesc);
        for (MethodInsnNode x : calls(c, m, owner, name, desc, 1)) { x.owner = PORT; x.name = portName; }
        byte[] out = write(c);
        ClassNode back = copy(out);
        for (MethodInsnNode x : calls(back, method(back, methodName, methodDesc), PORT, portName, desc, 1)) { x.owner = owner; x.name = name; }
        same(before, back, what);
        return out;
    }

    static byte[] compilePort(File work, File input, File support, String micro) throws Exception {
        return compileModule(work, input, support, micro, TEMPLATE, PORT);
    }

    /** Expands a module template's @~suffix@ markers and compiles it against the pinned input. */
    static byte[] compileModule(File work, File input, File support, String micro, String template, String module) throws Exception {
        String src = new String(Files.readAllBytes(new File(template).toPath()), "UTF-8");
        StringBuilder out = new StringBuilder(src.length() * 3);
        for (int i = 0; i < src.length();) {
            if (src.charAt(i) == '@' && i + 1 < src.length() && src.charAt(i + 1) == '~') {
                int end = src.indexOf('@', i + 2);
                check(end > 0, "Unterminated marker at " + i);
                out.append(n(src.substring(i + 2, end)));
                i = end + 1;
            } else out.append(src.charAt(i++));
        }
        File java = new File(work, module + ".java");
        Writer w = new OutputStreamWriter(new FileOutputStream(java), "UTF-8");
        try { w.write(out.toString()); } finally { w.close(); }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        check(javac != null, "javac (JDK) required");
        String cp = support.getPath() + File.pathSeparator + input.getPath() + File.pathSeparator + micro;
        int rc = javac.run(null, null, null, "-encoding", "UTF-8", "--release", "7", "-nowarn", "-Xlint:-options",
                "-cp", cp, "-d", work.getPath(), java.getPath());
        check(rc == 0, module + " does not compile against the pinned input");
        for (File f : work.listFiles()) check(!f.getName().startsWith(module + "$"), module + " must be a single class");
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(new File(work, module + ".class").toPath())).accept(node, ClassReader.SKIP_FRAMES);
        for (MethodNode m : node.methods) for (AbstractInsnNode x : m.instructions) check(x.getOpcode() != INVOKEDYNAMIC, "invokedynamic");
        check(node.interfaces.equals(Collections.singletonList("IModModule")), module + " is an IModModule");
        node.version = V1_4;
        byte[] bytes = write(node);
        HungBySonExpInfoPack.screen(module + ".class", bytes);
        return bytes;
    }

    static int intValue(AbstractInsnNode n) {
        int o = n.getOpcode();
        if (o >= ICONST_M1 && o <= ICONST_5) return o - ICONST_0;
        check(n.getOpcode() == BIPUSH || n.getOpcode() == SIPUSH, "Integer constant");
        return ((IntInsnNode)n).operand;
    }

    public static void main(String[] a) throws Exception {
        check(a.length == 6, "input support micro work output catalogPrefix");
        File input = new File(a[0]), support = new File(a[1]), work = new File(a[3]), output = new File(a[4]);
        String micro = a[2];
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)), "Pinned fog-route input");
        check(!output.exists(), "Fresh output"); check(work.isDirectory() || work.mkdirs(), "Work directory");
        Properties p = new Properties(); InputStream cfg = new FileInputStream(CATALOG);
        try { p.load(cfg); } finally { cfg.close(); }
        check(INPUT.equals(p.getProperty("output.sha256")), "Pinned source catalog");
        Map<String,byte[]> before = HungBySonExpInfoPack.read(input), out = new LinkedHashMap<String,byte[]>(before);
        check(!before.containsKey(PORT + ".class"), "Fresh " + PORT);
        check(before.containsKey(OLD + ".class"), "Old Auto Cường Hóa module present");

        byte[] port = compilePort(work, input, support, micro);
        ClassNode portNode = copy(port);
        String menuDesc = "(Let;Ljava/lang/Object;)Let;", addDesc = "(L" + ITEM + ";)V";
        method(portNode, "itemMenu", menuDesc); method(portNode, "add", addDesc); method(portNode, "showMenu", "()V");
        method(portNode, "onReply", "(Ljava/lang/String;I)V"); method(portNode, "register", "()V");
        out.put(PORT + ".class", port);

        // 1. Item screen: menu row and command 110.
        byte[] screen = retarget(before.get(ITEM_SCREEN + ".class"), "Item menu", n(""), "(L" + ITEM + ";)Let;",
                OLD, n("l"), menuDesc, "itemMenu");
        screen = retarget(screen, "Item command", "a", "(II)V", OLD, n("lil"), addDesc, "add");
        ClassNode screenNode = copy(screen);
        MethodNode command = method(screenNode, "a", "(II)V");
        AbstractInsnNode code = real(command.instructions.getFirst());
        check(code.getOpcode() == ILOAD && intValue(real(code.getNext())) == 110, "Command 110 is the first case");
        out.put(ITEM_SCREEN + ".class", screen);

        // 2. MOD dispatcher: -32 opens Đập đồ.
        byte[] dispatch = retarget(before.get(DISPATCH + ".class"), "Dispatcher", n("lliii"), "(II)Z", OLD, n("liliilll"), "()V", "showMenu");
        boolean minus32 = false;
        for (AbstractInsnNode x : method(copy(dispatch), n("lliii"), "(II)Z").instructions)
            if (call(x, INVOKESTATIC, PORT, "showMenu", "()V")) {
                AbstractInsnNode cmp = back(x.getPrevious()), key = back(cmp.getPrevious());
                minus32 = cmp.getOpcode() == IF_ICMPNE && intValue(key) == -32;
            }
        check(minus32, "showMenu sits behind action -32");
        out.put(DISPATCH + ".class", dispatch);

        // 3. MOD menu label: the XOR-encoded "Auto Cường Hóa >" before -32 becomes a plain "Đập đồ >".
        URLClassLoader loader = new URLClassLoader(new URL[]{input.toURI().toURL(), new File(micro).toURI().toURL()}, null);
        Method decode = loader.loadClass("key").getDeclaredMethod("d", byte[].class); decode.setAccessible(true);
        ClassNode modMenu = copy(before.get(MOD_MENU + ".class")), modMenuBefore = copy(before.get(MOD_MENU + ".class"));
        MethodNode addMenu = method(modMenu, "addMenu", "(Let;)V");
        List<AbstractInsnNode> removed = null; LdcInsnNode label = null;
        for (AbstractInsnNode x : addMenu.instructions.toArray()) {
            if (!call(x, INVOKESTATIC, "key", "d", "([B)Ljava/lang/String;")) continue;
            AbstractInsnNode action = real(x.getNext());
            if (action == null || action.getOpcode() != BIPUSH || ((IntInsnNode)action).operand != -32) continue;
            check(removed == null, "One -32 row");
            AbstractInsnNode array = x.getPrevious();
            while (array != null && !(array.getOpcode() == NEWARRAY && ((IntInsnNode)array).operand == T_BYTE)) array = array.getPrevious();
            check(array != null, "Encoded label array");
            AbstractInsnNode length = back(array.getPrevious());
            byte[] bytes = new byte[intValue(length)];
            for (AbstractInsnNode y = array.getNext(); y != x; y = y.getNext())
                if (y.getOpcode() == BASTORE) { AbstractInsnNode v = back(y.getPrevious()), i = back(v.getPrevious()); bytes[intValue(i)] = (byte)intValue(v); }
                else if (y.getOpcode() >= 0) check(y.getOpcode() == DUP || y.getOpcode() == BIPUSH || y.getOpcode() == SIPUSH
                        || (y.getOpcode() >= ICONST_M1 && y.getOpcode() <= ICONST_5), "Plain byte-array initialiser");
            check(OLD_LABEL.equals(decode.invoke(null, (Object)bytes)), "Encoded label is " + OLD_LABEL);
            removed = new ArrayList<AbstractInsnNode>();
            for (AbstractInsnNode y = length; ; y = y.getNext()) { removed.add(y); if (y == x) break; }
            label = new LdcInsnNode(NEW_LABEL);
            addMenu.instructions.insertBefore(length, label);
            for (AbstractInsnNode y : removed) addMenu.instructions.remove(y);
        }
        check(removed != null, "Auto Cường Hóa row");
        byte[] modMenuBytes = write(modMenu);
        ClassNode modMenuBack = copy(modMenuBytes);
        MethodNode addBack = method(modMenuBack, "addMenu", "(Let;)V");
        LdcInsnNode found = null;
        for (AbstractInsnNode x : addBack.instructions)
            if (x instanceof LdcInsnNode && NEW_LABEL.equals(((LdcInsnNode)x).cst)) { check(found == null, "One new label"); found = (LdcInsnNode)x; }
        check(found != null && intValue(real(found.getNext())) == -32, "New label before -32");
        InsnList restore = new InsnList();
        for (AbstractInsnNode y : removed) restore.add(y);
        addBack.instructions.insertBefore(found, restore); addBack.instructions.remove(found);
        same(modMenuBefore, modMenuBack, "MOD menu");
        out.put(MOD_MENU + ".class", modMenuBytes);

        // 4. HungBySonOfflineLocal: startup guide and the opcode-67 reply hook.
        ClassNode offline = copy(before.get(OFFLINE + ".class")), offlineBefore = copy(before.get(OFFLINE + ".class"));
        MethodNode guide = method(offline, "guide", "()Ljava/lang/String;");
        LdcInsnNode text = null;
        for (AbstractInsnNode x : guide.instructions) if (x instanceof LdcInsnNode) { check(text == null, "One guide constant"); text = (LdcInsnNode)x; }
        check(text != null && text.cst instanceof String, "Guide constant");
        String oldGuide = (String)text.cst;
        check(oldGuide.startsWith(GUIDE_HEAD + OLD_GUIDE_ENHANCE), "Guide holds the Auto Cường Hóa paragraph");
        String newGuide = GUIDE_HEAD + NEW_GUIDE_ENHANCE + oldGuide.substring(GUIDE_HEAD.length() + OLD_GUIDE_ENHANCE.length());
        text.cst = newGuide;
        MethodNode reply = method(offline, "onEnhancementReply", "(Ljava/lang/String;I)V");
        check((reply.access & ACC_STATIC) != 0, "Static reply hook");
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, 0)); hook.add(new VarInsnNode(ILOAD, 1));
        hook.add(new MethodInsnNode(INVOKESTATIC, PORT, "onReply", "(Ljava/lang/String;I)V", false));
        reply.instructions.insert(hook);
        byte[] offlineBytes = write(offline);
        ClassNode offlineBack = copy(offlineBytes);
        for (AbstractInsnNode x : method(offlineBack, "guide", "()Ljava/lang/String;").instructions)
            if (x instanceof LdcInsnNode) { check(newGuide.equals(((LdcInsnNode)x).cst), "New guide stored"); ((LdcInsnNode)x).cst = oldGuide; }
        MethodNode replyBack = method(offlineBack, "onEnhancementReply", "(Ljava/lang/String;I)V");
        AbstractInsnNode head = real(replyBack.instructions.getFirst());
        int[] shape = {ALOAD, ILOAD, INVOKESTATIC};
        for (int op : shape) { check(head.getOpcode() == op, "Reply hook shape"); AbstractInsnNode next = real(head.getNext()); replyBack.instructions.remove(head); head = next; }
        same(offlineBefore, offlineBack, "Offline local");
        out.put(OFFLINE + ".class", offlineBytes);

        // 5. Module loader: register right before the ModUpLv port, which stays last.
        ClassNode load = copy(before.get(LOADER + ".class")), loadBefore = copy(before.get(LOADER + ".class"));
        MethodNode ctor = method(load, "<init>", "()V");
        List<MethodInsnNode> registers = calls(load, ctor, MODUPLV, "register", "()V", 1);
        ctor.instructions.insertBefore(registers.get(0), new MethodInsnNode(INVOKESTATIC, PORT, "register", "()V", false));
        byte[] loadBytes = write(load);
        ClassNode loadBack = copy(loadBytes);
        MethodNode ctorBack = method(loadBack, "<init>", "()V");
        List<MethodInsnNode> mine = calls(loadBack, ctorBack, PORT, "register", "()V", 1);
        check(call(real(mine.get(0).getNext()), INVOKESTATIC, MODUPLV, "register", "()V"), "Registers right before the ModUpLv port");
        ctorBack.instructions.remove(mine.get(0));
        same(loadBefore, loadBack, "Module loader");
        out.put(LOADER + ".class", loadBytes);

        Set<String> allowed = new HashSet<String>(Arrays.asList(ITEM_SCREEN + ".class", DISPATCH + ".class", MOD_MENU + ".class",
                OFFLINE + ".class", LOADER + ".class"));
        List<String> changed = new ArrayList<String>(); int classes = 0, resources = 0;
        for (String name : out.keySet()) {
            if (!before.containsKey(name)) { check(name.equals(PORT + ".class"), "Only " + PORT + " is added: " + name); changed.add(name); classes++; continue; }
            if (!Arrays.equals(before.get(name), out.get(name))) { check(allowed.contains(name), "Archive allowlist " + name); changed.add(name); }
            if (name.endsWith(".class")) {
                classes++;
                check(copy(before.get(name)).version == copy(out.get(name)).version, "Class version " + name);
                String pool = new String(out.get(name), "ISO-8859-1"), prior = new String(before.get(name), "ISO-8859-1");
                for (String ref : new String[]{"java/lang/invoke/", "java/nio/", "java/time/", "java/awt/", "java/lang/reflect/", "addSuppressed"})
                    check(pool.indexOf(ref) < 0 || prior.indexOf(ref) >= 0, "New non-CLDC reference " + ref + " in " + name);
            } else resources++;
        }
        check(changed.size() == 6 && out.size() == before.size() + 1, "Five classes change and one is added: " + changed);
        check(Arrays.equals(before.get(OLD + ".class"), out.get(OLD + ".class")), "Old module bytes kept");
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String,byte[]> e : out.entrySet()) {
                JarEntry z = new JarEntry(e.getKey()); z.setTime(0); jar.putNextEntry(z); jar.write(e.getValue()); jar.closeEntry();
            }
        } finally { jar.close(); }
        p.setProperty("input.sha256", INPUT); p.setProperty("output.sha256", HungBySonExpInfoPack.hash(output));
        p.setProperty("enhance.port", "true"); p.setProperty("enhance.port.owner", PORT);
        p.setProperty("auto.pk.class.count", String.valueOf(classes));
        StringBuilder list = new StringBuilder();
        for (String name : changed) list.append(list.length() == 0 ? "" : ",").append(name);
        p.setProperty("auto.pk.changed.classes", list.toString());
        OutputStream cat = new FileOutputStream(a[5] + ".properties");
        try { p.store(cat, "ModUpLv Dap do replaces Hung Auto Cuong Hoa"); } finally { cat.close(); }
        PrintWriter log = new PrintWriter(a[5] + "-report.txt", "UTF-8");
        try {
            log.println("PASS pinned input; item menu/command, -32 dispatcher, MOD label, guide, reply hook and loader edits each with an exact inverse");
            log.println("PASS " + PORT + " compiled against the input (class 48, CLDC screen); old module bytes unchanged");
            log.println("CHANGED=" + list); log.println("CLASSES=" + classes + " RESOURCES=" + resources);
            log.println("SHA256=" + p.getProperty("output.sha256"));
        } finally { log.close(); }
        System.out.println("PASS enhance port " + p.getProperty("output.sha256"));
    }
}
