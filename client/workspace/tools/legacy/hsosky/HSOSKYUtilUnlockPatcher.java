import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Unlocks the "Tiện ích" panel as far as a client can be unlocked.
 *
 * The panel keeps one boolean for "the account owns the package". It only drives
 * colours and labels — the click handler sends opcode 79 sub 3 either way — so
 * every write to that boolean is rewritten to store true. That covers all four
 * sites: the status packet (80 sub 1), the purchase result (80 sub 2), the local
 * countdown reaching zero, and the refusal branch of 80 sub 3, which is what
 * re-locked the panel and printed "Chưa mua gói tiện ích".
 *
 * What this does NOT do, and cannot: make the server agree. The gate for opening
 * a feature lives in the server's answer to 79/3. After this patch the buttons
 * are live and the panel stays open, so pressing one shows whatever the server
 * replies — a refusal message if it enforces the package, the actual screen if
 * it does not. Either way the answer is visible in one click instead of guessed.
 *
 * The countdown label is prefixed "Mod " so a patched JAR is recognisable in game.
 *
 * Usage: java -cp ".;asm-9.7.jar;asm-tree-9.7.jar" HSOSKYUtilUnlockPatcher [input.jar] [output.jar]
 */
public final class HSOSKYUtilUnlockPatcher implements Opcodes {
    private static final String DEFAULT_INPUT = "hsosky_mod_v19.jar";
    private static final String DEFAULT_OUTPUT = "HSOSKY_MOD_UTILICH_UNLOCK.jar";

    /** Unique to the panel: the RMS record it saves its position in. */
    private static final String PANEL_MARKER = "utilPopup";
    /** The status packet handler, and the source of truth for the field layout. */
    private static final String STATUS_SETTER_DESC = "(ZIIIILjava/lang/String;)V";
    private static final String COUNTDOWN_LABEL = "Còn ";
    private static final String COUNTDOWN_LABEL_PATCHED = "Mod ";

    private static final Map<String, byte[]> ENTRIES = new LinkedHashMap<String, byte[]>();
    private static final Map<String, Long> TIMES = new LinkedHashMap<String, Long>();

    private static ClassNode panel;
    private static MethodNode statusSetter;
    private static String activeField;
    private static String remainingField;
    private static String priceField;
    private static String daysField;
    private static String messageField;
    private static final List<String> unlockedSites = new ArrayList<String>();
    private static int labelPatches;

    private HSOSKYUtilUnlockPatcher() {
    }

    public static void main(String[] args) throws Exception {
        File input = new File(args.length > 0 ? args[0] : DEFAULT_INPUT);
        File output = new File(args.length > 1 ? args[1] : DEFAULT_OUTPUT);
        require(!output.exists(), "Refusing to overwrite existing output: " + output.getPath());

        readJar(input);
        discoverPanel();
        discoverFields();
        patchPanel();
        writeJar(output);
        verifyOutput(output);

        System.out.println("[+] Built " + output.getPath() + " from " + input.getPath());
        System.out.println("    panel class      : " + shortName(panel.name));
        System.out.println("    owns-package flag: " + activeField + " (forced true at "
                + unlockedSites.size() + " write sites)");
        for (String site : unlockedSites) {
            System.out.println("      - " + site);
        }
        System.out.println("    price / period   : " + priceField + " / " + daysField
                + " (server still owns both, only read)");
        System.out.println("    label            : \"" + COUNTDOWN_LABEL + "\" -> \""
                + COUNTDOWN_LABEL_PATCHED + "\"");
        System.out.println();
        System.out.println("    In game: 13 nut sang, panel khong tu khoa lai, khong con");
        System.out.println("    \"Chua mua goi tien ich\". Bam 1 nut:");
        System.out.println("      - mo duoc         -> server khong chan, dung mien phi");
        System.out.println("      - hien thong bao  -> do la cau tra loi that cua server (80/3 code=0)");
    }

    // ------------------------------------------------------------------ discovery

    /** The panel is the only class that saves an RMS record called "utilPopup". */
    private static void discoverPanel() {
        List<ClassNode> found = new ArrayList<ClassNode>();
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode owner = readClass(entry.getValue());
            if (hasLdc(owner, PANEL_MARKER) && findMethod(owner, STATUS_SETTER_DESC) != null) {
                found.add(owner);
            }
        }
        require(found.size() == 1, "Expected one utility panel class, found " + found.size());
        panel = found.get(0);
    }

    /**
     * The status handler assigns the six values in packet order, so the fields can
     * be named from the order of its PUTFIELDs rather than from obfuscated names:
     * active, remaining, extra, price, days, message.
     */
    private static void discoverFields() {
        statusSetter = findMethod(panel, STATUS_SETTER_DESC);
        require(statusSetter != null, "Panel lacks the status handler " + STATUS_SETTER_DESC);
        List<FieldInsnNode> writes = new ArrayList<FieldInsnNode>();
        for (AbstractInsnNode insn = statusSetter.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (insn.getOpcode() == PUTFIELD && panel.name.equals(((FieldInsnNode) insn).owner)) {
                writes.add((FieldInsnNode) insn);
            }
        }
        require(writes.size() >= 6, "Status handler writes " + writes.size() + " fields, expected 6+");
        require("Z".equals(writes.get(0).desc), "First status field is not the boolean flag");
        require("I".equals(writes.get(1).desc) && "I".equals(writes.get(2).desc)
                && "I".equals(writes.get(3).desc) && "I".equals(writes.get(4).desc),
                "Status fields 2..5 are not four ints");
        require("Ljava/lang/String;".equals(writes.get(5).desc),
                "Sixth status field is not the message string");
        activeField = writes.get(0).name;
        remainingField = writes.get(1).name;
        priceField = writes.get(3).name;
        daysField = writes.get(4).name;
        messageField = writes.get(5).name;
        require(!activeField.equals(remainingField) && !priceField.equals(daysField),
                "Status fields did not map to distinct names");
    }

    // ------------------------------------------------------------------- patching

    private static void patchPanel() {
        for (MethodNode method : panel.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                    insn = insn.getNext()) {
                if (insn.getOpcode() == PUTFIELD) {
                    FieldInsnNode write = (FieldInsnNode) insn;
                    if (!panel.name.equals(write.owner) || !activeField.equals(write.name)) {
                        continue;
                    }
                    String was = describe(insn);
                    // stack is [this, value]: drop whatever was computed, store true
                    InsnList force = new InsnList();
                    force.add(new InsnNode(POP));
                    force.add(new InsnNode(ICONST_1));
                    method.instructions.insertBefore(insn, force);
                    unlockedSites.add(shortName(method.name) + method.desc + was);
                } else if (insn instanceof LdcInsnNode
                        && COUNTDOWN_LABEL.equals(((LdcInsnNode) insn).cst)) {
                    ((LdcInsnNode) insn).cst = COUNTDOWN_LABEL_PATCHED;
                    labelPatches++;
                }
            }
        }
        require(unlockedSites.size() >= 3,
                "Expected at least 3 writes to " + activeField + ", patched " + unlockedSites.size());
        require(labelPatches == 1, "Expected one countdown label, patched " + labelPatches);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        panel.accept(writer);
        ENTRIES.put(panel.name + ".class", writer.toByteArray());
    }

    /**
     * Notes what the original code stored there, which is the interesting part: a
     * bare "aload_0, iconst_0, putfield" is a hard re-lock, while a value arriving
     * through a branch is the server's answer being copied in.
     */
    private static String describe(AbstractInsnNode putfield) {
        AbstractInsnNode value = previousReal(putfield);
        AbstractInsnNode holder = previousReal(value);
        boolean hardLock = value != null && value.getOpcode() == ICONST_0
                && holder != null && holder.getOpcode() == ALOAD
                && !hasLabelBetween(value, putfield);
        return hardLock ? "  (was: khoa lai)" : "  (was: theo server)";
    }

    private static boolean hasLabelBetween(AbstractInsnNode from, AbstractInsnNode to) {
        for (AbstractInsnNode cursor = from.getNext(); cursor != null && cursor != to;
                cursor = cursor.getNext()) {
            if (cursor instanceof LabelNode) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------- output

    private static void verifyOutput(File output) throws IOException {
        JarFile jar = new JarFile(output);
        try {
            require(jar.getEntry("META-INF/MANIFEST.MF") != null, "Output JAR lost its manifest");
            require(jar.size() == ENTRIES.size(), "Output JAR changed entry count");
            int forced = 0;
            int labels = 0;
            int stale = 0;
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                byte[] bytes = readAll(jar.getInputStream(entry));
                new ClassReader(bytes);
                if (!entry.getName().equals(panel.name + ".class")) {
                    continue;
                }
                ClassNode patched = readClass(bytes);
                for (MethodNode method : patched.methods) {
                    for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                            insn = insn.getNext()) {
                        if (insn.getOpcode() == PUTFIELD
                                && panel.name.equals(((FieldInsnNode) insn).owner)
                                && activeField.equals(((FieldInsnNode) insn).name)) {
                            AbstractInsnNode one = previousReal(insn);
                            AbstractInsnNode pop = one == null ? null : previousReal(one);
                            require(one != null && one.getOpcode() == ICONST_1
                                    && pop != null && pop.getOpcode() == POP,
                                    "A write to " + activeField + " in " + method.name
                                            + " does not store a constant true");
                            forced++;
                        }
                        if (insn instanceof LdcInsnNode) {
                            Object constant = ((LdcInsnNode) insn).cst;
                            if (COUNTDOWN_LABEL_PATCHED.equals(constant)) {
                                labels++;
                            } else if (COUNTDOWN_LABEL.equals(constant)) {
                                stale++;
                            }
                        }
                    }
                }
            }
            require(forced == unlockedSites.size(),
                    "Output has " + forced + " forced writes, patched " + unlockedSites.size());
            require(labels == 1 && stale == 0, "Countdown label not replaced in the output");
        } finally {
            jar.close();
        }
    }

    private static void readJar(File file) throws IOException {
        require(file.isFile(), "Missing input JAR: " + file.getPath());
        JarFile jar = new JarFile(file);
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    ENTRIES.put(entry.getName(), readAll(jar.getInputStream(entry)));
                    TIMES.put(entry.getName(), Long.valueOf(entry.getTime()));
                }
            }
        } finally {
            jar.close();
        }
    }

    private static void writeJar(File output) throws IOException {
        File temp = new File(output.getPath() + ".tmp");
        JarOutputStream out = new JarOutputStream(new FileOutputStream(temp));
        try {
            for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
                String name = entry.getKey();
                if (name.startsWith("META-INF/")
                        && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"))) {
                    continue;
                }
                JarEntry jarEntry = new JarEntry(name);
                Long time = TIMES.get(name);
                if (time != null && time.longValue() >= 0L) {
                    jarEntry.setTime(time.longValue());
                }
                out.putNextEntry(jarEntry);
                out.write(entry.getValue());
                out.closeEntry();
            }
        } finally {
            out.close();
        }
        Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // ------------------------------------------------------------------- helpers

    private static boolean hasLdc(ClassNode owner, String value) {
        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                    insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode && value.equals(((LdcInsnNode) insn).cst)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static MethodNode findMethod(ClassNode owner, String desc) {
        MethodNode found = null;
        for (MethodNode method : owner.methods) {
            if (desc.equals(method.desc)) {
                if (found != null) {
                    return null;
                }
                found = method;
            }
        }
        return found;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn == null ? null : insn.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getPrevious();
        }
        return cursor;
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);
        byte[] chunk = new byte[4096];
        try {
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
        } finally {
            in.close();
        }
        return buffer.toByteArray();
    }

    private static String shortName(String value) {
        return value.length() <= 24 ? value
                : value.substring(0, 10) + "..." + value.substring(value.length() - 10);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
