import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.util.MemoryRecordStoreManager;

/** Verifies the QCache draw boundary follows the real native MOD toggle. */
public final class HungBySonInvestigateHudBinaryTest implements Opcodes {
    private static final String BRIDGE = "luna/generated/HungMonsterCacheAccess";
    private static final String STORAGE = "luna/generated/InvestigateStorage";
    private static final String HOST = "HungBySonInvestigateHudBinaryTest";

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode c = new ClassNode();
        new ClassReader(bytes).accept(c, 0);
        return c;
    }

    private static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods)
            if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new IllegalStateException("Missing method " + c.name + "." + name + desc);
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 3, "candidate.jar catalog.properties microemulator.jar");
        File jar = new File(args[0]);
        Properties p = new Properties();
        InputStream in = new FileInputStream(args[1]);
        try { p.load(in); } finally { in.close(); }
        require(jar.isFile() && new File(args[2]).isFile(), "Candidate and MicroEmulator exist");
        require(hash(jar).equals(p.getProperty("output.sha256")), "Candidate hash matches catalog");

        String owner = required(p, "hud.info.owner");
        String flag = required(p, "hud.info.field");
        String bridgeOwner = required(p, "bridge.owner");
        Map<String, byte[]> entries = read(jar);
        ClassNode nativeModule = node(entries.get(owner + ".class"));
        FieldNode info = null;
        for (FieldNode f : nativeModule.fields) if (f.name.equals(flag)) info = f;
        require(info != null && "Z".equals(info.desc)
                && (info.access & (ACC_PUBLIC | ACC_STATIC)) == (ACC_PUBLIC | ACC_STATIC),
                "Catalog points to the public static native info boolean");
        MethodNode clinit = method(nativeModule, "<clinit>", "()V");
        require(initializesTrue(clinit, owner, flag), "Native initializer defaults info to ON");
        verifyNativeAction(nativeModule, owner, flag);
        verifyBridgeGate(node(entries.get(bridgeOwner + ".class")), owner, flag);

        Common emulator = new Common(null);
        MemoryRecordStoreManager records = new MemoryRecordStoreManager();
        records.init(emulator);
        emulator.setRecordStoreManager(records);
        MIDletBridge.setMicroEmulator(emulator);

        ProbeLoader loader = new ProbeLoader(entries, owner, flag);
        try {
            Class<?> moduleType = Class.forName(owner.replace('/', '.'), true, loader);
            Field enabled = moduleType.getField(flag);
            require(enabled.getBoolean(null), "Isolated native class starts with the verified ON default");
            Object module = moduleType.getConstructor().newInstance();
            Method onAction = moduleType.getMethod("onAction", Integer.TYPE, Integer.TYPE);

            Class<?> bridgeType = Class.forName(bridgeOwner.replace('/', '.'), true, loader);
            Object bridge = bridgeType.getField("INSTANCE").get(null);
            Method hud = bridgeType.getMethod("hud", Object.class, String.class);
            Class<?> storageType = Class.forName(STORAGE.replace('/', '.'), true, loader);
            Method reset = storageType.getMethod("reset");
            Field calls = storageType.getField("calls");
            Field lastGraphics = storageType.getField("lastGraphics");
            Field lastText = storageType.getField("lastText");
            Object graphics = new Object();
            String status = "QCache S:12 R:3";

            reset.invoke(null);
            hud.invoke(bridge, graphics, status);
            require(calls.getInt(null) == 1 && lastGraphics.get(null) == graphics
                    && status.equals(lastText.get(null)), "ON sends QCache to the drawing boundary");

            onAction.invoke(module, Integer.valueOf(-77), Integer.valueOf(0));
            require(!enabled.getBoolean(null), "Actual native MOD handler turns info OFF");
            hud.invoke(bridge, graphics, status);
            require(calls.getInt(null) == 1, "OFF suppresses the QCache drawing boundary");

            onAction.invoke(module, Integer.valueOf(-77), Integer.valueOf(0));
            require(enabled.getBoolean(null), "Actual native MOD handler turns info back ON");
            hud.invoke(bridge, graphics, status);
            require(calls.getInt(null) == 2 && status.equals(lastText.get(null)),
                    "ON again restores QCache drawing");
        } finally { loader.close(); }
        System.out.println("HungBySonInvestigateHudBinaryTest PASS");
    }

    private static boolean initializesTrue(MethodNode m, String owner, String field) {
        AbstractInsnNode[] a = m.instructions.toArray();
        for (int i = 0; i + 1 < a.length; i++)
            if (a[i].getOpcode() == ICONST_1 && a[i + 1] instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode)a[i + 1];
                if (f.getOpcode() == PUTSTATIC && f.owner.equals(owner)
                        && f.name.equals(field) && "Z".equals(f.desc)) return true;
            }
        return false;
    }

    private static void verifyNativeAction(ClassNode c, String owner, String field) {
        MethodNode m = method(c, "onAction", "(II)Z");
        boolean action = false;
        int reads = 0, writes = 0;
        for (AbstractInsnNode i : m.instructions.toArray()) {
            if (i.getOpcode() == BIPUSH
                    && ((org.objectweb.asm.tree.IntInsnNode)i).operand == -77) action = true;
            if (i instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode)i;
                if (f.owner.equals(owner) && f.name.equals(field) && "Z".equals(f.desc)) {
                    if (f.getOpcode() == GETSTATIC) reads++;
                    if (f.getOpcode() == PUTSTATIC) writes++;
                }
            }
        }
        require(action && reads >= 2 && writes == 1,
                "Native -77 menu action toggles the catalogued boolean");
    }

    private static void verifyBridgeGate(ClassNode c, String owner, String field) {
        MethodNode m = method(c, "hud", "(Ljava/lang/Object;Ljava/lang/String;)V");
        AbstractInsnNode[] a = m.instructions.toArray();
        int read = -1, branch = -1, draw = -1;
        for (int i = 0; i < a.length; i++) {
            if (a[i] instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode)a[i];
                if (f.getOpcode() == GETSTATIC && f.owner.equals(owner)
                        && f.name.equals(field) && "Z".equals(f.desc)) read = i;
            }
            if (a[i] instanceof JumpInsnNode && a[i].getOpcode() == IFEQ) branch = i;
            if (a[i] instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)a[i];
                if (call.getOpcode() == INVOKESTATIC && call.owner.equals(STORAGE)
                        && call.name.equals("hud") && call.desc.equals("(Ljava/lang/Object;Ljava/lang/String;)V")) draw = i;
            }
        }
        require(read >= 0 && branch > read && draw > branch,
                "Bridge tests native flag before the Storage HUD call");
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        require(value != null && value.length() > 0, "Missing catalog key " + key);
        return value;
    }

    private static String hash(File file) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(file.toPath()));
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) s.append(String.format("%02X", Integer.valueOf(b & 255)));
        return s.toString();
    }

    private static Map<String, byte[]> read(File file) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<String, byte[]>();
        JarFile jar = new JarFile(file);
        try {
            Enumeration<JarEntry> es = jar.entries();
            while (es.hasMoreElements()) {
                JarEntry e = es.nextElement();
                InputStream in = jar.getInputStream(e);
                try {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] block = new byte[4096];
                    for (int n; (n = in.read(block)) != -1;) out.write(block, 0, n);
                    result.put(e.getName(), out.toByteArray());
                } finally { in.close(); }
            }
        } finally { jar.close(); }
        return result;
    }

    private static byte[] storageSpy() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(V1_7, ACC_PUBLIC | ACC_FINAL, STORAGE, null, "java/lang/Object", null);
        w.visitField(ACC_PUBLIC | ACC_STATIC, "calls", "I", null, null).visitEnd();
        w.visitField(ACC_PUBLIC | ACC_STATIC, "lastGraphics", "Ljava/lang/Object;", null, null).visitEnd();
        w.visitField(ACC_PUBLIC | ACC_STATIC, "lastText", "Ljava/lang/String;", null, null).visitEnd();
        MethodNode reset = new MethodNode(ACC_PUBLIC | ACC_STATIC, "reset", "()V", null, null);
        reset.instructions.add(new InsnNode(ICONST_0));
        reset.instructions.add(new FieldInsnNode(PUTSTATIC, STORAGE, "calls", "I"));
        reset.instructions.add(new InsnNode(ACONST_NULL));
        reset.instructions.add(new FieldInsnNode(PUTSTATIC, STORAGE, "lastGraphics", "Ljava/lang/Object;"));
        reset.instructions.add(new InsnNode(ACONST_NULL));
        reset.instructions.add(new FieldInsnNode(PUTSTATIC, STORAGE, "lastText", "Ljava/lang/String;"));
        reset.instructions.add(new InsnNode(RETURN));
        reset.accept(w);
        MethodNode hud = new MethodNode(ACC_PUBLIC | ACC_STATIC, "hud", "(Ljava/lang/Object;Ljava/lang/String;)V", null, null);
        hud.instructions.add(new FieldInsnNode(GETSTATIC, STORAGE, "calls", "I"));
        hud.instructions.add(new InsnNode(ICONST_1));
        hud.instructions.add(new InsnNode(IADD));
        hud.instructions.add(new FieldInsnNode(PUTSTATIC, STORAGE, "calls", "I"));
        hud.instructions.add(new VarInsnNode(ALOAD, 0));
        hud.instructions.add(new FieldInsnNode(PUTSTATIC, STORAGE, "lastGraphics", "Ljava/lang/Object;"));
        hud.instructions.add(new VarInsnNode(ALOAD, 1));
        hud.instructions.add(new FieldInsnNode(PUTSTATIC, STORAGE, "lastText", "Ljava/lang/String;"));
        hud.instructions.add(new InsnNode(RETURN));
        hud.accept(w);
        w.visitEnd();
        return w.toByteArray();
    }

    private static final class ProbeLoader extends ClassLoader {
        private final Map<String, byte[]> entries;
        private final String owner;
        private final String flag;
        ProbeLoader(Map<String, byte[]> entries, String owner, String flag) {
            super(HungBySonInvestigateHudBinaryTest.class.getClassLoader());
            this.entries = entries; this.owner = owner; this.flag = flag;
        }
        public void close() { }
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            String path = name.replace('.', '/') + ".class";
            boolean child = entries.containsKey(path) || name.equals(STORAGE.replace('/', '.'));
            if (!child) return super.loadClass(name, resolve);
            Class<?> c = findLoadedClass(name);
            if (c == null) c = findClass(name);
            if (resolve) resolveClass(c);
            return c;
        }
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String path = name.replace('.', '/') + ".class";
            byte[] bytes = name.equals(STORAGE.replace('/', '.'))
                    ? storageSpy() : entries.get(path);
            if (bytes == null) throw new ClassNotFoundException(name);
            if (path.equals(owner + ".class")) bytes = isolateModule(bytes, owner, flag);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static byte[] isolateModule(byte[] bytes, String owner, String flag) {
        ClassNode c = node(bytes);
        MethodNode clinit = method(c, "<clinit>", "()V");
        clinit.instructions.clear(); clinit.tryCatchBlocks.clear();
        clinit.instructions.add(new InsnNode(ICONST_1));
        clinit.instructions.add(new FieldInsnNode(PUTSTATIC, owner, flag, "Z"));
        clinit.instructions.add(new InsnNode(RETURN));
        clinit.maxStack = 1; clinit.maxLocals = 0;
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(w);
        return w.toByteArray();
    }
}
