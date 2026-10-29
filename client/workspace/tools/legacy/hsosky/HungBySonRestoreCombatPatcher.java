import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;

/** Restores only the two classes changed by HungBySonCast300Patcher. */
public final class HungBySonRestoreCombatPatcher {
    private static final String BEFORE_SHA256 =
            "8BC1B4B85E602FC3554DB9415B343003755C5D750D0922D632309BF5E9FCCCF4";
    private static final String CAST300_SHA256 =
            "0044709A7214F02934EBCCD20C3CFA1167DC722BF3E6B303CDF5DFC4480C3FF4";

    private static final class EntryData {
        final JarEntry source;
        final byte[] bytes;
        EntryData(JarEntry source, byte[] bytes) { this.source = source; this.bytes = bytes; }
    }

    private HungBySonRestoreCombatPatcher() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && "--verify-link".equals(args[0])) {
            verifyLink(new File(args[1]), new File(args[2]));
            return;
        }
        require(args.length == 4,
                "Usage: HungBySonRestoreCombatPatcher current.jar before-cast300.jar cast300-reference.jar output.jar");
        File current = new File(args[0]).getCanonicalFile();
        File before = new File(args[1]).getCanonicalFile();
        File cast300 = new File(args[2]).getCanonicalFile();
        File output = new File(args[3]).getCanonicalFile();
        require(current.isFile() && before.isFile() && cast300.isFile(), "Input JAR missing");
        require(!output.exists(), "Output already exists");
        require(!output.equals(current) && !output.equals(before) && !output.equals(cast300),
                "Output must be a new file");
        require(BEFORE_SHA256.equals(sha256(before)), "Unexpected pre-cast backup hash");
        require(CAST300_SHA256.equals(sha256(cast300)), "Unexpected 300 ms reference hash");

        LinkedHashMap<String, EntryData> nativeEntries = read(before);
        LinkedHashMap<String, EntryData> patchedEntries = read(cast300);
        LinkedHashMap<String, EntryData> currentEntries = read(current);
        require(nativeEntries.keySet().equals(patchedEntries.keySet()), "Reference entry names differ");
        List<String> restored = new ArrayList<String>();
        for (Map.Entry<String, EntryData> entry : nativeEntries.entrySet()) {
            String name = entry.getKey();
            if (Arrays.equals(entry.getValue().bytes, patchedEntries.get(name).bytes)) continue;
            require(name.endsWith(".class"), "300 ms reference changed a non-class entry");
            EntryData now = currentEntries.get(name);
            require(now != null, "Current combat class missing: " + name);
            require(Arrays.equals(now.bytes, patchedEntries.get(name).bytes),
                    "Combat class contains later edits; cannot restore the whole class: " + name);
            require(classVersion(now.bytes) == classVersion(entry.getValue().bytes),
                    "Restoration changes class version: " + name);
            restored.add(name);
        }
        require(restored.size() == 2, "Expected exactly two combat class changes");
        LinkedHashMap<String, EntryData> result = new LinkedHashMap<String, EntryData>(currentEntries);
        for (String name : restored) {
            result.put(name, new EntryData(currentEntries.get(name).source, nativeEntries.get(name).bytes));
        }
        File parent = output.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        write(output, result);
        LinkedHashMap<String, EntryData> actual = read(output);
        require(actual.keySet().equals(currentEntries.keySet()), "Output entry names changed");
        int classes = 0, resources = 0;
        for (String name : currentEntries.keySet()) {
            byte[] expected = restored.contains(name) ? nativeEntries.get(name).bytes : currentEntries.get(name).bytes;
            require(Arrays.equals(expected, actual.get(name).bytes), "Unexpected output change: " + name);
            if (name.endsWith(".class")) classes++;
            else if (!name.endsWith("/")) resources++;
        }
        System.out.println("INPUT_SHA256=" + sha256(current));
        System.out.println("OUTPUT_SHA256=" + sha256(output));
        System.out.println("ENTRIES=" + actual.size());
        System.out.println("CLASSES=" + classes);
        System.out.println("RESOURCES=" + resources);
        System.out.println("RESTORED_CLASSES=" + restored.size());
        for (String name : restored) System.out.println("RESTORED=" + name);
        System.out.println("CHECKS=CRC,exact-native-class-bytes,unchanged-other-entries,class-versions");
    }

    private static void verifyLink(File jarFile, File midpDependency) throws Exception {
        require(jarFile.isFile() && midpDependency.isFile(), "Link-check input missing");
        URLClassLoader loader = new URLClassLoader(new URL[]{jarFile.toURI().toURL(),
                midpDependency.toURI().toURL()}, null);
        JarFile jar = new JarFile(jarFile);
        int linked = 0;
        try {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class")) continue;
                Class<?> type = Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false, loader);
                type.getDeclaredMethods();
                type.getDeclaredFields();
                type.getDeclaredConstructors();
                linked++;
            }
        } finally { jar.close(); loader.close(); }
        System.out.println("LINKED_CLASSES=" + linked);
        System.out.println("LINK_CHECK=PASS (no class initialization or game connection)");
    }

    private static int classVersion(byte[] bytes) {
        require(bytes.length >= 8 && bytes[0] == (byte)0xCA && bytes[1] == (byte)0xFE,
                "Invalid class header");
        return ((bytes[6] & 255) << 8) | (bytes[7] & 255);
    }

    private static LinkedHashMap<String, EntryData> read(File file) throws IOException {
        LinkedHashMap<String, EntryData> result = new LinkedHashMap<String, EntryData>();
        JarFile jar = new JarFile(file);
        try {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                InputStream input = jar.getInputStream(entry);
                byte[] bytes;
                try { bytes = readAll(input); } finally { input.close(); }
                CRC32 crc = new CRC32();
                crc.update(bytes);
                require(crc.getValue() == entry.getCrc(), "ZIP CRC mismatch: " + entry.getName());
                require(!result.containsKey(entry.getName()), "Duplicate JAR entry");
                result.put(entry.getName(), new EntryData(entry, bytes));
            }
        } finally { jar.close(); }
        return result;
    }

    private static void write(File file, LinkedHashMap<String, EntryData> entries) throws IOException {
        JarOutputStream output = new JarOutputStream(new FileOutputStream(file));
        try {
            for (Map.Entry<String, EntryData> item : entries.entrySet()) {
                JarEntry entry = new JarEntry(item.getKey());
                JarEntry source = item.getValue().source;
                if (source.getTime() >= 0) entry.setTime(source.getTime());
                if (source.getComment() != null) entry.setComment(source.getComment());
                if (source.getExtra() != null) entry.setExtra(source.getExtra());
                output.putNextEntry(entry);
                output.write(item.getValue().bytes);
                output.closeEntry();
            }
        } finally { output.close(); }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static String sha256(File file) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder result = new StringBuilder();
        for (byte value : hash) result.append(String.format("%02X", Integer.valueOf(value & 255)));
        return result.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
