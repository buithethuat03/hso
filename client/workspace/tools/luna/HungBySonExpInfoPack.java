import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Merges the reviewed semantic support into the separately verified native UI patch. */
public final class HungBySonExpInfoPack {
    private static final String SUPPORT = "luna/features/ExpEstimateSupport.class";
    private static final String RECORD = "luna/features/ExpEstimateSupport$EstimateRecord.class";
    private static final String RATE = "luna/features/ExpRateSupport.class";
    private static void check(boolean b, String why) { if (!b) throw new IllegalStateException(why); }
    static ClassNode node(byte[] bytes) {
        ClassNode c = new ClassNode(); new ClassReader(bytes).accept(c, 0); return c;
    }
    static Map<String,byte[]> read(File file) throws Exception {
        Map<String,byte[]> data = new LinkedHashMap<String,byte[]>();
        JarFile jar = new JarFile(file);
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                check(!data.containsKey(e.getName()), "Duplicate entry " + e.getName());
                InputStream in = jar.getInputStream(e); ByteArrayOutputStream out = new ByteArrayOutputStream();
                try { byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) != -1) out.write(buf,0,n); }
                finally { in.close(); }
                data.put(e.getName(),out.toByteArray());
            }
        } finally { jar.close(); }
        return data;
    }
    static String hash(File file) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256"); InputStream in = new FileInputStream(file);
        try { byte[] b = new byte[8192]; int n; while ((n=in.read(b))!=-1) d.update(b,0,n); } finally { in.close(); }
        StringBuilder s = new StringBuilder(); for (byte b:d.digest()) s.append(String.format("%02X",b & 255)); return s.toString();
    }
    private static Map<String,MethodNode> methods(ClassNode c) {
        Map<String,MethodNode> m = new LinkedHashMap<String,MethodNode>();
        for (MethodNode n:c.methods) { check(!m.containsKey(n.name+n.desc),"Duplicate method"); m.put(n.name+n.desc,n); }
        return m;
    }
    static String canonical(MethodNode m) {
        Map<LabelNode,Integer> labels = new IdentityHashMap<LabelNode,Integer>(); int index=0;
        for (AbstractInsnNode n:m.instructions.toArray()) { if(n instanceof LabelNode) labels.put((LabelNode)n,index); if(n.getOpcode()>=0) index++; }
        StringBuilder b=new StringBuilder().append(m.access).append(m.name).append(m.desc).append(m.exceptions);
        for(AbstractInsnNode n:m.instructions.toArray()) if(n.getOpcode()>=0) {
            b.append('|').append(n.getOpcode());
            if(n instanceof VarInsnNode) b.append(':').append(((VarInsnNode)n).var);
            else if(n instanceof IntInsnNode) b.append(':').append(((IntInsnNode)n).operand);
            else if(n instanceof LdcInsnNode) b.append(':').append(((LdcInsnNode)n).cst);
            else if(n instanceof FieldInsnNode) { FieldInsnNode x=(FieldInsnNode)n; b.append(':').append(x.owner).append('.').append(x.name).append(x.desc); }
            else if(n instanceof MethodInsnNode) { MethodInsnNode x=(MethodInsnNode)n; b.append(':').append(x.owner).append('.').append(x.name).append(x.desc).append(x.itf); }
            else if(n instanceof TypeInsnNode) b.append(':').append(((TypeInsnNode)n).desc);
            else if(n instanceof JumpInsnNode) b.append(':').append(labels.get(((JumpInsnNode)n).label));
            else if(n instanceof IincInsnNode) { IincInsnNode x=(IincInsnNode)n; b.append(':').append(x.var).append(':').append(x.incr); }
            else if(n instanceof TableSwitchInsnNode) { TableSwitchInsnNode x=(TableSwitchInsnNode)n; b.append(':').append(x.min).append(':').append(x.max).append(':').append(labels.get(x.dflt)); for(LabelNode l:x.labels)b.append(':').append(labels.get(l)); }
            else if(n instanceof LookupSwitchInsnNode) { LookupSwitchInsnNode x=(LookupSwitchInsnNode)n; b.append(':').append(x.keys).append(':').append(labels.get(x.dflt)); for(LabelNode l:x.labels)b.append(':').append(labels.get(l)); }
            else if(n instanceof MultiANewArrayInsnNode) { MultiANewArrayInsnNode x=(MultiANewArrayInsnNode)n; b.append(':').append(x.desc).append(':').append(x.dims); }
            else check(!(n instanceof InvokeDynamicInsnNode),"Unexpected invokedynamic");
        }
        for(TryCatchBlockNode t:m.tryCatchBlocks) b.append("!T:").append(labels.get(t.start)).append(':').append(labels.get(t.end)).append(':').append(labels.get(t.handler)).append(':').append(t.type);
        return b.toString();
    }
    static void verifyClass(byte[] oldBytes,byte[] newBytes,Set<String> allowed,Set<String> additions) {
        ClassNode before=node(oldBytes),after=node(newBytes);
        check(before.name.equals(after.name)&&before.superName.equals(after.superName)&&before.access==after.access,"Class ABI changed");
        check(before.interfaces.equals(after.interfaces),"Class interfaces changed");
        Map<String,MethodNode> oldMethods=methods(before),newMethods=methods(after);
        for(String key:oldMethods.keySet()) {
            check(newMethods.containsKey(key),"Removed support method "+key);
            if(!allowed.contains(key)) check(canonical(oldMethods.get(key)).equals(canonical(newMethods.get(key))),"Unexpected support method change "+key);
        }
        for(String key:newMethods.keySet()) if(!oldMethods.containsKey(key)) check(additions.contains(key),"Unexpected added method "+key);
        check(before.fields.size()==after.fields.size(),"Support field topology changed");
        for(int i=0;i<before.fields.size();i++) {
            FieldNode a=before.fields.get(i),b=after.fields.get(i);
            check(a.name.equals(b.name)&&a.desc.equals(b.desc)&&a.access==b.access,"Support field ABI changed");
            if(!"CALCULATING".equals(a.name)) check(Objects.equals(a.value,b.value),"Support constant changed "+a.name);
        }
    }
    static void screen(String name,byte[] data) throws Exception {
        ClassNode c=node(data); check((c.version&65535)<=51,"Class version "+name);
        String pool=new String(data,"ISO-8859-1");
        for(String ref:new String[]{"java/lang/StringBuilder","java/lang/invoke/","java/util/","java/nio/","java/time/","java/awt/","java/lang/reflect/","addSuppressed"})
            check(pool.indexOf(ref)<0,"Non-CLDC reference "+name+" -> "+ref);
    }
    public static void main(String[] args) throws Exception {
        check(args.length==8,"ui.jar compiledSupportDir final.jar uiCatalog baseCatalog finalCatalog report source.jar");
        File input=new File(args[0]),compiled=new File(args[1]),output=new File(args[2]),catalogIn=new File(args[3]),baseCatalog=new File(args[4]),catalogOut=new File(args[5]),report=new File(args[6]);
        check(!output.exists()&&!catalogOut.exists()&&!report.exists(),"Outputs must be fresh");
        Properties p=new Properties(); InputStream base=new FileInputStream(baseCatalog);try{p.load(base);}finally{base.close();}
        String releaseSource=p.getProperty("output.sha256");
        InputStream pin=new FileInputStream(catalogIn); try{p.load(pin);}finally{pin.close();}
        check(releaseSource.equals(p.getProperty("input.sha256"))&&releaseSource.equals("38CBF77190184F479645EF44E9EA74898C19ACB0227C16E245DB6834D83877DE"),"UI stage is not based on the pinned percentage release");
        check(hash(input).equals(p.getProperty("output.sha256")),"UI stage/catalog hash mismatch");
        check("1".equals(p.getProperty("slots")),"x1 only");
        Map<String,byte[]> original=read(input),result=new LinkedHashMap<String,byte[]>(original);
        Set<String> allowed=new LinkedHashSet<String>(Arrays.asList("setStore(Lluna/api/ExpEstimateStore;)V","snapshot(Ljava/lang/String;III)V","gain(Ljava/lang/String;IIII)V","disconnect()V","line(I)Ljava/lang/String;"));
        Set<String> additions=Collections.singleton("info(IJ)Ljava/lang/String;");
        List<String> changed=new ArrayList<String>(),added=new ArrayList<String>();
        String[] names={SUPPORT,RECORD,RATE,"luna/api/ExpEstimateStore.class","luna/ui/ExpPercentInterpolator.class"};
        for(String name:names) {
            File file=new File(compiled,name.replace('/',File.separatorChar)); check(file.isFile(),"Missing compiled support "+name);
            byte[] data=Files.readAllBytes(file.toPath()); screen(name,data);
            if(original.containsKey(name)) {
                verifyClass(original.get(name),data,name.equals(SUPPORT)?allowed:Collections.<String>emptySet(),name.equals(SUPPORT)?additions:Collections.<String>emptySet());
                // Keep the existing ABI-identical helper/record payload, including its debug metadata.
                if(!name.equals(SUPPORT)) continue;
                check(!Arrays.equals(data,original.get(name)),"Support did not change"); changed.add(name);
            } else { check(name.equals(RATE),"Unexpected new support "+name); added.add(name); }
            result.put(name,data);
        }
        for(String name:original.keySet()) if(!changed.contains(name)) check(Arrays.equals(original.get(name),result.get(name)),"Unapproved payload change "+name);
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));
        try { for(Map.Entry<String,byte[]> e:result.entrySet()) { JarEntry entry=new JarEntry(e.getKey()); entry.setTime(0L); jar.putNextEntry(entry); jar.write(e.getValue()); jar.closeEntry(); } }
        finally { jar.close(); }
        Map<String,byte[]> reread=read(output); check(result.keySet().equals(reread.keySet()),"Archive topology changed");
        int classes=0; for(String name:result.keySet()){check(Arrays.equals(result.get(name),reread.get(name)),"Archive roundtrip changed "+name);if(name.endsWith(".class"))classes++;}
        File sourceJar=new File(args[7]);check(hash(sourceJar).equals(releaseSource),"Original release changed");
        Map<String,byte[]> release=read(sourceJar);
        Set<String> delta=new LinkedHashSet<String>(Arrays.asList(p.getProperty("changed.classes").split(",")));delta.addAll(changed);
        Set<String> newNames=new LinkedHashSet<String>(Arrays.asList(p.getProperty("added.classes").split(",")));newNames.addAll(added);
        check(delta.size()==3&&newNames.size()==2,"Unexpected final archive allow-list");
        check(result.size()==release.size()+newNames.size(),"Unexpected final archive entry count");
        int resources=0;
        for(String name:release.keySet()){
            check(result.containsKey(name),"Removed original entry "+name);
            if(!delta.contains(name))check(Arrays.equals(release.get(name),result.get(name)),"Unexpected final payload change "+name);
            else check(!Arrays.equals(release.get(name),result.get(name)),"Reported payload did not change "+name);
            if(name.endsWith(".class"))check(node(release.get(name)).version==node(result.get(name)).version,"Original class version changed "+name);
            else {check(Arrays.equals(release.get(name),result.get(name)),"Resource changed "+name);resources++;}
        }
        for(String name:result.keySet())if(!release.containsKey(name)){check(newNames.contains(name),"Unexpected new entry "+name);screen(name,result.get(name));}
        check(Arrays.equals(release.get(p.getProperty("slot.0.hud.owner")+".class"),result.get(p.getProperty("slot.0.hud.owner")+".class")),"Native HUD class changed");
        p.setProperty("info.ui.stage.sha256",hash(input));p.setProperty("output.sha256",hash(output));p.setProperty("calculating.text","Đang tính toán...");p.setProperty("info.enabled","true");
        p.setProperty("info.semantic.changed",String.join(",",changed));p.setProperty("info.semantic.added",String.join(",",added));
        p.setProperty("changed.classes",p.getProperty("changed.classes")+","+String.join(",",changed));
        p.setProperty("added.classes",p.getProperty("added.classes")+","+String.join(",",added));
        OutputStream po=new FileOutputStream(catalogOut);try{p.store(po,"Verified EXP info UI and semantic support");}finally{po.close();}
        PrintWriter log=new PrintWriter(new OutputStreamWriter(new FileOutputStream(report),"UTF-8"));
        log.println("EXP_INFO_PACK=PASS; unmodified methods canonical; exact final archive allow-list; CLDC screened");log.println("CHANGED="+delta);log.println("ADDED="+newNames);log.println("UNCHANGED_RESOURCES="+resources);log.println("NATIVE_HUD_BYTE_IDENTICAL=true");log.println("CLASSES="+classes);log.println("SHA256="+hash(output));log.close();
        System.out.println("EXP_INFO_PACK=PASS classes="+classes+" SHA256="+hash(output));
    }
}
