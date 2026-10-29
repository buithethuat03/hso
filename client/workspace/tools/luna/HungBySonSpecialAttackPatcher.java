import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Adds only synchronous attack dispatch; native selection and serializers survive unchanged. */
public final class HungBySonSpecialAttackPatcher implements Opcodes {
    static final String INPUT="DA429E64940D87852F212854AF793C992D99DDF039DED0B85CAD1747FD950646";
    static final String B="luna/generated/HungAutoAttackAccess",R="luna/features/AutoAttackRuntime";
    static String P,E,C; static Properties p=new Properties();
    static Map<String,byte[]> before,out;
    static Map<String,ClassNode> nodes=new LinkedHashMap<String,ClassNode>();
    static Map<MethodNode,String> originals=new IdentityHashMap<MethodNode,String>();
    static Map<MethodNode,List<AbstractInsnNode>> inserts=new IdentityHashMap<MethodNode,List<AbstractInsnNode>>();
    static List<MethodNode> added=new ArrayList<MethodNode>();
    static FieldInsnNode singleton,castTarget;
    static void check(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    static ClassNode node(String name){ClassNode c=nodes.get(name);if(c==null){c=HungBySonExpInfoPack.node(before.get(name+".class"));nodes.put(name,c);}return c;}
    static MethodNode method(ClassNode c,String name,String desc){for(MethodNode m:c.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;throw new IllegalStateException("Missing "+c.name+"."+name+desc);}
    static void op(InsnList l,int n){l.add(new InsnNode(n));}
    static void v(InsnList l,int op,int i){l.add(new VarInsnNode(op,i));}
    static void f(InsnList l,int op,String o,String n,String d){l.add(new FieldInsnNode(op,o,n,d));}
    static void invoke(InsnList l,int op,String o,String n,String d){l.add(new MethodInsnNode(op,o,n,d,false));}
    static void rt(InsnList l,String n,String d){invoke(l,INVOKESTATIC,R,n,d);}
    static void player(InsnList l){f(l,GETSTATIC,singleton.owner,singleton.name,singleton.desc);}
    static void get(InsnList l,String name,String desc){v(l,ALOAD,0);f(l,GETFIELD,C,name,desc);}
    static void insert(MethodNode m,AbstractInsnNode anchor,InsnList l,boolean after){
        if(!originals.containsKey(m)){originals.put(m,HungBySonExpInfoPack.canonical(m));inserts.put(m,new ArrayList<AbstractInsnNode>());}
        inserts.get(m).addAll(Arrays.asList(l.toArray()));
        if(anchor==null)m.instructions.insert(l);else if(after)m.instructions.insert(anchor,l);else m.instructions.insertBefore(anchor,l);
    }
    static MethodInsnNode uniqueCall(MethodNode m,String owner,String desc){MethodInsnNode hit=null;for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode){MethodInsnNode q=(MethodInsnNode)n;if(q.owner.equals(owner)&&q.desc.equals(desc)){check(hit==null||hit.name.equals(q.name),"Ambiguous call "+desc);hit=q;}}check(hit!=null,"Missing call "+desc);return hit;}
    static void copyCall(InsnList l,MethodInsnNode q){invoke(l,q.getOpcode(),q.owner,q.name,q.desc);}
    static void copyField(InsnList l,FieldInsnNode q){f(l,q.getOpcode(),q.owner,q.name,q.desc);}
    static int integer(AbstractInsnNode n){if(n.getOpcode()>=ICONST_M1&&n.getOpcode()<=ICONST_5)return n.getOpcode()-ICONST_0;if(n instanceof IntInsnNode)return((IntInsnNode)n).operand;if(n instanceof LdcInsnNode&&((LdcInsnNode)n).cst instanceof Integer)return(Integer)((LdcInsnNode)n).cst;return Integer.MIN_VALUE;}
    static String required(String k){String s=p.getProperty(k);check(s!=null,"Missing catalog "+k);return s;}
    static void patch() {
        P=required("native.skill.owner");E=required("entity.owner");C=required("cast.owner");
        ClassNode bridge=node(B),cast=node(C),player=node(P);
        MethodNode getter=method(bridge,"player","()Ljava/lang/Object;");
        for(AbstractInsnNode n:getter.instructions)if(n instanceof FieldInsnNode&&n.getOpcode()==GETSTATIC)singleton=(FieldInsnNode)n;
        check(singleton!=null&&singleton.desc.equals("L"+P+";"),"Player singleton");
        MethodNode owned=method(cast,"codexOwnedRequest","()V");
        MethodInsnNode targets=uniqueCall(owned,P,"(IL"+E+";)Let;");
        MethodNode animation=method(cast,required("cast.sender.method"),"()V");
        MethodInsnNode cooldown=uniqueCall(animation,P,"(II)V");
        Map<Integer,MethodInsnNode> senders=new HashMap<Integer,MethodInsnNode>();
        for(AbstractInsnNode n:owned.instructions)if(n instanceof MethodInsnNode){MethodInsnNode q=(MethodInsnNode)n;if(q.desc.equals("(Let;B)V")){
            MethodNode serializer=method(node(q.owner),q.name,q.desc);Integer code=null;
            for(AbstractInsnNode i:serializer.instructions)if(integer(i)==6||integer(i)==9){code=integer(i);break;}
            check(code!=null,"Native attack opcode");senders.put(code,q);
        }}
        check(senders.size()==2,"Both native serializers");
        MethodInsnNode senderGetter=uniqueCall(owned,senders.get(9).owner,"()L"+senders.get(9).owner+";");
        MethodNode eligible=null;
        for(MethodNode m:player.methods)if(m.desc.equals("(II)Z")){check(eligible==null,"Unique skill eligibility");eligible=m;}
        check(eligible!=null,"Native skill eligibility");
        // Only controller-owned choices see the donor delay. Manual and Farm calls remain native.
        InsnList l=new InsnList();LabelNode normal=new LabelNode();rt(l,"specialActive","()Z");l.add(new JumpInsnNode(IFEQ,normal));v(l,ILOAD,1);rt(l,"specialSkillAllowed","(I)Z");l.add(new JumpInsnNode(IFNE,normal));op(l,ICONST_0);op(l,IRETURN);l.add(normal);insert(eligible,null,l,false);
        cast.fields.add(new FieldNode(ACC_PUBLIC,"codexSpecialSent","Z",null,null));
        MethodNode init=method(cast,required("cast.init.method"),required("cast.init.descriptor"));
        l=new InsnList();v(l,ALOAD,0);op(l,ICONST_0);f(l,PUTFIELD,C,"codexSpecialSent","Z");insert(init,null,l,false);
        AbstractInsnNode anchor=null;for(AbstractInsnNode n:init.instructions)if(n instanceof FieldInsnNode){FieldInsnNode q=(FieldInsnNode)n;if(q.getOpcode()==PUTFIELD&&q.owner.equals(C)&&q.name.equals("n")&&q.desc.equals("I")){check(anchor==null,"Single skill index assignment");anchor=n;}}
        check(anchor!=null,"Prepared cast skill boundary");
        l=new InsnList();LabelNode nativeAnimation=new LabelNode();rt(l,"specialActive","()Z");l.add(new JumpInsnNode(IFEQ,nativeAnimation));get(l,required("request.tag.field"),"I");l.add(new JumpInsnNode(IFLE,nativeAnimation));v(l,ALOAD,0);invoke(l,INVOKEVIRTUAL,C,"codexSpecialSend","()V");op(l,RETURN);l.add(nativeAnimation);insert(init,anchor,l,true);
        direct(cast,owned,targets,cooldown,senderGetter,senders);
        MethodNode attack=method(bridge,"attack","(Ljava/lang/Object;)Z");int returns=0;
        for(AbstractInsnNode n:attack.instructions.toArray())if(n.getOpcode()==IRETURN){l=new InsnList();LabelNode old=new LabelNode();rt(l,"specialActive","()Z");l.add(new JumpInsnNode(IFEQ,old));op(l,POP);player(l);f(l,GETFIELD,E,"di","L"+C+";");f(l,GETFIELD,C,"codexSpecialSent","Z");op(l,IRETURN);l.add(old);insert(attack,n,l,false);returns++;}
        check(returns==1,"Native bridge attack result");
        // Reset before each attempt, including attempts rejected before cast initialization.
        l=new InsnList();player(l);f(l,GETFIELD,E,"di","L"+C+";");op(l,ICONST_0);f(l,PUTFIELD,C,"codexSpecialSent","Z");insert(attack,null,l,false);
        menu(bridge);
        p.setProperty("special.cast.owner",C);p.setProperty("special.send.method","codexSpecialSend");p.setProperty("special.sent.field","codexSpecialSent");
        p.setProperty("special.eligibility.method",eligible.name);p.setProperty("special.menu.action","-116");p.setProperty("menu.action","-116");
        p.setProperty("special.cooldown.method",cooldown.name);p.setProperty("special.targets.method",targets.name);
        p.setProperty("special.delay.ms","600");p.setProperty("special.delayed.skill.ids","7,8,19,20");
        p.setProperty("special.default.enabled","false");p.setProperty("special.persisted","false");
        p.setProperty("special.hung.delay.ignored","true");
    }
    static void direct(ClassNode cast,MethodNode owned,MethodInsnNode targets,MethodInsnNode cooldown,MethodInsnNode getter,Map<Integer,MethodInsnNode> senders){
        MethodNode m=new MethodNode(ACC_PUBLIC,"codexSpecialSend","()V",null,null);cast.methods.add(m);added.add(m);InsnList l=m.instructions;
        LabelNode fail=new LabelNode(),pk=new LabelNode(),sent=new LabelNode();
        get(l,required("request.tag.field"),"I");get(l,required("cast.target.field"),"L"+E+";");rt(l,"allowRequest","(ILjava/lang/Object;)Z");l.add(new JumpInsnNode(IFEQ,fail));
        rt(l,"specialActive","()Z");l.add(new JumpInsnNode(IFEQ,fail));get(l,"n","I");rt(l,"specialSkillAllowed","(I)Z");l.add(new JumpInsnNode(IFEQ,fail));
        get(l,"g","B");l.add(new JumpInsnNode(IFGE,fail));
        player(l);get(l,"n","I");get(l,required("cast.target.field"),"L"+E+";");copyCall(l,targets);v(l,ASTORE,1);
        v(l,ALOAD,1);invoke(l,INVOKEVIRTUAL,"et","c","()I");l.add(new JumpInsnNode(IFLE,fail));
        get(l,required("cast.target.field"),"L"+E+";");f(l,GETFIELD,E,"cv","B");l.add(new JumpInsnNode(IFEQ,pk));
        copyCall(l,getter);v(l,ALOAD,1);get(l,"n","I");op(l,I2B);copyCall(l,senders.get(9));l.add(new JumpInsnNode(GOTO,sent));
        l.add(pk);copyCall(l,getter);v(l,ALOAD,1);get(l,"n","I");op(l,I2B);copyCall(l,senders.get(6));
        l.add(sent);player(l);get(l,"k","I");get(l,"n","I");copyCall(l,cooldown);
        invoke(l,INVOKESTATIC,"java/lang/System","currentTimeMillis","()J");f(l,PUTSTATIC,required("request.timestamp.owner"),required("request.timestamp.field"),"J");
        rt(l,"specialSent","()V");v(l,ALOAD,0);invoke(l,INVOKESTATIC,B,"cancelCast","(L"+C+";)V");
        v(l,ALOAD,0);op(l,ICONST_1);f(l,PUTFIELD,C,"codexSpecialSent","Z");op(l,RETURN);
        l.add(fail);v(l,ALOAD,0);invoke(l,INVOKESTATIC,B,"cancelCast","(L"+C+";)V");op(l,RETURN);
    }
    static void menu(ClassNode bridge){
        MethodNode m=method(bridge,"showMenu","(ZZI)V");MethodInsnNode append=null,ctor=null;FieldInsnNode listener=null;AbstractInsnNode anchor=null;
        for(AbstractInsnNode n:m.instructions.toArray())if(integer(n)==-115){AbstractInsnNode q=n;while(q!=null){if(q instanceof MethodInsnNode&&((MethodInsnNode)q).owner.equals("et")){anchor=q;append=(MethodInsnNode)q;break;}q=q.getNext();}}
        for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode&&((MethodInsnNode)n).owner.equals("bt")&&((MethodInsnNode)n).name.equals("<init>")){ctor=(MethodInsnNode)n;AbstractInsnNode q=n.getPrevious();while(q!=null&&q.getOpcode()<0)q=q.getPrevious();check(q instanceof FieldInsnNode,"Menu listener load");listener=(FieldInsnNode)q;break;}
        check(anchor!=null&&ctor!=null&&listener!=null,"Existing battlefield row");
        InsnList l=new InsnList();v(l,ALOAD,4);l.add(new TypeInsnNode(NEW,"bt"));op(l,DUP);rt(l,"specialLabel","()Ljava/lang/String;");l.add(new IntInsnNode(BIPUSH,-116));copyField(l,listener);copyCall(l,ctor);copyCall(l,append);insert(m,anchor,l,true);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==5,"input.jar compiledSupport output.jar catalog report");File input=new File(args[0]),support=new File(args[1]),output=new File(args[2]);
        check(HungBySonExpInfoPack.hash(input).equals(INPUT),"Pinned input hash");check(!output.exists()&&!new File(args[3]).exists()&&!new File(args[4]).exists(),"Fresh outputs required");
        InputStream cfg=new FileInputStream("symbols/hung-auto-attack-20261014.properties");try{p.load(cfg);}finally{cfg.close();}
        before=HungBySonExpInfoPack.read(input);out=new LinkedHashMap<String,byte[]>(before);patch();
        for(ClassNode c:nodes.values())if(originals.keySet().stream().anyMatch(c.methods::contains)||c.name.equals(C)){
            ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);out.put(c.name+".class",w.toByteArray());
        }
        // Every old instruction survives an insertion inverse; the one new method and field are explicit.
        for(Map.Entry<MethodNode,String> e:originals.entrySet()){MethodNode m=e.getKey();for(AbstractInsnNode n:inserts.get(m))m.instructions.remove(n);check(HungBySonExpInfoPack.canonical(m).equals(e.getValue()),"Insertion inverse "+m.name);}
        for(ClassNode c:nodes.values()){
            ClassNode old=HungBySonExpInfoPack.node(before.get(c.name+".class"));
            for(MethodNode m:old.methods)check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(c,m.name,m.desc))),"Untouched method "+c.name+"."+m.name);
            check(c.methods.size()==old.methods.size()+(c.name.equals(C)?1:0),"Method allowlist");
            check(c.fields.size()==old.fields.size()+(c.name.equals(C)?1:0),"Field allowlist");
        }
        for(String name:new String[]{"luna/features/AutoAttackController.class",R+".class"}){
            byte[] b=Files.readAllBytes(new File(support,name).toPath());HungBySonExpInfoPack.screen(name,b);
            ClassNode old=HungBySonExpInfoPack.node(before.get(name)),now=HungBySonExpInfoPack.node(b);
            for(MethodNode m:old.methods){
                boolean changed=name.endsWith("AutoAttackController.class")&&(m.name.equals("handleAction")||m.name.equals("tick"));
                if(!changed)check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(now,m.name,m.desc))),"Unrelated support method changed "+name+"."+m.name);
            }
            check(now.superName.equals(old.superName)&&now.interfaces.equals(old.interfaces)&&now.access==old.access,"Support ABI preserved");
            for(FieldNode fld:old.fields){boolean found=false;for(FieldNode q:now.fields)if(fld.name.equals(q.name)&&fld.desc.equals(q.desc)&&fld.access==q.access&&Objects.equals(fld.value,q.value))found=true;check(found,"Support field preserved "+fld.name);}
            out.put(name,b);
        }
        Set<String> allowed=new LinkedHashSet<String>(Arrays.asList(B+".class",P+".class",C+".class","luna/features/AutoAttackController.class",R+".class"));
        List<String> changed=new ArrayList<String>();int resources=0,classes=0;
        for(String name:before.keySet()){
            if(!Arrays.equals(before.get(name),out.get(name))){check(allowed.contains(name),"Archive allowlist "+name);changed.add(name);}
            if(name.endsWith(".class")){classes++;check(HungBySonExpInfoPack.node(before.get(name)).version==HungBySonExpInfoPack.node(out.get(name)).version,"Class version preserved");}else resources++;
        }
        check(changed.size()==5&&before.keySet().equals(out.keySet()),"Exactly five existing classes change");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        Map<String,byte[]> reread=HungBySonExpInfoPack.read(output);check(reread.keySet().equals(before.keySet()),"Archive roundtrip");
        for(String name:out.keySet())check(Arrays.equals(out.get(name),reread.get(name)),"Archive payload roundtrip "+name);
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("changed.classes",String.join(",",changed));p.setProperty("native.inverse","PASS");p.setProperty("menu.rows","6");
        OutputStream config=new FileOutputStream(args[3]);try{p.store(config,"Special attack direct dispatch; target selection unchanged");}finally{config.close();}
        PrintWriter log=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[4]),"UTF-8"));log.println("PASS pinned input; insertion inverse; exactly five classes; native serializers, targeting and resources unchanged");log.println("CHANGED="+changed);log.println("CLASSES="+classes+" UNCHANGED_RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));log.close();System.out.println("SPECIAL_ATTACK_PATCH=PASS SHA256="+p.getProperty("output.sha256"));
    }
}
