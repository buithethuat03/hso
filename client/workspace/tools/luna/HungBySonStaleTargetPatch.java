import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Exact native boundaries for queued monster attacks and unseen-attacker HP. */
final class HungBySonStaleTargetPatch implements Opcodes {
    static final String BRIDGE=HungBySonInvestigatePatcher.BRIDGE;
    static final String SUPPORT=HungBySonInvestigatePatcher.SUPPORT;
    static final String ACCESS=HungBySonInvestigatePatcher.ACCESS;
    static final String WATCH="luna/features/MonsterTargetWatchSupport";
    static String n(String t){return HungBySonMonsterSelectionPatch.n(t);}
    static void check(boolean b,String s){HungBySonInvestigatePatcher.check(b,s);}
    static AbstractInsnNode prev(AbstractInsnNode i){return HungBySonInvestigatePatcher.prior(i);}
    static AbstractInsnNode next(AbstractInsnNode i){do{i=i.getNext();}while(i!=null&&i.getOpcode()<0);return i;}
    static MethodNode method(String o,String m,String d){return HungBySonInvestigatePatcher.method(HungBySonInvestigatePatcher.nodes.get(o),m,d);}
    static void apply(){
        String sender=HungBySonInvestigatePatcher.senderOwner;
        MethodNode attack=method(sender,n("il"),"(Let;B)V");
        List<AbstractInsnNode> r=HungBySonInvestigatePatcher.real(attack);
        check(r.get(1) instanceof IntInsnNode&&((IntInsnNode)r.get(1)).operand==6,"Expected native monster opcode6");
        MethodInsnNode send=null;JumpInsnNode normal=null;
        for(AbstractInsnNode i:r){
            if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;
                if(x.owner.equals(n("lllilii"))&&x.name.equals(n("lliiiii"))&&x.desc.equals("()V")){check(send==null,"One native enqueue");send=x;}}
            if(i instanceof JumpInsnNode&&i.getOpcode()==GOTO&&prev(i) instanceof JumpInsnNode&&prev(i).getOpcode()==IF_ICMPLT){check(normal==null,"One successful serializer exit");normal=(JumpInsnNode)i;}
        }
        check(send!=null&&normal!=null&&next(send).getOpcode()==RETURN,"Native sender exit shape");
        int flag=attack.maxLocals++;
        InsnList entry=new InsnList();entry.add(new InsnNode(ICONST_0));entry.add(new VarInsnNode(ISTORE,flag));attack.instructions.insert(entry);
        InsnList success=new InsnList();success.add(new InsnNode(ICONST_1));success.add(new VarInsnNode(ISTORE,flag));attack.instructions.insertBefore(normal,success);
        LabelNode skip=new LabelNode();InsnList tap=new InsnList();tap.add(new VarInsnNode(ILOAD,flag));tap.add(new JumpInsnNode(IFEQ,skip));tap.add(new VarInsnNode(ALOAD,1));tap.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"attackVector","(Ljava/lang/Object;)V",false));tap.add(skip);attack.instructions.insert(send,tap);
        ClassNode sc=HungBySonInvestigatePatcher.nodes.get(sender);HungBySonInvestigatePatcher.allow(sc,attack);HungBySonInvestigatePatcher.methodProps("hook.attackSend",sc,attack);HungBySonInvestigatePatcher.property("hook.attackSend.count","1");
        String packet=n("illlllil");ClassNode receiver=HungBySonInvestigatePatcher.nodes.get(HungBySonInvestigatePatcher.packetOwner);
        MethodNode combat=method(receiver.name,n(""),"(L"+packet+";BB)V");
        AbstractInsnNode exit=null;MethodInsnNode input=null;
        r=HungBySonInvestigatePatcher.real(combat);
        for(int k=0;k<r.size();k++){
            AbstractInsnNode i=r.get(k);
            if(i instanceof MethodInsnNode&&((MethodInsnNode)i).desc.equals("()Ljava/io/DataInputStream;"))input=(MethodInsnNode)i;
            if(i.getOpcode()==RETURN&&k>=2&&r.get(k-1) instanceof JumpInsnNode&&r.get(k-1).getOpcode()==IFEQ&&r.get(k-2) instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)r.get(k-2);if(f.name.equals("cL")&&f.desc.equals("Z")){check(exit==null,"Unique unseen attacker exit");exit=i;}}
        }
        check(exit!=null&&input!=null&&input.owner.equals(packet),"Unseen attacker packet boundary");
        LabelNode pk=new LabelNode();InsnList hp=new InsnList();hp.add(new VarInsnNode(ILOAD,2));hp.add(new InsnNode(ICONST_1));hp.add(new JumpInsnNode(IF_ICMPNE,pk));hp.add(new FieldInsnNode(GETSTATIC,BRIDGE,"INSTANCE",ACCESS));hp.add(new VarInsnNode(ALOAD,0));hp.add(new MethodInsnNode(INVOKEVIRTUAL,input.owner,input.name,input.desc,false));hp.add(new MethodInsnNode(INVOKESTATIC,SUPPORT,"onCombatSnapshot","("+ACCESS+"Ljava/io/DataInputStream;)V",false));hp.add(pk);combat.instructions.insert(hp);
        HungBySonInvestigatePatcher.allow(receiver,combat);HungBySonInvestigatePatcher.methodProps("hook.unseenCombat",receiver,combat);HungBySonInvestigatePatcher.property("hook.unseenCombat.count","1");
        HungBySonInvestigatePatcher.checkField(HungBySonInvestigatePatcher.nodes.get(n("liilli")),n("lilllli"),"S",false);
        HungBySonInvestigatePatcher.checkField(HungBySonInvestigatePatcher.nodes.get(n("liilli")),"b","B",false);
    }
    static void remove(MethodNode m,AbstractInsnNode from,AbstractInsnNode through){AbstractInsnNode i=from;while(true){check(i!=null,"Inverse hook bounds");AbstractInsnNode after=i.getNext();m.instructions.remove(i);if(i==through)return;i=after;}}
    static boolean inverse(MethodNode m,MethodInsnNode call){
        if(call.owner.equals(BRIDGE)&&call.name.equals("attackVector")){
            AbstractInsnNode arg=prev(call),jump=prev(arg),load=prev(jump);check(arg instanceof VarInsnNode&&arg.getOpcode()==ALOAD&&((VarInsnNode)arg).var==1&&jump instanceof JumpInsnNode&&jump.getOpcode()==IFEQ&&load instanceof VarInsnNode&&load.getOpcode()==ILOAD,"Inverse queued attack shape");
            int flag=((VarInsnNode)load).var;LabelNode end=((JumpInsnNode)jump).label;check(call.getNext()==end,"Inverse queued attack label");remove(m,load,end);
            int flags=0;for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof VarInsnNode&&i.getOpcode()==ISTORE&&((VarInsnNode)i).var==flag){AbstractInsnNode value=prev(i);check(value.getOpcode()==ICONST_0||value.getOpcode()==ICONST_1,"Inverse serializer flag");remove(m,value,i);flags++;}check(flags==2,"Two serializer flags");return true;
        }
        if(call.owner.equals(SUPPORT)&&call.name.equals("onCombatSnapshot")){
            AbstractInsnNode getter=prev(call),arg=prev(getter),instance=prev(arg),jump=prev(instance),one=prev(jump),type=prev(one);
            check(getter instanceof MethodInsnNode&&((MethodInsnNode)getter).desc.equals("()Ljava/io/DataInputStream;")&&arg instanceof VarInsnNode&&arg.getOpcode()==ALOAD&&((VarInsnNode)arg).var==0&&instance instanceof FieldInsnNode&&instance.getOpcode()==GETSTATIC&&jump instanceof JumpInsnNode&&jump.getOpcode()==IF_ICMPNE&&one.getOpcode()==ICONST_1&&type instanceof VarInsnNode&&type.getOpcode()==ILOAD&&((VarInsnNode)type).var==2,"Inverse unseen combat shape");
            LabelNode end=((JumpInsnNode)jump).label;check(call.getNext()==end,"Inverse unseen combat label");remove(m,type,end);return true;
        }
        return false;
    }
    static MethodVisitor start(ClassWriter w,String name,String desc){return HungBySonInvestigatePatcher.start(w,name,desc);}
    static void end(MethodVisitor m,int op){HungBySonInvestigatePatcher.finish(m,op);}
    static void obj(MethodVisitor m){HungBySonInvestigatePatcher.object(m);}
    static void addBridgeMethods(ClassWriter w){
        String entity=HungBySonInvestigatePatcher.entity;
        MethodVisitor m;
        for(String[] f:new String[][]{{"hp","bs","I"},{"id","cu","I"},{"type","cv","B"}}){m=start(w,f[0],"(Ljava/lang/Object;)I");obj(m);m.visitFieldInsn(GETFIELD,entity,f[1],f[2]);end(m,IRETURN);}
        m=start(w,"autoEnabled","()Z");m.visitFieldInsn(GETSTATIC,HungBySonMonsterSelectionPatch.CONFIG,HungBySonMonsterSelectionPatch.AUTO,"Z");end(m,IRETURN);
        FieldInsnNode target=HungBySonMonsterSelectionPatch.target;
        m=start(w,"clearAutoTarget","(Ljava/lang/Object;)V");Label done=new Label();m.visitFieldInsn(GETSTATIC,target.owner,target.name,target.desc);m.visitVarInsn(ALOAD,1);m.visitJumpInsn(IF_ACMPNE,done);m.visitInsn(ACONST_NULL);m.visitFieldInsn(PUTSTATIC,target.owner,target.name,target.desc);m.visitLabel(done);end(m,RETURN);
        m=start(w,"applyHp","(Ljava/lang/Object;I)Z");done=new Label();m.visitVarInsn(ALOAD,1);m.visitJumpInsn(IFNULL,done);obj(m);m.visitFieldInsn(GETFIELD,entity,"cv","B");m.visitInsn(ICONST_1);m.visitJumpInsn(IF_ICMPNE,done);obj(m);m.visitFieldInsn(GETFIELD,entity,"cL","Z");m.visitJumpInsn(IFNE,done);m.visitVarInsn(ILOAD,2);m.visitJumpInsn(IFLT,done);obj(m);m.visitFieldInsn(GETFIELD,entity,"bt","I");m.visitJumpInsn(IFLE,done);m.visitVarInsn(ILOAD,2);obj(m);m.visitFieldInsn(GETFIELD,entity,"bt","I");m.visitJumpInsn(IF_ICMPGT,done);obj(m);m.visitVarInsn(ILOAD,2);m.visitFieldInsn(PUTFIELD,entity,"bs","I");HungBySonCombatHpBridgePatch.emitPositiveRelease(m,entity,HungBySonInvestigatePatcher.mob);m.visitInsn(ICONST_1);m.visitInsn(IRETURN);m.visitLabel(done);m.visitInsn(ICONST_0);end(m,IRETURN);
        m=w.visitMethod(ACC_PUBLIC|ACC_STATIC,"attackVector","(Ljava/lang/Object;)V",null,null);m.visitCode();Label begin=new Label(),stop=new Label(),caught=new Label();m.visitTryCatchBlock(begin,stop,caught,"java/lang/Throwable");m.visitLabel(begin);
        m.visitFieldInsn(GETSTATIC,HungBySonMonsterSelectionPatch.CONFIG,HungBySonMonsterSelectionPatch.AUTO,"Z");m.visitJumpInsn(IFEQ,stop);m.visitVarInsn(ALOAD,0);m.visitTypeInsn(CHECKCAST,"et");m.visitVarInsn(ASTORE,1);m.visitVarInsn(ALOAD,1);m.visitMethodInsn(INVOKEVIRTUAL,"et","c","()I",false);m.visitJumpInsn(IFLE,stop);m.visitVarInsn(ALOAD,1);m.visitInsn(ICONST_0);m.visitMethodInsn(INVOKEVIRTUAL,"et","a","(I)Ljava/lang/Object;",false);m.visitTypeInsn(CHECKCAST,n("liilli"));m.visitVarInsn(ASTORE,2);m.visitVarInsn(ALOAD,2);m.visitFieldInsn(GETFIELD,n("liilli"),"b","B");m.visitInsn(ICONST_1);m.visitJumpInsn(IF_ICMPNE,stop);m.visitFieldInsn(GETSTATIC,BRIDGE,"INSTANCE",ACCESS);m.visitVarInsn(ALOAD,2);m.visitFieldInsn(GETFIELD,n("liilli"),n("lilllli"),"S");m.visitLdcInsn(Integer.valueOf(65535));m.visitInsn(IAND);m.visitMethodInsn(INVOKESTATIC,WATCH,"onAttack","("+ACCESS+"I)V",false);m.visitLabel(stop);m.visitInsn(RETURN);m.visitLabel(caught);m.visitInsn(POP);end(m,RETURN);
    }
}
