import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HSOSKYRangePatcher implements Opcodes {
    private static final String INPUT_JAR = "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345.jar";
    private static final String OUTPUT_JAR = "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_TEST.jar";
    private static final String SUPPORT_CLASS = "HSOSKYRangeSupport";

    private static final Map<String, byte[]> ENTRIES = new LinkedHashMap<String, byte[]>();
    private static final Map<String, Long> TIMES = new LinkedHashMap<String, Long>();

    private static ClassNode rangeOwner;
    private static MethodNode rangeGetter;
    private static ClassNode attackOwner;
    private static MethodNode attackStart;
    private static String playerClassOwner;
    private static String playerClassField;
    private static int patches;

    private HSOSKYRangePatcher() {
    }

    public static void main(String[] args) throws Exception {
        File output = new File(OUTPUT_JAR);
        require(!output.exists(), "Refusing to overwrite existing output: " + output.getPath());
        readJar(new File(INPUT_JAR));
        discoverRangeGetter();
        discoverAttackStartAndClassField();
        patchAttackRange();
        byte[] support = readAll(new FileInputStream(SUPPORT_CLASS + ".class"));
        validateSupport(support);
        ENTRIES.put(SUPPORT_CLASS + ".class", support);
        require(patches == 1, "Expected exactly one range patch, got " + patches);
        writeJar(output);
        verifyOutput(output);

        System.out.println("[+] Built " + OUTPUT_JAR);
        System.out.println("    melee classes: Warrior (0), Assassin (1)");
        System.out.println("    client test range: minimum 120");
        System.out.println("    ranged classes remain unchanged");
        System.out.println("    patched attack controller: " + shortName(attackOwner.name));
        System.out.println("    mapped player class field: " + shortName(playerClassField));
    }

    private static void readJar(File file) throws IOException {
        require(file.isFile(), "Missing input JAR: " + file.getPath());
        JarFile jar = new JarFile(file);
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    ENTRIES.put(entry.getName(), readAll(jar.getInputStream(entry)));
                    TIMES.put(entry.getName(), Long.valueOf(entry.getTime()));
                }
            }
        } finally {
            jar.close();
        }
    }

    private static void discoverRangeGetter() {
        List<ClassNode> owners = new ArrayList<ClassNode>();
        List<MethodNode> methods = new ArrayList<MethodNode>();
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode owner = readClass(entry.getValue());
            for (MethodNode method : owner.methods) {
                if ((method.access & ACC_STATIC) != 0 && "(I)I".equals(method.desc)
                        && containsInt(method, 50) && containsGetField(method, "S")
                        && callsObjectLookup(method)) {
                    owners.add(owner);
                    methods.add(method);
                }
            }
        }
        require(methods.size() == 1, "Expected one skill-range getter, found " + methods.size());
        rangeOwner = owners.get(0);
        rangeGetter = methods.get(0);
    }

    private static boolean callsObjectLookup(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == INVOKESTATIC && call.desc.startsWith("(I)L")
                        && call.desc.endsWith(";")) return true;
            }
        }
        return false;
    }

    private static void discoverAttackStartAndClassField() {
        List<ClassNode> owners = new ArrayList<ClassNode>();
        List<MethodNode> methods = new ArrayList<MethodNode>();
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode owner = readClass(entry.getValue());
            for (MethodNode method : owner.methods) {
                Type[] args = Type.getArgumentTypes(method.desc);
                if ((method.access & ACC_STATIC) != 0 || Type.getReturnType(method.desc).getSort() != Type.VOID
                        || args.length != 4 || args[0].getSort() != Type.OBJECT || args[1].getSort() != Type.OBJECT
                        || args[2].getSort() != Type.INT || args[3].getSort() != Type.BYTE
                        || !calls(method, rangeOwner.name, rangeGetter.name, "(I)I")) {
                    continue;
                }
                String classField = findPlayerClassField(method, args[0].getInternalName());
                if (classField != null) {
                    owners.add(owner);
                    methods.add(method);
                    playerClassOwner = args[0].getInternalName();
                    playerClassField = classField;
                }
            }
        }
        require(methods.size() == 1, "Expected one player attack-start method, found " + methods.size());
        attackOwner = owners.get(0);
        attackStart = methods.get(0);
    }

    private static String findPlayerClassField(MethodNode method, String playerOwner) {
        String found = null;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == GETFIELD) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (playerOwner.equals(field.owner) && "B".equals(field.desc)
                        && isInt(nextReal(insn), 3)) {
                    if (found != null && !found.equals(field.name)) return null;
                    found = field.name;
                }
            }
        }
        return found;
    }

    private static void patchAttackRange() {
        for (AbstractInsnNode insn = attackStart.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == INVOKESTATIC && rangeOwner.name.equals(call.owner)
                        && rangeGetter.name.equals(call.name) && "(I)I".equals(call.desc)) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(ALOAD, 1));
                    hook.add(new FieldInsnNode(GETFIELD, playerClassOwner, playerClassField, "B"));
                    hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT_CLASS, "adjustRange", "(II)I", false));
                    attackStart.instructions.insert(insn, hook);
                    patches++;
                }
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        attackOwner.accept(writer);
        ENTRIES.put(attackOwner.name + ".class", writer.toByteArray());
    }

    private static void validateSupport(byte[] bytes) {
        ClassNode support = readClass(bytes);
        MethodNode adjust = null;
        for (MethodNode method : support.methods) {
            if ("adjustRange".equals(method.name) && "(II)I".equals(method.desc)
                    && (method.access & ACC_STATIC) != 0) adjust = method;
        }
        require(adjust != null, "Support lacks static adjustRange(II)I");
        require(containsInt(adjust, 120), "Support test range is not 120");
    }

    private static void writeJar(File output) throws IOException {
        File temp = new File(output.getPath() + ".tmp");
        JarOutputStream out = new JarOutputStream(new FileOutputStream(temp));
        try {
            for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
                String name = entry.getKey();
                if (name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"))) continue;
                JarEntry jarEntry = new JarEntry(name);
                Long time = TIMES.get(name);
                if (time != null && time.longValue() >= 0L) jarEntry.setTime(time.longValue());
                out.putNextEntry(jarEntry);
                out.write(entry.getValue());
                out.closeEntry();
            }
        } finally {
            out.close();
        }
        Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void verifyOutput(File output) throws IOException {
        JarFile jar = new JarFile(output);
        try {
            require(jar.getEntry("META-INF/MANIFEST.MF") != null, "Output JAR lost its manifest");
            require(jar.getEntry(SUPPORT_CLASS + ".class") != null, "Output JAR lacks range support");
            require(jar.size() == ENTRIES.size(), "Output JAR unexpectedly changed entry count");
            int hookCalls = 0;
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                byte[] bytes = readAll(jar.getInputStream(entry));
                new ClassReader(bytes);
                if (entry.getName().equals(attackOwner.name + ".class")) {
                    ClassNode outputAttack = readClass(bytes);
                    MethodNode method = findMethod(outputAttack, attackStart.name, attackStart.desc);
                    for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode) insn;
                            if (SUPPORT_CLASS.equals(call.owner) && "adjustRange".equals(call.name)
                                    && "(II)I".equals(call.desc)) hookCalls++;
                        }
                    }
                }
            }
            require(hookCalls == 1, "Output range hook count is " + hookCalls);
        } finally {
            jar.close();
        }
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode findMethod(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        return null;
    }

    private static boolean calls(MethodNode method, String owner, String name, String desc) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) return true;
            }
        }
        return false;
    }

    private static boolean containsGetField(MethodNode method, String desc) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == GETFIELD
                    && desc.equals(((FieldInsnNode) insn).desc)) return true;
        }
        return false;
    }

    private static boolean containsInt(MethodNode method, int expected) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (isInt(insn, expected)) return true;
        }
        return false;
    }

    private static boolean isInt(AbstractInsnNode insn, int expected) {
        if (insn == null) return false;
        int opcode = insn.getOpcode();
        if (expected >= -1 && expected <= 5) return opcode == ICONST_0 + expected;
        if (insn instanceof IntInsnNode) return ((IntInsnNode) insn).operand == expected;
        return insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer
                && ((Integer) ((LdcInsnNode) insn).cst).intValue() == expected;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn.getNext();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getNext();
        return cursor;
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

    private static String shortName(String value) {
        return value.length() <= 20 ? value : value.substring(0, 8) + "..." + value.substring(value.length() - 8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
