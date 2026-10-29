package luna.features;

/**
 * Per-skill attack sends of one Special session and how the server answered them.
 * A send is a hit when the server's combat answer for that skill damages at least one
 * target, a miss when every damage is 0 ("né", dodged) or no answer arrives within
 * RESPONSE_MS ("mất", silent). The live server drops a rejected send without any answer, so
 * an answer belongs to the newest unanswered send of its skill (at least MIN_ANSWER_MS old);
 * older unanswered sends of that skill are misses. The counters restart whenever Tấn công
 * đặc biệt is turned ON.
 *
 * Gap measurement (kept across sessions until clearGaps): every resolved send, in send
 * order, is binned by the time since the previous hit, once grouped by the skill of that
 * hit ("chiêu trước") and once by its own skill ("chiêu sau"); fit() estimates the step X.
 * Lock pacing (measured live 2026-10-24): after an accepted send of any skill but
 * FREE_SKILL (Trọng kiếm) the server rejects sends for ~500ms, so paceReady holds the loop
 * LOCK_MS after the last such send; "Auto" learns its own wait (see the Auto constants below).
 * Retry: a skill whose newest send went unanswered may get
 * its cooldown cleared once until it is answered again (takeRetry). Resolved sends also
 * become CSV lines for the file log (attachLog/drainLog).
 */
public final class SkillCastStats {
    public static final int MAX_SKILLS = 64;
    public static final int PENDING = 32;
    public static final long RESPONSE_MS = 700L, MIN_ANSWER_MS = 30L;
    public static final int LOG = 512;
    public static final int GAP_SKILLS = 32, BIN_MS = 25, BINS = 41;
    public static final int HIT = 1, DODGE = 2, SILENT = 3;
    // Khóa 575ms (mode 4) was the best send-anchored mode live: 137 hits/min, 92% (2026-10-24).
    // Default Auto (mode 8) at the user's request (2026-10-29); the Đáp modes were removed.
    public static final int PACE_OFF = 0, PACE_DEFAULT = 8, FREE_SKILL = 0;
    // 600/650/700ms added 2026-10-29 for slower live tests; the cycle continues after 575.
    public static final int[] LOCK_MS = {500, 525, 550, 575, 600, 650, 700};
    public static final long ANSWER_FALLBACK_MS = 575L;
    // Auto (mode 8, after Khóa 700ms; second version of 2026-10-29):
    //  - The lock ends at max(send + G, answer + AUTO_ANSWER_MS), or max(send + G, send + 575ms)
    //    while no answer came. The server lock (~500ms) runs from when it received the previous
    //    accepted send, so G binds at low ping and the answer guard (which includes the uplink delay)
    //    at high ping. Đáp 400ms alone collapsed to ~80 hits/min on a low-ping VPS (sent ~420ms
    //    after the previous send, inside the lock). The 300ms guard only binds when the answer comes
    //    late (RTT above ~G - 300, a delayed uplink); at 350ms it stretched every high-ping cycle to
    //    RTT + 350 and lost ~6% to Khóa 575 in simulation.
    //  - G is learned: each lock picks a random arm G -/+ AUTO_DITHER; a send made right at a lock end
    //    that G set scores its arm (hit, time since the previous non-free send). With AUTO_N samples
    //    per arm, G steps AUTO_STEP toward the arm with more hits per second; equal steps both ways, so
    //    noise cannot drift it. Misses not caused by the lock hit both arms alike; windows where both
    //    arms mostly miss are skipped as a glitch.
    //  - Too short: after a G-set send went unanswered, the 575ms fallback send hitting is a strike;
    //    AUTO_STRIKES strikes within AUTO_STRIKE_SPAN scored sends raise G by AUTO_BUMP at once.
    //  - The next skill goes in Trọng kiếm/đòn 0's frame with đòn 0's target moved to the end of its
    //    list (live 2026-10-29: 94% vs 74% hits right after "Bắn thường", whose target just died).
    public static final int PACE_AUTO = 8;
    public static final int AUTO_G_START = 575, AUTO_G_MIN = 525, AUTO_G_MAX = 650, AUTO_ANSWER_MS = 300;
    public static final int AUTO_DITHER = 15, AUTO_STEP = 5, AUTO_N = 40, AUTO_BUMP = 15, AUTO_STRIKES = 3, AUTO_STRIKE_SPAN = 12;
    public static final long AUTO_BOUNDARY_MS = 40L, AUTO_CYCLE_MAX_MS = 2000L;
    private static final int ARM_SHORT = 1, ARM_LONG = 2, ARM_FALLBACK = 3, ARM_ROTATED = 4;
    public static final String COLUMNS = "session,seq,skill,sent_ms,result,answer_ms,rtt_ms,targets_sent,nearest,targets_answered,damaged,"
            + "prev_hit_skill,gap_prev_hit_ms,prev_sent_skill,gap_prev_sent_ms,pace,wait_ms";
    private static final int LINE_LIMIT = 262144;
    private static final Object LOCK = new Object();
    private static final int[] casts = new int[MAX_SKILLS];
    private static final int[] hits = new int[MAX_SKILLS];
    private static final int[] misses = new int[MAX_SKILLS];
    private static final int[] dodges = new int[MAX_SKILLS];
    private static final int[] silent = new int[MAX_SKILLS];
    private static final long[][] pending = new long[MAX_SKILLS][PENDING];
    private static final int[][] pendingSeq = new int[MAX_SKILLS][PENDING];
    private static final int[] head = new int[MAX_SKILLS];
    private static final int[] waiting = new int[MAX_SKILLS];
    private static int version;
    private static int session;
    private static long firstSent = -1L, lastSent = -1L;
    private static int lastSentSkill = -1;

    // Sends in send order: resolved entries are folded (gap bins + log line) oldest first.
    private static final int[] logSeq = new int[LOG], logSkill = new int[LOG];
    private static final long[] logAt = new long[LOG], logAnswerAt = new long[LOG], logWait = new long[LOG];
    private static final byte[] logState = new byte[LOG], logArm = new byte[LOG];
    private static final long[] logCycle = new long[LOG];
    private static final int[] logTargets = new int[LOG], logNearest = new int[LOG], logAnswered = new int[LOG], logDamaged = new int[LOG];
    private static int seq, cursor;
    private static int foldSkill = -1;
    private static long foldAt;

    private static final int[][] prevTotal = new int[GAP_SKILLS][BINS], prevHits = new int[GAP_SKILLS][BINS];
    private static final int[][] nextTotal = new int[GAP_SKILLS][BINS], nextHits = new int[GAP_SKILLS][BINS];
    private static int anchorSkill = -1;
    private static long anchorAt;
    private static int paceMode = PACE_DEFAULT;
    private static boolean lockSet;
    private static long lockAt;
    private static int lockSeq = -1;
    private static long lockAnswerAt = -1L;
    private static int autoG = AUTO_G_START, lockArm = 1, autoSeed = 0x2F6E2B1, strikes, strikeSpan;
    private static boolean lastScoredG;
    // Auto samples of the short and long arm of G.
    private static final int[] armSends = new int[2], armHits = new int[2];
    private static final long[] armTime = new long[2];
    private static boolean retryEnabled = true;
    private static final boolean[] retryFlag = new boolean[MAX_SKILLS], retried = new boolean[MAX_SKILLS];
    private static final int[] lastSeqOf = new int[MAX_SKILLS];
    private static StringBuffer lines;

    private SkillCastStats() { }

    public static void reset() {
        synchronized (LOCK) {
            for (int i = 0; i < MAX_SKILLS; i++) {
                casts[i] = hits[i] = misses[i] = dodges[i] = silent[i] = head[i] = waiting[i] = 0;
                retryFlag[i] = retried[i] = false;
            }
            cursor = seq;
            foldSkill = -1;
            firstSent = lastSent = -1L;
            lastSentSkill = -1;
            session++;
            note(new StringBuffer("# session ").append(session).append(" (Tấn công đặc biệt BẬT)").toString());
            version++;
        }
    }

    public static void sent(int skill, long now) {
        if (skill < 0 || skill >= MAX_SKILLS) return;
        synchronized (LOCK) {
            for (int i = 0; i < MAX_SKILLS; i++) expire(i, now);
            if (waiting[skill] == PENDING) resolve(skill, SILENT, -1L, 0, 0);
            if (seq - cursor >= LOG) cursor = seq - LOG + 1;
            int i = index(seq);
            logSeq[i] = seq; logSkill[i] = skill; logAt[i] = now; logState[i] = 0; logAnswerAt[i] = -1L;
            logTargets[i] = -1; logNearest[i] = -1; logAnswered[i] = logDamaged[i] = 0;
            logWait[i] = waitMs(paceMode);
            logArm[i] = 0; logCycle[i] = -1L;
            if (paceMode == PACE_AUTO) autoSent(skill, now, i);
            lastSeqOf[skill] = seq;
            if (skill != FREE_SKILL) { lockAt = now; lockSet = true; lockSeq = seq; lockAnswerAt = -1L; }
            int slot = (head[skill] + waiting[skill]) % PENDING;
            pending[skill][slot] = now;
            pendingSeq[skill][slot] = seq;
            waiting[skill]++;
            seq++;
            casts[skill]++;
            if (firstSent < 0L) firstSent = now;
            lastSent = now;
            lastSentSkill = skill;
            fold();
            version++;
        }
    }

    /** The attack loop's target list for the send just made: count and nearest distance. */
    public static void annotate(int targets, int nearest) {
        synchronized (LOCK) {
            int i = index(seq - 1);
            if (logSeq[i] != seq - 1) return;
            logTargets[i] = targets;
            logNearest[i] = nearest;
        }
    }

    /** The server's combat answer for one of our sends of this skill. */
    public static void answered(int skill, boolean damaged, long now) {
        answered(skill, damaged ? 1 : 0, damaged ? 1 : 0, now);
    }

    /** The answer with its target count and how many of them took damage. */
    public static void answered(int skill, int targets, int damaged, long now) {
        if (skill < 0 || skill >= MAX_SKILLS) return;
        synchronized (LOCK) {
            expire(skill, now);
            int n = waiting[skill];
            if (n == 0) return;
            int pick = n - 1;
            while (pick > 0 && now - pending[skill][(head[skill] + pick) % PENDING] < MIN_ANSWER_MS) pick--;
            if (lockSet && pendingSeq[skill][(head[skill] + pick) % PENDING] == lockSeq) lockAnswerAt = now;
            for (int k = 0; k < pick; k++) resolve(skill, SILENT, -1L, 0, 0);
            resolve(skill, damaged > 0 ? HIT : DODGE, now, targets, damaged);
            retried[skill] = retryFlag[skill] = false;
            fold();
            version++;
        }
    }

    /** Sends left unanswered for RESPONSE_MS become misses. */
    public static void expire(long now) {
        synchronized (LOCK) {
            for (int i = 0; i < MAX_SKILLS; i++) expire(i, now);
            fold();
        }
    }

    private static void expire(int skill, long now) {
        while (waiting[skill] > 0) {
            long at = pending[skill][head[skill]];
            if (now < at || now - at < RESPONSE_MS) return;
            int s = pendingSeq[skill][head[skill]];
            resolve(skill, SILENT, -1L, 0, 0);
            if (retryEnabled && waiting[skill] == 0 && s == lastSeqOf[skill] && !retried[skill]) retryFlag[skill] = true;
            version++;
        }
    }

    private static void resolve(int skill, int state, long answerAt, int targets, int damaged) {
        int s = pendingSeq[skill][head[skill]];
        head[skill] = (head[skill] + 1) % PENDING;
        waiting[skill]--;
        if (state == HIT) hits[skill]++;
        else { misses[skill]++; if (state == DODGE) dodges[skill]++; else silent[skill]++; }
        int i = index(s);
        if (logSeq[i] == s && logArm[i] > 0 && logArm[i] < ARM_ROTATED) { autoSample(logArm[i], state == HIT, logCycle[i]); logArm[i] = (byte)-logArm[i]; }
        if (logSeq[i] != s || s - cursor < 0) return;
        logState[i] = (byte)state;
        logAnswerAt[i] = answerAt;
        logAnswered[i] = targets;
        logDamaged[i] = damaged;
    }

    private static int index(int s) { return (s % LOG + LOG) % LOG; }

    /** Resolved sends, oldest first: gap bins since the previous hit and the CSV line. */
    private static void fold() {
        while (cursor != seq) {
            int i = index(cursor);
            if (logSeq[i] != cursor) { cursor++; continue; }
            int state = logState[i];
            if (state == 0) return;
            int skill = logSkill[i];
            long at = logAt[i];
            boolean hit = state == HIT;
            long gap = anchorSkill >= 0 && at >= anchorAt ? at - anchorAt : -1L;
            if (gap >= 0L && skill < GAP_SKILLS && anchorSkill < GAP_SKILLS) {
                int bin = (int)Math.min(gap / BIN_MS, BINS - 1);
                prevTotal[anchorSkill][bin]++;
                nextTotal[skill][bin]++;
                if (hit) { prevHits[anchorSkill][bin]++; nextHits[skill][bin]++; }
            }
            if (lines != null) {
                StringBuffer b = new StringBuffer(96);
                b.append(session).append(',').append(cursor).append(',').append(skill).append(',').append(at).append(',')
                        .append(state == HIT ? 'H' : state == DODGE ? 'N' : 'M').append(',');
                if (logAnswerAt[i] >= 0L) b.append(logAnswerAt[i]).append(',').append(logAnswerAt[i] - at);
                else b.append(',');
                b.append(',').append(logTargets[i]).append(',').append(logNearest[i]).append(',').append(logAnswered[i]).append(',').append(logDamaged[i]).append(',');
                if (gap >= 0L) b.append(anchorSkill).append(',').append(gap); else b.append(',');
                b.append(',');
                if (foldSkill >= 0 && at >= foldAt) b.append(foldSkill).append(',').append(at - foldAt); else b.append(',');
                b.append(',').append(paceLabel(paceMode)).append(armLabel(logArm[i])).append(',').append(logWait[i]);
                line(b.toString());
            }
            if (hit) { anchorSkill = skill; anchorAt = at; }
            foldSkill = skill;
            foldAt = at;
            cursor++;
            version++;
        }
    }

    public static int version() { synchronized (LOCK) { return version; } }
    public static int casts(int skill) { synchronized (LOCK) { return valid(skill) ? casts[skill] : 0; } }
    public static int hits(int skill) { synchronized (LOCK) { return valid(skill) ? hits[skill] : 0; } }
    public static int misses(int skill) { synchronized (LOCK) { return valid(skill) ? misses[skill] : 0; } }
    public static int dodges(int skill) { synchronized (LOCK) { return valid(skill) ? dodges[skill] : 0; } }
    public static int silent(int skill) { synchronized (LOCK) { return valid(skill) ? silent[skill] : 0; } }
    private static boolean valid(int skill) { return skill >= 0 && skill < MAX_SKILLS; }

    /** Skills sent this session, highest ID first (the Special priority order). */
    public static int[] skills() {
        synchronized (LOCK) {
            int n = 0;
            for (int i = 0; i < MAX_SKILLS; i++) if (casts[i] > 0) n++;
            int[] out = new int[n];
            for (int i = MAX_SKILLS - 1, k = 0; i >= 0; i--) if (casts[i] > 0) out[k++] = i;
            return out;
        }
    }

    /** "98,4%" of answered sends that hit; "-" before any answer. */
    public static String rate(int hit, int miss) {
        int answered = hit + miss;
        if (answered <= 0) return "-";
        int tenths = (int)(((long)hit * 1000L + answered / 2) / answered);
        return tenths(tenths).append('%').toString();
    }

    private static StringBuffer tenths(int tenths) {
        StringBuffer b = new StringBuffer().append(tenths / 10);
        if (tenths % 10 != 0) b.append(',').append(tenths % 10);
        return b;
    }

    /** Hits per minute between the first and the last send of the session; "-" under 5 seconds. */
    public static String hitsPerMinute() {
        synchronized (LOCK) {
            long span = lastSent - firstSent;
            if (firstSent < 0L || span < 5000L) return "-";
            long total = 0;
            for (int i = 0; i < MAX_SKILLS; i++) total += hits[i];
            return tenths((int)((total * 600000L + span / 2) / span)).toString();
        }
    }

    // ------------------------------------------------------------------ gaps

    /** Skills with gap samples, highest ID first, grouped by the previous hit or by the send itself. */
    public static int[] gapSkills(boolean byNext) {
        synchronized (LOCK) {
            int[][] total = byNext ? nextTotal : prevTotal;
            int n = 0;
            for (int s = 0; s < GAP_SKILLS; s++) if (sum(total[s], 0, BINS) > 0) n++;
            int[] out = new int[n];
            for (int s = GAP_SKILLS - 1, k = 0; s >= 0; s--) if (sum(total[s], 0, BINS) > 0) out[k++] = s;
            return out;
        }
    }

    /**
     * {samples, xMs, hitsBelow, sendsBelow, hitsAbove, sendsAbove}: the threshold X (a multiple
     * of BIN_MS, BINS-1 bins = 1000ms+) that best splits misses below from hits above; on a tie
     * the larger X. Null without samples.
     */
    public static int[] fit(boolean byNext, int skill) {
        if (skill < 0 || skill >= GAP_SKILLS) return null;
        synchronized (LOCK) { return fit((byNext ? nextTotal : prevTotal)[skill], (byNext ? nextHits : prevHits)[skill]); }
    }

    private static int[] fit(int[] total, int[] hit) {
        int samples = sum(total, 0, BINS);
        if (samples == 0) return null;
        int allHits = sum(hit, 0, BINS);
        int best = 0, bestScore = -1, missBelow = 0, hitBelow = 0;
        for (int k = 0; k < BINS; k++) {
            int score = missBelow + allHits - hitBelow;
            if (score >= bestScore) { bestScore = score; best = k; }
            missBelow += total[k] - hit[k];
            hitBelow += hit[k];
        }
        int hb = sum(hit, 0, best), sb = sum(total, 0, best);
        return new int[]{samples, best * BIN_MS, hb, sb, allHits - hb, samples - sb};
    }

    /** {sends, hits} per group of `width` ms from 0, the last group open-ended (1000ms+). */
    public static int[] gapBins(boolean byNext, int skill, int width) {
        if (skill < 0 || skill >= GAP_SKILLS || width < BIN_MS) return new int[0];
        synchronized (LOCK) {
            int per = width / BIN_MS, groups = (BINS - 1 + per - 1) / per + 1;
            int[] out = new int[groups * 2];
            int[] total = (byNext ? nextTotal : prevTotal)[skill], hit = (byNext ? nextHits : prevHits)[skill];
            for (int b = 0; b < BINS; b++) {
                int g = b == BINS - 1 ? groups - 1 : b / per;
                out[g * 2] += total[b];
                out[g * 2 + 1] += hit[b];
            }
            return out;
        }
    }

    public static void clearGaps() {
        synchronized (LOCK) {
            for (int s = 0; s < GAP_SKILLS; s++)
                for (int b = 0; b < BINS; b++) prevTotal[s][b] = prevHits[s][b] = nextTotal[s][b] = nextHits[s][b] = 0;
            anchorSkill = -1;
            note("# gap measurements cleared");
            version++;
        }
    }

    private static int sum(int[] a, int from, int to) { int s = 0; for (int i = from; i < to; i++) s += a[i]; return s; }

    // ------------------------------------------------------------------ lock pacing and retry

    public static int paceMode() { synchronized (LOCK) { return paceMode; } }

    public static boolean lockMode() { synchronized (LOCK) { return paceMode != PACE_OFF; } }

    /** Auto: the loop sends right after Trọng kiếm/đòn 0 and wakes at the lock end (Khóa keeps its 28ms frame). */
    public static boolean answerMode() { synchronized (LOCK) { return paceMode == PACE_AUTO; } }

    private static final int MODES = PACE_AUTO;

    /** Tắt -> Khóa 500 -> 525 -> 550 -> 575 -> 600 -> 650 -> 700 -> Auto -> Tắt. */
    public static void cyclePace() { setPaceMode((paceMode() + 1) % (MODES + 1)); }

    public static void setPaceMode(int mode) {
        synchronized (LOCK) {
            if (mode < PACE_OFF || mode > MODES || mode == paceMode) return;
            paceMode = mode;
            note(new StringBuffer("# pace ").append(paceLabel(mode)).toString());
            if (mode == PACE_AUTO) autoNote("start");
            version++;
        }
    }

    public static String paceLabel(int mode) {
        if (mode <= PACE_OFF || mode > MODES) return "Tắt";
        if (mode == PACE_AUTO) return "Auto";
        return new StringBuffer("Khóa ").append(LOCK_MS[mode - 1]).append("ms").toString();
    }

    private static long waitMs(int mode) {
        if (mode <= PACE_OFF || mode > MODES) return 0L;
        return mode == PACE_AUTO ? autoG : LOCK_MS[mode - 1];
    }

    /** The lock kept after a send of any skill but FREE_SKILL (Auto: its current G); 0 when pacing is off. */
    public static long lockMs() { synchronized (LOCK) { return waitMs(paceMode); } }

    /** When the lock after the last non-free send ends; -1 when none applies. */
    private static long paceEnd() {
        if (paceMode == PACE_OFF || !lockSet) return -1L;
        return paceMode == PACE_AUTO ? autoEnd() : lockAt + LOCK_MS[paceMode - 1];
    }

    /** False while the lock after the last non-free send still runs. */
    public static boolean paceReady(long now) {
        synchronized (LOCK) {
            long end = paceEnd();
            return end < 0L || now < lockAt || now >= end;
        }
    }

    /** Auto: the lock ended after `since` and by `now`, so the loop runs a frame without waiting for its 28ms. */
    public static boolean paceEndedSince(long since, long now) {
        synchronized (LOCK) {
            if (paceMode != PACE_AUTO) return false;
            long end = paceEnd();
            return end > since && end <= now && now >= lockAt;
        }
    }

    /** Auto: ms until the running lock ends (the canvas sleeps no longer); -1 otherwise. */
    public static long paceWait(long now) {
        synchronized (LOCK) {
            if (paceMode != PACE_AUTO) return -1L;
            long end = paceEnd();
            return end > now && now >= lockAt ? end - now : -1L;
        }
    }

    // ------------------------------------------------------------------ Auto pacing

    private static long autoSendEnd() { return lockAt + autoG + (lockArm < 0 ? -AUTO_DITHER : AUTO_DITHER); }

    private static long autoEnd() {
        long g = autoSendEnd();
        return Math.max(g, lockAnswerAt >= lockAt ? lockAnswerAt + AUTO_ANSWER_MS : lockAt + ANSWER_FALLBACK_MS);
    }

    private static int nextArm() {
        autoSeed = autoSeed * 1103515245 + 12345;
        return (autoSeed >>> 16 & 1) == 0 ? -1 : 1;
    }

    /** Before the lock moves: a send right at the lock end becomes a sample; a new arm for the next lock. */
    private static void autoSent(int skill, long now, int i) {
        if (skill == FREE_SKILL) return;
        boolean scored = false;
        if (lockSet) {
            long end = autoEnd(), cycle = now - lockAt;
            if (now >= end && now - end <= AUTO_BOUNDARY_MS && cycle > 0L && cycle < AUTO_CYCLE_MAX_MS) {
                if (lockAnswerAt < lockAt) { if (lastScoredG) logArm[i] = ARM_FALLBACK; }
                else if (autoSendEnd() >= lockAnswerAt + AUTO_ANSWER_MS) { logArm[i] = (byte)(lockArm < 0 ? ARM_SHORT : ARM_LONG); scored = true; }
                logCycle[i] = cycle;
            }
        }
        lastScoredG = scored;
        lockArm = nextArm();
    }

    private static String armLabel(int arm) {
        int a = arm < 0 ? -arm : arm;
        return a == ARM_SHORT ? " -" : a == ARM_LONG ? " +" : a == ARM_FALLBACK ? " dự phòng" : a == ARM_ROTATED ? " đổi MT" : "";
    }

    /** One scored send: G short/long, or the fallback send after an unanswered G-set send. */
    private static void autoSample(int arm, boolean hit, long cycle) {
        if (arm == ARM_FALLBACK) {
            if (hit && ++strikes >= AUTO_STRIKES) {
                autoG = Math.min(AUTO_G_MAX, autoG + AUTO_BUMP);
                autoNote("longer (fallback sends hit after unanswered sends)");
                clearArms();
            }
            return;
        }
        int k = arm - ARM_SHORT;
        armSends[k]++;
        if (hit) armHits[k]++;
        armTime[k] += cycle;
        if (++strikeSpan >= AUTO_STRIKE_SPAN) strikeSpan = strikes = 0;
        if (armSends[0] < AUTO_N || armSends[1] < AUTO_N) return;
        // Hits per second: short wins when hitsShort / timeShort >= hitsLong / timeLong.
        boolean shorter = (long)armHits[0] * armTime[1] >= (long)armHits[1] * armTime[0];
        String what;
        if ((armHits[0] + armHits[1]) * 2 < armSends[0] + armSends[1]) what = "skip (both arms miss: glitch)";
        else {
            autoG = Math.max(AUTO_G_MIN, Math.min(AUTO_G_MAX, autoG + (shorter ? -AUTO_STEP : AUTO_STEP)));
            what = shorter ? "shorter" : "longer";
        }
        autoNote(new StringBuffer(what).append(" (short ").append(armHits[0]).append('/').append(armSends[0]).append(" in ")
                .append(armTime[0]).append("ms, long ").append(armHits[1]).append('/').append(armSends[1])
                .append(" in ").append(armTime[1]).append("ms)").toString());
        clearArms();
    }

    private static void clearArms() {
        armSends[0] = armSends[1] = armHits[0] = armHits[1] = 0;
        armTime[0] = armTime[1] = 0L;
        strikes = strikeSpan = 0;
        version++;
    }

    private static void autoNote(String why) {
        note(new StringBuffer("# auto ").append(why).append(": send ").append(autoG).append("ms, answer ")
                .append(AUTO_ANSWER_MS).append("ms").toString());
    }

    /** Auto: the attack loop moves đòn 0's target behind the others for the next skill. */
    public static boolean rotateAfterFree() { synchronized (LOCK) { return paceMode == PACE_AUTO; } }

    /** The send just made went out with đòn 0's target moved to the end (logged "đổi MT"). */
    public static void markRotated() {
        synchronized (LOCK) {
            int i = index(seq - 1);
            if (logSeq[i] == seq - 1 && logArm[i] == 0) logArm[i] = (byte)ARM_ROTATED;
        }
    }

    /** {G, lock arm (-1 short, 1 long), strikes} of the Auto mode (tests and notes). */
    public static int[] autoState() { synchronized (LOCK) { return new int[]{autoG, lockArm, strikes}; } }

    public static boolean retryEnabled() { synchronized (LOCK) { return retryEnabled; } }

    public static void setRetry(boolean on) {
        synchronized (LOCK) {
            if (on == retryEnabled) return;
            retryEnabled = on;
            for (int i = 0; i < MAX_SKILLS; i++) retryFlag[i] = false;
            note(on ? "# retry Bật" : "# retry Tắt");
            version++;
        }
    }

    /** A skill whose newest send went unanswered: clear its cooldown once (until it is answered again); -1 when none. */
    public static int takeRetry() {
        synchronized (LOCK) {
            for (int s = 0; s < MAX_SKILLS; s++) {
                if (!retryFlag[s]) continue;
                retryFlag[s] = false;
                retried[s] = true;
                note(new StringBuffer("# retry skill ").append(s).toString());
                version++;
                return s;
            }
            return -1;
        }
    }

    // ------------------------------------------------------------------ file log lines

    /** Starts collecting CSV lines (the writer drains them). */
    public static void attachLog(String header) {
        synchronized (LOCK) {
            if (lines != null) return;
            lines = new StringBuffer();
            if (header != null) line(header);
            line(COLUMNS);
            line(new StringBuffer("# session ").append(session).append(", pace ").append(paceLabel(paceMode)).toString());
        }
    }

    public static void detachLog() { synchronized (LOCK) { lines = null; } }

    public static String drainLog() {
        synchronized (LOCK) {
            if (lines == null || lines.length() == 0) return "";
            String out = lines.toString();
            lines.setLength(0);
            return out;
        }
    }

    /** A comment line in the file log. */
    public static void note(String text) {
        synchronized (LOCK) { if (lines != null) line(text); }
    }

    private static void line(String text) {
        if (lines.length() > LINE_LIMIT) return;
        lines.append(text).append('\n');
    }
}
