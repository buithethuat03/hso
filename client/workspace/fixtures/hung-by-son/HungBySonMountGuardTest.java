import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Auto Thú cưỡi used the chosen horse (request 32) every 900 game ticks while Farm ran, even when
 * already riding, so players lost every horse in the bag. The candidate may use one only while the
 * native mount byte is -1, waits for the server's mount state, and turns itself off after two
 * unconfirmed uses.
 *
 * The real module update(), item list, local player and request serializer run from each JAR; only
 * the native network queue is captured (HungBySonAutoAttackBinaryTest probe loader).
 *
 * Usage: <input.jar> <candidate.jar>
 */
public final class HungBySonMountGuardTest {
    private static final String HELPER = "HungBySonAutoAttackBinaryTest";
    private static final int PERIOD = 900;
    private static final int HORSE_ID = 501;
    private static int checks;

    private HungBySonMountGuardTest() { }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }

    private static String encoded(String suffix) {
        StringBuilder value = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) value.append('i');
        return value.append(suffix).toString();
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 2, "<input.jar> <candidate.jar>");
        Probe input = new Probe(new File(args[0]), false);
        Probe candidate = new Probe(new File(args[1]), true);
        try {
            input.reset(0);
            require(input.periods(5) == 5, "Input reproduces the drain: a mounted rider uses a horse every 900 ticks");
            candidate.reset(0);
            require(candidate.periods(5) == 0, "Candidate never uses a horse while mounted");

            candidate.reset(-1);
            require(candidate.periods(1) == 1 && candidate.lastHorse(), "Unmounted with Farm on: one use of the chosen horse");
            candidate.ef(0);
            require(candidate.periods(4) == 0, "Server mount state confirms the ride; no further use");
            candidate.ef(-1);
            require(candidate.periods(1) == 1, "After a dismount or death the next period rides again");
            candidate.ef(0);
            candidate.periods(1);

            candidate.reset(-1);
            require(candidate.periods(1) == 1, "Unconfirmed: first use");
            require(candidate.periods(1) == 0, "Unconfirmed: waits for the server instead of spending another horse");
            candidate.age();
            require(candidate.periods(1) == 1, "Unconfirmed after 15s: one retry");
            candidate.age();
            require(candidate.periods(1) == 0 && !candidate.enabled(), "Two unconfirmed uses switch Auto Thu cuoi off");
            require(candidate.periods(3) == 0, "Switched off stays silent");
            candidate.enable();
            require(candidate.periods(1) == 1, "Turning it back on starts a fresh budget");

            for (Probe p : new Probe[]{input, candidate}) {
                p.reset(-1); p.farm(false);
                require(p.periods(2) == 0, (p.candidate ? "Candidate" : "Input") + ": Farm off, no use (native gate kept)");
                p.reset(-1); p.items(false);
                require(p.periods(2) == 0, (p.candidate ? "Candidate" : "Input") + ": chosen horse absent, no use");
                p.reset(-1); p.dead();
                require(p.periods(2) == 0, (p.candidate ? "Candidate" : "Input") + ": dead player, no use");
            }
        } finally {
            input.close();
            candidate.close();
        }
        System.out.println("HungBySonMountGuardTest PASS checks=" + checks + "; ride only while unmounted, confirm, bounded retries");
    }

    private static final class Probe {
        final boolean candidate;
        final ClassLoader loader;
        final Object module, player;
        final Method update;
        final Field enabled, counter, selected, names, farm, ef, state, items, sentAt;
        final Class<?> itemType, listType;
        final List<String> packets;

        @SuppressWarnings("unchecked")
        Probe(File jar, boolean candidate) throws Exception {
            this.candidate = candidate;
            Class<?> helper = Class.forName(HELPER);
            Properties inert = new Properties();
            for (String key : new String[]{"native.menu.display.owner", "native.menu.display.method",
                    "native.menu.callback.owner", "native.hpmp.owner", "native.hpmp.method"}) inert.setProperty(key, "unused");
            inert.setProperty("native.menu.display.descriptor", "()V");
            inert.setProperty("native.menu.callback.descriptor", "()V");
            inert.setProperty("native.hpmp.descriptor", "()V");
            Constructor<?> probe = null;
            for (Class<?> nested : helper.getDeclaredClasses())
                if (nested.getSimpleName().equals("NativeProbeLoader")) probe = nested.getDeclaredConstructor(File.class, Properties.class);
            if (probe == null) throw new NoSuchMethodException("NativeProbeLoader(File, Properties)");
            probe.setAccessible(true);
            loader = (ClassLoader)probe.newInstance(jar, inert);
            Field captured = helper.getDeclaredField("nativePackets");
            captured.setAccessible(true);
            packets = (List<String>)captured.get(null);

            Class<?> mountType = loader.loadClass(encoded("lliiilil"));
            Constructor<?> ctor = mountType.getDeclaredConstructor();
            ctor.setAccessible(true);
            module = ctor.newInstance();
            update = mountType.getMethod("update");
            enabled = field(mountType, encoded("lililllilll"));
            counter = field(mountType, encoded("lililllliil"));
            selected = field(mountType, encoded("lilillllili"));
            names = field(mountType, encoded("lilillllill"));
            farm = field(loader.loadClass(encoded("lliiiiil")), encoded("lililiilill"));
            Class<?> world = loader.loadClass(encoded("liiliii"));
            player = allocate(loader.loadClass(encoded("lliiil")));
            field(world, encoded("llilliill")).set(null, player);
            ef = field(player.getClass(), "ef");
            state = field(player.getClass(), "cG");
            itemType = loader.loadClass(encoded("llilli"));
            listType = loader.loadClass("et");
            items = field(itemType, encoded("liiillili"));
            sentAt = candidate ? field(mountType, "codexMountSentAt") : null;
        }

        void reset(int mount) throws Exception {
            String horse = ((String[])names.get(null))[0];
            selected.set(null, horse);
            enabled.setBoolean(null, true);
            counter.setInt(null, 0);
            farm.setBoolean(null, true);
            state.setInt(player, 0);
            ef.setByte(player, (byte)mount);
            if (candidate) {
                sentAt.setLong(null, 0L);
                field(module.getClass(), "codexMountUnconfirmed").setInt(null, 0);
            }
            items(true);
        }

        void items(boolean withHorse) throws Exception {
            Object list = listType.getDeclaredConstructor().newInstance();
            Method add = listType.getMethod(encoded("l"), Object.class);
            String horse = (String)selected.get(null);
            Object potion = allocate(itemType);
            field(itemType, encoded("liiiiliii")).set(potion, "Bình máu");
            field(itemType, "O").setInt(potion, 77);
            add.invoke(list, potion);
            if (withHorse) {
                Object item = allocate(itemType);
                field(itemType, encoded("liiiiliii")).set(item, horse + " (7 ngày)");
                field(itemType, "O").setInt(item, HORSE_ID);
                add.invoke(list, item);
            }
            items.set(null, list);
        }

        /** Runs n full 900-tick periods and returns the number of item-use requests sent. */
        int periods(int n) throws Exception {
            synchronized (packets) { packets.clear(); }
            for (int i = 0; i < n * PERIOD; i++) update.invoke(module);
            int uses = 0;
            synchronized (packets) { for (String p : packets) if (p.startsWith("32:")) uses++; }
            return uses;
        }

        boolean lastHorse() {
            synchronized (packets) {
                for (int i = packets.size() - 1; i >= 0; i--)
                    if (packets.get(i).startsWith("32:")) return packets.get(i).equals("32:[1, -11]");
            }
            return false;
        }

        void ef(int mount) throws Exception { ef.setByte(player, (byte)mount); }
        void age() throws Exception { sentAt.setLong(null, System.currentTimeMillis() - 16000L); }
        void farm(boolean on) throws Exception { farm.setBoolean(null, on); }
        void dead() throws Exception { state.setInt(player, 4); }
        boolean enabled() throws Exception { return enabled.getBoolean(null); }
        void enable() throws Exception { enabled.setBoolean(null, true); counter.setInt(null, 0); }
        void close() { try { ((URLClassLoader)loader).close(); } catch (Exception ignored) { } }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException next) { }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static Object allocate(Class<?> type) throws Exception {
        Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        return unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }
}
