import java.util.*;
import java.util.jar.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Read-only instruction audit for endpoint and login patch planning. */
public final class HungBySonChildLoginInspector {
    private static String shortName(String name) {
        return name.length() > 50 ? name.substring(0, 3) + "..." + name.substring(name.length() - 15) : name;
    }
    public static void main(String[] args) throws Exception {
        JarFile jar = new JarFile(args[0]);
        try {
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                JarEntry entry = e.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode c = new ClassNode();
                InputStream in = jar.getInputStream(entry);
                try { new ClassReader(in).accept(c, 0); } finally { in.close(); }
                for (MethodNode m : c.methods) {
                    AbstractInsnNode[] insns = m.instructions.toArray();
                    for (int i = 0; i < insns.length; i++) {
                        AbstractInsnNode insn = insns[i];
                        boolean hit = insn instanceof FieldInsnNode && ((FieldInsnNode)insn).owner.endsWith("ilillllii");
                        hit |= insn instanceof MethodInsnNode && ((MethodInsnNode)insn).desc.startsWith("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IB)");
                        hit |= insn instanceof LdcInsnNode && "221.121.0.242".equals(((LdcInsnNode)insn).cst);
                        if (!hit) continue;
                        System.out.println("SITE " + c.name + "." + m.name + m.desc + " @" + i);
                        for (int j = Math.max(0, i - 12); j < Math.min(insns.length, i + 8); j++) {
                            AbstractInsnNode n = insns[j];
                            String detail = "";
                            if (n instanceof FieldInsnNode) { FieldInsnNode f=(FieldInsnNode)n; detail=shortName(f.owner)+"."+shortName(f.name)+":"+f.desc; }
                            if (n instanceof MethodInsnNode) { MethodInsnNode f=(MethodInsnNode)n; detail=shortName(f.owner)+"."+shortName(f.name)+f.desc; }
                            if (n instanceof LdcInsnNode) detail=String.valueOf(((LdcInsnNode)n).cst);
                            if (n instanceof VarInsnNode) detail="local="+((VarInsnNode)n).var;
                            if (n instanceof IntInsnNode) detail="value="+((IntInsnNode)n).operand;
                            System.out.println(j+" opcode="+n.getOpcode()+" "+detail);
                        }
                    }
                }
            }
        } finally { jar.close(); }
    }
}
