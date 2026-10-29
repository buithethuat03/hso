package luna.tools;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public class CastAuditInspector {
 static Map<String,String> names=new LinkedHashMap<String,String>();
 static String name(String x){if(x.length()<40)return x; if(!names.containsKey(x))names.put(x,"N"+names.size());return names.get(x);}
 static int pos(MethodNode m, AbstractInsnNode q){int n=0;for(AbstractInsnNode i:m.instructions.toArray()){if(i==q){while(i!=null&&i.getOpcode()<0)i=i.getNext();q=i;} if(i==q)return n;if(i.getOpcode()>=0)n++;}return n;}
 static void dump(ClassNode c){System.out.println("CLASS "+name(c.name));for(FieldNode f:c.fields)System.out.println("FIELD "+name(f.name)+" "+f.desc);
 for(MethodNode m:c.methods){System.out.println("METHOD "+name(m.name)+m.desc);int n=0;for(AbstractInsnNode i:m.instructions.toArray()){if(i.getOpcode()<0)continue;String s="";if(i instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)i;s=name(f.owner)+"."+name(f.name)+" "+f.desc;}else if(i instanceof MethodInsnNode){MethodInsnNode f=(MethodInsnNode)i;s=name(f.owner)+"."+name(f.name)+f.desc;}else if(i instanceof JumpInsnNode)s="->"+pos(m,((JumpInsnNode)i).label);else if(i instanceof TableSwitchInsnNode){TableSwitchInsnNode t=(TableSwitchInsnNode)i;s="default->"+pos(m,t.dflt);for(int a=0;a<t.labels.size();a++)s+=" "+(a+t.min)+"->"+pos(m,t.labels.get(a));}else if(i instanceof LookupSwitchInsnNode){LookupSwitchInsnNode t=(LookupSwitchInsnNode)i;s="default->"+pos(m,t.dflt);for(int a=0;a<t.labels.size();a++)s+=" "+t.keys.get(a)+"->"+pos(m,t.labels.get(a));}else if(i instanceof VarInsnNode)s=""+((VarInsnNode)i).var;else if(i instanceof IntInsnNode)s=""+((IntInsnNode)i).operand;else if(i instanceof LdcInsnNode)s=""+((LdcInsnNode)i).cst;System.out.println(n+++": "+i.getOpcode()+" "+s);}}
 }
 public static void main(String[]args)throws Exception{JarFile j=new JarFile(args[0]);if(args.length>1){dump(CombatCadencePatcher.readClass(CombatCadencePatcher.readAll(j.getInputStream(j.getJarEntry(args[1]+".class")))));for(Map.Entry<String,String>e:names.entrySet())System.out.println("MAP "+e.getValue()+"="+e.getKey());j.close();return;}ClassNode cast=CombatCadencePatcher.discover(j);dump(cast);String actor=null;for(MethodNode m:cast.methods){Type[] a=Type.getArgumentTypes(m.desc);if(a.length==4&&a[0].getSort()==Type.OBJECT){actor=a[0].getInternalName();break;}}
 for(Enumeration<JarEntry> e=j.entries();e.hasMoreElements();){JarEntry z=e.nextElement();if(!z.getName().endsWith(".class"))continue;ClassNode c=CombatCadencePatcher.readClass(CombatCadencePatcher.readAll(j.getInputStream(z)));boolean match=false;for(MethodNode m:c.methods)if(m.desc.equals("(L"+actor+";)V"))for(AbstractInsnNode i:m.instructions.toArray())if(i.getOpcode()==170||i.getOpcode()==171)match=true;if(match)dump(c);}
 for(Map.Entry<String,String>e:names.entrySet())System.out.println("MAP "+e.getValue()+"="+e.getKey());j.close();}
}
