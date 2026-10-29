import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * ModUpLv_skill_stats.jar: the "Chi tiết tung chiêu" counters/table of Hung_By_Son_copy_special_skill
 * added to ModUpLv.jar (E282B0AA…) for a like-for-like comparison. ModUpLv's own attack logic is not
 * touched. Native names come from symbols/moduplv-aliases.properties (CFR K### alias -> obfuscated
 * word; members reuse the class words). Generates ModUpLvStatsAccess from the stub's signatures and
 * adds six hooks, each with an exact inverse:
 *  - attack manager (K154.K143()V, every update): ModUpLvSkillStats.frame() first;
 *  - attack serializers K183.K143/K157(K126,B)V (opcodes 9/6): sent(skill) first;
 *  - combat answer K135.K143(K156,B,B)V: combat(stream) first (mark/reset);
 *  - "Tan cong / muc tieu" menu K143.K095()V: row "Chi tiết tung chiêu" (17/20) before the back row;
 *  - group-17 handler K143.K006(I)V: menu(action) first, returning when it was ours;
 *  - game paint K066.K143(K023)V: paint(g) right after the active dialog paints.
 * New classes are stored as class version 47 like the input.
 */
public final class ModUpLvSkillStatsPatcher implements Opcodes {
    static final String INPUT = "E282B0AAD121205F9A0CFAF14104C9A9ED281B6838AE22D7FEDD4004E83F997C";
    static final String STATS = "ModUpLvSkillStats", ACCESS = "ModUpLvStatsAccess", COUNTERS = "luna/features/SkillCastStats";
    static final String ROW_LABEL = "Chi tiết tung chiêu";
    static final int ROW_GROUP = 17, ROW_ACTION = 20, VERSION = 47;
    static Properties alias = new Properties();
    static Map<String,byte[]> before;

    static void check(boolean value, String why) { if (!value) throw new IllegalStateException(why); }
    static String a(String key) { String v = alias.getProperty(key); check(v != null, "Alias " + key); return v; }
    static String d(String key) { return "L" + a(key) + ";"; }
    static ClassNode node(Map<String,byte[]> jar, String name) { byte[] b = jar.get(name + ".class"); check(b != null, "Class " + name); return HungBySonExpInfoPack.node(b); }
    static MethodNode find(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        return null;
    }
    static MethodNode method(ClassNode c, String name, String desc) { MethodNode m = find(c, name, desc); check(m != null, "Missing method " + c.name.length() + ":" + desc); return m; }
    /** Asserts the member exists in the input (owner or its superclasses). */
    static void field(String owner, String name, String desc, boolean isStatic) {
        for (String o = owner; o != null && before.containsKey(o + ".class"); ) {
            ClassNode c = node(before, o);
            for (FieldNode f : c.fields) if (f.name.equals(name) && f.desc.equals(desc)) {
                check(((f.access & ACC_STATIC) != 0) == isStatic, "Field staticness " + desc); return;
            }
            o = c.superName;
        }
        throw new IllegalStateException("Missing field " + desc + " in " + owner.length());
    }
    static void exists(String owner, String name, String desc) { method(node(before, owner), name, desc); }
    static AbstractInsnNode real(AbstractInsnNode n) { while (n != null && n.getOpcode() < 0) n = n.getNext(); return n; }
    static AbstractInsnNode back(AbstractInsnNode n) { while (n != null && n.getOpcode() < 0) n = n.getPrevious(); return n; }
    static boolean call(AbstractInsnNode n, String owner, String name, String desc) {
        return n instanceof MethodInsnNode && ((MethodInsnNode)n).owner.equals(owner) && ((MethodInsnNode)n).name.equals(name) && ((MethodInsnNode)n).desc.equals(desc);
    }
    static byte[] write(ClassNode c) { ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(w); return w.toByteArray(); }
    static void inverse(ClassNode orig, ClassNode undone, String what) {
        check(orig.methods.size() == undone.methods.size() && orig.fields.size() == undone.fields.size(), what + " topology");
        for (MethodNode m : orig.methods)
            check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(undone, m.name, m.desc))), what + " inverse");
    }
    static int unhook(MethodNode m, String name, int loads) {
        int removed = 0;
        for (AbstractInsnNode x : m.instructions.toArray()) {
            if (!(x instanceof MethodInsnNode) || !((MethodInsnNode)x).owner.equals(STATS) || !((MethodInsnNode)x).name.equals(name)) continue;
            for (int i = 0; i < loads; i++) m.instructions.remove(back(x.getPrevious()));
            m.instructions.remove(x);
            removed++;
        }
        return removed;
    }
    static byte[] downgrade(byte[] bytes) {
        ClassNode c = new ClassNode();
        new ClassReader(bytes).accept(c, ClassReader.SKIP_FRAMES);
        for (MethodNode m : c.methods) for (AbstractInsnNode x : m.instructions) check(x.getOpcode() != INVOKEDYNAMIC, "invokedynamic");
        c.version = VERSION;
        return write(c);
    }

    // ------------------------------------------------------------------ generated access

    static MethodVisitor begin(ClassWriter w, String name, String desc) {
        MethodVisitor m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, name, desc, null, null);
        m.visitCode();
        return m;
    }
    static void end(MethodVisitor m, int op) { m.visitInsn(op); m.visitMaxs(0, 0); m.visitEnd(); }
    static void getInt(ClassWriter w, String name, String owner, String fieldName, boolean dialog) {
        MethodVisitor m = begin(w, name, dialog ? "(Ljava/lang/Object;)I" : "()I");
        if (dialog) { m.visitVarInsn(ALOAD, 0); m.visitTypeInsn(CHECKCAST, owner); m.visitFieldInsn(GETFIELD, owner, fieldName, "I"); }
        else m.visitFieldInsn(GETSTATIC, owner, fieldName, "I");
        end(m, IRETURN);
    }

    static byte[] access() {
        String K154 = a("K154"), K151 = a("K151"), K010 = a("K010"), K189 = a("K189"), K052 = a("K052"), K107 = a("K107"),
                K070 = a("K070"), K066 = a("K066"), K141 = a("K141"), K093 = a("K093"), K025 = a("K025");
        field(K154, a("K143"), "Z", true);
        field(K151, a("K143"), d("K042"), true);
        field(K010, a("K149"), "I", false);
        exists(K189, a("K143"), "(I)" + d("K052"));
        field(K052, a("K143"), "Ljava/lang/String;", false);
        exists(K107, "<init>", "()V");
        exists(K107, a("K143"), "(Ljava/lang/String;" + d("K070") + "Z)V");
        exists(K070, "<init>", "(Ljava/lang/String;I)V");
        field(K066, a("K143"), d("K141"), true);
        for (String k : new String[]{"K144", "K177", "K012", "K006"}) field(K141, a(k), "I", false);
        for (String k : new String[]{"K157", "K095", "K067", "K111"}) field(K066, a(k), "I", true);
        field(K107, a("K157"), "I", true);
        exists(K066, a("K143"), "(" + d("K093") + ")V");
        exists(K093, a("K143"), "(I)V");
        exists(K093, a("K095"), "(IIII)V");
        String[] fonts = {a("K049"), a("K045"), a("K105"), a("K011")};
        for (String f : fonts) field(K025, f, d("K025"), true);
        exists(K025, a("K143"), "(" + d("K093") + "Ljava/lang/String;IIIZ)V");
        exists(K025, a("K143"), "(Ljava/lang/String;)I");
        verifyFonts(K025, fonts);
        verifyGraphics(K093);

        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(VERSION, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, ACCESS, null, "java/lang/Object", null);
        MethodVisitor m = begin(w, "special", "()Z");
        m.visitFieldInsn(GETSTATIC, K154, a("K143"), "Z"); end(m, IRETURN);

        m = begin(w, "localId", "()I");
        Label none = new Label();
        m.visitFieldInsn(GETSTATIC, K151, a("K143"), d("K042")); m.visitVarInsn(ASTORE, 0);
        m.visitVarInsn(ALOAD, 0); m.visitJumpInsn(IFNULL, none);
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, K010, a("K149"), "I"); m.visitInsn(IRETURN);
        m.visitLabel(none); m.visitLdcInsn(Integer.valueOf(Integer.MIN_VALUE)); end(m, IRETURN);

        m = begin(w, "skillName", "(I)Ljava/lang/String;");
        Label noSkill = new Label();
        m.visitVarInsn(ILOAD, 0); m.visitMethodInsn(INVOKESTATIC, K189, a("K143"), "(I)" + d("K052"), false); m.visitVarInsn(ASTORE, 1);
        m.visitVarInsn(ALOAD, 1); m.visitJumpInsn(IFNULL, noSkill);
        m.visitVarInsn(ALOAD, 1); m.visitFieldInsn(GETFIELD, K052, a("K143"), "Ljava/lang/String;"); m.visitInsn(ARETURN);
        m.visitLabel(noSkill); m.visitInsn(ACONST_NULL); end(m, ARETURN);

        m = begin(w, "openDialog", "(Ljava/lang/String;)Ljava/lang/Object;");
        m.visitTypeInsn(NEW, K107); m.visitInsn(DUP); m.visitMethodInsn(INVOKESPECIAL, K107, "<init>", "()V", false); m.visitVarInsn(ASTORE, 1);
        m.visitVarInsn(ALOAD, 1); m.visitVarInsn(ALOAD, 0);
        m.visitTypeInsn(NEW, K070); m.visitInsn(DUP); m.visitLdcInsn("Ok"); m.visitInsn(ICONST_M1);
        m.visitMethodInsn(INVOKESPECIAL, K070, "<init>", "(Ljava/lang/String;I)V", false);
        m.visitInsn(ICONST_1); m.visitMethodInsn(INVOKEVIRTUAL, K107, a("K143"), "(Ljava/lang/String;" + d("K070") + "Z)V", false);
        m.visitVarInsn(ALOAD, 1); m.visitFieldInsn(PUTSTATIC, K066, a("K143"), d("K141"));
        m.visitVarInsn(ALOAD, 1); end(m, ARETURN);

        m = begin(w, "activeDialog", "()Ljava/lang/Object;");
        m.visitFieldInsn(GETSTATIC, K066, a("K143"), d("K141")); end(m, ARETURN);
        getInt(w, "dialogX", K141, a("K144"), true);
        getInt(w, "dialogY", K141, a("K177"), true);
        getInt(w, "dialogWidth", K141, a("K012"), true);
        getInt(w, "dialogMode", K141, a("K006"), true);
        m = begin(w, "setDialogBox", "(Ljava/lang/Object;II)V");
        m.visitVarInsn(ALOAD, 0); m.visitTypeInsn(CHECKCAST, K141); m.visitVarInsn(ILOAD, 1); m.visitFieldInsn(PUTFIELD, K141, a("K144"), "I");
        m.visitVarInsn(ALOAD, 0); m.visitTypeInsn(CHECKCAST, K141); m.visitVarInsn(ILOAD, 2); m.visitFieldInsn(PUTFIELD, K141, a("K012"), "I");
        end(m, RETURN);
        getInt(w, "screenWidth", K066, a("K157"), false);
        getInt(w, "screenHeight", K066, a("K095"), false);
        getInt(w, "screenCenter", K066, a("K067"), false);
        getInt(w, "screenMargin", K066, a("K111"), false);
        getInt(w, "dialogBase", K107, a("K157"), false);

        m = begin(w, "resetClip", "(Ljava/lang/Object;)V");
        m.visitVarInsn(ALOAD, 0); m.visitTypeInsn(CHECKCAST, K093); m.visitMethodInsn(INVOKESTATIC, K066, a("K143"), "(" + d("K093") + ")V", false);
        end(m, RETURN);
        m = begin(w, "fill", "(Ljava/lang/Object;IIIII)V");
        m.visitVarInsn(ALOAD, 0); m.visitTypeInsn(CHECKCAST, K093); m.visitVarInsn(ILOAD, 1); m.visitMethodInsn(INVOKEVIRTUAL, K093, a("K143"), "(I)V", false);
        m.visitVarInsn(ALOAD, 0); m.visitTypeInsn(CHECKCAST, K093);
        for (int i = 2; i <= 5; i++) m.visitVarInsn(ILOAD, i);
        m.visitMethodInsn(INVOKEVIRTUAL, K093, a("K095"), "(IIII)V", false);
        end(m, RETURN);

        m = begin(w, "text", "(Ljava/lang/Object;ILjava/lang/String;III)V");
        font(m, K025, fonts, 1);
        m.visitVarInsn(ALOAD, 0); m.visitTypeInsn(CHECKCAST, K093); m.visitVarInsn(ALOAD, 2);
        m.visitVarInsn(ILOAD, 3); m.visitVarInsn(ILOAD, 4); m.visitVarInsn(ILOAD, 5); m.visitInsn(ICONST_1);
        m.visitMethodInsn(INVOKEVIRTUAL, K025, a("K143"), "(" + d("K093") + "Ljava/lang/String;IIIZ)V", false);
        end(m, RETURN);
        m = begin(w, "textWidth", "(ILjava/lang/String;)I");
        font(m, K025, fonts, 0);
        m.visitVarInsn(ALOAD, 1); m.visitMethodInsn(INVOKEVIRTUAL, K025, a("K143"), "(Ljava/lang/String;)I", false);
        end(m, IRETURN);

        MethodVisitor init = w.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(ALOAD, 0); init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        end(init, RETURN);
        w.visitEnd();
        return w.toByteArray();
    }

    /** Pushes font[index] (0 white, 1 yellow, 2 red, 3 green; anything else white). */
    static void font(MethodVisitor m, String owner, String[] fonts, int indexVar) {
        Label[] cases = new Label[fonts.length];
        Label done = new Label(), dflt = new Label();
        for (int i = 0; i < cases.length; i++) cases[i] = new Label();
        m.visitVarInsn(ILOAD, indexVar);
        m.visitTableSwitchInsn(0, fonts.length - 1, dflt, cases);
        for (int i = 0; i < fonts.length; i++) {
            m.visitLabel(cases[i]); m.visitFieldInsn(GETSTATIC, owner, fonts[i], "L" + owner + ";"); m.visitJumpInsn(GOTO, done);
        }
        m.visitLabel(dflt); m.visitFieldInsn(GETSTATIC, owner, fonts[0], "L" + owner + ";");
        m.visitLabel(done);
    }

    /** The four fonts are tahoma_7 white/yellow/red/green in the font class initializer. */
    static void verifyFonts(String owner, String[] fonts) {
        String[] colors = {"white", "yellow", "red", "green"};
        Map<String,String> made = new HashMap<String,String>();
        for (MethodNode m : node(before, owner).methods) {
            String last = null;
            for (AbstractInsnNode x : m.instructions) {
                if (x instanceof LdcInsnNode && ((LdcInsnNode)x).cst instanceof String && ((String)((LdcInsnNode)x).cst).startsWith("/mfont/tahoma_7_")
                        && ((String)((LdcInsnNode)x).cst).endsWith(".png")) last = (String)((LdcInsnNode)x).cst;
                if (x instanceof FieldInsnNode && x.getOpcode() == PUTSTATIC && last != null) { made.put(((FieldInsnNode)x).name, last); last = null; }
            }
        }
        for (int i = 0; i < fonts.length; i++) check(("/mfont/tahoma_7_" + colors[i] + ".png").equals(made.get(fonts[i])), "Font " + colors[i]);
    }

    /** setColor(int) and fillRect(x, y, w, h) of the graphics wrapper. */
    static void verifyGraphics(String owner) {
        ClassNode g = node(before, owner);
        boolean color = false, fill = false;
        for (AbstractInsnNode x : method(g, a("K143"), "(I)V").instructions)
            if (x instanceof MethodInsnNode && ((MethodInsnNode)x).name.equals("setColor")) color = true;
        for (AbstractInsnNode x : method(g, a("K095"), "(IIII)V").instructions)
            if (x instanceof MethodInsnNode && ((MethodInsnNode)x).name.equals("fillRect")) fill = true;
        check(color && fill, "Graphics wrapper setColor/fillRect");
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        check(args.length == 5, "input.jar aliases.properties compiled-support-dir output.jar catalog+report-prefix");
        File input = new File(args[0]), support = new File(args[2]), output = new File(args[3]);
        File catalogOut = new File(args[4] + ".properties"), report = new File(args[4] + "-report.txt");
        check(HungBySonExpInfoPack.hash(input).equals(INPUT), "Pinned ModUpLv.jar");
        check(!output.exists() && !catalogOut.exists() && !report.exists(), "Fresh outputs");
        InputStream in = new FileInputStream(args[1]);
        try { alias.load(new InputStreamReader(in, "UTF-8")); } finally { in.close(); }
        check(INPUT.equals(alias.getProperty("input.sha256")), "Aliases belong to the pinned input");
        before = HungBySonExpInfoPack.read(input);
        Map<String,byte[]> out = new LinkedHashMap<String,byte[]>(before);
        for (String n : new String[]{STATS, ACCESS, COUNTERS}) check(!before.containsKey(n + ".class"), "Fresh " + n);

        // Support: readable stats + counters (class 47), generated access matching the stub exactly.
        byte[] stats = downgrade(Files.readAllBytes(new File(support, STATS + ".class").toPath()));
        byte[] counters = downgrade(Files.readAllBytes(new File(support, COUNTERS + ".class").toPath()));
        byte[] access = access();
        ClassNode stub = HungBySonExpInfoPack.node(Files.readAllBytes(new File(support, ACCESS + ".class").toPath())), made = HungBySonExpInfoPack.node(access);
        Set<String> want = new TreeSet<String>(), have = new TreeSet<String>();
        for (MethodNode m : stub.methods) if ((m.access & ACC_PUBLIC) != 0) want.add(m.name + m.desc);
        for (MethodNode m : made.methods) if ((m.access & ACC_PUBLIC) != 0) have.add(m.name + m.desc);
        check(want.equals(have), "Generated access matches the stub: " + want + " vs " + have);
        for (byte[] b : new byte[][]{stats, counters}) HungBySonExpInfoPack.screen("support", b);
        out.put(STATS + ".class", stats); out.put(COUNTERS + ".class", counters); out.put(ACCESS + ".class", access);

        // 1. Attack manager: a fresh session when Special turns ON.
        String K154 = a("K154");
        ClassNode manager = node(before, K154), managerBefore = node(before, K154);
        method(manager, a("K143"), "()V").instructions.insert(new MethodInsnNode(INVOKESTATIC, STATS, "frame", "()V", false));
        out.put(K154 + ".class", write(manager));
        ClassNode managerBack = node(out, K154);
        check(unhook(method(managerBack, a("K143"), "()V"), "frame", 0) == 1, "Manager hook");
        inverse(managerBefore, managerBack, "Manager");

        // 2. Attack serializers (opcode 9 monsters, 6 players): one cast of the skill byte.
        String K183 = a("K183"), listDesc = "(" + d("K126") + "B)V";
        ClassNode sender = node(before, K183), senderBefore = node(before, K183);
        int[] codes = {9, 6};
        String[] names = {a("K143"), a("K157")};
        for (int i = 0; i < 2; i++) {
            MethodNode m = method(sender, names[i], listDesc);
            AbstractInsnNode self = real(m.instructions.getFirst()), op = real(self.getNext());
            check(self.getOpcode() == ALOAD && op.getOpcode() == BIPUSH && ((IntInsnNode)op).operand == codes[i], "Serializer opcode " + codes[i]);
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(ILOAD, 2)); hook.add(new MethodInsnNode(INVOKESTATIC, STATS, "sent", "(I)V", false));
            m.instructions.insert(hook);
        }
        out.put(K183 + ".class", write(sender));
        ClassNode senderBack = node(out, K183);
        check(unhook(method(senderBack, names[0], listDesc), "sent", 1) == 1 && unhook(method(senderBack, names[1], listDesc), "sent", 1) == 1, "Serializer hooks");
        inverse(senderBefore, senderBack, "Serializer");

        // 3. Combat answers: the stats read the same stream first, then reset it.
        String K135 = a("K135"), K156 = a("K156"), combatDesc = "(" + d("K156") + "BB)V";
        ClassNode reader = node(before, K135), readerBefore = node(before, K135);
        MethodNode combat = method(reader, a("K143"), combatDesc);
        check((combat.access & ACC_STATIC) != 0, "Static combat handler");
        boolean vec5 = false;
        for (AbstractInsnNode x : combat.instructions) if (x instanceof LdcInsnNode && "ReadMessenge vec5".equals(((LdcInsnNode)x).cst)) vec5 = true;
        check(vec5, "Combat handler builds 'ReadMessenge vec5'");
        exists(K156, a("K143"), "()Ljava/io/DataInputStream;");
        InsnList combatHook = new InsnList();
        combatHook.add(new VarInsnNode(ALOAD, 0));
        combatHook.add(new MethodInsnNode(INVOKEVIRTUAL, K156, a("K143"), "()Ljava/io/DataInputStream;", false));
        combatHook.add(new MethodInsnNode(INVOKESTATIC, STATS, "combat", "(Ljava/io/DataInputStream;)V", false));
        combat.instructions.insert(combatHook);
        out.put(K135 + ".class", write(reader));
        ClassNode readerBack = node(out, K135);
        check(unhook(method(readerBack, a("K143"), combatDesc), "combat", 2) == 1, "Combat hook");
        inverse(readerBefore, readerBack, "Combat handler");

        // 4 + 5. Menu row in "Tan cong / muc tieu", and its handler.
        String K143 = a("K143"), K126 = a("K126"), K070 = a("K070"), rowInit = "(Ljava/lang/String;II" + d("K130") + ")V";
        ClassNode menus = node(before, K143), menusBefore = node(before, K143);
        MethodNode attackMenu = method(menus, a("K095"), "()V");
        boolean title = false;
        for (AbstractInsnNode x : attackMenu.instructions) if (x instanceof LdcInsnNode && "Tan cong / muc tieu".equals(((LdcInsnNode)x).cst)) title = true;
        check(title, "Attack menu titled 'Tan cong / muc tieu'");
        AbstractInsnNode backRow = null, init = null, add = null;
        for (AbstractInsnNode x : attackMenu.instructions) {
            if (call(x, K143, a("K143"), "(" + d("K126") + ")V")) { check(backRow == null, "One back row"); backRow = x; }
            if (call(x, K070, "<init>", rowInit) && init == null) { init = x; add = real(x.getNext()); }
        }
        check(backRow != null && init != null && add instanceof MethodInsnNode && ((MethodInsnNode)add).owner.equals(K126), "Menu row shape");
        AbstractInsnNode list = back(back(backRow.getPrevious()).getPrevious());
        check(list.getOpcode() == ALOAD && ((VarInsnNode)list).var == 0 && back(backRow.getPrevious()).getOpcode() == ALOAD, "Back row: this.K143(list)");
        int listVar = ((VarInsnNode)back(backRow.getPrevious())).var;
        InsnList row = new InsnList();
        row.add(new VarInsnNode(ALOAD, listVar)); row.add(new TypeInsnNode(NEW, K070)); row.add(new InsnNode(DUP));
        row.add(new LdcInsnNode(ROW_LABEL)); row.add(new IntInsnNode(BIPUSH, ROW_GROUP)); row.add(new IntInsnNode(BIPUSH, ROW_ACTION));
        row.add(new VarInsnNode(ALOAD, 0)); row.add(new TypeInsnNode(CHECKCAST, a("K130")));
        row.add(init.clone(null)); row.add(add.clone(null));
        attackMenu.instructions.insertBefore(list, row);
        MethodNode handler = method(menus, a("K006"), "(I)V");
        InsnList dispatch = new InsnList(); LabelNode keep = new LabelNode();
        dispatch.add(new VarInsnNode(ILOAD, 1)); dispatch.add(new MethodInsnNode(INVOKESTATIC, STATS, "menu", "(I)Z", false));
        dispatch.add(new JumpInsnNode(IFEQ, keep)); dispatch.add(new InsnNode(RETURN)); dispatch.add(keep);
        handler.instructions.insert(dispatch);
        out.put(K143 + ".class", write(menus));
        ClassNode menusBack = node(out, K143);
        MethodNode attackBack = method(menusBack, a("K095"), "()V");
        int rows = 0;
        for (AbstractInsnNode x : attackBack.instructions.toArray()) {
            if (!(x instanceof LdcInsnNode) || !ROW_LABEL.equals(((LdcInsnNode)x).cst)) continue;
            AbstractInsnNode first = back(back(back(x.getPrevious()).getPrevious()).getPrevious()), last = x;
            for (int i = 0; i < 6; i++) last = real(last.getNext());
            check(call(last, K126, ((MethodInsnNode)add).name, ((MethodInsnNode)add).desc), "Row undo shape");
            for (AbstractInsnNode y = first; ; ) { AbstractInsnNode next = y.getNext(); attackBack.instructions.remove(y); if (y == last) break; y = next; }
            rows++;
        }
        MethodNode handlerBack = method(menusBack, a("K006"), "(I)V");
        AbstractInsnNode head = real(handlerBack.instructions.getFirst());
        for (int op : new int[]{ILOAD, INVOKESTATIC, IFEQ, RETURN}) {
            check(head.getOpcode() == op, "Handler hook shape"); AbstractInsnNode next = real(head.getNext()); handlerBack.instructions.remove(head); head = next;
        }
        check(rows == 1, "Menu row hook");
        inverse(menusBefore, menusBack, "Menus");

        // 6. Game paint: the table right after the active dialog paints.
        String K066 = a("K066"), G = d("K093");
        ClassNode engine = node(before, K066), engineBefore = node(before, K066);
        MethodNode paint = method(engine, a("K143"), "(" + d("K023") + ")V");
        AbstractInsnNode dialogPaint = null, graphics = null;
        for (AbstractInsnNode x : paint.instructions) {
            if (!(x instanceof FieldInsnNode) || x.getOpcode() != GETSTATIC || !((FieldInsnNode)x).desc.equals(d("K141"))
                    || !((FieldInsnNode)x).name.equals(a("K143"))) continue;
            AbstractInsnNode ifnull = real(x.getNext());
            if (ifnull.getOpcode() != IFNULL) continue;
            AbstractInsnNode load = real(ifnull.getNext()), self = real(load.getNext()), gfx = real(self.getNext()), invoke = real(gfx.getNext());
            if (load.getOpcode() == GETSTATIC && self.getOpcode() == ALOAD && gfx.getOpcode() == GETFIELD && ((FieldInsnNode)gfx).desc.equals(G)
                    && invoke instanceof MethodInsnNode && ((MethodInsnNode)invoke).desc.equals("(" + G + ")V")) {
                check(dialogPaint == null, "One active dialog paint"); dialogPaint = invoke; graphics = gfx;
            }
        }
        check(dialogPaint != null, "Active dialog paint");
        InsnList paintHook = new InsnList();
        paintHook.add(new VarInsnNode(ALOAD, 0)); paintHook.add(graphics.clone(null));
        paintHook.add(new MethodInsnNode(INVOKESTATIC, STATS, "paint", "(Ljava/lang/Object;)V", false));
        paint.instructions.insert(dialogPaint, paintHook);
        out.put(K066 + ".class", write(engine));
        ClassNode engineBack = node(out, K066);
        check(unhook(method(engineBack, a("K143"), "(" + d("K023") + ")V"), "paint", 2) == 1, "Paint hook");
        inverse(engineBefore, engineBack, "Game paint");

        Set<String> changedAllowed = new HashSet<String>(Arrays.asList(K154 + ".class", K183 + ".class", K135 + ".class", K143 + ".class", K066 + ".class"));
        List<String> changed = new ArrayList<String>();
        for (String name : out.keySet()) {
            if (!before.containsKey(name)) { changed.add(name); continue; }
            if (!Arrays.equals(before.get(name), out.get(name))) { check(changedAllowed.contains(name), "Archive allowlist " + name); changed.add(name); }
            if (name.endsWith(".class")) check(HungBySonExpInfoPack.node(out.get(name)).version == HungBySonExpInfoPack.node(before.get(name)).version, "Class version kept");
        }
        check(changed.size() == 8 && out.size() == before.size() + 3, "Five classes change and three are added");
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try {
            // META-INF/MANIFEST.MF stays the first entry, as in the input.
            for (Map.Entry<String,byte[]> e : out.entrySet()) {
                JarEntry z = new JarEntry(e.getKey()); z.setTime(0); jar.putNextEntry(z); jar.write(e.getValue()); jar.closeEntry();
            }
        } finally { jar.close(); }
        Properties p = new Properties();
        p.setProperty("input.sha256", INPUT); p.setProperty("output.sha256", HungBySonExpInfoPack.hash(output));
        p.setProperty("stats.owner", STATS); p.setProperty("access.owner", ACCESS);
        p.setProperty("menu.class", K143); p.setProperty("menu.method", a("K095")); p.setProperty("menu.handler", a("K006"));
        p.setProperty("menu.group", String.valueOf(ROW_GROUP)); p.setProperty("menu.action", String.valueOf(ROW_ACTION));
        p.setProperty("manager.owner", K154); p.setProperty("serializer.owner", K183); p.setProperty("combat.owner", K135);
        p.setProperty("combat.method", a("K143")); p.setProperty("message.owner", K156); p.setProperty("engine.owner", K066);
        p.setProperty("special.field", a("K143")); p.setProperty("player.owner", a("K151")); p.setProperty("player.field", a("K143"));
        p.setProperty("entity.owner", a("K010")); p.setProperty("entity.id.field", a("K149"));
        p.setProperty("changed.classes", String.join(",", changed)); p.setProperty("native.inverse", "PASS");
        OutputStream cfg = new FileOutputStream(catalogOut);
        try { p.store(cfg, "ModUpLv skill cast statistics"); } finally { cfg.close(); }
        PrintWriter log = new PrintWriter(report, "UTF-8");
        log.println("PASS pinned input; six hooks with exact inverses; generated access == stub; class 47");
        log.println("CHANGED=" + changed.size() + " SHA256=" + p.getProperty("output.sha256"));
        log.close();
        System.out.println("MODUPLV_STATS_PATCH=PASS SHA256=" + p.getProperty("output.sha256"));
    }
}
