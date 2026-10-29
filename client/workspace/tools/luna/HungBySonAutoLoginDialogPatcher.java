import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keeps the native reconnect fallback from acting on connected-session dialogs. */
public final class HungBySonAutoLoginDialogPatcher implements Opcodes {
    static final String INPUT_SHA256 = "D6F2489636C66EAE3AD1A959BB6FF4E428EE4BD16740EE2BBE22D575354DCFE1";
    static String n(String suffix) {
        StringBuilder result = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) result.append('i');
        return result.append(suffix).toString();
    }
    static final String AUTO = n("lliiiill"), TICK = n("lliliiii");
    static final String AUTO_ENABLED = n("lililliilii"), SCAN_COUNTER = n("lililliliii");
    static final String RETRY_DEADLINE = n("lililliilll"), RETRY_ATTEMPTS = n("lililliilli");
    static final String CAPTURE_USER = n("lilillillii");
    static final String ENGINE = n("liilllii"), CURRENT_SCREEN = n("liiiillllli");
    static final String ACTIVE_DIALOG = n("liiililiili"), GAME_SCREEN = n("liiiliiiiil");
    static final String LOGIN_SCREEN = n("liiiliiiiii"), LOADING_SCREEN = n("liiiliiiili");
    static final String SELECT_SCREEN = n("liiiliiilll");
    static final String DIALOG = n("llil"), DIALOG_BASE = n("lililii"), DIALOG_LINES = n("lllliliii");
    static final String LOGIN = n("lliill"), NATIVE_SHOW = n("lil"), TABS = n("liiiiili");
    static final String SELECT = n("lillilli"), WORLD_ENTERED = n("liiili");
    static final String CONNECTION = n("lililili"), CONNECTION_INSTANCE = n("liiliililli");
    static final String CONNECTED_GETTER = n("liiliill"), CONNECTED_FIELD = n("liiiiilli");
    static final String COMBAT = n("lliillil"), COMBAT_ENABLED = n("liilliliili");
    static final String FARM = n("lliiiiil"), FARM_ENABLED = n("lililiilill");
    static final String NORMALIZER = n("lliiliil"), NORMALIZE = n("lllil");
    static final String DISCONNECT_MATCHER = n("lliillii");
    static final String LOADING = n("lllilli"), SCREEN_BASE = n("lilillli");
    static final String WAIT_OWNER = n("llilil"), WAIT_FLAG = n("liiillli");

    static void require(boolean okay, String message) {
        if (!okay) throw new IllegalStateException(message);
    }
    static byte[] bytes(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] block = new byte[8192];
            for (int size; (size = input.read(block)) != -1;) output.write(block, 0, size);
            return output.toByteArray();
        } finally { input.close(); }
    }
    static String hash(byte[] data) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(data))
            result.append(String.format("%02X", value & 255));
        return result.toString();
    }
    static ClassNode cls(byte[] bytes) {
        require(bytes != null, "Missing class bytes");
        ClassNode result = new ClassNode(ASM9);
        new ClassReader(bytes).accept(result, 0);
        return result;
    }
    static MethodNode method(ClassNode owner, String name, String descriptor) {
        for (MethodNode method : owner.methods)
            if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        throw new IllegalStateException("Missing method " + owner.name + "." + name + descriptor);
    }
    static FieldNode field(ClassNode owner, String name, String descriptor) {
        for (FieldNode field : owner.fields)
            if (field.name.equals(name) && field.desc.equals(descriptor)) return field;
        throw new IllegalStateException("Missing field " + owner.name + "." + name + descriptor);
    }
    static void staticField(Map<String, byte[]> entries, String owner, String name, String descriptor) {
        FieldNode member = field(cls(entries.get(owner + ".class")), name, descriptor);
        require((member.access & ACC_STATIC) != 0, "Expected static field " + owner + "." + name);
        require(owner.equals(AUTO) || (member.access & ACC_PUBLIC) != 0, "Inaccessible field");
    }
    static AbstractInsnNode next(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }
    static boolean member(AbstractInsnNode instruction, int opcode, String owner, String name, String desc) {
        if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != opcode) return false;
        FieldInsnNode field = (FieldInsnNode) instruction;
        return field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(desc);
    }
    static void catalog(Properties p, String key, String owner, String member, String descriptor) {
        p.setProperty(key + ".owner", owner);
        p.setProperty(key + ".member", member);
        p.setProperty(key + ".descriptor", descriptor);
    }
    static byte[] isolatedMethod(ClassNode owner, MethodNode method) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(owner.version, owner.access, owner.name, owner.signature, owner.superName,
                owner.interfaces.toArray(new String[owner.interfaces.size()]));
        method.accept(writer.visitMethod(method.access, method.name, method.desc, method.signature,
                method.exceptions.toArray(new String[method.exceptions.size()])));
        writer.visitEnd();
        return writer.toByteArray();
    }
    static InsnList guard() {
        InsnList result = new InsnList();
        LabelNode nativeFallback = new LabelNode(), connectionPresent = new LabelNode();
        result.add(new FieldInsnNode(GETSTATIC, SELECT, WORLD_ENTERED, "Z"));
        result.add(new JumpInsnNode(IFEQ, nativeFallback));
        String[] screens = {LOGIN_SCREEN, LOADING_SCREEN, SELECT_SCREEN};
        String[] types = {LOGIN, LOADING, SELECT};
        for (int i = 0; i < screens.length; i++) {
            result.add(new FieldInsnNode(GETSTATIC, ENGINE, CURRENT_SCREEN, "L" + SCREEN_BASE + ";"));
            result.add(new FieldInsnNode(GETSTATIC, ENGINE, screens[i], "L" + types[i] + ";"));
            result.add(new JumpInsnNode(IF_ACMPEQ, nativeFallback));
        }
        result.add(new FieldInsnNode(GETSTATIC, CONNECTION, CONNECTION_INSTANCE, "L" + CONNECTION + ";"));
        result.add(new InsnNode(DUP));
        result.add(new JumpInsnNode(IFNONNULL, connectionPresent));
        result.add(new InsnNode(POP));
        result.add(new JumpInsnNode(GOTO, nativeFallback));
        result.add(connectionPresent);
        result.add(new MethodInsnNode(INVOKEVIRTUAL, CONNECTION, CONNECTED_GETTER, "()Z", false));
        result.add(new JumpInsnNode(IFEQ, nativeFallback));
        result.add(new InsnNode(ICONST_0));
        result.add(new FieldInsnNode(PUTSTATIC, AUTO, SCAN_COUNTER, "I"));
        result.add(new InsnNode(ICONST_0));
        result.add(new FieldInsnNode(PUTSTATIC, AUTO, RETRY_ATTEMPTS, "I"));
        result.add(new InsnNode(RETURN));
        result.add(nativeFallback);
        return result;
    }
    public static void main(String[] args) throws Exception {
        require(args.length == 4, "input.jar output.jar catalog.properties report.txt");
        File input = new File(args[0]), output = new File(args[1]);
        require(!output.exists(), "Output must be a new file");
        require(!input.getCanonicalFile().equals(output.getCanonicalFile()), "Input cannot be output");
        require(hash(bytes(new FileInputStream(input))).equals(INPUT_SHA256), "Unexpected input SHA-256");
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        Map<String, Long> timestamps = new HashMap<String, Long>();
        JarFile jar = new JarFile(input);
        try {
            for (Enumeration<JarEntry> iterator = jar.entries(); iterator.hasMoreElements();) {
                JarEntry entry = iterator.nextElement();
                require(!entries.containsKey(entry.getName()), "Duplicate archive entry");
                entries.put(entry.getName(), bytes(jar.getInputStream(entry)));
                timestamps.put(entry.getName(), entry.getTime());
            }
        } finally { jar.close(); }
        staticField(entries, SELECT, WORLD_ENTERED, "Z");
        staticField(entries, ENGINE, CURRENT_SCREEN, "L" + SCREEN_BASE + ";");
        staticField(entries, ENGINE, ACTIVE_DIALOG, "L" + DIALOG_BASE + ";");
        staticField(entries, ENGINE, LOGIN_SCREEN, "L" + LOGIN + ";");
        staticField(entries, ENGINE, LOADING_SCREEN, "L" + LOADING + ";");
        staticField(entries, ENGINE, SELECT_SCREEN, "L" + SELECT + ";");
        staticField(entries, CONNECTION, CONNECTION_INSTANCE, "L" + CONNECTION + ";");
        staticField(entries, AUTO, SCAN_COUNTER, "I");
        staticField(entries, AUTO, RETRY_ATTEMPTS, "I");
        ClassNode connection = cls(entries.get(CONNECTION + ".class"));
        MethodNode connected = method(connection, CONNECTED_GETTER, "()Z");
        require((connected.access & ACC_PUBLIC) != 0 && (connected.access & ACC_STATIC) == 0,
                "Connected accessor visibility/mode");
        require(member(next(connected.instructions.getFirst()), GETFIELD, CONNECTION, CONNECTED_FIELD, "Z"),
                "Connected accessor no longer reads audited native field");
        ClassNode owner = cls(entries.get(AUTO + ".class"));
        require(owner.version == V1_4, "Unexpected native Auto Login class version");
        MethodNode tick = method(owner, TICK, "()V");
        require((tick.access & ACC_STATIC) != 0, "Auto Login tick must be static");
        AbstractInsnNode insertion = null;
        int sites = 0, lossCalls = 0;
        for (AbstractInsnNode instruction : tick.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(AUTO) && call.name.equals(DISCONNECT_MATCHER) && call.desc.equals("()Z")) lossCalls++;
            }
            if (!member(instruction, GETSTATIC, ENGINE, ACTIVE_DIALOG, "L" + DIALOG_BASE + ";")) continue;
            AbstractInsnNode branch = next(instruction), after = branch == null ? null : next(branch);
            if (branch instanceof JumpInsnNode && branch.getOpcode() == IFNULL
                    && member(after, GETSTATIC, WAIT_OWNER, WAIT_FLAG, "Z")) {
                insertion = after;
                sites++;
            }
        }
        require(sites == 1 && lossCalls == 1, "Audited generic-modal/loss-handler structure changed");
        Map<String, byte[]> unchangedMethods = new LinkedHashMap<String, byte[]>();
        for (MethodNode nativeMethod : owner.methods) if (nativeMethod != tick)
            unchangedMethods.put(nativeMethod.name + nativeMethod.desc, isolatedMethod(owner, nativeMethod));
        int originalMethodCount = owner.methods.size(), originalFieldCount = owner.fields.size();
        tick.instructions.insertBefore(insertion, guard());
        ClassWriter writer = new ClassWriter(new ClassReader(entries.get(AUTO + ".class")), ClassWriter.COMPUTE_MAXS);
        owner.accept(writer);
        byte[] patchedClass = writer.toByteArray();
        ClassNode roundTrip = cls(patchedClass);
        require(roundTrip.methods.size() == originalMethodCount && roundTrip.fields.size() == originalFieldCount,
                "Native Auto Login interface changed");
        int preservedMethods = 0;
        for (MethodNode nativeMethod : roundTrip.methods) {
            byte[] expected = unchangedMethods.get(nativeMethod.name + nativeMethod.desc);
            if (expected == null) continue;
            require(Arrays.equals(expected, isolatedMethod(roundTrip, nativeMethod)),
                    "Unexpected change outside Auto Login tick: " + nativeMethod.name + nativeMethod.desc);
            preservedMethods++;
        }
        require(preservedMethods == unchangedMethods.size(), "Missing native methods");
        entries.put(AUTO + ".class", patchedClass);
        File parent = output.getAbsoluteFile().getParentFile();
        require(parent.isDirectory() || parent.mkdirs(), "Cannot create build directory");
        JarOutputStream stream = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                JarEntry packed = new JarEntry(entry.getKey());
                packed.setTime(timestamps.get(entry.getKey()).longValue());
                stream.putNextEntry(packed);
                stream.write(entry.getValue());
                stream.closeEntry();
            }
        } finally { stream.close(); }
        String outputHash = hash(bytes(new FileInputStream(output)));
        Properties catalog = new Properties();
        catalog.setProperty("input.sha256", INPUT_SHA256);
        catalog.setProperty("output.sha256", outputHash);
        catalog.setProperty("changed.classes", AUTO);
        catalog(catalog, "auto.tick", AUTO, TICK, "()V");
        catalog(catalog, "auto.enabled", AUTO, AUTO_ENABLED, "Z");
        catalog(catalog, "auto.scanCounter", AUTO, SCAN_COUNTER, "I");
        catalog(catalog, "auto.unknownRetries", AUTO, RETRY_ATTEMPTS, "I");
        catalog(catalog, "auto.retryDeadline", AUTO, RETRY_DEADLINE, "J");
        catalog(catalog, "native.connection.instance", CONNECTION, CONNECTION_INSTANCE, "L" + CONNECTION + ";");
        catalog(catalog, "native.connection.connected", CONNECTION, CONNECTED_GETTER, "()Z");
        catalog(catalog, "native.world.entered", SELECT, WORLD_ENTERED, "Z");
        catalog(catalog, "ui.currentScreen", ENGINE, CURRENT_SCREEN, "L" + SCREEN_BASE + ";");
        catalog(catalog, "ui.activeDialog", ENGINE, ACTIVE_DIALOG, "L" + DIALOG_BASE + ";");
        FileOutputStream properties = new FileOutputStream(args[2]);
        try { catalog.store(properties, "Hung By Son connected-session Auto Login dialog guard"); }
        finally { properties.close(); }
        PrintWriter report = new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[3]), "UTF-8"));
        try {
            report.println("INPUT_SHA256=" + INPUT_SHA256);
            report.println("OUTPUT_SHA256=" + outputHash);
            report.println("CHANGED_CLASS=" + AUTO);
            report.println("CHANGED_METHOD=" + TICK + "()V");
            report.println("UNCHANGED_NATIVE_METHODS=" + preservedMethods);
            report.println("GUARD=generic active-dialog fallback only; post-world connected gameplay UI returns without clicking any modal");
            report.println("RESET=popup scan counter and unknown-notice streak; retry deadline and captured credentials preserved");
            report.println("PRESERVED=native post-login popup, explicit loss-notice handler, offline retry, Login/CharSelect/loading flow, all other classes/resources");
            report.println("LIVE_SERVER=not exercised");
        } finally { report.close(); }
        System.out.println("AUTO_LOGIN_DIALOG_PATCH=BUILT " + outputHash);
    }
}
