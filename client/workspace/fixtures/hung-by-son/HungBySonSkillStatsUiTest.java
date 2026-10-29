import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;
import org.microemu.MIDletBridge;

/**
 * The EXP (i) dialog tabs in the running candidate: open through the real ExpInfoBridge, switch tabs
 * through the real native pointer route, paint the native dialog plus the tab bar and table into
 * images, grow with a new skill, keep the Xác nhận close. Screenshots: skill-tab.png, exp-tab.png.
 */
public final class HungBySonSkillStatsUiTest {
    static int checks;
    static String n(String t) { StringBuilder b = new StringBuilder(); for (int i = t.length(); i < 100; i++) b.append('i'); return b.append(t).toString(); }
    static void check(boolean b, String s) { checks++; if (!b) throw new AssertionError(s); System.out.println("PASS " + s); }
    static Object helper(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = HungBySonExpInfoBinaryTest.class.getDeclaredMethod(name, types); m.setAccessible(true);
        try { return m.invoke(null, args); } catch (InvocationTargetException e) { Throwable t = e.getCause(); if (t instanceof Exception) throw (Exception)t; throw e; }
    }
    static Field field(Class<?> c, String n) throws Exception {
        while (c != null) { try { Field f = c.getDeclaredField(n); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) { c = c.getSuperclass(); } }
        throw new NoSuchFieldException(n);
    }
    static Object get(Class<?> c, String f) throws Exception { return field(c, f).get(null); }
    static void set(Class<?> c, String f, Object v) throws Exception { field(c, f).set(null, v); }
    static int num(Object o, String f) throws Exception { return ((Number)field(o.getClass(), f).get(o)).intValue(); }
    static Class<?> cls(ClassLoader l, String c) throws Exception { return Class.forName(c.replace('/', '.'), true, l); }
    static Object allocate(Class<?> c) throws Exception { return helper("constructOrAllocate", new Class<?>[]{Class.class}, c); }
    static Object call(Method m, Object target, Object... args) throws Exception {
        try { return m.invoke(target, args); } catch (InvocationTargetException e) { Throwable t = e.getCause(); if (t instanceof Exception) throw (Exception)t; throw e; }
    }

    public static void main(String[] a) throws Exception {
        check(a.length == 4, "candidate.jar exp.properties microemulator.jar screenshot-directory");
        File host = new File(HungBySonSkillStatsUiTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File java = new File(new File(System.getProperty("java.home"), "bin"), System.getProperty("os.name").contains("Windows") ? "java.exe" : "java");
        List<String> command = new ArrayList<String>();
        Collections.addAll(command, java.getAbsolutePath(), "-Xverify:all", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-Dsun.reflect.inflationThreshold=10000",
                "-Dhso.log.dir=" + new File(a[3], "skill-gap-logs").getAbsolutePath(), "-cp", host.getAbsolutePath() + File.pathSeparator + new File(a[2]).getAbsolutePath(), "HungBySonSkillStatsUiTest$Worker");
        Collections.addAll(command, a);
        int status = new ProcessBuilder(command).inheritIO().start().waitFor();
        check(status == 0, "Skill tab worker passed");
        System.out.println("HungBySonSkillStatsUiTest PASS");
    }

    public static final class Worker {
        public static void main(String[] a) {
            try { run(a); System.out.println("Skill tab native PASS checks=" + checks); System.exit(0); }
            catch (Throwable t) { t.printStackTrace(); System.exit(1); }
        }

        static Class<?> engine, ui, guard, stats, counters, graphicsType;
        static Method press, release, drag;
        static int scale;

        static void run(String[] a) throws Exception {
            Properties p = new Properties(); InputStream s = new FileInputStream(a[1]); try { p.load(s); } finally { s.close(); }
            File screens = new File(a[3]); screens.mkdirs();
            helper("startEmulator", new Class<?>[]{File.class}, new File(a[0]));
            MIDlet midlet = MIDletBridge.getCurrentMIDlet(); check(midlet != null, "Candidate MIDlet started");
            ClassLoader l = midlet.getClass().getClassLoader();
            engine = cls(l, p.getProperty("pointer.press.owner")); ui = cls(l, "luna/generated/ExpInfoBridge"); guard = cls(l, "luna/generated/ExpInfoModalGuard");
            stats = cls(l, "HungBySonSkillStats"); counters = cls(l, "luna/features/SkillCastStats"); graphicsType = cls(l, p.getProperty("slot.0.graphics.owner"));
            Class<?> world = cls(l, p.getProperty("slot.0.main.owner"));
            Object game = get(engine, n("liiiliiiiil"));
            if (game == null) { game = allocate(field(engine, n("liiiliiiiil")).getType()); set(engine, n("liiiliiiiil"), game); }
            set(engine, n("liiiillllli"), game);
            Object player = get(world, p.getProperty("slot.0.main.field"));
            if (player == null) { player = allocate(field(world, p.getProperty("slot.0.main.field")).getType()); set(world, p.getProperty("slot.0.main.field"), player); }
            field(player.getClass(), "cu").set(player, Integer.valueOf(321)); field(player.getClass(), "bz").set(player, Short.valueOf((short)70));
            field(player.getClass(), "bA").set(player, Short.valueOf((short)500)); field(player.getClass(), "cG").set(player, Integer.valueOf(0));
            set(world, n("lliilliil"), Boolean.FALSE); set(engine, n("liiililiili"), null); set(engine, n("liiililiill"), null); set(cls(l, n("llilil")), n("liiillli"), Boolean.FALSE);
            Object menu = get(engine, n("liiiliillll")); if (menu != null) field(menu.getClass(), n("liiillli")).set(menu, Boolean.FALSE);
            set(engine, n("lllillll"), Integer.valueOf(240)); set(engine, n("liiilliilii"), Integer.valueOf(120)); set(engine, n("liiiiiil"), Integer.valueOf(320));
            press = engine.getMethod(p.getProperty("pointer.press.method"), Integer.TYPE, Integer.TYPE);
            release = engine.getMethod(p.getProperty("pointer.release.method"), Integer.TYPE, Integer.TYPE);
            drag = engine.getMethod(p.getProperty("pointer.drag.method"), Integer.TYPE, Integer.TYPE);
            scale = ((Integer)helper("graphicsScale", new Class<?>[]{ClassLoader.class, Properties.class}, l, p)).intValue();

            // Skill names from the native skill registry; skill 5 has no template.
            Class<?> skillType = cls(l, n("liiiiill"));
            Object registry = get(cls(l, n("lillilll")), "a");
            Method add = registry.getClass().getMethod(n("l"), Object.class);
            String[][] named = {{"20", "Lôi Thần"}, {"19", "Băng Phong Trảm Siêu Cấp Vô Song Thiên Hạ"}, {"2", "Chém"}};
            for (String[] sk : named) {
                Object t = allocate(skillType);
                field(skillType, "b").set(t, Integer.valueOf(Integer.parseInt(sk[0])));
                field(skillType, n("lllilllil")).set(t, sk[1]);
                add.invoke(registry, t);
            }
            Method sent = counters.getMethod("sent", Integer.TYPE, Long.TYPE), answered = counters.getMethod("answered", Integer.TYPE, Boolean.TYPE, Long.TYPE);
            long t = 1000000L;
            set(stats, "testNow", Long.valueOf(t));
            counters.getMethod("reset").invoke(null);
            for (int i = 0; i < 312; i++) { sent.invoke(null, Integer.valueOf(20), Long.valueOf(t)); answered.invoke(null, Integer.valueOf(20), Boolean.valueOf(i % 60 != 0), Long.valueOf(t)); }
            for (int i = 0; i < 280; i++) { sent.invoke(null, Integer.valueOf(19), Long.valueOf(t)); answered.invoke(null, Integer.valueOf(19), Boolean.valueOf(i % 20 != 0), Long.valueOf(t)); }
            for (int i = 0; i < 150; i++) { sent.invoke(null, Integer.valueOf(2), Long.valueOf(t)); answered.invoke(null, Integer.valueOf(2), Boolean.TRUE, Long.valueOf(t)); }
            sent.invoke(null, Integer.valueOf(5), Long.valueOf(t));

            check(((Boolean)ui.getMethod("ready").invoke(null)).booleanValue(), "Ready in native world");
            ui.getMethod("open").invoke(null);
            Object dialog = get(engine, n("liiililiili"));
            check(dialog != null && ((Boolean)guard.getMethod("owned").invoke(null)).booleanValue(), "EXP dialog opened and owned");
            String[] lines = (String[])field(dialog.getClass(), n("lllliliii")).get(dialog);
            check(lines.length > 1 && lines[0].length() == 0 && !lines[1].trim().isEmpty(), "EXP tab: a blank first line holds the tab bar above the EXP text; " + Arrays.asList(lines));
            int nativeWidth = num(dialog, "l");
            stats.getMethod("uiTick").invoke(null);   // the canvas loop runs it before every repaint
            int expWidth = num(dialog, "l");
            check(expWidth == 232 && expWidth >= nativeWidth && num(dialog, "n") >= 0 && num(dialog, "n") + expWidth <= 240, "The canvas loop widens the EXP dialog for the tab bar, within the screen");
            render(dialog, screens, "exp-tab.png", true);

            if (((Integer)get(stats, "TABS")).intValue() == 4) goldTab(l, dialog, screens);

            int[] skillTab = tabCenter(dialog, 1);
            tap(skillTab);
            check(((Integer)get(stats, "tab")).intValue() == 1 && get(engine, n("liiililiili")) == dialog && ((Boolean)guard.getMethod("owned").invoke(null)).booleanValue(),
                    "Native pointer press+release on the second tab switches the same owned dialog to 'Chi tiết tung chiêu'");
            lines = (String[])field(dialog.getClass(), n("lllliliii")).get(dialog);
            check(lines.length == 8 && allBlank(lines), "Table rows reserved: tab bar, header, 4 skills, total, né/không trả lời line (" + lines.length + ")");
            int[] ids = (int[])get(stats, "ids");
            check(Arrays.equals(ids, new int[]{20, 19, 5, 2}), "Skills listed by ID, highest first: " + Arrays.toString(ids));
            String[] names = (String[])get(stats, "names"), cast = (String[])get(stats, "cast"), miss = (String[])get(stats, "miss"), rate = (String[])get(stats, "rate");
            check(names[0].equals("Lôi Thần") && names[2].equals("Chiêu 5") && cast[0].equals("312") && miss[0].equals("6") && rate[0].equals("98,1%")
                    && cast[2].equals("1") && miss[2].equals("0") && rate[2].equals("-") && rate[3].equals("100%"),
                    "Rows: name, casts, misses, hit rate (unanswered skill shows '-'); " + Arrays.asList(names) + Arrays.asList(cast) + Arrays.asList(miss) + Arrays.asList(rate));
            check("743".equals(get(stats, "totalCast")) && "20".equals(get(stats, "totalMiss")) && "97,3%".equals(get(stats, "totalRate")),
                    "Total row: " + get(stats, "totalCast") + "/" + get(stats, "totalMiss") + "/" + get(stats, "totalRate"));
            check(num(dialog, "l") == expWidth, "Both tabs share one width");
            check(num(dialog, "o") >= 0, "The dialog fits vertically");
            render(dialog, screens, "skill-tab.png", false);

            sent.invoke(null, Integer.valueOf(3), Long.valueOf(t));
            set(stats, "testNow", Long.valueOf(t + 300));
            stats.getMethod("uiTick").invoke(null);
            lines = (String[])field(dialog.getClass(), n("lllliliii")).get(dialog);
            check(lines.length == 9 && Arrays.equals((int[])get(stats, "ids"), new int[]{20, 19, 5, 3, 2}), "A new skill appears live and the dialog grows (" + lines.length + ")");
            set(stats, "testNow", Long.valueOf(t + 2400));
            stats.getMethod("uiTick").invoke(null);
            check("1".equals(((String[])get(stats, "miss"))[2]) && "0%".equals(((String[])get(stats, "rate"))[2]), "An unanswered cast becomes a miss after 2s while the tab is open");

            int[] expTab = tabCenter(dialog, 0);
            call(press, null, Integer.valueOf(expTab[0]), Integer.valueOf(expTab[1]));
            call(drag, null, Integer.valueOf(0), Integer.valueOf(0));
            call(release, null, Integer.valueOf(0), Integer.valueOf(0));
            check(((Integer)get(stats, "tab")).intValue() == 1 && get(engine, n("liiililiili")) == dialog, "Releasing outside the tab changes nothing and keeps the dialog");
            tap(expTab);
            lines = (String[])field(dialog.getClass(), n("lllliliii")).get(dialog);
            check(((Integer)get(stats, "tab")).intValue() == 0 && lines[0].length() == 0 && !lines[1].trim().isEmpty() && num(dialog, "l") == expWidth,
                    "Back to 'Chi tiết EXP': EXP text again, same width");

            int[] center = (int[])helper("confirmationCenter", new Class<?>[]{Object.class, Properties.class, Integer.TYPE}, dialog, p, Integer.valueOf(scale));
            tap(center);
            check(get(engine, n("liiililiili")) == null && !((Boolean)guard.getMethod("owned").invoke(null)).booleanValue(), "Xác nhận still closes the dialog");
            tap(skillTab);
            ui.getMethod("open").invoke(null);
            check(((Integer)get(stats, "tab")).intValue() == 0, "Every opening starts on the EXP tab");
            Object again = get(engine, n("liiililiili"));
            tap(tabCenter(again, 1));
            tap((int[])helper("confirmationCenter", new Class<?>[]{Object.class, Properties.class, Integer.TYPE}, again, p, Integer.valueOf(scale)));
            check(get(engine, n("liiililiili")) == null, "Xác nhận closes from the table tab too");
            gapTab(p, sent, answered, t + 10000, screens);
        }

        static void goldTab(ClassLoader loader, Object dialog, File screens) throws Exception {
            Class<?> gold = cls(loader, "luna/features/GoldRateSupport");
            Method snapshot = gold.getMethod("snapshot", String.class, Integer.TYPE, Long.TYPE);
            Method balance = gold.getMethod("balance", String.class, Integer.TYPE, Long.TYPE, Long.TYPE);
            long now = System.currentTimeMillis();
            snapshot.invoke(null, "ui-gold", 321, now - 1000000L);
            balance.invoke(null, "ui-gold", 321, 10000L, now - 1000000L);
            balance.invoke(null, "ui-gold", 321, 10100L, now - 900001L);
            balance.invoke(null, "ui-gold", 321, 12600L, now - 1000L);
            tap(tabCenter(dialog, 3));
            check(((Integer)get(stats, "tab")).intValue() == 3, "Fourth tab opens gold information through the native pointer route");
            String body = Arrays.toString((String[])field(dialog.getClass(), n("lllliliii")).get(dialog));
            check(body.contains("Vàng hiện tại") && body.contains("12.600") && body.contains("Vàng/giờ")
                    && body.contains("10.000") && body.contains("2.600"), "Gold tab shows wallet, rolling rate and full login receipts: " + body);
            render(dialog, screens, "gold-tab.png", false);
            balance.invoke(null, "ui-gold", 321, 12700L, System.currentTimeMillis());
            set(stats, "economyAt", Long.valueOf(0));
            stats.getMethod("uiTick").invoke(null);
            String refreshed = Arrays.toString((String[])field(dialog.getClass(), n("lllliliii")).get(dialog));
            check(refreshed.contains("12.700") && refreshed.contains("2.700"), "Open gold tab refreshes the wallet and session receipts");
            render(dialog, screens, "gold-tab-updated.png", false);
            tap(tabCenter(dialog, 0));
            check(((Integer)get(stats, "tab")).intValue() == 0, "Gold tab returns to EXP without replacing the dialog");
        }

        /** Tab "Khoảng chờ": X per previous skill, the 100ms breakdown, the mode and clear buttons. */
        static void gapTab(Properties p, Method sent, Method answered, long t, File screens) throws Exception {
            counters.getMethod("clearGaps").invoke(null);
            check(((String)get(stats, "summary")).startsWith("Né "), "Summary line: " + get(stats, "summary"));
            for (int i = 0; i < 40; i++, t += 5000) {
                long gap = 100 + (i % 8) * 50;
                sent.invoke(null, Integer.valueOf(20), Long.valueOf(t));
                answered.invoke(null, Integer.valueOf(20), Boolean.TRUE, Long.valueOf(t + 50));
                sent.invoke(null, Integer.valueOf(2), Long.valueOf(t + gap));
                answered.invoke(null, Integer.valueOf(2), Boolean.valueOf(gap >= 300), Long.valueOf(t + gap + 50));
            }
            set(stats, "testNow", Long.valueOf(t));
            ui.getMethod("open").invoke(null);
            Object dialog = get(engine, n("liiililiili"));
            tap(tabCenter(dialog, 2));
            check(((Integer)get(stats, "tab")).intValue() == 2 && get(engine, n("liiililiili")) == dialog, "The third tab 'Khoảng chờ' opens on the same dialog");
            int[] gapIds = (int[])get(stats, "gapIds");
            String[] x = (String[])get(stats, "gapX"), below = (String[])get(stats, "gapBelow"), above = (String[])get(stats, "gapAbove");
            check(Arrays.equals(gapIds, new int[]{20, 2}) && x[0].equals("300") && below[0].equals("0%") && above[0].equals("100%") && x[1].equals("<=1000"),
                    "After skill 20: X = 300ms, 0% below, 100% above; " + Arrays.toString(gapIds) + Arrays.asList(x) + Arrays.asList(below) + Arrays.asList(above));
            String[] lines = (String[])field(dialog.getClass(), n("lllliliii")).get(dialog);
            check(lines.length == 6 && allBlank(lines), "Rows: tab bar, header, 2 skills, buttons, log path (" + lines.length + ")");
            render(dialog, screens, "gap-tab.png", false);

            tap(lineCenter(dialog, 1, 0.5));
            check(((Boolean)get(stats, "byNext")).booleanValue() && Arrays.equals((int[])get(stats, "gapIds"), new int[]{20, 2}), "Header tap: grouped by the send itself");
            tap(lineCenter(dialog, 1, 0.5));
            check(!((Boolean)get(stats, "byNext")).booleanValue(), "Header tap again: by the previous skill");
            tap(lineCenter(dialog, 2, 0.5));
            String[] label = (String[])get(stats, "binLabel"), sends = (String[])get(stats, "binSends"), rate = (String[])get(stats, "binRate");
            lines = (String[])field(dialog.getClass(), n("lllliliii")).get(dialog);
            check(((Integer)get(stats, "detail")).intValue() == 20 && label.length == 5 && label[0].equals("100-200ms") && label[4].equals("1000ms+")
                    && sends[0].equals("10") && rate[0].equals("0%") && label[2].equals("300-400ms") && sends[2].equals("10") && rate[2].equals("100%")
                    && sends[4].equals("20") && lines.length == 9,
                    "Row tap: 100ms groups with samples after skill 20; " + Arrays.asList(label) + Arrays.asList(sends) + Arrays.asList(rate) + " lines " + lines.length);
            render(dialog, screens, "gap-detail.png", false);
            tap(lineCenter(dialog, 1, 0.5));
            check(((Integer)get(stats, "detail")).intValue() == -1, "Header tap leaves the breakdown");

            int buttons = ((Integer)get(stats, "shownRows")).intValue() - 2;
            tap(lineCenter(dialog, buttons, 0.25));
            check(((Integer)counters.getMethod("paceMode").invoke(null)).intValue() == 0, "Mode button: Auto (default) -> Tắt");
            counters.getMethod("setPaceMode", Integer.TYPE).invoke(null, Integer.valueOf(0));
            tap(lineCenter(dialog, buttons, 0.5));
            check(!((Boolean)counters.getMethod("retryEnabled").invoke(null)).booleanValue(), "Middle button: Tung lại Bật -> Tắt");
            tap(lineCenter(dialog, buttons, 0.5));
            check(((Boolean)counters.getMethod("retryEnabled").invoke(null)).booleanValue(), "Middle button again: Tung lại Bật");
            tap(lineCenter(dialog, buttons, 0.75));
            check(((int[])get(stats, "gapIds")).length == 0 && ((Integer)get(stats, "shownRows")).intValue() == 5, "Xóa số đo empties the tab");
            counters.getMethod("cyclePace").invoke(null);
            check(((Integer)counters.getMethod("paceMode").invoke(null)).intValue() == 1, "Tắt -> Khóa 500ms");
            counters.getMethod("setPaceMode", Integer.TYPE).invoke(null, Integer.valueOf(4));
            tap((int[])helper("confirmationCenter", new Class<?>[]{Object.class, Properties.class, Integer.TYPE}, dialog, p, Integer.valueOf(scale)));
            check(get(engine, n("liiililiili")) == null, "Xác nhận closes from the gap tab");
        }

        /** A point on native line `line` at a fraction of the dialog's inner width. */
        static int[] lineCenter(Object dialog, int line, double across) throws Exception {
            int left = num(dialog, "n") + 10, width = num(dialog, "l") - 20;
            return new int[]{(int)((left + width * across) * scale), (num(dialog, "o") + 12 + line * 15 + 5) * scale};
        }

        static boolean allBlank(String[] lines) { for (String s : lines) if (s.trim().length() > 0) return false; return true; }

        static int[] tabCenter(Object dialog, int tab) throws Exception {
            int w = (num(dialog, "l") - 16) / ((Integer)get(stats, "TABS")).intValue();
            return new int[]{(num(dialog, "n") + 8 + tab * w + w / 2) * scale, (num(dialog, "o") + 5 + 8) * scale};
        }

        static void tap(int[] at) throws Exception {
            call(press, null, Integer.valueOf(at[0]), Integer.valueOf(at[1]));
            call(release, null, Integer.valueOf(at[0]), Integer.valueOf(at[1]));
        }

        static void render(Object dialog, File screens, String name, boolean expTab) throws Exception {
            Image image = Image.createImage(240, 320);
            javax.microedition.lcdui.Graphics g = image.getGraphics();
            g.setColor(0x405060); g.fillRect(0, 0, 240, 320);
            Object wrapper = helper("bindNativeGraphics", new Class<?>[]{Class.class, javax.microedition.lcdui.Graphics.class}, graphicsType, g);
            Method paint = null;
            for (Method m : dialog.getClass().getDeclaredMethods())
                if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == graphicsType && m.getReturnType() == Void.TYPE) { m.setAccessible(true); paint = m; }
            check(paint != null, "Native dialog paint method");
            call(paint, dialog, wrapper);
            int[] before = pixels(image);
            call(stats.getMethod("paint", graphicsType), null, wrapper);
            int[] after = pixels(image);
            int x = num(dialog, "n"), y = num(dialog, "o"), w = num(dialog, "l");
            check(changed(before, after, x + 8, y + 5, w - 16, 16), name + ": the tab bar is painted over the dialog's blank first line");
            int selected = ((Integer)get(stats, "tab")).intValue();
            if (selected == 1 || selected == 2) check(changed(before, after, x + 8, y + 27, w - 16, 60), name + ": the table is painted");
            helper("writePng", new Class<?>[]{Image.class, File.class}, image, new File(screens, name));
        }

        static int[] pixels(Image image) { int[] px = new int[240 * 320]; image.getRGB(px, 0, 240, 0, 0, 240, 320); return px; }
        static boolean changed(int[] a, int[] b, int x, int y, int w, int h) {
            for (int r = Math.max(0, y); r < Math.min(320, y + h); r++) for (int c = Math.max(0, x); c < Math.min(240, x + w); c++) if (a[r * 240 + c] != b[r * 240 + c]) return true;
            return false;
        }
    }
}
