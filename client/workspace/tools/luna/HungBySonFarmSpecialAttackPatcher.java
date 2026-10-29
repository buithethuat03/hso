import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Extends the reviewed direct sender to Farm and fixes donor-priority reservation. */
public final class HungBySonFarmSpecialAttackPatcher implements Opcodes {
    static final String INPUT="05F16E80580F2941F7DC8A6092BCA5A108DFD82D3CF94E930E436251B1006119";
    static final String B="luna/generated/HungAutoAttackAccess",R="luna/features/AutoAttackRuntime",A="luna/api/SpecialAttackAccess";
    static Properties p=new Properties();static String P,E,C,F,eligible,rawChoose,cursor;
    static Map<String,byte[]> before,out;static Map<String,ClassNode> nodes=new LinkedHashMap<String,ClassNode>();
    static Map<MethodNode,String> originals=new IdentityHashMap<MethodNode,String>();
    static List<Edit> edits=new ArrayList<Edit>();static Map<String,Set<String>> additions=new HashMap<String,Set<String>>();
    static FieldInsnNode singleton,hotbar,row,skillId,slotType,target;
    static class Edit {MethodNode m;List<AbstractInsnNode> old,added;AbstractInsnNode next;}
    static void check(boolean v,String text){if(!v)throw new IllegalStateException(text);}
    static String req(String k){String v=p.getProperty(k);check(v!=null,"Missing "+k);return v;}
    static ClassNode node(String n){ClassNode c=nodes.get(n);if(c==null){c=HungBySonExpInfoPack.node(before.get(n+".class"));nodes.put(n,c);}return c;}
    static MethodNode method(ClassNode c,String n,String d){for(MethodNode m:c.methods)if(m.name.equals(n)&&m.desc.equals(d))return m;throw new IllegalStateException("Missing "+c.name+"."+n+d);}
    static List<AbstractInsnNode> real(MethodNode m){List<AbstractInsnNode> a=new ArrayList<AbstractInsnNode>();for(AbstractInsnNode n:m.instructions)if(n.getOpcode()>=0)a.add(n);return a;}
    static void v(InsnList l,int op,int i){l.add(new VarInsnNode(op,i));}static void op(InsnList l,int op){l.add(new InsnNode(op));}
    static void f(InsnList l,int op,String o,String n,String d){l.add(new FieldInsnNode(op,o,n,d));}
    static void call(InsnList l,int op,String o,String n,String d){l.add(new MethodInsnNode(op,o,n,d,false));}
    static void rt(InsnList l,String n,String d){call(l,INVOKESTATIC,R,n,d);}static void bridge(InsnList l,String n,String d){call(l,INVOKESTATIC,B,n,d);}
    static void player(InsnList l){f(l,GETSTATIC,singleton.owner,singleton.name,singleton.desc);}
    static void cast(InsnList l){player(l);f(l,GETFIELD,E,"di","L"+C+";");}
    static void get(InsnList l,String name,String d){v(l,ALOAD,0);f(l,GETFIELD,C,name,d);}
    static void fld(InsnList l,FieldInsnNode q){f(l,q.getOpcode(),q.owner,q.name,q.desc);}
    static boolean invokes(AbstractInsnNode n,String owner,String name){return n instanceof MethodInsnNode&&((MethodInsnNode)n).owner.equals(owner)&&((MethodInsnNode)n).name.equals(name);}
    static void edit(MethodNode m,AbstractInsnNode first,AbstractInsnNode last,InsnList code){
        if(!originals.containsKey(m))originals.put(m,HungBySonExpInfoPack.canonical(m));
        Edit e=new Edit();e.m=m;e.old=new ArrayList<AbstractInsnNode>();e.added=Arrays.asList(code.toArray());e.next=last==null?m.instructions.getFirst():last.getNext();
        if(first!=null){for(AbstractInsnNode n=first;;){AbstractInsnNode next=n.getNext();e.old.add(n);m.instructions.remove(n);if(n==last)break;n=next;}}
        if(e.next==null)m.instructions.add(code);else m.instructions.insertBefore(e.next,code);edits.add(e);
    }
    static void prefix(MethodNode m,InsnList l){edit(m,null,null,l);}
    static void insertBefore(MethodNode m,AbstractInsnNode anchor,InsnList l){
        if(!originals.containsKey(m))originals.put(m,HungBySonExpInfoPack.canonical(m));
        Edit e=new Edit();e.m=m;e.old=new ArrayList<AbstractInsnNode>();e.added=Arrays.asList(l.toArray());e.next=anchor;m.instructions.insertBefore(anchor,l);edits.add(e);
    }
    static MethodNode add(ClassNode c,int access,String n,String d){MethodNode m=new MethodNode(access,n,d,null,null);c.methods.add(m);Set<String> s=additions.get(c.name);if(s==null){s=new HashSet<String>();additions.put(c.name,s);}s.add(n+d);return m;}
    static void replaceCall(MethodNode m,String owner,String name,String toOwner,String toName){int count=0;for(AbstractInsnNode n:m.instructions.toArray())if(invokes(n,owner,name)){MethodInsnNode q=(MethodInsnNode)n;InsnList l=new InsnList();call(l,INVOKESTATIC,toOwner,toName,q.desc);edit(m,n,n,l);count++;}check(count==1,"One call "+name+" in "+m.name);}
    static void discover(){
        P=req("native.skill.owner");E=req("entity.owner");C=req("cast.owner");F=req("native.farm.enabled.owner");eligible=req("special.eligibility.method");
        for(AbstractInsnNode n:method(node(B),"player","()Ljava/lang/Object;").instructions)if(n instanceof FieldInsnNode&&n.getOpcode()==GETSTATIC)singleton=(FieldInsnNode)n;
        for(AbstractInsnNode n:method(node(B),"attack","(Ljava/lang/Object;)Z").instructions)if(n instanceof FieldInsnNode&&n.getOpcode()==PUTSTATIC)target=(FieldInsnNode)n;
        MethodNode skills=method(node(P),req("native.skill.method"),"()Z");
        for(AbstractInsnNode n:skills.instructions)if(n instanceof FieldInsnNode){FieldInsnNode q=(FieldInsnNode)n;
            if(q.owner.equals(P)&&q.desc.startsWith("[[L"))hotbar=q;
            if(q.owner.equals(P)&&q.desc.equals("I"))row=q;
            if(q.getOpcode()==GETFIELD&&q.desc.equals("S"))skillId=q;
            if(q.getOpcode()==GETFIELD&&q.desc.equals("B"))slotType=q;
        }
        check(singleton!=null&&target!=null&&hotbar!=null&&row!=null&&skillId!=null&&slotType!=null,"Native active hotbar symbols");
        MethodNode farmChoose=method(node(P),"codexFarmChoose","(IZ)V");
        for(AbstractInsnNode n:farmChoose.instructions)if(n instanceof MethodInsnNode){MethodInsnNode q=(MethodInsnNode)n;if(q.owner.equals(P)&&q.desc.equals("(IZ)V"))rawChoose=q.name;}
        check(rawChoose!=null&&(method(node(P),rawChoose,"(IZ)V").access&ACC_PUBLIC)!=0,"Public native Farm chooser");
        MethodNode update=method(node(P),"codexLocalPlayerUpdate","()V");
        int stores=0;for(AbstractInsnNode n:update.instructions.toArray())if(invokes(n,P,"codexFarmChoose")){
            AbstractInsnNode q=n.getNext();while(q!=null&&q.getOpcode()<0)q=q.getNext();
            if(q!=null&&q.getOpcode()==ILOAD){q=q.getNext();while(q!=null&&q.getOpcode()<0)q=q.getNext();if(q!=null&&q.getOpcode()==I2B){q=q.getNext();while(q!=null&&q.getOpcode()<0)q=q.getNext();if(q instanceof FieldInsnNode&&q.getOpcode()==PUTSTATIC){FieldInsnNode z=(FieldInsnNode)q;check(z.owner.equals(P)&&z.desc.equals("B"),"Farm cursor store");cursor=z.name;InsnList l=new InsnList();bridge(l,"specialFarmCursor","(I)I");insertBefore(update,q,l);stores++;
                }}}
        }
        check(stores==2&&cursor!=null,"Two native Farm cursor writes");
    }
    static void patch(){
        discover();ClassNode b=node(B),pc=node(P),cc=node(C);b.interfaces.add(A);
        MethodNode base=new MethodNode(ACC_PUBLIC,"codexSpecialBaseEligible","(II)Z",null,null);method(pc,eligible,"(II)Z").accept(base);base.name="codexSpecialBaseEligible";base.access=ACC_PUBLIC;pc.methods.add(base);additions.computeIfAbsent(P,k->new HashSet<String>()).add(base.name+base.desc);
        List<AbstractInsnNode> a=real(base);check(a.size()>7&&invokes(a.get(0),R,"specialActive")&&invokes(a.get(3),R,"specialSkillAllowed")&&a.get(6).getOpcode()==IRETURN,"Pinned special eligibility prefix");for(int i=0;i<7;i++)base.instructions.remove(a.get(i));
        replaceCall(method(pc,eligible,"(II)Z"),R,"specialActive",B,"specialContext");
        MethodNode init=method(cc,req("cast.init.method"),req("cast.init.descriptor"));replaceCall(init,R,"specialActive",B,"specialContext");
        a=real(init);int changed=0;for(int i=2;i+2<a.size();i++)if(invokes(a.get(i-2),B,"specialContext")&&a.get(i).getOpcode()==ALOAD&&a.get(i+1) instanceof FieldInsnNode&&((FieldInsnNode)a.get(i+1)).name.equals(req("request.tag.field"))&&a.get(i+2).getOpcode()==IFLE){InsnList l=new InsnList();v(l,ALOAD,0);bridge(l,"specialCastContext","(L"+C+";)Z");l.add(new JumpInsnNode(IFEQ,((JumpInsnNode)a.get(i+2)).label));edit(init,a.get(i),a.get(i+2),l);changed++;}check(changed==1,"One prepared-cast scope guard");
        MethodNode direct=method(cc,"codexSpecialSend","()V");a=real(direct);check(invokes(a.get(4),R,"allowRequest"),"Original direct guard");InsnList l=new InsnList();v(l,ALOAD,0);bridge(l,"specialRequestAllowed","(L"+C+";)Z");edit(direct,a.get(0),a.get(4),l);replaceCall(direct,R,"specialActive",B,"specialContext");
        for(AbstractInsnNode n:direct.instructions.toArray())if(invokes(n,B,"cancelCast")){MethodInsnNode q=(MethodInsnNode)n;l=new InsnList();bridge(l,"finishSpecialCast",q.desc);edit(direct,n,n,l);}
        MethodNode gate=method(cc,"e","()Z");l=new InsnList();LabelNode normal=new LabelNode();bridge(l,"farmSpecialEnabled","()Z");l.add(new JumpInsnNode(IFEQ,normal));op(l,ICONST_1);op(l,IRETURN);l.add(normal);prefix(gate,l);
        accessMethods(b,pc);scopes(b);dispatch(b);
        MethodNode attack=method(b,"attack","(Ljava/lang/Object;)Z");l=new InsnList();normal=new LabelNode();rt(l,"specialActive","()Z");l.add(new JumpInsnNode(IFEQ,normal));v(l,ALOAD,1);l.add(new TypeInsnNode(CHECKCAST,E));f(l,PUTSTATIC,target.owner,target.name,target.desc);f(l,GETSTATIC,B,"VIEW","L"+B+";");op(l,ICONST_0);rt(l,"chooseSpecial","(L"+A+";Z)Z");op(l,IRETURN);l.add(normal);prefix(attack,l);
        MethodNode farmChoose=method(pc,"codexFarmChoose","(IZ)V");l=new InsnList();normal=new LabelNode();bridge(l,"farmSpecialEnabled","()Z");l.add(new JumpInsnNode(IFEQ,normal));v(l,ALOAD,0);bridge(l,"dispatchFarmSpecialChoose","(L"+P+";)V");op(l,RETURN);l.add(normal);prefix(farmChoose,l);
        // Controller transitions already hold the shared monitor. Cancel only marked local Farm casts.
        MethodNode cancel=method(b,"cancelAttack","(Ljava/lang/Object;)V");l=new InsnList();normal=new LabelNode();fld(l,new FieldInsnNode(GETSTATIC,F,req("native.farm.enabled.field"),"Z"));l.add(new JumpInsnNode(IFEQ,normal));player(l);l.add(new JumpInsnNode(IFNULL,normal));cast(l);v(l,ASTORE,2);v(l,ALOAD,2);l.add(new JumpInsnNode(IFNULL,normal));v(l,ALOAD,2);f(l,GETFIELD,C,"codexFarmRequest","Z");l.add(new JumpInsnNode(IFEQ,normal));v(l,ALOAD,2);f(l,GETFIELD,C,req("request.tag.field"),"I");l.add(new JumpInsnNode(IFNE,normal));v(l,ALOAD,2);bridge(l,"finishSpecialCast","(L"+C+";)V");op(l,RETURN);l.add(normal);prefix(cancel,l);
    }
    static void accessMethods(ClassNode b,ClassNode pc){
        InsnList l=add(b,ACC_PUBLIC,"specialSlotCount","()I").instructions;fld(l,hotbar);fld(l,row);op(l,AALOAD);op(l,ARRAYLENGTH);op(l,IRETURN);
        l=add(b,ACC_PUBLIC,"specialSkillAt","(I)I").instructions;LabelNode none=new LabelNode();fld(l,hotbar);fld(l,row);op(l,AALOAD);v(l,ILOAD,1);op(l,AALOAD);v(l,ASTORE,2);v(l,ALOAD,2);l.add(new JumpInsnNode(IFNULL,none));v(l,ALOAD,2);fld(l,slotType);l.add(new JumpInsnNode(IFNE,none));v(l,ALOAD,2);fld(l,skillId);op(l,IRETURN);l.add(none);op(l,ICONST_M1);op(l,IRETURN);
        MethodNode ready=add(b,ACC_PUBLIC,"specialSkillReady","(I)Z");l=ready.instructions;LabelNode readyStart=new LabelNode(),readyEnd=new LabelNode(),incomplete=new LabelNode();l.add(readyStart);player(l);v(l,ILOAD,1);op(l,ICONST_M1);call(l,INVOKEVIRTUAL,P,"codexSpecialBaseEligible","(II)Z");l.add(readyEnd);op(l,IRETURN);l.add(incomplete);op(l,POP);op(l,ICONST_0);op(l,IRETURN);ready.tryCatchBlocks.add(new TryCatchBlockNode(readyStart,readyEnd,incomplete,"java/lang/RuntimeException"));
        l=add(b,ACC_PUBLIC,"specialChoose","(IZ)Z").instructions;player(l);v(l,ILOAD,1);v(l,ILOAD,2);call(l,INVOKEVIRTUAL,P,"codexSpecialChoose","(IZ)Z");op(l,IRETURN);
        l=add(pc,ACC_PUBLIC,"codexSpecialChoose","(IZ)Z").instructions;v(l,ALOAD,0);f(l,GETFIELD,E,"di","L"+C+";");op(l,ICONST_0);f(l,PUTFIELD,C,"codexSpecialSent","Z");LabelNode auto=new LabelNode(),done=new LabelNode(),ret=new LabelNode(),autoCursor=new LabelNode();v(l,ILOAD,2);l.add(new JumpInsnNode(IFEQ,auto));v(l,ALOAD,0);v(l,ILOAD,1);op(l,ICONST_0);call(l,INVOKESPECIAL,P,rawChoose,"(IZ)V");l.add(new JumpInsnNode(GOTO,done));l.add(auto);v(l,ALOAD,0);v(l,ILOAD,1);op(l,ICONST_0);call(l,INVOKEVIRTUAL,P,req("native.choose.method"),"(IZ)V");l.add(done);
        v(l,ALOAD,0);f(l,GETFIELD,E,"di","L"+C+";");f(l,GETFIELD,C,"codexSpecialSent","Z");v(l,ISTORE,3);v(l,ILOAD,3);l.add(new JumpInsnNode(IFEQ,ret));v(l,ILOAD,1);l.add(new JumpInsnNode(IFLT,ret));v(l,ILOAD,2);l.add(new JumpInsnNode(IFEQ,autoCursor));v(l,ILOAD,1);op(l,I2B);f(l,PUTSTATIC,P,cursor,"B");l.add(new JumpInsnNode(GOTO,ret));l.add(autoCursor);v(l,ILOAD,1);op(l,I2B);f(l,PUTSTATIC,P,req("native.skill.slot.field"),"B");l.add(ret);v(l,ILOAD,3);op(l,IRETURN);
        l=add(b,ACC_PUBLIC|ACC_STATIC,"specialFarmCursor","(I)I").instructions;LabelNode nativeCursor=new LabelNode();bridge(l,"farmSpecialEnabled","()Z");l.add(new JumpInsnNode(IFEQ,nativeCursor));f(l,GETSTATIC,P,cursor,"B");op(l,IRETURN);l.add(nativeCursor);v(l,ILOAD,0);op(l,IRETURN);
    }
    static void scopes(ClassNode b){
        InsnList l=add(b,ACC_PUBLIC|ACC_STATIC,"farmSpecialEnabled","()Z").instructions;LabelNode no=new LabelNode();f(l,GETSTATIC,F,req("native.farm.enabled.field"),"Z");l.add(new JumpInsnNode(IFEQ,no));rt(l,"enabled","()Z");l.add(new JumpInsnNode(IFNE,no));rt(l,"specialEnabled","()Z");op(l,IRETURN);l.add(no);op(l,ICONST_0);op(l,IRETURN);
        l=add(b,ACC_PUBLIC|ACC_STATIC,"specialContext","()Z").instructions;LabelNode yes=new LabelNode();no=new LabelNode();rt(l,"specialActive","()Z");l.add(new JumpInsnNode(IFNE,yes));bridge(l,"farmSpecialEnabled","()Z");l.add(new JumpInsnNode(IFEQ,no));player(l);bridge(l,"captureFarmOrigin","(L"+E+";)Z");op(l,IRETURN);l.add(yes);op(l,ICONST_1);op(l,IRETURN);l.add(no);op(l,ICONST_0);op(l,IRETURN);
        l=add(b,ACC_PUBLIC|ACC_STATIC,"specialCastContext","(L"+C+";)Z").instructions;no=new LabelNode();yes=new LabelNode();bridge(l,"specialContext","()Z");l.add(new JumpInsnNode(IFEQ,no));get(l,req("cast.actor.field"),"L"+E+";");player(l);l.add(new JumpInsnNode(IF_ACMPNE,no));get(l,req("request.tag.field"),"I");l.add(new JumpInsnNode(IFGT,yes));get(l,req("request.tag.field"),"I");l.add(new JumpInsnNode(IFNE,no));get(l,"codexFarmRequest","Z");l.add(new JumpInsnNode(IFEQ,no));get(l,"g","B");l.add(new JumpInsnNode(IFGE,no));l.add(yes);op(l,ICONST_1);op(l,IRETURN);l.add(no);op(l,ICONST_0);op(l,IRETURN);
        l=add(b,ACC_PUBLIC|ACC_STATIC,"specialRequestAllowed","(L"+C+";)Z").instructions;no=new LabelNode();LabelNode farm=new LabelNode();get(l,"codexSpecialSent","Z");l.add(new JumpInsnNode(IFNE,no));v(l,ALOAD,0);bridge(l,"specialCastContext","(L"+C+";)Z");l.add(new JumpInsnNode(IFEQ,no));get(l,req("request.tag.field"),"I");l.add(new JumpInsnNode(IFEQ,farm));get(l,req("request.tag.field"),"I");get(l,req("cast.target.field"),"L"+E+";");rt(l,"allowRequest","(ILjava/lang/Object;)Z");op(l,IRETURN);
        l.add(farm);bridge(l,"isWorldReadyStatic","()Z");l.add(new JumpInsnNode(IFEQ,no));get(l,req("cast.target.field"),"L"+E+";");v(l,ASTORE,1);v(l,ALOAD,1);l.add(new JumpInsnNode(IFNULL,no));v(l,ALOAD,1);f(l,GETFIELD,E,"bs","I");l.add(new JumpInsnNode(IFLE,no));v(l,ALOAD,1);f(l,GETFIELD,E,"cG","I");op(l,ICONST_4);l.add(new JumpInsnNode(IF_ICMPEQ,no));v(l,ALOAD,1);call(l,INVOKESTATIC,"luna/generated/HungMonsterSelectionCommands","allow","(Ljava/lang/Object;)Z");op(l,IRETURN);l.add(no);op(l,ICONST_0);op(l,IRETURN);
        l=add(b,ACC_PUBLIC|ACC_STATIC,"finishSpecialCast","(L"+C+";)V").instructions;LabelNode cleanup=new LabelNode();get(l,req("request.tag.field"),"I");l.add(new JumpInsnNode(IFNE,cleanup));get(l,"codexFarmRequest","Z");l.add(new JumpInsnNode(IFEQ,cleanup));get(l,req("cast.actor.field"),"L"+E+";");player(l);l.add(new JumpInsnNode(IF_ACMPNE,cleanup));v(l,ALOAD,0);op(l,ICONST_M1);f(l,PUTFIELD,C,req("request.tag.field"),"I");l.add(cleanup);v(l,ALOAD,0);bridge(l,"cancelCast","(L"+C+";)V");op(l,RETURN);
    }
    static void dispatch(ClassNode b){
        MethodNode m=add(b,ACC_PUBLIC|ACC_STATIC,"dispatchFarmSpecialChoose","(L"+P+";)V");InsnList l=m.instructions;rt(l,"requestLock","()Ljava/lang/Object;");v(l,ASTORE,1);v(l,ALOAD,1);op(l,MONITORENTER);LabelNode start=new LabelNode(),finish=new LabelNode(),handler=new LabelNode(),skip=new LabelNode();l.add(start);bridge(l,"farmSpecialEnabled","()Z");l.add(new JumpInsnNode(IFEQ,skip));v(l,ALOAD,0);player(l);l.add(new JumpInsnNode(IF_ACMPNE,skip));bridge(l,"enterFarmStart","()V");
        LabelNode scopeStart=new LabelNode(),scopeEnd=new LabelNode(),scopeHandler=new LabelNode();l.add(scopeStart);f(l,GETSTATIC,B,"VIEW","L"+B+";");op(l,ICONST_1);rt(l,"chooseSpecial","(L"+A+";Z)Z");op(l,POP);l.add(scopeEnd);bridge(l,"exitFarmStart","()V");l.add(new JumpInsnNode(GOTO,skip));l.add(scopeHandler);v(l,ASTORE,2);bridge(l,"exitFarmStart","()V");v(l,ALOAD,2);op(l,ATHROW);l.add(skip);l.add(finish);v(l,ALOAD,1);op(l,MONITOREXIT);op(l,RETURN);l.add(handler);v(l,ASTORE,2);v(l,ALOAD,1);op(l,MONITOREXIT);v(l,ALOAD,2);op(l,ATHROW);m.tryCatchBlocks.add(new TryCatchBlockNode(scopeStart,scopeEnd,scopeHandler,null));m.tryCatchBlocks.add(new TryCatchBlockNode(start,finish,handler,null));
    }
    public static void main(String[] args)throws Exception{
        check(args.length==5,"input support output catalog report");File input=new File(args[0]),support=new File(args[1]),output=new File(args[2]);check(HungBySonExpInfoPack.hash(input).equals(INPUT),"Pinned current special release");check(!output.exists()&&!new File(args[3]).exists()&&!new File(args[4]).exists(),"Fresh outputs required");
        InputStream config=new FileInputStream("build/hung-special-attack-20261023-b/special-attack.properties");try{p.load(config);}finally{config.close();}before=HungBySonExpInfoPack.read(input);out=new LinkedHashMap<String,byte[]>(before);patch();
        for(String owner:new String[]{P,C,B}){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);node(owner).accept(w);out.put(owner+".class",w.toByteArray());}
        for(int i=edits.size()-1;i>=0;i--){Edit e=edits.get(i);for(AbstractInsnNode n:e.added)e.m.instructions.remove(n);for(AbstractInsnNode n:e.old){if(e.next==null)e.m.instructions.add(n);else e.m.instructions.insertBefore(e.next,n);}}
        for(Map.Entry<MethodNode,String> e:originals.entrySet())check(HungBySonExpInfoPack.canonical(e.getKey()).equals(e.getValue()),"Native instruction inverse "+e.getKey().name);
        for(String owner:new String[]{P,C,B}){ClassNode old=HungBySonExpInfoPack.node(before.get(owner+".class")),now=node(owner);check(old.fields.size()==now.fields.size(),"No new native fields");for(MethodNode m:old.methods)check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(now,m.name,m.desc))),"Unrelated method "+owner+"."+m.name);Set<String> extra=additions.get(owner);check(now.methods.size()==old.methods.size()+(extra==null?0:extra.size()),"Method allowlist");}
        for(String name:new String[]{"luna/features/AutoAttackController.class",R+".class",A+".class"}){byte[] data=Files.readAllBytes(new File(support,name).toPath());HungBySonExpInfoPack.screen(name,data);if(before.containsKey(name)){ClassNode old=HungBySonExpInfoPack.node(before.get(name)),now=HungBySonExpInfoPack.node(data);for(MethodNode m:old.methods)if(!m.name.equals("specialSkillAllowed")&&!m.name.equals("specialSent"))check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(now,m.name,m.desc))),"Unrelated support "+m.name);}out.put(name,data);}
        Set<String> allowed=new HashSet<String>(Arrays.asList(P+".class",C+".class",B+".class","luna/features/AutoAttackController.class",R+".class"));List<String> changed=new ArrayList<String>();int classes=0,resources=0;
        for(String name:before.keySet()){if(!Arrays.equals(before.get(name),out.get(name))){check(allowed.contains(name),"Archive allowlist "+name);changed.add(name);}if(name.endsWith(".class")){check(HungBySonExpInfoPack.node(before.get(name)).version==HungBySonExpInfoPack.node(out.get(name)).version,"Class version");classes++;}else resources++;}check(changed.size()==5&&out.size()==before.size()+1,"Five changed classes and one added ABI");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        Map<String,byte[]> reread=HungBySonExpInfoPack.read(output);check(reread.keySet().equals(out.keySet()),"Entry roundtrip");for(String n:out.keySet())check(Arrays.equals(out.get(n),reread.get(n)),"Payload roundtrip "+n);
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("farm.special.enabled","true");p.setProperty("special.priority","descending configured skill ID; reserve ready winner before 600ms gate");p.setProperty("special.baseEligibility.method","codexSpecialBaseEligible");p.setProperty("special.farm.cursor.field",cursor);p.setProperty("native.inverse","PASS");p.setProperty("changed.classes",String.join(",",changed));p.setProperty("added.classes",A+".class");
        OutputStream cfg=new FileOutputStream(args[3]);try{p.store(cfg,"Farm special dispatch and donor priority without target changes");}finally{cfg.close();}PrintWriter log=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[4]),"UTF-8"));log.println("PASS pinned input, exact inverse, unchanged members/resources, CLDC support");log.println("CHANGED="+changed+" ADDED="+A);log.println("CLASSES="+(classes+1)+" UNCHANGED_RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));log.close();System.out.println("FARM_SPECIAL_PATCH=PASS SHA256="+p.getProperty("output.sha256"));
    }
}
