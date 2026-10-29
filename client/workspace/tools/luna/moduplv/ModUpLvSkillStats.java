import java.io.DataInputStream;
import luna.features.SkillCastStats;

/**
 * "Chi tiết tung chiêu" for ModUpLv, measured exactly like Hung_By_Son_copy_special_skill so the
 * two clients can be compared (docs/HUNG_BY_SON_MODUPLV_PORT.md):
 *  - sent(): every attack request (opcodes 9 and 6) while Tấn công đặc biệt is ON is one cast;
 *  - combat(): the local player's combat answer is matched to the oldest unanswered cast of its
 *    skill: hit when a target takes damage, miss when every damage is 0 or no answer within 2s;
 *  - frame(): the counters restart whenever Tấn công đặc biệt turns ON;
 *  - menu "Tan cong / muc tieu" > "Chi tiết tung chiêu" (action 17/20) opens a native Ok dialog
 *    whose blank lines carry the table ID / Tên chiêu / Tung / Hụt / Trúng, drawn by paint().
 * Native access goes through ModUpLvStatsAccess (generated against the obfuscated names).
 */
public final class ModUpLvSkillStats {
    static final int MENU_ACTION = 20;
    static final int ROW = 15, TOP = 12, MARGIN = 8, MAX_WIDTH = 240;
    static final int WHITE = 0, YELLOW = 1, RED = 2, GREEN = 3;
    static final long REFRESH_MS = 250L;
    static final String TITLE = "Chi tiết tung chiêu";

    static boolean special, table;
    static Object dialog;
    static int shownRows, shownVersion = -1;
    static long builtAt;
    static int[] ids = new int[0];
    static String[] names = new String[0], cast = new String[0], miss = new String[0], rate = new String[0];
    static String totalCast = "", totalMiss = "", totalRate = "";
    static long testNow;

    private ModUpLvSkillStats() {
    }

    static long now() {
        return testNow != 0L ? testNow : System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ hooks

    /** Attack manager, every update: a fresh session each time Special turns ON. */
    public static void frame() {
        try {
            boolean on = ModUpLvStatsAccess.special();
            if (on && !special) SkillCastStats.reset();
            special = on;
        } catch (Throwable ignored) {
        }
    }

    /** Serializer prefix of the opcode-9 and opcode-6 attack requests. */
    public static void sent(int skill) {
        try {
            if (ModUpLvStatsAccess.special()) SkillCastStats.sent(skill, now());
        } catch (Throwable ignored) {
        }
    }

    /** Combat answer prefix: attacker, skill, targets (id, damage, hp, effects); the stream is reset. */
    public static void combat(DataInputStream in) {
        if (in == null || !in.markSupported()) return;
        in.mark(65536);
        try {
            int attacker = in.readShort();
            int skill = in.readByte();
            int count = in.readByte();
            if (attacker != ModUpLvStatsAccess.localId() || count <= 0) return;
            boolean damaged = false;
            for (int i = 0; i < count; i++) {
                in.readShort();
                if (in.readInt() > 0) damaged = true;
                in.readInt();
                int effects = in.readByte();
                for (int k = 0; k < effects; k++) { in.readByte(); in.readInt(); }
            }
            SkillCastStats.answered(skill, damaged, now());
        } catch (Throwable ignored) {
        } finally {
            try { in.reset(); } catch (Throwable ignored) { }
        }
    }

    /** "Tan cong / muc tieu" row handler prefix; true when the row was ours. */
    public static boolean menu(int action) {
        if (action != MENU_ACTION) return false;
        try { open(); } catch (Throwable ignored) { }
        return true;
    }

    // ------------------------------------------------------------------ dialog

    static void open() {
        build(now());
        int rows = rows();
        StringBuffer text = new StringBuffer(TITLE);
        if (ids.length == 0) text.append("\n\nChưa có lượt tung chiêu nào.\nBật tấn công đặc biệt để bắt đầu đếm.");
        else for (int i = 0; i < rows; i++) text.append('\n');
        dialog = ModUpLvStatsAccess.openDialog(text.toString());
        int width = ModUpLvStatsAccess.screenWidth() - 8;
        if (width > MAX_WIDTH) width = MAX_WIDTH;
        if (width > ModUpLvStatsAccess.dialogWidth(dialog))
            ModUpLvStatsAccess.setDialogBox(dialog, ModUpLvStatsAccess.screenCenter() - width / 2, width);
        shownRows = rows;
        table = ids.length > 0;
    }

    /** Title + header + skills that fit on screen + total. */
    static int rows() {
        int fit = (ModUpLvStatsAccess.screenHeight() - (ModUpLvStatsAccess.screenMargin() << 1) - 5
                - ModUpLvStatsAccess.dialogBase() - TOP) / ROW;
        return Math.min(ids.length, Math.max(1, fit - 3)) + 3;
    }

    static void build(long now) {
        SkillCastStats.expire(now);
        shownVersion = SkillCastStats.version();
        builtAt = now;
        int[] skills = SkillCastStats.skills();
        int n = skills.length;
        String[] name = new String[n], c = new String[n], m = new String[n], r = new String[n];
        int casts = 0, hits = 0, misses = 0;
        for (int i = 0; i < n; i++) {
            int s = skills[i], sc = SkillCastStats.casts(s), sh = SkillCastStats.hits(s), sm = SkillCastStats.misses(s);
            casts += sc; hits += sh; misses += sm;
            name[i] = skillName(s);
            c[i] = String.valueOf(sc);
            m[i] = String.valueOf(sm);
            r[i] = SkillCastStats.rate(sh, sm);
        }
        ids = skills; names = name; cast = c; miss = m; rate = r;
        totalCast = String.valueOf(casts);
        totalMiss = String.valueOf(misses);
        totalRate = SkillCastStats.rate(hits, misses);
    }

    static String skillName(int skill) {
        try {
            String name = ModUpLvStatsAccess.skillName(skill);
            if (name != null && name.length() > 0) return name;
        } catch (Throwable ignored) {
        }
        return new StringBuffer("Chiêu ").append(skill).toString();
    }

    // ------------------------------------------------------------------ paint

    /** After the active dialog paints: the table over our dialog's blank lines, refreshed every 250ms. */
    public static void paint(Object g) {
        try {
            Object d = dialog;
            if (d == null || g == null) return;
            if (ModUpLvStatsAccess.activeDialog() != d) { dialog = null; return; }
            if (!table || ModUpLvStatsAccess.dialogMode(d) != 0) return;
            long now = now();
            if (now < builtAt || now - builtAt >= REFRESH_MS) {
                SkillCastStats.expire(now);
                if (SkillCastStats.version() != shownVersion) build(now); else builtAt = now;
            }
            ModUpLvStatsAccess.resetClip(g);
            table(g, d);
        } catch (Throwable ignored) {
        }
    }

    static void table(Object g, Object d) {
        int x = ModUpLvStatsAccess.dialogX(d), w = ModUpLvStatsAccess.dialogWidth(d);
        int left = x + MARGIN + 2, right = x + w - MARGIN - 2;
        int rateX = right, missX = right - 44, castX = missX - 34, nameX = left + 20;
        int nameMax = castX - 30 - nameX;
        int y = ModUpLvStatsAccess.dialogY(d) + TOP + ROW;
        ModUpLvStatsAccess.text(g, YELLOW, "ID", left, y, 0);
        ModUpLvStatsAccess.text(g, YELLOW, "Tên chiêu", nameX, y, 0);
        ModUpLvStatsAccess.text(g, YELLOW, "Tung", castX, y, 1);
        ModUpLvStatsAccess.text(g, YELLOW, "Hụt", missX, y, 1);
        ModUpLvStatsAccess.text(g, YELLOW, "Trúng", rateX, y, 1);
        int rows = Math.min(ids.length, shownRows - 3);
        for (int i = 0; i < rows; i++) {
            y += ROW;
            ModUpLvStatsAccess.text(g, WHITE, String.valueOf(ids[i]), left, y, 0);
            ModUpLvStatsAccess.text(g, WHITE, fit(names[i], nameMax), nameX, y, 0);
            ModUpLvStatsAccess.text(g, WHITE, cast[i], castX, y, 1);
            ModUpLvStatsAccess.text(g, RED, miss[i], missX, y, 1);
            ModUpLvStatsAccess.text(g, GREEN, rate[i], rateX, y, 1);
        }
        y += ROW;
        ModUpLvStatsAccess.fill(g, 0x6E5A3C, left, y - 2, right - left, 1);
        ModUpLvStatsAccess.text(g, YELLOW, "Tổng", nameX, y, 0);
        ModUpLvStatsAccess.text(g, YELLOW, totalCast, castX, y, 1);
        ModUpLvStatsAccess.text(g, YELLOW, totalMiss, missX, y, 1);
        ModUpLvStatsAccess.text(g, YELLOW, totalRate, rateX, y, 1);
    }

    static String fit(String text, int width) {
        if (text == null || ModUpLvStatsAccess.textWidth(WHITE, text) <= width) return text;
        for (int k = text.length() - 1; k > 0; k--) {
            String cut = new StringBuffer(text.substring(0, k)).append("..").toString();
            if (ModUpLvStatsAccess.textWidth(WHITE, cut) <= width) return cut;
        }
        return "..";
    }
}
