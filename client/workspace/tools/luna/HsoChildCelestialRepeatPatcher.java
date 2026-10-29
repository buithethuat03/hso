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
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Adds server-backed repeat crafting for HSO CHILD celestial equipment. */
public final class HsoChildCelestialRepeatPatcher implements Opcodes {
    private static final String INPUT_SHA256 =
            "B94AF1CC26C08870BAD01DDBFAA8036CDB79A4D5055B972172CDB1266592B671";
    private static final String SUPPORT = "HsoChildCelestialRepeatSupport";
    private static final String FACADE = "HSOAutoTradeSupport";
    private static final String SENDER =
            "IIIllIIlllIlIIIllIllIIIlllIIlllIIlllIlIlIIlIIIllIIllIlllIIlllIIlIlllIIlIIlIIllIIlIIIlllIllIIlllIIlIllIIIllIIlIIlIIlIIllIIlIIlIIIllIIllIIlIIlIIlllIIlllIIIllIlIIllIllIIlllIlIIIlllIIllIIlllIIllIlllIIllIIIllIIllIIllIIIlllIlIIlllIIllIIllIIlll";
    private static final String NPC_MENU_SELECT =
            "IIllIllIIllIlIIlIlIIlllIllIIllIIllIlIIllIIIllIIIllIIllIIIlIllIIlIIlllIllIIlIllIllIIlIIlIlIIIlIllIIllIlllIllIIIllIIlIIlllIllIIlIIIlIIllIIllIIlIlIIlIllIIlllIlIIlllIIlIlIIlIIlIlllIIlIIIlIIllIllIlllIIlllIIIlIIllIIIlll";
    private static final String NPC_MENU_REQUEST =
            "IIllIIlIllIIlIlllIIllIIIllIIIllIlIIlIIIllIIlllIIllIllIlIIlllIIlllIlIlIIlllIIlllIIIllIIllIIlIIIllIIIlllIIIllIllIIIlllIIllIllIlllIIIllIIIllIIlIlIIllIIlIIIlllIIllIIllIllIlllIIIlIIlIIIlIIllIlIIllIlllIlIIIllIIIlIIIllIIIlIlIllIIllIlIll";

    private static final class EntryData {
        final byte[] bytes;
        final long time;

        EntryData(byte[] bytes, long time) {
            this.bytes = bytes;
            this.time = time;
        }
    }

    private static final class CraftSite {
        final ClassNode owner;
        final MethodNode method;
        final AbstractInsnNode resultAnchor;
        final String selectedField;
        final String materialField;

        CraftSite(ClassNode owner, MethodNode method, AbstractInsnNode resultAnchor,
                String selectedField, String materialField) {
            this.owner = owner;
            this.method = method;
            this.resultAnchor = resultAnchor;
            this.selectedField = selectedField;
            this.materialField = materialField;
        }
    }

    private HsoChildCelestialRepeatPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: patcher <input.jar> <support.class> <output.jar>");
        }
        File input = new File(args[0]);
        File supportFile = new File(args[1]);
        File output = new File(args[2]);
        require(input.isFile(), "Input JAR does not exist: " + input);
        require(supportFile.isFile(), "Support class does not exist: " + supportFile);
        require(!input.getCanonicalFile().equals(output.getCanonicalFile()),
                "Output must use a new filename");
        require(!output.exists(), "Refusing to overwrite output: " + output);
        require(INPUT_SHA256.equals(sha256(input)), "Unexpected input JAR SHA-256");

        LinkedHashMap<String, EntryData> entries = readZip(input);
        require(!entries.containsKey(SUPPORT + ".class"), "Support class already exists");

        LinkedHashMap<String, ClassNode> classes = new LinkedHashMap<String, ClassNode>();
        LinkedHashMap<String, String> classEntries = new LinkedHashMap<String, String>();
        for (Map.Entry<String, EntryData> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode node = readClass(entry.getValue().bytes);
            classes.put(node.name, node);
            classEntries.put(node.name, entry.getKey());
        }

        ClassNode support = readClass(readAll(new FileInputStream(supportFile)));
        require(SUPPORT.equals(support.name), "Unexpected support class name");
        require(support.version <= V1_7, "Support class is newer than Java 7");

        ClassNode screen = findScreen(classes);
        String modeField = findModeField(screen);
        MethodNode action = findActionMethod(screen);
        MethodNode tick = requireMethod(screen, "hsoskyAutoTickGate", "()V");
        MethodNode constructor = requireMethod(screen, "<init>", "(Ljava/lang/String;B)V");

        ClassNode sender = requireClass(classes, SENDER);
        MethodNode menuRequest = requireMethod(sender, NPC_MENU_REQUEST, "(B)V");
        MethodNode menuSelect = requireMethod(sender, NPC_MENU_SELECT, "(SBB)V");
        ClassNode facade = requireClass(classes, FACADE);
        MethodNode onNpcMenu = findFacadeMenu(facade);
        CraftSite craft = findCraftResult(classes, screen.name);

        patchNpcRequest(menuRequest);
        patchMenuSender(menuSelect);
        patchCraftConstructor(constructor, screen.name, modeField);
        patchAction(action, screen.name, modeField);
        patchTick(tick);
        patchFacade(onNpcMenu);
        craft.method.instructions.insert(craft.resultAnchor,
                call(SUPPORT, "onCraftResult", "()V"));

        verifyHooks(screen, sender, facade, craft);

        replaceClass(entries, classEntries, screen, false);
        replaceClass(entries, classEntries, sender, false);
        replaceClass(entries, classEntries, facade, true);
        if (craft.owner != screen && craft.owner != sender && craft.owner != facade) {
            replaceClass(entries, classEntries, craft.owner, false);
        }
        entries.put(SUPPORT + ".class",
                new EntryData(readAll(new FileInputStream(supportFile)), input.lastModified()));

        File parent = output.getCanonicalFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create output directory: " + parent);
        }
        writeZip(output, entries);
        verifyOutput(input, output, screen.name, craft.owner.name);

        System.out.println("input.sha256=" + INPUT_SHA256);
        System.out.println("output.sha256=" + sha256(output));
        System.out.println("screen.class=" + screen.name);
        System.out.println("result.class=" + craft.owner.name);
        System.out.println("selected.field=" + craft.selectedField);
        System.out.println("materials.field=" + craft.materialField);
        System.out.println("repeat.flow=opcode 23 NPC open + full menu chain -> server UI type 19 refresh");
    }

    private static ClassNode findScreen(Map<String, ClassNode> classes) {
        ClassNode found = null;
        for (ClassNode node : classes.values()) {
            if (findMethod(node, "hsoskyAutoTickGate", "()V") == null) continue;
            require(found == null, "Multiple enhancement/crafting screens found");
            found = node;
        }
        require(found != null, "Enhancement/crafting screen not found");
        return found;
    }

    private static String findModeField(ClassNode screen) {
        MethodNode constructor = requireMethod(screen, "<init>", "(Ljava/lang/String;B)V");
        String found = null;
        for (AbstractInsnNode insn = constructor.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != PUTFIELD) continue;
            FieldInsnNode field = (FieldInsnNode) insn;
            if (!screen.name.equals(field.owner) || !"B".equals(field.desc)) continue;
            AbstractInsnNode value = previousReal(insn);
            AbstractInsnNode target = previousReal(value);
            if (value != null && value.getOpcode() == ILOAD
                    && ((VarInsnNode) value).var == 2
                    && target != null && target.getOpcode() == ALOAD
                    && ((VarInsnNode) target).var == 0) {
                require(found == null || found.equals(field.name), "Ambiguous screen mode field");
                found = field.name;
            }
        }
        require(found != null, "Screen mode field not found");
        return found;
    }

    private static MethodNode findActionMethod(ClassNode screen) {
        MethodNode found = null;
        for (MethodNode method : screen.methods) {
            if (!"(II)V".equals(method.desc)) continue;
            if (callCount(method, screen.name, "hsoskyAutoHandle", "(II)Z") != 1) continue;
            require(found == null, "Multiple screen action methods found");
            found = method;
        }
        require(found != null, "Screen action method not found");
        return found;
    }

    private static MethodNode findFacadeMenu(ClassNode facade) {
        MethodNode found = null;
        for (MethodNode method : facade.methods) {
            if (!"onNpcMenu".equals(method.name) || !method.desc.startsWith("(SB")
                    || !method.desc.endsWith(")Z")) continue;
            require(found == null, "Multiple facade onNpcMenu methods found");
            found = method;
        }
        require(found != null, "Facade onNpcMenu method not found");
        return found;
    }

    private static CraftSite findCraftResult(Map<String, ClassNode> classes, String screen) {
        CraftSite found = null;
        for (ClassNode owner : classes.values()) {
            for (MethodNode method : owner.methods) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                        insn = insn.getNext()) {
                    if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != PUTSTATIC) continue;
                    FieldInsnNode selected = (FieldInsnNode) insn;
                    if (!screen.equals(selected.owner) || !selected.desc.startsWith("L")) continue;
                    AbstractInsnNode before = previousReal(insn);
                    if (!(before instanceof MethodInsnNode)
                            || before.getOpcode() != INVOKESPECIAL) continue;
                    MethodInsnNode constructor = (MethodInsnNode) before;
                    if (!"<init>".equals(constructor.name)
                            || !selected.desc.equals("L" + constructor.owner + ";")
                            || Type.getArgumentTypes(constructor.desc).length < 10) continue;

                    FieldInsnNode materials = null;
                    AbstractInsnNode cursor = previousReal(before);
                    for (int distance = 0; cursor != null && distance < 300;
                            cursor = previousReal(cursor), ++distance) {
                        if (!(cursor instanceof MethodInsnNode)) continue;
                        MethodInsnNode call = (MethodInsnNode) cursor;
                        if (!"removeAllElements".equals(call.name)
                                || !"()V".equals(call.desc)) continue;
                        AbstractInsnNode prior = previousReal(cursor);
                        for (int back = 0; prior != null && back < 8;
                                prior = previousReal(prior), ++back) {
                            if (!(prior instanceof FieldInsnNode)
                                    || prior.getOpcode() != GETSTATIC) continue;
                            FieldInsnNode candidate = (FieldInsnNode) prior;
                            if (screen.equals(candidate.owner)
                                    && candidate.desc.startsWith("L")) {
                                materials = candidate;
                                break;
                            }
                        }
                        if (materials != null) break;
                    }
                    require(materials != null, "Material clear not found before crafted item");
                    require(found == null, "Multiple crafted-item assignments found");
                    found = new CraftSite(owner, method, insn,
                            selected.name, materials.name);
                }
            }
        }
        require(found != null, "Crafted-item result assignment not found");
        return found;
    }

    private static void patchNpcRequest(MethodNode method) {
        require(callCount(method, SUPPORT, "observeNpcRequest", "(B)V") == 0,
                "NPC request sender already patched");
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ILOAD, 1));
        hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT,
                "observeNpcRequest", "(B)V", false));
        method.instructions.insert(hook);
    }

    private static void patchMenuSender(MethodNode method) {
        require(callCount(method, SUPPORT, "observeMenu", "(SBB)V") == 0,
                "Menu sender already patched");
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ILOAD, 1));
        hook.add(new VarInsnNode(ILOAD, 2));
        hook.add(new VarInsnNode(ILOAD, 3));
        hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "observeMenu", "(SBB)V", false));
        method.instructions.insert(hook);
    }

    private static void patchCraftConstructor(MethodNode method, String owner, String modeField) {
        int returns = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;) {
            AbstractInsnNode next = insn.getNext();
            if (insn.getOpcode() == RETURN) {
                ++returns;
                LabelNode skip = new LabelNode();
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(ALOAD, 0));
                hook.add(new FieldInsnNode(GETFIELD, owner, modeField, "B"));
                hook.add(new InsnNode(ICONST_3));
                hook.add(new JumpInsnNode(IF_ICMPNE, skip));
                hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT,
                        "onCraftScreenOpened", "()V", false));
                hook.add(skip);
                method.instructions.insertBefore(insn, hook);
            }
            insn = next;
        }
        require(returns == 2, "Unexpected crafting-screen constructor exits: " + returns);
    }

    private static void patchAction(MethodNode method, String owner, String modeField) {
        LabelNode notClose = new LabelNode();
        LabelNode proceed = new LabelNode();
        LabelNode checkNine = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ILOAD, 1));
        hook.add(new InsnNode(ICONST_M1));
        hook.add(new JumpInsnNode(IF_ICMPNE, notClose));
        hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "cancel", "()V", false));
        hook.add(notClose);
        hook.add(new VarInsnNode(ALOAD, 0));
        hook.add(new FieldInsnNode(GETFIELD, owner, modeField, "B"));
        hook.add(new InsnNode(ICONST_3));
        hook.add(new JumpInsnNode(IF_ICMPNE, proceed));
        hook.add(new VarInsnNode(ILOAD, 1));
        hook.add(new JumpInsnNode(IFNE, checkNine));
        hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isRearming", "()Z", false));
        hook.add(new JumpInsnNode(IFEQ, proceed));
        hook.add(new InsnNode(RETURN));
        hook.add(checkNine);
        hook.add(new VarInsnNode(ILOAD, 1));
        hook.add(new IntInsnNode(BIPUSH, 9));
        hook.add(new JumpInsnNode(IF_ICMPNE, proceed));
        hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isRearming", "()Z", false));
        hook.add(new JumpInsnNode(IFEQ, proceed));
        hook.add(new InsnNode(RETURN));
        hook.add(proceed);
        method.instructions.insert(hook);
    }

    private static void patchTick(MethodNode method) {
        require(callCount(method, SUPPORT, "tick", "()V") == 0, "Tick already patched");
        method.instructions.insert(call(SUPPORT, "tick", "()V"));
    }

    private static void patchFacade(MethodNode method) {
        LabelNode proceed = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ILOAD, 0));
        hook.add(new VarInsnNode(ILOAD, 1));
        hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "onNpcMenu", "(SB)Z", false));
        hook.add(new JumpInsnNode(IFEQ, proceed));
        hook.add(new InsnNode(ICONST_1));
        hook.add(new InsnNode(IRETURN));
        hook.add(proceed);
        method.instructions.insert(hook);
    }

    private static void verifyHooks(ClassNode screen, ClassNode sender,
            ClassNode facade, CraftSite craft) {
        require(callCount(requireMethod(sender, NPC_MENU_REQUEST, "(B)V"),
                SUPPORT, "observeNpcRequest", "(B)V") == 1,
                "NPC request observation hook mismatch");
        require(callCount(requireMethod(sender, NPC_MENU_SELECT, "(SBB)V"),
                SUPPORT, "observeMenu", "(SBB)V") == 1, "Menu observation hook mismatch");
        require(callCount(requireMethod(screen, "<init>", "(Ljava/lang/String;B)V"),
                SUPPORT, "onCraftScreenOpened", "()V") == 2, "Screen-open hook mismatch");
        MethodNode action = findActionMethod(screen);
        require(callCount(action, SUPPORT, "cancel", "()V") == 1,
                "Close cancellation hook mismatch");
        require(callCount(action, SUPPORT, "isRearming", "()Z") == 2,
                "Craft-action gate hook mismatch");
        require(callCount(requireMethod(screen, "hsoskyAutoTickGate", "()V"),
                SUPPORT, "tick", "()V") == 1, "Tick hook mismatch");
        require(callCount(findFacadeMenu(facade), SUPPORT, "onNpcMenu", "(SB)Z") == 1,
                "Incoming submenu hook mismatch");
        require(callCount(craft.method, SUPPORT, "onCraftResult", "()V") == 1,
                "Craft-result hook mismatch");
    }

    private static InsnList call(String owner, String name, String desc) {
        InsnList result = new InsnList();
        result.add(new MethodInsnNode(INVOKESTATIC, owner, name, desc, false));
        return result;
    }

    private static ClassNode requireClass(Map<String, ClassNode> classes, String name) {
        ClassNode node = classes.get(name);
        require(node != null, "Class not found: " + name);
        return node;
    }

    private static MethodNode requireMethod(ClassNode owner, String name, String desc) {
        MethodNode method = findMethod(owner, name, desc);
        require(method != null, "Method not found: " + owner.name + "." + name + desc);
        return method;
    }

    private static MethodNode findMethod(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        return null;
    }

    private static int callCount(MethodNode method, String owner, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) {
                ++count;
            }
        }
        return count;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        for (AbstractInsnNode cursor = insn == null ? null : insn.getPrevious(); cursor != null;
                cursor = cursor.getPrevious()) {
            if (cursor.getOpcode() >= 0) return cursor;
        }
        return null;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        for (AbstractInsnNode cursor = insn == null ? null : insn.getNext(); cursor != null;
                cursor = cursor.getNext()) {
            if (cursor.getOpcode() >= 0) return cursor;
        }
        return null;
    }

    private static void replaceClass(Map<String, EntryData> entries,
            Map<String, String> classEntries, ClassNode node, boolean frames) {
        String entry = classEntries.get(node.name);
        require(entry != null, "Missing class entry for " + node.name);
        EntryData original = entries.get(entry);
        entries.put(entry, new EntryData(writeClass(node, frames), original.time));
    }

    private static byte[] writeClass(ClassNode node, boolean frames) {
        ClassWriter writer = frames
                ? new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
                    protected String getCommonSuperClass(String left, String right) {
                        return "java/lang/Object";
                    }
                }
                : new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    private static LinkedHashMap<String, EntryData> readZip(File input) throws IOException {
        LinkedHashMap<String, EntryData> result = new LinkedHashMap<String, EntryData>();
        ZipFile zip = new ZipFile(input);
        try {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                result.put(entry.getName(), new EntryData(
                        readAll(zip.getInputStream(entry)), entry.getTime()));
            }
        } finally {
            zip.close();
        }
        return result;
    }

    private static void writeZip(File output, Map<String, EntryData> entries) throws IOException {
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

    private static void verifyOutput(File input, File output,
            String screen, String resultOwner) throws IOException {
        Map<String, EntryData> before = readZip(input);
        Map<String, EntryData> after = readZip(output);
        require(after.size() == before.size() + 1, "Unexpected output entry count");
        require(after.containsKey(SUPPORT + ".class"), "Support class missing from output");
        for (Map.Entry<String, EntryData> entry : before.entrySet()) {
            String name = entry.getKey();
            boolean changedClass = name.equals(screen + ".class")
                    || name.equals(SENDER + ".class")
                    || name.equals(FACADE + ".class")
                    || name.equals(resultOwner + ".class");
            require(after.containsKey(name), "Output lost entry: " + name);
            if (!changedClass) {
                require(java.util.Arrays.equals(entry.getValue().bytes, after.get(name).bytes),
                        "Unexpected changed entry: " + name);
            }
        }
        ZipFile zip = new ZipFile(output);
        try {
            Enumeration<? extends ZipEntry> items = zip.entries();
            while (items.hasMoreElements()) {
                ZipEntry item = items.nextElement();
                if (item.getName().endsWith(".class")) {
                    new ClassReader(readAll(zip.getInputStream(item)));
                }
            }
        } finally {
            zip.close();
        }
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally {
            stream.close();
        }
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
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) {
            hex.append(String.format("%02X", Integer.valueOf(value & 0xFF)));
        }
        return hex.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
