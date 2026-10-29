import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Real-time local cast pacing, retaining the native send frame while it is waiting. */
public final class HungBySonRealTimeCombatPatcher implements Opcodes {
    static final String INPUT = "2AF569F85FE379BD18BF770C55799CE95CAA25796F60CC13B0E36AAFA42B92E2";
    static int WAIT = 550;
    static int BEFORE_AUTO = 600;
    static final String PENDING = "codexWaitRealTime";
    public static void main(String[] args) throws Exception {
        check(args.length==4||args.length==7,"input.jar combat.properties new-output.jar new-catalog.properties [expected-sha before-auto-ms minimum-ms]");
        File input=new File(args[0]), output=new File(args[2]), config=new File(args[3]);
        String expected=args.length==7?args[4]:INPUT;
        if(args.length==7){BEFORE_AUTO=Integer.parseInt(args[5]);WAIT=Integer.parseInt(args[6]);}
        check(BEFORE_AUTO>0&&WAIT>0&&WAIT<=32767,"Invalid timing bounds");
        check(HungBySonNativeTimingPatcher.hash(input).equals(expected),"Unexpected current source JAR");
        check(!output.exists()&&!config.exists(),"Outputs must be new");
        Properties p=new Properties();InputStream in=new FileInputStream(args[1]);try{p.load(in);}finally{in.close();}
        Map<String,Long> times=new HashMap<String,Long>();
        LinkedHashMap<String,byte[]> before=HungBySonNativeTimingPatcher.readJar(input,times);
        String owner=prop(p,"cast.owner"), entry=owner+".class";
        check(owner.equals(prop(p,"request.owner")),"Expected shared native combat boundary");
        byte[] original=before.get(entry);ClassNode c=cls(original);
        for(FieldNode f:c.fields)check(!f.name.equals(PENDING),"Pending flag already exists");
        c.fields.add(new FieldNode(ACC_PUBLIC,PENDING,"Z",null,null));
        IntInsnNode auto=HungBySonNativeTimingPatcher.initializer(c,p);check(auto.operand==BEFORE_AUTO,"Unexpected source auto gate");auto.operand=WAIT;
        MethodNode request=method(c,prop(p,"request.method"),prop(p,"request.descriptor"));
        InsnList reset=new InsnList();reset.add(new VarInsnNode(ALOAD,0));reset.add(new InsnNode(ICONST_0));reset.add(new FieldInsnNode(PUTFIELD,owner,PENDING,"Z"));request.instructions.insert(reset);
        FieldInsnNode delay=HungBySonNativeTimingPatcher.threshold(request,p);
        AbstractInsnNode convert=next(delay), compare=next(convert), jump=next(compare);
        check(convert.getOpcode()==I2L&&compare.getOpcode()==LCMP&&jump.getOpcode()==IFLE,"Native interval comparison changed");
        JumpInsnNode local=localJump(request,p);check(local.label==((JumpInsnNode)jump).label,"Native local/time skip targets differ");
        int clocks=0;
        for(AbstractInsnNode i:request.instructions.toArray())if(field(i,GETSTATIC,prop(p,"request.now.owner"),prop(p,"request.now.field"),"J")){
            request.instructions.set(i,new MethodInsnNode(INVOKESTATIC,"java/lang/System","currentTimeMillis","()J",false));clocks++;
        }
        check(clocks==2,"Expected comparison and accepted-request timestamp clock reads");
        // Larger skill-provided request intervals remain respected, without narrowing to short.
        InsnList minimum=new InsnList();minimum.add(new IntInsnNode(SIPUSH,WAIT));
        minimum.add(new MethodInsnNode(INVOKESTATIC,"java/lang/Math","max","(II)I",false));request.instructions.insert(delay,minimum);
        LabelNode deferred=new LabelNode();((JumpInsnNode)jump).label=deferred;
        // Leave the actual frame unchanged. Its caller skips animation advance only while waiting.
        request.instructions.add(deferred);request.instructions.add(new VarInsnNode(ALOAD,0));
        request.instructions.add(new InsnNode(ICONST_1));request.instructions.add(new FieldInsnNode(PUTFIELD,owner,PENDING,"Z"));request.instructions.add(new InsnNode(RETURN));
        check(fieldDeclared(before,prop(p,"entity.owner"),"cQ","I"),"Missing exact native animation frame field");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);byte[] changed=writer.toByteArray();
        inverse(original,changed,p);
        LinkedHashMap<String,byte[]> after=new LinkedHashMap<String,byte[]>(before);after.put(entry,changed);
        String entityOwner=prop(p,"entity.owner"),entityEntry=entityOwner+".class";ClassNode entity=cls(before.get(entityEntry));
        MethodNode step=method(entity,"J","()V");MethodInsnNode requestCall=null;
        for(AbstractInsnNode i:step.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode m=(MethodInsnNode)i;if(m.owner.equals(owner)&&m.name.equals(request.name)&&m.desc.equals(request.desc)){check(requestCall==null,"Ambiguous entity request callback");requestCall=m;}}
        check(requestCall!=null,"Missing exact native entity request callback");
        AbstractInsnNode castReceiver=previous(requestCall),actorLoad=previous(castReceiver);
        check(actorLoad instanceof VarInsnNode&&actorLoad.getOpcode()==ALOAD&&((VarInsnNode)actorLoad).var==0
            &&castReceiver instanceof FieldInsnNode&&castReceiver.getOpcode()==GETFIELD&&((FieldInsnNode)castReceiver).owner.equals(entityOwner)&&((FieldInsnNode)castReceiver).desc.equals("L"+owner+";"),"Native callback receiver shape");
        AbstractInsnNode first=next(requestCall),animationReceiver=next(first),animationActor=next(animationReceiver),advance=next(animationActor);
        check(first instanceof VarInsnNode&&first.getOpcode()==ALOAD&&((VarInsnNode)first).var==0&&animationReceiver instanceof FieldInsnNode&&animationReceiver.getOpcode()==GETFIELD
            &&animationActor instanceof VarInsnNode&&animationActor.getOpcode()==ALOAD&&((VarInsnNode)animationActor).var==0
            &&advance instanceof MethodInsnNode&&advance.getOpcode()==INVOKEVIRTUAL&&((MethodInsnNode)advance).desc.equals("(L"+entityOwner+";)V"),"Native request/animation ordering changed");
        check(advance.getNext() instanceof LabelNode,"Missing native post-animation label");LabelNode resumed=(LabelNode)advance.getNext();
        InsnList hold=new InsnList();hold.add(new VarInsnNode(ALOAD,0));hold.add(castReceiver.clone(new HashMap<LabelNode,LabelNode>()));hold.add(new FieldInsnNode(GETFIELD,owner,PENDING,"Z"));hold.add(new JumpInsnNode(IFNE,resumed));step.instructions.insert(requestCall,hold);
        ClassWriter ew=new ClassWriter(ClassWriter.COMPUTE_MAXS);entity.accept(ew);byte[] entityChanged=ew.toByteArray();
        inverseEntity(before.get(entityEntry),entityChanged,p);after.put(entityEntry,entityChanged);
        p.setProperty("request.caller.owner",entityOwner);p.setProperty("request.caller.method","J");p.setProperty("request.caller.descriptor","()V");
        p.setProperty("request.controller.field",((FieldInsnNode)castReceiver).name);p.setProperty("request.controller.descriptor",((FieldInsnNode)castReceiver).desc);
        p.setProperty("request.animation.owner",((MethodInsnNode)advance).owner);p.setProperty("request.animation.method",((MethodInsnNode)advance).name);p.setProperty("request.animation.descriptor",((MethodInsnNode)advance).desc);
        output.getAbsoluteFile().getParentFile().mkdirs();JarOutputStream out=new JarOutputStream(new FileOutputStream(output));
        try{for(Map.Entry<String,byte[]> e:after.entrySet()){JarEntry z=new JarEntry(e.getKey());if(times.get(e.getKey())>=0)z.setTime(times.get(e.getKey()));out.putNextEntry(z);out.write(e.getValue());out.closeEntry();}}finally{out.close();}
        Map<String,byte[]> saved=HungBySonNativeTimingPatcher.readJar(output,new HashMap<String,Long>());check(saved.keySet().equals(before.keySet()),"Archive topology changed");
        int modified=0;for(String n:before.keySet()){check(Arrays.equals(after.get(n),saved.get(n)),"Saved entry differs");if(!Arrays.equals(before.get(n),saved.get(n))){check(n.equals(entry)||n.equals(entityEntry),"Unrelated entry changed");check(cls(before.get(n)).version==cls(saved.get(n)).version,"Class version changed");modified++;}}
        check(modified==2,"Class scope changed");
        p.setProperty("input.sha256",expected);p.setProperty("output.sha256",HungBySonNativeTimingPatcher.hash(output));
        p.setProperty("changed.classes",owner+","+entityOwner);p.setProperty("auto.delay.before.ms",String.valueOf(BEFORE_AUTO));p.setProperty("auto.delay.after.ms",String.valueOf(WAIT));
        p.setProperty("request.minimum.ms",String.valueOf(WAIT));p.setProperty("request.extra.ms","0");p.setProperty("request.clock","System.currentTimeMillis");
        p.setProperty("request.defer.frame.field","cQ");p.setProperty("request.defer.flag.field",PENDING);p.setProperty("request.defer.flag.descriptor","Z");p.setProperty("timing.mode","real-time-pending-frame");
        OutputStream props=new FileOutputStream(config);try{p.store(props,"Real-time local cast, pending send-frame retry, native skill intervals retained");}finally{props.close();}
        System.out.println("AUTO="+WAIT+"ms real; REQUEST=strict >max("+WAIT+",nativeInterval), direct real clock");
        System.out.println("DEFER=hold local send frame until ready; no metadata/cooldown/send when waiting");
        System.out.println("SCOPE=two classes, auto/request plus guarded animation advance; independent inverse canonical PASS");
        System.out.println("OUTPUT_SHA256="+HungBySonNativeTimingPatcher.hash(output));
    }
    static void inverse(byte[] original,byte[] changed,Properties p){
        ClassNode old=cls(original), normalized=cls(changed);HungBySonNativeTimingPatcher.initializer(normalized,p).operand=BEFORE_AUTO;
        int flags=0;for(Iterator<FieldNode> it=normalized.fields.iterator();it.hasNext();){FieldNode f=it.next();if(f.name.equals(PENDING)){check(f.access==ACC_PUBLIC&&f.desc.equals("Z"),"Pending field shape");it.remove();flags++;}}check(flags==1,"Pending field count");
        MethodNode r=method(normalized,prop(p,"request.method"),prop(p,"request.descriptor"));
        List<AbstractInsnNode> prefix=new ArrayList<AbstractInsnNode>();for(AbstractInsnNode i:r.instructions.toArray())if(i.getOpcode()>=0)prefix.add(i);
        check(prefix.get(0) instanceof VarInsnNode&&prefix.get(0).getOpcode()==ALOAD&&((VarInsnNode)prefix.get(0)).var==0&&prefix.get(1).getOpcode()==ICONST_0&&field(prefix.get(2),PUTFIELD,prop(p,"cast.owner"),PENDING,"Z"),"Pending reset prefix");
        for(int i=0;i<3;i++)r.instructions.remove(prefix.get(i));
        FieldInsnNode delay=HungBySonNativeTimingPatcher.threshold(r,p);AbstractInsnNode amount=next(delay), max=next(amount);
        check(amount instanceof IntInsnNode&&((IntInsnNode)amount).operand==WAIT&&call(max,"java/lang/Math","max","(II)I"),"Minimum gate shape");
        r.instructions.remove(amount);r.instructions.remove(max);
        JumpInsnNode gate=(JumpInsnNode)next(next(next(delay)));LabelNode deferred=gate.label;gate.label=localJump(r,p).label;
        List<AbstractInsnNode> block=new ArrayList<AbstractInsnNode>();for(AbstractInsnNode i=deferred;i!=null;i=i.getNext())if(i.getOpcode()>=0)block.add(i);
        check(block.size()==4&&block.get(0) instanceof VarInsnNode&&block.get(0).getOpcode()==ALOAD&&((VarInsnNode)block.get(0)).var==0
            &&block.get(1).getOpcode()==ICONST_1&&field(block.get(2),PUTFIELD,prop(p,"cast.owner"),PENDING,"Z")&&block.get(3).getOpcode()==RETURN,"Deferred pending block differs");
        for(AbstractInsnNode i=deferred;i!=null;){AbstractInsnNode following=i.getNext();r.instructions.remove(i);i=following;}
        int clocks=0;for(AbstractInsnNode i:r.instructions.toArray())if(call(i,"java/lang/System","currentTimeMillis","()J")){
            r.instructions.set(i,new FieldInsnNode(GETSTATIC,prop(p,"request.now.owner"),prop(p,"request.now.field"),"J"));clocks++;
        }
        check(clocks==2,"Unexpected clock substitutions");MethodNode prior=method(old,r.name,r.desc);r.maxStack=prior.maxStack;r.maxLocals=prior.maxLocals;
        check(Arrays.equals(HungBySonNativeTimingPatcher.canonical(old),HungBySonNativeTimingPatcher.canonical(normalized)),"Unrelated member/instruction change");
    }
    static void inverseEntity(byte[] original,byte[] changed,Properties p){
        ClassNode old=cls(original), normalized=cls(changed);MethodNode step=method(normalized,"J","()V");
        int guards=0;for(AbstractInsnNode i:step.instructions.toArray())if(field(i,GETFIELD,prop(p,"cast.owner"),PENDING,"Z")){
            AbstractInsnNode receiver=previous(i),actor=previous(receiver),branch=next(i);
            check(actor instanceof VarInsnNode&&actor.getOpcode()==ALOAD&&((VarInsnNode)actor).var==0&&receiver instanceof FieldInsnNode&&receiver.getOpcode()==GETFIELD
                &&((FieldInsnNode)receiver).owner.equals(prop(p,"entity.owner"))&&((FieldInsnNode)receiver).desc.equals("L"+prop(p,"cast.owner")+";")&&branch instanceof JumpInsnNode&&branch.getOpcode()==IFNE,"Pending animation guard shape");
            LabelNode end=((JumpInsnNode)branch).label;AbstractInsnNode advance=previous(end);
            check(advance instanceof MethodInsnNode&&advance.getOpcode()==INVOKEVIRTUAL&&((MethodInsnNode)advance).desc.equals("(L"+prop(p,"entity.owner")+";)V"),"Pending guard skip target");
            step.instructions.remove(actor);step.instructions.remove(receiver);step.instructions.remove(i);step.instructions.remove(branch);guards++;
        }
        check(guards==1,"Expected one pending animation guard");MethodNode prior=method(old,step.name,step.desc);step.maxStack=prior.maxStack;step.maxLocals=prior.maxLocals;
        check(Arrays.equals(HungBySonNativeTimingPatcher.canonical(old),HungBySonNativeTimingPatcher.canonical(normalized)),"Unrelated entity instruction/member change");
    }
    static JumpInsnNode localJump(MethodNode r,Properties p){JumpInsnNode result=null;
        for(AbstractInsnNode i:r.instructions.toArray())if(i instanceof JumpInsnNode&&i.getOpcode()==IF_ACMPNE){AbstractInsnNode prior=previous(i);
            if(field(prior,GETSTATIC,prop(p,"player.singleton.owner"),prop(p,"player.singleton.field"),prop(p,"player.singleton.descriptor"))){check(result==null,"Ambiguous local actor branch");result=(JumpInsnNode)i;}}
        check(result!=null,"Missing native local actor branch");return result;
    }
    static boolean fieldDeclared(Map<String,byte[]> jar,String o,String n,String d){for(FieldNode f:cls(jar.get(o+".class")).fields)if(f.name.equals(n)&&f.desc.equals(d))return true;return false;}
    static String prop(Properties p,String k){return HungBySonNativeTimingPatcher.property(p,k);}
    static ClassNode cls(byte[] b){return HungBySonNativeTimingPatcher.cls(b);}
    static MethodNode method(ClassNode c,String n,String d){return HungBySonNativeTimingPatcher.method(c,n,d);}
    static AbstractInsnNode next(AbstractInsnNode i){return HungBySonNativeTimingPatcher.next(i);}
    static AbstractInsnNode previous(AbstractInsnNode i){return HungBySonNativeTimingPatcher.previous(i);}
    static boolean field(AbstractInsnNode i,int op,String o,String n,String d){return HungBySonNativeTimingPatcher.field(i,op,o,n,d);}
    static boolean call(AbstractInsnNode i,String o,String n,String d){return i instanceof MethodInsnNode&&i.getOpcode()==INVOKESTATIC&&((MethodInsnNode)i).owner.equals(o)&&((MethodInsnNode)i).name.equals(n)&&((MethodInsnNode)i).desc.equals(d);}
    static void check(boolean b,String m){HungBySonNativeTimingPatcher.check(b,m);}
}
