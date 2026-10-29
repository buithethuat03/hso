import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Runs native death/removal and the recovery support together, without a server.
 * Native initializers and rendering effects are suppressed in the harness only.
 * The candidate's death method, packet-8 handler, bridge and support are unchanged.
 */
public final class HungBySonMonsterRecoveryBinaryTest implements Opcodes {
    static final String SUPPORT = "luna.features.MonsterCacheSupport";
    static int checks;
    static void require(boolean ok, String text) {
        checks++;
        if (!ok) throw new AssertionError(text);
    }
    static String n(String tail) {
        StringBuilder b = new StringBuilder();
        while (b.length() + tail.length() < 100) b.append('i');
        return b.append(tail).toString();
    }
    static ClassNode node(byte[] bytes) {
        ClassNode c = new ClassNode(); new ClassReader(bytes).accept(c, 0); return c;
    }
    static byte[] bytes(ClassNode c) {
        ClassWriter w = new ClassWriter(0); c.accept(w); return w.toByteArray();
    }
    static Map<String, byte[]> read(File f) throws Exception {
        Map<String, byte[]> out = new HashMap<String, byte[]>();
        JarFile jar = new JarFile(f);
        try {
            for (Enumeration<JarEntry> es = jar.entries(); es.hasMoreElements();) {
                JarEntry e = es.nextElement(); if (!e.getName().endsWith(".class")) continue;
                InputStream in = jar.getInputStream(e); ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[4096]; int count;
                try { while ((count = in.read(buf)) >= 0) b.write(buf, 0, count); } finally { in.close(); }
                out.put(e.getName(), b.toByteArray());
            }
        } finally { jar.close(); }
        return out;
    }
    static final class Loader extends ClassLoader {
        final Map<String, byte[]> entries; final String world;
        Loader(Map<String, byte[]> entries, String world) {
            super(HungBySonMonsterRecoveryBinaryTest.class.getClassLoader());
            this.entries = entries; this.world = world;
        }
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> c = findLoadedClass(name);
            if (c == null) c = entries.containsKey(name.replace('.', '/') + ".class")
                    ? findClass(name) : super.loadClass(name, false);
            if (resolve) resolveClass(c); return c;
        }
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] b = entries.get(name.replace('.', '/') + ".class");
            if (b == null) throw new ClassNotFoundException(name);
            if (!name.startsWith("luna.")) {
                ClassNode c = node(b);
                for (Iterator<MethodNode> it = c.methods.iterator(); it.hasNext();)
                    if (it.next().name.equals("<clinit>")) it.remove();
                // Rendering effects are irrelevant to the lifecycle, and require a MIDlet.
                if (name.equals(world)) for (MethodNode m : c.methods)
                    if ((m.access & ACC_STATIC) != 0 && m.desc.equals("(III)V")) {
                        m.instructions.clear(); m.tryCatchBlocks.clear();
                        m.instructions.add(new InsnNode(RETURN)); m.maxStack = 0; m.maxLocals = 3;
                    }
                b = bytes(c);
            }
            return defineClass(name, b, 0, b.length);
        }
    }
    static final class Harness implements InvocationHandler {
        final Loader loader; final Class<?> api, support, mob, entity, world, bridge, message, packets;
        final Object access, nativeAccess, vector;
        final Method add, remove, pending, death, worldLoop;
        final Map<Integer, Object> monsters = new HashMap<Integer, Object>();
        long now = 10000; int requests, restores;
        Harness(Map<String, byte[]> jar, Properties p) throws Exception {
            extractWorldLoop(jar,p);
            loader = new Loader(jar, p.getProperty("world.owner"));
            api = loader.loadClass("luna.api.MonsterCacheAccess");
            support = loader.loadClass(SUPPORT); mob = loader.loadClass(p.getProperty("mob.owner").replace('/','.'));
            entity = loader.loadClass(p.getProperty("entity.owner").replace('/','.')); world = loader.loadClass(p.getProperty("world.owner").replace('/','.'));
            bridge = loader.loadClass(p.getProperty("bridge.owner").replace('/','.')); nativeAccess = bridge.getField("INSTANCE").get(null);
            pending = bridge.getMethod("pending", Object.class, Boolean.TYPE);
            death = mob.getMethod("a_");
            Class<?> et = loader.loadClass("et"); vector = et.getConstructor(String.class).newInstance("recovery fixture");
            add = et.getMethod(n("il"), Object.class); remove = et.getMethod(n("ilii"), Object.class);
            world.getField(p.getProperty("list.field")).set(null, vector);
            ClassNode wc = node(jar.get(p.getProperty("world.owner") + ".class"));
            for (FieldNode f : wc.fields) if ((f.access & ACC_STATIC) != 0 && f.desc.equals("L" + n("lliiil") + ";")) {
                Object self = allocate(loader.loadClass(n("lliiil"))); set(self, "cu", 999); world.getField(f.name).set(null, self);
            }
            for (FieldNode f : wc.fields) if ((f.access & ACC_STATIC) != 0 && f.desc.equals("L" + n("liiilili") + ";"))
                world.getField(f.name).set(null,allocate(loader.loadClass(n("liiilili"))));
            message = loader.loadClass(n("illlllil")); packets = loader.loadClass(p.getProperty("hook.detail7.owner").replace('/','.'));
            Class<?> util=loader.loadClass(n("iliiii"));
            for(Field f:util.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==Random.class)
                f.set(null,new Random(1));
            access = Proxy.newProxyInstance(loader, new Class<?>[]{api}, this);
            Class<?> loop=loader.loadClass("RecoveryWorldLoop");loop.getField("ACCESS").set(null,access);
            worldLoop=loop.getMethod("run");
            call("onMap", new Class<?>[]{api}, access);
        }
        Object call(String name, Class<?>[] types, Object... args) throws Exception {
            try { return support.getMethod(name, types).invoke(null, args); }
            catch (InvocationTargetException e) { throw new RuntimeException(name, e.getCause()); }
        }
        Object make(int id) throws Exception {
            Object m = allocate(mob); set(m, "cu", id); set(m, "cv", (byte)1);
            set(m, "bs", 50); set(m, "bt", 100); set(m, "dB", -1); set(m, "f", 1);
            set(m, "aZ", 30); set(m, "ba", 40); set(m, "F", 30); set(m, "G", 40);
            Field clips=mob.getDeclaredField("z");clips.setAccessible(true);clips.set(m,new byte[5][3][4]);
            monsters.put(id, m); add.invoke(vector, m); return m;
        }
        void learn(int id) throws Exception {
            call("onObject", new Class<?>[]{api,int.class,int.class,int.class,int.class,int.class,int.class}, access,1,5,id,30,40,5);
            detail(id, 50);
        }
        void detail(int id, int hp) throws Exception {
            Object m = monsters.get(id); set(m,"bs",hp); set(m,"bt",100);
            call("onDetail", new Class<?>[]{api,int.class,int.class,int.class,int.class,int.class,int.class}, access,id,5,30,40,hp,100);
        }
        void tick(long delta) throws Exception { now += delta; call("tick",new Class<?>[]{api},access); }
        void worldFrame() throws Exception { worldLoop.invoke(null); }
        boolean hold(Object m) throws Exception { return (Boolean)call("hold", new Class<?>[]{Object.class},m); }
        void packet(String tail, byte[] data) throws Exception {
            Object msg = message.getConstructor(byte.class,byte[].class).newInstance((byte)8,data);
            packets.getMethod(n(tail),message).invoke(null,msg);
        }
        public Object invoke(Object proxy, Method method, Object[] a) throws Throwable {
            String name=method.getName();
            if(name.equals("now"))return now; if(name.equals("serverKey"))return "native-fixture";
            if(name.equals("mapId"))return 1; if(name.equals("zoneId"))return 2;
            if(name.equals("ready")||name.equals("save"))return true;
            if(name.equals("autoEnabled"))return false;
            if(name.equals("hp"))return get(a[0],"bs");
            if(name.equals("id"))return get(a[0],"cu");
            if(name.equals("type"))return get(a[0],"cv");
            if(name.equals("clearAutoTarget")){bridge.getMethod("clearAutoTarget",Object.class).invoke(nativeAccess,a);return null;}
            if(name.equals("applyHp"))return bridge.getMethod("applyHp",Object.class,int.class).invoke(nativeAccess,a);
            if(name.equals("load"))return null;
            if(name.equals("find")){Object m=monsters.get((Integer)a[0]);return m!=null&&!bool(m,"cL")?m:null;}
            if(name.equals("dead")){Object m=a[0];return get(m,"bs")<=0||get(m,"cG")==4||bool(m,"dk");}
            if(name.equals("hidden"))return bool(a[0],"dk");
            if(name.equals("retiring"))return bool(a[0],"cM")||bool(a[0],"cL");
            if(name.equals("corpse")){bridge.getMethod("corpse",Object.class,boolean.class).invoke(nativeAccess,a);return null;}
            if(name.equals("restore")){restores++;return make((Integer)a[1]);}
            if(name.equals("pending")){pending.invoke(nativeAccess,a);return null;}
            if(name.equals("discard")){Object m=a[0];monsters.remove(get(m,"cu"));remove.invoke(vector,m);return null;}
            if(name.equals("request")){requests++;return null;}
            if(name.equals("hud"))return null;
            if(name.equals("toString"))return "native recovery access";
            throw new AssertionError("Unexpected access method "+name);
        }
    }
    static Object allocate(Class<?> c) throws Exception {
        Class<?> u=Class.forName("sun.misc.Unsafe"); Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);
        return u.getMethod("allocateInstance",Class.class).invoke(f.get(null),c);
    }
    static AbstractInsnNode previousReal(AbstractInsnNode n){do{n=n.getPrevious();}while(n!=null&&n.getOpcode()<0);return n;}
    static void extractWorldLoop(Map<String,byte[]> jar,Properties p){
        ClassNode world=node(jar.get(p.getProperty("world.owner")+".class"));MethodNode tick=null;
        for(MethodNode method:world.methods)if(method.name.equals("a")&&method.desc.equals("()V"))tick=method;
        if(tick==null)throw new AssertionError("Missing actual world tick");
        AbstractInsnNode marker=null;
        for(AbstractInsnNode n:tick.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==GETFIELD&&((FieldInsnNode)n).name.equals("cM")){marker=n;break;}
        if(marker==null)throw new AssertionError("Missing actual removal flag branch");
        AbstractInsnNode body=marker;
        while(!(body instanceof FieldInsnNode&&body.getOpcode()==GETSTATIC&&((FieldInsnNode)body).desc.equals("Let;")))body=body.getPrevious();
        LabelNode bodyLabel=(LabelNode)body.getPrevious();
        AbstractInsnNode first=previousReal(previousReal(previousReal(bodyLabel)));
        if(first.getOpcode()!=ICONST_0)throw new AssertionError("Unexpected entity-loop initializer");
        AbstractInsnNode end=marker;
        while(!(end instanceof JumpInsnNode&&end.getOpcode()==IF_ICMPLT&&((JumpInsnNode)end).label==bodyLabel))end=end.getNext();
        Map<LabelNode,LabelNode> labels=new HashMap<LabelNode,LabelNode>();
        for(AbstractInsnNode n:tick.instructions.toArray())if(n instanceof LabelNode)labels.put((LabelNode)n,new LabelNode());
        ClassNode loop=new ClassNode();loop.version=V1_4;loop.access=ACC_PUBLIC;loop.name="RecoveryWorldLoop";loop.superName="java/lang/Object";
        String apiDesc="Lluna/api/MonsterCacheAccess;";
        loop.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC,"ACCESS",apiDesc,null,null));
        MethodNode run=new MethodNode(ACC_PUBLIC|ACC_STATIC,"run","()V",null,null);
        for(AbstractInsnNode n=first;;n=n.getNext()){
            AbstractInsnNode copy=n.clone(labels);
            if(copy instanceof FieldInsnNode&&copy.getOpcode()==GETSTATIC&&((FieldInsnNode)copy).owner.equals("luna/generated/HungMonsterCacheAccess"))
                copy=new FieldInsnNode(GETSTATIC,loop.name,"ACCESS",apiDesc);
            // Native entity rendering/movement needs a logged-in world; keep the actual
            // removal loop and hook, and test the real death/frame methods separately.
            if(copy instanceof MethodInsnNode&&((MethodInsnNode)copy).owner.equals(p.getProperty("entity.owner"))
                    &&((MethodInsnNode)copy).name.equals("a")&&((MethodInsnNode)copy).desc.equals("()V"))copy=new InsnNode(POP);
            run.instructions.add(copy);if(n==end)break;
        }
        run.instructions.add(new InsnNode(RETURN));run.maxLocals=3;loop.methods.add(run);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);loop.accept(writer);jar.put(loop.name+".class",writer.toByteArray());
    }
    static Field field(Object m,String name)throws Exception { return m.getClass().getField(name); }
    static void set(Object m,String name,Object value)throws Exception { field(m,name).set(m,value); }
    static int get(Object m,String name)throws Exception { return field(m,name).getInt(m); }
    static boolean bool(Object m,String name)throws Exception { return field(m,name).getBoolean(m); }
    static byte[] shorts(int... values)throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);
        for(int v:values)d.writeShort(v);return b.toByteArray();
    }
    public static void main(String[] args)throws Exception {
        require(args.length==3||(args.length==4&&args[3].equals("--legacy-repro")),"candidate.jar catalog.properties microemulator.jar [--legacy-repro]");
        Properties p=new Properties();InputStream in=new FileInputStream(args[1]);try{p.load(in);}finally{in.close();}
        Map<String,byte[]> jar=read(new File(args[0]));
        if(args.length==4){
            Harness old=new Harness(jar,p);Object entity=old.make(41);old.learn(41);
            set(entity,"cM",true);set(entity,"bT",4);old.detail(41,37);
            require(get(entity,"bs")==37&&bool(entity,"cM"),"Previous positive detail leaves the native deletion flag set");
            old.worldFrame();old.worldFrame();
            require(bool(entity,"cL")&&!old.hold(entity),"Actual previous world removal deletes a just-confirmed alive monster");
            System.out.println("LEGACY_REPRO=PASS; positive server HP then stale cM consumed by actual world loop removes the known monster");return;
        }
        require("a_".equals(p.getProperty("hook.pendingDeath.method")),"Hook uses native death method a_, not movement o");
        require("()V".equals(p.getProperty("hook.pendingDeath.descriptor")),"Death hook descriptor");
        Harness h=new Harness(jar,p);Object m=h.make(41);h.learn(41);
        // Actual remote death handler: an attacker outside our object list.
        h.packet("lili",shorts(777,41));
        require(get(m,"bs")==0,"Native remote death writes HP zero even when attacker is unknown");
        h.death.invoke(m);h.death.invoke(m);
        require(get(m,"cG")==4&&!bool(m,"dk")&&!bool(m,"cL"),"Known native death keeps a visible corpse without removal");
        for(int i=0;i<6&&h.requests==0;i++)h.tick(1000);
        require(h.hold(m)&&bool(m,"dz"),"Confirmed dead native object is borrowed into pending quarantine");
        int e=get(m,"e");h.death.invoke(m);
        require(get(m,"e")==e&&get(m,"bs")==0,"Actual patched a_ stops pending native death/predicted revival");
        h.detail(41,0);
        require(h.hold(m)&&get(m,"bs")==0&&get(m,"cG")==4&&!bool(m,"dk"),"Authoritative HP zero keeps visible corpse and suppresses predicted revival between cycles");
        set(m,"dB",0);set(m,"aX",0L);
        h.loader.loadClass(n("liilllii")).getField(n("iliiilliilil")).setLong(null,100000L);
        int before=get(m,"e");h.death.invoke(m);
        require(get(m,"bs")==0&&get(m,"e")==before,"Native timed revival cannot invent full HP after server confirms death");
        int previousRequests=h.requests;
        for(int i=0;i<8&&h.requests==previousRequests;i++)h.tick(1000);
        require(h.hold(m)&&bool(m,"dz")&&h.requests>previousRequests,"Dead confirmation permits a later bounded recovery cycle");
        set(m,"cK",69);set(m,"bb",12);set(m,"bc",4);set(m,"bd",5);
        h.detail(41,37);
        require(!h.hold(m)&&!bool(m,"dz")&&!bool(m,"dk")&&get(m,"cG")==0&&get(m,"bs")==37,
                "Positive authoritative detail revives hidden native object without inventing full HP");
        require(get(m,"cK")==0&&get(m,"bb")==0&&get(m,"bc")==0&&get(m,"bd")==0,"Revival resets native death/movement counters");
        h.packet("illi",shorts(41));
        require(bool(m,"cM"),"Actual packet-8 handler marks a monster for removal");
        // World update consumes cM, sets cL after five ticks, then removes by identity.
        set(m,"cL",true);h.monsters.remove(41);h.remove.invoke(h.vector,m);
        for(int i=0;i<6&&!h.monsters.containsKey(41);i++)h.tick(1000);
        Object recovered=h.monsters.get(41);
        require(recovered!=null&&recovered!=m&&h.restores==1&&h.hold(recovered),"Missing confirmed ID recovers through one new native object");
        h.detail(41,23);
        require(get(recovered,"bs")==23&&!bool(recovered,"dz"),"Replacement accepts current authoritative HP");
        // An unrelated special monster still follows its original terminal death branch.
        Object special=h.make(99);set(special,"bs",0);set(special,"cG",4);set(special,"dk",true);
        set(special,"dj",16);set(special,"dy",100);set(special,"cK",69);
        require(!h.hold(special),"Unowned unrelated entity is never held");h.death.invoke(special);
        require(bool(special,"cL"),"Unowned special native death still removes at counter 70");
        require(h.requests>0,"Recovery cycles issue actual access detail requests");
        Harness timeouts=new Harness(jar,p);Object remote=timeouts.make(42);timeouts.learn(42);
        set(remote,"bs",0);set(remote,"cG",4);set(remote,"dk",true);
        for(int cycle=0;cycle<3;cycle++) {
            int target=(cycle+1)*2;
            for(int t=0;t<70&&timeouts.requests<target;t++)timeouts.tick(1000);
            require(timeouts.requests==target,"Lost native details get exactly two sends per cycle "+cycle);
            timeouts.tick(8000);
            require(((String)timeouts.call("status",new Class<?>[0])).contains("W:0"),
                    "Native timeout releases the cycle's pending slot "+cycle);
        }
        set(remote,"dB",0);set(remote,"aX",0L);
        timeouts.loader.loadClass(n("liilllii")).getField(n("iliiilliilil")).setLong(null,1000000L);
        timeouts.death.invoke(remote);
        require(timeouts.hold(remote)&&get(remote,"bs")==0&&!bool(remote,"dk"),
                "Three timeouts still suppress synthetic full-HP native revival during cooldown");
        for(int t=0;t<65&&timeouts.requests<7;t++)timeouts.tick(1000);
        require(timeouts.requests==7&&bool(remote,"dz"),
                "Native borrowed monster retries after the maximum cooldown without packet-4");
        timeouts.detail(42,37);
        require(!timeouts.hold(remote)&&!bool(remote,"dk")&&!bool(remote,"dz")
                &&get(remote,"bs")==37&&timeouts.monsters.get(42)==remote,
                "Native delayed detail revives the same identity after three failed cycles");
        checkRetainedWorld(jar,p);
        System.out.println("HungBySonMonsterRecoveryBinaryTest PASS ("+checks+" checks); native remote death, hidden revival, packet8, pending cleanup, repeated timeout recovery");
    }
    static void checkRetainedWorld(Map<String,byte[]> jar,Properties p)throws Exception {
        Harness h=new Harness(jar,p);Object corpse=h.make(43);h.learn(43);
        Method frames=h.mob.getMethod("b_");
        set(corpse,"bs",0);set(corpse,"cG",4);set(corpse,"dk",true);
        set(corpse,"cy",(byte)3);set(corpse,"a",599);set(corpse,"cM",true);set(corpse,"bT",4);
        for(int i=0;i<12;i++){h.worldFrame();frames.invoke(corpse);h.death.invoke(corpse);}
        require(h.monsters.get(43)==corpse&&!bool(corpse,"cL")&&!bool(corpse,"cM")
                &&!bool(corpse,"dk")&&get(corpse,"bs")==0&&get(corpse,"cG")==4,
                "Actual world removal loop preserves the visible, non-targetable corpse identity");
        require(get(corpse,"a")==0&&get(corpse,"bT")==0&&get(corpse,"bb")==3,
                "Corpse animation reaches its final frame and suppresses the independent 600-frame cleanup counter");
        require(((String)h.call("status",new Class<?>[0])).contains("K:1"),"HUD distinguishes a retained corpse from a missing object");
        int requests=0,revivals=0;long start=h.now;
        for(int tick=0;tick<216000;tick++) {
            h.worldFrame();frames.invoke(corpse);h.death.invoke(corpse);h.tick(200);
            boolean outage=tick>=54000&&tick<60000;
            if(h.requests>requests){
                requests=h.requests;
                if(!outage)h.detail(43,0);
            }
            // Repeated valid positive replies, followed by remote removal, across twelve hours.
            if(tick%300==299&&!outage){
                h.detail(43,37);revivals++;
                if(bool(corpse,"cL")||bool(corpse,"cM")||bool(corpse,"dz")||get(corpse,"bs")!=37)
                    throw new AssertionError("Valid respawn retained a removal/quarantine flag");
                set(corpse,"cM",true);set(corpse,"bT",4);
            }
            if(bool(corpse,"cL")||bool(corpse,"dk")||get(corpse,"a")!=0)
                throw new AssertionError("Native cleanup lost the corpse at simulated tick "+tick);
        }
        require(h.now-start==43200000L&&revivals>650&&h.restores==0,
                "Twelve hours and hundreds of native death/removal/respawn cycles preserve one identity, including a twenty-minute outage");
        h.detail(43,37);h.worldFrame();
        require(get(corpse,"bs")==37&&!bool(corpse,"dz")&&!bool(corpse,"dk")
                &&!bool(corpse,"cM")&&!bool(corpse,"cL")&&get(corpse,"bT")==0,
                "Authoritative positive HP releases the corpse and all removal counters without a map reset");
        require(((String)h.call("status",new Class<?>[0])).contains("K:0"),"Positive respawn clears the retained-corpse HUD count");
        Object live=h.make(44);h.learn(44);set(live,"cM",true);set(live,"bT",4);set(live,"a",599);
        h.detail(44,29);h.worldFrame();
        require(get(live,"bs")==29&&!bool(live,"cM")&&!bool(live,"cL")&&get(live,"a")==0,
                "Positive detail arriving before world deletion cancels stale removal flags too");
        Object unrelated=h.make(99);set(unrelated,"cM",true);
        for(int i=0;i<7;i++)h.worldFrame();
        require(bool(unrelated,"cL")&&!h.hold(unrelated),"Unlearned entity still follows actual native removal loop");
        Object obsolete=corpse,replacement=h.make(43);set(obsolete,"cM",true);
        h.worldFrame();
        require(!h.hold(obsolete)&&get(replacement,"bs")==50&&!bool(replacement,"dz"),
                "A replacement identity is never overwritten by protection of its obsolete corpse");
    }
}
