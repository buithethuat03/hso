package luna.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Properties;
import java.util.jar.JarFile;

/** Verifies version metadata and the generated symbol map before a patch. */
public final class LunaSymbolCatalogVerifier {

    private static final String[] REQUIRED_KEYS = {
        "class.CANVAS", "class.ENGINE", "class.PLAYER", "class.ENTITY",
        "class.SHOP", "class.TILEMAP", "engine.field.INSTANCE"
    };

    private LunaSymbolCatalogVerifier() { }

    public static void main(String[] args) throws Exception {
        File metadata = new File(args.length > 0 ? args[0] : "symbols/luna-4.0.2.properties");
        require(metadata.isFile(), "symbol metadata not found: " + metadata.getPath());
        Properties version = load(metadata);
        require("4.0.2".equals(version.getProperty("manifest.version")),
                "symbol metadata is not for Luna 4.0.2");
        require("owner+member+descriptor".equals(version.getProperty("lookup.policy")),
                "symbol lookup policy must be owner+member+descriptor");

        String baselinePath = version.getProperty("baseline.path", version.getProperty("baseline.jar"));
        File baseline = new File(metadata.getParentFile(), baselinePath);
        require(baseline.isFile(), "baseline JAR not found: " + baseline.getPath());
        require(version.getProperty("baseline.sha256").equals(sha256(baseline)),
                "baseline JAR hash does not match symbol metadata");
        JarFile jar = new JarFile(baseline);
        try {
            require("4.0.2".equals(jar.getManifest().getMainAttributes()
                    .getValue("MIDlet-Version")), "baseline manifest version is not 4.0.2");
        } finally {
            jar.close();
        }

        File map = new File(metadata.getParentFile(), version.getProperty("symbol.map"));
        require(map.isFile(), "symbol map not found: " + map.getPath());
        Properties symbols = load(map);
        for (int i = 0; i < REQUIRED_KEYS.length; i++) {
            String value = symbols.getProperty(REQUIRED_KEYS[i]);
            require(value != null && value.length() > 0,
                    "missing semantic symbol: " + REQUIRED_KEYS[i]);
        }
        for (Object key : symbols.keySet()) {
            String value = symbols.getProperty(String.valueOf(key));
            require(value != null && value.trim().length() > 0,
                    "empty symbol value: " + key);
        }
        System.out.println("OK - symbol catalog " + metadata.getPath()
                + " matches baseline " + version.getProperty("baseline.jar"));
    }

    private static Properties load(File file) throws Exception {
        Properties result = new Properties();
        InputStream in = new FileInputStream(file);
        try {
            result.load(in);
        } finally {
            in.close();
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(file);
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
}
