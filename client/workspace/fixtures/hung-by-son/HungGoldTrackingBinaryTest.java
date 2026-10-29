import java.io.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Runs the production gold hook against a tiny ASM wallet receiver with native packet locals. */
public final class HungGoldTrackingBinaryTest implements Opcodes {
    private static final String GOLD = "luna/features/GoldRateSupport";
    private static final String EXP = "luna/features/ExpEstimateSupport";
    private static final String API = "luna/api/MonsterCacheAccess";
    private static final String CACHE = "luna/generated/HungMonsterCacheAccess";
    private static final String BRIDGE = "luna/generated/ExpEstimateBridge";
    private static final String PLAYER = n("liiiliii");
    private static final String MAIN = n("liiliii");
    private static final String RECEIVER = n("lllllll");
    private static final String PACKET = n("lllllil");
    private static final String MAIN_FIELD = n("llilliill");
    private static final String HANDLER = n("");
    private static int assertions;

    private HungGoldTrackingBinaryTest() { }

    private static String n(String suffix) {
        StringBuilder value = new StringBuilder();
        while (value.length() + suffix.length() < 100) value.append('i');
        return value.append(suffix).toString();
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static byte[] classBytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] resource(String name) throws IOException {
        InputStream input = HungGoldTrackingBinaryTest.class.getClassLoader()
                .getResourceAsStream(name + ".class");
        if (input == null) throw new IOException("Missing compiled class " + name);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static ClassNode newClass(String name, int access, String superName) {
        ClassNode node = new ClassNode();
        node.version = V1_4;
        node.access = access;
        node.name = name;
        node.superName = superName;
        node.interfaces = new ArrayList<String>();
        node.fields = new ArrayList<FieldNode>();
        node.methods = new ArrayList<MethodNode>();
        node.innerClasses = new ArrayList<InnerClassNode>();
        node.attrs = new ArrayList<Attribute>();
        node.methods.add(defaultConstructor(superName));
        return node;
    }

    private static MethodNode defaultConstructor(String superName) {
        MethodNode constructor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL, superName, "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(RETURN));
        constructor.maxStack = 1;
        constructor.maxLocals = 1;
        return constructor;
    }

    private static byte[] playerClass() {
        ClassNode player = newClass(PLAYER, ACC_PUBLIC, "java/lang/Object");
        player.fields.add(new FieldNode(ACC_PUBLIC, "id", "I", null, null));
        player.fields.add(new FieldNode(ACC_PUBLIC, "bD", "J", null, null));
        player.fields.add(new FieldNode(ACC_PUBLIC, "bC", "I", null, null));
        return classBytes(player);
    }

    private static byte[] mainClass() {
        ClassNode main = newClass(MAIN, ACC_PUBLIC, "java/lang/Object");
        main.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, MAIN_FIELD, "L" + PLAYER + ";", null, null));
        return classBytes(main);
    }

    private static byte[] apiClass() {
        ClassNode api = new ClassNode();
        api.version = V1_4;
        api.access = ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT;
        api.name = API;
        api.superName = "java/lang/Object";
        api.interfaces = new ArrayList<String>();
        api.fields = new ArrayList<FieldNode>();
        api.methods = new ArrayList<MethodNode>();
        api.methods.add(new MethodNode(ACC_PUBLIC | ACC_ABSTRACT, "serverKey",
                "()Ljava/lang/String;", null, null));
        api.innerClasses = new ArrayList<InnerClassNode>();
        api.attrs = new ArrayList<Attribute>();
        return classBytes(api);
    }

    private static byte[] cacheClass() {
        ClassNode cache = newClass(CACHE, ACC_PUBLIC, "java/lang/Object");
        cache.interfaces.add(API);
        cache.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "INSTANCE",
                "L" + API + ";", null, null));
        MethodNode init = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
        init.instructions.add(new TypeInsnNode(NEW, CACHE));
        init.instructions.add(new InsnNode(DUP));
        init.instructions.add(new MethodInsnNode(INVOKESPECIAL, CACHE, "<init>", "()V", false));
        init.instructions.add(new FieldInsnNode(PUTSTATIC, CACHE, "INSTANCE", "L" + API + ";"));
        init.instructions.add(new InsnNode(RETURN));
        init.maxStack = 2;
        init.maxLocals = 0;
        cache.methods.add(init);
        MethodNode server = new MethodNode(ACC_PUBLIC, "serverKey", "()Ljava/lang/String;", null, null);
        server.instructions.add(new LdcInsnNode("test-server"));
        server.instructions.add(new InsnNode(ARETURN));
        server.maxStack = 1;
        server.maxLocals = 1;
        cache.methods.add(server);
        return classBytes(cache);
    }

    private static byte[] expSupportClass() {
        ClassNode support = newClass(EXP, ACC_PUBLIC, "java/lang/Object");
        MethodNode snapshot = new MethodNode(ACC_PUBLIC | ACC_STATIC, "snapshot",
                "(Ljava/lang/String;III)V", null, null);
        snapshot.instructions.add(new InsnNode(RETURN));
        snapshot.maxStack = 0;
        snapshot.maxLocals = 4;
        support.methods.add(snapshot);
        MethodNode disconnect = new MethodNode(ACC_PUBLIC | ACC_STATIC, "disconnect", "()V", null, null);
        disconnect.instructions.add(new InsnNode(RETURN));
        disconnect.maxStack = 0;
        disconnect.maxLocals = 0;
        support.methods.add(disconnect);
        return classBytes(support);
    }

    private static byte[] bridgeClass() {
        ClassNode bridge = newClass(BRIDGE, ACC_PUBLIC | ACC_FINAL, "java/lang/Object");
        MethodNode snapshot = new MethodNode(ACC_PUBLIC | ACC_STATIC, "snapshot", "()V", null, null);
        snapshot.instructions.add(new LdcInsnNode("test-server"));
        snapshot.instructions.add(new InsnNode(ICONST_1));
        snapshot.instructions.add(new InsnNode(ICONST_0));
        snapshot.instructions.add(new InsnNode(ICONST_0));
        snapshot.instructions.add(new MethodInsnNode(INVOKESTATIC, EXP, "snapshot",
                "(Ljava/lang/String;III)V", false));
        snapshot.instructions.add(new InsnNode(RETURN));
        snapshot.maxStack = 4;
        snapshot.maxLocals = 0;
        bridge.methods.add(snapshot);
        MethodNode disconnect = new MethodNode(ACC_PUBLIC | ACC_STATIC, "disconnect", "()V", null, null);
        disconnect.instructions.add(new MethodInsnNode(INVOKESTATIC, EXP, "disconnect", "()V", false));
        disconnect.instructions.add(new InsnNode(RETURN));
        disconnect.maxStack = 0;
        disconnect.maxLocals = 0;
        bridge.methods.add(disconnect);
        return classBytes(bridge);
    }

    private static byte[] packetClass() {
        ClassNode packet = newClass(PACKET, ACC_PUBLIC, "java/io/DataInputStream");
        packet.methods.remove(0);
        MethodNode constructor = new MethodNode(ACC_PUBLIC, "<init>", "([B)V", null, null);
        constructor.instructions.add(new VarInsnNode(ALOAD, 0));
        constructor.instructions.add(new TypeInsnNode(NEW, "java/io/ByteArrayInputStream"));
        constructor.instructions.add(new InsnNode(DUP));
        constructor.instructions.add(new VarInsnNode(ALOAD, 1));
        constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/io/ByteArrayInputStream",
                "<init>", "([B)V", false));
        constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/io/DataInputStream",
                "<init>", "(Ljava/io/InputStream;)V", false));
        constructor.instructions.add(new InsnNode(RETURN));
        constructor.maxStack = 4;
        constructor.maxLocals = 2;
        packet.methods.add(constructor);
        return classBytes(packet);
    }

    private static byte[] receiverClass() {
        ClassNode receiver = newClass(RECEIVER, ACC_PUBLIC, "java/lang/Object");
        MethodNode method = new MethodNode(ACC_PUBLIC, HANDLER,
                "(L" + PACKET + ";Let;B)V", null, null);
        LabelNode done = new LabelNode();
        method.instructions.add(new VarInsnNode(ILOAD, 3));
        method.instructions.add(new JumpInsnNode(IFNE, done));
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/io/DataInputStream",
                "readLong", "()J", false));
        method.instructions.add(new VarInsnNode(LSTORE, 6));
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/io/DataInputStream",
                "readInt", "()I", false));
        method.instructions.add(new VarInsnNode(ISTORE, 8));
        method.instructions.add(new FieldInsnNode(GETSTATIC, MAIN, MAIN_FIELD, "L" + PLAYER + ";"));
        method.instructions.add(new VarInsnNode(LLOAD, 6));
        method.instructions.add(new FieldInsnNode(PUTFIELD, PLAYER, "bD", "J"));
        method.instructions.add(new FieldInsnNode(GETSTATIC, MAIN, MAIN_FIELD, "L" + PLAYER + ";"));
        method.instructions.add(new VarInsnNode(ILOAD, 8));
        method.instructions.add(new FieldInsnNode(PUTFIELD, PLAYER, "bC", "I"));
        method.instructions.add(done);
        method.instructions.add(new InsnNode(RETURN));
        method.maxStack = 3;
        method.maxLocals = 9;
        receiver.methods.add(method);
        return classBytes(receiver);
    }

    private static Map<String, byte[]> archive() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put(GOLD + ".class", resource(GOLD));
        entries.put("luna/features/ExpRateSupport.class", resource("luna/features/ExpRateSupport"));
        entries.put(EXP + ".class", expSupportClass());
        entries.put(API + ".class", apiClass());
        entries.put(CACHE + ".class", cacheClass());
        entries.put(BRIDGE + ".class", bridgeClass());
        entries.put(PLAYER + ".class", playerClass());
        entries.put(MAIN + ".class", mainClass());
        entries.put(PACKET + ".class", packetClass());
        entries.put("et.class", classBytes(newClass("et", ACC_PUBLIC, "java/lang/Object")));
        entries.put(RECEIVER + ".class", receiverClass());
        return entries;
    }

    private static final class MapLoader extends ClassLoader {
        private final Map<String, byte[]> classes;
        MapLoader(Map<String, byte[]> entries, ClassLoader parent) {
            super(parent);
            classes = new HashMap<String, byte[]>();
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                if (entry.getKey().endsWith(".class")) {
                    classes.put(entry.getKey().substring(0, entry.getKey().length() - 6)
                            .replace('/', '.'), entry.getValue());
                }
            }
        }
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) loaded = classes.containsKey(name)
                    ? findClass(name) : super.loadClass(name, false);
            if (resolve) resolveClass(loaded);
            return loaded;
        }
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) return super.findClass(name);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static Object invokeStatic(ClassLoader loader, String owner, String method,
            Class<?>[] types, Object[] args) throws Exception {
        return Class.forName(owner.replace('/', '.'), true, loader).getMethod(method, types).invoke(null, args);
    }

    private static Object receiver(ClassLoader loader) throws Exception {
        return Class.forName(RECEIVER.replace('/', '.'), true, loader).newInstance();
    }

    private static Object newPacket(ClassLoader loader, byte[] bytes) throws Exception {
        return Class.forName(PACKET.replace('/', '.'), true, loader)
                .getConstructor(byte[].class).newInstance(new Object[] { bytes });
    }

    private static void dispatch(ClassLoader loader, Object nativeReceiver, long gold,
            int secondary, int kind) throws Exception {
        byte[] packetBytes = packet(gold, secondary);
        Object packet = newPacket(loader, packetBytes);
        Class<?> packetType = Class.forName(PACKET.replace('/', '.'), true, loader);
        Class<?> entityType = Class.forName("et", true, loader);
        Class<?> handlerType = Class.forName(RECEIVER.replace('/', '.'), true, loader);
        handlerType.getMethod(HANDLER, packetType, entityType, Byte.TYPE)
                .invoke(nativeReceiver, packet, null, Byte.valueOf((byte)kind));
    }

    private static byte[] packet(long gold, int secondary) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeLong(gold);
        out.writeInt(secondary);
        out.close();
        return bytes.toByteArray();
    }

    private static byte[] incompleteLong(long gold) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeLong(gold);
        out.close();
        return bytes.toByteArray();
    }

    private static long supportValue(ClassLoader loader, String method) throws Exception {
        return ((Long)invokeStatic(loader, GOLD, method, new Class<?>[0], new Object[0])).longValue();
    }

    private static void malformed(ClassLoader loader, Object nativeReceiver, byte[] bytes) throws Exception {
        Object packet = newPacket(loader, bytes);
        Class<?> packetType = Class.forName(PACKET.replace('/', '.'), true, loader);
        Class<?> entityType = Class.forName("et", true, loader);
        Class<?> handlerType = Class.forName(RECEIVER.replace('/', '.'), true, loader);
        try {
            handlerType.getMethod(HANDLER, packetType, entityType, Byte.TYPE)
                    .invoke(nativeReceiver, packet, null, Byte.valueOf((byte)0));
            throw new AssertionError("Expected malformed packet read to fail");
        } catch (InvocationTargetException expected) {
            check(expected.getCause() instanceof EOFException, "Malformed packet failed before wallet write");
        }
    }

    private static void identity(ClassLoader loader, Object player, int id) throws Exception {
        Class.forName(PLAYER.replace('/', '.'), true, loader).getField("id").setInt(player, id);
    }

    private static Map<String, byte[]> transformed(Properties catalog) throws Exception {
        Map<String, byte[]> entries = archive();
        catalog.setProperty("slots", "1");
        catalog.setProperty("slot.0.bridge.owner", BRIDGE);
        catalog.setProperty("slot.0.main.owner", MAIN);
        catalog.setProperty("slot.0.main.field", MAIN_FIELD);
        catalog.setProperty("slot.0.main.descriptor", "L" + PLAYER + ";");
        catalog.setProperty("slot.0.entity.owner", PLAYER);
        catalog.setProperty("slot.0.id.field", "id");
        HungGoldTrackingPatch.apply(entries, catalog);
        check("1".equals(catalog.getProperty("gold.receiver.wallet.hooks")), "One synthetic native wallet hook added");
        return entries;
    }

    private static void setPlayer(ClassLoader loader, Object player) throws Exception {
        Class<?> playerType = Class.forName(PLAYER.replace('/', '.'), true, loader);
        Class<?> mainType = Class.forName(MAIN.replace('/', '.'), true, loader);
        mainType.getField(MAIN_FIELD).set(null, player);
        playerType.getField("bD").setLong(player, 0L);
    }

    private static void runScenarios(ClassLoader loader) throws Exception {
        Class<?> playerType = Class.forName(PLAYER.replace('/', '.'), true, loader);
        Object nativeReceiver = receiver(loader);
        Object player = playerType.newInstance();
        setPlayer(loader, player);
        identity(loader, player, 0);

        // Wallet packet first: it is held until login identity arrives, then attached as baseline.
        dispatch(loader, nativeReceiver, 1000L, 0, 0);
        check(supportValue(loader, "current") == -1L, "Pre-login wallet is not counted before identity readiness");
        identity(loader, player, 7);
        invokeStatic(loader, BRIDGE, "snapshot", new Class<?>[0], new Object[0]);
        check(supportValue(loader, "current") == 1000L, "Pending login wallet is attached as current baseline");
        check(supportValue(loader, "received") == 0L, "Initial wallet does not count as session income");

        dispatch(loader, nativeReceiver, 1000L, 0, 0);
        check(supportValue(loader, "received") == 0L, "Duplicate wallet packet has no positive delta");
        dispatch(loader, nativeReceiver, 1500L, 0, 0);
        check(supportValue(loader, "received") == 500L, "Positive wallet delta is received gold");
        invokeStatic(loader, BRIDGE, "snapshot", new Class<?>[0], new Object[0]);
        check(supportValue(loader, "received") == 500L, "Same-identity snapshot preserves session total");
        dispatch(loader, nativeReceiver, 900L, 0, 0);
        check(supportValue(loader, "current") == 900L && supportValue(loader, "received") == 500L,
                "Spending updates current wallet without reducing received total");
        dispatch(loader, nativeReceiver, 1000L, 0, 0);
        check(supportValue(loader, "received") == 600L, "Later positive delta counts after spending");
        dispatch(loader, nativeReceiver, 999999L, 0, 1);
        check(supportValue(loader, "current") == 1000L && supportValue(loader, "received") == 600L,
                "Non-wallet packet kind does not enter the wallet hook");

        malformed(loader, nativeReceiver, incompleteLong(5000L));
        malformed(loader, nativeReceiver, new byte[] { 1, 2, 3 });
        check(supportValue(loader, "current") == 1000L && supportValue(loader, "received") == 600L,
                "Incomplete/corrupt wallet packets fail before the post-write hook");

        // A new character resets measurements; the first packet for it is a baseline.
        identity(loader, player, 8);
        invokeStatic(loader, BRIDGE, "snapshot", new Class<?>[0], new Object[0]);
        check(supportValue(loader, "current") == -1L && supportValue(loader, "received") == -1L,
                "Character change clears the previous wallet session");
        dispatch(loader, nativeReceiver, 5000L, 0, 0);
        check(supportValue(loader, "current") == 5000L && supportValue(loader, "received") == 0L,
                "First post-snapshot wallet packet establishes the new baseline");
        dispatch(loader, nativeReceiver, 5010L, 0, 0);
        check(supportValue(loader, "received") == 10L, "New character gain starts a separate total");

        // Disconnect clears readiness and pending state. An early packet on the next connection
        // is held until that connection's character snapshot and then establishes its baseline.
        invokeStatic(loader, BRIDGE, "disconnect", new Class<?>[0], new Object[0]);
        check(supportValue(loader, "current") == -1L && supportValue(loader, "received") == -1L,
                "Disconnect clears wallet measurements");
        dispatch(loader, nativeReceiver, 700L, 0, 0);
        check(supportValue(loader, "current") == -1L, "Post-disconnect packet remains pending until identity snapshot");
        identity(loader, player, 9);
        invokeStatic(loader, BRIDGE, "snapshot", new Class<?>[0], new Object[0]);
        check(supportValue(loader, "current") == 700L && supportValue(loader, "received") == 0L,
                "Pending next-login wallet becomes only a baseline");
        dispatch(loader, nativeReceiver, 750L, 0, 0);
        check(supportValue(loader, "received") == 50L, "Post-baseline gain is retained");

        // Snapshot-first ordering also treats its first complete packet as baseline.
        invokeStatic(loader, BRIDGE, "disconnect", new Class<?>[0], new Object[0]);
        identity(loader, player, 10);
        invokeStatic(loader, BRIDGE, "snapshot", new Class<?>[0], new Object[0]);
        dispatch(loader, nativeReceiver, 8100L, 0, 0);
        check(supportValue(loader, "current") == 8100L && supportValue(loader, "received") == 0L,
                "Snapshot-first login treats the first complete wallet as baseline");
        dispatch(loader, nativeReceiver, 8110L, 0, 0);
        check(supportValue(loader, "received") == 10L, "Snapshot-first login counts the next gain");

        // A corrupt pre-login packet cannot populate the pending slot because it never writes bD.
        invokeStatic(loader, BRIDGE, "disconnect", new Class<?>[0], new Object[0]);
        identity(loader, player, 0);
        malformed(loader, nativeReceiver, new byte[] { 4, 5 });
        identity(loader, player, 11);
        invokeStatic(loader, BRIDGE, "snapshot", new Class<?>[0], new Object[0]);
        check(supportValue(loader, "current") == -1L,
                "Corrupt early packet does not become a pending wallet baseline");
        dispatch(loader, nativeReceiver, 99L, 0, 0);
        check(supportValue(loader, "current") == 99L && supportValue(loader, "received") == 0L,
                "First later complete packet becomes the baseline after corruption");

        // Native self-character packets use readShort: signed and zero IDs are valid.
        // Readiness comes from an explicit login snapshot, never the sign of the ID.
        for (int id : new int[] { -32768, -1, 0, 32767 }) {
            invokeStatic(loader, BRIDGE, "disconnect", new Class<?>[0], new Object[0]);
            identity(loader, player, id);
            dispatch(loader, nativeReceiver, 12000L, 0, 0);
            check(supportValue(loader, "current") == -1L, "Wallet waits for snapshot for signed ID " + id);
            invokeStatic(loader, BRIDGE, "snapshot", new Class<?>[0], new Object[0]);
            check(supportValue(loader, "current") == 12000L && supportValue(loader, "received") == 0L,
                    "Signed ID " + id + " attaches the initial wallet immediately");
            dispatch(loader, nativeReceiver, 12050L, 0, 0);
            check(supportValue(loader, "current") == 12050L && supportValue(loader, "received") == 50L,
                    "Signed ID " + id + " counts later receipts");
            dispatch(loader, nativeReceiver, 11000L, 0, 0);
            check(supportValue(loader, "current") == 11000L && supportValue(loader, "received") == 50L,
                    "Signed ID " + id + " keeps receipts when spending");
        }

        String info = (String)invokeStatic(loader, BRIDGE, "goldInfo", new Class<?>[0], new Object[0]);
        check(info.indexOf("Vàng hiện tại") >= 0 && info.indexOf("Vàng đã nhận từ khi đăng nhập") >= 0,
                "Generated EXP bridge exposes the three-row gold info method");
    }

    private static Properties loadProperties(File file) throws IOException {
        Properties result = new Properties();
        InputStream input = new FileInputStream(file);
        try { result.load(input); } finally { input.close(); }
        return result;
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
        } finally { input.close(); }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02X", value & 255));
        return result.toString();
    }

    private static Map<String, byte[]> readJar(File file) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        JarFile jar = new JarFile(file);
        try {
            Enumeration<JarEntry> values = jar.entries();
            while (values.hasMoreElements()) {
                JarEntry entry = values.nextElement();
                InputStream input = jar.getInputStream(entry);
                try {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                    entries.put(entry.getName(), output.toByteArray());
                } finally { input.close(); }
            }
        } finally { jar.close(); }
        return entries;
    }

    private static ClassNode readNode(Map<String, byte[]> entries, String owner) {
        byte[] bytes = entries.get(owner + ".class");
        check(bytes != null, "Candidate class exists: " + owner);
        ClassNode result = new ClassNode();
        new ClassReader(bytes).accept(result, 0);
        return result;
    }

    private static boolean hasMethod(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) if (method.name.equals(name) && method.desc.equals(desc)) return true;
        return false;
    }

    private static void verifyCandidate(File jarFile, File catalogFile, File microFile) throws Exception {
        check(jarFile.isFile() && catalogFile.isFile() && microFile.isFile(),
                "Candidate, catalog and MicroEmulator paths exist");
        JarFile micro = new JarFile(microFile);
        micro.close();
        Properties catalog = loadProperties(catalogFile);
        check(hash(jarFile).equals(catalog.getProperty("output.sha256")),
                "Candidate hash matches merged catalog");
        String receiverOwner = catalog.getProperty("gold.receiver.owner");
        String receiverMethod = catalog.getProperty("gold.receiver.method");
        String receiverDesc = catalog.getProperty("gold.receiver.descriptor");
        String walletOwner = catalog.getProperty("gold.wallet.owner");
        String walletField = catalog.getProperty("gold.wallet.field");
        String bridgeOwner = catalog.getProperty("gold.bridge.owner");
        check(receiverOwner != null && receiverMethod != null && receiverDesc != null
                && walletOwner != null && walletField != null && bridgeOwner != null,
                "Merged catalog contains exact native gold symbols");
        String[] changed = catalog.getProperty("gold.tracking.changed.classes", "").split(",");
        Set<String> changedSet = new HashSet<String>(Arrays.asList(changed));
        check(changedSet.contains(receiverOwner + ".class")
                && changedSet.contains(bridgeOwner + ".class"),
                "Gold changed-class allow-list contains receiver and bridge entries");
        Map<String, byte[]> entries = readJar(jarFile);
        ClassNode receiver = readNode(entries, receiverOwner);
        MethodNode handler = null;
        for (MethodNode method : receiver.methods) {
            if (method.name.equals(receiverMethod) && method.desc.equals(receiverDesc)) handler = method;
        }
        check(handler != null, "Candidate retains native gold receiver method");
        int writes = 0, hooks = 0;
        boolean hookAfterWalletWrite = false;
        for (AbstractInsnNode insn : handler.instructions.toArray()) {
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (field.getOpcode() == PUTFIELD && field.owner.equals(walletOwner)
                        && field.name.equals(walletField) && field.desc.equals("J")) {
                    writes++;
                    AbstractInsnNode next = insn.getNext();
                    while (next != null && next.getOpcode() < 0) next = next.getNext();
                    if (next instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) next;
                        if (call.getOpcode() == INVOKESTATIC && call.owner.equals(bridgeOwner)
                                && call.name.equals("goldBalance") && call.desc.equals("()V")) {
                            hookAfterWalletWrite = true;
                        }
                    }
                }
            }
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == INVOKESTATIC && call.owner.equals(bridgeOwner)
                        && call.name.equals("goldBalance") && call.desc.equals("()V")) hooks++;
            }
        }
        check(writes == 1 && hooks == 1 && hookAfterWalletWrite,
                "Candidate hooks exactly once after the native gold PUTFIELD");
        ClassNode bridge = readNode(entries, bridgeOwner);
        check(hasMethod(bridge, "goldBalance", "()V")
                && hasMethod(bridge, "goldInfo", "()Ljava/lang/String;")
                && hasMethod(bridge, "goldSnapshot", "()V")
                && hasMethod(bridge, "goldDisconnect", "()V"),
                "Candidate bridge exposes wallet, info, identity and disconnect methods");
        check(entries.containsKey(GOLD + ".class"), "Candidate includes GoldRateSupport");
        // Execute the candidate's four actual gold methods with controlled native peers.
        // This catches a packaged bridge that differs from the current generator.
        Properties probeCatalog = new Properties();
        Map<String,byte[]> probe = transformed(probeCatalog);
        ClassNode executable = readNode(probe, BRIDGE);
        for (Iterator<MethodNode> methods=executable.methods.iterator();methods.hasNext();)
            if(methods.next().name.startsWith("gold"))methods.remove();
        for(MethodNode nativeMethod:bridge.methods) if(nativeMethod.name.startsWith("gold")) {
            MethodNode copy=new MethodNode(nativeMethod.access,nativeMethod.name,nativeMethod.desc,null,null);
            nativeMethod.accept(copy);
            for(AbstractInsnNode instruction:copy.instructions.toArray()) if(instruction instanceof FieldInsnNode) {
                FieldInsnNode f=(FieldInsnNode)instruction;
                if(f.owner.equals(catalog.getProperty("gold.main.owner")) && f.name.equals(catalog.getProperty("gold.main.field"))) {
                    f.owner=MAIN;f.name=MAIN_FIELD;f.desc="L"+PLAYER+";";
                } else if(f.owner.equals(walletOwner)) {
                    f.owner=PLAYER;
                    if(f.name.equals(catalog.getProperty("gold.character.id.field")))f.name="id";
                }
            }
            executable.methods.add(copy);
        }
        probe.put(BRIDGE+".class",classBytes(executable));
        runScenarios(new MapLoader(probe,HungGoldTrackingBinaryTest.class.getClassLoader()));
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 0 || args.length == 3,
                "Usage: HungGoldTrackingBinaryTest [candidate.jar catalog.properties micro.jar]");
        Properties catalog = new Properties();
        Map<String, byte[]> entries = transformed(catalog);
        MapLoader loader = new MapLoader(entries, HungGoldTrackingBinaryTest.class.getClassLoader());
        runScenarios(loader);
        if (args.length == 3) verifyCandidate(new File(args[0]), new File(args[1]), new File(args[2]));
        System.out.println("HUNG_GOLD_TRACKING_BINARY_TEST=PASS; assertions=" + assertions
                + (args.length == 3 ? "; candidate archive verified" : "; ASM wallet fixture"));
    }
}
