import java.io.*; import java.net.*; import java.nio.file.*; import java.security.*; import java.util.*; import java.util.jar.*; import java.util.zip.CRC32; import java.lang.reflect.*; import org.objectweb.asm.*; import org.objectweb.asm.tree.*; import javax.microedition.rms.RecordStore; import org.microemu.MIDletBridge; import org.microemu.app.Common; import org.microemu.util.MemoryRecordStoreManager;
public final class HungBySonInvestigateBinaryTest {
    static final String SUPPORT="luna/features/MonsterCacheSupport", BRIDGE="luna/generated/HungMonsterCacheAccess", STORAGE="luna/generated/InvestigateStorage", RMS="javax/microedition/rms/RecordStore";
    static Set<String> csv(String s) { return new LinkedHashSet<String>(Arrays.asList(s.split(","))); }
    static ClassNode node(byte[] b) { ClassNode c=new ClassNode();new ClassReader(b).accept(c,0);return c; }
    static List<AbstractInsnNode> real(MethodNode m) { List<AbstractInsnNode> r=new ArrayList<AbstractInsnNode>();for(AbstractInsnNode i:m.instructions.toArray())if(i.getOpcode()>=0)r.add(i);return r; }
    static byte[] canonical(ClassNode c) { ClassWriter w=new ClassWriter(0);c.accept(w);return w.toByteArray(); }
    static MethodNode method(ClassNode c,String n,String d) { for(MethodNode m:c.methods)if(m.name.equals(n)&&m.desc.equals(d))return m;throw new AssertionError("Missing method "+n+d); }
    public static void main(String[] args)throws Exception {
        require(args.length==4,"baseline.jar candidate.jar catalog.properties micro.jar");
        Properties p=new Properties();InputStream pi=new FileInputStream(args[2]);try{p.load(pi);}finally{pi.close();}
        File baseline=new File(args[0]),candidate=new File(args[1]);
        require(hash(baseline).equals(p.getProperty("input.sha256")),"Baseline hash");require(hash(candidate).equals(p.getProperty("output.sha256")),"Candidate hash");
        Map<String,byte[]> old=read(baseline),actual=read(candidate);Set<String> added=csv(p.getProperty("added.classes")),allowed=csv(p.getProperty("changed.classes"));
        Set<String> expected=new LinkedHashSet<String>(old.keySet());for(String n:added)require(expected.add(n),"Added class collision");require(expected.equals(actual.keySet()),"Exact ZIP topology");
        Set<String> changed=new LinkedHashSet<String>();int classes=0;
        for(String n:old.keySet()) {
            if(!Arrays.equals(old.get(n),actual.get(n))){if(!n.equals("META-INF/MANIFEST.MF")){require(n.endsWith(".class")&&allowed.contains(n.substring(0,n.length()-6)),"Unexpected change "+n);changed.add(n);}}
            if(n.endsWith(".class"))require(major(old.get(n))==major(actual.get(n)),"Native version changed "+n);
        }
        for(String n:allowed)require(changed.contains(n+".class"),"Unused modification allow-list "+n);
        for(String n:actual.keySet())if(n.endsWith(".class")){classes++;require(major(actual.get(n))<=51,"Major >51 "+n);if(added.contains(n))require(major(actual.get(n))>=48,"Invalid added class major");}
        Manifest om=new Manifest(new ByteArrayInputStream(old.get("META-INF/MANIFEST.MF"))),nm=new Manifest(new ByteArrayInputStream(actual.get("META-INF/MANIFEST.MF")));
        require(om.getEntries().equals(nm.getEntries()),"Manifest sections changed");Attributes oa=om.getMainAttributes(),na=nm.getMainAttributes();
        require("Hung_By_Son Investigate".equals(na.getValue("MIDlet-Name"))&&"Hung_By_Son_Investigate".equals(na.getValue("MIDlet-Vendor")),"Variant identity");
        for(Object key:oa.keySet())if(!key.toString().equals("MIDlet-Name")&&!key.toString().equals("MIDlet-Vendor")&&!key.toString().equals("MIDlet-1"))require(Objects.equals(oa.get(key),na.get(key)),"Manifest attr "+key);
        require(oa.size()==na.size(),"Manifest attr topology");String[] one=oa.getValue("MIDlet-1").split(",",-1),two=na.getValue("MIDlet-1").split(",",-1);require(two.length==one.length&&two.length==3&&"Hung_By_Son Investigate".equals(two[0])&&one[1].equals(two[1])&&one[2].equals(two[2]),"MIDlet entry ABI");
        int baselineRms=0,rewritten=0;
        for(String n:old.keySet())if(n.endsWith(".class")) {
            ClassNode before=node(old.get(n)),after=node(actual.get(n));
            for(MethodNode m:before.methods)for(AbstractInsnNode i:real(m))if(i instanceof MethodInsnNode&&i.getOpcode()==Opcodes.INVOKESTATIC&&RMS.equals(((MethodInsnNode)i).owner))baselineRms++;
            for(MethodNode m:after.methods)for(AbstractInsnNode i:real(m))if(i instanceof MethodInsnNode&&i.getOpcode()==Opcodes.INVOKESTATIC){MethodInsnNode x=(MethodInsnNode)i;require(!RMS.equals(x.owner),"Unscoped native RMS");if(STORAGE.equals(x.owner))rewritten++;}
            if(changed.contains(n))inverse(before,after);
        }
        require(baselineRms==16&&rewritten==baselineRms&&Integer.parseInt(p.getProperty("rms.calls"))==rewritten,"RMS rewrite count");
        for(String n:actual.keySet())if(n.endsWith(".class")&&!n.equals(STORAGE+".class"))for(MethodNode m:node(actual.get(n)).methods)for(AbstractInsnNode i:real(m))if(i instanceof MethodInsnNode&&i.getOpcode()==Opcodes.INVOKESTATIC)require(!RMS.equals(((MethodInsnNode)i).owner),"Direct RMS outside storage "+n);
        require("500".equals(p.getProperty("auto.delay.ms"))&&"300".equals(p.getProperty("request.extra.ms")),"Native timing metadata");
        hooks(actual,p,"object4","onObject",1,6);hooks(actual,p,"detail7","onDetail",1,6);hooks(actual,p,"map12","onMap",2,0);hooks(actual,p,"tick","tick",Integer.parseInt(p.getProperty("hook.tick.count")),0);hooks(actual,p,"paint","paint",Integer.parseInt(p.getProperty("hook.paint.count")),0);hooks(actual,p,"endpoint","select",1,-1);
        if(p.getProperty("hook.destroy.owner")!=null)hooks(actual,p,"destroy","flushNow",Integer.parseInt(p.getProperty("hook.destroy.count","1")),0);
        URLClassLoader link=new URLClassLoader(new URL[]{candidate.toURI().toURL(),new File(args[3]).toURI().toURL()},null);int refs=0;
        try {
            for(String n:actual.keySet())if(n.endsWith(".class")){Class<?> c=Class.forName(n.substring(0,n.length()-6).replace('/','.'),false,link);c.getDeclaredFields();c.getDeclaredMethods();c.getDeclaredConstructors();}
            Set<String> examine=new LinkedHashSet<String>(changed);examine.addAll(added);
            for(String n:examine){ClassNode c=node(actual.get(n));Set<String> previous=old.containsKey(n)?references(old.get(n)):Collections.<String>emptySet();for(MethodNode m:c.methods)for(AbstractInsnNode i:real(m)) {
                if(!(i instanceof FieldInsnNode)&&!(i instanceof MethodInsnNode)||previous.contains(reference(i)))continue;
                if(i instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)i;Field r=resolveField(Class.forName(f.owner.replace('/','.'),false,link),f.name,f.desc);require(r!=null,"Missing field "+f.name);require(Modifier.isStatic(r.getModifiers())==(i.getOpcode()==Opcodes.GETSTATIC||i.getOpcode()==Opcodes.PUTSTATIC),"Field mode");accessible(c.name,r.getDeclaringClass(),r.getModifiers(),link);}
                else {MethodInsnNode x=(MethodInsnNode)i;Class<?> o=Class.forName(x.owner.replace('/','.'),false,link);if(x.name.equals("<init>")){Constructor<?> found=null;for(Constructor<?> r:o.getDeclaredConstructors())if(org.objectweb.asm.Type.getConstructorDescriptor(r).equals(x.desc))found=r;require(found!=null,"Missing ctor");accessible(c.name,found.getDeclaringClass(),found.getModifiers(),link);}else{Method r=resolveMethod(o,x.name,x.desc);require(r!=null,"Missing method "+x.owner+"."+x.name+x.desc);require(Modifier.isStatic(r.getModifiers())==(x.getOpcode()==Opcodes.INVOKESTATIC),"Method mode");accessible(c.name,r.getDeclaringClass(),r.getModifiers(),link);}}refs++;
            }}
        }finally{link.close();}
        storage(candidate);pending(actual,p);
        System.out.println("INVESTIGATE_BINARY=PASS; classes="+classes+" newReferences="+refs+" RMS="+rewritten+"; inverse native parity, hooks, shared-RMS restart, pending bridge");
    }
    static void accessible(String caller,Class<?> owner,int mods,ClassLoader loader)throws Exception {
        if(Modifier.isPublic(mods))return;String cn=caller.replace('/','.');if(cn.equals(owner.getName()))return;
        require(!Modifier.isPrivate(mods),"Private access");String op=owner.getPackage()==null?"":owner.getPackage().getName();int at=cn.lastIndexOf('.');String cp=at<0?"":cn.substring(0,at);
        require(op.equals(cp)||(Modifier.isProtected(mods)&&owner.isAssignableFrom(Class.forName(cn,false,loader))),"Member access");
    }
    static void inverse(ClassNode before,ClassNode after) {
        require(before.version==after.version&&before.fields.size()==after.fields.size()&&before.methods.size()==after.methods.size(),"Native ABI");
        for(MethodNode m:after.methods){inverseSelection(m);for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if(HungBySonStaleTargetBinaryTest.inverseHook(m,x))continue;if(STORAGE.equals(x.owner)){x.owner=RMS;continue;}int count=SUPPORT.equals(x.owner)?(x.name.equals("onObject")||x.name.equals("onDetail")?7:x.name.equals("onEntityUpdate")?3:1):BRIDGE.equals(x.owner)&&x.name.equals("select")?2:0;if(count>0){AbstractInsnNode prev=i.getPrevious();m.instructions.remove(i);while(count-->0){while(prev!=null&&prev.getOpcode()<0)prev=prev.getPrevious();require(prev!=null,"Hook shape");AbstractInsnNode next=prev.getPrevious();m.instructions.remove(prev);prev=next;}}}MethodNode original=method(before,m.name,m.desc);m.maxStack=original.maxStack;m.maxLocals=original.maxLocals;}
        require(Arrays.equals(canonical(before),canonical(after)),"Native instructions changed beyond hooks/RMS: "+before.name);
    }
    static AbstractInsnNode priorReal(AbstractInsnNode i){i=i.getPrevious();while(i!=null&&i.getOpcode()<0)i=i.getPrevious();return i;}
    static AbstractInsnNode nextReal(AbstractInsnNode i){i=i.getNext();while(i!=null&&i.getOpcode()<0)i=i.getNext();return i;}
    static void inverseSelection(MethodNode m){
        final String commands="luna/generated/HungMonsterSelectionCommands",support="luna/features/MonsterSelectionSupport",bridge="luna/generated/HungMonsterSelectionAccess";
        for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;
            if(SUPPORT.equals(x.owner)&&(x.name.equals("hold")||x.name.equals("advanceCorpse"))){AbstractInsnNode a=priorReal(i),jump=nextReal(i);require(a instanceof VarInsnNode&&a.getOpcode()==Opcodes.ALOAD&&((VarInsnNode)a).var==0&&jump instanceof JumpInsnNode&&jump.getOpcode()==Opcodes.IFEQ,"Pending death guard shape");LabelNode end=((JumpInsnNode)jump).label;require(x.name.equals("advanceCorpse")||nextReal(jump).getOpcode()==Opcodes.RETURN,"Pending death early return");AbstractInsnNode cursor=a;while(cursor!=end){require(cursor!=null,"Pending death label");AbstractInsnNode next=cursor.getNext();m.instructions.remove(cursor);cursor=next;}m.instructions.remove(end);}
            else if(commands.equals(x.owner)&&x.name.equals("append")){AbstractInsnNode a=priorReal(i);require(a instanceof VarInsnNode&&a.getOpcode()==Opcodes.ALOAD&&((VarInsnNode)a).var==0,"Selection menu hook");m.instructions.remove(a);m.instructions.remove(i);}
            else if(support.equals(x.owner)&&x.name.equals("allow")){AbstractInsnNode a=priorReal(i),b=priorReal(a);require(a instanceof VarInsnNode&&a.getOpcode()==Opcodes.ALOAD&&((VarInsnNode)a).var==0&&b instanceof FieldInsnNode&&b.getOpcode()==Opcodes.GETSTATIC&&((FieldInsnNode)b).owner.equals(bridge),"Selection passing gate");m.instructions.remove(a);m.instructions.remove(b);m.instructions.set(i,new InsnNode(Opcodes.ICONST_1));}
            else if(commands.equals(x.owner)&&x.name.equals("allow")&&m.name.equals("bachHoMonsterTargetGate")){AbstractInsnNode a=priorReal(i);require(a instanceof VarInsnNode&&a.getOpcode()==Opcodes.ALOAD&&((VarInsnNode)a).var==0&&nextReal(i).getOpcode()==Opcodes.IRETURN,"Composed retained-target gate");m.instructions.remove(a);m.instructions.set(i,new InsnNode(Opcodes.ICONST_1));}
            else if(commands.equals(x.owner)&&x.name.equals("allow")){AbstractInsnNode a=priorReal(i),jump=nextReal(i);require(a instanceof VarInsnNode&&a.getOpcode()==Opcodes.ALOAD&&jump instanceof JumpInsnNode,"Gather gate shape");int var=((VarInsnNode)a).var;
                if(var==4){require(jump.getOpcode()==Opcodes.IFEQ,"Secondary guard");m.instructions.remove(a);m.instructions.remove(i);m.instructions.remove(jump);}
                else{require(var==2&&jump.getOpcode()==Opcodes.IFNE,"Primary guard");LabelNode end=((JumpInsnNode)jump).label;AbstractInsnNode cursor=a;while(cursor!=end){require(cursor!=null,"Primary hook target");AbstractInsnNode next=cursor.getNext();m.instructions.remove(cursor);cursor=next;}m.instructions.remove(end);}
            }
        }
    }
    static void hooks(Map<String,byte[]> jar,Properties p,String key,String call,int count,int ints) {
        String prefix="hook."+key;MethodNode m=method(node(jar.get(p.getProperty(prefix+".owner")+".class")),p.getProperty(prefix+".method"),p.getProperty(prefix+".descriptor"));List<AbstractInsnNode> r=real(m);int hits=0;
        for(int k=0;k<r.size();k++)if(r.get(k) instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)r.get(k);if(x.name.equals(call)&&x.owner.equals(ints<0?BRIDGE:SUPPORT)) {hits++;require(x.getOpcode()==Opcodes.INVOKESTATIC,"Static hook");if(ints==6){for(int j=1;j<=6;j++){AbstractInsnNode a=r.get(k-7+j);require(a instanceof VarInsnNode&&a.getOpcode()==Opcodes.ILOAD&&((VarInsnNode)a).var==j,"Packet hook argument "+j);}AbstractInsnNode b=r.get(k-8);if(key.equals("object4"))require(b instanceof VarInsnNode&&b.getOpcode()==Opcodes.ISTORE&&((VarInsnNode)b).var==6,"Packet4 prefix");else require(b instanceof FieldInsnNode&&b.getOpcode()==Opcodes.PUTFIELD&&((FieldInsnNode)b).name.equals("cX"),"Packet7 after loaded HP");} }}require(hits==count,"Hook count "+key);
    }
    static void storage(File candidate)throws Exception {
        Common emulator=new Common(null);MemoryRecordStoreManager manager=new MemoryRecordStoreManager();manager.init(emulator);emulator.setRecordStoreManager(manager);MIDletBridge.setMicroEmulator(emulator);
        RecordStore original=RecordStore.openRecordStore("login",true);original.addRecord(new byte[]{99},0,1);original.closeRecordStore();
        URLClassLoader loader=new URLClassLoader(new URL[]{candidate.toURI().toURL()},HungBySonInvestigateBinaryTest.class.getClassLoader());byte[] saved={5,7,11};
        try {Class<?> s=Class.forName(STORAGE.replace('/','.'),true,loader);RecordStore own=(RecordStore)s.getMethod("openRecordStore",String.class,boolean.class).invoke(null,"login",true);own.addRecord(new byte[]{1},0,1);own.closeRecordStore();String[] names=(String[])s.getMethod("listRecordStores").invoke(null);require(names!=null&&new HashSet<String>(Arrays.asList(names)).equals(Collections.singleton("login")),"RMS scoped listing");s.getMethod("deleteRecordStore",String.class).invoke(null,"login");original=RecordStore.openRecordStore("login",false);require(original.getRecord(1)[0]==99,"RMS original overwritten");original.closeRecordStore();require(Boolean.TRUE.equals(s.getMethod("save",byte[].class).invoke(null,(Object)saved)),"Cache save");require(Arrays.equals(saved,(byte[])s.getMethod("load").invoke(null)),"Cache roundtrip");}finally{loader.close();}
        loader=new URLClassLoader(new URL[]{candidate.toURI().toURL()},HungBySonInvestigateBinaryTest.class.getClassLoader());try{Class<?> s=Class.forName(STORAGE.replace('/','.'),true,loader);require(Arrays.equals(saved,(byte[])s.getMethod("load").invoke(null)),"Fresh-loader persistence");}finally{loader.close();}
    }
    static void pending(final Map<String,byte[]> jar,Properties p)throws Exception {
        ClassLoader loader=new ClassLoader(HungBySonInvestigateBinaryTest.class.getClassLoader()) {protected Class<?> findClass(String name)throws ClassNotFoundException {byte[] b=jar.get(name.replace('.','/')+".class");if(b==null)throw new ClassNotFoundException(name);ClassNode c=node(b);if(!name.startsWith("luna.")){for(Iterator<MethodNode> i=c.methods.iterator();i.hasNext();)if(i.next().name.equals("<clinit>"))i.remove();b=canonical(c);}return defineClass(name,b,0,b.length);}};
        Class<?> bridge=Class.forName(BRIDGE.replace('/','.'),true,loader),entity=Class.forName(p.getProperty("entity.owner").replace('/','.'),false,loader);Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field uf=unsafe.getDeclaredField("theUnsafe");uf.setAccessible(true);Object u=uf.get(null),e=unsafe.getMethod("allocateInstance",Class.class).invoke(u,entity);Object access=bridge.getField("INSTANCE").get(null);Method pending=bridge.getMethod("pending",Object.class,boolean.class);
        pending.invoke(access,e,true);require(entity.getField("bs").getInt(e)==0&&entity.getField("cG").getInt(e)==4&&entity.getField("dz").getBoolean(e)&&entity.getField("cX").getBoolean(e),"Pending quarantine");entity.getField("bs").setInt(e,37);pending.invoke(access,e,false);require(entity.getField("cG").getInt(e)==0&&!entity.getField("dz").getBoolean(e),"Verified revival");entity.getField("bs").setInt(e,0);pending.invoke(access,e,false);require(entity.getField("cG").getInt(e)==4,"Dead entity remains unavailable");
    }
    private static Set<String> references(byte[] bytes) {
        Set<String> refs = new HashSet<String>(); ClassNode c = new ClassNode(); new ClassReader(bytes).accept(c, 0);
        for (MethodNode m : c.methods) for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof FieldInsnNode || i instanceof MethodInsnNode) refs.add(reference(i));
        return refs;
    }
    private static String reference(AbstractInsnNode insn) {
        if (insn instanceof FieldInsnNode) { FieldInsnNode f = (FieldInsnNode) insn; return f.getOpcode() + " " + f.owner + "." + f.name + " " + f.desc; }
        MethodInsnNode m = (MethodInsnNode) insn; return m.getOpcode() + " " + m.owner + "." + m.name + m.desc;
    }
    private static Field resolveField(Class<?> type, String name, String desc) {
        if (type == null) return null;
        for (Field f : type.getDeclaredFields()) if (f.getName().equals(name) && org.objectweb.asm.Type.getDescriptor(f.getType()).equals(desc)) return f;
        Field inherited = resolveField(type.getSuperclass(), name, desc); if (inherited != null) return inherited;
        for (Class<?> parent : type.getInterfaces()) { inherited = resolveField(parent, name, desc); if (inherited != null) return inherited; }
        return null;
    }
    private static Method resolveMethod(Class<?> type, String name, String desc) {
        if (type == null) return null;
        for (Method m : type.getDeclaredMethods()) if (m.getName().equals(name) && org.objectweb.asm.Type.getMethodDescriptor(m).equals(desc)) return m;
        Method inherited = resolveMethod(type.getSuperclass(), name, desc); if (inherited != null) return inherited;
        for (Class<?> parent : type.getInterfaces()) { inherited = resolveMethod(parent, name, desc); if (inherited != null) return inherited; }
        return null;
    }
    private static Map<String, byte[]> read(File file) throws Exception {
        Map<String, byte[]> data = new LinkedHashMap<String, byte[]>();
        JarFile jar = new JarFile(file);
        try {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                InputStream in = jar.getInputStream(entry);
                try { byte[] block = new byte[8192]; for (int n; (n = in.read(block)) != -1;) out.write(block, 0, n); }
                finally { in.close(); }
                byte[] bytes = out.toByteArray();
                CRC32 crc = new CRC32(); crc.update(bytes);
                require(crc.getValue() == entry.getCrc(), "CRC mismatch: " + entry.getName());
                require(!data.containsKey(entry.getName()), "duplicate entry: " + entry.getName());
                data.put(entry.getName(), bytes);
            }
        } finally { jar.close(); }
        return data;
    }
    private static int major(byte[] bytes) {
        require(bytes.length >= 8 && bytes[0] == (byte)0xCA && bytes[1] == (byte)0xFE
                && bytes[2] == (byte)0xBA && bytes[3] == (byte)0xBE, "invalid class header");
        return ((bytes[6] & 255) << 8) | (bytes[7] & 255);
    }
    private static String hash(File file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder result = new StringBuilder();
        for (byte b : digest) result.append(String.format("%02X", Integer.valueOf(b & 255)));
        return result.toString();
    }
    private static void require(boolean okay, String message) { if (!okay) throw new IllegalStateException(message); }
}

