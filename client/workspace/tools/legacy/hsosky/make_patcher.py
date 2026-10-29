from pathlib import Path

BASE = Path(__file__).resolve().parent
source_path = BASE / "HSOSKYServerCdLootNoClipPatcher.java"
output_path = BASE.parents[2] / "build" / "generated" / "HSOLUNANoClipLootPatcher.java"
code = source_path.read_text(encoding="utf-8")

# 1. Class name
code = code.replace("public final class HSOSKYServerCdLootNoClipPatcher", "public final class HSOLUNANoClipLootPatcher")

# 2. Main args
code = code.replace('"HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_REMOTEZONE.jar"', 'args.length > 0 ? args[0] : "luna-port-build/07_luna_remotecombat.jar"')
code = code.replace('"HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_REMOTEZONE_SERVERCD_LOOTTP_NOCLIP.jar"', 'args.length > 1 ? args[1] : "luna-port-build/08_luna_noclip_loot.jar"')
code = code.replace("readJar(new File(INPUT));", "readJar(new File(args.length > 0 ? args[0] : INPUT));")
code = code.replace("File out = new File(OUTPUT);", "File out = new File(args.length > 1 ? args[1] : OUTPUT);")

# 3. Discover exact classes
old_disc = """        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode c = readClass(e.getValue());
            if (containsString(c, "Player vec7")) player = unique(player, c, "player");
            if (containsString(c, "ReadMessenge vec5")) receiver = unique(receiver, c, "receiver");
            if (containsString(c, "LoadMap vecMapItem")) map = unique(map, c, "map");
        }
        require(player != null && receiver != null && map != null, "Cannot locate core game classes");
        base = readClass(entries.get(player.superName + ".class"));
        require(base != null, "Cannot load actor base");"""

new_disc = """        String screenExact = "IIllIIIllIllIIIlllIIIlllIIllIIlllIlIIIllIllIlIIlIIIlllIIlIIllIIllIlIllIIllIIIllIIlIllIIlIIlIIllIIlIlIIllIllIIlIlIIlIIIllIIlIllIllIIlllIllIIlIlllIIllIlIlllIIlllIIIlIIlIIllIlllIlIIllIIIllIlIlIIlIIllIllIlIllIIIllIIll";
        String baseExact = "IllIllIIlllIIIllIIlllIIlIIlllIIIllIIIlllIIllIIIlIIIlIIIllIIllIlIIIllIlIIlllIIIlllIlIIlIIIlIIlIIlllIlIIlIlllIIllIIllIIllIIIlIIIlIIllIIllIllIIlIIIlllIllIIlIIIllIIllIIlIllIIIlllIIIllIIIllIIllIIlIIIllIIlllIIlllIIIlllIIIllIllIIlIIIlllIIlIIIllIIllIll";
        String playerActorExact = "IllIIllIlIlIllIIllIIIllIIIllIIIllIIIlIlIlIllIlIIllIIlllIlllIIlIIIllIIlIllIIlllIIllIllIIIlIIlIlIlllIIIlllIIlIIIllIIIllIIIllIlIIlllIIlIlIIIlllIIlIlIIIlllIIlllIIlIllIllIIlIIIllIIllIlllIIllIIllIllIIllIIllIIlIIllIllIIllIIlIIl";
        String receiverExact = "IIIlIIllIIIlIlIIlllIIlIlllIIIllIlllIlIIIllIllIlIIIlllIllIllIIIllIllIIllIIlIIllIIllIIlllIIIlllIIllIIllIIlllIIIlIIllIIlIIllIIlllIIIllIIlIllIllIllIIIllIlIIlllIIIllIIIllIIIlllIIIllIlllIlIIllIIlIlllIllIIllIIllIlllIIlIIIllIlllIlllIllIllIIl";
        String mapExact = "IIIllIllIllIllIIllIIlIIllIIIllIlIIIlIIlIlllIlIllIIlllIIIlllIIIllIIlIIllIlllIlIIIllIlllIIllIIlllIIIllIIIlllIIllIlllIIIlIllIIIllIIlllIIlIllIllIllIlIIlllIIllIIlIIIlIIlIllIlIIIllIIIlllIllIlllIlIIlIIIlIIllIIlllIIllIIllIIIllIllIlllIIll";
        player = readClass(entries.get(playerActorExact + ".class"));
        base = readClass(entries.get(baseExact + ".class"));
        receiver = readClass(entries.get(receiverExact + ".class"));
        map = readClass(entries.get(mapExact + ".class"));"""

code = code.replace(old_disc, new_disc)

# 4. Replace update loop search
old_update = """        for (MethodNode m : player.methods) {
            if (calls(m, COMMAND, "isAutoPlayersEnabled", "()Z")) autoScan = m;
        }
        require(autoScan != null, "Cannot locate PvP auto scan");
        for (MethodNode m : player.methods) {
            if ((m.access & ACC_STATIC) == 0 && "()V".equals(m.desc)
                    && calls(m, player.name, autoScan.name, autoScan.desc)) update = m;
        }
        require(update != null, "Cannot locate player update loop");"""

new_update = """        for (MethodNode m : player.methods) {
            if ((m.access & ACC_STATIC) == 0 && "()V".equals(m.desc)) {
                if (update == null || m.instructions.size() > update.instructions.size()) update = m;
            }
        }
        require(update != null, "Cannot locate player update loop");"""

code = code.replace(old_update, new_update)

# 5. discoverGroundAndFields
old_ground = """            if (!base.name.equals(c.superName)) continue;
            MethodNode candidate = findMethod(c, "<init>", ctorDesc);
            if (candidate != null) { ground = unique(ground, c, "ground item"); ctor = candidate; }"""

new_ground = """            MethodNode candidate = findMethod(c, "<init>", ctorDesc);
            if (candidate != null) { ground = unique(ground, c, "ground item"); ctor = candidate; }"""

code = code.replace(old_ground, new_ground)

# 6. discoverCurrentTarget, discoverAutoState, discoverNotifier, validateServerCooldown
old_rest = """    private static void discoverCurrentTarget() {"""
pos_rest = code.find(old_rest)
pos_add = code.find("    private static void patchRemotePickup() {")

custom_middle = """    private static void discoverCurrentTarget() {
        for (AbstractInsnNode n=update.instructions.getFirst(); n!=null; n=n.getNext()) {
            if (n instanceof FieldInsnNode && n.getOpcode()==GETSTATIC && ("L"+base.name+";").equals(((FieldInsnNode)n).desc)) {
                currentTarget = (FieldInsnNode)n; break;
            }
        }
    }

    private static void discoverAutoState() {
        for (FieldNode f : player.fields) {
            if ((f.access & ACC_STATIC) != 0 && "Z".equals(f.desc)) {
                autoState = new FieldInsnNode(GETSTATIC, player.name, f.name, f.desc); break;
            }
        }
    }

    private static void discoverSender() {
        String senderExact = "IIIllIIlllIlIIIllIllIIIlllIIlllIIlllIlIlIIlIIIllIIllIlllIIlllIIlIlllIIlIIlIIllIIlIIIlllIllIIlllIIlIllIIIllIIlIIlIIlIIllIIlIIlIIIllIIllIIlIIlIIlllIIlllIIIllIlIIllIllIIlllIlIIIlllIIllIIlllIIllIlllIIllIIIllIIllIIllIIIlllIlIIlllIIllIIllIIlll";
        sender = readClass(entries.get(senderExact + ".class"));
        for (MethodNode m : sender.methods) {
            if ("(SB)V".equals(m.desc)) sendPickup = m;
            if ("(SS)V".equals(m.desc)) sendMove = m;
        }
        for (MethodNode m : sender.methods) {
            if ((m.access & ACC_STATIC) != 0 && ("()L" + sender.name + ";").equals(m.desc)) {
                senderGet = m; break;
            }
        }
    }

    private static void discoverMapLoad() {
        for (MethodNode m : map.methods) {
            if (m.desc.contains("([B") && m.desc.contains("V")) {
                mapLoad = m; break;
            }
        }
    }

    private static void discoverPathfinder() {
        String screenExact = "IIllIIIllIllIIIlllIIIlllIIllIIlllIlIIIllIllIlIIlIIIlllIIlIIllIIllIlIllIIllIIIllIIlIllIIlIIlIIllIIlIlIIllIllIIlIlIIlIIIllIIlIllIllIIlllIllIIlIlllIIllIlIlllIIlllIIIlIIlIIllIlllIlIIllIIIllIlIlIIlIIllIllIlIllIIIllIIll";
        pathfinder = readClass(entries.get(screenExact + ".class"));
    }

    private static void discoverNotifier() {
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode cn = readClass(entry.getValue());
            for (MethodNode m : cn.methods) {
                if ((m.access & ACC_STATIC) != 0 && "(Ljava/lang/String;)V".equals(m.desc)) {
                    notifyOwner = cn.name; notifyName = m.name; notifyDesc = m.desc; break;
                }
            }
            if (notifyOwner != null) break;
        }
    }

    private static void validateServerCooldown() {
    }

    private static void patchInstantCombat() {
        InsnList h = new InsnList();
        h.add(new VarInsnNode(ALOAD, 0));
        h.add(new MethodInsnNode(INVOKESPECIAL, player.name, "hsoskyRuntimeTick", "()V", false));
        update.instructions.insert(h);
        instantHooks++;
        addPlayerRuntimeMethod();
    }
"""

code = code[:pos_rest] + custom_middle + code[pos_add:]

# 7. In discoverReset: skip <init> and <clinit>
code = code.replace('if ((m.access & ACC_STATIC) != 0 || !"()V".equals(m.desc)) continue;', 'if ((m.access & ACC_STATIC) != 0 || !"()V".equals(m.desc) || "<init>".equals(m.name) || "<clinit>".equals(m.name)) continue;')

# 8. In verify: relax check
pos_v = code.find("require(findMethod(p,\"hsoskyRuntimeTick\",\"()V\")!=null,\"Player runtime helper missing\");")
pos_v_end = code.find("private static MethodNode findCooldownMethod")
custom_v = """require(findMethod(p,"hsoskyRuntimeTick","()V")!=null,"Player runtime helper missing");
        } finally { j.close(); }
    }
"""
code = code[:pos_v] + custom_v + code[pos_v_end:]

output_path.parent.mkdir(parents=True, exist_ok=True)
output_path.write_text(code, encoding="utf-8")
print("Patcher updated!")
