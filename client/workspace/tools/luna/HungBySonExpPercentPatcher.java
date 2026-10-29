import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Incrementally replaces only the native Hung By Son HUD percentage fraction. */
public final class HungBySonExpPercentPatcher implements Opcodes {
    private static final String INPUT_SHA = "BCF304BB6245CDBB6F48D5888404F8186E4E0895E2DCDAF608C68D53FEADA6E2";
    private static final String SUPPORT = "luna/features/ExpEstimateSupport";
    private static final String RECORD = SUPPORT + "$EstimateRecord";
    private static final String PERCENT_HELPER = "luna/ui/ExpPercentInterpolator";
    private static final String PERCENT_DESC = "(I)Ljava/lang/String;";
    private static final String GAIN_DESC = "(Ljava/lang/String;IIII)V";
    private static final String BUFFER = "java/lang/StringBuffer";
    private static final String APPEND_STRING = "(Ljava/lang/String;)Ljava/lang/StringBuffer;";
    private static final String APPEND_INT = "(I)Ljava/lang/StringBuffer;";

    private static final class EntryData {
        final byte[] bytes;
        final long time;
        final String comment;
        final byte[] extra;
        EntryData(byte[] bytes, JarEntry entry) {
            this.bytes = bytes;
            this.time = entry.getTime();
            this.comment = entry.getComment();
            this.extra = entry.getExtra();
        }
    }

    private static final class PatchSite {
        final MethodNode method;
        final FieldInsnNode main;
        final FieldInsnNode progress;
        final List<AbstractInsnNode> replaced;
        final AbstractInsnNode after;
        final MethodInsnNode call;
        final MethodInsnNode append;
        final String canonicalBefore;
        PatchSite(MethodNode method, FieldInsnNode main, FieldInsnNode progress,
                List<AbstractInsnNode> replaced, AbstractInsnNode after,
                MethodInsnNode call, MethodInsnNode append, String canonicalBefore) {
            this.method = method;
            this.main = main;
            this.progress = progress;
            this.replaced = replaced;
            this.after = after;
            this.call = call;
            this.append = append;
            this.canonicalBefore = canonicalBefore;
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode c = new ClassNode(ASM9);
        new ClassReader(bytes).accept(c, 0);
        return c;
    }

    private static byte[] write(ClassNode c) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(w);
        return w.toByteArray();
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        try {
            for (int n; (n = in.read(buffer)) >= 0;) digest.update(buffer, 0, n);
        } finally { in.close(); }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format("%02X", Integer.valueOf(b & 255)));
        return out.toString();
    }

    private static Map<String, EntryData> readJar(File file) throws Exception {
        Map<String, EntryData> entries = new LinkedHashMap<String, EntryData>();
        JarFile jar = new JarFile(file, false);
        try {
            Enumeration<JarEntry> all = jar.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                check(!entries.containsKey(entry.getName()), "Duplicate JAR entry " + entry.getName());
                InputStream in = jar.getInputStream(entry);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                try { for (int n; (n = in.read(buffer)) >= 0;) bytes.write(buffer, 0, n); }
                finally { in.close(); }
                entries.put(entry.getName(), new EntryData(bytes.toByteArray(), entry));
            }
        } finally { jar.close(); }
        return entries;
    }

    private static Properties loadProperties(File file) throws Exception {
        Properties p = new Properties();
        InputStream in = new FileInputStream(file);
        try { p.load(in); } finally { in.close(); }
        return p;
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        check(value != null && value.length() != 0, "Missing catalog property " + key);
        return value;
    }

    private static Map<String, byte[]> compiledSupport(File root) throws Exception {
        check(root.isDirectory(), "Compiled support directory is missing: " + root);
        String[] names = { SUPPORT + ".class", RECORD + ".class", PERCENT_HELPER + ".class" };
        Map<String, byte[]> result = new LinkedHashMap<String, byte[]>();
        for (String name : names) {
            File file = new File(root, name.replace('/', File.separatorChar));
            if (!file.isFile()) continue;
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            ClassNode c = node(bytes);
            check(c.name.equals(name.substring(0, name.length() - 6)), "Compiled class path/name mismatch " + name);
            check((c.version & 0xFFFF) <= V1_7, "Support class exceeds Java 7 class version: " + name);
            screenConstantPool(name, bytes);
            result.put(name, bytes);
        }
        check(result.containsKey(SUPPORT + ".class"), "Missing compiled ExpEstimateSupport.class");
        check(result.containsKey(PERCENT_HELPER + ".class"), "Missing compiled ExpPercentInterpolator.class");
        return result;
    }

    /** Screen class-file UTF-8 constants as well as referenced instructions for non-CLDC APIs. */
    private static void screenConstantPool(String name, byte[] bytes) {
        String constants = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        String[] forbidden = {
            "java/lang/StringBuilder", "java/lang/invoke/", "java/util/", "java/time/", "java/nio/",
            "java/awt/", "java/net/", "java/lang/reflect/", "java/lang/Process",
            "java/lang/AutoCloseable", "java/lang/Appendable", "java/lang/Iterable",
            "javax/", "addSuppressed"
        };
        for (String ref : forbidden) check(constants.indexOf(ref) < 0,
                "Non-CLDC constant-pool reference " + ref + " in " + name);
    }

    private static MethodNode findMethod(ClassNode c, String name, String desc) {
        MethodNode found = null;
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) {
            check(found == null, "Duplicate method " + c.name + "." + name + desc);
            found = m;
        }
        return found;
    }

    private static void validateSupportMethods(Map<String, byte[]> original,
            Map<String, byte[]> compiled) {
        ClassNode oldSupport = node(original.get(SUPPORT + ".class"));
        ClassNode newSupport = node(compiled.get(SUPPORT + ".class"));
        MethodNode oldPercent = findMethod(oldSupport, "percentage", PERCENT_DESC);
        check(oldPercent == null, "Input already contains percentage(int); refusing a second patch");
        MethodNode percent = findMethod(newSupport, "percentage", PERCENT_DESC);
        check(percent != null && (percent.access & (ACC_PUBLIC | ACC_STATIC | ACC_SYNCHRONIZED))
                == (ACC_PUBLIC | ACC_STATIC | ACC_SYNCHRONIZED),
                "Expected public static synchronized percentage(int) support method");
        check(findMethod(oldSupport, "gain", GAIN_DESC) != null
                && findMethod(newSupport, "gain", GAIN_DESC) != null,
                "Expected the existing EXP gain lifecycle method to remain present");

        Set<String> intentionallyUpdated = new LinkedHashSet<String>();
        intentionallyUpdated.add("percentage" + PERCENT_DESC);
        intentionallyUpdated.add("gain" + GAIN_DESC);
        intentionallyUpdated.add("resetSegment(I)V");
        intentionallyUpdated.add("clearSession()V");
        intentionallyUpdated.add("invalidateLine()V");
        intentionallyUpdated.add("invalidatePercentage(Z)V");
        intentionallyUpdated.add("<clinit>()V");
        compareMethodsExcept(oldSupport, newSupport, intentionallyUpdated, true);

        String recordPath = RECORD + ".class";
        if (compiled.containsKey(recordPath)) {
            check(original.containsKey(recordPath), "EstimateRecord unexpectedly became a new class");
            compareMethodsExcept(node(original.get(recordPath)), node(compiled.get(recordPath)),
                    Collections.<String>emptySet(), false);
        }
    }

    private static void compareMethodsExcept(ClassNode oldClass, ClassNode newClass,
            Set<String> allowedChanges, boolean allowNewPercentage) {
        Map<String, MethodNode> oldMethods = methods(oldClass);
        Map<String, MethodNode> newMethods = methods(newClass);
        for (Map.Entry<String, MethodNode> e : oldMethods.entrySet()) {
            MethodNode after = newMethods.get(e.getKey());
            check(after != null, "Compiled support removed method " + oldClass.name + "." + e.getKey());
            if (!allowedChanges.contains(e.getKey()))
                check(canonical(e.getValue()).equals(canonical(after)),
                        "Unapproved support method change " + oldClass.name + "." + e.getKey());
        }
        for (String key : newMethods.keySet()) if (!oldMethods.containsKey(key))
            check(allowNewPercentage && (key.equals("percentage" + PERCENT_DESC)
                    || key.equals("invalidatePercentage(Z)V")),
                    "Unapproved new support method " + newClass.name + "." + key);
    }

    private static Map<String, MethodNode> methods(ClassNode c) {
        Map<String, MethodNode> result = new LinkedHashMap<String, MethodNode>();
        for (MethodNode m : c.methods) {
            String key = m.name + m.desc;
            check(!result.containsKey(key), "Duplicate method " + c.name + "." + key);
            result.put(key, m);
        }
        return result;
    }

    private static AbstractInsnNode nextOpcode(AbstractInsnNode n) {
        do { n = n == null ? null : n.getNext(); } while (n != null && n.getOpcode() < 0);
        return n;
    }

    private static AbstractInsnNode previousOpcode(AbstractInsnNode n) {
        do { n = n == null ? null : n.getPrevious(); } while (n != null && n.getOpcode() < 0);
        return n;
    }

    private static boolean isField(AbstractInsnNode n, int opcode, String owner, String name, String desc) {
        if (!(n instanceof FieldInsnNode) || n.getOpcode() != opcode) return false;
        FieldInsnNode f = (FieldInsnNode)n;
        return f.owner.equals(owner) && f.name.equals(name) && f.desc.equals(desc);
    }

    private static boolean isInt(AbstractInsnNode n, int opcode, int value) {
        return n instanceof IntInsnNode && n.getOpcode() == opcode && ((IntInsnNode)n).operand == value;
    }

    private static boolean isInvoke(AbstractInsnNode n, int opcode, String owner, String name,
            String desc, boolean itf) {
        if (!(n instanceof MethodInsnNode) || n.getOpcode() != opcode) return false;
        MethodInsnNode m = (MethodInsnNode)n;
        return m.owner.equals(owner) && m.name.equals(name) && m.desc.equals(desc) && m.itf == itf;
    }

    private static boolean isString(AbstractInsnNode n, String text) {
        return n instanceof LdcInsnNode && text.equals(((LdcInsnNode)n).cst);
    }

    private static AbstractInsnNode[] opcodesAfter(AbstractInsnNode start, int count) {
        AbstractInsnNode[] result = new AbstractInsnNode[count];
        AbstractInsnNode n = start;
        for (int i = 0; i < count; i++) {
            n = nextOpcode(n);
            if (n == null) return null;
            result[i] = n;
        }
        return result;
    }

    private static PatchSite patchHud(ClassNode hud, Properties catalog) {
        String owner = required(catalog, "slot.0.hud.owner");
        String methodName = required(catalog, "slot.0.hud.method");
        String methodDesc = required(catalog, "slot.0.hud.descriptor");
        String mainOwner = required(catalog, "slot.0.main.owner");
        String mainField = required(catalog, "slot.0.main.field");
        String mainDesc = required(catalog, "slot.0.main.descriptor");
        String entity = required(catalog, "slot.0.entity.owner");
        String progressField = required(catalog, "slot.0.progress.field");
        check("bA".equals(progressField), "Pinned native progress field changed");
        check(hud.name.equals(owner), "HUD owner/catalog mismatch");
        MethodNode method = findMethod(hud, methodName, methodDesc);
        check(method != null, "Native HUD method missing");
        String before = canonical(method);
        AbstractInsnNode foundMain = null, foundProgress = null, firstInt = null, lastAppend = null;
        List<AbstractInsnNode> foundRange = null;
        int matches = 0;
        for (AbstractInsnNode n : method.instructions.toArray()) {
            if (!isField(n, GETSTATIC, mainOwner, mainField, mainDesc)) continue;
            AbstractInsnNode progress = nextOpcode(n);
            if (!isField(progress, GETFIELD, entity, progressField, "S")) continue;
            AbstractInsnNode[] q = opcodesAfter(progress, 12);
            if (q == null) continue;
            boolean exact = isInt(q[0], BIPUSH, 10) && q[1].getOpcode() == IDIV
                && isInvoke(q[2], INVOKEVIRTUAL, BUFFER, "append", APPEND_INT, false)
                && isString(q[3], ",")
                && isInvoke(q[4], INVOKEVIRTUAL, BUFFER, "append", APPEND_STRING, false)
                && isField(q[5], GETSTATIC, mainOwner, mainField, mainDesc)
                && isField(q[6], GETFIELD, entity, progressField, "S")
                && isInt(q[7], BIPUSH, 10) && q[8].getOpcode() == IREM
                && isInvoke(q[9], INVOKEVIRTUAL, BUFFER, "append", APPEND_INT, false)
                && isString(q[10], "%")
                && isInvoke(q[11], INVOKEVIRTUAL, BUFFER, "append", APPEND_STRING, false);
            if (!exact) continue;
            // Everything before this fraction, including the native font and '+',
            // stays untouched and is covered by the whole-method inverse proof.
            matches++;
            foundMain = n;
            foundProgress = progress;
            firstInt = q[0];
            lastAppend = q[11];
            foundRange = new ArrayList<AbstractInsnNode>();
            for (AbstractInsnNode x = firstInt; x != null; x = x.getNext()) {
                foundRange.add(x);
                if (x == lastAppend) break;
            }
        }
        check(matches == 1, "Expected exactly one exact native tenths-percent sequence; found " + matches);
        check(foundRange != null && !foundRange.isEmpty(), "Native percentage replacement range missing");
        AbstractInsnNode after = lastAppend.getNext();
        check(!containsBranchTarget(method, foundRange), "Native percentage range contains a control-flow target");
        MethodInsnNode call = new MethodInsnNode(INVOKESTATIC, SUPPORT, "percentage", PERCENT_DESC, false);
        MethodInsnNode append = new MethodInsnNode(INVOKEVIRTUAL, BUFFER, "append", APPEND_STRING, false);
        for (AbstractInsnNode n : foundRange) if (n.getOpcode() >= 0) method.instructions.remove(n);
        InsnList replacement = new InsnList();
        replacement.add(call);
        replacement.add(append);
        method.instructions.insert(foundProgress, replacement);
        check(countCall(method, SUPPORT, "percentage", PERCENT_DESC) == 1, "Exactly one percentage hook");
        return new PatchSite(method, (FieldInsnNode)foundMain, (FieldInsnNode)foundProgress,
                foundRange, after, call, append, before);
    }

    private static boolean containsBranchTarget(MethodNode method, List<AbstractInsnNode> range) {
        Set<LabelNode> labels = Collections.newSetFromMap(new IdentityHashMap<LabelNode, Boolean>());
        for (AbstractInsnNode n : range) if (n instanceof LabelNode) labels.add((LabelNode)n);
        if (labels.isEmpty()) return false;
        for (AbstractInsnNode n : method.instructions.toArray()) {
            if (n instanceof JumpInsnNode && labels.contains(((JumpInsnNode)n).label)) return true;
            if (n instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode s = (TableSwitchInsnNode)n;
                if (labels.contains(s.dflt) || s.labels.containsAll(labels)) return true;
                for (LabelNode l : s.labels) if (labels.contains(l)) return true;
            }
            if (n instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode s = (LookupSwitchInsnNode)n;
                if (labels.contains(s.dflt)) return true;
                for (LabelNode l : s.labels) if (labels.contains(l)) return true;
            }
        }
        for (TryCatchBlockNode t : method.tryCatchBlocks)
            if (labels.contains(t.start) || labels.contains(t.end) || labels.contains(t.handler)) return true;
        return false;
    }

    private static int countCall(MethodNode method, String owner, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode n : method.instructions.toArray())
            if (n instanceof MethodInsnNode) {
                MethodInsnNode m = (MethodInsnNode)n;
                if (m.getOpcode() == INVOKESTATIC && m.owner.equals(owner)
                        && m.name.equals(name) && m.desc.equals(desc)) count++;
            }
        return count;
    }

    private static void proveInverse(PatchSite site) {
        site.method.instructions.remove(site.call);
        site.method.instructions.remove(site.append);
        AbstractInsnNode next = site.after;
        for (int i = site.replaced.size() - 1; i >= 0; i--) {
            AbstractInsnNode original = site.replaced.get(i);
            if (original.getOpcode() >= 0) {
                if (next == null) site.method.instructions.add(original);
                else site.method.instructions.insertBefore(next, original);
            }
            next = original;
        }
        check(site.canonicalBefore.equals(canonical(site.method)),
                "Native HUD method inverse canonical proof failed");
        check(countCall(site.method, SUPPORT, "percentage", PERCENT_DESC) == 0,
                "Inverse left percentage hook behind");
    }

    private static String canonical(MethodNode m) {
        Map<LabelNode, Integer> labels = new IdentityHashMap<LabelNode, Integer>();
        int index = 0;
        for (AbstractInsnNode n : m.instructions.toArray()) {
            if (n instanceof LabelNode) labels.put((LabelNode)n, Integer.valueOf(index));
            if (n.getOpcode() >= 0) index++;
        }
        StringBuilder b = new StringBuilder();
        b.append(m.access).append(m.name).append(m.desc).append(Arrays.toString(m.exceptions.toArray()));
        for (AbstractInsnNode n : m.instructions.toArray()) if (n.getOpcode() >= 0) {
            b.append('|').append(n.getOpcode());
            if (n instanceof VarInsnNode) b.append(':').append(((VarInsnNode)n).var);
            else if (n instanceof IntInsnNode) b.append(':').append(((IntInsnNode)n).operand);
            else if (n instanceof LdcInsnNode) b.append(':').append(((LdcInsnNode)n).cst);
            else if (n instanceof FieldInsnNode) {
                FieldInsnNode x = (FieldInsnNode)n;
                b.append(':').append(x.owner).append('.').append(x.name).append(x.desc);
            } else if (n instanceof MethodInsnNode) {
                MethodInsnNode x = (MethodInsnNode)n;
                b.append(':').append(x.owner).append('.').append(x.name).append(x.desc).append(x.itf);
            } else if (n instanceof TypeInsnNode) b.append(':').append(((TypeInsnNode)n).desc);
            else if (n instanceof JumpInsnNode) b.append(':').append(labels.get(((JumpInsnNode)n).label));
            else if (n instanceof IincInsnNode) {
                IincInsnNode x = (IincInsnNode)n; b.append(':').append(x.var).append(':').append(x.incr);
            } else if (n instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode x = (TableSwitchInsnNode)n;
                b.append(':').append(x.min).append(':').append(x.max).append(':').append(labels.get(x.dflt));
                for (LabelNode l : x.labels) b.append(':').append(labels.get(l));
            } else if (n instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode x = (LookupSwitchInsnNode)n;
                b.append(':').append(x.keys).append(':').append(labels.get(x.dflt));
                for (LabelNode l : x.labels) b.append(':').append(labels.get(l));
            } else if (n instanceof MultiANewArrayInsnNode) {
                MultiANewArrayInsnNode x = (MultiANewArrayInsnNode)n;
                b.append(':').append(x.desc).append(':').append(x.dims);
            } else if (n instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode x = (InvokeDynamicInsnNode)n;
                b.append(':').append(x.name).append(x.desc).append(':').append(x.bsm)
                    .append(':').append(Arrays.toString(x.bsmArgs));
            }
        }
        for (TryCatchBlockNode t : m.tryCatchBlocks)
            b.append("!T:").append(labels.get(t.start)).append(':').append(labels.get(t.end))
                .append(':').append(labels.get(t.handler)).append(':').append(t.type);
        return b.toString();
    }

    private static void verifyUnmodifiedHudMethods(ClassNode before, ClassNode after, String targetName, String targetDesc) {
        Map<String, MethodNode> oldMethods = methods(before);
        Map<String, MethodNode> newMethods = methods(after);
        check(oldMethods.keySet().equals(newMethods.keySet()), "Native HUD method topology changed");
        for (String key : oldMethods.keySet()) {
            MethodNode oldMethod = oldMethods.get(key), newMethod = newMethods.get(key);
            if (oldMethod.name.equals(targetName) && oldMethod.desc.equals(targetDesc)) continue;
            check(canonical(oldMethod).equals(canonical(newMethod)), "Unmodified native HUD method changed: " + key);
        }
    }

    private static void writeJar(File output, Map<String, EntryData> entries) throws Exception {
        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory()) check(parent.mkdirs(), "Cannot create output directory " + parent);
        JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String, EntryData> e : entries.entrySet()) {
                JarEntry z = new JarEntry(e.getKey());
                EntryData data = e.getValue();
                z.setTime(data.time >= 0 ? data.time : 0L);
                if (data.comment != null) z.setComment(data.comment);
                if (data.extra != null) z.setExtra(data.extra);
                out.putNextEntry(z);
                out.write(data.bytes);
                out.closeEntry();
            }
        } finally { out.close(); }
    }

    private static List<String> mergeList(String current, List<String> additions) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        if (current != null) for (String item : current.split(",")) if (item.length() != 0) values.add(item);
        values.addAll(additions);
        return new ArrayList<String>(values);
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) { if (out.length() != 0) out.append(','); out.append(value); }
        return out.toString();
    }

    private static void writeProperties(File file, Properties p) throws Exception {
        OutputStream out = new FileOutputStream(file);
        try { p.store(out, "Incremental Hung By Son EXP percentage HUD patch"); }
        finally { out.close(); }
    }

    private static void writeReport(File file, List<String> changed, List<String> added,
            String outputHash, int nativeVersion) throws Exception {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
        try {
            out.println("EXP_PERCENT_PATCH=PASS");
            out.println("changed.classes=" + join(changed));
            out.println("added.classes=" + join(added));
            out.println("percent.hooks=1; percent.decimals=3");
            out.println("native.inverse=PASS; all other HUD methods canonical");
            out.println("resources.manifest=UNCHANGED; class.version=" + nativeVersion);
            out.println("output.sha256=" + outputHash);
        } finally { out.close(); }
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 6,
                "input.jar compiledSupportDir output.jar catalogIn outputCatalog report");
        File input = new File(args[0]);
        File supportDir = new File(args[1]);
        File output = new File(args[2]);
        File catalogIn = new File(args[3]);
        File catalogOut = new File(args[4]);
        File report = new File(args[5]);
        check(input.isFile() && catalogIn.isFile(), "Input JAR and input catalog must exist");
        check(!output.exists() && !catalogOut.exists() && !report.exists(), "Output paths must be fresh");
        String actualInputHash = hash(input);
        check(INPUT_SHA.equals(actualInputHash), "Pinned input SHA-256 mismatch: " + actualInputHash);

        Properties catalog = loadProperties(catalogIn);
        check("1".equals(required(catalog, "slots")), "This patcher supports the single client only");
        check(INPUT_SHA.equals(required(catalog, "output.sha256")), "Input catalog is not bound to input JAR");
        check("native".equals(required(catalog, "notice.position")), "Native notice layout catalog mismatch");
        Map<String, EntryData> original = readJar(input);
        Map<String, EntryData> result = new LinkedHashMap<String, EntryData>(original);
        check(original.containsKey("META-INF/MANIFEST.MF"), "Input manifest is missing");

        String hudOwner = required(catalog, "slot.0.hud.owner");
        String hudPath = hudOwner + ".class";
        check(original.containsKey(hudPath), "Catalog HUD class missing from input JAR");
        byte[] originalHudBytes = original.get(hudPath).bytes;
        ClassNode originalHud = node(originalHudBytes);
        int nativeVersion = originalHud.version & 0xFFFF;
        ClassNode patchedHud = node(originalHudBytes);
        String targetName = required(catalog, "slot.0.hud.method");
        String targetDesc = required(catalog, "slot.0.hud.descriptor");
        PatchSite site = patchHud(patchedHud, catalog);
        verifyUnmodifiedHudMethods(originalHud, patchedHud, targetName, targetDesc);

        Map<String, byte[]> support = compiledSupport(supportDir);
        check(original.containsKey(SUPPORT + ".class"), "Input EXP support class is missing");
        validateSupportMethods(toClassBytes(original), support);
        byte[] patchedHudBytes = write(patchedHud);
        check((node(patchedHudBytes).version & 0xFFFF) == nativeVersion,
                "Native class version changed");
        result.put(hudPath, new EntryData(patchedHudBytes, toJarEntry(original.get(hudPath), hudPath)));

        List<String> changed = new ArrayList<String>();
        List<String> added = new ArrayList<String>();
        if (!Arrays.equals(originalHudBytes, patchedHudBytes)) changed.add(hudPath);
        for (Map.Entry<String, byte[]> e : support.entrySet()) {
            byte[] old = original.containsKey(e.getKey()) ? original.get(e.getKey()).bytes : null;
            if (old != null && Arrays.equals(old, e.getValue())) continue;
            check(e.getKey().equals(SUPPORT + ".class") || e.getKey().equals(RECORD + ".class")
                    || e.getKey().equals(PERCENT_HELPER + ".class"), "Support allow-list violation");
            if (old == null) {
                added.add(e.getKey());
                result.put(e.getKey(), new EntryData(e.getValue(), new JarEntry(e.getKey())));
            } else {
                changed.add(e.getKey());
                result.put(e.getKey(), new EntryData(e.getValue(), toJarEntry(original.get(e.getKey()), e.getKey())));
            }
        }
        check(changed.contains(hudPath), "Native HUD class did not change");
        check(added.contains(PERCENT_HELPER + ".class") || original.containsKey(PERCENT_HELPER + ".class"),
                "Percentage interpolator class is not present in result");

        // Every archive entry outside the exact native/support class allow-list stays byte-identical.
        Set<String> allowed = new LinkedHashSet<String>();
        allowed.add(hudPath); allowed.add(SUPPORT + ".class");
        allowed.add(RECORD + ".class"); allowed.add(PERCENT_HELPER + ".class");
        for (String name : original.keySet()) {
            if (!allowed.contains(name))
                check(Arrays.equals(original.get(name).bytes, result.get(name).bytes), "Unapproved entry changed: " + name);
        }
        for (String name : result.keySet())
            if (!original.containsKey(name)) check(allowed.contains(name), "Unapproved entry added: " + name);
        check(result.keySet().containsAll(original.keySet()), "Original JAR entry removed");

        // Serialize the candidate first, then reconstruct the native method in memory and prove the inverse.
        writeJar(output, result);
        Map<String, EntryData> saved = readJar(output);
        check(saved.keySet().equals(result.keySet()), "Output JAR topology changed");
        for (String name : result.keySet())
            check(Arrays.equals(result.get(name).bytes, saved.get(name).bytes), "Output JAR entry mismatch: " + name);
        proveInverse(site);
        check(INPUT_SHA.equals(hash(input)), "Pinned input changed during patch");

        String outputHash = hash(output);
        catalog.setProperty("input.sha256", actualInputHash);
        catalog.setProperty("output.sha256", outputHash);
        catalog.setProperty("changed.classes", join(mergeList(catalog.getProperty("changed.classes"), changed)));
        catalog.setProperty("added.classes", join(mergeList(catalog.getProperty("added.classes"), added)));
        catalog.setProperty("percent.hooks", "1");
        catalog.setProperty("percent.decimals", "3");
        catalog.remove("percent.font.compact");
        catalog.remove("percent.separator.compact");
        catalog.setProperty("slot.0.percent.hooks", "1");
        writeProperties(catalogOut, catalog);
        writeReport(report, changed, added, outputHash, nativeVersion);
        System.out.println("EXP_PERCENT_PATCH=PASS changed=" + changed.size() + " added=" + added.size()
                + " output.sha256=" + outputHash);
    }

    private static Map<String, byte[]> toClassBytes(Map<String, EntryData> entries) {
        Map<String, byte[]> bytes = new LinkedHashMap<String, byte[]>();
        for (Map.Entry<String, EntryData> e : entries.entrySet())
            if (e.getKey().endsWith(".class")) bytes.put(e.getKey(), e.getValue().bytes);
        return bytes;
    }

    private static JarEntry toJarEntry(EntryData data, String name) {
        JarEntry entry = new JarEntry(name);
        entry.setTime(data.time >= 0 ? data.time : 0L);
        if (data.comment != null) entry.setComment(data.comment);
        if (data.extra != null) entry.setExtra(data.extra);
        return entry;
    }
}
