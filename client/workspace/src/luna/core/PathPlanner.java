package luna.core;

/**
 * Semantic home for movement planning. Binary-specific path encoding belongs
 * in the bridge; feature controllers only choose a planning strategy.
 */
public final class PathPlanner {

    /** Semantic tile query supplied by the bridge/runtime adapter. */
    public interface Walkability {
        boolean isWalkable(int x, int y);
    }

    public static final int STRAIGHT = 1;
    public static final int ENGINE = 2;
    public static final int BFS = 3;
    public static final int PORTAL = 4;

    private PathPlanner() { }

    /** Binary-compatible tile coordinate range used by the legacy engine path format. */
    public static boolean tileInRange(int tile) {
        return tile >= 0 && tile <= 127;
    }

    /** Packs one tile using the engine's high-byte x / low-byte y representation. */
    public static short tileWord(int tileX, int tileY) {
        return (short) ((tileX << 8) | (tileY & 0xff));
    }

    /**
     * Builds the legacy straight path while keeping all binary-free route math semantic.
     * The first element is the current tile; a zero-length route returns null.
     */
    public static short[] straightPath(int fromX, int fromY, int toX, int toY,
            int maxTiles) {
        if (!tileInRange(fromX) || !tileInRange(fromY)
                || !tileInRange(toX) || !tileInRange(toY)) return null;
        int dx = toX - fromX;
        int dy = toY - fromY;
        int total = Math.abs(dx) > Math.abs(dy) ? Math.abs(dx) : Math.abs(dy);
        if (total <= 0) return null;
        int steps = total < maxTiles ? total : maxTiles;
        short[] out = new short[steps + 1];
        out[0] = tileWord(fromX, fromY);
        for (int i = 1; i <= steps; i++) {
            out[i] = tileWord(fromX + dx * i / total, fromY + dy * i / total);
        }
        return out;
    }

    /**
     * Four-neighbour BFS over a real tile grid. This keeps the old deterministic
     * nearest-to-target tie-break while removing binary access from the algorithm.
     */
    public static short[] gridPath(int[] grid, int width, int fromX, int fromY,
            int toX, int toY, int radius) {
        if (grid == null || width <= 0) return null;
        int height = grid.length / width;
        if (height <= 0 || fromX < 0 || fromY < 0
                || fromX >= width || fromY >= height) return null;
        int total = width * height;
        if (total <= 0 || total > grid.length) return null;

        int[] previous = new int[total];
        int[] distance = new int[total];
        int[] queue = new int[total];
        for (int i = 0; i < total; i++) {
            previous[i] = -1;
            distance[i] = -1;
        }

        int start = fromY * width + fromX;
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        distance[start] = 0;
        while (head < tail) {
            int current = queue[head++];
            int x = current % width;
            int y = current / width;
            tail = pushGridTile(grid, width, height, x + 1, y, current,
                    queue, tail, previous, distance);
            tail = pushGridTile(grid, width, height, x - 1, y, current,
                    queue, tail, previous, distance);
            tail = pushGridTile(grid, width, height, x, y + 1, current,
                    queue, tail, previous, distance);
            tail = pushGridTile(grid, width, height, x, y - 1, current,
                    queue, tail, previous, distance);
        }

        int best = -1;
        int bestNear = Integer.MAX_VALUE;
        int bestSteps = Integer.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = toX + dx;
                int y = toY + dy;
                if (x < 0 || y < 0 || x >= width || y >= height) continue;
                int index = y * width + x;
                if (distance[index] < 0) continue;
                int near = dx * dx + dy * dy;
                if (near > bestNear || (near == bestNear && distance[index] >= bestSteps)) {
                    continue;
                }
                bestNear = near;
                bestSteps = distance[index];
                best = index;
            }
        }
        if (best < 0) return null;
        int length = distance[best] + 1;
        if (length < 2) return null;
        short[] out = new short[length];
        int current = best;
        for (int i = length - 1; i >= 0 && current >= 0; i--) {
            out[i] = tileWord(current % width, current / width);
            current = previous[current];
        }
        return out;
    }

    private static int pushGridTile(int[] grid, int width, int height, int x, int y,
            int from, int[] queue, int tail, int[] previous, int[] distance) {
        if (x < 0 || y < 0 || x >= width || y >= height
                || !tileInRange(x) || !tileInRange(y)) return tail;
        int index = y * width + x;
        if (distance[index] >= 0) return tail;
        int cell = grid[index];
        if (cell == 1 || cell == -1) return tail;
        distance[index] = distance[from] + 1;
        previous[index] = from;
        queue[tail++] = index;
        return tail;
    }

    /** Returns the number of unit steps in a straight Chebyshev route. */
    public static int straightStepCount(int fromX, int fromY, int toX, int toY) {
        int dx = Math.abs(toX - fromX);
        int dy = Math.abs(toY - fromY);
        return dx > dy ? dx : dy;
    }

    /** Writes a straight route as packed x/y pairs into a reusable buffer. */
    public static int writeStraightPath(int fromX, int fromY, int toX, int toY,
            int[] out, int offset, int capacity) {
        int steps = straightStepCount(fromX, fromY, toX, toY);
        if (out == null || capacity < steps + 1 || offset < 0
                || offset + steps + 1 > out.length) return 0;
        for (int i = 0; i <= steps; i++) {
            int x = fromX + progress(fromX, toX, steps, i);
            int y = fromY + progress(fromY, toY, steps, i);
            out[offset + i] = (x << 16) | (y & 0xffff);
        }
        return steps + 1;
    }

    /**
     * Writes an 8-neighbour BFS route without allocating a queue or node list.
     * The caller owns reusable buffers sized to the bounded rectangle.
     */
    public static int writeBfsPath(Walkability grid, int fromX, int fromY,
            int toX, int toY, int minX, int minY, int maxX, int maxY,
            int[] queue, int[] previous, int[] out, int capacity) {
        if (grid == null || queue == null || previous == null || out == null
                || minX > maxX || minY > maxY
                || fromX < minX || fromX > maxX || toX < minX || toX > maxX
                || fromY < minY || fromY > maxY || toY < minY || toY > maxY) return 0;
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int cells = width * height;
        if (cells > queue.length || cells > previous.length || capacity <= 0
                || capacity > out.length || !grid.isWalkable(fromX, fromY)
                || !grid.isWalkable(toX, toY)) return 0;
        for (int i = 0; i < cells; i++) previous[i] = -2;
        int start = index(fromX, fromY, minX, minY, width);
        int target = index(toX, toY, minX, minY, width);
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        previous[start] = -1;
        while (head < tail && previous[target] == -2) {
            int current = queue[head++];
            int cx = minX + current % width;
            int cy = minY + current / width;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (nx < minX || nx > maxX || ny < minY || ny > maxY
                            || !grid.isWalkable(nx, ny)) continue;
                    int next = index(nx, ny, minX, minY, width);
                    if (previous[next] != -2) continue;
                    previous[next] = current;
                    queue[tail++] = next;
                }
            }
        }
        if (previous[target] == -2) return 0;
        int length = 0;
        int cursor = target;
        while (cursor >= 0) {
            if (length >= capacity) return 0;
            int x = minX + cursor % width;
            int y = minY + cursor / width;
            out[length++] = pack(x, y);
            cursor = previous[cursor];
        }
        for (int left = 0, right = length - 1; left < right; left++, right--) {
            int swap = out[left];
            out[left] = out[right];
            out[right] = swap;
        }
        return length;
    }

    private static int index(int x, int y, int minX, int minY, int width) {
        return (y - minY) * width + (x - minX);
    }

    private static int pack(int x, int y) {
        return (x << 16) | (y & 0xffff);
    }

    private static int progress(int from, int to, int steps, int index) {
        if (steps == 0) return 0;
        return (to - from) * index / steps;
    }

    /** True when a sampled segment enters the axis-aligned avoidance box. */
    public static boolean segmentNearPoint(int x0, int y0, int dx, int dy, int steps,
            int pointX, int pointY, int radius) {
        if (steps < 0) return false;
        if (steps == 0) steps = 1;
        for (int i = 0; i <= steps; i++) {
            int x = x0 + dx * i / steps;
            int y = y0 + dy * i / steps;
            if (Math.abs(pointX - x) <= radius && Math.abs(pointY - y) <= radius) {
                return true;
            }
        }
        return false;
    }
}
