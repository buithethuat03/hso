import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Sets every client-side skill cooldown to 100 milliseconds. */
public final class HSOSKYCooldown100Patcher implements Opcodes {
    private static final String INPUT_JAR =
            "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_REMOTEZONE.jar";
    private static final String OUTPUT_JAR =
            "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_REMOTEZONE_CD100.jar";
    private static final int COOLDOWN_MS = 100;
    private static final String COMMAND_SUPPORT = "HSOSKYPetFeedSupport";
    private static final String RANGE_SUPPORT = "HSOSKYRangeSupport";

    private static final Map<String, byte[]> ENTRIES = new LinkedHashMap<String, byte[]>();
    private static final Map<String, Long> TIMES = new LinkedHashMap<String, Long>();

    private static ClassNode cooldownClass;
    private static MethodNode cooldownGetter;
    private static ClassNode playerClass;
    private static MethodNode cooldownApplyMethod;
    private static int getterPatches;
    private static int specialCooldownPatches;

    private HSOSKYCooldown100Patcher() {
    }

    public static void main(String[] args) throws Exception {
        File output = new File(OUTPUT_JAR);
        require(!output.exists(), "Refusing to overwrite existing output: " + output.getPath());
        readJar(new File(INPUT_JAR));
        discoverPlayerCooldownApplication();
        patchCooldownGetter();
        patchSpecialAllSkillCooldown();

        require(getterPatches == 1, "Expected one cooldown-getter patch, got " + getterPatches);
        require(specialCooldownPatches == 2,
                "Expected two special all-skill timer patches, got " + specialCooldownPatches);

        saveClass(cooldownClass);
        saveClass(playerClass);
        writeJar(output);
        verifyOutput(output);

        System.out.println("[+] Built " + OUTPUT_JAR);
        System.out.println("    per-skill client cooldown: " + COOLDOWN_MS + " ms");
        System.out.println("    special all-skill lock: " + COOLDOWN_MS + " ms");
        System.out.println("    remote-zone/PvP/help/autofeed features preserved");
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
        require(ENTRIES.containsKey(COMMAND_SUPPORT + ".class"), "Input lacks command support");
        require(ENTRIES.containsKey(RANGE_SUPPORT + ".class"), "Input lacks remote-range support");
    }

    private static void discoverPlayerCooldownApplication() {
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode candidate = readClass(entry.getValue());
            if (!containsString(candidate, "Player vec7")) continue;
            require(playerClass == null, "Multiple player controllers found");
            playerClass = candidate;
        }
        require(playerClass != null, "Cannot locate player controller");

        String getterOwner = null;
        String getterName = null;
        for (MethodNode method : playerClass.methods) {
            if ((method.access & ACC_STATIC) != 0 || !"(II)V".equals(method.desc)) continue;
            if (countInt(method, 2000) != 2) continue;
            Map<String, Integer> intCalls = new LinkedHashMap<String, Integer>();
            for (AbstractInsnNode insn = method.instructions.getFirst();
                    insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode && insn.getOpcode() == INVOKEVIRTUAL
                        && "()I".equals(((MethodInsnNode) insn).desc)) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    String key = call.owner + "\n" + call.name;
                    Integer old = intCalls.get(key);
                    intCalls.put(key, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
                }
            }
            String repeatedGetter = null;
            for (Map.Entry<String, Integer> call : intCalls.entrySet()) {
                if (call.getValue().intValue() == 2) {
                    require(repeatedGetter == null, "Multiple repeated no-arg int calls in cooldown method");
                    repeatedGetter = call.getKey();
                }
            }
            if (repeatedGetter != null) {
                require(cooldownApplyMethod == null, "Multiple cooldown application methods found");
                cooldownApplyMethod = method;
                String[] parts = repeatedGetter.split("\\n", -1);
                getterOwner = parts[0];
                getterName = parts[1];
            }
        }
        require(cooldownApplyMethod != null, "Cannot locate player cooldown application method");
        byte[] cooldownBytes = ENTRIES.get(getterOwner + ".class");
        require(cooldownBytes != null, "Cooldown getter owner is absent from JAR");
        cooldownClass = readClass(cooldownBytes);
        cooldownGetter = findMethod(cooldownClass, getterName, "()I");
        require(cooldownGetter != null && (cooldownGetter.access & ACC_STATIC) == 0,
                "Cannot locate skill cooldown getter");
    }

    private static void patchCooldownGetter() {
        cooldownGetter.instructions.clear();
        cooldownGetter.tryCatchBlocks.clear();
        if (cooldownGetter.localVariables != null) cooldownGetter.localVariables.clear();
        InsnList body = new InsnList();
        body.add(pushInt(COOLDOWN_MS));
        body.add(new InsnNode(IRETURN));
        cooldownGetter.instructions.add(body);
        getterPatches++;
    }

    private static void patchSpecialAllSkillCooldown() {
        AbstractInsnNode insn = cooldownApplyMethod.instructions.getFirst();
        while (insn != null) {
            AbstractInsnNode next = insn.getNext();
            if (isInt(insn, 2000)) {
                cooldownApplyMethod.instructions.set(insn, pushInt(COOLDOWN_MS));
                specialCooldownPatches++;
            }
            insn = next;
        }
    }

    private static void verifyOutput(File output) throws IOException {
        JarFile jar = new JarFile(output);
        try {
            require(jar.getEntry("META-INF/MANIFEST.MF") != null, "Output JAR lost its manifest");
            require(jar.getEntry(COMMAND_SUPPORT + ".class") != null, "Output lost command support");
            require(jar.getEntry(RANGE_SUPPORT + ".class") != null, "Output lost range support");
            require(jar.size() == ENTRIES.size(), "Output JAR unexpectedly changed entry count");

            ClassNode savedCooldown = readClass(readAll(jar.getInputStream(
                    jar.getEntry(cooldownClass.name + ".class"))));
            MethodNode savedGetter = findMethod(savedCooldown, cooldownGetter.name, cooldownGetter.desc);
            require(savedGetter != null && returnsConstant(savedGetter, COOLDOWN_MS),
                    "Output cooldown getter does not return 100 ms");

            ClassNode savedPlayer = readClass(readAll(jar.getInputStream(
                    jar.getEntry(playerClass.name + ".class"))));
            MethodNode savedApply = findMethod(savedPlayer, cooldownApplyMethod.name, cooldownApplyMethod.desc);
            require(savedApply != null && countInt(savedApply, 2000) == 0
                            && countInt(savedApply, COOLDOWN_MS) >= 2,
                    "Output retains the special 2000 ms all-skill lock");

            ClassNode savedRange = readClass(readAll(jar.getInputStream(
                    jar.getEntry(RANGE_SUPPORT + ".class"))));
            MethodNode range = findMethod(savedRange, "adjustRange", "(II)I");
            require(range != null && returnsConstant(range, 32767),
                    "Output lost remote-zone cast range");

            ClassNode commands = readClass(readAll(jar.getInputStream(
                    jar.getEntry(COMMAND_SUPPORT + ".class"))));
            require(findMethod(commands, "handleChat", "(Ljava/lang/String;)Z") != null
                            && findMethod(commands, "isAutoTargetType", "(I)Z") != null,
                    "Output lost chat/PvP command methods");
        } finally {
            jar.close();
        }
    }

    private static boolean returnsConstant(MethodNode method, int value) {
        AbstractInsnNode first = firstReal(method);
        AbstractInsnNode second = nextReal(first);
        return isInt(first, value) && second != null && second.getOpcode() == IRETURN
                && nextReal(second) == null;
    }

    private static int countFieldReads(MethodNode method, String owner, String desc) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == GETFIELD) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (owner.equals(field.owner) && desc.equals(field.desc)) count++;
            }
        }
        return count;
    }

    private static int countOpcode(MethodNode method, int opcode) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == opcode) count++;
        }
        return count;
    }

    private static int countInt(MethodNode method, int value) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (isInt(insn, value)) count++;
        }
        return count;
    }

    private static boolean containsString(ClassNode owner, String part) {
        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String
                        && ((String) ((LdcInsnNode) insn).cst).indexOf(part) >= 0) return true;
            }
        }
        return false;
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

    private static MethodNode findMethod(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        return null;
    }

    private static AbstractInsnNode pushInt(int value) {
        if (value >= -1 && value <= 5) return new InsnNode(ICONST_0 + value);
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) return new IntInsnNode(BIPUSH, value);
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) return new IntInsnNode(SIPUSH, value);
        return new LdcInsnNode(Integer.valueOf(value));
    }

    private static boolean isInt(AbstractInsnNode insn, int value) {
        if (insn == null) return false;
        int opcode = insn.getOpcode();
        if (opcode >= ICONST_M1 && opcode <= ICONST_5) return value == opcode - ICONST_0;
        if (insn instanceof IntInsnNode) return ((IntInsnNode) insn).operand == value;
        return insn instanceof LdcInsnNode && Integer.valueOf(value).equals(((LdcInsnNode) insn).cst);
    }

    private static AbstractInsnNode firstReal(MethodNode method) {
        AbstractInsnNode cursor = method.instructions.getFirst();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getNext();
        return cursor;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn == null ? null : insn.getNext();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getNext();
        return cursor;
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static void saveClass(ClassNode owner) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        owner.accept(writer);
        ENTRIES.put(owner.name + ".class", writer.toByteArray());
    }

    private static void writeJar(File output) throws IOException {
        File temp = new File(output.getPath() + ".tmp");
        JarOutputStream out = new JarOutputStream(new FileOutputStream(temp));
        try {
            for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
                String name = entry.getKey();
                if (name.startsWith("META-INF/")
                        && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"))) {
                    continue;
                }
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
