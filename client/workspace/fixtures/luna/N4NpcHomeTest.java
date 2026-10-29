import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

/**
 * Host test cho HSON4NpcHome trong JAR da va (N4NpcHomePatcher).
 *
 *   java -cp build/.../test N4NpcHomeTest out.jar microemulator.jar symbols/autotrade-symbols.properties
 *
 * Dung CHARS.entities/player, ban do (map id + luoi o), tui do bang reflection roi goi ham
 * that cua HSON4NpcHome: cho dung canh NPC, tim Bua ve lang, dong ho 15s.
 * notice() ngoai game khong co chat box; HSON4NpcHome tu nuot loi cua no.
 */
public final class N4NpcHomeTest {
    static Properties sym = new Properties();
    static ClassLoader loader;
    static Object unsafe;
    static Method allocate;
    static Class<?> home, support, tilemapClass;
    static Object map;
    static Object player;
    static int failures, cases;

    public static void main(String[] args) throws Exception {
        InputStream in = new FileInputStream(args[2]);
        try { sym.load(in); } finally { in.close(); }
        loader = new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL(), new File(args[1]).toURI().toURL()}, null);
        Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = f.get(null);
        allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);

        home = cls("HSON4NpcHome");
        support = cls("HSOAutoTradeSupport");
        tilemapClass = cls(sym.getProperty("class.TILEMAP"));
        setStatic(tilemapClass, sym.getProperty("tilemap.tileSize"), 24);
        map = allocate.invoke(unsafe, tilemapClass);
        setField(map, sym.getProperty("map.mapId"), 1);
        installMap();
        player = allocate.invoke(unsafe, cls(sym.getProperty("class.PLAYER")));
        setStatic(cls(sym.getProperty("class.CHARS")), sym.getProperty("chars.player"), player);
        setStatic(support, "noclipOn", false);

        // --- cho dung
        moveTo(100, 100);
        setEntities();
        expectHome("no NPC -> stand still", 100, 100);

        setEntities(entity(2, "Chỉ huy trưởng", 5, 900, 900), entity(1, "Phó chỉ huy", -37, 50, 50),
                entity(2, "Phó Chỉ Huy", -37, 300, 400));
        expectHome("walk to exact NPC position", 300, 400);

        setEntities(entity(2, "Phó chỉ huy", -37, 600, 600));
        expectHome("NPC position locked for the round", 300, 400);

        moveTo(320, 410);
        expectHome("near NPC -> stand still", 320, 410);

        moveTo(100, 100);
        setStatic(home, "locked", false);
        setEntities(entity(2, "Phó chỉ huy", -37, 300, 400));
        int[] grid = new int[40 * 40];
        grid[16 * 40 + 12] = 1;                       // o duoi chan NPC bi chan
        setGrid(grid, 40);
        expectHome("NPC tile blocked -> tile below", 300, 420);

        setStatic(support, "noclipOn", true);
        expectHome("noclip -> exact NPC position", 300, 400);
        setStatic(support, "noclipOn", false);

        moveTo(298, 425);
        expectHome("on substitute tile -> stand still", 298, 425);

        // --- Bua ve lang
        setBag(item(4, 142, null), item(3, 77, "Bùa về làng"));
        expectInt("scroll by name in any bag", call("findScroll"), 77);
        setBag(item(4, 142, null), item(3, 9, "Bình máu"));
        expectInt("scroll id 142 fallback", call("findScroll"), 142);
        setBag(item(3, 142, null));
        expectInt("id 142 outside consumables ignored", call("findScroll"), -1);
        setBag();
        expectInt("no bag", call("findScroll"), -1);

        // --- dong ho 15s
        setStatic(home, "lastCall", 0L);
        setStatic(support, "n4State", 2);
        setField(map, sym.getProperty("map.mapId"), 5);
        expectBool("other map: no timer", call("guard"), false);
        expectLong("other map: roundSince stays 0", getLong("roundSince"), 0L);

        setField(map, sym.getProperty("map.mapId"), 1);
        expectBool("town: first call runs step", call("guard"), false);
        long since = getLong("roundSince");
        expectBool("town: timer started", since != 0L, true);

        setBag(item(4, 50, "Bùa về làng"));
        setStatic(home, "roundSince", System.currentTimeMillis() - 16000L);
        Object fired = call("guard");
        expectInt("15s: back to N4_HOME", getStaticInt(support, "n4State"), 1);
        expectInt("15s: scroll use counted", getStaticInt(home, "scrollUses"), 1);
        System.out.println("info scroll send outside game: guard=" + fired + ", scrollAt=" + getLong("scrollAt"));

        setStatic(home, "scrollAt", 0L);
        setStatic(home, "scrollUses", 3);
        setStatic(home, "roundSince", System.currentTimeMillis() - 16000L);
        setStatic(support, "n4State", 2);
        call("guard");
        expectInt("4th scroll -> Auto Nga 4 stops", getStaticInt(support, "n4State"), 0);

        setStatic(home, "lastCall", System.currentTimeMillis() - 5000L);
        call("guard");
        expectInt("gap -> new round resets scroll count", getStaticInt(home, "scrollUses"), 0);

        // --- bi da ra lang: doi 35s moi ve cho Pho chi huy
        Method leave = support.getDeclaredMethod("n4StepLeave");
        leave.setAccessible(true);
        setStatic(support, "n4State", 6);
        setStatic(support, "n4LeaveAt", System.currentTimeMillis() - 10000L);
        leave.invoke(null);
        expectInt("10s after kick: still waiting", getStaticInt(support, "n4State"), 6);
        setStatic(support, "n4LeaveAt", System.currentTimeMillis() - 36000L);
        boolean passedWait;
        try {
            leave.invoke(null);
            passedWait = getStaticInt(support, "n4State") == 1;
        } catch (InvocationTargetException e) {
            passedWait = fromNotice(e.getCause());   // ngoai game: toi dong "Đã nạp xong..." la da qua cho doi
        }
        report("36s after kick: goes to Phó chỉ huy", passedWait, String.valueOf(passedWait));

        System.out.println(failures == 0 ? "N4_NPC_HOME_TEST=PASS; cases=" + cases : "N4_NPC_HOME_TEST=FAIL; failures=" + failures);
        if (failures != 0) System.exit(1);
    }

    // ------------------------------------------------------------ kiem tra

    static void expectHome(String name, int x, int y) throws Exception {
        int gx = (Integer) call("homeX");
        int gy = (Integer) call("homeY");
        report(name, gx == x && gy == y, "(" + gx + "," + gy + ") expected (" + x + "," + y + ")");
    }

    static void expectInt(String name, Object got, int want) {
        report(name, got instanceof Integer && (Integer) got == want, got + " expected " + want);
    }

    static void expectLong(String name, long got, long want) {
        report(name, got == want, got + " expected " + want);
    }

    static void expectBool(String name, Object got, boolean want) {
        report(name, Boolean.valueOf(want).equals(got), got + " expected " + want);
    }

    static void report(String name, boolean ok, String detail) {
        cases++;
        if (!ok) failures++;
        System.out.println((ok ? "ok   " : "FAIL ") + name + ": " + detail);
    }

    static boolean fromNotice(Throwable t) {
        for (StackTraceElement s : t.getStackTrace()) {
            if (s.getClassName().equals("HSOAutoTradeSupport") && s.getMethodName().equals("notice")) return true;
        }
        return false;
    }

    static Object call(String method) throws Exception {
        Method m = home.getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(null);
    }

    static long getLong(String name) throws Exception {
        Field f = home.getDeclaredField(name);
        f.setAccessible(true);
        return f.getLong(null);
    }

    static int getStaticInt(Class<?> owner, String name) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(null);
    }

    // ------------------------------------------------------------ dung trang thai game

    static void moveTo(int x, int y) throws Exception {
        setField(player, sym.getProperty("player.x"), x);
        setField(player, sym.getProperty("player.y"), y);
    }

    static void installMap() throws Exception {
        Class<?> canvas = cls(sym.getProperty("class.CANVAS"));
        for (Field f : canvas.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == tilemapClass) {
                f.setAccessible(true);
                f.set(null, map);
                return;
            }
        }
        throw new IllegalStateException("No static TILEMAP holder in CANVAS");
    }

    static void setGrid(int[] tiles, int width) throws Exception {
        setField(map, sym.getProperty("tilemap.tiles"), tiles);
        setField(map, sym.getProperty("tilemap.width"), width);
    }

    static Object entity(int kind, String name, int npcId, int x, int y) throws Exception {
        Object e = allocate.invoke(unsafe, cls(sym.getProperty("class.ENTITY")));
        setField(e, sym.getProperty("entity.kind"), (byte) kind);
        setField(e, sym.getProperty("entity.name"), name);
        setField(e, sym.getProperty("entity.npcId"), npcId);
        setField(e, sym.getProperty("entity.x"), x);
        setField(e, sym.getProperty("entity.y"), y);
        return e;
    }

    static Object item(int category, int id, String name) throws Exception {
        Object it = allocate.invoke(unsafe, cls(sym.getProperty("class.ITEM")));
        setNumber(it, sym.getProperty("item.category"), category);
        setNumber(it, sym.getProperty("item.id"), id);
        setField(it, sym.getProperty("item.name"), name);
        return it;
    }

    static void setEntities(Object... entities) throws Exception {
        setStatic(cls(sym.getProperty("class.CHARS")), sym.getProperty("chars.entities"), vec(entities));
    }

    static void setBag(Object... items) throws Exception {
        setStatic(cls(sym.getProperty("class.ITEM")), sym.getProperty("item.bag"), vec(items));
    }

    static Object vec(Object[] values) throws Exception {
        if (values.length == 0) return null;
        Class<?> vec = cls(sym.getProperty("class.VEC"));
        Constructor<?> c = vec.getDeclaredConstructor();
        c.setAccessible(true);
        Object list = c.newInstance();
        Method add = vec.getDeclaredMethod(sym.getProperty("vec.add"), Object.class);
        add.setAccessible(true);
        for (Object v : values) add.invoke(list, v);
        return list;
    }

    static Class<?> cls(String name) throws Exception {
        return Class.forName(name, false, loader);
    }

    static void setStatic(Class<?> owner, String name, Object value) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    static void setField(Object target, String name, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            for (Field f : c.getDeclaredFields()) {
                if (!f.getName().equals(name) || Modifier.isStatic(f.getModifiers())) continue;
                if (value == null ? f.getType().isPrimitive() : !box(f.getType()).isInstance(value)) continue;
                f.setAccessible(true);
                f.set(target, value);
                return;
            }
            c = c.getSuperclass();
        }
        throw new NoSuchFieldException(name + " on " + target.getClass().getName().substring(0, 12));
    }

    /** Gan so vao field cung ten bat ke kieu byte/short/int. */
    static void setNumber(Object target, String name, int value) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!f.getName().equals(name) || Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                if (f.getType() == byte.class) f.setByte(target, (byte) value);
                else if (f.getType() == short.class) f.setShort(target, (short) value);
                else if (f.getType() == int.class) f.setInt(target, value);
                else continue;
                return;
            }
        }
        throw new NoSuchFieldException(name);
    }

    static Class<?> box(Class<?> t) {
        if (t == int.class) return Integer.class;
        if (t == byte.class) return Byte.class;
        if (t == short.class) return Short.class;
        if (t == boolean.class) return Boolean.class;
        if (t == long.class) return Long.class;
        return t;
    }
}
