import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Farm + Special spot mode is present, slot-local and behaving in all three X3 clients. */
public final class HungBySonFarmSpotX3HostTest {
    static final String[] SLOTS = {"", "b/", "c/"};
    static int checks;
    static void check(boolean value, String text) { checks++; if (!value) throw new AssertionError(text); }

    static String encoded(String suffix) {
        StringBuilder value = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) value.append('i');
        return value.append(suffix).toString();
    }

    static ClassNode node(JarFile jar, String name) throws Exception {
        java.util.jar.JarEntry entry = jar.getJarEntry(name + ".class");
        check(entry != null, "Missing " + name);
        ClassNode c = new ClassNode();
        new ClassReader(jar.getInputStream(entry)).accept(c, 0);
        return c;
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 2, "x3.jar microemulator.jar");
        File candidate = new File(args[0]);
        JarFile jar = new JarFile(candidate);
        try {
            for (String slot : SLOTS) {
                String bridge = slot + "luna/generated/HungAutoAttackAccess", api = slot + "luna/api/FarmSpotAccess";
                ClassNode farm = node(jar, slot + encoded("lliiiiil"));
                int guards = 0;
                for (MethodNode m : farm.methods) if (m.name.equals("codexFarmUpdate") && m.desc.equals("()V"))
                    for (AbstractInsnNode n : m.instructions) if (n instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode)n;
                        if (call.getOpcode() == Opcodes.INVOKESTATIC && call.name.equals("farmSpot")) {
                            check(call.owner.equals(bridge) && call.desc.equals("()Z"), "Slot-local spot guard " + slot);
                            guards++;
                        }
                    }
                check(guards == 2, "Two Farm spot guards in slot '" + slot + "'");
                ClassNode access = node(jar, bridge);
                check(access.interfaces.contains(api), "Slot bridge implements its own FarmSpotAccess " + slot);
                int added = 0;
                for (MethodNode m : access.methods) if (m.name.startsWith("farmSpot")) added++;
                // Seven since the Farm spot release, eight once the stuck-cast cancel was added.
                check(added == 7 || added == 8, "Farm spot bridge methods in slot '" + slot + "': " + added);
            }
        } finally { jar.close(); }

        URLClassLoader loader = new URLClassLoader(new URL[]{candidate.toURI().toURL(), new File(args[1]).toURI().toURL()}, null);
        for (String slot : SLOTS) runController(loader, slot.replace('/', '.'));
        System.out.println("HungBySonFarmSpotX3HostTest PASS checks=" + checks);
    }

    /** The slot's own controller holds legacy auto, attacks the nearest valid mob and resets the counter. */
    static void runController(ClassLoader loader, final String pkg) throws Exception {
        Class<?> accessType = loader.loadClass(pkg + "luna.api.AutoAttackAccess");
        Class<?> settingsType = loader.loadClass(pkg + "luna.api.AutoAttackSettings");
        Class<?> clockType = loader.loadClass(pkg + "luna.api.LunaClock");
        Class<?> viewType = loader.loadClass(pkg + "luna.api.FarmSpotAccess");
        Class<?> controllerType = loader.loadClass(pkg + "luna.features.AutoAttackController");
        final Object near = new Object(), far = new Object();
        final List<String> calls = new ArrayList<String>();
        final Object[] attacked = new Object[1];
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] a) {
                String n = method.getName();
                calls.add(n);
                if (n.equals("load")) { int[] v = (int[])a[0]; v[0] = 0; v[1] = 650; return null; }
                if (n.equals("nowMillis")) return Long.valueOf(1000L);
                if (n.equals("entityCount")) return Integer.valueOf(2);
                if (n.equals("entityAt")) return ((Integer)a[0]).intValue() == 0 ? far : near;
                if (n.equals("distanceTo")) return Integer.valueOf(a[0] == near ? 40 : 300);
                if (n.equals("farmSpotTargetValid")) return Boolean.TRUE;
                if (n.equals("farmSpotAttack")) { attacked[0] = a[0]; return Boolean.TRUE; }
                Class<?> r = method.getReturnType();
                if (r == Boolean.TYPE) return Boolean.FALSE;
                if (r == Integer.TYPE) return Integer.valueOf(0);
                if (r == Long.TYPE) return Long.valueOf(0L);
                return null;
            }
        };
        Object access = Proxy.newProxyInstance(loader, new Class<?>[]{accessType}, handler);
        Object settings = Proxy.newProxyInstance(loader, new Class<?>[]{settingsType}, handler);
        Object clock = Proxy.newProxyInstance(loader, new Class<?>[]{clockType}, handler);
        Object view = Proxy.newProxyInstance(loader, new Class<?>[]{viewType}, handler);
        Object controller = controllerType.getConstructor(accessType, settingsType, clockType).newInstance(access, settings, clock);
        Method spot = controllerType.getMethod("farmSpot", viewType);
        // Lock-pacing builds default Tấn công quái thường ON (the ModUpLv loop attacks); this checks the Farm path.
        if (((String)controllerType.getMethod("normalLabel").invoke(controller)).endsWith("[BẬT]"))
            controllerType.getMethod("handleAction", Integer.TYPE).invoke(controller, Integer.valueOf(-117));
        check(Boolean.FALSE.equals(spot.invoke(controller, view)) && !calls.contains("farmSpotHold"),
                "Special OFF keeps native Farm in slot '" + pkg + "'");
        controllerType.getMethod("handleAction", Integer.TYPE).invoke(controller, Integer.valueOf(-116));
        check(Boolean.TRUE.equals(spot.invoke(controller, view)) && calls.contains("farmSpotHold")
                        && attacked[0] == near && calls.contains("farmSpotActive"),
                "Special ON stands, attacks the nearest mob and resets the counter in slot '" + pkg + "'");
    }
}
