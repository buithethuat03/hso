import org.objectweb.asm.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Auto Nga 4: thay cho dung co dinh (588,564) canh Pho chi huy bang vi tri tim tu NPC.
 *
 *   gen   N4NpcHome.java.in autotrade-symbols.properties out/HSON4NpcHome.java
 *   patch in.jar HSON4NpcHome.class out.jar
 *
 * patch trong HSOAutoTradeSupport:
 *   - n4StepHome(): doi hai cap "sipush 588; sipush 564" thanh HSON4NpcHome.homeX()/homeY()
 *     (cung 3 byte, khong doi stack map);
 *   - n4StepHome(), n4StepTalk(): them "if (HSON4NpcHome.guard()) return;" o dau ham, nhan
 *     dich la frame F_SAME (dau ham static khong tham so: locals va stack deu rong);
 *   - n4StepLeave(): bi da ra lang thi doi LEAVE_WAIT_MS moi ve cho Pho chi huy (N4_LEAVE_WAIT_MS
 *     1000 da duoc javac inline thanh ldc2_w); n4OnLeaveDungeon(): cau bao noi ro cho doi do.
 * roi them HSON4NpcHome.class. Moi entry khac giu nguyen byte, thu tu va kieu nen.
 */
public final class N4NpcHomePatcher implements Opcodes {
    static final String HOOK_OWNER = "HSOAutoTradeSupport";
    static final String HOOK_METHOD = "n4StepHome";
    static final String TALK_METHOD = "n4StepTalk";
    static final String LEAVE_METHOD = "n4StepLeave";
    static final String LEFT_METHOD = "n4OnLeaveDungeon";
    static final String HELPER = "HSON4NpcHome";
    static final int OLD_X = 588, OLD_Y = 564;
    static final long OLD_LEAVE_WAIT_MS = 1000L, LEAVE_WAIT_MS = 35000L;
    static final String OLD_LEFT_TEXT = ", đợi nạp xong rồi về chỗ Phó chỉ huy.";
    static final String LEFT_TEXT = ", đợi 35 giây rồi về chỗ Phó chỉ huy.";

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && args[0].equals("gen")) gen(args[1], args[2], args[3]);
        else if (args.length == 4 && args[0].equals("patch")) patch(new File(args[1]), new File(args[2]), new File(args[3]));
        else throw new IllegalArgumentException("gen tmpl props out.java | patch in.jar helper.class out.jar");
    }

    static void gen(String tmplPath, String propPath, String outPath) throws IOException {
        Properties sym = new Properties();
        InputStream in = new FileInputStream(propPath);
        try { sym.load(in); } finally { in.close(); }
        String text = new String(read(new FileInputStream(tmplPath)), "UTF-8");
        StringBuffer out = new StringBuffer();
        int at = 0;
        for (int i; (i = text.indexOf('@', at)) >= 0;) {
            int j = text.indexOf('@', i + 1);
            check(j > i, "Unclosed @ at " + i);
            String key = text.substring(i + 1, j);
            String value = sym.getProperty(key);
            check(value != null, "Missing symbol " + key);
            out.append(text, at, i).append(value);
            at = j + 1;
        }
        out.append(text.substring(at));
        File file = new File(outPath);
        file.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(file);
        try { fos.write(out.toString().getBytes("UTF-8")); } finally { fos.close(); }
        System.out.println("GEN=PASS; " + outPath);
    }

    static void patch(File inJar, File helperClass, File outJar) throws IOException {
        check(!outJar.exists(), "Output exists: " + outJar);
        byte[] helper = read(new FileInputStream(helperClass));
        check(HELPER.equals(new ClassReader(helper).getClassName()), "Helper class name");
        String hookEntry = HOOK_OWNER + ".class", helperEntry = HELPER + ".class";

        ZipFile zip = new ZipFile(inJar);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(buffer);
        int[] replaced = new int[4];
        long hookTime = 0L;
        try {
            for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements();) {
                ZipEntry src = en.nextElement();
                check(!src.getName().equals(helperEntry), "Input already has " + helperEntry);
                byte[] data = read(zip.getInputStream(src));
                if (src.getName().equals(hookEntry)) {
                    data = hook(data, replaced);
                    hookTime = src.getTime();
                }
                put(zos, src.getName(), data, src.getTime(), src.getMethod());
            }
            check(hookTime != 0L, "Missing " + hookEntry);
            put(zos, helperEntry, helper, hookTime, ZipEntry.DEFLATED);
            zos.close();
        } finally {
            zip.close();
        }
        check(replaced[0] == 2, "Expected 2 (588,564) pairs in " + HOOK_METHOD + ", found " + replaced[0]);
        check(replaced[1] == 2, "Expected 2 guards, inserted " + replaced[1]);
        check(replaced[2] == 1, "Expected 1 leave wait in " + LEAVE_METHOD + ", found " + replaced[2]);
        check(replaced[3] == 1, "Expected 1 leave notice in " + LEFT_METHOD + ", found " + replaced[3]);
        FileOutputStream fos = new FileOutputStream(outJar);
        try { fos.write(buffer.toByteArray()); } finally { fos.close(); }
        System.out.println("N4_NPC_HOME=PASS; pairs=" + replaced[0] + "; guards=" + replaced[1]
                + "; leaveWaitMs=" + LEAVE_WAIT_MS + "; helper=" + helperEntry);
    }

    /** Them guard o dau n4StepHome/n4StepTalk; doi cap sipush OLD_X, OLD_Y trong n4StepHome. */
    static byte[] hook(byte[] data, final int[] replaced) {
        ClassReader reader = new ClassReader(data);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                if (!desc.equals("()V") || (access & ACC_STATIC) == 0) return mv;
                if (name.equals(LEAVE_METHOD)) {
                    return new MethodVisitor(ASM9, mv) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            if (Long.valueOf(OLD_LEAVE_WAIT_MS).equals(value)) {
                                value = Long.valueOf(LEAVE_WAIT_MS);
                                replaced[2]++;
                            }
                            super.visitLdcInsn(value);
                        }
                    };
                }
                if (name.equals(LEFT_METHOD)) {
                    return new MethodVisitor(ASM9, mv) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            if (OLD_LEFT_TEXT.equals(value)) {
                                value = LEFT_TEXT;
                                replaced[3]++;
                            }
                            super.visitLdcInsn(value);
                        }
                    };
                }
                final boolean home = name.equals(HOOK_METHOD);
                if (!home && !name.equals(TALK_METHOD)) return mv;
                return new MethodVisitor(ASM9, mv) {
                    boolean pendingX;

                    @Override
                    public void visitCode() {
                        super.visitCode();
                        Label go = new Label();
                        super.visitMethodInsn(INVOKESTATIC, HELPER, "guard", "()Z", false);
                        super.visitJumpInsn(IFEQ, go);
                        super.visitInsn(RETURN);
                        super.visitLabel(go);
                        super.visitFrame(F_SAME, 0, null, 0, null);
                        replaced[1]++;
                    }

                    void flush() {
                        if (pendingX) super.visitIntInsn(SIPUSH, OLD_X);
                        pendingX = false;
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        if (home && opcode == SIPUSH && operand == OLD_X) {
                            flush();
                            pendingX = true;
                            return;
                        }
                        if (opcode == SIPUSH && operand == OLD_Y && pendingX) {
                            pendingX = false;
                            super.visitMethodInsn(INVOKESTATIC, HELPER, "homeX", "()I", false);
                            super.visitMethodInsn(INVOKESTATIC, HELPER, "homeY", "()I", false);
                            replaced[0]++;
                            return;
                        }
                        flush();
                        super.visitIntInsn(opcode, operand);
                    }

                    @Override public void visitInsn(int opcode) { flush(); super.visitInsn(opcode); }
                    @Override public void visitVarInsn(int opcode, int var) { flush(); super.visitVarInsn(opcode, var); }
                    @Override public void visitTypeInsn(int opcode, String type) { flush(); super.visitTypeInsn(opcode, type); }
                    @Override public void visitFieldInsn(int o, String w, String n, String d) { flush(); super.visitFieldInsn(o, w, n, d); }
                    @Override public void visitMethodInsn(int o, String w, String n, String d, boolean i) { flush(); super.visitMethodInsn(o, w, n, d, i); }
                    @Override public void visitJumpInsn(int opcode, Label label) { flush(); super.visitJumpInsn(opcode, label); }
                    @Override public void visitLabel(Label label) { flush(); super.visitLabel(label); }
                    @Override public void visitLdcInsn(Object value) { flush(); super.visitLdcInsn(value); }
                    @Override public void visitIincInsn(int var, int inc) { flush(); super.visitIincInsn(var, inc); }
                    @Override public void visitFrame(int t, int nl, Object[] l, int ns, Object[] s) { flush(); super.visitFrame(t, nl, l, ns, s); }
                    @Override public void visitMaxs(int stack, int locals) { flush(); super.visitMaxs(stack, locals); }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    static void put(ZipOutputStream zos, String name, byte[] data, long time, int method) throws IOException {
        ZipEntry dst = new ZipEntry(name);
        dst.setTime(time);
        dst.setMethod(method);
        if (method == ZipEntry.STORED) {
            CRC32 crc = new CRC32();
            crc.update(data);
            dst.setSize(data.length);
            dst.setCompressedSize(data.length);
            dst.setCrc(crc.getValue());
        }
        zos.putNextEntry(dst);
        zos.write(data);
        zos.closeEntry();
    }

    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int r; (r = in.read(buf)) > 0;) bos.write(buf, 0, r);
        in.close();
        return bos.toByteArray();
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("Check failed: " + what);
    }
}
