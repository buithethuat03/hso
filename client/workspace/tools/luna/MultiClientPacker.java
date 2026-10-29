import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Fast, offline x1 -> x2..x26 packaging using the established launcher ABI. */
public final class MultiClientPacker implements Opcodes {
    static final String MF="META-INF/MANIFEST.MF", INI="hso-xn-source.ini";
    static final String TEMPLATE_SHA="5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC";
    static void check(boolean ok,String message){HsoChildX3Packer.require(ok,message);}
    public static void main(String[] args)throws Exception {
        check(args.length==7,"source template output helper report count suite-name");
        File input=new File(args[0]), templateFile=new File(args[1]), output=new File(args[2]);
        int count=Integer.parseInt(args[5]); String suite=args[6];
        check(count>=2&&count<=26,"Count must be 2..26 (launcher RMS namespaces a..z)");
        check(!output.exists()&&!new File(args[4]).exists(),"Use fresh staged outputs");
        check(suite.length()>0&&suite.indexOf(',')<0&&suite.indexOf('\n')<0&&suite.indexOf('\r')<0,"Invalid suite name");
        String sourceHash=HsoChildX3Packer.hash(input);
        check(TEMPLATE_SHA.equals(HsoChildX3Packer.hash(templateFile)),"Launcher template checksum differs");
        Map<String,byte[]> source=HsoChildX3Packer.readJar(input), template=HsoChildX3Packer.readJar(templateFile);
        check(source.containsKey(MF),"Missing MIDlet manifest");
        Manifest manifest=new Manifest(new ByteArrayInputStream(source.get(MF)));
        Attributes attrs=manifest.getMainAttributes();
        String entry=attrs.getValue("MIDlet-1");
        check(entry!=null&&entry.split(",",-1).length==3,"Not a compatible J2ME x1 MIDlet");
        String main=entry.split(",",-1)[2].trim().replace('.','/');
        check(attrs.getValue("MIDlet-2")==null&&attrs.getValue("0")==null,"Input must be x1, not an existing multi-client suite");
        Map<String,byte[]> classes=new LinkedHashMap<String,byte[]>();
        for(String path:source.keySet()) {
            String upper=path.toUpperCase(Locale.ROOT);
            check(!upper.startsWith("META-INF/")||!(upper.endsWith(".SF")||upper.endsWith(".RSA")||upper.endsWith(".DSA")||upper.endsWith(".EC")),"Signed JAR unsupported; use an unsigned x1");
            if(!path.endsWith(".class"))continue;
            ClassNode c=new ClassNode();new ClassReader(source.get(path)).accept(c,0);
            check(path.equals(c.name+".class"),"Class path/name mismatch: "+path);
            check(!c.name.startsWith("lib/")&&!c.name.startsWith("MrQuyet/")&&!c.name.equals("Static")&&!c.name.equals("X3SlotSupport"),"Input contains reserved launcher classes: "+path);
            for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray()) {
                if(n instanceof LdcInsnNode&&"javax.microedition.rms.RecordStore".equals(((LdcInsnNode)n).cst))
                    check(c.name.equals("HSOSKYPetFeedSupport")&&(m.name.equals("loadRMS")||m.name.equals("saveRMS")),"Unsupported reflective RMS in "+c.name+"."+m.name+"; no output published");
            }
            // Static reset dispatcher needs to reach classes in every original package.
            c.access|=ACC_PUBLIC;ClassWriter writer=new ClassWriter(0);c.accept(writer);
            classes.put(c.name,writer.toByteArray());
        }
        check(classes.containsKey(main),"MIDlet entry class missing: "+main);
        check("javax/microedition/midlet/MIDlet".equals(new ClassReader(classes.get(main)).getSuperName()),"MIDlet must directly extend the native MIDlet API");
        check(!source.containsKey(INI)&&!source.containsKey("MrQuyet/mid.png"),"Reserved launcher resource collision");
        attrs.putValue("MIDlet-Name",suite);
        attrs.putValue("MIDlet-Vendor","XN_"+suite.replaceAll("[^A-Za-z0-9_]","_"));
        attrs.putValue("MIDlet-1",suite+",/MrQuyet/mid.png,MrQuyet.MrQuyet");
        attrs.putValue("HSO-XN-Source-SHA-256",sourceHash);
        attrs.putValue("HSO-XN-Count",String.valueOf(count));
        Map<String,byte[]> result=new LinkedHashMap<String,byte[]>();
        for(String path:source.keySet())if(!path.endsWith(".class")&&!path.equals(MF))result.put(path,source.get(path));
        int wrappers=0;
        for(String path:template.keySet())if(path.endsWith(".class")&&(path.startsWith("lib/")||path.startsWith("MrQuyet/"))){result.put(path,template.get(path));wrappers++;}
        check(wrappers==19,"Unexpected launcher ABI");
        result.put("MrQuyet/mid.png",template.get("MrQuyet/mid.png"));result.put(INI,source.get(MF));
        byte[] helper=HsoChildX3Packer.read(new FileInputStream(args[3]));
        for(int slot=0;slot<count;slot++) {
            String rms=String.valueOf((char)('a'+slot)), prefix=rms+"/";
            attrs.putValue(String.valueOf(slot),suite+" #"+(slot+1)+",/MrQuyet/mid.png,"+prefix.replace('/','.')+main.replace('/','.')+",/"+INI);
            Map<String,String> map=new LinkedHashMap<String,String>();
            for(String name:classes.keySet())map.put(name,prefix+name);
            map.put("Static",prefix+"Static");map.put("X3SlotSupport",prefix+"X3SlotSupport");HsoChildX3Packer.addWrapperTypes(map);
            int id=0;int[] boundaries=new int[4];
            for(String name:classes.keySet()) {
                String target=map.get(name)+".class";check(!result.containsKey(target),"Resource/namespace collision: "+target);
                result.put(target,HsoChildX3Packer.transform(HsoChildX3Packer.addLifecycle(classes.get(name),id++),map,rms,false,boundaries));
            }
            Map<String,String> helperMap=new LinkedHashMap<String,String>(map);helperMap.put("java/lang/StringBuilder","java/lang/StringBuffer");
            result.put(prefix+"X3SlotSupport.class",HsoChildX3Packer.transform(helper,helperMap,rms,true,new int[4]));
            result.put(prefix+"Static.class",HsoChildX3Packer.makeStatic(classes,prefix));
        }
        ByteArrayOutputStream mf=new ByteArrayOutputStream();manifest.write(mf);result.put(MF,mf.toByteArray());
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));
        try{for(Map.Entry<String,byte[]> e:result.entrySet()){JarEntry zip=new JarEntry(e.getKey());zip.setTime(0);jar.putNextEntry(zip);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        Map<String,byte[]> saved=HsoChildX3Packer.readJar(output);
        check(saved.keySet().equals(result.keySet()),"Saved archive entry mismatch");
        for(String path:result.keySet())check(Arrays.equals(result.get(path),saved.get(path)),"Saved archive content mismatch: "+path);
        check(sourceHash.equals(HsoChildX3Packer.hash(input))&&TEMPLATE_SHA.equals(HsoChildX3Packer.hash(templateFile)),"Input changed during build");
        String hash=HsoChildX3Packer.hash(output);
        PrintWriter report=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[4]),"UTF-8"));
        try{report.println("source="+input);report.println("source.sha256="+sourceHash);report.println("template.sha256="+TEMPLATE_SHA);report.println("output.sha256="+hash);report.println("suite="+suite);report.println("count="+count);report.println("source.classes.per.slot="+classes.size());report.println("entries="+saved.size());report.println("checks=archive round-trip; unchanged inputs/resources; isolated namespaces/RMS; pinned launcher");report.println("runtime/server=not tested (fast packaging only)");}finally{report.close();}
        System.out.println("XN_PACK=PASS count="+count+" classes/slot="+classes.size()+" SHA256="+hash);
    }
}
