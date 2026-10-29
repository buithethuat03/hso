import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Farm + Special at the saved spot: stand still and attack through the Special chooser,
 * like Auto đánh. Only the two "at spot" calls of Farm's tick are guarded; the native
 * return trip (death, village, map/zone, distance) and its walking stay unchanged.
 */
public final class HungBySonFarmSpotPatcher implements Opcodes {
    static final String INPUT = "FECE5BDA7ADC9909676EEAD8B0C0BF71821B118E94697432422016FD6D15A4D2";
    static final String CATALOG = "build/hung-farm-quiet-recovery-20261023-c/farm-special-attack.properties";
    static final String B = "luna/generated/HungAutoAttackAccess", R = "luna/features/AutoAttackRuntime",
            CTRL = "luna/features/AutoAttackController", V = "luna/api/FarmSpotAccess";
    static final String OBJ = "Ljava/lang/Object;";
    static Properties p = new Properties();
    static String E, P, C, F, T, TF, TD, SENT;

    static void check(boolean value, String why) { if (!value) throw new IllegalStateException(why); }
    static String req(String key) { String value = p.getProperty(key); check(value != null, "Missing " + key); return value; }
    static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new IllegalStateException("Missing " + c.name + "." + name + desc);
    }
    /** Obfuscated members are a run of 'i' followed by a short distinct suffix. */
    static String member(ClassNode c, String suffix, String desc, boolean field) {
        String found = null;
        if (field) { for (FieldNode f : c.fields) if (f.desc.equals(desc) && f.name.matches("i+" + suffix)) { check(found == null, "Ambiguous " + suffix); found = f.name; } }
        else for (MethodNode m : c.methods) if (m.desc.equals(desc) && m.name.matches("i+" + suffix)) { check(found == null, "Ambiguous " + suffix); found = m.name; }
        check(found != null, "Missing member " + c.name + " *" + suffix + desc);
        return found;
    }
    static MethodNode add(ClassNode c, int access, String name, String desc) {
        for (MethodNode m : c.methods) check(!(m.name.equals(name) && m.desc.equals(desc)), "Already present " + name);
        MethodNode m = new MethodNode(access, name, desc, null, null); c.methods.add(m); return m;
    }
    static void op(InsnList l, int code) { l.add(new InsnNode(code)); }
    static void var(InsnList l, int code, int index) { l.add(new VarInsnNode(code, index)); }
    static void field(InsnList l, int code, String owner, String name, String desc) { l.add(new FieldInsnNode(code, owner, name, desc)); }
    static void jump(InsnList l, int code, LabelNode to) { l.add(new JumpInsnNode(code, to)); }
    static void stat(InsnList l, String owner, String name, String desc) { l.add(new MethodInsnNode(INVOKESTATIC, owner, name, desc, false)); }
    static List<AbstractInsnNode> real(MethodNode m) {
        List<AbstractInsnNode> list = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode n : m.instructions) if (n.getOpcode() >= 0) list.add(n);
        return list;
    }
    static boolean calls(AbstractInsnNode n, int code, String owner, String name, String desc) {
        if (!(n instanceof MethodInsnNode) || n.getOpcode() != code) return false;
        MethodInsnNode m = (MethodInsnNode)n;
        return m.owner.equals(owner) && m.name.equals(name) && m.desc.equals(desc);
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 5, "input support output catalog report");
        File input = new File(args[0]), support = new File(args[1]), output = new File(args[2]);
        check(HungBySonExpInfoPack.hash(input).equals(INPUT), "Pinned render-only recovery release");
        check(!output.exists() && !new File(args[3]).exists() && !new File(args[4]).exists(), "Fresh outputs");
        InputStream cfg = new FileInputStream(CATALOG);
        try { p.load(cfg); } finally { cfg.close(); }
        check(INPUT.equals(req("output.sha256")), "Catalog belongs to the pinned input");
        E = req("entity.owner"); P = req("native.player.owner"); C = req("cast.owner");
        F = req("native.farm.enabled.owner"); T = req("native.target.owner"); TF = req("native.target.field");
        SENT = req("special.sent.field");
        Map<String,byte[]> before = HungBySonExpInfoPack.read(input);
        Map<String,byte[]> out = new LinkedHashMap<String,byte[]>(before);
        check(!before.containsKey(V + ".class"), "Fresh Farm spot interface");

        ClassNode bridge = HungBySonExpInfoPack.node(before.get(B + ".class"));
        ClassNode player = HungBySonExpInfoPack.node(before.get(P + ".class"));
        ClassNode farm = HungBySonExpInfoPack.node(before.get(F + ".class"));
        ClassNode oldBridge = HungBySonExpInfoPack.node(before.get(B + ".class"));
        ClassNode oldFarm = HungBySonExpInfoPack.node(before.get(F + ".class"));

        // Native references, discovered from the reviewed bridge rather than guessed.
        FieldInsnNode singleton = null, di = null, targetPut = null, view = null;
        for (AbstractInsnNode n : method(bridge, "player", "()" + OBJ).instructions)
            if (n instanceof FieldInsnNode && n.getOpcode() == GETSTATIC) singleton = (FieldInsnNode)n;
        for (AbstractInsnNode n : method(bridge, "attack", "(" + OBJ + ")Z").instructions) if (n instanceof FieldInsnNode) {
            FieldInsnNode f = (FieldInsnNode)n;
            if (f.getOpcode() == GETFIELD && f.name.equals("di")) di = f;
            if (f.getOpcode() == PUTSTATIC && f.owner.equals(T) && f.name.equals(TF)) targetPut = f;
        }
        for (AbstractInsnNode n : method(bridge, "isWorldReadyStatic", "()Z").instructions)
            if (n instanceof FieldInsnNode && ((FieldInsnNode)n).name.equals("VIEW")) view = (FieldInsnNode)n;
        check(singleton != null && singleton.desc.equals("L" + P + ";"), "Local player singleton");
        check(di != null && di.desc.equals("L" + C + ";"), "Native cast of the local player");
        check(targetPut != null && targetPut.desc.equals("L" + E + ";"), "Native selected target");
        check(view != null && view.desc.equals("L" + B + ";"), "Bridge view singleton");
        for (String[] existing : new String[][]{{"entityCount", "()I"}, {"entityAt", "(I)" + OBJ},
                {"distanceTo", "(" + OBJ + ")I"}, {"combatBusy", "()Z"}}) {
            MethodNode m = method(bridge, existing[0], existing[1]);
            check((m.access & (ACC_PUBLIC | ACC_STATIC)) == ACC_PUBLIC, "Instance view method " + existing[0]);
        }
        method(bridge, "farmSpecialEnabled", "()Z"); method(bridge, "cancelLegacyAuto", "(Z)V");
        method(bridge, "dispatchFarmSpecialChoose", "(L" + P + ";)V");
        method(player, "bachHoMonsterTargetGate", "(" + OBJ + OBJ + ")Z");
        String armed = member(player, "lllilli", "B", true), latch = member(player, "lllliiii", "Z", true),
                retarget = member(player, "lllillli", "Z", true), counter = member(farm, "lilililiiil", "I", true),
                rearm = member(farm, "lliiiiii", "()V", false), castLoop = member(farm, "lliiilil", "()V", false);

        bridge.interfaces.add(V);
        InsnList l; LabelNode no;
        // static boolean farmSpot(): Farm tick at its saved spot; false keeps the native tick.
        l = add(bridge, ACC_PUBLIC | ACC_STATIC, "farmSpot", "()Z").instructions; no = new LabelNode();
        stat(l, B, "farmSpecialEnabled", "()Z"); jump(l, IFEQ, no);
        field(l, GETSTATIC, B, view.name, view.desc); stat(l, R, "farmSpot", "(L" + V + ";)Z"); op(l, IRETURN);
        l.add(no); op(l, ICONST_0); op(l, IRETURN);
        // Living monster accepted by Farm's existing monster-selection gate.
        l = add(bridge, ACC_PUBLIC, "farmSpotTargetValid", "(" + OBJ + ")Z").instructions; no = new LabelNode();
        var(l, ALOAD, 1); l.add(new TypeInsnNode(INSTANCEOF, E)); jump(l, IFEQ, no);
        var(l, ALOAD, 1); l.add(new TypeInsnNode(CHECKCAST, E)); field(l, GETFIELD, E, "bs", "I"); jump(l, IFLE, no);
        var(l, ALOAD, 1); l.add(new TypeInsnNode(CHECKCAST, E)); field(l, GETFIELD, E, "cG", "I"); op(l, ICONST_4); jump(l, IF_ICMPEQ, no);
        var(l, ALOAD, 1); l.add(new TypeInsnNode(CHECKCAST, E)); l.add(new MethodInsnNode(INVOKEVIRTUAL, E, "x", "()Z", false)); jump(l, IFNE, no);
        var(l, ALOAD, 1); field(l, GETSTATIC, singleton.owner, singleton.name, singleton.desc);
        stat(l, P, "bachHoMonsterTargetGate", "(" + OBJ + OBJ + ")Z"); op(l, IRETURN);
        l.add(no); op(l, ICONST_0); op(l, IRETURN);
        // Ghost captcha monster; Farm's own captcha branch handles it once selected.
        l = add(bridge, ACC_PUBLIC, "farmSpotGhost", "(" + OBJ + ")Z").instructions; no = new LabelNode();
        var(l, ALOAD, 1); l.add(new TypeInsnNode(INSTANCEOF, E)); jump(l, IFEQ, no);
        var(l, ALOAD, 1); l.add(new TypeInsnNode(CHECKCAST, E)); field(l, GETFIELD, E, "cy", "B"); op(l, ICONST_2); jump(l, IF_ICMPNE, no);
        op(l, ICONST_1); op(l, IRETURN); l.add(no); op(l, ICONST_0); op(l, IRETURN);
        // Disarm the legacy walking auto like Farm stop, keeping the target and every path.
        l = add(bridge, ACC_PUBLIC, "farmSpotHold", "()V").instructions; no = new LabelNode();
        field(l, GETSTATIC, P, armed, "B"); op(l, ICONST_1); jump(l, IF_ICMPNE, no);
        op(l, ICONST_1); stat(l, B, "cancelLegacyAuto", "(Z)V"); l.add(no);
        op(l, ICONST_M1); field(l, PUTSTATIC, P, armed, "B");
        op(l, ICONST_0); field(l, PUTSTATIC, P, latch, "Z");
        op(l, ICONST_0); field(l, PUTSTATIC, P, retarget, "Z"); op(l, RETURN);
        l = add(bridge, ACC_PUBLIC, "farmSpotSelect", "(" + OBJ + ")V").instructions;
        var(l, ALOAD, 1); l.add(new TypeInsnNode(CHECKCAST, E)); field(l, PUTSTATIC, T, TF, targetPut.desc); op(l, RETURN);
        // Select and run the reviewed Farm Special chooser; report whether it sent.
        l = add(bridge, ACC_PUBLIC, "farmSpotAttack", "(" + OBJ + ")Z").instructions; no = new LabelNode();
        var(l, ALOAD, 0); var(l, ALOAD, 1); l.add(new MethodInsnNode(INVOKEVIRTUAL, B, "farmSpotSelect", "(" + OBJ + ")V", false));
        field(l, GETSTATIC, singleton.owner, singleton.name, singleton.desc); var(l, ASTORE, 2);
        var(l, ALOAD, 2); jump(l, IFNULL, no);
        var(l, ALOAD, 2); field(l, GETFIELD, di.owner, di.name, di.desc); jump(l, IFNULL, no);
        var(l, ALOAD, 2); field(l, GETFIELD, di.owner, di.name, di.desc); op(l, ICONST_0); field(l, PUTFIELD, C, SENT, "Z");
        var(l, ALOAD, 2); stat(l, B, "dispatchFarmSpecialChoose", "(L" + P + ";)V");
        var(l, ALOAD, 2); field(l, GETFIELD, di.owner, di.name, di.desc); field(l, GETFIELD, C, SENT, "Z"); op(l, IRETURN);
        l.add(no); op(l, ICONST_0); op(l, IRETURN);
        l = add(bridge, ACC_PUBLIC, "farmSpotActive", "()V").instructions;
        op(l, ICONST_0); field(l, PUTSTATIC, F, counter, "I"); op(l, RETURN);

        // Guard exactly the two "at spot" sites: stand (rearm + cast loop) and move mode.
        MethodNode update = method(farm, "codexFarmUpdate", "()V");
        List<AbstractInsnNode> code = real(update);
        List<Integer> loops = new ArrayList<Integer>();
        for (int i = 0; i < code.size(); i++) if (calls(code.get(i), INVOKESPECIAL, F, castLoop, "()V")) loops.add(i);
        check(loops.size() == 2, "Two native at-spot cast loops");
        int stand = loops.get(0), move = loops.get(1);
        check(code.get(stand - 1).getOpcode() == ALOAD && ((VarInsnNode)code.get(stand - 1)).var == 0
                && calls(code.get(stand - 2), INVOKESTATIC, F, rearm, "()V")
                && code.get(stand + 1).getOpcode() == RETURN, "Stand branch shape");
        AbstractInsnNode moveRead = code.get(move - 5);
        check(moveRead instanceof FieldInsnNode && moveRead.getOpcode() == GETSTATIC
                && ((FieldInsnNode)moveRead).owner.equals(P) && ((FieldInsnNode)moveRead).name.equals(armed)
                && code.get(move - 4).getOpcode() == ICONST_1 && code.get(move - 3).getOpcode() == IF_ICMPEQ
                && calls(code.get(move - 2), INVOKESTATIC, F, rearm, "()V"), "Move branch shape");
        List<AbstractInsnNode> inserted = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode anchor : new AbstractInsnNode[]{code.get(stand - 2), moveRead}) {
            InsnList guard = new InsnList(); LabelNode nativeTick = new LabelNode();
            stat(guard, B, "farmSpot", "()Z"); jump(guard, IFEQ, nativeTick); op(guard, RETURN); guard.add(nativeTick);
            for (AbstractInsnNode n : guard.toArray()) inserted.add(n);
            update.instructions.insertBefore(anchor, guard);
        }

        for (ClassNode c : new ClassNode[]{bridge, farm}) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(writer); out.put(c.name + ".class", writer.toByteArray());
        }
        // Exact inverse: without the two guards every old method is byte-for-byte canonical.
        for (AbstractInsnNode n : inserted) update.instructions.remove(n);
        for (MethodNode m : oldFarm.methods) check(HungBySonExpInfoPack.canonical(m).equals(
                HungBySonExpInfoPack.canonical(method(farm, m.name, m.desc))), "Farm inverse " + m.name);
        check(farm.methods.size() == oldFarm.methods.size() && farm.fields.size() == oldFarm.fields.size(), "Farm topology");
        for (MethodNode m : oldBridge.methods) check(HungBySonExpInfoPack.canonical(m).equals(
                HungBySonExpInfoPack.canonical(method(bridge, m.name, m.desc))), "Bridge inverse " + m.name);
        check(bridge.methods.size() == oldBridge.methods.size() + 7 && bridge.fields.size() == oldBridge.fields.size(), "Bridge allowlist");
        List<String> interfaces = new ArrayList<String>(oldBridge.interfaces); interfaces.add(V);
        check(bridge.interfaces.equals(interfaces), "Bridge interface addition");

        // Semantic support: only the reviewed Farm spot additions.
        Map<String,String[]> allowedChanges = new LinkedHashMap<String,String[]>();
        allowedChanges.put(CTRL + ".class", new String[]{"handleAction(I)Z", "farmSpot(L" + V + ";)Z",
                "farmSpotFind(L" + V + ";)" + OBJ, "farmSpotCurrent(L" + V + ";" + OBJ + ")Z"});
        allowedChanges.put(R + ".class", new String[]{"farmSpot(L" + V + ";)Z"});
        allowedChanges.put(V + ".class", null);
        for (Map.Entry<String,String[]> e : allowedChanges.entrySet()) {
            byte[] data = Files.readAllBytes(new File(support, e.getKey()).toPath());
            HungBySonExpInfoPack.screen(e.getKey(), data);
            if (e.getValue() != null) {
                ClassNode old = HungBySonExpInfoPack.node(before.get(e.getKey())), now = HungBySonExpInfoPack.node(data);
                Set<String> allowed = new HashSet<String>(Arrays.asList(e.getValue()));
                for (MethodNode m : old.methods) if (!allowed.contains(m.name + m.desc))
                    check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(now, m.name, m.desc))),
                            "Unrelated support method " + e.getKey() + " " + m.name);
                for (MethodNode m : now.methods) {
                    boolean existed = false; for (MethodNode o : old.methods) existed |= o.name.equals(m.name) && o.desc.equals(m.desc);
                    check(existed || allowed.contains(m.name + m.desc), "Unexpected support method " + m.name);
                }
                check(old.version == now.version, "Support class version");
            }
            out.put(e.getKey(), data);
        }

        Set<String> allowed = new HashSet<String>(Arrays.asList(B + ".class", F + ".class", CTRL + ".class", R + ".class"));
        List<String> changed = new ArrayList<String>(); int classes = 0, resources = 0;
        for (String name : before.keySet()) {
            if (!Arrays.equals(before.get(name), out.get(name))) { check(allowed.contains(name), "Archive allowlist " + name); changed.add(name); }
            if (name.endsWith(".class")) {
                classes++;
                check(HungBySonExpInfoPack.node(before.get(name)).version == HungBySonExpInfoPack.node(out.get(name)).version, "Class version " + name);
            } else resources++;
        }
        check(changed.size() == 4 && out.size() == before.size() + 1, "Exactly four changed classes and one added interface");
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String,byte[]> e : out.entrySet()) {
                JarEntry z = new JarEntry(e.getKey()); z.setTime(0); jar.putNextEntry(z); jar.write(e.getValue()); jar.closeEntry();
            }
        } finally { jar.close(); }

        p.setProperty("input.sha256", INPUT); p.setProperty("output.sha256", HungBySonExpInfoPack.hash(output));
        p.setProperty("farm.spot.enabled", "true"); p.setProperty("farm.spot.guards", "2");
        p.setProperty("farm.spot.armed.field", armed); p.setProperty("farm.spot.latch.field", latch);
        p.setProperty("farm.spot.retarget.field", retarget); p.setProperty("farm.spot.counter.field", counter);
        p.setProperty("farm.spot.rearm.method", rearm); p.setProperty("farm.spot.castLoop.method", castLoop);
        p.setProperty("farm.spot.interface", V); p.setProperty("changed.classes", String.join(",", changed));
        p.setProperty("added.classes", V + ".class"); p.setProperty("native.inverse", "PASS");
        OutputStream config = new FileOutputStream(args[3]);
        try { p.store(config, "Farm Special stands at its spot like Auto Attack; native return trip unchanged"); } finally { config.close(); }
        PrintWriter log = new PrintWriter(args[4], "UTF-8");
        log.println("PASS pinned input; two at-spot guards with exact Farm inverse; old bridge methods unchanged; CLDC support screen");
        log.println("CHANGED=" + changed + " ADDED=" + V + ".class");
        log.println("CLASSES=" + classes + " UNCHANGED_RESOURCES=" + resources); log.println("SHA256=" + p.getProperty("output.sha256"));
        log.close();
        System.out.println("FARM_SPOT_PATCH=PASS SHA256=" + p.getProperty("output.sha256"));
    }
}
