/**
 * Compile-time stub. ModUpLvSkillStatsPatcher replaces it with a generated class of the same
 * name and signatures whose bodies read/call ModUpLv's obfuscated members (resolved from the
 * reviewed K### aliases), so the readable support code never names them.
 */
public final class ModUpLvStatsAccess {
    private ModUpLvStatsAccess() { }
    private static RuntimeException stub() { return new IllegalStateException("ModUpLvStatsAccess stub"); }

    /** Tấn công đặc biệt (K154 flag). */
    public static boolean special() { throw stub(); }
    /** Local player id, or Integer.MIN_VALUE before login. */
    public static int localId() { throw stub(); }
    /** Skill template name, or null. */
    public static String skillName(int skill) { throw stub(); }
    /** Native message dialog with one "Ok" (-1) button, made the active dialog. */
    public static Object openDialog(String text) { throw stub(); }
    public static Object activeDialog() { throw stub(); }
    public static int dialogX(Object dialog) { throw stub(); }
    public static int dialogY(Object dialog) { throw stub(); }
    public static int dialogWidth(Object dialog) { throw stub(); }
    public static int dialogMode(Object dialog) { throw stub(); }
    public static void setDialogBox(Object dialog, int x, int width) { throw stub(); }
    public static int screenWidth() { throw stub(); }
    public static int screenHeight() { throw stub(); }
    public static int screenCenter() { throw stub(); }
    public static int screenMargin() { throw stub(); }
    /** Fixed dialog height added to 15px per text line. */
    public static int dialogBase() { throw stub(); }
    public static void resetClip(Object graphics) { throw stub(); }
    public static void fill(Object graphics, int color, int x, int y, int width, int height) { throw stub(); }
    /** font: 0 white, 1 yellow, 2 red, 3 green (tahoma 7); align 0 left, 1 right, 2 center. */
    public static void text(Object graphics, int font, String text, int x, int y, int align) { throw stub(); }
    public static int textWidth(int font, String text) { throw stub(); }
}
