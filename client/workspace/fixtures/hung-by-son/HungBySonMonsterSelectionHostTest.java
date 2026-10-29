import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import luna.api.MonsterSelectionAccess;

/** Host-side persistence and filtering checks for map-scoped monster choices. */
public final class HungBySonMonsterSelectionHostTest {
    private static int checks;

    private HungBySonMonsterSelectionHostTest() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("<candidate.jar>");
        File candidate = new File(args[0]);
        require(candidate.isFile(), "candidate JAR exists");

        checkDefaultsGroupingAndPersistence(candidate);
        checkScopeAndHiddenChoices(candidate);
        checkStalePopup(candidate);
        checkMalformedDataAndBounds(candidate);
        checkSaveFailure(candidate);
        checkExcludedObjectRows(candidate);

        System.out.println("HungBySonMonsterSelectionHostTest PASS (" + checks + " checks)");
    }

    private static byte[] oldObjectSelection(boolean mixed) throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
        out.writeInt(0x4d534c31); out.writeShort(1); out.writeShort(1);
        out.writeUTF("realm-objects"); out.writeInt(10); out.writeShort(mixed ? 3 : 2);
        out.writeUTF(" LỬA THIÊNG "); out.writeInt(1);
        out.writeUTF("con Ma"); out.writeInt(10);
        if (mixed) { out.writeUTF("Alpha Wolf"); out.writeInt(10); }
        out.close(); return bytes.toByteArray();
    }

    private static void checkExcludedObjectRows(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess access = sampleAccess(store, "realm-objects", 10, 1);
        access.add(new FakeMonster(700, 700, "Lửa thiêng", 1, 1));
        access.add(new FakeMonster(701, 700, " LỬA THIÊNG ", 2, 1));
        access.add(new FakeMonster(702, 701, "con Ma", 10, 1));
        access.add(new FakeMonster(703, 701, " CON MA ", 20, 1));
        access.add(new FakeMonster(704, 702, "Xà nữ", 40, 1));
        access.add(new FakeMonster(705, 703, "Lửa thiêng lớn", 4, 1));
        access.names.put(Integer.valueOf(710), "lửa thiêng");
        access.names.put(Integer.valueOf(711), " Con ma ");
        access.remembered = new int[]{710, 4, 711, 30};
        store.bytes = oldObjectSelection(true);
        try (Support support = new Support(candidate)) {
            support.open(access);
            for (String label : access.shown) {
                String lower = label.toLowerCase();
                require(!lower.startsWith("lửa thiêng -") && !lower.startsWith(" lửa thiêng ")
                        && !lower.startsWith("con ma -") && !lower.startsWith(" con ma "),
                        "live, remembered and saved object choices are hidden: " + label);
            }
            require(findGroup(access.shown, "Xà nữ", 40) > 0, "Xà nữ remains selectable");
            require(findGroup(access.shown, "Lửa thiêng lớn", 4) > 0, "object name exclusion is exact");
            require(support.allow(access, access.alpha10a), "valid saved monster selection is retained");
            require(!support.allow(access, access.beta20), "other monsters keep the saved filter");
            support.choose(findGroup(access.shown, "Alpha Wolf", 10));
            require(support.allow(access, access.beta20), "removing the last real choice returns to all monsters");
        }
        store.bytes = oldObjectSelection(false);
        try (Support support = new Support(candidate)) {
            support.open(access);
            require(access.shown[0].contains("[x]"), "legacy object-only selection falls back to all");
            require(support.allow(access, access.alpha10a) && support.allow(access, access.beta20),
                    "hidden legacy objects cannot block real monsters");
        }
    }

    private static void checkDefaultsGroupingAndPersistence(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess access = sampleAccess(store, "realm-a", 10, 2);
        try (Support support = new Support(candidate)) {
            support.open(access);
            require(access.shown != null && access.shown.length > 0, "open displays choices");
            require(groupCount(access.shown, "Alpha Wolf", 10) == 1,
                    "duplicate same-name and same-level instances share one row");
            require(groupCount(access.shown, "Alpha Wolf", 11) == 1,
                    "same monster name at a different level gets a separate row");
            require(groupCount(access.shown, "Beta Boar", 20) == 1,
                    "another monster group is visible");
            require(support.allow(access, access.alpha10a), "no profile allows monsters by default");
            require(support.allow(access, access.alpha11), "no profile allows other levels by default");
            require(support.allow(access, access.beta20), "no profile allows other monster names by default");
            support.status();

            int row = findGroup(access.shown, "Alpha Wolf", 10);
            require(row > 0, "group row can be found independently of its checkbox text");
            int saves = access.saves;
            support.choose(row);
            require(access.saves > saves && store.bytes != null,
                    "first toggle persists immediately");

            support.open(access);
            row = findGroup(access.shown, "Alpha Wolf", 11);
            require(row > 0, "second group row is available after rebuilding the choices");
            saves = access.saves;
            support.choose(row);
            require(access.saves > saves, "second toggle also persists immediately");

            require(support.allow(access, access.alpha10a), "selected name and level are allowed");
            require(support.allow(access, access.alpha10b),
                    "all native IDs in the selected name-and-level group are allowed");
            require(support.allow(access, access.alpha11), "second selected level is allowed");
            require(!support.allow(access, access.beta20), "unselected name is rejected by an active filter");
            require(!support.allow(access, access.alphaUnknown),
                    "unknown level is rejected while a profile is active");
            require(support.allow(access, access.player), "player entities pass through the monster filter");
        }

        FakeAccess restarted = sampleAccess(store, "realm-a", 10, 99);
        try (Support support = new Support(candidate)) {
            support.open(restarted);
            require(support.allow(restarted, restarted.alpha10a),
                    "selection survives a fresh class loader and a same-map zone change");
            require(support.allow(restarted, restarted.alpha11), "second group survives process restart");
            require(!support.allow(restarted, restarted.beta20), "unselected group remains filtered after restart");

            int saves = restarted.saves;
            support.choose(0);
            require(restarted.saves > saves, "row zero reset persists immediately");
            require(support.allow(restarted, restarted.alpha10a)
                    && support.allow(restarted, restarted.alpha11)
                    && support.allow(restarted, restarted.beta20),
                    "row zero restores allow-all behavior");
        }
    }

    private static void checkScopeAndHiddenChoices(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess source = sampleAccess(store, "realm-scope", 21, 1);
        try (Support support = new Support(candidate)) {
            support.open(source);
            support.choose(findGroup(source.shown, "Alpha Wolf", 10));
            support.choose(findGroup(source.shown, "Alpha Wolf", 11));
        }

        FakeAccess otherMap = sampleAccess(store, "realm-scope", 22, 1);
        try (Support support = new Support(candidate)) {
            support.open(otherMap);
            require(support.allow(otherMap, otherMap.beta20), "selection is isolated by map");
        }

        FakeAccess otherServer = sampleAccess(store, "realm-other", 21, 1);
        try (Support support = new Support(candidate)) {
            support.open(otherServer);
            require(support.allow(otherServer, otherServer.beta20), "selection is isolated by server");
        }

        FakeAccess hidden = sampleAccess(store, "realm-scope", 21, 88);
        hidden.monsters.clear();
        hidden.add(hidden.alpha11);
        hidden.add(hidden.beta20);
        hidden.remembered = new int[]{101, 10, 101, 11, 202, 20};
        try (Support support = new Support(candidate)) {
            support.open(hidden);
            int hiddenRow = findGroup(hidden.shown, "Alpha Wolf", 10);
            require(hiddenRow > 0,
                    "a selected group remains visible when no current native instance has that level");
            support.choose(hiddenRow);
            require(!support.allow(hidden, hidden.alpha10a),
                    "retained hidden group can be unchecked while another group remains selected");
            require(support.allow(hidden, hidden.alpha11), "unchecking one group preserves other choices");
            require(!support.allow(hidden, hidden.beta20), "hidden-group edit keeps the active filter strict");
        }
    }

    private static void checkStalePopup(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess source = sampleAccess(store, "realm-stale", 31, 4);
        try (Support support = new Support(candidate)) {
            support.open(source);
            support.choose(findGroup(source.shown, "Alpha Wolf", 10));

            support.open(source);
            source.map = 32;
            require(support.allow(source, source.beta20), "combat reads an unconfigured map while popup is retained");
            source.map = 31;
            support.choose(findGroup(source.shown, "Beta Boar", 20));
            require(support.allow(source, source.alpha10a) && support.allow(source, source.beta20)
                    && !support.allow(source, source.alpha11), "returning to popup context edits its original profile");
            int staleRow = findGroup(source.shown, "Beta Boar", 20);
            require(staleRow > 0, "stale-popup row is captured on the original map");
            source.map = 32;
            FakeStore snapshot = store.copy();
            int saves = source.saves;
            support.choose(staleRow);
            require(source.saves == saves && equalBytes(snapshot.bytes, store.bytes),
                    "map change while the popup is open prevents a cross-map write");
        }

        FakeAccess destination = sampleAccess(store, "realm-stale", 32, 4);
        try (Support support = new Support(candidate)) {
            support.open(destination);
            require(support.allow(destination, destination.beta20),
                    "stale popup click cannot activate a filter on the destination map");
        }
    }

    private static void checkMalformedDataAndBounds(File candidate) throws Exception {
        byte[][] malformed = new byte[][]{
            new byte[]{1},
            new byte[]{0, 0, 0, 0},
            new byte[]{'M', 'S', 'L', '1', 0, 2, 0, 0},
            new byte[]{'M', 'S', 'L', '1', 0, 1, 0, 1},
            new byte[]{'M', 'S', 'L', '1', 0, 1, -1, -1}
        };
        for (int i = 0; i < malformed.length; i++) {
            FakeStore store = new FakeStore();
            store.bytes = malformed[i].clone();
            FakeAccess access = sampleAccess(store, "realm-bad-" + i, 5, 1);
            try (Support support = new Support(candidate)) {
                support.open(access);
                require(support.allow(access, access.beta20),
                        "malformed, truncated, unsupported-version, and invalid-count data default safely");
            }
        }

        FakeStore store = new FakeStore();
        FakeAccess many = new FakeAccess(store, "realm-many", 6, 1);
        int[] pairs = new int[700];
        for (int i = 0; i < 350; i++) {
            int template = 1000 + i;
            pairs[i * 2] = template;
            pairs[i * 2 + 1] = 1 + (i % 80);
            many.names.put(Integer.valueOf(template), "Remembered Beast " + i);
        }
        many.remembered = pairs;
        try (Support support = new Support(candidate)) {
            support.open(many);
            require(many.shown.length <= 130,
                    "choice list remains bounded to 128 groups plus its control rows");
        }
    }

    private static void checkSaveFailure(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess access = sampleAccess(store, "realm-save-failure", 41, 2);
        access.failSave = true;
        try (Support support = new Support(candidate)) {
            support.open(access);
            int row = findGroup(access.shown, "Alpha Wolf", 10);
            int messages = access.messages;
            support.choose(row);
            require(access.saves > 0, "selection attempts to persist through the bridge");
            require(access.messages > messages,
                    "failed persistence is reported instead of silently claimed as saved");
            require(support.allow(access, access.alpha10a),
                    "failed persistence keeps the new choice active for this session");
            require(store.bytes == null, "fake failed write leaves shared storage unchanged");
        }

        FakeStore throwingStore = new FakeStore();
        FakeAccess throwing = sampleAccess(throwingStore, "realm-save-throw", 41, 2);
        throwing.throwOnSave = true;
        try (Support support = new Support(candidate)) {
            support.open(throwing);
            support.choose(findGroup(throwing.shown, "Alpha Wolf", 10));
            require(throwing.messages > 0, "thrown storage error is caught and surfaced");
        }
    }

    private static FakeAccess sampleAccess(FakeStore store, String server, int map, int zone) {
        FakeAccess access = new FakeAccess(store, server, map, zone);
        access.alpha10a = monster(11, 101, "Alpha Wolf", 10, 1);
        access.alpha10b = monster(12, 101, "Alpha Wolf", 10, 1);
        access.alpha11 = monster(13, 101, "Alpha Wolf", 11, 1);
        access.beta20 = monster(14, 202, "Beta Boar", 20, 1);
        access.alphaUnknown = monster(15, 101, "Alpha Wolf", 0, 1);
        access.player = monster(99, 0, "Player Hero", 99, 0);
        access.add(access.alpha10a);
        access.add(access.alpha10b);
        access.add(access.alpha11);
        access.add(access.beta20);
        access.add(access.player);
        access.remembered = new int[]{101, 10, 101, 10, 101, 11, 202, 20};
        access.names.put(Integer.valueOf(101), "Alpha Wolf");
        access.names.put(Integer.valueOf(202), "Beta Boar");
        return access;
    }

    private static FakeMonster monster(int id, int template, String name, int level, int type) {
        return new FakeMonster(id, template, name, level, type);
    }

    private static int groupCount(String[] labels, String name, int level) {
        int count = 0;
        for (String label : labels) if (matchesGroup(label, name, level)) count++;
        return count;
    }

    private static int findGroup(String[] labels, String name, int level) {
        for (int i = 0; i < labels.length; i++)
            if (matchesGroup(labels[i], name, level)) return i;
        return -1;
    }

    private static boolean matchesGroup(String label, String name, int level) {
        if (label == null || name == null) return false;
        String lower = label.toLowerCase();
        if (!lower.contains(name.toLowerCase())) return false;
        String number = String.valueOf(level);
        int offset = lower.indexOf(number);
        while (offset >= 0) {
            int end = offset + number.length();
            boolean leftBoundary = offset == 0 || !Character.isDigit(lower.charAt(offset - 1));
            boolean rightBoundary = end == lower.length() || !Character.isDigit(lower.charAt(end));
            if (leftBoundary && rightBoundary) return true;
            offset = lower.indexOf(number, offset + 1);
        }
        return false;
    }

    private static boolean equalBytes(byte[] left, byte[] right) {
        if (left == right) return true;
        if (left == null || right == null || left.length != right.length) return false;
        for (int i = 0; i < left.length; i++) if (left[i] != right[i]) return false;
        return true;
    }

    private static final class Support implements AutoCloseable {
        private final ChildFirstLoader loader;
        private final Method open;
        private final Method choose;
        private final Method allow;
        private final Method status;

        Support(File candidate) throws Exception {
            loader = new ChildFirstLoader(candidate);
            Class<?> type = loader.loadClass("luna.features.MonsterSelectionSupport");
            open = type.getMethod("open", MonsterSelectionAccess.class);
            choose = type.getMethod("choose", int.class);
            allow = type.getMethod("allow", MonsterSelectionAccess.class, Object.class);
            status = type.getMethod("status");
        }

        void open(MonsterSelectionAccess access) throws Exception {
            invoke(open, access);
        }

        void status() throws Exception {
            Object result = invoke(status);
            require(result == null || result instanceof String, "status hook returns text safely");
        }

        void choose(int row) throws Exception {
            invoke(choose, Integer.valueOf(row));
        }

        boolean allow(MonsterSelectionAccess access, Object monster) throws Exception {
            Object result = invoke(allow, access, monster);
            if (!(result instanceof Boolean)) throw new AssertionError("allow() did not return boolean");
            return ((Boolean)result).booleanValue();
        }

        private static Object invoke(Method method, Object... args) throws Exception {
            try {
                return method.invoke(null, args);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof Exception) throw (Exception)cause;
                if (cause instanceof Error) throw (Error)cause;
                throw new RuntimeException(cause);
            }
        }

        public void close() throws Exception { loader.close(); }
    }

    private static final class ChildFirstLoader extends URLClassLoader {
        ChildFirstLoader(File candidate) throws Exception {
            super(new URL[]{candidate.toURI().toURL()}, HungBySonMonsterSelectionHostTest.class.getClassLoader());
        }

        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("luna.features.MonsterSelectionSupport")) return super.loadClass(name, resolve);
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try { loaded = findClass(name); }
                catch (ClassNotFoundException missing) { return super.loadClass(name, resolve); }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    private static final class FakeStore {
        byte[] bytes;
        FakeStore copy() {
            FakeStore copy = new FakeStore();
            copy.bytes = bytes == null ? null : bytes.clone();
            return copy;
        }
    }

    private static final class FakeAccess implements MonsterSelectionAccess {
        final FakeStore store;
        String server;
        int map;
        int zone;
        boolean isReady = true;
        boolean failSave;
        boolean throwOnSave;
        int saves;
        int messages;
        int changedCount;
        int backs;
        String lastMessage;
        String[] shown;
        int[] remembered = new int[0];
        final List<FakeMonster> monsters = new ArrayList<FakeMonster>();
        final Map<Integer, String> names = new HashMap<Integer, String>();
        FakeMonster alpha10a;
        FakeMonster alpha10b;
        FakeMonster alpha11;
        FakeMonster beta20;
        FakeMonster alphaUnknown;
        FakeMonster player;

        FakeAccess(FakeStore store, String server, int map, int zone) {
            this.store = store;
            this.server = server;
            this.map = map;
            this.zone = zone;
        }

        void add(FakeMonster monster) { monsters.add(monster); }
        public String serverKey() { return server; }
        public int mapId() { return map; }
        public boolean ready() { return isReady; }
        public int size() { return monsters.size(); }
        public Object objectAt(int index) { return monsters.get(index); }
        public int type(Object monster) { return ((FakeMonster)monster).type; }
        public String name(Object monster) { return ((FakeMonster)monster).name; }
        public int level(Object monster) { return ((FakeMonster)monster).level; }
        public String templateName(int template) { return names.get(Integer.valueOf(template)); }
        public int[] remembered() { return remembered == null ? null : remembered.clone(); }

        public byte[] loadSelection() {
            return store.bytes == null ? null : store.bytes.clone();
        }

        public boolean saveSelection(byte[] data) {
            saves++;
            if (throwOnSave) throw new IllegalStateException("simulated storage exception");
            if (failSave) return false;
            store.bytes = data == null ? null : data.clone();
            return true;
        }

        public void showChoices(String[] labels) { shown = labels == null ? null : labels.clone(); }
        public void back() { backs++; }
        public void message(String text) { messages++; lastMessage = text; }
        public void changed() { changedCount++; }
    }

    private static final class FakeMonster {
        final int id;
        final int template;
        final String name;
        final int level;
        final int type;

        FakeMonster(int id, int template, String name, int level, int type) {
            this.id = id;
            this.template = template;
            this.name = name;
            this.level = level;
            this.type = type;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
