import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import javax.tools.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Scoped Hung upgrade: adaptive EXP/gold rates, wallet tab and inventory information modes. */
public final class HungEconomyInfoPatcher {
    static final String INPUT="AA16DCCA0DA0A48DB0C0F39088D402D40390FCC15B779C50142BCF731B10A03C";
    static final String BASE="build/hung-exp-rolling-fine-20261028-final/";
    static final String RATE="luna/features/ExpRateSupport", GOLD="luna/features/GoldRateSupport", STATS="HungBySonSkillStats";
    static void check(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    static ClassNode node(Map<String,byte[]> entries,String owner){return HungBySonExpInfoPack.node(entries.get(owner+".class"));}
    static String canon(MethodNode m){return HungBySonExpInfoPack.canonical(m);}
    static Properties properties(String path)throws Exception{Properties p=new Properties();InputStream in=new FileInputStream(path);try{p.load(in);}finally{in.close();}return p;}
    static void writeJar(Map<String,byte[]> entries,File path)throws Exception{
        check(!path.exists(),"Fresh archive "+path);JarOutputStream out=new JarOutputStream(new FileOutputStream(path));
        try{for(Map.Entry<String,byte[]> e:entries.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);out.putNextEntry(z);out.write(e.getValue());out.closeEntry();}}finally{out.close();}
    }
    static byte[] compileTemplate(String name,File template,File work,File input,File support,String micro)throws Exception{
        check(work.isDirectory()||work.mkdirs(),"Template work directory");
        String src=new String(Files.readAllBytes(template.toPath()),"UTF-8");StringBuilder expanded=new StringBuilder(src.length()*2);
        for(int i=0;i<src.length();){
            if(src.charAt(i)=='@'&&i+1<src.length()&&src.charAt(i+1)=='~'){
                int end=src.indexOf('@',i+2);check(end>i,"Template marker");expanded.append(HungBySonCopySpecialPatcher.n(src.substring(i+2,end)));i=end+1;
            }else expanded.append(src.charAt(i++));
        }
        File java=new File(work,name+".java");Writer w=new OutputStreamWriter(new FileOutputStream(java),"UTF-8");try{w.write(expanded.toString());}finally{w.close();}
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();check(compiler!=null,"JDK compiler");
        check(compiler.run(null,null,null,"-encoding","UTF-8","--release","7","-Xlint:-options","-cp",support.getPath()+File.pathSeparator+input.getPath()+File.pathSeparator+micro,"-d",work.getPath(),java.getPath())==0,"Compile "+name);
        ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(new File(work,name+".class").toPath())).accept(c,ClassReader.SKIP_FRAMES);c.version=Opcodes.V1_4;
        byte[] result=HungBySonCopySpecialPatcher.write(c);HungBySonExpInfoPack.screen(name,result);return result;
    }
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),support=new File(a[1]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned rolling/fine release");check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work directory");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Properties p=properties(BASE+"auto-pk.properties"),exp=properties(BASE+"exp.properties");
        check(INPUT.equals(p.getProperty("output.sha256"))&&INPUT.equals(exp.getProperty("output.sha256")),"Pinned source catalogs");
        p.putAll(exp);
        ClassNode oldRate=node(before,RATE),rate=HungBySonExpInfoPack.node(Files.readAllBytes(new File(support,RATE+".class").toPath()));
        check(rate.fields.size()==oldRate.fields.size()&&rate.methods.size()==oldRate.methods.size(),"Rate ABI preserved");
        for(FieldNode f:oldRate.fields){FieldNode next=null;for(FieldNode n:rate.fields)if(n.name.equals(f.name))next=n;
            check(next!=null&&next.desc.equals(f.desc)&&next.access==f.access,"Preserved rate field "+f.name);
            check(f.name.equals("RATE_BUCKETS")?Integer.valueOf(900).equals(next.value):Objects.equals(f.value,next.value),"Rate constant "+f.name);
        }
        Set<String> rateChanges=new HashSet<String>(Arrays.asList("rate","advanceWindow","clearState","<clinit>"));
        for(MethodNode m:oldRate.methods){MethodNode n=HungBySonCopySpecialPatcher.method(rate,m.name,m.desc);check(m.access==n.access,"Rate method ABI");if(!rateChanges.contains(m.name))check(canon(m).equals(canon(n)),"Unchanged rate method "+m.name);}
        rate.version=oldRate.version;out.put(RATE+".class",HungBySonCopySpecialPatcher.write(rate));
        check(!before.containsKey(GOLD+".class"),"Gold module is new");
        byte[] gold=Files.readAllBytes(new File(support,GOLD+".class").toPath());HungBySonExpInfoPack.screen(GOLD,gold);out.put(GOLD+".class",gold);
        HungGoldTrackingPatch.apply(out,p);
        File nativeStage=new File(work,"native-stage.jar");writeJar(out,nativeStage);
        byte[] inventory=compileTemplate("HungInventoryInfo",new File("tools/luna/HungInventoryInfo.java.in"),new File(work,"inventory"),nativeStage,support,a[2]);
        HungInventoryInfoPatch.apply(out,inventory,p);
        File uiStage=new File(work,"ui-stage.jar");writeJar(out,uiStage);
        File tabsWork=new File(work,"tabs");check(tabsWork.mkdirs(),"Fresh tabs compilation directory");
        Map<String,byte[]> compiled=HungBySonCopySpecialPatcher.compile(new File[]{new File("tools/luna/HungBySonModUpLv.java.in"),new File("tools/luna/HungBySonSkillStats.java.in")},tabsWork,uiStage,support,a[2],false);
        ClassNode stats=node(before,STATS),statsNew=HungBySonExpInfoPack.node(compiled.get(STATS+".class"));
        check(statsNew.methods.size()==stats.methods.size()&&statsNew.fields.size()==stats.fields.size()+3,"Only gold tab and economy refresh fields added");
        Set<String> uiChanges=new HashSet<String>(Arrays.asList("expText","target","act","show","uiTick","tabs","<clinit>"));int sameUi=0;
        for(MethodNode m:stats.methods){MethodNode n=HungBySonCopySpecialPatcher.method(statsNew,m.name,m.desc);check(n.access==m.access,"Stats method ABI");if(!uiChanges.contains(m.name)){check(canon(m).equals(canon(n)),"Unchanged stats method "+m.name);sameUi++;}}
        out.put(STATS+".class",compiled.get(STATS+".class"));
        Set<String> allowed=new HashSet<String>(Arrays.asList(RATE+".class",GOLD+".class",STATS+".class","HungInventoryInfo.class"));
        for(String key:new String[]{"gold.tracking.changed.classes","inventory.info.changed.classes"}){
            String value=p.getProperty(key);check(value!=null&&!value.isEmpty(),"Hook change catalog "+key);for(String owner:value.split(","))allowed.add(owner.trim());
        }
        List<String> changed=new ArrayList<String>();int classes=0;
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;
            if(!Arrays.equals(before.get(name),out.get(name))){
                check(allowed.contains(name),"Only scoped economy/HUD classes change: "+name);changed.add(name);
                if(name.equals(RATE+".class")||name.equals(GOLD+".class")||name.equals(STATS+".class")||name.equals("HungInventoryInfo.class"))HungBySonExpInfoPack.screen(name,out.get(name));
                else{
                    check(HungBySonExpInfoPack.node(out.get(name)).version==HungBySonExpInfoPack.node(before.get(name)).version,"Native class version preserved "+name);
                    String oldPool=new String(before.get(name),"ISO-8859-1"),newPool=new String(out.get(name),"ISO-8859-1");
                    for(String ref:new String[]{"java/lang/StringBuilder","java/lang/invoke/","java/nio/","java/time/","java/awt/","java/lang/reflect/","addSuppressed"})
                        check(!newPool.contains(ref)||oldPool.contains(ref),"No new unsupported native reference "+name+" -> "+ref);
                }
            }
        }
        check(out.size()==before.size()+2&&classes==287,"Exactly gold and inventory helpers added");
        writeJar(out,output);
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));
        p.setProperty("exp.rate.window.seconds","900");p.setProperty("exp.rate.minimum.seconds","300");p.setProperty("gold.rate.window.seconds","900");
        p.setProperty("gold.info.tab","true");p.setProperty("inventory.info.modes","3");p.setProperty("auto.pk.class.count",String.valueOf(classes));
        p.setProperty("auto.pk.changed.classes",join(changed));
        OutputStream cfg=new FileOutputStream(a[5]+".properties");try{p.store(cfg,"Adaptive economy rates, wallet tab and inventory information");}finally{cfg.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{log.println("PASS pinned input; adaptive rate and wallet native hooks; inventory modes; four-tab UI; "+sameUi+" stats methods unchanged; resources and manifest unchanged");log.println("CHANGED="+join(changed));log.println("CLASSES="+classes);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS economy info "+p.getProperty("output.sha256"));
    }
    static String join(Collection<String> values){StringBuilder result=new StringBuilder();for(String value:values){if(result.length()>0)result.append(',');result.append(value);}return result.toString();}
}
