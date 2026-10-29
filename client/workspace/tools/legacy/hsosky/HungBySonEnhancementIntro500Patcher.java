import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Shortens only the enhancement pre-result swirl; success/failure paths remain native. */
public final class HungBySonEnhancementIntro500Patcher implements Opcodes {
    private static final long OLD_INTRO_MS = 3700L;
    private static final long NEW_INTRO_MS = 500L;
    private static final int INTRO_BRANCHES = 6;

    private static final class EntryData {
        final JarEntry source;
        byte[] bytes;
        EntryData(JarEntry source, byte[] bytes) { this.source = source; this.bytes = bytes; }
    }

    private HungBySonEnhancementIntro500Patcher() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.jar output.jar");
        File input = new File(args[0]).getCanonicalFile();
        File output = new File(args[1]).getCanonicalFile();
        if (!input.isFile() || input.equals(output) || output.exists()) {
            throw new IllegalArgumentException("Input missing, equal to output, or output exists");
        }
        List<EntryData> entries = read(input);
        String screen = null;
        for (EntryData entry : entries) {
            if (!entry.source.getName().endsWith(".class")) continue;
            ClassNode node = parse(entry.bytes);
            if (!containsMarker(node, "TabRebuildItem vecListCmd")) continue;
            if (screen != null) throw new IllegalStateException("Multiple enhancement screens");
            MethodNode update = findIntroUpdate(node);
            if (update == null) throw new IllegalStateException("Enhancement intro update missing");
            int changed = patchIntro(update);
            if (changed != INTRO_BRANCHES) throw new IllegalStateException("Expected six intro branches, got " + changed);
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            entry.bytes = writer.toByteArray();
            screen = node.name;
        }
        if (screen == null) throw new IllegalStateException("Enhancement screen missing");
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot make output directory");
        write(output, entries);
        verify(output, screen, entries.size());
        System.out.println("SCREEN=" + screen);
        System.out.println("INTRO_MS=" + NEW_INTRO_MS);
        System.out.println("PATCHED_BRANCHES=" + INTRO_BRANCHES);
        System.out.println("ENTRIES=" + entries.size());
    }

    private static boolean containsMarker(ClassNode node, String marker) {
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode i = method.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof LdcInsnNode && marker.equals(((LdcInsnNode)i).cst)) return true;
            }
        }
        return false;
    }

    private static MethodNode findIntroUpdate(ClassNode node) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if (!"()V".equals(method.desc) || (method.access & ACC_STATIC) != 0) continue;
            int count = 0;
            for (AbstractInsnNode i = method.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof LdcInsnNode && Long.valueOf(OLD_INTRO_MS).equals(((LdcInsnNode)i).cst)) count++;
            }
            if (count == INTRO_BRANCHES) {
                if (result != null) throw new IllegalStateException("Multiple enhancement update methods");
                result = method;
            }
        }
        return result;
    }

    private static int patchIntro(MethodNode update) {
        int count = 0;
        for (AbstractInsnNode i = update.instructions.getFirst(); i != null; i = i.getNext()) {
            if (!(i instanceof LdcInsnNode) || !Long.valueOf(OLD_INTRO_MS).equals(((LdcInsnNode)i).cst)) continue;
            AbstractInsnNode before = previous(i);
            AbstractInsnNode compare = next(i);
            AbstractInsnNode branch = next(compare);
            if (before == null || before.getOpcode() != LSUB || compare == null
                    || compare.getOpcode() != LCMP || !(branch instanceof JumpInsnNode)
                    || branch.getOpcode() != IFLE) {
                throw new IllegalStateException("Unexpected enhancement intro timer pattern");
            }
            ((LdcInsnNode)i).cst = Long.valueOf(NEW_INTRO_MS);
            ((JumpInsnNode)branch).setOpcode(IFLT);
            count++;
        }
        return count;
    }

    private static AbstractInsnNode next(AbstractInsnNode i) {
        AbstractInsnNode n = i == null ? null : i.getNext();
        while (n != null && n.getOpcode() < 0) n = n.getNext();
        return n;
    }
    private static AbstractInsnNode previous(AbstractInsnNode i) {
        AbstractInsnNode n = i == null ? null : i.getPrevious();
        while (n != null && n.getOpcode() < 0) n = n.getPrevious();
        return n;
    }
    private static ClassNode parse(byte[] bytes) {
        ClassNode node = new ClassNode(ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
    private static List<EntryData> read(File input) throws IOException {
        List<EntryData> result = new ArrayList<EntryData>();
        JarFile jar = new JarFile(input);
        try {
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                JarEntry entry = e.nextElement();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                java.io.InputStream in = jar.getInputStream(entry);
                try {
                    byte[] block = new byte[8192];
                    for (int count; (count = in.read(block)) >= 0;) out.write(block, 0, count);
                } finally { in.close(); }
                result.add(new EntryData(entry, out.toByteArray()));
            }
        } finally { jar.close(); }
        return result;
    }
    private static void write(File output, List<EntryData> entries) throws IOException {
        JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
        try {
            for (EntryData data : entries) {
                JarEntry entry = new JarEntry(data.source.getName());
                if (data.source.getTime() >= 0) entry.setTime(data.source.getTime());
                if (data.source.getComment() != null) entry.setComment(data.source.getComment());
                if (data.source.getExtra() != null) entry.setExtra(data.source.getExtra());
                out.putNextEntry(entry);
                out.write(data.bytes);
                out.closeEntry();
            }
        } finally { out.close(); }
    }
    private static void verify(File output, String screen, int expectedEntries) throws IOException {
        JarFile jar = new JarFile(output);
        try {
            if (jar.size() != expectedEntries) throw new IllegalStateException("Entry count changed");
            JarEntry entry = jar.getJarEntry(screen + ".class");
            if (entry == null) throw new IllegalStateException("Patched screen missing");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            java.io.InputStream in = jar.getInputStream(entry);
            try {
                byte[] block = new byte[8192];
                for (int count; (count = in.read(block)) >= 0;) bytes.write(block, 0, count);
            } finally { in.close(); }
            ClassNode node = parse(bytes.toByteArray());
            int intro500 = 0, old3700 = 0;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode i = method.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i instanceof LdcInsnNode) {
                        if (Long.valueOf(NEW_INTRO_MS).equals(((LdcInsnNode)i).cst)
                                && next(i) != null && next(i).getOpcode() == LCMP
                                && next(next(i)).getOpcode() == IFLT) intro500++;
                        if (Long.valueOf(OLD_INTRO_MS).equals(((LdcInsnNode)i).cst)) old3700++;
                    }
                }
            }
            if (intro500 != INTRO_BRANCHES || old3700 != 0)
                throw new IllegalStateException("Intro timer verification failed");
        } finally { jar.close(); }
    }
}
