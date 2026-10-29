import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public class TempoScan {
  static int intValue(AbstractInsnNode n) {
    if (n.getOpcode() >= Opcodes.ICONST_M1 && n.getOpcode() <= Opcodes.ICONST_5) return n.getOpcode()-Opcodes.ICONST_0;
    if (n instanceof IntInsnNode) return ((IntInsnNode)n).operand;
    if (n instanceof LdcInsnNode && ((LdcInsnNode)n).cst instanceof Integer) return ((Integer)((LdcInsnNode)n).cst).intValue();
    return Integer.MIN_VALUE;
  }
  public static void main(String[] args) throws Exception {
    String mode=args.length>1?args[1]:"strings";
    try (JarFile jar=new JarFile(args[0])) {
      Enumeration<JarEntry> es=jar.entries();
      while (es.hasMoreElements()) {
        JarEntry e=es.nextElement(); if(!e.getName().endsWith(".class"))continue;
        ClassNode c=new ClassNode(Opcodes.ASM9);
        try(InputStream in=jar.getInputStream(e)){new ClassReader(in).accept(c,ClassReader.SKIP_FRAMES);}
        for(MethodNode m:c.methods) {
          List<String> strs=new ArrayList<String>(); List<String> refs=new ArrayList<String>(); Set<Integer> nums=new TreeSet<Integer>();
          for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) {
            if(n instanceof LdcInsnNode && ((LdcInsnNode)n).cst instanceof String) strs.add((String)((LdcInsnNode)n).cst);
            int iv=intValue(n); if(iv!=Integer.MIN_VALUE)nums.add(Integer.valueOf(iv));
            if(n instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)n;refs.add((n.getOpcode()==Opcodes.GETFIELD||n.getOpcode()==Opcodes.GETSTATIC?"R ":"W ")+f.owner+"."+f.name+":"+f.desc);}
            if(n instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)n;refs.add("M "+x.owner+"."+x.name+x.desc);}
          }
          if("summary".equals(mode)) { boolean match=false; for(String s:strs) if(s.toLowerCase().contains("fps") || s.toLowerCase().contains("game: [x") || s.matches("(?i)x[1-9]")) match=true; if(match) System.out.println(c.name+"."+m.name+m.desc+" nums="+nums+" strings="+strs+" refs="+refs);
          } else if("strings".equals(mode)) {
            for(String s:strs) if(s.matches("(?is).*(fps|speed|tempo|t[oố]c|nh[iị]p|frame|nhanh|m[uứ]c fps).*"))
              System.out.println(c.name+"."+m.name+m.desc+" STRING="+s);
          } else if("numeric".equals(mode)) {
            boolean interesting=false; for(Integer x:nums) if(x.intValue()==30||x.intValue()==34||x.intValue()==45||x.intValue()==60) interesting=true;
            if(interesting || (!strs.isEmpty() && nums.contains(1)&&nums.contains(2)&&nums.contains(3)))
              System.out.println(c.name+"."+m.name+m.desc+" nums="+nums+" strings="+strs+" refs="+refs);
          } else if("fpsfields".equals(mode)) { for(FieldNode f:(List<FieldNode>)c.fields) if("I".equals(f.desc) && f.name.endsWith("ililliliiiii")) System.out.println(c.name+"."+f.name+":"+f.desc+" access="+f.access);
          } else if("field".equals(mode)) {
            String q=args[2]+"."+args[3]+":"; List<String> hits=new ArrayList<String>();
            for(String r:refs) if((r.startsWith("R ")||r.startsWith("W ")) && r.contains(q)) hits.add(r);
            if(!hits.isEmpty()) System.out.println(c.name+"."+m.name+m.desc+" nums="+nums+" strings="+strs+" target="+hits);
          } else if("class".equals(mode)) {
            if(c.name.equals(args[2])) System.out.println(c.name+"."+m.name+m.desc+" nums="+nums+" strings="+strs+" refs="+refs);
          }
        }
      }
    }
  }
}
