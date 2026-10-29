import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Local-player movement pace: the native entity update applies the local player's velocity
 * through MovePace (one step per 28ms of real time, like ModUpLv) instead of the tempo's
 * five-of-eight tick mask. Other entities keep the mask; step sizes and mount bonus stay native.
 */
public final class HungBySonMovePacePatcher implements Opcodes {
    static final String INPUT = "4842A7CA88C337FB1E9E37DFE45CB85C684458E50B0B1E0148791D0F2ECA1F12";
    static final String CATALOG = "build/hung-battlefield-static-20261024-a/farm-special-attack.properties";
    static final String PACE = "luna/features/MovePace";
    static Properties p = new Properties();

    static void check(boolean value, String why) { if (!value) throw new IllegalStateException(why); }
    static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new IllegalStateException("Missing " + c.name + "." + name + desc);
    }
    static AbstractInsnNode real(AbstractInsnNode n) { while (n != null && n.getOpcode() < 0) n = n.getNext(); return n; }
    static int intValue(AbstractInsnNode n) {
        if (n.getOpcode() >= ICONST_M1 && n.getOpcode() <= ICONST_5) return n.getOpcode() - ICONST_0;
        if (n.getOpcode() == BIPUSH || n.getOpcode() == SIPUSH) return ((IntInsnNode)n).operand;
        return Integer.MIN_VALUE;
    }
    /** 109 >> (hsoMoveTick & 7) & 1; returns the trailing IAND or null. */
    static AbstractInsnNode mask(AbstractInsnNode n, String e) {
        int[] ops = {-1, GETSTATIC, -1, IAND, ISHR, -1, IAND};
        int[] values = {109, 0, 7, 0, 0, 1, 0};
        AbstractInsnNode at = n;
        for (int i = 0; i < ops.length; i++) {
            at = real(at);
            if (at == null) return null;
            if (ops[i] < 0) { if (intValue(at) != values[i]) return null; }
            else if (at.getOpcode() != ops[i]) return null;
            if (ops[i] == GETSTATIC) {
                FieldInsnNode f = (FieldInsnNode)at;
                if (!f.owner.equals(e) || !f.name.equals("hsoMoveTick") || !f.desc.equals("I")) return null;
            }
            if (i < ops.length - 1) at = at.getNext();
        }
        return at;
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 5, "input support output catalog report");
        File input = new File(args[0]), support = new File(args[1]), output = new File(args[2]);
        check(HungBySonExpInfoPack.hash(input).equals(INPUT), "Pinned Battlefield static release");
        check(!output.exists() && !new File(args[3]).exists() && !new File(args[4]).exists(), "Fresh outputs");
        InputStream cfg = new FileInputStream(CATALOG);
        try { p.load(cfg); } finally { cfg.close(); }
        check(INPUT.equals(p.getProperty("output.sha256")), "Catalog belongs to the pinned input");
        String E = p.getProperty("entity.owner"), SO = p.getProperty("player.singleton.owner"),
                SF = p.getProperty("player.singleton.field"), SD = p.getProperty("player.singleton.descriptor");
        check(E != null && SO != null && SF != null && SD != null, "Entity and local player singleton");
        Map<String,byte[]> before = HungBySonExpInfoPack.read(input);
        Map<String,byte[]> out = new LinkedHashMap<String,byte[]>(before);
        check(!before.containsKey(PACE + ".class"), "Fresh MovePace");

        ClassNode entity = HungBySonExpInfoPack.node(before.get(E + ".class"));
        ClassNode oldEntity = HungBySonExpInfoPack.node(before.get(E + ".class"));
        MethodNode update = method(entity, "a", "()V");
        List<AbstractInsnNode> masks = new ArrayList<AbstractInsnNode>();
        for (MethodNode m : entity.methods) for (AbstractInsnNode n : m.instructions) {
            AbstractInsnNode end = mask(n, E);
            if (end != null) { check(m == update, "Mask only in the entity update"); masks.add(end); }
        }
        check(masks.size() == 1, "One movement mask");
        AbstractInsnNode branch = real(masks.get(0).getNext());
        check(branch.getOpcode() == IFEQ, "Mask guards the velocity step");
        // aZ += bc follows the guard.
        AbstractInsnNode step = real(branch.getNext());
        check(step.getOpcode() == ALOAD && ((VarInsnNode)step).var == 0, "Velocity step on this");

        // Stack [mask]: for the local player replace it with MovePace.localStep().
        InsnList gate = new InsnList(); LabelNode keep = new LabelNode();
        gate.add(new VarInsnNode(ALOAD, 0));
        gate.add(new FieldInsnNode(GETSTATIC, SO, SF, SD));
        gate.add(new JumpInsnNode(IF_ACMPNE, keep));
        gate.add(new InsnNode(POP));
        gate.add(new MethodInsnNode(INVOKESTATIC, PACE, "localStep", "()Z", false));
        gate.add(keep);
        update.instructions.insertBefore(branch, gate);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); entity.accept(writer);
        out.put(E + ".class", writer.toByteArray());

        // Inverse: remove exactly the gate from a fresh read of the output.
        ClassNode back = HungBySonExpInfoPack.node(out.get(E + ".class"));
        MethodNode backUpdate = method(back, "a", "()V");
        AbstractInsnNode backEnd = null;
        for (AbstractInsnNode n : backUpdate.instructions) { AbstractInsnNode end = mask(n, E); if (end != null) backEnd = end; }
        check(backEnd != null, "Mask kept");
        AbstractInsnNode n = backEnd.getNext();
        int[] expected = {ALOAD, GETSTATIC, IF_ACMPNE, POP, INVOKESTATIC};
        for (int op : expected) {
            n = real(n);
            check(n.getOpcode() == op, "Gate shape " + op);
            if (op == INVOKESTATIC) check(((MethodInsnNode)n).owner.equals(PACE) && ((MethodInsnNode)n).name.equals("localStep"), "Gate call");
            AbstractInsnNode next = n.getNext(); backUpdate.instructions.remove(n); n = next;
        }
        check(real(n).getOpcode() == IFEQ, "Gate ends at the guard");
        for (MethodNode m : oldEntity.methods)
            check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(back, m.name, m.desc))), "Entity inverse " + m.name + m.desc);
        check(back.methods.size() == oldEntity.methods.size() && back.fields.size() == oldEntity.fields.size(), "Entity topology");

        byte[] pace = Files.readAllBytes(new File(support, PACE + ".class").toPath());
        HungBySonExpInfoPack.screen(PACE + ".class", pace);
        ClassNode paceNode = HungBySonExpInfoPack.node(pace);
        method(paceNode, "localStep", "()Z");
        check((method(paceNode, "localStep", "()Z").access & ACC_STATIC) != 0, "Static localStep");
        check(paceNode.version == HungBySonExpInfoPack.node(before.get("luna/features/AutoAttackController.class")).version, "Support version");
        out.put(PACE + ".class", pace);

        List<String> changed = new ArrayList<String>(); int classes = 0, resources = 0;
        for (String name : out.keySet()) {
            if (!before.containsKey(name)) { check(name.equals(PACE + ".class"), "Only MovePace is added " + name); changed.add(name); classes++; continue; }
            if (!Arrays.equals(before.get(name), out.get(name))) { check(name.equals(E + ".class"), "Archive allowlist " + name); changed.add(name); }
            if (name.endsWith(".class")) {
                classes++;
                check(HungBySonExpInfoPack.node(before.get(name)).version == HungBySonExpInfoPack.node(out.get(name)).version, "Class version " + name);
            } else resources++;
        }
        check(changed.size() == 2 && out.size() == before.size() + 1, "Exactly entity changes and MovePace is added");
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String,byte[]> e : out.entrySet()) {
                JarEntry z = new JarEntry(e.getKey()); z.setTime(0); jar.putNextEntry(z); jar.write(e.getValue()); jar.closeEntry();
            }
        } finally { jar.close(); }
        p.setProperty("input.sha256", INPUT); p.setProperty("output.sha256", HungBySonExpInfoPack.hash(output));
        p.setProperty("local.move.step.ms", "28"); p.setProperty("local.move.pace.owner", PACE);
        p.setProperty("changed.classes", String.join(",", changed)); p.setProperty("native.inverse", "PASS");
        OutputStream config = new FileOutputStream(args[3]);
        try { p.store(config, "Local player moves one native step per 28ms of real time"); } finally { config.close(); }
        PrintWriter log = new PrintWriter(args[4], "UTF-8");
        log.println("PASS pinned input; local-player movement gate with exact inverse; MovePace added");
        log.println("CHANGED=" + changed); log.println("CLASSES=" + classes + " UNCHANGED_RESOURCES=" + resources);
        log.println("SHA256=" + p.getProperty("output.sha256")); log.close();
        System.out.println("MOVE_PACE_PATCH=PASS SHA256=" + p.getProperty("output.sha256"));
    }
}
