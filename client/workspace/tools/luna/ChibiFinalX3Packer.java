import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Full three-client package of the fixed Chibi Final binary and pinned launcher. */
public final class ChibiFinalX3Packer implements Opcodes {
    static final String SOURCE_SHA = "A78EFD2FFA52B179A0EF77CABFDB6C5294E91C0BB3BD87635F891EC44A1B1DEB";
    static final String TEMPLATE_SHA = "5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC";
    static final String SUITE = "HSO CHIBI FINAL X3", VENDOR = "HSO_CHIBI_FINAL_X3", ROW = "HSO CHIBI FINAL";
    static final String INI = "chibi-final-x3.ini", MANIFEST = "META-INF/MANIFEST.MF";
    static final String AUTH = "luna/generated/ChibiLoginAuth";
    static final String[] SLOTS = {"", "b/", "c/"};
    static final int SOURCE_CLASSES = 217, PACKED_CLASSES = 676;

    public static void main(String[] args) throws Exception {
        require(args.length == 5, "source.jar template.jar new.jar X3SlotSupport.class report.txt");
        File input = new File(args[0]), templateFile = new File(args[1]), output = new File(args[2]);
        require(!output.exists() && !new File(args[4]).exists(), "Use fresh output paths");
        require(SOURCE_SHA.equals(HsoChildX3Packer.hash(input)), "Unexpected Chibi Final preimage");
        require(TEMPLATE_SHA.equals(HsoChildX3Packer.hash(templateFile)), "Unexpected launcher template");
        Map<String,byte[]> source = HsoChildX3Packer.readJar(input), template = HsoChildX3Packer.readJar(templateFile);
        Map<String,byte[]> classes = new LinkedHashMap<String,byte[]>();
        for (String name : source.keySet()) if (name.endsWith(".class")) classes.put(name.substring(0,name.length()-6), source.get(name));
        require(classes.size() == SOURCE_CLASSES && classes.containsKey("TemMidlet") && classes.containsKey(AUTH), "Source class set changed");
        require(!classes.containsKey("Static") && !classes.containsKey("X3SlotSupport"), "Already packed source");
        Manifest manifest = new Manifest(new ByteArrayInputStream(source.get(MANIFEST)));
        Attributes attributes = manifest.getMainAttributes();
        require("HSO CHIBI".equals(attributes.getValue("MIDlet-Name")), "Source identity changed");
        attributes.putValue("MIDlet-Name", SUITE); attributes.putValue("MIDlet-Vendor", VENDOR);
        attributes.putValue("MIDlet-1", SUITE + ",/MrQuyet/mid.png,MrQuyet.MrQuyet");
        attributes.remove(new Attributes.Name("MIDlet-2")); attributes.remove(new Attributes.Name("MIDlet-3"));
        for (int slot=0;slot<3;slot++) attributes.putValue(String.valueOf(slot), ROW + " " + (slot+1) + ",/MrQuyet/mid.png," + SLOTS[slot].replace('/','.') + "TemMidlet,/" + INI);
        attributes.putValue("Implementation-Title", SUITE + " Full Map Pickup");
        attributes.putValue("HSO-X3-Source-SHA-256", SOURCE_SHA); attributes.putValue("HSO-X3-Template-SHA-256", TEMPLATE_SHA);
        attributes.putValue("HSO-X3-RMS-Prefixes", "a,b,c");
        ByteArrayOutputStream mf = new ByteArrayOutputStream(); manifest.write(mf);
        Map<String,byte[]> result = new LinkedHashMap<String,byte[]>(); result.put(MANIFEST,mf.toByteArray());
        for (String name : source.keySet()) if (!name.endsWith(".class") && !MANIFEST.equals(name)) result.put(name,source.get(name));
        int wrappers=0;
        for (String name : template.keySet()) if ((name.startsWith("lib/") || name.startsWith("MrQuyet/")) && name.endsWith(".class")) { result.put(name,template.get(name)); wrappers++; }
        require(wrappers == 19 && template.containsKey("MrQuyet/mid.png"), "Wrapper ABI changed");
        result.put("MrQuyet/mid.png",template.get("MrQuyet/mid.png")); result.put(INI,source.get(MANIFEST));
        byte[] helper = HsoChildX3Packer.read(new FileInputStream(args[3]));
        for (int slot=0;slot<3;slot++) {
            Map<String,String> map = slotMap(classes.keySet(),slot);
            int id=0; int[] counts=new int[4];
            for (Map.Entry<String,byte[]> entry : classes.entrySet()) {
                byte[] lifecycle = prepareLifecycle(entry.getValue(),id++);
                result.put(map.get(entry.getKey())+".class",HsoChildX3Packer.transform(lifecycle,map,slot,false,counts));
            }
            Map<String,String> helperMap = new LinkedHashMap<String,String>(map);
            helperMap.put("java/lang/StringBuilder","java/lang/StringBuffer");
            result.put(SLOTS[slot]+"X3SlotSupport.class",HsoChildX3Packer.transform(helper,helperMap,slot,true,new int[4]));
            result.put(SLOTS[slot]+"Static.class",HsoChildX3Packer.makeStatic(classes,SLOTS[slot]));
            require(Arrays.equals(counts,new int[]{2,1,1,2}),"Unexpected RMS boundaries " + Arrays.toString(counts));
            System.out.println("CHIBI_FINAL_X3_SLOT="+(slot+1)+"; gameClasses="+classes.size()+"; rms(open,list,delete,reflect)="+Arrays.toString(counts));
        }
        HsoChildX3Packer.checkReferences(result,classes.keySet());
        int total=0; for(String name:result.keySet()) if(name.endsWith(".class")) total++;
        require(total==PACKED_CLASSES,"Unexpected packed class count");
        JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
        try { for(Map.Entry<String,byte[]> entry:result.entrySet()) { JarEntry zip=new JarEntry(entry.getKey());zip.setTime(0);out.putNextEntry(zip);out.write(entry.getValue());out.closeEntry(); } } finally { out.close(); }
        Map<String,byte[]> saved=HsoChildX3Packer.readJar(output);
        require(saved.keySet().equals(result.keySet()),"Archive topology changed while writing");
        for(String name:result.keySet()) require(Arrays.equals(result.get(name),saved.get(name)),"Saved content mismatch: "+name);
        for(String name:source.keySet()) if(!name.endsWith(".class")&&!MANIFEST.equals(name)) require(Arrays.equals(source.get(name),saved.get(name)),"Source resource changed: "+name);
        for(String name:template.keySet()) if((name.startsWith("lib/")||name.startsWith("MrQuyet/"))&&name.endsWith(".class")) require(Arrays.equals(template.get(name),saved.get(name)),"Template wrapper changed: "+name);
        require(SOURCE_SHA.equals(HsoChildX3Packer.hash(input))&&TEMPLATE_SHA.equals(HsoChildX3Packer.hash(templateFile)),"Input changed during packing");
        PrintWriter report=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[4]),"UTF-8"));
        try {
            report.println("source.sha256="+SOURCE_SHA);report.println("template.sha256="+TEMPLATE_SHA);report.println("output.sha256="+HsoChildX3Packer.hash(output));
            report.println("suite="+SUITE);report.println("vendor="+VENDOR);report.println("classes="+total);report.println("entries="+saved.size());
            report.println("source.classes.per.slot="+classes.size());report.println("namespaces=default,b/,c/");report.println("rms.prefixes=a,b,c");
            report.println("rms.boundaries.per.slot=open:2,list:1,delete:1,reflective.config:2");
            report.println("resources=source unchanged; wrapper icon and source-manifest virtual INI added");
            report.println("wrappers=19 byte-identical; launcher=MrQuyet.MrQuyet");report.println("cross-slot.game.references=NONE");
            report.println("source.login-and-self-initialization=preserved in all three full copies");
        } finally { report.close(); }
        System.out.println("CHIBI_FINAL_X3_PACK=PASS; classes="+total+"; entries="+saved.size()+"; SHA256="+HsoChildX3Packer.hash(output));
    }

    static Map<String,String> slotMap(Set<String> names,int slot) {
        Map<String,String> map=new LinkedHashMap<String,String>();for(String name:names) map.put(name,SLOTS[slot]+name);
        map.put("Static",SLOTS[slot]+"Static");map.put("X3SlotSupport",SLOTS[slot]+"X3SlotSupport");HsoChildX3Packer.addWrapperTypes(map);return map;
    }
    static byte[] prepareLifecycle(byte[] raw,int id) {
        ClassNode c=new ClassNode();new ClassReader(raw).accept(c,0);
        // The generated root Static must invoke public reset hooks across luna/* packages.
        c.access|=ACC_PUBLIC;ClassWriter writer=new ClassWriter(0);c.accept(writer);
        return HsoChildX3Packer.addLifecycle(writer.toByteArray(),id);
    }
    static void require(boolean value,String message){HsoChildX3Packer.require(value,message);}
}
