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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Trỏ client HSOSKY sang server mới bằng cách sửa bytecode (ASM):
 *  - Đổi host server (domain sv.hsosky.com + 2 IP cũ) -> IP mới.
 *  - Đổi tên hiển thị "HSOSKY" -> "HSO FREE" trong panel Chọn Máy Chủ.
 *  - Chuẩn hoá cổng 19130 -> 19129 (chỉ trong class danh sách server) để mọi dòng đều vào
 *    đúng server (server chỉ nghe 19129). Dòng đầu (mặc định) cũng là server mới.
 *
 *   java -cp ".;asm-9.7.jar;asm-tree-9.7.jar" ClientRepointPatcher <input.jar> <output.jar> <newIp>
 */
public final class ClientRepointPatcher implements Opcodes {

    // Mọi host server gốc cần trỏ về server mới (domain + 2 IP dự phòng cũ).
    private static final String[] OLD_HOSTS = {"sv.hsosky.com", "46.137.254.172", "54.254.156.202"};
    private static final String OLD_NAME = "HSOSKY";
    private static final String NEW_NAME = "HSO FREE";
    private static final int OLD_PORT = 19130; // cổng phụ vài dòng dùng -> ép về cổng chính
    private static final int NEW_PORT = 19129;

    public static void main(String[] args) throws Exception {
        String in = args.length > 0 ? args[0] : "HSOSKY.jar";
        String out = args.length > 1 ? args[1] : "HSOSKY_EC2.jar";
        String newIp = args.length > 2 ? args[2] : "13.229.47.78";

        int hostN = 0, nameN = 0, portN = 0;
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
                    boolean serverListClass = false;

                    // Pass 1: xác định đây có phải class danh sách server (chứa host gốc) không.
                    if (cn.methods != null) {
                        outer:
                        for (MethodNode mn : cn.methods) {
                            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String) {
                                    String s = (String) ((LdcInsnNode) insn).cst;
                                    for (String h : OLD_HOSTS) {
                                        if (h.equals(s)) {
                                            serverListClass = true;
                                            break outer;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pass 2: thay chuỗi (host + tên), và cổng nếu là class danh sách server.
                    if (cn.methods != null) {
                        for (MethodNode mn : cn.methods) {
                            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                                if (insn instanceof LdcInsnNode) {
                                    LdcInsnNode ldc = (LdcInsnNode) insn;
                                    if (ldc.cst instanceof String) {
                                        String s = (String) ldc.cst;
                                        boolean isHost = false;
                                        for (String h : OLD_HOSTS) {
                                            if (h.equals(s)) {
                                                isHost = true;
                                                break;
                                            }
                                        }
                                        if (isHost) {
                                            ldc.cst = newIp;
                                            changed = true;
                                            hostN++;
                                        } else if (OLD_NAME.equals(s)) {
                                            ldc.cst = NEW_NAME;
                                            changed = true;
                                            nameN++;
                                        }
                                    } else if (serverListClass && ldc.cst instanceof Integer
                                            && ((Integer) ldc.cst) == OLD_PORT) {
                                        ldc.cst = NEW_PORT;
                                        changed = true;
                                        portN++;
                                    }
                                } else if (serverListClass && insn instanceof IntInsnNode) {
                                    IntInsnNode ii = (IntInsnNode) insn;
                                    if ((ii.getOpcode() == SIPUSH || ii.getOpcode() == BIPUSH)
                                            && ii.operand == OLD_PORT) {
                                        ii.operand = NEW_PORT;
                                        changed = true;
                                        portN++;
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
        System.out.println("[+] host->" + newIp + ": " + hostN + " | ten '" + OLD_NAME + "'->'" + NEW_NAME
                + "': " + nameN + " | cong " + OLD_PORT + "->" + NEW_PORT + ": " + portN);
        System.out.println("[+] Xuat: " + out);
        if (hostN == 0 && nameN == 0) {
            System.out.println("[!] Khong thay host/ten nao — kiem tra lai jar dau vao.");
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
