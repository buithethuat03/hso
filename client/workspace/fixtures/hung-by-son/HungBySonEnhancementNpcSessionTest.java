import java.io.*;
import java.lang.reflect.*;

/** Validates native NPC-session setup, menu selection, fresh acknowledgement and server rejection. */
public final class HungBySonEnhancementNpcSessionTest extends HungBySonTestState {
    public static void main(String[] args) throws Exception {
        initialize(args[0]);
        Object player = alloc("~lliiil"); set(player, "~liiiliii", "aZ", 432); set(player, "~liiiliii", "ba", 528);
        set(null, "~liiliii", "~llilliill", player); Object map = alloc("cs"); set(map, "cs", "d", 1); set(null, "~liilllii", "~liiililiiii", map);
        Object npc = alloc("~liiiliii"); set(npc, "~liiiliii", "aZ", 432); set(npc, "~liiiliii", "ba", 528);
        set(npc, "~liiiliii", "cv", (byte)2); set(npc, "~liiiliii", "cu", -57);
        set(null, "~liiliii", "~llillilll", vector(npc));
        Object weapon = item(100, 3, "Kiếm", 0, 0); set(null, "~llilli", "~liiillili", vector(weapon));
        set(null, "~lliillii", "~liilliliili", true); set(null, "~lliillii", "~lillliiliil", 10); set(null, "~lliillii", "~lillliilili", 0);
        Method action = type("~lliillii").getDeclaredMethod(n("l"), String.class, int.class, int[].class); action.setAccessible(true);
        expect("no equipment request before NPC session", action.invoke(null, "ch.chon_do", 0, new int[]{100}), false);
        expect("position synchronized first", packets.get(0), "4:[1, -80, 2, 16]");
        expect("native NPC menu requested", packets.get(1), "23:[-57]");
        expect("native NPC data requested", packets.get(2), "5:[-1, -57]");
        for (int i = 0; i < 30; i++) action.invoke(null, "ch.chon_do", 0, new int[]{100});
        expect("session wait does not flood NPC requests", packets.size(), 3);

        Object box = alloc("~liilliil"); set(box, "~liilliil", "C", -57); set(box, "~liilliil", "B", 2); set(box, "~liilliil", "~liiillli", true);
        set(null, "~liilllii", "~liiiliillll", box);
        Object talk = alloc("bt"), pet = alloc("bt"), enhance = alloc("bt");
        set(talk, "bt", "~lliiilli", "Nói chuyện"); set(pet, "bt", "~lliiilli", "Cường hóa thú cưỡi"); set(enhance, "bt", "~lliiilli", "Cường hóa trang bị");
        Object menu = vector(talk, pet, enhance);
        Method dispatch = type("~lillllll").getDeclaredMethod(n("l"), type("et"), String.class); dispatch.setAccessible(true); dispatch.invoke(null, menu, "Pháp sư");
        expect("real menu hook finds equipment row", get(null, "HungBySonOfflineLocal", "npcMenuRow"), 2);
        set(null, "HungBySonOfflineLocal", "npcMenuAt", System.currentTimeMillis() - 1501L);
        action.invoke(null, "ch.chon_do", 0, new int[]{100});
        expect("native row dispatcher uses actual NPC/menu IDs", packets.get(3), "-30:[-1, -57, 2, 2]");
        expect("still no gear request before native screen", packets.size(), 4);

        Object tab = alloc("~lllili"), tabs = alloc("~liiiiili"); set(tabs, "~liiiiili", "a", 0); set(tabs, "~liiiiili", "b", vector(tab));
        set(null, "~liilllii", "~liiiillllli", tabs);
        expect("native enhancement screen confirms session", action.invoke(null, "ch.chon_do", 0, new int[]{100}), true);
        expect("gear selected only after session", packets.get(4), "67:[0, 0, 100, 3]");
        set(null, "HungBySonOfflineLocal", "lastActionAt", System.currentTimeMillis() - 501L);
        expect("no enhancement without gear confirmation", action.invoke(null, "ch.nang", 0, null), false);
        Object selected = alloc("~lililiii"); set(selected, "~llilli", "O", 100); set(selected, "~llilli", "~liiill", 3); set(null, "~lllili", "~liillllli", selected);
        expect("gold enhancement after confirmed session and item", action.invoke(null, "ch.nang", 0, null), true);
        expect("native gold bytes", packets.get(5), "67:[2, 0, 0, 0]");

        ByteArrayOutputStream data = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(data);
        out.writeByte(3); out.writeUTF("Không đủ đá cường hóa"); out.close();
        Object reply = type("~lllllil").getConstructor(byte.class, byte[].class).newInstance((byte)67, data.toByteArray());
        Method receive = type("~lllllll").getDeclaredMethod(n("liiiili"), type("~lllllil")); receive.setAccessible(true); receive.invoke(null, reply);
        expect("real rejection stops instead of timing out", get(null, "~lliillii", "~liilliliili"), false);
        expect("rejection sends no further request", packets.size(), 6);

        set(null, "~lliillii", "~liilliliili", true); set(null, "HungBySonOfflineLocal", "waiting", true);
        data = new ByteArrayOutputStream(); out = new DataOutputStream(data);
        out.writeUTF("Thông báo"); out.writeUTF("Không đủ vàng"); out.writeByte(0); out.close();
        reply = type("~lllllil").getConstructor(byte.class, byte[].class).newInstance((byte)37, data.toByteArray());
        Method notice = type("~lllllll").getDeclaredMethod(n("llilli"), type("~lllllil")); notice.setAccessible(true); notice.invoke(null, reply);
        expect("game notice body retained while waiting", get(null, "HungBySonOfflineLocal", "lastGameNotice"), "Không đủ vàng");
        expect("generic notice alone is not a forged result", get(null, "HungBySonOfflineLocal", "waiting"), true);
        System.out.println("NPC SESSION ALL PASS checks=" + HungBySonFullOfflineHostTest.checks);
    }
}
