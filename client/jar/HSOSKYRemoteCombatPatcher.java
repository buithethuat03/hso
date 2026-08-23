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
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Makes the existing PvP auto-combat scan cover the whole loaded zone and
 * prevents attack-start from pathing toward a distant target.
 */
public final class HSOSKYRemoteCombatPatcher implements Opcodes {
    private static final String INPUT_JAR =
            "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP.jar";
    private static final String OUTPUT_JAR =
            "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_REMOTEZONE.jar";
    private static final String COMMAND_SUPPORT = "HSOSKYPetFeedSupport";
    private static final String RANGE_SUPPORT = "HSOSKYRangeSupport";
    private static final int WHOLE_ZONE_RANGE = 32767;

    private static final Map<String, byte[]> ENTRIES = new LinkedHashMap<String, byte[]>();
    private static final Map<String, Long> TIMES = new LinkedHashMap<String, Long>();

    private static ClassNode playerClass;
    private static MethodNode autoTargetMethod;
    private static MethodNode playerUpdateMethod;
    private static String targetBaseClass;
    private static String detectionRangeField;
    private static ClassNode rangeSupportClass;
    private static int scanRangePatches;
    private static int targetRetentionPatches;
    private static int castRangePatches;

    private HSOSKYRemoteCombatPatcher() {
    }

    public static void main(String[] args) throws Exception {
        File output = new File(OUTPUT_JAR);
        require(!output.exists(), "Refusing to overwrite existing output: " + output.getPath());
        readJar(new File(INPUT_JAR));
        discoverPlayerAndAutoTarget();
        patchWholeZoneTargetScan();
        discoverAndPatchTargetRetention();
        patchCastRange();

        require(scanRangePatches == 1,
                "Expected one auto-target scan-range patch, got " + scanRangePatches);
        require(targetRetentionPatches == 1,
                "Expected one remote-target retention patch, got " + targetRetentionPatches);
        require(castRangePatches == 1,
                "Expected one cast-range patch, got " + castRangePatches);

        saveClass(playerClass);
        saveClass(rangeSupportClass);
        writeJar(output);
        verifyOutput(output);

        System.out.println("[+] Built " + OUTPUT_JAR);
        System.out.println("    auto target scan range: " + WHOLE_ZONE_RANGE);
        System.out.println("    cast range: " + WHOLE_ZONE_RANGE + " for every class");
        System.out.println("    movement toward attack targets: disabled by in-range attack start");
        System.out.println("    mode 6: monsters + hostile players; mode 7: monsters only");
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
        require(ENTRIES.containsKey(COMMAND_SUPPORT + ".class"),
                "Input lacks PvP/help command support");
        require(ENTRIES.containsKey(RANGE_SUPPORT + ".class"),
                "Input lacks range support");
    }

    private static void discoverPlayerAndAutoTarget() {
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode candidate = readClass(entry.getValue());
            if (containsString(candidate, "Player vec7")) {
                require(playerClass == null, "Multiple player controllers found");
                playerClass = candidate;
            }
        }
        require(playerClass != null, "Cannot locate player controller");
        targetBaseClass = playerClass.superName;

        for (MethodNode method : playerClass.methods) {
            if (calls(method, COMMAND_SUPPORT, "isAutoPlayersEnabled", "()Z")
                    && calls(method, COMMAND_SUPPORT, "isAutoTargetType", "(I)Z")) {
                throw new IllegalStateException("Auto-target hooks unexpectedly share one method");
            }
            if (calls(method, COMMAND_SUPPORT, "isAutoPlayersEnabled", "()Z")) {
                require(autoTargetMethod == null, "Multiple auto-target scan methods found");
                autoTargetMethod = method;
            }
        }
        require(autoTargetMethod != null, "Cannot locate PvP-aware auto-target scan");

        for (AbstractInsnNode insn = autoTargetMethod.instructions.getFirst();
                insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != GETFIELD) continue;
            FieldInsnNode field = (FieldInsnNode) insn;
            AbstractInsnNode three = nextReal(insn);
            AbstractInsnNode multiply = nextReal(three);
            AbstractInsnNode two = nextReal(multiply);
            AbstractInsnNode divide = nextReal(two);
            if (targetBaseClass.equals(field.owner) && "I".equals(field.desc)
                    && isInt(three, 3) && multiply != null && multiply.getOpcode() == IMUL
                    && isInt(two, 2) && divide != null && divide.getOpcode() == IDIV) {
                require(detectionRangeField == null, "Multiple detection-range fields found");
                detectionRangeField = field.name;
            }
        }
        require(detectionRangeField != null, "Cannot map player detection range");
    }

    private static void patchWholeZoneTargetScan() {
        for (AbstractInsnNode insn = autoTargetMethod.instructions.getFirst();
                insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != GETFIELD) continue;
            FieldInsnNode field = (FieldInsnNode) insn;
            AbstractInsnNode loadThis = previousReal(field);
            AbstractInsnNode three = nextReal(field);
            AbstractInsnNode multiply = nextReal(three);
            AbstractInsnNode two = nextReal(multiply);
            AbstractInsnNode divide = nextReal(two);
            if (!targetBaseClass.equals(field.owner) || !detectionRangeField.equals(field.name)
                    || !"I".equals(field.desc)
                    || !(loadThis instanceof VarInsnNode) || loadThis.getOpcode() != ALOAD
                    || ((VarInsnNode) loadThis).var != 0
                    || !isInt(three, 3) || multiply == null || multiply.getOpcode() != IMUL
                    || !isInt(two, 2) || divide == null || divide.getOpcode() != IDIV) continue;

            autoTargetMethod.instructions.insertBefore(loadThis, new LdcInsnNode(WHOLE_ZONE_RANGE));
            autoTargetMethod.instructions.remove(loadThis);
            autoTargetMethod.instructions.remove(field);
            autoTargetMethod.instructions.remove(three);
            autoTargetMethod.instructions.remove(multiply);
            autoTargetMethod.instructions.remove(two);
            autoTargetMethod.instructions.remove(divide);
            scanRangePatches++;
            break;
        }
    }

    private static void discoverAndPatchTargetRetention() {
        for (MethodNode method : playerClass.methods) {
            if ((method.access & ACC_STATIC) == 0 && "()V".equals(method.desc)
                    && calls(method, playerClass.name, autoTargetMethod.name, autoTargetMethod.desc)) {
                require(playerUpdateMethod == null, "Multiple player update loops call auto-target scan");
                playerUpdateMethod = method;
            }
        }
        require(playerUpdateMethod != null, "Cannot locate player update loop");

        for (AbstractInsnNode insn = playerUpdateMethod.instructions.getFirst();
                insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != GETFIELD) continue;
            FieldInsnNode field = (FieldInsnNode) insn;
            AbstractInsnNode twenty = nextReal(field);
            AbstractInsnNode add = nextReal(twenty);
            AbstractInsnNode loadThis = previousReal(field);
            if (!targetBaseClass.equals(field.owner) || !detectionRangeField.equals(field.name)
                    || !"I".equals(field.desc)
                    || !(loadThis instanceof VarInsnNode) || loadThis.getOpcode() != ALOAD
                    || ((VarInsnNode) loadThis).var != 0
                    || !isInt(twenty, 20) || add == null || add.getOpcode() != IADD) continue;

            playerUpdateMethod.instructions.insertBefore(loadThis, new LdcInsnNode(WHOLE_ZONE_RANGE));
            playerUpdateMethod.instructions.remove(loadThis);
            playerUpdateMethod.instructions.remove(field);
            playerUpdateMethod.instructions.remove(twenty);
            playerUpdateMethod.instructions.remove(add);
            targetRetentionPatches++;
        }
    }

    private static void patchCastRange() {
        rangeSupportClass = readClass(ENTRIES.get(RANGE_SUPPORT + ".class"));
        MethodNode adjust = null;
        for (MethodNode method : rangeSupportClass.methods) {
            if ((method.access & ACC_STATIC) != 0 && "adjustRange".equals(method.name)
                    && "(II)I".equals(method.desc)) {
                require(adjust == null, "Multiple adjustRange methods found");
                adjust = method;
            }
        }
        require(adjust != null, "Cannot locate adjustRange support method");
        adjust.instructions.clear();
        adjust.tryCatchBlocks.clear();
        if (adjust.localVariables != null) adjust.localVariables.clear();
        InsnList body = new InsnList();
        body.add(new LdcInsnNode(WHOLE_ZONE_RANGE));
        body.add(new InsnNode(IRETURN));
        adjust.instructions.add(body);
        castRangePatches++;
    }

    private static void verifyOutput(File output) throws IOException {
        JarFile jar = new JarFile(output);
        try {
            require(jar.getEntry("META-INF/MANIFEST.MF") != null, "Output JAR lost its manifest");
            require(jar.getEntry(COMMAND_SUPPORT + ".class") != null,
                    "Output lost PvP/help commands");
            require(jar.getEntry(RANGE_SUPPORT + ".class") != null,
                    "Output lost range support");
            require(jar.size() == ENTRIES.size(), "Output JAR unexpectedly changed entry count");

            ClassNode savedPlayer = readClass(readAll(jar.getInputStream(
                    jar.getEntry(playerClass.name + ".class"))));
            MethodNode savedScan = findMethod(savedPlayer, autoTargetMethod.name, autoTargetMethod.desc);
            require(savedScan != null && containsInt(savedScan, WHOLE_ZONE_RANGE),
                    "Output auto-target scan lacks whole-zone range");
            require(calls(savedScan, COMMAND_SUPPORT, "isAutoPlayersEnabled", "()Z"),
                    "Output lost mode-6 player filtering");

            ClassNode savedRange = readClass(readAll(jar.getInputStream(
                    jar.getEntry(RANGE_SUPPORT + ".class"))));
            MethodNode adjust = findMethod(savedRange, "adjustRange", "(II)I");
            require(adjust != null && returnsConstant(adjust, WHOLE_ZONE_RANGE),
                    "Output cast range is not whole-zone range");
        } finally {
            jar.close();
        }
    }

    private static boolean returnsConstant(MethodNode method, int value) {
        AbstractInsnNode first = method.instructions.getFirst();
        while (first != null && first.getOpcode() < 0) first = first.getNext();
        AbstractInsnNode second = nextReal(first);
        return isInt(first, value) && second != null && second.getOpcode() == IRETURN
                && nextReal(second) == null;
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

    private static boolean containsString(ClassNode owner, String value) {
        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String
                        && ((String) ((LdcInsnNode) insn).cst).indexOf(value) >= 0) return true;
            }
        }
        return false;
    }

    private static boolean containsInt(MethodNode method, int value) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (isInt(insn, value)) return true;
        }
        return false;
    }

    private static boolean isInt(AbstractInsnNode insn, int value) {
        if (insn == null) return false;
        int opcode = insn.getOpcode();
        if (opcode >= ICONST_M1 && opcode <= ICONST_5) return value == opcode - ICONST_0;
        if (insn instanceof org.objectweb.asm.tree.IntInsnNode) {
            return ((org.objectweb.asm.tree.IntInsnNode) insn).operand == value;
        }
        return insn instanceof LdcInsnNode && Integer.valueOf(value).equals(((LdcInsnNode) insn).cst);
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn == null ? null : insn.getNext();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getNext();
        return cursor;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn == null ? null : insn.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getPrevious();
        return cursor;
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
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
