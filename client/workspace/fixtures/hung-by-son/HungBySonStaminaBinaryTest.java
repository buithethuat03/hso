import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Auto phục hồi thể lực inside the candidate (docs/HUNG_BY_SON_STAMINA.md): the config-menu row, the
 * module loader, the native opcode-59 reply handler fed real messages, the native dialog filter, and
 * the auto with packets captured at the native send queue. Reuses HungBySonAutoAttackBinaryTest's
 * probe loader and world.
 */
public final class HungBySonStaminaBinaryTest {
    private static final String HELPER = "HungBySonAutoAttackBinaryTest";
    private static final String REQUEST = "59:[]";
    private static int checks;
    private static boolean buffSwap;
    private static ClassLoader loader;
    private static Class<?> port, itemType, entityType;
    private static Object probe, player;

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

    private static Object list(Object... items) throws Exception {
        Object v = invoke(helper("newNativeList", Class.class, String.class), null, cls("et"), "Stamina test");
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
    private static String use(int id) { return "32:[" + (byte)((id >> 8) & 255) + ", " + (byte)(id & 255) + "]"; }

    private static long now = 5000000L;
    private static List<String> tick(long advance) throws Exception {
        now += advance;
        setStatic(port, "testNow", Long.valueOf(now));
        port.getMethod("update").invoke(getStatic(port, "INSTANCE"));
        return drain();
    }
    private static boolean enabled() throws Exception { return field(port, "enabled").getBoolean(null); }
    private static int stamina() throws Exception { return field(cls(n("lliiil")), "e").getInt(null); }

    /** The real opcode-59 reply handler with (int stamina, int second). */
    private static void reply(int stamina, int second) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(stamina); out.writeInt(second); out.close();
        Class<?> message = cls(n("lllllil"));
        Object m = construct(message, new Class<?>[]{Byte.TYPE, byte[].class}, Byte.valueOf((byte)59), bytes.toByteArray());
        invoke(cls(n("lllllll")).getMethod(n("llilii"), message), null, m);
    }

    private static Object item(int id, int category, String name) throws Exception {
        Object item = allocate(itemType);
        setNumber(item, itemType, "O", id); setNumber(item, itemType, n("liiill"), category);
        field(itemType, n("liiiiliii")).set(item, name);
        return item;
    }
    private static void bag(Object... items) throws Exception { setStatic(itemType, n("liiillili"), list(items)); }

    private static Object dialogShown() throws Exception { return getStatic(cls(n("liilllii")), n("liiililiili")); }
    private static void clearDialog() throws Exception { setStatic(cls(n("liilllii")), n("liiililiili"), null); setStatic(port, "popupText", null); }
    /** A server dialog through the native Ok dialog opener. */
    private static void serverDialog(String text) throws Exception { invoke(cls(n("liilllii")).getMethod("a", String.class), null, text); }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("candidate.jar auto-attack.properties auto-pk.properties");
        File candidate = new File(args[0]);
        Properties auto = load(new File(args[1])), catalog = load(new File(args[2]));
        require("true".equals(catalog.getProperty("stamina")) && "HungBySonStamina".equals(catalog.getProperty("stamina.owner")),
                "Catalog records Auto phục hồi thể lực");
        buffSwap = "true".equals(catalog.getProperty("buff.swap"));
        verifyStructure(candidate);

        probe = construct(Class.forName(HELPER + "$NativeProbeLoader"), new Class<?>[]{File.class, Properties.class}, candidate, auto);
        loader = (ClassLoader)probe;
        invoke(helper("seedNativeFontMetrics", Class.forName(HELPER + "$NativeProbeLoader")), null, probe);
        port = cls("HungBySonStamina");
        itemType = cls(n("llilli"));
        entityType = cls(catalog.getProperty("entity.owner"));
        Class<?> bridgeType = cls("luna/generated/HungAutoAttackAccess");
        Object bridge = bridgeType.getDeclaredConstructor().newInstance();
        Object access = construct(Class.forName(HELPER + "$NativeAccess"), new Class<?>[]{ClassLoader.class, Object.class, Properties.class}, loader, bridge, auto);
        invoke(access.getClass().getDeclaredMethod("installReadyWorld"), access);
        require(((Boolean)bridgeType.getMethod("farmWorldReady").invoke(null)).booleanValue(), "Test world is ready (logged in, alive)");
        player = getStatic(cls(n("liiliii")), n("llilliill"));

        verifyModules();
        verifyHandler();
        verifyMenu();
        verifyPoll();
        verifyRecovery();
        verifyDailyLimit();
        verifyGuards();
        System.out.println("HungBySonStaminaBinaryTest PASS checks=" + checks);
    }

    // --------------------------------------------------------------- bytecode wiring

    private static void verifyStructure(File candidate) throws Exception {
        JarFile jar = new JarFile(candidate);
        try {
            int rows = 0, dialogs = 0, notifies = 0, writers = 0;
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                JarEntry z = e.nextElement();
                if (!z.getName().endsWith(".class") || z.getName().equals("HungBySonStamina.class")) continue;
                ClassNode c = new ClassNode();
                new ClassReader(jar.getInputStream(z)).accept(c, 0);
                for (MethodNode m : c.methods) for (AbstractInsnNode x : m.instructions) {
                    if (x instanceof FieldInsnNode && x.getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode)x).owner.equals(n("lliiil"))
                            && ((FieldInsnNode)x).name.equals("e")) writers++;
                    if (!(x instanceof MethodInsnNode) || !((MethodInsnNode)x).owner.equals("HungBySonStamina")) continue;
                    String name = ((MethodInsnNode)x).name;
                    if (name.equals("addConfigRows")) { rows++; require(c.name.equals(n("lliiiiil")) && m.name.equals(n("liliilll")), "addConfigRows is called by the config menu"); }
                    if (name.equals("onDialog")) { dialogs++; require(c.name.equals(n("lillllll")) && m.name.equals(n("lilll")) && m.desc.equals("(Ljava/lang/String;)Z"), "onDialog is called by the dialog filter"); }
                    if (name.equals("onStamina")) { notifies++; require(c.name.equals(n("lllllll")) && m.name.equals(n("llilii")), "onStamina is called by the opcode-59 handler"); }
                }
            }
            require(rows == 1 && dialogs == 1 && notifies == 1, "Config menu, dialog filter and opcode-59 handler each reach HungBySonStamina once");
            // X3 slot 1: the packer turns <clinit> into cinitclone() and adds the clears() reset of the same statics.
            boolean x3 = jar.getJarEntry("MrQuyet/MrQuyet.class") != null;
            require(writers == (x3 ? 3 : 2), "Stamina keeps its native writers (" + (x3 ? "X3 cinitclone/clears" : "<clinit>") + ", opcode 59)");
            ClassNode portNode = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry("HungBySonStamina.class"))).accept(portNode, 0);
            require(portNode.version == Opcodes.V1_4 && portNode.interfaces.contains("IModModule"), "Module class is a class-48 IModModule");
        } finally { jar.close(); }
    }

    private static void verifyModules() throws Exception {
        Class<?> mods = cls(n("lillllll"));
        mods.getMethod(n("lilliiii")).invoke(null);
        Vector<?> modules = (Vector<?>)getStatic(mods, n("liliiliilil"));
        int mine = modules.indexOf(getStatic(port, "INSTANCE"));
        // From Auto thay đồ khi BUFF on, its module registers between this one and the ModUpLv port.
        int gap = buffSwap ? 1 : 0;
        require(mine >= 1 && mine == modules.size() - 2 - gap && modules.lastElement() == getStatic(cls("HungBySonModUpLv"), "INSTANCE")
                && modules.get(mine - 1) == getStatic(cls("HungBySonEnhance"), "INSTANCE")
                && (!buffSwap || modules.get(mine + 1) == getStatic(cls("HungBySonBuffSwap"), "INSTANCE")),
                "Loader registers the stamina module between Đập đồ and " + (buffSwap ? "the buff-swap module, then " : "") + "the ModUpLv port, which stays last");
    }

    private static void verifyHandler() throws Exception {
        int before = field(port, "replies").getInt(null);
        reply(20843, 7);
        require(stamina() == 20843 && field(cls(n("lliiil")), "i").getInt(null) == 7, "Opcode-59 handler still stores both ints");
        require(field(port, "replies").getInt(null) == before + 1, "The handler reports each reply to the module");
        require(drain().isEmpty() && dialogShown() == null, "A reply alone sends nothing and opens nothing");
    }

    // --------------------------------------------------------------- menu

    private static void verifyMenu() throws Exception {
        Method config = cls(n("lliiiiil")).getMethod(n("liliilll"));
        config.invoke(null);
        List<String> rows = labels();
        int mine = rows.indexOf("Auto Phục Hồi Thể Lực: [TẮT]#-21");
        require(title().startsWith("CÀI ĐẶT") && mine > 0 && rows.get(mine - 1).startsWith("Auto Lửa Thiêng")
                && rows.get(mine + 1).equals("Reset Cấu Hình#-41"), "Cài đặt cấu hình: the row sits after Auto Lửa Thiêng, before Reset: " + rows);
        click(row("Auto Phục Hồi Thể Lực"));
        require(enabled() && title().startsWith("CÀI ĐẶT") && labels().contains("Auto Phục Hồi Thể Lực: [BẬT]#-21"),
                "Clicking turns it ON and reopens the config menu with [BẬT]");
        require(dialogShown() == null, "Turning it on opens no dialog");
        click(row("Auto Phục Hồi Thể Lực"));
        require(!enabled() && labels().contains("Auto Phục Hồi Thể Lực: [TẮT]#-21"), "Clicking again turns it OFF");
        drain();
    }

    // --------------------------------------------------------------- auto

    private static void on() throws Exception {
        if (!enabled()) port.getMethod("onAction", Integer.TYPE, Integer.TYPE).invoke(getStatic(port, "INSTANCE"), Integer.valueOf(-21), Integer.valueOf(0));
        require(enabled(), "Auto phục hồi thể lực ON");
        drain();
    }

    private static void verifyPoll() throws Exception {
        require(tick(0).isEmpty(), "OFF: no request");
        on();
        Object screen = lastPopup();
        require(tick(10).equals(Arrays.asList(REQUEST)), "ON: the first tick sends the empty opcode-59 request");
        require(tick(300).isEmpty() && tick(600).isEmpty(), "No second request inside one second");
        require(tick(100).equals(Arrays.asList(REQUEST)), "Next request after 1000ms");
        require(lastPopup() == screen && dialogShown() == null, "Polling opens no screen and no dialog");
        // A value from before the watch started is not trusted: the field keeps it across logins.
        field(cls(n("lliiil")), "e").setInt(null, 100);
        bag(item(700, 4, "Cơm đùi gà"));
        require(!tick(1000).contains(use(700)), "A stale low value without a fresh reply does not eat");
        reply(20843, 0);
        require(!tick(1000).contains(use(700)), "Fresh reply 20843: no meal");
    }

    private static void verifyRecovery() throws Exception {
        bag(item(5, 4, "Bình máu"), item(701, 4, "Cơm đùi gà"));
        reply(499, 0);
        List<String> sent = tick(10);
        require(sent.equals(Arrays.asList(use(701), REQUEST)), "Fresh reply 499: use Cơm đùi gà (op 32) then ask again: " + sent);
        serverDialog("Sức khỏe đã được phục hồi");
        require(dialogShown() == null, "'Sức khỏe đã được phục hồi' is closed before it opens");
        require(tick(300).isEmpty(), "No second meal while waiting");
        reply(30499, 0);
        require(tick(10).isEmpty() && field(port, "stage").getInt(null) == 0 && enabled(), "Reply 30499 after the meal: recovered, auto stays ON");
        require(field(port, "uses").getInt(null) == 1, "One meal");
        reply(28000, 0);
        require(!tick(1000).contains(use(701)), "Above 500 again: no meal");

        bag(item(702, 4, "Cơm gà"));
        reply(10, 0);
        require(tick(10).contains(use(702)), "'Cơm gà' also matches");
        reply(30010, 0);
        tick(1000);
        tick(5000);
        serverDialog("Thông báo khác");
        require(dialogShown() != null, "Outside the 5s after a meal, dialogs open normally");
        clearDialog();
    }

    private static void verifyDailyLimit() throws Exception {
        bag(item(703, 4, "Cơm đùi gà"));
        reply(300, 0);
        require(tick(1000).contains(use(703)), "Reply 300: eat");
        serverDialog("Một ngày chỉ có thể ăn được 1 vật phẩm sức khỏe");
        require(dialogShown() == null, "The daily-limit dialog is closed too");
        reply(300, 0);
        tick(500);
        require(enabled(), "One low reply is not enough to stop");
        reply(300, 0);
        tick(500);
        require(enabled(), "Two low replies inside 2s still wait");
        tick(1100);
        String text = (String)getStatic(port, "popupText");
        require(!enabled() && text != null && text.startsWith("Đã ăn Cơm đùi gà nhưng thể lực vẫn dưới 500")
                && text.contains("Một ngày chỉ có thể ăn được 1 vật phẩm sức khỏe") && dialogShown() != null,
                "Still below 500: OFF with an Ok dialog that names the server reason: " + text);
        clearDialog();
        drain();
        reply(100, 0);
        require(tick(2000).isEmpty(), "OFF: no request and no meal");
    }

    private static void verifyGuards() throws Exception {
        // No food in the bag.
        bag(item(5, 4, "Bình máu"));
        on();
        tick(1000);
        reply(50, 0);
        tick(1000);
        require(!enabled() && "Không còn Cơm gà trong hành trang! Đã tắt Auto phục hồi thể lực".equals(getStatic(port, "popupText"))
                && dialogShown() != null, "No Cơm gà: OFF with an Ok dialog");
        clearDialog();

        // Dead: wait.
        bag(item(704, 4, "Cơm đùi gà"));
        on();
        tick(1000);
        int state = field(entityType, "cG").getInt(player);
        setNumber(player, entityType, "cG", 4);
        reply(50, 0);
        require(!tick(1000).contains(use(704)), "Dead: no meal");
        setNumber(player, entityType, "cG", state);
        reply(50, 0);
        require(tick(1000).contains(use(704)), "Alive again: eat");

        // Connection notices are never swallowed.
        serverDialog("Mất kết nối với máy chủ");
        require(dialogShown() != null, "A connection notice right after a meal still opens");
        clearDialog();

        // No reply after the meal: stop at 10s.
        for (int i = 0; i < 10; i++) tick(1000);
        require(!enabled() && ((String)getStatic(port, "popupText")).startsWith("Server không trả lời thể lực"),
                "No reply for 10s after the meal: OFF");
        clearDialog();
        drain();
    }
}
