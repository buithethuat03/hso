package luna.core;

/**
 * Semantic route graph for the world portals.  The graph is versioned data,
 * not binary-client code; binary access is limited to reading the current map
 * id and sending the selected portal action through {@code LunaBridgeApi}.
 */
public final class WorldRoutePlanner {

    public static final int MAP_MAX = 256;

    public static final int[] MAP_IDS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,
        14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,
        28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41,
        42, 43, 44, 45, 47, 48, 50, 51, 52, 53, 54, 55, 56, 57,
        58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71,
        72, 73, 74, 75, 76, 77, 78, 79, 82, 88, 89, 90, 91, 92,
        93, 94, 95, 96, 97, 98, 102
    };

    /** Labels are used only for tolerant text matching in NPC/portal notices. */
    public static final String[] MAP_LABELS = {
        "Ng\u00f4i L\u00e0ng Nh\u1ecf", "L\u00e0ng S\u00f3i Tr\u1eafng", "Khu m\u1ecf",
        "B\u00eca R\u1eebng", "Hang L\u1eeda", "R\u1eebng \u1ea2o Gi\u00e1c",
        "Khe V\u1ef1c", "C\u00e1nh \u0110\u1ed3ng S\u00f3i", "Thung L\u0169ng K\u1ef3 B\u00ed",
        "H\u1ed3 K\u00fd \u1ee8c", "B\u00e3i \u0110\u1ea5t Tr\u1ed1ng", "B\u1edd Bi\u1ec3n",
        "V\u1ef1c \u0110\u00e1", "R\u1eb7ng \u0110\u00e1 Ng\u1ea7m", "Ngh\u0129a \u0110\u1ecba T\u00e0u \u0110\u1eafm",
        "\u0110\u1ea7m L\u1ea7y", "\u0110\u1ec1n C\u1ed5", "Hang D\u01a1i",
        "Hang S\u00f3i Qu\u1ef7", "C\u1eeda Bi\u1ec3n", "Sa M\u1ea1c",
        "\u0110\u1ed3i C\u00e1t", "V\u1ef1c L\u00fan", "H\u1ed1 T\u1eed Th\u1ea7n",
        "Ngh\u0129a \u0111\u1ecba c\u00e1t", "R\u1eebng Ch\u1ebft", "Su\u1ed1i Ma",
        "Thung L\u0169ng \u0110\u00e1", "Boss Guardian", "H\u1ea7m M\u1ed9 T\u1ea7ng 1",
        "H\u1ea7m M\u1ed9 T\u1ea7ng 2", "H\u1ea7m M\u1ed9 T\u1ea7ng 3", "H\u1ea7m m\u1ed9 qu\u00e1i v\u1eadt",
        "Th\u00e0nh Ph\u1ed1 Kho B\u00e1u", "Khu ph\u00eda T\u00e2y", "Khu ph\u00eda \u0110\u00f4ng",
        "\u0110\u1ea5u Tr\u01b0\u1eddng", "R\u1eebng cao nguy\u00ean", "Con \u0111\u01b0\u1eddng hi\u1ec3m tr\u1edf",
        "V\u00e1ch \u0111\u00e1 cheo leo", "N\u00fai C\u1ea7u V\u00f2ng", "L\u1ed1i l\u00ean Th\u01b0\u1ee3ng gi\u1edbi",
        "\u0110\u00e8o hoang s\u01a1", "\u0110\u01b0\u1eddng xu\u1ed1ng l\u00f2ng \u0111\u1ea5t", "C\u00e2y c\u1ea7u ma \u00e1m",
        "C\u1ed5ng v\u00e0o H\u1ea1 gi\u1edbi", "\u0110\u1ed3i x\u00e1c ch\u1ebft", "Ng\u00e3 t\u01b0 t\u1eed th\u1ea7n",
        "Khu v\u01b0\u1eddn", "C\u1ed5ng thi\u00ean \u0111\u00e0ng", "C\u1ed5ng \u0111\u1ecba ng\u1ee5c",
        "Chu\u1ea9n b\u1ecb", "L\u00e0ng \u00e1nh s\u00e1ng", "Chu\u1ea9n b\u1ecb",
        "L\u00e0ng gi\u00f3", "Chu\u1ea9n b\u1ecb", "L\u00e0ng s\u00e9t",
        "Chu\u1ea9n b\u1ecb", "L\u00e0ng l\u1eeda", "Chi\u1ebfn tr\u01b0\u1eddng",
        "\u0110\u1ecba ng\u1ee5c t\u1ea7ng 1", "R\u1eebng medusa", "R\u1eebng Chimera",
        "R\u1eebng qu\u00e1i v\u1eadt", "Th\u00e1c reo", "Th\u00e0nh ph\u1ed1 c\u1ea3ng",
        "Khu b\u1edd nam", "Khu b\u1edd b\u1eafc", "Khu b\u1edd t\u00e2y",
        "R\u1eebng chu\u1ed9t", "R\u1eebng hoa \u0111\u1ecf", "V\u1ecbnh Caribe",
        "M\u00ea cung", "M\u00ea cung t\u1ea7ng 1", "M\u00ea cung t\u1ea7ng 2",
        "M\u00ea cung t\u1ea7ng 3", "M\u00ea cung t\u1ea7ng 4", "M\u00ea cung t\u1ea7ng cu\u1ed1i",
        "Khu mua b\u00e1n", "Khu hang \u0111\u1ed9ng", "Khu \u0111\u1ed3i c\u00e1t",
        "Khu th\u1ea3o nguy\u00ean", "Khu \u0111\u1ed3i c\u1ecf", "C\u1ed5ng tr\u1eafng",
        "Th\u1ecb tr\u1ea5n m\u00f9a \u0111\u00f4ng", "Thung l\u0169ng b\u0103ng gi\u00e1", "Ch\u00e2n n\u00fai tuy\u1ebft",
        "\u0110\u00e8o b\u0103ng gi\u00e1", "V\u1ef1c th\u1eb3m s\u01b0\u01a1ng m\u00f9", "Tr\u1ea1m n\u00fai tuy\u1ebft",
        "Loi Dai"
    };

    /** Undirected portal edges, encoded as [from, to, from, to, ...]. */
    public static final int[] MAP_EDGES = {
        0, 1, 1, 2, 1, 3, 1, 7, 1, 50, 3, 4, 4, 5, 5, 6,
        7, 8, 7, 11, 7, 15, 7, 18, 8, 9, 9, 10, 11, 12, 12, 13,
        13, 14, 15, 16, 16, 17, 18, 25, 19, 24, 19, 47, 20, 23, 20, 34,
        21, 24, 21, 37, 22, 24, 22, 42, 23, 24, 25, 26, 25, 33, 26, 27,
        27, 28, 29, 30, 29, 35, 30, 31, 31, 32, 33, 34, 33, 35, 33, 36,
        37, 38, 38, 39, 39, 40, 40, 41, 41, 51, 42, 43, 43, 44, 44, 45,
        45, 52, 52, 62, 53, 54, 54, 61, 55, 56, 56, 61, 57, 58, 58, 61,
        59, 60, 60, 61, 63, 64, 63, 65, 63, 70, 63, 72, 64, 66, 64, 70,
        64, 71, 65, 68, 66, 69, 67, 68, 67, 69, 67, 70, 71, 73, 72, 73,
        73, 74, 74, 75, 75, 76, 76, 77, 77, 78, 78, 79, 79, 92, 92, 93,
        93, 94, 94, 95, 95, 96, 96, 97, 97, 98
    };

    private WorldRoutePlanner() { }

    public static int mapIdOfName(String name) {
        if (name == null) return -1;
        String normalized = name.toLowerCase().trim();
        if (normalized.length() == 0) return -1;
        int count = MAP_IDS.length < MAP_LABELS.length ? MAP_IDS.length : MAP_LABELS.length;
        for (int i = 0; i < count; i++) {
            String candidate = MAP_LABELS[i].toLowerCase();
            if (candidate.length() == 0) continue;
            if (normalized.equals(candidate) || normalized.indexOf(candidate) >= 0
                    || candidate.indexOf(normalized) >= 0) return MAP_IDS[i];
        }
        return -1;
    }

    /** Returns the next map on a shortest deterministic portal route. */
    public static int nextHop(int from, int to) {
        if (from == to) return to;
        if (from < 0 || from >= MAP_MAX || to < 0 || to >= MAP_MAX) return -1;
        boolean[] seen = new boolean[MAP_MAX];
        int[] previous = new int[MAP_MAX];
        int[] queue = new int[MAP_MAX];
        for (int i = 0; i < MAP_MAX; i++) previous[i] = -1;
        int head = 0;
        int tail = 0;
        queue[tail++] = from;
        seen[from] = true;
        while (head < tail && !seen[to]) {
            int current = queue[head++];
            for (int i = 0; i + 1 < MAP_EDGES.length; i += 2) {
                int a = MAP_EDGES[i];
                int b = MAP_EDGES[i + 1];
                int other = a == current ? b : (b == current ? a : -1);
                if (other < 0 || other >= MAP_MAX || seen[other]) continue;
                seen[other] = true;
                previous[other] = current;
                queue[tail++] = other;
                if (other == to) break;
            }
        }
        if (!seen[to]) return -1;
        int cursor = to;
        while (previous[cursor] != from) {
            cursor = previous[cursor];
            if (cursor < 0) return -1;
        }
        return cursor;
    }
}
