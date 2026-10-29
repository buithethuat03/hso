package luna.features;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import luna.api.ExpEstimateStore;
import luna.ui.ExpPercentInterpolator;

/** Semantic estimate of total EXP for the current level, driven by packet data. */
public final class ExpEstimateSupport {
    private static final int MAGIC = 0x45585031; // EXP1
    private static final int VERSION = 2;
    private static final int MAX_RECORDS = 255;
    private static final int MAX_LEGACY_RECORDS = 64;
    private static final int MAX_SAMPLES = 64;
    private static final int MIN_PROGRESS_SPAN = 10; // tenths of a percent
    private static final long SAVE_THROTTLE_MS = 5000L;
    private static final long MAX_LONG = Long.MAX_VALUE;
    private static final String CALCULATING = "Đang tính toán...";

    private static ExpEstimateStore store;
    private static EstimateRecord[] records = new EstimateRecord[0];
    private static EstimateRecord[] pendingRecords = new EstimateRecord[0];
    private static boolean dirty;
    private static boolean attemptedSave;
    private static long lastSaveAttempt;
    private static boolean attemptedLoad;
    private static long lastLoadAttempt;

    private static String activeServer;
    private static int activeCharacterId;
    private static int activeLevel;
    private static boolean active;
    private static boolean hasBaseline;
    private static int startProgress;
    private static int lastProgress;
    private static long sessionTotal;
    private static int sampleCount;
    private static final int[] sampleProgress = new int[MAX_SAMPLES];
    private static final long[] sampleTotal = new long[MAX_SAMPLES];
    private static long sessionLow = 1L;
    private static long sessionHigh;
    private static boolean sessionHasHigh;
    private static long pairLow;
    private static long pairHigh;
    private static boolean pairHasHigh;

    private static long shownLow;
    private static long shownHigh;
    private static int cachedLineProgress = Integer.MIN_VALUE;
    private static int cachedLineFine = Integer.MIN_VALUE;
    private static long cachedLineLow;
    private static long cachedLineHigh;
    private static String cachedLine;
    private static String cachedPercentage;
    private static int cachedPercentageProgress = Integer.MIN_VALUE;
    private static int lastFinePercentage = -1;
    private static final ExpPercentInterpolator fineInterpolation = new ExpPercentInterpolator();

    private ExpEstimateSupport() { }

    /** Installs a storage adapter. Calling this also starts a fresh in-memory runtime. */
    public static synchronized void setStore(ExpEstimateStore value) {
        ExpRateSupport.reset();
        flush(true);
        store = value;
        records = new EstimateRecord[0];
        pendingRecords = new EstimateRecord[0];
        dirty = false;
        attemptedSave = false;
        lastSaveAttempt = 0L;
        attemptedLoad = false;
        lastLoadAttempt = 0L;
        clearSession();
        show(0L, 0L);
    }

    /** Starts a login session and establishes the first progress baseline. */
    public static synchronized void snapshot(String server, int characterId,
            int level, int progress) {
        if (!active || activeCharacterId != characterId
                || !normalizeServer(server).equals(activeServer)) ExpRateSupport.reset();
        flush(true);
        clearSession();
        show(0L, 0L);

        if (!validSession(level, progress)) return;

        active = true;
        activeServer = normalizeServer(server);
        activeCharacterId = characterId;
        activeLevel = level;
        hasBaseline = true;
        startProgress = progress;
        lastProgress = progress;
        sampleCount = 1;
        sampleProgress[0] = progress;
        sampleTotal[0] = 0L;
        sessionLow = 1L;
        sessionHigh = 0L;
        sessionHasHigh = false;
        loadStore();
        if (dirty) flush(true);
        showCachedForActiveKey();
    }

    /** Rebaselines only when an already active identity reports a new key. */
    public static synchronized void levelChanged(String server, int characterId,
            int level, int progress) {
        String normalized = normalizeServer(server);
        if (!active || sameSession(normalized, characterId, level)) return;
        snapshot(server, characterId, level, progress);
    }

    /** Loads a missing session snapshot once, without adding HUD-path RMS I/O. */
    public static synchronized void ensureSession(String server, int characterId,
            int level, int progress) {
        if (!validSession(level, progress)) return;
        String normalized = normalizeServer(server);
        if (!active || !sameSession(normalized, characterId, level)) {
            snapshot(normalized, characterId, level, progress);
            return;
        }
        if (findRecord(activeLevel) < 0 && shouldRetryLoad()) {
            boolean migrated = loadStore();
            if (migrated && dirty) flush(true);
        }
        if (dirty) flush(false);
        showCachedForActiveKey();
    }

    /** Adds a clean same-level packet and samples the resulting progress. */
    public static synchronized void gain(String server, int characterId,
            int level, int progress, int gain) {
        if (!validSession(level, progress)) {
            ExpRateSupport.reset();
            clearSession();
            return;
        }
        server = normalizeServer(server);
        if (!active || activeCharacterId != characterId || !server.equals(activeServer)) {
            ExpRateSupport.reset();
        }
        // Count each self-character reward once, including a level-crossing packet
        // that the same-level total estimator must discard.
        ExpRateSupport.gain(gain, System.currentTimeMillis());

        if (!active || !sameSession(server, characterId, level)) {
            beginKey(server, characterId, level, progress);
            return; // No observed baseline: do not count this packet.
        }
        if (!hasBaseline) {
            resetSegment(progress);
            return;
        }
        if (progress < lastProgress || gain < 0) {
            resetSegment(progress);
            return;
        }
        if (gain == 0) {
            // A zero-reward packet with unchanged progress is not a sample, but
            // it does not make the surrounding positive-gain segment unclean.
            if (progress > lastProgress) resetSegment(progress);
            return;
        }

        long add = (long)gain;
        if (sessionTotal > MAX_LONG - add) {
            resetSegment(progress);
            return;
        }
        long nextTotal = sessionTotal + add;
        if (!intersectNewSample(progress, nextTotal)) {
            // A contradictory segment is discarded as a unit. Keep the prior
            // cached estimate until a later clean segment can confirm/replace it.
            resetSegment(progress);
            return;
        }

        sessionTotal = nextTotal;
        lastProgress = progress;
        appendSample(progress, nextTotal);
        invalidatePercentage(false);
        updateEstimate();
        fineInterpolation.update(progress, shownLow, shownHigh,
                sampleProgress, sampleTotal, sampleCount, sessionTotal);
    }

    /** Flushes pending persistence and ends the live segment. */
    public static synchronized void disconnect() {
        ExpRateSupport.reset();
        flush(true);
        clearSession();
    }

    /** Returns the already-computed display string; no storage work occurs here. */
    public static synchronized String line(int progress) {
        if (!validProgress(progress) || shownLow <= 0L || shownHigh < shownLow) {
            return CALCULATING;
        }
        int fine = -1;
        if (active && hasBaseline && progress == lastProgress) {
            percentage(progress);
            if (fineInterpolation.value() >= 0) fine = lastFinePercentage;
        }
        if (cachedLine != null && cachedLineProgress == progress
                && cachedLineFine == fine
                && cachedLineLow == shownLow && cachedLineHigh == shownHigh) {
            return cachedLine;
        }
        long total = midpoint(shownLow, shownHigh);
        long left = fine >= 0 ? multiplyFineProgress(total, fine) : multiplyProgress(total, progress);
        String result = new StringBuffer().append('(')
                .append(formatCompact(left)).append('/')
                .append(formatCompact(total)).append(')').toString();
        cachedLineProgress = progress;
        cachedLineFine = fine;
        cachedLineLow = shownLow;
        cachedLineHigh = shownHigh;
        cachedLine = result;
        return result;
    }

    /** Fine HUD progress from live samples; never changes the native EXP field. */
    public static synchronized String percentage(int progress) {
        if (cachedPercentage != null && cachedPercentageProgress == progress) {
            return cachedPercentage;
        }
        int fine = -1;
        if (active && hasBaseline && progress == lastProgress) {
            fine = fineInterpolation.update(progress, shownLow, shownHigh,
                    sampleProgress, sampleTotal, sampleCount, sessionTotal);
        }
        if (fine >= 0) {
            // A tighter total estimate must not make a clean positive run move backwards.
            if (lastFinePercentage > fine && lastFinePercentage < (progress + 1) * 100) {
                fine = lastFinePercentage;
            }
            lastFinePercentage = fine;
            cachedPercentage = ExpPercentInterpolator.format(fine, ',');
        } else {
            int nativeProgress = progress < 0 ? 0 : progress > 999 ? 999 : progress;
            cachedPercentage = new StringBuffer().append(nativeProgress / 10)
                    .append(',').append(nativeProgress % 10).append('%').toString();
        }
        cachedPercentageProgress = progress;
        return cachedPercentage;
    }

    /** Builds the on-demand info panel without new storage work or compact units. */
    public static synchronized String info(int progress, long nowMillis) {
        long hourly = ExpRateSupport.rate(nowMillis);
        boolean totalReady = active && validProgress(progress)
                && shownLow > 0L && shownHigh >= shownLow;
        long total = -1L;
        if (totalReady) {
            long width = shownHigh - shownLow;
            total = shownLow + width / 2L + width % 2L;
        }
        int fine = -1;
        if (totalReady && progress == lastProgress) {
            percentage(progress);
            if (fineInterpolation.value() >= 0) fine = lastFinePercentage;
        }
        StringBuffer text = new StringBuffer();
        text.append("Kinh nghiệm/giờ (ước tính):\n")
                .append(hourly < 0L ? CALCULATING : ExpRateSupport.formatLong(hourly));
        text.append("\nKinh nghiệm lên cấp (ước tính):\n")
                .append(totalReady ? ExpRateSupport.formatLong(total) : CALCULATING);
        text.append("\nThời gian còn lại (ước tính):\n")
                .append(totalReady && hourly > 0L
                        ? ExpRateSupport.formatLevelDuration(total, fine, progress, hourly) : CALCULATING);
        return text.toString();
    }

    /** Diagnostic bound currently used by line(), or zero while not ready. */
    public static synchronized long estimateLow() { return shownLow; }

    /** Diagnostic bound currently used by line(), or zero while not ready. */
    public static synchronized long estimateHigh() { return shownHigh; }

    public static synchronized boolean isReady() {
        return shownLow > 0L && shownHigh >= shownLow;
    }

    public static synchronized int sampleCount() { return sampleCount; }

    public static synchronized int cleanProgressSpan() {
        return hasBaseline && lastProgress >= startProgress
                ? lastProgress - startProgress : 0;
    }

    public static synchronized long currentSessionTotal() { return sessionTotal; }

    private static void beginKey(String server, int characterId, int level, int progress) {
        active = true;
        activeServer = normalizeServer(server);
        activeCharacterId = characterId;
        activeLevel = level;
        resetSegment(progress);
    }

    private static void resetSegment(int progress) {
        invalidatePercentage(true);
        hasBaseline = true;
        startProgress = progress;
        lastProgress = progress;
        sessionTotal = 0L;
        sampleCount = 1;
        sampleProgress[0] = progress;
        sampleTotal[0] = 0L;
        sessionLow = 1L;
        sessionHigh = 0L;
        sessionHasHigh = false;
        showCachedForActiveKey();
    }

    private static void clearSession() {
        invalidatePercentage(true);
        activeServer = null;
        activeCharacterId = 0;
        activeLevel = 0;
        active = false;
        hasBaseline = false;
        startProgress = 0;
        lastProgress = 0;
        sessionTotal = 0L;
        sampleCount = 0;
        sessionLow = 1L;
        sessionHigh = 0L;
        sessionHasHigh = false;
    }

    private static boolean intersectNewSample(int progress, long total) {
        long candidateLow = 1L;
        long candidateHigh = MAX_LONG;
        boolean candidateHasHigh = false;

        for (int i = 0; i < sampleCount; i++) {
            long deltaTotal = total - sampleTotal[i];
            int deltaProgress = progress - sampleProgress[i];
            if (deltaTotal <= 0L || deltaProgress < 0) return false;
            if (!pairBounds(deltaTotal, deltaProgress)) return false;
            if (pairLow > candidateLow) candidateLow = pairLow;
            if (pairHasHigh) {
                if (!candidateHasHigh || pairHigh < candidateHigh) candidateHigh = pairHigh;
                candidateHasHigh = true;
            }
        }

        long newLow = candidateLow > sessionLow ? candidateLow : sessionLow;
        long newHigh = sessionHigh;
        boolean newHasHigh = sessionHasHigh;
        if (candidateHasHigh) {
            if (!newHasHigh || candidateHigh < newHigh) newHigh = candidateHigh;
            newHasHigh = true;
        }
        if (newHasHigh && newLow > newHigh) return false;

        sessionLow = newLow;
        sessionHigh = newHasHigh ? newHigh : 0L;
        sessionHasHigh = newHasHigh;
        return true;
    }

    private static boolean pairBounds(long deltaTotal, int deltaProgress) {
        pairLow = 0L;
        pairHigh = 0L;
        pairHasHigh = false;
        if (deltaProgress < 0 || deltaProgress > 999) return false;
        int lowDenominator = deltaProgress + 1;
        long low = scaledDivide(deltaTotal, lowDenominator, true);
        if (low < 0L) return false;
        if (low == 0L) low = 1L;
        pairLow = low;
        if (deltaProgress <= 1) return true;
        long high = scaledDivide(deltaTotal, deltaProgress - 1, false);
        if (high < 0L || high < low) return false;
        pairHigh = high;
        pairHasHigh = true;
        return true;
    }

    /** Computes ceil/floor(value * 1000 / divisor) without forming the product. */
    private static long scaledDivide(long value, int divisor, boolean roundUp) {
        if (value <= 0L || divisor <= 0 || divisor > 1000) return -1L;
        long quotient = value / divisor;
        long remainder = value % divisor;
        if (quotient > MAX_LONG / 1000L) return -1L;
        long result = quotient * 1000L;
        long remainderProduct = remainder * 1000L; // remainder is at most 999.
        long extra = remainderProduct / divisor;
        if (result > MAX_LONG - extra) return -1L;
        result += extra;
        if (roundUp && remainderProduct % divisor != 0L) {
            if (result == MAX_LONG) return -1L;
            result++;
        }
        return result;
    }

    private static void appendSample(int progress, long total) {
        if (sampleCount == MAX_SAMPLES) {
            // Keep the original anchor plus the newest bounded window.
            for (int i = 1; i < MAX_SAMPLES - 1; i++) {
                sampleProgress[i] = sampleProgress[i + 1];
                sampleTotal[i] = sampleTotal[i + 1];
            }
            sampleCount--;
        }
        sampleProgress[sampleCount] = progress;
        sampleTotal[sampleCount] = total;
        sampleCount++;
    }

    private static void updateEstimate() {
        int span = cleanProgressSpan();
        boolean freshReady = sessionHasHigh
                && confident(sessionLow, sessionHigh, span);
        if (!freshReady) {
            showCachedForActiveKey();
            return;
        }

        int index = findRecord(activeLevel);
        if (index < 0) {
            putRecord(new EstimateRecord(activeLevel, sessionLow, sessionHigh,
                    sampleCount, span));
            show(sessionLow, sessionHigh);
            flush(true);
            return;
        }

        EstimateRecord old = records[index];
        if (sessionLow <= old.high && sessionHigh >= old.low) {
            long mergedLow = sessionLow > old.low ? sessionLow : old.low;
            long mergedHigh = sessionHigh < old.high ? sessionHigh : old.high;
            boolean boundsChanged = mergedLow != old.low || mergedHigh != old.high;
            boolean metricsChanged = sampleCount > old.samples || span > old.progressSpan;
            if (boundsChanged || metricsChanged) {
                putRecord(new EstimateRecord(activeLevel, mergedLow, mergedHigh,
                        sampleCount > old.samples ? sampleCount : old.samples,
                        span > old.progressSpan ? span : old.progressSpan));
                flush(false);
            }
            show(mergedLow, mergedHigh);
            return;
        }

        // A separately clean segment may correct a stale estimate, but only
        // after it has earned the same one-percent readiness threshold.
        if (span >= MIN_PROGRESS_SPAN) {
            putRecord(new EstimateRecord(activeLevel, sessionLow, sessionHigh,
                    sampleCount, span));
            show(sessionLow, sessionHigh);
            flush(true);
            return;
        }
        show(old.low, old.high);
    }

    private static boolean confident(long low, long high, int span) {
        if (span < MIN_PROGRESS_SPAN || low <= 0L || high < low) return false;
        double width = (double)(high - low);
        double centerScale = (double)high + (double)low;
        return centerScale > 0.0d && width / centerScale <= 0.11d;
    }

    private static void showCachedForActiveKey() {
        int index = active ? findRecord(activeLevel) : -1;
        if (index >= 0) show(records[index].low, records[index].high);
        else show(0L, 0L);
    }

    private static void show(long low, long high) {
        if (shownLow != low || shownHigh != high) invalidateLine();
        shownLow = low;
        shownHigh = high;
    }

    private static void invalidateLine() {
        invalidatePercentage(false);
        cachedLine = null;
        cachedLineProgress = Integer.MIN_VALUE;
        cachedLineFine = Integer.MIN_VALUE;
        cachedLineLow = 0L;
        cachedLineHigh = 0L;
    }

    private static void invalidatePercentage(boolean reset) {
        cachedPercentage = null;
        cachedPercentageProgress = Integer.MIN_VALUE;
        if (reset) {
            lastFinePercentage = -1;
            fineInterpolation.reset();
        }
    }

    private static long midpoint(long low, long high) {
        return low + (high - low) / 2L;
    }

    private static long multiplyProgress(long total, int progress) {
        long whole = total / 1000L;
        long remainder = total % 1000L;
        return whole * (long)progress + (remainder * (long)progress) / 1000L;
    }

    private static long multiplyFineProgress(long total, int fine) {
        long whole = total / 100000L;
        long remainder = total % 100000L;
        return whole * (long)fine + (remainder * (long)fine) / 100000L;
    }

    private static String formatCompact(long value) {
        if (value < 1000L) return String.valueOf(value);
        long divisor = value < 1000000L ? 1000L : 1000000L;
        long whole = value / divisor;
        long remainder = value % divisor;
        long tenths = whole * 10L + (remainder * 10L + divisor / 2L) / divisor;
        if (divisor == 1000L && tenths >= 10000L) return "1M";
        long integerPart = tenths / 10L;
        int decimal = (int)(tenths % 10L);
        StringBuffer result = new StringBuffer().append(integerPart);
        if (decimal != 0) result.append('.').append((char)('0' + decimal));
        result.append(divisor == 1000L ? 'K' : 'M');
        return result.toString();
    }

    private static void flush(boolean force) {
        if (!dirty || store == null) return;
        long now = System.currentTimeMillis();
        if (!force && attemptedSave && now >= lastSaveAttempt
                && now - lastSaveAttempt < SAVE_THROTTLE_MS) return;
        attemptedSave = true;
        lastSaveAttempt = now;
        byte[] data = encode(pendingRecords);
        if (data == null) return;
        try {
            if (store.save(data)) {
                pendingRecords = new EstimateRecord[0];
                dirty = false;
            }
        } catch (Throwable ignored) { }
    }

    /** Pure merge entry point for the shared RMS adapter; never takes this class lock. */
    public static byte[] mergeStored(byte[] existing, byte[] incoming) {
        if (existing == null && incoming == null) return null;

        EstimateRecord[] oldRecords = existing == null
                ? new EstimateRecord[0] : decode(existing);
        EstimateRecord[] newRecords = incoming == null
                ? new EstimateRecord[0] : decode(incoming);
        if (incoming != null && newRecords == null) return null;
        if (oldRecords == null) {
            if (incoming == null) return null;
            // A valid current payload can repair a corrupt common blob. A
            // corrupt incoming payload is rejected above, so this cannot erase
            // readable legacy/current records.
            oldRecords = new EstimateRecord[0];
        }
        EstimateRecord[] merged = oldRecords;
        for (int i = 0; i < newRecords.length; i++) {
            merged = upsert(merged, newRecords[i]);
        }
        return encode(merged);
    }

    private static boolean loadStore() {
        long now = System.currentTimeMillis();
        attemptedLoad = true;
        lastLoadAttempt = now;
        if (store == null) return false;
        byte[] data;
        try { data = store.load(); }
        catch (Throwable ignored) { return false; }
        if (data == null) return false;
        int version = payloadVersion(data);
        EstimateRecord[] loaded = decode(data);
        if (loaded == null) return false;
        mergeLoadedRecords(loaded);
        if (version == 1) {
            for (int i = 0; i < loaded.length; i++) {
                int index = findRecord(loaded[i].level);
                if (index >= 0) queuePending(records[index]);
            }
            return true;
        }
        return false;
    }

    private static boolean shouldRetryLoad() {
        if (!attemptedLoad) return true;
        long now = System.currentTimeMillis();
        return now < lastLoadAttempt || now - lastLoadAttempt >= SAVE_THROTTLE_MS;
    }

    private static void mergeLoadedRecords(EstimateRecord[] loaded) {
        for (int i = 0; i < loaded.length; i++) {
            EstimateRecord disk = loaded[i];
            int localIndex = findRecord(disk.level);
            if (localIndex < 0) {
                records = upsert(records, disk);
                continue;
            }
            EstimateRecord local = records[localIndex];
            int pendingIndex = findRecord(pendingRecords, disk.level);
            EstimateRecord merged = pendingIndex >= 0
                    ? mergeRecord(disk, pendingRecords[pendingIndex])
                    : mergeRecord(local, disk);
            records = upsert(records, merged);
        }
    }

    private static byte[] encode(EstimateRecord[] values) {
        if (values == null || values.length > MAX_RECORDS) return null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            out.writeByte(values.length);
            for (int i = 0; i < values.length; i++) {
                EstimateRecord record = values[i];
                if (!validRecord(record)) return null;
                out.writeInt(record.level);
                out.writeLong(record.low);
                out.writeLong(record.high);
                out.writeShort(record.samples);
                out.writeShort(record.progressSpan);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (Throwable ignored) { return null; }
    }

    private static int payloadVersion(byte[] data) {
        if (data == null || data.length < 6 || data.length > 16384) return -1;
        DataInputStream in = null;
        try {
            in = new DataInputStream(new ByteArrayInputStream(data));
            if (in.readInt() != MAGIC) return -1;
            int version = in.readUnsignedByte();
            int count = in.readUnsignedByte();
            if (version != 1 && version != VERSION) return -1;
            if (version == 1 && count > MAX_LEGACY_RECORDS) return -1;
            if (version == VERSION && count > MAX_RECORDS) return -1;
            return version;
        } catch (Throwable ignored) {
            return -1;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
    }

    private static EstimateRecord[] decode(byte[] data) {
        int version = payloadVersion(data);
        if (version < 0) return null;
        DataInputStream in = null;
        try {
            in = new DataInputStream(new ByteArrayInputStream(data));
            in.readInt();
            in.readUnsignedByte();
            int count = in.readUnsignedByte();
            EstimateRecord[] result = new EstimateRecord[0];
            for (int i = 0; i < count; i++) {
                String oldServer = null;
                int oldCharacterId = 0;
                int level;
                if (version == 1) {
                    oldServer = in.readUTF();
                    oldCharacterId = in.readInt();
                    level = in.readInt();
                } else {
                    level = in.readInt();
                }
                long low = in.readLong();
                long high = in.readLong();
                int samples = in.readUnsignedShort();
                int span = in.readUnsignedShort();
                boolean validLegacyKey = version != 1 || (oldServer != null
                        && oldServer.length() > 0 && oldServer.length() <= 64);
                EstimateRecord value = new EstimateRecord(level, low, high, samples, span);
                if (validLegacyKey && validRecord(value)) result = upsert(result, value);
            }
            if (in.read() != -1) return null;
            return result;
        } catch (Throwable ignored) {
            // Without row boundaries for the unread remainder, keep the prior
            // in-memory cache and refuse to replace the stored blob.
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
    }

    private static boolean validRecord(EstimateRecord record) {
        return record != null && record.level > 0 && record.samples >= 2
                && record.samples <= MAX_SAMPLES
                && confident(record.low, record.high, record.progressSpan);
    }

    private static EstimateRecord mergeRecord(EstimateRecord existing,
            EstimateRecord incoming) {
        if (incoming.low <= existing.high && incoming.high >= existing.low) {
            long low = incoming.low > existing.low ? incoming.low : existing.low;
            long high = incoming.high < existing.high ? incoming.high : existing.high;
            int samples = incoming.samples > existing.samples ? incoming.samples : existing.samples;
            int span = incoming.progressSpan > existing.progressSpan
                    ? incoming.progressSpan : existing.progressSpan;
            return new EstimateRecord(existing.level, low, high, samples, span);
        }
        // Incoming wins a disjoint interval so a newer clean session can
        // replace an old server table without poisoning other levels.
        return incoming;
    }

    private static EstimateRecord[] upsert(EstimateRecord[] values,
            EstimateRecord incoming) {
        int index = findRecord(values, incoming.level);
        int keep = values.length;
        int start = 0;
        EstimateRecord value = incoming;
        if (index >= 0) {
            value = mergeRecord(values[index], incoming);
            keep--;
        } else if (keep == MAX_RECORDS) {
            start = 1;
            keep--;
        }
        EstimateRecord[] next = new EstimateRecord[keep + 1];
        int target = 0;
        for (int i = start; i < values.length; i++) {
            if (i == index) continue;
            next[target++] = values[i];
        }
        next[target] = value;
        return next;
    }

    private static void putRecord(EstimateRecord value) {
        records = upsert(records, value);
        int index = findRecord(value.level);
        if (index >= 0) queuePending(records[index]);
    }

    private static void queuePending(EstimateRecord value) {
        pendingRecords = upsert(pendingRecords, value);
        dirty = true;
    }

    private static int findRecord(int level) {
        return findRecord(records, level);
    }

    private static int findRecord(EstimateRecord[] values, int level) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].level == level) return i;
        }
        return -1;
    }

    private static boolean sameSession(String server, int characterId, int level) {
        return active && activeCharacterId == characterId && activeLevel == level
                && activeServer.equals(server);
    }

    private static String normalizeServer(String server) {
        return server == null ? "" : server;
    }

    private static boolean validSession(int level, int progress) {
        return level > 0 && validProgress(progress);
    }

    private static boolean validProgress(int progress) {
        return progress >= 0 && progress <= 999;
    }

    private static final class EstimateRecord {
        final int level;
        final long low;
        final long high;
        final int samples;
        final int progressSpan;
        EstimateRecord(int currentLevel, long lower, long upper,
                int sampleMetric, int span) {
            level = currentLevel;
            low = lower;
            high = upper;
            samples = sampleMetric;
            progressSpan = span;
        }
    }
}
