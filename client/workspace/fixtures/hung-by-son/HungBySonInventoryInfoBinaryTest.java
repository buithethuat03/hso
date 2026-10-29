import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.app.Headless;
import org.microemu.app.util.DeviceEntry;
import org.microemu.device.j2se.J2SEDevice;
import org.microemu.util.MemoryRecordStoreManager;

/** Verifies the native MOD information row, three-state cycle, RMS restore, and HUD hook. */
public final class HungBySonInventoryInfoBinaryTest {
    private static final String SUPPORT = "HungInventoryInfo";
    private static final String GRAPHICS = nativeName("llilll");
    private static String nativeName(String suffix) {
        StringBuilder value = new StringBuilder();
        while (value.length() + suffix.length() < 100) value.append('i');
        return value.append(suffix).toString();
    }

    private HungBySonInventoryInfoBinaryTest() { }

    public static void main(String[] args) throws Exception {
        require(args.length == 3 || args.length == 4,
                "candidate.jar catalog.properties microemulator.jar [inventory-preview-dir]");
        File candidate = new File(args[0]), catalogFile = new File(args[1]), micro = new File(args[2]);
        require(candidate.isFile() && catalogFile.isFile() && micro.isFile(), "Candidate, catalog and MicroEmulator exist");
        Properties catalog = new Properties();
        InputStream in = new FileInputStream(catalogFile);
        try { catalog.load(in); } finally { in.close(); }
        require(hash(candidate).equals(required(catalog, "output.sha256")), "Candidate hash matches catalog");
        require("ON".equals(required(catalog, "inventory.info.default")), "New default mode is ON");
        require("0=OFF,1=ON,2=COMPACT".equals(required(catalog, "inventory.info.state.values")), "State mapping is stable");
        Map<String,byte[]> entries = read(candidate);

        Common emulator = new Common(null);
        MemoryRecordStoreManager records = new MemoryRecordStoreManager();
        records.init(emulator);
        emulator.setRecordStoreManager(records);
        MIDletBridge.setMicroEmulator(emulator);

        ProbeLoader loader = new ProbeLoader(candidate, entries);
        try {
            Class<?> infoType = loader.loadClass(required(catalog, "hud.info.owner").replace('/', '.'));
            Field nativeFlag = infoType.getField(required(catalog, "hud.info.field"));
            Object module = infoType.getConstructor().newInstance();
            Class<?> support = loader.loadClass(SUPPORT);
            Method mode = support.getMethod("mode");
            Method label = support.getMethod("menuLabel");
            Method line = support.getMethod("inventoryLine");
            Method action = infoType.getMethod("onAction", Integer.TYPE, Integer.TYPE);

            require(((Integer)mode.invoke(null)).intValue() == 1, "Old native ON flag seeds default ON");
            require("Hiện thông tin [BẬT]".equals(label.invoke(null)), "Menu label reports ON");
            require(line.invoke(null) == null, "No inventory line is fabricated before world/player/capacity readiness");

            require(Boolean.TRUE.equals(action.invoke(module, Integer.valueOf(-77), Integer.valueOf(0))), "Native -77 action handled");
            require(((Integer)mode.invoke(null)).intValue() == 2 && !nativeFlag.getBoolean(null), "ON cycles to COMPACT and hides old native rows");
            require("Hiện thông tin [GIẢN LƯỢC]".equals(label.invoke(null)), "Menu label reports COMPACT");
            require(Boolean.TRUE.equals(action.invoke(module, Integer.valueOf(-77), Integer.valueOf(0))), "Second native -77 action handled");
            require(((Integer)mode.invoke(null)).intValue() == 0 && !nativeFlag.getBoolean(null), "COMPACT cycles to OFF");
            require("Hiện thông tin [TẮT]".equals(label.invoke(null)), "Menu label reports OFF");
            require(Boolean.TRUE.equals(action.invoke(module, Integer.valueOf(-77), Integer.valueOf(0))), "Third native -77 action handled");
            require(((Integer)mode.invoke(null)).intValue() == 1 && nativeFlag.getBoolean(null), "OFF cycles to ON and restores native rows");

            Method cycle = support.getMethod("cycle");
            cycle.invoke(null); // Persist COMPACT.
            require(((Integer)mode.invoke(null)).intValue() == 2, "COMPACT is saved after the cycle");
            verifyMenuRow(loader, catalog, module, label);
            verifyHudHook(entries, catalog);

            ProbeLoader restored = new ProbeLoader(candidate, entries);
            try {
                Class<?> restoredInfo = restored.loadClass(required(catalog, "hud.info.owner").replace('/', '.'));
                Class<?> restoredSupport = restored.loadClass(SUPPORT);
                Field restoredFlag = restoredInfo.getField(required(catalog, "hud.info.field"));
                int restoredMode = ((Integer)restoredSupport.getMethod("mode").invoke(null)).intValue();
                require(restoredMode == 2 && !restoredFlag.getBoolean(null), "RMS restores COMPACT in a fresh class loader");
            } finally { restored.close(); }
        } finally { loader.close(); }
        if (args.length == 4) {
            File host = new File(HungBySonInventoryInfoBinaryTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File java = new File(new File(System.getProperty("java.home"), "bin"), System.getProperty("os.name").contains("Windows") ? "java.exe" : "java");
            List<String> command = new ArrayList<String>();
            Collections.addAll(command, java.getAbsolutePath(), "-Xverify:all", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8",
                    "-cp", host.getAbsolutePath()+File.pathSeparator+micro.getAbsolutePath(),
                    "HungBySonInventoryInfoBinaryTest$PreviewWorker", candidate.getAbsolutePath(), catalogFile.getAbsolutePath(), args[3]);
            require(new ProcessBuilder(command).inheritIO().start().waitFor() == 0, "Isolated native inventory preview passed");
        }
        System.out.println("HungBySonInventoryInfoBinaryTest PASS");
    }

    public static final class PreviewWorker {
        public static void main(String[] args) {
            try { renderPreviews(new File(args[0]), new File(args[1]), new File(args[2])); System.exit(0); }
            catch (Throwable error) { error.printStackTrace(); System.exit(1); }
        }
    }

    private static void renderPreviews(File candidate, File catalogFile, File screenshots) throws Exception {
        if (!screenshots.isDirectory() && !screenshots.mkdirs())
            throw new IOException("Cannot create preview directory " + screenshots);
        Properties catalog = new Properties();
        InputStream in = new FileInputStream(catalogFile);
        try { catalog.load(in); } finally { in.close(); }
        Common emulator = startEmulator(candidate);
        javax.microedition.midlet.MIDlet active = MIDletBridge.getCurrentMIDlet();
        require(active != null, "MicroEmulator starts the candidate MIDlet for native font preview");
        ClassLoader loader = active.getClass().getClassLoader();
        Class<?> runtime = Class.forName("luna.features.AutoAttackRuntime", true, loader);
        Object frameLock = runtime.getMethod("requestLock").invoke(null);
        synchronized (frameLock) {
        Thread.sleep(100L);
        installPreviewWorld(loader, catalog);

        Class<?> info = loader.loadClass(required(catalog, "hud.info.owner").replace('/', '.'));
        setStatic(info, required(catalog, "hud.info.field"), Boolean.TRUE);
        Class<?> support = loader.loadClass(SUPPORT);
        Method mode = support.getMethod("mode"), cycle = support.getMethod("cycle");
        Class<?> graphicsType = loader.loadClass(GRAPHICS.replace('/', '.'));
        Method draw = support.getMethod("draw", graphicsType);
        require(((Integer)mode.invoke(null)).intValue() == 1, "Preview starts in ON mode");
        renderOne(loader, graphicsType, draw, screenshots, "inventory-on.png", true);
        cycle.invoke(null);
        require(((Integer)mode.invoke(null)).intValue() == 2, "Preview reaches COMPACT mode");
        Method nativeDraw = info.getDeclaredMethod(required(catalog, "inventory.info.hud.method"), graphicsType);
        nativeDraw.setAccessible(true);
        renderOne(loader, graphicsType, nativeDraw, screenshots, "inventory-compact.png", true);
        cycle.invoke(null);
        require(((Integer)mode.invoke(null)).intValue() == 0, "Preview reaches OFF mode");
        renderOne(loader, graphicsType, nativeDraw, screenshots, "inventory-off.png", false);
        System.out.println("INVENTORY_PREVIEW=PASS; 240x320; logical=6,110; native yellow font; line=Hành trang: 12/30");
        if (emulator == null) throw new IllegalStateException("MicroEmulator preview was not initialized");
        System.exit(0);
        }
    }

    private static void renderOne(ClassLoader loader, Class<?> graphicsType, Method draw, File screenshots,
                                  String name, boolean expectedLine) throws Exception {
        Image image = Image.createImage(240, 320);
        Graphics graphics = image.getGraphics();
        graphics.setColor(0xFFFFFF); graphics.fillRect(0, 0, 240, 320);
        Object wrapper = allocate(graphicsType);
        boolean bound = false;
        for (Field field : graphicsType.getFields()) if (field.getType() == Graphics.class) {
            field.set(wrapper, graphics); bound = true; break;
        }
        require(bound, "Native graphics wrapper binds to MicroEmulator image");
        draw.invoke(null, wrapper);
        int[] pixels = new int[240 * 320]; image.getRGB(pixels, 0, 240, 0, 0, 240, 320);
        int visible = 0, yellow = 0;
        for (int y = 100; y < 132; y++) for (int x = 0; x < 210; x++) {
            int color = pixels[y * 240 + x] & 0xFFFFFF;
            if (color != 0xFFFFFF) visible++;
            int red = color >>> 16, green = (color >>> 8) & 255, blue = color & 255;
            if (red > 160 && green > 105 && blue < 155 && Math.abs(red-green) < 80) yellow++;
        }
        BufferedImage rendered = new BufferedImage(240, 320, BufferedImage.TYPE_INT_ARGB);
        rendered.setRGB(0, 0, 240, 320, pixels, 0, 240);
        ImageIO.write(rendered, "png", new File(screenshots, name));
        require(expectedLine ? visible > 0 && yellow > 0 : visible == 0,
                name + " visible=" + visible + " yellow=" + yellow);
        System.out.println(name + " visible=" + visible + " yellow=" + yellow);
    }

    private static void installPreviewWorld(ClassLoader loader, Properties catalog) throws Exception {
        String entityOwner = required(catalog, "native.entity.owner");
        Class<?> entity = loader.loadClass(entityOwner.replace('/', '.'));
        Class<?> playerType = loader.loadClass(required(catalog, "native.player.owner").replace('/', '.'));
        Object player = allocate(playerType);
        setField(player, entity, required(catalog, "native.entity.hp.field"), Integer.valueOf(100));
        setField(player, entity, required(catalog, "native.entity.state.field"), Integer.valueOf(2));
        setField(player, entity, required(catalog, "native.entity.type.field"), Byte.valueOf((byte)0));
        setField(player, entity, required(catalog, "native.entity.excluded.field"), Boolean.FALSE);
        setField(player, entity, "aZ", Integer.valueOf(0)); setField(player, entity, "ba", Integer.valueOf(0));
        Class<?> castType = loader.loadClass(required(catalog, "native.cast.owner").replace('/', '.'));
        Object cast = allocate(castType);
        setField(cast, castType, required(catalog, "request.tag.field"), Integer.valueOf(0));
        setField(player, entity, "di", cast);
        setField(player, entity, "dh", allocate(findField(entity, "dh").getType()));
        setStaticFromCatalog(loader, catalog, "native.player.singleton", player);

        Class<?> mapOwner = loader.loadClass(required(catalog, "native.map.owner").replace('/', '.'));
        Field mapField = findField(mapOwner, required(catalog, "native.map.field"));
        mapField.setAccessible(true);
        Object map = allocate(mapField.getType());
        setField(map, mapField.getType(), "d", Integer.valueOf(1));
        mapField.set(null, map);
        setStatic(mapField.getType(), "u", Byte.valueOf((byte)2));

        Class<?> vectorType = loader.loadClass("et");
        Object entityList = allocate(vectorType);
        setStaticFromCatalog(loader, catalog, "native.list", entityList);
        Class<?> listenerOwner = loader.loadClass(required(catalog, "native.listener.owner").replace('/', '.'));
        Object listener = allocate(findField(listenerOwner, required(catalog, "native.listener.field")).getType());
        setStaticFromCatalog(loader, catalog, "native.listener", listener);
        setStaticFromCatalog(loader, catalog, "ui.currentScreen", listener);
        Class<?> popupOwner = loader.loadClass(required(catalog, "native.popup.owner").replace('/', '.'));
        Object popup = allocate(findField(popupOwner, required(catalog, "native.popup.field")).getType());
        setStaticFromCatalog(loader, catalog, "native.popup", popup);
        setStaticFromCatalog(loader, catalog, "native.world.entered", Boolean.TRUE);
        setStaticFromCatalog(loader, catalog, "native.connection.wait", Boolean.FALSE);

        Class<?> connectionOwner = loader.loadClass(required(catalog, "native.connection.instance.owner").replace('/', '.'));
        Object connection = allocate(connectionOwner);
        setField(connection, connectionOwner, required(catalog, "native.connection.connected.field"), Boolean.TRUE);
        setStaticFromCatalog(loader, catalog, "native.connection.instance", connection);
        setStaticFromCatalog(loader, catalog, "ui.activeDialog", null);
        Class<?> input = loader.loadClass("luna.generated.HungAutoAttackInput");
        setStatic(input, "editing", Boolean.FALSE);

        Object bag = allocate(vectorType);
        Method add = vectorType.getMethod(required(catalog, "native.list.add.method"), Object.class);
        for (int i = 0; i < 12; i++) add.invoke(bag, new Object());
        setStaticFromCatalog(loader, catalog, "inventory.info.inventory.vector", bag);
        setStaticFromCatalog(loader, catalog, "inventory.info.inventory.capacity", Short.valueOf((short)30));
        Class<?> access = loader.loadClass("luna.generated.HungAutoAttackAccess");
        require(Boolean.TRUE.equals(access.getMethod("farmWorldReady").invoke(null)),
                "Native access accepts the prepared connected world, player, and inventory");
    }

    private static Common startEmulator(File jar) throws Exception {
        Headless headless = new Headless();
        Field field = Headless.class.getDeclaredField("emulator"); field.setAccessible(true);
        Common emulator = (Common)field.get(headless);
        MemoryRecordStoreManager records = new MemoryRecordStoreManager();
        records.init(emulator); emulator.setRecordStoreManager(records); MIDletBridge.setMicroEmulator(emulator);
        ArrayList<String> options = new ArrayList<String>();
        options.add("--rms"); options.add("memory"); options.add(jar.getAbsolutePath());
        DeviceEntry device = new DeviceEntry("Default device", null,
                "org/microemu/device/default/device.xml", true, false);
        require(emulator.initParams(options, device, J2SEDevice.class), "MicroEmulator initialization");
        emulator.initMIDlet(true); Thread.sleep(800L);
        return emulator;
    }

    private static Object allocate(Class<?> type) throws Exception {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException missing) {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field singleton = unsafeType.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
            return unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), type);
        }
    }

    private static Field findField(Class<?> owner, String name) throws Exception {
        for (Class<?> type = owner; type != null; type = type.getSuperclass())
            try { return type.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static void setField(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = findField(owner, name); field.setAccessible(true);
        if (field.getType() == Integer.TYPE) field.setInt(target, ((Number)value).intValue());
        else if (field.getType() == Short.TYPE) field.setShort(target, ((Number)value).shortValue());
        else if (field.getType() == Byte.TYPE) field.setByte(target, ((Number)value).byteValue());
        else if (field.getType() == Boolean.TYPE) field.setBoolean(target, ((Boolean)value).booleanValue());
        else field.set(target, value);
    }

    private static void setStaticFromCatalog(ClassLoader loader, Properties catalog, String key, Object value)
            throws Exception {
        Class<?> owner = loader.loadClass(required(catalog, key + ".owner").replace('/', '.'));
        setStatic(owner, required(catalog, key + ".field"), value);
    }

    private static void setStatic(Class<?> owner, String name, Object value) throws Exception {
        Field field = findField(owner, name); field.setAccessible(true);
        if (field.getType() == Boolean.TYPE) field.setBoolean(null, ((Boolean)value).booleanValue());
        else if (field.getType() == Byte.TYPE) field.setByte(null, ((Number)value).byteValue());
        else if (field.getType() == Short.TYPE) field.setShort(null, ((Number)value).shortValue());
        else field.set(null, value);
    }

    private static void verifyMenuRow(ClassLoader loader, Properties catalog, Object module, Method label) throws Exception {
        Class<?> menuType = loader.loadClass("et");
        Object menu = menuType.getConstructor().newInstance();
        Method addMenu = module.getClass().getMethod("addMenu", menuType);
        addMenu.invoke(module, menu);
        require(((Integer)menuType.getMethod("c").invoke(menu)).intValue() == 1, "Native info module adds one menu row");
        Object row = menuType.getMethod("a", Integer.TYPE).invoke(menu, Integer.valueOf(0));
        String wanted = (String)label.invoke(null);
        boolean found = false;
        for (Class<?> type = row.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != String.class) continue;
                field.setAccessible(true);
                if (wanted.equals(field.get(row))) found = true;
            }
        }
        require(found, "Native MOD row displays the current three-state label");
    }

    private static void verifyHudHook(Map<String,byte[]> entries, Properties catalog) {
        String owner = required(catalog, "inventory.info.hud.owner");
        String name = required(catalog, "inventory.info.hud.method");
        String desc = required(catalog, "inventory.info.hud.descriptor");
        ClassNode type = node(entries.get(owner + ".class"));
        MethodNode render = method(type, name, desc);
        boolean draw = false;
        for (AbstractInsnNode instruction : render.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC && call.owner.equals(SUPPORT)
                        && call.name.equals("draw") && call.desc.equals("(L" + GRAPHICS + ";)V")) draw = true;
            }
        }
        require(draw, "Native HUD render calls the inventory row");
        boolean beforeGate = false;
        for (AbstractInsnNode instruction : render.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (field.owner.equals(required(catalog, "hud.info.owner"))
                        && field.name.equals(required(catalog, "hud.info.field"))) break;
            }
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode)instruction).owner.equals(SUPPORT)
                    && ((MethodInsnNode)instruction).name.equals("draw")) beforeGate = true;
        }
        require(beforeGate, "Inventory draws before the old detail gate so COMPACT remains visible");
        require("6,110".equals(required(catalog, "inventory.info.hud.position.logical")), "HUD row uses the reviewed logical coordinates");
    }

    private static ClassNode node(byte[] bytes) {
        require(bytes != null, "Missing class entry");
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        return type;
    }

    private static MethodNode method(ClassNode type, String name, String desc) {
        for (MethodNode method : type.methods) if (method.name.equals(name) && method.desc.equals(desc)) return method;
        throw new IllegalStateException("Missing method " + type.name + "." + name + desc);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        require(value != null && value.length() > 0, "Missing catalog property " + key);
        return value;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream input = new FileInputStream(file);
        try {
            byte[] bytes = new byte[8192];
            for (int n; (n = input.read(bytes)) != -1;) digest.update(bytes, 0, n);
        } finally { input.close(); }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format("%02X", Integer.valueOf(b & 255)));
        return result.toString();
    }

    private static Map<String,byte[]> read(File file) throws Exception {
        Map<String,byte[]> result = new LinkedHashMap<String,byte[]>();
        JarFile jar = new JarFile(file);
        try {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                require(!result.containsKey(entry.getName()), "Duplicate JAR entry " + entry.getName());
                InputStream input = jar.getInputStream(entry);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try {
                    byte[] bytes = new byte[8192];
                    for (int n; (n = input.read(bytes)) != -1;) output.write(bytes, 0, n);
                } finally { input.close(); }
                result.put(entry.getName(), output.toByteArray());
            }
        } finally { jar.close(); }
        return result;
    }

    private static final class ProbeLoader extends URLClassLoader {
        private final Map<String,byte[]> entries;

        ProbeLoader(File jar, Map<String,byte[]> entries) throws Exception {
            super(new URL[] { jar.toURI().toURL() }, HungBySonInventoryInfoBinaryTest.class.getClassLoader());
            this.entries = entries;
        }

        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            String path = name.replace('.', '/') + ".class";
            if (!entries.containsKey(path)) return super.loadClass(name, resolve);
            Class<?> type = findLoadedClass(name);
            if (type == null) type = findClass(name);
            if (resolve) resolveClass(type);
            return type;
        }

        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = entries.get(name.replace('.', '/') + ".class");
            if (bytes == null) return super.findClass(name);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
