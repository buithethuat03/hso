import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds automatic ghost captcha answering (GhostMod port) without any menu entry.
 * Hook: after the network handler stores the image for message id 9999, pass it to
 * HungBySonGhostSupport.onCaptcha. Bridges: the support class's placeholder methods are
 * rewritten to call the native ghost answer sender and to test the captcha image field.
 */
public final class HungBySonGhostPatcher implements Opcodes {
    private static final String SUPPORT_CLASS = "HungBySonGhostSupport";
    private static final String IMAGE_DESC = "Ljavax/microedition/lcdui/Image;";
    private static final int CAPTCHA_ID = 9999;
    private static final int ANSWER_OPCODE = -28;

    private static final class EntryData {
        final JarEntry source;
        byte[] bytes;
        EntryData(JarEntry source, byte[] bytes) { this.source = source; this.bytes = bytes; }
    }

    // Discovered symbols.
    private static String netClass;
    private static FieldInsnNode captchaField;   // static actor field holding the captcha image holder
    private static String imageField;            // Image field inside the holder
    private static MethodInsnNode senderGetter;  // static ()Lsender;
    private static MethodInsnNode answerSender;  // virtual (B)V, opcode -28
    private static String answerCaller;

    private HungBySonGhostPatcher() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("input.jar output.jar HungBySonGhostSupport.class");
        File input = new File(args[0]).getCanonicalFile();
        File output = new File(args[1]).getCanonicalFile();
        File supportFile = new File(args[2]).getCanonicalFile();
        if (!input.isFile() || !supportFile.isFile() || input.equals(output) || output.exists()) {
            throw new IllegalArgumentException("Input/support missing, input equals output, or output exists");
        }
        List<EntryData> entries = read(input);
        for (EntryData e : entries) {
            if (e.source.getName().equals(SUPPORT_CLASS + ".class")) throw new IllegalStateException("Already patched");
        }

        EntryData net = findCaptchaStore(entries);
        imageField = findImageField(entries, Type.getType(captchaField.desc).getInternalName());
        findAnswerSender(entries);
        verifySenderOpcode(entries);

        ClassNode netNode = parse(net.bytes);
        int hooks = injectCaptchaHook(netNode);
        if (hooks != 1) throw new IllegalStateException("Expected one captcha hook, got " + hooks);
        ClassWriter netWriter = new ClassWriter(0);
        netNode.accept(netWriter);
        net.bytes = netWriter.toByteArray();

        byte[] support = buildSupport(readAll(new FileInputStream(supportFile)));
        entries.add(new EntryData(new JarEntry(SUPPORT_CLASS + ".class"), support));

        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot make output directory");
        write(output, entries);
        verify(output, entries.size());

        System.out.println("NET_CLASS=" + netClass);
        System.out.println("CAPTCHA_FIELD=" + captchaField.owner + "." + captchaField.name + ":" + captchaField.desc);
        System.out.println("IMAGE_FIELD=" + imageField);
        System.out.println("SENDER=" + senderGetter.owner + "." + senderGetter.name + senderGetter.desc
                + " -> " + answerSender.name + answerSender.desc + " (opcode " + ANSWER_OPCODE + ")");
        System.out.println("ANSWER_CALLER=" + answerCaller);
        System.out.println("ENTRIES=" + entries.size());
    }

    /** Locates: readShort -> istore; iload; sipush 9999; if_icmpne; aload; invokestatic H.m([B)LH; putstatic A.f:LH; */
    private static EntryData findCaptchaStore(List<EntryData> entries) {
        EntryData found = null;
        for (EntryData entry : entries) {
            if (!entry.source.getName().endsWith(".class")) continue;
            ClassNode node = parse(entry.bytes);
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode i = method.instructions.getFirst(); i != null; i = i.getNext()) {
                    FieldInsnNode store = matchCaptchaStore(i);
                    if (store == null) continue;
                    if (found != null) throw new IllegalStateException("Multiple captcha stores");
                    found = entry;
                    netClass = node.name;
                    captchaField = store;
                }
            }
        }
        if (found == null) throw new IllegalStateException("Captcha image store (id 9999) not found");
        return found;
    }

    private static FieldInsnNode matchCaptchaStore(AbstractInsnNode i) {
        if (!(i instanceof IntInsnNode) || i.getOpcode() != SIPUSH || ((IntInsnNode) i).operand != CAPTCHA_ID) return null;
        AbstractInsnNode branch = next(i);
        AbstractInsnNode load = next(branch);
        AbstractInsnNode decode = next(load);
        AbstractInsnNode store = next(decode);
        if (branch == null || branch.getOpcode() != IF_ICMPNE || load == null || load.getOpcode() != ALOAD
                || !(decode instanceof MethodInsnNode) || decode.getOpcode() != INVOKESTATIC
                || !(store instanceof FieldInsnNode) || store.getOpcode() != PUTSTATIC) return null;
        MethodInsnNode m = (MethodInsnNode) decode;
        FieldInsnNode f = (FieldInsnNode) store;
        String holder = "L" + m.owner + ";";
        if (!m.desc.equals("([B)" + holder) || !f.desc.equals(holder)) return null;
        return f;
    }

    private static String findImageField(List<EntryData> entries, String holder) {
        ClassNode node = parse(entry(entries, holder + ".class").bytes);
        String result = null;
        for (FieldNode field : node.fields) {
            if ((field.access & ACC_STATIC) == 0 && IMAGE_DESC.equals(field.desc)) {
                if (result != null) throw new IllegalStateException("Multiple Image fields in holder");
                result = field.name;
            }
        }
        if (result == null) throw new IllegalStateException("Holder Image field not found");
        return result;
    }

    /**
     * The native skill-key path answers a focused ghost:
     *   getstatic focus; getfield cy:B; iconst_2; if_icmpne ...; invokestatic S.get()LS; iload; i2b; invokevirtual S.m(B)V
     */
    private static void findAnswerSender(List<EntryData> entries) {
        String actor = captchaField.owner;
        for (EntryData entry : entries) {
            if (!entry.source.getName().endsWith(".class")) continue;
            ClassNode node = parse(entry.bytes);
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode i = method.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (!(i instanceof MethodInsnNode) || i.getOpcode() != INVOKEVIRTUAL || !"(B)V".equals(((MethodInsnNode) i).desc)) continue;
                    AbstractInsnNode cast = previous(i);
                    AbstractInsnNode index = previous(cast);
                    AbstractInsnNode getter = previous(index);
                    if (cast == null || cast.getOpcode() != I2B || !(index instanceof VarInsnNode) || index.getOpcode() != ILOAD
                            || !(getter instanceof MethodInsnNode) || getter.getOpcode() != INVOKESTATIC) continue;
                    MethodInsnNode get = (MethodInsnNode) getter;
                    MethodInsnNode send = (MethodInsnNode) i;
                    if (!get.owner.equals(send.owner) || !get.desc.equals("()L" + send.owner + ";")) continue;
                    if (!guardedByGhostState(getter, actor)) continue;
                    if (answerSender != null && !(answerSender.owner.equals(send.owner) && answerSender.name.equals(send.name))) {
                        throw new IllegalStateException("Ambiguous ghost answer sender");
                    }
                    senderGetter = get;
                    answerSender = send;
                    answerCaller = node.name + "." + method.name + method.desc;
                }
            }
        }
        if (answerSender == null) throw new IllegalStateException("Ghost answer sender not found");
    }

    private static boolean guardedByGhostState(AbstractInsnNode from, String actor) {
        AbstractInsnNode i = from;
        for (int n = 0; n < 16 && i != null; n++) {
            i = previous(i);
            if (i instanceof FieldInsnNode && i.getOpcode() == GETFIELD && actor.equals(((FieldInsnNode) i).owner)
                    && "B".equals(((FieldInsnNode) i).desc)) {
                AbstractInsnNode two = next(i);
                AbstractInsnNode cmp = next(two);
                if (two != null && two.getOpcode() == ICONST_2 && cmp != null && cmp.getOpcode() == IF_ICMPNE) return true;
            }
        }
        return false;
    }

    private static void verifySenderOpcode(List<EntryData> entries) {
        ClassNode node = parse(entry(entries, answerSender.owner + ".class").bytes);
        MethodNode method = null;
        for (MethodNode m : node.methods) {
            if (m.name.equals(answerSender.name) && m.desc.equals(answerSender.desc)) method = m;
        }
        if (method == null) throw new IllegalStateException("Answer sender body missing");
        AbstractInsnNode self = method.instructions.getFirst();
        while (self != null && self.getOpcode() < 0) self = self.getNext();
        AbstractInsnNode op = next(self);
        if (self == null || self.getOpcode() != ALOAD || !(op instanceof IntInsnNode) || op.getOpcode() != BIPUSH
                || ((IntInsnNode) op).operand != ANSWER_OPCODE) {
            throw new IllegalStateException("Answer sender does not emit opcode " + ANSWER_OPCODE);
        }
    }

    private static int injectCaptchaHook(ClassNode node) {
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode i = method.instructions.getFirst(); i != null; i = i.getNext()) {
                FieldInsnNode store = matchCaptchaStore(i);
                if (store == null) continue;
                InsnList hook = new InsnList();
                hook.add(new FieldInsnNode(GETSTATIC, store.owner, store.name, store.desc));
                hook.add(new FieldInsnNode(GETFIELD, Type.getType(store.desc).getInternalName(), imageField, IMAGE_DESC));
                hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT_CLASS, "onCaptcha", "(" + IMAGE_DESC + ")V", false));
                method.instructions.insert(store, hook);
                method.maxStack = Math.max(method.maxStack, 1);
                count++;
            }
        }
        return count;
    }

    private static byte[] buildSupport(byte[] compiled) {
        ClassNode node = new ClassNode(ASM9);
        new ClassReader(compiled).accept(node, ClassReader.SKIP_FRAMES);
        if (!SUPPORT_CLASS.equals(node.name)) throw new IllegalStateException("Unexpected support class " + node.name);
        int bridged = 0;
        for (MethodNode method : node.methods) {
            if ("sendAnswer".equals(method.name) && "(I)V".equals(method.desc)) {
                InsnList body = new InsnList();
                body.add(new MethodInsnNode(INVOKESTATIC, senderGetter.owner, senderGetter.name, senderGetter.desc, false));
                body.add(new VarInsnNode(ILOAD, 0));
                body.add(new InsnNode(I2B));
                body.add(new MethodInsnNode(INVOKEVIRTUAL, answerSender.owner, answerSender.name, answerSender.desc, false));
                body.add(new InsnNode(RETURN));
                replaceBody(method, body, 2, 1);
                bridged++;
            } else if ("captchaPending".equals(method.name) && "()Z".equals(method.desc)) {
                LabelNode none = new LabelNode();
                InsnList body = new InsnList();
                body.add(new FieldInsnNode(GETSTATIC, captchaField.owner, captchaField.name, captchaField.desc));
                body.add(new JumpInsnNode(IFNULL, none));
                body.add(new InsnNode(ICONST_1));
                body.add(new InsnNode(IRETURN));
                body.add(none);
                body.add(new InsnNode(ICONST_0));
                body.add(new InsnNode(IRETURN));
                replaceBody(method, body, 1, 0);
                bridged++;
            }
        }
        if (bridged != 2) throw new IllegalStateException("Support bridge placeholders missing");
        node.version = V1_4;
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void replaceBody(MethodNode method, InsnList body, int maxStack, int maxLocals) {
        method.instructions.clear();
        method.instructions.add(body);
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
        method.maxStack = maxStack;
        method.maxLocals = maxLocals;
    }

    private static void verify(File output, int expectedEntries) throws IOException {
        JarFile jar = new JarFile(output);
        try {
            if (jar.size() != expectedEntries) throw new IllegalStateException("Entry count mismatch");
            JarEntry support = jar.getJarEntry(SUPPORT_CLASS + ".class");
            if (support == null) throw new IllegalStateException("Support class missing");
            byte[] supportBytes = readAll(jar.getInputStream(support));
            if (new ClassReader(supportBytes).readUnsignedShort(6) != V1_4) throw new IllegalStateException("Support class version");
            ClassNode supportNode = parse(supportBytes);
            boolean sends = false, reads = false;
            for (MethodNode m : supportNode.methods) {
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i instanceof MethodInsnNode && ((MethodInsnNode) i).owner.equals(answerSender.owner)
                            && ((MethodInsnNode) i).name.equals(answerSender.name)) sends = true;
                    if (i instanceof FieldInsnNode && ((FieldInsnNode) i).name.equals(captchaField.name)
                            && ((FieldInsnNode) i).owner.equals(captchaField.owner)) reads = true;
                }
            }
            if (!sends || !reads) throw new IllegalStateException("Support bridges not wired");

            ClassNode net = parse(readAll(jar.getInputStream(jar.getJarEntry(netClass + ".class"))));
            int hooks = 0;
            for (MethodNode m : net.methods) {
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (matchCaptchaStore(i) == null) continue;
                    AbstractInsnNode store = next(next(next(next(i))));
                    AbstractInsnNode get = next(store);
                    AbstractInsnNode img = next(get);
                    AbstractInsnNode call = next(img);
                    if (get != null && get.getOpcode() == GETSTATIC && img != null && img.getOpcode() == GETFIELD
                            && call instanceof MethodInsnNode && SUPPORT_CLASS.equals(((MethodInsnNode) call).owner)
                            && "onCaptcha".equals(((MethodInsnNode) call).name)) hooks++;
                }
            }
            if (hooks != 1) throw new IllegalStateException("Captcha hook verification failed");
        } finally { jar.close(); }
    }

    private static EntryData entry(List<EntryData> entries, String name) {
        for (EntryData e : entries) if (e.source.getName().equals(name)) return e;
        throw new IllegalStateException("Missing entry " + name);
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
    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] block = new byte[8192];
            for (int count; (count = in.read(block)) >= 0;) out.write(block, 0, count);
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
}
