package luna.features;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import luna.api.MonsterCacheAccess;
import luna.api.MonsterCacheTemplateNames;

/**
 * Small semantic cache for monster instances already observed in a zone.
 * All native storage, object and drawing operations are supplied by the
 * version-specific access bridge.
 */
public final class MonsterCacheSupport {

    private static final int MAGIC = 0x4d435631; // MCV1
    private static final int VERSION = 1;
    private static final String EXCLUDED_TEMPLATE_NAME = "Xà nữ";
    private static final int CACHE_DISABLED_MAP_ID = 48;
    private static final int MAX_CONTEXTS = 128;
    private static final int MAX_ZONE_RECORDS = 256;
    private static final int MAX_SERIALIZED_BYTES = 128 * 1024;
    private static final int MAX_SERVER_KEY_CHARS = 128;
    private static final long RESTORE_SETTLE_MS = 800L;
    private static final long REQUEST_INTERVAL_MS = 400L;
    private static final long PENDING_TIMEOUT_MS = 8000L;
    private static final long RECOVERY_GRACE_MS = 1500L;
    private static final long RECOVERY_CHECK_MS = 1000L;
    private static final long RECOVERY_DEAD_CHECK_MS = 3000L;
    private static final long RECOVERY_BACKOFF_FIRST_MS = 20000L;
    private static final long RECOVERY_BACKOFF_SECOND_MS = 40000L;
    private static final long RECOVERY_BACKOFF_MAX_MS = 60000L;
    private static final long SAVE_RETRY_MS = 500L;
    private static final int MAX_REQUESTS_PER_ID = 2;
    private static final int MAX_RESTORES_PER_TICK = 4;
    private static final int MAX_RECOVERY_RECORDS_PER_TICK = 8;
    private static final int MAX_EXCLUDED_FILTER_RECORDS_PER_TICK = 8;
    private static final int MAX_RECOVERIES_PER_TICK = 1;
    private static final int MAX_RECOVERY_FAILURES = 3;

    private static final Object UNKNOWN_FIND = new Object();

    private static final Object LOCK = new Object();
    private static final Object OP_LOCK = new Object();
    private static final Object SAVE_LOCK = new Object();

    private static final Context[] CONTEXTS = new Context[MAX_CONTEXTS];
    private static final int[] EXCLUDED_FILTER_TEMPLATES =
            new int[MAX_EXCLUDED_FILTER_RECORDS_PER_TICK];
    private static int contextCount;
    private static final Pending[] PENDING = new Pending[MAX_ZONE_RECORDS];
    private static int pendingCount;

    private static boolean storeLoaded;
    private static Context current;
    private static long generation;
    private static int scopeMap;
    private static int scopeZone;
    private static String scopeServer = "";
    private static MonsterCacheAccess activeAccess;

    private static boolean hasReadySince;
    private static long readySince;
    private static int restoreCursor;
    private static int recoveryCursor;
    private static int excludedFilterCursor;
    private static boolean hasRequested;
    private static long lastRequestAt;

    private static boolean dirty;
    private static long dirtySince;
    private static long retrySaveAt;
    private static long revision;
    private static int failCount;
    private static int restoredCount;
    private static int confirmedCount;
    private static int changedIdCount;
    private static int combatPacketCount;
    private static int combatReviveCount;
    private static boolean loadFailed;
    private static String hudStatusCache;
    private static long hudStatusAt;
    private static boolean hasHudStatusAt;

    private MonsterCacheSupport() {
    }

    public static void onMap(MonsterCacheAccess access) {
        try { onMapImpl(access); } catch (Throwable ignored) { noteFailure(); }
    }
    public static void onObject(MonsterCacheAccess access, int type, int template,
            int id, int x, int y, int state) {
        try { onObjectImpl(access, type, template, id, x, y, state); }
        catch (Throwable ignored) { noteFailure(); }
    }
    public static void onDetail(MonsterCacheAccess access, int id, int level,
            int x, int y, int hp, int maxHp) {
        try { onDetailImpl(access, id, level, x, y, hp, maxHp); }
        catch (Throwable ignored) { noteFailure(); }
    }
    public static void tick(MonsterCacheAccess access) {
        try { tickImpl(access); } catch (Throwable ignored) { noteFailure(); }
    }
    public static void paint(Object graphics) {
        try { paintImpl(graphics); } catch (Throwable ignored) { noteFailure(); }
    }

    /** Called at the native map/zone transition hook. */
    private static void onMapImpl(MonsterCacheAccess access) {
        if (access == null) return;
        synchronized (OP_LOCK) {
            String server;
            int map;
            int zone;
            long now;
            boolean ready;
            try {
                server = access.serverKey();
                if (server == null) server = "";
                map = access.mapId();
                zone = access.zoneId();
                now = access.now();
                ready = access.ready();
            } catch (Throwable ignored) {
                noteFailure();
                return;
            }

            // Map 48 stays entirely native: do not even read persisted monster
            // records while it is active. Other maps still share the same store.
            if (map != CACHE_DISABLED_MAP_ID) ensureLoaded(access);

            Pending[] oldPending = new Pending[MAX_ZONE_RECORDS];
            int oldCount;
            synchronized (LOCK) {
                oldCount = pendingCount;
                int i;
                for (i = 0; i < oldCount; i++) {
                    oldPending[i] = PENDING[i];
                    PENDING[i] = null;
                }
                pendingCount = 0;
                generation++;
            }
            clearOldPlaceholders(access, oldPending, oldCount);

            synchronized (LOCK) {
                scopeServer = server;
                scopeMap = map;
                scopeZone = zone;
                activeAccess = access;
                current = null;
                if (map != CACHE_DISABLED_MAP_ID && server.length() <= MAX_SERVER_KEY_CHARS) {
                    current = findContext(server, map, zone);
                    if (current == null) current = addContext(server, map, zone);
                    if (current != null) {
                        moveContextToEnd(current);
                        resetSession(current);
                    }
                }
                restoreCursor = 0;
                recoveryCursor = 0;
                excludedFilterCursor = 0;
                hasReadySince = ready;
                readySince = ready ? now : 0L;
                hasRequested = false;
                lastRequestAt = 0L;
                failCount = loadFailed ? 1 : 0;
                restoredCount = 0;
                confirmedCount = 0;
                changedIdCount = 0;
                combatPacketCount = 0;
                combatReviveCount = 0;
                loadFailed = false;
                hudStatusCache = null;
                hasHudStatusAt = false;
            }
            // Persist the context ordering and any dirty records at the map edge.
            flush(access, true, now);
            MonsterTargetWatchSupport.onMap(access);
        }
    }

    /** Observes one native packet-4 object record. */
    private static void onObjectImpl(MonsterCacheAccess access, int type, int template,
            int id, int x, int y, int state) {
        // State 127 is a transient animation state (including ea/eb), not a removal.
        if (access == null || type != 1 || state == 127) return;
        if (!safeReady(access)) return;
        synchronized (OP_LOCK) {
            if (!scopeMatches(access)) {
                onMap(access);
                if (!scopeMatches(access)) return;
            }
            long now = safeNow(access);
            int safeId = id & 0xffff;
            int safeTemplate = template & 0xffff;
            if (isExcludedTemplate(access, safeTemplate)) {
                forgetExcludedTemplate(access, safeTemplate, safeId, now);
                return;
            }
            Pending stalePending = null;
            boolean discardPending = false;
            boolean changed = false;

            synchronized (LOCK) {
                if (current == null) return;
                int pendingIndex = findPendingIndex(safeId);
                Pending p = pendingIndex >= 0 ? PENDING[pendingIndex] : null;
                int recordIndex = findRecordIndex(current, safeId);
                Record record;
                boolean templateChanged = false;
                if (recordIndex < 0) {
                    if (current.count == MAX_ZONE_RECORDS) {
                        int evictedId = current.records[0].id;
                        int evictedPending = findPendingIndex(evictedId);
                        if (evictedPending >= 0) {
                            stalePending = PENDING[evictedPending];
                            removePending(evictedPending);
                            discardPending = true;
                        }
                        removeRecord(current, 0);
                    }
                    record = new Record(safeId, safeTemplate, x, y, 0);
                    recordIndex = current.count++;
                    current.records[recordIndex] = record;
                    changed = true;
                    templateChanged = true;
                } else {
                    record = current.records[recordIndex];
                    if (record.template != safeTemplate || record.x != x || record.y != y) {
                        templateChanged = record.template != safeTemplate;
                        changedIdCount++;
                        record.template = safeTemplate;
                        record.x = x;
                        record.y = y;
                        if (templateChanged) record.level = 0;
                        changed = true;
                    }
                }
                record.seenThisMap = true;
                record.restoreDone = true;
                if (templateChanged) {
                    record.confirmedThisMap = false;
                    record.object = null;
                    record.corpseHeld = false;
                }
                record.hasDeadSince = false;
                record.deadSince = 0L;
                record.nextCheckAt = now + RECOVERY_CHECK_MS;
                record.retryDetail = false;
                record.recoveryFailures = 0;
                record.nextRecoveryAt = 0L;
                if (p != null) {
                    if (p.template == safeTemplate && p.x == x && p.y == y) {
                        p.observed = true;
                    } else {
                        stalePending = p;
                        p.identityMismatch = true;
                        removePending(pendingIndex);
                        discardPending = true;
                        if (record.template != p.template) record.confirmedThisMap = false;
                        if (p.recovery) registerRecoveryFailure(record, now);
                        failCount++;
                    }
                }
                if (changed) markDirty(now);
            }

            if (discardPending && stalePending != null) {
                clearSupersededPending(access, stalePending);
            }
            if (changed) flush(access, false, now);
        }
    }

    /** Forces a pending cache write from a graceful application-exit hook. */
    public static void flushNow(MonsterCacheAccess access) {
        if (access == null) return;
        try {
            flush(access, true, safeNow(access));
        } catch (Throwable ignored) {
            noteFailure();
        }
    }

    /** Queues a read-only health verification through the shared detail-request budget. */
    public static boolean verifyTarget(MonsterCacheAccess access, int id, Object expected) {
        try {
            if (access == null || expected == null || !safeReady(access) || !access.autoEnabled()) return false;
            synchronized (OP_LOCK) {
                if (!scopeMatches(access)) return false;
                int safeId = id & 0xffff;
                if (safeFindForRecovery(access, safeId) != expected) return false;
                synchronized (LOCK) {
                    Record record = findRecord(safeId);
                    if (record == null || !record.confirmedThisMap || record.object != expected
                            || record.level <= 0 || record.retryDetail
                            || findPendingIndex(safeId) >= 0 || pendingCount >= MAX_ZONE_RECORDS) return false;
                    addPending(new Pending(safeId, record.template, record.x, record.y,
                            expected, generation, false, false, true));
                    return true;
                }
            }
        } catch (Throwable ignored) { noteFailure(); return false; }
    }

    /** Observes combat HP even if a later native animation/attacker branch returns. */
    public static void onCombatSnapshot(MonsterCacheAccess access, DataInputStream input) {
        if (access == null || input == null) return;
        synchronized (LOCK) {
            if (activeAccess == access && current != null
                    && combatPacketCount < Integer.MAX_VALUE) combatPacketCount++;
        }
        if (!input.markSupported()) return;
        input.mark(65536);
        try {
            input.readShort(); // attacker; HP parsing is independent of its object/animation
            onUnseenCombat(access, input);
        } catch (Throwable ignored) {
        } finally {
            try { input.reset(); } catch (Throwable ignored) { noteFailure(); }
        }
    }

    /** Reads the whole combat victim list before applying any HP. */
    public static void onUnseenCombat(MonsterCacheAccess access, DataInputStream input) {
        if (access == null || input == null) return;
        try {
            input.readByte(); // skill
            int count = input.readUnsignedByte();
            if (count <= 0 || count > 64) return;
            int[] ids = new int[count];
            int[] health = new int[count];
            for (int i = 0; i < count; i++) {
                ids[i] = input.readUnsignedShort();
                input.readInt(); // damage/effect magnitude
                health[i] = input.readInt();
                int effects = input.readUnsignedByte();
                if (health[i] < 0 || effects > 32) return;
                for (int j = 0; j < effects; j++) { input.readByte(); input.readInt(); }
            }
            input.readInt(); input.readInt(); input.readByte(); input.readInt();
            if (input.available() != 0 || !safeReady(access)) return;
            synchronized (OP_LOCK) {
                if (!scopeMatches(access)) return;
                long observedGeneration;
                synchronized (LOCK) { observedGeneration = generation; }
                for (int i = 0; i < count; i++) {
                    if (!scopeMatches(access)) return;
                    Record record;
                    Object expected;
                    synchronized (LOCK) {
                        record = findRecord(ids[i]);
                        if (generation != observedGeneration || record == null
                                || !record.confirmedThisMap || record.object == null) continue;
                        expected = record.object;
                    }
                    // Do not scan the loaded-player vector for unknown victims.
                    Object found = safeFindForRecovery(access, ids[i]);
                    if (found == expected && access.type(found) == 1) {
                        int previousHp = access.hp(found);
                        if (!access.applyHp(found, health[i])) continue;
                        boolean revived = reconcileCombatHealth(record, found, health[i], previousHp,
                                observedGeneration, safeNow(access));
                        if (revived) MonsterTargetWatchSupport.onRevived(access, ids[i], found, health[i]);
                        else MonsterTargetWatchSupport.onHpProgress(access, ids[i], found, health[i]);
                        if (health[i] == 0) access.clearAutoTarget(found);
                    }
                }
            }
        } catch (Throwable ignored) {
            // A truncated frame must not apply a partially decoded victim list.
        }
    }

    /** A validated current-identity snapshot also resolves death/recovery state.
     * This does not promote an unconfirmed placeholder or teach its template.
     * The bridge applies positive HP and releases native death flags atomically
     * without copying position fields from an unrelated detail response.
     */
    private static boolean reconcileCombatHealth(Record record, Object object, int hp,
            int previousHp, long observedGeneration, long now) {
        synchronized (LOCK) {
            if (generation != observedGeneration || findRecord(record.id) != record
                    || !record.confirmedThisMap || record.object != object) return false;
            boolean revived = hp > 0 && (record.corpseHeld || record.hasDeadSince
                    || record.retryDetail || previousHp <= 0);
            int index = findPendingIndex(record.id);
            Pending pending = index < 0 ? null : PENDING[index];
            // A live unchanged HP report does not resolve a stalled-target query.
            // Normal recovery has already obtained the authoritative HP it needs.
            if (pending != null && pending.generation == observedGeneration
                    && pending.object == object
                    && (hp > 0 ? (!pending.watchdog || hp != previousHp || revived)
                            : pending.watchdog)) removePending(index);
            if (hp > 0) {
                if (revived && combatReviveCount < Integer.MAX_VALUE) combatReviveCount++;
                clearRecoveryDeadState(record);
                record.nextCheckAt = now + RECOVERY_CHECK_MS;
            } else {
                if (!record.hasDeadSince) {
                    record.hasDeadSince = true;
                    record.deadSince = now;
                    record.nextCheckAt = now + RECOVERY_DEAD_CHECK_MS;
                }
            }
            return revived;
        }
    }

    /** Protects known corpses before native world removal, independently of a_(). */
    public static void onEntityUpdate(MonsterCacheAccess access, Object object) {
        retainCorpse(access, object, false);
    }

    /** Advances the bounded death clip instead of native terminal cleanup/looping. */
    public static boolean advanceCorpse(Object object) {
        return retainCorpse(activeAccess, object, true);
    }

    /** Holds death-related candidates; live health verification preserves animation. */
    public static boolean hold(Object object) {
        try {
            if (object == null) return false;
            if (retainCorpse(activeAccess, object, false)) return true;
            MonsterCacheAccess access;
            Record unconfirmed = null;
            long observedGeneration;
            synchronized (LOCK) {
                int i;
                for (i = 0; i < pendingCount; i++) {
                    Pending p = PENDING[i];
                    if (p.object == object && p.generation == generation
                            && !p.watchdog) return true;
                }
                access = activeAccess;
                observedGeneration = generation;
                if (current != null) for (i = 0; i < current.count; i++) {
                    Record record = current.records[i];
                    if (record.object == object && record.retryDetail) { unconfirmed = record; break; }
                }
            }
            // A never-confirmed, adopted placeholder may still await its first valid
            // detail. Preserve its former hidden-state guard without showing a corpse.
            if (unconfirmed != null && access != null && scopeMatches(access)
                    && safeHiddenState(access, object) == 1) synchronized (LOCK) {
                return generation == observedGeneration && findRecord(unconfirmed.id) == unconfirmed
                        && unconfirmed.object == object && unconfirmed.retryDetail;
            }
            return false;
        } catch (Throwable ignored) {
            noteFailure();
            return false;
        }
    }

    private static boolean retainCorpse(MonsterCacheAccess access, Object object, boolean advance) {
        if (access == null || object == null) return false;
        try {
            synchronized (OP_LOCK) {
                if (!safeReady(access) || !scopeMatches(access) || access.type(object) != 1) return false;
                int id = access.id(object) & 0xffff;
                Record record;
                long observedGeneration;
                synchronized (LOCK) {
                    observedGeneration = generation;
                    record = findRecord(id);
                    if (record == null || record.object != object || !record.confirmedThisMap) return false;
                }
                // Native lookup scans every loaded player/object. A live known
                // monster needs no corpse work, especially in a crowded zone.
                if (!record.corpseHeld && !access.retiring(object) && safeDeadState(access, object) != 1)
                    return false;
                Object found = safeFindForRecovery(access, id);
                if (found == UNKNOWN_FIND || (found != null && found != object)) return false;
                if (!scopeMatches(access)) return false;
                access.corpse(object, advance);
                synchronized (LOCK) {
                    if (generation != observedGeneration || findRecord(id) != record) return false;
                    record.corpseHeld = true;
                }
                return true;
            }
        } catch (Throwable ignored) {
            noteFailure();
            return false;
        }
    }

    /** Observes a native packet-7 detail after the native handler has run. */
    private static void onDetailImpl(MonsterCacheAccess access, int id, int level,
            int x, int y, int hp, int maxHp) {
        if (access == null) return;
        synchronized (OP_LOCK) {
            if (!scopeMatches(access)) return;
            long now = safeNow(access);
            int safeId = id & 0xffff;
            Pending p;
            Record record;
            long observedGeneration;
            synchronized (LOCK) {
                if (current == null) return;
                observedGeneration = generation;
                int pi = findPendingIndex(safeId);
                p = pi >= 0 ? PENDING[pi] : null;
                int ri = findRecordIndex(current, safeId);
                record = ri >= 0 ? current.records[ri] : null;
            }

            if (record != null && isExcludedTemplate(access, record.template)) {
                forgetExcludedTemplate(access, record.template, now);
                return;
            }

            if (p != null) {
                Object found = safeFindForRecovery(access, safeId);
                if (found == UNKNOWN_FIND) return;
                boolean same = found == p.object;
                // A confirmed borrowed identity can receive a native/late reply
                // before its paced recovery query. New owned placeholders still
                // require a request or an authoritative packet-4 observation.
                boolean established;
                synchronized (LOCK) {
                    established = generation == observedGeneration && record != null
                            && findRecord(safeId) == record && record.confirmedThisMap
                            && record.object == p.object && !p.owned && same;
                }
                if (!p.observed && p.requests == 0 && !established) {
                    // Native parsing already wrote HP. An unsent, unobserved
                    // placeholder must keep its original quarantine.
                    if (same && scopeMatches(access)) safePending(access, p.object, true);
                    return;
                }
                boolean valid;
                synchronized (LOCK) {
                    valid = current != null && generation == observedGeneration
                            && findPendingIndex(safeId) >= 0
                            && findPending(safeId) == p
                            && record != null && findRecord(safeId) == record
                            && p.generation == observedGeneration
                            && (p.observed || p.requests > 0 || established)
                            && hp >= 0 && maxHp >= 0 && hp <= maxHp
                            && same && detailMatches(record, p, level, x, y);
                }
                if (valid) {
                    boolean shouldSave = false;
                    synchronized (LOCK) {
                        if (current != null && generation == observedGeneration
                                && findPending(safeId) == p && findRecord(safeId) == record) {
                            if (record.level != level) {
                                record.level = level;
                                markDirty(now);
                                shouldSave = true;
                            }
                            record.confirmedThisMap = true;
                            record.object = p.object;
                            record.retryDetail = false;
                            if (hp == 0) {
                                record.hasDeadSince = true;
                                record.deadSince = now;
                                record.nextCheckAt = now + RECOVERY_DEAD_CHECK_MS;
                                record.recoveryFailures = 0;
                                record.nextRecoveryAt = 0L;
                            } else {
                                clearRecoveryDeadState(record);
                            }
                            confirmedCount++;
                            removePending(findPendingIndex(safeId));
                        } else {
                            valid = false;
                        }
                    }
                    if (valid) {
                        if (p.watchdog) {
                            MonsterTargetWatchSupport.onDetail(access, safeId, p.object, true, hp);
                            if (hp > 0 && safeDeadState(access, p.object) == 1)
                                safePending(access, p.object, false);
                        }
                        else safePending(access, p.object, false);
                        if (hp == 0) retainCorpse(access, p.object, false);
                        if (shouldSave) flush(access, false, now);
                        return;
                    }
                }

                retireFailedPending(access, p, now, true);
                return;
            }

            // A real packet-4 object can teach its level and confirm the live ID.
            Object found = safeFindForRecovery(access, safeId);
            boolean trackedObject = false;
            boolean recoveryObject = false;
            boolean observedObject = false;
            synchronized (LOCK) {
                trackedObject = current != null && generation == observedGeneration
                        && record != null && findRecord(safeId) == record
                        && found != null && found != UNKNOWN_FIND
                        && record.confirmedThisMap && record.object == found;
                recoveryObject = current != null && generation == observedGeneration
                        && record != null && findRecord(safeId) == record
                        && record.object == found && (trackedObject || record.retryDetail);
                observedObject = current != null && generation == observedGeneration
                        && record != null && findRecord(safeId) == record && record.seenThisMap
                        && found != null && found != UNKNOWN_FIND;
            }
            if (record != null && (record.seenThisMap || trackedObject)
                    && found != UNKNOWN_FIND && found != null
                    && level > 0 && level <= 255 && hp >= 0 && maxHp >= 0 && hp <= maxHp
                    && near(record.x, x) && near(record.y, y)
                    && (record.level == 0 || record.level == level)) {
                boolean saveLevel = false;
                boolean unhide = false;
                int deadState = recoveryObject && hp > 0
                        ? safeDeadState(access, found) : 0;
                boolean wasRetiring = recoveryObject && hp > 0 && access.retiring(found);
                synchronized (LOCK) {
                    if (current != null && generation == observedGeneration
                            && (record.seenThisMap || (trackedObject && record.object == found))
                            && findRecord(safeId) == record
                            && (record.level == 0 || record.level == level)) {
                        if (record.level != level) {
                            record.level = level;
                            markDirty(now);
                            saveLevel = true;
                        }
                        record.confirmedThisMap = true;
                        record.object = found;
                        record.retryDetail = false;
                        if (hp == 0) {
                            record.hasDeadSince = true;
                            record.deadSince = now;
                            record.nextCheckAt = now + RECOVERY_DEAD_CHECK_MS;
                            record.recoveryFailures = 0;
                            record.nextRecoveryAt = 0L;
                        } else {
                            unhide = recoveryObject && record.object == found
                                    && (deadState == 1 || record.corpseHeld || wasRetiring);
                            clearRecoveryDeadState(record);
                        }
                    }
                }
                if (unhide && scopeMatches(access)
                        && safeFindForRecovery(access, safeId) == found) {
                    safePending(access, found, false);
                }
                if (hp == 0) retainCorpse(access, found, false);
                MonsterTargetWatchSupport.onDetail(access, safeId, found, true, hp);
                if (saveLevel) flush(access, false, now);
            } else if ((trackedObject || observedObject) && scopeMatches(access)
                    && safeFindForRecovery(access, safeId) == found
                    && access.type(found) == 1 && (access.id(found) & 0xffff) == safeId) {
                // The native receiver already wrote this invalid detail's HP.
                // Do not let a rejected response make a known object targetable.
                synchronized (LOCK) {
                    if (generation != observedGeneration || findRecord(safeId) != record
                            || (!record.seenThisMap && (!record.confirmedThisMap
                                    || record.object != found))) return;
                    record.object = found;
                    registerRecoveryFailure(record, now);
                    failCount++;
                }
                safePending(access, found, true);
                retainCorpse(access, found, false);
            }
        }
    }

    /** Advances restoration, detail requests and bounded persistence retries. */
    private static void tickImpl(MonsterCacheAccess access) {
        if (access == null) return;
        synchronized (OP_LOCK) {
            if (!scopeMatches(access)) {
                onMap(access);
                if (!scopeMatches(access)) return;
            }
        }
        long now = safeNow(access);
        boolean ready = safeReady(access);
        synchronized (LOCK) {
            activeAccess = access;
            if (ready) {
                if (!hasReadySince) {
                    hasReadySince = true;
                    readySince = now;
                }
            } else {
                hasReadySince = false;
                readySince = 0L;
            }
        }

        flush(access, false, now);
        if (!ready) return;

        long localGeneration;
        synchronized (LOCK) {
            if (current == null || current.count == 0) return;
            localGeneration = generation;
        }
        filterLateExcludedRecords(access, now, localGeneration);
        boolean restoreReady;
        synchronized (LOCK) {
            if (current == null || current.count == 0 || generation != localGeneration) return;
            restoreReady = hasReadySince && elapsed(now, readySince) >= RESTORE_SETTLE_MS;
        }
        if (restoreReady) restorePass(access, now, localGeneration);
        recoveryPass(access, now, localGeneration);
        synchronized (OP_LOCK) {
            if (generation != localGeneration || !scopeMatches(access)) return;
            processPending(access, now, localGeneration);
        }
        MonsterTargetWatchSupport.tick(access);
    }

    /** Rechecks a bounded slice so a late-loaded template catalog can purge old records. */
    private static void filterLateExcludedRecords(MonsterCacheAccess access, long now,
            long localGeneration) {
        int excludedCount = 0;
        synchronized (OP_LOCK) {
            if (!scopeMatches(access)) return;
            int scanLimit;
            synchronized (LOCK) {
                if (current == null || generation != localGeneration || current.count == 0) return;
                scanLimit = current.count < MAX_EXCLUDED_FILTER_RECORDS_PER_TICK
                        ? current.count : MAX_EXCLUDED_FILTER_RECORDS_PER_TICK;
            }
            int scanned;
            for (scanned = 0; scanned < scanLimit; scanned++) {
                Record record;
                synchronized (LOCK) {
                    if (current == null || generation != localGeneration || current.count == 0) return;
                    int count = current.count;
                    int index = excludedFilterCursor;
                    if (index < 0 || index >= count) index = 0;
                    excludedFilterCursor = index + 1;
                    if (excludedFilterCursor >= count) excludedFilterCursor = 0;
                    record = current.records[index];
                }
                if (record == null || !isExcludedTemplate(access, record.template)) continue;
                boolean alreadyListed = false;
                int i;
                for (i = 0; i < excludedCount; i++) {
                    if (EXCLUDED_FILTER_TEMPLATES[i] == record.template) {
                        alreadyListed = true;
                        break;
                    }
                }
                if (!alreadyListed) EXCLUDED_FILTER_TEMPLATES[excludedCount++] = record.template;
            }
            int i;
            for (i = 0; i < excludedCount; i++) {
                forgetExcludedTemplate(access, EXCLUDED_FILTER_TEMPLATES[i], now);
            }
        }
    }

    /** Optional HUD entry point; rendering is delegated to the native boundary. */
    private static void paintImpl(Object graphics) {
        MonsterCacheAccess access;
        String text;
        synchronized (LOCK) {
            access = activeAccess;
        }
        if (access == null) return;
        if (!safeReady(access)) return;
        long now = safeNow(access);
        synchronized (LOCK) {
            if (hudStatusCache == null || !hasHudStatusAt
                    || elapsed(now, hudStatusAt) >= 250L) {
                hudStatusCache = buildStatusLocked();
                hudStatusAt = now;
                hasHudStatusAt = true;
            }
            text = hudStatusCache;
        }
        try {
            access.hud(graphics, text);
        } catch (Throwable ignored) {
            noteFailure();
        }
    }

    /** A compact ASCII status suitable for a small native HUD line. */
    public static String status() {
        synchronized (LOCK) {
            return buildStatusLocked();
        }
    }

    /** Template/level history for one map; instance IDs stay scoped to their original zones. */
    public static int[] remembered(String server, int map) {
        try {
            if (map == CACHE_DISABLED_MAP_ID) return new int[0];
            synchronized (LOCK) {
                int[] values = new int[512];
                int count = 0;
                for (int i = 0; i < contextCount; i++) {
                    Context c = CONTEXTS[i];
                    if (!c.server.equals(server) || c.map != map) continue;
                    for (int j = 0; j < c.count; j++) {
                        Record r = c.records[j];
                        if (r.level <= 0) continue;
                        boolean exists = false;
                        for (int k = 0; k < count; k += 2)
                            if (values[k] == r.template && values[k + 1] == r.level) { exists = true; break; }
                        if (!exists && count + 2 <= values.length) {
                            values[count++] = r.template; values[count++] = r.level;
                        }
                    }
                }
                int[] result = new int[count];
                System.arraycopy(values, 0, result, 0, count);
                return result;
            }
        } catch (Throwable ignored) { return new int[0]; }
    }

    private static String buildStatusLocked() {
        int total = 0;
        int i;
        for (i = 0; i < contextCount; i++) total += CONTEXTS[i].count;
        int currentRecords = current == null ? 0 : current.count;
        int corpses = 0;
        if (current != null) for (i = 0; i < current.count; i++)
            if (current.records[i].corpseHeld) corpses++;
        StringBuffer out = new StringBuffer(72);
        out.append("QCache S:").append(total)
                .append(" R:").append(restoredCount)
                .append(" OK:").append(confirmedCount)
                .append(" W:").append(pendingCount)
                .append(" X:").append(failCount)
                .append('\n').append("M:").append(scopeMap)
                .append(" Z:").append(scopeZone + 1)
                .append(" C:").append(currentRecords)
                .append(" H:").append(changedIdCount)
                .append(" K:").append(corpses)
                .append(" P:").append(combatPacketCount)
                .append(" V:").append(combatReviveCount);
        return out.toString();
    }

    /** Host-side deterministic test reset. It does not touch bridge storage. */
    public static void resetForTest() {
        synchronized (OP_LOCK) {
            synchronized (LOCK) {
                int i;
                for (i = 0; i < MAX_CONTEXTS; i++) CONTEXTS[i] = null;
                for (i = 0; i < MAX_ZONE_RECORDS; i++) PENDING[i] = null;
                contextCount = 0;
                pendingCount = 0;
                storeLoaded = false;
                current = null;
                generation++;
                scopeMap = 0;
                scopeZone = 0;
                scopeServer = "";
                activeAccess = null;
                hasReadySince = false;
                readySince = 0L;
                restoreCursor = 0;
                recoveryCursor = 0;
            excludedFilterCursor = 0;
                hasRequested = false;
                lastRequestAt = 0L;
                dirty = false;
                dirtySince = 0L;
                retrySaveAt = 0L;
                revision = 0L;
                failCount = 0;
                restoredCount = 0;
                confirmedCount = 0;
                changedIdCount = 0;
                combatPacketCount = 0;
                combatReviveCount = 0;
                loadFailed = false;
                hudStatusCache = null;
                hasHudStatusAt = false;
            }
        }
    }

    private static void ensureLoaded(MonsterCacheAccess access) {
        String activeServer = null;
        try {
            activeServer = access.serverKey();
            if (activeServer == null) activeServer = "";
        } catch (Throwable ignored) {
            noteFailure();
        }
        boolean alreadyLoaded;
        Context[] loadedContexts = null;
        int loadedCount = 0;
        synchronized (LOCK) {
            alreadyLoaded = storeLoaded;
            if (alreadyLoaded) {
                loadedCount = contextCount;
                loadedContexts = new Context[loadedCount];
                System.arraycopy(CONTEXTS, 0, loadedContexts, 0, loadedCount);
            }
        }
        if (alreadyLoaded) {
            if (activeServer != null && removeExcludedRecords(access, loadedContexts,
                    loadedCount, activeServer)) {
                long now = safeNow(access);
                synchronized (LOCK) { markDirty(now); }
            }
            return;
        }
        byte[] data = null;
        boolean failed = false;
        try {
            data = access.load();
        } catch (Throwable ignored) {
            failed = true;
        }
        Decoded decoded = decode(data);
        if (data != null && data.length > 0 && decoded == null) failed = true;
        if (decoded != null && activeServer != null) {
            decoded.droppedExcludedTemplate = removeExcludedRecords(access,
                    decoded.contexts, decoded.count, activeServer);
        }
        synchronized (LOCK) {
            if (!storeLoaded) {
                if (decoded != null) {
                    contextCount = decoded.count;
                    int i;
                    for (i = 0; i < MAX_CONTEXTS; i++) {
                        CONTEXTS[i] = i < contextCount ? decoded.contexts[i] : null;
                    }
                    if (decoded.droppedDisabledMap || decoded.droppedExcludedTemplate) {
                        dirty = true;
                        revision++;
                    }
                }
                storeLoaded = true;
                loadFailed = failed;
            }
        }
    }

    private static boolean isExcludedTemplate(MonsterCacheAccess access, int template) {
        if (!(access instanceof MonsterCacheTemplateNames)) return false;
        try {
            String name = ((MonsterCacheTemplateNames)access).templateName(template);
            if (name == null) return false;
            String trimmed = name.trim();
            return EXCLUDED_TEMPLATE_NAME.equalsIgnoreCase(trimmed)
                    || "Lửa thiêng".equalsIgnoreCase(trimmed)
                    || "con Ma".equalsIgnoreCase(trimmed);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean removeExcludedRecords(MonsterCacheAccess access,
            Context[] contexts, int count, String server) {
        boolean changed = false;
        for (int i = 0; i < count; i++) {
            Context context = contexts[i];
            if (context == null || !context.server.equals(server)) continue;
            for (int j = 0; j < context.count;) {
                if (isExcludedTemplate(access, context.records[j].template)) {
                    removeRecord(context, j);
                    changed = true;
                } else {
                    j++;
                }
            }
        }
        return changed;
    }

    private static void forgetExcludedTemplate(MonsterCacheAccess access, int template,
            long now) {
        forgetExcludedTemplate(access, template, -1, now);
    }

    private static void forgetExcludedTemplate(MonsterCacheAccess access, int template,
            int replacementId, long now) {
        if (access == null) return;
        synchronized (OP_LOCK) {
            String activeServer;
            try {
                if (access.mapId() == CACHE_DISABLED_MAP_ID) return;
                activeServer = access.serverKey();
                if (activeServer == null) activeServer = "";
            } catch (Throwable ignored) {
                noteFailure();
                return;
            }
            if (!scopeMatches(access)) return;

            Pending[] stalePending = new Pending[MAX_ZONE_RECORDS];
            int staleCount = 0;
            boolean changed = false;
            synchronized (LOCK) {
                if (current == null) return;
                int i;
                for (i = 0; i < contextCount; i++) {
                    Context context = CONTEXTS[i];
                    if (context == null || !context.server.equals(activeServer)) continue;
                    for (int j = 0; j < context.count;) {
                        Record record = context.records[j];
                        if (record.template != template
                                && !(context == current && record.id == replacementId)) {
                            j++;
                            continue;
                        }
                        if (context == current) {
                            int pendingIndex = findPendingIndex(record.id);
                            if (pendingIndex >= 0 && staleCount < stalePending.length) {
                                stalePending[staleCount++] = PENDING[pendingIndex];
                                removePending(pendingIndex);
                            }
                        }
                        removeRecord(context, j);
                        changed = true;
                    }
                }
                if (changed) markDirty(now);
            }
            int i;
            for (i = 0; i < staleCount; i++) clearSupersededPending(access, stalePending[i]);
            if (changed) flush(access, true, now);
        }
    }

    private static void clearOldPlaceholders(MonsterCacheAccess access,
            Pending[] oldPending, int count) {
        int i;
        for (i = 0; i < count; i++) {
            Pending p = oldPending[i];
            if (p == null) continue;
            if (p.watchdog) continue;
            if (p.owned) {
                discardIfStillOwned(access, p);
            } else if (!p.identityMismatch && safeFind(access, p.id) == p.object) {
                safePending(access, p.object, false);
            }
        }
    }

    private static void clearSupersededPending(MonsterCacheAccess access, Pending pending) {
        if (pending.watchdog) {
            MonsterTargetWatchSupport.onDetail(access, pending.id, pending.object, false, -1);
            return;
        }
        if (pending.owned) {
            discardIfStillOwned(access, pending);
        } else if (safeFind(access, pending.id) == pending.object) {
            safePending(access, pending.object, false);
        }
    }

    private static void discardIfStillOwned(MonsterCacheAccess access, Pending p) {
        if (p == null || !p.owned) return;
        Object found = safeFind(access, p.id);
        if (found == p.object) safeDiscard(access, p.object);
    }

    private static void restorePass(MonsterCacheAccess access, long now, long localGeneration) {
        int attempts = 0;
        int inspected = 0;
        while (attempts < MAX_RESTORES_PER_TICK) {
            Record record;
            int id;
            int template;
            int x;
            int y;
            synchronized (LOCK) {
                if (current == null || generation != localGeneration || current.count == 0) return;
                int count = current.count;
                if (inspected >= count) return;
                int index = restoreCursor;
                if (index >= count) index = 0;
                restoreCursor = index + 1;
                if (restoreCursor >= count) restoreCursor = 0;
                inspected++;
                record = current.records[index];
                if (record == null || record.seenThisMap || record.restoreDone
                        || record.nextRestoreAt > now) continue;
                id = record.id;
                template = record.template;
                x = record.x;
                y = record.y;
            }

            synchronized (OP_LOCK) {
                if (!scopeMatches(access)) return;
                synchronized (LOCK) {
                    if (current == null || generation != localGeneration
                            || findRecord(id) != record || record.seenThisMap || record.restoreDone) {
                        continue;
                    }
                }
                if (isExcludedTemplate(access, template)) {
                    forgetExcludedTemplate(access, template, now);
                    continue;
                }
                Object existing = safeFind(access, id);
                if (existing != null) {
                    synchronized (LOCK) {
                        if (generation == localGeneration && findRecord(id) == record) {
                            record.restoreDone = true;
                        }
                    }
                    continue;
                }

                if (!scopeMatches(access)) return;
                attempts++;
                Object created = safeRestore(access, template, id, x, y);
                if (created == null) {
                    Object after = safeFind(access, id);
                    synchronized (LOCK) {
                        if (generation == localGeneration && findRecord(id) == record) {
                            if (after != null) {
                                record.restoreDone = true;
                            } else if (record.restoreAttempts < 1) {
                                record.restoreAttempts++;
                                record.nextRestoreAt = now + RESTORE_SETTLE_MS;
                            } else {
                                record.restoreDone = true;
                                registerRecoveryFailure(record, now);
                                failCount++;
                            }
                        }
                    }
                    continue;
                }

                if (!scopeMatches(access)) {
                    safeDiscard(access, created);
                    return;
                }
                if (safeFind(access, id) != created) {
                    // restore() returned an owned object, but a native object won the race.
                    safeDiscard(access, created);
                    synchronized (LOCK) {
                        if (generation == localGeneration && findRecord(id) == record) {
                            record.restoreDone = true;
                        }
                    }
                    continue;
                }

                boolean abandoned;
                synchronized (LOCK) {
                    abandoned = current == null || generation != localGeneration
                            || findRecord(id) != record || record.seenThisMap || record.restoreDone;
                    if (!abandoned) {
                        record.restoreDone = true;
                        addPending(new Pending(id, template, x, y, created, localGeneration, true, false));
                        restoredCount++;
                    }
                }
                if (abandoned) {
                    safeDiscard(access, created);
                    continue;
                }
                if (!scopeMatches(access)) return;
                try {
                    access.pending(created, true);
                } catch (Throwable ignored) {
                    noteFailure();
                    synchronized (LOCK) {
                        int pi = findPendingIndex(id);
                        if (pi >= 0 && PENDING[pi].object == created) removePending(pi);
                        if (generation == localGeneration && findRecord(id) == record)
                            registerRecoveryFailure(record, now);
                        failCount++;
                    }
                    if (safeFind(access, id) == created) safeDiscard(access, created);
                }
            }
        }
    }

    /** Audits learned IDs that were confirmed or await a paced detail retry. */
    private static void recoveryPass(MonsterCacheAccess access, long now, long localGeneration) {
        int inspected = 0;
        int recoveries = 0;
        synchronized (OP_LOCK) {
            if (generation != localGeneration || !scopeMatches(access)) return;
            while (inspected < MAX_RECOVERY_RECORDS_PER_TICK
                    && recoveries < MAX_RECOVERIES_PER_TICK) {
                if (!scopeMatches(access)) return;
                Record record;
                int id;
                synchronized (LOCK) {
                    if (current == null || generation != localGeneration || current.count == 0) return;
                    int count = current.count;
                    int index = recoveryCursor;
                    if (index >= count) index = 0;
                    recoveryCursor = index + 1;
                    if (recoveryCursor >= count) recoveryCursor = 0;
                    inspected++;
                    record = current.records[index];
                    if (record == null || (!record.confirmedThisMap && !record.retryDetail)
                            || findPendingIndex(record.id) >= 0 || record.nextCheckAt > now) continue;
                    record.nextCheckAt = now + RECOVERY_CHECK_MS;
                    id = record.id;
                }

                Object existing = safeFindForRecovery(access, id);
                if (existing == UNKNOWN_FIND) continue;
                int deadState = existing == null ? 1 : safeDeadState(access, existing);
                if (deadState < 0) continue;

                if (deadState == 0 && !record.retryDetail) {
                    synchronized (LOCK) {
                        if (current != null && generation == localGeneration
                                && findRecord(id) == record && findPendingIndex(id) < 0
                                && !record.retryDetail) {
                            record.object = existing;
                            clearRecoveryDeadState(record);
                        }
                    }
                    continue;
                }

                int template;
                int x;
                int y;
                synchronized (LOCK) {
                    if (current == null || generation != localGeneration
                            || findRecord(id) != record || findPendingIndex(id) >= 0
                            || (!record.confirmedThisMap && !record.retryDetail)) continue;
                    record.object = existing;
                    if (!record.hasDeadSince) {
                        record.hasDeadSince = true;
                        record.deadSince = now;
                    }
                    if ((!record.retryDetail && elapsed(now, record.deadSince) < RECOVERY_GRACE_MS)
                            || record.nextRecoveryAt > now) continue;
                    template = record.template;
                    x = record.x;
                    y = record.y;
                }

                recoveries++;
                boolean owned = existing == null;
                if (!scopeMatches(access)) return;
                Object candidate = owned ? safeRestore(access, template, id, x, y) : existing;
                if (candidate == null) {
                    synchronized (LOCK) {
                        if (current != null && generation == localGeneration
                                && findRecord(id) == record) registerRecoveryFailure(record, now);
                    }
                    continue;
                }

                Object after = safeFindForRecovery(access, id);
                if (after != candidate) {
                    if (owned) safeDiscard(access, candidate);
                    synchronized (LOCK) {
                        if (current != null && generation == localGeneration
                                && findRecord(id) == record) registerRecoveryFailure(record, now);
                    }
                    continue;
                }

                Pending pending = new Pending(id, template, x, y, candidate,
                        localGeneration, owned, true);
                if (!scopeMatches(access)) {
                    if (owned) safeDiscard(access, candidate);
                    return;
                }
                boolean queued;
                synchronized (LOCK) {
                    queued = current != null && generation == localGeneration
                            && findRecord(id) == record
                            && findPendingIndex(id) < 0 && pendingCount < MAX_ZONE_RECORDS;
                    if (queued) {
                        addPending(pending);
                        if (owned) restoredCount++;
                    } else if (current != null && generation == localGeneration
                            && findRecord(id) == record) {
                        registerRecoveryFailure(record, now);
                    }
                }
                if (!queued) {
                    if (owned && safeFindForRecovery(access, id) == candidate) safeDiscard(access, candidate);
                    continue;
                }

                if (!scopeMatches(access)) return;
                try {
                    access.pending(candidate, true);
                } catch (Throwable ignored) {
                    noteFailure();
                    synchronized (LOCK) {
                        int pi = findPendingIndex(id);
                        if (generation == localGeneration && pi >= 0 && PENDING[pi] == pending) {
                            removePending(pi);
                            if (current != null && findRecord(id) == record)
                                registerRecoveryFailure(record, now);
                        }
                    }
                    if (owned) {
                        if (safeFindForRecovery(access, id) == candidate) safeDiscard(access, candidate);
                    } else if (safeFindForRecovery(access, id) == candidate) {
                        safePending(access, candidate, false);
                    }
                }
            }
        }
    }

    private static void processPending(MonsterCacheAccess access, long now, long localGeneration) {
        int i = 0;
        while (true) {
            if (!scopeMatches(access)) return;
            Pending p;
            synchronized (LOCK) {
                if (generation != localGeneration || i >= pendingCount) return;
                p = PENDING[i];
            }
            if (p == null) {
                i++;
                continue;
            }
            if (p.requests > 0 && elapsed(now, p.firstRequestAt) >= PENDING_TIMEOUT_MS) {
                retireFailedPending(access, p, now, false);
                continue;
            }

            if (p.requests >= MAX_REQUESTS_PER_ID) {
                i++;
                continue;
            }

            boolean due;
            synchronized (LOCK) {
                due = !hasRequested || elapsed(now, lastRequestAt) >= REQUEST_INTERVAL_MS;
            }
            if (!due) return;

            Object found = safeFindForRecovery(access, p.id);
            if (found == UNKNOWN_FIND) return;
            if (found != p.object) {
                retireFailedPending(access, p, now, false);
                continue;
            }

            synchronized (LOCK) {
                int pi = findPendingIndex(p.id);
                if (pi < 0 || PENDING[pi] != p || p.requests >= MAX_REQUESTS_PER_ID) {
                    continue;
                }
                if (hasRequested && elapsed(now, lastRequestAt) < REQUEST_INTERVAL_MS) return;
                p.requests++;
                p.lastRequestAt = now;
                if (p.requests == 1) p.firstRequestAt = now;
                hasRequested = true;
                lastRequestAt = now;
            }
            if (!scopeMatches(access)) return;
            try {
                access.request(p.id);
            } catch (Throwable ignored) {
                noteFailure();
                retireFailedPending(access, p, now, false);
            }
            return;
        }
    }

    /** Ends every failed cycle; only its record/cooldown survives for a later retry. */
    private static void retireFailedPending(MonsterCacheAccess access, Pending p,
            long now, boolean mismatch) {
        boolean quarantineWatch = p.watchdog && mismatch && scopeMatches(access)
                && safeFindForRecovery(access, p.id) == p.object;
        synchronized (LOCK) {
            int pi = findPendingIndex(p.id);
            if (p.generation != generation || pi < 0 || PENDING[pi] != p) return;
            removePending(pi);
            failCount++;
            if (!p.watchdog || quarantineWatch) {
                Record record = findRecord(p.id);
                if (record != null && (!p.watchdog
                        || (record.confirmedThisMap && record.object == p.object))) {
                    record.restoreDone = true;
                    record.object = p.object;
                    registerRecoveryFailure(record, now);
                }
            }
        }
        if (p.watchdog) {
            MonsterTargetWatchSupport.onDetail(access, p.id, p.object, false, -1);
            if (quarantineWatch && scopeMatches(access)
                    && safeFindForRecovery(access, p.id) == p.object) {
                safePending(access, p.object, true);
                retainCorpse(access, p.object, false);
            }
        } else if (scopeMatches(access) && safeFindForRecovery(access, p.id) == p.object) {
            // Packet-4 can adopt a placeholder; never delete that observed identity.
            if (p.owned && !p.observed) safeDiscard(access, p.object);
            // A rejected detail may already have written HP in the native receiver.
            // Keep borrowed objects quarantined until a matching server detail arrives.
            else safePending(access, p.object, mismatch);
        }
    }

    private static boolean detailMatches(Record record, Pending pending, int level,
            int x, int y) {
        if (record.template != pending.template || pending.template != record.template) return false;
        if (!near(record.x, x) || !near(record.y, y)) return false;
        if (record.level > 0 && record.level != level) return false;
        return level > 0 && level <= 255;
    }

    private static boolean scopeMatches(MonsterCacheAccess access) {
        String server;
        int map;
        int zone;
        try {
            server = access.serverKey();
            if (server == null) server = "";
            map = access.mapId();
            zone = access.zoneId();
        } catch (Throwable ignored) {
            noteFailure();
            return false;
        }
        synchronized (LOCK) {
            return scopeMap == map && scopeZone == zone && scopeServer.equals(server)
                    && (current != null || map == CACHE_DISABLED_MAP_ID);
        }
    }

    private static boolean near(int a, int b) {
        return Math.abs(a - b) <= 96;
    }

    private static long elapsed(long now, long then) {
        if (now < then) return 0L;
        return now - then;
    }

    private static long safeNow(MonsterCacheAccess access) {
        try {
            return access.now();
        } catch (Throwable ignored) {
            noteFailure();
            return 0L;
        }
    }

    private static boolean safeReady(MonsterCacheAccess access) {
        try {
            return access.ready();
        } catch (Throwable ignored) {
            noteFailure();
            return false;
        }
    }

    private static Object safeFind(MonsterCacheAccess access, int id) {
        try {
            return access.find(id);
        } catch (Throwable ignored) {
            noteFailure();
            return null;
        }
    }

    private static Object safeFindForRecovery(MonsterCacheAccess access, int id) {
        try {
            return access.find(id);
        } catch (Throwable ignored) {
            noteFailure();
            return UNKNOWN_FIND;
        }
    }

    private static int safeDeadState(MonsterCacheAccess access, Object object) {
        try {
            return access.dead(object) ? 1 : 0;
        } catch (Throwable ignored) {
            noteFailure();
            return -1;
        }
    }

    private static int safeHiddenState(MonsterCacheAccess access, Object object) {
        try {
            return access.hidden(object) ? 1 : 0;
        } catch (Throwable ignored) {
            noteFailure();
            return -1;
        }
    }

    private static void clearRecoveryDeadState(Record record) {
        record.hasDeadSince = false;
        record.deadSince = 0L;
        record.recoveryFailures = 0;
        record.nextRecoveryAt = 0L;
        record.retryDetail = false;
        record.corpseHeld = false;
    }

    private static void registerRecoveryFailure(Record record, long now) {
        record.retryDetail = true;
        if (record.recoveryFailures < MAX_RECOVERY_FAILURES) record.recoveryFailures++;
        if (record.recoveryFailures >= MAX_RECOVERY_FAILURES) {
            record.nextRecoveryAt = now + RECOVERY_BACKOFF_MAX_MS;
        } else if (record.recoveryFailures == 1) {
            record.nextRecoveryAt = now + RECOVERY_BACKOFF_FIRST_MS;
        } else {
            record.nextRecoveryAt = now + RECOVERY_BACKOFF_SECOND_MS;
        }
    }

    private static Object safeRestore(MonsterCacheAccess access, int template, int id,
            int x, int y) {
        try {
            return access.restore(template, id, x, y);
        } catch (Throwable ignored) {
            noteFailure();
            return null;
        }
    }

    private static void safePending(MonsterCacheAccess access, Object object, boolean value) {
        try {
            access.pending(object, value);
        } catch (Throwable ignored) {
            noteFailure();
        }
    }

    private static void safeDiscard(MonsterCacheAccess access, Object object) {
        try {
            access.discard(object);
        } catch (Throwable ignored) {
            noteFailure();
        }
    }

    private static void noteFailure() {
        synchronized (LOCK) {
            failCount++;
        }
    }

    private static void markDirty(long now) {
        if (!dirty) dirtySince = now;
        dirty = true;
        revision++;
    }

    private static void flush(MonsterCacheAccess access, boolean force, long now) {
        if (access == null) return;
        // Serialize map transitions against RMS writes so an exit hook or retry
        // cannot save while the native client is on the excluded map.
        synchronized (OP_LOCK) {
            int map;
            try {
                map = access.mapId();
            } catch (Throwable ignored) {
                noteFailure();
                return;
            }
            if (map == CACHE_DISABLED_MAP_ID) return;
            flushEnabled(access, force, now);
        }
    }

    private static void flushEnabled(MonsterCacheAccess access, boolean force, long now) {
        synchronized (SAVE_LOCK) {
            byte[] data;
            long savedRevision;
            synchronized (LOCK) {
                if (!storeLoaded || contextCount == 0) return;
                if (!force && (!dirty || elapsed(now, dirtySince) < SAVE_RETRY_MS
                        || (retrySaveAt > now))) return;
                pruneSerializedBudget();
                data = encode(CONTEXTS, contextCount);
                savedRevision = revision;
                if (data == null || data.length > MAX_SERIALIZED_BYTES) {
                    failCount++;
                    dirty = true;
                    retrySaveAt = now + SAVE_RETRY_MS;
                    return;
                }
            }
            boolean ok;
            try {
                ok = access.save(data);
            } catch (Throwable ignored) {
                ok = false;
            }
            synchronized (LOCK) {
                if (ok) {
                    if (revision == savedRevision) dirty = false;
                    retrySaveAt = 0L;
                } else {
                    failCount++;
                    dirty = true;
                    retrySaveAt = now + SAVE_RETRY_MS;
                }
            }
        }
    }

    private static Context findContext(String server, int map, int zone) {
        int i;
        for (i = 0; i < contextCount; i++) {
            Context c = CONTEXTS[i];
            if (c.map == map && c.zone == zone && c.server.equals(server)) return c;
        }
        return null;
    }

    private static int serializedSize() {
        int bytes = 8;
        for (int i = 0; i < contextCount; i++) {
            Context c = CONTEXTS[i];
            int utf = 0;
            for (int j = 0; j < c.server.length(); j++) {
                char ch = c.server.charAt(j);
                utf += ch > 0 && ch <= 127 ? 1 : ch <= 2047 ? 2 : 3;
            }
            bytes += 12 + utf + c.count * 10;
        }
        return bytes;
    }

    private static void pruneSerializedBudget() {
        while (contextCount > 1 && serializedSize() > MAX_SERIALIZED_BYTES) {
            int index = CONTEXTS[0] == current ? 1 : 0;
            for (int i = index + 1; i < contextCount; i++) CONTEXTS[i - 1] = CONTEXTS[i];
            CONTEXTS[--contextCount] = null;
            dirty = true;
            revision++;
        }
    }

    private static Context addContext(String server, int map, int zone) {
        if (map == CACHE_DISABLED_MAP_ID) return null;
        if (contextCount == MAX_CONTEXTS) {
            int i;
            for (i = 1; i < contextCount; i++) CONTEXTS[i - 1] = CONTEXTS[i];
            CONTEXTS[--contextCount] = null;
        }
        Context c = new Context(server, map, zone);
        CONTEXTS[contextCount++] = c;
        dirty = true;
        revision++;
        return c;
    }

    private static void moveContextToEnd(Context context) {
        int index = -1;
        int i;
        for (i = 0; i < contextCount; i++) {
            if (CONTEXTS[i] == context) {
                index = i;
                break;
            }
        }
        if (index < 0 || index == contextCount - 1) return;
        for (i = index + 1; i < contextCount; i++) CONTEXTS[i - 1] = CONTEXTS[i];
        CONTEXTS[contextCount - 1] = context;
        revision++;
        dirty = true;
    }

    private static void resetSession(Context context) {
        int i;
        for (i = 0; i < context.count; i++) {
            Record r = context.records[i];
            r.seenThisMap = false;
            r.restoreDone = false;
            r.restoreAttempts = 0;
            r.nextRestoreAt = 0L;
            r.confirmedThisMap = false;
            r.object = null;
            r.hasDeadSince = false;
            r.deadSince = 0L;
            r.nextCheckAt = 0L;
            r.nextRecoveryAt = 0L;
            r.recoveryFailures = 0;
            r.retryDetail = false;
            r.corpseHeld = false;
        }
    }

    private static int findRecordIndex(Context context, int id) {
        int i;
        for (i = 0; i < context.count; i++) {
            if (context.records[i].id == id) return i;
        }
        return -1;
    }

    private static Record findRecord(int id) {
        if (current == null) return null;
        int index = findRecordIndex(current, id);
        return index < 0 ? null : current.records[index];
    }

    private static void removeRecord(Context context, int index) {
        if (index < 0 || index >= context.count) return;
        int i;
        for (i = index + 1; i < context.count; i++) context.records[i - 1] = context.records[i];
        context.records[--context.count] = null;
        if (context == current) {
            if (restoreCursor > index) restoreCursor--;
            if (restoreCursor >= context.count) restoreCursor = 0;
            if (recoveryCursor > index) recoveryCursor--;
            if (recoveryCursor >= context.count) recoveryCursor = 0;
        }
    }

    private static int findPendingIndex(int id) {
        int i;
        for (i = 0; i < pendingCount; i++) {
            if (PENDING[i].id == id) return i;
        }
        return -1;
    }

    private static Pending findPending(int id) {
        int index = findPendingIndex(id);
        return index < 0 ? null : PENDING[index];
    }

    private static void addPending(Pending pending) {
        int existing = findPendingIndex(pending.id);
        if (existing >= 0) {
            PENDING[existing] = pending;
            return;
        }
        if (pendingCount < MAX_ZONE_RECORDS) PENDING[pendingCount++] = pending;
    }

    private static void removePending(int index) {
        if (index < 0 || index >= pendingCount) return;
        int i;
        for (i = index + 1; i < pendingCount; i++) PENDING[i - 1] = PENDING[i];
        PENDING[--pendingCount] = null;
    }

    private static byte[] encode(Context[] contexts, int count) {
        ByteArrayOutputStream bytes = null;
        DataOutputStream out = null;
        try {
            bytes = new ByteArrayOutputStream(1024);
            out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeShort(VERSION);
            int eligibleContexts = 0;
            int i;
            for (i = 0; i < count; i++) {
                Context context = contexts[i];
                if (context != null && context.map != CACHE_DISABLED_MAP_ID) eligibleContexts++;
            }
            out.writeShort(eligibleContexts);
            int totalRecords = 0;
            for (i = 0; i < count; i++) {
                Context c = contexts[i];
                if (c != null && c.map == CACHE_DISABLED_MAP_ID) continue;
                if (c == null || c.count < 0 || c.count > MAX_ZONE_RECORDS
                        || c.server.length() > MAX_SERVER_KEY_CHARS) return null;
                totalRecords += c.count;
                if (totalRecords > MAX_CONTEXTS * MAX_ZONE_RECORDS) return null;
                out.writeUTF(c.server);
                out.writeInt(c.map);
                out.writeInt(c.zone);
                out.writeShort(c.count);
                int j;
                for (j = 0; j < c.count; j++) {
                    Record r = c.records[j];
                    out.writeShort(r.id);
                    out.writeShort(r.template);
                    out.writeShort(r.x);
                    out.writeShort(r.y);
                    out.writeShort(r.level);
                }
                if (bytes.size() > MAX_SERIALIZED_BYTES) return null;
            }
            out.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > MAX_SERIALIZED_BYTES) return null;
            return result;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (out != null) {
                try { out.close(); } catch (IOException ignored) { }
            }
        }
    }

    private static Decoded decode(byte[] data) {
        if (data == null || data.length == 0) return new Decoded();
        if (data.length > MAX_SERIALIZED_BYTES || data.length < 8) return null;
        DataInputStream in = null;
        try {
            in = new DataInputStream(new ByteArrayInputStream(data));
            if (in.readInt() != MAGIC || in.readUnsignedShort() != VERSION) return null;
            int count = in.readUnsignedShort();
            if (count > MAX_CONTEXTS) return null;
            Decoded decoded = new Decoded();
            decoded.count = count;
            int totalRecords = 0;
            int i;
            for (i = 0; i < count; i++) {
                String server = in.readUTF();
                if (server == null || server.length() > MAX_SERVER_KEY_CHARS) return null;
                int map = in.readInt();
                int zone = in.readInt();
                int records = in.readUnsignedShort();
                if (records > MAX_ZONE_RECORDS) return null;
                totalRecords += records;
                if (totalRecords > MAX_CONTEXTS * MAX_ZONE_RECORDS) return null;
                Context context = new Context(server, map, zone);
                context.count = 0;
                int j;
                for (j = 0; j < records; j++) {
                    int id = in.readUnsignedShort();
                    int template = in.readUnsignedShort();
                    int x = in.readShort();
                    int y = in.readShort();
                    int level = in.readUnsignedShort();
                    if (level > 255 || findRecordIndex(context, id) >= 0) return null;
                    context.records[context.count++] = new Record(id, template, x, y, level);
                }
                int k;
                for (k = 0; k < i; k++) {
                    Context previous = decoded.contexts[k];
                    if (previous.map == map && previous.zone == zone && previous.server.equals(server)) return null;
                }
                decoded.contexts[i] = context;
            }
            if (in.available() != 0) return null;
            // Legacy versions may already contain map-48 records. Validate the
            // complete payload above, then drop those contexts before exposing
            // any records to restore or remembered-history paths.
            int kept = 0;
            for (i = 0; i < decoded.count; i++) {
                Context context = decoded.contexts[i];
                if (context.map == CACHE_DISABLED_MAP_ID) {
                    decoded.droppedDisabledMap = true;
                } else {
                    decoded.contexts[kept++] = context;
                }
            }
            int retainedCount = kept;
            while (kept < decoded.count) decoded.contexts[kept++] = null;
            decoded.count = retainedCount;
            return decoded;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
    }

    private static final class Context {
        final String server;
        final int map;
        final int zone;
        final Record[] records = new Record[MAX_ZONE_RECORDS];
        int count;

        Context(String server, int map, int zone) {
            this.server = server;
            this.map = map;
            this.zone = zone;
        }
    }

    private static final class Record {
        final int id;
        int template;
        int x;
        int y;
        int level;
        boolean seenThisMap;
        boolean restoreDone;
        boolean confirmedThisMap;
        int restoreAttempts;
        long nextRestoreAt;
        Object object;
        boolean hasDeadSince;
        long deadSince;
        long nextCheckAt;
        long nextRecoveryAt;
        int recoveryFailures;
        boolean retryDetail;
        boolean corpseHeld;

        Record(int id, int template, int x, int y, int level) {
            this.id = id;
            this.template = template;
            this.x = x;
            this.y = y;
            this.level = level;
        }
    }

    private static final class Pending {
        final int id;
        final int template;
        final int x;
        final int y;
        final Object object;
        int requests;
        long lastRequestAt;
        long firstRequestAt;
        boolean observed;
        final boolean owned;
        final boolean recovery;
        final boolean watchdog;
        boolean identityMismatch;
        final long generation;

        Pending(int id, int template, int x, int y, Object object, long generation,
                boolean owned, boolean recovery) {
            this(id, template, x, y, object, generation, owned, recovery, false);
        }

        Pending(int id, int template, int x, int y, Object object, long generation,
                boolean owned, boolean recovery, boolean watchdog) {
            this.id = id;
            this.template = template;
            this.x = x;
            this.y = y;
            this.object = object;
            this.generation = generation;
            this.owned = owned;
            this.recovery = recovery;
            this.watchdog = watchdog;
        }
    }

    private static final class Decoded {
        final Context[] contexts = new Context[MAX_CONTEXTS];
        int count;
        boolean droppedDisabledMap;
        boolean droppedExcludedTemplate;
    }
}
