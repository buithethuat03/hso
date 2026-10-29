import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Clicks the actual native Noclip root-menu row and checks its rebuild path. */
public final class HungBySonNoclipMenuHostTest implements Opcodes {
    private static final String HOST = "HungBySonNoclipMenuHostTest";
    private static final int ACTION = 106;
    private static final String ENABLED_FIELD = "codexNoclipEnabled";
    private static final String[] TEMPO_LABELS = new String[]{
        "Tốc độ x1", "Tốc độ x2", "Tốc độ x3", "Tốc độ x0.8", "Tốc độ x0.9"
    };
    private static final String NETWORK_OWNER = encoded("lililili");
    private static final String NETWORK_METHOD = encoded("lliiili");
    private static final String NETWORK_MESSAGE = encoded("lllllil");

    private static final List<PopupEvent> popupEvents = new ArrayList<PopupEvent>();
    private static final List<int[]> actionCalls = new ArrayList<int[]>();
    private static int blockedNetworkCalls;
    private static int checks;

    private HungBySonNoclipMenuHostTest() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("<candidate.jar> <tempo.properties>");
        File jar = new File(args[0]);
        Properties symbols = load(new File(args[1]));
        require(jar.isFile(), "candidate JAR exists");
        require(hash(jar).equals(required(symbols, "noclip.output.sha256")),
                "candidate bytes match the Noclip menu catalog");
        require(Integer.parseInt(required(symbols, "candidate.noclip.action")) == ACTION,
                "Noclip action is the byte-safe native ID 106");
        runFreshToggleCase(jar, symbols);
        System.out.println("HungBySonNoclipMenuHostTest PASS (" + checks + " checks)");
    }

    private static void runFreshToggleCase(File jar, Properties symbols) throws Exception {
        popupEvents.clear();
        actionCalls.clear();
        blockedNetworkCalls = 0;
        String menuOwner = required(symbols, "candidate.menu.owner");
        String refreshSignature = required(symbols, "candidate.menu.refresh");
        Member refresh = parseMember(refreshSignature);
        require(refresh.owner.equals(menuOwner) && "()V".equals(refresh.descriptor),
                "catalog identifies the native root menu builder");
        String actionSignature = required(symbols, "candidate.menu.action");
        Member action = parseAction(actionSignature, menuOwner);
        DisplayCall display = parseDisplay(required(symbols, "candidate.menu.display"));
        String menuClassName = menuOwner.replace('/', '.');

        try (ProbeLoader loader = new ProbeLoader(jar, display, menuOwner, action)) {
            Class<?> menuClass = Class.forName(menuClassName, true, loader);
            Field enabled = field(menuClass, ENABLED_FIELD);
            require(Modifier.isStatic(enabled.getModifiers()), "Noclip state is static native menu state");
            expect(!enabled.getBoolean(null), "fresh class loader starts with Noclip OFF");

            CallbackField callback = findRootCallback(jar, menuOwner, refresh.name,
                    Integer.parseInt(required(symbols, "candidate.menu.rootAction")));
            Field callbackMember = field(Class.forName(callback.owner.replace('/', '.'), true, loader), callback.name);
            require(Modifier.isStatic(callbackMember.getModifiers()), "root menu callback field is static native state");
            Object nativeGameScreen = newNativeInstance(loader, callbackMember.getType(), jar);
            callbackMember.set(null, nativeGameScreen);

            Method rootBuilder = declaredMethod(menuClass, refresh.name, refresh.descriptor);
            verifyTempoButtonsRemain(menuClass);

            invoke(rootBuilder, null);
            PopupEvent offPopup = lastNoclipPopup("initial root menu");
            expect(countNoclipLabels(offPopup.items) == 1, "root menu contains one Noclip row");
            expect("Noclip: [OFF]".equals(noclipLabel(findNoclipItem(offPopup.items))),
                    "root menu shows Noclip: [OFF]");

            Object offItem = findNoclipItem(offPopup.items);
            clickNativeItem(offItem, callbackMember.getType(), ACTION);
            expect(enabled.getBoolean(null), "first native click turns Noclip ON");
            expect("Noclip: [ON]".equals(noclipLabel(findNoclipItem(lastNoclipPopup("ON rebuild").items))),
                    "native handler rebuilds the root menu with Noclip: [ON]");
            expect(blockedNetworkCalls == 0, "first Noclip toggle makes no network send");

            Object onItem = findNoclipItem(lastNoclipPopup("ON popup").items);
            clickNativeItem(onItem, callbackMember.getType(), ACTION);
            expect(!enabled.getBoolean(null), "second native click turns Noclip OFF");
            expect("Noclip: [OFF]".equals(noclipLabel(findNoclipItem(lastNoclipPopup("OFF rebuild").items))),
                    "second native click rebuilds the root menu with Noclip: [OFF]");
            expect(blockedNetworkCalls == 0, "second Noclip toggle makes no network send");
        }
    }

    private static void verifyTempoButtonsRemain(Class<?> menuClass) throws Exception {
        Method tempoBuilder = declaredMethod(menuClass, "codexBuildTempoMenu", "()V");
        popupEvents.clear();
        invoke(tempoBuilder, null);
        PopupEvent popup = lastTempoPopup("existing tempo submenu");
        String[] labels = itemLabels(popup.items);
        expect(Arrays.equals(stripMarkers(labels), TEMPO_LABELS),
                "Noclip patch preserves the five native tempo buttons");
    }

    private static void clickNativeItem(Object item, Class<?> callbackType, int expectedAction) throws Exception {
        Method click = findNativeClickMethod(item.getClass(), callbackType);
        actionCalls.clear();
        popupEvents.clear();
        invoke(click, item);
        require(actionCalls.size() == 1, "one native handler dispatch per Noclip click");
        expect(actionCalls.get(0)[0] == expectedAction, "native click dispatches action " + expectedAction);
    }

    /** Captured replacement for the native popup display call. */
    public static void capturePopup(Object displayReceiver, Object items, int mode,
            String title, boolean flag, Object trailingList) {
        popupEvents.add(new PopupEvent(items, title, mode));
    }

    /** In-memory guard if an unexpected path attempts to send a game packet. */
    public static void captureBlockedPacket(Object ignored) { blockedNetworkCalls++; }
    public static void captureMenuAction(int first, int second) { actionCalls.add(new int[]{first, second}); }

    private static PopupEvent lastNoclipPopup(String context) throws Exception {
        for (int i = popupEvents.size() - 1; i >= 0; i--) {
            PopupEvent event = popupEvents.get(i);
            if (event.items != null && countNoclipLabels(event.items) > 0) return event;
        }
        throw new AssertionError(context + " did not produce the native root popup with a Noclip row");
    }

    private static PopupEvent lastTempoPopup(String context) throws Exception {
        for (int i = popupEvents.size() - 1; i >= 0; i--) {
            PopupEvent event = popupEvents.get(i);
            if (event.items == null) continue;
            try {
                if (Arrays.equals(stripMarkers(itemLabels(event.items)), TEMPO_LABELS)) return event;
            } catch (Throwable ignored) { }
        }
        throw new AssertionError(context + " did not produce the existing tempo popup");
    }

    private static int countNoclipLabels(Object items) throws Exception {
        int count = 0;
        for (int i = 0; i < vectorSize(items); i++)
            if (noclipLabelOrNull(vectorGet(items, i)) != null) count++;
        return count;
    }

    private static Object findNoclipItem(Object items) throws Exception {
        Object found = null;
        for (int i = 0; i < vectorSize(items); i++) {
            Object item = vectorGet(items, i);
            if (noclipLabelOrNull(item) == null) continue;
            if (found != null) throw new AssertionError("root menu has multiple Noclip rows");
            found = item;
        }
        if (found == null) throw new AssertionError("root menu is missing its Noclip row");
        return found;
    }

    private static String noclipLabel(Object item) throws Exception {
        String value = noclipLabelOrNull(item);
        if (value == null) throw new AssertionError("native item has no Noclip text");
        return value;
    }

    private static String noclipLabelOrNull(Object item) throws Exception {
        String found = null;
        for (Field field : allFields(item.getClass())) {
            if (field.getType() != String.class) continue;
            field.setAccessible(true);
            Object value = field.get(item);
            if (value instanceof String && ((String)value).startsWith("Noclip:")) {
                if (found != null) throw new AssertionError("native item has multiple Noclip labels");
                found = (String)value;
            }
        }
        return found;
    }

    private static String[] itemLabels(Object items) throws Exception {
        String[] result = new String[vectorSize(items)];
        for (int i = 0; i < result.length; i++) result[i] = itemLabel(vectorGet(items, i));
        return result;
    }

    private static String itemLabel(Object item) throws Exception {
        String found = null;
        for (Field field : allFields(item.getClass())) {
            if (field.getType() != String.class) continue;
            field.setAccessible(true);
            Object value = field.get(item);
            if (value instanceof String && ((String)value).startsWith("Tốc độ ")) {
                if (found != null) throw new AssertionError("tempo item has multiple labels");
                found = (String)value;
            }
        }
        if (found == null) throw new AssertionError("tempo item has no runtime label");
        return found;
    }

    private static String[] stripMarkers(String[] values) {
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i].endsWith(" [x]")
                ? values[i].substring(0, values[i].length() - 4) : values[i];
        return result;
    }

    private static int vectorSize(Object vector) throws Exception {
        Method size = null;
        for (Method method : vector.getClass().getMethods()) {
            if (method.getParameterTypes().length == 0 && method.getReturnType() == int.class
                    && method.getName().equals("c")) { size = method; break; }
        }
        if (size == null) throw new NoSuchMethodException(vector.getClass().getName() + ".c()I");
        return ((Integer)invoke(size, vector)).intValue();
    }

    private static Object vectorGet(Object vector, int index) throws Exception {
        Method get = null;
        for (Method method : vector.getClass().getMethods()) {
            if (method.getName().equals("a") && method.getParameterTypes().length == 1
                    && method.getParameterTypes()[0] == int.class && method.getReturnType() == Object.class) {
                get = method; break;
            }
        }
        if (get == null) throw new NoSuchMethodException(vector.getClass().getName() + ".a(I)Object");
        return invoke(get, vector, Integer.valueOf(index));
    }

    private static Method findNativeClickMethod(Class<?> itemClass, Class<?> callbackType) throws Exception {
        JarFile jar = activeLoader.get().getJar();
        ClassNode node = readClass(jar, itemClass.getName().replace('.', '/'));
        MethodNode event = method(node, "a", "()V");
        int callbackDispatches = 0;
        for (AbstractInsnNode instruction = event.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode && "(II)V".equals(((MethodInsnNode)instruction).desc))
                callbackDispatches++;
        }
        require(callbackDispatches > 0, "native bt a()V contains real two-int callback dispatches");
        require(!callbackType.isPrimitive(), "bt callback instance has an object type");
        return declaredMethod(itemClass, "a", "()V");
    }

    private static CallbackField findRootCallback(File jar, String menuOwner, String builderName,
            int rootAction) throws Exception {
        ClassNode node = readClass(jar, menuOwner);
        MethodNode builder = method(node, builderName, "()V");
        CallbackField found = null;
        for (AbstractInsnNode instruction = builder.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!integer(instruction, rootAction)) continue;
            AbstractInsnNode callbackNode = nextReal(instruction);
            if (!(callbackNode instanceof FieldInsnNode) || callbackNode.getOpcode() != GETSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode)callbackNode;
            AbstractInsnNode ctorNode = nextReal(callbackNode);
            if (!(ctorNode instanceof MethodInsnNode) || ctorNode.getOpcode() != INVOKESPECIAL) continue;
            MethodInsnNode ctor = (MethodInsnNode)ctorNode;
            if (!"bt".equals(ctor.owner) || !"<init>".equals(ctor.name)) continue;
            if (found != null && (!found.owner.equals(field.owner) || !found.name.equals(field.name)))
                throw new AssertionError("root rows use multiple native callback fields");
            found = new CallbackField(field.owner, field.name);
        }
        if (found == null) throw new AssertionError("root builder has no native callback field for action " + rootAction);
        return found;
    }

    private static Object newNativeInstance(ProbeLoader loader, Class<?> declaredType, File jar) throws Exception {
        Class<?> concrete = declaredType;
        if (concrete.isInterface() || Modifier.isAbstract(concrete.getModifiers()))
            concrete = findConcreteSubtype(loader, jar, declaredType);
        return unsafe().allocateInstance(concrete);
    }

    private static Class<?> findConcreteSubtype(ProbeLoader loader, File jar, Class<?> base) throws Exception {
        Class<?> match = null;
        Enumeration<JarEntry> entries = loader.getJar().entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.getName().endsWith(".class")) continue;
            String name = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
            Class<?> candidate;
            try { candidate = Class.forName(name, false, loader); }
            catch (Throwable ignored) { continue; }
            int access = candidate.getModifiers();
            if (base.isAssignableFrom(candidate) && !candidate.isInterface()
                    && !Modifier.isAbstract(access) && !candidate.isEnum()) {
                if (match != null) throw new IllegalStateException("ambiguous native callback implementation for " + base.getName());
                match = candidate;
            }
        }
        if (match == null) throw new IllegalStateException("no concrete native callback implementation for " + base.getName());
        return match;
    }

    private static final ThreadLocal<ProbeLoader> activeLoader = new ThreadLocal<ProbeLoader>();

    private static final class ProbeLoader extends URLClassLoader {
        private final JarFile jar;
        private final DisplayCall display;
        private final String menuOwner;
        private final Member action;

        ProbeLoader(File source, DisplayCall display, String menuOwner, Member action) throws Exception {
            super(new URL[]{source.toURI().toURL()}, HungBySonNoclipMenuHostTest.class.getClassLoader());
            this.jar = new JarFile(source);
            this.display = display;
            this.menuOwner = menuOwner;
            this.action = action;
            activeLoader.set(this);
        }

        JarFile getJar() { return jar; }

        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String internalName = name.replace('.', '/');
            JarEntry entry = jar.getJarEntry(internalName + ".class");
            if (entry == null) return super.findClass(name);
            try {
                ClassNode node = readClass(jar, internalName);
                for (MethodNode method : node.methods) {
                    if (internalName.equals(menuOwner) && method.name.equals(action.name)
                            && method.desc.equals(action.descriptor)) {
                        AbstractInsnNode first = firstReal(method.instructions.getFirst());
                        if (first == null) throw new AssertionError("empty native menu action method");
                        org.objectweb.asm.tree.InsnList trace = new org.objectweb.asm.tree.InsnList();
                        trace.add(new VarInsnNode(ILOAD, 0));
                        trace.add(new VarInsnNode(ILOAD, 1));
                        trace.add(new MethodInsnNode(INVOKESTATIC, HOST, "captureMenuAction", "(II)V", false));
                        method.instructions.insertBefore(first, trace);
                    }
                    for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
                        AbstractInsnNode next = instruction.getNext();
                        if (instruction instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode)instruction;
                            if (display.owner.equals(call.owner) && display.name.equals(call.name)
                                    && display.descriptor.equals(call.desc) && call.getOpcode() == INVOKEVIRTUAL) {
                                method.instructions.set(instruction, new MethodInsnNode(INVOKESTATIC, HOST,
                                        "capturePopup", "(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/String;ZLjava/lang/Object;)V", false));
                            }
                        }
                        instruction = next;
                    }
                }
                if (internalName.equals(NETWORK_OWNER)) patchNetworkSender(node);
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                byte[] bytes = writer.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (Throwable error) { throw new ClassNotFoundException(name, error); }
        }

        public void close() throws java.io.IOException {
            try { jar.close(); } finally { activeLoader.remove(); super.close(); }
        }

        private void patchNetworkSender(ClassNode node) {
            int count = 0;
            for (MethodNode method : node.methods) {
                String descriptor = "(L" + NETWORK_MESSAGE + ";)V";
                if (!method.name.equals(NETWORK_METHOD) || !method.desc.equals(descriptor)) continue;
                method.instructions.clear();
                method.tryCatchBlocks.clear();
                method.localVariables = null;
                int local = (method.access & ACC_STATIC) == 0 ? 1 : 0;
                method.instructions.add(new VarInsnNode(ALOAD, local));
                method.instructions.add(new MethodInsnNode(INVOKESTATIC, HOST, "captureBlockedPacket",
                        "(Ljava/lang/Object;)V", false));
                method.instructions.add(new InsnNode(RETURN));
                count++;
            }
            if (count > 1) throw new IllegalStateException("multiple native packet senders match guard");
        }
    }

    private static final class CallbackField {
        final String owner, name;
        CallbackField(String owner, String name) { this.owner = owner; this.name = name; }
    }

    private static final class PopupEvent {
        final Object items;
        final String title;
        final int mode;
        PopupEvent(Object items, String title, int mode) { this.items = items; this.title = title; this.mode = mode; }
    }

    private static final class DisplayCall {
        final String owner, name, descriptor;
        DisplayCall(String owner, String name, String descriptor) {
            this.owner = owner; this.name = name; this.descriptor = descriptor;
        }
    }

    private static final class Member {
        final String owner, name, descriptor;
        Member(String owner, String name, String descriptor) {
            this.owner = owner; this.name = name; this.descriptor = descriptor;
        }
    }

    private static Member parseAction(String signature, String owner) {
        int descriptorStart = signature.indexOf('(');
        if (descriptorStart <= 0) throw new IllegalArgumentException("invalid action signature: " + signature);
        return new Member(owner, signature.substring(0, descriptorStart), signature.substring(descriptorStart));
    }

    private static Member parseMember(String signature) {
        int descriptorStart = signature.indexOf('(');
        int methodStart = signature.lastIndexOf('.', descriptorStart);
        if (descriptorStart < 0 || methodStart < 0)
            throw new IllegalArgumentException("invalid owner.method descriptor: " + signature);
        return new Member(signature.substring(0, methodStart), signature.substring(methodStart + 1, descriptorStart),
                signature.substring(descriptorStart));
    }

    private static DisplayCall parseDisplay(String signature) {
        Member member = parseMember(signature);
        return new DisplayCall(member.owner, member.name, member.descriptor);
    }

    private static Method declaredMethod(Class<?> type, String name, String descriptor) throws Exception {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor)) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + descriptor);
    }

    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try { return method.invoke(receiver, args); }
        catch (java.lang.reflect.InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new RuntimeException(cause);
        }
    }

    private static ClassNode readClass(JarFile jar, String internalName) throws Exception {
        JarEntry entry = jar.getJarEntry(internalName + ".class");
        if (entry == null) throw new IllegalStateException("class missing from runtime JAR: " + internalName);
        ClassNode node = new ClassNode(ASM9);
        new ClassReader(readAll(jar.getInputStream(entry))).accept(node, 0);
        return node;
    }

    private static ClassNode readClass(File jarFile, String internalName) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) { return readClass(jar, internalName); }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] block = new byte[8192];
            for (int count; (count = in.read(block)) != -1;) out.write(block, 0, count);
            return out.toByteArray();
        }
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods)
            if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        throw new IllegalStateException("missing native menu method " + node.name + "." + name + descriptor);
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode instruction) {
        instruction = instruction == null ? null : instruction.getNext();
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
        return instruction;
    }

    private static AbstractInsnNode firstReal(AbstractInsnNode instruction) {
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
        return instruction;
    }

    private static boolean integer(AbstractInsnNode instruction, int expected) {
        int opcode = instruction.getOpcode();
        if (opcode >= ICONST_M1 && opcode <= ICONST_5) return opcode - ICONST_0 == expected;
        return instruction instanceof org.objectweb.asm.tree.IntInsnNode
                && ((org.objectweb.asm.tree.IntInsnNode)instruction).operand == expected;
    }

    private static Properties load(File file) throws Exception {
        if (!file.isFile()) throw new IllegalArgumentException("catalog missing: " + file.getAbsolutePath());
        Properties properties = new Properties();
        try (InputStream input = new java.io.FileInputStream(file)) { properties.load(input); }
        return properties;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) throw new IllegalStateException("catalog entry missing: " + key);
        return value.trim();
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> result = new ArrayList<Field>();
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
            result.addAll(Arrays.asList(cursor.getDeclaredFields()));
        return result;
    }

    private static String hash(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02X", Byte.valueOf(value)));
        return result.toString();
    }

    private static String encoded(String suffix) {
        StringBuilder value = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) value.append('i');
        return value.append(suffix).toString();
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe)field.get(null);
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
        System.out.println("PASS " + message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
