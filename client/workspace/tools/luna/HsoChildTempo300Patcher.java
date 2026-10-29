import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Combines the 300 ms real-time combat profile with the HSO CHILD x1/x2/x3/x5/x10 menu. */
public final class HsoChildTempo300Patcher implements Opcodes {
    private static final int BEFORE_MS = 650;
    private static final int AFTER_MS = 300;
    private static final int[] OLD_CODES = {1, 2, 3, -8, -9};
    private static final int[] NEW_CODES = {1, 2, 3, 5, 10};
    private static final String[] OLD_LABELS = {"1", "2", "3", "0.8", "0.9"};
    private static final String[] NEW_LABELS = {"1", "2", "3", "5", "10"};
    private static final int MAX_UPDATES = 10;
    private static final long OLD_BACKLOG_DROP_MS = 120L;
    private static final long NEW_BACKLOG_DROP_MS = 400L;
    private static final String TIMING_SYMBOLS = "symbols/hung-real650-20261006.properties";
    private static final String TEMPO_SYMBOLS = "symbols/hung-tempo-menu-fixed-20261004.properties";

    private HsoChildTempo300Patcher() { }

    public static void main(String[] args) throws Exception {
        check(args.length == 4,
                "usage: input.jar output.jar report.properties expected-input-sha256");
        final File input = new File(args[0]).getAbsoluteFile();
        final File output = new File(args[1]).getAbsoluteFile();
        final File report = new File(args[2]).getAbsoluteFile();
        final String expected = normalizeSha(args[3]);
        check(input.isFile(), "Input JAR is missing");
        check(!output.exists() && !report.exists(), "Output JAR and report must be new files");
        check(!input.equals(output), "Input and output paths must differ");
        check(HungBySonNativeTimingPatcher.hash(input).equals(expected),
                "Input JAR hash differs from the caller-supplied stage hash");

        File parent = output.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        File reportParent = report.getParentFile();
        if (reportParent != null) Files.createDirectories(reportParent.toPath());
        File finalStage = new File(output.getPath() + ".tempo300-stage.jar");
        File reportStage = new File(report.getPath() + ".tmp");
        check(!finalStage.exists() && !reportStage.exists(), "A patcher staging file already exists");
        try {
            Properties timingSymbols = load(new File(TIMING_SYMBOLS));
            Properties tempoSymbols = load(new File(TEMPO_SYMBOLS));
            verifyPinnedMaps(timingSymbols, tempoSymbols);
            Properties combined = new Properties();
            for (String key : timingSymbols.stringPropertyNames())
                combined.setProperty(key, timingSymbols.getProperty(key));
            LinkedHashMap<String, Long> times = new LinkedHashMap<String, Long>();
            LinkedHashMap<String, byte[]> stageEntries = HungBySonNativeTimingPatcher.readJar(input, times);
            LinkedHashMap<String, byte[]> entries = new LinkedHashMap<String, byte[]>(stageEntries);

            String timingOwner = required(timingSymbols, "cast.owner");
            String menuOwner = required(tempoSymbols, "candidate.menu.owner");
            String speedOwner = required(tempoSymbols, "candidate.speed.owner");
            String speedField = required(tempoSymbols, "candidate.speed.field");
            String canvasOwner = required(tempoSymbols, "candidate.canvas.owner");
            String timingEntry = timingOwner + ".class";
            String menuEntry = menuOwner + ".class";
            String speedEntry = speedOwner + ".class";
            String canvasEntry = canvasOwner + ".class";
            check(entries.containsKey(timingEntry) && entries.containsKey(menuEntry)
                    && entries.containsKey(speedEntry) && entries.containsKey(canvasEntry),
                    "Combat timing, tempo menu, speed, or game loop class is absent");

            byte[] timingOriginal = entries.get(timingEntry);
            byte[] timingPatched = patchTiming(timingOriginal, timingSymbols, BEFORE_MS, AFTER_MS);
            byte[] timingRestored = patchTiming(timingPatched, timingSymbols, AFTER_MS, BEFORE_MS);
            check(Arrays.equals(HungBySonNativeTimingPatcher.canonical(cls(timingOriginal)),
                    HungBySonNativeTimingPatcher.canonical(cls(timingRestored))),
                    "Combat-timing inverse did not reproduce the original class");

            byte[] menuOriginal = entries.get(menuEntry);
            byte[] menuPatched = patchMenu(menuOriginal, entries.get(speedEntry), tempoSymbols, OLD_CODES, NEW_CODES,
                    OLD_LABELS, NEW_LABELS);
            byte[] menuRestored = patchMenu(menuPatched, entries.get(speedEntry), tempoSymbols, NEW_CODES, OLD_CODES,
                    NEW_LABELS, OLD_LABELS);
            check(Arrays.equals(HungBySonNativeTimingPatcher.canonical(cls(menuOriginal)),
                    HungBySonNativeTimingPatcher.canonical(cls(menuRestored))),
                    "Tempo menu inverse did not reproduce the original class");

            byte[] canvasOriginal = entries.get(canvasEntry);
            byte[] canvasPatched = patchLoop(canvasOriginal, tempoSymbols, 3, MAX_UPDATES,
                    OLD_BACKLOG_DROP_MS, NEW_BACKLOG_DROP_MS);
            byte[] canvasRestored = patchLoop(canvasPatched, tempoSymbols, MAX_UPDATES, 3,
                    NEW_BACKLOG_DROP_MS, OLD_BACKLOG_DROP_MS);
            check(Arrays.equals(HungBySonNativeTimingPatcher.canonical(cls(canvasOriginal)),
                    HungBySonNativeTimingPatcher.canonical(cls(canvasRestored))),
                    "Game-loop cap inverse did not reproduce the original class");
            entries.put(timingEntry, timingPatched);
            entries.put(menuEntry, menuPatched);
            entries.put(canvasEntry, canvasPatched);

            writeJar(finalStage, entries, times);
            LinkedHashMap<String, Long> finalTimes = new LinkedHashMap<String, Long>();
            LinkedHashMap<String, byte[]> saved = HungBySonNativeTimingPatcher.readJar(finalStage, finalTimes);
            check(saved.keySet().equals(entries.keySet()), "Archive topology changed");
            Set<String> expectedChanges = new LinkedHashSet<String>();
            expectedChanges.add(timingEntry);
            expectedChanges.add(menuEntry);
            expectedChanges.add(canvasEntry);
            int changes = 0;
            for (String name : entries.keySet()) {
                check(Arrays.equals(entries.get(name), saved.get(name)), "Saved entry differs: " + name);
                byte[] before = stageEntries.get(name);
                if (!Arrays.equals(before, saved.get(name))) {
                    check(expectedChanges.contains(name), "Unexpected entry changed: " + name);
                    check(cls(before).version == cls(saved.get(name)).version,
                            "Class version changed: " + name);
                    changes++;
                }
            }
            check(changes == expectedChanges.size(), "Unexpected number of changed classes");

            combined.setProperty("input.sha256", expected);
            combined.setProperty("output.sha256", HungBySonNativeTimingPatcher.hash(finalStage));
            combined.setProperty("changed.classes", timingOwner + "," + canvasOwner + "," + menuOwner);
            combined.setProperty("auto.delay.before.ms", String.valueOf(BEFORE_MS));
            combined.setProperty("auto.delay.after.ms", String.valueOf(AFTER_MS));
            combined.setProperty("auto.delay.ms", String.valueOf(AFTER_MS));
            combined.setProperty("request.minimum.ms", String.valueOf(AFTER_MS));
            combined.setProperty("request.extra.ms", "0");
            copyCandidateProperties(tempoSymbols, combined);
            combined.setProperty("candidate.speed.codes", join(NEW_CODES));
            combined.setProperty("candidate.speed.labels", join(NEW_LABELS));
            combined.setProperty("candidate.speed.menuLabels", "x1,x2,x3,x5,x10");
            combined.setProperty("candidate.menu.childActions", "101,102,103,104,105");
            combined.setProperty("candidate.loop.maxUpdatesPerPass", String.valueOf(MAX_UPDATES));
            combined.setProperty("candidate.loop.backlogDropMs", String.valueOf(NEW_BACKLOG_DROP_MS));
            combined.setProperty("candidate.scale.mode", "elapsed multiplied by selected integer speed");
            combined.setProperty("tempo.source.input.sha256", required(tempoSymbols, "input.sha256"));
            combined.setProperty("tempo.source.output.sha256", required(tempoSymbols, "output.sha256"));
            store(combined, reportStage, "HSO CHILD real-time 300 ms and x1/x2/x3/x5/x10 profile");

            Files.move(finalStage.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE);
            Files.move(reportStage.toPath(), report.toPath(), StandardCopyOption.ATOMIC_MOVE);
            check(HungBySonNativeTimingPatcher.hash(output).equals(combined.getProperty("output.sha256")),
                    "Committed output hash differs from report");
            System.out.println("AUTO=300ms real; REQUEST=strict >max(300,nativeSkillInterval), pending frame retained");
            System.out.println("SPEEDS=x1/x2/x3/x5/x10; elapsed scale=1/2/3/5/10; loop cap=10 updates/pass");
            System.out.println("SCOPE=" + changes + " classes; inverse canonical PASS; class versions preserved");
            System.out.println("OUTPUT_SHA256=" + combined.getProperty("output.sha256"));
        } finally {
            Files.deleteIfExists(finalStage.toPath());
            Files.deleteIfExists(reportStage.toPath());
        }
    }

    private static byte[] patchTiming(byte[] original, Properties symbols, int fromMs, int toMs) {
        String owner = required(symbols, "cast.owner");
        String autoField = required(symbols, "auto.delay.field");
        String requestName = required(symbols, "request.method");
        String requestDescriptor = required(symbols, "request.descriptor");
        String requestDelayOwner = required(symbols, "request.delay.owner");
        String requestDelayField = required(symbols, "request.delay.field");
        check(owner.equals(required(symbols, "auto.delay.owner"))
                && owner.equals(required(symbols, "request.owner")),
                "Expected a single native cast/request timing owner");
        check(owner.equals(requestDelayOwner), "Native skill interval field owner changed");
        check("I".equals(required(symbols, "auto.delay.descriptor")), "Auto gate field descriptor changed");
        check("S".equals(required(symbols, "request.delay.descriptor")), "Native skill interval descriptor changed");
        check("()V".equals(requestDescriptor), "Native request method descriptor changed");

        ClassNode cast = cls(original);
        check(cast.name.equals(owner), "Unexpected native combat class owner");
        declaredField(cast, autoField, "I", true);
        declaredField(cast, requestDelayField, "S", true);
        IntInsnNode auto = HungBySonNativeTimingPatcher.initializer(cast, symbols);
        check(auto.getOpcode() == SIPUSH && auto.operand == fromMs,
                "Unexpected real-time auto gate initializer: expected " + fromMs + " ms");
        MethodNode autoGate = method(cast, required(symbols, "auto.gate.method"),
                required(symbols, "auto.gate.descriptor"));
        checkAutoGateUsesInitializer(autoGate, owner, autoField);

        FieldNode pending = declaredField(cast, required(symbols, "request.defer.flag.field"), "Z");
        check((pending.access & ACC_PUBLIC) != 0, "Pending-frame flag ABI changed");
        MethodNode request = method(cast, requestName, requestDescriptor);
        AbstractInsnNode[] prefix = firstReal(request, 3);
        check(prefix[0] instanceof VarInsnNode && prefix[0].getOpcode() == ALOAD
                && ((VarInsnNode)prefix[0]).var == 0 && prefix[1].getOpcode() == ICONST_0
                && field(prefix[2], PUTFIELD, owner, pending.name, "Z"),
                "Native request no longer resets the pending-frame flag at entry");

        FieldInsnNode interval = HungBySonNativeTimingPatcher.threshold(request, symbols);
        check(interval.owner.equals(requestDelayOwner) && interval.name.equals(requestDelayField)
                && interval.desc.equals("S"), "Native request no longer reads the skill cooldown interval");
        AbstractInsnNode amount = next(interval);
        AbstractInsnNode max = next(amount);
        AbstractInsnNode convert = next(max);
        AbstractInsnNode compare = next(convert);
        AbstractInsnNode blocked = next(compare);
        check(amount instanceof IntInsnNode && amount.getOpcode() == SIPUSH
                && ((IntInsnNode)amount).operand == fromMs
                && call(max, "java/lang/Math", "max", "(II)I")
                && convert.getOpcode() == I2L && compare.getOpcode() == LCMP
                && blocked instanceof JumpInsnNode && blocked.getOpcode() == IFLE,
                "Expected exact strict >max(" + fromMs + ",nativeSkillInterval) request gate");
        JumpInsnNode local = HungBySonRealTimeCombatPatcher.localJump(request, symbols);
        check(local.getOpcode() == IF_ACMPNE, "Native local-actor identity branch changed");
        AbstractInsnNode deferredActor = next(((JumpInsnNode)blocked).label);
        AbstractInsnNode deferredValue = next(deferredActor);
        AbstractInsnNode deferredWrite = next(deferredValue);
        AbstractInsnNode deferredReturn = next(deferredWrite);
        check(deferredActor instanceof VarInsnNode && deferredActor.getOpcode() == ALOAD
                && ((VarInsnNode)deferredActor).var == 0 && deferredValue.getOpcode() == ICONST_1
                && field(deferredWrite, PUTFIELD, owner, pending.name, "Z")
                && deferredReturn.getOpcode() == RETURN,
                "Native send-frame retry block changed");
        int clocks = 0;
        for (AbstractInsnNode i : request.instructions.toArray()) {
            if (call(i, "java/lang/System", "currentTimeMillis", "()J")) clocks++;
        }
        check(clocks == 2, "Expected direct real-clock reads for comparison and accepted timestamp");

        auto.operand = toMs;
        request.instructions.set(amount, push(toMs));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cast.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] patchMenu(byte[] original, byte[] speedClassBytes, Properties symbols, int[] fromCodes,
            int[] toCodes, String[] fromLabels, String[] toLabels) {
        check(fromCodes.length == 5 && toCodes.length == 5 && fromLabels.length == 5
                && toLabels.length == 5, "Expected exactly five speed options");
        String menuOwner = required(symbols, "candidate.menu.owner");
        String speedOwner = required(symbols, "candidate.speed.owner");
        String speedField = required(symbols, "candidate.speed.field");
        check("I".equals(required(symbols, "candidate.speed.desc")), "Speed field descriptor must be I");
        ClassNode menu = cls(original);
        ClassNode speedClass = cls(speedClassBytes);
        check(menu.name.equals(menuOwner), "Unexpected tempo menu class owner");
        check(speedClass.name.equals(speedOwner), "Unexpected speed-field owner");
        declaredField(speedClass, speedField, "I", true);
        MethodSignature actionSignature = parseMethod(required(symbols, "candidate.menu.action"));
        check("(II)Z".equals(actionSignature.descriptor), "Native menu callback descriptor changed");
        MethodNode action = method(menu, actionSignature.name, actionSignature.descriptor);
        MethodNode builder = method(menu, "codexBuildTempoMenu", "()V");
        MethodNode formatter = method(menu, "codexSpeedText", "(I)Ljava/lang/String;");

        // The menu class must reference only the catalogued static integer field.
        int actionWrites = 0;
        List<AbstractInsnNode> actionValueNodes = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode i : action.instructions.toArray()) {
            if (speed(i, speedOwner, speedField, PUTSTATIC)) {
                AbstractInsnNode value = previous(i);
                check(value != null, "Tempo selection has no integer speed value");
                actionValueNodes.add(value);
                actionWrites++;
            }
        }
        check(actionWrites == 5, "Expected five speed writes in the exact menu action method");
        assertAndReplaceValues(action.instructions, actionValueNodes, fromCodes, toCodes, "menu speed writes");

        int markerReads = 0;
        List<AbstractInsnNode> markerValueNodes = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode i : builder.instructions.toArray()) {
            if (speed(i, speedOwner, speedField, GETSTATIC)) {
                AbstractInsnNode value = next(i);
                check(value != null, "Tempo marker has no selected-value comparison");
                markerValueNodes.add(value);
                markerReads++;
            }
        }
        check(markerReads == 5, "Expected five selected-value comparisons in the tempo menu");
        assertAndReplaceValues(builder.instructions, markerValueNodes, fromCodes, toCodes, "selected menu markers");

        int[] rowActions = new int[5];
        int rowCount = 0;
        String callbackOwner = null, callbackName = null, callbackDescriptor = null;
        String buttonConstructorDescriptor = null;
        for (AbstractInsnNode i : builder.instructions.toArray()) {
            if (!(i instanceof MethodInsnNode) || i.getOpcode() != INVOKESPECIAL) continue;
            MethodInsnNode call = (MethodInsnNode)i;
            if (!"bt".equals(call.owner) || !"<init>".equals(call.name)
                    || !call.desc.startsWith("(Ljava/lang/String;I")) continue;
            check(rowCount < rowActions.length, "Too many native tempo rows");
            AbstractInsnNode callback = previous(i);
            AbstractInsnNode id = previous(callback);
            check(id != null, "Tempo row lacks a native button action ID");
            check(callback instanceof FieldInsnNode && callback.getOpcode() == GETSTATIC
                    && ((FieldInsnNode)callback).desc.startsWith("L"),
                    "Tempo button does not load its native callback field");
            FieldInsnNode callbackField = (FieldInsnNode)callback;
            if (buttonConstructorDescriptor == null) buttonConstructorDescriptor = call.desc;
            else check(buttonConstructorDescriptor.equals(call.desc),
                    "Tempo rows use different native button constructor descriptors");
            if (callbackOwner == null) {
                callbackOwner = callbackField.owner;
                callbackName = callbackField.name;
                callbackDescriptor = callbackField.desc;
            } else check(callbackOwner.equals(callbackField.owner) && callbackName.equals(callbackField.name)
                    && callbackDescriptor.equals(callbackField.desc),
                    "Tempo rows use more than one native callback field");
            rowActions[rowCount++] = integer(id);
        }
        check(rowCount == 5 && Arrays.equals(rowActions, new int[]{101, 102, 103, 104, 105}),
                "Native button actions changed or became ambiguous");

        int oldText = 0;
        for (AbstractInsnNode i : builder.instructions.toArray()) {
            if (i instanceof LdcInsnNode && ((LdcInsnNode)i).cst instanceof String) {
                LdcInsnNode text = (LdcInsnNode)i;
                for (int n = 0; n < fromLabels.length; n++) {
                    String before = "Tốc độ x" + fromLabels[n];
                    if (before.equals(text.cst)) {
                        text.cst = "Tốc độ x" + toLabels[n];
                        oldText++;
                        break;
                    }
                }
            }
        }
        check(oldText == 5, "Expected exactly the five native tempo row labels");
        patchFormatter(formatter, fromCodes, toCodes, fromLabels, toLabels);
        verifyRootFormatter(menu, speedOwner, speedField);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        menu.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] patchLoop(byte[] original, Properties symbols, int fromCap, int toCap,
            long fromDropMs, long toDropMs) {
        String canvasOwner = required(symbols, "candidate.canvas.owner");
        check("run()V".equals(required(symbols, "candidate.canvas.run")),
                "Unexpected native game-loop descriptor");
        ClassNode canvas = cls(original);
        check(canvas.name.equals(canvasOwner), "Unexpected game-loop class owner");
        declaredField(canvas, "codexTempoAccumulator", "J", false);
        declaredField(canvas, "codexTempoFraction", "J", false);
        declaredField(canvas, "codexTempoFractionSpeed", "I", false);
        MethodNode run = method(canvas, "run", "()V");
        List<AbstractInsnNode> caps = new ArrayList<AbstractInsnNode>();
        int backlogChecks = 0;
        for (AbstractInsnNode i : run.instructions.toArray()) {
            if (i instanceof JumpInsnNode && i.getOpcode() == IF_ICMPGE) {
                AbstractInsnNode value = previous(i);
                AbstractInsnNode counter = previous(value);
                if (value != null && counter != null && integer(value) == fromCap
                        && counter instanceof VarInsnNode && counter.getOpcode() == ILOAD)
                    caps.add(value);
            }
            if (i instanceof LdcInsnNode && Long.valueOf(fromDropMs).equals(((LdcInsnNode)i).cst)) {
                AbstractInsnNode compare = next(i);
                AbstractInsnNode branch = next(compare);
                check(compare != null && compare.getOpcode() == LCMP && branch instanceof JumpInsnNode
                        && branch.getOpcode() == IFLT, "120 ms game-loop backlog guard changed");
                backlogChecks++;
            }
        }
        check(caps.size() == 1, "Expected one native per-pass update cap of " + fromCap);
        check(backlogChecks == 1, "Expected one native backlog reset at " + fromDropMs + " ms");
        run.instructions.set(caps.get(0), push(toCap));
        int dropConstants = 0;
        for (AbstractInsnNode i : run.instructions.toArray())
            if (i instanceof LdcInsnNode && Long.valueOf(fromDropMs).equals(((LdcInsnNode)i).cst)) {
                ((LdcInsnNode)i).cst = Long.valueOf(toDropMs);
                dropConstants++;
            }
        check(dropConstants == 1, "Expected one exact backlog reset constant");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        canvas.accept(writer);
        return writer.toByteArray();
    }

    private static void patchFormatter(MethodNode formatter, int[] fromCodes, int[] toCodes,
            String[] fromLabels, String[] toLabels) {
        List<AbstractInsnNode> values = new ArrayList<AbstractInsnNode>();
        List<LdcInsnNode> labels = new ArrayList<LdcInsnNode>();
        for (AbstractInsnNode i : formatter.instructions.toArray()) {
            if (!(i instanceof VarInsnNode) || i.getOpcode() != ILOAD || ((VarInsnNode)i).var != 0) continue;
            AbstractInsnNode code = next(i);
            AbstractInsnNode branch = next(code);
            if (branch instanceof JumpInsnNode && branch.getOpcode() == IF_ICMPNE) {
                AbstractInsnNode label = next(branch);
                AbstractInsnNode result = next(label);
                check(label instanceof LdcInsnNode && ((LdcInsnNode)label).cst instanceof String
                        && result != null && result.getOpcode() == ARETURN,
                        "Speed formatter branch shape changed");
                values.add(code);
                labels.add((LdcInsnNode)label);
            }
        }
        check(values.size() == 5 && labels.size() == 5, "Expected five formatter choices");
        assertAndReplaceValues(formatter.instructions, values, fromCodes, toCodes,
                "root speed formatter codes");
        for (int i = 0; i < labels.size(); i++) {
            check(fromLabels[i].equals(labels.get(i).cst), "Unexpected speed formatter text at option " + i);
            labels.get(i).cst = toLabels[i];
        }
        AbstractInsnNode fallback = previous(formatter.instructions.getLast());
        check(fallback instanceof LdcInsnNode && "1".equals(((LdcInsnNode)fallback).cst),
                "Speed formatter fallback changed");
    }

    private static void verifyRootFormatter(ClassNode menu, String speedOwner, String speedField) {
        int calls = 0;
        for (MethodNode m : menu.methods) {
            if (m.name.equals("codexSpeedText")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (!speed(i, speedOwner, speedField, GETSTATIC)) continue;
                AbstractInsnNode formatter = next(i);
                if (formatter instanceof MethodInsnNode && formatter.getOpcode() == INVOKESTATIC
                        && ((MethodInsnNode)formatter).owner.equals(menu.name)
                        && ((MethodInsnNode)formatter).name.equals("codexSpeedText")
                        && ((MethodInsnNode)formatter).desc.equals("(I)Ljava/lang/String;")) {
                    AbstractInsnNode append = next(formatter);
                    check(append instanceof MethodInsnNode && ((MethodInsnNode)append).owner.equals("java/lang/StringBuilder")
                            && ((MethodInsnNode)append).name.equals("append")
                            && ((MethodInsnNode)append).desc.equals("(Ljava/lang/String;)Ljava/lang/StringBuilder;"),
                            "Root caption no longer appends the formatted speed string");
                    calls++;
                }
            }
        }
        check(calls == 1, "Expected one native root-caption formatter call");
    }

    private static void checkAutoGateUsesInitializer(MethodNode gate, String owner, String field) {
        int reads = 0;
        for (AbstractInsnNode i : gate.instructions.toArray())
            if (field(i, GETSTATIC, owner, field, "I")) reads++;
        check(reads == 1, "Auto gate must read its initialized timing field exactly once");
    }

    private static AbstractInsnNode[] firstReal(MethodNode method, int count) {
        AbstractInsnNode[] result = new AbstractInsnNode[count];
        int found = 0;
        for (AbstractInsnNode i : method.instructions.toArray()) {
            if (i.getOpcode() < 0) continue;
            if (found == count) break;
            result[found++] = i;
        }
        check(found == count, "Method is shorter than its expected prefix");
        return result;
    }

    private static void assertAndReplaceValues(InsnList instructions, List<AbstractInsnNode> nodes, int[] before,
            int[] after, String context) {
        check(nodes.size() == before.length, "Wrong count for " + context);
        int[] values = new int[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) values[i] = integer(nodes.get(i));
        check(Arrays.equals(values, before), "Unexpected speed-code sequence in " + context + ": "
                + Arrays.toString(values));
        for (int i = 0; i < nodes.size(); i++) {
            instructions.set(nodes.get(i), push(after[i]));
        }
    }

    private static void verifyPinnedMaps(Properties timing, Properties tempo) {
        check("650".equals(required(timing, "auto.delay.after.ms")),
                "Timing symbol map is not the 650 ms profile");
        check("650".equals(required(timing, "request.minimum.ms")),
                "Timing symbol map does not identify the 650 ms request minimum");
        check("1,2,3,-8,-9".equals(required(tempo, "candidate.speed.codes")),
                "Tempo symbol map is not the known fractional profile");
        check("1,2,3,0.8,0.9".equals(required(tempo, "candidate.speed.labels")),
                "Tempo symbol map labels differ from the known source menu");
        check("(J)J".equals(required(tempo, "candidate.scale.descriptor")),
                "Elapsed scaler descriptor changed");
        check("I".equals(required(tempo, "candidate.speed.desc")),
                "Speed field descriptor changed");
    }

    private static void copyCandidateProperties(Properties source, Properties target) {
        for (String name : source.stringPropertyNames())
            if (name.startsWith("candidate.")) target.setProperty(name, source.getProperty(name));
    }

    private static MethodSignature parseMethod(String value) {
        int descriptor = value.indexOf('(');
        check(descriptor > 0, "Malformed method symbol " + value);
        return new MethodSignature(value.substring(0, descriptor), value.substring(descriptor));
    }

    private static FieldNode declaredField(ClassNode c, String name, String descriptor) {
        FieldNode result = null;
        for (FieldNode f : c.fields) if (f.name.equals(name) && f.desc.equals(descriptor)) {
            check(result == null, "Ambiguous field " + c.name + "." + name + ":" + descriptor);
            result = f;
        }
        check(result != null, "Missing exact field " + c.name + "." + name + ":" + descriptor);
        return result;
    }

    private static void declaredField(ClassNode c, String name, String descriptor, boolean isStatic) {
        FieldNode field = declaredField(c, name, descriptor);
        check(((field.access & ACC_STATIC) != 0) == isStatic,
                "Unexpected static/instance field shape " + c.name + "." + name + ":" + descriptor);
    }

    private static void writeJar(File target, LinkedHashMap<String, byte[]> entries,
            Map<String, Long> times) throws Exception {
        JarOutputStream out = new JarOutputStream(new FileOutputStream(target));
        try {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                JarEntry zip = new JarEntry(e.getKey());
                Long time = times.get(e.getKey());
                if (time != null && time.longValue() >= 0L) zip.setTime(time.longValue());
                out.putNextEntry(zip);
                out.write(e.getValue());
                out.closeEntry();
            }
        } finally { out.close(); }
    }

    private static Properties load(File file) throws Exception {
        Properties p = new Properties();
        InputStream in = new FileInputStream(file);
        try { p.load(in); } finally { in.close(); }
        return p;
    }

    private static void store(Properties p, File file, String comment) throws Exception {
        OutputStream out = new FileOutputStream(file);
        try { p.store(out, comment); } finally { out.close(); }
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        check(value != null && value.trim().length() != 0, "Missing symbol " + key);
        return value.trim();
    }

    private static ClassNode cls(byte[] bytes) {
        check(bytes != null, "Missing class bytes");
        return HungBySonNativeTimingPatcher.cls(bytes);
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return HungBySonNativeTimingPatcher.method(owner, name, descriptor);
    }

    private static AbstractInsnNode next(AbstractInsnNode instruction) {
        return HungBySonNativeTimingPatcher.next(instruction);
    }

    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        return HungBySonNativeTimingPatcher.previous(instruction);
    }

    private static boolean speed(AbstractInsnNode instruction, String owner, String field, int opcode) {
        return instruction instanceof FieldInsnNode && instruction.getOpcode() == opcode
                && ((FieldInsnNode)instruction).owner.equals(owner)
                && ((FieldInsnNode)instruction).name.equals(field)
                && ((FieldInsnNode)instruction).desc.equals("I");
    }

    private static boolean field(AbstractInsnNode instruction, int opcode, String owner, String name,
            String descriptor) {
        return instruction instanceof FieldInsnNode && instruction.getOpcode() == opcode
                && ((FieldInsnNode)instruction).owner.equals(owner)
                && ((FieldInsnNode)instruction).name.equals(name)
                && ((FieldInsnNode)instruction).desc.equals(descriptor);
    }

    private static boolean call(AbstractInsnNode instruction, String owner, String name,
            String descriptor) {
        return instruction instanceof MethodInsnNode && instruction.getOpcode() == INVOKESTATIC
                && ((MethodInsnNode)instruction).owner.equals(owner)
                && ((MethodInsnNode)instruction).name.equals(name)
                && ((MethodInsnNode)instruction).desc.equals(descriptor);
    }

    private static int integer(AbstractInsnNode instruction) {
        if (instruction == null) throw new IllegalStateException("Expected integer instruction");
        int opcode = instruction.getOpcode();
        if (opcode >= ICONST_M1 && opcode <= ICONST_5) return opcode - ICONST_0;
        if (instruction instanceof IntInsnNode) return ((IntInsnNode)instruction).operand;
        if (instruction instanceof LdcInsnNode && ((LdcInsnNode)instruction).cst instanceof Integer)
            return ((Integer)((LdcInsnNode)instruction).cst).intValue();
        throw new IllegalStateException("Expected integer instruction, got opcode " + opcode);
    }

    private static AbstractInsnNode push(int value) {
        if (value >= -1 && value <= 5) return new InsnNode(ICONST_0 + value);
        if (value >= -128 && value <= 127) return new IntInsnNode(BIPUSH, value);
        return new IntInsnNode(SIPUSH, value);
    }

    private static String join(int[] values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) { if (i != 0) out.append(','); out.append(values[i]); }
        return out.toString();
    }

    private static String join(String[] values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) { if (i != 0) out.append(','); out.append(values[i]); }
        return out.toString();
    }

    private static String normalizeSha(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        check(normalized.matches("[0-9A-F]{64}"), "Expected input SHA-256 must be 64 hexadecimal characters");
        return normalized;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class MethodSignature {
        final String name, descriptor;
        MethodSignature(String name, String descriptor) { this.name = name; this.descriptor = descriptor; }
    }

}
