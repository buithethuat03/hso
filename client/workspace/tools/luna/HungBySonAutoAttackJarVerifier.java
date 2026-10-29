import java.io.*;
import java.net.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import java.util.zip.CRC32;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Independent archive/linkage verifier for the pinned Hung extension. */
public final class HungBySonAutoAttackJarVerifier {
    public static void main(String[] args) throws Exception {
        require(args.length == 5, "input.jar candidate.jar microemulator.jar catalog.properties report.txt");
        File input = new File(args[0]), candidate = new File(args[1]);
        Properties catalog = new Properties();
        InputStream config = new FileInputStream(args[3]);
        try { catalog.load(config); } finally { config.close(); }
        require(hash(input).equals(catalog.getProperty("input.sha256")), "source hash");
        require(hash(candidate).equals(catalog.getProperty("output.sha256")), "candidate hash");
        require("PASS".equals(catalog.getProperty("native.scope.inverse")), "native hook inverse");
        Map<String, byte[]> old = read(input), actual = read(candidate);
        Set<String> changed = classes(catalog.getProperty("changed.classes"));
        Set<String> added = classes(catalog.getProperty("added.classes"));
        Set<String> expected = new LinkedHashSet<String>(old.keySet());
        for (String entry : added) { require(!old.containsKey(entry), "addition collision " + entry); expected.add(entry); }
        require(expected.equals(actual.keySet()), "exact source plus additions topology");
        require(!changed.isEmpty(), "nonempty modification list");
        for (String entry : changed) require(old.containsKey(entry) && actual.containsKey(entry), "modified entry exists");
        int resources = 0, classes = 0, maximumMajor = 0;
        for (String entry : actual.keySet()) {
            byte[] now = actual.get(entry), before = old.get(entry);
            if (entry.endsWith(".class")) {
                classes++; ClassNode node = parse(now); maximumMajor = Math.max(maximumMajor, node.version & 65535);
                require((node.version & 65535) <= 51, "class version " + entry);
                if (before != null) {
                    require(parse(before).version == node.version, "preserved native class version " + entry);
                    require(changed.contains(entry) != Arrays.equals(before, now), "complete modification list " + entry);
                }
            } else { resources++; require(before != null && Arrays.equals(before, now), "unchanged resource/manifest " + entry); }
        }
        URLClassLoader loader = new URLClassLoader(new URL[] {candidate.toURI().toURL(), new File(args[2]).toURI().toURL()}, null);
        int linked = 0, references = 0;
        try {
            for (String entry : actual.keySet()) if (entry.endsWith(".class")) {
                Class<?> type = Class.forName(entry.substring(0, entry.length() - 6).replace('/', '.'), false, loader);
                type.getDeclaredFields(); type.getDeclaredMethods(); type.getDeclaredConstructors(); linked++;
            }
            Set<String> examine = new LinkedHashSet<String>(changed); examine.addAll(added);
            for (String entry : examine) {
                ClassNode node = parse(actual.get(entry));
                Set<String> previous = old.containsKey(entry) ? references(parse(old.get(entry))) : Collections.<String>emptySet();
                Class<?> caller = Class.forName(node.name.replace('/', '.'), false, loader);
                for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode && !previous.contains(reference(insn))) {
                        FieldInsnNode use = (FieldInsnNode) insn;
                        Field resolved = findField(Class.forName(use.owner.replace('/', '.'), false, loader), use.name, use.desc);
                        require(resolved != null, "unresolved field " + reference(insn));
                        require(Modifier.isStatic(resolved.getModifiers()) == (use.getOpcode() == Opcodes.GETSTATIC || use.getOpcode() == Opcodes.PUTSTATIC), "field mode " + reference(insn));
                        access(caller, resolved.getDeclaringClass(), resolved.getModifiers(), reference(insn)); references++;
                    } else if (insn instanceof MethodInsnNode && !previous.contains(reference(insn))) {
                        MethodInsnNode use = (MethodInsnNode) insn;
                        Class<?> owner = Class.forName(use.owner.replace('/', '.'), false, loader);
                        if ("<init>".equals(use.name)) {
                            Constructor<?> resolved = null;
                            for (Constructor<?> constructor : owner.getDeclaredConstructors())
                                if (Type.getConstructorDescriptor(constructor).equals(use.desc)) resolved = constructor;
                            require(resolved != null, "unresolved constructor " + reference(insn));
                            access(caller, owner, resolved.getModifiers(), reference(insn));
                        } else {
                            Method resolved = findMethod(owner, use.name, use.desc);
                            require(resolved != null, "unresolved method " + reference(insn));
                            require(Modifier.isStatic(resolved.getModifiers()) == (use.getOpcode() == Opcodes.INVOKESTATIC), "method mode " + reference(insn));
                            access(caller, resolved.getDeclaringClass(), resolved.getModifiers(), reference(insn));
                        }
                        references++;
                    }
                }
            }
        } finally { loader.close(); }
        require(linked == classes, "all classes linked");
        PrintWriter report = new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[4]), "UTF-8"));
        try {
            report.println("INPUT_SHA256=" + hash(input)); report.println("OUTPUT_SHA256=" + hash(candidate));
            report.println("LINKED_CLASSES=" + linked); report.println("NEW_REFERENCES_RESOLVED=" + references);
            report.println("UNCHANGED_RESOURCES=" + resources); report.println("MAX_MAJOR=" + maximumMajor);
            report.println("PASS=CRC, archive delta, native versions, -Xverify:all linkage, new member descriptors and access, unchanged resources/manifest");
        } finally { report.close(); }
        System.out.println("AUTO_ATTACK_JAR=PASS; linked=" + linked + "; references=" + references + "; resources=" + resources);
    }
    private static void access(Class<?> caller, Class<?> owner, int flags, String member) {
        if (Modifier.isPublic(flags) || caller == owner) return;
        require(!Modifier.isPrivate(flags) && (packageName(caller).equals(packageName(owner))
                || Modifier.isProtected(flags) && owner.isAssignableFrom(caller)), "inaccessible member " + member);
    }
    private static String packageName(Class<?> type) { String name = type.getName(); int dot = name.lastIndexOf('.'); return dot < 0 ? "" : name.substring(0, dot); }
    private static Field findField(Class<?> type, String name, String desc) {
        if (type == null) return null;
        for (Field field : type.getDeclaredFields()) if (field.getName().equals(name) && Type.getDescriptor(field.getType()).equals(desc)) return field;
        Field found = findField(type.getSuperclass(), name, desc); if (found != null) return found;
        for (Class<?> parent : type.getInterfaces()) { found = findField(parent, name, desc); if (found != null) return found; }
        return null;
    }
    private static Method findMethod(Class<?> type, String name, String desc) {
        if (type == null) return null;
        for (Method method : type.getDeclaredMethods()) if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(desc)) return method;
        Method found = findMethod(type.getSuperclass(), name, desc); if (found != null) return found;
        for (Class<?> parent : type.getInterfaces()) { found = findMethod(parent, name, desc); if (found != null) return found; }
        return null;
    }
    private static Set<String> references(ClassNode node) {
        Set<String> values = new HashSet<String>();
        for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray())
            if (insn instanceof FieldInsnNode || insn instanceof MethodInsnNode) values.add(reference(insn));
        return values;
    }
    private static String reference(AbstractInsnNode insn) {
        if (insn instanceof FieldInsnNode) { FieldInsnNode use = (FieldInsnNode) insn; return use.getOpcode() + " " + use.owner + "." + use.name + use.desc; }
        MethodInsnNode use = (MethodInsnNode) insn; return use.getOpcode() + " " + use.owner + "." + use.name + use.desc;
    }
    private static Set<String> classes(String value) {
        require(value != null, "catalog class list"); Set<String> result = new LinkedHashSet<String>();
        if (value.length() == 0) return result;
        for (String name : value.split(",")) { String entry = name.trim(); if (!entry.endsWith(".class")) entry = entry.replace('.', '/') + ".class";
            require(entry.endsWith(".class") && result.add(entry), "unique class entries"); }
        return result;
    }
    private static ClassNode parse(byte[] data) { ClassNode node = new ClassNode(); new ClassReader(data).accept(node, 0); return node; }
    private static Map<String, byte[]> read(File file) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<String, byte[]>(); JarFile jar = new JarFile(file);
        try { for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
            JarEntry entry = entries.nextElement(); InputStream in = jar.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream();
            try { byte[] block = new byte[8192]; int size; while ((size = in.read(block)) != -1) out.write(block, 0, size); } finally { in.close(); }
            byte[] data = out.toByteArray(); CRC32 crc = new CRC32(); crc.update(data);
            require(crc.getValue() == entry.getCrc() && !result.containsKey(entry.getName()), "CRC/duplicate " + entry.getName()); result.put(entry.getName(), data);
        } } finally { jar.close(); } return result;
    }
    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); InputStream in = new FileInputStream(file);
        try { byte[] block = new byte[8192]; int count; while ((count = in.read(block)) != -1) digest.update(block, 0, count); } finally { in.close(); }
        StringBuilder result = new StringBuilder(); for (byte value : digest.digest()) result.append(String.format("%02X", value & 255)); return result.toString();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
