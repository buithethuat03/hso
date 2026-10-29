import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Farm spot stuck policy: real-time stuck window instead of Farm's tick counter, refused
 * target rotation, ghost priority and stuck local cast cancellation. Adds one bridge method.
 */
public final class HungBySonFarmSpotGuardPatcher implements Opcodes {
    static final String INPUT = "50558BCB847E7FCE5028CC6258C7BB230EDD06C2BB7EDAF56FB85009C49C20FA";
    static final String CATALOG = "build/hung-special-pacing-20261024-a/farm-special-attack.properties";
    static final String B = "luna/generated/HungAutoAttackAccess", CTRL = "luna/features/AutoAttackController",
            V = "luna/api/FarmSpotAccess";
    static Properties p = new Properties();

    static void check(boolean value, String why) { if (!value) throw new IllegalStateException(why); }
    static MethodNode find(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        return null;
    }
    static MethodNode method(ClassNode c, String name, String desc) {
        MethodNode m = find(c, name, desc); check(m != null, "Missing " + c.name + "." + name + desc); return m;
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 5, "input support output catalog report");
        File input = new File(args[0]), support = new File(args[1]), output = new File(args[2]);
        check(HungBySonExpInfoPack.hash(input).equals(INPUT), "Pinned 28ms pacing release");
        check(!output.exists() && !new File(args[3]).exists() && !new File(args[4]).exists(), "Fresh outputs");
        InputStream cfg = new FileInputStream(CATALOG);
        try { p.load(cfg); } finally { cfg.close(); }
        check(INPUT.equals(p.getProperty("output.sha256")), "Catalog belongs to the pinned input");
        Map<String,byte[]> before = HungBySonExpInfoPack.read(input);
        Map<String,byte[]> out = new LinkedHashMap<String,byte[]>(before);

        ClassNode bridge = HungBySonExpInfoPack.node(before.get(B + ".class"));
        ClassNode oldBridge = HungBySonExpInfoPack.node(before.get(B + ".class"));
        check(bridge.interfaces.contains(V), "Bridge already implements FarmSpotAccess");
        method(bridge, "cancelLegacyAuto", "(Z)V");
        check(find(bridge, "farmSpotCancelCast", "()V") == null, "Fresh cancel method");
        // cancelLegacyAuto(true); then whatever tag the local cast has, clear it and the actor state.
        String E = p.getProperty("entity.owner"), C = p.getProperty("cast.owner");
        FieldInsnNode singleton = null;
        for (AbstractInsnNode n : method(bridge, "player", "()Ljava/lang/Object;").instructions)
            if (n instanceof FieldInsnNode && n.getOpcode() == GETSTATIC) singleton = (FieldInsnNode)n;
        check(singleton != null && E != null && C != null, "Native local player and cast owners");
        method(bridge, "cancelCast", "(L" + C + ";)V");
        MethodNode cancel = new MethodNode(ACC_PUBLIC, "farmSpotCancelCast", "()V", null, null);
        InsnList l = cancel.instructions; LabelNode done = new LabelNode(), noCast = new LabelNode(), tagged = new LabelNode(),
                noPending = new LabelNode(), noAttack = new LabelNode();
        l.add(new InsnNode(ICONST_1)); l.add(new MethodInsnNode(INVOKESTATIC, B, "cancelLegacyAuto", "(Z)V", false));
        l.add(new FieldInsnNode(GETSTATIC, singleton.owner, singleton.name, singleton.desc)); l.add(new VarInsnNode(ASTORE, 1));
        l.add(new VarInsnNode(ALOAD, 1)); l.add(new JumpInsnNode(IFNULL, done));
        l.add(new VarInsnNode(ALOAD, 1)); l.add(new FieldInsnNode(GETFIELD, E, "di", "L" + C + ";")); l.add(new VarInsnNode(ASTORE, 2));
        l.add(new VarInsnNode(ALOAD, 2)); l.add(new JumpInsnNode(IFNULL, noCast));
        l.add(new VarInsnNode(ALOAD, 2)); l.add(new FieldInsnNode(GETFIELD, C, "codexAutoAttackGeneration", "I")); l.add(new JumpInsnNode(IFNE, tagged));
        l.add(new VarInsnNode(ALOAD, 2)); l.add(new InsnNode(ICONST_M1)); l.add(new FieldInsnNode(PUTFIELD, C, "codexAutoAttackGeneration", "I"));
        l.add(tagged);
        l.add(new VarInsnNode(ALOAD, 2)); l.add(new MethodInsnNode(INVOKESTATIC, B, "cancelCast", "(L" + C + ";)V", false));
        l.add(new VarInsnNode(ALOAD, 2)); l.add(new InsnNode(ICONST_0)); l.add(new FieldInsnNode(PUTFIELD, C, "codexWaitRealTime", "Z"));
        l.add(noCast);
        l.add(new VarInsnNode(ALOAD, 1)); l.add(new FieldInsnNode(GETFIELD, E, "cA", "I")); l.add(new JumpInsnNode(IFLT, noPending));
        l.add(new VarInsnNode(ALOAD, 1)); l.add(new InsnNode(ICONST_M1)); l.add(new FieldInsnNode(PUTFIELD, E, "cA", "I"));
        l.add(noPending);
        l.add(new VarInsnNode(ALOAD, 1)); l.add(new FieldInsnNode(GETFIELD, E, "cG", "I")); l.add(new InsnNode(ICONST_2)); l.add(new JumpInsnNode(IF_ICMPNE, noAttack));
        l.add(new VarInsnNode(ALOAD, 1)); l.add(new InsnNode(ICONST_0)); l.add(new FieldInsnNode(PUTFIELD, E, "cG", "I"));
        l.add(noAttack); l.add(done); l.add(new InsnNode(RETURN));
        bridge.methods.add(cancel);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); bridge.accept(writer);
        out.put(B + ".class", writer.toByteArray());
        for (MethodNode m : oldBridge.methods) check(HungBySonExpInfoPack.canonical(m).equals(
                HungBySonExpInfoPack.canonical(method(bridge, m.name, m.desc))), "Bridge inverse " + m.name);
        check(bridge.methods.size() == oldBridge.methods.size() + 1, "Bridge allowlist");

        String view = "(L" + V + ";";
        Set<String> changedMethods = new HashSet<String>(Arrays.asList("chooseSpecial(Lluna/api/SpecialAttackAccess;Z)Z",
                "farmSpot" + view + ")Z", "handleAction(I)Z",
                "<init>(Lluna/api/AutoAttackAccess;Lluna/api/AutoAttackSettings;Lluna/api/LunaClock;)V", "farmSpotFind" + view + ")Ljava/lang/Object;"));
        Set<String> addedMethods = new HashSet<String>(Arrays.asList("farmSpotFind" + view + "J)Ljava/lang/Object;",
                "farmProgress" + view + "J)V", "farmKeepAlive" + view + "J)V"));
        byte[] controller = Files.readAllBytes(new File(support, CTRL + ".class").toPath());
        HungBySonExpInfoPack.screen(CTRL + ".class", controller);
        ClassNode oldCtrl = HungBySonExpInfoPack.node(before.get(CTRL + ".class")), newCtrl = HungBySonExpInfoPack.node(controller);
        for (MethodNode m : oldCtrl.methods) if (!changedMethods.contains(m.name + m.desc))
            check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(newCtrl, m.name, m.desc))),
                    "Unrelated controller method " + m.name);
        for (MethodNode m : newCtrl.methods) if (find(oldCtrl, m.name, m.desc) == null)
            check(addedMethods.contains(m.name + m.desc) || m.name.equals("<clinit>"), "Unexpected controller method " + m.name + m.desc);
        check(oldCtrl.version == newCtrl.version, "Controller version");
        out.put(CTRL + ".class", controller);

        byte[] api = Files.readAllBytes(new File(support, V + ".class").toPath());
        HungBySonExpInfoPack.screen(V + ".class", api);
        ClassNode oldApi = HungBySonExpInfoPack.node(before.get(V + ".class")), newApi = HungBySonExpInfoPack.node(api);
        for (MethodNode m : oldApi.methods) method(newApi, m.name, m.desc);
        check(newApi.methods.size() == oldApi.methods.size() + 1 && find(newApi, "farmSpotCancelCast", "()V") != null, "Interface adds only cancel");
        out.put(V + ".class", api);

        Set<String> allowed = new HashSet<String>(Arrays.asList(B + ".class", CTRL + ".class", V + ".class"));
        List<String> changed = new ArrayList<String>(); int classes = 0, resources = 0;
        for (String name : before.keySet()) {
            if (!Arrays.equals(before.get(name), out.get(name))) { check(allowed.contains(name), "Archive allowlist " + name); changed.add(name); }
            if (name.endsWith(".class")) {
                classes++;
                check(HungBySonExpInfoPack.node(before.get(name)).version == HungBySonExpInfoPack.node(out.get(name)).version, "Class version " + name);
            } else resources++;
        }
        check(changed.size() == 3 && out.size() == before.size(), "Exactly bridge, controller and interface change");
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String,byte[]> e : out.entrySet()) {
                JarEntry z = new JarEntry(e.getKey()); z.setTime(0); jar.putNextEntry(z); jar.write(e.getValue()); jar.closeEntry();
            }
        } finally { jar.close(); }
        p.setProperty("input.sha256", INPUT); p.setProperty("output.sha256", HungBySonExpInfoPack.hash(output));
        p.setProperty("farm.spot.stuck.ms", "15000"); p.setProperty("farm.spot.busy.cancel.ms", "1500");
        p.setProperty("farm.spot.guard", "true"); p.setProperty("changed.classes", String.join(",", changed));
        p.setProperty("native.inverse", "PASS");
        OutputStream config = new FileOutputStream(args[3]);
        try { p.store(config, "Farm spot real-time stuck window, target rotation, ghost priority, stuck cast cancel"); } finally { config.close(); }
        PrintWriter log = new PrintWriter(args[4], "UTF-8");
        log.println("PASS pinned input; bridge inverse +1 method; reviewed controller/interface changes; CLDC screen");
        log.println("CHANGED=" + changed); log.println("CLASSES=" + classes + " UNCHANGED_RESOURCES=" + resources);
        log.println("SHA256=" + p.getProperty("output.sha256")); log.close();
        System.out.println("FARM_SPOT_GUARD_PATCH=PASS SHA256=" + p.getProperty("output.sha256"));
    }
}
