import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import luna.api.MonsterCacheAccess;
import luna.api.MonsterCacheTemplateNames;

/** Host-side lifecycle checks for the standalone monster-cache support. */
public final class HungBySonMonsterCacheHostTest {
    private static int checks;

    private HungBySonMonsterCacheHostTest() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("<candidate.jar>");
        File candidate = new File(args[0]);
        require(candidate.isFile(), "candidate JAR exists");

        checkFreshCacheAndConfirmation(candidate);
        checkExcludedTemplate(candidate);
        checkExcludedStaticTemplates(candidate);
        checkLateExcludedTemplateCatalog(candidate);
        checkWorldIsolation(candidate);
        checkNativePacketReconciliation(candidate);
        checkContinuousRecovery(candidate);
        checkRecoveryMonitorBudget(candidate);
        checkMissingRecovery(candidate);
        checkRecoveryMismatchSuspension(candidate);
        checkRecoveryFailureRetry(candidate);
        checkRecoveryMismatchRetry(candidate);
        checkLateRecoveryDetail(candidate);
        checkInitialFailureRetry(candidate);
        checkLateAdoptedDetail(candidate);
        checkLongSessionRecovery(candidate);
        checkReadyGateAndMapIsolation(candidate);
        checkCorruptDataAndSaveFailure(candidate);
        checkRecordBound(candidate);
        checkQueuedCandidates(candidate);
        checkMultiZoneIdIsolation(candidate);
        checkLearnedZoneCapacityAndMemory(candidate);
        checkMap48Bypass(candidate);
        System.out.println("MAP48_CACHE_DISABLED_VERIFIED=true");

        System.out.println("HungBySonMonsterCacheHostTest PASS (" + checks + " checks)");
    }

    private static void checkFreshCacheAndConfirmation(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess access = new FakeAccess(store, "realm-a", 10, 3);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            access.now = 1000L;
            support.tick(access);
            require(access.restores.isEmpty(), "fresh startup restores nothing");
            require(access.requests.isEmpty(), "fresh startup requests nothing");

            support.onObject(access, 1, 5, 41, 12, 34, 5);
            support.onObject(access, 1, 7, 42, 50, 60, 7);
            support.onObject(access, 1, 8, 43, 70, 80, 8);
            support.onObject(access, 0, 10, 90, 1, 1, 1);
            support.onObject(access, 2, 11, 91, 2, 2, 2);
            support.onObject(access, 1, 12, 92, 3, 3, 5);
            FakeMonster ordinaryNative = new FakeMonster(92, 12, 3, 3, false);
            ordinaryNative.hp = 20; ordinaryNative.maxHp = 40;
            access.monsters.put(Integer.valueOf(92), ordinaryNative);
            support.onDetail(access, 92, 9, 3, 3, 20, 40);
            support.onObject(access, 1, 99, 92, 300, 300, 127);
            support.onObject(access, 1, 13, 93, 4, 4, 127);
            access.now = 1600L;
            support.tick(access);
            require(store.bytes != null && store.bytes.length > 0, "accepted packet data is persisted");
            require(access.saves > 0, "cache flush reaches bridge storage");
            support.status();
            support.paint(null);
        }

        FakeAccess restored = new FakeAccess(store, "realm-a", 10, 3);
        try (Support support = new Support(candidate)) {
            support.onMap(restored);
            support.tick(restored);
            require(restored.restores.isEmpty(), "cached records wait through the restore grace period");
            restored.now += 801L;
            support.tick(restored);
            require(restored.restores.size() == 4, "known packet-4 records restore incrementally");
            require(restored.monsters.containsKey(Integer.valueOf(41)), "accepted ID is restored");
            require(!restored.monsters.containsKey(Integer.valueOf(90))
                    && !restored.monsters.containsKey(Integer.valueOf(91))
                    && !restored.monsters.containsKey(Integer.valueOf(93)),
                    "type 0, type 2, and new state-127 IDs are not learned");
            FakeMonster keptThroughAnimation = restored.monsters.get(Integer.valueOf(92));
            require(keptThroughAnimation != null && keptThroughAnimation.template == 12
                    && keptThroughAnimation.x == 3 && keptThroughAnimation.y == 3,
                    "state 127 animation traffic preserves previously learned record data");

            FakeMonster confirmed = restored.monsters.get(Integer.valueOf(41));
            require(confirmed != null && confirmed.pending, "restored object is quarantined while pending");
            support.onDetail(restored, 999, 5, 12, 34, 22, 70);
            require(confirmed.pending, "detail for a different ID cannot confirm a placeholder");
            confirmed.hp = 22;
            confirmed.maxHp = 70;
            support.onDetail(restored, 41, 5, 12, 34, 22, 70);
            require(confirmed.hp == 22 && confirmed.maxHp == 70,
                    "native HP written before the detail hook remains authoritative");
            require(!confirmed.pending, "matching detail clears pending quarantine");

            FakeMonster wrongPosition = restored.monsters.get(Integer.valueOf(42));
            require(wrongPosition != null && wrongPosition.pending, "second cached object is pending");
            restored.now += 400L;
            support.tick(restored);
            support.onDetail(restored, 42, 7, 999, 999, 10, 10);
            require(wrongPosition.discarded && !restored.monsters.containsKey(Integer.valueOf(42)),
                    "wrong-position detail rejects and discards its owned placeholder");
            require(!restored.nativeDiscards, "mismatch cleanup never discards a native entity");

            long previousRequest = -1L;
            Map<Integer, Integer> requestCounts = new HashMap<Integer, Integer>();
            for (int i = 0; i < 7; i++) {
                restored.now += 400L;
                support.tick(restored);
            }
            require(!restored.requests.isEmpty(), "pending cache entries request authoritative data");
            for (Request request : restored.requests) {
                if (previousRequest >= 0L)
                    require(request.when - previousRequest >= 400L, "requests are globally paced at 400 ms");
                previousRequest = request.when;
                Integer id = Integer.valueOf(request.id);
                Integer count = requestCounts.get(id);
                requestCounts.put(id, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
            }
            for (Integer count : requestCounts.values())
                require(count.intValue() <= 2, "each pending ID receives at most two requests");

            FakeMonster timedOut = restored.monsters.get(Integer.valueOf(43));
            require(timedOut != null && timedOut.pending, "unconfirmed record remains pending before timeout");
            restored.now += 8001L;
            support.tick(restored);
            require(timedOut.discarded && !restored.monsters.containsKey(Integer.valueOf(43)),
                    "expired unobserved placeholder is discarded");
            for (FakeMonster entity : restored.discardedEntities)
                require(entity.placeholder, "discard is limited to cache-owned placeholders");
        }
    }

    private static void checkExcludedTemplate(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess access = new FakeAccess(store, "realm-excluded", 31, 0);
        access.templateNames.put(Integer.valueOf(666), "Xà nữ");
        access.templateNames.put(Integer.valueOf(667), "Xa nu");
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            support.onObject(access, 1, 666, 3100, 40, 50, 1);
            support.onObject(access, 1, 667, 3101, 42, 52, 1);
            access.now = 600L;
            support.tick(access);
            require(!hasPersistedRecord(store.bytes, 31, 3100, 666)
                    && hasPersistedRecord(store.bytes, 31, 3101, 667),
                    "new Xà nữ records are excluded while a different accented name is retained");
            require(!hasTemplate(support.remembered(access.server, 31), 666),
                    "excluded template never appears in remembered history");
        }

        FakeStore seeded = new FakeStore();
        seeded.bytes = excludedTemplateCacheBytes();
        FakeAccess loaded = new FakeAccess(seeded, "realm-excluded", 31, 0);
        loaded.templateNames.put(Integer.valueOf(666), "  XÀ NỮ  ");
        loaded.templateNames.put(Integer.valueOf(5), "Allowed Beast");
        loaded.templateNames.put(Integer.valueOf(667), "Xa nu");
        try (Support support = new Support(candidate)) {
            support.onMap(loaded);
            int[] firstMap = support.remembered(loaded.server, 31);
            require(!hasPair(firstMap, 666, 12) && hasPair(firstMap, 5, 15),
                    "load purges case-insensitive Xà nữ records before history is exposed");
            require(!hasPersistedRecord(seeded.bytes, 31, 3102, 666)
                    && hasPersistedRecord(seeded.bytes, 31, 3103, 5)
                    && !hasPersistedRecord(seeded.bytes, 32, 3202, 666)
                    && hasPersistedRecord(seeded.bytes, 32, 3203, 667)
                    && hasPersistedRecord(seeded.bytes, 33, 3302, 666),
                    "forced save removes this server's excluded records and retains another server's ID");
            loaded.now = 801L;
            support.tick(loaded);
            require(!containsMonsterId(loaded.restores, 3102)
                    && containsMonsterId(loaded.restores, 3103),
                    "purged Xà nữ records are never restored from an existing cache");

            FakeAccess nextMap = new FakeAccess(seeded, "realm-excluded", 32, 1);
            nextMap.templateNames.put(Integer.valueOf(666), "Xà nữ");
            nextMap.templateNames.put(Integer.valueOf(667), "Xa nu");
            support.onMap(nextMap);
            require(!hasPair(support.remembered(nextMap.server, 32), 666, 13)
                    && hasPair(support.remembered(nextMap.server, 32), 667, 14),
                    "accented exclusion leaves the visually similar unaccented name available");
            nextMap.now = 1602L;
            support.tick(nextMap);
            require(!containsMonsterId(nextMap.restores, 3202)
                    && containsMonsterId(nextMap.restores, 3203),
                    "historical Xà nữ IDs remain excluded after changing zones");

            support.onObject(nextMap, 1, 666, 3203, 44, 54, 1);
            require(!hasPair(support.remembered(nextMap.server, 32), 667, 14),
                    "an excluded template replacing an already cached ID removes the old record");
            require(!hasPersistedRecord(seeded.bytes, 32, 3203, 667),
                    "cached-ID replacement immediately rewrites the persisted record");
        }
    }

    private static void checkExcludedStaticTemplates(File candidate) throws Exception {
        FakeStore observedStore = new FakeStore();
        FakeAccess observed = new FakeAccess(observedStore, "realm-objects", 41, 0);
        observed.templateNames.put(Integer.valueOf(700), "  lỬA THIÊNG  ");
        observed.templateNames.put(Integer.valueOf(701), "  CON MA  ");
        observed.templateNames.put(Integer.valueOf(702), "Allowed Beast");
        try (Support support = new Support(candidate)) {
            support.onMap(observed);
            support.onObject(observed, 1, 700, 4100, 40, 50, 1);
            support.onObject(observed, 1, 701, 4101, 42, 52, 1);
            support.onObject(observed, 1, 702, 4102, 44, 54, 1);
            observed.now = 600L;
            support.tick(observed);
            require(!hasPersistedRecord(observedStore.bytes, 41, 4100, 700)
                    && !hasPersistedRecord(observedStore.bytes, 41, 4101, 701)
                    && hasPersistedRecord(observedStore.bytes, 41, 4102, 702),
                    "trimmed case-insensitive Lửa thiêng and con Ma observations are not saved");
            require(!hasTemplate(support.remembered(observed.server, 41), 700)
                    && !hasTemplate(support.remembered(observed.server, 41), 701),
                    "static object templates never appear in remembered history");
        }

        FakeStore oldStore = new FakeStore();
        oldStore.bytes = staticObjectTemplateCacheBytes();
        FakeAccess loaded = new FakeAccess(oldStore, "realm-objects", 41, 0);
        loaded.templateNames.put(Integer.valueOf(700), "  Lửa Thiêng  ");
        loaded.templateNames.put(Integer.valueOf(701), "  con ma  ");
        loaded.templateNames.put(Integer.valueOf(702), "Allowed Beast");
        try (Support support = new Support(candidate)) {
            support.onMap(loaded);
            require(!hasPair(support.remembered(loaded.server, 41), 700, 12)
                    && !hasPair(support.remembered(loaded.server, 41), 701, 13)
                    && hasPair(support.remembered(loaded.server, 41), 702, 14)
                    && !hasPair(support.remembered(loaded.server, 42), 700, 15)
                    && !hasPair(support.remembered(loaded.server, 42), 701, 16)
                    && hasPair(support.remembered(loaded.server, 42), 702, 17),
                    "loading old records purges both object templates across this server's maps");
            require(!hasPersistedRecord(oldStore.bytes, 41, 4102, 700)
                    && !hasPersistedRecord(oldStore.bytes, 41, 4103, 701)
                    && hasPersistedRecord(oldStore.bytes, 41, 4104, 702)
                    && !hasPersistedRecord(oldStore.bytes, 42, 4202, 700)
                    && !hasPersistedRecord(oldStore.bytes, 42, 4203, 701)
                    && hasPersistedRecord(oldStore.bytes, 42, 4204, 702)
                    && hasPersistedRecord(oldStore.bytes, 43, 4302, 700)
                    && hasPersistedRecord(oldStore.bytes, 43, 4303, 701),
                    "load cleanup saves only this server's exclusions and preserves other servers");
            loaded.now = 801L;
            support.tick(loaded);
            require(!containsMonsterId(loaded.restores, 4102)
                    && !containsMonsterId(loaded.restores, 4103)
                    && containsMonsterId(loaded.restores, 4104),
                    "loaded static object records are never restored");
        }

        FakeStore lateStore = new FakeStore();
        lateStore.bytes = staticObjectTemplateCacheBytes();
        FakeAccess late = new FakeAccess(lateStore, "realm-objects", 41, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(late);
            late.now = 801L;
            support.tick(late);
            FakeMonster cachedFire = late.monsters.get(Integer.valueOf(4102));
            FakeMonster cachedGhost = late.monsters.get(Integer.valueOf(4103));
            require(cachedFire != null && cachedFire.pending
                    && cachedGhost != null && cachedGhost.pending,
                    "unresolved old object templates initially restore as owned placeholders");

            late.templateNames.put(Integer.valueOf(700), " LỬA THIÊNG ");
            late.templateNames.put(Integer.valueOf(701), " CON MA ");
            late.now++;
            support.tick(late);
            require(cachedFire.discarded && cachedGhost.discarded
                    && !late.monsters.containsKey(Integer.valueOf(4102))
                    && !late.monsters.containsKey(Integer.valueOf(4103))
                    && late.monsters.containsKey(Integer.valueOf(4104)),
                    "late template resolution removes only cache-owned object placeholders");
            require(!hasPair(support.remembered(late.server, 41), 700, 12)
                    && !hasPair(support.remembered(late.server, 41), 701, 13)
                    && hasPair(support.remembered(late.server, 41), 702, 14),
                    "late object-template filtering removes history and retains ordinary monsters");
        }
    }

    private static byte[] staticObjectTemplateCacheBytes() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x4d435631);
        out.writeShort(1);
        out.writeShort(3);
        out.writeUTF("realm-objects");
        out.writeInt(41);
        out.writeInt(0);
        out.writeShort(3);
        out.writeShort(4102); out.writeShort(700); out.writeShort(40); out.writeShort(50); out.writeShort(12);
        out.writeShort(4103); out.writeShort(701); out.writeShort(42); out.writeShort(52); out.writeShort(13);
        out.writeShort(4104); out.writeShort(702); out.writeShort(44); out.writeShort(54); out.writeShort(14);
        out.writeUTF("realm-objects");
        out.writeInt(42);
        out.writeInt(1);
        out.writeShort(3);
        out.writeShort(4202); out.writeShort(700); out.writeShort(60); out.writeShort(70); out.writeShort(15);
        out.writeShort(4203); out.writeShort(701); out.writeShort(62); out.writeShort(72); out.writeShort(16);
        out.writeShort(4204); out.writeShort(702); out.writeShort(64); out.writeShort(74); out.writeShort(17);
        out.writeUTF("realm-other");
        out.writeInt(43);
        out.writeInt(2);
        out.writeShort(2);
        out.writeShort(4302); out.writeShort(700); out.writeShort(80); out.writeShort(90); out.writeShort(18);
        out.writeShort(4303); out.writeShort(701); out.writeShort(82); out.writeShort(92); out.writeShort(19);
        out.flush();
        return bytes.toByteArray();
    }

    private static void checkLateExcludedTemplateCatalog(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        store.bytes = excludedTemplateCacheBytes();
        FakeAccess access = new FakeAccess(store, "realm-excluded", 31, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            access.now = 400L;
            support.tick(access);
            require(access.restores.isEmpty(),
                    "late-catalog fixture waits before cached entities restore");

            // The first cache load could not resolve template 666. The catalog
            // becomes available while this same map session remains active.
            access.templateNames.put(Integer.valueOf(666), "Xà nữ");
            access.templateNames.put(Integer.valueOf(5), "Allowed Beast");
            access.templateNames.put(Integer.valueOf(667), "Xa nu");
            access.now = 801L;
            support.tick(access);

            require(!containsMonsterId(access.restores, 3102)
                    && containsMonsterId(access.restores, 3103),
                    "late-resolved Xà nữ is removed before restore while ordinary records survive");
            require(!hasPair(support.remembered(access.server, 31), 666, 12)
                    && hasPair(support.remembered(access.server, 31), 5, 15)
                    && !hasPair(support.remembered(access.server, 32), 666, 13)
                    && hasPair(support.remembered(access.server, 32), 667, 14)
                    && hasPair(support.remembered("realm-other", 33), 666, 16),
                    "late exclusion purges same-server history and preserves other-server IDs");
            require(!hasPersistedRecord(store.bytes, 31, 3102, 666)
                    && hasPersistedRecord(store.bytes, 31, 3103, 5)
                    && !hasPersistedRecord(store.bytes, 32, 3202, 666)
                    && hasPersistedRecord(store.bytes, 32, 3203, 667)
                    && hasPersistedRecord(store.bytes, 33, 3302, 666),
                    "late exclusion is flushed within its server without dropping unrelated records");
        }

        FakeStore restoredStore = new FakeStore();
        restoredStore.bytes = excludedTemplateCacheBytes();
        FakeAccess restored = new FakeAccess(restoredStore, "realm-excluded", 31, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(restored);
            restored.now = 801L;
            support.tick(restored);
            FakeMonster cachedXanu = restored.monsters.get(Integer.valueOf(3102));
            FakeMonster cachedAllowed = restored.monsters.get(Integer.valueOf(3103));
            require(cachedXanu != null && cachedXanu.pending && cachedAllowed != null,
                    "unknown names may initially leave owned placeholders pending");

            restored.templateNames.put(Integer.valueOf(666), "Xà nữ");
            restored.templateNames.put(Integer.valueOf(5), "Allowed Beast");
            restored.now++;
            support.tick(restored);
            require(cachedXanu.discarded
                    && !restored.monsters.containsKey(Integer.valueOf(3102))
                    && restored.monsters.get(Integer.valueOf(3103)) == cachedAllowed,
                    "bounded active filter safely discards only the owned excluded placeholder");
            require(!hasPair(support.remembered(restored.server, 31), 666, 12)
                    && hasPair(support.remembered(restored.server, 31), 5, 15),
                    "late active filter removes excluded history and keeps the allowed pair");
        }

        FakeStore detailStore = new FakeStore();
        detailStore.bytes = excludedTemplateCacheBytes();
        FakeAccess detail = new FakeAccess(detailStore, "realm-excluded", 31, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(detail);
            detail.now = 801L;
            support.tick(detail);
            FakeMonster cachedXanu = detail.monsters.get(Integer.valueOf(3102));
            require(cachedXanu != null && cachedXanu.pending,
                    "detail-guard fixture starts with a cache-owned pending placeholder");
            detail.templateNames.put(Integer.valueOf(666), "Xà nữ");
            detail.templateNames.put(Integer.valueOf(5), "Allowed Beast");
            cachedXanu.hp = 20;
            cachedXanu.maxHp = 50;
            support.onDetail(detail, 3102, 12, 40, 50, 20, 50);
            require(cachedXanu.discarded
                    && !detail.monsters.containsKey(Integer.valueOf(3102)),
                    "late-resolved excluded detail discards its owned placeholder before confirmation");
            require(!hasPair(support.remembered(detail.server, 31), 666, 12)
                    && hasPair(support.remembered(detail.server, 31), 5, 15),
                    "detail exclusion removes history without changing ordinary records");
        }
    }

    private static byte[] excludedTemplateCacheBytes() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x4d435631);
        out.writeShort(1);
        out.writeShort(3);
        out.writeUTF("realm-excluded");
        out.writeInt(31);
        out.writeInt(0);
        out.writeShort(2);
        out.writeShort(3102); out.writeShort(666); out.writeShort(40); out.writeShort(50); out.writeShort(12);
        out.writeShort(3103); out.writeShort(5); out.writeShort(42); out.writeShort(52); out.writeShort(15);
        out.writeUTF("realm-excluded");
        out.writeInt(32);
        out.writeInt(1);
        out.writeShort(2);
        out.writeShort(3202); out.writeShort(666); out.writeShort(60); out.writeShort(70); out.writeShort(13);
        out.writeShort(3203); out.writeShort(667); out.writeShort(62); out.writeShort(72); out.writeShort(14);
        out.writeUTF("realm-other");
        out.writeInt(33);
        out.writeInt(2);
        out.writeShort(1);
        out.writeShort(3302); out.writeShort(666); out.writeShort(64); out.writeShort(74); out.writeShort(16);
        out.flush();
        return bytes.toByteArray();
    }

    private static void checkWorldIsolation(File candidate) throws Exception {
        FakeStore original = new FakeStore();
        FakeAccess seed = new FakeAccess(original, "realm-isolated", 7, 4);
        try (Support support = new Support(candidate)) {
            support.onMap(seed);
            support.onObject(seed, 1, 23, 600, 14, 18, 2);
            seed.now = 600L;
            support.tick(seed);
        }
        require(original.bytes != null, "isolation fixture has persisted source data");

        FakeAccess[] mismatches = new FakeAccess[]{
            new FakeAccess(original.copy(), "realm-other", 7, 4),
            new FakeAccess(original.copy(), "realm-isolated", 8, 4),
            new FakeAccess(original.copy(), "realm-isolated", 7, 5)
        };
        String[] labels = new String[]{"server", "map", "zone"};
        for (int i = 0; i < mismatches.length; i++) {
            FakeAccess mismatch = mismatches[i];
            try (Support support = new Support(candidate)) {
                support.onMap(mismatch);
                mismatch.now = 801L;
                support.tick(mismatch);
                require(mismatch.restores.isEmpty(), "cached records are isolated by " + labels[i]);
            }
        }
    }

    private static void checkNativePacketReconciliation(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess seed = new FakeAccess(store, "realm-native", 12, 6);
        try (Support support = new Support(candidate)) {
            support.onMap(seed);
            support.onObject(seed, 1, 25, 700, 20, 30, 4);
            seed.now = 600L;
            support.tick(seed);
        }

        FakeAccess world = new FakeAccess(store, "realm-native", 12, 6);
        try (Support support = new Support(candidate)) {
            support.onMap(world);
            world.now = 801L;
            support.tick(world);
            require(world.restores.size() == 1, "cached ID is restored before native reconciliation");

            FakeMonster nativeEntity = new FakeMonster(700, 99, 55, 66, false);
            nativeEntity.hp = 31;
            nativeEntity.maxHp = 90;
            world.monsters.put(Integer.valueOf(700), nativeEntity);
            support.onObject(world, 1, 99, 700, 55, 66, 9);
            world.now += 500L;
            support.tick(world);
            require(world.monsters.get(Integer.valueOf(700)) == nativeEntity,
                    "authoritative packet-4 entity wins a cached-ID conflict");
            require(!nativeEntity.pending && !nativeEntity.discarded,
                    "reconciliation neither quarantines nor discards the native entity");
            require(!world.nativeDiscards, "cache cleanup never discards a native entity");
            require(world.restores.size() == 1, "reconciled native ID is not restored a second time");
        }
    }

    private static void checkContinuousRecovery(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess access = new FakeAccess(store, "realm-recovery-live", 13, 1);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            FakeMonster nativeEntity = confirmedNative(access, support, 900, 21, 100, 120, 15, 50, 80);
            require(hasPair(support.remembered(access.server, access.map), 21, 15),
                    "valid native packet-4 and detail learn template level");

            nativeEntity.hidden = true;
            require(support.hold(nativeEntity), "confirmed hidden native identity is held during recovery");
            access.now = 1000L;
            support.tick(access);
            require(access.requests.isEmpty(), "confirmed native IDs wait through the recovery grace");
            access.now = 2000L;
            support.tick(access);
            require(access.requests.isEmpty(), "1500 ms grace is honored across one-second monitor checks");
            access.now = 3000L;
            support.tick(access);
            require(access.requests.size() == 1 && access.requests.get(0).id == 900,
                    "confirmed hidden ID requests authoritative detail after grace");
            require(access.monsters.get(Integer.valueOf(900)) == nativeEntity
                    && access.restores.isEmpty() && !nativeEntity.discarded,
                    "recovery borrows the existing native object without replacing or discarding it");

            nativeEntity.hp = 0; nativeEntity.maxHp = 80; nativeEntity.dead = true; nativeEntity.hidden = true;
            support.onDetail(access, 900, 15, 100, 120, 0, 80);
            require(nativeEntity.hp == 0 && nativeEntity.dead && !nativeEntity.hidden,
                    "server HP zero leaves the borrowed entity as a visible non-targetable corpse");
            require(support.hold(nativeEntity), "confirmed hidden object stays held after an HP-zero response");
            access.now = 5999L;
            support.tick(access);
            require(access.requests.size() == 1 && nativeEntity.hp == 0,
                    "HP-zero response waits three seconds before querying again");
            access.now = 6000L;
            support.tick(access);
            require(access.requests.size() == 2 && access.requests.get(1).id == 900,
                    "an authoritative HP-zero response is queried again after three seconds");

            for (int expectedRequests = 3; expectedRequests <= 4; expectedRequests++) {
                access.now += 3000L;
                support.tick(access);
                require(access.requests.size() == expectedRequests,
                        "HP-zero reply continues its three-second recovery cycle " + expectedRequests);
                support.onDetail(access, 900, 15, 100, 120, 0, 80);
                require(nativeEntity.hp == 0 && nativeEntity.dead && !nativeEntity.hidden
                        && support.hold(nativeEntity),
                        "repeated HP-zero confirmation never promotes the borrowed object alive");
            }
            nativeEntity.hp = 37; nativeEntity.maxHp = 80;
            support.onDetail(access, 900, 15, 100, 120, 37, 80);
            require(!support.hold(nativeEntity) && !nativeEntity.dead && !nativeEntity.hidden,
                    "positive detail clears recovery hold without another packet-4 or approach");
            require(access.monsters.get(Integer.valueOf(900)) == nativeEntity
                    && access.restores.isEmpty() && !nativeEntity.discarded,
                    "positive detail revives the borrowed identity in place");
        }
    }

    private static void checkMissingRecovery(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess access = new FakeAccess(store, "realm-recovery-missing", 14, 1);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            FakeMonster nativeEntity = confirmedNative(access, support, 901, 22, 200, 220, 16, 30, 60);
            access.monsters.remove(Integer.valueOf(901));
            access.now = 1000L; support.tick(access);
            access.now = 2000L; support.tick(access);
            require(access.restores.isEmpty(), "missing confirmed ID waits through its recovery grace");
            access.now = 3000L; support.tick(access);
            FakeMonster restored = access.monsters.get(Integer.valueOf(901));
            require(restored != null && restored != nativeEntity && restored.placeholder,
                    "missing confirmed ID restores a new cache-owned placeholder");
            require(access.requests.size() == 1 && access.requests.get(0).id == 901,
                    "missing-ID placeholder requests authoritative detail");
            require(!nativeEntity.discarded && !access.nativeDiscards && support.hold(restored),
                    "missing-ID recovery preserves the former native object and holds only its placeholder");

            restored.hp = 29; restored.maxHp = 60; restored.dead = false; restored.hidden = false;
            support.onDetail(access, 901, 16, 200, 220, 29, 60);
            require(!support.hold(restored) && !restored.pending && !restored.discarded,
                    "matching positive detail confirms the restored missing ID");
        }
    }

    private static void checkRecoveryMonitorBudget(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess access = new FakeAccess(store, "realm-recovery-budget", 141, 1);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            FakeMonster[] entities = new FakeMonster[10];
            for (int i = 0; i < entities.length; i++) {
                entities[i] = confirmedNative(access, support, 920 + i, 25, 500 + i, 520 + i,
                        19, 40, 80);
                entities[i].hidden = true;
            }

            access.now = 1000L;
            int start = access.deadChecks.size();
            support.tick(access);
            require(access.deadChecks.size() - start == 8,
                    "recovery scans at most eight confirmed records per tick");
            access.now = 1500L;
            start = access.deadChecks.size();
            support.tick(access);
            require(access.deadChecks.size() - start == 2,
                    "rotating recovery scan advances to the remaining due records");
            access.now = 2000L;
            start = access.deadChecks.size();
            support.tick(access);
            require(access.deadChecks.size() - start <= 8,
                    "each subsequent monitor tick stays within its eight-record bound");
            assertDeadCheckIntervals(access.deadChecks);

            access.now = 3000L;
            start = access.deadChecks.size();
            support.tick(access);
            require(access.requests.size() == 1 && access.deadChecks.size() - start <= 8,
                    "one tick starts at most one recovery even with ten confirmed hidden IDs");
        }
    }

    private static void assertDeadCheckIntervals(List<DeadCheck> checks) {
        Map<Integer, Long> last = new HashMap<Integer, Long>();
        for (DeadCheck check : checks) {
            Integer id = Integer.valueOf(check.id);
            Long previous = last.get(id);
            if (previous != null)
                require(check.when - previous.longValue() >= 1000L,
                        "confirmed ID monitor checks are spaced by at least one second");
            last.put(id, Long.valueOf(check.when));
        }
    }

    private static void checkRecoveryFailureRetry(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess access = new FakeAccess(store, "realm-recovery-failures", 15, 1);
        access.failRestores = true;
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            FakeMonster nativeEntity = confirmedNative(access, support, 902, 23, 300, 320, 17, 35, 70);
            access.monsters.remove(Integer.valueOf(902));
            access.now = 1000L; support.tick(access);
            access.now = 3000L; support.tick(access);
            require(access.restoreAttempts == 1, "first failed recovery enters the first cooldown");
            for (access.now = 4000L; access.now < 23000L; access.now += 1000L) support.tick(access);
            access.now = 22999L; support.tick(access);
            require(access.restoreAttempts == 1, "first recovery failure waits twenty seconds");
            access.now = 23000L; support.tick(access);
            require(access.restoreAttempts == 2, "second failed recovery enters the second cooldown");
            for (access.now = 24000L; access.now < 63000L; access.now += 1000L) support.tick(access);
            access.now = 62999L; support.tick(access);
            require(access.restoreAttempts == 2, "second recovery failure waits forty seconds");
            access.now = 63000L; support.tick(access);
            require(access.restoreAttempts == 3, "third failed recovery enters the maximum cooldown");
            for (access.now = 64000L; access.now < 123000L; access.now += 1000L) support.tick(access);
            require(access.restoreAttempts == 3 && access.requests.isEmpty(),
                    "three consecutive failures pause for sixty seconds without flooding requests");
            access.failRestores = false;
            access.now = 123000L; support.tick(access);
            FakeMonster replacement = access.monsters.get(Integer.valueOf(902));
            require(replacement != null && access.restoreAttempts == 4 && access.requests.size() == 1,
                    "same-map recovery retries after three failures without another packet-4");
            replacement.hp = 35; replacement.maxHp = 70;
            support.onDetail(access, 902, 17, 300, 320, 35, 70);
            require(!replacement.pending && !replacement.discarded && !nativeEntity.discarded,
                    "matching detail recovers the missing ID in place without a map reset");
        }
    }

    private static void checkRecoveryMismatchSuspension(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess access = new FakeAccess(store, "realm-recovery-mismatch", 16, 1);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            FakeMonster nativeEntity = confirmedNative(access, support, 903, 24, 400, 420, 18, 45, 90);
            nativeEntity.hidden = true;
            access.now = 1000L; support.tick(access);
            access.now = 2000L; support.tick(access);
            access.now = 3000L; support.tick(access);
            require(access.requests.size() == 1 && support.hold(nativeEntity),
                    "mismatch fixture starts from a confirmed held recovery request");

            nativeEntity.hp = 45; nativeEntity.maxHp = 90;
            support.onDetail(access, 903, 18, 900, 920, 45, 90);
            access.now = 4000L;
            for (int i = 0; i < 12; i++, access.now += 1000L) support.tick(access);
            require(access.requests.size() == 1 && !nativeEntity.discarded,
                    "mismatched recovery detail suspends requests without discarding borrowed identity");

            nativeEntity.hp = 45; nativeEntity.dead = false; nativeEntity.hidden = true;
            access.now = 16000L;
            support.onObject(access, 1, 24, 903, 400, 420, 1);
            support.onDetail(access, 903, 18, 400, 420, 45, 90);
            require(!support.hold(nativeEntity) && !nativeEntity.hidden,
                    "authoritative packet-4 and positive detail clear mismatch suspension");
            nativeEntity.hidden = true;
            require(support.hold(nativeEntity), "revalidated hidden identity enters a new recovery cycle");
            access.now = 17000L; support.tick(access);
            access.now = 18000L; support.tick(access);
            access.now = 19000L; support.tick(access);
            require(access.requests.size() == 2,
                    "recovery may request again after a matching native packet-4 revalidates identity");
        }
    }

    private static FakeMonster confirmedNative(FakeAccess access, Support support, int id,
            int template, int x, int y, int level, int hp, int maxHp) throws Exception {
        FakeMonster nativeEntity = new FakeMonster(id, template, x, y, false);
        nativeEntity.hp = hp;
        nativeEntity.maxHp = maxHp;
        access.monsters.put(Integer.valueOf(id), nativeEntity);
        support.onObject(access, 1, template, id, x, y, 1);
        support.onDetail(access, id, level, x, y, hp, maxHp);
        return nativeEntity;
    }

    private static void checkRecoveryMismatchRetry(File candidate) throws Exception {
        FakeAccess access = new FakeAccess(new FakeStore(), "realm-mismatch-retry", 38, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            FakeMonster entity = confirmedNative(access, support, 1800, 24, 400, 420, 18, 45, 90);
            entity.hidden = true;
            access.now = 1000L; support.tick(access);
            access.now = 3000L; support.tick(access);
            entity.hp = 45;
            support.onDetail(access, 1800, 18, 900, 920, 45, 90);
            require(entity.pending && entity.hp == 0 && !entity.discarded,
                    "invalid borrowed detail remains quarantined without trusting native HP writes");
            require(support.statusText().contains("W:0"),
                    "mismatch ends the pending cycle instead of retaining a terminal slot");
            for (access.now = 4000L; access.now < 23000L; access.now += 1000L) support.tick(access);
            require(access.requests.size() == 1, "mismatch retry obeys its twenty-second cooldown");
            access.now = 23000L; support.tick(access);
            require(access.requests.size() == 2, "mismatch retries in the same map without packet-4");
            entity.hp = 37; entity.maxHp = 90;
            support.onDetail(access, 1800, 18, 400, 420, 37, 90);
            require(!entity.hidden && !entity.dead && !entity.pending && !support.hold(entity),
                    "matching delayed detail restores borrowed identity and clears quarantine");
            require(access.monsters.get(Integer.valueOf(1800)) == entity && !access.nativeDiscards,
                    "mismatch retry never replaces or deletes the borrowed native object");
        }
    }

    private static void checkInitialFailureRetry(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        persistCacheRecord(candidate, store, "realm-initial-retry", 39, 0, 25, 1810, 600, 620, 20);
        FakeAccess access = new FakeAccess(store, "realm-initial-retry", 39, 0);
        access.failRestores = true;
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            support.tick(access);
            access.now = 800L; support.tick(access);
            access.now = 1600L; support.tick(access);
            require(access.restoreAttempts == 2 && access.requests.isEmpty(),
                    "initial factory failure is bounded before entering a cooldown");
            access.failRestores = false;
            access.now = 21600L; support.tick(access);
            FakeMonster restored = access.monsters.get(Integer.valueOf(1810));
            require(restored != null && access.requests.size() == 1,
                    "initial factory failure retries without leaving the map");
            // A native packet-4 adopts the placeholder, then both detail replies are lost.
            support.onObject(access, 1, 25, 1810, 600, 620, 1);
            access.now = 22000L; support.tick(access);
            access.now = 29600L; support.tick(access);
            require(!restored.discarded && support.statusText().contains("W:0"),
                    "observed placeholder timeout releases its slot while preserving native adoption");
            access.now = 49600L; support.tick(access);
            require(access.requests.size() == 3, "observed timeout also retries without packet-4");
            restored.hp = 29; restored.maxHp = 60;
            support.onDetail(access, 1810, 20, 600, 620, 29, 60);
            require(!restored.pending && !restored.dead && !restored.discarded,
                    "initial timeout can later accept authoritative HP in the same session");

            restored.hidden = true;
            access.now = 50600L; support.tick(access);
            access.throwOnRequest = true;
            access.now = 52600L; support.tick(access);
            require(support.statusText().contains("W:0"), "send exception does not leak a pending slot");
            access.throwOnRequest = false;
            access.now = 72600L; support.tick(access);
            restored.hp = 27;
            support.onDetail(access, 1810, 20, 600, 620, 27, 60);
            require(!restored.hidden && !restored.pending,
                    "send exception recovery preserves identity and resumes after cooldown");
        }
    }

    private static void checkLateRecoveryDetail(File candidate) throws Exception {
        FakeAccess access = new FakeAccess(new FakeStore(), "realm-late-detail", 38, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            FakeMonster entity = confirmedNative(access, support, 1820, 24, 400, 420, 18, 45, 90);
            entity.hidden = true;
            access.now = 1000L; support.tick(access);
            access.now = 3000L; support.tick(access);
            access.now = 3400L; support.tick(access);
            access.now = 11000L; support.tick(access);
            require(support.statusText().contains("W:0"), "late reply begins after timeout retirement");
            // A delayed, valid reply is newer evidence than the earlier request failure.
            access.now = 12000L;
            entity.hp = 0;
            support.onDetail(access, 1820, 18, 400, 420, 0, 90);
            access.now = 14999L; support.tick(access);
            require(access.requests.size() == 2, "late HP-zero detail waits for its normal dead-check interval");
            access.now = 15000L; support.tick(access);
            require(access.requests.size() == 3,
                    "late HP-zero detail clears failure cooldown and resumes the three-second cycle");
            entity.hp = 37;
            support.onDetail(access, 1820, 18, 400, 420, 37, 90);
            require(!entity.hidden && !entity.pending, "late reply recovery accepts only authoritative positive HP");
        }
    }

    private static void checkLateAdoptedDetail(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        persistCacheRecord(candidate, store, "realm-late-adopted", 38, 0, 25, 1830, 600, 620, 20);
        FakeAccess access = new FakeAccess(store, "realm-late-adopted", 38, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            support.tick(access);
            access.now = 800L; support.tick(access);
            FakeMonster entity = access.monsters.get(Integer.valueOf(1830));
            support.onObject(access, 1, 25, 1830, 600, 620, 1);
            access.now = 1200L; support.tick(access);
            access.now = 8800L; support.tick(access);
            require(!entity.discarded && support.statusText().contains("W:0"),
                    "unconfirmed adopted identity survives timeout retirement");
            access.now = 9000L;
            entity.hp = 37; entity.maxHp = 60;
            support.onDetail(access, 1830, 20, 600, 620, 37, 60);
            require(!entity.pending && !entity.dead && !entity.hidden,
                    "late positive detail immediately clears adopted quarantine without another request");
            require(access.requests.size() == 2 && !access.nativeDiscards,
                    "late adopted confirmation neither resends nor deletes its identity");
        }
    }

    private static void checkLongSessionRecovery(File candidate) throws Exception {
        FakeAccess access = new FakeAccess(new FakeStore(), "realm-long-session", 38, 0);
        final int count = 41;
        final long duration = 4L * 60L * 60L * 1000L;
        int[] positiveReplies = new int[count];
        long[] lastPositiveAt = new long[count];
        int received = 0;
        int lost = 0;
        int mismatched = 0;
        boolean paced = true;
        long lastRequest = -400L;
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            for (int i = 0; i < count; i++)
                confirmedNative(access, support, 2000 + i, 31, 100 + i * 4, 200, 25, 43, 100);
            // Four simulated hours in one map, with recurring remote death/removal and
            // a twenty-minute outage affecting two thirds of the known IDs.
            for (access.now = 1000L; access.now <= duration; access.now += 100L) {
                if ((access.now - 1000L) % 60000L == 0 && access.now < duration - 120000L) {
                    for (int i = 0; i < count; i++) {
                        FakeMonster entity = access.monsters.get(Integer.valueOf(2000 + i));
                        if (entity == null) continue;
                        entity.hp = 0; entity.dead = true; entity.hidden = true;
                        if (i % 4 == 0) access.monsters.remove(Integer.valueOf(entity.id));
                    }
                }
                support.tick(access);
                while (received < access.requests.size()) {
                    Request request = access.requests.get(received++);
                    paced &= request.when - lastRequest >= 400L;
                    lastRequest = request.when;
                    int i = request.id - 2000;
                    boolean outage = access.now >= 600000L && access.now < 1800000L;
                    if (outage && i % 3 == 0) { lost++; continue; }
                    FakeMonster entity = access.monsters.get(Integer.valueOf(request.id));
                    if (entity == null) throw new AssertionError("detail request has no live candidate");
                    entity.hp = 43; entity.maxHp = 100;
                    if (outage && i % 3 == 1) {
                        mismatched++;
                        support.onDetail(access, request.id, 25, 1500, 1500, 43, 100);
                    } else {
                        support.onDetail(access, request.id, 25, 100 + i * 4, 200, 43, 100);
                        positiveReplies[i]++;
                        lastPositiveAt[i] = access.now;
                    }
                }
            }
            require(lost > 50 && mismatched > 50, "long session includes repeated loss and identity rejection");
            require(paced, "four-hour recovery retains the global 400ms request limit");
            for (int i = 0; i < count; i++) {
                FakeMonster entity = access.monsters.get(Integer.valueOf(2000 + i));
                require(entity != null && entity.hp == 43 && !entity.hidden && !entity.dead && !entity.pending,
                        "long session restores every far-away ID after outage " + i);
                require(positiveReplies[i] > 100 && lastPositiveAt[i] > duration - 240000L,
                        "ID remains recoverable hours after its initial confirmation " + i);
            }
            require(support.statusText().contains("W:0") && !access.nativeDiscards,
                    "long session drains pending slots without discarding native identities");
        }
    }

    private static void checkReadyGateAndMapIsolation(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        persistCacheRecord(candidate, store, "realm-ready", 17, 0, 26, 904, 600, 620, 20);
        FakeAccess access = new FakeAccess(store, "realm-ready", 17, 0);
        access.isReady = false;
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            access.now = 1000L;
            support.tick(access);
            require(access.restores.isEmpty() && access.requests.isEmpty(),
                    "unready native world cannot restore or request cached entities");

            access.isReady = true;
            support.tick(access);
            require(access.restores.isEmpty(), "restore grace starts only after readiness returns");
            access.now = 1800L;
            support.tick(access);
            require(access.restores.size() == 1 && access.restores.get(0).id == 904,
                    "same-map cache restores after the ready-world grace period");

            access.isReady = false;
            access.map = 18;
            support.onMap(access);
            access.now = 3000L;
            support.tick(access);
            access.isReady = true;
            support.tick(access);
            access.now = 3801L;
            support.tick(access);
            require(access.restores.size() == 1,
                    "readying a new map never restores the previous map's cached record");
        }
    }

    private static void checkCorruptDataAndSaveFailure(File candidate) throws Exception {
        FakeStore corrupt = new FakeStore();
        corrupt.bytes = new byte[]{1, 2, 3, 4, 5};
        FakeAccess malformed = new FakeAccess(corrupt, "realm-corrupt", 3, 1);
        try (Support support = new Support(candidate)) {
            support.onMap(malformed);
            malformed.now = 1000L;
            support.tick(malformed);
            require(malformed.restores.isEmpty(), "truncated cache data is ignored safely");
        }

        FakeStore failing = new FakeStore();
        FakeAccess access = new FakeAccess(failing, "realm-save-failure", 3, 1);
        access.throwOnSave = true;
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            support.onObject(access, 1, 17, 811, 40, 50, 3);
            access.now = 600L;
            support.tick(access);
            require(access.saves > 0, "failed storage is attempted through the bridge");
            require(access.monsters.isEmpty(), "storage failure does not fabricate world entities");
        }
    }

    private static void checkRecordBound(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess seed = new FakeAccess(store, "realm-bound", 19, 2);
        try (Support support = new Support(candidate)) {
            support.onMap(seed);
            for (int i = 0; i < 300; i++)
                support.onObject(seed, 1, 1 + (i % 20), 1000 + i, i, i + 1, 1);
            seed.now = 1000L;
            support.tick(seed);
        }

        FakeAccess loaded = new FakeAccess(store, "realm-bound", 19, 2);
        try (Support support = new Support(candidate)) {
            support.onMap(loaded);
            loaded.now = 801L;
            for (int i = 0; i < 32; i++) {
                support.tick(loaded);
                loaded.now += 100L;
            }
            require(loaded.restores.size() <= 256, "persisted records stay within the 256-entry context bound");
        }
    }

    private static void checkQueuedCandidates(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        FakeAccess seed = new FakeAccess(store, "realm-queue", 20, 2);
        try (Support support = new Support(candidate)) {
            support.onMap(seed);
            for (int id = 100; id < 124; id++) support.onObject(seed, 1, 7, id, 10, 10, -1);
            seed.now = 600L; support.tick(seed);
        }
        FakeAccess access = new FakeAccess(store, "realm-queue", 20, 2);
        try (Support support = new Support(candidate)) {
            support.onMap(access); access.now = 801L;
            for (int i = 0; i < 6; i++) { support.tick(access); access.now++; }
            require(access.restores.size() == 24, "large queues restore incrementally");
            FakeMonster queued = access.monsters.get(Integer.valueOf(123));
            require(support.hold(queued), "active queued placeholder holds native death and respawn hooks");
            queued.hp = 37; queued.maxHp = 50;
            support.onDetail(access, 123, 7, 10, 10, 37, 50);
            require(queued.pending && !queued.discarded, "unsolicited detail leaves an unrequested candidate quarantined");
            access.now = 9000L; support.tick(access);
            require(!queued.discarded && support.hold(queued),
                    "queued hold persists beyond eight seconds until the first request");
            boolean queried = false;
            for (int i = 0; i < 60 && !queried; i++) {
                access.now += 400L; support.tick(access);
                for (Request request : access.requests) if (request.id == 123) queried = true;
            }
            require(queried && !queued.discarded, "the last queued candidate gets its bounded request");
            queued.hp = 0; queued.maxHp = 50; queued.dead = true; queued.hidden = true;
            support.onDetail(access, 123, 7, 10, 10, 0, 50);
            require(queued.pending && queued.hp == 0 && support.hold(queued),
                    "server HP zero confirms identity without promoting it alive");
            require(queued.dead && !queued.hidden,
                    "server-confirmed death remains visible and blocked from auto");
            queued.hp = 37; queued.maxHp = 50; queued.dead = false; queued.hidden = false;
            support.onDetail(access, 123, 7, 10, 10, 37, 50);
            require(!queued.pending && !queued.discarded, "requested matching detail confirms the queued candidate");
            require(!support.hold(queued), "confirmation releases the placeholder hold");
        }
    }

    private static void checkMultiZoneIdIsolation(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        persistCacheRecord(candidate, store, "realm-zones", 27, 0, 31, 55, 12, 22, 10);
        persistCacheRecord(candidate, store, "realm-zones", 27, 1, 42, 55, 90, 100, 11);

        FakeAccess zone0 = new FakeAccess(store, "realm-zones", 27, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(zone0);
            zone0.now += 801L;
            support.tick(zone0);
            require(zone0.restores.size() == 1, "zone 0 restores only its saved instance");
            FakeMonster restored = zone0.restores.get(0);
            require(restored.id == 55 && restored.template == 31
                    && restored.x == 12 && restored.y == 22,
                    "same ID retains zone 0 template and coordinates after restart");
        }

        FakeAccess zone1 = new FakeAccess(store, "realm-zones", 27, 1);
        try (Support support = new Support(candidate)) {
            support.onMap(zone1);
            zone1.now += 801L;
            support.tick(zone1);
            require(zone1.restores.size() == 1, "zone 1 restores only its saved instance");
            FakeMonster restored = zone1.restores.get(0);
            require(restored.id == 55 && restored.template == 42
                    && restored.x == 90 && restored.y == 100,
                    "same ID retains zone 1 template and coordinates after restart");
        }
    }

    private static void checkLearnedZoneCapacityAndMemory(File candidate) throws Exception {
        FakeStore store = new FakeStore();
        final String server = "realm-40-zones";
        final int map = 38;
        for (int zone = 0; zone < 40; zone++) {
            int template = 1000 + zone;
            int level = 10 + zone;
            persistCacheRecord(candidate, store, server, map, zone,
                    template, 777, zone * 3, zone * 5, level);
        }
        // These records share only one scope component with the 40-zone set.
        persistCacheRecord(candidate, store, server, map + 1, 0, 3001, 778, 4, 5, 70);
        persistCacheRecord(candidate, store, "realm-other", map, 0, 4002, 779, 6, 7, 71);

        FakeAccess restarted = new FakeAccess(store, server, map, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(restarted);
            int[] remembered = support.remembered(server, map);
            require(remembered.length == 80,
                    "remembered history aggregates 40 learned zones for the same server and map");
            require(hasPair(remembered, 1000, 10) && hasPair(remembered, 1039, 49),
                    "remembered history retains pairs from the first and fortieth zones");
            require(!hasPair(remembered, 3001, 70) && !hasPair(remembered, 4002, 71),
                    "remembered history excludes other maps and servers");

            restarted.now += 801L;
            support.tick(restarted);
            require(restarted.restores.size() == 1,
                    "the first of 40 learned zones remains present after a fresh class loader");
            FakeMonster restored = restarted.restores.get(0);
            require(restored.id == 777 && restored.template == 1000
                    && restored.x == 0 && restored.y == 0,
                    "restarted first zone restores its own record after exceeding the old 32-context cap");
        }
    }

    private static void checkMap48Bypass(File candidate) throws Exception {
        final String server = "realm-map48";
        FakeStore store = new FakeStore();
        store.bytes = legacyCacheBytes(server);
        byte[] original = store.bytes.clone();

        FakeAccess excluded = new FakeAccess(store, server, 48, 2);
        FakeMonster nativeMonster = new FakeMonster(4802, 48, 80, 90, false);
        nativeMonster.hp = 57;
        nativeMonster.maxHp = 80;
        excluded.monsters.put(Integer.valueOf(nativeMonster.id), nativeMonster);
        try (Support support = new Support(candidate)) {
            support.onMap(excluded);
            require(excluded.loads == 0,
                    "map 48 does not load old monster-cache records from RMS");
            require(support.remembered(server, 48).length == 0,
                    "map 48 never exposes remembered monster history");
            support.onObject(excluded, 1, 48, 4802, 80, 90, 5);
            support.onDetail(excluded, 4802, 12, 80, 90, 57, 80);
            excluded.now = 3000L;
            support.tick(excluded);
            support.flushNow(excluded);
            require(excluded.saves == 0,
                    "map 48 never writes the shared monster-cache RMS store");
            require(excluded.restores.isEmpty() && excluded.requests.isEmpty(),
                    "map 48 neither restores cached monsters nor requests cache verification");
            require(excluded.monsters.get(Integer.valueOf(4802)) == nativeMonster
                    && nativeMonster.hp == 57 && !nativeMonster.pending
                    && !nativeMonster.discarded,
                    "native map-48 monsters retain their identity and server HP");
        }
        require(java.util.Arrays.equals(original, store.bytes),
                "map 48 leaves preexisting RMS bytes untouched");

        FakeAccess ordinary = new FakeAccess(store, server, 49, 3);
        try (Support support = new Support(candidate)) {
            support.onMap(ordinary);
            require(ordinary.loads == 1 && ordinary.saves > 0,
                    "another map still loads and saves the shared cache");
            require(!hasPersistedMap(store.bytes, 48) && hasPersistedMap(store.bytes, 49)
                    && hasPersistedMap(store.bytes, 50),
                    "the next permitted save removes legacy map-48 contexts and retains other maps");
            require(support.remembered(server, 48).length == 0
                    && hasPair(support.remembered(server, 49), 49, 20),
                    "legacy map-48 history is dropped while other-map history remains available");
            ordinary.now = 801L;
            support.tick(ordinary);
            require(ordinary.restores.size() == 1
                    && ordinary.restores.get(0).id == 4901
                    && ordinary.restores.get(0).template == 49,
                    "map 49 restores its own legacy record without reusing map-48 data");
        }

        FakeStore transitionStore = new FakeStore();
        transitionStore.bytes = legacyCacheBytes(server);
        FakeAccess map49 = new FakeAccess(transitionStore, server, 49, 3);
        try (Support support = new Support(candidate)) {
            support.onMap(map49);
            require(map49.loads == 1 && map49.saves == 1,
                    "the same-process transition fixture loads and sanitizes allowed contexts first");
            support.onObject(map49, 1, 55, 4902, 60, 61, 2);
            map49.now = 600L;
            map49.throwOnSave = true;
            support.tick(map49);
            require(map49.saves == 2,
                    "a failed map-49 save leaves learned records dirty for retry");
            byte[] beforeExcludedMap = transitionStore.bytes.clone();

            FakeAccess transition48 = new FakeAccess(transitionStore, server, 48, 1);
            support.onMap(transition48);
            transition48.now = 5000L;
            support.flushNow(transition48);
            support.tick(transition48);
            require(transition48.loads == 0 && transition48.saves == 0
                    && map49.saves == 2,
                    "map-48 transition, forced flush, and retry tick issue no cache RMS operation");
            require(java.util.Arrays.equals(beforeExcludedMap, transitionStore.bytes),
                    "a dirty map-49 retry cannot change persisted bytes while on map 48");

            FakeAccess resumed49 = new FakeAccess(transitionStore, server, 49, 3);
            support.onMap(resumed49);
            require(resumed49.loads == 0 && resumed49.saves == 1,
                    "returning from map 48 resumes the same in-memory cache and persists dirty records");
            require(!hasPersistedMap(transitionStore.bytes, 48)
                    && hasPersistedRecord(transitionStore.bytes, 49, 4901, 49)
                    && hasPersistedRecord(transitionStore.bytes, 49, 4902, 55)
                    && hasPersistedRecord(transitionStore.bytes, 50, 5001, 50),
                    "the resumed save keeps map-49 changes and the preexisting map-50 context");
            resumed49.now = 801L;
            support.tick(resumed49);
            require(containsMonsterId(resumed49.restores, 4901)
                    && containsMonsterId(resumed49.restores, 4902),
                    "map 49 resumes restoration of existing and newly learned allowed records");

            FakeAccess resumed50 = new FakeAccess(transitionStore, server, 50, 4);
            support.onMap(resumed50);
            resumed50.now = 801L;
            support.tick(resumed50);
            require(containsMonsterId(resumed50.restores, 5001),
                    "another allowed context remains restorable after the map-48 transition");
        }
    }

    private static byte[] legacyCacheBytes(String server) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x4d435631);
        out.writeShort(1);
        out.writeShort(3);
        out.writeUTF(server);
        out.writeInt(48);
        out.writeInt(2);
        out.writeShort(1);
        out.writeShort(4801); out.writeShort(48); out.writeShort(20); out.writeShort(30); out.writeShort(18);
        out.writeUTF(server);
        out.writeInt(49);
        out.writeInt(3);
        out.writeShort(1);
        out.writeShort(4901); out.writeShort(49); out.writeShort(40); out.writeShort(50); out.writeShort(20);
        out.writeUTF(server);
        out.writeInt(50);
        out.writeInt(4);
        out.writeShort(1);
        out.writeShort(5001); out.writeShort(50); out.writeShort(70); out.writeShort(80); out.writeShort(25);
        out.flush();
        return bytes.toByteArray();
    }

    private static boolean hasPersistedMap(byte[] data, int expectedMap) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        if (in.readInt() != 0x4d435631 || in.readUnsignedShort() != 1) return false;
        int contexts = in.readUnsignedShort();
        for (int i = 0; i < contexts; i++) {
            in.readUTF();
            int map = in.readInt();
            in.readInt();
            int records = in.readUnsignedShort();
            for (int j = 0; j < records; j++) {
                in.readUnsignedShort(); in.readUnsignedShort(); in.readShort();
                in.readShort(); in.readUnsignedShort();
            }
            if (map == expectedMap) return true;
        }
        return false;
    }

    private static boolean hasPersistedRecord(byte[] data, int expectedMap,
            int expectedId, int expectedTemplate) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        if (in.readInt() != 0x4d435631 || in.readUnsignedShort() != 1) return false;
        int contexts = in.readUnsignedShort();
        for (int i = 0; i < contexts; i++) {
            in.readUTF();
            int map = in.readInt();
            in.readInt();
            int records = in.readUnsignedShort();
            for (int j = 0; j < records; j++) {
                int id = in.readUnsignedShort();
                int template = in.readUnsignedShort();
                in.readShort(); in.readShort(); in.readUnsignedShort();
                if (map == expectedMap && id == expectedId && template == expectedTemplate) return true;
            }
        }
        return false;
    }

    private static boolean containsMonsterId(List<FakeMonster> monsters, int id) {
        for (FakeMonster monster : monsters) if (monster.id == id) return true;
        return false;
    }

    private static void persistCacheRecord(File candidate, FakeStore store, String server,
            int map, int zone, int template, int id, int x, int y, int level) throws Exception {
        FakeAccess access = new FakeAccess(store, server, map, zone);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            support.onObject(access, 1, template, id, x, y, 1);
            FakeMonster nativeEntity = new FakeMonster(id, template, x, y, false);
            nativeEntity.hp = 20; nativeEntity.maxHp = 50;
            access.monsters.put(Integer.valueOf(id), nativeEntity);
            support.onDetail(access, id, level, x, y, 20, 50);
            access.now = 600L;
            support.tick(access);
        }
    }

    private static boolean hasPair(int[] pairs, int template, int level) {
        if (pairs == null) return false;
        for (int i = 0; i + 1 < pairs.length; i += 2)
            if (pairs[i] == template && pairs[i + 1] == level) return true;
        return false;
    }

    private static boolean hasTemplate(int[] pairs, int template) {
        if (pairs == null) return false;
        for (int i = 0; i + 1 < pairs.length; i += 2)
            if (pairs[i] == template) return true;
        return false;
    }

    private static final class Support implements AutoCloseable {
        private static final Class<?>[] ACCESS_ARG = new Class<?>[]{MonsterCacheAccess.class};
        private static final Class<?>[] SIX_INTS = new Class<?>[]{int.class, int.class, int.class,
                int.class, int.class, int.class};

        private final ChildFirstLoader loader;
        private final Method onMap;
        private final Method onObject;
        private final Method onDetail;
        private final Method tick;
        private final Method flushNow;
        private final Method hold;
        private final Method paint;
        private final Method status;
        private final Method remembered;

        Support(File candidate) throws Exception {
            loader = new ChildFirstLoader(candidate);
            Class<?> type = loader.loadClass("luna.features.MonsterCacheSupport");
            onMap = type.getMethod("onMap", ACCESS_ARG);
            onObject = type.getMethod("onObject", prependAccess());
            onDetail = type.getMethod("onDetail", SIX_INTS_WITH_ACCESS());
            tick = type.getMethod("tick", ACCESS_ARG);
            flushNow = type.getMethod("flushNow", ACCESS_ARG);
            hold = type.getMethod("hold", Object.class);
            paint = type.getMethod("paint", Object.class);
            status = type.getMethod("status");
            remembered = type.getMethod("remembered", String.class, int.class);
        }

        void onMap(MonsterCacheAccess access) throws Exception { invoke(onMap, access); }

        void onObject(MonsterCacheAccess access, int type, int template, int id,
                int x, int y, int state) throws Exception {
            invoke(onObject, access, Integer.valueOf(type), Integer.valueOf(template), Integer.valueOf(id),
                    Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(state));
        }

        void onDetail(MonsterCacheAccess access, int id, int level,
                int x, int y, int hp, int maxHp) throws Exception {
            invoke(onDetail, access, Integer.valueOf(id), Integer.valueOf(level), Integer.valueOf(x),
                    Integer.valueOf(y), Integer.valueOf(hp), Integer.valueOf(maxHp));
        }

        void tick(MonsterCacheAccess access) throws Exception { invoke(tick, access); }
        void flushNow(MonsterCacheAccess access) throws Exception { invoke(flushNow, access); }

        boolean hold(Object object) throws Exception {
            Object value = hold.invoke(null, object);
            if (!(value instanceof Boolean)) throw new AssertionError("hold() did not return boolean");
            return ((Boolean)value).booleanValue();
        }

        void paint(Object graphics) throws Exception { invoke(paint, graphics); }

        void status() throws Exception {
            Object value = status.invoke(null);
            require(value == null || value instanceof String, "status hook returns text safely");
        }

        String statusText() throws Exception { return (String)status.invoke(null); }

        int[] remembered(String server, int map) throws Exception {
            Object value = remembered.invoke(null, server, Integer.valueOf(map));
            if (!(value instanceof int[])) throw new AssertionError("remembered() did not return pairs");
            return (int[])value;
        }

        private static Class<?>[] prependAccess() {
            Class<?>[] result = new Class<?>[SIX_INTS.length + 1];
            result[0] = MonsterCacheAccess.class;
            System.arraycopy(SIX_INTS, 0, result, 1, SIX_INTS.length);
            return result;
        }

        private static Class<?>[] SIX_INTS_WITH_ACCESS() {
            return prependAccess();
        }

        private static void invoke(Method method, MonsterCacheAccess access, Object... rest) throws Exception {
            Object[] args = new Object[rest.length + 1];
            args[0] = access;
            System.arraycopy(rest, 0, args, 1, rest.length);
            try {
                method.invoke(null, args);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof Exception) throw (Exception)cause;
                if (cause instanceof Error) throw (Error)cause;
                throw new RuntimeException(cause);
            }
        }

        private static void invoke(Method method, Object... args) throws Exception {
            try {
                method.invoke(null, args);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof Exception) throw (Exception)cause;
                if (cause instanceof Error) throw (Error)cause;
                throw new RuntimeException(cause);
            }
        }

        public void close() throws Exception { loader.close(); }
    }

    private static final class ChildFirstLoader extends URLClassLoader {
        ChildFirstLoader(File candidate) throws Exception {
            super(new URL[]{candidate.toURI().toURL()}, HungBySonMonsterCacheHostTest.class.getClassLoader());
        }

        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("luna.features.MonsterCacheSupport")) return super.loadClass(name, resolve);
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try { loaded = findClass(name); }
                catch (ClassNotFoundException missing) { return super.loadClass(name, resolve); }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    private static final class FakeStore {
        byte[] bytes;

        FakeStore copy() {
            FakeStore copy = new FakeStore();
            copy.bytes = bytes == null ? null : bytes.clone();
            return copy;
        }
    }

    private static final class FakeAccess implements MonsterCacheAccess, MonsterCacheTemplateNames {
        long now;
        String server;
        int map;
        int zone;
        boolean isReady = true;
        boolean throwOnSave;
        int loads;
        boolean failRestores;
        boolean throwOnRequest;
        boolean nativeDiscards;
        int saves;
        int restoreAttempts;
        final FakeStore store;
        final Map<Integer, FakeMonster> monsters = new HashMap<Integer, FakeMonster>();
        final List<FakeMonster> restores = new ArrayList<FakeMonster>();
        final List<FakeMonster> discardedEntities = new ArrayList<FakeMonster>();
        final List<Request> requests = new ArrayList<Request>();
        final List<DeadCheck> deadChecks = new ArrayList<DeadCheck>();
        final Map<Integer, String> templateNames = new HashMap<Integer, String>();

        FakeAccess(FakeStore store, String server, int map, int zone) {
            this.store = store;
            this.server = server;
            this.map = map;
            this.zone = zone;
        }

        public long now() { return now; }
        public String serverKey() { return server; }
        public int mapId() { return map; }
        public int zoneId() { return zone; }
        public boolean ready() { return isReady; }
        public String templateName(int template) { return templateNames.get(Integer.valueOf(template)); }

        public Object find(int id) { return monsters.get(Integer.valueOf(id)); }
        public int hp(Object object) { return ((FakeMonster)object).hp; }
        public int id(Object object) { return ((FakeMonster)object).id; }
        public int type(Object object) { return 1; }
        public boolean autoEnabled() { return false; }
        public void clearAutoTarget(Object object) { }
        public boolean applyHp(Object object, int hp) {
            FakeMonster monster = (FakeMonster)object;
            if (hp < 0 || hp > monster.maxHp || monster.maxHp <= 0) return false;
            monster.hp = hp; return true;
        }

        public boolean dead(Object object) {
            if (!(object instanceof FakeMonster)) return false;
            FakeMonster monster = (FakeMonster)object;
            deadChecks.add(new DeadCheck(monster.id, now));
            return monster.dead || monster.hp <= 0 || monster.hidden;
        }

        public boolean hidden(Object object) {
            return object instanceof FakeMonster && ((FakeMonster)object).hidden;
        }
        public boolean retiring(Object object) { return false; }
        public void corpse(Object object, boolean advance) {
            FakeMonster monster = (FakeMonster)object;
            monster.hp = 0; monster.dead = true; monster.hidden = false; monster.pending = true;
        }

        public Object restore(int template, int id, int x, int y) {
            restoreAttempts++;
            if (failRestores) return null;
            FakeMonster monster = new FakeMonster(id, template, x, y, true);
            monsters.put(Integer.valueOf(id), monster);
            restores.add(monster);
            return monster;
        }

        public void pending(Object object, boolean value) {
            if (object instanceof FakeMonster) {
                FakeMonster monster = (FakeMonster)object;
                monster.pending = value;
                if (value) {
                    monster.hp = 0;
                    monster.dead = true;
                } else if (monster.hp > 0) {
                    monster.dead = false;
                    monster.hidden = false;
                }
                // Clearing quarantine at HP zero must preserve native death and hidden state.
            }
        }

        public void discard(Object object) {
            if (!(object instanceof FakeMonster)) return;
            FakeMonster monster = (FakeMonster)object;
            if (!monster.placeholder) nativeDiscards = true;
            monster.discarded = true;
            discardedEntities.add(monster);
            Integer key = Integer.valueOf(monster.id);
            if (monsters.get(key) == monster) monsters.remove(key);
        }

        public void request(int id) {
            if (throwOnRequest) throw new IllegalStateException("simulated detail send failure");
            requests.add(new Request(id, now));
        }

        public byte[] load() {
            loads++;
            return store.bytes == null ? null : store.bytes.clone();
        }

        public boolean save(byte[] data) {
            saves++;
            if (throwOnSave) throw new IllegalStateException("simulated RMS write failure");
            store.bytes = data == null ? null : data.clone();
            return true;
        }

        public void hud(Object graphics, String status) { }
    }

    private static final class FakeMonster {
        final int id;
        int template;
        int x;
        int y;
        final boolean placeholder;
        int hp;
        int maxHp;
        boolean pending;
        boolean discarded;
        boolean dead;
        boolean hidden;

        FakeMonster(int id, int template, int x, int y, boolean placeholder) {
            this.id = id;
            this.template = template;
            this.x = x;
            this.y = y;
            this.placeholder = placeholder;
            this.hp = 1;
            this.maxHp = 1;
        }
    }

    private static final class Request {
        final int id;
        final long when;

        Request(int id, long when) { this.id = id; this.when = when; }
    }

    private static final class DeadCheck {
        final int id;
        final long when;

        DeadCheck(int id, long when) { this.id = id; this.when = when; }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
