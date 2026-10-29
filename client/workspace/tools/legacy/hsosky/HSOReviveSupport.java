import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Chết thì về làng ngay, rồi tự đi lên phía bắc.
 *
 * Bật tắt bằng chat: gõ "0" để bật, "1" để tắt. Vào game mặc định là "1", tức
 * tắt. Hai lệnh đó không được gửi lên server, chúng bị chặn ngay tại client.
 *
 * Khi bật, mỗi menu server gửi xuống (opcode -30) đều được soi: nếu có lựa chọn
 * nào chứa "làng" thì trả lời ngay tại thread đọc socket, nên không có độ trễ
 * đáng kể. Menu lúc chết do server sinh ra chứ không nằm trong jar, nên bắt theo
 * nội dung là cách duy nhất chắc chắn.
 *
 * Sau khi đã chọn về làng, mỗi giây gửi một bước đi lên phía bắc (y giảm).
 *
 * Cấu hình trong hso_mirror.cfg:
 *   reviveKeyword=làng     từ khoá nhận biết lựa chọn về làng
 *   northStep=8            số pixel mỗi bước
 *   northPeriodMs=1000     nhịp bước
 *   reviveLog=hso_revive.log
 *
 * Mọi lối vào đều nuốt Throwable: mod hỏng thì game vẫn phải chạy.
 */
public final class HSOReviveSupport {
    private static final int MENU_OPCODE = -30;
    private static final String CONFIG_FILE = "hso_mirror.cfg";

    private static boolean loaded;
    private static boolean enabled;
    private static String keyword = "làng";
    private static int stepCount = 5;
    private static int step = 70;
    private static int tapDx;
    private static int tapDy = -60;
    private static boolean dumpFields;
    private static int[] lastInts;
    private static long periodMs = 1000L;
    private static long pollMs = 120L;
    private static volatile int lastHp = -1;
    private static OutputStream log;

    private static HSOBridgeApi bridge;
    private static boolean bridgeReady;

    private HSOReviveSupport() {
    }

    /** Trả về true nghĩa là đã xử lý như lệnh, không gửi câu chat lên server. */
    public static boolean onChat(String text) {
        try {
            init();
            String value = text == null ? "" : text.trim();
            if ("0".equals(value)) {
                enabled = true;
                note("BẬT: chết sẽ về làng ngay, sau đó đi bắc " + stepCount + " bước, mỗi "
                        + periodMs + "ms");
                return true;
            }
            if ("1".equals(value)) {
                enabled = false;
                note("TẮT");
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static void onRecv(int opcode, byte[] payload) {
        try {
            init();
            if (payload == null) {
                return;
            }
            if (opcode == MENU_OPCODE) {
                onMenu(payload);
                return;
            }
            // Nếu menu lúc chết đi bằng opcode khác thì dòng này chỉ ra ngay.
            if (enabled && contains(payload, utf8(keyword))) {
                note("chú ý: opcode " + opcode + " (" + payload.length + " byte) có chứa \""
                        + keyword + "\"");
            }
        } catch (Throwable ignored) {
        }
    }

    /** [npcId:short][menuId:byte][soLuaChon:byte][nhãn:UTF ...][tiêu đề:UTF] */
    private static void onMenu(byte[] payload) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int npc = in.readShort();
            int menu = in.readByte();
            int count = in.readByte() & 0xFF;
            String[] labels = new String[count];
            StringBuffer listed = new StringBuffer();
            for (int i = 0; i < count; i++) {
                labels[i] = in.readUTF();
                if (i > 0) {
                    listed.append(" | ");
                }
                listed.append(i).append('=').append(labels[i]);
            }
            String title = in.available() > 0 ? in.readUTF() : "";
            note("menu npc=" + npc + " id=" + menu + " tiêu đề=\"" + title + "\"  [" + listed + "]");

            if (!enabled) {
                return;
            }
            int chosen = -1;
            for (int i = 0; i < count; i++) {
                if (matches(labels[i])) {
                    chosen = i;
                    break;
                }
            }
            if (chosen < 0) {
                return;
            }
            if (!bridgeReady) {
                note("không gọi được bridge, bỏ qua menu này");
                return;
            }
            bridge.menuReply(npc, menu, chosen);
            note("đã chọn \"" + labels[chosen] + "\" (index " + chosen + ")");
        } catch (Throwable failure) {
            note("đọc menu lỗi: " + failure);
        }
    }

    private static boolean matches(String label) {
        if (label == null) {
            return false;
        }
        String plain = strip(label);
        return plain.indexOf(strip(keyword)) >= 0;
    }

    /** Bỏ dấu tiếng Việt để so khớp không phụ thuộc cách server viết hoa hay có dấu. */
    private static String strip(String value) {
        String source = "àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ";
        String target = "aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooooouuuuuuuuuuuyyyyyd";
        StringBuffer text = new StringBuffer(value.length());
        String lower = value.toLowerCase();
        for (int i = 0; i < lower.length(); i++) {
            char letter = lower.charAt(i);
            int at = source.indexOf(letter);
            text.append(at < 0 ? letter : target.charAt(at));
        }
        return text.toString();
    }

    private static synchronized void init() {
        if (loaded) {
            return;
        }
        loaded = true;

        Properties config = new Properties();
        try {
            File file = new File(CONFIG_FILE);
            if (file.isFile()) {
                InputStream in = new FileInputStream(file);
                try {
                    // load(InputStream) đọc theo ISO-8859-1, sẽ làm hỏng chữ có dấu.
                    config.load(new java.io.InputStreamReader(in, "UTF-8"));
                } finally {
                    in.close();
                }
            }
        } catch (Throwable ignored) {
        }
        keyword = config.getProperty("reviveKeyword", keyword).trim();
        step = number(config.getProperty("northStep"), step);
        tapDx = number(config.getProperty("northTapDx"), tapDx);
        tapDy = number(config.getProperty("northTapDy"), tapDy);
        dumpFields = "1".equals(config.getProperty("dumpFields", "0").trim());
        periodMs = number(config.getProperty("northPeriodMs"), (int) periodMs);
        pollMs = number(config.getProperty("revivePollMs"), (int) pollMs);
        stepCount = number(config.getProperty("northSteps"), stepCount);

        String logName = config.getProperty("reviveLog", "hso_revive.log").trim();
        try {
            log = new BufferedOutputStream(new FileOutputStream(logName, true), 4096);
        } catch (Throwable ignored) {
        }
        note("khởi động, mặc định TẮT (chat \"0\" để bật), soi HP mỗi " + pollMs
                + "ms, đi bắc " + stepCount + " bước x " + step + "px, nhịp " + periodMs + "ms");

        try {
            // Chỉ một lần dùng reflection để tạo đối tượng; mọi lời gọi sau đó đi
            // qua interface, vì reflection lặp lại sẽ nổ trong emulator.
            bridge = (HSOBridgeApi) Class.forName("HSOReviveBridge").newInstance();
            bridgeReady = true;
        } catch (Throwable failure) {
            note("không nạp được HSOReviveBridge: " + failure);
        }

        try {
            spawn(new Walker(), "hso-revive-walker");
        } catch (Throwable failure) {
            note("không chạy được luồng đi bắc: " + failure);
        }
    }

    private static int number(String value, int fallback) {
        try {
            if (value != null && value.trim().length() > 0) {
                return Integer.parseInt(value.trim());
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    /**
     * Tạo luồng không viết "new Thread" vào bytecode: giả lập sẽ đổi constructor
     * đó thành MIDletThread của nó.
     */
    private static void spawn(Runnable body, String name) throws Exception {
        Class<?> type = Class.forName("java.lang.Thread");
        Object thread = type.getConstructor(new Class[] { Runnable.class, String.class })
                .newInstance(new Object[] { body, name });
        type.getMethod("setDaemon", new Class[] { Boolean.TYPE })
                .invoke(thread, new Object[] { Boolean.TRUE });
        type.getMethod("start", new Class[0]).invoke(thread, new Object[0]);
    }

    /**
     * Soi HP mỗi pollMs: HP vừa tụt xuống 0 là chết, gửi ngay opcode 31 payload 00
     * tức "Về làng" (payload 01 là hồi sinh tại chỗ, tốn ngọc, nên không dùng).
     * Sau khi về làng thì mỗi periodMs gửi một bước lên phía bắc.
     */
    /**
     * Soi HP mỗi pollMs. HP vừa tụt xuống 0 là chết, gửi ngay opcode 31 payload 00
     * tức "Về làng" (payload 01 là hồi sinh tại chỗ, tốn ngọc, không dùng).
     *
     * Về làng xong, đợi HP hồi lại rồi mới đi: mỗi periodMs một bước lên phía bắc
     * (y giảm), tối đa stepCount bước rồi dừng.
     */
    private static final class Walker implements Runnable {
        private boolean awaitingRespawn;
        private int stepsLeft;
        private long nextStepAt;

        public void run() {
            while (true) {
                try {
                    Thread.sleep(pollMs);
                } catch (InterruptedException stop) {
                    return;
                }
                try {
                    if (!bridgeReady) {
                        continue;
                    }
                    if (dumpFields) {
                        dump();
                    }
                    int hp = bridge.hp();
                    if (enabled && lastHp > 0 && hp <= 0) {
                        bridge.revive();
                        awaitingRespawn = true;
                        stepsLeft = stepCount;
                        note("HP " + lastHp + " -> " + hp + ", đã gửi về làng ngay");
                    }
                    lastHp = hp;
                    if (!enabled) {
                        continue;
                    }
                    if (awaitingRespawn) {
                        if (hp <= 0) {
                            continue;
                        }
                        awaitingRespawn = false;
                        nextStepAt = System.currentTimeMillis() + periodMs;
                        note("đã về làng, HP=" + hp + ", chuẩn bị đi bắc " + stepsLeft + " bước");
                    }
                    if (stepsLeft <= 0) {
                        continue;
                    }
                    long now = System.currentTimeMillis();
                    if (now < nextStepAt) {
                        continue;
                    }
                    nextStepAt = now + periodMs;
                    // Opcode 4 là báo cáo vị trí, không phải xin đi. Nên đặt toạ độ
                    // ở client trước rồi mới báo lên, giống cách client tự làm khi
                    // người chơi bấm phím.
                    int x = bridge.px();
                    int y = bridge.py();
                    if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE) {
                        note("chưa đọc được toạ độ, bỏ bước này");
                        continue;
                    }
                    int targetY = y - step;
                    --stepsLeft;
                    bridge.setPosition(x, targetY);
                    bridge.move(x, targetY);
                    note("bước " + (stepCount - stepsLeft) + "/" + stepCount + ": (" + x + ","
                            + y + ") -> (" + x + "," + targetY + "), đọc lại được ("
                            + bridge.px() + "," + bridge.py() + ")"
                            + (stepsLeft == 0 ? "  [hết bước, dừng]" : ""));
                } catch (Throwable failure) {
                    note("vòng soi HP lỗi: " + failure);
                }
            }
        }
    }

    /**
     * Ghi ra những field int của nhân vật vừa đổi giá trị. Đi vài bước rồi xem log
     * là biết field nào là toạ độ, đối chiếu số với toạ độ trong hso_master.log.
     */
    private static long nextDumpAt;

    private static void dump() {
        long now = System.currentTimeMillis();
        if (now < nextDumpAt) {
            return;
        }
        nextDumpAt = now + 1000L;
        int[] values = bridge.ints();
        if (values.length == 0) {
            return;
        }
        if (lastInts == null || lastInts.length != values.length) {
            lastInts = values;
            StringBuffer all = new StringBuffer("dump lần đầu, " + values.length + " field:");
            for (int i = 0; i < values.length; i++) {
                all.append(' ').append(i).append('=').append(values[i]);
            }
            note(all.toString());
            return;
        }
        StringBuffer changed = new StringBuffer();
        for (int i = 0; i < values.length; i++) {
            if (values[i] != lastInts[i]) {
                changed.append(' ').append(i).append(':').append(lastInts[i]).append("->").append(values[i]);
            }
        }
        lastInts = values;
        if (changed.length() > 0) {
            note("field đổi:" + changed);
        }
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) {
            return false;
        }
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            int j = 0;
            while (j < needle.length && haystack[i + j] == needle[j]) {
                ++j;
            }
            if (j == needle.length) {
                return true;
            }
        }
        return false;
    }

    private static byte[] utf8(String value) {
        try {
            return value.getBytes("UTF-8");
        } catch (Throwable ignored) {
            return value.getBytes();
        }
    }

    private static synchronized void note(String line) {
        try {
            if (log != null) {
                log.write(utf8(System.currentTimeMillis() + " " + line + "\n"));
                log.flush();
            }
        } catch (Throwable ignored) {
        }
    }
}
