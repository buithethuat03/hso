import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Tim TAM DANH (theo o) cua nhan vat.
 *
 * Diem neo la chinh ham ve vong tron tam danh cua mod: no tinh ban kinh bang
 *     r = player.<tamDanh> * 3 / 2
 * roi goi Graphics.drawArc. Nen doc bytecode cua ham do, lay field int cua PLAYER dung ngay
 * truoc cap lenh (iconst_3, imul).
 *
 * javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AddAtkRangeSymbol.java
 * java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AddAtkRangeSymbol [jar]
 */
public final class AddAtkRangeSymbol {

    public static void main(String[] args) throws Exception {
        String jarName = args.length > 0 ? args[0] : "HSOLUNA_TRUONGBK_PERFECT.jar";
        Map<String, ClassNode> classes = new LinkedHashMap<String, ClassNode>();
        JarFile jar = new JarFile(jarName);
        Enumeration<JarEntry> it = jar.entries();
        while (it.hasMoreElements()) {
            JarEntry e = it.nextElement();
            if (!e.getName().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            InputStream in = jar.getInputStream(e);
            new ClassReader(in).accept(cn, 0);
            in.close();
            classes.put(cn.name, cn);
        }
        jar.close();

        Properties sym = new Properties();
        InputStream ps = new FileInputStream("symbols/autotrade-symbols.properties");
        sym.load(ps);
        ps.close();
        String engine = req(sym, "class.ENGINE");
        String player = req(sym, "class.PLAYER");
        String paint = req(sym, "engine.method.paintArc");

        ClassNode en = classes.get(engine);
        if (en == null) throw new RuntimeException("Khong thay lop ENGINE trong " + jarName);
        MethodNode m = null;
        for (MethodNode x : en.methods) {
            if (paint.equals(x.name)) m = x;
        }
        if (m == null) throw new RuntimeException("Khong thay ham ve vong tron tam danh");

        String range = null;
        String owner = null;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() != Opcodes.GETFIELD) continue;
            FieldInsnNode f = (FieldInsnNode) n;
            if (!"I".equals(f.desc)) continue;
            owner = f.owner;
            AbstractInsnNode a = f.getNext();
            while (a != null && a.getOpcode() < 0) a = a.getNext();
            if (a == null || a.getOpcode() != Opcodes.ICONST_3) continue;
            AbstractInsnNode b = a.getNext();
            while (b != null && b.getOpcode() < 0) b = b.getNext();
            if (b == null || b.getOpcode() != Opcodes.IMUL) continue;
            if (range != null && !range.equals(f.name)) throw new RuntimeException("Nhieu ung vien");
            range = f.name;
        }
        if (range == null) throw new RuntimeException("Khong tim thay field tam danh");

        System.out.println("owner = PLAYER ? " + player.equals(owner));
        System.out.println("player.atkRange len=" + range.length() + "  I  (ban kinh ve = x*3/2 o)");
        Map<String, String> add = new LinkedHashMap<String, String>();
        add.put("player.atkRange", range);
        merge(add);
    }

    static String req(Properties p, String k) {
        String v = p.getProperty(k);
        if (v == null) throw new RuntimeException("Thieu ky hieu " + k);
        return v;
    }

    static void merge(Map<String, String> add) throws IOException {
        List<String> head = new ArrayList<String>();
        List<String> body = new ArrayList<String>();
        BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream("symbols/autotrade-symbols.properties"), "UTF-8"));
        String l;
        while ((l = r.readLine()) != null) {
            if (l.startsWith("#")) { head.add(l); continue; }
            if (l.trim().length() == 0) continue;
            boolean skip = false;
            for (String k : add.keySet()) if (l.startsWith(k + "=")) skip = true;
            if (!skip) body.add(l);
        }
        r.close();
        for (Map.Entry<String, String> e : add.entrySet()) body.add(e.getKey() + "=" + e.getValue());
        Collections.sort(body);
        Writer w = new OutputStreamWriter(new FileOutputStream("symbols/autotrade-symbols.properties"), "UTF-8");
        for (String x : head) { w.write(x); w.write("\n"); }
        for (String x : body) { w.write(x); w.write("\n"); }
        w.close();
        System.out.println("Da ghi (tong " + body.size() + " ky hieu)");
    }
}
