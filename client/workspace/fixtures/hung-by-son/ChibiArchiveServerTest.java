import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import javax.microedition.rms.RecordStore;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.util.MemoryRecordStoreManager;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Independent archive, endpoint, login-identity, and RMS boundary checks for HSO CHIBI. */
public final class ChibiArchiveServerTest implements Opcodes {
    private static int checks;
    private static String host;
    private static int port, connects;
    private static boolean connected;

    public static Object session() { return new Object(); }
    public static boolean connected(Object ignored) { return connected; }
    public static void connect(Object ignored, String value, int number) {
        host = value; port = number; connects++;
    }

    private static final class ProbeLoader extends ClassLoader {
        ProbeLoader(ClassLoader parent) { super(parent); }
        Class<?> define(String name, byte[] bytes) { return defineClass(name, bytes, 0, bytes.length); }
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 4, "input.jar candidate.jar report.properties microemulator.jar");
        File inputFile = new File(args[0]), candidateFile = new File(args[1]), microFile = new File(args[3]);
        require(inputFile.isFile() && candidateFile.isFile() && new File(args[2]).isFile() && microFile.isFile(),
                "Input, candidate, patch report, and MicroEmulator exist");
        Properties report = properties(new File(args[2]));
        require("HSO CHIBI".equals(report.getProperty("suite.name"))
                && "HSO CHIBI".equals(report.getProperty("suite.vendor")), "Patch identity properties");
        require("103.77.173.232".equals(report.getProperty("server.host"))
                && "19129".equals(report.getProperty("server.port"))
                && "9.9.9".equals(report.getProperty("login.version")), "Pinned endpoint properties");
        require("0".equals(report.getProperty("server.index")) && "1".equals(report.getProperty("server.count")),
                "Singleton server properties");
        require("chibiOnlyServer".equals(report.getProperty("server.list.factory")), "Singleton factory property");
        require("HSO_CHIBI_".equals(report.getProperty("rms.prefix"))
                && "disabled".equals(report.getProperty("rms.exp.legacy.import")), "Fresh CHIBI RMS properties");

        Map<String, byte[]> before = read(inputFile), after = read(candidateFile);
        require(hash(inputFile).equals(report.getProperty("input.sha256")), "Input SHA-256 report");
        require(hash(candidateFile).equals(report.getProperty("output.sha256")), "Output SHA-256 report");
        Set<String> changedAllow = entries(report.getProperty("changed.classes"));
        Set<String> additions = entries(report.getProperty("added.classes"));
        require(additions.equals(Collections.singleton("luna/generated/ChibiLoginAuth.class")),
                "Only the CHIBI authentication class may be added");
        Set<String> expectedEntries = new LinkedHashSet<String>(before.keySet());
        for (String entry : additions) require(expectedEntries.add(entry), "Addition collision: " + entry);
        require(expectedEntries.equals(after.keySet()), "Exact archive topology plus reported class additions");
        require(additions.contains("luna/generated/ChibiLoginAuth.class"), "New CHIBI authentication class reported");

        JarFile zip = new JarFile(candidateFile);
        Set<String> changed = new LinkedHashSet<String>();
        int classes = 0, resources = 0, classMax = 0;
        try {
            for (String name : after.keySet()) {
                byte[] old = before.get(name), now = after.get(name);
                JarEntry entry = zip.getJarEntry(name);
                CRC32 crc = new CRC32(); crc.update(now);
                require(entry != null && entry.getCrc() == crc.getValue(), "CRC: " + name);
                if (old != null && !Arrays.equals(old, now)) {
                    changed.add(name);
                    require(name.equals("META-INF/MANIFEST.MF") || name.endsWith(".class"),
                            "Only manifest and classes may change: " + name);
                    if (name.endsWith(".class")) require(changedAllow.contains(name), "Unreported class change: " + name);
                }
                if (name.endsWith(".class")) {
                    classes++;
                    int major = major(now); classMax = Math.max(classMax, major);
                    require(major <= 51, "CLDC class version: " + name);
                    if (old != null) require(major == major(old), "Native class version changed: " + name);
                } else if (!name.endsWith("/")) {
                    resources++;
                    if (!name.equals("META-INF/MANIFEST.MF")) require(old != null && Arrays.equals(old, now),
                            "Unchanged non-manifest resource: " + name);
                }
            }
        } finally { zip.close(); }
        Set<String> actualChangedClasses = new LinkedHashSet<String>();
        for (String entry : changed) if (entry.endsWith(".class")) actualChangedClasses.add(entry);
        require(actualChangedClasses.equals(changedAllow), "Changed-class allow-list matches archive delta");
        require(!changed.isEmpty() && changed.contains("META-INF/MANIFEST.MF"), "Manifest and feature changes exist");
        checkManifest(before.get("META-INF/MANIFEST.MF"), after.get("META-INF/MANIFEST.MF"));

        String listOwner = required(report, "server.list.owner");
        String selectorOwner = required(report, "selector.owner");
        String selectorName = required(report, "selector.name");
        String selectorDesc = required(report, "selector.desc");
        require("B".equals(selectorDesc), "Byte selector descriptor");
        Set<String> tables = new LinkedHashSet<String>(Arrays.asList(required(report, "server.list.fields").split(",")));
        require(tables.size() == 2, "Both native server tables reported");
        int tableWrites = 0, selectorWrites = 0;
        for (Map.Entry<String, byte[]> item : after.entrySet()) if (item.getKey().endsWith(".class")) {
            ClassNode node = parse(item.getValue());
            for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != PUTSTATIC) continue;
                FieldInsnNode field = (FieldInsnNode)insn;
                if (field.owner.equals(listOwner) && tables.contains(field.name)) {
                    AbstractInsnNode callNode = previousOpcode(insn);
                    require(callNode instanceof MethodInsnNode, "Server table assignment uses factory");
                    MethodInsnNode call = (MethodInsnNode)callNode;
                    require(call.getOpcode() == INVOKESTATIC && call.owner.equals(listOwner)
                            && call.name.equals(required(report, "server.list.factory"))
                            && call.desc.equals("()[[Ljava/lang/String;"), "Server table assignment bypasses singleton factory");
                    tableWrites++;
                }
                if (field.owner.equals(selectorOwner) && field.name.equals(selectorName) && field.desc.equals("B")) {
                    AbstractInsnNode zero = previousOpcode(insn), pop = zero == null ? null : previousOpcode(zero);
                    require(zero != null && zero.getOpcode() == ICONST_0 && pop != null && pop.getOpcode() == POP,
                            "Selector writer does not normalize to zero");
                    selectorWrites++;
                }
            }
        }
        require(tableWrites == integer(report, "server.table.writes") && tableWrites >= 2,
                "Every server-table assignment is factory-backed");
        require(selectorWrites == integer(report, "server.selector.writes") && selectorWrites > 0,
                "Every selector assignment is normalized");

        URL[] urls = new URL[] { candidateFile.toURI().toURL(), microFile.toURI().toURL() };
        URLClassLoader loader = new URLClassLoader(urls, ChibiArchiveServerTest.class.getClassLoader());
        int linked = 0, newReferences = 0;
        try {
            for (String entry : after.keySet()) if (entry.endsWith(".class")) {
                Class<?> type = Class.forName(className(entry), false, loader);
                type.getDeclaredFields(); type.getDeclaredMethods(); type.getDeclaredConstructors(); linked++;
            }
            require(linked == classes, "All candidate classes loaded through MicroEmulator");
            newReferences = verifyNewReferences(before, after, changedAllow, additions, loader);
            verifyTablesAndConnect(before, after, report, loader);
            verifyMetadata(inputFile, candidateFile, report, ChibiArchiveServerTest.class.getClassLoader(), urls[1]);
            verifyFreshRms(after, report, loader);
        } finally { loader.close(); }
        System.out.println("CHIBI_ARCHIVE_SERVER=PASS; entries=" + after.size() + "; classes=" + classes
                + "; resources=" + resources + "; maxMajor=" + classMax + "; linked=" + linked
                + "; changed=" + changed.size() + "; newReferences=" + newReferences
                + "; CRC/resources/manifest, singleton tables/selectors, metadata, native dial, and HSO_CHIBI RMS");
    }

    private static void verifyTablesAndConnect(Map<String, byte[]> before, Map<String, byte[]> after,
            Properties p, URLClassLoader loader) throws Exception {
        String listOwner = required(p, "server.list.owner");
        String selectorOwner = required(p, "selector.owner"), selectorName = required(p, "selector.name");
        Set<String> fields = new LinkedHashSet<String>(Arrays.asList(required(p, "server.list.fields").split(",")));
        Class<?> list = Class.forName(listOwner.replace('/', '.'), true, loader);
        Method factory = list.getDeclaredMethod(required(p, "server.list.factory"));
        require(Modifier.isStatic(factory.getModifiers()), "Singleton factory is static");
        factory.setAccessible(true);
        checkSoleRows((String[][])factory.invoke(null), p, "Factory result");
        for (String name : fields) {
            Field table = list.getDeclaredField(name); table.setAccessible(true);
            checkSoleRows((String[][])table.get(null), p, "Initialized server table " + name);
        }
        Class<?> selector = Class.forName(selectorOwner.replace('/', '.'), true, loader);
        Field selected = selector.getDeclaredField(selectorName); selected.setAccessible(true);
        require(selected.getByte(null) == 0, "Startup server selector is row zero");
        Class<?> refreshOwner = Class.forName(required(p, "server.refresh.owner").replace('/', '.'), true, loader);
        Method refresh = refreshOwner.getDeclaredMethod(required(p, "server.refresh.method"), String.class, boolean.class);
        require(Modifier.isStatic(refresh.getModifiers()), "Server refresh method is static");
        refresh.setAccessible(true);
        refresh.invoke(null, "Injected:198.51.100.7:1234:1,Overflow:203.0.113.44:4321:0,", true);
        for (String name : fields) {
            Field table = list.getDeclaredField(name); table.setAccessible(true);
            checkSoleRows((String[][])table.get(null), p, "Remote multi-row refresh remains singleton " + name);
        }

        ClassNode original = parse(before.get(required(p, "connection.owner") + ".class"));
        MethodNode source = method(original, required(p, "connection.method"), "()V");
        ClassNode candidateNode = parse(after.get(required(p, "connection.owner") + ".class"));
        MethodNode target = method(candidateNode, required(p, "connection.method"), "()V");
        Class<?> probe = makeConnectProbe(target, "ChibiConnectProbe", loader);
        String[][] rows = (String[][])fieldsValue(list, fields.iterator().next());
        for (boolean language : new boolean[] { false, true }) {
            Object[] result = runConnection(probe, language, rows, false);
            require("103.77.173.232".equals(result[0]), "Native dial host for language=" + language);
            require(Integer.valueOf(19129).equals(result[1]), "Native dial port for language=" + language);
            require("9.9.9".equals(result[2]), "Native dial version for language=" + language);
            require(Integer.valueOf(1).equals(result[3]), "One native dial for language=" + language);
        }
        Object[] active = runConnection(probe, false, rows, true);
        require(Integer.valueOf(0).equals(active[3]), "Active-session guard prevents a second dial");
        require(source != null, "Baseline connection method exists");
    }

    private static Class<?> makeConnectProbe(MethodNode source, String name, ClassLoader parent) {
        MethodNode copy = new MethodNode(ACC_PUBLIC | ACC_STATIC, "connect", "()V", null, null);
        Map<LabelNode, LabelNode> labels = labelMap(source);
        for (AbstractInsnNode insn : source.instructions.toArray()) {
            if (insn instanceof FrameNode) continue;
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)insn;
                if (field.getOpcode() == PUTSTATIC) {
                    if (field.desc.equals("Ljava/lang/String;")) copy.instructions.add(new FieldInsnNode(PUTSTATIC, name, "version", field.desc));
                    else copy.instructions.add(new InsnNode(POP));
                } else if (field.desc.equals("B")) copy.instructions.add(new FieldInsnNode(GETSTATIC, name, "selected", "B"));
                else if (field.desc.equals("Z")) copy.instructions.add(new FieldInsnNode(GETSTATIC, name, "locale", "Z"));
                else if (field.desc.equals("[[Ljava/lang/String;")) copy.instructions.add(new FieldInsnNode(GETSTATIC, name, "list", field.desc));
                else if (field.desc.equals("Ljava/lang/String;")) copy.instructions.add(new LdcInsnNode(""));
                else { require("I".equals(field.desc), "Unexpected connect field descriptor " + field.desc); copy.instructions.add(new InsnNode(ICONST_0)); }
            } else if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)insn;
                if (call.getOpcode() == INVOKESTATIC) copy.instructions.add(new MethodInsnNode(INVOKESTATIC,
                        "ChibiArchiveServerTest", "session", "()Ljava/lang/Object;", false));
                else if (call.desc.equals("()Z")) copy.instructions.add(new MethodInsnNode(INVOKESTATIC,
                        "ChibiArchiveServerTest", "connected", "(Ljava/lang/Object;)Z", false));
                else {
                    require(call.desc.equals("(Ljava/lang/String;I)V"), "Unexpected native connect call " + call.desc);
                    copy.instructions.add(new MethodInsnNode(INVOKESTATIC, "ChibiArchiveServerTest", "connect",
                            "(Ljava/lang/Object;Ljava/lang/String;I)V", false));
                }
            } else copy.instructions.add(insn.clone(labels));
        }
        ClassNode probe = new ClassNode(); probe.version = V1_3; probe.access = ACC_PUBLIC; probe.name = name; probe.superName = "java/lang/Object";
        MethodNode ctor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(ALOAD, 0)); ctor.instructions.add(new MethodInsnNode(INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false)); ctor.instructions.add(new InsnNode(RETURN)); probe.methods.add(ctor);
        for (String[] field : new String[][] { {"selected", "B"}, {"locale", "Z"}, {"list", "[[Ljava/lang/String;"}, {"version", "Ljava/lang/String;"} })
            probe.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, field[0], field[1], null, null));
        probe.methods.add(copy);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); probe.accept(writer);
        return new ProbeLoader(parent).define(name, writer.toByteArray());
    }

    private static Object[] runConnection(Class<?> probe, boolean language, String[][] rows, boolean active) throws Exception {
        probe.getField("selected").setByte(null, (byte)0); probe.getField("locale").setBoolean(null, language);
        probe.getField("list").set(null, rows); probe.getField("version").set(null, "unchanged");
        host = null; port = 0; connects = 0; connected = active;
        probe.getMethod("connect").invoke(null);
        return new Object[] { host, Integer.valueOf(port), probe.getField("version").get(null), Integer.valueOf(connects) };
    }

    private static void verifyMetadata(File inputFile, File candidateFile, Properties p,
            ClassLoader parent, URL microUrl) throws Exception {
        String owner = required(p, "connection.owner");
        URLClassLoader oldLoader = new URLClassLoader(new URL[] { inputFile.toURI().toURL(), microUrl }, parent);
        URLClassLoader newLoader = new URLClassLoader(new URL[] { candidateFile.toURI().toURL(), microUrl }, parent);
        try {
            Class<?> oldConfig = Class.forName(owner.replace('/', '.'), true, oldLoader);
            Class<?> newConfig = Class.forName(owner.replace('/', '.'), true, newLoader);
            int arrays = 0, portMetadata = 0;
            for (Field oldField : oldConfig.getDeclaredFields()) if (oldField.getType() == int[].class
                    && Modifier.isStatic(oldField.getModifiers())) {
                oldField.setAccessible(true); int[] old = (int[])oldField.get(null);
                if (old == null || old.length != 10) continue;
                Field newField = newConfig.getDeclaredField(oldField.getName()); newField.setAccessible(true);
                require(newField.getType() == int[].class && Modifier.isStatic(newField.getModifiers()), "Metadata field shape");
                int[] current = (int[])newField.get(null);
                require(current != null && current.length == 1 && current[0] == old[8],
                        "Port/language metadata reduced to selected row: " + oldField.getName());
                arrays++; if (current[0] == 19129) portMetadata++;
            }
            require(arrays == 2 && portMetadata == 1, "Exactly one port and one language metadata array became length one");
        } finally { oldLoader.close(); newLoader.close(); }
    }

    private static void verifyFreshRms(Map<String, byte[]> after, Properties p, ClassLoader loader) throws Exception {
        require("luna/generated/InvestigateStorage".equals(required(p, "rms.boundary")), "RMS boundary class");
        Common emulator = new Common(null); MemoryRecordStoreManager manager = new MemoryRecordStoreManager();
        manager.init(emulator); emulator.setRecordStoreManager(manager); MIDletBridge.setMicroEmulator(emulator);
        writeStore("inv_login", 99); writeStore("inv_mc_inv_v1", 77); writeStore("inv_ms_inv_v1", 66);
        Class<?> storage = Class.forName(required(p, "rms.boundary").replace('/', '.'), true, loader);
        require(storage.getMethod("load").invoke(null) == null && storage.getMethod("loadSelection").invoke(null) == null,
                "Legacy investigation RMS is not imported");
        RecordStore store = (RecordStore)storage.getMethod("openRecordStore", String.class, boolean.class)
                .invoke(null, "login", true);
        try { store.addRecord(new byte[] { 11 }, 0, 1); } finally { store.closeRecordStore(); }
        require(readStore("HSO_CHIBI_login") == 11 && readStore("inv_login") == 99, "Fresh CHIBI login RMS is isolated");
        store = (RecordStore)storage.getMethod("openRecordStore", String.class, boolean.class)
                .invoke(null, "HSO_CHIBI_login", false);
        try { require(store.getRecord(1)[0] == 11, "Prefix is idempotent"); } finally { store.closeRecordStore(); }
        String[] names = (String[])storage.getMethod("listRecordStores").invoke(null);
        require(names != null && Arrays.equals(names, new String[] { "login" }), "CHIBI RMS listing hides other suites");
        require(readStore("inv_mc_inv_v1") == 77 && readStore("inv_ms_inv_v1") == 66,
                "Legacy cache and selection RMS remain unchanged");
    }

    private static int verifyNewReferences(Map<String, byte[]> before, Map<String, byte[]> after,
            Set<String> changed, Set<String> added, ClassLoader loader) throws Exception {
        int references = 0;
        Set<String> examine = new LinkedHashSet<String>(changed); examine.addAll(added);
        for (String entry : examine) {
            ClassNode current = parse(after.get(entry));
            Set<String> oldRefs = before.containsKey(entry) ? references(before.get(entry)) : Collections.<String>emptySet();
            Class<?> caller = Class.forName(current.name.replace('/', '.'), false, loader);
            for (MethodNode method : current.methods) for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof FieldInsnNode && !oldRefs.contains(reference(insn))) {
                    FieldInsnNode use = (FieldInsnNode)insn;
                    Field found = findField(Class.forName(use.owner.replace('/', '.'), false, loader), use.name, use.desc);
                    require(found != null, "Unresolved field " + reference(insn));
                    boolean wantStatic = use.getOpcode() == GETSTATIC || use.getOpcode() == PUTSTATIC;
                    require(Modifier.isStatic(found.getModifiers()) == wantStatic, "Field invocation mode " + reference(insn));
                    checkAccess(caller, found.getDeclaringClass(), found.getModifiers(), reference(insn)); references++;
                } else if (insn instanceof MethodInsnNode && !oldRefs.contains(reference(insn))) {
                    MethodInsnNode use = (MethodInsnNode)insn;
                    Class<?> owner = Class.forName(use.owner.replace('/', '.'), false, loader);
                    if ("<init>".equals(use.name)) {
                        Constructor<?> found = null;
                        for (Constructor<?> constructor : owner.getDeclaredConstructors())
                            if (org.objectweb.asm.Type.getConstructorDescriptor(constructor).equals(use.desc)) found = constructor;
                        require(found != null, "Unresolved constructor " + reference(insn));
                        checkAccess(caller, owner, found.getModifiers(), reference(insn));
                    } else {
                        Method found = findMethod(owner, use.name, use.desc);
                        require(found != null, "Unresolved method " + reference(insn));
                        require(Modifier.isStatic(found.getModifiers()) == (use.getOpcode() == INVOKESTATIC),
                                "Method invocation mode " + reference(insn));
                        checkAccess(caller, found.getDeclaringClass(), found.getModifiers(), reference(insn));
                    }
                    references++;
                }
            }
        }
        return references;
    }

    private static void checkAccess(Class<?> caller, Class<?> owner, int flags, String member) {
        if (Modifier.isPublic(flags) || caller == owner) return;
        String callerPackage = packageName(caller), ownerPackage = packageName(owner);
        require(!Modifier.isPrivate(flags) && (callerPackage.equals(ownerPackage)
                || Modifier.isProtected(flags) && owner.isAssignableFrom(caller)), "Inaccessible member " + member);
    }

    private static Field findField(Class<?> type, String name, String desc) {
        if (type == null) return null;
        for (Field field : type.getDeclaredFields()) if (field.getName().equals(name)
                && org.objectweb.asm.Type.getDescriptor(field.getType()).equals(desc)) return field;
        Field found = findField(type.getSuperclass(), name, desc); if (found != null) return found;
        for (Class<?> parent : type.getInterfaces()) { found = findField(parent, name, desc); if (found != null) return found; }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, String desc) {
        if (type == null) return null;
        for (Method method : type.getDeclaredMethods()) if (method.getName().equals(name)
                && org.objectweb.asm.Type.getMethodDescriptor(method).equals(desc)) return method;
        Method found = findMethod(type.getSuperclass(), name, desc); if (found != null) return found;
        for (Class<?> parent : type.getInterfaces()) { found = findMethod(parent, name, desc); if (found != null) return found; }
        return null;
    }

    private static Set<String> references(byte[] bytes) {
        Set<String> result = new HashSet<String>(); ClassNode node = parse(bytes);
        for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray())
            if (insn instanceof FieldInsnNode || insn instanceof MethodInsnNode) result.add(reference(insn));
        return result;
    }

    private static String reference(AbstractInsnNode insn) {
        if (insn instanceof FieldInsnNode) { FieldInsnNode f = (FieldInsnNode)insn; return f.getOpcode() + " " + f.owner + "." + f.name + f.desc; }
        MethodInsnNode m = (MethodInsnNode)insn; return m.getOpcode() + " " + m.owner + "." + m.name + m.desc;
    }

    private static void checkManifest(byte[] oldBytes, byte[] currentBytes) throws Exception {
        require(oldBytes != null && currentBytes != null, "Manifest exists");
        Manifest oldManifest = new Manifest(new ByteArrayInputStream(oldBytes));
        Manifest currentManifest = new Manifest(new ByteArrayInputStream(currentBytes));
        Attributes old = oldManifest.getMainAttributes(), current = currentManifest.getMainAttributes();
        require("HSO CHIBI".equals(current.getValue("MIDlet-Name"))
                && "HSO CHIBI".equals(current.getValue("MIDlet-Vendor")), "CHIBI manifest identity");
        Set<Object> identity = new HashSet<Object>(Arrays.<Object>asList(
                new Attributes.Name("MIDlet-Name"), new Attributes.Name("MIDlet-Vendor"), new Attributes.Name("MIDlet-1")));
        for (Map.Entry<Object, Object> attr : old.entrySet()) if (!identity.contains(attr.getKey()))
            require(attr.getValue().equals(current.get(attr.getKey())),
                    "Unrelated manifest attribute changed: " + attr.getKey());
        for (Map.Entry<Object, Object> attr : current.entrySet()) if (!identity.contains(attr.getKey()))
            require(attr.getValue().equals(old.get(attr.getKey())),
                    "Unexpected manifest attribute added: " + attr.getKey());
        require(oldManifest.getEntries().equals(currentManifest.getEntries()), "Per-entry manifest sections preserved");
        String oldEntry = old.getValue("MIDlet-1"), entry = current.getValue("MIDlet-1");
        int oldComma = oldEntry == null ? -1 : oldEntry.indexOf(','), comma = entry == null ? -1 : entry.indexOf(',');
        require(oldComma > 0 && comma > 0 && entry.startsWith("HSO CHIBI,")
                && entry.substring(comma).equals(oldEntry.substring(oldComma)), "MIDlet entrypoint and remaining fields preserved");
    }

    private static void checkSoleRows(String[][] value, Properties p, String message) {
        require(value != null && value.length == 1 && value[0] != null && value[0].length == 2
                && required(p, "server.label").equals(value[0][0])
                && required(p, "server.host").equals(value[0][1]), message);
    }

    private static Object fieldsValue(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }

    private static void writeStore(String name, int value) throws Exception {
        RecordStore store = RecordStore.openRecordStore(name, true);
        try { store.addRecord(new byte[] { (byte)value }, 0, 1); } finally { store.closeRecordStore(); }
    }

    private static int readStore(String name) throws Exception {
        RecordStore store = RecordStore.openRecordStore(name, false);
        try { return store.getRecord(1)[0] & 255; } finally { store.closeRecordStore(); }
    }

    private static Properties properties(File file) throws IOException {
        Properties result = new Properties(); Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
        try { result.load(reader); } finally { reader.close(); } return result;
    }

    private static String required(Properties p, String name) {
        String value = p.getProperty(name); require(value != null && value.length() > 0, "Missing property " + name); return value;
    }

    private static int integer(Properties p, String name) { return Integer.parseInt(required(p, name)); }

    private static Set<String> entries(String value) {
        require(value != null, "Class list property"); Set<String> result = new LinkedHashSet<String>();
        if (value.length() == 0) return result;
        for (String part : value.split(",")) {
            String entry = part.trim();
            if (!entry.endsWith(".class")) entry = entry.replace('.', '/') + ".class";
            require(result.add(entry), "Duplicate class entry " + entry);
        }
        return result;
    }

    private static Map<String, byte[]> read(File file) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<String, byte[]>(); JarFile jar = new JarFile(file);
        try { for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
            JarEntry entry = entries.nextElement(); InputStream in = jar.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream();
            try { byte[] block = new byte[8192]; for (int n; (n = in.read(block)) != -1;) out.write(block, 0, n); }
            finally { in.close(); }
            require(!result.containsKey(entry.getName()), "Duplicate archive entry " + entry.getName());
            byte[] data = out.toByteArray(); CRC32 crc = new CRC32(); crc.update(data);
            require(crc.getValue() == entry.getCrc(), "Input CRC " + entry.getName()); result.put(entry.getName(), data);
        } } finally { jar.close(); } return result;
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); InputStream in = new FileInputStream(file);
        try { byte[] block = new byte[8192]; for (int n; (n = in.read(block)) != -1;) digest.update(block, 0, n); }
        finally { in.close(); }
        StringBuilder value = new StringBuilder(); for (byte b : digest.digest()) value.append(String.format("%02X", b & 255)); return value.toString();
    }

    private static ClassNode parse(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }

    private static int major(byte[] bytes) {
        require(bytes != null && bytes.length >= 8 && bytes[0] == (byte)0xCA && bytes[1] == (byte)0xFE
                && bytes[2] == (byte)0xBA && bytes[3] == (byte)0xBE, "Class header");
        return ((bytes[6] & 255) << 8) | (bytes[7] & 255);
    }

    private static String className(String entry) { return entry.substring(0, entry.length() - 6).replace('/', '.'); }

    private static AbstractInsnNode previousOpcode(AbstractInsnNode insn) {
        for (AbstractInsnNode current = insn.getPrevious(); current != null; current = current.getPrevious())
            if (current.getOpcode() >= 0) return current;
        return null;
    }

    private static Map<LabelNode, LabelNode> labelMap(MethodNode method) {
        Map<LabelNode, LabelNode> labels = new IdentityHashMap<LabelNode, LabelNode>();
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof LabelNode) labels.put((LabelNode)insn, new LabelNode());
        return labels;
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) if (method.name.equals(name) && method.desc.equals(desc)) return method;
        throw new IllegalStateException("Method missing " + node.name + "." + name + desc);
    }

    private static String packageName(Class<?> type) {
        String name = type.getName(); int dot = name.lastIndexOf('.'); return dot < 0 ? "" : name.substring(0, dot);
    }

    private static void require(boolean okay, String message) { checks++; if (!okay) throw new IllegalStateException(message); }
}
