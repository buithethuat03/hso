/**
 * Chat commands for changing zone ("doi khu") without the in-game panel.
 *
 * The panel works like this: the server pushes opcode 54 with the zone list, the
 * client shows it as the "Khu vuc" popup, and picking row i sends opcode 51 with
 * one byte = i. This class sends that same opcode 51 straight from the chat box,
 * so a broken or unreachable panel is no longer in the way.
 *
 * Commands (typed in the chat box):
 *   k1, k2, ... kN   -> pick row N of the zone list (N-1 on the wire, same as clicking)
 *   kk0, kk1, ... kkN-> send N unchanged, for a server that wants the zone id itself
 *   khelp            -> show this list in game
 *
 * The class names below are placeholders; the patcher rewrites them to the
 * obfuscated names it finds in the JAR, and the calls go through reflection so
 * this file never has to be compiled against the client.
 */
public final class HSOSKYZoneChatSupport {
    private static String serviceClass = "@SERVICE_CLASS@";
    private static String serviceGetter = "@SERVICE_GETTER@";
    private static String serviceZone = "@SERVICE_ZONE@";
    private static String toastClass = "@TOAST_CLASS@";
    private static String toastMethod = "@TOAST_METHOD@";

    private HSOSKYZoneChatSupport() {
    }

    /** @return true when the message was a zone command and must not be sent as chat. */
    public static boolean handleChat(String message) {
        if (message == null) {
            return false;
        }
        String command = message.trim().toLowerCase();
        if (command.length() < 2 || command.charAt(0) != 'k') {
            return false;
        }
        if ("khelp".equals(command) || "k?".equals(command)) {
            toast("kN: doi sang khu N (vi du k1, k7)\n"
                    + "kkN: gui thang so N (neu kN chua dung khu)\n"
                    + "khelp: hien lai huong dan");
            return true;
        }
        boolean raw = command.charAt(1) == 'k';
        String digits = command.substring(raw ? 2 : 1);
        int value = parse(digits);
        if (value < 0) {
            return false;
        }
        int wire = raw ? value : value - 1;
        if (wire < 0 || wire > 127) {
            toast("Khu " + value + " khong hop le");
            return true;
        }
        if (sendZone(wire)) {
            toast(raw ? ("Da gui doi khu (byte " + wire + ")")
                    : ("Dang doi sang Khu " + value));
        } else {
            toast("Chua gui duoc yeu cau doi khu");
        }
        return true;
    }

    private static int parse(String digits) {
        if (digits.length() == 0 || digits.length() > 3) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            value = value * 10 + (c - '0');
        }
        return value;
    }

    /** Sends opcode 51 with the one byte the zone popup would have sent. */
    private static boolean sendZone(int wire) {
        try {
            Class<?> service = Class.forName(serviceClass);
            Object instance = service.getMethod(serviceGetter, new Class[0])
                    .invoke(null, new Object[0]);
            if (instance == null) {
                return false;
            }
            service.getMethod(serviceZone, new Class[] { Byte.TYPE })
                    .invoke(instance, new Object[] { Byte.valueOf((byte) wire) });
            System.out.println("ZoneChat: sent opcode 51 byte " + wire);
            return true;
        } catch (Throwable failure) {
            System.out.println("ZoneChat: send failed: " + failure);
            return false;
        }
    }

    private static void toast(String text) {
        System.out.println("ZoneChat: " + text);
        try {
            Class.forName(toastClass)
                    .getMethod(toastMethod, new Class[] { String.class })
                    .invoke(null, new Object[] { text });
        } catch (Throwable ignored) {
            // The console line above is enough when the in-game toast is unavailable.
        }
    }
}
