import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Keeps local-player travel at the original rate while the frame loop runs 1.5x. */
public final class HungBySonSpeedPatcher implements Opcodes {
    private static final String CANVAS = "com/silverknight/a";
    private static final String TARGET = "codexFrameTarget";
    private static final String FRAME_CARRY = "codexFrameRemainder";
    private static final String MOVE_CARRY = "codexMovementRemainder";
    private static final String MOVE_THIS_FRAME = "codexMoveThisFrame";

    private static final class EntryData {
        final JarEntry source;
        byte[] bytes;
        EntryData(JarEntry source, byte[] bytes) { this.source = source; this.bytes = bytes; }
    }

    private HungBySonSpeedPatcher() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.jar output.jar");
        File input = new File(args[0]).getCanonicalFile();
        File output = new File(args[1]).getCanonicalFile();
        if (!input.isFile() || input.equals(output) || output.exists()) {
            throw new IllegalArgumentException("Input missing, equal to output, or output exists");
        }
        List<EntryData> entries = read(input);
        ClassNode player = findPlayer(entries);
        String actor = player.superName;
        String[] fpsFields = findFpsFields(entries);
        String menuOwner = findFpsMenu(entries, fpsFields);
        boolean canvasPatched = false, actorPatched = false, licensePatched = false, menuPatched = false;
        for (EntryData entry : entries) {
            if (entry.source.getName().equals(CANVAS + ".class")) {
                ClassNode node = parse(entry.bytes);
                patchCanvas(node, fpsFields[0], fpsFields[1], fpsFields[2]);
                entry.bytes = writeClass(node);
                canvasPatched = true;
            }
            if (entry.source.getName().equals(actor + ".class")) {
                ClassNode node = parse(entry.bytes);
                patchMovement(node, player.name);
                entry.bytes = writeClass(node);
                actorPatched = true;
            }
            if (entry.source.getName().equals(fpsFields[0] + ".class")) {
                ClassNode node = parse(entry.bytes);
                patchFpsDefaults(node, fpsFields[1], fpsFields[2]);
                entry.bytes = writeClass(node);
                licensePatched = true;
            }
            if (entry.source.getName().equals(menuOwner + ".class")) {
                ClassNode node = parse(entry.bytes);
                patchFpsMenu(node, fpsFields[0], fpsFields[1], fpsFields[2]);
                entry.bytes = writeClass(node);
                menuPatched = true;
            }
        }
        if (!canvasPatched || !actorPatched || !licensePatched || !menuPatched)
            throw new IllegalStateException("Missing frame, movement, FPS setting, or menu class");
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot make output directory");
        write(output, entries);
        System.out.println("FPS_DEFAULT=30; MENU=30/45/60; FRAME_INTERVAL=1000/FPS with remainder");
        System.out.println("LOCAL_MOVEMENT=30 displacement updates/s nominal");
        System.out.println("PLAYER=" + player.name);
        System.out.println("ACTOR=" + actor);
        System.out.println("ENTRIES=" + entries.size());
    }

    private static ClassNode findPlayer(List<EntryData> entries) {
        ClassNode match = null;
        for (EntryData entry : entries) {
            if (!entry.source.getName().endsWith(".class")) continue;
            ClassNode node = parse(entry.bytes);
            MethodNode clinit = method(node, "<clinit>", "()V");
            if (clinit == null) continue;
            boolean twelveSlotArray = false;
            for (AbstractInsnNode i = clinit.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i.getOpcode() == ANEWARRAY && isInt(previous(i), 12)
                        && next(i) != null && next(i).getOpcode() == AASTORE) {
                    twelveSlotArray = true;
                    break;
                }
            }
            if (!twelveSlotArray) continue;
            boolean fiveKeys = false;
            for (MethodNode m : node.methods) {
                if (hasInt(m, 21) && hasInt(m, 23) && hasInt(m, 25)
                        && hasInt(m, 27) && hasInt(m, 29)) fiveKeys = true;
            }
            if (!fiveKeys) continue;
            if (match != null) throw new IllegalStateException("Multiple player classes");
            match = node;
        }
        if (match == null) throw new IllegalStateException("Cannot identify 12-slot player class");
        return match;
    }

    private static String[] findFpsFields(List<EntryData> entries) {
        ClassNode canvas = null;
        for (EntryData entry : entries) {
            if (entry.source.getName().equals(CANVAS + ".class")) canvas = parse(entry.bytes);
        }
        if (canvas == null) throw new IllegalStateException("Canvas missing");
        MethodNode run = method(canvas, "run", "()V");
        FieldInsnNode interval = null;
        int reads = 0;
        for (AbstractInsnNode i = run.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof FieldInsnNode && i.getOpcode() == GETSTATIC
                    && "J".equals(((FieldInsnNode)i).desc)) {
                FieldInsnNode found = (FieldInsnNode)i;
                if (interval != null && (!interval.owner.equals(found.owner)
                        || !interval.name.equals(found.name))) throw new IllegalStateException("Ambiguous interval field");
                interval = found;
                reads++;
            }
        }
        if (interval == null || reads != 2) throw new IllegalStateException("Cannot identify FPS interval");
        for (EntryData entry : entries) {
            if (!entry.source.getName().equals(interval.owner + ".class")) continue;
            ClassNode settings = parse(entry.bytes);
            String fps = null;
            for (FieldNode field : settings.fields) {
                if ("I".equals(field.desc) && field.name.endsWith("ililliliiiii")) fps = field.name;
            }
            if (fps == null) throw new IllegalStateException("FPS setting field missing");
            return new String[]{interval.owner, fps, interval.name};
        }
        throw new IllegalStateException("FPS settings class missing");
    }

    private static String findFpsMenu(List<EntryData> entries, String[] fpsFields) {
        String owner = null;
        for (EntryData entry : entries) {
            if (!entry.source.getName().endsWith(".class")) continue;
            ClassNode node = parse(entry.bytes);
            for (MethodNode method : node.methods) {
                if (!"(II)Z".equals(method.desc)) continue;
                boolean updatesFps = false, updatesInterval = false;
                for (AbstractInsnNode i = method.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (!(i instanceof FieldInsnNode) || i.getOpcode() != PUTSTATIC) continue;
                    FieldInsnNode f = (FieldInsnNode)i;
                    if (f.owner.equals(fpsFields[0]) && f.name.equals(fpsFields[1])) updatesFps = true;
                    if (f.owner.equals(fpsFields[0]) && f.name.equals(fpsFields[2])) updatesInterval = true;
                }
                if (updatesFps && updatesInterval) {
                    if (owner != null) throw new IllegalStateException("Multiple FPS menus");
                    owner = node.name;
                }
            }
        }
        if (owner == null) throw new IllegalStateException("FPS menu missing");
        return owner;
    }

    private static void patchFpsDefaults(ClassNode node, String fpsField, String intervalField) {
        MethodNode clinit = method(node, "<clinit>", "()V");
        int fpsWrites = 0, intervalWrites = 0;
        for (AbstractInsnNode i = clinit.instructions.getFirst(); i != null; i = i.getNext()) {
            if (!(i instanceof FieldInsnNode) || i.getOpcode() != PUTSTATIC) continue;
            FieldInsnNode f = (FieldInsnNode)i;
            if (f.name.equals(fpsField) && f.owner.equals(node.name)) {
                AbstractInsnNode prior = previous(i);
                if (!(prior instanceof IntInsnNode) || ((IntInsnNode)prior).operand != 25)
                    throw new IllegalStateException("Unexpected default FPS");
                ((IntInsnNode)prior).operand = 30;
                fpsWrites++;
            }
            if (f.name.equals(intervalField) && f.owner.equals(node.name)) {
                AbstractInsnNode prior = previous(i);
                if (!(prior instanceof LdcInsnNode) || !Long.valueOf(40L).equals(((LdcInsnNode)prior).cst))
                    throw new IllegalStateException("Unexpected default frame interval");
                ((LdcInsnNode)prior).cst = Long.valueOf(33L);
                intervalWrites++;
            }
        }
        if (fpsWrites != 1 || intervalWrites != 1) throw new IllegalStateException("FPS defaults incomplete");
    }

    private static void patchFpsMenu(ClassNode node, String settingsOwner, String fpsField, String intervalField) {
        MethodNode action = null;
        for (MethodNode method : node.methods) {
            if (!"(II)Z".equals(method.desc)) continue;
            for (AbstractInsnNode i = method.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof FieldInsnNode && i.getOpcode() == PUTSTATIC) {
                    FieldInsnNode f = (FieldInsnNode)i;
                    if (f.owner.equals(settingsOwner) && f.name.equals(intervalField)) action = method;
                }
            }
        }
        if (action == null) throw new IllegalStateException("FPS menu action missing");
        boolean inFpsAction = false;
        int[] counts = new int[4];
        for (AbstractInsnNode i = action.instructions.getFirst(); i != null; i = i.getNext()) {
            if (!(i instanceof IntInsnNode)) continue;
            IntInsnNode n = (IntInsnNode)i;
            if (n.operand == -31) { inFpsAction = true; continue; }
            if (inFpsAction && n.operand == -34) break;
            if (!inFpsAction) continue;
            if (n.operand == 25) { n.operand = 30; counts[0]++; }
            else if (n.operand == 30) { n.operand = 45; counts[1]++; }
            else if (n.operand == 35) { n.operand = 60; counts[2]++; }
            else if (n.operand == 40) { n.operand = 30; counts[3]++; }
        }
        if (counts[0] != 2 || counts[1] != 2 || counts[2] != 2 || counts[3] != 1)
            throw new IllegalStateException("Unexpected FPS menu choices");
    }

    private static void patchCanvas(ClassNode node, String fpsOwner, String fpsField, String intervalField) {
        if (!CANVAS.equals(node.name)) throw new IllegalStateException("Wrong canvas class");
        MethodNode run = method(node, "run", "()V");
        if (run == null) throw new IllegalStateException("Canvas run() missing");
        int frameReads = 0;
        for (AbstractInsnNode i = run.instructions.getFirst(); i != null;) {
            AbstractInsnNode following = i.getNext();
            if (i instanceof FieldInsnNode && i.getOpcode() == GETSTATIC
                    && "J".equals(((FieldInsnNode)i).desc)
                    && fpsOwner.equals(((FieldInsnNode)i).owner)
                    && intervalField.equals(((FieldInsnNode)i).name)) {
                run.instructions.set(i, new FieldInsnNode(GETSTATIC, CANVAS, TARGET, "J"));
                frameReads++;
            }
            i = following;
        }
        if (frameReads != 2) throw new IllegalStateException("Expected two frame-interval reads, got " + frameReads);
        node.fields.add(new FieldNode(ACC_PRIVATE | ACC_STATIC, TARGET, "J", null, null));
        node.fields.add(new FieldNode(ACC_PRIVATE | ACC_STATIC, FRAME_CARRY, "I", null, null));
        node.fields.add(new FieldNode(ACC_PRIVATE | ACC_STATIC, MOVE_CARRY, "I", null, null));
        node.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, MOVE_THIS_FRAME, "Z", null, null));

        MethodNode target = new MethodNode(ASM9, ACC_PRIVATE | ACC_STATIC,
                "codexNextFrameTarget", "()J", null, null);
        LabelNode validFps = new LabelNode();
        LabelNode noExtraMs = new LabelNode();
        LabelNode skipMove = new LabelNode();
        LabelNode done = new LabelNode();
        target.instructions.add(new FieldInsnNode(GETSTATIC, fpsOwner, fpsField, "I"));
        target.instructions.add(new VarInsnNode(ISTORE, 0));
        target.instructions.add(new VarInsnNode(ILOAD, 0));
        target.instructions.add(new JumpInsnNode(IFGT, validFps));
        target.instructions.add(new IntInsnNode(BIPUSH, 30));
        target.instructions.add(new VarInsnNode(ISTORE, 0));
        target.instructions.add(validFps);
        target.instructions.add(new FieldInsnNode(GETSTATIC, CANVAS, FRAME_CARRY, "I"));
        target.instructions.add(new IntInsnNode(SIPUSH, 1000));
        target.instructions.add(new VarInsnNode(ILOAD, 0));
        target.instructions.add(new InsnNode(IREM));
        target.instructions.add(new InsnNode(IADD));
        target.instructions.add(new VarInsnNode(ISTORE, 1));
        target.instructions.add(new IntInsnNode(SIPUSH, 1000));
        target.instructions.add(new VarInsnNode(ILOAD, 0));
        target.instructions.add(new InsnNode(IDIV));
        target.instructions.add(new VarInsnNode(ISTORE, 2));
        target.instructions.add(new VarInsnNode(ILOAD, 1));
        target.instructions.add(new VarInsnNode(ILOAD, 0));
        target.instructions.add(new JumpInsnNode(IF_ICMPLT, noExtraMs));
        target.instructions.add(new VarInsnNode(ILOAD, 1));
        target.instructions.add(new VarInsnNode(ILOAD, 0));
        target.instructions.add(new InsnNode(ISUB));
        target.instructions.add(new VarInsnNode(ISTORE, 1));
        target.instructions.add(new org.objectweb.asm.tree.IincInsnNode(2, 1));
        target.instructions.add(noExtraMs);
        target.instructions.add(new VarInsnNode(ILOAD, 1));
        target.instructions.add(new FieldInsnNode(PUTSTATIC, CANVAS, FRAME_CARRY, "I"));

        target.instructions.add(new FieldInsnNode(GETSTATIC, CANVAS, MOVE_CARRY, "I"));
        target.instructions.add(new IntInsnNode(BIPUSH, 30));
        target.instructions.add(new InsnNode(IADD));
        target.instructions.add(new VarInsnNode(ISTORE, 3));
        target.instructions.add(new VarInsnNode(ILOAD, 3));
        target.instructions.add(new VarInsnNode(ILOAD, 0));
        target.instructions.add(new JumpInsnNode(IF_ICMPLT, skipMove));
        target.instructions.add(new VarInsnNode(ILOAD, 3));
        target.instructions.add(new VarInsnNode(ILOAD, 0));
        target.instructions.add(new InsnNode(ISUB));
        target.instructions.add(new FieldInsnNode(PUTSTATIC, CANVAS, MOVE_CARRY, "I"));
        target.instructions.add(new InsnNode(ICONST_1));
        target.instructions.add(new FieldInsnNode(PUTSTATIC, CANVAS, MOVE_THIS_FRAME, "Z"));
        target.instructions.add(new JumpInsnNode(GOTO, done));
        target.instructions.add(skipMove);
        target.instructions.add(new VarInsnNode(ILOAD, 3));
        target.instructions.add(new FieldInsnNode(PUTSTATIC, CANVAS, MOVE_CARRY, "I"));
        target.instructions.add(new InsnNode(ICONST_0));
        target.instructions.add(new FieldInsnNode(PUTSTATIC, CANVAS, MOVE_THIS_FRAME, "Z"));
        target.instructions.add(done);
        target.instructions.add(new VarInsnNode(ILOAD, 2));
        target.instructions.add(new InsnNode(I2L));
        target.instructions.add(new InsnNode(LRETURN));
        node.methods.add(target);

        AbstractInsnNode first = run.instructions.getFirst();
        while (first != null && first.getOpcode() < 0) first = first.getNext();
        if (first == null) throw new IllegalStateException("Empty canvas loop");
        InsnList begin = new InsnList();
        begin.add(new MethodInsnNode(INVOKESTATIC, CANVAS, "codexNextFrameTarget", "()J", false));
        begin.add(new FieldInsnNode(PUTSTATIC, CANVAS, TARGET, "J"));
        run.instructions.insertBefore(first, begin);
    }

    private static void patchMovement(ClassNode node, String player) {
        MethodNode update = method(node, "a", "()V");
        if (update == null) throw new IllegalStateException("Actor a() missing");
        AbstractInsnNode first = update.instructions.getFirst();
        while (first != null && first.getOpcode() < 0) first = first.getNext();
        if (first == null || first.getOpcode() != ALOAD) throw new IllegalStateException("Unexpected actor update start");
        AbstractInsnNode afterMove = null;
        int writes = 0;
        for (AbstractInsnNode i = first; i != null && writes < 2; i = i.getNext()) {
            if (i instanceof FieldInsnNode && i.getOpcode() == PUTFIELD) {
                FieldInsnNode field = (FieldInsnNode)i;
                if (node.name.equals(field.owner)
                        && ((writes == 0 && "aZ".equals(field.name))
                        || (writes == 1 && "ba".equals(field.name)))
                        && "I".equals(field.desc)) {
                    writes++;
                    afterMove = i.getNext();
                }
            }
        }
        if (writes != 2 || afterMove == null) throw new IllegalStateException("Actor displacement writes missing");
        LabelNode move = new LabelNode();
        LabelNode done = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(ALOAD, 0));
        guard.add(new TypeInsnNode(INSTANCEOF, player));
        guard.add(new JumpInsnNode(IFEQ, move));
        guard.add(new FieldInsnNode(GETSTATIC, CANVAS, MOVE_THIS_FRAME, "Z"));
        guard.add(new JumpInsnNode(IFEQ, done));
        guard.add(move);
        update.instructions.insertBefore(first, guard);
        update.instructions.insertBefore(afterMove, done);
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        return null;
    }
    private static boolean hasInt(MethodNode m, int n) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) if (isInt(i, n)) return true;
        return false;
    }
    private static boolean isInt(AbstractInsnNode i, int n) {
        if (i == null) return false;
        int op = i.getOpcode();
        if (op >= ICONST_M1 && op <= ICONST_5) return op - ICONST_0 == n;
        return i instanceof IntInsnNode && ((IntInsnNode)i).operand == n;
    }
    private static AbstractInsnNode next(AbstractInsnNode i) {
        AbstractInsnNode n = i == null ? null : i.getNext();
        while (n != null && n.getOpcode() < 0) n = n.getNext();
        return n;
    }
    private static AbstractInsnNode previous(AbstractInsnNode i) {
        AbstractInsnNode n = i == null ? null : i.getPrevious();
        while (n != null && n.getOpcode() < 0) n = n.getPrevious();
        return n;
    }
    private static ClassNode parse(byte[] bytes) {
        ClassNode node = new ClassNode(ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
    private static byte[] writeClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
    private static List<EntryData> read(File input) throws IOException {
        List<EntryData> result = new ArrayList<EntryData>();
        JarFile jar = new JarFile(input);
        try {
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                JarEntry entry = e.nextElement();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                java.io.InputStream in = jar.getInputStream(entry);
                try {
                    byte[] block = new byte[8192];
                    for (int count; (count = in.read(block)) >= 0;) out.write(block, 0, count);
                } finally { in.close(); }
                result.add(new EntryData(entry, out.toByteArray()));
            }
        } finally { jar.close(); }
        return result;
    }
    private static void write(File output, List<EntryData> entries) throws IOException {
        JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
        try {
            for (EntryData data : entries) {
                JarEntry entry = new JarEntry(data.source.getName());
                if (data.source.getTime() >= 0) entry.setTime(data.source.getTime());
                if (data.source.getComment() != null) entry.setComment(data.source.getComment());
                if (data.source.getExtra() != null) entry.setExtra(data.source.getExtra());
                out.putNextEntry(entry);
                out.write(data.bytes);
                out.closeEntry();
            }
        } finally { out.close(); }
    }
}
