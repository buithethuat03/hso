import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Build all three complete namespaces from the current single-client binary. */
public final class HsoChildX3Packer {
    static final String[] PACKAGES = {"", "b/", "c/"};
    static final String[] PREFIXES = {"a", "b", "c"};
    static final String SUPPORT = "X3SlotSupport";
    static final String RMS = "javax/microedition/rms/RecordStore";
    static final String MANIFEST = "META-INF/MANIFEST.MF";

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException(
                "Usage: HsoChildX3Packer source.jar template-x3.jar new.jar X3SlotSupport.class report.txt");
        File input = new File(args[0]), templateFile = new File(args[1]), output = new File(args[2]);
        require(!output.exists(), "Output exists; use a new filename");
        String sourceHash = hash(input);
        Map<String, byte[]> source = readJar(input);
        String templateHash = hash(templateFile);
        require(templateHash.equals("5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC"), "Unexpected x3 template; inspect its wrapper ABI first");
        Map<String, byte[]> template = readJar(templateFile);
        Map<String, byte[]> classes = new LinkedHashMap<String, byte[]>();
        for (Map.Entry<String, byte[]> e : source.entrySet()) {
            if (e.getKey().endsWith(".class")) classes.put(e.getKey().substring(0, e.getKey().length()-6), e.getValue());
        }
        require(classes.size() == 216, "Expected the current 216-class HSO CHILD input");
        require(!classes.containsKey(SUPPORT), "Input is already packed");
        Manifest mf = new Manifest(new ByteArrayInputStream(source.get(MANIFEST)));
        Attributes a = mf.getMainAttributes();
        require("HSO CHILD".equals(a.getValue("MIDlet-Name")), "Not HSO CHILD");
        require("221.121.0.242".equals(a.getValue("HSO-Server-IP")), "Unexpected endpoint");
        a.putValue("MIDlet-Name", "HSO CHILD X3");
        a.putValue("MIDlet-Vendor", "HSO_X3");
        a.putValue("MIDlet-1", "HSO CHILD X3,/MrQuyet/mid.png,MrQuyet.MrQuyet");
        a.remove(new Attributes.Name("MIDlet-2")); a.remove(new Attributes.Name("MIDlet-3"));
        for (int i = 0; i < 3; i++) a.putValue(String.valueOf(i),
                "HSO CHILD "+(i+1)+",/MrQuyet/mid.png,"+PACKAGES[i].replace('/', '.')+"TemMidlet,/1786000454049.ini");
        a.putValue("Implementation-Title", "HSO CHILD X3 Full Map Pickup");
        a.putValue("HSO-X3-Source-SHA-256", sourceHash);
        a.putValue("HSO-X3-RMS-Prefixes", "a,b,c");
        a.putValue("HSO-X3-Template-SHA-256", templateHash);
        ByteArrayOutputStream mfBytes = new ByteArrayOutputStream();
        mf.write(mfBytes);
        Map<String, byte[]> result = new LinkedHashMap<String, byte[]>();
        result.put(MANIFEST, mfBytes.toByteArray());
        for (Map.Entry<String, byte[]> e : source.entrySet()) {
            if (!e.getKey().endsWith(".class") && !MANIFEST.equals(e.getKey())) result.put(e.getKey(), e.getValue());
        }
        int wrapperCount = 0;
        for (Map.Entry<String,byte[]> e : template.entrySet()) {
            if ((e.getKey().startsWith("lib/") || e.getKey().startsWith("MrQuyet/")) && e.getKey().endsWith(".class")) {
                result.put(e.getKey(),e.getValue()); wrapperCount++;
            }
        }
        require(wrapperCount==19, "Expected the supplied template's 19 shared wrapper classes");
        result.put("MrQuyet/mid.png",template.get("MrQuyet/mid.png"));
        // Virtual MIDlet properties must come from the current game, not the old Luna.
        result.put("1786000454049.ini", source.get(MANIFEST));
        byte[] support = read(new FileInputStream(args[3]));
        for (int slot = 0; slot < 3; slot++) {
            Map<String, String> map = new LinkedHashMap<String, String>();
            for (String name : classes.keySet()) map.put(name, PACKAGES[slot]+name);
            map.put(SUPPORT, PACKAGES[slot]+SUPPORT);
            map.put("Static", PACKAGES[slot]+"Static");
            addWrapperTypes(map);
            int[] counts = new int[4];
            int classId = 0;
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                byte[] bytes = transform(addLifecycle(e.getValue(), classId++), map, slot, false, counts);
                result.put(map.get(e.getKey())+".class", bytes);
            }
            result.put(map.get(SUPPORT)+".class", transform(support, map, slot, true, new int[4]));
            result.put(PACKAGES[slot]+"Static.class", makeStatic(classes, PACKAGES[slot]));
            require(Arrays.equals(counts, new int[]{2,1,1,2}), "Unexpected RMS boundaries: "+Arrays.toString(counts));
            System.out.println("slot="+(slot+1)+" classes="+(classes.size()+2)+" rms(open/list/delete/reflect)="+Arrays.toString(counts));
        }
        checkReferences(result, classes.keySet());
        output.getAbsoluteFile().getParentFile().mkdirs();
        JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String, byte[]> e : result.entrySet()) {
                JarEntry entry = new JarEntry(e.getKey());
                entry.setTime(0);
                out.putNextEntry(entry); out.write(e.getValue()); out.closeEntry();
            }
        } finally { out.close(); }
        Map<String, byte[]> actual = readJar(output);
        require(actual.keySet().equals(result.keySet()), "Archive entries differ after writing");
        for (String name : result.keySet()) require(Arrays.equals(result.get(name), actual.get(name)), "CRC/content mismatch: "+name);
        for (String name : source.keySet()) {
            if (!name.endsWith(".class") && !name.equals(MANIFEST))
                require(Arrays.equals(source.get(name), actual.get(name)), "Resource changed: "+name);
        }
        require(sourceHash.equals(hash(input)), "Input changed during build");
        require(templateHash.equals(hash(templateFile)), "Template changed during build");
        PrintWriter report = new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[4]), "UTF-8"));
        report.println("source="+input.getPath());
        report.println("source.sha256="+sourceHash);
        report.println("output="+output.getPath());
        report.println("output.sha256="+hash(output));
        report.println("template="+templateFile.getPath()); report.println("template.sha256="+templateHash);
        report.println("classes=673"); report.println("entries="+actual.size());
        report.println("resources=732 (source manifest changed; source's other 729 resources byte-identical; wrapper icon and virtual MIDlet ini added)");
        report.println("namespaces=default,b/,c/ (all 216 source classes copied, including luna/*)");
        report.println("launcher=MrQuyet.MrQuyet (one native MIDlet; three virtual lib.MIDlets)");
        report.println("wrappers=19 classes copied byte-identically from supplied template");
        report.println("lifecycle=generated Static registration/cinitclone/clears for all 216 game classes per slot");
        report.println("rms.prefixes=a,b,c");
        report.println("rms.boundaries.per.slot=open:2,list:1,delete:1,reflective.config:2");
        for (String name:template.keySet()) if ((name.startsWith("lib/") || name.startsWith("MrQuyet/")) && name.endsWith(".class"))
            require(Arrays.equals(template.get(name),actual.get(name)),"Wrapper modified: "+name);
        report.println("archive.crc/content=OK"); report.println("cross-slot.game.references=NONE");
        report.close();
        System.out.println("archive/resource/reference checks=OK sha256="+hash(output));
    }

    static byte[] transform(byte[] raw, Map<String,String> map, int slot, boolean helper, int[] counts) {
        return transformInternal(raw, map, PREFIXES[slot], helper, counts, false);
    }

    /** Parameterized boundary for the reusable xN packer; legacy x3 callers stay unchanged. */
    static byte[] transform(byte[] raw, Map<String,String> map, String rmsPrefix, boolean helper, int[] counts) {
        return transformInternal(raw, map, rmsPrefix, helper, counts, true);
    }

    static byte[] transformInternal(byte[] raw, Map<String,String> map, String rmsPrefix, boolean helper, int[] counts, boolean generic) {
        ClassNode cn = new ClassNode(); new ClassReader(raw).accept(cn, 0);
        require(cn.version <= 51, "Unsupported class version");
        require(cn.signature == null && cn.visibleAnnotations == null && cn.invisibleAnnotations == null,
                "Unsupported class signature/annotations");
        cn.outerClass = RepackSupportX3.cls(cn.outerClass, map);
        cn.outerMethodDesc = RepackSupportX3.desc(cn.outerMethodDesc, map);
        for (InnerClassNode inner : cn.innerClasses) {
            inner.name = RepackSupportX3.cls(inner.name, map);
            inner.outerName = RepackSupportX3.cls(inner.outerName, map);
        }
        for (FieldNode f : cn.fields) require(f.signature == null && f.visibleAnnotations == null && f.invisibleAnnotations == null, "Unsupported field metadata");
        for (MethodNode m : cn.methods) {
            require(m.signature == null && m.visibleAnnotations == null && m.invisibleAnnotations == null, "Unsupported method metadata");
            for (AbstractInsnNode n : m.instructions.toArray()) {
                if (n instanceof FrameNode) {
                    FrameNode frame = (FrameNode)n; remapFrame(frame.local, map); remapFrame(frame.stack, map);
                } else if (n instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode)n;
                    if (ldc.cst instanceof String) {
                        String value = (String)ldc.cst;
                        if (!helper && value.equals("hsosky_cfg")) {
                            require(cn.name.equals("HSOSKYPetFeedSupport") && (m.name.equals("loadRMS") || m.name.equals("saveRMS")), "Unknown config RMS literal");
                            ldc.cst = rmsPrefix+value; counts[3]++;
                        } else if (map.containsKey(value.replace('.', '/')) && (!generic || isClassLookup(n))) {
                            ldc.cst = map.get(value.replace('.', '/')).replace('/', '.');
                        }
                    }
                } else if (!helper && n instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode)n;
                    if (call.owner.equals(RMS)) {
                        int count = call.name.equals("openRecordStore") ? 0 : call.name.equals("listRecordStores") ? 1 : call.name.equals("deleteRecordStore") ? 2 : -1;
                        if (count >= 0) {
                            require(call.getOpcode() == Opcodes.INVOKESTATIC, "Unexpected RMS invocation");
                            if (count == 0) require(call.desc.equals("(Ljava/lang/String;Z)Ljavax/microedition/rms/RecordStore;")
                                    || (generic && call.desc.equals("(Ljava/lang/String;ZIZ)Ljavax/microedition/rms/RecordStore;")), "Unsupported RMS overload");
                            call.owner = "lib/RecordStore";
                            call.name = rmsPrefix + call.name.substring(1); counts[count]++;
                        }
                    }
                    if ((call.owner.equals("java/lang/Class") && call.name.equals("getResourceAsStream"))
                            || (call.owner.equals("javax/microedition/lcdui/Image") && call.name.equals("createImage") && call.desc.startsWith("(Ljava/lang/String;"))) {
                        m.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC, SUPPORT,
                                "absoluteResource", "(Ljava/lang/String;)Ljava/lang/String;", false));
                    }
                }
            }
            if (!helper && (m.name.equals("paint") || m.name.equals("keyPressed") || m.name.equals("keyReleased"))
                    && (cn.superName.equals("javax/microedition/lcdui/Canvas") || (generic && (cn.superName.equals("com/nokia/mid/ui/FullCanvas") || cn.superName.equals("javax/microedition/lcdui/game/GameCanvas"))))) {
                m.name = m.name.equals("paint") ? "PAINT" : m.name.equals("keyPressed") ? "leyPressed" : "leyReleased";
            }
        }
        ClassWriter prepared = new ClassWriter(0); cn.accept(prepared);
        return RepackSupportX3.remap(prepared.toByteArray(), map);
    }

    static boolean isClassLookup(AbstractInsnNode literal) {
        AbstractInsnNode next=literal.getNext();
        while(next!=null && next.getOpcode()<0)next=next.getNext();
        if(!(next instanceof MethodInsnNode))return false;
        MethodInsnNode call=(MethodInsnNode)next;
        return call.owner.equals("java/lang/Class") && call.name.equals("forName") && call.desc.equals("(Ljava/lang/String;)Ljava/lang/Class;");
    }

    static void remapFrame(List<Object> values, Map<String,String> map) {
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) if (values.get(i) instanceof String) {
            String name = (String)values.get(i);
            values.set(i, name.startsWith("[") ? RepackSupportX3.desc(name, map) : RepackSupportX3.cls(name, map));
        }
    }

    static void checkReferences(final Map<String,byte[]> entries, Set<String> sourceClasses) {
        for (String path : entries.keySet()) {
            if (!path.endsWith(".class")) continue;
            final String owner = path.substring(0, path.length()-6);
            if (owner.startsWith("lib/") || owner.startsWith("MrQuyet/")) continue;
            final String slot = owner.startsWith("b/") ? "b/" : owner.startsWith("c/") ? "c/" : "";
            ClassNode cn = new ClassNode(); new ClassReader(entries.get(path)).accept(cn, 0);
            checkType(cn.superName, slot, entries); for (String iface : cn.interfaces) checkType(iface, slot, entries);
            for (FieldNode f : cn.fields) checkDesc(f.desc, slot, entries);
            for (MethodNode m : cn.methods) {
                checkDesc(m.desc, slot, entries);
                for (AbstractInsnNode n : m.instructions.toArray()) {
                    if (n instanceof FieldInsnNode) { FieldInsnNode f=(FieldInsnNode)n; checkType(f.owner,slot,entries); checkDesc(f.desc,slot,entries); }
                    else if (n instanceof MethodInsnNode) { MethodInsnNode c=(MethodInsnNode)n; checkType(c.owner,slot,entries); checkDesc(c.desc,slot,entries); }
                    else if (n instanceof TypeInsnNode) checkType(((TypeInsnNode)n).desc,slot,entries);
                    else if (n instanceof MultiANewArrayInsnNode) checkDesc(((MultiANewArrayInsnNode)n).desc,slot,entries);
                    else if (n instanceof LdcInsnNode && ((LdcInsnNode)n).cst instanceof Type) checkDesc(((Type)((LdcInsnNode)n).cst).getDescriptor(),slot,entries);
                }
            }
        }
    }

    static void checkDesc(String desc, String slot, Map<String,byte[]> entries) {
        for (int i=0; i<desc.length(); i++) if (desc.charAt(i)=='L') {
            int end=desc.indexOf(';',i); require(end>=0,"Bad descriptor"); checkType(desc.substring(i+1,end),slot,entries); i=end;
        }
    }
    static void checkType(String name, String slot, Map<String,byte[]> entries) {
        if (name == null) return;
        if (name.startsWith("[")) { checkDesc(name,slot,entries); return; }
        if (name.startsWith("java/") || name.startsWith("javax/")) return;
        require(entries.containsKey(name+".class"), "Missing referenced class: "+name);
        if (name.startsWith("lib/") || name.startsWith("MrQuyet/")) return;
        String targetSlot=name.startsWith("b/") ? "b/" : name.startsWith("c/") ? "c/" : "";
        require(slot.equals(targetSlot), "Cross-slot class reference: "+name);
    }

    static void addWrapperTypes(Map<String,String> map) {
        map.put("javax/microedition/midlet/MIDlet","lib/MIDlet");
        map.put("javax/microedition/lcdui/Canvas","lib/Canvas");
        map.put("javax/microedition/lcdui/game/GameCanvas","lib/GameCanvas");
        map.put("com/nokia/mid/ui/FullCanvas","lib/FullCanvas");
        map.put("javax/microedition/lcdui/Display","lib/Display");
        map.put("javax/microedition/lcdui/TextBox","lib/TextBox");
        map.put("javax/microedition/lcdui/Form","lib/Form");
        map.put("javax/microedition/lcdui/List","lib/List");
    }

    /** Mirror the template's restartable static state with the CURRENT game fields. */
    static byte[] addLifecycle(byte[] raw, int id) {
        ClassNode cn=new ClassNode(); new ClassReader(raw).accept(cn,0);
        boolean iface=(cn.access & Opcodes.ACC_INTERFACE)!=0;
        if (iface) return raw;
        for (FieldNode f:cn.fields) if ((f.access & Opcodes.ACC_STATIC)!=0 && f.value==null) f.access &= ~Opcodes.ACC_FINAL;
        MethodNode initializer=null;
        for (MethodNode m:cn.methods) {
            require(!m.name.equals("cinitclone") && !m.name.equals("clears"), "Already wrapped class");
            if (m.name.equals("<clinit>")) initializer=m;
        }
        if (initializer==null) {
            initializer=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"cinitclone","()V",null,null);
            initializer.instructions.add(new InsnNode(Opcodes.RETURN)); cn.methods.add(initializer);
        } else { initializer.name="cinitclone"; initializer.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC; }
        MethodNode clinit=new MethodNode(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);
        clinit.instructions.add(new LdcInsnNode(Integer.valueOf(id)));
        clinit.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"Static","regClass","(I)V",false));
        clinit.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,cn.name,"cinitclone","()V",false));
        clinit.instructions.add(new InsnNode(Opcodes.RETURN)); clinit.maxStack=1; cn.methods.add(clinit);
        MethodNode clear=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"clears","()V",null,null);
        for (FieldNode f:cn.fields) if ((f.access & Opcodes.ACC_STATIC)!=0 && f.value==null) {
            char type=f.desc.charAt(0); int op=type=='J' ? Opcodes.LCONST_0 : type=='D' ? Opcodes.DCONST_0 : type=='F' ? Opcodes.FCONST_0 : type=='L'||type=='[' ? Opcodes.ACONST_NULL : Opcodes.ICONST_0;
            clear.instructions.add(new InsnNode(op)); clear.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC,cn.name,f.name,f.desc));
        }
        clear.instructions.add(new InsnNode(Opcodes.RETURN)); clear.maxStack=2; cn.methods.add(clear);
        ClassWriter writer=new ClassWriter(0); cn.accept(writer); return writer.toByteArray();
    }

    static byte[] makeStatic(Map<String,byte[]> classes, String prefix) {
        Set<String> names=classes.keySet();
        ClassWriter cw=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String owner=prefix+"Static";
        cw.visit(Opcodes.V1_3,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL,owner,null,"lib/Stack",null);
        cw.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"staticvector","Ljava/util/Vector;",null,null).visitEnd();
        MethodVisitor m=cw.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null); m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,0); m.visitFieldInsn(Opcodes.GETSTATIC,owner,"staticvector","Ljava/util/Vector;");
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"lib/Stack","<init>","(Ljava/util/Vector;)V",false); m.visitInsn(Opcodes.RETURN); m.visitMaxs(0,0); m.visitEnd();
        m=cw.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null); m.visitCode();
        m.visitTypeInsn(Opcodes.NEW,"java/util/Vector"); m.visitInsn(Opcodes.DUP); m.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/Vector","<init>","()V",false);
        m.visitFieldInsn(Opcodes.PUTSTATIC,owner,"staticvector","Ljava/util/Vector;"); m.visitInsn(Opcodes.RETURN); m.visitMaxs(0,0); m.visitEnd();
        m=cw.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"regClass","(I)V",null,null); m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC,owner,"staticvector","Ljava/util/Vector;"); m.visitTypeInsn(Opcodes.NEW,"java/lang/Integer"); m.visitInsn(Opcodes.DUP); m.visitVarInsn(Opcodes.ILOAD,0);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Integer","<init>","(I)V",false); m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/Vector","addElement","(Ljava/lang/Object;)V",false);
        m.visitInsn(Opcodes.RETURN); m.visitMaxs(0,0); m.visitEnd();
        for (String method:new String[]{"cinitclones","clears"}) {
            m=cw.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL,method,"(I)V",null,null); m.visitCode();
            Label end=new Label(); Label[] labels=new Label[names.size()]; for (int i=0;i<labels.length;i++) labels[i]=new Label();
            m.visitVarInsn(Opcodes.ILOAD,1); m.visitTableSwitchInsn(0,labels.length-1,end,labels);
            int i=0; for (String name:names) {
                m.visitLabel(labels[i++]);
                // Interfaces do not have restartable state or lifecycle methods.
                // Their IDs are never registered; leave their switch case empty.
                if ((new ClassReader(classes.get(name)).getAccess() & Opcodes.ACC_INTERFACE)==0)
                    m.visitMethodInsn(Opcodes.INVOKESTATIC,prefix+name,method.equals("clears") ? "clears" : "cinitclone","()V",false);
                m.visitInsn(Opcodes.RETURN);
            }
            m.visitLabel(end); m.visitInsn(Opcodes.RETURN); m.visitMaxs(0,0); m.visitEnd();
        }
        cw.visitEnd(); return cw.toByteArray();
    }

    static Map<String,byte[]> readJar(File file) throws Exception {
        Map<String,byte[]> entries = new LinkedHashMap<String,byte[]>(); JarFile jar = new JarFile(file);
        try { for (Enumeration<JarEntry> es=jar.entries(); es.hasMoreElements();) {
            JarEntry e=es.nextElement(); if (e.isDirectory()) continue;
            require(!entries.containsKey(e.getName()), "Duplicate entry"); entries.put(e.getName(),read(jar.getInputStream(e)));
        }} finally { jar.close(); } return entries;
    }
    static byte[] read(InputStream in) throws Exception {
        try { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n;
            while ((n=in.read(b))!=-1) out.write(b,0,n); return out.toByteArray();
        } finally { in.close(); }
    }
    static String hash(File file) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(read(new FileInputStream(file)));
        StringBuilder s=new StringBuilder(); for (byte b:digest) s.append(String.format("%02X",b&255)); return s.toString();
    }
    static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
}
