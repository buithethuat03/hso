import java.io.File;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

/**
 * Exercises HungBySonTradeLocal inside the patched Hung_By_Son.jar using the real game classes.
 * Objects are allocated with Unsafe (no game constructors run); static state is set by reflection.
 * Formulas are read through the patched getter the V3 executor uses (~lilllill.~lili).
 */
public class TradeLocalTest {
    static ClassLoader L;
    static sun.misc.Unsafe U;
    static int failures;

    static String n(String s) { StringBuilder b = new StringBuilder(); for (int i = s.length(); i < 100; i++) b.append('i'); return b.append(s).toString(); }
    static Class<?> c(String s) throws Exception { return Class.forName(s.startsWith("~") ? n(s.substring(1)) : s, false, L); }
    static Object alloc(String cls) throws Exception { return U.allocateInstance(c(cls)); }
    static Field f(Class<?> k, String name) throws Exception {
        for (Class<?> x = k; x != null; x = x.getSuperclass()) {
            try { Field fl = x.getDeclaredField(name.startsWith("~") ? n(name.substring(1)) : name); fl.setAccessible(true); return fl; } catch (NoSuchFieldException e) { }
        }
        throw new NoSuchFieldException(name);
    }
    static void set(Object o, String cls, String field, Object v) throws Exception { f(c(cls), field).set(o, v); }
    static Object get(Object o, String cls, String field) throws Exception { return f(c(cls), field).get(o); }
    static Object vec(Object... items) throws Exception {
        Object v = alloc("et");
        // et wraps a java.util.Vector-like store; find its add method ~l(Object)V
        Method add = c("et").getMethod(n("l"), Object.class);
        Field store = null;
        for (Field fl : c("et").getDeclaredFields()) if (!Modifier.isStatic(fl.getModifiers()) && fl.getType() == Vector.class) store = fl;
        if (store != null) { store.setAccessible(true); store.set(v, new Vector()); }
        for (Object i : items) add.invoke(v, i);
        return v;
    }
    static Object item(int id, int category, String name) throws Exception {
        Object it = alloc("~llilli");
        set(it, "~llilli", "O", id);
        set(it, "~llilli", "~liiill", category);
        set(it, "~llilli", "~liiiiliii", name);
        return it;
    }
    static void expect(String what, Object got, Object want) {
        boolean ok = String.valueOf(got).equals(String.valueOf(want));
        System.out.println((ok ? "PASS " : "FAIL ") + what + " got=" + got + " want=" + want);
        if (!ok) failures++;
    }

    public static void main(String[] a) throws Exception {
        L = new URLClassLoader(new URL[]{new File(a[0]).toURI().toURL(), new File(a[1]).toURI().toURL()}, null);
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true); U = (sun.misc.Unsafe) uf.get(null);

        Class<?> secrets = c("~lilllill");
        Method getter = secrets.getMethod(n("lili"), String.class, int.class);
        Method verified = secrets.getMethod(n("liii"), String.class, int.class);
        Class<?> local = Class.forName("HungBySonTradeLocal", true, L);

        // world: player at (300,420), one NPC (cu=-57) at (320,430), another far away
        Object player = alloc("~lliiil");
        set(player, "~liiiliii", "aZ", 300); set(player, "~liiiliii", "ba", 420);
        Object npc = alloc("~liiiliii");
        set(npc, "~liiiliii", "aZ", 320); set(npc, "~liiiliii", "ba", 430); set(npc, "~liiiliii", "cv", (byte) 2); set(npc, "~liiiliii", "cu", -57);
        Object far = alloc("~liiiliii");
        set(far, "~liiiliii", "aZ", 900); set(far, "~liiiliii", "ba", 900); set(far, "~liiiliii", "cv", (byte) 2); set(far, "~liiiliii", "cu", -58);
        f(c("~liiliii"), "~llilliill").set(null, player);
        f(c("~liiliii"), "~llillilll").set(null, vec(far, npc));
        Object map = alloc("cs"); set(map, "cs", "d", 52);
        f(c("~liilllii"), "~liiililiiii").set(null, map);

        // bag: a "Lụa" (silk, category 4) must NOT match Lừa? -> it normalises to "lua" too; see report.
        Object bag = vec(item(900, 4, "Tơ lụa"), item(501, 3, "Bò vàng"), item(777, 4, "Bò vàng"), item(778, 4, "Lạc đà"), item(12, 4, "Kẹo sữa"));
        f(c("~llilli"), "~liiillili").set(null, bag);

        expect("verified(mua_gem)", verified.invoke(null, "buon.mua_gem", 0), true);
        expect("verified(other key)", verified.invoke(null, "buon.xyz", 0), false);
        expect("thu_len Bo", getter.invoke(null, "buon_tay.thu_len", 2), "ep|4|s300;s420&&ep|32|s777");
        expect("thu_xuong Bo", getter.invoke(null, "buon_tay.thu_xuong", 2), "ep|4|s300;s420&&ep|32|s777");
        expect("thu_len Lac Da", getter.invoke(null, "buon_tay.thu_len", 1), "ep|4|s300;s420&&ep|32|s778");
        expect("thu_len Lua (missing)", getter.invoke(null, "buon_tay.thu_len", 0), "local");
        expect("ban_gem -> V3 menu fallback", getter.invoke(null, "buon.ban_gem", 0), "local");

        // revive: depends on ticket check; just ensure a formula is produced
        Object rv = getter.invoke(null, "buon.hoi_sinh", 0);
        expect("hoi_sinh formula", "qn|-51|0|0".equals(rv) || "ep|31|b0".equals(rv), true);

        // buying: first call opens the nearest NPC shop (sends op23/op5 via the sender; network absent -> may throw inside, caught)
        Object first = getter.invoke(null, "buon.mua_gem", 0);
        expect("mua_gem before shop known", first, "local");
        expect("opening flag", f(local, "opening").get(null), true);
        expect("opening npc", f(local, "openNpc").get(null), -57);

        // menu box shows the NPC menu: rows "Nói chuyện", "Mua bán"
        Object box = alloc("~liilliil");
        set(box, "~liilliil", "~liiillli", true); set(box, "~liilliil", "C", -57);
        f(c("~liilllii"), "~liiiliillll").set(null, box);
        Object r0 = alloc("bt"); set(r0, "bt", "~lliiilli", "Nói chuyện");
        Object r1 = alloc("bt"); set(r1, "bt", "~lliiilli", "Mua bán");
        Method onMenu = local.getMethod("onMenu", c("et"), String.class);
        onMenu.invoke(null, vec(r0, r1), "Mr Dylan");
        expect("menu row remembered", f(local, "menuRow").get(null), 1);

        // shop constructor hook with gem goods (shop items are ~lililiii extends ~llilli)
        Object g1 = alloc("~lililiii"); set(g1, "~llilli", "O", 3592); set(g1, "~llilli", "~liiiiliii", "Kim cương");
        Object g2 = alloc("~lililiii"); set(g2, "~llilli", "O", 3593); set(g2, "~llilli", "~liiiiliii", "Ruby");
        Object g3 = alloc("~lililiii"); set(g3, "~llilli", "O", 3594); set(g3, "~llilli", "~liiiiliii", "Saphia");
        Method onShop = local.getMethod("onShop", Object.class, c("et"), int.class);
        Object shopObj = new Object();
        onShop.invoke(null, shopObj, bag, 7);            // a bag vector must be ignored
        expect("bag vector ignored", f(local, "opening").get(null), true);
        onShop.invoke(null, shopObj, vec(g1, g2, g3), 5);
        expect("shop captured", f(local, "opening").get(null), false);

        expect("mua_gem KC", getter.invoke(null, "buon.mua_gem", 0), "ep|24|b5;s3592;s1");
        expect("mua_gem too fast", getter.invoke(null, "buon.mua_gem", 1), "local");
        f(local, "lastBuyAt").set(null, 0L);
        expect("mua_gem Ruby", getter.invoke(null, "buon.mua_gem", 1), "ep|24|b5;s3593;s1");
        f(local, "lastBuyAt").set(null, 0L);
        expect("mua_gem Saphia", getter.invoke(null, "buon.mua_gem", 2), "ep|24|b5;s3594;s1");

        // other map: shop must be re-read
        set(map, "cs", "d", 19);
        f(local, "lastBuyAt").set(null, 0L);
        expect("mua_gem new map not known", getter.invoke(null, "buon.mua_gem", 0), "local");

        // non-trade keys are left to the original getter (null from formula())
        Method formula = local.getMethod("formula", String.class, int.class);
        expect("unrelated key untouched", formula.invoke(null, "other.key", 0), null);

        // Run formulas through V3's real executor (~lilllill.~(String,I,[I)Z); the game sender logs each packet.
        set(map, "cs", "d", 52);
        Method exec = secrets.getMethod(n(""), String.class, int.class, int[].class);
        System.out.println("-- exec thu_len Bo");
        expect("exec thu_len", exec.invoke(null, "buon_tay.thu_len", 2, null), true);
        f(local, "lastBuyAt").set(null, 0L);
        System.out.println("-- exec mua_gem Saphia");
        expect("exec mua_gem", exec.invoke(null, "buon.mua_gem", 2, null), true);
        System.out.println("-- exec hoi_sinh");
        expect("exec hoi_sinh", exec.invoke(null, "buon.hoi_sinh", 0, null), true);
        System.out.println("-- exec ban_gem (must send nothing, return false)");
        expect("exec ban_gem", exec.invoke(null, "buon.ban_gem", 0, null), false);
        Method err = secrets.getMethod(n("lil"), String.class, int.class);
        expect("ban_gem error text for V3 fallback", err.invoke(null, "buon.ban_gem", 0), "Khong thi hanh duoc cong thuc");
        System.out.println(failures == 0 ? "ALL PASS" : "FAILURES=" + failures);
        System.exit(failures);
    }
}
