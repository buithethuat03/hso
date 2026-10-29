import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Executes the original native combat timing gates from the supplied JARs. */
public final class HungBySonNativeTimingHostTest implements Opcodes {
    private static final String ORIGINAL_SHA256 =
            "8BC1B4B85E602FC3554DB9415B343003755C5D750D0922D632309BF5E9FCCCF4";
    private static final String HOST = "HungBySonNativeTimingHostTest";

    public static long now;
    public static long timestamp;
    public static int interval;
    private static int checks;

    private HungBySonNativeTimingHostTest() { }

    public static void main(String[] args) throws Exception {
        require(args.length == 4,
                "<original-reference.jar> <candidate.jar> <symbols.properties> <microemulator.jar>");
        File original = new File(args[0]);
        File candidate = new File(args[1]);
        File symbolsFile = new File(args[2]);
        File microEmulator = new File(args[3]);
        require(original.isFile() && candidate.isFile(), "original reference and candidate JARs exist");
        require(symbolsFile.isFile(), "timing symbol catalog exists");
        require(microEmulator.isFile(), "MicroEmulator dependency exists");
        require(ORIGINAL_SHA256.equals(sha256(original)), "unexpected original pre-cast reference hash");

        Properties symbols = load(symbolsFile);
        validateSymbols(symbols);
        String gateOwner = property(symbols, "auto.gate.owner");
        String requestOwner = property(symbols, "request.owner");
        require(gateOwner.equals(requestOwner), "mapped auto and request methods share the combat class");

        String originalAuto = testAutoGate(original, microEmulator, symbols, "original");
        String candidateAuto = testAutoGate(candidate, microEmulator, symbols, "candidate");
        require(originalAuto.equals(candidateAuto), "original and candidate auto-gate traces match");

        RequestWindow originalWindow = extractRequestWindow(original, symbols, "original");
        RequestWindow candidateWindow = extractRequestWindow(candidate, symbols, "candidate");
        require(originalWindow.signature.equals(candidateWindow.signature),
                "candidate request comparison bytecode matches the original dynamic comparison");
        require(!hasFixed300Addition(candidateWindow),
                "candidate request comparison contains no SIPUSH 300 / IADD override");
        testRequestGate(originalWindow, candidateWindow);

        System.out.println("AUTO_GATE=initialized 600ms; strict >600 PASS");
        System.out.println("REQUEST_GATE=dynamic native interval; strict >interval PASS");
        System.out.println("REQUEST_COMPARISON=identical to original; fixed +300 absent");
        System.out.println("HungBySonNativeTimingHostTest PASS (" + checks + " checks)");
    }

    private static void validateSymbols(Properties symbols) {
        require("I".equals(property(symbols, "auto.delay.descriptor")), "auto delay is int");
        require("()Z".equals(property(symbols, "auto.gate.descriptor")), "auto gate is ()Z");
        require("J".equals(property(symbols, "auto.timestamp.descriptor")), "auto timestamp is long");
        require("()V".equals(property(symbols, "request.descriptor")), "request method is ()V");
        require("J".equals(property(symbols, "request.now.descriptor")), "request clock is long");
        require("J".equals(property(symbols, "request.timestamp.descriptor")), "request timestamp is long");
        require("S".equals(property(symbols, "request.delay.descriptor")), "native interval is short");
    }

    private static String testAutoGate(File jar, File microEmulator, Properties symbols, String side)
            throws Exception {
        String owner = property(symbols, "auto.gate.owner");
        String methodName = property(symbols, "auto.gate.method");
        String descriptor = property(symbols, "auto.gate.descriptor");
        String delayOwner = property(symbols, "auto.delay.owner");
        String delayName = property(symbols, "auto.delay.field");
        String timestampOwner = property(symbols, "auto.timestamp.owner");
        String timestampName = property(symbols, "auto.timestamp.field");

        ClassNode node = parse(readClass(jar, owner));
        MethodNode gate = method(node, methodName, descriptor);
        require((gate.access & ACC_STATIC) != 0 && Type.BOOLEAN_TYPE.equals(Type.getReturnType(descriptor))
                && Type.getArgumentTypes(descriptor).length == 0, side + " mapped auto gate is static ()Z");
        require(replaceClock(gate) == 1, side + " actual auto gate has one redirected clock read");
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        final byte[] patched = writer.toByteArray();
        final String binaryName = owner.replace('/', '.');
        URL[] urls = new URL[]{jar.toURI().toURL(), microEmulator.toURI().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls, HungBySonNativeTimingHostTest.class.getClassLoader()) {
            protected Class<?> findClass(String requested) throws ClassNotFoundException {
                if (requested.equals(binaryName)) return defineClass(requested, patched, 0, patched.length);
                return super.findClass(requested);
            }
        }) {
            Class<?> combat = Class.forName(binaryName, true, loader);
            Field delay = declaredField(type(loader, delayOwner), delayName);
            require(Modifier.isStatic(delay.getModifiers()) && delay.getType() == Integer.TYPE
                    && delay.getInt(null) == 600, side + " initialized auto gate is 600ms");
            Field previous = declaredField(type(loader, timestampOwner), timestampName);
            require(Modifier.isStatic(previous.getModifiers()) && previous.getType() == Long.TYPE,
                    side + " auto timestamp is static long");
            Method ready = declaredMethod(combat, methodName, descriptor);
            long baseline = 100000L;
            StringBuilder trace = new StringBuilder();
            for (int elapsed : new int[]{599, 600, 601}) {
                setLong(previous, baseline);
                now = baseline + elapsed;
                boolean result = ((Boolean)ready.invoke(null)).booleanValue();
                require(result == (elapsed > 600), side + " auto gate strict boundary elapsed=" + elapsed);
                trace.append(elapsed).append(':').append(result).append(';');
            }
            return trace.toString();
        }
    }

    private static RequestWindow extractRequestWindow(File jar, Properties symbols, String side)
            throws Exception {
        String owner = property(symbols, "request.owner");
        String methodName = property(symbols, "request.method");
        String descriptor = property(symbols, "request.descriptor");
        String clockOwner = property(symbols, "request.now.owner");
        String clockName = property(symbols, "request.now.field");
        String timestampOwner = property(symbols, "request.timestamp.owner");
        String timestampName = property(symbols, "request.timestamp.field");
        String delayOwner = property(symbols, "request.delay.owner");
        String delayName = property(symbols, "request.delay.field");
        String clockDescriptor = property(symbols, "request.now.descriptor");
        String timestampDescriptor = property(symbols, "request.timestamp.descriptor");
        String delayDescriptor = property(symbols, "request.delay.descriptor");

        ClassNode node = parse(readClass(jar, owner));
        MethodNode source = method(node, methodName, descriptor);
        List<AbstractInsnNode> code = realInstructions(source);
        RequestWindow found = null;
        int matches = 0;
        for (int start = 0; start < code.size(); start++) {
            if (!isStaticField(code.get(start), clockOwner, clockName, clockDescriptor)) continue;
            int timestampIndex = findStaticField(code, start + 1, timestampOwner, timestampName,
                    timestampDescriptor, 8);
            if (timestampIndex < 0 || timestampIndex != start + 1
                    || code.get(timestampIndex + 1).getOpcode() != LSUB) continue;
            int delayIndex = findStaticField(code, timestampIndex + 1, delayOwner, delayName,
                    delayDescriptor, 10);
            if (delayIndex < 0) continue;
            int branchIndex = -1;
            for (int cursor = delayIndex + 1; cursor < code.size() && cursor <= delayIndex + 6; cursor++) {
                int opcode = code.get(cursor).getOpcode();
                if (opcode == LCMP) continue;
                if (opcode == IFLE || opcode == IF_ICMPLE || opcode == IF_ICMPLT) {
                    branchIndex = cursor;
                    break;
                }
            }
            if (branchIndex < 0 || branchIndex <= delayIndex + 1) continue;
            int comparisonCount = 0;
            for (int cursor = delayIndex + 1; cursor < branchIndex; cursor++) {
                if (code.get(cursor).getOpcode() == LCMP) comparisonCount++;
            }
            if (comparisonCount != 1) continue;
            matches++;
            found = new RequestWindow(code, start, timestampIndex, delayIndex, branchIndex,
                    clockOwner, clockName, clockDescriptor, timestampOwner, timestampName,
                    timestampDescriptor, delayOwner, delayName, delayDescriptor);
        }
        require(matches == 1 && found != null, side + " exactly one mapped dynamic request comparison exists");
        require(found.code.get(found.branchIndex).getOpcode() == IFLE,
                side + " request timing uses long comparison with strict greater-than semantics");
        require(found.code.get(found.delayIndex + 1).getOpcode() == I2L
                && found.code.get(found.delayIndex + 2).getOpcode() == LCMP,
                side + " request comparison converts the native short interval directly to long");
        return found;
    }

    private static void testRequestGate(RequestWindow original, RequestWindow candidate) throws Exception {
        Method originalReady = buildProbe(original, "O");
        Method candidateReady = buildProbe(candidate, "C");
        long baseline = 100000L;
        for (int nativeInterval : new int[]{0, 100, 600, 32767}) {
            interval = nativeInterval;
            for (int elapsed : new int[]{Math.max(0, nativeInterval - 1), nativeInterval, nativeInterval + 1}) {
                timestamp = baseline;
                now = baseline + elapsed;
                boolean expected = elapsed > nativeInterval;
                boolean originalResult = ((Boolean)originalReady.invoke(null)).booleanValue();
                boolean candidateResult = ((Boolean)candidateReady.invoke(null)).booleanValue();
                require(originalResult == expected, "original request gate strict boundary interval="
                        + nativeInterval + " elapsed=" + elapsed);
                require(candidateResult == expected, "candidate request gate strict boundary interval="
                        + nativeInterval + " elapsed=" + elapsed);
                require(candidateResult == originalResult, "candidate request result matches original");
            }
        }
    }

    private static Method buildProbe(final RequestWindow window, String suffix) throws Exception {
        final String probeName = "HungBySonNativeRequestProbe" + suffix;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_4, ACC_PUBLIC, probeName, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "ready", "()Z", null, null);
        method.visitCode();
        for (int cursor = window.start; cursor < window.branchIndex; cursor++) {
            AbstractInsnNode instruction = window.code.get(cursor);
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (matches(field, window.clockOwner, window.clockName, window.clockDescriptor)) {
                    method.visitFieldInsn(GETSTATIC, HOST, "now", "J");
                } else if (matches(field, window.timestampOwner, window.timestampName,
                        window.timestampDescriptor)) {
                    method.visitFieldInsn(GETSTATIC, HOST, "timestamp", "J");
                } else if (matches(field, window.delayOwner, window.delayName, window.delayDescriptor)) {
                    method.visitFieldInsn(GETSTATIC, HOST, "interval", "I");
                } else instruction.accept(method);
            } else {
                require(!(instruction instanceof org.objectweb.asm.tree.JumpInsnNode),
                        "mapped request comparison expression contains no internal branch");
                instruction.accept(method);
            }
        }
        int branchOpcode = window.code.get(window.branchIndex).getOpcode();
        Label no = new Label();
        method.visitJumpInsn(branchOpcode, no);
        method.visitInsn(ICONST_1);
        method.visitInsn(IRETURN);
        method.visitLabel(no);
        method.visitInsn(ICONST_0);
        method.visitInsn(IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        final byte[] bytes = writer.toByteArray();
        Class<?> probe = new ClassLoader(HungBySonNativeTimingHostTest.class.getClassLoader()) {
            Class<?> loadProbe() { return defineClass(probeName, bytes, 0, bytes.length); }
        }.loadProbe();
        return probe.getMethod("ready");
    }

    private static boolean hasFixed300Addition(RequestWindow window) {
        for (int cursor = window.delayIndex + 1; cursor + 1 < window.branchIndex; cursor++) {
            AbstractInsnNode current = window.code.get(cursor);
            AbstractInsnNode next = window.code.get(cursor + 1);
            if (current instanceof IntInsnNode && current.getOpcode() == SIPUSH
                    && ((IntInsnNode)current).operand == 300 && next.getOpcode() == IADD) return true;
        }
        return false;
    }

    private static String signature(RequestWindow window) {
        StringBuilder result = new StringBuilder();
        for (int cursor = window.start; cursor <= window.branchIndex; cursor++) {
            AbstractInsnNode instruction = window.code.get(cursor);
            result.append(canonical(instruction, window)).append(';');
        }
        return result.toString();
    }

    private static String canonical(AbstractInsnNode instruction, RequestWindow window) {
        int opcode = instruction.getOpcode();
        if (instruction instanceof FieldInsnNode) {
            FieldInsnNode field = (FieldInsnNode)instruction;
            if (matches(field, window.clockOwner, window.clockName, window.clockDescriptor))
                return opcode + " FIELD NOW";
            if (matches(field, window.timestampOwner, window.timestampName, window.timestampDescriptor))
                return opcode + " FIELD TIMESTAMP";
            if (matches(field, window.delayOwner, window.delayName, window.delayDescriptor))
                return opcode + " FIELD INTERVAL";
            return opcode + " FIELD " + field.owner + '.' + field.name + ':' + field.desc;
        }
        if (instruction instanceof IntInsnNode)
            return opcode + " INT " + ((IntInsnNode)instruction).operand;
        if (instruction instanceof VarInsnNode)
            return opcode + " VAR " + ((VarInsnNode)instruction).var;
        return opcode + " " + instruction.getType();
    }

    private static int replaceClock(MethodNode method) {
        int reads = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.getOpcode() == INVOKESTATIC && "java/lang/System".equals(call.owner)
                        && "currentTimeMillis".equals(call.name) && "()J".equals(call.desc)) {
                    method.instructions.set(instruction, new FieldInsnNode(GETSTATIC, HOST, "now", "J"));
                    reads++;
                }
            }
            instruction = next;
        }
        return reads;
    }

    private static int findStaticField(List<AbstractInsnNode> code, int start, String owner, String name,
            String descriptor, int distance) {
        int end = Math.min(code.size(), start + distance + 1);
        for (int cursor = start; cursor < end; cursor++) {
            if (isStaticField(code.get(cursor), owner, name, descriptor)) return cursor;
        }
        return -1;
    }

    private static boolean isStaticField(AbstractInsnNode instruction, String owner, String name,
            String descriptor) {
        return instruction instanceof FieldInsnNode && instruction.getOpcode() == GETSTATIC
                && matches((FieldInsnNode)instruction, owner, name, descriptor);
    }

    private static boolean matches(FieldInsnNode field, String owner, String name, String descriptor) {
        return field.getOpcode() == GETSTATIC && owner.equals(field.owner) && name.equals(field.name)
                && descriptor.equals(field.desc);
    }

    private static List<AbstractInsnNode> realInstructions(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction : method.instructions.toArray())
            if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }

    private static ClassNode parse(byte[] bytes) {
        ClassNode node = new ClassNode(ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] readClass(File jarFile, String internalName) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry(internalName + ".class");
            if (entry == null) throw new IllegalStateException("class missing from JAR: " + internalName);
            return readAll(jar.getInputStream(entry));
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }

    private static Class<?> type(ClassLoader loader, String owner) throws Exception {
        return Class.forName(owner.replace('/', '.'), true, loader);
    }

    private static Field declaredField(Class<?> owner, String name) throws Exception {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static Method declaredMethod(Class<?> owner, String name, String descriptor) throws Exception {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor)) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name + descriptor);
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        for (MethodNode method : owner.methods)
            if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        throw new IllegalStateException("method missing: " + owner.name + "." + name + descriptor);
    }

    private static Properties load(File file) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(file)) { properties.load(input); }
        return properties;
    }

    private static String property(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) throw new IllegalStateException("catalog entry missing: " + key);
        return value.trim();
    }

    private static void setLong(Field field, long value) throws Exception {
        field.setLong(null, value);
    }

    private static String sha256(File file) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder result = new StringBuilder();
        for (byte value : hash) result.append(String.format("%02X", Integer.valueOf(value & 255)));
        return result.toString();
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class RequestWindow {
        final List<AbstractInsnNode> code;
        final int start;
        final int timestampIndex;
        final int delayIndex;
        final int branchIndex;
        final String clockOwner, clockName, clockDescriptor;
        final String timestampOwner, timestampName, timestampDescriptor;
        final String delayOwner, delayName, delayDescriptor;
        final String signature;

        RequestWindow(List<AbstractInsnNode> code, int start, int timestampIndex, int delayIndex,
                int branchIndex, String clockOwner, String clockName, String clockDescriptor,
                String timestampOwner, String timestampName, String timestampDescriptor,
                String delayOwner, String delayName, String delayDescriptor) {
            this.code = code;
            this.start = start;
            this.timestampIndex = timestampIndex;
            this.delayIndex = delayIndex;
            this.branchIndex = branchIndex;
            this.clockOwner = clockOwner;
            this.clockName = clockName;
            this.clockDescriptor = clockDescriptor;
            this.timestampOwner = timestampOwner;
            this.timestampName = timestampName;
            this.timestampDescriptor = timestampDescriptor;
            this.delayOwner = delayOwner;
            this.delayName = delayName;
            this.delayDescriptor = delayDescriptor;
            this.signature = HungBySonNativeTimingHostTest.signature(this);
        }
    }
}
