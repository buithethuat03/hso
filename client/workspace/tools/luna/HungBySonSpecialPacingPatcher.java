import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Special sends keep a real-time minimum gap (controller) independent of game speed, and
 * Farm-origin Special sends no longer require the world screen with no dialog open, like
 * native Farm casts. Otherwise a UI (guild member list) blocks every send and Farm's
 * 400-tick no-attack counter uses a village scroll.
 */
public final class HungBySonSpecialPacingPatcher implements Opcodes {
    static final String INPUT = "588B9F9370AFB4E3589E7BB3D794DA9E2564383F10B57351515E3E984A18EE0E";
    static final String CATALOG = "build/hung-farm-spot-20261023-b/farm-special-attack.properties";
    static final String B = "luna/generated/HungAutoAttackAccess", CTRL = "luna/features/AutoAttackController";
    static Properties p = new Properties();

    static void check(boolean value, String why) { if (!value) throw new IllegalStateException(why); }
    static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new IllegalStateException("Missing " + c.name + "." + name + desc);
    }
    static List<AbstractInsnNode> real(MethodNode m) {
        List<AbstractInsnNode> list = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode n : m.instructions) if (n.getOpcode() >= 0) list.add(n);
        return list;
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 5, "input support output catalog report");
        File input = new File(args[0]), support = new File(args[1]), output = new File(args[2]);
        check(HungBySonExpInfoPack.hash(input).equals(INPUT), "Pinned Farm spot release");
        check(!output.exists() && !new File(args[3]).exists() && !new File(args[4]).exists(), "Fresh outputs");
        InputStream cfg = new FileInputStream(CATALOG);
        try { p.load(cfg); } finally { cfg.close(); }
        check(INPUT.equals(p.getProperty("output.sha256")), "Catalog belongs to the pinned input");
        Map<String,byte[]> before = HungBySonExpInfoPack.read(input);
        Map<String,byte[]> out = new LinkedHashMap<String,byte[]>(before);
        ClassNode bridge = HungBySonExpInfoPack.node(before.get(B + ".class"));
        ClassNode oldBridge = HungBySonExpInfoPack.node(before.get(B + ".class"));

        // farmWorldReady(): the native ready() minus text input, world-screen and dialog checks.
        MethodNode ready = method(bridge, "ready", "()Z");
        MethodNode farmReady = new MethodNode(ACC_PUBLIC | ACC_STATIC, "farmWorldReady", "()Z", null, null);
        ready.accept(farmReady);
        farmReady.access = ACC_PUBLIC | ACC_STATIC; farmReady.name = "farmWorldReady";
        for (AbstractInsnNode n : farmReady.instructions) check(!(n instanceof VarInsnNode), "ready() must not use locals or this");
        List<AbstractInsnNode> code = real(farmReady);
        String[][] drops = {{"GETSTATIC", "editing", "IFNE"}, {"INVOKESTATIC", "gameplayScreen", "IFEQ"}, {"GETSTATIC", null, "IFNONNULL"}};
        int removed = 0;
        for (int i = 0; i + 1 < code.size(); i++) {
            AbstractInsnNode a = code.get(i), b = code.get(i + 1);
            boolean edit = a.getOpcode() == GETSTATIC && ((FieldInsnNode)a).name.equals("editing") && b.getOpcode() == IFNE;
            boolean screen = a.getOpcode() == INVOKESTATIC && ((MethodInsnNode)a).name.equals("gameplayScreen") && b.getOpcode() == IFEQ;
            boolean dialog = i == 6 && a.getOpcode() == GETSTATIC && b.getOpcode() == IFNONNULL;
            if (edit || screen || dialog) { farmReady.instructions.remove(a); farmReady.instructions.remove(b); removed++; i++; }
        }
        check(removed == 3, "Exactly the input, screen and dialog checks removed: " + removed);
        check(real(farmReady).size() == real(ready).size() - 6, "farmWorldReady keeps every world/player/connection check");
        bridge.methods.add(farmReady);
        p.setProperty("special.farm.ready.method", "farmWorldReady");

        MethodNode gate = method(bridge, "specialRequestAllowed", "(L" + p.getProperty("cast.owner") + ";)Z");
        MethodInsnNode readyCall = null;
        for (AbstractInsnNode n : gate.instructions)
            if (n instanceof MethodInsnNode && ((MethodInsnNode)n).owner.equals(B) && ((MethodInsnNode)n).name.equals("isWorldReadyStatic")) {
                check(readyCall == null, "Single Farm readiness call"); readyCall = (MethodInsnNode)n;
            }
        check(readyCall != null, "Farm readiness call in the Special request gate");
        readyCall.name = "farmWorldReady";

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); bridge.accept(writer);
        out.put(B + ".class", writer.toByteArray());
        // Inverse: restoring the one call name leaves every old method canonical.
        readyCall.name = "isWorldReadyStatic";
        for (MethodNode m : oldBridge.methods) check(HungBySonExpInfoPack.canonical(m).equals(
                HungBySonExpInfoPack.canonical(method(bridge, m.name, m.desc))), "Bridge inverse " + m.name);
        check(bridge.methods.size() == oldBridge.methods.size() + 1 && bridge.fields.size() == oldBridge.fields.size(), "Bridge allowlist");

        byte[] controller = Files.readAllBytes(new File(support, CTRL + ".class").toPath());
        HungBySonExpInfoPack.screen(CTRL + ".class", controller);
        ClassNode oldCtrl = HungBySonExpInfoPack.node(before.get(CTRL + ".class")), newCtrl = HungBySonExpInfoPack.node(controller);
        for (MethodNode m : oldCtrl.methods) if (!(m.name + m.desc).equals("chooseSpecial(Lluna/api/SpecialAttackAccess;Z)Z"))
            check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(newCtrl, m.name, m.desc))), "Unrelated controller method " + m.name);
        check(oldCtrl.methods.size() == newCtrl.methods.size() && oldCtrl.version == newCtrl.version, "Controller topology");
        out.put(CTRL + ".class", controller);

        List<String> changed = new ArrayList<String>(); int classes = 0, resources = 0;
        for (String name : before.keySet()) {
            if (!Arrays.equals(before.get(name), out.get(name))) {
                check(name.equals(B + ".class") || name.equals(CTRL + ".class"), "Archive allowlist " + name); changed.add(name);
            }
            if (name.endsWith(".class")) {
                classes++;
                check(HungBySonExpInfoPack.node(before.get(name)).version == HungBySonExpInfoPack.node(out.get(name)).version, "Class version " + name);
            } else resources++;
        }
        check(changed.size() == 2 && out.size() == before.size(), "Exactly bridge and controller change");
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String,byte[]> e : out.entrySet()) {
                JarEntry z = new JarEntry(e.getKey()); z.setTime(0); jar.putNextEntry(z); jar.write(e.getValue()); jar.closeEntry();
            }
        } finally { jar.close(); }
        p.setProperty("input.sha256", INPUT); p.setProperty("output.sha256", HungBySonExpInfoPack.hash(output));
        p.setProperty("special.min.gap.ms", "28"); p.setProperty("changed.classes", String.join(",", changed));
        p.setProperty("native.inverse", "PASS");
        OutputStream config = new FileOutputStream(args[3]);
        try { p.store(config, "Special real-time gap; Farm Special sends with UI screens/dialogs open"); } finally { config.close(); }
        PrintWriter log = new PrintWriter(args[4], "UTF-8");
        log.println("PASS pinned input; bridge inverse; only chooseSpecial changed in controller; CLDC screen");
        log.println("CHANGED=" + changed); log.println("CLASSES=" + classes + " UNCHANGED_RESOURCES=" + resources);
        log.println("SHA256=" + p.getProperty("output.sha256")); log.close();
        System.out.println("SPECIAL_PACING_PATCH=PASS SHA256=" + p.getProperty("output.sha256"));
    }
}
