import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Read-only inspection of item lock strings and their bytecode references. */
public class ItemLockInspect {
    static String shortName(String s) { return s.replaceAll("i{20,}", "~"); }
    static String show(AbstractInsnNode n) {
        String s = "" + n.getOpcode();
        if(n instanceof FieldInsnNode){FieldInsnNode x=(FieldInsnNode)n;s+=" FIELD "+x.owner+"."+x.name+" "+x.desc;}
        if(n instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)n;s+=" CALL "+x.owner+"."+x.name+x.desc;}
        if(n instanceof LdcInsnNode)s+=" LDC "+((LdcInsnNode)n).cst;
        if(n instanceof VarInsnNode)s+=" VAR "+((VarInsnNode)n).var;
        if(n instanceof IntInsnNode)s+=" INT "+((IntInsnNode)n).operand;
        if(n instanceof JumpInsnNode)s+=" JUMP";
        return shortName(s);
    }
    public static void main(String[] args)throws Exception {
        JarFile z=new JarFile(args[0]); List<ClassNode> cs=new ArrayList<ClassNode>();
        for(Enumeration<JarEntry> es=z.entries();es.hasMoreElements();){JarEntry e=es.nextElement();if(!e.getName().endsWith(".class"))continue;ClassNode c=new ClassNode();new ClassReader(z.getInputStream(e)).accept(c,0);cs.add(c);}
        if(args.length>2 && args[1].equals("--strings")) {
            for(ClassNode c:cs)for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof LdcInsnNode){Object v=((LdcInsnNode)n).cst;if(v instanceof String && ((String)v).matches(args[2]))System.out.println(shortName(c.name)+" "+shortName(m.name+m.desc)+" STRING "+v);}
            z.close();return;
        }
        Set<String> fields=new HashSet<String>();
        for(ClassNode c:cs)for(MethodNode m:c.methods){AbstractInsnNode[] ns=m.instructions.toArray();for(int i=0;i<ns.length;i++)if(ns[i] instanceof LdcInsnNode){Object v=((LdcInsnNode)ns[i]).cst;if(v instanceof String && ((String)v).matches("(?is).*(khóa|khoá).*")){System.out.println("STRING "+shortName(c.name)+" "+m.name+m.desc);for(int j=Math.max(0,i-2);j<Math.min(ns.length,i+5);j++){System.out.println(j+" "+show(ns[j]));if(ns[j] instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)ns[j];fields.add(f.owner+"."+f.name);}}}}}
        for(ClassNode c:cs)for(MethodNode m:c.methods){AbstractInsnNode[] ns=m.instructions.toArray();Set<Integer> hits=new TreeSet<Integer>();for(int i=0;i<ns.length;i++){AbstractInsnNode n=ns[i];if(n instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)n;if(args.length>1?shortName(f.owner+"."+f.name).equals(args[1]):fields.contains(f.owner+"."+f.name)){for(int j=Math.max(0,i-14);j<Math.min(ns.length,i+18);j++)hits.add(j);}}}if(args.length>2&&shortName(c.name).equals(args[2])&&(args.length<4||shortName(m.name+m.desc).equals(args[3])))for(int i=0;i<ns.length;i++)hits.add(i);if(!hits.isEmpty()){System.out.println("METHOD "+shortName(c.name)+" "+shortName(m.name+m.desc));for(int i:hits)System.out.println(i+" "+show(ns[i]));}}
        z.close();
    }
}
