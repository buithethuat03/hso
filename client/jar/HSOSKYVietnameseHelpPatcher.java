import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Changes only the user-facing help text in the tested SERVERCD build. */
public final class HSOSKYVietnameseHelpPatcher {
    private static final String INPUT =
            "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_REMOTEZONE_SERVERCD_LOOTTP_NOCLIP.jar";
    private static final String OUTPUT =
            "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_REMOTEZONE_SERVERCD_LOOTTP_NOCLIP_VIHELP_DEFAULT37.jar";
    private static final String TARGET = "HSOSKYPetFeedSupport.class";
    private static final String HELP = "LỆNH MOD\n"
            + "3: Pet ăn đồ Vàng và Tím (mặc định)\n"
            + "4: Pet ăn thêm đồ Cam, giữ đồ Cam Xạ Thủ\n"
            + "5: Pet ăn mọi đồ Vàng, Tím và Cam\n"
            + "6: Tàn sát Quái và Người chơi đối địch\n"
            + "7: Chỉ tàn sát Quái (mặc định)\n"
            + "help: Hiện danh sách lệnh";

    private HSOSKYVietnameseHelpPatcher() {
    }

    public static void main(String[] args) throws Exception {
        File input = new File(INPUT);
        File output = new File(OUTPUT);
        require(input.isFile(), "Không tìm thấy bản nguồn: " + INPUT);
        require(!output.exists(), "Không ghi đè bản đã có: " + OUTPUT);

        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        Map<String, Long> times = new LinkedHashMap<String, Long>();
        try (JarFile jar = new JarFile(input)) {
            Enumeration<JarEntry> all = jar.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                entries.put(entry.getName(), readAll(jar.getInputStream(entry)));
                times.put(entry.getName(), Long.valueOf(entry.getTime()));
            }
        }

        byte[] original = entries.get(TARGET);
        require(original != null, "Thiếu class lệnh chat: " + TARGET);
        byte[] patched = patchHelp(original);
        entries.put(TARGET, patched);

        File temporary = new File(OUTPUT + ".tmp");
        require(!temporary.exists(), "Tệp tạm đã tồn tại: " + temporary.getName());
        try {
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(temporary.toPath()))) {
                for (Map.Entry<String, byte[]> item : entries.entrySet()) {
                    JarEntry entry = new JarEntry(item.getKey());
                    Long time = times.get(item.getKey());
                    if (time != null && time.longValue() >= 0L) {
                        entry.setTime(time.longValue());
                    }
                    out.putNextEntry(entry);
                    if (!item.getKey().endsWith("/")) {
                        out.write(item.getValue());
                    }
                    out.closeEntry();
                }
            }
            verify(input, temporary, original, patched);
            Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }

        System.out.println("[+] Đã tạo " + OUTPUT);
        System.out.println("    Chỉ thay đổi: " + TARGET);
        System.out.println("    Nội dung help:\n" + HELP);
    }

    private static byte[] patchHelp(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int replacements = 0;
        for (MethodNode method : node.methods) {
            if (!"handleChat".equals(method.name) || !"(Ljava/lang/String;)Z".equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                    instruction = instruction.getNext()) {
                if (instruction instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) instruction;
                    if (ldc.cst instanceof String && ((String) ldc.cst).startsWith("LENH MOD\n")) {
                        ldc.cst = HELP;
                        replacements++;
                    }
                }
            }
        }
        require(replacements == 1, "Số chuỗi help được thay không đúng: " + replacements);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void verify(File input, File output, byte[] original, byte[] patched) throws Exception {
        require(!java.util.Arrays.equals(original, patched), "Class help không thay đổi");
        ClassNode node = new ClassNode();
        new ClassReader(patched).accept(node, 0);
        int exactHelpStrings = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                    instruction = instruction.getNext()) {
                if (instruction instanceof LdcInsnNode && HELP.equals(((LdcInsnNode) instruction).cst)) {
                    exactHelpStrings++;
                }
            }
        }
        require(exactHelpStrings == 1, "Không xác minh được chuỗi help tiếng Việt");

        try (JarFile before = new JarFile(input); JarFile after = new JarFile(output)) {
            require(after.getManifest() != null, "JAR đầu ra không có MANIFEST");
            require(before.size() == after.size(), "Số entry thay đổi: " + before.size() + " -> " + after.size());
            Enumeration<JarEntry> all = before.entries();
            int changed = 0;
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                JarEntry counterpart = after.getJarEntry(entry.getName());
                require(counterpart != null, "Thiếu entry: " + entry.getName());
                byte[] left = readAll(before.getInputStream(entry));
                byte[] right = readAll(after.getInputStream(counterpart));
                if (!java.util.Arrays.equals(left, right)) {
                    changed++;
                    require(TARGET.equals(entry.getName()), "Entry ngoài dự kiến bị đổi: " + entry.getName());
                }
            }
            require(changed == 1, "Số entry thay đổi không đúng: " + changed);
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) >= 0) {
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
