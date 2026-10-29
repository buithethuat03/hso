import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Read-only inventory of binary boundaries relevant to an isolated x3 suite. */
public final class X3Inspect {
    public static void main(String[] args) throws Exception {
        JarFile jar = new JarFile(args[0]);
        int index = 0;
        for (Enumeration<JarEntry> es = jar.entries(); es.hasMoreElements();) {
            JarEntry e = es.nextElement();
            if (!e.getName().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(jar.getInputStream(e)).accept(cn, 0);
            String label = cn.name.length() > 80 ? "class#" + index : cn.name;
            index++;
            for (MethodNode m : cn.methods) {
                for (AbstractInsnNode n : m.instructions.toArray()) {
                    if (!(n instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode)n;
                    if (call.owner.equals("javax/microedition/rms/RecordStore")
                            || call.name.equals("getResourceAsStream")
                            || call.name.equals("forName")
                            || (call.owner.equals("javax/microedition/lcdui/Image") && call.desc.startsWith("(Ljava/lang/String;"))) {
                        System.out.println(label + " " + m.name + m.desc + " -> " + call.owner + "." + call.name + call.desc);
                        AbstractInsnNode prev = n.getPrevious();
                        for (int i = 0; prev != null && i < 5; i++, prev = prev.getPrevious()) {
                            if (prev instanceof LdcInsnNode) System.out.println("  ldc=" + ((LdcInsnNode)prev).cst);
                            else if (prev instanceof VarInsnNode) System.out.println("  var=" + prev.getOpcode() + ":" + ((VarInsnNode)prev).var);
                            else if (prev instanceof MethodInsnNode) System.out.println("  method=" + ((MethodInsnNode)prev).name + ((MethodInsnNode)prev).desc);
                            else System.out.println("  op=" + prev.getOpcode());
                        }
                    }
                }
            }
        }
        jar.close();
    }
}
