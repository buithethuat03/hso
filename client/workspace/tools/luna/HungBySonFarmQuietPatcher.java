import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Pins the local Farm-special avatar to idle, including receive-side visual casts. */
public final class HungBySonFarmQuietPatcher implements Opcodes {
    static final String INPUT="826E8FE3BCE4D08541A206BDA7C91DCC01CF06BFC6B956D744BD1F36FBC26EA4";
    static final String B="luna/generated/HungAutoAttackAccess";
    static Properties p=new Properties();static String P,E,C,A;static FieldInsnNode singleton;
    static Map<String,byte[]> before,out;static Map<String,ClassNode> nodes=new LinkedHashMap<String,ClassNode>();
    static Map<MethodNode,String> original=new IdentityHashMap<MethodNode,String>();
    static Map<MethodNode,List<AbstractInsnNode>> inserts=new IdentityHashMap<MethodNode,List<AbstractInsnNode>>();
    static void check(boolean v,String s){if(!v)throw new IllegalStateException(s);}
    static String req(String k){String s=p.getProperty(k);check(s!=null,"Missing "+k);return s;}
    static ClassNode node(String n){ClassNode c=nodes.get(n);if(c==null){c=HungBySonExpInfoPack.node(before.get(n+".class"));nodes.put(n,c);}return c;}
    static MethodNode method(String owner,String name,String desc){for(MethodNode m:node(owner).methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;throw new IllegalStateException("Missing "+owner+"."+name+desc);}
    static void op(InsnList l,int n){l.add(new InsnNode(n));}static void v(InsnList l,int op,int i){l.add(new VarInsnNode(op,i));}
    static void f(InsnList l,int op,String o,String n,String d){l.add(new FieldInsnNode(op,o,n,d));}
    static void call(InsnList l,int op,String o,String n,String d){l.add(new MethodInsnNode(op,o,n,d,false));}
    static void bridge(InsnList l,String n,String d){call(l,INVOKESTATIC,B,n,d);}
    static void player(InsnList l){f(l,GETSTATIC,singleton.owner,singleton.name,singleton.desc);}
    static void insert(MethodNode m,AbstractInsnNode anchor,InsnList l){if(!original.containsKey(m)){original.put(m,HungBySonExpInfoPack.canonical(m));inserts.put(m,new ArrayList<AbstractInsnNode>());}inserts.get(m).addAll(Arrays.asList(l.toArray()));if(anchor==null)m.instructions.insert(l);else m.instructions.insertBefore(anchor,l);}
    static MethodNode add(String n,String d){MethodNode m=new MethodNode(ACC_PUBLIC|ACC_STATIC,n,d,null,null);node(B).methods.add(m);return m;}
    static void idle(InsnList l,int actor){v(l,ALOAD,actor);bridge(l,"quietLocalAvatar","(L"+E+";)Z");op(l,POP);}
    static void guard(MethodNode m,int actor,boolean contextExempt){InsnList l=new InsnList();LabelNode normal=new LabelNode();v(l,ALOAD,actor);bridge(l,"isQuietLocalAvatar","(L"+E+";)Z");l.add(new JumpInsnNode(IFEQ,normal));if(contextExempt){bridge(l,"specialContext","()Z");l.add(new JumpInsnNode(IFNE,normal));}idle(l,actor);op(l,RETURN);l.add(normal);insert(m,null,l);}
    static void patch(){
        P=req("native.player.owner");E=req("entity.owner");C=req("cast.owner");A=req("request.animation.owner");
        for(AbstractInsnNode n:method(B,"player","()Ljava/lang/Object;").instructions)if(n instanceof FieldInsnNode&&n.getOpcode()==GETSTATIC)singleton=(FieldInsnNode)n;
        check(singleton!=null,"Local singleton");
        MethodNode is=add("isQuietLocalAvatar","(L"+E+";)Z");InsnList l=is.instructions;LabelNode no=new LabelNode();v(l,ALOAD,0);l.add(new JumpInsnNode(IFNULL,no));v(l,ALOAD,0);player(l);l.add(new JumpInsnNode(IF_ACMPNE,no));bridge(l,"farmSpecialEnabled","()Z");l.add(new JumpInsnNode(IFEQ,no));v(l,ALOAD,0);f(l,GETFIELD,E,"bs","I");l.add(new JumpInsnNode(IFLE,no));v(l,ALOAD,0);f(l,GETFIELD,E,"cG","I");op(l,ICONST_4);l.add(new JumpInsnNode(IF_ICMPEQ,no));op(l,ICONST_1);op(l,IRETURN);l.add(no);op(l,ICONST_0);op(l,IRETURN);
        MethodNode reset=add("quietLocalAvatar","(L"+E+";)Z");l=reset.instructions;LabelNode done=new LabelNode(),noAnimator=new LabelNode();v(l,ALOAD,0);bridge(l,"isQuietLocalAvatar","(L"+E+";)Z");l.add(new JumpInsnNode(IFEQ,done));
        for(String field:new String[]{"cG","cQ","bc","bd"}){v(l,ALOAD,0);op(l,ICONST_0);f(l,PUTFIELD,E,field,"I");}
        for(String field:new String[]{"cA","cR"}){v(l,ALOAD,0);op(l,ICONST_M1);f(l,PUTFIELD,E,field,"I");}
        for(String[] pair:new String[][]{{"bg","aZ"},{"bh","ba"}}){v(l,ALOAD,0);v(l,ALOAD,0);f(l,GETFIELD,E,pair[1],"I");f(l,PUTFIELD,E,pair[0],"I");}
        v(l,ALOAD,0);op(l,ACONST_NULL);f(l,PUTFIELD,E,"cO","[S");v(l,ALOAD,0);op(l,ACONST_NULL);f(l,PUTFIELD,E,"cS","Let;");
        MethodInsnNode initAnimator=null;for(AbstractInsnNode n:method(C,req("cast.helper.method"),"(I)V").instructions)if(n instanceof MethodInsnNode){MethodInsnNode q=(MethodInsnNode)n;if(q.owner.equals(A)&&q.desc.equals("(I)V"))initAnimator=q;}
        check(initAnimator!=null,"Native animator initialization");v(l,ALOAD,0);f(l,GETFIELD,E,"dh","L"+A+";");l.add(new JumpInsnNode(IFNULL,noAnimator));v(l,ALOAD,0);f(l,GETFIELD,E,"dh","L"+A+";");op(l,ICONST_0);call(l,INVOKEVIRTUAL,A,initAnimator.name,initAnimator.desc);l.add(noAnimator);op(l,ICONST_1);op(l,IRETURN);l.add(done);op(l,ICONST_0);op(l,IRETURN);
        // Receive-side echoes are not tagged as a Farm initiation. Skip only their local visual init.
        guard(method(C,req("cast.init.method"),req("cast.init.descriptor")),1,true);
        // A native fallback/buff path must not start a local avatar animation either.
        MethodNode helper=method(C,req("cast.helper.method"),"(I)V");l=new InsnList();LabelNode nativeHelper=new LabelNode();v(l,ALOAD,0);f(l,GETFIELD,C,req("cast.actor.field"),"L"+E+";");bridge(l,"quietLocalAvatar","(L"+E+";)Z");l.add(new JumpInsnNode(IFEQ,nativeHelper));v(l,ALOAD,0);bridge(l,"finishSpecialCast","(L"+C+";)V");op(l,RETURN);l.add(nativeHelper);insert(helper,null,l);
        guard(method(A,req("request.animation.method"),req("request.animation.descriptor")),1,false);
        MethodNode update=method(P,"codexLocalPlayerUpdate","()V");l=new InsnList();player(l);bridge(l,"quietLocalAvatar","(L"+E+";)Z");op(l,POP);insert(update,null,l);int returns=0;for(AbstractInsnNode n:update.instructions.toArray())if(n.getOpcode()==RETURN){l=new InsnList();player(l);bridge(l,"quietLocalAvatar","(L"+E+";)Z");op(l,POP);insert(update,n,l);returns++;}check(returns>0,"Player update exits");
    }
    public static void main(String[] args)throws Exception{
        check(args.length==5,"input support-unused output catalog report");File input=new File(args[0]),output=new File(args[2]);check(HungBySonExpInfoPack.hash(input).equals(INPUT),"Pinned Farm release");check(!output.exists(),"Fresh output");InputStream in=new FileInputStream("build/hung-farm-special-attack-20261023-a/farm-special-attack.properties");try{p.load(in);}finally{in.close();}before=HungBySonExpInfoPack.read(input);out=new LinkedHashMap<String,byte[]>(before);patch();
        Set<String> changed=new LinkedHashSet<String>();for(String name:new String[]{B,P,C,A}){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);node(name).accept(w);out.put(name+".class",w.toByteArray());changed.add(name+".class");}
        for(MethodNode m:original.keySet()){for(AbstractInsnNode n:inserts.get(m))m.instructions.remove(n);check(HungBySonExpInfoPack.canonical(m).equals(original.get(m)),"Exact instruction inverse "+m.name);}
        for(String name:new String[]{B,P,C,A}){ClassNode old=HungBySonExpInfoPack.node(before.get(name+".class"));for(MethodNode m:old.methods)check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(name,m.name,m.desc))),"Unrelated method "+m.name);check(old.fields.size()==node(name).fields.size(),"Fields unchanged");}
        int count=0;for(String n:before.keySet()){if(!changed.contains(n))check(Arrays.equals(before.get(n),out.get(n)),"Archive boundary "+n);if(n.endsWith(".class")){count++;check(HungBySonExpInfoPack.node(before.get(n)).version==HungBySonExpInfoPack.node(out.get(n)).version,"Version preserved");}}check(out.keySet().equals(before.keySet()),"Entry identity");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("quiet.farm.local","true");p.setProperty("changed.classes",String.join(",",changed));p.setProperty("added.classes","");p.setProperty("native.inverse","PASS");OutputStream cfg=new FileOutputStream(args[3]);try{p.store(cfg,"Local Farm-special idle visual guards; receiver payload preserved");}finally{cfg.close();}PrintWriter log=new PrintWriter(args[4],"UTF-8");log.println("PASS exact instruction inverse; four native classes; fields/resources/manifest and all other methods unchanged");log.println("CLASSES="+count);log.println("SHA256="+p.getProperty("output.sha256"));log.close();System.out.println("FARM_QUIET_PATCH=PASS SHA256="+p.getProperty("output.sha256"));
    }
}
