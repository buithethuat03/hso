import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Baseline-aware checks for both the legacy Luna and semantic Chibi packages. */
public final class ChibiEntryArchiveVerifier {
    static int checks;
    public static void main(String[] args) throws Exception {
        check(args.length == 4, "original.jar patched.jar catalog.properties microemulator.jar");
        Map<String,byte[]> original = ChibiEntryCompatibilityPatcher.read(new File(args[0]));
        Map<String,byte[]> patched = ChibiEntryCompatibilityPatcher.read(new File(args[1]));
        Properties p = new Properties(); Reader r = new InputStreamReader(new FileInputStream(args[2]), "UTF-8");
        try { p.load(r); } finally { r.close(); }
        check(p.getProperty("input.sha256").equals(ChibiEntryCompatibilityPatcher.hash(new File(args[0]))), "Original/catalog hash mismatch");
        check(p.getProperty("output.sha256").equals(ChibiEntryCompatibilityPatcher.hash(new File(args[1]))), "Candidate/catalog hash mismatch");
        Set<String> allowed = new HashSet<String>();
        allowed.add(p.getProperty("login.owner") + ".class"); allowed.add(p.getProperty("protocol.receiver") + ".class");
        check(allowed.size() == 2 && patched.size() == original.size() + 1, "Archive topology mismatch");
        int resources = 0, changed = 0, classes = 0, maximumMajor = 0;
        for (String name : original.keySet()) {
            check(patched.containsKey(name), "Entry removed: " + name);
            boolean same = Arrays.equals(original.get(name), patched.get(name));
            if (!same) { check(allowed.contains(name), "Unrelated entry changed: " + name); changed++; }
            if (!name.endsWith(".class")) { check(same, "Resource or manifest changed: " + name); resources++; }
        }
        check(changed == 2, "Unexpected changed entry count");
        for (String name : patched.keySet()) {
            if (!original.containsKey(name)) check(name.equals(ChibiEntryCompatibilityPatcher.AUTH + ".class"), "Unexpected extra class: " + name);
            if (!name.endsWith(".class")) continue;
            ClassNode c = ChibiEntryCompatibilityPatcher.node(patched.get(name)); classes++;
            int major = c.version & 65535; maximumMajor = Math.max(maximumMajor, major);
            check(major <= 51, "Class exceeds Java 7/J2ME pipeline version");
            if (name.equals(ChibiEntryCompatibilityPatcher.AUTH + ".class")) {
                for (MethodNode m : c.methods) for (AbstractInsnNode n : m.instructions.toArray()) {
                    if (n instanceof MethodInsnNode) {
                        String owner = ((MethodInsnNode)n).owner;
                        check(owner.equals(c.name) || owner.startsWith("java/lang/"), "Unexpected auth runtime dependency " + owner);
                        check(!owner.equals("java/lang/StringBuilder"), "Auth helper introduced StringBuilder");
                    }
                }
            }
        }
        URLClassLoader oldLoader = loader(args[0], args[3]), fixedLoader = loader(args[1], args[3]);
        int routePairs = 0, pathCases = 0;
        try {
            for (String name : patched.keySet()) if (name.endsWith(".class")) {
                Class<?> c = Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false, fixedLoader);
                c.getDeclaredMethods(); c.getDeclaredFields();
            }
            String bridge = original.containsKey("LunaGeneratedLegacyHooks.class") ? "LunaGeneratedLegacyHooks" : "HSOAutoTradeSupport";
            Class<?> old = Class.forName(bridge, true, oldLoader), fixed = Class.forName(bridge, true, fixedLoader);
            Method oldHop = accessible(old, "nextHop", int.class, int.class), fixedHop = accessible(fixed, "nextHop", int.class, int.class);
            check(((Integer)oldHop.invoke(null, 52, 8)).intValue() == 45, "Baseline route profile unexpectedly changed");
            for (int from = -1; from <= 150; from++) for (int to = -1; to <= 150; to++) {
                Object a = oldHop.invoke(null, from, to), b = fixedHop.invoke(null, from, to);
                check(a.equals(b), "Native route changed for " + from + " -> " + to); routePairs++;
            }
            Method oldMap = accessible(old, "mapIdOfName", String.class), fixedMap = accessible(fixed, "mapIdOfName", String.class);
            String[] maps = {"Cổng địa ngục", "Thung Lũng Kỳ Bí", "Cổng vào Hạ giới", "Cánh Đồng Sói", "Thành Phố Kho Báu", "unknown"};
            for (String map : maps) check(oldMap.invoke(null, map).equals(fixedMap.invoke(null, map)), "Native map lookup changed");
            Method oldPath = accessible(old, "straightPath", int.class, int.class, int.class, int.class, int.class);
            Method fixedPath = accessible(fixed, "straightPath", int.class, int.class, int.class, int.class, int.class);
            int[][] paths = {{10,10,20,10,64},{10,10,10,25,64},{10,10,30,30,10},{0,0,0,0,64},{10,20,0,0,64},{100,100,0,0,126}};
            for (int[] path : paths) {
                Object[] values = {path[0],path[1],path[2],path[3],path[4]};
                check(Arrays.equals((short[])oldPath.invoke(null,values),(short[])fixedPath.invoke(null,values)), "Native straight path changed"); pathCases++;
            }
            System.out.println("CHIBI_ENTRY_ARCHIVE=PASS; checks=" + checks + "; classes=" + classes + "; resources=" + resources + "; maximumMajor=" + maximumMajor
                    + "; routeBridge=" + bridge + "; nativeRoutePairs=" + routePairs + "; nativePathCases=" + pathCases + "; allClassesLinked=true; resourceChanges=0");
        } finally { oldLoader.close(); fixedLoader.close(); }
    }
    static URLClassLoader loader(String jar, String micro) throws Exception { return new URLClassLoader(new URL[]{new File(jar).toURI().toURL(),new File(micro).toURI().toURL()}, null); }
    static Method accessible(Class<?> owner, String name, Class<?>... params) throws Exception { Method m = owner.getDeclaredMethod(name, params); m.setAccessible(true); return m; }
    static void check(boolean ok, String message) { checks++; if (!ok) throw new IllegalStateException(message); }
}
