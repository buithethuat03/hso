package luna.tools;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Binary-boundary fixture; executes gates from the supplied JAR without a game server. */
public final class LunaCombatCadenceHostTest implements Opcodes {
    public static long now, timestamp;
    public static int interval;
    private static int checks;

    public static void main(String[] args) throws Exception {
        File before = new File(args[0]), after = new File(args[1]);
        Properties symbols = new Properties(); InputStream input = new FileInputStream(args[2]);
        try { symbols.load(input); } finally { input.close(); }
        int expectedExtra = args.length > 3 ? Integer.parseInt(args[3]) : 100;
        expect(Integer.parseInt(symbols.getProperty("extra.ms")) == expectedExtra, "catalog matches requested cumulative delay");
        testAuto(before, symbols, 100); testAuto(after, symbols, 100 + expectedExtra);
        testRequest(before, symbols, 0); testRequest(after, symbols, expectedExtra);
        JarInputStream crc = new JarInputStream(new FileInputStream(after));
        try { while (crc.getNextJarEntry() != null) CombatCadencePatcher.readAll(new FilterInputStream(crc) { public void close() { } }); }
        finally { crc.close(); }
        System.out.println("ZIP CRC=OK");
        System.out.println("LunaCombatCadenceHostTest PASS (" + checks + " checks)");
    }

    private static void testAuto(File jar, Properties symbols, int expectedDelay) throws Exception {
        String name = symbols.getProperty("cast.owner");
        ClassNode node = read(jar, name);
        MethodNode gate = NativeCombatDelayPatcher.method(node, symbols.getProperty("auto.gate.method"), "()Z");
        int clockReads = 0;
        for (AbstractInsnNode insn : gate.instructions.toArray()) {
            if (insn instanceof MethodInsnNode && insn.getOpcode() == INVOKESTATIC
                    && "java/lang/System".equals(((MethodInsnNode) insn).owner)
                    && "currentTimeMillis".equals(((MethodInsnNode) insn).name)) {
                gate.instructions.set(insn, new FieldInsnNode(GETSTATIC, "luna/tools/LunaCombatCadenceHostTest", "now", "J"));
                clockReads++;
            }
        }
        expect(clockReads == 1, "one native auto clock read");
        ClassWriter writer = new ClassWriter(0); node.accept(writer);
        final byte[] deterministicCast = writer.toByteArray(); final String className = name.replace('/', '.');
        URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, LunaCombatCadenceHostTest.class.getClassLoader()) {
            protected Class<?> findClass(String requested) throws ClassNotFoundException {
                if (requested.equals(className)) return defineClass(requested, deterministicCast, 0, deterministicCast.length);
                return super.findClass(requested);
            }
        };
        try {
            Class<?> type = Class.forName(className, true, loader);
            Field delay = type.getDeclaredField(symbols.getProperty("auto.delay.field")); delay.setAccessible(true);
            expect(delay.getInt(null) == expectedDelay, "actual initialized auto interval");
            Field last = type.getDeclaredField(symbols.getProperty("auto.timestamp.field")); last.setAccessible(true);
            last.setLong(null, 100000L);
            Method ready = type.getDeclaredMethod(symbols.getProperty("auto.gate.method")); ready.setAccessible(true);
            for (long elapsed : new long[]{0, 99, 100, 101, expectedDelay - 1, expectedDelay, expectedDelay + 1, 1000}) {
                now = 100000L + elapsed;
                expect(((Boolean) ready.invoke(null)).booleanValue() == (elapsed > expectedDelay), "auto boundary " + elapsed);
            }
        } finally { loader.close(); }
    }

    private static void testRequest(File jar, Properties symbols, int expectedExtra) throws Exception {
        ClassNode node = read(jar, symbols.getProperty("request.owner"));
        MethodNode source = NativeCombatDelayPatcher.method(node, symbols.getProperty("request.method"), symbols.getProperty("request.descriptor"));
        List<AbstractInsnNode> real = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : source.instructions.toArray()) if (insn.getOpcode() >= 0) real.add(insn);
        int index = -1;
        for (int i = 0; i < real.size(); i++) {
            if (real.get(i) instanceof FieldInsnNode && real.get(i).getOpcode() == GETSTATIC
                    && symbols.getProperty("cast.owner").equals(((FieldInsnNode) real.get(i)).owner)
                    && symbols.getProperty("request.delay.field").equals(((FieldInsnNode) real.get(i)).name)) {
                expect(index < 0, "unique native request comparison"); index = i;
            }
        }
        expect(index >= 3, "native elapsed-time input");
        String probeName = "LunaNativeRequestGateProbe";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_4, ACC_PUBLIC, probeName, null, "java/lang/Object", null);
        MethodVisitor code = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "ready", "()Z", null, null); code.visitCode();
        int fields = 0, cursor = index - 3;
        while (real.get(cursor).getOpcode() != IFLE) {
            AbstractInsnNode insn = real.get(cursor++);
            if (insn instanceof FieldInsnNode) {
                String value = fields == 0 ? "now" : fields == 1 ? "timestamp" : "interval";
                String descriptor = fields < 2 ? "J" : "I";
                code.visitFieldInsn(GETSTATIC, "luna/tools/LunaCombatCadenceHostTest", value, descriptor); fields++;
            } else insn.accept(code);
        }
        expect(fields == 3, "native gate reads current time, prior time and interval");
        Label no = new Label(); code.visitJumpInsn(IFLE, no); code.visitInsn(ICONST_1); code.visitInsn(IRETURN);
        code.visitLabel(no); code.visitInsn(ICONST_0); code.visitInsn(IRETURN); code.visitMaxs(0, 0); code.visitEnd(); writer.visitEnd();
        final byte[] bytes = writer.toByteArray();
        Class<?> probe = new ClassLoader(LunaCombatCadenceHostTest.class.getClassLoader()) {
            Class<?> loadProbe() { return defineClass("LunaNativeRequestGateProbe", bytes, 0, bytes.length); }
        }.loadProbe();
        Method ready = probe.getMethod("ready");
        timestamp = 100000L;
        for (int nativeInterval : new int[]{0, 100, 600, 32767}) {
            interval = nativeInterval;
            long threshold = nativeInterval + expectedExtra;
            for (long elapsed : new long[]{0, threshold - 1, threshold, threshold + 1, threshold + 1000}) {
                now = timestamp + elapsed;
                expect(((Boolean) ready.invoke(null)).booleanValue() == (elapsed > threshold), "native request boundary");
            }
        }
    }

    private static ClassNode read(File file, String name) throws Exception {
        JarFile jar = new JarFile(file);
        try { return CombatCadencePatcher.readClass(CombatCadencePatcher.readAll(jar.getInputStream(jar.getJarEntry(name + ".class")))); }
        finally { jar.close(); }
    }
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
