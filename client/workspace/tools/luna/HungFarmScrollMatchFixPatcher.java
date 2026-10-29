import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.tree.*;

/** Matches the existing Auto Farm scroll search rather than assuming a template ID/category. */
public final class HungFarmScrollMatchFixPatcher {
    static final String INPUT="2F07C9183C568D929F29BC2FE5B12FBD73FBB36CFEA36C48C86CE22A2E6651AF";
    static final String BRIDGE=HungSaleReturnScrollPatcher.B;
    static void check(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    public static void main(String[] a)throws Exception{
        check(a.length==4,"input.jar supportDir output.jar report.properties");File input=new File(a[0]),output=new File(a[2]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned return-scroll release");check(!output.exists()&&!new File(a[3]).exists(),"Fresh outputs");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        String marker=HungSaleReturnScrollPatcher.farmScrollName(input,before);HungSaleReturnScrollPatcher.scrollName=marker;
        ClassNode bridge=HungBySonExpInfoPack.node(before.get(BRIDGE+".class"));String desc="()L"+HungSaleReturnScrollPatcher.ITEM+";";
        MethodNode original=HungAutoAttackNativeBuilder.method(bridge,"findScroll",desc);
        MethodNode fixed=HungAutoAttackNativeBuilder.method(HungSaleReturnScrollPatcher.bridge(),"findScroll",desc);
        bridge.methods.set(bridge.methods.indexOf(original),fixed);byte[] result=HungAutoAttackNativeBuilder.bytes(bridge);
        HungBySonExpInfoPack.verifyClass(before.get(BRIDGE+".class"),result,Collections.singleton("findScroll"+desc),Collections.<String>emptySet());
        HungBySonExpInfoPack.screen(BRIDGE,result);out.put(BRIDGE+".class",result);
        for(String key:before.keySet())if(!key.equals(BRIDGE+".class"))check(Arrays.equals(before.get(key),out.get(key)),"Unrelated class/resource "+key);
        check(out.size()==before.size(),"Entry set unchanged");JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));
        try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        Properties p=new Properties();p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("classes","280");
        p.setProperty("changed.method",BRIDGE+".findScroll"+desc);p.setProperty("scroll.name",marker);p.setProperty("scroll.match.source",HungSaleReturnScrollPatcher.FARM+"."+HungSaleReturnScrollPatcher.n("lliiiili")+"()V");
        p.setProperty("scroll.id","actual matched item O; no fixed ID/category/quantity gate");p.setProperty("native.classes","byte-identical");p.setProperty("resources","byte-identical");
        OutputStream cfg=new FileOutputStream(a[3]);try{p.store(cfg,"Auto Sell identifies the same scroll as native Farm and sends its actual ID");}finally{cfg.close();}
        System.out.println("FARM_SCROLL_MATCH_FIX=PASS SHA256="+p.getProperty("output.sha256"));
    }
}
