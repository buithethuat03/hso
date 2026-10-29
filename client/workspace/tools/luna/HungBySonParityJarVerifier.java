import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.CRC32;
import java.lang.reflect.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Release boundary checks, independent of the two feature patchers. */
public final class HungBySonParityJarVerifier {
    public static void main(String[] args) throws Exception {
        if (args.length < 5) throw new IllegalArgumentException(
                "input.jar candidate.jar microemulator.jar report.txt allowed-class ...");
        File before = new File(args[0]), after = new File(args[1]);
        Set<String> allowed = new LinkedHashSet<String>();
        for (int i = 4; i < args.length; i++) allowed.add(args[i].endsWith(".class")
                ? args[i] : args[i].replace('.', '/') + ".class");
        Map<String, byte[]> old = read(before), actual = read(after);
        require(old.keySet().equals(actual.keySet()), "archive topology changed");
        List<String> changed = new ArrayList<String>();
        int classes = 0, resources = 0, maxMajor = 0;
        Map<Integer, Integer> versions = new TreeMap<Integer, Integer>();
        for (String name : old.keySet()) {
            byte[] a = old.get(name), b = actual.get(name);
            if (!Arrays.equals(a, b)) {
                require(name.endsWith(".class") && allowed.contains(name), "unexpected modification: " + name);
                changed.add(name);
            }
            if (name.endsWith(".class")) {
                classes++;
                int major = major(b);
                require(major(a) == major, "class version changed: " + name);
                require(major <= 51, "non-CLDC class version: " + name);
                maxMajor = Math.max(maxMajor, major);
                Integer count = versions.get(Integer.valueOf(major));
                versions.put(Integer.valueOf(major), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
            } else if (!name.endsWith("/")) resources++;
        }
        require(!changed.isEmpty(), "no features were patched");
        URLClassLoader loader = new URLClassLoader(new URL[]{after.toURI().toURL(), new File(args[2]).toURI().toURL()}, null);
        int linked = 0, newReferences = 0;
        try {
            for (String name : actual.keySet()) if (name.endsWith(".class")) {
                Class<?> c = Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false, loader);
                c.getDeclaredMethods(); c.getDeclaredFields(); c.getDeclaredConstructors(); linked++;
            }
            for (String name : changed) {
                Set<String> previous = references(old.get(name));
                ClassNode node = new ClassNode(); new ClassReader(actual.get(name)).accept(node, 0);
                for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode && !previous.contains(reference(insn))) {
                        FieldInsnNode f = (FieldInsnNode) insn;
                        Field resolved = resolveField(Class.forName(f.owner.replace('/', '.'), false, loader), f.name, f.desc);
                        require(resolved != null, "missing new field reference: " + f.owner + "." + f.name + f.desc);
                        boolean wantStatic = f.getOpcode() == Opcodes.GETSTATIC || f.getOpcode() == Opcodes.PUTSTATIC;
                        require(Modifier.isStatic(resolved.getModifiers()) == wantStatic, "field static mode mismatch");
                        require(!Modifier.isPrivate(resolved.getModifiers()) || node.name.replace('/', '.').equals(resolved.getDeclaringClass().getName()), "inaccessible new private field");
                        newReferences++;
                    } else if (insn instanceof MethodInsnNode && !previous.contains(reference(insn))) {
                        MethodInsnNode m = (MethodInsnNode) insn;
                        Class<?> owner = Class.forName(m.owner.replace('/', '.'), false, loader);
                        if ("<init>".equals(m.name)) {
                            boolean found = false;
                            for (Constructor<?> constructor : owner.getDeclaredConstructors()) if (org.objectweb.asm.Type.getConstructorDescriptor(constructor).equals(m.desc)) found = true;
                            require(found, "missing new constructor reference");
                        } else {
                            Method resolved = resolveMethod(owner, m.name, m.desc);
                            require(resolved != null, "missing new method reference: " + m.owner + "." + m.name + m.desc);
                            require(Modifier.isStatic(resolved.getModifiers()) == (m.getOpcode() == Opcodes.INVOKESTATIC), "method static mode mismatch");
                            require(!Modifier.isPrivate(resolved.getModifiers()) || node.name.replace('/', '.').equals(resolved.getDeclaringClass().getName()), "inaccessible new private method");
                        }
                        newReferences++;
                    }
                }
            }
        } finally { loader.close(); }
        require(linked == classes, "incomplete linking");
        PrintWriter report = new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[3]), "UTF-8"));
        try {
            report.println("INPUT_SHA256=" + hash(before));
            report.println("CANDIDATE_SHA256=" + hash(after));
            report.println("ENTRIES=" + actual.size());
            report.println("CLASSES=" + classes);
            report.println("RESOURCES=" + resources);
            report.println("CLASS_VERSIONS=" + versions);
            report.println("MAX_MAJOR=" + maxMajor);
            report.println("LINKED_CLASSES=" + linked);
            report.println("NEW_REFERENCES_RESOLVED=" + newReferences);
            for (String name : changed) report.println("CHANGED=" + name);
            report.println("PASS=ZIP CRC, exact entry topology, identical resources and manifest, modification allow-list, preserved class versions, -Xverify:all reflection linkage, new field/method references and private access");
            report.println("LIVE_SERVER=not exercised");
        } finally { report.close(); }
        System.out.println("ARCHIVE_BOUNDARY=PASS; entries=" + actual.size() + "; changed=" + changed.size() + "; linked=" + linked);
    }
    private static Set<String> references(byte[] bytes) {
        Set<String> refs = new HashSet<String>(); ClassNode c = new ClassNode(); new ClassReader(bytes).accept(c, 0);
        for (MethodNode m : c.methods) for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof FieldInsnNode || i instanceof MethodInsnNode) refs.add(reference(i));
        return refs;
    }
    private static String reference(AbstractInsnNode insn) {
        if (insn instanceof FieldInsnNode) { FieldInsnNode f = (FieldInsnNode) insn; return f.getOpcode() + " " + f.owner + "." + f.name + " " + f.desc; }
        MethodInsnNode m = (MethodInsnNode) insn; return m.getOpcode() + " " + m.owner + "." + m.name + m.desc;
    }
    private static Field resolveField(Class<?> type, String name, String desc) {
        if (type == null) return null;
        for (Field f : type.getDeclaredFields()) if (f.getName().equals(name) && org.objectweb.asm.Type.getDescriptor(f.getType()).equals(desc)) return f;
        Field inherited = resolveField(type.getSuperclass(), name, desc); if (inherited != null) return inherited;
        for (Class<?> parent : type.getInterfaces()) { inherited = resolveField(parent, name, desc); if (inherited != null) return inherited; }
        return null;
    }
    private static Method resolveMethod(Class<?> type, String name, String desc) {
        if (type == null) return null;
        for (Method m : type.getDeclaredMethods()) if (m.getName().equals(name) && org.objectweb.asm.Type.getMethodDescriptor(m).equals(desc)) return m;
        Method inherited = resolveMethod(type.getSuperclass(), name, desc); if (inherited != null) return inherited;
        for (Class<?> parent : type.getInterfaces()) { inherited = resolveMethod(parent, name, desc); if (inherited != null) return inherited; }
        return null;
    }
    private static Map<String, byte[]> read(File file) throws Exception {
        Map<String, byte[]> data = new LinkedHashMap<String, byte[]>();
        JarFile jar = new JarFile(file);
        try {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                InputStream in = jar.getInputStream(entry);
                try { byte[] block = new byte[8192]; for (int n; (n = in.read(block)) != -1;) out.write(block, 0, n); }
                finally { in.close(); }
                byte[] bytes = out.toByteArray();
                CRC32 crc = new CRC32(); crc.update(bytes);
                require(crc.getValue() == entry.getCrc(), "CRC mismatch: " + entry.getName());
                require(!data.containsKey(entry.getName()), "duplicate entry: " + entry.getName());
                data.put(entry.getName(), bytes);
            }
        } finally { jar.close(); }
        return data;
    }
    private static int major(byte[] bytes) {
        require(bytes.length >= 8 && bytes[0] == (byte)0xCA && bytes[1] == (byte)0xFE
                && bytes[2] == (byte)0xBA && bytes[3] == (byte)0xBE, "invalid class header");
        return ((bytes[6] & 255) << 8) | (bytes[7] & 255);
    }
    private static String hash(File file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder result = new StringBuilder();
        for (byte b : digest) result.append(String.format("%02X", Integer.valueOf(b & 255)));
        return result.toString();
    }
    private static void require(boolean okay, String message) { if (!okay) throw new IllegalStateException(message); }
}
