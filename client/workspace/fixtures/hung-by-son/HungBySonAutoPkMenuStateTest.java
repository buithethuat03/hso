import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Native popup regression for both Auto PK mode labels and their callback. */
public final class HungBySonAutoPkMenuStateTest {
    private static final String HELPER = "HungBySonAutoAttackBinaryTest";
    private static int checks;

    private HungBySonAutoPkMenuStateTest() { }

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }

    private static Properties load(File file) throws Exception {
        Properties result = new Properties();
        InputStream input = new FileInputStream(file);
        try { result.load(input); } finally { input.close(); }
        return result;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().length() == 0)
            throw new IllegalArgumentException("Missing catalog key " + key);
        return value.trim();
    }

    private static String hash(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        InputStream input = new FileInputStream(file);
        try {
            byte[] block = new byte[8192];
            for (int count; (count = input.read(block)) >= 0;) digest.update(block, 0, count);
        } finally { input.close(); }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02X", Integer.valueOf(value & 255)));
        return result.toString();
    }

    private static Object construct(Class<?> type, Class<?>[] parameterTypes, Object... args) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private static Object call(Object target, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try { return method.invoke(target, args); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new IllegalStateException(cause);
        }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        for (Class<?> cursor = owner; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field result = cursor.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static Object lastPopup(Object probe) throws Exception {
        return call(probe, "lastPopup", new Class<?>[0]);
    }

    private static String label(Object row) throws Exception {
        for (Class<?> cursor = row.getClass(); cursor != null; cursor = cursor.getSuperclass())
            for (Field member : cursor.getDeclaredFields()) {
                if (member.getType() != String.class || Modifier.isStatic(member.getModifiers())) continue;
                member.setAccessible(true);
                Object value = member.get(row);
                if (value instanceof String) return (String)value;
            }
        throw new AssertionError("Native Auto Attack row has no label");
    }

    private static int listSize(Object list) throws Exception {
        Method method = list.getClass().getMethod("c");
        method.setAccessible(true);
        return ((Integer)method.invoke(list)).intValue();
    }

    private static Object listGet(Object list, int index) throws Exception {
        Method method = list.getClass().getMethod("a", Integer.TYPE);
        method.setAccessible(true);
        return method.invoke(list, Integer.valueOf(index));
    }

    private static int action(Object row) throws Exception {
        for (Class<?> cursor = row.getClass(); cursor != null; cursor = cursor.getSuperclass())
            for (Field member : cursor.getDeclaredFields()) {
                if (Modifier.isStatic(member.getModifiers())) continue;
                Class<?> type = member.getType();
                if (type != Byte.TYPE && type != Short.TYPE && type != Integer.TYPE) continue;
                member.setAccessible(true);
                int value = type == Byte.TYPE ? member.getByte(row)
                        : type == Short.TYPE ? member.getShort(row) : member.getInt(row);
                if (value <= -110 && value >= -117) return value;
            }
        throw new AssertionError("Native Auto Attack row has no action code");
    }

    private static Object popupItems(Object popup) throws Exception {
        return field(popup.getClass(), "items").get(popup);
    }

    private static Object click(Object row) throws Exception {
        Method method = row.getClass().getDeclaredMethod("a");
        method.setAccessible(true);
        return method.invoke(row);
    }

    private static Object proxy(ClassLoader loader, Class<?> api, final boolean settings) {
        return Proxy.newProxyInstance(loader, new Class<?>[]{api}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                if (settings && "load".equals(method.getName())) {
                    int[] saved = (int[])args[0]; saved[0] = 0; saved[1] = 650;
                }
                if (!settings && "nowMillis".equals(method.getName())) return Long.valueOf(1000L);
                Class<?> type = method.getReturnType();
                if (type == Boolean.TYPE) return Boolean.FALSE;
                if (type == Byte.TYPE) return Byte.valueOf((byte)0);
                if (type == Short.TYPE) return Short.valueOf((short)0);
                if (type == Integer.TYPE) return Integer.valueOf(0);
                if (type == Long.TYPE) return Long.valueOf(0L);
                if (type == Float.TYPE) return Float.valueOf(0.0f);
                if (type == Double.TYPE) return Double.valueOf(0.0d);
                if (type == Character.TYPE) return Character.valueOf((char)0);
                return null;
            }
        });
    }

    private static List<String> verifyStates(Object bridge, Class<?> bridgeType, Object probe) throws Exception {
        Method show = bridgeType.getMethod("showMenu", Boolean.TYPE, Boolean.TYPE, Integer.TYPE);
        Object firstPkRow = null;
        List<String> lastLabels = null;
        boolean[] enabledValues = new boolean[]{false, true};
        boolean[] playerValues = new boolean[]{false, true};
        for (boolean enabled : enabledValues) for (boolean players : playerValues) {
            show.invoke(bridge, Boolean.valueOf(enabled), Boolean.valueOf(players), Integer.valueOf(650));
            Object popup = lastPopup(probe);
            require(popup != null, "Native popup captured for enabled=" + enabled + ", players=" + players);
            Object items = popupItems(popup);
            require(listSize(items) >= 4, "Native popup keeps its Auto Attack rows");
            String pk = label(listGet(items, 2));
            String expected = players ? "Auto PK [x]" : "Auto PK";
            require(expected.equals(pk), "PK row is " + expected + " for enabled=" + enabled + ", players=" + players + "; saw " + pk);
            require(!pk.contains("+ PK"), "PK row has no legacy generic label");
            require((label(listGet(items, 0)).indexOf("BẬT") >= 0) == enabled,
                    "Master checkbox reflects enabled=" + enabled);
            for (int i = 0; i < listSize(items); i++) {
                Object row = listGet(items, i);
                String rowLabel = label(row);
                require(!rowLabel.contains("+ PK"), "No generic quái + PK wording in native rows: " + rowLabel);
                require(action(row) != -117, "No native menu row retains legacy action -117");
            }
            if (!enabled && !players && firstPkRow == null) firstPkRow = listGet(items, 2);
            lastLabels = new ArrayList<String>();
            for (int i = 0; i < listSize(items); i++) lastLabels.add(label(listGet(items, i)));
        }
        if (firstPkRow == null) throw new AssertionError("No unselected PK row for callback test");
        show.invoke(bridge, Boolean.FALSE, Boolean.FALSE, Integer.valueOf(650));
        Object currentItems = popupItems(lastPopup(probe));
        click(listGet(currentItems, 2));
        return lastLabels;
    }

    private static void verifyPkFiltersMonsters(Object nativeAccess, Class<?> runtime) throws Exception {
        Class<?>[] entityArgs = new Class<?>[]{Integer.TYPE, Byte.TYPE, Integer.TYPE, Integer.TYPE,
                Integer.TYPE, Integer.TYPE, Integer.TYPE};
        Object monster = call(nativeAccess, "entity", entityArgs, Integer.valueOf(100), Byte.valueOf((byte)1),
                Integer.valueOf(2), Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(20), Integer.valueOf(20));
        Object hostile = call(nativeAccess, "entity", entityArgs, Integer.valueOf(100), Byte.valueOf((byte)0),
                Integer.valueOf(2), Integer.valueOf(0), Integer.valueOf(1), Integer.valueOf(250), Integer.valueOf(250));
        call(nativeAccess, "replaceEntities", new Class<?>[]{Object[].class}, new Object[]{new Object[]{monster, hostile}});
        runtime.getMethod("tick").invoke(null);
        require(runtime.getMethod("target").invoke(null) == hostile,
                "PK callback's player mode filters a closer monster and keeps the native-valid hostile player");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("candidate.jar auto-attack.properties");
        File candidate = new File(args[0]), catalogFile = new File(args[1]);
        require(candidate.isFile() && catalogFile.isFile(), "Candidate and Auto Attack catalog exist");
        Properties catalog = load(catalogFile);
        String expectedHash = catalog.getProperty("output.sha256");
        if (expectedHash != null) require(expectedHash.equalsIgnoreCase(hash(candidate)), "Candidate matches catalog hash");

        Object probe = construct(Class.forName(HELPER + "$NativeProbeLoader"),
                new Class<?>[]{File.class, Properties.class}, candidate, catalog);
        ClassLoader loader = (ClassLoader)probe;
        try {
            Class<?> bridgeType = loader.loadClass(required(catalog, "bridge.owner").replace('/', '.'));
            Object bridge = bridgeType.getDeclaredConstructor().newInstance();
            Object nativeAccess = construct(Class.forName(HELPER + "$NativeAccess"),
                    new Class<?>[]{ClassLoader.class, Object.class, Properties.class}, loader, bridge, catalog);
            call(nativeAccess, "installReadyWorld", new Class<?>[0]);

            Class<?> api = loader.loadClass("luna.api.AutoAttackAccess");
            Class<?> settingsType = loader.loadClass("luna.api.AutoAttackSettings");
            Class<?> clockType = loader.loadClass("luna.api.LunaClock");
            Class<?> runtime = loader.loadClass("luna.features.AutoAttackRuntime");
            runtime.getMethod("installForTest", api, settingsType, clockType).invoke(null,
                    bridge, proxy(loader, settingsType, true), proxy(loader, clockType, false));
            require(Boolean.TRUE.equals(bridgeType.getMethod("ready").invoke(bridge)),
                    "Native world is ready for the actual PK menu callback");

            verifyStates(bridge, bridgeType, probe);
            require(Boolean.TRUE.equals(runtime.getMethod("enabled").invoke(null))
                    && Boolean.TRUE.equals(runtime.getMethod("playersEnabled").invoke(null)),
                    "Clicking the native PK row enables Auto Attack in player mode");
            @SuppressWarnings("unchecked")
            List<Integer> callbacks = (List<Integer>)field(probe.getClass(), "actionCalls").get(probe);
            require(callbacks.contains(Integer.valueOf(-113)), "Native button callback forwards PK action -113");
            for (Integer callback : callbacks)
                require(callback.intValue() != -117, "Native callback trace never uses hidden legacy action -117");
            verifyPkFiltersMonsters(nativeAccess, runtime);

            System.out.println("HungBySonAutoPkMenuStateTest PASS checks=" + checks
                    + "; four native enabled/mode checkbox states and PK callback");
        } finally {
            if (loader instanceof java.net.URLClassLoader) ((java.net.URLClassLoader)loader).close();
        }
    }
}
