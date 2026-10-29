package luna.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Creates a separately named Luna endpoint variant without changing its source JAR. */
public final class ServerVariantPatcher {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String MANIFEST = "META-INF/MANIFEST.MF";

    private static final String[] OLD_HOSTS = {
            "46.137.254.172",
            "54.254.156.202",
            "54.151.177.35"
    };

    private ServerVariantPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4 && args.length != 5) {
            throw new IllegalArgumentException(
                    "Usage: ServerVariantPatcher <input.jar> <output.jar> <server-ip> <server-name> [--keep-manifest]");
        }

        File input = new File(args[0]);
        File output = new File(args[1]);
        String serverIp = args[2];
        String serverName = args[3];
        boolean keepManifest = args.length == 5;
        require(!keepManifest || "--keep-manifest".equals(args[4]), "Unknown option");
        require(input.isFile(), "Input JAR not found: " + input);
        require(!input.getCanonicalFile().equals(output.getCanonicalFile()),
                "Output must differ from input");
        require(!output.exists(), "Output already exists: " + output);
        require(serverIp.matches("(?:\\d{1,3}\\.){3}\\d{1,3}"),
                "Invalid IPv4 address: " + serverIp);
        require(serverName.length() > 0, "Server name must not be empty");

        LinkedHashMap<String, EntryData> entries = readJar(input);
        LinkedHashMap<String, byte[]> original = snapshot(entries);
        require(entries.containsKey(MANIFEST), "Input has no manifest");

        PatchCounts counts = new PatchCounts();
        for (Map.Entry<String, EntryData> item : entries.entrySet()) {
            if (item.getKey().endsWith(".class")) {
                byte[] patched = patchClass(item.getValue().bytes, serverIp, serverName, counts);
                item.getValue().bytes = patched;
            }
        }
        if (!keepManifest) entries.get(MANIFEST).bytes = patchManifest(entries.get(MANIFEST).bytes, serverIp, serverName);

        require(counts.plainHosts == 5,
                "Expected 5 direct endpoint constants, found " + counts.plainHosts);
        require(counts.encodedHosts == 1,
                "Expected 1 XOR endpoint constant, found " + counts.encodedHosts);
        require(counts.serverNames == 1,
                "Expected 1 server-label constant, found " + counts.serverNames);

        List<String> changed = changedEntries(original, entries);
        require(changed.size() == (keepManifest ? 3 : 4), "Unexpected changed entries: " + changed);
        require(changed.contains(MANIFEST) != keepManifest, "Unexpected manifest change");

        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        File staging = new File(output.getPath() + ".tmp");
        require(!staging.exists() || staging.delete(), "Cannot clear staging file: " + staging);
        writeJar(staging, entries);
        verifyWritten(staging, entries.size(), serverIp, serverName, changed, keepManifest);
        if (keepManifest) require(Arrays.equals(original.get(MANIFEST), readJar(staging).get(MANIFEST).bytes), "Manifest bytes changed");
        Files.move(staging.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);

        System.out.println("Created: " + output.getCanonicalPath());
        System.out.println("Input SHA-256: " + sha256(input));
        System.out.println("Output SHA-256: " + sha256(output));
        System.out.println("Changed entries: " + changed);
    }

    private static byte[] patchClass(byte[] source, String serverIp,
            String serverName, PatchCounts counts) throws IOException {
        byte[] encodedOldHost = xor("103.178.235.83", 5).getBytes(UTF8);
        byte[] encodedNewHost = xor(serverIp, 5).getBytes(UTF8);
        byte[][] oldHosts = new byte[OLD_HOSTS.length][];
        for (int i = 0; i < OLD_HOSTS.length; i++) {
            oldHosts[i] = OLD_HOSTS[i].getBytes(UTF8);
        }
        byte[] newHost = serverIp.getBytes(UTF8);
        byte[] oldName = "LUNA".getBytes(UTF8);
        byte[] newName = serverName.getBytes(UTF8);

        DataInputStream input = new DataInputStream(new ByteArrayInputStream(source));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(source.length + 32);
        DataOutputStream output = new DataOutputStream(bytes);
        boolean changed = false;

        int magic = input.readInt();
        require(magic == 0xCAFEBABE, "Invalid classfile magic");
        output.writeInt(magic);
        output.writeShort(input.readUnsignedShort());
        output.writeShort(input.readUnsignedShort());
        int constantPoolCount = input.readUnsignedShort();
        output.writeShort(constantPoolCount);

        for (int index = 1; index < constantPoolCount; index++) {
            int tag = input.readUnsignedByte();
            output.writeByte(tag);
            if (tag == 1) {
                int length = input.readUnsignedShort();
                byte[] value = new byte[length];
                input.readFully(value);
                byte[] replacement = value;
                for (int i = 0; i < oldHosts.length; i++) {
                    if (Arrays.equals(value, oldHosts[i])) {
                        replacement = newHost;
                        counts.plainHosts++;
                        changed = true;
                        break;
                    }
                }
                if (Arrays.equals(value, encodedOldHost)) {
                    replacement = encodedNewHost;
                    counts.encodedHosts++;
                    changed = true;
                } else if (Arrays.equals(value, oldName)) {
                    replacement = newName;
                    counts.serverNames++;
                    changed = true;
                }
                output.writeShort(replacement.length);
                output.write(replacement);
            } else if (tag == 3 || tag == 4 || tag == 9 || tag == 10 || tag == 11
                    || tag == 12 || tag == 17 || tag == 18) {
                copyBytes(input, output, 4);
            } else if (tag == 5 || tag == 6) {
                copyBytes(input, output, 8);
                index++;
            } else if (tag == 7 || tag == 8 || tag == 16 || tag == 19 || tag == 20) {
                copyBytes(input, output, 2);
            } else if (tag == 15) {
                copyBytes(input, output, 3);
            } else {
                throw new IOException("Unsupported constant-pool tag: " + tag);
            }
        }

        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        output.flush();
        return changed ? bytes.toByteArray() : source;
    }

    private static void copyBytes(DataInputStream input, DataOutputStream output, int length)
            throws IOException {
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        output.write(bytes);
    }

    private static byte[] patchManifest(byte[] source, String serverIp, String serverName)
            throws IOException {
        Manifest manifest = new Manifest(new ByteArrayInputStream(source));
        Attributes attributes = manifest.getMainAttributes();
        require("HSOLUNA".equals(attributes.getValue("MIDlet-Name")),
                "Unexpected input MIDlet-Name: " + attributes.getValue("MIDlet-Name"));
        require("HSOLUNA,,TemMidlet".equals(attributes.getValue("MIDlet-1")),
                "Unexpected input MIDlet-1: " + attributes.getValue("MIDlet-1"));

        String text = new String(source, UTF8);
        text = replaceExactly(text, "MIDlet-1: HSOLUNA,,TemMidlet",
                "MIDlet-1: " + serverName + ",,TemMidlet");
        text = replaceExactly(text, "MIDlet-Vendor: Truongbk", "MIDlet-Vendor: HSO");
        text = replaceExactly(text, "MIDlet-Name: HSOLUNA", "MIDlet-Name: " + serverName);
        text = replaceExactly(text, "Implementation-Title: HSOLUNA Full Map Pickup",
                "Implementation-Title: " + serverName + " Full Map Pickup");
        String versionLine = "Implementation-Version: 4.0.2-full-map-pickup\r\n";
        require(text.indexOf(versionLine) >= 0, "Implementation-Version line not found");
        text = text.replace(versionLine, versionLine
                + "Implementation-Vendor: HSO\r\n"
                + "HSO-Server-IP: " + serverIp + "\r\n");
        return text.getBytes(UTF8);
    }

    private static String replaceExactly(String text, String oldValue, String newValue) {
        int first = text.indexOf(oldValue);
        require(first >= 0, "Manifest value not found: " + oldValue);
        require(text.indexOf(oldValue, first + oldValue.length()) < 0,
                "Manifest value is not unique: " + oldValue);
        return text.substring(0, first) + newValue + text.substring(first + oldValue.length());
    }

    private static void verifyWritten(File jarFile, int expectedEntries, String serverIp,
            String serverName, List<String> expectedChanged, boolean keepManifest) throws Exception {
        LinkedHashMap<String, EntryData> written = readJar(jarFile);
        require(written.size() == expectedEntries,
                "Entry count changed: expected " + expectedEntries + ", got " + written.size());

        Manifest manifest = new Manifest(new ByteArrayInputStream(written.get(MANIFEST).bytes));
        Attributes attributes = manifest.getMainAttributes();
        if (!keepManifest) {
            require(serverName.equals(attributes.getValue("MIDlet-Name")), "Wrong MIDlet-Name");
            require((serverName + ",,TemMidlet").equals(attributes.getValue("MIDlet-1")), "Wrong MIDlet-1");
            require(serverIp.equals(attributes.getValue("HSO-Server-IP")), "Wrong HSO-Server-IP");
        }

        int classCount = 0;
        int ipConstants = 0;
        int nameConstants = 0;
        for (Map.Entry<String, EntryData> item : written.entrySet()) {
            if (!item.getKey().endsWith(".class")) {
                continue;
            }
            classCount++;
            ConstantCounter counter = countConstants(item.getValue().bytes, serverIp, serverName);
            ipConstants += counter.ipConstants;
            nameConstants += counter.nameConstants;
        }
        require(classCount == 216, "Expected 216 classes, got " + classCount);
        require(ipConstants == 6, "Expected 6 endpoint constants, got " + ipConstants);
        require(nameConstants == 2, "Expected 2 server-name loads, got " + nameConstants);
        require(expectedChanged.size() == (keepManifest ? 3 : 4), "Unexpected changed-entry verification input");

        JarInputStream check = new JarInputStream(new FileInputStream(jarFile));
        byte[] buffer = new byte[8192];
        try {
            while (check.getNextJarEntry() != null) {
                while (check.read(buffer) != -1) {
                    // Reading every entry validates the ZIP stream and CRCs.
                }
            }
        } finally {
            check.close();
        }
    }

    private static ConstantCounter countConstants(byte[] bytes, final String serverIp,
            final String serverName) {
        final ConstantCounter counter = new ConstantCounter();
        final String encodedIp = xor(serverIp, 5);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    public void visitLdcInsn(Object value) {
                        if (serverIp.equals(value) || encodedIp.equals(value)) {
                            counter.ipConstants++;
                        }
                        if (serverName.equals(value)) {
                            counter.nameConstants++;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return counter;
    }

    private static LinkedHashMap<String, EntryData> readJar(File file) throws IOException {
        LinkedHashMap<String, EntryData> entries = new LinkedHashMap<String, EntryData>();
        JarFile jar = new JarFile(file);
        try {
            Enumeration<JarEntry> enumeration = jar.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                InputStream input = jar.getInputStream(entry);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                try {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        bytes.write(buffer, 0, count);
                    }
                } finally {
                    input.close();
                }
                entries.put(entry.getName(), new EntryData(bytes.toByteArray(), entry.getTime()));
            }
        } finally {
            jar.close();
        }
        return entries;
    }

    private static void writeJar(File file, LinkedHashMap<String, EntryData> entries)
            throws IOException {
        JarOutputStream output = new JarOutputStream(new FileOutputStream(file));
        try {
            for (Map.Entry<String, EntryData> item : entries.entrySet()) {
                JarEntry entry = new JarEntry(item.getKey());
                if (item.getValue().time >= 0) {
                    entry.setTime(item.getValue().time);
                }
                output.putNextEntry(entry);
                output.write(item.getValue().bytes);
                output.closeEntry();
            }
        } finally {
            output.close();
        }
    }

    private static LinkedHashMap<String, byte[]> snapshot(LinkedHashMap<String, EntryData> entries) {
        LinkedHashMap<String, byte[]> result = new LinkedHashMap<String, byte[]>();
        for (Map.Entry<String, EntryData> item : entries.entrySet()) {
            result.put(item.getKey(), item.getValue().bytes);
        }
        return result;
    }

    private static List<String> changedEntries(LinkedHashMap<String, byte[]> original,
            LinkedHashMap<String, EntryData> patched) {
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, EntryData> item : patched.entrySet()) {
            if (!Arrays.equals(original.get(item.getKey()), item.getValue().bytes)) {
                result.add(item.getKey());
            }
        }
        return result;
    }

    private static String xor(String value, int key) {
        char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (chars[i] ^ key);
        }
        return new String(chars);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream input = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        try {
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        } finally {
            input.close();
        }
        byte[] hash = digest.digest();
        StringBuilder text = new StringBuilder(hash.length * 2);
        for (int i = 0; i < hash.length; i++) {
            text.append(String.format("%02X", Integer.valueOf(hash[i] & 0xFF)));
        }
        return text.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class EntryData {
        byte[] bytes;
        final long time;

        EntryData(byte[] bytes, long time) {
            this.bytes = bytes;
            this.time = time;
        }
    }

    private static final class PatchCounts {
        int plainHosts;
        int encodedHosts;
        int serverNames;
    }

    private static final class ConstantCounter {
        int ipConstants;
        int nameConstants;
    }
}
