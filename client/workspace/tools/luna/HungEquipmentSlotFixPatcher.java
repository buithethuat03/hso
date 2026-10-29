import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.tree.*;

/** Corrects the footwear/necklace mapping, preserving every other release entry byte-for-byte. */
public final class HungEquipmentSlotFixPatcher {
    static final String INPUT="92EF9789FC89BBA7A037F08A6A2A9A4CA9073068A2F820802D04CE84A501BE2D";
    static final String POLICY="luna/features/EquipmentFilterPolicy.class";
    static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    public static void main(String[] a)throws Exception{
        check(a.length==4,"input.jar supportDir output.jar report.properties");
        File input=new File(a[0]),output=new File(a[2]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned equipment-filter release");
        check(!output.exists()&&!new File(a[3]).exists(),"Fresh outputs");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),after=new LinkedHashMap<String,byte[]>(before);
        byte[] policy=Files.readAllBytes(new File(a[1],POLICY).toPath());
        HungBySonExpInfoPack.screen(POLICY,policy);
        HungBySonExpInfoPack.verifyClass(before.get(POLICY),policy,Collections.singleton("groupFor(II)I"),Collections.<String>emptySet());
        ClassNode oldNode=HungBySonExpInfoPack.node(before.get(POLICY)),newNode=HungBySonExpInfoPack.node(policy);
        check(oldNode.version==newNode.version,"Policy class version preserved");
        check(!Arrays.equals(before.get(POLICY),policy),"Mapping changed");after.put(POLICY,policy);
        // Prove all 12 standard native type results from the newly compiled class itself.
        java.net.URLClassLoader loader=new java.net.URLClassLoader(new java.net.URL[]{new File(a[1]).toURI().toURL()},null);
        try{
            java.lang.reflect.Method group=Class.forName("luna.features.EquipmentFilterPolicy",true,loader).getMethod("groupFor",Integer.TYPE,Integer.TYPE);
            int[] groups={4,4,4,4,2,2,4,0,1,1,8,8};
            for(int type=0;type<groups.length;type++)check(((Integer)group.invoke(null,type,-1)).intValue()==groups[type],"Native slot group "+type);
            check(((Integer)group.invoke(null,12,-1)).intValue()==0,"Non-requested slot excluded");
            check(((Integer)group.invoke(null,14,-1)).intValue()==0,"Pet slot excluded");
        }finally{loader.close();}
        for(String name:before.keySet())if(!name.equals(POLICY))check(Arrays.equals(before.get(name),after.get(name)),"Unrelated entry "+name);
        check(before.size()==after.size(),"Entry set unchanged");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));
        try{for(Map.Entry<String,byte[]> e:after.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        Properties p=new Properties();p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));
        p.setProperty("changed.classes",POLICY);p.setProperty("changed.method","groupFor(II)I");p.setProperty("native.classes","byte-identical");p.setProperty("resources","byte-identical");
        p.setProperty("groups.strength","8,9");p.setProperty("groups.dexterity","4,5");p.setProperty("groups.vitality","0,1,2,3,6");p.setProperty("groups.spirit","10,11");
        p.setProperty("evidence","native type-to-wear-slot map; exact 20px enlarged screentab6 frames; native profession weapon selection");
        OutputStream config=new FileOutputStream(a[3]);try{p.store(config,"Boots type6 belongs to vitality; necklace type5 belongs to dexterity");}finally{config.close();}
        System.out.println("EQUIPMENT_SLOT_FIX=PASS SHA256="+p.getProperty("output.sha256"));
    }
}
