import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Pinned, reversible packet/lifecycle hooks for the separate monster-cache test suite. */
public final class HungBySonInvestigatePatcher implements Opcodes {
    static final String INPUT="490AE902E7F4D3975663123283F4E93509CFA263E9E5E7D482DD078B500DCA1F";
    static final String API="luna/api/MonsterCacheAccess", CACHE_NAMES="luna/api/MonsterCacheTemplateNames", SUPPORT="luna/features/MonsterCacheSupport";
    static final String BRIDGE="luna/generated/HungMonsterCacheAccess", STORAGE="luna/generated/InvestigateStorage";
    static final String RMS="javax/microedition/rms/RecordStore";
    static final String ACCESS="L"+API+";";
    static final String INFO_OWNER=HungBySonMonsterSelectionPatch.n("lliiliii");
    static final String INFO_FIELD=HungBySonMonsterSelectionPatch.n("lilliiilill");
    static Properties p=new Properties();
    static Map<String,ClassNode> nodes=new LinkedHashMap<String,ClassNode>();
    static Map<String,Set<String>> allowed=new LinkedHashMap<String,Set<String>>();
    static String entity,world,mob,packetOwner,mapOwner,senderOwner;
    static FieldInsnNode mapSingleton,ready,player,list;
    static MethodInsnNode find,factory,catalog,getSender,request;

    static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static byte[] read(InputStream in)throws Exception{try{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=in.read(x))!=-1)b.write(x,0,n);return b.toByteArray();}finally{in.close();}}
    static String hash(File f)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f.toPath()));StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format("%02X",b&255));return s.toString();}
    static ClassNode cls(byte[] b){check(b!=null,"Missing class");ClassNode c=new ClassNode();new ClassReader(b).accept(c,0);return c;}
    static byte[] bytes(ClassNode c,boolean max){ClassWriter w=new ClassWriter(max?ClassWriter.COMPUTE_MAXS:0);c.accept(w);return w.toByteArray();}
    static List<AbstractInsnNode> real(MethodNode m){List<AbstractInsnNode>a=new ArrayList<AbstractInsnNode>();for(AbstractInsnNode i:m.instructions.toArray())if(i.getOpcode()>=0)a.add(i);return a;}
    static MethodNode method(ClassNode c,String n,String d){for(MethodNode m:c.methods)if(m.name.equals(n)&&m.desc.equals(d))return m;throw new IllegalStateException("Missing "+c.name+"."+n+d);}
    static boolean calls(MethodNode m,String o,String n,String d){for(AbstractInsnNode i:real(m))if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if((o==null||o.equals(x.owner))&&(n==null||n.equals(x.name))&&d.equals(x.desc))return true;}return false;}
    static boolean text(MethodNode m,String s){for(AbstractInsnNode i:real(m))if(i instanceof LdcInsnNode&&((LdcInsnNode)i).cst instanceof String&&((String)((LdcInsnNode)i).cst).indexOf(s)>=0)return true;return false;}
    static void allow(ClassNode c,MethodNode m){Set<String>s=allowed.get(c.name);if(s==null){s=new LinkedHashSet<String>();allowed.put(c.name,s);}s.add(m.name+m.desc);}
    static AbstractInsnNode prior(AbstractInsnNode i){i=i.getPrevious();while(i!=null&&i.getOpcode()<0)i=i.getPrevious();return i;}
    static FieldInsnNode field(MethodNode m,int op,String o,String n,String d){FieldInsnNode hit=null;for(AbstractInsnNode i:real(m))if(i instanceof FieldInsnNode&&i.getOpcode()==op){FieldInsnNode x=(FieldInsnNode)i;if((o==null||o.equals(x.owner))&&(n==null||n.equals(x.name))&&d.equals(x.desc)){if(hit==null)hit=x;else check(hit.owner.equals(x.owner)&&hit.name.equals(x.name),"Ambiguous field "+n);}}check(hit!=null,"Missing field "+n+d);return hit;}
    static MethodNode uniquePacket(ClassNode c,String which){MethodNode hit=null;for(MethodNode m:c.methods){boolean yes="detail".equals(which)?text(m,"loi cho nay ne monsterinfo"):"objects".equals(which)?calls(m,mob,"a","(IIII)V"):calls(m,"cs","b","([B)V")&&calls(m,"cs","c","([B)V");if(yes){check(hit==null,"Ambiguous packet "+which);hit=m;}}check(hit!=null,"Missing packet "+which);return hit;}
    static InsnList hook(String name,int ints){InsnList a=new InsnList();a.add(new FieldInsnNode(GETSTATIC,BRIDGE,"INSTANCE",ACCESS));for(int i=1;i<=ints;i++)a.add(new VarInsnNode(ILOAD,i));StringBuilder d=new StringBuilder("("+ACCESS);for(int i=0;i<ints;i++)d.append('I');d.append(")V");a.add(new MethodInsnNode(INVOKESTATIC,SUPPORT,name,d.toString(),false));return a;}
    static void hookStore(ClassNode c,MethodNode m,int var,String call){AbstractInsnNode hit=null;for(AbstractInsnNode i:real(m))if(i instanceof VarInsnNode&&i.getOpcode()==ISTORE&&((VarInsnNode)i).var==var){hit=i;break;}check(hit!=null,"Missing packet prefix local");m.instructions.insert(hit,hook(call,6));allow(c,m);}
    static void property(String key,String value){p.setProperty(key,value);}
    static void methodProps(String key,ClassNode c,MethodNode m){property(key+".owner",c.name);property(key+".method",m.name);property(key+".descriptor",m.desc);}

    public static void main(String[] args)throws Exception{
        check(args.length==5,"input.jar compiled-support-dir output.jar catalog.properties report.txt");
        File input=new File(args[0]),supportDir=new File(args[1]),out=new File(args[2]),config=new File(args[3]),report=new File(args[4]);
        check(INPUT.equals(hash(input)),"Unexpected input release");check(!out.exists()&&!config.exists()&&!report.exists(),"Outputs must be new");
        JarFile jar=new JarFile(input);LinkedHashMap<String,byte[]> old=new LinkedHashMap<String,byte[]>();Map<String,Long>times=new HashMap<String,Long>();
        try{for(Enumeration<JarEntry>es=jar.entries();es.hasMoreElements();){JarEntry e=es.nextElement();check(!old.containsKey(e.getName()),"Duplicate entry");byte[] b=read(jar.getInputStream(e));old.put(e.getName(),b);times.put(e.getName(),e.getTime());if(e.getName().endsWith(".class")){ClassNode c=cls(b);nodes.put(c.name,c);}}}finally{jar.close();}
        ClassNode packets=null;MethodNode detail=null;
        for(ClassNode c:nodes.values())for(MethodNode m:c.methods)if(text(m,"loi cho nay ne monsterinfo")){check(packets==null,"Ambiguous monster receiver");packets=c;detail=m;}
        check(packets!=null,"Missing monster receiver");packetOwner=packets.name;
        for(AbstractInsnNode i:real(detail)){if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if("a".equals(x.name)&&x.desc.startsWith("(IB)L")){find=x;entity=x.owner;}}if(i instanceof TypeInsnNode&&i.getOpcode()==CHECKCAST&&mob==null)mob=((TypeInsnNode)i).desc;}
        check(find!=null&&mob!=null,"Missing native find/factory types");
        MethodNode objects=uniquePacket(packets,"objects"),map=uniquePacket(packets,"map");
        for(AbstractInsnNode i:real(objects))if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if(x.owner.equals(mob)&&x.name.equals("a")&&x.desc.equals("(IIII)V"))factory=x;}
        check(factory!=null,"Missing native factory");mapSingleton=field(map,GETSTATIC,null,null,"Lcs;");mapOwner="cs";
        ready=field(objects,GETSTATIC,null,null,"Z");
        for(AbstractInsnNode i:real(objects))if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if(x.getOpcode()==INVOKESTATIC&&x.desc.equals("()L"+x.owner+";")){getSender=x;break;}}
        check(getSender!=null,"Missing sender singleton");senderOwner=getSender.owner;
        for(AbstractInsnNode i:real(objects))if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if(x.owner.equals(senderOwner)&&x.desc.equals("(S)V")){request=x;break;}}
        check(request!=null,"Missing monster request");
        ClassNode monster=nodes.get(mob);catalog=new MethodInsnNode(INVOKESTATIC,mob,"b",findCatalogDesc(monster),false);
        for(AbstractInsnNode i:real(method(monster,"a","(IIII)V")))if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if(x.getOpcode()==INVOKESTATIC&&x.desc.equals("(L"+entity+";)V")){world=x.owner;break;}}
        check(world!=null,"Missing world insertion");
        ClassNode worldNode=nodes.get(world);MethodNode tick=method(worldNode,"a","()V");
        list=findEntityList(tick);
        MethodNode socketCtor=null;ClassNode socket=null;
        for(ClassNode c:nodes.values())for(MethodNode m:c.methods)if(m.name.equals("<init>")&&m.desc.equals("(Ljava/lang/String;I)V")&&text(m,"socket://")){check(socket==null,"Ambiguous socket ctor");socket=c;socketCtor=m;}
        check(socket!=null,"Missing endpoint identity");
        // Map/zone and catalog guards must be actual public fields for the generated bridge.
        checkField(nodes.get("cs"),"d","I",false);checkField(nodes.get("cs"),"u","B",true);
        for(String name:new String[]{"cu","bs","bt","cG","cH","aZ","ba","bg","bh","bb","bc","bd","cK","dW","bT"})checkField(nodes.get(entity),name,"I",false);
        for(String name:new String[]{"cL","cM","cX","dz","dk"})checkField(nodes.get(entity),name,"Z",false);
        checkField(monster,"a","I",false);
        boolean deathFrames=false;for(FieldNode f:monster.fields)if(f.name.equals("z")&&f.desc.equals("[[[B")&&(f.access&ACC_STATIC)==0)deathFrames=true;
        check(deathFrames,"Missing native death-frame table");
        checkField(monster,"L","Let;",true);checkField(nodes.get(list.owner),list.name,"Let;",true);
        checkField(nodes.get(INFO_OWNER),INFO_FIELD,"Z",true);
        property("hud.info.owner",INFO_OWNER);property("hud.info.field",INFO_FIELD);
        hookStore(packets,objects,6,"onObject");methodProps("hook.object4",packets,objects);
        FieldInsnNode loaded=field(detail,PUTFIELD,entity,"cX","Z");
        detail.instructions.insert(loaded,hook("onDetail",6));allow(packets,detail);methodProps("hook.detail7",packets,detail);
        int maxHandler=-1;for(TryCatchBlockNode t:map.tryCatchBlocks)maxHandler=Math.max(maxHandler,map.instructions.indexOf(t.handler));
        List<AbstractInsnNode> mapExits=new ArrayList<AbstractInsnNode>();for(AbstractInsnNode i:map.instructions.toArray())if(i.getOpcode()==RETURN&&map.instructions.indexOf(i)<maxHandler)mapExits.add(i);
        int mapHooks=0;for(AbstractInsnNode i:mapExits){map.instructions.insertBefore(i,hook("onMap",0));mapHooks++;}
        check(mapHooks==2,"Expected two successful map exits");allow(packets,map);methodProps("hook.map12",packets,map);
        int tickHooks=0;for(AbstractInsnNode i:tick.instructions.toArray())if(i.getOpcode()==RETURN){tick.instructions.insertBefore(i,hook("tick",0));tickHooks++;}
        check(tickHooks>0&&tickHooks<=3,"Unexpected world tick exits");allow(worldNode,tick);methodProps("hook.tick",worldNode,tick);
        FieldInsnNode retiring=field(tick,GETFIELD,entity,"cM","Z");
        check(retiring!=null && field(tick,PUTFIELD,entity,"cL","Z")!=null,"Missing world removal lifecycle");
        AbstractInsnNode stored=prior(retiring);
        check(stored instanceof VarInsnNode&&stored.getOpcode()==ASTORE&&((VarInsnNode)stored).var==2,
                "Unexpected world removal object local");
        InsnList corpseTick=new InsnList();corpseTick.add(new InsnNode(DUP));
        corpseTick.add(new FieldInsnNode(GETSTATIC,BRIDGE,"INSTANCE",ACCESS));corpseTick.add(new InsnNode(SWAP));
        corpseTick.add(new MethodInsnNode(INVOKESTATIC,SUPPORT,"onEntityUpdate","("+ACCESS+"Ljava/lang/Object;)V",false));
        tick.instructions.insertBefore(retiring,corpseTick);methodProps("hook.corpseTick",worldNode,tick);
        MethodNode paint=method(nodes.get("com/silverknight/a"),"paint","(Ljavax/microedition/lcdui/Graphics;)V");
        int paintHooks=0;for(AbstractInsnNode i:paint.instructions.toArray())if(i.getOpcode()==RETURN){InsnList a=new InsnList();a.add(new VarInsnNode(ALOAD,1));a.add(new MethodInsnNode(INVOKESTATIC,SUPPORT,"paint","(Ljava/lang/Object;)V",false));paint.instructions.insertBefore(i,a);paintHooks++;}
        check(paintHooks>0&&paintHooks<=3,"Unexpected canvas paint exits");allow(nodes.get("com/silverknight/a"),paint);methodProps("hook.paint",nodes.get("com/silverknight/a"),paint);
        ClassNode midletNode=nodes.get("com/silverknight/TemMidlet");MethodNode destroy=method(midletNode,"destroyApp","(Z)V");
        int destroyHooks=0;for(AbstractInsnNode i:destroy.instructions.toArray())if(i.getOpcode()==RETURN){destroy.instructions.insertBefore(i,hook("flushNow",0));destroyHooks++;}
        check(destroyHooks==1,"Unexpected destroy exits");allow(midletNode,destroy);methodProps("hook.destroy",midletNode,destroy);
        AbstractInsnNode initialized=null;for(AbstractInsnNode i:real(socketCtor))if(i instanceof MethodInsnNode&&i.getOpcode()==INVOKESPECIAL&&"<init>".equals(((MethodInsnNode)i).name)){initialized=i;break;}
        check(initialized!=null,"Missing socket super ctor");InsnList endpoint=new InsnList();endpoint.add(new VarInsnNode(ALOAD,1));endpoint.add(new VarInsnNode(ILOAD,2));endpoint.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"select","(Ljava/lang/String;I)V",false));socketCtor.instructions.insert(initialized,endpoint);allow(socket,socketCtor);methodProps("hook.endpoint",socket,socketCtor);
        int rmsCalls=0;for(ClassNode c:nodes.values())for(MethodNode m:c.methods)for(AbstractInsnNode i:real(m))if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if(x.getOpcode()==INVOKESTATIC&&RMS.equals(x.owner)){check((x.name.equals("openRecordStore")&&x.desc.equals("(Ljava/lang/String;Z)Ljavax/microedition/rms/RecordStore;"))||(x.name.equals("deleteRecordStore")&&x.desc.equals("(Ljava/lang/String;)V"))||(x.name.equals("listRecordStores")&&x.desc.equals("()[Ljava/lang/String;")),"Unhandled RMS static API");x.owner=STORAGE;allow(c,m);rmsCalls++;}}
        check(rmsCalls>0,"No native RMS boundaries");
        // Pending candidates and confirmed hidden corpses wait for a server-validated respawn.
        MethodNode death=method(monster,"a_","()V");
        check(calls(death,mob,"r","()V")&&field(death,PUTFIELD,entity,"dk","Z")!=null,"Unexpected native monster death lifecycle");
        LabelNode nativeDeath=new LabelNode();InsnList hold=new InsnList();hold.add(new VarInsnNode(ALOAD,0));hold.add(new MethodInsnNode(INVOKESTATIC,SUPPORT,"hold","(Ljava/lang/Object;)Z",false));hold.add(new JumpInsnNode(IFEQ,nativeDeath));hold.add(new InsnNode(RETURN));hold.add(nativeDeath);death.instructions.insert(hold);allow(monster,death);methodProps("hook.pendingDeath",monster,death);
        MethodNode frames=method(monster,"b_","()V");
        check(field(frames,PUTFIELD,entity,"cL","Z")!=null,"Missing terminal animation cleanup");
        LabelNode nativeFrames=new LabelNode();InsnList retainedFrames=new InsnList();
        retainedFrames.add(new VarInsnNode(ALOAD,0));retainedFrames.add(new MethodInsnNode(INVOKESTATIC,SUPPORT,"advanceCorpse","(Ljava/lang/Object;)Z",false));
        retainedFrames.add(new JumpInsnNode(IFEQ,nativeFrames));retainedFrames.add(corpseFrameStep());retainedFrames.add(new InsnNode(RETURN));retainedFrames.add(nativeFrames);
        frames.instructions.insert(retainedFrames);allow(monster,frames);methodProps("hook.corpseFrames",monster,frames);
        HungBySonMonsterSelectionPatch.apply();
        HungBySonStaleTargetPatch.apply();
        LinkedHashMap<String,byte[]> after=new LinkedHashMap<String,byte[]>(old);
        for(String owner:allowed.keySet()){
            ClassNode c=nodes.get(owner);byte[] modified=bytes(c,true);inverseCheck(cls(old.get(owner+".class")),cls(modified));after.put(owner+".class",modified);
        }
        List<String> added=new ArrayList<String>();collectSupport(supportDir,supportDir,after,added);check(added.contains(API+".class")&&added.contains(SUPPORT+".class")&&added.contains(STORAGE+".class"),"Missing support source output");
        check(!after.containsKey(BRIDGE+".class"),"Bridge collision");after.put(BRIDGE+".class",bridge());added.add(BRIDGE+".class");
        after.put(HungBySonMonsterSelectionPatch.BRIDGE+".class",HungBySonMonsterSelectionPatch.bridge());added.add(HungBySonMonsterSelectionPatch.BRIDGE+".class");
        after.put(HungBySonMonsterSelectionPatch.COMMANDS+".class",HungBySonMonsterSelectionPatch.commands());added.add(HungBySonMonsterSelectionPatch.COMMANDS+".class");
        Manifest manifest=new Manifest(new ByteArrayInputStream(old.get("META-INF/MANIFEST.MF")));Attributes a=manifest.getMainAttributes();a.putValue("MIDlet-Name","Hung_By_Son Investigate");a.putValue("MIDlet-Vendor","Hung_By_Son_Investigate");String midlet=a.getValue("MIDlet-1");check(midlet!=null&&midlet.indexOf(',')>0,"Missing MIDlet");a.putValue("MIDlet-1","Hung_By_Son Investigate"+midlet.substring(midlet.indexOf(',')));ByteArrayOutputStream manifestBytes=new ByteArrayOutputStream();manifest.write(manifestBytes);after.put("META-INF/MANIFEST.MF",manifestBytes.toByteArray());
        Files.createDirectories(out.getAbsoluteFile().getParentFile().toPath());JarOutputStream stream=new JarOutputStream(new FileOutputStream(out));try{for(Map.Entry<String,byte[]>e:after.entrySet()){JarEntry z=new JarEntry(e.getKey());Long time=times.get(e.getKey());z.setTime(time==null?0:time.longValue());stream.putNextEntry(z);stream.write(e.getValue());stream.closeEntry();}}finally{stream.close();}
        property("input.sha256",INPUT);property("output.sha256",hash(out));property("changed.classes",join(allowed.keySet()));property("added.classes",join(added));property("entity.owner",entity);property("mob.owner",mob);property("world.owner",world);property("list.owner",list.owner);property("list.field",list.name);property("ready.owner",ready.owner);property("ready.field",ready.name);property("map.owner",mapSingleton.owner);property("map.field",mapSingleton.name);property("rms.calls",String.valueOf(rmsCalls));property("auto.delay.ms","500");property("request.extra.ms","300");property("bridge.owner",BRIDGE);property("hook.map12.count",String.valueOf(mapHooks));property("hook.tick.count",String.valueOf(tickHooks));property("hook.paint.count",String.valueOf(paintHooks));property("hook.destroy.count",String.valueOf(destroyHooks));
        OutputStream properties=new FileOutputStream(config);try{p.store(properties,"Verified Hung investigation hook symbols");}finally{properties.close();}
        PrintWriter log=new PrintWriter(new OutputStreamWriter(new FileOutputStream(report),"UTF-8"));log.println("PASS exact input, unique packet/factory/socket discovery and descriptors");log.println("PASS inverse canonical checks: only hooks and scoped RMS calls in native classes");log.println("RMS_CALLS="+rmsCalls);log.println("MAP_HOOKS="+mapHooks+" TICK_HOOKS="+tickHooks+" PAINT_HOOKS="+paintHooks);log.println("NATIVE_CHANGED="+allowed.size()+" ADDED="+added.size());log.println("MANIFEST=separate name/vendor; RMS=inv_ namespace");log.println("OUTPUT_SHA256="+hash(out));log.close();System.out.println("INVESTIGATE_PATCH=PASS; SHA256="+hash(out));
    }

    static String findCatalogDesc(ClassNode c){for(MethodNode m:c.methods)if(m.name.equals("b")&&m.desc.startsWith("(I)L"))return m.desc;throw new IllegalStateException("Missing monster catalog");}
    static FieldInsnNode findEntityList(MethodNode m){List<AbstractInsnNode>a=real(m);for(int i=3;i<a.size();i++)if(a.get(i) instanceof TypeInsnNode&&a.get(i).getOpcode()==CHECKCAST&&entity.equals(((TypeInsnNode)a.get(i)).desc)){for(int n=i-1;n>=Math.max(0,i-5);n--)if(a.get(n) instanceof FieldInsnNode&&a.get(n).getOpcode()==GETSTATIC&&"Let;".equals(((FieldInsnNode)a.get(n)).desc))return (FieldInsnNode)a.get(n);}throw new IllegalStateException("Missing entity vector");}
    static void checkField(ClassNode c,String n,String d,boolean stat){boolean hit=false;for(FieldNode f:c.fields)if(f.name.equals(n)&&f.desc.equals(d)){check((f.access&ACC_PUBLIC)!=0,"Bridge field not public "+n);check(((f.access&ACC_STATIC)!=0)==stat,"Field static mismatch "+n);hit=true;}check(hit,"Missing bridge field "+n+d);}
    static void inverseCheck(ClassNode old,ClassNode after){
        check(old.fields.size()==after.fields.size()&&old.methods.size()==after.methods.size()&&old.version==after.version,"Native ABI changed");
        for(MethodNode m:after.methods){
            for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;
                if(STORAGE.equals(x.owner)){x.owner=RMS;continue;}
                if(HungBySonStaleTargetPatch.inverse(m,x))continue;
                if(SUPPORT.equals(x.owner)&&(x.name.equals("hold")||x.name.equals("advanceCorpse"))){
                    AbstractInsnNode arg=prior(x),branch=x.getNext();check(arg instanceof VarInsnNode&&((VarInsnNode)arg).var==0&&branch instanceof JumpInsnNode&&branch.getOpcode()==IFEQ,"Inverse pending death guard");LabelNode end=((JumpInsnNode)branch).label;AbstractInsnNode cursor=arg;while(cursor!=end){check(cursor!=null,"Pending guard label");AbstractInsnNode next=cursor.getNext();m.instructions.remove(cursor);cursor=next;}m.instructions.remove(end);continue;
                }
                if(HungBySonMonsterSelectionPatch.inverse(m,x))continue;
                int remove=SUPPORT.equals(x.owner)?("onObject".equals(x.name)||"onDetail".equals(x.name)?7:"onEntityUpdate".equals(x.name)?3:1):BRIDGE.equals(x.owner)&&"select".equals(x.name)?2:0;
                if(remove==0)continue;AbstractInsnNode before=prior(i);m.instructions.remove(i);for(int n=0;n<remove;n++){check(before!=null,"Inverse hook shape");AbstractInsnNode next=prior(before);m.instructions.remove(before);before=next;}
            }
            MethodNode original=method(old,m.name,m.desc);m.maxStack=original.maxStack;m.maxLocals=original.maxLocals;
        }
        check(Arrays.equals(bytes(old,false),bytes(after,false)),"Inverse canonical diff exceeds allowed hooks");
    }
    static void collectSupport(File root,File dir,Map<String,byte[]> result,List<String> names)throws Exception{File[] files=dir.listFiles();check(files!=null,"Missing compiled support directory");Arrays.sort(files);for(File f:files){if(f.isDirectory())collectSupport(root,f,result,names);else if(f.getName().endsWith(".class")){String path=root.toPath().relativize(f.toPath()).toString().replace('\\','/');check(path.startsWith("luna/"),"Unexpected support class "+path);check(!result.containsKey(path),"Class collision "+path);byte[] b=Files.readAllBytes(f.toPath());check(cls(b).version<=V1_7,"Support class version");result.put(path,b);names.add(path);}}}
    static String join(Collection<String> a){StringBuilder b=new StringBuilder();for(String s:a){if(b.length()>0)b.append(',');b.append(s);}return b.toString();}
    static MethodVisitor start(ClassWriter w,String n,String d){MethodVisitor m=w.visitMethod(ACC_PUBLIC,n,d,null,null);m.visitCode();return m;}
    static void finish(MethodVisitor m,int op){m.visitInsn(op);m.visitMaxs(0,0);m.visitEnd();}
    static void map(MethodVisitor m){m.visitFieldInsn(GETSTATIC,mapSingleton.owner,mapSingleton.name,mapSingleton.desc);}
    static void object(MethodVisitor m){m.visitVarInsn(ALOAD,1);m.visitTypeInsn(CHECKCAST,entity);}
    static void nativeFind(MethodVisitor m){m.visitVarInsn(ILOAD,1);m.visitInsn(I2S);m.visitInsn(ICONST_1);m.visitMethodInsn(INVOKESTATIC,find.owner,find.name,find.desc,false);}
    static InsnList corpseFrameStep(){
        // The private animation table is read only inside its original owning class.
        MethodNode m=new MethodNode();Label done=new Label();
        m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,mob,"z","[[[B");m.visitVarInsn(ASTORE,1);
        m.visitVarInsn(ALOAD,1);m.visitJumpInsn(IFNULL,done);m.visitVarInsn(ALOAD,1);m.visitInsn(ARRAYLENGTH);m.visitInsn(ICONST_4);m.visitJumpInsn(IF_ICMPLE,done);
        m.visitVarInsn(ALOAD,1);m.visitInsn(ICONST_4);m.visitInsn(AALOAD);m.visitVarInsn(ASTORE,1);
        m.visitVarInsn(ALOAD,1);m.visitJumpInsn(IFNULL,done);m.visitVarInsn(ALOAD,1);m.visitInsn(ARRAYLENGTH);m.visitJumpInsn(IFEQ,done);
        m.visitVarInsn(ALOAD,1);m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,entity,"cH","I");m.visitInsn(ICONST_0);m.visitMethodInsn(INVOKESTATIC,"java/lang/Math","max","(II)I",false);
        m.visitInsn(ICONST_2);m.visitMethodInsn(INVOKESTATIC,"java/lang/Math","min","(II)I",false);
        m.visitVarInsn(ALOAD,1);m.visitInsn(ARRAYLENGTH);m.visitInsn(ICONST_1);m.visitInsn(ISUB);m.visitMethodInsn(INVOKESTATIC,"java/lang/Math","min","(II)I",false);
        m.visitInsn(AALOAD);m.visitVarInsn(ASTORE,1);
        m.visitVarInsn(ALOAD,1);m.visitJumpInsn(IFNULL,done);m.visitVarInsn(ALOAD,1);m.visitInsn(ARRAYLENGTH);m.visitJumpInsn(IFEQ,done);
        m.visitVarInsn(ALOAD,0);m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,entity,"bb","I");m.visitInsn(ICONST_1);m.visitInsn(IADD);
        m.visitInsn(ICONST_0);m.visitMethodInsn(INVOKESTATIC,"java/lang/Math","max","(II)I",false);
        m.visitVarInsn(ALOAD,1);m.visitInsn(ARRAYLENGTH);m.visitInsn(ICONST_1);m.visitInsn(ISUB);m.visitMethodInsn(INVOKESTATIC,"java/lang/Math","min","(II)I",false);m.visitFieldInsn(PUTFIELD,entity,"bb","I");
        m.visitLabel(done);return m.instructions;
    }
    static void corpseBridge(ClassWriter w){
        MethodVisitor m=start(w,"corpse","(Ljava/lang/Object;Z)V");
        object(m);m.visitTypeInsn(CHECKCAST,mob);m.visitVarInsn(ASTORE,3);
        for(String f:new String[]{"bs","bT"}){m.visitVarInsn(ALOAD,3);m.visitInsn(ICONST_0);m.visitFieldInsn(PUTFIELD,entity,f,"I");}
        m.visitVarInsn(ALOAD,3);m.visitInsn(ICONST_4);m.visitFieldInsn(PUTFIELD,entity,"cG","I");
        for(String f:new String[]{"dk","cL","cM"}){m.visitVarInsn(ALOAD,3);m.visitInsn(ICONST_0);m.visitFieldInsn(PUTFIELD,entity,f,"Z");}
        for(String f:new String[]{"dz","cX"}){m.visitVarInsn(ALOAD,3);m.visitInsn(ICONST_1);m.visitFieldInsn(PUTFIELD,entity,f,"Z");}
        m.visitVarInsn(ALOAD,3);m.visitInsn(ICONST_0);m.visitFieldInsn(PUTFIELD,mob,"a","I");
        finish(m,RETURN);
    }
    static byte[] bridge(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(V1_4,ACC_PUBLIC|ACC_FINAL,BRIDGE,null,"java/lang/Object",new String[]{API,CACHE_NAMES});
        w.visitField(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"INSTANCE",ACCESS,null,null).visitEnd();w.visitField(ACC_PRIVATE|ACC_STATIC,"key","Ljava/lang/String;",null,null).visitEnd();
        MethodVisitor m=start(w,"<init>","()V");m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);finish(m,RETURN);
        m=w.visitMethod(ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();m.visitTypeInsn(NEW,BRIDGE);m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,BRIDGE,"<init>","()V",false);m.visitFieldInsn(PUTSTATIC,BRIDGE,"INSTANCE",ACCESS);m.visitLdcInsn("");m.visitFieldInsn(PUTSTATIC,BRIDGE,"key","Ljava/lang/String;");finish(m,RETURN);
        m=w.visitMethod(ACC_PUBLIC|ACC_STATIC,"select","(Ljava/lang/String;I)V",null,null);m.visitCode();m.visitTypeInsn(NEW,"java/lang/StringBuffer");m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,"java/lang/StringBuffer","<init>","()V",false);m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKEVIRTUAL,"java/lang/StringBuffer","append","(Ljava/lang/String;)Ljava/lang/StringBuffer;",false);m.visitLdcInsn(":");m.visitMethodInsn(INVOKEVIRTUAL,"java/lang/StringBuffer","append","(Ljava/lang/String;)Ljava/lang/StringBuffer;",false);m.visitVarInsn(ILOAD,1);m.visitMethodInsn(INVOKEVIRTUAL,"java/lang/StringBuffer","append","(I)Ljava/lang/StringBuffer;",false);m.visitMethodInsn(INVOKEVIRTUAL,"java/lang/StringBuffer","toString","()Ljava/lang/String;",false);m.visitFieldInsn(PUTSTATIC,BRIDGE,"key","Ljava/lang/String;");finish(m,RETURN);
        m=start(w,"now","()J");m.visitMethodInsn(INVOKESTATIC,"java/lang/System","currentTimeMillis","()J",false);finish(m,LRETURN);
        m=start(w,"serverKey","()Ljava/lang/String;");m.visitFieldInsn(GETSTATIC,BRIDGE,"key","Ljava/lang/String;");finish(m,ARETURN);
        m=start(w,"templateName","(I)Ljava/lang/String;");m.visitVarInsn(ILOAD,1);m.visitInsn(I2S);m.visitMethodInsn(INVOKESTATIC,catalog.owner,catalog.name,catalog.desc,false);m.visitVarInsn(ASTORE,2);Label absentTemplate=new Label();m.visitVarInsn(ALOAD,2);m.visitJumpInsn(IFNULL,absentTemplate);m.visitVarInsn(ALOAD,2);FieldInsnNode catalogName=HungBySonMonsterSelectionPatch.templateName;m.visitFieldInsn(GETFIELD,catalogName.owner,catalogName.name,catalogName.desc);m.visitInsn(ARETURN);m.visitLabel(absentTemplate);m.visitInsn(ACONST_NULL);finish(m,ARETURN);
        m=start(w,"mapId","()I");map(m);m.visitFieldInsn(GETFIELD,"cs","d","I");finish(m,IRETURN);
        m=start(w,"zoneId","()I");m.visitFieldInsn(GETSTATIC,"cs","u","B");finish(m,IRETURN);
        m=start(w,"ready","()Z");Label fail=new Label();m.visitFieldInsn(GETSTATIC,ready.owner,ready.name,"Z");m.visitJumpInsn(IFEQ,fail);map(m);m.visitJumpInsn(IFNULL,fail);m.visitFieldInsn(GETSTATIC,world,list.name,list.desc);m.visitJumpInsn(IFNULL,fail);m.visitFieldInsn(GETSTATIC,mob,"L","Let;");m.visitJumpInsn(IFNULL,fail);m.visitFieldInsn(GETSTATIC,mob,"L","Let;");m.visitMethodInsn(INVOKEVIRTUAL,"et","c","()I",false);m.visitJumpInsn(IFLE,fail);m.visitInsn(ICONST_1);m.visitInsn(IRETURN);m.visitLabel(fail);m.visitInsn(ICONST_0);finish(m,IRETURN);
        m=start(w,"find","(I)Ljava/lang/Object;");nativeFind(m);m.visitVarInsn(ASTORE,2);Label none=new Label();m.visitVarInsn(ALOAD,2);m.visitJumpInsn(IFNULL,none);m.visitVarInsn(ALOAD,2);m.visitFieldInsn(GETFIELD,entity,"cL","Z");m.visitJumpInsn(IFNE,none);m.visitVarInsn(ALOAD,2);m.visitInsn(ARETURN);m.visitLabel(none);m.visitInsn(ACONST_NULL);finish(m,ARETURN);
        m=start(w,"dead","(Ljava/lang/Object;)Z");Label deadObject=new Label();object(m);m.visitFieldInsn(GETFIELD,entity,"bs","I");m.visitJumpInsn(IFLE,deadObject);object(m);m.visitFieldInsn(GETFIELD,entity,"cG","I");m.visitInsn(ICONST_4);m.visitJumpInsn(IF_ICMPEQ,deadObject);object(m);m.visitFieldInsn(GETFIELD,entity,"dk","Z");m.visitJumpInsn(IFNE,deadObject);m.visitInsn(ICONST_0);m.visitInsn(IRETURN);m.visitLabel(deadObject);m.visitInsn(ICONST_1);finish(m,IRETURN);
        m=start(w,"hidden","(Ljava/lang/Object;)Z");object(m);m.visitFieldInsn(GETFIELD,entity,"dk","Z");finish(m,IRETURN);
        m=start(w,"retiring","(Ljava/lang/Object;)Z");Label retiringYes=new Label();object(m);m.visitFieldInsn(GETFIELD,entity,"cM","Z");m.visitJumpInsn(IFNE,retiringYes);object(m);m.visitFieldInsn(GETFIELD,entity,"cL","Z");m.visitJumpInsn(IFNE,retiringYes);m.visitInsn(ICONST_0);m.visitInsn(IRETURN);m.visitLabel(retiringYes);m.visitInsn(ICONST_1);finish(m,IRETURN);
        corpseBridge(w);
        m=start(w,"restore","(IIII)Ljava/lang/Object;");Label noRestore=new Label();m.visitVarInsn(ALOAD,0);m.visitVarInsn(ILOAD,2);m.visitMethodInsn(INVOKEVIRTUAL,BRIDGE,"find","(I)Ljava/lang/Object;",false);m.visitJumpInsn(IFNONNULL,noRestore);m.visitVarInsn(ILOAD,1);m.visitInsn(I2S);m.visitMethodInsn(INVOKESTATIC,catalog.owner,catalog.name,catalog.desc,false);m.visitJumpInsn(IFNULL,noRestore);m.visitVarInsn(ILOAD,2);m.visitInsn(I2S);m.visitVarInsn(ILOAD,3);m.visitVarInsn(ILOAD,4);m.visitVarInsn(ILOAD,1);m.visitInsn(I2S);m.visitMethodInsn(INVOKESTATIC,factory.owner,factory.name,factory.desc,false);m.visitVarInsn(ALOAD,0);m.visitVarInsn(ILOAD,2);m.visitMethodInsn(INVOKEVIRTUAL,BRIDGE,"find","(I)Ljava/lang/Object;",false);m.visitInsn(ARETURN);m.visitLabel(noRestore);m.visitInsn(ACONST_NULL);finish(m,ARETURN);
        m=start(w,"pending","(Ljava/lang/Object;Z)V");Label accepted=new Label(),dead=new Label(),done=new Label();object(m);m.visitVarInsn(ASTORE,3);m.visitVarInsn(ALOAD,3);m.visitVarInsn(ILOAD,2);m.visitFieldInsn(PUTFIELD,entity,"dz","Z");m.visitVarInsn(ALOAD,3);m.visitInsn(ICONST_1);m.visitFieldInsn(PUTFIELD,entity,"cX","Z");m.visitVarInsn(ILOAD,2);m.visitJumpInsn(IFEQ,accepted);m.visitVarInsn(ALOAD,3);m.visitInsn(ICONST_0);m.visitFieldInsn(PUTFIELD,entity,"bs","I");m.visitVarInsn(ALOAD,3);m.visitInsn(ICONST_4);m.visitFieldInsn(PUTFIELD,entity,"cG","I");m.visitJumpInsn(GOTO,done);m.visitLabel(accepted);m.visitVarInsn(ALOAD,3);m.visitFieldInsn(GETFIELD,entity,"bs","I");m.visitJumpInsn(IFLE,dead);
        for(String f:new String[]{"cG","cK","bb","bc","bd","dW","bT"}){m.visitVarInsn(ALOAD,3);m.visitInsn(ICONST_0);m.visitFieldInsn(PUTFIELD,entity,f,"I");}
        for(String f:new String[]{"cL","cM"}){m.visitVarInsn(ALOAD,3);m.visitInsn(ICONST_0);m.visitFieldInsn(PUTFIELD,entity,f,"Z");}
        Label nonMob=new Label();m.visitVarInsn(ALOAD,3);m.visitTypeInsn(INSTANCEOF,mob);m.visitJumpInsn(IFEQ,nonMob);m.visitVarInsn(ALOAD,3);m.visitTypeInsn(CHECKCAST,mob);m.visitInsn(ICONST_0);m.visitFieldInsn(PUTFIELD,mob,"a","I");m.visitLabel(nonMob);
        m.visitVarInsn(ALOAD,3);m.visitInsn(ICONST_0);m.visitFieldInsn(PUTFIELD,entity,"dk","Z");
        for(String[] f:new String[][]{{"aZ","bg"},{"ba","bh"}}){m.visitVarInsn(ALOAD,3);m.visitVarInsn(ALOAD,3);m.visitFieldInsn(GETFIELD,entity,f[1],"I");m.visitFieldInsn(PUTFIELD,entity,f[0],"I");}
        m.visitJumpInsn(GOTO,done);m.visitLabel(dead);m.visitVarInsn(ALOAD,3);m.visitInsn(ICONST_4);m.visitFieldInsn(PUTFIELD,entity,"cG","I");m.visitLabel(done);finish(m,RETURN);
        // Immediate removal by identity permits the native packet4 factory to replace stale templates.
        String vectorField=null;for(FieldNode f:nodes.get("et").fields)if("Ljava/util/Vector;".equals(f.desc))vectorField=f.name;check(vectorField!=null,"Missing vector storage");
        m=start(w,"discard","(Ljava/lang/Object;)V");object(m);m.visitInsn(ICONST_1);m.visitFieldInsn(PUTFIELD,entity,"cL","Z");m.visitFieldInsn(GETSTATIC,list.owner,list.name,list.desc);m.visitFieldInsn(GETFIELD,"et",vectorField,"Ljava/util/Vector;");m.visitVarInsn(ALOAD,1);m.visitMethodInsn(INVOKEVIRTUAL,"java/util/Vector","removeElement","(Ljava/lang/Object;)Z",false);m.visitInsn(POP);finish(m,RETURN);
        m=start(w,"request","(I)V");m.visitMethodInsn(INVOKESTATIC,getSender.owner,getSender.name,getSender.desc,false);m.visitVarInsn(ILOAD,1);m.visitInsn(I2S);m.visitMethodInsn(INVOKEVIRTUAL,request.owner,request.name,request.desc,false);finish(m,RETURN);
        m=start(w,"load","()[B");m.visitMethodInsn(INVOKESTATIC,STORAGE,"load","()[B",false);finish(m,ARETURN);
        m=start(w,"save","([B)Z");m.visitVarInsn(ALOAD,1);m.visitMethodInsn(INVOKESTATIC,STORAGE,"save","([B)Z",false);finish(m,IRETURN);
        m=start(w,"hud","(Ljava/lang/Object;Ljava/lang/String;)V");Label hidden=new Label();m.visitFieldInsn(GETSTATIC,INFO_OWNER,INFO_FIELD,"Z");m.visitJumpInsn(IFEQ,hidden);m.visitVarInsn(ALOAD,1);m.visitVarInsn(ALOAD,2);m.visitMethodInsn(INVOKESTATIC,STORAGE,"hud","(Ljava/lang/Object;Ljava/lang/String;)V",false);m.visitLabel(hidden);finish(m,RETURN);
        HungBySonStaleTargetPatch.addBridgeMethods(w);
        w.visitEnd();return w.toByteArray();
    }
}
