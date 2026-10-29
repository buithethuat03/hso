import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Auto PK 365px: recompiles the radius policy and the painter (inlined radius + target lines) together. */
public final class HungAutoPkLinesPatcher {
    static final String INPUT="0227180CBD4839C468F2AAA466E4F3555A67663181DC21F32419D5817CD98E01";
    static final String PORT="HungBySonModUpLv",POLICY="luna/features/AutoPkTargetPolicy";
    static final int RADIUS=365;
    static void check(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static ClassNode node(Map<String,byte[]> map,String name){return HungBySonExpInfoPack.node(map.get(name+".class"));}
    static boolean calls(MethodNode m,String name,String desc){
        for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())
            if(i instanceof MethodInsnNode&&((MethodInsnNode)i).name.equals(name)&&((MethodInsnNode)i).desc.equals(desc))return true;
        return false;
    }
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),support=new File(a[1]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned map-objects input");check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work directory");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-map-objects-20261026-b/auto-pk.properties");
        try{p.load(cfg);}finally{cfg.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");
        ClassNode policy=new ClassNode();new ClassReader(Files.readAllBytes(new File(support,POLICY+".class").toPath())).accept(policy,ClassReader.SKIP_FRAMES);
        policy.version=node(before,POLICY).version;
        boolean radius=false;for(FieldNode f:policy.fields)if(f.name.equals("RADIUS"))radius=Integer.valueOf(RADIUS).equals(f.value);
        check(radius,"Compiled radius is "+RADIUS+"px");
        out.put(POLICY+".class",HungBySonCopySpecialPatcher.write(policy));
        Map<String,byte[]> compiled=HungBySonCopySpecialPatcher.compile(new File[]{new File("tools/luna/HungBySonModUpLv.java.in"),new File("tools/luna/HungBySonSkillStats.java.in")},work,input,support,a[2],false);
        ClassNode port=node(before,PORT),fresh=HungBySonExpInfoPack.node(compiled.get(PORT+".class"));
        String desc="(L"+HungBySonCopySpecialPatcher.n("llilll")+";)V";
        MethodNode old=HungBySonCopySpecialPatcher.method(port,"paintPkRange",desc),paint=HungBySonCopySpecialPatcher.method(fresh,"paintPkRange",desc);
        check(!calls(old,"drawLine","(IIII)V")&&calls(paint,"drawLine","(IIII)V")&&calls(paint,"drawArc","(IIIIII)V"),"Painter gains target lines and keeps the ring");
        check(calls(paint,"validTarget","(Ljava/lang/Object;Z)Z")&&calls(paint,"distanceSquared","(IIII)J"),"Lines use the PK selector's filters");
        port.methods.set(port.methods.indexOf(old),paint);out.put(PORT+".class",HungBySonCopySpecialPatcher.write(port));
        for(String name:new String[]{PORT,POLICY}){
            ClassNode original=node(before,name),changed=node(out,name);
            check(original.fields.size()==changed.fields.size()&&original.methods.size()==changed.methods.size()&&original.version==changed.version,"Preserved ABI/version "+name);
            for(MethodNode m:original.methods){
                if(name.equals(PORT)&&m.name.equals("paintPkRange")&&m.desc.equals(desc))continue;
                if(name.equals(POLICY)&&(m.name.equals("distanceSquared")||m.name.equals("offer")))continue;
                check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(HungBySonCopySpecialPatcher.method(changed,m.name,m.desc))),"Unchanged method "+name+"."+m.name);
            }
            HungBySonExpInfoPack.screen(name,out.get(name+".class"));
        }
        int classes=0,resources=0,changes=0;
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(before.get(name),out.get(name))){check(name.equals(PORT+".class")||name.equals(POLICY+".class"),"Only policy and painter change");changes++;}
        }
        check(changes==2,"Two intended class edits");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("auto.pk.radius",String.valueOf(RADIUS));
        p.setProperty("auto.pk.lines","true");
        p.setProperty("auto.pk.class.count",String.valueOf(classes));p.setProperty("auto.pk.changed.classes",PORT+".class,"+POLICY+".class");
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Auto PK radius 365px with target lines");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{log.println("PASS pinned input; "+RADIUS+"px policy and painter (inlined radius + feet-to-feet target lines) recompiled together; all other methods/classes/resources/manifest unchanged");log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS Auto PK lines365 "+p.getProperty("output.sha256"));
    }
}
