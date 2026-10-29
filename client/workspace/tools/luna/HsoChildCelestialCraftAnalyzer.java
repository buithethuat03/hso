import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
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
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Read-only bytecode report for the HSO CHILD celestial-crafting flow. */
public final class HsoChildCelestialCraftAnalyzer implements Opcodes {
    private HsoChildCelestialCraftAnalyzer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: analyzer <jar>");
        File input = new File(args[0]);
        List<ClassNode> classes = readClasses(input);
        for (ClassNode node : classes) {
            for (MethodNode method : node.methods) {
                boolean match = false;
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                        insn = insn.getNext()) {
                    if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof String)) continue;
                    String value = (String) ((LdcInsnNode) insn).cst;
                    String folded = fold(value);
                    if (folded.contains("chon") || folded.contains("vat pham")
                            || folded.contains("muon tao") || folded.contains("tinh tu")
                            || folded.contains("thanh cong hay that bai")) {
                        System.out.println("MATCH class=" + node.name + " method=" + method.name + method.desc
                                + " string=" + printable(value));
                        match = true;
                    }
                }
                if (match) dump(node, method);
            }
        }

        for (ClassNode node : classes) {
            MethodNode tick = findMethod(node, "hsoskyAutoTickGate", "()V");
            if (tick == null) continue;
            System.out.println("SCREEN class=" + node.name);
        }
    }

    private static String fold(String value) {
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT).replace('đ', 'd');
    }

    private static String printable(String value) {
        return value.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static void dump(ClassNode owner, MethodNode method) {
        System.out.println("BEGIN " + owner.name + "." + method.name + method.desc);
        int index = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            System.out.println(String.format("%04d %s", Integer.valueOf(index++), describe(insn)));
        }
        System.out.println("END " + owner.name + "." + method.name + method.desc);
    }

    private static String describe(AbstractInsnNode insn) {
        if (insn instanceof LabelNode) return "LABEL " + id((LabelNode) insn);
        if (insn.getOpcode() < 0) return insn.getClass().getSimpleName();
        String op = Integer.toString(insn.getOpcode());
        if (insn instanceof FieldInsnNode) {
            FieldInsnNode f = (FieldInsnNode) insn;
            return op + " FIELD " + f.owner + "." + f.name + " " + f.desc;
        }
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode m = (MethodInsnNode) insn;
            return op + " CALL " + m.owner + "." + m.name + m.desc;
        }
        if (insn instanceof LdcInsnNode) return op + " LDC " + printable(String.valueOf(((LdcInsnNode) insn).cst));
        if (insn instanceof IntInsnNode) return op + " INT " + ((IntInsnNode) insn).operand;
        if (insn instanceof VarInsnNode) return op + " VAR " + ((VarInsnNode) insn).var;
        if (insn instanceof TypeInsnNode) return op + " TYPE " + ((TypeInsnNode) insn).desc;
        if (insn instanceof JumpInsnNode) return op + " JUMP " + id(((JumpInsnNode) insn).label);
        if (insn instanceof IincInsnNode) {
            IincInsnNode i = (IincInsnNode) insn;
            return op + " IINC " + i.var + " " + i.incr;
        }
        if (insn instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode s = (TableSwitchInsnNode) insn;
            return op + " TABLESWITCH " + s.min + ".." + s.max + " default=" + id(s.dflt);
        }
        if (insn instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode s = (LookupSwitchInsnNode) insn;
            return op + " LOOKUPSWITCH keys=" + s.keys + " default=" + id(s.dflt);
        }
        if (insn instanceof InvokeDynamicInsnNode) return op + " INVOKEDYNAMIC " + ((InvokeDynamicInsnNode) insn).name;
        if (insn instanceof MultiANewArrayInsnNode) return op + " MULTIANEWARRAY " + ((MultiANewArrayInsnNode) insn).desc;
        return op + " " + insn.getClass().getSimpleName();
    }

    private static String id(LabelNode label) {
        return "L" + Integer.toHexString(System.identityHashCode(label));
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        return null;
    }

    private static List<ClassNode> readClasses(File input) throws IOException {
        List<ClassNode> result = new ArrayList<ClassNode>();
        JarFile jar = new JarFile(input);
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                InputStream stream = jar.getInputStream(entry);
                try {
                    ClassNode node = new ClassNode();
                    new ClassReader(readAll(stream)).accept(node, ClassReader.EXPAND_FRAMES);
                    result.add(node);
                } finally {
                    stream.close();
                }
            }
        } finally {
            jar.close();
        }
        return result;
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }
}
