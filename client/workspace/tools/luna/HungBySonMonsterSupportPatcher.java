import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/** Replaces the installed monster-support families and their existing cache bridge. */
public final class HungBySonMonsterSupportPatcher {
    private static final String PINNED_INPUT =
            "8B67184EE5AEBD1181931B80CD6F9EF0AC833637BE90B5EB5C5AA14289A55953";
    private static final String[] SUPPORT_PREFIXES = {
        "luna/features/MonsterCacheSupport", "luna/features/MonsterTargetWatchSupport"
    };

    private HungBySonMonsterSupportPatcher() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException(
                "input.jar support-dir output.jar feature-catalog.properties report.txt");
        File input = new File(args[0]).getCanonicalFile();
        File support = new File(args[1]).getCanonicalFile();
        File output = new File(args[2]).getCanonicalFile();
        File catalog = new File(args[3]).getCanonicalFile();
        File report = new File(args[4]).getCanonicalFile();
        require(input.isFile(), "missing input JAR");
        require(support.isDirectory(), "missing staged support directory");
        require(catalog.isFile(), "missing feature catalog");
        require(!output.exists(), "refusing to overwrite output JAR");
        require(!input.equals(output), "input and output paths must differ");

        Properties featureCatalog = load(catalog);
        require(PINNED_INPUT.equals(featureCatalog.getProperty("output.sha256")),
                "feature catalog does not describe the pinned real650 input");
        require("650".equals(featureCatalog.getProperty("auto.delay.ms")),
                "feature catalog is not the real650 variant");
        String inputHash = hash(input);
        require(PINNED_INPUT.equals(inputHash), "unexpected current Hung_By_Son input hash: " + inputHash);

        Map<String, byte[]> before = readArchive(input);
        Map<String, byte[]> replacements = readSupport(support);
        Set<String> currentFamily = family(before.keySet());
        require(!currentFamily.isEmpty(), "input has no MonsterCacheSupport classes");
        require(currentFamily.equals(replacements.keySet()),
                "compiled family paths differ from the current JAR family; current="
                        + currentFamily + " staged=" + replacements.keySet());

        for (String name : currentFamily) {
            require(before.containsKey(name), "support family path is absent from input: " + name);
            ClassInfo oldInfo = inspect(before.get(name), name);
            ClassInfo newInfo = inspect(replacements.get(name), name);
            require(oldInfo.version == newInfo.version,
                    "classfile version changed for " + name + ": " + oldInfo.version + " to " + newInfo.version);
            require(newInfo.major <= 51, "support class exceeds CLDC-compatible classfile major: " + name);
        }

        String bridgeName = required(featureCatalog, "bridge.owner") + ".class";
        String entityName = required(featureCatalog, "entity.owner");
        String monsterName = required(featureCatalog, "mob.owner");
        require("luna/generated/HungMonsterCacheAccess.class".equals(bridgeName),
                "feature catalog names an unexpected monster cache bridge");
        require(before.containsKey(bridgeName), "current JAR has no generated monster cache bridge");
        validateReleaseFields(before, entityName, monsterName);
        byte[] bridgeBytes = patchBridge(before.get(bridgeName), entityName, monsterName);
        ClassInfo oldBridge = inspect(before.get(bridgeName), bridgeName);
        ClassInfo newBridge = inspect(bridgeBytes, bridgeName);
        require(oldBridge.version == newBridge.version,
                "generated bridge classfile version changed: " + oldBridge.version + " to " + newBridge.version);
        require(newBridge.major <= 51, "generated bridge exceeds CLDC-compatible classfile major");
        replacements.put(bridgeName, bridgeBytes);

        for (String name : before.keySet()) {
            if (isJarSignature(name)) throw new IllegalStateException(
                    "cannot preserve a signed archive while replacing classes: " + name);
        }

        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("cannot create output directory: " + parent);
        File temporary = new File(output.getPath() + ".tmp");
        require(!temporary.exists(), "temporary output already exists");
        try {
            writeArchive(input, temporary, replacements);
            Map<String, byte[]> after = readArchive(temporary);
            require(before.keySet().equals(after.keySet()), "archive entry topology changed");
            List<String> changed = new ArrayList<String>();
            boolean supportChanged = false;
            boolean bridgeChanged = false;
            for (String name : before.keySet()) {
                if (!equal(before.get(name), after.get(name))) {
                    require(currentFamily.contains(name) || bridgeName.equals(name),
                            "unexpected archive entry changed: " + name);
                    changed.add(name);
                    if (currentFamily.contains(name)) supportChanged = true;
                    if (bridgeName.equals(name)) bridgeChanged = true;
                }
                if (!currentFamily.contains(name) && !bridgeName.equals(name)) require(equal(before.get(name), after.get(name)),
                        "non-support entry bytes changed: " + name);
            }
            require(supportChanged, "compiled support families are byte-identical to the input");
            require(bridgeChanged, "combat HP bridge was byte-identical to the input");
            require(changed.size() <= currentFamily.size() + 1, "changed entries exceed support and bridge scope");
            if (!temporary.renameTo(output)) {
                Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE);
            }
            writeReport(report, input, output, catalog, currentFamily, bridgeName, changed, before.size());
            System.out.println("MONSTER_SUPPORT_PATCH=PASS; changed=" + changed.size()
                    + "; entries=" + before.size() + "; sha256=" + hash(output));
        } catch (Throwable failure) {
            if (temporary.exists() && !temporary.delete())
                failure.addSuppressed(new IOException("could not remove partial temporary JAR: " + temporary));
            if (output.exists() && !output.delete())
                failure.addSuppressed(new IOException("could not remove failed output JAR: " + output));
            if (failure instanceof Exception) throw (Exception)failure;
            throw (Error)failure;
        }
    }

    private static Properties load(File file) throws Exception {
        Properties p = new Properties();
        InputStream in = new FileInputStream(file);
        try { p.load(in); } finally { in.close(); }
        return p;
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        require(value != null && value.length() != 0, "feature catalog is missing " + key);
        return value;
    }

    private static void validateReleaseFields(Map<String, byte[]> classes, String entity, String monster) {
        ClassNode entityNode = node(classes, entity);
        ClassNode monsterNode = node(classes, monster);
        String[] entityIntFields = {"cG", "cK", "bb", "bc", "bd", "dW", "bT"};
        String[] entityBooleanFields = {"dk", "cL", "cM", "dz", "cX"};
        for (String field : entityIntFields) requirePublicInstanceField(entityNode, field, "I");
        for (String field : entityBooleanFields) requirePublicInstanceField(entityNode, field, "Z");
        requirePublicInstanceField(monsterNode, "a", "I");
        require(isSubclass(classes, monsterNode, entity),
                "native monster class is not assignable to the configured entity class");
    }

    private static boolean isSubclass(Map<String, byte[]> classes, ClassNode child, String ancestor) {
        Set<String> visited = new LinkedHashSet<String>();
        String next = child.name;
        while (next != null && visited.add(next)) {
            if (ancestor.equals(next)) return true;
            byte[] bytes = classes.get(next + ".class");
            if (bytes == null) return false;
            ClassNode current = new ClassNode();
            new ClassReader(bytes).accept(current, 0);
            next = current.superName;
        }
        return false;
    }

    private static ClassNode node(Map<String, byte[]> classes, String internalName) {
        byte[] bytes = classes.get(internalName + ".class");
        require(bytes != null, "native class is absent from current JAR: " + internalName);
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        require(internalName.equals(node.name), "native class path/name mismatch: " + internalName);
        return node;
    }

    private static void requirePublicInstanceField(ClassNode owner, String name, String descriptor) {
        FieldNode found = null;
        for (Object value : owner.fields) {
            FieldNode field = (FieldNode)value;
            if (field.name.equals(name)) {
                require(found == null, "duplicate native field: " + owner.name + "." + name);
                found = field;
            }
        }
        require(found != null, "missing native field: " + owner.name + "." + name);
        require(descriptor.equals(found.desc), "native field descriptor mismatch: " + owner.name + "." + name);
        require((found.access & Opcodes.ACC_PUBLIC) != 0, "native field is not public: " + owner.name + "." + name);
        require((found.access & Opcodes.ACC_STATIC) == 0, "native field is static: " + owner.name + "." + name);
    }

    private static byte[] patchBridge(byte[] bytes, String entity, String monster) {
        ClassNode bridge = new ClassNode();
        new ClassReader(bytes).accept(bridge, 0);
        Set<String> abi = bridgeAbi(bridge);
        HungBySonCombatHpBridgePatch.apply(bridge, entity, monster);
        require(abi.equals(bridgeAbi(bridge)), "generated bridge API changed");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        bridge.accept(writer);
        return writer.toByteArray();
    }

    private static Set<String> bridgeAbi(ClassNode node) {
        Set<String> abi = new LinkedHashSet<String>();
        abi.add("class " + node.name + " " + node.superName + " " + node.access + " " + node.version);
        for (Object value : node.interfaces) abi.add("interface " + value);
        for (Object value : node.fields) {
            FieldNode field = (FieldNode)value;
            abi.add("field " + field.name + " " + field.desc + " " + field.access);
        }
        for (Object value : node.methods) {
            org.objectweb.asm.tree.MethodNode method = (org.objectweb.asm.tree.MethodNode)value;
            abi.add("method " + method.name + " " + method.desc + " " + method.access);
        }
        return abi;
    }

    private static Map<String, byte[]> readArchive(File file) throws Exception {
        Map<String, byte[]> data = new LinkedHashMap<String, byte[]>();
        JarFile jar = new JarFile(file);
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                InputStream in = jar.getInputStream(entry);
                try {
                    byte[] block = new byte[8192];
                    for (int n; (n = in.read(block)) != -1;) out.write(block, 0, n);
                } finally { in.close(); }
                byte[] bytes = out.toByteArray();
                CRC32 crc = new CRC32(); crc.update(bytes);
                require(crc.getValue() == entry.getCrc(), "CRC mismatch: " + entry.getName());
                require(!data.containsKey(entry.getName()), "duplicate archive entry: " + entry.getName());
                data.put(entry.getName(), bytes);
            }
        } finally { jar.close(); }
        return data;
    }

    private static Map<String, byte[]> readSupport(File root) throws Exception {
        Map<String, byte[]> data = new LinkedHashMap<String, byte[]>();
        String rootPath = root.getCanonicalPath();
        String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
            collectSupport(root, prefix, data);
        return data;
    }

    private static void collectSupport(File current, String prefix,
            Map<String, byte[]> data) throws Exception {
        File[] children = current.listFiles();
        if (children == null) throw new IOException("cannot list staged support directory: " + current);
        for (File child : children) {
            String canonical = child.getCanonicalPath();
            require(canonical.startsWith(prefix), "staged path escapes its directory: " + child);
            if (child.isDirectory()) {
                collectSupport(child, prefix, data);
                continue;
            }
            require(child.isFile(), "staged support contains a non-file path: " + child);
            String name = canonical.substring(prefix.length()).replace(File.separatorChar, '/');
            require(isSupportClass(name), "staged output contains a non-support file: " + name);
            require(!data.containsKey(name), "duplicate staged support path: " + name);
            data.put(name, Files.readAllBytes(child.toPath()));
        }
    }

    private static Set<String> family(Set<String> names) {
        Set<String> found = new LinkedHashSet<String>();
        for (String name : names) if (isSupportClass(name)) found.add(name);
        return found;
    }

    private static boolean isSupportClass(String name) {
        for (String prefix : SUPPORT_PREFIXES) {
            if (name.startsWith(prefix) && name.endsWith(".class")
                    && (name.length() == prefix.length() + 6 || name.charAt(prefix.length()) == '$')
                    && name.indexOf('/', prefix.length()) < 0) return true;
        }
        return false;
    }

    private static ClassInfo inspect(byte[] bytes, String path) {
        require(bytes.length >= 8 && bytes[0] == (byte)0xca && bytes[1] == (byte)0xfe
                && bytes[2] == (byte)0xba && bytes[3] == (byte)0xbe, "invalid class file: " + path);
        ClassReader reader = new ClassReader(bytes);
        String internal = path.substring(0, path.length() - 6);
        require(internal.equals(reader.getClassName()), "class path/name mismatch: " + path
                + " contains " + reader.getClassName());
        ClassNode parsed = new ClassNode();
        reader.accept(parsed, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        int major = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        require((parsed.version & 65535) == major, "unsupported class version encoding: " + path);
        return new ClassInfo(major, parsed.version);
    }

    private static void writeArchive(File input, File output,
            Map<String, byte[]> replacements) throws Exception {
        JarFile source = new JarFile(input);
        JarOutputStream target = new JarOutputStream(new FileOutputStream(output));
        try {
            if (source.getComment() != null) target.setComment(source.getComment());
            Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                byte[] bytes = replacements.containsKey(entry.getName())
                        ? replacements.get(entry.getName()) : readEntry(source, entry);
                JarEntry copy = new JarEntry(entry.getName());
                if (entry.getTime() >= 0L) copy.setTime(entry.getTime());
                if (entry.getComment() != null) copy.setComment(entry.getComment());
                if (entry.getExtra() != null) copy.setExtra(entry.getExtra());
                int method = entry.getMethod();
                copy.setMethod(method);
                if (method == JarEntry.STORED) {
                    CRC32 crc = new CRC32(); crc.update(bytes);
                    copy.setSize(bytes.length);
                    copy.setCompressedSize(bytes.length);
                    copy.setCrc(crc.getValue());
                }
                target.putNextEntry(copy);
                target.write(bytes);
                target.closeEntry();
            }
        } finally {
            try { target.close(); } finally { source.close(); }
        }
    }

    private static byte[] readEntry(JarFile jar, JarEntry entry) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = jar.getInputStream(entry);
        try {
            byte[] block = new byte[8192];
            for (int n; (n = in.read(block)) != -1;) out.write(block, 0, n);
        } finally { in.close(); }
        return out.toByteArray();
    }

    private static void writeReport(File report, File input, File output, File catalog,
            Set<String> family, String bridgeName, List<String> changed, int entries) throws Exception {
        StringBuilder text = new StringBuilder();
        text.append("INPUT_SHA256=").append(hash(input)).append('\n');
        text.append("CANDIDATE_SHA256=").append(hash(output)).append('\n');
        text.append("CATALOG=").append(catalog.getCanonicalPath()).append('\n');
        text.append("ENTRIES=").append(entries).append('\n');
        text.append("SUPPORT_FAMILY=").append(join(family)).append('\n');
        text.append("STAGED_FILES_USED=").append(join(family)).append('\n');
        text.append("NON_SUPPORT_COMPILE_OUTPUT_USED=none\n");
        text.append("BRIDGE_PATCH=").append(bridgeName).append(" via HungBySonCombatHpBridgePatch\n");
        text.append("CHANGED_CLASSES=").append(join(changed)).append('\n');
        for (String name : changed) text.append("CHANGED_CLASS=").append(name).append('\n');
        text.append("BOUNDARY=only existing MonsterCacheSupport/MonsterTargetWatchSupport families and generated cache bridge payloads changed; all other expanded entry bytes and archive topology preserved\n");
        Files.write(report.toPath(), text.toString().getBytes("UTF-8"));
        for (String name : changed) System.out.println("CHANGED=" + name);
    }

    private static String join(Iterable<String> names) {
        StringBuilder result = new StringBuilder();
        for (String name : names) {
            if (result.length() != 0) result.append(',');
            result.append(name);
        }
        return result.toString();
    }

    private static boolean isJarSignature(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        if (!upper.startsWith("META-INF/")) return false;
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA")
                || upper.endsWith(".EC") || upper.matches("META-INF/SIG-[^/]+");
    }

    private static boolean equal(byte[] a, byte[] b) {
        if (a == b) return true;
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(file);
        try {
            byte[] block = new byte[8192];
            for (int n; (n = in.read(block)) != -1;) digest.update(block, 0, n);
        } finally { in.close(); }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format("%02X", Integer.valueOf(b & 255)));
        return result.toString();
    }

    private static void require(boolean okay, String message) {
        if (!okay) throw new IllegalStateException(message);
    }

    private static final class ClassInfo {
        final int major;
        final int version;
        ClassInfo(int major, int version) { this.major = major; this.version = version; }
    }
}
