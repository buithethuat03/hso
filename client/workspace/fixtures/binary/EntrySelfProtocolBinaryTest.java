import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Checks opcode-3 self initialization reads against a complete synthetic Hso payload. */
public final class EntrySelfProtocolBinaryTest implements Opcodes {
    private static int checks;
    private static int probeSequence;
    private static DataInputStream input;
    public static short probeCapacity;
    public static byte probePages;
    private static int probePetId, probePetNumber, probePetLevel;
    private static String probePetName, probeTailText;
    private static long probeTimestamp;
    private static boolean probeTimestampCaptured;
    private static int probeRemaining, probeTrailer;
    private static final String SELF = "EntrySelfProtocolBinaryTest";
    private static final int[] CAPACITIES = { 0, 1, 41, 42, 43, 127, 128, 300 };
    private static final String HEADER_TEXT = "entry-header";
    private static final String TAIL_TEXT = "entry-tail";
    private static final long TIMESTAMP = 0x1020304050607080L;
    private static final int TRAILER = 0x5a;

    private EntrySelfProtocolBinaryTest() { }

    public static DataInputStream stream() { return input; }
    public static void capturePetId(short value) { probePetId = value; }
    public static void capturePetNumber(int value) { probePetNumber = value; }
    public static void capturePetName(String value) { probePetName = value; }
    public static void capturePetLevel(byte value) { probePetLevel = value; }
    public static void captureTailText(String value) { probeTailText = value; }
    public static void captureTimestamp(long value) { probeTimestamp = value; probeTimestampCaptured = true; }
    public static void captureCursor() {
        try {
            probeRemaining = input.available();
            probeTrailer = -1;
            if (probeRemaining > 0) {
                input.mark(1);
                probeTrailer = input.readUnsignedByte();
                input.reset();
            }
        } catch (Exception e) { throw new IllegalStateException("Cannot capture probe cursor", e); }
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 4, "donor.jar before-current.jar after.jar catalog.properties");
        File donorFile = new File(args[0]);
        File beforeFile = new File(args[1]);
        File afterFile = new File(args[2]);
        Properties catalog = load(new File(args[3]));

        String donorHash = ChibiEntryCompatibilityPatcher.hash(donorFile);
        String beforeHash = ChibiEntryCompatibilityPatcher.hash(beforeFile);
        String afterHash = ChibiEntryCompatibilityPatcher.hash(afterFile);
        require(ChibiEntryCompatibilityPatcher.DONOR.equals(donorHash), "Hso donor hash mismatch");
        require(beforeHash.equals(catalog.getProperty("self.input.sha256")), "Before jar/catalog mismatch");
        require(afterHash.equals(catalog.getProperty("output.sha256")), "Candidate jar/catalog mismatch");

        Map<String,byte[]> donorJar = ChibiEntryCompatibilityPatcher.read(donorFile);
        Map<String,byte[]> beforeJar = ChibiEntryCompatibilityPatcher.read(beforeFile);
        Map<String,byte[]> afterJar = ChibiEntryCompatibilityPatcher.read(afterFile);
        String owner = required(catalog, "self.owner");
        String methodName = required(catalog, "self.method");
        String methodDesc = required(catalog, "self.desc");
        ClassNode beforeClass = ChibiEntryCompatibilityPatcher.node(beforeJar.get(owner + ".class"));
        ClassNode afterClass = ChibiEntryCompatibilityPatcher.node(afterJar.get(owner + ".class"));
        MethodNode beforeMethod = ChibiEntryCompatibilityPatcher.method(beforeClass, methodName, methodDesc);
        MethodNode afterMethod = ChibiEntryCompatibilityPatcher.method(afterClass, methodName, methodDesc);
        ChibiEntryCompatibilityPatcher.verifyUntouchedMethods(beforeClass, afterClass, afterMethod);
        MethodNode donorMethod = ChibiEntryCompatibilityPatcher.method(
                ChibiEntryCompatibilityPatcher.node(donorJar.get("Illllll.class")), "Il", "(LlII;)V");

        EntrySelfInitPatcher.Site beforeSite = findSite(beforeMethod, "readByte", false);
        EntrySelfInitPatcher.Site afterSite = findSite(afterMethod, "readShort", true);
        EntrySelfInitPatcher.Site donorSite = findSite(donorMethod, "readShort", true);
        verifyCatalogField(catalog, beforeJar, "self.capacity", beforeSite.capacity);
        verifyCatalogField(catalog, afterJar, "self.capacity", afterSite.capacity);
        verifyCatalogField(catalog, beforeJar, "self.pages", beforeSite.pages);
        verifyCatalogField(catalog, afterJar, "self.pages", afterSite.pages);
        verifyNativeField(donorJar, donorSite.capacity, "S");
        verifyNativeField(donorJar, donorSite.pages, "B");
        require("S".equals(afterSite.capacity.desc) && "B".equals(afterSite.pages.desc),
                "Capacity/page field ABI changed; the capacity field must remain short");
        require("readByte".equals(beforeSite.reader.name), "Original capacity reader is not byte-wide");
        require("readShort".equals(afterSite.reader.name) && "readShort".equals(donorSite.reader.name),
                "Candidate/donor capacity reader is not short-wide");
        verifyReadSchema(beforeMethod, afterMethod, donorMethod, beforeSite, afterSite, donorSite,
                Integer.parseInt(required(catalog, "self.prefix.readers")));
        verifyCeilingAndClamp(beforeMethod, afterMethod, donorMethod, beforeSite, afterSite, donorSite);
        FieldInsnNode readyMarker = verifyEntryControlFlow(afterMethod, afterSite);
        require("max(1,(capacity+41)/42)".equals(required(catalog, "self.page.expression")),
                "Candidate page-expression catalog mismatch");

        int[] petIds = { -1, 17 };
        for (int capacity : CAPACITIES) {
            for (int petId : petIds) {
                SyntheticPacket packet = payload(capacity, petId);
                Parsed old = executeSlice(beforeMethod, beforeSite, packet);
                Parsed fixed = executeSlice(afterMethod, afterSite, packet);
                Parsed reference = executeSlice(donorMethod, donorSite, packet);
                require(!matches(old, capacity, petId), "Original byte reader unexpectedly aligns capacity="
                        + capacity + " petId=" + petId);
                require(matches(fixed, capacity, petId), "Candidate native slice failed payload capacity="
                        + capacity + " petId=" + petId + ": " + describe(fixed));
                require(matches(reference, capacity, petId), "Hso native slice failed capacity="
                        + capacity + " petId=" + petId + ": " + describe(reference));
                int expectedPages = Math.max(1, (capacity + 41) / 42);
                require(fixed.pages == expectedPages && reference.pages == expectedPages,
                        "Native ceiling/min-page result mismatch capacity=" + capacity);
                if (capacity == 0 || capacity == 43 || capacity == 128 || capacity == 300)
                    require(old.pages != expectedPages,
                            "Original native page expression unexpectedly matches capacity=" + capacity);
                if (capacity == 300)
                    require(old.error != null && !old.timestampCaptured,
                            "Original native slice reached timestamp/readiness despite misaligned pet tail");
                checks += 2;
            }
        }
        System.out.println("ENTRY_SELF_PROTOCOL=PASS; opcode=3; prefixReaders="
                + required(catalog, "self.prefix.readers") + "; payloads=" + (CAPACITIES.length * petIds.length)
                + "; petRecords=absent,present; executedNativeSlice=capacity-through-timestamp"
                + "; pageEdges=0,1,41,42,43,127,128,300; original300=pre-timestamp-failure"
                + "; capacityField=S; pageField=B; candidate/donor alignment"
                + "; readyWrite=" + readyMarker.owner + "." + readyMarker.name + ":Z; checks=" + checks);
    }

    private static void verifyReadSchema(MethodNode before, MethodNode after, MethodNode donor,
            EntrySelfInitPatcher.Site beforeSite, EntrySelfInitPatcher.Site afterSite,
            EntrySelfInitPatcher.Site donorSite, int expectedReaders) {
        List<MethodInsnNode> oldReads = ChibiEntryCompatibilityPatcher.reads(before);
        List<MethodInsnNode> newReads = ChibiEntryCompatibilityPatcher.reads(after);
        List<MethodInsnNode> donorReads = ChibiEntryCompatibilityPatcher.reads(donor);
        int oldBoundary = oldReads.indexOf(beforeSite.reader);
        int newBoundary = newReads.indexOf(afterSite.reader);
        int donorBoundary = donorReads.indexOf(donorSite.reader);
        require(oldBoundary == newBoundary && newBoundary == donorBoundary,
                "Capacity read moved within the opcode-3 prefix");
        require(oldBoundary + 1 == expectedReaders, "Unexpected mandatory prefix reader count " + (oldBoundary + 1));
        for (int i = 0; i < oldBoundary; i++) {
            MethodInsnNode a = oldReads.get(i), b = newReads.get(i), c = donorReads.get(i);
            require(a.name.equals(b.name) && b.name.equals(c.name)
                            && a.desc.equals(b.desc) && b.desc.equals(c.desc),
                    "Mandatory prefix read mismatch at site " + i + ": " + a.name + "/" + b.name + "/" + c.name);
        }
        // Capacity is the only width difference; all mandatory tail readers retain Hso's ABI.
        require(beforeSite.reader.desc.equals("()B") && afterSite.reader.desc.equals("()S")
                        && donorSite.reader.desc.equals("()S"), "Capacity reader descriptors differ");
        int tailEnd = Math.min(Math.min(oldReads.size(), newReads.size()), donorReads.size());
        for (int i = oldBoundary + 1; i < tailEnd; i++) {
            MethodInsnNode a = oldReads.get(i), b = newReads.get(i), c = donorReads.get(i);
            require(a.name.equals(b.name) && b.name.equals(c.name)
                            && a.desc.equals(b.desc) && b.desc.equals(c.desc),
                    "Post-capacity read schema mismatch at site " + i);
        }
        require("readShort".equals(newReads.get(newBoundary + 1).name), "Pet ID is not short-wide after capacity");
        require("readInt".equals(newReads.get(newBoundary + 2).name)
                        && "readUTF".equals(newReads.get(newBoundary + 3).name)
                        && "readByte".equals(newReads.get(newBoundary + 4).name)
                        && "readUTF".equals(newReads.get(newBoundary + 5).name)
                        && "readLong".equals(newReads.get(newBoundary + 6).name),
                "Optional pet/tail name/timestamp read schema changed");
        checks += oldBoundary + 8;
    }

    private static EntrySelfInitPatcher.Site findSite(MethodNode method, String readerName, boolean ceiling) {
        EntrySelfInitPatcher.Site found = null;
        for (MethodInsnNode reader : ChibiEntryCompatibilityPatcher.reads(method)) {
            if (!reader.name.equals(readerName)) continue;
            AbstractInsnNode n = nextReal(reader);
            if (n != null && n.getOpcode() == I2S) n = nextReal(n);
            boolean retainedCapacity = n != null && n.getOpcode() == DUP;
            if (retainedCapacity) n = nextReal(n);
            if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTSTATIC
                    || !"S".equals(((FieldInsnNode)n).desc)) continue;
            FieldInsnNode capacity = (FieldInsnNode)n;
            n = nextReal(n);
            if (!retainedCapacity) {
                if (!(n instanceof FieldInsnNode) || n.getOpcode() != GETSTATIC
                        || !sameField(capacity, (FieldInsnNode)n)) continue;
                n = nextReal(n);
            }
            if (ceiling) {
                if (integer(n) != 41 || nextReal(n) == null || nextReal(n).getOpcode() != IADD) continue;
                n = nextReal(nextReal(n));
            } else if (integer(n) == 41) continue;
            if (integer(n) != 42) continue;
            AbstractInsnNode divisor = n;
            n = nextReal(n);
            if (n == null || n.getOpcode() != IDIV) continue;
            n = nextReal(n);
            if (n == null || n.getOpcode() != I2B) continue;
            n = nextReal(n);
            if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTSTATIC
                    || !"B".equals(((FieldInsnNode)n).desc)) continue;
            require(found == null, "Ambiguous capacity/page site in " + method.name + method.desc);
            found = new EntrySelfInitPatcher.Site();
            found.reader = reader; found.capacity = capacity; found.pages = (FieldInsnNode)n; found.divisor = divisor;
        }
        require(found != null, "Capacity/page site missing in " + method.name + method.desc);
        return found;
    }

    private static void verifyCeilingAndClamp(MethodNode before, MethodNode after, MethodNode donor,
            EntrySelfInitPatcher.Site beforeSite, EntrySelfInitPatcher.Site afterSite,
            EntrySelfInitPatcher.Site donorSite) {
        require(!hasCeiling(beforeSite), "Original unexpectedly uses ceiling-page expression");
        require(hasCeiling(afterSite) && hasCeiling(donorSite), "Candidate/donor lacks ceiling-page expression");
        require(!hasMinClamp(before, beforeSite.pages), "Original unexpectedly clamps zero pages");
        require(hasMinClamp(after, afterSite.pages), "Candidate lacks minimum-one-page clamp");
        checks += 4;
    }

    private static FieldInsnNode verifyEntryControlFlow(MethodNode method, EntrySelfInitPatcher.Site site) {
        List<MethodInsnNode> reads = ChibiEntryCompatibilityPatcher.reads(method);
        int capacity = reads.indexOf(site.reader);
        MethodInsnNode petId = reads.get(capacity + 1);
        MethodInsnNode petNumber = reads.get(capacity + 2);
        MethodInsnNode petName = reads.get(capacity + 3);
        MethodInsnNode petLevel = reads.get(capacity + 4);
        MethodInsnNode tailName = reads.get(capacity + 5);
        MethodInsnNode timestamp = reads.get(capacity + 6);
        require("readShort".equals(petId.name) && "readInt".equals(petNumber.name)
                        && "readUTF".equals(petName.name) && "readByte".equals(petLevel.name)
                        && "readUTF".equals(tailName.name) && "readLong".equals(timestamp.name),
                "Entry tail read order changed");
        AbstractInsnNode[] code = method.instructions.toArray();
        int petIdAt = indexOf(code, petId), petNumberAt = indexOf(code, petNumber);
        int petNameAt = indexOf(code, petName), petLevelAt = indexOf(code, petLevel);
        int tailNameAt = indexOf(code, tailName), timestampAt = indexOf(code, timestamp);
        JumpInsnNode petBranch = null;
        for (int i = petIdAt + 1; i < petNumberAt; i++)
            if (code[i] instanceof JumpInsnNode && code[i].getOpcode() == IFLT) petBranch = (JumpInsnNode)code[i];
        require(petBranch != null, "Native optional-pet branch (`petId < 0`) absent");
        int branchTarget = indexOf(code, petBranch.label);
        require(branchTarget > petLevelAt && branchTarget <= tailNameAt,
                "Optional-pet branch does not skip exactly to the entry tail");
        FieldInsnNode readyWrite = null;
        for (int i = timestampAt + 1; i < code.length; i++) {
            AbstractInsnNode n = code[i];
            if (n instanceof FieldInsnNode && n.getOpcode() == PUTSTATIC && "Z".equals(((FieldInsnNode)n).desc)) {
                AbstractInsnNode value = previousReal(n);
                if (value != null && value.getOpcode() == ICONST_1) { readyWrite = (FieldInsnNode)n; break; }
            }
        }
        require(readyWrite != null, "No post-timestamp static ready=true write found");
        require(indexOf(code, readyWrite) > timestampAt,
                "Readiness is set before the timestamp tail is consumed");
        checks += 4;
        return readyWrite;
    }

    private static int indexOf(AbstractInsnNode[] code, AbstractInsnNode target) {
        for (int i = 0; i < code.length; i++) if (code[i] == target) return i;
        return -1;
    }

    private static boolean hasCeiling(EntrySelfInitPatcher.Site site) {
        AbstractInsnNode plus42 = previousReal(site.divisor);
        AbstractInsnNode plus41 = previousReal(plus42);
        return integer(site.divisor) == 42 && plus42 != null && plus42.getOpcode() == IADD
                && integer(plus41) == 41;
    }

    private static boolean hasMinClamp(MethodNode method, FieldInsnNode pagesWrite) {
        AbstractInsnNode a = nextReal(pagesWrite), b = nextReal(a), c = nextReal(b);
        AbstractInsnNode d = nextReal(c), e = nextReal(d);
        return a instanceof FieldInsnNode && a.getOpcode() == GETSTATIC && sameField((FieldInsnNode)a, pagesWrite)
                && b != null && b.getOpcode() == ICONST_1
                && c instanceof JumpInsnNode && c.getOpcode() == IF_ICMPGE
                && d != null && d.getOpcode() == ICONST_1
                && e instanceof FieldInsnNode && e.getOpcode() == PUTSTATIC && sameField((FieldInsnNode)e, pagesWrite);
    }

    private static void verifyCatalogField(Properties p, Map<String,byte[]> jar, String prefix, FieldInsnNode instruction) {
        require(instruction.owner.equals(required(p, prefix + ".owner"))
                        && instruction.name.equals(required(p, prefix + ".name"))
                        && instruction.desc.equals(required(p, prefix + ".desc")),
                "Catalog target mismatch for " + prefix);
        ClassNode owner = ChibiEntryCompatibilityPatcher.node(jar.get(instruction.owner + ".class"));
        FieldNode field = null;
        for (FieldNode candidate : owner.fields)
            if (candidate.name.equals(instruction.name) && candidate.desc.equals(instruction.desc)) field = candidate;
        require(field != null && (field.access & ACC_STATIC) != 0, "Native static target field absent: " + prefix);
        checks++;
    }

    private static void verifyNativeField(Map<String,byte[]> jar, FieldInsnNode instruction, String descriptor) {
        require(descriptor.equals(instruction.desc), "Hso native target descriptor mismatch");
        ClassNode owner = ChibiEntryCompatibilityPatcher.node(jar.get(instruction.owner + ".class"));
        FieldNode field = null;
        for (FieldNode candidate : owner.fields)
            if (candidate.name.equals(instruction.name) && candidate.desc.equals(instruction.desc)) field = candidate;
        require(field != null && (field.access & ACC_STATIC) != 0, "Hso native static target field absent");
    }

    private static MethodNode method(Map<String,byte[]> jar, String owner, String name, String desc) {
        return ChibiEntryCompatibilityPatcher.method(
                ChibiEntryCompatibilityPatcher.node(jar.get(owner + ".class")), name, desc);
    }

    private static SyntheticPacket payload(int capacity, int petId) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(321); // Character identifier.
        out.writeUTF(HEADER_TEXT); // Header text; semantics are intentionally unnamed.
        for (int i = 0; i < 4; i++) out.writeInt(100 + i);
        for (int i = 0; i < 4; i++) out.writeByte(i + 1);
        out.writeByte(0); // Zero inventory/item entries in this packet fixture.
        for (int i = 0; i < 4; i++) out.writeShort(i + 10);
        for (int i = 0; i < 8; i++) out.writeShort(i + 20); // Two fixed arrays, four shorts each.
        for (int i = 0; i < 42; i++) out.writeByte(i); // Two arrays of 21 byte values.
        out.writeByte(2);
        out.writeShort(3);
        int capacityOffset = bytes.size();
        out.writeShort(capacity);
        out.writeShort(petId);
        if (petId >= 0) {
            out.writeInt(7001);
            out.writeUTF("fixture-pet");
            out.writeByte(6);
        }
        out.writeUTF(TAIL_TEXT);
        out.writeLong(TIMESTAMP);
        out.writeByte(TRAILER); // First later opcode-3 field after the mandatory self tail.
        out.flush();
        return new SyntheticPacket(bytes.toByteArray(), capacityOffset);
    }

    private static Parsed executeSlice(MethodNode source, EntrySelfInitPatcher.Site site,
            SyntheticPacket packet) throws Exception {
        input = new DataInputStream(new ByteArrayInputStream(packet.bytes));
        require(input.skipBytes(packet.capacityOffset) == packet.capacityOffset, "Cannot position at capacity field");
        probeCapacity = 0; probePages = 0;
        probePetId = 0; probePetNumber = 0; probePetLevel = 0;
        probePetName = null; probeTailText = null; probeTimestamp = 0L;
        probeTimestampCaptured = false; probeRemaining = -1; probeTrailer = -1;
        String probeName = SELF + "Slice" + (++probeSequence);
        byte[] bytecode = makeSliceProbe(source, site, probeName);
        ProbeLoader loader = new ProbeLoader(EntrySelfProtocolBinaryTest.class.getClassLoader());
        Throwable failure = null;
        try {
            Class<?> probe = loader.define(probeName, bytecode);
            probe.getMethod("probe").invoke(null);
        } catch (InvocationTargetException e) {
            failure = e.getCause();
        } catch (Throwable e) {
            failure = e;
        }
        int remaining = -1;
        try { remaining = input.available(); } catch (Exception ignored) { }
        String message = failure == null ? null : failure.getClass().getSimpleName() + ": " + failure.getMessage();
        return new Parsed(probeCapacity, probePages, probePetId, probePetNumber, probePetName,
                probePetLevel, probeTailText, probeTimestamp, probeTimestampCaptured,
                probeRemaining >= 0 ? probeRemaining : remaining, probeTrailer, message);
    }

    private static byte[] makeSliceProbe(MethodNode source, EntrySelfInitPatcher.Site site, String name) {
        AbstractInsnNode[] sourceCode = source.instructions.toArray();
        List<MethodInsnNode> reads = ChibiEntryCompatibilityPatcher.reads(source);
        int capacityReadAt = indexOf(sourceCode, site.reader);
        MethodInsnNode petId = reads.get(reads.indexOf(site.reader) + 1);
        MethodInsnNode petNumber = reads.get(reads.indexOf(site.reader) + 2);
        MethodInsnNode petName = reads.get(reads.indexOf(site.reader) + 3);
        MethodInsnNode petLevel = reads.get(reads.indexOf(site.reader) + 4);
        MethodInsnNode tailName = reads.get(reads.indexOf(site.reader) + 5);
        MethodInsnNode timestamp = reads.get(reads.indexOf(site.reader) + 6);
        int timestampAt = indexOf(sourceCode, timestamp);
        require(capacityReadAt >= 0 && timestampAt > capacityReadAt, "Invalid native capacity-to-timestamp slice");

        ClassNode generated = new ClassNode();
        generated.version = V1_5;
        generated.access = ACC_PUBLIC;
        generated.name = name.replace('.', '/');
        generated.superName = "java/lang/Object";
        MethodNode probe = new MethodNode(ACC_PUBLIC | ACC_STATIC, "probe", "()V", null, null);
        Map<LabelNode,LabelNode> labels = new java.util.IdentityHashMap<LabelNode,LabelNode>();
        LabelNode stop = new LabelNode();
        for (int i = 0; i < sourceCode.length; i++) {
            if (!(sourceCode[i] instanceof LabelNode)) continue;
            labels.put((LabelNode)sourceCode[i], i >= capacityReadAt && i <= timestampAt
                    ? new LabelNode() : stop);
        }
        initializeUnassignedLocals(probe.instructions, sourceCode, capacityReadAt, timestampAt);
        probe.instructions.add(new MethodInsnNode(INVOKESTATIC, SELF, "stream",
                "()Ljava/io/DataInputStream;", false));
        for (int i = capacityReadAt; i <= timestampAt; i++) {
            AbstractInsnNode n = sourceCode[i];
            if (n instanceof FrameNode || n instanceof LineNumberNode) continue;
            if (n instanceof LabelNode) { probe.instructions.add(labels.get(n)); continue; }
            if (n instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)n;
                if (n == timestamp) {
                    probe.instructions.add(call.clone(labels));
                    probe.instructions.add(new InsnNode(DUP2));
                    probe.instructions.add(new MethodInsnNode(INVOKESTATIC, SELF, "captureTimestamp", "(J)V", false));
                    probe.instructions.add(new InsnNode(POP2));
                    probe.instructions.add(new MethodInsnNode(INVOKESTATIC, SELF, "captureCursor", "()V", false));
                    probe.instructions.add(new InsnNode(RETURN));
                    break;
                }
                appendCall(probe.instructions, call, n == petId, n == petNumber,
                        n == petName, n == petLevel, n == tailName);
                continue;
            }
            if (n instanceof FieldInsnNode) {
                appendField(probe.instructions, (FieldInsnNode)n, site);
                continue;
            }
            if (n instanceof TypeInsnNode) {
                TypeInsnNode type = (TypeInsnNode)n;
                if (type.getOpcode() == NEW) probe.instructions.add(new TypeInsnNode(NEW, "java/lang/Object"));
                else if (type.getOpcode() == CHECKCAST) { /* Native object fields are stubbed below. */ }
                else if (type.getOpcode() == INSTANCEOF) {
                    probe.instructions.add(new InsnNode(POP)); probe.instructions.add(new InsnNode(ICONST_0));
                } else probe.instructions.add(new TypeInsnNode(type.getOpcode(), "java/lang/Object"));
                continue;
            }
            probe.instructions.add(n.clone(labels));
        }
        probe.instructions.add(stop);
        probe.instructions.add(new InsnNode(RETURN));
        probe.maxLocals = source.maxLocals;
        generated.methods.add(probe);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        generated.accept(writer);
        return writer.toByteArray();
    }

    private static void appendCall(InsnList out, MethodInsnNode call, boolean petId, boolean petNumber,
            boolean petName, boolean petLevel, boolean tailName) {
        Type returned = Type.getReturnType(call.desc);
        if (call.owner.equals("java/io/DataInputStream") && call.name.startsWith("read")) {
            out.add(call.clone(new HashMap<LabelNode,LabelNode>()));
            if (petId || petNumber || petName || petLevel || tailName) {
                out.add(new InsnNode(returned.getSize() == 2 ? DUP2 : DUP));
                String method, descriptor;
                if (petId) { method = "capturePetId"; descriptor = "(S)V"; }
                else if (petNumber) { method = "capturePetNumber"; descriptor = "(I)V"; }
                else if (petName) { method = "capturePetName"; descriptor = "(Ljava/lang/String;)V"; }
                else if (petLevel) { method = "capturePetLevel"; descriptor = "(B)V"; }
                else { method = "captureTailText"; descriptor = "(Ljava/lang/String;)V"; }
                out.add(new MethodInsnNode(INVOKESTATIC, SELF, method, descriptor, false));
            }
            return;
        }
        if (Type.getReturnType(call.desc).getDescriptor().equals("Ljava/io/DataInputStream;")) {
            popInvocationOperands(out, call);
            out.add(new MethodInsnNode(INVOKESTATIC, SELF, "stream", "()Ljava/io/DataInputStream;", false));
            return;
        }
        if (call.name.equals("<init>")) {
            Type[] arguments = Type.getArgumentTypes(call.desc);
            for (int i = arguments.length - 1; i >= 0; i--) pop(out, arguments[i]);
            out.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
            return;
        }
        popInvocationOperands(out, call);
        pushDefault(out, returned);
    }

    private static void appendField(InsnList out, FieldInsnNode field, EntrySelfInitPatcher.Site site) {
        if (sameField(field, site.capacity)) {
            out.add(new FieldInsnNode(field.getOpcode(), SELF, "probeCapacity", "S"));
            return;
        }
        if (sameField(field, site.pages)) {
            out.add(new FieldInsnNode(field.getOpcode(), SELF, "probePages", "B"));
            return;
        }
        Type type = Type.getType(field.desc);
        if (field.getOpcode() == GETSTATIC) {
            if (field.desc.equals("Ljava/io/DataInputStream;"))
                out.add(new MethodInsnNode(INVOKESTATIC, SELF, "stream", "()Ljava/io/DataInputStream;", false));
            else pushDefault(out, type);
        } else if (field.getOpcode() == PUTSTATIC) pop(out, type);
        else if (field.getOpcode() == GETFIELD) {
            out.add(new InsnNode(POP));
            if (field.desc.equals("Ljava/io/DataInputStream;"))
                out.add(new MethodInsnNode(INVOKESTATIC, SELF, "stream", "()Ljava/io/DataInputStream;", false));
            else pushDefault(out, type);
        } else if (field.getOpcode() == PUTFIELD) {
            pop(out, type); out.add(new InsnNode(POP));
        } else throw new IllegalStateException("Unexpected field operation " + field.getOpcode());
    }

    private static void initializeUnassignedLocals(InsnList out, AbstractInsnNode[] code, int from, int to) {
        Map<Integer,Integer> unassigned = new java.util.TreeMap<Integer,Integer>();
        Set<Integer> stored = new HashSet<Integer>();
        for (int i = from; i <= to; i++) {
            if (!(code[i] instanceof VarInsnNode)) continue;
            VarInsnNode v = (VarInsnNode)code[i];
            if (isLoad(v.getOpcode()) && !stored.contains(v.var) && !unassigned.containsKey(v.var))
                unassigned.put(v.var, v.getOpcode());
            if (isStore(v.getOpcode())) stored.add(v.var);
        }
        for (Map.Entry<Integer,Integer> entry : unassigned.entrySet()) {
            int opcode = entry.getValue(), local = entry.getKey();
            Type type = localType(opcode);
            pushDefault(out, type);
            out.add(new VarInsnNode(storeOpcode(opcode), local));
        }
    }

    private static boolean isLoad(int opcode) { return opcode >= ILOAD && opcode <= ALOAD; }
    private static boolean isStore(int opcode) { return opcode >= ISTORE && opcode <= ASTORE; }
    private static int storeOpcode(int loadOpcode) { return loadOpcode + (ISTORE - ILOAD); }
    private static Type localType(int opcode) {
        switch (opcode) {
            case ILOAD: return Type.INT_TYPE;
            case LLOAD: return Type.LONG_TYPE;
            case FLOAD: return Type.FLOAT_TYPE;
            case DLOAD: return Type.DOUBLE_TYPE;
            case ALOAD: return Type.getType(Object.class);
            default: throw new IllegalArgumentException("Unexpected load opcode " + opcode);
        }
    }

    private static void popInvocationOperands(InsnList out, MethodInsnNode call) {
        Type[] arguments = Type.getArgumentTypes(call.desc);
        for (int i = arguments.length - 1; i >= 0; i--) pop(out, arguments[i]);
        if (call.getOpcode() != INVOKESTATIC) out.add(new InsnNode(POP));
    }

    private static void pop(InsnList out, Type type) { out.add(new InsnNode(type.getSize() == 2 ? POP2 : POP)); }

    private static void pushDefault(InsnList out, Type type) {
        switch (type.getSort()) {
            case Type.VOID: return;
            case Type.BOOLEAN: case Type.BYTE: case Type.CHAR: case Type.SHORT: case Type.INT:
                out.add(new InsnNode(ICONST_0)); return;
            case Type.FLOAT: out.add(new InsnNode(FCONST_0)); return;
            case Type.LONG: out.add(new InsnNode(LCONST_0)); return;
            case Type.DOUBLE: out.add(new InsnNode(DCONST_0)); return;
            case Type.ARRAY: case Type.OBJECT: out.add(new InsnNode(ACONST_NULL)); return;
            default: throw new IllegalArgumentException("Unsupported default type " + type);
        }
    }

    private static boolean matches(Parsed value, int capacity, int petId) {
        if (value.error != null || !value.timestampCaptured || value.capacity != capacity || value.petId != petId
                || !value.tailText.equals(TAIL_TEXT) || value.timestamp != TIMESTAMP
                || value.remaining != 1 || value.trailer != TRAILER) return false;
        if (petId < 0) return true;
        return value.petNumber == 7001 && value.petName.equals("fixture-pet") && value.petLevel == 6;
    }

    private static String describe(Parsed p) { return p.error == null ? "capacity=" + p.capacity
            + ", pages=" + p.pages + ", petId=" + p.petId + ", tail=" + p.tailText
            + ", timestampCaptured=" + p.timestampCaptured : p.error; }

    private static AbstractInsnNode nextReal(AbstractInsnNode n) {
        if (n == null) return null;
        do { n = n.getNext(); } while (n != null && n.getOpcode() < 0);
        return n;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode n) {
        if (n == null) return null;
        do { n = n.getPrevious(); } while (n != null && n.getOpcode() < 0);
        return n;
    }

    private static int integer(AbstractInsnNode n) {
        if (n instanceof IntInsnNode) return ((IntInsnNode)n).operand;
        if (n != null && n.getOpcode() == BIPUSH) return ((IntInsnNode)n).operand;
        if (n != null && n.getOpcode() == SIPUSH) return ((IntInsnNode)n).operand;
        if (n instanceof org.objectweb.asm.tree.LdcInsnNode && ((org.objectweb.asm.tree.LdcInsnNode)n).cst instanceof Integer)
            return ((Integer)((org.objectweb.asm.tree.LdcInsnNode)n).cst).intValue();
        if (n != null && n.getOpcode() >= ICONST_M1 && n.getOpcode() <= ICONST_5) return n.getOpcode() - ICONST_0;
        return Integer.MIN_VALUE;
    }

    private static boolean sameField(FieldInsnNode a, FieldInsnNode b) {
        return a.owner.equals(b.owner) && a.name.equals(b.name) && a.desc.equals(b.desc);
    }

    private static Properties load(File file) throws Exception {
        Properties result = new Properties();
        InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
        try { result.load(reader); } finally { reader.close(); }
        return result;
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        require(value != null && !value.isEmpty(), "Catalog property missing: " + key);
        return value;
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Parsed {
        final int capacity, pages, petId, petNumber, petLevel, remaining, trailer;
        final String petName, tailText, error;
        final long timestamp;
        final boolean timestampCaptured;
        Parsed(int capacity, int pages, int petId, int petNumber, String petName, int petLevel,
                String tailText, long timestamp, boolean timestampCaptured,
                int remaining, int trailer, String error) {
            this.capacity = capacity; this.pages = pages; this.petId = petId; this.petNumber = petNumber;
            this.petName = petName; this.petLevel = petLevel; this.tailText = tailText;
            this.timestamp = timestamp; this.timestampCaptured = timestampCaptured;
            this.remaining = remaining; this.trailer = trailer; this.error = error;
        }
    }

    private static final class SyntheticPacket {
        final byte[] bytes;
        final int capacityOffset;
        SyntheticPacket(byte[] bytes, int capacityOffset) { this.bytes = bytes; this.capacityOffset = capacityOffset; }
    }

    private static final class ProbeLoader extends ClassLoader {
        ProbeLoader(ClassLoader parent) { super(parent); }
        Class<?> define(String name, byte[] bytecode) { return defineClass(name, bytecode, 0, bytecode.length); }
    }
}
