import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;

/**
 * Packet 12 clears the object-receive flag, then calls the native map reset on the receiver
 * thread. The game thread's world update removes every cL actor it sees, so the list shrinks under
 * the reset. On the input, et.a throws or returns null, packet 12 stops before restoring the flag,
 * and all NPC / zone-board / teleport records are dropped. The candidate must neither throw nor
 * leave an old-map actor unmarked, and its world update must remove only the actor it inspected.
 *
 * The native reset and list classes run from each JAR. The game thread replays world update
 * offsets 341-456 (skip null, remove cL) through each JAR's own list methods.
 *
 * Usage: <input.jar> <candidate.jar>
 */
public final class HungBySonMapResetRaceTest {
    private static final String HELPER = "HungBySonAutoAttackBinaryTest";
    private static final int[] CROWDS = {5, 15, 40, 100};
    private static final int TRIALS = 1500;
    private static int checks;
    private static volatile boolean stop;

    private HungBySonMapResetRaceTest() { }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }

    private static String encoded(String suffix) {
        StringBuilder value = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) value.append('i');
        return value.append(suffix).toString();
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 2, "<input.jar> <candidate.jar>");
        Probe input = new Probe(new File(args[0]), false);
        Probe candidate = new Probe(new File(args[1]), true);
        try {
            verifyListSemantics(candidate);
            verifyResetExemptionsAndNullSlot(input, candidate);
            int inputFailures = 0;
            for (int crowd : CROWDS) {
                Race old = race(input, crowd);
                Race now = race(candidate, crowd);
                System.out.println("RACE actors=" + crowd + " trials=" + TRIALS + " input=" + old + " candidate=" + now);
                inputFailures += old.failures + old.unmarked;
                require(now.failures == 0, "Candidate reset never throws while the world removes (" + crowd + " actors)");
                require(now.unmarked == 0, "Candidate reset marks every old-map actor (" + crowd + " actors)");
                require(now.overlaps > TRIALS / 2, "Candidate race really overlapped the world loop (" + crowd + " actors)");
            }
            require(inputFailures > 0, "Input reproduces the dropped/unmarked reset under the same overlap");
            benchmark(input, candidate);
        } finally {
            input.close();
            candidate.close();
        }
        System.out.println("HungBySonMapResetRaceTest PASS checks=" + checks
                + "; native reset vs world removal, atomic list access, identity removal");
    }

    private static void verifyListSemantics(Probe p) throws Exception {
        Object list = p.newList();
        Object a = new Object(), b = new Object(), c = new Object();
        p.add(list, a); p.add(list, b); p.add(list, c);
        require(p.get(list, -1) == null && p.get(list, 3) == null && p.get(list, 1) == b,
                "et.a keeps native results: out of range is null, in range is the element");
        p.removeAt(list, -1); p.removeAt(list, 3);
        require(p.size(list) == 3, "et index removal out of range is a no-op");
        p.setAt(list, a, 7);
        require(p.get(list, 0) == a && p.size(list) == 3, "et index set out of range is a no-op");
        p.setAt(list, c, 0);
        require(p.get(list, 0) == c, "et index set in range still writes");
        p.setAt(list, a, 0);

        // Receiver removed a; world still holds index 1 for b. Native removeAt(1) would drop c.
        p.items(list).removeElement(a);
        p.removeSame(list, 1, b);
        require(p.size(list) == 1 && p.get(list, 0) == c, "Identity removal deletes the inspected actor, not the one that shifted into its slot");
        p.removeSame(list, 0, b);
        require(p.size(list) == 1 && p.get(list, 0) == c, "Identity removal of an already removed actor changes nothing");
        p.items(list).addElement(null);
        p.removeSame(list, 1, null);
        require(p.size(list) == 1, "A null slot that is still null is removed like native");
        p.items(list).insertElementAt(null, 0);
        p.removeSame(list, 1, null);
        require(p.size(list) == 2 && p.get(list, 1) == c, "A null slot that shifted away removes nothing");

        Object[] snapshot = p.snapshot(list);
        require(snapshot.length == 2 && snapshot[0] == null && snapshot[1] == c, "Snapshot copies the list under its lock");
    }

    private static void verifyResetExemptionsAndNullSlot(Probe input, Probe candidate) throws Exception {
        for (Probe p : new Probe[]{input, candidate}) {
            Object list = p.newList();
            Object pet = p.newPet();
            p.setPet(pet);
            p.add(list, p.player); p.add(list, pet);
            Object[] actors = new Object[8];
            for (int i = 0; i < actors.length; i++) { actors[i] = p.newActor(100 + i); p.add(list, actors[i]); }
            p.items(list).insertElementAt(null, 3);
            p.setEntities(list);
            Throwable failure = p.reset();
            if (!p.candidate) {
                require(failure instanceof NullPointerException, "Input reset dies on a null slot (reference)");
                p.setPet(null);
                continue;
            }
            require(failure == null, "Candidate reset skips a null slot");
            boolean all = true;
            for (Object actor : actors) all &= p.cL(actor) && p.previousMap(actor);
            require(all, "Candidate reset marks every old-map actor with cL and the previous-map marker");
            require(!p.cL(p.player) && !p.cL(pet) && !p.previousMap(pet), "Local player and pet stay exempt");
            p.setPet(null);
        }
    }

    private static final class Race {
        int failures, unmarked, overlaps;
        public String toString() { return "failures=" + failures + " unmarked=" + unmarked + " overlaps=" + overlaps; }
    }

    private static Race race(final Probe p, int crowd) throws Exception {
        Race result = new Race();
        for (int t = 0; t < TRIALS; t++) {
            final Object list = p.newList();
            p.add(list, p.player);
            final Object[] actors = new Object[crowd];
            for (int i = 0; i < crowd; i++) { actors[i] = p.newActor(1000 + i); p.add(list, actors[i]); }
            p.setEntities(list);
            stop = false;
            final CountDownLatch running = new CountDownLatch(1);
            final int[] removed = new int[1];
            Thread world = new Thread(new Runnable() {
                public void run() {
                    try {
                        running.countDown();
                        while (!stop) {
                            for (int i = 0; i < p.size(list); i++) {
                                Object actor = p.get(list, i);
                                if (actor != null && !p.cL(actor)) continue;
                                if (p.candidate) p.removeSame(list, i, actor); else p.removeAt(list, i);
                                removed[0]++;
                                i--;
                            }
                        }
                    } catch (Throwable ignored) { }
                }
            }, "world-update");
            world.start();
            running.await();
            for (int spin = (t * 7919) % 20000; spin > 0; spin--) Thread.onSpinWait();
            Throwable failure = p.reset();
            stop = true;
            world.join();
            if (failure != null) result.failures++;
            if (removed[0] > 0) result.overlaps++;
            for (Object actor : actors) if (!p.cL(actor)) { result.unmarked++; break; }
        }
        return result;
    }

    /** Informational: one list lock per et.a instead of two. Not a server or device frame-rate claim. */
    private static void benchmark(Probe input, Probe candidate) throws Exception {
        long[] ns = new long[2];
        for (int round = 0; round < 3; round++) {
            Probe[] probes = {input, candidate};
            for (int k = 0; k < 2; k++) {
                Probe p = probes[k];
                Object list = p.newList();
                for (int i = 0; i < 40; i++) p.add(list, p.newActor(i));
                Method get = p.get;
                long start = System.nanoTime();
                int found = 0;
                for (int n = 0; n < 50000; n++) for (int i = 0; i < 40; i++) if (get.invoke(list, i) != null) found++;
                ns[k] = System.nanoTime() - start;
                if (found != 50000 * 40) throw new AssertionError("benchmark lookups");
            }
        }
        System.out.println("BENCH 40 actors x 50000 scans via reflection: input=" + ns[0] / 1000000 + "ms candidate="
                + ns[1] / 1000000 + "ms (informational)");
    }

    private static final class Probe {
        final boolean candidate;
        final ClassLoader loader;
        final Class<?> list, entity, world, placeholder;
        final Method size, get, add, removeAt, setAt, removeSame, snapshot, reset;
        final Field items, cL, previousMap, entities, pet;
        final Constructor<?> actor;
        final Object player;

        Probe(File jar, boolean candidate) throws Exception {
            this.candidate = candidate;
            Class<?> helper = Class.forName(HELPER);
            Properties inert = new Properties();
            for (String key : new String[]{"native.menu.display.owner", "native.menu.display.method",
                    "native.menu.callback.owner", "native.hpmp.owner", "native.hpmp.method"}) inert.setProperty(key, "unused");
            inert.setProperty("native.menu.display.descriptor", "()V");
            inert.setProperty("native.menu.callback.descriptor", "()V");
            inert.setProperty("native.hpmp.descriptor", "()V");
            Constructor<?> probe = null;
            for (Class<?> nested : helper.getDeclaredClasses())
                if (nested.getSimpleName().equals("NativeProbeLoader")) probe = nested.getDeclaredConstructor(File.class, Properties.class);
            if (probe == null) throw new NoSuchMethodException("NativeProbeLoader(File, Properties)");
            probe.setAccessible(true);
            loader = (ClassLoader)probe.newInstance(jar, inert);
            list = loader.loadClass("et");
            entity = loader.loadClass(encoded("liiiliii"));
            world = loader.loadClass(encoded("liiliii"));
            placeholder = loader.loadClass(encoded("lillil"));
            size = list.getMethod("c");
            get = list.getMethod("a", int.class);
            add = list.getMethod(encoded("l"), Object.class);
            removeAt = list.getMethod(encoded("lilli"), int.class);
            setAt = list.getMethod(encoded(""), Object.class, int.class);
            removeSame = candidate ? list.getMethod("codexRemoveSame", Vector.class, int.class, Object.class) : null;
            snapshot = candidate ? list.getMethod("codexSnapshot", Vector.class) : null;
            items = field(list, encoded("liiiiill"));
            cL = field(entity, "cL");
            previousMap = field(entity, "codexPreviousMap");
            entities = field(world, encoded("llillilll"));
            pet = field(world, encoded("llillilii"));
            reset = world.getDeclaredMethod(encoded("liiil"));
            reset.setAccessible(true);
            actor = placeholder.getDeclaredConstructor(int.class, byte.class, String.class, int.class, int.class);
            actor.setAccessible(true);
            // The reset clears several other lists; give each an empty native list.
            for (Class<?> owner : new Class<?>[]{world, loader.loadClass(encoded("lliili")), loader.loadClass(encoded("liiiilil"))}) {
                for (Field f : owner.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) || !list.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    if (f.get(null) == null) { Object empty = allocate(f.getType()); items.set(empty, new Vector()); f.set(null, empty); }
                }
            }
            player = allocate(loader.loadClass(encoded("lliiil")));
            field(world, encoded("llilliill")).set(null, player);
        }

        Object newList() throws Exception { return list.getDeclaredConstructor().newInstance(); }
        Object newPet() throws Exception { return allocate(pet.getType()); }
        Object newActor(int id) throws Exception { return actor.newInstance(id, (byte)0, "", 24 * id, 24); }
        Vector items(Object l) throws Exception { return (Vector)items.get(l); }
        int size(Object l) throws Exception { return ((Integer)size.invoke(l)).intValue(); }
        Object get(Object l, int i) throws Exception { return get.invoke(l, i); }
        void add(Object l, Object v) throws Exception { add.invoke(l, v); }
        void removeAt(Object l, int i) throws Exception { removeAt.invoke(l, i); }
        void setAt(Object l, Object v, int i) throws Exception { setAt.invoke(l, v, i); }
        void removeSame(Object l, int i, Object v) throws Exception { removeSame.invoke(null, items(l), i, v); }
        Object[] snapshot(Object l) throws Exception { return (Object[])snapshot.invoke(null, items(l)); }
        boolean cL(Object a) throws Exception { return cL.getBoolean(a); }
        boolean previousMap(Object a) throws Exception { return previousMap.getBoolean(a); }
        void setEntities(Object l) throws Exception { entities.set(null, l); }
        void setPet(Object a) throws Exception { pet.set(null, a); }
        Throwable reset() throws Exception {
            try { reset.invoke(null); return null; }
            catch (InvocationTargetException failure) { return failure.getCause(); }
        }
        void close() { try { ((URLClassLoader)loader).close(); } catch (Exception ignored) { } }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException next) { }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static Object allocate(Class<?> type) throws Exception {
        Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        return unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }
}
