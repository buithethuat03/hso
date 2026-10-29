import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.File;

/** Read only comparison of the supplied multiplexer template and current game. */
public final class X3TemplateInspect {
    public static void main(String[] args) throws Exception {
        Map<String,byte[]> source=HsoChildX3Packer.readJar(new File(args[0]));
        Map<String,byte[]> template=HsoChildX3Packer.readJar(new File(args[1]));
        Set<String> calls=new TreeSet<String>(); int clones=0,clears=0;
        for (String name:source.keySet()) {
            if (!name.endsWith(".class")) continue;
            ClassNode s=new ClassNode(); new ClassReader(source.get(name)).accept(s,0);
            if (!template.containsKey(name)) continue;
            ClassNode t=new ClassNode(); new ClassReader(template.get(name)).accept(t,0);
            String label=s.name.length()>80 ? "game#"+name.hashCode() : s.name;
            if (!s.superName.equals(t.superName)) System.out.println(label+" superclass: "+s.superName+" -> "+t.superName);
            for (MethodNode m:t.methods) {
                if (m.name.equals("cinitclone")) clones++;
                if (m.name.equals("clears")) clears++;
                if (m.name.equals("PAINT") || m.name.equals("leyPressed") || m.name.equals("leyReleased")) System.out.println(label+" callback: "+m.name+m.desc);
                for (AbstractInsnNode n:m.instructions.toArray()) if (n instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)n;
                    if (call.owner.startsWith("lib/")) calls.add(call.owner+"."+call.name+call.desc);
                }
            }
        }
        System.out.println("cinitclone="+clones+" clears="+clears);
        for (String call:calls) System.out.println(call);
        for (String path:template.keySet()) if (path.startsWith("lib/") && path.endsWith(".class")) {
            ClassNode t=new ClassNode(); new ClassReader(template.get(path)).accept(t,0);
            if (!t.name.equals("lib/Canvas") && !t.name.equals("lib/MIDlet") && !t.name.equals("lib/Stack")) continue;
            for (MethodNode m:t.methods) if (m.name.equals("<clinit>") || m.name.equals("keyPressed") || t.name.equals("lib/Stack")) {
                System.out.println(t.name+" "+m.name+m.desc);
                for (AbstractInsnNode n:m.instructions.toArray()) {
                    if (n instanceof LdcInsnNode) System.out.println("  ldc "+((LdcInsnNode)n).cst);
                    else if (n instanceof IntInsnNode) System.out.println("  int "+n.getOpcode()+":"+((IntInsnNode)n).operand);
                    else if (n instanceof MethodInsnNode) {MethodInsnNode c=(MethodInsnNode)n; System.out.println("  call "+c.owner+"."+c.name+c.desc);}
                }
            }
        }
    }
}
