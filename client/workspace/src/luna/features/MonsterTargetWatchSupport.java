package luna.features;

import luna.api.MonsterCacheAccess;

/**
 * Bounded, session-only watchdog for auto-attacked monster targets that make
 * no observable HP progress. Native state changes and detail-query ownership
 * remain at the cache bridge boundary.
 */
public final class MonsterTargetWatchSupport {

    private static final int MAX_EXCLUSIONS = 32;
    private static final int REQUIRED_SENDS = 3;
    private static final long REQUIRED_STALL_MS = 2500L;
    private static final long IDLE_RESET_MS = 30000L;
    private static final long RECENT_SEND_MS = 5000L;
    private static final long ROTATE_MS = 3000L;
    private static final long MAX_ROTATE_MS = 30000L;
    private static final long VERIFY_WAIT_MS = 10000L;

    private static final Object LOCK = new Object();
    private static final Exclusion[] EXCLUSIONS = new Exclusion[MAX_EXCLUSIONS];

    private static boolean hasScope;
    private static String scopeServer = "";
    private static int scopeMap;
    private static int scopeZone;
    private static long scopeVersion;

    private static int primaryId = -1;
    private static Object primaryObject;
    private static int baselineHp;
    private static int sentSinceProgress;
    private static long noProgressSince = -1L;
    private static long lastSentAt = -1L;
    private static int nextQueryToken;

    static {
        int i;
        for (i = 0; i < MAX_EXCLUSIONS; i++) EXCLUSIONS[i] = new Exclusion();
    }

    private MonsterTargetWatchSupport() {
    }

    /** Clears all transient observations at a native map/zone boundary. */
    public static void onMap(MonsterCacheAccess access) {
        try {
            if (access == null) {
                synchronized (LOCK) {
                    clearAllLocked();
                    hasScope = false;
                    scopeServer = "";
                }
                return;
            }
            String server = safeServer(access);
            int map = access.mapId();
            int zone = access.zoneId();
            synchronized (LOCK) {
                clearAllLocked();
                hasScope = true;
                scopeServer = server;
                scopeMap = map;
                scopeZone = zone;
                scopeVersion++;
            }
        } catch (Throwable ignored) {
            resetAfterFailure();
        }
    }

    /** Called only after a monster attack packet has been serialized and queued. */
    public static void onAttack(MonsterCacheAccess access, int targetId) {
        try {
            if (access == null) return;
            if (!enterScope(access)) return;
            if (!safeAutoEnabled(access)) {
                clearWatchState();
                return;
            }
            if (!safeReady(access)) return;

            long now = access.now();
            int id = targetId & 0xffff;
            Object object = access.find(id);
            if (object == null || (access.id(object) & 0xffff) != id
                    || access.type(object) != 1) {
                synchronized (LOCK) { resetPrimaryLocked(); }
                return;
            }
            int hp = access.hp(object);
            if (hp <= 0) {
                synchronized (LOCK) {
                    removeExclusionLocked(id, object);
                    if (primaryId == id && primaryObject == object) resetPrimaryLocked();
                }
                return;
            }

            synchronized (LOCK) {
                advanceExclusionsLocked(now);
                if (isExcludedLocked(id, object, now)) return;

                if (primaryObject != object || primaryId != id) {
                    resetPrimaryLocked();
                    primaryId = id;
                    primaryObject = object;
                    baselineHp = hp;
                    noProgressSince = now;
                } else if (hp != baselineHp) {
                    baselineHp = hp;
                    sentSinceProgress = 0;
                    noProgressSince = now;
                    lastSentAt = -1L;
                    removeExclusionLocked(id, object);
                }

                if (lastSentAt >= 0L && elapsed(now, lastSentAt) > IDLE_RESET_MS) {
                    sentSinceProgress = 0;
                    noProgressSince = now;
                }
                if (noProgressSince < 0L) noProgressSince = now;
                if (sentSinceProgress < Integer.MAX_VALUE) sentSinceProgress++;
                lastSentAt = now;
            }
        } catch (Throwable ignored) {
            resetAfterFailure();
        }
    }

    /** Advances the single active primary watch and schedules bounded queries. */
    public static void tick(MonsterCacheAccess access) {
        try {
            if (access == null || !enterScope(access)) return;
            if (!safeAutoEnabled(access)) {
                clearWatchState();
                return;
            }
            if (!safeReady(access)) return;

            long now = access.now();
            int id;
            Object object;
            int expectedHp;
            int sends;
            long since;
            long lastSend;
            long version;
            synchronized (LOCK) {
                advanceExclusionsLocked(now);
                id = primaryId;
                object = primaryObject;
                expectedHp = baselineHp;
                sends = sentSinceProgress;
                since = noProgressSince;
                lastSend = lastSentAt;
                version = scopeVersion;
            }
            if (object == null || id < 0) return;

            Object current = access.find(id);
            if (current != object || (access.id(object) & 0xffff) != id
                    || access.type(object) != 1) {
                synchronized (LOCK) {
                    if (version == scopeVersion && primaryId == id && primaryObject == object)
                        resetPrimaryLocked();
                }
                return;
            }

            int hp = access.hp(object);
            if (hp <= 0) {
                synchronized (LOCK) {
                    if (version == scopeVersion && primaryId == id && primaryObject == object) {
                        removeExclusionLocked(id, object);
                        baselineHp = hp;
                        sentSinceProgress = 0;
                        noProgressSince = now;
                        lastSentAt = -1L;
                    }
                }
                return;
            }
            if (hp != expectedHp) {
                synchronized (LOCK) {
                    if (version == scopeVersion && primaryId == id && primaryObject == object) {
                        baselineHp = hp;
                        sentSinceProgress = 0;
                        noProgressSince = now;
                        lastSentAt = -1L;
                        removeExclusionLocked(id, object);
                    }
                }
                return;
            }

            if (sends < REQUIRED_SENDS || since < 0L
                    || elapsed(now, since) < REQUIRED_STALL_MS
                    || lastSend < 0L || elapsed(now, lastSend) > RECENT_SEND_MS) return;

            int token;
            Exclusion exclusion;
            synchronized (LOCK) {
                if (version != scopeVersion || primaryId != id || primaryObject != object
                        || sentSinceProgress < REQUIRED_SENDS
                        || isExcludedLocked(id, object, now)) return;
                exclusion = reserveExclusionLocked(id, object, expectedHp, now);
                if (exclusion == null) return;
                nextQueryToken++;
                if (nextQueryToken == 0) nextQueryToken++;
                token = nextQueryToken;
                exclusion.queryToken = token;
                exclusion.queryPending = true;
                exclusion.until = now + VERIFY_WAIT_MS;
                exclusion.createdAt = now;
            }

            // Native target mutation and cache verification must not run under LOCK.
            if (access.find(id) == object) access.clearAutoTarget(object);
            boolean queued = false;
            if (access.find(id) == object && safeAutoEnabled(access)) {
                queued = MonsterCacheSupport.verifyTarget(access, id, object);
            }

            synchronized (LOCK) {
                if (version == scopeVersion && exclusion.used && exclusion.id == id
                        && exclusion.object == object && exclusion.queryToken == token
                        && exclusion.queryPending && !queued) {
                    exclusion.queryPending = false;
                    exclusion.until = now + rotationDelay(exclusion);
                    resetPrimaryLocked();
                }
            }
        } catch (Throwable ignored) {
            resetAfterFailure();
        }
    }

    /**
     * Auto-target filter. It excludes only the exact object identity during a
     * short watchdog/query window; it never changes combat or HP state.
     */
    public static boolean allow(MonsterCacheAccess access, Object object) {
        try {
            if (object == null || access == null) return true;
            if (!enterScope(access)) return true;
            if (!safeAutoEnabled(access)) {
                clearWatchState();
                return true;
            }
            if (!safeReady(access)) return true;
            if (access.type(object) != 1) return true;
            if (access.hp(object) <= 0) return false;

            long now = access.now();
            int id = access.id(object) & 0xffff;
            synchronized (LOCK) {
                advanceExclusionsLocked(now);
                return !isExcludedLocked(id, object, now);
            }
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Receives a cache-validated packet-7 response or a failed/expired query. */
    public static void onDetail(MonsterCacheAccess access, int id, Object object,
            boolean valid, int hp) {
        try {
            if (access == null || object == null || !enterScope(access)) return;
            long now = access.now();
            int safeId = id & 0xffff;
            boolean currentIdentity = (access.id(object) & 0xffff) == safeId
                    && access.type(object) == 1 && access.find(safeId) == object;
            boolean accepted = valid && currentIdentity;

            synchronized (LOCK) {
                advanceExclusionsLocked(now);
                Exclusion exclusion = findExclusionLocked(safeId, object);
                if (accepted && hp > 0) {
                    boolean changed = exclusion != null && hp != exclusion.baselineHp;
                    if (exclusion != null) {
                        if (changed) {
                            clearExclusion(exclusion);
                        } else if (exclusion.queryPending) {
                            exclusion.queryPending = false;
                            exclusion.until = now + rotationDelay(exclusion);
                        }
                    }
                    if (primaryId == safeId && primaryObject == object) {
                        baselineHp = hp;
                        sentSinceProgress = 0;
                        noProgressSince = now;
                        lastSentAt = -1L;
                    }
                } else if (accepted && hp == 0) {
                    if (exclusion != null) clearExclusion(exclusion);
                    if (primaryId == safeId && primaryObject == object) {
                        baselineHp = 0;
                        sentSinceProgress = 0;
                        noProgressSince = now;
                        lastSentAt = -1L;
                    }
                } else if (exclusion != null && exclusion.queryPending) {
                    exclusion.queryPending = false;
                    exclusion.until = now + rotationDelay(exclusion);
                    if (primaryId == safeId && primaryObject == object) {
                        sentSinceProgress = 0;
                        noProgressSince = now;
                        lastSentAt = -1L;
                    }
                }
            }
        } catch (Throwable ignored) {
            resetAfterFailure();
        }
    }

    /** A received HP change immediately releases a stalled identity, including respawn. */
    public static void onHpProgress(MonsterCacheAccess access, int id, Object object, int hp) {
        onHpProgressImpl(access, id, object, hp, false);
    }

    /** A validated death-to-live transition is progress even at the old HP. */
    public static void onRevived(MonsterCacheAccess access, int id, Object object, int hp) {
        if (hp > 0) onHpProgressImpl(access, id, object, hp, true);
    }

    private static void onHpProgressImpl(MonsterCacheAccess access, int id, Object object,
            int hp, boolean revived) {
        try {
            if (access == null || object == null) return;
            int safeId = id & 0xffff;
            synchronized (LOCK) {
                if ((primaryObject != object || primaryId != safeId)
                        && findExclusionLocked(safeId, object) == null) return;
            }
            // Most remote victims are not the watched target. Avoid a second
            // linear world lookup for each of them in a crowded combat packet.
            if (!enterScope(access) || access.find(safeId) != object) return;
            synchronized (LOCK) {
                Exclusion e = findExclusionLocked(safeId, object);
                if (e != null && (revived || hp == 0 || hp != e.baselineHp)) clearExclusion(e);
                if (primaryObject == object && primaryId == safeId && (revived || hp != baselineHp)) {
                    baselineHp = hp; sentSinceProgress = 0; noProgressSince = access.now(); lastSentAt = -1L;
                }
            }
        } catch (Throwable ignored) { resetAfterFailure(); }
    }

    private static long rotationDelay(Exclusion e) {
        long delay = ROTATE_MS;
        for (int i = 1; i < e.stalls && delay < MAX_ROTATE_MS; i++) delay *= 2L;
        return delay > MAX_ROTATE_MS ? MAX_ROTATE_MS : delay;
    }

    private static boolean enterScope(MonsterCacheAccess access) {
        String server;
        int map;
        int zone;
        try {
            server = safeServer(access);
            map = access.mapId();
            zone = access.zoneId();
        } catch (Throwable ignored) {
            resetAfterFailure();
            return false;
        }
        synchronized (LOCK) {
            if (!hasScope || scopeMap != map || scopeZone != zone
                    || !scopeServer.equals(server)) {
                clearAllLocked();
                hasScope = true;
                scopeServer = server;
                scopeMap = map;
                scopeZone = zone;
                scopeVersion++;
            }
        }
        return true;
    }

    private static String safeServer(MonsterCacheAccess access) {
        String server = access.serverKey();
        return server == null ? "" : server;
    }

    private static boolean safeAutoEnabled(MonsterCacheAccess access) {
        try {
            return access.autoEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean safeReady(MonsterCacheAccess access) {
        try {
            return access.ready();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void advanceExclusionsLocked(long now) {
        int i;
        for (i = 0; i < MAX_EXCLUSIONS; i++) {
            Exclusion e = EXCLUSIONS[i];
            if (!e.used) continue;
            if (e.queryPending && elapsed(now, e.until) > 0L) {
                e.queryPending = false;
                e.until = now + rotationDelay(e);
                if (primaryId == e.id && primaryObject == e.object) resetPrimaryLocked();
            } else if (!e.queryPending && e.until > 0L && now >= e.until) {
                if (primaryId == e.id && primaryObject == e.object) resetPrimaryLocked();
                e.until = 0L; // retain bounded identity/history until HP progresses or LRU eviction
            }
        }
    }

    private static Exclusion reserveExclusionLocked(int id, Object object, int hp, long now) {
        Exclusion e = findExclusionLocked(id, object);
        if (e == null) {
            int i;
            for (i = 0; i < MAX_EXCLUSIONS; i++) {
                if (!EXCLUSIONS[i].used) {
                    e = EXCLUSIONS[i];
                    break;
                }
            }
        }
        if (e == null) {
            int i;
            e = EXCLUSIONS[0];
            for (i = 1; i < MAX_EXCLUSIONS; i++) {
                if (EXCLUSIONS[i].createdAt < e.createdAt) e = EXCLUSIONS[i];
            }
        }
        e.used = true;
        if (e.id != id || e.object != object || e.baselineHp != hp) e.stalls = 0;
        if (e.stalls < 5) e.stalls++;
        e.id = id;
        e.object = object;
        e.baselineHp = hp;
        e.createdAt = now;
        return e;
    }

    private static Exclusion findExclusionLocked(int id, Object object) {
        int i;
        for (i = 0; i < MAX_EXCLUSIONS; i++) {
            Exclusion e = EXCLUSIONS[i];
            if (e.used && e.id == id && e.object == object) return e;
        }
        return null;
    }

    private static boolean isExcludedLocked(int id, Object object, long now) {
        Exclusion e = findExclusionLocked(id, object);
        return e != null && (e.queryPending || now < e.until);
    }

    private static void removeExclusionLocked(int id, Object object) {
        Exclusion e = findExclusionLocked(id, object);
        if (e != null) clearExclusion(e);
    }

    private static void clearExclusion(Exclusion e) {
        e.used = false;
        e.id = -1;
        e.object = null;
        e.baselineHp = 0;
        e.until = 0L;
        e.createdAt = 0L;
        e.queryPending = false;
        e.queryToken = 0;
        e.stalls = 0;
    }

    private static void resetPrimaryLocked() {
        primaryId = -1;
        primaryObject = null;
        baselineHp = 0;
        sentSinceProgress = 0;
        noProgressSince = -1L;
        lastSentAt = -1L;
    }

    private static void clearAllLocked() {
        int i;
        for (i = 0; i < MAX_EXCLUSIONS; i++) clearExclusion(EXCLUSIONS[i]);
        resetPrimaryLocked();
        nextQueryToken = 0;
        scopeVersion++;
    }

    private static void clearWatchState() {
        synchronized (LOCK) {
            clearAllLocked();
            hasScope = false;
            scopeServer = "";
        }
    }

    private static void resetAfterFailure() {
        synchronized (LOCK) {
            clearAllLocked();
            hasScope = false;
            scopeServer = "";
        }
    }

    private static long elapsed(long now, long then) {
        if (now < then) return 0L;
        return now - then;
    }

    /** Test-only reset; no native bridge state is changed. */
    public static void resetForTest() {
        synchronized (LOCK) {
            clearAllLocked();
            hasScope = false;
            scopeServer = "";
            scopeMap = 0;
            scopeZone = 0;
            scopeVersion = 0L;
        }
    }

    private static final class Exclusion {
        boolean used;
        int id = -1;
        Object object;
        int baselineHp;
        long until;
        long createdAt;
        boolean queryPending;
        int queryToken;
        int stalls;
    }
}
