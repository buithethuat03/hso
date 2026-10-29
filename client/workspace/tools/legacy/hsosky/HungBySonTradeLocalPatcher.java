import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Makes the Hung_By_Son "Auto Di Buon V3" actions work without the licence server.
 *
 * 1. Expands HungBySonTradeLocal.java.in (markers @~suffix@ = 100-char obfuscated names), compiles it
 *    against the input JAR (so every referenced name/descriptor/access is checked), stores it as class
 *    version 48 like the client.
 * 2. Hooks: formula getter -> HungBySonTradeLocal.formula; "key verified" -> handles; menu-open
 *    dispatcher -> onMenu; shop screen constructor -> onShop.
 *
 * Usage: java -cp "<out>;asm-9.7.jar;asm-tree-9.7.jar" HungBySonTradeLocalPatcher
 *            input.jar output.jar HungBySonTradeLocal.java.in workDir
 */
public final class HungBySonTradeLocalPatcher implements Opcodes {
    private static final int NAME_LEN = 100;
    private static final String SUPPORT = "HungBySonTradeLocal";

    private static final String SECRETS = n("lilllill");
    private static final String MODMENU = n("lillllll");
    private static final String SHOP = n("liililli");
    private static final String GETTER = n("lili");
    private static final String VERIFIED = n("liii");
    private static final String DISPATCH = n("l");

    private static final class EntryData {
        final JarEntry source;
        byte[] bytes;
        EntryData(JarEntry source, byte[] bytes) { this.source = source; this.bytes = bytes; }
    }

    private HungBySonTradeLocalPatcher() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("input.jar output.jar template.java.in workDir");
        File input = new File(args[0]).getCanonicalFile();
        File output = new File(args[1]).getCanonicalFile();
        File template = new File(args[2]).getCanonicalFile();
        File work = new File(args[3]).getCanonicalFile();
        if (!input.isFile() || !template.isFile() || input.equals(output) || output.exists()) {
            throw new IllegalArgumentException("Input/template missing, input equals output, or output exists");
        }
        if (!work.isDirectory() && !work.mkdirs()) throw new IOException("Cannot make " + work);

        List<EntryData> entries = read(input);
        for (EntryData e : entries) require(!e.source.getName().equals(SUPPORT + ".class"), "already patched");

        byte[] support = compileSupport(template, work, input);

        patch(entries, SECRETS, new Patch() {
            public int apply(ClassNode c) {
                MethodNode m = method(c, GETTER, "(Ljava/lang/String;I)Ljava/lang/String;", true);
                require(readsHashtable(m), "formula getter reads the cache");
                LabelNode keep = new LabelNode();
                InsnList h = new InsnList();
                h.add(new VarInsnNode(ALOAD, 0));
                h.add(new VarInsnNode(ILOAD, 1));
                h.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "formula", "(Ljava/lang/String;I)Ljava/lang/String;", false));
                h.add(new InsnNode(DUP));
                h.add(new JumpInsnNode(IFNULL, keep));
                h.add(new InsnNode(ARETURN));
                h.add(keep);
                h.add(new InsnNode(POP));
                m.instructions.insert(h);

                MethodNode v = method(c, VERIFIED, "(Ljava/lang/String;I)Z", true);
                require(readsHashtable(v), "verified check reads the cache");
                LabelNode no = new LabelNode();
                InsnList g = new InsnList();
                g.add(new VarInsnNode(ALOAD, 0));
                g.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "handles", "(Ljava/lang/String;)Z", false));
                g.add(new JumpInsnNode(IFEQ, no));
                g.add(new InsnNode(ICONST_1));
                g.add(new InsnNode(IRETURN));
                g.add(no);
                v.instructions.insert(g);
                return 2;
            }
        });
        patch(entries, MODMENU, new Patch() {
            public int apply(ClassNode c) {
                MethodNode m = method(c, DISPATCH, "(Let;Ljava/lang/String;)Z", true);
                InsnList h = new InsnList();
                h.add(new VarInsnNode(ALOAD, 0));
                h.add(new VarInsnNode(ALOAD, 1));
                h.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "onMenu", "(Let;Ljava/lang/String;)V", false));
                m.instructions.insert(h);
                return 1;
            }
        });
        patch(entries, SHOP, new Patch() {
            public int apply(ClassNode c) {
                MethodNode m = method(c, "<init>", "(Let;BLjava/lang/String;IB)V", false);
                int n = 0;
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() != RETURN) continue;
                    InsnList h = new InsnList();
                    h.add(new VarInsnNode(ALOAD, 0));
                    h.add(new VarInsnNode(ALOAD, 1));
                    h.add(new VarInsnNode(ILOAD, 4));
                    h.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "onShop", "(Ljava/lang/Object;Let;I)V", false));
                    m.instructions.insertBefore(i, h);
                    n++;
                }
                require(n >= 1, "shop constructor returns");
                return n;
            }
        });

        entries.add(new EntryData(new JarEntry(SUPPORT + ".class"), support));
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot make output directory");
        writeJar(output, entries);
        verify(output, entries.size());
        System.out.println("SUPPORT=" + SUPPORT + " (" + support.length + " bytes, class version 48)");
        System.out.println("HOOKS formula/handles on " + SECRETS.replaceAll("i{40,}", "~") + ", onMenu on "
                + MODMENU.replaceAll("i{40,}", "~") + ", onShop on " + SHOP.replaceAll("i{40,}", "~"));
        System.out.println("ENTRIES=" + entries.size());
    }

    // ------------------------------------------------------------------ support class

    private static byte[] compileSupport(File template, File work, File jar) throws Exception {
        String src = new String(readAll(new FileInputStream(template)), "UTF-8");
        StringBuilder out = new StringBuilder(src.length() * 3);
        int i = 0;
        while (i < src.length()) {
            char ch = src.charAt(i);
            if (ch == '@' && i + 1 < src.length() && src.charAt(i + 1) == '~') {
                int end = src.indexOf('@', i + 2);
                require(end > 0, "unterminated marker at " + i);
                out.append(n(src.substring(i + 2, end)));
                i = end + 1;
            } else {
                out.append(ch);
                i++;
            }
        }
        File java = new File(work, SUPPORT + ".java");
        Writer w = new OutputStreamWriter(new FileOutputStream(java), "UTF-8");
        try { w.write(out.toString()); } finally { w.close(); }

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        require(javac != null, "javac (JDK) required");
        int rc = javac.run(null, null, null, "-encoding", "UTF-8", "--release", "7", "-nowarn", "-Xlint:-options",
                "-cp", jar.getPath() + File.pathSeparator + System.getProperty("hbs.extra.cp", ""),
                "-d", work.getPath(), java.getPath());
        require(rc == 0, "support class does not compile against " + jar.getName());

        ClassNode node = new ClassNode(ASM9);
        new ClassReader(readAll(new FileInputStream(new File(work, SUPPORT + ".class")))).accept(node, ClassReader.SKIP_FRAMES);
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode x = m.instructions.getFirst(); x != null; x = x.getNext()) {
                require(x.getOpcode() != INVOKEDYNAMIC, "invokedynamic in support class");
            }
        }
        require(new File(work, SUPPORT + "$1.class").exists() == false, "support must be a single class");
        node.version = V1_4;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(cw);
        return cw.toByteArray();
    }

    // ------------------------------------------------------------------ patching

    private interface Patch { int apply(ClassNode c); }

    private static void patch(List<EntryData> entries, String cls, Patch p) {
        EntryData e = entry(entries, cls + ".class");
        ClassNode c = parse(e.bytes);
        require(c.version == V1_4, cls + " is class version 48");
        require(p.apply(c) > 0, "patch applied to " + cls);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(cw);
        e.bytes = cw.toByteArray();
    }

    private static MethodNode method(ClassNode c, String name, String desc, boolean isStatic) {
        MethodNode found = null;
        for (MethodNode m : c.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                require(found == null, "duplicate " + name + desc);
                found = m;
            }
        }
        require(found != null, "method " + desc + " in " + c.name.replaceAll("i{40,}", "~"));
        require(((found.access & ACC_STATIC) != 0) == isStatic, "static flag of " + desc);
        return found;
    }

    private static boolean readsHashtable(MethodNode m) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode && "java/util/Hashtable".equals(((MethodInsnNode) i).owner)
                    && "get".equals(((MethodInsnNode) i).name)) return true;
        }
        return false;
    }

    private static void verify(File output, int expected) throws IOException {
        JarFile jar = new JarFile(output);
        try {
            require(jar.size() == expected, "entry count");
            ClassNode s = parse(readAll(jar.getInputStream(jar.getJarEntry(SUPPORT + ".class"))));
            require(s.version == V1_4 && s.interfaces.contains("IModModule"), "support class shape");
            require(calls(jar, SECRETS, "formula") == 1 && calls(jar, SECRETS, "handles") == 1, "secret hooks");
            require(calls(jar, MODMENU, "onMenu") == 1, "menu hook");
            require(calls(jar, SHOP, "onShop") >= 1, "shop hook");
        } finally { jar.close(); }
    }

    private static int calls(JarFile jar, String cls, String name) throws IOException {
        ClassNode c = parse(readAll(jar.getInputStream(jar.getJarEntry(cls + ".class"))));
        int n = 0;
        for (MethodNode m : c.methods) {
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof MethodInsnNode && SUPPORT.equals(((MethodInsnNode) i).owner)
                        && name.equals(((MethodInsnNode) i).name)) n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ helpers

    /** "~suffix" -> leading 'i' run + suffix, 100 characters in total. */
    private static String n(String suffix) {
        require(suffix.length() <= NAME_LEN, "suffix too long");
        StringBuilder b = new StringBuilder(NAME_LEN);
        for (int i = suffix.length(); i < NAME_LEN; i++) b.append('i');
        return b.append(suffix).toString();
    }
    private static void require(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("Unexpected structure: " + what);
    }
    private static ClassNode parse(byte[] b) { ClassNode n = new ClassNode(ASM9); new ClassReader(b).accept(n, 0); return n; }
    private static EntryData entry(List<EntryData> entries, String name) {
        for (EntryData e : entries) if (e.source.getName().equals(name)) return e;
        throw new IllegalStateException("Missing entry " + name.replaceAll("i{40,}", "~"));
    }
    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int k; (k = in.read(b)) >= 0;) out.write(b, 0, k);
            return out.toByteArray();
        } finally { in.close(); }
    }
    private static List<EntryData> read(File input) throws IOException {
        List<EntryData> result = new ArrayList<EntryData>();
        JarFile jar = new JarFile(input);
        try {
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                JarEntry entry = e.nextElement();
                result.add(new EntryData(entry, readAll(jar.getInputStream(entry))));
            }
        } finally { jar.close(); }
        return result;
    }
    private static void writeJar(File output, List<EntryData> entries) throws IOException {
        JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
        try {
            for (EntryData d : entries) {
                JarEntry e = new JarEntry(d.source.getName());
                if (d.source.getTime() >= 0) e.setTime(d.source.getTime());
                out.putNextEntry(e);
                out.write(d.bytes);
                out.closeEntry();
            }
        } finally { out.close(); }
    }
}
