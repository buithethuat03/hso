import luna.core.FakeLunaClock;
import luna.core.MovementArbiter;
import luna.core.PathPlanner;
import luna.ui.NoticeReader;

/** Minimal host test for semantic code; it does not load or decompile a JAR. */
public final class LunaSemanticHostTest {

    public static void main(String[] args) {
        FakeLunaClock clock = new FakeLunaClock(10L);
        check(clock.nowMillis() == 10L, "fake clock initial value");
        clock.advance(90L);
        check(clock.nowMillis() == 100L, "fake clock advance");

        MovementArbiter arbiter = new MovementArbiter();
        check(arbiter.acquire(MovementArbiter.OWNER_AUTOTRADE), "autotrade acquires free arbiter");
        check(arbiter.acquire(MovementArbiter.OWNER_N4), "N4 takes ownership from AutoTrade");
        check(!arbiter.acquire(MovementArbiter.OWNER_AUTOTRADE), "lower priority cannot overwrite owner");
        check(arbiter.acquire(MovementArbiter.OWNER_COMBAT), "higher priority preempts owner");
        int[] path = new int[4];
        check(arbiter.submitPath(MovementArbiter.OWNER_COMBAT, path, 4), "owner submits path");
        check(!arbiter.submitPath(MovementArbiter.OWNER_N4, path, 1), "non-owner cannot submit path");
        check(arbiter.release(MovementArbiter.OWNER_COMBAT), "owner releases path");

        int[] route = new int[8];
        int count = PathPlanner.writeStraightPath(2, 4, 5, 6, route, 0, route.length);
        check(count == 4, "straight route includes start and three steps");
        check(route[0] == ((2 << 16) | 4), "route starts at player");
        check(route[3] == ((5 << 16) | 6), "route ends at destination");

        int[] queue = new int[36];
        int[] previous = new int[36];
        int[] bfs = new int[36];
        int bfsCount = PathPlanner.writeBfsPath(new PathPlanner.Walkability() {
            public boolean isWalkable(int x, int y) {
                return !(x == 2 && y < 5);
            }
        }, 0, 0, 4, 0, 0, 0, 4, 5, queue, previous, bfs, bfs.length);
        check(bfsCount > 0, "BFS finds a route around a blocked column");
        check(bfs[0] == 0 && bfs[bfsCount - 1] == (4 << 16), "BFS endpoints");

        check(NoticeReader.isHorseItemUnavailable("Không thể sử dụng vật phẩm này"),
                "horse transient failure is recognized");
        check(NoticeReader.isOtherMountInUse("Bạn đang sử dụng thú cưỡi khác"),
                "already-mounted failure is recognized");

        check(NoticeReader.isLoginCooldownNotice("sau 9.9s nua moi co the vao"),
                "login cooldown notice is recognized");
        check(NoticeReader.isLoginCooldownNotice("sau 9.9s nữa mới có thể vào!"),
                "utf8 login cooldown notice is recognized");
        check(NoticeReader.isLoginCooldownNotice("sau 9,9s nữa mới có thể vào!"),
                "unicode comma login cooldown notice is recognized");
        check(NoticeReader.loginCooldownDelayMillis("sau 9,9s nữa mới có thể vào!") >= 9900L,
                "cooldown delay is parsed from the notice");
        check(!NoticeReader.isLoginCooldownNotice("sai mat khau"),
                "ordinary login error is not treated as cooldown");

        System.out.println("OK - Luna semantic host test");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
