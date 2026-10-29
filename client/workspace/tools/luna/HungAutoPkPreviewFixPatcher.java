import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.tree.*;

/** Fixes the checked PK label and previews its true radius even when automatic attacks are off. */
public final class HungAutoPkPreviewFixPatcher {
    static final String INPUT="D2E1A3318F082F4BF7EEAB4BE06BDB6F4BAED85156899734238032C5022B43B0";
    static final String PORT="HungBySonModUpLv",BRIDGE="luna/generated/HungAutoAttackAccess";
    static void check(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static ClassNode node(Map<String,byte[]> map,String name){return HungBySonExpInfoPack.node(map.get(name+".class"));}
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),support=new File(a[1]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned centered-radius input");check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work directory");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-auto-pk-center-20261025-c/auto-pk.properties");
        try{p.load(cfg);}finally{cfg.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned catalog");
        ClassNode bridge=node(before,BRIDGE);int labels=0;
        for(AbstractInsnNode x:HungBySonCopySpecialPatcher.method(bridge,"showMenu","(ZZI)V").instructions)if(x instanceof LdcInsnNode&&"Auto quái + PK [x]".equals(((LdcInsnNode)x).cst)){
            ((LdcInsnNode)x).cst="Auto PK [x]";labels++;
        }
        check(labels==1,"Exactly the remaining checked-mode label");
        out.put(BRIDGE+".class",HungBySonCopySpecialPatcher.write(bridge));
        Map<String,byte[]> compiled=HungBySonCopySpecialPatcher.compile(new File[]{new File("tools/luna/HungBySonModUpLv.java.in"),new File("tools/luna/HungBySonSkillStats.java.in")},work,input,support,a[2],false);
        ClassNode port=node(before,PORT),newPort=HungBySonExpInfoPack.node(compiled.get(PORT+".class"));
        String descriptor="(L"+HungBySonCopySpecialPatcher.n("llilll")+";)V";
        MethodNode oldPaint=HungBySonCopySpecialPatcher.method(port,"paintPkRange",descriptor);
        MethodNode newPaint=HungBySonCopySpecialPatcher.method(newPort,"paintPkRange",descriptor);
        port.methods.set(port.methods.indexOf(oldPaint),newPaint);
        out.put(PORT+".class",HungBySonCopySpecialPatcher.write(port));
        for(String name:new String[]{PORT,BRIDGE}){
            ClassNode old=node(before,name),changed=node(out,name);
            check(old.fields.size()==changed.fields.size()&&old.methods.size()==changed.methods.size()&&old.version==changed.version,"Preserved class ABI");
            for(MethodNode m:old.methods){if(name.equals(PORT)&&m.name.equals("paintPkRange")&&m.desc.equals(descriptor))continue;
                if(name.equals(BRIDGE)&&m.name.equals("showMenu")&&m.desc.equals("(ZZI)V"))continue;
                check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(HungBySonCopySpecialPatcher.method(changed,m.name,m.desc))),"Unchanged method "+name+"."+m.name);
            }
            HungBySonExpInfoPack.screen(name,out.get(name+".class"));
        }
        int classes=0,resources=0,changes=0;
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(before.get(name),out.get(name))){check(name.equals(PORT+".class")||name.equals(BRIDGE+".class"),"Only UI label and painter change");changes++;}
        }
        check(changes==2,"Two intended class edits");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("auto.pk.preview.selected","true");p.setProperty("auto.pk.checked.label.fixed","true");
        p.setProperty("auto.pk.class.count",String.valueOf(classes));p.setProperty("auto.pk.changed.classes",PORT+".class,"+BRIDGE+".class");
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Checked PK label and selected-mode preview");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{log.println("PASS pinned input; only checked label and paintPkRange changed; all combat/world hooks/resources/manifest unchanged; method/ABI/CLDC screen");log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS selected PK label and preview "+p.getProperty("output.sha256"));
    }
}
