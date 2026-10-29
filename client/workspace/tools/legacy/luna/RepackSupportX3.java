import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Thay cac lop support trong ban x3 (ba the hien trong mot jar).
 *
 * Ban x3 la CHINH ban mod duoc nhan ba: 193 lop o goi mac dinh, 193 ban sao o goi b/, 193
 * o goi c/ - moi ban mot o luu RMS rieng nen ba nhan vat dang nhap cung luc duoc. Sua mot
 * lop support thi phai thay du CA BA ban, va hai ban b/ c/ tham chieu toi cac lop da doi
 * ten theo goi.
 *
 * Doi ten theo goi o day lam bang tay tren ClassNode vi trong thu muc khong co asm-commons
 * (ClassRemapper). Bu lai co mot phep tu kiem manh hon doc code: chay dung phep doi ten do
 * len lop CU cua chinh jar dau vao, roi so voi lop b/ va c/ CU dang nam san trong jar. Hai
 * ben trung nhau nghia la phep doi ten tai tao dung nhung gi cong cu goi da lam.
 *
 *   java -cp ".;asm-9.7.jar;asm-tree-9.7.jar" RepackSupportX3 \
 *        autotrade_v22_x3.jar autotrade_v23_x3.jar HSOAutoTradeSupport HSOSKYRuntimeSupport
 */
public final class RepackSupportX3 {

    static final String[] PKGS = {"b", "c"};

    public static void main(String[] args) throws Exception {
        String inPath = args[0];
        String outPath = args[1];
        List<String> names = new ArrayList<String>();
        for (int i = 2; i < args.length; i++) names.add(args[i]);
        if (names.isEmpty()) throw new RuntimeException("chua noi thay lop nao");

        File outFile = new File(outPath);
        if (outFile.exists()) {
            throw new RuntimeException(outPath + " da ton tai - dat ten khac de con lui lai duoc.");
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
        System.out.println("[1] Doc " + inPath + ": " + entries.size() + " entry");

        // ------------------------------------------------ bang doi ten cho tung goi
        Map<String, Map<String, String>> maps = new LinkedHashMap<String, Map<String, String>>();
        for (int p = 0; p < PKGS.length; p++) {
            String pkg = PKGS[p];
            Map<String, String> m = new LinkedHashMap<String, String>();
            for (String key : entries.keySet()) {
                if (!key.endsWith(".class") || key.indexOf('/') >= 0) continue;
                String root = key.substring(0, key.length() - 6);
                if (entries.containsKey(pkg + "/" + root + ".class")) {
                    m.put(root, pkg + "/" + root);
                }
            }
            maps.put(pkg, m);
            System.out.println("[2] Goi " + pkg + "/: doi ten " + m.size() + " lop");
        }

        // ------------------------------------------------ tu kiem tren lop CU
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            byte[] oldRoot = entries.get(n + ".class");
            if (oldRoot == null) throw new RuntimeException("jar khong co " + n + ".class");
            for (int p = 0; p < PKGS.length; p++) {
                String pkg = PKGS[p];
                byte[] oldPkg = entries.get(pkg + "/" + n + ".class");
                if (oldPkg == null) throw new RuntimeException("jar khong co " + pkg + "/" + n);
                byte[] made = remap(oldRoot, maps.get(pkg));
                String why = sameShape(made, oldPkg);
                if (why != null) {
                    throw new RuntimeException("TU KIEM THAT BAI cho " + pkg + "/" + n
                            + ": " + why + " - phep doi goi khong tai tao duoc ban goc,"
                            + " dung tin no de ghi jar moi");
                }
                System.out.println("[3] Tu kiem " + pkg + "/" + n + ": khop ban co san");
            }
        }

        // ------------------------------------------------ thay bang ban moi tren dia
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            File f = new File(n + ".class");
            if (!f.exists()) throw new RuntimeException("Thieu " + n + ".class - chay javac truoc.");
            byte[] fresh = readAll(new FileInputStream(f));
            entries.put(n + ".class", fresh);
            for (int p = 0; p < PKGS.length; p++) {
                String pkg = PKGS[p];
                entries.put(pkg + "/" + n + ".class", remap(fresh, maps.get(pkg)));
            }
            System.out.println("[4] Thay " + n + " o ca 3 goi (" + fresh.length + " byte ban goc)");
        }

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
        System.out.println("[5] Ghi " + outPath + " (" + outFile.length() + " byte)");

        verify(outPath, names);
        System.out.println("[6] Verify jar vua ghi: OK");
    }

    // ---------------------------------------------------------------- doi ten theo goi

    static byte[] remap(byte[] raw, Map<String, String> map) {
        ClassNode cn = new ClassNode();
        new ClassReader(raw).accept(cn, 0);

        cn.name = cls(cn.name, map);
        cn.superName = cls(cn.superName, map);
        if (cn.interfaces != null) {
            for (int i = 0; i < cn.interfaces.size(); i++) {
                cn.interfaces.set(i, cls(cn.interfaces.get(i), map));
            }
        }
        for (FieldNode f : cn.fields) f.desc = desc(f.desc, map);

        for (MethodNode m : cn.methods) {
            m.desc = desc(m.desc, map);
            if (m.exceptions != null) {
                for (int i = 0; i < m.exceptions.size(); i++) {
                    m.exceptions.set(i, cls(m.exceptions.get(i), map));
                }
            }
            if (m.tryCatchBlocks != null) {
                for (TryCatchBlockNode t : m.tryCatchBlocks) t.type = cls(t.type, map);
            }
            if (m.localVariables != null) {
                for (LocalVariableNode v : m.localVariables) v.desc = desc(v.desc, map);
            }
            if (m.instructions == null) continue;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof FieldInsnNode) {
                    FieldInsnNode x = (FieldInsnNode) n;
                    x.owner = cls(x.owner, map);
                    x.desc = desc(x.desc, map);
                } else if (n instanceof MethodInsnNode) {
                    MethodInsnNode x = (MethodInsnNode) n;
                    x.owner = cls(x.owner, map);
                    x.desc = desc(x.desc, map);
                } else if (n instanceof TypeInsnNode) {
                    TypeInsnNode x = (TypeInsnNode) n;
                    x.desc = x.desc.charAt(0) == '[' ? desc(x.desc, map) : cls(x.desc, map);
                } else if (n instanceof MultiANewArrayInsnNode) {
                    MultiANewArrayInsnNode x = (MultiANewArrayInsnNode) n;
                    x.desc = desc(x.desc, map);
                } else if (n instanceof LdcInsnNode) {
                    LdcInsnNode x = (LdcInsnNode) n;
                    if (x.cst instanceof Type) {
                        Type t = (Type) x.cst;
                        x.cst = Type.getType(desc(t.getDescriptor(), map));
                    }
                } else if (n instanceof InvokeDynamicInsnNode) {
                    throw new RuntimeException("gap invokedynamic - lop nay khong phai CLDC,"
                            + " phep doi goi o day chua xu ly");
                }
            }
        }
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }

    static String cls(String name, Map<String, String> map) {
        if (name == null) return null;
        String hit = map.get(name);
        return hit != null ? hit : name;
    }

    /** Doi ten lop trong MOI descriptor, ke ca mang va tham so cua method. */
    static String desc(String d, Map<String, String> map) {
        if (d == null) return null;
        StringBuilder sb = new StringBuilder(d.length());
        int i = 0;
        while (i < d.length()) {
            char c = d.charAt(i);
            if (c != 'L') { sb.append(c); i++; continue; }
            int end = d.indexOf(';', i);
            if (end < 0) { sb.append(d.substring(i)); break; }
            sb.append('L').append(cls(d.substring(i + 1, end), map)).append(';');
            i = end + 1;
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- kiem tra

    /**
     * So hai lop theo HINH chu khong theo byte: ten lop, danh sach field, danh sach method
     * kem descriptor, va chuoi opcode cua tung method. Thu tu hang trong constant pool thi
     * khac nhau tuy trinh bien dich, khong co y nghia gi.
     */
    static String sameShape(byte[] a, byte[] b) {
        ClassNode ca = new ClassNode();
        ClassNode cb = new ClassNode();
        new ClassReader(a).accept(ca, 0);
        new ClassReader(b).accept(cb, 0);
        if (!ca.name.equals(cb.name)) return "ten lop " + ca.name + " != " + cb.name;
        if (ca.fields.size() != cb.fields.size()) {
            return "so field " + ca.fields.size() + " != " + cb.fields.size();
        }
        for (int i = 0; i < ca.fields.size(); i++) {
            FieldNode fa = (FieldNode) ca.fields.get(i);
            FieldNode fb = (FieldNode) cb.fields.get(i);
            if (!fa.name.equals(fb.name) || !fa.desc.equals(fb.desc)) {
                return "field thu " + i + " lech";
            }
        }
        if (ca.methods.size() != cb.methods.size()) {
            return "so method " + ca.methods.size() + " != " + cb.methods.size();
        }
        for (int i = 0; i < ca.methods.size(); i++) {
            MethodNode ma = (MethodNode) ca.methods.get(i);
            MethodNode mb = (MethodNode) cb.methods.get(i);
            if (!ma.name.equals(mb.name) || !ma.desc.equals(mb.desc)) {
                return "method thu " + i + " lech ten/descriptor";
            }
            String sa = ops(ma);
            String sb2 = ops(mb);
            if (!sa.equals(sb2)) return "than method " + ma.name + " lech chuoi lenh";
        }
        return null;
    }

    static String ops(MethodNode m) {
        StringBuilder sb = new StringBuilder();
        if (m.instructions == null) return "";
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() < 0) continue;
            sb.append(n.getOpcode()).append(',');
            if (n instanceof FieldInsnNode) {
                FieldInsnNode x = (FieldInsnNode) n;
                sb.append(x.owner).append('.').append(x.name).append(':').append(x.desc);
            } else if (n instanceof MethodInsnNode) {
                MethodInsnNode x = (MethodInsnNode) n;
                sb.append(x.owner).append('.').append(x.name).append(x.desc);
            } else if (n instanceof TypeInsnNode) {
                sb.append(((TypeInsnNode) n).desc);
            } else if (n instanceof LdcInsnNode) {
                sb.append(String.valueOf(((LdcInsnNode) n).cst));
            }
            sb.append(';');
        }
        return sb.toString();
    }

    /** Doc lai jar vua ghi: moi lop tham chieu toi phai co that trong chinh jar do. */
    static void verify(String path, List<String> names) throws Exception {
        Map<String, byte[]> got = new LinkedHashMap<String, byte[]>();
        JarFile jar = new JarFile(path);
        try {
            Enumeration<JarEntry> it = jar.entries();
            while (it.hasMoreElements()) {
                JarEntry e = it.nextElement();
                if (e.isDirectory()) continue;
                got.put(e.getName(), readAll(jar.getInputStream(e)));
            }
        } finally {
            jar.close();
        }
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            check(got, n + ".class");
            for (int p = 0; p < PKGS.length; p++) check(got, PKGS[p] + "/" + n + ".class");
        }
    }

    static void check(Map<String, byte[]> entries, String key) {
        byte[] raw = entries.get(key);
        if (raw == null) throw new RuntimeException("jar thieu " + key);
        ClassNode cn = new ClassNode();
        new ClassReader(raw).accept(cn, 0);
        for (MethodNode m : cn.methods) {
            if (m.instructions == null) continue;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                String owner = null;
                if (n instanceof FieldInsnNode) owner = ((FieldInsnNode) n).owner;
                else if (n instanceof MethodInsnNode) owner = ((MethodInsnNode) n).owner;
                if (owner == null || owner.startsWith("[")) continue;
                if (owner.startsWith("java/") || owner.startsWith("javax/")) continue;
                if (!entries.containsKey(owner + ".class")) {
                    throw new RuntimeException(key + " goi toi " + shrink(owner)
                            + " ma jar khong co lop do - doi goi sot cho nao do");
                }
            }
        }
    }

    static String shrink(String s) {
        return s.length() > 30 ? s.substring(0, 6) + "...<" + s.length() + "ch>" : s;
    }

    static byte[] readAll(InputStream in) throws Exception {
        byte[] buf = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }
}
