import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Properties;

/** Proves the Chibi cache stubs stop persistence and let native monster lifecycle run. */
public final class ChibiNoMonsterCacheTest {
    static int checks;

    static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    static Properties properties(File file) throws Exception {
        Properties result = new Properties();
        FileInputStream in = new FileInputStream(file);
        try { result.load(in); } finally { in.close(); }
        return result;
    }

    static Object call(Class<?> support, String name, Class<?>[] types, Object... args)
            throws Exception {
        try { return support.getMethod(name, types).invoke(null, args); }
        catch (InvocationTargetException e) { throw new RuntimeException(name, e.getCause()); }
    }

    static int vectorSize(Object vector) throws Exception {
        return ((Integer)vector.getClass().getMethod("c").invoke(vector)).intValue();
    }

    static void checkNoOpContract(HungBySonMonsterRecoveryBinaryTest.Harness h) throws Exception {
        final int[] calls = {0};
        Object access = Proxy.newProxyInstance(h.loader, new Class<?>[] {h.api},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        calls[0]++;
                        throw new AssertionError("cache stub called access." + method.getName());
                    }
                });
        Class<?> api = h.api;
        Class<?> support = h.support;
        Object marker = new Object();
        Class<?>[] accessOnly = {api};
        Class<?>[] accessAndSixInts = {api, int.class, int.class, int.class,
                int.class, int.class, int.class};
        call(support, "onMap", accessOnly, access);
        call(support, "onObject", accessAndSixInts, access, 1, 5, 41, 30, 40, 5);
        call(support, "onDetail", accessAndSixInts, access, 41, 5, 30, 40, 50, 100);
        call(support, "tick", accessOnly, access);
        call(support, "paint", new Class<?>[] {Object.class}, marker);
        call(support, "flushNow", accessOnly, access);
        require(!((Boolean)call(support, "verifyTarget",
                new Class<?>[] {api, int.class, Object.class}, access, 41, marker)).booleanValue(),
                "verifyTarget stays disabled");
        DataInputStream empty = new DataInputStream(new ByteArrayInputStream(new byte[0]));
        call(support, "onCombatSnapshot", new Class<?>[] {api, DataInputStream.class}, access, empty);
        call(support, "onUnseenCombat", new Class<?>[] {api, DataInputStream.class}, access, empty);
        call(support, "onEntityUpdate", new Class<?>[] {api, Object.class}, access, marker);
        require(!((Boolean)call(support, "advanceCorpse",
                new Class<?>[] {Object.class}, marker)).booleanValue(),
                "advanceCorpse falls through to native animation");
        require(!((Boolean)call(support, "hold",
                new Class<?>[] {Object.class}, marker)).booleanValue(),
                "hold falls through to native death handling");
        require(((String)call(support, "status", new Class<?>[0])).length() == 0,
                "QCache status is absent");
        require(((int[])call(support, "remembered",
                new Class<?>[] {String.class, int.class}, "server", 1)).length == 0,
                "remembered monster cache is empty");
        call(support, "resetForTest", new Class<?>[0]);
        require(calls[0] == 0, "no support method reads, writes, requests, restores, or mutates through the bridge");
    }

    static void checkCachedBaseline(Map<String, byte[]> jar, Properties p) throws Exception {
        HungBySonMonsterRecoveryBinaryTest.Harness h =
                new HungBySonMonsterRecoveryBinaryTest.Harness(jar, p);
        Object corpse = h.make(41);
        h.learn(41);
        HungBySonMonsterRecoveryBinaryTest.set(corpse, "bs", 0);
        HungBySonMonsterRecoveryBinaryTest.set(corpse, "cG", 4);
        HungBySonMonsterRecoveryBinaryTest.set(corpse, "dk", true);
        h.call("onEntityUpdate", new Class<?>[] {h.api, Object.class}, h.access, corpse);
        require(h.hold(corpse), "cached baseline retains a confirmed corpse");
        require(h.restores == 0, "baseline setup does not need a placeholder restore");
    }

    static void checkNativeLifecycle(HungBySonMonsterRecoveryBinaryTest.Harness h) throws Exception {
        checkNoOpContract(h);
        Object dying = h.make(51);
        h.learn(51);
        require(!h.hold(dying), "learned object is not quarantined by the stub");
        HungBySonMonsterRecoveryBinaryTest.set(dying, "bs", 0);
        HungBySonMonsterRecoveryBinaryTest.set(dying, "cG", 4);
        HungBySonMonsterRecoveryBinaryTest.set(dying, "dk", true);
        HungBySonMonsterRecoveryBinaryTest.set(dying, "dj", 16);
        HungBySonMonsterRecoveryBinaryTest.set(dying, "dy", 100);
        HungBySonMonsterRecoveryBinaryTest.set(dying, "cK", 69);
        h.death.invoke(dying);
        require(HungBySonMonsterRecoveryBinaryTest.bool(dying, "cL"),
                "native death counter reaches its normal removal state");

        Object animation = h.make(52);
        HungBySonMonsterRecoveryBinaryTest.set(animation, "bs", 0);
        HungBySonMonsterRecoveryBinaryTest.set(animation, "cG", 4);
        HungBySonMonsterRecoveryBinaryTest.set(animation, "dk", true);
        int beforeFrame = HungBySonMonsterRecoveryBinaryTest.get(animation, "bb");
        h.mob.getMethod("b_").invoke(animation);
        require(HungBySonMonsterRecoveryBinaryTest.get(animation, "bb") == beforeFrame + 1,
                "native corpse animation advances after advanceCorpse returns false");

        Object removed = h.make(53);
        h.learn(53);
        HungBySonMonsterRecoveryBinaryTest.set(removed, "bs", 0);
        HungBySonMonsterRecoveryBinaryTest.set(removed, "cG", 4);
        HungBySonMonsterRecoveryBinaryTest.set(removed, "dk", true);
        int size = vectorSize(h.vector);
        h.packet("illi", HungBySonMonsterRecoveryBinaryTest.shorts(53));
        require(HungBySonMonsterRecoveryBinaryTest.bool(removed, "cM"),
                "native packet 8 still marks the monster for removal");
        HungBySonMonsterRecoveryBinaryTest.set(removed, "bT", 4);
        for (int i = 0; i < 10 && !HungBySonMonsterRecoveryBinaryTest.bool(removed, "cL"); i++)
            h.worldFrame();
        require(HungBySonMonsterRecoveryBinaryTest.bool(removed, "cL"),
                "native world removal consumes cM and sets cL");
        require(vectorSize(h.vector) == size - 1,
                "native world loop removes the dead monster from the entity list");
        require(h.requests == 0 && h.restores == 0,
                "candidate never queues detail requests or creates cached replacements");
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 4, "cached.jar candidate.jar symbols.properties microemulator.jar");
        Properties p = properties(new File(args[2]));
        Map<String, byte[]> cached = HungBySonMonsterRecoveryBinaryTest.read(new File(args[0]));
        Map<String, byte[]> candidate = HungBySonMonsterRecoveryBinaryTest.read(new File(args[1]));
        checkCachedBaseline(cached, p);
        HungBySonMonsterRecoveryBinaryTest.Harness h =
                new HungBySonMonsterRecoveryBinaryTest.Harness(candidate, p);
        checkNativeLifecycle(h);
        System.out.println("ChibiNoMonsterCacheTest PASS (" + checks
                + " checks); no cache callbacks, native death, animation, and world removal");
    }
}
