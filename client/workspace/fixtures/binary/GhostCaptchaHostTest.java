package luna.tools;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Runs the published OCR/controller with deterministic time and simulated native bridges. */
public final class GhostCaptchaHostTest implements Opcodes {
    private static final String SELF = "luna/tools/GhostCaptchaHostTest";
    private static final String IMAGE = "javax/microedition/lcdui/Image";
    private static final String SUPPORT = "HungBySonGhostSupport";
    private static long now;
    private static boolean pending;
    private static boolean observedClear;
    private static int scenario, checks;
    private static int[] pixels;
    private static Method onCaptcha;
    private static Object image;
    private static final List<Integer> answers = new ArrayList<Integer>();
    private static final List<Long> times = new ArrayList<Long>();

    public static void main(String[] args) throws Exception {
        JarFile jar = new JarFile(args[0]);
        byte[] original;
        try { original = read(jar.getInputStream(jar.getJarEntry(SUPPORT + ".class"))); }
        finally { jar.close(); }
        expect(new ClassReader(original).readUnsignedShort(6) == V1_4, "CLDC support class version");
        ClassNode node = new ClassNode(ASM9);
        new ClassReader(original).accept(node, ClassReader.SKIP_FRAMES);
        for (MethodNode m : node.methods) {
            if (m.name.equals("sendAnswer") || m.name.equals("captchaPending")) {
                m.instructions.clear(); m.tryCatchBlocks.clear();
                if (m.localVariables != null) m.localVariables.clear();
                if (m.name.equals("sendAnswer")) m.instructions.add(new VarInsnNode(ILOAD, 0));
                m.instructions.add(new MethodInsnNode(INVOKESTATIC, SELF,
                        m.name.equals("sendAnswer") ? "answer" : "pending", m.desc, false));
                m.instructions.add(new InsnNode(m.name.equals("sendAnswer") ? RETURN : IRETURN));
            } else for (AbstractInsnNode i : m.instructions.toArray()) {
                if (!(i instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode)i;
                if (call.owner.equals("java/lang/System") && call.name.equals("currentTimeMillis")) call.owner = SELF;
                if (call.owner.equals("java/lang/Thread") && call.name.equals("sleep")) call.owner = SELF;
                if (call.owner.equals("java/lang/Thread") && call.name.equals("start"))
                    m.instructions.set(call, new InsnNode(POP)); // run worker synchronously below
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
        final byte[] controlled = writer.toByteArray();
        ClassLoader loader = new ClassLoader(GhostCaptchaHostTest.class.getClassLoader()) {
            protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(SUPPORT) && !name.equals(IMAGE.replace('/', '.'))) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name);
                if (resolve) resolveClass(type);
                return type;
            }
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = name.equals(SUPPORT) ? controlled : name.equals(IMAGE.replace('/', '.')) ? imageClass() : null;
                if (bytes == null) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        Class<?> imageType = loader.loadClass(IMAGE.replace('/', '.'));
        image = imageType.getConstructor().newInstance();
        Class<?> support = loader.loadClass(SUPPORT);
        onCaptcha = support.getMethod("onCaptcha", imageType);
        Method ocr = support.getDeclaredMethod("ocr", imageType); ocr.setAccessible(true);
        for (String text : new String[]{"13579", "97531", "55197"}) {
            for (boolean transparent : new boolean[]{false, true}) {
                draw(text, transparent);
                int[] result = (int[])ocr.invoke(null, image);
                for (int i = 0; i < 5; i++) expect(result[i] == text.charAt(i) - '0', "OCR " + text + " alpha=" + transparent);
            }
        }
        expect(ocr.invoke(null, new Object[]{null}) == null, "null image ignored");
        draw("13579", false);
        Constructor<?> ctor = support.getDeclaredConstructor(); ctor.setAccessible(true);
        for (scenario = 1; scenario <= 3; scenario++) {
            now = 0; pending = true; observedClear = false; answers.clear(); times.clear();
            onCaptcha.invoke(null, image);
            ((Runnable)ctor.newInstance()).run();
            int expected = scenario == 3 ? 50 : 10;
            expect(answers.size() == expected, "attempt count scenario=" + scenario);
            expect(times.get(0).longValue() == 1500, "initial settle delay");
            for (int i = 0; i < expected; i++) {
                expect(answers.get(i).intValue() == i % 5, "digit answer index " + i);
                if (i % 5 != 0) expect(times.get(i) - times.get(i - 1) == 1500, "key spacing");
            }
            if (scenario == 1 || scenario == 3)
                expect(times.get(5) - times.get(4) == 21500, "retry after completed sequence plus 20 seconds");
            if (scenario == 2) expect(times.get(5) - times.get(4) < 20000, "same digits accepted after observed clear");
            Field running = support.getDeclaredField("running"); running.setAccessible(true);
            expect(!running.getBoolean(null), "worker exits");
        }
        System.out.println("GhostCaptchaHostTest PASS (" + checks + " checks); synthetic OCR; deterministic controller time/native bridges");
    }

    public static long currentTimeMillis() { return now; }
    public static boolean pending() { if (!pending) observedClear = true; return pending; }
    public static void answer(int index) {
        answers.add(index); times.add(now);
        if (scenario == 2 && answers.size() == 5 || scenario != 3 && answers.size() == 10) pending = false;
    }
    public static void sleep(long ms) throws Exception {
        now += ms;
        if (now > 400000) throw new AssertionError("worker did not exit");
        if (answers.size() == 2 && ms == 1500) onCaptcha.invoke(null, image); // repeated network delivery
        if (scenario == 2 && observedClear && answers.size() == 5 && ms == 250) {
            pending = true; onCaptcha.invoke(null, image);
        }
    }
    public static void rgb(int[] dest, int offset, int stride, int x, int y, int w, int h) {
        for (int row = 0; row < h; row++) System.arraycopy(pixels, (y + row) * 50 + x, dest, offset + row * stride, w);
    }
    private static void draw(String text, boolean transparent) {
        pixels = new int[50 * 6]; Arrays.fill(pixels, transparent ? 0 : 0xff000000);
        int[][] widths = {{2,2},{4,4},{5,4},{6,2},{4,3}};
        for (int i = 0; i < 5; i++) {
            int[] pair = widths[(text.charAt(i) - '1') / 2];
            for (int x = 0; x < pair[0]; x++) pixels[50 + i * 10 + 1 + x] = -1;
            for (int x = 0; x < pair[1]; x++) pixels[4 * 50 + i * 10 + 1 + x] = -1;
        }
    }
    private static byte[] imageClass() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(V1_4, ACC_PUBLIC, IMAGE, null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        m.visitCode(); m.visitVarInsn(ALOAD, 0); m.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        m.visitInsn(RETURN); m.visitMaxs(0,0); m.visitEnd();
        for (String name : new String[]{"getWidth", "getHeight"}) {
            m = w.visitMethod(ACC_PUBLIC, name, "()I", null, null); m.visitCode();
            m.visitIntInsn(BIPUSH, name.equals("getWidth") ? 50 : 6); m.visitInsn(IRETURN); m.visitMaxs(0,0); m.visitEnd();
        }
        m = w.visitMethod(ACC_PUBLIC, "getRGB", "([IIIIIII)V", null, null); m.visitCode();
        m.visitVarInsn(ALOAD,1); for (int i=2;i<=7;i++) m.visitVarInsn(ILOAD,i);
        m.visitMethodInsn(INVOKESTATIC,SELF,"rgb","([IIIIIII)V",false); m.visitInsn(RETURN); m.visitMaxs(0,0); m.visitEnd();
        w.visitEnd(); return w.toByteArray();
    }
    private static byte[] read(InputStream in) throws IOException {
        try { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192];
            for (int n; (n=in.read(buf))>=0;) out.write(buf,0,n); return out.toByteArray(); }
        finally { in.close(); }
    }
    private static void expect(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
}
