import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Exact, reversible EXP receive/HUD hooks; patches the existing single or X3 artifact. */
public final class HungBySonExpPatcher implements Opcodes {
    static final String SUPPORT="luna/features/ExpEstimateSupport";
    static final String BRIDGE="luna/generated/ExpEstimateBridge";
    static final String CACHE="luna/generated/HungMonsterCacheAccess";
    static final String API="luna/api/MonsterCacheAccess";
    static final String STORE="luna/generated/ExpEstimateRmsStore";
    static final String SHARED_STORE="luna/generated/ExpEstimateSharedRmsStore";
    static final String NATIVE_STORE="luna/generated/InvestigateStorage";
    static final String NOTICE="luna/ui/ExpEstimateNoticeLayout";
    static final String[] PREFIX={"","b/","c/"};
    static final Properties catalog=new Properties();
    static final List<String> changed=new ArrayList<String>(),added=new ArrayList<String>();
    static final Map<MethodNode,String> before=new IdentityHashMap<MethodNode,String>();
    static final Map<MethodNode,List<Runnable>> undo=new IdentityHashMap<MethodNode,List<Runnable>>();
    static final Map<String,ClassNode> touched=new LinkedHashMap<String,ClassNode>();
    static final Map<MethodVisitor,Label[]> guards=new IdentityHashMap<MethodVisitor,Label[]>();
    static void check(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    static ClassNode node(byte[] b){ClassNode c=new ClassNode();new ClassReader(b).accept(c,0);return c;}
    static byte[] bytes(ClassNode c){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();}
    static AbstractInsnNode next(AbstractInsnNode n){do{n=n.getNext();}while(n!=null&&n.getOpcode()<0);return n;}
    static AbstractInsnNode prev(AbstractInsnNode n){do{n=n.getPrevious();}while(n!=null&&n.getOpcode()<0);return n;}
    static boolean text(MethodNode m,String s){for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof LdcInsnNode&&s.equals(((LdcInsnNode)n).cst))return true;return false;}
    static MethodNode method(ClassNode c,String n,String d){for(MethodNode m:c.methods)if(m.name.equals(n)&&m.desc.equals(d))return m;throw new IllegalStateException("Missing "+c.name+"."+n+d);}
    static void track(final MethodNode m){if(!before.containsKey(m)){before.put(m,canonical(m));undo.put(m,new ArrayList<Runnable>());}}
    static void inject(final MethodNode m,AbstractInsnNode at,InsnList ins,boolean after){
        track(m);final AbstractInsnNode[] nodes=ins.toArray();if(after)m.instructions.insert(at,ins);else m.instructions.insertBefore(at,ins);
        undo.get(m).add(new Runnable(){public void run(){for(AbstractInsnNode n:nodes)m.instructions.remove(n);}});
    }
    static void replace(final MethodNode m,final AbstractInsnNode old,final AbstractInsnNode value){
        track(m);m.instructions.set(old,value);undo.get(m).add(new Runnable(){public void run(){m.instructions.set(value,old);}});
    }
    static Map<String,byte[]> read(File f)throws Exception{
        Map<String,byte[]> r=new LinkedHashMap<String,byte[]>();JarFile jar=new JarFile(f);
        try{for(Enumeration<JarEntry> e=jar.entries();e.hasMoreElements();){JarEntry z=e.nextElement();
            InputStream in=jar.getInputStream(z);ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
            try{while((n=in.read(buf))!=-1)b.write(buf,0,n);}finally{in.close();}r.put(z.getName(),b.toByteArray());}}
        finally{jar.close();}return r;
    }
    static String hash(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");InputStream in=new FileInputStream(f);byte[] b=new byte[8192];int n;try{while((n=in.read(b))!=-1)d.update(b,0,n);}finally{in.close();}StringBuilder s=new StringBuilder();for(byte v:d.digest())s.append(String.format("%02X",v&255));return s.toString();}
    static void collect(File root,File dir,Map<String,byte[]> data)throws Exception{
        File[] files=dir.listFiles();check(files!=null,"Missing compiled support "+dir);Arrays.sort(files);
        for(File f:files)if(f.isDirectory())collect(root,f,data);else if(f.getName().endsWith(".class")){
            String p=root.toPath().relativize(f.toPath()).toString().replace('\\','/');
            check(p.startsWith("luna/")&&p.contains("ExpEstimate"),"Unexpected support class "+p);
            byte[] b=java.nio.file.Files.readAllBytes(f.toPath());check(node(b).version<=V1_7,"Support version");data.put(p,b);
        }
    }
    static void prop(String key,Object value){catalog.setProperty(key,String.valueOf(value));}
    static final class Symbols {
        String prefix,bridge,entity,graphics,font,packet,idField;
        ClassNode hud,receiver;MethodNode paint,xp,snapshot,notice;
        FieldInsnNode main,level,progress,frame,hide,noticeBase,estimateFont;MethodInsnNode fontDraw,crop;
        List<String> noticeFields=new ArrayList<String>();
    }
    static Symbols discover(Map<String,byte[]> entries,String prefix,int slot){
        Symbols s=new Symbols();s.prefix=prefix;s.bridge=prefix+BRIDGE;
        for(Map.Entry<String,byte[]> e:entries.entrySet())if(e.getKey().endsWith(".class")&&e.getKey().startsWith(prefix)){
            if(prefix.length()==0&&(e.getKey().startsWith("b/")||e.getKey().startsWith("c/")))continue;
            ClassNode c=node(e.getValue());for(MethodNode m:c.methods)if(text(m,"Lv.")&&text(m,"%")){
                check(s.paint==null,"Unique HUD per namespace");s.hud=c;s.paint=m;
            }
        }
        check(s.paint!=null,"HUD missing "+prefix);Type[] args=Type.getArgumentTypes(s.paint.desc);
        check((s.paint.access&ACC_STATIC)==0&&args.length==5&&args[1].getSort()==Type.INT&&args[2].getSort()==Type.INT&&args[3].getSort()==Type.BOOLEAN,"Native HUD ABI");
        s.graphics=args[0].getInternalName();s.font=args[4].getInternalName();
        boolean levelText=false;
        for(AbstractInsnNode n:s.paint.instructions.toArray()){
            if(n instanceof LdcInsnNode&&"Lv.".equals(((LdcInsnNode)n).cst))levelText=true;
            if(levelText&&n instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)n;
                if(f.getOpcode()==GETFIELD&&f.desc.equals("S")){
                    if(s.level==null){s.level=f;AbstractInsnNode p=prev(f);check(p instanceof FieldInsnNode&&p.getOpcode()==GETSTATIC,"Main field boundary");s.main=(FieldInsnNode)p;}
                    else if(s.progress==null&&!f.name.equals(s.level.name))s.progress=f;
                }
            }
            if(levelText&&n instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)n;
                if(x.owner.equals(s.font)&&x.desc.equals("(L"+s.graphics+";Ljava/lang/String;IIIZ)V")){s.fontDraw=x;break;}
            }
        }
        check(s.level!=null&&s.progress!=null&&s.fontDraw!=null,"HUD fields and font renderer");s.entity=s.progress.owner;
        check(s.level.owner.equals(s.entity),"Level/EXP common entity");
        ClassNode fontClass=node(entries.get(s.font+".class"));
        for(MethodNode fontInit:fontClass.methods)for(AbstractInsnNode n:fontInit.instructions.toArray())if(n instanceof LdcInsnNode&&"/mfont/tahoma_7_white.png".equals(((LdcInsnNode)n).cst)){
            for(AbstractInsnNode x=n.getNext();x!=null;x=x.getNext())if(x instanceof FieldInsnNode&&x.getOpcode()==PUTSTATIC){FieldInsnNode f=(FieldInsnNode)x;
                check(s.estimateFont==null&&f.owner.equals(s.font)&&f.desc.equals("L"+s.font+";"),"Unique regular white EXP font");s.estimateFont=f;break;
            }
        }check(s.estimateFont!=null,"Existing regular white font for compact EXP line");
        for(AbstractInsnNode n:s.paint.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==GETSTATIC&&((FieldInsnNode)n).desc.equals("Z")){
            AbstractInsnNode branch=next(n);if(branch!=null&&branch.getOpcode()==IFEQ&&next(branch).getOpcode()==RETURN){s.hide=(FieldInsnNode)n;break;}
        }check(s.hide!=null,"Native panel visibility guard");
        for(MethodNode m:s.hud.methods)if(m.desc.equals("(L"+s.graphics+";)V")&&(m.access&ACC_STATIC)==0){
            int draws=0;Set<String> fields=new LinkedHashSet<String>();FieldInsnNode base=null;
            for(AbstractInsnNode n:m.instructions.toArray()){
                if(n instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)n;if(x.owner.equals(s.font)&&x.name.equals(s.fontDraw.name)&&x.desc.equals(s.fontDraw.desc))draws++;}
                if(n instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)n;
                    if(f.getOpcode()==GETFIELD&&f.owner.equals(s.hud.name)&&f.desc.equals("Ljava/lang/String;"))fields.add(f.name);
                    if(base==null&&f.getOpcode()==GETSTATIC&&f.owner.equals(s.hud.name)&&f.desc.equals("I")&&next(f) instanceof VarInsnNode&&next(f).getOpcode()==ISTORE&&((VarInsnNode)next(f)).var==2)base=f;
                }
            }
            if(draws==3&&fields.size()==3&&base!=null){check(s.notice==null,"Unique native notice renderer");s.notice=m;s.noticeBase=base;s.noticeFields.addAll(fields);}
        }check(s.notice!=null,"Three-row native server/local notice renderer");
        for(AbstractInsnNode n:s.paint.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==GETSTATIC){FieldInsnNode f=(FieldInsnNode)n;
            if(f.name.equals("ag")){s.frame=f;break;}}
        check(s.frame!=null,"Stock frame sprite");
        for(AbstractInsnNode n=s.frame.getNext();n!=null;n=n.getNext())if(n instanceof MethodInsnNode){s.crop=(MethodInsnNode)n;break;}
        check(s.crop!=null&&s.crop.owner.equals(s.graphics)&&Type.getArgumentTypes(s.crop.desc).length==9,"Frame drawRegion ABI");
        for(Map.Entry<String,byte[]> e:entries.entrySet())if(e.getKey().endsWith(".class")&&e.getKey().startsWith(prefix)){
            if(prefix.length()==0&&(e.getKey().startsWith("b/")||e.getKey().startsWith("c/")))continue;
            ClassNode c=node(e.getValue());for(MethodNode m:c.methods){
                boolean writes=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)n;if(f.getOpcode()==PUTFIELD&&f.owner.equals(s.entity)&&f.name.equals(s.progress.name)&&f.desc.equals("S"))writes=true;}
                if(writes&&text(m,"xp")){check(s.xp==null,"Unique EXP handler");s.receiver=c;s.xp=m;}
            }
        }
        check(s.xp!=null,"EXP packet handler");s.packet=Type.getArgumentTypes(s.xp.desc)[0].getInternalName();
        for(MethodNode m:s.receiver.methods)if(m!=s.xp)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)n;
            if(f.getOpcode()==PUTFIELD&&f.owner.equals(s.entity)&&f.name.equals(s.progress.name)){check(s.snapshot==null,"Unique main progress snapshot");s.snapshot=m;}}
        check(s.snapshot!=null,"Main snapshot");
        ClassNode cache=node(entries.get(prefix+CACHE+".class"));MethodNode id=method(cache,"id","(Ljava/lang/Object;)I");
        for(AbstractInsnNode n:id.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==GETFIELD){FieldInsnNode f=(FieldInsnNode)n;check(f.owner.equals(s.entity)&&f.desc.equals("I"),"Native character ID ABI");s.idField=f.name;}
        check(s.idField!=null,"Character ID");
        boolean progressWritten=false;int identities=0;
        for(AbstractInsnNode n:s.snapshot.instructions.toArray())if(n instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)n;
            if(f.getOpcode()==PUTFIELD&&f.owner.equals(s.entity)){
                if(f.name.equals(s.progress.name)&&f.desc.equals("S"))progressWritten=true;
                if(f.name.equals(s.idField)&&f.desc.equals("I")){check(progressWritten,"Login identity assignment follows EXP progress");identities++;}
            }
        }check(identities==1,"Unique completed login identity assignment");
        String p="slot."+slot+".";
        prop(p+"hud.owner",s.hud.name);prop(p+"hud.method",s.paint.name);prop(p+"hud.descriptor",s.paint.desc);
        prop(p+"receiver.owner",s.receiver.name);prop(p+"xp.method",s.xp.name);prop(p+"xp.descriptor",s.xp.desc);
        prop(p+"snapshot.method",s.snapshot.name);prop(p+"snapshot.descriptor",s.snapshot.desc);
        prop(p+"entity.owner",s.entity);prop(p+"main.owner",s.main.owner);prop(p+"main.field",s.main.name);prop(p+"main.descriptor",s.main.desc);
        prop(p+"progress.field",s.progress.name);prop(p+"level.field",s.level.name);prop(p+"id.field",s.idField);prop(p+"snapshot.trigger.field",s.idField);prop(p+"snapshot.trigger.descriptor","I");
        prop(p+"font.owner",s.font);prop(p+"font.draw.method",s.fontDraw.name);prop(p+"font.draw.descriptor",s.fontDraw.desc);prop(p+"estimate.font.field",s.estimateFont.name);prop(p+"estimate.font.resource","/mfont/tahoma_7_white.png");prop(p+"graphics.owner",s.graphics);prop(p+"bridge.owner",s.bridge);
        prop(p+"notice.method",s.notice.name);prop(p+"notice.descriptor",s.notice.desc);prop(p+"notice.base.field",s.noticeBase.name);prop(p+"notice.fields",join(s.noticeFields));prop(p+"notice.hide.owner",s.hide.owner);prop(p+"notice.hide.field",s.hide.name);prop(p+"notice.hooks",0);
        return s;
    }
    static void touch(ClassNode c){check(!touched.containsKey(c.name)||touched.get(c.name)==c,"Class graph duplicate");touched.put(c.name,c);}
    static void patch(Symbols s,Map<String,byte[]> entries,int slot){
        touch(s.hud);touch(s.receiver);
        String originalNotice=canonical(s.notice);
        // Packet30: consume each decoded EXP once, even when native popup visibility is OFF.
        int gains=0;for(AbstractInsnNode n:s.xp.instructions.toArray())if(n instanceof MethodInsnNode){MethodInsnNode m=(MethodInsnNode)n;
            if(m.owner.equals("java/io/DataInputStream")&&m.name.equals("readInt")){
                AbstractInsnNode store=next(n);check(store instanceof VarInsnNode&&store.getOpcode()==ISTORE,"EXP gain local");
                AbstractInsnNode own=prev(prev(prev(n))); // structural locals are verified below, not guessed from names.
                check(((VarInsnNode)store).var==3,"Verified native gain local3");
                boolean object2=false;for(AbstractInsnNode x:s.xp.instructions.toArray())if(x instanceof VarInsnNode&&x.getOpcode()==ASTORE&&((VarInsnNode)x).var==2)object2=true;
                check(object2,"Verified resolved entity local2");InsnList h=new InsnList();h.add(new VarInsnNode(ALOAD,2));h.add(new VarInsnNode(ILOAD,3));h.add(new MethodInsnNode(INVOKESTATIC,s.bridge,"gain","(Ljava/lang/Object;I)V",false));inject(s.xp,store,h,true);gains++;
            }}check(gains==1,"One decoded gain hook");
        // Progress precedes the login ID write: restore only once the identity is assigned.
        int levels=0,snaps=0;for(MethodNode m:s.receiver.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)n;
            if(f.getOpcode()!=PUTFIELD||!f.owner.equals(s.entity))continue;
            boolean level=f.name.equals(s.level.name)&&f.desc.equals("S"),snapshot=m==s.snapshot&&f.name.equals(s.idField)&&f.desc.equals("I");
            if(level||snapshot){InsnList pre=new InsnList();pre.add(new InsnNode(DUP2));inject(m,f,pre,false);
                InsnList post=new InsnList();post.add(new InsnNode(POP));post.add(new MethodInsnNode(INVOKESTATIC,s.bridge,level?"levelChanged":"snapshotObject","(Ljava/lang/Object;)V",false));inject(m,f,post,true);if(level)levels++;else snaps++;}
        }check(levels>=2&&snaps==1,"Snapshot/level hooks");prop("slot."+slot+".level.hooks",levels);
        // Stock left/middle/right frame pieces are extended using only their existing pixels.
        int frames=0,right=0,repeat=0;
        for(AbstractInsnNode n:s.paint.instructions.toArray()){
            if(n instanceof FieldInsnNode&&n.getOpcode()==GETSTATIC&&((FieldInsnNode)n).owner.equals(s.frame.owner)&&((FieldInsnNode)n).name.equals(s.frame.name)){
                MethodInsnNode call=null;for(AbstractInsnNode x=n.getNext();x!=null;x=x.getNext())if(x instanceof MethodInsnNode){call=(MethodInsnNode)x;break;}
                check(call!=null&&call.owner.equals(s.crop.owner)&&call.name.equals(s.crop.name)&&call.desc.equals(s.crop.desc),"Stock frame call");
                replace(s.paint,call,new MethodInsnNode(INVOKESTATIC,s.bridge,"framePart","(L"+s.graphics+";"+s.crop.desc.substring(1),false));frames++;
            }
            if(n instanceof IntInsnNode&&((IntInsnNode)n).operand==96)right++;
            if(n.getOpcode()==ICONST_4&&next(n) instanceof JumpInsnNode&&next(n).getOpcode()==IF_ICMPLT&&prev(n) instanceof VarInsnNode&&prev(n).getOpcode()==ILOAD&&((VarInsnNode)prev(n)).var==0)repeat++;
        }check(frames==3&&right==1&&repeat==1,"Exact rectangular panel geometry boundary");
        InsnList draw=new InsnList();draw.add(new VarInsnNode(ALOAD,1));draw.add(new VarInsnNode(ALOAD,5));draw.add(new VarInsnNode(ILOAD,2));draw.add(new VarInsnNode(ILOAD,3));draw.add(new MethodInsnNode(INVOKESTATIC,s.bridge,"drawEstimate","(L"+s.graphics+";L"+s.font+";II)V",false));inject(s.paint,s.fontDraw,draw,true);
        int lower=0;for(MethodNode m:s.hud.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)n;
            if(f.getOpcode()==PUTSTATIC&&f.owner.equals(s.hud.name)&&f.name.equals("h")&&f.desc.equals("I")){
                AbstractInsnNode v=prev(n);
                if(s.prefix.length()>0||entries.containsKey("b/TemMidlet.class")){if(v.getOpcode()==ICONST_0)continue;}
                check(v instanceof IntInsnNode&&((IntInsnNode)v).operand==45,"Mail/count position boundary");replace(m,v,new IntInsnNode(BIPUSH,59));lower++;
            }}check(lower==2,"Both lower icon layouts shifted");prop("slot."+slot+".lower.icon.y",59);
        // Session endpoint changes and destruction flush learning and end the live segment.
        int endpoint=0,destroy=0,close=0,cleanups=0;
        for(Map.Entry<String,byte[]> e:entries.entrySet())if(e.getKey().endsWith(".class")&&e.getKey().startsWith(s.prefix)){
            if(s.prefix.length()==0&&(e.getKey().startsWith("b/")||e.getKey().startsWith("c/")))continue;
            String cn=e.getKey().substring(0,e.getKey().length()-6);ClassNode c=touched.get(cn);if(c==null)c=node(e.getValue());
            for(MethodNode m:c.methods){boolean ep=false,cl=false,listStores=false,deleteStores=false,de=m.name.equals("destroyApp")&&m.desc.equals("(Z)V")&&c.name.endsWith("TemMidlet");
                for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)n;
                    if(x.owner.equals(s.prefix+CACHE)&&x.name.equals("select")&&x.desc.equals("(Ljava/lang/String;I)V"))ep=true;
                    if((x.owner.equals("javax/microedition/io/SocketConnection")||x.owner.equals("javax/microedition/io/Connection"))&&x.name.equals("close")&&x.desc.equals("()V"))cl=true;
                    if(x.owner.equals(s.prefix+NATIVE_STORE)&&x.name.equals("listRecordStores")&&x.desc.equals("()[Ljava/lang/String;"))listStores=true;
                    if(x.owner.equals(s.prefix+NATIVE_STORE)&&x.name.equals("deleteRecordStore")&&x.desc.equals("(Ljava/lang/String;)V"))deleteStores=true;
                }
                if(listStores&&deleteStores){check(c.name.endsWith("TemMidlet")&&cleanups==0,"Unique native login RMS cleanup");touch(c);
                    InsnList init=new InsnList();init.add(new MethodInsnNode(INVOKESTATIC,s.bridge,"prepareStore","()V",false));inject(m,m.instructions.getFirst(),init,false);
                    int deletes=0;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)n;
                        if(x.owner.equals(s.prefix+NATIVE_STORE)&&x.name.equals("deleteRecordStore")&&x.desc.equals("(Ljava/lang/String;)V")){
                            replace(m,x,new MethodInsnNode(INVOKESTATIC,s.bridge,"deleteSessionRecord",x.desc,false));deletes++;
                        }
                    }check(deletes==1,"One native login deletion boundary");cleanups++;
                    prop("slot."+slot+".cleanup.owner",c.name);prop("slot."+slot+".cleanup.method",m.name);prop("slot."+slot+".cleanup.descriptor",m.desc);prop("slot."+slot+".cleanup.exp.preserved",true);
                }
                if(ep||cl||de){touch(c);for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==RETURN){InsnList h=new InsnList();h.add(new MethodInsnNode(INVOKESTATIC,s.bridge,"disconnect","()V",false));inject(m,n,h,false);}
                    if(ep)endpoint++;if(cl)close++;if(de)destroy++;}
            }
        }check(endpoint==1&&destroy==1&&cleanups==1,"Endpoint/destroy and native login cleanup lifecycle");prop("slot."+slot+".close.methods",close);
        check(originalNotice.equals(canonical(s.notice)),"Original native notice renderer preserved");prop("slot."+slot+".notice.native.unchanged",true);
    }
    static MethodVisitor start(ClassWriter w,String n,String d){MethodVisitor m=w.visitMethod(n.equals("<clinit>")?ACC_STATIC:ACC_PUBLIC|ACC_STATIC,n,d,null,null);m.visitCode();Label a=new Label(),b=new Label(),c=new Label();m.visitTryCatchBlock(a,b,c,"java/lang/Throwable");m.visitLabel(a);guards.put(m,new Label[]{a,b,c});return m;}
    static void end(MethodVisitor m,int opcode){m.visitInsn(opcode);Label[] g=guards.remove(m);m.visitLabel(g[1]);m.visitLabel(g[2]);m.visitInsn(POP);if(opcode==ARETURN)m.visitLdcInsn("Caculating...");if(opcode==IRETURN)m.visitVarInsn(ILOAD,0);m.visitInsn(opcode);m.visitMaxs(24,12);m.visitEnd();}
    static void main(MethodVisitor m,Symbols s){m.visitFieldInsn(GETSTATIC,s.main.owner,s.main.name,s.main.desc);}
    static void data(MethodVisitor m,Symbols s){
        m.visitFieldInsn(GETSTATIC,s.prefix+CACHE,"INSTANCE","L"+s.prefix+API+";");m.visitMethodInsn(INVOKEINTERFACE,s.prefix+API,"serverKey","()Ljava/lang/String;",true);
        main(m,s);m.visitFieldInsn(GETFIELD,s.entity,s.idField,"I");main(m,s);m.visitFieldInsn(GETFIELD,s.entity,s.level.name,"S");main(m,s);m.visitFieldInsn(GETFIELD,s.entity,s.progress.name,"S");
    }
    static byte[] bridge(Symbols s){
        ClassWriter w=new ClassWriter(0);w.visit(V1_4,ACC_PUBLIC|ACC_FINAL,s.bridge,null,"java/lang/Object",null);
        MethodVisitor m=start(w,"<clinit>","()V");m.visitTypeInsn(NEW,s.prefix+STORE);m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,s.prefix+STORE,"<init>","()V",false);m.visitMethodInsn(INVOKESTATIC,s.prefix+SUPPORT,"setStore","(L"+s.prefix+"luna/api/ExpEstimateStore;)V",false);end(m,RETURN);
        m=start(w,"snapshot","()V");Label fail=new Label();main(m,s);m.visitJumpInsn(IFNULL,fail);data(m,s);m.visitMethodInsn(INVOKESTATIC,s.prefix+SUPPORT,"snapshot","(Ljava/lang/String;III)V",false);m.visitLabel(fail);end(m,RETURN);
        for(String n:new String[]{"snapshotObject","levelChanged"}){m=start(w,n,"(Ljava/lang/Object;)V");Label stop=new Label();m.visitVarInsn(ALOAD,0);main(m,s);m.visitJumpInsn(IF_ACMPNE,stop);main(m,s);m.visitJumpInsn(IFNULL,stop);
            if(n.equals("levelChanged")){data(m,s);m.visitMethodInsn(INVOKESTATIC,s.prefix+SUPPORT,"levelChanged","(Ljava/lang/String;III)V",false);}
            else m.visitMethodInsn(INVOKESTATIC,s.bridge,"snapshot","()V",false);
            m.visitLabel(stop);end(m,RETURN);}
        m=start(w,"gain","(Ljava/lang/Object;I)V");fail=new Label();m.visitVarInsn(ALOAD,0);main(m,s);m.visitJumpInsn(IF_ACMPNE,fail);main(m,s);m.visitJumpInsn(IFNULL,fail);data(m,s);m.visitVarInsn(ILOAD,1);m.visitMethodInsn(INVOKESTATIC,s.prefix+SUPPORT,"gain","(Ljava/lang/String;IIII)V",false);m.visitLabel(fail);end(m,RETURN);
        m=start(w,"disconnect","()V");m.visitMethodInsn(INVOKESTATIC,s.prefix+SUPPORT,"disconnect","()V",false);end(m,RETURN);
        m=start(w,"prepareStore","()V");m.visitMethodInsn(INVOKESTATIC,SHARED_STORE,"load","()[B",false);m.visitInsn(POP);end(m,RETURN);
        // Forward the native delete (and its exceptions) unchanged except for the old EXP table.
        m=w.visitMethod(ACC_PUBLIC|ACC_STATIC,"deleteSessionRecord","(Ljava/lang/String;)V",null,null);m.visitCode();Label delete=new Label();m.visitLdcInsn("exp_approx_v1");m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false);m.visitJumpInsn(IFEQ,delete);m.visitInsn(RETURN);m.visitLabel(delete);m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESTATIC,s.prefix+NATIVE_STORE,"deleteRecordStore","(Ljava/lang/String;)V",false);m.visitInsn(RETURN);m.visitMaxs(2,1);m.visitEnd();
        m=start(w,"noticeY","(I)I");Label hidden=new Label(),call=new Label();main(m,s);m.visitJumpInsn(IFNULL,hidden);m.visitFieldInsn(GETSTATIC,s.hide.owner,s.hide.name,s.hide.desc);m.visitJumpInsn(IFNE,hidden);m.visitVarInsn(ILOAD,0);m.visitInsn(ICONST_1);m.visitJumpInsn(GOTO,call);m.visitLabel(hidden);m.visitVarInsn(ILOAD,0);m.visitInsn(ICONST_0);m.visitLabel(call);m.visitMethodInsn(INVOKESTATIC,s.prefix+NOTICE,"baseY","(IZ)I",false);end(m,IRETURN);
        m=start(w,"line","()Ljava/lang/String;");fail=new Label();main(m,s);m.visitJumpInsn(IFNULL,fail);data(m,s);m.visitMethodInsn(INVOKESTATIC,s.prefix+SUPPORT,"ensureSession","(Ljava/lang/String;III)V",false);main(m,s);m.visitFieldInsn(GETFIELD,s.entity,s.progress.name,"S");m.visitMethodInsn(INVOKESTATIC,s.prefix+SUPPORT,"line","(I)Ljava/lang/String;",false);m.visitInsn(ARETURN);m.visitLabel(fail);m.visitLdcInsn("Caculating...");end(m,ARETURN);
        m=start(w,"drawEstimate","(L"+s.graphics+";L"+s.font+";II)V");m.visitFieldInsn(GETSTATIC,s.estimateFont.owner,s.estimateFont.name,s.estimateFont.desc);m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESTATIC,s.bridge,"line","()Ljava/lang/String;",false);m.visitVarInsn(ILOAD,2);m.visitInsn(ICONST_3);m.visitInsn(IADD);m.visitVarInsn(ILOAD,3);m.visitIntInsn(BIPUSH,38);m.visitInsn(IADD);m.visitInsn(ICONST_0);m.visitInsn(ICONST_0);m.visitMethodInsn(INVOKEVIRTUAL,s.font,s.fontDraw.name,s.fontDraw.desc,false);end(m,RETURN);
        m=start(w,"framePart","(L"+s.graphics+";"+s.crop.desc.substring(1));
        // Stretch each left/middle/right piece uniformly: top38 + interior14 + bottom4.
        for(int part=0;part<3;part++){
            m.visitVarInsn(ALOAD,0);m.visitVarInsn(ALOAD,1);m.visitVarInsn(ILOAD,2);m.visitVarInsn(ILOAD,3);
            if(part>0){m.visitIntInsn(BIPUSH,part==1?14:38);m.visitInsn(IADD);}m.visitVarInsn(ILOAD,4);m.visitIntInsn(BIPUSH,part==0?38:part==1?14:4);m.visitVarInsn(ILOAD,6);m.visitVarInsn(ILOAD,7);m.visitVarInsn(ILOAD,8);
            if(part>0){m.visitIntInsn(BIPUSH,part==1?38:52);m.visitInsn(IADD);}m.visitVarInsn(ILOAD,9);m.visitMethodInsn(INVOKEVIRTUAL,s.crop.owner,s.crop.name,s.crop.desc,false);
        }end(m,RETURN);w.visitEnd();return w.toByteArray();
    }
    static String canonical(MethodNode m){
        Map<LabelNode,Integer> labels=new IdentityHashMap<LabelNode,Integer>();int index=0;
        for(AbstractInsnNode n:m.instructions.toArray()){if(n instanceof LabelNode)labels.put((LabelNode)n,index);if(n.getOpcode()>=0)index++;}
        StringBuilder b=new StringBuilder();b.append(m.access).append(m.name).append(m.desc);
        for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()>=0){b.append('|').append(n.getOpcode());
            if(n instanceof VarInsnNode)b.append(':').append(((VarInsnNode)n).var);
            else if(n instanceof IntInsnNode)b.append(':').append(((IntInsnNode)n).operand);
            else if(n instanceof LdcInsnNode)b.append(':').append(((LdcInsnNode)n).cst);
            else if(n instanceof FieldInsnNode){FieldInsnNode x=(FieldInsnNode)n;b.append(':').append(x.owner).append('.').append(x.name).append(x.desc);}
            else if(n instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)n;b.append(':').append(x.owner).append('.').append(x.name).append(x.desc).append(x.itf);}
            else if(n instanceof TypeInsnNode)b.append(':').append(((TypeInsnNode)n).desc);
            else if(n instanceof JumpInsnNode)b.append(':').append(labels.get(((JumpInsnNode)n).label));
            else if(n instanceof IincInsnNode){IincInsnNode x=(IincInsnNode)n;b.append(':').append(x.var).append(':').append(x.incr);}
            else if(n instanceof TableSwitchInsnNode){TableSwitchInsnNode x=(TableSwitchInsnNode)n;b.append(':').append(x.min).append(':').append(x.max).append(':').append(labels.get(x.dflt));for(LabelNode l:x.labels)b.append(':').append(labels.get(l));}
            else if(n instanceof LookupSwitchInsnNode){LookupSwitchInsnNode x=(LookupSwitchInsnNode)n;b.append(':').append(x.keys).append(':').append(labels.get(x.dflt));for(LabelNode l:x.labels)b.append(':').append(labels.get(l));}
            else if(n instanceof MultiANewArrayInsnNode){MultiANewArrayInsnNode x=(MultiANewArrayInsnNode)n;b.append(':').append(x.desc).append(':').append(x.dims);}
        }
        for(TryCatchBlockNode t:m.tryCatchBlocks)b.append("!T:").append(labels.get(t.start)).append(':').append(labels.get(t.end)).append(':').append(labels.get(t.handler)).append(':').append(t.type);
        return b.toString();
    }
    static void remapMetadata(ClassNode c,Map<String,String> map){
        c.outerClass=RepackSupportX3.cls(c.outerClass,map);
        if(c.outerMethodDesc!=null)c.outerMethodDesc=RepackSupportX3.desc(c.outerMethodDesc,map);
        for(InnerClassNode n:c.innerClasses){n.name=RepackSupportX3.cls(n.name,map);n.outerName=RepackSupportX3.cls(n.outerName,map);}
        for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FrameNode){FrameNode f=(FrameNode)n;remapFrame(f.local,map);remapFrame(f.stack,map);}
    }
    static void remapFrame(List<Object> values,Map<String,String> map){if(values==null)return;for(int i=0;i<values.size();i++)if(values.get(i) instanceof String){String s=(String)values.get(i);values.set(i,s.startsWith("[")?RepackSupportX3.desc(s,map):RepackSupportX3.cls(s,map));}}
    public static void main(String[] args)throws Exception{
        check(args.length==6,"input.jar supportDir new-output.jar new-catalog.properties new-report.txt expectedSHA");File input=new File(args[0]),support=new File(args[1]),output=new File(args[2]),props=new File(args[3]),report=new File(args[4]);
        check(hash(input).equals(args[5]),"Pinned input hash");check(!output.exists()&&!props.exists()&&!report.exists(),"Outputs must be new");
        Map<String,byte[]> original=read(input),result=new LinkedHashMap<String,byte[]>(original),compiled=new LinkedHashMap<String,byte[]>();collect(support,support,compiled);
        check(compiled.containsKey(SUPPORT+".class")&&compiled.containsKey(STORE+".class")&&compiled.containsKey(SHARED_STORE+".class")&&compiled.containsKey("luna/api/ExpEstimateStore.class")&&compiled.containsKey(NOTICE+".class"),"Complete EXP support, shared store and notice layout");
        int slots=original.containsKey("b/TemMidlet.class")?3:1;prop("slots",slots);prop("input.sha256",args[5]);prop("frame.height.before",42);prop("frame.height.after",56);prop("frame.width.before",96);prop("frame.width.after",96);prop("frame.upper.width",96);prop("frame.rectangular",true);prop("estimate.y.offset",38);prop("notice.position","native");
        prop("exp.cache.key","level");prop("exp.cache.version",2);prop("exp.cache.shared.slots",true);prop("exp.cache.store","hbs_exp_levels_v2");prop("exp.cache.shared.owner",SHARED_STORE);
        for(int slot=0;slot<slots;slot++){
            Symbols s=discover(original,PREFIX[slot],slot);patch(s,original,slot);result.put(s.bridge+".class",bridge(s));added.add(s.bridge+".class");
            Map<String,String> map=new LinkedHashMap<String,String>();
            for(String name:original.keySet())if(name.endsWith(".class")&&!name.startsWith("b/")&&!name.startsWith("c/")){String n=name.substring(0,name.length()-6);if(slot==0||original.containsKey(PREFIX[slot]+n+".class"))map.put(n,PREFIX[slot]+n);}
            for(String name:compiled.keySet()){String n=name.substring(0,name.length()-6);map.put(n,PREFIX[slot]+n);}
            // Only the EXP table/lock is shared; live sampling and all existing slot data stay local.
            map.put(SHARED_STORE,SHARED_STORE);
            for(Map.Entry<String,byte[]> e:compiled.entrySet()){
                if(slot>0&&e.getKey().equals(SHARED_STORE+".class"))continue;
                ClassNode c=node(RepackSupportX3.remap(e.getValue(),map));remapMetadata(c,map);c.access|=ACC_PUBLIC;String path=c.name+".class";check(!original.containsKey(path),"New class collision "+path);result.put(path,bytes(c));added.add(path);
            }
        }
        for(ClassNode c:touched.values()){result.put(c.name+".class",bytes(c));changed.add(c.name+".class");}
        // Undo every declared mutation and prove native methods, branches and exception boundaries match exactly.
        for(Map.Entry<MethodNode,List<Runnable>> e:undo.entrySet()){List<Runnable> u=e.getValue();for(int i=u.size()-1;i>=0;i--)u.get(i).run();check(before.get(e.getKey()).equals(canonical(e.getKey())),"Native inverse scope check "+e.getKey().name);}
        for(String n:original.keySet())if(!changed.contains(n))check(Arrays.equals(original.get(n),result.get(n)),"Unexpected change "+n);
        output.getAbsoluteFile().getParentFile().mkdirs();JarOutputStream out=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:result.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);out.putNextEntry(z);out.write(e.getValue());out.closeEntry();}}finally{out.close();}
        Map<String,byte[]> saved=read(output);check(saved.keySet().equals(result.keySet()),"Saved topology");for(String n:result.keySet())check(Arrays.equals(saved.get(n),result.get(n)),"Saved bytes "+n);
        prop("output.sha256",hash(output));prop("changed.classes",join(changed));prop("added.classes",join(added));
        OutputStream po=new FileOutputStream(props);try{catalog.store(po,"Verified EXP receive/HUD native symbols");}finally{po.close();}
        PrintWriter r=new PrintWriter(new OutputStreamWriter(new FileOutputStream(report),"UTF-8"));r.println("EXP_PATCH=PASS; exact input, native inverse canonical checks, unchanged resources/manifest");for(String n:changed)r.println("CHANGED_CLASS="+n);for(String n:added)r.println("ADDED_CLASS="+n);r.println("SLOTS="+slots);r.println("OUTPUT_SHA256="+hash(output));r.close();
        check(hash(input).equals(args[5]),"Input preserved");System.out.println("EXP_PATCH=PASS; slots="+slots+" changed="+changed.size()+" added="+added.size()+" SHA256="+hash(output));
    }
    static String join(List<String> a){StringBuilder b=new StringBuilder();for(String s:a){if(b.length()>0)b.append(',');b.append(s);}return b.toString();}
}
