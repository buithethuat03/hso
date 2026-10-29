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

/** Executes the real candidate popup item event and native callback route without an emulator. */
public final class HungBySonTempoClickHostTest implements Opcodes {
    private static final String HOST = "HungBySonTempoClickHostTest";
    private static String[] LABELS = new String[]{
        "Tốc độ x1", "Tốc độ x2", "Tốc độ x3", "Tốc độ x0.8", "Tốc độ x0.9"
    };
    private static int[] VALUES = new int[]{1, 2, 3, -8, -9};
    private static final String NETWORK_OWNER = encoded("lililili");
    private static final String NETWORK_METHOD = encoded("lliiili");
    private static final String NETWORK_MESSAGE = encoded("lllllil");

    private static final List<PopupEvent> popupEvents = new ArrayList<PopupEvent>();
    private static final List<int[]> actionCalls = new ArrayList<int[]>();
    private static int blockedNetworkCalls;
    private static int checks;

    private HungBySonTempoClickHostTest() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("<candidate.jar> <tempo.properties>");
        File jar = new File(args[0]);
        Properties symbols = load(new File(args[1]));
        require(jar.isFile(), "candidate JAR exists");
        String sourceHash = symbols.getProperty("input.sha256");
        require(sourceHash != null && sourceHash.matches("(?i)[0-9a-f]{64}"),
                "tempo catalog records the guarded input JAR hash");
        require(hash(jar).equals(symbols.getProperty("output.sha256")), "candidate bytes match the click-test tempo catalog");
        VALUES = parseIntegers(required(symbols, "candidate.speed.codes"));
        String[] factors = parseDecimals(required(symbols, "candidate.speed.labels"));
        require(VALUES.length == 5 && factors.length == 5, "tempo catalog contains five speed choices");
        LABELS = new String[factors.length];
        for (int i = 0; i < factors.length; i++) LABELS[i] = "T\u1ED1c \u0111\u1ED9 x" + factors[i];
        runClickCases(jar, symbols);
        System.out.println("HungBySonTempoClickHostTest PASS (" + checks + " checks)");
    }

    private static void runClickCases(File jar, Properties symbols) throws Exception {
        for (int option = 0; option < LABELS.length; option++) {
            runOneClick(jar, symbols, option);
        }
    }

    private static void runOneClick(File jar, Properties symbols, int option) throws Exception {
        popupEvents.clear();
        blockedNetworkCalls = 0;
        String menuOwner = required(symbols, "candidate.menu.owner");
        String speedOwner = required(symbols, "candidate.speed.owner");
        String speedField = required(symbols, "candidate.speed.field");
        DisplayCall display = parseDisplay(required(symbols, "candidate.menu.display"));
        ActionMethod action = parseAction(required(symbols, "candidate.menu.action"));
        int[] childActions = parseIntegers(required(symbols, "candidate.menu.childActions"));
        int[] actualCodes = parseIntegers(required(symbols, "candidate.speed.codes"));
        String[] actualLabels = parseDecimals(required(symbols, "candidate.speed.labels"));
        require(childActions.length == 5 && actualCodes.length == 5 && actualLabels.length == 5,
                "tempo catalog contains five button IDs, factors, and labels");
        require(Arrays.equals(childActions, new int[]{101, 102, 103, 104, 105}),
                "native button IDs fit the event byte field and reach menu codes 101..105");
        require(Arrays.equals(actualCodes, VALUES), "tempo handler stores each configured integer or fractional factor");
        require(Arrays.equals(actualLabels, decimalsFromLabels(LABELS)),
                "tempo catalog labels match all five native menu rows");
        String menuClassName = menuOwner.replace('/', '.');
        String speedClassName = speedOwner.replace('/', '.');

        try (ProbeLoader loader = new ProbeLoader(jar, display, menuOwner, action)) {
            Class<?> menuClass = Class.forName(menuClassName, true, loader);
            Class<?> speedClass = Class.forName(speedClassName, true, loader);
            Field speed = field(speedClass, speedField);
            expect(speed.getInt(null) == 1, "native startup speed is x1");

            CallbackField callback = findBuilderCallback(jar, menuOwner);
            Field callbackMember = field(Class.forName(callback.owner.replace('/', '.'), true, loader), callback.name);
            require(Modifier.isStatic(callbackMember.getModifiers()), "tempo row callback field is static native state");
            Object nativeGameScreen = newNativeInstance(loader, callbackMember.getType(), jar);
            callbackMember.set(null, nativeGameScreen);

            Method build = declaredMethod(menuClass, "codexBuildTempoMenu", "()V");
            int startingValue = VALUES[option] == 1 ? 2 : 1;
            speed.setInt(null, startingValue);
            invoke(build, null);
            PopupEvent initial = lastTempoPopup("initial submenu build");
            String[] initialLabels = menuLabels(initial.items);
            expect(Arrays.equals(stripMarkers(initialLabels), LABELS), "native popup contains exactly the five intended tempo rows: "
                    + Arrays.toString(initialLabels));
            expect(selectedLabel(initialLabels).equals(labelForValue(startingValue)),
                    "native submenu marks the row matching its current speed");

            Object item = findItem(initial.items, LABELS[option]);
            Method click = findNativeClickMethod(item.getClass(), callbackMember.getType());
            int before = popupEvents.size();
            actionCalls.clear();
            invoke(click, item);
            int afterSpeed = speed.getInt(null);
            require(actionCalls.size() == 1, "one actual menu action dispatch per bt click");
            expect(actionCalls.get(0)[0] == childActions[option], "bt callback delivers native action code " + childActions[option]);
            expect(afterSpeed == VALUES[option], "bt click " + LABELS[option] + " reaches the tempo handler and selects " + afterSpeed);
            List<PopupEvent> clickPopups = new ArrayList<PopupEvent>(popupEvents.subList(before, popupEvents.size()));
            expect(noActivationKeyPopup(clickPopups), "click " + LABELS[option] + " does not open the activation-key flow");
            expect(blockedNetworkCalls == 0, "tempo click makes no network send");

            popupEvents.clear();
            invoke(build, null);
            PopupEvent updated = lastTempoPopup("selected-state submenu rebuild");
            String[] updatedLabels = menuLabels(updated.items);
            expect(Arrays.equals(stripMarkers(updatedLabels), LABELS), "rebuilt popup keeps the five factor labels");
            expect(selectedLabel(updatedLabels).equals(LABELS[option] + " [x]"),
                    "rebuilt popup marks only the selected factor " + LABELS[option]);
            expect(blockedNetworkCalls == 0, "menu rebuild makes no network send");
        }
    }

    /** Captured replacement for the native popup display call; all real menu rows were built first. */
    public static void capturePopup(Object displayReceiver, Object items, int mode,
            String title, boolean flag, Object trailingList) {
        popupEvents.add(new PopupEvent(items, title, mode));
    }

    /** In-memory guard if an unexpected path attempts to send a game packet. */
    public static void captureBlockedPacket(Object ignored) { blockedNetworkCalls++; }
    public static void captureMenuAction(int first, int second) { actionCalls.add(new int[]{first, second}); }

    private static boolean noActivationKeyPopup(List<PopupEvent> events) throws Exception {
        for (PopupEvent event : events) {
            if (containsKeyText(event.title)) return false;
            if (event.items != null) {
                int count = vectorSize(event.items);
                for (int i = 0; i < count; i++) {
                    Object item = vectorGet(event.items, i);
                    for (Field field : allFields(item.getClass())) {
                        if (field.getType() != String.class) continue;
                        field.setAccessible(true);
                        if (containsKeyText((String)field.get(item))) return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean containsKeyText(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase();
        return lower.contains("nhập key") || lower.contains("activation key") || lower.contains("license key");
    }

    private static PopupEvent lastTempoPopup(String context) {
        for (int i = popupEvents.size() - 1; i >= 0; i--) {
            PopupEvent event = popupEvents.get(i);
            if (event.items == null) continue;
            try {
                String[] labels = menuLabels(event.items);
                if (Arrays.equals(stripMarkers(labels), LABELS)) return event;
            } catch (Exception ignored) { }
        }
        throw new AssertionError(context + " did not produce the actual tempo popup");
    }

    private static String[] stripMarkers(String[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = values[i].endsWith(" [x]")
                ? values[i].substring(0, values[i].length() - 4) : values[i];
        return out;
    }

    private static String[] menuLabels(Object items) throws Exception {
        int count = vectorSize(items);
        String[] result = new String[count];
        for (int i = 0; i < count; i++) result[i] = itemLabel(vectorGet(items, i));
        return result;
    }

    private static String itemLabel(Object item) throws Exception {
        String found = null;
        for (Field field : allFields(item.getClass())) {
            if (field.getType() != String.class) continue;
            field.setAccessible(true);
            Object value = field.get(item);
            if (value instanceof String && ((String)value).startsWith("Tốc độ ")) {
                if (found != null) throw new AssertionError("tempo row has multiple labels");
                found = (String)value;
            }
        }
        if (found == null) throw new AssertionError("menu item has no runtime tempo label");
        return found;
    }

    private static Object findItem(Object items, String label) throws Exception {
        for (int i = 0; i < vectorSize(items); i++) {
            Object item = vectorGet(items, i);
            if (label.equals(stripMarker(itemLabel(item)))) return item;
        }
        throw new AssertionError("popup item missing: " + label);
    }

    private static String stripMarker(String value) {
        return value.endsWith(" [x]") ? value.substring(0, value.length() - 4) : value;
    }

    private static String selectedLabel(String[] labels) {
        String selected = null;
        int count = 0;
        for (String label : labels) if (label.endsWith(" [x]")) { selected = label; count++; }
        if (count != 1) throw new AssertionError("expected one selected speed row, found " + count);
        return selected;
    }

    private static String labelForValue(int value) {
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i] == value) return LABELS[i] + " [x]";
        throw new AssertionError("unknown speed code " + value);
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

    private static final ThreadLocal<ProbeLoader> activeLoader = new ThreadLocal<ProbeLoader>();

    private static CallbackField findBuilderCallback(File jar, String menuOwner) throws Exception {
        ClassNode node = readClass(jar, menuOwner);
        MethodNode builder = method(node, "codexBuildTempoMenu", "()V");
        CallbackField found = null;
        for (AbstractInsnNode instruction = builder.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != INVOKESPECIAL) continue;
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (!"bt".equals(call.owner) || !"<init>".equals(call.name)
                    || !call.desc.startsWith("(Ljava/lang/String;I")) continue;
            AbstractInsnNode previous = previousReal(instruction);
            if (!(previous instanceof FieldInsnNode) || previous.getOpcode() != GETSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode)previous;
            if (!field.desc.startsWith("L")) continue;
            if (found != null && (!found.owner.equals(field.owner) || !found.name.equals(field.name)))
                throw new AssertionError("tempo rows use multiple callback fields");
            found = new CallbackField(field.owner, field.name);
        }
        if (found == null) throw new AssertionError("codexBuildTempoMenu has no native callback field load");
        return found;
    }

    private static Object newNativeInstance(ProbeLoader loader, Class<?> declaredType, File jar) throws Exception {
        Class<?> concrete = declaredType;
        if (concrete.isInterface() || Modifier.isAbstract(concrete.getModifiers())) {
            concrete = findConcreteSubtype(loader, jar, declaredType);
        }
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
                if (match != null) throw new IllegalStateException("ambiguous native screen implementation for " + base.getName());
                match = candidate;
            }
        }
        if (match == null) throw new IllegalStateException("no concrete native screen implementation for " + base.getName());
        return match;
    }

    private static final class CallbackField {
        final String owner, name;
        CallbackField(String owner, String name) { this.owner = owner; this.name = name; }
    }
    private static final class PopupEvent {
        final Object items; final String title; final int mode;
        PopupEvent(Object items, String title, int mode) { this.items = items; this.title = title; this.mode = mode; }
    }
    private static final class DisplayCall {
        final String owner, name, descriptor;
        DisplayCall(String owner, String name, String descriptor) { this.owner = owner; this.name = name; this.descriptor = descriptor; }
    }
    private static final class ActionMethod {
        final String name, descriptor;
        ActionMethod(String name, String descriptor) { this.name = name; this.descriptor = descriptor; }
    }

    private static ActionMethod parseAction(String signature) {
        int start = signature.indexOf('(');
        if (start <= 0) throw new IllegalArgumentException("invalid candidate.menu.action signature");
        return new ActionMethod(signature.substring(0, start), signature.substring(start));
    }

    private static int[] parseIntegers(String value) {
        String[] parts = value.split(","); int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Integer.parseInt(parts[i].trim());
        return result;
    }

    private static String[] parseDecimals(String value) {
        String[] parts = value.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    private static String[] decimalsFromLabels(String[] labels) {
        String[] result = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            int marker = labels[i].lastIndexOf('x');
            if (marker < 0) throw new IllegalArgumentException("tempo label has no speed factor: " + labels[i]);
            result[i] = labels[i].substring(marker + 1);
        }
        return result;
    }

    private static DisplayCall parseDisplay(String signature) {
        int descriptorStart = signature.indexOf('(');
        int methodStart = signature.lastIndexOf('.', descriptorStart);
        if (descriptorStart < 0 || methodStart < 0) throw new IllegalArgumentException("invalid candidate.menu.display signature");
        return new DisplayCall(signature.substring(0, methodStart), signature.substring(methodStart + 1, descriptorStart),
                signature.substring(descriptorStart));
    }

    private static final class ProbeLoader extends URLClassLoader {
        private final JarFile jar;
        private final DisplayCall display;
        private final String menuOwner;
        private final ActionMethod action;
        ProbeLoader(File source, DisplayCall display, String menuOwner, ActionMethod action) throws Exception {
            super(new URL[]{source.toURI().toURL()}, HungBySonTempoClickHostTest.class.getClassLoader());
            this.jar = new JarFile(source); this.display = display; this.menuOwner = menuOwner; this.action = action;
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
                        AbstractInsnNode first = method.instructions.getFirst();
                        while (first != null && first.getOpcode() < 0) first = first.getNext();
                        if (first == null) throw new AssertionError("empty menu action method");
                        org.objectweb.asm.tree.InsnList trace = new org.objectweb.asm.tree.InsnList();
                        trace.add(new VarInsnNode(ILOAD, 0)); trace.add(new VarInsnNode(ILOAD, 1));
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
                            if ("java/lang/System".equals(call.owner) && "currentTimeMillis".equals(call.name)
                                    && "()J".equals(call.desc)) {
                                // Menu probes do not depend on wall time. Returning a stable epoch
                                // avoids invoking the host clock from actual game callbacks.
                                method.instructions.set(instruction, new MethodInsnNode(INVOKESTATIC, HOST,
                                        "fixedClock", "()J", false));
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
                method.instructions.clear(); method.tryCatchBlocks.clear(); method.localVariables = null;
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

    public static long fixedClock() { return 100000L; }

    private static List<Field> allFields(Class<?> type) {
        List<Field> result = new ArrayList<Field>();
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
            result.addAll(Arrays.asList(cursor.getDeclaredFields()));
        return result;
    }
    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try { Field field = cursor.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private static Method declaredMethod(Class<?> type, String name, String desc) throws Exception {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(desc)) {
                method.setAccessible(true); return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + desc);
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
            byte[] block = new byte[8192]; int count;
            while ((count = in.read(block)) != -1) out.write(block, 0, count);
            return out.toByteArray();
        }
    }
    private static MethodNode method(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) if (method.name.equals(name) && method.desc.equals(desc)) return method;
        throw new IllegalStateException("missing actual builder method " + node.name + "." + name + desc);
    }
    private static AbstractInsnNode previousReal(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction == null ? null : instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }
    private static Properties load(File file) throws Exception {
        if (!file.isFile()) throw new IllegalArgumentException("catalog missing: " + file.getAbsolutePath());
        Properties properties = new Properties();
        try (InputStream in = new java.io.FileInputStream(file)) { properties.load(in); }
        return properties;
    }
    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) throw new IllegalStateException("tempo catalog entry missing: " + key);
        return value.trim();
    }
    private static String hash(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
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
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return (sun.misc.Unsafe)field.get(null);
    }
    private static void expect(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
        System.out.println("PASS " + message);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
