import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.microemu.MIDletBridge;
import org.microemu.app.Headless;
import org.microemu.app.Common;
import org.microemu.app.util.DeviceEntry;
import org.microemu.app.util.FileRecordStoreManager;
import org.microemu.device.j2se.J2SEDevice;
import org.microemu.util.MemoryRecordStoreManager;
import javax.microedition.rms.RecordStore;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Graphics;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Binary integration fixture for the independent Auto Đánh feature.
 *
 * The runtime section executes the candidate's compiled AutoAttackRuntime and
 * AutoAttackController against deterministic world objects and clock. The
 * native sections below are intentionally catalog-driven so the test follows
 * the exact patched menu, target, cast, request, and cleanup methods in the
 * candidate JAR rather than depending on historical obfuscated names.
 */
public final class HungBySonAutoAttackBinaryTest {
    private static final String RUNTIME = "luna.features.AutoAttackRuntime";
    private static final String CONTROLLER = "luna.features.AutoAttackController";
    private static final String ACCESS = "luna.api.AutoAttackAccess";
    private static final String SETTINGS = "luna.api.AutoAttackSettings";
    private static final String CLOCK = "luna.api.LunaClock";
    private static final int MENU = -110;
    private static final int TOGGLE = -111;
    private static final int MONSTERS = -112;
    private static final int MONSTERS_AND_PLAYERS = -113;
    private static final int DELAY = -114;
    private static int checks;
    private static final List<PopupEvent> nativePopups = new ArrayList<PopupEvent>();
    private static final List<Integer> nativeActions = new ArrayList<Integer>();
    private static final List<String> nativePackets = new ArrayList<String>();
    private static final List<String> nativeSerializedPackets = new ArrayList<String>();
    public static volatile long nativeRequestNow;
    public static volatile int nativeHpMpCalls;
    private static volatile Object nativeRequestSkill;
    private static volatile Object nativeRequestTargets;
    private static volatile Object nativeRequestQ;
    private static volatile CountDownLatch nativeEnqueueEntered;
    private static volatile CountDownLatch nativeEnqueueRelease;

    private HungBySonAutoAttackBinaryTest() { }

    public static void main(String[] args) throws Exception {
        require(args.length == 6,
                "<input.jar> <candidate.jar> <donor.jar> <auto-attack.properties> <microemulator.jar> <reportDir>");
        File input = new File(args[0]);
        File candidate = new File(args[1]);
        File donor = new File(args[2]);
        File catalogFile = new File(args[3]);
        File micro = new File(args[4]);
        File reportDir = new File(args[5]);
        require(input.isFile() && candidate.isFile() && donor.isFile() && micro.isFile(),
                "Input, candidate, donor, and MicroEmulator JARs exist");
        Properties catalog = load(catalogFile);
        verifyHashes(input, candidate, donor, catalog);
        verifyCandidateContract(candidate, catalog);
        verifyControllerBinary(candidate, catalog);
        verifyNativeMenuAndTargetPath(candidate, donor, catalog);
        verifyNativeCastAndRequestPath(input, candidate, donor, catalog);
        System.out.println("NATIVE_PATHS_VERIFIED=true");
        verifySharedFarmDelay(candidate, catalog);
        System.out.println("SHARED_FARM_DELAY_VERIFIED=true");
        verifyManualMovementPriority(candidate, catalog);
        System.out.println("MANUAL_MOVEMENT_PRIORITY_VERIFIED=true");
        System.out.println("MOVEMENT_RESUME_100MS_VERIFIED=true");
        verifyInventoryGameplay(candidate, catalog);
        System.out.println("INVENTORY_AUTO_ATTACK_VERIFIED=true");
        verifyRmsLifecycle(candidate, catalog, micro, reportDir);
        System.out.println("RMS_LIFECYCLE_VERIFIED=true");
        verifyHudSnapshots(candidate, catalog, micro, reportDir);
        System.out.println("HUD_SNAPSHOTS_VERIFIED=true");
        verifyRemoteAnimationBarrier(input, candidate, catalog);
        System.out.println("REMOTE_BARRIER_VERIFIED=true");
        System.out.println("HungBySonAutoAttackBinaryTest PASS checks=" + checks
                + "; actual candidate native menu/target/cast/request/RMS/HUD hooks");
    }

    private static void verifyHashes(File input, File candidate, File donor, Properties catalog)
            throws Exception {
        String inputHash = hash(input);
        String candidateHash = hash(candidate);
        String donorHash = hash(donor);
        require(inputHash.equalsIgnoreCase(requiredAny(catalog, "input.sha256", "source.sha256")),
                "Input JAR hash matches patch catalog");
        require(candidateHash.equalsIgnoreCase(requiredAny(catalog, "output.sha256", "candidate.sha256")),
                "Candidate JAR hash matches patch catalog");
        require(donorHash.equalsIgnoreCase(requiredAny(catalog, "donor.sha256", "reference.sha256")),
                "Donor JAR hash matches patch catalog");
        require(!inputHash.equals(candidateHash), "Candidate is a distinct artifact from its input");
    }

    private static void verifyCandidateContract(File candidate, Properties catalog) throws Exception {
        Map<String, byte[]> entries = readClasses(candidate);
        require(entries.containsKey("luna/api/AutoAttackAccess.class"), "Candidate contains AutoAttackAccess ABI");
        require(entries.containsKey("luna/api/AutoAttackSettings.class"), "Candidate contains AutoAttackSettings ABI");
        require(entries.containsKey("luna/features/AutoAttackController.class"), "Candidate contains semantic controller");
        require(entries.containsKey("luna/features/AutoAttackRuntime.class"), "Candidate contains hook runtime");
        require(entries.containsKey("luna/features/AutoAttackRmsStore.class"), "Candidate contains isolated RMS adapter");
        require(entries.containsKey("luna/generated/HungAutoAttackAccess.class"), "Candidate contains generated native bridge");
        require(entries.containsKey("luna/generated/HungAutoAttackInput.class"), "Candidate contains native delay-input callback");
        require("hbs_auto_attack_v1".equals(requiredAny(catalog, "rms.name", "storage.name")),
                "Catalog names the isolated Auto Attack RMS store");
        require("4".equals(catalog.getProperty("rms.record.bytes", catalog.getProperty("storage.record.bytes", "4"))),
                "Auto Attack settings use the exact four-byte mode/delay record");
        require("650".equals(requiredAny(catalog, "default.delay.ms", "delay.default.ms")),
                "Catalog records the 650ms default");
        require("1".equals(requiredAny(catalog, "delay.minimum.ms", "delay.min.ms")),
                "Catalog records the 1ms minimum");
        require("1000".equals(requiredAny(catalog, "delay.maximum.ms", "delay.max.ms")),
                "Catalog records the 1000ms maximum");
    }

    /** Runtime behavior is exercised from the candidate classes; native binary integration follows below. */
    private static void verifyControllerBinary(File candidate, Properties catalog) throws Exception {
        try (CandidateLoader loader = new CandidateLoader(candidate)) {
            Class<?> runtime = loader.loadClass(RUNTIME);
            Class<?> accessType = loader.loadClass(ACCESS);
            Class<?> settingsType = loader.loadClass(SETTINGS);
            Class<?> clockType = loader.loadClass(CLOCK);
            World world = new World();
            world.runtime = runtime;
            Object access = Proxy.newProxyInstance(loader, new Class<?>[]{accessType}, world);
            int[] persisted = new int[]{0, 650};
            Object settings = Proxy.newProxyInstance(loader, new Class<?>[]{settingsType},
                    new SettingsHandler(persisted));
            Clock clock = new Clock();
            Object nativeClock = Proxy.newProxyInstance(loader, new Class<?>[]{clockType}, clock);
            Method install = runtime.getMethod("installForTest", accessType, settingsType, clockType);
            install.invoke(null, access, settings, nativeClock);

            require(!bool(runtime, "enabled"), "New runtime starts Auto Attack OFF");
            require(!bool(runtime, "playersEnabled"), "Default target mode is monsters only");
            require(integer(runtime, "delayMs") == 650, "Default delay is 650ms");
            require(invoke(runtime, "statusText") == null, "OFF HUD status is absent");

            invokeAction(runtime, MENU);
            require(world.menuCalls == 1 && !world.menuEnabled && world.menuDelay == 650,
                    "Runtime reports OFF/monster/650ms settings state to the native menu bridge");
            invokeAction(runtime, DELAY);
            require(world.delayInputCalls == 1 && world.delayInput == 650,
                    "Delay action opens the native input with the current value");
            invokeAction(runtime, TOGGLE);
            require(bool(runtime, "enabled") && world.stopFarmCalls == 1,
                    "Turning Auto Attack ON first yields the native Farm owner");
            require("Auto Đánh: Chỉ Quái [7]".equals(invoke(runtime, "statusText")),
                    "Monster-only HUD line follows the master toggle");

            Object nearest = new Object();
            Object far = new Object();
            Object friendlyPlayer = new Object();
            Object hostilePlayer = new Object();
            Object nonCombat = new Object();
            world.entities.add(nearest);
            world.entities.add(far);
            world.entities.add(friendlyPlayer);
            world.entities.add(hostilePlayer);
            world.entities.add(nonCombat);
            world.distances.put(nearest, Integer.valueOf(120));
            world.distances.put(far, Integer.valueOf(800));
            world.distances.put(friendlyPlayer, Integer.valueOf(10));
            world.distances.put(hostilePlayer, Integer.valueOf(90));
            world.distances.put(nonCombat, Integer.valueOf(1));
            world.kinds.put(nearest, "monster");
            world.kinds.put(far, "monster");
            world.kinds.put(friendlyPlayer, "friendly-player");
            world.kinds.put(hostilePlayer, "hostile-player");
            world.kinds.put(nonCombat, "other");

            invoke(runtime, "tick");
            require(invoke(runtime, "target") == nearest,
                    "Monster mode chooses the nearest valid monster and skips nearer player/other objects");
            Object tied = new Object();
            world.entities.add(tied);
            world.distances.put(tied, Integer.valueOf(120));
            world.kinds.put(tied, "monster");
            invoke(runtime, "tick");
            require(invoke(runtime, "target") == nearest,
                    "Native entity order breaks equal-distance ties in favor of the first target");

            clock.now = 650L;
            invoke(runtime, "tick");
            require(world.attacks == 0, "Strict delay waits through exactly 650ms");
            clock.now = 651L;
            invoke(runtime, "tick");
            require(world.attacks == 1 && world.lastAttackTarget == nearest,
                    "Attack resumes at 651ms using the retained native target");
            int tag = world.lastAttackTag;
            require(tag != 0 && integerArg(runtime, "requestMinimum", tag) == 650,
                    "Native attack callback captures a dynamic delay tag");
            require(boolArg(runtime, "allowRequest", tag, nearest),
                    "Current attack generation authorizes its target request");
            require(boolArg(runtime, "allowRequest", 0, friendlyPlayer),
                    "Untagged native Farm requests keep their legacy path");

            invokeAction(runtime, MONSTERS_AND_PLAYERS);
            require(bool(runtime, "enabled") && bool(runtime, "playersEnabled"),
                    "Selecting Auto quái + PK sets mode and enables the master");
            require(invoke(runtime, "target") == null,
                    "Mode change invalidates the old target and cast generation");
            invoke(runtime, "tick");
            require(invoke(runtime, "target") == hostilePlayer,
                    "PK mode selects hostile players but still rejects friendly players and other types");
            require("Auto Đánh: Quái & PK [6]".equals(invoke(runtime, "statusText")),
                    "PK mode updates HUD status");
            require(!boolStringArg(runtime, "submitDelay", "0"), "Delay 0ms is rejected");
            require(!boolStringArg(runtime, "submitDelay", "1001"), "Delay 1001ms is rejected");
            require(!boolStringArg(runtime, "submitDelay", "bad"), "Nonnumeric delay is rejected");
            require(integer(runtime, "delayMs") == 650, "Invalid delay leaves the current setting unchanged");
            require(boolStringArg(runtime, "submitDelay", "1"), "Delay minimum 1ms is accepted");
            clock.now = 652L;
            invoke(runtime, "tick");
            require(world.attacks == 1, "Delay 1ms remains strict at its exact boundary");
            clock.now = 653L;
            invoke(runtime, "tick");
            require(world.attacks == 2, "Delay 1ms opens at 2ms");
            require(integerArg(runtime, "requestMinimum", world.lastAttackTag) == 1,
                    "Tagged native requests use the selected 1ms delay");

            require(boolStringArg(runtime, "submitDelay", "1000"), "Delay maximum 1000ms is accepted");
            clock.now = 1653L;
            invoke(runtime, "tick");
            require(world.attacks == 2, "Delay 1000ms remains strict at its exact boundary");
            clock.now = 1654L;
            invoke(runtime, "tick");
            require(world.attacks == 3, "Delay 1000ms opens at 1001ms");

            int staleTag = world.lastAttackTag;
            invoke(runtime, "beforeFarmEnable");
            require(!bool(runtime, "enabled") && invoke(runtime, "target") == null,
                    "Farm handoff stops Auto Attack and clears its retained target");
            require(!boolArg(runtime, "allowRequest", staleTag, hostilePlayer),
                    "Farm handoff invalidates an in-flight tagged cast");
            require(integerArg(runtime, "requestMinimum", 0) == 650
                    && boolArg(runtime, "allowRequest", 0, hostilePlayer),
                    "Untagged Farm cadence and native request path stay unchanged");

            persisted[0] = 1; persisted[1] = 1000;
            world.ready = false;
            runtime.getMethod("installForTest", accessType, settingsType, clockType)
                    .invoke(null, access, settings, nativeClock);
            require(!bool(runtime, "enabled") && bool(runtime, "playersEnabled")
                    && integer(runtime, "delayMs") == 1000,
                    "Reloaded settings retain mode/delay while the session master remains OFF");
            require(invoke(runtime, "statusText") == null, "Persisted mode does not auto-enable after restart");
            invokeAction(runtime, TOGGLE);
            require(bool(runtime, "enabled") && bool(runtime, "playersEnabled"),
                    "Manual master ON restores the persisted PK mode");
            invokeAction(runtime, TOGGLE);
            require(!bool(runtime, "enabled") && bool(runtime, "playersEnabled")
                    && invoke(runtime, "statusText") == null,
                    "Master OFF hides HUD but preserves selected mode for this session");
        }
    }

    private static boolean boolStringArg(Class<?> runtime, String method, String value) throws Exception {
        return ((Boolean)runtime.getMethod(method, String.class).invoke(null, value)).booleanValue();
    }

    /** Replacement for the native popup paint endpoint; all native rows are constructed first. */
    public static void captureNativePopup(Object display, Object items, int mode,
            String title, boolean flag, Object trailing) {
        nativePopups.add(new PopupEvent(items, title, mode));
    }

    /** Observes the real byte-to-int callback path while letting the native dispatcher continue. */
    public static void captureNativeAction(int first, int second) {
        nativeActions.add(Integer.valueOf(first));
    }

    public static void captureNativeHpMpCall() { nativeHpMpCalls++; }

    public static Object provideNativeSkill(int slot) { return nativeRequestSkill; }
    public static Object provideNativeTargets(Object actor, int skill, Object source) { return nativeRequestTargets; }
    public static Object provideNativeQ() { return nativeRequestQ; }
    public static void captureNativeVisual(int action, int x, byte type, Object targets) { }

    public static void captureNativePacket(Object packet) throws Exception {
        Field opcode = field(packet.getClass(), "a");
        Method bytes = method(packet.getClass(), encoded("lliilil"), "()[B");
        String value = opcode.getByte(packet) + ":" + Arrays.toString((byte[])invoke(bytes, packet));
        synchronized (nativeSerializedPackets) { nativeSerializedPackets.add(value); }
    }

    public static void captureNativeEnqueue(Object sender) throws Exception {
        Field buffer = null;
        String bufferType = encoded("illlllil").replace('/', '.');
        for (Field field : allFields(sender.getClass())) {
            if (bufferType.equals(field.getType().getName())) { buffer = field; break; }
        }
        if (buffer == null) throw new NoSuchFieldException(sender.getClass().getName() + " message buffer");
        buffer.setAccessible(true);
        Object message = buffer.get(sender);
        if (message == null) throw new AssertionError("Native sender has no serialized message");
        Field opcode = field(message.getClass(), "a");
        Method bytes = null;
        for (Method candidate : message.getClass().getDeclaredMethods()) {
            if (candidate.getParameterTypes().length == 0 && candidate.getReturnType() == byte[].class) {
                bytes = candidate; break;
            }
        }
        if (bytes == null) throw new NoSuchMethodException(message.getClass().getName() + " byte payload");
        bytes.setAccessible(true);
        String value = opcode.getByte(message) + ":" + Arrays.toString((byte[])invoke(bytes, message));
        synchronized (nativePackets) { nativePackets.add(value); }
        CountDownLatch entered = nativeEnqueueEntered;
        CountDownLatch release = nativeEnqueueRelease;
        int op = opcode.getByte(message);
        // The same native queue carries unrelated movement/interaction packets (for example
        // opcode 40). Keep recording those, but only let real skill requests satisfy the
        // attack-send latch used by the atomic Farm handoff test.
        if ((op == 6 || op == 9) && entered != null && release != null) {
            entered.countDown();
            if (!release.await(8L, TimeUnit.SECONDS)) throw new AssertionError("native enqueue latch timed out");
        }
    }

    private static String encoded(String suffix) {
        StringBuilder value = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) value.append('i');
        return value.append(suffix).toString();
    }

    private static Object newNativeList(Class<?> listType, String title) throws Exception {
        try {
            Constructor<?> constructor = listType.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(title);
        } catch (NoSuchMethodException absent) {
            Constructor<?> constructor = listType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }
    }

    private static int listSize(Object list) throws Exception {
        Method method = list.getClass().getMethod("c");
        method.setAccessible(true);
        return ((Integer)invoke(method, list)).intValue();
    }

    private static Object listGet(Object list, int index) throws Exception {
        Method method = list.getClass().getMethod("a", Integer.TYPE);
        method.setAccessible(true);
        return invoke(method, list, Integer.valueOf(index));
    }

    private static String nativeLabel(Object item) throws Exception {
        for (Field field : allFields(item.getClass())) {
            if (field.getType() != String.class) continue;
            field.setAccessible(true);
            Object value = field.get(item);
            if (value instanceof String && (((String)value).startsWith("Auto ")
                    || ((String)value).startsWith("Delay "))) return (String)value;
        }
        throw new AssertionError("Native Auto Attack row has no expected label");
    }

    private static int nativeAction(Object item) throws Exception {
        for (Field field : allFields(item.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if (field.getType() == Byte.TYPE) {
                int value = field.getByte(item);
                if (value <= -110 && value >= -114) return value;
            } else if (field.getType() == Short.TYPE) {
                int value = field.getShort(item);
                if (value <= -110 && value >= -114) return value;
            } else if (field.getType() == Integer.TYPE) {
                int value = field.getInt(item);
                if (value <= -110 && value >= -114) return value;
            }
        }
        throw new AssertionError("Native bt row does not retain a signed Auto Attack action byte");
    }

    private static Method findNativeClick(Class<?> itemType) throws Exception {
        Method method = itemType.getDeclaredMethod("a");
        method.setAccessible(true);
        return method;
    }

    private static Object allocate(ClassLoader loader, String descriptor) throws Exception {
        if (descriptor == null || descriptor.length() < 3 || descriptor.charAt(0) != 'L')
            throw new IllegalArgumentException("Expected object descriptor: " + descriptor);
        String name = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        Class<?> type = Class.forName(name, false, loader);
        return unsafeAllocate(type);
    }

    private static Object unsafeAllocate(Class<?> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        return Class.forName("sun.misc.Unsafe").getMethod("allocateInstance", Class.class)
                .invoke(unsafe, type);
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> result = new ArrayList<Field>();
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
            result.addAll(Arrays.asList(cursor.getDeclaredFields()));
        return result;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field result = cursor.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method method(Class<?> type, String name, String descriptor) throws Exception {
        for (Method method : type.getDeclaredMethods()) {
            if (name.equals(method.getName()) && Type.getMethodDescriptor(method).equals(descriptor)) {
                method.setAccessible(true); return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + descriptor);
    }

    private static void setField(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = field(owner, name);
        if (field.getType() == Boolean.TYPE) field.setBoolean(target, ((Boolean)value).booleanValue());
        else if (field.getType() == Byte.TYPE) field.setByte(target, ((Byte)value).byteValue());
        else if (field.getType() == Short.TYPE) field.setShort(target, ((Short)value).shortValue());
        else if (field.getType() == Integer.TYPE) field.setInt(target, ((Integer)value).intValue());
        else if (field.getType() == Long.TYPE) field.setLong(target, ((Long)value).longValue());
        else field.set(target, value);
    }

    private static void setStaticField(Class<?> owner, String name, Object value) throws Exception {
        Field member = field(owner, name);
        try { setField(null, owner, name, value); }
        catch (IllegalAccessException denied) {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeType.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Object base = unsafeType.getMethod("staticFieldBase", Field.class).invoke(unsafe, member);
            long offset = ((Long)unsafeType.getMethod("staticFieldOffset", Field.class).invoke(unsafe, member)).longValue();
            Class<?> type = member.getType();
            String setter = type == Boolean.TYPE ? "putBoolean" : type == Byte.TYPE ? "putByte"
                    : type == Short.TYPE ? "putShort" : type == Integer.TYPE ? "putInt"
                    : type == Long.TYPE ? "putLong" : null;
            if (setter == null) unsafeType.getMethod("putObject", Object.class, Long.TYPE, Object.class)
                    .invoke(unsafe, base, Long.valueOf(offset), value);
            else unsafeType.getMethod(setter, Object.class, Long.TYPE, type)
                    .invoke(unsafe, base, Long.valueOf(offset), value);
        }
    }

    private static Object descriptorType(ClassLoader loader, String descriptor) throws Exception {
        return allocate(loader, descriptor);
    }

    private static void addNativeListItem(Properties catalog, Object list, Object item) throws Exception {
        Method add = list.getClass().getMethod(requiredAny(catalog, "native.list.add.method", "native.list.add"), Object.class);
        add.setAccessible(true);
        invoke(add, list, item);
    }

    private static Object invokeNative(Object receiver, String name, Class<?>[] types, Object[] args) throws Exception {
        Method method = receiver.getClass().getMethod(name, types);
        method.setAccessible(true);
        return invoke(method, receiver, args);
    }

    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try { return method.invoke(receiver, args); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new IllegalStateException(cause);
        }
    }

    private static String methodCode(MethodNode method) {
        StringBuilder value = new StringBuilder();
        List<AbstractInsnNode> code = real(method);
        for (AbstractInsnNode instruction : code) {
            value.append(instruction.getOpcode()).append(':');
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                value.append(field.owner).append('.').append(field.name).append(field.desc);
            } else if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                value.append(call.owner).append('.').append(call.name).append(call.desc);
            } else if (instruction instanceof org.objectweb.asm.tree.VarInsnNode)
                value.append(((org.objectweb.asm.tree.VarInsnNode)instruction).var);
            else if (instruction instanceof org.objectweb.asm.tree.IntInsnNode)
                value.append(((org.objectweb.asm.tree.IntInsnNode)instruction).operand);
            else if (instruction instanceof org.objectweb.asm.tree.LdcInsnNode)
                value.append(((org.objectweb.asm.tree.LdcInsnNode)instruction).cst);
            value.append(';');
        }
        return value.toString();
    }

    private static boolean isVar(AbstractInsnNode instruction, int opcode, int index) {
        return instruction instanceof org.objectweb.asm.tree.VarInsnNode
                && instruction.getOpcode() == opcode
                && ((org.objectweb.asm.tree.VarInsnNode)instruction).var == index;
    }

    private static byte[] readJarClass(File jarFile, String internalName) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry(internalName + ".class");
            if (entry == null) throw new AssertionError("Missing class " + internalName + " in " + jarFile);
            return readAll(jar.getInputStream(entry));
        }
    }

    private static final class PopupEvent {
        final Object items;
        final String title;
        final int mode;
        PopupEvent(Object items, String title, int mode) {
            this.items = items; this.title = title; this.mode = mode;
        }
    }

    private static final class NativeProbeLoader extends URLClassLoader {
        private static final String HOST = "HungBySonAutoAttackBinaryTest";
        private final File jarFile;
        private final JarFile jar;
        private final String displayOwner, displayName, displayDescriptor;
        private final String callbackOwner, callbackDescriptor;
        private final String ownedOwner, ownedName, ownedDescriptor;
        private final String hpMpOwner, hpMpMethod, hpMpDescriptor;
        private final String farmGateOwner, farmGateMethod, farmGateDescriptor;
        private final String networkOwner = encoded("lililili");
        private final String networkMethod = encoded("lliiili");
        private final String messageOwner = encoded("lllllil");
        private final String enqueueOwner = encoded("lllilii");
        private final String enqueueMethod = encoded("lliiiii");

        NativeProbeLoader(File jarFile, Properties catalog) throws Exception {
            super(new URL[]{jarFile.toURI().toURL()}, HungBySonAutoAttackBinaryTest.class.getClassLoader());
            this.jarFile = jarFile;
            this.jar = new JarFile(jarFile);
            displayOwner = requiredAny(catalog, "native.menu.display.owner", "menu.display.owner");
            displayName = requiredAny(catalog, "native.menu.display.method", "menu.display.method");
            displayDescriptor = requiredAny(catalog, "native.menu.display.descriptor", "menu.display.descriptor");
            callbackOwner = requiredAny(catalog, "native.menu.callback.owner", "menu.callback.owner");
            callbackDescriptor = requiredAny(catalog, "native.menu.callback.descriptor", "menu.callback.descriptor");
            ownedOwner = catalog.getProperty("native.ownedRequest.owner", "");
            ownedName = catalog.getProperty("native.ownedRequest.method", "");
            ownedDescriptor = catalog.getProperty("native.ownedRequest.descriptor", "");
            hpMpOwner = requiredAny(catalog, "native.hpmp.owner", "farm.hpmp.owner");
            hpMpMethod = requiredAny(catalog, "native.hpmp.method", "farm.hpmp.method");
            hpMpDescriptor = requiredAny(catalog, "native.hpmp.descriptor", "farm.hpmp.descriptor");
            farmGateOwner = catalog.getProperty("hook.farmAutoGate.owner", "");
            farmGateMethod = catalog.getProperty("hook.farmAutoGate.method", "");
            farmGateDescriptor = catalog.getProperty("hook.farmAutoGate.descriptor", "");
            nativePopups.clear(); nativeActions.clear();
            synchronized (nativePackets) { nativePackets.clear(); }
            synchronized (nativeSerializedPackets) { nativeSerializedPackets.clear(); }
        }

        PopupEvent lastPopup() {
            return nativePopups.isEmpty() ? null : nativePopups.get(nativePopups.size() - 1);
        }

        List<Integer> actionCalls = nativeActions;

        @Override protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.")
                    || name.startsWith("org.objectweb.asm.") || name.startsWith("org.microemu."))
                return super.loadClass(name, resolve);
            String entryName = name.replace('.', '/') + ".class";
            if (jar.getJarEntry(entryName) == null) return super.loadClass(name, resolve);
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
                ClassNode node = parse(readAll(jar.getInputStream(entry)));
                for (MethodNode method : node.methods) {
                    if (internal.equals(hpMpOwner) && hpMpMethod.equals(method.name)
                            && hpMpDescriptor.equals(method.desc)) {
                        AbstractInsnNode firstHpMp = method.instructions.getFirst();
                        while (firstHpMp != null && firstHpMp.getOpcode() < 0) firstHpMp = firstHpMp.getNext();
                        if (firstHpMp == null) throw new AssertionError("Native HP/MP helper is empty");
                        method.instructions.insertBefore(firstHpMp,
                                new MethodInsnNode(Opcodes.INVOKESTATIC, HOST, "captureNativeHpMpCall", "()V", false));
                    }
                    if (internal.equals(callbackOwner) && callbackDescriptor.equals(method.desc)) {
                        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
                        org.objectweb.asm.tree.InsnList trace = new org.objectweb.asm.tree.InsnList();
                        trace.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, isStatic ? 0 : 1));
                        trace.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, isStatic ? 1 : 2));
                        trace.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOST, "captureNativeAction", "(II)V", false));
                        AbstractInsnNode first = method.instructions.getFirst();
                        while (first != null && first.getOpcode() < 0) first = first.getNext();
                        if (first == null) throw new AssertionError("Empty native action callback " + internal + method.desc);
                        method.instructions.insertBefore(first, trace);
                    }
                    if (internal.equals(ownedOwner) && ownedName.equals(method.name)
                            && ownedDescriptor.equals(method.desc)) transformOwnedRequest(method);
                    for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
                        AbstractInsnNode next = instruction.getNext();
                        if (instruction instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode)instruction;
                            if (internal.equals(farmGateOwner) && farmGateMethod.equals(method.name)
                                    && farmGateDescriptor.equals(method.desc)
                                    && call.getOpcode() == Opcodes.INVOKESTATIC
                                    && "java/lang/System".equals(call.owner)
                                    && "currentTimeMillis".equals(call.name) && "()J".equals(call.desc)) {
                                method.instructions.set(call, new FieldInsnNode(Opcodes.GETSTATIC, HOST,
                                        "nativeRequestNow", "J"));
                            }
                            if (displayOwner.equals(call.owner) && displayName.equals(call.name)
                                    && displayDescriptor.equals(call.desc)
                                    && (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                                            || call.getOpcode() == Opcodes.INVOKEINTERFACE)) {
                                method.instructions.set(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                        HOST, "captureNativePopup",
                                        "(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/String;ZLjava/lang/Object;)V", false));
                            }
                        }
                        instruction = next;
                    }
                }
                if (internal.equals(networkOwner)) transformNetworkSender(node);
                if (internal.equals(enqueueOwner)) transformNativeEnqueue(node);
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                byte[] bytes = writer.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (ClassNotFoundException failure) { throw failure; }
            catch (Throwable failure) { throw new ClassNotFoundException(name, failure); }
        }

        private void transformOwnedRequest(MethodNode method) {
            int clocks = 0, skills = 0, targets = 0, dispatches = 0, qGetters = 0;
            String serializerOwner = null;
            for (AbstractInsnNode instruction : method.instructions.toArray())
                if (instruction instanceof MethodInsnNode && instruction.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && "(Let;B)V".equals(((MethodInsnNode)instruction).desc))
                    serializerOwner = ((MethodInsnNode)instruction).owner;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
                AbstractInsnNode next = instruction.getNext();
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (call.getOpcode() == Opcodes.INVOKESTATIC && "java/lang/System".equals(call.owner)
                            && "currentTimeMillis".equals(call.name) && "()J".equals(call.desc)) {
                        method.instructions.set(call, new FieldInsnNode(Opcodes.GETSTATIC, HOST,
                                "nativeRequestNow", "J")); clocks++;
                    } else if (call.getOpcode() == Opcodes.INVOKESTATIC && call.desc.startsWith("(I)L")
                            && !"java/lang/String".equals(call.owner)) {
                        String result = Type.getReturnType(call.desc).getInternalName();
                        MethodInsnNode replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, HOST,
                                "provideNativeSkill", "(I)Ljava/lang/Object;", false);
                        method.instructions.set(call, replacement);
                        method.instructions.insert(replacement,
                                new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, result));
                        skills++;
                    } else if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.desc.startsWith("(IL")
                            && call.desc.endsWith(")Let;")) {
                        MethodInsnNode replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, HOST,
                                "provideNativeTargets", "(Ljava/lang/Object;ILjava/lang/Object;)Ljava/lang/Object;", false);
                        method.instructions.set(call, replacement);
                        method.instructions.insert(replacement,
                                new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, "et"));
                        targets++;
                    } else if (call.getOpcode() == Opcodes.INVOKESTATIC && "(IIBLet;)V".equals(call.desc)) {
                        method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOST,
                                "captureNativeVisual", "(IIBLjava/lang/Object;)V", false));
                        dispatches++;
                    } else if (call.getOpcode() == Opcodes.INVOKESTATIC && serializerOwner != null
                            && serializerOwner.equals(call.owner) && call.desc.equals("()L" + call.owner + ";")) {
                        MethodInsnNode replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, HOST,
                                "provideNativeQ", "()Ljava/lang/Object;", false);
                        method.instructions.set(call, replacement);
                        method.instructions.insert(replacement,
                                new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, call.owner));
                        qGetters++;
                    }
                }
                instruction = next;
            }
            if (clocks != 0) require(clocks == 2, "Owned native request has two deterministic time reads");
            if (skills != 0) require(skills == 1, "Owned native request has one native skill lookup");
            if (targets != 0) require(targets == 1, "Owned native request has one native target-list lookup");
            if (dispatches != 0) require(dispatches == 1, "Owned native request has one visual-dispatch substitution");
            if (qGetters != 0) require(qGetters >= 2, "Opcode-6 and opcode-9 serializers share the native Q route");
        }

        private void transformNetworkSender(ClassNode node) {
            int found = 0;
            for (MethodNode method : node.methods) {
                if (!networkMethod.equals(method.name) || !("(L" + messageOwner + ";)V").equals(method.desc)) continue;
                method.instructions.clear(); method.tryCatchBlocks.clear(); method.localVariables = null;
                method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOST,
                        "captureNativePacket", "(Ljava/lang/Object;)V", false));
                method.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
                method.maxLocals = 2; method.maxStack = 1; found++;
            }
            require(found == 1, "Native serializer network boundary redirects one packet to the in-memory capture");
        }

        private void transformNativeEnqueue(ClassNode node) {
            int found = 0;
            for (MethodNode method : node.methods) {
                if (!enqueueMethod.equals(method.name) || !"()V".equals(method.desc)) continue;
                method.instructions.clear(); method.tryCatchBlocks.clear(); method.localVariables = null;
                method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOST,
                        "captureNativeEnqueue", "(Ljava/lang/Object;)V", false));
                method.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
                method.maxLocals = 1; method.maxStack = 1; found++;
            }
            require(found == 1, "Native serializer queue boundary redirects one completed message to the latch sink");
        }

        @Override public void close() throws java.io.IOException {
            try { jar.close(); } finally { super.close(); }
        }
    }

    private static final class NativeAccess implements InvocationHandler {
        final ClassLoader loader;
        final Object bridge;
        final Properties catalog;
        final List<Object> entities = new ArrayList<Object>();
        final java.util.Map<String, Object> farmBefore = new java.util.LinkedHashMap<String, Object>();
        final String farmEnabledField;
        Object player, entityList;
        Object modal;
        Object lastAttackTarget;
        int lastAttackTag;
        boolean modePlayers, enabled;
        boolean runNativeSkill;
        Object nativeSkillResult;
        Throwable nativeSkillFailure;
        int stopFarmCalls, attackCalls;

        NativeAccess(ClassLoader loader, Object bridge, Properties catalog) throws Exception {
            this.loader = loader; this.bridge = bridge; this.catalog = catalog;
            farmEnabledField = requiredAny(catalog, "native.farm.enabled.field", "native.farm.enabledField");
        }

        void installReadyWorld() throws Exception {
            Class<?> playerType = loader.loadClass(requiredAny(catalog, "native.player.owner", "player.owner").replace('/', '.'));
            Class<?> entityType = loader.loadClass(requiredAny(catalog, "native.entity.owner", "entity.owner").replace('/', '.'));
            player = unsafeAllocate(playerType);
            setField(player, entityType, requiredAny(catalog, "native.entity.hp.field", "entity.hp.field"), Integer.valueOf(100));
            setField(player, entityType, requiredAny(catalog, "native.entity.state.field", "entity.state.field"), Integer.valueOf(2));
            // Native cv is the entity-kind byte: local players use 0, monsters use 1.
            // Marking our local actor as a monster routes the real serializer through
            // the NPC opcode-40 branch and never exercises the player skill opcodes.
            setField(player, entityType, requiredAny(catalog, "native.entity.type.field", "entity.type.field"), Byte.valueOf((byte)0));
            setField(player, entityType, requiredAny(catalog, "native.entity.excluded.field", "entity.excluded.field"), Boolean.FALSE);
            setField(player, entityType, "aZ", Integer.valueOf(0)); setField(player, entityType, "ba", Integer.valueOf(0));
            setField(player, entityType, "cA", Integer.valueOf(0));
            setField(player, entityType, "cw", Byte.valueOf((byte)1));
            initializeNativeActorHooks(player, playerType, catalog);

            String singletonOwner = requiredAny(catalog, "native.player.singleton.owner", "player.singleton.owner");
            setStaticField(loader.loadClass(singletonOwner.replace('/', '.')),
                    requiredAny(catalog, "native.player.singleton.field", "player.singleton.field"), player);

            Class<?> mapOwner = loader.loadClass(requiredAny(catalog, "native.map.owner", "map.owner").replace('/', '.'));
            String mapField = requiredAny(catalog, "native.map.field", "map.field");
            Field mapRef = field(mapOwner, mapField);
            Object map = unsafeAllocate(mapRef.getType());
            setField(map, mapRef.getType(), "d", Integer.valueOf(1));
            mapRef.set(null, map);
            setStaticField(mapRef.getType(), "u", Byte.valueOf((byte)2));

            entityList = newNativeList(loader.loadClass("et"), "Auto Attack test world");
            setStaticField(loader.loadClass(requiredAny(catalog, "native.list.owner", "list.owner").replace('/', '.')),
                    requiredAny(catalog, "native.list.field", "list.field"), entityList);

            String listenerDescriptor = requiredAny(catalog, "native.listener.descriptor", "menu.listener.descriptor");
            Object listener = allocate(loader, listenerDescriptor);
            setStaticField(loader.loadClass(requiredAny(catalog, "native.listener.owner", "menu.listener.owner").replace('/', '.')),
                    requiredAny(catalog, "native.listener.field", "menu.listener.field"), listener);
            setStaticField(loader.loadClass(requiredAny(catalog, "ui.currentScreen.owner", "ui.currentScreen.owner").replace('/', '.')),
                    requiredAny(catalog, "ui.currentScreen.field", "ui.currentScreen.field"), listener);
            Class<?> popupOwner = loader.loadClass(requiredAny(catalog, "native.popup.owner", "menu.popup.owner").replace('/', '.'));
            Object popup = allocate(loader, requiredAny(catalog, "native.popup.descriptor", "menu.popup.descriptor"));
            setStaticField(popupOwner, requiredAny(catalog, "native.popup.field", "menu.popup.field"), popup);
            setStaticField(loader.loadClass(requiredAny(catalog, "native.world.entered.owner", "world.entered.owner").replace('/', '.')),
                    requiredAny(catalog, "native.world.entered.field", "world.entered.field"), Boolean.TRUE);
            setStaticField(loader.loadClass(requiredAny(catalog, "native.connection.wait.owner", "connection.wait.owner").replace('/', '.')),
                    requiredAny(catalog, "native.connection.wait.field", "connection.wait.field"), Boolean.FALSE);
            Class<?> connectionOwner = loader.loadClass(requiredAny(catalog, "native.connection.instance.owner", "connection.owner").replace('/', '.'));
            Object connection = unsafeAllocate(connectionOwner);
            setField(connection, connectionOwner,
                    requiredAny(catalog, "native.connection.connected.field", "connection.connected.field"), Boolean.TRUE);
            setStaticField(connectionOwner, requiredAny(catalog, "native.connection.instance.field", "connection.instance.field"), connection);
            Class<?> input = loader.loadClass("luna.generated.HungAutoAttackInput");
            setStaticField(input, "editing", Boolean.FALSE);
            setNativePkGate();
            snapshotFarm();

            String registry = catalog.getProperty("hook.registry.owner");
            if (registry != null) Class.forName(registry.replace('/', '.'), true, loader);
        }

        Object entity(int hp, byte kind, int state, int excluded, int hostility,
                int x, int y) throws Exception {
            Class<?> entityType = loader.loadClass(requiredAny(catalog, "native.entity.owner", "entity.owner").replace('/', '.'));
            Object value;
            if (kind == 0) {
                value = unsafeAllocate(loader.loadClass(requiredAny(catalog, "native.player.owner", "player.owner").replace('/', '.')));
            } else {
                Class<?> mobType = loader.loadClass(encoded("llllil").replace('/', '.'));
                Constructor<?> constructor = mobType.getDeclaredConstructor(Integer.TYPE, Integer.TYPE,
                        Integer.TYPE, String.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
                constructor.setAccessible(true);
                value = constructor.newInstance(Integer.valueOf(41), Integer.valueOf(1), Integer.valueOf(0),
                        "Auto Attack fixture mob", Integer.valueOf(0), Integer.valueOf(x), Integer.valueOf(y),
                        Integer.valueOf(hp));
            }
            setField(value, entityType, requiredAny(catalog, "native.entity.hp.field", "entity.hp.field"), Integer.valueOf(hp));
            setField(value, entityType, requiredAny(catalog, "native.entity.type.field", "entity.type.field"), Byte.valueOf(kind));
            setField(value, entityType, requiredAny(catalog, "native.entity.state.field", "entity.state.field"), Integer.valueOf(state));
            setField(value, entityType, requiredAny(catalog, "native.entity.excluded.field", "entity.excluded.field"), Boolean.valueOf(excluded != 0));
            setField(value, entityType, "aZ", Integer.valueOf(x)); setField(value, entityType, "ba", Integer.valueOf(y));
            setField(value, entityType, "cu", Integer.valueOf(41));
            if (kind == 0) {
                setField(value, entityType, "dL", Boolean.valueOf(hostility != 0));
                setField(value, entityType, "cw", Byte.valueOf((byte)(hostility != 0 ? 2 : 1)));
            }
            return value;
        }

        void replaceEntities(Object[] values) throws Exception {
            entityList = newNativeList(loader.loadClass("et"), "Auto Attack test world");
            setStaticField(loader.loadClass(requiredAny(catalog, "native.list.owner", "list.owner").replace('/', '.')),
                    requiredAny(catalog, "native.list.field", "list.field"), entityList);
            entities.clear();
            for (Object value : values) { entities.add(value); addNativeListItem(catalog, entityList, value); }
        }

        void setReadiness(String prefix, Object value) throws Exception {
            setStaticField(loader.loadClass(requiredAny(catalog, prefix + ".owner", prefix + ".owner").replace('/', '.')),
                    requiredAny(catalog, prefix + ".field", prefix + ".field"), value);
        }

        void setBoolean(String prefix, boolean value) throws Exception {
            setStaticField(loader.loadClass(requiredAny(catalog, prefix + ".owner", prefix + ".owner").replace('/', '.')),
                    requiredAny(catalog, prefix + ".field", prefix + ".field"), Boolean.valueOf(value));
        }

        void clearReadiness() throws Exception { setReadiness("ui.activeDialog", null); }

        boolean farmConfigUnchanged() throws Exception {
            Class<?> owner = loader.loadClass(requiredAny(catalog, "native.farm.enabled.owner", "farm.enabled.owner").replace('/', '.'));
            for (Field field : allFields(owner)) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                if (farmEnabledField.equals(field.getName())) continue;
                Object before = farmBefore.get(field.getName());
                Object after = field.get(null);
                if (!sameSnapshotValue(before, after)) return false;
            }
            return true;
        }

        boolean farmEnabled() throws Exception {
            Class<?> owner = loader.loadClass(requiredAny(catalog, "native.farm.enabled.owner", "farm.enabled.owner").replace('/', '.'));
            return field(owner, farmEnabledField).getBoolean(null);
        }

        private boolean sameSnapshotValue(Object before, Object after) {
            if (before == after) return true;
            if (before == null || after == null) return false;
            if (before.getClass().isArray() && after.getClass() == before.getClass()) {
                if (before instanceof byte[]) return Arrays.equals((byte[])before, (byte[])after);
                if (before instanceof int[]) return Arrays.equals((int[])before, (int[])after);
                if (before instanceof short[]) return Arrays.equals((short[])before, (short[])after);
                if (before instanceof boolean[]) return Arrays.equals((boolean[])before, (boolean[])after);
            }
            return before.equals(after);
        }

        private void snapshotFarm() throws Exception {
            Class<?> owner = loader.loadClass(requiredAny(catalog, "native.farm.enabled.owner", "farm.enabled.owner").replace('/', '.'));
            Field enabled = field(owner, farmEnabledField);
            enabled.setBoolean(null, true);
            for (Field field : allFields(owner)) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                farmBefore.put(field.getName(), field.get(null));
            }
        }

        private void setNativePkGate() throws Exception {
            Class<?> playerType = loader.loadClass(requiredAny(catalog, "native.player.owner", "player.owner").replace('/', '.'));
            String pkField = catalog.getProperty("native.player.pkFlag.field");
            if (pkField != null) setField(player, playerType, pkField, Boolean.TRUE);
            Class<?> cs = loader.loadClass("cs");
            Method pk = cs.getDeclaredMethod("a");
            if (pk.getReturnType() == Boolean.TYPE && pk.getParameterTypes().length == 0) {
                ClassNode node = parse(readJarClass(jarFileFor(loader), "cs"));
                MethodNode method = findMethod(node, pk.getName(), "()Z");
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof FieldInsnNode && instruction.getOpcode() == Opcodes.GETSTATIC) {
                        FieldInsnNode f = (FieldInsnNode)instruction;
                        if ("Z".equals(f.desc)) {
                            setStaticField(loader.loadClass(f.owner.replace('/', '.')), f.name, Boolean.FALSE);
                            break;
                        }
                    }
                }
            }
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("stopFarm".equals(name)) {
                stopFarmCalls++;
                try { return invokeNative(bridge, name, method.getParameterTypes(), args); }
                catch (Throwable failure) { throw failure; }
            }
            if ("attack".equals(name)) {
                attackCalls++; lastAttackTarget = args[0];
                Class<?> runtime = loader.loadClass(RUNTIME);
                lastAttackTag = ((Integer)runtime.getMethod("captureGeneration").invoke(null)).intValue();
                if (runNativeSkill) {
                    try {
                        nativeSkillResult = invokeNative(bridge, name, method.getParameterTypes(), args);
                        return nativeSkillResult;
                    } catch (Throwable failure) {
                        nativeSkillFailure = failure;
                        return Boolean.FALSE;
                    }
                }
                return Boolean.FALSE;
            }
            if ("showMenu".equals(name)) {
                enabled = ((Boolean)args[0]).booleanValue(); modePlayers = ((Boolean)args[1]).booleanValue();
            }
            return invokeNative(bridge, name, method.getParameterTypes(), args);
        }

        private File jarFileFor(ClassLoader ignored) { return ((NativeProbeLoader)loader).jarFile; }
    }

    private static void initializeNativeActorHooks(Object player, Class<?> playerType, Properties catalog)
            throws Exception {
        Class<?> castType = loaderClass(playerType, requiredAny(catalog, "native.cast.owner", "cast.owner"));
        Object cast = unsafeAllocate(castType);
        setField(cast, castType, requiredAny(catalog, "request.tag.field", "native.cast.tag.field"), Integer.valueOf(0));
        String startSerial = catalog.getProperty("native.cast.startSerial.field");
        if (startSerial != null) setField(cast, castType, startSerial, Integer.valueOf(0));
        setField(player, playerType, requiredAny(catalog, "request.controller.field", "native.player.cast.field"), cast);

        Field animationField = field(playerType, "dh");
        Object animation = unsafeAllocate(animationField.getType());
        setField(player, playerType, animationField.getName(), animation);
        try {
            Method initialize = animation.getClass().getDeclaredMethod(encoded("lilil"), Integer.TYPE);
            initialize.setAccessible(true);
            invoke(initialize, animation, Integer.valueOf(1));
        } catch (NoSuchMethodException missing) { }
    }

    private static Class<?> loaderClass(Class<?> anchor, String internalName) throws ClassNotFoundException {
        return Class.forName(internalName.replace('/', '.'), true, anchor.getClassLoader());
    }

    private static void verifyNativeMenuAndTargetPath(File candidate, File donor, Properties catalog)
            throws Exception {
        verifyNativeConstructorKinds(candidate, catalog);
        try (NativeProbeLoader loader = new NativeProbeLoader(candidate, catalog)) {
            Class<?> bridgeType = loader.loadClass(requiredAny(catalog, "bridge.owner", "native.menu.owner").replace('/', '.'));
            Object bridge = bridgeType.getDeclaredConstructor().newInstance();
            Class<?> api = loader.loadClass(ACCESS);
            Class<?> settingsType = loader.loadClass(SETTINGS);
            Class<?> clockType = loader.loadClass(CLOCK);
            NativeAccess world = new NativeAccess(loader, bridge, catalog);
            Object nativeAccess = Proxy.newProxyInstance(loader, new Class<?>[]{api}, world);
            int[] saved = new int[]{0, 650};
            Object settings = Proxy.newProxyInstance(loader, new Class<?>[]{settingsType}, new SettingsHandler(saved));
            Object clock = Proxy.newProxyInstance(loader, new Class<?>[]{clockType}, new Clock());
            Class<?> runtime = loader.loadClass(RUNTIME);
            runtime.getMethod("installForTest", api, settingsType, clockType).invoke(null, nativeAccess, settings, clock);

            world.installReadyWorld();
            Method ready = bridgeType.getMethod("ready");
            require(Boolean.TRUE.equals(ready.invoke(bridge)),
                    "Generated native readiness accepts a connected, entered world and rejects no active UI state");
            verifyNativeFarmHpMp(loader, bridge, world, catalog);

            Class<?> listType = loader.loadClass("et");
            Object rootItems = newNativeList(listType, "ROOT");
            Method root = bridgeType.getMethod("addMenu", listType);
            root.invoke(bridge, rootItems);
            require(listSize(rootItems) == 1, "Actual generated root hook adds one Auto Attack entry");
            Object entry = listGet(rootItems, 0);
            require("Auto đánh >".equals(nativeLabel(entry)), "Native root entry has the intended label");
            require(nativeAction(entry) == MENU, "Root entry stores the signed-byte-safe action -110");

            Method show = bridgeType.getMethod("showMenu", Boolean.TYPE, Boolean.TYPE, Integer.TYPE);
            show.invoke(bridge, Boolean.FALSE, Boolean.FALSE, Integer.valueOf(650));
            PopupEvent popup = loader.lastPopup();
            boolean battlefieldProfile;
            try (JarFile archive = new JarFile(candidate)) {
                battlefieldProfile = archive.getEntry("luna/features/BattlefieldSupport.class") != null;
            }
            int expectedRows = 4 + (battlefieldProfile ? 1 : 0)
                    + (catalog.getProperty("special.menu.action") == null ? 0 : 1)
                    + (catalog.getProperty("normal.monsters.action") == null ? 0 : 1);
            require(popup != null && listSize(popup.items) == expectedRows,
                    "Actual native Auto Attack popup renders " + expectedRows + " rows");
            require("Auto tự đánh: [TẮT]".equals(nativeLabel(listGet(popup.items, 0))),
                    "Native submenu labels the master OFF action explicitly");
            require(nativeLabel(listGet(popup.items, 1)).startsWith("Auto chỉ quái"),
                    "Native submenu exposes the monster-only mode");
            require(nativeLabel(listGet(popup.items, 2)).startsWith(
                    "true".equals(catalog.getProperty("auto.pk.upgrade")) ? "Auto PK" : "Auto quái + PK"),
                    "Native submenu exposes the expected PK mode");
            require("Delay (ms): 650".equals(nativeLabel(listGet(popup.items, 3))),
                    "Native submenu shows the current 650ms delay; saw " + nativeLabel(listGet(popup.items, 3)));
            for (int i = 0; i < 4; i++) {
                int expected = new int[]{TOGGLE, MONSTERS, MONSTERS_AND_PLAYERS, DELAY}[i];
                require(nativeAction(listGet(popup.items, i)) == expected,
                        "Native bt row keeps signed action " + expected);
            }

            // Drive the real bt event method. It dispatches through the native two-int callback;
            // the callback's first argument is checked after byte sign-extension.
            Object modeItem = listGet(popup.items, 2);
            invoke(findNativeClick(modeItem.getClass()), modeItem);
            require(loader.actionCalls.contains(Integer.valueOf(MONSTERS_AND_PLAYERS)),
                    "Actual native button callback receives the signed PK action -113");
            require(world.modePlayers && world.enabled && world.stopFarmCalls == 1,
                    "Actual callback selects PK mode, enables the master, and yields Farm once");
            require(!world.farmEnabled(), "Auto Attack handoff clears the native Farm enabled flag");
            require(world.farmConfigUnchanged(), "Auto Attack handoff preserves every other native Farm static setting");

            Object monsterNear = world.entity(10, (byte)1, 2, 0, 0, 20, 30);
            Object monsterFar = world.entity(10, (byte)1, 2, 0, 0, 100, 100);
            Object dead = world.entity(0, (byte)1, 2, 0, 0, 1, 0);
            Object excluded = world.entity(10, (byte)1, 2, 1, 0, 2, 0);
            Object corpse = world.entity(10, (byte)1, 4, 0, 0, 3, 0);
            Object friendly = world.entity(10, (byte)0, 2, 0, 0, 4, 0);
            Object hostile = world.entity(10, (byte)0, 2, 0, 1, 5, 0);
            world.replaceEntities(new Object[]{monsterFar, dead, excluded, corpse, friendly, hostile, monsterNear});
            Method valid = bridgeType.getMethod("validTarget", Object.class, Boolean.TYPE);
            require(Boolean.TRUE.equals(valid.invoke(bridge, monsterNear, Boolean.FALSE)),
                    "Generated target predicate accepts a live native monster");
            require(Boolean.FALSE.equals(valid.invoke(bridge, dead, Boolean.FALSE)),
                    "Generated target predicate rejects native HP-zero entities");
            require(Boolean.FALSE.equals(valid.invoke(bridge, excluded, Boolean.FALSE)),
                    "Generated target predicate rejects native exclusion flags");
            require(Boolean.FALSE.equals(valid.invoke(bridge, corpse, Boolean.FALSE)),
                    "Generated target predicate rejects native removed-state entities");
            require(Boolean.FALSE.equals(valid.invoke(bridge, friendly, Boolean.TRUE)),
                    "Native player filter rejects a friendly player");
            require(Boolean.TRUE.equals(valid.invoke(bridge, hostile, Boolean.TRUE)),
                    "Native player filter accepts the native hostile-player predicate");

            invoke(runtime.getMethod("tick"), null);
            require(runtime.getMethod("target").invoke(null) == hostile,
                    "Native-world selector chooses the nearest valid PK target, skipping invalid entities");
            world.modePlayers = false;
            Method toggleMode = bridgeType.getMethod("onAction", Integer.TYPE, Integer.TYPE);
            require(Boolean.TRUE.equals(toggleMode.invoke(bridge, Integer.valueOf(MONSTERS), Integer.valueOf(0))),
                    "Generated native menu callback handles the signed monster-only action");
            require(!world.modePlayers && world.enabled,
                    "Monster-only option stays a mode selection while keeping the master ON");
            Method ownedTargetGate = bridgeType.getMethod("ownedTargetGate", Object.class);
            require(Boolean.TRUE.equals(invoke(ownedTargetGate, null, monsterNear))
                            && Boolean.FALSE.equals(invoke(ownedTargetGate, null, dead))
                            && Boolean.FALSE.equals(invoke(ownedTargetGate, null, excluded))
                            && Boolean.FALSE.equals(invoke(ownedTargetGate, null, corpse))
                            && Boolean.FALSE.equals(invoke(ownedTargetGate, null, friendly))
                            && Boolean.FALSE.equals(invoke(ownedTargetGate, null, hostile)),
                    "Actual native fanout gate accepts live monsters and rejects dead, excluded, removed, and all player entities in mode 7");
            invoke(runtime.getMethod("tick"), null);
            require(runtime.getMethod("target").invoke(null) == monsterNear,
                    "Native-world selector chooses the nearest live monster in monster-only mode");
            require(Boolean.TRUE.equals(invoke(toggleMode, bridge, Integer.valueOf(MONSTERS_AND_PLAYERS), Integer.valueOf(0)))
                            && Boolean.TRUE.equals(invoke(ownedTargetGate, null, hostile))
                            && Boolean.FALSE.equals(invoke(ownedTargetGate, null, friendly)),
                    "Actual native fanout gate admits only the hostile player after mode 6 is selected");

            Class<?> farmOwner = loader.loadClass(requiredAny(catalog, "native.farmAction.owner", "farm.action.owner").replace('/', '.'));
            Method farmAction = farmOwner.getMethod(requiredAny(catalog, "native.farmAction.method", "farm.action.method"),
                    Integer.TYPE, Integer.TYPE);
            Object farmScreen = unsafeAllocate(farmOwner);
            require(Boolean.TRUE.equals(invoke(farmAction, farmScreen, Integer.valueOf(-40), Integer.valueOf(0))),
                    "Actual Farm toggle callback enables the native Farm owner");
            require(world.farmEnabled() && !bool(runtime, "enabled"),
                    "Native Farm enable callback yields Auto Attack and owns the mode after handoff");
            require(invoke(runtime, "target") == null,
                    "Two-way Farm handoff clears the Auto Attack target before any stale request can resume");
            require(saved[0] == 1 && saved[1] == 650,
                    "Farm handoff preserves Auto Attack mode/delay RMS state without persisting ON");
            require(world.farmConfigUnchanged(),
                    "Farm re-enable changes only its native enabled flag and leaves all other config fields intact");

            world.setReadiness("ui.activeDialog", allocate(loader, catalog.getProperty("ui.activeDialog.descriptor")));
            require(Boolean.FALSE.equals(ready.invoke(bridge)), "Native readiness pauses while a modal dialog is active");
            world.clearReadiness();
            world.setBoolean("native.world.entered", false);
            require(Boolean.FALSE.equals(ready.invoke(bridge)), "Native readiness pauses before world entry");
            world.setBoolean("native.world.entered", true);
            world.setBoolean("native.connection.wait", true);
            require(Boolean.FALSE.equals(ready.invoke(bridge)), "Native readiness pauses during connection wait");
            world.setBoolean("native.connection.wait", false);
            world.setBoolean("native.world.entered", false);
            invoke(runtime.getMethod("tick"), null);
            require(runtime.getMethod("target").invoke(null) == null,
                    "Leaving the ready world invalidates the native target");
            world.setBoolean("native.world.entered", true);

        }
    }

    private static void verifyNativeConstructorKinds(File candidate, Properties catalog) throws Exception {
        String entityOwner = requiredAny(catalog, "native.entity.owner", "entity.owner");
        String playerOwner = requiredAny(catalog, "native.player.owner", "player.owner");
        String mobOwner = encoded("llllil");
        ClassNode mob = parse(readJarClass(candidate, mobOwner));
        ClassNode player = parse(readJarClass(candidate, playerOwner));
        require(hasSuperclass(candidate, mob, entityOwner),
                "Native monster class is the raw game entity subtype");
        int mobKinds = 0;
        for (MethodNode constructor : mob.methods) {
            if (!"<init>".equals(constructor.name)) continue;
            List<AbstractInsnNode> code = real(constructor);
            for (int i = 1; i < code.size(); i++) {
                AbstractInsnNode write = code.get(i);
                if (write instanceof FieldInsnNode && write.getOpcode() == Opcodes.PUTFIELD) {
                    FieldInsnNode field = (FieldInsnNode)write;
                    if (entityOwner.equals(field.owner) && "cv".equals(field.name) && "B".equals(field.desc)
                            && integer(code.get(i - 1)) == 1) mobKinds++;
                }
            }
        }
        int playerKinds = 0;
        for (MethodNode constructor : player.methods) {
            if (!"<init>".equals(constructor.name)) continue;
            List<AbstractInsnNode> code = real(constructor);
            for (int i = 1; i < code.size(); i++) {
                AbstractInsnNode write = code.get(i);
                if (write instanceof FieldInsnNode && write.getOpcode() == Opcodes.PUTFIELD) {
                    FieldInsnNode field = (FieldInsnNode)write;
                    if (entityOwner.equals(field.owner) && "cv".equals(field.name) && "B".equals(field.desc)
                            && integer(code.get(i - 1)) == 0) playerKinds++;
                }
            }
        }
        require(mobKinds > 0 && playerKinds > 0,
                "Pinned native constructors establish monster cv=1 and player cv=0 (monster writes="
                        + mobKinds + ", player writes=" + playerKinds + ")");
    }

    private static void verifyNativeFarmHpMp(NativeProbeLoader loader, Object bridge,
            NativeAccess world, Properties catalog) throws Exception {
        Class<?> farmType = loader.loadClass(requiredAny(catalog, "native.farmAction.owner", "farm.action.owner").replace('/', '.'));
        Object farm = unsafeAllocate(farmType);
        Method update = farmType.getMethod("update");
        Field enabled = field(farmType, encoded("lililiillli"));
        Field cooldown = field(farmType, encoded("lililillili"));
        Object enabledBefore = enabled.get(null), cooldownBefore = cooldown.get(null);
        Class<?> thresholdsOwner = loader.loadClass(encoded("illil").replace('/', '.'));
        Field thresholdsField = field(thresholdsOwner, encoded("llllll"));
        Object thresholdsBefore = thresholdsField.get(null);
        int[] thresholds = thresholdsBefore instanceof int[] ? (int[])thresholdsBefore : new int[]{0, 0};
        int[] thresholdCopy = thresholds.clone();
        if (thresholdCopy.length < 2) thresholdCopy = new int[]{0, 0};
        thresholdCopy[0] = 50; thresholdCopy[1] = 50;
        thresholdsField.set(null, thresholdCopy);
        Class<?> inventoryOwner = loader.loadClass(encoded("illilli").replace('/', '.'));
        Field inventoryField = field(inventoryOwner, encoded("liiillili"));
        Object inventoryBefore = inventoryField.get(null);
        inventoryField.set(null, newNativeList(loader.loadClass("et"), "empty native potion inventory"));

        Class<?> entityType = loader.loadClass(requiredAny(catalog, "native.entity.owner", "entity.owner").replace('/', '.'));
        Field hp = field(entityType, "bs"), maxHp = field(entityType, "bt");
        Field mp = field(entityType, "bu"), maxMp = field(entityType, "bv");
        Field state = field(entityType, "cG");
        int hpBefore = hp.getInt(world.player), maxHpBefore = maxHp.getInt(world.player);
        int mpBefore = mp.getInt(world.player), maxMpBefore = maxMp.getInt(world.player);
        int stateBefore = state.getInt(world.player);
        int packetsBefore = attackPacketCount();
        try {
            state.setInt(world.player, 0);
            hp.setInt(world.player, 100); maxHp.setInt(world.player, 100);
            mp.setInt(world.player, 100); maxMp.setInt(world.player, 100);
            cooldown.setInt(null, 0);
            enabled.setBoolean(null, false);
            nativeHpMpCalls = 0;
            update.invoke(farm);
            require(nativeHpMpCalls == 1,
                    "Actual patched Farm.update reaches its unchanged native HP/MP helper while auto-potion is OFF");

            enabled.setBoolean(null, true);
            update.invoke(farm);
            require(nativeHpMpCalls == 2 && attackPacketCount() == packetsBefore,
                    "Native HP/MP thresholds do not send items while both resources remain above 50 percent");

            hp.setInt(world.player, 10); mp.setInt(world.player, 10);
            update.invoke(farm);
            require(nativeHpMpCalls == 3 && attackPacketCount() == packetsBefore,
                    "Native HP and MP threshold branches execute with an empty real inventory without changing Auto Attack packets");
            require(Boolean.TRUE.equals(bridge.getClass().getMethod("ready").invoke(bridge)),
                    "Native HP/MP updates leave Auto Attack world readiness intact");
        } finally {
            state.setInt(world.player, stateBefore);
            hp.setInt(world.player, hpBefore); maxHp.setInt(world.player, maxHpBefore);
            mp.setInt(world.player, mpBefore); maxMp.setInt(world.player, maxMpBefore);
            enabled.set(null, enabledBefore); cooldown.set(null, cooldownBefore);
            inventoryField.set(null, inventoryBefore);
            thresholdsField.set(null, thresholdsBefore);
        }
    }

    private static void verifyNativeCastAndRequestPath(File input, File candidate, File donor,
            Properties catalog) throws Exception {
        verifyNativeRequestShape(input, candidate, catalog);
        verifyNativeRequestRuntime(candidate, catalog);
    }

    private static void verifySharedFarmDelay(File candidate, Properties catalog) throws Exception {
        verifyFarmOriginWrapper(candidate, catalog);
        try (NativeProbeLoader loader = new NativeProbeLoader(candidate, catalog)) {
            Class<?> runtime = loader.loadClass(RUNTIME);
            Class<?> api = loader.loadClass(ACCESS);
            Class<?> settingsType = loader.loadClass(SETTINGS);
            Class<?> clockType = loader.loadClass(CLOCK);
            Class<?> bridgeType = loader.loadClass("luna.generated.HungAutoAttackAccess");
            Object bridge = bridgeType.getDeclaredConstructor().newInstance();
            NativeAccess world = new NativeAccess(loader, bridge, catalog);
            Object access = Proxy.newProxyInstance(loader, new Class<?>[]{api}, world);
            final int[] saved = new int[]{0, 650};
            Object settings = Proxy.newProxyInstance(loader, new Class<?>[]{settingsType}, new SettingsHandler(saved));
            Clock clock = new Clock();
            Object nativeClock = Proxy.newProxyInstance(loader, new Class<?>[]{clockType}, clock);
            runtime.getMethod("installForTest", api, settingsType, clockType)
                    .invoke(null, access, settings, nativeClock);
            world.installReadyWorld();
            require(!bool(runtime, "enabled") && world.farmEnabled(),
                    "Shared-delay fixture starts with native Farm ON and independent Auto Attack OFF");

            Class<?> farmType = loader.loadClass(requiredAny(catalog, "native.farmAction.owner", "farm.action.owner")
                    .replace('/', '.'));
            Method farmAction = farmType.getMethod(requiredAny(catalog, "native.farmAction.method", "farm.action.method"),
                    Integer.TYPE, Integer.TYPE);
            Object farmScreen = unsafeAllocate(farmType);
            invoke(farmAction, farmScreen, Integer.valueOf(-40), Integer.valueOf(0));
            require(!world.farmEnabled(), "Actual native Farm callback turns the shared-delay fixture OFF");
            invoke(farmAction, farmScreen, Integer.valueOf(-40), Integer.valueOf(0));
            require(world.farmEnabled(), "Actual native Farm callback turns the shared-delay fixture back ON");

            Method farmMinimum = bridgeType.getMethod(requiredAny(catalog, "native.farmAutoMinimum.method",
                    "farmAutoMinimum.method"));
            Method farmGate = loader.loadClass(requiredAny(catalog, "hook.farmAutoGate.owner", "native.cast.owner")
                    .replace('/', '.')).getMethod(requiredAny(catalog, "hook.farmAutoGate.method", "farm.autoGate.method"));
            Method requestMinimum = bridgeType.getMethod(requiredAny(catalog, "native.requestMinimum.method",
                    "requestMinimum.method"), loader.loadClass(requiredAny(catalog, "native.cast.owner", "cast.owner")
                    .replace('/', '.')));
            Method localChoose = loader.loadClass(requiredAny(catalog, "native.farmChoose.owner", "farm.choose.owner")
                    .replace('/', '.')).getMethod(requiredAny(catalog, "native.farmChoose.method", "farm.choose.method"),
                    Integer.TYPE, Boolean.TYPE);
            Class<?> playerType = loader.loadClass(requiredAny(catalog, "native.player.owner", "player.owner").replace('/', '.'));
            Class<?> entityType = loader.loadClass(requiredAny(catalog, "native.entity.owner", "entity.owner").replace('/', '.'));
            Class<?> castType = loader.loadClass(requiredAny(catalog, "native.cast.owner", "cast.owner").replace('/', '.'));
            Class<?> listType = loader.loadClass("et");
            Method enterFarm = bridgeType.getMethod("enterFarmStart");
            Method exitFarm = bridgeType.getMethod("exitFarmStart");
            String originName = requiredAny(catalog, "request.farmOrigin.field", "native.cast.farmOrigin.field");
            String tagName = requiredAny(catalog, "request.tag.field", "native.cast.tag.field");
            String actorName = requiredAny(catalog, "cast.actor.field", "native.cast.actor.field");
            Method castInit = castType.getDeclaredMethod(requiredAny(catalog, "cast.init.method", "hook.castInit.method"),
                    entityType, listType, Integer.TYPE, Byte.TYPE);
            castInit.setAccessible(true);

            Object monster = world.entity(100, (byte)1, 2, 0, 0, 20, 30);
            setField(monster, entityType, "cu", Integer.valueOf(41));
            world.replaceEntities(new Object[]{monster});
            Object skillDefinition = seedNativeSkill(loader, playerType, catalog);
            seedNativeFontMetrics(loader);
            setField(world.player, playerType, "cA", Integer.valueOf(-1));
            setField(world.player, playerType, "cG", Integer.valueOf(0));
            setField(world.player, playerType, "ef", Byte.valueOf((byte)1));
            setField(world.player, playerType, "cw", Byte.valueOf((byte)0));
            setField(world.player, playerType, "bu", Integer.valueOf(100));
            setStaticField(playerType, "e", Integer.valueOf(1));
            Class<?> skillCellType = field(playerType, encoded("lliliiil")).getType()
                    .getComponentType().getComponentType();
            setStaticField(skillCellType, "f", Byte.valueOf((byte)1));
            NativeRequestSpec support = prepareNativeRequest(loader, candidate, catalog, world.player,
                    monster, 0, (byte)1);
            String targetsName = requiredAny(catalog, "cast.targets.field", "native.cast.targets.field");
            String targetName = requiredAny(catalog, "cast.target.field", "native.cast.target.field");
            Object targetRecords = field(castType, targetsName).get(support.cast);

            int[] delays = new int[]{1, 650, 1000};
            long base = 7000000L;
            for (int delay : delays) {
                require(boolStringArg(runtime, "submitDelay", String.valueOf(delay)),
                        "Shared Farm/Auto Attack delay accepts " + delay + "ms");
                require(((Integer)farmMinimum.invoke(bridge)).intValue() == delay,
                        "Actual native Farm gate minimum follows the shared " + delay + "ms setting");
                setNativeRequestClock(loader, catalog, base, (short)0);
                setNativeFarmGateClock(loader, catalog, base);
                nativeRequestNow = base + delay;
                require(!Boolean.TRUE.equals(invoke(farmGate, null)),
                        "Actual native Farm auto gate stays closed at the shared " + delay + "ms floor");
                nativeRequestNow = base + delay + 1L;
                require(Boolean.TRUE.equals(invoke(farmGate, null)),
                        "Actual native Farm auto gate opens above the shared " + delay + "ms floor");
                setField(world.player, playerType, "cA", Integer.valueOf(-1));
                setField(world.player, playerType, "cG", Integer.valueOf(0));
                Object hotbar = field(playerType, encoded("lliliiil")).get(null);
                Object row = java.lang.reflect.Array.get(hotbar, 0);
                Object cell = java.lang.reflect.Array.get(row, 1);
                setField(cell, cell.getClass(), "b", Byte.valueOf((byte)0));
                Throwable chooseFailure = null;
                try { invoke(localChoose, world.player, Integer.valueOf(0), Boolean.FALSE); }
                catch (Throwable incompleteFarmWorld) {
                    // The chooser is part of the real Farm update loop and expects live native
                    // cooldown/UI state. The following cast-init fallback still executes the
                    // exact thread-scoped origin capture used by that wrapper.
                    chooseFailure = incompleteFarmWorld;
                }
                Object farmCast = field(playerType, requiredAny(catalog, "request.controller.field",
                        "native.player.cast.field")).get(world.player);
                boolean wrapperOrigin = farmCast != null && field(castType, originName).getBoolean(farmCast)
                        && field(castType, tagName).getInt(farmCast) == 0;
                if (!wrapperOrigin) {
                    // The direct native wrapper is the same call made by Farm's update loop. If its
                    // game-state prerequisites prevent a cast in this isolated world, execute its
                    // exact origin scope around the unchanged native constructor below; the shape
                    // verifier separately pins the three Farm call sites to this wrapper.
                    invoke(enterFarm, null);
                    try {
                        farmCast = unsafeAllocate(castType);
                        invoke(castInit, farmCast, world.player, targetRecords, Integer.valueOf(0), Byte.valueOf((byte)0));
                    }
                    finally { invoke(exitFarm, null); }
                }
                if (delay == 1) require(chooseFailure == null,
                        "Actual native Farm choose wrapper remains callable with an eligible skill; failure="
                                + chooseFailure + " skill=" + skillDefinition + " hotbar=" + nativeSkillCellId(playerType));
                require(farmCast != null && field(castType, originName).getBoolean(farmCast)
                                && field(castType, tagName).getInt(farmCast) == 0,
                        "Actual Farm-origin local cast captures tag zero and its origin marker");
                require(world.farmEnabled(), "Auto Attack delay edits never turn native Farm off");
                copyInstanceState(support.cast, farmCast);
                setField(farmCast, castType, originName, Boolean.TRUE);
                setField(farmCast, castType, tagName, Integer.valueOf(0));
                setField(farmCast, castType, actorName, world.player);
                setField(farmCast, castType, targetName, monster);
                setField(farmCast, castType, targetsName, targetRecords);
                setField(farmCast, castType, "g", Byte.valueOf((byte)-1));
                setField(farmCast, castType, "k", Integer.valueOf(0));
                setField(farmCast, castType, "n", Integer.valueOf(0));
                setField(farmCast, castType, "codexWaitRealTime", Boolean.FALSE);
                setField(world.player, playerType, "cQ", Integer.valueOf(1));
                require(((Integer)requestMinimum.invoke(bridge, farmCast)).intValue() == delay,
                        "Actual tag-zero local Farm cast resolves the shared " + delay + "ms request floor");

                setNativeRequestClock(loader, catalog, base, (short)0);
                nativeRequestNow = base + delay - 1L;
                synchronized (nativePackets) { nativePackets.clear(); }
                Method request = castType.getMethod(requiredAny(catalog, "hook.request.method", "request.method"));
                invoke(request, farmCast);
                require(attackPacketCount() == 0 && field(castType, "codexWaitRealTime").getBoolean(farmCast),
                        "Real Farm-origin native request waits below the shared " + delay + "ms floor; packets="
                                + nativePacketSnapshot() + " wait="
                                + field(castType, "codexWaitRealTime").getBoolean(farmCast) + " now="
                                + nativeRequestNow + " origin=" + field(castType, originName).getBoolean(farmCast)
                                + " resolved=" + ((Integer)requestMinimum.invoke(bridge, farmCast)).intValue());
                nativeRequestNow = base + delay;
                invoke(request, farmCast);
                require(attackPacketCount() == 0,
                        "Real Farm-origin native request remains pending at exactly " + delay + "ms");
                nativeRequestNow = base + delay + 1L;
                invoke(request, farmCast);
                require(attackPacketCount() == 1 && packetHasOpcode(9)
                                && packetHasPayload(9, "[0, 1, 0, 41]"),
                        "Real native Farm cast sends its monster packet only above " + delay + "ms; captured="
                                + nativePacketSnapshot());
                base += 100000L;
            }

            require(boolStringArg(runtime, "submitDelay", "1000"), "Shared delay returns to 1000ms for mode isolation checks");
            invokeAction(runtime, MONSTERS);
            require(saved[0] == 0 && saved[1] == 1000 && !world.farmEnabled(),
                    "Selecting monsters-only mode persists the shared delay and performs the normal Farm handoff");
            invokeAction(runtime, TOGGLE);
            invoke(farmAction, farmScreen, Integer.valueOf(-40), Integer.valueOf(0));
            require(world.farmEnabled() && ((Integer)farmMinimum.invoke(bridge)).intValue() == 1000,
                    "Auto Farm reads the retained 1000ms delay after selecting monsters-only mode");
            invokeAction(runtime, MONSTERS_AND_PLAYERS);
            require(saved[0] == 1 && saved[1] == 1000 && !world.farmEnabled(),
                    "Selecting Quái + PK mode persists the same delay and performs the normal Farm handoff");
            invokeAction(runtime, TOGGLE);
            invoke(farmAction, farmScreen, Integer.valueOf(-40), Integer.valueOf(0));
            require(world.farmEnabled() && ((Integer)farmMinimum.invoke(bridge)).intValue() == 1000,
                    "Auto Farm reads the retained 1000ms delay after selecting Quái + PK mode");

            initializeNativeActorHooks(world.player, playerType, catalog);
            Object localManual = unsafeAllocate(castType);
            invoke(castInit, localManual, world.player, targetRecords, Integer.valueOf(0), Byte.valueOf((byte)0));
            require(!field(castType, originName).getBoolean(localManual)
                            && ((Integer)requestMinimum.invoke(bridge, localManual)).intValue() == 650,
                    "Manual local tag-zero cast retains its original 650ms floor while Farm is ON");
            Object remote = unsafeAllocate(playerType);
            initializeNativeActorHooks(remote, playerType, catalog);
            Object remoteFarmCast = unsafeAllocate(castType);
            invoke(enterFarm, null);
            try { invoke(castInit, remoteFarmCast, remote, targetRecords, Integer.valueOf(0), Byte.valueOf((byte)0)); }
            finally { invoke(exitFarm, null); }
            require(!field(castType, originName).getBoolean(remoteFarmCast)
                            && ((Integer)requestMinimum.invoke(bridge, remoteFarmCast)).intValue() == 650,
                    "Remote tag-zero cast cannot inherit the local Farm delay origin");
            require(!bool(runtime, "enabled") && world.farmEnabled() && saved[1] == 1000,
                    "Shared Farm delay leaves Auto Attack OFF, Farm ON, and its configured delay saved");
        }
    }

    private static void verifyFarmOriginWrapper(File candidate, Properties catalog) throws Exception {
        String owner = requiredAny(catalog, "native.farmChoose.owner", "farm.choose.owner");
        ClassNode type = parse(readJarClass(candidate, owner));
        MethodNode wrapper = findMethod(type, requiredAny(catalog, "native.farmChoose.method", "farm.choose.method"),
                requiredAny(catalog, "native.farmChoose.descriptor", "farm.choose.descriptor"));
        String bridge = requiredAny(catalog, "bridge.owner", "native.farmAutoMinimum.owner");
        String nativeDescriptor = requiredAny(catalog, "native.farmChoose.descriptor", "farm.choose.descriptor");
        int enter = 0, exit = 0, original = 0;
        for (AbstractInsnNode instruction : wrapper.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (bridge.equals(call.owner) && "enterFarmStart".equals(call.name) && "()V".equals(call.desc)) enter++;
            if (bridge.equals(call.owner) && "exitFarmStart".equals(call.name) && "()V".equals(call.desc)) exit++;
            if (owner.equals(call.owner) && nativeDescriptor.equals(call.desc)
                    && call.getOpcode() == Opcodes.INVOKESPECIAL) original++;
        }
        require(enter == 1 && exit == 2 && original == 1,
                "Native Farm choose wraps the unchanged native chooser in a thread-scoped origin with finally cleanup");
    }

    private static void setNativeFarmGateClock(NativeProbeLoader loader, Properties catalog, long timestamp)
            throws Exception {
        String ownerName = requiredAny(catalog, "hook.farmAutoGate.owner", "native.cast.owner");
        String methodName = requiredAny(catalog, "hook.farmAutoGate.method", "farm.autoGate.method");
        String descriptor = requiredAny(catalog, "hook.farmAutoGate.descriptor", "farm.autoGate.descriptor");
        ClassNode owner = parse(readJarClass(loader.jarFile, ownerName));
        MethodNode gate = findMethod(owner, methodName, descriptor);
        FieldInsnNode timestampField = null;
        for (AbstractInsnNode instruction : gate.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == Opcodes.GETSTATIC
                    && "J".equals(((FieldInsnNode)instruction).desc)) {
                timestampField = (FieldInsnNode)instruction; break;
            }
        }
        require(timestampField != null, "Native Farm auto gate uses its original static long timestamp");
        Class<?> timestampOwner = loader.loadClass(timestampField.owner.replace('/', '.'));
        setStaticField(timestampOwner, timestampField.name, Long.valueOf(timestamp));
    }

    private static void verifyManualMovementPriority(File candidate, Properties catalog) throws Exception {
        verifyMovementHookPlacement(candidate, catalog);
        try (NativeProbeLoader loader = new NativeProbeLoader(candidate, catalog)) {
            Class<?> runtime = loader.loadClass(RUNTIME);
            Class<?> api = loader.loadClass(ACCESS);
            Class<?> settingsType = loader.loadClass(SETTINGS);
            Class<?> clockType = loader.loadClass(CLOCK);
            Class<?> bridgeType = loader.loadClass("luna.generated.HungAutoAttackAccess");
            Object bridge = bridgeType.getDeclaredConstructor().newInstance();
            NativeAccess world = new NativeAccess(loader, bridge, catalog);
            Object access = Proxy.newProxyInstance(loader, new Class<?>[]{api}, world);
            Object settings = Proxy.newProxyInstance(loader, new Class<?>[]{settingsType}, new SettingsHandler(new int[]{0, 650}));
            Clock clock = new Clock();
            Object nativeClock = Proxy.newProxyInstance(loader, new Class<?>[]{clockType}, clock);
            runtime.getMethod("installForTest", api, settingsType, clockType)
                    .invoke(null, access, settings, nativeClock);
            world.installReadyWorld();

            Class<?> playerType = loader.loadClass(requiredAny(catalog, "native.player.owner", "player.owner")
                    .replace('/', '.'));
            Class<?> entityType = loader.loadClass(requiredAny(catalog, "native.entity.owner", "entity.owner")
                    .replace('/', '.'));
            Class<?> castType = loader.loadClass(requiredAny(catalog, "native.cast.owner", "cast.owner")
                    .replace('/', '.'));
            seedNativeSkill(loader, playerType, catalog);
            seedNativeFontMetrics(loader);
            Object monster = world.entity(100, (byte)1, 2, 0, 0, 20, 30);
            world.replaceEntities(new Object[]{monster});
            setField(world.player, playerType, "cA", Integer.valueOf(-1));
            setField(world.player, playerType, "cG", Integer.valueOf(0));
            setField(world.player, playerType, "ef", Byte.valueOf((byte)1));
            setField(world.player, playerType, "cw", Byte.valueOf((byte)0));
            setField(world.player, playerType, "bu", Integer.valueOf(100));
            setStaticField(playerType, "e", Integer.valueOf(1));

            world.runNativeSkill = true;
            invokeAction(runtime, MONSTERS);
            invoke(runtime, "tick");
            clock.now = 651L;
            invoke(runtime, "tick");
            Object cast = field(playerType, requiredAny(catalog, "request.controller.field", "native.player.cast.field"))
                    .get(world.player);
            require(Boolean.TRUE.equals(world.nativeSkillResult) && cast != null
                            && field(playerType, "cG").getInt(world.player) == 2,
                    "Movement-priority test begins with a real busy native Auto Attack cast");
            int oldTag = field(castType, requiredAny(catalog, "request.tag.field", "native.cast.tag.field"))
                    .getInt(cast);
            int oldStarted = field(castType, requiredAny(catalog, "native.cast.startSerial.field", "native.cast.started.field"))
                    .getInt(cast);

            NativeRequestSpec requestSupport = prepareNativeRequest(loader, candidate, catalog,
                    world.player, monster, oldTag, (byte)1);
            setField(cast, castType, requiredAny(catalog, "cast.target.field", "native.cast.target.field"), monster);
            setField(cast, castType, requiredAny(catalog, "cast.targets.field", "native.cast.targets.field"),
                    field(castType, requiredAny(catalog, "cast.targets.field", "native.cast.targets.field"))
                            .get(requestSupport.cast));
            setField(cast, castType, "g", Byte.valueOf((byte)-1));
            setField(cast, castType, "k", Integer.valueOf(0));
            setField(cast, castType, "n", Integer.valueOf(0));
            setField(cast, castType, "codexWaitRealTime", Boolean.FALSE);
            Method nativeRequest = castType.getMethod(requiredAny(catalog, "hook.request.method", "request.method"));

            setNativeMovementKey(loader, catalog, 0, true);
            Method held = loader.loadClass(requiredAny(catalog, "native.movement.held.owner", "movement.held.owner")
                    .replace('/', '.')).getMethod(requiredAny(catalog, "native.movement.held.method", "movement.held.method"),
                    Integer.TYPE);
            Method movementRequested = bridgeType.getMethod(requiredAny(catalog, "native.movement.detect.method",
                    "movement.detect.method"));
            require(Boolean.TRUE.equals(invoke(held, null, Integer.valueOf(0)))
                            && Boolean.TRUE.equals(invoke(movementRequested, bridge)),
                    "Native world detector observes a held directional key from the real engine key-state array");
            invoke(runtime, "movementTick");
            assertMovementCancelled(runtime, world, cast, castType, oldTag, oldStarted, monster, catalog,
                    "Directional-key movement");
            synchronized (nativePackets) { nativePackets.clear(); }
            nativeRequest.invoke(cast);
            require(attackPacketCount() == 0,
                    "Movement-cancelled native cast cannot emit a stale opcode-6 or opcode-9 request");

            setNativeMovementKey(loader, catalog, 0, false);
            int attemptsBeforeResume = world.attackCalls;
            clock.now = 1302L;
            invoke(runtime, "tick");
            clock.now = 1401L;
            invoke(runtime, "tick");
            require(world.attackCalls == attemptsBeforeResume,
                    "Directional movement restart waits below the fixed 100ms boundary");
            clock.now = 1402L;
            invoke(runtime, "tick");
            require(world.attackCalls == attemptsBeforeResume + 1 && Boolean.TRUE.equals(world.nativeSkillResult)
                            && field(playerType, "cG").getInt(world.player) == 2,
                    "Auto Attack remains enabled and resumes at 100ms once directional movement ends; calls="
                            + world.attackCalls + " before=" + attemptsBeforeResume + " result=" + world.nativeSkillResult
                            + " cG=" + field(playerType, "cG").getInt(world.player) + " enabled=" + bool(runtime, "enabled")
                            + " held=" + invoke(held, null, Integer.valueOf(0)) + " movement="
                            + invoke(movementRequested, bridge) + " now=" + clock.now + " target="
                            + (invoke(runtime, "target") != null) + " failure=" + world.nativeSkillFailure);
            Object touchCast = field(playerType,
                    requiredAny(catalog, "request.controller.field", "native.player.cast.field")).get(world.player);
            require(field(castType,catalog.getProperty("native.cast.movementResume.field")).getBoolean(touchCast)
                && ((Integer)invoke(bridgeType.getMethod("requestMinimum",castType),bridge,touchCast)).intValue()==100,
                "First resumed native cast latches a100ms floor independently of configured650ms");
            NativeRequestSpec resumedSupport=prepareNativeRequest(loader,candidate,catalog,world.player,monster,
                integer(runtime,"generation"),(byte)0);
            copyInstanceState(resumedSupport.cast,touchCast);
            setField(touchCast,castType,catalog.getProperty("native.cast.movementResume.field"),Boolean.TRUE);
            setField(touchCast,castType,"g",Byte.valueOf((byte)-1));
            setField(touchCast,castType,"k",Integer.valueOf(0));
            setField(touchCast,castType,"n",Integer.valueOf(0));
            setField(world.player,playerType,"cQ",Integer.valueOf(1));
            setNativeRequestClock(loader,catalog,4000000L,(short)1500);
            synchronized(nativePackets){nativePackets.clear();}
            nativeRequestNow=4000101L;invoke(nativeRequest,touchCast);
            require(attackPacketCount()==0,"Resumed100ms floor never bypasses the native1500ms skill interval");
            nativeRequestNow=4001500L;invoke(nativeRequest,touchCast);
            require(attackPacketCount()==0,"Resumed native request remains strict at actual1500ms skill boundary");
            nativeRequestNow=4001501L;invoke(nativeRequest,touchCast);
            require(attackPacketCount()==1,"Resumed native request sends once above the actual native skill interval");
            copyInstanceState(resumedSupport.cast,touchCast);
            setField(touchCast,castType,catalog.getProperty("native.cast.movementResume.field"),Boolean.TRUE);
            setField(touchCast,castType,"g",Byte.valueOf((byte)-1));setField(touchCast,castType,"k",Integer.valueOf(0));
            setField(touchCast,castType,"n",Integer.valueOf(0));setField(world.player,playerType,"cQ",Integer.valueOf(1));
            setNativeRequestClock(loader,catalog,4000000L,(short)0);
            synchronized(nativePackets){nativePackets.clear();}
            nativeRequestNow=4000100L;invoke(nativeRequest,touchCast);
            require(attackPacketCount()==0,"Resumed real native request remains strict at100ms floor");
            nativeRequestNow=4000101L;invoke(nativeRequest,touchCast);
            require(attackPacketCount()==1 && packetHasPayload(9,"[0, 1, 0, 41]"),
                "Resumed real native request sends at101ms instead of waiting configured650ms");
            int touchTag = field(castType, requiredAny(catalog, "request.tag.field", "native.cast.tag.field"))
                    .getInt(touchCast);
            int touchStarted = field(castType, requiredAny(catalog,
                    "native.cast.startSerial.field", "native.cast.started.field")).getInt(touchCast);
            setField(world.player, playerType, requiredAny(catalog, "native.movement.path.field", "movement.path.field"),
                    new short[]{20, 30});
            Method manualGroundMove = bridgeType.getMethod(requiredAny(catalog, "native.movement.accept.method",
                    "movement.accept.method"));
            invoke(manualGroundMove, bridge);
            require(Boolean.TRUE.equals(invoke(movementRequested, bridge)),
                    "Accepted blank-ground path is recognized as active native manual movement");
            assertMovementCancelled(runtime, world, touchCast, castType, touchTag, touchStarted, monster, catalog,
                    "Accepted ground-touch movement");
            synchronized (nativePackets) { nativePackets.clear(); }
            nativeRequest.invoke(touchCast);
            require(attackPacketCount() == 0,
                    "Accepted ground-touch movement prevents a stale Auto Attack packet");
            setField(world.player, playerType, requiredAny(catalog, "native.movement.path.field", "movement.path.field"), null);
            int attemptsBeforeTouchResume = world.attackCalls;
            clock.now = 1403L;
            invoke(runtime, "tick");
            clock.now = 1502L;
            invoke(runtime, "tick");
            require(world.attackCalls == attemptsBeforeTouchResume,
                    "Ground-touch movement restart waits below the fixed 100ms boundary");
            clock.now = 1503L;
            invoke(runtime, "tick");
            require(world.attackCalls == attemptsBeforeTouchResume + 1 && bool(runtime, "enabled")
                            && Boolean.TRUE.equals(world.nativeSkillResult)
                            && field(playerType, "cG").getInt(world.player) == 2,
                    "Touch movement ends cleanly, preserves Auto Attack intent, and resumes after its delay");
            world.runNativeSkill = false;
        }
    }

    private static void verifyInventoryGameplay(File candidate, Properties catalog) throws Exception {
        try (NativeProbeLoader loader = new NativeProbeLoader(candidate, catalog)) {
            Class<?> runtime = loader.loadClass(RUNTIME), api = loader.loadClass(ACCESS);
            Class<?> settingsType = loader.loadClass(SETTINGS), clockType = loader.loadClass(CLOCK);
            Class<?> bridgeType = loader.loadClass("luna.generated.HungAutoAttackAccess");
            Object bridge = bridgeType.getDeclaredConstructor().newInstance();
            NativeAccess world = new NativeAccess(loader, bridge, catalog);
            Clock clock = new Clock();
            runtime.getMethod("installForTest", api, settingsType, clockType).invoke(null,
                Proxy.newProxyInstance(loader, new Class<?>[]{api}, world),
                Proxy.newProxyInstance(loader, new Class<?>[]{settingsType}, new SettingsHandler(new int[]{0,650})),
                Proxy.newProxyInstance(loader, new Class<?>[]{clockType}, clock));
            world.installReadyWorld();
            Class<?> main = loader.loadClass(catalog.getProperty("ui.currentScreen.owner").replace('/','.'));
            Object game = field(main, catalog.getProperty("native.listener.field")).get(null);
            Class<?> tabsType = loader.loadClass(catalog.getProperty("native.inventory.enter.owner").replace('/','.'));
            Object tabs = tabsType.getDeclaredConstructor().newInstance();
            setStaticField(main, catalog.getProperty("native.inventory.singleton.field"), tabs);
            Class<?> tutorialType = loader.loadClass(encoded("liiilili").replace('/','.'));
            Object tutorial = unsafeAllocate(tutorialType);
            setStaticField(loader.loadClass(encoded("liiliii").replace('/','.')), encoded("llillllli"), tutorial);
            seedNativeFontMetrics(loader);
            Class<?> bagType=loader.loadClass(catalog.getProperty("native.inventory.panel.owner").replace('/','.'));
            Object bag=unsafeAllocate(bagType);setField(bag,bagType,catalog.getProperty("native.inventory.panel.type.field"),Byte.valueOf((byte)0));
            Object panels=field(tabsType,catalog.getProperty("native.inventory.panels.field")).get(tabs);
            addNativeListItem(catalog,panels,bag);
            Method enter = tabsType.getMethod(catalog.getProperty("native.inventory.enter.method"), game.getClass().getSuperclass());
            invoke(enter, tabs, game);
            require(field(main, catalog.getProperty("ui.currentScreen.field")).get(null)==tabs
                && field(tabsType, catalog.getProperty("native.inventory.parent.field")).get(tabs)==game,
                "Real inventory entry installs the native singleton and preserves its world parent");
            require(Boolean.TRUE.equals(invoke(bridgeType.getMethod("ready"),bridge)),
                "Connected native inventory with world parent remains gameplay-ready");
            setField(bag,bagType,catalog.getProperty("native.inventory.panel.type.field"),Byte.valueOf((byte)16));
            require(Boolean.FALSE.equals(invoke(bridgeType.getMethod("ready"),bridge)),"Native Sell panel on the reused inventory singleton stays guarded");
            setField(bag,bagType,catalog.getProperty("native.inventory.panel.type.field"),Byte.valueOf((byte)15));
            require(Boolean.FALSE.equals(invoke(bridgeType.getMethod("ready"),bridge)),"NPC info panel on the reused inventory singleton stays guarded");
            setField(bag,bagType,catalog.getProperty("native.inventory.panel.type.field"),Byte.valueOf((byte)0));
            setField(tabs,tabsType,catalog.getProperty("native.inventory.selected.field"),Integer.valueOf(1));
            require(Boolean.FALSE.equals(invoke(bridgeType.getMethod("ready"),bridge)),"Out-of-range native inventory tab stays guarded");
            setField(tabs,tabsType,catalog.getProperty("native.inventory.selected.field"),Integer.valueOf(0));
            Class<?> playerType=world.player.getClass();
            seedNativeSkill(loader,playerType,catalog);
            setField(world.player,playerType,"cA",Integer.valueOf(-1));
            setField(world.player,playerType,"cG",Integer.valueOf(0));
            setField(world.player,playerType,"ef",Byte.valueOf((byte)1));
            setField(world.player,playerType,"cw",Byte.valueOf((byte)0));
            setField(world.player,playerType,"bu",Integer.valueOf(100));
            setStaticField(playerType,"e",Integer.valueOf(1));
            Object monster=world.entity(100,(byte)1,2,0,0,20,30);world.replaceEntities(new Object[]{monster});
            world.runNativeSkill=true;
            invokeAction(runtime,MONSTERS);
            Method callback=bridgeType.getMethod("update");invoke(callback,bridge);
            setNativeMovementKey(loader,catalog,0,true);
            require(Boolean.FALSE.equals(invoke(bridgeType.getMethod("movementRequested"),bridge)),
                "Inventory directional navigation never counts as manual walking");
            clock.now=651L;invoke(callback,bridge);
            require(world.attackCalls==1 && Boolean.TRUE.equals(world.nativeSkillResult)
                && field(playerType,"cG").getInt(world.player)==2,
                "Actual module update selects and starts one native cast while Hanh Trang remains open");
            Object cast=field(playerType,catalog.getProperty("request.controller.field")).get(world.player);
            int generation=integer(runtime,"generation");
            NativeRequestSpec support=prepareNativeRequest(loader,candidate,catalog,world.player,monster,generation,(byte)0);
            copyInstanceState(support.cast,cast);
            setField(cast,cast.getClass(),"g",Byte.valueOf((byte)-1));
            setField(cast,cast.getClass(),"k",Integer.valueOf(0));
            setField(cast,cast.getClass(),"n",Integer.valueOf(0));
            setField(world.player,playerType,"cQ",Integer.valueOf(1));
            setNativeRequestClock(loader,catalog,3000000L,(short)0);nativeRequestNow=3000651L;
            synchronized(nativePackets){nativePackets.clear();}
            Method request=cast.getClass().getMethod(catalog.getProperty("hook.request.method"));invoke(request,cast);
            invoke(callback,bridge);
            require(attackPacketCount()==1 && packetHasPayload(9,"[0, 1, 0, 41]") && world.attackCalls==1,
                "Inventory native cast sends one real monster packet without duplicate module attacks");
            require(field(main,catalog.getProperty("ui.currentScreen.field")).get(null)==tabs,
                "Combat leaves native inventory screen and its menu intact");
            setNativeMovementKey(loader,catalog,0,false);
            world.setReadiness("ui.activeDialog",allocate(loader,catalog.getProperty("ui.activeDialog.descriptor")));
            require(Boolean.FALSE.equals(invoke(bridgeType.getMethod("ready"),bridge)),"Inventory item/NPC modal remains a combat guard");
            world.clearReadiness();
            Object invalid=unsafeAllocate(tabsType);
            setStaticField(main,catalog.getProperty("ui.currentScreen.field"),invalid);
            require(Boolean.FALSE.equals(invoke(bridgeType.getMethod("ready"),bridge)),"Another tab wrapper is not accepted as native inventory");
            invoke(callback,bridge);int packets=attackPacketCount();nativeRequestNow+=2000L;invoke(request,cast);
            require(attackPacketCount()==packets && bool(runtime,"enabled"),"Leaving inventory for invalid screen invalidates old callback without disabling intent");
            setStaticField(main,catalog.getProperty("ui.currentScreen.field"),tabs);
            setField(tabs,tabsType,catalog.getProperty("native.inventory.parent.field"),invalid);
            require(Boolean.FALSE.equals(invoke(bridgeType.getMethod("ready"),bridge)),"Native tabs outside a world parent stay guarded");
            setField(tabs,tabsType,catalog.getProperty("native.inventory.parent.field"),game);
            world.setBoolean("native.connection.wait",true);
            require(Boolean.FALSE.equals(invoke(bridgeType.getMethod("ready"),bridge)),"Inventory never bypasses disconnected/wait guard");
            world.setBoolean("native.connection.wait",false);setField(world.player,playerType,"bs",Integer.valueOf(0));
            require(Boolean.FALSE.equals(invoke(bridgeType.getMethod("ready"),bridge)),"Inventory never bypasses dead-player guard");
        }
    }

    private static void assertMovementCancelled(Class<?> runtime, NativeAccess world, Object cast,
            Class<?> castType, int priorTag, int priorStarted, Object target, Properties catalog,
            String movement) throws Exception {
        Class<?> playerType = world.player.getClass();
        int cancelledTag = field(castType, requiredAny(catalog, "request.tag.field", "native.cast.tag.field"))
                .getInt(cast);
        require(bool(runtime, "enabled") && cancelledTag == -1
                        && field(playerType, "cG").getInt(world.player) == 0
                        && field(castType, requiredAny(catalog, "native.cast.startSerial.field", "native.cast.started.field"))
                                .getInt(cast) == priorStarted
                        && !boolArg(runtime, "allowRequest", priorTag, target),
                movement + " preempts the busy cast, invalidates its epoch, and keeps Auto Attack enabled");
    }

    private static void verifyMovementHookPlacement(File candidate, Properties catalog) throws Exception {
        Map<String, byte[]> entries = readClasses(candidate);
        String playerOwner = requiredAny(catalog, "native.player.owner", "player.owner");
        for (String prefix : new String[]{"hook.movement.keyboard", "hook.movement.localUpdate"}) {
            String owner = requiredAny(catalog, prefix + ".owner", prefix + ".owner");
            String name = requiredAny(catalog, prefix + ".method", prefix + ".method");
            String desc = requiredAny(catalog, prefix + ".descriptor", prefix + ".descriptor");
            ClassNode type = parse(entries.get(owner + ".class"));
            MethodNode method = findMethod(type, name, desc);
            int ticks = 0;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if ("luna/features/AutoAttackRuntime".equals(call.owner)
                            && "movementTick".equals(call.name) && "()V".equals(call.desc)) ticks++;
                }
            }
            require(owner.equals(playerOwner) && ticks == 1,
                    "Native local " + prefix.substring(prefix.lastIndexOf('.') + 1)
                            + " hook checks manual input before the native cast update");
        }
        String groundOwner = requiredAny(catalog, "hook.movement.ground.owner", "movement.ground.owner");
        MethodNode ground = findMethod(parse(entries.get(groundOwner + ".class")),
                requiredAny(catalog, "hook.movement.ground.method", "movement.ground.method"),
                requiredAny(catalog, "hook.movement.ground.descriptor", "movement.ground.descriptor"));
        List<Integer> acceptedIndices = new ArrayList<Integer>();
        List<AbstractInsnNode> code = real(ground);
        for (int i = 0; i < code.size(); i++) {
            AbstractInsnNode instruction = code.get(i);
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)instruction;
            if ("luna/generated/HungAutoAttackAccess".equals(call.owner)
                    && "manualGroundMove".equals(call.name) && "()V".equals(call.desc)) {
                acceptedIndices.add(Integer.valueOf(i));
            }
        }
        int groundIndex = Integer.parseInt(requiredAny(catalog, "native.movement.ground.original.index",
                "movement.ground.original.index"));
        int portalIndex = Integer.parseInt(requiredAny(catalog, "native.movement.portal.original.index",
                "movement.portal.original.index"));
        require(acceptedIndices.size() == 2 && acceptedIndices.contains(Integer.valueOf(portalIndex))
                        && acceptedIndices.contains(Integer.valueOf(groundIndex + 1)),
                "Accepted blank-ground and portal movement paths call the native movement bridge at their catalogued points");
    }

    private static void setNativeMovementKey(NativeProbeLoader loader, Properties catalog,
            int logicalKey, boolean down) throws Exception {
        String owner = requiredAny(catalog, "native.movement.held.owner", "movement.held.owner");
        String name = requiredAny(catalog, "native.movement.held.method", "movement.held.method");
        String descriptor = requiredAny(catalog, "native.movement.held.descriptor", "movement.held.descriptor");
        ClassNode type = parse(readJarClass(loader.jarFile, owner));
        MethodNode method = findMethod(type, name, descriptor);
        FieldInsnNode input = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == Opcodes.GETSTATIC
                    && "[Z".equals(((FieldInsnNode)instruction).desc)) {
                input = (FieldInsnNode)instruction; break;
            }
        }
        require(input != null, "Native held-key reader identifies its real boolean input array");
        Class<?> inputOwner = loader.loadClass(input.owner.replace('/', '.'));
        Field keys = field(inputOwner, input.name);
        boolean[] values = (boolean[])keys.get(null);
        if (values == null || values.length < 37) {
            values = new boolean[37];
            setStaticField(inputOwner, input.name, values);
        }
        int[] primary = new int[]{8, 2, 4, 6};
        require(logicalKey >= 0 && logicalKey < primary.length, "Movement test uses a native directional key id");
        values[primary[logicalKey]] = down;
        values[primary[logicalKey] + 20] = false;
    }

    private static void seedNativeFontMetrics(NativeProbeLoader loader) throws Exception {
        Class<?> nativeFonts = Class.forName(encoded("ililillil").replace('/', '.'), true, loader);
        Object fontFallback = unsafeAllocate(nativeFonts);
        for (Field field : allFields(nativeFonts)) {
            if (Modifier.isStatic(field.getModifiers()) && nativeFonts.equals(field.getType())
                    && field.get(null) != null) { fontFallback = field.get(null); break; }
        }
        for (Field field : allFields(nativeFonts)) {
            if (Modifier.isStatic(field.getModifiers()) && nativeFonts.equals(field.getType())
                    && field.get(null) == null) setStaticField(nativeFonts, field.getName(), fontFallback);
        }
        char[] glyphs = new char[256];
        for (int i = 0; i < glyphs.length; i++) glyphs[i] = (char)i;
        setField(fontFallback, nativeFonts, encoded("liliiilli"), new String(glyphs));
        int[][] widths = new int[glyphs.length][3];
        for (int i = 0; i < widths.length; i++) widths[i][2] = 1;
        setField(fontFallback, nativeFonts, encoded("liilililiii"), widths);
    }

    private static void copyInstanceState(Object source, Object target) throws Exception {
        for (Field member : allFields(source.getClass())) {
            if (Modifier.isStatic(member.getModifiers())) continue;
            member.setAccessible(true);
            member.set(target, member.get(source));
        }
    }

    private static void verifyNativeRequestShape(File input, File candidate, Properties catalog) throws Exception {
        String owner = requiredAny(catalog, "hook.request.owner", "request.owner");
        String name = requiredAny(catalog, "hook.request.method", "request.method");
        String desc = requiredAny(catalog, "hook.request.descriptor", "request.descriptor");
        String ownedOwner = requiredAny(catalog, "native.ownedRequest.owner", "hook.ownedRequest.owner");
        String ownedName = requiredAny(catalog, "native.ownedRequest.method", "hook.ownedRequest.method");
        String ownedDesc = requiredAny(catalog, "native.ownedRequest.descriptor", "hook.ownedRequest.descriptor");
        ClassNode sourceOwner = parse(readJarClass(input, owner));
        ClassNode candidateOwner = parse(readJarClass(candidate, owner));
        MethodNode source = findMethod(sourceOwner, name, desc);
        MethodNode wrapper = findMethod(candidateOwner, name, desc);
        MethodNode request = findMethod(candidateOwner, ownedName, ownedDesc);
        String castOwner = requiredAny(catalog, "native.cast.owner", "request.owner");
        String tag = requiredAny(catalog, "request.tag.field", "native.cast.tag.field");
        String minimumOwner = requiredAny(catalog, "native.requestMinimum.owner", "bridge.owner");
        String minimumMethod = requiredAny(catalog, "native.requestMinimum.method", "requestMinimum.method");
        String minimumDescriptor = requiredAny(catalog, "native.requestMinimum.descriptor",
                "requestMinimum.descriptor");
        String cancelled = requiredAny(catalog, "request.tag.cancelled", "native.cast.tag.cancelled");
        require("-1".equals(cancelled), "Cancelled native cast tag uses the negative invalid sentinel");
        int minimumCalls = 0, allowCalls = 0, cancelCalls = 0;
        List<String> sendMethods = new ArrayList<String>();
        boolean dynamicMinimumBoundToCast = false;
        List<AbstractInsnNode> code = real(request);
        for (int i = 0; i < code.size(); i++) {
            AbstractInsnNode instruction = code.get(i);
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (minimumOwner.equals(call.owner) && minimumMethod.equals(call.name)
                    && minimumDescriptor.equals(call.desc)) {
                minimumCalls++;
                dynamicMinimumBoundToCast = i >= 1 && isVar(code.get(i - 1), Opcodes.ALOAD, 0)
                        && call.getOpcode() == Opcodes.INVOKESTATIC
                        && ("(L" + castOwner + ";)I").equals(call.desc);
            }
            if ("luna/features/AutoAttackRuntime".equals(call.owner) && "allowRequest".equals(call.name)
                    && "(ILjava/lang/Object;)Z".equals(call.desc)) allowCalls++;
            if (call.owner.contains("HungAutoAttackAccess") && "cancelCast".equals(call.name)) cancelCalls++;
            if ("(Let;B)V".equals(call.desc) && call.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                sendMethods.add(call.name);
            }
        }
        require(minimumCalls == 1 && dynamicMinimumBoundToCast,
                "Actual native request uses the cast-aware shared Farm/Auto Attack minimum resolver");
        require(allowCalls == 1 && cancelCalls == 1,
                "Actual native request rejects stale generations through native cast cleanup before send");
        require(sendMethods.size() == 2 && !sendMethods.get(0).equals(sendMethods.get(1)),
                "Actual native request retains two distinct opcode-6/opcode-9 serializers");
        boolean dispatchShim = false, needsLock = false;
        for (AbstractInsnNode instruction : wrapper.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)instruction;
            if ("luna/generated/HungAutoAttackAccess".equals(call.owner)) {
                if ("dispatchOwnedRequest".equals(call.name)
                        && ("(L" + castOwner + ";)V").equals(call.desc)) dispatchShim = true;
                if ("needsRequestLock".equals(call.name)
                        && ("(L" + castOwner + ";)Z").equals(call.desc)) needsLock = true;
            }
        }
        require(dispatchShim && needsLock,
                "Native request wrapper asks the generated bridge whether tagged or owned legacy Farm work needs the atomic shim");
        require(owner.equals(ownedOwner), "Atomic owned request remains on the native cast class");
        require(!methodCode(source).equals(methodCode(wrapper))
                        && !methodCode(source).equals(methodCode(request)),
                "Candidate request wrapper and owned serializer are isolated from the input method");
    }

    private static void verifyNativeOpcode9(File candidate, Properties catalog) throws Exception {
        try (NativeProbeLoader loader = new NativeProbeLoader(candidate, catalog)) {
            Class<?> runtime = loader.loadClass(RUNTIME);
            Class<?> api = loader.loadClass(ACCESS);
            Class<?> settingsType = loader.loadClass(SETTINGS);
            Class<?> clockType = loader.loadClass(CLOCK);
            Class<?> bridgeType = loader.loadClass("luna.generated.HungAutoAttackAccess");
            Object bridge = bridgeType.getDeclaredConstructor().newInstance();
            NativeAccess world = new NativeAccess(loader, bridge, catalog);
            Object access = Proxy.newProxyInstance(loader, new Class<?>[]{api}, world);
            int[] saved = new int[]{0, 650};
            Object settings = Proxy.newProxyInstance(loader, new Class<?>[]{settingsType}, new SettingsHandler(saved));
            Clock clock = new Clock();
            Object nativeClock = Proxy.newProxyInstance(loader, new Class<?>[]{clockType}, clock);
            runtime.getMethod("installForTest", api, settingsType, clockType)
                    .invoke(null, access, settings, nativeClock);
            world.installReadyWorld();
            verifyRealNativeSkillStart(loader, bridge, runtime, world, catalog, clock);
            Object monster = world.entity(100, (byte)1, 2, 0, 0, 20, 30);
            world.replaceEntities(new Object[]{monster});
            setField(world.player, world.player.getClass(), "cA", Integer.valueOf(-1));
            invokeAction(runtime, MONSTERS);
            invoke(runtime, "tick");
            clock.now = 651L;
            invoke(runtime, "tick");
            int generation = integer(runtime, "generation");
            require(world.lastAttackTarget == monster && world.lastAttackTag == generation
                            && boolArg(runtime, "allowRequest", generation, monster),
                    "Native monster selection produces a current generation accepted by the real request gate");

            NativeRequestSpec request = prepareNativeRequest(loader, candidate, catalog, world.player,
                    monster, generation, (byte)0);
            Method runRequest = request.castType.getMethod(requiredAny(catalog, "hook.request.method", "request.method"));
            long base = 3000000L;
            setNativeRequestClock(loader, catalog, base, (short)0);
            nativeRequestNow = base + 651L;
            synchronized (nativePackets) { nativePackets.clear(); }
            invoke(runRequest, request.cast);
            require(packetHasOpcode(9) && packetHasPayload(9, "[0, 1, 0, 41]"),
                    "Actual native monster serializer emits opcode 9 with its category and target id; captured="
                            + nativePacketSnapshot());
        }
    }

    private static void verifyRealNativeSkillStart(NativeProbeLoader loader, Object bridge,
            Class<?> runtime, NativeAccess world, Properties catalog, Clock clock) throws Exception {
        // Native skill selection asks the game's font metrics for the chosen skill
        // label. Supply a complete in-memory glyph-width table so the real chooser
        // runs without requiring a graphics device or changing its branch logic.
        Class<?> nativeFonts = Class.forName(encoded("ililillil").replace('/', '.'), true, loader);
        Object fontFallback = unsafeAllocate(nativeFonts);
        for (Field field : allFields(nativeFonts)) {
            if (Modifier.isStatic(field.getModifiers()) && nativeFonts.equals(field.getType())
                    && field.get(null) != null) { fontFallback = field.get(null); break; }
        }
        for (Field field : allFields(nativeFonts)) {
            if (Modifier.isStatic(field.getModifiers()) && nativeFonts.equals(field.getType())
                    && field.get(null) == null) setStaticField(nativeFonts, field.getName(), fontFallback);
        }
        char[] glyphs = new char[256];
        for (int i = 0; i < glyphs.length; i++) glyphs[i] = (char)i;
        setField(fontFallback, nativeFonts, encoded("liliiilli"), new String(glyphs));
        int[][] widths = new int[glyphs.length][3];
        for (int i = 0; i < widths.length; i++) widths[i][2] = 1;
        setField(fontFallback, nativeFonts, encoded("liilililiii"), widths);
        String playerOwner = requiredAny(catalog, "native.player.owner", "player.owner");
        Class<?> playerType = loader.loadClass(playerOwner.replace('/', '.'));
        Object skillDefinition = seedNativeSkill(loader, playerType, catalog);
        Object monster = world.entity(100, (byte)1, 2, 0, 0, 20, 30);
        world.replaceEntities(new Object[]{monster});
        setField(world.player, playerType, "cA", Integer.valueOf(-1));
        setField(world.player, playerType, "cG", Integer.valueOf(0));
        setField(world.player, playerType, "ef", Byte.valueOf((byte)1));
        setField(world.player, playerType, "cw", Byte.valueOf((byte)0));
        setField(world.player, playerType, "bu", Integer.valueOf(100));
        // The native hostile-target predicate rejects every monster when the
        // local player's level is still the zero value from Unsafe allocation.
        // A real logged-in character always has a positive level.
        setStaticField(playerType, "e", Integer.valueOf(1));
        Class<?> castType = loader.loadClass(requiredAny(catalog, "native.cast.owner", "cast.owner").replace('/', '.'));
        Object priorCast = unsafeAllocate(castType);
        setField(priorCast, castType, requiredAny(catalog, "native.cast.startSerial.field", "native.cast.started.field"),
                Integer.valueOf(0));
        setField(world.player, playerType, requiredAny(catalog, "request.controller.field", "native.player.cast.field"),
                priorCast);
        Object animation = unsafeAllocate(field(playerType, "dh").getType());
        setField(world.player, playerType, "dh", animation);
        for (Method method : animation.getClass().getDeclaredMethods()) {
            if (method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == Integer.TYPE
                    && method.getReturnType() == Void.TYPE) {
                method.setAccessible(true);
                method.invoke(animation, Integer.valueOf(1));
                break;
            }
        }
        world.runNativeSkill = true;
        Class<?> skillCellType = field(playerType, encoded("lliliiil")).getType().getComponentType().getComponentType();
        setStaticField(skillCellType, "f", Byte.valueOf((byte)1));
        Method nativeEligibility = playerType.getDeclaredMethod(encoded("illl"), Integer.TYPE, Integer.TYPE);
        nativeEligibility.setAccessible(true);
        Object eligibility = invoke(nativeEligibility, world.player, Integer.valueOf(0), Integer.valueOf(-1));
        Object ownedSlotEligibility = invoke(nativeEligibility, world.player, Integer.valueOf(0), Integer.valueOf(1));
        Method hostile = playerType.getDeclaredMethod(requiredAny(catalog,
                "native.player.hostile.method", "native.hostile.method"),
                loader.loadClass(requiredAny(catalog, "native.entity.owner", "entity.owner").replace('/', '.')));
        hostile.setAccessible(true);
        Object hostileResult = invoke(hostile, world.player, monster);
        require(Boolean.TRUE.equals(hostileResult), "Native player-level/PK predicate accepts a live monster target");
        require(Boolean.TRUE.equals(ownedSlotEligibility),
                "Native private skill gate accepts skill 0 in the actual selected hotbar slot 1; result="
                        + ownedSlotEligibility + " fallback=" + eligibility + " cell=" + nativeSkillCellId(playerType));
        invokeAction(runtime, MONSTERS);
        require(boolStringArg(runtime, "submitDelay", "1"), "Native skill path uses the valid 1ms delay floor");
        invoke(runtime, "tick");
        clock.now = 2L;
        invoke(runtime, "tick");
        int generation = integer(runtime, "generation");
        require(world.lastAttackTarget == monster && world.lastAttackTag == generation,
                "Runtime target selection enters the generated bridge with the current attack generation; calls="
                        + world.attackCalls + " target=" + (world.lastAttackTarget == monster) + " tag="
                        + world.lastAttackTag + " generation=" + generation + " enabled=" + bool(runtime, "enabled")
                        + " failure=" + world.nativeSkillFailure);
        Object nativeCast = field(world.player.getClass(), requiredAny(catalog, "request.controller.field", "native.player.cast.field"))
                .get(world.player);
        require(nativeCast != null, "Actual native slot scan and cast constructor create a native cast instance; result="
                + world.nativeSkillResult + " failure=" + world.nativeSkillFailure + " slot="
                + field(playerType, "codexAutoAttackSlot").getByte(null) + " cA="
                + field(playerType, "cA").getInt(world.player) + " cG="
                + field(playerType, "cG").getInt(world.player));
        int tag = field(nativeCast.getClass(), requiredAny(catalog, "request.tag.field", "native.cast.tag.field"))
                .getInt(nativeCast);
        require(tag == generation,
                "Native cast constructor captures the active Auto Attack generation from the real bridge.attack path; tag="
                        + tag + " generation=" + generation + " started="
                        + field(nativeCast.getClass(), requiredAny(catalog, "native.cast.startSerial.field", "native.cast.started.field"))
                                .getInt(nativeCast) + " result=" + world.nativeSkillResult + " calls="
                        + world.attackCalls + " nativeMode=" + world.runNativeSkill + " fail=" + world.nativeSkillFailure + " cG="
                        + field(playerType, "cG").getInt(world.player) + " cA="
                        + field(playerType, "cA").getInt(world.player) + " eligibility=" + eligibility
                        + " slot=" + field(playerType, "codexAutoAttackSlot").getByte(null)
                        + " cellId=" + nativeSkillCellId(playerType) + " cellb=" + nativeSkillCellBusy(playerType));
        int started = field(nativeCast.getClass(), requiredAny(catalog, "native.cast.startSerial.field", "native.cast.started.field"))
                .getInt(nativeCast);
        require(started > 0, "Generated bridge.attack starts a native cast through the real eligible hotbar slot");
        require(Boolean.TRUE.equals(world.nativeSkillResult), "Generated bridge.attack reports the real native cast start as successful");
        require(field(playerType, "cG").getInt(world.player) == 2,
                "Native successful cast enters the game's busy state before another attack tick");
        require(integer(runtime, "generation") == tag,
                "Native skill helper leaves the active cast epoch unchanged");
        verifyNativeFanout(loader, bridge, runtime, world, playerType, skillDefinition, monster, catalog);
        invoke(runtime, "tick");
        require(field(nativeCast.getClass(), requiredAny(catalog, "native.cast.startSerial.field", "native.cast.started.field"))
                        .getInt(nativeCast) == started && integer(runtime, "generation") == tag,
                "Native busy cast blocks repeated Auto Attack startup without changing its epoch");
        verifyNativeSkillRejections(loader, runtime, world, clock, playerType, skillDefinition, nativeCast,
                monster, catalog);
        world.runNativeSkill = false;
    }

    private static void verifyNativeFanout(NativeProbeLoader loader, Object bridge, Class<?> runtime,
            NativeAccess world, Class<?> playerType, Object definition, Object primary, Properties catalog)
            throws Exception {
        Class<?> entityType = loader.loadClass(requiredAny(catalog, "native.entity.owner", "entity.owner").replace('/', '.'));
        Object validSecondary = world.entity(90, (byte)1, 2, 0, 0, 20, 30);
        Object dead = world.entity(0, (byte)1, 2, 0, 0, 23, 30);
        Object excluded = world.entity(90, (byte)1, 2, 1, 0, 24, 30);
        Object removed = world.entity(90, (byte)1, 4, 0, 0, 25, 30);
        Object ghost = world.entity(90, (byte)1, 2, 0, 0, 26, 30);
        Object friendlyPlayer = world.entity(90, (byte)0, 2, 0, 0, 27, 30);
        Object hostilePlayer = world.entity(90, (byte)0, 2, 0, 1, 28, 30);
        Object[] candidates = new Object[]{primary, validSecondary, dead, excluded, removed, ghost,
                friendlyPlayer, hostilePlayer};
        int[] ids = new int[]{100, 201, 202, 203, 204, 205, 206, 207};
        for (int i = 0; i < candidates.length; i++) setField(candidates[i], entityType, "cu", Integer.valueOf(ids[i]));
        String phaseName = requiredAny(catalog, "native.entity.phase.field", "entity.phase.field");
        Field phase = field(entityType, phaseName);
        if (phase.getType() == Byte.TYPE) phase.setByte(ghost, (byte)2);
        else if (phase.getType() == Integer.TYPE) phase.setInt(ghost, 2);
        else if (phase.getType() == Boolean.TYPE) phase.setBoolean(ghost, true);
        world.replaceEntities(candidates);

        Field costArrayField = field(definition.getClass(), encoded("lllillllil"));
        Object costArray = costArrayField.get(definition);
        Object cost = costArray.getClass().isArray() ? java.lang.reflect.Array.get(costArray, 0) : costArray;
        require(field(definition.getClass(), "e").getByte(definition) == 0
                        && field(definition.getClass(), "g").getByte(definition) == 0,
                "Native fanout uses the real eligible offensive skill branch e=0/g=0");
        Field targetCount = field(cost.getClass(), "j");
        byte oldTargetCount = targetCount.getByte(cost);
        targetCount.setByte(cost, (byte)4);
        Field range = field(cost.getClass(), encoded("lllliiliil"));
        short oldRange = range.getShort(cost);
        range.setShort(cost, (short)2000);
        Method fanout = playerType.getDeclaredMethod(encoded(""), Integer.TYPE, entityType);
        fanout.setAccessible(true);
        require(Boolean.TRUE.equals(bridge.getClass().getMethod("ready").invoke(bridge))
                        && bool(runtime, "enabled") && invoke(runtime, "target") == primary,
                "Native fanout fixture is inside the actual current Auto Attack request epoch");
        Object records = invoke(fanout, world.player, Integer.valueOf(0), primary);
        List<Integer> returnedIds = nativeTargetIds(records, loader);
        Object cast = field(playerType, "di").get(world.player);
        Object castTarget = field(cast.getClass(), requiredAny(catalog, "cast.target.field", "native.cast.target.field"))
                .get(cast);
        Method ownedContext = bridge.getClass().getMethod("ownedContext", Object.class);
        Method ownedGate = bridge.getClass().getMethod("ownedTargetGate", Object.class);
        Method nativeAllow = loader.loadClass("luna.generated.HungMonsterSelectionCommands")
                .getMethod("allow", Object.class);
        Object mappedDefinition = invoke(loader.loadClass(encoded("liillil")).getDeclaredMethod(encoded("liili"), Integer.TYPE),
                null, Integer.valueOf(0));
        require(castTarget == primary && mappedDefinition == definition
                        && Boolean.TRUE.equals(invoke(ownedContext, null, primary)),
                "Native fanout uses the active native cast target and the exact skill definition in the owned request context");
        require(Boolean.TRUE.equals(invoke(ownedGate, null, validSecondary))
                        && Boolean.TRUE.equals(invoke(nativeAllow, null, validSecondary)),
                "Live secondary passes the generated target gate and the existing native selection filter before fanout");
        require(returnedIds.contains(Integer.valueOf(100)) && returnedIds.contains(Integer.valueOf(201))
                        && !returnedIds.contains(Integer.valueOf(202)) && !returnedIds.contains(Integer.valueOf(203))
                        && !returnedIds.contains(Integer.valueOf(204)) && !returnedIds.contains(Integer.valueOf(205))
                        && !returnedIds.contains(Integer.valueOf(206)) && !returnedIds.contains(Integer.valueOf(207)),
                "Real native player fanout builds same-kind target records and filters HP-zero, excluded, removed, ghost, friendly, and hostile-PK mismatches; returned="
                        + returnedIds);
        targetCount.setByte(cost, oldTargetCount);
        range.setShort(cost, oldRange);
    }

    private static List<Integer> nativeTargetIds(Object records, ClassLoader loader) throws Exception {
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < listSize(records); i++) {
            Object record = listGet(records, i);
            Class<?> type = record.getClass();
            Field id;
            try { id = field(type, "cu"); }
            catch (NoSuchFieldException absent) {
                Field shortField = null;
                for (Field candidate : allFields(type)) if (candidate.getType() == Short.TYPE) { shortField = candidate; break; }
                if (shortField != null) id = shortField;
                else try { id = field(type, "a"); }
                catch (NoSuchFieldException alsoAbsent) {
                    StringBuilder names = new StringBuilder();
                    for (Field candidate : allFields(type)) names.append(candidate.getName()).append(':')
                            .append(candidate.getType().getName()).append(' ');
                    throw new AssertionError("Native fanout record has no known id field: " + type.getName() + " " + names);
                }
            }
            int value = id.getType() == Short.TYPE ? id.getShort(record)
                    : id.getType() == Byte.TYPE ? id.getByte(record) : id.getInt(record);
            result.add(Integer.valueOf(value));
        }
        return result;
    }

    private static void verifyNativeSkillRejections(NativeProbeLoader loader, Class<?> runtime,
            NativeAccess world, Clock clock, Class<?> playerType, Object definition, Object nativeCast,
            Object monster, Properties catalog) throws Exception {
        invokeAction(runtime, TOGGLE);
        require(!bool(runtime, "enabled") && field(playerType, "cG").getInt(world.player) == 0,
                "Turning Auto Attack OFF cancels the active native cast and releases its combat state");
        int serialBefore = field(nativeCast.getClass(), requiredAny(catalog,
                "native.cast.startSerial.field", "native.cast.started.field")).getInt(nativeCast);
        Method nativeEligibility = playerType.getDeclaredMethod(encoded("illl"), Integer.TYPE, Integer.TYPE);
        nativeEligibility.setAccessible(true);
        Field costsField = field(definition.getClass(), encoded("lllillllil"));
        Object costs = costsField.get(definition);
        Object cost = costs.getClass().isArray() ? java.lang.reflect.Array.get(costs, 0) : costs;
        Field amount = field(cost.getClass(), encoded("lilllli"));
        Field mana = field(playerType, "bu");
        Object oldCost = amount.get(cost);
        int oldMana = mana.getInt(world.player);
        try {
            amount.setShort(cost, (short)1);
            mana.setInt(world.player, 0);
            require(Boolean.FALSE.equals(invoke(nativeEligibility, world.player, Integer.valueOf(0), Integer.valueOf(1))),
                    "Native private skill gate rejects an offensive skill when MP is below its configured cost");
            invokeAction(runtime, TOGGLE);
            int calls = world.attackCalls;
            clock.now += 2L;
            invoke(runtime, "tick");
            require(world.attackCalls == calls + 1 && Boolean.FALSE.equals(world.nativeSkillResult)
                            && field(nativeCast.getClass(), requiredAny(catalog,
                                    "native.cast.startSerial.field", "native.cast.started.field")).getInt(nativeCast) == serialBefore
                            && field(playerType, "cG").getInt(world.player) == 0,
                    "Real bridge.attack refuses native cast startup when the native MP-cost gate fails");

            amount.setShort(cost, (short)0);
            mana.setInt(world.player, Math.max(oldMana, 100));
            Field learned = field(playerType, encoded("llilllii"));
            int[] learnedSkills = (int[])learned.get(null);
            int[] oldLearned = learnedSkills.clone();
            learnedSkills[0] = 0;
            try {
                require(Boolean.FALSE.equals(invoke(nativeEligibility, world.player, Integer.valueOf(0), Integer.valueOf(1))),
                        "Native private skill gate rejects a hotbar skill that the character has not learned");
            } finally {
                System.arraycopy(oldLearned, 0, learnedSkills, 0, learnedSkills.length);
            }

            Field cooldownsField = field(playerType, encoded("lliillli"));
            Object cooldowns = cooldownsField.get(null);
            Object cooldown = java.lang.reflect.Array.get(cooldowns, 0);
            Field cooldownValue = field(cooldown.getClass(), "b");
            Object oldCooldown = cooldownValue.get(cooldown);
            try {
                if (cooldownValue.getType() == Long.TYPE) cooldownValue.setLong(cooldown, System.currentTimeMillis() + 60000L);
                else if (cooldownValue.getType() == Integer.TYPE) cooldownValue.setInt(cooldown, Integer.MAX_VALUE);
                else if (cooldownValue.getType() == Short.TYPE) cooldownValue.setShort(cooldown, Short.MAX_VALUE);
                else if (cooldownValue.getType() == Byte.TYPE) cooldownValue.setByte(cooldown, Byte.MAX_VALUE);
                else throw new AssertionError("Unexpected native cooldown clock type: " + cooldownValue.getType());
                require(Boolean.FALSE.equals(invoke(nativeEligibility, world.player, Integer.valueOf(0), Integer.valueOf(1))),
                        "Native private skill gate rejects an offensive skill during its live cooldown");
                int cooldownCalls = world.attackCalls;
                clock.now += 2L;
                invoke(runtime, "tick");
                require(world.attackCalls == cooldownCalls + 1 && Boolean.FALSE.equals(world.nativeSkillResult)
                                && field(nativeCast.getClass(), requiredAny(catalog,
                                        "native.cast.startSerial.field", "native.cast.started.field")).getInt(nativeCast) == serialBefore
                                && field(playerType, "cG").getInt(world.player) == 0,
                        "Real bridge.attack refuses another native cast until cooldown expires");
            } finally { cooldownValue.set(cooldown, oldCooldown); }
        } finally {
            amount.set(cost, oldCost);
            mana.setInt(world.player, oldMana);
            if (bool(runtime, "enabled")) invokeAction(runtime, TOGGLE);
        }
        setField(definition, definition.getClass(), "e", Byte.valueOf((byte)0));
        Object hotbar = field(playerType, encoded("lliliiil")).get(null);
        Object row = java.lang.reflect.Array.get(hotbar, 0);
        int selectedSlot = field(playerType, "codexAutoAttackSlot").getByte(null);
        Object selectedCell = java.lang.reflect.Array.get(row, selectedSlot);
        field(selectedCell.getClass(), "b").setByte(selectedCell, (byte)0);
        setField(world.player, playerType, "cA", Integer.valueOf(-1));
        setField(world.player, playerType, "cG", Integer.valueOf(0));
        invokeAction(runtime, TOGGLE);
        int attemptsBeforeSecondStart = world.attackCalls;
        clock.now += 2L;
        invoke(runtime, "tick");
        int secondStarted = field(nativeCast.getClass(), requiredAny(catalog,
                "native.cast.startSerial.field", "native.cast.started.field")).getInt(nativeCast);
        require(world.attackCalls == attemptsBeforeSecondStart + 1 && Boolean.TRUE.equals(world.nativeSkillResult)
                        && secondStarted == serialBefore + 1 && field(playerType, "cG").getInt(world.player) == 2,
                "A second valid native cast advances the same cast instance's start serial and enters busy state; previous="
                        + serialBefore + " current=" + secondStarted + " result=" + world.nativeSkillResult
                        + " calls=" + world.attackCalls);
        int secondGeneration = integer(runtime, "generation");
        require(field(nativeCast.getClass(), requiredAny(catalog, "request.tag.field", "native.cast.tag.field"))
                        .getInt(nativeCast) == secondGeneration,
                "Second successful native cast carries its new Auto Attack generation");
        invokeAction(runtime, TOGGLE);
        require(!bool(runtime, "enabled") && field(playerType, "cG").getInt(world.player) == 0,
                "Turning Auto Attack OFF cancels the second native cast as well");
    }

    private static Object seedNativeSkill(NativeProbeLoader loader, Class<?> playerType,
            Properties catalog) throws Exception {
        Field hotbarField = field(playerType, encoded("lliliiil"));
        Class<?> skillCellType = hotbarField.getType().getComponentType().getComponentType();
        Object hotbar = hotbarField.get(null);
        if (hotbar == null || java.lang.reflect.Array.getLength(hotbar) < 1)
            hotbar = java.lang.reflect.Array.newInstance(hotbarField.getType().getComponentType(), 3);
        Object[] row = (Object[])java.lang.reflect.Array.newInstance(skillCellType, 2);
        Object occupied = unsafeAllocate(skillCellType);
        setField(occupied, skillCellType, "b", Byte.valueOf((byte)1));
        Object skillCell = unsafeAllocate(skillCellType);
        setField(skillCell, skillCellType, "b", Byte.valueOf((byte)0));
        setField(skillCell, skillCellType, encoded("lilllli"), Short.valueOf((short)0));
        row[0] = occupied; row[1] = skillCell;
        java.lang.reflect.Array.set(hotbar, 0, row);
        setStaticField(playerType, hotbarField.getName(), hotbar);
        setStaticField(playerType, "d", Integer.valueOf(0));
        setStaticField(playerType, encoded("llilllii"), new int[]{1, 0, 0, 0});
        setStaticField(playerType, encoded("llilllil"), new int[]{0, 0, 0, 0});
        invoke(playerType.getMethod("codexAutoAttackResetSlot"), null);

        Field cooldowns = field(playerType, encoded("lliillli"));
        Object values = cooldowns.get(null);
        if (values == null || !values.getClass().isArray() || java.lang.reflect.Array.getLength(values) == 0)
            values = java.lang.reflect.Array.newInstance(cooldowns.getType().getComponentType(), 2);
        Object firstCooldown = java.lang.reflect.Array.get(values, 0);
        if (firstCooldown == null) firstCooldown = unsafeAllocate(cooldowns.getType().getComponentType());
        try { setField(firstCooldown, firstCooldown.getClass(), "b", Integer.valueOf(0)); }
        catch (NoSuchFieldException ignored) { }
        java.lang.reflect.Array.set(values, 0, firstCooldown);
        setStaticField(playerType, cooldowns.getName(), values);

        Class<?> definitionType = loader.loadClass(encoded("liiiiill").replace('/', '.'));
        Object definition = unsafeAllocate(definitionType);
        setField(definition, definitionType, "b", Integer.valueOf(0));
        // The native offensive skill branch requires e==0 and supported g==0.
        // e==1 routes into an unrelated buff branch whose g values do not cast.
        setField(definition, definitionType, "e", Byte.valueOf((byte)0));
        setField(definition, definitionType, "g", Byte.valueOf((byte)0));
        Field costs = field(definitionType, encoded("lllillllil"));
        Class<?> costType = costs.getType().isArray() ? costs.getType().getComponentType() : costs.getType();
        Object cost = unsafeAllocate(costType);
        setField(cost, costType, encoded("lilllli"), Short.valueOf((short)0));
        if (costs.getType().isArray()) {
            Object costArray = java.lang.reflect.Array.newInstance(costType, 1);
            java.lang.reflect.Array.set(costArray, 0, cost);
            setField(definition, definitionType, costs.getName(), costArray);
        } else setField(definition, definitionType, costs.getName(), cost);
        Class<?> registryOwner = loader.loadClass(encoded("lillilll").replace('/', '.'));
        Field registry = field(registryOwner, "a");
        Object definitions = newNativeList(loader.loadClass("et"), "Auto Attack native skill registry");
        addNativeListItem(catalog, definitions, definition);
        setStaticField(registryOwner, registry.getName(), definitions);
        return definition;
    }

    private static int nativeSkillCellId(Class<?> playerType) throws Exception {
        Object hotbar = field(playerType, encoded("lliliiil")).get(null);
        Object row = java.lang.reflect.Array.get(hotbar, 0);
        Object cell = java.lang.reflect.Array.get(row, 1);
        return field(cell.getClass(), encoded("lilllli")).getShort(cell);
    }

    private static byte nativeSkillCellBusy(Class<?> playerType) throws Exception {
        Object hotbar = field(playerType, encoded("lliliiil")).get(null);
        Object row = java.lang.reflect.Array.get(hotbar, 0);
        Object cell = java.lang.reflect.Array.get(row, 1);
        return field(cell.getClass(), "b").getByte(cell);
    }

    private static boolean hasSuperclass(File candidate, ClassNode type, String expected) throws Exception {
        String parent = type.superName;
        while (parent != null) {
            if (expected.equals(parent)) return true;
            try { parent = parse(readJarClass(candidate, parent)).superName; }
            catch (AssertionError missing) { return false; }
        }
        return false;
    }

    private static void verifyNativeRequestRuntime(File candidate, Properties catalog) throws Exception {
        verifyNativeOpcode9(candidate, catalog);
        try (NativeProbeLoader loader = new NativeProbeLoader(candidate, catalog)) {
            Class<?> runtime = loader.loadClass(RUNTIME);
            Class<?> api = loader.loadClass(ACCESS);
            Class<?> settingsType = loader.loadClass(SETTINGS);
            Class<?> clockType = loader.loadClass(CLOCK);
            Class<?> bridgeType = loader.loadClass("luna.generated.HungAutoAttackAccess");
            Object bridge = bridgeType.getDeclaredConstructor().newInstance();
            NativeAccess world = new NativeAccess(loader, bridge, catalog);
            Object access = Proxy.newProxyInstance(loader, new Class<?>[]{api}, world);
            int[] saved = new int[]{0, 650};
            Object settings = Proxy.newProxyInstance(loader, new Class<?>[]{settingsType}, new SettingsHandler(saved));
            Clock clock = new Clock();
            Object nativeClock = Proxy.newProxyInstance(loader, new Class<?>[]{clockType}, clock);
            runtime.getMethod("installForTest", api, settingsType, clockType)
                    .invoke(null, access, settings, nativeClock);
            world.installReadyWorld();
            Object taggedTarget = world.entity(100, (byte)0, 2, 0, 1, 20, 30);
            world.replaceEntities(new Object[]{taggedTarget});
            setField(world.player, world.player.getClass(), "cA", Integer.valueOf(-1));
            setField(world.player, world.player.getClass(), "cG", Integer.valueOf(0));
            invokeAction(runtime, MONSTERS_AND_PLAYERS);
            invoke(runtime, "tick");
            clock.now = 651L; invoke(runtime, "tick");
            int generation = integer(runtime, "generation");
            require(world.lastAttackTarget == taggedTarget && world.lastAttackTag == generation,
                    "Native world attack entry captures the active candidate generation around the real entity selection; target="
                            + (world.lastAttackTarget == taggedTarget) + " tag=" + world.lastAttackTag + " generation="
                            + generation + " enabled=" + bool(runtime, "enabled") + " players="
                            + bool(runtime, "playersEnabled") + " ready=" + Boolean.TRUE.equals(bridgeType.getMethod("ready").invoke(bridge))
                            + " actorBusy=" + field(world.player.getClass(), "cG").getInt(world.player)
                            + " selected=" + (runtime.getMethod("target").invoke(null) == taggedTarget)
                            + " valid=" + bridgeType.getMethod("validTarget", Object.class, Boolean.TYPE)
                                    .invoke(bridge, taggedTarget, Boolean.TRUE)
                            + " type=" + field(taggedTarget.getClass(), requiredAny(catalog, "native.entity.type.field", "entity.type.field"))
                                    .getByte(taggedTarget)
                            + " dL=" + field(taggedTarget.getClass(), "dL").getBoolean(taggedTarget)
                            + " cv=" + field(taggedTarget.getClass(), "cv").getByte(taggedTarget));
            require(boolArg(runtime, "allowRequest", generation, taggedTarget),
                    "Current generation and valid hostile-player raw target pass the owned request gate");

            long base = 2000000L;
            verifyNativeRequestCadenceMatrix(loader, candidate, catalog, runtime, world.player,
                    taggedTarget, generation, base);
            final NativeRequestSpec request = prepareNativeRequest(loader, candidate, catalog, world.player,
                    taggedTarget, generation, (byte)1);
            Method runRequest = request.castType.getMethod(requiredAny(catalog, "hook.request.method", "request.method"));
            setNativeRequestClock(loader, catalog, base, (short)0);
            nativeRequestNow = base + 650L;
            synchronized (nativePackets) { nativePackets.clear(); }
            Field waitFlag = field(request.castType, "codexWaitRealTime");
            int beforeFloor = attackPacketCount();
            invoke(runRequest, request.cast);
            require(attackPacketCount() == beforeFloor && waitFlag.getBoolean(request.cast),
                    "Actual native cast request stays pending at exactly 650ms and retains its frame");
            int frame = field(world.player.getClass(), "cQ").getInt(world.player);
            require(frame == 1, "Native pending request leaves the current animation frame unchanged");

            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            nativeEnqueueEntered = entered; nativeEnqueueRelease = release;
            nativeRequestNow = base + 651L;
            final Throwable[] requestFailure = new Throwable[1];
            Thread sendThread = new Thread(new Runnable() {
                public void run() {
                    try { invoke(runRequest, request.cast); }
                    catch (Throwable failure) { requestFailure[0] = failure; }
                }
            }, "auto-attack-native-request");
            sendThread.start();
            boolean nativeSendReached = entered.await(5L, TimeUnit.SECONDS);
            require(nativeSendReached,
                    "Actual native opcode-6/9 serializer reaches the enqueue boundary above the 650ms floor; captured="
                            + nativePacketSnapshot());
            require(packetHasOpcode(6) && packetHasPayload(6, "[0, 1, 0, 41]"),
                    "Actual native player/PK serializer emits opcode 6 with its serialized target id; captured="
                            + nativePacketSnapshot());

            Class<?> farmOwner = loader.loadClass(requiredAny(catalog, "native.farmAction.owner", "farm.action.owner").replace('/', '.'));
            Method farmAction = farmOwner.getMethod(requiredAny(catalog, "native.farmAction.method", "farm.action.method"),
                    Integer.TYPE, Integer.TYPE);
            Object farmScreen = unsafeAllocate(farmOwner);
            final boolean[] handoffReturned = new boolean[1];
            final Throwable[] handoffFailure = new Throwable[1];
            Thread farmThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        invoke(farmAction, farmScreen, Integer.valueOf(-40), Integer.valueOf(0));
                        handoffReturned[0] = true;
                    } catch (Throwable failure) { handoffFailure[0] = failure; }
                }
            }, "auto-attack-farm-handoff");
            farmThread.start();
            Thread.sleep(150L);
            require(!handoffReturned[0] && !world.farmEnabled(),
                    "Native Farm handoff waits while the actual tagged send owns the controller request lock");
            int persistedModeBeforeHandoff = saved[0];
            int persistedDelayBeforeHandoff = saved[1];
            release.countDown();
            sendThread.join(5000L); farmThread.join(5000L);
            nativeEnqueueEntered = null; nativeEnqueueRelease = null;
            if (requestFailure[0] != null) throw new AssertionError("Native serializer failed", requestFailure[0]);
            if (handoffFailure[0] != null) throw new AssertionError("Native Farm handoff failed", handoffFailure[0]);
            require(!sendThread.isAlive() && !farmThread.isAlive() && handoffReturned[0],
                    "Enqueue completes before the blocked native Farm handoff returns");
            require(!bool(runtime, "enabled") && world.farmEnabled(),
                    "Farm enable commits after the tagged native send, leaving Auto Attack OFF");
            require(saved[0] == persistedModeBeforeHandoff && saved[1] == persistedDelayBeforeHandoff,
                    "Farm handoff leaves the independent Auto Attack mode/delay RMS unchanged");
            require(world.farmConfigUnchanged(), "Atomic Farm handoff preserves every unrelated native Farm setting");

            int sentAttackPackets = attackPacketCount();
            nativeRequestNow = base + 3000L;
            invoke(runRequest, request.cast);
            synchronized (nativePackets) {
                require(attackPacketCount() == sentAttackPackets,
                        "Stale post-handoff native request emits no opcode 6 or 9 packet");
            }
            require(field(request.castType, requiredAny(catalog, "request.tag.field", "native.cast.tag.field"))
                    .getInt(request.cast) == -1,
                    "Native stale-cast cleanup writes the negative cancellation sentinel");
            invoke(runRequest, request.cast);
            synchronized (nativePackets) {
                require(attackPacketCount() == sentAttackPackets,
                        "A callback after tag cancellation still cannot enqueue opcode 6 or 9");
            }
            require(integerArg(runtime, "requestMinimum", 0) == 650,
                    "Untagged Farm request keeps its original 650ms cadence after Auto Attack mode changes");
            require(boolStringArg(runtime, "submitDelay", "1")
                    && boolStringArg(runtime, "submitDelay", "1000"),
                    "Candidate native request path retains both delay-boundary settings");
            require(integerArg(runtime, "requestMinimum", generation) == 1000,
                    "A current tagged request uses the 1000ms configured delay dynamically");
            require(integerArg(runtime, "requestMinimum", 0) == 650,
                    "Changing Auto Attack delay never changes the tag-zero legacy request floor");
        }
    }

    private static NativeRequestSpec prepareNativeRequest(NativeProbeLoader loader, File candidate,
            Properties catalog, Object actor, Object target, int generation, byte targetType) throws Exception {
        String owner = requiredAny(catalog, "native.ownedRequest.owner", "hook.ownedRequest.owner");
        String name = requiredAny(catalog, "native.ownedRequest.method", "hook.ownedRequest.method");
        String descriptor = requiredAny(catalog, "native.ownedRequest.descriptor", "hook.ownedRequest.descriptor");
        ClassNode requestOwner = parse(readJarClass(candidate, owner));
        MethodNode requestBody = findMethod(requestOwner, name, descriptor);
        MethodInsnNode skillCall = null, sendCall = null;
        FieldInsnNode intervalField = null;
        for (AbstractInsnNode instruction : requestBody.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.getOpcode() == Opcodes.INVOKESTATIC && call.desc.startsWith("(I)L")
                        && !"java/lang/String".equals(call.owner)) skillCall = call;
                if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && "(Let;B)V".equals(call.desc)) sendCall = call;
            }
            if (intervalField == null && instruction instanceof FieldInsnNode
                    && instruction.getOpcode() == Opcodes.GETFIELD
                    && "S".equals(((FieldInsnNode)instruction).desc)) intervalField = (FieldInsnNode)instruction;
        }
        require(skillCall != null && sendCall != null && intervalField != null,
                "Native owned request retains its real skill interval and packet serializer inputs");
        Class<?> skillType = loader.loadClass(Type.getReturnType(skillCall.desc).getInternalName().replace('/', '.'));
        Object skill = unsafeAllocate(skillType);
        setField(skill, skillType, intervalField.name, Short.valueOf((short)0));
        nativeRequestSkill = skill;

        Object targets = newNativeList(loader.loadClass("et"), "Auto Attack native request targets");
        Class<?> targetRecordType = loader.loadClass(encoded("liilli").replace('/', '.'));
        Constructor<?> targetRecordConstructor = targetRecordType.getDeclaredConstructor(Short.TYPE, Byte.TYPE);
        targetRecordConstructor.setAccessible(true);
        Field targetId = field(target.getClass(), "cu");
        Object targetRecord = targetRecordConstructor.newInstance(Short.valueOf((short)targetId.getInt(target)),
                Byte.valueOf(targetType));
        addNativeListItem(catalog, targets, targetRecord);
        nativeRequestTargets = targets;

        String serializerOwner = sendCall.owner;
        Class<?> serializerType = loader.loadClass(serializerOwner.replace('/', '.'));
        Class<?> bufferType = loader.loadClass(encoded("illlllil").replace('/', '.'));
        String bufferField = null;
        for (Field field : allFields(serializerType))
            if (bufferType.equals(field.getType())) { bufferField = field.getName(); break; }
        require(bufferField != null, "Native Q serializer exposes its real MIDP message buffer");
        Object q = unsafeAllocate(serializerType);
        Object buffer = bufferType.getDeclaredConstructor().newInstance();
        setField(q, serializerType, bufferField, buffer);
        nativeRequestQ = q;

        seedNativeRequestArrays(loader, requestBody);
        Class<?> castType = loader.loadClass(requiredAny(catalog, "native.cast.owner", "request.owner").replace('/', '.'));
        Object cast = unsafeAllocate(castType);
        setField(cast, castType, requiredAny(catalog, "cast.actor.field", "native.cast.actor.field"), actor);
        setField(cast, castType, requiredAny(catalog, "cast.target.field", "native.cast.target.field"), target);
        setField(cast, castType, requiredAny(catalog, "cast.targets.field", "native.cast.targets.field"), targets);
        setField(cast, castType, requiredAny(catalog, "request.tag.field", "native.cast.tag.field"), Integer.valueOf(generation));
        setField(cast, castType, "codexWaitRealTime", Boolean.FALSE);
        // Match the ordinary native cast constructor used by the timing fixture. A
        // non-negative g selects the alternate BBLet route instead of opcode 6/9.
        setField(cast, castType, "g", Byte.valueOf((byte)-1));
        setField(cast, castType, "k", Integer.valueOf(0));
        setField(cast, castType, "n", Integer.valueOf(0));
        setField(cast, castType, requiredAny(catalog, "cast.request.frame.field", "request.defer.frame.field"),
                Integer.valueOf(1));
        setField(actor, actor.getClass(), "cQ", Integer.valueOf(1));
        setStaticRequestInterval(loader, catalog, (short)0);
        return new NativeRequestSpec(castType, cast, actor, target, intervalField.name);
    }

    private static void verifyNativeRequestCadenceMatrix(NativeProbeLoader loader, File candidate,
            Properties catalog, Class<?> runtime, Object actor, Object target, int generation, long base)
            throws Exception {
        int[] configuredDelays = new int[]{1, 650, 1000};
        short[] nativeIntervals = new short[]{0, 1500};
        int[] speedCodes = new int[]{1, 2, 3, -8, -9};
        Class<?> speedOwner = loader.loadClass(encoded("lliiliil").replace('/', '.'));
        Field speedField = field(speedOwner, encoded("lilliliiiii"));
        require(Modifier.isStatic(speedField.getModifiers()) && speedField.getType() == Integer.TYPE,
                "Native speed setting is the expected static integer field");
        Class<?> owner = loader.loadClass(requiredAny(catalog, "request.timestamp.owner", "request.owner").replace('/', '.'));
        Field timestamp = field(owner, requiredAny(catalog, "request.timestamp.field", "request.timestamp.field"));
        int rows = 0;
        for (int speed : speedCodes) {
          speedField.setInt(null, speed);
          for (short interval : nativeIntervals) for (int delay : configuredDelays) {
            require(boolStringArg(runtime, "submitDelay", String.valueOf(delay)),
                    "Native runtime accepts cadence matrix delay " + delay);
            NativeRequestSpec spec = prepareNativeRequest(loader, candidate, catalog, actor, target,
                    generation, (byte)1);
            setField(nativeRequestSkill, nativeRequestSkill.getClass(), spec.skillIntervalField,
                    Short.valueOf(interval));
            setNativeRequestClock(loader, catalog, base, interval);
            nativeRequestNow = base;
            long floor = Math.max((long)delay, (long)interval);
            require(integerArg(runtime, "requestMinimum", generation) == delay,
                    "Tagged request floor tracks configured Auto Attack delay independently of native interval");
            Method send = spec.castType.getMethod(requiredAny(catalog, "hook.request.method", "request.method"));
            Field wait = field(spec.castType, "codexWaitRealTime");
            int beforeFrame = field(actor.getClass(), "cQ").getInt(actor);
            synchronized (nativePackets) { nativePackets.clear(); }
            nativeRequestNow = base + floor - 1L;
            invoke(send, spec.cast);
            require(attackPacketCount() == 0 && wait.getBoolean(spec.cast)
                            && field(actor.getClass(), "cQ").getInt(actor) == beforeFrame
                            && timestamp.getLong(null) == base,
                    "Actual native opcode-6 request stays pending and keeps animation/timestamp at floor-1; delay="
                            + delay + " interval=" + interval);
            nativeRequestNow = base + floor;
            invoke(send, spec.cast);
            require(attackPacketCount() == 0 && wait.getBoolean(spec.cast)
                            && field(actor.getClass(), "cQ").getInt(actor) == beforeFrame
                            && timestamp.getLong(null) == base,
                    "Actual native request remains pending at the strict floor; delay="
                            + delay + " interval=" + interval);
            nativeRequestNow = base + floor + 1L;
            invoke(send, spec.cast);
            require(attackPacketCount() == 1 && packetHasOpcode(6)
                            && packetHasPayload(6, "[0, 1, 0, 41]")
                            && timestamp.getLong(null) == nativeRequestNow,
                    "Actual native player opcode-6 request sends once above max(delay,native interval); delay="
                            + delay + " interval=" + interval + " speed=" + speed + " packets=" + nativePacketSnapshot()
                            + " timestamp=" + timestamp.getLong(null));
            rows++;
          }
        }
        speedField.setInt(null, 1);
        require(rows == 30, "Actual native request matrix covers 1/650/1000ms, intervals 0/1500ms, and x1/x2/x3/x0.8/x0.9");
        require(boolStringArg(runtime, "submitDelay", "650"), "Request matrix restores the default 650ms delay");
    }

    private static void seedNativeRequestArrays(ClassLoader loader, MethodNode request) throws Exception {
        java.util.HashSet<String> assigned = new java.util.HashSet<String>();
        for (AbstractInsnNode instruction : request.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != Opcodes.GETSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode)instruction;
            if (!("[I".equals(field.desc) || "[[I".equals(field.desc))) continue;
            String key = field.owner + "." + field.name;
            if (!assigned.add(key)) continue;
            Class<?> owner = loader.loadClass(field.owner.replace('/', '.'));
            Field member = field(owner, field.name);
            Object value = member.get(null);
            if ("[I".equals(field.desc)) {
                if (!(value instanceof int[])) value = new int[64];
                else Arrays.fill((int[])value, 0);
            } else {
                int[][] rows = value instanceof int[][] ? (int[][])value : new int[8][64];
                for (int i = 0; i < rows.length; i++) {
                    if (rows[i] == null || rows[i].length < 64) rows[i] = new int[64];
                    else Arrays.fill(rows[i], 0);
                }
                value = rows;
            }
            if (member.get(null) != value) setStaticField(owner, field.name, value);
        }
        require(!assigned.isEmpty(), "Native request skill/cost tables are seeded with real native arrays");
    }

    private static void setStaticRequestInterval(ClassLoader loader, Properties catalog, short value) throws Exception {
        Class<?> owner = loader.loadClass(requiredAny(catalog, "request.timestamp.owner", "request.owner").replace('/', '.'));
        setStaticField(owner, requiredAny(catalog, "request.timestamp.field", "request.timestamp.field"), Long.valueOf(1000000L));
        setStaticField(owner, requiredAny(catalog, "request.delay.field", "request.delay.field"), Short.valueOf(value));
    }

    private static void setNativeRequestClock(ClassLoader loader, Properties catalog, long timestamp, short interval)
            throws Exception {
        Class<?> owner = loader.loadClass(requiredAny(catalog, "request.timestamp.owner", "request.owner").replace('/', '.'));
        setStaticField(owner, requiredAny(catalog, "request.timestamp.field", "request.timestamp.field"), Long.valueOf(timestamp));
        setStaticField(owner, requiredAny(catalog, "request.delay.field", "request.delay.field"), Short.valueOf(interval));
    }

    private static boolean packetHasOpcode(int opcode) {
        synchronized (nativePackets) {
            String prefix = opcode + ":[";
            for (String packet : nativePackets) if (packet.startsWith(prefix) && packet.length() > prefix.length()) return true;
        }
        return false;
    }

    private static boolean packetHasPayload(int opcode, String payload) {
        synchronized (nativePackets) { return nativePackets.contains(opcode + ":" + payload); }
    }

    private static boolean hasAttackPacket() {
        return packetHasOpcode(6) || packetHasOpcode(9);
    }

    private static int attackPacketCount() {
        synchronized (nativePackets) {
            int count = 0;
            for (String packet : nativePackets) {
                if (packet.startsWith("6:[") || packet.startsWith("9:[")) count++;
            }
            return count;
        }
    }

    private static String nativePacketSnapshot() {
        synchronized (nativePackets) { return nativePackets.toString(); }
    }

    private static final class NativeRequestSpec {
        final Class<?> castType;
        final Object cast;
        final Object actor, target;
        final String skillIntervalField;
        NativeRequestSpec(Class<?> castType, Object cast, Object actor, Object target, String skillIntervalField) {
            this.castType = castType; this.cast = cast; this.actor = actor; this.target = target;
            this.skillIntervalField = skillIntervalField;
        }
    }

    private static void verifyRmsLifecycle(File candidate, Properties catalog, File micro, File reportDir)
            throws Exception {
        Common emulator = new Common(null);
        MemoryRecordStoreManager manager = new MemoryRecordStoreManager();
        manager.init(emulator);
        emulator.setRecordStoreManager(manager);
        MIDletBridge.setMicroEmulator(emulator);
        try (CandidateLoader loader = new CandidateLoader(candidate)) {
            Class<?> rmsType = loader.loadClass("luna.features.AutoAttackRmsStore");
            Object rms = rmsType.getConstructor().newInstance();
            Method load = rmsType.getMethod("load", int[].class);
            Method save = rmsType.getMethod("save", Boolean.TYPE, Integer.TYPE);
            int[] settings = new int[]{9, 9};
            load.invoke(rms, (Object)settings);
            require(settings[0] == 0 && settings[1] == 650,
                    "Missing Auto Attack RMS record defaults to monster mode/650ms");
            save.invoke(rms, Boolean.TRUE, Integer.valueOf(650));
            RecordStore store = RecordStore.openRecordStore("hbs_auto_attack_v1", false);
            byte[] bytes;
            try { bytes = store.getRecord(1); }
            finally { store.closeRecordStore(); }
            require(Arrays.equals(bytes, new byte[]{1, 1, 2, (byte)138}),
                    "Native RMS record is version 1, PK mode, 650ms in four bytes");
            settings[0] = 0; settings[1] = 0;
            load.invoke(rms, (Object)settings);
            require(settings[0] == 1 && settings[1] == 650,
                    "Native RMS adapter reloads mode and delay");
            save.invoke(rms, Boolean.TRUE, Integer.valueOf(1000));
            settings[0] = 0; settings[1] = 0;
            load.invoke(rms, (Object)settings);
            require(settings[0] == 1 && settings[1] == 1000,
                    "Native RMS persists the shared 1000ms delay with Quái + PK mode");
            save.invoke(rms, Boolean.FALSE, Integer.valueOf(1000));
            settings[0] = 1; settings[1] = 0;
            load.invoke(rms, (Object)settings);
            require(settings[0] == 0 && settings[1] == 1000,
                    "Switching back to Quái-only mode preserves the same shared RMS delay");
            save.invoke(rms, Boolean.FALSE, Integer.valueOf(1));
            settings[0] = 9; settings[1] = 9;
            load.invoke(rms, (Object)settings);
            require(settings[0] == 0 && settings[1] == 1,
                    "Native RMS persists 1ms/minimum and monsters-only mode");
            boolean rejected = false;
            try { save.invoke(rms, Boolean.TRUE, Integer.valueOf(1001)); }
            catch (java.lang.reflect.InvocationTargetException expected) {
                rejected = expected.getCause() instanceof IllegalArgumentException;
            }
            require(rejected, "RMS adapter rejects out-of-range delay instead of truncating it");
        }
        Properties expSymbols = load(new File("symbols/hung-exp-native-notices-single-20261013.properties"));
        runFileRmsPair(candidate, catalog, micro, reportDir, expSymbols);
    }

    private static void runFileRmsPair(File candidate, Properties catalog, File micro, File reportDir,
            Properties expSymbols) throws Exception {
        if (!reportDir.exists() && !reportDir.mkdirs()) throw new IllegalStateException("Could not create report directory");
        File home = new File(reportDir, "auto-attack-rms-home");
        File proof = new File(reportDir, "auto-attack-rms-proof.properties");
        File java = new File(new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        File host = new File(HungBySonAutoAttackBinaryTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        for (String mode : new String[]{"write", "read"}) {
            ArrayList<String> command = new ArrayList<String>();
            command.add(java.getAbsolutePath()); command.add("-Xverify:all");
            command.add("-Djava.awt.headless=true"); command.add("-Duser.home=" + home.getAbsolutePath());
            command.add("-Dsun.reflect.inflationThreshold=10000");
            command.add("-cp"); command.add(host.getAbsolutePath() + File.pathSeparator + micro.getAbsolutePath());
            command.add(HungBySonAutoAttackBinaryTest.class.getName() + "$FileRmsWorker");
            command.add(candidate.getAbsolutePath()); command.add(mode); command.add(proof.getAbsolutePath());
            command.add(expSymbols.getProperty("slot.0.cleanup.owner"));
            command.add(expSymbols.getProperty("slot.0.cleanup.method"));
            command.add(expSymbols.getProperty("slot.0.cleanup.descriptor"));
            command.add(catalog.getProperty("native.farmSave.owner"));
            command.add(catalog.getProperty("native.farmSave.method"));
            command.add(catalog.getProperty("native.farmSave.descriptor"));
            Process process = new ProcessBuilder(command).inheritIO().start();
            int exit = process.waitFor();
            require(exit == 0, "Independent File RMS " + mode + " JVM exits cleanly");
        }
    }

    /** Writer and reader run in different JVMs over MicroEmulator's File RMS. */
    public static final class FileRmsWorker {
        public static void main(String[] args) {
            int exit = 0;
            try { run(args); }
            catch (Throwable failure) { failure.printStackTrace(); exit = 1; }
            System.exit(exit);
        }

        private static void run(String[] args) throws Exception {
            require(args.length == 9,
                    "candidate.jar write|read proof.properties cleanup.owner cleanup.method cleanup.descriptor farm.owner farm.save farm.descriptor");
            File jar = new File(args[0]);
            String mode = args[1];
            Headless headless = new Headless();
            Field emulatorField = Headless.class.getDeclaredField("emulator");
            emulatorField.setAccessible(true);
            Common emulator = (Common)emulatorField.get(headless);
            FileRecordStoreManager disk = new FileRecordStoreManager();
            disk.init(emulator); emulator.setRecordStoreManager(disk); MIDletBridge.setMicroEmulator(emulator);
            ArrayList<String> options = new ArrayList<String>();
            options.add("--rms"); options.add("file"); options.add(jar.getAbsolutePath());
            DeviceEntry device = new DeviceEntry("Default device", null,
                    "org/microemu/device/default/device.xml", true, false);
            require(emulator.initParams(options, device, J2SEDevice.class), "File RMS emulator initialized");
            emulator.initMIDlet(true); Thread.sleep(500L);
            javax.microedition.midlet.MIDlet active = MIDletBridge.getCurrentMIDlet();
            require(active != null, "Candidate MIDlet starts in isolated File RMS");
            ClassLoader loader = active.getClass().getClassLoader();
            Class<?> runtime = Class.forName(RUNTIME, true, loader);
            Class<?> storeType = Class.forName("luna.features.AutoAttackRmsStore", true, loader);
            Object store = storeType.getDeclaredConstructor().newInstance();
            Method load = storeType.getMethod("load", int[].class);
            int[] settings = new int[]{9, 9};
            if ("write".equals(mode)) {
                require(Boolean.TRUE.equals(runtime.getMethod("handleAction", Integer.TYPE)
                        .invoke(null, Integer.valueOf(MONSTERS_AND_PLAYERS))),
                        "Native runtime accepts the PK mode action");
                require(Boolean.TRUE.equals(runtime.getMethod("submitDelay", String.class)
                        .invoke(null, "1000")), "Native runtime accepts the 1000ms limit");
                runtime.getMethod("beforeFarmEnable").invoke(null);
                require(!((Boolean)runtime.getMethod("enabled").invoke(null)).booleanValue(),
                        "Native Farm handoff/cleanup leaves Auto Attack OFF");
                byte[] expBefore = new byte[]{0x45, 0x58, 0x50, 0x31, 2, 1,
                        0, 0, 0x1e, 0x61,
                        0, 0, 0, 0, 0x2d, (byte)0xa6, (byte)0xd9, 0x54,
                        0, 0, 0, 0, 0x2d, (byte)0xa6, (byte)0xd9, 0x54,
                        0, 20, 0, 30};
                writeStore("hbs_exp_levels_v2", expBefore);
                writeStore("inv_fixture_cleanup_probe", new byte[]{99});
                Class<?> farmType = Class.forName(args[6].replace('/', '.'), true, loader);
                Method farmSave = farmType.getDeclaredMethod(args[7], new Class<?>[0]);
                farmSave.setAccessible(true);
                require("()V".equals(args[8]) && Void.TYPE.equals(farmSave.getReturnType()),
                        "Auto Attack catalog identifies the native Farm config save method");
                Object farmReceiver = Modifier.isStatic(farmSave.getModifiers()) ? null : unsafeAllocate(farmType);
                farmSave.invoke(farmReceiver);
                byte[] farmBefore = readStore("inv_sys_auto_cfg");
                require(farmBefore != null && farmBefore.length > 0,
                        "Native Farm save method writes its existing inv_sys_auto_cfg settings record");
                byte[] farmLocations = new byte[]{0x46, 0x4c, 0x4f, 0x43, 0, 7, 1};
                writeStore("inv_sys_farm_loc", farmLocations);
                java.io.ByteArrayOutputStream accountBytes = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream accountData = new java.io.DataOutputStream(accountBytes);
                accountData.writeUTF("fixture-user"); accountData.writeUTF("fixture-pass"); accountData.flush();
                Method saveAccount = active.getClass().getDeclaredMethod("a", String.class, byte[].class);
                saveAccount.setAccessible(true);
                Object accountReceiver = Modifier.isStatic(saveAccount.getModifiers()) ? null : active;
                saveAccount.invoke(accountReceiver, "user_pass", accountBytes.toByteArray());
                byte[] accountBefore = readStore("inv_user_pass");
                require(accountBefore != null && accountBefore.length > 0,
                        "Actual native account-save method writes the existing inv_user_pass record with fixture credentials");
                Class<?> cleanupType = Class.forName(args[3].replace('/', '.'), true, loader);
                Method nativeCleanup = cleanupType.getDeclaredMethod(args[4], new Class<?>[0]);
                nativeCleanup.setAccessible(true);
                require(nativeCleanup.getParameterTypes().length == 0
                                && Void.TYPE.equals(nativeCleanup.getReturnType())
                                && "()V".equals(args[5]),
                        "Maintained EXP validation catalog identifies the native cleanup signature");
                Object receiver = Modifier.isStatic(nativeCleanup.getModifiers()) ? null : active;
                nativeCleanup.invoke(receiver);
                load.invoke(store, (Object)settings);
                require(settings[0] == 1 && settings[1] == 1000,
                        "Actual native login cleanup retained Auto Attack mode and delay");
                require(Arrays.equals(readStore("hbs_auto_attack_v1"), new byte[]{1, 1, 3, (byte)232}),
                        "Actual native cleanup preserves the separate four-byte Auto Attack record");
                require(RecordStore.listRecordStores() != null
                                && Arrays.asList(RecordStore.listRecordStores()).contains("hbs_exp_levels_v2")
                                && Arrays.equals(readStore("hbs_exp_levels_v2"), expBefore),
                        "Actual native cleanup preserves the byte-exact EXP RMS record");
                require(Arrays.asList(RecordStore.listRecordStores()).contains("inv_sys_auto_cfg")
                                && Arrays.equals(readStore("inv_sys_auto_cfg"), farmBefore),
                        "Actual native cleanup preserves the native Farm settings record byte-for-byte; names="
                                + Arrays.toString(RecordStore.listRecordStores()) + " actual="
                                + (Arrays.asList(RecordStore.listRecordStores()).contains("inv_sys_auto_cfg")
                                        ? Arrays.toString(readStore("inv_sys_auto_cfg")) : "deleted")
                                + " before=" + Arrays.toString(farmBefore));
                require(Arrays.asList(RecordStore.listRecordStores()).contains("inv_sys_farm_loc")
                                && Arrays.equals(readStore("inv_sys_farm_loc"), farmLocations),
                        "Actual native cleanup preserves the existing Farm locations record byte-for-byte");
                require(Arrays.asList(RecordStore.listRecordStores()).contains("inv_user_pass")
                                && Arrays.equals(readStore("inv_user_pass"), accountBefore),
                        "Actual native cleanup preserves the account RMS record byte-for-byte");
                require(!Arrays.asList(RecordStore.listRecordStores()).contains("inv_fixture_cleanup_probe"),
                        "Actual native cleanup still deletes an unrelated per-session RMS probe");
                Properties proof = new Properties();
                proof.setProperty("mode", String.valueOf(settings[0]));
                proof.setProperty("delay", String.valueOf(settings[1]));
                proof.setProperty("native_cleanup", "PASS");
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(args[2])) {
                    proof.store(output, "Auto Attack File RMS writer proof");
                }
            } else if ("read".equals(mode)) {
                load.invoke(store, (Object)settings);
                require(settings[0] == 1 && settings[1] == 1000,
                        "Fresh JVM reloads the saved mode/delay from File RMS");
                require(!((Boolean)runtime.getMethod("enabled").invoke(null)).booleanValue(),
                        "Fresh JVM starts Auto Attack OFF despite persisted mode/delay");
                require(((Boolean)runtime.getMethod("playersEnabled").invoke(null)).booleanValue()
                        && ((Integer)runtime.getMethod("delayMs").invoke(null)).intValue() == 1000,
                        "Lazy native runtime reads config but not the session ON flag");
                require(runtime.getMethod("statusText").invoke(null) == null,
                        "Restarted OFF HUD does not claim that Auto Attack is active");
                Properties proof = load(new File(args[2]));
                require("1".equals(proof.getProperty("mode"))
                        && "1000".equals(proof.getProperty("delay")),
                        "Reader observes the separate writer process proof");
            } else throw new IllegalArgumentException("Unknown RMS worker mode " + mode);
            System.out.println("FILE_RMS=" + mode + " PASS mode=" + settings[0] + " delay=" + settings[1]);
        }

        private static void writeStore(String name, byte[] data) throws Exception {
            RecordStore store = RecordStore.openRecordStore(name, true);
            try {
                if (store.getNumRecords() == 0) store.addRecord(data, 0, data.length);
                else store.setRecord(1, data, 0, data.length);
            } finally { store.closeRecordStore(); }
        }

        private static byte[] readStore(String name) throws Exception {
            RecordStore store = RecordStore.openRecordStore(name, false);
            try { return store.getRecord(1); }
            finally { store.closeRecordStore(); }
        }
    }

    private static void verifyHudSnapshots(File candidate, Properties catalog, File micro, File reportDir)
            throws Exception {
        if (!reportDir.exists() && !reportDir.mkdirs()) throw new IllegalStateException("Could not create HUD report directory");
        verifyNativeHudHookPlacement(candidate, catalog);
        File host = new File(HungBySonAutoAttackBinaryTest.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        File java = new File(new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        ArrayList<String> command = new ArrayList<String>();
        command.add(java.getAbsolutePath()); command.add("-Xverify:all");
        command.add("-Djava.awt.headless=true"); command.add("-Dsun.reflect.inflationThreshold=10000");
        command.add("-cp"); command.add(host.getAbsolutePath() + File.pathSeparator + micro.getAbsolutePath());
        command.add(HungBySonAutoAttackBinaryTest.class.getName() + "$HudWorker");
        String[] hudSymbols = nativeHudSymbols(candidate);
        command.add(candidate.getAbsolutePath()); command.add(reportDir.getAbsolutePath());
        command.add(hudSymbols[0]); command.add(hudSymbols[1]); command.add(hudSymbols[2]);
        Process process = new ProcessBuilder(command).inheritIO().start();
        int exit = process.waitFor();
        require(exit == 0, "Native HUD worker exits cleanly after rendering actual bridge output");
        File narrow = new File(reportDir, "auto-attack-hud-240.png");
        File wide = new File(reportDir, "auto-attack-hud-450.png");
        require(narrow.isFile() && narrow.length() > 64, "240px native Auto Attack HUD snapshot exists");
        require(wide.isFile() && wide.length() > 64, "450px native Auto Attack HUD snapshot exists");
    }

    private static void verifyNativeHudHookPlacement(File candidate, Properties catalog) throws Exception {
        Map<String, byte[]> entries = readClasses(candidate);
        String owner = requiredAny(catalog, "hook.hud.owner", "native.hud.owner");
        String methodName = requiredAny(catalog, "hook.hud.method", "native.hud.method");
        String descriptor = requiredAny(catalog, "hook.hud.descriptor", "native.hud.descriptor");
        ClassNode type = parse(entries.get(owner + ".class"));
        MethodNode method = findMethod(type, methodName, descriptor);
        List<AbstractInsnNode> code = real(method);
        String infoOwner = requiredAny(catalog, "hud.info.owner", "native.hud.info.owner");
        String infoField = requiredAny(catalog, "hud.info.field", "native.hud.info.field");
        int gate = -1, callback = -1, lastRowIncrement = -1;
        for (int i = 0; i < code.size(); i++) {
            AbstractInsnNode instruction = code.get(i);
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == Opcodes.GETSTATIC) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (infoOwner.equals(field.owner) && infoField.equals(field.name) && "Z".equals(field.desc)) gate = i;
            }
            if (instruction instanceof org.objectweb.asm.tree.IincInsnNode) {
                org.objectweb.asm.tree.IincInsnNode increment = (org.objectweb.asm.tree.IincInsnNode)instruction;
                if (increment.var == 7 && increment.incr == 11) lastRowIncrement = i;
            }
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if ("luna/generated/HungAutoAttackAccess".equals(call.owner)
                        && "hud".equals(call.name) && "(Ljava/lang/Object;II)V".equals(call.desc)) callback = i;
            }
        }
        require(gate >= 0 && gate < Math.min(8, code.size() - 1)
                        && code.get(gate + 1) instanceof org.objectweb.asm.tree.JumpInsnNode
                        && ((org.objectweb.asm.tree.JumpInsnNode)code.get(gate + 1)).getOpcode() == Opcodes.IFEQ,
                "Native HUD renderer keeps the Hiển thông tin early gate before its rows");
        require(lastRowIncrement >= 0 && callback > lastRowIncrement && callback + 1 < code.size()
                        && code.get(callback + 1) instanceof org.objectweb.asm.tree.JumpInsnNode,
                "HUD callback uses the native last-row y after its 11px increment and before the loop exit");
        require(code.get(callback - 2) instanceof org.objectweb.asm.tree.VarInsnNode
                        && ((org.objectweb.asm.tree.VarInsnNode)code.get(callback - 2)).var == 8
                        && code.get(callback - 1) instanceof org.objectweb.asm.tree.VarInsnNode
                        && ((org.objectweb.asm.tree.VarInsnNode)code.get(callback - 1)).var == 7,
                "Native HUD hook receives the established x/y coordinates from the original renderer");
    }

    private static String[] nativeHudSymbols(File candidate) throws Exception {
        ClassNode bridge = parse(readJarClass(candidate, "luna/generated/HungAutoAttackAccess"));
        MethodNode method = findMethod(bridge, "hud", "(Ljava/lang/Object;II)V");
        String graphics = null, fontOwner = null, fontField = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof org.objectweb.asm.tree.TypeInsnNode
                    && instruction.getOpcode() == Opcodes.CHECKCAST)
                graphics = ((org.objectweb.asm.tree.TypeInsnNode)instruction).desc;
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == Opcodes.GETSTATIC) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (field.desc.startsWith("L")) { fontOwner = field.owner; fontField = field.name; }
            }
        }
        require(graphics != null && fontOwner != null && fontField != null,
                "Native HUD bridge catalog exposes a Graphics wrapper and font singleton");
        return new String[]{graphics, fontOwner, fontField};
    }

    /** Isolated from host ASM 9 while the native MIDP font and graphics classes load. */
    public static final class HudWorker {
        public static void main(String[] args) {
            int exit = 0;
            try { render(args); }
            catch (Throwable failure) { failure.printStackTrace(); exit = 1; }
            System.exit(exit);
        }

        private static void render(String[] args) throws Exception {
            require(args.length == 5, "candidate.jar reportDir graphics.owner font.owner font.field");
            File jar = new File(args[0]), directory = new File(args[1]);
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not create " + directory);
            Headless headless = new Headless();
            Field emulatorField = Headless.class.getDeclaredField("emulator");
            emulatorField.setAccessible(true);
            Common emulator = (Common)emulatorField.get(headless);
            MemoryRecordStoreManager manager = new MemoryRecordStoreManager();
            manager.init(emulator); emulator.setRecordStoreManager(manager); MIDletBridge.setMicroEmulator(emulator);
            ArrayList<String> options = new ArrayList<String>();
            options.add("--rms"); options.add("memory"); options.add(jar.getAbsolutePath());
            DeviceEntry device = new DeviceEntry("Default device", null,
                    "org/microemu/device/default/device.xml", true, false);
            require(emulator.initParams(options, device, J2SEDevice.class), "Memory RMS emulator initialized for HUD");
            emulator.initMIDlet(true);
            Thread.sleep(700L);
            javax.microedition.midlet.MIDlet active = MIDletBridge.getCurrentMIDlet();
            require(active != null, "Candidate MIDlet starts before rendering the native HUD");
            ClassLoader loader = active.getClass().getClassLoader();
            Class<?> runtime = Class.forName(RUNTIME, true, loader);
            Class<?> api = Class.forName(ACCESS, true, loader);
            Class<?> settingsType = Class.forName(SETTINGS, true, loader);
            Class<?> clockType = Class.forName(CLOCK, true, loader);
            World world = new World(); world.runtime = runtime;
            Object access = Proxy.newProxyInstance(loader, new Class<?>[]{api}, world);
            int[] inputSettings = new int[]{1, 650};
            Object settings = Proxy.newProxyInstance(loader, new Class<?>[]{settingsType}, new SettingsHandler(inputSettings));
            Object clock = Proxy.newProxyInstance(loader, new Class<?>[]{clockType}, new Clock());
            runtime.getMethod("installForTest", api, settingsType, clockType).invoke(null, access, settings, clock);
            verifyNativeDelayInput(loader, active, runtime, inputSettings);

            Class<?> bridge = Class.forName("luna.generated.HungAutoAttackAccess", true, loader);
            Class<?> graphicsType = Class.forName(args[2].replace('/', '.'), true, loader);
            Class<?> fontType = Class.forName(args[3].replace('/', '.'), true, loader);
            Field fontMember = field(fontType, args[4]);
            Object font = fontMember.get(null);
            require(font != null, "Native font is initialized by the candidate MIDlet");
            Method draw = bridge.getMethod("hud", Object.class, Integer.TYPE, Integer.TYPE);
            require(runtime.getMethod("statusText").invoke(null) == null, "OFF HUD status is absent before enabling");
            Image blank = renderOne(draw, graphicsType, null, 240, directory, "auto-attack-hud-240-off.png");
            int beforePixels = nonBackgroundPixels(blank, 0x18202B);
            require(beforePixels == 0, "OFF native HUD snapshot leaves the dark gameplay row clear");
            require(Boolean.TRUE.equals(runtime.getMethod("handleAction", Integer.TYPE)
                    .invoke(null, Integer.valueOf(MONSTERS))), "Actual runtime enables the native HUD row");
            String text = (String)runtime.getMethod("statusText").invoke(null);
            require("Auto Đánh: Chỉ Quái [7]".equals(text), "HUD status text reflects the selected monster mode");
            Image narrow = renderOne(draw, graphicsType, text, 240, directory, "auto-attack-hud-240.png");
            Image wide = renderOne(draw, graphicsType, text, 450, directory, "auto-attack-hud-450.png");
            int narrowPixels = nonBackgroundPixels(narrow, 0x18202B);
            int widePixels = nonBackgroundPixels(wide, 0x18202B);
            require(narrowPixels >= 200,
                    "Actual native font paints the active status over the OFF snapshot at 240px");
            require(widePixels >= 200,
                    "Actual native font paints the active status over the OFF snapshot at 450px");
            require(narrowPixels < 240 * 32 && widePixels < 450 * 32
                    && maxForegroundX(narrow, 0x18202B) < 238 && maxForegroundX(wide, 0x18202B) < 448,
                    "HUD status remains within the first 32px at both native viewport widths");
            require(Boolean.TRUE.equals(runtime.getMethod("handleAction", Integer.TYPE)
                    .invoke(null, Integer.valueOf(MONSTERS_AND_PLAYERS))), "Actual runtime switches HUD into Quái & PK mode");
            String pkText = (String)runtime.getMethod("statusText").invoke(null);
            require(pkText != null && pkText.contains("Quái & PK [6]"), "PK HUD line reports the selected native mode");
            Image pk = renderOne(draw, graphicsType, pkText, 240, directory, "auto-attack-hud-pk-240.png");
            require(nonBackgroundPixels(pk, 0x18202B) >= 200, "PK HUD status is readable on the dark 240px snapshot");
            require(Boolean.TRUE.equals(runtime.getMethod("handleAction", Integer.TYPE)
                    .invoke(null, Integer.valueOf(TOGGLE))), "Actual runtime toggles Auto Attack OFF after the PK snapshot");
            Image offAgain = renderOne(draw, graphicsType, null, 240, directory, "auto-attack-hud-off.png");
            require(runtime.getMethod("statusText").invoke(null) == null
                            && nonBackgroundPixels(offAgain, 0x18202B) == 0,
                    "Turning Auto Attack OFF clears the rendered HUD line");
            System.out.println("HUD_RENDERED=240,450; STATUS=" + text);
        }

        private static Image renderOne(Method draw, Class<?> graphicsType,
                String text, int width, File directory, String fileName) throws Exception {
            Image image = Image.createImage(width, 64);
            Graphics graphics = image.getGraphics();
            graphics.setColor(0x18202B); graphics.fillRect(0, 0, width, 64);
            Object wrapper = unsafeAllocate(graphicsType);
            boolean bound = false;
            for (Field field : allFields(graphicsType)) {
                if (Graphics.class.equals(field.getType())) {
                    field.setAccessible(true); field.set(wrapper, graphics); bound = true; break;
                }
            }
            require(bound, "Native Graphics wrapper binds to MicroEmulator's image surface");
            if (text != null) draw.invoke(null, wrapper, Integer.valueOf(8), Integer.valueOf(12));
            writeImage(image, new File(directory, fileName));
            return image;
        }

        private static void verifyNativeDelayInput(ClassLoader loader, javax.microedition.midlet.MIDlet active,
                Class<?> runtime, int[] settings) throws Exception {
            Class<?> input = Class.forName("luna.generated.HungAutoAttackInput", true, loader);
            Method open = input.getMethod("open", Integer.TYPE);
            javax.microedition.lcdui.Display display = javax.microedition.lcdui.Display.getDisplay(active);
            open.invoke(null, Integer.valueOf(650));
            awaitDisplay(display, javax.microedition.lcdui.TextBox.class);
            javax.microedition.lcdui.Displayable current = display.getCurrent();
            require(current instanceof javax.microedition.lcdui.TextBox
                            && ((Boolean)field(input, "editing").get(null)).booleanValue(),
                    "Native Delay action opens the actual editor and pauses Auto Attack selection; current="
                            + (current == null ? "null" : current.getClass().getName()) + " editing="
                            + field(input, "editing").get(null));
            javax.microedition.lcdui.TextBox editor = (javax.microedition.lcdui.TextBox)current;
            Field okField = field(input, "ok"), cancelField = field(input, "cancel");
            javax.microedition.lcdui.Command ok = (javax.microedition.lcdui.Command)okField.get(editor);
            javax.microedition.lcdui.Command cancel = (javax.microedition.lcdui.Command)cancelField.get(editor);
            // The native TextBox is numeric-only, so exercise a parseable but
            // out-of-range value (the runtime separately rejects malformed strings).
            editor.setString("0");
            ((javax.microedition.lcdui.CommandListener)editor).commandAction(ok, editor);
            awaitDisplay(display, javax.microedition.lcdui.Alert.class);
            require(display.getCurrent() instanceof javax.microedition.lcdui.Alert
                            && ((javax.microedition.lcdui.Alert)display.getCurrent()).getTimeout()
                                    == javax.microedition.lcdui.Alert.FOREVER
                            && ((Boolean)field(input, "editing").get(null)).booleanValue()
                            && ((Integer)runtime.getMethod("delayMs").invoke(null)).intValue() == 650,
                    "Invalid native input holds the editor behind an active alert and leaves saved delay unchanged");
            display.setCurrent(editor); // Dismiss the alert as the MIDP user would.
            ((javax.microedition.lcdui.CommandListener)editor).commandAction(cancel, editor);
            require(!((Boolean)field(input, "editing").get(null)).booleanValue()
                            && !((Boolean)runtime.getMethod("enabled").invoke(null)).booleanValue()
                            && ((Boolean)runtime.getMethod("playersEnabled").invoke(null)).booleanValue()
                            && ((Integer)runtime.getMethod("delayMs").invoke(null)).intValue() == 650
                            && settings[0] == 1 && settings[1] == 650,
                    "Cancel closes the actual input without changing mode, delay, or Auto Attack state");

            open.invoke(null, Integer.valueOf(650));
            awaitDisplay(display, javax.microedition.lcdui.TextBox.class);
            editor = (javax.microedition.lcdui.TextBox)display.getCurrent();
            ok = (javax.microedition.lcdui.Command)okField.get(editor);
            editor.setString("1000");
            ((javax.microedition.lcdui.CommandListener)editor).commandAction(ok, editor);
            require(!((Boolean)field(input, "editing").get(null)).booleanValue()
                            && ((Integer)runtime.getMethod("delayMs").invoke(null)).intValue() == 1000
                            && settings[0] == 1 && settings[1] == 1000,
                    "Native input saves the accepted 1000ms upper boundary and returns to gameplay");
            open.invoke(null, Integer.valueOf(1000));
            awaitDisplay(display, javax.microedition.lcdui.TextBox.class);
            editor = (javax.microedition.lcdui.TextBox)display.getCurrent();
            ok = (javax.microedition.lcdui.Command)okField.get(editor);
            editor.setString("650");
            ((javax.microedition.lcdui.CommandListener)editor).commandAction(ok, editor);
            require(((Integer)runtime.getMethod("delayMs").invoke(null)).intValue() == 650
                            && settings[0] == 1 && settings[1] == 650
                            && !((Boolean)runtime.getMethod("enabled").invoke(null)).booleanValue(),
                    "Native input saves the 650ms default while Auto Attack remains OFF");
        }

        private static void awaitDisplay(javax.microedition.lcdui.Display display, Class<?> expected)
                throws InterruptedException {
            for (int i = 0; i < 40 && !expected.isInstance(display.getCurrent()); i++) Thread.sleep(25L);
        }

        private static int nonBackgroundPixels(Image image, int rgb) {
            int width = image.getWidth(), height = image.getHeight();
            int[] pixels = new int[width * height];
            image.getRGB(pixels, 0, width, 0, 0, width, height);
            int count = 0;
            for (int pixel : pixels) if ((pixel & 0xFFFFFF) != rgb) count++;
            return count;
        }

        private static int maxForegroundX(Image image, int background) {
            int width = image.getWidth(), height = image.getHeight();
            int[] pixels = new int[width * height];
            image.getRGB(pixels, 0, width, 0, 0, width, height);
            int max = -1;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                if ((pixels[y * width + x] & 0xFFFFFF) != background) max = Math.max(max, x);
            return max;
        }

        private static void writeImage(Image image, File file) throws Exception {
            int width = image.getWidth(), height = image.getHeight();
            int[] pixels = new int[width * height];
            image.getRGB(pixels, 0, width, 0, 0, width, height);
            BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            out.setRGB(0, 0, width, height, pixels, 0, width);
            ImageIO.write(out, "png", file);
        }
    }

    private static void verifyRemoteAnimationBarrier(File input, File candidate, Properties catalog)
            throws Exception {
        Map<String, byte[]> before = readClasses(input);
        Map<String, byte[]> after = readClasses(candidate);
        String entity = requiredAny(catalog, "native.entity.owner", "entity.owner");
        Properties remoteSymbols = load(new File("build/hung-exp-native-notices-20261013-a/single-validation.properties"));
        String callerOwner = requiredAny(remoteSymbols, "hook.unseenCombat.owner", "unseenCombat.owner");
        String callerName = requiredAny(remoteSymbols, "hook.unseenCombat.method", "unseenCombat.method");
        String callerDescriptor = requiredAny(remoteSymbols, "hook.unseenCombat.descriptor", "unseenCombat.descriptor");
        ClassNode callerClass = parse(before.get(callerOwner + ".class"));
        MethodNode pinnedCaller = findMethod(callerClass, callerName, callerDescriptor);
        String dispatchDescriptor = "(L" + entity + ";Let;IB)V";
        String helperOwner = null, helperName = null;
        int matchingHelpers = 0;
        for (AbstractInsnNode instruction : pinnedCaller.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (!dispatchDescriptor.equals(call.desc)) continue;
            byte[] helperBytes = before.get(call.owner + ".class");
            if (helperBytes == null) continue;
            ClassNode helperClass = parse(helperBytes);
            MethodNode helper;
            try { helper = findMethod(helperClass, call.name, call.desc); }
            catch (AssertionError missing) { continue; }
            if (animationBarrierCount(helper, entity) != 2) continue;
            helperOwner = call.owner; helperName = call.name; matchingHelpers++;
        }
        require(matchingHelpers == 1,
                "Pinned native remote receiver resolves exactly one two-cG animation helper; found=" + matchingHelpers);
        ClassNode source = parse(before.get(helperOwner + ".class"));
        ClassNode output = parse(after.get(helperOwner + ".class"));
        MethodNode sourceMethod = findMethod(source, helperName, dispatchDescriptor);
        MethodNode outputMethod = findMethod(output, helperName, dispatchDescriptor);
        String sourceCanonical = canonical(sourceMethod);
        MethodNode comparableOutput = outputMethod;
        String castOwner = requiredAny(catalog, "native.cast.owner", "cast.owner");
        String tagField = requiredAny(catalog, "request.tag.field", "native.cast.tag.field");
        if (helperOwner.equals(castOwner)) {
            comparableOutput = withoutAutoAttackCastTagPrefix(outputMethod, castOwner, tagField);
            List<AbstractInsnNode> resumeCode=real(comparableOutput);
            require(resumeCode.size()>3 && isVar(resumeCode.get(0),Opcodes.ALOAD,0)
                && resumeCode.get(1) instanceof MethodInsnNode
                && "captureMovementResume".equals(((MethodInsnNode)resumeCode.get(1)).name)
                && resumeCode.get(2) instanceof FieldInsnNode
                && catalog.getProperty("native.cast.movementResume.field").equals(((FieldInsnNode)resumeCode.get(2)).name),
                "Native cast resumes marker has exact initializer latch shape");
            for(int i=2;i>=0;i--)comparableOutput.instructions.remove(resumeCode.get(i));
            String startedField = requiredAny(catalog, "native.cast.startSerial.field", "native.cast.started.field");
            String bridgeOwner = requiredAny(catalog, "bridge.owner", "native.menu.owner");
            comparableOutput = withoutSpecialAttackDispatch(comparableOutput, castOwner, tagField, bridgeOwner);
            comparableOutput = withoutAutoAttackCastStartSerial(comparableOutput, castOwner, tagField, startedField);
            String farmOriginField = requiredAny(catalog, "request.farmOrigin.field", "native.cast.farmOrigin.field");
            String entityOwner = requiredAny(catalog, "native.entity.owner", "entity.owner");
            comparableOutput = withoutAutoAttackFarmOrigin(comparableOutput, castOwner, farmOriginField,
                    bridgeOwner, entityOwner);
        }
        String outputCanonical = canonical(comparableOutput);
        require(sourceCanonical.equals(outputCanonical),
                "Native remote-animation helper and cleanup are unchanged in " + helperOwner + "." + helperName
                        + " source=" + sourceCanonical.length() + " output=" + outputCanonical.length());
        require(animationBarrierCount(sourceMethod, entity) == 2,
                "Native remote animation still checks both actor/target cG==4 barriers");
    }

    private static boolean isField(AbstractInsnNode n, int opcode, String owner, String name, String descriptor) {
        if (!(n instanceof FieldInsnNode)) return false;
        FieldInsnNode f = (FieldInsnNode)n;
        return f.getOpcode() == opcode && f.owner.equals(owner) && f.name.equals(name) && f.desc.equals(descriptor);
    }

    private static MethodNode withoutSpecialAttackDispatch(MethodNode method, String castOwner, String tagField,
            String bridgeOwner) {
        List<AbstractInsnNode> code = real(method);
        int removed = 0;
        for (int i = 1; i + 7 < code.size(); i++) {
            if (!(code.get(i) instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)code.get(i);
            boolean legacy = "luna/features/AutoAttackRuntime".equals(call.owner)
                    && "specialActive".equals(call.name) && "()Z".equals(call.desc);
            boolean farmContext = bridgeOwner.equals(call.owner)
                    && "specialContext".equals(call.name) && "()Z".equals(call.desc);
            if (!legacy && !farmContext) continue;
            boolean exactDispatch = call.getOpcode() == Opcodes.INVOKESTATIC
                    && isField(code.get(i - 1), Opcodes.PUTFIELD, castOwner, "n", "I")
                    && isVar(code.get(i + 5), Opcodes.ALOAD, 0)
                    && code.get(i + 6) instanceof MethodInsnNode
                    && castOwner.equals(((MethodInsnNode)code.get(i + 6)).owner)
                    && "codexSpecialSend".equals(((MethodInsnNode)code.get(i + 6)).name)
                    && "()V".equals(((MethodInsnNode)code.get(i + 6)).desc)
                    && code.get(i + 6).getOpcode() == Opcodes.INVOKEVIRTUAL
                    && code.get(i + 7).getOpcode() == Opcodes.RETURN;
            if (legacy) {
                exactDispatch = exactDispatch
                        && code.get(i + 1).getOpcode() == Opcodes.IFEQ
                        && isVar(code.get(i + 2), Opcodes.ALOAD, 0)
                        && isField(code.get(i + 3), Opcodes.GETFIELD, castOwner, tagField, "I")
                        && code.get(i + 4).getOpcode() == Opcodes.IFLE
                        && ((org.objectweb.asm.tree.JumpInsnNode)code.get(i + 1)).label
                                == ((org.objectweb.asm.tree.JumpInsnNode)code.get(i + 4)).label;
            } else {
                if (!(code.get(i + 3) instanceof MethodInsnNode)) exactDispatch = false;
                else {
                    MethodInsnNode castContext = (MethodInsnNode)code.get(i + 3);
                    exactDispatch = exactDispatch
                            && code.get(i + 1).getOpcode() == Opcodes.IFEQ
                            && isVar(code.get(i + 2), Opcodes.ALOAD, 0)
                            && bridgeOwner.equals(castContext.owner)
                            && "specialCastContext".equals(castContext.name)
                            && ("(L" + castOwner + ";)Z").equals(castContext.desc)
                            && castContext.getOpcode() == Opcodes.INVOKESTATIC
                            && code.get(i + 4).getOpcode() == Opcodes.IFEQ
                            && ((org.objectweb.asm.tree.JumpInsnNode)code.get(i + 1)).label
                                    == ((org.objectweb.asm.tree.JumpInsnNode)code.get(i + 4)).label;
                }
            }
            require(exactDispatch, "Special dispatch insertion has the exact prepared-skill and guarded shape");
            for (int j = 0; j < 8; j++) method.instructions.remove(code.get(i + j));
            removed++;
        }
        require(removed <= 1, "At most one special dispatch insertion is normalized");
        return method;
    }

    private static int animationBarrierCount(MethodNode method, String entity) {
        int guards = 0;
        List<AbstractInsnNode> code = real(method);
        for (int i = 0; i + 2 < code.size(); i++) {
            AbstractInsnNode fieldNode = code.get(i);
            AbstractInsnNode constant = code.get(i + 1);
            AbstractInsnNode branch = code.get(i + 2);
            if (!(fieldNode instanceof FieldInsnNode) || !(branch instanceof org.objectweb.asm.tree.JumpInsnNode)) continue;
            FieldInsnNode field = (FieldInsnNode)fieldNode;
            org.objectweb.asm.tree.JumpInsnNode jump = (org.objectweb.asm.tree.JumpInsnNode)branch;
            if (field.getOpcode() == Opcodes.GETFIELD && entity.equals(field.owner)
                    && "cG".equals(field.name) && "I".equals(field.desc)
                    && integer(constant) == 4 && (jump.getOpcode() == Opcodes.IF_ICMPEQ
                            || jump.getOpcode() == Opcodes.IF_ICMPNE)) guards++;
        }
        return guards;
    }

    private static MethodNode withoutAutoAttackCastTagPrefix(MethodNode method, String castOwner, String tagField) {
        MethodNode copy = new MethodNode(Opcodes.ASM9);
        method.accept(copy);
        if (copy.tryCatchBlocks == null) copy.tryCatchBlocks = new ArrayList<org.objectweb.asm.tree.TryCatchBlockNode>();
        AbstractInsnNode first = copy.instructions.getFirst();
        while (first != null && first.getOpcode() < 0) first = first.getNext();
        AbstractInsnNode tagStore = null;
        for (AbstractInsnNode instruction = first; instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == Opcodes.PUTFIELD) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (castOwner.equals(field.owner) && tagField.equals(field.name) && "I".equals(field.desc)) {
                    tagStore = instruction; break;
                }
            }
        }
        require(first != null && tagStore != null, "Catalogue-specific cast generation prefix is present for normalization");
        for (AbstractInsnNode instruction = first; instruction != null; ) {
            AbstractInsnNode next = instruction.getNext();
            copy.instructions.remove(instruction);
            if (instruction == tagStore) break;
            instruction = next;
        }
        return copy;
    }

    private static MethodNode withoutAutoAttackCastStartSerial(MethodNode method, String castOwner,
            String tagField, String startedField) {
        List<AbstractInsnNode> code = real(method);
        int stores = 0;
        int tagReads = 0;
        int removedStart = -1;
        int removedEnd = -1;
        for (int i = 0; i < code.size(); i++) {
            AbstractInsnNode instruction = code.get(i);
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == Opcodes.GETFIELD) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (castOwner.equals(field.owner) && tagField.equals(field.name) && "I".equals(field.desc)) tagReads++;
            }
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != Opcodes.PUTFIELD) continue;
            FieldInsnNode field = (FieldInsnNode)instruction;
            if (!castOwner.equals(field.owner) || !startedField.equals(field.name) || !"I".equals(field.desc)) continue;
            stores++;
            require(i >= 8 && code.get(i - 1).getOpcode() == Opcodes.IADD
                            && integer(code.get(i - 2)) == 1
                            && code.get(i - 3) instanceof FieldInsnNode
                            && code.get(i - 3).getOpcode() == Opcodes.GETFIELD
                            && castOwner.equals(((FieldInsnNode)code.get(i - 3)).owner)
                            && startedField.equals(((FieldInsnNode)code.get(i - 3)).name)
                            && code.get(i - 4).getOpcode() == Opcodes.DUP
                            && code.get(i - 5) instanceof org.objectweb.asm.tree.VarInsnNode
                            && code.get(i - 5).getOpcode() == Opcodes.ALOAD
                            && ((org.objectweb.asm.tree.VarInsnNode)code.get(i - 5)).var == 0
                            && code.get(i - 6) instanceof org.objectweb.asm.tree.JumpInsnNode
                            && (code.get(i - 6).getOpcode() == Opcodes.IFNE
                                    || code.get(i - 6).getOpcode() == Opcodes.IFEQ
                                    || code.get(i - 6).getOpcode() == Opcodes.IFLE
                                    || code.get(i - 6).getOpcode() == Opcodes.IFGT)
                            && code.get(i - 7) instanceof FieldInsnNode
                            && code.get(i - 7).getOpcode() == Opcodes.GETFIELD
                            && castOwner.equals(((FieldInsnNode)code.get(i - 7)).owner)
                            && tagField.equals(((FieldInsnNode)code.get(i - 7)).name)
                            && code.get(i - 8) instanceof org.objectweb.asm.tree.VarInsnNode
                            && code.get(i - 8).getOpcode() == Opcodes.ALOAD
                            && ((org.objectweb.asm.tree.VarInsnNode)code.get(i - 8)).var == 0,
                    "Native cast-start serial instrumentation has the exact catalogued tag guard and increment shape");
            removedStart = i - 8;
            removedEnd = i;
        }
        require(stores == 1 && tagReads == 1,
                "Native cast helper contains exactly one generation-guarded start-serial instrumentation");
        for (int i = removedEnd; i >= removedStart; i--) method.instructions.remove(code.get(i));
        return method;
    }

    private static MethodNode withoutAutoAttackFarmOrigin(MethodNode method, String castOwner,
            String farmOriginField, String bridgeOwner, String entityOwner) {
        List<AbstractInsnNode> code = real(method);
        int stores = 0, originCalls = 0;
        int start = -1, end = -1;
        for (int i = 0; i < code.size(); i++) {
            AbstractInsnNode instruction = code.get(i);
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.getOpcode() == Opcodes.INVOKESTATIC && bridgeOwner.equals(call.owner)
                        && "captureFarmOrigin".equals(call.name)
                        && ("(L" + entityOwner + ";)Z").equals(call.desc)) originCalls++;
            }
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != Opcodes.PUTFIELD) continue;
            FieldInsnNode field = (FieldInsnNode)instruction;
            if (!castOwner.equals(field.owner) || !farmOriginField.equals(field.name) || !"Z".equals(field.desc)) continue;
            stores++;
            require(i >= 3 && code.get(i - 1) instanceof MethodInsnNode
                            && code.get(i - 1).getOpcode() == Opcodes.INVOKESTATIC
                            && bridgeOwner.equals(((MethodInsnNode)code.get(i - 1)).owner)
                            && "captureFarmOrigin".equals(((MethodInsnNode)code.get(i - 1)).name)
                            && ("(L" + entityOwner + ";)Z").equals(((MethodInsnNode)code.get(i - 1)).desc)
                            && isVar(code.get(i - 2), Opcodes.ALOAD, 1)
                            && isVar(code.get(i - 3), Opcodes.ALOAD, 0),
                    "Farm request provenance is captured from the initializer target through the generated bridge");
            start = i - 3; end = i;
        }
        require(stores == 1 && originCalls == 1,
                "Native cast initializer contains one exact Farm-origin provenance write");
        for (int i = end; i >= start; i--) method.instructions.remove(code.get(i));
        return method;
    }

    private static ClassNode parse(byte[] bytes) {
        if (bytes == null) throw new AssertionError("Missing class bytes");
        ClassNode result = new ClassNode(); new ClassReader(bytes).accept(result, 0); return result;
    }

    private static MethodNode findMethod(ClassNode type, String name, String descriptor) {
        MethodNode result = null;
        for (MethodNode method : type.methods) if (method.name.equals(name) && method.desc.equals(descriptor)) {
            if (result != null) throw new AssertionError("Duplicate " + type.name + "." + name + descriptor);
            result = method;
        }
        if (result == null) throw new AssertionError("Missing " + type.name + "." + name + descriptor);
        return result;
    }

    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction : method.instructions.toArray())
            if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }

    private static int integer(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) return opcode - Opcodes.ICONST_0;
        if (instruction instanceof org.objectweb.asm.tree.IntInsnNode)
            return ((org.objectweb.asm.tree.IntInsnNode)instruction).operand;
        if (instruction instanceof org.objectweb.asm.tree.LdcInsnNode
                && ((org.objectweb.asm.tree.LdcInsnNode)instruction).cst instanceof Integer)
            return ((Integer)((org.objectweb.asm.tree.LdcInsnNode)instruction).cst).intValue();
        return Integer.MIN_VALUE;
    }

    private static String canonical(MethodNode method) {
        List<AbstractInsnNode> instructions = real(method);
        java.util.IdentityHashMap<AbstractInsnNode, Integer> positions = new java.util.IdentityHashMap<AbstractInsnNode, Integer>();
        int pc = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() >= 0) positions.put(instruction, Integer.valueOf(pc++));
            else positions.put(instruction, Integer.valueOf(pc));
        }
        StringBuilder value = new StringBuilder();
        for (AbstractInsnNode instruction : instructions) {
            value.append(instruction.getOpcode()).append(':');
            if (instruction instanceof org.objectweb.asm.tree.VarInsnNode)
                value.append(((org.objectweb.asm.tree.VarInsnNode)instruction).var);
            else if (instruction instanceof org.objectweb.asm.tree.IntInsnNode)
                value.append(((org.objectweb.asm.tree.IntInsnNode)instruction).operand);
            else if (instruction instanceof org.objectweb.asm.tree.TypeInsnNode)
                value.append(((org.objectweb.asm.tree.TypeInsnNode)instruction).desc);
            else if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                value.append(field.owner).append('.').append(field.name).append(field.desc);
            } else if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                value.append(call.owner).append('.').append(call.name).append(call.desc).append(call.itf);
            } else if (instruction instanceof org.objectweb.asm.tree.IincInsnNode) {
                org.objectweb.asm.tree.IincInsnNode increment = (org.objectweb.asm.tree.IincInsnNode)instruction;
                value.append(increment.var).append(',').append(increment.incr);
            } else if (instruction instanceof org.objectweb.asm.tree.LdcInsnNode)
                value.append(((org.objectweb.asm.tree.LdcInsnNode)instruction).cst);
            if (instruction instanceof org.objectweb.asm.tree.JumpInsnNode) {
                AbstractInsnNode label = ((org.objectweb.asm.tree.JumpInsnNode)instruction).label;
                value.append("->").append(positions.get(label));
            } else if (instruction instanceof org.objectweb.asm.tree.TableSwitchInsnNode) {
                org.objectweb.asm.tree.TableSwitchInsnNode table = (org.objectweb.asm.tree.TableSwitchInsnNode)instruction;
                value.append(table.min).append(',').append(table.max).append(',').append(positions.get(table.dflt));
                for (org.objectweb.asm.tree.LabelNode label : table.labels) value.append(',').append(positions.get(label));
            } else if (instruction instanceof org.objectweb.asm.tree.LookupSwitchInsnNode) {
                org.objectweb.asm.tree.LookupSwitchInsnNode table = (org.objectweb.asm.tree.LookupSwitchInsnNode)instruction;
                value.append(table.keys).append(',').append(positions.get(table.dflt));
                for (org.objectweb.asm.tree.LabelNode label : table.labels) value.append(',').append(positions.get(label));
            }
            value.append(';');
        }
        if (method.tryCatchBlocks != null) for (org.objectweb.asm.tree.TryCatchBlockNode block : method.tryCatchBlocks)
            value.append('T').append(positions.get(block.start)).append(',').append(positions.get(block.end))
                    .append(',').append(positions.get(block.handler)).append(',').append(block.type);
        return value.toString();
    }

    private static boolean bool(Class<?> runtime, String method) throws Exception {
        return ((Boolean)runtime.getMethod(method).invoke(null)).booleanValue();
    }

    private static Object invoke(Class<?> runtime, String method, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) types[i] = args[i] instanceof Integer ? Integer.TYPE : args[i].getClass();
        try { return runtime.getMethod(method, types).invoke(null, args); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new IllegalStateException(cause);
        }
    }

    private static int integer(Class<?> runtime, String method) throws Exception {
        return ((Integer)runtime.getMethod(method).invoke(null)).intValue();
    }

    private static void invokeAction(Class<?> runtime, int action) throws Exception {
        require(Boolean.TRUE.equals(runtime.getMethod("handleAction", Integer.TYPE)
                .invoke(null, Integer.valueOf(action))), "Candidate runtime handles action " + action);
    }

    private static int integerArg(Class<?> runtime, String method, int value) throws Exception {
        return ((Integer)runtime.getMethod(method, Integer.TYPE).invoke(null, Integer.valueOf(value))).intValue();
    }

    private static boolean boolArg(Class<?> runtime, String method, int tag, Object target) throws Exception {
        return ((Boolean)runtime.getMethod(method, Integer.TYPE, Object.class)
                .invoke(null, Integer.valueOf(tag), target)).booleanValue();
    }

    private static final class Clock implements InvocationHandler {
        long now;
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("nowMillis".equals(method.getName())) return Long.valueOf(now);
            return defaultValue(method.getReturnType());
        }
    }

    private static final class SettingsHandler implements InvocationHandler {
        final int[] state;
        SettingsHandler(int[] state) { this.state = state; }
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("load".equals(method.getName())) {
                int[] out = (int[])args[0]; out[0] = state[0]; out[1] = state[1]; return null;
            }
            if ("save".equals(method.getName())) {
                state[0] = ((Boolean)args[0]).booleanValue() ? 1 : 0;
                state[1] = ((Integer)args[1]).intValue(); return null;
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static final class World implements InvocationHandler {
        final List<Object> entities = new ArrayList<Object>();
        final IdentityHashMap<Object, Integer> distances = new IdentityHashMap<Object, Integer>();
        final IdentityHashMap<Object, String> kinds = new IdentityHashMap<Object, String>();
        Object player = new Object();
        Object lastAttackTarget;
        boolean ready = true;
        boolean busy;
        boolean menuEnabled;
        boolean menuPlayers;
        int menuDelay;
        int map = 1;
        int zone = 2;
        int stopFarmCalls;
        int cancelCalls;
        int menuCalls;
        int delayInputCalls;
        int delayInput;
        int attacks;
        int lastAttackTag;
        Class<?> runtime;

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("ready".equals(name)) return Boolean.valueOf(ready);
            if ("player".equals(name)) return player;
            if ("mapId".equals(name)) return Integer.valueOf(map);
            if ("zoneId".equals(name)) return Integer.valueOf(zone);
            if ("entityCount".equals(name)) return Integer.valueOf(entities.size());
            if ("entityAt".equals(name)) return entities.get(((Integer)args[0]).intValue());
            if ("validTarget".equals(name)) {
                Object target = args[0]; boolean players = ((Boolean)args[1]).booleanValue();
                String kind = kinds.get(target);
                return Boolean.valueOf("monster".equals(kind) || (players && "hostile-player".equals(kind)));
            }
            if ("distanceTo".equals(name)) {
                Integer value = distances.get(args[0]); return value == null ? Integer.valueOf(-1) : value;
            }
            if ("combatBusy".equals(name)) return Boolean.valueOf(busy);
            if ("attack".equals(name)) {
                attacks++;
                lastAttackTarget = args[0];
                lastAttackTag = ((Integer)runtime.getMethod("captureGeneration").invoke(null)).intValue();
                return Boolean.TRUE;
            }
            if ("cancelAttack".equals(name)) { cancelCalls++; return null; }
            if ("stopFarm".equals(name)) { stopFarmCalls++; return null; }
            if ("showMenu".equals(name)) {
                menuCalls++;
                menuEnabled = ((Boolean)args[0]).booleanValue();
                menuPlayers = ((Boolean)args[1]).booleanValue();
                menuDelay = ((Integer)args[2]).intValue();
                return null;
            }
            if ("showDelayInput".equals(name)) {
                delayInputCalls++;
                delayInput = ((Integer)args[0]).intValue();
                return null;
            }
            if ("notice".equals(name)) return null;
            return defaultValue(method.getReturnType());
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == Boolean.TYPE) return Boolean.FALSE;
        if (type == Byte.TYPE) return Byte.valueOf((byte)0);
        if (type == Short.TYPE) return Short.valueOf((short)0);
        if (type == Integer.TYPE) return Integer.valueOf(0);
        if (type == Long.TYPE) return Long.valueOf(0L);
        if (type == Float.TYPE) return Float.valueOf(0.0f);
        if (type == Double.TYPE) return Double.valueOf(0.0d);
        if (type == Character.TYPE) return Character.valueOf((char)0);
        return null;
    }

    private static final class CandidateLoader extends URLClassLoader {
        private final File jar;
        CandidateLoader(File jar) throws Exception {
            super(new URL[]{jar.toURI().toURL()}, HungBySonAutoAttackBinaryTest.class.getClassLoader());
            this.jar = jar;
        }
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("luna.")) return super.loadClass(name, resolve);
            Class<?> value = findLoadedClass(name);
            if (value == null) {
                String path = name.replace('.', '/') + ".class";
                try (JarFile file = new JarFile(jar)) {
                    JarEntry entry = file.getJarEntry(path);
                    if (entry == null) throw new ClassNotFoundException(name + " missing from candidate JAR");
                    InputStream in = file.getInputStream(entry);
                    byte[] bytes = readAll(in);
                    value = defineClass(name, bytes, 0, bytes.length);
                } catch (Exception failure) { throw new ClassNotFoundException(name, failure); }
            }
            if (resolve) resolveClass(value);
            return value;
        }
    }

    private static Map<String, byte[]> readClasses(File jarFile) throws Exception {
        java.util.HashMap<String, byte[]> result = new java.util.HashMap<String, byte[]>();
        try (JarFile jar = new JarFile(jarFile)) {
            java.util.Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                InputStream in = jar.getInputStream(entry);
                result.put(entry.getName(), readAll(in));
            }
        }
        return result;
    }

    private static Properties load(File file) throws Exception {
        Properties result = new Properties();
        try (InputStream in = new FileInputStream(file)) { result.load(in); }
        return result;
    }

    private static String requiredAny(Properties properties, String first, String second) {
        String value = properties.getProperty(first);
        if (value == null) value = properties.getProperty(second);
        require(value != null && value.length() > 0, "Catalog key " + first + " or " + second);
        return value.trim();
    }

    private static byte[] readAll(InputStream input) throws Exception {
        try (InputStream in = input; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02X", value & 0xff));
        return result.toString();
    }

    private static void require(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
}
