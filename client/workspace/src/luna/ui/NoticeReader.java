package luna.ui;

/** Small semantic text helper shared by menu, shop, and notification logic. */
public final class NoticeReader {

    private NoticeReader() { }

    public static boolean containsIgnoreCase(String text, String phrase) {
        if (text == null || phrase == null) return false;
        return text.toLowerCase().indexOf(phrase.toLowerCase()) >= 0;
    }

    public static boolean isSoldOut(String text) {
        return containsIgnoreCase(text, "đã bán hết")
                || containsIgnoreCase(text, "da ban het");
    }

    public static boolean isConnectionNotice(String text) {
        return containsIgnoreCase(text, "mất kết nối")
                || containsIgnoreCase(text, "mat ket noi")
                || containsIgnoreCase(text, "kết nối lại")
                || containsIgnoreCase(text, "ket noi lai");
    }

    /** Server cooldown notice sent after a logout/reconnect attempt. */
    public static boolean isLoginCooldownNotice(String text) {
        if (text == null || !containsIgnoreCase(text, "sau ")) return false;
        return containsIgnoreCase(text, "mới có thể vào")
                || containsIgnoreCase(text, "mới được vào")
                || containsIgnoreCase(text, "moi co the vao")
                || containsIgnoreCase(text, "moi duoc vao")
                || containsIgnoreCase(text, "có thể vào")
                || containsIgnoreCase(text, "co the vao");
    }

    /** Reads the remaining seconds from a dot/comma-decimal cooldown notice. */
    public static long loginCooldownDelayMillis(String text) {
        if (!isLoginCooldownNotice(text)) return -1L;
        String lower = text.toLowerCase();
        int start = lower.indexOf("sau ") + 4;
        int end = start;
        while (end < lower.length()) {
            char c = lower.charAt(end);
            if (!((c >= '0' && c <= '9') || c == '.' || c == ',')) break;
            end++;
        }
        if (end == start) return -1L;
        String number = lower.substring(start, end).replace(',', '.');
        try {
            float seconds = Float.parseFloat(number);
            if (seconds < 0.0f || seconds > 120.0f) return -1L;
            long delay = (long) (seconds * 1000.0f) + 500L;
            return delay < 500L ? 500L : delay;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    public static boolean isHorseItemUnavailable(String text) {
        return containsIgnoreCase(text, "không thể sử dụng vật phẩm này")
                || containsIgnoreCase(text, "khong the su dung vat pham nay");
    }

    public static boolean isOtherMountInUse(String text) {
        return containsIgnoreCase(text, "bạn đang sử dụng thú cưỡi khác")
                || containsIgnoreCase(text, "ban dang su dung thu cuoi khac");
    }
}
