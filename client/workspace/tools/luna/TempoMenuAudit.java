import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Narrow menu topology audit without launching network or account state. */
public final class TempoMenuAudit {
    static ClassNode read(JarFile jar, String name) throws Exception {
        InputStream in = jar.getInputStream(jar.getJarEntry(name + ".class"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192]; for (int n; (n = in.read(b)) >= 0;) out.write(b, 0, n); in.close();
        ClassNode c = new ClassNode(); new ClassReader(out.toByteArray()).accept(c, 0); return c;
    }
    static String display(AbstractInsnNode i) {
        if (i instanceof LdcInsnNode) return i.getOpcode() + " ldc=" + ((LdcInsnNode)i).cst;
        if (i instanceof IntInsnNode) return i.getOpcode() + " int=" + ((IntInsnNode)i).operand;
        if (i instanceof MethodInsnNode) { MethodInsnNode m = (MethodInsnNode)i; return i.getOpcode() + " call=" + m.name.substring(Math.max(0,m.name.length()-15)) + m.desc; }
        if (i instanceof FieldInsnNode) { FieldInsnNode f = (FieldInsnNode)i; return i.getOpcode() + " field=" + f.name.substring(Math.max(0,f.name.length()-15)) + " " + f.desc; }
        if (i instanceof VarInsnNode) return i.getOpcode() + " var=" + ((VarInsnNode)i).var;
        return "" + i.getOpcode();
    }
    public static void main(String[] args) throws Exception {
        Properties p = new Properties(); InputStream in = new FileInputStream(args[2]); p.load(in); in.close();
        JarFile ref = new JarFile(args[0]), hung = new JarFile(args[1]);
        ClassNode engine = read(ref, p.getProperty("reference.speed.owner"));
        String option = p.getProperty("reference.menu.optionsBuilder").replace("()V", "");
        for (MethodNode m : engine.methods) if (m.name.equals(option) && m.desc.equals("()V")) {
            List<AbstractInsnNode> code = new ArrayList<AbstractInsnNode>();
            for (AbstractInsnNode i : m.instructions.toArray()) if (i.getOpcode() >= 0) code.add(i);
            System.out.println("REFERENCE_MENU_LITERALS");
            for (AbstractInsnNode i : code) if (i instanceof LdcInsnNode) System.out.println(display(i));
            System.out.println("REFERENCE_MENU_DISPLAY_TAIL");
            for (int i=Math.max(0,code.size()-16);i<code.size();i++) System.out.println(display(code.get(i)));
        }
        String menuOwner=p.getProperty("candidate.menu.owner");
        String action=p.getProperty("candidate.menu.action"); action=action.substring(0,action.indexOf('('));
        for (Enumeration<JarEntry> e=hung.entries();e.hasMoreElements();) {
            JarEntry z=e.nextElement();if(!z.getName().endsWith(".class"))continue;
            ClassNode c=read(hung,z.getName().substring(0,z.getName().length()-6));
            for(MethodNode m:c.methods){List<AbstractInsnNode> code=new ArrayList<AbstractInsnNode>();for(AbstractInsnNode i:m.instructions.toArray())if(i.getOpcode()>=0)code.add(i);
                for(int k=0;k<code.size();k++)if(code.get(k) instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)code.get(k);if(!call.owner.equals(menuOwner)||!call.name.equals(action)||!call.desc.equals("(II)Z"))continue;
                    System.out.println("CALLER="+c.name+"."+m.name+m.desc);
                    for(int j=Math.max(0,k-15);j<Math.min(code.size(),k+5);j++)System.out.println(display(code.get(j)));
                }
            }
        }
        ref.close();hung.close();
    }
}
