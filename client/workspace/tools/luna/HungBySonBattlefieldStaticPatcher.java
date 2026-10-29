import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Battlefield mode: the base entity no-arg constructor no longer captures a (0,0) position.
 * NPCs (zone sign "Khu"), dropped items and other static objects are built through it and
 * positioned afterwards, so align() snapped them to the map corner. Objects with server
 * position packets keep their receive-hook captures. Also sets the Special gap to 30ms.
 */
public final class HungBySonBattlefieldStaticPatcher implements Opcodes {
    static final String INPUT = "8DF9215EDBD99E17ED008568E44D9A724BF871C21F8919029D01B48AF3DAC152";
    static final String CATALOG = "build/hung-farm-spot-guard-20261024-a/farm-special-attack.properties";
    static final String BF = "luna/generated/BattlefieldBridge", CTRL = "luna/features/AutoAttackController";
    static Properties p = new Properties();

    static void check(boolean value, String why) { if (!value) throw new IllegalStateException(why); }
    static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new IllegalStateException("Missing " + c.name + "." + name + desc);
    }
    static boolean capture(AbstractInsnNode n, String e) {
        return n instanceof MethodInsnNode && n.getOpcode() == INVOKESTATIC && ((MethodInsnNode)n).owner.equals(BF)
                && ((MethodInsnNode)n).name.equals("capturePosition") && ((MethodInsnNode)n).desc.equals("(L" + e + ";)V");
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 5, "input support output catalog report");
        File input = new File(args[0]), support = new File(args[1]), output = new File(args[2]);
        check(HungBySonExpInfoPack.hash(input).equals(INPUT), "Pinned Farm spot guard release");
        check(!output.exists() && !new File(args[3]).exists() && !new File(args[4]).exists(), "Fresh outputs");
        InputStream cfg = new FileInputStream(CATALOG);
        try { p.load(cfg); } finally { cfg.close(); }
        check(INPUT.equals(p.getProperty("output.sha256")), "Catalog belongs to the pinned input");
        String E = p.getProperty("entity.owner");
        Map<String,byte[]> before = HungBySonExpInfoPack.read(input);
        Map<String,byte[]> out = new LinkedHashMap<String,byte[]>(before);

        ClassNode entity = HungBySonExpInfoPack.node(before.get(E + ".class"));
        ClassNode oldEntity = HungBySonExpInfoPack.node(before.get(E + ".class"));
        MethodNode ctor = method(entity, "<init>", "()V");
        List<AbstractInsnNode> captures = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode n : ctor.instructions) if (capture(n, E)) captures.add(n);
        check(captures.size() == 1, "One capture in the no-arg constructor");
        AbstractInsnNode call = captures.get(0), self = call.getPrevious();
        while (self.getOpcode() < 0) self = self.getPrevious();
        check(self.getOpcode() == ALOAD && ((VarInsnNode)self).var == 0, "Capture of this");
        AbstractInsnNode next = call.getNext();
        while (next.getOpcode() < 0) next = next.getNext();
        check(next.getOpcode() == RETURN, "Capture is the constructor's last action");
        int otherCaptures = 0;
        for (MethodNode m : entity.methods) if (m != ctor) for (AbstractInsnNode n : m.instructions) if (capture(n, E)) otherCaptures++;
        check(otherCaptures >= 1, "Positioned constructor keeps its capture");
        ctor.instructions.remove(self); ctor.instructions.remove(call);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); entity.accept(writer);
        out.put(E + ".class", writer.toByteArray());
        // Inverse: only the no-arg constructor differs, by exactly those two instructions.
        for (MethodNode m : oldEntity.methods) {
            MethodNode now = method(entity, m.name, m.desc);
            if (m.name.equals("<init>") && m.desc.equals("()V")) {
                InsnList restore = new InsnList(); restore.add(new VarInsnNode(ALOAD, 0));
                restore.add(new MethodInsnNode(INVOKESTATIC, BF, "capturePosition", "(L" + E + ";)V", false));
                AbstractInsnNode ret = null;
                for (AbstractInsnNode n : now.instructions) if (n.getOpcode() == RETURN) ret = n;
                now.instructions.insertBefore(ret, restore);
            }
            check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(now)), "Entity inverse " + m.name + m.desc);
        }
        check(entity.methods.size() == oldEntity.methods.size() && entity.fields.size() == oldEntity.fields.size(), "Entity topology");

        byte[] controller = Files.readAllBytes(new File(support, CTRL + ".class").toPath());
        HungBySonExpInfoPack.screen(CTRL + ".class", controller);
        ClassNode oldCtrl = HungBySonExpInfoPack.node(before.get(CTRL + ".class")), newCtrl = HungBySonExpInfoPack.node(controller);
        for (MethodNode m : oldCtrl.methods) if (!(m.name + m.desc).equals("chooseSpecial(Lluna/api/SpecialAttackAccess;Z)Z"))
            check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(newCtrl, m.name, m.desc))), "Controller method " + m.name);
        check(oldCtrl.methods.size() == newCtrl.methods.size() && oldCtrl.fields.size() == newCtrl.fields.size(), "Controller topology");
        for (int i = 0; i < oldCtrl.fields.size(); i++) {
            FieldNode a = oldCtrl.fields.get(i), b = newCtrl.fields.get(i);
            check(a.name.equals(b.name) && a.desc.equals(b.desc), "Controller field " + a.name);
            if (a.name.equals("SPECIAL_MIN_GAP_MS")) check(Integer.valueOf(28).equals(a.value) && Integer.valueOf(30).equals(b.value), "Gap 28 -> 30");
            else check(Objects.equals(a.value, b.value), "Controller constant " + a.name);
        }
        out.put(CTRL + ".class", controller);

        List<String> changed = new ArrayList<String>(); int classes = 0, resources = 0;
        for (String name : before.keySet()) {
            if (!Arrays.equals(before.get(name), out.get(name))) {
                check(name.equals(E + ".class") || name.equals(CTRL + ".class"), "Archive allowlist " + name); changed.add(name);
            }
            if (name.endsWith(".class")) {
                classes++;
                check(HungBySonExpInfoPack.node(before.get(name)).version == HungBySonExpInfoPack.node(out.get(name)).version, "Class version " + name);
            } else resources++;
        }
        check(changed.size() == 2 && out.size() == before.size(), "Exactly entity and controller change");
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String,byte[]> e : out.entrySet()) {
                JarEntry z = new JarEntry(e.getKey()); z.setTime(0); jar.putNextEntry(z); jar.write(e.getValue()); jar.closeEntry();
            }
        } finally { jar.close(); }
        p.setProperty("input.sha256", INPUT); p.setProperty("output.sha256", HungBySonExpInfoPack.hash(output));
        p.setProperty("special.min.gap.ms", "30"); p.setProperty("battlefield.static.objects.native", "true");
        p.setProperty("changed.classes", String.join(",", changed)); p.setProperty("native.inverse", "PASS");
        OutputStream config = new FileOutputStream(args[3]);
        try { p.store(config, "Battlefield keeps static objects native; Special gap 30ms"); } finally { config.close(); }
        PrintWriter log = new PrintWriter(args[4], "UTF-8");
        log.println("PASS pinned input; entity no-arg constructor capture removed with exact inverse; controller gap 28->30 only");
        log.println("CHANGED=" + changed); log.println("CLASSES=" + classes + " UNCHANGED_RESOURCES=" + resources);
        log.println("SHA256=" + p.getProperty("output.sha256")); log.close();
        System.out.println("BATTLEFIELD_STATIC_PATCH=PASS SHA256=" + p.getProperty("output.sha256"));
    }
}
