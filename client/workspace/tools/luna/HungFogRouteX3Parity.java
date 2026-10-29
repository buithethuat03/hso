import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** HungFogRouteX3Parity <x1.jar> <x3.jar> <oldX3.jar>: patched fog/route methods equal x1 in every X3 slot after removing the slot prefix. */
public class HungFogRouteX3Parity {
    static String route() { StringBuilder b = new StringBuilder(); while (b.length() + 8 < 100) b.append('i'); return b.append("lliiiiii").toString(); }
    static final String[][] METHODS = {
        {"HungBySonModUpLv", "fogTick"}, {"HungBySonModUpLv", "codexFogSaleBusy"}, {"HungBySonModUpLv", "onDialog"},
        {route(), "update"}, {route(), "codexFogHold"}};
    static int checks;

    static ClassNode load(File jar, String name) throws IOException {
        JarFile f = new JarFile(jar);
        try {
            JarEntry e = f.getJarEntry(name + ".class");
            if (e == null) return null;
            ClassNode c = new ClassNode(); new ClassReader(f.getInputStream(e)).accept(c, 0); return c;
        } finally { f.close(); }
    }
    static String strip(String s, String prefix) {
        if (s == null || prefix.isEmpty()) return s;
        return s.replace("L" + prefix, "L").replaceAll("^" + prefix, "");
    }
    static String text(ClassNode c, String method, String prefix) {
        StringBuilder out = new StringBuilder();
        MethodNode found = null;
        for (MethodNode m : c.methods) if (m.name.equals(method)) found = m;
        if (found == null) return null;
        Map<LabelNode, Integer> labels = new HashMap<LabelNode, Integer>();
        for (AbstractInsnNode i : found.instructions.toArray()) if (i instanceof LabelNode) labels.put((LabelNode) i, labels.size());
        out.append(strip(found.desc, prefix)).append('\n');
        for (AbstractInsnNode i : found.instructions.toArray()) {
            if (i instanceof LineNumberNode || i instanceof FrameNode) continue;
            if (i instanceof LabelNode) { out.append("L").append(labels.get(i)).append('\n'); continue; }
            out.append(i.getOpcode());
            if (i instanceof MethodInsnNode) { MethodInsnNode m = (MethodInsnNode) i; out.append(' ').append(strip(m.owner, prefix)).append('.').append(m.name).append(strip(m.desc, prefix)); }
            else if (i instanceof FieldInsnNode) { FieldInsnNode m = (FieldInsnNode) i; out.append(' ').append(strip(m.owner, prefix)).append('.').append(m.name).append(strip(m.desc, prefix)); }
            else if (i instanceof TypeInsnNode) out.append(' ').append(strip(((TypeInsnNode) i).desc, prefix));
            else if (i instanceof VarInsnNode) out.append(' ').append(((VarInsnNode) i).var);
            else if (i instanceof IntInsnNode) out.append(' ').append(((IntInsnNode) i).operand);
            else if (i instanceof LdcInsnNode) out.append(' ').append(((LdcInsnNode) i).cst);
            else if (i instanceof JumpInsnNode) out.append(" L").append(labels.get(((JumpInsnNode) i).label));
            else if (i instanceof IincInsnNode) out.append(' ').append(((IincInsnNode) i).var).append(' ').append(((IincInsnNode) i).incr);
            out.append('\n');
        }
        for (TryCatchBlockNode t : found.tryCatchBlocks) out.append("try L").append(labels.get(t.start)).append(" L").append(labels.get(t.end)).append(" L").append(labels.get(t.handler)).append(' ').append(t.type).append('\n');
        return out.toString();
    }
    static void require(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); System.out.println("PASS " + message); }

    public static void main(String[] a) throws Exception {
        File x1 = new File(a[0]), x3 = new File(a[1]), old = new File(a[2]);
        for (String[] m : METHODS) {
            String want = text(load(x1, m[0]), m[1], "");
            require(want != null, "x1 has " + m[1]);
            for (String slot : new String[]{"", "b/", "c/"}) {
                ClassNode c = load(x3, slot + m[0]);
                require(c != null && want.equals(text(c, m[1], slot)), "X3 slot " + (slot.isEmpty() ? "1" : slot) + " " + (m[0].startsWith("Hung") ? m[0] : "route") + "." + m[1] + " equals x1");
            }
        }
        String oldFog = text(load(old, "HungBySonModUpLv"), "fogTick", "");
        require(!oldFog.equals(text(load(x1, "HungBySonModUpLv"), "fogTick", "")) && oldFog.contains("\n16 16\n"),
                "Negative control: previous X3 fogTick differs (still sends Mrs Oda row 16)");
        require(text(load(old, route()), "codexFogHold", "") == null, "Negative control: previous X3 route has no Làng Phủ Sương hold");
        System.out.println("HungFogRouteX3Parity PASS checks=" + checks);
    }
}
