import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Deterministic 60-second A/B trace of Special's monster attack loop on the pinned E8 release
 * and its Auto PK candidate. This exercises client enqueue/cooldown/MP behavior only, not server
 * hit rate. Each run uses a separate isolated native-probe classloader and the same simulated
 * real-time cadence.
 */
public final class HungBySonAutoPkCadenceTest {
    private static final String AUTO_PK = "HungBySonAutoPkBinaryTest";
    private static final String COPY = "HungBySonCopySpecialBinaryTest";
    private static final String NATIVE = "HungBySonAutoAttackBinaryTest";
    private static final String INPUT = "E8A95AEBF84055D9137276792C7BD4D6E2468B21FF51A8B3C48E57CD8ACC2CFA";
    private static final int[] SKILLS = {0, 1, 2, 7, 8};
    private static final int[] STEPS = {10, 16, 28, 40, 80};
    private static final long DURATION_MS = 60000L;
    private static int checks;

    private HungBySonAutoPkCadenceTest() { }

    private static void check(boolean ok, String text) {
        checks++;
        if (!ok) throw new AssertionError(text);
    }

    private static String pad(String suffix) {
        StringBuilder value = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) value.append('i');
        return value.append(suffix).toString();
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field result = cursor.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method result = type.getDeclaredMethod(name, parameters);
        result.setAccessible(true);
        return result;
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) throws Exception {
        try { return method.invoke(receiver, arguments); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new IllegalStateException(cause);
        }
    }

    private static void setNumber(Object target, Class<?> owner, String name, long value) throws Exception {
        Field member = field(owner, name);
        Class<?> type = member.getType();
        if (type == Byte.TYPE) member.setByte(target, (byte)value);
        else if (type == Short.TYPE) member.setShort(target, (short)value);
        else if (type == Integer.TYPE) member.setInt(target, (int)value);
        else if (type == Long.TYPE) member.setLong(target, value);
        else throw new IllegalArgumentException(member.toString());
    }

    private static long number(Object target, Class<?> owner, String name) throws Exception {
        return ((Number)field(owner, name).get(target)).longValue();
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
        } finally { input.close(); }
        StringBuilder value = new StringBuilder();
        for (byte part : digest.digest()) value.append(String.format("%02X", part & 255));
        return value.toString();
    }

    private static Properties load(File file) throws Exception {
        Properties result = new Properties();
        InputStream input = new FileInputStream(file);
        try { result.load(input); } finally { input.close(); }
        return result;
    }

    private static String attack(int opcode, int skill, int id) {
        return new StringBuilder().append(opcode).append(':').append('[')
                .append((byte)skill).append(", 1, ").append((byte)(id >> 8)).append(", ")
                .append((byte)id).append(']').toString();
    }

    private static Object callStatic(Class<?> owner, String name, Class<?>[] parameters, Object... values)
            throws Exception {
        return invoke(method(owner, name, parameters), null, values);
    }

    private static final class Result {
        final List<String> packets;
        final List<String> states;
        final int[] casts;
        final long mana;
        final long[] cooldowns;
        Result(List<String> packets, List<String> states, int[] casts, long mana, long[] cooldowns) {
            this.packets = packets; this.states = states; this.casts = casts;
            this.mana = mana; this.cooldowns = cooldowns;
        }
    }

    private static Result simulate(File jar, Properties auto, int paceMode) throws Exception {
        Class<?> autoPk = Class.forName(AUTO_PK);
        Class<?> nativeProbe = Class.forName(NATIVE + "$NativeProbeLoader");
        Constructor<?> constructor = nativeProbe.getDeclaredConstructor(File.class, Properties.class);
        constructor.setAccessible(true);
        URLClassLoader loader = (URLClassLoader)constructor.newInstance(jar, auto);
        try {
            field(autoPk, "loader").set(null, loader);
            Class<?> copy = Class.forName(COPY);
            field(copy, "loader").set(null, loader);
            callStatic(autoPk, "setupRuntime", new Class<?>[]{Properties.class}, auto);

            Object player = field(autoPk, "player").get(null);
            Class<?> entityType = (Class<?>)field(autoPk, "entityType").get(null);
            Method makeMonster = method(autoPk, "monster", Integer.TYPE, Integer.TYPE, Integer.TYPE);
            Object monster = invoke(makeMonster, null, Integer.valueOf(500), Integer.valueOf(100), Integer.valueOf(0));
            Object[] world = new Object[]{player, monster};
            invoke(method(autoPk, "replaceWorld", Object[].class), null, (Object)world);
            installCadenceSkills(autoPk, copy);
            // Exercise both the unpaced donor path and the shipped 575ms lock with retry.
            Class<?> counters=loader.loadClass("luna.features.SkillCastStats");
            counters.getMethod("setPaceMode",Integer.TYPE).invoke(null,Integer.valueOf(paceMode));
            counters.getMethod("setRetry",Boolean.TYPE).invoke(null,Boolean.valueOf(paceMode!=0));
            setNumber(player,entityType,"bu",1000000L);
            callStatic(autoPk, "invokeAction", new Class<?>[]{Integer.TYPE}, Integer.valueOf(-116));

            @SuppressWarnings("unchecked")
            List<String> pending = (List<String>)field(Class.forName(NATIVE), "nativePackets").get(null);
            synchronized (pending) { pending.clear(); }

            Object port = field(autoPk, "port").get(null);
            Method setTime = method(autoPk, "frame", Long.TYPE);
            Method drain = method(autoPk, "drainPackets");
            Method cooldown = method(autoPk, "cooldown", Integer.TYPE);
            Method casts = method(autoPk, "casts", Integer.TYPE);
            Method setLong = method(autoPk, "setNumber", Object.class, Class.class, String.class, Long.TYPE);
            Method getNumber = method(autoPk, "number", Object.class, Class.class, String.class);

            List<String> packetTrace = new ArrayList<String>();
            List<String> stateTrace = new ArrayList<String>();
            long start = 10000000L;
            long now = start;
            long end = start + DURATION_MS;
            int stepIndex = 0;
            while (now < end) {
                long next = Math.min(end, now + STEPS[stepIndex++ % STEPS.length]);
                long delta = next - now;
                for (int skill : SKILLS) {
                    Object cd = invoke(cooldown, null, Integer.valueOf(skill));
                    long b = ((Number)invoke(getNumber, null, cd, cd.getClass(), "b")).longValue();
                    long c = ((Number)invoke(getNumber, null, cd, cd.getClass(), "c")).longValue();
                    invoke(setLong, null, cd, cd.getClass(), "b", Long.valueOf(Math.max(0L, b - delta)));
                    invoke(setLong, null, cd, cd.getClass(), "c", Long.valueOf(Math.max(0L, c - delta)));
                }
                invoke(setTime, null, Long.valueOf(next));
                // frame(long) already invokes the candidate's port.frame() after setting its deterministic clock.
                List<?> sent = (List<?>)invoke(drain, null);
                for (Object item : sent) {
                    String packet = String.valueOf(item);
                    boolean expectedMonster = false;
                    for (int skill : SKILLS) if (attack(9, skill, 500).equals(packet)) expectedMonster = true;
                    check(expectedMonster, "only the fixture monster is emitted: " + packet);
                    packetTrace.add((next - start) + ":" + packet);
                }
                StringBuilder state = new StringBuilder().append(next - start).append(':')
                        .append(number(player, entityType, "bu"));
                for (int skill : SKILLS) {
                    Object cd = invoke(cooldown, null, Integer.valueOf(skill));
                    state.append(':').append(number(cd, cd.getClass(), "b"))
                            .append('/').append(number(cd, cd.getClass(), "c"));
                }
                stateTrace.add(state.toString());
                now = next;
            }

            int[] castCounts = new int[SKILLS.length];
            long[] finalCooldowns = new long[SKILLS.length * 2];
            for (int i = 0; i < SKILLS.length; i++) {
                int skill = SKILLS[i];
                castCounts[i] = ((Integer)invoke(casts, null, Integer.valueOf(skill))).intValue();
                Object cd = invoke(cooldown, null, Integer.valueOf(skill));
                finalCooldowns[i * 2] = number(cd, cd.getClass(), "b");
                finalCooldowns[i * 2 + 1] = number(cd, cd.getClass(), "c");
            }
            long mana = number(player, entityType, "bu");
            check(!packetTrace.isEmpty(), "60-second simulation produced monster attacks for " + jar.getName());
            for (int i = 0; i < SKILLS.length; i++)
                if (paceMode==0 || SKILLS[i]==0 || SKILLS[i]==8)
                    check(castCounts[i] > 0, "skill " + SKILLS[i] + " participated in the cadence trace for " + jar.getName());
            return new Result(packetTrace, stateTrace, castCounts, mana, finalCooldowns);
        } finally {
            try { loader.close(); } finally {
                field(autoPk, "loader").set(null, null);
                field(Class.forName(COPY), "loader").set(null, null);
            }
        }
    }

    private static void installCadenceSkills(Class<?> autoPk, Class<?> copy) throws Exception {
        Method makeSkill = method(copy, "skill", Integer.TYPE, Integer.TYPE, String.class, int[][].class);
        Object skillList = callStatic(autoPk, "newList", new Class<?>[]{String.class}, "Cadence skill fixture");
        Method add = skillList.getClass().getMethod(pad("l"), Object.class);
        int[][] rows = {{3, 5, 700}, {4, 5, 800}, {5, 5, 900}, {8, 5, 1000}, {10, 5, 1200}};
        String[] names = {"skill-0", "skill-1", "skill-2", "skill-7", "skill-8"};
        for (int i = 0; i < SKILLS.length; i++) {
            Object created = invoke(makeSkill, null, Integer.valueOf(SKILLS[i]), Integer.valueOf(0), names[i], (Object)new int[][]{rows[i]});
            add.invoke(skillList, created);
        }
        Class<?> skillOwner = (Class<?>)callStatic(autoPk, "cls", new Class<?>[]{String.class}, pad("lillilll"));
        callStatic(autoPk, "setStatic", new Class<?>[]{Class.class, String.class, Object.class}, skillOwner, "a", skillList);
        Class<?> learnedOwner = (Class<?>)callStatic(autoPk, "cls", new Class<?>[]{String.class}, pad("lliiil"));
        int[] learned = new int[21], bonus = new int[21];
        for (int skill : SKILLS) learned[skill] = 1;
        callStatic(autoPk, "setStatic", new Class<?>[]{Class.class, String.class, Object.class}, learnedOwner, pad("llilllii"), learned);
        callStatic(autoPk, "setStatic", new Class<?>[]{Class.class, String.class, Object.class}, learnedOwner, pad("llilllil"), bonus);
        for (int skill : SKILLS) {
            Object cd = callStatic(autoPk, "cooldown", new Class<?>[]{Integer.TYPE}, Integer.valueOf(skill));
            setNumber(cd, cd.getClass(), "b", 0L);
            setNumber(cd, cd.getClass(), "c", 0L);
            field(cd.getClass(), "d").setBoolean(cd, false);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("baseline.jar candidate.jar auto-attack.properties");
        File baseline = new File(args[0]), candidate = new File(args[1]), catalogFile = new File(args[2]);
        if (!baseline.isFile() || !candidate.isFile() || !catalogFile.isFile())
            throw new IllegalArgumentException("Baseline, candidate and catalog must exist");
        Properties auto = load(catalogFile);
        check(INPUT.equals(hash(baseline)), "baseline is the pinned E8 release");
        check(hash(candidate).equals(auto.getProperty("output.sha256")), "candidate matches the rebound Auto Attack catalog");
        check("true".equals(auto.getProperty("auto.pk.upgrade")), "candidate catalog identifies the Auto PK upgrade");

        compare(baseline,candidate,auto,0);
        compare(baseline,candidate,auto,4);
        System.out.println("HungBySonAutoPkCadenceTest PASS checks="+checks+"; 60s per mode, pacing OFF and default lock575/retry, irregular 10/16/28/40/80ms frames, no server hit-rate claim");
    }

    private static void compare(File baseline,File candidate,Properties auto,int paceMode)throws Exception {
        Result before = simulate(baseline, auto, paceMode);
        Result after = simulate(candidate, auto, paceMode);
        check(before.packets.equals(after.packets), "60s monster packet traces match exactly by timestamp, skill and targets");
        check(before.states.equals(after.states), "60s per-frame MP and all cooldown states match exactly");
        check(Arrays.equals(before.casts, after.casts), "per-skill cast counts match");
        check(before.mana == after.mana, "final MP matches");
        check(Arrays.equals(before.cooldowns, after.cooldowns), "final cooldowns match");
        System.out.println("CADENCE_MATCH paceMode="+paceMode+" packets=" + before.packets.size()
                + " casts=" + Arrays.toString(before.casts) + " finalMP=" + before.mana
                + " finalCooldowns=" + Arrays.toString(before.cooldowns)
                + "; full 60s with sufficient MP");
    }
}
