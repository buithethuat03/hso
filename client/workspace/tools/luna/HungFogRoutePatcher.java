import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import javax.tools.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * After an Auto bán rác trip the sale restores Farm in its "return to the Farm map" phase, and Farm
 * sets the native route target to the saved map (e.g. Phủ Sương 125) every tick. The native router has
 * no edges for the Phủ Sương hunting maps; it files them under region 3 (maps >= 63), so from Làng Sói
 * Trắng (map 1) its next hop is map 33 and it walks the player off towards the teleport stone or a
 * portal. Tự động vào Làng Phủ Sương only runs every 1.4s, so the player never reached Mrs Oda.
 *
 * The route module's update() now yields while Tự động vào Làng Phủ Sương owns the trip: fog ON, on
 * map 1 or 135, routing towards a map the router has no edges for, and no Auto bán rác trip in
 * progress. The fog walk to Mrs Oda/Serena then leads; inside the saved map Farm resumes as before.
 * Only the route class changes.
 */
public final class HungFogRoutePatcher implements Opcodes {
    static final String INPUT="206D2781D8DC22B20DA78ADEC540B19A15ECBC7DBF685FB80E4FC2D0427D4082";
    static final String ROUTE=HungBySonCopySpecialPatcher.n("lliiiiii"),MAIN=HungBySonCopySpecialPatcher.n("liilllii"),
            MAP=HungBySonCopySpecialPatcher.n("liiililiiii"),ROUTING=HungBySonCopySpecialPatcher.n("liliillilil"),
            TARGET=HungBySonCopySpecialPatcher.n("liliillilli"),EDGES=HungBySonCopySpecialPatcher.n("liilll"),PORT="HungBySonModUpLv";
    static final String HELPER="CodexFogRoute";

    static void check(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static boolean staticField(ClassNode c,String name,String desc){for(FieldNode f:c.fields)if(f.name.equals(name)&&f.desc.equals(desc))return (f.access&ACC_STATIC)!=0;return false;}

    static String source(){
        return "final class "+HELPER+" {\n"+
            "    /** Tự động vào Làng Phủ Sương leads on maps 1/135 towards a map the router cannot reach. */\n"+
            "    static boolean codexFogHold() {\n"+
            "        if (!"+PORT+".fog || !"+ROUTE+"."+ROUTING+") return false;\n"+
            "        cs current = "+MAIN+"."+MAP+";\n"+
            "        if (current == null) return false;\n"+
            "        int map = current.d, target = "+ROUTE+"."+TARGET+";\n"+
            "        if (map != 1 && map != 135 || target == map || "+ROUTE+"."+EDGES+"(target).length != 0) return false;\n"+
            "        return !"+PORT+".codexFogSaleBusy();\n"+
            "    }\n"+
            "}\n";
    }

    static ClassNode helper(File work,File input)throws Exception{
        File src=new File(work,HELPER+".java"),out=new File(work,"fog-route-helper");check(out.isDirectory()||out.mkdirs(),"Helper directory");
        Files.write(src.toPath(),source().getBytes("UTF-8"));
        JavaCompiler javac=ToolProvider.getSystemJavaCompiler();check(javac!=null,"javac (JDK) required");
        check(javac.run(null,null,null,"-encoding","UTF-8","--release","7","-nowarn","-Xlint:-options","-g:none","-cp",input.getPath(),"-d",out.getPath(),src.getPath())==0,"Helper compile");
        return HungBySonExpInfoPack.node(Files.readAllBytes(new File(out,HELPER+".class").toPath()));
    }

    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned fog-oda input");check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work directory");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-fog-oda-20261026-a/auto-pk.properties");
        try{p.load(cfg);}finally{cfg.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");

        ClassNode port=HungBySonExpInfoPack.node(before.get(PORT+".class"));
        check(staticField(port,"fog","Z")&&HungBySonCopySpecialPatcher.method(port,"codexFogSaleBusy","()Z")!=null,"Fog flag and sale gate present");
        ClassNode route=HungBySonExpInfoPack.node(before.get(ROUTE+".class"));
        check(route.interfaces.contains("IModModule"),"Route is a mod module");
        check(staticField(route,ROUTING,"Z")&&staticField(route,TARGET,"I"),"Route state fields");
        check((HungBySonCopySpecialPatcher.method(route,EDGES,"(I)[I").access&ACC_STATIC)!=0,"Route edge table");
        MethodNode update=HungBySonCopySpecialPatcher.method(route,"update","()V");
        Map<String,String> unchanged=new HashMap<String,String>();
        for(MethodNode m:route.methods)if(m!=update)unchanged.put(m.name+m.desc,HungBySonExpInfoPack.canonical(m));
        int fields=route.fields.size();

        // The first routing-flag read follows the pending-direction prologue: yield right before it.
        AbstractInsnNode first=null;
        for(AbstractInsnNode x=update.instructions.getFirst();x!=null;x=x.getNext())
            if(x.getOpcode()==GETSTATIC&&((FieldInsnNode)x).owner.equals(ROUTE)&&((FieldInsnNode)x).name.equals(ROUTING)){first=x;break;}
        check(first!=null,"Route update reads the routing flag");
        AbstractInsnNode label=first.getPrevious();
        check(label instanceof LabelNode,"Routing check starts a block");
        for(TryCatchBlockNode t:update.tryCatchBlocks)check(t.handler!=label,"Gate outside the prologue handler");
        LabelNode free=new LabelNode();InsnList gate=new InsnList();
        gate.add(new MethodInsnNode(INVOKESTATIC,ROUTE,"codexFogHold","()Z",false));gate.add(new JumpInsnNode(IFEQ,free));gate.add(new InsnNode(RETURN));gate.add(free);
        update.instructions.insertBefore(first,gate);

        ClassNode h=helper(work,input);
        int added=0;
        for(MethodNode m:h.methods){
            if(!m.name.startsWith("codexFog"))continue;
            m.access=ACC_PUBLIC|ACC_STATIC;m.localVariables=null;
            for(Iterator<AbstractInsnNode> i=m.instructions.iterator();i.hasNext();){
                AbstractInsnNode x=i.next();
                if(x instanceof FrameNode||x instanceof LineNumberNode){i.remove();continue;}
                if(x instanceof MethodInsnNode)check(!((MethodInsnNode)x).owner.equals(HELPER),"Helper is self-contained");
            }
            check(HungBySonCopySpecialPatcher.find(route,m.name,m.desc)==null,"New method "+m.name);route.methods.add(m);added++;
        }
        check(added==1&&route.fields.size()==fields,"One hold method, no new fields");
        for(MethodNode m:route.methods)if(unchanged.containsKey(m.name+m.desc))check(unchanged.get(m.name+m.desc).equals(HungBySonExpInfoPack.canonical(m)),"Unchanged "+m.name);
        byte[] bytes=HungBySonCopySpecialPatcher.write(route);
        check(HungBySonExpInfoPack.node(bytes).version==route.version,"Class version kept");
        String pool=new String(bytes,"ISO-8859-1"),prior=new String(before.get(ROUTE+".class"),"ISO-8859-1");
        for(String ref:new String[]{"java/lang/invoke/","java/util/","java/nio/","java/time/","java/awt/","java/lang/reflect/","addSuppressed"})
            check(pool.indexOf(ref)<0||prior.indexOf(ref)>=0,"New non-CLDC reference "+ref);
        out.put(ROUTE+".class",bytes);

        int classes=0,resources=0;List<String> changed=new ArrayList<String>();
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(before.get(name),out.get(name)))changed.add(name);
        }
        check(changed.equals(Collections.singletonList(ROUTE+".class")),"One intended class edit: "+changed);
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("fog.route","true");
        p.setProperty("auto.pk.class.count",String.valueOf(classes));p.setProperty("auto.pk.changed.classes",ROUTE+".class");
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Route yields to Lang Phu Suong on maps 1/135");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{log.println("PASS pinned input; route update gated by codexFogHold; other route methods unchanged");log.println("CHANGED="+ROUTE+".class");log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS fog route "+p.getProperty("output.sha256"));
    }
}
