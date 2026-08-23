import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Trỏ một dòng server trong client Hiệp Sĩ (TeaMobi / SilverKnight) về server riêng bằng ASM.
 * Thay chuỗi hằng trong constant pool (không sed thô được vì độ dài khác + modified-UTF8).
 *
 * Mặc định: đổi server "Chiến Thần" (hs1.teamobi.com) -> IP mới, đổi tên hiển thị thành "HSO FREE".
 * Các server TeaMobi khác giữ nguyên. Cổng client dùng sẵn là 19129 (khớp server), không cần đổi.
 *
 *   java -cp ".;asm-9.7.jar;asm-tree-9.7.jar" ClientRepointPatcher <input.jar> <output.jar> <newIp>
 */
public final class ClientRepointPatcher {

    public static void main(String[] args) throws Exception {
        String in = args.length > 0 ? args[0] : "KnightOnline_402.jar";
        String out = args.length > 1 ? args[1] : "KnightOnline_402_EC2.jar";
        String newIp = args.length > 2 ? args[2] : "13.229.47.78";

        // {chuỗi cũ trong jar, chuỗi mới}. Chỉ chuỗi khớp CHÍNH XÁC mới bị thay.
        String[][] repl = {
            {"hs1.teamobi.com", newIp},   // host server "Chiến Thần" -> server riêng
            {"Chiến Thần", "HSO FREE"},   // tên hiển thị trong panel Chọn Máy Chủ
        };

        int[] counts = new int[repl.length];
        File tmp = new File(out + ".building");
        try (JarFile jar = new JarFile(in);
                JarOutputStream jos = new JarOutputStream(new FileOutputStream(tmp))) {
            Enumeration<JarEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                byte[] data = readAll(jar.getInputStream(e));
                if (e.getName().endsWith(".class")) {
                    ClassNode cn = new ClassNode();
                    new ClassReader(data).accept(cn, 0);
                    boolean changed = false;
                    if (cn.methods != null) {
                        for (MethodNode mn : cn.methods) {
                            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String) {
                                    LdcInsnNode ldc = (LdcInsnNode) insn;
                                    for (int i = 0; i < repl.length; i++) {
                                        if (repl[i][0].equals(ldc.cst)) {
                                            ldc.cst = repl[i][1];
                                            counts[i]++;
                                            changed = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (changed) {
                        ClassWriter cw = new ClassWriter(0);
                        cn.accept(cw);
                        data = cw.toByteArray();
                    }
                }
                JarEntry ne = new JarEntry(e.getName());
                ne.setTime(e.getTime());
                jos.putNextEntry(ne);
                jos.write(data);
                jos.closeEntry();
            }
        }
        File outFile = new File(out);
        if (outFile.exists()) {
            outFile.delete();
        }
        if (!tmp.renameTo(outFile)) {
            throw new IllegalStateException("Khong the tao " + out);
        }
        int total = 0;
        for (int i = 0; i < repl.length; i++) {
            System.out.println("  \"" + repl[i][0] + "\" -> \"" + repl[i][1] + "\" : " + counts[i] + " lan");
            total += counts[i];
        }
        System.out.println("[+] Tong thay: " + total + " | Xuat: " + out);
        if (total == 0) {
            System.out.println("[!] Khong thay chuoi nao — kiem tra lai jar dau vao / chuoi can thay.");
        }
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}
