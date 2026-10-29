import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.midlet.MIDlet;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.app.Headless;
import org.microemu.app.util.DeviceEntry;
import org.microemu.device.j2se.J2SEDevice;
import org.microemu.util.MemoryRecordStoreManager;

/** Actual launcher, per-slot EXP rate, info icon/modal, and Static lifecycle checks. */
public final class HungBySonExpInfoX3Test {
    private static final String SINGLE_SHA = "3CB0DBA1EC67F0687057AEB1533C025A7858D85DC11B2A246097A029C053FCCF";
    private static final String SHARED = "luna/generated/ExpEstimateSharedRmsStore";
    private static int checks;

    public static void main(String[] args) throws Exception {
        require(args.length == 5, "candidate.jar single.jar info-catalog.properties microemulator.jar screenshotsDir");
        File candidate = new File(args[0]);
        File single = new File(args[1]);
        File catalog = new File(args[2]);
        File micro = new File(args[3]);
        File screenshots = new File(args[4]);
        Properties p = load(catalog);
        require(candidate.isFile() && single.isFile() && micro.isFile(), "Candidate, source, and MicroEmulator exist");
        require(SINGLE_SHA.equals(required(p, "single.sha256")) && SINGLE_SHA.equals(hash(single)),
                "Pinned X1 source SHA matches the fixture catalog");
        require(hash(candidate).equals(required(p, "output.sha256")), "Candidate SHA matches the fixture catalog");
        require("true".equals(required(p, "modal.guard.pointer.only")), "Modal contract is pointer-only");
        require("12".equals(required(p, "icon.size")), "Info icon is exactly 12px");
        require("Xác nhận".equals(required(p, "dialog.button.label")), "Native confirmation label is retained");
        require("-1".equals(required(p, "dialog.close.command")), "Native confirmation command is retained");
        require("luna/generated/ExpInfoBridge".equals(required(p, "ui.owner")), "Info bridge owner is catalogued");
        require("luna/generated/ExpInfoModalGuard".equals(required(p, "modal.guard.owner")), "Modal guard owner is catalogued");
        require("luna/generated/ExpEstimateSharedRmsStore".equals(required(p, "exp.cache.shared.owner")), "Shared EXP backend is catalogued");
        checkIconResource(single, candidate, p);
        screenshots.mkdirs();
        runWorker(candidate, single, catalog, micro, screenshots);
        System.out.println("X3_EXP_INFO_VERIFIED=true");
        System.out.println("X3_EXP_RATE_ISOLATION_VERIFIED=true");
        System.out.println("X3_EXP_INFO_LIFECYCLE_VERIFIED=true");
        System.out.println("HungBySonExpInfoX3Test PASS checks=" + checks);
    }

    /** The runtime process gets only this fixture's classes and MicroEmulator, never host ASM 9. */
    private static void runWorker(File candidate, File single, File catalog, File micro, File screenshots) throws Exception {
        File host = new File(HungBySonExpInfoX3Test.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File home = new File(screenshots, "runtime-home"); home.mkdirs();
        File java = new File(new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name").toLowerCase().contains("windows") ? "java.exe" : "java");
        ArrayList<String> command = new ArrayList<String>();
        command.add(java.getAbsolutePath()); command.add("-Xverify:all"); command.add("-Djava.awt.headless=true");
        command.add("-Duser.home=" + home.getAbsolutePath()); command.add("-Dsun.reflect.inflationThreshold=10000");
        command.add("-cp"); command.add(host.getAbsolutePath() + File.pathSeparator + micro.getAbsolutePath());
        command.add("HungBySonExpInfoX3Test$Worker");
        command.add(candidate.getAbsolutePath()); command.add(single.getAbsolutePath());
        command.add(catalog.getAbsolutePath()); command.add(micro.getAbsolutePath()); command.add(screenshots.getAbsolutePath());
        Process process = new ProcessBuilder(command).inheritIO().start();
        require(process.waitFor() == 0, "Isolated MicroEmulator worker passed");
    }

    public static final class Worker {
        public static void main(String[] args) {
            try { run(args); System.out.println("X3_EXP_INFO_WORKER PASS checks=" + checks); System.exit(0); }
            catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        }

        private static void run(String[] args) throws Exception {
            require(args.length == 5, "candidate single catalog micro screenshots");
            File candidate = new File(args[0]); File single = new File(args[1]);
            Properties p = load(new File(args[2])); File screenshots = new File(args[4]); screenshots.mkdirs();
            require(SINGLE_SHA.equals(required(p, "single.sha256")) && SINGLE_SHA.equals(hash(single)), "Worker source hash is pinned");
            require(hash(candidate).equals(required(p, "output.sha256")), "Worker candidate hash is pinned");
            Common emulator = startEmulator(candidate);
            MIDlet launcher = MIDletBridge.getCurrentMIDlet();
            require(launcher != null && "MrQuyet.MrQuyet".equals(launcher.getClass().getName()), "Actual X3 launcher starts");
            ClassLoader loader = launcher.getClass().getClassLoader();
            Class<?> displayType = Class.forName("lib.Display", true, loader);
            Display display = (Display)field(displayType, "d").get(null);
            require(display != null && display.getCurrent() instanceof List, "Launcher displays its native client menu");
            List menu = (List)display.getCurrent();
            require(menu.size() == 3, "Launcher has three virtual clients");
            Object[] virtuals = virtuals(launcher); Object[] canvases = new Object[3];
            Method select = launcher.getClass().getMethod("commandAction", Command.class, Displayable.class);
            for (int slot = 0; slot < 3; slot++) {
                menu.setSelectedIndex(slot, true);
                select.invoke(launcher, new Command("Select", Command.OK, 0), menu);
                awaitCanvas(display);
                virtuals = virtuals(launcher);
                require(virtuals[slot] != null, "Launcher created virtual client " + (slot + 1));
                canvases[slot] = display.getCurrent();
                String expected = slot == 0 ? "TemMidlet" : slot == 1 ? "b.TemMidlet" : "c.TemMidlet";
                require(expected.equals(virtuals[slot].getClass().getName()), "Correct virtual namespace for slot " + (slot + 1));
                if (slot < 2) {
                    key(canvases[slot], "keyPressed", 42);
                    awaitMenu(display);
                    key(canvases[slot], "keyReleased", 42);
                }
            }
            virtuals = virtuals(launcher);
            for (int slot = 0; slot < 3; slot++) require(virtuals[slot] != null && canvases[slot] != null,
                    "All three actual virtual clients remain allocated " + (slot + 1));

            Slot[] slots = new Slot[3];
            for (int slot = 0; slot < 3; slot++) slots[slot] = new Slot(slot, loader, p, screenshots, single);
            for (int slot = 0; slot < 3; slot++) slots[slot].verifyFakeRate();
            verifyFakeRateIsolation(slots);
            for (int slot = 0; slot < 3; slot++) slots[slot].seedLifecycleRate();
            for (int slot = 0; slot < 3; slot++) slots[slot].prepareNativeWorld();
            byte[] sharedBefore = seedSharedExp(loader);
            for (int slot = 0; slot < 3; slot++) slots[slot].renderIconAndOpen();
            for (int slot = 0; slot < 3; slot++) slots[slot].verifyModalProtection();
            for (int slot = 0; slot < 3; slot++) slots[slot].seedLifecycleRate();
            slots[0].verifyClearIsolation(slots[1], slots[2], sharedBefore);
            slots[1].confirmAndClose(); slots[2].confirmAndClose();
            require(!slots[0].owned(), "Lifecycle-cleared slot no longer owns its modal");
            checkDistinct(slots[0].guard, slots[1].guard, slots[2].guard);
            snapshot(emulator, new File(screenshots, "launcher-after-x3-exp-info.png"));
        }

        private static final class Slot {
            final int index; final ClassLoader loader; final Properties p; final File screenshots;
            final String prefix; final Class<?> engine, info, guard, rate, worldManager, playerType;
            Object player; Object screen, dialog; int guardLifecycleId = -1, engineLifecycleId = -1, rateLifecycleId = -1;
            final File single; final Object canvas;
            Slot(int index, ClassLoader loader, Properties p, File screenshots, File single) throws Exception {
                this.index = index; this.loader = loader; this.p = p; this.screenshots = screenshots;
                this.single = single;
                prefix = index == 0 ? "" : index == 1 ? "b/" : "c/";
                engine = cls(loader, prefix + required(p, "pointer.press.owner"));
                info = cls(loader, prefix + required(p, "ui.owner"));
                guard = cls(loader, prefix + required(p, "modal.guard.owner"));
                rate = cls(loader, prefix + "luna/features/ExpRateSupport");
                worldManager = cls(loader, required(p, "slot." + index + ".main.owner"));
                playerType = descriptorClass(loader, required(p, "slot." + index + ".main.descriptor"));
                canvas = field(cls(loader, prefix + "com/silverknight/a"), "c").get(null);
                require(canvas != null && engine.isInstance(canvas), "Slot " + (index + 1) + " native key callback receiver exists");
                Method reset = rate.getMethod("reset"); reset.invoke(null);
                guardLifecycleId = lifecycleId(single, index, required(p, "modal.guard.owner"));
                engineLifecycleId = lifecycleId(single, index, required(p, "pointer.press.owner"));
                rateLifecycleId = lifecycleId(single, index, "luna/features/ExpRateSupport");
            }

            void verifyFakeRate() throws Exception {
                Method gain = rate.getMethod("gain", Integer.TYPE, Long.TYPE);
                Method getRate = rate.getMethod("rate", Long.TYPE);
                rate.getMethod("reset").invoke(null);
                gain.invoke(null, Integer.valueOf(index + 1), Long.valueOf(1000L));
                long expected = (index + 1) * 12L;
                require(((Long)getRate.invoke(null, Long.valueOf(301000L))).longValue() == expected,
                        "Slot " + (index + 1) + " synthetic EXP/hour is isolated at " + expected);
            }

            void seedLifecycleRate() throws Exception {
                long first = System.currentTimeMillis() - 300000L;
                rate.getMethod("reset").invoke(null);
                rate.getMethod("gain", Integer.TYPE, Long.TYPE).invoke(null, Integer.valueOf(index + 1), Long.valueOf(first));
            }

            void prepareNativeWorld() throws Exception {
                Field gameField = field(engine, pad("liiiliiiiil"));
                screen = gameField.get(null);
                if (screen == null) { screen = allocate(gameField.getType()); gameField.set(null, screen); }
                field(engine, pad("liiiillllli")).set(null, screen);
                player = field(worldManager, required(p, "slot." + index + ".main.field")).get(null);
                if (player == null) {
                    player = allocate(playerType);
                    field(worldManager, required(p, "slot." + index + ".main.field")).set(null, player);
                }
                setNative(player, required(p, "slot." + index + ".id.field"), Integer.valueOf(321 + index));
                setNative(player, required(p, "slot." + index + ".level.field"), Short.valueOf((short)70));
                setNative(player, required(p, "slot." + index + ".progress.field"), Short.valueOf((short)500));
                field(worldManager, pad("lliilliil")).setBoolean(null, false);
                field(engine, required(p, "dialog.active.field")).set(null, null);
                field(engine, pad("liiililiill")).set(null, null);
                setOptionalStatic(cls(loader, prefix + p.getProperty("icon.wait.owner")), p.getProperty("icon.wait.field"), Boolean.FALSE);
                Object menu = optionalStatic(engine, p.getProperty("icon.menu.field"));
                if (menu != null) field(menu.getClass(), required(p, "icon.menu.active.field")).setBoolean(menu, false);
                require(((Boolean)info.getMethod("ready").invoke(null)).booleanValue(), "Slot " + (index + 1) + " native world is ready");
            }

            void renderIconAndOpen() throws Exception {
                Class<?> graphicsType = cls(loader, required(p, "slot." + index + ".graphics.owner"));
                Class<?> fontType = cls(loader, required(p, "slot." + index + ".font.owner"));
                Class<?> estimateBridge = cls(loader, required(p, "slot." + index + ".bridge.owner"));
                Object font = firstStatic(fontType, fontType);
                require(font != null, "Slot " + (index + 1) + " native font initialized");
                Object image = newSurface(loader, 180, 100);
                Object graphics = image.getClass().getMethod("getGraphics").invoke(image);
                graphics.getClass().getMethod("setColor", Integer.TYPE).invoke(graphics, Integer.valueOf(0xFFFFFF));
                graphics.getClass().getMethod("fillRect", Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE)
                        .invoke(graphics, Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(180), Integer.valueOf(100));
                Object nativeGraphics = bindNativeGraphics(graphicsType, graphics);
                Method draw = estimateBridge.getMethod("drawEstimate", graphicsType, fontType, Integer.TYPE, Integer.TYPE);
                draw.invoke(null, nativeGraphics, font, Integer.valueOf(0), Integer.valueOf(0));
                require(((Boolean)info.getField("painted").get(null)).booleanValue(), "Slot " + (index + 1) + " native EXP draw paints info icon");
                int x = info.getField("x").getInt(null), y = info.getField("y").getInt(null);
                boolean compact = field(engine, pad("liliiliil")).getBoolean(null);
                String[] bounds = required(p, compact ? "icon.bounds.compact" : "icon.bounds.normal").split(",");
                require(x == Integer.parseInt(bounds[0]) && y == Integer.parseInt(bounds[1]), "Slot " + (index + 1) + " icon position matches native HUD");
                require(nonWhite(image, x, y, 12, 12), "Slot " + (index + 1) + " icon pixels render inside the 12px bounds");
                writeImage(image, new File(screenshots, "exp-info-icon-slot-" + (index + 1) + ".png"));
                verifyIconClassAndNativeA();
                int scale = graphicsType.getField("b").getInt(null);
                require(scale > 0, "Slot " + (index + 1) + " native touch scale is positive");
                int xTouch = (x + 6) * scale, yTouch = (y + 6) * scale;
                Method press = engine.getMethod(required(p, "pointer.press.method"), Integer.TYPE, Integer.TYPE);
                Method release = engine.getMethod(required(p, "pointer.release.method"), Integer.TYPE, Integer.TYPE);
                press.invoke(null, Integer.valueOf(xTouch), Integer.valueOf(yTouch));
                require(((Boolean)info.getField("captured").get(null)).booleanValue(), "Slot " + (index + 1) + " native pointer press captures the icon");
                require(optionalStatic(engine, required(p, "dialog.active.field")) == null, "Slot " + (index + 1) + " waits for pointer release");
                release.invoke(null, Integer.valueOf(xTouch), Integer.valueOf(yTouch));
                dialog = optionalStatic(engine, required(p, "dialog.active.field"));
                require(dialog != null && owned(), "Slot " + (index + 1) + " actual pointer callback opens its own modal");
                require(field(guard, "dialog").get(null) == dialog && field(guard, "player").get(null) == player,
                        "Slot " + (index + 1) + " modal guard owns this slot's dialog/player");
                confirmationCenter(dialog, p, 1);
                writeDialog(dialog, graphicsType, new File(screenshots, "exp-info-modal-slot-" + (index + 1) + ".png"));
            }

            void verifyIconClassAndNativeA() throws Exception {
                Class<?> icon = cls(loader, prefix + "luna/ui/ExpInfoIcon");
                Method getImage = icon.getMethod("image", Integer.TYPE);
                Object base = getImage.invoke(null, Integer.valueOf(1));
                Object again = getImage.invoke(null, Integer.valueOf(1));
                require(base != null && base == again, "Slot " + (index + 1) + " reuses its cached icon image");
                require(dim(base, "getWidth") == 12 && dim(base, "getHeight") == 12, "Slot " + (index + 1) + " icon is 12x12");
                Object nativeA = imageFromResource(loader, "x1/interface/iconauto.png");
                require(dim(base, "getWidth") == dim(nativeA, "getWidth") && dim(base, "getHeight") == dim(nativeA, "getHeight"),
                        "Slot " + (index + 1) + " info icon exactly matches native A dimensions");
                Object doubled = getImage.invoke(null, Integer.valueOf(2));
                require(dim(doubled, "getWidth") == 24 && dim(doubled, "getHeight") == 24, "Slot " + (index + 1) + " icon scales to 24x24");
                int[] one = pixels(base), two = pixels(doubled);
                for (int yy = 0; yy < 24; yy++) for (int xx = 0; xx < 24; xx++)
                    require(two[yy * 24 + xx] == one[(yy / 2) * 12 + (xx / 2)], "Slot " + (index + 1) + " icon uses nearest-neighbor scaling");
            }

            void verifyModalProtection() throws Exception {
                Method keyPress = engine.getMethod(required(p, "modal.guard.keys.press"), Integer.TYPE);
                Method keyRelease = engine.getMethod(required(p, "modal.guard.keys.release"), Integer.TYPE);
                Method keyRoute = dialog.getClass().getMethod("d");
                Method command = dialog.getClass().getMethod(required(p, "modal.guard.command.method"), Integer.TYPE, Integer.TYPE);
                for (int key : new int[]{-5, 10, 13, -6, -7}) {
                    keyPress.invoke(canvas, Integer.valueOf(key)); keyRoute.invoke(dialog); keyRelease.invoke(canvas, Integer.valueOf(key));
                    command.invoke(dialog, Integer.valueOf(-1), Integer.valueOf(0));
                    require(optionalStatic(engine, required(p, "dialog.active.field")) == dialog,
                            "Slot " + (index + 1) + " key/command cannot close its owned modal");
                }
                Class<?> farmSwitch = cls(loader, prefix + pad("lliiiiil"));
                Class<?> farm = cls(loader, prefix + required(p, "modal.guard.farm.b"));
                Object farmInstance = allocate(farm);
                field(farmSwitch, pad("lililiilill")).setBoolean(null, true);
                field(farm, pad("lililllllii")).setBoolean(null, true);
                field(farm, pad("lilliiiiili")).setLong(null, System.currentTimeMillis());
                field(farm, pad("lilliiiiiii")).setInt(null, 100);
                Method update = farm.getMethod("update");
                long until = System.currentTimeMillis() + 2200L; int updates = 0;
                while (System.currentTimeMillis() < until) {
                    update.invoke(farmInstance); keyPress.invoke(canvas, Integer.valueOf(-5)); keyRoute.invoke(dialog); keyRelease.invoke(canvas, Integer.valueOf(-5));
                    require(optionalStatic(engine, required(p, "dialog.active.field")) == dialog,
                            "Slot " + (index + 1) + " actual Farm update preserves its owned modal");
                    updates++; Thread.sleep(50L);
                }
                require(updates >= 20 && field(farm, pad("lilliiiiiii")).getInt(null) < 100,
                        "Slot " + (index + 1) + " passed the real native Farm countdown and clear point");
                field(farmSwitch, pad("lililiilill")).setBoolean(null, false);
                field(farm, pad("lililllllii")).setBoolean(null, false);
                int scale = cls(loader, required(p, "slot." + index + ".graphics.owner")).getField("b").getInt(null);
                int[] center = confirmationCenter(dialog, p, scale);
                Method press = engine.getMethod(required(p, "pointer.press.method"), Integer.TYPE, Integer.TYPE);
                Method drag = engine.getMethod(required(p, "pointer.drag.method"), Integer.TYPE, Integer.TYPE);
                Method release = engine.getMethod(required(p, "pointer.release.method"), Integer.TYPE, Integer.TYPE);
                press.invoke(null, Integer.valueOf(0), Integer.valueOf(0)); release.invoke(null, Integer.valueOf(0), Integer.valueOf(0));
                require(optionalStatic(engine, required(p, "dialog.active.field")) == dialog, "Slot " + (index + 1) + " outside tap leaves modal open");
                press.invoke(null, Integer.valueOf(center[0]), Integer.valueOf(center[1]));
                drag.invoke(null, Integer.valueOf(0), Integer.valueOf(0));
                release.invoke(null, Integer.valueOf(center[0]), Integer.valueOf(center[1]));
                require(optionalStatic(engine, required(p, "dialog.active.field")) == dialog, "Slot " + (index + 1) + " drag-out cancels confirmation");
                confirmAndClose();
                info.getMethod("open").invoke(null);
                dialog = optionalStatic(engine, required(p, "dialog.active.field"));
                require(dialog != null && owned(), "Slot " + (index + 1) + " reopens its own modal for lifecycle checks");
            }

            boolean owned() throws Exception { return ((Boolean)guard.getMethod("owned").invoke(null)).booleanValue(); }

            void confirmAndClose() throws Exception {
                int scale = cls(loader, required(p, "slot." + index + ".graphics.owner")).getField("b").getInt(null);
                int[] center = confirmationCenter(dialog, p, scale);
                Method press = engine.getMethod(required(p, "pointer.press.method"), Integer.TYPE, Integer.TYPE);
                Method release = engine.getMethod(required(p, "pointer.release.method"), Integer.TYPE, Integer.TYPE);
                press.invoke(null, Integer.valueOf(center[0]), Integer.valueOf(center[1]));
                release.invoke(null, Integer.valueOf(center[0]), Integer.valueOf(center[1]));
                require(optionalStatic(engine, required(p, "dialog.active.field")) == null && !owned(),
                        "Slot " + (index + 1) + " confirmation pointer closes exactly its own modal");
            }

            void verifyClearIsolation(Slot one, Slot two, byte[] sharedBefore) throws Exception {
                Class<?> staticType = cls(loader, prefix + "Static"); Object stack = staticType.getConstructor().newInstance();
                Vector registered = (Vector)field(staticType, "staticvector").get(null);
                require(registered.contains(Integer.valueOf(guardLifecycleId))
                        && registered.contains(Integer.valueOf(engineLifecycleId))
                        && registered.contains(Integer.valueOf(rateLifecycleId)), "Slot 1 Static registered source-order modal, engine, and rate IDs");
                Method clear = staticType.getMethod("clears", Integer.TYPE);
                clear.invoke(stack, Integer.valueOf(guardLifecycleId));
                clear.invoke(stack, Integer.valueOf(rateLifecycleId));
                require(field(guard, "dialog").get(null) == null && field(guard, "player").get(null) == null,
                        "Slot 1 Static clear removes modal and player references");
                require(!owned(), "Slot 1 Static clear resets its native dialog ownership");
                dialog.getClass().getMethod(required(p, "modal.guard.command.method"), Integer.TYPE, Integer.TYPE)
                        .invoke(dialog, Integer.valueOf(-1), Integer.valueOf(0));
                require(optionalStatic(engine, required(p, "dialog.active.field")) == null,
                        "Native lifecycle cleanup closes the released slot 1 dialog");
                require(one.owned() && two.owned() && field(one.guard, "dialog").get(null) == one.dialog
                        && field(two.guard, "dialog").get(null) == two.dialog,
                        "Clearing slot 1 leaves slot 2 and 3 modal ownership intact");
                long rateNow = System.currentTimeMillis();
                require(rateValue(rate, rateNow) == -1L && rateValue(one.rate, rateNow) == 24L && rateValue(two.rate, rateNow) == 36L,
                        "Static clear resets only slot 1 EXP rate state");
                Class<?> shared = cls(loader, SHARED);
                byte[] sharedAfter = (byte[])shared.getMethod("load").invoke(null);
                require(Arrays.equals(sharedBefore, sharedAfter), "Slot clear preserves the root shared EXP level table");
            }
        }

        private static long rateValue(Class<?> rate, long now) throws Exception {
            return ((Long)rate.getMethod("rate", Long.TYPE).invoke(null, Long.valueOf(now))).longValue();
        }

        private static void verifyFakeRateIsolation(Slot[] slots) throws Exception {
            for (int i = 0; i < 3; i++)
                require(rateValue(slots[i].rate, 301000L) == (i + 1) * 12L,
                        "All synthetic slot rates coexist independently, slot " + (i + 1));
        }

        private static byte[] seedSharedExp(ClassLoader loader) throws Exception {
            Class<?> shared = cls(loader, SHARED);
            byte[] data = expRecord();
            require(((Boolean)shared.getMethod("save", byte[].class).invoke(null, new Object[]{data})).booleanValue(),
                    "One root shared EXP backend accepts a level record");
            byte[] loaded = (byte[])shared.getMethod("load").invoke(null);
            require(Arrays.equals(data, loaded), "Shared EXP table round-trips before lifecycle clear");
            return loaded;
        }

        private static byte[] expRecord() throws Exception {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(0x45585031); out.writeByte(2); out.writeByte(1); out.writeInt(70);
            out.writeLong(100000000L); out.writeLong(100000000L); out.writeShort(20); out.writeShort(30); out.close();
            return bytes.toByteArray();
        }

        private static int lifecycleId(File single, int slot, String sourceName) throws Exception {
            JarFile jar = new JarFile(single); int id = 0, found = -1;
            try {
                for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                    String name = e.nextElement().getName();
                    if (!name.endsWith(".class") || name.equals(SHARED + ".class")) continue;
                    String owner = name.substring(0, name.length() - 6);
                    if (owner.equals(sourceName)) found = id;
                    id++;
                }
            } finally { jar.close(); }
            require(found >= 0, "Source-order lifecycle ID found for " + sourceName + " slot " + (slot + 1));
            return found;
        }

        private static Object[] virtuals(MIDlet launcher) throws Exception {
            for (Field f : launcher.getClass().getDeclaredFields()) {
                if (f.getType().isArray() && f.getType().getComponentType().getName().equals("lib.MIDlet")) {
                    f.setAccessible(true); return (Object[])f.get(null);
                }
            }
            throw new AssertionError("Missing virtual clients array");
        }

        private static void awaitCanvas(Display display) throws Exception {
            long until = System.currentTimeMillis() + 4000L;
            while (!(display.getCurrent() instanceof javax.microedition.lcdui.Canvas) && System.currentTimeMillis() < until) Thread.sleep(25L);
            require(display.getCurrent() instanceof javax.microedition.lcdui.Canvas, "Virtual client Canvas became current");
        }

        private static void awaitMenu(Display display) throws Exception {
            long until = System.currentTimeMillis() + 2500L;
            while (!(display.getCurrent() instanceof List) && System.currentTimeMillis() < until) Thread.sleep(25L);
            require(display.getCurrent() instanceof List, "Held * returned to the actual launcher menu");
        }

        private static void key(Object target, String method, int code) throws Exception {
            target.getClass().getMethod(method, Integer.TYPE).invoke(target, Integer.valueOf(code));
        }

        private static int[] confirmationCenter(Object dialog, Properties p, int scale) throws Exception {
            Object commands = field(dialog.getClass(), pad("llliii")).get(dialog);
            int count = ((Integer)commands.getClass().getMethod("c").invoke(commands)).intValue();
            String label = required(p, "dialog.button.label");
            for (int i = 0; i < count; i++) {
                Object button = commands.getClass().getMethod("a", Integer.TYPE).invoke(commands, Integer.valueOf(i));
                String text = null; int command = Integer.MIN_VALUE;
                for (Field f : button.getClass().getFields()) {
                    f.setAccessible(true); Object value = f.get(button);
                    if (value instanceof String && label.equals(value)) text = (String)value;
                    if (f.getType() == Byte.TYPE && "e".equals(f.getName())) command = f.getByte(button);
                }
                if (text == null) continue;
                require(command == -1, "Confirmation button has native close command -1");
                require(field(button.getClass(), pad("liiiiiili")).get(button) == dialog,
                        "Confirmation command is bound to its owning dialog");
                int x = field(button.getClass(), "h").getInt(button);
                int y = field(button.getClass(), "i").getInt(button);
                return new int[]{Math.max(2, x + 2) * scale, Math.max(2, y + 2) * scale};
            }
            throw new AssertionError("Missing native confirmation button");
        }

        private static String dialogText(Object dialog) throws Exception {
            Object rows = field(dialog.getClass(), pad("lllliliii")).get(dialog);
            if (!(rows instanceof String[])) return String.valueOf(rows);
            StringBuilder text = new StringBuilder();
            for (String line : (String[])rows) text.append(line).append('\n');
            return text.toString();
        }

        private static void writeDialog(Object dialog, Class<?> graphicsType, File file) throws Exception {
            Object image = newSurface(dialog.getClass().getClassLoader(), 240, 320);
            Object graphics = image.getClass().getMethod("getGraphics").invoke(image);
            graphics.getClass().getMethod("setColor", Integer.TYPE).invoke(graphics, Integer.valueOf(0xFFFFFF));
            graphics.getClass().getMethod("fillRect", Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE)
                    .invoke(graphics, Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(240), Integer.valueOf(320));
            Object wrapper = bindNativeGraphics(graphicsType, graphics);
            Method paint = null;
            for (Method m : dialog.getClass().getDeclaredMethods()) {
                if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == graphicsType && m.getReturnType() == Void.TYPE) {
                    m.setAccessible(true); paint = m; break;
                }
            }
            if (paint == null) return;
            paint.invoke(dialog, wrapper);
            writeImage(image, file);
        }

        private static void checkDistinct(Class<?> a, Class<?> b, Class<?> c) throws Exception {
            require(a != b && b != c && a != c, "Each virtual slot has its own modal guard class");
        }
    }

    private static void checkIconResource(File single, File candidate, Properties p) throws Exception {
        String resource = required(p, "icon.resource");
        JarFile source = new JarFile(single), output = new JarFile(candidate);
        try {
            byte[] a = jarEntry(source, resource), b = jarEntry(output, resource);
            require(Arrays.equals(a, b), "X3 preserves the supplied 12px icon resource byte-for-byte");
            require(output.getJarEntry("b/" + resource) == null && output.getJarEntry("c/" + resource) == null,
                    "Three icon classes share one root resource");
            byte[] nativeA = jarEntry(source, "x1/interface/iconauto.png");
            require(nativeA.length > 0, "Native A resource is present in the source");
        } finally { source.close(); output.close(); }
    }

    private static byte[] jarEntry(JarFile jar, String name) throws Exception {
        JarEntry entry = jar.getJarEntry(name);
        require(entry != null, "Missing JAR resource " + name);
        return read(jar.getInputStream(entry));
    }

    private static Common startEmulator(File candidate) throws Exception {
        Headless headless = new Headless(); Field f = Headless.class.getDeclaredField("emulator"); f.setAccessible(true);
        Common emulator = (Common)f.get(headless); MemoryRecordStoreManager rms = new MemoryRecordStoreManager();
        rms.init(emulator); emulator.setRecordStoreManager(rms); MIDletBridge.setMicroEmulator(emulator);
        ArrayList<String> options = new ArrayList<String>(); options.add("--rms"); options.add("memory"); options.add(candidate.getAbsolutePath());
        DeviceEntry device = new DeviceEntry("Default device", null, "org/microemu/device/default/device.xml", true, false);
        require(emulator.initParams(options, device, J2SEDevice.class), "MicroEmulator initializes X3 candidate");
        emulator.initMIDlet(true); Thread.sleep(600L); return emulator;
    }

    private static Object newSurface(ClassLoader loader, int width, int height) throws Exception {
        Class<?> image = Class.forName("javax.microedition.lcdui.Image", true, loader);
        return image.getMethod("createImage", Integer.TYPE, Integer.TYPE).invoke(null, Integer.valueOf(width), Integer.valueOf(height));
    }

    private static Object imageFromResource(ClassLoader loader, String resource) throws Exception {
        InputStream in = loader.getResourceAsStream(resource); require(in != null, "Classloader resource exists: " + resource);
        byte[] bytes = read(in); Class<?> image = Class.forName("javax.microedition.lcdui.Image", true, loader);
        return image.getMethod("createImage", byte[].class, Integer.TYPE, Integer.TYPE)
                .invoke(null, bytes, Integer.valueOf(0), Integer.valueOf(bytes.length));
    }

    private static Object bindNativeGraphics(Class<?> graphicsType, Object graphics) throws Exception {
        Object wrapper = allocate(graphicsType); boolean bound = false;
        for (Field f : graphicsType.getFields()) {
            if (f.getType().getName().equals("javax.microedition.lcdui.Graphics")) {
                f.set(wrapper, graphics); bound = true; break;
            }
        }
        require(bound, "Native slot graphics wrapper is bound through reflection"); return wrapper;
    }

    private static boolean nonWhite(Object image, int x, int y, int width, int height) throws Exception {
        int[] pixels = pixels(image); int imageWidth = dim(image, "getWidth"), imageHeight = dim(image, "getHeight");
        for (int row = Math.max(0, y); row < Math.min(imageHeight, y + height); row++)
            for (int col = Math.max(0, x); col < Math.min(imageWidth, x + width); col++)
                if ((pixels[row * imageWidth + col] & 0x00FFFFFF) != 0x00FFFFFF) return true;
        return false;
    }

    private static int[] pixels(Object image) throws Exception {
        int width = dim(image, "getWidth"), height = dim(image, "getHeight"); int[] out = new int[width * height];
        Method getRGB = image.getClass().getMethod("getRGB", int[].class, Integer.TYPE, Integer.TYPE,
                Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
        getRGB.invoke(image, out, Integer.valueOf(0), Integer.valueOf(width), Integer.valueOf(0), Integer.valueOf(0),
                Integer.valueOf(width), Integer.valueOf(height));
        return out;
    }

    private static void writeImage(Object image, File file) throws Exception {
        int width = dim(image, "getWidth"), height = dim(image, "getHeight");
        BufferedImage png = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0, 0, width, height, pixels(image), 0, width); ImageIO.write(png, "png", file);
    }

    private static int dim(Object image, String method) throws Exception {
        return ((Integer)image.getClass().getMethod(method).invoke(image)).intValue();
    }

    private static Object firstStatic(Class<?> owner, Class<?> expected) throws Exception {
        for (Field f : owner.getDeclaredFields()) if (Modifier.isStatic(f.getModifiers()) && f.getType() == expected) {
            f.setAccessible(true); Object value = f.get(null); if (value != null) return value;
        }
        return null;
    }

    private static Object allocate(Class<?> type) throws Exception {
        try { java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(); constructor.setAccessible(true); return constructor.newInstance(); }
        catch (NoSuchMethodException missing) {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe"); Field f = unsafeType.getDeclaredField("theUnsafe"); f.setAccessible(true);
            Object unsafe = f.get(null); return unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        }
    }

    private static void setNative(Object target, String name, Object value) throws Exception {
        Field f = field(target.getClass(), name);
        if (f.getType() == Integer.TYPE) f.setInt(target, ((Number)value).intValue());
        else if (f.getType() == Short.TYPE) f.setShort(target, ((Number)value).shortValue());
        else throw new IllegalStateException("Unexpected native EXP field " + f);
    }

    private static Object[] virtuals(MIDlet launcher) throws Exception {
        for (Field f : launcher.getClass().getDeclaredFields()) {
            if (f.getType().isArray() && f.getType().getComponentType().getName().equals("lib.MIDlet")) {
                f.setAccessible(true); return (Object[])f.get(null);
            }
        }
        throw new AssertionError("Missing virtual client array");
    }

    private static Class<?> descriptorClass(ClassLoader loader, String descriptor) throws Exception {
        if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) throw new IllegalArgumentException("Bad class descriptor " + descriptor);
        return cls(loader, descriptor.substring(1, descriptor.length() - 1));
    }

    private static Class<?> cls(ClassLoader loader, String internalName) throws Exception {
        return Class.forName(internalName.replace('/', '.'), true, loader);
    }

    private static Object optionalStatic(Class<?> owner, String name) throws Exception {
        if (owner == null || name == null) return null;
        try { return field(owner, name).get(null); } catch (NoSuchFieldException ignored) { return null; }
    }

    private static void setOptionalStatic(Class<?> owner, String name, Object value) throws Exception {
        if (owner == null || name == null) return;
        try { field(owner, name).set(null, value); } catch (NoSuchFieldException ignored) { }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static String slotProp(int slot, String name, String fallback) {
        return name.equals("rate.owner") ? (slot == 0 ? fallback : (slot == 1 ? "b/" : "c/") + fallback) : fallback;
    }

    private static int lifecycleId(File single, int slot, String sourceName) throws Exception {
        JarFile jar = new JarFile(single); int id = 0, found = -1;
        try {
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                String name = e.nextElement().getName();
                if (!name.endsWith(".class") || name.equals(SHARED + ".class")) continue;
                String owner = name.substring(0, name.length() - 6);
                if (owner.equals(sourceName)) found = id;
                id++;
            }
        } finally { jar.close(); }
        require(found >= 0, "Source-order lifecycle ID found for " + sourceName + " slot " + (slot + 1));
        return found;
    }

    private static void snapshot(Common emulator, File file) throws Exception {
        org.microemu.device.j2se.J2SEDeviceDisplay display =
                (org.microemu.device.j2se.J2SEDeviceDisplay)emulator.getDevice().getDeviceDisplay();
        BufferedImage image = new BufferedImage(display.getFullWidth(), display.getFullHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics graphics = image.getGraphics();
        display.paintDisplayable(graphics, 0, 0, image.getWidth(), image.getHeight()); graphics.dispose(); ImageIO.write(image, "png", file);
    }

    private static String pad(String tail) {
        StringBuilder value = new StringBuilder(); while (value.length() + tail.length() < 100) value.append('i'); return value.append(tail).toString();
    }

    private static byte[] read(InputStream in) throws Exception {
        try { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] chunk = new byte[8192]; int count;
            while ((count = in.read(chunk)) != -1) out.write(chunk, 0, count); return out.toByteArray();
        } finally { in.close(); }
    }

    private static Properties load(File file) throws Exception {
        Properties p = new Properties(); InputStream in = new FileInputStream(file);
        try { p.load(in); } finally { in.close(); } return p;
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key); require(value != null && value.length() > 0, "Missing catalog property " + key); return value;
    }

    private static String hash(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(file); try { byte[] chunk = new byte[65536]; int count;
            while ((count = in.read(chunk)) != -1) digest.update(chunk, 0, count);
        } finally { in.close(); }
        StringBuilder hex = new StringBuilder(); for (byte b : digest.digest()) hex.append(String.format("%02X", b & 255)); return hex.toString();
    }

    private static void require(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
}
