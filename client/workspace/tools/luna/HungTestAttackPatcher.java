import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Hung_test_attack (2026-10-29): answer-anchored "Đáp"/Auto pacing on top of a released Hung_By_Son
 * (INPUTS). Replaces luna/features/SkillCastStats.class with the recompiled source and, inside the
 * released HungBySonModUpLv, only frame()V, frameSleep(J)J and attackFrame(player)Z with the
 * methods compiled from the template; every other member and entry stays byte-identical.
 * With the file log (Hung_test_attack v2): also sent(I)V and footer(font, width) of the released
 * HungBySonSkillStats from its file-log template variant, plus luna/features/SkillCastLog.class.
 * Args: input.jar compiledPort.class SkillCastStats.class [fileLogStats.class SkillCastLog.class] output.jar
 */
public final class HungTestAttackPatcher {
    // Released inputs: 46C6C77F (Khóa 600/650/700ms), 56454EF0 (Đáp 400ms default), 879B7F69 (Đáp 400+
    // default), C33DF6F2 (Khóa 575ms default).
    static final String[] INPUTS = {"46C6C77F798F3C33A770DA16822C716AA8711029ED9A1FD5DB3594B27AB97F04",
            "56454EF0A5BB0812B72E949D96D7105F1318FBA9AFA915D7C3F21F5151F5E127",
            "879B7F69E69B24C425D1D71D6D26D331FB3DCA3757B5BBB04DD290E85AF4D494",
            "C33DF6F29581F3FB235C19FF1A4345B49CB6BA4BB4252E11D7BE54132501FDF9"};
    static final String PORT = "HungBySonModUpLv.class", COUNTERS = "luna/features/SkillCastStats.class",
            STATS = "HungBySonSkillStats.class", FILE_LOG = "luna/features/SkillCastLog.class";

    static void check(boolean value, String why) { if (!value) throw new IllegalStateException(why); }

    /** The 100-character obfuscated name ending in suffix. */
    static String n(String suffix) {
        StringBuilder b = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) b.append('i');
        return b.append(suffix).toString();
    }

    static String hash(byte[] data) throws Exception {
        StringBuilder b = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(data)) b.append(String.format("%02X", x & 255));
        return b.toString();
    }

    static ClassNode node(byte[] bytes) {
        ClassNode c = new ClassNode();
        new ClassReader(bytes).accept(c, ClassReader.SKIP_FRAMES);
        return c;
    }

    static int indexOf(ClassNode c, String name, String desc) {
        for (int i = 0; i < c.methods.size(); i++) if (c.methods.get(i).name.equals(name) && c.methods.get(i).desc.equals(desc)) return i;
        return -1;
    }

    // Members the template may add to the released port (Đáp 400+ test: đòn 0's target moved behind).
    static final Set<String> PORT_ADDITIONS = new HashSet<String>(Arrays.asList(
            "ROTATE_MS J", "zeroSet Z", "zeroTarget S", "zeroAt J", "behind(Let;S)Let;"));

    /**
     * The released class with the named methods taken from the compiled template; members only in
     * the template must be listed in `additions` and are added, every other member stays as released.
     */
    static byte[] swap(byte[] released, byte[] template, String[][] methods, Set<String> additions, String what) {
        ClassNode c = node(released), compiled = node(template);
        int addedFields = 0, addedMethods = 0;
        for (FieldNode f : compiled.fields) {
            boolean known = false;
            for (FieldNode r : c.fields) known |= r.name.equals(f.name) && r.desc.equals(f.desc);
            if (known) continue;
            check(additions.contains(f.name + " " + f.desc), "Unexpected new field " + what + "." + f.name);
            c.fields.add(f); addedFields++;
        }
        for (MethodNode m : compiled.methods) {
            if (indexOf(c, m.name, m.desc) >= 0) continue;
            check(additions.contains(m.name + m.desc), "Unexpected new method " + what + "." + m.name + m.desc);
            c.methods.add(m); addedMethods++;
        }
        check(c.methods.size() == compiled.methods.size() && c.fields.size() == compiled.fields.size(), "Template topology matches the released " + what);
        for (String[] m : methods) {
            int at = indexOf(c, m[0], m[1]), from = indexOf(compiled, m[0], m[1]);
            check(at >= 0 && from >= 0, "Method " + what + "." + m[0] + m[1]);
            c.methods.set(at, compiled.methods.get(from));
        }
        for (MethodNode m : c.methods) for (AbstractInsnNode x : m.instructions) check(x.getOpcode() != Opcodes.INVOKEDYNAMIC, "invokedynamic");
        if (addedFields + addedMethods > 0) System.out.println("Added to " + what + ": " + addedFields + " fields, " + addedMethods + " methods");
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(w);
        return w.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 4 || args.length == 6, "input.jar compiledPort.class SkillCastStats.class [fileLogStats.class SkillCastLog.class] output.jar");
        boolean fileLog = args.length == 6;
        byte[] input = Files.readAllBytes(new File(args[0]).toPath());
        check(Arrays.asList(INPUTS).contains(hash(input)), "Pinned input Hung_By_Son 46C6C77F, 56454EF0, 879B7F69 or C33DF6F2");
        File output = new File(args[args.length - 1]);
        check(!output.exists(), "Fresh output");

        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        Map<String, ZipEntry> meta = new HashMap<String, ZipEntry>();
        ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(input));
        try {
            for (ZipEntry e; (e = in.getNextEntry()) != null;) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0;) b.write(buf, 0, n);
                entries.put(e.getName(), b.toByteArray());
                meta.put(e.getName(), e);
            }
        } finally { in.close(); }
        check(entries.containsKey(PORT) && entries.containsKey(COUNTERS) && entries.containsKey(STATS) && !entries.containsKey(FILE_LOG),
                "Input has the port, the counters and the stats UI, no file log");

        entries.put(PORT, swap(entries.get(PORT), Files.readAllBytes(new File(args[1]).toPath()),
                new String[][]{{"frame", "()V"}, {"frameSleep", "(J)J"}, {"attackFrame", "(L" + n("lliiil") + ";)Z"}}, PORT_ADDITIONS, "port"));
        entries.put(COUNTERS, Files.readAllBytes(new File(args[2]).toPath()));
        if (fileLog) {
            entries.put(STATS, swap(entries.get(STATS), Files.readAllBytes(new File(args[3]).toPath()),
                    new String[][]{{"sent", "(I)V"}, {"footer", "(L" + n("lilillil") + ";I)Ljava/lang/String;"}}, new HashSet<String>(), "stats"));
            entries.put(FILE_LOG, Files.readAllBytes(new File(args[4]).toPath()));
            meta.put(FILE_LOG, meta.get(COUNTERS));
        }

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry old = meta.get(e.getKey()), z = new ZipEntry(e.getKey());
                z.setTime(old.getTime());
                out.putNextEntry(z);
                out.write(e.getValue());
                out.closeEntry();
            }
        } finally { out.close(); }
        System.out.println("HungTestAttackPatcher wrote " + output + " SHA-256 " + hash(Files.readAllBytes(output.toPath())));
    }
}
