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
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Lets the native close action cancel an in-progress enhancement/crafting animation. */
public final class HsoChildEnhancementExitPatcher implements Opcodes {
    private static final String INPUT_SHA256 =
            "34B7070AB689AB9CEFCEF7926CA800C8911BD97E3C0F61A5567DA02EC728E86E";
    private static final String SUPPORT = "HSOSKYAutoEnhanceSupport";

    private static final class EntryData {
        final byte[] bytes;
        final long time;

        EntryData(byte[] bytes, long time) {
            this.bytes = bytes;
            this.time = time;
        }
    }

    private HsoChildEnhancementExitPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: HsoChildEnhancementExitPatcher <input.jar> <output.jar>");
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
        MethodNode handler = findMethod(screen, "hsoskyAutoHandle", "(II)Z");
        MethodNode restore = findMethod(screen, "hsoskyAutoRestoreUi", "()V");
        require(handler != null && restore != null, "Auto-enhance close helpers are missing");
        verifyRestoreUi(screen, restore, state);
        patchCloseBranch(screen, handler);
        verifyCloseBranch(screen, handler);

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
        System.out.println("close.action=-1");
        System.out.println("close.clears=stage,animation,input-lock,popup");
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
        require(!stage.equals(animation), "Enhancement stage and animation fields collided");
        return new StateFields(stage, animation, busy);
    }

    private static void verifyRestoreUi(ClassNode screen, MethodNode restore, StateFields state) {
        require(zeroWriteCount(restore, screen.name, state.stage, "B") == 1,
                "UI restore does not clear the enhancement stage exactly once");
        require(zeroWriteCount(restore, screen.name, state.animation, "B") == 1,
                "UI restore does not clear the animation counter exactly once");
        require(zeroWriteCount(restore, screen.name, state.busy, "Z") == 1,
                "UI restore does not clear the input lock exactly once");
    }

    private static void patchCloseBranch(ClassNode screen, MethodNode handler) {
        AbstractInsnNode branchStart = null;
        JumpInsnNode notClose = null;
        for (AbstractInsnNode insn = handler.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof VarInsnNode) || insn.getOpcode() != ILOAD
                    || ((VarInsnNode) insn).var != 1) continue;
            AbstractInsnNode minusOne = nextReal(insn);
            AbstractInsnNode jump = nextReal(minusOne);
            if (minusOne != null && minusOne.getOpcode() == ICONST_M1
                    && jump instanceof JumpInsnNode && jump.getOpcode() == IF_ICMPNE) {
                branchStart = jump.getNext();
                notClose = (JumpInsnNode) jump;
                break;
            }
        }
        require(branchStart != null && notClose != null, "Native close action -1 branch not found");

        MethodInsnNode stop = null;
        int existingRestoreCalls = 0;
        for (AbstractInsnNode insn = branchStart; insn != null && insn != notClose.label;
                insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (SUPPORT.equals(call.owner) && "stop".equals(call.name) && "()V".equals(call.desc)) {
                require(stop == null, "Close branch contains multiple auto-enhance stop calls");
                stop = call;
            }
            if (screen.name.equals(call.owner) && "hsoskyAutoRestoreUi".equals(call.name)
                    && "()V".equals(call.desc)) existingRestoreCalls++;
        }
        require(stop != null, "Close branch does not stop auto-enhance ownership");
        require(existingRestoreCalls == 0, "Close branch is already animation-exit patched");

        InsnList cleanup = new InsnList();
        cleanup.add(new MethodInsnNode(INVOKESTATIC, screen.name, "hsoskyAutoRestoreUi", "()V", false));
        handler.instructions.insert(stop, cleanup);
    }

    private static void verifyCloseBranch(ClassNode screen, MethodNode handler) {
        AbstractInsnNode closeJump = null;
        for (AbstractInsnNode insn = handler.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof VarInsnNode) || insn.getOpcode() != ILOAD
                    || ((VarInsnNode) insn).var != 1) continue;
            AbstractInsnNode minusOne = nextReal(insn);
            AbstractInsnNode jump = nextReal(minusOne);
            if (minusOne != null && minusOne.getOpcode() == ICONST_M1
                    && jump instanceof JumpInsnNode && jump.getOpcode() == IF_ICMPNE) {
                closeJump = jump;
                break;
            }
        }
        require(closeJump instanceof JumpInsnNode, "Patched close branch disappeared");
        AbstractInsnNode end = ((JumpInsnNode) closeJump).label;
        int restoreCalls = 0;
        int returns = 0;
        for (AbstractInsnNode insn = closeJump.getNext(); insn != null && insn != end; insn = insn.getNext()) {
            if (insn.getOpcode() == IRETURN) returns++;
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (screen.name.equals(call.owner) && "hsoskyAutoRestoreUi".equals(call.name)
                    && "()V".equals(call.desc) && call.getOpcode() == INVOKESTATIC) restoreCalls++;
        }
        require(restoreCalls == 1, "Close action must restore the UI exactly once");
        require(returns == 1, "Close action no longer returns to the native handler exactly once");
    }

    private static int zeroWriteCount(MethodNode method, String owner, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != PUTSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode) insn;
            if (!owner.equals(field.owner) || !name.equals(field.name) || !desc.equals(field.desc)) continue;
            AbstractInsnNode previous = previousReal(insn);
            if (previous != null && previous.getOpcode() == ICONST_0) count++;
        }
        return count;
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
                verifyCloseBranch(screen, findMethod(screen, "hsoskyAutoHandle", "(II)Z"));
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
