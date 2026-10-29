import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.tree.*;

/** Object cache exclusions and Battlefield focused-player line, on the verified target-lock release. */
public final class HungCacheFocusPatcher {
    static final String INPUT="B597B3B7588D5E5692B688A456E2B11B3B7A0A0A4B82EAF0DBAEEFAE0E2B4DBC";
    static final String PORT="HungBySonModUpLv", CACHE="luna/features/MonsterCacheSupport";
    static void check(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static ClassNode node(Map<String,byte[]> map,String name){return HungBySonExpInfoPack.node(map.get(name+".class"));}
    static String canon(MethodNode m){return HungBySonExpInfoPack.canonical(m);}
    static void replace(ClassNode old,ClassNode fresh,String changed){
        check(old.methods.size()==fresh.methods.size()&&old.fields.size()==fresh.fields.size(),"Preserved topology "+old.name);
        for(FieldNode x:old.fields){
            FieldNode y=null;for(FieldNode f:fresh.fields)if(f.name.equals(x.name)&&f.desc.equals(x.desc))y=f;
            check(y!=null&&x.access==y.access&&Objects.equals(x.value,y.value),"Preserved field "+old.name+"."+x.name);
        }
        MethodNode replacement=null;
        for(MethodNode m:old.methods){
            MethodNode next=HungBySonCopySpecialPatcher.method(fresh,m.name,m.desc);
            if(m.name.equals(changed)){check(!canon(m).equals(canon(next)),"Intended method differs "+changed);replacement=next;}
            else if(!(old.name.equals(PORT)&&m.name.equals("sendPlayers")))
                check(canon(m).equals(canon(next)),"Unchanged source method "+old.name+"."+m.name);
        }
        check(replacement!=null,"Replacement exists "+changed);
        for(int i=0;i<old.methods.size();i++)if(old.methods.get(i).name.equals(changed)){old.methods.set(i,replacement);break;}
    }
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),support=new File(a[1]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned current target-lock input");
        check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work directory");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-auto-pk-target-lock-20261027-b/auto-pk.properties");
        try{p.load(cfg);}finally{cfg.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");
        Map<String,byte[]> compiled=HungBySonCopySpecialPatcher.compile(new File[]{new File("tools/luna/HungBySonModUpLv.java.in"),new File("tools/luna/HungBySonSkillStats.java.in")},work,input,support,a[2],false);
        ClassNode port=node(before,PORT),fresh=HungBySonExpInfoPack.node(compiled.get(PORT+".class"));
        replace(port,fresh,"paintPkRange");out.put(PORT+".class",HungBySonCopySpecialPatcher.write(port));
        ClassNode cache=node(before,CACHE),cacheNew=HungBySonExpInfoPack.node(Files.readAllBytes(new File(support,CACHE+".class").toPath()));
        replace(cache,cacheNew,"isExcludedTemplate");out.put(CACHE+".class",HungBySonCopySpecialPatcher.write(cache));
        List<String> changed=new ArrayList<String>();int classes=0,resources=0;
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(before.get(name),out.get(name)))changed.add(name);
        }
        check(changed.size()==2&&changed.contains(PORT+".class")&&changed.contains(CACHE+".class"),"Exactly painter and cache edits: "+changed);
        for(String name:changed)HungBySonExpInfoPack.screen(name,out.get(name));
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));
        try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));
        p.setProperty("auto.pk.focus.lines","true");p.setProperty("cache.exclude.objects","true");
        p.setProperty("auto.pk.class.count",String.valueOf(classes));p.setProperty("auto.pk.changed.classes",PORT+".class,"+CACHE+".class");
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Cache objects and orange focused-player line");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");
        try{log.println("PASS pinned input; only paintPkRange and isExcludedTemplate replaced; every other method, field, resource and manifest unchanged");log.println("CHANGED="+p.getProperty("auto.pk.changed.classes"));log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS cache and focus "+p.getProperty("output.sha256"));
    }
}
