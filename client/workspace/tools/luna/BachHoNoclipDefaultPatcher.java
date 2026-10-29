package luna.tools;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Applies the Bạch Hổ Noclip default and fresh-login reset to the exact verified release. */
public final class BachHoNoclipDefaultPatcher implements Opcodes {
    private static final String EXPECTED_INPUT_SHA256 =
            "A8A4165F0E78C7E852444A425CB8C0611BF772A70F6D44B1374091225732FF0E";
    private static final String HOOK = "LunaGeneratedLegacyHooks";
    private static final String RUNTIME = "HSOSKYRuntimeSupport";
    private static final String SEMANTIC_HOOKS = "luna/core/LunaSemanticHooks";
    private static final String CONTROLLER = "luna/features/AutoTradeController";
    private static final String PET_SUPPORT = "HSOSKYPetFeedSupport";
    private static final String RESET = "resetNoClipForFreshLogin";
    private static final String RESET_DESC = "(Z)V";

    private BachHoNoclipDefaultPatcher() { }

    public static void main(String[] args) throws Exception {
        require(args.length == 5,
                "Usage: <input.jar> <new-output.jar> <base-symbols.properties> <new-symbols.properties> <audit.txt>");
        File input = new File(args[0]);
        File output = new File(args[1]);
        File baseCatalog = new File(args[2]);
        File newCatalog = new File(args[3]);
        File audit = new File(args[4]);
        require(input.isFile() && baseCatalog.isFile(), "Missing input JAR or base catalog");
        require(!output.exists() && !newCatalog.exists() && !audit.exists(),
                "Output, new catalog, and audit must be new paths");
        String inputHash = sha256(input);
        require(EXPECTED_INPUT_SHA256.equals(inputHash), "Unexpected input JAR hash: " + inputHash);
        Properties base = new Properties();
        InputStream baseIn = new FileInputStream(baseCatalog);
        try { base.load(baseIn); } finally { baseIn.close(); }
        require(EXPECTED_INPUT_SHA256.equals(base.getProperty("output.sha256")),
                "Base catalog does not identify the requested input JAR");

        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        Map<String, Long> times = new HashMap<String, Long>();
        readJar(input, entries, times);
        String[] requiredEntries = {
            HOOK + ".class", RUNTIME + ".class", CONTROLLER + ".class", SEMANTIC_HOOKS + ".class",
            PET_SUPPORT + ".class"
        };
        for (int i = 0; i < requiredEntries.length; i++) {
            require(entries.containsKey(requiredEntries[i]), "Missing exact required class " + requiredEntries[i]);
        }

        entries.put(RUNTIME + ".class", patchRuntime(entries.get(RUNTIME + ".class")));
        entries.put(CONTROLLER + ".class", patchController(entries.get(CONTROLLER + ".class")));
        entries.put(SEMANTIC_HOOKS + ".class", patchSemanticHooks(entries.get(SEMANTIC_HOOKS + ".class")));
        entries.put(HOOK + ".class", patchHook(entries.get(HOOK + ".class"), entries.get(PET_SUPPORT + ".class")));
        verifyPatch(entries);

        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        File tmp = new File(output.getAbsolutePath() + ".tmp");
        require(!tmp.exists(), "Temporary output already exists: " + tmp);
        writeJar(tmp, entries, times);
        verifyArchive(input, tmp, entries, times);
        Files.move(tmp.toPath(), output.toPath());
        String outputHash = sha256(output);

        writeCatalog(newCatalog, input, inputHash, output, outputHash);
        File auditParent = audit.getAbsoluteFile().getParentFile();
        if (auditParent != null) Files.createDirectories(auditParent.toPath());
        writeAudit(audit, input, inputHash, output, outputHash, entries);
        System.out.println("input.sha256=" + inputHash);
        System.out.println("output.sha256=" + outputHash);
        System.out.println("changed.classes=" + HOOK + "," + RUNTIME + "," + CONTROLLER + "," + SEMANTIC_HOOKS);
    }

    private static byte[] patchRuntime(byte[] bytes) {
        ClassNode node = readClass(bytes);
        require(RUNTIME.equals(node.name), "Runtime owner mismatch");
        FieldNode active = field(node, "noClipEnabled", "Z");
        FieldNode requested = field(node, "requestedNoClipEnabled", "Z");
        require((active.access & ACC_STATIC) != 0 && (requested.access & ACC_STATIC) != 0,
                "Runtime Noclip fields must be static");
        MethodNode onWorld = method(node, "onWorld", "(II)V");
        List<AbstractInsnNode> worldCode = real(onWorld);
        int preservedSelection = 0;
        for (int i = 0; i + 1 < worldCode.size(); i++) {
            if (field(worldCode.get(i), GETSTATIC, RUNTIME, requested.name, "Z")
                    && field(worldCode.get(i + 1), PUTSTATIC, RUNTIME, active.name, "Z")) {
                preservedSelection++;
            }
        }
        require(preservedSelection == 1,
                "Map transition no longer restores the requested Noclip selection");
        patchTrueInitializer(node, active.name);
        patchTrueInitializer(node, requested.name);
        require(methodOrNull(node, RESET, RESET_DESC) == null, "Runtime reset method already exists");

        MethodNode reset = new MethodNode(ACC_PUBLIC | ACC_STATIC | ACC_SYNCHRONIZED,
                RESET, RESET_DESC, null, null);
        reset.instructions.add(new FieldInsnNode(GETSTATIC, RUNTIME, active.name, "Z"));
        reset.instructions.add(new VarInsnNode(ILOAD, 0));
        reset.instructions.add(new InsnNode(ICONST_1));
        reset.instructions.add(new InsnNode(IXOR));
        reset.instructions.add(new InsnNode(IAND));
        reset.instructions.add(new FieldInsnNode(PUTSTATIC, RUNTIME, active.name, "Z"));
        reset.instructions.add(new FieldInsnNode(GETSTATIC, RUNTIME, requested.name, "Z"));
        reset.instructions.add(new VarInsnNode(ILOAD, 0));
        reset.instructions.add(new InsnNode(ICONST_1));
        reset.instructions.add(new InsnNode(IXOR));
        reset.instructions.add(new InsnNode(IAND));
        reset.instructions.add(new FieldInsnNode(PUTSTATIC, RUNTIME, requested.name, "Z"));
        reset.instructions.add(new InsnNode(RETURN));
        reset.maxStack = 3;
        reset.maxLocals = 1;
        node.methods.add(reset);
        return writeClass(node);
    }

    private static byte[] patchController(byte[] bytes) {
        ClassNode node = readClass(bytes);
        require(CONTROLLER.equals(node.name), "Semantic controller owner mismatch");
        FieldNode noClip = field(node, "noclipEnabled", "Z");
        require((noClip.access & ACC_STATIC) == 0, "AutoTrade Noclip state must be an instance field");
        MethodNode constructor = method(node, "<init>",
                "(Lluna/api/LunaBridgeApi;Lluna/api/LunaClock;Lluna/core/MovementArbiter;)V");
        int writes = patchTrueInstanceInitializer(constructor, node.name, noClip.name);
        require(writes == 1, "Expected one semantic Noclip initializer, found " + writes);
        require(methodOrNull(node, RESET, RESET_DESC) == null, "Semantic reset method already exists");

        MethodNode reset = new MethodNode(ACC_PUBLIC, RESET, RESET_DESC, null, null);
        reset.instructions.add(new VarInsnNode(ALOAD, 0));
        reset.instructions.add(new VarInsnNode(ALOAD, 0));
        reset.instructions.add(new FieldInsnNode(GETFIELD, CONTROLLER, noClip.name, "Z"));
        reset.instructions.add(new VarInsnNode(ILOAD, 1));
        reset.instructions.add(new InsnNode(ICONST_1));
        reset.instructions.add(new InsnNode(IXOR));
        reset.instructions.add(new InsnNode(IAND));
        reset.instructions.add(new FieldInsnNode(PUTFIELD, CONTROLLER, noClip.name, "Z"));
        reset.instructions.add(new InsnNode(RETURN));
        reset.maxStack = 4;
        reset.maxLocals = 2;
        node.methods.add(reset);
        return writeClass(node);
    }

    private static byte[] patchSemanticHooks(byte[] bytes) {
        ClassNode node = readClass(bytes);
        require(SEMANTIC_HOOKS.equals(node.name), "Semantic hook owner mismatch");
        FieldNode runtime = field(node, "RUNTIME", "Lluna/core/LunaRuntime;");
        require((runtime.access & (ACC_STATIC | ACC_PRIVATE)) == (ACC_STATIC | ACC_PRIVATE),
                "Semantic runtime singleton has unexpected access");
        method(node, "handleMenuAction", "(II)Z");
        method(node, "tick", "()V");
        require(methodOrNull(node, RESET, RESET_DESC) == null, "Semantic reset bridge already exists");

        MethodNode reset = new MethodNode(ACC_PUBLIC | ACC_STATIC, RESET, RESET_DESC, null, null);
        reset.instructions.add(new FieldInsnNode(GETSTATIC, SEMANTIC_HOOKS, runtime.name, runtime.desc));
        reset.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "luna/core/LunaRuntime", "autoTrade",
                "()Lluna/features/AutoTradeController;", false));
        reset.instructions.add(new VarInsnNode(ILOAD, 0));
        reset.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, CONTROLLER, RESET, RESET_DESC, false));
        reset.instructions.add(new InsnNode(RETURN));
        reset.maxStack = 2;
        reset.maxLocals = 1;
        node.methods.add(reset);
        return writeClass(node);
    }

    private static byte[] patchHook(byte[] bytes, byte[] petSupportBytes) {
        ClassNode node = readClass(bytes);
        require(HOOK.equals(node.name), "Generated hook owner mismatch");
        FieldNode noClip = field(node, "noclipOn", "Z");
        FieldNode savedNoClip = field(node, "n4SavedNoclip", "Z");
        require((noClip.access & ACC_STATIC) != 0 && (savedNoClip.access & ACC_STATIC) != 0,
                "Generated Noclip states must be static");
        patchTrueInitializer(node, noClip.name);
        require(fieldOrNull(node, "noclipLoginResetDone", "Z") == null,
                "Login reset guard already exists");
        require(fieldOrNull(node, "loginEntryAwaitingWorld", "Z") == null,
                "Login-entry pending flag already exists");
        node.fields.add(new FieldNode(ACC_STATIC, "noclipLoginResetDone", "Z", null, null));
        node.fields.add(new FieldNode(ACC_STATIC, "loginEntryAwaitingWorld", "Z", null, null));

        ClassNode petSupport = readClass(petSupportBytes);
        FieldNode disconnected = field(petSupport, "isDisconnected", "Z");
        require((disconnected.access & ACC_STATIC) != 0, "Native disconnect flag is not static");
        method(node, "onLoginScreen", "()Z");
        require(methodOrNull(node, RESET, "()V") == null, "Fresh-login reset helper already exists");
        addResetHelper(node, noClip, savedNoClip, disconnected);
        require(methodOrNull(node, "onFreshLoginWorldEntry", "()V") == null,
                "Fresh-login callback already exists");
        MethodNode callback = new MethodNode(ACC_STATIC, "onFreshLoginWorldEntry", "()V", null, null);
        callback.instructions.add(new InsnNode(RETURN));
        callback.maxStack = 0;
        callback.maxLocals = 0;
        node.methods.add(callback);
        patchLifecycle(node);
        return writeClass(node);
    }

    private static void addResetHelper(ClassNode node, FieldNode noClip, FieldNode savedNoClip,
            FieldNode disconnected) {
        MethodNode reset = new MethodNode(ACC_STATIC, RESET, "()V", null, null);
        reset.instructions.add(new FieldInsnNode(GETSTATIC, PET_SUPPORT, disconnected.name, "Z"));
        reset.instructions.add(new MethodInsnNode(INVOKESTATIC, HOOK, "onLoginScreen", "()Z", false));
        reset.instructions.add(new InsnNode(IOR));
        reset.instructions.add(new VarInsnNode(ISTORE, 0));
        reset.instructions.add(new FieldInsnNode(GETSTATIC, HOOK, "noclipLoginResetDone", "Z"));
        reset.instructions.add(new InsnNode(ICONST_1));
        reset.instructions.add(new InsnNode(IXOR));
        reset.instructions.add(new VarInsnNode(ILOAD, 0));
        reset.instructions.add(new InsnNode(IAND));
        reset.instructions.add(new VarInsnNode(ISTORE, 1));
        reset.instructions.add(new FieldInsnNode(GETSTATIC, HOOK, noClip.name, "Z"));
        reset.instructions.add(new VarInsnNode(ILOAD, 1));
        reset.instructions.add(new InsnNode(ICONST_1));
        reset.instructions.add(new InsnNode(IXOR));
        reset.instructions.add(new InsnNode(IAND));
        reset.instructions.add(new FieldInsnNode(PUTSTATIC, HOOK, noClip.name, "Z"));
        reset.instructions.add(new FieldInsnNode(GETSTATIC, HOOK, savedNoClip.name, "Z"));
        reset.instructions.add(new VarInsnNode(ILOAD, 1));
        reset.instructions.add(new InsnNode(ICONST_1));
        reset.instructions.add(new InsnNode(IXOR));
        reset.instructions.add(new InsnNode(IAND));
        reset.instructions.add(new FieldInsnNode(PUTSTATIC, HOOK, savedNoClip.name, "Z"));
        reset.instructions.add(new FieldInsnNode(GETSTATIC, HOOK, "noclipLoginResetDone", "Z"));
        reset.instructions.add(new VarInsnNode(ILOAD, 0));
        reset.instructions.add(new InsnNode(IOR));
        reset.instructions.add(new FieldInsnNode(PUTSTATIC, HOOK, "noclipLoginResetDone", "Z"));
        reset.instructions.add(new FieldInsnNode(GETSTATIC, HOOK, "loginEntryAwaitingWorld", "Z"));
        reset.instructions.add(new VarInsnNode(ILOAD, 1));
        reset.instructions.add(new InsnNode(IOR));
        reset.instructions.add(new FieldInsnNode(PUTSTATIC, HOOK, "loginEntryAwaitingWorld", "Z"));
        reset.instructions.add(new VarInsnNode(ILOAD, 1));
        reset.instructions.add(new MethodInsnNode(INVOKESTATIC, RUNTIME, RESET, RESET_DESC, false));
        reset.instructions.add(new VarInsnNode(ILOAD, 1));
        // This bridge resets the semantic mirror without changing menu or N4 policy.
        reset.instructions.add(new MethodInsnNode(INVOKESTATIC, SEMANTIC_HOOKS, RESET, RESET_DESC, false));
        reset.instructions.add(new InsnNode(RETURN));
        reset.maxStack = 3;
        reset.maxLocals = 2;
        node.methods.add(reset);
    }

    private static void patchLifecycle(ClassNode owner) {
        MethodNode observe = method(owner, "attackRecoveryObserve", "()V");
        List<AbstractInsnNode> code = real(observe);
        LabelNode notInGameTarget = null;
        int notInGameBranches = 0;
        for (int i = 0; i + 1 < code.size(); i++) {
            if (!(code.get(i) instanceof VarInsnNode) || code.get(i).getOpcode() != ILOAD
                    || ((VarInsnNode) code.get(i)).var != 0 || !(code.get(i + 1) instanceof JumpInsnNode)
                    || code.get(i + 1).getOpcode() != IFNE) continue;
            JumpInsnNode branch = (JumpInsnNode) code.get(i + 1);
            AbstractInsnNode target = nextReal(branch.label);
            if (field(target, GETSTATIC, HOOK, "attackRecoveryWasInGame", "Z")) {
                notInGameTarget = branch.label;
                notInGameBranches++;
            }
        }
        require(notInGameBranches == 1 && notInGameTarget != null,
                "Cannot uniquely identify the in-game lifecycle branch");
        AbstractInsnNode notInGameBranch = null;
        for (int i = 0; i + 1 < code.size(); i++) {
            if (code.get(i) instanceof VarInsnNode && code.get(i).getOpcode() == ILOAD
                    && ((VarInsnNode) code.get(i)).var == 0 && code.get(i + 1) instanceof JumpInsnNode
                    && code.get(i + 1).getOpcode() == IFNE
                    && ((JumpInsnNode) code.get(i + 1)).label == notInGameTarget) {
                notInGameBranch = code.get(i + 1);
                break;
            }
        }
        require(notInGameBranch != null, "Missing not-in-game entry");
        observe.instructions.insert(notInGameBranch,
                new MethodInsnNode(INVOKESTATIC, HOOK, RESET, "()V", false));
        InsnList rearm = new InsnList();
        rearm.add(new InsnNode(ICONST_0));
        rearm.add(new FieldInsnNode(PUTSTATIC, HOOK, "noclipLoginResetDone", "Z"));
        // Keep the existing frame at the branch label, ahead of the new code.
        observe.instructions.insertBefore(nextReal(notInGameTarget), rearm);

        code = real(observe);
        LabelNode transitionExit = null;
        AbstractInsnNode transitionInsertBefore = null;
        int transitionCount = 0;
        for (int i = 0; i + 5 < code.size(); i++) {
            if (!field(code.get(i), GETSTATIC, HOOK, "attackRecoveryWasInGame", "Z")
                    || !(code.get(i + 1) instanceof JumpInsnNode)
                    || code.get(i + 1).getOpcode() != IFNE
                    || !field(code.get(i + 2), GETSTATIC, HOOK, "postLoginEnterSent", "Z")
                    || !(code.get(i + 3) instanceof JumpInsnNode)
                    || code.get(i + 3).getOpcode() != IFNE) continue;
            JumpInsnNode first = (JumpInsnNode) code.get(i + 1);
            JumpInsnNode second = (JumpInsnNode) code.get(i + 3);
            if (first.label != second.label || code.get(i + 4).getOpcode() != ICONST_1
                    || !field(code.get(i + 5), PUTSTATIC, HOOK, "postLoginEnterPending", "Z")) continue;
            transitionExit = first.label;
            transitionInsertBefore = code.get(i + 4);
            transitionCount++;
        }
        require(transitionCount == 1 && transitionExit != null && transitionInsertBefore != null,
                "Cannot uniquely identify the login/world-entry arm branch");
        InsnList callback = new InsnList();
        callback.add(new FieldInsnNode(GETSTATIC, HOOK, "loginEntryAwaitingWorld", "Z"));
        callback.add(new JumpInsnNode(IFEQ, transitionExit));
        callback.add(new InsnNode(ICONST_0));
        callback.add(new FieldInsnNode(PUTSTATIC, HOOK, "loginEntryAwaitingWorld", "Z"));
        callback.add(new MethodInsnNode(INVOKESTATIC, HOOK, "onFreshLoginWorldEntry", "()V", false));
        observe.instructions.insertBefore(transitionInsertBefore, callback);
        observe.maxStack = Math.max(observe.maxStack + 1, 4);
    }

    private static void patchTrueInitializer(ClassNode owner, String fieldName) {
        MethodNode init = method(owner, "<clinit>", "()V");
        int found = 0;
        for (AbstractInsnNode insn : init.instructions.toArray()) {
            if (!field(insn, PUTSTATIC, owner.name, fieldName, "Z")) continue;
            AbstractInsnNode value = previousReal(insn);
            require(value != null && value.getOpcode() == ICONST_1,
                    "Noclip initializer is not the expected true constant: " + owner.name + "." + fieldName);
            init.instructions.set(value, new InsnNode(ICONST_0));
            found++;
        }
        require(found == 1, "Expected one true initializer for " + owner.name + "." + fieldName + ", found " + found);
    }

    private static boolean hasField(MethodNode method, int opcode, String owner, String name, String desc) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (field(insn, opcode, owner, name, desc)) return true;
        }
        return false;
    }

    private static boolean hasCall(MethodNode method, int opcode, String owner, String name, String desc) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode) || insn.getOpcode() != opcode) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) return true;
        }
        return false;
    }

    private static int patchTrueInstanceInitializer(MethodNode method, String owner, String fieldName) {
        int found = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!field(insn, PUTFIELD, owner, fieldName, "Z")) continue;
            AbstractInsnNode value = previousReal(insn);
            AbstractInsnNode receiver = value == null ? null : previousReal(value);
            require(value != null && value.getOpcode() == ICONST_1
                    && receiver != null && receiver.getOpcode() == ALOAD
                    && ((VarInsnNode) receiver).var == 0,
                    "Unexpected semantic Noclip initialization");
            method.instructions.set(value, new InsnNode(ICONST_0));
            found++;
        }
        return found;
    }

    private static void verifyPatch(Map<String, byte[]> entries) {
        ClassNode hook = readClass(entries.get(HOOK + ".class"));
        ClassNode runtime = readClass(entries.get(RUNTIME + ".class"));
        ClassNode controller = readClass(entries.get(CONTROLLER + ".class"));
        ClassNode semantic = readClass(entries.get(SEMANTIC_HOOKS + ".class"));
        require(fieldOrNull(hook, "noclipLoginResetDone", "Z") != null
                && fieldOrNull(hook, "loginEntryAwaitingWorld", "Z") != null,
                "Missing lifecycle state");
        method(hook, RESET, "()V");
        method(hook, "onFreshLoginWorldEntry", "()V");
        method(hook, "attackRecoveryObserve", "()V");
        method(runtime, RESET, RESET_DESC);
        method(controller, RESET, RESET_DESC);
        method(semantic, RESET, RESET_DESC);
        MethodNode n4Restore = method(hook, "n4Stop", "(Ljava/lang/String;)V");
        require(hasField(n4Restore, GETSTATIC, HOOK, "n4SavedNoclip", "Z")
                && hasField(n4Restore, PUTSTATIC, HOOK, "noclipOn", "Z")
                && hasCall(n4Restore, INVOKESTATIC, RUNTIME, "setNoClipEnabled", "(Z)V"),
                "N4 must continue restoring its saved Noclip preference");
        MethodNode n4Want = method(hook, "n4WantNoclip", "(Z)V");
        require(hasCall(n4Want, INVOKESTATIC, RUNTIME, "setNoClipEnabled", "(Z)V"),
                "N4 temporary Noclip policy must remain connected to runtime state");
    }

    private static void verifyArchive(File input, File output, Map<String, byte[]> expected,
            Map<String, Long> times) throws Exception {
        LinkedHashMap<String, byte[]> before = new LinkedHashMap<String, byte[]>();
        Map<String, Long> beforeTimes = new HashMap<String, Long>();
        readJar(input, before, beforeTimes);
        LinkedHashMap<String, byte[]> after = new LinkedHashMap<String, byte[]>();
        Map<String, Long> afterTimes = new HashMap<String, Long>();
        readJar(output, after, afterTimes);
        require(before.keySet().equals(after.keySet()), "Archive entry set changed");
        require(expected.keySet().equals(after.keySet()), "Output does not match staged entries");
        String[] changed = { HOOK + ".class", RUNTIME + ".class", CONTROLLER + ".class", SEMANTIC_HOOKS + ".class" };
        Set<String> allowed = new HashSet<String>(Arrays.asList(changed));
        for (String name : before.keySet()) {
            if (allowed.contains(name)) continue;
            require(Arrays.equals(before.get(name), after.get(name)), "Unexpected entry change: " + name);
        }
        for (String name : changed) {
            require(!Arrays.equals(before.get(name), after.get(name)), "Expected class was not patched: " + name);
            ClassNode oldClass = readClass(before.get(name));
            ClassNode newClass = readClass(after.get(name));
            require(oldClass.name.equals(newClass.name) && oldClass.version == newClass.version,
                    "Class identity/version changed: " + name);
        }
        require(beforeTimes.size() == times.size(), "Input entry timestamps changed during read");
    }

    private static void writeCatalog(File file, File input, String inputHash, File output,
            String outputHash) throws Exception {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        StringBuilder text = new StringBuilder();
        text.append("input.jar=").append(relative(input)).append('\n');
        text.append("input.sha256=").append(inputHash).append('\n');
        text.append("output.jar=").append(relative(output)).append('\n');
        text.append("output.sha256=").append(outputHash).append('\n');
        text.append("legacy.owner=").append(HOOK).append('\n');
        text.append("legacy.default.field=noclipOn:Z=false\n");
        text.append("legacy.lifecycle=attackRecoveryObserve()V\n");
        text.append("legacy.login.reset=disconnect-or-login-screen; once until in-game\n");
        text.append("legacy.login.callback=onFreshLoginWorldEntry()V\n");
        text.append("legacy.n4.saved.field=n4SavedNoclip:Z\n");
        text.append("runtime.owner=").append(RUNTIME).append('\n');
        text.append("runtime.default.fields=noClipEnabled:Z=false,requestedNoClipEnabled:Z=false\n");
        text.append("runtime.map.policy=requested selection persists through onWorld/zone changes\n");
        text.append("semantic.owner=").append(CONTROLLER).append('\n');
        text.append("semantic.default.field=noclipEnabled:Z=false\n");
        text.append("semantic.reset.bridge=").append(SEMANTIC_HOOKS).append('.').append(RESET).append(RESET_DESC).append('\n');
        writeUtf8New(file, text.toString());
    }

    private static void writeAudit(File file, File input, String inputHash, File output,
            String outputHash, Map<String, byte[]> entries) throws Exception {
        StringBuilder text = new StringBuilder();
        text.append("BachHo Noclip default patch verification\n");
        text.append("input=").append(relative(input)).append('\n');
        text.append("input.sha256=").append(inputHash).append('\n');
        text.append("output=").append(relative(output)).append('\n');
        text.append("output.sha256=").append(outputHash).append('\n');
        text.append("classes.changed=").append(HOOK).append(".class,").append(RUNTIME).append(".class,")
                .append(CONTROLLER).append(".class,").append(SEMANTIC_HOOKS).append(".class\n");
        text.append("legacy.noclip.default=OFF\n");
        text.append("runtime.noClipEnabled.default=OFF\n");
        text.append("runtime.requestedNoClipEnabled.default=OFF\n");
        text.append("semantic.autotrade.noclipEnabled.default=OFF\n");
        text.append("login.reset=only when native isDisconnected or current screen is the native login screen; one-shot until in-game\n");
        text.append("map.transitions=preserve requested Noclip setting; reset is not armed by map/world changes\n");
        text.append("n4=n4SavedNoclip adopts OFF on fresh login; N4 may still request temporary states and restores the saved OFF state\n");
        text.append("callback=onFreshLoginWorldEntry()V, called once at the corresponding in-game entry\n");
        text.append("archive.check=all non-target entries byte-identical; entry set and class versions preserved\n");
        text.append("entry.count=").append(entries.size()).append('\n');
        writeUtf8New(file, text.toString());
    }

    private static void writeJar(File file, LinkedHashMap<String, byte[]> entries,
            Map<String, Long> times) throws Exception {
        JarOutputStream output = new JarOutputStream(new FileOutputStream(file));
        try {
            for (Map.Entry<String, byte[]> item : entries.entrySet()) {
                JarEntry entry = new JarEntry(item.getKey());
                Long time = times.get(item.getKey());
                if (time != null && time.longValue() >= 0L) entry.setTime(time.longValue());
                output.putNextEntry(entry);
                output.write(item.getValue());
                output.closeEntry();
            }
        } finally { output.close(); }
    }

    private static void readJar(File file, LinkedHashMap<String, byte[]> entries,
            Map<String, Long> times) throws Exception {
        JarFile jar = new JarFile(file);
        try {
            Enumeration<JarEntry> all = jar.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                require(!entries.containsKey(entry.getName()), "Duplicate archive entry " + entry.getName());
                InputStream in = jar.getInputStream(entry);
                try { entries.put(entry.getName(), readAll(in)); }
                finally { in.close(); }
                times.put(entry.getName(), Long.valueOf(entry.getTime()));
            }
        } finally { jar.close(); }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        return out.toByteArray();
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] writeClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static FieldNode field(ClassNode node, String name, String desc) {
        FieldNode found = fieldOrNull(node, name, desc);
        require(found != null, "Missing field " + node.name + "." + name + ":" + desc);
        return found;
    }

    private static FieldNode fieldOrNull(ClassNode node, String name, String desc) {
        FieldNode found = null;
        for (FieldNode field : node.fields) {
            if (!name.equals(field.name) || !desc.equals(field.desc)) continue;
            require(found == null, "Duplicate field " + node.name + "." + name + ":" + desc);
            found = field;
        }
        return found;
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        MethodNode found = methodOrNull(node, name, desc);
        require(found != null, "Missing method " + node.name + "." + name + desc);
        return found;
    }

    private static MethodNode methodOrNull(ClassNode node, String name, String desc) {
        MethodNode found = null;
        for (MethodNode method : node.methods) {
            if (!name.equals(method.name) || !desc.equals(method.desc)) continue;
            require(found == null, "Duplicate method " + node.name + "." + name + desc);
            found = method;
        }
        return found;
    }

    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() >= 0) result.add(insn);
        return result;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        AbstractInsnNode previous = insn.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }

    private static boolean field(AbstractInsnNode insn, int opcode, String owner, String name, String desc) {
        if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != opcode) return false;
        FieldInsnNode field = (FieldInsnNode) insn;
        return owner.equals(field.owner) && name.equals(field.name) && desc.equals(field.desc);
    }

    private static void writeUtf8New(File file, String value) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        OutputStream out = new FileOutputStream(file);
        try { out.write(value.getBytes(StandardCharsets.UTF_8)); }
        finally { out.close(); }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        } finally { in.close(); }
        StringBuilder value = new StringBuilder();
        byte[] hash = digest.digest();
        for (int i = 0; i < hash.length; i++) value.append(String.format("%02X", hash[i] & 255));
        return value.toString();
    }

    private static String relative(File file) {
        return file.getPath().replace('\\', '/');
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
