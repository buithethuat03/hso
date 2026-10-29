import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Auto PK Battlefield: inside each priority group (Xà nữ, level 40, others) lower HP goes first, nearer
 * on equal HP. Policy: offer(...) gains the hp key and nearestSquared() scans the kept targets. Port:
 * nearestPlayers passes the hp (0 with Battlefield OFF, so distance only) and priorityRank packs
 * group and inverted HP for the Special OFF controller, whose rank comparison is unchanged.
 */
public final class HungAutoPkHpOrderPatcher {
    static final String INPUT="A215437818175041B179C5CA841BA36F0EC7ADBFA6A4EB7533CB490931E0EF2F";
    static final String CATALOG="build/hung-buff-swap-20261027-a/auto-pk.properties";
    static final String PORT="HungBySonModUpLv",POLICY="luna/features/AutoPkTargetPolicy";
    static final String OLD_OFFER="(Ljava/lang/Object;IJZ)V",NEW_OFFER="(Ljava/lang/Object;IJZI)V";
    static final List<String> REPLACED=Arrays.asList("nearestPlayers","priorityRank");
    static void check(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static ClassNode node(Map<String,byte[]> map,String name){return HungBySonExpInfoPack.node(map.get(name+".class"));}
    static String canon(MethodNode m){return HungBySonExpInfoPack.canonical(m);}
    static MethodNode find(ClassNode c,String name){return HungAutoPkSerpentPatcher.find(c,name);}
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),support=new File(a[1]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned buff-swap input");check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work directory");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Properties p=new Properties();InputStream cfg=new FileInputStream(CATALOG);
        try{p.load(cfg);}finally{cfg.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");
        StringBuilder report=new StringBuilder();

        // Policy: whole class from the support compile; only offer and nearestSquared may differ.
        ClassNode policyOld=node(before,POLICY),policy=new ClassNode();
        new ClassReader(Files.readAllBytes(new File(support,POLICY+".class").toPath())).accept(policy,ClassReader.SKIP_FRAMES);
        policy.version=policyOld.version;
        boolean radius=false;for(FieldNode f:policy.fields)if(f.name.equals("RADIUS"))radius=Integer.valueOf(365).equals(f.value);
        check(radius,"Compiled radius stays 365px");
        check(HungBySonCopySpecialPatcher.method(policyOld,"offer",OLD_OFFER)!=null&&HungBySonCopySpecialPatcher.method(policy,"offer",NEW_OFFER)!=null,"offer gains the hp key");
        for(MethodNode m:policy.methods)check(!(m.name.equals("offer")&&m.desc.equals(OLD_OFFER)),"Old offer removed");
        check(policy.methods.size()==policyOld.methods.size()&&policy.fields.size()==policyOld.fields.size()+1,"Policy adds only the hp array");
        for(MethodNode m:policyOld.methods){
            if(m.name.equals("offer")||m.name.equals("nearestSquared")||m.name.equals("<init>"))continue;
            check(canon(m).equals(canon(HungBySonCopySpecialPatcher.method(policy,m.name,m.desc))),"Unchanged policy method "+m.name);
        }
        out.put(POLICY+".class",HungBySonCopySpecialPatcher.write(policy));

        // Port: transplant the two ranking methods; every other method must already match the template compile.
        Map<String,byte[]> compiled=HungBySonCopySpecialPatcher.compile(new File[]{new File("tools/luna/HungBySonModUpLv.java.in"),new File("tools/luna/HungBySonSkillStats.java.in")},work,input,support,a[2],false);
        ClassNode port=node(before,PORT),fresh=HungBySonExpInfoPack.node(compiled.get(PORT+".class"));
        check(port.methods.size()==fresh.methods.size()&&port.fields.size()==fresh.fields.size(),"Port topology matches the template");
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
        MethodNode select=find(port,"nearestPlayers");
        check(HungAutoPkLinesPatcher.calls(select,"offer",NEW_OFFER)&&!HungAutoPkLinesPatcher.calls(select,"offer",OLD_OFFER),"Selector offers the hp key");
        out.put(PORT+".class",HungBySonCopySpecialPatcher.write(port));
        report.append("PORT unchanged methods verified against template: ").append(same).append('\n');

        // No class may still call the removed offer.
        for(Map.Entry<String,byte[]> e:out.entrySet())if(e.getKey().endsWith(".class")){
            ClassNode c=HungBySonExpInfoPack.node(e.getValue());
            for(MethodNode m:c.methods)for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())if(x instanceof MethodInsnNode){
                MethodInsnNode call=(MethodInsnNode)x;
                check(!(call.owner.equals(POLICY)&&call.name.equals("offer")&&call.desc.equals(OLD_OFFER)),"Stale offer call in "+c.name+"."+m.name);
            }
        }
        for(String name:new String[]{PORT,POLICY})HungBySonExpInfoPack.screen(name,out.get(name+".class"));
        int classes=0,resources=0,changes=0;List<String> changed=new ArrayList<String>();
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(before.get(name),out.get(name))){changed.add(name);changes++;}
        }
        check(changes==2&&changed.containsAll(Arrays.asList(PORT+".class",POLICY+".class")),"Two intended class edits: "+changed);
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));
        p.setProperty("auto.pk.hp.order","true");
        p.setProperty("auto.pk.class.count",String.valueOf(classes));
        StringBuilder list=new StringBuilder();for(String c:changed){if(list.length()>0)list.append(',');list.append(c);}
        p.setProperty("auto.pk.changed.classes",list.toString());
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Auto PK Battlefield: lower HP first inside each priority group");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{log.print(report);log.println("PASS pinned input; policy offer/nearestSquared and port nearestPlayers/priorityRank only; resources/manifest unchanged");log.println("CHANGED="+list);log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS Auto PK hp order "+p.getProperty("output.sha256"));
    }
}
