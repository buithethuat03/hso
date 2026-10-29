import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Scoped current-Hso authentication and inventory wire-layout port. */
public final class ChibiEntryCompatibilityPatcher implements Opcodes {
    static final String DONOR = "C69F9C6E9FF3AB998D21C9D78919859E5364C4D28B628723DC74DA48EFDC68C6";
    static final String CHIBI = "A5C8FC9C5659518E2E62AB702381FD4E781B3C55ABE82F87EA4FF63A0D142DFB";
    static final String LUNA = "F8998F3EC1570304DB02C7EDBD8AE844CCA90BE917B621B0F45A3DAA95C62CD2";
    static final String LOGIN = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IB)V";
    static final String AUTH = "luna/generated/ChibiLoginAuth";
    static final String[] LIST_MARKERS = {"size item quest=", "size material=", "size item="};

    public static void main(String[] args) throws Exception {
        check(args.length == 4, "input.jar Hso.jar output.jar report.properties");
        File input = new File(args[0]), reference = new File(args[1]), output = new File(args[2]);
        String inputHash = hash(input);
        check(CHIBI.equals(inputHash) || LUNA.equals(inputHash), "Unrecognized input; preserve user changes");
        check(DONOR.equals(hash(reference)), "Current Hso reference hash mismatch");
        check(!output.exists() && !new File(args[3]).exists(), "Use fresh output paths");
        Map<String,byte[]> before = read(input), donor = read(reference);
        Map<String,byte[]> after = new LinkedHashMap<String,byte[]>(before);
        Map<String,ClassNode> nodes = nodes(before);
        ClassNode sender = null, receiver = null, session = null;
        MethodNode login = null, inventory = null;
        for (ClassNode c : nodes.values()) {
            for (MethodNode m : c.methods) {
                if (m.desc.equals(LOGIN)) {
                    check(login == null, "Ambiguous login serializer"); sender = c; login = m;
                }
                if (has(m, LIST_MARKERS[0]) && has(m, LIST_MARKERS[1]) && has(m, LIST_MARKERS[2])) {
                    check(inventory == null, "Ambiguous shared inventory reader"); receiver = c; inventory = m;
                }
            }
            boolean inputStream = false, outputStream = false, singleton = false;
            for (FieldNode f : c.fields) {
                if (f.desc.equals("Ljava/io/DataInputStream;")) inputStream = true;
                if (f.desc.equals("Ljava/io/DataOutputStream;")) outputStream = true;
            }
            for (MethodNode m : c.methods) if (m.desc.equals("()L" + c.name + ";") && (m.access & ACC_STATIC) != 0) singleton = true;
            if (inputStream && outputStream && singleton) {
                check(session == null, "Ambiguous network session"); session = c;
            }
        }
        check(sender != null && receiver != null && session != null, "Required native symbols absent");
        check((login.access & ACC_STATIC) == 0 && utf(login).size() == 8 && has(login, "LOCK") && has(login, "|HSOR_SECRET_2026"), "Unexpected login preimage");
        FieldNode key = null; MethodNode getter = null, connected = null;
        for (FieldNode f : session.fields) if (f.desc.equals("[B") && (f.access & ACC_STATIC) == 0) {
            check(key == null && (f.access & ACC_PUBLIC) != 0, "Unexpected session-key ABI"); key = f;
        }
        for (MethodNode m : session.methods) {
            if (m.desc.equals("()L" + session.name + ";") && (m.access & ACC_STATIC) != 0) {
                check(getter == null, "Ambiguous session getter"); getter = m;
            }
            if (m.desc.equals("()Z") && (m.access & ACC_STATIC) == 0) {
                check(connected == null, "Ambiguous connected getter"); connected = m;
            }
        }
        check(key != null && getter != null && connected != null, "Missing session/key access");
        Properties report = new Properties();
        report.setProperty("input.sha256", inputHash); report.setProperty("reference.sha256", DONOR);
        report.setProperty("login.owner", sender.name); report.setProperty("login.method", login.name); report.setProperty("login.desc", LOGIN);
        report.setProperty("session.owner", session.name); report.setProperty("session.getter", getter.name);
        report.setProperty("session.connected", connected.name); report.setProperty("session.key", key.name);
        report.setProperty("protocol.receiver", receiver.name); report.setProperty("protocol.method", inventory.name); report.setProperty("protocol.desc", inventory.desc);
        patchLogin(login, node(donor.get("IIlI.class")), session, key, getter, connected);
        ClassNode helper = node(donor.get("IIIl.class"));
        check(!before.containsKey(AUTH + ".class"), "Authentication-helper collision");
        helper.name = AUTH;
        for (MethodNode m : helper.methods) for (AbstractInsnNode n : m.instructions.toArray()) {
            if (n instanceof FieldInsnNode && ((FieldInsnNode)n).owner.equals("IIIl")) ((FieldInsnNode)n).owner = AUTH;
            if (n instanceof MethodInsnNode && ((MethodInsnNode)n).owner.equals("IIIl")) ((MethodInsnNode)n).owner = AUTH;
        }
        after.put(AUTH + ".class", bytes(helper, null));
        patchInventory(inventory, findInventory(node(donor.get("Illllll.class"))));
        after.put(sender.name + ".class", bytes(sender, before.get(sender.name + ".class")));
        after.put(receiver.name + ".class", bytes(receiver, before.get(receiver.name + ".class")));
        verifyUntouchedMethods(node(before.get(sender.name + ".class")), sender, login);
        verifyUntouchedMethods(node(before.get(receiver.name + ".class")), receiver, inventory);
        check(after.size() == before.size() + 1, "Unexpected archive topology");
        int changed = 0;
        for (String name : before.keySet()) if (!Arrays.equals(before.get(name), after.get(name))) {
            check(name.equals(sender.name + ".class") || name.equals(receiver.name + ".class"), "Unexpected changed entry " + name); changed++;
        }
        check(changed == 2, "Expected exactly sender and inventory receiver changes");
        JarFile original = new JarFile(input);
        JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Enumeration<JarEntry> e = original.entries(); e.hasMoreElements();) {
                JarEntry old = e.nextElement(), fresh = new JarEntry(old.getName()); fresh.setTime(old.getTime());
                out.putNextEntry(fresh); out.write(after.get(old.getName())); out.closeEntry();
            }
            JarEntry extra = new JarEntry(AUTH + ".class"); extra.setTime(0); out.putNextEntry(extra); out.write(after.get(extra.getName())); out.closeEntry();
        } finally { out.close(); original.close(); }
        report.setProperty("output.sha256", hash(output));
        report.setProperty("changed.classes", sender.name + "," + receiver.name);
        report.setProperty("added.classes", AUTH); report.setProperty("login.auth.owner", AUTH); report.setProperty("login.auth.method", "II");
        report.setProperty("login.version", "9.9.9"); report.setProperty("login.final.utf", "LOCK");
        report.setProperty("protocol.short.readers", "5"); report.setProperty("protocol.inventory.opcodes", "16,65");
        report.setProperty("resource.changes", "0"); report.setProperty("rms.changes", "0");
        Writer writer = new OutputStreamWriter(new FileOutputStream(args[3]), "UTF-8");
        try { report.store(writer, "Current Hso login/inventory compatibility; original feature bytes retained"); } finally { writer.close(); }
        System.out.println("CHIBI_ENTRY_PATCH=PASS; changedClasses=2; addedAuth=1; shortReaders=5; resourcesUnchanged=true; SHA256=" + hash(output));
    }

    static void patchLogin(MethodNode target, ClassNode donorSender, ClassNode session, FieldNode key, MethodNode getter, MethodNode connected) {
        MethodNode donor = method(donorSender, "II", LOGIN);
        List<MethodInsnNode> oldUtf = utf(target), donorUtf = utf(donor);
        check(donorUtf.size() == 8 && has(donor, "LOCK"), "Donor login contract changed");
        FieldInsnNode packet = null, stream = null;
        for (AbstractInsnNode n = target.instructions.getFirst(); n != oldUtf.get(0); n = n.getNext()) if (n instanceof FieldInsnNode && n.getOpcode() == GETFIELD) {
            FieldInsnNode f = (FieldInsnNode)n;
            if (f.desc.equals("Ljava/io/DataOutputStream;")) stream = f; else packet = f;
        }
        check(packet != null && stream != null && packet.desc.equals("L" + stream.owner + ";"), "Inlined packet/output-stream mapping changed");
        Map<LabelNode,LabelNode> labels = new IdentityHashMap<LabelNode,LabelNode>();
        for (AbstractInsnNode n : donor.instructions.toArray()) if (n instanceof LabelNode) labels.put((LabelNode)n, new LabelNode());
        Set<AbstractInsnNode> copied = Collections.newSetFromMap(new IdentityHashMap<AbstractInsnNode,Boolean>());
        InsnList auth = new InsnList();
        for (AbstractInsnNode n = donorUtf.get(4).getNext(); n != donorUtf.get(5).getNext(); n = n.getNext()) {
            copied.add(n); if (n instanceof FrameNode) continue;
            AbstractInsnNode clone = n.clone(labels);
            if (clone instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode)clone;
                if (f.owner.equals("IIl") && f.name.equals("Ill") && f.desc.equals("[B")) { f.owner = session.name; f.name = key.name; }
                else if (f.owner.equals("llllll") && f.name.equals("Il")) { f.owner = packet.owner; f.name = packet.name; f.desc = packet.desc; }
                else check(f.owner.startsWith("java/"), "Unmapped authentication field " + f.owner + "." + f.name);
            } else if (clone instanceof MethodInsnNode) {
                MethodInsnNode c = (MethodInsnNode)clone;
                if (c.owner.equals("IIl") && c.desc.equals("()LIIl;")) { c.owner = session.name; c.name = getter.name; c.desc = getter.desc; }
                else if (c.owner.equals("IIl") && c.desc.equals("()Z")) { c.owner = session.name; c.name = connected.name; }
                else if (c.owner.equals("lII") && c.desc.equals("()Ljava/io/DataOutputStream;")) clone = new FieldInsnNode(GETFIELD, stream.owner, stream.name, stream.desc);
                else if (c.owner.equals("IIIl")) c.owner = AUTH;
                else check(c.owner.startsWith("java/"), "Unmapped authentication method " + c.owner + "." + c.name);
            }
            auth.add(clone);
        }
        for (TryCatchBlockNode t : donor.tryCatchBlocks) if (copied.contains(t.start) && copied.contains(t.end) && copied.contains(t.handler))
            target.tryCatchBlocks.add(0, new TryCatchBlockNode(labels.get(t.start), labels.get(t.end), labels.get(t.handler), t.type));
        AbstractInsnNode stop = oldUtf.get(5).getNext();
        remove(target, oldUtf.get(4).getNext(), stop); target.instructions.insertBefore(stop, auth);
        InsnList parameters = new InsnList();
        parameters.add(new LdcInsnNode("9.9.9")); parameters.add(new VarInsnNode(ASTORE, 3));
        for (int i = 4; i <= 6; i++) { parameters.add(new LdcInsnNode("0")); parameters.add(new VarInsnNode(ASTORE, i)); }
        target.instructions.insert(parameters);
        check(utf(target).size() == 8 && !has(target, "|HSOR_SECRET_2026"), "Authentication replacement failed");
    }

    static MethodNode findInventory(ClassNode c) {
        MethodNode result = null;
        for (MethodNode m : c.methods) if (has(m, LIST_MARKERS[0]) && has(m, LIST_MARKERS[1]) && has(m, LIST_MARKERS[2])) {
            check(result == null, "Ambiguous donor inventory method"); result = m;
        }
        check(result != null, "Donor inventory method absent"); return result;
    }

    static void patchInventory(MethodNode target, MethodNode donor) {
        check((target.access & ACC_STATIC) != 0 && Type.getArgumentTypes(target.desc).length == 3, "Inventory ABI changed");
        List<MethodInsnNode> oldReads = reads(target), donorReads = reads(donor);
        // Prefix reads: storage capacity, action, subtype, optional currency long/int,
        // first list category, first list count. Validate each position against donor.
        String[] prefix = {"readByte", "readByte", "readByte", "readLong", "readInt", "readByte", "readByte"};
        String[] expected = {"readShort", "readByte", "readByte", "readLong", "readInt", "readByte", "readShort"};
        check(oldReads.size() > prefix.length && donorReads.size() > prefix.length, "Missing inventory prefix");
        for (int i = 0; i < prefix.length; i++) {
            check(oldReads.get(i).name.equals(prefix[i]) && donorReads.get(i).name.equals(expected[i]), "Inventory prefix mismatch at " + i);
        }
        Set<MethodInsnNode> changes = Collections.newSetFromMap(new IdentityHashMap<MethodInsnNode,Boolean>());
        changes.add(oldReads.get(0)); changes.add(oldReads.get(6));
        for (String marker : LIST_MARKERS) {
            MethodInsnNode current = countBeforeMarker(target, marker), reference = countBeforeMarker(donor, marker);
            check(current.name.equals("readByte") && reference.name.equals("readShort"), "Inventory count contract changed: " + marker);
            check(next(current) instanceof VarInsnNode && next(current).getOpcode() == ISTORE, "List count not stored as int");
            changes.add(current);
        }
        check(changes.size() == 5, "Inventory patch coverage changed");
        for (MethodInsnNode c : changes) { c.name = "readShort"; c.desc = "()S"; }
    }

    static MethodInsnNode countBeforeMarker(MethodNode m, String marker) {
        AbstractInsnNode constant = null;
        for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof LdcInsnNode && marker.equals(((LdcInsnNode)n).cst)) {
            check(constant == null, "Repeated list marker " + marker); constant = n;
        }
        check(constant != null, "List marker absent " + marker);
        for (AbstractInsnNode n = constant.getPrevious(); n != null; n = n.getPrevious()) if (n instanceof MethodInsnNode && ((MethodInsnNode)n).owner.equals("java/io/DataInputStream")) return (MethodInsnNode)n;
        throw new IllegalStateException("Missing list count reader " + marker);
    }
    static void verifyUntouchedMethods(ClassNode before, ClassNode after, MethodNode allowed) {
        check(before.methods.size() == after.methods.size() && before.fields.size() == after.fields.size(), "Native ABI changed");
        for (int i = 0; i < before.fields.size(); i++) {
            FieldNode a = before.fields.get(i), b = after.fields.get(i);
            check(a.name.equals(b.name) && a.desc.equals(b.desc) && a.access == b.access && Objects.equals(a.value, b.value), "Native field changed");
        }
        int preserved = 0;
        for (int i = 0; i < before.methods.size(); i++) {
            MethodNode a = before.methods.get(i), b = after.methods.get(i);
            check(a.name.equals(b.name) && a.desc.equals(b.desc) && a.access == b.access, "Native method ABI changed");
            if (b == allowed) continue;
            check(Arrays.equals(methodBytes(a), methodBytes(b)), "Unrelated method changed: " + before.name + "." + a.name + a.desc); preserved++;
        }
        System.out.println("UNCHANGED_NATIVE_METHODS=" + preserved + "; owner=" + before.name);
    }
    static byte[] methodBytes(MethodNode m) { ClassWriter w = new ClassWriter(0); w.visit(V1_3, ACC_PUBLIC, "MethodFingerprint", null, "java/lang/Object", null); m.accept(w); w.visitEnd(); return w.toByteArray(); }
    static List<MethodInsnNode> reads(MethodNode m) {
        List<MethodInsnNode> result = new ArrayList<MethodInsnNode>();
        for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof MethodInsnNode && ((MethodInsnNode)n).owner.equals("java/io/DataInputStream")) result.add((MethodInsnNode)n);
        return result;
    }
    static List<MethodInsnNode> utf(MethodNode m) {
        List<MethodInsnNode> result = new ArrayList<MethodInsnNode>();
        for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof MethodInsnNode && ((MethodInsnNode)n).owner.equals("java/io/DataOutputStream") && ((MethodInsnNode)n).name.equals("writeUTF")) result.add((MethodInsnNode)n);
        return result;
    }
    static AbstractInsnNode next(AbstractInsnNode n) { do { n = n.getNext(); } while (n != null && n.getOpcode() < 0); return n; }
    static void remove(MethodNode m, AbstractInsnNode start, AbstractInsnNode end) { for (AbstractInsnNode n = start; n != end;) { check(n != null, "Invalid instruction range"); AbstractInsnNode following = n.getNext(); m.instructions.remove(n); n = following; } }
    static boolean has(MethodNode m, Object value) { for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof LdcInsnNode && value.equals(((LdcInsnNode)n).cst)) return true; return false; }
    static MethodNode method(ClassNode c, String name, String desc) { for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m; throw new IllegalStateException("Missing method " + c.name + "." + name + desc); }
    static ClassNode node(byte[] bytes) { check(bytes != null, "Class absent"); ClassNode c = new ClassNode(); new ClassReader(bytes).accept(c, 0); return c; }
    static Map<String,ClassNode> nodes(Map<String,byte[]> jar) { Map<String,ClassNode> result = new LinkedHashMap<String,ClassNode>(); for (String n : jar.keySet()) if (n.endsWith(".class")) { ClassNode c = node(jar.get(n)); result.put(c.name, c); } return result; }
    static byte[] bytes(ClassNode c, byte[] previous) { ClassWriter w = previous == null ? new ClassWriter(ClassWriter.COMPUTE_MAXS) : new ClassWriter(new ClassReader(previous), ClassWriter.COMPUTE_MAXS); c.accept(w); return w.toByteArray(); }
    static Map<String,byte[]> read(File file) throws IOException {
        Map<String,byte[]> result = new LinkedHashMap<String,byte[]>(); JarFile jar = new JarFile(file);
        try { for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) { JarEntry entry = e.nextElement(); InputStream in = jar.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream(); try { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) >= 0) out.write(b, 0, n); } finally { in.close(); } check(!result.containsKey(entry.getName()), "Duplicate ZIP entry"); result.put(entry.getName(), out.toByteArray()); } } finally { jar.close(); }
        return result;
    }
    static String hash(File file) throws Exception { MessageDigest d = MessageDigest.getInstance("SHA-256"); InputStream in = new FileInputStream(file); try { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) >= 0) d.update(b, 0, n); } finally { in.close(); } StringBuilder s = new StringBuilder(); for (byte b : d.digest()) s.append(String.format("%02X", b & 255)); return s.toString(); }
    static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
}
