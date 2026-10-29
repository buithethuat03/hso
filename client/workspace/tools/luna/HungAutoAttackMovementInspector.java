import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.tree.*;

/** Read-only native input/path audit. */
public final class HungAutoAttackMovementInspector {
    public static void main(String[] args)throws Exception{
        JarFile jar=new JarFile(args[0]);try{for(Enumeration<JarEntry> all=jar.entries();all.hasMoreElements();){JarEntry e=all.nextElement();if(!e.getName().endsWith(".class"))continue;ClassNode c=HungAutoAttackNativeBuilder.parse(HungAutoAttackNativeBuilder.read(jar.getInputStream(e)));for(MethodNode m:c.methods){boolean match=false;for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)i;if(f.owner.equals(HungBySonAutoAttackPatcher.MAIN)&&f.desc.equals("[Z")&&(f.name.equals(HungBySonAutoAttackPatcher.n("liiilliilli"))||f.name.equals(HungBySonAutoAttackPatcher.n("liiilliliii"))))match=true;}if(!match)continue;System.out.println("METHOD "+c.name+"."+m.name+m.desc);List<AbstractInsnNode> instructions=HungAutoAttackNativeBuilder.real(m);for(int k=0;k<instructions.size();k++){AbstractInsnNode i=instructions.get(k);String detail="";if(i instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)i;detail=f.owner+"."+f.name+f.desc;}else if(i instanceof MethodInsnNode){MethodInsnNode f=(MethodInsnNode)i;detail=f.owner+"."+f.name+f.desc;}else if(i instanceof VarInsnNode)detail=""+((VarInsnNode)i).var;else if(i instanceof IntInsnNode)detail=""+((IntInsnNode)i).operand;else if(i instanceof JumpInsnNode){AbstractInsnNode next=((JumpInsnNode)i).label;while(next!=null&&next.getOpcode()<0)next=next.getNext();detail="→"+instructions.indexOf(next);}else if(i instanceof LdcInsnNode)detail=""+((LdcInsnNode)i).cst;System.out.println(k+" "+i.getOpcode()+" "+detail);}}}}finally{jar.close();}
    }
}
