import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Changes only the native auto gate initializer; the request interval is untouched. */
public final class HungBySonAutoGatePatcher implements Opcodes {
    private static final String INPUT_SHA =
        "D917DA2442C8AED8A67F4D30BD5EC2F462B3883D0319827ACA684EAF05204005";

    public static void main(String[] args) throws Exception {
        require(args.length == 4 || args.length == 7,
            "input.jar output.jar combat.properties output.properties [expected-input-sha256 before-ms after-ms]");
        File input = new File(args[0]), output = new File(args[1]), catalog = new File(args[3]);
        String expectedInput = args.length == 7 ? args[4] : INPUT_SHA;
        int beforeMs = args.length == 7 ? Integer.parseInt(args[5]) : 400;
        int afterMs = args.length == 7 ? Integer.parseInt(args[6]) : 500;
        require(beforeMs > 0 && afterMs > 0 && afterMs <= Short.MAX_VALUE && beforeMs != afterMs, "Invalid gate values");
        require(expectedInput.matches("[0-9A-F]{64}") && expectedInput.equals(hash(input)), "Unexpected current release hash");
        require(!output.exists() && !catalog.exists(), "Outputs must be new");
        Properties props = new Properties();
        InputStream source = new FileInputStream(args[2]);
        try { props.load(source); } finally { source.close(); }
        String owner = props.getProperty("auto.delay.owner");
        String field = props.getProperty("auto.delay.field");
        require(owner != null && field != null, "Missing exact auto symbols");
        String changed = owner + ".class";
        JarFile jar = new JarFile(input);
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        LinkedHashMap<String, Long> times = new LinkedHashMap<String, Long>();
        try {
            for (Enumeration<JarEntry> all = jar.entries(); all.hasMoreElements();) {
                JarEntry entry = all.nextElement();
                require(!entries.containsKey(entry.getName()), "Duplicate ZIP entry");
                InputStream stream = jar.getInputStream(entry);
                try { entries.put(entry.getName(), read(stream)); } finally { stream.close(); }
                times.put(entry.getName(), Long.valueOf(entry.getTime()));
            }
        } finally { jar.close(); }
        byte[] original = entries.get(changed);
        require(original != null, "Missing cast class");
        ClassNode node = parse(original);
        IntInsnNode value = initializer(node, owner, field);
        require(value.getOpcode() == SIPUSH && value.operand == beforeMs, "Expected exact " + beforeMs + "ms initializer");
        value.operand = afterMs;
        byte[] modified = bytes(node);
        ClassNode normalized = parse(modified);
        IntInsnNode restored = initializer(normalized, owner, field);
        require(restored.operand == afterMs, "Saved auto gate differs");
        restored.operand = beforeMs;
        require(Arrays.equals(bytes(parse(original)), bytes(normalized)),
            "Change exceeds one initializer constant");
        require(node.version == parse(modified).version, "Class version changed");
        entries.put(changed, modified);
        Files.createDirectories(output.getAbsoluteFile().getParentFile().toPath());
        JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                JarEntry zip = new JarEntry(entry.getKey());
                if (times.get(entry.getKey()).longValue() >= 0) zip.setTime(times.get(entry.getKey()).longValue());
                out.putNextEntry(zip); out.write(entry.getValue()); out.closeEntry();
            }
        } finally { out.close(); }
        JarFile saved = new JarFile(output);
        try {
            require(saved.size() == entries.size(), "Archive entry count changed");
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                InputStream stream = saved.getInputStream(saved.getJarEntry(entry.getKey()));
                try { require(Arrays.equals(entry.getValue(), read(stream)), "Saved entry differs"); }
                finally { stream.close(); }
            }
        } finally { saved.close(); }
        props.setProperty("input.sha256", expectedInput);
        props.setProperty("output.sha256", hash(output));
        props.setProperty("auto.delay.before.ms", String.valueOf(beforeMs));
        props.setProperty("auto.delay.after.ms", String.valueOf(afterMs));
        props.setProperty("timing.mode", "auto-gate-adjusted");
        require(props.getProperty("request.extra.ms") != null, "Missing request interval metadata");
        props.setProperty("changed.classes", owner);
        OutputStream config = new FileOutputStream(catalog);
        try { props.store(config, "Auto gate adjustment only; native request interval unchanged"); }
        finally { config.close(); }
        System.out.println("AUTO_GATE=" + beforeMs + "->" + afterMs + "ms (strict >)");
        System.out.println("SCOPE=one SIPUSH initializer in one class; inverse canonical check PASS");
        System.out.println("REQUEST_ANIMATION_COOLDOWN_TARGETS_OTHER_ENTRIES=unchanged");
        System.out.println("OUTPUT_SHA256=" + hash(output));
    }

    private static IntInsnNode initializer(ClassNode node, String owner, String field) {
        IntInsnNode result = null;
        for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != PUTSTATIC) continue;
            FieldInsnNode write = (FieldInsnNode)insn;
            if (!owner.equals(write.owner) || !field.equals(write.name)) continue;
            require("<clinit>".equals(method.name) && "I".equals(write.desc) && result == null,
                "Unexpected initializer writer");
            AbstractInsnNode prior = insn.getPrevious();
            while (prior != null && prior.getOpcode() < 0) prior = prior.getPrevious();
            require(prior instanceof IntInsnNode, "Initializer shape differs");
            result = (IntInsnNode)prior;
        }
        require(result != null, "Missing auto initializer");
        return result;
    }
    private static ClassNode parse(byte[] input) {
        ClassNode result = new ClassNode(); new ClassReader(input).accept(result, 0); return result;
    }
    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
    }
    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int size;
        while ((size = input.read(buffer)) != -1) out.write(buffer, 0, size); return out.toByteArray();
    }
    private static String hash(File file) throws Exception {
        byte[] result = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder out = new StringBuilder();
        for (byte value : result) out.append(String.format("%02X", Integer.valueOf(value & 255)));
        return out.toString();
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
