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
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Blocks the actual player-interaction action path while monster-only mode is active. */
public final class HSOSKYMode7NoPlayerUiPatcher implements Opcodes {
    private static final String INPUT =
            "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_REMOTEZONE_SERVERCD_LOOTTP_NOCLIP_VIHELP_DEFAULT37.jar";
    private static final String OUTPUT =
            "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_REMOTEZONE_SERVERCD_LOOTTP_NOCLIP_VIHELP_DEFAULT37_MODE7_NOPLAYERUI_V4.jar";
    private static final String SUPPORT = "HSOSKYPetFeedSupport";

    private static final Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
    private static final Map<String, Long> times = new LinkedHashMap<String, Long>();
    private static ClassNode player;
    private static ClassNode base;
    private static MethodNode autoScan;
    private static MethodNode update;
    private static MethodNode targetAction;
    private static MethodNode targetMenuDispatch;
    private static FieldInsnNode currentTarget;
    private static String targetTypeField;
    private static int hooks;
    private static int originalUpdateSupportCalls;
    private static int originalTargetActionSupportCalls;
    private static int originalTargetMenuDispatchSupportCalls;

    private HSOSKYMode7NoPlayerUiPatcher() {
    }

    public static void main(String[] args) throws Exception {
        File input = new File(INPUT);
        File output = new File(OUTPUT);
        require(input.isFile(), "Missing input JAR: " + INPUT);
        require(!output.exists(), "Refusing to overwrite existing output: " + OUTPUT);
        readJar(input);

        byte[] originalPlayer = discover();
        patchMode7PlayerInteraction();
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
        System.out.println("    hooks: " + hooks
                + " (pointer scan + target action + menu dispatch)");
        System.out.println("    mode 6: player targeting unchanged");
        System.out.println("    mode 7: player interaction target is cleared before UI opens");
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
        byte[] originalPlayer = entries.get(player.name + ".class");
        base = readClass(entries.get(player.superName + ".class"));
        require(base != null, "Base actor class was not found");

        for (MethodNode method : player.methods) {
            if (calls(method, SUPPORT, "isAutoPlayersEnabled", "()Z")) {
                require(autoScan == null, "Multiple PvP auto-scan methods matched");
                autoScan = method;
            }
        }
        require(autoScan != null, "PvP auto-scan method was not found");

        for (MethodNode method : player.methods) {
            if ((method.access & ACC_STATIC) == 0 && "()V".equals(method.desc)
                    && calls(method, player.name, autoScan.name, autoScan.desc)) {
                require(update == null, "Multiple Player update methods matched");
                update = method;
            }
        }
        require(update != null, "Player update method was not found");

        for (AbstractInsnNode instruction = update.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != GETSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            if (!("L" + base.name + ";").equals(field.desc)) continue;
            AbstractInsnNode next = nextReal(instruction);
            if (next instanceof FieldInsnNode && next.getOpcode() == GETFIELD
                    && base.name.equals(((FieldInsnNode) next).owner)
                    && "B".equals(((FieldInsnNode) next).desc)) {
                currentTarget = field;
                break;
            }
        }
        require(currentTarget != null, "Current-target field was not found");

        for (MethodNode method : player.methods) {
            if ((method.access & ACC_PUBLIC) == 0 || (method.access & ACC_STATIC) != 0) continue;
            int reads = fieldCount(method, GETSTATIC,
                    currentTarget.owner, currentTarget.name, currentTarget.desc);
            int writes = fieldCount(method, PUTSTATIC,
                    currentTarget.owner, currentTarget.name, currentTarget.desc);
            if ("()V".equals(method.desc) && reads >= 20 && writes == 0) {
                require(targetAction == null, "Multiple target-action methods matched");
                targetAction = method;
            }
            if ("(IZ)V".equals(method.desc) && reads >= 20 && writes == 0) {
                require(targetMenuDispatch == null, "Multiple target-menu dispatch methods matched");
                targetMenuDispatch = method;
            }
        }
        require(targetAction != null, "Target-action method was not found");
        require(targetMenuDispatch != null, "Target-menu dispatch method was not found");
        require(fieldCount(targetAction, GETSTATIC,
                        currentTarget.owner, currentTarget.name, currentTarget.desc) == 27,
                "Unexpected target-action structure");
        require(fieldCount(targetMenuDispatch, GETSTATIC,
                        currentTarget.owner, currentTarget.name, currentTarget.desc) == 44,
                "Unexpected target-menu dispatch structure");

        originalUpdateSupportCalls = callCount(update, SUPPORT, "isAutoPlayersEnabled", "()Z");
        originalTargetActionSupportCalls = callCount(
                targetAction, SUPPORT, "isAutoPlayersEnabled", "()Z");
        originalTargetMenuDispatchSupportCalls = callCount(
                targetMenuDispatch, SUPPORT, "isAutoPlayersEnabled", "()Z");
        return originalPlayer;
    }

    private static void patchMode7PlayerInteraction() {
        TableSwitchInsnNode actorTypeSwitch = null;
        for (AbstractInsnNode instruction = update.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof TableSwitchInsnNode)) continue;
            TableSwitchInsnNode table = (TableSwitchInsnNode) instruction;
            if (table.min == 0 && table.max == 7) {
                require(actorTypeSwitch == null, "Multiple actor-type switches matched");
                actorTypeSwitch = table;
            }
        }
        require(actorTypeSwitch != null, "Pointer actor-type switch was not found");
        require(actorTypeSwitch.labels.size() == 8
                        && actorTypeSwitch.labels.get(0) == actorTypeSwitch.labels.get(2),
                "Unexpected pointer actor-type switch structure");

        AbstractInsnNode switchInput = previousReal(actorTypeSwitch);
        require(switchInput instanceof VarInsnNode && switchInput.getOpcode() == ILOAD,
                "Actor-type switch input is not a local int");
        int typeVar = ((VarInsnNode) switchInput).var;

        FieldInsnNode typeRead = null;
        AbstractInsnNode cursor = switchInput;
        for (int count = 0; count < 12 && cursor != null; count++) {
            cursor = previousReal(cursor);
            if (!(cursor instanceof FieldInsnNode) || cursor.getOpcode() != GETFIELD) continue;
            FieldInsnNode field = (FieldInsnNode) cursor;
            AbstractInsnNode store = nextReal(cursor);
            if (base.name.equals(field.owner) && "B".equals(field.desc)
                    && store instanceof VarInsnNode && store.getOpcode() == ISTORE
                    && ((VarInsnNode) store).var == typeVar) {
                typeRead = field;
                break;
            }
        }
        require(typeRead != null, "Actor-type field read was not found");
        targetTypeField = typeRead.name;
        AbstractInsnNode actorLoad = previousReal(typeRead);
        require(actorLoad instanceof VarInsnNode && actorLoad.getOpcode() == ALOAD,
                "Actor candidate is not in a local reference");
        int actorVar = ((VarInsnNode) actorLoad).var;

        JumpInsnNode nearestReject = null;
        cursor = actorLoad;
        for (int count = 0; count < 8 && cursor != null; count++) {
            cursor = previousReal(cursor);
            if (cursor instanceof JumpInsnNode) {
                nearestReject = (JumpInsnNode) cursor;
                break;
            }
        }
        require(nearestReject != null, "Actor reject branch was not found");
        LabelNode reject = nearestReject.label;
        require(update.instructions.indexOf(reject) > update.instructions.indexOf(actorTypeSwitch),
                "Actor reject label is before the type switch");

        LabelNode allowCandidate = new LabelNode();
        InsnList filter = new InsnList();
        filter.add(new VarInsnNode(ALOAD, actorVar));
        filter.add(new FieldInsnNode(GETFIELD, base.name, targetTypeField, "B"));
        filter.add(new JumpInsnNode(IFNE, allowCandidate));
        filter.add(new MethodInsnNode(
                INVOKESTATIC, SUPPORT, "isAutoPlayersEnabled", "()Z", false));
        filter.add(new JumpInsnNode(IFNE, allowCandidate));
        filter.add(new JumpInsnNode(GOTO, reject));
        filter.add(allowCandidate);
        update.instructions.insertBefore(switchInput, filter);
        hooks++;

        addMode7PlayerGuard(targetAction);
        addMode7PlayerGuard(targetMenuDispatch);
    }

    /** Clears a type-0 player target before either building or dispatching its interaction UI. */
    private static void addMode7PlayerGuard(MethodNode method) {
        LabelNode allow = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new MethodInsnNode(
                INVOKESTATIC, SUPPORT, "isAutoPlayersEnabled", "()Z", false));
        guard.add(new JumpInsnNode(IFNE, allow));
        guard.add(new FieldInsnNode(
                GETSTATIC, currentTarget.owner, currentTarget.name, currentTarget.desc));
        guard.add(new JumpInsnNode(IFNULL, allow));
        guard.add(new FieldInsnNode(
                GETSTATIC, currentTarget.owner, currentTarget.name, currentTarget.desc));
        guard.add(new FieldInsnNode(GETFIELD, base.name, targetTypeField, "B"));
        guard.add(new JumpInsnNode(IFNE, allow));
        guard.add(new InsnNode(ACONST_NULL));
        guard.add(new FieldInsnNode(
                PUTSTATIC, currentTarget.owner, currentTarget.name, currentTarget.desc));
        guard.add(new InsnNode(RETURN));
        guard.add(allow);
        method.instructions.insert(guard);
        hooks++;
    }

    private static void verify(File input, File output, byte[] originalPlayer, byte[] patchedPlayer)
            throws Exception {
        require(hooks == 3, "Unexpected hook count: " + hooks);
        require(!Arrays.equals(originalPlayer, patchedPlayer), "Player class did not change");
        try (JarFile before = new JarFile(input); JarFile after = new JarFile(output)) {
            require(after.getManifest() != null, "Output JAR has no manifest");
            require(before.size() == after.size(),
                    "Entry count changed: " + before.size() + " -> " + after.size());
            Enumeration<JarEntry> all = before.entries();
            int changed = 0;
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

            ClassNode patched = readClass(readAll(
                    after.getInputStream(after.getJarEntry(player.name + ".class"))));
            MethodNode patchedUpdate = findMethod(patched, update.name, update.desc);
            MethodNode patchedAction = findMethod(patched, targetAction.name, targetAction.desc);
            MethodNode patchedDispatch = findMethod(
                    patched, targetMenuDispatch.name, targetMenuDispatch.desc);
            require(patchedUpdate != null && patchedAction != null && patchedDispatch != null,
                    "A patched Player method is missing");
            require(callCount(patchedUpdate, SUPPORT, "isAutoPlayersEnabled", "()Z")
                            == originalUpdateSupportCalls + 1,
                    "Pointer filter was not persisted");
            require(callCount(patchedAction, SUPPORT, "isAutoPlayersEnabled", "()Z")
                            == originalTargetActionSupportCalls + 1,
                    "Target-action guard was not persisted");
            require(callCount(patchedDispatch, SUPPORT, "isAutoPlayersEnabled", "()Z")
                            == originalTargetMenuDispatchSupportCalls + 1,
                    "Target-menu dispatch guard was not persisted");
            require(containsString(readClass(readAll(after.getInputStream(
                            after.getJarEntry(SUPPORT + ".class")))), "LỆNH MOD"),
                    "Vietnamese help class is missing");
        }

        URLClassLoader loader = new URLClassLoader(
                new URL[] { output.toURI().toURL(), new File("microemulator.jar").toURI().toURL() },
                HSOSKYMode7NoPlayerUiPatcher.class.getClassLoader());
        try {
            Class.forName(SUPPORT, false, loader);
            Class.forName(player.name.replace('/', '.'), false, loader);
        } finally {
            loader.close();
        }
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
        if (bytes == null) return null;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static void save(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        entries.put(node.name + ".class", writer.toByteArray());
    }

    private static boolean containsString(ClassNode node, String value) {
        if (node == null) return false;
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

    private static int fieldCount(
            MethodNode method, int opcode, String owner, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != opcode) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            if (owner.equals(field.owner) && name.equals(field.name) && desc.equals(field.desc)) count++;
        }
        return count;
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
