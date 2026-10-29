import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Patches HSO CHILD auto-enhance to retry on a 500 ms timer, with or without a reply. */
public final class HsoChildAutoEnhanceCadencePatcher implements Opcodes {
    private static final String INPUT_SHA256 =
            "947304FFC5136D4C1B0D51B4BEBBEB64FAA491343C4A13FC4B99710C2E56A46D";
    private static final String SUPPORT = "HSOSKYAutoEnhanceSupport";
    private static final String SUPPORT_ENTRY = SUPPORT + ".class";
    private static final long CADENCE_MS = 500L;

    private static final class EntryData {
        final byte[] bytes;
        final long time;

        EntryData(byte[] bytes, long time) {
            this.bytes = bytes;
            this.time = time;
        }
    }

    private HsoChildAutoEnhanceCadencePatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: HsoChildAutoEnhanceCadencePatcher <input.jar> <output.jar>");
        }

        File input = new File(args[0]);
        File output = new File(args[1]);
        require(input.isFile(), "Input JAR does not exist: " + input);
        require(!input.getCanonicalFile().equals(output.getCanonicalFile()),
                "Output must use a new filename");
        require(INPUT_SHA256.equals(sha256(input)), "Unexpected input JAR SHA-256");

        LinkedHashMap<String, EntryData> entries = readZip(input);
        EntryData supportEntry = entries.get(SUPPORT_ENTRY);
        require(supportEntry != null, "Auto-enhance support class is missing");

        ClassNode support = readClass(supportEntry.bytes);
        require(SUPPORT.equals(support.name), "Unexpected support class name");
        patchSupport(support);
        verifySupport(support);

        int screenCount = 0;
        for (Map.Entry<String, EntryData> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class") || SUPPORT_ENTRY.equals(entry.getKey())) {
                continue;
            }
            ClassNode node = readClass(entry.getValue().bytes);
            MethodNode tickGate = findMethod(node, "hsoskyAutoTickGate", "()V");
            if (tickGate == null) {
                continue;
            }
            screenCount++;
            require(countCalls(tickGate, SUPPORT, "isResultReady", "()Z") == 1,
                    "Auto-enhance tick gate does not use isResultReady exactly once");
            require(countCalls(tickGate, SUPPORT, "isResultSettled", "()Z") == 2,
                    "Auto-enhance tick gate has unexpected result-settle flow");
            require(countCalls(tickGate, SUPPORT, "isRequestStalled", "()Z") == 1,
                    "Auto-enhance unanswered-request stop guard is missing");
        }
        require(screenCount == 1, "Expected exactly one patched auto-enhance screen");

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        support.accept(writer);
        entries.put(SUPPORT_ENTRY, new EntryData(writer.toByteArray(), supportEntry.time));

        File parent = output.getCanonicalFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create output directory: " + parent);
        }
        writeZip(output, entries);
        verifyZipContents(output, entries);
        System.out.println("input.sha256=" + INPUT_SHA256);
        System.out.println("output.sha256=" + sha256(output));
        System.out.println("patched.class=" + SUPPORT_ENTRY);
        System.out.println("auto.enhance.screen.count=" + screenCount);
        System.out.println("cadence.ms=" + CADENCE_MS);
    }

    private static void patchSupport(ClassNode node) {
        MethodNode setter = findMethod(node, "setSettleDelayMs", "(J)V");
        MethodNode initializer = findMethod(node, "<clinit>", "()V");
        MethodNode resultReady = findMethod(node, "isResultReady", "()Z");
        MethodNode resultSettled = findMethod(node, "isResultSettled", "()Z");
        require(setter != null && initializer != null && resultReady != null && resultSettled != null,
                "Expected auto-enhance timer methods are missing");

        InsnList setDelay = new InsnList();
        setDelay.add(new LdcInsnNode(Long.valueOf(CADENCE_MS)));
        setDelay.add(new FieldInsnNode(PUTSTATIC, SUPPORT, "settleDelayMs", "J"));
        setDelay.add(new InsnNode(RETURN));
        replaceBody(setter, setDelay);

        InsnList initializeDelay = new InsnList();
        initializeDelay.add(new LdcInsnNode(Long.valueOf(CADENCE_MS)));
        initializeDelay.add(new FieldInsnNode(PUTSTATIC, SUPPORT, "settleDelayMs", "J"));
        initializeDelay.add(new InsnNode(RETURN));
        replaceBody(initializer, initializeDelay);

        replaceBody(resultReady, makeReadyBody());
        replaceBody(resultSettled, makeSettledBody());
    }

    private static InsnList makeReadyBody() {
        InsnList code = new InsnList();
        LabelNode no = new LabelNode();
        LabelNode ready = new LabelNode();
        LabelNode done = new LabelNode();

        code.add(new FieldInsnNode(GETSTATIC, SUPPORT, "active", "Z"));
        code.add(new JumpInsnNode(IFEQ, no));
        code.add(new FieldInsnNode(GETSTATIC, SUPPORT, "waiting", "Z"));
        code.add(new JumpInsnNode(IFEQ, no));
        code.add(new FieldInsnNode(GETSTATIC, SUPPORT, "resultReady", "Z"));
        code.add(new JumpInsnNode(IFNE, ready));
        addElapsedAtLeast(code, "requestStartedAt", "settleDelayMs", no);
        code.add(ready);
        code.add(new InsnNode(ICONST_1));
        code.add(new JumpInsnNode(GOTO, done));
        code.add(no);
        code.add(new InsnNode(ICONST_0));
        code.add(done);
        code.add(new InsnNode(IRETURN));
        return code;
    }

    private static InsnList makeSettledBody() {
        InsnList code = new InsnList();
        LabelNode no = new LabelNode();
        LabelNode unanswered = new LabelNode();
        LabelNode settled = new LabelNode();
        LabelNode done = new LabelNode();

        code.add(new FieldInsnNode(GETSTATIC, SUPPORT, "active", "Z"));
        code.add(new JumpInsnNode(IFEQ, no));
        code.add(new FieldInsnNode(GETSTATIC, SUPPORT, "waiting", "Z"));
        code.add(new JumpInsnNode(IFEQ, no));
        code.add(new FieldInsnNode(GETSTATIC, SUPPORT, "resultReady", "Z"));
        code.add(new JumpInsnNode(IFEQ, unanswered));
        addElapsedAtLeast(code, "resultReadyAt", "settleDelayMs", no);
        code.add(new JumpInsnNode(GOTO, settled));

        code.add(unanswered);
        addElapsedAtLeast(code, "requestStartedAt", "settleDelayMs", no);
        code.add(settled);
        code.add(new InsnNode(ICONST_1));
        code.add(new JumpInsnNode(GOTO, done));
        code.add(no);
        code.add(new InsnNode(ICONST_0));
        code.add(done);
        code.add(new InsnNode(IRETURN));
        return code;
    }

    /** Pushes currentTimeMillis() - startField >= delayField; branches to notEnough if false. */
    private static void addElapsedAtLeast(InsnList code, String startField, String delayField,
            LabelNode notEnough) {
        code.add(new MethodInsnNode(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
        code.add(new FieldInsnNode(GETSTATIC, SUPPORT, startField, "J"));
        code.add(new InsnNode(LSUB));
        code.add(new FieldInsnNode(GETSTATIC, SUPPORT, delayField, "J"));
        code.add(new InsnNode(LCMP));
        code.add(new JumpInsnNode(IFLT, notEnough));
    }

    private static void verifySupport(ClassNode node) {
        MethodNode setter = findMethod(node, "setSettleDelayMs", "(J)V");
        MethodNode initializer = findMethod(node, "<clinit>", "()V");
        MethodNode ready = findMethod(node, "isResultReady", "()Z");
        MethodNode settled = findMethod(node, "isResultSettled", "()Z");
        require(containsLong(setter, CADENCE_MS) && containsLong(initializer, CADENCE_MS),
                "Auto-enhance delay is not fixed at 500 ms");
        require(countCalls(ready, "java/lang/System", "currentTimeMillis", "()J") == 1,
                "A missing server response does not open the 500 ms retry window");
        require(countCalls(settled, "java/lang/System", "currentTimeMillis", "()J") == 2,
                "Success and no-response settle paths are not both timed");
        require(countFieldReads(settled, SUPPORT, "requestStartedAt", "J") == 1,
                "No-response settle path is missing its request timer");
        require(countFieldReads(settled, SUPPORT, "resultReadyAt", "J") == 1,
                "Server-response settle path is missing its response timer");
    }

    private static boolean containsLong(MethodNode method, long value) {
        for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getFirst();
                insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode && Long.valueOf(value).equals(((LdcInsnNode) insn).cst)) {
                return true;
            }
        }
        return false;
    }

    private static void replaceBody(MethodNode method, InsnList replacement) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        method.instructions.add(replacement);
    }

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static int countCalls(MethodNode method, String owner, String name, String descriptor) {
        int count = 0;
        for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getFirst();
                insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (owner.equals(call.owner) && name.equals(call.name) && descriptor.equals(call.desc)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countFieldReads(MethodNode method, String owner, String name, String descriptor) {
        int count = 0;
        for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getFirst();
                insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (GETSTATIC == field.getOpcode() && owner.equals(field.owner)
                        && name.equals(field.name) && descriptor.equals(field.desc)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    private static LinkedHashMap<String, EntryData> readZip(File input) throws IOException {
        LinkedHashMap<String, EntryData> entries = new LinkedHashMap<String, EntryData>();
        ZipFile zip = new ZipFile(input);
        try {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                InputStream stream = zip.getInputStream(entry);
                try {
                    entries.put(entry.getName(), new EntryData(readAll(stream), entry.getTime()));
                } finally {
                    stream.close();
                }
            }
        } finally {
            zip.close();
        }
        return entries;
    }

    private static void writeZip(File output, LinkedHashMap<String, EntryData> entries) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String, EntryData> item : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(item.getKey());
                if (item.getValue().time >= 0L) {
                    entry.setTime(item.getValue().time);
                }
                zip.putNextEntry(entry);
                zip.write(item.getValue().bytes);
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
    }

    private static void verifyZipContents(File output, LinkedHashMap<String, EntryData> expected)
            throws IOException {
        LinkedHashMap<String, EntryData> actual = readZip(output);
        require(actual.size() == expected.size(), "JAR entry count changed");
        for (Map.Entry<String, EntryData> entry : expected.entrySet()) {
            EntryData found = actual.get(entry.getKey());
            require(found != null, "JAR entry disappeared: " + entry.getKey());
            if (!SUPPORT_ENTRY.equals(entry.getKey())) {
                require(java.util.Arrays.equals(entry.getValue().bytes, found.bytes),
                        "Unexpected JAR content change: " + entry.getKey());
            }
        }
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        } finally {
            input.close();
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format("%02X", Integer.valueOf(value & 0xFF)));
        }
        return result.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
