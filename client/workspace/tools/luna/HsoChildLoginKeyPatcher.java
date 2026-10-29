import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Applies the current Tuoi Tho login-key contract to the verified HSO CHILD
 * release. The patch is deliberately structural: it finds the sole opcode-1
 * serializer by descriptor and its eight DataOutputStream.writeUTF calls
 * (six header strings plus two later device/runtime strings), then overrides
 * arguments 4-6 at the serializer boundary. This covers native,
 * automatic, and replayed login requests without relying on obfuscated names.
 */
public final class HsoChildLoginKeyPatcher implements Opcodes {
    private static final String EXPECTED_INPUT_SHA256 =
            "D7FC5548F9D0ED9A09ACA443D218857616BAAE8EBD3AF30BAD185FE399D26767";
    private static final String LOGIN_DESC =
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
            + "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IB)V";
    private static final String WRITE_UTF_DESC = "(Ljava/lang/String;)V";
    private static final int EXPECTED_WRITE_UTF_CALLS = 8;
    private static final String LOGIN_KEY = "TUOITHO_SECRET_KEY_2026";
    private static final String LEGACY_SUFFIX = "|HSOR_SECRET_2026";

    private HsoChildLoginKeyPatcher() {
    }

    private static final class EntryData {
        final byte[] bytes;
        final long time;

        EntryData(byte[] bytes, long time) {
            this.bytes = bytes;
            this.time = time;
        }
    }

    private static final class SerializerMatch {
        final String entryName;
        final ClassNode owner;
        final MethodNode method;

        SerializerMatch(String entryName, ClassNode owner, MethodNode method) {
            this.entryName = entryName;
            this.owner = owner;
            this.method = method;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: HsoChildLoginKeyPatcher <input.jar> <output.jar>");
        }

        File input = new File(args[0]);
        File output = new File(args[1]);
        require(input.isFile(), "Input JAR does not exist: " + input);
        require(!input.getCanonicalFile().equals(output.getCanonicalFile()),
                "Output must use a new filename");

        String inputHash = sha256(input);
        require(EXPECTED_INPUT_SHA256.equals(inputHash),
                "Unexpected HSO CHILD input SHA-256: " + inputHash);

        LinkedHashMap<String, EntryData> entries = readZip(input);
        SerializerMatch match = findSerializer(entries);
        patchSerializer(match.method);
        verifySerializer(match.method);

        ClassWriter writer = new ClassWriter(0);
        match.owner.accept(writer);
        EntryData original = entries.get(match.entryName);
        entries.put(match.entryName, new EntryData(writer.toByteArray(), original.time));

        File parent = output.getCanonicalFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create output directory: " + parent);
        }
        writeZip(output, entries);

        LinkedHashMap<String, EntryData> written = readZip(output);
        require(written.size() == entries.size(), "Output entry count changed");
        SerializerMatch verified = findSerializer(written);
        verifySerializer(verified.method);

        System.out.println("Patched serializer: " + match.owner.name + "." + match.method.name
                + match.method.desc);
        System.out.println("Login fields 4-6: " + LOGIN_KEY);
        System.out.println("Removed legacy suffix: " + LEGACY_SUFFIX);
        System.out.println("Entries: " + entries.size());
        System.out.println("Output SHA-256: " + sha256(output));
    }

    private static SerializerMatch findSerializer(Map<String, EntryData> entries) {
        SerializerMatch found = null;
        for (Map.Entry<String, EntryData> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode owner = new ClassNode();
            new ClassReader(entry.getValue().bytes).accept(owner, 0);
            for (MethodNode method : owner.methods) {
                if (!LOGIN_DESC.equals(method.desc) || (method.access & ACC_STATIC) != 0) {
                    continue;
                }
                if (countWriteUtf(method) != EXPECTED_WRITE_UTF_CALLS
                        || !startsOpcodeOne(method)) {
                    continue;
                }
                require(found == null, "Multiple opcode-1 login serializers found");
                found = new SerializerMatch(entry.getKey(), owner, method);
            }
        }
        require(found != null, "Opcode-1 login serializer not found");
        return found;
    }

    private static boolean startsOpcodeOne(MethodNode method) {
        int inspected = 0;
        boolean sawOne = false;
        for (AbstractInsnNode insn = method.instructions.getFirst();
                insn != null && inspected < 12; insn = insn.getNext()) {
            if (insn.getOpcode() < 0) {
                continue;
            }
            inspected++;
            if (insn.getOpcode() == ICONST_1) {
                sawOne = true;
            } else if (sawOne && insn instanceof MethodInsnNode
                    && "(B)V".equals(((MethodInsnNode) insn).desc)) {
                return true;
            }
        }
        return false;
    }

    private static int countWriteUtf(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst();
                insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) insn;
            if (call.getOpcode() == INVOKEVIRTUAL
                    && "java/io/DataOutputStream".equals(call.owner)
                    && "writeUTF".equals(call.name)
                    && WRITE_UTF_DESC.equals(call.desc)) {
                count++;
            }
        }
        return count;
    }

    private static void patchSerializer(MethodNode method) {
        int legacySuffixes = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst();
                insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode
                    && LEGACY_SUFFIX.equals(((LdcInsnNode) insn).cst)) {
                ((LdcInsnNode) insn).cst = "";
                legacySuffixes++;
            }
        }
        require(legacySuffixes == 1,
                "Expected one legacy login suffix, found " + legacySuffixes);

        InsnList override = new InsnList();
        for (int local = 4; local <= 6; local++) {
            override.add(new LdcInsnNode(LOGIN_KEY));
            override.add(new VarInsnNode(ASTORE, local));
        }
        method.instructions.insertBefore(method.instructions.getFirst(), override);
    }

    private static void verifySerializer(MethodNode method) {
        require(countWriteUtf(method) == EXPECTED_WRITE_UTF_CALLS,
                "Login serializer writeUTF shape changed");
        int keyLoads = 0;
        int legacySuffixes = 0;
        boolean[] stores = new boolean[7];
        for (AbstractInsnNode insn = method.instructions.getFirst();
                insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode) {
                Object value = ((LdcInsnNode) insn).cst;
                if (LOGIN_KEY.equals(value)) {
                    keyLoads++;
                    AbstractInsnNode next = nextReal(insn.getNext());
                    require(next instanceof VarInsnNode && next.getOpcode() == ASTORE,
                            "Login key load is not followed by ASTORE");
                    int local = ((VarInsnNode) next).var;
                    require(local >= 4 && local <= 6,
                            "Login key stored in unexpected local " + local);
                    stores[local] = true;
                } else if (LEGACY_SUFFIX.equals(value)) {
                    legacySuffixes++;
                }
            }
        }
        require(keyLoads == 3 && stores[4] && stores[5] && stores[6],
                "Login fields 4-6 were not all overridden");
        require(legacySuffixes == 0, "Legacy login suffix remains");
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        while (insn != null && insn.getOpcode() < 0) {
            insn = insn.getNext();
        }
        return insn;
    }

    private static LinkedHashMap<String, EntryData> readZip(File file) throws IOException {
        LinkedHashMap<String, EntryData> entries = new LinkedHashMap<String, EntryData>();
        ZipInputStream in = new ZipInputStream(new FileInputStream(file));
        try {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                require(!entries.containsKey(entry.getName()),
                        "Duplicate ZIP entry: " + entry.getName());
                entries.put(entry.getName(), new EntryData(readAll(in), entry.getTime()));
                in.closeEntry();
            }
        } finally {
            in.close();
        }
        return entries;
    }

    private static void writeZip(File file, Map<String, EntryData> entries) throws IOException {
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file));
        try {
            for (Map.Entry<String, EntryData> item : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(item.getKey());
                if (item.getValue().time >= 0L) {
                    entry.setTime(item.getValue().time);
                }
                out.putNextEntry(entry);
                out.write(item.getValue().bytes);
                out.closeEntry();
            }
        } finally {
            out.close();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } finally {
            in.close();
        }
        byte[] hash = digest.digest();
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (int i = 0; i < hash.length; i++) {
            result.append(String.format("%02X", Integer.valueOf(hash[i] & 0xff)));
        }
        return result.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
