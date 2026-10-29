import luna.core.PathPlanner;
import luna.core.WorldRoutePlanner;

/** Host regression for portal graph and packed movement paths. */
public final class LunaRouteHostTest {

    public static void main(String[] args) {
        check(WorldRoutePlanner.mapIdOfName("C\u1ed5ng \u0111\u1ecba ng\u1ee5c") == 52,
                "portal label resolves to map 52");
        check(WorldRoutePlanner.mapIdOfName("Thung L\u0169ng K\u1ef3 B\u00ed") == 8,
                "portal label resolves to map 8");
        check(WorldRoutePlanner.nextHop(52, 8) == 45,
                "52 -> 8 follows the recorded portal route");
        check(WorldRoutePlanner.nextHop(-1, 8) == -1,
                "invalid map id is rejected");
        short[] path = PathPlanner.straightPath(10, 10, 30, 30, 64);
        check(path != null && path.length == 21
                && path[path.length - 1] == PathPlanner.tileWord(30, 30),
                "straight path keeps engine tile encoding");
        System.out.println("OK - Luna route host test");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
