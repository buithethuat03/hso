import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class HSOSKYServerPatcher {
    private static final String INPUT = "HSOSKY_10SKILL_FASTCAST.jar";
    private static final String OUTPUT = "HSOSKY_10SKILL_FASTCAST_27.0.12.133_19129.jar";
    private static final String STAGING = OUTPUT + ".building";
    private static final String NEW_HOST = "27.0.12.133";
    private static final int NEW_PORT = 19129;

    private static final Set<String> OLD_HOSTS = new HashSet<String>(Arrays.asList(
            "sv.hsosky.com", "46.137.254.172", "54.254.156.202"
    ));
    private static final Set<String> changedEntries = new HashSet<String>();
    private static int hostPatches;
    private static int portPatches;

    private HSOSKYServerPatcher() {
    }

    public static void main(String[] args) throws Exception {
        File input = new File(INPUT);
        File output = new File(OUTPUT);
        File staging = new File(STAGING);
        require(input.isFile(), "Missing source JAR: " + INPUT);
        require(!output.exists(), "Refusing to overwrite existing output: " + OUTPUT);

        ZipFile source = new ZipFile(input);
        ZipOutputStream target = new ZipOutputStream(new FileOutputStream(staging));
        try {
            Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] original = readAll(source.getInputStream(entry));
                byte[] result = original;
                if (entry.getName().endsWith(".class")) {
                    result = patchClass(original, entry.getName());
                }

                ZipEntry newEntry = new ZipEntry(entry.getName());
                if (entry.getTime() >= 0L) newEntry.setTime(entry.getTime());
                target.putNextEntry(newEntry);
                target.write(result);
                target.closeEntry();
            }
        } finally {
            try {
                target.close();
            } finally {
                source.close();
            }
        }

        require(hostPatches == 10, "Expected 10 server host loads to patch, got " + hostPatches);
        require(portPatches == 3, "Expected three 19130 ports to patch, got " + portPatches);
        require(changedEntries.size() == 2, "Expected exactly two changed classes, got " + changedEntries.size());

        verify(input, staging);
        Files.move(staging.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE);
        verify(input, output);
        System.out.println("[+] Built new JAR without overwriting source: " + OUTPUT);
        System.out.println("[+] Endpoint forced to " + NEW_HOST + ":" + NEW_PORT);
    }

    private static byte[] patchClass(byte[] original, String entryName) {
        ClassReader reader = new ClassReader(original);
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        reader.accept(owner, 0);
        boolean changed = false;
        boolean serverConfigClass = false;

        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String) {
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    String value = (String) ldc.cst;
                    if ("sv.hsosky.com".equals(value)) serverConfigClass = true;
                    if (OLD_HOSTS.contains(value)) {
                        ldc.cst = NEW_HOST;
                        hostPatches++;
                        changed = true;
                    }
                }
            }
        }

        if (serverConfigClass) {
            for (MethodNode method : owner.methods) {
                if (!"<clinit>".equals(method.name)) continue;
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof IntInsnNode && insn.getOpcode() == Opcodes.SIPUSH
                            && ((IntInsnNode) insn).operand == 19130) {
                        ((IntInsnNode) insn).operand = NEW_PORT;
                        portPatches++;
                        changed = true;
                    }
                }
            }
        }

        if (!changed) return original;
        changedEntries.add(entryName);
        ClassWriter writer = new ClassWriter(reader, 0);
        owner.accept(writer);
        System.out.println("[+] Patched endpoint class: " + entryName);
        return writer.toByteArray();
    }

    private static void verify(File input, File output) throws Exception {
        JarFile executable = new JarFile(output);
        try {
            require(executable.getManifest() != null, "Output manifest cannot be read");
        } finally {
            executable.close();
        }

        ZipFile source = new ZipFile(input);
        ZipFile built = new ZipFile(output);
        int entriesRead = 0;
        int classesRead = 0;
        try {
            Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry sourceEntry = entries.nextElement();
                ZipEntry builtEntry = built.getEntry(sourceEntry.getName());
                require(builtEntry != null, "Output lost entry: " + sourceEntry.getName());
                byte[] sourceBytes = readAll(source.getInputStream(sourceEntry));
                byte[] builtBytes = readAll(built.getInputStream(builtEntry));
                if (!changedEntries.contains(sourceEntry.getName())) {
                    require(Arrays.equals(sourceBytes, builtBytes),
                            "Unexpected content change in: " + sourceEntry.getName());
                }
                if (sourceEntry.getName().endsWith(".class")) {
                    new ClassReader(builtBytes);
                    classesRead++;
                }
                entriesRead++;
            }
            require(built.size() == source.size(), "Output entry count differs from source");
        } finally {
            try {
                built.close();
            } finally {
                source.close();
            }
        }

        verifyEndpointConstants(output);
        System.out.println("[+] Verify OK: " + entriesRead + " entries, " + classesRead
                + " classes; unchanged entries are byte-identical");
    }

    private static void verifyEndpointConstants(File output) throws IOException {
        ZipFile jar = new ZipFile(output);
        int newHostLoads = 0;
        int oldHostLoads = 0;
        int ports19129 = 0;
        int ports19130 = 0;
        try {
            Enumeration<? extends ZipEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                byte[] bytes = readAll(jar.getInputStream(entry));
                ClassNode owner = new ClassNode(Opcodes.ASM9);
                new ClassReader(bytes).accept(owner, 0);
                boolean endpointClass = false;
                for (MethodNode method : owner.methods) {
                    for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String) {
                            String value = (String) ((LdcInsnNode) insn).cst;
                            if (NEW_HOST.equals(value)) {
                                newHostLoads++;
                                endpointClass = true;
                            }
                            if (OLD_HOSTS.contains(value)) oldHostLoads++;
                        }
                    }
                }
                if (endpointClass) {
                    for (MethodNode method : owner.methods) {
                        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                            if (insn instanceof IntInsnNode && insn.getOpcode() == Opcodes.SIPUSH) {
                                int value = ((IntInsnNode) insn).operand;
                                if (value == 19129) ports19129++;
                                if (value == 19130) ports19130++;
                            }
                        }
                    }
                }
            }
        } finally {
            jar.close();
        }
        require(newHostLoads == 10, "Expected 10 new endpoint host loads, got " + newHostLoads);
        require(oldHostLoads == 0, "Old endpoint hosts remain: " + oldHostLoads);
        require(ports19129 == 8, "Expected eight 19129 server ports, got " + ports19129);
        require(ports19130 == 0, "Port 19130 still remains in endpoint configuration");
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
