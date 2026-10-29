import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import luna.api.MonsterCacheAccess;

/** Regression checks for corpse recovery under combat traffic in crowded zones. */
public final class HungBySonCrowdedMonsterHostTest {
    private static int checks;

    private HungBySonCrowdedMonsterHostTest() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2)
            throw new IllegalArgumentException("<candidate.jar> [all|positive|initial-detail|watchdog-detail|watch-revival|repeated-zero]");
        File candidate = new File(args[0]);
        require(candidate.isFile(), "candidate JAR exists");
        System.out.println("candidate SHA-256=" + sha256(candidate));

        String selected = args.length == 2 ? args[1] : "all";
        if ("all".equals(selected)) {
            checkPositiveCombatReleasesCorpse(candidate);
            checkZeroAndInvalidCombatKeepCorpse(candidate);
            checkUnsentDetailIdentityRules(candidate);
            checkInvalidNativeDetailsQuarantine(candidate);
            checkMalformedInitialDetailQuarantine(candidate);
            checkInvalidWatchdogDetailQuarantine(candidate);
            checkUnchangedHpWatchQueryClearsOnRevive(candidate);
            checkRepeatedZeroCombatKeepsRecovery(candidate);
            checkReplacementAndContextIsolation(candidate);
            checkUnknownCombatSkipsLookup(candidate);
            checkCrowdedMultiHourRevival(candidate);
        } else if ("positive".equals(selected)) {
            checkPositiveCombatReleasesCorpse(candidate);
        } else if ("initial-detail".equals(selected)) {
            checkMalformedInitialDetailQuarantine(candidate);
        } else if ("watchdog-detail".equals(selected)) {
            checkInvalidWatchdogDetailQuarantine(candidate);
        } else if ("watch-revival".equals(selected)) {
            checkUnchangedHpWatchQueryClearsOnRevive(candidate);
        } else if ("repeated-zero".equals(selected)) {
            checkRepeatedZeroCombatKeepsRecovery(candidate);
        } else {
            throw new IllegalArgumentException("Unknown scenario: " + selected);
        }

        System.out.println("HungBySonCrowdedMonsterHostTest PASS (" + checks + " checks; "
                + selected + ")");
    }

    private static void checkPositiveCombatReleasesCorpse(File candidate) throws Exception {
        Access access = fresh("crowded-positive", 40, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            Monster corpse = learn(support, access, 41, 5, 30, 40, 5, 50, 100);
            nativeDetail(support, access, corpse, 5, 30, 40, 0, 100);
            access.now = 3000L;
            support.tick(access);
            require(access.requests.size() == 1 && access.requests.get(0).id == 41,
                    "corpse enters its first paced recovery request");
            require(corpse.hp == 0 && corpse.dead && corpse.pending,
                    "zero detail remains a pending corpse before positive combat");

            int oldX = corpse.x, oldY = corpse.y;
            access.now = 3200L;
            support.onCombatSnapshot(access, combat(new int[]{41}, new int[]{37}));
            require(corpse.hp == 37 && !corpse.dead && !corpse.hidden && !corpse.pending,
                    "positive server combat HP revives the confirmed identity");
            require(corpse.x == oldX && corpse.y == oldY,
                    "combat revival preserves the confirmed monster coordinates");
            require(statusNumber(support.status(), "K:") == 0
                    && statusNumber(support.status(), "W:") == 0,
                    "positive combat clears corpse and matching recovery state");

            for (int i = 0; i < 12; i++) {
                access.now += 1000L;
                support.onEntityUpdate(access, corpse);
                support.tick(access);
                require(corpse.hp == 37 && !corpse.dead && !corpse.hidden,
                        "later world updates preserve revived HP, pass " + i);
            }
            require(access.requests.size() == 1,
                    "a revived identity does not receive repeated recovery requests");
        }
    }

    private static void checkZeroAndInvalidCombatKeepCorpse(File candidate) throws Exception {
        Access access = fresh("crowded-invalid", 41, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            Monster corpse = learn(support, access, 51, 6, 70, 80, 7, 60, 100);
            nativeDetail(support, access, corpse, 7, 70, 80, 0, 100);

            support.onCombatSnapshot(access, combat(new int[]{51}, new int[]{101}));
            require(corpse.hp == 0 && corpse.dead && support.hold(corpse),
                    "HP above max is rejected and does not release a corpse");
            support.onCombatSnapshot(access, combat(new int[]{51}, new int[]{-1}));
            require(corpse.hp == 0 && corpse.dead && support.hold(corpse),
                    "negative combat HP is rejected and does not release a corpse");

            DataInputStream truncated = combat(new int[]{51}, new int[]{39});
            byte[] all = readRemaining(truncated);
            support.onCombatSnapshot(access, new DataInputStream(
                    new ByteArrayInputStream(all, 0, all.length - 5)));
            require(corpse.hp == 0 && corpse.dead,
                    "truncated combat frame applies no partial positive HP");

            support.onCombatSnapshot(access, combat(new int[]{51}, new int[]{0}));
            support.onEntityUpdate(access, corpse);
            require(corpse.hp == 0 && corpse.dead && !corpse.hidden,
                    "valid zero combat HP remains a visible non-targetable corpse");
            String zeroStatus = support.status();
            int corpseCount = statusNumber(zeroStatus, "K:");
            require(corpseCount == 1,
                    "zero combat HP keeps the corpse recovery record (parsed K="
                            + corpseCount + "): " + zeroStatus);
        }
    }

    private static void checkUnsentDetailIdentityRules(File candidate) throws Exception {
        Access borrowedAccess = fresh("crowded-borrowed", 42, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(borrowedAccess);
            Monster first = learn(support, borrowedAccess, 61, 7, 10, 20, 8, 50, 100);
            Monster second = learn(support, borrowedAccess, 62, 7, 30, 40, 8, 55, 100);
            nativeDetail(support, borrowedAccess, first, 8, 10, 20, 0, 100);
            nativeDetail(support, borrowedAccess, second, 8, 30, 40, 0, 100);

            borrowedAccess.now = 3000L;
            support.tick(borrowedAccess);
            borrowedAccess.now = 3100L;
            support.tick(borrowedAccess);
            require(borrowedAccess.requests.size() == 1 && borrowedAccess.requests.get(0).id == 61,
                    "global request pacing leaves the second borrowed recovery unsent");
            require(statusNumber(support.status(), "W:") == 2,
                    "both borrowed recoveries are pending while only one was sent");

            second.hp = 37; // Native packet-7 has written HP before its support hook.
            second.maxHp = 100;
            support.onDetail(borrowedAccess, 62, 8, 30, 40, 37, 100);
            support.onEntityUpdate(borrowedAccess, second);
            require(second.hp == 37 && !second.dead && !second.hidden && !second.pending,
                    "valid unsolicited detail releases a previously confirmed borrowed identity");
            require(statusNumber(support.status(), "W:") == 1,
                    "borrowed unsolicited detail closes only its own unsent pending recovery");
            require(borrowedAccess.requests.size() == 1,
                    "releasing unsent borrowed recovery does not manufacture a request");
        }

        Store store = new Store();
        Access seed = new Access(store, "crowded-owned", 43, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(seed);
            seedRecord(support, seed, 71, 8, 50, 60);
            seedRecord(support, seed, 72, 8, 70, 80);
            seed.now = 600L;
            support.tick(seed);
        }

        Access restored = new Access(store, "crowded-owned", 43, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(restored);
            restored.now = 801L;
            support.tick(restored);
            require(restored.restores.size() == 2,
                    "cached identities restore into owned placeholders for the unsolicited-detail check");
            Monster owned = restored.monsters.get(Integer.valueOf(72));
            require(owned != null && owned.placeholder && owned.pending,
                    "second restored identity is an owned pending placeholder");
            require(restored.requests.size() == 1 && restored.requests.get(0).id == 71,
                    "second owned placeholder has no sent detail request");

            owned.hp = 37; // Model packet-7's native field write before the observer hook.
            owned.maxHp = 100;
            support.onDetail(restored, 72, 8, 70, 80, 37, 100);
            support.onCombatSnapshot(restored, combat(new int[]{72}, new int[]{37}));
            support.onEntityUpdate(restored, owned);
            require(owned.pending && owned.placeholder && support.hold(owned),
                    "unsolicited detail cannot promote an unconfirmed owned placeholder");
            require(!owned.discarded && restored.requests.size() == 1,
                    "unrequested placeholder remains bounded without discard or extra request");
        }
    }

    private static void checkInvalidNativeDetailsQuarantine(File candidate) throws Exception {
        final int cases = 4;
        for (int kind = 0; kind < cases; kind++) {
            Access access = fresh("crowded-invalid-detail-" + kind, 431 + kind, 0);
            try (Support support = new Support(candidate)) {
                support.onMap(access);
                int id = 901 + kind;
                int x = 120 + kind * 20;
                int y = 160 + kind * 20;
                int level = 15 + kind;
                Monster monster = learn(support, access, id, 11, x, y, level, 45, 100);

                int detailLevel = level;
                int detailX = x;
                int detailY = y;
                int detailHp = 101;
                int detailMaxHp = 100;
                String reason = "HP above max";
                if (kind == 1) {
                    detailHp = -1;
                    reason = "negative HP";
                } else if (kind == 2) {
                    detailHp = 37;
                    detailLevel = level + 1;
                    reason = "wrong level";
                } else if (kind == 3) {
                    detailHp = 37;
                    detailX = x + 200;
                    detailY = y + 200;
                    reason = "wrong coordinates";
                }

                nativeDetail(support, access, monster, detailLevel, detailX, detailY,
                        detailHp, detailMaxHp);
                require(monster.hp == 0 && monster.dead && monster.pending
                        && support.hold(monster),
                        "invalid no-pending detail is quarantined for the confirmed identity: " + reason);
                require(access.requests.isEmpty(),
                        "invalid native detail does not synchronously send a request: " + reason);

                nativeDetail(support, access, monster, level, x, y, 37, 100);
                require(monster.hp == 37 && !monster.dead && !monster.hidden
                        && !monster.pending && !support.hold(monster),
                        "valid late same-identity detail releases the quarantined monster: " + reason);
                require(monster.x == x && monster.y == y,
                        "valid detail restores the server-confirmed coordinates after " + reason);
            }
        }
    }

    private static void checkMalformedInitialDetailQuarantine(File candidate) throws Exception {
        Access access = fresh("crowded-initial-invalid-detail", 438, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            Monster monster = new Monster(951, 12, 180, 220, false);
            monster.hp = 101;
            monster.maxHp = 100;
            access.monsters.put(Integer.valueOf(monster.id), monster);
            support.onObject(access, 1, 12, monster.id, monster.x, monster.y, 5);

            nativeDetail(support, access, monster, 18, 180, 220, 101, 100);
            require(monster.hp == 0 && access.dead(monster) && monster.pending,
                    "malformed first detail after packet-4 quarantines the unconfirmed native identity");
            require(access.requests.isEmpty(),
                    "malformed first detail does not auto-confirm or synchronously query its new identity");

            nativeDetail(support, access, monster, 18, 180, 220, 37, 100);
            require(monster.hp == 37 && !monster.dead && !monster.hidden && !monster.pending,
                    "matching detail confirms and releases the quarantined first packet-4 identity");
            require(!support.hold(monster),
                    "first packet-4 identity leaves quarantine after its matching detail");
        }
    }

    private static void checkInvalidWatchdogDetailQuarantine(File candidate) throws Exception {
        Access access = fresh("crowded-watchdog-detail", 439, 0);
        access.autoEnabled = true;
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            Monster monster = learn(support, access, 961, 13, 240, 260, 19, 48, 100);
            require(support.verifyTarget(access, monster.id, monster),
                    "confirmed native identity accepts an explicit watchdog verification");
            access.now = 1000L;
            support.tick(access);
            require(access.requests.size() == 1 && access.requests.get(0).id == monster.id,
                    "watchdog verification enters the shared native detail request path");

            nativeDetail(support, access, monster, 19, 240, 260, 101, 100);
            require(monster.hp == 0 && monster.dead && monster.pending && support.hold(monster),
                    "invalid watchdog detail quarantines the already-confirmed native object");

            nativeDetail(support, access, monster, 19, 240, 260, 37, 100);
            require(monster.hp == 37 && !monster.dead && !monster.hidden && !monster.pending,
                    "valid late watchdog detail recovers the same native identity");
            require(!support.hold(monster), "valid watchdog recovery releases the native object hold");
        }
    }

    private static void checkUnchangedHpWatchQueryClearsOnRevive(File candidate) throws Exception {
        Access access = fresh("crowded-watch-revival", 440, 0);
        access.autoEnabled = true;
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            Monster monster = learn(support, access, 965, 13, 260, 280, 19, 37, 100);

            access.now = 1000L;
            support.watchAttack(access, monster.id);
            access.now = 2000L;
            support.watchAttack(access, monster.id);
            access.now = 3500L;
            support.watchAttack(access, monster.id);
            support.watchTick(access);
            require(!support.watchAllow(access, monster),
                    "three stalled attacks create a target exclusion before respawn");
            require(statusNumber(support.status(), "W:") == 1,
                    "watchdog queues one target detail verification");

            support.onCombatSnapshot(access, combat(new int[]{monster.id}, new int[]{37}));
            require(!support.watchAllow(access, monster)
                    && statusNumber(support.status(), "W:") == 1,
                    "unchanged live HP leaves its watchdog query and exclusion active");

            monster.hp = 0;
            monster.dead = true;
            support.onEntityUpdate(access, monster);
            require(statusNumber(support.status(), "K:") == 1,
                    "native corpse hook records the death without an HP-zero detail event");
            support.onCombatSnapshot(access, combat(new int[]{monster.id}, new int[]{37}));
            require(monster.hp == 37 && !monster.dead && !monster.pending,
                    "same-baseline positive combat HP revives the same watched identity");
            require(statusNumber(support.status(), "K:") == 0
                    && statusNumber(support.status(), "W:") == 0,
                    "revival clears corpse and watchdog pending state");
            require(support.watchAllow(access, monster),
                    "same-baseline revival immediately clears its stale watch exclusion");
        }
    }

    private static void checkRepeatedZeroCombatKeepsRecovery(File candidate) throws Exception {
        Access access = fresh("crowded-repeated-zero", 46, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            Monster monster = learn(support, access, 971, 14, 280, 300, 20, 45, 100);
            nativeDetail(support, access, monster, 20, 280, 300, 0, 100);
            access.now = 3000L;
            support.tick(access);
            require(access.requests.size() == 1 && access.requests.get(0).id == monster.id
                    && statusNumber(support.status(), "W:") == 1,
                    "normal corpse recovery starts an in-flight request");

            for (long now = 3500L; now <= 65000L; now += 500L) {
                access.now = now;
                int pendingBefore = statusNumber(support.status(), "W:");
                support.onCombatSnapshot(access, combat(new int[]{monster.id}, new int[]{0}));
                int pendingAfter = statusNumber(support.status(), "W:");
                if (pendingBefore > 0) {
                    require(pendingAfter == pendingBefore,
                            "repeated HP-zero combat does not cancel a normal in-flight request at " + now);
                }
                support.tick(access);
                require(monster.hp == 0 && monster.dead && !monster.hidden,
                        "repeated HP-zero combat remains a visible corpse at " + now);
                require(access.requests.size() <= 5,
                        "unanswered HP-zero stream stays within bounded recovery requests at " + now);
            }
            require(access.requests.size() >= 4 && access.requests.size() <= 5,
                    "normal recovery retries after cooldown without flooding during 65 seconds");
            require(access.requests.get(2).when - access.requests.get(1).when >= 20000L,
                    "second recovery cycle observes the bounded first backoff");

            nativeDetail(support, access, monster, 20, 280, 300, 37, 100);
            require(monster.hp == 37 && !monster.dead && !monster.hidden && !monster.pending,
                    "matching positive detail revives after prolonged HP-zero combat without map reset");
            require(statusNumber(support.status(), "M:") == 46
                    && statusNumber(support.status(), "K:") == 0
                    && statusNumber(support.status(), "W:") == 0,
                    "late detail clears corpse and recovery state in the same map scope");

            int requestsAfterDetail = access.requests.size();
            for (long now = 65500L; now <= 100000L; now += 500L) {
                access.now = now;
                support.tick(access);
            }
            require(access.requests.size() == requestsAfterDetail,
                    "valid detail stops normal retries after the long zero-HP stream");
        }
    }

    private static void checkReplacementAndContextIsolation(File candidate) throws Exception {
        Access zone0 = fresh("crowded-replacement", 44, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(zone0);
            Monster original = learn(support, zone0, 81, 9, 100, 110, 12, 50, 100);
            nativeDetail(support, zone0, original, 12, 100, 110, 0, 100);

            Monster replacement = new Monster(81, 9, 100, 110, false);
            replacement.hp = 44;
            replacement.maxHp = 100;
            zone0.monsters.put(Integer.valueOf(81), replacement);
            support.onCombatSnapshot(zone0, combat(new int[]{81}, new int[]{37}));
            require(original.hp == 0 && original.dead && replacement.hp == 44,
                    "combat for a cached ID cannot revive a same-ID replacement before native confirmation");

            support.onObject(zone0, 1, 9, 81, 100, 110, 5);
            replacement.hp = 44;
            support.onDetail(zone0, 81, 12, 100, 110, 44, 100);
            support.onCombatSnapshot(zone0, combat(new int[]{81}, new int[]{37}));
            require(replacement.hp == 37 && !replacement.dead,
                    "packet-4 and matching detail rebind the replacement identity");

            Access zone1 = new Access(zone0.store, "crowded-replacement", 44, 1);
            support.onMap(zone1);
            Monster otherZone = learn(support, zone1, 81, 10, 300, 310, 13, 30, 90);
            support.onCombatSnapshot(zone0, combat(new int[]{81}, new int[]{20}));
            require(otherZone.hp == 30 && replacement.hp == 37,
                    "a stale access from the previous zone cannot mutate a same-ID current identity");
            support.onCombatSnapshot(zone1, combat(new int[]{81}, new int[]{21}));
            require(otherZone.hp == 21 && replacement.hp == 37,
                    "same IDs in separate zones retain independent confirmed identities");
        }
    }

    private static void checkUnknownCombatSkipsLookup(File candidate) throws Exception {
        Access access = fresh("crowded-unknown-snapshot", 449, 0);
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            int[] ids = new int[55];
            int[] hp = new int[55];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = 2000 + i;
                hp[i] = 30 + (i % 50);
            }
            int before = access.findCalls;
            support.onCombatSnapshot(access, combat(ids, hp));
            require(access.findCalls == before,
                    "unknown full-zone combat targets are skipped without native find lookups");
            require(access.monsters.isEmpty(), "unknown combat IDs never fabricate local entities");
        }
    }

    private static void checkCrowdedMultiHourRevival(File candidate) throws Exception {
        final int count = 55;
        final int cycles = 720;
        final int ticksPerCycle = 60;
        Access access = fresh("crowded-55", 45, 0);
        Monster[] monsters = new Monster[count];
        int[] ids = new int[count];
        int[] positiveHp = new int[count];
        int simulatedTicks = 0;
        try (Support support = new Support(candidate)) {
            support.onMap(access);
            for (int i = 0; i < count; i++) {
                monsters[i] = learn(support, access, 1000 + i, 20 + (i % 4),
                        200 + i * 3, 400 + i * 2, 30 + (i % 10), 70, 100);
                ids[i] = monsters[i].id;
                positiveHp[i] = 25 + (i % 65);
            }

            for (int cycle = 0; cycle < cycles; cycle++) {
                long cycleStart = cycle * 30000L;
                access.now = cycleStart;
                for (int i = 0; i < count; i++) {
                    Monster monster = monsters[i];
                    monster.hp = 0;
                    monster.dead = true;
                    monster.pending = false;
                    monster.hidden = false;
                    if (cycle % 6 == 0) {
                        nativeDetail(support, access, monster, 30 + (i % 10),
                                monster.x, monster.y, 0, 100);
                    } else {
                        support.onEntityUpdate(access, monster); // Packet-7 detail outage.
                    }
                }

                if (cycle % 17 == 0) {
                    int[] invalidHp = new int[count];
                    for (int i = 0; i < count; i++) invalidHp[i] = positiveHp[i];
                    invalidHp[0] = -1;
                    support.onCombatSnapshot(access, combat(ids, invalidHp));
                    boolean allStillDead = true;
                    for (int i = 0; i < count; i++) {
                        if (monsters[i].hp != 0 || !monsters[i].dead) allStillDead = false;
                    }
                    require(allStillDead,
                            "occasional malformed full-zone frame applies no partial HP, cycle " + cycle);
                }

                int requestsAfterRevival = -1;
                for (int step = 1; step <= ticksPerCycle; step++) {
                    access.now = cycleStart + step * 500L;
                    if (step == 30) {
                        int lookupsBeforeCombat = access.findCalls;
                        support.onCombatSnapshot(access, combat(ids, positiveHp));
                        int combatLookups = access.findCalls - lookupsBeforeCombat;
                        require(combatLookups <= count,
                                "55 known combat victims use at most 55 identity lookups, cycle " + cycle
                                        + " (observed " + combatLookups + ")");
                        require(statusNumber(support.status(), "K:") == 0,
                                "full 55-ID combat frame clears corpses at cycle " + cycle);
                        require(statusNumber(support.status(), "W:") == 0,
                                "full 55-ID combat frame drains pending recoveries at cycle " + cycle);

                        int lookupsBeforeLiveUpdates = access.findCalls;
                        for (int i = 0; i < count; i++) {
                            Monster monster = monsters[i];
                            support.onEntityUpdate(access, monster);
                            require(monster.hp == positiveHp[i] && !monster.dead
                                    && !monster.hidden && !monster.pending,
                                    "crowded live update preserves ID " + monster.id + " at cycle " + cycle);
                        }
                        require(access.findCalls == lookupsBeforeLiveUpdates,
                                "live corpse checks skip full identity lookups at cycle " + cycle);
                        requestsAfterRevival = access.requests.size();
                    }
                    support.tick(access);
                    simulatedTicks++;
                }
                require(requestsAfterRevival >= 0, "each cycle includes its positive update");
                require(access.requests.size() == requestsAfterRevival,
                        "live crowded identities do not repeat requests during 500 ms ticks, cycle " + cycle);
                require(statusNumber(support.status(), "K:") == 0
                        && statusNumber(support.status(), "W:") == 0,
                        "crowded corpse and pending counts stay drained, cycle " + cycle);
            }
            require(access.monsters.size() == count && access.restores.isEmpty()
                    && access.discarded.isEmpty(),
                    "720 crowded cycles over six simulated hours keep all 55 native identities");
            require(access.findCalls < 1000000,
                    "bounded identity-lookup count after 43,200 half-second ticks: " + access.findCalls);
            require(simulatedTicks == 43200 && access.now == 6L * 60L * 60L * 1000L,
                    "crowded simulation advances six hours in exactly 43,200 half-second ticks");
        }
    }

    private static Access fresh(String server, int map, int zone) {
        return new Access(new Store(), server, map, zone);
    }

    private static Monster learn(Support support, Access access, int id, int template,
            int x, int y, int level, int hp, int maxHp) throws Exception {
        Monster monster = new Monster(id, template, x, y, false);
        monster.hp = hp;
        monster.maxHp = maxHp;
        access.monsters.put(Integer.valueOf(id), monster);
        support.onObject(access, 1, template, id, x, y, 5);
        nativeDetail(support, access, monster, level, x, y, hp, maxHp);
        return monster;
    }

    private static void seedRecord(Support support, Access access, int id,
            int template, int x, int y) throws Exception {
        learn(support, access, id, template, x, y, 8, 40, 100);
    }

    private static void nativeDetail(Support support, Access access, Monster monster,
            int level, int x, int y, int hp, int maxHp) throws Exception {
        monster.hp = hp;
        monster.maxHp = maxHp;
        monster.x = x;
        monster.y = y;
        if (hp == 0) monster.dead = true;
        support.onDetail(access, monster.id, level, x, y, hp, maxHp);
    }

    private static DataInputStream combat(int[] ids, int[] hp) throws Exception {
        if (ids.length != hp.length) throw new IllegalArgumentException("parallel arrays required");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(777); // attacker
        out.writeByte(19); // skill
        out.writeByte(ids.length);
        for (int i = 0; i < ids.length; i++) {
            out.writeShort(ids[i]);
            out.writeInt(13); // damage
            out.writeInt(hp[i]);
            out.writeByte(0); // effects
        }
        out.writeInt(1234);
        out.writeInt(567);
        out.writeByte(2);
        out.writeInt(8);
        out.flush();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static byte[] readRemaining(DataInputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (input.available() > 0) bytes.write(input.readByte());
        return bytes.toByteArray();
    }

    private static int statusNumber(String status, String key) {
        int at = status.lastIndexOf(key);
        if (at < 0) throw new AssertionError("status is missing " + key + ": " + status);
        at += key.length();
        int end = at;
        while (end < status.length() && Character.isDigit(status.charAt(end))) end++;
        return Integer.parseInt(status.substring(at, end));
    }

    private static String sha256(File file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(file.toPath()));
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format("%02X", Integer.valueOf(value & 255)));
        return result.toString();
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
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

    private static final class Support implements AutoCloseable {
        private final SupportLoader loader;
        private final Method onMap;
        private final Method onObject;
        private final Method onDetail;
        private final Method tick;
        private final Method onCombatSnapshot;
        private final Method onEntityUpdate;
        private final Method hold;
        private final Method status;
        private final Method verifyTarget;
        private final Method watchAttack;
        private final Method watchTick;
        private final Method watchAllow;

        Support(File candidate) throws Exception {
            loader = new SupportLoader(candidate);
            Class<?> type = loader.loadClass("luna.features.MonsterCacheSupport");
            onMap = type.getMethod("onMap", MonsterCacheAccess.class);
            onObject = type.getMethod("onObject", MonsterCacheAccess.class, int.class,
                    int.class, int.class, int.class, int.class, int.class);
            onDetail = type.getMethod("onDetail", MonsterCacheAccess.class, int.class,
                    int.class, int.class, int.class, int.class, int.class);
            tick = type.getMethod("tick", MonsterCacheAccess.class);
            onCombatSnapshot = type.getMethod("onCombatSnapshot", MonsterCacheAccess.class,
                    DataInputStream.class);
            onEntityUpdate = type.getMethod("onEntityUpdate", MonsterCacheAccess.class, Object.class);
            hold = type.getMethod("hold", Object.class);
            status = type.getMethod("status");
            verifyTarget = type.getMethod("verifyTarget", MonsterCacheAccess.class, int.class, Object.class);
            Class<?> watchType = loader.loadClass("luna.features.MonsterTargetWatchSupport");
            watchAttack = watchType.getMethod("onAttack", MonsterCacheAccess.class, int.class);
            watchTick = watchType.getMethod("tick", MonsterCacheAccess.class);
            watchAllow = watchType.getMethod("allow", MonsterCacheAccess.class, Object.class);
        }

        void onMap(MonsterCacheAccess access) throws Exception { invoke(onMap, access); }
        void onObject(MonsterCacheAccess access, int type, int template, int id,
                int x, int y, int state) throws Exception {
            invoke(onObject, access, Integer.valueOf(type), Integer.valueOf(template),
                    Integer.valueOf(id), Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(state));
        }
        void onDetail(MonsterCacheAccess access, int id, int level,
                int x, int y, int hp, int maxHp) throws Exception {
            invoke(onDetail, access, Integer.valueOf(id), Integer.valueOf(level), Integer.valueOf(x),
                    Integer.valueOf(y), Integer.valueOf(hp), Integer.valueOf(maxHp));
        }
        void tick(MonsterCacheAccess access) throws Exception { invoke(tick, access); }
        void onCombatSnapshot(MonsterCacheAccess access, DataInputStream input) throws Exception {
            invoke(onCombatSnapshot, access, input);
        }
        void onEntityUpdate(MonsterCacheAccess access, Object monster) throws Exception {
            invoke(onEntityUpdate, access, monster);
        }
        boolean hold(Object monster) throws Exception {
            Object value = hold.invoke(null, monster);
            return Boolean.TRUE.equals(value);
        }
        boolean verifyTarget(MonsterCacheAccess access, int id, Object monster) throws Exception {
            try {
                return Boolean.TRUE.equals(verifyTarget.invoke(null,
                        access, Integer.valueOf(id), monster));
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof Exception) throw (Exception)cause;
                if (cause instanceof Error) throw (Error)cause;
                throw new RuntimeException(cause);
            }
        }
        void watchAttack(MonsterCacheAccess access, int id) throws Exception {
            invoke(watchAttack, access, Integer.valueOf(id));
        }
        void watchTick(MonsterCacheAccess access) throws Exception { invoke(watchTick, access); }
        boolean watchAllow(MonsterCacheAccess access, Object monster) throws Exception {
            return Boolean.TRUE.equals(watchAllow.invoke(null, access, monster));
        }
        String status() throws Exception { return (String)status.invoke(null); }
        public void close() throws Exception { loader.close(); }
    }

    private static final class SupportLoader extends URLClassLoader {
        SupportLoader(File candidate) throws Exception {
            super(new URL[]{candidate.toURI().toURL()}, HungBySonCrowdedMonsterHostTest.class.getClassLoader());
        }

        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("luna.features.")) return super.loadClass(name, resolve);
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try { loaded = findClass(name); }
                catch (ClassNotFoundException missing) { return super.loadClass(name, resolve); }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    private static final class Store {
        byte[] bytes;
    }

    private static final class Access implements MonsterCacheAccess {
        long now;
        final String server;
        final int map;
        final int zone;
        boolean ready = true;
        boolean autoEnabled;
        final Store store;
        final Map<Integer, Monster> monsters = new HashMap<Integer, Monster>();
        final List<Monster> restores = new ArrayList<Monster>();
        final List<Monster> discarded = new ArrayList<Monster>();
        final List<Request> requests = new ArrayList<Request>();
        int findCalls;

        Access(Store store, String server, int map, int zone) {
            this.store = store;
            this.server = server;
            this.map = map;
            this.zone = zone;
        }

        public long now() { return now; }
        public String serverKey() { return server; }
        public int mapId() { return map; }
        public int zoneId() { return zone; }
        public boolean ready() { return ready; }
        public Object find(int id) {
            findCalls++;
            return monsters.get(Integer.valueOf(id));
        }
        public int hp(Object object) { return ((Monster)object).hp; }
        public int id(Object object) { return ((Monster)object).id; }
        public int type(Object object) { return object instanceof Monster ? 1 : 0; }
        public boolean autoEnabled() { return autoEnabled; }
        public void clearAutoTarget(Object object) { }

        /** Models the native bridge: bounded HP write, and positive HP clears death/pending state. */
        public boolean applyHp(Object object, int hp) {
            if (!(object instanceof Monster)) return false;
            Monster monster = (Monster)object;
            if (monsters.get(Integer.valueOf(monster.id)) != monster || monster.retiring
                    || monster.maxHp <= 0 || hp < 0 || hp > monster.maxHp) return false;
            monster.hp = hp;
            if (hp > 0) {
                monster.dead = false;
                monster.hidden = false;
                monster.pending = false;
                monster.retiring = false;
                monster.deathState = 0;
                monster.removalCount = 0;
            }
            return true;
        }

        public boolean dead(Object object) {
            if (!(object instanceof Monster)) return false;
            Monster monster = (Monster)object;
            return monster.hp <= 0 || monster.dead || monster.hidden || monster.deathState == 4
                    || monster.retiring;
        }

        public boolean hidden(Object object) {
            return object instanceof Monster && ((Monster)object).hidden;
        }

        public boolean retiring(Object object) {
            return object instanceof Monster && ((Monster)object).retiring;
        }

        public void corpse(Object object, boolean advanceFrame) {
            Monster monster = (Monster)object;
            monster.hp = 0;
            monster.dead = true;
            monster.hidden = false;
            monster.pending = true;
            monster.deathState = 4;
            monster.retiring = false;
            monster.removalCount++;
            if (advanceFrame) monster.deathFrames++;
        }

        public Object restore(int template, int id, int x, int y) {
            Monster monster = new Monster(id, template, x, y, true);
            monsters.put(Integer.valueOf(id), monster);
            restores.add(monster);
            return monster;
        }

        public void pending(Object object, boolean value) {
            if (!(object instanceof Monster)) return;
            Monster monster = (Monster)object;
            monster.pending = value;
            if (value) {
                monster.hp = 0;
                monster.dead = true;
                monster.deathState = 4;
            } else if (monster.hp > 0) {
                monster.dead = false;
                monster.hidden = false;
                monster.deathState = 0;
                monster.retiring = false;
                monster.removalCount = 0;
            }
        }

        public void discard(Object object) {
            if (!(object instanceof Monster)) return;
            Monster monster = (Monster)object;
            monster.discarded = true;
            discarded.add(monster);
            if (monsters.get(Integer.valueOf(monster.id)) == monster)
                monsters.remove(Integer.valueOf(monster.id));
        }

        public void request(int id) { requests.add(new Request(id, now)); }
        public byte[] load() { return store.bytes == null ? null : store.bytes.clone(); }
        public boolean save(byte[] data) { store.bytes = data == null ? null : data.clone(); return true; }
        public void hud(Object graphics, String status) { }
    }

    private static final class Monster {
        final int id;
        final int template;
        int x;
        int y;
        final boolean placeholder;
        int hp = 1;
        int maxHp = 1;
        boolean dead;
        boolean hidden;
        boolean pending;
        boolean retiring;
        boolean discarded;
        int deathState;
        int deathFrames;
        int removalCount;

        Monster(int id, int template, int x, int y, boolean placeholder) {
            this.id = id;
            this.template = template;
            this.x = x;
            this.y = y;
            this.placeholder = placeholder;
        }
    }

    private static final class Request {
        final int id;
        final long when;
        Request(int id, long when) { this.id = id; this.when = when; }
    }
}
