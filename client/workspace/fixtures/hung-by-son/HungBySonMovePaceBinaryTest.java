import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Runs the candidate's actual native entity update prefix (Battlefield align, movement guard and
 * the two coordinate writes) on a local player and on another entity: the local player steps on
 * real time (28ms), every other entity keeps the tempo's five-of-eight tick mask.
 */
public final class HungBySonMovePaceBinaryTest implements Opcodes {
    private static int checks;

    private static void require(boolean value, String text) {
        checks++;
        if (!value) throw new AssertionError(text);
        System.out.println("PASS " + text);
    }

    private static Properties load(File file) throws IOException {
        Properties p = new Properties();
        InputStream in = new FileInputStream(file);
        try { p.load(in); } finally { in.close(); }
        return p;
    }

    private static byte[] entry(File jar, String name) throws IOException {
        JarFile file = new JarFile(jar);
        try {
            JarEntry e = file.getJarEntry(name);
            if (e == null) throw new AssertionError("Missing " + name);
            InputStream in = file.getInputStream(e);
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0;) out.write(b, 0, n);
            return out.toByteArray();
        } finally { file.close(); }
    }

    private static Object unsafe() throws Exception {
        Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); f.setAccessible(true); return f.get(null);
    }

    private static Object allocate(Class<?> type) throws Exception {
        Object u = unsafe();
        return u.getClass().getMethod("allocateInstance", Class.class).invoke(u, type);
    }

    /** Static move(Entity): the native a()V prefix through the second coordinate write. */
    private static byte[] probe(byte[] entityBytes, String owner) {
        ClassNode entity = new ClassNode(); new ClassReader(entityBytes).accept(entity, 0);
        MethodNode update = null;
        for (MethodNode m : entity.methods) if (m.name.equals("a") && m.desc.equals("()V")) update = m;
        require(update != null, "Native entity update a()V exists");
        MethodNode move = new MethodNode(ACC_PUBLIC | ACC_STATIC, "move", "(L" + owner + ";)V", null, null);
        Map<LabelNode,LabelNode> labels = new HashMap<LabelNode,LabelNode>();
        for (AbstractInsnNode i : update.instructions.toArray()) if (i instanceof LabelNode) labels.put((LabelNode)i, new LabelNode());
        Set<LabelNode> copied = new HashSet<LabelNode>();
        int writes = 0, gates = 0;
        for (AbstractInsnNode original : update.instructions.toArray()) {
            if (original instanceof FrameNode || original instanceof LineNumberNode) continue;
            AbstractInsnNode i = original.clone(labels); move.instructions.add(i);
            if (i instanceof LabelNode) copied.add((LabelNode)i);
            if (i instanceof MethodInsnNode && ((MethodInsnNode)i).owner.equals("luna/features/MovePace")) gates++;
            if (i instanceof FieldInsnNode && i.getOpcode() == PUTFIELD && ((FieldInsnNode)i).owner.equals(owner)
                    && (((FieldInsnNode)i).name.equals("aZ") || ((FieldInsnNode)i).name.equals("ba"))) writes++;
            if (writes == 2) break;
        }
        require(writes == 2 && gates == 1, "Extracted the actual guard with one MovePace gate and both coordinate writes");
        LabelNode done = new LabelNode();
        for (AbstractInsnNode i : move.instructions.toArray())
            if (i instanceof JumpInsnNode && !copied.contains(((JumpInsnNode)i).label)) ((JumpInsnNode)i).label = done;
        move.instructions.add(done); move.instructions.add(new InsnNode(RETURN));
        ClassNode probe = new ClassNode(); probe.version = V1_4; probe.access = ACC_PUBLIC;
        probe.name = "HungMovePaceProbe"; probe.superName = "java/lang/Object"; probe.methods.add(move);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); probe.accept(writer);
        return writer.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("candidate.jar catalog microemulator.jar");
        File candidate = new File(args[0]);
        Properties catalog = load(new File(args[1]));
        require("28".equals(catalog.getProperty("local.move.step.ms")), "Catalog records the 28ms local step");
        String owner = catalog.getProperty("entity.owner");
        URLClassLoader loader = new URLClassLoader(new URL[]{candidate.toURI().toURL(), new File(args[2]).toURI().toURL()}, null);
        final byte[] bytes = probe(entry(candidate, owner + ".class"), owner);
        Class<?> probe = new ClassLoader(loader) {
            Class<?> define() { return defineClass("HungMovePaceProbe", bytes, 0, bytes.length); }
        }.define();
        Method move = probe.getMethod("move", loader.loadClass(owner.replace('/', '.')));

        Class<?> entityType = loader.loadClass(owner.replace('/', '.'));
        Class<?> singletonOwner = loader.loadClass(catalog.getProperty("player.singleton.owner").replace('/', '.'));
        String playerDesc = catalog.getProperty("player.singleton.descriptor");
        Class<?> playerType = loader.loadClass(playerDesc.substring(1, playerDesc.length() - 1).replace('/', '.'));
        Field singleton = singletonOwner.getField(catalog.getProperty("player.singleton.field"));
        Field tick = entityType.getField("hsoMoveTick"), x = entityType.getField("aZ"), y = entityType.getField("ba"),
                vx = entityType.getField("bc"), vy = entityType.getField("bd");
        Class<?> pace = loader.loadClass("luna.features.MovePace");
        require(pace.getField("STEP_MS").getInt(null) == 28, "MovePace steps every 28ms");

        Object local = allocate(playerType), other = allocate(entityType), remote = allocate(playerType);
        singleton.set(null, local);
        for (Object e : new Object[]{local, other, remote}) { x.setInt(e, 100); y.setInt(e, 200); vx.setInt(e, 6); vy.setInt(e, -6); }

        // Burst of 16 updates inside a few ms: the tempo mask moves others 10/16; the local player at most twice.
        pace.getMethod("resetForTest").invoke(null);
        for (int t = 0; t < 16; t++) {
            tick.setInt(null, t);
            move.invoke(null, local); move.invoke(null, other); move.invoke(null, remote);
        }
        require(x.getInt(other) == 160 && y.getInt(other) == 140, "Another entity keeps the native five-of-eight mask: 10 of 16 updates");
        require(x.getInt(remote) == 160, "Another player-class entity keeps the mask too");
        int burst = (x.getInt(local) - 100) / 6;
        require(burst >= 1 && burst <= 2 && y.getInt(local) == 200 - burst * 6,
                "Local player ignores the tick count: " + burst + " step(s) for 16 bunched updates");

        // Real time: one step per 28ms whatever the update frequency.
        for (long sleep : new long[]{0L, 4L, 9L}) {
            pace.getMethod("resetForTest").invoke(null);
            x.setInt(local, 0); y.setInt(local, 0);
            long start = System.nanoTime(); int updates = 0;
            while (System.nanoTime() - start < 1120L * 1000000L) {
                tick.setInt(null, updates++); move.invoke(null, local);
                if (sleep > 0) Thread.sleep(sleep);
            }
            int steps = x.getInt(local) / 6;
            require(steps >= 37 && steps <= 42 && y.getInt(local) == -steps * 6,
                    "1120ms of updates every " + sleep + "ms step the local player " + steps + " times (expected ~40) over " + updates + " updates");
        }

        // Velocity stays native: a mounted step (6 + bonus) moves by that step size.
        pace.getMethod("resetForTest").invoke(null);
        x.setInt(local, 0); vx.setInt(local, 6 + 4); move.invoke(null, local);
        require(x.getInt(local) == 10, "The native step size (base + mount bonus) is applied unchanged");
        System.out.println("HungBySonMovePaceBinaryTest PASS checks=" + checks);
    }
}
