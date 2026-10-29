import java.io.*;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Copies one server row from the donor, preserving all existing target indices. */
public final class HungBySonServerListPatcher implements Opcodes {
    static final String LABEL = "Bạch Hổ";
    static class Config {
        String owner, field;
        String[][] rows;
        byte[] bytes;
    }
    static class Metadata {
        String owner;
        byte[] bytes;
        Map<String, int[]> arrays = new LinkedHashMap<String, int[]>();
    }
    static Metadata metadata(File file, int count) throws Exception {
        Metadata result = null;
        try (JarFile jar = new JarFile(file)) {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                byte[] bytes = read(jar.getInputStream(entry)); ClassNode node = parse(bytes);
                boolean marker = false;
                for (MethodNode m : node.methods) for (AbstractInsnNode i : m.instructions) {
                    if (i instanceof LdcInsnNode && "http://knightageonline.com/srvip/".equals(((LdcInsnNode)i).cst)) marker = true;
                }
                if (!marker) continue;
                Metadata found = new Metadata(); found.owner = node.name; found.bytes = bytes;
                for (MethodNode method : node.methods) if ("<clinit>".equals(method.name)) {
                    for (AbstractInsnNode i : method.instructions) {
                        if (!(i instanceof IntInsnNode) || i.getOpcode() != NEWARRAY || ((IntInsnNode)i).operand != T_INT
                                || integer(prev(i)) != count) continue;
                        int[] values = new int[count]; AbstractInsnNode n = next(i);
                        while (n != null && n.getOpcode() == DUP) {
                            AbstractInsnNode index = next(n), value = next(index), store = next(value);
                            if (integer(index) < 0 || integer(index) >= count || integer(value) == Integer.MIN_VALUE
                                    || store.getOpcode() != IASTORE) throw new IllegalStateException("Unexpected metadata initializer");
                            values[integer(index)] = integer(value); n = next(store);
                        }
                        if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTSTATIC || !"[I".equals(((FieldInsnNode)n).desc)) throw new IllegalStateException("Metadata store missing");
                        found.arrays.put(((FieldInsnNode)n).name, values);
                    }
                }
                if (found.arrays.size() != 2 || result != null) throw new IllegalStateException("Expected port/language arrays");
                result = found;
            }
        }
        if (result == null) throw new IllegalStateException("Server metadata missing");
        return result;
    }
    static byte[] patchMetadata(Metadata target, Metadata donor, int donorIndex, int count) {
        if (!target.owner.equals(donor.owner) || !target.arrays.keySet().equals(donor.arrays.keySet())) throw new IllegalStateException("Metadata symbols differ");
        ClassNode node = parse(target.bytes); int changed = 0;
        for (MethodNode method : node.methods) if ("<clinit>".equals(method.name)) {
            for (AbstractInsnNode i : method.instructions.toArray()) {
                if (!(i instanceof FieldInsnNode) || i.getOpcode() != PUTSTATIC) continue;
                FieldInsnNode field = (FieldInsnNode)i;
                if (!target.owner.equals(field.owner) || !target.arrays.containsKey(field.name) || !"[I".equals(field.desc)) continue;
                AbstractInsnNode allocation = prev(i);
                while (allocation != null && allocation.getOpcode() != NEWARRAY) allocation = prev(allocation);
                if (allocation == null || integer(prev(allocation)) != count) throw new IllegalStateException("Metadata allocation missing");
                method.instructions.set(prev(allocation), push(count + 1));
                InsnList add = new InsnList(); add.add(new InsnNode(DUP)); add.add(push(count));
                add.add(push(donor.arrays.get(field.name)[donorIndex])); add.add(new InsnNode(IASTORE));
                method.instructions.insertBefore(i, add); changed++;
                System.out.println("METADATA=" + field.name + " appended=" + donor.arrays.get(field.name)[donorIndex]);
            }
        }
        if (changed != 2) throw new IllegalStateException("Expected two metadata changes");
        ClassWriter writer = new ClassWriter(new ClassReader(target.bytes), 0); node.accept(writer); return writer.toByteArray();
    }
    static byte[] read(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] block = new byte[8192];
            for (int n; (n = in.read(block)) != -1;) out.write(block, 0, n);
            return out.toByteArray();
        } finally { in.close(); }
    }
    static ClassNode parse(byte[] bytes) {
        ClassNode node = new ClassNode(ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
    static Config config(File file) throws Exception {
        Config result = null;
        try (JarFile jar = new JarFile(file);
             URLClassLoader loader = new URLClassLoader(new URL[]{file.toURI().toURL()}, null)) {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                byte[] bytes = read(jar.getInputStream(entry));
                ClassNode node = parse(bytes);
                boolean marker = false;
                for (MethodNode m : node.methods) for (AbstractInsnNode i : m.instructions) {
                    if (i instanceof LdcInsnNode && "hs1.teamobi.com".equals(((LdcInsnNode)i).cst)) marker = true;
                }
                if (!marker) continue;
                Class<?> type = Class.forName(node.name.replace('/', '.'), true, loader);
                for (Field f : type.getDeclaredFields()) {
                    if (f.getType() != String[][].class) continue;
                    String[][] rows = (String[][]) f.get(null);
                    boolean custom = false;
                    for (String[] row : rows) if (Arrays.asList(row).contains("103.178.234.202")) custom = true;
                    if (!custom) continue;
                    if (result != null) throw new IllegalStateException("Ambiguous server list");
                    result = new Config(); result.owner = node.name; result.field = f.getName();
                    result.rows = rows; result.bytes = bytes;
                }
            }
        }
        if (result == null) throw new IllegalStateException("Server list not found");
        return result;
    }
    static String[] donorRow(Config config) {
        String[] found = null;
        for (String[] row : config.rows) if (LABEL.equalsIgnoreCase(row[0])) {
            if (found != null || row.length != 2) throw new IllegalStateException("Ambiguous donor row");
            found = row.clone();
        }
        if (found == null) throw new IllegalStateException("Donor server not found");
        return found;
    }
    static int integer(AbstractInsnNode i) {
        if (i.getOpcode() >= ICONST_M1 && i.getOpcode() <= ICONST_5) return i.getOpcode() - ICONST_0;
        if (i instanceof IntInsnNode) return ((IntInsnNode)i).operand;
        if (i instanceof LdcInsnNode && ((LdcInsnNode)i).cst instanceof Integer) return (Integer)((LdcInsnNode)i).cst;
        return Integer.MIN_VALUE;
    }
    static AbstractInsnNode prev(AbstractInsnNode i) {
        do { i = i.getPrevious(); } while (i != null && i.getOpcode() < 0);
        return i;
    }
    static AbstractInsnNode next(AbstractInsnNode i) {
        do { i = i.getNext(); } while (i != null && i.getOpcode() < 0);
        return i;
    }
    static AbstractInsnNode push(int value) { return new IntInsnNode(SIPUSH, value); }
    static byte[] patch(Config target, String[] row) {
        for (String[] old : target.rows) if (row[0].equalsIgnoreCase(old[0])) throw new IllegalStateException("Server already present");
        ClassNode node = parse(target.bytes);
        int patched = 0;
        for (MethodNode method : node.methods) {
            if (!"<clinit>".equals(method.name)) continue;
            for (AbstractInsnNode i : method.instructions.toArray()) {
                if (!(i instanceof FieldInsnNode)) continue;
                FieldInsnNode store = (FieldInsnNode)i;
                if (store.getOpcode() != PUTSTATIC || !target.owner.equals(store.owner)
                        || !target.field.equals(store.name) || !"[[Ljava/lang/String;".equals(store.desc)) continue;
                AbstractInsnNode allocation = i.getPrevious();
                while (allocation != null && !(allocation instanceof TypeInsnNode && allocation.getOpcode() == ANEWARRAY
                        && "[Ljava/lang/String;".equals(((TypeInsnNode)allocation).desc))) allocation = allocation.getPrevious();
                if (allocation == null || integer(prev(allocation)) != target.rows.length) throw new IllegalStateException("Unexpected list allocation");
                method.instructions.set(prev(allocation), push(target.rows.length + 1));
                InsnList add = new InsnList();
                add.add(new InsnNode(DUP)); add.add(push(target.rows.length)); add.add(new InsnNode(ICONST_2));
                add.add(new TypeInsnNode(ANEWARRAY, "java/lang/String"));
                for (int col = 0; col < 2; col++) {
                    add.add(new InsnNode(DUP)); add.add(push(col)); add.add(new LdcInsnNode(row[col])); add.add(new InsnNode(AASTORE));
                }
                add.add(new InsnNode(AASTORE)); method.instructions.insertBefore(i, add); patched++;
                method.maxStack = Math.max(method.maxStack, 7);
            }
        }
        if (patched != 1) throw new IllegalStateException("Expected one list initializer");
        ClassWriter writer = new ClassWriter(new ClassReader(target.bytes), 0);
        node.accept(writer);
        return writer.toByteArray();
    }
    static void inspect(File file, Config config) throws Exception {
        System.out.println("JAR=" + file + " OWNER=" + config.owner + " FIELD=" + config.field);
        for (int i = 0; i < config.rows.length; i++) System.out.println(i + " " + Arrays.toString(config.rows[i]));
        try (JarFile jar = new JarFile(file)) {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = parse(read(jar.getInputStream(entry)));
                for (MethodNode method : node.methods) for (AbstractInsnNode i : method.instructions) {
                    if (!(i instanceof FieldInsnNode) || i.getOpcode() != GETSTATIC) continue;
                    FieldInsnNode field = (FieldInsnNode)i;
                    if (!config.owner.equals(field.owner) || !config.field.equals(field.name)) continue;
                    System.out.println("REF " + node.name + " " + method.name + method.desc);
                    for (AbstractInsnNode n = next(i), end = n; n != null; n = next(n)) {
                        System.out.println("  opcode=" + n.getOpcode() + " int=" + integer(n)
                            + (n instanceof FieldInsnNode ? " field=" + ((FieldInsnNode)n).owner + "." + ((FieldInsnNode)n).name : "")
                            + (n instanceof LdcInsnNode ? " constant=" + ((LdcInsnNode)n).cst : ""));
                        if (n.getOpcode() == AALOAD || n.getOpcode() == ARRAYLENGTH || n == end && n.getOpcode() == PUTSTATIC) break;
                    }
                }
            }
        }
    }
    static void verify(File before, File after, Config target, String[] row, Metadata oldMetadata, Metadata donorMetadata, int donorIndex) throws Exception {
        Config actual = config(after);
        if (!actual.owner.equals(target.owner) || !actual.field.equals(target.field)
                || actual.rows.length != target.rows.length + 1) throw new IllegalStateException("List shape changed");
        for (int i = 0; i < target.rows.length; i++) if (!Arrays.equals(target.rows[i], actual.rows[i])) throw new IllegalStateException("Existing row changed: " + i);
        if (!Arrays.equals(row, actual.rows[target.rows.length])) throw new IllegalStateException("Added row mismatch");
        Metadata newMetadata = metadata(after, actual.rows.length);
        for (String name : oldMetadata.arrays.keySet()) {
            int[] old = oldMetadata.arrays.get(name), added = newMetadata.arrays.get(name);
            if (added.length != old.length + 1 || added[old.length] != donorMetadata.arrays.get(name)[donorIndex]) throw new IllegalStateException("New metadata mismatch");
            for (int i = 0; i < old.length; i++) if (old[i] != added[i]) throw new IllegalStateException("Existing metadata changed");
        }
        int changes = 0;
        try (JarFile a = new JarFile(before); JarFile b = new JarFile(after)) {
            if (a.size() != b.size()) throw new IllegalStateException("Entry count changed");
            Set<String> names = new HashSet<String>();
            for (Enumeration<JarEntry> entries = a.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                if (!names.add(entry.getName())) throw new IllegalStateException("Duplicate entry");
                JarEntry other = b.getJarEntry(entry.getName());
                if (other == null) throw new IllegalStateException("Missing entry");
                byte[] old = read(a.getInputStream(entry)), changed = read(b.getInputStream(other));
                java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(changed);
                if (crc.getValue() != other.getCrc()) throw new IllegalStateException("CRC mismatch");
                if (entry.getName().endsWith(".class") && parse(old).version != parse(changed).version) throw new IllegalStateException("Class version changed");
                if (!Arrays.equals(old, changed)) {
                    if (!entry.getName().equals(target.owner + ".class") && !entry.getName().equals(oldMetadata.owner + ".class")) throw new IllegalStateException("Unexpected change: " + entry.getName());
                    changes++;
                }
            }
            if (changes != 2) throw new IllegalStateException("Expected exactly two changed classes");
            System.out.println("VERIFIED rows=" + actual.rows.length + " entries=" + a.size() + " changed_classes=" + changes);
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[]{after.toURI().toURL()}, HungBySonServerListPatcher.class.getClassLoader());
             JarFile jar = new JarFile(after)) {
            int loaded = 0;
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class")) continue;
                Class<?> type = Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false, loader);
                type.getDeclaredFields(); type.getDeclaredMethods(); type.getDeclaredConstructors(); loaded++;
            }
            System.out.println("JVM_CLASS_LINK=OK classes=" + loaded);
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "inspect".equals(args[0])) {
            File file = new File(args[1]); inspect(file, config(file)); return;
        }
        if (args.length != 3) throw new IllegalArgumentException("donor.jar target.jar output.jar | inspect jar");
        File donor = new File(args[0]).getCanonicalFile(), input = new File(args[1]).getCanonicalFile(), output = new File(args[2]).getCanonicalFile();
        if (!donor.isFile() || !input.isFile() || output.exists() || output.equals(input) || output.equals(donor)) throw new IllegalArgumentException("Unsafe output");
        Config target = config(input), donorConfig = config(donor); String[] row = donorRow(donorConfig);
        int donorIndex = -1;
        for (int i = 0; i < donorConfig.rows.length; i++) if (Arrays.equals(row, donorConfig.rows[i])) donorIndex = i;
        Metadata targetMetadata = metadata(input, target.rows.length), donorMetadata = metadata(donor, donorConfig.rows.length);
        byte[] bytes = patch(target, row);
        byte[] metadataBytes = patchMetadata(targetMetadata, donorMetadata, donorIndex, target.rows.length);
        try (JarFile jar = new JarFile(input); JarOutputStream out = new JarOutputStream(new FileOutputStream(output))) {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                JarEntry old = entries.nextElement(); JarEntry entry = new JarEntry(old.getName());
                entry.setTime(old.getTime());
                if (old.getComment() != null) entry.setComment(old.getComment());
                if (old.getExtra() != null) entry.setExtra(old.getExtra());
                out.putNextEntry(entry);
                out.write(old.getName().equals(target.owner + ".class") ? bytes
                    : old.getName().equals(targetMetadata.owner + ".class") ? metadataBytes : read(jar.getInputStream(old)));
                out.closeEntry();
            }
        }
        verify(input, output, target, row, targetMetadata, donorMetadata, donorIndex);
        System.out.println("ADDED=" + Arrays.toString(row));
    }
}
