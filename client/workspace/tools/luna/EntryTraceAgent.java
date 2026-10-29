import java.io.*;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Host-only live diagnostic. Never embedded into a release or logs credential payloads. */
public final class EntryTraceAgent implements Opcodes {
    static final String SELF = "EntryTraceBootstrap";
    static final Map<String,String> aliases = new LinkedHashMap<String,String>();
    static final Map<String,ClassNode> classes = new LinkedHashMap<String,ClassNode>();
    static final Map<Integer,Integer> counts = new TreeMap<Integer,Integer>();
    static final ThreadLocal<Integer> currentOpcode = new ThreadLocal<Integer>();
    static final Map<Integer,Integer> saved = new HashMap<Integer,Integer>();
    static PrintWriter log;
    static File directory;
    static String packet;
    static int sequence;

    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        File jar = new File(System.getProperty("entry.jar"));
        directory = new File(System.getProperty("entry.traceDir"));
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create diagnostic directory");
        log = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(directory, "trace.txt")), "UTF-8"), true);
        JarFile archive = new JarFile(jar);
        try {
            for (Enumeration<JarEntry> e = archive.entries(); e.hasMoreElements();) {
                JarEntry entry = e.nextElement(); if (!entry.getName().endsWith(".class")) continue;
                InputStream in = archive.getInputStream(entry);
                ClassNode c = new ClassNode(); try { new ClassReader(in).accept(c, 0); } finally { in.close(); }
                classes.put(c.name, c); aliases.put(c.name, c.name.length() > 50 ? "C" + aliases.size() : c.name);
                boolean input = false, output = false, incoming = false;
                for (FieldNode f : c.fields) {
                    if (f.desc.equals("Ljava/io/DataInputStream;")) input = true;
                    if (f.desc.equals("Ljava/io/DataOutputStream;")) output = true;
                }
                for (MethodNode m : c.methods) if (m.name.equals("<init>") && m.desc.equals("(B[B)V")) incoming = true;
                if (input && output && incoming) {
                    if (packet != null) throw new IllegalStateException("Ambiguous native packet class"); packet = c.name;
                }
            }
        } finally { archive.close(); }
        if (packet == null) throw new IllegalStateException("Native packet class absent");
        Properties mapping = new Properties();
        for (ClassNode c : classes.values()) {
            mapping.setProperty(aliases.get(c.name), c.name);
            for (int i = 0; i < c.methods.size(); i++) {
                MethodNode m = c.methods.get(i); mapping.setProperty(aliases.get(c.name) + ".m" + i, m.name + m.desc);
            }
        }
        Writer out = new OutputStreamWriter(new FileOutputStream(new File(directory, "symbols.properties")), "UTF-8");
        try { mapping.store(out, "Native class and method aliases; contains no field values"); } finally { out.close(); }
        event("START jar=" + jar.getName() + " packet=" + aliases.get(packet));
        // Native stdout/stderr are not audited for credentials. Discard them;
        // only the structured events above are persisted by this diagnostic.
        PrintStream discard = new PrintStream(new OutputStream() { public void write(int b) { } });
        System.setOut(discard); System.setErr(discard);
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable error) { failure("UNCAUGHT thread=" + t.getName(), error); }
        });
        instrumentation.addTransformer(new Transformer());
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() { summary(); }
        }, "EntryTraceSummary"));
    }

    static final class Transformer implements ClassFileTransformer {
        public byte[] transform(ClassLoader loader, String name, Class<?> redefining, ProtectionDomain domain, byte[] bytes) {
            boolean emulator = name.equals("org/microemu/app/Common");
            if (!classes.containsKey(name) && !emulator) return null;
            try {
                ClassNode c = new ClassNode(); new ClassReader(bytes).accept(c, 0);
                boolean changed = false;
                for (int index = 0; index < c.methods.size(); index++) {
                    MethodNode m = c.methods.get(index);
                    String label = (emulator ? "EMULATOR" : aliases.get(name)) + ".m" + index;
                    if (emulator && m.name.equals("handleStartMidletException")) {
                        InsnList hook = new InsnList(); hook.add(new LdcInsnNode("MIDLET_START")); hook.add(new VarInsnNode(ALOAD, 1));
                        hook.add(new MethodInsnNode(INVOKESTATIC, SELF, "failure", "(Ljava/lang/String;Ljava/lang/Throwable;)V", false));
                        m.instructions.insert(hook); changed = true;
                    }
                    Set<LabelNode> catchLabels = Collections.newSetFromMap(new IdentityHashMap<LabelNode,Boolean>());
                    for (TryCatchBlockNode handler : m.tryCatchBlocks) catchLabels.add(handler.handler);
                    for (LabelNode handler : catchLabels) {
                        InsnList hook = new InsnList(); hook.add(new InsnNode(DUP)); hook.add(new LdcInsnNode(label)); hook.add(new InsnNode(SWAP));
                        hook.add(new MethodInsnNode(INVOKESTATIC, SELF, "failure", "(Ljava/lang/String;Ljava/lang/Throwable;)V", false));
                        AbstractInsnNode first = handler.getNext();
                        while (first != null && first.getOpcode() < 0) first = first.getNext();
                        if (first == null) throw new IllegalStateException("Empty catch handler");
                        m.instructions.insertBefore(first, hook); changed = true;
                    }
                    boolean sendBuilder = false;
                    for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode)n;
                        if (call.owner.equals(packet) && call.name.equals("<init>") && call.desc.equals("(B[B)V") && m.name.equals("run")) {
                            AbstractInsnNode array = previous(n), command = previous(array), store = next(n);
                            if (!(array instanceof VarInsnNode) || array.getOpcode() != ALOAD || !(command instanceof VarInsnNode) || command.getOpcode() != ILOAD || !(store instanceof VarInsnNode) || store.getOpcode() != ASTORE)
                                throw new IllegalStateException("Incoming frame constructor shape changed");
                            InsnList hook = new InsnList(); hook.add(command.clone(new IdentityHashMap<LabelNode,LabelNode>())); hook.add(array.clone(new IdentityHashMap<LabelNode,LabelNode>()));
                            hook.add(new MethodInsnNode(INVOKESTATIC, SELF, "received", "(I[B)V", false)); m.instructions.insert(store, hook); changed = true;
                            event("HOOK_RX " + label);
                        }
                        if (call.owner.equals(packet) && call.name.equals("<init>") && call.desc.equals("(B)V") && m.desc.equals("(B)V")) sendBuilder = true;
                    }
                    if (sendBuilder) {
                        InsnList hook = new InsnList(); hook.add(new VarInsnNode(ILOAD, (m.access & ACC_STATIC) != 0 ? 0 : 1));
                        hook.add(new MethodInsnNode(INVOKESTATIC, SELF, "sent", "(I)V", false)); m.instructions.insert(hook); changed = true;
                        event("HOOK_TX " + label);
                    }
                    Type[] params = Type.getArgumentTypes(m.desc);
                    if (params.length == 1 && params[0].getDescriptor().equals("L" + packet + ";") && Type.getReturnType(m.desc).getSort() == Type.VOID && !m.name.equals("<init>")) {
                        InsnList hook = new InsnList(); hook.add(new LdcInsnNode(label)); hook.add(new MethodInsnNode(INVOKESTATIC, SELF, "entered", "(Ljava/lang/String;)V", false));
                        m.instructions.insert(hook); changed = true;
                    }
                }
                if (!changed) return null;
                ClassWriter writer = new ClassWriter(new ClassReader(bytes), ClassWriter.COMPUTE_MAXS); c.accept(writer); return writer.toByteArray();
            } catch (Throwable error) { failure("TRANSFORM " + aliases.get(name), error); throw new IllegalStateException("Cannot instrument native client", error); }
        }
    }
    public static synchronized void received(int opcode, byte[] payload) {
        currentOpcode.set(Integer.valueOf(opcode));
        Integer old = counts.get(Integer.valueOf(opcode)); counts.put(Integer.valueOf(opcode), Integer.valueOf(old == null ? 1 : old.intValue() + 1));
        int number = ++sequence; event("RX seq=" + number + " op=" + opcode + " len=" + (payload == null ? -1 : payload.length));
        // Explicit incoming-data allow-list. Never persist opcode 31 credentials,
        // outgoing opcode 1 authentication, character lists, or arbitrary messages.
        boolean capture = opcode == 1 || opcode == 3 || opcode == 16 || opcode == 65 || opcode == -50 || opcode == -54;
        Integer prior = saved.get(Integer.valueOf(opcode)); int count = prior == null ? 0 : prior.intValue();
        if (capture && payload != null && payload.length <= 1024 * 1024 && count < 3) {
            saved.put(Integer.valueOf(opcode), Integer.valueOf(count + 1));
            try {
                FileOutputStream out = new FileOutputStream(new File(directory, String.format("rx-%05d-op-%d.bin", number, opcode)));
                try { out.write(payload); } finally { out.close(); }
            } catch (IOException e) { event("CAPTURE_FAILED " + e.getClass().getName()); }
        }
    }
    public static synchronized void sent(int opcode) { event("TX op=" + opcode); }
    public static synchronized void entered(String label) { event("ENTER " + label + " rx=" + currentOpcode.get()); }
    public static synchronized void failure(String label, Throwable error) {
        event("FAIL " + label + " rx=" + currentOpcode.get() + " type=" + error.getClass().getName());
        if (error instanceof LinkageError) event("  LINKAGE " + error.getMessage());
        StackTraceElement[] stack = error.getStackTrace();
        for (int i = 0; i < Math.min(stack.length, 20); i++) {
            String owner = stack[i].getClassName().replace('.', '/'), alias = aliases.get(owner);
            event("  AT " + (alias == null ? stack[i].getClassName() : alias) + "." + (stack[i].getMethodName().length() > 50 ? "native" : stack[i].getMethodName()) + ":" + stack[i].getLineNumber());
        }
    }
    public static synchronized void state(String value) { event("STATE " + value); }
    public static synchronized void summary() { event("COUNTS " + counts); }
    static synchronized void event(String text) { if (log != null) log.println(System.currentTimeMillis() + " " + text); }
    static AbstractInsnNode previous(AbstractInsnNode n) { do { n = n.getPrevious(); } while (n != null && n.getOpcode() < 0); return n; }
    static AbstractInsnNode next(AbstractInsnNode n) { do { n = n.getNext(); } while (n != null && n.getOpcode() < 0); return n; }
}
