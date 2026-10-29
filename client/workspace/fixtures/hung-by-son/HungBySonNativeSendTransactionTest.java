import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Deterministic probe for the shared native message transaction used by opcode -44
 * object-detail requests and opcode-6 PK casts. It pauses the actual native network
 * boundary after the request message is handed off, runs the actual PK serializer,
 * then lets the request boundary read the message. No socket or game server is used.
 *
 * Usage: <input.jar> [transaction-fixed.jar] <auto-pk.properties>
 */
public final class HungBySonNativeSendTransactionTest {
    private static final String HOST = "HungBySonNativeSendTransactionTest";
    private static volatile CountDownLatch firstNetworkEntered;
    private static volatile CountDownLatch releaseFirstNetwork;
    private static volatile CountDownLatch secondNetworkEntered;
    private static volatile Object firstMessage;
    private static volatile boolean sameMessageObject;
    private static final List<String> captured = new ArrayList<String>();

    private HungBySonNativeSendTransactionTest() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 3) {
            throw new IllegalArgumentException("<input.jar> [transaction-fixed.jar] <auto-pk.properties>");
        }
        File input = new File(args[0]);
        File catalogFile = new File(args[args.length - 1]);
        Properties catalog = new Properties();
        FileInputStream in = new FileInputStream(catalogFile);
        try { catalog.load(in); } finally { in.close(); }

        List<String> before = runInterleaving(input, catalog);
        String request = "-44:[18, 52]";       // short ID 0x1234, big-endian
        String attack = "6:[7, 1, 69, 103]";    // skill 7, one target ID 0x4567
        require(before.size() == 2, "Input executes both actual senders; captured=" + before);
        require(before.get(0).equals(attack) && before.get(1).equals(attack),
                "Input deterministically reproduces lost opcode -44: request ID 0x1234 is overwritten by the actual opcode-6 payload; captured=" + before);
        System.out.println("INPUT_RACE_REPRODUCED=true");
        System.out.println("INPUT_CAPTURED=" + before);
        System.out.println("EXPECTED_REQUEST=" + request);
        System.out.println("EXPECTED_ATTACK=" + attack);

        if (args.length == 3) {
            File fixed = new File(args[1]);
            List<String> after = runInterleaving(fixed, catalog);
            require(after.size() == 2, "Candidate executes both actual senders; captured=" + after);
            require(after.get(0).equals(request) && after.get(1).equals(attack),
                    "Candidate keeps the entire opcode -44 and opcode-6 transactions intact; captured=" + after);
            System.out.println("CANDIDATE_TRANSACTION_SERIALIZED=true");
            System.out.println("CANDIDATE_CAPTURED=" + after);
        }
    }

    private static List<String> runInterleaving(File jarFile, Properties catalog) throws Exception {
        firstNetworkEntered = new CountDownLatch(1);
        releaseFirstNetwork = new CountDownLatch(1);
        secondNetworkEntered = new CountDownLatch(1);
        firstMessage = null;
        sameMessageObject = false;
        synchronized (captured) { captured.clear(); }

        NativeProbeLoader loader = new NativeProbeLoader(jarFile, catalog);
        try {
            installConnection(loader, catalog);
            Class<?> senderType = loader.loadClass(pad("lilillll").replace('/', '.'));
            Method singleton = senderType.getDeclaredMethod(pad("liililli"));
            singleton.setAccessible(true);
            final Object sender = invoke(singleton, null);

            final Method request = findType2Request(senderType);
            String attackName = required(catalog, "auto.pk.sender.method");
            String attackDesc = required(catalog, "auto.pk.sender.descriptor");
            Class<?> listType = loader.loadClass("et");
            Method foundAttack = null;
            for (Method method : senderType.getDeclaredMethods()) {
                if (method.getName().equals(attackName)
                        && descriptor(method).equals(attackDesc)) foundAttack = method;
            }
            require(foundAttack != null, "Native opcode-6 serializer exists at " + attackName + attackDesc);
            foundAttack.setAccessible(true);
            final Method attack = foundAttack;

            final Object targets = newNativeList(listType);
            Class<?> targetRecordType = loader.loadClass(pad("liilli").replace('/', '.'));
            Constructor<?> targetConstructor = targetRecordType.getDeclaredConstructor(Short.TYPE, Byte.TYPE);
            targetConstructor.setAccessible(true);
            Object target = targetConstructor.newInstance(Short.valueOf((short)0x4567), Byte.valueOf((byte)0));
            Method add = listType.getDeclaredMethod(pad("l"), Object.class);
            add.setAccessible(true);
            invoke(add, targets, target);

            final Throwable[] requestFailure = new Throwable[1];
            Thread requestThread = new Thread(new Runnable() {
                public void run() {
                    try { invoke(request, sender, Short.valueOf((short)0x1234)); }
                    catch (Throwable failure) { requestFailure[0] = unwrap(failure); }
                }
            }, "native-type2-request");
            requestThread.start();
            require(firstNetworkEntered.await(5L, TimeUnit.SECONDS),
                    "Actual opcode -44 sender reaches the controllable native network boundary");

            final CountDownLatch attackStarted = new CountDownLatch(1);
            final Throwable[] attackFailure = new Throwable[1];
            Thread attackThread = new Thread(new Runnable() {
                public void run() {
                    attackStarted.countDown();
                    try { invoke(attack, sender, targets, Byte.valueOf((byte)7)); }
                    catch (Throwable failure) { attackFailure[0] = unwrap(failure); }
                }
            }, "native-pk-attack");
            attackThread.start();
            require(attackStarted.await(2L, TimeUnit.SECONDS), "PK sender thread starts");

            // If the transaction owner serializes these calls, the PK network boundary
            // cannot run until the paused type-2 send completes. Otherwise the PK send
            // overwrites the shared message while the earlier send is still in flight.
            secondNetworkEntered.await(750L, TimeUnit.MILLISECONDS);
            releaseFirstNetwork.countDown();
            requestThread.join(5000L);
            attackThread.join(5000L);
            require(!requestThread.isAlive() && !attackThread.isAlive(), "Both native senders finish");
            if (requestFailure[0] != null) throw new AssertionError("Opcode -44 sender failed", requestFailure[0]);
            if (attackFailure[0] != null) throw new AssertionError("Opcode-6 sender failed", attackFailure[0]);
            require(sameMessageObject, "Both native transactions use the same mutable message object");
            synchronized (captured) { return new ArrayList<String>(captured); }
        } finally {
            releaseFirstNetwork.countDown();
            loader.close();
        }
    }

    /** Invoked at the real native network boundary, before it reads message opcode/data. */
    public static void captureNativePacket(Object message) throws Exception {
        String thread = Thread.currentThread().getName();
        if ("native-type2-request".equals(thread)) {
            firstMessage = message;
            firstNetworkEntered.countDown();
            if (!releaseFirstNetwork.await(8L, TimeUnit.SECONDS)) {
                throw new AssertionError("Paused type-2 native send timed out");
            }
        } else if ("native-pk-attack".equals(thread)) {
            sameMessageObject = message == firstMessage;
        }
        String packet = snapshot(message);
        synchronized (captured) { captured.add(packet); }
        if ("native-pk-attack".equals(thread)) secondNetworkEntered.countDown();
    }

    private static Method findType2Request(Class<?> senderType) throws Exception {
        String expectedName = pad("lilll");
        Method found = null;
        for (Method method : senderType.getDeclaredMethods()) {
            if (!method.getName().equals(expectedName) || !descriptor(method).equals("(S)V")) continue;
            found = method;
        }
        if (found == null) {
            throw new NoSuchMethodException("Native type-2 request S." + expectedName + "(short)");
        }
        found.setAccessible(true);
        return found;
    }

    private static void installConnection(ClassLoader loader, Properties catalog) throws Exception {
        String ownerName = required(catalog, "native.connection.instance.owner").replace('/', '.');
        Class<?> owner = loader.loadClass(ownerName);
        Object connection = unsafeAllocate(owner);
        setField(connection, owner, required(catalog, "native.connection.connected.field"), Boolean.TRUE);
        setStaticField(owner, required(catalog, "native.connection.instance.field"), connection);
        String waitOwnerName = required(catalog, "native.connection.wait.owner").replace('/', '.');
        Class<?> waitOwner = loader.loadClass(waitOwnerName);
        setStaticField(waitOwner, required(catalog, "native.connection.wait.field"), Boolean.FALSE);
    }

    private static Object newNativeList(Class<?> listType) throws Exception {
        try {
            Constructor<?> constructor = listType.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance("Native sender transaction probe");
        } catch (NoSuchMethodException absent) {
            Constructor<?> constructor = listType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }
    }

    private static String snapshot(Object message) throws Exception {
        Field opcode = field(message.getClass(), "a");
        Method payload = null;
        for (Method method : message.getClass().getDeclaredMethods()) {
            if (method.getParameterTypes().length == 0 && method.getReturnType() == byte[].class) {
                payload = method; break;
            }
        }
        if (payload == null) throw new NoSuchMethodException("Native message byte[] accessor");
        payload.setAccessible(true);
        return opcode.getByte(message) + ":" + Arrays.toString((byte[])invoke(payload, message));
    }

    private static String descriptor(Method method) {
        StringBuilder value = new StringBuilder("(");
        for (Class<?> parameter : method.getParameterTypes()) value.append(typeDescriptor(parameter));
        return value.append(')').append(typeDescriptor(method.getReturnType())).toString();
    }

    private static String typeDescriptor(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        if (!type.isPrimitive()) return "L" + type.getName().replace('.', '/') + ";";
        if (type == Void.TYPE) return "V";
        if (type == Boolean.TYPE) return "Z";
        if (type == Byte.TYPE) return "B";
        if (type == Character.TYPE) return "C";
        if (type == Short.TYPE) return "S";
        if (type == Integer.TYPE) return "I";
        if (type == Long.TYPE) return "J";
        if (type == Float.TYPE) return "F";
        if (type == Double.TYPE) return "D";
        throw new IllegalArgumentException(type.getName());
    }

    private static Object unsafeAllocate(Class<?> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        return unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }

    private static void setField(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = field(owner, name);
        field.set(target, value);
    }

    private static void setStaticField(Class<?> owner, String name, Object value) throws Exception {
        Field field = field(owner, name);
        field.set(null, value);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        for (Class<?> at = owner; at != null; at = at.getSuperclass()) {
            try {
                Field field = at.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException absent) { }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try { return method.invoke(receiver, args); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new RuntimeException(cause);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof java.lang.reflect.InvocationTargetException
                && ((java.lang.reflect.InvocationTargetException)failure).getCause() != null
                ? ((java.lang.reflect.InvocationTargetException)failure).getCause() : failure;
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.length() == 0) throw new IllegalArgumentException("Missing property " + key);
        return value;
    }

    private static String pad(String suffix) {
        StringBuilder out = new StringBuilder(100);
        for (int i = suffix.length(); i < 100; i++) out.append('i');
        return out.append(suffix).toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class NativeProbeLoader extends URLClassLoader {
        private final JarFile jar;
        private final String networkOwner = pad("lililili");
        private final String networkMethod = pad("lliiili");
        private final String messageOwner = pad("lllllil");

        NativeProbeLoader(File jarFile, Properties catalog) throws Exception {
            super(new URL[]{jarFile.toURI().toURL()}, HungBySonNativeSendTransactionTest.class.getClassLoader());
            jar = new JarFile(jarFile);
        }

        @Override protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.")
                    || name.startsWith("org.objectweb.asm.")) return super.loadClass(name, resolve);
            String entry = name.replace('.', '/') + ".class";
            if (jar.getJarEntry(entry) == null) return super.loadClass(name, resolve);
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) loaded = findClass(name);
            if (resolve) resolveClass(loaded);
            return loaded;
        }

        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            String internal = name.replace('.', '/');
            try {
                JarEntry entry = jar.getJarEntry(internal + ".class");
                if (entry == null) throw new ClassNotFoundException(name);
                java.io.InputStream stream = jar.getInputStream(entry);
                ClassNode node = new ClassNode();
                try { new ClassReader(readAll(stream)).accept(node, 0); }
                finally { stream.close(); }
                if (internal.equals(networkOwner)) transformNetworkBoundary(node);
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                byte[] bytes = writer.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (ClassNotFoundException failure) { throw failure; }
            catch (Throwable failure) { throw new ClassNotFoundException(name, failure); }
        }

        private void transformNetworkBoundary(ClassNode node) {
            int found = 0;
            String descriptor = "(L" + messageOwner + ";)V";
            for (MethodNode method : node.methods) {
                if (!networkMethod.equals(method.name) || !descriptor.equals(method.desc)) continue;
                method.instructions.clear();
                method.tryCatchBlocks.clear();
                method.localVariables = null;
                InsnList code = method.instructions;
                code.add(new VarInsnNode(Opcodes.ALOAD, 1));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOST,
                        "captureNativePacket", "(Ljava/lang/Object;)V", false));
                code.add(new InsnNode(Opcodes.RETURN));
                method.maxLocals = 2;
                method.maxStack = 1;
                found++;
            }
            require(found == 1, "Exactly one actual native network boundary is intercepted");
        }

        @Override public void close() throws IOException {
            try { jar.close(); } finally { super.close(); }
        }
    }

    private static byte[] readAll(java.io.InputStream input) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }
}
