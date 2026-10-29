package luna.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Applies the Tuoi Tho login key at the HSOSKY shared opcode-1 serializer. */
public final class HsoSkyLoginKeyPatcher implements Opcodes {
    private static final String LOGIN_DESC =
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
            + "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IBZ)V";
    private static final String WRITE_UTF_DESC = "(Ljava/lang/String;)V";
    private static final int EXPECTED_WRITE_UTF_CALLS = 7;
    private static final String LOGIN_KEY = "TUOITHO_SECRET_KEY_2026";

    private HsoSkyLoginKeyPatcher() {
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
                    "Usage: HsoSkyLoginKeyPatcher <input.jar> <output.jar>");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        require(input.isFile(), "Input JAR does not exist: " + input);
        require(!input.getCanonicalFile().equals(output.getCanonicalFile()),
                "Output must differ from input");

        LinkedHashMap<String, EntryData> entries = readZip(input);
        SerializerMatch match = findSerializer(entries);
        patchSerializer(match.method);
        verifySerializer(match.method);

        ClassWriter writer = new ClassWriter(0);
        match.owner.accept(writer);
        EntryData original = entries.get(match.entryName);
        entries.put(match.entryName, new EntryData(writer.toByteArray(), original.time));
        writeZip(output, entries);

        LinkedHashMap<String, EntryData> written = readZip(output);
        require(written.size() == entries.size(), "Output entry count changed");
        SerializerMatch verified = findSerializer(written);
        verifySerializer(verified.method);
        System.out.println("Patched serializer: " + match.owner.name + "."
                + match.method.name + match.method.desc);
        System.out.println("Login fields 4-6: " + LOGIN_KEY);
        System.out.println("Entries: " + entries.size());
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
                if ((method.access & ACC_STATIC) != 0 || !LOGIN_DESC.equals(method.desc)) {
                    continue;
                }
                if (countWriteUtf(method) != EXPECTED_WRITE_UTF_CALLS
                        || !startsOpcodeOne(method)) {
                    continue;
                }
                require(found == null, "Multiple shared opcode-1 serializers found");
                found = new SerializerMatch(entry.getKey(), owner, method);
            }
        }
        require(found != null, "Shared opcode-1 serializer not found");
        return found;
    }

    private static boolean startsOpcodeOne(MethodNode method) {
        int inspected = 0;
        boolean sawOne = false;
        for (AbstractInsnNode insn = method.instructions.getFirst();
                insn != null && inspected < 16; insn = insn.getNext()) {
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
        boolean[] stores = new boolean[7];
        for (AbstractInsnNode insn = method.instructions.getFirst();
                insn != null; insn = insn.getNext()) {
            if (!(insn instanceof LdcInsnNode)
                    || !LOGIN_KEY.equals(((LdcInsnNode) insn).cst)) {
                continue;
            }
            keyLoads++;
            AbstractInsnNode next = nextReal(insn.getNext());
            require(next instanceof VarInsnNode && next.getOpcode() == ASTORE,
                    "Login key load is not followed by ASTORE");
            int local = ((VarInsnNode) next).var;
            require(local >= 4 && local <= 6,
                    "Login key stored in unexpected local " + local);
            stores[local] = true;
        }
        require(keyLoads == 3 && stores[4] && stores[5] && stores[6],
                "Login fields 4-6 were not all overridden");
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        while (insn != null && insn.getOpcode() < 0) {
            insn = insn.getNext();
        }
        return insn;
    }

    private static LinkedHashMap<String, EntryData> readZip(File file) throws IOException {
        LinkedHashMap<String, EntryData> entries = new LinkedHashMap<String, EntryData>();
        ZipFile zip = new ZipFile(file);
        try {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                InputStream input = zip.getInputStream(entry);
                try {
                    entries.put(entry.getName(), new EntryData(readAll(input), entry.getTime()));
                } finally {
                    input.close();
                }
            }
        } finally {
            zip.close();
        }
        return entries;
    }

    private static void writeZip(File file, Map<String, EntryData> entries) throws IOException {
        ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file));
        try {
            for (Map.Entry<String, EntryData> item : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(item.getKey());
                if (item.getValue().time >= 0L) {
                    entry.setTime(item.getValue().time);
                }
                output.putNextEntry(entry);
                output.write(item.getValue().bytes);
                output.closeEntry();
            }
        } finally {
            output.close();
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
