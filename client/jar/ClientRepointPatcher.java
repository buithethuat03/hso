import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
 * Trỏ client HSOSKY sang server mới: thay các chuỗi IP server trong bytecode.
 * Dùng ASM vì độ dài chuỗi khác nhau (không thể sed thô trong jar do constant pool có prefix độ dài).
 * Cổng client dùng sẵn là 19129 (khớp server), nên chỉ cần đổi IP.
 *
 *   java -cp ".;asm-9.7.jar;asm-tree-9.7.jar" ClientRepointPatcher <input.jar> <output.jar> <newIp>
 */
public final class ClientRepointPatcher {

    // Các IP server gốc của HSOSKY cần thay.
    private static final String[] OLD_IPS = {"46.137.254.172", "54.254.156.202"};

    public static void main(String[] args) throws Exception {
        String in = args.length > 0 ? args[0] : "HSOSKY.jar";
        String out = args.length > 1 ? args[1] : "HSOSKY_EC2.jar";
        String newIp = args.length > 2 ? args[2] : "13.229.47.78";

        int replaced = 0;
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
                                if (insn instanceof LdcInsnNode) {
                                    LdcInsnNode ldc = (LdcInsnNode) insn;
                                    if (ldc.cst instanceof String) {
                                        for (String oldIp : OLD_IPS) {
                                            if (oldIp.equals(ldc.cst)) {
                                                ldc.cst = newIp;
                                                changed = true;
                                                replaced++;
                                                System.out.println("  " + cn.name + ": " + oldIp + " -> " + newIp);
                                            }
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
        System.out.println("[+] Da thay " + replaced + " chuoi IP. Xuat: " + out);
        if (replaced == 0) {
            System.out.println("[!] Khong thay chuoi IP nao — kiem tra lai OLD_IPS/jar dau vao.");
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
