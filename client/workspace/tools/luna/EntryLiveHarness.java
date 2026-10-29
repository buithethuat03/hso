import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.midlet.MIDlet;

import org.microemu.app.Headless;
import org.microemu.app.util.DeviceEntry;
import org.microemu.app.util.FileRecordStoreManager;
import org.microemu.device.j2se.J2SEDevice;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;

/**
 * Live entry diagnostic for a copied native account RMS. The harness only
 * drives the game's own Canvas key callbacks; it never reads or prints account
 * record contents and never creates a character.
 */
public final class EntryLiveHarness {
    private static final int ENTER_KEY = 10;
    private static final String[] DISABLE_FLAGS = new String[] {
        "engine.field.autoAttack", "engine.field.autoHpMp", "engine.field.autoHorse",
        "engine.field.petFeed", "engine.field.autoNextMap", "engine.field.autoSell"
    };

    private EntryLiveHarness() { }

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            run(args);
        } catch (Throwable failure) {
            safeFailure("HARNESS", failure);
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    private static void run(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            safeState("HARNESS_USAGE=1");
            return;
        }
        File jar = new File(args[0]).getAbsoluteFile();
        int duration = args.length == 2 ? Integer.parseInt(args[1]) : 30;
        if (duration < 1 || duration > 600) throw new IllegalArgumentException("durationSeconds out of range");
        if (!jar.isFile()) throw new IOException("Input JAR is not a file");
        String tracePath = System.getProperty("entry.traceDir");
        if (tracePath == null || tracePath.length() == 0) throw new IllegalStateException("entry.traceDir is required");
        File traceDir = new File(tracePath).getAbsoluteFile();
        File trace = new File(traceDir, "trace.txt");

        safeState("HARNESS_START durationSeconds=" + duration + " jarBytes=" + jar.length());
        Headless headless = new Headless();
        Field emulatorField = Headless.class.getDeclaredField("emulator");
        emulatorField.setAccessible(true);
        Common emulator = (Common)emulatorField.get(headless);
        FileRecordStoreManager disk = new FileRecordStoreManager();
        disk.init(emulator);
        emulator.setRecordStoreManager(disk);
        MIDletBridge.setMicroEmulator(emulator);
        ArrayList<String> options = new ArrayList<String>();
        options.add("--rms");
        options.add("file");
        options.add("--appclass"); options.add("EntryTraceBootstrap");
        options.add(jar.getAbsolutePath());
        DeviceEntry device = new DeviceEntry("Default device", null,
                "org/microemu/device/default/device.xml", true, false);
        if (!emulator.initParams(options, device, J2SEDevice.class)) {
            throw new IllegalStateException("MicroEmulator initParams failed");
        }
        emulator.initMIDlet(true);
        Thread.sleep(500L);

        MIDlet active = MIDletBridge.getCurrentMIDlet();
        if (active == null) throw new IllegalStateException("MIDlet did not start");
        ClassLoader loader = active.getClass().getClassLoader();
        Properties symbols = loadProperties(System.getProperty("entry.symbols",
                "symbols/autotrade-symbols.properties"));
        Map<String,String> aliases = loadAliases(new File(traceDir, "symbols.properties"));
        Canvas canvas = getCurrentCanvas(active);
        long canvasDeadline = System.currentTimeMillis() + 5000L;
        while (canvas == null && System.currentTimeMillis() < canvasDeadline) {
            Thread.sleep(100L); active = MIDletBridge.getCurrentMIDlet(); canvas = getCurrentCanvas(active);
        }
        safeState("ACTIVE_MIDLET class=" + active.getClass().getName() + " display=" + (Display.getDisplay(active).getCurrent() == null ? "NONE" : Display.getDisplay(active).getCurrent().getClass().getName()));
        if (canvas == null) throw new IllegalStateException("MIDlet display has no current Canvas");
        ScreenReader screenReader = new ScreenReader(loader, symbols, aliases, canvas);
        Class<?> engineClass = loadNativeClass(loader, symbols.getProperty("class.ENGINE"));
        disableAutomation(engineClass, symbols);

        safeState("MIDLET_STARTED width=" + canvas.getWidth() + " height=" + canvas.getHeight()
                + " ui=" + screenReader.alias());
        long began = System.currentTimeMillis();
        long deadline = began + duration * 1000L;
        boolean loginEnterSent = false;
        boolean characterEnterSent = false;
        boolean worldDataReceived = false;
        int lastOpcode13Length = -1;
        int lastScreenHash = Integer.MIN_VALUE;
        int sequenceAtSelection = Integer.MAX_VALUE;
        long existingListSeenAt = 0L;

        while (System.currentTimeMillis() < deadline) {
            disableAutomation(engineClass, symbols);
            String alias = screenReader.alias();
            int screenHash = screenReader.classHash();
            if (screenHash != lastScreenHash) {
                safeState("UI_CHANGE alias=" + alias + " classCode=" + screenHash);
                lastScreenHash = screenHash;
            }

            TraceSnapshot snapshot = readTrace(trace);
            if (snapshot.rx13Length >= 0 && snapshot.rx13Length != lastOpcode13Length) {
                lastOpcode13Length = snapshot.rx13Length;
                if (snapshot.rx13Length > 1 && existingListSeenAt == 0L) {
                    existingListSeenAt = System.currentTimeMillis();
                }
                safeState("CHARACTER_RESPONSE payloadLength=" + snapshot.rx13Length
                        + " nonEmpty=" + (snapshot.rx13Length > 1 ? 1 : 0));
            }

            if (!loginEnterSent && System.currentTimeMillis() - began >= 1800L
                    && snapshot.rx13Length < 0 && snapshot.tx1Count == 0) {
                sendEnter(canvas);
                loginEnterSent = true;
                safeState("NATIVE_LOGIN_ENTER sent=1 ui=" + alias);
            }

            if (!characterEnterSent && snapshot.rx13Length > 1 && snapshot.tx13Count == 0
                    && existingListSeenAt != 0L && System.currentTimeMillis() - existingListSeenAt >= 300L) {
                sequenceAtSelection = snapshot.maxRxSequence;
                sendEnter(canvas);
                characterEnterSent = true;
                safeState("NATIVE_SELECT_FIRST_EXISTING sent=1 payloadLength=" + snapshot.rx13Length
                        + " ui=" + screenReader.alias());
            }

            if (characterEnterSent && snapshot.worldDataSequence > sequenceAtSelection) {
                worldDataReceived = true;
                safeState("WORLD_DATA_RECEIVED incomingData=1 rx3=" + snapshot.rx3Count
                        + " rx16=" + snapshot.rx16Count + " rx65=" + snapshot.rx65Count);
                break;
            }
            Thread.sleep(200L);
        }

        TraceSnapshot finalSnapshot = readTrace(trace);
        safeState("HARNESS_END loginEnter=" + (loginEnterSent ? 1 : 0)
                + " existingList=" + (finalSnapshot.rx13Length > 1 ? 1 : 0)
                + " selectEnter=" + (characterEnterSent ? 1 : 0)
                + " worldDataReceived=" + (worldDataReceived ? 1 : 0)
                + " rx13Length=" + finalSnapshot.rx13Length
                + " tx1=" + finalSnapshot.tx1Count + " tx13=" + finalSnapshot.tx13Count
                + " rx3=" + finalSnapshot.rx3Count + " rx16=" + finalSnapshot.rx16Count
                + " rx65=" + finalSnapshot.rx65Count);
    }

    private static Canvas getCurrentCanvas(MIDlet active) {
        Displayable current = Display.getDisplay(active).getCurrent();
        return current instanceof Canvas ? (Canvas)current : null;
    }

    private static void sendEnter(Canvas canvas) throws Exception {
        Method pressed = canvas.getClass().getDeclaredMethod("keyPressed", Integer.TYPE);
        Method released = canvas.getClass().getDeclaredMethod("keyReleased", Integer.TYPE);
        pressed.setAccessible(true);
        released.setAccessible(true);
        pressed.invoke(canvas, Integer.valueOf(ENTER_KEY));
        Thread.sleep(100L);
        released.invoke(canvas, Integer.valueOf(ENTER_KEY));
    }

    private static void disableAutomation(Class<?> engineClass, Properties symbols) {
        if (engineClass == null) return;
        for (int i = 0; i < DISABLE_FLAGS.length; i++) {
            String fieldName = symbols.getProperty(DISABLE_FLAGS[i]);
            if (fieldName == null) continue;
            try {
                Field field = engineClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                if (field.getType() == Boolean.TYPE && field.getBoolean(null)) field.setBoolean(null, false);
            } catch (Throwable ignored) { }
        }
    }

    private static Class<?> loadNativeClass(ClassLoader loader, String internalName) {
        if (internalName == null) return null;
        try { return Class.forName(internalName.replace('/', '.'), true, loader); }
        catch (Throwable ignored) { return null; }
    }

    private static Properties loadProperties(String path) throws IOException {
        Properties result = new Properties();
        InputStream input = new FileInputStream(path);
        try { result.load(input); } finally { input.close(); }
        return result;
    }

    private static Map<String,String> loadAliases(File file) {
        Map<String,String> result = new HashMap<String,String>();
        if (!file.isFile()) return result;
        Properties properties = new Properties();
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            properties.load(input);
            for (Map.Entry<Object,Object> entry : properties.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (key.indexOf('.') < 0) result.put(String.valueOf(entry.getValue()), key);
            }
        } catch (Throwable ignored) {
            result.clear();
        } finally {
            if (input != null) try { input.close(); } catch (IOException ignored) { }
        }
        return result;
    }

    private static TraceSnapshot readTrace(File trace) {
        TraceSnapshot result = new TraceSnapshot();
        if (!trace.isFile()) return result;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(trace));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.indexOf(" RX seq=") >= 0) {
                    int sequence = integerAfter(line, " RX seq=");
                    int opcode = integerAfter(line, " op=");
                    int length = integerAfter(line, " len=");
                    if (sequence > result.maxRxSequence) result.maxRxSequence = sequence;
                    if (opcode == 13 && length >= 0) result.rx13Length = length;
                    else if (opcode == 3) result.rx3Count++;
                    else if (opcode == 16) result.rx16Count++;
                    else if (opcode == 65) result.rx65Count++;
                    if (opcode == 3 || opcode == 16 || opcode == 65 || opcode == 52
                            || opcode == -50 || opcode == -54) {
                        result.worldDataSequence = sequence;
                    }
                } else if (line.indexOf(" TX op=") >= 0) {
                    int opcode = integerAfter(line, " TX op=");
                    if (opcode == 1) result.tx1Count++;
                    else if (opcode == 13) result.tx13Count++;
                }
            }
        } catch (Throwable ignored) {
            return result;
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) { }
        }
        return result;
    }

    private static int integerAfter(String line, String marker) {
        int at = line.indexOf(marker);
        if (at < 0) return -1;
        at += marker.length();
        int end = at;
        if (end < line.length() && line.charAt(end) == '-') end++;
        while (end < line.length() && Character.isDigit(line.charAt(end))) end++;
        if (end == at || (end == at + 1 && line.charAt(at) == '-')) return -1;
        try { return Integer.parseInt(line.substring(at, end)); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static void safeState(String message) {
        try {
            Class<?> agent = Class.forName("EntryTraceBootstrap");
            Method state = agent.getMethod("state", String.class);
            state.invoke(null, message);
        } catch (Throwable ignored) { }
    }

    private static void safeFailure(String label, Throwable failure) {
        try {
            Class<?> agent = Class.forName("EntryTraceBootstrap");
            Method log = agent.getMethod("failure", String.class, Throwable.class);
            log.invoke(null, label, failure);
        } catch (Throwable ignored) {
            safeState("HARNESS_FAILURE=1");
        }
    }

    private static final class TraceSnapshot {
        int rx13Length = -1;
        int tx1Count;
        int tx13Count;
        int rx3Count;
        int rx16Count;
        int rx65Count;
        int maxRxSequence;
        int worldDataSequence = -1;
    }

    private static final class ScreenReader {
        private final Field currentScreen;
        private final Map<String,String> aliases;
        private final Canvas fallbackCanvas;
        private Object lastScreen;

        ScreenReader(ClassLoader loader, Properties symbols, Map<String,String> aliases, Canvas fallbackCanvas) {
            this.aliases = aliases;
            this.fallbackCanvas = fallbackCanvas;
            Field found = null;
            try {
                Class<?> canvasType = Class.forName(symbols.getProperty("class.CANVAS").replace('/', '.'), true, loader);
                found = canvasType.getDeclaredField(symbols.getProperty("canvas.currentScreen"));
                found.setAccessible(true);
            } catch (Throwable ignored) { }
            currentScreen = found;
        }

        String alias() {
            Object screen = read();
            if (screen == null) return "NONE";
            String className = screen.getClass().getName().replace('.', '/');
            String alias = aliases.get(className);
            return alias == null ? "UI_OTHER" : alias;
        }

        int classHash() {
            Object screen = read();
            return screen == null ? 0 : screen.getClass().getName().hashCode();
        }

        private Object read() {
            try { lastScreen = currentScreen == null ? fallbackCanvas : currentScreen.get(null); }
            catch (Throwable ignored) { lastScreen = fallbackCanvas; }
            return lastScreen;
        }
    }
}
