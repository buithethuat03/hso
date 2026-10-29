import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Special's pick-up follows the mod's "Auto nhặt đồ" menu. pickupFrame used a fixed quality >= 3 rule, so
 * purple dropped even with only Cam ticked; it now runs the mod's whole-map pass (on/off and colour boxes)
 * and keeps only the map-48 Rương vàng exception. Only HungBySonModUpLv.pickupFrame changes.
 */
public final class HungLootMenuPatcher {
    static final String INPUT="896F3B6919C168A3D92FD5455F5DDEA3F0C32D0FA88B3E1A477A2DC55FCF79D8";
    static final String PORT="HungBySonModUpLv",FARM=HungBySonCopySpecialPatcher.n("lliiiiil"),
            LOOT=HungBySonCopySpecialPatcher.n("lliiilii"),ITEM=HungBySonCopySpecialPatcher.n("liiiliii");
    static void check(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static boolean readsQuality(MethodNode m){
        for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())
            if(x instanceof FieldInsnNode&&((FieldInsnNode)x).owner.equals(ITEM)&&((FieldInsnNode)x).name.equals("ct"))return true;
        return false;
    }
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),support=new File(a[1]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned Xa nu input");check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work directory");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-auto-pk-serpent-20261026-a/auto-pk.properties");
        try{p.load(cfg);}finally{cfg.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");
        StringBuilder report=new StringBuilder();

        // The mod's whole-map pass must be the public no-arg method gated by Auto nhặt and the colour filter.
        ClassNode farm=HungBySonExpInfoPack.node(before.get(FARM+".class"));
        MethodNode loot=HungBySonCopySpecialPatcher.method(farm,LOOT,"()V");
        check((loot.access&Opcodes.ACC_STATIC)!=0&&(loot.access&Opcodes.ACC_PUBLIC)!=0,"Mod loot pass is public static");
        check(HungAutoPkLinesPatcher.calls(loot,HungBySonCopySpecialPatcher.n("liliil"),"(I)Z"),"Mod loot pass applies the colour filter");

        Map<String,byte[]> compiled=HungBySonCopySpecialPatcher.compile(new File[]{new File("tools/luna/HungBySonModUpLv.java.in"),new File("tools/luna/HungBySonSkillStats.java.in")},work,input,support,a[2],false);
        ClassNode port=HungBySonExpInfoPack.node(before.get(PORT+".class")),fresh=HungBySonExpInfoPack.node(compiled.get(PORT+".class"));
        check(port.methods.size()==fresh.methods.size()&&port.fields.size()==fresh.fields.size(),"Port topology");
        int same=0;String pickup="(L"+HungBySonCopySpecialPatcher.n("lliiil")+";)V";
        for(MethodNode m:port.methods){
            // sendPlayers is a template stub; the released body is the generated PK serializer and stays as is.
            if(m.name.equals("pickupFrame")||m.name.equals("sendPlayers"))continue;
            check(HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(HungBySonCopySpecialPatcher.method(fresh,m.name,m.desc))),"Template already matches released port method "+m.name+m.desc);same++;
        }
        MethodNode old=HungBySonCopySpecialPatcher.method(port,"pickupFrame",pickup),now=HungBySonCopySpecialPatcher.method(fresh,"pickupFrame",pickup);
        check(readsQuality(old)&&!readsQuality(now),"Fixed quality rule removed");
        check(HungAutoPkLinesPatcher.calls(now,LOOT,"()V")&&HungAutoPkLinesPatcher.calls(now,"dungeonGoldChest","(L"+ITEM+";)Z"),"Menu pass plus Rương vàng exception");
        port.methods.set(port.methods.indexOf(old),now);
        out.put(PORT+".class",HungBySonCopySpecialPatcher.write(port));
        HungBySonExpInfoPack.screen(PORT,out.get(PORT+".class"));
        report.append("PORT unchanged methods verified against template: ").append(same).append('\n');

        int classes=0,resources=0;List<String> changed=new ArrayList<String>();
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(before.get(name),out.get(name)))changed.add(name);
        }
        check(changed.equals(Collections.singletonList(PORT+".class")),"One intended class edit: "+changed);
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("loot.menu","true");
        p.setProperty("auto.pk.class.count",String.valueOf(classes));p.setProperty("auto.pk.changed.classes",PORT+".class");
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Special pick-up follows the Auto nhat do menu");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{log.print(report);log.println("PASS pinned input; only HungBySonModUpLv.pickupFrame replaced; resources/manifest unchanged");log.println("CHANGED="+PORT+".class");log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS loot menu "+p.getProperty("output.sha256"));
    }
}
