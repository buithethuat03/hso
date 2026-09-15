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
 * Tim BAN DO O THAT de mod tu tim duong khi tat Noclip.
 *
 * Diem neo la ham hoi o cua TILEMAP: method (II)I duy nhat co dung MOT iaload. Than no la
 *     idx = (pixelY / TILE) * width + (pixelX / TILE);  return tiles[idx];
 * nen doc thang tu bytecode ra duoc ca hai thu can: mang o (field [I) va chieu rong (field I
 * dung ngay truoc imul). Khong doan theo ten - ten obfuscate bi dung lai cho ca class, field
 * lan method, o day chinh ten mang o trung voi ten field player.y.
 *
 * javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AddTileGridSymbols.java
 * java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AddTileGridSymbols
 */
public final class AddTileGridSymbols {

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
        InputStream ps = new FileInputStream("autotrade-symbols.properties");
        sym.load(ps);
        ps.close();
        String tilemap = req(sym, "class.TILEMAP");
        ClassNode tm = classes.get(tilemap);
        if (tm == null) throw new RuntimeException("Khong thay lop TILEMAP trong " + jarName);

        MethodNode query = null;
        for (MethodNode m : tm.methods) {
            if (!"(II)I".equals(m.desc) || (m.access & Opcodes.ACC_STATIC) != 0) continue;
            if (count(m, Opcodes.IALOAD) != 1) continue;
            if (query != null) throw new RuntimeException("Nhieu ung vien ham hoi o");
            query = m;
        }
        if (query == null) throw new RuntimeException("Khong tim thay ham hoi o (II)I");

        // Mang o: field [I cua TILEMAP duoc doc ngay truoc iaload duy nhat.
        String tiles = null;
        for (AbstractInsnNode n = query.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() != Opcodes.IALOAD) continue;
            for (AbstractInsnNode p = n.getPrevious(); p != null; p = p.getPrevious()) {
                if (p.getOpcode() != Opcodes.GETFIELD) continue;
                FieldInsnNode f = (FieldInsnNode) p;
                if (!tilemap.equals(f.owner) || !"[I".equals(f.desc)) continue;
                tiles = f.name;
                break;
            }
        }
        if (tiles == null) throw new RuntimeException("Khong tim thay mang o [I");

        // Chieu rong: field I cua TILEMAP dung ngay truoc imul (idx = ty * width + tx).
        String width = null;
        for (AbstractInsnNode n = query.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() != Opcodes.IMUL) continue;
            AbstractInsnNode p = n.getPrevious();
            while (p != null && p.getOpcode() < 0) p = p.getPrevious();
            if (p == null || p.getOpcode() != Opcodes.GETFIELD) continue;
            FieldInsnNode f = (FieldInsnNode) p;
            if (!tilemap.equals(f.owner) || !"I".equals(f.desc)) continue;
            if (width != null && !width.equals(f.name)) throw new RuntimeException("Nhieu ung vien chieu rong");
            width = f.name;
        }
        if (width == null) throw new RuntimeException("Khong tim thay field chieu rong");

        System.out.println("tilemap.query  len=" + query.name.length() + "  (II)I, 1 iaload");
        System.out.println("tilemap.tiles  len=" + tiles.length() + "  [I");
        System.out.println("tilemap.width  len=" + width.length() + "  I");
        System.out.println("trung ten voi player.y ? " + tiles.equals(sym.getProperty("player.y")));

        Map<String, String> add = new LinkedHashMap<String, String>();
        add.put("tilemap.tiles", tiles);
        add.put("tilemap.width", width);
        merge(add);
    }

    static int count(MethodNode m, int opcode) {
        int n = 0;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i.getOpcode() == opcode) n++;
        }
        return n;
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
                new FileInputStream("autotrade-symbols.properties"), "UTF-8"));
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
        Writer w = new OutputStreamWriter(new FileOutputStream("autotrade-symbols.properties"), "UTF-8");
        for (String x : head) { w.write(x); w.write("\n"); }
        for (String x : body) { w.write(x); w.write("\n"); }
        w.close();
        System.out.println("Da ghi (tong " + body.size() + " ky hieu)");
    }
}
