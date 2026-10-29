import luna.features.MovePace;

/** Local-player movement pace under Hung's tempo loop at every menu speed. */
public final class MovePaceHostTest {
    private static int checks;

    private static void check(boolean value, String text) {
        checks++;
        if (!value) throw new AssertionError(text);
    }

    /** Hung's loop: accumulate 2 * scaled elapsed, 40 per update, at most three updates per pass. */
    private static int[] run(int speed, long pass, long millis) {
        MovePace.resetForTest();
        long accumulator = 0, fraction = 0, now = 1000000L;
        int steps = 0, updates = 0, maskSteps = 0;
        for (long end = now + millis; now < end; now += pass) {
            long scaled;
            if (speed >= 0) scaled = pass * speed;
            else { long raw = pass * -speed + fraction; fraction = raw % 10; scaled = raw / 10; }
            accumulator += scaled + scaled;
            for (int i = 0; accumulator >= 40 && i < 3; i++) {
                if (MovePace.step(now)) steps++;
                if ((109 >> (updates & 7) & 1) != 0) maskSteps++;
                updates++;
                accumulator -= 40;
            }
            if (accumulator >= 120) accumulator = 0;
        }
        return new int[]{steps, updates, maskSteps};
    }

    public static void main(String[] args) {
        int[] speeds = {-8, -9, 1, 2, 3};
        String[] labels = {"x0.8", "x0.9", "x1", "x2", "x3"};
        double target = 1000.0 / MovePace.STEP_MS;
        for (int s = 0; s < speeds.length; s++) {
            for (long pass = 16; pass <= 27; pass++) {
                int[] r = run(speeds[s], pass, 10000L);
                double rate = r[0] / 10.0;
                check(rate >= target - 1.0 && rate <= target + 0.5,
                        labels[s] + " pass " + pass + "ms: " + rate + " steps/s, expected ~" + target);
            }
            int[] r = run(speeds[s], 16, 10000L);
            System.out.println(labels[s] + ": old mask " + (r[2] / 10.0) + " steps/s, now " + (r[0] / 10.0) + " steps/s");
        }
        // Slow frames: never faster than the pace; owed steps are made up while updates allow it.
        for (int s = 0; s < speeds.length; s++) for (long pass = 28; pass <= 80; pass += 4) {
            int[] r = run(speeds[s], pass, 10000L);
            double rate = r[0] / 10.0, updates = r[1] / 10.0;
            check(rate <= target + 0.5 && rate >= Math.min(target, updates) - 1.5,
                    labels[s] + " " + pass + "ms frames: " + rate + " steps/s, " + updates + " updates/s");
        }

        MovePace.resetForTest();
        check(MovePace.step(5000L), "The first update after a pause steps immediately");
        check(!MovePace.step(5000L) && !MovePace.step(5027L), "No second step inside 28ms");
        check(MovePace.step(5028L), "Next step at 28ms");
        check(MovePace.step(5060L) && !MovePace.step(5060L), "Leftover time carries, at most one owed step per update");
        check(MovePace.step(5084L), "The carried 4ms brings the next step forward");
        check(MovePace.step(9000L) && !MovePace.step(9010L), "A long idle restarts the pace without a burst");
        check(MovePace.step(8000L), "A clock moving backwards restarts the pace");
        System.out.println("MovePaceHostTest PASS checks=" + checks);
    }
}
