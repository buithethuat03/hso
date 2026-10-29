import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;
import org.microemu.MIDletBridge;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Regression for drawing the catalogued Auto PK outline in the live world camera transform. */
public final class HungBySonAutoPkWorldRangeTest {
    private static final String OLD_SHA256 = "3AE3D8126E4041F96F19DD6336C1BEE5F5824477946E6A706C83A7119A8A1B46";
    private static final String PORT = "HungBySonModUpLv";
    private static final String MAIN = pad("liilllii");
    private static final String ENTITY = pad("liiiliii");
    private static int checks;

    private HungBySonAutoPkWorldRangeTest() { }

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }

    private static String pad(String tail) {
        StringBuilder value = new StringBuilder();
        while (value.length() + tail.length() < 100) value.append('i');
        return value.append(tail).toString();
    }

    private static Properties load(File file) throws Exception {
        Properties result = new Properties();
        InputStream input = new FileInputStream(file);
        try { result.load(input); } finally { input.close(); }
        return result;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().length() == 0) throw new IllegalArgumentException("Missing catalog key " + key);
        return value.trim();
    }

    private static String hash(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        InputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) >= 0;) digest.update(buffer, 0, count);
        } finally { input.close(); }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02X", Integer.valueOf(value & 255)));
        return result.toString();
    }

    private static ClassNode readClass(File jarFile, String owner) throws Exception {
        JarFile jar = new JarFile(jarFile);
        try {
            JarEntry entry = jar.getJarEntry(owner + ".class");
            if (entry == null) throw new AssertionError("Missing class " + owner + " in " + jarFile);
            InputStream input = jar.getInputStream(entry);
            try {
                ClassNode node = new ClassNode();
                new ClassReader(input).accept(node, 0);
                return node;
            } finally { input.close(); }
        } finally { jar.close(); }
    }

    private static MethodNode findMethod(ClassNode owner, String name, String descriptor) {
        MethodNode found = null;
        for (MethodNode method : owner.methods) if (name.equals(method.name) && descriptor.equals(method.desc)) {
            if (found != null) throw new AssertionError("Duplicate " + owner.name + "." + name + descriptor);
            found = method;
        }
        if (found == null) throw new AssertionError("Missing " + owner.name + "." + name + descriptor);
        return found;
    }

    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction : method.instructions.toArray())
            if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }

    private static boolean call(AbstractInsnNode instruction, int opcode, String owner,
            String name, String descriptor) {
        if (!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != opcode) return false;
        MethodInsnNode method = (MethodInsnNode)instruction;
        return owner.equals(method.owner) && name.equals(method.name) && descriptor.equals(method.desc);
    }

    private static int callCount(List<AbstractInsnNode> code, int opcode, String owner,
            String name, String descriptor) {
        int result = 0;
        for (AbstractInsnNode instruction : code)
            if (call(instruction, opcode, owner, name, descriptor)) result++;
        return result;
    }

    private static int previousCall(List<AbstractInsnNode> code, int from,
            int opcode, String owner, String name, String descriptor) {
        for (int i = from - 1; i >= 0; i--)
            if (call(code.get(i), opcode, owner, name, descriptor)) return i;
        return -1;
    }

    private static int exactCandidateSite(File jar, Properties catalog) throws Exception {
        String world = required(catalog, "auto.pk.range.world.owner");
        String graphics = required(catalog, "auto.pk.range.graphics.owner");
        String worldMethod = catalog.getProperty("auto.pk.range.world.method", "a");
        String worldDesc = required(catalog, "auto.pk.range.world.descriptor");
        String graphicsDesc = "(L" + graphics + ";)V";
        String paintOwner = catalog.getProperty("modulv.port.owner", PORT);
        String cameraOwner = required(catalog, "auto.pk.range.camera.owner");
        String cameraField = required(catalog, "auto.pk.range.camera.field");
        ClassNode node = readClass(jar, world);
        List<AbstractInsnNode> code = real(findMethod(node, worldMethod, worldDesc));
        int entityCall = -1, resetCall = -1, hookCall = -1, validSites = 0, hookCount = 0;
        List<Integer> translateSites = new ArrayList<Integer>();
        for (int i = 0; i < code.size(); i++) {
            AbstractInsnNode instruction = code.get(i);
            if (call(instruction, Opcodes.INVOKESTATIC, ENTITY, "i", graphicsDesc)) entityCall = i;
            if (call(instruction, Opcodes.INVOKEVIRTUAL, graphics, pad("illii"), "(II)V"))
                translateSites.add(Integer.valueOf(i));
            if (call(instruction, Opcodes.INVOKESTATIC, paintOwner, "paintPkRange", graphicsDesc)) {
                hookCall = i;
                hookCount++;
                if (i >= 4 && i + 2 < code.size()
                        && call(code.get(i - 2), Opcodes.INVOKESTATIC, ENTITY, "i", graphicsDesc)
                        && isAload(code.get(i - 1), 1)
                        && isAload(code.get(i + 1), 1)
                        && call(code.get(i + 2), Opcodes.INVOKESTATIC, MAIN, "a", graphicsDesc)) {
                    validSites++;
                    resetCall = i + 2;
                }
            }
        }
        require(hookCount == 1, "Candidate has one world-render PK paint hook (found " + hookCount + ")");
        require(validSites == 1 && hookCall > entityCall && resetCall == hookCall + 2,
                "Single hook is after Entity.i(G), immediately before Main.a(G), before minimap branches");

        require(translateSites.size() == 4 && entityCall > 0,
                "World render keeps its native camera wrapper translations before Entity.i(G)");
        require(hasField(code, translateSites.get(0).intValue(), "bu")
                        && hasField(code, translateSites.get(0).intValue(), "bv")
                        && hasField(code, translateSites.get(1).intValue(), cameraOwner, cameraField)
                        && hasField(code, translateSites.get(1).intValue(), "a")
                        && hasField(code, translateSites.get(1).intValue(), "b"),
                "Native camera setup reads bu/bv and the configured singleton camera a/b fields");
        require(translateSites.get(0).intValue() < entityCall
                        && translateSites.get(1).intValue() < entityCall
                        && translateSites.get(2).intValue() > resetCall
                        && translateSites.get(3).intValue() > resetCall,
                "First two illii calls establish world camera space; final pair belong to minimap");
        require(resetCall < translateSites.get(2).intValue(),
                "Selected Main.a reset precedes both minimap translate paths");
        int firstBranchAfterReset = -1;
        for (int i = resetCall + 1; i < code.size(); i++) {
            int opcode = code.get(i).getOpcode();
            if (opcode >= Opcodes.IFEQ && opcode <= Opcodes.JSR) { firstBranchAfterReset = i; break; }
        }
        require(firstBranchAfterReset > resetCall && hookCall < firstBranchAfterReset,
                "PK painter runs before the first camera-reset/minimap branch");
        return hookCall;
    }

    private static boolean isAload(AbstractInsnNode instruction, int index) {
        return instruction instanceof VarInsnNode && instruction.getOpcode() == Opcodes.ALOAD
                && ((VarInsnNode)instruction).var == index;
    }

    private static boolean hasField(List<AbstractInsnNode> code, int end, String name) {
        int start = Math.max(0, end - 12);
        for (int i = start; i < end; i++)
            if (code.get(i) instanceof FieldInsnNode && name.equals(((FieldInsnNode)code.get(i)).name)) return true;
        return false;
    }

    private static boolean hasField(List<AbstractInsnNode> code, int end, String owner, String name) {
        int start = Math.max(0, end - 12);
        for (int i = start; i < end; i++) {
            if (!(code.get(i) instanceof FieldInsnNode)) continue;
            FieldInsnNode field = (FieldInsnNode)code.get(i);
            if (owner.equals(field.owner) && name.equals(field.name)) return true;
        }
        return false;
    }

    /** The pinned 3AE3 JAR is a negative control: both hooks are in the post-reset minimap branch. */
    private static void verifyOldNegativeControl(File old, Properties catalog) throws Exception {
        require(OLD_SHA256.equalsIgnoreCase(hash(old)), "Negative-control JAR is the pinned 3AE3 release");
        String world = required(catalog, "auto.pk.range.world.owner");
        String graphics = required(catalog, "auto.pk.range.graphics.owner");
        String descriptor = "(L" + graphics + ";)V";
        String owner = catalog.getProperty("modulv.port.owner", PORT);
        MethodNode render = findMethod(readClass(old, world),
                catalog.getProperty("auto.pk.range.world.method", "a"),
                required(catalog, "auto.pk.range.world.descriptor"));
        List<AbstractInsnNode> code = real(render);
        int hooks = 0, afterReset = 0;
        for (int i = 0; i < code.size(); i++) if (call(code.get(i), Opcodes.INVOKESTATIC,
                owner, "paintPkRange", descriptor)) {
            hooks++;
            if (previousCall(code, i, Opcodes.INVOKESTATIC, MAIN, "a", descriptor) >= 0) afterReset++;
        }
        require(hooks == 2 && afterReset == hooks,
                "3AE3 is detected as the regression: its two hooks execute after Main.a camera reset");
        require(!hasCorrectSite(code, owner, descriptor),
                "3AE3 fails the new Entity.i → paint → Main.a callsite contract");
    }

    private static boolean hasCorrectSite(List<AbstractInsnNode> code, String paintOwner, String descriptor) {
        int count = 0;
        for (int i = 4; i + 2 < code.size(); i++)
            if (call(code.get(i), Opcodes.INVOKESTATIC, paintOwner, "paintPkRange", descriptor)
                    && call(code.get(i - 2), Opcodes.INVOKESTATIC, ENTITY, "i", descriptor)
                    && isAload(code.get(i - 1), 1) && isAload(code.get(i + 1), 1)
                    && call(code.get(i + 2), Opcodes.INVOKESTATIC, MAIN, "a", descriptor)) count++;
        return count == 1;
    }

    private static String microPath() throws Exception {
        String configured = System.getProperty("microemulator.jar");
        if (configured != null && new File(configured).isFile()) return new File(configured).getAbsolutePath();
        for (String entry : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            File path = new File(entry);
            if (path.isFile() && "microemulator.jar".equalsIgnoreCase(path.getName())) return path.getAbsolutePath();
        }
        throw new IllegalStateException("Pass -Dmicroemulator.jar=...");
    }

    private static void runPixelWorker(File jar, File catalog, File output, boolean expectCentered)
            throws Exception {
        File host = new File(HungBySonAutoPkWorldRangeTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        File home = new File(output.getParentFile(), "runtime-" + (expectCentered ? "candidate" : "old"));
        if (!home.isDirectory() && !home.mkdirs()) throw new IllegalStateException("Cannot create " + home);
        File java = new File(new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        List<String> command = new ArrayList<String>();
        command.add(java.getAbsolutePath()); command.add("-Xverify:all");
        command.add("-Djava.awt.headless=true"); command.add("-Duser.home=" + home.getAbsolutePath());
        command.add("-Dsun.reflect.inflationThreshold=10000"); command.add("-cp");
        command.add(host.getAbsolutePath() + File.pathSeparator + microPath());
        command.add("HungBySonAutoPkWorldRangeTest$PixelWorker");
        command.add(jar.getAbsolutePath()); command.add(catalog.getAbsolutePath());
        command.add(output.getAbsolutePath()); command.add(expectCentered ? "candidate" : "old");
        Process process = new ProcessBuilder(command).inheritIO().start();
        int exit = process.waitFor();
        require(exit == 0, (expectCentered ? "Candidate" : "3AE3 negative-control") + " pixel worker exit=" + exit);
    }

    public static final class PixelWorker {
        public static void main(String[] args) {
            int status = 0;
            try { run(args); }
            catch (Throwable failure) { failure.printStackTrace(); status = 1; }
            System.exit(status);
        }

        private static void run(String[] args) throws Exception {
            if (args.length != 4) throw new IllegalArgumentException("jar catalog image.png candidate|old");
            File jar = new File(args[0]), catalogFile = new File(args[1]), png = new File(args[2]);
            Properties catalog = load(catalogFile);
            int radius = Integer.parseInt(catalog.getProperty("auto.pk.radius", "500"));
            if (radius <= 0 || radius >= 600) throw new IllegalArgumentException("Invalid PK radius " + radius);
            boolean candidate = "candidate".equals(args[3]);
            Class<?> expWorker = Class.forName("HungBySonExpInfoBinaryTest");
            helper(expWorker, "startEmulator", new Class<?>[]{File.class}, jar);
            MIDlet midlet = MIDletBridge.getCurrentMIDlet();
            if (midlet == null) throw new AssertionError("Candidate MIDlet did not start");
            ClassLoader loader = midlet.getClass().getClassLoader();
            if (loader == null) throw new AssertionError("Candidate MIDlet class loader missing");

            Class<?> rangeUi = Class.forName("HungBySonAutoPkRangeUiTest$EmulatorWorker");
            helper(rangeUi, "suppressWorkerCanvas", new Class<?>[]{MIDlet.class}, midlet);
            Object player = helper(rangeUi, "installReadyWorld", new Class<?>[]{ClassLoader.class, Properties.class},
                    loader, catalog);
            Class<?> runtime = Class.forName("luna.features.AutoAttackRuntime", true, loader);
            helper(rangeUi, "installRuntime", new Class<?>[]{ClassLoader.class, Class.class, Object.class},
                    loader, runtime, player);
            if (!((Boolean)runtime.getMethod("playersEnabled").invoke(null)).booleanValue()
                    || ((Boolean)runtime.getMethod("enabled").invoke(null)).booleanValue())
                throw new AssertionError("Fixture must start PK selected with master OFF");
            runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-116));

            String worldName = required(catalog, "auto.pk.range.world.owner");
            String graphicsName = required(catalog, "auto.pk.range.graphics.owner");
            String playerName = required(catalog, "native.player.owner");
            String playerX = "aZ", playerY = "ba";
            Class<?> worldType = Class.forName(worldName.replace('/', '.'), true, loader);
            Class<?> graphicsType = Class.forName(graphicsName.replace('/', '.'), true, loader);
            Class<?> playerType = Class.forName(playerName.replace('/', '.'), true, loader);
            Image image = Image.createImage(240, 320);
            Graphics raw = image.getGraphics();
            raw.setColor(0x111111); raw.fillRect(0, 0, 240, 320);
            raw.setColor(0x123456);
            Object graphics = helper(expWorker, "bindNativeGraphics",
                    new Class<?>[]{Class.class, Graphics.class}, graphicsType, raw);
            setStatic(graphicsType, catalog.getProperty("auto.pk.range.graphics.scale.field", "b"), Integer.valueOf(1));

            Field playerRef = field(loader.loadClass(required(catalog, "native.player.singleton.owner").replace('/', '.')),
                    required(catalog, "native.player.singleton.field"));
            Object nativePlayer = playerRef.get(null);
            setNumber(nativePlayer, playerType, playerX, 600);
            setNumber(nativePlayer, playerType, playerY, 600);
            setNumber(null, worldType, "bu", 7);
            setNumber(null, worldType, "bv", -11);
            int cameraX = 600 + 7 - radius;

            String cameraOwnerName = required(catalog, "auto.pk.range.camera.owner");
            String cameraFieldName = required(catalog, "auto.pk.range.camera.field");
            String[] cameraXY = catalog.getProperty("auto.pk.range.camera.xy", "a,b").split(",");
            Class<?> cameraOwner = Class.forName(cameraOwnerName.replace('/', '.'), true, loader);
            Field cameraField = field(cameraOwner, cameraFieldName);
            Object camera = cameraField.get(null);
            if (camera == null) {
                camera = allocate(cameraField.getType());
                setStatic(cameraOwner, cameraFieldName, camera);
            }
            setNumber(camera, camera.getClass(), cameraXY[0], cameraX);
            setNumber(camera, camera.getClass(), cameraXY[1], 429);

            Method translate = findMethod(graphicsType, pad("illii"), Integer.TYPE, Integer.TYPE);
            translate.invoke(graphics, Integer.valueOf(7), Integer.valueOf(-11));
            translate.invoke(graphics, Integer.valueOf(-cameraX), Integer.valueOf(-429));
            Class<?> entityType = Class.forName(required(catalog, "entity.owner").replace('/', '.'), true, loader);
            Method marker = findMethod(entityType, "i", graphicsType);
            marker.invoke(null, graphics);

            Class<?> main = Class.forName(pad("liilllii").replace('/', '.'), true, loader);
            Method reset = findMethod(main, "a", graphicsType);
            Object receiver = Modifier.isStatic(reset.getModifiers()) ? null : allocate(main);
            Method paint = Class.forName(PORT, true, loader).getMethod("paintPkRange", graphicsType);
            if (candidate) paint.invoke(null, graphics);
            reset.invoke(receiver, graphics);
            if (!candidate) paint.invoke(null, graphics);

            helper(expWorker, "writePng", new Class<?>[]{Image.class, File.class}, image, png);
            int nearGreen = nearColor(image, 0, 160, 5);
            if (candidate && nearGreen == 0)
                throw new AssertionError("Fixed hook missed the radius-" + radius
                        + " projected world actor edge at screen 0,160");
            if (!candidate && nearGreen != 0)
                throw new AssertionError("3AE3 painted the ring before camera centering instead of after reset");
            System.out.println("PixelWorker " + args[3] + " PASS; projected-world-edge pixels=" + nearGreen);
        }

        private static Object helper(Class<?> owner, String name, Class<?>[] types, Object... args) throws Exception {
            Method method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            try { return method.invoke(null, args); }
            catch (java.lang.reflect.InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Exception) throw (Exception)cause;
                if (cause instanceof Error) throw (Error)cause;
                throw new IllegalStateException(cause);
            }
        }

        private static Method findMethod(Class<?> owner, String name, Class<?> argument) throws Exception {
            for (Class<?> cursor = owner; cursor != null; cursor = cursor.getSuperclass())
                for (Method method : cursor.getDeclaredMethods())
                    if (method.getName().equals(name) && method.getParameterTypes().length == 1
                            && method.getParameterTypes()[0] == argument) {
                        method.setAccessible(true);
                        return method;
                    }
            throw new NoSuchMethodException(owner.getName() + "." + name + "(" + argument.getName() + ")");
        }

        private static Method findMethod(Class<?> owner, String name, Class<?> first, Class<?> second)
                throws Exception {
            for (Class<?> cursor = owner; cursor != null; cursor = cursor.getSuperclass())
                for (Method method : cursor.getDeclaredMethods())
                    if (method.getName().equals(name) && method.getParameterTypes().length == 2
                            && method.getParameterTypes()[0] == first && method.getParameterTypes()[1] == second) {
                        method.setAccessible(true);
                        return method;
                    }
            throw new NoSuchMethodException(owner.getName() + "." + name);
        }

        private static String pad(String tail) {
            StringBuilder value = new StringBuilder();
            while (value.length() + tail.length() < 100) value.append('i');
            return value.append(tail).toString();
        }

        private static Properties load(File file) throws Exception {
            Properties result = new Properties();
            InputStream input = new FileInputStream(file);
            try { result.load(input); } finally { input.close(); }
            return result;
        }

        private static String required(Properties properties, String key) {
            String value = properties.getProperty(key);
            if (value == null || value.trim().length() == 0)
                throw new IllegalArgumentException("Missing catalog key " + key);
            return value.trim();
        }

        private static Field field(Class<?> owner, String name) throws Exception {
            for (Class<?> cursor = owner; cursor != null; cursor = cursor.getSuperclass())
                try { Field result = cursor.getDeclaredField(name); result.setAccessible(true); return result; }
                catch (NoSuchFieldException ignored) { }
            throw new NoSuchFieldException(owner.getName() + "." + name);
        }

        private static void setStatic(Class<?> owner, String name, Object value) throws Exception {
            field(owner, name).set(null, value);
        }

        private static Object allocate(Class<?> type) throws Exception {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field singleton = unsafeType.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
            return unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), type);
        }

        private static void setNumber(Object target, Class<?> owner, String name, long value) throws Exception {
            Field field = field(owner, name);
            if (field.getType() == Byte.TYPE) field.setByte(target, (byte)value);
            else if (field.getType() == Short.TYPE) field.setShort(target, (short)value);
            else if (field.getType() == Integer.TYPE) field.setInt(target, (int)value);
            else if (field.getType() == Long.TYPE) field.setLong(target, value);
            else throw new IllegalArgumentException(field.toString());
        }

        private static int nearColor(Image image, int x, int y, int radius) {
            int count = 0;
            int[] pixel = new int[1];
            for (int yy = Math.max(0, y - radius); yy <= Math.min(image.getHeight() - 1, y + radius); yy++)
                for (int xx = Math.max(0, x - radius); xx <= Math.min(image.getWidth() - 1, x + radius); xx++) {
                    image.getRGB(pixel, 0, 1, xx, yy, 1, 1);
                    if ((pixel[0] & 0xFFFFFF) == 0x50FF00) count++;
                }
            return count;
        }
    }

    private static Object helper(Class<?> owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = owner.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try { return method.invoke(null, args); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new IllegalStateException(cause);
        }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        for (Class<?> cursor = owner; cursor != null; cursor = cursor.getSuperclass())
            try { Field result = cursor.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static void setStatic(Class<?> owner, String name, Object value) throws Exception {
        field(owner, name).set(null, value);
    }

    private static void setNumber(Object target, Class<?> owner, String name, long value) throws Exception {
        Field field = field(owner, name);
        if (field.getType() == Byte.TYPE) field.setByte(target, (byte)value);
        else if (field.getType() == Short.TYPE) field.setShort(target, (short)value);
        else if (field.getType() == Integer.TYPE) field.setInt(target, (int)value);
        else if (field.getType() == Long.TYPE) field.setLong(target, value);
        else throw new IllegalArgumentException(field.toString());
    }

    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        return unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), type);
    }

    private static void runPixel(File jar, File catalog, File screenshot, boolean candidate)
            throws Exception {
        runPixelWorker(jar, catalog, screenshot, candidate);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException(
                "candidate.jar old-3AE3.jar auto-pk.properties screenshotsDir");
        File candidate = new File(args[0]), old = new File(args[1]), screenshots = new File(args[3]);
        Properties catalog = load(new File(args[2]));
        require(candidate.isFile() && old.isFile() && new File(args[2]).isFile(), "Candidate, old release and catalog exist");
        require(screenshots.isDirectory() || screenshots.mkdirs(), "Screenshot directory exists");
        verifyOldNegativeControl(old, catalog);
        exactCandidateSite(candidate, catalog);
        String expected = catalog.getProperty("output.sha256");
        if (expected != null) require(expected.equalsIgnoreCase(hash(candidate)), "Candidate matches Auto PK catalog hash");
        int radius = Integer.parseInt(catalog.getProperty("auto.pk.radius", "500"));
        runPixel(candidate, new File(args[2]), new File(screenshots,
                "pk-world-range-r" + radius + "-centered.png"), true);
        runPixel(old, new File(args[2]), new File(screenshots, "pk-world-range-3ae3-offset.png"), false);
        System.out.println("HungBySonAutoPkWorldRangeTest PASS checks=" + checks
                + "; 3AE3 negative control fails placement and fixed candidate centers on the projected world actor");
    }
}
