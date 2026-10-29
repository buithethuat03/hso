import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * HungX3SlotParity <x1.jar> <previousX1.jar> <x3.jar> <previousX3.jar>: every class of x1 that is new or
 * changed since previousX1 (the source of the previous X3) has, in each X3 slot, every one of its x1
 * methods instruction-for-instruction equal after removing the slot prefix and the packer's wrapper
 * renames (lib/ wrapper types, per-slot RMS open aliases, the MIDlet moved to the root, rooted resource paths). Negative control: the previous X3 lacks the
 * new classes in slot 1.
 */
public class HungX3SlotParity {
    static final String[] SLOTS = {"", "b/", "c/"};
    static int checks;

    static Map<String, byte[]> read(File jar) throws IOException {
        Map<String, byte[]> out = new TreeMap<String, byte[]>();
        JarFile f = new JarFile(jar);
        try {
            for (Enumeration<JarEntry> e = f.entries(); e.hasMoreElements();) {
                JarEntry z = e.nextElement();
                if (!z.getName().endsWith(".class")) continue;
                InputStream in = f.getInputStream(z);
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0;) b.write(buf, 0, n);
                out.put(z.getName().substring(0, z.getName().length() - 6), b.toByteArray());
            }
        } finally { f.close(); }
        return out;
    }
    static ClassNode node(byte[] b) { ClassNode c = new ClassNode(); new ClassReader(b).accept(c, 0); return c; }

    /** Slot prefix and wrapper renames removed from an internal name or descriptor. */
    static String norm(String s, String slot) {
        if (s == null) return null;
        if (!slot.isEmpty()) s = s.replace("L" + slot, "L").replaceAll("^" + slot, "");
        return s.replaceAll("javax/microedition/[a-z]+/", "lib/").replace("com/silverknight/TemMidlet", "TemMidlet");
    }
    static String name(String n) { return n.matches("[abc]penRecordStore") ? "openRecordStore" : n; }

    static String text(MethodNode m, String slot) {
        StringBuilder out = new StringBuilder(norm(m.desc, slot)).append('\n');
        Map<LabelNode, Integer> labels = new HashMap<LabelNode, Integer>();
        for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof LabelNode) labels.put((LabelNode)i, labels.size());
        for (AbstractInsnNode i : m.instructions.toArray()) {
            if (i instanceof LineNumberNode || i instanceof FrameNode) continue;
            if (i instanceof LabelNode) { out.append('L').append(labels.get(i)).append('\n'); continue; }
            // The packer roots resource paths through the slot helper after each resource name.
            if (i instanceof MethodInsnNode && ((MethodInsnNode)i).owner.endsWith("X3SlotSupport") && ((MethodInsnNode)i).name.equals("absoluteResource")) continue;
            out.append(i.getOpcode());
            if (i instanceof MethodInsnNode) { MethodInsnNode x = (MethodInsnNode)i; out.append(' ').append(norm(x.owner, slot)).append('.').append(name(x.name)).append(norm(x.desc, slot)); }
            else if (i instanceof FieldInsnNode) { FieldInsnNode x = (FieldInsnNode)i; out.append(' ').append(norm(x.owner, slot)).append('.').append(x.name).append(norm(x.desc, slot)); }
            else if (i instanceof TypeInsnNode) out.append(' ').append(norm(((TypeInsnNode)i).desc, slot));
            else if (i instanceof VarInsnNode) out.append(' ').append(((VarInsnNode)i).var);
            else if (i instanceof IntInsnNode) out.append(' ').append(((IntInsnNode)i).operand);
            else if (i instanceof LdcInsnNode) { Object c = ((LdcInsnNode)i).cst; out.append(' ').append(c instanceof Type ? norm(((Type)c).getDescriptor(), slot) : c); }
            else if (i instanceof JumpInsnNode) out.append(" L").append(labels.get(((JumpInsnNode)i).label));
            else if (i instanceof IincInsnNode) out.append(' ').append(((IincInsnNode)i).var).append(' ').append(((IincInsnNode)i).incr);
            else if (i instanceof TableSwitchInsnNode) { TableSwitchInsnNode x = (TableSwitchInsnNode)i; out.append(' ').append(x.min).append(' ').append(x.max); for (LabelNode l : x.labels) out.append(" L").append(labels.get(l)); out.append(" L").append(labels.get(x.dflt)); }
            else if (i instanceof LookupSwitchInsnNode) { LookupSwitchInsnNode x = (LookupSwitchInsnNode)i; out.append(' ').append(x.keys); for (LabelNode l : x.labels) out.append(" L").append(labels.get(l)); out.append(" L").append(labels.get(x.dflt)); }
            else if (i instanceof MultiANewArrayInsnNode) out.append(' ').append(norm(((MultiANewArrayInsnNode)i).desc, slot)).append(' ').append(((MultiANewArrayInsnNode)i).dims);
            out.append('\n');
        }
        for (TryCatchBlockNode t : m.tryCatchBlocks)
            out.append("try L").append(labels.get(t.start)).append(" L").append(labels.get(t.end)).append(" L").append(labels.get(t.handler)).append(' ').append(norm(t.type, slot)).append('\n');
        return out.toString();
    }
    static void require(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); System.out.println("PASS " + message); }
    static String shortName(String n) { return n.length() > 40 ? "~" + n.replaceAll("^i+", "") : n; }

    public static void main(String[] a) throws Exception {
        Map<String, byte[]> x1 = read(new File(a[0])), prior = read(new File(a[1])), x3 = read(new File(a[2])), oldX3 = read(new File(a[3]));
        List<String> delta = new ArrayList<String>(), added = new ArrayList<String>();
        for (Map.Entry<String, byte[]> e : x1.entrySet()) {
            byte[] before = prior.get(e.getKey());
            if (before == null) added.add(e.getKey());
            if (before == null || !Arrays.equals(before, e.getValue())) delta.add(e.getKey());
        }
        require(!delta.isEmpty(), "x1 changes since the previous X3 source: " + delta.size() + " classes, new " + added);
        int methods = 0;
        for (String cls : delta) {
            ClassNode want = node(x1.get(cls));
            String mapped = cls.equals("com/silverknight/TemMidlet") ? "TemMidlet" : cls;
            for (String slot : SLOTS) {
                byte[] bytes = x3.get(slot + mapped);
                require(bytes != null, "X3 slot " + (slot.isEmpty() ? "1" : slot) + " has " + shortName(cls));
                ClassNode got = node(bytes);
                Map<String, MethodNode> have = new HashMap<String, MethodNode>();
                for (MethodNode m : got.methods) have.put(m.name + norm(m.desc, slot), m);
                int same = 0;
                List<String> differ = new ArrayList<String>();
                for (MethodNode m : want.methods) {
                    MethodNode g = have.get(m.name + norm(m.desc, ""));
                    if (g == null || !text(m, "").equals(text(g, slot))) differ.add(m.name + m.desc);
                    else same++;
                }
                // The packer's lifecycle rewrites <clinit> of every game class to register its reset hook.
                differ.remove("<clinit>()V");
                require(differ.isEmpty(), "X3 slot " + (slot.isEmpty() ? "1" : slot) + " " + shortName(cls) + ": " + same + " methods equal x1" + (differ.isEmpty() ? "" : ", differ " + differ));
                methods += same;
            }
        }
        for (String cls : added) require(!oldX3.containsKey(cls), "Negative control: the previous X3 has no " + shortName(cls));
        System.out.println("HungX3SlotParity PASS checks=" + checks + " classes=" + delta.size() + " methods=" + methods);
    }
}
