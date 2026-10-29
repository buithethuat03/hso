package luna.tools;

import java.io.*;
import java.lang.reflect.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Executes the real horse re-engage method from the before/after client JARs. */
public final class LunaHorseStateHostTest implements Opcodes {
    private static final String STUB = "luna/tools/HorseStateNativeStub";
    private static final String PLAYER = "luna/tools/HorseStatePlayerStub";
    private static final String EXTRACTED = "luna/tools/HorseStateExtracted";
    private static final String MOUNT_KEY = "MOUNT";
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("usage: LunaHorseStateHostTest before.jar after.jar symbols.properties");
        File before = new File(args[0]), after = new File(args[1]);
        Properties symbols = loadProperties(new File(args[2]));
        expect(sha256(before).equalsIgnoreCase(required(symbols, "input.sha256")), "catalog input hash");
        expect(sha256(after).equalsIgnoreCase(required(symbols, "output.sha256")), "catalog output hash");

        String supportOwner = required(symbols, "support.owner");
        String methodName = required(symbols, "support.method");
        String methodDesc = required(symbols, "support.descriptor");
        String mountOwner = required(symbols, "mount.access.owner");
        String mountName = required(symbols, "mount.field");
        String mountDesc = required(symbols, "mount.descriptor");
        expect("B".equals(mountDesc), "native mount descriptor is byte");
        expect("()V".equals(methodDesc), "re-engage descriptor");

        MethodNode original = readMethod(before, supportOwner, methodName, methodDesc);
        MethodNode patched = readMethod(after, supportOwner, methodName, methodDesc);
        int oldMountWrites = countMountWrites(original, mountName, mountDesc);
        int newMountWrites = countMountWrites(patched, mountName, mountDesc);
        expect(oldMountWrites == 1, "original method has one native mount write");
        expect(newMountWrites == 0, "patched method has no native mount write");
        expect("3".equals(symbols.getProperty("removed.instructions")), "catalog records three removed executable instructions");
        expect(countMountWrites(original, mountName, mountDesc, mountOwner) == 1,
                "original support write uses the inherited mount field owner");
        expect(mountOwner.equals(required(symbols, "player.owner")), "mount access owner matches player owner");
        compareNativeMountWriterSites(before, after, mountName, mountDesc,
                required(symbols, "mount.owner"), mountOwner, supportOwner);

        String speedOwner = required(symbols, "mount.owner");
        String speedName = required(symbols, "mount.speed.method");
        String speedDesc = required(symbols, "mount.speed.descriptor");
        MethodNode oldSpeed = readMethod(before, speedOwner, speedName, speedDesc);
        MethodNode newSpeed = readMethod(after, speedOwner, speedName, speedDesc);
        expect("()I".equals(speedDesc), "native mount bonus accessor descriptor");
        Program oldProgram = new Program(symbols, original, oldSpeed);
        Program newProgram = new Program(symbols, patched, newSpeed);
        testMissingPlayerIsNoOp(newProgram);
        testZeroHpIsNoOp(newProgram);
        testRepeatedReengagePreservesMount(oldProgram, newProgram);
        testNativeBonusAccessor(oldProgram, newProgram);
        verifyZipCrc(after);
        System.out.println("LunaHorseStateHostTest PASS (" + checks + " checks)");
    }

    private static void testMissingPlayerIsNoOp(Program program) throws Exception {
        Execution run = program.newExecution((byte) -1, 0, false);
        Map<String, String> before = run.snapshot();
        run.invoke();
        expect(before.equals(run.snapshot()), "missing current player leaves native state unchanged");
    }

    private static void testZeroHpIsNoOp(Program program) throws Exception {
        for (int hp : new int[]{-1, 0}) {
            Execution run = program.newExecution((byte) 2, hp, true);
            Map<String, String> before = run.snapshot();
            run.invoke();
            expect(before.equals(run.snapshot()), "non-positive HP leaves native state unchanged: " + hp);
        }
    }

    private static void testRepeatedReengagePreservesMount(Program oldProgram, Program newProgram) throws Exception {
        byte[] states = new byte[]{-1, 0, 1, 2, 3, 7, 127};
        for (byte mount : states) {
            Execution oldRun = oldProgram.newExecution(mount, 10, true);
            Execution newRun = newProgram.newExecution(mount, 10, true);
            Map<String, String> oldStart = oldRun.snapshotWithoutMount();
            Map<String, String> newStart = newRun.snapshotWithoutMount();
            oldRun.invoke(); oldRun.invoke();
            newRun.invoke(); newRun.invoke();

            expect(oldRun.mountValue() == 1, "original bytecode reproduces forced mount=1 for " + mount);
            expect(newRun.mountValue() == mount, "patched repeated re-engage preserves mount " + mount);
            Map<String, String> oldResult = oldRun.snapshotWithoutMount();
            Map<String, String> newResult = newRun.snapshotWithoutMount();
            expect(oldResult.equals(newResult), "all non-mount state changes are retained for mount " + mount);
            expect(!oldStart.equals(oldRun.snapshotWithoutMount()), "re-engage still changes native combat state for mount " + mount);
            expect(!newStart.equals(newRun.snapshotWithoutMount()), "patched re-engage still changes native combat state for mount " + mount);
        }
    }

    private static void testNativeBonusAccessor(Program oldProgram, Program newProgram) throws Exception {
        byte[] bonuses = new byte[]{11, 23, 47};
        for (byte mount : new byte[]{-1, 0, 1, 2}) {
            Execution oldRun = oldProgram.newExecution(mount, 10, true, bonuses);
            Execution newRun = newProgram.newExecution(mount, 10, true, bonuses);
            int expected = mount < 0 ? 0 : bonuses[mount];
            expect(oldRun.bonusValue() == expected, "original native bonus accessor for mount " + mount);
            expect(newRun.bonusValue() == expected, "patched native bonus accessor for mount " + mount);
            oldRun.invoke(); oldRun.invoke();
            newRun.invoke(); newRun.invoke();
            expect(newRun.bonusValue() == expected, "re-engage preserves native bonus lookup for mount " + mount);
            expect(oldRun.bonusValue() == (mount == 1 ? expected : bonuses[1]),
                    "original forced mount changes native bonus lookup for mount " + mount);
        }
    }

    private static void compareNativeMountWriterSites(File before, File after, String mountName,
                                                       String mountDesc, String entityOwner,
                                                       String accessOwner, String supportOwner) throws Exception {
        List<String> oldSites = nativeMountWriterSites(before, mountName, mountDesc, entityOwner, accessOwner, supportOwner);
        List<String> newSites = nativeMountWriterSites(after, mountName, mountDesc, entityOwner, accessOwner, supportOwner);
        expect(!oldSites.isEmpty(), "native server mount writers exist outside support");
        expect(oldSites.equals(newSites), "native server mount writer sites are unchanged");
    }

    private static List<String> nativeMountWriterSites(File jarFile, String mountName, String mountDesc,
                                                         String entityOwner, String accessOwner,
                                                         String supportOwner) throws Exception {
        List<String> sites = new ArrayList<String>();
        JarFile jar = new JarFile(jarFile);
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode owner = readClass(readAll(jar.getInputStream(entry)));
                if (supportOwner.equals(owner.name)) continue;
                for (MethodNode method : owner.methods) {
                    int occurrence = 0;
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (!(insn instanceof FieldInsnNode)) continue;
                        FieldInsnNode field = (FieldInsnNode) insn;
                        if ((field.owner.equals(entityOwner) || field.owner.equals(accessOwner))
                                && (field.getOpcode() == PUTFIELD || field.getOpcode() == PUTSTATIC)
                                && mountName.equals(field.name) && mountDesc.equals(field.desc)) {
                            sites.add(owner.name + "." + method.name + method.desc + "#" + occurrence++
                                    + "#" + field.getOpcode() + "#" + field.owner);
                        }
                    }
                }
            }
        } finally { jar.close(); }
        Collections.sort(sites);
        return sites;
    }

    private static int countMountWrites(MethodNode method, String mountName, String mountDesc) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == PUTFIELD) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (mountName.equals(field.name) && mountDesc.equals(field.desc)) count++;
            }
        }
        return count;
    }

    private static int countMountWrites(MethodNode method, String mountName, String mountDesc, String owner) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == PUTFIELD) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (owner.equals(field.owner) && mountName.equals(field.name) && mountDesc.equals(field.desc)) count++;
            }
        }
        return count;
    }

    private static MethodNode readMethod(File jarFile, String owner, String name, String desc) throws Exception {
        JarFile jar = new JarFile(jarFile);
        try {
            JarEntry entry = jar.getJarEntry(owner + ".class");
            if (entry == null) throw new IllegalStateException("Missing support class " + owner);
            ClassNode node = readClass(readAll(jar.getInputStream(entry)));
            for (MethodNode method : node.methods) {
                if (name.equals(method.name) && desc.equals(method.desc)) return method;
            }
            throw new IllegalStateException("Missing method " + owner + "." + name + desc);
        } finally { jar.close(); }
    }

    private static final class Program {
        final String mountName, mountDesc, playerOwner;
        final String currentKey, hpKey, tableKey;
        final Map<String, FieldSpec> specs = new TreeMap<String, FieldSpec>();
        final Map<String, String> generatedNames = new HashMap<String, String>();
        final byte[] stubBytes, playerBytes, extractedBytes;

        Program(Properties symbols, MethodNode source, MethodNode speedSource) throws Exception {
            this.mountName = required(symbols, "mount.field");
            this.mountDesc = required(symbols, "mount.descriptor");
            this.playerOwner = required(symbols, "player.owner");
            String currentOwner = required(symbols, "player.current.owner");
            String currentName = required(symbols, "player.current.field");
            String currentDesc = required(symbols, "player.current.descriptor");
            this.currentKey = staticKey(currentOwner, currentName, currentDesc);
            this.hpKey = findHpKey(source);
            String tableOwner = required(symbols, "mount.speed.table.owner");
            String tableName = required(symbols, "mount.speed.table.field");
            String tableDesc = required(symbols, "mount.speed.table.descriptor");
            this.tableKey = staticKey(tableOwner, tableName, tableDesc);

            addMethodFields(source);
            addMethodFields(speedSource);
            addSpec(currentKey, true, currentOwner, currentName, currentDesc);
            addSpec(tableKey, true, tableOwner, tableName, tableDesc);
            addSpec(MOUNT_KEY, false, required(symbols, "mount.access.owner"), mountName, mountDesc);
            int ordinal = 0;
            for (String key : specs.keySet()) generatedNames.put(key, MOUNT_KEY.equals(key) ? "mount" : "f" + ordinal++);

            this.stubBytes = makeStubClass();
            this.playerBytes = makePlayerClass();
            this.extractedBytes = makeExtractedClass(source, speedSource);
        }

        private void addMethodFields(MethodNode source) {
            for (AbstractInsnNode insn : source.instructions.toArray()) {
                if (!(insn instanceof FieldInsnNode)) continue;
                FieldInsnNode field = (FieldInsnNode) insn;
                int opcode = field.getOpcode();
                boolean isStatic = opcode == GETSTATIC || opcode == PUTSTATIC;
                if (isStatic) addSpec(staticKey(field.owner, field.name, field.desc), true, field.owner, field.name, field.desc);
                else if (mountName.equals(field.name) && mountDesc.equals(field.desc)) continue;
                else addSpec(instanceKey(field.owner, field.name, field.desc), false, field.owner, field.name, field.desc);
            }
        }

        Execution newExecution(byte mount, int hp, boolean hasPlayer) throws Exception {
            return newExecution(mount, hp, hasPlayer, new byte[]{11, 23, 47});
        }

        Execution newExecution(byte mount, int hp, boolean hasPlayer, byte[] bonuses) throws Exception {
            Map<String, byte[]> classes = new HashMap<String, byte[]>();
            classes.put(STUB.replace('/', '.'), stubBytes);
            classes.put(PLAYER.replace('/', '.'), playerBytes);
            classes.put(EXTRACTED.replace('/', '.'), extractedBytes);
            ByteLoader loader = new ByteLoader(classes);
            Class<?> stubType = loader.loadClass(STUB.replace('/', '.'));
            Class<?> playerType = loader.loadClass(PLAYER.replace('/', '.'));
            Class<?> methodType = loader.loadClass(EXTRACTED.replace('/', '.'));
            Object player = playerType.getDeclaredConstructor().newInstance();

            Field mountField = reflected(MOUNT_KEY, stubType, playerType);
            mountField.setByte(player, mount);
            Field hpField = reflected(hpKey, stubType, playerType);
            expect("I".equals(hpField.getType() == Integer.TYPE ? "I" : ""), "HP guard maps to an int field");
            hpField.setInt(player, hp);
            Field currentField = reflected(currentKey, stubType, playerType);
            expect(currentField.getType() == playerType, "catalog current-player field maps to host player stub");
            currentField.set(null, hasPlayer ? player : null);
            Field tableField = reflected(tableKey, stubType, playerType);
            expect(tableField.getType() == byte[].class, "catalog bonus table maps to byte array");
            tableField.set(null, bonuses.clone());

            Method reengage = methodType.getMethod("reengage");
            Method bonus = methodType.getMethod("horseBonus", playerType);
            return new Execution(this, stubType, playerType, player, reengage, bonus);
        }

        Field reflected(String key, Class<?> stubType, Class<?> playerType) throws Exception {
            FieldSpec spec = specs.get(key);
            if (spec == null) throw new IllegalStateException("No extracted field for " + key);
            Field field = (spec.isStatic ? stubType : playerType).getDeclaredField(generatedNames.get(key));
            field.setAccessible(true);
            return field;
        }

        private byte[] makeStubClass() {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(V1_4, ACC_PUBLIC | ACC_SUPER, STUB, null, "java/lang/Object", null);
            writer.visitField(ACC_PUBLIC | ACC_STATIC, "clock", "J", null, null).visitEnd();
            for (FieldSpec spec : specs.values()) {
                if (!spec.isStatic) continue;
                writer.visitField(ACC_PUBLIC | ACC_STATIC, generatedNames.get(spec.key), remapDescriptor(spec.desc), null, null).visitEnd();
            }
            writer.visitEnd();
            return writer.toByteArray();
        }

        private byte[] makePlayerClass() {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(V1_4, ACC_PUBLIC | ACC_SUPER, PLAYER, null, "java/lang/Object", null);
            MethodVisitor init = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            init.visitCode(); init.visitVarInsn(ALOAD, 0); init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            init.visitInsn(RETURN); init.visitMaxs(0, 0); init.visitEnd();
            for (FieldSpec spec : specs.values()) {
                if (spec.isStatic) continue;
                writer.visitField(ACC_PUBLIC, generatedNames.get(spec.key), remapDescriptor(spec.desc), null, null).visitEnd();
            }
            writer.visitEnd();
            return writer.toByteArray();
        }

        private byte[] makeExtractedClass(MethodNode source, MethodNode speedSource) {
            MethodNode copied = copyMethod(source, "reengage", "()V");
            String speedDesc = (speedSource.access & ACC_STATIC) == 0 ? "(L" + PLAYER + ";)I" : speedSource.desc;
            MethodNode copiedSpeed = copyMethod(speedSource, "horseBonus", speedDesc);
            remapInstructions(copied);
            remapInstructions(copiedSpeed);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(V1_4, ACC_PUBLIC | ACC_SUPER, EXTRACTED, null, "java/lang/Object", null);
            copied.accept(writer);
            copiedSpeed.accept(writer);
            writer.visitEnd();
            return writer.toByteArray();
        }

        private MethodNode copyMethod(MethodNode source, String name, String desc) {
            MethodNode copied = new MethodNode(ACC_PUBLIC | ACC_STATIC, name, desc, null, null);
            source.accept(copied);
            copied.access = ACC_PUBLIC | ACC_STATIC;
            copied.name = name;
            copied.desc = desc;
            return copied;
        }

        private void remapInstructions(MethodNode copied) {
            for (AbstractInsnNode insn : copied.instructions.toArray()) {
                if (insn instanceof FrameNode) {
                    copied.instructions.remove(insn);
                } else if (insn instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) insn;
                    boolean isStatic = field.getOpcode() == GETSTATIC || field.getOpcode() == PUTSTATIC;
                    String key = isStatic ? staticKey(field.owner, field.name, field.desc)
                            : (mountName.equals(field.name) && mountDesc.equals(field.desc)
                                    ? MOUNT_KEY : instanceKey(field.owner, field.name, field.desc));
                    String targetOwner = isStatic ? STUB : PLAYER;
                    field.owner = targetOwner;
                    field.name = generatedNames.get(key);
                    field.desc = remapDescriptor(field.desc);
                } else if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if ("java/lang/System".equals(call.owner) && "currentTimeMillis".equals(call.name)
                            && "()J".equals(call.desc) && call.getOpcode() == INVOKESTATIC) {
                        copied.instructions.set(call, new FieldInsnNode(GETSTATIC, STUB, "clock", "J"));
                    } else {
                        throw new IllegalStateException("Unexpected call in extracted method: " + call.owner + "." + call.name + call.desc);
                    }
                } else if (insn instanceof TypeInsnNode) {
                    TypeInsnNode type = (TypeInsnNode) insn;
                    if (playerOwner.equals(type.desc)) type.desc = PLAYER;
                }
            }
        }

        private String remapDescriptor(String desc) {
            if (desc.equals("L" + playerOwner + ";")) return "L" + PLAYER + ";";
            if (desc.startsWith("[")) {
                int dimensions = 0;
                while (dimensions < desc.length() && desc.charAt(dimensions) == '[') dimensions++;
                if (dimensions < desc.length() && desc.charAt(dimensions) == 'L') {
                    StringBuilder mapped = new StringBuilder();
                    for (int i = 0; i < dimensions; i++) mapped.append('[');
                    return mapped.append('L').append(PLAYER).append(';').toString();
                }
                return desc;
            }
            if (desc.startsWith("L")) return "Ljava/lang/Object;";
            return desc;
        }

        private void addSpec(String key, boolean isStatic, String owner, String name, String desc) {
            if (!specs.containsKey(key)) specs.put(key, new FieldSpec(key, isStatic, owner, name, desc));
        }
    }

    private static final class Execution {
        final Program program;
        final Class<?> stubType, playerType;
        final Object player;
        final Method method, bonus;

        Execution(Program program, Class<?> stubType, Class<?> playerType, Object player, Method method, Method bonus) {
            this.program = program; this.stubType = stubType; this.playerType = playerType;
            this.player = player; this.method = method; this.bonus = bonus;
        }

        void invoke() throws Exception {
            try { method.invoke(null); }
            catch (InvocationTargetException e) { throw new AssertionError("Extracted native method failed", e.getCause()); }
        }

        byte mountValue() throws Exception { return program.reflected(MOUNT_KEY, stubType, playerType).getByte(player); }
        int bonusValue() throws Exception {
            try { return ((Integer) bonus.invoke(null, player)).intValue(); }
            catch (InvocationTargetException e) { throw new AssertionError("Extracted native bonus accessor failed", e.getCause()); }
        }

        Map<String, String> snapshot() throws Exception { return snapshot(false); }
        Map<String, String> snapshotWithoutMount() throws Exception { return snapshot(true); }

        private Map<String, String> snapshot(boolean omitMount) throws Exception {
            Map<String, String> values = new TreeMap<String, String>();
            for (FieldSpec spec : program.specs.values()) {
                if (omitMount && MOUNT_KEY.equals(spec.key)) continue;
                Field field = program.reflected(spec.key, stubType, playerType);
                Object value = field.get(spec.isStatic ? null : player);
                String rendered;
                if (value == null) rendered = "<null>";
                else if (value == player) rendered = "<player>";
                else if (value.getClass().isArray()) rendered = "<array:" + value.getClass().getName() + ">";
                else rendered = String.valueOf(value);
                values.put(spec.key, rendered);
            }
            return values;
        }
    }

    private static final class FieldSpec {
        final String key, owner, name, desc;
        final boolean isStatic;
        FieldSpec(String key, boolean isStatic, String owner, String name, String desc) {
            this.key = key; this.isStatic = isStatic; this.owner = owner; this.name = name; this.desc = desc;
        }
    }

    private static final class ByteLoader extends ClassLoader {
        final Map<String, byte[]> classes;
        ByteLoader(Map<String, byte[]> classes) { super(LunaHorseStateHostTest.class.getClassLoader()); this.classes = classes; }
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) return super.findClass(name);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static String findHpKey(MethodNode method) {
        AbstractInsnNode[] code = method.instructions.toArray();
        for (int i = 0; i + 1 < code.length; i++) {
            if (code[i] instanceof FieldInsnNode && code[i].getOpcode() == GETFIELD
                    && "I".equals(((FieldInsnNode) code[i]).desc)) {
                AbstractInsnNode next = nextReal(code[i]);
                if (next instanceof JumpInsnNode && next.getOpcode() == IFGT) {
                    FieldInsnNode field = (FieldInsnNode) code[i];
                    return instanceKey(field.owner, field.name, field.desc);
                }
            }
        }
        throw new IllegalStateException("Cannot locate the positive-HP guard in re-engage method");
    }

    private static String staticKey(String owner, String name, String desc) { return "S|" + owner + "|" + name + "|" + desc; }
    private static String instanceKey(String owner, String name, String desc) { return "I|" + owner + "|" + name + "|" + desc; }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn.getNext();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getNext();
        return cursor;
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node;
    }

    private static Properties loadProperties(File file) throws Exception {
        Properties properties = new Properties(); InputStream in = new FileInputStream(file);
        try { properties.load(in); } finally { in.close(); }
        return properties;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.length() == 0) throw new IllegalArgumentException("Missing symbol " + key);
        return value;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); InputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        } finally { in.close(); }
        StringBuilder value = new StringBuilder();
        for (byte b : digest.digest()) value.append(String.format("%02X", Integer.valueOf(b & 0xff)));
        return value.toString();
    }

    private static void verifyZipCrc(File file) throws Exception {
        JarInputStream jar = new JarInputStream(new FileInputStream(file));
        try {
            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) readAll(jar);
        } finally { jar.close(); }
        expect(true, "patched archive CRC");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        return out.toByteArray();
    }

    private static void expect(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
