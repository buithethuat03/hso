import java.util.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.*;
/** Read-only discovery of dungeon strings, including native XOR-encoded labels. */
public class HungDungeonAudit {
 public static void main(String[] args) throws Exception {
  Map<String,HungBySonFullOfflinePatcher.Entry> entries=HungBySonFullOfflinePatcher.read(new java.io.File(args[0]));
  for(HungBySonFullOfflinePatcher.Entry e:entries.values()) {
   if(!e.info.getName().endsWith(".class")) continue;
   ClassNode c=HungBySonFullOfflinePatcher.parse(e.bytes);
   for(MethodNode m:c.methods) {
    Set<String> strings=HungBySonFullOfflinePatcher.strings(m);
    boolean match=false;
    for(String s:strings) if(s.toLowerCase().matches("(?s).*(phó bản|pho ban|horae|ngã tư|số lượt|lượt free).*")) match=true;
    if(args.length>1 && args[1].equals("coords")) {match=strings.contains(":");}
    if(match) {System.out.println("METHOD "+c.name+" "+m.name+" "+m.desc);for(String s:strings)System.out.println("  "+s);}
   }
   if(c.name.equals(HungBySonFullOfflinePatcher.n("lliilili")) && args.length>1 && !args[1].equals("coords")) {
    for(MethodNode m:c.methods) {
     AbstractInsnNode start=null; byte[] array=null;
     for(AbstractInsnNode i=m.instructions.getFirst();i!=null;) {
      AbstractInsnNode next=i.getNext();
      if(i.getOpcode()==Opcodes.NEWARRAY && ((IntInsnNode)i).operand==Opcodes.T_BYTE) {
       start=HungBySonFullOfflinePatcher.previous(i); Integer len=HungBySonFullOfflinePatcher.integer(start); array=len==null?null:new byte[len];
      } else if(i.getOpcode()==Opcodes.BASTORE && array!=null) {
       Integer index=HungBySonFullOfflinePatcher.integer(HungBySonFullOfflinePatcher.previous(HungBySonFullOfflinePatcher.previous(i))), value=HungBySonFullOfflinePatcher.integer(HungBySonFullOfflinePatcher.previous(i));
       if(index!=null && value!=null) array[index]=(byte)(int)value;
      } else if(i instanceof MethodInsnNode && ((MethodInsnNode)i).owner.equals("key") && ((MethodInsnNode)i).name.equals("d") && array!=null) {
       for(int k=0;k<array.length;k++) array[k]^=42;
       m.instructions.insertBefore(start,new LdcInsnNode(new String(array,"UTF-8")));
       while(start!=i){AbstractInsnNode n=start.getNext();m.instructions.remove(start);start=n;}
       m.instructions.remove(i);array=null;start=null;
      }
      i=next;
     }
    }
    ClassWriter w=new ClassWriter(0);c.accept(w);java.nio.file.Files.write(java.nio.file.Paths.get(args[1],c.name+".class"),w.toByteArray());
   }
  }
 }
}
