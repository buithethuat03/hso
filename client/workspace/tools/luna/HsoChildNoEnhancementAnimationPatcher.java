import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Removes the shared equipment-enhancement/celestial-crafting result animation. */
public final class HsoChildNoEnhancementAnimationPatcher implements Opcodes {
    private static final String INPUT_SHA256 =
            "3D792263E2EA962AD98890F874AB20633B37D5D36879B3571A78342EAB578449";
    private static final String SUPPORT = "HSOSKYAutoEnhanceSupport";

    private static final class EntryData {
        final byte[] bytes;
        final long time;

        EntryData(byte[] bytes, long time) {
            this.bytes = bytes;
            this.time = time;
        }
    }

    private static final class StateFields {
        final String stage;
        final String animation;
        final String busy;

        StateFields(String stage, String animation, String busy) {
            this.stage = stage;
            this.animation = animation;
            this.busy = busy;
        }
    }

    private HsoChildNoEnhancementAnimationPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: HsoChildNoEnhancementAnimationPatcher <input.jar> <output.jar>");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        require(input.isFile(), "Input JAR does not exist: " + input);
        require(!input.getCanonicalFile().equals(output.getCanonicalFile()),
                "Output must use a new filename");
        require(!output.exists(), "Refusing to overwrite output: " + output);
        require(INPUT_SHA256.equals(sha256(input)), "Unexpected input JAR SHA-256");

        LinkedHashMap<String, EntryData> entries = readZip(input);
        ClassNode screen = null;
        String screenEntry = null;
        EntryData originalScreen = null;
        for (Map.Entry<String, EntryData> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode candidate = readClass(entry.getValue().bytes);
            if (findMethod(candidate, "hsoskyAutoTickGate", "()V") == null) continue;
            require(screen == null, "Multiple enhancement screens found");
            screen = candidate;
            screenEntry = entry.getKey();
            originalScreen = entry.getValue();
        }
        require(screen != null, "Enhancement screen not found");

        StateFields state = discoverStateFields(screen);
        MethodNode tick = findMethod(screen, "hsoskyAutoTickGate", "()V");
        MethodNode response = findMethod(screen, "hsoskyAutoOnServerResult", "()V");
        require(tick != null && response != null, "Enhancement animation hooks are missing");
        patchTick(tick, screen.name, state);
        patchResponse(response, screen.name, state);
        verifyNoAnimation(screen, state);

        entries.put(screenEntry, new EntryData(writeClass(screen), originalScreen.time));
        File parent = output.getCanonicalFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create output directory: " + parent);
        }
        writeZip(output, entries);
        verifyZip(output, entries, screenEntry, screen.name);

        System.out.println("input.sha256=" + INPUT_SHA256);
        System.out.println("output.sha256=" + sha256(output));
        System.out.println("patched.class=" + screenEntry);
        System.out.println("enhancement.animation=disabled");
        System.out.println("disabled.state=stage,animation,input-lock");
    }

    private static StateFields discoverStateFields(ClassNode screen) {
        MethodNode restore = findMethod(screen, "hsoskyAutoRestoreUi", "()V");
        require(restore != null, "Enhancement UI restore method is missing");
        String stage = null;
        String animation = null;
        String busy = null;
        for (AbstractInsnNode insn = restore.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != PUTSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode) insn;
            if (!screen.name.equals(field.owner)) continue;
            if ("B".equals(field.desc)) {
                if (stage == null) stage = field.name;
                else if (animation == null) animation = field.name;
                else throw new IllegalStateException("Unexpected third byte state in UI restore");
            } else if ("Z".equals(field.desc)) {
                require(busy == null, "Unexpected second boolean state in UI restore");
                busy = field.name;
            }
        }
        require(stage != null && animation != null && busy != null,
                "Could not derive enhancement animation state");
        return new StateFields(stage, animation, busy);
    }

    private static void patchTick(MethodNode tick, String owner, StateFields state) {
        require(!startsWithClear(tick, owner, state), "Per-frame animation suppression already exists");
        InsnList clear = clearState(owner, state);
        tick.instructions.insert(clear);
    }

    private static void patchResponse(MethodNode response, String owner, StateFields state) {
        require(callCount(response, SUPPORT, "markResultReady", "()V") == 1,
                "Server-result readiness hook is missing");
        require(callCount(response, SUPPORT, "isActive", "()Z") == 1,
                "Unexpected server-result hook layout");
        response.instructions.clear();
        response.tryCatchBlocks.clear();
        response.localVariables = null;
        response.instructions.add(new MethodInsnNode(INVOKESTATIC, SUPPORT,
                "markResultReady", "()V", false));
        response.instructions.add(clearState(owner, state));
        response.instructions.add(new InsnNode(RETURN));
    }

    private static InsnList clearState(String owner, StateFields state) {
        InsnList clear = new InsnList();
        clear.add(new InsnNode(ICONST_0));
        clear.add(new FieldInsnNode(PUTSTATIC, owner, state.stage, "B"));
        clear.add(new InsnNode(ICONST_0));
        clear.add(new FieldInsnNode(PUTSTATIC, owner, state.animation, "B"));
        clear.add(new InsnNode(ICONST_0));
        clear.add(new FieldInsnNode(PUTSTATIC, owner, state.busy, "Z"));
        return clear;
    }

    private static void verifyNoAnimation(ClassNode screen, StateFields state) {
        MethodNode tick = findMethod(screen, "hsoskyAutoTickGate", "()V");
        MethodNode response = findMethod(screen, "hsoskyAutoOnServerResult", "()V");
        require(tick != null && startsWithClear(tick, screen.name, state),
                "Per-frame animation suppression was not persisted");
        require(response != null, "Server-result hook disappeared");
        require(callCount(response, SUPPORT, "markResultReady", "()V") == 1,
                "Server-result hook no longer notifies auto-enhance");
        require(callCount(response, SUPPORT, "isActive", "()Z") == 0,
                "Manual enhancement is still excluded from animation suppression");
        require(zeroWriteCount(response, screen.name, state.stage, "B") == 1
                        && zeroWriteCount(response, screen.name, state.animation, "B") == 1
                        && zeroWriteCount(response, screen.name, state.busy, "Z") == 1,
                "Server-result hook does not clear every animation lock");
        MethodNode close = findMethod(screen, "hsoskyAutoHandle", "(II)Z");
        require(close != null && callCount(close, screen.name, "hsoskyAutoRestoreUi", "()V") >= 1,
                "Prior close cleanup patch was lost");
    }

    private static boolean startsWithClear(MethodNode method, String owner, StateFields state) {
        AbstractInsnNode cursor = firstReal(method);
        cursor = requireOpcode(cursor, ICONST_0);
        cursor = requireField(cursor, PUTSTATIC, owner, state.stage, "B");
        cursor = requireOpcode(cursor, ICONST_0);
        cursor = requireField(cursor, PUTSTATIC, owner, state.animation, "B");
        cursor = requireOpcode(cursor, ICONST_0);
        return matchesField(cursor, PUTSTATIC, owner, state.busy, "Z");
    }

    private static AbstractInsnNode requireOpcode(AbstractInsnNode insn, int opcode) {
        if (insn == null || insn.getOpcode() != opcode) return null;
        return nextReal(insn);
    }

    private static AbstractInsnNode requireField(AbstractInsnNode insn, int opcode,
            String owner, String name, String desc) {
        if (!matchesField(insn, opcode, owner, name, desc)) return null;
        return nextReal(insn);
    }

    private static boolean matchesField(AbstractInsnNode insn, int opcode,
            String owner, String name, String desc) {
        if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != opcode) return false;
        FieldInsnNode field = (FieldInsnNode) insn;
        return owner.equals(field.owner) && name.equals(field.name) && desc.equals(field.desc);
    }

    private static AbstractInsnNode firstReal(MethodNode method) {
        AbstractInsnNode cursor = method.instructions.getFirst();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getNext();
        return cursor;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        for (AbstractInsnNode cursor = insn == null ? null : insn.getNext(); cursor != null;
                cursor = cursor.getNext()) {
            if (cursor.getOpcode() >= 0) return cursor;
        }
        return null;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        for (AbstractInsnNode cursor = insn == null ? null : insn.getPrevious(); cursor != null;
                cursor = cursor.getPrevious()) {
            if (cursor.getOpcode() >= 0) return cursor;
        }
        return null;
    }

    private static int zeroWriteCount(MethodNode method, String owner, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!matchesField(insn, PUTSTATIC, owner, name, desc)) continue;
            AbstractInsnNode previous = previousReal(insn);
            if (previous != null && previous.getOpcode() == ICONST_0) count++;
        }
        return count;
    }

    private static int callCount(MethodNode method, String owner, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) count++;
        }
        return count;
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        return null;
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    private static byte[] writeClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static LinkedHashMap<String, EntryData> readZip(File input) throws IOException {
        LinkedHashMap<String, EntryData> entries = new LinkedHashMap<String, EntryData>();
        ZipFile zip = new ZipFile(input);
        try {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                InputStream stream = zip.getInputStream(entry);
                try {
                    entries.put(entry.getName(), new EntryData(readAll(stream), entry.getTime()));
                } finally {
                    stream.close();
                }
            }
        } finally {
            zip.close();
        }
        return entries;
    }

    private static void writeZip(File output, LinkedHashMap<String, EntryData> entries) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String, EntryData> item : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(item.getKey());
                if (item.getValue().time >= 0L) entry.setTime(item.getValue().time);
                zip.putNextEntry(entry);
                zip.write(item.getValue().bytes);
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
    }

    private static void verifyZip(File output, LinkedHashMap<String, EntryData> expected,
            String changedEntry, String screenName) throws IOException {
        LinkedHashMap<String, EntryData> actual = readZip(output);
        require(actual.size() == expected.size(), "JAR entry count changed");
        int changed = 0;
        for (Map.Entry<String, EntryData> entry : expected.entrySet()) {
            EntryData found = actual.get(entry.getKey());
            require(found != null, "JAR entry disappeared: " + entry.getKey());
            if (changedEntry.equals(entry.getKey())) {
                changed++;
                ClassNode screen = readClass(found.bytes);
                require(screenName.equals(screen.name), "Patched screen identity changed");
                verifyNoAnimation(screen, discoverStateFields(screen));
            } else {
                require(java.util.Arrays.equals(entry.getValue().bytes, found.bytes),
                        "Unexpected JAR content change: " + entry.getKey());
            }
        }
        require(changed == 1, "Expected exactly one changed screen class");
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        } finally {
            input.close();
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02X", Integer.valueOf(value & 0xFF)));
        return result.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
