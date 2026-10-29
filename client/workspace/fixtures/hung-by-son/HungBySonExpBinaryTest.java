import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreNotFoundException;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.app.Headless;
import org.microemu.app.util.DeviceEntry;
import org.microemu.device.j2se.J2SEDevice;
import org.microemu.device.j2se.J2SEDeviceDisplay;
import org.microemu.app.util.FileRecordStoreManager;
import org.microemu.util.MemoryRecordStoreManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Native packet, bridge, RMS, namespace and HUD rendering verification. */
public final class HungBySonExpBinaryTest implements Opcodes {
    private static int checks;
    private static MemoryRecordStoreManager rmsManager;

    public static void main(String[] args) throws Exception {
        require(args.length == 4, "candidate.jar catalog.properties microemulator.jar screenshotsDir");
        File jarFile = new File(args[0]);
        File micro = new File(args[2]);
        File screenshots = new File(args[3]);
        Properties catalog = load(new File(args[1]));
        require(jarFile.isFile() && micro.isFile(), "Candidate and MicroEmulator exist");
        require(hash(jarFile).equals(required(catalog, "output.sha256")), "Candidate hash matches catalog");
        int slots = Integer.parseInt(required(catalog, "slots"));
        require(slots == 1 || slots == 3, "Supported single or three-slot candidate");
        require("42".equals(catalog.getProperty("frame.height.before"))
                && "56".equals(catalog.getProperty("frame.height.after")), "Native HUD height expands 42 to 56");
        require("96".equals(catalog.getProperty("frame.upper.width"))
                && "96".equals(catalog.getProperty("frame.width.after")), "Native HUD returns to its original 96px width");
        require("38".equals(catalog.getProperty("estimate.y.offset")), "EXP line follows native bars");

        Map<String, byte[]> entries = read(jarFile);
        verifyLinkage(entries, slots, jarFile, catalog);
        for (int slot = 0; slot < slots; slot++) verifyNativeHooks(entries, catalog, slot);

        screenshots.mkdirs();
        Properties runtimeCatalog = new Properties(); runtimeCatalog.putAll(catalog);
        for (int slot = 0; slot < slots; slot++) {
            runtimeCatalog.setProperty("slot." + slot + ".entity.list.field", entityListField(entries, catalog, slot));
            runtimeCatalog.setProperty("slot." + slot + ".notice.text.fields", noticeTextFields(entries, catalog, slot));
            runtimeCatalog.setProperty("slot." + slot + ".notice.scroll.fields", noticeScrollFields(entries, catalog, slot));
            runtimeCatalog.setProperty("slot." + slot + ".notice.center.field", noticeCenterField(entries, catalog, slot));
            runtimeCatalog.setProperty("slot." + slot + ".notice.layout.spec", noticeLayoutSpec(entries, catalog, slot));
        }
        File workerCatalog = new File(screenshots, "runtime-catalog.properties");
        java.io.FileOutputStream runtimeOut = new java.io.FileOutputStream(workerCatalog);
        try { runtimeCatalog.store(runtimeOut, "derived native entity registry fields"); }
        finally { runtimeOut.close(); }
        runEmulatorWorker(jarFile, workerCatalog, micro, screenshots);
        System.out.println("HungBySonExpBinaryTest PASS checks=" + checks
                + "; linked=" + classCount(entries) + "; slots=" + slots
                + "; native packet30 hook, reset/RMS/namespace and HUD+notice screenshots");
    }

    /** MicroEmulator 2.x bundles ASM 3; isolate it from the host ASM 9 verifier. */
    private static void runEmulatorWorker(File jar, File catalog, File micro, File screenshots) throws Exception {
        File host = new File(HungBySonExpBinaryTest.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        File runtimeHome = new File(screenshots, "runtime-home"); runtimeHome.mkdirs();
        File java = new File(new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        ArrayList<String> command = new ArrayList<String>();
        command.add(java.getAbsolutePath()); command.add("-Xverify:all");
        command.add("-Djava.awt.headless=true"); command.add("-Duser.home=" + runtimeHome.getAbsolutePath());
        command.add("-Dsun.reflect.inflationThreshold=10000");
        command.add("-cp"); command.add(host.getAbsolutePath() + File.pathSeparator + micro.getAbsolutePath());
        command.add("HungBySonExpBinaryTest$EmulatorWorker");
        command.add(jar.getAbsolutePath()); command.add(catalog.getAbsolutePath());
        command.add(micro.getAbsolutePath()); command.add(screenshots.getAbsolutePath());
        Process process = new ProcessBuilder(command).inheritIO().start();
        int exit = process.waitFor(); require(exit == 0, "MicroEmulator HUD worker exit=" + exit);
        runPersistentRmsPair(jar, catalog, micro, screenshots);
    }

    private static void runPersistentRmsPair(File jar, File catalog, File micro, File screenshots) throws Exception {
        File host = new File(HungBySonExpBinaryTest.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        File runtimeHome = new File(screenshots, "persistent-rms-home"); runtimeHome.mkdirs();
        File state = new File(screenshots, "persistent-rms-proof.properties");
        String scope = "3".equals(load(catalog).getProperty("slots")) ? "x3" : "single";
        File java = new File(new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        for (String mode : new String[]{"write", "read"}) {
            ArrayList<String> command = new ArrayList<String>();
            command.add(java.getAbsolutePath()); command.add("-Xverify:all");
            command.add("-Djava.awt.headless=true"); command.add("-Duser.home=" + runtimeHome.getAbsolutePath());
            command.add("-Dsun.reflect.inflationThreshold=10000");
            command.add("-cp"); command.add(host.getAbsolutePath() + File.pathSeparator + micro.getAbsolutePath());
            command.add("HungBySonExpBinaryTest$PersistentRmsWorker");
            command.add(jar.getAbsolutePath()); command.add(catalog.getAbsolutePath());
            command.add(micro.getAbsolutePath()); command.add(mode); command.add(state.getAbsolutePath()); command.add(scope);
            Process process = new ProcessBuilder(command).inheritIO().start();
            int exit = process.waitFor(); require(exit == 0, "Fresh-process File RMS " + mode + " worker exit=" + exit);
        }
    }

    /** Runs with only MicroEmulator's bundled ASM on the classpath. */
    public static final class EmulatorWorker {
        public static void main(String[] args) {
            int exit = 0;
            try { run(args); }
            catch (Throwable failure) { failure.printStackTrace(); exit = 1; }
            System.exit(exit);
        }

        private static void run(String[] args) throws Exception {
            if (args.length != 4) throw new IllegalArgumentException("jar catalog micro screenshots");
            File jar = new File(args[0]); File screenshots = new File(args[3]);
            Properties p = load(new File(args[1]));
            int slots = Integer.parseInt(required(p, "slots"));
            Common emulator = startEmulator(jar);
            MIDlet active = MIDletBridge.getCurrentMIDlet();
            require(active != null, "MicroEmulator started the candidate MIDlet");
            ClassLoader loader = active.getClass().getClassLoader();
            if (slots == 3) verifyX3Screenshots(emulator, active, screenshots);
            else snapshotDisplay(emulator, new File(screenshots, "single-login.png"));
            long[] learnedLow = new long[slots];
            long[] learnedHigh = new long[slots];
            for (int slot = 0; slot < slots; slot++) {
                verifyNativeCleanup(loader, p, slots, slot);
                verifyBridgeLifecycle(loader, p, slot);
                verifyPacketRuntime(loader, p, slot, jar, learnedLow, learnedHigh);
                renderHud(loader, p, slot, screenshots);
            }
            verifyClcdStringBuilderBypass(loader, jar);
            System.out.println("MicroEmulator native HUD rendering PASS slots=" + slots);
            System.exit(0);
        }
    }

    private static void verifyClcdStringBuilderBypass(ClassLoader parent, File jar) throws Exception {
        byte[] legacy = jarEntry(jar, "luna/generated/InvestigateStorage.class");
        byte[] shared = jarEntry(jar, "luna/generated/ExpEstimateSharedRmsStore.class");
        int replacements = replaceUtf8(legacy, "java/lang/StringBuilder", "fake/lang/StringBuilder");
        require(replacements > 0, "Legacy InvestigateStorage fixture contains the CLDC-incompatible concat reference");
        ChildByteLoader loader = new ChildByteLoader(parent);
        loader.define("luna.generated.InvestigateStorage", legacy);
        loader.define("luna.generated.ExpEstimateSharedRmsStore", shared);
        boolean oldPathFailed = false;
        try {
            Class<?> legacyType = loader.loadClass("luna.generated.InvestigateStorage");
            Method open = legacyType.getMethod("openRecordStore", String.class, Boolean.TYPE);
            open.invoke(null, "cldc_stringbuilder_probe", Boolean.TRUE);
        }
        catch (java.lang.reflect.InvocationTargetException expected) {
            Throwable cause = expected.getCause();
            oldPathFailed = cause instanceof NoClassDefFoundError
                    && cause.getMessage() != null && (cause.getMessage().indexOf("fake/lang/StringBuilder") >= 0
                        || cause.getMessage().indexOf("fake.lang.StringBuilder") >= 0);
        } catch (NoClassDefFoundError expected) {
            oldPathFailed = expected.getMessage() != null && (expected.getMessage().indexOf("fake/lang/StringBuilder") >= 0
                    || expected.getMessage().indexOf("fake.lang.StringBuilder") >= 0);
        }
        require(oldPathFailed, "CLDC-shaped runtime reproduces legacy RMS failure when StringBuilder is absent");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x45585031); out.writeByte(2); out.writeByte(1); out.writeInt(7777);
        out.writeLong(765432100L); out.writeLong(765432100L); out.writeShort(20); out.writeShort(30); out.flush();
        Class<?> sharedType = loader.loadClass("luna.generated.ExpEstimateSharedRmsStore");
        require(((Boolean)sharedType.getMethod("save", byte[].class).invoke(null, new Object[]{bytes.toByteArray()})).booleanValue(),
                "Direct MIDP shared-store save works while legacy StringBuilder linkage is broken");
        byte[] loaded = (byte[])sharedType.getMethod("load").invoke(null);
        require(containsLevel(loaded, 7777),
                "Direct MIDP shared-store read returns the CLDC-shaped runtime's saved level");
        check(true, "CLDC-shaped old-path failure and new-backend bypass reproduced");
    }

    private static byte[] jarEntry(File jar, String name) throws Exception {
        JarFile file = new JarFile(jar);
        try {
            JarEntry entry = file.getJarEntry(name);
            require(entry != null, "Candidate contains " + name);
            InputStream in = file.getInputStream(entry);
            try { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[2048];
                for (int n; (n = in.read(buffer)) >= 0;) out.write(buffer, 0, n); return out.toByteArray(); }
            finally { in.close(); }
        } finally { file.close(); }
    }

    private static int replaceUtf8(byte[] data, String from, String to) throws Exception {
        byte[] oldBytes = from.getBytes("UTF-8"), newBytes = to.getBytes("UTF-8");
        if (oldBytes.length != newBytes.length) throw new IllegalArgumentException("replacement must be length preserving");
        int count = 0;
        for (int i = 0; i <= data.length - oldBytes.length; i++) {
            boolean match = true;
            for (int j = 0; j < oldBytes.length; j++) if (data[i + j] != oldBytes[j]) { match = false; break; }
            if (!match) continue;
            System.arraycopy(newBytes, 0, data, i, newBytes.length); count++; i += oldBytes.length - 1;
        }
        return count;
    }

    private static boolean containsLevel(byte[] encoded, int target) throws Exception {
        if (encoded == null || encoded.length < 6 || readInt(encoded, 0) != 0x45585031 || (encoded[4] & 255) != 2) return false;
        java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(encoded));
        in.readInt(); in.readUnsignedByte(); int count = in.readUnsignedByte();
        for (int i = 0; i < count; i++) {
            int level = in.readInt(); in.readLong(); in.readLong(); in.readUnsignedShort(); in.readUnsignedShort();
            if (level == target) return true;
        }
        return false;
    }

    private static final class ChildByteLoader extends ClassLoader {
        private final Map<String, byte[]> definitions = new LinkedHashMap<String, byte[]>();
        ChildByteLoader(ClassLoader parent) { super(parent); }
        void define(String name, byte[] bytes) { definitions.put(name, bytes); }
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null && definitions.containsKey(name)) {
                byte[] bytes = definitions.remove(name); loaded = defineClass(name, bytes, 0, bytes.length);
            }
            if (loaded == null) loaded = super.loadClass(name, false);
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    /** Two separate JVMs prove the level table survives a real MicroEmulator File RMS reopen. */
    public static final class PersistentRmsWorker {
        public static void main(String[] args) {
            int exit = 0;
            try { run(args); }
            catch (Throwable failure) { failure.printStackTrace(); exit = 1; }
            System.exit(exit);
        }

        private static void run(String[] args) throws Exception {
            if (args.length != 6) throw new IllegalArgumentException("jar catalog micro mode state");
            File jar = new File(args[0]); Properties p = load(new File(args[1]));
            String mode = args[3]; File stateFile = new File(args[4]);
            // args[5] is reserved for future cache dimensions; accept only explicit test scope.
            if (!"single".equals(args[5]) && !"x3".equals(args[5])) throw new IllegalArgumentException("scope");
            int slots = Integer.parseInt(required(p, "slots"));
            Headless headless = new Headless();
            Field emulatorField = Headless.class.getDeclaredField("emulator"); emulatorField.setAccessible(true);
            Common emulator = (Common)emulatorField.get(headless);
            FileRecordStoreManager disk = new FileRecordStoreManager();
            disk.init(emulator); emulator.setRecordStoreManager(disk); MIDletBridge.setMicroEmulator(emulator);
            ArrayList<String> options = new ArrayList<String>(); options.add("--rms"); options.add("file"); options.add(jar.getAbsolutePath());
            DeviceEntry device = new DeviceEntry("Default device", null,
                    "org/microemu/device/default/device.xml", true, false);
            require(emulator.initParams(options, device, J2SEDevice.class), "Persistent MicroEmulator initialization");
            emulator.initMIDlet(true); Thread.sleep(600);
            MIDlet active = MIDletBridge.getCurrentMIDlet(); require(active != null, "Persistent RMS MIDlet started");
            ClassLoader loader = active.getClass().getClassLoader();
            int slot = mode.equals("read") && slots == 3 ? 1 : 0;
            String slotPrefix = slotPrefix(p, slot);
            String supportName = (slotPrefix + "luna.features.ExpEstimateSupport").replace('/', '.');
            String apiName = (slotPrefix + "luna.api.ExpEstimateStore").replace('/', '.');
            String storeName = (slotPrefix + "luna.generated.ExpEstimateRmsStore").replace('/', '.');
            Class<?> support = Class.forName(supportName, true, loader);
            Class<?> api = Class.forName(apiName, true, loader);
            Object store = Class.forName(storeName, true, loader).getDeclaredConstructor().newInstance();
            support.getMethod("setStore", api).invoke(null, store);
            Method snapshot = support.getMethod("snapshot", String.class, Integer.TYPE, Integer.TYPE, Integer.TYPE);
            Method gain = support.getMethod("gain", String.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
            Method disconnect = support.getMethod("disconnect");
            Method low = support.getMethod("estimateLow"), high = support.getMethod("estimateHigh");
            Method ready = support.getMethod("isReady"), current = support.getMethod("currentSessionTotal");
            String server = mode.equals("write") ? "disk-writer-server" : "disk-reader-different-server";
            int id = mode.equals("write") ? 8800 : 9900;
            if (mode.equals("write")) {
                Properties state = stateProperties(stateFile);
                for (int level = 80; level <= 81; level++) {
                    snapshot.invoke(null, server, Integer.valueOf(id), Integer.valueOf(level), Integer.valueOf(300));
                    int each = level == 80 ? 100000 : 120000;
                    for (int i = 1; i <= 10; i++) gain.invoke(null, server, Integer.valueOf(id),
                            Integer.valueOf(level), Integer.valueOf(300 + i), Integer.valueOf(each));
                    require(((Boolean)ready.invoke(null)).booleanValue(), "File RMS writer learns level " + level);
                    state.setProperty("level." + level + ".low", String.valueOf(low.invoke(null)));
                    state.setProperty("level." + level + ".high", String.valueOf(high.invoke(null)));
                    disconnect.invoke(null);
                }
                writeState(stateFile, state);
            } else {
                Properties expected = load(stateFile);
                for (int level = 80; level <= 81; level++) {
                    snapshot.invoke(null, server, Integer.valueOf(id + level), Integer.valueOf(level), Integer.valueOf(300));
                    require(((Boolean)ready.invoke(null)).booleanValue(),
                            "Fresh JVM and different identity reload shared level " + level);
                    require(((Long)low.invoke(null)).longValue() == Long.parseLong(expected.getProperty("level." + level + ".low"))
                                    && ((Long)high.invoke(null)).longValue() == Long.parseLong(expected.getProperty("level." + level + ".high")),
                            "File RMS retained exact bounds for level " + level + " after unrelated-level merge");
                    require(((Long)current.invoke(null)).longValue() == 0L,
                            "Disk reloaded estimate does not restore offline EXP samples");
                    require(((String)support.getMethod("line", Integer.TYPE).invoke(null, Integer.valueOf(300))).startsWith("("),
                            "Fresh process exposes the restored estimate line");
                }
            }
            String[] names = RecordStore.listRecordStores();
            require(contains(names, "hbs_exp_levels_v2"), "File RMS contains the single shared v2 table");
            require(!contains(names, "inv_exp_approx_v1"), "New writes no longer depend on the legacy namespaced store");
            System.out.println("Persistent File RMS " + mode + " PASS slots=" + slots + " activeSlot=" + slot);
            // --rms argument order was jar plus three parameters; the final scope argument is deliberate.
            System.exit(0);
        }
    }

    private static void verifyLinkage(Map<String, byte[]> entries, int slots, File jarFile, Properties catalog) throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[]{jarFile.toURI().toURL()},
                HungBySonExpBinaryTest.class.getClassLoader());
        try {
            for (String path : entries.keySet()) {
                if (!path.endsWith(".class")) continue;
                String name = path.substring(0, path.length() - 6).replace('/', '.');
                Class<?> c = Class.forName(name, false, loader);
                c.getDeclaredFields(); c.getDeclaredMethods(); c.getDeclaredConstructors();
            }
        } finally { loader.close(); }
        if (slots == 3) {
            for (String p : new String[]{"", "b/", "c/"}) {
                require(entries.containsKey(p + "TemMidlet.class"), "Native X3 MIDlet namespace " + p);
                require(entries.containsKey(p + "luna/generated/ExpEstimateBridge.class"), "EXP bridge namespace " + p);
                require(entries.containsKey(p + "luna/features/ExpEstimateSupport.class"), "EXP support namespace " + p);
            }
        }
        verifySharedStoreBytecode(entries, slots, catalog);
        check(true, "All candidate classes link and expose members");
    }

    private static void verifyNativeHooks(Map<String, byte[]> entries, Properties p, int slot) {
        String prefix = "slot." + slot + ".";
        String hudOwner = required(p, prefix + "hud.owner");
        String hudMethod = required(p, prefix + "hud.method");
        String hudDesc = required(p, prefix + "hud.descriptor");
        String receiver = required(p, prefix + "receiver.owner");
        String xpMethod = required(p, prefix + "xp.method");
        String xpDesc = required(p, prefix + "xp.descriptor");
        String bridge = required(p, prefix + "bridge.owner");
        ClassNode hud = node(entries.get(hudOwner + ".class"));
        MethodNode paint = method(hud, hudMethod, hudDesc);
        int frameCalls = 0, estimates = 0, bottomCalls = 0, nativeLv = 0;
        for (AbstractInsnNode n : paint.instructions.toArray()) {
            if (n instanceof org.objectweb.asm.tree.LdcInsnNode
                    && "Lv.".equals(((org.objectweb.asm.tree.LdcInsnNode)n).cst)) nativeLv++;
            if (n instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)n;
                if (call.owner.equals(bridge) && call.name.equals("framePart")) frameCalls++;
                if (call.owner.equals(bridge) && call.name.equals("drawEstimate")) estimates++;
                if (call.owner.equals(bridge) && call.name.equals("frameBottom")) bottomCalls++;
            }
        }
        nativeLv = countLdc(paint, "Lv.");
        require(nativeLv == 1, "Native level line remains in the HUD");
        require(frameCalls >= 3 && bottomCalls == 0, "Full native frame remains routed through stock-pixel stretching");
        require(estimates == 1, "One estimate line is drawn by the native font renderer");
        if ("3".equals(p.getProperty("percent.decimals"))) {
            int percentCalls = 0;
            for (AbstractInsnNode n : paint.instructions.toArray()) if (n instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)n;
                if (call.owner.equals(slotPrefix(p, slot) + "luna/features/ExpEstimateSupport")
                        && call.name.equals("percentage") && call.desc.equals("(I)Ljava/lang/String;")) percentCalls++;
            }
            require(percentCalls == 1, "Native level line uses the fine percentage exactly once");
        }
        require(containsInt(paint, 96) && containsInt(paint, 4),
                "Native HUD restores the original 96px frame and four center tiles");
        require("59".equals(p.getProperty(prefix + "lower.icon.y")), "Mail/count icon stays below the 56px panel");
        verifyCleanupHook(entries, p, slot, bridge);
        ClassNode bridgeNode = node(entries.get(bridge + ".class"));
        MethodNode frame = namedMethod(bridgeNode, "framePart");
        require(frame != null && containsInt(frame, 14), "Native stock frame tiles extend the body by 14 rows");
        String estimateFontField = required(p, prefix + "estimate.font.field");
        String estimateFontResource = required(p, prefix + "estimate.font.resource");
        require("/mfont/tahoma_7_white.png".equals(estimateFontResource),
                "EXP uses the existing small white bitmap font with number and unit glyphs");
        MethodNode drawEstimate = namedMethod(bridgeNode, "drawEstimate");
        int estimateFontReads = 0;
        for (AbstractInsnNode n : drawEstimate.instructions.toArray()) if (n instanceof FieldInsnNode) {
            FieldInsnNode f = (FieldInsnNode)n;
            if (f.getOpcode() == GETSTATIC && f.owner.equals(required(p, prefix + "font.owner"))
                    && f.name.equals(estimateFontField)
                    && f.desc.equals("L" + required(p, prefix + "font.owner") + ";")) estimateFontReads++;
        }
        require(estimateFontReads == 1,
                "EXP-only renderer reads the cataloged Tahoma 7 white font; native HUD font stays unchanged");
        MethodNode bridgeLine = namedMethod(bridgeNode, "line");
        String supportOwner = (slotPrefix(p, slot) + "luna/features/ExpEstimateSupport");
        int ensureSession = -1, cachedLine = -1;
        for (int i = 0; i < bridgeLine.instructions.size(); i++) {
            AbstractInsnNode n = bridgeLine.instructions.get(i);
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)n;
            if (call.owner.equals(supportOwner) && "ensureSession".equals(call.name)
                    && "(Ljava/lang/String;III)V".equals(call.desc)) ensureSession = i;
            if (call.owner.equals(supportOwner) && "line".equals(call.name)
                    && "(I)Ljava/lang/String;".equals(call.desc)) cachedLine = i;
        }
        require(ensureSession >= 0 && cachedLine > ensureSession,
                "Native HUD bridge reconciles live identity before reading the cached line");

        String noticeName = slotOrGlobal(p, slot, "notice.method");
        String noticeDesc = slotOrGlobal(p, slot, "notice.descriptor");
        String baseYField = slotOrGlobal(p, slot, "notice.base.field");
        MethodNode noticePaint = method(hud, noticeName, noticeDesc);
        int noticeBridgeCalls = 0, noticeFontCalls = 0, nativeBaseReads = 0;
        for (AbstractInsnNode n : noticePaint.instructions.toArray()) if (n instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)n;
            if (call.owner.equals(bridge)) noticeBridgeCalls++;
            if (call.owner.equals(required(p, prefix + "font.owner"))
                    && call.name.equals(required(p, prefix + "font.draw.method"))
                    && call.desc.equals(required(p, prefix + "font.draw.descriptor"))) noticeFontCalls++;
        }
        for (AbstractInsnNode n : noticePaint.instructions.toArray()) if (n instanceof FieldInsnNode) {
            FieldInsnNode field = (FieldInsnNode)n;
            if (field.getOpcode() == GETSTATIC && field.owner.equals(hudOwner)
                    && field.name.equals(baseYField) && "I".equals(field.desc)) nativeBaseReads++;
        }
        require(noticeBridgeCalls == 0 && nativeBaseReads > 0,
                "Native notice renderer reads its original baseY directly with no bridge layout hook");
        require(noticeFontCalls == 3 && noticeTextFields(entries, p, slot).split(",").length == 3,
                "Native notice renderer retains its original three strings and native font draws");
        require("native".equals(slotOrGlobal(p, slot, "notice.position")),
                "Native notices retain their original screen position");
        require("true".equals(slotOrGlobal(p, slot, "notice.native.unchanged")),
                "Patcher inverse check confirms byte-identical native notice renderer");
        require("0".equals(slotOrGlobal(p, slot, "notice.hooks")),
                "No notice baseline hook is installed");

        ClassNode packetOwner = node(entries.get(receiver + ".class"));
        MethodNode packet = method(packetOwner, xpMethod, xpDesc);
        int gainCall = -1, popupText = -1, popupGate = -1;
        AbstractInsnNode[] code = packet.instructions.toArray();
        for (int i = 0; i < code.length; i++) {
            AbstractInsnNode n = code[i];
            if (n instanceof MethodInsnNode) {
                MethodInsnNode m = (MethodInsnNode)n;
                if (m.owner.equals(bridge) && m.name.equals("gain")
                        && m.desc.equals("(Ljava/lang/Object;I)V")) gainCall = i;
            }
            if (n instanceof org.objectweb.asm.tree.LdcInsnNode
                    && "xp".equals(((org.objectweb.asm.tree.LdcInsnNode)n).cst)) popupText = i;
        }
        if (gainCall >= 0 && popupText > gainCall) for (int i = gainCall + 1; i < popupText; i++) {
            if (code[i] instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)code[i];
                if (field.getOpcode() == GETSTATIC && "Z".equals(field.desc)) { popupGate = i; break; }
            }
        }
        require(gainCall >= 0, "Real native packet30 parser invokes the estimator bridge");
        require(popupText < 0 || (gainCall < popupGate && popupGate < popupText),
                "EXP is sampled before the native popup visibility gate and formatting");
        require(required(p, prefix + "xp.descriptor").startsWith("(L"), "Packet30 handler has native packet ABI");
        require(required(p, prefix + "snapshot.descriptor").startsWith("(L"), "Level/session snapshot has native packet ABI");
        verifySnapshotIdentityHook(entries, p, slot, bridge);
        check(true, "Native HUD and packet hooks verified for slot " + slot);
    }

    private static void verifySharedStoreBytecode(Map<String, byte[]> entries, int slots, Properties catalog) {
        require("level".equals(required(catalog, "exp.cache.key")),
                "EXP result cache is keyed only by level");
        require("2".equals(required(catalog, "exp.cache.version"))
                && "true".equals(required(catalog, "exp.cache.shared.slots"))
                && "hbs_exp_levels_v2".equals(required(catalog, "exp.cache.store")),
                "Single v2 RMS table is shared by every slot");
        String common = "luna/generated/ExpEstimateSharedRmsStore";
        byte[] bytes = entries.get(common + ".class");
        require(bytes != null, "Suite-wide shared RMS backend is present once in the root namespace");
        ClassNode node = node(bytes);
        boolean directMidp = false, wrapperReference = false, stringBuilder = false;
        for (MethodNode m : node.methods) for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)insn;
                if ("javax/microedition/rms/RecordStore".equals(call.owner)) directMidp = true;
                if (call.owner.endsWith("InvestigateStorage")) wrapperReference = true;
            }
            if (insn instanceof org.objectweb.asm.tree.TypeInsnNode) {
                String type = ((org.objectweb.asm.tree.TypeInsnNode)insn).desc;
                if ("java/lang/StringBuilder".equals(type)) stringBuilder = true;
                if (type.endsWith("InvestigateStorage")) wrapperReference = true;
            }
        }
        require(directMidp && !wrapperReference && !stringBuilder,
                "Shared RMS bypasses the legacy StringBuilder-based storage wrapper");
        if (slots == 3) {
            require(!entries.containsKey("b/" + common + ".class")
                    && !entries.containsKey("c/" + common + ".class"),
                    "X3 contains one shared backend, not three divergent copies");
        }
    }

    private static void verifyCleanupHook(Map<String, byte[]> entries, Properties p, int slot, String bridge) {
        String prefix = "slot." + slot + ".";
        String owner = required(p, prefix + "cleanup.owner");
        String name = required(p, prefix + "cleanup.method");
        String desc = required(p, prefix + "cleanup.descriptor");
        require("true".equals(required(p, prefix + "cleanup.exp.preserved")),
                "Native cache cleanup must retain the EXP migration record");
        MethodNode cleanup = method(node(entries.get(owner + ".class")), name, desc);
        AbstractInsnNode first = cleanup.instructions.getFirst();
        while (first != null && first.getOpcode() < 0) first = first.getNext();
        require(first instanceof MethodInsnNode
                && ((MethodInsnNode)first).owner.equals(bridge)
                && "prepareStore".equals(((MethodInsnNode)first).name)
                && "()V".equals(((MethodInsnNode)first).desc),
                "Native cleanup imports legacy estimates before scanning stores");
        String expSuffix = "luna/generated/ExpEstimateBridge";
        require(bridge.endsWith(expSuffix), "Catalog EXP bridge belongs to the slot namespace");
        String attackBridge = bridge.substring(0, bridge.length() - expSuffix.length())
                + "luna/generated/HungAutoAttackAccess";
        int routedDelete = 0, attackDelete = 0, rawDelete = 0;
        for (AbstractInsnNode n : cleanup.instructions.toArray()) if (n instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)n;
            if (call.owner.equals(bridge) && "deleteSessionRecord".equals(call.name)
                    && "(Ljava/lang/String;)V".equals(call.desc)) routedDelete++;
            if (attackBridge.equals(call.owner)
                    && "deleteSessionRecord".equals(call.name)
                    && "(Ljava/lang/String;)V".equals(call.desc)) attackDelete++;
            if ("deleteRecordStore".equals(call.name)) rawDelete++;
        }
        if (entries.containsKey(attackBridge + ".class")) {
            require(routedDelete == 0 && attackDelete == 1 && rawDelete == 0,
                    "Native cleanup routes exactly one deletion through this slot's Auto Attack Farm-preserving wrapper");
            verifyAutoAttackFarmCleanupWrapper(entries, p, bridge, attackBridge);
        } else {
            require(routedDelete > 0 && attackDelete == 0 && rawDelete == 0,
                    "Native cleanup routes deletions through the historical EXP-preserving guard");
        }
    }

    private static void verifyAutoAttackFarmCleanupWrapper(Map<String, byte[]> entries,
            Properties p, String expBridge, String owner) {
        String configName = p.getProperty("rms.farm.config.logical", "sys_auto_cfg");
        String positionName = p.getProperty("rms.farm.position.logical", "sys_farm_loc");
        require("sys_auto_cfg".equals(configName) && "sys_farm_loc".equals(positionName),
                "Auto Attack cleanup catalog identifies both native Farm RMS records");

        MethodNode wrapper = method(node(entries.get(owner + ".class")), "deleteSessionRecord",
                "(Ljava/lang/String;)V");
        ArrayList<AbstractInsnNode> code = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction : wrapper.instructions.toArray())
            if (instruction.getOpcode() >= 0) code.add(instruction);

        require(Modifier.isStatic(wrapper.access) && wrapper.instructions.size() > 0,
                "Auto Attack cleanup wrapper is the generated static one-argument adapter");
        require(code.size() == 11
                        && code.get(0) instanceof org.objectweb.asm.tree.LdcInsnNode
                        && configName.equals(((org.objectweb.asm.tree.LdcInsnNode)code.get(0)).cst)
                        && code.get(1).getOpcode() == ALOAD
                        && ((org.objectweb.asm.tree.VarInsnNode)code.get(1)).var == 0
                        && code.get(2) instanceof MethodInsnNode
                        && ((MethodInsnNode)code.get(2)).getOpcode() == INVOKEVIRTUAL
                        && "java/lang/String".equals(((MethodInsnNode)code.get(2)).owner)
                        && "equals".equals(((MethodInsnNode)code.get(2)).name)
                        && "(Ljava/lang/Object;)Z".equals(((MethodInsnNode)code.get(2)).desc)
                        && code.get(3) instanceof org.objectweb.asm.tree.JumpInsnNode
                        && code.get(3).getOpcode() == IFNE
                        && code.get(4) instanceof org.objectweb.asm.tree.LdcInsnNode
                        && positionName.equals(((org.objectweb.asm.tree.LdcInsnNode)code.get(4)).cst)
                        && code.get(5).getOpcode() == ALOAD
                        && ((org.objectweb.asm.tree.VarInsnNode)code.get(5)).var == 0
                        && code.get(6) instanceof MethodInsnNode
                        && ((MethodInsnNode)code.get(6)).getOpcode() == INVOKEVIRTUAL
                        && "java/lang/String".equals(((MethodInsnNode)code.get(6)).owner)
                        && "equals".equals(((MethodInsnNode)code.get(6)).name)
                        && "(Ljava/lang/Object;)Z".equals(((MethodInsnNode)code.get(6)).desc)
                        && code.get(7) instanceof org.objectweb.asm.tree.JumpInsnNode
                        && code.get(7).getOpcode() == IFNE,
                "Auto Attack wrapper has exactly the two catalogued Farm-name equality guards");
        org.objectweb.asm.tree.JumpInsnNode configGuard = (org.objectweb.asm.tree.JumpInsnNode)code.get(3);
        org.objectweb.asm.tree.JumpInsnNode positionGuard = (org.objectweb.asm.tree.JumpInsnNode)code.get(7);
        require(configGuard.label == positionGuard.label
                        && code.get(10) instanceof org.objectweb.asm.tree.InsnNode
                        && code.get(10).getOpcode() == RETURN
                        && configGuard.label.getNext() == code.get(10),
                "Both protected Farm names converge directly on the wrapper's return");
        require(code.get(8).getOpcode() == ALOAD
                        && ((org.objectweb.asm.tree.VarInsnNode)code.get(8)).var == 0
                        && code.get(9) instanceof MethodInsnNode
                        && ((MethodInsnNode)code.get(9)).getOpcode() == INVOKESTATIC
                        && expBridge.equals(((MethodInsnNode)code.get(9)).owner)
                        && "deleteSessionRecord".equals(((MethodInsnNode)code.get(9)).name)
                        && "(Ljava/lang/String;)V".equals(((MethodInsnNode)code.get(9)).desc),
                "Every unprotected session record delegates to the original EXP cleanup method");

        int expDelegations = 0, rawDelete = 0;
        for (AbstractInsnNode instruction : wrapper.instructions.toArray())
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (expBridge.equals(call.owner) && "deleteSessionRecord".equals(call.name)
                        && "(Ljava/lang/String;)V".equals(call.desc)) expDelegations++;
                if ("deleteRecordStore".equals(call.name)) rawDelete++;
            }
        require(expDelegations == 1 && rawDelete == 0,
                "Farm guard preserves the original EXP deletion fallback without raw RMS deletion");
    }

    private static void verifySnapshotIdentityHook(Map<String, byte[]> entries, Properties p,
            int slot, String bridge) {
        String prefix = "slot." + slot + ".";
        String receiver = required(p, prefix + "receiver.owner");
        MethodNode snapshot = method(node(entries.get(receiver + ".class")),
                required(p, prefix + "snapshot.method"), required(p, prefix + "snapshot.descriptor"));
        String entity = required(p, prefix + "entity.owner");
        String idField = p.getProperty(prefix + "snapshot.trigger.field", required(p, prefix + "id.field"));
        String idDesc = p.getProperty(prefix + "snapshot.trigger.descriptor", "I");
        String progressField = required(p, prefix + "progress.field");
        String levelField = required(p, prefix + "level.field");
        int levelWrite = -1, progressWrite = -1, stringWrite = -1, idWrite = -1, observer = -1;
        AbstractInsnNode[] code = snapshot.instructions.toArray();
        for (int i = 0; i < code.length; i++) {
            AbstractInsnNode n = code[i];
            if (n instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode)n;
                if (f.getOpcode() != PUTFIELD || !f.owner.equals(entity)) continue;
                if (f.name.equals(levelField) && "S".equals(f.desc)) levelWrite = i;
                if (f.name.equals(progressField) && "S".equals(f.desc)) progressWrite = i;
                if (f.name.equals(idField) && idDesc.equals(f.desc)) idWrite = i;
                if ("Ljava/lang/String;".equals(f.desc)) stringWrite = i;
            } else if (n instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)n;
                if (call.owner.equals(bridge) && "snapshotObject".equals(call.name)
                        && "(Ljava/lang/Object;)V".equals(call.desc)) observer = i;
            }
        }
        require(levelWrite >= 0 && progressWrite > levelWrite && stringWrite > progressWrite
                        && idWrite > stringWrite && observer > idWrite,
                "Snapshot hook runs after progress, name, and final cu:I character identity are decoded");
        int gap = 0;
        for (int i = idWrite + 1; i < observer; i++) if (code[i].getOpcode() >= 0) gap++;
        require(gap <= 2, "Identity observer runs directly after the native cu:I write");
    }

    private static int countLdc(MethodNode method, String text) {
        int n = 0;
        for (AbstractInsnNode i : method.instructions.toArray())
            if (i instanceof org.objectweb.asm.tree.LdcInsnNode
                    && text.equals(((org.objectweb.asm.tree.LdcInsnNode)i).cst)) n++;
        return n;
    }

    private static boolean containsInt(MethodNode m, int value) {
        for (AbstractInsnNode i : m.instructions.toArray()) {
            if (i.getOpcode() >= Opcodes.ICONST_M1 && i.getOpcode() <= Opcodes.ICONST_5
                    && i.getOpcode() - Opcodes.ICONST_0 == value) return true;
            if (i instanceof org.objectweb.asm.tree.IntInsnNode
                    && ((org.objectweb.asm.tree.IntInsnNode)i).operand == value) return true;
        }
        return false;
    }

    private static void verifyBridgeLifecycle(ClassLoader loader, Properties p, int slot) throws Exception {
        String prefix = "slot." + slot + ".";
        String bridgeName = dotted(required(p, prefix + "bridge.owner"));
        String supportName = (slotPrefix(p, slot) + "luna.features.ExpEstimateSupport").replace('/', '.');
        Class<?> bridge = Class.forName(bridgeName, true, loader);
        Class<?> support = Class.forName(supportName, true, loader);
        Method line = bridge.getMethod("line");
        Method disconnect = bridge.getMethod("disconnect");
        require(p.getProperty("calculating.text", "Caculating...").equals(line.invoke(null)),
                "Unknown native estimate uses the configured calculating text");
        Method currentTotal = support.getMethod("currentSessionTotal");
        require(((Long)currentTotal.invoke(null)).longValue() == 0L, "New slot has no prior live EXP");

        // Exercise the deployed native bridge against the slot's real RMS adapter.
        Field storeField = support.getDeclaredField("store"); storeField.setAccessible(true);
        Object rmsStore = storeField.get(null);
        require(rmsStore != null, "Bridge installs the native RMS adapter");
        Method setStore = support.getMethod("setStore", Class.forName(
                (slotPrefix(p, slot) + "luna.api.ExpEstimateStore").replace('/', '.'), true, loader));
        setStore.invoke(null, rmsStore);
        disconnect.invoke(null);
        check(true, "Bridge lifecycle and RMS adapter available for slot " + slot);
    }

    private static boolean cleanupRmsSeeded;
    private static void verifyNativeCleanup(ClassLoader loader, Properties p, int slots, int slot) throws Exception {
        if (!cleanupRmsSeeded) {
            String[] legacy = slots == 3
                    ? new String[]{"inv_exp_approx_v1", "ainv_exp_approx_v1", "binv_exp_approx_v1", "cinv_exp_approx_v1"}
                    : new String[]{"inv_exp_approx_v1"};
            for (int i = 0; i < legacy.length; i++) {
                byte[] v1 = estimateRecord("legacy-migration-proof", 7700 + i, 901 + i,
                        500000000L + i, 500000000L + i, 20, 30);
                putRmsRecord(legacy[i], v1);
            }
            cleanupRmsSeeded = true;
        }
        String physicalPrefix = slots == 3 ? new String[]{"ainv_", "binv_", "cinv_"}[slot] : "inv_";
        String probeName = physicalPrefix + "fixture_cleanup_probe";
        putRmsRecord(probeName, new byte[]{1});

        String prefix = "slot." + slot + ".";
        Class<?> owner = Class.forName(dotted(required(p, prefix + "cleanup.owner")), true, loader);
        Method cleanup = owner.getDeclaredMethod(required(p, prefix + "cleanup.method"), new Class<?>[0]);
        cleanup.setAccessible(true);
        Object receiver = Modifier.isStatic(cleanup.getModifiers()) ? null : constructOrAllocate(owner);
        cleanup.invoke(receiver);

        String[] stores = RecordStore.listRecordStores();
        require(contains(stores, probeName) == false,
                "Native session cleanup still deletes unrelated slot-local cache " + probeName);
        String[] legacyNames = slots == 3
                ? new String[]{"inv_exp_approx_v1", "ainv_exp_approx_v1", "binv_exp_approx_v1", "cinv_exp_approx_v1"}
                : new String[]{"inv_exp_approx_v1"};
        for (String name : legacyNames) require(contains(RecordStore.listRecordStores(), name),
                "Native session cleanup preserves legacy EXP RMS store " + name);

        Class<?> shared = Class.forName("luna.generated.ExpEstimateSharedRmsStore", true, loader);
        byte[] migrated = (byte[])shared.getMethod("load").invoke(null);
        require(migrated != null && migrated.length >= 6
                        && readInt(migrated, 0) == 0x45585031 && (migrated[4] & 255) == 2
                        && (migrated[5] & 255) >= legacyNames.length,
                "Native cleanup pre-hook migrated old v1 stores into the shared level-only v2 record");
        require(contains(RecordStore.listRecordStores(), "hbs_exp_levels_v2"),
                "Native cleanup leaves the shared RMS table available for login");
        check(true, "Native login cleanup preservation/migration for slot " + slot);
    }

    private static boolean contains(String[] values, String expected) {
        if (values != null) for (String value : values) if (expected.equals(value)) return true;
        return false;
    }

    private static void putRmsRecord(String name, byte[] bytes) throws Exception {
        RecordStore store = null;
        try {
            try { store = RecordStore.openRecordStore(name, true); }
            catch (RecordStoreNotFoundException impossible) { throw impossible; }
            if (store.getNumRecords() == 0) store.addRecord(bytes, 0, bytes.length);
            else {
                javax.microedition.rms.RecordEnumeration records = store.enumerateRecords(null, null, false);
                try { if (records.hasNextElement()) store.setRecord(records.nextRecordId(), bytes, 0, bytes.length); }
                finally { records.destroy(); }
            }
        } finally { if (store != null) store.closeRecordStore(); }
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16
                | (bytes[offset + 2] & 255) << 8 | bytes[offset + 3] & 255;
    }

    private static void verifyPacketRuntime(ClassLoader loader, Properties p, int slot, File jar,
            long[] learnedLow, long[] learnedHigh) throws Exception {
        String prefix = "slot." + slot + ".";
        String ownerName = required(p, prefix + "receiver.owner");
        String xpName = required(p, prefix + "xp.method");
        String xpDesc = required(p, prefix + "xp.descriptor");
        String mainOwner = required(p, prefix + "main.owner");
        String entityOwner = required(p, prefix + "entity.owner");
        String bridgeName = dotted(required(p, prefix + "bridge.owner"));
        Class<?> entityType = Class.forName(dotted(entityOwner), true, loader);
        Object self = mainObject(loader, p, slot);
        Field mainField = Class.forName(dotted(mainOwner), true, loader)
                .getDeclaredField(required(p, prefix + "main.field"));
        mainField.setAccessible(true);
        int id = 6200 + slot * 10, level = 70 + slot, progress = 300;
        setNative(self, required(p, prefix + "id.field"), Integer.valueOf(id));
        setNative(self, required(p, prefix + "level.field"), Short.valueOf((short)level));
        setNative(self, required(p, prefix + "progress.field"), Short.valueOf((short)progress));
        setIfPresent(self, "cv", Byte.valueOf((byte)0));
        setIfPresent(self, "cL", Boolean.FALSE);

        String listField = required(p, prefix + "entity.list.field");
        ArrayList<Object> lists = nativeEntityLists(loader, mainOwner, listField);
        for (Object list : lists) addNativeEntity(list, self);
        Method lookup = entityType.getDeclaredMethod("a", Integer.TYPE, Byte.TYPE); lookup.setAccessible(true);
        require(lookup.invoke(null, Integer.valueOf(id), Byte.valueOf((byte)0)) == self,
                "Native entity registry resolves packet30 self character");

        Object remote = constructOrAllocate(self.getClass());
        int remoteId = id + 1;
        setNative(remote, required(p, prefix + "id.field"), Integer.valueOf(remoteId));
        setIfPresent(remote, "cv", Byte.valueOf((byte)0));
        setIfPresent(remote, "cL", Boolean.FALSE);
        setNative(remote, required(p, prefix + "level.field"), Short.valueOf((short)level));
        setNative(remote, required(p, prefix + "progress.field"), Short.valueOf((short)progress));
        for (Object list : lists) addNativeEntity(list, remote);
        require(lookup.invoke(null, Integer.valueOf(remoteId), Byte.valueOf((byte)0)) == remote,
                "Native entity registry resolves a remote character separately");

        Class<?> bridge = Class.forName(bridgeName, true, loader);
        Class<?> support = Class.forName((slotPrefix(p, slot) + "luna.features.ExpEstimateSupport").replace('/', '.'), true, loader);
        String server = "exp-binary-fixture-server-" + slot;
        Class<?> cache = Class.forName((slotPrefix(p, slot) + "luna.generated.HungMonsterCacheAccess").replace('/', '.'), true, loader);
        cache.getMethod("select", String.class, Integer.TYPE).invoke(null, server, Integer.valueOf(1));
        String nativeServer = nativeServerKey(loader, p, slot);
        require((server + ":1").equals(nativeServer),
                "Estimator key uses the selected native host-and-port server key");
        if (slot > 0) {
            setNative(self, required(p, prefix + "level.field"), Short.valueOf((short)70));
            bridge.getMethod("snapshot").invoke(null);
            Method estimateLow = support.getMethod("estimateLow"), estimateHigh = support.getMethod("estimateHigh");
            require(((Boolean)support.getMethod("isReady").invoke(null)).booleanValue()
                            && ((Long)estimateLow.invoke(null)).longValue() == learnedLow[0]
                            && ((Long)estimateHigh.invoke(null)).longValue() == learnedHigh[0],
                    "Different X3 character/server reads the same shared level estimate");
            require(((Long)support.getMethod("currentSessionTotal").invoke(null)).longValue() == 0L,
                    "Shared level estimate does not share another slot's live samples");
            setNative(self, required(p, prefix + "level.field"), Short.valueOf((short)level));
        }
        bridge.getMethod("snapshot").invoke(null);
        Method currentTotal = support.getMethod("currentSessionTotal");
        Method xp = Class.forName(dotted(ownerName), true, loader)
                .getDeclaredMethod(xpName, packetType(loader, xpDesc));
        xp.setAccessible(true);
        invokeXp(xp, id, progress + 1, 100000, loader, xpDesc);
        require(((Long)currentTotal.invoke(null)).longValue() == 100000L,
                "Actual packet30/type1 self-character gain is counted");
        invokeXp(xp, remoteId, progress + 1, 900000, loader, xpDesc);
        require(((Long)currentTotal.invoke(null)).longValue() == 100000L,
                "Actual packet30/type1 remote-character gain is ignored");
        invokeXp(xp, id, progress + 2, 100000, loader, xpDesc);
        require(((Long)currentTotal.invoke(null)).longValue() == 200000L,
                "Repeated self-character packet increments again");

        bridge.getMethod("levelChanged", Object.class).invoke(null, self);
        require(((Long)currentTotal.invoke(null)).longValue() == 200000L,
                "Same-level notification preserves the estimator segment");
        setNative(self, required(p, prefix + "level.field"), Short.valueOf((short)(level + 1)));
        bridge.getMethod("levelChanged", Object.class).invoke(null, self);
        require(((Long)currentTotal.invoke(null)).longValue() == 0L,
                "Changed-level notification resets the live segment");

        bridge.getMethod("disconnect").invoke(null);
        require(((Long)currentTotal.invoke(null)).longValue() == 0L,
                "Disconnect resets the current session total");
        setNative(self, required(p, prefix + "level.field"), Short.valueOf((short)level));
        setNative(self, required(p, prefix + "progress.field"), Short.valueOf((short)progress));
        bridge.getMethod("snapshot").invoke(null);
        int gainEach = 100000 + slot * 10000;
        for (int i = 1; i <= 10; i++) invokeXp(xp, id, progress + i, gainEach, loader, xpDesc);
        require(((Boolean)support.getMethod("isReady").invoke(null)).booleanValue(),
                "Native packet30 stream learns a level estimate");
        require(((Long)currentTotal.invoke(null)).longValue() == (long)gainEach * 10L,
                "Ten native gains are accumulated once each");
        String line = (String)bridge.getMethod("line").invoke(null);
        require(line.length() > 0 && line.charAt(0) == '(' && line.indexOf("Approx.") < 0
                        && line.indexOf("B") < 0,
                "Native bridge line uses compact K/M display without Approx or B units");
        Method estimateLow = support.getMethod("estimateLow"), estimateHigh = support.getMethod("estimateHigh");
        learnedLow[slot] = ((Long)estimateLow.invoke(null)).longValue();
        learnedHigh[slot] = ((Long)estimateHigh.invoke(null)).longValue();
        require(learnedLow[slot] > 0L && learnedHigh[slot] >= learnedLow[slot],
                "Learned estimate bounds are stored for namespace comparison");
        require(((Long)currentTotal.invoke(null)).longValue() == (long)gainEach * 10L,
                "Slot-local live samples remain independent while persisted estimates are shared");

        bridge.getMethod("disconnect").invoke(null);
        Field storeField = support.getDeclaredField("store"); storeField.setAccessible(true);
        Class<?> storeType = Class.forName((slotPrefix(p, slot) + "luna.generated.ExpEstimateRmsStore").replace('/', '.'), true, loader);
        Object freshStore = constructOrAllocate(storeType);
        support.getMethod("setStore", Class.forName((slotPrefix(p, slot) + "luna.api.ExpEstimateStore").replace('/', '.'), true, loader))
                .invoke(null, freshStore);
        setNative(self, required(p, prefix + "id.field"), Integer.valueOf(0));
        bridge.getMethod("snapshot").invoke(null);
        require(((Boolean)support.getMethod("isReady").invoke(null)).booleanValue()
                        && ((Long)estimateLow.invoke(null)).longValue() == learnedLow[slot]
                        && ((Long)estimateHigh.invoke(null)).longValue() == learnedHigh[slot],
                "Level-only result remains available while native cu identity is provisional");
        setNative(self, required(p, prefix + "id.field"), Integer.valueOf(id));
        String relogLine = (String)bridge.getMethod("line").invoke(null);
        require(((Boolean)support.getMethod("isReady").invoke(null)).booleanValue()
                        && relogLine.length() > 0 && relogLine.indexOf("Approx.") < 0,
                "HUD line reconciles the finalized cu identity and reloads its prior estimate after login");
        require(((Long)estimateLow.invoke(null)).longValue() == learnedLow[slot]
                        && ((Long)estimateHigh.invoke(null)).longValue() == learnedHigh[slot],
                "Relog restore retains this level's exact learned bounds");
        String reloadedLine = (String)bridge.getMethod("line").invoke(null);
        require(reloadedLine.length() > 0 && reloadedLine.indexOf("Approx.") < 0
                        && reloadedLine.indexOf("B") < 0,
                "RMS reload is visible through the native HUD bridge without Approx");
        String rmsName = "hbs_exp_levels_v2";
        boolean foundRms = false;
        String[] rmsNames = rmsManager.listRecordStores();
        if (rmsNames != null) for (String name : rmsNames) if (rmsName.equals(name)) foundRms = true;
        require(foundRms, "Native RMS contains the shared level-only estimate store " + rmsName
                + " among " + java.util.Arrays.toString(rmsNames));
        check(true, "Native packet30, identity, level/session reset and RMS round trip for slot " + slot);
    }

    private static String nativeServerKey(ClassLoader loader, Properties p, int slot) throws Exception {
        String name = (slotPrefix(p, slot) + "luna.generated.HungMonsterCacheAccess").replace('/', '.');
        Class<?> cache = Class.forName(name, true, loader);
        Field instance = cache.getDeclaredField("INSTANCE"); instance.setAccessible(true);
        Object access = instance.get(null);
        require(access != null, "Native cache access singleton exists for EXP server identity");
        String key = (String)cache.getMethod("serverKey").invoke(access);
        require(key != null && key.length() > 0, "Native cache supplies a nonempty EXP server key");
        return key;
    }

    private static Class<?> packetType(ClassLoader loader, String descriptor) throws Exception {
        int start = descriptor.indexOf('L') + 1;
        int end = descriptor.indexOf(';', start);
        return Class.forName(descriptor.substring(start, end).replace('/', '.'), true, loader);
    }

    private static void invokeXp(Method xp, int id, int progress, int gain,
            ClassLoader loader, String descriptor) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(id); out.writeShort(progress); out.writeInt(gain); out.flush();
        Class<?> packet = packetType(loader, descriptor);
        Object value = packet.getConstructor(Byte.TYPE, byte[].class)
                .newInstance(Byte.valueOf((byte)30), bytes.toByteArray());
        try { xp.invoke(null, value); }
        catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            throw e;
        }
    }

    private static String entityListField(Map<String, byte[]> entries, Properties p, int slot) {
        String prefix = "slot." + slot + ".";
        String entityOwner = required(p, prefix + "entity.owner");
        String mainOwner = required(p, prefix + "main.owner");
        ClassNode entity = node(entries.get(entityOwner + ".class"));
        MethodNode lookup = method(entity, "a", "(IB)L" + entityOwner + ";");
        LinkedHashSet<String> found = new LinkedHashSet<String>();
        for (AbstractInsnNode n : lookup.instructions.toArray()) {
            if (!(n instanceof FieldInsnNode)) continue;
            FieldInsnNode field = (FieldInsnNode)n;
            String registryDesc = "L" + slotPrefix(p, slot) + "et;";
            if (field.getOpcode() == GETSTATIC && field.owner.equals(mainOwner) && registryDesc.equals(field.desc)) {
                found.add(field.name);
            }
        }
        require(!found.isEmpty(), "Read native entity lookup registry fields from bytecode");
        StringBuilder names = new StringBuilder();
        for (String name : found) { if (names.length() > 0) names.append(','); names.append(name); }
        return names.toString();
    }

    private static String noticeTextFields(Map<String, byte[]> entries, Properties p, int slot) {
        String prefix = "slot." + slot + ".";
        String owner = required(p, prefix + "hud.owner");
        MethodNode paint = method(node(entries.get(owner + ".class")),
                slotOrGlobal(p, slot, "notice.method"), slotOrGlobal(p, slot, "notice.descriptor"));
        LinkedHashSet<String> fields = new LinkedHashSet<String>();
        for (AbstractInsnNode n : paint.instructions.toArray()) if (n instanceof FieldInsnNode) {
            FieldInsnNode field = (FieldInsnNode)n;
            if (field.getOpcode() == GETFIELD && field.owner.equals(owner)
                    && "Ljava/lang/String;".equals(field.desc)) fields.add(field.name);
        }
        require(fields.size() == 3, "Notice renderer uses three native text fields");
        StringBuilder names = new StringBuilder();
        for (String name : fields) { if (names.length() > 0) names.append(','); names.append(name); }
        return names.toString();
    }

    private static String noticeScrollFields(Map<String, byte[]> entries, Properties p, int slot) {
        String prefix = "slot." + slot + ".";
        String owner = required(p, prefix + "hud.owner");
        String fontOwner = required(p, prefix + "font.owner");
        String fontDraw = required(p, prefix + "font.draw.method");
        String fontDesc = required(p, prefix + "font.draw.descriptor");
        MethodNode paint = method(node(entries.get(owner + ".class")),
                slotOrGlobal(p, slot, "notice.method"), slotOrGlobal(p, slot, "notice.descriptor"));
        AbstractInsnNode[] code = paint.instructions.toArray();
        LinkedHashSet<String> counters = new LinkedHashSet<String>();
        for (int drawIndex = 0; drawIndex < code.length; drawIndex++) {
            if (counters.size() == 2) break;
            if (!(code[drawIndex] instanceof MethodInsnNode)) continue;
            MethodInsnNode draw = (MethodInsnNode)code[drawIndex];
            if (!draw.owner.equals(fontOwner) || !draw.name.equals(fontDraw) || !draw.desc.equals(fontDesc)) continue;
            int textRead = -1;
            for (int i = drawIndex - 1; i >= 0; i--) if (code[i] instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)code[i];
                if (field.getOpcode() == GETFIELD && field.owner.equals(owner)
                        && "Ljava/lang/String;".equals(field.desc)) { textRead = i; break; }
                if (field.getOpcode() == GETFIELD && field.owner.equals(owner) && "I".equals(field.desc)) {
                    // Keep searching backward through x/scroll fields for the text feeding this draw.
                }
            }
            if (textRead < 0) continue;
            String counter = null;
            for (int i = textRead + 1; i < drawIndex && code[i].getOpcode() != ISUB; i++) {
                if (!(code[i] instanceof FieldInsnNode)) continue;
                FieldInsnNode field = (FieldInsnNode)code[i];
                if (field.getOpcode() == GETFIELD && field.owner.equals(owner) && "I".equals(field.desc)) {
                    counter = field.name; break;
                }
            }
            if (counter != null) counters.add(counter);
        }
        require(counters.size() == 2, "Two independent server notice scroll counters");
        StringBuilder names = new StringBuilder();
        for (String name : counters) { if (names.length() > 0) names.append(','); names.append(name); }
        return names.toString();
    }

    private static String noticeCenterField(Map<String, byte[]> entries, Properties p, int slot) {
        String prefix = "slot." + slot + ".";
        String owner = required(p, prefix + "hud.owner");
        String[] serverFields = noticeTextFields(entries, p, slot).split(",");
        String fontOwner = required(p, prefix + "font.owner");
        String fontDraw = required(p, prefix + "font.draw.method");
        String fontDesc = required(p, prefix + "font.draw.descriptor");
        MethodNode paint = method(node(entries.get(owner + ".class")),
                slotOrGlobal(p, slot, "notice.method"), slotOrGlobal(p, slot, "notice.descriptor"));
        AbstractInsnNode[] code = paint.instructions.toArray();
        LinkedHashSet<String> centers = new LinkedHashSet<String>();
        int marqueeRows = 0;
        for (int drawIndex = 0; drawIndex < code.length; drawIndex++) {
            if (!(code[drawIndex] instanceof MethodInsnNode)) continue;
            MethodInsnNode draw = (MethodInsnNode)code[drawIndex];
            if (!draw.owner.equals(fontOwner) || !draw.name.equals(fontDraw) || !draw.desc.equals(fontDesc)) continue;
            int textRead = -1;
            String textField = null;
            for (int i = drawIndex - 1; i >= 0; i--) if (code[i] instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)code[i];
                if (field.getOpcode() == GETFIELD && field.owner.equals(owner)
                        && "Ljava/lang/String;".equals(field.desc)) { textRead = i; textField = field.name; break; }
            }
            if (textRead < 0 || (!serverFields[0].equals(textField) && !serverFields[1].equals(textField))) continue;
            String counter = null;
            for (int i = textRead + 1; i < drawIndex && code[i].getOpcode() != ISUB; i++) {
                if (!(code[i] instanceof FieldInsnNode)) continue;
                FieldInsnNode field = (FieldInsnNode)code[i];
                if (field.getOpcode() == GETFIELD && field.owner.equals(owner) && "I".equals(field.desc)) {
                    counter = field.name; break;
                }
            }
            if (counter == null) continue; // Centered toast has a bounce field, not a marquee counter.
            marqueeRows++;
            for (int i = textRead + 1; i < drawIndex; i++) if (code[i] instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)code[i];
                if (field.getOpcode() == GETSTATIC && "I".equals(field.desc)) {
                    centers.add(field.owner + "#" + field.name); break;
                }
            }
        }
        require(marqueeRows == 2 && centers.size() == 1,
                "Both server marquee rows share one native viewport-center field");
        return centers.iterator().next();
    }

    private static String noticeLayoutSpec(Map<String, byte[]> entries, Properties p, int slot) {
        String owner = required(p, "slot." + slot + ".hud.owner");
        MethodNode layout = method(node(entries.get(owner + ".class")), "a", "()V");
        ArrayList<AbstractInsnNode> real = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode n : layout.instructions.toArray()) if (n.getOpcode() >= 0) real.add(n);
        for (int i = 0; i + 7 < real.size(); i++) {
            AbstractInsnNode[] seq = new AbstractInsnNode[8];
            for (int j = 0; j < seq.length; j++) seq[j] = real.get(i + j);
            if (!(seq[0] instanceof FieldInsnNode) || seq[0].getOpcode() != GETSTATIC
                    || !"I".equals(((FieldInsnNode)seq[0]).desc)
                    || seq[1].getOpcode() != ICONST_1 || seq[2].getOpcode() != ISHL
                    || seq[3].getOpcode() != ICONST_3 || seq[4].getOpcode() != IDIV
                    || !(seq[5] instanceof org.objectweb.asm.tree.IntInsnNode)
                    || seq[5].getOpcode() != BIPUSH
                    || ((org.objectweb.asm.tree.IntInsnNode)seq[5]).operand != 63
                    || seq[6].getOpcode() != ISUB || !(seq[7] instanceof FieldInsnNode)
                    || seq[7].getOpcode() != PUTSTATIC || !"I".equals(((FieldInsnNode)seq[7]).desc)) continue;
            FieldInsnNode screenWidth = (FieldInsnNode)seq[0];
            FieldInsnNode noticeWidth = (FieldInsnNode)seq[7];
            require(Modifier.isStatic(layout.access), "Native notice layout method a()V is static");
            return screenWidth.owner + "#" + screenWidth.name + "|" + noticeWidth.owner + "#"
                    + noticeWidth.name + "|" + layout.name + "|" + layout.desc;
        }
        throw new AssertionError("Derive native notice width and screen-width input from HUD.a()V");
    }

    private static String slotOrGlobal(Properties p, int slot, String key) {
        String value = p.getProperty("slot." + slot + "." + key);
        if (value == null || value.length() == 0) value = p.getProperty(key);
        require(value != null && value.length() > 0, "Missing slot/global " + key);
        return value;
    }

    private static ArrayList<Object> nativeEntityLists(ClassLoader loader, String mainOwner, String fieldNames) throws Exception {
        Class<?> main = Class.forName(dotted(mainOwner), true, loader);
        ArrayList<Object> lists = new ArrayList<Object>();
        String[] names = fieldNames.split(",");
        for (String fieldName : names) {
            Field f = main.getDeclaredField(fieldName); f.setAccessible(true);
            Object list = f.get(null);
            if (list == null) { list = constructOrAllocate(f.getType()); f.set(null, list); }
            boolean duplicate = false;
            for (Object prior : lists) if (prior == list) { duplicate = true; break; }
            if (!duplicate) lists.add(list);
        }
        return lists;
    }

    private static void addNativeEntity(Object list, Object entity) throws Exception {
        Class<?> type = list.getClass(); Method size = type.getDeclaredMethod("c"); size.setAccessible(true);
        for (Method m : type.getDeclaredMethods()) {
            if (m.getReturnType() != Void.TYPE || m.getParameterTypes().length != 1
                    || m.getParameterTypes()[0] != Object.class) continue;
            m.setAccessible(true); int before = ((Integer)size.invoke(list)).intValue();
            m.invoke(list, entity); int after = ((Integer)size.invoke(list)).intValue();
            if (after > before) return;
        }
        throw new IllegalStateException("Could not add entity to native registry " + type.getName());
    }

    private static void setIfPresent(Object target, String name, Object value) throws Exception {
        Field f;
        try { f = findField(target.getClass(), name); } catch (NoSuchFieldException absent) { return; }
        f.setAccessible(true);
        if (f.getType() == Byte.TYPE) f.setByte(target, ((Number)value).byteValue());
        else if (f.getType() == Boolean.TYPE) f.setBoolean(target, ((Boolean)value).booleanValue());
        else if (f.getType() == Integer.TYPE) f.setInt(target, ((Number)value).intValue());
        else if (f.getType() == Short.TYPE) f.setShort(target, ((Number)value).shortValue());
    }

    private static void renderHud(ClassLoader loader, Properties p, int slot, File screenshots) throws Exception {
        String prefix = "slot." + slot + ".";
        String hudOwner = dotted(required(p, prefix + "hud.owner"));
        String hudMethod = required(p, prefix + "hud.method");
        String hudDesc = required(p, prefix + "hud.descriptor");
        String graphicsName = dotted(required(p, prefix + "graphics.owner"));
        String fontName = dotted(required(p, prefix + "font.owner"));
        String bridgeName = dotted(required(p, prefix + "bridge.owner"));
        Class<?> hudType = Class.forName(hudOwner, true, loader);
        Class<?> graphicsType = Class.forName(graphicsName, true, loader);
        Class<?> fontType = Class.forName(fontName, true, loader);
        Class<?> bridgeType = Class.forName(bridgeName, true, loader);

        Object font = firstStatic(fontType, fontType);
        require(font != null, "Native HUD font singleton is initialized");
        Field estimateFontField = fontType.getDeclaredField(required(p, prefix + "estimate.font.field"));
        estimateFontField.setAccessible(true);
        Object estimateFont = estimateFontField.get(null);
        require(estimateFont != null && "/mfont/tahoma_7_white.png".equals(required(p, prefix + "estimate.font.resource")),
                "EXP uses the existing regular white bitmap font");
        Method width = null;
        for (Method m : fontType.getDeclaredMethods())
            if (m.getReturnType() == Integer.TYPE && m.getParameterTypes().length == 1
                    && m.getParameterTypes()[0] == String.class) { width = m; break; }
        require(width != null, "Native font width method exists");
        width.setAccessible(true);
        int ordinary = ((Integer)width.invoke(estimateFont, "(37.4M/100M)")).intValue();
        int large = ((Integer)width.invoke(estimateFont, "(1229.4M/1229.4M)")).intValue();
        require(ordinary <= 88 && large <= 88,
                "Regular M-unit line fits inside the original 96px frame: " + ordinary + "," + large);
        verifyEstimateGlyphs(p, prefix, graphicsType, fontType, estimateFont);

        Object renderer = constructOrAllocate(hudType);
        Object nativeMain = mainObject(loader, p, slot);
        int id = 321 + slot, level = 70, progress = 374;
        setNative(nativeMain, required(p, prefix + "id.field"), Integer.valueOf(id));
        setNative(nativeMain, required(p, prefix + "level.field"), Short.valueOf((short)level));
        setNative(nativeMain, required(p, prefix + "progress.field"), Short.valueOf((short)progress));
        setPositiveNumericFields(nativeMain);
        setNative(nativeMain, required(p, prefix + "id.field"), Integer.valueOf(id));
        setNative(nativeMain, required(p, prefix + "level.field"), Short.valueOf((short)level));
        setNative(nativeMain, required(p, prefix + "progress.field"), Short.valueOf((short)progress));

        Class<?> support = Class.forName((slotPrefix(p, slot) + "luna.features.ExpEstimateSupport").replace('/', '.'), true, loader);
        Class<?> store = Class.forName((slotPrefix(p, slot) + "luna.api.ExpEstimateStore").replace('/', '.'), true, loader);
        support.getMethod("setStore", store).invoke(null, new Object[]{null});
        support.getMethod("snapshot", String.class, Integer.TYPE, Integer.TYPE, Integer.TYPE)
                .invoke(null, "hud-unknown-" + slot, Integer.valueOf(id), Integer.valueOf(level), Integer.valueOf(progress));
        Method bridgeLine = bridgeType.getMethod("line");
        require(p.getProperty("calculating.text", "Caculating...").equals(bridgeLine.invoke(null)),
                "Unknown screenshot uses the configured calculating text");
        renderNativeHud(hudType, hudMethod, graphicsType, fontType, renderer, font, true,
                new File(screenshots, "native-hud-slot-" + slot + "-unknown.png"));

        seedExactEstimate(loader, p, slot, id, level, progress);
        require("(37.4M/100M)".equals(bridgeLine.invoke(null)), "Bridge renders exact example line");
        renderNativeHud(hudType, hudMethod, graphicsType, fontType, renderer, font, true,
                new File(screenshots, "native-hud-slot-" + slot + "-estimate.png"));
        if ("3".equals(p.getProperty("percent.decimals"))) {
            Method percentage = support.getMethod("percentage", Integer.TYPE);
            require("37,4%".equals(percentage.invoke(null, Integer.valueOf(progress))),
                    "Login keeps native precision until a live gain gives a phase sample");
            Method xp = Class.forName(dotted(required(p, prefix + "receiver.owner")), true, loader)
                    .getDeclaredMethod(required(p, prefix + "xp.method"),
                            packetType(loader, required(p, prefix + "xp.descriptor")));
            xp.setAccessible(true);
            invokeXp(xp, id, progress, 1000, loader, required(p, prefix + "xp.descriptor"));
            String firstFine = (String)percentage.invoke(null, Integer.valueOf(progress));
            invokeXp(xp, id, progress, 20000, loader, required(p, prefix + "xp.descriptor"));
            String nextFine = (String)percentage.invoke(null, Integer.valueOf(progress));
            require(firstFine.matches("37,4[0-9]{2}%") && nextFine.matches("37,4[0-9]{2}%")
                            && nextFine.compareTo(firstFine) > 0,
                    "Real native EXP packets update three digits inside an unchanged 0.1 percent bucket");
            Field nativeProgress = findField(nativeMain.getClass(), required(p, prefix + "progress.field"));
            nativeProgress.setAccessible(true);
            require(nativeProgress.getShort(nativeMain) == progress,
                    "Fine percentage never overwrites the native progress field or XP bar input");
            require(((Integer)width.invoke(font, "Lv.255 +99,999%")).intValue() > 0,
                    "Fine percentage is measured with the unchanged native level font");
            renderNativeHud(hudType, hudMethod, graphicsType, fontType, renderer, font, true,
                    new File(screenshots, "native-hud-slot-" + slot + "-fine-percent.png"));
        }
        seedEstimate(loader, p, slot, id, level, progress, 1229400000L);
        require("(459.8M/1229.4M)".equals(bridgeLine.invoke(null)),
                "Bridge renders a large M-unit line without B notation");
        int largeLineWidth = ((Integer)width.invoke(estimateFont, "(459.8M/1229.4M)")).intValue();
        require(largeLineWidth <= 88, "Large M-unit text fits inside the original 96px frame: " + largeLineWidth);
        renderNativeHud(hudType, hudMethod, graphicsType, fontType, renderer, font, true,
                new File(screenshots, "native-hud-slot-" + slot + "-estimate-large.png"));
        renderNoticeViewports(loader, p, slot, screenshots, hudType, hudMethod,
                graphicsType, fontType, bridgeType, renderer, font, width);
        check(true, "Native HUD rendered unknown and estimate lines with the stock frame for slot " + slot);
    }

    private static void verifyEstimateGlyphs(Properties p, String prefix, Class<?> graphicsType,
            Class<?> fontType, Object estimateFont) throws Exception {
        Method draw = fontType.getDeclaredMethod(required(p, prefix + "font.draw.method"),
                graphicsType, String.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, Boolean.TYPE);
        draw.setAccessible(true);
        String glyphs = "0123456789KM./()";
        for (int i = 0; i < glyphs.length(); i++) {
            Image glyphImage = Image.createImage(24, 24);
            javax.microedition.lcdui.Graphics g = glyphImage.getGraphics();
            g.setColor(0); g.fillRect(0, 0, 24, 24);
            Object wrapper = bindNativeGraphics(graphicsType, g);
            draw.invoke(estimateFont, wrapper, glyphs.substring(i, i + 1),
                    Integer.valueOf(2), Integer.valueOf(2), Integer.valueOf(0), Boolean.FALSE);
            boolean visible = false;
            for (int pixel : imagePixels(glyphImage)) if ((pixel & 0xFFFFFF) != 0) { visible = true; break; }
            require(visible, "Regular EXP font visibly draws glyph " + glyphs.charAt(i));
        }
    }

    private static void renderNoticeViewports(ClassLoader loader, Properties p, int slot,
            File screenshots, Class<?> hudType, String hudMethod, Class<?> graphicsType,
            Class<?> fontType, Class<?> bridgeType, Object renderer, Object font, Method fontWidth) throws Exception {
        String prefix = "slot." + slot + ".";
        String noticeMethodName = slotOrGlobal(p, slot, "notice.method");
        String noticeDesc = slotOrGlobal(p, slot, "notice.descriptor");
        require(noticeDesc.equals("(L" + required(p, prefix + "graphics.owner") + ";)V"),
                "Notice renderer accepts the native Graphics wrapper");
        Method noticePaint = hudType.getDeclaredMethod(noticeMethodName,
                new Class<?>[]{graphicsType}); noticePaint.setAccessible(true);
        Field baseYField = findField(hudType, slotOrGlobal(p, slot, "notice.base.field"));
        baseYField.setAccessible(true);
        require(Modifier.isStatic(baseYField.getModifiers()) && baseYField.getType() == Integer.TYPE,
                "Native notice baseline is a static screen y coordinate");
        int originalY = baseYField.getInt(null);
        require(originalY >= 0 && originalY < 56,
                "Fixture reads the native notice baseline at its original HUD position: " + originalY);

        String hideOwner = dotted(slotOrGlobal(p, slot, "notice.hide.owner"));
        String hideFieldName = slotOrGlobal(p, slot, "notice.hide.field");
        Class<?> hideType = Class.forName(hideOwner, true, loader);
        Field hideField = findField(hideType, hideFieldName); hideField.setAccessible(true);
        require(Modifier.isStatic(hideField.getModifiers()) && hideField.getType() == Boolean.TYPE,
                "HUD visibility flag is a native static boolean");
        String[] centerSpec = required(p, prefix + "notice.center.field").split("#", 2);
        require(centerSpec.length == 2, "Cataloged native notice viewport center");
        Field screenCenter = findField(Class.forName(dotted(centerSpec[0]), true, loader), centerSpec[1]);
        screenCenter.setAccessible(true);
        require(Modifier.isStatic(screenCenter.getModifiers()) && screenCenter.getType() == Integer.TYPE,
                "Notice renderer uses the native static viewport center");
        int originalCenter = screenCenter.getInt(null);
        String[] layoutSpec = required(p, prefix + "notice.layout.spec").split("\\|", -1);
        require(layoutSpec.length == 4, "Catalog native HUD notice layout inputs");
        String[] screenWidthSpec = layoutSpec[0].split("#", 2);
        String[] noticeWidthSpec = layoutSpec[1].split("#", 2);
        require(screenWidthSpec.length == 2 && noticeWidthSpec.length == 2,
                "Native screen-width and notice-width fields are fully qualified");
        Field screenWidthField = findField(Class.forName(dotted(screenWidthSpec[0]), true, loader), screenWidthSpec[1]);
        screenWidthField.setAccessible(true);
        Field noticeWidthField = findField(Class.forName(dotted(noticeWidthSpec[0]), true, loader), noticeWidthSpec[1]);
        noticeWidthField.setAccessible(true);
        require(Modifier.isStatic(screenWidthField.getModifiers()) && screenWidthField.getType() == Integer.TYPE,
                "Native layout reads the static screen-width global");
        require(Modifier.isStatic(noticeWidthField.getModifiers()) && noticeWidthField.getType() == Integer.TYPE,
                "Native layout writes the static notice viewport width");
        Method nativeLayout = hudType.getDeclaredMethod(layoutSpec[2], new Class<?>[0]);
        nativeLayout.setAccessible(true);
        require(Modifier.isStatic(nativeLayout.getModifiers()) && "()V".equals(layoutSpec[3]),
                "Fixture invokes the native static HUD.a()V layout method");
        int originalScreenWidth = screenWidthField.getInt(null);
        int originalNoticeWidth = noticeWidthField.getInt(null);
        Method noticeY = bridgeType.getMethod("noticeY", Integer.TYPE);
        hideField.setBoolean(null, false);
        require(((Integer)noticeY.invoke(null, Integer.valueOf(originalY))).intValue() == originalY,
                "Retained noticeY bridge ABI returns the native baseline unchanged");
        hideField.setBoolean(null, true);
        require(((Integer)noticeY.invoke(null, Integer.valueOf(originalY))).intValue() == originalY,
                "Notice baseline remains unchanged when the HUD is hidden");
        hideField.setBoolean(null, false);

        String[] fields = required(p, prefix + "notice.text.fields").split(",");
        require(fields.length == 3, "Seed all three native server/toast strings");
        String[] texts = new String[]{
            "zzkhoadzzz đã trúng Đá ba ma...",
            "Thông báo máy chủ: phần thưởng đã được gửi tới toàn bản đồ.",
            "Số lượng MP lớn đã đầy"
        };
        for (int i = 0; i < fields.length; i++) {
            Field text = findField(hudType, fields[i]); text.setAccessible(true);
            require(text.getType() == String.class, "Native notice text field " + fields[i]);
            text.set(renderer, texts[i]);
        }
        String[] scrollFields = required(p, prefix + "notice.scroll.fields").split(",");
        require(scrollFields.length == 2, "Seed two native marquee offsets for screenshot visibility");

        for (int width : new int[]{450, 240}) {
            screenWidthField.setInt(null, width);
            nativeLayout.invoke(null);
            int nativeNoticeWidth = noticeWidthField.getInt(null);
            int expectedNoticeWidth = ((width << 1) / 3) - 63;
            require(nativeNoticeWidth == expectedNoticeWidth && nativeNoticeWidth > 0,
                    "Native HUD.a()V computes notice width " + expectedNoticeWidth + " at screen width " + width
                            + ", got " + nativeNoticeWidth);
            screenCenter.setInt(null, width / 2);
            Image hudOnly = Image.createImage(width, 320);
            Image withNotices = Image.createImage(width, 320);
            javax.microedition.lcdui.Graphics hudGraphics = hudOnly.getGraphics();
            javax.microedition.lcdui.Graphics noticeGraphics = withNotices.getGraphics();
            hudGraphics.setColor(0xFFFFFF); hudGraphics.fillRect(0, 0, width, 320);
            noticeGraphics.setColor(0xFFFFFF); noticeGraphics.fillRect(0, 0, width, 320);
            Object hudWrapper = bindNativeGraphics(graphicsType, hudGraphics);
            Object noticeWrapper = bindNativeGraphics(graphicsType, noticeGraphics);
            for (int i = 0; i < scrollFields.length; i++) {
                Field scroll = findField(hudType, scrollFields[i]); scroll.setAccessible(true);
                require(scroll.getType() == Integer.TYPE, "Native marquee scroll field " + scrollFields[i]);
                int measuredWidth = ((Integer)fontWidth.invoke(font, texts[i])).intValue();
                require(measuredWidth > 0, "Native font measures marquee text");
                scroll.setInt(renderer, nativeNoticeWidth); // Native leading edge: x = center - viewportWidth/2.
            }
            paintHud(hudType, hudMethod, graphicsType, fontType, renderer, font, hudWrapper, true);
            paintHud(hudType, hudMethod, graphicsType, fontType, renderer, font, noticeWrapper, true);
            noticePaint.invoke(renderer, noticeWrapper);
            int[] baseline = imagePixels(hudOnly);
            int[] rendered = imagePixels(withNotices);
            require(samePixels(baseline, rendered, width, originalY),
                    "Native notice rendering preserves all pixels above its original baseline at width " + width);
            require(hasPixelDifference(baseline, rendered, width, originalY, 320),
                    "Server announcement/toast render from the native baseline at width " + width);
            writePng(withNotices, new File(screenshots,
                    "native-hud-notices-slot-" + slot + "-" + width + ".png"));
        }
        screenCenter.setInt(null, originalCenter);
        screenWidthField.setInt(null, originalScreenWidth);
        noticeWidthField.setInt(null, originalNoticeWidth);
    }

    private static void setPositiveNumericFields(Object target) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                if (f.getType() == Integer.TYPE) f.setInt(target, 100);
                else if (f.getType() == Short.TYPE) f.setShort(target, (short)100);
            }
        }
    }

    private static void renderNativeHud(Class<?> hudType, String hudMethod, Class<?> graphicsType,
            Class<?> fontType, Object renderer, Object font, boolean visible, File file) throws Exception {
        Image image = Image.createImage(200, 80);
        javax.microedition.lcdui.Graphics g = image.getGraphics();
        g.setColor(0xFFFFFF); g.fillRect(0, 0, 200, 80);
        Object wrapper = bindNativeGraphics(graphicsType, g);
        paintHud(hudType, hudMethod, graphicsType, fontType, renderer, font, wrapper, visible);
        require(hasNonWhitePixel(image, 0, 35, 96, 21), "Native original-width rectangular frame and line are visible");
        writePng(image, file);
    }

    private static Object bindNativeGraphics(Class<?> graphicsType,
            javax.microedition.lcdui.Graphics graphics) throws Exception {
        Object wrapper = constructOrAllocate(graphicsType);
        boolean bound = false;
        for (Field f : graphicsType.getFields()) if (f.getType() == javax.microedition.lcdui.Graphics.class) {
            f.set(wrapper, graphics); bound = true; break;
        }
        require(bound, "Native Graphics wrapper bound to MicroEmulator Image");
        return wrapper;
    }

    private static void paintHud(Class<?> hudType, String hudMethod, Class<?> graphicsType,
            Class<?> fontType, Object renderer, Object font, Object wrapper, boolean visible) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[]{graphicsType, Integer.TYPE, Integer.TYPE,
                Boolean.TYPE, fontType};
        Method paint = hudType.getDeclaredMethod(hudMethod, parameterTypes); paint.setAccessible(true);
        paint.invoke(renderer, wrapper, Integer.valueOf(0), Integer.valueOf(0), Boolean.valueOf(visible), font);
    }

    private static int[] imagePixels(Image image) {
        int width = image.getWidth(), height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(pixels, 0, width, 0, 0, width, height);
        return pixels;
    }

    private static boolean samePixels(int[] left, int[] right, int width, int rows) {
        for (int y = 0; y < rows; y++) for (int x = 0; x < width; x++)
            if (left[y * width + x] != right[y * width + x]) return false;
        return true;
    }

    private static boolean hasPixelDifference(int[] left, int[] right, int width, int firstRow, int lastRow) {
        for (int y = firstRow; y < lastRow; y++) for (int x = 0; x < width; x++)
            if (left[y * width + x] != right[y * width + x]) return true;
        return false;
    }

    private static boolean hasNonWhitePixel(Image image, int x, int y, int width, int height) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getRGB(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        for (int row = y; row < y + height; row++)
            for (int col = x; col < x + width; col++)
                if ((pixels[row * image.getWidth() + col] & 0xFFFFFF) != 0xFFFFFF) return true;
        return false;
    }

    private static void seedExactEstimate(ClassLoader loader, Properties p, int slot,
            int id, int level, int progress) throws Exception {
        seedEstimate(loader, p, slot, id, level, progress, 100000000L);
    }

    private static void seedEstimate(ClassLoader loader, Properties p, int slot,
            int id, int level, int progress, long total) throws Exception {
        String prefix = "slot." + slot + ".";
        String supportName = (slotPrefix(p, slot) + "luna.features.ExpEstimateSupport").replace('/', '.');
        String storeName = (slotPrefix(p, slot) + "luna.api.ExpEstimateStore").replace('/', '.');
        Class<?> support = Class.forName(supportName, true, loader);
        Class<?> store = Class.forName(storeName, true, loader);
        Class<?> cache = Class.forName((slotPrefix(p, slot) + "luna.generated.HungMonsterCacheAccess").replace('/', '.'), true, loader);
        Object cacheAccess = cache.getField("INSTANCE").get(null);
        String server = (String)cache.getMethod("serverKey").invoke(cacheAccess);
        final byte[] record = estimateRecord(server, id, level, total, total, 20, 30);
        Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{store}, new InvocationHandler() {
            public Object invoke(Object proxy, Method m, Object[] args) {
                if (m.getName().equals("load")) return record;
                if (m.getName().equals("save")) return Boolean.TRUE;
                return null;
            }
        });
        support.getMethod("setStore", store).invoke(null, proxy);
        support.getMethod("snapshot", String.class, Integer.TYPE, Integer.TYPE, Integer.TYPE)
                .invoke(null, server, Integer.valueOf(id), Integer.valueOf(level), Integer.valueOf(progress));
    }

    private static byte[] estimateRecord(String server, int id, int level,
            long low, long high, int samples, int span) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x45585031); out.writeByte(1); out.writeByte(1); out.writeUTF(server);
        out.writeInt(id); out.writeInt(level); out.writeLong(low); out.writeLong(high);
        out.writeShort(samples); out.writeShort(span); out.flush();
        return bytes.toByteArray();
    }

    private static Object mainObject(ClassLoader loader, Properties p, int slot) throws Exception {
        String prefix = "slot." + slot + ".";
        Class<?> main = Class.forName(dotted(required(p, prefix + "main.owner")), true, loader);
        Field f = main.getDeclaredField(required(p, prefix + "main.field")); f.setAccessible(true);
        Object value = f.get(null);
        if (value == null) {
            Class<?> type = Class.forName(dotted(required(p, prefix + "main.descriptor").substring(1,
                    required(p, prefix + "main.descriptor").length() - 1)), true, loader);
            value = constructOrAllocate(type); f.set(null, value);
        }
        return value;
    }

    private static void setNative(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name); f.setAccessible(true);
        if (f.getType() == Integer.TYPE) f.setInt(target, ((Number)value).intValue());
        else if (f.getType() == Short.TYPE) f.setShort(target, ((Number)value).shortValue());
        else throw new IllegalStateException("Unexpected native field type " + f);
    }

    private static Field findField(Class<?> type, String name) throws Exception {
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Object firstStatic(Class<?> type, Class<?> fieldType) throws Exception {
        for (Field f : type.getDeclaredFields()) if (Modifier.isStatic(f.getModifiers()) && f.getType() == fieldType) {
            f.setAccessible(true); Object value = f.get(null); if (value != null) return value;
        }
        return null;
    }

    private static Object constructOrAllocate(Class<?> type) throws Exception {
        try {
            java.lang.reflect.Constructor<?> c = type.getDeclaredConstructor(); c.setAccessible(true); return c.newInstance();
        } catch (NoSuchMethodException missing) {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field singleton = unsafeType.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
            Object unsafe = singleton.get(null);
            return unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        }
    }

    private static Common startEmulator(File jar) throws Exception {
        Headless headless = new Headless();
        Field f = Headless.class.getDeclaredField("emulator"); f.setAccessible(true);
        Common emulator = (Common)f.get(headless);
        rmsManager = new MemoryRecordStoreManager();
        rmsManager.init(emulator); emulator.setRecordStoreManager(rmsManager); MIDletBridge.setMicroEmulator(emulator);
        ArrayList<String> options = new ArrayList<String>();
        options.add("--rms"); options.add("memory"); options.add(jar.getAbsolutePath());
        DeviceEntry device = new DeviceEntry("Default device", null,
                "org/microemu/device/default/device.xml", true, false);
        require(emulator.initParams(options, device, J2SEDevice.class), "MicroEmulator initialization");
        emulator.initMIDlet(true); Thread.sleep(800);
        return emulator;
    }

    private static void verifyX3Screenshots(Common emulator, MIDlet launcher, File dir) throws Exception {
        ClassLoader loader = launcher.getClass().getClassLoader();
        Class<?> displayClass = Class.forName("lib.Display", false, loader);
        Display display = (Display)displayClass.getField("d").get(null);
        Method action = launcher.getClass().getMethod("commandAction",
                javax.microedition.lcdui.Command.class, Displayable.class);
        Field virtualField = null;
        for (Field f : launcher.getClass().getDeclaredFields())
            if (f.getType().isArray() && f.getType().getComponentType().getName().equals("lib.MIDlet")) virtualField = f;
        require(virtualField != null, "X3 launcher exposes its three virtual clients"); virtualField.setAccessible(true);
        Object[] clients = (Object[])virtualField.get(null);
        Displayable menu = display.getCurrent();
        require(menu instanceof javax.microedition.lcdui.List, "X3 launcher menu is visible");
        javax.microedition.lcdui.List rows = (javax.microedition.lcdui.List)menu;
        require(rows.size() == 3, "X3 has three native client rows");
        snapshotDisplay(emulator, new File(dir, "x3-launcher.png"));
        for (int slot = 0; slot < 3; slot++) {
            rows.setSelectedIndex(slot, true);
            action.invoke(launcher, new javax.microedition.lcdui.Command("Select",
                    javax.microedition.lcdui.Command.OK, 0), rows);
            long until = System.currentTimeMillis() + 4000L;
            while ((clients[slot] == null || display.getCurrent() instanceof javax.microedition.lcdui.List)
                    && System.currentTimeMillis() < until) Thread.sleep(40);
            require(clients[slot] != null && display.getCurrent() instanceof Canvas, "X3 client started " + slot);
            snapshotDisplay(emulator, new File(dir, "x3-slot-" + slot + "-login.png"));
            if (slot < 2) {
                Canvas canvas = (Canvas)display.getCurrent();
                canvas.getClass().getMethod("keyPressed", Integer.TYPE).invoke(canvas, Integer.valueOf(42));
                until = System.currentTimeMillis() + 2000L;
                while (display.getCurrent() != menu && System.currentTimeMillis() < until) Thread.sleep(25);
                require(display.getCurrent() == menu, "Held-* returns to X3 menu");
                canvas.getClass().getMethod("keyReleased", Integer.TYPE).invoke(canvas, Integer.valueOf(42));
            }
        }
    }

    private static void snapshotDisplay(Common emulator, File file) throws Exception {
        J2SEDeviceDisplay display = (J2SEDeviceDisplay)emulator.getDevice().getDeviceDisplay();
        BufferedImage image = new BufferedImage(display.getFullWidth(), display.getFullHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics g = image.getGraphics();
        display.paintDisplayable(g, 0, 0, image.getWidth(), image.getHeight()); g.dispose();
        ImageIO.write(image, "png", file);
    }

    private static void writePng(Image image, File file) throws Exception {
        int width = image.getWidth(), height = image.getHeight();
        int[] pixels = new int[width * height]; image.getRGB(pixels, 0, width, 0, 0, width, height);
        BufferedImage rendered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        rendered.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(rendered, "png", file);
    }

    private static ClassNode node(byte[] bytes) { ClassNode c = new ClassNode(); new ClassReader(bytes).accept(c, 0); return c; }
    private static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new IllegalStateException("Missing " + c.name + "." + name + desc);
    }
    private static MethodNode namedMethod(ClassNode c, String name) {
        for (MethodNode m : c.methods) if (m.name.equals(name)) return m;
        return null;
    }
    private static Map<String, byte[]> read(File file) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<String, byte[]>(); JarFile jar = new JarFile(file);
        try { Enumeration<JarEntry> es = jar.entries(); while (es.hasMoreElements()) {
            JarEntry e = es.nextElement(); if (e.isDirectory()) continue;
            InputStream in = jar.getInputStream(e); try { ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[8192]; for (int n; (n = in.read(b)) >= 0;) out.write(b, 0, n);
                result.put(e.getName(), out.toByteArray()); } finally { in.close(); }
        }} finally { jar.close(); } return result;
    }
    private static int classCount(Map<String, byte[]> entries) {
        int count = 0; for (String n : entries.keySet()) if (n.endsWith(".class")) count++; return count;
    }
    private static Properties load(File file) throws Exception {
        Properties p = new Properties(); InputStream in = new FileInputStream(file);
        try { p.load(in); } finally { in.close(); } return p;
    }
    private static Properties stateProperties(File file) throws Exception {
        return file.isFile() ? load(file) : new Properties();
    }
    private static void writeState(File file, Properties p) throws Exception {
        java.io.FileOutputStream out = new java.io.FileOutputStream(file);
        try { p.store(out, "Fresh-process shared-level RMS bounds"); } finally { out.close(); }
    }
    private static String required(Properties p, String key) {
        String value = p.getProperty(key); require(value != null && value.length() > 0, "Missing " + key); return value;
    }
    private static String dotted(String value) { return value.replace('/', '.'); }
    private static String slotPrefix(Properties p, int slot) { return slot == 0 ? "" : (slot == 1 ? "b/" : "c/"); }
    private static String hash(File file) throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath()); byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder(); for (byte b : digest) out.append(String.format("%02X", Integer.valueOf(b & 255))); return out.toString();
    }
    private static void require(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
