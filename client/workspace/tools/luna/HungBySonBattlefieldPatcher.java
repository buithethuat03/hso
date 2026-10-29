import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Incremental, pinned battlefield hooks. Semantic policy lives in BattlefieldSupport. */
public final class HungBySonBattlefieldPatcher implements Opcodes {
    static final String INPUT="3CB0DBA1EC67F0687057AEB1533C025A7858D85DC11B2A246097A029C053FCCF";
    static final String B="luna/generated/BattlefieldBridge",S="luna/features/BattlefieldSupport",A="luna/api/BattlefieldAccess";
    static final String CACHE="luna/generated/HungMonsterCacheAccess",NAMES="luna/api/MonsterCacheTemplateNames";
    static String E,P,W,G,F,RECV; static FieldInsnNode player,red;
    static Map<String,byte[]> before,out; static Map<String,ClassNode> nodes=new LinkedHashMap<String,ClassNode>();
    static Map<MethodNode,String> originals=new IdentityHashMap<MethodNode,String>();
    static Map<MethodNode,List<AbstractInsnNode>> inserts=new IdentityHashMap<MethodNode,List<AbstractInsnNode>>();
    static Properties symbols=new Properties();
    static void check(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    static ClassNode node(String name){ClassNode n=nodes.get(name);if(n==null){check(before.containsKey(name+".class"),"Missing "+name);n=HungBySonExpInfoPack.node(before.get(name+".class"));nodes.put(name,n);}return n;}
    static MethodNode method(ClassNode n,String name,String desc){for(MethodNode m:n.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;throw new IllegalStateException(n.name+"."+name+desc);}
    static void insert(MethodNode m,AbstractInsnNode anchor,InsnList code,boolean after){
        if(!originals.containsKey(m)){originals.put(m,HungBySonExpInfoPack.canonical(m));inserts.put(m,new ArrayList<AbstractInsnNode>());}
        inserts.get(m).addAll(Arrays.asList(code.toArray()));
        if(anchor==null)m.instructions.insert(code);else if(after)m.instructions.insert(anchor,code);else m.instructions.insertBefore(anchor,code);
    }
    static InsnList code(){return new InsnList();}
    static void v(InsnList l,int op,int n){l.add(new VarInsnNode(op,n));}
    static void op(InsnList l,int op){l.add(new InsnNode(op));}
    static void f(InsnList l,int op,String owner,String name,String d){l.add(new FieldInsnNode(op,owner,name,d));}
    static void call(InsnList l,String owner,String name,String d){l.add(new MethodInsnNode(INVOKESTATIC,owner,name,d,false));}
    static void invoke(InsnList l,int op,String owner,String name,String d){l.add(new MethodInsnNode(op,owner,name,d,op==INVOKEINTERFACE));}
    static void number(InsnList l,int i){l.add(new LdcInsnNode(Integer.valueOf(i)));}
    static MethodNode add(ClassNode c,int access,String name,String desc){MethodNode m=new MethodNode(access,name,desc,null,null);c.methods.add(m);return m;}
    static void prop(String k,String s){symbols.setProperty(k,s);}
    static void discover() throws Exception {
        Properties p=new Properties();InputStream in=new FileInputStream("symbols/hung-auto-attack-20261014.properties");try{p.load(in);}finally{in.close();}
        E=p.getProperty("entity.owner");P=p.getProperty("hook.movement.localUpdate.owner");
        ClassNode access=node("luna/generated/HungAutoAttackAccess");
        MethodNode pm=method(access,"player","()Ljava/lang/Object;");
        for(AbstractInsnNode n:pm.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==GETSTATIC){check(player==null,"Unique player singleton");player=(FieldInsnNode)n;}
        check(player!=null&&player.desc.equals("L"+P+";"),"Pinned player descriptor");W=player.owner;
        for(MethodNode m:node(E).methods)if(m.name.equals("a")&&m.desc.endsWith(";I)V")){
            Type[] args=Type.getArgumentTypes(m.desc);if(args.length==2&&args[0].getSort()==Type.OBJECT)G=args[0].getInternalName();
        }
        check(G!=null,"Graphics owner");
        // Resolve the bitmap font by the exact name renderer descriptor.
        for(AbstractInsnNode n:method(node(E),"a","(L"+G+";I)V").instructions.toArray())if(n instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)n;if(x.desc.equals("(L"+G+";Ljava/lang/String;IIIZ)V"))F=x.owner;}
        check(F!=null,"Font owner");
        for(MethodNode m:node(F).methods){AbstractInsnNode[] all=m.instructions.toArray();for(int i=0;i<all.length;i++)if(all[i] instanceof LdcInsnNode&&"/mfont/tahoma_7b_red.png".equals(((LdcInsnNode)all[i]).cst)){
            for(int j=i+1;j<Math.min(i+12,all.length);j++)if(all[j] instanceof FieldInsnNode&&all[j].getOpcode()==PUTSTATIC){check(red==null,"Unique bold red font");red=(FieldInsnNode)all[j];break;}
        }}
        check(red!=null&&red.desc.equals("L"+F+";"),"Existing bold red sprite font");
        Properties death=new Properties();InputStream d=new FileInputStream("symbols/hung-investigate-stale-target-20261005.properties");try{death.load(d);}finally{d.close();}
        RECV=death.getProperty("hook.unseenCombat.owner");
        prop("combat.owner",RECV);prop("combat.method",death.getProperty("hook.unseenCombat.method"));prop("combat.descriptor",death.getProperty("hook.unseenCombat.descriptor"));
        prop("entity.owner",E);prop("player.owner",P);prop("graphics.owner",G);prop("font.owner",F);prop("font.red.field",red.name);prop("singleton.owner",W);prop("singleton.field",player.name);
    }
    static void patchEntity(){
        ClassNode c=node(E);
        for(String name:new String[]{"battlefieldX","battlefieldY"})c.fields.add(new FieldNode(ACC_PUBLIC,name,"I",null,null));
        c.fields.add(new FieldNode(ACC_PUBLIC,"battlefieldPositionKnown","Z",null,null));
        int ctors=0;
        for(MethodNode m:c.methods)if(m.name.equals("<init>"))for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==RETURN){InsnList l=code();v(l,ALOAD,0);call(l,B,"capturePosition","(L"+E+";)V");insert(m,n,l,false);ctors++;}
        check(ctors==2,"Pinned constructors: "+ctors);
        MethodNode update=method(c,"a","()V");int self=update.maxLocals++;InsnList l=code();v(l,ALOAD,0);v(l,ASTORE,self);v(l,ALOAD,self);call(l,B,"align","(L"+E+";)Z");op(l,POP);insert(update,null,l,false);
        for(AbstractInsnNode n:update.instructions.toArray())if(n.getOpcode()==RETURN){l=code();v(l,ALOAD,self);call(l,B,"align","(L"+E+";)Z");op(l,POP);insert(update,n,l,false);}
        for(String name:new String[]{"h","L","p_","z","A"}){
            MethodNode m=method(c,name,"()V");l=code();LabelNode nativeCode=new LabelNode();v(l,ALOAD,0);call(l,B,"align","(L"+E+";)Z");l.add(new JumpInsnNode(IFEQ,nativeCode));op(l,RETURN);l.add(nativeCode);insert(m,null,l,false);
        }
        // Wrap only the font operand of the two actual overhead-name draws.
        MethodNode name=method(c,"a","(L"+G+";I)V");AbstractInsnNode[] all=name.instructions.toArray();int wrapped=0;
        for(int i=0;i+2<all.length;i++)if(all[i] instanceof VarInsnNode&&all[i].getOpcode()==ALOAD&&((VarInsnNode)all[i]).var==4){
            AbstractInsnNode x=next(all[i]),y=next(x);if(x instanceof VarInsnNode&&x.getOpcode()==ALOAD&&((VarInsnNode)x).var==1&&y instanceof VarInsnNode&&y.getOpcode()==ALOAD&&((VarInsnNode)y).var==3){
                l=code();v(l,ALOAD,0);call(l,B,"nameFont","(L"+F+";L"+E+";)L"+F+";");insert(name,all[i],l,true);wrapped++;
            }
        }
        MethodNode name2=method(c,"b","(L"+G+";)V");
        for(AbstractInsnNode n:name2.instructions.toArray())if(n instanceof VarInsnNode&&n.getOpcode()==ALOAD&&((VarInsnNode)n).var==8){
            AbstractInsnNode x=next(n),y=next(x);if(x instanceof VarInsnNode&&x.getOpcode()==ALOAD&&((VarInsnNode)x).var==1&&y instanceof TypeInsnNode&&y.getOpcode()==NEW){
                l=code();v(l,ALOAD,0);call(l,B,"nameFont","(L"+F+";L"+E+";)L"+F+";");insert(name2,n,l,true);wrapped++;
            }
        }
        check(wrapped==2,"Two overhead name font operands: "+wrapped);prop("name.hooks",String.valueOf(wrapped));
    }
    static AbstractInsnNode next(AbstractInsnNode n){do{n=n.getNext();}while(n!=null&&n.getOpcode()<0);return n;}
    static boolean entitySubclass(String name){
        Set<String> visited=new HashSet<String>();
        while(name!=null&&before.containsKey(name+".class")&&visited.add(name)){
            if(name.equals(E))return true;
            name=node(name).superName;
        }
        return false;
    }
    static void patchRemoteUpdates(){
        int count=0;
        for(String entry:before.keySet())if(entry.endsWith(".class")){
            String name=entry.substring(0,entry.length()-6);
            if(name.equals(E)||!entitySubclass(name))continue;
            for(MethodNode m:node(name).methods)if(m.name.equals("a")&&m.desc.equals("()V")&&(m.access&(ACC_STATIC|ACC_ABSTRACT))==0){
                int self=m.maxLocals++;
                InsnList l=code();v(l,ALOAD,0);v(l,ASTORE,self);v(l,ALOAD,self);call(l,B,"align","(L"+E+";)Z");op(l,POP);insert(m,null,l,false);
                for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==RETURN){l=code();v(l,ALOAD,self);call(l,B,"align","(L"+E+";)Z");op(l,POP);insert(m,n,l,false);}
                count++;
            }
        }
        check(count>0,"Native entity subclass update overrides");prop("position.subclass.updates",String.valueOf(count));
    }
    static boolean integer(AbstractInsnNode n,int value){return n instanceof IntInsnNode&&((IntInsnNode)n).operand==value||n instanceof LdcInsnNode&&Integer.valueOf(value).equals(((LdcInsnNode)n).cst);}
    static void patchPackets(){
        ClassNode recv=node(RECV);int target=0,current=0;
        for(MethodNode m:recv.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTFIELD){
            FieldInsnNode x=(FieldInsnNode)n;if(x.owner.equals(E)&&x.desc.equals("I")&&(x.name.equals("bh")||x.name.equals("ba"))){
                InsnList pre=code();op(pre,DUP2);insert(m,n,pre,false);InsnList post=code();op(post,POP);call(post,B,x.name.equals("bh")?"captureTarget":"capturePosition","(L"+E+";)V");insert(m,n,post,true);if(x.name.equals("bh"))target++;else current++;
            }
        }
        check(target==7&&current==11,"Pinned packet coordinate writes target="+target+" position="+current);
        MethodNode combat=method(recv,symbols.getProperty("combat.method"),symbols.getProperty("combat.descriptor"));
        // Mirror the already-installed snapshot observer's stream accessor, without consuming native input.
        MethodInsnNode stream=null;for(AbstractInsnNode n:combat.instructions.toArray())if(n instanceof MethodInsnNode&&((MethodInsnNode)n).desc.equals("()Ljava/io/DataInputStream;")){stream=(MethodInsnNode)n;break;}
        check(stream!=null&&(combat.access&ACC_STATIC)!=0,"Static combat stream accessor");InsnList l=code();f(l,GETSTATIC,B,"INSTANCE","L"+A+";");v(l,ALOAD,0);invoke(l,stream.getOpcode(),stream.owner,stream.name,stream.desc);v(l,ILOAD,2);call(l,S,"onCombat","(L"+A+";Ljava/io/DataInputStream;I)V");insert(combat,null,l,false);
        prop("position.target.hooks",String.valueOf(target));prop("position.current.hooks",String.valueOf(current));
    }
    static void patchAuto(){
        ClassNode c=node("luna/generated/HungAutoAttackAccess");MethodNode valid=method(c,"validTarget","(Ljava/lang/Object;Z)Z");InsnList l=code();LabelNode pass=new LabelNode();v(l,ALOAD,1);call(l,B,"blocked","(Ljava/lang/Object;)Z");l.add(new JumpInsnNode(IFEQ,pass));op(l,ICONST_0);op(l,IRETURN);l.add(pass);insert(valid,null,l,false);
        MethodNode menu=method(c,"showMenu","(ZZI)V");AbstractInsnNode rowAnchor=null;
        for(AbstractInsnNode n:menu.instructions.toArray())if(integer(n,-114)){
            for(AbstractInsnNode a=n;a!=null;a=a.getNext())if(a instanceof MethodInsnNode&&a.getOpcode()==INVOKEVIRTUAL&&((MethodInsnNode)a).owner.equals("et")){rowAnchor=a;break;}
        }
        check(rowAnchor!=null,"Delay menu row");MethodInsnNode append=(MethodInsnNode)rowAnchor;
        MethodInsnNode ctor=null;FieldInsnNode listener=null;for(AbstractInsnNode n:menu.instructions.toArray()){
            if(n instanceof MethodInsnNode&&((MethodInsnNode)n).owner.equals("bt")&&((MethodInsnNode)n).name.equals("<init>"))ctor=(MethodInsnNode)n;
            if(n instanceof FieldInsnNode&&n.getOpcode()==GETSTATIC){FieldInsnNode x=(FieldInsnNode)n;if(x.desc.startsWith("L")&&!x.owner.equals("java/lang"))listener=x;}
        }
        // Listener is the static object immediately before the bt constructor.
        check(ctor!=null,"Menu item constructor");for(AbstractInsnNode n:menu.instructions.toArray())if(n instanceof MethodInsnNode&&((MethodInsnNode)n).owner.equals("bt")){AbstractInsnNode p=n.getPrevious();while(p!=null&&p.getOpcode()<0)p=p.getPrevious();if(p instanceof FieldInsnNode)listener=(FieldInsnNode)p;break;}
        check(listener!=null,"Menu callback");l=code();v(l,ALOAD,4);l.add(new TypeInsnNode(NEW,"bt"));op(l,DUP);call(l,S,"label","()Ljava/lang/String;");number(l,-115);f(l,GETSTATIC,listener.owner,listener.name,listener.desc);invoke(l,INVOKESPECIAL,"bt","<init>",ctor.desc);invoke(l,INVOKEVIRTUAL,"et",append.name,append.desc);insert(menu,rowAnchor,l,true);
        MethodNode action=method(c,"onAction","(II)Z");l=code();LabelNode normal=new LabelNode();v(l,ILOAD,1);number(l,-115);l.add(new JumpInsnNode(IF_ICMPNE,normal));call(l,S,"toggle","()V");v(l,ALOAD,0);call(l,"luna/features/AutoAttackRuntime","enabled","()Z");call(l,"luna/features/AutoAttackRuntime","playersEnabled","()Z");call(l,"luna/features/AutoAttackRuntime","delayMs","()I");invoke(l,INVOKEVIRTUAL,c.name,"showMenu","(ZZI)V");op(l,ICONST_1);op(l,IRETURN);l.add(normal);insert(action,null,l,false);
    }
    static void patchCache(){
        ClassNode c=node(CACHE);c.interfaces.add(NAMES);MethodNode m=add(c,ACC_PUBLIC,"templateName","(I)Ljava/lang/String;");InsnList l=m.instructions;
        f(l,GETSTATIC,"luna/generated/HungMonsterSelectionAccess","INSTANCE","Lluna/api/MonsterSelectionAccess;");v(l,ILOAD,1);invoke(l,INVOKEINTERFACE,"luna/api/MonsterSelectionAccess","templateName","(I)Ljava/lang/String;");op(l,ARETURN);
    }
    static void generateBridge(){
        ClassNode c=new ClassNode();c.version=V1_4;c.access=ACC_PUBLIC|ACC_FINAL;c.name=B;c.superName="java/lang/Object";c.interfaces.add(A);c.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"INSTANCE","L"+A+";",null,null));
        MethodNode m=add(c,ACC_PUBLIC,"<init>","()V");InsnList l=m.instructions;v(l,ALOAD,0);invoke(l,INVOKESPECIAL,"java/lang/Object","<init>","()V");op(l,RETURN);
        m=add(c,ACC_STATIC,"<clinit>","()V");l=m.instructions;l.add(new TypeInsnNode(NEW,B));op(l,DUP);invoke(l,INVOKESPECIAL,B,"<init>","()V");f(l,PUTSTATIC,B,"INSTANCE","L"+A+";");op(l,RETURN);
        m=add(c,ACC_PUBLIC,"player","()Ljava/lang/Object;");l=m.instructions;f(l,GETSTATIC,player.owner,player.name,player.desc);op(l,ARETURN);
        m=add(c,ACC_PUBLIC,"find","(II)Ljava/lang/Object;");l=m.instructions;v(l,ILOAD,1);v(l,ILOAD,2);op(l,I2B);invoke(l,INVOKESTATIC,E,"a","(IB)L"+E+";");op(l,ARETURN);
        String[] methods={"id","type","level","hp","name"},fields={"cu","cv","bz","bs","cC"},descs={"I","B","S","I","Ljava/lang/String;"};
        for(int i=0;i<methods.length;i++){m=add(c,ACC_PUBLIC,methods[i],"(Ljava/lang/Object;)"+(i==4?"Ljava/lang/String;":"I"));l=m.instructions;v(l,ALOAD,1);l.add(new TypeInsnNode(CHECKCAST,E));f(l,GETFIELD,E,fields[i],descs[i]);op(l,i==4?ARETURN:IRETURN);}
        for(boolean dest:new boolean[]{false,true}){
            m=add(c,ACC_PUBLIC|ACC_STATIC,dest?"captureTarget":"capturePosition","(L"+E+";)V");l=m.instructions;
            for(int i=0;i<2;i++){v(l,ALOAD,0);v(l,ALOAD,0);f(l,GETFIELD,E,dest?(i==0?"bg":"bh"):(i==0?"aZ":"ba"),"I");f(l,PUTFIELD,E,i==0?"battlefieldX":"battlefieldY","I");}
            v(l,ALOAD,0);op(l,ICONST_1);f(l,PUTFIELD,E,"battlefieldPositionKnown","Z");v(l,ALOAD,0);call(l,B,"align","(L"+E+";)Z");op(l,POP);op(l,RETURN);
        }
        m=add(c,ACC_PUBLIC|ACC_STATIC,"align","(L"+E+";)Z");l=m.instructions;LabelNode no=new LabelNode();call(l,S,"enabled","()Z");l.add(new JumpInsnNode(IFEQ,no));v(l,ALOAD,0);l.add(new JumpInsnNode(IFNULL,no));v(l,ALOAD,0);f(l,GETSTATIC,player.owner,player.name,player.desc);l.add(new JumpInsnNode(IF_ACMPEQ,no));v(l,ALOAD,0);f(l,GETFIELD,E,"battlefieldPositionKnown","Z");l.add(new JumpInsnNode(IFEQ,no));
        for(String field:new String[]{"aZ","bg","ba","bh"}){v(l,ALOAD,0);v(l,ALOAD,0);f(l,GETFIELD,E,(field.equals("aZ")||field.equals("bg"))?"battlefieldX":"battlefieldY","I");f(l,PUTFIELD,E,field,"I");}
        for(String field:new String[]{"bc","bd"}){v(l,ALOAD,0);op(l,ICONST_0);f(l,PUTFIELD,E,field,"I");}op(l,ICONST_1);op(l,IRETURN);l.add(no);op(l,ICONST_0);op(l,IRETURN);
        m=add(c,ACC_PUBLIC|ACC_STATIC,"blocked","(Ljava/lang/Object;)Z");l=m.instructions;LabelNode allowed=new LabelNode();v(l,ALOAD,0);l.add(new TypeInsnNode(INSTANCEOF,E));l.add(new JumpInsnNode(IFEQ,allowed));v(l,ALOAD,0);l.add(new TypeInsnNode(CHECKCAST,E));v(l,ASTORE,1);v(l,ALOAD,1);call(l,B,"align","(L"+E+";)Z");op(l,POP);v(l,ALOAD,1);f(l,GETFIELD,E,"cv","B");l.add(new JumpInsnNode(IFNE,allowed));v(l,ALOAD,1);f(l,GETFIELD,E,"cC","Ljava/lang/String;");call(l,S,"excluded","(Ljava/lang/String;)Z");op(l,IRETURN);l.add(allowed);op(l,ICONST_0);op(l,IRETURN);
        m=add(c,ACC_PUBLIC|ACC_STATIC,"nameFont","(L"+F+";L"+E+";)L"+F+";");l=m.instructions;LabelNode ordinary=new LabelNode();v(l,ALOAD,1);call(l,B,"blocked","(Ljava/lang/Object;)Z");l.add(new JumpInsnNode(IFEQ,ordinary));f(l,GETSTATIC,red.owner,red.name,red.desc);op(l,ARETURN);l.add(ordinary);v(l,ALOAD,0);op(l,ARETURN);
        ClassWriter cw=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(cw);out.put(B+".class",cw.toByteArray());
    }
    public static void main(String[] args)throws Exception{
        check(args.length==5,"input.jar support-dir candidate.jar symbols.properties report.txt");File input=new File(args[0]),output=new File(args[2]);check(!output.exists(),"Fresh output required");check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Unexpected input hash");
        before=HungBySonExpInfoPack.read(input);out=new LinkedHashMap<String,byte[]>(before);discover();patchEntity();patchRemoteUpdates();patchPackets();patchAuto();patchCache();generateBridge();
        for(ClassNode n:nodes.values()){
            boolean changed=false;for(MethodNode m:n.methods)if(originals.containsKey(m))changed=true;
            if(n.name.equals(CACHE))changed=true;if(!changed)continue;
            ClassWriter cw=new ClassWriter(new ClassReader(before.get(n.name+".class")),ClassWriter.COMPUTE_MAXS);n.accept(cw);out.put(n.name+".class",cw.toByteArray());
        }
        // Undo every native insertion and prove all pre-existing method bodies survive exactly.
        for(MethodNode m:originals.keySet()){for(AbstractInsnNode n:inserts.get(m))m.instructions.remove(n);check(originals.get(m).equals(HungBySonExpInfoPack.canonical(m)),"Native inverse "+m.name+m.desc);}
        for(Map.Entry<String,byte[]> x:before.entrySet())if(x.getKey().endsWith(".class")){
            ClassNode old=HungBySonExpInfoPack.node(x.getValue()),now=nodes.get(old.name);if(now==null)continue;
            for(MethodNode m:old.methods)check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(now,m.name,m.desc))),"Untouched native member "+old.name+"."+m.name);
        }
        File support=new File(args[1]);Set<String> required=new LinkedHashSet<String>(Arrays.asList(S+".class",A+".class",NAMES+".class"));
        for(String entry:before.keySet())if(entry.startsWith("luna/features/MonsterCacheSupport")&&entry.endsWith(".class"))required.add(entry);
        for(String entry:required){byte[] bytes=Files.readAllBytes(new File(support,entry).toPath());HungBySonExpInfoPack.screen(entry,bytes);out.put(entry,bytes);}
        List<String> changed=new ArrayList<String>(),added=new ArrayList<String>();
        for(String name:out.keySet())if(!before.containsKey(name))added.add(name);else if(!Arrays.equals(before.get(name),out.get(name)))changed.add(name);
        for(String name:changed){check(name.endsWith(".class"),"Resources unchanged");check(HungBySonExpInfoPack.node(before.get(name)).version==HungBySonExpInfoPack.node(out.get(name)).version,"Version unchanged "+name);}
        check(added.size()==4,"Four new feature classes: "+added);
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> x:out.entrySet()){JarEntry z=new JarEntry(x.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(x.getValue());jar.closeEntry();}}finally{jar.close();}
        prop("input.sha256",INPUT);prop("output.sha256",HungBySonExpInfoPack.hash(output));prop("native.inverse","PASS");prop("changed.classes",String.join(",",changed));prop("added.classes",String.join(",",added));prop("menu.action","-115");
        OutputStream config=new FileOutputStream(args[3]);try{symbols.store(config,"Battlefield pinned incremental symbols");}finally{config.close();}
        PrintWriter report=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[4]),"UTF-8"));report.println("PASS pinned input, exact hooks, native inverse, untouched members, resource/manifest identity, CLDC screen");report.println("CHANGED="+changed);report.println("ADDED="+added);report.println("SHA256="+symbols.getProperty("output.sha256"));report.close();System.out.println("BATTLEFIELD_PATCH=PASS SHA256="+symbols.getProperty("output.sha256"));
    }
}
