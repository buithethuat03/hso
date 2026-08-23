import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Tightens mode-6 PvP selection and keeps a valid explicitly selected target.
 *
 * <p>A player is eligible for automatic selection only when both characters
 * have an active PvP flag and the two flag values differ. The game's original
 * hostility/state checks still run afterwards. If the current target survives
 * every original and added check, it wins over the nearest-target scan.</p>
 */
public final class HSOSKYPvpTargetPatcher implements Opcodes {
    private static final String INPUT =
            "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_"
            + "REMOTEZONE_SERVERCD_LOOTTP_NOCLIP_VIHELP_DEFAULT37_MODE7_NOPLAYERUI_V4.jar";
    private static final String OUTPUT =
            "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_"
            + "REMOTEZONE_SERVERCD_LOOTTP_NOCLIP_VIHELP_DEFAULT37_MODE7_NOPLAYERUI_"
            + "V4_PVPVALID_KEEP.jar";
    private static final String SUPPORT = "HSOSKYPetFeedSupport";

    private static final Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
    private static final Map<String, Long> times = new LinkedHashMap<String, Long>();

    private static ClassNode player;
    private static String baseName;
    private static MethodNode hostility;
    private static MethodNode autoScan;
    private static FieldInsnNode currentTarget;
    private static FieldInsnNode pvpFlag;
    private static int candidateVar;
    private static int filterHooks;
    private static int retentionHooks;

    private HSOSKYPvpTargetPatcher() {
    }

    public static void main(String[] args) throws Exception {
        File input = new File(INPUT);
        File output = new File(OUTPUT);
        require(input.isFile(), "Missing input JAR: " + INPUT);
        require(!output.exists(), "Refusing to overwrite existing output: " + OUTPUT);

        readJar(input);
        byte[] originalPlayer = discover();
        patchStrictPvpFilter();
        patchValidTargetRetention();
        require(filterHooks == 1, "Unexpected PvP filter hook count: " + filterHooks);
        require(retentionHooks == 2, "Unexpected target-retention hook count: " + retentionHooks);
        save(player);
        byte[] patchedPlayer = entries.get(player.name + ".class");

        File temporary = new File(OUTPUT + ".tmp");
        require(!temporary.exists(), "Temporary output already exists: " + temporary.getName());
        try {
            writeJar(temporary);
            verify(input, temporary, originalPlayer, patchedPlayer);
            Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }

        System.out.println("[+] Built " + OUTPUT);
        System.out.println("    mode 6 player filter: both PvP flags > 0 and different");
        System.out.println("    original hostility/state checks: preserved");
        System.out.println("    valid current target: retained instead of nearest-target replacement");
        System.out.println("    mode 7 behavior: unchanged");
    }

    private static byte[] discover() {
        for (Map.Entry<String, byte[]> item : entries.entrySet()) {
            if (!item.getKey().endsWith(".class")) continue;
            ClassNode candidate = readClass(item.getValue());
            if (containsString(candidate, "Player vec7")) {
                require(player == null, "Multiple Player classes matched");
                player = candidate;
            }
        }
        require(player != null, "Player class was not found");
        baseName = player.superName;
        require(baseName != null, "Player has no actor superclass");

        for (MethodNode method : player.methods) {
            if ((method.access & ACC_STATIC) == 0
                    && ("(L" + baseName + ";)Z").equals(method.desc)) {
                require(hostility == null, "Multiple hostility methods matched");
                hostility = method;
            }
        }
        require(hostility != null, "Player hostility method was not found");

        for (MethodNode method : player.methods) {
            if ((method.access & ACC_STATIC) != 0 || !"()V".equals(method.desc)) continue;
            if (calls(method, SUPPORT, "isAutoPlayersEnabled", "()Z")
                    && calls(method, player.name, hostility.name, hostility.desc)
                    && containsInt(method, Short.MAX_VALUE)
                    && countStaticActorWrites(method) == 1) {
                require(autoScan == null, "Multiple mode-6 auto-scan methods matched");
                autoScan = method;
            }
        }
        require(autoScan != null, "Mode-6 auto-scan method was not found");

        for (AbstractInsnNode instruction = autoScan.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != PUTSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            if (("L" + baseName + ";").equals(field.desc)) {
                require(currentTarget == null, "Multiple selected-target writes in auto scan");
                currentTarget = field;
            }
        }
        require(currentTarget != null, "Selected-target field was not found");

        findPvpFlagField();
        return entries.get(player.name + ".class");
    }

    /** Maps the flag field from the original same-flag rejection in isHostile(actor). */
    private static void findPvpFlagField() {
        for (AbstractInsnNode instruction = hostility.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != GETFIELD) continue;
            FieldInsnNode first = (FieldInsnNode) instruction;
            if (!"B".equals(first.desc)) continue;
            AbstractInsnNode loadCandidate = previousReal(first);
            AbstractInsnNode loadSelf = nextReal(first);
            AbstractInsnNode secondNode = nextReal(loadSelf);
            AbstractInsnNode comparison = nextReal(secondNode);
            if (!isLoad(loadCandidate, 1) || !isLoad(loadSelf, 0)
                    || !(secondNode instanceof FieldInsnNode)
                    || secondNode.getOpcode() != GETFIELD
                    || !(comparison instanceof JumpInsnNode)
                    || (comparison.getOpcode() != IF_ICMPNE
                        && comparison.getOpcode() != IF_ICMPEQ)) continue;
            FieldInsnNode second = (FieldInsnNode) secondNode;
            if (!first.owner.equals(second.owner) || !first.name.equals(second.name)
                    || !first.desc.equals(second.desc)) continue;

            if (pvpFlag == null) pvpFlag = first;
            else require(pvpFlag.owner.equals(first.owner) && pvpFlag.name.equals(first.name),
                    "Multiple candidate PvP flag fields matched");
        }
        require(pvpFlag != null, "PvP flag field was not found");
        require(countFieldReads(hostility, pvpFlag.owner, pvpFlag.name, "B") >= 8,
                "Mapped PvP flag field has too few hostility references");
    }

    /** Adds targetFlag > 0, selfFlag > 0 and targetFlag != selfFlag before isHostile. */
    private static void patchStrictPvpFilter() {
        MethodInsnNode supportCall = null;
        MethodInsnNode hostilityCall = null;
        JumpInsnNode reject = null;
        AbstractInsnNode loadSelf = null;

        for (AbstractInsnNode instruction = autoScan.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (SUPPORT.equals(call.owner) && "isAutoPlayersEnabled".equals(call.name)
                    && "()Z".equals(call.desc)) {
                AbstractInsnNode jumpNode = nextReal(call);
                AbstractInsnNode selfNode = nextReal(jumpNode);
                AbstractInsnNode candidateNode = nextReal(selfNode);
                AbstractInsnNode hostilityNode = nextReal(candidateNode);
                if (jumpNode instanceof JumpInsnNode && jumpNode.getOpcode() == IFEQ
                        && isLoad(selfNode, 0)
                        && candidateNode instanceof VarInsnNode
                        && candidateNode.getOpcode() == ALOAD
                        && hostilityNode instanceof MethodInsnNode
                        && player.name.equals(((MethodInsnNode) hostilityNode).owner)
                        && hostility.name.equals(((MethodInsnNode) hostilityNode).name)
                        && hostility.desc.equals(((MethodInsnNode) hostilityNode).desc)) {
                    require(supportCall == null, "Multiple auto-scan player branches matched");
                    supportCall = call;
                    reject = (JumpInsnNode) jumpNode;
                    loadSelf = selfNode;
                    candidateVar = ((VarInsnNode) candidateNode).var;
                    hostilityCall = (MethodInsnNode) hostilityNode;
                }
            }
        }
        require(supportCall != null && hostilityCall != null && reject != null && loadSelf != null,
                "Mode-6 player branch structure was not found");

        InsnList filter = new InsnList();
        filter.add(new VarInsnNode(ALOAD, candidateVar));
        filter.add(new FieldInsnNode(GETFIELD, pvpFlag.owner, pvpFlag.name, "B"));
        filter.add(new JumpInsnNode(IFLE, reject.label));
        filter.add(new VarInsnNode(ALOAD, 0));
        filter.add(new FieldInsnNode(GETFIELD, pvpFlag.owner, pvpFlag.name, "B"));
        filter.add(new JumpInsnNode(IFLE, reject.label));
        filter.add(new VarInsnNode(ALOAD, candidateVar));
        filter.add(new FieldInsnNode(GETFIELD, pvpFlag.owner, pvpFlag.name, "B"));
        filter.add(new VarInsnNode(ALOAD, 0));
        filter.add(new FieldInsnNode(GETFIELD, pvpFlag.owner, pvpFlag.name, "B"));
        filter.add(new JumpInsnNode(IF_ICMPEQ, reject.label));
        autoScan.instructions.insertBefore(loadSelf, filter);
        filterHooks++;
    }

    /**
     * Lets the current target bypass nearest-distance replacement only after it
     * has passed the scan's complete type, flag, hostility and alive checks.
     */
    private static void patchValidTargetRetention() {
        MethodInsnNode distanceCall = null;
        InsnNode duplicate = null;
        VarInsnNode distanceStore = null;
        VarInsnNode rangeLoad = null;
        JumpInsnNode nearer = null;

        for (AbstractInsnNode instruction = autoScan.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != INVOKESTATIC) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (!"(IIII)I".equals(call.desc)) continue;
            AbstractInsnNode dupNode = nextReal(call);
            AbstractInsnNode storeNode = nextReal(dupNode);
            AbstractInsnNode rangeNode = nextReal(storeNode);
            AbstractInsnNode jumpNode = nextReal(rangeNode);
            if (dupNode instanceof InsnNode && dupNode.getOpcode() == DUP
                    && storeNode instanceof VarInsnNode && storeNode.getOpcode() == ISTORE
                    && rangeNode instanceof VarInsnNode && rangeNode.getOpcode() == ILOAD
                    && jumpNode instanceof JumpInsnNode && jumpNode.getOpcode() == IF_ICMPLT) {
                require(distanceCall == null, "Multiple nearest-distance comparisons matched");
                distanceCall = call;
                duplicate = (InsnNode) dupNode;
                distanceStore = (VarInsnNode) storeNode;
                rangeLoad = (VarInsnNode) rangeNode;
                nearer = (JumpInsnNode) jumpNode;
            }
        }
        require(distanceCall != null && duplicate != null && distanceStore != null
                        && rangeLoad != null && nearer != null,
                "Nearest-target comparison was not found");

        int distanceVar = distanceStore.var;
        autoScan.instructions.set(duplicate, new VarInsnNode(ISTORE, distanceVar));
        autoScan.instructions.remove(distanceStore);

        LabelNode normalDistance = new LabelNode();
        InsnList preferBeforeDistance = new InsnList();
        preferBeforeDistance.add(new MethodInsnNode(
                INVOKESTATIC, SUPPORT, "isAutoPlayersEnabled", "()Z", false));
        preferBeforeDistance.add(new JumpInsnNode(IFEQ, normalDistance));
        preferBeforeDistance.add(new VarInsnNode(ALOAD, candidateVar));
        preferBeforeDistance.add(new FieldInsnNode(GETSTATIC,
                currentTarget.owner, currentTarget.name, currentTarget.desc));
        preferBeforeDistance.add(new JumpInsnNode(IF_ACMPEQ, nearer.label));
        preferBeforeDistance.add(normalDistance);
        preferBeforeDistance.add(new VarInsnNode(ILOAD, distanceVar));
        autoScan.instructions.insertBefore(rangeLoad, preferBeforeDistance);
        retentionHooks++;

        AbstractInsnNode accepted = nextReal(nearer.label);
        AbstractInsnNode storeRange = nextReal(accepted);
        AbstractInsnNode loadCandidate = nextReal(storeRange);
        AbstractInsnNode storeBest = nextReal(loadCandidate);
        require(accepted instanceof VarInsnNode && accepted.getOpcode() == ILOAD
                        && ((VarInsnNode) accepted).var == distanceVar,
                "Accepted-distance load changed unexpectedly");
        require(storeRange instanceof VarInsnNode && storeRange.getOpcode() == ISTORE,
                "Accepted range store was not found");
        require(loadCandidate instanceof VarInsnNode && loadCandidate.getOpcode() == ALOAD
                        && ((VarInsnNode) loadCandidate).var == candidateVar,
                "Accepted candidate load was not found");
        require(storeBest instanceof VarInsnNode && storeBest.getOpcode() == ASTORE,
                "Best-target store was not found");

        JumpInsnNode breakLoop = null;
        AbstractInsnNode cursor = storeBest;
        for (int count = 0; count < 10 && cursor != null; count++) {
            cursor = nextReal(cursor);
            if (cursor instanceof JumpInsnNode && cursor.getOpcode() == GOTO) {
                breakLoop = (JumpInsnNode) cursor;
                break;
            }
        }
        require(breakLoop != null, "Auto-scan loop exit was not found");

        LabelNode continueOriginalChecks = new LabelNode();
        InsnList stopAfterRetainedTarget = new InsnList();
        stopAfterRetainedTarget.add(new MethodInsnNode(
                INVOKESTATIC, SUPPORT, "isAutoPlayersEnabled", "()Z", false));
        stopAfterRetainedTarget.add(new JumpInsnNode(IFEQ, continueOriginalChecks));
        stopAfterRetainedTarget.add(new VarInsnNode(ALOAD, candidateVar));
        stopAfterRetainedTarget.add(new FieldInsnNode(GETSTATIC,
                currentTarget.owner, currentTarget.name, currentTarget.desc));
        stopAfterRetainedTarget.add(new JumpInsnNode(IF_ACMPEQ, breakLoop.label));
        stopAfterRetainedTarget.add(continueOriginalChecks);
        autoScan.instructions.insert(storeBest, stopAfterRetainedTarget);
        retentionHooks++;
    }

    private static void verify(File input, File output, byte[] originalPlayer, byte[] patchedPlayer)
            throws Exception {
        require(!Arrays.equals(originalPlayer, patchedPlayer), "Player class did not change");
        try (JarFile before = new JarFile(input); JarFile after = new JarFile(output)) {
            require(after.getManifest() != null, "Output JAR has no manifest");
            require(before.size() == after.size(),
                    "Entry count changed: " + before.size() + " -> " + after.size());

            int changed = 0;
            Enumeration<JarEntry> all = before.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                JarEntry counterpart = after.getJarEntry(entry.getName());
                require(counterpart != null, "Missing entry: " + entry.getName());
                byte[] left = readAll(before.getInputStream(entry));
                byte[] right = readAll(after.getInputStream(counterpart));
                if (!Arrays.equals(left, right)) {
                    changed++;
                    require((player.name + ".class").equals(entry.getName()),
                            "Unexpected changed entry: " + entry.getName());
                }
                if (entry.getName().endsWith(".class")) new ClassReader(right);
            }
            require(changed == 1, "Unexpected changed-entry count: " + changed);

            ClassNode patched = readClass(readAll(after.getInputStream(
                    after.getJarEntry(player.name + ".class"))));
            MethodNode scan = findMethod(patched, autoScan.name, autoScan.desc);
            require(scan != null, "Patched auto-scan method is missing");
            require(callCount(scan, SUPPORT, "isAutoPlayersEnabled", "()Z")
                            == callCount(autoScan, SUPPORT, "isAutoPlayersEnabled", "()Z"),
                    "Mode checks were not persisted");
            require(countFieldReads(scan, pvpFlag.owner, pvpFlag.name, "B") >= 4,
                    "Strict PvP flag reads were not persisted");
            require(countFieldReads(scan,
                            currentTarget.owner, currentTarget.name, currentTarget.desc) >= 2,
                    "Current-target retention reads were not persisted");
        }

        URLClassLoader loader = new URLClassLoader(
                new URL[] { output.toURI().toURL(), new File("microemulator.jar").toURI().toURL() },
                HSOSKYPvpTargetPatcher.class.getClassLoader());
        try {
            Class.forName(SUPPORT, false, loader);
            Class.forName(player.name.replace('/', '.'), false, loader);
        } finally {
            loader.close();
        }
    }

    private static int countStaticActorWrites(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == PUTSTATIC
                    && ("L" + baseName + ";").equals(((FieldInsnNode) instruction).desc)) count++;
        }
        return count;
    }

    private static int countFieldReads(
            MethodNode method, String owner, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != GETFIELD
                    && instruction.getOpcode() != GETSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            if (owner.equals(field.owner) && name.equals(field.name) && desc.equals(field.desc)) count++;
        }
        return count;
    }

    private static boolean isLoad(AbstractInsnNode instruction, int local) {
        return instruction instanceof VarInsnNode && instruction.getOpcode() == ALOAD
                && ((VarInsnNode) instruction).var == local;
    }

    private static boolean calls(MethodNode method, String owner, String name, String desc) {
        return callCount(method, owner, name, desc) > 0;
    }

    private static int callCount(MethodNode method, String owner, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) count++;
        }
        return count;
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        return null;
    }

    private static boolean containsString(ClassNode node, String value) {
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                    instruction = instruction.getNext()) {
                if (instruction instanceof LdcInsnNode
                        && ((LdcInsnNode) instruction).cst instanceof String
                        && ((String) ((LdcInsnNode) instruction).cst).contains(value)) return true;
            }
        }
        return false;
    }

    private static boolean containsInt(MethodNode method, int value) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            int opcode = instruction.getOpcode();
            if (opcode >= ICONST_M1 && opcode <= ICONST_5
                    && value == opcode - ICONST_0) return true;
            if (instruction instanceof org.objectweb.asm.tree.IntInsnNode
                    && ((org.objectweb.asm.tree.IntInsnNode) instruction).operand == value) return true;
            if (instruction instanceof LdcInsnNode
                    && Integer.valueOf(value).equals(((LdcInsnNode) instruction).cst)) return true;
        }
        return false;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode instruction) {
        instruction = instruction == null ? null : instruction.getNext();
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
        return instruction;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode instruction) {
        instruction = instruction == null ? null : instruction.getPrevious();
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getPrevious();
        return instruction;
    }

    private static void readJar(File file) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> all = jar.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                entries.put(entry.getName(), readAll(jar.getInputStream(entry)));
                times.put(entry.getName(), Long.valueOf(entry.getTime()));
            }
        }
    }

    private static void writeJar(File file) throws Exception {
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, byte[]> item : entries.entrySet()) {
                JarEntry entry = new JarEntry(item.getKey());
                Long time = times.get(item.getKey());
                if (time != null && time.longValue() >= 0L) entry.setTime(time.longValue());
                out.putNextEntry(entry);
                if (!item.getKey().endsWith("/")) out.write(item.getValue());
                out.closeEntry();
            }
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static void save(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        entries.put(node.name + ".class", writer.toByteArray());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
