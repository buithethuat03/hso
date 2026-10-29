import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.util.Properties;

/** Focused current-binary selector/request check; reuses the existing native world fixture. */
public final class HungBySonAutoAttackPkCorpseHostTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static Object create(Class<?> type, Class<?>[] args, Object... values) throws Exception {
        Constructor<?> c = type.getDeclaredConstructor(args); c.setAccessible(true); return c.newInstance(values);
    }
    private static Object call(Object receiver, String name, Class<?>[] args, Object... values) throws Exception {
        Method m = receiver.getClass().getDeclaredMethod(name, args); m.setAccessible(true); return m.invoke(receiver, values);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object entity(Object world, int hp, byte kind, int state, int x) throws Exception {
        return call(world, "entity", new Class<?>[]{int.class, byte.class, int.class, int.class, int.class, int.class, int.class},
                hp, kind, state, 0, 1, x, 0);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("current.jar catalog.properties");
        Properties p = new Properties(); FileInputStream input = new FileInputStream(args[1]);
        try { p.load(input); } finally { input.close(); }
        Class<?> helper = Class.forName("HungBySonAutoAttackBinaryTest");
        Class<?> loaderType = Class.forName(helper.getName() + "$NativeProbeLoader");
        URLClassLoader loader = (URLClassLoader) create(loaderType, new Class<?>[]{File.class, Properties.class}, new File(args[0]), p);
        try {
            Class<?> bridgeType = loader.loadClass(p.getProperty("bridge.owner").replace('/', '.'));
            Object bridge = bridgeType.getConstructor().newInstance();
            Class<?> worldType = Class.forName(helper.getName() + "$NativeAccess");
            Object world = create(worldType, new Class<?>[]{ClassLoader.class, Object.class, Properties.class}, loader, bridge, p);
            call(world, "installReadyWorld", new Class<?>[0]);
            Object me = field(worldType, "player").get(world);
            field(me.getClass(), "cG").setInt(me, 0); field(me.getClass(), "cA").setInt(me, -1);
            Class<?> api = loader.loadClass("luna.api.AutoAttackAccess");
            Class<?> settings = loader.loadClass("luna.api.AutoAttackSettings");
            Class<?> clockApi = loader.loadClass("luna.api.LunaClock");
            Object access = Proxy.newProxyInstance(loader, new Class<?>[]{api}, (InvocationHandler) world);
            Class<?> clockType = Class.forName(helper.getName() + "$Clock");
            Object clock = create(clockType, new Class<?>[0]);
            Object time = Proxy.newProxyInstance(loader, new Class<?>[]{clockApi}, (InvocationHandler) clock);
            Class<?> storeType = Class.forName(helper.getName() + "$SettingsHandler");
            Object store = create(storeType, new Class<?>[]{int[].class}, (Object) new int[]{1, 650});
            Object config = Proxy.newProxyInstance(loader, new Class<?>[]{settings}, (InvocationHandler) store);
            Class<?> runtime = loader.loadClass("luna.features.AutoAttackRuntime");
            runtime.getMethod("installForTest", api, settings, clockApi).invoke(null, access, config, time);
            runtime.getMethod("handleAction", int.class).invoke(null, -113);
            Object a = entity(world, 100, (byte)0, 0, 1), b = entity(world, 100, (byte)0, 0, 5);
            call(world, "replaceEntities", new Class<?>[]{Object[].class}, (Object) new Object[]{a, b});
            Method tick = runtime.getMethod("tick"), target = runtime.getMethod("target");
            Method allow = runtime.getMethod("allowRequest", int.class, Object.class);
            Method valid = bridgeType.getMethod("validTarget", Object.class, boolean.class);
            field(clockType, "now").setLong(clock, 651); tick.invoke(null);
            int tag = field(worldType, "lastAttackTag").getInt(world);
            check(target.invoke(null) == a && tag > 0, "Starts on nearest live PK player A");
            check(Boolean.TRUE.equals(allow.invoke(null, tag, a)), "Live retained player authorizes tagged request");
            field(a.getClass(), p.getProperty("native.entity.hp.field")).setInt(a, 0);
            check(Boolean.FALSE.equals(valid.invoke(bridge, a, true)), "HP0 player rejected before death state changes");
            check(Boolean.FALSE.equals(allow.invoke(null, tag, a)), "Pending request rejected immediately after HP0 before next tick");
            tick.invoke(null);
            check(target.invoke(null) == b, "Selects player B while HP0 player A remains in native world list");
            check(((Integer)bridgeType.getMethod("entityCount").invoke(bridge)).intValue() == 2,
                    "No out-zone/removal event was required");
            int attempts = field(worldType, "attackCalls").getInt(world);
            field(clockType, "now").setLong(clock, 1301); tick.invoke(null);
            check(field(worldType, "attackCalls").getInt(world) == attempts, "Target change waits configured650ms boundary");
            field(clockType, "now").setLong(clock, 1302); tick.invoke(null);
            check(field(worldType, "lastAttackTarget").get(world) == b, "Next attack goes to B after configured delay");
            field(b.getClass(), p.getProperty("native.entity.state.field")).setInt(b, 4);
            Object mob = entity(world, 100, (byte)1, 0, 3), c = entity(world, 100, (byte)0, 0, 8);
            call(world, "replaceEntities", new Class<?>[]{Object[].class}, (Object) new Object[]{a, b, c, mob});
            check(Boolean.FALSE.equals(valid.invoke(bridge, b, true)), "Death state4 rejects a player even with stale positive HP");
            tick.invoke(null);
            boolean pkOnly = "true".equals(p.getProperty("auto.pk.upgrade"));
            check(target.invoke(null) == (pkOnly ? c : mob), pkOnly
                    ? "PK-only mode ignores the nearer monster and selects live player C"
                    : "Mixed mode chooses nearer valid monster rather than giving players priority");
            field(mob.getClass(), p.getProperty("native.entity.hp.field")).setInt(mob, 0);
            field(c.getClass(), p.getProperty("native.entity.hp.field")).setInt(c, 0);
            tick.invoke(null);
            check(target.invoke(null) == null, "Only corpses left means no target");
            System.out.println("PK_CORPSE_CURRENT_BINARY=PASS checks=" + checks + "; HP0 retained object, stale request, death state, target handoff, mixed nearest policy");
        } finally { loader.close(); }
    }
}
