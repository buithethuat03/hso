import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import luna.api.MonsterCacheAccess;

/** Host lifecycle and payload checks for stale auto-target quarantine. */
public final class HungBySonStaleTargetHostTest {
    private static int checks;

    private HungBySonStaleTargetHostTest() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("<candidate.jar>");
        File candidate = new File(args[0]);
        require(candidate.isFile(), "candidate JAR exists");

        checkStallThresholdAndQuietWindow(candidate);
        checkHpProgressResetsWatch(candidate);
        checkValidatedCooldownAndHpOutcomes(candidate);
        checkRepeatedStallBackoff(candidate);
        checkMismatchAndTimeoutRelease(candidate);
        checkSharedRequestBudget(candidate);
        checkHealthQueriesShareNormalRecoveryQueue(candidate);
        checkAutoGateAndIdentityScope(candidate);
        checkExclusionCapacity(candidate);
        checkUnseenCombatFallback(candidate);
        checkCombatSnapshot(candidate);

        System.out.println("HungBySonStaleTargetHostTest PASS (" + checks + " checks)");
    }

    private static void checkStallThresholdAndQuietWindow(File candidate) throws Exception {
        try (Harness h = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-stall", 4, 2);
            h.onMap(access);
            FakeMonster target = h.confirmed(access, 100, 17, 20, 30, 55, 80);
            access.target = target;

            access.now = 1000L; h.watch.onAttack(access, target.id);
            access.now = 2000L; h.watch.onAttack(access, target.id);
            access.now = 3499L; h.watch.tick(access);
            require(access.requests.isEmpty(), "two queued notifications cannot trigger verification");
            require(h.watch.allow(access, target), "unverified primary target remains eligible");

            access.now = 3500L; h.watch.onAttack(access, target.id);
            h.watch.tick(access);
            h.cache.tick(access);
            require(access.requests.size() == 1 && access.requests.get(0).id == target.id,
                    "three queued notifications across 2500 ms start cache verification");
            require(!h.watch.allow(access, target), "the exact stalled target is temporarily excluded");
            require(access.clearTargetCalls == 1 && access.lastCleared == target && access.target == null,
                    "verification clears only the held native target identity");
            require(access.pendingCalls == 0 && access.hpWriteCalls == 0,
                    "live verification never quarantines or writes native HP");

            Harness quiet = new Harness(candidate);
            try {
                FakeAccess idle = new FakeAccess("watch-quiet", 4, 2);
                quiet.onMap(idle);
                FakeMonster quietTarget = quiet.confirmed(idle, 101, 18, 20, 30, 40, 80);
                idle.now = 1000L; quiet.watch.onAttack(idle, quietTarget.id);
                idle.now = 2000L; quiet.watch.onAttack(idle, quietTarget.id);
                idle.now = 3500L; quiet.watch.onAttack(idle, quietTarget.id);
                idle.now = 8500L; quiet.watch.tick(idle);
                require(idle.requests.isEmpty(), "five seconds without a queued send suppresses verification");
                idle.now = 8501L; quiet.watch.onAttack(idle, quietTarget.id);
                quiet.watch.tick(idle);
                quiet.cache.tick(idle);
                require(idle.requests.size() == 1,
                        "a fresh queued notification reopens the recent-send verification window");
            } finally {
                quiet.close();
            }
        }

        try (Harness idleReset = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-idle-reset", 4, 2);
            idleReset.onMap(access);
            FakeMonster target = idleReset.confirmed(access, 102, 19, 20, 30, 45, 80);
            access.now = 1000L; idleReset.watch.onAttack(access, target.id);
            access.now = 2000L; idleReset.watch.onAttack(access, target.id);
            access.now = 3500L; idleReset.watch.onAttack(access, target.id);
            access.now = 33501L; idleReset.watch.onAttack(access, target.id);
            idleReset.watch.tick(access);
            idleReset.cache.tick(access);
            require(access.requests.isEmpty(), "a 30-second idle gap resets the queued-send count");
            access.now = 34751L; idleReset.watch.onAttack(access, target.id);
            access.now = 36001L; idleReset.watch.onAttack(access, target.id);
            idleReset.watch.tick(access);
            idleReset.cache.tick(access);
            require(access.requests.size() == 1, "a fresh post-idle sequence can trigger after 2500 ms");
        }
    }

    private static void checkHpProgressResetsWatch(File candidate) throws Exception {
        try (Harness h = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-progress", 5, 1);
            h.onMap(access);
            FakeMonster target = h.confirmed(access, 110, 21, 40, 50, 60, 100);
            access.now = 1000L; h.watch.onAttack(access, target.id);
            access.now = 2000L; h.watch.onAttack(access, target.id);

            access.now = 3500L;
            target.hp = 59;
            h.watch.onDetail(access, target.id, target, true, target.hp);
            h.watch.tick(access);
            require(access.requests.isEmpty(), "positive HP progress resets the old stall timer");

            access.now = 3600L; h.watch.onAttack(access, target.id);
            access.now = 4600L; h.watch.onAttack(access, target.id);
            access.now = 6100L; h.watch.tick(access);
            require(access.requests.isEmpty(), "two sends after HP progress are still below the threshold");
            h.watch.onAttack(access, target.id);
            h.watch.tick(access);
            h.cache.tick(access);
            require(access.requests.size() == 1,
                    "three fresh sends plus 2500 ms without further HP progress trigger verification");
        }
    }

    private static void checkValidatedCooldownAndHpOutcomes(File candidate) throws Exception {
        try (Harness sameHp = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-unchanged", 6, 1);
            sameHp.onMap(access);
            FakeMonster target = sameHp.confirmed(access, 120, 22, 1, 2, 35, 70);
            trigger(sameHp, access, target, 1000L);
            require(!sameHp.watch.allow(access, target), "query-pending target stays excluded");

            access.now += 1L;
            sameHp.cache.onDetail(access, target.id, 1, target.x, target.y, target.hp, target.maxHp);
            access.now += 2999L;
            sameHp.watch.tick(access);
            require(!sameHp.watch.allow(access, target), "matching unchanged HP has a three-second cooldown");
            access.now += 1L;
            require(sameHp.watch.allow(access, target), "unchanged HP becomes eligible after its cooldown");
            require(access.pendingCalls == 0 && access.hpWriteCalls == 0,
                    "validated live target still receives no pending or HP mutation");
        }

        try (Harness progress = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-progress-release", 6, 1);
            progress.onMap(access);
            FakeMonster target = progress.confirmed(access, 121, 22, 1, 2, 35, 70);
            trigger(progress, access, target, 1000L);
            target.hp = 34;
            progress.cache.onDetail(access, target.id, 1, target.x, target.y, target.hp, target.maxHp);
            require(progress.watch.allow(access, target), "positive HP progress immediately releases exclusion");
            require(target.hp == 34 && access.hpWriteCalls == 0,
                    "watchdog observes HP progress without writing HP");
        }

        try (Harness dead = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-hp-zero", 6, 1);
            dead.onMap(access);
            FakeMonster target = dead.confirmed(access, 122, 22, 1, 2, 35, 70);
            trigger(dead, access, target, 1000L);
            target.hp = 0;
            target.dead = true;
            dead.cache.onDetail(access, target.id, 1, target.x, target.y, 0, target.maxHp);
            require(!dead.watch.allow(access, target), "HP-zero native target remains gated as dead");
            require(access.pendingCalls == 0 && access.hpWriteCalls == 0,
                    "HP-zero result releases watchdog state without reviving or quarantining the object");
        }
    }

    private static void checkMismatchAndTimeoutRelease(File candidate) throws Exception {
        try (Harness mismatch = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-mismatch", 7, 1);
            mismatch.onMap(access);
            FakeMonster target = mismatch.confirmed(access, 130, 23, 7, 8, 30, 60);
            trigger(mismatch, access, target, 1000L);
            mismatch.cache.onDetail(access, target.id, 1, target.x + 100, target.y + 100,
                    target.hp, target.maxHp);
            access.now += 2999L;
            mismatch.watch.tick(access);
            require(!mismatch.watch.allow(access, target), "mismatched validation retains the short exclusion");
            access.now += 1L;
            require(mismatch.watch.allow(access, target), "mismatched validation releases after three seconds");
        }

        try (Harness timeout = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-timeout", 7, 1);
            timeout.onMap(access);
            FakeMonster target = timeout.confirmed(access, 131, 23, 7, 8, 30, 60);
            trigger(timeout, access, target, 1000L);
            access.now += 7999L;
            timeout.cache.tick(access);
            require(!timeout.watch.allow(access, target), "health query remains pending before the eight-second cache timeout");
            access.now += 1L;
            timeout.cache.tick(access);
            require(!timeout.watch.allow(access, target), "cache timeout moves to the bounded three-second cooldown");
            access.now += 2999L;
            timeout.cache.tick(access);
            require(!timeout.watch.allow(access, target), "timeout cooldown does not release early");
            access.now += 1L;
            require(timeout.watch.allow(access, target), "timed-out target cannot remain permanently excluded");
            require(access.pendingCalls == 0 && access.hpWriteCalls == 0,
                    "timeout cleanup does not quarantine or alter HP");
        }

        try (Harness watchdogLimit = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-pending-limit", 7, 1);
            watchdogLimit.onMap(access);
            FakeMonster target = watchdogLimit.confirmed(access, 132, 23, 7, 8, 30, 60);
            triggerWithoutCachePoll(watchdogLimit, access, target, 1000L);
            access.now += 9999L;
            watchdogLimit.watch.tick(access);
            require(!watchdogLimit.watch.allow(access, target), "watch query exclusion remains active before ten seconds");
            access.now += 2L;
            watchdogLimit.watch.tick(access);
            require(!watchdogLimit.watch.allow(access, target), "watch expiry enters the short retry cooldown after ten seconds");
            access.now += 2999L;
            watchdogLimit.watch.tick(access);
            require(!watchdogLimit.watch.allow(access, target), "watch expiry cooldown does not release early");
            access.now += 1L;
            require(watchdogLimit.watch.allow(access, target), "watch query exclusion has a ten-second maximum plus short cooldown");
        }
    }

    private static void checkSharedRequestBudget(File candidate) throws Exception {
        try (Harness h = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-shared-budget", 71, 1);
            h.onMap(access);
            FakeMonster first = h.confirmed(access, 133, 23, 7, 8, 30, 60);
            FakeMonster second = h.confirmed(access, 134, 24, 9, 10, 25, 55);

            trigger(h, access, first, 1000L); // first request at 3500
            trigger(h, access, second, 4000L); // global queue sends first ID's retry at 6500
            access.now = 6899L;
            h.cache.tick(access);
            require(access.requests.size() == 2, "shared request queue waits until 400 ms has elapsed");
            access.now = 6900L;
            h.cache.tick(access);
            require(access.requests.size() == 3, "shared queue serves the next watchdog after the 400 ms interval");
            require(access.requests.get(0).when == 3500L
                    && access.requests.get(1).when == 6500L
                    && access.requests.get(2).when == 6900L,
                    "watchdog reads share one global 400 ms request clock");
            int firstCount = 0;
            for (Request request : access.requests) if (request.id == first.id) firstCount++;
            require(firstCount <= 2, "each live target receives at most two detail requests");
            require(access.pendingCalls == 0 && access.hpWriteCalls == 0,
                    "shared health checks never invoke cache quarantine or HP writes");
        }
    }

    private static void checkHealthQueriesShareNormalRecoveryQueue(File candidate) throws Exception {
        try (Harness h = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-with-dead-queue", 72, 1);
            h.onMap(access);
            FakeMonster liveOne = h.confirmed(access, 135, 25, 7, 8, 30, 60);
            FakeMonster normalDead = h.confirmed(access, 136, 26, 9, 10, 20, 45);
            FakeMonster liveTwo = h.confirmed(access, 137, 27, 11, 12, 25, 50);
            normalDead.hp = 0;
            normalDead.dead = true;

            trigger(h, access, liveOne, 1000L); // request 1 at 3500
            access.now = 3900L;
            h.cache.tick(access); // request 2 for liveOne
            access.now = 5400L;
            h.cache.tick(access); // queues and requests normal dead recovery
            require(normalDead.pending, "ordinary dead-entity recovery keeps its native quarantine behavior");

            trigger(h, access, liveTwo, 6000L); // cache services normal retry first at 8500
            access.now = 8899L;
            h.cache.tick(access);
            require(access.requests.size() == 4, "watchdog query waits behind a queued normal dead retry");
            access.now = 8900L;
            h.cache.tick(access);
            require(access.requests.size() == 5, "shared request clock serves the watchdog after 400 ms");
            long previous = -1L;
            int firstCount = 0;
            int deadCount = 0;
            for (Request request : access.requests) {
                if (previous >= 0L) require(request.when - previous >= 400L,
                        "normal recovery and health queries share global 400 ms pacing");
                previous = request.when;
                if (request.id == liveOne.id) firstCount++;
                if (request.id == normalDead.id) deadCount++;
            }
            require(firstCount == 2 && deadCount == 2,
                    "both live watchdog and ordinary dead recovery respect the two-request per-ID cap");
            require(!liveOne.pending && !liveTwo.pending && access.pendingCalls == 1,
                    "only ordinary dead recovery receives pending(true); live health queries stay read-only");
            require(access.hpWriteCalls == 0, "queued health verification never writes live HP");
        }
    }

    private static void checkAutoGateAndIdentityScope(File candidate) throws Exception {
        try (Harness h = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-auto-off", 8, 1);
            h.onMap(access);
            FakeMonster target = h.confirmed(access, 140, 24, 1, 2, 20, 40);
            access.auto = false;
            access.now = 1000L; h.watch.onAttack(access, target.id);
            access.now = 5000L; h.watch.tick(access);
            require(access.requests.isEmpty() && h.watch.allow(access, target),
                    "manual mode bypasses the stale-target watchdog and exclusion");

            access.auto = true;
            FakeMonster pk = h.confirmed(access, 141, 900, 4, 5, 20, 40);
            pk.type = 2;
            access.now = 6000L; h.watch.onAttack(access, pk.id);
            access.now = 10000L; h.watch.tick(access);
            require(access.requests.isEmpty() && h.watch.allow(access, pk),
                    "PK target types bypass monster-only stale-target filtering");

            access.now = 11000L;
            FakeMonster impostor = new FakeMonster(target.id, target.template, target.x, target.y, false);
            access.target = impostor;
            h.watch.onAttack(access, target.id);
            access.now = 12000L; h.watch.onAttack(access, target.id);
            access.now = 13500L; h.watch.onAttack(access, target.id);
            h.watch.tick(access);
            h.cache.tick(access);
            require(access.target == impostor,
                    "same-ID replacement target identity is not cleared by an old watch reference");

            access.map = 9;
            access.now += 1L;
            h.onMap(access);
            require(h.watch.allow(access, target), "map transition clears prior-map object exclusions");
            FakeMonster sameIdNewZone = new FakeMonster(target.id, target.template, target.x, target.y, false);
            access.monsters.put(Integer.valueOf(target.id), sameIdNewZone);
            access.zone = 2;
            h.onMap(access);
            require(h.watch.allow(access, sameIdNewZone), "zone transition does not inherit an old object exclusion");
        }
    }

    private static void checkExclusionCapacity(File candidate) throws Exception {
        try (Harness h = new Harness(candidate)) {
            Field exclusions = h.watch.type.getDeclaredField("EXCLUSIONS");
            exclusions.setAccessible(true);
            Object slots = exclusions.get(null);
            require(slots != null && Array.getLength(slots) == 32,
                    "watch exclusions use a fixed bounded table of 32 entries");
        }
    }

    private static void checkUnseenCombatFallback(File candidate) throws Exception {
        try (Harness h = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-packet", 10, 1);
            h.onMap(access);
            FakeMonster first = h.confirmed(access, 200, 31, 10, 11, 50, 100);
            FakeMonster second = h.confirmed(access, 201, 32, 12, 13, 70, 120);
            FakeMonster player = h.confirmed(access, 202, 33, 14, 15, 40, 90);
            player.type = 2;

            DataInputStream valid = packet(new Victim(200, 12, 44, 2,
                    new int[]{3, 90, 5, 0}), new Victim(201, 9, 61, 1,
                    new int[]{7, 120}));
            h.cache.onUnseenCombat(access, valid);
            require(first.hp == 44 && second.hp == 61,
                    "fully parsed unseen combat payload applies HP for both confirmed known monsters");
            require(access.hpWriteCalls == 2 && access.hpWriteIds.get(0).intValue() == first.id
                    && access.hpWriteIds.get(1).intValue() == second.id,
                    "fallback writes exactly the two known monster identities after parsing effects and footer");
            require(valid.available() == 0, "valid fallback consumes both effects lists and the packet footer");

            access.hpWriteCalls = 0;
            access.hpWriteIds.clear();
            DataInputStream includesPlayer = packet(new Victim(200, 12, 43, 0, new int[0]),
                    new Victim(202, 8, 20, 0, new int[0]));
            h.cache.onUnseenCombat(access, includesPlayer);
            require(first.hp == 43 && player.hp == 40 && access.hpWriteCalls == 1,
                    "unseen fallback ignores a known object whose native type is PK/player");

            access.hpWriteCalls = 0;
            first.hp = 43;
            DataInputStream truncated = truncatedPacket(200);
            h.cache.onUnseenCombat(access, truncated);
            require(first.hp == 43 && access.hpWriteCalls == 0,
                    "truncated trailing victim data cannot partially mutate an earlier parsed victim");

            DataInputStream overLimit = packetPrefix(65);
            h.cache.onUnseenCombat(access, overLimit);
            require(first.hp == 43 && access.hpWriteCalls == 0,
                    "victim count above 64 is rejected before any HP update");

            DataInputStream invalidHealth = packet(new Victim(200, 1, -1, 0, new int[0]),
                    new Victim(201, 1, 12, 0, new int[0]));
            h.cache.onUnseenCombat(access, invalidHealth);
            require(first.hp == 43 && second.hp == 61 && access.hpWriteCalls == 0,
                    "negative victim HP rejects the whole frame before applying any target");

            DataInputStream invalidEffects = effectsOverflowPacket(200);
            h.cache.onUnseenCombat(access, invalidEffects);
            require(first.hp == 43 && access.hpWriteCalls == 0,
                    "effect count above 32 is rejected before applying the victim list");

            DataInputStream onlyPlayer = packet(new Victim(202, 1, 10, 0, new int[0]));
            h.cache.onUnseenCombat(access, onlyPlayer);
            require(player.hp == 40 && access.hpWriteCalls == 0,
                    "fallback never writes HP for a PK/player object");

            DataInputStream unknown = packet(new Victim(999, 1, 10, 0, new int[0]));
            h.cache.onUnseenCombat(access, unknown);
            require(access.hpWriteCalls == 0, "fallback ignores IDs without a confirmed current object");
        }
    }

    private static void checkRepeatedStallBackoff(File candidate) throws Exception {
        try (Harness h = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-repeat", 6, 1);
            h.onMap(access);
            FakeMonster target = h.confirmed(access, 121, 22, 1, 2, 35, 70);
            long[] delays = {3000L, 6000L, 12000L, 24000L, 30000L, 30000L};
            for (int cycle = 0; cycle < delays.length; cycle++) {
                trigger(h, access, target, access.now + 1000L);
                h.cache.onDetail(access, target.id, 1, target.x, target.y, target.hp, target.maxHp);
                long response = access.now;
                access.now = response + delays[cycle] - 1L;
                require(!h.watch.allow(access, target), "repeated unchanged HP increases cooldown " + cycle);
                access.now++;
                require(h.watch.allow(access, target), "repeated stall always allows a bounded retry " + cycle);
            }
            trigger(h, access, target, access.now + 1000L);
            h.cache.onDetail(access, target.id, 1, target.x, target.y, target.hp, target.maxHp);
            h.cache.onUnseenCombat(access, packet(new Victim(target.id, 6, 29, 0, new int[0])));
            require(h.watch.allow(access, target), "combat HP progress immediately releases a thirty-second exclusion");
            trigger(h, access, target, access.now + 1000L);
            h.cache.onDetail(access, target.id, 1, target.x, target.y, target.hp, target.maxHp);
            access.now += 3000L;
            require(h.watch.allow(access, target), "actual HP progress resets adaptive stall history");
        }
    }

    private static void checkCombatSnapshot(File candidate) throws Exception {
        try (Harness h = new Harness(candidate)) {
            FakeAccess access = new FakeAccess("watch-snapshot", 6, 1);
            h.onMap(access);
            FakeMonster target = h.confirmed(access, 122, 22, 1, 2, 35, 70);
            DataInputStream victims = packet(new Victim(target.id, 35, 0, 0, new int[0]));
            byte[] bytes = new byte[victims.available() + 2]; bytes[0] = 3; bytes[1] = 9;
            victims.readFully(bytes, 2, bytes.length - 2);
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            h.cache.snapshot(access, input);
            require(target.hp == 0, "entry observer applies death HP independently of attacker animation");
            require(input.available() == bytes.length && input.readUnsignedShort() == 777,
                    "snapshot restores the exact native stream position");
            target.hp = 35;
            input = new DataInputStream(new ByteArrayInputStream(java.util.Arrays.copyOf(bytes, bytes.length - 1)));
            int before = input.available(); h.cache.snapshot(access, input);
            require(target.hp == 35 && input.available() == before,
                    "truncated snapshot preserves native stream and applies no partial HP list");
        }
    }

    private static void trigger(Harness h, FakeAccess access, FakeMonster target, long firstSend)
            throws Exception {
        access.now = firstSend; h.watch.onAttack(access, target.id);
        access.now = firstSend + 1250L; h.watch.onAttack(access, target.id);
        access.now = firstSend + 2500L; h.watch.onAttack(access, target.id);
        h.watch.tick(access);
        h.cache.tick(access);
        require(!access.requests.isEmpty(), "fixture setup triggered one stale-target verification");
    }

    private static void triggerWithoutCachePoll(Harness h, FakeAccess access,
            FakeMonster target, long firstSend) throws Exception {
        access.now = firstSend; h.watch.onAttack(access, target.id);
        access.now = firstSend + 1250L; h.watch.onAttack(access, target.id);
        access.now = firstSend + 2500L; h.watch.onAttack(access, target.id);
        h.watch.tick(access);
        require(!h.watch.allow(access, target), "fixture setup created the ten-second watch exclusion");
    }

    private static DataInputStream packet(Victim... victims) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(19); // skill
        out.writeByte(victims.length);
        for (Victim victim : victims) writeVictim(out, victim.id, victim.damage,
                victim.hp, victim.effects, victim.effectData);
        out.writeInt(1234); // attacker HP
        out.writeInt(567); // attacker MP
        out.writeByte(2); // auxiliary type
        out.writeInt(8); // auxiliary damage
        out.flush();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static DataInputStream packetPrefix(int count) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1);
        out.writeByte(count);
        out.flush();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static DataInputStream truncatedPacket(int firstId) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1);
        out.writeByte(2);
        writeVictim(out, firstId, 1, 10, 0, new int[0]);
        out.writeShort(201);
        out.writeInt(4);
        out.writeInt(20);
        out.writeByte(1);
        out.writeByte(6); // the effect value and footer are missing
        out.flush();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static DataInputStream effectsOverflowPacket(int id) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1);
        out.writeByte(1);
        out.writeShort(id);
        out.writeInt(1);
        out.writeInt(10);
        out.writeByte(33);
        out.flush();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static void writeVictim(DataOutputStream out, int id, int damage, int hp,
            int effects, int[] effectData) throws Exception {
        out.writeShort(id);
        out.writeInt(damage);
        out.writeInt(hp);
        out.writeByte(effects);
        for (int i = 0; i < effectData.length; i += 2) {
            out.writeByte(effectData[i]);
            out.writeInt(effectData[i + 1]);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static final class Harness implements AutoCloseable {
        private static final Class<?>[] ACCESS = new Class<?>[]{MonsterCacheAccess.class};
        private final ChildFirstLoader loader;
        final Watch watch;
        final Cache cache;

        Harness(File candidate) throws Exception {
            loader = new ChildFirstLoader(candidate);
            watch = new Watch(loader.loadClass("luna.features.MonsterTargetWatchSupport"));
            cache = new Cache(loader.loadClass("luna.features.MonsterCacheSupport"));
        }

        void onMap(FakeAccess access) throws Exception {
            cache.onMap(access);
            watch.onMap(access);
        }

        FakeMonster confirmed(FakeAccess access, int id, int template, int x, int y,
                int hp, int maxHp) throws Exception {
            FakeMonster monster = new FakeMonster(id, template, x, y, false);
            monster.hp = hp;
            monster.maxHp = maxHp;
            access.monsters.put(Integer.valueOf(id), monster);
            cache.onObject(access, 1, template, id, x, y, 1);
            cache.onDetail(access, id, 1, x, y, hp, maxHp);
            return monster;
        }

        public void close() throws Exception { loader.close(); }
    }

    private static final class Watch {
        final Class<?> type;
        private final Method onMap;
        private final Method onAttack;
        private final Method tick;
        private final Method allow;
        private final Method onDetail;

        Watch(Class<?> type) throws Exception {
            this.type = type;
            onMap = type.getMethod("onMap", MonsterCacheAccess.class);
            onAttack = type.getMethod("onAttack", MonsterCacheAccess.class, int.class);
            tick = type.getMethod("tick", MonsterCacheAccess.class);
            allow = type.getMethod("allow", MonsterCacheAccess.class, Object.class);
            onDetail = type.getMethod("onDetail", MonsterCacheAccess.class, int.class,
                    Object.class, boolean.class, int.class);
            Method reset = type.getMethod("resetForTest");
            reset.invoke(null);
        }

        void onMap(MonsterCacheAccess a) throws Exception { invoke(onMap, a); }
        void onAttack(MonsterCacheAccess a, int id) throws Exception { invoke(onAttack, a, Integer.valueOf(id)); }
        void tick(MonsterCacheAccess a) throws Exception { invoke(tick, a); }
        void onDetail(MonsterCacheAccess a, int id, Object o, boolean valid, int hp) throws Exception {
            invoke(onDetail, a, Integer.valueOf(id), o, Boolean.valueOf(valid), Integer.valueOf(hp));
        }
        boolean allow(MonsterCacheAccess a, Object o) throws Exception {
            Object result = allow.invoke(null, a, o);
            if (!(result instanceof Boolean)) throw new AssertionError("allow() did not return boolean");
            return ((Boolean)result).booleanValue();
        }
    }

    private static final class Cache {
        private final Method onMap;
        private final Method onObject;
        private final Method onDetail;
        private final Method tick;
        private final Method onUnseenCombat;
        private final Method snapshot;

        Cache(Class<?> type) throws Exception {
            onMap = type.getMethod("onMap", MonsterCacheAccess.class);
            onObject = type.getMethod("onObject", MonsterCacheAccess.class,
                    int.class, int.class, int.class, int.class, int.class, int.class);
            onDetail = type.getMethod("onDetail", MonsterCacheAccess.class,
                    int.class, int.class, int.class, int.class, int.class, int.class);
            tick = type.getMethod("tick", MonsterCacheAccess.class);
            onUnseenCombat = type.getMethod("onUnseenCombat", MonsterCacheAccess.class,
                    DataInputStream.class);
            snapshot = type.getMethod("onCombatSnapshot", MonsterCacheAccess.class, DataInputStream.class);
        }

        void onMap(MonsterCacheAccess a) throws Exception { invoke(onMap, a); }
        void onObject(MonsterCacheAccess a, int type, int template, int id,
                int x, int y, int state) throws Exception {
            invoke(onObject, a, Integer.valueOf(type), Integer.valueOf(template), Integer.valueOf(id),
                    Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(state));
        }
        void onDetail(MonsterCacheAccess a, int id, int level, int x, int y, int hp, int maxHp)
                throws Exception {
            invoke(onDetail, a, Integer.valueOf(id), Integer.valueOf(level), Integer.valueOf(x),
                    Integer.valueOf(y), Integer.valueOf(hp), Integer.valueOf(maxHp));
        }
        void tick(MonsterCacheAccess a) throws Exception { invoke(tick, a); }
        void onUnseenCombat(MonsterCacheAccess a, DataInputStream in) throws Exception {
            invoke(onUnseenCombat, a, in);
        }
        void snapshot(MonsterCacheAccess a, DataInputStream in) throws Exception { invoke(snapshot, a, in); }
    }

    private static final class ChildFirstLoader extends URLClassLoader {
        ChildFirstLoader(File candidate) throws Exception {
            super(new URL[]{candidate.toURI().toURL()}, HungBySonStaleTargetHostTest.class.getClassLoader());
        }

        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("luna.features.MonsterTargetWatchSupport")
                    && !name.startsWith("luna.features.MonsterCacheSupport"))
                return super.loadClass(name, resolve);
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try { loaded = findClass(name); }
                catch (ClassNotFoundException missing) { return super.loadClass(name, resolve); }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    private static final class FakeAccess implements MonsterCacheAccess {
        long now;
        final String server;
        int map;
        int zone;
        boolean ready = true;
        boolean auto = true;
        Object target;
        Object lastCleared;
        int clearTargetCalls;
        int pendingCalls;
        int hpWriteCalls;
        final List<Integer> hpWriteIds = new ArrayList<Integer>();
        final Map<Integer, FakeMonster> monsters = new HashMap<Integer, FakeMonster>();
        final List<Request> requests = new ArrayList<Request>();
        final FakeStore store = new FakeStore();

        FakeAccess(String server, int map, int zone) {
            this.server = server;
            this.map = map;
            this.zone = zone;
        }

        public long now() { return now; }
        public String serverKey() { return server; }
        public int mapId() { return map; }
        public int zoneId() { return zone; }
        public boolean ready() { return ready; }
        public Object find(int id) { return monsters.get(Integer.valueOf(id)); }
        public boolean dead(Object object) { return !(object instanceof FakeMonster)
                || ((FakeMonster)object).dead || ((FakeMonster)object).hp <= 0; }
        public boolean hidden(Object object) { return object instanceof FakeMonster
                && ((FakeMonster)object).hidden; }
        public boolean retiring(Object object) { return false; }
        public void corpse(Object object, boolean advance) {
            FakeMonster monster = (FakeMonster)object;
            monster.hp = 0; monster.dead = true; monster.hidden = false; monster.pending = true;
        }
        public int hp(Object object) { return object instanceof FakeMonster ? ((FakeMonster)object).hp : 0; }
        public int id(Object object) { return object instanceof FakeMonster ? ((FakeMonster)object).id : -1; }
        public int type(Object object) { return object instanceof FakeMonster ? ((FakeMonster)object).type : -1; }
        public boolean autoEnabled() { return auto; }
        public void clearAutoTarget(Object object) {
            clearTargetCalls++;
            lastCleared = object;
            if (target == object) target = null;
        }
        public boolean applyHp(Object object, int value) {
            if (!(object instanceof FakeMonster)) return false;
            FakeMonster monster = (FakeMonster)object;
            if (find(monster.id) != monster || monster.type != 1 || value < 0 || value > monster.maxHp)
                return false;
            monster.hp = value;
            hpWriteCalls++;
            hpWriteIds.add(Integer.valueOf(monster.id));
            return true;
        }
        public Object restore(int template, int id, int x, int y) {
            FakeMonster monster = new FakeMonster(id, template, x, y, true);
            monsters.put(Integer.valueOf(id), monster);
            return monster;
        }
        public void pending(Object object, boolean value) {
            pendingCalls++;
            if (object instanceof FakeMonster) ((FakeMonster)object).pending = value;
        }
        public void discard(Object object) { }
        public void request(int id) { requests.add(new Request(id, now)); }
        public byte[] load() { return store.bytes == null ? null : store.bytes.clone(); }
        public boolean save(byte[] data) { store.bytes = data == null ? null : data.clone(); return true; }
        public void hud(Object graphics, String status) { }
    }

    private static final class FakeMonster {
        final int id;
        final int template;
        final int x;
        final int y;
        final boolean placeholder;
        int type = 1;
        int hp = 1;
        int maxHp = 1;
        boolean pending;
        boolean dead;
        boolean hidden;

        FakeMonster(int id, int template, int x, int y, boolean placeholder) {
            this.id = id;
            this.template = template;
            this.x = x;
            this.y = y;
            this.placeholder = placeholder;
        }
    }

    private static final class FakeStore { byte[] bytes; }

    private static final class Request {
        final int id;
        final long when;
        Request(int id, long when) { this.id = id; this.when = when; }
    }

    private static final class Victim {
        final int id;
        final int damage;
        final int hp;
        final int effects;
        final int[] effectData;
        Victim(int id, int damage, int hp, int effects, int[] effectData) {
            this.id = id; this.damage = damage; this.hp = hp;
            this.effects = effects; this.effectData = effectData;
        }
    }

    private static void invoke(Method method, Object access, Object... rest) throws Exception {
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
}
