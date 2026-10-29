import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Icon-only update: use the user SVG, with the same pixel dimensions as native A. */
public final class HungBySonExpSvgPatcher implements Opcodes {
    static final String INPUT="3E09C037BBE7144A475228477980C36D92336F7D8BC05A34ED848120A6942299";
    static final String UI="luna/generated/ExpInfoBridge.class",ART="luna/ui/exp-info.png";
    static void check(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    static MethodNode method(ClassNode c,String name,String desc){for(MethodNode m:c.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;throw new IllegalStateException("Missing "+name+desc);}
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input.jar raster.png final.jar inputCatalog outputCatalog report");
        File input=new File(a[0]),art=new File(a[1]),output=new File(a[2]),catalogIn=new File(a[3]),catalogOut=new File(a[4]),report=new File(a[5]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Unexpected current x1 hash");check(!output.exists()&&!catalogOut.exists()&&!report.exists(),"Fresh outputs required");
        Properties p=new Properties();InputStream pin=new FileInputStream(catalogIn);try{p.load(pin);}finally{pin.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Input catalog hash mismatch");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),entries=new LinkedHashMap<String,byte[]>(before);
        BufferedImage badge=ImageIO.read(new ByteArrayInputStream(before.get("x1/interface/iconauto.png"))),icon=ImageIO.read(art);
        check(badge.getWidth()==12&&badge.getHeight()==12&&icon.getWidth()==badge.getWidth()&&icon.getHeight()==badge.getHeight(),"Both icons must be exactly 12x12");
        ClassNode ui=HungBySonExpInfoPack.node(before.get(UI));Map<MethodNode,String> originals=new IdentityHashMap<MethodNode,String>();for(MethodNode m:ui.methods)originals.put(m,HungBySonExpInfoPack.canonical(m));
        MethodNode draw=null,hit=method(ui,"hit","(II)Z");for(MethodNode m:ui.methods)if(m.name.equals("draw")){check(draw==null,"One draw method");draw=m;}check(draw!=null,"Icon draw missing");
        Map<Integer,Integer> coordinates=new HashMap<Integer,Integer>();coordinates.put(84,85);coordinates.put(44,45);coordinates.put(96,97);coordinates.put(45,46);
        int coordinateEdits=0;List<LdcInsnNode> changedNodes=new ArrayList<LdcInsnNode>();List<Object> changedValues=new ArrayList<Object>();
        for(AbstractInsnNode n:draw.instructions.toArray())if(n instanceof LdcInsnNode&&coordinates.containsKey(((LdcInsnNode)n).cst)){
            LdcInsnNode ldc=(LdcInsnNode)n;changedNodes.add(ldc);changedValues.add(ldc.cst);ldc.cst=coordinates.get(ldc.cst);coordinateEdits++;
        }
        check(coordinateEdits==4,"Expected only the four prior icon position constants");
        int bounds=0;for(AbstractInsnNode n:hit.instructions.toArray())if(n instanceof LdcInsnNode&&Integer.valueOf(14).equals(((LdcInsnNode)n).cst)){
            LdcInsnNode ldc=(LdcInsnNode)n;changedNodes.add(ldc);changedValues.add(ldc.cst);ldc.cst=Integer.valueOf(12);bounds++;
        }check(bounds==2,"Expected two prior icon hitbox constants");
        ClassWriter writer=new ClassWriter(new ClassReader(before.get(UI)),ClassWriter.COMPUTE_MAXS);ui.accept(writer);entries.put(UI,writer.toByteArray());entries.put(ART,Files.readAllBytes(art.toPath()));
        for(int i=0;i<changedNodes.size();i++)changedNodes.get(i).cst=changedValues.get(i);
        for(MethodNode m:ui.methods)check(originals.get(m).equals(HungBySonExpInfoPack.canonical(m)),"Icon method inverse mismatch "+m.name);
        check(entries.keySet().equals(before.keySet()),"No archive additions or removals");int resources=0,classes=0;
        for(String name:before.keySet()){
            if(!name.equals(UI)&&!name.equals(ART))check(Arrays.equals(before.get(name),entries.get(name)),"Unapproved change "+name);
            if(name.endsWith(".class"))classes++;else resources++;
        }
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]>e:entries.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0L);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        Map<String,byte[]> saved=HungBySonExpInfoPack.read(output);check(saved.keySet().equals(entries.keySet()),"Archive roundtrip topology");for(String name:entries.keySet())check(Arrays.equals(saved.get(name),entries.get(name)),"Archive roundtrip "+name);
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("changed.classes",UI);p.setProperty("added.classes","");p.setProperty("changed.resources",ART);
        p.setProperty("icon.bounds.normal","97,46,12,12");p.setProperty("icon.bounds.compact","85,45,12,12");p.setProperty("icon.size","12");p.setProperty("icon.source.svg","src/luna/ui/icons/exp-info.svg");p.setProperty("icon.matches.native.a.size","true");
        p.setProperty("icon.source.svg.sha256",HungBySonExpInfoPack.hash(new File("src/luna/ui/icons/exp-info.svg")));
        p.setProperty("icon.source.filename","icon-info (1).svg");
        OutputStream po=new FileOutputStream(catalogOut);try{p.store(po,"Supplied SVG info badge at exact native A dimensions");}finally{po.close();}
        PrintWriter r=new PrintWriter(new OutputStreamWriter(new FileOutputStream(report),"UTF-8"));r.println("EXP_SVG_ICON=PASS; one image and six icon draw/hitbox constants only; complete method inverse");r.println("ICON_SIZE=12x12; native A=12x12; aligned center unchanged");r.println("UNCHANGED_CLASSES="+(classes-1));r.println("UNCHANGED_RESOURCES="+(resources-1));r.println("SHA256="+p.getProperty("output.sha256"));r.close();System.out.println("EXP_SVG_ICON=PASS SHA256="+p.getProperty("output.sha256"));
    }
}
