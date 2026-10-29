import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * ModUpLv port in every X3 slot: each slot has its own HungBySonModUpLv and all five hooks
 * (Special targets, Auto đánh row, Cài Đặt row, dialog filter, module loader) call that slot's
 * copy; the normal-monsters option and the module state are independent per slot. Lock-pacing
 * builds also: every slot's canvas loop, serializers, combat handler and EXP dialog call that
 * slot's HungBySonModUpLv / HungBySonSkillStats, and the cast counters, pacing mode and lock of
 * SkillCastStats are per slot.
 */
public final class HungBySonModUpLvX3HostTest {
    static final String[] SLOTS = {"", "b/", "c/"};
    static int checks;

    static void check(boolean value, String text) {
        checks++;
        if (!value) throw new AssertionError(text);
        System.out.println("PASS " + text);
    }

    static String n(String suffix) {
        StringBuilder b = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) b.append('i');
        return b.append(suffix).toString();
    }

    static ClassNode node(JarFile jar, String name) throws IOException {
        JarEntry e = jar.getJarEntry(name + ".class");
        if (e == null) throw new AssertionError("Missing " + name);
        ClassNode c = new ClassNode();
        InputStream in = jar.getInputStream(e);
        try { new ClassReader(in).accept(c, 0); } finally { in.close(); }
        return c;
    }

    static int calls(ClassNode c, String method, String owner, String name) {
        int count = 0;
        for (MethodNode m : c.methods) {
            if (method != null && !m.name.equals(method)) continue;
            for (AbstractInsnNode x : m.instructions)
                if (x instanceof MethodInsnNode && ((MethodInsnNode)x).owner.equals(owner) && ((MethodInsnNode)x).name.equals(name)) count++;
        }
        return count;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("candidate.jar microemulator.jar cast.owner");
        File candidate = new File(args[0]);
        String cast = args[2];
        JarFile jar = new JarFile(candidate);
        try {
            for (String slot : SLOTS) {
                String port = slot + "HungBySonModUpLv";
                ClassNode c = node(jar, port);
                check(c.version == Opcodes.V1_4 && c.interfaces.contains(slot + "IModModule"), "Slot '" + slot + "' has its own class-48 IModModule port");
                int references = 0;
                for (MethodNode m : c.methods) for (AbstractInsnNode x : m.instructions) {
                    String owner = x instanceof MethodInsnNode ? ((MethodInsnNode)x).owner
                            : x instanceof FieldInsnNode ? ((FieldInsnNode)x).owner
                            : x instanceof TypeInsnNode ? ((TypeInsnNode)x).desc : null;
                    if (owner == null || owner.startsWith("java/") || owner.startsWith("javax/") || owner.startsWith("lib/")) continue;
                    boolean inSlot = slot.isEmpty() ? !owner.startsWith("b/") && !owner.startsWith("c/") : owner.startsWith(slot);
                    if (!inSlot) throw new AssertionError("Slot '" + slot + "' port references " + owner);
                    references++;
                }
                check(references > 50, "Slot '" + slot + "' port references only its own slot classes (" + references + ")");
                check(calls(node(jar, slot + cast), "codexSpecialSend", port, "specialTargets") == 1, "Slot '" + slot + "' Special send uses its own specialTargets");
                ClassNode autoBridge = node(jar, slot + "luna/generated/HungAutoAttackAccess");
                // The Auto PK x1 removed the Tấn công quái thường row; Special now always uses that mode.
                boolean autoPk = autoBridge.interfaces.contains(slot + "luna/api/AutoPkPriorityAccess");
                check(calls(autoBridge, "showMenu", slot + "luna/features/AutoAttackRuntime", "normalLabel") == (autoPk ? 0 : 1),
                        "Slot '" + slot + "' Auto đánh menu " + (autoPk ? "has no" : "shows its own") + " normal-monsters row");
                check(calls(node(jar, slot + n("lliiiiil")), n("liliilll"), port, "addConfigRows") == 1, "Slot '" + slot + "' Cài Đặt Cấu Hình has the Lửa Thiêng row");
                check(calls(node(jar, slot + n("lillllll")), n("lilll"), port, "onDialog") == 1, "Slot '" + slot + "' dialog filter calls its own port");
                check(calls(node(jar, slot + n("lliillll")), "<init>", port, "register") == 1, "Slot '" + slot + "' module loader registers its own port");
                if (jar.getJarEntry(slot + "HungBySonSkillStats.class") == null) continue;
                lockPacing = true;
                String stats = slot + "HungBySonSkillStats";
                check(calls(node(jar, slot + "com/silverknight/a"), "run", port, "frame") == 1 && calls(node(jar, slot + "com/silverknight/a"), "run", port, "frameSleep") == 2,
                        "Slot '" + slot + "' canvas loop runs its own attack frame");
                check(calls(node(jar, slot + n("lilillll")), null, stats, "sent") == 3 && calls(node(jar, slot + n("lllllll")), null, stats, "combat") == 1
                                && calls(node(jar, slot + n("lllllll")), n("liilll"), stats, "buff") == 1,
                        "Slot '" + slot + "' serializers (9, 6, buff 40), combat and buff answers feed its own counters");
                check(calls(node(jar, slot + "luna/generated/ExpInfoBridge"), "open", stats, "expText") == 1
                        && calls(node(jar, slot + n("liilllii")), null, stats, "paint") == 1, "Slot '" + slot + "' EXP dialog tabs are its own");
                check(jar.getJarEntry(slot + "luna/features/SkillCastLog.class") == null, "Slot '" + slot + "' has no file log");
            }
        } finally { jar.close(); }

        URLClassLoader loader = new URLClassLoader(new URL[]{candidate.toURI().toURL(), new File(args[1]).toURI().toURL()}, null);
        Object[] controllers = new Object[3];
        for (int i = 0; i < 3; i++) controllers[i] = controller(loader, SLOTS[i].replace('/', '.'));
        Method active = controllers[1].getClass().getMethod("normalMonstersActive");
        // Tấn công quái thường defaults ON in lock-pacing builds: Tấn công đặc biệt then starts it.
        boolean defaultOn = ((String)controllers[1].getClass().getMethod("normalLabel").invoke(controllers[1])).endsWith("[BẬT]");
        controllers[1].getClass().getMethod("handleAction", Integer.TYPE).invoke(controllers[1], Integer.valueOf(defaultOn ? -116 : -117));
        check(((Boolean)active.invoke(controllers[1])).booleanValue()
                        && !((Boolean)controllers[0].getClass().getMethod("normalMonstersActive").invoke(controllers[0])).booleanValue()
                        && !((Boolean)controllers[2].getClass().getMethod("normalMonstersActive").invoke(controllers[2])).booleanValue(),
                "Tấn công quái thường in slot 2 does not touch slots 1 and 3");

        Class<?>[] ports = new Class<?>[3];
        for (int i = 0; i < 3; i++) ports[i] = Class.forName(SLOTS[i].replace('/', '.') + "HungBySonModUpLv", true, loader);
        Field maze = ports[2].getDeclaredField("maze"); maze.setAccessible(true); maze.setBoolean(null, true);
        Method dialog0 = ports[0].getMethod("onDialog", String.class), dialog2 = ports[2].getMethod("onDialog", String.class);
        check(Boolean.TRUE.equals(dialog2.invoke(null, "Xin chờ 3 giây")) && Boolean.FALSE.equals(dialog0.invoke(null, "Xin chờ 3 giây")),
                "Auto Mê cung state is per slot (slot 3 closes 'Xin chờ', slot 1 does not)");
        if (hasField(ports[0], "entryRefusals")) dailyLimitPerSlot(ports);
        if (lockPacing) lockPerSlot(loader);
        System.out.println("HungBySonModUpLvX3HostTest PASS checks=" + checks);
    }

    static boolean lockPacing;

    static boolean hasField(Class<?> c, String name) {
        try { c.getDeclaredField(name); return true; } catch (NoSuchFieldException e) { return false; }
    }

    static boolean flag(Class<?> port, String name) throws Exception {
        Field f = port.getDeclaredField(name); f.setAccessible(true); return f.getBoolean(null);
    }

    /** The Ngã Tư daily limit, sent with combining tone marks, stops entry only in the slot that saw it. */
    static void dailyLimitPerSlot(Class<?>[] ports) throws Exception {
        for (Class<?> port : ports) for (String name : new String[]{"gem", "finish"}) {
            Field f = port.getDeclaredField(name); f.setAccessible(true); f.setBoolean(null, true);
        }
        String limit = java.text.Normalizer.normalize("Bạn chỉ có thể vào Ngã Tư Tử Thần 10 lần 1 ngày. Nếu muốn vào tiếp bạn phải có vé vào phó bản",
                java.text.Normalizer.Form.NFD);
        ports[1].getMethod("onDialog", String.class).invoke(null, limit);
        check(!flag(ports[1], "gem") && !flag(ports[1], "finish"), "Slot 2 stops Ngã Tư entry on the daily-limit dialog");
        check(flag(ports[0], "gem") && flag(ports[0], "finish") && flag(ports[2], "gem") && flag(ports[2], "finish"),
                "Slots 1 and 3 keep their Ngã Tư entry");
    }

    /** Each slot's SkillCastStats keeps its own counters, pacing mode and lock. */
    static void lockPerSlot(ClassLoader loader) throws Exception {
        Class<?>[] stats = new Class<?>[3];
        for (int i = 0; i < 3; i++) stats[i] = Class.forName(SLOTS[i].replace('/', '.') + "luna.features.SkillCastStats", true, loader);
        check(stats[0] != stats[1] && stats[1] != stats[2], "Three separate SkillCastStats classes");
        for (int i = 0; i < 3; i++)
            check("Auto".equals(stats[i].getMethod("paceLabel", Integer.TYPE).invoke(null, stats[i].getMethod("paceMode").invoke(null)))
                    && ((Boolean)stats[i].getMethod("retryEnabled").invoke(null)).booleanValue(), "Slot " + (i + 1) + " defaults: Auto, Tung lại Bật");
        stats[1].getMethod("setPaceMode", Integer.TYPE).invoke(null, Integer.valueOf(0));
        check(((Integer)stats[1].getMethod("paceMode").invoke(null)).intValue() == 0 && ((Integer)stats[0].getMethod("paceMode").invoke(null)).intValue() == 8
                && ((Integer)stats[2].getMethod("paceMode").invoke(null)).intValue() == 8, "Pacing Tắt in slot 2 leaves slots 1 and 3 on Auto");
        Method sent = stats[2].getMethod("sent", Integer.TYPE, Long.TYPE), ready = stats[0].getMethod("paceReady", Long.TYPE);
        sent.invoke(null, Integer.valueOf(8), Long.valueOf(1000L));
        check(((Integer)stats[2].getMethod("casts", Integer.TYPE).invoke(null, Integer.valueOf(8))).intValue() == 1
                && ((Integer)stats[0].getMethod("casts", Integer.TYPE).invoke(null, Integer.valueOf(8))).intValue() == 0
                && !((Boolean)stats[2].getMethod("paceReady", Long.TYPE).invoke(null, Long.valueOf(1100L))).booleanValue()
                && ((Boolean)ready.invoke(null, Long.valueOf(1100L))).booleanValue(), "A cast in slot 3 counts and locks only slot 3");
        stats[2].getMethod("answered", Integer.TYPE, Integer.TYPE, Integer.TYPE, Long.TYPE).invoke(null, Integer.valueOf(8), Integer.valueOf(1), Integer.valueOf(1), Long.valueOf(1020L));
        int[] st = (int[])stats[2].getMethod("autoState").invoke(null);
        long end = 1000L + st[0] + (st[1] < 0 ? -15 : 15);
        check(!((Boolean)stats[2].getMethod("paceReady", Long.TYPE).invoke(null, Long.valueOf(end - 1))).booleanValue()
                && ((Boolean)stats[2].getMethod("paceReady", Long.TYPE).invoke(null, Long.valueOf(end))).booleanValue()
                && ((Long)stats[0].getMethod("paceWait", Long.TYPE).invoke(null, Long.valueOf(1100L))).longValue() == -1L,
                "Auto in slot 3: low ping, ready G after the send (" + (end - 1000L) + "ms); slot 1 has no lock");
        stats[2].getMethod("setPaceMode", Integer.TYPE).invoke(null, Integer.valueOf(7));
        check("Khóa 700ms".equals(stats[2].getMethod("paceLabel", Integer.TYPE).invoke(null, stats[2].getMethod("paceMode").invoke(null)))
                && ((Long)stats[2].getMethod("lockMs").invoke(null)).longValue() == 700L
                && ((Integer)stats[0].getMethod("paceMode").invoke(null)).intValue() == 8, "Khóa 700ms in slot 3 leaves slot 1 on Auto");
        stats[2].getMethod("cyclePace").invoke(null);
        check(((Integer)stats[2].getMethod("paceMode").invoke(null)).intValue() == 8, "Slot 3 cycles Khóa 700ms -> Auto");
    }

    static Object controller(ClassLoader loader, String pkg) throws Exception {
        Class<?> accessType = loader.loadClass(pkg + "luna.api.AutoAttackAccess");
        Class<?> settingsType = loader.loadClass(pkg + "luna.api.AutoAttackSettings");
        Class<?> clockType = loader.loadClass(pkg + "luna.api.LunaClock");
        Class<?> controllerType = loader.loadClass(pkg + "luna.features.AutoAttackController");
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] a) {
                if (method.getName().equals("load")) { int[] v = (int[])a[0]; v[0] = 0; v[1] = 650; return null; }
                Class<?> r = method.getReturnType();
                if (r == Boolean.TYPE) return Boolean.FALSE;
                if (r == Integer.TYPE) return Integer.valueOf(0);
                if (r == Long.TYPE) return Long.valueOf(1000L);
                return null;
            }
        };
        Object access = Proxy.newProxyInstance(loader, new Class<?>[]{accessType}, handler);
        Object settings = Proxy.newProxyInstance(loader, new Class<?>[]{settingsType}, handler);
        Object clock = Proxy.newProxyInstance(loader, new Class<?>[]{clockType}, handler);
        return controllerType.getConstructor(accessType, settingsType, clockType).newInstance(access, settings, clock);
    }
}
