package luna.tools;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Adds delay at native cast gates, preserving animation and per-skill cooldowns. */
public final class NativeCombatDelayPatcher implements Opcodes {
    public static void main(String[] args) throws Exception {
        check(args.length == 4, "Usage: <input.jar> <new-output.jar> <extra-ms> <new-symbol-catalog>");
        File input = new File(args[0]), output = new File(args[1]), catalogFile = new File(args[3]);
        int extra = Integer.parseInt(args[2]);
        check(extra > 0 && extra <= 10000, "Invalid added delay");
        check(!output.exists() && !catalogFile.exists(), "Output/catalog already exists");
        JarFile jar = new JarFile(input);
        LinkedHashMap<String, byte[]> before = new LinkedHashMap<String, byte[]>();
        Map<String, Long> times = new HashMap<String, Long>();
        ClassNode cast;
        try {
            cast = CombatCadencePatcher.discover(jar);
            Enumeration<JarEntry> all = jar.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                check(!before.containsKey(entry.getName()), "Duplicate JAR entry");
                before.put(entry.getName(), CombatCadencePatcher.readAll(jar.getInputStream(entry)));
                times.put(entry.getName(), entry.getTime());
            }
        } finally { jar.close(); }
        MethodNode gate = null;
        FieldInsnNode autoDelay = null;
        for (MethodNode method : cast.methods) {
            if (!"()Z".equals(method.desc)) continue;
            List<AbstractInsnNode> code = real(method);
            if (code.size() == 11 && code.get(0) instanceof MethodInsnNode
                    && "java/lang/System".equals(((MethodInsnNode) code.get(0)).owner)
                    && "currentTimeMillis".equals(((MethodInsnNode) code.get(0)).name)
                    && field(code.get(1), GETSTATIC, cast.name, "J")
                    && code.get(2).getOpcode() == LSUB && field(code.get(3), GETSTATIC, cast.name, "I")
                    && code.get(4).getOpcode() == I2L && code.get(5).getOpcode() == LCMP
                    && code.get(6).getOpcode() == IFLE && code.get(7).getOpcode() == ICONST_1
                    && code.get(8).getOpcode() == IRETURN && code.get(9).getOpcode() == ICONST_0
                    && code.get(10).getOpcode() == IRETURN) {
                check(gate == null, "Ambiguous auto gate"); gate = method; autoDelay = (FieldInsnNode) code.get(3);
            }
        }
        check(gate != null, "Missing exact auto gate");
        MethodNode clinit = method(cast, "<clinit>", "()V");
        IntInsnNode autoConstant = null;
        int autoWrites = 0, shortWrites = 0;
        ClassNode requester = null;
        MethodNode request = null;
        FieldInsnNode threshold = null;
        Properties catalog = new Properties();
        for (Map.Entry<String, byte[]> item : before.entrySet()) {
            if (!item.getKey().endsWith(".class")) continue;
            ClassNode node = CombatCadencePatcher.readClass(item.getValue());
            for (MethodNode method : node.methods) {
                List<AbstractInsnNode> code = real(method);
                for (int i = 0; i < code.size(); i++) {
                    AbstractInsnNode insn = code.get(i);
                    if (field(insn, PUTSTATIC, cast.name, "I") && ((FieldInsnNode) insn).name.equals(autoDelay.name)) autoWrites++;
                    if (field(insn, PUTSTATIC, cast.name, "S")) shortWrites++;
                    if (!field(insn, GETSTATIC, cast.name, "S")) continue;
                    check(i >= 3 && i + 6 < code.size()
                            && field(code.get(i - 3), GETSTATIC, null, "J")
                            && field(code.get(i - 2), GETSTATIC, cast.name, "J")
                            && code.get(i - 1).getOpcode() == LSUB && code.get(i + 1).getOpcode() == I2L
                            && code.get(i + 2).getOpcode() == LCMP && code.get(i + 3).getOpcode() == IFLE
                            && field(code.get(i + 5), GETFIELD, null, "S")
                            && field(code.get(i + 6), PUTSTATIC, cast.name, "S")
                            && ((FieldInsnNode) insn).name.equals(((FieldInsnNode) code.get(i + 6)).name),
                            "Unexpected request interval read");
                    check(request == null, "Ambiguous request gate");
                    requester = node; request = method; threshold = (FieldInsnNode) insn;
                    catalog.setProperty("request.now.owner", ((FieldInsnNode) code.get(i - 3)).owner);
                    catalog.setProperty("request.now.field", ((FieldInsnNode) code.get(i - 3)).name);
                    catalog.setProperty("request.timestamp.field", ((FieldInsnNode) code.get(i - 2)).name);
                }
            }
        }
        check(request != null && autoWrites == 1 && shortWrites == 2 && !requester.name.equals(cast.name),
                "Unexpected native timing symbols/writers");
        for (AbstractInsnNode insn : clinit.instructions.toArray()) {
            if (field(insn, PUTSTATIC, cast.name, "I") && ((FieldInsnNode) insn).name.equals(autoDelay.name)) {
                AbstractInsnNode previous = previous(insn);
                check(previous instanceof IntInsnNode && previous.getOpcode() == SIPUSH && autoConstant == null,
                        "Unexpected auto delay initializer");
                autoConstant = (IntInsnNode) previous;
            }
        }
        check(autoConstant != null, "Missing initializer");
        int oldAuto = autoConstant.operand;
        check(oldAuto + extra <= Short.MAX_VALUE, "Auto delay overflow");
        catalog.setProperty("input.sha256", sha256(input));
        catalog.setProperty("cast.owner", cast.name);
        catalog.setProperty("auto.delay.field", autoDelay.name);
        catalog.setProperty("auto.delay.descriptor", "I");
        catalog.setProperty("auto.delay.before.ms", String.valueOf(oldAuto));
        catalog.setProperty("auto.delay.after.ms", String.valueOf(oldAuto + extra));
        catalog.setProperty("auto.gate.method", gate.name);
        catalog.setProperty("auto.gate.descriptor", gate.desc);
        catalog.setProperty("auto.timestamp.field", ((FieldInsnNode) real(gate).get(1)).name);
        catalog.setProperty("request.owner", requester.name);
        catalog.setProperty("request.method", request.name);
        catalog.setProperty("request.descriptor", request.desc);
        catalog.setProperty("request.delay.field", threshold.name);
        catalog.setProperty("request.delay.descriptor", "S");
        catalog.setProperty("extra.ms", String.valueOf(extra));
        LinkedHashMap<String, byte[]> after = new LinkedHashMap<String, byte[]>(before);
        String castEntry = cast.name + ".class", requestEntry = requester.name + ".class";
        autoConstant.operand = oldAuto + extra;
        after.put(castEntry, rewrite(before.get(castEntry), clinit));
        InsnList addition = new InsnList(); addition.add(new IntInsnNode(SIPUSH, extra)); addition.add(new InsnNode(IADD));
        request.instructions.insert(threshold, addition);
        after.put(requestEntry, rewrite(before.get(requestEntry), request));
        // Invert the two permitted edits and compare complete class structures.
        ClassNode normalizedCast = CombatCadencePatcher.readClass(after.get(castEntry));
        MethodNode normalizedClinit = method(normalizedCast, "<clinit>", "()V");
        int restored = 0;
        for (AbstractInsnNode insn : normalizedClinit.instructions.toArray()) {
            if (field(insn, PUTSTATIC, cast.name, "I") && ((FieldInsnNode) insn).name.equals(autoDelay.name)) {
                IntInsnNode value = (IntInsnNode) previous(insn);
                check(value.operand == oldAuto + extra, "Incorrect saved auto delay"); value.operand = oldAuto; restored++;
            }
        }
        check(restored == 1 && Arrays.equals(canonical(CombatCadencePatcher.readClass(before.get(castEntry))), canonical(normalizedCast)),
                "Unrelated cast class change");
        ClassNode normalizedRequester = CombatCadencePatcher.readClass(after.get(requestEntry));
        MethodNode normalizedRequest = method(normalizedRequester, request.name, request.desc);
        int removed = 0;
        for (AbstractInsnNode insn : normalizedRequest.instructions.toArray()) {
            if (field(insn, GETSTATIC, cast.name, "S") && ((FieldInsnNode) insn).name.equals(threshold.name)) {
                AbstractInsnNode amount = next(insn), add = next(amount);
                check(amount instanceof IntInsnNode && amount.getOpcode() == SIPUSH
                        && ((IntInsnNode) amount).operand == extra && add.getOpcode() == IADD
                        && next(add).getOpcode() == I2L, "Incorrect saved request gate");
                normalizedRequest.instructions.remove(amount); normalizedRequest.instructions.remove(add); removed++;
            }
        }
        normalizedRequest.maxStack = method(CombatCadencePatcher.readClass(before.get(requestEntry)), request.name, request.desc).maxStack;
        check(removed == 1 && Arrays.equals(canonical(CombatCadencePatcher.readClass(before.get(requestEntry))), canonical(normalizedRequester)),
                "Unrelated requester class change");
        Files.createDirectories(output.getAbsoluteFile().getParentFile().toPath());
        JarOutputStream stream = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String, byte[]> item : after.entrySet()) {
                JarEntry entry = new JarEntry(item.getKey()); if (times.get(item.getKey()) >= 0) entry.setTime(times.get(item.getKey()));
                stream.putNextEntry(entry); stream.write(item.getValue()); stream.closeEntry();
            }
        } finally { stream.close(); }
        JarFile saved = new JarFile(output);
        try {
            check(saved.size() == before.size(), "Entry count changed"); int changed = 0;
            for (String name : before.keySet()) {
                byte[] bytes = CombatCadencePatcher.readAll(saved.getInputStream(saved.getJarEntry(name)));
                check(Arrays.equals(bytes, after.get(name)), "Saved bytes differ");
                if (!Arrays.equals(bytes, before.get(name))) {
                    changed++; check(name.equals(castEntry) || name.equals(requestEntry), "Unexpected entry change");
                    check(CombatCadencePatcher.readClass(bytes).version == CombatCadencePatcher.readClass(before.get(name)).version,
                            "Class version changed");
                }
            }
            check(changed == 2, "Expected exactly two changed classes");
        } finally { saved.close(); }
        catalog.setProperty("output.sha256", sha256(output));
        OutputStream catalogOutput = new FileOutputStream(catalogFile);
        try { catalog.store(catalogOutput, "Exact native timing symbols and verified patch scope"); } finally { catalogOutput.close(); }
        System.out.println("auto gate=" + oldAuto + " -> " + (oldAuto + extra) + " ms");
        System.out.println("request interval=native skill interval + " + extra + " ms");
        System.out.println("binaryScope=OK (2 classes, 1 constant and 1 comparison addition only)");
        System.out.println("manifest/resources/endpoint/other entries/class versions=unchanged");
        System.out.println("sha256=" + sha256(output));
    }

    private static boolean field(AbstractInsnNode insn, int opcode, String owner, String descriptor) {
        return insn instanceof FieldInsnNode && insn.getOpcode() == opcode
                && (owner == null || owner.equals(((FieldInsnNode) insn).owner)) && descriptor.equals(((FieldInsnNode) insn).desc);
    }
    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() >= 0) result.add(insn); return result;
    }
    static MethodNode method(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        throw new IllegalStateException("Missing exact method");
    }
    private static AbstractInsnNode next(AbstractInsnNode insn) { do { insn = insn.getNext(); } while (insn != null && insn.getOpcode() < 0); return insn; }
    private static AbstractInsnNode previous(AbstractInsnNode insn) { do { insn = insn.getPrevious(); } while (insn != null && insn.getOpcode() < 0); return insn; }
    private static byte[] canonical(ClassNode node) { ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }
    private static byte[] rewrite(byte[] bytes, final MethodNode replacement) {
        ClassReader reader = new ClassReader(bytes); final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(ASM9, writer) {
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor target = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (replacement.name.equals(name) && replacement.desc.equals(descriptor)) { replacement.accept(target); return null; }
                return target;
            }
        }, 0); return writer.toByteArray();
    }
    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); InputStream input = new FileInputStream(file);
        try { byte[] buffer = new byte[8192]; int n; while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n); }
        finally { input.close(); }
        StringBuilder result = new StringBuilder(); for (byte value : digest.digest()) result.append(String.format("%02X", value & 255)); return result.toString();
    }
    static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
