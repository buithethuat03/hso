import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Binary integration regression for packet 4 object batches. A departed actor
 * (cL=true) can remain in the native vector until the next world update. The
 * following packet-4 record must still be parsed, and a normal record for the
 * departed actor must create a live replacement with the same (id,type).
 * State 127 follows the native not-found/update paths: it may create a missing
 * player placeholder, does not respawn a missing NPC/static actor, and must
 * continue through later records in the packet.
 *
 * The native receiver, entity lookup/factory, world-vector add, and object
 * request serializers execute from each JAR. Only the existing in-memory
 * network queue boundary from HungBySonAutoAttackBinaryTest is captured.
 *
 * Usage: <input.jar> <candidate.jar>
 */
public final class HungBySonMapObjectLoadingTest {
    private static final String HELPER = "HungBySonAutoAttackBinaryTest";
    private static final String MESSAGE = encoded("lllllil");
    private static final String HANDLER = encoded("lllllll");
    private static final String PACKET4 = encoded("li");
    private static final String ENTITY = encoded("liiiliii");
    private static final String ENTITY_LIST_OWNER = encoded("liiliii");
    private static final String ENTITY_LIST_FIELD = encoded("llillilll");
    private static final String PLACEHOLDER = encoded("ilillil");
    private static final String WORLD = encoded("liiliii");
    private static final String WORLD_RESET = encoded("liiil");
    private static final String PREVIOUS_MAP = "codexPreviousMap";

    private static int checks;

    private HungBySonMapObjectLoadingTest() { }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }

    private static String encoded(String suffix) {
        StringBuilder value = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) value.append('i');
        return value.append(suffix).toString();
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 2, "<input.jar> <candidate.jar>");
        File input = new File(args[0]);
        File candidate = new File(args[1]);
        require(input.isFile() && candidate.isFile(), "Input and candidate JARs exist");
        require(!Arrays.equals(readClass(input, HANDLER), readClass(candidate, HANDLER)),
                "Candidate contains the packet-4 receiver change");
        verifyPreviousMapMarker(input, candidate);

        List<Record> head = records(new Record(2, 101, 20, 30, 0),
                new Record(2, 102, 40, 50, 0), new Record(2, 103, 60, 70, 0));
        verifyDepartedReplacement(input, candidate, "departed type-2 actor at batch head", head, 0,
                new int[]{101, 102, 103}, new int[]{-44, -44, -44});

        List<Record> middle = records(new Record(2, 201, 20, 30, 0),
                new Record(2, 202, 40, 50, 0), new Record(2, 203, 60, 70, 0));
        verifyDepartedReplacement(input, candidate, "departed type-2 actor in batch middle", middle, 1,
                new int[]{201, 202, 203}, new int[]{-44, -44, -44});

        List<Record> tail = records(new Record(2, 301, 20, 30, 0),
                new Record(2, 302, 40, 50, 0), new Record(2, 303, 60, 70, 0));
        verifyDepartedReplacement(input, candidate, "departed type-2 actor at batch tail", tail, 2,
                new int[]{301, 302, 303}, new int[]{-44, -44, -44});

        List<Record> player = records(new Record(0, 401, 20, 30, 0),
                new Record(2, 402, 40, 50, 0), new Record(2, 403, 60, 70, 0));
        verifyDepartedReplacement(input, candidate, "departed player at batch head", player, 0,
                new int[]{401, 402, 403}, new int[]{5, -44, -44});

        verifyState127(input, candidate, false);
        verifyState127(input, candidate, true);
        verifyPriorMapPlayerState127(input, candidate);
        verifyMissingPlayerState127(input, candidate);
        verifyFullNpcMapRecord(input, candidate);
        verifySameMapDeparture(input, candidate);
        verifyMultiplePackets(input, candidate);

        System.out.println("HungBySonMapObjectLoadingTest PASS checks=" + checks
                + "; native packet-4 batches, stale actor replacement, state-127 continuation, and request packets");
    }

    private static void verifyPreviousMapMarker(File input, File candidate) throws Exception {
        ClassNode oldEntity = readNode(input, ENTITY);
        ClassNode newEntity = readNode(candidate, ENTITY);
        FieldNode marker = null;
        for (FieldNode field : newEntity.fields) {
            if (PREVIOUS_MAP.equals(field.name)) {
                require(marker == null && "Z".equals(field.desc)
                                && (field.access & org.objectweb.asm.Opcodes.ACC_PUBLIC) != 0,
                        "Previous-map marker is one public boolean field");
                marker = field;
            }
        }
        require(marker != null && findField(oldEntity, PREVIOUS_MAP) == null,
                "Only the candidate adds the previous-map marker");

        int writers = 0;
        String writer = null;
        JarFile jar = new JarFile(candidate);
        try {
            java.util.Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                java.io.InputStream stream = jar.getInputStream(entry);
                ClassNode type = new ClassNode();
                try { new ClassReader(stream).accept(type, 0); }
                finally { stream.close(); }
                for (MethodNode method : type.methods) {
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (!(instruction instanceof FieldInsnNode)) continue;
                        FieldInsnNode field = (FieldInsnNode)instruction;
                        if (field.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD
                                && ENTITY.equals(field.owner) && PREVIOUS_MAP.equals(field.name)
                                && "Z".equals(field.desc)) {
                            writers++;
                            writer = type.name + "." + method.name + method.desc;
                            require(type.name.equals(WORLD) && method.name.equals(WORLD_RESET)
                                            && "()V".equals(method.desc),
                                    "Only native current-world reset writes the previous-map marker");
                            requireImmediatelyAfterRemovalFlag(method, field);
                        }
                    }
                }
            }
        } finally { jar.close(); }
        require(writers == 1 && (WORLD + "." + WORLD_RESET + "()V").equals(writer),
                "Exactly one previous-map writer exists in native world reset");

        MethodNode before = findMethod(readNode(input, WORLD), WORLD_RESET, "()V");
        MethodNode after = findMethod(readNode(candidate, WORLD), WORLD_RESET, "()V");
        Set<AbstractInsnNode> inserted = markerWriteSequence(after);
        require(methodShape(before, Collections.<AbstractInsnNode>emptySet())
                        .equals(methodShape(after, inserted)),
                "Removing the marker write restores the native reset method, including local/special actor exemptions");
    }

    private static void requireImmediatelyAfterRemovalFlag(MethodNode method, FieldInsnNode marker) {
        AbstractInsnNode value = previousReal(marker);
        AbstractInsnNode owner = previousReal(value);
        AbstractInsnNode removalFlag = previousReal(owner);
        AbstractInsnNode removalValue = previousReal(removalFlag);
        AbstractInsnNode removalOwner = previousReal(removalValue);
        require(value != null && value.getOpcode() == org.objectweb.asm.Opcodes.ICONST_1
                        && owner instanceof VarInsnNode && owner.getOpcode() == org.objectweb.asm.Opcodes.ALOAD
                        && removalFlag instanceof FieldInsnNode && removalFlag.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD
                        && "cL".equals(((FieldInsnNode)removalFlag).name)
                        && ENTITY.equals(((FieldInsnNode)removalFlag).owner)
                        && removalValue != null && removalValue.getOpcode() == org.objectweb.asm.Opcodes.ICONST_1
                        && removalOwner instanceof VarInsnNode && removalOwner.getOpcode() == org.objectweb.asm.Opcodes.ALOAD
                        && ((VarInsnNode)owner).var == ((VarInsnNode)removalOwner).var,
                "Map reset marks the same actor immediately after setting cL=true");
    }

    private static Set<AbstractInsnNode> markerWriteSequence(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof FieldInsnNode)) continue;
            FieldInsnNode field = (FieldInsnNode)instruction;
            if (field.getOpcode() != org.objectweb.asm.Opcodes.PUTFIELD
                    || !ENTITY.equals(field.owner) || !PREVIOUS_MAP.equals(field.name)) continue;
            Set<AbstractInsnNode> removed = Collections.newSetFromMap(
                    new IdentityHashMap<AbstractInsnNode, Boolean>());
            removed.add(field);
            removed.add(previousReal(field));
            removed.add(previousReal(previousReal(field)));
            return removed;
        }
        throw new AssertionError("Missing marker writer in native reset");
    }

    private static String methodShape(MethodNode method, Set<AbstractInsnNode> ignored) {
        Map<LabelNode, Integer> labels = new IdentityHashMap<LabelNode, Integer>();
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LabelNode) labels.put((LabelNode)instruction, Integer.valueOf(index));
            if (instruction.getOpcode() >= 0 && !ignored.contains(instruction)) index++;
        }
        StringBuilder result = new StringBuilder();
        index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() < 0 || ignored.contains(instruction)) continue;
            result.append(index++).append(':').append(instruction.getOpcode());
            if (instruction instanceof VarInsnNode) result.append(':').append(((VarInsnNode)instruction).var);
            else if (instruction instanceof IntInsnNode) result.append(':').append(((IntInsnNode)instruction).operand);
            else if (instruction instanceof TypeInsnNode) result.append(':').append(((TypeInsnNode)instruction).desc);
            else if (instruction instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode)instruction;
                result.append(':').append(f.owner).append('.').append(f.name).append(f.desc);
            } else if (instruction instanceof MethodInsnNode) {
                MethodInsnNode m = (MethodInsnNode)instruction;
                result.append(':').append(m.owner).append('.').append(m.name).append(m.desc).append(':').append(m.itf);
            } else if (instruction instanceof JumpInsnNode) {
                result.append(':').append(labelPosition(labels, ((JumpInsnNode)instruction).label));
            } else if (instruction instanceof LdcInsnNode) {
                result.append(':').append(String.valueOf(((LdcInsnNode)instruction).cst));
            } else if (instruction instanceof IincInsnNode) {
                IincInsnNode i = (IincInsnNode)instruction;
                result.append(':').append(i.var).append(':').append(i.incr);
            } else if (instruction instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode s = (TableSwitchInsnNode)instruction;
                result.append(':').append(s.min).append(':').append(s.max).append(':')
                        .append(labelPosition(labels, s.dflt));
                for (LabelNode label : s.labels) result.append(':').append(labelPosition(labels, label));
            } else if (instruction instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode s = (LookupSwitchInsnNode)instruction;
                result.append(':').append(labelPosition(labels, s.dflt));
                for (int i = 0; i < s.keys.size(); i++)
                    result.append(':').append(s.keys.get(i)).append('=').append(labelPosition(labels, s.labels.get(i)));
            } else if (instruction instanceof MultiANewArrayInsnNode) {
                MultiANewArrayInsnNode a = (MultiANewArrayInsnNode)instruction;
                result.append(':').append(a.desc).append(':').append(a.dims);
            }
            result.append(';');
        }
        for (org.objectweb.asm.tree.TryCatchBlockNode block : method.tryCatchBlocks) {
            result.append("try:").append(labelPosition(labels, block.start)).append('-')
                    .append(labelPosition(labels, block.end)).append('>')
                    .append(labelPosition(labels, block.handler)).append(':').append(block.type).append(';');
        }
        return result.toString();
    }

    private static int labelPosition(Map<LabelNode, Integer> labels, LabelNode label) {
        Integer position = labels.get(label);
        if (position == null) throw new AssertionError("Unmapped bytecode label");
        return position.intValue();
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }

    private static void verifyDepartedReplacement(File input, File candidate, String label,
            List<Record> batch, int staleIndex, int[] expectedIds, int[] expectedOpcodes) throws Exception {
        int staleId = batch.get(staleIndex).id;
        int detailId = batch.get(staleIndex).type == 2 ? staleId : -1;
        Outcome old = run(input, new List[]{batch}, staleIndex, true, true, true, detailId);
        Outcome fixed = run(candidate, new List[]{batch}, staleIndex, true, true, true, detailId);
        require(old.requests.size() < expectedOpcodes.length,
                "Regression control " + label + " reproduces missing requests: " + old.requests);
        require(old.count(staleId, batch.get(staleIndex).type, false) == 0,
                "Regression control " + label + " leaves the departed actor unreplaced");
        for (int i = 0; i < expectedIds.length; i++) {
            int id = expectedIds[i];
            int type = batch.get(i).type;
            require(fixed.count(id, type, false) == 1,
                    label + " keeps a live native entity for id=" + id + " type=" + type
                            + " (live count=" + fixed.count(id, type, false) + ")");
            require(fixed.hasRequest(expectedOpcodes[i], id),
                    label + " emits native opcode " + expectedOpcodes[i] + " for id=" + id
                            + " (captured=" + fixed.requests + ")");
        }
        require(fixed.requests.size() == expectedOpcodes.length,
                label + " emits exactly one detail request per newly-created actor: " + fixed.requests);
        if (detailId >= 0) {
            ObjectSnapshot oldInstance = fixed.find(detailId, 2, true);
            ObjectSnapshot replacement = fixed.find(detailId, 2, false);
            require(oldInstance != null && "".equals(oldInstance.name)
                            && oldInstance.x == batch.get(staleIndex).x && oldInstance.y == batch.get(staleIndex).y,
                    label + " keeps the prior-map cL instance unchanged after the detail response");
            require(replacement != null && ("NPC-DETAIL-" + detailId).equals(replacement.name)
                            && replacement.targetX == 777 && replacement.targetY == 888,
                    label + " routes the native detail response to the new same-id entity");
        }
    }

    private static void verifyState127(File input, File candidate, boolean departed) throws Exception {
        int id = departed ? 511 : 501;
        List<Record> batch = records(new Record(2, id, 27, 44, 127), new Record(2, id + 1, 55, 66, 0));
        Outcome old = run(input, new List[]{batch}, 0, true, departed, departed);
        Outcome fixed = run(candidate, new List[]{batch}, 0, true, departed, departed);
        require(fixed.count(id, 2, false) == (departed ? 0 : 1),
                "state=127 " + (departed ? "does not resurrect a departed actor" : "updates an existing actor"));
        require(fixed.hasRequest(-44, id + 1),
                "state=127 continues to the following type-2 record; captured=" + fixed.requests);
        require(!fixed.hasRequest(-44, id), "state=127 itself emits no type-2 detail request");
        if (departed) {
            require(old.requests.size() < fixed.requests.size(),
                    "Regression control state=127 departed actor demonstrates the old whole-batch return");
        } else {
            require(old.requests.size() == 1,
                    "Existing live state=127 behavior continues through the following record");
        }
        if (!departed) {
            ObjectSnapshot current = fixed.find(id, 2, false);
            require(current != null && current.ea && current.eb == 44,
                    "state=127 preserves native ea/eb update on a live actor");
        }
    }

    private static void verifyMissingPlayerState127(File input, File candidate) throws Exception {
        List<Record> batch = records(new Record(0, 521, 27, 44, 127), new Record(2, 522, 55, 66, 0));
        Outcome old = run(input, new List[]{batch}, -1, false, false, false);
        Outcome fixed = run(candidate, new List[]{batch}, -1, false, false, false);
        require(fixed.count(521, 0, false) == 1,
                "Missing type-0 state=127 record leaves its native placeholder in the vector");
        ObjectSnapshot player = fixed.find(521, 0, false);
        require(player != null && player.ea && player.eb == 44,
                "Missing type-0 state=127 record writes ea/eb to the created placeholder");
        require(fixed.hasRequest(-44, 522),
                "Missing type-0 state=127 record does not abort the following type-2 request: " + fixed.requests);
        require(old.count(522, 2, false) == 0,
                "Regression control: missing type-0 state=127 record aborts the rest of the old batch");
    }

    private static void verifyPriorMapPlayerState127(File input, File candidate) throws Exception {
        List<Record> batch = records(new Record(0, 515, 27, 44, 127), new Record(2, 516, 55, 66, 0));
        Outcome old = run(input, new List[]{batch}, 0, true, true, true);
        Outcome fixed = run(candidate, new List[]{batch}, 0, true, true, true);
        require(fixed.count(515, 0, false) == 1,
                "Map-stale type-0 state=127 record follows the native not-found placeholder branch");
        ObjectSnapshot player = fixed.find(515, 0, false);
        require(player != null && player.ea && player.eb == 44,
                "Map-stale type-0 state=127 record writes ea/eb to its new placeholder");
        require(fixed.hasRequest(-44, 516),
                "Map-stale type-0 state=127 record continues into the next type-2 detail request");
        require(old.count(516, 2, false) == 0,
                "Regression control: map-stale type-0 state=127 returns before later records");
    }

    private static void verifyFullNpcMapRecord(File input, File candidate) throws Exception {
        int id = 81;
        List<Record> packet4 = records(new Record(2, id, 901, 902, 0));
        Record fullNpc = new Record(2, id, 801, 802, 0);
        Outcome old = run(input, new List[]{packet4}, 0, true, true, true, -1, fullNpc);
        Outcome fixed = run(candidate, new List[]{packet4}, 0, true, true, true, -1, fullNpc);
        ObjectSnapshot oldMapActor = fixed.find(id, 2, true);
        ObjectSnapshot replacement = fixed.find(id, 2, false);
        require(oldMapActor != null && oldMapActor.x == 901 && oldMapActor.y == 902
                        && "".equals(oldMapActor.name),
                "Full-NPC packet leaves the prior-map cL object untouched");
        require(replacement != null && replacement.className.endsWith(encoded("iliiiilli"))
                        && ("FULL-NPC-" + id).equals(replacement.name),
                "Full-NPC packet creates the native specialized replacement class for the reused ID: "
                        + describe(fixed) + " pre=" + describe(fixed.preFullNpc));
        ObjectSnapshot createdBeforePacket4 = find(fixed.preFullNpc, id, 2, false);
        require(createdBeforePacket4 != null && createdBeforePacket4.className.endsWith(encoded("iliiiilli"))
                        && ("FULL-NPC-" + id).equals(createdBeforePacket4.name)
                        && find(fixed.preFullNpc, id, 2, true) != null,
                "Full-NPC handler creates the specialized replacement before packet 4 arrives");
        require(fixed.count(id, 2, false) == 1 && !fixed.hasRequest(-44, id),
                "Following packet 4 reuses that replacement without duplicate detail request");
        ObjectSnapshot revivedOld = old.find(id, 2, false);
        require(old.count(id, 2, false) >= 1 && old.count(id, 2, true) == 0
                        && revivedOld != null && ("FULL-NPC-" + id).equals(revivedOld.name),
                "Regression control revives the stale object in place instead of replacing it");
    }

    private static void verifySameMapDeparture(File input, File candidate) throws Exception {
        List<Record> batch = records(new Record(2, 531, 27, 44, 0), new Record(2, 532, 55, 66, 0));
        Outcome old = run(input, new List[]{batch}, 0, true, true, false);
        Outcome fixed = run(candidate, new List[]{batch}, 0, true, true, false);
        require(fixed.count(531, 2, false) == 0 && !fixed.hasRequest(-44, 531),
                "Same-map cL actor is not recreated or requested");
        require(fixed.hasRequest(-44, 532) && fixed.count(532, 2, false) == 1,
                "Same-map cL record is skipped while later records in its batch continue");
        require(old.requests.size() < fixed.requests.size(),
                "Regression control: same-map cL actor used to terminate the whole packet");
    }

    private static void verifyMultiplePackets(File input, File candidate) throws Exception {
        List<Record> first = records(new Record(2, 601, 20, 30, 0));
        List<Record> second = records(new Record(2, 602, 40, 50, 0),
                new Record(2, 603, 60, 70, 0));
        Outcome old = run(input, new List[]{first, second}, -1, false, false, false, 601);
        Outcome fixed = run(candidate, new List[]{first, second}, -1, false, false, false, 601);
        require(fixed.count(601, 2, false) == 1 && fixed.count(602, 2, false) == 1
                        && fixed.count(603, 2, false) == 1,
                "Separate packet-4 messages retain independently created native objects");
        require(fixed.hasRequest(-44, 601) && fixed.hasRequest(-44, 602) && fixed.hasRequest(-44, 603)
                        && fixed.requests.size() == 3,
                "Separate packet-4 messages emit each -44 request once: " + fixed.requests);
        require(old.requests.size() == 3,
                "Regression control processes independent messages when no departed actor is present: " + old.requests);
        ObjectSnapshot detail = fixed.find(601, 2, false);
        require(detail != null && "NPC-DETAIL-601".equals(detail.name)
                        && detail.targetX == 777 && detail.targetY == 888,
                "Native detail response also populates an ordinary new type-2 entity");
    }

    private static Outcome run(File jarFile, List<Record>[] packets, int staleIndex) throws Exception {
        return run(jarFile, packets, staleIndex, true, true, true);
    }

    private static Outcome run(File jarFile, List<Record>[] packets, int staleIndex,
            boolean seedActor, boolean departed)
            throws Exception {
        return run(jarFile, packets, staleIndex, seedActor, departed, departed);
    }

    private static Outcome run(File jarFile, List<Record>[] packets, int staleIndex,
            boolean seedActor, boolean departed, boolean previousMap)
            throws Exception {
        return run(jarFile, packets, staleIndex, seedActor, departed, previousMap, -1);
    }

    private static Outcome run(File jarFile, List<Record>[] packets, int staleIndex,
            boolean seedActor, boolean departed, boolean previousMap, int detailId)
            throws Exception {
        return run(jarFile, packets, staleIndex, seedActor, departed, previousMap, detailId, null);
    }

    private static Outcome run(File jarFile, List<Record>[] packets, int staleIndex,
            boolean seedActor, boolean departed, boolean previousMap, int detailId, Record fullNpcBefore)
            throws Exception {
        Class<?> helper = Class.forName(HELPER);
        Properties inert = new Properties();
        inert.setProperty("native.menu.display.owner", "unused");
        inert.setProperty("native.menu.display.method", "unused");
        inert.setProperty("native.menu.display.descriptor", "()V");
        inert.setProperty("native.menu.callback.owner", "unused");
        inert.setProperty("native.menu.callback.descriptor", "()V");
        inert.setProperty("native.hpmp.owner", "unused");
        inert.setProperty("native.hpmp.method", "unused");
        inert.setProperty("native.hpmp.descriptor", "()V");
        Constructor<?> probeCtor = null;
        for (Class<?> nested : helper.getDeclaredClasses()) {
            if (nested.getSimpleName().equals("NativeProbeLoader")) {
                probeCtor = nested.getDeclaredConstructor(File.class, Properties.class);
                break;
            }
        }
        if (probeCtor == null) throw new NoSuchMethodException("NativeProbeLoader(File, Properties)");
        probeCtor.setAccessible(true);
        ClassLoader loader = (ClassLoader)probeCtor.newInstance(jarFile, inert);
        try {
            Field capturedField = helper.getDeclaredField("nativePackets");
            capturedField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> captured = (List<String>)capturedField.get(null);
            synchronized (captured) { captured.clear(); }

            Class<?> entityType = loader.loadClass(ENTITY.replace('/', '.'));
            Class<?> entityListOwner = loader.loadClass(ENTITY_LIST_OWNER.replace('/', '.'));
            Class<?> nativeListType = loader.loadClass("et");
            Object entityList = newNativeList(nativeListType);
            Field entityListField = field(entityListOwner, ENTITY_LIST_FIELD);
            entityListField.set(null, entityList);

            if (seedActor && staleIndex >= 0) {
                Record deadRecord = packets[0].get(staleIndex);
                Object dead = placeholder(loader, entityType, deadRecord.id, deadRecord.type, deadRecord.x, deadRecord.y);
                field(entityType, "cL").setBoolean(dead, departed);
                if (departed && previousMap) setOptionalBoolean(dead, entityType, "codexPreviousMap", true);
                add(entityList, dead);
            }

            enablePacket4World(loader);
            Class<?> messageType = loader.loadClass(MESSAGE.replace('/', '.'));
            Class<?> handlerType = loader.loadClass(HANDLER.replace('/', '.'));
            Method handler = handlerType.getDeclaredMethod(PACKET4, messageType);
            handler.setAccessible(true);
            List<ObjectSnapshot> preFullNpc = null;
            if (fullNpcBefore != null) {
                seedNativeMap(loader);
                deliverFullNpcList(messageType, handlerType, fullNpcBefore);
                preFullNpc = snapshots(entityList, entityType);
            }
            for (List<Record> packet : packets) {
                byte[] bytes = encode(packet);
                Constructor<?> messageConstructor = messageType.getDeclaredConstructor(Byte.TYPE, byte[].class);
                messageConstructor.setAccessible(true);
                Object message = messageConstructor.newInstance(Byte.valueOf((byte)4), bytes);
                try { handler.invoke(null, message); }
                catch (InvocationTargetException failure) { throwCause(failure.getCause()); }
            }
            if (detailId >= 0) deliverType2Detail(messageType, handlerType, detailId);

            List<String> requests;
            synchronized (captured) { requests = new ArrayList<String>(captured); }
            return new Outcome(snapshots(entityList, entityType), requests, preFullNpc);
        } finally {
            try { ((URLClassLoader)loader).close(); } catch (IOException ignored) { }
        }
    }

    private static void deliverType2Detail(Class<?> messageType, Class<?> handlerType, int id) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(id);
        out.writeUTF("NPC-DETAIL-" + id);
        out.writeInt(9000);
        out.writeInt(7000);
        out.writeShort(33);
        out.writeShort(777);
        out.writeShort(888);
        out.writeByte(0);
        out.writeByte(0);
        out.writeUTF("");
        out.writeShort(0);
        out.writeByte(0);
        out.writeShort(0);
        out.flush();
        Constructor<?> constructor = messageType.getDeclaredConstructor(Byte.TYPE, byte[].class);
        constructor.setAccessible(true);
        Object message = constructor.newInstance(Byte.valueOf((byte)-44), bytes.toByteArray());
        Method handler = handlerType.getDeclaredMethod(encoded("ll"), messageType);
        handler.setAccessible(true);
        try { handler.invoke(null, message); }
        catch (InvocationTargetException failure) { throwCause(failure.getCause()); }
    }

    private static void deliverFullNpcList(Class<?> messageType, Class<?> handlerType, Record record) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1);
        out.writeUTF("FULL-NPC-" + record.id);
        out.writeUTF("test subtitle");
        out.writeByte(record.id);
        out.writeByte(4);
        out.writeShort(record.x);
        out.writeShort(record.y);
        out.writeByte(0); out.writeByte(0); out.writeByte(0);
        out.writeByte(0);
        out.writeUTF("");
        out.writeByte(0);
        out.writeByte(0);
        out.flush();
        Constructor<?> constructor = messageType.getDeclaredConstructor(Byte.TYPE, byte[].class);
        constructor.setAccessible(true);
        Object message = constructor.newInstance(Byte.valueOf((byte)-50), bytes.toByteArray());
        Method handler = handlerType.getDeclaredMethod(encoded("liill"), messageType);
        handler.setAccessible(true);
        try { handler.invoke(null, message); }
        catch (InvocationTargetException failure) { throwCause(failure.getCause()); }
    }

    private static void seedNativeMap(ClassLoader loader) throws Exception {
        Class<?> mapType = loader.loadClass("cs");
        // cs() reloads the absent /table_item resource and prints an unrelated
        // caught exception in this headless probe. Seed only the zero-size map
        // state used by this zero-footprint full-NPC record.
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeType.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Object map = unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, mapType);
        field(mapType, "e").setInt(map, 0);
        field(mapType, "f").setInt(map, 0);
        field(mapType, "m").set(map, new int[0]);
        field(mapType, "n").set(map, new int[0]);
        Class<?> mapOwner = loader.loadClass(encoded("liilllii").replace('/', '.'));
        Field mapField = null;
        for (Class<?> current = mapOwner; current != null; current = current.getSuperclass()) {
            for (Field candidate : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(candidate.getModifiers())
                        && mapType.equals(candidate.getType())) {
                    if (mapField != null) throw new AssertionError("Ambiguous native cs map singleton field");
                    candidate.setAccessible(true);
                    mapField = candidate;
                }
            }
        }
        if (mapField == null) throw new NoSuchFieldException(mapOwner.getName() + " static cs map");
        mapField.set(null, map);
    }

    private static void setOptionalBoolean(Object target, Class<?> owner, String name, boolean value) throws Exception {
        try { field(owner, name).setBoolean(target, value); }
        catch (NoSuchFieldException absentOnInput) { /* The regression input predates the lifecycle marker. */ }
    }

    private static String describe(Outcome result) {
        StringBuilder out = new StringBuilder("objects=[");
        for (ObjectSnapshot object : result.objects) {
            if (object.id < 80 || object.id > 82) continue;
            if (out.charAt(out.length() - 1) != '[') out.append(", ");
            out.append(object.id).append('/').append(object.type).append(" dead=").append(object.dead)
                    .append(" name=").append(object.name).append(" class=").append(object.className)
                    .append(" pos=").append(object.x).append(',').append(object.y);
        }
        return out.append("] requests=").append(result.requests).toString();
    }

    private static String describe(List<ObjectSnapshot> objects) {
        StringBuilder out = new StringBuilder("[");
        for (ObjectSnapshot object : objects) {
            if (object.id < 80 || object.id > 82) continue;
            if (out.charAt(out.length() - 1) != '[') out.append(", ");
            out.append(object.id).append('/').append(object.type).append(" dead=").append(object.dead)
                    .append(" name=").append(object.name).append(" class=").append(object.className);
        }
        return out.append(']').toString();
    }

    private static ObjectSnapshot find(List<ObjectSnapshot> objects, int id, int type, boolean dead) {
        if (objects == null) return null;
        for (ObjectSnapshot object : objects)
            if (object.id == id && object.type == type && object.dead == dead) return object;
        return null;
    }

    private static void enablePacket4World(ClassLoader loader) throws Exception {
        byte[] bytes = readJarClass(new File(((URLClassLoader)loader).getURLs()[0].toURI()), HANDLER);
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        MethodNode method = null;
        for (MethodNode candidate : type.methods) {
            if (candidate.name.equals(PACKET4) && candidate.desc.equals("(L" + MESSAGE + ";)V")) {
                method = candidate; break;
            }
        }
        if (method == null) throw new AssertionError("Missing native packet-4 handler " + HANDLER + "." + PACKET4);
        FieldInsnNode gate = null;
        for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC
                    && "Z".equals(((FieldInsnNode)instruction).desc)) {
                gate = (FieldInsnNode)instruction;
                break;
            }
        }
        if (gate == null) throw new AssertionError("Native packet-4 handler has no readiness gate");
        Class<?> owner = loader.loadClass(gate.owner.replace('/', '.'));
        field(owner, gate.name).setBoolean(null, true);
    }

    private static Object placeholder(ClassLoader loader, Class<?> entityType, int id, int type, int x, int y)
            throws Exception {
        Class<?> placeholderType = loader.loadClass(PLACEHOLDER.replace('/', '.'));
        Constructor<?> constructor = placeholderType.getDeclaredConstructor(Integer.TYPE, Byte.TYPE,
                String.class, Integer.TYPE, Integer.TYPE);
        constructor.setAccessible(true);
        Object actor = constructor.newInstance(Integer.valueOf(id), Byte.valueOf((byte)type), "", Integer.valueOf(x), Integer.valueOf(y));
        field(entityType, "cu").setInt(actor, id);
        field(entityType, "cv").setByte(actor, (byte)type);
        return actor;
    }

    private static Object newNativeList(Class<?> type) throws Exception {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance("Map object batch regression");
        } catch (NoSuchMethodException absent) {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }
    }

    private static void add(Object list, Object actor) throws Exception {
        Method method = list.getClass().getMethod(encoded("l"), Object.class);
        method.setAccessible(true);
        method.invoke(list, actor);
    }

    private static int listSize(Object list) throws Exception {
        Method method = list.getClass().getMethod("c");
        method.setAccessible(true);
        return ((Integer)method.invoke(list)).intValue();
    }

    private static Object listGet(Object list, int index) throws Exception {
        Method method = list.getClass().getMethod("a", Integer.TYPE);
        method.setAccessible(true);
        return method.invoke(list, Integer.valueOf(index));
    }

    private static ObjectSnapshot snapshot(Object actor, Class<?> entityType) throws Exception {
        return new ObjectSnapshot(field(entityType, "cu").getInt(actor), field(entityType, "cv").getByte(actor),
                field(entityType, "cL").getBoolean(actor), field(entityType, "ea").getBoolean(actor),
                field(entityType, "eb").getInt(actor), (String)field(entityType, "cC").get(actor),
                field(entityType, "aZ").getInt(actor), field(entityType, "ba").getInt(actor),
                field(entityType, "bg").getInt(actor), field(entityType, "bh").getInt(actor),
                actor.getClass().getName());
    }

    private static List<ObjectSnapshot> snapshots(Object entityList, Class<?> entityType) throws Exception {
        List<ObjectSnapshot> snapshots = new ArrayList<ObjectSnapshot>();
        int size = listSize(entityList);
        for (int i = 0; i < size; i++) {
            Object actor = listGet(entityList, i);
            if (entityType.isInstance(actor)) snapshots.add(snapshot(actor, entityType));
        }
        return snapshots;
    }

    private static byte[] encode(List<Record> records) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        for (Record record : records) {
            out.writeByte(record.type); out.writeShort(record.template); out.writeShort(record.id);
            out.writeShort(record.x); out.writeShort(record.y); out.writeByte(record.state);
        }
        out.flush();
        return bytes.toByteArray();
    }

    private static List<Record> records(Record... values) { return Arrays.asList(values); }

    private static Field field(Class<?> owner, String name) throws Exception {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static byte[] readClass(File jar, String owner) throws Exception { return readJarClass(jar, owner); }

    private static ClassNode readNode(File jar, String owner) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(readJarClass(jar, owner)).accept(node, 0);
        return node;
    }

    private static FieldNode findField(ClassNode type, String name) {
        for (FieldNode field : type.fields) if (name.equals(field.name)) return field;
        return null;
    }

    private static MethodNode findMethod(ClassNode type, String name, String descriptor) {
        for (MethodNode method : type.methods)
            if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        throw new AssertionError("Missing " + type.name + "." + name + descriptor);
    }

    private static byte[] readJarClass(File jarFile, String owner) throws Exception {
        JarFile jar = new JarFile(jarFile);
        try {
            JarEntry entry = jar.getJarEntry(owner + ".class");
            if (entry == null) throw new IOException("Missing " + owner + ".class in " + jarFile);
            java.io.InputStream input = jar.getInputStream(entry);
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) >= 0; ) if (count != 0) bytes.write(buffer, 0, count);
                return bytes.toByteArray();
            } finally { input.close(); }
        } finally { jar.close(); }
    }

    private static void throwCause(Throwable cause) throws Exception {
        if (cause instanceof Exception) throw (Exception)cause;
        if (cause instanceof Error) throw (Error)cause;
        throw new RuntimeException(cause);
    }

    private static final class Record {
        final int type, id, x, y, state, template;
        Record(int type, int id, int x, int y, int state) {
            this.type = type; this.id = id; this.x = x; this.y = y; this.state = state; this.template = 1;
        }
    }

    private static final class ObjectSnapshot {
        final int id, type, eb, x, y, targetX, targetY;
        final boolean dead, ea;
        final String name, className;
        ObjectSnapshot(int id, int type, boolean dead, boolean ea, int eb,
                String name, int x, int y, int targetX, int targetY, String className) {
            this.id = id; this.type = type; this.dead = dead; this.ea = ea; this.eb = eb;
            this.name = name; this.x = x; this.y = y; this.targetX = targetX; this.targetY = targetY;
            this.className = className.replace('.', '/');
        }
    }

    private static final class Outcome {
        final List<ObjectSnapshot> objects;
        final List<String> requests;
        final List<ObjectSnapshot> preFullNpc;
        Outcome(List<ObjectSnapshot> objects, List<String> requests, List<ObjectSnapshot> preFullNpc) {
            this.objects = objects; this.requests = requests; this.preFullNpc = preFullNpc;
        }
        int count(int id, int type, boolean dead) {
            int count = 0;
            for (ObjectSnapshot object : objects)
                if (object.id == id && object.type == type && object.dead == dead) count++;
            return count;
        }
        ObjectSnapshot find(int id, int type, boolean dead) {
            for (ObjectSnapshot object : objects)
                if (object.id == id && object.type == type && object.dead == dead) return object;
            return null;
        }
        boolean hasRequest(int opcode, int id) {
            String expected = packet(opcode, id);
            for (String request : requests) if (expected.equals(request)) return true;
            return false;
        }
        private static String packet(int opcode, int id) {
            return opcode + ":[" + (byte)(id >> 8) + ", " + (byte)id + "]";
        }
    }
}
