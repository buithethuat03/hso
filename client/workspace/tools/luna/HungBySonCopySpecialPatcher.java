import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Hung_By_Son_copy_special_skill: ModUpLv's special attack loop and the skill cast tab of the
 * EXP dialog on top of the released ModUpLv port (193BA880…). Recompiles HungBySonModUpLv and
 * adds HungBySonSkillStats (markers @~suffix@ = 100-character names), and adds six native hooks,
 * each with an exact inverse:
 *  - canvas loop: HungBySonModUpLv.frame() before every repaint, and every Thread.sleep argument
 *    through HungBySonModUpLv.frameSleep (wake for the next 28ms ModUpLv frame);
 *  - attack serializers (opcodes 9 and 6) and the buff request (opcode 40): HungBySonSkillStats.sent(skill) first;
 *  - buff answer handler (opcode 40): HungBySonSkillStats.buff(stream) first (mark/reset);
 *  - combat answer handler: HungBySonSkillStats.combat(stream) first (mark/reset);
 *  - game paint: HungBySonSkillStats.paint(g) right after the active dialog paints;
 *  - ExpInfoModalGuard.press/release: HungBySonSkillStats.press/release first;
 *  - ExpInfoBridge.open: the EXP text goes through HungBySonSkillStats.expText.
 * The Auto đánh controller hands Special casting to the loop when Tấn công quái thường is ON
 * (now the default) and resets the cast counters when Special turns ON. SkillCastStats (counters,
 * gap measurement, pacing) and SkillCastLog (the CSV file log) are added from the support build.
 */
public final class HungBySonCopySpecialPatcher implements Opcodes {
    static final String INPUT = "193BA880410CE79C1398C2074FE43239A1F238A2C96D78D9530F9E19869162D9";
    static final String CATALOG = "build/hung-moduplv-port-20261024-c/farm-special-attack.properties";
    static final String PORT = "HungBySonModUpLv", STATS = "HungBySonSkillStats",
            CTRL = "luna/features/AutoAttackController", COUNTERS = "luna/features/SkillCastStats", FILE_LOG = "luna/features/SkillCastLog",
            CANVAS = "com/silverknight/a", GUARD = "luna/generated/ExpInfoModalGuard", EXP_UI = "luna/generated/ExpInfoBridge",
            EXP = "luna/features/ExpEstimateSupport";
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
    static ClassNode node(Map<String,byte[]> jar, String name) { return HungBySonExpInfoPack.node(jar.get(name + ".class")); }
    static void inverse(ClassNode before, ClassNode after, String what) {
        check(before.methods.size() == after.methods.size() && before.fields.size() == after.fields.size(), what + " topology");
        for (MethodNode m : before.methods)
            check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(after, m.name, m.desc))), what + " inverse " + m.name + m.desc);
    }
    /** Removes every hook instruction (a call to owner.name, plus the given loads right before it). */
    static int unhook(MethodNode m, String owner, String name, int loads) {
        int removed = 0;
        for (AbstractInsnNode x : m.instructions.toArray()) {
            if (!(x instanceof MethodInsnNode) || !((MethodInsnNode)x).owner.equals(owner) || !((MethodInsnNode)x).name.equals(name)) continue;
            for (int i = 0; i < loads; i++) {
                AbstractInsnNode prev = x.getPrevious();
                while (prev.getOpcode() < 0) prev = prev.getPrevious();
                m.instructions.remove(prev);
            }
            m.instructions.remove(x);
            removed++;
        }
        return removed;
    }

    /** Expands the markers of both templates and compiles them together against the pinned input. */
    static Map<String,byte[]> compile(File[] templates, File work, File input, File support, String micro, boolean fileLog) throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        check(javac != null, "javac (JDK) required");
        List<String> args = new ArrayList<String>(Arrays.asList("-encoding", "UTF-8", "--release", "7", "-nowarn", "-Xlint:-options",
                "-cp", support.getPath() + File.pathSeparator + input.getPath() + File.pathSeparator + micro, "-d", work.getPath()));
        String[] names = {PORT, STATS};
        for (int t = 0; t < templates.length; t++) {
            String src = fileLogVariant(new String(Files.readAllBytes(templates[t].toPath()), "UTF-8"), fileLog);
            StringBuilder out = new StringBuilder(src.length() * 3);
            for (int i = 0; i < src.length();) {
                if (src.charAt(i) == '@' && i + 1 < src.length() && src.charAt(i + 1) == '~') {
                    int end = src.indexOf('@', i + 2);
                    check(end > 0, "Unterminated marker at " + i);
                    out.append(n(src.substring(i + 2, end)));
                    i = end + 1;
                } else out.append(src.charAt(i++));
            }
            File java = new File(work, names[t] + ".java");
            Writer w = new OutputStreamWriter(new FileOutputStream(java), "UTF-8");
            try { w.write(out.toString()); } finally { w.close(); }
            args.add(java.getPath());
        }
        check(javac.run(null, null, null, args.toArray(new String[0])) == 0, "Templates do not compile against the pinned input");
        Map<String,byte[]> classes = new LinkedHashMap<String,byte[]>();
        for (File f : work.listFiles()) if (f.getName().endsWith(".class"))
            check(Arrays.asList(PORT + ".class", STATS + ".class").contains(f.getName()), "Single-class templates: " + f.getName());
        for (String name : names) {
            ClassNode node = new ClassNode();
            new ClassReader(Files.readAllBytes(new File(work, name + ".class").toPath())).accept(node, ClassReader.SKIP_FRAMES);
            for (MethodNode m : node.methods) for (AbstractInsnNode x : m.instructions) check(x.getOpcode() != INVOKEDYNAMIC, "invokedynamic");
            node.version = V1_4;
            byte[] bytes = write(node);
            HungBySonExpInfoPack.screen(name + ".class", bytes);
            classes.put(name + ".class", bytes);
        }
        return classes;
    }

    /** Release builds (fileLog false) drop the //@file-log-begin..end lines and enable the //@no-file-log lines. */
    static String fileLogVariant(String src, boolean fileLog) {
        StringBuilder out = new StringBuilder(src.length());
        boolean skip = false;
        int blocks = 0;
        for (String line : src.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.equals("//@file-log-begin")) { check(!skip, "Nested file-log block"); skip = !fileLog; blocks++; continue; }
            if (trimmed.equals("//@file-log-end")) { check(skip == !fileLog, "Unbalanced file-log block"); skip = false; continue; }
            if (skip) continue;
            if (!fileLog && line.startsWith("//@no-file-log")) line = line.substring("//@no-file-log".length());
            out.append(line).append('\n');
        }
        check(!skip, "Unterminated file-log block");
        out.setLength(out.length() - 1);
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 8 || args.length == 9 && args[8].equals("--no-file-log"),
                "input support portTemplate statsTemplate micro work output catalog+report-prefix [--no-file-log]");
        boolean fileLog = args.length == 8;
        File input = new File(args[0]), support = new File(args[1]);
        File[] templates = {new File(args[2]), new File(args[3])};
        String micro = args[4];
        File work = new File(args[5]), output = new File(args[6]);
        File catalogOut = new File(args[7] + ".properties"), report = new File(args[7] + "-report.txt");
        check(HungBySonExpInfoPack.hash(input).equals(INPUT), "Pinned ModUpLv port release");
        check(!output.exists() && !catalogOut.exists() && !report.exists(), "Fresh outputs");
        check(work.isDirectory() || work.mkdirs(), "Work directory");
        InputStream cfg = new FileInputStream(CATALOG);
        try { p.load(cfg); } finally { cfg.close(); }
        check(INPUT.equals(p.getProperty("output.sha256")), "Catalog belongs to the pinned input");
        Map<String,byte[]> before = HungBySonExpInfoPack.read(input);
        Map<String,byte[]> out = new LinkedHashMap<String,byte[]>(before);
        check(before.containsKey(PORT + ".class") && !before.containsKey(STATS + ".class") && !before.containsKey(COUNTERS + ".class"), "Input has the port only");

        // 0. Templates: the port keeps every released method and gains the attack loop.
        Map<String,byte[]> compiled = compile(templates, work, input, support, micro, fileLog);
        ClassNode oldPort = node(before, PORT), port = HungBySonExpInfoPack.node(compiled.get(PORT + ".class"));
        Set<String> loop = new HashSet<String>(Arrays.asList("frame()V", "frameSleep(J)J", "attackFrame(L" + n("lliiil") + ";)Z",
                "nearestMonsters(L" + n("lliiil") + ";)Let;", "loopMonster(L" + n("liiiliii") + ";L" + n("lliiil") + ";)Z", "pickupFrame(L" + n("lliiil") + ";)V",
                "lockOrder([I[II)[I", "rank([I[II)I"));
        for (MethodNode m : oldPort.methods) check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(port, m.name, m.desc))), "Port method kept " + m.name + m.desc);
        for (MethodNode m : port.methods) check(find(oldPort, m.name, m.desc) != null || loop.remove(m.name + m.desc), "Unexpected port method " + m.name + m.desc);
        check(loop.isEmpty() && port.methods.size() == oldPort.methods.size() + 8, "Port gains exactly the eight loop methods");
        out.put(PORT + ".class", compiled.get(PORT + ".class"));
        out.put(STATS + ".class", compiled.get(STATS + ".class"));
        ClassNode stats = HungBySonExpInfoPack.node(compiled.get(STATS + ".class"));
        String G = n("llilll");
        for (String m : new String[]{"sent(I)V", "combat(Ljava/io/DataInputStream;)V", "buff(Ljava/io/DataInputStream;)V", "expText(Ljava/lang/String;)Ljava/lang/String;",
                "press(II)V", "release(II)V", "paint(L" + G + ";)V", "uiTick()V"})
            method(stats, m.substring(0, m.indexOf('(')), m.substring(m.indexOf('(')));

        // 1. Canvas loop: frame() before repaint, frameSleep(ms) before every Thread.sleep.
        ClassNode canvas = node(before, CANVAS), canvasBefore = node(before, CANVAS);
        MethodNode run = method(canvas, "run", "()V");
        int repaints = 0, sleeps = 0;
        for (AbstractInsnNode x : run.instructions.toArray()) {
            if (call(x, INVOKEVIRTUAL, "javax/microedition/lcdui/Canvas", "repaint", "()V")) {
                run.instructions.insertBefore(x, new MethodInsnNode(INVOKESTATIC, PORT, "frame", "()V", false)); repaints++;
            }
            if (call(x, INVOKESTATIC, "java/lang/Thread", "sleep", "(J)V")) {
                run.instructions.insertBefore(x, new MethodInsnNode(INVOKESTATIC, PORT, "frameSleep", "(J)J", false)); sleeps++;
            }
        }
        check(repaints == 1 && sleeps == 2, "Canvas loop: one repaint, two sleeps (" + repaints + "/" + sleeps + ")");
        out.put(CANVAS + ".class", write(canvas));
        ClassNode canvasBack = node(out, CANVAS);
        MethodNode runBack = method(canvasBack, "run", "()V");
        check(unhook(runBack, PORT, "frame", 0) == 1 && unhook(runBack, PORT, "frameSleep", 0) == 2, "Canvas hooks");
        inverse(canvasBefore, canvasBack, "Canvas");

        // 2. Attack serializers: opcode 9 (monsters) and 6 (players) count one cast of the skill byte.
        String Q = n("lilillll");
        ClassNode sender = node(before, Q), senderBefore = node(before, Q);
        int serializers = 0;
        for (MethodNode m : sender.methods) {
            if (!m.desc.equals("(Let;B)V")) continue;
            AbstractInsnNode a = real(m.instructions.getFirst());
            if (a.getOpcode() == ICONST_0) a = real(a.getNext());   // the opcode-6 writer starts with a local flag
            if (a.getOpcode() == ISTORE) a = real(a.getNext());
            AbstractInsnNode op = real(a.getNext()), opCall = real(op.getNext());
            check(a.getOpcode() == ALOAD && op.getOpcode() == BIPUSH && call(opCall, INVOKEVIRTUAL, sender.superName, n("lii"), "(B)V"),
                    "Serializer " + m.name + " starts a message");
            int code = ((IntInsnNode)op).operand;
            check(code == 9 && m.name.equals(n("")) || code == 6 && m.name.equals(n("l")), "Serializer opcodes: " + m.name + " -> " + code);
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(ILOAD, 2));
            hook.add(new MethodInsnNode(INVOKESTATIC, STATS, "sent", "(I)V", false));
            m.instructions.insert(hook);
            serializers++;
        }
        check(serializers == 2, "Two attack serializers");
        MethodNode buffRequest = method(sender, n(""), "(BBLet;)V");
        AbstractInsnNode b0 = real(buffRequest.instructions.getFirst()), b1 = real(b0.getNext()), b2 = real(b1.getNext());
        check(b0.getOpcode() == ALOAD && b1.getOpcode() == BIPUSH && ((IntInsnNode)b1).operand == 40
                && call(b2, INVOKEVIRTUAL, sender.superName, n("lii"), "(B)V"), "Buff request starts an opcode-40 message");
        InsnList buffHook = new InsnList();
        buffHook.add(new VarInsnNode(ILOAD, 1));
        buffHook.add(new MethodInsnNode(INVOKESTATIC, STATS, "sent", "(I)V", false));
        buffRequest.instructions.insert(buffHook);
        out.put(Q + ".class", write(sender));
        ClassNode senderBack = node(out, Q);
        int sentHooks = 0;
        for (MethodNode m : senderBack.methods) sentHooks += unhook(m, STATS, "sent", 1);
        check(sentHooks == 3, "Serializer hooks");
        inverse(senderBefore, senderBack, "Serializer");

        // 3. Combat answers: the stats read the same stream first, then reset it.
        String R = n("lllllll"), MSG = n("lllllil");
        ClassNode reader = node(before, R), readerBefore = node(before, R);
        MethodNode combat = method(reader, n(""), "(L" + MSG + ";BB)V");
        check((combat.access & ACC_STATIC) != 0, "Static combat handler");
        MethodInsnNode stream = null;
        for (AbstractInsnNode x : combat.instructions)
            if (x instanceof MethodInsnNode && ((MethodInsnNode)x).owner.equals("luna/features/BattlefieldSupport") && ((MethodInsnNode)x).name.equals("onCombat")) {
                AbstractInsnNode getter = back(back(x.getPrevious()).getPrevious());
                check(getter instanceof MethodInsnNode && ((MethodInsnNode)getter).owner.equals(MSG)
                        && ((MethodInsnNode)getter).desc.equals("()Ljava/io/DataInputStream;"), "Battlefield hook reads the message stream");
                stream = (MethodInsnNode)getter;
            }
        check(stream != null, "Combat handler stream getter");
        InsnList combatHook = new InsnList();
        combatHook.add(new VarInsnNode(ALOAD, 0));
        combatHook.add(stream.clone(null));
        combatHook.add(new MethodInsnNode(INVOKESTATIC, STATS, "combat", "(Ljava/io/DataInputStream;)V", false));
        combat.instructions.insert(combatHook);
        // 3b. Buff answers (opcode 40, dispatched to the static handler n("liilll")): same stream, buff() first.
        MethodNode buffAnswer = method(reader, n("liilll"), "(L" + MSG + ";)V");
        check((buffAnswer.access & ACC_STATIC) != 0 && call(real(real(buffAnswer.instructions.getFirst()).getNext()), INVOKEVIRTUAL, MSG, stream.name, stream.desc),
                "Static buff answer handler reads the message stream");
        InsnList buffAnswerHook = new InsnList();
        buffAnswerHook.add(new VarInsnNode(ALOAD, 0));
        buffAnswerHook.add(stream.clone(null));
        buffAnswerHook.add(new MethodInsnNode(INVOKESTATIC, STATS, "buff", "(Ljava/io/DataInputStream;)V", false));
        buffAnswer.instructions.insert(buffAnswerHook);
        out.put(R + ".class", write(reader));
        ClassNode readerBack = node(out, R);
        check(unhook(method(readerBack, n(""), "(L" + MSG + ";BB)V"), STATS, "combat", 2) == 1, "Combat hook");
        check(unhook(method(readerBack, n("liilll"), "(L" + MSG + ";)V"), STATS, "buff", 2) == 1, "Buff answer hook");
        inverse(readerBefore, readerBack, "Combat handler");

        // 4. Game paint: the tab bar/table right after the active dialog paints.
        String E = n("liilllii"), DIALOG_BASE = n("lililii");
        ClassNode engine = node(before, E), engineBefore = node(before, E);
        MethodNode paint = method(engine, n(""), "(L" + n("lillii") + ";)V");
        AbstractInsnNode dialogPaint = null, graphics = null;
        for (AbstractInsnNode x : paint.instructions) {
            if (!(x instanceof FieldInsnNode) || x.getOpcode() != GETSTATIC || !((FieldInsnNode)x).name.equals(n("liiililiili"))) continue;
            AbstractInsnNode ifnull = real(x.getNext());
            if (ifnull.getOpcode() != IFNULL) continue;
            AbstractInsnNode load = real(ifnull.getNext()), self = real(load.getNext()), gfx = real(self.getNext()), invoke = real(gfx.getNext());
            if (load.getOpcode() == GETSTATIC && self.getOpcode() == ALOAD && gfx.getOpcode() == GETFIELD
                    && ((FieldInsnNode)gfx).desc.equals("L" + G + ";") && invoke instanceof MethodInsnNode
                    && ((MethodInsnNode)invoke).name.equals("a") && ((MethodInsnNode)invoke).desc.equals("(L" + G + ";)V")) {
                check(dialogPaint == null, "One dialog paint"); dialogPaint = invoke; graphics = gfx;
            }
        }
        check(dialogPaint != null && ((VarInsnNode)back(graphics.getPrevious())).var == 0, "Active dialog paint");
        InsnList paintHook = new InsnList();
        paintHook.add(new VarInsnNode(ALOAD, 0));
        paintHook.add(graphics.clone(null));
        paintHook.add(new MethodInsnNode(INVOKESTATIC, STATS, "paint", "(L" + G + ";)V", false));
        paint.instructions.insert(dialogPaint, paintHook);
        out.put(E + ".class", write(engine));
        ClassNode engineBack = node(out, E);
        check(unhook(method(engineBack, n(""), "(L" + n("lillii") + ";)V"), STATS, "paint", 2) == 1, "Paint hook");
        inverse(engineBefore, engineBack, "Game paint");

        // 5. EXP modal pointer: tab presses are seen first; the guard keeps owning the touch.
        ClassNode guard = node(before, GUARD), guardBefore = node(before, GUARD);
        for (String name : new String[]{"press", "release"}) {
            MethodNode m = method(guard, name, "(II)Z");
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(ILOAD, 0)); hook.add(new VarInsnNode(ILOAD, 1));
            hook.add(new MethodInsnNode(INVOKESTATIC, STATS, name, "(II)V", false));
            m.instructions.insert(hook);
        }
        out.put(GUARD + ".class", write(guard));
        ClassNode guardBack = node(out, GUARD);
        check(unhook(method(guardBack, "press", "(II)Z"), STATS, "press", 2) == 1
                && unhook(method(guardBack, "release", "(II)Z"), STATS, "release", 2) == 1, "Pointer hooks");
        inverse(guardBefore, guardBack, "EXP modal guard");

        // 6. EXP dialog text: one blank line on top for the tab bar.
        ClassNode open = node(before, EXP_UI), openBefore = node(before, EXP_UI);
        MethodNode opener = method(open, "open", "()V");
        AbstractInsnNode info = null;
        for (AbstractInsnNode x : opener.instructions) if (call(x, INVOKESTATIC, EXP, "info", "(IJ)Ljava/lang/String;")) { check(info == null, "One EXP text"); info = x; }
        check(info != null, "EXP text call");
        opener.instructions.insert(info, new MethodInsnNode(INVOKESTATIC, STATS, "expText", "(Ljava/lang/String;)Ljava/lang/String;", false));
        out.put(EXP_UI + ".class", write(open));
        ClassNode openBack = node(out, EXP_UI);
        check(unhook(method(openBack, "open", "()V"), STATS, "expText", 0) == 1, "EXP text hook");
        inverse(openBefore, openBack, "EXP dialog");

        // 7. Controller: the loop owns Special casting with normal monsters (default ON); counters reset on Special ON.
        Set<String> ctrlChanged = new HashSet<String>(Arrays.asList("<init>(Lluna/api/AutoAttackAccess;Lluna/api/AutoAttackSettings;Lluna/api/LunaClock;)V",
                "engageMonsters()V", "chooseSpecial(Lluna/api/SpecialAttackAccess;Z)Z", "farmSpot(Lluna/api/FarmSpotAccess;)Z", "handleAction(I)Z", "tick()V"));
        HungBySonModUpLvPatcher.replaceSupport(support, before, out, CTRL, ctrlChanged, new HashSet<String>(), new String[0]);
        check(fileLog == new String(compiled.get(STATS + ".class"), "ISO-8859-1").contains(FILE_LOG), "File log referenced only by file-log builds");
        for (String support0 : fileLog ? new String[]{COUNTERS, FILE_LOG} : new String[]{COUNTERS}) {
            byte[] bytes = Files.readAllBytes(new File(support, support0 + ".class").toPath());
            HungBySonExpInfoPack.screen(support0 + ".class", bytes);
            check(HungBySonExpInfoPack.node(bytes).version == node(before, CTRL).version, "Support class version " + support0);
            out.put(support0 + ".class", bytes);
        }
        for (String same : new String[]{"luna/features/AutoAttackRuntime", "luna/features/ExpEstimateSupport"})
            check(Arrays.equals(before.get(same + ".class"), Files.readAllBytes(new File(support, same + ".class").toPath())), "Unchanged support " + same);

        Set<String> allowed = new HashSet<String>(Arrays.asList(CANVAS + ".class", Q + ".class", R + ".class", E + ".class", GUARD + ".class",
                EXP_UI + ".class", PORT + ".class", CTRL + ".class"));
        Set<String> added = new HashSet<String>(Arrays.asList(STATS + ".class", COUNTERS + ".class"));
        if (fileLog) added.add(FILE_LOG + ".class");
        List<String> changed = new ArrayList<String>(); int classes = 0, resources = 0;
        for (String name : out.keySet()) {
            if (!before.containsKey(name)) { check(added.contains(name), "Unexpected new entry " + name); changed.add(name); classes++; continue; }
            if (!Arrays.equals(before.get(name), out.get(name))) { check(allowed.contains(name), "Archive allowlist " + name); changed.add(name); }
            if (name.endsWith(".class")) {
                classes++;
                check(HungBySonExpInfoPack.node(before.get(name)).version == HungBySonExpInfoPack.node(out.get(name)).version, "Class version " + name);
            } else resources++;
        }
        check(changed.size() == 8 + added.size() && out.size() == before.size() + added.size(), "Eight classes change and " + added.size() + " are added: " + changed);
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String,byte[]> e : out.entrySet()) {
                JarEntry z = new JarEntry(e.getKey()); z.setTime(0); jar.putNextEntry(z); jar.write(e.getValue()); jar.closeEntry();
            }
        } finally { jar.close(); }
        p.setProperty("input.sha256", INPUT); p.setProperty("output.sha256", HungBySonExpInfoPack.hash(output));
        p.setProperty("copy.special.loop.owner", PORT); p.setProperty("copy.special.stats.owner", STATS);
        p.setProperty("copy.special.frame.ms", "28"); p.setProperty("copy.special.skills", "21");
        p.setProperty("copy.special.pickup.range", "50000"); p.setProperty("copy.special.miss.ms", "700"); p.setProperty("copy.special.lock.ms", "550"); p.setProperty("copy.special.free.skill", "0");
        p.setProperty("copy.special.file.log", fileLog ? FILE_LOG : "none"); p.setProperty("copy.special.gap.bin.ms", "25");
        p.setProperty("copy.special.combat.owner", R); p.setProperty("copy.special.message.owner", MSG);
        p.setProperty("copy.special.stream.getter", stream.name);
        p.setProperty("changed.classes", String.join(",", changed)); p.setProperty("native.inverse", "PASS");
        OutputStream config = new FileOutputStream(catalogOut);
        try { p.store(config, "Copy special skill: ModUpLv attack loop + skill cast tab"); } finally { config.close(); }
        PrintWriter log = new PrintWriter(report, "UTF-8");
        log.println("PASS pinned input; six native hooks with exact inverses; templates compiled against the input (class 48, CLDC screen)");
        log.println("CHANGED=" + changed); log.println("CLASSES=" + classes + " UNCHANGED_RESOURCES=" + resources);
        log.println("SHA256=" + p.getProperty("output.sha256")); log.close();
        System.out.println("COPY_SPECIAL_PATCH=PASS SHA256=" + p.getProperty("output.sha256"));
    }
}
