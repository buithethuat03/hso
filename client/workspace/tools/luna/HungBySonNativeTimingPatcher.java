import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Restores timing from a pinned native reference without replacing feature classes. */
public final class HungBySonNativeTimingPatcher implements Opcodes {
    static final String NATIVE_SHA = "8BC1B4B85E602FC3554DB9415B343003755C5D750D0922D632309BF5E9FCCCF4";
    public static void main(String[] args) throws Exception {
        check(args.length == 5, "input.jar native-reference.jar combat.properties new-output.jar new-catalog.properties");
        File input = new File(args[0]), reference = new File(args[1]), output = new File(args[3]), catalog = new File(args[4]);
        check(!output.exists() && !catalog.exists(), "Outputs must be new");
        check(hash(reference).equals(NATIVE_SHA), "Native reference hash mismatch");
        Properties p = new Properties(); InputStream config = new FileInputStream(args[2]);
        try { p.load(config); } finally { config.close(); }
        Map<String, Long> times = new HashMap<String, Long>();
        LinkedHashMap<String, byte[]> before = readJar(input, times), nativeEntries = readJar(reference, new HashMap<String, Long>());
        String castOwner = property(p, "auto.delay.owner"), requester = property(p, "request.owner");
        String castEntry = castOwner + ".class", requestEntry = requester + ".class";
        ClassNode cast = cls(before.get(castEntry)), nativeCast = cls(nativeEntries.get(castEntry));
        MethodNode clinit = method(cast, "<clinit>", "()V"), nativeClinit = method(nativeCast, "<clinit>", "()V");
        IntInsnNode currentAuto = initializer(cast, p), nativeAuto = initializer(nativeCast, p);
        check(currentAuto.operand == 500 && nativeAuto.operand == 600, "Unexpected native/current auto initializer");
        check(Arrays.equals(methodBytes(method(cast, property(p, "auto.gate.method"), "()Z")),
                methodBytes(method(nativeCast, property(p, "auto.gate.method"), "()Z"))), "Auto gate differs beyond initializer");
        currentAuto.operand = nativeAuto.operand;
        check(Arrays.equals(methodBytes(clinit), methodBytes(nativeClinit)), "Restored initializer differs from native reference");
        ClassNode requestClass = castEntry.equals(requestEntry) ? cast : cls(before.get(requestEntry));
        MethodNode request = method(requestClass, property(p, "request.method"), property(p, "request.descriptor"));
        FieldInsnNode delay = threshold(request, p);
        AbstractInsnNode amount = next(delay), add = next(amount);
        check(amount instanceof IntInsnNode && amount.getOpcode() == SIPUSH && ((IntInsnNode)amount).operand == 300
                && add.getOpcode() == IADD && next(add).getOpcode() == I2L, "Expected exact request +300ms addition");
        request.instructions.remove(amount); request.instructions.remove(add);
        MethodNode nativeRequest = method(cls(nativeEntries.get(requestEntry)), request.name, request.desc);
        check(Arrays.equals(gateBytes(request, p), gateBytes(nativeRequest, p)), "Restored request comparison differs from native reference");
        LinkedHashMap<String, byte[]> after = new LinkedHashMap<String, byte[]>(before);
        after.put(castEntry, rewrite(before.get(castEntry), clinit));
        after.put(requestEntry, rewrite(after.get(requestEntry), request));
        // Independent inverse of the two intended edits proves complete preservation of other members/code.
        Set<String> changedEntries = new LinkedHashSet<String>(Arrays.asList(castEntry, requestEntry));
        for (String name : changedEntries) {
            ClassNode normalized = cls(after.get(name));
            if (name.equals(castEntry)) initializer(normalized, p).operand = 500;
            if (name.equals(requestEntry)) {
                MethodNode normal = method(normalized, request.name, request.desc);
                InsnList restore = new InsnList(); restore.add(new IntInsnNode(SIPUSH, 300)); restore.add(new InsnNode(IADD));
                normal.instructions.insert(threshold(normal, p), restore);
                normal.maxStack = method(cls(before.get(requestEntry)), normal.name, normal.desc).maxStack;
            }
            check(Arrays.equals(canonical(normalized), canonical(cls(before.get(name)))), "Unrelated class change " + name);
        }
        Files.createDirectories(output.getAbsoluteFile().getParentFile().toPath());
        JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String, byte[]> item : after.entrySet()) {
                JarEntry entry = new JarEntry(item.getKey()); if (times.get(item.getKey()) >= 0) entry.setTime(times.get(item.getKey()));
                out.putNextEntry(entry); out.write(item.getValue()); out.closeEntry();
            }
        } finally { out.close(); }
        LinkedHashMap<String, byte[]> saved = readJar(output, new HashMap<String, Long>());
        check(saved.keySet().equals(before.keySet()), "ZIP topology changed");
        int changes = 0;
        for (String name : before.keySet()) {
            check(Arrays.equals(saved.get(name), after.get(name)), "Saved entry mismatch");
            if (!Arrays.equals(before.get(name), saved.get(name))) {
                check(name.equals(castEntry) || name.equals(requestEntry), "Scope escaped"); changes++;
                check(cls(before.get(name)).version == cls(saved.get(name)).version, "Class version changed");
            }
        }
        check(changes == changedEntries.size(), "Unexpected changed class count");
        p.setProperty("input.sha256", hash(input)); p.setProperty("native.reference.sha256", NATIVE_SHA);
        p.setProperty("output.sha256", hash(output)); p.setProperty("changed.classes", castOwner.equals(requester) ? castOwner : castOwner + "," + requester);
        p.setProperty("auto.delay.before.ms", "500"); p.setProperty("auto.delay.after.ms", String.valueOf(nativeAuto.operand));
        p.setProperty("request.extra.ms", "0"); p.setProperty("timing.mode", "original-native");
        OutputStream properties = new FileOutputStream(catalog);
        try { p.store(properties, "Exact native timing restoration; feature hooks retained"); } finally { properties.close(); }
        System.out.println("NATIVE_TIMING=PASS (auto initializer/gate and dynamic request comparison match original reference)");
        System.out.println("SCOPE=" + changes + " classes; initializer restored; extra request delay removed; inverse canonical PASS");
        System.out.println("OUTPUT_SHA256=" + hash(output));
    }
    static String property(Properties p, String key) { String v=p.getProperty(key);check(v!=null,"Missing exact symbol "+key);return v; }
    static ClassNode cls(byte[] b) { check(b!=null,"Missing class");ClassNode c=new ClassNode();new ClassReader(b).accept(c,0);return c; }
    static MethodNode method(ClassNode c,String n,String d) { for(MethodNode m:c.methods)if(m.name.equals(n)&&m.desc.equals(d))return m;throw new IllegalStateException("Missing method "+n+d); }
    static AbstractInsnNode next(AbstractInsnNode i) { do{i=i.getNext();}while(i!=null&&i.getOpcode()<0);return i; }
    static AbstractInsnNode previous(AbstractInsnNode i) { do{i=i.getPrevious();}while(i!=null&&i.getOpcode()<0);return i; }
    static boolean field(AbstractInsnNode i,int op,String owner,String name,String desc) { return i instanceof FieldInsnNode&&i.getOpcode()==op&&owner.equals(((FieldInsnNode)i).owner)&&name.equals(((FieldInsnNode)i).name)&&desc.equals(((FieldInsnNode)i).desc); }
    static IntInsnNode initializer(ClassNode c,Properties p) {
        IntInsnNode value=null;for(MethodNode m:c.methods)for(AbstractInsnNode i:m.instructions.toArray())
            if(field(i,PUTSTATIC,property(p,"auto.delay.owner"),property(p,"auto.delay.field"),"I")) {
                check(m.name.equals("<clinit>")&&value==null,"Unexpected auto writer");
                AbstractInsnNode prior=previous(i);check(prior instanceof IntInsnNode&&prior.getOpcode()==SIPUSH,"Initializer shape");value=(IntInsnNode)prior;
            }
        check(value!=null,"Missing auto initializer");return value;
    }
    static FieldInsnNode threshold(MethodNode m,Properties p) {
        FieldInsnNode result=null;for(AbstractInsnNode i:m.instructions.toArray())
            if(field(i,GETSTATIC,property(p,"request.delay.owner"),property(p,"request.delay.field"),"S")) { check(result==null,"Ambiguous request threshold");result=(FieldInsnNode)i; }
        check(result!=null,"Missing threshold");return result;
    }
    static byte[] gateBytes(MethodNode m,Properties p) {
        AbstractInsnNode threshold=threshold(m,p),store=previous(threshold),duplicate=previous(store),sub=previous(duplicate);
        AbstractInsnNode timestamp=previous(sub),start=previous(timestamp);
        check(field(start,GETSTATIC,property(p,"request.now.owner"),property(p,"request.now.field"),"J")
                &&field(timestamp,GETSTATIC,property(p,"request.timestamp.owner"),property(p,"request.timestamp.field"),"J")
                &&sub.getOpcode()==LSUB&&duplicate.getOpcode()==DUP2&&store.getOpcode()==LSTORE&&next(threshold).getOpcode()==I2L
                &&next(next(threshold)).getOpcode()==LCMP&&next(next(next(threshold))).getOpcode()==IFLE,"Native request gate shape");
        ClassWriter w=new ClassWriter(0);w.visit(V1_4,ACC_PUBLIC,"NativeComparison",null,"java/lang/Object",null);
        MethodVisitor out=w.visitMethod(ACC_PUBLIC|ACC_STATIC,"gate","()I",null,null);out.visitCode();
        for(AbstractInsnNode i=start;i!=next(next(next(threshold)));i=next(i))i.accept(out);
        out.visitInsn(IRETURN);out.visitMaxs(6,((VarInsnNode)store).var+2);out.visitEnd();w.visitEnd();return w.toByteArray();
    }
    static byte[] methodBytes(MethodNode m) {
        ClassWriter w=new ClassWriter(0);w.visit(V1_4,ACC_PUBLIC,"NativeMethod",null,"java/lang/Object",null);
        MethodVisitor v=w.visitMethod(m.access,m.name,m.desc,m.signature,m.exceptions.toArray(new String[m.exceptions.size()]));m.accept(v);w.visitEnd();return w.toByteArray();
    }
    static byte[] canonical(ClassNode c) { ClassWriter w=new ClassWriter(0);c.accept(w);return w.toByteArray(); }
    static byte[] rewrite(byte[] bytes,final MethodNode replacement) {
        ClassReader r=new ClassReader(bytes);final ClassWriter w=new ClassWriter(r,ClassWriter.COMPUTE_MAXS);
        r.accept(new ClassVisitor(ASM9,w){public MethodVisitor visitMethod(int access,String n,String d,String s,String[] e){MethodVisitor v=super.visitMethod(access,n,d,s,e);if(n.equals(replacement.name)&&d.equals(replacement.desc)){replacement.accept(v);return null;}return v;}},0);return w.toByteArray();
    }
    static LinkedHashMap<String,byte[]> readJar(File file,Map<String,Long> times)throws Exception {
        LinkedHashMap<String,byte[]> data=new LinkedHashMap<String,byte[]>();JarFile jar=new JarFile(file);
        try{for(Enumeration<JarEntry> es=jar.entries();es.hasMoreElements();){JarEntry e=es.nextElement();check(!data.containsKey(e.getName()),"Duplicate entry");InputStream in=jar.getInputStream(e);ByteArrayOutputStream out=new ByteArrayOutputStream();try{byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}finally{in.close();}data.put(e.getName(),out.toByteArray());times.put(e.getName(),e.getTime());}}finally{jar.close();}return data;
    }
    static String hash(File f)throws Exception { byte[] d=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f.toPath()));StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format("%02X",b&255));return s.toString(); }
    static void check(boolean b,String m) { if(!b)throw new IllegalStateException(m); }
}
