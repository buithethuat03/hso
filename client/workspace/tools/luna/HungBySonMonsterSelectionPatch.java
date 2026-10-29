import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Native ABI boundary for the map-scoped semantic selection feature. */
final class HungBySonMonsterSelectionPatch implements Opcodes {
    static final String API="luna/api/MonsterSelectionAccess", ACCESS="L"+API+";";
    static final String SUPPORT="luna/features/MonsterSelectionSupport";
    static final String BRIDGE="luna/generated/HungMonsterSelectionAccess";
    static final String COMMANDS="luna/generated/HungMonsterSelectionCommands";
    static final String CONFIG=n("lliiiiil"), BASE=n("liiiiil");
    static final String AUTO=n("lililiillii"), MENU=n("lilllili");
    static final String POP_OWNER=n("liilllii"), POP_FIELD=n("liiiliillll"), POP_TYPE=n("liilliil");
    static final String POP_DESC="(Let;ILjava/lang/String;ZLet;)V";
    // Normal popup dispatch passes only e/f bytes; row indices are decoded unsigned.
    static FieldInsnNode target;
    static FieldInsnNode templateName;
    static String add;
    static String playerOwner;

    static String n(String tail){StringBuilder b=new StringBuilder();while(b.length()+tail.length()<100)b.append('i');return b.append(tail).toString();}
    static void check(boolean b,String s){HungBySonInvestigatePatcher.check(b,s);}
    static ClassNode node(String name){ClassNode c=HungBySonInvestigatePatcher.nodes.get(name);check(c!=null,"Missing selection ABI class "+name);return c;}
    static List<AbstractInsnNode> real(MethodNode m){return HungBySonInvestigatePatcher.real(m);}
    static MethodNode method(String owner,String name,String desc){return HungBySonInvestigatePatcher.method(node(owner),name,desc);}
    static AbstractInsnNode prior(AbstractInsnNode x){return HungBySonInvestigatePatcher.prior(x);}
    static void property(String k,String v){HungBySonInvestigatePatcher.property(k,v);}
    static void props(String key,ClassNode c,MethodNode m){HungBySonInvestigatePatcher.methodProps(key,c,m);}

    static void apply(){
        String entity=HungBySonInvestigatePatcher.entity;
        for(String f:new String[]{"cv"})HungBySonInvestigatePatcher.checkField(node(entity),f,"B",false);
        HungBySonInvestigatePatcher.checkField(node(entity),"cC","Ljava/lang/String;",false);
        HungBySonInvestigatePatcher.checkField(node(entity),"bz","S",false);
        HungBySonInvestigatePatcher.checkField(node(CONFIG),AUTO,"Z",true);
        HungBySonInvestigatePatcher.checkField(node(POP_OWNER),POP_FIELD,"L"+POP_TYPE+";",true);
        method(POP_TYPE,n(""),POP_DESC);
        method(BASE,"<init>","()V");method(BASE,"a","(II)V");method(BASE,"a","(ILjava/lang/Object;)V");
        method("bt","<init>","(Ljava/lang/String;IL"+BASE+";)V");
        method("bt","<init>","(Ljava/lang/String;IIL"+BASE+";)V");
        for(MethodNode m:node("et").methods)if(m.desc.equals("(Ljava/lang/Object;)V")){
            for(AbstractInsnNode x:real(m))if(x instanceof MethodInsnNode&&((MethodInsnNode)x).owner.equals("java/util/Vector")&&((MethodInsnNode)x).name.equals("addElement"))add=m.name;
        }
        check(add!=null,"Missing native vector append");
        String template=Type.getReturnType(HungBySonInvestigatePatcher.catalog.desc).getInternalName();
        for(FieldNode f:node(template).fields)if(f.desc.equals("Ljava/lang/String;")){
            check(templateName==null,"Ambiguous catalog name");
            check((f.access&ACC_PUBLIC)!=0,"Catalog name must be public");templateName=new FieldInsnNode(GETFIELD,template,f.name,f.desc);
        }
        check(templateName!=null,"Missing template name");
        ClassNode player=null;MethodNode gate=null,gather=null;
        for(ClassNode c:HungBySonInvestigatePatcher.nodes.values())for(MethodNode m:c.methods)if(m.name.equals("bachHoMonsterTargetGate")){
            check(gate==null,"Ambiguous target gate");player=c;gate=m;
        }
        check(player!=null&&gate.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Z"),"Missing native auto gate");playerOwner=player.name;
        for(MethodNode m:player.methods)if(HungBySonInvestigatePatcher.text(m,"Player vec7")){check(gather==null,"Ambiguous gather");gather=m;}
        check(gather!=null&&gather.desc.equals("(IL"+entity+";)Let;"),"Missing auto attack vector");
        // Existing return-true branch is reached only after native type/self/pending checks.
        AbstractInsnNode pass=null;
        for(AbstractInsnNode x:real(gate))if(x.getOpcode()==IRETURN&&prior(x).getOpcode()==ICONST_1){check(pass==null,"Ambiguous passing gate");pass=prior(x);}
        check(pass!=null,"Missing passing gate");InsnList filter=new InsnList();filter.add(new VarInsnNode(ALOAD,0));filter.add(new MethodInsnNode(INVOKESTATIC,COMMANDS,"allow","(Ljava/lang/Object;)Z",false));
        gate.instructions.insertBefore(pass,filter);gate.instructions.remove(pass);HungBySonInvestigatePatcher.allow(player,gate);props("hook.selectionGate",player,gate);
        for(MethodNode m:player.methods)for(AbstractInsnNode x:real(m))if(x instanceof MethodInsnNode&&((MethodInsnNode)x).name.equals("bachHoMonsterTargetGate")){
            // Retention checks read the public world target; capture it by exact descriptor.
            AbstractInsnNode p=prior(prior(x));
            if(p instanceof FieldInsnNode&&p.getOpcode()==GETSTATIC&&((FieldInsnNode)p).owner.equals(HungBySonInvestigatePatcher.world)&&((FieldInsnNode)p).desc.equals("L"+entity+";")){
                FieldInsnNode f=(FieldInsnNode)p;if(target!=null)check(target.name.equals(f.name),"Ambiguous retained target");target=f;
            }
        }
        check(target!=null,"Missing retained auto target");HungBySonInvestigatePatcher.checkField(node(target.owner),target.name,target.desc,true);
        List<TypeInsnNode> allocations=new ArrayList<TypeInsnNode>();
        for(AbstractInsnNode x:real(gather))if(x.getOpcode()==NEW&&x instanceof TypeInsnNode&&!((TypeInsnNode)x).desc.equals("et"))allocations.add((TypeInsnNode)x);
        check(allocations.size()==3&&allocations.get(0).desc.equals(allocations.get(1).desc)&&allocations.get(1).desc.equals(allocations.get(2).desc),"Expected three target DTOs");
        for(int i=0;i<3;i++){
            TypeInsnNode allocation=allocations.get(i);InsnList guard=new InsnList();guard.add(new VarInsnNode(ALOAD,i==0?2:4));guard.add(new MethodInsnNode(INVOKESTATIC,COMMANDS,"allow","(Ljava/lang/Object;)Z",false));
            if(i==0){LabelNode accepted=new LabelNode();guard.add(new JumpInsnNode(IFNE,accepted));guard.add(new TypeInsnNode(NEW,"et"));guard.add(new InsnNode(DUP));guard.add(new MethodInsnNode(INVOKESPECIAL,"et","<init>","()V",false));guard.add(new InsnNode(ARETURN));guard.add(accepted);}
            else {
                // Skip to the existing iteration increment, not the count limit.
                AbstractInsnNode x=allocation;while(x!=null&&!(x instanceof IincInsnNode&&((IincInsnNode)x).var==7))x=x.getNext();check(x!=null,"Missing gather continuation");
                LabelNode loop=null;for(AbstractInsnNode q=x.getPrevious();q!=null&&q.getOpcode()<0;q=q.getPrevious())if(q instanceof LabelNode)loop=(LabelNode)q;
                check(loop!=null,"Missing gather loop label");guard.add(new JumpInsnNode(IFEQ,loop));
            }
            gather.instructions.insertBefore(allocation,guard);
        }
        HungBySonInvestigatePatcher.allow(player,gather);props("hook.selectionGather",player,gather);property("hook.selectionGather.count","3");
        MethodNode menu=method(CONFIG,MENU,"()V");FieldInsnNode receiver=null;
        for(AbstractInsnNode x:real(menu))if(x instanceof FieldInsnNode&&x.getOpcode()==GETSTATIC){FieldInsnNode f=(FieldInsnNode)x;if(f.owner.equals(POP_OWNER)&&f.name.equals(POP_FIELD)&&f.desc.equals("L"+POP_TYPE+";")){
            check(receiver==null,"Ambiguous final auto popup");receiver=f;
        }}
        check(receiver!=null,"Missing auto popup");InsnList append=new InsnList();append.add(new VarInsnNode(ALOAD,0));append.add(new MethodInsnNode(INVOKESTATIC,COMMANDS,"append","(Let;)V",false));menu.instructions.insertBefore(receiver,append);
        HungBySonInvestigatePatcher.allow(node(CONFIG),menu);props("hook.selectionMenu",node(CONFIG),menu);
        property("selection.bridge",BRIDGE);property("selection.commands",COMMANDS);property("selection.auto.owner",CONFIG);property("selection.auto.field",AUTO);
        property("selection.popup.owner",POP_OWNER);property("selection.popup.field",POP_FIELD);property("selection.popup.type",POP_TYPE);
        property("selection.target.owner",target.owner);property("selection.target.field",target.name);
        property("selection.template.owner",templateName.owner);property("selection.template.name",templateName.name);
    }

    static boolean inverse(MethodNode m,MethodInsnNode x){
        if(COMMANDS.equals(x.owner)&&x.name.equals("allow")&&m.name.equals("bachHoMonsterTargetGate")){
            AbstractInsnNode arg=prior(x);check(arg.getOpcode()==ALOAD&&((VarInsnNode)arg).var==0,"Inverse combined target gate");m.instructions.insertBefore(arg,new InsnNode(ICONST_1));m.instructions.remove(arg);m.instructions.remove(x);return true;
        }
        if(SUPPORT.equals(x.owner)&&x.name.equals("allow")){
            AbstractInsnNode arg=prior(x),access=prior(arg);check(arg.getOpcode()==ALOAD&&access.getOpcode()==GETSTATIC,"Inverse selection gate shape");
            m.instructions.insertBefore(access,new InsnNode(ICONST_1));m.instructions.remove(access);m.instructions.remove(arg);m.instructions.remove(x);return true;
        }
        if(!COMMANDS.equals(x.owner))return false;
        if(x.name.equals("append")){AbstractInsnNode arg=prior(x);check(arg instanceof VarInsnNode&&((VarInsnNode)arg).var==0,"Inverse menu local");m.instructions.remove(arg);m.instructions.remove(x);return true;}
        check(x.name.equals("allow"),"Unexpected native selection command");AbstractInsnNode arg=prior(x),jump=x.getNext();while(jump.getOpcode()<0)jump=jump.getNext();check(jump instanceof JumpInsnNode,"Missing selection jump");
        if(((VarInsnNode)arg).var==2){LabelNode accepted=((JumpInsnNode)jump).label;AbstractInsnNode cursor=arg;while(cursor!=accepted){AbstractInsnNode next=cursor.getNext();m.instructions.remove(cursor);cursor=next;}m.instructions.remove(accepted);}
        else {check(((VarInsnNode)arg).var==4&&jump.getOpcode()==IFEQ,"Inverse secondary guard");m.instructions.remove(arg);m.instructions.remove(x);m.instructions.remove(jump);}
        return true;
    }

    static MethodVisitor start(ClassWriter w,String name,String desc,int flags){MethodVisitor m=w.visitMethod(flags,name,desc,null,null);m.visitCode();return m;}
    static MethodVisitor start(ClassWriter w,String name,String desc){return start(w,name,desc,ACC_PUBLIC);}
    static MethodVisitor stat(ClassWriter w,String name,String desc){return start(w,name,desc,ACC_PUBLIC|ACC_STATIC);}
    static void end(MethodVisitor m,int op){m.visitInsn(op);m.visitMaxs(0,0);m.visitEnd();}
    static void ctor(ClassWriter w,String owner,String parent){MethodVisitor m=start(w,"<init>","()V");m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESPECIAL,parent,"<init>","()V",false);end(m,RETURN);m=start(w,"<clinit>","()V",ACC_STATIC);m.visitTypeInsn(NEW,owner);m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,owner,"<init>","()V",false);m.visitFieldInsn(PUTSTATIC,owner,"INSTANCE",owner.equals(BRIDGE)?ACCESS:"L"+owner+";");end(m,RETURN);}
    static void cacheAccess(MethodVisitor m){m.visitFieldInsn(GETSTATIC,HungBySonInvestigatePatcher.BRIDGE,"INSTANCE",HungBySonInvestigatePatcher.ACCESS);}
    static void selectedAccess(MethodVisitor m){m.visitFieldInsn(GETSTATIC,BRIDGE,"INSTANCE",ACCESS);}
    static void entity(MethodVisitor m){m.visitVarInsn(ALOAD,1);m.visitTypeInsn(CHECKCAST,HungBySonInvestigatePatcher.entity);}
    static void list(MethodVisitor m){FieldInsnNode f=HungBySonInvestigatePatcher.list;m.visitFieldInsn(GETSTATIC,f.owner,f.name,f.desc);}
    static void display(MethodVisitor m,int local,String title){m.visitFieldInsn(GETSTATIC,POP_OWNER,POP_FIELD,"L"+POP_TYPE+";");m.visitVarInsn(ALOAD,local);m.visitInsn(ICONST_2);if(title==null)m.visitVarInsn(ALOAD,0);else m.visitLdcInsn(title);m.visitInsn(ICONST_0);m.visitInsn(ACONST_NULL);m.visitMethodInsn(INVOKEVIRTUAL,POP_TYPE,n(""),POP_DESC,false);}
    static void row(MethodVisitor m,String label,int action){m.visitTypeInsn(NEW,"bt");m.visitInsn(DUP);m.visitLdcInsn(label);m.visitIntInsn(BIPUSH,action);m.visitFieldInsn(GETSTATIC,COMMANDS,"INSTANCE","L"+COMMANDS+";");m.visitMethodInsn(INVOKESPECIAL,"bt","<init>","(Ljava/lang/String;IL"+BASE+";)V",false);}
    static byte[] bridge(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(V1_4,ACC_PUBLIC|ACC_FINAL,BRIDGE,null,"java/lang/Object",new String[]{API});w.visitField(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"INSTANCE",ACCESS,null,null).visitEnd();ctor(w,BRIDGE,"java/lang/Object");
        for(String[] f:new String[][]{{"serverKey","()Ljava/lang/String;"},{"mapId","()I"},{"ready","()Z"}}){MethodVisitor m=start(w,f[0],f[1]);cacheAccess(m);m.visitMethodInsn(INVOKEINTERFACE,HungBySonInvestigatePatcher.API,f[0],f[1],true);end(m,f[1].endsWith(";")?ARETURN:IRETURN);}
        MethodVisitor m=start(w,"size","()I");list(m);m.visitMethodInsn(INVOKEVIRTUAL,"et","c","()I",false);end(m,IRETURN);
        m=start(w,"objectAt","(I)Ljava/lang/Object;");list(m);m.visitVarInsn(ILOAD,1);m.visitMethodInsn(INVOKEVIRTUAL,"et","a","(I)Ljava/lang/Object;",false);end(m,ARETURN);
        for(String[] f:new String[][]{{"type","cv","B"},{"level","bz","S"},{"name","cC","Ljava/lang/String;"}}){m=start(w,f[0],"(Ljava/lang/Object;)"+(f[0].equals("name")?"Ljava/lang/String;":"I"));entity(m);m.visitFieldInsn(GETFIELD,HungBySonInvestigatePatcher.entity,f[1],f[2]);end(m,f[0].equals("name")?ARETURN:IRETURN);}
        m=start(w,"templateName","(I)Ljava/lang/String;");m.visitVarInsn(ILOAD,1);m.visitInsn(I2S);MethodInsnNode cat=HungBySonInvestigatePatcher.catalog;m.visitMethodInsn(INVOKESTATIC,cat.owner,cat.name,cat.desc,false);m.visitVarInsn(ASTORE,2);Label absent=new Label();m.visitVarInsn(ALOAD,2);m.visitJumpInsn(IFNULL,absent);m.visitVarInsn(ALOAD,2);m.visitFieldInsn(GETFIELD,templateName.owner,templateName.name,templateName.desc);m.visitInsn(ARETURN);m.visitLabel(absent);m.visitInsn(ACONST_NULL);end(m,ARETURN);
        m=start(w,"remembered","()[I");m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKEVIRTUAL,BRIDGE,"serverKey","()Ljava/lang/String;",false);m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKEVIRTUAL,BRIDGE,"mapId","()I",false);m.visitMethodInsn(INVOKESTATIC,HungBySonInvestigatePatcher.SUPPORT,"remembered","(Ljava/lang/String;I)[I",false);end(m,ARETURN);
        m=start(w,"loadSelection","()[B");m.visitMethodInsn(INVOKESTATIC,HungBySonInvestigatePatcher.STORAGE,"loadSelection","()[B",false);end(m,ARETURN);
        m=start(w,"saveSelection","([B)Z");m.visitVarInsn(ALOAD,1);m.visitMethodInsn(INVOKESTATIC,HungBySonInvestigatePatcher.STORAGE,"saveSelection","([B)Z",false);end(m,IRETURN);
        m=start(w,"showChoices","([Ljava/lang/String;)V");m.visitVarInsn(ALOAD,1);m.visitMethodInsn(INVOKESTATIC,COMMANDS,"showChoices","([Ljava/lang/String;)V",false);end(m,RETURN);
        m=start(w,"back","()V");m.visitMethodInsn(INVOKESTATIC,CONFIG,MENU,"()V",false);end(m,RETURN);
        m=start(w,"message","(Ljava/lang/String;)V");m.visitVarInsn(ALOAD,1);m.visitMethodInsn(INVOKESTATIC,COMMANDS,"message","(Ljava/lang/String;)V",false);end(m,RETURN);
        m=start(w,"changed","()V");Label done=new Label();m.visitFieldInsn(GETSTATIC,CONFIG,AUTO,"Z");m.visitJumpInsn(IFEQ,done);m.visitFieldInsn(GETSTATIC,target.owner,target.name,target.desc);m.visitJumpInsn(IFNULL,done);selectedAccess(m);m.visitFieldInsn(GETSTATIC,target.owner,target.name,target.desc);m.visitMethodInsn(INVOKESTATIC,SUPPORT,"allow","("+ACCESS+"Ljava/lang/Object;)Z",false);m.visitJumpInsn(IFNE,done);m.visitInsn(ACONST_NULL);m.visitFieldInsn(PUTSTATIC,target.owner,target.name,target.desc);m.visitLabel(done);end(m,RETURN);
        w.visitEnd();return w.toByteArray();
    }
    static byte[] commands(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(V1_4,ACC_PUBLIC|ACC_FINAL,COMMANDS,null,BASE,null);w.visitField(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"INSTANCE","L"+COMMANDS+";",null,null).visitEnd();
        // Deferred notice survives the following popup redraw/back performed by semantic code.
        w.visitField(ACC_PRIVATE|ACC_STATIC,"notice","Ljava/lang/String;",null,null).visitEnd();ctor(w,COMMANDS,BASE);
        MethodVisitor m=stat(w,"allow","(Ljava/lang/Object;)Z");Label filter=new Label();m.visitFieldInsn(GETSTATIC,CONFIG,AUTO,"Z");m.visitJumpInsn(IFNE,filter);m.visitInsn(ICONST_1);m.visitInsn(IRETURN);m.visitLabel(filter);m.visitFieldInsn(GETSTATIC,HungBySonInvestigatePatcher.BRIDGE,"INSTANCE",HungBySonInvestigatePatcher.ACCESS);m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESTATIC,"luna/features/MonsterTargetWatchSupport","allow","("+HungBySonInvestigatePatcher.ACCESS+"Ljava/lang/Object;)Z",false);Label rejected=new Label();m.visitJumpInsn(IFEQ,rejected);selectedAccess(m);m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESTATIC,SUPPORT,"allow","("+ACCESS+"Ljava/lang/Object;)Z",false);m.visitInsn(IRETURN);m.visitLabel(rejected);m.visitInsn(ICONST_0);end(m,IRETURN);
        m=stat(w,"append","(Let;)V");m.visitVarInsn(ALOAD,0);row(m,"Chọn quái",1);m.visitMethodInsn(INVOKEVIRTUAL,"et",add,"(Ljava/lang/Object;)V",false);end(m,RETURN);
        m=start(w,"a","(II)V");Label noAction=new Label(),noticeDone=new Label(),notRow=new Label();m.visitVarInsn(ILOAD,1);m.visitIntInsn(BIPUSH,43);m.visitJumpInsn(IF_ICMPNE,notRow);m.visitVarInsn(ILOAD,2);m.visitIntInsn(SIPUSH,255);m.visitInsn(IAND);m.visitMethodInsn(INVOKESTATIC,SUPPORT,"choose","(I)V",false);m.visitJumpInsn(GOTO,noticeDone);m.visitLabel(notRow);m.visitVarInsn(ILOAD,1);m.visitInsn(ICONST_1);m.visitJumpInsn(IF_ICMPNE,noAction);selectedAccess(m);m.visitMethodInsn(INVOKESTATIC,SUPPORT,"open","("+ACCESS+")V",false);m.visitJumpInsn(GOTO,noticeDone);m.visitLabel(noAction);m.visitVarInsn(ILOAD,1);m.visitInsn(ICONST_3);m.visitJumpInsn(IF_ICMPNE,noticeDone);selectedAccess(m);m.visitMethodInsn(INVOKESTATIC,SUPPORT,"open","("+ACCESS+")V",false);m.visitLabel(noticeDone);m.visitMethodInsn(INVOKESTATIC,COMMANDS,"showNotice","()V",false);end(m,RETURN);
        m=start(w,"a","(ILjava/lang/Object;)V");Label noChoice=new Label();m.visitVarInsn(ILOAD,1);m.visitIntInsn(BIPUSH,43);m.visitJumpInsn(IF_ICMPNE,noChoice);m.visitVarInsn(ALOAD,2);m.visitTypeInsn(INSTANCEOF,"java/lang/Integer");m.visitJumpInsn(IFEQ,noChoice);m.visitVarInsn(ALOAD,2);m.visitTypeInsn(CHECKCAST,"java/lang/Integer");m.visitMethodInsn(INVOKEVIRTUAL,"java/lang/Integer","intValue","()I",false);m.visitMethodInsn(INVOKESTATIC,SUPPORT,"choose","(I)V",false);m.visitLabel(noChoice);m.visitMethodInsn(INVOKESTATIC,COMMANDS,"showNotice","()V",false);end(m,RETURN);
        m=stat(w,"showChoices","([Ljava/lang/String;)V");m.visitTypeInsn(NEW,"et");m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,"et","<init>","()V",false);m.visitVarInsn(ASTORE,1);m.visitInsn(ICONST_0);m.visitVarInsn(ISTORE,2);Label test=new Label(),loop=new Label();m.visitJumpInsn(GOTO,test);m.visitLabel(loop);m.visitVarInsn(ALOAD,1);m.visitTypeInsn(NEW,"bt");m.visitInsn(DUP);m.visitVarInsn(ALOAD,0);m.visitVarInsn(ILOAD,2);m.visitInsn(AALOAD);m.visitIntInsn(BIPUSH,43);m.visitVarInsn(ILOAD,2);m.visitFieldInsn(GETSTATIC,COMMANDS,"INSTANCE","L"+COMMANDS+";");m.visitMethodInsn(INVOKESPECIAL,"bt","<init>","(Ljava/lang/String;IIL"+BASE+";)V",false);m.visitMethodInsn(INVOKEVIRTUAL,"et",add,"(Ljava/lang/Object;)V",false);m.visitIincInsn(2,1);m.visitLabel(test);m.visitVarInsn(ILOAD,2);m.visitVarInsn(ALOAD,0);m.visitInsn(ARRAYLENGTH);m.visitJumpInsn(IF_ICMPLT,loop);display(m,1,"Chọn quái");end(m,RETURN);
        m=stat(w,"message","(Ljava/lang/String;)V");m.visitVarInsn(ALOAD,0);m.visitFieldInsn(PUTSTATIC,COMMANDS,"notice","Ljava/lang/String;");end(m,RETURN);
        m=start(w,"showNotice","()V",ACC_PRIVATE|ACC_STATIC);Label noNotice=new Label();m.visitFieldInsn(GETSTATIC,COMMANDS,"notice","Ljava/lang/String;");m.visitVarInsn(ASTORE,0);m.visitVarInsn(ALOAD,0);m.visitJumpInsn(IFNULL,noNotice);m.visitInsn(ACONST_NULL);m.visitFieldInsn(PUTSTATIC,COMMANDS,"notice","Ljava/lang/String;");m.visitTypeInsn(NEW,"et");m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,"et","<init>","()V",false);m.visitVarInsn(ASTORE,1);m.visitVarInsn(ALOAD,1);row(m,"Trở lại",3);m.visitMethodInsn(INVOKEVIRTUAL,"et",add,"(Ljava/lang/Object;)V",false);display(m,1,null);m.visitLabel(noNotice);end(m,RETURN);
        w.visitEnd();return w.toByteArray();
    }
}
