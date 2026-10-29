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
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds chat commands for changing zone: "k1".."kN", plus "kkN" and "khelp".
 *
 * Why: the "Chuyen khu vuc" soft command in this client is id 12 and the game
 * screen has no case 12, so pressing it sends nothing at all. The working path is
 * the server-pushed zone popup (opcode 54) whose rows carry command id 13, and
 * case 13 sends opcode 51 with one byte = the row index. This patch reaches that
 * same request straight from the chat box, so the popup is no longer required.
 *
 * Discovery anchors, all structural:
 *   - zone request : the only (B)V method whose body opens a frame with 51
 *   - service      : the static no-arg accessor of the class that owns it
 *   - toast        : the static (String)V the mod menu ("modMenu") uses for notices
 *   - chat sender  : the method already carrying the HSOSKYPetFeedSupport chat hook
 *
 * Usage: java -cp ".;asm-9.7.jar;asm-tree-9.7.jar" HSOSKYZoneChatPatcher [in.jar] [out.jar]
 */
public final class HSOSKYZoneChatPatcher implements Opcodes {
    private static final String DEFAULT_INPUT =
            "HSOSKY_MOD_AUTO_ENHANCE_GOLD_SERVER_GATED_NOFREEZE_V8.jar";
    private static final String DEFAULT_OUTPUT =
            "HSOSKY_MOD_AUTO_ENHANCE_GOLD_ZONECHAT_V9.jar";

    private static final String SUPPORT = "HSOSKYZoneChatSupport";
    private static final String SUPPORT_ENTRY = SUPPORT + ".class";
    private static final String EXISTING_CHAT_HOOK = "HSOSKYPetFeedSupport";
    private static final String MOD_MENU_MARKER = "modMenu";
    private static final int ZONE_OPCODE = 51;

    private static final Map<String, byte[]> ENTRIES = new LinkedHashMap<String, byte[]>();
    private static final Map<String, Long> TIMES = new LinkedHashMap<String, Long>();

    private static String serviceOwner;
    private static String zoneMethod;
    private static String serviceGetter;
    private static String toastOwner;
    private static String toastMethod;
    private static String chatOwner;
    private static String chatName;
    private static String chatDesc;

    private HSOSKYZoneChatPatcher() {
    }

    public static void main(String[] args) throws Exception {
        File input = new File(args.length > 0 ? args[0] : DEFAULT_INPUT);
        File output = new File(args.length > 1 ? args[1] : DEFAULT_OUTPUT);
        require(!output.exists(), "Refusing to overwrite existing output: " + output.getPath());
        require(new File(SUPPORT_ENTRY).isFile(), "Compile " + SUPPORT_ENTRY + " first");

        readJar(input);
        discoverZoneRequest();
        discoverToast();
        discoverChatSender();
        patchChatSender();
        injectSupport();
        writeJar(output);
        verifyOutput(output);

        System.out.println("[+] Built " + output.getPath() + " from " + input.getPath());
        System.out.println("    zone request : " + shortName(serviceOwner) + "." + zoneMethod
                + "(B) -> opcode " + ZONE_OPCODE);
        System.out.println("    service      : " + serviceGetter + "()");
        System.out.println("    toast        : " + shortName(toastOwner) + "." + toastMethod
                + "(String)");
        System.out.println("    chat sender  : " + shortName(chatOwner) + "." + chatName + chatDesc);
        System.out.println();
        System.out.println("    Trong game go vao khung chat:");
        System.out.println("      k1 .. kN : doi sang khu N (nhu bam dong thu N cua bang Khu vuc)");
        System.out.println("      kkN      : gui thang so N (neu server dung id khu, khong dung so dong)");
        System.out.println("      khelp    : hien huong dan");
    }

    // ------------------------------------------------------------------ discovery

    /**
     * Every request method in the service class opens with a call that writes the
     * opcode, so the zone request is the only single-byte method in the JAR that
     * opens one with the constant 51.
     */
    private static void discoverZoneRequest() {
        List<String> found = new ArrayList<String>();
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode owner = readClass(entry.getValue());
            for (MethodNode method : owner.methods) {
                if (!"(B)V".equals(method.desc) || (method.access & ACC_STATIC) != 0) {
                    continue;
                }
                if (!opensFrameWith(owner.name, method, ZONE_OPCODE)) {
                    continue;
                }
                found.add(owner.name + " " + method.name);
                serviceOwner = owner.name;
                zoneMethod = method.name;
            }
        }
        require(found.size() == 1,
                "Expected one opcode " + ZONE_OPCODE + " sender, found " + found.size());

        ClassNode service = readClass(ENTRIES.get(serviceOwner + ".class"));
        List<String> getters = new ArrayList<String>();
        for (MethodNode method : service.methods) {
            if ((method.access & ACC_STATIC) == 0
                    || !("()L" + serviceOwner + ";").equals(method.desc)) {
                continue;
            }
            getters.add(method.name);
            // Several accessors delegate to each other; take the one that owns the
            // singleton, so the instance exists even on the first call.
            if (serviceGetter == null || instantiates(service.name, method)) {
                serviceGetter = method.name;
            }
        }
        require(!getters.isEmpty(), "Could not locate the service accessor");
    }

    private static boolean instantiates(String owner, MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (insn.getOpcode() == NEW
                    && owner.equals(((org.objectweb.asm.tree.TypeInsnNode) insn).desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean opensFrameWith(String owner, MethodNode method, int opcodeValue) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) insn;
            if (!owner.equals(call.owner) || !"(B)V".equals(call.desc)) {
                continue;
            }
            AbstractInsnNode constant = previousReal(call);
            if (constant instanceof IntInsnNode && ((IntInsnNode) constant).operand == opcodeValue) {
                return true;
            }
        }
        return false;
    }

    /** The mod menu prints every notice through one static (String)V helper. */
    private static void discoverToast() {
        List<ClassNode> menus = new ArrayList<ClassNode>();
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode owner = readClass(entry.getValue());
            if (hasLdc(owner, MOD_MENU_MARKER)) {
                menus.add(owner);
            }
        }
        require(!menus.isEmpty(), "Could not locate the mod menu class");

        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (ClassNode menu : menus) {
            for (MethodNode method : menu.methods) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                        insn = insn.getNext()) {
                    if (insn.getOpcode() != INVOKESTATIC) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (!"(Ljava/lang/String;)V".equals(call.desc)
                            || call.owner.equals(menu.name)) {
                        continue;
                    }
                    String key = call.owner + " " + call.name;
                    Integer seen = counts.get(key);
                    counts.put(key, Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
                }
            }
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> candidate : counts.entrySet()) {
            if (candidate.getValue().intValue() > bestCount) {
                bestCount = candidate.getValue().intValue();
                best = candidate.getKey();
            }
        }
        require(best != null, "Could not locate the in-game notice helper");
        toastOwner = best.substring(0, best.indexOf(' '));
        toastMethod = best.substring(best.indexOf(' ') + 1);
    }

    /** The chat sender is the method that already carries the pet-feed chat hook. */
    private static void discoverChatSender() {
        List<String> found = new ArrayList<String>();
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode owner = readClass(entry.getValue());
            for (MethodNode method : owner.methods) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                        insn = insn.getNext()) {
                    if (insn.getOpcode() != INVOKESTATIC) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (EXISTING_CHAT_HOOK.equals(call.owner) && "handleChat".equals(call.name)) {
                        found.add(owner.name + " " + method.name + method.desc);
                        chatOwner = owner.name;
                        chatName = method.name;
                        chatDesc = method.desc;
                    }
                }
            }
        }
        require(found.size() == 1, "Expected one existing chat hook, found " + found.size());
        require("(Ljava/lang/String;)V".equals(chatDesc),
                "Unexpected chat sender signature: " + chatDesc);
    }

    // ------------------------------------------------------------------- patching

    /** Consume the message before the client sends it on as chat. */
    private static void patchChatSender() {
        ClassNode owner = readClass(ENTRIES.get(chatOwner + ".class"));
        MethodNode chat = null;
        for (MethodNode method : owner.methods) {
            if (chatName.equals(method.name) && chatDesc.equals(method.desc)) {
                chat = method;
            }
        }
        require(chat != null, "Chat sender disappeared before patching");
        require(!callsSupport(chat), "Chat sender already carries the zone hook");

        int messageSlot = (chat.access & ACC_STATIC) != 0 ? 0 : 1;
        LabelNode carryOn = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, messageSlot));
        hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "handleChat",
                "(Ljava/lang/String;)Z", false));
        hook.add(new JumpInsnNode(IFEQ, carryOn));
        hook.add(new InsnNode(RETURN));
        hook.add(carryOn);
        chat.instructions.insert(hook);

        ClassWriter writer = new ClassWriter(0);
        owner.accept(writer);
        ENTRIES.put(chatOwner + ".class", writer.toByteArray());
    }

    /** Bake the discovered names into the support class, which then uses reflection. */
    private static void injectSupport() throws IOException {
        byte[] compiled = Files.readAllBytes(new File(SUPPORT_ENTRY).toPath());
        ClassNode support = readClass(compiled);
        Map<String, String> names = new LinkedHashMap<String, String>();
        names.put("@SERVICE_CLASS@", serviceOwner.replace('/', '.'));
        names.put("@SERVICE_GETTER@", serviceGetter);
        names.put("@SERVICE_ZONE@", zoneMethod);
        names.put("@TOAST_CLASS@", toastOwner.replace('/', '.'));
        names.put("@TOAST_METHOD@", toastMethod);

        int replaced = 0;
        for (MethodNode method : support.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                    insn = insn.getNext()) {
                if (!(insn instanceof LdcInsnNode)) {
                    continue;
                }
                LdcInsnNode ldc = (LdcInsnNode) insn;
                String value = names.get(ldc.cst);
                if (value != null) {
                    ldc.cst = value;
                    replaced++;
                }
            }
        }
        require(replaced == names.size(),
                "Replaced " + replaced + " placeholders, expected " + names.size());

        ClassWriter writer = new ClassWriter(0);
        support.accept(writer);
        ENTRIES.put(SUPPORT_ENTRY, writer.toByteArray());
        TIMES.put(SUPPORT_ENTRY, Long.valueOf(System.currentTimeMillis()));
    }

    // ---------------------------------------------------------------- verification

    private static void verifyOutput(File output) throws Exception {
        JarFile jar = new JarFile(output);
        try {
            JarEntry chatEntry = jar.getJarEntry(chatOwner + ".class");
            require(chatEntry != null, "Chat sender missing from the output");
            ClassNode owner = readClass(readAll(jar.getInputStream(chatEntry)));
            int hooks = 0;
            for (MethodNode method : owner.methods) {
                if (chatName.equals(method.name) && chatDesc.equals(method.desc)
                        && callsSupport(method)) {
                    hooks++;
                }
            }
            require(hooks == 1, "Output carries " + hooks + " zone chat hooks");

            JarEntry supportEntry = jar.getJarEntry(SUPPORT_ENTRY);
            require(supportEntry != null, "Support class missing from the output");
            ClassNode support = readClass(readAll(jar.getInputStream(supportEntry)));
            for (MethodNode method : support.methods) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                        insn = insn.getNext()) {
                    if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String
                            && ((String) ((LdcInsnNode) insn).cst).startsWith("@")) {
                        require(false, "Placeholder left in the support class: "
                                + ((LdcInsnNode) insn).cst);
                    }
                }
            }
        } finally {
            jar.close();
        }
    }

    private static boolean callsSupport(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (insn.getOpcode() == INVOKESTATIC
                    && SUPPORT.equals(((MethodInsnNode) insn).owner)
                    && "handleChat".equals(((MethodInsnNode) insn).name)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------- plumbing

    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        AbstractInsnNode previous = insn.getPrevious();
        while (previous instanceof LabelNode || previous instanceof LineNumberNode
                || previous instanceof FrameNode) {
            previous = previous.getPrevious();
        }
        return previous;
    }

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

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
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

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
        }
        in.close();
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
