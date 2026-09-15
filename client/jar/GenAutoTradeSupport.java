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
 * Sinh HSOAutoTradeSupport.java tu template HSOAutoTradeSupport.java.in bang cach thay
 * moi &#64;key&#64; bang ten obfuscate tuong ung trong autotrade-symbols.properties.
 *
 * Ly do phai qua mot buoc sinh: ten obfuscate trong jar dai 210-250 ky tu, viet thang
 * vao source thi khong ai doc noi va rat de go nham. Template moi la ban de sua tay;
 * file .java sinh ra la artifact build, dung commit.
 *
 * javac -encoding UTF-8 GenAutoTradeSupport.java
 * java  GenAutoTradeSupport [template] [properties] [output]
 */
public final class GenAutoTradeSupport {

    public static void main(String[] args) throws Exception {
        String tmplPath = args.length > 0 ? args[0] : "HSOAutoTradeSupport.java.in";
        String propPath = args.length > 1 ? args[1] : "autotrade-symbols.properties";
        String outPath = args.length > 2 ? args[2] : "HSOAutoTradeSupport.java";

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
