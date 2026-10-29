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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Keeps HSO CHILD auto-enhance sends on one 500 ms cadence, independent of replies. */
public final class HsoChildAutoEnhanceFixedIntervalPatcher implements Opcodes {
    private static final String INPUT_SHA256 =
            "43D1D596C700D266C422856BC147DC51E90118E97F23155DB4B3E09E9696D702";
    private static final String SUPPORT = "HSOSKYAutoEnhanceSupport";
    private static final String SUPPORT_ENTRY = SUPPORT + ".class";

    private static final class EntryData {
        final byte[] bytes;
        final long time;

        EntryData(byte[] bytes, long time) {
            this.bytes = bytes;
            this.time = time;
        }
    }

    private HsoChildAutoEnhanceFixedIntervalPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: HsoChildAutoEnhanceFixedIntervalPatcher <input.jar> <output.jar>");
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
        patchTimerGate(support);
        verifyTimerGate(support);

        ClassNode screen = null;
        for (Map.Entry<String, EntryData> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class") || SUPPORT_ENTRY.equals(entry.getKey())) {
                continue;
            }
            ClassNode candidate = readClass(entry.getValue().bytes);
            if (findMethod(candidate, "hsoskyAutoTickGate", "()V") != null) {
                require(screen == null, "Multiple auto-enhance screen classes found");
                screen = candidate;
            }
        }
        require(screen != null, "Auto-enhance screen class was not found");
        verifyScreen(screen);

        entries.put(SUPPORT_ENTRY, new EntryData(writeClass(support), supportEntry.time));

        File parent = output.getCanonicalFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create output directory: " + parent);
        }
        writeZip(output, entries);
        verifyZipContents(output, entries, SUPPORT_ENTRY);
        System.out.println("input.sha256=" + INPUT_SHA256);
        System.out.println("output.sha256=" + sha256(output));
        System.out.println("patched.class=" + SUPPORT_ENTRY);
        System.out.println("cadence.ms=500");
        System.out.println("response.continuation=timed-update-only");
    }

    private static void patchTimerGate(ClassNode node) {
        require(SUPPORT.equals(node.name), "Unexpected support class name");
        MethodNode ready = findMethod(node, "isResultReady", "()Z");
        MethodNode settled = findMethod(node, "isResultSettled", "()Z");
        require(ready != null && settled != null, "Expected timer methods are missing");
        replaceBody(ready, makeTimerGateBody());
        replaceBody(settled, makeTimerGateBody());
    }

    private static InsnList makeTimerGateBody() {
        InsnList code = new InsnList();
        LabelNode no = new LabelNode();
        LabelNode done = new LabelNode();
        code.add(new FieldInsnNode(GETSTATIC, SUPPORT, "active", "Z"));
        code.add(new JumpInsnNode(IFEQ, no));
        code.add(new FieldInsnNode(GETSTATIC, SUPPORT, "waiting", "Z"));
        code.add(new JumpInsnNode(IFEQ, no));
        code.add(new MethodInsnNode(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
        code.add(new FieldInsnNode(GETSTATIC, SUPPORT, "requestStartedAt", "J"));
        code.add(new InsnNode(LSUB));
        code.add(new FieldInsnNode(GETSTATIC, SUPPORT, "settleDelayMs", "J"));
        code.add(new InsnNode(LCMP));
        code.add(new JumpInsnNode(IFLT, no));
        code.add(new InsnNode(ICONST_1));
        code.add(new JumpInsnNode(GOTO, done));
        code.add(no);
        code.add(new InsnNode(ICONST_0));
        code.add(done);
        code.add(new InsnNode(IRETURN));
        return code;
    }

    private static void verifyTimerGate(ClassNode node) {
        MethodNode ready = findMethod(node, "isResultReady", "()Z");
        MethodNode settled = findMethod(node, "isResultSettled", "()Z");
        require(timerBody(ready) && timerBody(settled),
                "Response state is still able to bypass the 500 ms request timer");
        require(countFieldReads(ready, "resultReady", "Z") == 0
                        && countFieldReads(settled, "resultReadyAt", "J") == 0,
                "Timer decision still depends on a server response");
    }

    private static boolean timerBody(MethodNode method) {
        return countFieldReads(method, "active", "Z") == 1
                && countFieldReads(method, "waiting", "Z") == 1
                && countFieldReads(method, "requestStartedAt", "J") == 1
                && countFieldReads(method, "settleDelayMs", "J") == 1
                && countCalls(method, "java/lang/System", "currentTimeMillis", "()J") == 1;
    }

    private static void verifyScreen(ClassNode screen) {
        MethodNode tick = findMethod(screen, "hsoskyAutoTickGate", "()V");
        require(tick != null && countCalls(tick, SUPPORT, "isResultReady", "()Z") == 1,
                "Timed update path is missing");
        require(countCalls(tick, screen.name, "hsoskyAutoOnResponse", "()V") == 1,
                "Auto continuation is not owned by the timed update path");
        int continuationCalls = 0;
        for (MethodNode method : screen.methods) {
            int calls = countCalls(method, screen.name, "hsoskyAutoOnResponse", "()V");
            if (calls > 0) {
                require(method == tick, "Auto continuation has a non-timed call path: " + method.name);
                continuationCalls += calls;
            }
        }
        require(continuationCalls == 1, "Expected one timed auto-continuation call");
    }

    private static void replaceBody(MethodNode method, InsnList replacement) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        method.instructions.add(replacement);
    }

    private static int countFieldReads(MethodNode method, String name, String descriptor) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (field.getOpcode() == GETSTATIC && SUPPORT.equals(field.owner)
                        && name.equals(field.name) && descriptor.equals(field.desc)) count++;
            }
        }
        return count;
    }

    private static int countCalls(MethodNode method, String owner, String name, String descriptor) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (owner.equals(call.owner) && name.equals(call.name) && descriptor.equals(call.desc)) count++;
            }
        }
        return count;
    }

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && (descriptor == null || descriptor.equals(method.desc))) return method;
        }
        return null;
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    private static byte[] writeClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
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
                if (item.getValue().time >= 0L) entry.setTime(item.getValue().time);
                zip.putNextEntry(entry);
                zip.write(item.getValue().bytes);
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
    }

    private static void verifyZipContents(File output, LinkedHashMap<String, EntryData> expected,
            String supportEntry) throws IOException {
        LinkedHashMap<String, EntryData> actual = readZip(output);
        require(actual.size() == expected.size(), "JAR entry count changed");
        for (Map.Entry<String, EntryData> entry : expected.entrySet()) {
            EntryData found = actual.get(entry.getKey());
            require(found != null, "JAR entry disappeared: " + entry.getKey());
            if (!supportEntry.equals(entry.getKey())) {
                require(java.util.Arrays.equals(entry.getValue().bytes, found.bytes),
                        "Unexpected JAR content change: " + entry.getKey());
            }
        }
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        } finally {
            input.close();
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02X", Integer.valueOf(value & 0xFF)));
        return result.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
