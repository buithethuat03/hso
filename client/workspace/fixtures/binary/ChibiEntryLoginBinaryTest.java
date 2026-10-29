import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Runs the current Hso authentication slice and Luna's native login serializer in isolation. */
public final class ChibiEntryLoginBinaryTest implements Opcodes {
    private static final String TEST = "ChibiEntryLoginBinaryTest";
    private static final String AUTH = "luna/generated/ChibiLoginAuth";
    private static final String LOGIN_DESC =
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
            + "Ljava/lang/String;Ljava/lang/String;IB)V";
    private static final String AUTH_DESC = "(Ljava/lang/String;[B)Ljava/lang/String;";
    private static final String DONOR_SHA =
            "C69F9C6E9FF3AB998D21C9D78919859E5364C4D28B628723DC74DA48EFDC68C6";
    private static ByteArrayOutputStream bytes;
    private static DataOutputStream out;
    public static byte[] sessionKey;
    private static boolean connected;
    private static int opcode;
    private static int checks;
    public static int deviceInt = 6;
    public static boolean deviceFlag = true;
    public static int deviceByte = 2;
    public static int deviceShort = 0x1234;
    public static String deviceInfo = "device-info";

    public static void begin(Object receiver, byte value) {
        opcode = value;
        bytes = new ByteArrayOutputStream();
        out = new DataOutputStream(bytes);
    }

    public static DataOutputStream stream() { return out; }
    public static Object session() { return new Object(); }
    public static boolean connected(Object ignored) { return connected; }

    private static final class ProbeLoader extends ClassLoader {
        private final byte[] helper;
        ProbeLoader(byte[] value) { super(ChibiEntryLoginBinaryTest.class.getClassLoader()); helper = value; }
        Class<?> defineProbe(String name, byte[] value) { return defineClass(name, value, 0, value.length); }
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if ("luna.generated.ChibiLoginAuth".equals(name) && helper != null)
                return defineClass(name, helper, 0, helper.length);
            return super.findClass(name);
        }
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 3 || args.length == 4,
                "Usage: ChibiEntryLoginBinaryTest Hso.jar candidate.jar candidate.properties [baseline.jar]");
        File donorFile = new File(args[0]);
        Map<String, byte[]> donor = read(donorFile);
        Map<String, byte[]> candidate = read(new File(args[1]));
        Properties p = load(args[2]);
        String loginOwner = required(p, "login.owner");
        String loginName = required(p, "login.method");
        String loginDesc = p.getProperty("login.desc", LOGIN_DESC);
        check(LOGIN_DESC.equals(loginDesc), "Unexpected login descriptor: " + loginDesc);
        check(DONOR_SHA.equals(sha256(donorFile)), "Current Hso donor SHA-256 changed");

        byte[] donorHelper = remapDonorHelper(donor.get("IIIl.class"));
        byte[] candidateHelper = candidate.get(AUTH + ".class");
        check(candidateHelper != null, "Generated auth helper is missing");
        Class<?> old = probe(donor, "IIlI", "II", false, null, donorHelper, "ChibiEntryDonorProbe");
        Class<?> next = probe(candidate, loginOwner, loginName, true, p, candidateHelper, "ChibiEntryProbe");

        String[] users = {"fixture-user", "fixture-user", "Unicode-\u7528\u6237-\u96ea",
                "fixture-user", "fixture-user", "fixture-user", "alternate-user"};
        String[] passes = {"fixture-pass", "fixture-pass", "\u043f\u0430\u0440\u043e\u043b\u044c-\u0394",
                "fixture-pass", "fixture-pass", "fixture-pass", "unicode-\u96ea"};
        byte[][] keys = {new byte[] {1, 2, 3, 4}, new byte[] {9, 8, 7, 6, 5},
                new byte[] {1, 2, 3, 4}, null, null, new byte[0], new byte[] {(byte)0xff, 0, 7}};
        boolean[] connectedStates = {true, true, true, false, true, true, false};
        int[] selectors = {-1, 42, 11, -1, 0, 22, -128};
        String[] goldens = {"5086dd461938ea14", "b5596b21f7e7e945", "8790e52c7f2832a8",
                "1e6a57656a643d18", "1e6a57656a643d18", "1e6a57656a643d18", null};
        byte[][] packets = new byte[keys.length][];
        long boundedWaitNanos = 1000000000L;
        for (int i = 0; i < keys.length; i++) {
            sessionKey = keys[i];
            connected = connectedStates[i];
            setDevice(i);
            String donorHash = authHash(old, users[i], keys[i]);
            String candidateHash = authHash(next, users[i], keys[i]);
            if (goldens[i] != null) check(goldens[i].equals(donorHash), "Donor signature golden changed in case " + i);
            check(donorHash.equals(candidateHash), "Generated helper differs from current Hso in case " + i);

            long start = System.nanoTime();
            byte[] expected = packet(old, users[i], passes[i], "9.9.9", "0", "0", "0", selectors[i], (byte)0);
            long donorNanos = System.nanoTime() - start;
            start = System.nanoTime();
            byte[] actual = packet(next, users[i], passes[i], "2.5.8", "caller-four", "caller-five",
                    "caller-six", selectors[i], (byte)0);
            long candidateNanos = System.nanoTime() - start;
            check(Arrays.equals(expected, actual), "Full opcode-1 payload differs in case " + i);
            if (keys[i] == null && connectedStates[i])
                check(donorNanos < boundedWaitNanos && candidateNanos < boundedWaitNanos,
                        "Connected null-key wait exceeded one second");
            verifyPayload(actual, users[i], passes[i], selectors[i], (byte)0, "0|" + donorHash);
            check(opcode == 1, "Login opcode changed in case " + i);
            packets[i] = actual;
        }
        check(!Arrays.equals(packets[0], packets[1]), "Distinct session keys produced identical payloads");
        check(goldens[3].equals(goldens[5]), "Null and empty keys diverged from current donor semantics");

        boolean baselineChecked = false;
        if (args.length == 4) {
            Map<String, byte[]> baseline = read(new File(args[3]));
            MethodRef baselineLogin = findLogin(baseline, loginOwner, loginName, loginDesc);
            Class<?> baselineProbe = probe(baseline, baselineLogin.owner, baselineLogin.method,
                    true, p, null, "ChibiEntryBaselineProbe");
            sessionKey = new byte[] {1, 2, 3, 4};
            connected = true;
            setDevice(0);
            byte[] baselinePacket = packet(baselineProbe, users[0], passes[0], "9.9.9", "0", "0", "0",
                    selectors[0], (byte)0);
            String baselineSignature = sixthUtf(baselinePacket);
            String transplantedSignature = "0|" + authHash(old, users[0], keys[0]);
            check(!transplantedSignature.equals(baselineSignature),
                    "Baseline static sixth-UTF signature unexpectedly matches current Hso signature");
            check(Arrays.equals(opcode13Fingerprints(baseline), opcode13Fingerprints(candidate)),
                    "Native opcode-13 sender method body changed from baseline");
            baselineChecked = true;
        }
        System.out.println("CHIBI_ENTRY_LOGIN=PASS; cases=" + keys.length
                + "; full opcode-1 payload parity; current donor signature goldens; null-key wait bounded"
                + (baselineChecked ? "; baseline signature divergence; opcode-13 body parity" : "; baseline comparison omitted"));
    }

    private static Class<?> probe(Map<String, byte[]> jar, String owner, String methodName,
            boolean candidate, Properties p, byte[] helper, String name) throws Exception {
        ClassNode source = node(jar.get(owner + ".class"));
        MethodNode m = method(source, methodName, LOGIN_DESC);
        List<MethodInsnNode> writes = utfWrites(m);
        check(writes.size() == 8, "Expected eight UTF writes in " + name + ", got " + writes.size());
        MethodNode copy = new MethodNode(ACC_PUBLIC, "login", LOGIN_DESC, null, null);
        Map<LabelNode, LabelNode> labels = new IdentityHashMap<LabelNode, LabelNode>();
        for (AbstractInsnNode q : m.instructions.toArray())
            if (q instanceof LabelNode) labels.put((LabelNode)q, new LabelNode());
        Set<LabelNode> included = Collections.newSetFromMap(new IdentityHashMap<LabelNode, Boolean>());
        int utf = 0;
        for (AbstractInsnNode n : m.instructions.toArray()) {
            AbstractInsnNode x;
            if (n instanceof FrameNode) continue;
            if (n instanceof LabelNode) included.add((LabelNode)n);
            if (n instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode)n;
                if (f.getOpcode() == GETFIELD) {
                    if (f.desc.equals("[B")) {
                        copy.instructions.add(new InsnNode(POP));
                        copy.instructions.add(new FieldInsnNode(GETSTATIC, TEST, "sessionKey", "[B"));
                    } else if (f.desc.equals("Ljava/io/DataOutputStream;")) {
                        copy.instructions.add(new InsnNode(POP));
                        copy.instructions.add(new MethodInsnNode(INVOKESTATIC, TEST, "stream",
                                "()Ljava/io/DataOutputStream;", false));
                    } else {
                        copy.instructions.add(new InsnNode(POP));
                        if (candidate && (f.desc.charAt(0) == 'L' || f.desc.charAt(0) == '['))
                            copy.instructions.add(new InsnNode(ACONST_NULL));
                    }
                } else if (f.getOpcode() == GETSTATIC && candidate && p != null
                        && f.owner.equals(p.getProperty("selector.owner"))
                        && f.name.equals(p.getProperty("selector.name"))) {
                    copy.instructions.add(new FieldInsnNode(GETSTATIC, name, "selected", "B"));
                } else if (f.getOpcode() == GETSTATIC) {
                    pushDevice(copy.instructions, f.desc);
                } else throw new IllegalStateException("Unexpected field instruction " + f.owner + "." + f.name + f.desc);
            } else if (n instanceof MethodInsnNode) {
                MethodInsnNode c = (MethodInsnNode)n;
                if (c.owner.equals("java/io/DataOutputStream") && c.name.equals("writeUTF")) {
                    x = n.clone(labels);
                    copy.instructions.add(x);
                    if (++utf == 8) break;
                } else if (c.desc.equals("()Ljava/io/DataOutputStream;")) {
                    copy.instructions.add(new MethodInsnNode(INVOKESTATIC, TEST, "stream",
                            "()Ljava/io/DataOutputStream;", false));
                } else if (c.desc.equals("(B)V") && !c.owner.startsWith("java/")) {
                    copy.instructions.add(new MethodInsnNode(INVOKESTATIC, TEST, "begin",
                            "(Ljava/lang/Object;B)V", false));
                } else if ((c.owner.equals("IIl") && c.desc.equals("()LIIl;"))
                        || (candidate && p != null && c.owner.equals(p.getProperty("session.owner"))
                        && c.name.equals(p.getProperty("session.getter")))) {
                    copy.instructions.add(new MethodInsnNode(INVOKESTATIC, TEST, "session", "()Ljava/lang/Object;", false));
                } else if ((c.owner.equals("IIl") && c.desc.equals("()Z"))
                        || (candidate && p != null && c.owner.equals(p.getProperty("session.owner"))
                        && c.name.equals(p.getProperty("session.connected")))) {
                    copy.instructions.add(new MethodInsnNode(INVOKESTATIC, TEST, "connected", "(Ljava/lang/Object;)Z", false));
                } else if ((c.owner.equals("IIIl") && c.desc.equals(AUTH_DESC))
                        || (candidate && c.owner.equals(AUTH) && c.desc.equals(AUTH_DESC))) {
                    copy.instructions.add(new MethodInsnNode(INVOKESTATIC, AUTH, "II", AUTH_DESC, false));
                } else if (c.owner.startsWith("java/")) {
                    copy.instructions.add(n.clone(labels));
                } else {
                    throw new IllegalStateException("Unmapped method call " + c.owner + "." + c.name + c.desc);
                }
            } else {
                copy.instructions.add(n.clone(labels));
            }
        }
        check(utf == 8, "Truncated before final UTF in " + name);
        for (TryCatchBlockNode t : m.tryCatchBlocks)
            if (included.contains(t.start) && included.contains(t.end) && included.contains(t.handler))
                copy.tryCatchBlocks.add(new TryCatchBlockNode(label(labels, t.start), label(labels, t.end),
                        label(labels, t.handler), t.type));
        copy.instructions.add(new InsnNode(RETURN));
        ClassNode c = new ClassNode(); c.version = V1_3; c.access = ACC_PUBLIC; c.name = name; c.superName = "java/lang/Object";
        MethodNode ctor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new InsnNode(RETURN)); c.methods.add(ctor);
        c.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, "selected", "B", null, null));
        c.methods.add(copy);
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(w);
        return new ProbeLoader(helper).defineProbe(name, w.toByteArray());
    }

    private static LabelNode label(Map<LabelNode, LabelNode> labels, LabelNode key) {
        LabelNode n = labels.get(key);
        if (n == null) { n = new LabelNode(); labels.put(key, n); }
        return n;
    }

    private static void pushDevice(InsnList b, String desc) {
        if (desc.equals("Ljava/lang/String;")) b.add(new FieldInsnNode(GETSTATIC, TEST, "deviceInfo", desc));
        else if (desc.equals("Z")) b.add(new FieldInsnNode(GETSTATIC, TEST, "deviceFlag", desc));
        else if (desc.equals("B")) b.add(new FieldInsnNode(GETSTATIC, TEST, "deviceByte", "I"));
        else if (desc.equals("S")) b.add(new FieldInsnNode(GETSTATIC, TEST, "deviceShort", "I"));
        else if (desc.equals("I")) b.add(new FieldInsnNode(GETSTATIC, TEST, "deviceInt", "I"));
        else if (desc.equals("J")) b.add(new LdcInsnNode(Long.valueOf(0L)));
        else throw new IllegalStateException("Unexpected static device type " + desc);
    }

    private static void setDevice(int i) {
        if (i == 1) { deviceInt = 13; deviceFlag = false; deviceByte = -3; deviceShort = -123; deviceInfo = "runtime-\u5c3e"; }
        else if (i == 2) { deviceInt = 4; deviceFlag = true; deviceByte = 7; deviceShort = 321; deviceInfo = "unicode-\u96ea"; }
        else if (i == 5) { deviceInt = 21; deviceFlag = false; deviceByte = -9; deviceShort = 0x4567; deviceInfo = "tail-variation"; }
        else { deviceInt = 6; deviceFlag = true; deviceByte = 2; deviceShort = 0x1234; deviceInfo = "device-info"; }
    }

    private static String authHash(Class<?> probe, String user, byte[] key) throws Exception {
        Class<?> auth = Class.forName("luna.generated.ChibiLoginAuth", true, probe.getClassLoader());
        return (String)auth.getMethod("II", String.class, byte[].class).invoke(null, user, key);
    }

    private static byte[] remapDonorHelper(byte[] source) {
        ClassNode c = node(source); c.name = AUTH;
        for (MethodNode m : c.methods) for (AbstractInsnNode n : m.instructions.toArray()) {
            if (n instanceof FieldInsnNode && ((FieldInsnNode)n).owner.equals("IIIl")) ((FieldInsnNode)n).owner = AUTH;
            else if (n instanceof MethodInsnNode && ((MethodInsnNode)n).owner.equals("IIIl")) ((MethodInsnNode)n).owner = AUTH;
        }
        ClassWriter w = new ClassWriter(0); c.accept(w); return w.toByteArray();
    }

    private static byte[] packet(Class<?> probe, String user, String pass, String version,
            String ext4, String ext5, String ext6, int selector, byte server) throws Exception {
        probe.getField("selected").setByte(null, (byte)0);
        Object receiver = probe.getConstructor().newInstance(); bytes = null; out = null;
        probe.getMethod("login", String.class, String.class, String.class, String.class,
                String.class, String.class, int.class, byte.class)
                .invoke(receiver, user, pass, version, ext4, ext5, ext6, selector, server);
        out.flush(); return bytes.toByteArray();
    }

    private static void verifyPayload(byte[] data, String user, String pass, int selector, byte server,
            String token) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        check(user.equals(in.readUTF()), "Username order"); check(pass.equals(in.readUTF()), "Password order");
        check("9.9.9".equals(in.readUTF()), "Native version"); check("0".equals(in.readUTF()), "Extension 4");
        check("0".equals(in.readUTF()), "Extension 5"); check(token.equals(in.readUTF()), "Current signature golden/position");
        check(in.readUnsignedByte() == (deviceInt & 255), "First login flag");
        check(in.readUnsignedByte() == 0, "Second login flag"); check(in.readInt() == selector, "Selector int");
        check(in.readUnsignedByte() == (server & 255), "Server selector byte");
        check(in.readUnsignedByte() == (deviceFlag ? 1 : 0), "Device flag 1");
        check(in.readUnsignedByte() == (deviceByte & 255), "Device byte 2");
        check(in.readUnsignedByte() == (deviceByte & 255), "Device byte 3");
        check(in.readUnsignedByte() == (deviceFlag ? 1 : 0), "Device flag 4");
        check(in.readShort() == deviceShort, "Device short"); check(deviceInfo.equals(in.readUTF()), "Device info UTF");
        check("LOCK".equals(in.readUTF()), "Final UTF"); check(in.available() == 0, "Trailing bytes");
    }

    private static String sixthUtf(byte[] payload) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        String value = null;
        for (int i = 0; i < 6; i++) value = in.readUTF();
        return value;
    }

    private static MethodRef findLogin(Map<String, byte[]> jar, String owner, String name, String desc) {
        byte[] bytes = jar.get(owner + ".class");
        if (bytes != null) {
            ClassNode c = node(bytes);
            for (MethodNode m : c.methods) if (name.equals(m.name) && desc.equals(m.desc))
                return new MethodRef(c.name, m.name);
        }
        MethodRef found = null;
        for (Map.Entry<String, byte[]> e : jar.entrySet()) if (e.getKey().endsWith(".class")) {
            ClassNode c = node(e.getValue());
            for (MethodNode m : c.methods) if (desc.equals(m.desc) && utfWrites(m).size() == 8) {
                check(found == null, "Ambiguous baseline login serializer"); found = new MethodRef(c.name, m.name);
            }
        }
        check(found != null, "Baseline login serializer missing"); return found;
    }

    private static String[] opcode13Fingerprints(Map<String, byte[]> jar) {
        List<String> found = new ArrayList<String>();
        for (Map.Entry<String, byte[]> e : jar.entrySet()) if (e.getKey().endsWith(".class")) {
            ClassNode c = node(e.getValue());
            for (MethodNode m : c.methods) if (startsOpcode13(m))
                found.add(c.name + "." + m.name + m.desc + "=" + fingerprint(m));
        }
        Collections.sort(found);
        check(!found.isEmpty(), "Opcode-13 native sender was not found for baseline parity");
        return found.toArray(new String[found.size()]);
    }

    private static boolean startsOpcode13(MethodNode m) {
        for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)n;
            if (!call.owner.startsWith("java/") && "(B)V".equals(call.desc) && intValue(previousReal(n)) == 13)
                return true;
        }
        return false;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode n) {
        for (AbstractInsnNode p = n.getPrevious(); p != null; p = p.getPrevious()) if (p.getOpcode() >= 0) return p;
        return null;
    }

    private static int intValue(AbstractInsnNode n) {
        if (n == null) return Integer.MIN_VALUE;
        int op = n.getOpcode();
        if (op == ICONST_M1) return -1;
        if (op >= ICONST_0 && op <= ICONST_5) return op - ICONST_0;
        if (n instanceof IntInsnNode) return ((IntInsnNode)n).operand;
        if (n instanceof LdcInsnNode && ((LdcInsnNode)n).cst instanceof Integer) return ((Integer)((LdcInsnNode)n).cst).intValue();
        return Integer.MIN_VALUE;
    }

    private static String fingerprint(MethodNode m) {
        AbstractInsnNode[] a = m.instructions.toArray();
        IdentityHashMap<LabelNode, Integer> labels = new IdentityHashMap<LabelNode, Integer>();
        for (int i = 0; i < a.length; i++) if (a[i] instanceof LabelNode) labels.put((LabelNode)a[i], Integer.valueOf(i));
        StringBuffer b = new StringBuffer();
        for (int i = 0; i < a.length; i++) {
            AbstractInsnNode n = a[i]; int op = n.getOpcode(); if (op < 0) continue;
            b.append(op).append(':');
            if (n instanceof IntInsnNode) b.append(((IntInsnNode)n).operand);
            else if (n instanceof VarInsnNode) b.append(((VarInsnNode)n).var);
            else if (n instanceof TypeInsnNode) b.append(((TypeInsnNode)n).desc);
            else if (n instanceof FieldInsnNode) { FieldInsnNode f=(FieldInsnNode)n; b.append(f.owner).append('.').append(f.name).append(f.desc); }
            else if (n instanceof MethodInsnNode) { MethodInsnNode c=(MethodInsnNode)n; b.append(c.owner).append('.').append(c.name).append(c.desc).append(c.itf); }
            else if (n instanceof JumpInsnNode) b.append(target(labels, ((JumpInsnNode)n).label));
            else if (n instanceof LdcInsnNode) b.append(String.valueOf(((LdcInsnNode)n).cst));
            else if (n instanceof IincInsnNode) { IincInsnNode x=(IincInsnNode)n; b.append(x.var).append(',').append(x.incr); }
            else if (n instanceof TableSwitchInsnNode) { TableSwitchInsnNode x=(TableSwitchInsnNode)n; b.append(x.min).append(',').append(x.max).append(',').append(target(labels,x.dflt)); for(LabelNode l:x.labels)b.append(',').append(target(labels,l)); }
            else if (n instanceof LookupSwitchInsnNode) { LookupSwitchInsnNode x=(LookupSwitchInsnNode)n; b.append(target(labels,x.dflt)); for(int j=0;j<x.keys.size();j++)b.append(',').append(x.keys.get(j)).append(':').append(target(labels,x.labels.get(j))); }
            else if (n instanceof MultiANewArrayInsnNode) { MultiANewArrayInsnNode x=(MultiANewArrayInsnNode)n; b.append(x.desc).append(',').append(x.dims); }
            b.append(';');
        }
        for (TryCatchBlockNode t : m.tryCatchBlocks)
            b.append("T:").append(target(labels,t.start)).append(',').append(target(labels,t.end)).append(',')
                    .append(target(labels,t.handler)).append(',').append(t.type).append(';');
        return b.toString();
    }

    private static Integer target(IdentityHashMap<LabelNode, Integer> labels, LabelNode label) {
        Integer value = labels.get(label); return value == null ? Integer.valueOf(-1) : value;
    }

    private static List<MethodInsnNode> utfWrites(MethodNode m) {
        List<MethodInsnNode> out = new ArrayList<MethodInsnNode>();
        for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof MethodInsnNode) {
            MethodInsnNode c = (MethodInsnNode)n;
            if (c.owner.equals("java/io/DataOutputStream") && c.name.equals("writeUTF")) out.add(c);
        }
        return out;
    }

    private static final class MethodRef {
        final String owner; final String method;
        MethodRef(String o, String m) { owner = o; method = m; }
    }

    private static ClassNode node(byte[] b) {
        if (b == null) throw new IllegalStateException("Class bytes missing");
        ClassNode c = new ClassNode(); new ClassReader(b).accept(c, 0); return c;
    }

    private static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new IllegalStateException("Method missing " + c.name + "." + name + desc);
    }

    private static Map<String, byte[]> read(File f) throws Exception {
        Map<String, byte[]> m = new LinkedHashMap<String, byte[]>(); JarFile j = new JarFile(f);
        try {
            for (Enumeration<JarEntry> e = j.entries(); e.hasMoreElements();) {
                JarEntry x = e.nextElement(); InputStream in = j.getInputStream(x); ByteArrayOutputStream b = new ByteArrayOutputStream();
                try { byte[] q = new byte[8192]; for (int n; (n = in.read(q)) != -1;) b.write(q, 0, n); }
                finally { in.close(); }
                m.put(x.getName(), b.toByteArray());
            }
        } finally { j.close(); }
        return m;
    }

    private static Properties load(String path) throws Exception {
        Properties p = new Properties(); Reader r = new InputStreamReader(new FileInputStream(path), "UTF-8");
        try { p.load(r); } finally { r.close(); } return p;
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key); check(value != null && value.length() != 0, "Missing property " + key); return value;
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256"); InputStream in = new FileInputStream(f);
        try { byte[] b = new byte[8192]; for (int n; (n = in.read(b)) != -1;) md.update(b, 0, n); }
        finally { in.close(); }
        StringBuffer s = new StringBuffer(); byte[] h = md.digest();
        for (int i = 0; i < h.length; i++) { int v = h[i] & 255; if (v < 16) s.append('0'); s.append(Integer.toHexString(v)); }
        return s.toString().toUpperCase(Locale.US);
    }

    private static void check(boolean ok, String message) {
        checks++; if (!ok) throw new IllegalStateException(message);
    }
}
