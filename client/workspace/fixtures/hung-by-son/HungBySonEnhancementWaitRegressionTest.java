import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Reproduces the old empty-error stop through the real auto-controller update loop. */
public final class HungBySonEnhancementWaitRegressionTest extends HungBySonTestState {
    public static void main(String[] args) throws Exception {
        initialize(args[0]);
        Object player = alloc("~lliiil"); set(player, "~liiiliii", "aZ", 432); set(player, "~liiiliii", "ba", 528);
        set(null, "~liiliii", "~llilliill", player); Object map = alloc("cs"); set(map, "cs", "d", 1); set(null, "~liilllii", "~liiililiiii", map);
        Object weapon = item(100, 3, "Kiếm", 8, 0), clover = item(772, 7, "Cỏ 4 lá", 0, 11);
        set(null, "~llilli", "~liiillili", vector(weapon, clover));
        Vector<Integer> queue = new Vector<Integer>(); queue.add(0);
        set(null, "~lliillii", "~lillliilill", queue); set(null, "~lliillii", "~liilliliili", true);
        set(null, "~lliillii", "~lillliiliil", 15); set(null, "~lliillii", "~lillliilili", 3);
        set(null, "~lliillii", "~lillliiliii", 2); set(null, "~lliillii", "~liliiiilill", 0);
        set(null, "~lllili", "~liiiliiii", (byte)0); set(null, "~lllili", "~liillllli", null);
        set(null, "HungBySonOfflineLocal", "selectedId", 100); set(null, "HungBySonOfflineLocal", "waiting", false);
        Object controller = alloc("~lliillii"); Method update = type("~lliillii").getMethod("update");
        if (!(args.length > 1 && "--original".equals(args[1]))) set(null, "HungBySonOfflineLocal", "npcReady", true);
        for (int i = 0; i < 1800; i++) update.invoke(controller);
        if (args.length > 1 && "--original".equals(args[1])) {
            expect("original incorrectly stops while selection is pending", get(null, "~lliillii", "~liilliliili"), false);
            expect("original leaves empty error reason", get(null, "~lilllill", "~liilllllili"), "");
            System.out.println("OLD FAILURE REPRODUCED"); return;
        }
        expect("controller waiting does not exhaust MOD-error counter", get(null, "~lliillii", "~liilliliili"), true);
        expect("selection wait has reason", get(null, "~lilllill", "~liilllllili"), "server game chưa xác nhận chọn trang bị");
        expect("no packet while equipment is unconfirmed", packets.size(), 0);
        set(null, "HungBySonOfflineLocal", "waitAt", System.currentTimeMillis() - 12001L);
        set(null, "~lliillii", "~liliiiilill", 0); update.invoke(controller);
        expect("selection timeout stops safely", get(null, "~lliillii", "~liilliliili"), false);

        // A complete controller cycle: choose item -> choose clover -> enhance -> real result -> next item selection.
        set(null, "~lliillii", "~liilliliili", true); set(null, "~lliillii", "~lillliiliii", 1);
        set(null, "~lliillii", "~liliiiilill", 0); set(null, "~lllili", "~liliiliil", false);
        type("HungBySonOfflineLocal").getMethod("reset").invoke(null);
        set(null, "HungBySonOfflineLocal", "npcReady", true); // NPC-session fixture covers actual setup separately
        update.invoke(controller); expect("controller selects item", packets.size(), 1);
        Object selected = alloc("~lililiii"); set(selected, "~llilli", "O", 100); set(selected, "~llilli", "~liiill", 3);
        set(null, "~lllili", "~liillllli", selected);
        ready(); update.invoke(controller); expect("controller selects four-leaf clover", packets.size(), 2);
        expect("clover request bytes", packets.get(1), "67:[0, 3, 4, 7]");
        set(null, "~lllili", "~liliiliil", true); ready(); update.invoke(controller);
        expect("controller enhances", packets.get(2), "67:[2, 0, 0, 0]");
        ByteArrayOutputStream data = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(data);
        out.writeByte(2); out.writeByte(3); out.writeUTF("Nâng cấp thành công"); out.close();
        Object response = type("~lllllil").getConstructor(byte.class, byte[].class).newInstance((byte)67, data.toByteArray());
        Method receive = type("~lllllll").getDeclaredMethod(n("liiiili"), type("~lllllil")); receive.setAccessible(true); receive.invoke(null, response);
        set(weapon, "~llilli", "~llilil", (byte)9); ready();
        set(null, "HungBySonOfflineLocal", "resultAt", System.currentTimeMillis() - 501L);
        update.invoke(controller);
        expect("background result unlocks native animation", get(null, "~lllili", "~liiiliiii"), (byte)0);
        expect("controller proceeds after first successful enhancement", packets.get(3), "67:[0, 0, 100, 3]");
        expect("auto remains enabled for next attempt", get(null, "~lliillii", "~liilliliili"), true);

        Object tab = alloc("~lllili"), tabScreen = alloc("~liiiiili"); set(tabScreen, "~liiiiili", "b", vector(tab));
        set(tabScreen, "~liiiiili", "a", 0); set(null, "~liilllii", "~liiiillllli", tabScreen);
        set(null, "~lllili", "~liiiliiii", (byte)1); set(null, "HungBySonOfflineLocal", "resultAt", System.currentTimeMillis() - 501L);
        ready(); int before = packets.size(); update.invoke(controller);
        expect("visible native enhancement keeps animation", get(null, "~lllili", "~liiiliiii"), (byte)1);
        expect("visible animation sends no request", packets.size(), before);
        System.out.println("WAIT REGRESSION ALL PASS checks=" + HungBySonFullOfflineHostTest.checks);
    }
    static void ready() throws Exception {
        set(null, "~lliillii", "~liliiiilill", 0);
        set(null, "HungBySonOfflineLocal", "lastActionAt", System.currentTimeMillis() - 501L);
    }
}

/** Shared utility adapter, using the same in-memory packet sink as the full offline fixture. */
class HungBySonTestState {
    static java.util.List<String> packets = HungBySonFullOfflineHostTest.packets;
    static void initialize(String jar) throws Exception {
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
        HungBySonFullOfflineHostTest.unsafe = (sun.misc.Unsafe)uf.get(null);
        HungBySonFullOfflineHostTest.loader = new HungBySonFullOfflineHostTest.SinkLoader(new File(jar));
        Class.forName(HungBySonFullOfflineHostTest.n("lliiliil"), true, HungBySonFullOfflineHostTest.loader);
    }
    static String n(String name) { return HungBySonFullOfflineHostTest.n(name); }
    static Class<?> type(String name) throws Exception { return HungBySonFullOfflineHostTest.type(name); }
    static Object alloc(String name) throws Exception { return HungBySonFullOfflineHostTest.alloc(name); }
    static void set(Object object, String owner, String name, Object value) throws Exception { HungBySonFullOfflineHostTest.set(object, owner, name, value); }
    static Object get(Object object, String owner, String name) throws Exception { return HungBySonFullOfflineHostTest.get(object, owner, name); }
    static Object item(int id, int category, String name, int level, int subtype) throws Exception { return HungBySonFullOfflineHostTest.item(id, category, name, level, subtype); }
    static Object vector(Object... items) throws Exception { return HungBySonFullOfflineHostTest.vector(items); }
    static void expect(String name, Object actual, Object expected) { HungBySonFullOfflineHostTest.expect(name, actual, expected); }
}
