import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Sinh HSOAutoTradeSupport.java facade tu template HSOAutoTradeSupport.java.in bang cach thay
 * moi &#64;key&#64; bang ten obfuscate tuong ung trong autotrade-symbols.properties.
 * Dong thoi sinh LunaGeneratedLegacyHooks.java tu tools/legacy/luna/legacy-hook.template;
 * class nay la compatibility bridge, khong phai feature API.
 *
 * Ly do phai qua mot buoc sinh: ten obfuscate trong jar dai 210-250 ky tu, viet thang
 * vao source thi khong ai doc noi va rat de go nham. Template moi la ban de sua tay;
 * file .java sinh ra la artifact build, dung commit.
 *
 * javac -encoding UTF-8 -d build/tools tools/legacy/luna/GenAutoTradeSupport.java
 * java  -cp build/tools GenAutoTradeSupport [template] [properties] [output]
 */
public final class GenAutoTradeSupport {

    public static void main(String[] args) throws Exception {
        String tmplPath = args.length > 0 ? args[0] : "tools/legacy/luna/HSOAutoTradeSupport.java.in";
        String propPath = args.length > 1 ? args[1] : "symbols/autotrade-symbols.properties";
        String outPath = args.length > 2 ? args[2] : "build/generated/HSOAutoTradeSupport.java";

        Properties sym = new Properties();
        InputStream in = new FileInputStream(propPath);
        try {
            sym.load(in);
        } finally {
            in.close();
        }

        String tmpl = readUtf8(new File(tmplPath));
        StringBuilder out = new StringBuilder(tmpl.length() * 4);
        List<String> missing = new ArrayList<String>();
        int used = 0;

        int i = 0;
        while (i < tmpl.length()) {
            char c = tmpl.charAt(i);
            if (c != '@') {
                out.append(c);
                i++;
                continue;
            }
            int end = tmpl.indexOf('@', i + 1);
            if (end < 0) throw new RuntimeException("Marker @ khong dong o offset " + i);
            String key = tmpl.substring(i + 1, end);
            if (key.length() == 0) {          // "@@" -> mot dau @ thuc su
                out.append('@');
                i = end + 1;
                continue;
            }
            String value = sym.getProperty(key);
            if (value == null) {
                if (!missing.contains(key)) missing.add(key);
                out.append("__MISSING_").append(key).append("__");
            } else {
                out.append(value);
                used++;
            }
            i = end + 1;
        }

        if (!missing.isEmpty()) {
            System.out.println("THIEU " + missing.size() + " ky hieu trong " + propPath + ":");
            for (String k : missing) System.out.println("  - " + k);
            throw new RuntimeException("Template tham chieu ky hieu khong co trong bang");
        }

        writeUtf8(new File(outPath), out.toString());
        System.out.println("Da sinh " + outPath + " (" + used + " lan thay ky hieu, "
                + out.length() + " byte)");

        // The old binary-facing implementation is generated as a separate bridge class.
        // HSOAutoTradeSupport itself remains a small ABI facade for the ASM hooks.
        generateLegacyBridge(propPath, sym);
    }

    static void generateLegacyBridge(String propPath, Properties sym) throws Exception {
        File input = new File("tools/legacy/luna/legacy-hook.template");
        String template = readUtf8(input);
        String rendered = substitute(template, propPath, sym);
        rendered = rendered.replace("public final class HSOAutoTradeSupport",
                "public final class LunaGeneratedLegacyHooks");
        rendered = rendered.replace("private HSOAutoTradeSupport()",
                "private LunaGeneratedLegacyHooks()");
        File output = new File("build/generated/LunaGeneratedLegacyHooks.java");
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new RuntimeException("Khong tao duoc " + parent);
        }
        writeUtf8(output, rendered);
        System.out.println("Da sinh " + output + " (legacy bridge; khong sua tay)");
    }

    static String substitute(String template, String propPath, Properties sym) {
        StringBuilder out = new StringBuilder(template.length() * 4);
        List<String> missing = new ArrayList<String>();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c != '@') {
                out.append(c);
                i++;
                continue;
            }
            int end = template.indexOf('@', i + 1);
            if (end < 0) throw new RuntimeException("Marker @ khong dong o offset " + i);
            String key = template.substring(i + 1, end);
            if (key.length() == 0) {
                out.append('@');
                i = end + 1;
                continue;
            }
            String value = sym.getProperty(key);
            if (value == null) {
                if (!missing.contains(key)) missing.add(key);
                out.append("__MISSING_").append(key).append("__");
            } else {
                out.append(value);
            }
            i = end + 1;
        }
        if (!missing.isEmpty()) {
            System.out.println("THIEU " + missing.size() + " ky hieu trong " + propPath + ":");
            for (String key : missing) System.out.println("  - " + key);
            throw new RuntimeException("Legacy bridge tham chieu ky hieu khong co trong bang");
        }
        return out.toString();
    }

    static String readUtf8(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    static void writeUtf8(File f, String s) throws Exception {
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            w.write(s);
        } finally {
            w.close();
        }
    }
}
