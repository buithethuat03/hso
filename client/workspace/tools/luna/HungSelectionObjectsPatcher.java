import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.tree.*;

/** Hide non-monster object names from live, remembered and saved selection rows. */
public final class HungSelectionObjectsPatcher {
    static final String INPUT="5E16E5EAFC1BE81DE19318894F1F4F94787B405635431962964AF09844766E1F";
    static final String OWNER="luna/features/MonsterSelectionSupport";
    static void check(boolean b,String text){if(!b)throw new IllegalStateException(text);}
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned cache/focus input");check(!output.exists(),"Fresh output");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        ClassNode old=HungBySonExpInfoPack.node(before.get(OWNER+".class"));
        ClassNode fresh=HungBySonExpInfoPack.node(Files.readAllBytes(new File(a[1],OWNER+".class").toPath()));
        check(old.methods.size()+1==fresh.methods.size()&&old.fields.size()==fresh.fields.size(),"Only one new helper");
        for(FieldNode x:old.fields){FieldNode match=null;for(FieldNode y:fresh.fields)if(x.name.equals(y.name)&&x.desc.equals(y.desc))match=y;
            check(match!=null&&match.access==x.access&&Objects.equals(match.value,x.value),"Unchanged field "+x.name);}
        int replaced=0,same=0;
        for(int i=0;i<old.methods.size();i++){
            MethodNode m=old.methods.get(i),next=HungBySonCopySpecialPatcher.method(fresh,m.name,m.desc);
            boolean equal=HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(next));
            if(m.name.equals("addRow")||m.name.equals("addGroup")){check(!equal,"Changed "+m.name);old.methods.set(i,next);replaced++;}
            else{check(equal,"Unchanged method "+m.name);same++;}
        }
        check(replaced==2,"Two selection entry boundaries replaced");
        MethodNode helper=HungBySonCopySpecialPatcher.method(fresh,"excludedObjectName","(Ljava/lang/String;)Z");old.methods.add(helper);
        out.put(OWNER+".class",HungBySonCopySpecialPatcher.write(old));
        // The unchanged legacy status() already references StringBuilder. Screen only new code;
        // all old methods and fields above are verified bytecode-identical.
        ClassNode delta=new ClassNode();delta.name=OWNER;delta.superName="java/lang/Object";delta.version=old.version;
        for(MethodNode m:old.methods)if(m.name.equals("addRow")||m.name.equals("addGroup")||m.name.equals("excludedObjectName"))delta.methods.add(m);
        HungBySonExpInfoPack.screen(OWNER+" changed methods",HungBySonCopySpecialPatcher.write(delta));
        int classes=0,changes=0;
        for(String name:out.keySet()){if(name.endsWith(".class"))classes++;if(!Arrays.equals(before.get(name),out.get(name))){check(name.equals(OWNER+".class"),"Only selection support changes");changes++;}}
        check(changes==1,"Exactly one class changes");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));
        try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-cache-focus-20261028-final/auto-pk.properties");try{p.load(cfg);}finally{cfg.close();}
        check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("selection.exclude.objects","true");
        p.setProperty("auto.pk.class.count",String.valueOf(classes));p.setProperty("auto.pk.changed.classes",OWNER+".class");
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Exclude objects from monster selection");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{log.println("PASS only addRow/addGroup and one helper; unchanged fields, "+same+" other methods, other classes, resources and manifest");log.println("CHANGED="+OWNER+".class");log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS selection objects "+p.getProperty("output.sha256"));
    }
}
