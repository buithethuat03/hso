import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Executes the native receiver's real DataInputStream reads with a synthetic packet. */
public final class ChibiEntryProtocolBinaryTest implements Opcodes {
    private static final String SELF = "ChibiEntryProtocolBinaryTest";
    private static final String DATA = "java/io/DataInputStream";
    private static final byte[] TRAILER = new byte[] { (byte)0xA5, (byte)0x5A };
    private static final int[] COUNTS = new int[] { 0, 1, 127, 128, 300 };
    private static final int[] SUBTYPES = new int[] { 3, 4, 5, 7 };
    private static final String[] MARKERS = new String[] {
            "size item quest=", "size material=", "size item="
    };
    private static DataInputStream input;
    private static int captured;
    private static int cursor;
    private static int probeId;
    private static int checks;

    private ChibiEntryProtocolBinaryTest() { }

    public static DataInputStream stream() { return input; }

    public static void capture(int value) { captured = value; }

    public static void captureCursor() {
        try { cursor = input.available(); }
        catch (Exception e) { throw new IllegalStateException("Cannot read synthetic cursor", e); }
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 4, "donor.jar original.jar patched.jar catalog.properties");
        File donorFile = new File(args[0]);
        File originalFile = new File(args[1]);
        File patchedFile = new File(args[2]);
        Properties catalog = load(new File(args[3]));

        String donorHash = ChibiEntryCompatibilityPatcher.hash(donorFile);
        String originalHash = ChibiEntryCompatibilityPatcher.hash(originalFile);
        String patchedHash = ChibiEntryCompatibilityPatcher.hash(patchedFile);
        check(ChibiEntryCompatibilityPatcher.DONOR.equals(donorHash), "Hso donor hash changed");
        check(ChibiEntryCompatibilityPatcher.CHIBI.equals(originalHash)
                        || ChibiEntryCompatibilityPatcher.LUNA.equals(originalHash),
                "Original bytes are not the inspected Chibi or Luna input");
        check(originalHash.equals(catalog.getProperty("input.sha256")), "Catalog belongs to another input");
        check(patchedHash.equals(catalog.getProperty("output.sha256")), "Catalog belongs to another candidate");
        check(donorHash.equals(catalog.getProperty("reference.sha256")), "Catalog donor hash mismatch");

        Map<String,byte[]> donor = ChibiEntryCompatibilityPatcher.read(donorFile);
        Map<String,byte[]> original = ChibiEntryCompatibilityPatcher.read(originalFile);
        Map<String,byte[]> patched = ChibiEntryCompatibilityPatcher.read(patchedFile);
        String receiver = required(catalog, "protocol.receiver");
        String methodName = required(catalog, "protocol.method");
        String methodDesc = required(catalog, "protocol.desc");
        MethodNode originalMethod = ChibiEntryCompatibilityPatcher.method(
                ChibiEntryCompatibilityPatcher.node(original.get(receiver + ".class")), methodName, methodDesc);
        MethodNode patchedMethod = ChibiEntryCompatibilityPatcher.method(
                ChibiEntryCompatibilityPatcher.node(patched.get(receiver + ".class")), methodName, methodDesc);
        MethodNode donorMethod = ChibiEntryCompatibilityPatcher.findInventory(
                ChibiEntryCompatibilityPatcher.node(donor.get("Illllll.class")));

        MethodInsnNode[] originalReads = listCounts(originalMethod);
        MethodInsnNode[] patchedReads = listCounts(patchedMethod);
        MethodInsnNode[] donorReads = listCounts(donorMethod);
        verifyWidths(originalMethod, patchedMethod, donorMethod, originalReads, patchedReads, donorReads);

        URLClassLoader donorLoader = loader(donorFile);
        URLClassLoader originalLoader = loader(originalFile);
        URLClassLoader patchedLoader = loader(patchedFile);
        try {
            verifyCapacity(originalMethod, patchedMethod, donorMethod,
                    originalLoader, patchedLoader, donorLoader);
            for (int i = 0; i < SUBTYPES.length; i++) {
                for (int count : COUNTS) {
                    byte[] packet = listPacket(SUBTYPES[i], count);
                    ProbeResult old = run(originalLoader, originalMethod, originalReads[i], packet, (byte)0);
                    ProbeResult reference = run(donorLoader, donorMethod, donorReads[i], packet, (byte)0);
                    ProbeResult fixed = run(patchedLoader, patchedMethod, patchedReads[i], packet, (byte)0);
                    int expectedCursor = packet.length - TRAILER.length;
                    check(reference.value == count && reference.remaining == TRAILER.length
                                    && reference.consumed == expectedCursor,
                            "Hso donor wire count/cursor mismatch, subtype=" + SUBTYPES[i] + " count=" + count);
                    check(fixed.value == count && fixed.remaining == TRAILER.length
                                    && fixed.consumed == expectedCursor,
                            "Patched native count/cursor mismatch, subtype=" + SUBTYPES[i] + " count=" + count);
                    check(old.consumed == expectedCursor - 1 && old.remaining == TRAILER.length + 1,
                            "Original native reader did not expose the one-byte cursor shift, subtype="
                                    + SUBTYPES[i] + " count=" + count);
                    if (count != 0)
                        check(old.value != count,
                                "Original native reader unexpectedly returned the two-byte count, subtype="
                                        + SUBTYPES[i] + " count=" + count);
                }
            }
        } finally {
            donorLoader.close();
            originalLoader.close();
            patchedLoader.close();
        }
        System.out.println("CHIBI_ENTRY_PROTOCOL=PASS; checks=" + checks
                + "; subtypeCounts=" + (SUBTYPES.length * COUNTS.length)
                + "; contexts=0,9; donor/native/original cursor comparison; synthetic packets only");
    }

    private static void verifyWidths(MethodNode old, MethodNode fixed, MethodNode donor,
            MethodInsnNode[] oldCounts, MethodInsnNode[] fixedCounts, MethodInsnNode[] donorCounts) {
        java.util.List<MethodInsnNode> oldReads = ChibiEntryCompatibilityPatcher.reads(old);
        java.util.List<MethodInsnNode> fixedReads = ChibiEntryCompatibilityPatcher.reads(fixed);
        java.util.List<MethodInsnNode> donorAllReads = ChibiEntryCompatibilityPatcher.reads(donor);
        check(oldReads.get(0).name.equals("readByte"), "Original opcode-65 capacity is not byte-wide");
        check(fixedReads.get(0).name.equals("readShort"), "Patched opcode-65 capacity is not short-wide");
        check(donorAllReads.get(0).name.equals("readShort"), "Donor opcode-65 capacity is not short-wide");
        for (int i = 0; i < SUBTYPES.length; i++) {
            check(oldCounts[i].name.equals("readByte"), "Original list count was not byte-wide for subtype " + SUBTYPES[i]);
            check(fixedCounts[i].name.equals("readShort"), "Patched list count is not short-wide for subtype " + SUBTYPES[i]);
            check(donorCounts[i].name.equals("readShort"), "Donor list count is not short-wide for subtype " + SUBTYPES[i]);
        }
    }

    private static void verifyCapacity(MethodNode old, MethodNode fixed, MethodNode donor,
            ClassLoader oldLoader, ClassLoader fixedLoader, ClassLoader donorLoader) throws Exception {
        MethodInsnNode oldRead = ChibiEntryCompatibilityPatcher.reads(old).get(0);
        MethodInsnNode fixedRead = ChibiEntryCompatibilityPatcher.reads(fixed).get(0);
        MethodInsnNode donorRead = ChibiEntryCompatibilityPatcher.reads(donor).get(0);
        byte[] packet = capacityPacket(300);
        ProbeResult previous = run(oldLoader, old, oldRead, packet, (byte)9);
        ProbeResult reference = run(donorLoader, donor, donorRead, packet, (byte)9);
        ProbeResult candidate = run(fixedLoader, fixed, fixedRead, packet, (byte)9);
        int expectedCursor = packet.length - TRAILER.length;
        check(reference.value == 300 && reference.consumed == expectedCursor
                        && reference.remaining == TRAILER.length,
                "Hso opcode-65 capacity reader/cursor mismatch");
        check(candidate.value == 300 && candidate.consumed == expectedCursor
                        && candidate.remaining == TRAILER.length,
                "Patched opcode-65 capacity reader/cursor mismatch");
        check(previous.value != 300 && previous.consumed == expectedCursor - 1
                        && previous.remaining == TRAILER.length + 1,
                "Original opcode-65 capacity reader did not expose the one-byte cursor shift");
    }

    private static MethodInsnNode[] listCounts(MethodNode method) {
        MethodInsnNode[] result = new MethodInsnNode[4];
        result[0] = ChibiEntryCompatibilityPatcher.countBeforeMarker(method, MARKERS[2]); // subtype 3
        result[1] = ChibiEntryCompatibilityPatcher.reads(method).get(6); // subtype 4
        result[2] = ChibiEntryCompatibilityPatcher.countBeforeMarker(method, MARKERS[0]); // subtype 5
        result[3] = ChibiEntryCompatibilityPatcher.countBeforeMarker(method, MARKERS[1]); // subtype 7
        return result;
    }

    private static byte[] listPacket(int subtype, int count) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(0); // Inventory action.
        out.writeByte(subtype);
        out.writeLong(0L); // Shared opcode-16 header.
        out.writeInt(0);
        out.writeByte(0); // Per-subtype category/flags byte.
        out.writeShort(count);
        out.write(TRAILER);
        out.flush();
        return bytes.toByteArray();
    }

    private static byte[] capacityPacket(int capacity) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(capacity);
        out.write(TRAILER);
        out.flush();
        return bytes.toByteArray();
    }

    private static ProbeResult run(ClassLoader targetLoader, MethodNode source,
            MethodInsnNode targetRead, byte[] packet, byte context) throws Exception {
        input = new DataInputStream(new ByteArrayInputStream(packet));
        captured = Integer.MIN_VALUE;
        cursor = -1;
        String probeName = "ChibiEntryWireProbe" + (++probeId);
        byte[] bytes = makeProbe(source, targetRead, probeName);
        ProbeLoader loader = new ProbeLoader(targetLoader);
        Class<?> probeClass = loader.define(probeName.replace('/', '.'), bytes);
        Method probe = probeClass.getMethod("probe", parameterTypes(probeClass.getClassLoader(), source.desc));
        Object instance = Modifier.isStatic(probe.getModifiers()) ? null : probeClass.getDeclaredConstructor().newInstance();
        Object[] arguments = new Object[probe.getParameterTypes().length];
        for (int i = 0; i < arguments.length; i++) {
            Class<?> type = probe.getParameterTypes()[i];
            if (type == byte.class) arguments[i] = Byte.valueOf(context);
            else if (type == short.class) arguments[i] = Short.valueOf((short)context);
            else if (type == int.class) arguments[i] = Integer.valueOf(context);
            else arguments[i] = null;
        }
        try { probe.invoke(instance, arguments); }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new IllegalStateException("Native receiver probe failed at " + source.name + source.desc, cause);
        }
        check(captured != Integer.MIN_VALUE && cursor >= 0,
                "Native receiver did not reach target DataInputStream read " + targetRead.name + targetRead.desc);
        return new ProbeResult(captured, packet.length - cursor, cursor);
    }

    private static Class<?>[] parameterTypes(ClassLoader loader, String descriptor) throws Exception {
        Type[] types = Type.getArgumentTypes(descriptor);
        Class<?>[] result = new Class<?>[types.length];
        for (int i = 0; i < types.length; i++) {
            Type type = types[i];
            switch (type.getSort()) {
                case Type.BOOLEAN: result[i] = boolean.class; break;
                case Type.BYTE: result[i] = byte.class; break;
                case Type.CHAR: result[i] = char.class; break;
                case Type.SHORT: result[i] = short.class; break;
                case Type.INT: result[i] = int.class; break;
                case Type.FLOAT: result[i] = float.class; break;
                case Type.LONG: result[i] = long.class; break;
                case Type.DOUBLE: result[i] = double.class; break;
                case Type.ARRAY: result[i] = Class.forName(type.getDescriptor().replace('/', '.'), false, loader); break;
                case Type.OBJECT: result[i] = Class.forName(type.getClassName(), false, loader); break;
                default: throw new IllegalArgumentException("Unsupported method argument " + type);
            }
        }
        return result;
    }

    private static byte[] makeProbe(MethodNode source, MethodInsnNode targetRead, String name) {
        ClassNode generated = new ClassNode();
        generated.version = V1_5;
        generated.access = ACC_PUBLIC;
        generated.name = name;
        generated.superName = "java/lang/Object";
        MethodNode ctor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new InsnNode(RETURN));
        generated.methods.add(ctor);

        int access = ACC_PUBLIC | (source.access & ACC_STATIC);
        MethodNode copy = new MethodNode(access, "probe", source.desc, null, null);
        Map<LabelNode,LabelNode> labels = new java.util.IdentityHashMap<LabelNode,LabelNode>();
        for (AbstractInsnNode node : source.instructions.toArray())
            if (node instanceof LabelNode) labels.put((LabelNode)node, new LabelNode());
        for (AbstractInsnNode node : source.instructions.toArray()) {
            if (node instanceof FrameNode) continue;
            if (node instanceof LineNumberNode) {
                LineNumberNode line = (LineNumberNode)node;
                copy.instructions.add(new LineNumberNode(line.line, label(labels, line.start)));
                continue;
            }
            if (node == targetRead) {
                copy.instructions.add(node.clone(labels));
                copy.instructions.add(new InsnNode(DUP));
                copy.instructions.add(new MethodInsnNode(INVOKESTATIC, SELF, "capture", "(I)V", false));
                copy.instructions.add(new MethodInsnNode(INVOKESTATIC, SELF, "captureCursor", "()V", false));
                copy.instructions.add(new InsnNode(POP));
                copy.instructions.add(new InsnNode(RETURN));
                continue;
            }
            if (node instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)node;
                if (DATA.equals(call.owner) && call.name.startsWith("read")) {
                    copy.instructions.add(call.clone(labels));
                } else if (Type.getReturnType(call.desc).getDescriptor().equals("Ljava/io/DataInputStream;")) {
                    InsnList replacement = new InsnList();
                    popInvocationOperands(replacement, call);
                    replacement.add(new MethodInsnNode(INVOKESTATIC, SELF, "stream", "()Ljava/io/DataInputStream;", false));
                    copy.instructions.add(replacement);
                } else if (call.name.equals("<init>")) {
                    copy.instructions.add(call.clone(labels));
                } else {
                    InsnList replacement = new InsnList();
                    popInvocationOperands(replacement, call);
                    pushDefault(replacement, Type.getReturnType(call.desc));
                    copy.instructions.add(replacement);
                }
            } else if (node instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)node;
                InsnList replacement = new InsnList();
                Type type = Type.getType(field.desc);
                if (field.getOpcode() == GETSTATIC) pushDefault(replacement, type);
                else if (field.getOpcode() == PUTSTATIC) pop(replacement, type);
                else if (field.getOpcode() == GETFIELD) {
                    replacement.add(new InsnNode(POP));
                    pushDefault(replacement, type);
                } else if (field.getOpcode() == PUTFIELD) {
                    pop(replacement, type);
                    replacement.add(new InsnNode(POP));
                } else throw new IllegalStateException("Unexpected field opcode " + field.getOpcode());
                copy.instructions.add(replacement);
            } else {
                copy.instructions.add(node.clone(labels));
            }
        }
        for (TryCatchBlockNode t : source.tryCatchBlocks)
            copy.tryCatchBlocks.add(new TryCatchBlockNode(label(labels, t.start), label(labels, t.end),
                    label(labels, t.handler), t.type));
        copy.maxLocals = source.maxLocals;
        generated.methods.add(copy);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        generated.accept(writer);
        return writer.toByteArray();
    }

    private static void popInvocationOperands(InsnList out, MethodInsnNode call) {
        Type[] args = Type.getArgumentTypes(call.desc);
        for (int i = args.length - 1; i >= 0; i--) pop(out, args[i]);
        if (call.getOpcode() != INVOKESTATIC) out.add(new InsnNode(POP));
    }

    private static void pop(InsnList out, Type type) {
        out.add(new InsnNode(type.getSize() == 2 ? POP2 : POP));
    }

    private static void pushDefault(InsnList out, Type type) {
        switch (type.getSort()) {
            case Type.VOID: return;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT: out.add(new InsnNode(ICONST_0)); return;
            case Type.FLOAT: out.add(new InsnNode(FCONST_0)); return;
            case Type.LONG: out.add(new InsnNode(LCONST_0)); return;
            case Type.DOUBLE: out.add(new InsnNode(DCONST_0)); return;
            case Type.ARRAY:
            case Type.OBJECT: out.add(new InsnNode(ACONST_NULL)); return;
            default: throw new IllegalArgumentException("Unsupported value type " + type);
        }
    }

    private static LabelNode label(Map<LabelNode,LabelNode> labels, LabelNode source) {
        LabelNode result = labels.get(source);
        if (result == null) { result = new LabelNode(); labels.put(source, result); }
        return result;
    }

    private static URLClassLoader loader(File jar) throws Exception {
        return new URLClassLoader(new URL[] { jar.toURI().toURL() }, ChibiEntryProtocolBinaryTest.class.getClassLoader());
    }

    private static Properties load(File file) throws Exception {
        Properties result = new Properties();
        InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
        try { result.load(reader); } finally { reader.close(); }
        return result;
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        check(value != null && value.length() != 0, "Catalog property missing: " + key);
        return value;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class ProbeLoader extends ClassLoader {
        ProbeLoader(ClassLoader parent) { super(parent); }
        Class<?> define(String name, byte[] bytes) { return defineClass(name, bytes, 0, bytes.length); }
    }

    private static final class ProbeResult {
        final int value;
        final int consumed;
        final int remaining;
        ProbeResult(int value, int consumed, int remaining) {
            this.value = value;
            this.consumed = consumed;
            this.remaining = remaining;
        }
    }
}
