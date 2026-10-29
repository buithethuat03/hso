import java.io.*;
import java.util.*;
import java.util.jar.*;
import javax.tools.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Replaces the mod-service boundaries and connects native enhancement packets to local policy. */
public final class HungBySonFullOfflinePatcher implements Opcodes {
    static final String SUPPORT = "HungBySonOfflineLocal";
    static final String SECRETS = n("lilllill"), ACTIVATE = n("lliiliil"), ENHANCE = n("lliillii"), SCREEN = n("lllili"), RECEIVER = n("lllllll");
    static final String MODMENU = n("lillllll");
    static final String[] KEYS = {"ch.chon_do", "ch.chon_bua", "ch.nang", "ch.ma_bua", "ch.bua_theo_cap",
        "buon.mua_gem", "buon_tay.thu_len", "buon_tay.thu_xuong", "buon.hoi_sinh", "buon.ban_gem"};
    static final String[] FLAGS = {"lilliililll", "lilliilliii", "lilliilliil", "lilliillili", "lilliillill", "lilliilllii", "lilliilllil"};
    static class Entry {
        JarEntry info; byte[] bytes;
        Entry(JarEntry info, byte[] bytes) { this.info = info; this.bytes = bytes; }
    }
    static String n(String suffix) { StringBuilder s = new StringBuilder(); for (int i = suffix.length(); i < 100; i++) s.append('i'); return s.append(suffix).toString(); }
    static String sh(String s) { return s.replaceAll("i{40,}", "~"); }
    static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    static byte[] bytes(InputStream in) throws IOException {
        try { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; for (int k; (k = in.read(b)) != -1;) out.write(b, 0, k); return out.toByteArray(); }
        finally { in.close(); }
    }
    static ClassNode parse(byte[] bytes) { ClassNode c = new ClassNode(ASM9); new ClassReader(bytes).accept(c, 0); return c; }
    static LinkedHashMap<String, Entry> read(File input) throws Exception {
        LinkedHashMap<String, Entry> entries = new LinkedHashMap<String, Entry>();
        try (JarFile jar = new JarFile(input)) {
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                JarEntry i = e.nextElement(); require(!entries.containsKey(i.getName()), "Duplicate entry"); entries.put(i.getName(), new Entry(i, bytes(jar.getInputStream(i))));
            }
        }
        return entries;
    }
    static MethodNode method(ClassNode c, String name, String desc) {
        MethodNode found = null;
        for (MethodNode m : c.methods) if (name.equals(m.name) && desc.equals(m.desc)) { require(found == null, "Ambiguous method"); found = m; }
        require(found != null, "Missing " + sh(c.name) + "." + sh(name) + desc); return found;
    }
    static void clear(MethodNode m) {
        m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
        m.visibleLocalVariableAnnotations = null; m.invisibleLocalVariableAnnotations = null; m.attrs = null;
    }
    static void noOp(MethodNode m) { clear(m); m.instructions.add(new InsnNode(RETURN)); }
    static void constant(MethodNode m, Object value) {
        clear(m); m.instructions.add(value instanceof Boolean ? new InsnNode((Boolean)value ? ICONST_1 : ICONST_0) : new LdcInsnNode(value));
        m.instructions.add(new InsnNode(value instanceof Boolean ? IRETURN : ARETURN));
    }
    static void save(Map<String, Entry> entries, ClassNode c) {
        Entry e = entries.get(c.name + ".class"); require(e != null, "Missing class");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(writer); e.bytes = writer.toByteArray();
    }
    static AbstractInsnNode previous(AbstractInsnNode i) { do { i = i.getPrevious(); } while (i != null && i.getOpcode() < 0); return i; }
    static Integer integer(AbstractInsnNode i) {
        if (i == null) return null;
        if (i.getOpcode() >= ICONST_M1 && i.getOpcode() <= ICONST_5) return i.getOpcode() - ICONST_0;
        if (i instanceof IntInsnNode) return ((IntInsnNode)i).operand;
        if (i instanceof LdcInsnNode && ((LdcInsnNode)i).cst instanceof Integer) return (Integer)((LdcInsnNode)i).cst;
        return null;
    }
    static Set<String> strings(MethodNode method) throws Exception {
        Set<String> values = new LinkedHashSet<String>(); byte[] array = null;
        for (AbstractInsnNode i : method.instructions) {
            if (i instanceof LdcInsnNode && ((LdcInsnNode)i).cst instanceof String) values.add((String)((LdcInsnNode)i).cst);
            if (i instanceof IntInsnNode && i.getOpcode() == NEWARRAY && ((IntInsnNode)i).operand == T_BYTE) {
                Integer length = integer(previous(i)); array = length == null || length < 0 || length > 65535 ? null : new byte[length];
            } else if (i.getOpcode() == BASTORE && array != null) {
                Integer index = integer(previous(previous(i))), value = integer(previous(i));
                if (index != null && value != null && index >= 0 && index < array.length) array[index] = (byte)(int)value;
            } else if (i instanceof MethodInsnNode && "key".equals(((MethodInsnNode)i).owner) && "d".equals(((MethodInsnNode)i).name) && array != null) {
                for (int k = 0; k < array.length; k++) array[k] ^= 42;
                values.add(new String(array, "UTF-8")); array = null;
            }
        }
        return values;
    }
    static Set<String> audit(Map<String, Entry> entries, boolean output) throws Exception {
        Set<String> keys = new TreeSet<String>(); int connectorCalls = 0;
        for (Entry entry : entries.values()) {
            if (!entry.info.getName().endsWith(".class")) continue;
            ClassNode c = parse(entry.bytes);
            for (MethodNode m : c.methods) {
                for (String s : strings(m)) {
                    if (s.matches("(?:ch|buon|buon_tay)\\.[a-z_]+")) keys.add(s);
                    if (!output && s.startsWith("http") && (s.contains("thaygiaonam") || s.contains("supabase"))) System.out.println("MOD_URL " + sh(c.name) + "." + sh(m.name) + " " + s);
                }
                for (AbstractInsnNode i : m.instructions) if (i instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode)i;
                    if (SECRETS.equals(call.owner) && !SECRETS.equals(c.name)) {
                        require(c.name.equals(ENHANCE) || c.name.equals(n("lliillil")) || (output && c.name.equals(SUPPORT)), "Unaudited secrets client: " + sh(c.name));
                    }
                    if ("javax/microedition/io/Connector".equals(call.owner) && "open".equals(call.name)) {
                        connectorCalls++;
                        require(c.name.equals("com/silverknight/TemMidlet") || c.name.equals(n("liiilli")), "Unaudited external connector in " + sh(c.name));
                        System.out.println("NATIVE_CONNECTION " + sh(c.name) + "." + sh(m.name) + m.desc);
                    }
                    if (output && (c.name.equals(SECRETS) || c.name.equals(ACTIVATE) || c.name.equals(SUPPORT))) {
                        require(!call.owner.equals("javax/microedition/io/Connector") && !call.owner.equals("java/net/URL"), "Mod external network call remains");
                        require(!call.owner.equals("java/lang/Thread") || !call.name.equals("start"), "Mod request worker remains");
                    }
                }
            }
        }
        require(keys.equals(new TreeSet<String>(Arrays.asList(KEYS))), "Unknown/missing mod instructions: " + keys);
        require(connectorCalls == 2, "Native connection audit changed");
        System.out.println("AUDIT_KEYS=" + keys + " MOD_CONNECTOR_CALLS=0"); return keys;
    }
    static void patchSecrets(Map<String, Entry> entries) {
        ClassNode c = parse(entries.get(SECRETS + ".class").bytes);
        MethodNode getter = method(c, n("lili"), "(Ljava/lang/String;I)Ljava/lang/String;");
        boolean existing = false;
        for (AbstractInsnNode i : getter.instructions) if (i instanceof MethodInsnNode && "HungBySonTradeLocal".equals(((MethodInsnNode)i).owner)) existing = true;
        require(existing, "Expected local V3 source");
        clear(getter); getter.instructions.add(new VarInsnNode(ALOAD, 0)); getter.instructions.add(new VarInsnNode(ILOAD, 1));
        getter.instructions.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "formula", getter.desc, false)); getter.instructions.add(new InsnNode(ARETURN));
        MethodNode verified = method(c, n("liii"), "(Ljava/lang/String;I)Z");
        clear(verified); verified.instructions.add(new VarInsnNode(ALOAD, 0));
        verified.instructions.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "handles", "(Ljava/lang/String;)Z", false)); verified.instructions.add(new InsnNode(IRETURN));
        constant(method(c, n("lli"), "(Ljava/lang/String;I)Z"), Boolean.FALSE);
        constant(method(c, n("lll"), "(Ljava/lang/String;I)Z"), Boolean.FALSE);
        constant(method(c, n("liliiili"), "()Z"), Boolean.FALSE);
        constant(method(c, n("liliiill"), "()Ljava/lang/String;"), "offline:");
        noOp(method(c, n("liliilii"), "()V"));
        noOp(method(c, n(""), "(Ljava/lang/String;Ljava/lang/String;I)V"));
        noOp(method(c, "run", "()V"));
        save(entries, c);
    }
    static void patchActivation(Map<String, Entry> entries) {
        ClassNode c = parse(entries.get(ACTIVATE + ".class").bytes);
        noOp(method(c, "run", "()V"));
        MethodNode local = method(c, "codexLocalActivate", "()V");
        InsnList guide = new InsnList();
        guide.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "guide", "()Ljava/lang/String;", false));
        guide.add(new FieldInsnNode(PUTSTATIC, ACTIVATE, n("lilliliiili"), "Ljava/lang/String;"));
        local.instructions.insertBefore(local.instructions.getLast(), guide);
        save(entries, c);
    }
    static void patchEnhancement(Map<String, Entry> entries) {
        ClassNode c = parse(entries.get(ENHANCE + ".class").bytes);
        MethodNode action = method(c, n("l"), "(Ljava/lang/String;I[I)Z"); int calls = 0;
        for (AbstractInsnNode i : action.instructions) if (i instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)i;
            if (SECRETS.equals(call.owner) && n("").equals(call.name) && action.desc.equals(call.desc)) {
                call.owner = SUPPORT; call.name = "executeEnhancement"; calls++;
            }
        }
        require(calls == 1, "Native-position enhancement executor missing");
        patchEnhancementWait(c);
        MethodNode stop = method(c, n("llilllll"), "()V");
        stop.instructions.insert(new MethodInsnNode(INVOKESTATIC, SUPPORT, "reset", "()V", false));
        MethodNode menu = method(c, "onAction", "(II)Z"); LabelNode keep = new LabelNode(), reset = new LabelNode();
        InsnList h = new InsnList(); h.add(new VarInsnNode(ILOAD, 1)); h.add(new IntInsnNode(BIPUSH, -85)); h.add(new JumpInsnNode(IF_ICMPEQ, reset));
        h.add(new VarInsnNode(ILOAD, 1)); h.add(new IntInsnNode(BIPUSH, -86)); h.add(new JumpInsnNode(IF_ICMPNE, keep));
        h.add(reset); h.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "reset", "()V", false)); h.add(keep); menu.instructions.insert(h);
        save(entries, c);
    }
    static void patchEnhancementWait(ClassNode c) {
        MethodNode action = method(c, n("l"), "(Ljava/lang/String;I[I)Z");
        clear(action);
        action.instructions.add(new VarInsnNode(ALOAD, 0)); action.instructions.add(new VarInsnNode(ILOAD, 1)); action.instructions.add(new VarInsnNode(ALOAD, 2));
        action.instructions.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "executeEnhancement", action.desc, false)); action.instructions.add(new InsnNode(IRETURN));
        MethodNode retry = method(c, n("llliliil"), "()V"); clear(retry);
        retry.instructions.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "waitForEnhancement", "()V", false)); retry.instructions.add(new InsnNode(RETURN));
    }
    static void patchReceiver(Map<String, Entry> entries) {
        ClassNode c = parse(entries.get(RECEIVER + ".class").bytes);
        MethodNode receive = method(c, n("liiiili"), "(L" + n("lllllil") + ";)V"); int hooks = 0;
        for (AbstractInsnNode i : receive.instructions.toArray()) {
            if (!(i instanceof FieldInsnNode)) continue;
            FieldInsnNode field = (FieldInsnNode)i;
            if (field.getOpcode() == PUTSTATIC && field.owner.equals(SCREEN) && field.name.equals(n("lililiiii")) && field.desc.equals("Ljava/lang/String;")) {
                require(previous(i) instanceof MethodInsnNode && "readUTF".equals(((MethodInsnNode)previous(i)).name), "Result text boundary changed");
                receive.instructions.insert(i, new MethodInsnNode(INVOKESTATIC, SUPPORT, "onEnhancementResult", "()V", false)); hooks++;
            }
        }
        require(hooks == 2, "Expected native result hooks for response types 2 and 5"); save(entries, c);
    }
    static void patchSessionBoundaries(Map<String, Entry> entries) {
        ClassNode menu = parse(entries.get(MODMENU + ".class").bytes);
        MethodNode dispatch = method(menu, n("l"), "(Let;Ljava/lang/String;)Z");
        for (AbstractInsnNode i : dispatch.instructions) if (i instanceof MethodInsnNode)
            require(!SUPPORT.equals(((MethodInsnNode)i).owner) || !"onEnhancementMenu".equals(((MethodInsnNode)i).name), "NPC session hook already present");
        InsnList before = new InsnList(); before.add(new VarInsnNode(ALOAD, 0)); before.add(new VarInsnNode(ALOAD, 1));
        before.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "onEnhancementMenu", "(Let;Ljava/lang/String;)V", false)); dispatch.instructions.insert(before); save(entries, menu);
        ClassNode receiver = parse(entries.get(RECEIVER + ".class").bytes);
        MethodNode receive = method(receiver, n("liiiili"), "(L" + n("lllllil") + ";)V");
        int subtype = receive.maxLocals++, reads = 0, texts = 0;
        for (AbstractInsnNode i : receive.instructions.toArray()) if (i instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)i;
            if (!call.owner.equals("java/io/DataInputStream")) continue;
            if (call.name.equals("readByte") && reads++ == 0) {
                InsnList h = new InsnList(); h.add(new InsnNode(DUP)); h.add(new VarInsnNode(ISTORE, subtype));
                h.add(new InsnNode(DUP)); h.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "onEnhancementReplyType", "(I)V", false)); receive.instructions.insert(i, h);
            }
            if (call.name.equals("readUTF")) {
                InsnList h = new InsnList(); h.add(new InsnNode(DUP)); h.add(new VarInsnNode(ILOAD, subtype));
                h.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "onEnhancementReply", "(Ljava/lang/String;I)V", false)); receive.instructions.insert(i, h); texts++;
            }
        }
        require(reads > 0 && texts > 0, "Enhancement acknowledgement parser missing");
        MethodNode notice = method(receiver, n("llilli"), "(L" + n("lllllil") + ";)V"); int notices = 0;
        for (AbstractInsnNode i : notice.instructions.toArray()) if (i instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)i;
            if (call.owner.equals("java/io/DataInputStream") && call.name.equals("readUTF") && ++notices == 2) {
                InsnList h = new InsnList(); h.add(new InsnNode(DUP)); h.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "onGameNotice", "(Ljava/lang/String;)V", false)); notice.instructions.insert(i, h);
            }
        }
        require(notices >= 2, "Game notice body missing"); save(entries, receiver);
    }
    static byte[] compile(File input, File template, File work) throws Exception {
        String source = new String(bytes(new FileInputStream(template)), "UTF-8");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("@~([a-z]*)@").matcher(source);
        StringBuffer expanded = new StringBuffer(); while (matcher.find()) matcher.appendReplacement(expanded, n(matcher.group(1))); matcher.appendTail(expanded);
        File javaFile = new File(work, SUPPORT + ".java");
        try (Writer out = new OutputStreamWriter(new FileOutputStream(javaFile), "UTF-8")) { out.write(expanded.toString()); }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler(); require(compiler != null, "JDK required");
        int rc = compiler.run(null, null, null, "-encoding", "UTF-8", "--release", "7", "-Xlint:-options", "-cp", input.getPath() + File.pathSeparator + System.getProperty("hbs.extra.cp", ""), "-d", work.getPath(), javaFile.getPath());
        require(rc == 0, "Support compilation failed");
        ClassNode c = new ClassNode(ASM9); new ClassReader(bytes(new FileInputStream(new File(work, SUPPORT + ".class")))).accept(c, ClassReader.SKIP_FRAMES);
        c.version = V1_4;
        for (MethodNode m : c.methods) for (AbstractInsnNode i : m.instructions) {
            require(i.getOpcode() != INVOKEDYNAMIC, "Invokedynamic in support");
            if (i instanceof TypeInsnNode && ((TypeInsnNode)i).desc.equals("java/lang/StringBuilder")) ((TypeInsnNode)i).desc = "java/lang/StringBuffer";
            if (i instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)i;
                if (call.owner.equals("java/lang/StringBuilder")) call.owner = "java/lang/StringBuffer";
                call.desc = call.desc.replace("java/lang/StringBuilder", "java/lang/StringBuffer");
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(writer); return writer.toByteArray();
    }
    static void write(File file, Map<String, Entry> entries) throws Exception {
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file))) {
            for (Entry e : entries.values()) {
                JarEntry i = new JarEntry(e.info.getName()); if (e.info.getTime() >= 0) i.setTime(e.info.getTime());
                if (e.info.getComment() != null) i.setComment(e.info.getComment()); if (e.info.getExtra() != null) i.setExtra(e.info.getExtra());
                out.putNextEntry(i); out.write(e.bytes); out.closeEntry();
            }
        }
    }
    static void verify(File input, File output) throws Exception {
        Map<String, Entry> old = read(input), now = read(output);
        require(now.size() == old.size() + 1, "Entry count mismatch");
        Set<String> expected = new HashSet<String>(Arrays.asList(SECRETS + ".class", ACTIVATE + ".class", ENHANCE + ".class", RECEIVER + ".class", MODMENU + ".class"));
        Set<String> changed = new HashSet<String>(); int classes = 0;
        for (Map.Entry<String, Entry> item : now.entrySet()) {
            String name = item.getKey(); Entry e = item.getValue(); java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(e.bytes);
            require(crc.getValue() == e.info.getCrc(), "ZIP CRC mismatch");
            if (name.endsWith(".class")) classes++;
            Entry before = old.get(name);
            if (before == null) { require(name.equals(SUPPORT + ".class") && parse(e.bytes).version == V1_4, "Unexpected new entry/version"); continue; }
            if (name.endsWith(".class")) require(parse(before.bytes).version == parse(e.bytes).version, "Class version changed");
            if (!Arrays.equals(before.bytes, e.bytes)) changed.add(name);
        }
        require(changed.equals(expected), "Unexpected changed entries: " + changed); audit(now, true);
        System.out.println("ARCHIVE=OK entries=" + now.size() + " classes=" + classes + " changed=" + changed.size() + " added=1 resources_and_manifest=identical");
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 5 && "repair".equals(args[0])) {
            File input = new File(args[1]).getCanonicalFile(), output = new File(args[2]).getCanonicalFile(), template = new File(args[3]).getCanonicalFile(), work = new File(args[4]).getCanonicalFile();
            require(input.isFile() && template.isFile() && !output.exists() && !input.equals(output), "Unsafe repair input/output");
            require(work.isDirectory() || work.mkdirs(), "Cannot create repair build directory");
            Map<String, Entry> entries = read(input); require(entries.containsKey(SUPPORT + ".class"), "Local source required");
            entries.get(SUPPORT + ".class").bytes = compile(input, template, work);
            ClassNode module = parse(entries.get(ENHANCE + ".class").bytes); patchEnhancementWait(module); save(entries, module); patchSessionBoundaries(entries);
            write(output, entries);
            Map<String, Entry> before = read(input), after = read(output); require(before.keySet().equals(after.keySet()), "Repair entry set changed");
            Set<String> changes = new HashSet<String>();
            for (String name : before.keySet()) {
                if (!Arrays.equals(before.get(name).bytes, after.get(name).bytes)) changes.add(name);
                if (name.endsWith(".class")) require(parse(before.get(name).bytes).version == parse(after.get(name).bytes).version, "Repair class version changed");
                java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(after.get(name).bytes); require(crc.getValue() == after.get(name).info.getCrc(), "Repair CRC mismatch");
            }
            Set<String> allowed = new HashSet<String>(Arrays.asList(ENHANCE + ".class", SUPPORT + ".class", MODMENU + ".class", RECEIVER + ".class"));
            require(allowed.containsAll(changes) && changes.contains(SUPPORT + ".class") && changes.contains(MODMENU + ".class") && changes.contains(RECEIVER + ".class"), "Unexpected repair changes");
            audit(after, true); System.out.println("REPAIR=OK changed_classes=" + changes.size() + " entries=" + after.size() + " resources_and_manifest=identical"); return;
        }
        if (args.length == 2 && "audit".equals(args[0])) {
            Map<String, Entry> entries = read(new File(args[1])); audit(entries, entries.containsKey(SUPPORT + ".class")); return;
        }
        if (args.length != 4) throw new IllegalArgumentException("input.jar output.jar template.java.in workDir | audit jar");
        File input = new File(args[0]).getCanonicalFile(), output = new File(args[1]).getCanonicalFile(), template = new File(args[2]).getCanonicalFile(), work = new File(args[3]).getCanonicalFile();
        require(input.isFile() && template.isFile() && !output.exists() && !input.equals(output), "Unsafe input/output");
        require(work.isDirectory() || work.mkdirs(), "Cannot create build directory");
        Map<String, Entry> entries = read(input); require(!entries.containsKey(SUPPORT + ".class"), "Already patched"); audit(entries, false);
        byte[] support = compile(input, template, work);
        patchSecrets(entries); patchActivation(entries); patchEnhancement(entries); patchReceiver(entries); patchSessionBoundaries(entries);
        entries.put(SUPPORT + ".class", new Entry(new JarEntry(SUPPORT + ".class"), support));
        write(output, entries); verify(input, output);
    }
}
