import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;
import org.microemu.MIDletBridge;

/**
 * ModUpLv_skill_stats.jar in MicroEmulator: every class links; casts are counted through ModUpLv's
 * real opcode-9 serializer, answers through its real combat handler; the counters restart when
 * Tấn công đặc biệt turns ON; the real "Tan cong / muc tieu" menu and its group-17 handler open the
 * native Ok dialog; the table is painted over it (screenshot moduplv-skill-stats.png).
 */
public final class ModUpLvSkillStatsUiTest {
    static int checks;
    static void check(boolean b, String s) { checks++; if (!b) throw new AssertionError(s); System.out.println("PASS " + s); }
    static Object helper(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = HungBySonExpInfoBinaryTest.class.getDeclaredMethod(name, types); m.setAccessible(true);
        try { return m.invoke(null, args); } catch (InvocationTargetException e) { Throwable t = e.getCause(); if (t instanceof Exception) throw (Exception)t; throw e; }
    }
    static Field field(Class<?> c, String n, Class<?> type) throws Exception {
        for (; c != null; c = c.getSuperclass()) for (Field f : c.getDeclaredFields()) if (f.getName().equals(n) && (type == null || f.getType() == type)) { f.setAccessible(true); return f; }
        throw new NoSuchFieldException(n.length() + "");
    }
    static Method method(Class<?> c, String n, Class<?>... types) throws Exception { Method m = c.getDeclaredMethod(n, types); m.setAccessible(true); return m; }
    /** The obfuscator reuses one name for methods that differ only in return type. */
    static Method singleton(Class<?> c, String n) throws Exception {
        for (Method m : c.getDeclaredMethods())
            if (m.getName().equals(n) && m.getParameterTypes().length == 0 && m.getReturnType() == c && Modifier.isStatic(m.getModifiers())) { m.setAccessible(true); return m; }
        throw new NoSuchMethodException("singleton");
    }
    static Object call(Method m, Object target, Object... args) throws Exception {
        try { return m.invoke(target, args); } catch (InvocationTargetException e) { Throwable t = e.getCause(); if (t instanceof Exception) throw (Exception)t; throw e; }
    }

    public static void main(String[] a) throws Exception {
        check(a.length == 5, "candidate.jar aliases.properties catalog.properties microemulator.jar screenshot-directory");
        File host = new File(ModUpLvSkillStatsUiTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File java = new File(new File(System.getProperty("java.home"), "bin"), System.getProperty("os.name").contains("Windows") ? "java.exe" : "java");
        List<String> command = new ArrayList<String>();
        Collections.addAll(command, java.getAbsolutePath(), "-Xverify:all", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-Dsun.reflect.inflationThreshold=10000",
                "-cp", host.getAbsolutePath() + File.pathSeparator + new File(a[3]).getAbsolutePath(), "ModUpLvSkillStatsUiTest$Worker");
        Collections.addAll(command, a);
        check(new ProcessBuilder(command).inheritIO().start().waitFor() == 0, "ModUpLv stats worker passed");
        System.out.println("ModUpLvSkillStatsUiTest PASS");
    }

    public static final class Worker {
        public static void main(String[] a) {
            try { run(a); System.out.println("ModUpLv stats native PASS checks=" + checks); System.exit(0); }
            catch (Throwable t) { t.printStackTrace(); System.exit(1); }
        }

        static Properties aliases = new Properties();
        static ClassLoader l;
        static Class<?> k(String alias) throws Exception { return Class.forName(aliases.getProperty(alias), true, l); }
        static String n(String alias) { return aliases.getProperty(alias); }

        static void run(String[] a) throws Exception {
            InputStream in = new FileInputStream(a[1]); try { aliases.load(new InputStreamReader(in, "UTF-8")); } finally { in.close(); }
            File screens = new File(a[4]); screens.mkdirs();
            helper("startEmulator", new Class<?>[]{File.class}, new File(a[0]));
            MIDlet midlet = MIDletBridge.getCurrentMIDlet(); check(midlet != null, "ModUpLv MIDlet started");
            l = midlet.getClass().getClassLoader();
            int linked = 0;
            JarFile jar = new JarFile(a[0]);
            try {
                for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                    String name = e.nextElement().getName();
                    if (!name.endsWith(".class")) continue;
                    Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), true, l); linked++;
                }
            } finally { jar.close(); }
            check(linked == 208, "All " + linked + " classes load and link (205 + 3 added)");
            Class<?> stats = Class.forName("ModUpLvSkillStats", true, l), counters = Class.forName("luna.features.SkillCastStats", true, l);
            Class<?> engine = k("K066"), manager = k("K154"), dialogBase = k("K141");

            // Local player and the special flag.
            Class<?> playerType = k("K042");
            Object player = helper("constructOrAllocate", new Class<?>[]{Class.class}, playerType);
            field(k("K010"), n("K149"), Integer.TYPE).setInt(player, 321);
            field(k("K151"), n("K143"), playerType).set(null, player);
            Field special = field(manager, n("K143"), Boolean.TYPE);
            Method frame = stats.getMethod("frame"), casts = counters.getMethod("casts", Integer.TYPE), misses = counters.getMethod("misses", Integer.TYPE), hits = counters.getMethod("hits", Integer.TYPE);
            special.setBoolean(null, false); call(frame, null);
            Method send = method(k("K183"), n("K143"), k("K126"), Byte.TYPE);
            Object sender = call(singleton(k("K183"), n("K143")), null);
            Object list = k("K126").getConstructor(String.class).newInstance("test");
            sendQuietly(send, sender, list, 8);
            check(((Integer)casts.invoke(null, 8)).intValue() == 0, "Special OFF: ModUpLv's own sends are not counted");
            special.setBoolean(null, true); call(frame, null);
            for (int i = 0; i < 3; i++) sendQuietly(send, sender, list, 8);
            sendQuietly(send, sender, list, 2);
            check(((Integer)casts.invoke(null, 8)).intValue() == 3 && ((Integer)casts.invoke(null, 2)).intValue() == 1,
                    "Special ON: ModUpLv's real opcode-9 serializer counts each cast of its skill");

            Method combat = method(k("K135"), n("K143"), k("K156"), Byte.TYPE, Byte.TYPE);
            answer(combat, 321, 8, 0, 0);
            answer(combat, 321, 8, 0, 45);
            answer(combat, 999, 8, 77);
            answer(combat, 321, 2, 0);
            check(((Integer)misses.invoke(null, 8)).intValue() == 1 && ((Integer)hits.invoke(null, 8)).intValue() == 1 && ((Integer)misses.invoke(null, 2)).intValue() == 1,
                    "ModUpLv's real combat handler: all-zero answer = miss, a damaged target = hit, another attacker ignored");

            // Real menu: the "Tan cong / muc tieu" row list and its handler.
            Object menus = call(singleton(k("K143"), n("K143")), null);
            Method attackMenu = method(k("K143"), n("K095"));
            try { call(attackMenu, menus); } catch (Exception popupNeedsWorld) { }
            Field active = field(engine, n("K143"), dialogBase);
            active.set(null, null);
            field(engine, n("K157"), Integer.TYPE).setInt(null, 240);
            field(engine, n("K067"), Integer.TYPE).setInt(null, 120);
            field(engine, n("K095"), Integer.TYPE).setInt(null, 320);
            call(method(k("K143"), n("K006"), Integer.TYPE), menus, Integer.valueOf(20));
            Object dialog = active.get(null);
            check(dialog != null && dialog.getClass() == k("K107"), "Group 17 / action 20 opens a native Ok dialog");
            String[] lines = (String[])field(dialogBase, n("K143"), String[].class).get(dialog);
            check(lines.length == 5 && lines[0].equals("Chi tiết tung chiêu") && lines[1].isEmpty() && lines[4].isEmpty(),
                    "Title line + blank rows for header, 2 skills and the total; " + Arrays.asList(lines));
            int x = field(dialogBase, n("K144"), Integer.TYPE).getInt(dialog), y = field(dialogBase, n("K177"), Integer.TYPE).getInt(dialog), w = field(dialogBase, n("K012"), Integer.TYPE).getInt(dialog);
            check(w == 232 && x == 4 && y >= 0, "Dialog widened to 232px and fits (" + x + "," + y + "," + w + ")");
            String[] rate = (String[])field(stats, "rate", null).get(null);
            check(Arrays.equals((int[])field(stats, "ids", null).get(null), new int[]{8, 2}) && rate[0].equals("50%") && rate[1].equals("0%")
                    && ((String[])field(stats, "names", null).get(null))[0].equals("Chiêu 8"), "Rows: IDs high first, rates, fallback name without a skill registry");

            Image image = Image.createImage(240, 320);
            javax.microedition.lcdui.Graphics g = image.getGraphics();
            g.setColor(0x405060); g.fillRect(0, 0, 240, 320);
            Object wrapper = helper("bindNativeGraphics", new Class<?>[]{Class.class, javax.microedition.lcdui.Graphics.class}, k("K093"), g);
            Method paint = method(k("K107"), n("K143"), k("K093"));
            call(paint, dialog, wrapper);
            int[] before = pixels(image);
            call(stats.getMethod("paint", Object.class), null, wrapper);
            int[] after = pixels(image);
            check(changed(before, after, x + 8, y + 27, w - 16, 60), "The table is painted over the dialog");
            helper("writePng", new Class<?>[]{Image.class, File.class}, image, new File(screens, "moduplv-skill-stats.png"));

            active.set(null, null);
            call(stats.getMethod("paint", Object.class), null, wrapper);
            check(field(stats, "dialog", null).get(null) == null, "Closed dialog: the table stops");
            special.setBoolean(null, false); call(frame, null);
            check(((Integer)casts.invoke(null, 8)).intValue() == 3, "Special OFF keeps the numbers");
            special.setBoolean(null, true); call(frame, null);
            check(((Integer)casts.invoke(null, 8)).intValue() == 0 && ((Integer)misses.invoke(null, 2)).intValue() == 0, "Special ON again: counters restart");
        }

        static void sendQuietly(Method send, Object sender, Object list, int skill) {
            try { send.invoke(sender, list, Byte.valueOf((byte)skill)); } catch (Throwable offline) { }
        }

        static void answer(Method combat, int attacker, int skill, int... damages) throws Exception {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeShort(attacker); out.writeByte(skill); out.writeByte(damages.length);
            for (int i = 0; i < damages.length; i++) { out.writeShort(600 + i); out.writeInt(damages[i]); out.writeInt(50); out.writeByte(1); out.writeByte(3); out.writeInt(7); }
            out.writeInt(100); out.writeInt(100); out.writeByte(0); out.writeInt(0);
            Object message = k("K156").getConstructor(Byte.TYPE, byte[].class).newInstance(Byte.valueOf((byte)9), bytes.toByteArray());
            try { combat.invoke(null, message, Byte.valueOf((byte)0), Byte.valueOf((byte)1)); } catch (InvocationTargetException nativeRest) { }
        }

        static int[] pixels(Image image) { int[] px = new int[240 * 320]; image.getRGB(px, 0, 240, 0, 0, 240, 320); return px; }
        static boolean changed(int[] p, int[] q, int x, int y, int w, int h) {
            for (int r = Math.max(0, y); r < Math.min(320, y + h); r++) for (int c = Math.max(0, x); c < Math.min(240, x + w); c++) if (p[r * 240 + c] != q[r * 240 + c]) return true;
            return false;
        }
    }
}
