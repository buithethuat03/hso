package luna.tools;

import java.util.Properties;

/** Exact semantic symbol lookup used by bridge and hook generators. */
public final class LunaSymbolResolver {

    private LunaSymbolResolver() { }

    public static String key(String owner, String member, String descriptor) {
        return "symbol." + owner + "." + member + "." + descriptor;
    }

    public static String resolve(Properties catalog, String owner, String member,
            String descriptor) {
        String lookupKey = key(owner, member, descriptor);
        String value = catalog.getProperty(lookupKey);
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException("Missing exact symbol: " + lookupKey);
        }
        return value;
    }
}
