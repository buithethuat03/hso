import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Crowded monster-cache recovery checks against all three packed X3 namespaces. */
public final class HungBySonCrowdedCacheX3HostTest {
    private static final String[] PREFIXES = {"", "b.", "c."};
    private static int checks;

    private HungBySonCrowdedCacheX3HostTest() { }

    public static void main(String[] args) throws Exception {
        require(args.length == 1, "<candidate-x3.jar>");
        File candidate = new File(args[0]);
        require(candidate.isFile(), "candidate JAR exists");
        System.out.println("candidate SHA-256=" + sha256(candidate));

        try (URLClassLoader loader = new X3Loader(candidate)) {
            Slot[] slots = new Slot[3];
            String[] finalStatuses = new String[3];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = new Slot(loader, i);
                checkOwnedPlaceholderRemainsHeld(slots[i]);
                checkBorrowedUnsentDetailReleases(slots[i]);
                finalStatuses[i] = checkCrowdedSlot(slots[i]);
            }

            for (int i = 0; i < slots.length; i++) {
                String status = slots[i].status();
                require(status.equals(finalStatuses[i]),
                        "slot " + (i + 1) + " retains its own current context and P/V counters");
                require(statusNumber(status, "C:") == 55
                        && statusNumber(status, "K:") == 0
                        && statusNumber(status, "W:") == 0
                        && statusNumber(status, "P:") == i + 1
                        && statusNumber(status, "V:") == i + 1,
                        "slot " + (i + 1) + " keeps its distinct 55-record recovery state: " + status);
            }
        }
        System.out.println("HungBySonCrowdedCacheX3HostTest PASS (" + checks
                + " checks; all three slot namespaces in one classloader)");
    }

    private static String checkCrowdedSlot(Slot slot) throws Exception {
        HostAccess access = slot.newAccess(new Store(), "crowded-x3", 214, 4);
        slot.onMap(access.proxy);

        final int count = 55;
        for (int i = 0; i < count; i++) {
            int id = 1000 + i;
            int template = 200 + i;
            int level = 20 + (i % 30);
            int x = 100 + i * 3;
            int y = 300 + i * 2;
            Monster monster = new Monster(id, template, x, y, false);
            monster.hp = 60;
            monster.maxHp = 100;
            access.monsters.put(Integer.valueOf(id), monster);
            slot.onObject(access.proxy, template, id, x, y);
            slot.onDetail(access.proxy, id, level, x, y, 60, 100);
        }

        String learned = slot.status();
        require(statusNumber(learned, "M:") == 214 && statusNumber(learned, "C:") == count,
                "slot " + (slot.index + 1) + " learns 55 records in the active map context: " + learned);
        int[] remembered = slot.remembered("crowded-x3", 214);
        require(remembered.length == count * 2,
                "slot " + (slot.index + 1) + " remembers all 55 template/level pairs");

        int corpseCount = slot.index + 1;
        Monster[] corpses = new Monster[corpseCount];
        int[] xs = new int[corpseCount];
        int[] ys = new int[corpseCount];
        for (int i = 0; i < corpseCount; i++) {
            int id = 1000 + i;
            corpses[i] = access.monsters.get(Integer.valueOf(id));
            xs[i] = corpses[i].x;
            ys[i] = corpses[i].y;
            slot.nativeDetail(access, corpses[i], 20 + i, 0, 100);
        }

        for (int i = 0; i < corpseCount; i++) {
            access.now = 3000L + i * 100L;
            slot.tick(access.proxy);
        }
        String queued = slot.status();
        require(statusNumber(queued, "K:") == corpseCount
                && statusNumber(queued, "W:") == corpseCount,
                "slot " + (slot.index + 1) + " queues each held corpse for recovery: " + queued);

        for (int i = 0; i < corpseCount; i++) {
            access.now += 500L;
            slot.onCombatSnapshot(access.proxy, combat(
                    new int[]{corpses[i].id}, new int[]{37}));
            Monster corpse = corpses[i];
            require(corpse.hp == 37 && !corpse.dead && !corpse.hidden && !corpse.pending,
                    "slot " + (slot.index + 1) + " positive combat HP releases corpse " + i);
            require(corpse.x == xs[i] && corpse.y == ys[i],
                    "slot " + (slot.index + 1) + " revival preserves corpse coordinates " + i);
            require(corpse.deathState == 0 && corpse.removalCount == 0,
                    "slot " + (slot.index + 1) + " native HP boundary clears removal state " + i);
        }

        String status = slot.status();
        require(statusNumber(status, "K:") == 0 && statusNumber(status, "W:") == 0,
                "slot " + (slot.index + 1) + " positive combat clears K/W: " + status);
        require(statusNumber(status, "P:") == corpseCount
                && statusNumber(status, "V:") == corpseCount,
                "slot " + (slot.index + 1) + " counts its own combat and revivals: " + status);
        require(access.requests.size() <= corpseCount,
                "slot " + (slot.index + 1) + " keeps crowded recovery requests bounded");
        return status;
    }

    private static void checkBorrowedUnsentDetailReleases(Slot slot) throws Exception {
        HostAccess access = slot.newAccess(new Store(), "borrowed-x3-" + slot.index, 502, 0);
        slot.onMap(access.proxy);
        Monster first = learn(slot, access, 6101, 701, 30, 40, 8);
        Monster second = learn(slot, access, 6102, 702, 50, 60, 8);
        slot.nativeDetail(access, first, 8, 0, 100);
        slot.nativeDetail(access, second, 8, 0, 100);

        access.now = 3000L;
        slot.tick(access.proxy);
        access.now = 3100L;
        slot.tick(access.proxy);
        require(access.requests.size() == 1 && access.requests.get(0).id == first.id,
                "slot " + (slot.index + 1) + " leaves the second borrowed detail request unsent");
        require(statusNumber(slot.status(), "W:") == 2,
                "slot " + (slot.index + 1) + " tracks both borrowed corpse recoveries");

        int x = second.x, y = second.y;
        second.hp = 37; // The native detail packet writes HP before its observer hook.
        second.maxHp = 100;
        slot.onDetail(access.proxy, second.id, 8, x, y, 37, 100);
        slot.onEntityUpdate(access.proxy, second);
        require(second.hp == 37 && !second.dead && !second.hidden && !second.pending,
                "slot " + (slot.index + 1) + " accepts valid unsolicited detail for confirmed borrowed identity");
        require(statusNumber(slot.status(), "W:") == 1,
                "slot " + (slot.index + 1) + " unsolicited detail clears only its own pending recovery");
        require(access.requests.size() == 1,
                "slot " + (slot.index + 1) + " does not send a request for accepted unsolicited detail");
    }

    private static void checkOwnedPlaceholderRemainsHeld(Slot slot) throws Exception {
        Store store = new Store();
        HostAccess seed = slot.newAccess(store, "owned-x3-" + slot.index, 501, 0);
        slot.onMap(seed.proxy);
        learn(slot, seed, 6201, 801, 70, 80, 8);
        learn(slot, seed, 6202, 802, 90, 100, 8);
        seed.now = 600L;
        slot.tick(seed.proxy);

        HostAccess restored = slot.newAccess(store, "owned-x3-" + slot.index, 501, 0);
        slot.onMap(restored.proxy);
        restored.now = 801L;
        slot.tick(restored.proxy);
        require(restored.restores.size() == 2,
                "slot " + (slot.index + 1) + " restores both cached owned placeholders");
        Monster owned = restored.monsters.get(Integer.valueOf(6202));
        require(owned != null && owned.placeholder && owned.pending,
                "slot " + (slot.index + 1) + " has an owned pending placeholder");
        require(restored.requests.size() == 1 && restored.requests.get(0).id == 6201,
                "slot " + (slot.index + 1) + " leaves the second owned detail request unsent");

        owned.hp = 37; // Native packet-7 writes HP before the support hook.
        owned.maxHp = 100;
        slot.onDetail(restored.proxy, 6202, 8, owned.x, owned.y, 37, 100);
        slot.onCombatSnapshot(restored.proxy, combat(new int[]{6202}, new int[]{37}));
        slot.onEntityUpdate(restored.proxy, owned);
        require(owned.pending && owned.placeholder && slot.hold(owned),
                "slot " + (slot.index + 1) + " keeps unrequested owned placeholder quarantined");
        require(!owned.discarded && restored.requests.size() == 1,
                "slot " + (slot.index + 1) + " bounds owned quarantine without discard or extra request");
    }

    private static Monster learn(Slot slot, HostAccess access, int id, int template,
            int x, int y, int level) throws Exception {
        Monster monster = new Monster(id, template, x, y, false);
        monster.hp = 45;
        monster.maxHp = 100;
        access.monsters.put(Integer.valueOf(id), monster);
        slot.onObject(access.proxy, template, id, x, y);
        slot.onDetail(access.proxy, id, level, x, y, 45, 100);
        return monster;
    }

    private static DataInputStream combat(int[] ids, int[] hp) throws Exception {
        if (ids.length != hp.length) throw new IllegalArgumentException("parallel arrays required");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(777);
        out.writeByte(19);
        out.writeByte(ids.length);
        for (int i = 0; i < ids.length; i++) {
            out.writeShort(ids[i]);
            out.writeInt(13);
            out.writeInt(hp[i]);
            out.writeByte(0);
        }
        out.writeInt(1234);
        out.writeInt(567);
        out.writeByte(2);
        out.writeInt(8);
        out.flush();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static int statusNumber(String status, String key) {
        int at = status.lastIndexOf(key);
        if (at < 0) throw new AssertionError("status is missing " + key + ": " + status);
        at += key.length();
        int end = at;
        while (end < status.length() && Character.isDigit(status.charAt(end))) end++;
        return Integer.parseInt(status.substring(at, end));
    }

    private static String sha256(File file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                java.nio.file.Files.readAllBytes(file.toPath()));
        StringBuilder result = new StringBuilder();
        for (byte value : digest)
            result.append(String.format("%02X", Integer.valueOf(value & 255)));
        return result.toString();
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static Object invoke(Method method, Object... args) throws Exception {
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new RuntimeException(cause);
        }
    }

    private static final class X3Loader extends URLClassLoader {
        X3Loader(File candidate) throws Exception {
            super(new URL[]{candidate.toURI().toURL()},
                    HungBySonCrowdedCacheX3HostTest.class.getClassLoader());
        }

        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.startsWith("luna.features.") && !name.startsWith("luna.api.")
                    && !name.startsWith("b.luna.features.") && !name.startsWith("b.luna.api.")
                    && !name.startsWith("c.luna.features.") && !name.startsWith("c.luna.api."))
                return super.loadClass(name, resolve);
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try { loaded = findClass(name); }
                catch (ClassNotFoundException missing) { return super.loadClass(name, resolve); }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    private static final class Slot {
        final int index;
        final ClassLoader loader;
        final Class<?> api;
        final Class<?> support;
        final Method onMap;
        final Method onObject;
        final Method onDetail;
        final Method tick;
        final Method onCombatSnapshot;
        final Method onEntityUpdate;
        final Method hold;
        final Method status;
        final Method remembered;

        Slot(ClassLoader loader, int index) throws Exception {
            this.index = index;
            this.loader = loader;
            String prefix = PREFIXES[index];
            api = loader.loadClass(prefix + "luna.api.MonsterCacheAccess");
            support = loader.loadClass(prefix + "luna.features.MonsterCacheSupport");
            onMap = support.getMethod("onMap", api);
            onObject = support.getMethod("onObject", api, int.class, int.class,
                    int.class, int.class, int.class, int.class);
            onDetail = support.getMethod("onDetail", api, int.class, int.class,
                    int.class, int.class, int.class, int.class);
            tick = support.getMethod("tick", api);
            onCombatSnapshot = support.getMethod("onCombatSnapshot", api, DataInputStream.class);
            onEntityUpdate = support.getMethod("onEntityUpdate", api, Object.class);
            hold = support.getMethod("hold", Object.class);
            status = support.getMethod("status");
            remembered = support.getMethod("remembered", String.class, int.class);
        }

        void onMap(Object proxy) throws Exception { invoke(onMap, proxy); }
        void onObject(Object proxy, int template, int id, int x, int y) throws Exception {
            invoke(onObject, proxy, Integer.valueOf(1), Integer.valueOf(template),
                    Integer.valueOf(id), Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(5));
        }
        void onDetail(Object proxy, int id, int level, int x, int y, int hp, int maxHp)
                throws Exception {
            invoke(onDetail, proxy, Integer.valueOf(id), Integer.valueOf(level), Integer.valueOf(x),
                    Integer.valueOf(y), Integer.valueOf(hp), Integer.valueOf(maxHp));
        }
        void nativeDetail(HostAccess access, Monster monster, int level, int hp, int maxHp)
                throws Exception {
            monster.hp = hp;
            monster.maxHp = maxHp;
            if (hp == 0) monster.dead = true;
            onDetail(access.proxy, monster.id, level, monster.x, monster.y, hp, maxHp);
        }
        void tick(Object proxy) throws Exception { invoke(tick, proxy); }
        void onCombatSnapshot(Object proxy, DataInputStream input) throws Exception {
            invoke(onCombatSnapshot, proxy, input);
        }
        void onEntityUpdate(Object proxy, Object monster) throws Exception {
            invoke(onEntityUpdate, proxy, monster);
        }
        boolean hold(Object monster) throws Exception {
            return Boolean.TRUE.equals(hold.invoke(null, monster));
        }
        String status() throws Exception { return (String)status.invoke(null); }
        int[] remembered(String server, int map) throws Exception {
            return (int[])remembered.invoke(null, server, Integer.valueOf(map));
        }
        HostAccess newAccess(Store store, String server, int map, int zone) {
            return new HostAccess(api, store, server, map, zone);
        }
    }

    private static final class Store { byte[] bytes; }

    private static final class HostAccess implements InvocationHandler {
        final Store store;
        final String server;
        final int map;
        final int zone;
        final Map<Integer, Monster> monsters = new HashMap<Integer, Monster>();
        final List<Monster> restores = new ArrayList<Monster>();
        final List<Monster> discarded = new ArrayList<Monster>();
        final List<Request> requests = new ArrayList<Request>();
        long now;
        Object proxy;

        HostAccess(Class<?> api, Store store, String server, int map, int zone) {
            this.store = store;
            this.server = server;
            this.map = map;
            this.zone = zone;
            this.proxy = Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, this);
        }

        public Object invoke(Object ignored, Method method, Object[] args) {
            String name = method.getName();
            if (name.equals("toString")) return "HostAccess(" + server + "," + map + ")";
            if (name.equals("hashCode")) return Integer.valueOf(System.identityHashCode(ignored));
            if (name.equals("equals")) return Boolean.valueOf(ignored == args[0]);
            if (name.equals("now")) return Long.valueOf(now);
            if (name.equals("serverKey")) return server;
            if (name.equals("mapId")) return Integer.valueOf(map);
            if (name.equals("zoneId")) return Integer.valueOf(zone);
            if (name.equals("ready")) return Boolean.TRUE;
            if (name.equals("autoEnabled")) return Boolean.FALSE;
            if (name.equals("find")) return monsters.get((Integer)args[0]);
            if (name.equals("dead")) return Boolean.valueOf(dead((Monster)args[0]));
            if (name.equals("hidden")) return Boolean.valueOf(((Monster)args[0]).hidden);
            if (name.equals("retiring")) return Boolean.valueOf(((Monster)args[0]).retiring);
            if (name.equals("hp")) return Integer.valueOf(((Monster)args[0]).hp);
            if (name.equals("id")) return Integer.valueOf(((Monster)args[0]).id);
            if (name.equals("type")) return Integer.valueOf(args[0] instanceof Monster ? 1 : 0);
            if (name.equals("clearAutoTarget") || name.equals("hud")) return null;
            if (name.equals("corpse")) {
                Monster monster = (Monster)args[0];
                monster.hp = 0;
                monster.dead = true;
                monster.hidden = false;
                monster.pending = true;
                monster.deathState = 4;
                monster.retiring = false;
                monster.removalCount++;
                if (Boolean.TRUE.equals(args[1])) monster.deathFrames++;
                return null;
            }
            if (name.equals("applyHp")) {
                Monster monster = (Monster)args[0];
                int hp = ((Integer)args[1]).intValue();
                if (monsters.get(Integer.valueOf(monster.id)) != monster || monster.retiring
                        || monster.maxHp <= 0 || hp < 0 || hp > monster.maxHp) return Boolean.FALSE;
                monster.hp = hp;
                if (hp > 0) {
                    monster.dead = false;
                    monster.hidden = false;
                    monster.pending = false;
                    monster.retiring = false;
                    monster.deathState = 0;
                    monster.removalCount = 0;
                }
                return Boolean.TRUE;
            }
            if (name.equals("restore")) {
                Monster monster = new Monster(((Integer)args[1]).intValue(),
                        ((Integer)args[0]).intValue(), ((Integer)args[2]).intValue(),
                        ((Integer)args[3]).intValue(), true);
                monsters.put(Integer.valueOf(monster.id), monster);
                restores.add(monster);
                return monster;
            }
            if (name.equals("pending")) {
                Monster monster = (Monster)args[0];
                boolean pending = ((Boolean)args[1]).booleanValue();
                monster.pending = pending;
                if (pending) {
                    monster.hp = 0;
                    monster.dead = true;
                    monster.deathState = 4;
                } else if (monster.hp > 0) {
                    monster.dead = false;
                    monster.hidden = false;
                    monster.deathState = 0;
                    monster.retiring = false;
                    monster.removalCount = 0;
                }
                return null;
            }
            if (name.equals("discard")) {
                Monster monster = (Monster)args[0];
                monster.discarded = true;
                discarded.add(monster);
                if (monsters.get(Integer.valueOf(monster.id)) == monster)
                    monsters.remove(Integer.valueOf(monster.id));
                return null;
            }
            if (name.equals("request")) {
                requests.add(new Request(((Integer)args[0]).intValue(), now));
                return null;
            }
            if (name.equals("load")) return store.bytes == null ? null : store.bytes.clone();
            if (name.equals("save")) {
                byte[] bytes = (byte[])args[0];
                store.bytes = bytes == null ? null : bytes.clone();
                return Boolean.TRUE;
            }
            if (method.getReturnType() == Void.TYPE) return null;
            throw new AssertionError("Unhandled MonsterCacheAccess method: " + method);
        }

        private boolean dead(Monster monster) {
            return monster.hp <= 0 || monster.dead || monster.hidden
                    || monster.deathState == 4 || monster.retiring;
        }
    }

    private static final class Monster {
        final int id;
        final int template;
        int x;
        int y;
        final boolean placeholder;
        int hp = 1;
        int maxHp = 1;
        boolean dead;
        boolean hidden;
        boolean pending;
        boolean retiring;
        boolean discarded;
        int deathState;
        int deathFrames;
        int removalCount;

        Monster(int id, int template, int x, int y, boolean placeholder) {
            this.id = id;
            this.template = template;
            this.x = x;
            this.y = y;
            this.placeholder = placeholder;
        }
    }

    private static final class Request {
        final int id;
        final long when;
        Request(int id, long when) { this.id = id; this.when = when; }
    }
}
