import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Narrow bytecode inventory for the MAX endpoint port; no decompilation. */
public final class MaxVariantInspect {
    public static void main(String[] args) throws Exception {
        JarFile jar = new JarFile(args[0]);
        System.out.println("MANIFEST " + jar.getManifest().getMainAttributes().entrySet());
        for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
            JarEntry entry = e.nextElement();
            if (!entry.getName().endsWith(".class")) continue;
            ClassNode c = new ClassNode(); new ClassReader(jar.getInputStream(entry)).accept(c, 0);
            for (MethodNode m : c.methods) {
                List<String> anchors = new ArrayList<String>();
                boolean login = m.desc.contains("Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;");
                for (AbstractInsnNode n : m.instructions.toArray()) {
                    if (n instanceof LdcInsnNode && ((LdcInsnNode)n).cst instanceof String) {
                        String s = (String)((LdcInsnNode)n).cst;
                        if (s.matches(".*(?:HSO|MAX|Max|CHIBI|listServer|hs1\\.teamobi|103\\.77|9\\.9\\.9).*")) anchors.add(s);
                        if (s.contains("socket:")) anchors.add(s);
                    }
                    if (n instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode)n;
                        if (call.owner.equals("javax/microedition/rms/RecordStore")) anchors.add("RMS " + call.name + call.desc);
                        if (call.owner.equals("javax/microedition/io/Connector")) anchors.add("CONNECTOR");
                        if (call.desc.equals("(Ljava/lang/String;I)V") && !call.name.equals("<init>") && !call.owner.startsWith("java/")) anchors.add("DIAL " + call.owner + "." + call.name);
                        if (call.desc.contains("Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;")) anchors.add("LOGIN CALL");
                    }
                }
                if (login || !anchors.isEmpty()) {
                    System.out.println("METHOD " + c.name + " " + m.name + m.desc + " " + anchors);
                    if (args.length > 1) for (AbstractInsnNode n : m.instructions.toArray()) {
                        if (n instanceof LdcInsnNode) System.out.println("  LDC " + ((LdcInsnNode)n).cst);
                        else if (n instanceof MethodInsnNode) {MethodInsnNode x=(MethodInsnNode)n;System.out.println("  CALL " + x.getOpcode()+" "+shorten(x.owner)+"."+shorten(x.name)+x.desc);}
                        else if (n instanceof FieldInsnNode) {FieldInsnNode x=(FieldInsnNode)n;System.out.println("  FIELD " + x.getOpcode()+" "+shorten(x.owner)+"."+shorten(x.name)+x.desc);}
                        else if (n instanceof VarInsnNode) System.out.println("  VAR "+n.getOpcode()+" "+((VarInsnNode)n).var);
                        else if (n instanceof IntInsnNode) System.out.println("  INT "+n.getOpcode()+" "+((IntInsnNode)n).operand);
                        else if(n.getOpcode()>=0) System.out.println("  OP "+n.getOpcode());
                    }
                }
            }
        }
        jar.close();
    }
    private static String shorten(String s) { return s.length() > 60 ? s.substring(0,12) + "..." + s.substring(s.length()-12) : s; }
}
