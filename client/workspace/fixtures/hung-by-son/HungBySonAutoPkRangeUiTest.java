import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Properties;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;
import org.microemu.MIDletBridge;

/** Pixel-level check of the native PK radius painter in the actual candidate MIDlet. */
public final class HungBySonAutoPkRangeUiTest {
    private static int checks;
    private static final String PORT = "HungBySonModUpLv";
    private static final int GREEN = 0x50FF00;

    private HungBySonAutoPkRangeUiTest() { }

    public static void main(String[] args) throws Exception {
        require(args.length == 3, "candidate.jar auto-attack.properties screenshotsDir");
        File candidate = new File(args[0]);
        File catalog = new File(args[1]);
        File screenshots = new File(args[2]);
        require(candidate.isFile() && catalog.isFile(), "Candidate and native catalog exist");
        Properties p = load(catalog);
        require(hash(candidate).equals(required(p, "output.sha256")), "Candidate hash matches catalog");
        require(screenshots.isDirectory() || screenshots.mkdirs(), "Screenshot directory exists");
        runEmulatorWorker(candidate, catalog, screenshots);
        System.out.println("HungBySonAutoPkRangeUiTest PASS launcherChecks=" + checks);
    }

    /** Keep MicroEmulator's bundled ASM isolated from fixture-host ASM 9. */
    private static void runEmulatorWorker(File candidate, File catalog, File screenshots) throws Exception {
        File host = new File(HungBySonAutoPkRangeUiTest.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        File runtimeHome = new File(screenshots, "pk-range-runtime-home");
        require(runtimeHome.isDirectory() || runtimeHome.mkdirs(), "Runtime home exists");
        File java = new File(new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        ArrayList<String> command = new ArrayList<String>();
        command.add(java.getAbsolutePath()); command.add("-Xverify:all");
        command.add("-Djava.awt.headless=true"); command.add("-Duser.home=" + runtimeHome.getAbsolutePath());
        command.add("-Dsun.reflect.inflationThreshold=10000"); command.add("-cp");
        command.add(host.getAbsolutePath() + File.pathSeparator + microEmulatorPath());
        command.add("HungBySonAutoPkRangeUiTest$EmulatorWorker");
        command.add(candidate.getAbsolutePath()); command.add(catalog.getAbsolutePath());
        command.add(screenshots.getAbsolutePath());
        Process process = new ProcessBuilder(command).inheritIO().start();
        int exit = process.waitFor(); require(exit == 0, "Isolated MicroEmulator worker exit=" + exit);
    }

    private static String microEmulatorPath() throws Exception {
        String configured = System.getProperty("microemulator.jar");
        if (configured != null && new File(configured).isFile()) return new File(configured).getAbsolutePath();
        String[] classpath = System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator));
        for (String entry : classpath) {
            File candidate = new File(entry);
            if (candidate.isFile() && "microemulator.jar".equalsIgnoreCase(candidate.getName()))
                return candidate.getAbsolutePath();
        }
        throw new IllegalStateException("Pass microemulator.jar as -Dmicroemulator.jar");
    }

    /** Runs with only fixture host classes and MicroEmulator. */
    public static final class EmulatorWorker {
        public static void main(String[] args) {
            int exit = 0;
            try { run(args); }
            catch (Throwable failure) { failure.printStackTrace(); exit = 1; }
            System.exit(exit);
        }

        private static void run(String[] args) throws Exception {
            require(args.length == 3, "candidate catalog screenshots");
            final File candidate = new File(args[0]); File screenshots = new File(args[2]);
            Properties p = load(new File(args[1]));
            int radius = Integer.parseInt(p.getProperty("auto.pk.radius", "500"));
            require(radius > 0 && radius < 600, "Catalogued PK radius is positive and fits the viewport probes");
            helper("startEmulator", new Class<?>[]{File.class}, candidate);
            MIDlet midlet=MIDletBridge.getCurrentMIDlet();
            ClassLoader loader=midlet==null?null:midlet.getClass().getClassLoader();
            require(midlet != null && loader != null, "MIDletBridge exposed the candidate's actual class loader");
            // Finish native static initialization and stop normal painting BEFORE replacing world data.
            suppressWorkerCanvas(midlet);
            Object player = installReadyWorld(loader, p);

            Class<?> runtime = Class.forName("luna.features.AutoAttackRuntime", true, loader);
            installRuntime(loader, runtime, player);
            Class<?> access = Class.forName(required(p, "bridge.owner").replace('/', '.'), true, loader);
            Method ready = access.getMethod("farmWorldReady");
            require(((Boolean)ready.invoke(null)).booleanValue(), "Pinned native farm-world gate is ready");

            Class<?> port = Class.forName(PORT, true, loader);
            Method paint = port.getMethod("paintPkRange", new Class<?>[]{
                    Class.forName(graphicsOwner(p), true, loader)});
            Class<?> graphicsType = paint.getParameterTypes()[0];
            int scale = 1;
            setStatic(graphicsType, p.getProperty("auto.pk.range.graphics.scale.field", "b"), Integer.valueOf(scale));
            boolean selectedModePreview = Boolean.parseBoolean(p.getProperty("auto.pk.preview.selected", "false"));

            require(bool(runtime, "playersEnabled") && !bool(runtime, "enabled")
                    && !bool(runtime, "specialEnabled"), "Initial session is PK-selected with both attack toggles off");
            Paint selectedOff = paintImage(paint, graphicsType, screenshots, "pk-range-selected-master-off-special-off.png",
                    240, 320, 1, 600, 600, 600 - radius, 440);
            require((countColor(selectedOff.image, GREEN) > 0) == selectedModePreview,
                    "PK-selected preview with Auto and Special OFF follows the catalog's selected-mode preview flag");
            action(runtime, -116);
            require(bool(runtime, "specialEnabled") && !bool(runtime, "enabled"),
                    "Standalone Special may remain enabled while master Auto is off");
            Paint full = paintImage(paint, graphicsType, screenshots, "pk-range-radius-" + radius + "-scale-1.png",
                    1200, 1200, 1, 600, 600, 0, 0);
            bounds(full.image, 600 - radius, 600 - radius, 600 + radius, 600 + radius, 3,
                    "Scale-one world radius " + radius + " produces a " + (radius * 2)
                            + "px circle centered at 600,600");
            require(pixel(full.image, 600, 600) != GREEN, "Circle remains an outline");

            Paint doubled = paintImage(paint, graphicsType, screenshots, "pk-range-radius-" + radius + "-scale-2.png",
                    2400, 2400, 2, 600, 600, 0, 0);
            bounds(doubled.image, 1200 - radius * 2, 1200 - radius * 2,
                    1200 + radius * 2, 1200 + radius * 2, 4,
                    "Scale two doubles the rendered diameter while retaining " + radius + " world-unit radius");

            Paint clipped = paintImage(paint, graphicsType, screenshots, "pk-range-camera-clip-240x320.png",
                    240, 320, 1, 600, 600, 600 - radius, 440);
            int[] clipBounds = colorBounds(clipped.image, GREEN);
            int expectedVisibleMaxX = (int)Math.ceil(radius - Math.sqrt(radius * radius - 160.0 * 160.0));
            require(clipBounds[0] == 0 && clipBounds[1] <= 1
                    && Math.abs(clipBounds[2] - expectedVisibleMaxX) <= 3 && clipBounds[3] >= 318,
                    "Native 240x320 clip retains the radius-" + radius
                            + " circle edge that intersects the camera viewport; expected right bound near "
                            + expectedVisibleMaxX + ", actual " + clipBounds[2]);

            action(runtime, -116);
            require(!bool(runtime, "specialEnabled") && !bool(runtime, "enabled"), "All attack toggles are off");
            Paint selectedAllOff = paintImage(paint, graphicsType, screenshots, "pk-range-selected-all-off.png",
                    240, 320, 1, 600, 600, 600 - radius, 440);
            require((countColor(selectedAllOff.image, GREEN) > 0) == selectedModePreview,
                    "PK-selected ring remains a preview with Auto and Special OFF only when enabled by the catalog");

            action(runtime, -113);
            require(bool(runtime, "enabled") && bool(runtime, "playersEnabled"), "PK master mode is enabled");
            Paint masterPk = paintImage(paint, graphicsType, screenshots, "pk-range-master-pk.png", 240, 320,
                    1, 600, 600, 600 - radius, 440);
            require(countColor(masterPk.image, GREEN) > 0,
                    "PK master mode paints its in-range circle segment");

            action(runtime, -112);
            require(bool(runtime, "enabled") && !bool(runtime, "playersEnabled"), "Monster-only master mode is selected");
            assertNoRing(paint, graphicsType, screenshots, "pk-range-monster-only.png", 240, 320, radius,
                    "No PK ring in monster-only mode");
            action(runtime, -111);
            require(!bool(runtime, "enabled"), "Master Auto is off again");
            assertNoRing(paint, graphicsType, screenshots, "pk-range-master-off.png", 240, 320, radius,
                    "Monster mode remains without a PK ring after master Auto turns off");
            System.out.println("HungBySonAutoPkRangeUiTest EmulatorWorker PASS checks=" + checks
                    + "; actual MIDlet, radius geometry, camera clip/transform/color and mode guards");
        }

        private static Paint paintImage(Method paint, Class<?> graphicsType, File screenshots,
                String name, int width, int height, int scale, int playerX, int playerY,
                int cameraX, int cameraY) throws Exception {
            setStatic(graphicsType, "b", Integer.valueOf(scale));
            Image image = Image.createImage(width, height);
            Graphics g = image.getGraphics();
            g.setColor(0x111111); g.fillRect(0, 0, width, height);
            g.setColor(0x123456);
            g.translate(-cameraX * scale, -cameraY * scale);
            int tx = g.getTranslateX(), ty = g.getTranslateY(), color = g.getColor();
            int clipX = g.getClipX(), clipY = g.getClipY(), clipW = g.getClipWidth(), clipH = g.getClipHeight();
            setPlayerPosition(paint.getDeclaringClass().getClassLoader(), playerX, playerY);
            Object wrapper = helper("bindNativeGraphics", new Class<?>[]{Class.class, Graphics.class}, graphicsType, g);
            paint.invoke(null, wrapper);
            require(g.getTranslateX() == tx && g.getTranslateY() == ty, "Painter preserves camera translation");
            require(g.getColor() == color, "Painter restores the caller's graphics color");
            require(g.getClipX() == clipX && g.getClipY() == clipY
                    && g.getClipWidth() == clipW && g.getClipHeight() == clipH, "Painter preserves native clip");
            helper("writePng", new Class<?>[]{Image.class, File.class}, image, new File(screenshots, name));
            return new Paint(image);
        }

        private static void assertNoRing(Method paint, Class<?> graphicsType, File screenshots,
                String name, int width, int height, int radius, String message) throws Exception {
            Paint result = paintImage(paint, graphicsType, screenshots, name,
                    width, height, 1, 600, 600, 600 - radius, 440);
            require(countColor(result.image, GREEN) == 0, message);
        }

        private static Object installReadyWorld(ClassLoader loader, Properties p) throws Exception {
            Class<?> entityType = cls(loader, required(p, "native.entity.owner"));
            Class<?> playerType = cls(loader, required(p, "native.player.owner"));
            Object player = allocate(playerType);
            setField(player, entityType, required(p, "native.entity.hp.field"), Integer.valueOf(100));
            setField(player, entityType, required(p, "native.entity.type.field"), Byte.valueOf((byte)0));
            setField(player, entityType, required(p, "native.entity.state.field"), Integer.valueOf(0));
            setField(player, entityType, required(p, "native.entity.excluded.field"), Boolean.FALSE);
            // Entity.H() runs from the emulator canvas before a logged-in world exists.
            // Keep its fake player inert and its native collections valid while startup finishes.
            setField(player, entityType, "dL", Boolean.TRUE);
            setField(player, entityType, "dv", nativeList(loader, "PK UI fixture pending effects"));
            setField(player, entityType, "dw", nativeList(loader, "PK UI fixture pending actions"));
            setField(player, entityType, "k", nativeList(loader, "PK UI fixture skill effects"));
            setField(player, entityType, "aZ", Integer.valueOf(600)); setField(player, entityType, "ba", Integer.valueOf(600));
            setField(player, entityType, "cA", Integer.valueOf(0)); setField(player, entityType, "cw", Byte.valueOf((byte)1));
            Field castField = findField(playerType, required(p, "request.controller.field"));
            setField(player, playerType, castField.getName(), allocate(castField.getType()));
            Field animationField = findField(playerType, "dh");
            setField(player, playerType, animationField.getName(), allocate(animationField.getType()));

            setStatic(cls(loader, required(p, "native.player.singleton.owner")),
                    required(p, "native.player.singleton.field"), player);
            Class<?> mapOwner = cls(loader, required(p, "native.map.owner"));
            Field mapField = findField(mapOwner, required(p, "native.map.field"));
            setStatic(mapOwner, mapField.getName(), allocate(mapField.getType()));
            try { setStatic(mapField.getType(), "u", Byte.valueOf((byte)2)); } catch (NoSuchFieldException ignored) { }
            Class<?> listOwner = cls(loader, required(p, "native.list.owner"));
            setStatic(listOwner, required(p, "native.list.field"), allocate(cls(loader, "et")));

            Class<?> listenerOwner = cls(loader, required(p, "native.listener.owner"));
            Object listener = allocate(descriptorClass(loader, required(p, "native.listener.descriptor")));
            setStatic(listenerOwner, required(p, "native.listener.field"), listener);
            setStatic(cls(loader, required(p, "ui.currentScreen.owner")),
                    required(p, "ui.currentScreen.field"), listener);
            Class<?> popupOwner = cls(loader, required(p, "native.popup.owner"));
            setStatic(popupOwner, required(p, "native.popup.field"),
                    allocate(descriptorClass(loader, required(p, "native.popup.descriptor"))));
            setStatic(cls(loader, required(p, "native.world.entered.owner")),
                    required(p, "native.world.entered.field"), Boolean.TRUE);
            setStatic(cls(loader, required(p, "native.connection.wait.owner")),
                    required(p, "native.connection.wait.field"), Boolean.FALSE);
            Class<?> connectionOwner = cls(loader, required(p, "native.connection.instance.owner"));
            Object connection = allocate(connectionOwner);
            setField(connection, connectionOwner, required(p, "native.connection.connected.field"), Boolean.TRUE);
            setStatic(connectionOwner, required(p, "native.connection.instance.field"), connection);
            setStatic(cls(loader, "luna.generated.HungAutoAttackInput"), "editing", Boolean.FALSE);
            setStatic(cls(loader, pad("lillilli")), pad("liiili"), Boolean.TRUE);
            setStatic(cls(loader, pad("llilil")), pad("liiillli"), Boolean.FALSE);
            setStatic(cls(loader, pad("liilllii")), pad("liiililiili"), null);
            setStatic(cls(loader, required(p, "native.farm.enabled.owner")),
                    required(p, "native.farm.enabled.field"), Boolean.FALSE);
            return player;
        }

        private static Object nativeList(ClassLoader loader, String title) throws Exception {
            Class<?> type = cls(loader, "et");
            try {
                java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(String.class);
                constructor.setAccessible(true); return constructor.newInstance(title);
            } catch (NoSuchMethodException absentStringConstructor) {
                try {
                    java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
                    constructor.setAccessible(true); return constructor.newInstance();
                } catch (NoSuchMethodException absentDefaultConstructor) { return allocate(type); }
            }
        }

        private static void suppressWorkerCanvas(MIDlet midlet) throws Exception {
            Display display=Display.getDisplay(midlet);
            Canvas blank=new Canvas() {
                protected void paint(Graphics graphics) { }
            };
            display.setCurrent(blank);
            for(int i=0;i<40&&display.getCurrent()!=blank;i++)Thread.sleep(25L);
            require(display.getCurrent()==blank,"Fixture suspends native world paint before installing partial test state");
        }

        private static void installRuntime(ClassLoader loader, Class<?> runtime, final Object player) throws Exception {
            Class<?> accessType = cls(loader, "luna.api.AutoAttackAccess");
            Object access = Proxy.newProxyInstance(loader, new Class<?>[]{accessType}, new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if ("ready".equals(name)) return Boolean.TRUE;
                    if ("player".equals(name)) return player;
                    if ("mapId".equals(name) || "zoneId".equals(name)) return Integer.valueOf(1);
                    return defaultValue(method.getReturnType());
                }
            });
            Class<?> settingsType = cls(loader, "luna.api.AutoAttackSettings");
            Object settings = Proxy.newProxyInstance(loader, new Class<?>[]{settingsType}, new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    if ("load".equals(method.getName())) { int[] saved = (int[])args[0]; saved[0] = 1; saved[1] = 650; }
                    return defaultValue(method.getReturnType());
                }
            });
            Class<?> clockType = cls(loader, "luna.api.LunaClock");
            Object clock = Proxy.newProxyInstance(loader, new Class<?>[]{clockType}, new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    return "nowMillis".equals(method.getName()) ? Long.valueOf(1000L) : defaultValue(method.getReturnType());
                }
            });
            runtime.getMethod("installForTest", accessType, settingsType, clockType).invoke(null, access, settings, clock);
        }

        private static void setPlayerPosition(ClassLoader loader, int x, int y) throws Exception {
            Class<?> playerType = cls(loader, pad("lliiil"));
            Object player = getStatic(cls(loader, pad("liiliii")), pad("llilliill"));
            setField(player, playerType, "aZ", Integer.valueOf(x)); setField(player, playerType, "ba", Integer.valueOf(y));
        }

        private static boolean bool(Class<?> runtime, String method) throws Exception {
            return ((Boolean)runtime.getMethod(method).invoke(null)).booleanValue();
        }
        private static void action(Class<?> runtime, int action) throws Exception {
            runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(action));
        }
        private static Class<?> cls(ClassLoader loader, String name) throws Exception {
            return Class.forName(name.replace('/', '.'), true, loader);
        }
        private static Class<?> descriptorClass(ClassLoader loader, String descriptor) throws Exception {
            if (descriptor.charAt(0) != 'L' || descriptor.charAt(descriptor.length() - 1) != ';')
                throw new IllegalArgumentException("Expected object descriptor " + descriptor);
            return cls(loader, descriptor.substring(1, descriptor.length() - 1));
        }
        private static Object allocate(Class<?> type) throws Exception {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe"); Field singleton = unsafeType.getDeclaredField("theUnsafe");
            singleton.setAccessible(true); Object unsafe = singleton.get(null);
            return unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        }
        private static Field findField(Class<?> type, String name) throws Exception {
            for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
                try { Field field = cursor.getDeclaredField(name); field.setAccessible(true); return field; }
                catch (NoSuchFieldException ignored) { }
            }
            throw new NoSuchFieldException(type.getName() + "." + name);
        }
        private static Object getStatic(Class<?> owner, String name) throws Exception {
            Field field = findField(owner, name); return field.get(null);
        }
        private static void setStatic(Class<?> owner, String name, Object value) throws Exception {
            Field field = findField(owner, name); field.set(null, value);
        }
        private static void setField(Object target, Class<?> owner, String name, Object value) throws Exception {
            Field field = findField(owner, name);
            if (field.getType() == Integer.TYPE) field.setInt(target, ((Number)value).intValue());
            else if (field.getType() == Short.TYPE) field.setShort(target, ((Number)value).shortValue());
            else if (field.getType() == Byte.TYPE) field.setByte(target, ((Number)value).byteValue());
            else if (field.getType() == Boolean.TYPE) field.setBoolean(target, ((Boolean)value).booleanValue());
            else field.set(target, value);
        }
        private static Object defaultValue(Class<?> type) {
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
        private static String graphicsOwner(Properties p) {
            String owner = p.getProperty("auto.pk.range.graphics.owner");
            if (owner == null) owner = p.getProperty("graphics.owner");
            return owner == null ? pad("llilll") : owner;
        }
        private static void require(boolean ok, String message) {
            checks++; if (!ok) throw new AssertionError(message);
        }
    }

    private static final class Paint { final Image image; Paint(Image image) { this.image = image; } }

    private static int countColor(Image image, int color) {
        int[] pixels = pixels(image); int count = 0;
        for (int pixel : pixels) if ((pixel & 0xFFFFFF) == color) count++;
        return count;
    }
    private static int pixel(Image image, int x, int y) {
        int[] value = new int[1]; image.getRGB(value, 0, 1, x, y, 1, 1); return value[0] & 0xFFFFFF;
    }
    private static int[] colorBounds(Image image, int color) {
        int width = image.getWidth(), height = image.getHeight(), minX = width, minY = height, maxX = -1, maxY = -1;
        int[] values = pixels(image);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            if ((values[y * width + x] & 0xFFFFFF) == color) {
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
            }
        return new int[]{minX, minY, maxX, maxY};
    }
    private static int[] pixels(Image image) {
        int width = image.getWidth(), height = image.getHeight(); int[] result = new int[width * height];
        image.getRGB(result, 0, width, 0, 0, width, height); return result;
    }
    private static void bounds(Image image, int minX, int minY, int maxX, int maxY, int tolerance, String message) {
        int[] actual = colorBounds(image, GREEN);
        require(Math.abs(actual[0] - minX) <= tolerance && Math.abs(actual[1] - minY) <= tolerance
                && Math.abs(actual[2] - maxX) <= tolerance && Math.abs(actual[3] - maxY) <= tolerance,
                message + "; bounds=" + actual[0] + "," + actual[1] + ".." + actual[2] + "," + actual[3]);
    }
    private static Object helper(String name, Class<?>[] types, Object... values) throws Exception {
        Class<?> host = Class.forName("HungBySonExpInfoBinaryTest");
        Method method = host.getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(null, values);
    }
    private static Properties load(File file) throws Exception {
        Properties result = new Properties(); InputStream input = new FileInputStream(file);
        try { result.load(input); } finally { input.close(); } return result;
    }
    private static String required(Properties p, String key) {
        String value = p.getProperty(key); if (value == null || value.length() == 0) throw new IllegalStateException("Missing " + key); return value;
    }
    private static String hash(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        InputStream input = new FileInputStream(file);
        try { byte[] block = new byte[8192]; for (int n; (n = input.read(block)) >= 0;) digest.update(block, 0, n); }
        finally { input.close(); }
        StringBuilder result = new StringBuilder(); for (byte value : digest.digest()) result.append(String.format("%02X", Integer.valueOf(value & 255)));
        return result.toString();
    }
    private static String pad(String tail) {
        StringBuilder value = new StringBuilder(); while (value.length() + tail.length() < 100) value.append('i'); return value.append(tail).toString();
    }
    private static void require(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
