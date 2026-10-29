import luna.features.SaleReturnScrollController;

/** Host coverage for the one-shot return-scroll wait state. */
public final class SaleReturnScrollHostTest {
    private static int checks;

    private SaleReturnScrollHostTest() { }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        SaleReturnScrollController.cancel();
        check(SaleReturnScrollController.poll(0L, 5, true, 1)
                        == SaleReturnScrollController.NONE,
                "poll without a trip returns NONE");
        check(!SaleReturnScrollController.waiting(), "controller starts idle");

        SaleReturnScrollController.begin(1000L, 41);
        check(SaleReturnScrollController.waiting(), "begin starts one wait");
        check(SaleReturnScrollController.fallbackDestination() == 41,
                "begin stores the native destination");
        check(SaleReturnScrollController.poll(1000L, 5, true, 1)
                        == SaleReturnScrollController.WAITING,
                "unsupported map remains in the wait");
        check(SaleReturnScrollController.poll(5000L, 5, true, 1)
                        == SaleReturnScrollController.WAITING,
                "repeated poll does not issue another scroll or finish early");
        check(SaleReturnScrollController.waiting(), "polling preserves the active wait");
        check(SaleReturnScrollController.fallbackDestination() == 41,
                "polling does not overwrite fallback destination");
        check(SaleReturnScrollController.poll(10999L, 5, true, 1)
                        == SaleReturnScrollController.WAITING,
                "wait continues one millisecond before its deadline");
        check(SaleReturnScrollController.poll(11000L, 5, true, 1)
                        == SaleReturnScrollController.FALLBACK,
                "deadline returns FALLBACK at exactly ten seconds");
        check(!SaleReturnScrollController.waiting(), "fallback clears the wait");
        check(SaleReturnScrollController.fallbackDestination() == 41,
                "fallback destination survives wait completion");
        check(SaleReturnScrollController.poll(11001L, 5, true, 1)
                        == SaleReturnScrollController.NONE,
                "completed fallback is reported only once");

        int[] villages = {1, 33, 70};
        for (int i = 0; i < villages.length; i++) {
            SaleReturnScrollController.begin(20000L + i * 20000L, 50 + i);
            check(SaleReturnScrollController.poll(20001L + i * 20000L,
                            villages[i], true, 1) == SaleReturnScrollController.ARRIVED,
                    "sale village " + villages[i] + " completes as arrival");
            check(!SaleReturnScrollController.waiting(), "arrival clears its wait");
            check(SaleReturnScrollController.fallbackDestination() == 50 + i,
                    "arrival retains fallback destination");
            check(SaleReturnScrollController.poll(20002L + i * 20000L,
                            villages[i], true, 1) == SaleReturnScrollController.NONE,
                    "arrival is reported only once");
        }

        SaleReturnScrollController.begin(80000L, 62);
        check(SaleReturnScrollController.poll(80001L, 5, false, 1)
                        == SaleReturnScrollController.CANCELLED,
                "inactive trip cancels the wait");
        check(!SaleReturnScrollController.waiting(), "inactive cancellation clears wait");
        check(SaleReturnScrollController.fallbackDestination() == 62,
                "inactive cancellation retains destination");

        SaleReturnScrollController.begin(90000L, 63);
        check(SaleReturnScrollController.poll(90001L, 5, true, 0)
                        == SaleReturnScrollController.CANCELLED,
                "native sale state change cancels the wait");
        check(!SaleReturnScrollController.waiting(), "state cancellation clears wait");

        SaleReturnScrollController.begin(100000L, 64);
        check(SaleReturnScrollController.poll(100001L, 5, true, 1)
                        == SaleReturnScrollController.WAITING,
                "manual-cancel setup is waiting");
        SaleReturnScrollController.cancel();
        check(!SaleReturnScrollController.waiting(), "manual cancel clears wait");
        check(SaleReturnScrollController.fallbackDestination() == 64,
                "manual cancel does not consume destination");
        check(SaleReturnScrollController.poll(100002L, 5, true, 1)
                        == SaleReturnScrollController.NONE,
                "manual cancellation ends the current trip");

        SaleReturnScrollController.begin(120000L, 65);
        check(SaleReturnScrollController.poll(119999L, 5, true, 1)
                        == SaleReturnScrollController.FALLBACK,
                "clock rollback falls back immediately");
        check(SaleReturnScrollController.fallbackDestination() == 65,
                "rollback fallback retains destination");

        SaleReturnScrollController.begin(130000L, 70);
        check(SaleReturnScrollController.poll(130001L, 5, true, 1)
                        == SaleReturnScrollController.WAITING,
                "new trip waits on unsupported map");
        check(SaleReturnScrollController.fallbackDestination() == 70,
                "new trip replaces the prior destination");
        check(SaleReturnScrollController.poll(140000L, 5, true, 1)
                        == SaleReturnScrollController.FALLBACK,
                "new trip uses its own timeout origin");
        check(SaleReturnScrollController.fallbackDestination() == 70,
                "new trip fallback keeps its destination");

        check(SaleReturnScrollController.isSaleVillage(1), "map 1 is a sale village");
        check(SaleReturnScrollController.isSaleVillage(33), "map 33 is a sale village");
        check(SaleReturnScrollController.isSaleVillage(70), "map 70 is a sale village");
        check(!SaleReturnScrollController.isSaleVillage(5), "unsupported map is not a village");

        System.out.println("SaleReturnScrollHostTest PASS checks=" + checks);
    }
}
