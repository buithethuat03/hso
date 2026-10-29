package luna.tools;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Removes the auto-attack mount spoof without rebuilding the legacy support. */
public final class NativeHorseStatePatcher implements Opcodes {
    public static void main(String[] args) throws Exception {
        check(args.length == 4, "Usage: <input.jar> <new-output.jar> <base-symbols> <new-catalog>");
        File input = new File(args[0]), output = new File(args[1]), catalogFile = new File(args[3]);
        check(!output.exists() && !catalogFile.exists(), "Output/catalog already exists");
        Properties base = new Properties();
        InputStream symbolInput = new FileInputStream(args[2]);
        try { base.load(symbolInput); } finally { symbolInput.close(); }
        LinkedHashMap<String, byte[]> before = new LinkedHashMap<String, byte[]>();
        Map<String, Long> times = new HashMap<String, Long>();
        JarFile jar = new JarFile(input);
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                check(!before.containsKey(entry.getName()), "Duplicate JAR entry");
                before.put(entry.getName(), CombatCadencePatcher.readAll(jar.getInputStream(entry)));
                times.put(entry.getName(), entry.getTime());
            }
        } finally { jar.close(); }
        String entityOwner = required(base, "class.ENTITY"), playerOwner = required(base, "class.PLAYER");
        ClassNode entity = read(before, entityOwner), player = read(before, playerOwner);
        check(entityOwner.equals(player.superName), "Player must inherit the catalog entity");
        FieldInsnNode mount = null, bonusTable = null;
        MethodNode speed = null;
        // The native speed bonus is zero for mount -1, otherwise byteTable[mount].
        for (MethodNode method : entity.methods) {
            List<AbstractInsnNode> code = real(method);
            if (!"()I".equals(method.desc) || code.size() != 11) continue;
            if (code.get(0).getOpcode() != ALOAD || !field(code.get(1), GETFIELD, entityOwner, "B")
                    || code.get(2).getOpcode() != ICONST_M1 || code.get(3).getOpcode() != IF_ICMPNE
                    || code.get(4).getOpcode() != ICONST_0 || code.get(5).getOpcode() != IRETURN
                    || !field(code.get(6), GETSTATIC, null, "[B") || code.get(7).getOpcode() != ALOAD
                    || !field(code.get(8), GETFIELD, entityOwner, "B")
                    || !((FieldInsnNode)code.get(1)).name.equals(((FieldInsnNode)code.get(8)).name)
                    || code.get(9).getOpcode() != BALOAD || code.get(10).getOpcode() != IRETURN) continue;
            check(speed == null, "Ambiguous native mount speed accessor");
            speed = method; mount = (FieldInsnNode)code.get(1); bonusTable = (FieldInsnNode)code.get(6);
        }
        check(speed != null, "Missing exact native mount speed accessor");
        check(hasField(entity, mount.name, "B"), "Mount field not declared in entity");
        check(!hasField(player, mount.name, "B"), "Unexpected shadowed player mount field");
        check(hasField(read(before, bonusTable.owner), bonusTable.name, "[B"), "Missing native speed table");
        String supportOwner = "HSOSKYPetFeedSupport", supportEntry = supportOwner + ".class";
        ClassNode support = read(before, supportOwner);
        MethodNode reengage = NativeCombatDelayPatcher.method(support, "reengageAutoAttack", "()V");
        FieldInsnNode write = null, currentPlayer = null;
        AbstractInsnNode one = null;
        for (AbstractInsnNode insn : reengage.instructions.toArray()) {
            if (!field(insn, PUTFIELD, playerOwner, "B") || !mount.name.equals(((FieldInsnNode)insn).name)) continue;
            check(write == null, "Multiple local mount writes");
            one = previous(insn);
            AbstractInsnNode load = previous(one);
            check(one.getOpcode() == ICONST_1 && field(load, GETSTATIC, null, "L" + playerOwner + ";"),
                    "Unexpected local mount assignment");
            write = (FieldInsnNode)insn; currentPlayer = (FieldInsnNode)load;
        }
        check(write != null, "Missing auto-attack mount spoof");
        check(hasField(read(before, currentPlayer.owner), currentPlayer.name, currentPlayer.desc), "Missing current-player field");
        int localWriters = 0;
        for (Map.Entry<String, byte[]> entry : before.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode node = CombatCadencePatcher.readClass(entry.getValue());
            if (!node.name.equals(supportOwner) && !node.name.startsWith("luna/")
                    && !node.name.equals("LunaGeneratedLegacyHooks") && !node.name.equals("HSOAutoTradeSupport")) continue;
            for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray()) {
                if ((field(insn, PUTFIELD, playerOwner, "B") || field(insn, PUTFIELD, entityOwner, "B"))
                        && mount.name.equals(((FieldInsnNode)insn).name)) localWriters++;
            }
        }
        check(localWriters == 1, "Unexpected additional MOD mount writers");
        // Preserve the anchor so the full class can be reconstructed for comparison.
        AbstractInsnNode anchor = currentPlayer.getPrevious();
        reengage.instructions.remove(currentPlayer);
        reengage.instructions.remove(one);
        reengage.instructions.remove(write);
        byte[] patched = rewrite(before.get(supportEntry), reengage);
        InsnList restored = new InsnList();
        restored.add(new FieldInsnNode(GETSTATIC, currentPlayer.owner, currentPlayer.name, currentPlayer.desc));
        restored.add(new InsnNode(ICONST_1));
        restored.add(new FieldInsnNode(PUTFIELD, write.owner, write.name, write.desc));
        reengage.instructions.insert(anchor, restored);
        check(Arrays.equals(canonical(CombatCadencePatcher.readClass(before.get(supportEntry))), canonical(support)),
                "Removal affected other support instructions");
        ClassNode savedSupport = CombatCadencePatcher.readClass(patched);
        check(savedSupport.version == support.version, "Class version changed");
        MethodNode savedMethod = NativeCombatDelayPatcher.method(savedSupport, reengage.name, reengage.desc);
        check(real(savedMethod).size() + 3 == real(reengage).size(), "Wrong removed instruction count");
        // Reconstruct from saved bytes as a second proof, including every other method/field.
        List<AbstractInsnNode> oldCode = real(reengage), newCode = real(savedMethod);
        int position = -1;
        for (int i = 0; i < oldCode.size(); i++) if (field(oldCode.get(i), PUTFIELD, playerOwner, "B")
                && mount.name.equals(((FieldInsnNode)oldCode.get(i)).name)) position = i - 2;
        check(position > 0, "Missing reconstruction anchor");
        InsnList inverse = new InsnList();
        inverse.add(new FieldInsnNode(GETSTATIC, currentPlayer.owner, currentPlayer.name, currentPlayer.desc));
        inverse.add(new InsnNode(ICONST_1)); inverse.add(new FieldInsnNode(PUTFIELD, write.owner, write.name, write.desc));
        savedMethod.instructions.insert(newCode.get(position - 1), inverse);
        savedMethod.maxStack = reengage.maxStack;
        check(Arrays.equals(canonical(CombatCadencePatcher.readClass(before.get(supportEntry))), canonical(savedSupport)),
                "Saved class contains unrelated changes");
        Files.createDirectories(output.getAbsoluteFile().getParentFile().toPath());
        JarOutputStream stream = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String, byte[]> entry : before.entrySet()) {
                JarEntry target = new JarEntry(entry.getKey());
                if (times.get(entry.getKey()) >= 0) target.setTime(times.get(entry.getKey()));
                stream.putNextEntry(target);
                stream.write(entry.getKey().equals(supportEntry) ? patched : entry.getValue()); stream.closeEntry();
            }
        } finally { stream.close(); }
        JarFile saved = new JarFile(output);
        try {
            check(saved.size() == before.size(), "Entry count changed");
            for (Map.Entry<String, byte[]> entry : before.entrySet()) {
                byte[] expected = entry.getKey().equals(supportEntry) ? patched : entry.getValue();
                check(Arrays.equals(expected, CombatCadencePatcher.readAll(saved.getInputStream(saved.getJarEntry(entry.getKey())))),
                        "Unexpected saved entry: " + entry.getKey());
            }
        } finally { saved.close(); }
        Properties catalog = new Properties();
        catalog.setProperty("input.sha256", sha256(input)); catalog.setProperty("output.sha256", sha256(output));
        catalog.setProperty("mount.owner", entityOwner); catalog.setProperty("mount.access.owner", playerOwner);
        catalog.setProperty("mount.field", mount.name); catalog.setProperty("mount.descriptor", mount.desc);
        catalog.setProperty("mount.speed.method", speed.name); catalog.setProperty("mount.speed.descriptor", speed.desc);
        catalog.setProperty("mount.speed.table.owner", bonusTable.owner); catalog.setProperty("mount.speed.table.field", bonusTable.name);
        catalog.setProperty("mount.speed.table.descriptor", bonusTable.desc);
        catalog.setProperty("player.owner", playerOwner); catalog.setProperty("player.current.owner", currentPlayer.owner);
        catalog.setProperty("player.current.field", currentPlayer.name); catalog.setProperty("player.current.descriptor", currentPlayer.desc);
        catalog.setProperty("support.owner", supportOwner); catalog.setProperty("support.method", reengage.name);
        catalog.setProperty("support.descriptor", reengage.desc); catalog.setProperty("removed.instructions", "3");
        OutputStream catalogOutput = new FileOutputStream(catalogFile);
        try { catalog.store(catalogOutput, "Verified native mount field and removed auto-attack mount spoof"); }
        finally { catalogOutput.close(); }
        System.out.println("binaryScope=OK (one support method; three mount-spoof instructions removed)");
        System.out.println("native mount packets/speed tables/combat cadence/manifest/resources/other entries=unchanged");
        System.out.println("sha256=" + sha256(output));
    }
    private static ClassNode read(Map<String, byte[]> entries, String owner) {
        byte[] data = entries.get(owner + ".class"); check(data != null, "Missing exact owner: " + owner);
        return CombatCadencePatcher.readClass(data);
    }
    private static boolean hasField(ClassNode node, String name, String descriptor) {
        for (FieldNode field : node.fields) if (field.name.equals(name) && field.desc.equals(descriptor)) return true;
        return false;
    }
    private static boolean field(AbstractInsnNode insn, int opcode, String owner, String descriptor) {
        return insn instanceof FieldInsnNode && insn.getOpcode() == opcode
                && (owner == null || owner.equals(((FieldInsnNode)insn).owner)) && descriptor.equals(((FieldInsnNode)insn).desc);
    }
    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> code = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() >= 0) code.add(insn);
        return code;
    }
    private static AbstractInsnNode previous(AbstractInsnNode insn) {
        do { insn = insn.getPrevious(); } while (insn != null && insn.getOpcode() < 0); return insn;
    }
    private static String required(Properties symbols, String key) {
        String value = symbols.getProperty(key); check(value != null, "Missing symbol: " + key); return value;
    }
    private static byte[] canonical(ClassNode node) {
        // Deleting source line 458 coalesces its label with the try-end label.
        // Compare all executable structure, frames and handlers without line metadata.
        ClassWriter first = new ClassWriter(0); node.accept(first);
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(first.toByteArray()).accept(writer, ClassReader.SKIP_DEBUG);
        return writer.toByteArray();
    }
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
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
