import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.tree.*;

/** Moves only the mispositioned overlay calls; combat, target selection and resources are untouched. */
public final class HungAutoPkRangeFixPatcher {
    static final String INPUT="3AE3D8126E4041F96F19DD6336C1BEE5F5824477946E6A706C83A7119A8A1B46";
    static void check(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned Auto PK input");check(!output.exists(),"Fresh output");
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-auto-pk-20261025-g/auto-pk.properties");
        try{p.load(cfg);}finally{cfg.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        HungAutoPkRangeHook.apply(before,out,p);
        String world=HungBySonCopySpecialPatcher.n("liiliii")+".class";int changed=0,classes=0,resources=0;
        check(before.keySet().equals(out.keySet()),"No added or removed entries");
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(before.get(name),out.get(name))){check(name.equals(world),"Only world-render hook changes");changed++;}
        }
        check(changed==1,"Exactly one class changed");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));
        try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));
        p.setProperty("auto.pk.class.count",String.valueOf(classes));p.setProperty("auto.pk.changed.classes",world);
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Auto PK world-camera center fix");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");
        try{log.println("PASS pinned input; replace two minimap overlay calls with one pre-reset world call; structural inverse; all other methods/classes/resources/manifest byte-identical");
            log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("CHANGED="+world);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS PK radius world-camera fix "+p.getProperty("output.sha256"));
    }
}
