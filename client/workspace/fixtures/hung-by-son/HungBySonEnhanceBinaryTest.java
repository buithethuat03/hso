import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * ModUpLv Đập đồ inside the candidate (docs/HUNG_BY_SON_ENHANCE.md): the rewired native item menu,
 * command 110, MOD menu row -32 and module loader, the real Đập đồ menus clicked through the native
 * dispatcher, and the auto steps with packets captured at the native send queue — away from the
 * Làng Sói Trắng wizard. Reuses HungBySonAutoAttackBinaryTest's probe loader and world.
 */
public final class HungBySonEnhanceBinaryTest {
    private static final String HELPER = "HungBySonAutoAttackBinaryTest";
    private static int checks;
    private static ClassLoader loader;
    private static Class<?> port, entityType, itemType, tabType;
    /** From the popup fix on: ModUpLv's type-3 messages open the native Ok dialog, menu confirmations stay overhead. */
    private static boolean popups;
    /** Auto phục hồi thể lực registers its module right after Đập đồ. */
    private static boolean stamina, buffSwap;

    private static void require(boolean value, String text) {
        checks++;
        if (!value) throw new AssertionError(text);
        System.out.println("PASS " + text);
    }

    static String n(String suffix) {
        StringBuilder b = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) b.append('i');
        return b.append(suffix).toString();
    }

    private static Properties load(File file) throws IOException {
        Properties p = new Properties(); InputStream in = new FileInputStream(file);
        try { p.load(in); } finally { in.close(); }
        return p;
    }
    private static Class<?> helper() throws Exception { return Class.forName(HELPER); }
    private static Method helper(String name, Class<?>... types) throws Exception {
        Method m = helper().getDeclaredMethod(name, types); m.setAccessible(true); return m;
    }
    private static Object invoke(Method m, Object target, Object... args) throws Exception {
        try { m.setAccessible(true); return m.invoke(target, args); }
        catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception) throw (Exception)c;
            if (c instanceof Error) throw (Error)c;
            throw new RuntimeException(c);
        }
    }
    private static Object construct(Class<?> type, Class<?>[] types, Object... args) throws Exception {
        Constructor<?> c = type.getDeclaredConstructor(types); c.setAccessible(true); return c.newInstance(args);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field f = unsafe.getDeclaredField("theUnsafe"); f.setAccessible(true);
        return unsafe.getMethod("allocateInstance", Class.class).invoke(f.get(null), type);
    }
    private static Class<?> cls(String name) throws Exception { return Class.forName(name.replace('/', '.'), true, loader); }
    private static void setNumber(Object target, Class<?> owner, String name, long value) throws Exception {
        Field f = field(owner, name);
        Class<?> t = f.getType();
        if (t == Byte.TYPE) f.setByte(target, (byte)value);
        else if (t == Short.TYPE) f.setShort(target, (short)value);
        else if (t == Integer.TYPE) f.setInt(target, (int)value);
        else if (t == Long.TYPE) f.setLong(target, value);
        else throw new IllegalArgumentException(name + " " + t);
    }
    private static void setStatic(Class<?> owner, String name, Object value) throws Exception { field(owner, name).set(null, value); }
    private static Object getStatic(Class<?> owner, String name) throws Exception { return field(owner, name).get(null); }

    private static Object list(Object... items) throws Exception {
        Object v = invoke(helper("newNativeList", Class.class, String.class), null, cls("et"), "Enhance test");
        Method add = cls("et").getMethod(n("l"), Object.class);
        for (Object i : items) add.invoke(v, i);
        return v;
    }
    private static int size(Object list) throws Exception { return ((Integer)cls("et").getMethod("c").invoke(list)).intValue(); }
    private static Object at(Object list, int i) throws Exception { return cls("et").getMethod("a", Integer.TYPE).invoke(list, Integer.valueOf(i)); }

    private static Object lastPopup() throws Exception { return invoke(probeMethod("lastPopup"), probe); }
    private static Object probe;
    private static Method probeMethod(String name) throws Exception { return probe.getClass().getDeclaredMethod(name); }
    private static String title() throws Exception { return (String)field(lastPopup().getClass(), "title").get(lastPopup()); }
    private static Object rows() throws Exception { return field(lastPopup().getClass(), "items").get(lastPopup()); }
    private static String label(Object row) throws Exception { return (String)field(cls("bt"), n("lliiilli")).get(row); }
    private static int action(Object row) throws Exception { return field(cls("bt"), "e").getByte(row); }
    private static int extra(Object row) throws Exception { return field(cls("bt"), "f").getByte(row); }
    private static List<String> labels(Object rows) throws Exception {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < size(rows); i++) out.add(label(at(rows, i)) + "#" + action(at(rows, i)));
        return out;
    }
    private static List<String> labels() throws Exception { return labels(rows()); }
    private static Object row(String prefix) throws Exception {
        Object rows = rows();
        for (int i = 0; i < size(rows); i++) if (label(at(rows, i)).startsWith(prefix)) return at(rows, i);
        throw new AssertionError("No row " + prefix + " in " + title() + ": " + labels());
    }
    private static void click(Object row) throws Exception { invoke(cls("bt").getDeclaredMethod("a"), row); }

    @SuppressWarnings("unchecked")
    private static List<String> drain() throws Exception {
        List<String> all = (List<String>)getStatic(helper(), "nativePackets");
        synchronized (all) { List<String> copy = new ArrayList<String>(all); all.clear(); return copy; }
    }
    private static String packet(int op, int... bytes) {
        StringBuilder b = new StringBuilder().append(op).append(":[");
        for (int i = 0; i < bytes.length; i++) { if (i > 0) b.append(", "); b.append((byte)bytes[i]); }
        return b.append("]").toString();
    }
    private static String enhance(int sub, int id, int category) { return packet(67, sub, (id >> 8) & 255, id & 255, category); }

    private static long now = 1000000L;
    private static void tick() throws Exception {
        setStatic(port, "testNow", Long.valueOf(now));
        port.getMethod("update").invoke(getStatic(port, "INSTANCE"));
    }
    /** Advance one pause and tick once. */
    private static List<String> step() throws Exception {
        now += pauseMs();
        tick();
        return drain();
    }
    private static long pauseMs() throws Exception { return field(port, "pause").getInt(null) * 28L; }
    private static boolean enabled() throws Exception { return field(port, "enabled").getBoolean(null); }
    private static int queued() throws Exception { return size(getStatic(port, "queue")); }

    private static Object item(int id, int category, int level, String name) throws Exception {
        Object item = allocate(itemType);
        setNumber(item, itemType, "O", id); setNumber(item, itemType, n("liiill"), category);
        setNumber(item, itemType, n("llilil"), level); field(itemType, n("liiiiliii")).set(item, name);
        return item;
    }
    private static void bag(Object... items) throws Exception { setStatic(itemType, n("liiillili"), list(items)); }
    private static void level(Object item, int level) throws Exception { setNumber(item, itemType, n("llilil"), level); }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("candidate.jar auto-attack.properties auto-pk.properties");
        File candidate = new File(args[0]);
        Properties auto = load(new File(args[1])), catalog = load(new File(args[2]));
        require("true".equals(catalog.getProperty("enhance.port")) && "HungBySonEnhance".equals(catalog.getProperty("enhance.port.owner")),
                "Catalog records the Đập đồ port");
        popups = "true".equals(catalog.getProperty("enhance.popup"));
        stamina = "true".equals(catalog.getProperty("stamina"));
        buffSwap = "true".equals(catalog.getProperty("buff.swap"));
        verifyStructure(candidate);

        probe = construct(Class.forName(HELPER + "$NativeProbeLoader"), new Class<?>[]{File.class, Properties.class}, candidate, auto);
        loader = (ClassLoader)probe;
        // The native Ok dialog wraps its text with the game font; the shared helper seeds its metrics.
        Class<?> probeType = Class.forName(HELPER + "$NativeProbeLoader");
        invoke(helper("seedNativeFontMetrics", probeType), null, probe);
        port = cls("HungBySonEnhance");
        entityType = cls(catalog.getProperty("entity.owner"));
        itemType = cls(n("llilli"));
        tabType = cls(n("lllili"));
        Class<?> bridgeType = cls("luna/generated/HungAutoAttackAccess");
        Object bridge = bridgeType.getDeclaredConstructor().newInstance();
        Object access = construct(Class.forName(HELPER + "$NativeAccess"), new Class<?>[]{ClassLoader.class, Object.class, Properties.class}, loader, bridge, auto);
        invoke(access.getClass().getDeclaredMethod("installReadyWorld"), access);
        require(((Boolean)bridgeType.getMethod("farmWorldReady").invoke(null)).booleanValue(), "Test world is ready (logged in, alive)");
        Object player = getStatic(cls(n("liiliii")), n("llilliill"));
        setNumber(player, entityType, "bD", 1000000L);

        verifyModules();
        verifyMenus();
        verifyItemMenu();
        verifyAuto(player);
        System.out.println("HungBySonEnhanceBinaryTest PASS checks=" + checks);
    }

    // --------------------------------------------------------------- bytecode wiring

    private static void verifyStructure(File candidate) throws Exception {
        JarFile jar = new JarFile(candidate);
        try {
            String old = n("lliillii");
            int oldMenu = 0, oldAdd = 0, oldShow = 0, newMenu = 0, newAdd = 0, newShow = 0;
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                JarEntry z = e.nextElement();
                if (!z.getName().endsWith(".class") || z.getName().equals(old + ".class") || z.getName().equals("HungBySonEnhance.class")) continue;
                ClassNode c = new ClassNode();
                new ClassReader(jar.getInputStream(z)).accept(c, 0);
                for (MethodNode m : c.methods) for (AbstractInsnNode x : m.instructions) {
                    if (!(x instanceof MethodInsnNode)) continue;
                    MethodInsnNode k = (MethodInsnNode)x;
                    if (k.owner.equals(old) && k.name.equals(n("l")) && k.desc.equals("(Let;Ljava/lang/Object;)Let;")) oldMenu++;
                    if (k.owner.equals(old) && k.name.equals(n("lil"))) oldAdd++;
                    if (k.owner.equals(old) && k.name.equals(n("liliilll"))) oldShow++;
                    if (k.owner.equals("HungBySonEnhance") && k.name.equals("itemMenu")) { newMenu++; require(c.name.equals(n("liililli")), "itemMenu is called by the item screen"); }
                    if (k.owner.equals("HungBySonEnhance") && k.name.equals("add")) { newAdd++; require(c.name.equals(n("liililli")) && m.name.equals("a") && m.desc.equals("(II)V"), "add is called by the item command handler"); }
                    if (k.owner.equals("HungBySonEnhance") && k.name.equals("showMenu")) { newShow++; require(c.name.equals(n("lillllll")) && m.name.equals(n("lliii")), "showMenu is called by the MOD dispatcher"); }
                }
            }
            require(oldMenu == 0 && oldAdd == 0 && oldShow == 0, "No native caller reaches the old 'Thêm vào DS' row, its queue or its menu");
            require(newMenu == 1 && newAdd == 1 && newShow == 1, "Item menu, command 110 and -32 each reach HungBySonEnhance once");
            ClassNode portNode = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry("HungBySonEnhance.class"))).accept(portNode, 0);
            require(portNode.version == Opcodes.V1_4 && portNode.interfaces.contains("IModModule"), "Port class is a class-48 IModModule");
        } finally { jar.close(); }
    }

    private static void verifyModules() throws Exception {
        Class<?> mods = cls(n("lillllll"));
        mods.getMethod(n("lilliiii")).invoke(null);
        Vector<?> modules = (Vector<?>)getStatic(mods, n("liliiliilil"));
        int mine = modules.indexOf(getStatic(port, "INSTANCE"));
        // From Auto phục hồi thể lực on, its module registers between Đập đồ and the ModUpLv port.
        int gap = (stamina ? 1 : 0) + (buffSwap ? 1 : 0);
        require(mine >= 0 && mine == modules.size() - 2 - gap && modules.lastElement() == getStatic(cls("HungBySonModUpLv"), "INSTANCE")
                && (!stamina || modules.get(mine + 1) == getStatic(cls("HungBySonStamina"), "INSTANCE")),
                "Loader registers Đập đồ right before " + (stamina ? "the stamina module and " : "") + "the ModUpLv port, which stays last");
        String guide = (String)getStatic(cls(n("lliiliil")), n("lilliliiili"));
        if (guide == null) guide = (String)cls("HungBySonOfflineLocal").getMethod("guide").invoke(null);
        require(guide.contains("Đập đồ: trong túi đồ chọn trang bị > Đập đồ") && guide.contains("không cần về làng")
                && !guide.contains("Thêm vào DS") && guide.contains("Auto Đi Buôn V3"), "Startup guide explains Đập đồ and keeps the other paragraphs");
    }

    // --------------------------------------------------------------- menus

    private static void verifyMenus() throws Exception {
        Class<?> mods = cls(n("lillllll"));
        setStatic(cls(n("lliiliil")), n("lilliililli"), Boolean.FALSE);
        setStatic(cls(n("lliiliil")), n("lilliiliili"), "test");
        mods.getMethod(n("lilliiil")).invoke(null);
        List<String> rows = labels();
        require(title().equals("MOD HungDZ") && rows.contains("Đập đồ >#-32") && !rows.toString().contains("Cường Hóa"),
                "MOD menu: 'Auto Cường Hóa >' is now 'Đập đồ >' (-32); rows=" + rows);
        click(row("Đập đồ >"));
        require(title().equals("ĐẬP ĐỒ") && labels().equals(Arrays.asList("Auto đập đồ lên +0: [TẮT]#-10", "Tăng cấp đập [+]#-11",
                "Giảm cấp đập [-]#-12", "Chọn bảo hiểm cho cấp đập >#-13", "Khoảng nghỉ: 30 (840ms) [+]#-16",
                "Khoảng nghỉ: 30 (840ms) [-]#-17", "Xem danh sách đồ cần đập (0) >#-18", "<< Quay lại#-34")),
                "Đập đồ menu has ModUpLv's rows with diacritics: " + labels());
        click(row("Auto đập đồ"));
        require(!enabled() && title().equals("ĐẬP ĐỒ"), "Auto stays OFF at +0, like ModUpLv");
        dialog("Không thể đập đồ lên +0, hãy tăng cấp muốn đập!", "+0 refusal");
        click(row("Giảm cấp")); click(row("Giảm cấp"));
        require(field(port, "target").getInt(null) == 0, "Giảm cannot go below the floor");
        click(row("Tăng cấp")); click(row("Tăng cấp")); click(row("Tăng cấp"));
        require(field(port, "target").getInt(null) == 3 && labels().get(0).equals("Auto đập đồ lên +3: [TẮT]#-10"), "Tăng raises the target to +3: " + labels().get(0));
        noDialog("Cấp đập tăng");
        for (int i = 0; i < 20; i++) click(row("Tăng cấp"));
        require(field(port, "target").getInt(null) == 15, "Target stops at +15");
        for (int i = 0; i < 20; i++) click(row("Giảm cấp"));
        require(field(port, "target").getInt(null) == 1, "Target stops at +1 going down");
        click(row("Tăng cấp")); click(row("Tăng cấp"));

        for (int i = 0; i < 40; i++) click(rowEnding("[-]"));
        require(field(port, "pause").getInt(null) == 1 && labels().get(5).equals("Khoảng nghỉ: 1 (28ms) [-]#-17"), "Khoảng nghỉ stays >= 1: " + labels().get(5));
        click(rowEnding("[+]"));
        require(field(port, "pause").getInt(null) == 2, "Khoảng nghỉ [+] adds one frame");

        click(row("Chọn bảo hiểm"));
        Object insuranceRows = rows();
        require(title().equals("CHỌN BẢO HIỂM") && size(insuranceRows) == 16 && label(at(insuranceRows, 0)).equals("Cấp +1 (Không dùng)")
                && label(at(insuranceRows, 14)).equals("Cấp +15 (Không dùng)") && action(at(insuranceRows, 1)) == -14 && extra(at(insuranceRows, 1)) == 2
                && labels().get(15).equals("<< Quay lại menu đập đồ#-32"), "Bảo hiểm menu lists +1..+15 with the level as the row extra: " + labels());
        click(row("Cấp +3 "));
        require(title().equals("BẢO HIỂM CẤP +3") && labels().equals(Arrays.asList("Không dùng#-15", "Đá may mắn#-15", "Cỏ ba lá#-15",
                "Cỏ bốn lá#-15", "Đá ba màu#-15", "<< Quay lại chọn bảo hiểm#-13")), "Per-level choices: " + labels());
        click(row("Cỏ ba lá"));
        require(((int[])getStatic(port, "insurance"))[3] == 2 && title().equals("CHỌN BẢO HIỂM") && label(row("Cấp +3 ")).equals("Cấp +3 (Cỏ ba lá)"),
                "Choosing Cỏ ba lá for +3 returns to the level list");
        click(row("<< Quay lại"));
        require(title().equals("ĐẬP ĐỒ"), "Back returns to the Đập đồ menu");
        click(row("<< Quay lại"));
        require(title().equals("MOD HungDZ"), "Back returns to MOD HungDZ");
    }

    private static Object rowEnding(String suffix) throws Exception {
        Object rows = rows();
        for (int i = 0; i < size(rows); i++) if (label(at(rows, i)).endsWith(suffix) && label(at(rows, i)).startsWith("Khoảng nghỉ")) return at(rows, i);
        throw new AssertionError("No row ending " + suffix + ": " + labels());
    }

    // --------------------------------------------------------------- item menu and command 110

    private static void verifyItemMenu() throws Exception {
        Class<?> screenType = cls(n("liililli"));
        Object screen = allocate(screenType);
        setNumber(screen, cls(n("liililii")), n("llllilliii"), 0);
        Method itemMenu = port.getMethod("itemMenu", cls("et"), Object.class);
        Object menu = invoke(itemMenu, null, list(), screen);
        List<String> rows = labels(menu);
        require(rows.size() >= 1 && rows.get(0).equals("Đập đồ#110") && !rows.toString().contains("Thêm vào DS"),
                "Bag item menu: 'Đập đồ' (110) replaces 'Thêm vào DS': " + rows);
        Object oldMenu = invoke(cls(n("lliillii")).getMethod(n("l"), cls("et"), Object.class), null, list(), screen);
        require(labels(oldMenu).get(0).equals("Thêm vào DS#110") && labels(oldMenu).subList(1, labels(oldMenu).size()).equals(rows.subList(1, rows.size())),
                "The S1/S2/S3 rows after it are the ones the old hook added: " + labels(oldMenu));
        setNumber(screen, cls(n("liililii")), n("llllilliii"), 16);
        require(!labels(invoke(itemMenu, null, list(), screen)).contains("Đập đồ#110"), "Like ModUpLv, only the bag tab offers Đập đồ");

        Object sword = item(100, 3, 1, "Kiếm"), clover = item(50, 7, 0, "Cỏ ba lá"), stone = item(51, 7, 0, "Đá may mắn");
        bag(sword, clover, stone);
        Method add = port.getMethod("add", itemType);
        invoke(add, null, sword);
        dialog("Đã thêm Kiếm", "Queued");
        invoke(add, null, item(100, 3, 1, "Kiếm"));
        dialog("Kiếm đã có trong danh sách.", "Duplicate");
        invoke(add, null, clover);
        dialog("Không thể đập vật phẩm này: Cỏ ba lá", "Not equipment");
        require(queued() == 1, "Equipment is queued once (matched by id); a clover is refused");
        // Command 110 on the real item screen goes through the rewired handler.
        cls("et").getMethod(n("lliliil")).invoke(getStatic(port, "queue"));
        setNumber(screen, cls(n("liililii")), n("llllilliii"), 0);
        setNumber(screen, screenType, n("liillliil"), 0);
        invoke(screenType.getMethod("a", Integer.TYPE, Integer.TYPE), screen, Integer.valueOf(110), Integer.valueOf(0));
        require(queued() == 1 && ((Vector<?>)getStatic(cls(n("lliillii")), n("lillliilill"))).isEmpty(),
                "Native command 110 queues the selected bag item for Đập đồ, not the old queue");
    }

    // --------------------------------------------------------------- auto

    private static void verifyAuto(Object player) throws Exception {
        Object sword = at(getStatic(itemType, n("liiillili")), 0), clover = at(getStatic(itemType, n("liiillili")), 1);
        setStatic(port, "target", Integer.valueOf(3));
        Object toggle = rowToggle();
        drain();
        tick();
        require(drain().isEmpty(), "Nothing is sent while Auto đập đồ is OFF");
        click(toggle);
        require(enabled(), "Auto đập đồ turns ON with a target");
        // Hung's old gate needed map 1 within 45 of the wizard at (432, 528); stand well outside it.
        Object map = getStatic(cls(n("liilllii")), n("liiililiiii"));
        int x = field(entityType, "aZ").getInt(player), y = field(entityType, "ba").getInt(player);
        require(field(map.getClass(), "d").getInt(map) == 1 && Math.abs(x - 432) + Math.abs(y - 528) > 45,
                "Player stands outside Hung's old wizard gate (map 1 at " + x + "," + y + ")");
        require(step().equals(Arrays.asList(enhance(0, 100, 3))), "Step 1 selects the equipment: 67 (0, 100, 3)");
        require(step().equals(Collections.<String>emptyList()), "Step 2 at +1 → +2: Không dùng sends nothing");
        require(step().equals(Arrays.asList(enhance(2, 0, 0))), "Step 3 enhances with gold: 67 (2, 0, 0), no position/NPC packet");
        level(sword, 2);
        require(step().equals(Arrays.asList(enhance(0, 100, 3))), "Next attempt reselects the equipment");
        require(step().equals(Arrays.asList(enhance(0, 50, 7))), "+2 → +3 uses the Cỏ ba lá insurance: 67 (0, 50, 7)");
        require(step().equals(Arrays.asList(enhance(2, 0, 0))), "Then enhances");
        now += 10; tick();
        require(drain().isEmpty(), "Nothing is sent before the pause elapses");

        // Không dùng removes a clover the server still reports as selected.
        level(sword, 1);
        setStatic(tabType, n("liliiliil"), Boolean.TRUE); setStatic(tabType, n("lilililil"), clover);
        step();
        require(step().equals(Arrays.asList(enhance(1, 50, 7))), "Không dùng with a held clover deselects it: 67 (1, 50, 7)");
        step();
        setStatic(tabType, n("liliiliil"), Boolean.FALSE); setStatic(tabType, n("lilililil"), null);

        // Missing insurance stops instead of enhancing unprotected.
        level(sword, 2);
        bag(sword);
        step();
        require(step().isEmpty() && !enabled(), "Missing Cỏ ba lá turns Auto OFF without the enhance packet");
        dialog("Hết Cỏ ba lá trong hành trang! Đã tắt Auto đập đồ", "Missing insurance");
        bag(sword, clover);
        enable();

        // Animation flag left by a background result is released after 500ms.
        setStatic(tabType, n("liiiliiii"), Byte.valueOf((byte)1));
        now += 1; tick();
        now += 499; tick();
        require(((Byte)getStatic(tabType, n("liiiliiii"))).byteValue() == 1, "Result animation flag kept for 500ms");
        now += 1; tick();
        require(((Byte)getStatic(tabType, n("liiiliiii"))).byteValue() == 0, "Then released so selection replies register again");
        drain();

        // Server refusal (reply type 3) stops; the hook runs even with the old module OFF.
        cls("HungBySonOfflineLocal").getMethod("onEnhancementReply", String.class, Integer.TYPE).invoke(null, "Không đủ điều kiện", Integer.valueOf(3));
        require(!enabled(), "Reply type 3 turns Auto OFF");
        dialog("Đập đồ: Không đủ điều kiện", "Server refusal");
        enable();
        cls("HungBySonOfflineLocal").getMethod("onEnhancementReply", String.class, Integer.TYPE).invoke(null, "Thành công", Integer.valueOf(2));
        require(enabled(), "Other reply types keep running");

        // Gold below 6000 stops.
        setNumber(player, entityType, "bD", 5999L);
        require(step().isEmpty() && !enabled(), "Below 6000 vàng Auto turns OFF");
        dialog("Sắp hết vàng rồi không đập nữa đâu!", "Low gold");
        setNumber(player, entityType, "bD", 1000000L);
        enable();

        // Target reached: the item leaves the queue.
        level(sword, 3);
        require(step().isEmpty() && queued() == 0 && enabled(), "+3 reached: the item leaves the queue, nothing sent");
        dialog("Item Kiếm đã đạt cấp độ mục tiêu, bỏ qua đập! Danh sách đồ cần đập đã hết.", "Target reached, last item");
        require(step().isEmpty(), "An empty queue sends nothing");

        // Queued item gone from the bag is dropped.
        invoke(port.getMethod("add", itemType), null, item(200, 3, 0, "Giáp"));
        dialog("Đã thêm Giáp", "Queued");
        require(queued() == 1 && step().isEmpty() && queued() == 0, "An item no longer in the bag is dropped");
        dialog("Item không còn trong hành trang, bỏ qua! Danh sách đồ cần đập đã hết.", "Missing item");

        // Auto Mê cung keeps exclusive ownership.
        Field maze = field(cls("HungBySonModUpLv"), "maze");
        maze.setBoolean(null, true);
        tick();
        require(!enabled(), "Auto Mê cung turns Đập đồ OFF");
        dialog("Auto Mê cung đang chạy: đã tắt Auto đập đồ", "Maze");
        port.getMethod("onAction", Integer.TYPE, Integer.TYPE).invoke(getStatic(port, "INSTANCE"), Integer.valueOf(-10), Integer.valueOf(0));
        require(!enabled(), "Đập đồ cannot be enabled during Auto Mê cung");
        maze.setBoolean(null, false);

        // List menu shows the queue and clears it.
        invoke(port.getMethod("add", itemType), null, sword);
        level(sword, 1);
        port.getMethod("showMenu").invoke(null);
        click(row("Xem danh sách"));
        require(title().equals("DANH SÁCH ĐỒ ĐẬP") && labels().equals(Arrays.asList("Kiếm +1#-18", "Xóa hết danh sách#-19", "<< Quay lại menu đập đồ#-32")),
                "Danh sách shows the queued item with its live level: " + labels());
        click(row("Xóa hết"));
        require(queued() == 0 && labels().equals(Arrays.asList("<< Quay lại menu đập đồ#-32")), "Xóa hết danh sách empties the queue");
        drain();
    }

    /** The last message opened the native Ok dialog (or, before the popup fix, nothing to check). */
    private static void dialog(String expected, String what) throws Exception {
        if (!popups) return;
        Class<?> main = cls(n("liilllii"));
        Object shown = getStatic(main, n("liiililiili"));
        require(expected.equals(getStatic(port, "popupText")) && shown != null && shown.getClass() == cls(n("llil")),
                what + ": native Ok dialog \"" + getStatic(port, "popupText") + "\"");
        setStatic(port, "popupText", null); setStatic(main, n("liiililiili"), null);
    }
    private static void noDialog(String what) throws Exception {
        if (!popups) return;
        require(getStatic(port, "popupText") == null && getStatic(cls(n("liilllii")), n("liiililiili")) == null, what + ": overhead text, no dialog");
    }

    private static Object rowToggle() throws Exception {
        port.getMethod("showMenu").invoke(null);
        return row("Auto đập đồ");
    }
    private static void enable() throws Exception {
        click(rowToggle());
        require(enabled(), "Auto đập đồ back ON");
        field(port, "nextAt").setLong(null, 0L);
        drain();
    }
}
