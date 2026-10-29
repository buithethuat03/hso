import java.io.ByteArrayOutputStream;
import java.io.File;
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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * MOD HungDZ menu: the main entry becomes a plain "Auto Farm >" that opens the farm detail menu
 * (the former "Cài Đặt Cấu Hình" screen, retitled AUTO FARM) with an ON/OFF row at the top.
 * The separate "Cài Đặt Cấu Hình" main-menu entry is removed because the same screen is now under Auto Farm.
 */
public final class HungBySonAutoFarmMenuPatcher implements Opcodes {
    private static final int FARM_ENTRY_ID = -40;   // main menu "Auto Farm" item
    private static final int CONFIG_MENU_ID = -39;  // opens the farm detail menu
    private static final int TOGGLE_ID = -49;       // new: ON/OFF row inside the detail menu
    private static final String MAIN_LABEL = "Auto Farm >";
    private static final String DETAIL_TITLE = "AUTO FARM";
    private static final String OLD_DETAIL_TITLE = "CÀI ĐẶT CấU HÌNH";
    private static final String TOGGLE_ON = "Auto Farm: [BẬT]";
    private static final String TOGGLE_OFF = "Auto Farm: [TẮT]";
    private static final String NOTICE_ON = "Auto Farm: Đã BẬT";
    private static final String NOTICE_OFF = "Auto Farm: Đã TẮT";

    private static final class EntryData {
        final JarEntry source;
        byte[] bytes;
        EntryData(JarEntry source, byte[] bytes) { this.source = source; this.bytes = bytes; }
    }

    // Discovered in the farm module's onAction(-40) branch.
    private static FieldInsnNode farmFlag;
    private static MethodInsnNode applyState;
    private static MethodInsnNode saveConfig;
    private static MethodInsnNode mainMenu;
    private static MethodInsnNode configMenu;
    private static MethodInsnNode notice;

    private HungBySonAutoFarmMenuPatcher() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.jar output.jar");
        File input = new File(args[0]).getCanonicalFile();
        File output = new File(args[1]).getCanonicalFile();
        if (!input.isFile() || input.equals(output) || output.exists()) {
            throw new IllegalArgumentException("Input missing, equal to output, or output exists");
        }
        List<EntryData> entries = read(input);

        EntryData moduleEntry = null;
        ClassNode module = null;
        MethodNode onAction = null;
        for (EntryData e : entries) {
            if (!e.source.getName().endsWith(".class")) continue;
            ClassNode node = parse(e.bytes);
            if (!node.interfaces.contains("IModModule")) continue;
            MethodNode m = method(node, "onAction", "(II)Z");
            if (m != null && branch(m, FARM_ENTRY_ID) != null) {
                if (module != null) throw new IllegalStateException("Multiple Auto Farm modules");
                moduleEntry = e; module = node; onAction = m;
            }
        }
        if (module == null) throw new IllegalStateException("Auto Farm module not found");
        if (method(module, "onAction", "(II)Z") == null) throw new IllegalStateException("onAction missing");
        for (MethodNode m : module.methods) {
            // The patcher runs once; the toggle id must still be free.
            if (m == onAction && branch(m, TOGGLE_ID) != null) throw new IllegalStateException("Already patched");
        }

        discover(module, onAction);
        MethodNode config = method(module, configMenu.name, configMenu.desc);
        if (config == null) throw new IllegalStateException("Config menu builder missing");

        rewriteFarmEntryBranch(onAction);
        addToggleBranch(onAction);
        int retitled = retitle(config);
        if (retitled != 2) throw new IllegalStateException("Expected two config titles, got " + retitled);
        addToggleRow(config);
        removeConfigEntry(module);
        moduleEntry.bytes = write(module);

        EntryData mainEntry = entry(entries, mainMenu.owner + ".class");
        ClassNode mainNode = parse(mainEntry.bytes);
        MethodNode builder = method(mainNode, mainMenu.name, mainMenu.desc);
        if (builder == null) throw new IllegalStateException("Main menu builder missing");
        if (relabelMainEntry(builder) != 1) throw new IllegalStateException("Main Auto Farm row not found once");
        mainEntry.bytes = write(mainNode);

        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot make output directory");
        writeJar(output, entries);
        verify(output, module.name, mainNode.name, entries.size());

        System.out.println("MODULE=" + module.name);
        System.out.println("FARM_FLAG=" + farmFlag.owner + "." + farmFlag.name);
        System.out.println("CONFIG_MENU=" + configMenu.owner + "." + configMenu.name + configMenu.desc);
        System.out.println("MAIN_MENU=" + mainMenu.owner + "." + mainMenu.name + mainMenu.desc);
        System.out.println("TOGGLE_ID=" + TOGGLE_ID);
        System.out.println("ENTRIES=" + entries.size());
    }

    /** Returns the IF_ICMPNE of "iload 1; push id; if_icmpne" in the method, or null. */
    private static JumpInsnNode branch(MethodNode m, int id) {
        JumpInsnNode found = null;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (!(i instanceof VarInsnNode) || i.getOpcode() != ILOAD || ((VarInsnNode) i).var != 1) continue;
            AbstractInsnNode c = next(i);
            AbstractInsnNode j = next(c);
            if (intOf(c) != null && intOf(c).intValue() == id && j != null && j.getOpcode() == IF_ICMPNE) {
                if (found != null) throw new IllegalStateException("Duplicate branch for id " + id);
                found = (JumpInsnNode) j;
            }
        }
        return found;
    }

    private static void discover(ClassNode module, MethodNode onAction) {
        // -40: getstatic F; ifne; iconst_1; goto; iconst_0; putstatic F; invokestatic apply; invokestatic save; invokestatic main; iconst_1; ireturn
        AbstractInsnNode i = next(branch(onAction, FARM_ENTRY_ID));
        require(i instanceof FieldInsnNode && i.getOpcode() == GETSTATIC && "Z".equals(((FieldInsnNode) i).desc), "farm flag read");
        farmFlag = (FieldInsnNode) i;
        require(farmFlag.owner.equals(module.name), "farm flag owner");
        AbstractInsnNode put = i;
        for (int n = 0; n < 5 && put != null; n++) put = next(put); // ifne, iconst_1, goto, iconst_0, putstatic
        require(put instanceof FieldInsnNode && put.getOpcode() == PUTSTATIC
                && ((FieldInsnNode) put).name.equals(farmFlag.name), "farm flag toggle");
        AbstractInsnNode a = next(put), s = next(a), mm = next(s), one = next(mm), ret = next(one);
        require(isStaticVoid(a) && isStaticVoid(s) && isStaticVoid(mm), "apply/save/main calls");
        require(one != null && one.getOpcode() == ICONST_1 && ret != null && ret.getOpcode() == IRETURN, "branch return");
        applyState = (MethodInsnNode) a;
        saveConfig = (MethodInsnNode) s;
        mainMenu = (MethodInsnNode) mm;
        require(applyState.owner.equals(module.name) && saveConfig.owner.equals(module.name), "apply/save owner");
        require(!mainMenu.owner.equals(module.name), "main menu owner");

        AbstractInsnNode c = next(branch(onAction, CONFIG_MENU_ID));
        require(isStaticVoid(c) && ((MethodInsnNode) c).owner.equals(module.name), "config menu call");
        configMenu = (MethodInsnNode) c;

        // Notice helper: the main class's static (String)V used for toggle messages in this handler.
        for (AbstractInsnNode x = onAction.instructions.getFirst(); x != null; x = x.getNext()) {
            if (x instanceof MethodInsnNode && x.getOpcode() == INVOKESTATIC
                    && ((MethodInsnNode) x).owner.equals(mainMenu.owner)
                    && "(Ljava/lang/String;)V".equals(((MethodInsnNode) x).desc)) {
                if (notice == null) notice = (MethodInsnNode) x;
                else require(notice.name.equals(((MethodInsnNode) x).name), "single notice helper");
            }
        }
        require(notice != null, "notice helper");
    }

    private static boolean isStaticVoid(AbstractInsnNode i) {
        return i instanceof MethodInsnNode && i.getOpcode() == INVOKESTATIC && "()V".equals(((MethodInsnNode) i).desc);
    }

    /** -40 now opens the detail menu instead of toggling. */
    private static void rewriteFarmEntryBranch(MethodNode onAction) {
        JumpInsnNode br = branch(onAction, FARM_ENTRY_ID);
        AbstractInsnNode first = next(br);
        AbstractInsnNode last = mainMenuCall(first);
        // Remove getstatic..invokestatic main (labels in between belong only to the toggle ternary).
        AbstractInsnNode i = first;
        while (true) {
            AbstractInsnNode nxt = i.getNext();
            boolean end = i == last;
            if (i instanceof LabelNode) {
                require(!isJumpTarget(onAction, (LabelNode) i, first, last), "external jump into -40 branch");
            }
            onAction.instructions.remove(i);
            if (end) { onAction.instructions.insertBefore(nxt, call(configMenu)); break; }
            i = nxt;
        }
    }

    private static AbstractInsnNode mainMenuCall(AbstractInsnNode from) {
        for (AbstractInsnNode i = from; i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode && ((MethodInsnNode) i).owner.equals(mainMenu.owner)
                    && ((MethodInsnNode) i).name.equals(mainMenu.name)) return i;
        }
        throw new IllegalStateException("Main menu call not found in -40 branch");
    }

    private static boolean isJumpTarget(MethodNode m, LabelNode label, AbstractInsnNode first, AbstractInsnNode last) {
        for (org.objectweb.asm.tree.TryCatchBlockNode t : m.tryCatchBlocks) {
            if (t.start == label || t.end == label || t.handler == label) return true;
        }
        boolean inside = false;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i == first) inside = true;
            if (!inside && i instanceof JumpInsnNode && ((JumpInsnNode) i).label == label) return true;
            if (i == last) inside = false;
        }
        return false;
    }

    /** New -49: toggle the farm flag, apply, save, notify and refresh the detail menu. */
    private static void addToggleBranch(MethodNode onAction) {
        LabelNode skip = new LabelNode(), off = new LabelNode(), set = new LabelNode(), msgOff = new LabelNode(), msg = new LabelNode();
        InsnList b = new InsnList();
        b.add(new VarInsnNode(ILOAD, 1));
        b.add(new IntInsnNode(BIPUSH, TOGGLE_ID));
        b.add(new JumpInsnNode(IF_ICMPNE, skip));
        b.add(new FieldInsnNode(GETSTATIC, farmFlag.owner, farmFlag.name, "Z"));
        b.add(new JumpInsnNode(IFNE, off));
        b.add(new InsnNode(ICONST_1));
        b.add(new JumpInsnNode(GOTO, set));
        b.add(off);
        b.add(new InsnNode(ICONST_0));
        b.add(set);
        b.add(new FieldInsnNode(PUTSTATIC, farmFlag.owner, farmFlag.name, "Z"));
        b.add(call(applyState));
        b.add(call(saveConfig));
        b.add(new FieldInsnNode(GETSTATIC, farmFlag.owner, farmFlag.name, "Z"));
        b.add(new JumpInsnNode(IFEQ, msgOff));
        b.add(new LdcInsnNode(NOTICE_ON));
        b.add(new JumpInsnNode(GOTO, msg));
        b.add(msgOff);
        b.add(new LdcInsnNode(NOTICE_OFF));
        b.add(msg);
        b.add(call(notice));
        b.add(call(configMenu));
        b.add(new InsnNode(ICONST_1));
        b.add(new InsnNode(IRETURN));
        b.add(skip);
        // Insert after the first label so the branch stays inside the handler's try range.
        AbstractInsnNode first = onAction.instructions.getFirst();
        if (first instanceof LabelNode) onAction.instructions.insert(first, b);
        else onAction.instructions.insertBefore(first, b);
    }

    private static int retitle(MethodNode config) {
        int count = 0;
        AbstractInsnNode[] ins = config.instructions.toArray();
        byte[] cur = null;
        for (int k = 0; k < ins.length; k++) {
            AbstractInsnNode i = ins[k];
            if (i.getOpcode() == NEWARRAY && ((IntInsnNode) i).operand == T_BYTE) {
                Integer len = intOf(prev(ins, k, 1));
                cur = len == null ? null : new byte[len.intValue()];
            } else if (i.getOpcode() == BASTORE && cur != null) {
                Integer idx = intOf(prev(ins, k, 2)), val = intOf(prev(ins, k, 1));
                if (idx != null && val != null && idx.intValue() < cur.length) cur[idx.intValue()] = (byte) val.intValue();
            } else if (i instanceof MethodInsnNode && ((MethodInsnNode) i).owner.equals("key")
                    && ((MethodInsnNode) i).name.equals("d") && cur != null) {
                if (OLD_DETAIL_TITLE.equals(decode(cur))) {
                    InsnList r = new InsnList();
                    r.add(new InsnNode(POP));
                    r.add(new LdcInsnNode(DETAIL_TITLE));
                    config.instructions.insert(i, r);
                    count++;
                }
                cur = null;
            }
        }
        return count;
    }

    private static String decode(byte[] b) {
        byte[] out = new byte[b.length];
        for (int x = 0; x < b.length; x++) out[x] = (byte) (b[x] ^ 42);
        try { return new String(out, "UTF-8"); } catch (java.io.UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }

    /** Inserts "Auto Farm: [BẬT]/[TẮT]" (id -49) as the first row, using the same item/handler calls as the menu's rows. */
    private static void addToggleRow(MethodNode config) {
        AbstractInsnNode store = null;
        MethodInsnNode itemCtor = null, add = null;
        FieldInsnNode handler = null;
        for (AbstractInsnNode i = config.instructions.getFirst(); i != null; i = i.getNext()) {
            if (store == null && i.getOpcode() == ASTORE && ((VarInsnNode) i).var == 0) {
                AbstractInsnNode p = previous(i);
                require(p instanceof MethodInsnNode && p.getOpcode() == INVOKESPECIAL && ((MethodInsnNode) p).name.equals("<init>")
                        && "(Ljava/lang/String;)V".equals(((MethodInsnNode) p).desc), "menu construction");
                store = i;
            }
            if (itemCtor == null && i instanceof MethodInsnNode && i.getOpcode() == INVOKESPECIAL
                    && ((MethodInsnNode) i).name.equals("<init>") && ((MethodInsnNode) i).desc.startsWith("(Ljava/lang/String;I")) {
                itemCtor = (MethodInsnNode) i;
                AbstractInsnNode h = previous(i);
                require(h instanceof FieldInsnNode && h.getOpcode() == GETSTATIC, "item handler");
                handler = (FieldInsnNode) h;
                AbstractInsnNode a = next(i);
                require(a instanceof MethodInsnNode && a.getOpcode() == INVOKEVIRTUAL
                        && "(Ljava/lang/Object;)V".equals(((MethodInsnNode) a).desc), "menu add");
                add = (MethodInsnNode) a;
            }
        }
        require(store != null && itemCtor != null, "config menu layout");
        LabelNode off = new LabelNode(), label = new LabelNode();
        InsnList row = new InsnList();
        row.add(new VarInsnNode(ALOAD, 0));
        row.add(new TypeInsnNode(NEW, itemCtor.owner));
        row.add(new InsnNode(DUP));
        row.add(new FieldInsnNode(GETSTATIC, farmFlag.owner, farmFlag.name, "Z"));
        row.add(new JumpInsnNode(IFEQ, off));
        row.add(new LdcInsnNode(TOGGLE_ON));
        row.add(new JumpInsnNode(GOTO, label));
        row.add(off);
        row.add(new LdcInsnNode(TOGGLE_OFF));
        row.add(label);
        row.add(new IntInsnNode(BIPUSH, TOGGLE_ID));
        row.add(new FieldInsnNode(GETSTATIC, handler.owner, handler.name, handler.desc));
        row.add(new MethodInsnNode(INVOKESPECIAL, itemCtor.owner, itemCtor.name, itemCtor.desc, false));
        row.add(new MethodInsnNode(INVOKEVIRTUAL, add.owner, add.name, add.desc, false));
        config.instructions.insert(store, row);
    }

    /** addMenu only added the "Cài Đặt Cấu Hình" (-39) row; that screen now lives under Auto Farm. */
    private static void removeConfigEntry(ClassNode module) {
        MethodNode addMenu = method(module, "addMenu", null);
        require(addMenu != null, "addMenu");
        boolean onlyConfig = false;
        int items = 0;
        for (AbstractInsnNode i = addMenu.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals("<init>")
                    && ((MethodInsnNode) i).desc.startsWith("(Ljava/lang/String;I")) {
                items++;
                Integer id = intOf(previous(previous(i)));
                onlyConfig = id != null && id.intValue() == CONFIG_MENU_ID;
            }
        }
        require(items == 1 && onlyConfig, "addMenu adds only the config row");
        addMenu.instructions.clear();
        addMenu.tryCatchBlocks.clear();
        if (addMenu.localVariables != null) addMenu.localVariables.clear();
        addMenu.instructions.add(new InsnNode(RETURN));
    }

    /** Main menu row -40: replace the computed "[BẬT]/[TẮT]" label with a constant. */
    private static int relabelMainEntry(MethodNode builder) {
        int count = 0;
        for (AbstractInsnNode i = builder.instructions.getFirst(); i != null; i = i.getNext()) {
            Integer v = intOf(i);
            if (v == null || v.intValue() != FARM_ENTRY_ID) continue;
            AbstractInsnNode h = next(i), ctor = next(h);
            if (!(h instanceof FieldInsnNode) || h.getOpcode() != GETSTATIC || !(ctor instanceof MethodInsnNode)
                    || !((MethodInsnNode) ctor).name.equals("<init>")
                    || !((MethodInsnNode) ctor).desc.startsWith("(Ljava/lang/String;I")) continue;
            InsnList r = new InsnList();
            r.add(new InsnNode(POP));
            r.add(new LdcInsnNode(MAIN_LABEL));
            builder.instructions.insertBefore(i, r);
            count++;
        }
        return count;
    }

    private static void verify(File output, String module, String main, int expected) throws IOException {
        JarFile jar = new JarFile(output);
        try {
            require(jar.size() == expected, "entry count");
            ClassNode m = parse(readAll(jar.getInputStream(jar.getJarEntry(module + ".class"))));
            MethodNode onAction = method(m, "onAction", "(II)Z");
            require(branch(onAction, TOGGLE_ID) != null, "toggle branch");
            AbstractInsnNode c = next(branch(onAction, FARM_ENTRY_ID));
            require(c instanceof MethodInsnNode && ((MethodInsnNode) c).name.equals(configMenu.name), "-40 opens detail menu");
            require(hasLdc(method(m, configMenu.name, configMenu.desc), TOGGLE_ON)
                    && hasLdc(method(m, configMenu.name, configMenu.desc), DETAIL_TITLE), "detail menu row/title");
            ClassNode mn = parse(readAll(jar.getInputStream(jar.getJarEntry(main + ".class"))));
            require(hasLdc(method(mn, mainMenu.name, mainMenu.desc), MAIN_LABEL), "main label");
        } finally { jar.close(); }
    }

    private static boolean hasLdc(MethodNode m, String s) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof LdcInsnNode && s.equals(((LdcInsnNode) i).cst)) return true;
        }
        return false;
    }

    private static MethodInsnNode call(MethodInsnNode m) {
        return new MethodInsnNode(INVOKESTATIC, m.owner, m.name, m.desc, false);
    }
    private static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && (desc == null || m.desc.equals(desc))) return m;
        return null;
    }
    private static Integer intOf(AbstractInsnNode i) {
        if (i == null) return null;
        int op = i.getOpcode();
        if (op >= ICONST_M1 && op <= ICONST_5) return Integer.valueOf(op - ICONST_0);
        if (op == BIPUSH || op == SIPUSH) return Integer.valueOf(((IntInsnNode) i).operand);
        if (i instanceof LdcInsnNode && ((LdcInsnNode) i).cst instanceof Integer) return (Integer) ((LdcInsnNode) i).cst;
        return null;
    }
    private static AbstractInsnNode prev(AbstractInsnNode[] ins, int k, int n) {
        int c = 0;
        for (int x = k - 1; x >= 0; x--) {
            if (ins[x].getOpcode() < 0) continue;
            if (++c == n) return ins[x];
        }
        return null;
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
    private static void require(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("Unexpected structure: " + what);
    }
    private static ClassNode parse(byte[] bytes) {
        ClassNode node = new ClassNode(ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
    private static byte[] write(ClassNode node) {
        require(node.version == V1_4, "class version 48 (no stack map frames)");
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(w);
        return w.toByteArray();
    }
    private static EntryData entry(List<EntryData> entries, String name) {
        for (EntryData e : entries) if (e.source.getName().equals(name)) return e;
        throw new IllegalStateException("Missing entry " + name);
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
    private static void writeJar(File output, List<EntryData> entries) throws IOException {
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
