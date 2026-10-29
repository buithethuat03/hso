import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keep map-reset instances distinct from same-map tombstones while reading batches. */
public final class HungMapObjectsPatcher implements Opcodes {
    static final String INPUT="721671B5C0762A85CFEF8F09F02E94CEF2111137FD30F74EBD445B4E54AEB7AB";
    static final String OWNER=n("lllllll"), ENTITY=n("liiiliii"), MESSAGE=n("lllllil"), WORLD=n("liiliii");
    static final String OLD_MAP="codexPreviousMap";
    static String n(String s){return HungBySonCopySpecialPatcher.n(s);}
    static void check(boolean value,String why){if(!value)throw new IllegalStateException(why);}
    static AbstractInsnNode next(AbstractInsnNode i){do{i=i.getNext();}while(i!=null&&i.getOpcode()<0);return i;}
    static AbstractInsnNode prev(AbstractInsnNode i){do{i=i.getPrevious();}while(i!=null&&i.getOpcode()<0);return i;}
    static List<AbstractInsnNode> insertAfter(MethodNode m,AbstractInsnNode at,InsnList code){
        List<AbstractInsnNode> nodes=Arrays.asList(code.toArray());m.instructions.insert(at,code);return nodes;
    }
    static void remove(MethodNode m,List<AbstractInsnNode> nodes){for(AbstractInsnNode i:nodes)m.instructions.remove(i);}
    static ClassNode node(Map<String,byte[]> map,String owner){return HungBySonExpInfoPack.node(map.get(owner+".class"));}
    public static void main(String[] args)throws Exception{
        check(args.length==6,"input support micro work output catalogPrefix");
        File input=new File(args[0]),output=new File(args[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned radius400 input");check(!output.exists(),"Fresh candidate required");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);

        ClassNode entity=node(before,ENTITY);
        for(FieldNode f:entity.fields)check(!f.name.equals(OLD_MAP),"Fresh previous-map marker");
        FieldNode marker=new FieldNode(ACC_PUBLIC,OLD_MAP,"Z",null,null);entity.fields.add(marker);
        out.put(ENTITY+".class",HungBySonCopySpecialPatcher.write(entity));
        entity.fields.remove(marker);HungBySonCopySpecialPatcher.inverse(node(before,ENTITY),entity,"Entity marker inverse");

        ClassNode world=node(before,WORLD);
        MethodNode reset=HungBySonCopySpecialPatcher.method(world,n("liiil"),"()V");
        List<AbstractInsnNode> resetAdded=null;int marks=0;
        for(AbstractInsnNode ins:reset.instructions.toArray())if(ins instanceof FieldInsnNode){
            FieldInsnNode f=(FieldInsnNode)ins;
            if(f.getOpcode()==PUTFIELD&&f.owner.equals(ENTITY)&&f.name.equals("cL")&&f.desc.equals("Z")){
                AbstractInsnNode one=prev(ins),actor=prev(one);
                check(one.getOpcode()==ICONST_1&&actor.getOpcode()==ALOAD,"Native map reset marks actor for removal");
                InsnList code=new InsnList();code.add(new VarInsnNode(ALOAD,((VarInsnNode)actor).var));code.add(new InsnNode(ICONST_1));
                code.add(new FieldInsnNode(PUTFIELD,ENTITY,OLD_MAP,"Z"));resetAdded=insertAfter(reset,ins,code);marks++;
            }
        }
        check(marks==1,"One map-reset mark, below native local/special actor exemptions");
        out.put(WORLD+".class",HungBySonCopySpecialPatcher.write(world));
        remove(reset,resetAdded);HungBySonCopySpecialPatcher.inverse(node(before,WORLD),world,"World reset inverse");

        ClassNode receiver=node(before,OWNER);
        MethodNode batch=HungBySonCopySpecialPatcher.method(receiver,n("li"),"(L"+MESSAGE+";)V");
        LabelNode create=null,hit=null;JumpInsnNode transitionLookup=null;int lookups=0,actorLocal=-1;
        for(AbstractInsnNode ins:batch.instructions.toArray())if(ins instanceof MethodInsnNode){
            MethodInsnNode call=(MethodInsnNode)ins;
            if(call.owner.equals(ENTITY)&&call.name.equals("a")&&call.desc.equals("(IB)L"+ENTITY+";")){
                AbstractInsnNode dup=next(ins),store=next(dup),branch=next(store);
                check(dup.getOpcode()==DUP&&store.getOpcode()==ASTORE,"Native lookup result local");
                if(branch.getOpcode()==IFNONNULL){
                    create=new LabelNode();batch.instructions.insert(branch,create);
                    hit=((JumpInsnNode)branch).label;actorLocal=((VarInsnNode)store).var;lookups++;
                }else if(branch.getOpcode()==IFNULL){check(transitionLookup==null,"One state127 lookup");transitionLookup=(JumpInsnNode)branch;}
            }
        }
        check(lookups==1&&create!=null&&hit!=null&&transitionLookup!=null,"Normal/state127 native lookup branches");
        AbstractInsnNode first=batch.instructions.getFirst();while(first.getOpcode()<0)first=first.getNext();
        check(first.getOpcode()==GOTO,"Native batch loop header");LabelNode loop=((JumpInsnNode)first).label;
        InsnList oldMap=new InsnList();oldMap.add(new VarInsnNode(ALOAD,actorLocal));oldMap.add(new FieldInsnNode(GETFIELD,ENTITY,OLD_MAP,"Z"));
        oldMap.add(new JumpInsnNode(IFNE,create));List<AbstractInsnNode> hitAdded=insertAfter(batch,hit,oldMap);
        InsnList transitionMap=new InsnList();transitionMap.add(new VarInsnNode(ALOAD,actorLocal));
        transitionMap.add(new FieldInsnNode(GETFIELD,ENTITY,OLD_MAP,"Z"));
        transitionMap.add(new JumpInsnNode(IFNE,transitionLookup.label));
        List<AbstractInsnNode> transitionAdded=insertAfter(batch,transitionLookup,transitionMap);

        List<AbstractInsnNode> oldReturns=new ArrayList<AbstractInsnNode>(),jumps=new ArrayList<AbstractInsnNode>();
        for(AbstractInsnNode ins:batch.instructions.toArray())if(ins instanceof FieldInsnNode){
            FieldInsnNode field=(FieldInsnNode)ins;
            if(field.getOpcode()==GETFIELD&&field.owner.equals(ENTITY)&&field.name.equals("cL")&&field.desc.equals("Z")){
                AbstractInsnNode branch=next(ins),ret=next(branch);
                if(branch.getOpcode()==IFEQ&&ret.getOpcode()==RETURN)oldReturns.add(ret);
            }
        }
        check(oldReturns.size()==2,"Two native departing-record premature returns");
        for(AbstractInsnNode ret:oldReturns){JumpInsnNode jump=new JumpInsnNode(GOTO,loop);batch.instructions.set(ret,jump);jumps.add(jump);}

        // State127's missing-player branch creates local8, but its native tail
        // writes ea/eb through the null lookup local7. Use the created actor.
        List<VarInsnNode> nullLoads=new ArrayList<VarInsnNode>();int placeholders=0;
        for(AbstractInsnNode ins:batch.instructions.toArray()){
            if(ins instanceof TypeInsnNode&&ins.getOpcode()==NEW&&((TypeInsnNode)ins).desc.equals(n("lillil")))placeholders++;
            if(placeholders==3&&ins instanceof FieldInsnNode&&ins.getOpcode()==PUTFIELD){
                FieldInsnNode f=(FieldInsnNode)ins;
                if(f.owner.equals(ENTITY)&&(f.name.equals("ea")||f.name.equals("eb"))){
                    AbstractInsnNode value=prev(ins),actor=prev(value);
                    check(actor.getOpcode()==ALOAD&&((VarInsnNode)actor).var==actorLocal,"Native state127 null lookup load");
                    nullLoads.add((VarInsnNode)actor);((VarInsnNode)actor).var=actorLocal+1;
                }
            }
        }
        check(placeholders==3&&nullLoads.size()==2,"State127 new-player ea/eb loads");

        // Opcode -50 is the independent full NPC-list path. A previous-map ID
        // must use its native specialized constructor, rather than unhide the
        // old instance and leave two live actors after the next packet4.
        MethodNode npcList=HungBySonCopySpecialPatcher.method(receiver,n("liill"),"(L"+MESSAGE+";)V");
        LabelNode npcCreate=null;List<AbstractInsnNode> npcAdded=null;int npcLookups=0;
        for(AbstractInsnNode ins:npcList.instructions.toArray())if(ins instanceof MethodInsnNode){
            MethodInsnNode call=(MethodInsnNode)ins;
            if(call.owner.equals(ENTITY)&&call.name.equals("a")&&call.desc.equals("(IB)L"+ENTITY+";")){
                AbstractInsnNode dup=next(ins),store=next(dup),branch=next(store);
                check(dup.getOpcode()==DUP&&store.getOpcode()==ASTORE&&branch.getOpcode()==IFNONNULL,"Native full NPC-list lookup");
                npcCreate=new LabelNode();npcList.instructions.insert(branch,npcCreate);
                InsnList code=new InsnList();code.add(new VarInsnNode(ALOAD,((VarInsnNode)store).var));
                code.add(new FieldInsnNode(GETFIELD,ENTITY,OLD_MAP,"Z"));code.add(new JumpInsnNode(IFNE,npcCreate));
                npcAdded=insertAfter(npcList,((JumpInsnNode)branch).label,code);npcLookups++;
            }
        }
        check(npcLookups==1,"One authoritative NPC-list lookup");
        out.put(OWNER+".class",HungBySonCopySpecialPatcher.write(receiver));
        // Remove every edit and compare all original methods, including all guards.
        for(VarInsnNode load:nullLoads)load.var=actorLocal;
        for(int i=0;i<jumps.size();i++)batch.instructions.set(jumps.get(i),oldReturns.get(i));
        remove(batch,hitAdded);remove(batch,transitionAdded);batch.instructions.remove(create);
        remove(npcList,npcAdded);npcList.instructions.remove(npcCreate);
        HungBySonCopySpecialPatcher.inverse(node(before,OWNER),receiver,"Object batch inverse");

        Set<String> allowed=new HashSet<String>(Arrays.asList(OWNER+".class",WORLD+".class",ENTITY+".class"));
        int classes=0,resources=0,changes=0;
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(before.get(name),out.get(name))){check(allowed.contains(name),"Only map receiver/reset/marker change");changes++;
                check(HungBySonExpInfoPack.node(before.get(name)).version==HungBySonExpInfoPack.node(out.get(name)).version,"Preserve class version");}
        }
        check(changes==3,"Three intended native class edits");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));
        try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        Properties p=new Properties();InputStream cat=new FileInputStream("build/hung-auto-pk-radius400-20261026-a/auto-pk.properties");
        try{p.load(cat);}finally{cat.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned base catalog");
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));
        p.setProperty("map.objects.previousMap.field",OLD_MAP);p.setProperty("map.objects.receiver",OWNER);
        p.setProperty("auto.pk.class.count",String.valueOf(classes));p.setProperty("auto.pk.changed.classes",OWNER+".class,"+WORLD+".class,"+ENTITY+".class");
        OutputStream cfg=new FileOutputStream(args[5]+".properties");try{p.store(cfg,"Preserve map object batches and distinguish previous-map actors");}finally{cfg.close();}
        PrintWriter log=new PrintWriter(args[5]+"-report.txt","UTF-8");
        try{log.println("PASS pinned input; marker only at native reset; native not-found factories for previous-map hits in packet4/state127/full NPC list; skip same-map cL records; fix state127 null actor tail; strict inverses; combat methods/resources/manifest unchanged");log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS map object batch fix "+p.getProperty("output.sha256"));
    }
}
