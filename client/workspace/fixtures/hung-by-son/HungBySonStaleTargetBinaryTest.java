import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Actual native serializers and unseen combat receiver; no live connection. */
public final class HungBySonStaleTargetBinaryTest implements Opcodes {
    static final String BRIDGE="luna/generated/HungMonsterCacheAccess", SUPPORT="luna/features/MonsterCacheSupport", WATCH="luna/features/MonsterTargetWatchSupport";
    static int checks;
    static void require(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static String n(String t){return HungBySonMonsterRecoveryBinaryTest.n(t);}
    static AbstractInsnNode prev(AbstractInsnNode i){do{i=i.getPrevious();}while(i!=null&&i.getOpcode()<0);return i;}
    static void remove(MethodNode m,AbstractInsnNode first,AbstractInsnNode last){for(AbstractInsnNode i=first;;){require(i!=null,"Inverse range");AbstractInsnNode next=i.getNext();m.instructions.remove(i);if(i==last)return;i=next;}}
    /** Independent fixture normalization: each removed node has a pinned shape. */
    static boolean inverseHook(MethodNode m,MethodInsnNode c){
        if(c.owner.equals(BRIDGE)&&c.name.equals("attackVector")){
            AbstractInsnNode arg=prev(c),branch=prev(arg),load=prev(branch);
            require(c.desc.equals("(Ljava/lang/Object;)V")&&arg instanceof VarInsnNode&&arg.getOpcode()==ALOAD&&((VarInsnNode)arg).var==1,"Attack vector argument");
            require(branch instanceof JumpInsnNode&&branch.getOpcode()==IFEQ&&load instanceof VarInsnNode&&load.getOpcode()==ILOAD,"Attack complete-serialization guard");
            int flag=((VarInsnNode)load).var;LabelNode end=((JumpInsnNode)branch).label;require(c.getNext()==end,"Fresh attack guard label");remove(m,load,end);
            int zero=0,one=0;for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof VarInsnNode&&i.getOpcode()==ISTORE&&((VarInsnNode)i).var==flag){AbstractInsnNode v=prev(i);if(v.getOpcode()==ICONST_0)zero++;else if(v.getOpcode()==ICONST_1)one++;else throw new AssertionError("Unexpected flag store");remove(m,v,i);}require(zero==1&&one==1,"Flag initialized and marked exactly once");return true;
        }
        if(c.owner.equals(SUPPORT)&&c.name.equals("onCombatSnapshot")){
            AbstractInsnNode getter=prev(c),arg=prev(getter),access=prev(arg),branch=prev(access),one=prev(branch),type=prev(one);
            require(getter instanceof MethodInsnNode&&((MethodInsnNode)getter).desc.equals("()Ljava/io/DataInputStream;")&&arg instanceof VarInsnNode&&arg.getOpcode()==ALOAD&&((VarInsnNode)arg).var==0,"Unseen stream argument");
            require(access instanceof FieldInsnNode&&access.getOpcode()==GETSTATIC&&((FieldInsnNode)access).owner.equals(BRIDGE)&&((FieldInsnNode)access).name.equals("INSTANCE"),"Native cache access argument");
            require(branch instanceof JumpInsnNode&&branch.getOpcode()==IF_ICMPNE&&one.getOpcode()==ICONST_1&&type instanceof VarInsnNode&&type.getOpcode()==ILOAD&&((VarInsnNode)type).var==2,"Monster-only receive guard");LabelNode end=((JumpInsnNode)branch).label;require(c.getNext()==end,"Fresh receive guard label");remove(m,type,end);return true;
        }
        return false;
    }
    static byte[] spy(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(V1_4,ACC_PUBLIC,WATCH,null,"java/lang/Object",null);
        for(String f:new String[]{"attacks","id","sends","failSend"})w.visitField(ACC_PUBLIC|ACC_STATIC,f,"I",null,null).visitEnd();
        MethodVisitor m=w.visitMethod(ACC_PUBLIC|ACC_STATIC,"onAttack","(Lluna/api/MonsterCacheAccess;I)V",null,null);m.visitCode();m.visitFieldInsn(GETSTATIC,WATCH,"attacks","I");m.visitInsn(ICONST_1);m.visitInsn(IADD);m.visitFieldInsn(PUTSTATIC,WATCH,"attacks","I");m.visitVarInsn(ILOAD,1);m.visitFieldInsn(PUTSTATIC,WATCH,"id","I");m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();
        for(String name:new String[]{"onMap","tick"}){m=w.visitMethod(ACC_PUBLIC|ACC_STATIC,name,"(Lluna/api/MonsterCacheAccess;)V",null,null);m.visitCode();m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();}
        for(String name:new String[]{"onHpProgress","onRevived"}){m=w.visitMethod(ACC_PUBLIC|ACC_STATIC,name,"(Lluna/api/MonsterCacheAccess;ILjava/lang/Object;I)V",null,null);m.visitCode();m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();}
        m=w.visitMethod(ACC_PUBLIC|ACC_STATIC,"onDetail","(Lluna/api/MonsterCacheAccess;ILjava/lang/Object;ZI)V",null,null);m.visitCode();m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    static void stubEnqueue(Map<String,byte[]> jar){
        String owner=n("lllilii");ClassNode c=HungBySonMonsterRecoveryBinaryTest.node(jar.get(owner+".class"));
        for(MethodNode m:c.methods)if(m.name.equals(n("lliiiii"))&&m.desc.equals("()V")){
            m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();LabelNode ok=new LabelNode();m.instructions.add(new FieldInsnNode(GETSTATIC,WATCH,"failSend","I"));m.instructions.add(new JumpInsnNode(IFEQ,ok));m.instructions.add(new TypeInsnNode(NEW,"java/lang/IllegalStateException"));m.instructions.add(new InsnNode(DUP));m.instructions.add(new MethodInsnNode(INVOKESPECIAL,"java/lang/IllegalStateException","<init>","()V",false));m.instructions.add(new InsnNode(ATHROW));m.instructions.add(ok);m.instructions.add(new FieldInsnNode(GETSTATIC,WATCH,"sends","I"));m.instructions.add(new InsnNode(ICONST_1));m.instructions.add(new InsnNode(IADD));m.instructions.add(new FieldInsnNode(PUTSTATIC,WATCH,"sends","I"));m.instructions.add(new InsnNode(RETURN));m.maxStack=2;m.maxLocals=1;
        }jar.put(owner+".class",HungBySonMonsterRecoveryBinaryTest.bytes(c));jar.put(WATCH+".class",spy());
    }
    static byte[] combat(int id,int hp,boolean effects)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.writeShort(777);d.writeByte(0);d.writeByte(1);d.writeShort(id);d.writeInt(7);d.writeInt(hp);d.writeByte(effects?2:0);if(effects){d.writeByte(3);d.writeInt(12);d.writeByte(4);d.writeInt(18);}d.writeInt(70);d.writeInt(50);d.writeByte(0);d.writeInt(0);return b.toByteArray();
    }
    static int value(Class<?> c,String f)throws Exception{return c.getField(f).getInt(null);}
    static Object vector(HungBySonMonsterRecoveryBinaryTest.Harness h,int id,int type)throws Exception{
        Object v=h.loader.loadClass("et").getConstructor().newInstance();Object dto=h.loader.loadClass(n("liilli")).getConstructor(short.class,byte.class).newInstance((short)id,(byte)type);h.add.invoke(v,dto);return v;
    }
    public static void main(String[] args)throws Exception{
        require(args.length==3,"candidate.jar catalog.properties microemulator.jar");Properties p=new Properties();InputStream in=new FileInputStream(args[1]);try{p.load(in);}finally{in.close();}
        Map<String,byte[]> jar=HungBySonMonsterRecoveryBinaryTest.read(new File(args[0]));
        ClassNode sender=HungBySonMonsterRecoveryBinaryTest.node(jar.get(p.getProperty("hook.attackSend.owner")+".class"));int hooks=0;
        for(MethodNode m:sender.methods)for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).name.equals("attackVector")){require(m.name.equals(n("il"))&&m.desc.equals("(Let;B)V"),"Only opcode6 serializer instrumented");hooks++;}
        require(hooks==1,"Exactly one outgoing tap");
        stubEnqueue(jar);HungBySonMonsterRecoveryBinaryTest.Harness h=new HungBySonMonsterRecoveryBinaryTest.Harness(jar,p);
        Class<?> spy=h.loader.loadClass(WATCH.replace('/','.')),config=h.loader.loadClass(p.getProperty("selection.auto.owner"));Field auto=config.getField(p.getProperty("selection.auto.field"));
        Class<?> sendClass=h.loader.loadClass(p.getProperty("hook.attackSend.owner"));Object send=HungBySonMonsterRecoveryBinaryTest.allocate(sendClass);Class<?> et=h.loader.loadClass("et");Method attack=sendClass.getMethod(n("il"),et,byte.class),pk=sendClass.getMethod(n(""),et,byte.class);
        auto.setBoolean(null,true);Object targets=vector(h,65530,1);Object secondary=h.loader.loadClass(n("liilli")).getConstructor(short.class,byte.class).newInstance((short)41,(byte)1);h.add.invoke(targets,secondary);attack.invoke(send,targets,(byte)0);require(value(spy,"attacks")==1&&value(spy,"id")==65530&&value(spy,"sends")==1,"Successful native send counts only unsigned first monster ID");
        auto.setBoolean(null,false);attack.invoke(send,targets,(byte)0);require(value(spy,"attacks")==1&&value(spy,"sends")==2,"Auto OFF does not count");auto.setBoolean(null,true);
        pk.invoke(send,targets,(byte)0);require(value(spy,"attacks")==1,"Opcode9 never counted");
        attack.invoke(send,vector(h,7,0),(byte)0);require(value(spy,"attacks")==1,"Non-monster primary not counted");
        Object empty=et.getConstructor().newInstance();attack.invoke(send,empty,(byte)0);require(value(spy,"attacks")==1,"Empty list not counted");
        Object bad=et.getConstructor().newInstance();h.add.invoke(bad,"wrong DTO");attack.invoke(send,bad,(byte)0);require(value(spy,"attacks")==1,"Caught serializer failure is not an attack event");
        spy.getField("failSend").setInt(null,1);boolean failed=false;try{attack.invoke(send,targets,(byte)0);}catch(InvocationTargetException expected){failed=expected.getCause() instanceof IllegalStateException;}finally{spy.getField("failSend").setInt(null,0);}require(failed&&value(spy,"attacks")==1,"Failed native enqueue is not counted");
        Object m=h.make(41);h.learn(41);Class<?> ready=h.loader.loadClass(p.getProperty("ready.owner"));ready.getField(p.getProperty("ready.field")).setBoolean(null,true);
        Class<?> map=h.loader.loadClass("cs");Object mapObject=HungBySonMonsterRecoveryBinaryTest.allocate(map);map.getField("d").setInt(mapObject,1);map.getField("u").setByte(null,(byte)2);Class<?> mapHolder=h.loader.loadClass(p.getProperty("map.owner"));mapHolder.getField(p.getProperty("map.field")).set(null,mapObject);
        Object catalog=et.getConstructor().newInstance();h.add.invoke(catalog,m);h.mob.getField("L").set(null,catalog);
        h.call("onMap",new Class<?>[]{h.api},h.nativeAccess);
        h.call("onObject",new Class<?>[]{h.api,int.class,int.class,int.class,int.class,int.class,int.class},h.nativeAccess,1,5,41,30,40,5);
        h.call("onDetail",new Class<?>[]{h.api,int.class,int.class,int.class,int.class,int.class,int.class},h.nativeAccess,41,5,30,40,50,100);
        Method receive=h.packets.getMethod(p.getProperty("hook.unseenCombat.method"),h.message,byte.class,byte.class);
        Object msg=h.message.getConstructor(byte.class,byte[].class).newInstance((byte)6,combat(41,0,true));receive.invoke(null,msg,(byte)0,(byte)1);require(HungBySonMonsterRecoveryBinaryTest.get(m,"bs")==0,"Unknown attacker applies authoritative death HP with effects");
        HungBySonMonsterRecoveryBinaryTest.set(m,"bs",50);msg=h.message.getConstructor(byte.class,byte[].class).newInstance((byte)9,combat(41,0,false));receive.invoke(null,msg,(byte)0,(byte)0);require(HungBySonMonsterRecoveryBinaryTest.get(m,"bs")==50,"PK targetType bypasses fallback");
        byte[] good=combat(41,0,true);byte[] truncated=Arrays.copyOf(good,good.length-1);msg=h.message.getConstructor(byte.class,byte[].class).newInstance((byte)6,truncated);receive.invoke(null,msg,(byte)0,(byte)1);require(HungBySonMonsterRecoveryBinaryTest.get(m,"bs")==50,"Truncated footer never mutates HP");
        h.bridge.getMethod("applyHp",Object.class,int.class).invoke(h.nativeAccess,m,101);require(HungBySonMonsterRecoveryBinaryTest.get(m,"bs")==50,"HP above native max rejected");
        h.bridge.getMethod("applyHp",Object.class,int.class).invoke(h.nativeAccess,m,-1);require(HungBySonMonsterRecoveryBinaryTest.get(m,"bs")==50,"Negative HP rejected");
        Object actor=HungBySonMonsterRecoveryBinaryTest.allocate(h.entity);
        HungBySonMonsterRecoveryBinaryTest.set(actor,"cu",777);HungBySonMonsterRecoveryBinaryTest.set(actor,"cv",(byte)0);
        HungBySonMonsterRecoveryBinaryTest.set(actor,"bs",100);HungBySonMonsterRecoveryBinaryTest.set(actor,"bt",100);h.add.invoke(h.vector,actor);
        msg=h.message.getConstructor(byte.class,byte[].class).newInstance((byte)6,combat(41,0,true));
        try{receive.invoke(null,msg,(byte)0,(byte)1);}catch(InvocationTargetException e){if(!(e.getCause() instanceof NullPointerException))throw e;}
        require(HungBySonMonsterRecoveryBinaryTest.get(m,"bs")==0,
                "Loaded attacker still delivers validated death HP before native animation needs its missing assets");
        System.out.println("STALE_TARGET_BINARY=PASS; checks="+checks+"; actual serializers, unseen combat, PK/truncation gates");
    }
}
