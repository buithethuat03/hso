import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Executes the candidate's actual native cG animation guard as an isolated bytecode slice.
 * This tests the guard and lifecycle bridge interaction; it does not render a full animation.
 */
public final class HungBySonRemoteAnimationBarrierTest implements Opcodes {
    private static final String SLICE = "fixture/hungbyson/RemoteAnimationGuardSlice";
    private static final String CANDIDATE_HASH = "F806EE5F395B6979057ECF49DA4C10E7313D39A6F6AB23A10D04E88347A4B892";
    private static final String BASELINE_HASH = "8B67184EE5AEBD1181931B80CD6F9EF0AC833637BE90B5EB5C5AA14289A55953";
    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static ClassNode node(byte[] bytes) {
        if (bytes == null) throw new AssertionError("Missing native class bytes");
        ClassNode result = new ClassNode();
        new ClassReader(bytes).accept(result, 0);
        return result;
    }

    private static byte[] bytes(ClassNode type) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        return writer.toByteArray();
    }

    private static Map<String, byte[]> read(File file) throws Exception {
        Map<String, byte[]> result = new HashMap<String, byte[]>();
        JarFile jar = new JarFile(file);
        try {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                InputStream in = jar.getInputStream(entry);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                try { while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count); }
                finally { in.close(); }
                result.put(entry.getName(), out.toByteArray());
            }
        } finally { jar.close(); }
        return result;
    }

    private static Properties properties(String path) throws Exception {
        Properties result = new Properties();
        InputStream in = new FileInputStream(path);
        try { result.load(in); } finally { in.close(); }
        return result;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) >= 0) digest.update(buffer, 0, count);
        } finally { in.close(); }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format("%02X", b & 0xff));
        return result.toString();
    }

    private static AbstractInsnNode real(AbstractInsnNode n) {
        while (n != null && n.getOpcode() < 0) n = n.getNext();
        return n;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode n) {
        n = n.getPrevious();
        while (n != null && n.getOpcode() < 0) n = n.getPrevious();
        return n;
    }

    private static boolean isVar(AbstractInsnNode n, int opcode, int slot) {
        return n instanceof VarInsnNode && n.getOpcode() == opcode
                && ((VarInsnNode)n).var == slot;
    }

    private static boolean isField(AbstractInsnNode n, int opcode, String owner,
            String name, String desc) {
        if (!(n instanceof FieldInsnNode) || n.getOpcode() != opcode) return false;
        FieldInsnNode field = (FieldInsnNode)n;
        return field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(desc);
    }

    private static void checkField(AbstractInsnNode n, int opcode, String owner,
            String name, String desc, String context) {
        require(isField(n, opcode, owner, name, desc), "Unexpected native field in " + context);
    }

    private static MethodNode findMethod(ClassNode owner, String name, String desc) {
        MethodNode found = null;
        for (MethodNode method : owner.methods) if (method.name.equals(name) && method.desc.equals(desc)) {
            if (found != null) throw new AssertionError("Duplicate native method " + name + desc);
            found = method;
        }
        if (found == null) throw new AssertionError("Missing native method " + owner.name + "." + name + desc);
        return found;
    }

    /** Finds the called remote helper, proves the two-cG guard shape, then copies that
     * native control-flow slice while substituting only its two native entity inputs.
     */
    private static byte[] actualGuardSlice(Map<String, byte[]> jar, Properties catalog) {
        String entity = catalog.getProperty("entity.owner");
        String receiverOwner = catalog.getProperty("hook.unseenCombat.owner");
        String receiverName = catalog.getProperty("hook.unseenCombat.method");
        String receiverDesc = catalog.getProperty("hook.unseenCombat.descriptor");
        require(entity != null && receiverOwner != null && receiverName != null
                && receiverDesc != null, "Combat receiver catalog fields");
        ClassNode receiverClass = node(jar.get(receiverOwner + ".class"));
        MethodNode receiver = findMethod(receiverClass, receiverName, receiverDesc);
        String nativeCallDesc = "(L" + entity + ";Let;IB)V";
        MethodInsnNode dispatch = null;
        for (AbstractInsnNode instruction : receiver.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (!call.desc.equals(nativeCallDesc)) continue;
            require(call.getOpcode() == INVOKEVIRTUAL, "Remote dispatch is not the expected virtual helper call");
            if (dispatch != null) throw new AssertionError("Ambiguous remote animation helper call");
            dispatch = call;
        }
        require(dispatch != null, "Missing actual remote animation helper call");

        int diReads = 0;
        for (AbstractInsnNode instruction : receiver.instructions.toArray())
            if (isField(instruction, GETFIELD, entity, "di", "L" + dispatch.owner + ";")) diReads++;
        require(diReads == 1, "Expected one receiver read of the dispatched native di helper");

        ClassNode helperClass = node(jar.get(dispatch.owner + ".class"));
        MethodNode helper = findMethod(helperClass, dispatch.name, dispatch.desc);
        List<AbstractInsnNode> real = new ArrayList<AbstractInsnNode>();
        List<Integer> cG = new ArrayList<Integer>();
        for (AbstractInsnNode instruction : helper.instructions.toArray()) {
            if (instruction.getOpcode() >= 0) {
                if (isField(instruction, GETFIELD, entity, "cG", "I")) cG.add(Integer.valueOf(real.size()));
                real.add(instruction);
            }
        }
        require(cG.size() == 2, "Native helper must retain exactly two cG comparisons");
        int first = cG.get(0).intValue(), second = cG.get(1).intValue();
        require(first >= 5 && second >= 5, "cG guard locals are in range");

        // Each cG comparison is preceded by a null guard and then loads the same
        // helper entity field again. The branch labels must preserve the native gate.
        require(isVar(real.get(first - 5), ALOAD, 0), "Actor null-guard load shape");
        checkField(real.get(first - 4), GETFIELD, helperClass.name, nullSafeFieldName(
                real.get(first - 4)), "L" + entity + ";", "actor reference");
        require(real.get(first - 3) instanceof JumpInsnNode
                && real.get(first - 3).getOpcode() == IFNULL, "Actor null branch shape");
        require(isVar(real.get(first - 2), ALOAD, 0), "Actor cG load shape");
        require(real.get(first - 1) instanceof FieldInsnNode
                && real.get(first - 1).getOpcode() == GETFIELD
                && ((FieldInsnNode)real.get(first - 1)).owner.equals(helperClass.name)
                && ((FieldInsnNode)real.get(first - 1)).desc.equals("L" + entity + ";"),
                "Actor cG source field shape");
        require(real.get(first + 1).getOpcode() == ICONST_4
                && real.get(first + 2) instanceof JumpInsnNode
                && real.get(first + 2).getOpcode() == IF_ICMPEQ,
                "Actor cG4 branch shape");

        require(isVar(real.get(second - 5), ALOAD, 0), "Target null-guard load shape");
        require(real.get(second - 4) instanceof FieldInsnNode
                && real.get(second - 4).getOpcode() == GETFIELD
                && ((FieldInsnNode)real.get(second - 4)).owner.equals(helperClass.name)
                && ((FieldInsnNode)real.get(second - 4)).desc.equals("L" + entity + ";"),
                "Target null-guard source field shape");
        require(real.get(second - 3) instanceof JumpInsnNode
                && real.get(second - 3).getOpcode() == IFNULL, "Target null branch shape");
        require(isVar(real.get(second - 2), ALOAD, 0), "Target cG load shape");
        require(real.get(second - 1) instanceof FieldInsnNode
                && real.get(second - 1).getOpcode() == GETFIELD
                && ((FieldInsnNode)real.get(second - 1)).owner.equals(helperClass.name)
                && ((FieldInsnNode)real.get(second - 1)).desc.equals("L" + entity + ";"),
                "Target cG source field shape");
        require(real.get(second + 1).getOpcode() == ICONST_4
                && real.get(second + 2) instanceof JumpInsnNode
                && real.get(second + 2).getOpcode() == IF_ICMPNE,
                "Target live cG branch shape");

        FieldInsnNode actorRef = (FieldInsnNode)real.get(first - 4);
        FieldInsnNode actorCgRef = (FieldInsnNode)real.get(first - 1);
        FieldInsnNode targetNullRef = (FieldInsnNode)real.get(second - 4);
        FieldInsnNode targetCgRef = (FieldInsnNode)real.get(second - 1);
        FieldInsnNode cg1 = (FieldInsnNode)real.get(first);
        FieldInsnNode cg2 = (FieldInsnNode)real.get(second);
        require(actorRef.name.equals(actorCgRef.name) && targetNullRef.name.equals(targetCgRef.name)
                && !actorRef.name.equals(targetNullRef.name), "Distinct actor and first-target source fields");
        checkField(cg1, GETFIELD, entity, "cG", "I", "actor cG comparison");
        checkField(cg2, GETFIELD, entity, "cG", "I", "target cG comparison");

        JumpInsnNode actorNull = (JumpInsnNode)real.get(first - 3);
        JumpInsnNode actorDead = (JumpInsnNode)real.get(first + 2);
        JumpInsnNode targetNull = (JumpInsnNode)real.get(second - 3);
        JumpInsnNode targetLive = (JumpInsnNode)real.get(second + 2);
        require(actorNull.label == actorDead.label && actorNull.label == targetNull.label,
                "Native cG/null failures share the original cleanup branch");
        LabelNode cleanup = actorNull.label;
        LabelNode good = targetLive.label;
        require(helper.instructions.indexOf(cleanup) >= 0 && helper.instructions.indexOf(good) >= 0
                && helper.instructions.indexOf(cleanup) < helper.instructions.indexOf(good),
                "Native cleanup and successful target labels are ordered");

        // Ensure the early path still performs all three real actor-field resets.
        List<AbstractInsnNode> cleanupReal = new ArrayList<AbstractInsnNode>();
        boolean inCleanup = false;
        for (AbstractInsnNode instruction : helper.instructions.toArray()) {
            if (instruction == cleanup) inCleanup = true;
            if (instruction == good) break;
            if (inCleanup && instruction.getOpcode() >= 0) cleanupReal.add(instruction);
        }
        require(cleanupReal.size() == 10, "Native early-return block has the expected three resets");
        String[] resetNames = new String[3];
        String[] resetDescs = new String[3];
        int[] resetIndices = new int[]{2, 5, 8};
        for (int i = 0; i < 3; i++) {
            int at = i * 3;
            require(isVar(cleanupReal.get(at), ALOAD, 1), "Cleanup reset actor local " + i);
            require(cleanupReal.get(at + 1).getOpcode() == (i < 2 ? ICONST_M1 : ACONST_NULL),
                    "Cleanup reset value " + i);
            AbstractInsnNode reset = cleanupReal.get(resetIndices[i]);
            require(reset instanceof FieldInsnNode && reset.getOpcode() == PUTFIELD,
                    "Cleanup reset field " + i);
            FieldInsnNode field = (FieldInsnNode)reset;
            require(field.owner.equals(entity) && field.desc.equals(i < 2 ? "I" : "Let;"),
                    "Cleanup reset owner/type " + i);
            resetNames[i] = field.name;
            resetDescs[i] = field.desc;
        }
        require(cleanupReal.get(9).getOpcode() == RETURN, "Native early branch returns after resets");
        require(!resetNames[0].equals(resetNames[1]) && !resetNames[0].equals(resetNames[2])
                && !resetNames[1].equals(resetNames[2]), "Native cleanup reset fields are distinct");

        AbstractInsnNode start = real.get(first - 5);
        AbstractInsnNode end = good;
        require(start instanceof VarInsnNode && end instanceof LabelNode, "Native slice endpoints");
        Map<LabelNode, LabelNode> labels = new HashMap<LabelNode, LabelNode>();
        for (AbstractInsnNode instruction = start; instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof LabelNode) labels.put((LabelNode)instruction, new LabelNode());
            if (instruction == end) break;
        }
        require(labels.containsKey(cleanup) && labels.containsKey(good), "Native branch labels stay inside slice");

        ClassNode sliceClass = new ClassNode();
        sliceClass.version = V1_4;
        sliceClass.access = ACC_PUBLIC | ACC_FINAL;
        sliceClass.name = SLICE;
        sliceClass.superName = "java/lang/Object";
        MethodNode method = new MethodNode(ACC_PUBLIC | ACC_STATIC, "allows",
                "(L" + entity + ";L" + entity + ";)Z", null, null);
        for (AbstractInsnNode instruction = start; instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof VarInsnNode) {
                VarInsnNode load = (VarInsnNode)instruction;
                require(load.getOpcode() == ALOAD && (load.var == 0 || load.var == 1),
                        "Unexpected native guard local load");
                if (load.var == 0) {
                    AbstractInsnNode fieldNode = instruction.getNext();
                    require(fieldNode instanceof FieldInsnNode && fieldNode.getOpcode() == GETFIELD
                            && ((FieldInsnNode)fieldNode).owner.equals(helperClass.name)
                            && ((FieldInsnNode)fieldNode).desc.equals("L" + entity + ";"),
                            "Native helper field is paired with its receiver load");
                    FieldInsnNode field = (FieldInsnNode)fieldNode;
                    require(field.name.equals(actorRef.name) || field.name.equals(targetNullRef.name),
                            "Unexpected native actor/target source field");
                    method.instructions.add(new VarInsnNode(ALOAD,
                            field.name.equals(actorRef.name) ? 0 : 1));
                    instruction = fieldNode;
                } else {
                    // The native helper's cleanup stores target its actor parameter.
                    method.instructions.add(new VarInsnNode(ALOAD, 0));
                }
            } else if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (field.getOpcode() == GETFIELD) {
                    require(field.owner.equals(entity) && field.name.equals("cG") && field.desc.equals("I"),
                            "Unexpected native guard read");
                    method.instructions.add(instruction.clone(labels));
                } else {
                    require(field.getOpcode() == PUTFIELD && field.owner.equals(entity),
                            "Unexpected native guard write");
                    int reset = -1;
                    for (int i = 0; i < resetNames.length; i++)
                        if (field.name.equals(resetNames[i]) && field.desc.equals(resetDescs[i])) reset = i;
                    require(reset >= 0, "Unexpected native cleanup field");
                    method.instructions.add(instruction.clone(labels));
                }
            } else if (instruction instanceof JumpInsnNode) {
                JumpInsnNode branch = (JumpInsnNode)instruction;
                require((branch.getOpcode() == IFNULL || branch.getOpcode() == IF_ICMPEQ
                        || branch.getOpcode() == IF_ICMPNE)
                        && labels.containsKey(branch.label), "Unexpected native guard branch");
                method.instructions.add(instruction.clone(labels));
            } else if (instruction instanceof LabelNode) {
                method.instructions.add(instruction.clone(labels));
            } else if (instruction instanceof FrameNode || instruction instanceof LineNumberNode) {
                // Metadata does not alter the copied native branch semantics.
            } else if (instruction instanceof InsnNode) {
                int opcode = instruction.getOpcode();
                if (opcode == RETURN) {
                    require(instruction == cleanupReal.get(9), "Only the verified native early return is copied");
                    method.instructions.add(new InsnNode(ICONST_0));
                    method.instructions.add(new InsnNode(IRETURN));
                } else {
                    require(opcode == ICONST_4 || opcode == ICONST_M1 || opcode == ACONST_NULL,
                            "Unexpected native guard constant");
                    method.instructions.add(instruction.clone(labels));
                }
            } else {
                throw new AssertionError("Unexpected instruction in native guard slice: "
                        + instruction.getClass().getName());
            }
            if (instruction == end) break;
        }
        method.instructions.add(new InsnNode(ICONST_1));
        method.instructions.add(new InsnNode(IRETURN));
        method.maxLocals = 2;
        sliceClass.methods.add(method);
        return bytes(sliceClass);
    }

    private static String nullSafeFieldName(AbstractInsnNode instruction) {
        if (!(instruction instanceof FieldInsnNode)) throw new AssertionError("Missing native actor field");
        return ((FieldInsnNode)instruction).name;
    }

    private static byte[] combat(int id, int hp) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(777);
        out.writeByte(0);
        out.writeByte(1);
        out.writeShort(id);
        out.writeInt(13);
        out.writeInt(hp);
        out.writeByte(0);
        out.writeInt(1234);
        out.writeInt(567);
        out.writeByte(0);
        out.writeInt(0);
        out.flush();
        return bytes.toByteArray();
    }

    private static void snapshot(HungBySonMonsterRecoveryBinaryTest.Harness h, int id, int hp)
            throws Exception {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(combat(id, hp)));
        int before = input.available();
        h.call("onCombatSnapshot", new Class<?>[]{h.api, DataInputStream.class}, h.access, input);
        require(input.available() == before, "Combat observer restores the native packet cursor");
    }

    private static boolean allows(HungBySonMonsterRecoveryBinaryTest.Harness h,
            Object actor, Object firstTarget) throws Exception {
        Class<?> slice = h.loader.loadClass(SLICE.replace('/', '.'));
        return ((Boolean)slice.getMethod("allows", h.entity, h.entity).invoke(null, actor, firstTarget)).booleanValue();
    }

    private static void resetActor(HungBySonMonsterRecoveryBinaryTest.Harness h, Object actor)
            throws Exception {
        HungBySonMonsterRecoveryBinaryTest.set(actor, "cA", 7);
        HungBySonMonsterRecoveryBinaryTest.set(actor, "cR", 8);
        HungBySonMonsterRecoveryBinaryTest.set(actor, "cS", h.vector);
    }

    private static void assertCleanup(Object actor, String label) throws Exception {
        require(HungBySonMonsterRecoveryBinaryTest.get(actor, "cA") == -1
                && HungBySonMonsterRecoveryBinaryTest.get(actor, "cR") == -1
                && actor.getClass().getField("cS").get(actor) == null,
                "Native early branch retains its animation reset " + label);
    }

    private static final class Setup {
        final Map<String, byte[]> jar;
        final Properties catalog;
        final HungBySonMonsterRecoveryBinaryTest.Harness harness;
        final Object corpse;
        Setup(File jarFile, Properties catalog, int id) throws Exception {
            this.catalog = catalog;
            jar = read(jarFile);
            jar.put(SLICE + ".class", actualGuardSlice(jar, catalog));
            harness = new HungBySonMonsterRecoveryBinaryTest.Harness(jar, catalog);
            corpse = harness.make(id);
            harness.learn(id);
            harness.detail(id, 0);
            harness.worldFrame();
            require(HungBySonMonsterRecoveryBinaryTest.get(corpse, "cG") == 4
                    && harness.hold(corpse), "Harness creates an actual cache-held cG4 corpse " + id);
        }
    }

    private static Object actor(Setup setup) throws Exception {
        Object actor = HungBySonMonsterRecoveryBinaryTest.allocate(setup.harness.entity);
        HungBySonMonsterRecoveryBinaryTest.set(actor, "cv", (byte)0);
        HungBySonMonsterRecoveryBinaryTest.set(actor, "cG", 0);
        return actor;
    }

    private static void candidate(File candidate, Properties catalog) throws Exception {
        Setup setup = new Setup(candidate, catalog, 41);
        Object actor = actor(setup);
        resetActor(setup.harness, actor);
        require(!allows(setup.harness, actor, setup.corpse),
                "Copied native helper blocks a remote actor on the real corpse cG4 guard");
        assertCleanup(actor, "for corpse target");

        snapshot(setup.harness, 41, 37);
        require(HungBySonMonsterRecoveryBinaryTest.get(setup.corpse, "bs") == 37
                && HungBySonMonsterRecoveryBinaryTest.get(setup.corpse, "cG") == 0
                && !HungBySonMonsterRecoveryBinaryTest.bool(setup.corpse, "dz"),
                "Candidate positive combat snapshot releases the confirmed current corpse before dispatch");
        resetActor(setup.harness, actor);
        require(allows(setup.harness, actor, setup.corpse),
                "Copied native helper allows dispatch after candidate bridge releases target cG4");
        require(HungBySonMonsterRecoveryBinaryTest.get(actor, "cA") == 7
                && HungBySonMonsterRecoveryBinaryTest.get(actor, "cR") == 8
                && actor.getClass().getField("cS").get(actor) == setup.harness.vector,
                "Successful native path preserves actor animation state");

        HungBySonMonsterRecoveryBinaryTest.set(actor, "cG", 4);
        resetActor(setup.harness, actor);
        require(!allows(setup.harness, actor, setup.corpse),
                "A genuinely cG4 actor still takes the native early return");
        assertCleanup(actor, "for dead actor");
        require(HungBySonMonsterRecoveryBinaryTest.get(actor, "cG") == 4,
                "The test slice does not revive a dead actor");

        Setup replacementSetup = new Setup(candidate, catalog, 51);
        Object replacement = replacementSetup.harness.make(51);
        HungBySonMonsterRecoveryBinaryTest.set(replacement, "bs", 0);
        HungBySonMonsterRecoveryBinaryTest.set(replacement, "cG", 4);
        snapshot(replacementSetup.harness, 51, 37);
        require(HungBySonMonsterRecoveryBinaryTest.get(replacement, "bs") == 0
                && HungBySonMonsterRecoveryBinaryTest.get(replacement, "cG") == 4
                && HungBySonMonsterRecoveryBinaryTest.get(replacementSetup.corpse, "cG") == 4,
                "Positive combat does not release an unconfirmed same-ID replacement");
        Object replacementActor = actor(replacementSetup);
        require(!allows(replacementSetup.harness, replacementActor, replacement),
                "Replacement remains behind the native cG4 barrier");

        Setup unconfirmedSetup = new Setup(candidate, catalog, 61);
        Object unconfirmed = unconfirmedSetup.harness.make(62);
        HungBySonMonsterRecoveryBinaryTest.set(unconfirmed, "bs", 0);
        HungBySonMonsterRecoveryBinaryTest.set(unconfirmed, "cG", 4);
        snapshot(unconfirmedSetup.harness, 62, 37);
        require(HungBySonMonsterRecoveryBinaryTest.get(unconfirmed, "bs") == 0
                && HungBySonMonsterRecoveryBinaryTest.get(unconfirmed, "cG") == 4,
                "Positive combat does not release an unconfirmed monster placeholder");
        require(!allows(unconfirmedSetup.harness, actor(unconfirmedSetup), unconfirmed),
                "Unconfirmed placeholder remains behind the native cG4 barrier");
    }

    private static void baseline(File baseline, Properties catalog) throws Exception {
        Map<String, byte[]> nativeJar = read(baseline);
        String receiverOwner = catalog.getProperty("hook.unseenCombat.owner");
        MethodNode receiver = findMethod(node(nativeJar.get(receiverOwner + ".class")),
                catalog.getProperty("hook.unseenCombat.method"), catalog.getProperty("hook.unseenCombat.descriptor"));
        String entity = catalog.getProperty("entity.owner");
        String dispatchDesc = "(L" + entity + ";Let;IB)V";
        int dispatchAt = -1, healthWriteAfter = -1, observerAt = -1, offset = 0;
        for (AbstractInsnNode instruction : receiver.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.desc.equals(dispatchDesc)) {
                    require(dispatchAt < 0, "Legacy receiver has one remote helper dispatch");
                    dispatchAt = offset;
                }
                if (call.owner.equals("luna/features/MonsterCacheSupport")
                        && call.name.equals("onCombatSnapshot")) observerAt = offset;
            }
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == PUTFIELD
                    && isField(instruction, PUTFIELD, entity, "bs", "I") && dispatchAt >= 0
                    && healthWriteAfter < 0) healthWriteAfter = offset;
            offset++;
        }
        require(observerAt >= 0 && observerAt < dispatchAt,
                "Pinned baseline has the pre-native observer but its old bridge does not release corpse state");
        require(dispatchAt >= 0 && healthWriteAfter > dispatchAt,
                "Pinned baseline dispatches the helper before its own HP write");

        Setup setup = new Setup(baseline, catalog, 71);
        Object actor = actor(setup);
        snapshot(setup.harness, 71, 37);
        require(HungBySonMonsterRecoveryBinaryTest.get(setup.corpse, "bs") == 37
                && HungBySonMonsterRecoveryBinaryTest.get(setup.corpse, "cG") == 4,
                "Baseline old bridge applies positive HP but leaves the cG4 marker intact");
        require(!allows(setup.harness, actor, setup.corpse),
                "Baseline actual helper guard stays blocked after its positive HP update");
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && "--current-input".equals(args[3])) {
            File candidate = new File(args[0]);
            File currentInput = new File(args[2]);
            Properties catalog = properties(args[1]);
            require(candidate.isFile() && currentInput.isFile(), "Current input and candidate JARs exist");
            require(sha256(candidate).equals(catalog.getProperty("output.sha256")),
                    "Current candidate hash matches its staged validation catalog");
            require(!sha256(candidate).equals(sha256(currentInput)),
                    "Current-input barrier comparison uses a distinct staged candidate");
            candidate(currentInput, catalog);
            candidate(candidate, catalog);
            System.out.println("HungBySonRemoteAnimationBarrierTest PASS (" + checks
                    + " checks); current input and candidate retain the native cG barrier/release path");
            return;
        }
        require(args.length == 3, "candidate-E.jar candidate-E.properties baseline-old.jar [current input mode]");
        File candidate = new File(args[0]);
        File baseline = new File(args[2]);
        require(CANDIDATE_HASH.equals(sha256(candidate)), "Pinned candidate E hash");
        require(BASELINE_HASH.equals(sha256(baseline)), "Pinned pre-release baseline hash");
        Properties candidateCatalog = properties(args[1]);
        require(CANDIDATE_HASH.equals(candidateCatalog.getProperty("output.sha256")),
                "Candidate E catalog matches the tested JAR");
        candidate(candidate, candidateCatalog);
        baseline(baseline, candidateCatalog);
        System.out.println("HungBySonRemoteAnimationBarrierTest PASS (" + checks
                + " checks); copied native helper guard, candidate lifecycle release, legacy bridge and identity quarantine");
    }
}
