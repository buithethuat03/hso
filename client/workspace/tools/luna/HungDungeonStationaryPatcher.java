import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Narrow patch of the pinned x1 release: B dungeon positions, live notices, chest exception, hide A. */
public final class HungDungeonStationaryPatcher implements Opcodes {
    static final String INPUT = "EA213F3915D6000262A1C8CB8D7EF05DCCCCCAEE4A87DA7696735F5B1CDFFF6B";
    static final String PORT = "HungBySonModUpLv", POLICY = "luna/features/HungDungeonPositionController";
    static String n(String s) { return HungBySonCopySpecialPatcher.n(s); }
    static void check(boolean b,String why) { if(!b)throw new IllegalStateException(why); }
    static MethodNode method(ClassNode c,String name,String desc) { return HungBySonCopySpecialPatcher.method(c,name,desc); }
    static byte[] write(ClassNode c) { return HungBySonCopySpecialPatcher.write(c); }
    static AbstractInsnNode previous(AbstractInsnNode x) { return HungBySonCopySpecialPatcher.back(x.getPrevious()); }
    static void unchangedExcept(ClassNode before,ClassNode after,Set<String> allowed) {
        for(MethodNode m:before.methods) if(!allowed.contains(m.name+m.desc))
            check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(method(after,m.name,m.desc))),"Unchanged method "+before.name+"."+m.name+m.desc);
    }
    public static void main(String[] a)throws Exception {
        check(a.length==6,"input.jar supportDir micro.jar workDir output.jar catalogPrefix");
        File input=new File(a[0]),support=new File(a[1]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned input hash");
        check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work dir");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Map<String,byte[]> compiled=HungBySonCopySpecialPatcher.compile(new File[]{new File("tools/luna/HungBySonModUpLv.java.in"),new File("tools/luna/HungBySonSkillStats.java.in")},work,input,support,a[2],false);
        ClassNode oldPort=HungBySonExpInfoPack.node(before.get(PORT+".class")),port=HungBySonExpInfoPack.node(compiled.get(PORT+".class"));
        Set<String> changed=new HashSet<String>(Arrays.asList("<clinit>()V","frame()V","pickupFrame(L"+n("lliiil")+";)V","onAction(II)Z","update()V","dungeonFight(L"+n("lliiil")+";)V"));
        unchangedExcept(oldPort,port,changed);
        // Every entry/dialog/maze/fire/attack serializer path outside the requested changes is exact.
        out.put(PORT+".class",compiled.get(PORT+".class"));
        for(String semantic:new String[]{POLICY,"luna/core/MovementArbiter"}) {
            byte[] data=Files.readAllBytes(new File(support,semantic+".class").toPath());
            ClassNode node=new ClassNode();new ClassReader(data).accept(node,ClassReader.SKIP_FRAMES);node.version=V1_4;
            data=write(node);HungBySonExpInfoPack.screen(semantic+".class",data);out.put(semantic+".class",data);
        }

        String mods=n("lillllll");ClassNode manager=HungBySonExpInfoPack.node(before.get(mods+".class"));
        MethodNode notice=method(manager,n("lliii"),"(Ljava/lang/String;)V");
        check((notice.access&ACC_STATIC)!=0,"Static live notice ingress");
        InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(new MethodInsnNode(INVOKESTATIC,PORT,"onDungeonNotice","(Ljava/lang/String;)V",false));notice.instructions.insert(hook);
        out.put(mods+".class",write(manager));

        String menuName=n("lliillli");ClassNode menu=HungBySonExpInfoPack.node(before.get(menuName+".class"));
        MethodNode add=method(menu,"addMenu","(Let;)V");int removed=0;
        for(AbstractInsnNode x:add.instructions.toArray()) if(x instanceof IntInsnNode && ((IntInsnNode)x).operand==-78) {
            AbstractInsnNode start=x;for(int j=0;j<4;j++)start=previous(start);
            check(start instanceof VarInsnNode && start.getOpcode()==ALOAD && ((VarInsnNode)start).var==1,"A row list receiver");
            AbstractInsnNode end=x;while(end!=null && !(end instanceof MethodInsnNode && ((MethodInsnNode)end).owner.equals("et")&&end.getOpcode()==INVOKEVIRTUAL))end=end.getNext();
            check(end!=null,"A row list add");AbstractInsnNode stop=end.getNext();
            while(start!=stop){AbstractInsnNode next=start.getNext();add.instructions.remove(start);start=next;}removed++;
        }
        check(removed==1,"Exactly one native A menu row removed");out.put(menuName+".class",write(menu));
        // A is retired: no hidden action or leftover state can compete with B.
        String legacyName=n("lliilili");ClassNode legacy=HungBySonExpInfoPack.node(before.get(legacyName+".class"));
        MethodNode tick=method(legacy,"update","()V"),action=method(legacy,"onAction","(II)Z");
        HungBySonFullOfflinePatcher.clear(tick);tick.instructions.add(new InsnNode(RETURN));
        HungBySonFullOfflinePatcher.clear(action);action.instructions.add(new InsnNode(ICONST_0));action.instructions.add(new InsnNode(IRETURN));
        out.put(legacyName+".class",write(legacy));
        Set<String> expected=new HashSet<String>(Arrays.asList(PORT+".class",POLICY+".class","luna/core/MovementArbiter.class",mods+".class",menuName+".class",legacyName+".class"));
        List<String> actual=new ArrayList<String>();
        for(Map.Entry<String,byte[]> e:out.entrySet()) if(!Arrays.equals(e.getValue(),before.get(e.getKey()))) {check(expected.remove(e.getKey()),"Unexpected entry change "+e.getKey());actual.add(e.getKey());}
        check(expected.isEmpty(),"All intended entries changed");
        for(Map.Entry<String,byte[]> e:out.entrySet()) if(!e.getKey().endsWith(".class"))check(Arrays.equals(e.getValue(),before.get(e.getKey())),"Resources/manifest unchanged");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-lock-buff-x1-20261024-a/copy-special.properties");try{p.load(cfg);}finally{cfg.close();}
        check(INPUT.equals(p.getProperty("output.sha256")),"Pinned release catalog");
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));
        p.setProperty("dungeon.stationary","true");p.setProperty("dungeon.policy.owner",POLICY);
        p.setProperty("dungeon.notice.owner",mods);p.setProperty("dungeon.notice.method",n("lliii"));p.setProperty("dungeon.notice.descriptor","(Ljava/lang/String;)V");
        p.setProperty("dungeon.legacy.menu.owner",menuName);p.setProperty("dungeon.legacy.owner",legacyName);
        OutputStream catalog=new FileOutputStream(a[5]+".properties");try{p.store(catalog,"Hung stationary dungeon x1; exact pinned symbols");}finally{catalog.close();}
        PrintWriter log=new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[5]+"-report.txt"),"UTF-8"));
        try{log.println("PASS: input "+INPUT);log.println("PASS: unchanged entry/dialog/maze/fire/attack methods; exactly 4 changed classes + 2 semantic classes, no resources changed");for(String entry:actual)log.println(entry);log.println("output="+HungBySonExpInfoPack.hash(output));}finally{log.close();}
        System.out.println("PASS stationary dungeon patch "+HungBySonExpInfoPack.hash(output));
    }
}
