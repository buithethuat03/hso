import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Auto thay đồ khi BUFF inside the candidate (docs/HUNG_BY_SON_BUFF_SWAP.md): the Tùy chọn tự đánh
 * row, the module loader, the submenu of learned buffs, and the auto against the real MOD "Thay Đồ"
 * module (Mặc Set 1/2 through the native MOD dispatcher), the real Tấn công đặc biệt loop sending the
 * buff (opcode 40) and the real opcode-40 answer handler. Packets are captured at the native send
 * queue. Reuses HungBySonAutoAttackBinaryTest's probe loader and world.
 */
public final class HungBySonBuffSwapBinaryTest {
    private static final String HELPER = "HungBySonAutoAttackBinaryTest";
    private static final int ACTION = -107, ARMOR = 14, GUARD = 9, STRIKE = 2, SELF = 4242;
    private static int checks;
    private static ClassLoader loader;
    private static Class<?> port, entityType, runtime, swapType, itemType;
    private static Object probe, player, swap;
    private static Object[] cooldowns;
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
        else throw new IllegalArgumentException(name + " " + t);
    }
    private static void setStatic(Class<?> owner, String name, Object value) throws Exception { field(owner, name).set(null, value); }
    private static Object getStatic(Class<?> owner, String name) throws Exception { return field(owner, name).get(null); }
    private static boolean flag(Class<?> owner, String name) throws Exception { return field(owner, name).getBoolean(null); }
    private static int count(String name) throws Exception { return field(port, name).getInt(null); }

    private static Object list(Object... items) throws Exception {
        Object v = invoke(helper("newNativeList", Class.class, String.class), null, cls("et"), "Buff swap test");
        Method add = cls("et").getMethod(n("l"), Object.class);
        for (Object i : items) add.invoke(v, i);
        return v;
    }
    private static int size(Object list) throws Exception { return ((Integer)cls("et").getMethod("c").invoke(list)).intValue(); }
    private static Object at(Object list, int i) throws Exception { return cls("et").getMethod("a", Integer.TYPE).invoke(list, Integer.valueOf(i)); }

    private static Object lastPopup() throws Exception { return invoke(probe.getClass().getDeclaredMethod("lastPopup"), probe); }
    private static String title() throws Exception { return (String)field(lastPopup().getClass(), "title").get(lastPopup()); }
    private static Object rows() throws Exception { return field(lastPopup().getClass(), "items").get(lastPopup()); }
    private static String label(Object row) throws Exception { return (String)field(cls("bt"), n("lliiilli")).get(row); }
    private static int action(Object row) throws Exception { return field(cls("bt"), "e").getByte(row); }
    private static List<String> labels() throws Exception {
        List<String> out = new ArrayList<String>();
        Object rows = rows();
        for (int i = 0; i < size(rows); i++) out.add(label(at(rows, i)) + "#" + action(at(rows, i)));
        return out;
    }
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

    private static Object dialogShown() throws Exception { return getStatic(cls(n("liilllii")), n("liiililiili")); }
    private static void clearDialog() throws Exception { setStatic(cls(n("liilllii")), n("liiililiili"), null); setStatic(port, "popupText", null); }

    // --------------------------------------------------------------- world

    static Object skill(int id, int type, String name, int rows, int cooldown) throws Exception {
        Class<?> skillType = cls(n("liiiiill")), levelType = cls(n("liiilill"));
        Object array = Array.newInstance(levelType, rows);
        for (int i = 0; i < rows; i++) {
            Object level = allocate(levelType);
            setNumber(level, levelType, n("lilllli"), 1);
            setNumber(level, levelType, "j", 1);
            setNumber(level, levelType, "g", cooldown);
            Array.set(array, i, level);
        }
        Object s = allocate(skillType);
        setNumber(s, skillType, "b", id); setNumber(s, skillType, "e", type);
        field(skillType, n("lllillllil")).set(s, array);
        field(skillType, n("lllilllil")).set(s, name);
        return s;
    }
    private static int cd(int skill) throws Exception { return field(cooldowns[skill].getClass(), "b").getInt(cooldowns[skill]); }
    private static void cd(int skill, int value) throws Exception { setNumber(cooldowns[skill], cooldowns[skill].getClass(), "b", value); }

    /** Learned: 2 (attack), 9 Hộ Thể (buff, level 1), 14 Đạn xuyên giáp (buff, 12 + bonus 1 = Lv13). 16 is an unlearned buff. */
    private static void installSkills() throws Exception {
        setStatic(cls(n("lillilll")), "a", list(
                skill(STRIKE, 0, "Chém Gió", 1, 500),
                skill(GUARD, 1, "Hộ Thể", 1, 30000),
                skill(ARMOR, 1, "Đạn xuyên giáp", 13, 52000),
                skill(16, 1, "Chưa học", 1, 30000)));
        int[] levels = new int[21], bonus = new int[21];
        levels[STRIKE] = levels[GUARD] = 1; levels[ARMOR] = 12; bonus[ARMOR] = 1;
        Class<?> P = cls(n("lliiil"));
        setStatic(P, n("llilllii"), levels);
        setStatic(P, n("llilllil"), bonus);
        Class<?> cdType = field(P, n("lliillli")).getType().getComponentType();
        Object cdArray = Array.newInstance(cdType, 21);
        cooldowns = new Object[21];
        for (int i = 0; i < 21; i++) { cooldowns[i] = allocate(cdType); Array.set(cdArray, i, cooldowns[i]); }
        setStatic(P, n("lliillli"), cdArray);
        for (int i = 0; i < 21; i++) cd(i, 99999);
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
    private static boolean rt(String name) throws Exception { return ((Boolean)runtime.getMethod(name).invoke(null)).booleanValue(); }
    private static void special(boolean on) throws Exception {
        if (rt("specialEnabled") != on) runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-116));
        require(rt("specialEnabled") == on, "Tấn công đặc biệt " + (on ? "ON" : "OFF"));
    }
    private static void farm(boolean on) throws Exception { field(cls(n("lliiiiil")), n("lililiilill")).setBoolean(null, on); }

    // --------------------------------------------------------------- Thay Đồ

    private static void thayDo(boolean on) throws Exception { field(swapType, n("liilliliili")).setBoolean(null, on); }
    @SuppressWarnings("unchecked")
    private static Vector<Object> set(int n) throws Exception { return (Vector<Object>)getStatic(swapType, n == 1 ? n("liliiiiiiil") : n("liliiiiiili")); }
    private static boolean wearing(int n) throws Exception { return flag(swapType, n == 1 ? n("liliiiiilll") : n == 2 ? n("liliiiiliii") : n("liliiiiliil")); }
    private static boolean busy() throws Exception { return wearing(1) || wearing(2) || wearing(3); }
    /** The Thay Đồ module's own update until its queue is worn; the packets it sent. */
    private static List<String> finishWear() throws Exception {
        List<String> sent = new ArrayList<String>();
        Method update = swap.getClass().getMethod("update");
        for (int i = 0; i < 200 && busy(); i++) { invoke(update, swap); sent.addAll(drain()); }
        require(!busy(), "Thay Đồ finished wearing");
        return sent;
    }

    // --------------------------------------------------------------- packets

    private static void at(long now) throws Exception {
        clockNow = now;
        setStatic(port, "testNow", Long.valueOf(now));
        setStatic(cls("HungBySonModUpLv"), "testNow", Long.valueOf(now));
        setStatic(cls("HungBySonSkillStats"), "testNow", Long.valueOf(now));
    }
    private static long now = 70000000L;
    /** One update of the buff-swap module, `advance` ms later. */
    private static void tick(long advance) throws Exception {
        now += advance;
        at(now);
        port.getMethod("update").invoke(getStatic(port, "INSTANCE"));
    }
    /** One Tấn công đặc biệt frame at the current time: the opcode-40 buff request when a buff is ready. */
    private static List<String> frame() throws Exception {
        at(now);
        invoke(cls("HungBySonModUpLv").getMethod("frame"), null);
        return drain();
    }
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

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("candidate.jar auto-attack.properties auto-pk.properties");
        File candidate = new File(args[0]);
        Properties auto = load(new File(args[1])), catalog = load(new File(args[2]));
        require("true".equals(catalog.getProperty("buff.swap")) && "HungBySonBuffSwap".equals(catalog.getProperty("buff.swap.owner")),
                "Catalog records Auto thay đồ khi BUFF");
        verifyStructure(candidate);

        probe = construct(Class.forName(HELPER + "$NativeProbeLoader"), new Class<?>[]{File.class, Properties.class}, candidate, auto);
        loader = (ClassLoader)probe;
        invoke(helper("seedNativeFontMetrics", Class.forName(HELPER + "$NativeProbeLoader")), null, probe);
        port = cls("HungBySonBuffSwap");
        swapType = cls(n("lillllil"));
        itemType = cls(n("llilli"));
        entityType = cls(catalog.getProperty("entity.owner"));
        runtime = cls("luna/features/AutoAttackRuntime");
        Class<?> bridgeType = cls("luna/generated/HungAutoAttackAccess");
        Object bridge = bridgeType.getDeclaredConstructor().newInstance();
        Object access = construct(Class.forName(HELPER + "$NativeAccess"), new Class<?>[]{ClassLoader.class, Object.class, Properties.class}, loader, bridge, auto);
        invoke(access.getClass().getDeclaredMethod("installReadyWorld"), access);
        player = getStatic(cls(n("liiliii")), n("llilliill"));
        setNumber(player, entityType, "cG", 0);
        setNumber(player, entityType, "bu", 100000);
        setNumber(player, entityType, "cu", SELF);
        installController(bridgeType, bridge);
        require(((Boolean)bridgeType.getMethod("farmWorldReady").invoke(null)).booleanValue(), "Test world is ready (logged in, alive)");
        // The local licence flags granted at login (HungBySonOfflineLocal); Thay Đồ's update turns itself off without them.
        Class<?> licence = cls(n("lliiliil"));
        field(licence, n("lilliilllii")).setBoolean(null, true);
        field(licence, n("lilliililli")).setBoolean(null, false);
        // The ModUpLv loop with pacing Tắt and no retries, so a frame sends exactly when a skill is ready.
        Class<?> counters = cls("luna/features/SkillCastStats");
        counters.getMethod("setPaceMode", Integer.TYPE).invoke(null, Integer.valueOf(0));
        counters.getMethod("setRetry", Boolean.TYPE).invoke(null, Boolean.FALSE);
        installSkills();

        verifyModules();
        verifyMenu();
        verifyGate();
        verifyCycle();
        verifyTimeouts();
        verifyPerSkill();
        verifyGuards();
        verifyEmptySet();
        System.out.println("HungBySonBuffSwapBinaryTest PASS checks=" + checks);
    }

    // --------------------------------------------------------------- bytecode wiring

    private static void verifyStructure(File candidate) throws Exception {
        JarFile jar = new JarFile(candidate);
        try {
            int rows = 0, answers = 0;
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                JarEntry z = e.nextElement();
                if (!z.getName().endsWith(".class") || z.getName().equals("HungBySonBuffSwap.class")) continue;
                ClassNode c = new ClassNode();
                new ClassReader(jar.getInputStream(z)).accept(c, 0);
                for (MethodNode m : c.methods) for (AbstractInsnNode x : m.instructions) {
                    if (!(x instanceof MethodInsnNode) || !((MethodInsnNode)x).owner.equals("HungBySonBuffSwap")) continue;
                    String name = ((MethodInsnNode)x).name;
                    if (name.equals("addAttackRows")) { rows++; require(c.name.equals(n("lliiiiil")) && m.name.equals(n("lilllili")), "addAttackRows is called by Tùy chọn tự đánh"); }
                    if (name.equals("onBuff")) {
                        answers++;
                        AbstractInsnNode prior = x.getPrevious();
                        for (int k = 0; k < 2; k++) { prior = prior.getPrevious(); while (prior.getOpcode() < 0) prior = prior.getPrevious(); }
                        require(c.name.equals(n("lllllll")) && m.name.equals(n("liilll")) && prior instanceof MethodInsnNode
                                && ((MethodInsnNode)prior).owner.equals("HungBySonSkillStats") && ((MethodInsnNode)prior).name.equals("buff"),
                                "onBuff follows HungBySonSkillStats.buff in the opcode-40 answer handler");
                    }
                }
            }
            require(rows == 1 && answers == 1, "Tùy chọn tự đánh and the opcode-40 handler each reach HungBySonBuffSwap once");
            ClassNode portNode = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry("HungBySonBuffSwap.class"))).accept(portNode, 0);
            require(portNode.version == Opcodes.V1_4 && portNode.interfaces.contains("IModModule"), "Module class is a class-48 IModModule");
        } finally { jar.close(); }
    }

    private static void verifyModules() throws Exception {
        Class<?> mods = cls(n("lillllll"));
        mods.getMethod(n("lilliiii")).invoke(null);
        Vector<?> modules = (Vector<?>)getStatic(mods, n("liliiliilil"));
        int mine = modules.indexOf(getStatic(port, "INSTANCE"));
        require(mine >= 1 && mine == modules.size() - 2 && modules.lastElement() == getStatic(cls("HungBySonModUpLv"), "INSTANCE")
                && modules.get(mine - 1) == getStatic(cls("HungBySonStamina"), "INSTANCE"),
                "Loader registers the buff-swap module between the stamina module and the ModUpLv port, which stays last");
        for (Object m : modules) if (m.getClass() == swapType) swap = m;
        require(swap != null, "The MOD Thay Đồ module is registered");
    }

    // --------------------------------------------------------------- menu

    private static void verifyMenu() throws Exception {
        cls(n("lliiiiil")).getMethod(n("lilllili")).invoke(null);
        List<String> rows = labels();
        int mine = rows.indexOf("Auto thay đồ khi BUFF >#-107");
        require(title().equals("TÙY CHỌN TỰ ĐÁNH") && mine > 0 && rows.get(mine + 1).equals("<< Quay Lại#-39"),
                "Tùy chọn tự đánh: the row sits right before << Quay Lại: " + rows);
        click(row("Auto thay đồ khi BUFF"));
        require(title().equals("AUTO THAY ĐỒ KHI BUFF") && labels().equals(Arrays.asList(
                "Auto thay đồ: [TẮT]#-107", "Hộ Thể Lv1#-107", "Đạn xuyên giáp Lv13#-107", "<< Quay Lại#-107")),
                "Submenu: the switch, learned buffs with name + Lv (level + bonus), back; no attack skill, no unlearned buff: " + labels());
        click(row("Đạn xuyên giáp"));
        require(labels().contains("Đạn xuyên giáp Lv13 [x]#-107") && labels().contains("Hộ Thể Lv1#-107"), "Choosing a buff marks it [x]");
        click(row("Hộ Thể"));
        click(row("Hộ Thể"));
        require(labels().contains("Hộ Thể Lv1#-107"), "Choosing again clears [x]");
        thayDo(false);
        click(row("Auto thay đồ:"));
        require(flag(port, "enabled") && labels().get(0).equals("Auto thay đồ: [BẬT]#-107"), "The switch turns ON and the submenu reopens with [BẬT]");
        require("Auto thay đồ khi buff cần: bật Menu > Thay Đồ".equals(getStatic(port, "popupText")) && dialogShown() != null,
                "Thay Đồ switch OFF: an Ok dialog says what is missing");
        clearDialog();
        click(row("Auto thay đồ:"));
        require(!flag(port, "enabled") && labels().get(0).equals("Auto thay đồ: [TẮT]#-107"), "Clicking again turns it OFF");
        click(row("<< Quay Lại"));
        require(title().equals("TÙY CHỌN TỰ ĐÁNH"), "<< Quay Lại returns to Tùy chọn tự đánh");

        int[] levels = (int[])getStatic(cls(n("lliiil")), n("llilllii"));
        levels[GUARD] = levels[ARMOR] = 0;
        port.getMethod("onAction", Integer.TYPE, Integer.TYPE).invoke(getStatic(port, "INSTANCE"), Integer.valueOf(ACTION), Integer.valueOf(0));
        require(labels().equals(Arrays.asList("Auto thay đồ: [TẮT]#-107", "Chưa học chiêu buff nào#-107", "<< Quay Lại#-107")),
                "No learned buff: a placeholder row: " + labels());
        levels[GUARD] = 1; levels[ARMOR] = 12;
        require(!((Boolean)port.getMethod("onAction", Integer.TYPE, Integer.TYPE).invoke(getStatic(port, "INSTANCE"), Integer.valueOf(-24), Integer.valueOf(0))).booleanValue(),
                "Other actions (Mặc Set 1) are left to their module");
        drain();
    }

    // --------------------------------------------------------------- auto

    private static void on() throws Exception {
        if (!flag(port, "enabled")) port.getMethod("onAction", Integer.TYPE, Integer.TYPE).invoke(getStatic(port, "INSTANCE"), Integer.valueOf(ACTION), Integer.valueOf(1));
        require(flag(port, "enabled"), "Auto thay đồ khi buff ON");
        clearDialog();
        drain();
    }
    private static void choose(int skill, boolean on) throws Exception { ((boolean[])getStatic(port, "chosen"))[skill] = on; }

    /** A bag equipment (category 3) and its Thay Đồ key "id_byte_type_N"; type 12 is worn to slot 0. */
    private static Object gear(int id) throws Exception {
        Object item = allocate(itemType);
        setNumber(item, itemType, "O", id); setNumber(item, itemType, n("liiill"), 3);
        setNumber(item, itemType, n("llilil"), 0); setNumber(item, itemType, n("llliiili"), 12); setNumber(item, itemType, "N", 0);
        field(itemType, n("liiiiliii")).set(item, "Dây chuyền " + id);
        return item;
    }
    static final int SET1_ITEM = 21, SET2_ITEM = 22;
    static final List<String> WEAR1 = Arrays.asList("11:[" + SET1_ITEM + ", 0]"), WEAR2 = Arrays.asList("11:[" + SET2_ITEM + ", 0]");

    private static void verifyGate() throws Exception {
        thayDo(true);
        setStatic(itemType, n("liiillili"), list(gear(SET1_ITEM), gear(SET2_ITEM)));
        set(1).addElement(SET1_ITEM + "_0_12_0");
        set(2).addElement(SET2_ITEM + "_0_12_0");
        choose(ARMOR, true);
        on();
        special(false); farm(false);
        cd(ARMOR, 1500);
        tick(100);
        require(count("wears1") == 0 && !busy(), "Farm and Tấn công đặc biệt OFF: no change of gear");
        thayDo(false);
        special(true);
        tick(100);
        require(count("wears1") == 0 && !busy(), "Thay Đồ switch OFF: no change of gear");
        thayDo(true);
        special(false);
        farm(true);
        tick(100);
        require(count("wears1") == 1 && wearing(1), "Auto Farm ON is enough: 1.5s left -> Mặc Set 1 through the Thay Đồ module");
        List<String> sent = finishWear();
        require(sent.equals(WEAR1), "Thay Đồ wears the Set 1 item with its native equip packet (opcode 11): " + sent);
        farm(false);
        tick(100);
        require(count("wears2") == 1 && wearing(2), "Farm OFF again: the auto stops and puts Set 2 back on");
        sent = finishWear();
        require(sent.equals(WEAR2), "... with the Set 2 item's equip packet: " + sent);
        cd(ARMOR, 99999);
        drain();
    }

    private static void verifyCycle() throws Exception {
        special(true);
        int w1 = count("wears1"), w2 = count("wears2");
        cd(ARMOR, 2500);
        tick(100);
        require(count("wears1") == w1, "2.5s of cooldown left: not yet");
        cd(ARMOR, 2000);
        tick(100);
        require(count("wears1") == w1 + 1 && wearing(1), "2s left: Mặc Set 1 at once (\"Đang mặc Set 1...\")");
        tick(100);
        require(count("wears1") == w1 + 1, "Set 1 is asked once");
        require(finishWear().equals(WEAR1), "Set 1 item worn (opcode 11)");
        cd(ARMOR, 0);
        List<String> sent = frame();
        boolean buff = false;
        for (String p : sent) buff |= p.startsWith("40:[" + ARMOR + ",");
        require(buff && cd(ARMOR) == 52000, "Tấn công đặc biệt casts the ready buff itself (opcode 40) and starts its cooldown: " + sent);
        tick(100);
        require(count("wears2") == w2 && !busy(), "Cast sent, no answer yet: Set 1 stays on");
        buffAnswer(SELF + 1, ARMOR);
        tick(100);
        require(count("wears2") == w2, "Another player's buff answer is not ours");
        buffAnswer(SELF, ARMOR);
        tick(100);
        require(count("wears2") == w2 + 1 && wearing(2) && count("successes") == 1,
                "The server's buff answer (opcode 40) for our cast: Mặc Set 2 at once");
        require(finishWear().equals(WEAR2), "Set 2 item worn (opcode 11) after the buff");
        tick(1000);
        require(count("wears1") == w1 + 1, "Cooldown restarted: no new cycle until 2s are left again");
        cd(ARMOR, 1900);
        tick(100);
        require(count("wears1") == w1 + 2, "Next cycle: Set 1 again at 2s");
        finishWear();
        cd(ARMOR, 0);
        frame();
        buffAnswer(SELF, ARMOR);
        tick(100);
        require(count("wears2") == w2 + 2, "... and Set 2 after its answer");
        finishWear();
        drain();
    }

    private static void verifyTimeouts() throws Exception {
        int w1 = count("wears1"), w2 = count("wears2"), g = count("giveUps");
        // Ready but never cast (no frame runs): give up after 5s.
        cd(ARMOR, 1000);
        tick(100);
        finishWear();
        cd(ARMOR, 0);
        tick(100);
        tick(4800);
        require(count("wears2") == w2, "Ready for 4.9s without a cast: still waiting on Set 1");
        tick(200);
        require(count("giveUps") == g + 1 && count("wears2") == w2 + 1, "Ready for 5s without a cast: give up and put Set 2 back");
        finishWear();
        tick(3000);
        require(count("wears1") == w1 + 1, "After giving up, no new Set 1 inside 10s");
        tick(7000);
        require(count("wears1") == w1 + 2, "10s later the skill may ask for Set 1 again");
        finishWear();
        // Cast but never answered: give up 2s after the cooldown restarted.
        cd(ARMOR, 52000);
        tick(100);
        tick(1800);
        require(count("wears2") == w2 + 1, "Cast without an answer for 1.9s: still waiting");
        tick(200);
        require(count("giveUps") == g + 2 && count("wears2") == w2 + 2, "No answer for 2s after the cast: give up, Set 2");
        finishWear();
        cd(ARMOR, 99999);
        tick(20000);
        drain();
    }

    private static void verifyPerSkill() throws Exception {
        choose(GUARD, true);
        int w1 = count("wears1"), w2 = count("wears2");
        cd(GUARD, 1500);
        tick(100);
        require(count("wears1") == w1 + 1, "Hộ Thể at 1.5s: Set 1");
        finishWear();
        cd(ARMOR, 1800);
        tick(100);
        require(count("wears1") == w1 + 1, "Đạn xuyên giáp arms while Set 1 is already on: no second Mặc Set 1");
        cd(GUARD, 30000);
        buffAnswer(SELF, GUARD);
        tick(100);
        require(count("wears2") == w2, "Hộ Thể answered, Đạn xuyên giáp still waiting: Set 1 stays");
        cd(ARMOR, 52000);
        buffAnswer(SELF, ARMOR);
        tick(100);
        require(count("wears2") == w2 + 1, "Both answered: Set 2");
        finishWear();
        buffAnswer(SELF, GUARD);
        cd(GUARD, 1000);
        tick(100);
        require(count("wears1") == w1 + 2, "An answer from before the skill armed does not end its cycle");
        finishWear();
        cd(GUARD, 30000);
        buffAnswer(SELF, GUARD);
        tick(100);
        finishWear();
        choose(GUARD, false);
        cd(GUARD, 1000);
        tick(100);
        require(count("wears1") == w1 + 2, "Unchosen buff: ignored");
        cd(GUARD, 99999); cd(ARMOR, 99999);
        drain();
    }

    private static void verifyGuards() throws Exception {
        int w1 = count("wears1"), w2 = count("wears2");
        // Dead: nothing.
        setNumber(player, entityType, "cG", 4);
        cd(ARMOR, 1000);
        tick(100);
        require(count("wears1") == w1, "Dead: no change of gear");
        setNumber(player, entityType, "cG", 0);
        tick(100);
        require(count("wears1") == w1 + 1, "Alive again: Set 1");
        finishWear();
        // Thay Đồ switched OFF while Set 1 is on: wait, then put Set 2 back once it is ON again.
        thayDo(false);
        special(false);
        tick(100);
        require(count("wears2") == w2, "Thay Đồ OFF: Set 2 cannot be worn yet");
        thayDo(true);
        tick(100);
        require(count("wears2") == w2 + 1, "Thay Đồ ON again: Set 2 goes back on");
        finishWear();
        special(true);
        // Turning the auto OFF while Set 1 is on.
        tick(100);
        finishWear();
        port.getMethod("onAction", Integer.TYPE, Integer.TYPE).invoke(getStatic(port, "INSTANCE"), Integer.valueOf(ACTION), Integer.valueOf(1));
        require(!flag(port, "enabled"), "Auto OFF");
        tick(100);
        require(count("wears2") == w2 + 2, "Auto OFF with Set 1 on: Set 2 goes back on");
        finishWear();
        tick(100);
        require(count("wears1") == w1 + 2 && count("wears2") == w2 + 2, "Auto OFF: nothing more");
        cd(ARMOR, 99999);
        drain();
    }

    private static void verifyEmptySet() throws Exception {
        on();
        Vector<Object> saved = new Vector<Object>(set(1));
        set(1).removeAllElements();
        cd(ARMOR, 1000);
        tick(100);
        String text = (String)getStatic(port, "popupText");
        require(!flag(port, "enabled") && !busy() && text != null && text.startsWith("Set 1 trống!") && dialogShown() != null,
                "Empty Set 1: the Thay Đồ module wears nothing, the auto turns OFF with an Ok dialog: " + text);
        clearDialog();
        tick(100);
        require(!busy(), "OFF: no retry every tick");
        set(1).addAll(saved);
        drain();
    }
}
