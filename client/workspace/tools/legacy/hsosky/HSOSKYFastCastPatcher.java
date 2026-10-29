import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HSOSKYFastCastPatcher implements Opcodes {
    private static final String INPUT_JAR = "HSOSKY_10SKILL.jar";
    private static final String OUTPUT_JAR = "HSOSKY_10SKILL_FASTCAST.jar";
    private static final int CAST_FRAME = 1;
    private static final int RECOVERY_END_FRAME = 2;
    private static final int MIN_REQUEST_INTERVAL_MS = 100;

    private static final Map<String, byte[]> ENTRIES = new LinkedHashMap<String, byte[]>();
    private static final Map<String, Long> TIMES = new LinkedHashMap<String, Long>();

    private static ClassNode castClass;
    private static ClassNode animationClass;
    private static MethodNode castInitMethod;
    private static MethodNode animationUpdateMethod;
    private static String actorName;
    private static String castFrameField;
    private static String actorStateField;
    private static String actorFrameField;
    private static FieldInsnNode localPlayerField;

    private static int castFramePatches;
    private static int recoveryPatches;
    private static int requestIntervalPatches;
    private static int autoGatePatches;

    private HSOSKYFastCastPatcher() {
    }

    public static void main(String[] args) throws Exception {
        File input = new File(args.length > 0 ? args[0] : INPUT_JAR);
        File output = new File(args.length > 1 ? args[1] : OUTPUT_JAR);
        require(input.isFile(), "Missing input: " + input.getPath());
        require(!output.exists(), "Refusing to overwrite: " + output.getPath());
        readJar(input);
        discoverCastClass();
        discoverFieldsAndAnimationClass();
        patchCastStart();
        patchRequestInterval();
        patchAutoGate();
        patchRecovery();
        validate();
        saveClass(castClass);
        saveClass(animationClass);
        writeJar(output);
        verifyOutput(output);

        System.out.println("[+] Built " + output.getPath());
        System.out.println("    cast packet frame: " + CAST_FRAME + " (patches=" + castFramePatches + ")");
        System.out.println("    recovery end frame: " + RECOVERY_END_FRAME + " (patches=" + recoveryPatches + ")");
        System.out.println("    minimum request interval: " + MIN_REQUEST_INTERVAL_MS + " ms (patches="
                + requestIntervalPatches + ")");
        System.out.println("    auto-combat gate: " + MIN_REQUEST_INTERVAL_MS + " ms (patches=" + autoGatePatches + ")");
    }

    private static void readJar(File input) throws IOException {
        require(input.isFile(), "Missing input JAR: " + input.getPath());
        JarFile jar = new JarFile(input);
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                ENTRIES.put(entry.getName(), readAll(jar.getInputStream(entry)));
                TIMES.put(entry.getName(), Long.valueOf(entry.getTime()));
            }
        } finally {
            jar.close();
        }
    }

    private static void discoverCastClass() {
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode node = readClass(entry.getValue());
            MethodNode candidateInit = findCastInitializer(node);
            if (candidateInit != null && hasStaticIntInitialization(node, 600)
                    && hasCurrentTimeMillisBooleanMethod(node)) {
                castClass = node;
                castInitMethod = candidateInit;
                break;
            }
        }
        require(castClass != null, "Cannot locate the skill-cast state machine");
    }

    private static MethodNode findCastInitializer(ClassNode owner) {
        for (MethodNode method : owner.methods) {
            Type[] args = Type.getArgumentTypes(method.desc);
            if ((method.access & ACC_STATIC) == 0 && Type.VOID_TYPE.equals(Type.getReturnType(method.desc))
                    && args.length == 4 && args[0].getSort() == Type.OBJECT && args[1].getSort() == Type.OBJECT
                    && args[2].getSort() == Type.INT && args[3].getSort() == Type.BYTE) {
                return method;
            }
        }
        return null;
    }

    private static boolean hasStaticIntInitialization(ClassNode owner, int value) {
        MethodNode clinit = findMethod(owner, "<clinit>", "()V");
        if (clinit == null) {
            return false;
        }
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == PUTSTATIC
                    && owner.name.equals(((FieldInsnNode) insn).owner) && "I".equals(((FieldInsnNode) insn).desc)
                    && isInt(previousReal(insn), value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCurrentTimeMillisBooleanMethod(ClassNode owner) {
        for (MethodNode method : owner.methods) {
            if (!"()Z".equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode && insn.getOpcode() == INVOKESTATIC
                        && "java/lang/System".equals(((MethodInsnNode) insn).owner)
                        && "currentTimeMillis".equals(((MethodInsnNode) insn).name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void discoverFieldsAndAnimationClass() {
        Type[] args = Type.getArgumentTypes(castInitMethod.desc);
        actorName = args[0].getInternalName();
        String actorDesc = args[0].getDescriptor();

        Set<String> assignedInts = new LinkedHashSet<String>();
        for (AbstractInsnNode insn = castInitMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == PUTFIELD) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (castClass.name.equals(field.owner) && "I".equals(field.desc)) {
                    assignedInts.add(field.name);
                    if (assignedInts.size() == 4) {
                        castFrameField = field.name;
                    }
                }
            }

            if (localPlayerField == null && insn instanceof JumpInsnNode
                    && (insn.getOpcode() == IF_ACMPNE || insn.getOpcode() == IF_ACMPEQ)) {
                AbstractInsnNode first = previousReal(insn);
                AbstractInsnNode second = previousReal(first);
                FieldInsnNode staticObject = first instanceof FieldInsnNode && first.getOpcode() == GETSTATIC
                        ? (FieldInsnNode) first
                        : (second instanceof FieldInsnNode && second.getOpcode() == GETSTATIC
                        ? (FieldInsnNode) second : null);
                VarInsnNode actorLoad = first instanceof VarInsnNode ? (VarInsnNode) first
                        : (second instanceof VarInsnNode ? (VarInsnNode) second : null);
                if (staticObject != null && staticObject.desc.startsWith("L") && actorLoad != null
                        && actorLoad.getOpcode() == ALOAD && actorLoad.var == 1) {
                    localPlayerField = staticObject;
                }
            }

            if (insn instanceof FieldInsnNode && insn.getOpcode() == GETFIELD) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (actorName.equals(field.owner) && "I".equals(field.desc)
                        && isInt(nextReal(insn), 4) && nextReal(nextReal(insn)) instanceof JumpInsnNode) {
                    actorStateField = field.name;
                }
            }
        }

        require(castFrameField != null, "Cannot identify cast packet frame field");
        require(localPlayerField != null, "Cannot identify local-player singleton field");
        require(actorStateField != null, "Cannot identify actor animation-state field");

        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode candidate = readClass(entry.getValue());
            MethodNode update = null;
            boolean hasIntSetup = false;
            for (MethodNode method : candidate.methods) {
                if ((method.access & ACC_STATIC) == 0 && "(I)V".equals(method.desc)) {
                    hasIntSetup = true;
                }
                if ((method.access & ACC_STATIC) == 0 && ("(" + actorDesc + ")V").equals(method.desc)
                        && containsSwitch(method)) {
                    update = method;
                }
            }
            if (!hasIntSetup || update == null) {
                continue;
            }
            String frameField = findIncrementedActorField(update);
            if (frameField != null) {
                animationClass = candidate;
                animationUpdateMethod = update;
                actorFrameField = frameField;
                break;
            }
        }
        require(animationClass != null && animationUpdateMethod != null,
                "Cannot identify attack-animation controller");
        require(actorFrameField != null, "Cannot identify actor animation-frame counter");
        System.out.println("Mapped cast=" + castClass.name + " init=" + castInitMethod.name + castInitMethod.desc);
        System.out.println("Mapped animation=" + animationClass.name + " update="
                + animationUpdateMethod.name + animationUpdateMethod.desc);
    }

    private static boolean containsSwitch(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == TABLESWITCH || insn.getOpcode() == LOOKUPSWITCH) {
                return true;
            }
        }
        return false;
    }

    private static String findIncrementedActorField(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != PUTFIELD) {
                continue;
            }
            FieldInsnNode put = (FieldInsnNode) insn;
            AbstractInsnNode add = previousReal(insn);
            AbstractInsnNode one = previousReal(add);
            AbstractInsnNode get = previousReal(one);
            if (actorName.equals(put.owner) && "I".equals(put.desc) && add != null && add.getOpcode() == IADD
                    && isInt(one, 1) && get instanceof FieldInsnNode && get.getOpcode() == GETFIELD
                    && put.name.equals(((FieldInsnNode) get).name)) {
                return put.name;
            }
        }
        return null;
    }

    private static void patchCastStart() {
        for (AbstractInsnNode insn = castInitMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof JumpInsnNode && insn.getOpcode() == IF_ACMPNE
                    && comparisonUsesLocalPlayer(insn)) {
                InsnList code = new InsnList();
                code.add(new VarInsnNode(ALOAD, 0));
                code.add(pushInt(CAST_FRAME));
                code.add(new FieldInsnNode(PUTFIELD, castClass.name, castFrameField, "I"));
                castInitMethod.instructions.insert(insn, code);
                castFramePatches++;
                break;
            }
        }
    }

    private static boolean comparisonUsesLocalPlayer(AbstractInsnNode jump) {
        AbstractInsnNode cursor = previousReal(jump);
        for (int i = 0; i < 3 && cursor != null; i++, cursor = previousReal(cursor)) {
            if (cursor instanceof FieldInsnNode && cursor.getOpcode() == GETSTATIC) {
                FieldInsnNode field = (FieldInsnNode) cursor;
                if (localPlayerField.owner.equals(field.owner) && localPlayerField.name.equals(field.name)
                        && localPlayerField.desc.equals(field.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void patchRequestInterval() {
        for (MethodNode method : castClass.methods) {
            if ("<clinit>".equals(method.name)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof FieldInsnNode && insn.getOpcode() == PUTSTATIC
                        && castClass.name.equals(((FieldInsnNode) insn).owner)
                        && ("S".equals(((FieldInsnNode) insn).desc)
                        || "I".equals(((FieldInsnNode) insn).desc))) {
                    InsnList clamp = new InsnList();
                    clamp.add(new InsnNode(POP));
                    clamp.add(pushInt(MIN_REQUEST_INTERVAL_MS));
                    method.instructions.insertBefore(insn, clamp);
                    requestIntervalPatches++;
                }
            }
        }
    }

    private static void patchAutoGate() {
        MethodNode clinit = findMethod(castClass, "<clinit>", "()V");
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == PUTSTATIC
                    && castClass.name.equals(((FieldInsnNode) insn).owner) && "I".equals(((FieldInsnNode) insn).desc)
                    && isInt(previousReal(insn), 600)) {
                clinit.instructions.set(previousReal(insn), pushInt(MIN_REQUEST_INTERVAL_MS));
                autoGatePatches++;
            }
        }
    }

    private static void patchRecovery() {
        LabelNode normal = new LabelNode();
        InsnList code = new InsnList();
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new FieldInsnNode(GETSTATIC, localPlayerField.owner, localPlayerField.name, localPlayerField.desc));
        code.add(new JumpInsnNode(IF_ACMPNE, normal));
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new FieldInsnNode(GETFIELD, actorName, actorStateField, "I"));
        code.add(new InsnNode(ICONST_2));
        code.add(new JumpInsnNode(IF_ICMPNE, normal));
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new FieldInsnNode(GETFIELD, actorName, actorFrameField, "I"));
        code.add(pushInt(RECOVERY_END_FRAME));
        code.add(new JumpInsnNode(IF_ICMPLT, normal));
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new InsnNode(ICONST_0));
        code.add(new FieldInsnNode(PUTFIELD, actorName, actorStateField, "I"));
        code.add(new InsnNode(RETURN));
        code.add(normal);
        animationUpdateMethod.instructions.insert(code);
        recoveryPatches++;
    }

    private static void validate() {
        require(castFramePatches == 1, "Expected one cast-frame patch, got " + castFramePatches);
        require(recoveryPatches == 1, "Expected one recovery patch, got " + recoveryPatches);
        require(requestIntervalPatches <= 1,
                "Expected at most one minimum-request-interval patch, got " + requestIntervalPatches);
        require(autoGatePatches == 1, "Expected one auto-combat gate patch, got " + autoGatePatches);
    }

    private static void saveClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        ENTRIES.put(node.name + ".class", writer.toByteArray());
    }

    private static void writeJar(File output) throws IOException {
        File temp = new File(output.getPath() + ".tmp");
        JarOutputStream out = new JarOutputStream(new FileOutputStream(temp));
        try {
            for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
                if (entry.getKey().startsWith("META-INF/")
                        && (entry.getKey().endsWith(".SF") || entry.getKey().endsWith(".RSA")
                        || entry.getKey().endsWith(".DSA"))) {
                    continue;
                }
                JarEntry jarEntry = new JarEntry(entry.getKey());
                Long time = TIMES.get(entry.getKey());
                if (time != null && time.longValue() >= 0L) {
                    jarEntry.setTime(time.longValue());
                }
                out.putNextEntry(jarEntry);
                out.write(entry.getValue());
                out.closeEntry();
            }
        } finally {
            out.close();
        }
        Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void verifyOutput(File output) throws Exception {
        JarFile jar = new JarFile(output);
        try {
            require(jar.getEntry("META-INF/MANIFEST.MF") != null, "Output JAR lost its manifest");
            require(jar.size() == ENTRIES.size(), "Output JAR unexpectedly changed entry count");
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    new ClassReader(readAll(jar.getInputStream(entry)));
                }
            }
        } finally {
            jar.close();
        }

        URL[] urls = new URL[]{output.toURI().toURL(), new File("microemulator.jar").toURI().toURL()};
        URLClassLoader loader = new URLClassLoader(urls, HSOSKYFastCastPatcher.class.getClassLoader());
        try {
            Class.forName(castClass.name.replace('/', '.'), false, loader);
            Class.forName(animationClass.name.replace('/', '.'), false, loader);
        } finally {
            loader.close();
        }
    }

    private static ClassNode readClass(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode findMethod(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static AbstractInsnNode pushInt(int value) {
        if (value >= -1 && value <= 5) {
            return new InsnNode(ICONST_0 + value);
        }
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            return new IntInsnNode(BIPUSH, value);
        }
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            return new IntInsnNode(SIPUSH, value);
        }
        return new LdcInsnNode(Integer.valueOf(value));
    }

    private static boolean isInt(AbstractInsnNode insn, int expected) {
        Integer value = intValue(insn);
        return value != null && value.intValue() == expected;
    }

    private static Integer intValue(AbstractInsnNode insn) {
        if (insn == null) {
            return null;
        }
        int opcode = insn.getOpcode();
        if (opcode >= ICONST_M1 && opcode <= ICONST_5) {
            return Integer.valueOf(opcode - ICONST_0);
        }
        if (insn instanceof IntInsnNode && (opcode == BIPUSH || opcode == SIPUSH)) {
            return Integer.valueOf(((IntInsnNode) insn).operand);
        }
        if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer) {
            return (Integer) ((LdcInsnNode) insn).cst;
        }
        return null;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn == null ? null : insn.getNext();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        return cursor;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn == null ? null : insn.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getPrevious();
        }
        return cursor;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
