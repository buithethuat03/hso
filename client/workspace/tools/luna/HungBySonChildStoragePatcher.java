import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Separates HSOCHILD suite identity and every native RMS store from Investigate. */
public final class HungBySonChildStoragePatcher {
    private static final String EXPECTED="6E60A7F923F5A6509660BED882DF96814E0CCF8455174FE6BBC75543ABD5B04E";
    public static final String STORAGE="luna/generated/InvestigateStorage.class";
    public static final String PREFIX="HSOCHILD_";
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("input.jar output.jar report.properties");
        File input=new File(args[0]),output=new File(args[1]);
        check(EXPECTED.equals(HungBySonChildLoginPatcher.hash(input)),"Unexpected pre-storage input");
        check(!output.exists(),"Output already exists");
        Map<String,byte[]> data=HungBySonChildLoginPatcher.read(input);
        byte[] previous=data.get(STORAGE);check(previous!=null,"Storage boundary missing");
        ClassNode node=new ClassNode();new ClassReader(previous).accept(node,0);
        int prefix=0,cache=0,selection=0,fieldValues=0;
        for(FieldNode field:node.fields) {Object value=replacement(field.value);if(value!=field.value){field.value=value;fieldValues++;}}
        for(MethodNode method:node.methods)for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof LdcInsnNode) {
            LdcInsnNode literal=(LdcInsnNode)instruction;
            if("inv_".equals(literal.cst))prefix++;
            if("mc_inv_v1".equals(literal.cst))cache++;
            if("ms_inv_v1".equals(literal.cst))selection++;
            literal.cst=replacement(literal.cst);
        }
        check(prefix==5&&cache==2&&selection==2&&fieldValues==3,"Storage constant layout changed");
        ClassWriter writer=new ClassWriter(new ClassReader(previous),0);node.accept(writer);data.put(STORAGE,writer.toByteArray());
        Manifest manifest=new Manifest(new ByteArrayInputStream(data.get("META-INF/MANIFEST.MF")));
        Attributes a=manifest.getMainAttributes();
        check("Hung_By_Son_Investigate".equals(a.getValue("MIDlet-Vendor")),"Unexpected suite identity");
        String midlet=a.getValue("MIDlet-1");check(midlet!=null&&midlet.indexOf(',')>0,"MIDlet entry missing");
        a.putValue("MIDlet-Name","HSOCHILD");a.putValue("MIDlet-Vendor","HSOCHILD");a.putValue("MIDlet-1","HSOCHILD"+midlet.substring(midlet.indexOf(',')));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();manifest.write(bytes);data.put("META-INF/MANIFEST.MF",bytes.toByteArray());
        JarFile source=new JarFile(input);ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(output));
        try {for(Enumeration<JarEntry> e=source.entries();e.hasMoreElements();) {JarEntry old=e.nextElement();ZipEntry entry=new ZipEntry(old.getName());entry.setTime(old.getTime());zip.putNextEntry(entry);zip.write(data.get(old.getName()));zip.closeEntry();}}finally{zip.close();source.close();}
        Properties report=new Properties();report.setProperty("input.sha256",EXPECTED);report.setProperty("output.sha256",HungBySonChildLoginPatcher.hash(output));
        report.setProperty("suite.name","HSOCHILD");report.setProperty("suite.vendor","HSOCHILD");report.setProperty("rms.prefix",PREFIX);
        report.setProperty("rms.cache","HSOCHILD_mc_v1");report.setProperty("rms.selection","HSOCHILD_ms_v1");report.setProperty("changed.entries",STORAGE+",META-INF/MANIFEST.MF");
        Writer out=new OutputStreamWriter(new FileOutputStream(args[2]),"UTF-8");try{report.store(out,"HSOCHILD isolated data identity");}finally{out.close();}
        System.out.println("STORAGE_PATCH=PASS; suite=HSOCHILD; RMS=HSOCHILD_; only storage boundary + manifest changed");
        System.out.println("SHA256="+report.getProperty("output.sha256"));
    }
    private static Object replacement(Object value) {
        if("inv_".equals(value))return PREFIX;
        if("mc_inv_v1".equals(value))return "mc_v1";
        if("ms_inv_v1".equals(value))return "ms_v1";
        return value;
    }
    private static void check(boolean okay,String message){if(!okay)throw new IllegalStateException(message);}
}
