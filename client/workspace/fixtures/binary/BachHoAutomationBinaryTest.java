package luna.tools;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Checks hooks and exercises the candidate's actual RMS encoder/decoder. */
public final class BachHoAutomationBinaryTest implements Opcodes {
    private static int checks;
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("before.jar candidate.jar symbols.properties");
        Properties symbols = new Properties(); InputStream in = new FileInputStream(args[2]);
        try { symbols.load(in); } finally { in.close(); }
        JarFile before = new JarFile(args[0]), after = new JarFile(args[1]);
        try {
            ClassNode facade = read(after, "HSOAutoTradeSupport");
            expect(calls(method(facade, "handleMenuAction", "(II)Z"), "luna/core/BachHoClientHooks", "handleMenuAction", "(II)Z") == 1, "menu hook");
            expect(calls(method(facade, "tick", "()V"), "luna/core/BachHoClientHooks", "tick", "()V") == 1, "frame hook");
            expect(calls(method(facade, "autoAttackPath", "([S)[S"), "luna/core/BachHoClientHooks", "filterPath", "([S)[S") == 1, "native path gate");
            expect(calls(method(facade, "autoTargetGate", "(Ljava/lang/Object;Ljava/lang/Object;)Z"), "LunaGeneratedLegacyHooks", "autoTargetGate", "(Ljava/lang/Object;Ljava/lang/Object;)Z") == 1, "original monster/PK/N4 validator retained");
            expect(calls(method(read(after, "LunaGeneratedLegacyHooks"), "onFreshLoginWorldEntry", "()V"), "luna/core/BachHoClientHooks", "onLoginEntry", "()V") == 1, "fresh login hook");
            expect(calls(method(read(after, "LunaGeneratedLegacyHooks"), "attackRecoveryRouteOwnsCombat", "()Z"),
                    "luna/core/BachHoClientHooks", "ownsCombat", "()Z") == 1, "saved combat owns native recovery");
            expect(calls(method(read(after, "HSOSKYPetFeedSupport"), "reengageAutoAttack", "()V"),
                    "luna/core/BachHoClientHooks", "shouldPauseNativeCombat", "()Z") == 1, "native rearm yields while returning");
            verifyNativeRearmBody(before, after);
            ClassNode engine = read(after, symbols.getProperty("class.ENGINE"));
            expect(calls(method(engine, symbols.getProperty("engine.method.onNpcMenuOpened"), "(SBL" + symbols.getProperty("class.VEC") + ";)Z"),
                    "BachHoGeneratedAutomationBridge", "observeMaterialMenu", "(SBLjava/lang/Object;)V") == 1, "server NPC/menu IDs are observed");
            MethodNode menu = method(engine, symbols.getProperty("engine.method.showMainMenu"), "()V");
            expect(strings(menu, "Auto tự đánh >") == 1, "one new combat menu row");
            expect(strings(menu, "Auto Đi Buôn") == 1, "one trade row preserved");
            Enumeration<JarEntry> all = before.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                if (!entry.getName().endsWith(".class") || entry.getName().contains("Ghost")) {
                    expect(Arrays.equals(bytes(before, entry.getName()), bytes(after, entry.getName())), "preserved resource/support " + entry.getName());
                }
            }
            ClassNode bridge = read(after, "BachHoGeneratedAutomationBridge");
            MethodNode hold = method(bridge, "holdPosition", "(II)V");
            for (String key : new String[] {"player.x", "player.y", "player.stepDestX", "player.stepDestY", "combat.deltaX.field", "combat.deltaY.field"}) {
                int writes = 0;
                for (AbstractInsnNode insn : hold.instructions.toArray()) if (insn instanceof FieldInsnNode
                        && insn.getOpcode() == PUTFIELD && ((FieldInsnNode) insn).name.equals(symbols.getProperty(key))) writes++;
                expect(writes == 1, "anchoring writes exact native field " + key);
            }
        } finally { before.close(); after.close(); }
        testRms(new File(args[1]));
        System.out.println("BachHoAutomationBinaryTest PASS (" + checks + " checks)");
    }

    private static void verifyNativeRearmBody(JarFile before, JarFile after) throws Exception {
        ClassNode old = new ClassNode(), next = new ClassNode();
        new ClassReader(bytes(before, "HSOSKYPetFeedSupport.class")).accept(old, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        new ClassReader(bytes(after, "HSOSKYPetFeedSupport.class")).accept(next, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        MethodNode original = method(old, "reengageAutoAttack", "()V"), guarded = method(next, "reengageAutoAttack", "()V");
        List<AbstractInsnNode> code = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode i : guarded.instructions.toArray()) if (i.getOpcode() >= 0) code.add(i);
        expect(code.get(0) instanceof MethodInsnNode && code.get(1).getOpcode() == IFEQ
                && code.get(2).getOpcode() == RETURN, "native rearm has only the three-instruction guard prefix");
        for (int i = 0; i < 3; i++) guarded.instructions.remove(code.get(i));
        expect(Arrays.equals(bodyBytes(original), bodyBytes(guarded)), "all native rearm/horse/skill instructions preserved after the guard");
    }

    private static byte[] bodyBytes(MethodNode method) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_7, ACC_PUBLIC, "RearmCompare", null, "java/lang/Object", null);
        method.accept(writer); writer.visitEnd(); return writer.toByteArray();
    }

    private static void testRms(File jar) throws Exception {
        final byte[] fakeStore = recordStore();
        URLClassLoader loader = new URLClassLoader(new URL[] {jar.toURI().toURL()}, BachHoAutomationBinaryTest.class.getClassLoader()) {
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if ("javax.microedition.rms.RecordStore".equals(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = defineClass(name, fakeStore, 0, fakeStore.length);
                    if (resolve) resolveClass(type); return type;
                }
                return super.loadClass(name, resolve);
            }
        };
        try {
            Class<?> bridge = Class.forName("BachHoGeneratedAutomationBridge", true, loader);
            Object adapter = bridge.newInstance();
            Method save = bridge.getMethod("savePosition", int[].class), load = bridge.getMethod("loadPosition", int[].class);
            int[] result = {-1, 0, 0, 0}; load.invoke(adapter, (Object) result);
            expect(result[0] == -1, "empty RMS means no saved position");
            int[] original = {52, 300, 420, 3}; save.invoke(adapter, (Object) original);
            load.invoke(bridge.newInstance(), (Object) result);
            expect(Arrays.equals(original, result), "actual candidate RMS round trip across adapter instances");
            Class<?> store = Class.forName("javax.microedition.rms.RecordStore", true, loader);
            byte[] record = (byte[]) store.getField("record").get(null);
            DataInputStream data = new DataInputStream(new ByteArrayInputStream(record));
            expect(record.length == 20 && data.readInt() == 1, "versioned four-int coordinate format");
            save.invoke(adapter, (Object) new int[] {-1, 300, 420, 3});
            result[0] = 52; load.invoke(adapter, (Object) result);
            expect(result[0] == -1, "clear position persists across reload");
            store.getField("record").set(null, new byte[] {1, 2, 3});
            result[0] = -1; load.invoke(adapter, (Object) result);
            expect(result[0] == -1, "truncated RMS fails safely");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bytes);
            for (int n : new int[] {1, 52, 300, 420, 128}) output.writeInt(n);
            store.getField("record").set(null, bytes.toByteArray());
            load.invoke(adapter, (Object) result); expect(result[0] == -1, "invalid zone rejected atomically");
        } finally { loader.close(); }
    }

    /** Memory RMS boundary stub, so the production codec runs without a game account. */
    private static byte[] recordStore() {
        String owner = "javax/microedition/rms/RecordStore"; ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(V1_5, ACC_PUBLIC, owner, null, "java/lang/Object", null);
        w.visitField(ACC_PUBLIC | ACC_STATIC, "record", "[B", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null); m.visitCode();
        m.visitVarInsn(ALOAD, 0); m.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); finish(m, RETURN);
        m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "openRecordStore", "(Ljava/lang/String;Z)L" + owner + ";", null, null); m.visitCode();
        m.visitTypeInsn(NEW, owner); m.visitInsn(DUP); m.visitMethodInsn(INVOKESPECIAL, owner, "<init>", "()V", false); finish(m, ARETURN);
        m = w.visitMethod(ACC_PUBLIC, "getNumRecords", "()I", null, null); m.visitCode();
        Label empty = new Label(); m.visitFieldInsn(GETSTATIC, owner, "record", "[B"); m.visitJumpInsn(IFNULL, empty);
        m.visitInsn(ICONST_1); m.visitInsn(IRETURN); m.visitLabel(empty); m.visitInsn(ICONST_0); finish(m, IRETURN);
        m = w.visitMethod(ACC_PUBLIC, "getRecord", "(I)[B", null, null); m.visitCode(); m.visitFieldInsn(GETSTATIC, owner, "record", "[B"); finish(m, ARETURN);
        m = w.visitMethod(ACC_PUBLIC, "addRecord", "([BII)I", null, null); m.visitCode();
        m.visitVarInsn(ALOAD, 1); m.visitFieldInsn(PUTSTATIC, owner, "record", "[B"); m.visitInsn(ICONST_1); finish(m, IRETURN);
        m = w.visitMethod(ACC_PUBLIC, "setRecord", "(I[BII)V", null, null); m.visitCode(); m.visitVarInsn(ALOAD, 2); m.visitFieldInsn(PUTSTATIC, owner, "record", "[B"); finish(m, RETURN);
        m = w.visitMethod(ACC_PUBLIC, "closeRecordStore", "()V", null, null); m.visitCode(); finish(m, RETURN);
        w.visitEnd(); return w.toByteArray();
    }
    private static void finish(MethodVisitor m, int opcode) { m.visitInsn(opcode); m.visitMaxs(0, 0); m.visitEnd(); }
    private static int calls(MethodNode m, String owner, String name, String desc) {
        int n = 0; for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof MethodInsnNode) {
            MethodInsnNode c = (MethodInsnNode) i; if (owner.equals(c.owner) && name.equals(c.name) && desc.equals(c.desc)) n++;
        } return n;
    }
    private static int strings(MethodNode m, String text) { int n = 0; for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof LdcInsnNode && text.equals(((LdcInsnNode) i).cst)) n++; return n; }
    private static MethodNode method(ClassNode n, String name, String desc) { for (MethodNode m : n.methods) if (name.equals(m.name) && desc.equals(m.desc)) return m; throw new IllegalStateException("missing " + name + desc); }
    private static ClassNode read(JarFile jar, String owner) throws Exception { ClassNode n = new ClassNode(); new ClassReader(bytes(jar, owner + ".class")).accept(n, 0); return n; }
    private static byte[] bytes(JarFile jar, String name) throws Exception { JarEntry entry = jar.getJarEntry(name); if (entry == null) return null;
        InputStream in = jar.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream(); try { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) >= 0) out.write(b, 0, n); } finally { in.close(); } return out.toByteArray(); }
    private static void expect(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
