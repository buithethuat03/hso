import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Sinh jar chi de BIEN DICH HSOAutoTradeSupport.
 *
 * Hai field cua man hinh cua hang (loai cua hang + danh sach hang) la private, ma support
 * class phai doc chung de biet ban goi mua voi type nao va id ngoc nao. Jar nay la ban sao
 * cua jar goc voi dung hai field do doi thanh public; AutoTradePatcher lam y het voi jar
 * xuat ra, nen luc chay va luc bien dich khop nhau.
 *
 * Noi rong quyen truy cap KHONG the doi ket qua phan giai field: JVM phan giai theo
 * owner + ten + kieu ghi thang trong lenh, quyen chi duoc kiem sau do.
 *
 * javac -encoding UTF-8 -cp "<asm-cp>" -d build/tools tools/legacy/luna/MakeAutoTradeCompileJar.java
 * java  -cp "build/tools;<asm-cp>" MakeAutoTradeCompileJar [input.jar] [output.jar] [symbols.properties]
 */
public final class MakeAutoTradeCompileJar {

    /** Cac field can mo quyen: khoa lop -> cac khoa field. */
    static final String[][] OPEN = {
        {"class.SHOP", "shop.type", "shop.items"},
        {"class.MENUBOX", "menu.isShowing", "menu.title"},
    };

    static final String RUNTIME_SUPPORT = "HSOSKYRuntimeSupport";
    static final String LEGACY_SUPPORT = "LunaGeneratedLegacyHooks";
    static final File GENERATED_CLASSES = new File("build/generated");
    /** Output cua buoc compile source semantic, duoc dong goi de support co the goi truc tiep. */
    static final File SEMANTIC_CLASSES = new File("build/semantic");

    public static void main(String[] args) throws Exception {
        String inPath = args.length > 0 ? args[0] : "HSOLUNA_TRUONGBK_PERFECT.jar";
        String outPath = args.length > 1 ? args[1] : ".autotrade-compile.jar";
        String symbolPath = args.length > 2 ? args[2] : "symbols/autotrade-symbols.properties";

        Properties sym = new Properties();
        InputStream sin = new FileInputStream(symbolPath);
        try {
            sym.load(sin);
        } finally {
            sin.close();
        }

        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        Map<String, Long> times = new LinkedHashMap<String, Long>();
        JarFile jar = new JarFile(inPath);
        try {
            Enumeration<JarEntry> it = jar.entries();
            while (it.hasMoreElements()) {
                JarEntry e = it.nextElement();
                if (e.isDirectory()) continue;
                entries.put(e.getName(), readAll(jar.getInputStream(e)));
                times.put(e.getName(), Long.valueOf(e.getTime()));
            }
        } finally {
            jar.close();
        }

        int opened = openFields(entries, sym);
        System.out.println("Mo quyen " + opened + " field");

        /*
         * Thay HSOSKYRuntimeSupport bang ban vua bien dich tren dia - dung y het viec
         * AutoTradePatcher lam khi ghi jar xuat ra (buoc [7]).
         *
         * Vi sao can: jar goc mang ban CU cua lop do, con ban tren dia moi la ban co
         * setNoClipEnabled(boolean) de Auto Di Buon tat noclip. Lay ban cu thi javac bao
         * "cannot find symbol". Va de lech thi ban bien dich khac ban chay, tuc bien dich
         * duoc chua chac chay duoc - dung thu phai tranh nhat.
         */
        File rtSrc = new File(GENERATED_CLASSES, RUNTIME_SUPPORT + ".class");
        if (!rtSrc.exists()) {
            throw new RuntimeException("Thieu " + RUNTIME_SUPPORT
                    + ".class - chay javac cho no truoc.");
        }
        InputStream rin = new FileInputStream(rtSrc);
        try {
            entries.put(RUNTIME_SUPPORT + ".class", readAll(rin));
        } finally {
            rin.close();
        }
        System.out.println("Thay " + RUNTIME_SUPPORT + " bang ban vua bien dich tren dia");

        File legacySrc = new File(GENERATED_CLASSES, LEGACY_SUPPORT + ".class");
        if (!legacySrc.exists()) {
            throw new RuntimeException("Thieu " + legacySrc.getPath()
                    + " - chay GenAutoTradeSupport va compile legacy bridge truoc.");
        }
        InputStream lin = new FileInputStream(legacySrc);
        try {
            entries.put(LEGACY_SUPPORT + ".class", readAll(lin));
        } finally {
            lin.close();
        }
        times.put(LEGACY_SUPPORT + ".class", Long.valueOf(legacySrc.lastModified()));
        System.out.println("Them generated legacy bridge " + LEGACY_SUPPORT);

        int semanticCount = addSemanticClasses(entries, times);
        if (semanticCount == 0) {
            throw new RuntimeException("Khong co class semantic trong "
                    + SEMANTIC_CLASSES.getPath() + " - hay compile src/luna truoc.");
        }
        System.out.println("Them " + semanticCount + " class semantic tu "
                + SEMANTIC_CLASSES.getPath());

        OutputStream fos = new FileOutputStream(outPath);
        JarOutputStream jos = new JarOutputStream(fos);
        try {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                Long t = times.get(e.getKey());
                if (t != null && t.longValue() >= 0) ze.setTime(t.longValue());
                jos.putNextEntry(ze);
                jos.write(e.getValue());
                jos.closeEntry();
            }
        } finally {
            jos.close();
        }
        System.out.println("Da ghi " + outPath + " (" + new File(outPath).length() + " byte)");
    }

    /** Doi private/protected thanh public cho cac field trong bang OPEN. Tra ve so field da doi. */
    static int openFields(Map<String, byte[]> entries, Properties sym) {
        int count = 0;
        for (int i = 0; i < OPEN.length; i++) {
            String owner = sym.getProperty(OPEN[i][0]);
            if (owner == null) throw new RuntimeException("Thieu ky hieu " + OPEN[i][0]);
            byte[] raw = entries.get(owner + ".class");
            if (raw == null) throw new RuntimeException("Khong tim thay class " + OPEN[i][0]);
            ClassNode cn = new ClassNode();
            new ClassReader(raw).accept(cn, 0);
            for (int k = 1; k < OPEN[i].length; k++) {
                String name = sym.getProperty(OPEN[i][k]);
                if (name == null) throw new RuntimeException("Thieu ky hieu " + OPEN[i][k]);
                boolean found = false;
                for (FieldNode f : cn.fields) {
                    if (!f.name.equals(name)) continue;
                    f.access = (f.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED))
                            | Opcodes.ACC_PUBLIC;
                    found = true;
                    count++;
                }
                if (!found) throw new RuntimeException("Khong tim thay field " + OPEN[i][k]);
            }
            ClassWriter cw = new ClassWriter(0);
            cn.accept((ClassVisitor) cw);
            entries.put(owner + ".class", cw.toByteArray());
        }
        return count;
    }

    static byte[] readAll(InputStream in) throws Exception {
        byte[] buf = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    /** Chen cac class src/luna da bien dich vao compile jar, khong chen file nguon. */
    static int addSemanticClasses(Map<String, byte[]> entries, Map<String, Long> times)
            throws Exception {
        if (!SEMANTIC_CLASSES.isDirectory()) {
            throw new RuntimeException("Thieu thu muc " + SEMANTIC_CLASSES.getPath()
                    + " - chay javac cho src/luna truoc.");
        }
        return addSemanticClasses(SEMANTIC_CLASSES, SEMANTIC_CLASSES, entries, times);
    }

    static int addSemanticClasses(File root, File current, Map<String, byte[]> entries,
            Map<String, Long> times) throws Exception {
        File[] files = current.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                count += addSemanticClasses(root, file, entries, times);
                continue;
            }
            if (!file.getName().endsWith(".class")) continue;
            String relative = file.getPath().substring(root.getPath().length() + 1)
                    .replace(File.separatorChar, '/');
            if (!relative.startsWith("luna/") && !relative.startsWith("luna\\")) {
                continue;
            }
            FileInputStream in = new FileInputStream(file);
            try {
                entries.put(relative, readAll(in));
            } finally {
                in.close();
            }
            times.put(relative, Long.valueOf(file.lastModified()));
            count++;
        }
        return count;
    }
}
