import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Removes the simulation-wide idle hooks; masks attack pose reads only in rendering. */
public final class HungBySonFarmQuietRecoveryPatcher implements Opcodes {
    static final String INPUT = "0FA01EA3F54230DF6A7529C61C55996BDC689B694450474220893494ACEF6187";
    static final String BASE = "826E8FE3BCE4D08541A206BDA7C91DCC01CF06BFC6B956D744BD1F36FBC26EA4";
    static final String BASE_JAR = "build/hung-farm-special-attack-20261023-a/Hung_By_Son.jar";
    static final String B = "luna/generated/HungAutoAttackAccess";
    static final String[] POSE = {"cG", "cf", "cQ", "dX", "A", "bb"};
    static final String[] GETTERS = {"farmRenderState", "farmRenderPose", "farmRenderTick",
            "farmRenderEffectFrame", "farmRenderMountFrame", "farmRenderIdleFrame"};
    static Properties p = new Properties();
    static String E, P, C, A;
    static void check(boolean value, String why) { if (!value) throw new IllegalStateException(why); }
    static String req(String key) { String value=p.getProperty(key); check(value!=null,"Missing "+key); return value; }
    static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m:c.methods) if(m.name.equals(name)&&m.desc.equals(desc)) return m;
        throw new IllegalStateException("Missing "+c.name+"."+name+desc);
    }
    static void field(InsnList l,int op,String owner,String name,String desc) { l.add(new FieldInsnNode(op,owner,name,desc)); }
    static void call(InsnList l,String name,String desc) { l.add(new MethodInsnNode(INVOKESTATIC,B,name,desc,false)); }
    static void actor(InsnList l) { l.add(new VarInsnNode(ALOAD,0)); }
    static boolean pureIndex(MethodNode m) {
        if(!m.desc.equals("()I")&&!m.desc.equals("(I)I")) return false;
        for(AbstractInsnNode n:m.instructions) {
            int op=n.getOpcode();
            if(op==PUTFIELD||op==PUTSTATIC||op==ATHROW||op==NEW||op==MONITORENTER
                    ||op==MONITOREXIT||n instanceof MethodInsnNode||op==IASTORE||op==AASTORE
                    ||op==BASTORE||op==SASTORE||op==LASTORE||op==FASTORE||op==DASTORE||op==CASTORE) return false;
        }
        return true;
    }
    public static void main(String[] args) throws Exception {
        check(args.length==5,"input support-unused output catalog report");
        File input=new File(args[0]), output=new File(args[2]), baseFile=new File(BASE_JAR);
        check(HungBySonExpInfoPack.hash(input).equals(INPUT),"Pinned broken quiet release");
        check(HungBySonExpInfoPack.hash(baseFile).equals(BASE),"Pinned working special/Farm baseline");
        check(!output.exists()&&!new File(args[3]).exists()&&!new File(args[4]).exists(),"Fresh outputs");
        InputStream cfg=new FileInputStream("build/hung-farm-quiet-20261023-a/farm-special-attack.properties");
        try { p.load(cfg); } finally { cfg.close(); }
        E=req("entity.owner"); P=req("native.player.owner"); C=req("cast.owner"); A=req("request.animation.owner");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input), baseline=HungBySonExpInfoPack.read(baseFile);
        check(before.keySet().equals(baseline.keySet()),"No entry changes since working baseline");
        Set<String> oldHooks=new HashSet<String>(Arrays.asList(P+".class",C+".class",A+".class",B+".class"));
        for(String name:before.keySet()) if(!oldHooks.contains(name))
            check(Arrays.equals(before.get(name),baseline.get(name)),"Only reviewed quiet hooks may differ: "+name);
        Map<String,byte[]> out=new LinkedHashMap<String,byte[]>(baseline);
        ClassNode bridge=HungBySonExpInfoPack.node(baseline.get(B+".class"));
        ClassNode entity=HungBySonExpInfoPack.node(baseline.get(E+".class"));
        FieldInsnNode singleton=null;
        for(AbstractInsnNode n:method(bridge,"player","()Ljava/lang/Object;").instructions)
            if(n instanceof FieldInsnNode&&n.getOpcode()==GETSTATIC) singleton=(FieldInsnNode)n;
        check(singleton!=null,"Local singleton");
        MethodNode quiet=new MethodNode(ACC_PUBLIC|ACC_STATIC,"farmQuietRender","(L"+E+";)Z",null,null);
        bridge.methods.add(quiet); InsnList l=quiet.instructions; LabelNode no=new LabelNode(),yes=new LabelNode();
        actor(l); l.add(new JumpInsnNode(IFNULL,no)); actor(l);
        field(l,GETSTATIC,singleton.owner,singleton.name,singleton.desc); l.add(new JumpInsnNode(IF_ACMPNE,no));
        call(l,"farmSpecialEnabled","()Z"); l.add(new JumpInsnNode(IFEQ,no));
        actor(l); field(l,GETFIELD,E,"bs","I"); l.add(new JumpInsnNode(IFLE,no));
        actor(l); field(l,GETFIELD,E,"cG","I"); l.add(new JumpInsnNode(IFEQ,yes));
        actor(l); field(l,GETFIELD,E,"cG","I"); l.add(new InsnNode(ICONST_2)); l.add(new JumpInsnNode(IF_ICMPNE,no));
        l.add(yes); l.add(new InsnNode(ICONST_1)); l.add(new InsnNode(IRETURN));
        l.add(no); l.add(new InsnNode(ICONST_0)); l.add(new InsnNode(IRETURN));
        for(int i=0;i<POSE.length;i++) {
            MethodNode getter=new MethodNode(ACC_PUBLIC|ACC_STATIC,GETTERS[i],"(L"+E+";)I",null,null);
            bridge.methods.add(getter); l=getter.instructions; LabelNode nativeRead=new LabelNode();
            actor(l); call(l,"farmQuietRender","(L"+E+";)Z"); l.add(new JumpInsnNode(IFEQ,nativeRead));
            // Native d/e renderers treat a negative effect frame as no cast overlay.
            l.add(new InsnNode(POSE[i].equals("dX")?ICONST_M1:ICONST_0));
            l.add(new InsnNode(IRETURN)); l.add(nativeRead);
            actor(l); field(l,GETFIELD,E,POSE[i],"I"); l.add(new InsnNode(IRETURN));
            p.setProperty("quiet.render.field."+POSE[i],GETTERS[i]);
        }
        // Discover the graphics type from Player's actual body-draw call, rather than guess it.
        ClassNode player=HungBySonExpInfoPack.node(baseline.get(P+".class")); String graphicsDesc=null;
        for(MethodNode m:player.methods) for(AbstractInsnNode n:m.instructions)
            if(n instanceof MethodInsnNode) { MethodInsnNode q=(MethodInsnNode)n;
                if(q.owner.equals(E)&&q.name.equals("c")&&q.desc.endsWith(";I)V")) graphicsDesc=q.desc.substring(1,q.desc.indexOf(';')+1);
            }
        check(graphicsDesc!=null,"Native graphics descriptor");
        Set<MethodNode> render=new LinkedHashSet<MethodNode>();
        for(MethodNode m:entity.methods) if(m.desc.contains(graphicsDesc)) render.add(m);
        boolean grew;
        do { grew=false; for(MethodNode m:new ArrayList<MethodNode>(render)) for(AbstractInsnNode n:m.instructions)
            if(n instanceof MethodInsnNode) { MethodInsnNode q=(MethodInsnNode)n;
                if(q.owner.equals(E)) { MethodNode target=method(entity,q.name,q.desc);
                    if(pureIndex(target)&&render.add(target)) grew=true;
                }
            }
        } while(grew);
        List<Object[]> changes=new ArrayList<Object[]>(); Set<MethodNode> modified=new LinkedHashSet<MethodNode>();
        for(MethodNode m:render) for(AbstractInsnNode n:m.instructions.toArray())
            if(n instanceof FieldInsnNode&&n.getOpcode()==GETFIELD) { FieldInsnNode f=(FieldInsnNode)n;
                if(f.owner.equals(E)&&f.desc.equals("I")) for(int i=0;i<POSE.length;i++) if(f.name.equals(POSE[i])) {
                    MethodInsnNode read=new MethodInsnNode(INVOKESTATIC,B,GETTERS[i],"(L"+E+";)I",false);
                    m.instructions.set(f,read); changes.add(new Object[]{m,f,read}); modified.add(m);
                }
            }
        check(changes.size()>20&&modified.size()>2,"Body rendering and sprite index helpers covered");
        for(ClassNode c:new ClassNode[]{bridge,entity}) { ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(writer); out.put(c.name+".class",writer.toByteArray()); }
        // Prove that all existing behavior is exactly baseline after undoing only render reads.
        for(Object[] change:changes) ((MethodNode)change[0]).instructions.set((AbstractInsnNode)change[2],(AbstractInsnNode)change[1]);
        for(ClassNode c:new ClassNode[]{bridge,entity}) {
            ClassNode old=HungBySonExpInfoPack.node(baseline.get(c.name+".class"));
            for(MethodNode m:old.methods) check(HungBySonExpInfoPack.canonical(m).equals(
                    HungBySonExpInfoPack.canonical(method(c,m.name,m.desc))),"Exact native inverse "+c.name+"."+m.name);
            check(c.fields.size()==old.fields.size(),"No fields added or changed");
            check(c.methods.size()==old.methods.size()+(c==bridge?7:0),"Method allowlist");
        }
        Set<String> changed=new LinkedHashSet<String>(); int classes=0,resources=0;
        Set<String> allowed=new HashSet<String>(oldHooks); allowed.add(E+".class");
        for(String name:before.keySet()) {
            if(!Arrays.equals(before.get(name),out.get(name))) { check(allowed.contains(name),"Archive allowlist "+name); changed.add(name); }
            if(name.endsWith(".class")) { classes++; check(HungBySonExpInfoPack.node(before.get(name)).version==HungBySonExpInfoPack.node(out.get(name)).version,"Version preserved"); } else resources++;
        }
        for(String nativeOwner:new String[]{P,C,A}) check(Arrays.equals(out.get(nativeOwner+".class"),baseline.get(nativeOwner+".class")),"Native update/cast/animation bytes restored");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));
        try { for(Map.Entry<String,byte[]> e:out.entrySet()) { JarEntry z=new JarEntry(e.getKey()); z.setTime(0); jar.putNextEntry(z); jar.write(e.getValue()); jar.closeEntry(); } } finally { jar.close(); }
        p.setProperty("input.sha256",INPUT); p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));
        p.setProperty("quiet.farm.local","false"); p.setProperty("quiet.farm.render","true");
        p.setProperty("quiet.render.reads",Integer.toString(changes.size())); p.setProperty("quiet.render.graphics",graphicsDesc);
        p.setProperty("quiet.native.baseline",BASE); p.setProperty("changed.classes",String.join(",",changed));
        p.setProperty("native.inverse","PASS");
        OutputStream config=new FileOutputStream(args[3]); try { p.store(config,"Render-only idle pose; native Farm movement, cast and callbacks restored"); } finally { config.close(); }
        PrintWriter log=new PrintWriter(args[4],"UTF-8");
        log.println("PASS native player/cast/animator exact baseline bytes; all old bridge methods unchanged; exact render-read inverse");
        log.println("RENDER_READS="+changes.size()+" METHODS="+modified.size());
        for(MethodNode m:modified) log.println("RENDER="+m.name+m.desc);
        log.println("CLASSES="+classes+" UNCHANGED_RESOURCES="+resources); log.println("SHA256="+p.getProperty("output.sha256")); log.close();
        System.out.println("FARM_QUIET_RECOVERY_PATCH=PASS SHA256="+p.getProperty("output.sha256"));
    }
}
