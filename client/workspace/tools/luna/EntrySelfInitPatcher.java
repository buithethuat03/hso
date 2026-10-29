import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Current Hso self-character initialization: short bag capacity and ceiling pages. */
public final class EntrySelfInitPatcher implements Opcodes {
    static final String CHIBI = "47C2A7F3A874133C37301F193B2A4FAA20572A9429DEB43C7D6C3A5C92A06D95";
    static final String LUNA = "F46209F1E1AE9E34729C65DB80B5AEDCD645C9C3B1E4A1CC5A2FD732E76BA06A";
    public static void main(String[] args) throws Exception {
        check(args.length == 5, "current.jar Hso.jar output.jar prior.properties output.properties");
        File input = new File(args[0]), donor = new File(args[1]), output = new File(args[2]);
        String sourceHash = ChibiEntryCompatibilityPatcher.hash(input);
        check(CHIBI.equals(sourceHash) || LUNA.equals(sourceHash), "Unexpected current input; preserve user changes");
        check(ChibiEntryCompatibilityPatcher.DONOR.equals(ChibiEntryCompatibilityPatcher.hash(donor)), "Hso donor changed");
        check(!output.exists() && !new File(args[4]).exists(), "Use fresh output paths");
        Properties p = new Properties(); Reader r = new InputStreamReader(new FileInputStream(args[3]), "UTF-8");
        try { p.load(r); } finally { r.close(); }
        check(sourceHash.equals(p.getProperty("output.sha256")), "Prior catalog/output mismatch");
        Map<String,byte[]> original = ChibiEntryCompatibilityPatcher.read(input);
        Map<String,byte[]> reference = ChibiEntryCompatibilityPatcher.read(donor);
        String owner = p.getProperty("protocol.receiver");
        ClassNode receiver = ChibiEntryCompatibilityPatcher.node(original.get(owner + ".class"));
        check(receiver.methods.size() > 3, "Self receiver method absent");
        MethodNode target = receiver.methods.get(3);
        MethodNode expected = ChibiEntryCompatibilityPatcher.method(ChibiEntryCompatibilityPatcher.node(reference.get("Illllll.class")), "Il", "(LlII;)V");
        check((target.access & ACC_STATIC) != 0 && Type.getArgumentTypes(target.desc).length == 1 && Type.getReturnType(target.desc).getSort() == Type.VOID, "Self receiver ABI changed");
        Site site = site(target, false), donorSite = site(expected, true);
        List<MethodInsnNode> actualReads = ChibiEntryCompatibilityPatcher.reads(target), expectedReads = ChibiEntryCompatibilityPatcher.reads(expected);
        int boundary = actualReads.indexOf(site.reader), donorBoundary = expectedReads.indexOf(donorSite.reader);
        check(boundary == donorBoundary, "Self mandatory-prefix reader count differs");
        for (int i = 0; i < boundary; i++) check(actualReads.get(i).name.equals(expectedReads.get(i).name) && actualReads.get(i).desc.equals(expectedReads.get(i).desc), "Unexpected self-prefix read mismatch at " + i);
        site.reader.name = "readShort"; site.reader.desc = "()S";
        InsnList ceiling = new InsnList(); ceiling.add(new IntInsnNode(BIPUSH, 41)); ceiling.add(new InsnNode(IADD));
        target.instructions.insertBefore(site.divisor, ceiling);
        LabelNode validPages = new LabelNode();
        InsnList minimum = new InsnList();
        minimum.add(new FieldInsnNode(GETSTATIC, site.pages.owner, site.pages.name, site.pages.desc));
        minimum.add(new InsnNode(ICONST_1)); minimum.add(new JumpInsnNode(IF_ICMPGE, validPages));
        minimum.add(new InsnNode(ICONST_1)); minimum.add(new FieldInsnNode(PUTSTATIC, site.pages.owner, site.pages.name, site.pages.desc));
        minimum.add(validPages); target.instructions.insert(site.pages, minimum);
        // Keep the existing i2s; it is harmless after a signed-short read.
        ClassNode before = ChibiEntryCompatibilityPatcher.node(original.get(owner + ".class"));
        ChibiEntryCompatibilityPatcher.verifyUntouchedMethods(before, receiver, target);
        Map<String,byte[]> after = new LinkedHashMap<String,byte[]>(original);
        after.put(owner + ".class", ChibiEntryCompatibilityPatcher.bytes(receiver, original.get(owner + ".class")));
        int changed = 0;
        for (String name : original.keySet()) if (!Arrays.equals(original.get(name), after.get(name))) { check(name.equals(owner + ".class"), "Unrelated entry changed"); changed++; }
        check(changed == 1 && after.size() == original.size(), "Unexpected archive change");
        JarFile archive = new JarFile(input); JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
        try { for (Enumeration<JarEntry> e = archive.entries(); e.hasMoreElements();) { JarEntry old = e.nextElement(), fresh = new JarEntry(old.getName()); fresh.setTime(old.getTime()); out.putNextEntry(fresh); out.write(after.get(old.getName())); out.closeEntry(); } } finally { out.close(); archive.close(); }
        p.setProperty("self.input.sha256", sourceHash); p.setProperty("output.sha256", ChibiEntryCompatibilityPatcher.hash(output));
        p.setProperty("self.owner", owner); p.setProperty("self.method", target.name); p.setProperty("self.desc", target.desc);
        field(p, "self.capacity", site.capacity); field(p, "self.pages", site.pages);
        p.setProperty("self.wire.capacity", "signed-short"); p.setProperty("self.page.expression", "max(1,(capacity+41)/42)");
        p.setProperty("self.prefix.readers", String.valueOf(boundary + 1));
        Writer writer = new OutputStreamWriter(new FileOutputStream(args[4]), "UTF-8");
        try { p.store(writer, "Self opcode-3 bag capacity alignment; current Hso prefix contract"); } finally { writer.close(); }
        System.out.println("ENTRY_SELF_PATCH=PASS; opcode=3; shortCapacity=true; ceilingPages=true; prefixReaders=" + (boundary + 1) + "; changedClasses=1; changedMethods=1; SHA256=" + p.getProperty("output.sha256"));
    }
    static final class Site {
        MethodInsnNode reader; FieldInsnNode capacity, pages; AbstractInsnNode divisor;
    }
    static Site site(MethodNode method, boolean donor) {
        Site found = null;
        for (MethodInsnNode reader : ChibiEntryCompatibilityPatcher.reads(method)) {
            if (!reader.name.equals(donor ? "readShort" : "readByte")) continue;
            AbstractInsnNode n = next(reader);
            if (!donor) { if (n == null || n.getOpcode() != I2S) continue; n = next(n); }
            boolean retainedCapacity = n != null && n.getOpcode() == DUP;
            if (retainedCapacity) n = next(n);
            if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTSTATIC || !((FieldInsnNode)n).desc.equals("S")) continue;
            FieldInsnNode capacity = (FieldInsnNode)n;
            n = next(n);
            if (!retainedCapacity) {
                if (!(n instanceof FieldInsnNode) || n.getOpcode() != GETSTATIC || !same(capacity, (FieldInsnNode)n)) continue;
                n = next(n);
            }
            if (donor) { if (integer(n) != 41 || next(n).getOpcode() != IADD) continue; n = next(next(n)); }
            if (integer(n) != 42) continue;
            AbstractInsnNode divisor = n; n = next(n);
            if (n == null || n.getOpcode() != IDIV) continue; n = next(n);
            if (n == null || n.getOpcode() != I2B) continue; n = next(n);
            if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTSTATIC || !((FieldInsnNode)n).desc.equals("B")) continue;
            check(found == null, "Ambiguous self capacity/page site");
            found = new Site(); found.reader = reader; found.capacity = capacity; found.pages = (FieldInsnNode)n; found.divisor = divisor;
        }
        check(found != null, "Expected self capacity/page sequence absent"); return found;
    }
    static void field(Properties p, String prefix, FieldInsnNode f) { p.setProperty(prefix + ".owner", f.owner); p.setProperty(prefix + ".name", f.name); p.setProperty(prefix + ".desc", f.desc); }
    static boolean same(FieldInsnNode a, FieldInsnNode b) { return a.owner.equals(b.owner) && a.name.equals(b.name) && a.desc.equals(b.desc); }
    static int integer(AbstractInsnNode n) { if (n instanceof IntInsnNode) return ((IntInsnNode)n).operand; if (n instanceof LdcInsnNode && ((LdcInsnNode)n).cst instanceof Integer) return ((Integer)((LdcInsnNode)n).cst).intValue(); return Integer.MIN_VALUE; }
    static AbstractInsnNode next(AbstractInsnNode n) { return ChibiEntryCompatibilityPatcher.next(n); }
    static void check(boolean ok, String message) { ChibiEntryCompatibilityPatcher.check(ok, message); }
}
