package luna.tools;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Small, verified adapters/hooks on the current release, rather than a baseline rebuild. */
public final class BachHoAutomationPatcher implements Opcodes {
    private static final String HOOK = "luna/core/BachHoClientHooks";
    private static Properties symbols;
    private static Map<String, byte[]> entries;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) throw new IllegalArgumentException("--prepare input.jar extra.properties template generated.java catalog.properties | --patch input.jar output.jar classes catalog.properties");
        if ("--prepare".equals(args[0])) {
            check(args.length == 6, "prepare argument count");
            entries = readJar(new File(args[1]));
            symbols = load(new File("symbols/autotrade-symbols.properties"));
            symbols.putAll(load(new File(args[2])));
            discoverCombat();
            validate();
            String template = new String(Files.readAllBytes(new File(args[3]).toPath()), "UTF-8");
            StringBuffer generated = new StringBuffer();
            int start = 0;
            while (start < template.length()) {
                int at = template.indexOf('@', start);
                if (at < 0) { generated.append(template.substring(start)); break; }
                generated.append(template.substring(start, at));
                int end = template.indexOf('@', at + 1);
                check(end > at, "unclosed template marker");
                generated.append(sym(template.substring(at + 1, end)));
                start = end + 1;
            }
            File source = new File(args[4]); source.getAbsoluteFile().getParentFile().mkdirs();
            Files.write(source.toPath(), generated.toString().getBytes("UTF-8"));
            symbols.setProperty("input.sha256", sha256(new File(args[1])));
            OutputStream out = new FileOutputStream(args[5]);
            try { symbols.store(out, "Bach Ho automation exact-symbol catalog"); } finally { out.close(); }
            System.out.println("bridgeGeneration=OK; native fields/methods/opcodes=verified");
            return;
        }
        check("--patch".equals(args[0]) && args.length == 5, "patch arguments");
        File input = new File(args[1]), output = new File(args[2]);
        check(!input.getCanonicalFile().equals(output.getCanonicalFile()) && !output.exists(), "distinct new output required");
        entries = readJar(input); symbols = load(new File(args[4]));
        check(sha256(input).equals(sym("input.sha256")), "prepared input hash changed");
        validate();
        Map<String, byte[]> before = new LinkedHashMap<String, byte[]>(entries);
        ClassNode facade = owner("HSOAutoTradeSupport");
        MethodNode action = method(facade, "handleMenuAction", "(II)Z");
        InsnList pre = new InsnList(); LabelNode pass = new LabelNode();
        pre.add(new VarInsnNode(ILOAD, 0)); pre.add(new VarInsnNode(ILOAD, 1));
        pre.add(call(HOOK, "handleMenuAction", "(II)Z")); pre.add(new JumpInsnNode(IFEQ, pass));
        pre.add(new InsnNode(ICONST_1)); pre.add(new InsnNode(IRETURN)); pre.add(pass);
        if (facade.version >= V1_6) pre.add(new FrameNode(F_SAME, 0, null, 0, null));
        action.instructions.insert(pre);
        beforeReturn(method(facade, "tick", "()V"), call(HOOK, "tick", "()V"), RETURN);
        beforeReturn(method(facade, "autoAttackPath", "([S)[S"), call(HOOK, "filterPath", "([S)[S"), ARETURN);
        MethodNode gate = method(facade, "autoTargetGate", "(Ljava/lang/Object;Ljava/lang/Object;)Z");
        gate.instructions.clear(); gate.tryCatchBlocks.clear(); if (gate.localVariables != null) gate.localVariables.clear();
        LabelNode rejected = new LabelNode();
        gate.instructions.add(new VarInsnNode(ALOAD, 0)); gate.instructions.add(new VarInsnNode(ALOAD, 1));
        gate.instructions.add(call("LunaGeneratedLegacyHooks", "autoTargetGate", gate.desc));
        gate.instructions.add(new JumpInsnNode(IFEQ, rejected)); gate.instructions.add(new VarInsnNode(ALOAD, 0));
        gate.instructions.add(call(HOOK, "allowTarget", "(Ljava/lang/Object;)Z")); gate.instructions.add(new InsnNode(IRETURN));
        gate.instructions.add(rejected); if (facade.version >= V1_6) gate.instructions.add(new FrameNode(F_SAME, 0, null, 0, null));
        gate.instructions.add(new InsnNode(ICONST_0)); gate.instructions.add(new InsnNode(IRETURN));
        saveChecked(facade, before.get(facade.name + ".class"), new String[] {"handleMenuAction(II)Z", "tick()V", "autoAttackPath([S)[S", "autoTargetGate(Ljava/lang/Object;Ljava/lang/Object;)Z"});

        ClassNode legacy = owner("LunaGeneratedLegacyHooks");
        MethodNode entry = method(legacy, "onFreshLoginWorldEntry", "()V");
        check(executable(entry) == 1 && entry.instructions.getLast().getOpcode() == RETURN, "fresh-login extension must be empty");
        entry.instructions.insert(call(HOOK, "onLoginEntry", "()V"));
        MethodNode ownership = method(legacy, "attackRecoveryRouteOwnsCombat", "()Z");
        int ownershipReturns = 0;
        for (AbstractInsnNode insn : ownership.instructions.toArray()) if (insn.getOpcode() == IRETURN) {
            InsnList own = new InsnList(); own.add(call(HOOK, "ownsCombat", "()Z")); own.add(new InsnNode(IOR));
            ownership.instructions.insertBefore(insn, own); ownershipReturns++;
        }
        check(ownershipReturns == 1, "combat ownership return count");
        saveChecked(legacy, before.get(legacy.name + ".class"), new String[] {"onFreshLoginWorldEntry()V", "attackRecoveryRouteOwnsCombat()Z"});

        ClassNode pet = owner("HSOSKYPetFeedSupport");
        MethodNode rearm = method(pet, "reengageAutoAttack", "()V");
        InsnList guard = new InsnList(); LabelNode nativeRearm = new LabelNode();
        guard.add(call(HOOK, "shouldPauseNativeCombat", "()Z")); guard.add(new JumpInsnNode(IFEQ, nativeRearm));
        guard.add(new InsnNode(RETURN)); guard.add(nativeRearm);
        if (pet.version >= V1_6) guard.add(new FrameNode(F_SAME, 0, null, 0, null));
        rearm.instructions.insert(guard);
        saveChecked(pet, before.get(pet.name + ".class"), new String[] {"reengageAutoAttack()V"});

        ClassNode engine = owner(sym("class.ENGINE"));
        MethodNode menu = method(engine, sym("engine.method.showMainMenu"), "()V");
        check((menu.access & ACC_STATIC) != 0, "main menu must be static");
        AbstractInsnNode anchor = null;
        for (AbstractInsnNode insn : menu.instructions.toArray()) if (insn instanceof LdcInsnNode
                && "Auto Đi Buôn".equals(((LdcInsnNode) insn).cst)) {
            check(anchor == null, "duplicate trade menu row");
            for (AbstractInsnNode next = insn; next != null; next = next.getNext()) if (next instanceof MethodInsnNode
                    && ((MethodInsnNode) next).owner.equals(sym("class.VEC"))
                    && ((MethodInsnNode) next).name.equals(sym("vec.add"))
                    && ((MethodInsnNode) next).desc.equals("(Ljava/lang/Object;)V")) { anchor = next; break; }
        }
        check(anchor != null, "main menu anchor missing");
        InsnList row = new InsnList();
        row.add(new VarInsnNode(ALOAD, 0)); row.add(new TypeInsnNode(NEW, sym("class.CMD"))); row.add(new InsnNode(DUP));
        row.add(new LdcInsnNode("Auto tự đánh >")); row.add(new IntInsnNode(BIPUSH, -90));
        row.add(new FieldInsnNode(GETSTATIC, engine.name, sym("engine.field.INSTANCE"), "L" + engine.name + ";"));
        row.add(new MethodInsnNode(INVOKESPECIAL, sym("class.CMD"), "<init>", "(Ljava/lang/String;IL" + sym("class.LISTENER") + ";)V", false));
        row.add(new MethodInsnNode(INVOKEVIRTUAL, sym("class.VEC"), sym("vec.add"), "(Ljava/lang/Object;)V", false));
        menu.instructions.insert(anchor, row);
        MethodNode npc = method(engine, sym("engine.method.onNpcMenuOpened"), "(SBL" + sym("class.VEC") + ";)Z");
        check((npc.access & ACC_STATIC) != 0, "NPC menu callback must be static");
        InsnList observe = new InsnList();
        observe.add(new VarInsnNode(ILOAD, 0)); observe.add(new VarInsnNode(ILOAD, 1)); observe.add(new VarInsnNode(ALOAD, 2));
        observe.add(call("BachHoGeneratedAutomationBridge", "observeMaterialMenu", "(SBLjava/lang/Object;)V"));
        npc.instructions.insert(observe);
        saveChecked(engine, before.get(engine.name + ".class"), new String[] {menu.name + menu.desc, npc.name + npc.desc});
        String[] added = {"BachHoGeneratedAutomationBridge", "luna/api/AutoCombatSnapshot", "luna/api/BachHoAutomationBridge",
                "luna/features/AutoCombatController", "luna/features/LoginMaterialController", HOOK};
        for (String name : added) {
            check(!entries.containsKey(name + ".class"), "already patched class " + name);
            byte[] bytes = Files.readAllBytes(new File(args[3], name + ".class").toPath());
            ClassNode node = parse(bytes); check(node.version <= V1_7 && node.name.equals(name), "support identity/version");
            for (MethodNode m : node.methods) for (AbstractInsnNode insn : m.instructions.toArray()) {
                check(!(insn instanceof InvokeDynamicInsnNode), "CLDC invokedynamic");
                if (insn instanceof TypeInsnNode) check(!((TypeInsnNode) insn).desc.equals("java/lang/StringBuilder"), "CLDC StringBuilder");
            }
            entries.put(name + ".class", bytes);
        }
        output.getAbsoluteFile().getParentFile().mkdirs();
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try { for (Map.Entry<String, byte[]> e : entries.entrySet()) { jar.putNextEntry(new JarEntry(e.getKey())); jar.write(e.getValue()); jar.closeEntry(); } }
        finally { jar.close(); }
        Map<String, byte[]> saved = readJar(output); check(saved.size() == entries.size(), "output entry count");
        for (Map.Entry<String, byte[]> e : entries.entrySet()) check(Arrays.equals(e.getValue(), saved.get(e.getKey())), "saved entry " + e.getKey());
        for (Map.Entry<String, byte[]> e : before.entrySet()) if (!e.getKey().endsWith(".class"))
            check(Arrays.equals(e.getValue(), saved.get(e.getKey())), "resource changed " + e.getKey());
        symbols.setProperty("output.sha256", sha256(output));
        OutputStream out = new FileOutputStream(args[4]); try { symbols.store(out, "Bach Ho automation verified input/output"); } finally { out.close(); }
        System.out.println("patchScope=OK (4 existing classes, 9 methods; 6 new classes)");
        System.out.println("manifest/resources/native combat packets/cadence=unchanged");
        System.out.println("sha256=" + sha256(output));
    }

    private static void discoverCombat() {
        ClassNode support = owner("HSOSKYPetFeedSupport");
        MethodNode rearm = method(support, "reengageAutoAttack", "()V");
        for (AbstractInsnNode insn : rearm.instructions.toArray()) if (insn instanceof FieldInsnNode && insn.getOpcode() == PUTSTATIC) {
            FieldInsnNode field = (FieldInsnNode) insn;
            AbstractInsnNode previous = previous(insn);
            if (previous instanceof FieldInsnNode && previous.getOpcode() == GETFIELD) {
                FieldInsnNode coordinate = (FieldInsnNode) previous;
                if (coordinate.name.equals(sym("player.x"))) {
                    symbols.setProperty("combat.origin.owner", field.owner); symbols.setProperty("combat.originX.field", field.name);
                } else if (coordinate.name.equals(sym("player.y"))) {
                    check(field.owner.equals(sym("combat.origin.owner")), "origin axes owners differ");
                    symbols.setProperty("combat.originY.field", field.name);
                }
            }
        }
        MethodNode auto = method(support, "isPlayerAuto", "()Z");
        FieldInsnNode flag = null;
        for (AbstractInsnNode insn : auto.instructions.toArray()) if (insn instanceof FieldInsnNode
                && ((FieldInsnNode) insn).owner.equals(sym("class.PLAYER")) && ((FieldInsnNode) insn).desc.equals("Z")) {
            check(flag == null || flag.name.equals(((FieldInsnNode) insn).name), "ambiguous player auto field"); flag = (FieldInsnNode) insn;
        }
        check(flag != null, "player auto field missing");
        symbols.setProperty("combat.auto.owner", flag.owner); symbols.setProperty("combat.auto.field", flag.name);
    }

    private static void validate() {
        field(sym("zone.owner"), sym("zone.field"), sym("zone.descriptor"), true);
        field(sym("combat.origin.owner"), sym("combat.originX.field"), "I", true);
        field(sym("combat.origin.owner"), sym("combat.originY.field"), "I", true);
        field(sym("combat.auto.owner"), sym("combat.auto.field"), "Z", true);
        field(sym("class.ENTITY"), sym("combat.deltaX.field"), "I", false);
        field(sym("class.ENTITY"), sym("combat.deltaY.field"), "I", false);
        field(sym("class.MENUBOX"), sym("menu.items"), "L" + sym("class.VEC") + ";", false);
        field(sym("class.CMD"), sym("cmd.label"), "Ljava/lang/String;", false);
        field("LunaGeneratedLegacyHooks", "state", "I", true);
        field("LunaGeneratedLegacyHooks", "n4State", "I", true);
        check(sym("sender.zone.descriptor").equals("(B)V"), "zone sender descriptor");
        MethodNode zone = method(owner(sym("sender.zone.owner")), sym("sender.zone"), sym("sender.zone.descriptor"));
        check(opcode(zone) == Integer.parseInt(sym("sender.zone.opcode")), "native zone sender opcode mismatch");
        MethodNode select = method(owner(sym("class.SENDER")), sym("sender.npcMenuSelect"), "(SBB)V");
        check(opcode(select) == -30, "menu selector opcode must be -30");
    }

    private static void field(String owner, String name, String desc, boolean isStatic) {
        for (FieldNode f : owner(owner).fields) if (f.name.equals(name) && f.desc.equals(desc)) {
            check(((f.access & ACC_STATIC) != 0) == isStatic, "field static mismatch " + name);
            check((f.access & ACC_PRIVATE) == 0, "private field needs an accessor " + name); return;
        }
        throw new IllegalStateException("missing exact field " + owner + "." + name + ":" + desc);
    }
    private static int opcode(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode
                && ((MethodInsnNode) insn).desc.equals("(B)V")) { Integer n = integer(previous(insn)); if (n != null) return n.intValue(); }
        throw new IllegalStateException("missing sender opcode");
    }
    private static Integer integer(AbstractInsnNode n) {
        if (n instanceof IntInsnNode) return ((IntInsnNode) n).operand;
        if (n instanceof LdcInsnNode && ((LdcInsnNode) n).cst instanceof Integer) return (Integer) ((LdcInsnNode) n).cst;
        if (n != null && n.getOpcode() >= ICONST_M1 && n.getOpcode() <= ICONST_5) return n.getOpcode() - ICONST_0;
        return null;
    }
    private static void saveChecked(ClassNode node, byte[] original, String[] changed) {
        ClassWriter writer = new ClassWriter(new ClassReader(original), ClassWriter.COMPUTE_MAXS); node.accept(writer);
        byte[] saved = writer.toByteArray(); ClassNode old = parse(original), next = parse(saved);
        check(old.version == next.version && old.fields.size() == next.fields.size() && old.methods.size() == next.methods.size(), "class topology changed");
        Set<String> allowed = new HashSet<String>(Arrays.asList(changed));
        for (MethodNode m : old.methods) if (!allowed.contains(m.name + m.desc))
            check(Arrays.equals(methodBytes(old.name, m), methodBytes(next.name, method(next, m.name, m.desc))), "unrelated method changed " + m.name + m.desc);
        entries.put(node.name + ".class", saved);
    }
    private static byte[] methodBytes(String owner, MethodNode method) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); writer.visit(V1_7, ACC_PUBLIC, owner, null, "java/lang/Object", null);
        method.accept(writer); writer.visitEnd(); return writer.toByteArray();
    }
    private static void beforeReturn(MethodNode method, MethodInsnNode call, int opcode) {
        int found = 0; for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() == opcode) {
            method.instructions.insertBefore(insn, call); found++;
        }
        check(found == 1, "expected one return in " + method.name);
    }
    private static int executable(MethodNode m) { int count = 0; for (AbstractInsnNode i : m.instructions.toArray()) if (i.getOpcode() >= 0) count++; return count; }
    private static AbstractInsnNode previous(AbstractInsnNode n) { do { n = n.getPrevious(); } while (n != null && n.getOpcode() < 0); return n; }
    private static MethodInsnNode call(String owner, String name, String desc) { return new MethodInsnNode(INVOKESTATIC, owner, name, desc, false); }
    private static MethodNode method(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new IllegalStateException("missing exact method " + node.name + "." + name + desc);
    }
    private static ClassNode owner(String name) { byte[] bytes = entries.get(name + ".class"); check(bytes != null, "missing owner " + name); return parse(bytes); }
    private static ClassNode parse(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static Properties load(File file) throws Exception { Properties p = new Properties(); InputStream in = new FileInputStream(file); try { p.load(in); } finally { in.close(); } return p; }
    private static String sym(String key) { String value = symbols.getProperty(key); check(value != null && value.length() > 0, "missing symbol " + key); return value; }
    private static Map<String, byte[]> readJar(File file) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<String, byte[]>(); JarFile jar = new JarFile(file);
        try { Enumeration<JarEntry> all = jar.entries(); while (all.hasMoreElements()) { JarEntry entry = all.nextElement(); InputStream in = jar.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream();
            try { byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n); } finally { in.close(); }
            check(!result.containsKey(entry.getName()), "duplicate jar entry"); result.put(entry.getName(), out.toByteArray()); } } finally { jar.close(); } return result;
    }
    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); InputStream in = new FileInputStream(file);
        try { byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) >= 0) digest.update(buffer, 0, n); } finally { in.close(); }
        StringBuffer text = new StringBuffer(); for (byte b : digest.digest()) text.append(String.format("%02X", b & 255)); return text.toString();
    }
    private static void check(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
}
