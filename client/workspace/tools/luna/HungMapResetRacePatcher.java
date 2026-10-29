import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import javax.tools.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Packet 12 clears the object-receive flag, then the native map reset walks the entity list on the
 * receiver thread while the game thread's world update removes every cL entity it sees. The list
 * shrinks under the reset, et.a(i) checks size then reads (two locks) and throws, packet 12 returns
 * before restoring the flag, and every later NPC/zone board/teleport record is dropped. The world
 * update also removes by index, so a receiver removal in between deletes a different, live actor.
 *
 * - et getter/remover/setter become one atomic Vector call; out of range is a no-op/null as before.
 * - et gains codexSnapshot / codexRemoveSame, compiled from Java with a synchronized block.
 * - Map reset marks a snapshot taken under the list lock and skips null slots.
 * - World update removes only the actor it inspected and skips a null slot instead of aborting the frame.
 */
public final class HungMapResetRacePatcher implements Opcodes {
    static final String INPUT="2EB9258B2F68805E7F90B74AEAF88DA3CAF691707500B3DF48280FCE10A33C7E";
    static final String LIST="et",VECTOR="java/util/Vector",AIOOBE="java/lang/ArrayIndexOutOfBoundsException";
    static final String WORLD=HungBySonCopySpecialPatcher.n("liiliii"),ENTITY=HungBySonCopySpecialPatcher.n("liiiliii"),
            ENTITIES=HungBySonCopySpecialPatcher.n("llillilll"),PLAYER=HungBySonCopySpecialPatcher.n("llilliill"),
            PET=HungBySonCopySpecialPatcher.n("llillilii"),RESET=HungBySonCopySpecialPatcher.n("liiil"),
            ITEMS=HungBySonCopySpecialPatcher.n("liiiiill"),REMOVE_AT=HungBySonCopySpecialPatcher.n("lilli");
    static final String SNAPSHOT="codexSnapshot",SNAPSHOT_DESC="(Ljava/util/Vector;)[Ljava/lang/Object;";
    static final String REMOVE_SAME="codexRemoveSame",REMOVE_SAME_DESC="(Ljava/util/Vector;ILjava/lang/Object;)V";
    static final String HELPER_SOURCE=
        "import java.util.Vector;\n"+
        "final class CodexListHelper {\n"+
        "    static Object[] snapshot(Vector items) {\n"+
        "        synchronized (items) { Object[] copy = new Object[items.size()]; items.copyInto(copy); return copy; }\n"+
        "    }\n"+
        "    static void removeSame(Vector items, int index, Object actor) {\n"+
        "        synchronized (items) {\n"+
        "            if (index >= 0 && index < items.size() && items.elementAt(index) == actor) items.removeElementAt(index);\n"+
        "            else if (actor != null) items.removeElement(actor);\n"+
        "        }\n"+
        "    }\n"+
        "}\n";

    static void check(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static boolean field(AbstractInsnNode x,int op,String owner,String name){
        return x instanceof FieldInsnNode&&x.getOpcode()==op&&((FieldInsnNode)x).owner.equals(owner)&&((FieldInsnNode)x).name.equals(name);
    }
    static boolean calls(MethodNode m,String owner,String name){
        for(AbstractInsnNode x:m.instructions)if(x instanceof MethodInsnNode&&((MethodInsnNode)x).owner.equals(owner)&&((MethodInsnNode)x).name.equals(name))return true;
        return false;
    }
    static int count(MethodNode m,String owner,String name){
        int n=0;for(AbstractInsnNode x:m.instructions)if(x instanceof MethodInsnNode&&((MethodInsnNode)x).owner.equals(owner)&&((MethodInsnNode)x).name.equals(name))n++;
        return n;
    }
    static MethodNode listMethodCalling(ClassNode list,String vectorCall,String desc){
        MethodNode found=null;
        for(MethodNode m:list.methods)if(m.desc.equals(desc)&&calls(m,VECTOR,vectorCall)){check(found==null,"Ambiguous et."+vectorCall);found=m;}
        check(found!=null,"Missing et wrapper for "+vectorCall);return found;
    }
    static FieldInsnNode items(){return new FieldInsnNode(GETFIELD,LIST,ITEMS,"Ljava/util/Vector;");}

    /** One Vector call; ArrayIndexOutOfBounds from a concurrent shrink becomes the native out-of-range result. */
    static void atomic(MethodNode m,String vectorCall,String vectorDesc,boolean getter,boolean withValue){
        check(calls(m,VECTOR,"size")&&calls(m,VECTOR,vectorCall),"Native range-checked et."+vectorCall);
        int index=withValue?2:1;
        m.instructions.clear();m.tryCatchBlocks.clear();m.localVariables=null;
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),outside=new LabelNode();
        InsnList c=m.instructions;
        c.add(new VarInsnNode(ILOAD,index));c.add(new JumpInsnNode(IFLT,outside));
        c.add(start);c.add(new VarInsnNode(ALOAD,0));c.add(items());
        if(withValue)c.add(new VarInsnNode(ALOAD,1));
        c.add(new VarInsnNode(ILOAD,index));c.add(new MethodInsnNode(INVOKEVIRTUAL,VECTOR,vectorCall,vectorDesc,false));
        c.add(end);c.add(new InsnNode(getter?ARETURN:RETURN));
        c.add(handler);c.add(new InsnNode(POP));
        c.add(outside);if(getter)c.add(new InsnNode(ACONST_NULL));c.add(new InsnNode(getter?ARETURN:RETURN));
        m.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,AIOOBE));
    }

    static Map<String,MethodNode> helpers(File work)throws Exception{
        File src=new File(work,"CodexListHelper.java"),out=new File(work,"helper");check(out.isDirectory()||out.mkdirs(),"Helper directory");
        Files.write(src.toPath(),HELPER_SOURCE.getBytes("UTF-8"));
        JavaCompiler javac=ToolProvider.getSystemJavaCompiler();check(javac!=null,"javac (JDK) required");
        check(javac.run(null,null,null,"-encoding","UTF-8","--release","7","-nowarn","-Xlint:-options","-g:none","-d",out.getPath(),src.getPath())==0,"Helper compile");
        ClassNode c=HungBySonExpInfoPack.node(Files.readAllBytes(new File(out,"CodexListHelper.class").toPath()));
        Map<String,MethodNode> r=new HashMap<String,MethodNode>();
        r.put(SNAPSHOT,HungBySonCopySpecialPatcher.method(c,"snapshot",SNAPSHOT_DESC));
        r.put(REMOVE_SAME,HungBySonCopySpecialPatcher.method(c,"removeSame",REMOVE_SAME_DESC));
        for(Map.Entry<String,MethodNode> e:r.entrySet()){
            MethodNode m=e.getValue();m.name=e.getKey();m.access=ACC_PUBLIC|ACC_STATIC;m.localVariables=null;
            // Target classes are major 48: drop the javac 51 stack-map frames and line numbers.
            for(Iterator<AbstractInsnNode> i=m.instructions.iterator();i.hasNext();){AbstractInsnNode x=i.next();if(x instanceof FrameNode||x instanceof LineNumberNode)i.remove();}
            for(AbstractInsnNode x:m.instructions)check(!(x instanceof MethodInsnNode)||((MethodInsnNode)x).owner.equals(VECTOR),"Helper only calls Vector");
        }
        return r;
    }

    /** Map reset: iterate a locked snapshot of the entity list and skip null slots. */
    static void patchReset(MethodNode m){
        int gets=0,sizes=0,guards=0;int slot=m.maxLocals;
        for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext()){
            AbstractInsnNode next=HungBySonCopySpecialPatcher.real(x.getNext());
            if(field(x,GETSTATIC,WORLD,ENTITIES)&&next instanceof VarInsnNode&&next.getOpcode()==ILOAD){
                AbstractInsnNode call=HungBySonCopySpecialPatcher.real(next.getNext());
                if(HungBySonCopySpecialPatcher.call(call,INVOKEVIRTUAL,LIST,"a","(I)Ljava/lang/Object;")){
                    m.instructions.set(x,new VarInsnNode(ALOAD,slot));
                    AbstractInsnNode a=new InsnNode(AALOAD);m.instructions.set(call,a);x=a;gets++;continue;
                }
            }
            if(field(x,GETSTATIC,WORLD,ENTITIES)&&HungBySonCopySpecialPatcher.call(next,INVOKEVIRTUAL,LIST,"c","()I")){
                m.instructions.set(x,new VarInsnNode(ALOAD,slot));AbstractInsnNode len=new InsnNode(ARRAYLENGTH);m.instructions.set(next,len);x=len;sizes++;continue;
            }
            // aload_1; getstatic pet; if_acmpeq skip  ->  aload_1; ifnull skip; aload_1; getstatic pet; if_acmpeq skip
            if(x instanceof VarInsnNode&&x.getOpcode()==ALOAD&&((VarInsnNode)x).var==1&&field(next,GETSTATIC,WORLD,PET)){
                AbstractInsnNode jump=HungBySonCopySpecialPatcher.real(next.getNext());
                check(jump instanceof JumpInsnNode&&jump.getOpcode()==IF_ACMPEQ,"Reset pet exemption shape");
                InsnList g=new InsnList();g.add(new VarInsnNode(ALOAD,1));g.add(new JumpInsnNode(IFNULL,((JumpInsnNode)jump).label));
                m.instructions.insertBefore(x,g);guards++;
            }
        }
        check(gets==1&&sizes==1&&guards==1,"Reset entity loop shape gets="+gets+" sizes="+sizes+" guards="+guards);
        InsnList head=new InsnList();
        head.add(new FieldInsnNode(GETSTATIC,WORLD,ENTITIES,"L"+LIST+";"));head.add(items());
        head.add(new MethodInsnNode(INVOKESTATIC,LIST,SNAPSHOT,SNAPSHOT_DESC,false));head.add(new VarInsnNode(ASTORE,slot));
        m.instructions.insert(head);m.maxLocals=slot+1;
    }

    /** World update: remove the inspected actor only; a null slot skips to the native removal branch. */
    static void patchWorldUpdate(MethodNode m){
        int removals=0,guards=0;
        for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext()){
            AbstractInsnNode next=HungBySonCopySpecialPatcher.real(x.getNext());
            if(field(x,GETSTATIC,WORLD,ENTITIES)&&next instanceof VarInsnNode&&next.getOpcode()==ILOAD&&((VarInsnNode)next).var==1){
                AbstractInsnNode call=HungBySonCopySpecialPatcher.real(next.getNext());
                if(HungBySonCopySpecialPatcher.call(call,INVOKEVIRTUAL,LIST,REMOVE_AT,"(I)V")){
                    m.instructions.insert(x,items());
                    InsnList r=new InsnList();r.add(new VarInsnNode(ALOAD,2));r.add(new MethodInsnNode(INVOKESTATIC,LIST,REMOVE_SAME,REMOVE_SAME_DESC,false));
                    m.instructions.insertBefore(call,r);m.instructions.remove(call);removals++;continue;
                }
            }
            // getstatic entities; iload_1; et.a; checkcast entity; dup; astore_2; dup; ... getfield cM; ifeq native-removal
            if(x instanceof TypeInsnNode&&x.getOpcode()==CHECKCAST&&((TypeInsnNode)x).desc.equals(ENTITY)){
                AbstractInsnNode d=HungBySonCopySpecialPatcher.real(x.getNext()),store=HungBySonCopySpecialPatcher.real(d.getNext());
                AbstractInsnNode get=HungBySonCopySpecialPatcher.back(x.getPrevious()),idx=HungBySonCopySpecialPatcher.back(get.getPrevious()),list=HungBySonCopySpecialPatcher.back(idx.getPrevious());
                AbstractInsnNode dup2=store==null?null:HungBySonCopySpecialPatcher.real(store.getNext()),hook=dup2==null?null:HungBySonCopySpecialPatcher.real(dup2.getNext());
                if(d.getOpcode()==DUP&&store instanceof VarInsnNode&&store.getOpcode()==ASTORE&&((VarInsnNode)store).var==2
                        &&HungBySonCopySpecialPatcher.call(get,INVOKEVIRTUAL,LIST,"a","(I)Ljava/lang/Object;")&&field(list,GETSTATIC,WORLD,ENTITIES)
                        &&dup2.getOpcode()==DUP&&field(hook,GETSTATIC,"luna/generated/HungMonsterCacheAccess","INSTANCE")){
                    // dup; getstatic INSTANCE; swap; invokestatic onEntityUpdate; getfield cM; ifeq native-removal
                    AbstractInsnNode y=hook;for(int k=0;k<3;k++)y=HungBySonCopySpecialPatcher.real(y.getNext());
                    check(field(y,GETFIELD,ENTITY,"cM"),"World update reads cM right after the cache hook");
                    AbstractInsnNode j=HungBySonCopySpecialPatcher.real(y.getNext());check(j.getOpcode()==IFEQ,"cM branch");
                    JumpInsnNode toRemoval=(JumpInsnNode)j;
                    AbstractInsnNode nullCheck=HungBySonCopySpecialPatcher.real(toRemoval.label.getNext()),nullJump=HungBySonCopySpecialPatcher.real(nullCheck.getNext());
                    check(nullCheck instanceof VarInsnNode&&((VarInsnNode)nullCheck).var==2&&nullJump.getOpcode()==IFNULL,"Native removal branch starts with the null check");
                    LabelNode live=new LabelNode();InsnList g=new InsnList();
                    g.add(new VarInsnNode(ALOAD,2));g.add(new JumpInsnNode(IFNONNULL,live));g.add(new InsnNode(POP));g.add(new JumpInsnNode(GOTO,toRemoval.label));g.add(live);
                    m.instructions.insert(store,g);guards++;
                }
            }
        }
        check(removals==1&&guards==1,"World update entity loop shape removals="+removals+" guards="+guards);
    }

    /** Native classes already use CLDC java.util.Vector; the edit may add no other non-CLDC reference. */
    static void screenNative(String name,byte[] old,byte[] now)throws Exception{
        check(HungBySonExpInfoPack.node(now).version==HungBySonExpInfoPack.node(old).version,"Class version kept "+name);
        String pool=new String(now,"ISO-8859-1").replace("java/util/Vector",""),prior=new String(old,"ISO-8859-1").replace("java/util/Vector","");
        for(String ref:new String[]{"java/lang/StringBuilder","java/lang/invoke/","java/util/","java/nio/","java/time/","java/awt/","java/lang/reflect/","addSuppressed"})
            check(pool.indexOf(ref)<0||prior.indexOf(ref)>=0,"New non-CLDC reference "+name+" -> "+ref);
    }

    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned loot-menu input");check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work directory");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-loot-menu-20261026-b/auto-pk.properties");
        try{p.load(cfg);}finally{cfg.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");
        StringBuilder report=new StringBuilder();

        ClassNode list=HungBySonExpInfoPack.node(before.get(LIST+".class"));
        MethodNode getter=HungBySonCopySpecialPatcher.method(list,"a","(I)Ljava/lang/Object;");
        check(listMethodCalling(list,"elementAt","(I)Ljava/lang/Object;")==getter,"Getter is et.a");
        MethodNode remover=listMethodCalling(list,"removeElementAt","(I)V");check(remover.name.equals(REMOVE_AT),"Index remover is et."+REMOVE_AT);
        MethodNode setter=listMethodCalling(list,"setElementAt","(Ljava/lang/Object;I)V");
        Set<MethodNode> listEdits=new HashSet<MethodNode>(Arrays.asList(getter,remover,setter));
        Map<String,String> listBefore=new HashMap<String,String>();
        for(MethodNode m:list.methods)if(!listEdits.contains(m))listBefore.put(m.name+m.desc,HungBySonExpInfoPack.canonical(m));
        atomic(getter,"elementAt","(I)Ljava/lang/Object;",true,false);
        atomic(remover,"removeElementAt","(I)V",false,false);
        atomic(setter,"setElementAt","(Ljava/lang/Object;I)V",false,true);
        for(MethodNode h:helpers(work).values()){check(HungBySonCopySpecialPatcher.find(list,h.name,h.desc)==null,"New et helper "+h.name);list.methods.add(h);}
        for(MethodNode m:list.methods)if(listBefore.containsKey(m.name+m.desc))check(listBefore.get(m.name+m.desc).equals(HungBySonExpInfoPack.canonical(m)),"et unchanged "+m.name);
        check(list.methods.size()==listBefore.size()+5,"et gains exactly two helpers");
        out.put(LIST+".class",HungBySonCopySpecialPatcher.write(list));
        report.append("et: getter/removeAt/setAt atomic; codexSnapshot, codexRemoveSame added; ").append(listBefore.size()).append(" methods unchanged\n");

        ClassNode world=HungBySonExpInfoPack.node(before.get(WORLD+".class"));
        MethodNode reset=HungBySonCopySpecialPatcher.method(world,RESET,"()V"),update=HungBySonCopySpecialPatcher.method(world,"a","()V");
        check(calls(reset,LIST,"a")&&calls(update,LIST,REMOVE_AT),"Pinned reset/update shapes");
        Map<String,String> worldBefore=new HashMap<String,String>();
        for(MethodNode m:world.methods)if(m!=reset&&m!=update)worldBefore.put(m.name+m.desc,HungBySonExpInfoPack.canonical(m));
        int resetMarks=0;for(AbstractInsnNode x:reset.instructions)if(field(x,PUTFIELD,ENTITY,"cL")||field(x,PUTFIELD,ENTITY,"codexPreviousMap"))resetMarks++;
        check(resetMarks==2,"Reset still sets cL and the previous-map marker");
        int indexRemovals=count(update,LIST,REMOVE_AT);
        patchReset(reset);patchWorldUpdate(update);
        check(!calls(reset,LIST,"a")&&!calls(reset,LIST,"c")&&calls(reset,LIST,SNAPSHOT),"Reset reads only the snapshot");
        check(count(update,LIST,REMOVE_AT)==indexRemovals-1&&count(update,LIST,REMOVE_SAME)==1,"World update removes entities by identity; other lists unchanged");
        for(MethodNode m:world.methods)if(worldBefore.containsKey(m.name+m.desc))check(worldBefore.get(m.name+m.desc).equals(HungBySonExpInfoPack.canonical(m)),"World unchanged "+m.name);
        out.put(WORLD+".class",HungBySonCopySpecialPatcher.write(world));
        report.append("world: reset iterates a locked snapshot, null-safe; update null guard + identity removal; ").append(worldBefore.size()).append(" methods unchanged\n");
        for(String name:new String[]{LIST,WORLD})screenNative(name,before.get(name+".class"),out.get(name+".class"));

        int classes=0,resources=0;List<String> changed=new ArrayList<String>();
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(before.get(name),out.get(name)))changed.add(name);
        }
        Collections.sort(changed);List<String> expected=new ArrayList<String>(Arrays.asList(LIST+".class",WORLD+".class"));Collections.sort(expected);
        check(changed.equals(expected),"Two intended class edits: "+changed);
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        String changedList=LIST+".class,"+WORLD+".class";
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("map.reset.race","true");
        p.setProperty("auto.pk.class.count",String.valueOf(classes));p.setProperty("auto.pk.changed.classes",changedList);
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Map reset no longer races the world update");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{log.print(report);log.println("PASS pinned input; only et and world reset/update changed; resources/manifest unchanged");log.println("CHANGED="+changedList);log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS map reset race "+p.getProperty("output.sha256"));
    }
}
