import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.midlet.MIDlet;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.app.Headless;
import org.microemu.app.util.DeviceEntry;
import org.microemu.device.j2se.J2SEDevice;
import org.microemu.device.j2se.J2SEDeviceDisplay;
import org.microemu.util.MemoryRecordStoreManager;

/** Runtime fixture for the new native EXP information icon and modal. */
public final class HungBySonExpInfoBinaryTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        require(args.length == 4, "candidate.jar merged-catalog.properties microemulator.jar screenshotsDir");
        File jar = new File(args[0]);
        File catalog = new File(args[1]);
        File micro = new File(args[2]);
        File screenshots = new File(args[3]);
        Properties p = load(catalog);
        require(jar.isFile() && micro.isFile(), "Candidate and MicroEmulator exist");
        require(hash(jar).equals(required(p, "output.sha256")), "Candidate hash matches merged catalog");
        require("luna/generated/ExpInfoBridge".equals(required(p, "ui.owner")), "Generated UI bridge is catalogued");
        require("Xác nhận".equals(required(p, "dialog.button.label")), "Native confirmation label is Vietnamese");
        require("-1".equals(required(p, "dialog.close.command")), "Native confirmation command is retained");
        screenshots.mkdirs();
        runEmulatorWorker(jar, catalog, micro, screenshots);
        System.out.println("HungBySonExpInfoBinaryTest PASS checks=" + checks
                + "; native icon input, modal text/rate states, close and screenshots");
    }

    /** The MicroEmulator worker must not inherit the host ASM 9 verifier classpath. */
    private static void runEmulatorWorker(File jar, File catalog, File micro, File screenshots) throws Exception {
        File host = new File(HungBySonExpInfoBinaryTest.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        File runtimeHome = new File(screenshots, "runtime-home"); runtimeHome.mkdirs();
        File java = new File(new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        ArrayList<String> command = new ArrayList<String>();
        command.add(java.getAbsolutePath()); command.add("-Xverify:all");
        command.add("-Djava.awt.headless=true"); command.add("-Duser.home=" + runtimeHome.getAbsolutePath());
        command.add("-Dsun.reflect.inflationThreshold=10000");
        command.add("-cp"); command.add(host.getAbsolutePath() + File.pathSeparator + micro.getAbsolutePath());
        command.add("HungBySonExpInfoBinaryTest$EmulatorWorker");
        command.add(jar.getAbsolutePath()); command.add(catalog.getAbsolutePath());
        command.add(micro.getAbsolutePath()); command.add(screenshots.getAbsolutePath());
        Process process = new ProcessBuilder(command).inheritIO().start();
        int exit = process.waitFor(); require(exit == 0, "Isolated MicroEmulator worker exit=" + exit);
    }

    /** Runs with only this fixture's classes and MicroEmulator's bundled ASM 3. */
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
            Common emulator = startEmulator(jar);
            MIDlet active = MIDletBridge.getCurrentMIDlet();
            require(active != null, "MicroEmulator started the candidate MIDlet");
            ClassLoader loader = active.getClass().getClassLoader();
            Class<?> engine = Class.forName(dotted(required(p, "pointer.press.owner")), true, loader);
            Class<?> info = Class.forName("luna.generated.ExpInfoBridge", true, loader);
            Class<?> exp = Class.forName("luna.generated.ExpEstimateBridge", true, loader);
            Class<?> support = Class.forName("luna.features.ExpEstimateSupport", true, loader);
            Class<?> rate = Class.forName("luna.features.ExpRateSupport", true, loader);
            if("true".equals(p.getProperty("icon.art.custom"))){
                Class<?> icon=Class.forName("luna.ui.ExpInfoIcon",true,loader);
                Method load=icon.getMethod("image",Integer.TYPE);
                int iconSize=Integer.parseInt(p.getProperty("icon.size","14"));
                Image base=(Image)load.invoke(null,Integer.valueOf(1));
                require(base!=null&&base.getWidth()==iconSize&&base.getHeight()==iconSize,"Info badge loads at its catalogued native pixel size");
                require(base==load.invoke(null,Integer.valueOf(1)),"Unchanged frame reuses the icon image");
                Image twice=(Image)load.invoke(null,Integer.valueOf(2));
                int scaledSize=iconSize*2;
                require(twice.getWidth()==scaledSize&&twice.getHeight()==scaledSize,"Info badge supports native scale two");
                int[] original=imagePixels(base),scaled=imagePixels(twice);
                for(int y=0;y<scaledSize;y++)for(int x=0;x<scaledSize;x++)
                    require(scaled[y*scaledSize+x]==original[(y/2)*iconSize+x/2],"Scaled badge preserves pixel art and transparency");
                if("true".equals(p.getProperty("icon.matches.native.a.size"))){
                    InputStream input=loader.getResourceAsStream("x1/interface/iconauto.png");
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[1024];int count;
                    try{while((count=input.read(chunk))!=-1)bytes.write(chunk,0,count);}finally{input.close();}
                    byte[] imageBytes=bytes.toByteArray();Image nativeA=Image.createImage(imageBytes,0,imageBytes.length);
                    require(base.getWidth()==nativeA.getWidth()&&base.getHeight()==nativeA.getHeight(),"Supplied info icon is exactly the same size as native A");
                }
            }
            setStatic(engine, pad("lllillll"), Integer.valueOf(240));
            setStatic(engine, pad("liiilliilii"), Integer.valueOf(120));
            setStatic(engine, pad("liiiiiil"), Integer.valueOf(320));
            Class<?> worldType = fieldType(engine, pad("liiiliiiiil"));
            Class<?> entityType = Class.forName(required(p, "slot.0.main.descriptor")
                    .substring(1, required(p, "slot.0.main.descriptor").length() - 1).replace('/', '.'), true, loader);
            Object world = staticValue(engine, pad("liiiliiiiil"));
            if (world == null) { world = constructOrAllocate(worldType); setStatic(engine, pad("liiiliiiiil"), world); }
            Object player = staticValue(worldType, required(p, "slot.0.main.field"));
            if (player == null) {
                player = constructOrAllocate(entityType);
                setStatic(worldType, required(p, "slot.0.main.field"), player);
            }
            setStatic(worldType, pad("llilliill"), player);
            setStatic(engine, pad("liiiillllli"), world);
            setStatic(engine, pad("liiililiili"), null);
            setStatic(engine, pad("liiililiill"), null);
            setStatic(worldType, pad("lliilliil"), Boolean.FALSE);

            int id = 321, level = 70, progress = 500;
            setNative(player, required(p, "slot.0.id.field"), Integer.valueOf(id));
            setNative(player, required(p, "slot.0.level.field"), Short.valueOf((short)level));
            setNative(player, required(p, "slot.0.progress.field"), Short.valueOf((short)progress));
            require(((Boolean)info.getMethod("ready").invoke(null)).booleanValue(),
                    "Generated bridge is ready in the native world");

            renderHud(p, loader, info, screenshots, 240, 320, "portrait");
            renderHud(p, loader, info, screenshots, 320, 240, "landscape");
            int iconX = ((Integer)info.getField("x").get(null)).intValue();
            int iconY = ((Integer)info.getField("y").get(null)).intValue();
            require(iconX > 0 && iconY > 0, "Native icon has positive screen coordinates");
            renderRelativeIcon(p, loader, info, screenshots);
            if("true".equals(p.getProperty("icon.position.absolute"))){
                boolean compact=((Boolean)staticValue(engine,pad("liliiliil"))).booleanValue();
                for(boolean layout:new boolean[]{false,true}){
                    setStatic(engine,pad("liliiliil"),Boolean.valueOf(layout));
                    renderHud(p,loader,info,screenshots,240,320,layout?"compact-aligned":"normal-aligned");
                    verifyAbsolutePosition(p,loader,info,layout);
                }
                setStatic(engine,pad("liliiliil"),Boolean.valueOf(compact));
            }
            renderHud(p, loader, info, screenshots, 320, 240, "landscape-input");
            iconX = ((Integer)info.getField("x").get(null)).intValue();
            iconY = ((Integer)info.getField("y").get(null)).intValue();
            require(((Boolean)info.getField("painted").get(null)).booleanValue(),
                    "Native HUD paint reached the generated icon draw");
            int scale = graphicsScale(loader, p);
            int iconSize=Integer.parseInt(p.getProperty("icon.size","12"));
            int insideX = (iconX + iconSize/2) * scale, insideY = (iconY + iconSize/2) * scale;
            int outsideX = (iconX + iconSize) * scale, outsideY = (iconY + iconSize/2) * scale;
            Method hit = info.getMethod("hit", Integer.TYPE, Integer.TYPE);
            require(((Boolean)hit.invoke(null, Integer.valueOf(insideX), Integer.valueOf(insideY))).booleanValue(),
                    "Hitbox accepts an interior point after native coordinate scaling");
            require(!((Boolean)hit.invoke(null, Integer.valueOf(outsideX), Integer.valueOf(outsideY))).booleanValue(),
                    "Hitbox excludes its right edge");

            Method press = engine.getMethod(required(p, "pointer.press.method"), Integer.TYPE, Integer.TYPE);
            Method drag = engine.getMethod(required(p, "pointer.drag.method"), Integer.TYPE, Integer.TYPE);
            Method release = engine.getMethod(required(p, "pointer.release.method"), Integer.TYPE, Integer.TYPE);
            verifyOverlayGuards(p, loader, info, insideX, insideY);
            seedEstimateAndRate(loader, exp, support, rate, player, id, level, progress);
            rate.getMethod("reset").invoke(null);
            int tapX = insideX, tapY = insideY;
            press.invoke(null, Integer.valueOf(tapX), Integer.valueOf(tapY));
            require(((Boolean)info.getField("captured").get(null)).booleanValue(),
                    "Actual native press callback captures the icon gesture");
            require(staticValue(engine, required(p, "dialog.active.field")) == null,
                    "Modal waits for release instead of opening on press");
            release.invoke(null, Integer.valueOf(tapX), Integer.valueOf(tapY));
            require(!((Boolean)info.getField("captured").get(null)).booleanValue(),
                    "Actual native release callback consumes capture once");
            Object firstDialog = staticValue(engine, required(p, "dialog.active.field"));
            require(firstDialog != null, "Inside release opens the native modal");
            renderModal(firstDialog, p, loader, screenshots, 240, 320, "portrait");
            verifyDialogText(firstDialog, p, loader, support, 0, "unknown-rate");
            require(((Long)rate.getMethod("rate", Long.TYPE).invoke(null,
                    Long.valueOf(System.currentTimeMillis()))).longValue() < 0L,
                    "Rate is unknown before the five-minute window");
            dumpDialogState(firstDialog);

            closeNativeDialog(press, release, firstDialog, engine, p, info, scale);
            require(staticValue(engine, required(p, "dialog.active.field")) == null,
                    "Native confirmation returns to the game world");
            require(staticValue(engine, pad("liiiillllli")) == world,
                    "Confirmation returns to the existing game screen");
            release.invoke(null, Integer.valueOf(tapX), Integer.valueOf(tapY));
            require(staticValue(engine, required(p, "dialog.active.field")) == null,
                    "Second release does not recreate the modal");

            renderHud(p, loader, info, screenshots, 240, 320, "portrait-after-close");
            iconX = ((Integer)info.getField("x").get(null)).intValue();
            iconY = ((Integer)info.getField("y").get(null)).intValue();
            tapX = (iconX+6)*scale; tapY = (iconY+6)*scale;
            press.invoke(null, Integer.valueOf(tapX), Integer.valueOf(tapY));
            require(((Boolean)info.getField("captured").get(null)).booleanValue(),
                    "A new painted icon is captured after the modal closes");
            drag.invoke(null, Integer.valueOf((iconX + 25) * scale), Integer.valueOf((iconY + 6) * scale));
            require(((Boolean)info.getField("cancelled").get(null)).booleanValue(),
                    "Actual native drag callback cancels a gesture moved outside the icon");
            release.invoke(null, Integer.valueOf((iconX + 25) * scale), Integer.valueOf((iconY + 6) * scale));
            require(staticValue(engine, required(p, "dialog.active.field")) == null,
                    "Release outside after drag cancellation does not open the modal");
            require(!((Boolean)info.getField("captured").get(null)).booleanValue(),
                    "Cancelled gesture is consumed through release");

            press.invoke(null, Integer.valueOf(20 * scale), Integer.valueOf(20 * scale));
            release.invoke(null, Integer.valueOf(20 * scale), Integer.valueOf(20 * scale));
            require(staticValue(engine, required(p, "dialog.active.field")) == null,
                    "Unrelated native input does not open the EXP modal");
            require(!((Boolean)info.getField("captured").get(null)).booleanValue(),
                    "Unrelated native input is left to the original menu path");

            seedEstimateAndRate(loader, exp, support, rate, player, id, level, progress);
            press.invoke(null, Integer.valueOf(tapX), Integer.valueOf(tapY));
            release.invoke(null, Integer.valueOf(tapX), Integer.valueOf(tapY));
            Object knownDialog = staticValue(engine, required(p, "dialog.active.field"));
            require(knownDialog != null, "Icon opens a second native dialog after cancellation");
            verifyDialogText(knownDialog, p, loader, support, 1, "known-rate");
            renderModal(knownDialog, p, loader, screenshots, 240, 320, "known-rate-portrait");
            renderModal(knownDialog, p, loader, screenshots, 320, 240, "known-rate-landscape");
            snapshotDisplay(emulator, screenshots, "native-display-modal.png");
            closeNativeDialog(press, release, knownDialog, engine, p, info, scale);
            require(staticValue(engine, required(p, "dialog.active.field")) == null,
                    "Confirmation closes the known-rate modal back to the world");
            require(staticValue(engine, pad("liiiillllli")) == world,
                    "Known-rate confirmation leaves the native game screen active");
        }
    }

    private static void closeNativeDialog(Method press, Method release, Object dialog, Class<?> engine,
            Properties p, Class<?> info, int scale) throws Exception {
        int[] center = confirmationCenter(dialog, p, scale);
        press.invoke(null, Integer.valueOf(center[0]), Integer.valueOf(center[1]));
        release.invoke(null, Integer.valueOf(center[0]), Integer.valueOf(center[1]));
        dialog.getClass().getMethod("e").invoke(dialog);
        long until = System.currentTimeMillis() + 800L;
        while (staticValue(engine, required(p, "dialog.active.field")) == dialog
                && System.currentTimeMillis() < until) Thread.sleep(20L);
        require(staticValue(engine, required(p, "dialog.active.field")) != dialog,
                "Native Xác nhận command closes through the existing pointer route");
    }

    private static int[] confirmationCenter(Object dialog, Properties p, int scale) throws Exception {
        String label = required(p, "dialog.button.label");
        Object commands = findField(dialog.getClass(), pad("llliii")).get(dialog);
        int size = ((Integer)commands.getClass().getMethod("c").invoke(commands)).intValue();
        for (int index=0; index<size; index++) {
            Object button = commands.getClass().getMethod("a", Integer.TYPE).invoke(commands, Integer.valueOf(index));
            String text = null; int command = Integer.MIN_VALUE;
            for (Field child : button.getClass().getFields()) {
                child.setAccessible(true); Object value = child.get(button);
                if (value instanceof String && label.equals(value)) text = (String)value;
                if (child.getType() == Byte.TYPE && "e".equals(child.getName())) command = child.getByte(button);
            }
            if (text == null) continue;
            require(command == -1, "Native dialog button retains close command -1");
            require(findField(button.getClass(),pad("liiiiiili")).get(button)==dialog,
                    "Confirmation dispatch is bound directly to its own dialog");
            int x = positiveInt(button, "h"); int y = positiveInt(button, "i");
            System.out.println("Native confirm button center candidate=" + x + "," + y + " dialog=" + dialogSummary(dialog));
            return new int[]{x * scale, y * scale};
        }
        throw new AssertionError("Could not find native Xác nhận button and its coordinates");
    }

    private static int positiveInt(Object target, String name) throws Exception {
        Field f = findField(target.getClass(), name); f.setAccessible(true);
        int value = f.getInt(target); return value > 0 ? value + 2 : 2;
    }

    private static void verifyDialogText(Object dialog, Properties p, ClassLoader loader,
            Class<?> support, int knownRate, String label) throws Exception {
        Set<String> strings = new LinkedHashSet<String>();
        collectStrings(dialog, 0, new IdentityHashMap<Object, Boolean>(), strings, loader);
        Field rows = findField(dialog.getClass(), pad("lllliliii")); rows.setAccessible(true);
        String[] actualRows = (String[])rows.get(dialog);
        StringBuilder all = new StringBuilder();
        for (String text : actualRows) all.append(text).append('\n');
        String body = all.toString();
        String normalized = body.replaceAll("\\s+", " ");
        require(normalized.indexOf("Kinh nghiệm/giờ (ước tính):") >= 0, label + " dialog has the hourly heading");
        require(normalized.indexOf("Kinh nghiệm lên cấp (ước tính):") >= 0, label + " dialog has the level heading");
        require(normalized.indexOf("Thời gian còn lại (ước tính):") >= 0, label + " dialog has the ETA heading");
        require(confirmationCenter(dialog,p,1).length==2, label + " dialog exposes its native confirm label");
        require(body.indexOf("100.000.000") >= 0, label + " dialog displays the whole level total with dot grouping");
        String calculating = "Đang tính toán...";
        if (knownRate == 0) {
            require(occurrences(body, calculating) == 2,
                    "Before five minutes only hourly rate and ETA remain unknown: " + occurrences(body, calculating));
            require(body.indexOf("12.000") < 0, "Unknown hourly rate is not shown early");
        } else {
            require(occurrences(body, calculating) == 0, "After five minutes all three values are available");
            require(body.indexOf("12.000") >= 0, "Five-minute hourly rate is displayed with dot grouping");
            require(body.indexOf("giờ") >= 0 && body.indexOf("phút") >= 0,
                    "Five-minute ETA uses Vietnamese hour and minute labels");
        }
        System.out.println("Native dialog " + label + " strings=" + strings);
        verifyNativeGlyphs(p, loader, body);
        verifyWrappedTextFits(p, loader, body);
    }

    private static void verifyWrappedTextFits(Properties p, ClassLoader loader, String body) throws Exception {
        Class<?> fontType = Class.forName(dotted(required(p, "slot.0.font.owner")), true, loader);
        Object font = firstStatic(fontType, fontType);
        Method width = null;
        for (Method method : fontType.getDeclaredMethods()) if (method.getReturnType() == Integer.TYPE
                && method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == String.class) {
            method.setAccessible(true); width = method; break;
        }
        require(width != null && font != null, "Native dialog font width API is initialized");
        int available = 224;
        String[] lines = body.split("\n", -1);
        for (int line = 0; line < lines.length; line++) {
            String value = lines[line];
            if (value.length() == 0 || "Xác nhận".equals(value)) continue;
            int measured = ((Integer)width.invoke(font, value)).intValue();
            if (measured > available) {
                StringBuilder wrapped = new StringBuilder(); int last = 0;
                for (int i = 0; i < value.length(); i++) {
                    if (value.charAt(i) == ' ' || i == value.length() - 1) {
                        int end = i == value.length() - 1 ? i + 1 : i;
                        if (end > last) {
                            String fragment = value.substring(last, end);
                            int partWidth = ((Integer)width.invoke(font, fragment)).intValue();
                            require(partWidth <= available, "Native dialog wraps within visible width: " + fragment);
                            if (wrapped.length() > 0) wrapped.append(' '); wrapped.append(fragment);
                        }
                        last = i + 1;
                    }
                }
                require(wrapped.length() > 0, "Long native dialog line has visible word wrapping");
            } else require(measured > 0, "Nonempty native dialog line has measurable glyph width");
        }
    }

    private static void verifyNativeGlyphs(Properties p, ClassLoader loader, String body) throws Exception {
        Class<?> graphicsType = Class.forName(dotted(required(p, "slot.0.graphics.owner")), true, loader);
        Class<?> fontType = Class.forName(dotted(required(p, "slot.0.font.owner")), true, loader);
        Object font = firstStatic(fontType, fontType);
        Method draw = fontType.getDeclaredMethod(required(p, "slot.0.font.draw.method"), graphicsType,
                String.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, Boolean.TYPE);
        draw.setAccessible(true);
        String accentSet = "Đã nghiệm lên cấp Thời gian còn lại ước tính";
        Image image = Image.createImage(320, 64); javax.microedition.lcdui.Graphics g = image.getGraphics();
        g.setColor(0xFFFFFF); g.fillRect(0, 0, 320, 64);
        Object wrapper = bindNativeGraphics(graphicsType, g);
        draw.invoke(font, wrapper, accentSet, Integer.valueOf(2), Integer.valueOf(2), Integer.valueOf(0), Boolean.FALSE);
        int[] pixels = imagePixels(image); boolean visible = false;
        for (int pixel : pixels) if ((pixel & 0xFFFFFF) != 0xFFFFFF) { visible = true; break; }
        require(visible, "Native bitmap font draws Vietnamese dialog glyphs");
        require(body.indexOf('ệ') >= 0 && body.indexOf('ờ') >= 0,
                "Reflected dialog text retains Vietnamese diacritics");
    }

    private static void renderHud(Properties p, ClassLoader loader, Class<?> bridge, File screenshots,
            int width, int height, String suffix) throws Exception {
        Class<?> hudType = Class.forName(dotted(required(p, "slot.0.hud.owner")), true, loader);
        Class<?> graphicsType = Class.forName(dotted(required(p, "slot.0.graphics.owner")), true, loader);
        Class<?> fontType = Class.forName(dotted(required(p, "slot.0.font.owner")), true, loader);
        Object font = firstStatic(fontType, fontType);
        Object renderer = constructOrAllocate(hudType);
        Method paint = hudType.getDeclaredMethod(required(p, "slot.0.hud.method"), graphicsType,
                Integer.TYPE, Integer.TYPE, Boolean.TYPE, Class.forName(dotted(required(p, "slot.0.font.owner")), true, loader));
        paint.setAccessible(true);
        Image image = Image.createImage(width, height); javax.microedition.lcdui.Graphics g = image.getGraphics();
        g.setColor(0xFFFFFF); g.fillRect(0, 0, width, height);
        Object wrapper = bindNativeGraphics(graphicsType, g);
        paint.invoke(renderer, wrapper, Integer.valueOf(0), Integer.valueOf(0), Boolean.TRUE, font);
        if("true".equals(p.getProperty("icon.art.custom"))){
            Class<?> world=Class.forName(dotted(required(p,"slot.0.main.owner")),true,loader);
            Object player=staticValue(world,required(p,"slot.0.main.field"));
            Field autoBadge=findField(player.getClass(),pad("lllilli"));autoBadge.setAccessible(true);autoBadge.setByte(player,(byte)0);
            hudType.getMethod(pad("lli"),graphicsType).invoke(renderer,wrapper);
        }
        require(((Boolean)bridge.getField("painted").get(null)).booleanValue(), "HUD frame painted the EXP information icon");
        int x = ((Integer)bridge.getField("x").get(null)).intValue();
        int y = ((Integer)bridge.getField("y").get(null)).intValue();
        int size=Integer.parseInt(p.getProperty("icon.size","12"));
        require(hasNonWhitePixel(image, x, y, size, size), "New icon is visible in the native HUD screenshot");
        if("true".equals(p.getProperty("icon.art.custom"))){
            Class<?> engine=Class.forName(dotted(required(p,"pointer.press.owner")),true,loader);
            boolean compact=((Boolean)staticValue(engine,pad("liliiliil"))).booleanValue();
            int aCenter=compact?91:103;
            require(x+size/2==aCenter,"Custom info badge is aligned to the actual native A column");
            require(hasNonWhitePixel(image,aCenter-6,(compact?35:36)-6,12,12),"Native A badge remains visible above the independent icon");
        }
        int[] origin = hudOrigin(image);
        require(hasNonWhitePixel(image, Math.max(0, origin[0] - 3), Math.max(0, origin[1] - 2), 96, 42),
                "Native HUD frame and status remain visible around the new icon");
        writePng(image, new File(screenshots, "native-hud-" + suffix + ".png"));
    }

    private static void renderRelativeIcon(Properties p, ClassLoader loader, Class<?> info,
            File screenshots) throws Exception {
        Class<?> graphicsType = Class.forName(dotted(required(p, "slot.0.graphics.owner")), true, loader);
        Class<?> fontType = Class.forName(dotted(required(p, "slot.0.font.owner")), true, loader);
        Class<?> expBridge = Class.forName(dotted(required(p, "slot.0.bridge.owner")), true, loader);
        Object font = firstStatic(fontType, fontType);
        Method draw = expBridge.getDeclaredMethod("drawEstimate", graphicsType, fontType, Integer.TYPE, Integer.TYPE);
        draw.setAccessible(true);
        Image image = Image.createImage(160, 80); javax.microedition.lcdui.Graphics g = image.getGraphics();
        g.setColor(0xFFFFFF); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        Object wrapper = bindNativeGraphics(graphicsType, g);
        draw.invoke(null, wrapper, font, Integer.valueOf(0), Integer.valueOf(0));
        int x = ((Integer)info.getField("x").get(null)).intValue();
        int y = ((Integer)info.getField("y").get(null)).intValue();
        Class<?> engine = Class.forName(dotted(required(p, "pointer.press.owner")), true, loader);
        String boundsKey = staticValue(engine, pad("liliiliil")).equals(Boolean.TRUE)
                ? "icon.bounds.compact" : "icon.bounds.normal";
        String[] bounds = required(p, boundsKey).split(",");
        int size=Integer.parseInt(p.getProperty("icon.size","12"));
        require(x == Integer.parseInt(bounds[0]) && y == Integer.parseInt(bounds[1])
                        && Integer.parseInt(bounds[2]) == size && Integer.parseInt(bounds[3]) == size,
                "Generated icon draw uses its catalogued native bounds: " + x + "," + y);
        require(((Boolean)info.getField("painted").get(null)).booleanValue()
                        && hasNonWhitePixel(image, x, y, size, size),
                "Generated icon rim and yellow i glyph are visible at the native bounds");
        writePng(image, new File(screenshots, "native-icon-relative.png"));
    }

    private static void verifyAbsolutePosition(Properties p,ClassLoader loader,Class<?> info,boolean compact)throws Exception{
        Class<?> graphicsType=Class.forName(dotted(required(p,"slot.0.graphics.owner")),true,loader);
        Class<?> fontType=Class.forName(dotted(required(p,"slot.0.font.owner")),true,loader);
        Class<?> bridge=Class.forName(dotted(required(p,"slot.0.bridge.owner")),true,loader);
        Image image=Image.createImage(180,100);
        Object graphics=bindNativeGraphics(graphicsType,image.getGraphics());
        bridge.getMethod("drawEstimate",graphicsType,fontType,Integer.TYPE,Integer.TYPE)
                .invoke(null,graphics,firstStatic(fontType,fontType),Integer.valueOf(31),Integer.valueOf(19));
        String[] bounds=p.getProperty(compact?"icon.bounds.compact":"icon.bounds.normal").split(",");
        require(info.getField("x").getInt(null)==Integer.parseInt(bounds[0])
                        &&info.getField("y").getInt(null)==Integer.parseInt(bounds[1]),
                "Info icon stays aligned with A when the EXP panel supplies a nonzero origin");
    }

    private static void renderModal(Object dialog, Properties p, ClassLoader loader, File screenshots,
            int width, int height, String suffix) throws Exception {
        Class<?> engine = Class.forName(dotted(required(p, "dialog.active.owner")), true, loader);
        setStatic(engine, pad("lllillll"), Integer.valueOf(width));
        setStatic(engine, pad("liiilliilii"), Integer.valueOf(width/2));
        setStatic(engine, pad("liiiiiil"), Integer.valueOf(height));
        Class<?> support = Class.forName("luna.features.ExpEstimateSupport", true, loader);
        Object commands = findField(dialog.getClass(), pad("llliii")).get(dialog);
        Object confirm = commands.getClass().getMethod("a", Integer.TYPE).invoke(commands, Integer.valueOf(0));
        String body = (String)support.getMethod("info",Integer.TYPE,Long.TYPE).invoke(null,Integer.valueOf(500),Long.valueOf(System.currentTimeMillis()));
        dialog.getClass().getMethod(required(p,"dialog.setup.method"),String.class,confirm.getClass(),Boolean.TYPE)
                .invoke(dialog,body,confirm,Boolean.TRUE);
        int left=readInt(dialog,"n"),top=readInt(dialog,"o");
        int boxWidth=readInt(dialog,"l"),boxHeight=readInt(dialog,"m");
        require(left>=0&&top>=0&&left+boxWidth<=width&&top+boxHeight<=height,
                "Native dialog and confirmation fit the "+width+"x"+height+" viewport");
        Class<?> graphicsType = Class.forName(dotted(required(p, "slot.0.graphics.owner")), true, loader);
        Method paint = null;
        for (Method method : dialog.getClass().getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 1 && parameters[0] == graphicsType && method.getReturnType() == Void.TYPE) {
                method.setAccessible(true); paint = method; break;
            }
        }
        require(paint != null, "Native dialog exposes its native paint method");
        Image image = Image.createImage(width, height); javax.microedition.lcdui.Graphics g = image.getGraphics();
        g.setColor(0xFFFFFF); g.fillRect(0, 0, width, height);
        Object wrapper = bindNativeGraphics(graphicsType, g);
        paint.invoke(dialog, wrapper);
        require(hasNonWhitePixel(image, 0, 0, width, height), "Native modal paints visible pixels in " + suffix);
        writePng(image, new File(screenshots, "native-modal-" + suffix + ".png"));
    }

    private static void verifyOverlayGuards(Properties p, ClassLoader loader, Class<?> info,
            int x, int y) throws Exception {
        Class<?> engine=Class.forName(dotted(required(p,"icon.menu.owner")),true,loader);
        Object menu=staticValue(engine,required(p,"icon.menu.field"));
        if(menu==null){menu=constructOrAllocate(Class.forName(dotted(required(p,"icon.menu.active.owner")),true,loader));setStatic(engine,required(p,"icon.menu.field"),menu);}
        Field menuFlag=findField(menu.getClass(),required(p,"icon.menu.active.field"));menuFlag.setAccessible(true);
        boolean old=menuFlag.getBoolean(menu);menuFlag.setBoolean(menu,true);
        require(!((Boolean)info.getMethod("ready").invoke(null)).booleanValue(),"Native menu overlay blocks the info icon");
        require(!((Boolean)info.getMethod("press",Integer.TYPE,Integer.TYPE).invoke(null,Integer.valueOf(x),Integer.valueOf(y))).booleanValue(),"Info cannot capture a menu overlay tap");
        menuFlag.setBoolean(menu,old);
        Class<?> wait=Class.forName(dotted(required(p,"icon.wait.owner")),true,loader);
        Field flag=findField(wait,required(p,"icon.wait.field"));flag.setAccessible(true);old=flag.getBoolean(null);flag.setBoolean(null,true);
        require(!((Boolean)info.getMethod("ready").invoke(null)).booleanValue(),"Connection wait blocks the info icon");
        require(!((Boolean)info.getMethod("press",Integer.TYPE,Integer.TYPE).invoke(null,Integer.valueOf(x),Integer.valueOf(y))).booleanValue(),"Info cannot capture a connection-wait tap");
        flag.setBoolean(null,old);
    }

    private static void seedEstimateAndRate(ClassLoader loader, Class<?> expBridge,
            Class<?> support, Class<?> rate, Object player, int id, int level, int progress) throws Exception {
        Class<?> storeType = Class.forName("luna.api.ExpEstimateStore", true, loader);
        final byte[] record = estimateRecord(level, 100000000L, 100000000L, 20, 30);
        Object store = Proxy.newProxyInstance(loader, new Class<?>[]{storeType}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                if (method.getName().equals("load")) return record;
                if (method.getName().equals("save")) return Boolean.TRUE;
                return null;
            }
        });
        support.getMethod("setStore", storeType).invoke(null, store);
        expBridge.getMethod("snapshot").invoke(null);
        Field progressField = findField(player.getClass(), "bA"); progressField.setAccessible(true);
        progressField.setShort(player, (short)progress);
        int rateBuckets = 300;
        try { Field count = rate.getDeclaredField("RATE_BUCKETS"); count.setAccessible(true); rateBuckets = count.getInt(null); }
        catch (NoSuchFieldException legacySessionRate) { }
        rate.getMethod("gain", Integer.TYPE, Long.TYPE).invoke(null,
                Integer.valueOf(1000), Long.valueOf(System.currentTimeMillis() - rateBuckets * 1000L - 1L));
        try {
            rate.getDeclaredField("bucketGain");
            rate.getMethod("gain", Integer.TYPE, Long.TYPE).invoke(null,
                    Integer.valueOf(rateBuckets == 900 ? 3000 : 1000), Long.valueOf(System.currentTimeMillis() - 1000L));
        } catch (NoSuchFieldException legacySessionRate) { }
        require(((Boolean)support.getMethod("isReady").invoke(null)).booleanValue(),
                "Seeded native level total is available to the modal");
        require(((Long)rate.getMethod("rate", Long.TYPE).invoke(null,
                Long.valueOf(System.currentTimeMillis()))).longValue() == 12000L,
                "Seeded rate reaches the five-minute boundary without waiting");
    }

    private static byte[] estimateRecord(int level, long low, long high, int samples, int span) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x45585031); out.writeByte(2); out.writeByte(1); out.writeInt(level);
        out.writeLong(low); out.writeLong(high); out.writeShort(samples); out.writeShort(span); out.flush();
        return bytes.toByteArray();
    }

    private static int graphicsScale(ClassLoader loader, Properties p) throws Exception {
        Class<?> graphicsType = Class.forName(dotted(required(p, "slot.0.graphics.owner")), true, loader);
        Field scale = graphicsType.getField("b"); int value = scale.getInt(null);
        require(value > 0, "Native touch scale is positive"); return value;
    }

    private static Object bindNativeGraphics(Class<?> graphicsType, javax.microedition.lcdui.Graphics graphics) throws Exception {
        Object wrapper = constructOrAllocate(graphicsType); boolean bound = false;
        for (Field f : graphicsType.getFields()) if (f.getType() == javax.microedition.lcdui.Graphics.class) {
            f.set(wrapper, graphics); bound = true; break;
        }
        require(bound, "Native Graphics wrapper bound to MicroEmulator image"); return wrapper;
    }

    private static Object firstStatic(Class<?> type, Class<?> fieldType) throws Exception {
        for (Field f : type.getDeclaredFields()) if (Modifier.isStatic(f.getModifiers()) && f.getType() == fieldType) {
            f.setAccessible(true); Object value = f.get(null); if (value != null) return value;
        }
        return null;
    }

    private static Object constructOrAllocate(Class<?> type) throws Exception {
        try { java.lang.reflect.Constructor<?> c = type.getDeclaredConstructor(); c.setAccessible(true); return c.newInstance(); }
        catch (NoSuchMethodException missing) {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe"); Field singleton = unsafeType.getDeclaredField("theUnsafe");
            singleton.setAccessible(true); Object unsafe = singleton.get(null);
            return unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        }
    }

    private static void snapshotDisplay(Common emulator, File screenshots, String name) throws Exception {
        J2SEDeviceDisplay display = (J2SEDeviceDisplay)emulator.getDevice().getDeviceDisplay();
        BufferedImage image = new BufferedImage(display.getFullWidth(), display.getFullHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics g = image.getGraphics();
        display.paintDisplayable(g, 0, 0, image.getWidth(), image.getHeight()); g.dispose();
        ImageIO.write(image, "png", new File(screenshots, name));
    }

    private static void writePng(Image image, File file) throws Exception {
        int width = image.getWidth(), height = image.getHeight(); int[] pixels = imagePixels(image);
        BufferedImage rendered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        rendered.setRGB(0, 0, width, height, pixels, 0, width); ImageIO.write(rendered, "png", file);
    }
    private static int[] imagePixels(Image image) {
        int width = image.getWidth(), height = image.getHeight(); int[] pixels = new int[width * height];
        image.getRGB(pixels, 0, width, 0, 0, width, height); return pixels;
    }
    private static boolean hasNonWhitePixel(Image image, int x, int y, int width, int height) {
        int[] pixels = imagePixels(image); int imageWidth = image.getWidth();
        for (int row = Math.max(0, y); row < Math.min(image.getHeight(), y + height); row++)
            for (int col = Math.max(0, x); col < Math.min(imageWidth, x + width); col++)
                if ((pixels[row * imageWidth + col] & 0xFFFFFF) != 0xFFFFFF) return true;
        return false;
    }
    private static int[] hudOrigin(Image image) {
        int[] pixels = imagePixels(image); int imageWidth = image.getWidth();
        int minX = image.getWidth(), minY = image.getHeight();
        for (int y = 0; y < Math.min(80, image.getHeight()); y++)
            for (int x = 0; x < Math.min(120, imageWidth); x++)
                if ((pixels[y * imageWidth + x] & 0xFFFFFF) != 0xFFFFFF) {
                    if (x < minX) minX = x; if (y < minY) minY = y;
                }
        return new int[]{minX, minY};
    }

    private static void collectStrings(Object value, int depth, IdentityHashMap<Object, Boolean> seen,
            Set<String> result, ClassLoader loader) throws Exception {
        if (value == null || depth > 5 || seen.containsKey(value)) return;
        seen.put(value, Boolean.TRUE);
        if (value instanceof String) { result.add((String)value); return; }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length && i < 256; i++) collectStrings(java.lang.reflect.Array.get(value, i), depth + 1, seen, result, loader);
            return;
        }
        if (type.getClassLoader() != loader) return;
        for (Class<?> c = type; c != null && c.getClassLoader() == loader; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                field.setAccessible(true);
                try { collectStrings(field.get(value), depth + 1, seen, result, loader); }
                catch (IllegalAccessException ignored) { }
            }
        }
    }

    private static void dumpDialogState(Object dialog) throws Exception {
        System.out.println("Native dialog fields=" + dialogSummary(dialog));
        for (Field field : dialog.getClass().getFields()) {
            if (!field.getType().getName().equals("bt")) continue;
            Object button = field.get(dialog); if (button == null) continue;
            StringBuilder values = new StringBuilder("button " + field.getName() + " ");
            for (Field child : button.getClass().getFields()) {
                child.setAccessible(true); Object v = child.get(button);
                if (v == null || v instanceof String || v instanceof Number || v instanceof Boolean)
                    values.append(child.getName()).append('=').append(v).append(' ');
            }
            System.out.println(values.toString());
        }
    }
    private static String dialogSummary(Object dialog) throws Exception {
        StringBuilder result = new StringBuilder();
        for (Class<?> c = dialog.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType().isPrimitive() || field.getType() == String.class) {
                    field.setAccessible(true);
                    result.append(field.getName()).append('=').append(field.get(dialog)).append(';');
                }
            }
        }
        return result.toString();
    }

    private static Object staticValue(Class<?> owner, String name) throws Exception {
        Field f = findField(owner, name); f.setAccessible(true); return f.get(null);
    }
    private static void setStatic(Class<?> owner, String name, Object value) throws Exception {
        Field f = findField(owner, name); f.setAccessible(true); f.set(null, value);
    }
    private static Class<?> fieldType(Class<?> owner, String name) throws Exception { return findField(owner, name).getType(); }
    private static Field findField(Class<?> type, String name) throws Exception {
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private static int readInt(Object value,String name)throws Exception{
        Field f=findField(value.getClass(),name);f.setAccessible(true);return f.getInt(value);
    }
    private static void setNative(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name); f.setAccessible(true);
        if (f.getType() == Integer.TYPE) f.setInt(target, ((Number)value).intValue());
        else if (f.getType() == Short.TYPE) f.setShort(target, ((Number)value).shortValue());
        else throw new IllegalStateException("Unexpected native field type " + f);
    }
    private static String dotted(String value) { return value.replace('/', '.'); }
    private static String pad(String tail) {
        StringBuilder value = new StringBuilder(); while (value.length() + tail.length() < 100) value.append('i'); return value.append(tail).toString();
    }
    private static int occurrences(String text, String fragment) {
        int count = 0, from = 0; while ((from = text.indexOf(fragment, from)) >= 0) { count++; from += fragment.length(); } return count;
    }
    private static Properties load(File file) throws Exception {
        Properties p = new Properties(); InputStream in = new FileInputStream(file);
        try { p.load(in); } finally { in.close(); } return p;
    }

    private static Common startEmulator(File jar) throws Exception {
        Headless headless = new Headless();
        Field f = Headless.class.getDeclaredField("emulator"); f.setAccessible(true);
        Common emulator = (Common)f.get(headless);
        MemoryRecordStoreManager rms = new MemoryRecordStoreManager();
        rms.init(emulator); emulator.setRecordStoreManager(rms); MIDletBridge.setMicroEmulator(emulator);
        ArrayList<String> options = new ArrayList<String>();
        options.add("--rms"); options.add("memory"); options.add(jar.getAbsolutePath());
        DeviceEntry device = new DeviceEntry("Default device", null,
                "org/microemu/device/default/device.xml", true, false);
        require(emulator.initParams(options, device, J2SEDevice.class), "MicroEmulator initialization");
        emulator.initMIDlet(true); Thread.sleep(800L);
        return emulator;
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key); require(value != null && value.length() > 0, "Missing " + key); return value;
    }
    private static String hash(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(file);
        try { byte[] data = new byte[8192]; for (int n; (n = in.read(data)) >= 0;) digest.update(data, 0, n); }
        finally { in.close(); }
        byte[] bytes = digest.digest(); StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format("%02X", Integer.valueOf(b & 255))); return out.toString();
    }
    private static void require(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
