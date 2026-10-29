import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Hung_By_Son_copy_special_skill inside the candidate: the six native hooks, then ModUpLv's attack
 * loop (skill order, MP/cooldown/target checks, 600ms gate that ends the frame, 20 nearest
 * monsters, cooldown/MP bookkeeping, 28ms frames, game speed independence), the map-wide pick-up,
 * and the cast/miss counters fed by the real serializers and the real combat answer handler.
 * Packets are captured at the native send queue. Reuses HungBySonAutoAttackBinaryTest's probe.
 */
public final class HungBySonCopySpecialBinaryTest {
    private static final String HELPER = "HungBySonAutoAttackBinaryTest";
    private static int checks;
    private static ClassLoader loader;
    private static boolean lootMenu;
    private static Class<?> port, stats, counters, entityType, playerType, runtime;
    static volatile long clockNow = 1000000L;

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
        return ((Number)field(owner, name).get(target)).longValue();
    }
    private static void setStatic(Class<?> owner, String name, Object value) throws Exception { field(owner, name).set(null, value); }
    private static Object getStatic(Class<?> owner, String name) throws Exception { return field(owner, name).get(null); }

    private static Object list(Object... items) throws Exception {
        Object v = invoke(helper("newNativeList", Class.class, String.class), null, cls("et"), "Copy special test");
        Method add = cls("et").getMethod(n("l"), Object.class);
        for (Object i : items) add.invoke(v, i);
        return v;
    }
    private static Object entity(Class<?> type, int id, int kind, int x, int y, String name) throws Exception {
        Object e = allocate(type);
        setNumber(e, entityType, "cu", id); setNumber(e, entityType, "cv", kind);
        setNumber(e, entityType, "bs", 100); setNumber(e, entityType, "cG", 0);
        setNumber(e, entityType, "aZ", x); setNumber(e, entityType, "ba", y);
        field(entityType, "cC").set(e, name);
        return e;
    }
    private static Object monster(int id, int x, int y) throws Exception { return entity(entityType, id, 1, x, y, "Quái " + id); }
    private static Object item(int id, int kind, int quality, int x, int y) throws Exception {
        Object e = entity(entityType, id, kind, x, y, "Vật phẩm " + id);
        setNumber(e, entityType, "ct", quality);
        return e;
    }
    private static Object player() throws Exception { return getStatic(cls(n("liiliii")), n("llilliill")); }

    @SuppressWarnings("unchecked")
    private static List<String> drain() throws Exception {
        List<String> all = (List<String>)getStatic(helper(), "nativePackets");
        synchronized (all) { List<String> copy = new ArrayList<String>(all); all.clear(); return copy; }
    }
    private static String attack(int op, int skill, int... ids) {
        StringBuilder b = new StringBuilder().append(op).append(":[").append((byte)skill).append(", ").append((byte)ids.length);
        for (int id : ids) b.append(", ").append((byte)(id >> 8)).append(", ").append((byte)id);
        return b.append("]").toString();
    }
    private static String pickup(int id, int kind) {
        return "20:[" + (byte)(id >> 8) + ", " + (byte)id + ", " + (byte)kind + "]";
    }

    private static void at(long now) throws Exception {
        clockNow = now;
        setStatic(port, "testNow", Long.valueOf(now));
        setStatic(stats, "testNow", Long.valueOf(now));
    }
    private static void frame(long now) throws Exception {
        at(now);
        invoke(port.getMethod("frame"), null);
    }
    private static boolean rt(String name) throws Exception { return ((Boolean)runtime.getMethod(name).invoke(null)).booleanValue(); }
    private static void action(int code) throws Exception { runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(code)); }
    private static int count(String name, int skill) throws Exception {
        return ((Integer)counters.getMethod(name, Integer.TYPE).invoke(null, Integer.valueOf(skill))).intValue();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("candidate.jar auto-attack.properties copy-special.properties");
        File candidate = new File(args[0]);
        if (System.getProperty("hso.log.dir") == null)
            System.setProperty("hso.log.dir", new File(new File(args[2]).getAbsoluteFile().getParentFile(), "skill-gap-logs").getPath());
        Properties auto = load(new File(args[1])), catalog = load(new File(args[2]));
        require("HungBySonModUpLv".equals(catalog.getProperty("copy.special.loop.owner")) && "HungBySonSkillStats".equals(catalog.getProperty("copy.special.stats.owner"))
                && "28".equals(catalog.getProperty("copy.special.frame.ms")), "Catalog records the loop, the stats class and the 28ms frame");
        verifyStructure(candidate, catalog);
        lootMenu = "true".equals(catalog.getProperty("loot.menu"));

        Object probe = construct(Class.forName(HELPER + "$NativeProbeLoader"), new Class<?>[]{File.class, Properties.class}, candidate, auto);
        loader = (ClassLoader)probe;
        port = cls("HungBySonModUpLv");
        stats = cls("HungBySonSkillStats");
        counters = cls("luna/features/SkillCastStats");
        entityType = cls(catalog.getProperty("entity.owner"));
        playerType = cls(catalog.getProperty("player.owner"));
        Class<?> bridgeType = cls("luna/generated/HungAutoAttackAccess");
        Object bridge = bridgeType.getDeclaredConstructor().newInstance();
        Object access = construct(Class.forName(HELPER + "$NativeAccess"), new Class<?>[]{ClassLoader.class, Object.class, Properties.class}, loader, bridge, auto);
        call(access, "installReadyWorld", new Class<?>[0]);
        Object player = player();
        setNumber(player, entityType, "cG", 0);
        setNumber(player, entityType, "bu", 1000);
        setNumber(player, entityType, "cu", 4242);
        runtime = cls("luna/features/AutoAttackRuntime");
        installController(bridgeType, bridge);
        require(((Boolean)bridgeType.getMethod("farmWorldReady").invoke(null)).booleanValue(), "Test world is ready (logged in, alive)");

        // The ModUpLv-order checks run with pacing Tắt and no retries; the lock mode is checked last.
        counters.getMethod("setPaceMode", Integer.TYPE).invoke(null, Integer.valueOf(0));
        counters.getMethod("setRetry", Boolean.TYPE).invoke(null, Boolean.FALSE);
        verifyLoop(player);
        if (lootMenu) verifyMenuPickup(player); else verifyPickup(player);
        verifyCounters(player);
        verifyLock(player);
        if (hasMethod(counters, "answerMode")) verifyAnswerLock(player);
        if ("true".equals(catalog.getProperty("dungeon.stationary"))) verifyDungeonFrames(player);
        System.out.println("HungBySonCopySpecialBinaryTest PASS checks=" + checks);
    }

    private static void verifyDungeonFrames(Object player) throws Exception {
        Object map=getStatic(cls(n("liilllii")),n("liiililiiii"));
        setNumber(map,map.getClass(),"d",48);setStatic(port,"finish",Boolean.TRUE);
        runtime.getMethod("engageMonsters").invoke(null);
        counters.getMethod("setPaceMode",Integer.TYPE).invoke(null,Integer.valueOf(0));
        for(int i=0;i<cooldowns.length;i++)cd(i,99999);cd(1,0);
        setNumber(player,entityType,"aZ",100);setNumber(player,entityType,"ba",100);setNumber(player,entityType,"bu",1000);
        setStatic(cls(n("liiliii")),n("llillilll"),list(monster(880,1500,1500)));
        long t=90000000L;drain();frame(t);
        require(drain().isEmpty(),"Dungeon frame waits for a fresh gate announcement before attacking");
        at(t+1);cls(n("lillllll")).getMethod(n("lliii"),String.class).invoke(null,"Bầy quái vật sẽ xuất hiện ở cửa phía Tây sau 15 giây nữa...");
        frame(t+28);require(drain().isEmpty(),"Dungeon frame does not attack before reaching its stand tile");
        setNumber(player,entityType,"aZ",492);setNumber(player,entityType,"ba",492);
        int walks=field(port,"walks").getInt(null);
        frame(t+56);require(drain().equals(Arrays.asList(attack(9,1,880))),"At tile 20:20 the real Special serializer attacks a distant monster");
        require(field(port,"walks").getInt(null)==walks && field(playerType,"cO").get(player)==null,"Special frame sends while stationary, without a chase path");
        require(number(null,playerType,n("lllilli"))==-1 && !field(playerType,n("lllliiii")).getBoolean(null),"Native walking auto remains disabled while the dungeon Special frame runs");
        setStatic(port,"finish",Boolean.FALSE);setNumber(map,map.getClass(),"d",1);
    }

    private static void installController(Class<?> bridgeType, Object bridge) throws Exception {
        Class<?> settings = cls("luna/api/AutoAttackSettings"), clock = cls("luna/api/LunaClock"), accessApi = cls("luna/api/AutoAttackAccess");
        Object store = Proxy.newProxyInstance(loader, new Class<?>[]{settings}, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) { if (m.getName().equals("load")) { int[] v = (int[])a[0]; v[0] = 0; v[1] = 650; } return null; }
        });
        Object time = Proxy.newProxyInstance(loader, new Class<?>[]{clock}, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) { return Long.valueOf(clockNow); }
        });
        runtime.getMethod("installForTest", accessApi, settings, clock).invoke(null, bridge, store, time);
    }

    // --------------------------------------------------------------- structure

    private static void verifyStructure(File candidate, Properties catalog) throws Exception {
        JarFile jar = new JarFile(candidate);
        try {
            ClassNode canvas = node(jar, "com/silverknight/a");
            MethodNode run = method(canvas, "run", "()V");
            int frames = 0, sleeps = 0;
            for (AbstractInsnNode x : run.instructions) {
                if (calls(x, "HungBySonModUpLv", "frame")) {
                    frames++;
                    AbstractInsnNode next = x.getNext();
                    require(next instanceof MethodInsnNode && ((MethodInsnNode)next).name.equals("repaint"), "The ModUpLv frame runs right before each canvas repaint");
                }
                if (calls(x, "HungBySonModUpLv", "frameSleep")) {
                    sleeps++;
                    AbstractInsnNode next = x.getNext();
                    require(next instanceof MethodInsnNode && ((MethodInsnNode)next).owner.equals("java/lang/Thread"), "frameSleep feeds Thread.sleep");
                }
            }
            require(frames == 1 && sleeps == 2, "Canvas loop: one frame hook, both sleeps wrapped");
            ClassNode sender = node(jar, n("lilillll"));
            int sent = 0;
            for (MethodNode m : sender.methods) for (AbstractInsnNode x : m.instructions) if (calls(x, "HungBySonSkillStats", "sent")) {
                require((m.desc.equals("(Let;B)V") || m.desc.equals("(BBLet;)V")) && real(m.instructions.getFirst()).getOpcode() == Opcodes.ILOAD,
                        "Cast counter is the first thing of serializer " + m.desc);
                sent++;
            }
            require(sent == 3, "Both attack serializers (opcodes 9 and 6) and the buff request (opcode 40) count casts");
            MethodNode buffAnswer = method(node(jar, catalog.getProperty("copy.special.combat.owner")), n("liilll"), "(L" + catalog.getProperty("copy.special.message.owner") + ";)V");
            require(calls(real(real(real(buffAnswer.instructions.getFirst()).getNext()).getNext()), "HungBySonSkillStats", "buff"), "The buff answer handler (opcode 40) feeds the counters first");
            ClassNode reader = node(jar, catalog.getProperty("copy.special.combat.owner"));
            MethodNode combat = method(reader, n(""), "(L" + catalog.getProperty("copy.special.message.owner") + ";BB)V");
            AbstractInsnNode first = real(combat.instructions.getFirst());
            AbstractInsnNode third = real(real(first.getNext()).getNext());
            require(calls(third, "HungBySonSkillStats", "combat"), "The combat answer handler feeds the counters first");
            ClassNode engine = node(jar, n("liilllii"));
            int paints = 0;
            for (MethodNode m : engine.methods) for (AbstractInsnNode x : m.instructions) if (calls(x, "HungBySonSkillStats", "paint")) paints++;
            require(paints == 1, "One paint hook after the active dialog");
            ClassNode guard = node(jar, "luna/generated/ExpInfoModalGuard");
            require(calls(real(real(real(method(guard, "press", "(II)Z").instructions.getFirst()).getNext()).getNext()), "HungBySonSkillStats", "press")
                    && calls(real(real(real(method(guard, "release", "(II)Z").instructions.getFirst()).getNext()).getNext()), "HungBySonSkillStats", "release"),
                    "EXP modal pointer: tab press/release first");
            int texts = 0;
            for (AbstractInsnNode x : method(node(jar, "luna/generated/ExpInfoBridge"), "open", "()V").instructions)
                if (calls(x, "HungBySonSkillStats", "expText")) texts++;
            require(texts == 1, "EXP dialog text goes through the tab layout");
            fileLog = !"none".equals(catalog.getProperty("copy.special.file.log"));
            require(fileLog ? jar.getEntry("luna/features/SkillCastLog.class") != null && "luna/features/SkillCastLog".equals(catalog.getProperty("copy.special.file.log"))
                    : jar.getEntry("luna/features/SkillCastLog.class") == null, fileLog ? "The file log class is added" : "Release build: no file log class");
            for (String name : fileLog ? new String[]{"HungBySonModUpLv", "HungBySonSkillStats", "luna/features/SkillCastStats", "luna/features/SkillCastLog"}
                    : new String[]{"HungBySonModUpLv", "HungBySonSkillStats", "luna/features/SkillCastStats"})
                require(node(jar, name).version == Opcodes.V1_4 || name.startsWith("luna/"), name + " is class 48");
        } finally { jar.close(); }
    }

    private static boolean calls(AbstractInsnNode x, String owner, String name) {
        return x instanceof MethodInsnNode && ((MethodInsnNode)x).owner.equals(owner) && ((MethodInsnNode)x).name.equals(name);
    }
    private static AbstractInsnNode real(AbstractInsnNode x) { while (x != null && x.getOpcode() < 0) x = x.getNext(); return x; }
    private static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new AssertionError("Missing " + c.name + "." + name + desc);
    }
    private static ClassNode node(JarFile jar, String name) throws IOException {
        InputStream in = jar.getInputStream(jar.getJarEntry(name + ".class"));
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192];
        for (int k; (k = in.read(b)) > 0;) out.write(b, 0, k);
        in.close();
        ClassNode c = new ClassNode(); new ClassReader(out.toByteArray()).accept(c, 0); return c;
    }

    // --------------------------------------------------------------- attack loop

    static Object[] cooldowns;
    static Object skill(int id, int type, String name, int[][] levels) throws Exception {
        Class<?> skillType = cls(n("liiiiill")), levelType = cls(n("liiilill"));
        Object array = Array.newInstance(levelType, levels.length);
        for (int i = 0; i < levels.length; i++) {
            Object level = allocate(levelType);
            setNumber(level, levelType, n("lilllli"), levels[i][0]);
            setNumber(level, levelType, "j", levels[i][1]);
            setNumber(level, levelType, "g", levels[i][2]);
            Array.set(array, i, level);
        }
        Object s = allocate(skillType);
        setNumber(s, skillType, "b", id); setNumber(s, skillType, "e", type);
        field(skillType, n("lllillllil")).set(s, array);
        field(skillType, n("lllilllil")).set(s, name);
        return s;
    }
    static Object cooldown(int skill) { return cooldowns[skill]; }
    static long cd(int skill) throws Exception { return number(cooldown(skill), cooldown(skill).getClass(), "b"); }
    static void cd(int skill, int value) throws Exception { setNumber(cooldown(skill), cooldown(skill).getClass(), "b", value); }

    private static void verifyLoop(Object player) throws Exception {
        // Learned: 2, 7, 8 (level 1 + bonus 1 -> second level row), 9 (no targets: a buff), 15 (MP 5000). 20 is not learned.
        setStatic(cls(n("lillilll")), "a", list(
                skill(2, 0, "Chém Gió", new int[][]{{10, 5, 500}}),
                skill(7, 0, "Lôi Thần", new int[][]{{10, 5, 1000}}),
                skill(8, 0, "Băng Phong", new int[][]{{40, 5, 900}, {10, 5, 1200}}),
                skill(9, 1, "Hộ Thể", new int[][]{{10, 0, 1000}}),
                skill(15, 0, "Thiên Hỏa", new int[][]{{5000, 5, 1000}}),
                skill(20, 0, "Chưa học", new int[][]{{1, 5, 1000}})));
        int[] levels = new int[21], bonus = new int[21];
        levels[2] = levels[7] = levels[8] = levels[9] = levels[15] = 1; bonus[8] = 1;
        Class<?> P = cls(n("lliiil"));
        setStatic(P, n("llilllii"), levels);
        setStatic(P, n("llilllil"), bonus);
        Class<?> cdType = field(P, n("lliillli")).getType().getComponentType();
        Object cdArray = Array.newInstance(cdType, 21);
        cooldowns = new Object[21];
        for (int i = 0; i < 21; i++) { cooldowns[i] = allocate(cdType); Array.set(cdArray, i, cooldowns[i]); }
        setStatic(P, n("lliillli"), cdArray);
        setNumber(player, entityType, "aZ", 0); setNumber(player, entityType, "ba", 0);

        List<Object> all = new ArrayList<Object>();
        Object dead = monster(900, 5, 0); setNumber(dead, entityType, "cG", 4); all.add(dead);
        Object ghost = monster(901, 6, 0); setNumber(ghost, entityType, "cy", 2); all.add(ghost);
        Object empty = monster(902, 7, 0); setNumber(empty, entityType, "bs", 0); all.add(empty);
        Object removed = monster(903, 8, 0); field(entityType, "cL").setBoolean(removed, true); all.add(removed);
        all.add(entity(entityType, -50, 2, 9, 0, "NPC"));
        all.add(entity(playerType, 904, 0, 10, 0, "Người chơi"));
        Object marked = monster(905, 11, 0); field(entityType, "dz").setBoolean(marked, true); all.add(marked);
        for (int i = 29; i >= 0; i--) all.add(monster(600 + i, 0, 20 + i * 300));  // 20 .. 8720 away, listed far to near
        setStatic(cls(n("liiliii")), n("llillilll"), list(all.toArray()));
        int[] nearest = new int[20];
        nearest[0] = 905;
        for (int i = 1; i < 20; i++) nearest[i] = 600 + i - 1;
        drain();

        long t = 5000000L;
        frame(t);
        require(drain().isEmpty(), "Special OFF: the loop sends nothing");
        require(((Long)port.getMethod("frameSleep", Long.TYPE).invoke(null, Long.valueOf(16L))).longValue() == 16L, "Special OFF: the canvas keeps its 16ms sleep");
        require(rt("normalMonstersActive") == false && ((String)runtime.getMethod("normalLabel").invoke(null)).equals("Tấn công quái thường: [BẬT]"),
                "Tấn công quái thường is ON by default (ModUpLv), waiting for Tấn công đặc biệt");
        action(-116);
        require(rt("specialEnabled") && rt("normalMonstersActive") && !rt("enabled"), "Tấn công đặc biệt alone starts the loop; Auto đánh stays off");

        frame(t += 1000);
        List<String> sent = drain();
        require(sent.equals(Arrays.asList(attack(9, 8, nearest))),
                "Highest ready learned skill (8; 9 has no targets, 15 lacks MP, 20 unlearned) to the 20 nearest living monsters, nearest first; " + sent);
        require(cd(8) == 1200 && number(cooldown(8), cooldown(8).getClass(), "c") == 1200 && field(cooldown(8).getClass(), "d").getBoolean(cooldown(8)),
                "Cooldown from the level row of level + bonus (1200ms), like ModUpLv");
        require(number(player, entityType, "bu") == 990, "MP is spent by the client at once (ModUpLv)");
        require(field(entityType, "cG").getInt(player) == 0 && getStatic(cls(n("liiliii")), n("llillilil")) == null, "No cast, walk or target change for the player");
        long sleep = ((Long)port.getMethod("frameSleep", Long.TYPE).invoke(null, Long.valueOf(16L))).longValue();
        require(sleep == 16L, "Next frame is 28ms away: the canvas sleeps its 16ms (" + sleep + ")");
        at(t + 20);
        sleep = ((Long)port.getMethod("frameSleep", Long.TYPE).invoke(null, Long.valueOf(16L))).longValue();
        require(sleep == 8L, "At +20ms the canvas wakes 8ms later for the next ModUpLv frame (" + sleep + ")");

        frame(t + 10);
        require(drain().isEmpty(), "Same 28ms frame: nothing more, whatever the game speed");
        frame(t += 28);
        require(drain().isEmpty(), "Skill 7 is ready but inside 600ms of the last send: the frame ends, skill 2 is not tried");
        cd(7, 5000);
        frame(t += 28);
        require(drain().equals(Arrays.asList(attack(9, 2, nearest))), "With 7 cooling down, the next skill (2) goes");
        cd(7, 0);
        frame(t += 28);
        require(drain().isEmpty(), "7 ready again but the gate still holds (sent 28ms ago)");
        frame(t += 601);
        require(drain().equals(Arrays.asList(attack(9, 7, nearest))), "After 600ms skill 7 goes");
        cd(8, 0); cd(2, 0); cd(7, 0);
        setNumber(player, entityType, "bu", 5);
        frame(t += 700);
        require(drain().isEmpty(), "Not enough MP for any skill: nothing");
        setNumber(player, entityType, "bu", 1000);
        setNumber(player, entityType, "cG", 4);
        frame(t += 700);
        require(drain().isEmpty(), "Dead: nothing");
        setNumber(player, entityType, "cG", 0);

        action(-117);
        require(rt("specialEnabled") && !rt("normalMonstersActive"), "Tấn công quái thường OFF keeps Special (Auto đánh/Farm send it again)");
        frame(t += 700);
        require(drain().isEmpty(), "Loop idle while Tấn công quái thường is OFF");
        action(-117);
        frame(t += 700);
        require(drain().equals(Arrays.asList(attack(9, 8, nearest))), "ON again: the loop resumes");
    }

    // --------------------------------------------------------------- pick-up

    private static void verifyPickup(Object player) throws Exception {
        cd(8, 99999); cd(7, 99999); cd(2, 99999);
        // The native distance (shared with ModUpLv) overflows past ~46340, so 50000 covers any map.
        Object good = item(700, 3, 3, 100, 0), cheap = item(701, 3, 2, 110, 0), gold = item(702, 4, 0, 40000, 0),
                quest = item(703, 5, 0, 120, 0), npc = entity(entityType, 704, 2, 20, 0, "NPC"), material = item(705, 7, 0, 30000, 30000),
                asked = item(706, 4, 0, 130, 0), monster = monster(709, 30, 0);
        field(entityType, "dz").setBoolean(asked, true);
        setStatic(cls(n("liiliii")), n("llillilll"), list(good, cheap, gold, quest, npc, material, asked, monster));
        long t = 9000000L;
        frame(t);
        List<String> sent = drain();
        require(sent.equals(Arrays.asList(pickup(700, 3), pickup(702, 4), pickup(703, 5), pickup(705, 7))),
                "Pick-up (op 20) once for items 3 (quality >= 3), 4, 5, 7 anywhere on the map; not NPCs/monsters; " + sent);
        require(field(entityType, "dz").getBoolean(good) && !field(entityType, "dz").getBoolean(cheap), "Requested items are marked");
        frame(t += 28);
        require(drain().isEmpty(), "Each item is requested once");
        action(-117);
        Object late = item(707, 4, 0, 10, 0);
        setStatic(cls(n("liiliii")), n("llillilll"), list(late));
        frame(t += 28);
        require(drain().equals(Arrays.asList(pickup(707, 4))), "Pick-up also runs with Tấn công quái thường OFF (Special ON)");
        action(-117);
        action(-116);
        setStatic(cls(n("liiliii")), n("llillilll"), list(item(708, 4, 0, 10, 0)));
        frame(t += 28);
        require(drain().isEmpty() && !rt("specialEnabled"), "Special OFF: no pick-up");
    }

    /** Special's pick-up is the mod's whole-map pass: Auto nhặt on/off and the Trắng/Xanh/Vàng/Tím/Cam boxes. */
    private static void verifyMenuPickup(Object player) throws Exception {
        cd(8, 99999); cd(7, 99999); cd(2, 99999);
        Class<?> mod = cls(n("lliiiiil"));
        String auto = n("lilililiill");
        String[] colours = { n("lililililii"), n("lililililil"), n("lililililli"), n("lililililll"), n("lilililliii") };
        // The reported menu: Auto nhặt BẬT, only Cam ticked.
        setStatic(mod, auto, Boolean.TRUE);
        for (int i = 0; i < colours.length; i++) setStatic(mod, colours[i], Boolean.valueOf(i == 4));
        Object white = item(720, 3, 0, 100, 0), blue = item(721, 3, 1, 110, 0), yellow = item(722, 3, 2, 120, 0),
                purple = item(723, 3, 3, 130, 0), orange = item(724, 3, 4, 40000, 0), purpleHigh = item(725, 3, 23, 140, 0),
                orangeHigh = item(726, 3, 24, 150, 0), gold = item(727, 4, 0, 40000, 0), quest = item(728, 5, 0, 160, 0),
                material = item(729, 7, 0, 30000, 30000), npc = entity(entityType, 730, 2, 20, 0, "NPC"), monster = monster(731, 30, 0);
        setStatic(cls(n("liiliii")), n("llillilll"), list(white, blue, yellow, purple, orange, purpleHigh, orangeHigh, gold, quest, material, npc, monster));
        long t = 9000000L;
        frame(t);
        List<String> sent = drain();
        require(sent.equals(Arrays.asList(pickup(724, 3), pickup(726, 3), pickup(727, 4), pickup(728, 5), pickup(729, 7))),
                "Only Cam ticked: orange gear (4 and 24) plus items 4/5/7 anywhere on the map; no white/blue/yellow/purple, NPC or monster; " + sent);
        require(!field(entityType, "dz").getBoolean(purple) && !field(entityType, "dz").getBoolean(purpleHigh) && field(entityType, "dz").getBoolean(orange),
                "Skipped colours stay unrequested");
        frame(t += 28);
        require(drain().isEmpty(), "Each item is requested once");
        setStatic(mod, colours[3], Boolean.TRUE);
        frame(t += 28);
        require(drain().equals(Arrays.asList(pickup(723, 3), pickup(725, 3))), "Ticking Tím picks the purple gear (3 and 23)");
        setStatic(mod, auto, Boolean.FALSE);
        Object late = item(732, 4, 0, 10, 0), lateOrange = item(733, 3, 4, 10, 0);
        setStatic(cls(n("liiliii")), n("llillilll"), list(late, lateOrange));
        frame(t += 28);
        require(drain().isEmpty(), "Auto nhặt TẮT: Special picks nothing");
        setStatic(mod, auto, Boolean.TRUE);
        frame(t += 28);
        require(drain().equals(Arrays.asList(pickup(732, 4), pickup(733, 3))), "Auto nhặt BẬT again resumes");
        action(-117);
        setStatic(cls(n("liiliii")), n("llillilll"), list(item(734, 4, 0, 10, 0)));
        frame(t += 28);
        require(drain().equals(Arrays.asList(pickup(734, 4))), "Pick-up also runs with Tấn công quái thường OFF (Special ON)");
        action(-117);
        action(-116);
        setStatic(cls(n("liiliii")), n("llillilll"), list(item(735, 4, 0, 10, 0)));
        frame(t += 28);
        require(drain().isEmpty() && !rt("specialEnabled"), "Special OFF: no pick-up");
        setStatic(mod, auto, Boolean.FALSE);
    }

    // --------------------------------------------------------------- counters

    private static void answer(int attacker, int skill, int... damages) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(attacker);
        out.writeByte(skill);
        out.writeByte(damages.length);
        for (int i = 0; i < damages.length; i++) {
            out.writeShort(600 + i); out.writeInt(damages[i]); out.writeInt(50); out.writeByte(1); out.writeByte(3); out.writeInt(7);
        }
        out.writeInt(100); out.writeInt(100); out.writeByte(0); out.writeInt(0);
        Class<?> msg = cls(n("lllllil"));
        Object message = construct(msg, new Class<?>[]{Byte.TYPE, byte[].class}, Byte.valueOf((byte)9), bytes.toByteArray());
        DataInputStream in = (DataInputStream)msg.getMethod(n("lliilli")).invoke(message);
        Method handler = cls(n("lllllll")).getMethod(n(""), msg, Byte.TYPE, Byte.TYPE);
        invoke(handler, null, message, Byte.valueOf((byte)0), Byte.valueOf((byte)1));
    }

    private static void verifyCounters(Object player) throws Exception {
        action(-116);
        require(count("casts", 8) == 0 && count("casts", 7) == 0, "Special ON again: the counters start from zero");
        setStatic(cls(n("liiliii")), n("llillilll"), list(monster(600, 0, 50), monster(601, 0, 80), monster(602, 0, 90)));
        cd(8, 0); cd(7, 0); cd(2, 0);
        long t = 20000000L;
        frame(t);
        cd(8, 99999);
        frame(t += 28);   // 7 gated
        cd(7, 99999);
        frame(t += 28);   // 2
        drain();
        require(count("casts", 8) == 1 && count("casts", 2) == 1 && count("casts", 7) == 0, "Each send counts one cast of its skill");
        at(t += 100);
        int self = (int)number(player, entityType, "cu");
        answer(self, 8, 0, 0, 0);
        require(count("misses", 8) == 1 && count("hits", 8) == 0, "Through the real combat handler: every target at 0 damage (né đòn) is a miss");
        answer(self, 8, 120);
        require(count("hits", 8) == 0 && count("misses", 8) == 1, "An answer with no cast waiting is ignored");
        answer(self + 1, 2, 0);
        require(count("misses", 2) == 0, "Another attacker's answer is ignored");
        answer(self, 2, 0, 75);
        require(count("hits", 2) == 1, "One damaged target makes the cast a hit");
        cd(8, 0);
        frame(t += 700);
        drain();
        at(t + 699);
        counters.getMethod("expire", Long.TYPE).invoke(null, Long.valueOf(t + 699));
        require(count("misses", 8) == 1, "Unanswered for 699ms: still waiting");
        counters.getMethod("expire", Long.TYPE).invoke(null, Long.valueOf(t + 700));
        require(count("misses", 8) == 2 && count("casts", 8) == 2, "No answer within 700ms: a miss");
        Method rate = counters.getMethod("rate", Integer.TYPE, Integer.TYPE);
        require("50%".equals(rate.invoke(null, Integer.valueOf(1), Integer.valueOf(1))) && "66,7%".equals(rate.invoke(null, Integer.valueOf(2), Integer.valueOf(1)))
                && "-".equals(rate.invoke(null, Integer.valueOf(0), Integer.valueOf(0))), "Hit rate: answered hits / answered casts, one decimal with a comma");
        action(-116);
        action(-116);
        require(count("casts", 8) == 0 && count("misses", 8) == 0 && count("hits", 2) == 0, "Special OFF then ON resets the counters");
        cd(8, 0);
        action(-116);
        frame(t += 5000);
        require(drain().isEmpty() && count("casts", 8) == 0, "Special OFF: nothing sent, nothing counted");

        // The file log gets each cast with its answer and targets.
        action(-116);
        cd(8, 0); cd(7, 99999); cd(2, 99999);
        long sent8 = t += 5000;
        frame(sent8);
        require(drain().size() == 1 && count("casts", 8) == 1, "Special ON again: skill 8 goes out");
        at(sent8 + 90);
        answer(self, 8, 10, 0, 0);
        require(count("hits", 8) == 1, "The answer counts the cast as a hit");
        if (!fileLog) return;
        String text = logText();
        String expected = ",8," + sent8 + ",H," + (sent8 + 90) + ",90,3,";
        require(text.indexOf(expected) > 0 && text.indexOf(",Tắt,0") > 0,
                "The log line of the cast: answered, 3 targets sent, nearest distance, pacing Tắt");
    }

    static boolean fileLog;

    private static String logText() throws Exception {
        Class<?> fileLog = cls("luna/features/SkillCastLog");
        fileLog.getMethod("flush").invoke(null);
        String path = (String)fileLog.getMethod("path").invoke(null);
        require(path != null && fileLog.getMethod("error").invoke(null) == null, "The first counted cast opened the file log: " + path);
        StringBuilder text = new StringBuilder();
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
        try { for (String line; (line = in.readLine()) != null;) text.append(line).append('\n'); } finally { in.close(); }
        return text.toString();
    }

    // --------------------------------------------------------------- lock mode

    /** Skill bytes of the attack packets sent since the last drain. */
    private static List<Integer> skillsSent() throws Exception {
        List<Integer> out = new ArrayList<Integer>();
        for (String packet : drain()) {
            int open = packet.indexOf(":[");
            if (!packet.startsWith("9:[") && !packet.startsWith("40:[")) continue;
            out.add(Integer.valueOf(packet.substring(open + 2, packet.indexOf(','))));
            lastPacket = packet;
        }
        return out;
    }

    static String lastPacket;

    /** The server's buff answer (opcode 40) through the real handler: flag, caster type, caster, skill, time, target, type. */
    private static void buffAnswer(int caster, int skill) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1); out.writeByte(1); out.writeShort(caster); out.writeByte(skill); out.writeInt(45000);
        out.writeShort(caster); out.writeByte(0); out.writeByte(skill); out.writeByte(0);
        Class<?> msg = cls(n("lllllil"));
        Object message = construct(msg, new Class<?>[]{Byte.TYPE, byte[].class}, Byte.valueOf((byte)40), bytes.toByteArray());
        Method handler = cls(n("lllllll")).getMethod(n("liilll"), msg);
        try { invoke(handler, null, message); } catch (Throwable nativeRest) { }
    }

    private static void lockFrame(long now, Integer expected, String what, int self) throws Exception {
        frame(now);
        List<Integer> sent = skillsSent();
        require(expected == null ? sent.isEmpty() : sent.equals(Arrays.asList(expected)), what + " " + sent);
        if (expected != null && self != 0) { at(now + 60); answer(self, expected.intValue(), 10); }
    }

    private static void verifyLock(Object player) throws Exception {
        // Learned: 0 Trọng kiếm, 1 (level 2 + bonus 1 = 3), 2 (level 1), 3 (level 2), 7 and 8 (area).
        setStatic(cls(n("lillilll")), "a", list(
                skill(0, 0, "Trọng kiếm", new int[][]{{1, 1, 1000}}),
                skill(1, 0, "Búa địa chấn", new int[][]{{1, 1, 1200}, {1, 1, 1200}, {1, 1, 1200}}),
                skill(2, 0, "Hỏa kiếm", new int[][]{{1, 1, 1200}}),
                skill(3, 0, "Rồng đất", new int[][]{{1, 1, 3200}, {1, 1, 3200}}),
                skill(7, 0, "Gươm cuồng phong", new int[][]{{1, 5, 8200}}),
                skill(8, 0, "Bão lửa", new int[][]{{1, 5, 8200}}),
                skill(14, 1, "Đạn xuyên giáp", new int[][]{{1, 1, 45000}})));
        int[] levels = new int[21], bonus = new int[21];
        levels[0] = levels[2] = levels[7] = levels[8] = 1; levels[1] = 2; bonus[1] = 1; levels[3] = 2;
        Class<?> P = cls(n("lliiil"));
        setStatic(P, n("llilllii"), levels);
        setStatic(P, n("llilllil"), bonus);
        for (int i = 0; i < 21; i++) cd(i, 0);
        setNumber(player, entityType, "bu", 100000);
        setStatic(cls(n("liiliii")), n("llillilll"), list(monster(600, 0, 50), monster(601, 0, 80)));
        int self = (int)number(player, entityType, "cu");
        counters.getMethod("setPaceMode", Integer.TYPE).invoke(null, Integer.valueOf(3));
        counters.getMethod("setRetry", Boolean.TYPE).invoke(null, Boolean.TRUE);
        require("Khóa 550ms".equals(counters.getMethod("paceLabel", Integer.TYPE).invoke(null, counters.getMethod("paceMode").invoke(null)))
                && rt("specialEnabled"), "Lock mode Khóa 550ms with Tấn công đặc biệt ON");
        drain();

        long t = 90000000L;
        lockFrame(t, Integer.valueOf(0), "Trọng kiếm goes first: it sets no server lock", self);
        lockFrame(t + 28, Integer.valueOf(8), "Next frame: the area skills before the rest, 8 before 7", self);
        lockFrame(t + 300, null, "Locked for 550ms after skill 8", 0);
        lockFrame(t + 578, null, "Lock over, but 7 keeps ModUpLv's 600ms gate (and ends the frame)", 0);
        lockFrame(t + 629, Integer.valueOf(7), "Skill 7 after its gate", self);
        cd(0, 0); cd(8, 0);
        lockFrame(t + 1179, Integer.valueOf(0), "Lock over: Trọng kiếm slotted in first again", self);
        lockFrame(t + 1207, null, "8 waits for the gate of skill 7 (578ms)", 0);
        lockFrame(t + 1235, Integer.valueOf(8), "8 goes 606ms after 7: the Trọng kiếm send did not restart the gate", self);
        lockFrame(t + 1785, Integer.valueOf(1), "Then 1..6 by upgraded level: skill 1 (level 3)", self);
        lockFrame(t + 2335, Integer.valueOf(3), "skill 3 (level 2)", self);
        lockFrame(t + 2885, Integer.valueOf(2), "skill 2 (level 1); this one is not answered", 0);
        require(cd(2) == 1200, "Skill 2 on its 1200ms cooldown");
        lockFrame(t + 3585, Integer.valueOf(2), "Unanswered for 700ms: its cooldown is cleared and skill 2 goes again", 0);
        lockFrame(t + 4313, null, "A second miss in a row is not retried", 0);
        require(cd(2) == 1200 && count("hits", 0) == 2 && count("hits", 1) == 1 && count("silent", 2) == 2, "Cooldown kept; counters match");
        if (fileLog) require(logText().contains("# retry skill 2"), "The retry is in the file log");

        // Buff skills (type 1) go out as the native buff request (opcode 40) to the player, not as an attack.
        levels[14] = 1;
        for (int i = 0; i < 21; i++) cd(i, 99999);
        cd(14, 0);
        long buffAt = t + 6000;
        lockFrame(buffAt, Integer.valueOf(14), "Buff skill 14 goes out when the lock is over", 0);
        int cv = (int)number(player, entityType, "cv");
        require(lastPacket.equals("40:[14, " + cv + ", 1, " + (byte)(self >> 8) + ", " + (byte)self + "]"),
                "As the opcode-40 buff request: skill, the player's type, one target (the player); " + lastPacket);
        require(cd(14) == 45000, "Buff cooldown from its level row (45s)");
        at(buffAt + 90);
        buffAnswer(self + 1, 14);
        require(count("hits", 14) == 0, "Another player's buff answer is not ours");
        buffAnswer(self, 14);
        require(count("hits", 14) == 1 && count("misses", 14) == 0, "The server's buff answer (opcode 40) counts the buff as a hit");
        lockFrame(buffAt + 900, null, "Answered: no retry of the buff, its cooldown stays", 0);
        require(cd(14) == 45000, "Buff cooldown kept");
        counters.getMethod("setRetry", Boolean.TYPE).invoke(null, Boolean.FALSE);
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (Method m : type.getMethods()) if (m.getName().equals(name)) return true;
        return false;
    }

    private static List<Integer> sendsAt(long now) throws Exception { frame(now); return skillsSent(); }

    private static long sleepAt(long now, long ms) throws Exception {
        at(now);
        return ((Long)port.getMethod("frameSleep", Long.TYPE).invoke(null, Long.valueOf(ms))).longValue();
    }

    /** The target ids of an attack packet "9:[skill, count, hi, lo, ...]". */
    private static List<Integer> targetIds(String packet) {
        String[] v = packet.substring(packet.indexOf('[') + 1, packet.indexOf(']')).split(", ");
        List<Integer> out = new ArrayList<Integer>();
        for (int i = 2; i + 1 < v.length; i += 2) out.add(Integer.valueOf((Integer.parseInt(v[i]) << 8) | (Integer.parseInt(v[i + 1]) & 255)));
        return out;
    }

    /** Auto's G for the lock that the last non-free send started: G -/+ 15 by the arm drawn at that send. */
    private static long autoWait() throws Exception {
        int[] st = (int[])counters.getMethod("autoState").invoke(null);
        return st[0] + (st[1] < 0 ? -15 : 15);
    }

    /** Auto: the skill right after đòn 0 gets đòn 0's target last; Khóa 575ms sends it a frame later in the old order. */
    private static void verifyRotate(long t) throws Exception {
        for (int mode : new int[]{8, 4}) {
            counters.getMethod("setPaceMode", Integer.TYPE).invoke(null, Integer.valueOf(mode));
            for (int i = 0; i < 21; i++) cd(i, 99999);
            cd(0, 0); cd(3, 0);
            drain();
            frame(t);
            if (mode == 4) frame(t + 28);
            List<String> packets = new ArrayList<String>();
            for (String p : drain()) if (p.startsWith("9:[")) packets.add(p);
            require(packets.size() == 2 && packets.get(0).startsWith("9:[0, ") && packets.get(1).startsWith("9:[3, "), "Mode " + mode + ": đòn 0 then skill 3 " + packets);
            List<Integer> zero = targetIds(packets.get(0)), next = targetIds(packets.get(1));
            List<Integer> expected = new ArrayList<Integer>(zero);
            if (mode == 8) expected.add(expected.remove(0));
            require(zero.size() >= 2 && next.equals(expected), (mode == 8 ? "Auto: đòn 0's target " + zero.get(0) + " goes last for skill 3 "
                    : "Khóa 575ms: skill 3 keeps the nearest-first order ") + zero + " -> " + next);
            t += 30000;
        }
    }

    /**
     * Auto (mode 8): đòn 0 and the next skill in one frame, lock max(send + G, answer + 300ms) with the
     * 575ms fallback while unanswered, early wake at the lock end, the 600ms gate of skill 7.
     */
    private static void verifyAnswerLock(Object player) throws Exception {
        for (int i = 0; i < 21; i++) cd(i, 99999);
        cd(0, 0); cd(8, 0); cd(2, 0);
        int self = (int)number(player, entityType, "cu");
        counters.getMethod("setPaceMode", Integer.TYPE).invoke(null, Integer.valueOf(8));
        require("Auto".equals(counters.getMethod("paceLabel", Integer.TYPE).invoke(null, counters.getMethod("paceMode").invoke(null))), "Pace mode 8 is Auto");
        drain();
        long t = 95000000L;
        require(sendsAt(t).equals(Arrays.asList(0, 8)), "Trọng kiếm, then skill 8 in the same frame");
        long g = autoWait();
        at(t + 340); answer(self, 8, 10);
        long end = t + Math.max(g, 640);
        long f = t + 28;
        for (; f < end - 40; f += 28) require(sendsAt(f).isEmpty(), "Locked: answer after 340ms, 300ms after it binds (frame +" + (f - t) + ")");
        require(sendsAt(end - 10).isEmpty(), "Still locked 10ms before the end");
        require(sleepAt(end - 5, 100L) == 5L, "The canvas sleeps only until the lock end");
        require(sendsAt(end - 1).isEmpty(), "Not before the lock end");
        require(sendsAt(end).equals(Arrays.asList(2)), "At the lock end the frame runs early: skill 2 (0 and 8 on cooldown)");
        long end2 = end + Math.max(575, autoWait());
        require(sendsAt(end2 - 1).isEmpty(), "Skill 2 unanswered: max(G, 575ms) from the send");
        cd(1, 0);
        require(sendsAt(end2).equals(Arrays.asList(1)), "Skill 1 at the fallback (early frame)");
        long g3 = autoWait();
        at(end2 + 20); answer(self, 1, 10);
        long end3 = end2 + Math.max(g3, 320);
        cd(0, 0); cd(7, 0);
        require(sendsAt(end3 - 1).isEmpty(), "Fast answer (20ms): G from the send binds (" + g3 + "ms)");
        require(sendsAt(end3).equals(Arrays.asList(0)), "Trọng kiếm goes; skill 7 keeps its 600ms gate and ends the frame");
        long when = Math.max(end3 + 28, end2 + 601);
        require(sendsAt(when).equals(Arrays.asList(7)), "Skill 7 after its gate");
        counters.getMethod("setPaceMode", Integer.TYPE).invoke(null, Integer.valueOf(4));
        cd(0, 0); cd(3, 0);
        long k = when + 1000;
        require(sendsAt(k).equals(Arrays.asList(0)) && sendsAt(k + 28).equals(Arrays.asList(3)), "Khóa 575ms keeps one send per frame");
        require(sleepAt(k + 40, 100L) == 16L, "Khóa 575ms keeps the 28ms frame sleep");
        verifyRotate(k + 30000);
    }
}
