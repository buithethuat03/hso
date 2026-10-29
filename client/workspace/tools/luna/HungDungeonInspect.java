import java.io.*;
import java.util.*;
import org.objectweb.asm.tree.*;
/** Read-only discovery for the pinned Hung dungeon integration. */
public final class HungDungeonInspect {
 public static void main(String[] args)throws Exception {
  Map<String,byte[]> jar=HungBySonExpInfoPack.read(new File(args[0]));
  String mods=HungBySonModUpLvPatcher.n("lillllll");
  for(Map.Entry<String,byte[]> e:jar.entrySet()) {
   if(!e.getKey().endsWith(".class"))continue;
   ClassNode c=HungBySonExpInfoPack.node(e.getValue());
   int texts=0;for(FieldNode f:c.fields)if(f.desc.equals("Ljava/lang/String;")&&(f.access&8)==0)texts++;
   if(texts>=3 && texts<9){System.out.print("TEXTCLASS "+c.name+" "+texts+" ");for(MethodNode m:c.methods)if(m.desc.contains("Ljava/lang/String;"))System.out.print(m.name+m.desc+" ");System.out.println();}
   for(MethodNode m:c.methods) for(AbstractInsnNode x:m.instructions) if(x instanceof MethodInsnNode) {
    MethodInsnNode call=(MethodInsnNode)x;
    if(c.name.equals(HungBySonModUpLvPatcher.n("lllllll")) && call.desc.contains("Ljava/lang/String;") && call.desc.endsWith(")V"))System.out.println("NOTICECALL "+call.owner+"."+call.name+call.desc);
    if(call.owner.equals(mods)&&call.name.equals(HungBySonModUpLvPatcher.n("lilll"))
      || call.owner.equals("HSOAutoTradeSupport")&&call.name.toLowerCase().contains("info"))
     System.out.println("CALL "+c.name+"."+m.name+m.desc+" -> "+call.owner+"."+call.name+call.desc);
   }
  }
 }
}
