package luna.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Prevents the original client from opening its native menu titled
 * "Tự động". This is applied at MENUBOX.show, so it does not depend on the
 * frame hook running after the menu has already been displayed.
 */
public final class NativeAutoMenuPatcher implements Opcodes {

    private static final String TITLE = "Tự động";
    private static final String STRING = "java/lang/String";

    private NativeAutoMenuPatcher() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: java -cp build/tools;asm*.jar "
                    + "luna.tools.NativeAutoMenuPatcher <input.jar> <output.jar>");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        if (!input.isFile()) throw new IllegalArgumentException("input JAR not found: " + input);
        if (output.exists()) throw new IllegalArgumentException("refusing to overwrite: " + output);

        String symbolPath = System.getProperty("luna.symbols", "symbols/autotrade-symbols.properties");
        Properties symbols = load(new File(symbolPath));
        String menuClass = required(symbols, "class.MENUBOX");
        String titleField = required(symbols, "menu.title");
        String showingField = required(symbols, "menu.isShowing");
        String showName = required(symbols, "menubox.show");
        String showDesc = "(L" + required(symbols, "class.VEC")
                + ";ILjava/lang/String;ZL" + required(symbols, "class.VEC") + ";)V";

        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        Map<String, Long> times = new LinkedHashMap<String, Long>();
        readJar(input, entries, times);
        String entry = menuClass + ".class";
        byte[] original = entries.get(entry);
        require(original != null, "MENUBOX class missing from input JAR");
        byte[] patched = patch(original, menuClass, titleField, showingField, showName, showDesc);
        entries.put(entry, patched);
        writeJar(output, entries, times);
        verify(patched, menuClass, titleField, showingField, showName, showDesc);
        System.out.println("nativeAutoMenu=OK (MENUBOX.show guard)");
    }

    private static byte[] patch(byte[] bytes, String owner, String titleField,
            String showingField, String showName, String showDesc) {
        ClassNode node = readClass(bytes);
        require(owner.equals(node.name), "MENUBOX symbol points to a different class");
        require(findField(node, titleField, "Ljava/lang/String;") != null,
                "menu.title symbol is not a String field on MENUBOX");
        require(findField(node, showingField, "Z") != null,
                "menu.isShowing symbol is not a boolean field on MENUBOX");
        MethodNode show = findMethod(node, showName, showDesc);
        require(show != null, "MENUBOX.show symbol/descriptor not found");

        LabelNode continueLabel = new LabelNode();
        InsnList guard = new InsnList();
        // MENUBOX.show(Vector, int, String title, boolean, Vector): title is local 3.
        guard.add(new VarInsnNode(ALOAD, 3));
        guard.add(new JumpInsnNode(IFNULL, continueLabel));
        guard.add(new LdcInsnNode(TITLE));
        guard.add(new VarInsnNode(ALOAD, 3));
        guard.add(new MethodInsnNode(INVOKEVIRTUAL, STRING, "equals",
                "(Ljava/lang/Object;)Z", false));
        guard.add(new JumpInsnNode(IFEQ, continueLabel));
        guard.add(new VarInsnNode(ALOAD, 0));
        guard.add(new InsnNode(ICONST_0));
        guard.add(new FieldInsnNode(PUTFIELD, owner, showingField, "Z"));
        guard.add(new InsnNode(RETURN));
        guard.add(continueLabel);
        show.instructions.insert(guard);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void verify(byte[] bytes, String owner, String titleField,
            String showingField, String showName, String showDesc) {
        ClassNode node = readClass(bytes);
        MethodNode show = findMethod(node, showName, showDesc);
        require(show != null, "patched MENUBOX.show disappeared");
        int titleConstants = 0;
        int closeWrites = 0;
        for (AbstractInsnNode insn = show.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode && TITLE.equals(((LdcInsnNode) insn).cst)) {
                titleConstants++;
            }
            if (insn instanceof FieldInsnNode && insn.getOpcode() == PUTFIELD) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (owner.equals(field.owner) && showingField.equals(field.name)
                        && "Z".equals(field.desc)) closeWrites++;
            }
        }
        require(titleConstants == 1, "native auto title guard missing or duplicated");
        require(closeWrites >= 1, "native auto guard does not clear isShowing");
        System.out.println("nativeAutoMenuGuard=verified");
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static FieldNode findField(ClassNode node, String name, String desc) {
        for (FieldNode field : node.fields) {
            if (name.equals(field.name) && desc.equals(field.desc)) return field;
        }
        return null;
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        return null;
    }

    private static Properties load(File file) throws Exception {
        Properties properties = new Properties();
        InputStream in = new FileInputStream(file);
        try {
            properties.load(in);
        } finally {
            in.close();
        }
        return properties;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        require(value != null && value.length() > 0, "missing symbol: " + key);
        return value;
    }

    private static void readJar(File file, Map<String, byte[]> entries,
            Map<String, Long> times) throws Exception {
        JarFile jar = new JarFile(file);
        try {
            Enumeration<JarEntry> all = jar.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                if (entry.isDirectory()) continue;
                entries.put(entry.getName(), readAll(jar.getInputStream(entry)));
                times.put(entry.getName(), Long.valueOf(entry.getTime()));
            }
        } finally {
            jar.close();
        }
    }

    private static void writeJar(File file, Map<String, byte[]> entries,
            Map<String, Long> times) throws Exception {
        OutputStream out = new FileOutputStream(file);
        JarOutputStream jar = new JarOutputStream(out);
        try {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zip = new ZipEntry(entry.getKey());
                Long time = times.get(entry.getKey());
                if (time != null && time.longValue() >= 0) zip.setTime(time.longValue());
                jar.putNextEntry(zip);
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        } finally {
            jar.close();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        try {
            while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
        } finally {
            in.close();
        }
        return out.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
