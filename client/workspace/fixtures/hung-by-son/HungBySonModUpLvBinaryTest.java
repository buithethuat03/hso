import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * ModUpLv port inside the candidate: real native menus (Auto đánh, Cài Đặt Cấu Hình, MOD menu,
 * Phó bản / mê cung), the real Special target builder against map-wide monsters, and the module
 * tick/dialog hook for Lửa Thiêng, Ngã Tư Tử Thần, Làng Phủ Sương and Mê cung with the packets
 * captured at the native send queue. Reuses HungBySonAutoAttackBinaryTest's probe loader/world.
 */
public final class HungBySonModUpLvBinaryTest {
    private static final String HELPER = "HungBySonAutoAttackBinaryTest";
    private static int checks;
    private static ClassLoader loader;
    private static Object probe;
    private static Class<?> port, entityType, playerType;
    private static boolean stationary;
    private static boolean lootMenu;
    private static boolean fogOda;
    private static boolean fogRoute;

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
        try { return m.invoke(target, args); }
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
    private static Object call(Object receiver, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = receiver.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return invoke(m, receiver, args);
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
    private static long number(Object target, Class<?> owner, String name) throws Exception {
        Object v = field(owner, name).get(target);
        return ((Number)v).longValue();
    }
    private static void setStatic(Class<?> owner, String name, Object value) throws Exception { field(owner, name).set(null, value); }
    private static Object getStatic(Class<?> owner, String name) throws Exception { return field(owner, name).get(null); }
    private static boolean flag(String name) throws Exception { return field(port, name).getBoolean(null); }

    private static Object list(Object... items) throws Exception {
        Object v = invoke(helper("newNativeList", Class.class, String.class), null, cls("et"), "ModUpLv test");
        Method add = cls("et").getMethod(n("l"), Object.class);
        for (Object i : items) add.invoke(v, i);
        return v;
    }
    private static int size(Object list) throws Exception { return ((Integer)cls("et").getMethod("c").invoke(list)).intValue(); }
    private static Object at(Object list, int i) throws Exception { return cls("et").getMethod("a", Integer.TYPE).invoke(list, Integer.valueOf(i)); }

    private static Object entity(Class<?> type, int id, int kind, int x, int y, String name) throws Exception {
        Object e = allocate(type);
        setNumber(e, entityType, "cu", id); setNumber(e, entityType, "cv", kind);
        setNumber(e, entityType, "bs", 100); setNumber(e, entityType, "cG", 0);
        setNumber(e, entityType, "aZ", x); setNumber(e, entityType, "ba", y);
        field(entityType, "cC").set(e, name);
        return e;
    }
    private static Object monster(int id, int x, int y) throws Exception { return entity(entityType, id, 1, x, y, "Quái " + id); }
    private static void world(Object... entities) throws Exception {
        setStatic(cls(n("liiliii")), n("llillilll"), list(entities));
    }
    private static void map(int id) throws Exception {
        Object map = getStatic(cls(n("liilllii")), n("liiililiiii"));
        setNumber(map, map.getClass(), "d", id);
    }
    private static Object player() throws Exception { return getStatic(cls(n("liiliii")), n("llilliill")); }
    private static void at(Object e, int x, int y) throws Exception { setNumber(e, entityType, "aZ", x); setNumber(e, entityType, "ba", y); }

    // --------------------------------------------------------------- native popups and packets

    private static Object lastPopup() throws Exception { return call(probe, "lastPopup", new Class<?>[0]); }
    private static String title() throws Exception { return (String)field(lastPopup().getClass(), "title").get(lastPopup()); }
    private static Object rows() throws Exception { return field(lastPopup().getClass(), "items").get(lastPopup()); }
    private static String label(Object row) throws Exception { return (String)field(cls("bt"), n("lliiilli")).get(row); }
    private static int action(Object row) throws Exception { return field(cls("bt"), "e").getByte(row); }
    private static Object row(String prefix) throws Exception {
        Object rows = rows();
        for (int i = 0; i < size(rows); i++) if (label(at(rows, i)).startsWith(prefix)) return at(rows, i);
        throw new AssertionError("No row " + prefix + " in " + title() + ": " + labels());
    }
    private static List<String> labels() throws Exception {
        List<String> out = new ArrayList<String>(); Object rows = rows();
        for (int i = 0; i < size(rows); i++) out.add(label(at(rows, i)) + "#" + action(at(rows, i)));
        return out;
    }
    private static void click(Object row) throws Exception {
        Method m = cls("bt").getDeclaredMethod("a"); m.setAccessible(true); invoke(m, row);
    }

    @SuppressWarnings("unchecked")
    private static List<String> packets() throws Exception { return (List<String>)getStatic(helper(), "nativePackets"); }
    private static List<String> drain() throws Exception {
        List<String> all = packets();
        synchronized (all) { List<String> copy = new ArrayList<String>(all); all.clear(); return copy; }
    }
    private static String packet(int op, int... bytes) {
        StringBuilder b = new StringBuilder().append(op).append(":[");
        for (int i = 0; i < bytes.length; i++) { if (i > 0) b.append(", "); b.append((byte)bytes[i]); }
        return b.append("]").toString();
    }
    private static String shortPacket(int op, int value, int... tail) {
        int[] bytes = new int[2 + tail.length];
        bytes[0] = (value >> 8) & 255; bytes[1] = value & 255;
        System.arraycopy(tail, 0, bytes, 2, tail.length);
        return packet(op, bytes);
    }

    private static void tick(long now) throws Exception {
        setStatic(port, "testNow", Long.valueOf(now));
        Object instance = getStatic(port, "INSTANCE");
        port.getMethod("update").invoke(instance);
    }
    private static boolean dialog(String text) throws Exception {
        return ((Boolean)invoke(cls(n("lillllll")).getMethod(n("lilll"), String.class), null, text)).booleanValue();
    }
    private static void action(int code) throws Exception {
        Object instance = getStatic(port, "INSTANCE");
        port.getMethod("onAction", Integer.TYPE, Integer.TYPE).invoke(instance, Integer.valueOf(code), Integer.valueOf(0));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("candidate.jar auto-attack.properties port.properties");
        File candidate = new File(args[0]);
        Properties auto = load(new File(args[1])), catalog = load(new File(args[2]));
        stationary = "true".equals(catalog.getProperty("dungeon.stationary"));
        lootMenu = "true".equals(catalog.getProperty("loot.menu"));
        fogOda = "true".equals(catalog.getProperty("fog.oda"));
        fogRoute = "true".equals(catalog.getProperty("fog.route"));
        boolean pkUpgrade = "true".equals(catalog.getProperty("auto.pk.upgrade"));
        require("HungBySonModUpLv".equals(catalog.getProperty("modulv.port.owner"))
                && (pkUpgrade ? catalog.getProperty("normal.monsters.action") == null : "-117".equals(catalog.getProperty("normal.monsters.action"))),
                "Catalog records the port and the profile's monster-row visibility");
        verifyStructure(candidate, catalog);

        probe = construct(Class.forName(HELPER + "$NativeProbeLoader"), new Class<?>[]{File.class, Properties.class}, candidate, auto);
        loader = (ClassLoader)probe;
        port = cls("HungBySonModUpLv");
        entityType = cls(catalog.getProperty("entity.owner"));
        playerType = cls(catalog.getProperty("player.owner"));
        Class<?> bridgeType = cls("luna/generated/HungAutoAttackAccess");
        Object bridge = bridgeType.getDeclaredConstructor().newInstance();
        Object access = construct(Class.forName(HELPER + "$NativeAccess"), new Class<?>[]{ClassLoader.class, Object.class, Properties.class}, loader, bridge, auto);
        call(access, "installReadyWorld", new Class<?>[0]);
        Object player = player();
        setNumber(player, entityType, "cG", 0);
        Class<?> runtime = cls("luna/features/AutoAttackRuntime");
        installController(runtime, bridgeType, bridge);
        require(((Boolean)bridgeType.getMethod("farmWorldReady").invoke(null)).booleanValue(), "Test world is Farm-ready (logged in, alive)");

        verifyMenus(bridgeType, bridge, runtime);
        // Menu tests may enter exclusive Maze mode, which deliberately turns Special OFF.
        // The following target-builder scenario requires Special ON independently of menus.
        if (!((Boolean)runtime.getMethod("specialEnabled").invoke(null)).booleanValue())
            runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-116));
        verifyTargets(player, runtime);
        verifyFire(player);
        verifyDungeon(player, runtime);
        verifyFog(player);
        verifyMaze(player);
        System.out.println("HungBySonModUpLvBinaryTest PASS checks=" + checks);
    }

    private static void installController(Class<?> runtime, Class<?> bridgeType, final Object bridge) throws Exception {
        Class<?> settings = cls("luna/api/AutoAttackSettings"), clock = cls("luna/api/LunaClock"), accessApi = cls("luna/api/AutoAttackAccess");
        Object store = Proxy.newProxyInstance(loader, new Class<?>[]{settings}, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) { if (m.getName().equals("load")) { int[] v = (int[])a[0]; v[0] = 0; v[1] = 650; } return null; }
        });
        Object time = Proxy.newProxyInstance(loader, new Class<?>[]{clock}, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) { return Long.valueOf(System.currentTimeMillis()); }
        });
        runtime.getMethod("installForTest", accessApi, settings, clock).invoke(null, bridge, store, time);
    }

    private static void verifyStructure(File candidate, Properties catalog) throws Exception {
        JarFile jar = new JarFile(candidate);
        try {
            ClassNode cast = new ClassNode();
            new ClassReader(readAll(jar, catalog.getProperty("cast.owner") + ".class")).accept(cast, 0);
            int hooks = 0, builders = 0;
            for (MethodNode m : cast.methods) for (AbstractInsnNode x : m.instructions) {
                if (!(x instanceof MethodInsnNode)) continue;
                MethodInsnNode c = (MethodInsnNode)x;
                if (c.owner.equals("HungBySonModUpLv") && c.name.equals("specialTargets")) { require(m.name.equals("codexSpecialSend"), "specialTargets only in the Special send"); hooks++; }
                if (m.name.equals("codexSpecialSend") && c.owner.equals(catalog.getProperty("player.owner")) && c.name.equals(n("")) && c.desc.endsWith(")Let;")) builders++;
            }
            require(hooks == 1 && builders == 0, "The Special send builds its targets only through HungBySonModUpLv.specialTargets");
            ClassNode portNode = new ClassNode();
            new ClassReader(readAll(jar, "HungBySonModUpLv.class")).accept(portNode, 0);
            require(portNode.version == Opcodes.V1_4 && portNode.interfaces.contains("IModModule"), "Port class is a class-48 IModModule");
        } finally { jar.close(); }
    }

    private static byte[] readAll(JarFile jar, String name) throws IOException {
        InputStream in = jar.getInputStream(jar.getJarEntry(name));
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192];
        for (int k; (k = in.read(b)) > 0;) out.write(b, 0, k);
        in.close(); return out.toByteArray();
    }

    // --------------------------------------------------------------- menus

    private static void verifyMenus(Class<?> bridgeType, Object bridge, Class<?> runtime) throws Exception {
        // The game runs the module loader when it first paints the MENU button.
        Class<?> mods = cls(n("lillllll"));
        mods.getMethod(n("lilliiii")).invoke(null);
        Vector<?> modules = (Vector<?>)getStatic(mods, n("liliiliilil"));
        require(modules.size() > 2 && modules.lastElement() == getStatic(port, "INSTANCE"),
                "The native module loader registers the port last (" + modules.size() + " modules)");
        bridgeType.getMethod("showMenu", Boolean.TYPE, Boolean.TYPE, Integer.TYPE).invoke(bridge, Boolean.FALSE, Boolean.FALSE, Integer.valueOf(650));
        List<String> rows = labels();
        boolean pkUpgrade = rows.size() == 6 && rows.get(2).startsWith("Auto PK");
        if (pkUpgrade) {
            require(rows.get(5).equals("Tấn công đặc biệt: [TẮT]#-116"), "Upgrade hides the monster toggle and exposes Auto PK: " + rows);
            // Only the compatibility harness uses the hidden historical ABI to exercise old casts.
            runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-117));
        } else {
        require(rows.size() == 7 && rows.get(5).equals("Tấn công đặc biệt: [TẮT]#-116") && rows.get(6).equals("Tấn công quái thường: [BẬT]#-117"),
                "Auto đánh menu: 'Tấn công quái thường: [BẬT]' (-117, ON by default like ModUpLv) follows 'Tấn công đặc biệt'; rows=" + rows);
        click(row("Tấn công quái thường"));
        rows = labels();
        require(rows.get(6).equals("Tấn công quái thường: [TẮT]#-117") && rows.get(5).equals("Tấn công đặc biệt: [TẮT]#-116"),
                "Actual row click turns the default OFF without touching Tấn công đặc biệt; rows=" + rows);
        click(row("Tấn công quái thường"));
        rows = labels();
        require(rows.get(6).equals("Tấn công quái thường: [BẬT]#-117") && rows.get(5).equals("Tấn công đặc biệt: [BẬT]#-116")
                        && ((Boolean)runtime.getMethod("normalMonstersActive").invoke(null)).booleanValue(),
                "Actual row click turns normal monsters ON with Tấn công đặc biệt (its target mode); rows=" + rows);
        click(row("Tấn công quái thường"));
        require(labels().get(6).equals("Tấn công quái thường: [TẮT]#-117") && labels().get(5).equals("Tấn công đặc biệt: [BẬT]#-116")
                        && !((Boolean)runtime.getMethod("normalMonstersActive").invoke(null)).booleanValue(),
                "Turning normal monsters OFF keeps Tấn công đặc biệt");
        }

        cls(n("lliiiiil")).getMethod(n("liliilll")).invoke(null);
        rows = labels();
        int fire = rows.indexOf("Auto Lửa Thiêng: [TẮT]#-96");
        // From Auto phục hồi thể lực on, its row (-21) follows Lửa Thiêng.
        int reset = fire >= 0 && rows.get(fire + 1).endsWith("#-21") ? fire + 2 : fire + 1;
        require(fire >= 0 && rows.get(reset).endsWith("#-41"), "Cài Đặt Cấu Hình: 'Auto Lửa Thiêng: [TẮT]' sits before 'Reset Cấu Hình'; rows=" + rows);
        click(row("Auto Lửa Thiêng"));
        require(flag("fire") && labels().contains("Auto Lửa Thiêng: [BẬT]#-96")
                        && field(port, "fireMap").getInt(null) == 1 && field(port, "fireZone").getInt(null) == 2,
                "Actual row click enables Lửa Thiêng for the current map 1 / zone 2 and reopens the menu");
        click(row("Auto Lửa Thiêng"));
        require(!flag("fire"), "Second click turns Lửa Thiêng off");

        setStatic(cls(n("lliiliil")), n("lilliililli"), Boolean.FALSE);
        setStatic(cls(n("lliiliil")), n("lilliiliili"), "test");
        mods.getMethod(n("lilliiil")).invoke(null);
        rows = labels();
        require(title().equals("MOD HungDZ") && rows.get(rows.size() - 1).equals("Phó bản / Mê cung >#-87"),
                "MOD menu ends with 'Phó bản / Mê cung >' (-87); rows=" + rows);
        if (stationary) {
            for (String text : rows) require(!text.endsWith("#-78") && !text.startsWith("Auto Phó Bản"), "Legacy A menu row is absent: " + text);
            Object legacy = construct(cls(n("lliilili")), new Class<?>[0]);
            require(!((Boolean)call(legacy, "onAction", new Class<?>[]{Integer.TYPE,Integer.TYPE}, Integer.valueOf(-79),Integer.valueOf(0))).booleanValue(), "Retired A cannot be enabled through its former action");
        }
        click(row("Phó bản / Mê cung"));
        require(title().equals("PHÓ BẢN / MÊ CUNG") && labels().equals(Arrays.asList("Phó bản Ngã Tư Tử Thần >#-88", "Làng Phủ Sương >#-92",
                "Auto Mê cung: [TẮT]#-94", "Hoàn thành phó bản: [TẮT]#-95", "<< Quay lại#-34")), "Phó bản / mê cung submenu, Vietnamese with diacritics: " + labels());
        click(row("Phó bản Ngã Tư Tử Thần"));
        require(title().equals("NGÃ TƯ TỬ THẦN") && labels().equals(Arrays.asList("Vào bằng lượt free, đồng Horae: [TẮT]#-89", "Vào bằng ngọc: [TẮT]#-90",
                "Vào bằng cả lượt free, đồng Horae và ngọc: [TẮT]#-91", "<< Quay lại#-87")), "Ngã Tư Tử Thần submenu keeps ModUpLv's three entry modes: " + labels());
        click(row("Vào bằng ngọc"));
        require(flag("gem") && labels().get(1).equals("Vào bằng ngọc: [BẬT]#-90"), "Entry mode toggles in place");
        click(row("Vào bằng ngọc"));
        click(row("<< Quay lại"));
        click(row("Làng Phủ Sương"));
        require(title().equals("LÀNG PHỦ SƯƠNG") && labels().equals(Arrays.asList("Tự động vào Làng Phủ Sương: [TẮT]#-93", "<< Quay lại#-87")),
                "Làng Phủ Sương submenu: " + labels());
        click(row("<< Quay lại"));
        click(row("Auto Mê cung"));
        require(flag("maze") && labels().get(2).equals("Auto Mê cung: [BẬT]#-94"), "Auto Mê cung toggles from the submenu");
        click(row("Auto Mê cung"));
        click(row("Hoàn thành phó bản"));
        require(flag("finish") && labels().get(3).equals("Hoàn thành phó bản: [BẬT]#-95"), "Hoàn thành phó bản toggles from the submenu");
        click(row("Hoàn thành phó bản"));
        require(!flag("maze") && !flag("finish") && !flag("gem"), "Every option is back OFF");
        drain();
    }

    // --------------------------------------------------------------- Special targets

    private static Object array(Class<?> owner, String name, int size) throws Exception {
        Field f = field(owner, name);
        Object a = Array.newInstance(f.getType().getComponentType(), size);
        f.set(null, a);
        return a;
    }

    private static void verifyTargets(Object player, Class<?> runtime) throws Exception {
        // One monster skill (id 5) whose native level reaches 3 targets within 100px of the main one.
        Class<?> skillType = cls(n("liiiiill")), levelType = cls(n("liiilill"));
        Object level = allocate(levelType);
        setNumber(level, levelType, "j", 3); setNumber(level, levelType, n("lllliiliil"), 100); setNumber(level, levelType, n("lilllli"), 0);
        Object levels = Array.newInstance(levelType, 1); Array.set(levels, 0, level);
        Object skill = allocate(skillType);
        setNumber(skill, skillType, "b", 5); setNumber(skill, skillType, "e", 0); setNumber(skill, skillType, "g", 0);
        field(skillType, n("lllillllil")).set(skill, levels);
        setStatic(cls(n("lillilll")), "a", list(skill));
        Object levelsOwned = array(playerType, n("llilllii"), 21); Array.setInt(levelsOwned, 5, 1);
        array(playerType, n("llilllil"), 21);
        at(player, 0, 0);

        List<Object> all = new ArrayList<Object>();
        Object main = monster(500, 3000, 0); all.add(main);
        all.add(monster(501, 3050, 0)); all.add(monster(502, 3090, 0)); all.add(monster(503, 3099, 0));
        Object dead = monster(900, 5, 0); setNumber(dead, entityType, "cG", 4); all.add(dead);
        Object ghost = monster(901, 6, 0); setNumber(ghost, entityType, "cy", 2); all.add(ghost);
        Object empty = monster(902, 7, 0); setNumber(empty, entityType, "bs", 0); all.add(empty);
        all.add(entity(entityType, -50, 2, 8, 0, "NPC"));
        all.add(entity(playerType, 903, 0, 9, 0, "Người chơi"));
        for (int i = 0; i < 30; i++) all.add(monster(600 + i, 0, 10 + i * 40));  // 10..1170px from the player
        world(all.toArray());
        Method targets = port.getMethod("specialTargets", playerType, Integer.TYPE, entityType);

        Object nativeList = targets.invoke(null, player, Integer.valueOf(5), main);
        require(size(nativeList) == 3 && targetId(at(nativeList, 0)) == 500 && targetId(at(nativeList, 1)) == 501 && targetId(at(nativeList, 2)) == 502,
                "OFF: the native builder's own list (main + same-type within 100px, max 3)");
        runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-117));
        Object wide = targets.invoke(null, player, Integer.valueOf(5), main);
        List<Integer> ids = new ArrayList<Integer>();
        for (int i = 0; i < size(wide); i++) ids.add(targetId(at(wide, i)));
        List<Integer> expected = new ArrayList<Integer>();
        expected.add(500);
        for (int i = 0; i < 19; i++) expected.add(600 + i);
        require(ids.equals(expected), "ON: main target + the 19 nearest living monsters of the whole map; ids=" + ids);
        require(targetType(at(wide, 1)) == 1, "Map-wide entries keep the monster type byte");
        Object npcTarget = entity(entityType, -60, 2, 600, 0, "NPC");
        all.add(npcTarget); world(all.toArray());
        require(targetId(at(targets.invoke(null, player, Integer.valueOf(5), npcTarget), 0)) == -60
                        && size(targets.invoke(null, player, Integer.valueOf(5), npcTarget)) <= 3,
                "A non-monster main target keeps the native list");
        runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-117));
        require(size(targets.invoke(null, player, Integer.valueOf(5), main)) == 3, "OFF again: native list");
        world();
        drain();
    }

    private static int targetId(Object element) throws Exception {
        for (Field f : element.getClass().getDeclaredFields()) if (f.getType() == Short.TYPE && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); return f.getShort(element); }
        throw new AssertionError("target id");
    }
    private static int targetType(Object element) throws Exception {
        for (Field f : element.getClass().getDeclaredFields()) if (f.getType() == Byte.TYPE && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); return f.getByte(element); }
        throw new AssertionError("target type");
    }

    // --------------------------------------------------------------- Lửa Thiêng

    private static Object item(int id, String name, int count) throws Exception {
        Class<?> t = cls(n("llilli"));
        Object it = allocate(t);
        setNumber(it, t, "O", id); field(t, n("liiiiliii")).set(it, name); setNumber(it, t, n("llllii"), count);
        return it;
    }

    private static void verifyFire(Object player) throws Exception {
        map(1);
        action(-96);
        require(flag("fire"), "Lửa Thiêng ON at map 1 / zone 2");
        setStatic(cls(n("lliiil")), n("lllilli"), Byte.valueOf((byte)1));
        setStatic(cls(n("llilli")), n("liiillili"), list(item(3001, "Lửa thiêng", 2), item(3002, "Bình dầu", 1), item(3003, "Bình máu", 9)));
        world();
        long t = 1000000L;
        tick(t);
        require(drain().equals(Arrays.asList(shortPacket(32, 3001))), "No fire on the map: use the Lửa thiêng item (op 32)");
        Object flame = entity(entityType, 77, 1, 40, 0, "Lửa thiêng");
        setNumber(flame, entityType, "bz", 3);
        world(flame);
        tick(t += 500);
        require(drain().isEmpty(), "ModUpLv cadence: nothing before 1400ms");
        tick(t += 900);
        require(drain().equals(Arrays.asList(shortPacket(32, 3002))), "Fire below level 4: use Bình dầu");
        setNumber(flame, entityType, "bz", 4);
        tick(t += 1400);
        require(drain().isEmpty(), "Fire at level 4 needs nothing");
        world();
        map(2);
        tick(t += 1400);
        require(drain().isEmpty(), "Another map: nothing");
        map(1);
        Object farmOn = getStatic(cls(n("lliiiiil")), n("lililiilill"));
        setStatic(cls(n("lliiiiil")), n("lililiilill"), Boolean.FALSE);
        setStatic(cls(n("lliiil")), n("lllilli"), Byte.valueOf((byte)-1));
        tick(t += 1400);
        require(drain().isEmpty(), "No auto running (native auto, Auto đánh, Farm): nothing");
        setStatic(cls(n("lliiiiil")), n("lililiilill"), Boolean.TRUE);
        tick(t += 1400);
        require(drain().equals(Arrays.asList(shortPacket(32, 3001))), "Auto Farm alone counts as an auto running");
        setStatic(cls(n("lliiiiil")), n("lililiilill"), farmOn);
        setStatic(cls(n("lliiil")), n("lllilli"), Byte.valueOf((byte)1));
        setStatic(cls(n("llilli")), n("liiillili"), list(item(3001, "Lửa thiêng", 0)));
        tick(t += 1400);
        require(drain().isEmpty(), "An empty Lửa thiêng stack is not used");
        action(-96);
        require(!flag("fire"), "Lửa Thiêng OFF");
    }

    // --------------------------------------------------------------- Ngã Tư Tử Thần

    private static void verifyDungeon(Object player, Class<?> runtime) throws Exception {
        action(-89);
        action(-95);
        require(flag("free") && flag("finish"), "Free entry + Hoàn thành phó bản ON");
        map(1);
        at(player, 100, 100);
        world();
        long t = 2000000L;
        tick(t);
        require(field(port, "lastWalkX").getInt(null) == 615 && field(port, "lastWalkY").getInt(null) == 528,
                "Map 1: walk to the Phó chỉ huy (615, 528)");
        require(drain().equals(Arrays.asList(shortPacket(-30, -37, 0, 0))), "Map 1: ask NPC -37 menu 0 row 0 (op -30)");
        tick(t += 400);
        require(drain().isEmpty(), "ModUpLv cadence: 840ms between entry attempts");
        require(!dialog("Bạn có muốn vào Ngã Tư Tử Thần một mình?"), "The entry question is still shown");
        require(drain().equals(Arrays.asList(packet(-32, 0, 0, 5, 1))), "Free mode answers 'một mình?' with op -32 (0, 5, 1)");
        require(!dialog("Vào phó bản? Phí: 5 ngọc") && drain().isEmpty() && !flag("free") && !flag("finish"),
                "Free mode meets the 5-gem fee: stop free entry and Hoàn thành phó bản, pay nothing");

        action(-90); action(-95);
        require(!dialog("Vào phó bản? Phí: 5 ngọc") && drain().equals(Arrays.asList(packet(-32, 0, 0, 5, 1))), "Gem mode accepts the 5-gem fee");
        dialog("Bạn chỉ có thể vào Ngã Tư Tử Thần 10 lần trong ngày");
        require(!flag("gem") && !flag("finish"), "Ten entries a day: gem mode stops");

        String marked = (String)getStatic(port, "VI_MARKED"), plainLetters = (String)getStatic(port, "VI_PLAIN");
        require(marked.length() == plainLetters.length(), "Folding tables align");
        java.lang.reflect.Method plain = port.getDeclaredMethod("plain", String.class);
        plain.setAccessible(true);
        for (int i = 0; i < marked.length(); i++) {
            String one = String.valueOf(marked.charAt(i));
            String decomposed = java.text.Normalizer.normalize(one, java.text.Normalizer.Form.NFD);
            require(plain.invoke(null, one).equals(plain.invoke(null, decomposed)), "Precomposed and combining fold alike: " + one);
        }
        String limit = "Bạn chỉ có thể vào Ngã Tư Tử Thần 10 lần 1 ngày. Nếu muốn vào tiếp bạn phải có vé vào phó bản";
        for (int mode = -91; mode <= -89; mode++) {
            action(mode); action(-95);
            tick(t += 1000);
            require(drain().equals(Arrays.asList(shortPacket(-30, -37, 0, 0))), "Mode " + mode + " asks the Phó chỉ huy");
            dialog(java.text.Normalizer.normalize(limit, java.text.Normalizer.Form.NFD));
            require(!flag("free") && !flag("gem") && !flag("both") && !flag("finish"),
                    "Daily limit with combining tone marks stops every entry mode (" + mode + ")");
            tick(t += 1000);
            tick(t += 1000);
            require(drain().isEmpty(), "After the daily limit the NPC is not asked again (" + mode + ")");
        }

        action(-90); action(-95);
        dialog("Phó chỉ huy: lời từ chối chưa biết");
        dialog("Phó chỉ huy: lời từ chối chưa biết");
        require(flag("gem") && flag("finish"), "Two unknown refusals keep entry running");
        require(!dialog("Vào phó bản? Phí: 5 ngọc") && drain().equals(Arrays.asList(packet(-32, 0, 0, 5, 1))), "An answered fee resets the refusal count");
        dialog("Phó chỉ huy: lời từ chối chưa biết");
        dialog("Phó chỉ huy: lời từ chối chưa biết");
        require(flag("gem") && flag("finish"), "Count restarts after an answer");
        dialog("Phó chỉ huy: lời từ chối chưa biết");
        require(!flag("gem") && !flag("finish"), "Three unanswered dialogs in a row stop the entry loop");
        tick(t += 1000);
        require(drain().isEmpty(), "No NPC request after the refusal stop");

        action(-91); action(-95);
        map(12);
        tick(t += 1000);
        drain();
        tick(t += 2000);
        require(drain().isEmpty(), "Away from a village: wait 5s");
        tick(t += 3000);
        require(drain().equals(Arrays.asList(shortPacket(32, 142))), "Then use the return scroll (item 142)");

        map(48);
        Object meteor = entity(entityType, 300, 1, 900, 900, "Thiên thạch");
        setNumber(meteor, entityType, "cG", 4);
        Object near = monster(301, 950, 900), far = monster(302, 100, 120);
        at(player, 100, 100);
        world(meteor, near, far);
        tick(t += 1000);
        require(((Boolean)runtime.getMethod("enabled").invoke(null)).booleanValue() && ((Boolean)runtime.getMethod("normalMonstersActive").invoke(null)).booleanValue()
                        && !((Boolean)runtime.getMethod("playersEnabled").invoke(null)).booleanValue(),
                "Map 48: Auto đánh on monsters with Special + Tấn công quái thường");
        if (stationary) verifyStationaryDungeon(player, t);
        else require(field(port, "lastWalkX").getInt(null) == 950 && field(port, "lastWalkY").getInt(null) == 900,
                "Map 48: walk to the monster nearest to the Thiên thạch");
    }

    private static void notice(String text, long now) throws Exception {
        setStatic(port,"testNow",Long.valueOf(now));
        cls(n("lillllll")).getMethod(n("lliii"),String.class).invoke(null,text);
    }
    private static int walks() throws Exception { return field(port,"walks").getInt(null); }
    private static void standRoute(Object player,String text,int tx,int ty,long t) throws Exception {
        at(player,100,100); field(playerType,"cO").set(player,null);
        notice(text,t); tick(t+1000);
        require(field(port,"lastWalkX").getInt(null)==tx*24+12 && field(port,"lastWalkY").getInt(null)==ty*24+12,
                "Live notice routes to minimap tile " + tx + ":" + ty + " — " + text);
        at(player,tx*24+12,ty*24+12);field(playerType,"cO").set(player,new short[]{1,2});setNumber(player,entityType,"cG",1);
        int count=walks();tick(t+2000);
        require(walks()==count && field(playerType,"cO").get(player)==null && number(player,entityType,"cG")==0,
                "At the stand tile, clears residual walking and never chases monsters");
        notice(text,t+2500);tick(t+3000);require(walks()==count,"Repeated gate notice does not restart movement");
    }
    private static void pickupDungeon(Object player)throws Exception {
        Method method=port.getDeclaredMethod("pickupFrame",playerType);method.setAccessible(true);invoke(method,null,player);
    }
    private static long verifyMenuChest(Object player,long t)throws Exception {
        // Menu as reported: Auto nhặt BẬT, only Cam ticked; the chest exception is the only gear outside the menu.
        Class<?> mod=cls(n("lliiiiil"));String auto=n("lilililiill");
        String[] colours={n("lililililii"),n("lililililil"),n("lililililli"),n("lililililll"),n("lilililliii")};
        setStatic(mod,auto,Boolean.TRUE);for(int i=0;i<colours.length;i++)setStatic(mod,colours[i],Boolean.valueOf(i==4));
        Object chest=entity(entityType,710,3,2000,2000,"Rương vàng"),yellow=entity(entityType,711,3,2000,2000,"Áo vàng"),
                purple=entity(entityType,712,3,2000,2000,"Áo tím"),orange=entity(entityType,713,3,2000,2000,"Áo cam");
        setNumber(chest,entityType,"ct",2);setNumber(yellow,entityType,"ct",2);setNumber(purple,entityType,"ct",3);setNumber(orange,entityType,"ct",4);
        world(chest,yellow,purple,orange);drain();
        pickupDungeon(player);
        require(drain().equals(Arrays.asList(shortPacket(20,713,3),shortPacket(20,710,3))),"Map 48: menu colours (Cam) plus the gold chest; no purple/yellow gear");
        require(field(entityType,"dz").getBoolean(chest)&&!field(entityType,"dz").getBoolean(purple),"Chest uses the native sent flag; skipped purple stays unrequested");
        pickupDungeon(player);require(drain().isEmpty(),"Sent chest is not requested twice");
        setStatic(mod,auto,Boolean.FALSE);field(entityType,"dz").setBoolean(chest,false);
        pickupDungeon(player);
        require(drain().equals(Arrays.asList(shortPacket(20,710,3))),"Auto nhặt TẮT: map 48 still picks Rương vàng only");
        setStatic(mod,auto,Boolean.TRUE);
        map(1);tick(t+=5000);field(entityType,"dz").setBoolean(chest,false);field(entityType,"dz").setBoolean(orange,false);drain();
        pickupDungeon(player);
        require(drain().equals(Arrays.asList(shortPacket(20,713,3))),"Outside the dungeon the gold chest exception is inactive");
        setStatic(mod,auto,Boolean.FALSE);
        return t;
    }
    private static void verifyStationaryDungeon(Object player,long t)throws Exception {
        // A fresh entry must wait for a new direction, never use a leftover scrolling notice.
        int before=walks();tick(t+=1000);require(walks()==before,"Map 48 waits for a live direction, does not chase the meteor's nearest monster");
        notice("Anh em chạy về phía Nam",t+100);tick(t+=1000);require(walks()==before,"Ordinary compass text without a gate marker cannot move the player");
        standRoute(player,"Bầy quái vật sẽ xuất hiện ở cửa phía Tây sau 15 giây nữa...",20,20,t+=5000);
        standRoute(player,"Bầy quái vật sẽ xuất hiện ở cửa phía Bắc sau 15 giây nữa...",20,20,t+=5000);
        standRoute(player,"Bầy quái vật sẽ xuất hiện ở cửa phía Đông sau 15 giây nữa...",33,20,t+=5000);
        standRoute(player,"Bầy quái vật sẽ xuất hiện ở cửa phía Nam sau 15 giây nữa...",19,33,t+=5000);
        standRoute(player,"Bầy quái vật sẽ xuất hiện ở cửa phía Bắc, Tây sau 15 giây nữa...",27,26,t+=5000);
        if(lootMenu)t=verifyMenuChest(player,t);else{
        // Exercise the actual native pickup serializer with the new exception and unchanged rarity filter.
        Object chest=entity(entityType,710,3,2000,2000,"Rương vàng"),yellow=entity(entityType,711,3,2000,2000,"Áo vàng"),purple=entity(entityType,712,3,2000,2000,"Áo tím");
        setNumber(chest,entityType,"ct",2);setNumber(yellow,entityType,"ct",2);setNumber(purple,entityType,"ct",3);world(chest,yellow,purple);drain();
        pickupDungeon(player);
        require(drain().equals(Arrays.asList(shortPacket(20,710,3),shortPacket(20,712,3))),"Dungeon picks the gold chest plus purple gear, skips other yellow gear");
        require(field(entityType,"dz").getBoolean(chest),"Chest request uses the native sent flag");
        pickupDungeon(player);require(drain().isEmpty(),"Sent chest is not requested twice");
        map(1);tick(t+=5000);field(entityType,"dz").setBoolean(chest,false);field(entityType,"dz").setBoolean(purple,false);drain();
        pickupDungeon(player);
        require(drain().equals(Arrays.asList(shortPacket(20,712,3))),"Outside the dungeon the gold chest exception is inactive");
        }
        int old=walks();notice("Bầy quái vật sẽ xuất hiện ở cửa phía Đông sau 15 giây nữa...",t+100);map(48);tick(t+=1000);
        require(walks()==old,"A notice received outside cannot be replayed on the next entry");
    }

    // --------------------------------------------------------------- Làng Phủ Sương

    private static void verifyFog(Object player) throws Exception {
        action(-95); action(-91);
        require(!flag("finish") && !flag("both"), "Phó bản options OFF");
        action(-93);
        require(flag("fog"), "Làng Phủ Sương ON");
        map(135);
        at(player, 0, 0);
        Object serena = entity(entityType, -104, 2, 60, 0, "Serena");
        world(serena);
        long t = 3000000L;
        tick(t);
        require(field(port, "lastWalkX").getInt(null) == 60 && drain().isEmpty(), "Map 135: walk to Serena");
        at(player, 20, 0);
        tick(t += 1400);
        require(drain().equals(Arrays.asList(shortPacket(-30, -104, 0, 1))), "Next to Serena and standing: enter (op -30 -104/0/1)");
        map(1);
        world(entity(entityType, -81, 2, 30, 0, "Mrs Oda"));
        // Mrs Oda's menu on this server: 16 rows, "Làng phủ sương" last (row 15). ModUpLv's row 16 is ignored.
        int odaRow = fogOda ? 15 : 16;
        tick(t += 1400);
        require(drain().equals(Arrays.asList(shortPacket(-30, -81, 0, odaRow))), "Map 1: Mrs Oda menu (op -30 -81/0/" + odaRow + ")");
        if (fogOda) verifyFogSale(player, t += 1400);
        if (fogRoute) verifyFogRoute();
        dialog("Bạn đã hết vé vào làng phủ sương");
        require(!flag("fog"), "Out of tickets ('...sương'): Làng Phủ Sương stops");
        world();
    }

    /** An Auto bán rác trip owns map 1 until it has sold and restored Farm. */
    private static void verifyFogSale(Object player, long t) throws Exception {
        Class<?> sale = cls(n("lliiilll")), farm = cls(n("lliiiiil"));
        Field saleOn = field(sale, n("lilliiiiill")), state = field(sale, n("lilliiiilil")), farmOn = field(farm, n("lililiilill"));
        boolean priorSale = saleOn.getBoolean(null), priorFarm = farmOn.getBoolean(null);
        int priorState = state.getInt(null);
        try {
            saleOn.setBoolean(null, true); farmOn.setBoolean(null, true);
            for (int s = 1; s <= 6; s++) {
                state.setInt(null, s);
                int old = field(port, "walks").getInt(null);
                tick(t += 1400);
                require(drain().isEmpty() && field(port, "walks").getInt(null) == old, "Sale trip state " + s + ": no Mrs Oda menu, no walk");
            }
            at(player, 400, 0);
            int old = field(port, "walks").getInt(null);
            tick(t += 1400);
            require(drain().isEmpty() && field(port, "walks").getInt(null) == old, "Sale trip far from Mrs Oda: the sale keeps the walk");
            state.setInt(null, 0);
            tick(t += 1400);
            require(field(port, "walks").getInt(null) == old + 1 && field(port, "lastWalkX").getInt(null) == 30, "Sale finished: walk to Mrs Oda");
            at(player, 20, 0);
            tick(t += 1400);
            require(drain().equals(Arrays.asList(shortPacket(-30, -81, 0, 15))), "Sale finished, next to Mrs Oda: Làng phủ sương row 15");
            state.setInt(null, 2); farmOn.setBoolean(null, false);
            tick(t += 1400);
            require(drain().equals(Arrays.asList(shortPacket(-30, -81, 0, 15))), "Farm OFF: a paused sale state does not block Mrs Oda");
            farmOn.setBoolean(null, true); saleOn.setBoolean(null, false);
            tick(t += 1400);
            require(drain().equals(Arrays.asList(shortPacket(-30, -81, 0, 15))), "Auto bán rác OFF: a stale sale state does not block Mrs Oda");
        } finally {
            saleOn.setBoolean(null, priorSale); farmOn.setBoolean(null, priorFarm); state.setInt(null, priorState);
        }
    }

    /**
     * After a sale Farm returns in phase 1 and routes to its saved map every tick; the native router has no
     * edges for the Phủ Sương maps and heads for map 33. On maps 1/135 it now yields to the fog walk.
     */
    private static void verifyFogRoute() throws Exception {
        Class<?> route = cls(n("lliiiiii")), sale = cls(n("lliiilll")), farm = cls(n("lliiiiil"));
        Field routing = field(route, n("liliillilil")), target = field(route, n("liliillilli")), last = field(route, n("liliillllll"));
        Field saleOn = field(sale, n("lilliiiiill")), state = field(sale, n("lilliiiilil")), farmOn = field(farm, n("lililiilill"));
        Method next = route.getDeclaredMethod(n("llili"), Integer.TYPE, Integer.TYPE); next.setAccessible(true);
        Method hold = route.getMethod("codexFogHold");
        Object module = allocate(route);
        Method update = route.getMethod("update");
        boolean priorSale = saleOn.getBoolean(null), priorFarm = farmOn.getBoolean(null);
        int priorState = state.getInt(null);
        try {
            require(((Integer)invoke(next, null, Integer.valueOf(1), Integer.valueOf(125))).intValue() == 33,
                    "Native router: from map 1 the next hop towards Phủ Sương 125 is map 33 (walks away from Mrs Oda)");
            saleOn.setBoolean(null, true); farmOn.setBoolean(null, true); state.setInt(null, 0);
            map(1); routing.setBoolean(null, true); target.setInt(null, 125); last.setInt(null, -5);
            require(((Boolean)hold.invoke(null)).booleanValue(), "Fog ON, map 1, routing to 125, sale done: route holds");
            invoke(update, module);
            require(last.getInt(null) == -5 && routing.getBoolean(null), "Held route update does not run (Farm keeps its target for later)");
            for (int t : new int[]{126, 127}) {
                target.setInt(null, t);
                require(((Boolean)hold.invoke(null)).booleanValue(), "Routing to unreachable map " + t + " from map 1: held");
            }
            target.setInt(null, 125); map(135);
            require(((Boolean)hold.invoke(null)).booleanValue(), "Map 135 routing to 125: held while Serena leads");
            map(125);
            require(!((Boolean)hold.invoke(null)).booleanValue(), "Inside map 125: Farm/route run normally");
            map(1); target.setInt(null, 33);
            require(!((Boolean)hold.invoke(null)).booleanValue(), "Routing to a known map (33) from map 1: not held");
            target.setInt(null, 125); state.setInt(null, 2);
            require(!((Boolean)hold.invoke(null)).booleanValue(), "Sale trip in progress: route not held");
            state.setInt(null, 0); routing.setBoolean(null, false);
            require(!((Boolean)hold.invoke(null)).booleanValue(), "Routing OFF: nothing to hold");
            routing.setBoolean(null, true);
            setStatic(port, "fog", Boolean.FALSE);
            require(!((Boolean)hold.invoke(null)).booleanValue(), "Làng Phủ Sương OFF: native route unchanged");
            last.setInt(null, -5);
            invoke(update, module);
            require(last.getInt(null) == 1, "Fog OFF: route update runs as before");
        } finally {
            setStatic(port, "fog", Boolean.TRUE);
            routing.setBoolean(null, false); target.setInt(null, 0); map(1);
            saleOn.setBoolean(null, priorSale); farmOn.setBoolean(null, priorFarm); state.setInt(null, priorState);
        }
    }

    // --------------------------------------------------------------- Mê cung

    private static void verifyMaze(Object player) throws Exception {
        action(-94);
        require(flag("maze"), "Auto Mê cung ON");
        require(dialog("Xin chờ 3 giây"), "'Xin chờ...' is closed (suppressed) while Mê cung runs");
        map(1);
        long t = 4000000L;
        for (int i = 0; i < 4; i++) { tick(t); t += 1500; }
        require(drain().equals(Arrays.asList(shortPacket(32, 241), shortPacket(32, 241), shortPacket(32, 241), shortPacket(32, 241))),
                "Village: use item 241 every 1.5s");
        require(field(port, "mazeUses").getInt(null) == 0, "After 4 uses the counter restarts");
        map(108);
        setNumber(player, entityType, "cG", 0);
        tick(t += 200);
        require(field(port, "lastWalkX").getInt(null) == 501 && drain().equals(Arrays.asList(shortPacket(-30, -87, 0, 0))) && flag("found108"),
                "Map 108: walk to (501, 598) and talk to NPC -87");
        map(112);
        tick(t += 200);
        require(field(port, "door112").getInt(null) == 1 && field(port, "lastWalkX").getInt(null) == 92, "Map 112: first door (92, 832)");
        map(109);
        Object guard = monster(3, 30, 0);
        at(player, 0, 0);
        world(guard);
        tick(t += 200);
        require(getStatic(cls(n("liiliii")), n("llillilil")) == guard, "Map 109 door 1: target the guard (id 3) within 150px");
        action(-94);
        require(!flag("maze") && !dialog("Xin chờ 3 giây"), "Auto Mê cung OFF: 'Xin chờ' shows again");
        world();
    }
}
