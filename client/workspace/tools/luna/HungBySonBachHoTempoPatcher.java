import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Historical 1..5 staging step. Use build-hung-bachho-parity.ps1, which repairs byte-sized button actions before release. */
public final class HungBySonBachHoTempoPatcher implements Opcodes {
    private static final String EXPECTED_INPUT = "EA401D771635723AD2EE2BF3EB5FE9A7D5F490559DE6F97983BD1F107A5659AC";
    private static final String EXPECTED_REFERENCE = "2BE9C4DB203EBAD94DB20B21D1F3DD0F4B611C90756D738DF44272BF2C2CF840";
    private static final String CANVAS = "com/silverknight/a";
    private static final String SPEED = "codexGameSpeed";
    private static final String ACCUMULATOR = "codexTempoAccumulator";
    private static final String LAST_TICK = "codexTempoLastTick";
    private static final int FIRST_CHILD_ACTION = -3101;
    private static final int ROOT_SPEED_ACTION = -31;
    private static final int BACK_ACTION = -34;

    private static final class EntryData {
        final JarEntry source;
        byte[] bytes;
        EntryData(JarEntry source, byte[] bytes) { this.source = source; this.bytes = bytes; }
    }

    private static final class ReferenceMap {
        String engine, speedField, rootBuilder, optionBuilder, actionMethod;
        String canvas, tickMethod, tickOwner, tickDesc, intervalOwner, intervalField;
        String entity, movementMethod, accumulatorField, lastFrameField;
    }

    private static final class CandidateMap {
        String fpsOwner, fpsField, intervalOwner, intervalField, menuOwner, menuAction;
        String refreshOwner, refreshMethod, callbackOwner, callbackField, callbackDesc;
        String displayOwner, displayMethod, menuManagerOwner, menuManagerField, menuManagerDesc;
        String menuAddMethod, menuItemCtor;
        String actor, player;
        String updateOwner, updateMethod, updateDesc, timeOwner, timeMethod, clockOwner, clockField;
        MethodNode menuBuilder;
    }

    private HungBySonBachHoTempoPatcher() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException(
                "input.jar bachho_reference.jar output.jar report.txt catalog.properties");
        File input = new File(args[0]).getCanonicalFile();
        File reference = new File(args[1]).getCanonicalFile();
        File output = new File(args[2]).getCanonicalFile();
        File report = new File(args[3]).getCanonicalFile();
        File catalog = new File(args[4]).getCanonicalFile();
        require(input.isFile(), "Hung input missing");
        require(reference.isFile(), "Bạch Hổ reference missing");
        require(!input.equals(output) && !output.exists(), "Output must be a new path");
        String inputHash = sha256(input);
        String referenceHash = sha256(reference);
        require(EXPECTED_INPUT.equals(inputHash), "Unexpected Hung_By_Son input SHA-256: " + inputHash);
        require(EXPECTED_REFERENCE.equals(referenceHash), "Unexpected Bạch Hổ reference SHA-256: " + referenceHash);

        List<EntryData> inputEntries = read(input);
        List<EntryData> referenceEntries = read(reference);
        Map<String, EntryData> inputByName = index(inputEntries);
        Map<String, EntryData> referenceByName = index(referenceEntries);
        ReferenceMap ref = discoverReference(referenceEntries);
        CandidateMap target = discoverCandidate(inputEntries);

        patchCandidate(inputByName, ref, target);
        List<EntryData> outputEntries = new ArrayList<EntryData>(inputEntries);
        for (int i = 0; i < outputEntries.size(); i++) {
            EntryData e = outputEntries.get(i);
            byte[] changed = inputByName.get(e.source.getName()).bytes;
            if (changed != e.bytes) outputEntries.set(i, new EntryData(e.source, changed));
        }
        ensureChangedClasses(inputEntries, outputEntries);
        write(output, outputEntries);

        String outHash = sha256(output);
        String changedClasses = changedClasses(inputEntries, outputEntries);
        String reportText = makeReport(inputHash, referenceHash, outHash, ref, target, changedClasses);
        writeText(report, reportText);
        Properties properties = makeCatalog(inputHash, referenceHash, outHash, ref, target, changedClasses);
        File parent = catalog.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create catalog directory");
        FileOutputStream propsOut = new FileOutputStream(catalog);
        try { properties.store(propsOut, "Hung_By_Son / Bạch Hổ tempo parity map"); }
        finally { propsOut.close(); }
        System.out.println(reportText);
    }

    private static void patchCandidate(Map<String, EntryData> entries, ReferenceMap ref, CandidateMap target) {
        ClassNode canvas = parse(entries.get(CANVAS + ".class").bytes);
        ClassNode actor = parse(entries.get(target.actor + ".class").bytes);
        ClassNode settings = parse(entries.get(target.fpsOwner + ".class").bytes);
        ClassNode menu = parse(entries.get(target.menuOwner + ".class").bytes);

        patchTempoDefault(settings, target);
        patchTempoMenu(menu, target);
        patchTempoLoop(canvas, actor, target);
        patchMovementGate(actor, target.player, target.actor);

        entries.put(CANVAS + ".class", replace(entries.get(CANVAS + ".class"), writeClass(canvas)));
        entries.put(target.actor + ".class", replace(entries.get(target.actor + ".class"), writeClass(actor)));
        entries.put(target.fpsOwner + ".class", replace(entries.get(target.fpsOwner + ".class"), writeClass(settings)));
        entries.put(target.menuOwner + ".class", replace(entries.get(target.menuOwner + ".class"), writeClass(menu)));
    }

    private static ReferenceMap discoverReference(List<EntryData> entries) {
        ReferenceMap out = new ReferenceMap();
        for (EntryData entry : entries) {
            if (!entry.source.getName().endsWith(".class")) continue;
            ClassNode c = parse(entry.bytes);
            for (MethodNode m : c.methods) {
                if (!"()V".equals(m.desc) || !hasStringContaining(m, "Game: [x")) continue;
                FieldInsnNode speed = findIntFieldAppendedByStringBuilder(m, "Game: [x");
                if (speed == null) continue;
                if (out.rootBuilder != null) throw new IllegalStateException("Multiple Bạch Hổ tempo root builders");
                out.engine = c.name; out.rootBuilder = m.name; out.speedField = speed.name;
            }
        }
        require(out.engine != null, "Bạch Hổ tempo root row missing");
        int options = 0, actions = 0;
        for (EntryData entry : entries) {
            if (!entry.source.getName().endsWith(".class")) continue;
            ClassNode c = parse(entry.bytes);
            for (MethodNode m : c.methods) {
                if (c.name.equals(out.engine) && "()V".equals(m.desc)
                        && containsTempoOptions(m) && countField(m, GETSTATIC, out.engine, out.speedField, "I") >= 5) {
                    options++; out.optionBuilder = m.name;
                }
                if (c.name.equals(out.engine) && "(II)V".equals(m.desc)
                        && hasIntRange(m, 81, 85)
                        && countField(m, PUTSTATIC, out.engine, out.speedField, "I") == 1) {
                    actions++; out.actionMethod = m.name;
                }
                if ("run".equals(m.name) && "()V".equals(m.desc) && c.superName != null
                        && c.superName.equals("javax/microedition/lcdui/Canvas")) {
                    MethodNode run = method(c, "run", "()V");
                    if (run != null && countField(run, GETSTATIC, out.engine, out.speedField, "I") == 2) {
                        String candidateTickOwner = findTempoTickOwner(run, out.engine);
                        if (candidateTickOwner == null) continue;
                        if (out.canvas != null) throw new IllegalStateException("Multiple Bạch Hổ tempo canvases: " + out.canvas + " and " + c.name + " tick=" + candidateTickOwner);
                        out.canvas = c.name;
                        out.tickOwner = candidateTickOwner;
                        out.tickMethod = findTempoTickMethod(run, out.tickOwner);
                        out.tickDesc = "()V";
                    }
                }
            }
        }
        require(options == 1 && actions == 1, "Bạch Hổ tempo option/action topology ambiguous");
        require(out.canvas != null && out.tickOwner != null && out.tickMethod != null,
                "Bạch Hổ simulation run/tick call missing");
        ClassNode canvasClass = parse(find(entries, out.canvas + ".class").bytes);
        MethodNode clinit = method(canvasClass, "<clinit>", "()V");
        FieldInsnNode interval = findInitializedLong(canvasClass, clinit, 16L);
        require(interval != null, "Bạch Hổ fixed 16ms interval field missing");
        out.intervalOwner = interval.owner; out.intervalField = interval.name;
        MethodNode referenceRun = method(parse(find(entries, out.canvas + ".class").bytes), "run", "()V");
        out.accumulatorField = findLongFieldWithMostWrites(referenceRun, false);
        out.lastFrameField = findFirstLongWrite(referenceRun);
        for (EntryData entry : entries) {
            if (!entry.source.getName().endsWith(".class")) continue;
            ClassNode c = parse(entry.bytes);
            if (field(c, "hsoMoveTick", "I") != null) {
                MethodNode movement = null;
                for (MethodNode m : c.methods) if (hasInt(m, 109) && hasField(m, GETSTATIC, c.name, "hsoMoveTick", "I")) movement = m;
                if (movement != null) { out.entity = c.name; out.movementMethod = movement.name + movement.desc; }
            }
        }
        require(out.entity != null, "Bạch Hổ hsoMoveTick movement gate missing");
        return out;
    }

    private static CandidateMap discoverCandidate(List<EntryData> entries) {
        CandidateMap out = new CandidateMap();
        Map<String, ClassNode> classes = new HashMap<String, ClassNode>();
        for (EntryData e : entries) if (e.source.getName().endsWith(".class")) classes.put(parse(e.bytes).name, parse(e.bytes));
        ClassNode canvas = classes.get(CANVAS);
        require(canvas != null, "Hung canvas com/silverknight/a missing");
        MethodNode run = method(canvas, "run", "()V");
        require(run != null && hasMethod(run, "codexNextFrameTarget", "()J"), "Expected patched Hung canvas loop missing");
        out.updateOwner = null;
        for (int i = 0; i < run.instructions.size(); i++) {
            AbstractInsnNode n = run.instructions.get(i);
            if (!(n instanceof MethodInsnNode) || n.getOpcode() != INVOKEVIRTUAL) continue;
            MethodInsnNode call = (MethodInsnNode)n;
            if (!"()V".equals(call.desc) || call.owner.equals("javax/microedition/lcdui/Canvas")) continue;
            AbstractInsnNode before = previousReal(n);
            if (before instanceof FieldInsnNode && before.getOpcode() == GETSTATIC
                    && ((FieldInsnNode)before).desc.startsWith("L")) {
                out.updateOwner = call.owner; out.updateMethod = call.name; out.updateDesc = call.desc;
                break;
            }
        }
        require(out.updateOwner != null, "Hung game-update invocation missing from run()V");
        out.timeOwner = findTimeOwner(run); out.timeMethod = findTimeMethod(run, out.timeOwner);
        require(out.timeOwner != null && out.timeMethod != null, "Hung elapsed clock method missing");
        FieldInsnNode clockWrite = null;
        for (AbstractInsnNode n = run.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTSTATIC || !"J".equals(((FieldInsnNode)n).desc)) continue;
            if (!out.updateOwner.equals(((FieldInsnNode)n).owner)) continue;
            AbstractInsnNode prior = previousReal(n);
            if (prior instanceof MethodInsnNode && prior.getOpcode() == INVOKESTATIC
                    && out.timeOwner.equals(((MethodInsnNode)prior).owner)
                    && out.timeMethod.equals(((MethodInsnNode)prior).name)
                    && "()J".equals(((MethodInsnNode)prior).desc)) {
                require(clockWrite == null, "Multiple Hung cached clock writers");
                clockWrite = (FieldInsnNode)n;
            }
        }
        require(clockWrite != null, "Hung cached clock field missing");
        out.clockOwner = clockWrite.owner; out.clockField = clockWrite.name;

        String fpsSuffix = "ililliliiiii";
        for (ClassNode c : classes.values()) for (FieldNode f : c.fields)
            if ("I".equals(f.desc) && f.name.endsWith(fpsSuffix)) {
                require(out.fpsOwner == null, "Multiple Hung FPS setting fields");
                out.fpsOwner = c.name; out.fpsField = f.name;
            }
        require(out.fpsOwner != null, "Hung FPS setting field missing");
        for (ClassNode c : classes.values()) for (MethodNode m : c.methods) {
            if (!"(II)Z".equals(m.desc)) continue;
            if (hasField(m, PUTSTATIC, out.fpsOwner, out.fpsField, "I") && hasInt(m, ROOT_SPEED_ACTION)) {
                require(out.menuAction == null, "Multiple Hung FPS menu handlers");
                out.menuOwner = c.name; out.menuAction = m.name;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n instanceof FieldInsnNode && n.getOpcode() == PUTSTATIC
                            && "J".equals(((FieldInsnNode)n).desc)) {
                        FieldInsnNode f = (FieldInsnNode)n;
                        if (f.owner.equals(out.fpsOwner)) { out.intervalOwner = f.owner; out.intervalField = f.name; }
                    }
                }
            }
        }
        require(out.menuOwner != null, "Hung FPS menu handler missing");
        ClassNode menu = classes.get(out.menuOwner);
        for (MethodNode m : menu.methods) {
            if ("()V".equals(m.desc) && hasField(m, GETSTATIC, out.fpsOwner, out.fpsField, "I")
                    && hasInt(m, ROOT_SPEED_ACTION)) {
                require(out.menuBuilder == null, "Multiple Hung FPS menu builders");
                out.menuBuilder = m;
            }
        }
        require(out.menuBuilder != null,
                "Hung FPS menu builder missing");
        findMenuUiSymbols(out, menu, out.menuBuilder, method(menu, out.menuAction, "(II)Z"));
        out.player = findPlayer(classes.values());
        ClassNode p = classes.get(out.player);
        require(p != null && p.superName != null, "Hung player superclass missing");
        out.actor = p.superName;
        ClassNode actor = classes.get(out.actor);
        require(actor != null && method(actor, "a", "()V") != null, "Hung actor movement method missing");
        return out;
    }

    private static void findMenuUiSymbols(CandidateMap map, ClassNode menu, MethodNode builder, MethodNode action) {
        require(action != null, "Hung FPS action body missing");
        FieldInsnNode rootSpeedItem = null;
        for (AbstractInsnNode n = builder.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof IntInsnNode) || ((IntInsnNode)n).operand != ROOT_SPEED_ACTION) continue;
            AbstractInsnNode next = nextReal(n);
            if (!(next instanceof FieldInsnNode) || next.getOpcode() != GETSTATIC) continue;
            rootSpeedItem = (FieldInsnNode)next; break;
        }
        require(rootSpeedItem != null, "Hung FPS root item callback field missing");
        map.callbackOwner = rootSpeedItem.owner; map.callbackField = rootSpeedItem.name; map.callbackDesc = rootSpeedItem.desc;

        MethodInsnNode display = null;
        for (AbstractInsnNode n = builder.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode && "(Let;ILjava/lang/String;ZLet;)V".equals(((MethodInsnNode)n).desc)) {
                display = (MethodInsnNode)n; break;
            }
        }
        require(display != null, "Hung native menu display call missing");
        map.displayOwner = display.owner; map.displayMethod = display.name;
        for (AbstractInsnNode n = builder.instructions.getFirst(); n != null && n != display; n = n.getNext()) {
            if (!(n instanceof FieldInsnNode) || n.getOpcode() != GETSTATIC) continue;
            String desc = ((FieldInsnNode)n).desc;
            if (!desc.startsWith("L") || !desc.endsWith(";")) continue;
            if (n instanceof FieldInsnNode && n.getOpcode() == GETSTATIC
                    && display.owner.equals(desc.substring(1, desc.length()-1))) {
                map.menuManagerOwner = ((FieldInsnNode)n).owner;
                map.menuManagerField = ((FieldInsnNode)n).name;
                map.menuManagerDesc = ((FieldInsnNode)n).desc;
            }
        }
        require(map.menuManagerOwner != null, "Hung menu manager field missing");
        for (AbstractInsnNode n = builder.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode) {
                MethodInsnNode m = (MethodInsnNode)n;
                if ("et".equals(m.owner) && "(Ljava/lang/Object;)V".equals(m.desc)) map.menuAddMethod = m.name;
                if ("bt".equals(m.owner) && m.getOpcode() == INVOKESPECIAL
                        && m.desc.startsWith("(Ljava/lang/String;I") && m.desc.endsWith(")V")) map.menuItemCtor = m.desc;
            }
        }
        require(map.menuAddMethod != null && map.menuItemCtor != null, "Hung native menu item/list API missing");
        AbstractInsnNode refresh = null;
        for (AbstractInsnNode n = action.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode && n.getOpcode() == INVOKESTATIC
                    && menu.name.equals(((MethodInsnNode)n).owner) && "()V".equals(((MethodInsnNode)n).desc)) {
                if (refresh != null && !((MethodInsnNode)refresh).name.equals(((MethodInsnNode)n).name))
                    throw new IllegalStateException("Multiple candidate menu refresh helpers");
                refresh = n;
            }
        }
        require(refresh instanceof MethodInsnNode, "Hung menu refresh helper not found after back action");
        map.refreshOwner = ((MethodInsnNode)refresh).owner;
        map.refreshMethod = ((MethodInsnNode)refresh).name;
    }

    private static void patchTempoDefault(ClassNode settings, CandidateMap map) {
        MethodNode clinit = method(settings, "<clinit>", "()V");
        require(clinit != null, "Hung settings <clinit> missing");
        int writes = 0;
        for (AbstractInsnNode n = clinit.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!fieldIs(n, PUTSTATIC, settings.name, map.fpsField, "I")) continue;
            AbstractInsnNode value = previousReal(n);
            require(isInt(value, 30), "Hung FPS default is not 30");
            clinit.instructions.set(value, new InsnNode(ICONST_1));
            writes++;
        }
        require(writes == 1, "Expected one Hung tempo default writer");
        if (settings.name.equals(map.intervalOwner) && map.intervalField != null) {
            for (AbstractInsnNode n = clinit.instructions.getFirst(); n != null;) {
                AbstractInsnNode following = n.getNext();
                if (fieldIs(n, PUTSTATIC, map.intervalOwner, map.intervalField, "J")) {
                    AbstractInsnNode value = previousReal(n);
                    require(value instanceof LdcInsnNode && ((LdcInsnNode)value).cst instanceof Long,
                            "Unexpected FPS interval initializer");
                    clinit.instructions.remove(value);
                    clinit.instructions.remove(n);
                }
                n = following;
            }
        }
    }

    private static void patchTempoMenu(ClassNode menu, CandidateMap map) {
        MethodNode builder = method(menu, map.menuBuilder.name, map.menuBuilder.desc);
        require(builder != null, "Hung menu builder missing from output class");
        MethodNode action = method(menu, map.menuAction, "(II)Z");
        patchRootTempoLabel(builder, map);
        removeOldFpsAction(action);
        addTempoMenuHelper(menu, map);
        addTempoActions(action, menu, map);
    }

    private static void patchRootTempoLabel(MethodNode builder, CandidateMap map) {
        FieldInsnNode speedRead = null;
        for (AbstractInsnNode n = builder.instructions.getFirst(); n != null; n = n.getNext()) {
            if (fieldIs(n, GETSTATIC, map.fpsOwner, map.fpsField, "I")) { speedRead = (FieldInsnNode)n; break; }
        }
        require(speedRead != null, "Hung FPS root label speed read missing");
        MethodInsnNode decoder = null;
        for (AbstractInsnNode n = speedRead.getPrevious(); n != null; n = n.getPrevious()) {
            if (n instanceof MethodInsnNode && "([B)Ljava/lang/String;".equals(((MethodInsnNode)n).desc)) {
                decoder = (MethodInsnNode)n; break;
            }
        }
        require(decoder != null, "Hung encoded FPS row prefix missing");
        // Replace the decoded legacy prefix while preserving the existing StringBuffer on the stack.
        InsnList replacement = new InsnList();
        replacement.add(new InsnNode(POP));
        replacement.add(new LdcInsnNode("Tốc độ Game: [x"));
        builder.instructions.insertBefore(decoder, replacement);
        builder.instructions.remove(decoder);

        AbstractInsnNode appendInt = null;
        for (AbstractInsnNode n = speedRead.getNext(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode && "(I)Ljava/lang/StringBuilder;".equals(((MethodInsnNode)n).desc)) {
                appendInt = n; break;
            }
        }
        require(appendInt != null, "FPS row integer append missing");
        InsnList close = new InsnList();
        close.add(new LdcInsnNode("]"));
        close.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        builder.instructions.insert(appendInt, close);
    }

    private static void removeOldFpsAction(MethodNode action) {
        AbstractInsnNode compare = null;
        JumpInsnNode branch = null;
        for (AbstractInsnNode n = action.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!isInt(n, ROOT_SPEED_ACTION)) continue;
            AbstractInsnNode j = nextReal(n);
            if (j instanceof JumpInsnNode && j.getOpcode() == IF_ICMPNE) { compare = n; branch = (JumpInsnNode)j; break; }
        }
        require(compare != null && branch != null, "Original FPS click branch missing");
        LabelNode end = branch.label;
        AbstractInsnNode first = previousReal(compare);
        require(first != null && first.getOpcode() == ILOAD, "FPS branch shape changed");
        int incoming = 0;
        for (AbstractInsnNode n = action.instructions.getFirst(); n != null; n = n.getNext())
            if (n instanceof JumpInsnNode && ((JumpInsnNode)n).label == end) incoming++;
        require(incoming == 1, "FPS branch target has external incoming control flow");
        AbstractInsnNode n = first;
        while (n != null && n != end) {
            AbstractInsnNode following = n.getNext();
            if (n.getOpcode() >= 0) action.instructions.remove(n);
            n = following;
        }
        for (int i = action.tryCatchBlocks.size() - 1; i >= 0; i--) {
            org.objectweb.asm.tree.TryCatchBlockNode range = action.tryCatchBlocks.get(i);
            boolean executable = false;
            for (AbstractInsnNode instruction = range.start; instruction != null && instruction != range.end;
                    instruction = instruction.getNext()) if (instruction.getOpcode() >= 0) executable = true;
            if (!executable) action.tryCatchBlocks.remove(i);
        }
    }

    private static void addTempoMenuHelper(ClassNode menu, CandidateMap map) {
        require(method(menu, "codexBuildTempoMenu", "()V") == null, "Tempo helper already exists");
        MethodNode helper = new MethodNode(ASM9, ACC_PRIVATE | ACC_STATIC, "codexBuildTempoMenu", "()V", null, null);
        helper.instructions.add(new TypeInsnNode(NEW, "et"));
        helper.instructions.add(new InsnNode(DUP));
        helper.instructions.add(new LdcInsnNode("CHỌN TỐC ĐỘ"));
        helper.instructions.add(new MethodInsnNode(INVOKESPECIAL, "et", "<init>", "(Ljava/lang/String;)V", false));
        helper.instructions.add(new VarInsnNode(ASTORE, 0));
        for (int speed = 1; speed <= 5; speed++) addTempoOption(helper, map, speed);
        helper.instructions.add(new FieldInsnNode(GETSTATIC, map.menuManagerOwner, map.menuManagerField, map.menuManagerDesc));
        helper.instructions.add(new VarInsnNode(ALOAD, 0));
        helper.instructions.add(new InsnNode(ICONST_2));
        helper.instructions.add(new LdcInsnNode("CHỌN TỐC ĐỘ"));
        helper.instructions.add(new InsnNode(ICONST_0));
        helper.instructions.add(new InsnNode(ACONST_NULL));
        helper.instructions.add(new TypeInsnNode(CHECKCAST, "et"));
        helper.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, map.displayOwner, map.displayMethod,
                "(Let;ILjava/lang/String;ZLet;)V", false));
        helper.instructions.add(new InsnNode(RETURN));
        menu.methods.add(helper);
    }

    private static void addTempoOption(MethodNode helper, CandidateMap map, int speed) {
        LabelNode notSelected = new LabelNode();
        LabelNode done = new LabelNode();
        helper.instructions.add(new VarInsnNode(ALOAD, 0));
        helper.instructions.add(new TypeInsnNode(NEW, "bt"));
        helper.instructions.add(new InsnNode(DUP));
        helper.instructions.add(new TypeInsnNode(NEW, "java/lang/StringBuffer"));
        helper.instructions.add(new InsnNode(DUP));
        helper.instructions.add(new LdcInsnNode("Tốc độ x" + speed));
        helper.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/StringBuffer", "<init>", "(Ljava/lang/String;)V", false));
        helper.instructions.add(new FieldInsnNode(GETSTATIC, map.fpsOwner, map.fpsField, "I"));
        helper.instructions.add(pushInt(speed));
        helper.instructions.add(new JumpInsnNode(IF_ICMPNE, notSelected));
        helper.instructions.add(new LdcInsnNode(" [x]"));
        helper.instructions.add(new JumpInsnNode(GOTO, done));
        helper.instructions.add(notSelected);
        helper.instructions.add(new LdcInsnNode(""));
        helper.instructions.add(done);
        helper.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuffer", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuffer;", false));
        helper.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuffer", "toString", "()Ljava/lang/String;", false));
        helper.instructions.add(pushInt(FIRST_CHILD_ACTION - (speed - 1)));
        helper.instructions.add(new FieldInsnNode(GETSTATIC, map.callbackOwner, map.callbackField, map.callbackDesc));
        helper.instructions.add(new MethodInsnNode(INVOKESPECIAL, "bt", "<init>", map.menuItemCtor, false));
        helper.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "et", map.menuAddMethod, "(Ljava/lang/Object;)V", false));
    }

    private static void addTempoActions(MethodNode action, ClassNode owner, CandidateMap map) {
        InsnList prefix = new InsnList();
        LabelNode next = new LabelNode();
        prefix.add(new VarInsnNode(ILOAD, 0));
        prefix.add(pushInt(ROOT_SPEED_ACTION));
        prefix.add(new JumpInsnNode(IF_ICMPNE, next));
        prefix.add(new MethodInsnNode(INVOKESTATIC, owner.name, "codexBuildTempoMenu", "()V", false));
        prefix.add(new InsnNode(ICONST_1));
        prefix.add(new InsnNode(IRETURN));
        prefix.add(next);
        for (int speed = 1; speed <= 5; speed++) {
            LabelNode following = new LabelNode();
            prefix.add(new VarInsnNode(ILOAD, 0));
            prefix.add(pushInt(FIRST_CHILD_ACTION - (speed - 1)));
            prefix.add(new JumpInsnNode(IF_ICMPNE, following));
            prefix.add(pushInt(speed));
            prefix.add(new FieldInsnNode(PUTSTATIC, map.fpsOwner, map.fpsField, "I"));
            prefix.add(new MethodInsnNode(INVOKESTATIC, map.refreshOwner, map.refreshMethod, "()V", false));
            prefix.add(new InsnNode(ICONST_1));
            prefix.add(new InsnNode(IRETURN));
            prefix.add(following);
        }
        action.instructions.insert(prefix);
    }

    private static void patchTempoLoop(ClassNode canvas, ClassNode actor, CandidateMap map) {
        MethodNode run = method(canvas, "run", "()V");
        require(run != null, "Hung run()V missing");
        MethodNode frameHelper = method(canvas, "codexNextFrameTarget", "()J");
        require(frameHelper != null, "Hung FPS helper missing");
        FieldNode target = field(canvas, "codexFrameTarget", "J");
        FieldNode carry = field(canvas, "codexFrameRemainder", "I");
        FieldNode moveCarry = field(canvas, "codexMovementRemainder", "I");
        FieldNode moveFlag = field(canvas, "codexMoveThisFrame", "Z");
        require(target != null && carry != null && moveCarry != null && moveFlag != null,
                "Hung FPS/movement throttle fields missing");

        AbstractInsnNode targetCall = null, targetPut = null;
        for (AbstractInsnNode n = run.instructions.getFirst(); n != null;) {
            AbstractInsnNode following = n.getNext();
            if (n instanceof MethodInsnNode && n.getOpcode() == INVOKESTATIC
                    && canvas.name.equals(((MethodInsnNode)n).owner)
                    && "codexNextFrameTarget".equals(((MethodInsnNode)n).name)
                    && "()J".equals(((MethodInsnNode)n).desc)) {
                AbstractInsnNode put = nextReal(n);
                require(fieldIs(put, PUTSTATIC, canvas.name, "codexFrameTarget", "J"), "Frame target writer shape changed");
                require(targetCall == null, "Multiple frame-target helpers in run()V");
                targetCall = n; targetPut = put;
            }
            n = following;
        }
        require(targetCall != null && targetPut != null, "Hung FPS helper invocation missing");
        run.instructions.remove(targetPut); run.instructions.remove(targetCall);
        int fixedReads = 0;
        for (AbstractInsnNode n = run.instructions.getFirst(); n != null;) {
            AbstractInsnNode next = n.getNext();
            if (fieldIs(n, GETSTATIC, canvas.name, "codexFrameTarget", "J")) {
                run.instructions.set(n, new LdcInsnNode(Long.valueOf(16L))); fixedReads++;
            }
            n = next;
        }
        require(fixedReads == 2, "Hung frame target read count changed: " + fixedReads);

        MethodInsnNode updateCall = null;
        FieldInsnNode updateReceiver = null;
        for (AbstractInsnNode n = run.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode) || n.getOpcode() != INVOKEVIRTUAL) continue;
            MethodInsnNode m = (MethodInsnNode)n;
            if (m.owner.equals(map.updateOwner) && m.name.equals(map.updateMethod) && m.desc.equals(map.updateDesc)) {
                AbstractInsnNode prior = previousReal(n);
                require(prior instanceof FieldInsnNode && prior.getOpcode() == GETSTATIC,
                        "Hung update call receiver missing");
                updateCall = m; updateReceiver = (FieldInsnNode)prior; break;
            }
        }
        require(updateCall != null, "Hung update call disappeared");

        canvas.fields.add(new FieldNode(ACC_PRIVATE, ACCUMULATOR, "J", null, null));
        canvas.fields.add(new FieldNode(ACC_PRIVATE, LAST_TICK, "J", null, null));
        ensureField(actor, ACC_PUBLIC | ACC_STATIC, "hsoMoveTick", "I");

        removeOuterClockWrite(run, map);
        InsnList timing = makeTempoLoop(updateReceiver, updateCall, actor.name, map.fpsOwner, map.fpsField,
                map.clockOwner, map.clockField);
        run.instructions.insertBefore(updateReceiver, timing);
        run.instructions.remove(updateReceiver);
        run.instructions.remove(updateCall);
        initializeTempoLastTick(run, map);
        insertPaintTimer(run, map);
        removeHungDebugDelay(run, map);
        removeField(canvas, target); removeField(canvas, carry); removeField(canvas, moveCarry); removeField(canvas, moveFlag);
        canvas.methods.remove(frameHelper);
    }

    private static InsnList makeTempoLoop(FieldInsnNode receiver, MethodInsnNode update, String actor,
            String speedOwner, String speedField, String clockOwner, String clockField) {
        InsnList code = new InsnList();
        LabelNode noScale = new LabelNode();
        LabelNode updatesDone = new LabelNode(), noReset = new LabelNode();
        LabelNode updateLoop = new LabelNode();
        // local 3 is the current simulation timestamp captured at the top of the pass.
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETFIELD, CANVAS, LAST_TICK, "J"));
        code.add(new VarInsnNode(LSTORE, 5));
        code.add(new VarInsnNode(LLOAD, 3));
        code.add(new VarInsnNode(LLOAD, 5));
        code.add(new InsnNode(LSUB));
        code.add(new VarInsnNode(LSTORE, 7));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new VarInsnNode(LLOAD, 3));
        code.add(new FieldInsnNode(PUTFIELD, CANVAS, LAST_TICK, "J"));
        code.add(new FieldInsnNode(GETSTATIC, speedOwner, speedField, "I"));
        code.add(new InsnNode(ICONST_1));
        code.add(new JumpInsnNode(IF_ICMPLE, noScale));
        code.add(new VarInsnNode(LLOAD, 7));
        code.add(new FieldInsnNode(GETSTATIC, speedOwner, speedField, "I"));
        code.add(new InsnNode(I2L));
        code.add(new InsnNode(LMUL));
        code.add(new VarInsnNode(LSTORE, 7));
        code.add(noScale);
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new InsnNode(DUP));
        code.add(new FieldInsnNode(GETFIELD, CANVAS, ACCUMULATOR, "J"));
        code.add(new VarInsnNode(LLOAD, 7));
        code.add(new InsnNode(LADD));
        code.add(new VarInsnNode(LLOAD, 7));
        code.add(new InsnNode(LADD));
        code.add(new FieldInsnNode(PUTFIELD, CANVAS, ACCUMULATOR, "J"));
        code.add(new InsnNode(ICONST_0));
        code.add(new VarInsnNode(ISTORE, 9));
        code.add(updateLoop);
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETFIELD, CANVAS, ACCUMULATOR, "J"));
        code.add(new LdcInsnNode(Long.valueOf(40L)));
        code.add(new InsnNode(LCMP));
        code.add(new JumpInsnNode(IFLT, updatesDone));
        code.add(new VarInsnNode(ILOAD, 9));
        code.add(new InsnNode(ICONST_3));
        code.add(new JumpInsnNode(IF_ICMPGE, updatesDone));
        code.add(new FieldInsnNode(GETSTATIC, receiver.owner, receiver.name, receiver.desc));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, update.owner, update.name, update.desc, update.itf));
        code.add(new FieldInsnNode(GETSTATIC, actor, "hsoMoveTick", "I"));
        code.add(new InsnNode(ICONST_1));
        code.add(new InsnNode(IADD));
        code.add(new FieldInsnNode(PUTSTATIC, actor, "hsoMoveTick", "I"));
        code.add(new MethodInsnNode(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
        code.add(new FieldInsnNode(PUTSTATIC, clockOwner, clockField, "J"));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new InsnNode(DUP));
        code.add(new FieldInsnNode(GETFIELD, CANVAS, ACCUMULATOR, "J"));
        code.add(new LdcInsnNode(Long.valueOf(40L)));
        code.add(new InsnNode(LSUB));
        code.add(new FieldInsnNode(PUTFIELD, CANVAS, ACCUMULATOR, "J"));
        code.add(new IincInsnNode(9, 1));
        code.add(new JumpInsnNode(GOTO, updateLoop));
        code.add(updatesDone);
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETFIELD, CANVAS, ACCUMULATOR, "J"));
        code.add(new LdcInsnNode(Long.valueOf(120L)));
        code.add(new InsnNode(LCMP));
        code.add(new JumpInsnNode(IFLT, noReset));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new InsnNode(LCONST_0));
        code.add(new FieldInsnNode(PUTFIELD, CANVAS, ACCUMULATOR, "J"));
        code.add(noReset);
        return code;
    }

    private static void initializeTempoLastTick(MethodNode run, CandidateMap map) {
        InsnList init = new InsnList();
        init.add(new VarInsnNode(ALOAD, 0));
        init.add(new MethodInsnNode(INVOKESTATIC, map.timeOwner, map.timeMethod, "()J", false));
        init.add(new FieldInsnNode(PUTFIELD, CANVAS, LAST_TICK, "J"));
        run.instructions.insert(init);
    }

    private static void insertPaintTimer(MethodNode run, CandidateMap map) {
        MethodInsnNode repaint = null;
        for (AbstractInsnNode n = run.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode && ((MethodInsnNode)n).owner.equals("javax/microedition/lcdui/Canvas")
                    && "repaint".equals(((MethodInsnNode)n).name) && "()V".equals(((MethodInsnNode)n).desc)) { repaint = (MethodInsnNode)n; break; }
        }
        require(repaint != null, "Hung repaint()V call missing");
        InsnList markPaint = new InsnList();
        markPaint.add(new MethodInsnNode(INVOKESTATIC, map.timeOwner, map.timeMethod, "()J", false));
        markPaint.add(new VarInsnNode(LSTORE, 3));
        run.instructions.insertBefore(repaint, markPaint);
    }

    private static void removeOuterClockWrite(MethodNode run, CandidateMap map) {
        int removed = 0;
        for (AbstractInsnNode n = run.instructions.getFirst(); n != null;) {
            AbstractInsnNode next = n.getNext();
            if (fieldIs(n, PUTSTATIC, map.clockOwner, map.clockField, "J")) {
                AbstractInsnNode time = previousReal(n);
                require(time instanceof MethodInsnNode && time.getOpcode() == INVOKESTATIC
                        && "()J".equals(((MethodInsnNode)time).desc), "Outer cached clock write shape changed");
                run.instructions.remove(time); run.instructions.remove(n); removed++;
            }
            n = next;
        }
        require(removed == 1, "Expected one outer cached clock write");
    }

    private static void removeHungDebugDelay(MethodNode run, CandidateMap map) {
        // The old FPS patch builds and discards an elapsed-time StringBuffer on slow frames.
        AbstractInsnNode stringBufferNew = null;
        for (AbstractInsnNode n = run.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof TypeInsnNode && n.getOpcode() == NEW
                    && "java/lang/StringBuffer".equals(((TypeInsnNode)n).desc)) stringBufferNew = n;
        }
        require(stringBufferNew != null, "Hung debug elapsed buffer missing");
        AbstractInsnNode oneSleep = null;
        for (AbstractInsnNode n = stringBufferNew; n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode && n.getOpcode() == INVOKESTATIC
                    && "java/lang/Thread".equals(((MethodInsnNode)n).owner)
                    && "sleep".equals(((MethodInsnNode)n).name) && "(J)V".equals(((MethodInsnNode)n).desc)) {
                AbstractInsnNode value = previousReal(n);
                if (value != null && value.getOpcode() == LCONST_1) { oneSleep = n; break; }
            }
        }
        require(oneSleep != null, "Hung slow-frame 1ms sleep missing");
        AbstractInsnNode sleepValue = previousReal(oneSleep);
        require(sleepValue != null && sleepValue.getOpcode() == LCONST_1, "Slow frame fallback lost its 1ms operand");
        AbstractInsnNode n = stringBufferNew;
        while (n != sleepValue) {
            AbstractInsnNode next = n.getNext();
            if (n.getOpcode() >= 0) run.instructions.remove(n);
            n = next;
        }
        // Replace the old 1ms fallback's following custom clock write with the donor's simple loop backedge.
        AbstractInsnNode maybeTime = nextReal(oneSleep);
        if (maybeTime instanceof MethodInsnNode && ((MethodInsnNode)maybeTime).desc.equals("()J")) {
            AbstractInsnNode maybePut = nextReal(maybeTime);
            if (maybePut instanceof FieldInsnNode && maybePut.getOpcode() == PUTSTATIC
                    && "J".equals(((FieldInsnNode)maybePut).desc)
                    && map.clockOwner.equals(((FieldInsnNode)maybePut).owner)
                    && map.clockField.equals(((FieldInsnNode)maybePut).name)) {
                run.instructions.remove(maybeTime); run.instructions.remove(maybePut);
            }
        }
    }

    private static void patchMovementGate(ClassNode actor, String player, String actorName) {
        MethodNode update = method(actor, "a", "()V");
        require(update != null, "Hung actor movement method missing");
        removeOldMovementGuard(update, player);
        AbstractInsnNode firstWrite = null, secondWrite = null;
        int writes = 0;
        for (AbstractInsnNode n = update.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTFIELD) continue;
            FieldInsnNode f = (FieldInsnNode)n;
            if (!actor.name.equals(f.owner) || !"I".equals(f.desc)) continue;
            if ("aZ".equals(f.name) || "ba".equals(f.name)) {
                writes++;
                if (writes == 1) firstWrite = n;
                else if (writes == 2) secondWrite = n;
            }
        }
        require(writes >= 2 && firstWrite != null && secondWrite != null, "Hung displacement writes missing in " + actor.name + " count=" + writes);
        AbstractInsnNode start = firstReal(update.instructions.getFirst());
        require(start != null && start.getOpcode() == ALOAD, "Hung actor movement prologue changed");
        LabelNode skip = new LabelNode();
        InsnList gate = new InsnList();
        gate.add(pushInt(109));
        gate.add(new FieldInsnNode(GETSTATIC, actorName, "hsoMoveTick", "I"));
        gate.add(pushInt(7));
        gate.add(new InsnNode(IAND));
        gate.add(new InsnNode(ISHR));
        gate.add(new InsnNode(ICONST_1));
        gate.add(new InsnNode(IAND));
        gate.add(new JumpInsnNode(IFEQ, skip));
        update.instructions.insertBefore(start, gate);
        update.instructions.insert(secondWrite, skip);
    }

    private static void removeOldMovementGuard(MethodNode update, String player) {
        AbstractInsnNode first = firstReal(update.instructions.getFirst());
        if (!(first instanceof VarInsnNode) || first.getOpcode() != ALOAD) return;
        AbstractInsnNode n = nextReal(first);
        if (!(n instanceof TypeInsnNode) || n.getOpcode() != INSTANCEOF || !player.equals(((TypeInsnNode)n).desc)) return;
        AbstractInsnNode jumpLocal = nextReal(n);
        AbstractInsnNode field = nextReal(jumpLocal);
        AbstractInsnNode jumpMove = nextReal(field);
        require(jumpLocal instanceof JumpInsnNode && jumpLocal.getOpcode() == IFEQ,
                "Old local movement guard branch missing");
        require(field instanceof FieldInsnNode && field.getOpcode() == GETSTATIC
                && "codexMoveThisFrame".equals(((FieldInsnNode)field).name), "Old movement guard field missing");
        require(jumpMove instanceof JumpInsnNode && jumpMove.getOpcode() == IFEQ,
                "Old movement guard skip missing");
        LabelNode move = ((JumpInsnNode)jumpLocal).label;
        LabelNode done = ((JumpInsnNode)jumpMove).label;
        AbstractInsnNode moveLabel = jumpMove.getNext();
        while (moveLabel != null && moveLabel.getOpcode() >= 0) moveLabel = moveLabel.getNext();
        require(moveLabel instanceof LabelNode && moveLabel == move, "Old movement continue label changed");
          // Remove executable guard instructions but keep metadata labels in place. Some of
          // these labels delimit the actor method's exception-table ranges; removing one
          // leaves ASM with a TryCatchBlockNode whose endpoint is no longer in the method.
          AbstractInsnNode n2 = first;
          while (n2 != moveLabel) {
              AbstractInsnNode next = n2.getNext();
              if (n2.getOpcode() >= 0) update.instructions.remove(n2);
              n2 = next;
          }
    }

    private static FieldInsnNode findIntFieldAppendedByStringBuilder(MethodNode m, String beforeText) {
        boolean afterLabel = false;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof LdcInsnNode && ((LdcInsnNode)n).cst instanceof String
                    && ((String)((LdcInsnNode)n).cst).contains(beforeText)) afterLabel = true;
            if (!afterLabel || !(n instanceof FieldInsnNode) || n.getOpcode() != GETSTATIC) continue;
            FieldInsnNode f = (FieldInsnNode)n;
            if (!"I".equals(f.desc)) continue;
            AbstractInsnNode append = nextReal(n);
            if (append instanceof MethodInsnNode && "(I)Ljava/lang/StringBuffer;".equals(((MethodInsnNode)append).desc)) return f;
            if (append instanceof MethodInsnNode && "(I)Ljava/lang/StringBuilder;".equals(((MethodInsnNode)append).desc)) return f;
        }
        return null;
    }

    private static boolean containsTempoOptions(MethodNode m) {
        return hasStringContaining(m, "x1") && hasStringContaining(m, "x5") && hasStringContaining(m, "TỐC");
    }

    private static String findTempoTickOwner(MethodNode run, String engine) {
        for (AbstractInsnNode n = run.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode) || n.getOpcode() != INVOKESTATIC) continue;
            MethodInsnNode call = (MethodInsnNode)n;
            if (!"()V".equals(call.desc) || call.owner.equals(engine)) continue;
            AbstractInsnNode x = n;
            for (int i = 0; i < 8 && x != null; i++, x = x.getNext()) {
                if (x instanceof FieldInsnNode && x.getOpcode() == GETSTATIC
                        && "hsoMoveTick".equals(((FieldInsnNode)x).name)) return call.owner;
            }
        }
        return null;
    }

    private static String findTempoTickMethod(MethodNode run, String owner) {
        if (owner == null) return null;
        for (AbstractInsnNode n = run.instructions.getFirst(); n != null; n = n.getNext())
            if (n instanceof MethodInsnNode && n.getOpcode() == INVOKESTATIC
                    && owner.equals(((MethodInsnNode)n).owner) && "()V".equals(((MethodInsnNode)n).desc)) {
                AbstractInsnNode x = n;
                for (int i = 0; i < 8 && x != null; i++, x = x.getNext())
                    if (x instanceof FieldInsnNode && "hsoMoveTick".equals(((FieldInsnNode)x).name)) return ((MethodInsnNode)n).name;
            }
        return null;
    }

    private static FieldInsnNode findInitializedLong(ClassNode owner, MethodNode clinit, long value) {
        if (clinit == null) return null;
        for (AbstractInsnNode n = clinit.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTSTATIC || !"J".equals(((FieldInsnNode)n).desc)) continue;
            AbstractInsnNode prior = previousReal(n);
            if (prior instanceof LdcInsnNode && Long.valueOf(value).equals(((LdcInsnNode)prior).cst)
                    && owner.name.equals(((FieldInsnNode)n).owner)) return (FieldInsnNode)n;
        }
        return null;
    }

    private static String findTimeOwner(MethodNode run) {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (AbstractInsnNode n = run.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode) || n.getOpcode() != INVOKESTATIC) continue;
            MethodInsnNode m = (MethodInsnNode)n;
            if (!"()J".equals(m.desc)) continue;
            String key = m.owner + "." + m.name;
            Integer old = counts.get(key);
            counts.put(key, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
        }
        String best = null; int count = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) if (e.getValue().intValue() > count) { best=e.getKey(); count=e.getValue().intValue(); }
        if (best == null) return null;
        return best.substring(0,best.lastIndexOf('.'));
    }

    private static String findTimeMethod(MethodNode run, String owner) {
        if (owner == null) return "";
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (AbstractInsnNode n = run.instructions.getFirst(); n != null; n = n.getNext())
            if (n instanceof MethodInsnNode && n.getOpcode() == INVOKESTATIC
                    && owner.equals(((MethodInsnNode)n).owner) && "()J".equals(((MethodInsnNode)n).desc)) {
                String name = ((MethodInsnNode)n).name; Integer old=counts.get(name);
                counts.put(name,Integer.valueOf(old==null?1:old.intValue()+1));
            }
        String best=null; int count=0;
        for(Map.Entry<String,Integer> e:counts.entrySet()) if(e.getValue().intValue()>count){best=e.getKey();count=e.getValue().intValue();}
        return best == null ? "" : best;
    }

    private static MethodInsnNode findClockCall(MethodNode run) {
        String owner=findTimeOwner(run), name=findTimeMethod(run,owner);
        if(owner==null||name==null)return null;
        for(AbstractInsnNode n=run.instructions.getFirst();n!=null;n=n.getNext())
            if(n instanceof MethodInsnNode&&n.getOpcode()==INVOKESTATIC&&owner.equals(((MethodInsnNode)n).owner)
                    &&name.equals(((MethodInsnNode)n).name)&&"()J".equals(((MethodInsnNode)n).desc))return (MethodInsnNode)n;
        return null;
    }

    private static String findLongFieldWithMostWrites(MethodNode run, boolean staticField) {
        Map<String,Integer> counts=new HashMap<String,Integer>();
        for(AbstractInsnNode n=run.instructions.getFirst();n!=null;n=n.getNext())
            if(n instanceof FieldInsnNode&&n.getOpcode()==(staticField?PUTSTATIC:PUTFIELD)&&"J".equals(((FieldInsnNode)n).desc)){
                FieldInsnNode f=(FieldInsnNode)n; String key=f.owner+"/"+f.name; Integer old=counts.get(key);
                counts.put(key,Integer.valueOf(old==null?1:old.intValue()+1));
            }
        String best=null;int count=0;
        for(Map.Entry<String,Integer> e:counts.entrySet())if(e.getValue().intValue()>count){best=e.getKey();count=e.getValue().intValue();}
        if(best==null)return null; return best.substring(best.lastIndexOf('/')+1);
    }

    private static String findFirstLongWrite(MethodNode run) {
        for(AbstractInsnNode n=run.instructions.getFirst();n!=null;n=n.getNext())
            if(n instanceof FieldInsnNode&&n.getOpcode()==PUTFIELD&&"J".equals(((FieldInsnNode)n).desc))return ((FieldInsnNode)n).name;
        return null;
    }

    private static String findPlayer(Iterable<ClassNode> classes) {
        String player = null;
        for (ClassNode c : classes) {
            MethodNode clinit = method(c, "<clinit>", "()V");
            if (clinit == null) continue;
            boolean twelve = false;
            for (AbstractInsnNode n = clinit.instructions.getFirst(); n != null; n = n.getNext())
                if (n.getOpcode() == ANEWARRAY && isInt(previousReal(n), 12)
                        && nextReal(n) != null && nextReal(n).getOpcode() == AASTORE) twelve = true;
            if (!twelve) continue;
            boolean keys = false;
            for (MethodNode m : c.methods) if (hasInt(m, 21) && hasInt(m, 23) && hasInt(m, 25) && hasInt(m, 27) && hasInt(m, 29)) keys = true;
            if (keys) { require(player == null, "Multiple 12-cell player classes"); player = c.name; }
        }
        require(player != null, "Hung 12-cell player class missing");
        return player;
    }

    private static void ensureChangedClasses(List<EntryData> before, List<EntryData> after) {
        String[] changed = changedClasses(before, after).split(",");
        require(changed.length == 4, "Expected exactly four changed classes, got " + changedClasses(before, after));
    }

    private static String changedClasses(List<EntryData> before, List<EntryData> after) {
        Map<String, EntryData> a = index(before), b = index(after);
        List<String> names = new ArrayList<String>();
        for (String n : a.keySet()) if (!equalBytes(a.get(n).bytes, b.get(n).bytes) && n.endsWith(".class")) names.add(n.substring(0, n.length()-6));
        Collections.sort(names);
        StringBuilder out = new StringBuilder();
        for (int i=0;i<names.size();i++) { if(i>0)out.append(','); out.append(names.get(i)); }
        return out.toString();
    }

    private static boolean equalBytes(byte[] a, byte[] b) {
        if (a == b) return true; if (a == null || b == null || a.length != b.length) return false;
        for (int i=0;i<a.length;i++) if(a[i]!=b[i])return false; return true;
    }

    private static String makeReport(String inputHash, String refHash, String outputHash,
            ReferenceMap ref, CandidateMap c, String changedClasses) {
        StringBuilder s = new StringBuilder();
        s.append("input.sha256=").append(inputHash).append('\n');
        s.append("reference.sha256=").append(refHash).append('\n');
        s.append("output.sha256=").append(outputHash).append('\n');
        s.append("reference.tempo=1..5; default=1; selected rows=x1..x5; marker=[x]\n");
        s.append("reference.scheduler=elapsed*speed, added twice to accumulator; step=40ms; max=3 updates/pass; drop accumulator at >=120ms; frame target=16ms\n");
        s.append("reference.menu=").append(ref.engine).append('.').append(ref.rootBuilder).append("()V / ").append(ref.engine).append('.').append(ref.optionBuilder).append("()V / ").append(ref.engine).append('.').append(ref.actionMethod).append("(II)V\n");
        s.append("candidate.menu=").append(c.menuOwner).append(".<init>()V / ").append(c.menuOwner).append('.').append(c.menuAction).append("(II)Z; root=-31; child=-3101..-3105; back=-34\n");
        s.append("candidate.canvas=").append(CANVAS).append(".run()V; update=").append(c.updateOwner).append('.').append(c.updateMethod).append(c.updateDesc).append("; tick=hsoMoveTick++ after each update\n");
        s.append("candidate.movement=").append(c.actor).append(".a()V; mask=(109 >>> (hsoMoveTick & 7)) & 1, gate applies to the first two displacement writes\n");
        s.append("changed.classes=").append(changedClasses).append('\n');
        return s.toString();
    }

    private static Properties makeCatalog(String inputHash, String refHash, String outputHash,
            ReferenceMap r, CandidateMap c, String changedClasses) {
        Properties p = new Properties();
        put(p,"input.sha256",inputHash); put(p,"reference.sha256",refHash); put(p,"output.sha256",outputHash);
        put(p,"changed.classes",changedClasses);
        put(p,"reference.speed.owner",r.engine); put(p,"reference.speed.field",r.speedField); put(p,"reference.speed.desc","I"); put(p,"reference.speed.default","1");
        put(p,"reference.menu.rootBuilder",r.rootBuilder+"()V"); put(p,"reference.menu.optionsBuilder",r.optionBuilder+"()V");
        put(p,"reference.menu.action",r.actionMethod+"(II)V"); put(p,"reference.menu.rootAction","8"); put(p,"reference.menu.optionActions","81,82,83,84,85");
        put(p,"reference.menu.rootLabel","Tốc độ Game: [xN]"); put(p,"reference.menu.optionLabels","Tốc độ x1..Tốc độ x5"); put(p,"reference.menu.selectedMarker"," [x]");
        put(p,"reference.canvas.owner",r.canvas); put(p,"reference.canvas.run","run()V"); put(p,"reference.canvas.update",r.tickOwner+"."+r.tickMethod+r.tickDesc);
        put(p,"reference.canvas.interval",r.intervalOwner+"."+r.intervalField+":J=16"); put(p,"reference.canvas.accumulatorStepMs","40"); put(p,"reference.canvas.maxUpdatesPerPass","3"); put(p,"reference.canvas.deltaScale","2*elapsed*speed");
        put(p,"reference.entity.owner",r.entity); put(p,"reference.entity.movement",r.movementMethod); put(p,"reference.entity.tickField","hsoMoveTick:I"); put(p,"reference.entity.mask","109/8");
        put(p,"candidate.speed.owner",c.fpsOwner); put(p,"candidate.speed.field",c.fpsField); put(p,"candidate.speed.desc","I"); put(p,"candidate.speed.default","1");
        put(p,"candidate.menu.owner",c.menuOwner); put(p,"candidate.menu.builder","<init>()V"); put(p,"candidate.menu.action",c.menuAction+"(II)Z");
        put(p,"candidate.menu.rootAction","-31"); put(p,"candidate.menu.childActions","-3101,-3102,-3103,-3104,-3105"); put(p,"candidate.menu.backAction","-34"); put(p,"candidate.menu.refresh",c.refreshOwner+"."+c.refreshMethod+"()V");
        put(p,"candidate.canvas.owner",CANVAS); put(p,"candidate.canvas.run","run()V"); put(p,"candidate.canvas.update",c.updateOwner+"."+c.updateMethod+c.updateDesc);
        put(p,"candidate.canvas.accumulatorField",ACCUMULATOR+":J"); put(p,"candidate.canvas.lastTickField",LAST_TICK+":J"); put(p,"candidate.canvas.frameTargetMs","16");
        put(p,"candidate.entity.owner",c.actor); put(p,"candidate.entity.movement","a()V"); put(p,"candidate.entity.tickField","hsoMoveTick:I"); put(p,"candidate.entity.mask","109/8");
        put(p,"candidate.menu.display",c.displayOwner+"."+c.displayMethod+"(Let;ILjava/lang/String;ZLet;)V");
        return p;
    }

    private static void put(Properties p, String key, String value) { p.setProperty(key, value); }

    private static List<EntryData> read(File file) throws IOException {
        List<EntryData> entries = new ArrayList<EntryData>();
        JarFile jar = new JarFile(file);
        try {
            Enumeration<JarEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                InputStream in = jar.getInputStream(e);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try { byte[] buf = new byte[8192]; for (int n; (n = in.read(buf)) >= 0;) out.write(buf, 0, n); }
                finally { in.close(); }
                entries.add(new EntryData(e, out.toByteArray()));
            }
        } finally { jar.close(); }
        return entries;
    }

    private static Map<String, EntryData> index(List<EntryData> entries) {
        Map<String, EntryData> map = new HashMap<String, EntryData>();
        for (EntryData e : entries) map.put(e.source.getName(), e);
        return map;
    }

    private static EntryData find(List<EntryData> entries, String name) {
        for (EntryData e : entries) if (name.equals(e.source.getName())) return e;
        return null;
    }

    private static EntryData replace(EntryData old, byte[] bytes) { return new EntryData(old.source, bytes); }

    private static void write(File output, List<EntryData> entries) throws IOException {
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot make output directory");
        JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
        try {
            for (EntryData data : entries) {
                JarEntry entry = new JarEntry(data.source.getName());
                if (data.source.getTime() >= 0) entry.setTime(data.source.getTime());
                if (data.source.getComment() != null) entry.setComment(data.source.getComment());
                if (data.source.getExtra() != null) entry.setExtra(data.source.getExtra());
                out.putNextEntry(entry); out.write(data.bytes); out.closeEntry();
            }
        } finally { out.close(); }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(f);
        try { byte[] b = new byte[16384]; for (int n; (n = in.read(b)) >= 0;) d.update(b, 0, n); }
        finally { in.close(); }
        StringBuilder s = new StringBuilder();
        for (byte b : d.digest()) s.append(String.format("%02X", Integer.valueOf(b & 255)));
        return s.toString();
    }

    private static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot make report directory");
        Files.write(file.toPath(), text.getBytes(Charset.forName("UTF-8")));
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static ClassNode parse(byte[] bytes) { ClassNode n = new ClassNode(ASM9); new ClassReader(bytes).accept(n, 0); return n; }
    private static byte[] writeClass(ClassNode n) { ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); n.accept(w); return w.toByteArray(); }
    private static MethodNode method(ClassNode n, String name, String desc) { for (MethodNode m : n.methods) if (name.equals(m.name) && desc.equals(m.desc)) return m; return null; }
    private static FieldNode field(ClassNode n, String name, String desc) { for (FieldNode f : n.fields) if (name.equals(f.name) && desc.equals(f.desc)) return f; return null; }
    private static AbstractInsnNode nextReal(AbstractInsnNode n) { AbstractInsnNode x = n == null ? null : n.getNext(); while (x != null && x.getOpcode() < 0) x = x.getNext(); return x; }
    private static AbstractInsnNode previousReal(AbstractInsnNode n) { AbstractInsnNode x = n == null ? null : n.getPrevious(); while (x != null && x.getOpcode() < 0) x = x.getPrevious(); return x; }
    private static AbstractInsnNode firstReal(AbstractInsnNode n) { while (n != null && n.getOpcode() < 0) n = n.getNext(); return n; }
    private static boolean fieldIs(AbstractInsnNode n, int opcode, String owner, String name, String desc) { return n instanceof FieldInsnNode && n.getOpcode() == opcode && owner.equals(((FieldInsnNode)n).owner) && name.equals(((FieldInsnNode)n).name) && desc.equals(((FieldInsnNode)n).desc); }
    private static boolean hasField(MethodNode m, int opcode, String owner, String name, String desc) { for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) if (fieldIs(n, opcode, owner, name, desc)) return true; return false; }
    private static int countField(MethodNode m, int opcode, String owner, String name, String desc) { int count=0; for (AbstractInsnNode n=m.instructions.getFirst(); n!=null; n=n.getNext()) if(fieldIs(n,opcode,owner,name,desc))count++; return count; }
    private static boolean hasMethod(MethodNode m, String name, String desc) { for (AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) if(n instanceof MethodInsnNode && name.equals(((MethodInsnNode)n).name) && desc.equals(((MethodInsnNode)n).desc))return true; return false; }
    private static boolean isInt(AbstractInsnNode n, int value) { if(n==null)return false; int op=n.getOpcode(); if(op>=ICONST_M1&&op<=ICONST_5)return op-ICONST_0==value; if(n instanceof IntInsnNode)return ((IntInsnNode)n).operand==value; if(n instanceof LdcInsnNode&&((LdcInsnNode)n).cst instanceof Integer)return ((Integer)((LdcInsnNode)n).cst).intValue()==value; return false; }
    private static AbstractInsnNode pushInt(int value) { if(value>=-1&&value<=5)return new InsnNode(ICONST_0+value); if(value>=Byte.MIN_VALUE&&value<=Byte.MAX_VALUE)return new IntInsnNode(BIPUSH,value); return new IntInsnNode(SIPUSH,value); }
    private static void ensureField(ClassNode n, int access, String name, String desc) { if(field(n,name,desc)==null)n.fields.add(new FieldNode(access,name,desc,null,null)); }
    private static void removeField(ClassNode n, FieldNode f) { n.fields.remove(f); }
    private static List<FieldInsnNode> fieldRefs(MethodNode m, int opcode, String desc) { List<FieldInsnNode> r=new ArrayList<FieldInsnNode>(); for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext())if(n instanceof FieldInsnNode&&n.getOpcode()==opcode&&desc.equals(((FieldInsnNode)n).desc))r.add((FieldInsnNode)n);return r; }
    private static boolean hasString(MethodNode m, String value) { for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext())if(n instanceof LdcInsnNode&&value.equals(((LdcInsnNode)n).cst))return true;return false; }
    private static boolean hasStringPrefix(MethodNode m, String value) { for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext())if(n instanceof LdcInsnNode&&((LdcInsnNode)n).cst instanceof String&&((String)((LdcInsnNode)n).cst).startsWith(value))return true;return false; }
    private static boolean hasStringContaining(MethodNode m, String value) { for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext())if(n instanceof LdcInsnNode&&((LdcInsnNode)n).cst instanceof String&&((String)((LdcInsnNode)n).cst).contains(value))return true;return false; }
    private static boolean hasInt(MethodNode m, int value) { for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext())if(isInt(n,value))return true;return false; }
    private static boolean hasIntRange(MethodNode m, int low, int high) { for(int i=low;i<=high;i++)if(hasInt(m,i))return true;return false; }
}
