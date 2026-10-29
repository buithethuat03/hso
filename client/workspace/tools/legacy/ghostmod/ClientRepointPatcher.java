import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public final class ClientRepointPatcher implements Opcodes {

    public static void main(String[] args) throws Exception {
        String in = args.length > 0 ? args[0] : "KnightOnline_402.jar";
        String out = args.length > 1 ? args[1] : "client_goc.jar";
        String newIp = args.length > 2 ? args[2] : "127.0.0.1";

        File inputFile = new File(in);
        if (!inputFile.exists()) {
            System.err.println("Input file not found: " + in);
            System.exit(1);
        }

        System.out.println("[*] Reading " + in);
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        JarFile jar = new JarFile(inputFile);
        Enumeration<JarEntry> en = jar.entries();
        while (en.hasMoreElements()) {
            JarEntry entry = en.nextElement();
            InputStream is = jar.getInputStream(entry);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            is.close();
            entries.put(entry.getName(), baos.toByteArray());
        }
        jar.close();

        int patchedCount = 0;

        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String name = entry.getKey();
            if (!name.endsWith(".class")) continue;

            byte[] data = entry.getValue();
            ClassReader cr = new ClassReader(data);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);
            boolean changed = false;

            // 1. Patch u.<init>() to bypass dead HTTP request to getserver.php
            if ("u.class".equals(name) || "u".equals(cn.name)) {
                for (MethodNode mn : cn.methods) {
                    if ("<init>".equals(mn.name) && "()V".equals(mn.desc)) {
                        mn.instructions.clear();
                        mn.instructions.add(new VarInsnNode(ALOAD, 0));
                        mn.instructions.add(new MethodInsnNode(INVOKESPECIAL, "p", "<init>", "()V", false));
                        mn.instructions.add(new MethodInsnNode(INVOKESTATIC, "u", "b", "()V", false));
                        mn.instructions.add(new InsnNode(RETURN));
                        mn.tryCatchBlocks.clear();
                        changed = true;
                        System.out.println("[+] Patched u.<init>() -> directly invokes u.b()");
                    }
                }
            }

            // 2. Patch fu.class: default server index fu.I = 0 instead of 4
            if ("fu.class".equals(name) || "fu".equals(cn.name)) {
                for (MethodNode mn : cn.methods) {
                    if ("<clinit>".equals(mn.name)) {
                        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                            if (insn.getOpcode() == BIPUSH && ((IntInsnNode) insn).operand == 4) {
                                AbstractInsnNode next = insn.getNext();
                                if (next != null && next.getOpcode() == PUTSTATIC && "I".equals(((FieldInsnNode) next).name)) {
                                    ((IntInsnNode) insn).operand = 0;
                                    changed = true;
                                    System.out.println("[+] Patched fu.I default server index 4 -> 0");
                                }
                            }
                        }
                    }
                }
            }

            // 3. Replace all Teamobi / server domains with newIp across all classes
            if (cn.methods != null) {
                for (MethodNode mn : cn.methods) {
                    for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String) {
                            LdcInsnNode ldc = (LdcInsnNode) insn;
                            String s = (String) ldc.cst;
                            if (s.endsWith(".teamobi.com") || "46.137.254.172".equals(s) || "54.254.156.202".equals(s)) {
                                ldc.cst = newIp;
                                changed = true;
                                patchedCount++;
                            } else if ("Chiến Thần".equals(s) || "Chi\u1ebfn Th\u1ea7n".equals(s)) {
                                ldc.cst = "HSO FREE";
                                changed = true;
                                patchedCount++;
                            }
                        }
                    }
                }
            }

            if (changed) {
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                cn.accept(cw);
                entry.setValue(cw.toByteArray());
            }
        }

        // Write output
        File outputFile = new File(out);
        File tmpFile = new File(out + ".tmp");
        if (tmpFile.exists()) tmpFile.delete();

        JarOutputStream jos = new JarOutputStream(new FileOutputStream(tmpFile));
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            JarEntry je = new JarEntry(entry.getKey());
            jos.putNextEntry(je);
            jos.write(entry.getValue());
            jos.closeEntry();
        }
        jos.close();

        try {
            Files.move(tmpFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            System.err.println("[!] Khong the ghi de " + out + " (dong MicroEmulator truoc khi build): " + ex.getMessage());
            System.exit(1);
        }

        System.out.println("[+] SUCCESS! Generated " + out + " (Total replacements: " + patchedCount + ")");
    }
}
