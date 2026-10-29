package luna.features;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import luna.api.MonsterSelectionAccess;

/** Map-scoped monster-name/level selection for the native auto-target menu. */
public final class MonsterSelectionSupport {

    private static final int MAGIC = 0x4d534c31; // MSL1
    private static final int VERSION = 1;
    private static final int MAX_PROFILES = 128;
    private static final int MAX_GROUPS = 128;
    private static final int MAX_NAME_CHARS = 96;
    private static final int MAX_SERVER_BYTES = 128;
    private static final int MAX_SERIALIZED_BYTES = 128 * 1024;
    private static final int MAX_REMEMBERED_PAIRS = 1024;

    private static final Object LOCK = new Object();
    private static final Object OP_LOCK = new Object();
    private static final Object LOAD_LOCK = new Object();

    private static final Profile[] PROFILES = new Profile[MAX_PROFILES];
    private static int profileCount;
    private static boolean storeLoaded;
    private static boolean loadFailed;

    private static String activeServer = "";
    private static int activeMap;
    private static Profile activeProfile;

    private static MonsterSelectionAccess menuAccess;
    private static String menuServer = "";
    private static int menuMap;
    private static Row[] menuRows = new Row[MAX_GROUPS];
    private static int menuRowCount;
    private static boolean menuOpen;

    private MonsterSelectionSupport() {
    }

    /** Opens the native multi-choice popup for the current server and map. */
    public static void open(MonsterSelectionAccess access) {
        if (access == null) return;
        synchronized (OP_LOCK) {
            try {
                if (!safeReady(access)) {
                    safeMessage(access, "Vui lòng vào bản đồ trước.");
                    safeBack(access);
                    clearMenu();
                    return;
                }
                String server = safeServer(access);
                int map = safeMap(access);
                if (!validServer(server)) {
                    safeMessage(access, "Không xác định được máy chủ.");
                    safeBack(access);
                    clearMenu();
                    return;
                }

                ensureLoaded(access);
                synchronized (LOCK) {
                    activate(server, map);
                    menuAccess = access;
                    menuServer = server;
                    menuMap = map;
                    menuOpen = true;
                }
                if (loadFailed) safeMessage(access,
                        "Không đọc được lựa chọn quái; tạm dùng tất cả.");
                rebuildRows(access);
                showMenu(access);
            } catch (Throwable ignored) {
                safeMessage(access, "Không mở được lựa chọn quái.");
                safeBack(access);
                clearMenu();
            }
        }
    }

    /** Handles one native popup row; each group toggle is saved immediately. */
    public static void choose(int row) {
        try {
            synchronized (OP_LOCK) {
            MonsterSelectionAccess access;
            String server;
            int map;
            int rows;
            synchronized (LOCK) {
                if (!menuOpen || menuAccess == null) return;
                access = menuAccess;
                server = menuServer;
                map = menuMap;
                rows = menuRowCount;
            }
            if (!sameMenuContext(access, server, map)) {
                safeMessage(access, "Bản đồ đã thay đổi.");
                safeBack(access);
                clearMenu();
                return;
            }
            synchronized (LOCK) {
                // Combat can activate another map while this popup remains in memory.
                activate(server, map);
            }

            if (row == rows + 1) {
                safeBack(access);
                clearMenu();
                return;
            }
            if (row < 0 || row > rows) {
                showMenu(access);
                return;
            }
            if (row == 0) {
                boolean hadSelection;
                boolean removedProfile;
                synchronized (LOCK) {
                    hadSelection = activeProfile != null && activeProfile.count > 0;
                    removedProfile = activeProfile != null;
                    if (activeProfile != null) removeProfile(activeProfile);
                    activeProfile = null;
                }
                if (hadSelection) {
                    safeChanged(access);
                }
                if (removedProfile) persist(access);
                rebuildRows(access);
                showMenu(access);
                return;
            }

            Row selectedRow;
            boolean missingRow;
            synchronized (LOCK) {
                missingRow = row - 1 >= menuRowCount;
                selectedRow = missingRow ? null : menuRows[row - 1];
            }
            if (missingRow || selectedRow == null) {
                showMenu(access);
                return;
            }

            boolean changed;
            String limitMessage = null;
            synchronized (LOCK) {
                Group existing = findGroup(activeProfile, selectedRow.name, selectedRow.level);
                if (existing != null) {
                    removeGroup(activeProfile, existing);
                    if (activeProfile.count == 0) {
                        removeProfile(activeProfile);
                        activeProfile = null;
                    }
                    changed = true;
                } else {
                    if (activeProfile == null) {
                        if (profileCount >= MAX_PROFILES) {
                            changed = false;
                            limitMessage = "Đã đạt giới hạn 128 bản đồ.";
                        } else {
                            activeProfile = new Profile(menuServer, menuMap);
                            PROFILES[profileCount++] = activeProfile;
                            addGroup(activeProfile, selectedRow.name, selectedRow.level);
                            changed = true;
                        }
                    } else if (activeProfile.count >= MAX_GROUPS) {
                        changed = false;
                        limitMessage = "Đã đạt giới hạn 128 nhóm quái.";
                    } else {
                        addGroup(activeProfile, selectedRow.name, selectedRow.level);
                        changed = true;
                    }
                }
            }

            if (limitMessage != null) safeMessage(access, limitMessage);
            if (changed) {
                safeChanged(access);
                persist(access);
            }
            rebuildRows(access);
            showMenu(access);
            }
        } catch (Throwable ignored) {
            MonsterSelectionAccess access;
            synchronized (LOCK) {
                access = menuAccess;
            }
            if (access != null) safeMessage(access, "Không cập nhật được lựa chọn quái.");
        }
    }

    /** Returns whether this monster is allowed by the cached active profile. */
    public static boolean allow(MonsterSelectionAccess access, Object monster) {
        try {
            return allowInternal(access, monster);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean allowInternal(MonsterSelectionAccess access, Object monster) {
        if (access == null || monster == null) return true;
        try {
            if (access.type(monster) != 1) return true;
        } catch (Throwable ignored) {
            return true;
        }

        String server;
        int map;
        try {
            server = access.serverKey();
            map = access.mapId();
        } catch (Throwable ignored) {
            return true;
        }
        if (server == null || !validServer(server)) return true;

        ensureLoaded(access);
        Profile profile;
        synchronized (LOCK) {
            if (activeMap != map || !activeServer.equals(server)) activate(server, map);
            profile = activeProfile;
            if (profile == null || profile.count == 0) return true;
        }

        String name;
        int level;
        try {
            name = access.name(monster);
            level = access.level(monster);
        } catch (Throwable ignored) {
            return false;
        }
        if (!validName(name) || level <= 0) return false;
        synchronized (LOCK) {
            // A map switch while the native object was being read fails closed.
            if (activeProfile != profile || !activeServer.equals(server) || activeMap != map) return false;
            return findGroup(profile, name, level) != null;
        }
    }

    /** Diagnostic state used by the host fixture and optional native HUD. */
    public static String status() {
        synchronized (LOCK) {
            if (activeProfile == null || activeProfile.count == 0) {
                return "Monster selection: all";
            }
            return "Monster selection: " + activeProfile.count + " groups";
        }
    }

    /** Clears only static test state; bridge storage is left intact. */
    public static void resetForTest() {
        synchronized (OP_LOCK) {
            synchronized (LOAD_LOCK) {
                synchronized (LOCK) {
                    int i;
                    for (i = 0; i < MAX_PROFILES; i++) PROFILES[i] = null;
                    profileCount = 0;
                    storeLoaded = false;
                    loadFailed = false;
                    activeServer = "";
                    activeMap = 0;
                    activeProfile = null;
                    menuAccess = null;
                    menuServer = "";
                    menuMap = 0;
                    menuRows = new Row[MAX_GROUPS];
                    menuRowCount = 0;
                    menuOpen = false;
                }
            }
        }
    }

    private static void ensureLoaded(MonsterSelectionAccess access) {
        synchronized (LOCK) {
            if (storeLoaded) return;
        }
        synchronized (LOAD_LOCK) {
            synchronized (LOCK) {
                if (storeLoaded) return;
            }
            byte[] data = null;
            boolean failed = false;
            try {
                data = access.loadSelection();
            } catch (Throwable ignored) {
                failed = true;
            }
            Decoded decoded = decode(data);
            if (data != null && data.length > 0 && decoded == null) failed = true;
            synchronized (LOCK) {
                if (!storeLoaded) {
                    int i;
                    for (i = 0; i < MAX_PROFILES; i++) {
                        PROFILES[i] = decoded != null && i < decoded.count
                                ? decoded.profiles[i] : null;
                    }
                    profileCount = decoded == null ? 0 : decoded.count;
                    loadFailed = failed;
                    storeLoaded = true;
                }
            }
        }
    }

    private static void persist(MonsterSelectionAccess access) {
        byte[] data;
        synchronized (LOCK) {
            data = encode(PROFILES, profileCount);
        }
        boolean saved = false;
        if (data != null) {
            try {
                saved = access.saveSelection(data);
            } catch (Throwable ignored) {
                saved = false;
            }
        }
        if (!saved) {
            safeMessage(access, "Lỗi lưu lựa chọn quái; chỉ áp dụng trong phiên này.");
        } else {
            synchronized (LOCK) {
                loadFailed = false;
            }
        }
    }

    private static void rebuildRows(MonsterSelectionAccess access) {
        Row[] rows = new Row[MAX_GROUPS];
        int count = 0;
        Profile profile;
        synchronized (LOCK) {
            profile = activeProfile;
            if (profile != null) {
                int i;
                for (i = 0; i < profile.count; i++) {
                    Group group = profile.groups[i];
                    count = addRow(rows, count, group.name, group.level);
                }
            }
        }

        int size = 0;
        try {
            size = access.size();
        } catch (Throwable ignored) {
            safeMessage(access, "Không đọc được danh sách quái hiện tại.");
        }
        if (size < 0) size = 0;
        int i;
        for (i = 0; i < size && count < MAX_GROUPS; i++) {
            Object object;
            try {
                object = access.objectAt(i);
                if (object == null || access.type(object) != 1) continue;
                String name = access.name(object);
                int level = access.level(object);
                count = addRow(rows, count, name, level);
            } catch (Throwable ignored) {
                // A changing native vector can invalidate one entry; continue with the rest.
            }
        }

        int[] remembered = null;
        try {
            remembered = access.remembered();
        } catch (Throwable ignored) {
            remembered = null;
        }
        if (remembered != null) {
            int pairs = remembered.length / 2;
            if (pairs > MAX_REMEMBERED_PAIRS) pairs = MAX_REMEMBERED_PAIRS;
            for (i = 0; i < pairs && count < MAX_GROUPS; i++) {
                int template = remembered[i * 2];
                int level = remembered[i * 2 + 1];
                if (level <= 0) continue;
                try {
                    count = addRow(rows, count, access.templateName(template), level);
                } catch (Throwable ignored) {
                    // Catalog entries can disappear while the map is changing.
                }
            }
        }

        sortRows(rows, count);
        synchronized (LOCK) {
            menuRows = rows;
            menuRowCount = count;
        }
    }

    private static int addRow(Row[] rows, int count, String name, int level) {
        if (!validName(name) || excludedObjectName(name) || level <= 0 || count >= MAX_GROUPS) return count;
        int i;
        for (i = 0; i < count; i++) {
            if (rows[i].level == level && rows[i].name.equals(name)) return count;
        }
        rows[count++] = new Row(name, level);
        return count;
    }

    private static void sortRows(Row[] rows, int count) {
        int i;
        for (i = 1; i < count; i++) {
            Row value = rows[i];
            int j = i - 1;
            while (j >= 0 && compareRows(rows[j], value) > 0) {
                rows[j + 1] = rows[j];
                j--;
            }
            rows[j + 1] = value;
        }
    }

    private static int compareRows(Row a, Row b) {
        int byName = a.name.compareTo(b.name);
        if (byName != 0) return byName;
        if (a.level < b.level) return -1;
        if (a.level > b.level) return 1;
        return 0;
    }

    private static void showMenu(MonsterSelectionAccess access) {
        Row[] rows;
        int count;
        boolean all;
        Profile profile;
        synchronized (LOCK) {
            rows = menuRows;
            count = menuRowCount;
            profile = activeProfile;
            all = profile == null || profile.count == 0;
        }
        String[] labels = new String[count + 2];
        labels[0] = all ? "Tất cả quái [x]" : "Tất cả quái [ ]";
        int i;
        for (i = 0; i < count; i++) {
            Row row = rows[i];
            boolean selected;
            synchronized (LOCK) {
                selected = findGroup(activeProfile, row.name, row.level) != null;
            }
            StringBuffer label = new StringBuffer(row.name.length() + 20);
            label.append(row.name).append(" - Lv ").append(row.level)
                    .append(selected ? " [x]" : " [ ]");
            labels[i + 1] = label.toString();
        }
        labels[count + 1] = "Xong";
        try {
            access.showChoices(labels);
        } catch (Throwable ignored) {
            safeMessage(access, "Không hiển thị được lựa chọn quái.");
        }
    }

    private static boolean sameMenuContext(MonsterSelectionAccess access,
            String server, int map) {
        try {
            if (!safeReady(access)) return false;
            String currentServer = access.serverKey();
            if (currentServer == null) currentServer = "";
            return server.equals(currentServer) && map == access.mapId();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String safeServer(MonsterSelectionAccess access) {
        try {
            String server = access.serverKey();
            return server == null ? "" : server;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int safeMap(MonsterSelectionAccess access) {
        try {
            return access.mapId();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean safeReady(MonsterSelectionAccess access) {
        try {
            return access.ready();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void safeChanged(MonsterSelectionAccess access) {
        try {
            access.changed();
        } catch (Throwable ignored) {
            // The selection is still retained in this session if the hook fails.
        }
    }

    private static void safeMessage(MonsterSelectionAccess access, String text) {
        try {
            access.message(text);
        } catch (Throwable ignored) {
        }
    }

    private static void safeBack(MonsterSelectionAccess access) {
        try {
            access.back();
        } catch (Throwable ignored) {
        }
    }

    private static void clearMenu() {
        synchronized (LOCK) {
            menuOpen = false;
            menuAccess = null;
            menuRows = new Row[MAX_GROUPS];
            menuRowCount = 0;
        }
    }

    private static void activate(String server, int map) {
        activeServer = server;
        activeMap = map;
        activeProfile = findProfile(server, map);
    }

    private static Profile findProfile(String server, int map) {
        int i;
        for (i = 0; i < profileCount; i++) {
            Profile profile = PROFILES[i];
            if (profile.map == map && profile.server.equals(server)) return profile;
        }
        return null;
    }

    private static void removeProfile(Profile profile) {
        if (profile == null) return;
        int index = -1;
        int i;
        for (i = 0; i < profileCount; i++) {
            if (PROFILES[i] == profile) {
                index = i;
                break;
            }
        }
        if (index < 0) return;
        for (i = index + 1; i < profileCount; i++) PROFILES[i - 1] = PROFILES[i];
        PROFILES[--profileCount] = null;
    }

    private static Group findGroup(Profile profile, String name, int level) {
        if (profile == null) return null;
        int i;
        for (i = 0; i < profile.count; i++) {
            Group group = profile.groups[i];
            if (group.level == level && group.name.equals(name)) return group;
        }
        return null;
    }

    private static void addGroup(Profile profile, String name, int level) {
        if (excludedObjectName(name) || profile.count >= MAX_GROUPS || findGroup(profile, name, level) != null) return;
        profile.groups[profile.count++] = new Group(name, level);
    }

    /** These native type-1 objects are not selectable monster groups. */
    private static boolean excludedObjectName(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        return "Lửa thiêng".equalsIgnoreCase(trimmed) || "con Ma".equalsIgnoreCase(trimmed);
    }

    private static void removeGroup(Profile profile, Group group) {
        if (profile == null || group == null) return;
        int index = -1;
        int i;
        for (i = 0; i < profile.count; i++) {
            if (profile.groups[i] == group) {
                index = i;
                break;
            }
        }
        if (index < 0) return;
        for (i = index + 1; i < profile.count; i++) profile.groups[i - 1] = profile.groups[i];
        profile.groups[--profile.count] = null;
    }

    private static boolean validServer(String server) {
        return server != null && server.length() > 0 && utf8Length(server) <= MAX_SERVER_BYTES;
    }

    private static int utf8Length(String value) {
        int length = 0;
        int i;
        for (i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x7f) length++;
            else if (c <= 0x7ff) length += 2;
            else if (c >= 0xd800 && c <= 0xdbff && i + 1 < value.length()
                    && value.charAt(i + 1) >= 0xdc00 && value.charAt(i + 1) <= 0xdfff) {
                length += 4;
                i++;
            } else length += 3;
        }
        return length;
    }

    private static boolean validName(String name) {
        return name != null && name.length() > 0 && name.length() <= MAX_NAME_CHARS;
    }

    private static byte[] encode(Profile[] profiles, int count) {
        ByteArrayOutputStream bytes = null;
        DataOutputStream out = null;
        try {
            bytes = new ByteArrayOutputStream(512);
            out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeShort(VERSION);
            out.writeShort(count);
            int i;
            for (i = 0; i < count; i++) {
                Profile profile = profiles[i];
                if (profile == null || !validServer(profile.server)
                        || profile.count < 0 || profile.count > MAX_GROUPS) return null;
                out.writeUTF(profile.server);
                out.writeInt(profile.map);
                out.writeShort(profile.count);
                int j;
                for (j = 0; j < profile.count; j++) {
                    Group group = profile.groups[j];
                    if (group == null || !validName(group.name) || group.level <= 0) return null;
                    out.writeUTF(group.name);
                    out.writeInt(group.level);
                    if (bytes.size() > MAX_SERIALIZED_BYTES) return null;
                }
                if (bytes.size() > MAX_SERIALIZED_BYTES) return null;
            }
            out.flush();
            byte[] result = bytes.toByteArray();
            return result.length <= MAX_SERIALIZED_BYTES ? result : null;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (out != null) {
                try { out.close(); } catch (IOException ignored) { }
            }
        }
    }

    private static Decoded decode(byte[] data) {
        if (data == null || data.length == 0) return new Decoded();
        if (data.length > MAX_SERIALIZED_BYTES || data.length < 8) return null;
        DataInputStream in = null;
        try {
            in = new DataInputStream(new ByteArrayInputStream(data));
            if (in.readInt() != MAGIC || in.readUnsignedShort() != VERSION) return null;
            int count = in.readUnsignedShort();
            if (count > MAX_PROFILES) return null;
            Decoded decoded = new Decoded();
            decoded.count = count;
            int i;
            for (i = 0; i < count; i++) {
                String server = in.readUTF();
                if (!validServer(server)) return null;
                int map = in.readInt();
                int groups = in.readUnsignedShort();
                if (groups > MAX_GROUPS) return null;
                Profile profile = new Profile(server, map);
                int j;
                for (j = 0; j < groups; j++) {
                    String name = in.readUTF();
                    int level = in.readInt();
                    if (!validName(name) || level <= 0
                            || findGroup(profile, name, level) != null) return null;
                    addGroup(profile, name, level);
                }
                int k;
                for (k = 0; k < i; k++) {
                    Profile previous = decoded.profiles[k];
                    if (previous.map == map && previous.server.equals(server)) return null;
                }
                decoded.profiles[i] = profile;
            }
            if (in.available() != 0) return null;
            return decoded;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
    }

    private static final class Profile {
        final String server;
        final int map;
        final Group[] groups = new Group[MAX_GROUPS];
        int count;

        Profile(String server, int map) {
            this.server = server;
            this.map = map;
        }
    }

    private static final class Group {
        final String name;
        final int level;

        Group(String name, int level) {
            this.name = name;
            this.level = level;
        }
    }

    private static final class Row {
        final String name;
        final int level;

        Row(String name, int level) {
            this.name = name;
            this.level = level;
        }
    }

    private static final class Decoded {
        final Profile[] profiles = new Profile[MAX_PROFILES];
        int count;
    }
}
