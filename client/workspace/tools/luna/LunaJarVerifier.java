package luna.tools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Read-only JAR verifier and build-manifest writer for the Luna pipeline.
 * Run from client/workspace; it never overwrites a JAR.
 */
public final class LunaJarVerifier {

    private static final String GOLDEN_SHA256 =
            "06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F";

    private LunaJarVerifier() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "--help".equals(args[0])) {
            usage();
            return;
        }

        File jar = new File(args[0]);
        File manifestOut = null;
        File compareWith = null;
        boolean loadClasses = false;
        Set<String> allowedResources = new HashSet<String>();
        for (int i = 1; i < args.length; i++) {
            if ("--write-manifest".equals(args[i]) && i + 1 < args.length) {
                manifestOut = new File(args[++i]);
            } else if ("--compare-resources".equals(args[i]) && i + 1 < args.length) {
                compareWith = new File(args[++i]);
            } else if ("--allow-resource".equals(args[i]) && i + 1 < args.length) {
                allowedResources.add(args[++i]);
            } else if ("--load-classes".equals(args[i])) {
                loadClasses = true;
            } else {
                throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        JarReport report = inspect(jar);
        print(report);
        if (jar.getName().equalsIgnoreCase("luna_final-golden-baseline.jar")) {
            require(GOLDEN_SHA256.equals(report.sha256),
                    "luna_final-golden-baseline.jar hash does not match the recorded golden baseline");
            require("4.0.2".equals(report.version), "golden manifest version must be 4.0.2");
        }
        require(report.hookCount == 1, "expected exactly one HSOAutoTradeSupport hook class");
        require(report.semanticClassCount > 0,
                "output does not contain compiled src/luna classes");
        require(report.hasLegacyBridge,
                "output does not contain LunaGeneratedLegacyHooks bridge");
        require(report.maximumMajor <= 51,
                "class version above 51 is not compatible with the J2ME pipeline");

        if (compareWith != null) compareResources(compareWith, jar, allowedResources);
        if (loadClasses) loadClasses(jar, report);
        if (manifestOut != null) writeManifest(manifestOut, report);
    }

    private static JarReport inspect(File file) throws Exception {
        if (!file.isFile()) throw new IOException("JAR not found: " + file.getPath());
        JarReport report = new JarReport();
        report.file = file;
        report.sha256 = sha256(file);
        JarFile jar = new JarFile(file);
        try {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                Attributes a = manifest.getMainAttributes();
                report.name = a.getValue("MIDlet-Name");
                report.version = a.getValue("MIDlet-Version");
                report.configuration = a.getValue("MicroEdition-Configuration");
                report.profile = a.getValue("MicroEdition-Profile");
            }
            List<String> entries = new ArrayList<String>();
            java.util.Enumeration<JarEntry> all = jar.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                entries.add(entry.getName());
                if (entry.isDirectory()) continue;
                if (entry.getName().endsWith(".class")) {
                    report.classCount++;
                    if (entry.getName().equals("HSOAutoTradeSupport.class")) report.hookCount++;
                    if (entry.getName().startsWith("luna/")
                            && entry.getName().endsWith(".class")) report.semanticClassCount++;
                    if (entry.getName().equals("LunaGeneratedLegacyHooks.class")) {
                        report.hasLegacyBridge = true;
                    }
                    int major = readMajor(jar.getInputStream(entry));
                    report.majorCounts[major < 0 || major >= report.majorCounts.length
                            ? report.majorCounts.length - 1 : major]++;
                    if (major > report.maximumMajor) report.maximumMajor = major;
                } else {
                    report.resourceCount++;
                }
            }
            Collections.sort(entries);
            report.entries = entries;
        } finally {
            jar.close();
        }
        return report;
    }

    private static int readMajor(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(input));
        try {
            if (in.readInt() != 0xCAFEBABE) throw new IOException("invalid class magic");
            in.readUnsignedShort();
            return in.readUnsignedShort();
        } finally {
            in.close();
        }
    }

    private static void compareResources(File baseline, File candidate,
            Set<String> allowed) throws Exception {
        JarFile oldJar = new JarFile(baseline);
        JarFile newJar = new JarFile(candidate);
        try {
            Set<String> names = new HashSet<String>();
            java.util.Enumeration<JarEntry> oldEntries = oldJar.entries();
            while (oldEntries.hasMoreElements()) {
                JarEntry e = oldEntries.nextElement();
                if (!e.isDirectory() && !e.getName().endsWith(".class")) names.add(e.getName());
            }
            java.util.Enumeration<JarEntry> newEntries = newJar.entries();
            while (newEntries.hasMoreElements()) {
                JarEntry e = newEntries.nextElement();
                if (!e.isDirectory() && !e.getName().endsWith(".class")) names.add(e.getName());
            }
            for (String name : names) {
                if (allowed.contains(name)) continue;
                byte[] left = bytes(oldJar.getJarEntry(name), oldJar);
                byte[] right = bytes(newJar.getJarEntry(name), newJar);
                require(equal(left, right), "resource changed outside allow-list: " + name);
            }
            System.out.println("resourceComparison=OK");
        } finally {
            oldJar.close();
            newJar.close();
        }
    }

    private static void loadClasses(File file, JarReport report) throws Exception {
        URLClassLoader loader = new URLClassLoader(
                new URL[] {file.toURI().toURL()}, LunaJarVerifier.class.getClassLoader());
        try {
            for (String entry : report.entries) {
                if (!entry.endsWith(".class") || entry.endsWith("module-info.class")) continue;
                String name = entry.substring(0, entry.length() - 6).replace('/', '.');
                Class.forName(name, false, loader);
            }
            System.out.println("classLoad=OK (" + report.classCount + " classes verified by JVM)");
        } finally {
            loader.close();
        }
    }

    private static byte[] bytes(JarEntry entry, JarFile jar) throws IOException {
        if (entry == null) return null;
        InputStream in = jar.getInputStream(entry);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        in.close();
        return out.toByteArray();
    }

    private static boolean equal(byte[] left, byte[] right) {
        if (left == right) return true;
        if (left == null || right == null || left.length != right.length) return false;
        for (int i = 0; i < left.length; i++) if (left[i] != right[i]) return false;
        return true;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) digest.update(buffer, 0, n);
        } finally {
            in.close();
        }
        byte[] hash = digest.digest();
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            int value = hash[i] & 0xff;
            if (value < 16) out.append('0');
            out.append(Integer.toHexString(value));
        }
        return out.toString().toUpperCase();
    }

    private static void writeManifest(File file, JarReport report) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create manifest directory: " + parent);
        }
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        try {
            out.write(("jar=" + report.file.getPath() + "\n").getBytes("UTF-8"));
            out.write(("sha256=" + report.sha256 + "\n").getBytes("UTF-8"));
            out.write(("manifest.name=" + value(report.name) + "\n").getBytes("UTF-8"));
            out.write(("manifest.version=" + value(report.version) + "\n").getBytes("UTF-8"));
            out.write(("manifest.configuration=" + value(report.configuration) + "\n").getBytes("UTF-8"));
            out.write(("manifest.profile=" + value(report.profile) + "\n").getBytes("UTF-8"));
            out.write(("class.count=" + report.classCount + "\n").getBytes("UTF-8"));
            out.write(("resource.count=" + report.resourceCount + "\n").getBytes("UTF-8"));
            out.write(("hook.count=" + report.hookCount + "\n").getBytes("UTF-8"));
            out.write(("semantic.class.count=" + report.semanticClassCount + "\n").getBytes("UTF-8"));
            out.write(("legacy.bridge=" + report.hasLegacyBridge + "\n").getBytes("UTF-8"));
            out.write(("class.maximumMajor=" + report.maximumMajor + "\n").getBytes("UTF-8"));
            out.write(("class.major.47=" + report.majorCounts[47] + "\n").getBytes("UTF-8"));
            out.write(("class.major.51=" + report.majorCounts[51] + "\n").getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static String value(String value) { return value == null ? "" : value; }

    private static void print(JarReport report) {
        System.out.println("jar=" + report.file.getPath());
        System.out.println("sha256=" + report.sha256);
        System.out.println("manifest.name=" + value(report.name));
        System.out.println("manifest.version=" + value(report.version));
        System.out.println("manifest.configuration=" + value(report.configuration));
        System.out.println("manifest.profile=" + value(report.profile));
        System.out.println("class.count=" + report.classCount);
        System.out.println("resource.count=" + report.resourceCount);
        System.out.println("hook.count=" + report.hookCount);
        System.out.println("semantic.class.count=" + report.semanticClassCount);
        System.out.println("legacy.bridge=" + report.hasLegacyBridge);
        System.out.println("class.maximumMajor=" + report.maximumMajor);
        System.out.println("class.major.47=" + report.majorCounts[47]);
        System.out.println("class.major.51=" + report.majorCounts[51]);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void usage() {
        System.out.println("Usage: java -cp build/tools luna.tools.LunaJarVerifier <jar>"
                + " [--write-manifest <file>] [--compare-resources <baseline>]"
                + " [--allow-resource <entry>] [--load-classes]");
    }

    private static final class JarReport {
        File file;
        String sha256;
        String name;
        String version;
        String configuration;
        String profile;
        int classCount;
        int resourceCount;
        int hookCount;
        int semanticClassCount;
        boolean hasLegacyBridge;
        int maximumMajor;
        int[] majorCounts = new int[256];
        List<String> entries;
    }
}
