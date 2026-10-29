import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Incremental Hung-only PK profile. Native serializers, pacing and other features stay pinned. */
public final class HungBySonAutoPkPatcher implements Opcodes {
    static final String INPUT="E8A95AEBF84055D9137276792C7BD4D6E2468B21FF51A8B3C48E57CD8ACC2CFA";
    static final String PORT="HungBySonModUpLv", BRIDGE="luna/generated/HungAutoAttackAccess";
    static final String CTRL="luna/features/AutoAttackController", API="luna/api/AutoPkPriorityAccess";
    static String n(String tail){return HungBySonCopySpecialPatcher.n(tail);}
    static void check(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static MethodNode method(ClassNode c,String name,String desc){return HungBySonCopySpecialPatcher.method(c,name,desc);}
    static ClassNode node(Map<String,byte[]> map,String name){return HungBySonExpInfoPack.node(map.get(name+".class"));}
    static byte[] write(ClassNode c){return HungBySonCopySpecialPatcher.write(c);}
    static void call(InsnList l,String owner,String name,String desc){l.add(new MethodInsnNode(INVOKESTATIC,owner,name,desc,false));}
    static void transplant(ClassNode before,ClassNode after,Set<String> allowed){
        for(MethodNode m:before.methods)if(!allowed.contains(m.name+m.desc))
            check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(after,m.name,m.desc))),"Unchanged "+before.name+"."+m.name+m.desc);
    }
    static byte[] support(File root,String name,int version)throws Exception{
        ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(new File(root,name+".class").toPath())).accept(c,version>=V1_6?0:ClassReader.SKIP_FRAMES);
        c.version=version;byte[] bytes=write(c);HungBySonExpInfoPack.screen(name+".class",bytes);return bytes;
    }
    static void menu(ClassNode bridge){
        MethodNode menu=method(bridge,"showMenu","(ZZI)V");
        int renamed=0,removed=0;
        for(AbstractInsnNode x:menu.instructions.toArray()){
            if(x instanceof LdcInsnNode&&((LdcInsnNode)x).cst instanceof String){
                String label=(String)((LdcInsnNode)x).cst;
                if(label.equals("Auto quái + PK")||label.equals("Auto quái + PK [x]")){
                    ((LdcInsnNode)x).cst="Auto PK"+label.substring("Auto quái + PK".length());renamed++;
                }
            }
            if(!(x instanceof MethodInsnNode)||!((MethodInsnNode)x).owner.equals("luna/features/AutoAttackRuntime")||!((MethodInsnNode)x).name.equals("normalLabel"))continue;
            AbstractInsnNode start=x;
            while(start!=null&&!(start instanceof VarInsnNode&&start.getOpcode()==ALOAD&&((VarInsnNode)start).var==4))start=start.getPrevious();
            AbstractInsnNode end=x;
            while(end!=null&&!(end instanceof MethodInsnNode&&((MethodInsnNode)end).owner.equals("et")&&((MethodInsnNode)end).desc.equals("(Ljava/lang/Object;)V")))end=end.getNext();
            check(start!=null&&end!=null,"Exact legacy monster-mode row");
            for(AbstractInsnNode y=start;y!=null;){AbstractInsnNode next=y.getNext();menu.instructions.remove(y);if(y==end)break;y=next;}removed++;
        }
        check(renamed==2&&removed==1,"Both PK labels / removed row");
    }
    static void targetGate(ClassNode bridge){
        MethodNode valid=method(bridge,"validTarget","(Ljava/lang/Object;Z)Z");
        InsnList gate=new InsnList();LabelNode nativeGate=new LabelNode();
        gate.add(new VarInsnNode(ALOAD,1));gate.add(new VarInsnNode(ILOAD,2));call(gate,PORT,"validPkMode","(Ljava/lang/Object;Z)Z");
        gate.add(new JumpInsnNode(IFNE,nativeGate));gate.add(new InsnNode(ICONST_0));gate.add(new InsnNode(IRETURN));gate.add(nativeGate);
        LabelNode afterBattlefield=null;
        for(AbstractInsnNode x:valid.instructions)if(x instanceof MethodInsnNode&&((MethodInsnNode)x).owner.equals("luna/generated/BattlefieldBridge")&&((MethodInsnNode)x).name.equals("blocked")){
            AbstractInsnNode branch=x.getNext();while(branch!=null&&branch.getOpcode()<0)branch=branch.getNext();
            check(branch instanceof JumpInsnNode&&branch.getOpcode()==IFEQ,"Battlefield eligibility prelude");
            check(afterBattlefield==null,"One battlefield prelude");afterBattlefield=((JumpInsnNode)branch).label;
        }
        check(afterBattlefield!=null,"Battlefield alignment before PK geometry");valid.instructions.insert(afterBattlefield,gate);
        bridge.interfaces.add(API);
        MethodNode priority=new MethodNode(ACC_PUBLIC,"priorityRank","(Ljava/lang/Object;)I",null,null);
        priority.instructions.add(new VarInsnNode(ALOAD,1));call(priority.instructions,PORT,"priorityRank","(Ljava/lang/Object;)I");priority.instructions.add(new InsnNode(IRETURN));bridge.methods.add(priority);
    }
    static void pkSerializer(Map<String,byte[]> before,ClassNode port,Properties p){
        ClassNode cast=node(before,p.getProperty("cast.owner"));
        MethodNode direct=method(cast,"codexSpecialSend","()V");
        MethodInsnNode getter=null,sender=null;int senders=0;
        for(AbstractInsnNode x:direct.instructions.toArray())if(x instanceof MethodInsnNode){
            MethodInsnNode c=(MethodInsnNode)x;
            if(c.getOpcode()==INVOKESTATIC&&c.desc.equals("()L"+n("lilillll")+";"))getter=c;
            if(c.owner.equals(n("lilillll"))&&c.desc.equals("(Let;B)V")){sender=c;senders++;}
        }
        check(getter!=null&&sender!=null&&senders==2,"Pinned direct op9 then op6 sender");
        MethodNode nativeSender=method(node(before,sender.owner),sender.name,sender.desc);
        boolean op6=false;
        for(AbstractInsnNode x:nativeSender.instructions)if(HungAutoAttackNativeBuilder.integer(x)==6)op6=true;
        check(op6,"PK serializer contains opcode 6");
        MethodNode send=method(port,"sendPlayers","(Let;I)V");send.instructions.clear();send.tryCatchBlocks.clear();send.localVariables=null;
        send.instructions.add(new MethodInsnNode(getter.getOpcode(),getter.owner,getter.name,getter.desc,getter.itf));
        send.instructions.add(new VarInsnNode(ALOAD,0));send.instructions.add(new VarInsnNode(ILOAD,1));send.instructions.add(new InsnNode(I2B));
        send.instructions.add(new MethodInsnNode(sender.getOpcode(),sender.owner,sender.name,sender.desc,sender.itf));send.instructions.add(new InsnNode(RETURN));
        p.setProperty("auto.pk.sender.owner",sender.owner);p.setProperty("auto.pk.sender.method",sender.name);p.setProperty("auto.pk.sender.descriptor",sender.desc);
    }
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),support=new File(a[1]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned current Hung x1 input");check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work directory");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-sale-scroll-farm-match-20261025-a/auto-attack.properties");try{p.load(cfg);}finally{cfg.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");
        Map<String,byte[]> compiled=HungBySonCopySpecialPatcher.compile(new File[]{new File("tools/luna/HungBySonModUpLv.java.in"),new File("tools/luna/HungBySonSkillStats.java.in")},work,input,support,a[2],false);
        check(Arrays.equals(compiled.get("HungBySonSkillStats.class"),before.get("HungBySonSkillStats.class")),"Skill stats and pacing hooks unchanged");
        ClassNode oldPort=node(before,PORT),port=HungBySonExpInfoPack.node(compiled.get(PORT+".class"));
        transplant(oldPort,port,new HashSet<String>(Arrays.asList("<clinit>()V","frame()V","attackFrame(L"+n("lliiil")+";)Z","setMaze(Z)V")));
        pkSerializer(before,port,p);out.put(PORT+".class",write(port));
        ClassNode oldController=node(before,CTRL),controller=HungBySonExpInfoPack.node(support(support,CTRL,oldController.version));
        transplant(oldController,controller,new HashSet<String>(Arrays.asList("statusText()Ljava/lang/String;","tick()V","findTarget()Ljava/lang/Object;")));
        out.put(CTRL+".class",write(controller));
        for(String name:new String[]{API,"luna/features/AutoPkTargetPolicy"})out.put(name+".class",support(support,name,V1_4));
        ClassNode bridge=node(before,BRIDGE);menu(bridge);targetGate(bridge);out.put(BRIDGE+".class",write(bridge));
        p.setProperty("graphics.owner",n("llilll"));
        HungAutoPkRangeHook.apply(before,out,p);
        Set<String> allowed=new HashSet<String>(Arrays.asList(PORT+".class",CTRL+".class",BRIDGE+".class",API+".class","luna/features/AutoPkTargetPolicy.class",n("liiliii")+".class"));
        int classes=0,resources=0;List<String> changed=new ArrayList<String>();
        for(Map.Entry<String,byte[]> e:out.entrySet()){
            if(e.getKey().endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(e.getValue(),before.get(e.getKey()))){check(allowed.remove(e.getKey()),"Unexpected change "+e.getKey());changed.add(e.getKey());
                // The pinned native world already contains desktop references. Its two inserted
                // calls are checked by structural inverse; screen all newly compiled boundaries.
                if(!e.getKey().equals(n("liiliii")+".class"))HungBySonExpInfoPack.screen(e.getKey(),e.getValue());
                if(before.containsKey(e.getKey()))check(node(before,e.getKey().substring(0,e.getKey().length()-6)).version==HungBySonExpInfoPack.node(e.getValue()).version,"Preserved class version");}
        }
        check(allowed.isEmpty(),"Every intended change applied "+allowed);
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("auto.pk.upgrade","true");p.setProperty("auto.pk.preview.selected","true");p.setProperty("auto.pk.radius","400");p.setProperty("auto.pk.limit","20");p.setProperty("auto.pk.class.count",String.valueOf(classes));p.setProperty("auto.pk.changed.classes",String.join(",",changed));p.remove("normal.monsters.action");
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Hung-only PK upgrade, pinned native boundary");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{log.println("PASS pinned input, unchanged methods outside allow-list, native opcode-6 invocation, no serializer/stats/timing edits, manifest/resource identity, CLDC/version screen");log.println("CHANGED="+changed);log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS Auto PK candidate "+p.getProperty("output.sha256"));
    }
}
