import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Endpoint-specific patch of the verified Hung By Son binary. */
public final class HungBySonChildLoginPatcher implements Opcodes {
    public static final String INPUT_SHA = "8B67184EE5AEBD1181931B80CD6F9EF0AC833637BE90B5EB5C5AA14289A55953";
    public static final String CHILD_SHA = "B94AF1CC26C08870BAD01DDBFAA8036CDB79A4D5055B972172CDB1266592B671";
    public static final String LOGIN_DESC = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IB)V";
    public static final String KEY = "TUOITHO_SECRET_KEY_2026";
    public static final String HOST = "221.121.0.242";
    public static final int PORT = 19129;
    public static final int SERVER_INDEX = 8;

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("input.jar child-reference.jar output.jar symbols.properties");
        File input = new File(args[0]), reference = new File(args[1]), output = new File(args[2]);
        require(INPUT_SHA.equals(hash(input)), "Unexpected Hung source hash");
        require(CHILD_SHA.equals(hash(reference)), "Unexpected CHILD reference hash");
        require(!output.exists(), "Output already exists");
        Map<String, byte[]> original = read(input);
        Map<String, byte[]> candidate = new LinkedHashMap<String, byte[]>(original);
        Map<String, ClassNode> nodes = new LinkedHashMap<String, ClassNode>();
        for (Map.Entry<String, byte[]> e : original.entrySet()) if (e.getKey().endsWith(".class")) {
            ClassNode c = new ClassNode(); new ClassReader(e.getValue()).accept(c, 0); nodes.put(c.name, c);
        }
        Properties symbols = new Properties();
        ClassNode list = null, connection = null, sender = null;
        MethodNode connect = null, login = null;
        LdcInsnNode listHost = null;
        for (ClassNode c : nodes.values()) for (MethodNode m : c.methods) {
            for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof LdcInsnNode && "Server L\u1eadu".equals(((LdcInsnNode)n).cst)) {
                require(list == null && "<clinit>".equals(m.name), "Ambiguous Server Lau list");
                require(intValue(previous(previous(previous(previous(previous(n)))))) == SERVER_INDEX, "Server Lau index changed");
                AbstractInsnNode host = next(next(next(next(n))));
                require(host instanceof LdcInsnNode && "103.178.234.202".equals(((LdcInsnNode)host).cst), "Unexpected Server Lau endpoint shape");
                list = c; listHost = (LdcInsnNode)host;
            }
            if (LOGIN_DESC.equals(m.desc) && countUtf(m) == 7) {
                require(login == null, "Ambiguous opcode-1 serializer");
                require(next(m.instructions.getFirst()).getOpcode() == ICONST_1, "Not opcode 1");
                sender = c; login = m;
            }
            if ("o".equals(m.name) && "()V".equals(m.desc) && hasConstant(m, "4.0.2")) {
                require(connect == null, "Ambiguous native connect method"); connection = c; connect = m;
            }
        }
        require(list != null && login != null && connect != null, "Required symbols not found");
        FieldInsnNode selector = null, directHost = null, directPort = null, directVersion = null;
        int connectionCalls = 0;
        for (AbstractInsnNode n : connect.instructions.toArray()) {
            if (n.getOpcode() == IF_ICMPNE && intValue(previous(n)) == SERVER_INDEX) {
                require(selector == null && previous(previous(n)) instanceof FieldInsnNode, "Ambiguous selector");
                selector = (FieldInsnNode)previous(previous(n));
            }
            if (n instanceof MethodInsnNode && "(Ljava/lang/String;I)V".equals(((MethodInsnNode)n).desc)) {
                connectionCalls++;
                if (previous(n) instanceof FieldInsnNode) {
                    directPort = (FieldInsnNode)previous(n); directHost = (FieldInsnNode)previous(directPort);
                    directVersion = (FieldInsnNode)previous(previous(previous(directHost)));
                }
            }
        }
        require(connectionCalls == 2 && selector != null && directHost != null && directVersion != null, "Connection shape changed");
        require(selector.getOpcode() == GETSTATIC && "B".equals(selector.desc), "Selector is not static byte");
        require(directPort.getOpcode() == GETSTATIC && "I".equals(directPort.desc), "Direct port shape changed");
        require(directHost.getOpcode() == GETSTATIC && "Ljava/lang/String;".equals(directHost.desc), "Direct host shape changed");
        require(directVersion.getOpcode() == GETSTATIC && "Ljava/lang/String;".equals(directVersion.desc), "Direct version shape changed");
        require(directHost.owner.equals(directPort.owner) && directHost.owner.equals(directVersion.owner), "Direct config owner mismatch");
        ClassNode config = nodes.get(directHost.owner);
        require(config != null, "Direct config class missing");
        for (MethodNode m : config.methods) for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof FieldInsnNode && n.getOpcode() == PUTSTATIC) {
            FieldInsnNode f=(FieldInsnNode)n;
            if (f.name.equals(directVersion.name) || f.name.equals(directHost.name)) require("".equals(((LdcInsnNode)previous(n)).cst), "Unexpected populated direct config");
        }
        // The original special endpoint has empty host/version and port zero.
        // Bind its actual socket call to the new endpoint, with the native 4.0.2 version.
        listHost.cst = HOST;
        connect.instructions.set(directHost, new LdcInsnNode(HOST));
        connect.instructions.set(directPort, new IntInsnNode(SIPUSH, PORT));
        connect.instructions.set(directVersion, new LdcInsnNode("4.0.2"));
        // All native/manual/direct/automatic callers converge here.
        InsnList prefix = new InsnList(); LabelNode unchanged = new LabelNode();
        prefix.add(new FieldInsnNode(GETSTATIC, selector.owner, selector.name, selector.desc));
        prefix.add(new IntInsnNode(BIPUSH, SERVER_INDEX));
        prefix.add(new JumpInsnNode(IF_ICMPNE, unchanged));
        for (int local = 4; local <= 6; local++) {
            prefix.add(new LdcInsnNode(KEY)); prefix.add(new VarInsnNode(ASTORE, local));
        }
        prefix.add(unchanged); login.instructions.insert(prefix);
        String[] changed = {list.name, connection.name, sender.name};
        for (String name : changed) {
            ClassWriter writer = new ClassWriter(new ClassReader(original.get(name+".class")), ClassWriter.COMPUTE_MAXS);
            nodes.get(name).accept(writer); candidate.put(name+".class", writer.toByteArray());
        }
        int callers=0;
        for (ClassNode c : nodes.values()) for (MethodNode m : c.methods) for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof MethodInsnNode) {
            MethodInsnNode call=(MethodInsnNode)n;
            if (call.owner.equals(sender.name) && call.name.equals(login.name) && call.desc.equals(login.desc)) {
                symbols.setProperty("login.call."+callers, c.name+"."+m.name+m.desc); callers++;
            }
        }
        require(callers == 4, "Unexpected login caller count: "+callers);
        symbols.setProperty("input.sha256", INPUT_SHA); symbols.setProperty("reference.sha256", CHILD_SHA);
        symbols.setProperty("server.label", "Server L\u1eadu"); symbols.setProperty("server.index", "8");
        symbols.setProperty("server.host", HOST); symbols.setProperty("server.port", "19129");
        symbols.setProperty("server.list.owner", list.name);
        symbols.setProperty("connection.owner", connection.name); symbols.setProperty("connection.method", connect.name);
        symbols.setProperty("selector.owner", selector.owner); symbols.setProperty("selector.name", selector.name); symbols.setProperty("selector.desc", selector.desc);
        symbols.setProperty("login.owner", sender.name); symbols.setProperty("login.method", login.name); symbols.setProperty("login.desc", login.desc);
        symbols.setProperty("login.key", KEY); symbols.setProperty("login.version", "4.0.2"); symbols.setProperty("login.calls", String.valueOf(callers));
        symbols.setProperty("changed.classes", changed[0]+","+changed[1]+","+changed[2]);
        write(input, output, candidate); symbols.setProperty("output.sha256", hash(output));
        Writer report = new OutputStreamWriter(new FileOutputStream(args[3]), "UTF-8");
        try { symbols.store(report, "Hung By Son -> HSO CHILD, endpoint-specific login"); } finally { report.close(); }
        System.out.println("PATCH=PASS; Server Lau -> "+HOST+":"+PORT+"; guarded login keys; 3 changed classes; "+callers+" login call sites");
        System.out.println("SHA256="+hash(output));
    }
    public static AbstractInsnNode previous(AbstractInsnNode n) { do { n=n.getPrevious(); } while(n!=null && n.getOpcode()<0); return n; }
    public static AbstractInsnNode next(AbstractInsnNode n) { do { n=n.getNext(); } while(n!=null && n.getOpcode()<0); return n; }
    public static int intValue(AbstractInsnNode n) {
        if(n instanceof IntInsnNode)return ((IntInsnNode)n).operand;
        return n!=null && n.getOpcode()>=ICONST_M1 && n.getOpcode()<=ICONST_5 ? n.getOpcode()-ICONST_0 : Integer.MIN_VALUE;
    }
    public static int countUtf(MethodNode m) { int count=0; for(AbstractInsnNode n:m.instructions.toArray()) if(n instanceof MethodInsnNode) { MethodInsnNode c=(MethodInsnNode)n; if(c.owner.equals("java/io/DataOutputStream")&&c.name.equals("writeUTF"))count++; } return count; }
    public static boolean hasConstant(MethodNode m,Object value) { for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof LdcInsnNode&&value.equals(((LdcInsnNode)n).cst))return true;return false; }
    public static Map<String,byte[]> read(File file) throws Exception {
        Map<String,byte[]> result=new LinkedHashMap<String,byte[]>();JarFile jar=new JarFile(file);
        try { for(Enumeration<JarEntry> e=jar.entries();e.hasMoreElements();) { JarEntry entry=e.nextElement();InputStream in=jar.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream();try { byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n); }finally{in.close();}require(result.put(entry.getName(),out.toByteArray())==null,"Duplicate ZIP entry"); } }finally{jar.close();}return result;
    }
    private static void write(File source,File output,Map<String,byte[]> data)throws Exception {
        JarFile jar=new JarFile(source);ZipOutputStream out=new ZipOutputStream(new FileOutputStream(output));
        try { for(Enumeration<JarEntry> e=jar.entries();e.hasMoreElements();) { JarEntry old=e.nextElement();ZipEntry entry=new ZipEntry(old.getName());entry.setTime(old.getTime());if(old.getComment()!=null)entry.setComment(old.getComment());out.putNextEntry(entry);out.write(data.get(old.getName()));out.closeEntry(); } }finally{out.close();jar.close();}
    }
    public static String hash(File file)throws Exception {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02X",Integer.valueOf(b&255)));return out.toString();}
    public static void require(boolean okay,String message){if(!okay)throw new IllegalStateException(message);}
}
