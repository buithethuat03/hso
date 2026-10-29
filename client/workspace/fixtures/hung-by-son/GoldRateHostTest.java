import luna.features.GoldRateSupport;

/** Host fixture for login-scoped wallet and rolling gold/hour behavior. */
public final class GoldRateHostTest {
    private static int checks;
    private static final String WAIT = "Đang tính toán...";

    private GoldRateHostTest() { }

    public static void main(String[] args) {
        testWalletUnknownAndBaseline();
        testRollingRateAndBalanceDeltas();
        testIdentityAndDisconnect();
        testClockRollbackKeepsLoginTotal();
        testSaturatedTotals();
        System.out.println("GoldRateHostTest PASS checks=" + checks);
    }

    private static void testWalletUnknownAndBaseline() {
        GoldRateSupport.disconnect();
        equal(-1L, GoldRateSupport.current(), "disconnected current wallet is unavailable");
        equal(-1L, GoldRateSupport.received(), "disconnected login total is unavailable");
        equal(-1L, GoldRateSupport.rate(0L), "disconnected rate is unavailable");
        equal("Vàng hiện tại:\n" + WAIT
                + "\nVàng/giờ (ước tính):\n" + WAIT
                + "\nVàng đã nhận từ khi đăng nhập:\n" + WAIT,
                GoldRateSupport.info(0L), "unknown wallet renders all three calculating rows");

        GoldRateSupport.snapshot("server", 1, 0L);
        equal(-1L, GoldRateSupport.current(), "snapshot does not invent a wallet balance");
        equal(-1L, GoldRateSupport.received(), "received waits for the first wallet baseline");
        GoldRateSupport.balance("server", 1, 100L, 1L);
        equal(100L, GoldRateSupport.current(), "first wallet sample becomes current balance");
        equal(0L, GoldRateSupport.received(), "first wallet sample is not counted as received");
        equal(-1L, GoldRateSupport.rate(500001L), "baseline alone does not start gold warmup");
    }

    private static void testRollingRateAndBalanceDeltas() {
        GoldRateSupport.disconnect();
        GoldRateSupport.snapshot("server", 2, 0L);
        GoldRateSupport.balance("server", 2, 100L, 0L);
        GoldRateSupport.balance("server", 2, 110L, 0L);
        equal(10L, GoldRateSupport.received(), "positive delta after baseline counts once");
        equal(-1L, GoldRateSupport.rate(299999L), "rate waits for five-minute warmup");
        equal(120L, GoldRateSupport.rate(300000L), "five-minute rate uses elapsed time");

        GoldRateSupport.balance("server", 2, 140L, 899000L);
        equal(40L, GoldRateSupport.received(), "later positive delta adds to login total");
        equal(120L, GoldRateSupport.rate(900000L), "rate uses capped fifteen-minute denominator");
        equal(120L, GoldRateSupport.rate(1200000L), "recent bucket remains within 900 seconds");
        equal(0L, GoldRateSupport.rate(1800000L), "expired warmed window reports zero");

        GoldRateSupport.balance("server", 2, 90L, 1801000L);
        equal(40L, GoldRateSupport.received(), "wallet decrease does not subtract received gold");
        equal(90L, GoldRateSupport.current(), "wallet decrease updates current balance");
        equal(0L, GoldRateSupport.rate(1801000L), "wallet decrease adds no rate amount");
        GoldRateSupport.balance("server", 2, 95L, 1802000L);
        equal(45L, GoldRateSupport.received(), "increase after decrease counts its positive delta");
        equal(20L, GoldRateSupport.rate(1802000L), "idle resume does not repeat warmup");

        GoldRateSupport.snapshot("server", 2, 1803000L);
        equal(95L, GoldRateSupport.current(), "same-identity snapshot preserves wallet baseline");
        equal(45L, GoldRateSupport.received(), "same-identity snapshot preserves login total");
        equal("Vàng hiện tại:\n95\nVàng/giờ (ước tính):\n20"
                + "\nVàng đã nhận từ khi đăng nhập:\n45",
                GoldRateSupport.info(1803000L), "info rows use the shared whole-number formatter");
    }

    private static void testIdentityAndDisconnect() {
        GoldRateSupport.disconnect();
        GoldRateSupport.snapshot("server", 7, 1000L);
        GoldRateSupport.balance("server", 7, 1000L, 1000L);
        GoldRateSupport.balance("server", 7, 1010L, 2000L);
        equal(10L, GoldRateSupport.received(), "setup earns login gold");

        GoldRateSupport.snapshot("other", 7, 3000L);
        equal(-1L, GoldRateSupport.current(), "server change clears wallet baseline");
        equal(-1L, GoldRateSupport.received(), "server change clears login total until wallet sample");
        equal(-1L, GoldRateSupport.rate(303000L), "server change clears rate warmup");
        GoldRateSupport.balance("other", 7, 500L, 4000L);
        equal(0L, GoldRateSupport.received(), "new identity baseline is not counted");
        GoldRateSupport.balance("other", 7, 550L, 5000L);
        equal(50L, GoldRateSupport.received(), "new identity begins a separate login total");

        GoldRateSupport.disconnect();
        equal(-1L, GoldRateSupport.current(), "disconnect clears wallet state");
        equal(-1L, GoldRateSupport.received(), "disconnect clears received state");
        equal(-1L, GoldRateSupport.rate(6000L), "disconnect clears rate state");
        GoldRateSupport.balance("other", 7, 900L, 7000L);
        equal(-1L, GoldRateSupport.current(), "stale balance after disconnect is ignored");
    }

    private static void testClockRollbackKeepsLoginTotal() {
        GoldRateSupport.disconnect();
        GoldRateSupport.snapshot("server", 3, 1000L);
        GoldRateSupport.balance("server", 3, 100L, 1000L);
        GoldRateSupport.balance("server", 3, 150L, 2000L);
        equal(600L, GoldRateSupport.rate(302000L), "setup rate reaches warm state");

        equal(-1L, GoldRateSupport.rate(1000L), "clock rollback resets the rate warmup");
        equal(150L, GoldRateSupport.current(), "clock rollback keeps wallet baseline");
        equal(50L, GoldRateSupport.received(), "clock rollback preserves login total");
        GoldRateSupport.balance("server", 3, 200L, 2000L);
        equal(100L, GoldRateSupport.received(), "post-rollback positive delta uses prior wallet baseline");
        equal(-1L, GoldRateSupport.rate(301999L), "new positive delta starts a fresh warmup");
        equal(600L, GoldRateSupport.rate(302000L), "fresh rate uses post-rollback delta");
    }

    private static void testSaturatedTotals() {
        GoldRateSupport.disconnect();
        GoldRateSupport.snapshot("server", 4, 0L);
        GoldRateSupport.balance("server", 4, 0L, 0L);
        GoldRateSupport.balance("server", 4, Long.MAX_VALUE, 0L);
        equal(Long.MAX_VALUE, GoldRateSupport.received(), "login total saturates instead of wrapping");
        equal(Long.MAX_VALUE, GoldRateSupport.rate(300000L), "large hourly rate saturates instead of wrapping");
    }

    private static void equal(long expected, long actual, String message) {
        checks++;
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void equal(String expected, String actual, String message) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
