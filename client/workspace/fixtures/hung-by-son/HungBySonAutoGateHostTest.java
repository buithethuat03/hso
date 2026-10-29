import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Executes the real initialized automatic-combat gate in isolated JAR loaders. */
public final class HungBySonAutoGateHostTest implements Opcodes {
    public static long now;
    private static int checks;

    private HungBySonAutoGateHostTest() { }

    public static void main(String[] args) throws Exception {
        require(args.length == 4 || args.length == 6, "<input.jar> <candidate.jar> <combat.properties> <microemulator.jar> [before-ms after-ms]");
        int beforeMs = args.length == 6 ? Integer.parseInt(args[4]) : 400;
        int afterMs = args.length == 6 ? Integer.parseInt(args[5]) : 500;
        File input = new File(args[0]);
        File candidate = new File(args[1]);
        File symbolsFile = new File(args[2]);
        File microEmulator = new File(args[3]);
        require(input.isFile() && candidate.isFile(), "input and candidate JARs exist");
        require(microEmulator.isFile(), "MicroEmulator dependency exists");
        Properties symbols = load(symbolsFile);

        String castOwner = propertyAny(symbols, "auto.gate.owner", "cast.owner");
        String requestOwner = property(symbols, "request.owner");
        String requestName = property(symbols, "request.method");
        String requestDescriptor = property(symbols, "request.descriptor");
        assertUnchangedScope(input, candidate, castOwner, requestOwner, requestName, requestDescriptor);
        testGate(input, microEmulator, symbols, castOwner, beforeMs,
                new int[]{beforeMs - 1, beforeMs, beforeMs + 1}, new boolean[]{false, false, true}, "input " + beforeMs + "ms");
        testGate(candidate, microEmulator, symbols, castOwner, afterMs,
                new int[]{afterMs - 1, afterMs, afterMs + 1, beforeMs + 1, afterMs, afterMs + 1},
                new boolean[]{false, false, true, beforeMs + 1 > afterMs, false, true}, "candidate " + afterMs + "ms");
        System.out.println("HungBySonAutoGateHostTest PASS (" + checks + " checks)");
    }

    private static void testGate(File jar, File microEmulator, Properties symbols, String owner,
            int expectedInterval, int[] elapsedCases, boolean[] expected, String side) throws Exception {
        String gateName = property(symbols, "auto.gate.method");
        String gateDescriptor = optionalProperty(symbols, "auto.gate.descriptor", "()Z");
        String timestampOwner = optionalProperty(symbols, "auto.timestamp.owner", owner);
        String timestampName = property(symbols, "auto.timestamp.field");
        String delayOwner = optionalProperty(symbols, "auto.delay.owner", owner);
        String delayName = property(symbols, "auto.delay.field");

        ClassNode node = parse(readClass(jar, owner));
        MethodNode gate = method(node, gateName, gateDescriptor);
        require(Type.BOOLEAN_TYPE.equals(Type.getReturnType(gateDescriptor))
                && Type.getArgumentTypes(gateDescriptor).length == 0
                && (gate.access & ACC_STATIC) != 0, side + " mapped e() is static ()Z");
        int clockReads = replaceClock(gate);
        require(clockReads == 1, side + " actual gate has one redirected clock read");
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        final byte[] patched = writer.toByteArray();
        final String binaryName = owner.replace('/', '.');
        URL[] urls = new URL[]{jar.toURI().toURL(), microEmulator.toURI().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls, HungBySonAutoGateHostTest.class.getClassLoader()) {
            protected Class<?> findClass(String requested) throws ClassNotFoundException {
                if (requested.equals(binaryName)) return defineClass(requested, patched, 0, patched.length);
                return super.findClass(requested);
            }
        }) {
            Class<?> cast = Class.forName(binaryName, true, loader);
            Field delay = declaredField(type(loader, delayOwner), delayName);
            require(Modifier.isStatic(delay.getModifiers()) && delay.getType() == Integer.TYPE
                    && delay.getInt(null) == expectedInterval,
                    side + " initialized auto gate interval is " + expectedInterval + "ms");
            Field timestamp = declaredField(type(loader, timestampOwner), timestampName);
            require(Modifier.isStatic(timestamp.getModifiers()) && timestamp.getType() == Long.TYPE,
                    side + " auto timestamp is static long");
            Method ready = declaredMethod(cast, gateName, gateDescriptor);
            long baseline = 100000L;
            for (int i = 0; i < elapsedCases.length; i++) {
                setLong(timestamp, baseline);
                now = baseline + elapsedCases[i];
                boolean result = ((Boolean)ready.invoke(null)).booleanValue();
                require(result == expected[i], side + " strict boundary elapsed=" + elapsedCases[i]
                        + " threshold=" + expectedInterval + " result=" + result);
            }
        }
    }

    private static void assertUnchangedScope(File input, File candidate, String castOwner,
            String requestOwner, String requestName, String requestDescriptor) throws Exception {
        ClassNode oldCast = parse(readClass(input, castOwner));
        ClassNode newCast = parse(readClass(candidate, castOwner));
        require(oldCast.methods.size() == newCast.methods.size(), "cast method count unchanged");
        int unchanged = 0;
        for (MethodNode before : oldCast.methods) {
            MethodNode after = findMethod(newCast, before.name, before.desc);
            require(after != null && before.access == after.access,
                    "cast method declaration retained: " + before.name + before.desc);
            if ("<clinit>".equals(before.name)) continue;
            require(Arrays.equals(methodBytes(oldCast, before), methodBytes(newCast, after)),
                    "cast method unchanged: " + before.name + before.desc);
            unchanged++;
        }
        require(unchanged > 0, "cast methods outside <clinit> were compared");

        ClassNode oldRequest = parse(readClass(input, requestOwner));
        ClassNode newRequest = parse(readClass(candidate, requestOwner));
        MethodNode beforeRequest = method(oldRequest, requestName, requestDescriptor);
        MethodNode afterRequest = method(newRequest, requestName, requestDescriptor);
        require(Arrays.equals(methodBytes(oldRequest, beforeRequest), methodBytes(newRequest, afterRequest)),
                "native request method canonical bytes unchanged");
    }

    private static byte[] methodBytes(ClassNode owner, MethodNode method) {
        ClassWriter writer = new ClassWriter(0);
        String[] interfaces = owner.interfaces.toArray(new String[owner.interfaces.size()]);
        writer.visit(owner.version, owner.access, owner.name, owner.signature, owner.superName, interfaces);
        MethodVisitor visitor = writer.visitMethod(method.access, method.name, method.desc,
                method.signature, method.exceptions == null ? null
                        : method.exceptions.toArray(new String[method.exceptions.size()]));
        method.accept(visitor);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static int replaceClock(MethodNode method) {
        int reads = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.getOpcode() == INVOKESTATIC && "java/lang/System".equals(call.owner)
                        && "currentTimeMillis".equals(call.name) && "()J".equals(call.desc)) {
                    method.instructions.set(instruction, new FieldInsnNode(GETSTATIC,
                            "HungBySonAutoGateHostTest", "now", "J"));
                    reads++;
                }
            }
            instruction = next;
        }
        return reads;
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

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
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
        MethodNode found = findMethod(owner, name, descriptor);
        if (found == null) throw new IllegalStateException("method missing: " + owner.name + "." + name + descriptor);
        return found;
    }

    private static MethodNode findMethod(ClassNode owner, String name, String descriptor) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        }
        return null;
    }

    private static Properties load(File file) throws Exception {
        require(file.isFile(), "combat symbols exist");
        Properties properties = new Properties();
        try (InputStream in = new FileInputStream(file)) { properties.load(in); }
        return properties;
    }

    private static String property(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) throw new IllegalStateException("catalog entry missing: " + key);
        return value.trim();
    }

    private static String propertyAny(Properties properties, String first, String second) {
        String value = properties.getProperty(first);
        if (value == null) value = properties.getProperty(second);
        if (value == null) throw new IllegalStateException("catalog entries missing: " + first + " or " + second);
        return value.trim();
    }

    private static String optionalProperty(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : value.trim();
    }

    private static void setLong(Field field, long value) throws Exception {
        if (field.getType() == Long.TYPE) field.setLong(null, value);
        else if (field.getType() == Integer.TYPE) field.setInt(null, (int)value);
        else throw new IllegalStateException("unsupported auto timestamp field: " + field);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
