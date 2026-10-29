import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Pinned Hung variant: singleton endpoint, donor authentication and isolated RMS. */
public final class ChibiUpPatcher implements Opcodes {
    public static final String INPUT="BCF304BB6245CDBB6F48D5888404F8186E4E0895E2DCDAF608C68D53FEADA6E2";
    public static final String DONOR="24B6563FA347F63245AF2432EC215367FFDEFE33830C46E111CDAA91E9B4DDAD";
    public static final String LABEL="HSO CHIBI", PREFIX="HSO_CHIBI_", FACTORY="chibiOnlyServer";
    public static final String AUTH="luna/generated/ChibiLoginAuth";
    static final String DESC=HungBySonChildLoginPatcher.LOGIN_DESC;
    static Map<String,byte[]> before,after,donor;
    static Map<String,ClassNode> nodes=new LinkedHashMap<String,ClassNode>();
    static Set<String> changed=new LinkedHashSet<String>();
    static Properties p=new Properties();
    static ClassNode list,config,sender;
    static MethodNode connect,login;
    static FieldInsnNode selector;
    static String host,version;
    static int port;

    public static void main(String[] args)throws Exception {
        check(args.length==4,"input.jar Hso.jar output.jar report.properties");
        File input=new File(args[0]),reference=new File(args[1]),output=new File(args[2]);
        check(INPUT.equals(hash(input))&&DONOR.equals(hash(reference)),"Pinned source/reference mismatch");
        check(!output.exists(),"Output exists");before=read(input);after=new LinkedHashMap<String,byte[]>(before);donor=read(reference);
        for(Map.Entry<String,byte[]> e:before.entrySet())if(e.getKey().endsWith(".class")){ClassNode c=node(e.getValue());nodes.put(c.name,c);}
        discover();patchServers();patchLogin();patchStorage();
        for(String name:changed)after.put(name+".class",bytes(nodes.get(name),before.get(name+".class")));
        Manifest manifest=new Manifest(new ByteArrayInputStream(before.get("META-INF/MANIFEST.MF")));
        Attributes a=manifest.getMainAttributes();String midlet=a.getValue("MIDlet-1");check(midlet!=null&&midlet.indexOf(',')>0,"MIDlet entry");
        a.putValue("MIDlet-Name",LABEL);a.putValue("MIDlet-Vendor",LABEL);a.putValue("MIDlet-1",LABEL+midlet.substring(midlet.indexOf(',')));
        ByteArrayOutputStream manifestBytes=new ByteArrayOutputStream();manifest.write(manifestBytes);after.put("META-INF/MANIFEST.MF",manifestBytes.toByteArray());
        JarFile source=new JarFile(input);JarOutputStream out=new JarOutputStream(new FileOutputStream(output));
        try {for(Enumeration<JarEntry> entries=source.entries();entries.hasMoreElements();){JarEntry old=entries.nextElement(),entry=new JarEntry(old.getName());entry.setTime(old.getTime());out.putNextEntry(entry);out.write(after.get(old.getName()));out.closeEntry();}
            for(String name:after.keySet())if(!before.containsKey(name)){JarEntry entry=new JarEntry(name);entry.setTime(0);out.putNextEntry(entry);out.write(after.get(name));out.closeEntry();}
        }finally{out.close();source.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("reference.sha256",DONOR);p.setProperty("output.sha256",hash(output));
        p.setProperty("changed.classes",join(changed));p.setProperty("added.classes",AUTH);p.setProperty("suite.name",LABEL);p.setProperty("suite.vendor",LABEL);p.setProperty("rms.prefix",PREFIX);
        Writer report=new OutputStreamWriter(new FileOutputStream(args[3]),"UTF-8");try{p.store(report,"HSO CHIBI from pinned Hung + Hso reference");}finally{report.close();}
        System.out.println("CHIBI_PATCH=PASS; endpoint="+host+":"+port+"; singleton server; isolated RMS; SHA256="+hash(output));
    }
    static void discover() {
        for(ClassNode c:nodes.values())for(MethodNode m:c.methods){
            if(DESC.equals(m.desc)&&HungBySonChildLoginPatcher.countUtf(m)==7){check(login==null,"Ambiguous login");sender=c;login=m;}
            if("o".equals(m.name)&&"()V".equals(m.desc)&&has(m,"4.0.2")){check(connect==null,"Ambiguous connect");config=c;connect=m;}
            if("<clinit>".equals(m.name)&&has(m,"hs1.teamobi.com")){check(list==null,"Ambiguous server tables");list=c;}
        }
        check(list!=null&&config!=null&&sender!=null,"Native symbols missing");
        for(AbstractInsnNode n:connect.instructions.toArray())if(n.getOpcode()==IF_ICMPNE&&integer(prev(n))==8){check(selector==null,"Ambiguous selector");selector=(FieldInsnNode)prev(prev(n));}
        check(selector!=null&&selector.owner.equals(config.name)&&selector.desc.equals("B"),"Selector mapping");
        p.setProperty("server.list.owner",list.name);p.setProperty("selector.owner",selector.owner);p.setProperty("selector.name",selector.name);p.setProperty("selector.desc","B");
        p.setProperty("connection.owner",config.name);p.setProperty("connection.method",connect.name);p.setProperty("login.owner",sender.name);p.setProperty("login.method",login.name);p.setProperty("login.desc",DESC);
        // The donor configuration is verified again below by the packet fixture.
        host="103.77.173.232";port=19129;version="9.9.9";
        int hosts=0,versions=0;for(byte[] b:donor.values())if(b.length>4&&b[0]==(byte)0xca){ClassNode c=node(b);for(MethodNode m:c.methods){if(has(m,host))hosts++;if(has(m,version))versions++;}}
        check(hosts>0&&versions>0,"Donor endpoint/version changed");
        p.setProperty("server.host",host);p.setProperty("server.port",String.valueOf(port));p.setProperty("server.label",LABEL);p.setProperty("server.index","0");p.setProperty("server.count","1");p.setProperty("login.version",version);
    }
    static void patchServers() {
        Set<String> tables=new LinkedHashSet<String>();for(FieldNode f:list.fields)if((f.access&ACC_STATIC)!=0&&f.desc.equals("[[Ljava/lang/String;"))tables.add(f.name);
        check(tables.size()==2,"Server table count");
        MethodNode factory=new MethodNode(ACC_PUBLIC|ACC_STATIC,FACTORY,"()[[Ljava/lang/String;",null,null);InsnList b=factory.instructions;
        b.add(new InsnNode(ICONST_1));b.add(new TypeInsnNode(ANEWARRAY,"[Ljava/lang/String;"));b.add(new InsnNode(DUP));b.add(new InsnNode(ICONST_0));b.add(new InsnNode(ICONST_2));b.add(new TypeInsnNode(ANEWARRAY,"java/lang/String"));
        b.add(new InsnNode(DUP));b.add(new InsnNode(ICONST_0));b.add(new LdcInsnNode(LABEL));b.add(new InsnNode(AASTORE));b.add(new InsnNode(DUP));b.add(new InsnNode(ICONST_1));b.add(new LdcInsnNode(host));b.add(new InsnNode(AASTORE));b.add(new InsnNode(AASTORE));b.add(new InsnNode(ARETURN));list.methods.add(factory);changed.add(list.name);
        int refresh=0,fetches=0;
        for(ClassNode c:nodes.values())for(MethodNode m:c.methods)if(m.desc.equals("(Ljava/lang/String;Z)V")&&has(m,"listServer")) {
            boolean writes=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTSTATIC){FieldInsnNode f=(FieldInsnNode)n;if(f.owner.equals(list.name)&&tables.contains(f.name))writes=true;}
            if(writes){clear(m);m.instructions.add(new InsnNode(RETURN));p.setProperty("server.refresh.owner",c.name);p.setProperty("server.refresh.method",m.name);changed.add(c.name);refresh++;}
        }
        check(refresh==1,"List refresh parser count");
        for(ClassNode c:nodes.values()){
            boolean screen=c.name.equals(p.getProperty("server.refresh.owner"));for(MethodNode m:c.methods)if(has(m,"List_Ip_Server_Screen vecchoice"))screen=true;
            if(screen)for(MethodNode m:c.methods)if(m.name.equals("<init>"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)n;if(call.owner.equals("com/silverknight/TemMidlet")&&call.name.equals("c")&&call.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")){InsnNode pop=new InsnNode(POP);m.instructions.set(n,pop);m.instructions.insert(pop,new InsnNode(ACONST_NULL));changed.add(c.name);fetches++;}}
        }
        check(fetches==2,"List-only HTTP sites");int tableWrites=0,selectorWrites=0,initialTables=0;
        for(ClassNode c:nodes.values())for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTSTATIC){FieldInsnNode f=(FieldInsnNode)n;
            if(f.owner.equals(list.name)&&tables.contains(f.name)&&f.desc.equals("[[Ljava/lang/String;")){
                if(c==list&&m.name.equals("<clinit>")){AbstractInsnNode allocation=prev(n);while(allocation!=null&&!(allocation instanceof TypeInsnNode&&allocation.getOpcode()==ANEWARRAY&&((TypeInsnNode)allocation).desc.equals("[Ljava/lang/String;")))allocation=prev(allocation);check(allocation!=null&&integer(prev(allocation))>1,"Table initializer");remove(m,prev(allocation),n);initialTables++;}
                else m.instructions.insertBefore(n,new InsnNode(POP));
                m.instructions.insertBefore(n,new MethodInsnNode(INVOKESTATIC,list.name,FACTORY,"()[[Ljava/lang/String;",false));tableWrites++;changed.add(c.name);
            }
            if(f.owner.equals(selector.owner)&&f.name.equals(selector.name)&&f.desc.equals("B")){InsnList reset=new InsnList();reset.add(new InsnNode(POP));reset.add(new InsnNode(ICONST_0));m.instructions.insertBefore(n,reset);selectorWrites++;changed.add(c.name);}
        }
        check(initialTables==2&&tableWrites>=4&&selectorWrites>=4,"Assignment topology");
        int metadata=0;MethodNode init=method(config,"<clinit>","()V");
        for(AbstractInsnNode n:init.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTSTATIC&&((FieldInsnNode)n).desc.equals("[I")){
            AbstractInsnNode allocation=prev(n);while(allocation!=null&&allocation.getOpcode()!=NEWARRAY&&allocation.getOpcode()!=PUTSTATIC)allocation=prev(allocation);
            if(!(allocation instanceof IntInsnNode)||((IntInsnNode)allocation).operand!=T_INT||integer(prev(allocation))!=10)continue;
            int value=0;for(AbstractInsnNode cursor=next(allocation);cursor!=n;){check(cursor.getOpcode()==DUP,"Metadata shape");AbstractInsnNode index=next(cursor),item=next(index),store=next(item);check(store.getOpcode()==IASTORE,"Metadata item");if(integer(index)==8)value=integer(item);cursor=next(store);}
            remove(init,prev(allocation),n);InsnList one=new InsnList();one.add(new InsnNode(ICONST_1));one.add(new IntInsnNode(NEWARRAY,T_INT));one.add(new InsnNode(DUP));one.add(new InsnNode(ICONST_0));one.add(new IntInsnNode(SIPUSH,value));one.add(new InsnNode(IASTORE));init.instructions.insertBefore(n,one);metadata++;
        }
        check(metadata==2,"Port/language arrays");check(HsoChildSingleServerPatcher.reindexGuard(connect,selector.owner,selector.name)==1,"Direct connect guard");
        int direct=0;for(AbstractInsnNode n:connect.instructions.toArray())if(n instanceof MethodInsnNode&&((MethodInsnNode)n).desc.equals("(Ljava/lang/String;I)V")&&prev(n) instanceof FieldInsnNode){AbstractInsnNode directPort=prev(n),directHost=prev(directPort),directVersion=prev(prev(prev(directHost)));check(directVersion instanceof FieldInsnNode,"Direct version");connect.instructions.set(directPort,new IntInsnNode(SIPUSH,port));connect.instructions.set(directHost,new LdcInsnNode(host));connect.instructions.set(directVersion,new LdcInsnNode(version));direct++;}
        check(direct==1,"Direct endpoint count");changed.add(config.name);
        p.setProperty("server.list.fields",join(tables));p.setProperty("server.list.factory",FACTORY);p.setProperty("server.table.writes",String.valueOf(tableWrites));p.setProperty("server.selector.writes",String.valueOf(selectorWrites));
    }
    static void patchLogin() {
        MethodNode reference=method(node(donor.get("IIlI.class")),"II",DESC);
        check(HungBySonChildLoginPatcher.countUtf(reference)==8&&has(reference,"LOCK"),"Donor login shape");
        MethodInsnNode getter=null,connected=null;for(AbstractInsnNode n:connect.instructions.toArray())if(n instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)n;if(c.getOpcode()==INVOKESTATIC&&c.desc.startsWith("()L"))getter=c;if(c.getOpcode()==INVOKEVIRTUAL&&c.desc.equals("()Z"))connected=c;}
        check(getter!=null&&connected!=null&&getter.owner.equals(connected.owner),"Session mapping");
        ClassNode session=nodes.get(getter.owner);FieldNode key=null;
        for(FieldNode f:session.fields)if(f.desc.equals("[B")&&(f.access&ACC_STATIC)==0){check(key==null,"Ambiguous session key");key=f;}
        check(key!=null&&(key.access&ACC_PUBLIC)!=0,"Session key unavailable");
        p.setProperty("session.owner",session.name);p.setProperty("session.getter",getter.name);p.setProperty("session.connected",connected.name);p.setProperty("session.key",key.name);
        List<MethodInsnNode> oldUtf=utfWrites(login),newUtf=utfWrites(reference);
        FieldInsnNode packet=null;MethodInsnNode stream=null;
        for(AbstractInsnNode n=login.instructions.getFirst();n!=oldUtf.get(0);n=n.getNext()){
            if(n instanceof FieldInsnNode&&n.getOpcode()==GETFIELD)packet=(FieldInsnNode)n;
            if(n instanceof MethodInsnNode&&((MethodInsnNode)n).desc.equals("()Ljava/io/DataOutputStream;"))stream=(MethodInsnNode)n;
        }
        check(packet!=null&&stream!=null,"Packet stream mapping");
        Map<LabelNode,LabelNode> labels=new IdentityHashMap<LabelNode,LabelNode>();for(AbstractInsnNode n:reference.instructions.toArray())if(n instanceof LabelNode)labels.put((LabelNode)n,new LabelNode());
        InsnList auth=new InsnList();Set<AbstractInsnNode> copied=Collections.newSetFromMap(new IdentityHashMap<AbstractInsnNode,Boolean>());
        for(AbstractInsnNode n=newUtf.get(4).getNext();n!=newUtf.get(5).getNext();n=n.getNext()){
            copied.add(n);if(n instanceof FrameNode)continue;AbstractInsnNode clone=n.clone(labels);
            if(clone instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)clone;
                if(f.owner.equals("IIl")&&f.name.equals("Ill")&&f.desc.equals("[B")){f.owner=session.name;f.name=key.name;}
                else if(f.owner.equals("llllll")&&f.name.equals("Il")){f.owner=packet.owner;f.name=packet.name;f.desc=packet.desc;}
                else check(f.owner.startsWith("java/"),"Unmapped donor auth field "+f.owner+"."+f.name);
            }else if(clone instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)clone;
                if(call.owner.equals("IIl")&&call.desc.equals("()LIIl;")){call.owner=getter.owner;call.name=getter.name;call.desc=getter.desc;}
                else if(call.owner.equals("IIl")&&call.desc.equals("()Z")){call.owner=connected.owner;call.name=connected.name;}
                else if(call.owner.equals("lII")&&call.desc.equals("()Ljava/io/DataOutputStream;")){call.owner=stream.owner;call.name=stream.name;}
                else if(call.owner.equals("IIIl")){call.owner=AUTH;}
                else check(call.owner.startsWith("java/"),"Unmapped donor auth method "+call.owner+"."+call.name);
            }
            auth.add(clone);
        }
        for(TryCatchBlockNode t:reference.tryCatchBlocks)if(copied.contains(t.start)&&copied.contains(t.end)&&copied.contains(t.handler))login.tryCatchBlocks.add(0,new TryCatchBlockNode(labels.get(t.start),labels.get(t.end),labels.get(t.handler),t.type));
        AbstractInsnNode afterSixth=oldUtf.get(5).getNext();remove(login,oldUtf.get(4).getNext(),afterSixth);login.instructions.insertBefore(afterSixth,auth);
        // Native Hung ends after the device-info UTF; the reference adds a final UTF.
        InsnList lock=new InsnList();lock.add(new VarInsnNode(ALOAD,0));lock.add(new FieldInsnNode(GETFIELD,packet.owner,packet.name,packet.desc));lock.add(new MethodInsnNode(stream.getOpcode(),stream.owner,stream.name,stream.desc,false));lock.add(new LdcInsnNode("LOCK"));lock.add(new MethodInsnNode(INVOKEVIRTUAL,"java/io/DataOutputStream","writeUTF","(Ljava/lang/String;)V",false));login.instructions.insert(oldUtf.get(6),lock);
        InsnList params=new InsnList();params.add(new LdcInsnNode(version));params.add(new VarInsnNode(ASTORE,3));for(int local=4;local<=6;local++){params.add(new LdcInsnNode("0"));params.add(new VarInsnNode(ASTORE,local));}login.instructions.insert(params);changed.add(sender.name);
        ClassNode helper=node(donor.get("IIIl.class"));helper.name=AUTH;
        for(MethodNode m:helper.methods)for(AbstractInsnNode n:m.instructions.toArray()){
            if(n instanceof FieldInsnNode&&((FieldInsnNode)n).owner.equals("IIIl"))((FieldInsnNode)n).owner=AUTH;
            if(n instanceof MethodInsnNode&&((MethodInsnNode)n).owner.equals("IIIl"))((MethodInsnNode)n).owner=AUTH;
        }
        check(!before.containsKey(AUTH+".class"),"Auth class collision");after.put(AUTH+".class",bytes(helper,null));
        p.setProperty("login.auth.owner",AUTH);p.setProperty("login.auth.method","II");p.setProperty("login.auth.source","IIIl.class");p.setProperty("login.utf.count","8");p.setProperty("login.final.utf","LOCK");
        int callers=0;for(ClassNode c:nodes.values())for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)n;if(call.owner.equals(sender.name)&&call.name.equals(login.name)&&call.desc.equals(DESC))callers++;}
        check(callers==4,"Login call-site count changed");p.setProperty("login.calls","4");
    }
    static List<MethodInsnNode> utfWrites(MethodNode m){List<MethodInsnNode> result=new ArrayList<MethodInsnNode>();for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)n;if(c.owner.equals("java/io/DataOutputStream")&&c.name.equals("writeUTF"))result.add(c);}return result;}
    static void patchStorage() {
        final String storage="luna/generated/InvestigateStorage", shared="luna/generated/ExpEstimateSharedRmsStore";
        ClassNode boundary=nodes.get(storage);check(boundary!=null&&nodes.containsKey(shared),"Storage boundaries missing");
        int constants=0;for(FieldNode f:boundary.fields)if("inv_".equals(f.value)){f.value=PREFIX;constants++;}
        for(MethodNode m:boundary.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof LdcInsnNode&&"inv_".equals(((LdcInsnNode)n).cst)){((LdcInsnNode)n).cst=PREFIX;constants++;}
        check(constants==6,"Storage prefix layout changed");changed.add(storage);
        // A new endpoint must never seed its level table from another server.
        MethodNode migration=method(nodes.get(shared),"migrateLegacy","()V");clear(migration);migration.instructions.add(new InsnNode(RETURN));changed.add(shared);
        int redirected=0;for(ClassNode c:nodes.values())if(c!=boundary)for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode){
            MethodInsnNode call=(MethodInsnNode)n;if(call.getOpcode()==INVOKESTATIC&&call.owner.equals("javax/microedition/rms/RecordStore")&&Arrays.asList("openRecordStore","deleteRecordStore","listRecordStores").contains(call.name)){call.owner=storage;changed.add(c.name);redirected++;}
        }
        check(redirected==4,"Unexpected direct MOD RMS call inventory: "+redirected);
        p.setProperty("rms.boundary",storage);p.setProperty("rms.redirected.calls",String.valueOf(redirected));p.setProperty("rms.exp.legacy.import","disabled");
    }
    static void clear(MethodNode m){m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();m.maxStack=0;}
    static void remove(MethodNode m,AbstractInsnNode start,AbstractInsnNode end){for(AbstractInsnNode n=start;n!=end;){check(n!=null,"Invalid instruction range");AbstractInsnNode following=n.getNext();m.instructions.remove(n);n=following;}}
    static ClassNode node(byte[] data){ClassNode c=new ClassNode();new ClassReader(data).accept(c,0);return c;}
    static MethodNode method(ClassNode c,String name,String desc){return HsoChildSingleServerPatcher.method(c,name,desc);}
    static byte[] bytes(ClassNode c,byte[] previous){ClassWriter w=previous==null?new ClassWriter(ClassWriter.COMPUTE_MAXS):new ClassWriter(new ClassReader(previous),ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();}
    static AbstractInsnNode prev(AbstractInsnNode n){return HungBySonChildLoginPatcher.previous(n);}static AbstractInsnNode next(AbstractInsnNode n){return HungBySonChildLoginPatcher.next(n);}static int integer(AbstractInsnNode n){return HungBySonChildLoginPatcher.intValue(n);}
    static boolean has(MethodNode m,Object value){return HungBySonChildLoginPatcher.hasConstant(m,value);}static Map<String,byte[]> read(File f)throws Exception{return HungBySonChildLoginPatcher.read(f);}static String hash(File f)throws Exception{return HungBySonChildLoginPatcher.hash(f);}
    static String join(Collection<String> values){return HsoChildSingleServerPatcher.join(values);}static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
