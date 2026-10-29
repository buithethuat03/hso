import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Auto PK Battlefield target lock on top of the HP order: the chosen lower-HP target is kept until it dies,
 * leaves the radius or a higher group is in range. Port only: nearestPlayers offers the lock with HP -1 and
 * stores the new first target in pkLock; priorityRank ranks the controller's current target as HP 0.
 */
public final class HungAutoPkTargetLockPatcher {
    static final String INPUT="E52366BDB974761D106955F3E051346EA513A5D73ABC410D3649ACB5297CDE97";
    static final String CATALOG="build/hung-auto-pk-hp-order-20261027-c/auto-pk.properties";
    static final String PORT="HungBySonModUpLv";
    static final List<String> REPLACED=Arrays.asList("nearestPlayers","priorityRank");
    static void check(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static ClassNode node(Map<String,byte[]> map,String name){return HungBySonExpInfoPack.node(map.get(name+".class"));}
    static String canon(MethodNode m){return HungBySonExpInfoPack.canonical(m);}
    static MethodNode find(ClassNode c,String name){return HungAutoPkSerpentPatcher.find(c,name);}
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),support=new File(a[1]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned hp-order input");check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work directory");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Properties p=new Properties();InputStream cfg=new FileInputStream(CATALOG);
        try{p.load(cfg);}finally{cfg.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");
        StringBuilder report=new StringBuilder();

        // Port: transplant the two ranking methods and the lock field; every other method must already match the template.
        Map<String,byte[]> compiled=HungBySonCopySpecialPatcher.compile(new File[]{new File("tools/luna/HungBySonModUpLv.java.in"),new File("tools/luna/HungBySonSkillStats.java.in")},work,input,support,a[2],false);
        ClassNode port=node(before,PORT),fresh=HungBySonExpInfoPack.node(compiled.get(PORT+".class"));
        check(port.methods.size()==fresh.methods.size()&&port.fields.size()+1==fresh.fields.size(),"Template adds only the lock field");
        int same=0;
        for(MethodNode m:port.methods){
            // sendPlayers is generated from the native serializer; the template only holds a stub.
            if(REPLACED.contains(m.name)||m.name.equals("sendPlayers"))continue;
            check(canon(m).equals(canon(HungBySonCopySpecialPatcher.method(fresh,m.name,m.desc))),"Template already matches released port method "+m.name+m.desc);same++;
        }
        for(String name:REPLACED){
            MethodNode old=find(port,name),now=find(fresh,name);
            check(old!=null&&now!=null&&old.desc.equals(now.desc)&&!canon(old).equals(canon(now)),"Replace "+name);
            port.methods.set(port.methods.indexOf(old),now);
        }
        FieldNode lock=null;for(FieldNode f:fresh.fields)if(f.name.equals("pkLock"))lock=f;
        check(lock!=null&&lock.desc.equals("L"+HungBySonCopySpecialPatcher.n("liiiliii")+";")&&(lock.access&Opcodes.ACC_STATIC)!=0,"pkLock is a static entity field");
        for(FieldNode f:port.fields)check(!f.name.equals("pkLock"),"pkLock is new");port.fields.add(lock);
        String entity="(L"+HungBySonCopySpecialPatcher.n("liiiliii")+";)";
        MethodNode select=find(port,"nearestPlayers"),rank=find(port,"priorityRank");
        boolean stores=false;for(AbstractInsnNode x=select.instructions.getFirst();x!=null;x=x.getNext())
            if(x.getOpcode()==Opcodes.PUTSTATIC&&((FieldInsnNode)x).name.equals("pkLock"))stores=true;
        check(stores&&HungAutoPkLinesPatcher.calls(select,"offer","(Ljava/lang/Object;IJZI)V"),"Selector stores the lock");
        check(HungAutoPkLinesPatcher.calls(rank,"target","()Ljava/lang/Object;"),"Rank reads the controller target");
        out.put(PORT+".class",HungBySonCopySpecialPatcher.write(port));
        report.append("PORT unchanged methods verified against template: ").append(same).append('\n');

        HungBySonExpInfoPack.screen(PORT,out.get(PORT+".class"));
        int classes=0,resources=0,changes=0;List<String> changed=new ArrayList<String>();
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(before.get(name),out.get(name))){changed.add(name);changes++;}
        }
        check(changes==1&&changed.contains(PORT+".class"),"One intended class edit: "+changed);
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));
        p.setProperty("auto.pk.target.lock","true");
        p.setProperty("auto.pk.class.count",String.valueOf(classes));
        p.setProperty("auto.pk.changed.classes",PORT+".class");
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Auto PK Battlefield target lock inside the HP order");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{log.print(report);log.println("PASS pinned input; port nearestPlayers/priorityRank and pkLock only; resources/manifest unchanged");log.println("CHANGED="+PORT+".class");log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS Auto PK target lock "+p.getProperty("output.sha256"));
    }
}
