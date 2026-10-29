import luna.features.SkillCastStats;

/** Cast/miss counters of the Special session, gap measurement, lock pacing, retry and log lines. */
public final class SkillCastStatsHostTest {
    private static int checks;

    private static void check(boolean value, String text) {
        checks++;
        if (!value) throw new AssertionError(text);
    }

    /** A CSV line without its session and sequence columns. */
    private static String tail(String row) { return row.substring(row.indexOf(',', row.indexOf(',') + 1) + 1); }

    private static void counters() {
        SkillCastStats.setPaceMode(SkillCastStats.PACE_OFF);
        SkillCastStats.setRetry(false);
        SkillCastStats.reset();
        check(SkillCastStats.skills().length == 0 && "-".equals(SkillCastStats.rate(0, 0)), "Empty session");
        long t = 1000L;
        SkillCastStats.sent(20, t);
        SkillCastStats.sent(20, t + 30);
        SkillCastStats.sent(7, t + 60);
        check(SkillCastStats.casts(20) == 2 && SkillCastStats.casts(7) == 1, "Casts per skill");
        int[] skills = SkillCastStats.skills();
        check(skills.length == 2 && skills[0] == 20 && skills[1] == 7, "Highest ID first");

        int version = SkillCastStats.version();
        SkillCastStats.answered(20, true, t + 100);
        check(SkillCastStats.hits(20) == 1 && SkillCastStats.misses(20) == 1 && SkillCastStats.silent(20) == 1 && SkillCastStats.version() != version,
                "An answer belongs to the newest waiting cast (+30); the older unanswered one is a silent miss");
        SkillCastStats.answered(20, true, t + 130);
        check(SkillCastStats.hits(20) == 1 && SkillCastStats.misses(20) == 1, "Answer without a waiting cast is ignored");
        SkillCastStats.answered(8, true, t + 130);
        check(SkillCastStats.hits(8) == 0 && SkillCastStats.casts(8) == 0, "Answer for a skill never cast is ignored");

        SkillCastStats.expire(t + 60 + SkillCastStats.RESPONSE_MS - 1);
        check(SkillCastStats.misses(7) == 0, "Still waiting under 700ms");
        SkillCastStats.expire(t + 60 + SkillCastStats.RESPONSE_MS);
        check(SkillCastStats.misses(7) == 1 && SkillCastStats.silent(7) == 1, "No answer within 700ms: a silent miss");
        SkillCastStats.sent(7, t + 5000);
        SkillCastStats.answered(7, true, t + 5800);
        check(SkillCastStats.misses(7) == 2 && SkillCastStats.hits(7) == 0, "A late answer finds its cast already expired");
        SkillCastStats.sent(9, t + 9000);
        SkillCastStats.sent(9, t + 8000);
        check(SkillCastStats.misses(9) == 0, "A clock step back does not expire a fresh cast early");

        SkillCastStats.sent(6, t + 20000);
        SkillCastStats.sent(6, t + 20490);
        SkillCastStats.answered(6, 2, 0, t + 20500);
        check(SkillCastStats.dodges(6) == 1 && SkillCastStats.silent(6) == 0 && SkillCastStats.casts(6) == 2,
                "An answer 10ms after the newest cast is too early for it: it goes to the older cast (all-zero damage: né)");
        SkillCastStats.reset();
        for (int i = 0; i < SkillCastStats.PENDING + 3; i++) SkillCastStats.sent(3, t);
        check(SkillCastStats.casts(3) == SkillCastStats.PENDING + 3 && SkillCastStats.misses(3) == 3, "A full waiting queue drops its oldest as misses");
        SkillCastStats.sent(-1, t);
        SkillCastStats.sent(SkillCastStats.MAX_SKILLS, t);
        SkillCastStats.answered(-1, true, t);
        check(SkillCastStats.skills().length == 1 && SkillCastStats.casts(-1) == 0, "Out-of-range skills are ignored");

        check("98,1%".equals(SkillCastStats.rate(306, 6)) && "100%".equals(SkillCastStats.rate(5, 0)) && "0%".equals(SkillCastStats.rate(0, 4))
                && "66,7%".equals(SkillCastStats.rate(2, 1)) && "50%".equals(SkillCastStats.rate(1, 1)), "Rate: rounded to one decimal, comma, no trailing ,0");
        SkillCastStats.reset();
        check(SkillCastStats.skills().length == 0 && SkillCastStats.casts(3) == 0 && SkillCastStats.misses(3) == 0, "Reset clears the session");
    }

    private static void gaps() {
        SkillCastStats.reset();
        SkillCastStats.clearGaps();
        SkillCastStats.attachLog("# test");
        check(SkillCastStats.drainLog().startsWith("# test\n" + SkillCastStats.COLUMNS + "\n"), "Log header and columns");
        long t = 100000L;
        SkillCastStats.sent(2, t);
        SkillCastStats.annotate(20, 150);
        SkillCastStats.sent(1, t + 28);
        SkillCastStats.sent(0, t + 600);
        SkillCastStats.answered(0, 1, 1, t + 680);
        check(SkillCastStats.gapSkills(false).length == 0, "Nothing folds while an earlier send waits");
        SkillCastStats.answered(2, 4, 2, t + 90);
        SkillCastStats.answered(1, 1, 0, t + 110);
        int[] prev = SkillCastStats.gapSkills(false), next = SkillCastStats.gapSkills(true);
        check(prev.length == 1 && prev[0] == 2 && next.length == 2 && next[0] == 1 && next[1] == 0, "Grouped by the previous hit (2) and by the send (1, 0)");
        int[] f = SkillCastStats.fit(false, 2);
        check(f[0] == 2 && f[1] == 600 && f[2] == 0 && f[3] == 1 && f[4] == 1 && f[5] == 1, "Fit after skill 2: miss at 28ms, hit at 600ms -> X 600 " + java.util.Arrays.toString(f));
        int[] bins = SkillCastStats.gapBins(false, 2, 100);
        check(bins.length == 22 && bins[0] == 1 && bins[1] == 0 && bins[12] == 1 && bins[13] == 1, "100ms groups: 0-100 one miss, 600-700 one hit");
        String log = SkillCastStats.drainLog();
        String[] rows = log.split("\n");
        check(rows.length == 3 && tail(rows[0]).equals("2,100000,H,100090,90,20,150,4,2,,,,,Tắt,0")
                && tail(rows[1]).equals("1,100028,N,100110,82,-1,-1,1,0,2,28,2,28,Tắt,0")
                && tail(rows[2]).equals("0,100600,H,100680,80,-1,-1,1,1,2,600,1,572,Tắt,0"), "CSV lines in send order:\n" + log);
        check("".equals(SkillCastStats.drainLog()), "Drained lines are gone");
        SkillCastStats.sent(3, t + 700);
        SkillCastStats.expire(t + 1400);
        rows = SkillCastStats.drainLog().split("\n");
        check(rows.length == 1 && tail(rows[0]).startsWith("3,100700,M,,,") && rows[0].contains(",0,0,0,100,0,100,"), "Silent send: result M, gap since the last hit (skill 0): " + rows[0]);

        // Hits per minute between the first and last send; gap data survives a Special restart.
        SkillCastStats.reset();
        check("-".equals(SkillCastStats.hitsPerMinute()), "No rate before 5 seconds");
        long at = t + 100000;
        for (int i = 0; i <= 60; i++) { SkillCastStats.sent(0, at + i * 1000L); SkillCastStats.answered(0, 1, i % 2, at + i * 1000L + 50); }
        check("30".equals(SkillCastStats.hitsPerMinute()), "30 hits in 60s: " + SkillCastStats.hitsPerMinute());
        check(SkillCastStats.fit(false, 2)[0] == 2, "Gap samples survive a Special restart");
        SkillCastStats.clearGaps();
        check(SkillCastStats.gapSkills(false).length == 0 && SkillCastStats.gapSkills(true).length == 0, "Xóa số đo clears the gap samples");
    }

    /** Lock pacing: the server lock after any skill but Trọng kiếm (0); retry of an unanswered skill. */
    private static void lock() {
        SkillCastStats.reset();
        SkillCastStats.drainLog();
        check("Tắt".equals(SkillCastStats.paceLabel(SkillCastStats.paceMode())) && SkillCastStats.paceReady(0L) && SkillCastStats.lockMs() == 0L, "Pacing off: always ready");
        SkillCastStats.cyclePace();
        check("Khóa 500ms".equals(SkillCastStats.paceLabel(SkillCastStats.paceMode())), "Tắt -> Khóa 500ms");
        SkillCastStats.setPaceMode(3);
        check("Khóa 550ms".equals(SkillCastStats.paceLabel(SkillCastStats.paceMode())) && SkillCastStats.lockMode() && SkillCastStats.lockMs() == 550L, "Khóa 550ms");
        long t = 500000L;
        SkillCastStats.sent(8, t);
        check(!SkillCastStats.paceReady(t + 549) && SkillCastStats.paceReady(t + 550), "Locked 550ms after a skill");
        SkillCastStats.sent(0, t + 560);
        check(SkillCastStats.paceReady(t + 588), "Trọng kiếm sets no lock: the next skill may follow at once");
        SkillCastStats.sent(2, t + 588);
        check(!SkillCastStats.paceReady(t + 1137) && SkillCastStats.paceReady(t + 1138), "The next skill locks again");
        SkillCastStats.cyclePace();
        check("Khóa 575ms".equals(SkillCastStats.paceLabel(SkillCastStats.paceMode())), "Khóa 550 -> 575");
        String[] slower = {"Khóa 600ms", "Khóa 650ms", "Khóa 700ms"};
        for (int i = 0; i < slower.length; i++) {
            SkillCastStats.cyclePace();
            check(slower[i].equals(SkillCastStats.paceLabel(SkillCastStats.paceMode())), "Cycle continues to " + slower[i]);
        }
        check(SkillCastStats.lockMs() == 700L, "Khóa 700ms holds 700ms");
        t = 520000L;
        SkillCastStats.sent(9, t);
        check(!SkillCastStats.paceReady(t + 699) && SkillCastStats.paceReady(t + 700), "Locked 700ms after a skill");
        SkillCastStats.cyclePace();
        check(SkillCastStats.paceMode() == SkillCastStats.PACE_AUTO && "Auto".equals(SkillCastStats.paceLabel(SkillCastStats.paceMode()))
                && SkillCastStats.answerMode() && SkillCastStats.rotateAfterFree(), "Khóa 700ms -> Auto");
        SkillCastStats.cyclePace();
        check(SkillCastStats.paceMode() == SkillCastStats.PACE_OFF && !SkillCastStats.answerMode() && !SkillCastStats.rotateAfterFree(),
                "The cycle returns to Tắt after Auto (no Đáp modes)");
        SkillCastStats.setPaceMode(SkillCastStats.PACE_DEFAULT);

        SkillCastStats.setRetry(true);
        SkillCastStats.reset();
        t = 600000L;
        SkillCastStats.sent(7, t);
        SkillCastStats.expire(t + 699);
        check(SkillCastStats.takeRetry() == -1, "No retry while the answer may still come");
        SkillCastStats.expire(t + 700);
        check(SkillCastStats.takeRetry() == 7 && SkillCastStats.takeRetry() == -1, "Unanswered for 700ms: skill 7 may be cast again, once");
        SkillCastStats.sent(7, t + 720);
        SkillCastStats.expire(t + 1420);
        check(SkillCastStats.takeRetry() == -1, "A second miss in a row is not retried (the server cooldown might run)");
        SkillCastStats.sent(7, t + 9000);
        SkillCastStats.answered(7, 3, 3, t + 9120);
        SkillCastStats.sent(7, t + 18000);
        SkillCastStats.expire(t + 18700);
        check(SkillCastStats.takeRetry() == 7, "An answer re-arms the retry");
        SkillCastStats.sent(5, t + 20000);
        SkillCastStats.sent(5, t + 26000);
        SkillCastStats.answered(5, 1, 1, t + 26100);
        SkillCastStats.expire(t + 30000);
        check(SkillCastStats.takeRetry() == -1, "An older miss answered by a newer cast needs no retry");
        SkillCastStats.sent(4, t + 31000);
        SkillCastStats.setRetry(false);
        SkillCastStats.expire(t + 32000);
        check(SkillCastStats.takeRetry() == -1, "Tung lại Tắt: no retries");
        String notes = SkillCastStats.drainLog();
        check(notes.contains("# pace Khóa 500ms\n") && notes.contains("# pace Khóa 550ms\n") && notes.contains("# retry skill 7\n")
                && notes.contains("# retry Tắt\n") && notes.contains(",Auto,575\n"), "Mode changes, retries and the lock (default Auto, G 575ms) are logged:\n" + notes);
        SkillCastStats.setRetry(true);
    }

    private static long readyAt(long from) {
        long t = from;
        while (!SkillCastStats.paceReady(t)) t++;
        return t;
    }

    /**
     * A simulated server: a non-free send is accepted when it comes serverLock ms after the last
     * accepted one and answered rtt ms later; a rejected send gets no answer. Every other cycle
     * starts with đòn 0 when free is set. Returns the time after the last cycle.
     */
    private static long simulate(long t, int cycles, int serverLock, int rtt, boolean free) {
        long accepted = -100000L;
        for (int c = 0; c < cycles; c++) {
            t = readyAt(t);
            if (free && c % 2 == 0) SkillCastStats.sent(0, t);
            long s = readyAt(t);
            int skill = 2 + c % 3;
            SkillCastStats.sent(skill, s);
            if (s - accepted >= serverLock) { SkillCastStats.answered(skill, 1, 1, s + rtt); accepted = s; }
            t = s + 1;
        }
        return t;
    }

    /** Auto: lock end from G and the answer guard, and G following simulated servers. */
    private static void autoLock() {
        SkillCastStats.setPaceMode(4);
        check(!SkillCastStats.answerMode() && !SkillCastStats.rotateAfterFree() && SkillCastStats.paceWait(0L) == -1L
                && !SkillCastStats.paceEndedSince(0L, 1L), "Khóa modes keep the old frame cadence");
        SkillCastStats.setPaceMode(SkillCastStats.PACE_AUTO);
        int[] st = SkillCastStats.autoState();
        check(st[0] == 575 && SkillCastStats.lockMs() == 575L && SkillCastStats.answerMode() && SkillCastStats.rotateAfterFree(),
                "Auto starts at G 575ms, sends after đòn 0 at once and moves its target behind");
        long t = 2000000L;
        SkillCastStats.sent(8, t);
        st = SkillCastStats.autoState();
        long g = 575 + (st[1] < 0 ? -SkillCastStats.AUTO_DITHER : SkillCastStats.AUTO_DITHER);
        check(readyAt(t) == t + Math.max(575, g), "No answer yet: max(G of this lock's arm, 575ms) from the send");
        SkillCastStats.answered(8, 1, 1, t + 20);
        check(readyAt(t) == t + g, "Low ping (answer after 20ms): G from the send binds (" + g + "ms)");
        long t2 = t + 3000;
        SkillCastStats.sent(7, t2);
        st = SkillCastStats.autoState();
        g = 575 + (st[1] < 0 ? -SkillCastStats.AUTO_DITHER : SkillCastStats.AUTO_DITHER);
        SkillCastStats.answered(7, 1, 1, t2 + 340);
        check(readyAt(t2) == t2 + Math.max(g, 640), "Delayed answer (340ms): 300ms after the answer binds");
        SkillCastStats.sent(0, t2 + 640);
        check(SkillCastStats.paceReady(t2 + 640), "Đòn 0 sets no lock: the next skill may go in the same frame");

        SkillCastStats.drainLog();
        t = simulate(t2 + 5000, 1500, 0, 20, true);
        st = SkillCastStats.autoState();
        check(st[0] == SkillCastStats.AUTO_G_MIN, "Low ping, no server lock: both arms hit, the shorter wins and G falls to 525 (" + st[0] + ")");
        String notes = SkillCastStats.drainLog();
        check(notes.contains("# auto shorter (short ") && notes.contains(",Auto -,") && notes.contains(",Auto +,"),
                "Decisions and arms are logged:\n" + notes.substring(0, Math.min(600, notes.length())));

        t = simulate(t + 5000, 2500, 560, 20, true);
        st = SkillCastStats.autoState();
        notes = SkillCastStats.drainLog();
        check(notes.contains("# auto longer (fallback sends hit after unanswered sends)"), "G at 525 under a 560ms lock: fallback strikes raise it at once");
        check(st[0] >= 550 && st[0] <= 615, "Low ping, server lock 560ms: G settles near the edge (" + st[0] + ")");
        int settled = st[0];

        t = simulate(t + 5000, 400, 500, 340, true);
        check(SkillCastStats.autoState()[0] == settled, "Very high ping (answer guard binds): G is left alone (" + SkillCastStats.autoState()[0] + ")");

        t = simulate(t + 5000, 300, 1000000, 20, true);
        check(SkillCastStats.autoState()[0] == settled, "Glitch (every send rejected): no strike, no step (" + SkillCastStats.autoState()[0] + ")");
        SkillCastStats.setPaceMode(SkillCastStats.PACE_OFF);
        check(SkillCastStats.paceReady(t), "Tắt after Auto: always ready");
    }

    /** SkillCastLog appends the CSV lines to <hso.log.dir>/skill-gap-<ms>.csv. */
    private static void fileLog() {
        try {
            java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"), "skill-gap-test-" + System.nanoTime());
            System.setProperty("hso.log.dir", dir.getPath());
            luna.features.SkillCastLog.start();
            String path = luna.features.SkillCastLog.path();
            check(path != null && luna.features.SkillCastLog.error() == null && new java.io.File(path).getParentFile().equals(dir.getAbsoluteFile()), "Log file under hso.log.dir: " + path);
            SkillCastStats.reset();
            SkillCastStats.sent(7, 900000L);
            SkillCastStats.answered(7, 5, 5, 900070L);
            luna.features.SkillCastLog.flush();
            java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(path), "UTF-8"));
            StringBuffer text = new StringBuffer();
            try { for (String line; (line = in.readLine()) != null;) text.append(line).append('\n'); } finally { in.close(); }
            String all = text.toString();
            check(all.startsWith("# Hung_By_Son skill gap log") && all.contains(SkillCastStats.COLUMNS) && all.contains(",7,900000,H,900070,70,") && all.contains("(Tấn công đặc biệt BẬT)"),
                    "The file holds the header, the columns, the session and the cast:\n" + all);
        } catch (java.io.IOException e) {
            throw new AssertionError(e.toString());
        }
    }

    public static void main(String[] args) {
        check(SkillCastStats.paceMode() == SkillCastStats.PACE_DEFAULT && SkillCastStats.retryEnabled(), "Defaults: Auto, Tung lại Bật");
        counters();
        gaps();
        lock();
        autoLock();
        SkillCastStats.detachLog();
        SkillCastStats.note("x");
        check("".equals(SkillCastStats.drainLog()), "Detached: no lines kept");
        fileLog();
        System.out.println("SkillCastStatsHostTest PASS checks=" + checks);
    }
}
