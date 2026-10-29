import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Executes native request, entity callback, animation advance, tempo scaler and
 * packet serialization in an isolated host. World/render initialization, skill
 * and target lookup, common visual dispatch and the final network enqueue are
 * replaced; clocks are deterministic. The old-input comparison is structural,
 * while the new-input pending-frame retry executes the actual callback.
 */
public final class HungBySonRealTimeCombatHostTest implements Opcodes {
    private static final String HOST = "HungBySonRealTimeCombatHostTest";
    private static final String BRIDGE = HOST + "$NativeBridge";
    private static final String NETWORK_OWNER = encoded("lililili");
    private static final String NETWORK_METHOD = encoded("lliiili");
    private static final String MESSAGE_OWNER = encoded("lllllil");
    private static final String SENDER_OWNER = encoded("lllilii");
    private static final String SENDER_METHOD = encoded("lliiiii");
    private static final String BUFFER_OWNER = encoded("illlllil");
    private static int[] SPEED_CODES = new int[]{1, 2, 3, -8, -9};
    private static String[] SPEED_LABELS = new String[]{"x1", "x2", "x3", "x0.8", "x0.9"};
    private static int minimum = 550;
    private static int maxUpdatesPerPass = 3;
    private static int backlogDropMs = 120;
    private static Properties autoAttackCatalog;

    public static long now;
    public static Object currentSkill;
    public static Object currentTargets;
    public static Object currentQ;
    public static final List<String> packets = new ArrayList<String>();
    public static final List<String> visualDispatches = new ArrayList<String>();
    private static int checks;

    private HungBySonRealTimeCombatHostTest() { }

    public static void main(String[] args) throws Exception {
        boolean preservedTiming = args.length == 6 && "--preserved-timing".equals(args[5]);
        boolean autoAttackRegression = args.length == 7 && "--auto-attack-regression".equals(args[5]);
        require(args.length == 5 || preservedTiming || autoAttackRegression,
                "<input.jar> <candidate.jar> <combat.properties> <tempo.properties> <microemulator.jar> [--preserved-timing|--auto-attack-regression <auto-attack.properties>]");
        File input = new File(args[0]);
        File candidate = new File(args[1]);
        File combatFile = new File(args[2]);
        File tempoFile = new File(args[3]);
        File micro = new File(args[4]);
        require(input.isFile() && candidate.isFile(), "input and candidate JARs exist");
        require(combatFile.isFile() && tempoFile.isFile(), "combat and tempo catalogs exist");
        require(micro.isFile(), "MicroEmulator dependency exists");
        Properties combat = load(combatFile);
        Properties tempo = load(tempoFile);
        autoAttackCatalog = autoAttackRegression ? load(new File(args[6])) : null;
        require(hash(input).equals(required(combat, "input.sha256")), "input bytes match the combat catalog baseline");
        require(hash(candidate).equals(required(combat, "output.sha256")),
                "combat catalog names the supplied candidate");
        minimum = Integer.parseInt(required(combat, "request.minimum.ms"));
        require(minimum > 0 && minimum <= 32767, "supported configured request minimum");
        require("0".equals(required(combat, "request.extra.ms")), "request has no added native interval");
        SPEED_CODES = parseInts(required(tempo, "candidate.speed.codes"));
        String[] speedFactors = parseStrings(required(tempo, "candidate.speed.labels"));
        require(SPEED_CODES.length == 5 && speedFactors.length == 5,
                "tempo catalog has five speed values and labels");
        SPEED_LABELS = new String[speedFactors.length];
        for (int i = 0; i < speedFactors.length; i++) SPEED_LABELS[i] = "x" + speedFactors[i];
        maxUpdatesPerPass = Integer.parseInt(tempo.getProperty("candidate.loop.maxUpdatesPerPass", "3"));
        backlogDropMs = Integer.parseInt(tempo.getProperty("candidate.loop.backlogDropMs", "120"));
        require(maxUpdatesPerPass >= 3 && backlogDropMs >= 40,
                "tempo catalog has a valid bounded native update loop");

        verifyAutoGate(candidate, combat, tempo);
        verifyRequestBoundaries(candidate, combat);
        verifyPoisonedCachedClock(candidate, combat);
        verifyRealScalerCounts(candidate, tempo);
        verifySpeedIndependentRetries(candidate, combat, tempo);
        verifySourceTimingAndPendingFrame(input, candidate, combat, preservedTiming, autoAttackRegression);
        verifyRemoteAndNpc(candidate, combat);
        System.out.println("REALTIME_REQUEST=actual candidate ()V; direct System clock substituted; opcode-6 bytes captured at memory enqueue");
        System.out.println("CALLBACK=actual entity J()V request, codexWaitRealTime guard, and native animation advance");
        System.out.println("TEMPO=" + Arrays.toString(SPEED_LABELS) + " actual native-loop update counts="
                + Arrays.toString(expectedUpdateCounts()));
        if (autoAttackRegression) {
            System.out.println("AUTO_ATTACK_REGRESSION=tag0 default request minimum 650ms; tagged dynamic minimum checked by native binary fixture");
        }
        System.out.println("HungBySonRealTimeCombatHostTest PASS (" + checks + " checks)");
    }

    private static void verifyAutoGate(File jar, Properties combat, Properties tempo) throws Exception {
        for (int code : SPEED_CODES) {
        RuntimeFixture fixture = new RuntimeFixture(jar, combat, tempo, 0);
            fixture.setSpeed(code);
            long baseline = 100000L;
            setStatic(fixture.autoOwner, required(combat, "auto.timestamp.field"), baseline);
            setStatic(fixture.autoOwner, required(combat, "auto.delay.field"), minimum);
            Method gate = method(fixture.autoClass, required(combat, "auto.gate.method"),
                    required(combat, "auto.gate.descriptor"));
            now = baseline + minimum;
            require(Boolean.FALSE.equals(invoke(gate, null)), "auto gate holds at " + minimum + "ms under " + speedLabel(code));
            now = baseline + minimum + 1L;
            require(Boolean.TRUE.equals(invoke(gate, null)), "auto gate opens at " + (minimum + 1) + "ms under " + speedLabel(code));
            fixture.close();
        }
    }

    private static void verifyRequestBoundaries(File jar, Properties combat) throws Exception {
        int[] nativeIntervals = new int[]{0, 100, 600, 32767};
        int[] openAt = new int[]{minimum+1, minimum+1, Math.max(minimum,600)+1, 32768};
        for (int i = 0; i < nativeIntervals.length; i++) {
            RuntimeFixture fixture = new RuntimeFixture(jar, combat, null, nativeIntervals[i]);
            long baseline = 1000000L;
            fixture.setClockState(baseline, Long.MIN_VALUE);
            for (int elapsed : waitBoundaries(nativeIntervals[i])) {
                int packetCount = packets.size();
                int frame = fixture.frame();
                State before = new State(fixture.requestInstance, fixture.actor, fixture.requestClass, fixture.actor.getClass(),
                        required(combat, "request.timestamp.field"), required(combat, "request.delay.field"));
                now = baseline + elapsed;
                fixture.requestOnly();
                require(packets.size() == packetCount,
                        "native interval " + nativeIntervals[i] + " waits at elapsed " + elapsed);
                require(fixture.frame() == frame, "waiting keeps local cQ=m at " + elapsed);
                require(fixture.waitFlag(), "waiting sets the real-time retry flag at " + elapsed);
                before.requireOnlyFrameChanged(fixture.requestInstance, fixture.actor, fixture.requestClass,
                        fixture.actor.getClass(), required(combat, "request.timestamp.field"),
                        required(combat, "request.delay.field"), required(combat, "request.defer.flag.field"), frame,
                        "waiting changes only its retry flag and leaves sender, metadata, mana, cooldown, and actor untouched");
                fixture.callback();
                require(fixture.frame() == frame, "actual J()V guard skips native animation advance while waiting");
                require(fixture.waitFlag(), "actual J()V leaves cQ=m pending through the wait");
            }
            int threshold = Math.max(minimum, nativeIntervals[i]);
            int packetCount = packets.size();
            fixture.setFrame(fixture.frameTarget);
            now = baseline + openAt[i];
            fixture.callback();
            require(packets.size() == packetCount + 1,
                    "native interval " + nativeIntervals[i] + " dispatches once at elapsed " + openAt[i]);
            requireOpcode6(packetCount, "native interval " + nativeIntervals[i] + " serializes opcode 6");
            require(fixture.requestTimestamp() == now, "accepted request writes real wall-clock timestamp");
            require(fixture.frame() == fixture.frameTarget + 1,
                    "ready J()V runs native animation advance beyond its one-shot send frame");
            fixture.callback();
            fixture.callback();
            require(packets.size() == packetCount + 1,
                    "native interval " + nativeIntervals[i] + " does not duplicate after the send frame");
            require(openAt[i] > threshold, "accepted timestamp is strictly above max(" + minimum + ",nativeInterval)");
            fixture.close();
        }
    }

    private static void verifyPoisonedCachedClock(File jar, Properties combat) throws Exception {
        RuntimeFixture fixture = new RuntimeFixture(jar, combat, null, 0);
        long baseline = 2000000L;
        fixture.setClockState(baseline, Long.MAX_VALUE);
        now = baseline + minimum;
        fixture.callback();
        require(packets.isEmpty(), "stale cached clock cannot open the request at " + minimum + "ms");
        fixture.poisonOldClock(Long.MIN_VALUE);
        now = baseline + minimum + 1L;
        fixture.callback();
        require(packets.size() == 1, "accelerated cached clock cannot block the request after " + (minimum + 1) + "ms");
        fixture.close();
    }

    private static void verifyRealScalerCounts(File jar, Properties tempo) throws Exception {
        JarClassLoader loader = new JarClassLoader(jar, null, tempo);
        Class<?> scaleClass = Class.forName(required(tempo, "candidate.scale.owner").replace('/', '.'), true, loader);
        Class<?> speedOwner = Class.forName(required(tempo, "candidate.speed.owner").replace('/', '.'), true, loader);
        for (int i = 0; i < SPEED_CODES.length; i++) {
            int code = SPEED_CODES[i];
            field(speedOwner, required(tempo, "candidate.speed.field")).setInt(null, code);
            Object canvas = unsafe().allocateInstance(scaleClass);
            set(canvas, scaleClass, "codexTempoFractionSpeed", -100);
            set(canvas, scaleClass, "codexTempoFraction", 0L);
            Method scale = method(scaleClass, required(tempo, "candidate.scale.method"),
                    required(tempo, "candidate.scale.descriptor"));
            long accumulator = 0L;
            int updates = 0;
            for (int tick = 0; tick < 100; tick++) {
                long scaled = ((Long)invoke(scale, canvas, Long.valueOf(16L))).longValue();
                accumulator += scaled * 2L;
                int inPass = 0;
                while (accumulator >= 40L && inPass < maxUpdatesPerPass) {
                    accumulator -= 40L;
                    inPass++;
                    updates++;
                }
                if (accumulator >= backlogDropMs) accumulator = 0L;
            }
            int expected = expectedUpdateCount(code);
            require(updates == expected,
                    "actual native scaler and loop produce " + updates + " callbacks for " + speedLabel(code)
                            + " / 1600ms (expected " + expected + ")");
        }
        loader.close();
    }

    private static void verifySpeedIndependentRetries(File jar, Properties combat, Properties tempo)
            throws Exception {
        for (int code : SPEED_CODES) {
            RuntimeFixture fixture = new RuntimeFixture(jar, combat, tempo, 0);
            fixture.setSpeed(code);
            long baseline = 5000000L;
            fixture.setClockState(baseline - minimum - 1L, 0L); // first eligible frame must not wait for a stale startup value
            now = baseline + 1L;
            fixture.setFrame(fixture.frameTarget);
            fixture.callback();
            require(packets.size() == 1, "first cast sends immediately above " + minimum + "ms under " + speedLabel(code));
            requireOpcode6(0, "first cast under " + speedLabel(code) + " reaches native opcode 6 serializer");
            fixture.close();

            fixture = new RuntimeFixture(jar, combat, tempo, 0);
            long start = 7000000L;
            fixture.setClockState(start, start);
            Object canvas = unsafe().allocateInstance(fixture.scaleClass);
            set(canvas, fixture.scaleClass, "codexTempoFractionSpeed", -100);
            set(canvas, fixture.scaleClass, "codexTempoFraction", 0L);
            Method scale = method(fixture.scaleClass, required(tempo, "candidate.scale.method"),
                    required(tempo, "candidate.scale.descriptor"));
            long accumulator = 0L;
            int updates = 0;
            int lastCount = 0;
            for (int tick = 0; tick < (minimum+90)/16; tick++) {
                fixture.setSpeed(code);
                now = start + tick * 16L;
                long scaled = ((Long)invoke(scale, canvas, Long.valueOf(16L))).longValue();
                accumulator += scaled * 2L;
                int inPass = 0;
                while (accumulator >= 40L && inPass < maxUpdatesPerPass) {
                    accumulator -= 40L;
                    fixture.callback();
                    updates++;
                    inPass++;
                }
                if (accumulator >= backlogDropMs) accumulator = 0L;
                if (!packets.isEmpty()) {
                    require(now > start + minimum, "" + speedLabel(code) + " send waits for configured real minimum");
                    require(packets.size() == 1, "" + speedLabel(code) + " sends only once while callbacks continue");
                }
                lastCount = packets.size();
            }
            now = start + minimum + 90L;
            fixture.setFrame(fixture.frameTarget);
            fixture.callback();
            require(lastCount == 1 && packets.size() == 1,
                    "" + speedLabel(code) + " produces one pending retry independent of callback count " + updates);
            fixture.close();
        }

        RuntimeFixture changing = new RuntimeFixture(jar, combat, tempo, 0);
        long start = 9000000L;
        changing.setClockState(start, start);
        Object canvas = unsafe().allocateInstance(changing.scaleClass);
        set(canvas, changing.scaleClass, "codexTempoFractionSpeed", -100);
        set(canvas, changing.scaleClass, "codexTempoFraction", 0L);
        Method scale = method(changing.scaleClass, required(tempo, "candidate.scale.method"),
                required(tempo, "candidate.scale.descriptor"));
        long accumulator = 0L;
        for (int tick = 0; tick < (minimum+90)/16; tick++) {
            changing.setSpeed(SPEED_CODES[tick % SPEED_CODES.length]);
            now = start + tick * 16L;
            long scaled = ((Long)invoke(scale, canvas, Long.valueOf(16L))).longValue();
            accumulator += scaled * 2L;
            int inPass = 0;
            while (accumulator >= 40L && inPass < maxUpdatesPerPass) {
                accumulator -= 40L;
                changing.callback();
                inPass++;
            }
            if (accumulator >= backlogDropMs) accumulator = 0L;
        }
        now = start + minimum + 90L;
        changing.setFrame(changing.frameTarget);
        changing.callback();
        require(packets.size() == 1, "changing tempo while a local send is pending never duplicates it");
        changing.close();
    }

    private static void verifySourceTimingAndPendingFrame(File input, File candidate,
            Properties combat, boolean preservedTiming, boolean autoAttackRegression) throws Exception {
        ClassNode oldCombat = parse(readJarClass(input, required(combat, "request.owner")));
        MethodNode oldRequest = findMethod(oldCombat, required(combat, "request.method"),
                required(combat, "request.descriptor"));
        boolean oldInterval = false, oldWaitBranch = false, oldFrameWrite = false;
        for (AbstractInsnNode instruction : oldRequest.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == GETSTATIC) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (required(combat, "request.delay.owner").equals(field.owner)
                        && required(combat, "request.delay.field").equals(field.name)
                        && "S".equals(field.desc)) oldInterval = true;
            }
            if (instruction instanceof JumpInsnNode && instruction.getOpcode() == IFLE) oldWaitBranch = true;
            if (fieldInsn(instruction, PUTFIELD, required(combat, "entity.owner"), "cQ", "I")) oldFrameWrite = true;
        }
        require(oldInterval && oldWaitBranch && !oldFrameWrite,
                "650 ms source reads the native interval and keeps pending state outside actor animation");
        require(requestMinimum(oldRequest, combat, false) == 650,
                "source input uses strict >max(650,nativeSkillInterval)");
        ClassNode newCombat = parse(readJarClass(candidate, required(combat, "request.owner")));
        String candidateRequestName = autoAttackCatalog == null ? required(combat, "request.method")
                : required(autoAttackCatalog, "native.ownedRequest.method");
        String candidateRequestDescriptor = autoAttackCatalog == null ? required(combat, "request.descriptor")
                : required(autoAttackCatalog, "native.ownedRequest.descriptor");
        MethodNode newRequest = findMethod(newCombat, candidateRequestName, candidateRequestDescriptor);
        int candidateMinimum = requestMinimum(newRequest, combat, autoAttackRegression);
        require(candidateMinimum == (preservedTiming || autoAttackRegression ? minimum : 300),
                preservedTiming || autoAttackRegression ? "candidate retains the configured request minimum"
                        : "candidate lowers the strict request minimum to 300ms");
        if (preservedTiming) {
            require(Arrays.equals(readJarClass(input, required(combat, "request.owner")),
                            readJarClass(candidate, required(combat, "request.owner"))),
                    "cache repair leaves the entire native request class byte-identical");
            require(Arrays.equals(readJarClass(input, required(combat, "entity.owner")),
                            readJarClass(candidate, required(combat, "entity.owner"))),
                    "cache repair leaves the entire native entity/callback class byte-identical");
        } else if (autoAttackRegression) {
            require(minimum == 650, "auto-attack regression catalog keeps the preexisting native gate at 650ms");
            require(normalizedRequestMinimumEquals(oldCombat, oldRequest, newCombat, newRequest, combat),
                    "candidate request method is byte-equivalent after normalizing only its ownership shim, stale-cast guard and contextual minimum hooks");
            require(runtimeMinimum(candidate, 0) == 650,
                    "candidate native/un-tagged request context resolves Auto Attack requestMinimum(0) to 650ms");
        }
        ClassNode oldEntity = parse(readJarClass(input, required(combat, "entity.owner")));
        MethodNode oldTick = findMethod(oldEntity, required(combat, "request.caller.method"),
                required(combat, "request.caller.descriptor"));
        MethodInsnNode oldRequestCall = null;
        for (AbstractInsnNode instruction : oldTick.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (required(combat, "request.owner").equals(call.owner)
                        && required(combat, "request.method").equals(call.name)
                        && required(combat, "request.descriptor").equals(call.desc)) oldRequestCall = call;
            }
        }
        require(oldRequestCall != null && nextMethodInsn(oldRequestCall) != null
                && nextMethodInsn(oldRequestCall).desc.equals("(L" + oldEntity.name + ";)V"),
                "2AF entity callback advances animation immediately after a request returns");
        require(containsWaitGuard(oldTick, required(combat, "request.owner"),
                        required(combat, "request.defer.flag.field")),
                "650 ms source already holds the local send frame while waiting");
        ClassNode newEntity = parse(readJarClass(candidate, required(combat, "entity.owner")));
        MethodNode newTick = findMethod(newEntity, required(combat, "request.caller.method"),
                required(combat, "request.caller.descriptor"));
        require(containsWaitGuard(newTick, required(combat, "request.owner"),
                        required(combat, "request.defer.flag.field")),
                "candidate preserves the pending-frame animation guard");

        RuntimeFixture fixture = new RuntimeFixture(candidate, combat, null, 600);
        long baseline = 12000000L;
        fixture.setClockState(baseline, Long.MIN_VALUE);
        now = baseline + 100L;
        fixture.callback();
        require(packets.isEmpty() && fixture.frame() == fixture.frameTarget && fixture.waitFlag(),
                "candidate keeps the exact request frame at +100ms while interval 600 is pending");
        now = baseline + Math.max(minimum,600) + 1L;
        fixture.callback();
        require(packets.size() == 1 && fixture.frame() == fixture.frameTarget + 1,
                "candidate retries the retained frame and advances after the configured native send threshold");
        requireOpcode6(0, "lost-frame reproduction emits a serialized opcode 6 packet");
        fixture.close();
        System.out.println("PENDING_FRAME=650 ms source retains local send frame; " + minimum
                + " ms candidate keeps interval-600 retry at +" + (Math.max(minimum, 600) + 1));
    }

    private static int requestMinimum(MethodNode request, Properties combat) {
        return requestMinimum(request, combat, false);
    }

    private static int requestMinimum(MethodNode request, Properties combat, boolean allowAutoAttackContext) {
        if (allowAutoAttackContext && autoAttackCatalog != null)
            return sharedRequestMinimum(request, combat, autoAttackCatalog);
        int dynamicCalls = 0;
        if (allowAutoAttackContext) {
            String tagSpec = required(combat, "request.tag.field");
            int colon = tagSpec.indexOf(':');
            String tagName = colon < 0 ? tagSpec : tagSpec.substring(0, colon);
            String tagDesc = colon < 0 ? "I" : tagSpec.substring(colon + 1);
            String tagOwner = combat.getProperty("request.tag.owner", required(combat, "request.owner"));
            String runtimeOwner = "luna/features/AutoAttackRuntime";
            String runtimeMethod = "requestMinimum";
            String runtimeDesc = "(I)I";
            List<AbstractInsnNode> code = real(request);
            for (int i = 0; i < code.size(); i++) {
                AbstractInsnNode instruction = code.get(i);
                if (!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != INVOKESTATIC) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!runtimeOwner.equals(call.owner) || !runtimeMethod.equals(call.name)
                        || !runtimeDesc.equals(call.desc)) continue;
                require(i >= 2 && code.get(i - 1) instanceof FieldInsnNode
                                && code.get(i - 1).getOpcode() == GETFIELD,
                        "dynamic request minimum reads the cast tag immediately before the runtime bridge");
                FieldInsnNode tag = (FieldInsnNode) code.get(i - 1);
                require(tagOwner.equals(tag.owner) && tagName.equals(tag.name) && tagDesc.equals(tag.desc),
                        "dynamic request minimum reads the catalogued cast tag field");
                    require(isVarInstruction(code.get(i - 2), ALOAD, 0),
                        "dynamic request minimum tag belongs to the current native cast instance");
                require(i >= 4 && fieldInsn(code.get(i - 3), GETSTATIC,
                                required(combat, "request.delay.owner"),
                                required(combat, "request.delay.field"), "S"),
                        "tagged Auto Attack minimum remains paired with the catalogued native skill interval");
                AbstractInsnNode maxInsn = i + 1 < code.size() ? code.get(i + 1) : null;
                require(maxInsn instanceof MethodInsnNode && maxInsn.getOpcode() == INVOKESTATIC
                                && "java/lang/Math".equals(((MethodInsnNode)maxInsn).owner)
                                && "max".equals(((MethodInsnNode)maxInsn).name)
                                && "(II)I".equals(((MethodInsnNode)maxInsn).desc),
                        "native skill interval and contextual Auto Attack delay feed the same adjacent Math.max gate");
                dynamicCalls++;
            }
        }
        if (dynamicCalls > 0) {
            require(dynamicCalls == 1, "one contextual Auto Attack minimum is present in the native request method");
            require(Integer.parseInt(required(combat, "request.minimum.ms")) == 650,
                    "dynamic request minimum catalog retains the native 650ms floor");
            return 650;
        }
        for (AbstractInsnNode instruction : request.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != GETSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode)instruction;
            if (!required(combat, "request.delay.owner").equals(field.owner)
                    || !required(combat, "request.delay.field").equals(field.name)
                    || !"S".equals(field.desc)) continue;
            AbstractInsnNode value = nextInstruction(instruction);
            AbstractInsnNode max = nextInstruction(value);
            if (value instanceof org.objectweb.asm.tree.IntInsnNode
                    && ((org.objectweb.asm.tree.IntInsnNode)value).getOpcode() == SIPUSH
                    && max instanceof MethodInsnNode && max.getOpcode() == INVOKESTATIC
                    && "java/lang/Math".equals(((MethodInsnNode)max).owner)
                    && "max".equals(((MethodInsnNode)max).name)
                    && "(II)I".equals(((MethodInsnNode)max).desc))
                return ((org.objectweb.asm.tree.IntInsnNode)value).operand;
        }
        throw new AssertionError("exact real-time Math.max request minimum is missing");
    }

    private static int sharedRequestMinimum(MethodNode request, Properties combat, Properties auto) {
        String bridge = required(auto, "native.requestMinimum.owner");
        String name = required(auto, "native.requestMinimum.method");
        String descriptor = required(auto, "native.requestMinimum.descriptor");
        String castOwner = required(combat, "request.owner");
        List<AbstractInsnNode> code = real(request);
        int calls = 0;
        for (int i = 0; i < code.size(); i++) {
            if (!(code.get(i) instanceof MethodInsnNode) || code.get(i).getOpcode() != INVOKESTATIC) continue;
            MethodInsnNode call = (MethodInsnNode)code.get(i);
            if (!bridge.equals(call.owner) || !name.equals(call.name) || !descriptor.equals(call.desc)) continue;
            require(i >= 2 && isVarInstruction(code.get(i - 1), ALOAD, 0),
                    "shared Farm/Auto Attack minimum is bound to the current native cast");
            require(("(L" + castOwner + ";)I").equals(descriptor),
                    "shared minimum resolver accepts the exact native cast descriptor");
            require(fieldInsn(code.get(i - 2), GETSTATIC,
                            required(combat, "request.delay.owner"), required(combat, "request.delay.field"), "S")
                            && i + 1 < code.size() && code.get(i + 1) instanceof MethodInsnNode
                            && ((MethodInsnNode)code.get(i + 1)).getOpcode() == INVOKESTATIC
                            && "java/lang/Math".equals(((MethodInsnNode)code.get(i + 1)).owner)
                            && "max".equals(((MethodInsnNode)code.get(i + 1)).name)
                            && "(II)I".equals(((MethodInsnNode)code.get(i + 1)).desc),
                    "shared request minimum remains maxed with the native skill interval");
            calls++;
        }
        require(calls == 1, "one cast-aware shared delay resolver appears in the native request body");
        int defaultDelay = Integer.parseInt(auto.getProperty("delay.default.ms", "650"));
        require(defaultDelay == Integer.parseInt(required(combat, "request.minimum.ms")),
                "Auto Attack configured delay default retains the preexisting 650ms native floor");
        return defaultDelay;
    }

    private static boolean normalizedRequestMinimumEquals(ClassNode oldOwner, MethodNode oldRequest,
            ClassNode newOwner, MethodNode newRequest, Properties combat) {
        MethodNode normalized = findMethod(newOwner, newRequest.name, newRequest.desc);
        if (autoAttackCatalog == null) removeAutoAttackOwnedRequestDispatch(normalized, combat);
        removeAutoAttackStaleGuard(normalized, combat);
        int replacements = 0;
        for (AbstractInsnNode instruction = normalized.instructions.getFirst(); instruction != null; ) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction instanceof MethodInsnNode && instruction.getOpcode() == INVOKESTATIC) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (autoAttackCatalog != null
                        && required(autoAttackCatalog, "native.requestMinimum.owner").equals(call.owner)
                        && required(autoAttackCatalog, "native.requestMinimum.method").equals(call.name)
                        && required(autoAttackCatalog, "native.requestMinimum.descriptor").equals(call.desc)) {
                    AbstractInsnNode castInsn = previousInstruction(instruction);
                    require(isVarInstruction(castInsn, ALOAD, 0),
                            "normalizer only replaces the cast-aware shared resolver on this native cast");
                    InsnList replacement = new InsnList();
                    replacement.add(new IntInsnNode(SIPUSH, 650));
                    normalized.instructions.insertBefore(castInsn, replacement);
                    normalized.instructions.remove(castInsn);
                    normalized.instructions.remove(instruction);
                    replacements++;
                } else if ("luna/features/AutoAttackRuntime".equals(call.owner)
                        && "requestMinimum".equals(call.name) && "(I)I".equals(call.desc)) {
                    String tagSpec = required(combat, "request.tag.field");
                    int colon = tagSpec.indexOf(':');
                    String tagName = colon < 0 ? tagSpec : tagSpec.substring(0, colon);
                    String tagDesc = colon < 0 ? "I" : tagSpec.substring(colon + 1);
                    String tagOwner = combat.getProperty("request.tag.owner", required(combat, "request.owner"));
                    AbstractInsnNode tagInsn = previousInstruction(instruction);
                    AbstractInsnNode castInsn = previousInstruction(tagInsn);
                    require(tagInsn instanceof FieldInsnNode && tagInsn.getOpcode() == GETFIELD
                                    && tagOwner.equals(((FieldInsnNode) tagInsn).owner)
                                    && tagName.equals(((FieldInsnNode) tagInsn).name)
                                    && tagDesc.equals(((FieldInsnNode) tagInsn).desc),
                            "normalizer only recognizes the catalogued Auto Attack tag field");
                    require(isVarInstruction(castInsn, ALOAD, 0), "normalizer tag receiver remains the native cast");
                    InsnList replacement = new InsnList();
                    replacement.add(new IntInsnNode(SIPUSH, 650));
                    normalized.instructions.insertBefore(castInsn, replacement);
                    normalized.instructions.remove(castInsn);
                    normalized.instructions.remove(tagInsn);
                    normalized.instructions.remove(instruction);
                    replacements++;
                }
            }
            instruction = next;
        }
        require(replacements == 1, "normalizer replaces exactly one contextual request-minimum hook");
        normalized.name = oldRequest.name;
        normalized.desc = oldRequest.desc;
        normalized.access = oldRequest.access;
        normalized.signature = oldRequest.signature;
        normalized.exceptions = oldRequest.exceptions;
        normalized.maxStack = oldRequest.maxStack;
        normalized.maxLocals = oldRequest.maxLocals;
        for (AbstractInsnNode instruction : normalized.instructions.toArray()) {
            if (instruction instanceof org.objectweb.asm.tree.FrameNode
                    || instruction instanceof org.objectweb.asm.tree.LineNumberNode)
                normalized.instructions.remove(instruction);
        }
        normalized.localVariables = null;
        return Arrays.equals(methodBytes(oldOwner, oldRequest), methodBytes(newOwner, normalized));
    }

    private static void removeAutoAttackOwnedRequestDispatch(MethodNode request, Properties combat) {
        List<AbstractInsnNode> code = real(request);
        require(code.size() >= 7, "owned-request shim leaves the legacy request body in place");
        String castOwner = required(combat, "request.owner");
        require(isVarInstruction(code.get(0), ALOAD, 0)
                        && code.get(1) instanceof MethodInsnNode && code.get(1).getOpcode() == INVOKESTATIC,
                "owned-request shim asks the catalogued native ownership predicate");
        MethodInsnNode decision = (MethodInsnNode) code.get(1);
        require(required(combat, "hook.request.lock.owner").equals(decision.owner)
                        && required(combat, "hook.request.lock.method").equals(decision.name)
                        && required(combat, "hook.request.lock.descriptor").equals(decision.desc)
                        && ("(L" + castOwner + ";)Z").equals(decision.desc),
                "owned-request shim uses the exact catalogued lock-decision ABI for this cast");
        require(code.get(2) instanceof JumpInsnNode && code.get(2).getOpcode() == IFEQ,
                "legacy native requests branch around the synchronized ownership dispatcher");
        require(isVarInstruction(code.get(3), ALOAD, 0)
                        && code.get(4) instanceof MethodInsnNode && code.get(4).getOpcode() == INVOKESTATIC,
                "tagged native requests dispatch through the synchronized ownership bridge");
        MethodInsnNode dispatch = (MethodInsnNode) code.get(4);
        require(required(combat, "native.requestDispatch.owner").equals(dispatch.owner)
                        && required(combat, "native.requestDispatch.method").equals(dispatch.name)
                        && required(combat, "native.requestDispatch.descriptor").equals(dispatch.desc)
                        && ("(L" + castOwner + ";)V").equals(dispatch.desc),
                "owned-request shim calls the catalogued synchronized dispatcher with this cast");
        require(code.get(5).getOpcode() == RETURN
                        && nextInstruction(((JumpInsnNode) code.get(2)).label) == code.get(6),
                "owned requests return after dispatch and tag-zero requests resume at the original body");
        for (int i = 0; i < 6; i++) request.instructions.remove(code.get(i));
    }

    private static void removeAutoAttackStaleGuard(MethodNode request, Properties combat) {
        String tagSpec = required(combat, "request.tag.field");
        int colon = tagSpec.indexOf(':');
        String tagName = colon < 0 ? tagSpec : tagSpec.substring(0, colon);
        String tagDesc = colon < 0 ? "I" : tagSpec.substring(colon + 1);
        String castOwner = required(combat, "request.owner");
        String targetField = required(combat, "cast.target.field");
        String entityType = required(combat, "entity.owner");
        String bridgeOwner = combat.getProperty("bridge.owner", "luna/generated/HungAutoAttackAccess");
        List<AbstractInsnNode> code = real(request);
        int found = 0;
        for (AbstractInsnNode instruction = request.instructions.getFirst(); instruction != null; ) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction instanceof MethodInsnNode && instruction.getOpcode() == INVOKESTATIC) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if ("luna/features/AutoAttackRuntime".equals(call.owner)
                        && "allowRequest".equals(call.name)
                        && "(ILjava/lang/Object;)Z".equals(call.desc)) {
                    AbstractInsnNode targetGet = previousInstruction(instruction);
                    AbstractInsnNode targetLoad = previousInstruction(targetGet);
                    AbstractInsnNode tagGet = previousInstruction(targetLoad);
                    AbstractInsnNode tagLoad = previousInstruction(tagGet);
                    require(isVarInstruction(tagLoad, ALOAD, 0)
                                    && tagGet instanceof FieldInsnNode && tagGet.getOpcode() == GETFIELD,
                            "stale-cast guard loads its tag from the native cast instance");
                    FieldInsnNode tag = (FieldInsnNode) tagGet;
                    require(castOwner.equals(tag.owner) && tagName.equals(tag.name) && tagDesc.equals(tag.desc),
                            "stale-cast guard uses the catalogued generation field");
                    require(isVarInstruction(targetLoad, ALOAD, 0)
                                    && targetGet instanceof FieldInsnNode && targetGet.getOpcode() == GETFIELD,
                            "stale-cast guard supplies the captured target from the same cast");
                    FieldInsnNode target = (FieldInsnNode) targetGet;
                    require(castOwner.equals(target.owner) && targetField.equals(target.name)
                                    && ("L" + entityType + ";").equals(target.desc),
                            "stale-cast guard checks the exact captured native target");
                    AbstractInsnNode branch = nextInstruction(instruction);
                    AbstractInsnNode cancelReceiver = nextInstruction(branch);
                    AbstractInsnNode cancel = nextInstruction(cancelReceiver);
                    AbstractInsnNode returnInsn = nextInstruction(cancel);
                    require(branch instanceof JumpInsnNode && branch.getOpcode() == IFNE,
                            "accepted generation continues to the original native request");
                    require(isVarInstruction(cancelReceiver, ALOAD, 0)
                                    && cancel instanceof MethodInsnNode && cancel.getOpcode() == INVOKESTATIC,
                            "rejected stale generation clears its native cast before returning");
                    MethodInsnNode cancelCall = (MethodInsnNode) cancel;
                    require(bridgeOwner.equals(cancelCall.owner) && "cancelCast".equals(cancelCall.name)
                                    && ("(L" + castOwner + ";)V").equals(cancelCall.desc),
                            "stale-cast guard calls the generated native cleanup ABI");
                    require(returnInsn != null && returnInsn.getOpcode() == RETURN,
                            "rejected stale generation returns without enqueueing a cast");
                    AbstractInsnNode start = tagLoad;
                    AbstractInsnNode end = returnInsn;
                    for (AbstractInsnNode remove = start; remove != null; ) {
                        AbstractInsnNode after = remove.getNext();
                        request.instructions.remove(remove);
                        if (remove == end) break;
                        remove = after;
                    }
                    found++;
                }
            }
            instruction = next;
        }
        require(found == 1, "normalizer removes exactly one catalogued stale-cast guard");
    }

    private static AbstractInsnNode previousInstruction(AbstractInsnNode instruction) {
        instruction = instruction == null ? null : instruction.getPrevious();
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getPrevious();
        return instruction;
    }

    private static String candidateRequestMethod(Properties combat) {
        return autoAttackCatalog == null ? required(combat, "request.method")
                : required(autoAttackCatalog, "native.ownedRequest.method");
    }

    private static String candidateRequestDescriptor(Properties combat) {
        return autoAttackCatalog == null ? required(combat, "request.descriptor")
                : required(autoAttackCatalog, "native.ownedRequest.descriptor");
    }

    private static boolean isVarInstruction(AbstractInsnNode instruction, int opcode, int slot) {
        return instruction instanceof VarInsnNode && instruction.getOpcode() == opcode
                && ((VarInsnNode) instruction).var == slot;
    }

    private static byte[] methodBytes(ClassNode owner, MethodNode method) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(owner.version, owner.access, owner.name, owner.signature, owner.superName,
                owner.interfaces.toArray(new String[owner.interfaces.size()]));
        MethodVisitor visitor = writer.visitMethod(method.access, method.name, method.desc,
                method.signature, method.exceptions == null ? null
                        : method.exceptions.toArray(new String[method.exceptions.size()]));
        method.accept(visitor);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static int runtimeMinimum(File jar, int tag) throws Exception {
        URL[] urls = new URL[]{jar.toURI().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls, HungBySonRealTimeCombatHostTest.class.getClassLoader())) {
            Class<?> runtime = Class.forName("luna.features.AutoAttackRuntime", true, loader);
            Method minimumMethod = runtime.getDeclaredMethod("requestMinimum", Integer.TYPE);
            minimumMethod.setAccessible(true);
            return ((Integer) minimumMethod.invoke(null, Integer.valueOf(tag))).intValue();
        }
    }

    private static AbstractInsnNode nextInstruction(AbstractInsnNode instruction) {
        do { instruction = instruction == null ? null : instruction.getNext(); }
        while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    private static void verifyRemoteAndNpc(File jar, Properties combat) throws Exception {
        ClassNode owner = parse(readJarClass(jar, required(combat, "request.owner")));
        MethodNode request = findMethod(owner, candidateRequestMethod(combat), candidateRequestDescriptor(combat));
        int npcBranch = -1, localIdentity = -1, firstClock = -1, lastClock = -1, index = 0;
        for (AbstractInsnNode instruction : request.instructions.toArray()) {
            if (npcBranch < 0 && instruction instanceof FieldInsnNode && instruction.getOpcode() == GETFIELD
                    && required(combat, "entity.owner").equals(((FieldInsnNode)instruction).owner)
                    && "cv".equals(((FieldInsnNode)instruction).name)
                    && "B".equals(((FieldInsnNode)instruction).desc)) npcBranch = index;
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.getOpcode() == INVOKESTATIC && "java/lang/System".equals(call.owner)
                        && "currentTimeMillis".equals(call.name)) {
                    if (firstClock < 0) firstClock = index;
                    lastClock = index;
                }
            }
            if (instruction instanceof JumpInsnNode && instruction.getOpcode() == IF_ACMPNE
                    && localIdentity < 0) localIdentity = index;
            index++;
        }
        require(npcBranch >= 0 && firstClock > npcBranch,
                "NPC state-9 handling occurs before the real-time local-player clock gate");
        require(localIdentity >= 0 && firstClock > localIdentity && lastClock >= firstClock,
                "nonlocal actors branch around both direct clock reads");
        System.out.println("REMOTE_NPC=timing gate is scoped after state-9 and local-player checks");
    }

    private static boolean containsWaitGuard(MethodNode method, String owner, String flag) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == GETFIELD
                    && owner.equals(((FieldInsnNode)instruction).owner)
                    && flag.equals(((FieldInsnNode)instruction).name)
                    && "Z".equals(((FieldInsnNode)instruction).desc)) return true;
        }
        return false;
    }

    private static void requireOpcode6(int packetIndex, String message) {
        require(packetIndex >= 0 && packetIndex < packets.size(), message + " packet exists");
        String packet = packets.get(packetIndex);
        require(packet.startsWith("6:[") && packet.length() > 6,
                message + " has serialized opcode-6 bytes: " + packet);
    }

    private static int[] waitBoundaries(int interval) {
        int threshold = Math.max(minimum, interval);
        return new int[]{threshold-1, threshold};
    }

    public static Object provideSkill(int slot) { return currentSkill; }
    public static Object provideTargets() { return currentTargets; }
    public static Object provideQ() { return currentQ; }
    public static void captureDispatch(int action, int x, byte type, Object targets) {
        visualDispatches.add("dispatch:" + action + "," + x + "," + type + ",targets="
                + (targets == null ? "null" : targets.getClass().getName()));
    }

    public static void capture(Object packet) throws Exception {
        Class<?> message = packet.getClass();
        Field opcode = field(message, "a");
        Method payload = method(message, encoded("lliilil"), "()[B");
        byte[] bytes = (byte[])invoke(payload, packet);
        packets.add(opcode.getByte(packet) + ":" + Arrays.toString(bytes));
    }

    public static void captureEnqueue(Object sender) throws Exception {
        Field buffer = nativeBufferField(sender.getClass());
        Object message = buffer.get(sender);
        Class<?> type = message.getClass();
        byte opcode = field(type, "a").getByte(message);
        Method bytes = null;
        for (Method method : type.getDeclaredMethods()) {
            if (method.getParameterTypes().length == 0 && method.getReturnType() == byte[].class) {
                if (bytes != null) throw new IllegalStateException("ambiguous native message buffer byte getter");
                method.setAccessible(true); bytes = method;
            }
        }
        if (bytes == null) throw new NoSuchMethodException(type.getName() + "()[B");
        packets.add(opcode + ":" + Arrays.toString((byte[])invoke(bytes, message)));
    }

    private static final class RuntimeFixture {
        final JarClassLoader loader;
        final Properties combat;
        final Properties tempo;
        final Class<?> requestClass;
        final Class<?> entityClass;
        final Class<?> autoClass;
        final Class<?> autoOwner;
        final Class<?> scaleClass;
        final Class<?> speedOwner;
        final Class<?> advanceClass;
        final Method requestMethod;
        final Method advance;
        final Method entityTick;
        final Object requestInstance;
        final Object actor;
        final Object advanceReceiver;
        final Object skill;
        final Object targetList;
        final Object nativeQ;
        final NativeRoute nativeRoute;
        final int frameTarget = 1;
        final int nativeInterval;
        final String oldClockOwner;
        final String oldClockField;
        final FrameAdvance frameAdvance;

        RuntimeFixture(File jar, Properties combat, Properties tempo, int interval) throws Exception {
            this.combat = combat;
            this.tempo = tempo;
            this.nativeInterval = interval;
            this.loader = new JarClassLoader(jar, combat, tempo);
            String requestOwner = required(combat, "request.owner");
            requestClass = Class.forName(requestOwner.replace('/', '.'), true, loader);
            entityClass = Class.forName(required(combat, "entity.owner").replace('/', '.'), true, loader);
            autoOwner = Class.forName(required(combat, "auto.gate.owner").replace('/', '.'), true, loader);
            autoClass = autoOwner;
            scaleClass = tempo == null ? null : Class.forName(required(tempo, "candidate.scale.owner").replace('/', '.'), true, loader);
            speedOwner = tempo == null ? null : Class.forName(required(tempo, "candidate.speed.owner").replace('/', '.'), true, loader);
            requestMethod = method(requestClass, candidateRequestMethod(combat), candidateRequestDescriptor(combat));
            frameAdvance = findFrameAdvance(jar, combat);
            nativeRoute = findNativeRoute(jar, combat);
            advanceClass = Class.forName(frameAdvance.owner.replace('/', '.'), true, loader);
            advance = method(advanceClass, frameAdvance.method, frameAdvance.descriptor);
            advanceReceiver = Modifier.isStatic(advance.getModifiers()) ? null : unsafe().allocateInstance(advanceClass);
            if (advanceReceiver != null) {
                Field mode = field(advanceClass, frameAdvance.modeField);
                mode.setInt(advanceReceiver, 3);
            }
            Class<?> playerClass = Class.forName(required(combat, "player.owner").replace('/', '.'), true, loader);
            require(entityClass.isAssignableFrom(playerClass), "mapped player actor extends native entity type");
            actor = unsafe().allocateInstance(playerClass);
            requestInstance = unsafe().allocateInstance(requestClass);
            set(actor, entityClass, "cv", (byte)1);
            set(actor, entityClass, "cu", 1);
            set(actor, entityClass, "cG", 2);
            set(actor, entityClass, "cQ", frameTarget);
            set(actor, entityClass, required(combat, "request.controller.field"), requestInstance);
            set(actor, entityClass, frameAdvance.animationField, advanceReceiver);
            set(actor, entityClass, frameAdvance.animationBytesField, new byte[]{0});
            set(requestInstance, requestClass, required(combat, "cast.actor.field"), actor);
            Object targetActor = unsafe().allocateInstance(playerClass);
            set(targetActor, entityClass, "cv", (byte)0);
            set(targetActor, entityClass, "cu", 41);
            set(requestInstance, requestClass, required(combat, "cast.target.field"), targetActor);
            set(requestInstance, requestClass, required(combat, "cast.request.frame.field"), frameTarget);
            set(requestInstance, requestClass, "g", (byte)-1);
            set(requestInstance, requestClass, "k", 0);
            set(requestInstance, requestClass, "n", 0);
            Field singleton = field(loader.loadClass(required(combat, "player.singleton.owner").replace('/', '.')),
                    required(combat, "player.singleton.field"));
            singleton.set(null, actor);
            entityTick = method(entityClass, required(combat, "request.caller.method"),
                    required(combat, "request.caller.descriptor"));
            seedRequestArrays(jar, loader, combat);
            FieldInsnNode skillField = findSkillIntervalField(readJarClass(jar, requestOwner),
                    candidateRequestMethod(combat), candidateRequestDescriptor(combat));
            Class<?> skillClass = Class.forName(skillField.owner.replace('/', '.'), true, loader);
            skill = unsafe().allocateInstance(skillClass);
            field(skillClass, skillField.name).setShort(skill, (short)interval);
            currentSkill = skill;
            Class<?> listClass = Class.forName("et", true, loader);
            targetList = listClass.getDeclaredConstructor().newInstance();
            Class<?> targetClass = Class.forName(encoded("liilli").replace('/', '.'), true, loader);
            Object target = targetClass.getConstructor(short.class, byte.class)
                    .newInstance(Short.valueOf((short)41), Byte.valueOf((byte)0));
            invoke(method(listClass, encoded("l"), "(Ljava/lang/Object;)V"), targetList, target);
            currentTargets = targetList;
            Class<?> qClass = Class.forName(nativeRoute.owner.replace('/', '.'), true, loader);
            nativeQ = unsafe().allocateInstance(qClass);
            Class<?> bufferClass = Class.forName(BUFFER_OWNER.replace('/', '.'), true, loader);
            Object buffer = bufferClass.getDeclaredConstructor().newInstance();
            set(nativeQ, qClass, findNativeBufferFieldName(jar), buffer);
            currentQ = nativeQ;
            oldClockOwner = required(combat, "request.now.owner");
            oldClockField = required(combat, "request.now.field");
            poisonOldClock(Long.MIN_VALUE);
            packets.clear();
            visualDispatches.clear();
        }

        void setClockState(long timestamp, long oldClock) throws Exception {
            setStatic(requestClass, required(combat, "request.timestamp.field"), timestamp);
            setStatic(requestClass, required(combat, "request.delay.field"), (short)nativeInterval);
            poisonOldClock(oldClock);
        }

        void poisonOldClock(long value) throws Exception {
            Class<?> owner = Class.forName(oldClockOwner.replace('/', '.'), true, loader);
            setStatic(owner, oldClockField, value);
        }

        long requestTimestamp() throws Exception {
            return field(requestClass, required(combat, "request.timestamp.field")).getLong(null);
        }

        int frame() throws Exception { return field(entityClass, "cQ").getInt(actor); }
        boolean waitFlag() throws Exception {
            return field(requestClass, required(combat, "request.defer.flag.field")).getBoolean(requestInstance);
        }
        void setFrame(int frame) throws Exception { set(actor, entityClass, "cQ", frame); }

        void setSpeed(int code) throws Exception {
            field(speedOwner, required(tempo, "candidate.speed.field")).setInt(null, code);
        }

        void requestOnly() throws Exception { invoke(requestMethod, requestInstance); }
        void advanceOnly() throws Exception { invoke(advance, advanceReceiver, actor); }
        void callback() throws Exception { invoke(entityTick, actor); }

        void close() throws Exception { loader.close(); }
    }

    private static final class State {
        final List<FieldValue> requestFields;
        final List<FieldValue> actorFields;
        final Object timestamp;
        final Object interval;
        State(Object request, Object actor, Class<?> requestClass, Class<?> actorClass,
                String timestampName, String intervalName) throws Exception {
            requestFields = snapshot(request, requestClass, false);
            actorFields = snapshot(actor, actorClass, false);
            timestamp = field(requestClass, timestampName).get(null);
            interval = field(requestClass, intervalName).get(null);
        }
        void requireOnlyFrameChanged(Object request, Object actor, Class<?> requestClass, Class<?> actorClass,
                String timestampName, String intervalName, String waitFlag, int expectedFrame, String message) throws Exception {
            compare(requestFields, snapshot(request, requestClass, false), waitFlag, message + " request state");
            compare(actorFields, snapshot(actor, actorClass, false), "cQ", message + " actor state");
            require(field(actorClass, "cQ").getInt(actor) == expectedFrame, message + " frame");
            require(eq(timestamp, field(requestClass, timestampName).get(null)), message + " timestamp");
            require(eq(interval, field(requestClass, intervalName).get(null)), message + " native interval metadata");
        }
    }

    private static final class FieldValue {
        final Field field;
        final Object value;
        FieldValue(Field field, Object value) { this.field = field; this.value = value; }
    }

    private static void compare(List<FieldValue> before, List<FieldValue> after, String ignored, String message)
            throws Exception {
        require(before.size() == after.size(), message + " field topology");
        for (int i = 0; i < before.size(); i++) {
            FieldValue a = before.get(i), b = after.get(i);
            if (ignored != null && ignored.equals(a.field.getName())) continue;
            require(eq(a.value, b.value), message + " unchanged " + a.field.getName());
        }
    }

    private static List<FieldValue> snapshot(Object target, Class<?> type, boolean statics) throws Exception {
        List<FieldValue> result = new ArrayList<FieldValue>();
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (Field field : cursor.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) != statics) continue;
                field.setAccessible(true);
                result.add(new FieldValue(field, field.get(statics ? null : target)));
            }
        }
        return result;
    }

    private static final class FrameAdvance {
        final String owner, method, descriptor, modeField, animationField, animationBytesField;
        FrameAdvance(String owner, String method, String descriptor, String modeField,
                String animationField, String animationBytesField) {
            this.owner = owner; this.method = method; this.descriptor = descriptor; this.modeField = modeField;
            this.animationField = animationField; this.animationBytesField = animationBytesField;
        }
    }

    private static final class NativeRoute {
        final String owner, getter, getterDescriptor, attack, pk;
        NativeRoute(String owner, String getter, String getterDescriptor, String attack, String pk) {
            this.owner = owner; this.getter = getter; this.getterDescriptor = getterDescriptor;
            this.attack = attack; this.pk = pk;
        }
    }

    private static NativeRoute findNativeRoute(File jar, Properties combat) throws Exception {
        ClassNode node = parse(readJarClass(jar, required(combat, "request.owner")));
        MethodNode request = findMethod(node, candidateRequestMethod(combat), candidateRequestDescriptor(combat));
        List<MethodInsnNode> sends = new ArrayList<MethodInsnNode>();
        for (AbstractInsnNode instruction : request.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.getOpcode() == INVOKEVIRTUAL && "(Let;B)V".equals(call.desc)) sends.add(call);
            }
        }
        require(sends.size() == 2, "request retains exactly native opcode-6 and opcode-9 target senders");
        MethodInsnNode attack = sends.get(0), pk = sends.get(1);
        require(attack.owner.equals(pk.owner), "native opcode senders share a network serializer");
        MethodInsnNode getter = null;
        for (AbstractInsnNode instruction : request.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.getOpcode() == INVOKESTATIC && call.owner.equals(attack.owner)
                        && call.desc.equals("()L" + attack.owner + ";")) getter = call;
            }
        }
        require(getter != null, "native target serializers use a mapped singleton getter");
        return new NativeRoute(attack.owner, getter.name, getter.desc, attack.name, pk.name);
    }

    private static String findNativeBufferFieldName(File jar) throws Exception {
        ClassNode node = parse(readJarClass(jar, SENDER_OWNER));
        String descriptor = "L" + BUFFER_OWNER + ";";
        String found = null;
        for (org.objectweb.asm.tree.FieldNode field : node.fields) {
            if (descriptor.equals(field.desc)) {
                if (found != null) throw new IllegalStateException("ambiguous native message buffer field");
                found = field.name;
            }
        }
        if (found == null) throw new NoSuchFieldException(SENDER_OWNER + ".*" + descriptor);
        return found;
    }

    private static Field nativeBufferField(Class<?> type) throws Exception {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (Field field : cursor.getDeclaredFields()) {
                if (BUFFER_OWNER.replace('/', '.').equals(field.getType().getName())) {
                    field.setAccessible(true); return field;
                }
            }
        }
        throw new NoSuchFieldException(type.getName() + " native buffer");
    }

    private static FrameAdvance findFrameAdvance(File jar, Properties combat) throws Exception {
        ClassNode node = parse(readJarClass(jar, required(combat, "entity.owner")));
        MethodNode nativeTick = null;
        String callerName = required(combat, "request.caller.method");
        String callerDescriptor = required(combat, "request.caller.descriptor");
        for (MethodNode method : node.methods)
            if (callerDescriptor.equals(method.desc) && callerName.equals(method.name)) nativeTick = method;
        require(nativeTick != null, "mapped entity J()V callback exists");
        MethodInsnNode requestCall = null, advanceCall = null;
        for (AbstractInsnNode instruction : nativeTick.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.owner.equals(required(combat, "request.owner"))
                    && call.name.equals(required(combat, "request.method"))
                    && call.desc.equals(required(combat, "request.descriptor"))) requestCall = call;
            else if (requestCall != null && call.desc.equals("(L" + node.name + ";)V")) {
                advanceCall = call;
                break;
            }
        }
        require(requestCall != null && advanceCall != null,
                "native J()V calls request then its animation advance on the entity");
        require(nextMethodInsn(requestCall) == advanceCall,
                "request and frame advance are consecutive callback operations");
        String flagName = required(combat, "request.defer.flag.field");
        FieldInsnNode waitField = null;
        JumpInsnNode waitBranch = null;
        for (AbstractInsnNode instruction = requestCall.getNext(); instruction != null && instruction != advanceCall;
                instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == GETFIELD
                    && required(combat, "request.owner").equals(((FieldInsnNode)instruction).owner)
                    && flagName.equals(((FieldInsnNode)instruction).name)
                    && "Z".equals(((FieldInsnNode)instruction).desc)) waitField = (FieldInsnNode)instruction;
            if (waitField != null && instruction instanceof JumpInsnNode) waitBranch = (JumpInsnNode)instruction;
        }
        require(waitField != null && waitBranch != null && waitBranch.getOpcode() == IFNE,
                "actual J()V skips animation advance when codexWaitRealTime is true");
        require(instructionIndex(nativeTick, waitBranch.label) > instructionIndex(nativeTick, advanceCall),
                "wait branch target is after the single native animation advance");
        ClassNode helper = parse(readJarClass(jar, advanceCall.owner));
        MethodNode method = findMethod(helper, advanceCall.name, advanceCall.desc);
        String modeField = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == GETFIELD
                    && advanceCall.owner.equals(((FieldInsnNode)instruction).owner)
                    && "I".equals(((FieldInsnNode)instruction).desc)) {
                modeField = ((FieldInsnNode)instruction).name;
            }
        }
        String animationField = null, animationBytesField = null;
        boolean afterAdvance = false;
        for (AbstractInsnNode instruction : nativeTick.instructions.toArray()) {
            if (instruction == advanceCall) { afterAdvance = true; continue; }
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == GETFIELD
                    && node.name.equals(((FieldInsnNode)instruction).owner)
                    && ("L" + advanceCall.owner + ";").equals(((FieldInsnNode)instruction).desc))
                animationField = ((FieldInsnNode)instruction).name;
            if (afterAdvance && instruction instanceof FieldInsnNode && instruction.getOpcode() == GETFIELD
                    && node.name.equals(((FieldInsnNode)instruction).owner)
                    && "[B".equals(((FieldInsnNode)instruction).desc)) {
                animationBytesField = ((FieldInsnNode)instruction).name;
                break;
            }
        }
        require(modeField != null, "native animation advance has a state mode field");
        require(animationField != null && animationBytesField != null,
                "native J()V exposes its animation helper and post-advance byte frame array");
        require(hasIncrementTail(method, node.name, required(combat, "request.defer.frame.field")),
                "native animation method increments cQ after its frame-state switch");
        return new FrameAdvance(advanceCall.owner, advanceCall.name, advanceCall.desc, modeField,
                animationField, animationBytesField);
    }

    private static int instructionIndex(MethodNode method, AbstractInsnNode target) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction == target) return index;
            index++;
        }
        return -1;
    }

    private static boolean hasIncrementTail(MethodNode method, String entity, String frameField) {
        List<AbstractInsnNode> code = real(method);
        for (int i = 0; i + 5 < code.size(); i++) {
            int fieldIndex = i + 1;
            if (code.get(i).getOpcode() == ALOAD && ((VarInsnNode)code.get(i)).var == 1
                    && code.get(fieldIndex).getOpcode() == DUP) fieldIndex++;
            if (code.get(i).getOpcode() == ALOAD && ((VarInsnNode)code.get(i)).var == 1
                    && fieldInsn(code.get(fieldIndex), GETFIELD, entity, frameField, "I")
                    && code.get(fieldIndex + 1).getOpcode() == ICONST_1
                    && code.get(fieldIndex + 2).getOpcode() == IADD
                    && fieldInsn(code.get(fieldIndex + 3), PUTFIELD, entity, frameField, "I")) return true;
        }
        return false;
    }

    private static MethodInsnNode nextMethodInsn(MethodInsnNode source) {
        for (AbstractInsnNode instruction = source.getNext(); instruction != null; instruction = instruction.getNext())
            if (instruction instanceof MethodInsnNode) return (MethodInsnNode)instruction;
        return null;
    }

    private static FieldInsnNode findSkillIntervalField(byte[] bytes, String name, String descriptor) {
        ClassNode node = parse(bytes);
        MethodNode method = findMethod(node, name, descriptor);
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == GETFIELD
                    && "S".equals(((FieldInsnNode)instruction).desc)) return (FieldInsnNode)instruction;
        }
        throw new IllegalStateException("request has no native skill short interval field");
    }

    private static void seedRequestArrays(File jar, ClassLoader loader, Properties combat) throws Exception {
        ClassNode owner = parse(readJarClass(jar, required(combat, "request.owner")));
        MethodNode request = findMethod(owner, candidateRequestMethod(combat), candidateRequestDescriptor(combat));
        List<String> assigned = new ArrayList<String>();
        for (AbstractInsnNode instruction : request.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != GETSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode)instruction;
            Object value;
            if ("[I".equals(field.desc)) value = new int[64];
            else if ("[[I".equals(field.desc)) value = new int[8][64];
            else continue;
            String key = field.owner + "." + field.name;
            if (assigned.contains(key)) continue;
            Class<?> target = Class.forName(field.owner.replace('/', '.'), true, loader);
            setStaticObject(target, field.name, value);
            assigned.add(key);
        }
        require(!assigned.isEmpty(), "actual request has native skill/cost arrays seeded for local cast");
    }

    private static final class JarClassLoader extends URLClassLoader {
        private final File jarFile;
        private final Properties combat;
        private final Properties tempo;
        private byte[] bridgeBytes;
        JarClassLoader(File jar, Properties combat, Properties tempo) throws Exception {
            super(new URL[]{jar.toURI().toURL()}, HungBySonRealTimeCombatHostTest.class.getClassLoader());
            this.jarFile = jar;
            this.combat = combat;
            this.tempo = tempo;
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                if (BRIDGE.equals(name)) {
                    if (bridgeBytes == null) throw new ClassNotFoundException("bridge metadata not ready");
                    return defineClass(name, bridgeBytes, 0, bridgeBytes.length);
                }
                String internal = name.replace('.', '/');
                byte[] bytes = readJarClass(jarFile, internal);
                ClassNode node = parse(bytes);
                for (int i = node.methods.size() - 1; i >= 0; i--) {
                    MethodNode method = node.methods.get(i);
                    if ("<clinit>".equals(method.name)) {
                        node.methods.remove(i); // avoid native world, MIDP, and renderer initialization
                    }
                }
                if (combat != null && internal.equals(required(combat, "request.owner"))) transformCombat(node);
                if (autoAttackCatalog != null && combat != null
                        && internal.equals(required(combat, "entity.owner"))) transformEntityCallback(node);
                if (internal.equals(NETWORK_OWNER)) transformNetwork(node);
                if (internal.equals(SENDER_OWNER)) transformEnqueue(node);
                ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(out);
                byte[] changed = out.toByteArray();
                return defineClass(name, changed, 0, changed.length);
            } catch (ClassNotFoundException error) { throw error; }
            catch (Throwable error) { throw new ClassNotFoundException(name, error); }
        }
        private void transformCombat(ClassNode node) throws Exception {
            MethodNode request = findMethod(node, candidateRequestMethod(combat), candidateRequestDescriptor(combat));
            MethodNode auto = findMethod(node, required(combat, "auto.gate.method"),
                    required(combat, "auto.gate.descriptor"));
            MethodInsnNode skillCall = null, targetCall = null, dispatchCall = null;
            int clockCount = 0;
            for (AbstractInsnNode instruction : request.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.getOpcode() == INVOKESTATIC && "java/lang/System".equals(call.owner)
                        && "currentTimeMillis".equals(call.name) && "()J".equals(call.desc)) {
                    request.instructions.set(call, new FieldInsnNode(GETSTATIC, HOST, "now", "J"));
                    clockCount++;
                } else if (call.getOpcode() == INVOKESTATIC && call.desc.matches("\\(I\\)L[^;]+;")) {
                    skillCall = call;
                } else if (call.getOpcode() == INVOKEVIRTUAL && call.desc.startsWith("(IL")
                        && call.desc.endsWith(")Let;")) {
                    targetCall = call;
                } else if (call.getOpcode() == INVOKESTATIC && "(IIBLet;)V".equals(call.desc)) {
                    dispatchCall = call;
                }
            }
            int autoClocks = 0;
            for (AbstractInsnNode instruction : auto.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (call.getOpcode() == INVOKESTATIC && "java/lang/System".equals(call.owner)
                            && "currentTimeMillis".equals(call.name) && "()J".equals(call.desc)) {
                        auto.instructions.set(call, new FieldInsnNode(GETSTATIC, HOST, "now", "J"));
                        autoClocks++;
                    }
                }
            }
            require(clockCount == 2, "actual request has two direct clock reads");
            require(autoClocks == 1, "actual auto gate has one direct clock read");
            require(skillCall != null && targetCall != null && dispatchCall != null,
                    "request skill lookup, target provider, and native dispatch are mapped");
            NativeRoute route = findNativeRoute(jarFile, combat);
            bridgeBytes = makeBridge(skillCall, targetCall, dispatchCall, route);
            MethodInsnNode bridgeSkill = new MethodInsnNode(INVOKESTATIC, BRIDGE, "skill", skillCall.desc, false);
            request.instructions.set(skillCall, bridgeSkill);
            String targetDescriptor = "(L" + targetCall.owner + ";" + targetCall.desc.substring(1);
            request.instructions.set(targetCall,
                    new MethodInsnNode(INVOKESTATIC, BRIDGE, "targets", targetDescriptor, false));
            request.instructions.set(dispatchCall,
                    new MethodInsnNode(INVOKESTATIC, BRIDGE, "dispatch", dispatchCall.desc, false));
            int getterCalls = 0;
            for (AbstractInsnNode instruction : request.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (call.getOpcode() == INVOKESTATIC && call.owner.equals(route.owner)
                            && call.desc.equals(route.getterDescriptor)) {
                        request.instructions.set(call,
                                new MethodInsnNode(INVOKESTATIC, BRIDGE, "q", route.getterDescriptor, false));
                        getterCalls++;
                    }
                }
            }
            require(getterCalls >= 2, "native op6/op9 target senders use the deterministic Q instance; found "
                    + getterCalls + " for " + route.owner + route.getterDescriptor);
        }
        private void transformEntityCallback(ClassNode node) {
            String requestOwner = required(combat, "request.owner");
            String legacyName = required(combat, "request.method");
            String legacyDescriptor = required(combat, "request.descriptor");
            String ownedName = candidateRequestMethod(combat);
            int replaced = 0;
            for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.getOpcode() == INVOKEVIRTUAL && requestOwner.equals(call.owner)
                        && legacyName.equals(call.name) && legacyDescriptor.equals(call.desc)) {
                    method.instructions.set(call, new MethodInsnNode(INVOKEVIRTUAL, requestOwner,
                            ownedName, legacyDescriptor, false));
                    replaced++;
                }
            }
            require(replaced > 0, "Actual entity callback routes its send retry through the real owned request body");
        }
        private void transformNetwork(ClassNode node) {
            int found = 0;
            for (MethodNode method : node.methods) {
                if (NETWORK_METHOD.equals(method.name)
                        && ("(L" + MESSAGE_OWNER + ";)V").equals(method.desc)) {
                    method.instructions.clear();
                    method.tryCatchBlocks.clear();
                    method.localVariables = null;
                    method.instructions.add(new VarInsnNode(ALOAD, 1));
                    method.instructions.add(new MethodInsnNode(INVOKESTATIC, HOST, "capture",
                            "(Ljava/lang/Object;)V", false));
                    method.instructions.add(new InsnNode(RETURN));
                    method.maxLocals = 2;
                    method.maxStack = 1;
                    found++;
                }
            }
            require(found == 1, "one actual network sender redirected to the in-memory sink");
        }
        private void transformEnqueue(ClassNode node) {
            int found = 0;
            for (MethodNode method : node.methods) {
                if (SENDER_METHOD.equals(method.name) && "()V".equals(method.desc)) {
                    method.instructions.clear(); method.tryCatchBlocks.clear(); method.localVariables = null;
                    method.instructions.add(new VarInsnNode(ALOAD, 0));
                    method.instructions.add(new MethodInsnNode(INVOKESTATIC, HOST, "captureEnqueue",
                            "(Ljava/lang/Object;)V", false));
                    method.instructions.add(new InsnNode(RETURN));
                    method.maxLocals = 1; method.maxStack = 1; found++;
                }
            }
            require(found == 1, "actual native serializer enqueue redirected to byte-capturing memory sink");
        }
        private byte[] makeBridge(MethodInsnNode skill, MethodInsnNode targets, MethodInsnNode dispatch,
                NativeRoute route) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(V1_7, ACC_PUBLIC | ACC_FINAL, BRIDGE.replace('.', '/'), null, "java/lang/Object", null);
            MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "skill", skill.desc, null, null);
            method.visitCode();
            method.visitVarInsn(ILOAD, 0);
            method.visitMethodInsn(INVOKESTATIC, HOST, "provideSkill", "(I)Ljava/lang/Object;", false);
            method.visitTypeInsn(CHECKCAST, Type.getReturnType(skill.desc).getInternalName());
            method.visitInsn(ARETURN);
            method.visitMaxs(0, 0); method.visitEnd();

            String targetDesc = "(L" + targets.owner + ";" + targets.desc.substring(1);
            MethodVisitor target = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "targets", targetDesc, null, null);
            target.visitCode();
            target.visitMethodInsn(INVOKESTATIC, HOST, "provideTargets", "()Ljava/lang/Object;", false);
            target.visitTypeInsn(CHECKCAST, Type.getReturnType(targetDesc).getInternalName());
            target.visitInsn(ARETURN);
            target.visitMaxs(0, 0); target.visitEnd();

            MethodVisitor send = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "dispatch", dispatch.desc, null, null);
            send.visitCode();
            send.visitVarInsn(ILOAD, 0);
            send.visitVarInsn(ILOAD, 1);
            send.visitVarInsn(ILOAD, 2);
            send.visitVarInsn(ALOAD, 3);
            send.visitMethodInsn(INVOKESTATIC, HOST, "captureDispatch",
                    "(IIBLjava/lang/Object;)V", false);
            send.visitInsn(RETURN);
            send.visitMaxs(0, 0); send.visitEnd();

            MethodVisitor q = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "q", route.getterDescriptor, null, null);
            q.visitCode();
            q.visitMethodInsn(INVOKESTATIC, HOST, "provideQ", "()Ljava/lang/Object;", false);
            q.visitTypeInsn(CHECKCAST, route.owner);
            q.visitInsn(ARETURN);
            q.visitMaxs(0, 0); q.visitEnd();
            writer.visitEnd();
            return writer.toByteArray();
        }
    }

    private static byte[] readJarClass(File jarFile, String internalName) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry(internalName + ".class");
            if (entry == null) throw new IllegalStateException("class missing from candidate: " + internalName);
            try (InputStream input = jar.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
                return out.toByteArray();
            }
        }
    }

    private static ClassNode parse(byte[] bytes) {
        ClassNode node = new ClassNode(ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods)
            if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        throw new IllegalStateException("method missing: " + node.name + "." + name + descriptor);
    }

    private static Method method(Class<?> owner, String name, String descriptor) throws Exception {
        for (Method method : owner.getDeclaredMethods()) {
            if (name.equals(method.getName()) && descriptor.equals(Type.getMethodDescriptor(method))) {
                method.setAccessible(true); return method;
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name + descriptor);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        for (Class<?> cursor = owner; cursor != null; cursor = cursor.getSuperclass()) {
            try { Field field = cursor.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        field(owner, name).set(target, value);
    }

    private static void setStatic(Class<?> owner, String name, Object value) throws Exception {
        field(owner, name).set(null, value);
    }

    private static void setStaticObject(Class<?> owner, String name, Object value) throws Exception {
        Field target = field(owner, name);
        try { target.set(null, value); }
        catch (IllegalAccessException finalField) {
            sun.misc.Unsafe memory = unsafe();
            memory.putObject(memory.staticFieldBase(target), memory.staticFieldOffset(target), value);
        }
    }

    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try { return method.invoke(receiver, args); }
        catch (java.lang.reflect.InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new RuntimeException(cause);
        }
    }

    private static boolean fieldInsn(AbstractInsnNode instruction, int opcode, String owner, String name, String desc) {
        if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != opcode) return false;
        FieldInsnNode field = (FieldInsnNode)instruction;
        return owner.equals(field.owner) && name.equals(field.name) && desc.equals(field.desc);
    }

    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }

    private static String speedLabel(int code) {
        for (int i = 0; i < SPEED_CODES.length; i++) if (SPEED_CODES[i] == code) return SPEED_LABELS[i];
        return Integer.toString(code);
    }

    private static int[] expectedUpdateCounts() {
        int[] result = new int[SPEED_CODES.length];
        for (int i = 0; i < result.length; i++) result[i] = expectedUpdateCount(SPEED_CODES[i]);
        return result;
    }

    private static int expectedUpdateCount(int code) {
        long accumulator = 0L, fraction = 0L;
        int updates = 0;
        for (int tick = 0; tick < 100; tick++) {
            long scaled;
            if (code >= 0) scaled = 16L * code;
            else {
                long product = 16L * (-(long)code) + fraction;
                fraction = product % 10L;
                scaled = product / 10L;
            }
            accumulator += scaled * 2L;
            int inPass = 0;
            while (accumulator >= 40L && inPass < maxUpdatesPerPass) {
                accumulator -= 40L;
                updates++;
                inPass++;
            }
            if (accumulator >= backlogDropMs) accumulator = 0L;
        }
        return updates;
    }

    private static int[] parseInts(String value) {
        String[] split = value.split(","); int[] result = new int[split.length];
        for (int i = 0; i < split.length; i++) result[i] = Integer.parseInt(split[i].trim());
        return result;
    }

    private static String[] parseStrings(String value) {
        String[] result = value.split(",");
        for (int i = 0; i < result.length; i++) result[i] = result[i].trim();
        return result;
    }

    private static Properties load(File file) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file.toPath())) { properties.load(input); }
        return properties;
    }

    private static String required(Properties properties, String key) {
        if (properties == null) throw new IllegalStateException("catalog not supplied for " + key);
        String value = properties.getProperty(key);
        if (value == null) throw new IllegalStateException("catalog entry missing: " + key);
        return value.trim();
    }

    private static String hash(File file) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02X", Integer.valueOf(value & 255)));
        return result.toString();
    }

    private static String encoded(String suffix) {
        StringBuilder result = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) result.append('i');
        return result.append(suffix).toString();
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe)field.get(null);
    }

    private static boolean eq(Object first, Object second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first.getClass().isArray() && second.getClass().isArray()) return first == second;
        return first.equals(second);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
