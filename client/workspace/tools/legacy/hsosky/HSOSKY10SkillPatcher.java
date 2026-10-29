import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
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

public final class HSOSKY10SkillPatcher implements Opcodes {
    private static final String INPUT_JAR = "HSOSKY.jar";
    private static final String OUTPUT_JAR = "HSOSKY_10SKILL.jar";
    private static final String SUPPORT_CLASS = "HSOSKY10SkillSupport";

    private static final Map<String, byte[]> ENTRIES = new LinkedHashMap<String, byte[]>();
    private static final Map<String, Long> TIMES = new LinkedHashMap<String, Long>();

    private static ClassNode playerClass;
    private static ClassNode hotbarStorageClass;
    private static ClassNode paintClass;
    private static ClassNode canvasClass;
    private static ClassNode skillsClass;
    private static ClassNode shopClass;


    private static String keyStateOwner;
    private static String keyStateField;
    private static String keyHeldField;
    private static String actionMethod;
    private static String actionOwner;
    private static String actionDesc;
    private static int actionReceiverVar;

    private static int hotbarArrayPatches;
    private static int canvasArrayPatches;
    private static int canvasClearPatches;
    private static int keyMethodPatches;
    private static int actionInputPatches;
    private static int labelPatches;
    private static int positionPatches;
    private static int touchWidthPatches;
    private static int menuPatches;
    private static int migrationPatches;

    private HSOSKY10SkillPatcher() {
    }

    public static void main(String[] args) throws Exception {
        File input = new File(args.length > 0 ? args[0] : INPUT_JAR);
        File output = new File(args.length > 1 ? args[1] : OUTPUT_JAR);
        require(input.isFile(), "Missing input: " + input.getPath());
        require(!output.exists(), "Refusing to overwrite: " + output.getPath());
        readJar(input);
        discoverClasses();
        patchPlayer();
        patchCanvas();
        patchPaint();
        patchSkills();
        patchShop();
        validatePatch();
        verifyMigrationHelper();
        writeJar(output);
        verifyOutput(output);
        verifyPatchedClasses(output);

        System.out.println("[+] Built " + output.getPath());
        System.out.println("    hotbar arrays: " + hotbarArrayPatches);
        System.out.println("    key arrays/clear loops: " + canvasArrayPatches + "/" + canvasClearPatches);
        System.out.println("    key press/release + action: " + keyMethodPatches + "/" + actionInputPatches);
        System.out.println("    labels/position/touch: " + labelPatches + "/" + positionPatches + "/" + touchWidthPatches);
        System.out.println("    assignment menus: " + menuPatches);
        System.out.println("    saved-hotbar migration: " + migrationPatches);
    }

    private static void readJar(File file) throws IOException {
        JarFile jar = new JarFile(file);
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                ENTRIES.put(entry.getName(), readAll(jar.getInputStream(entry)));
                TIMES.put(entry.getName(), Long.valueOf(entry.getTime()));
            }
        } finally {
            jar.close();
        }
    }

    private static void discoverClasses() {
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode node = readClass(entry.getValue());
            if (containsString(node, "Player vec7")) {
                playerClass = node;
                hotbarStorageClass = node;
            }
            if (containsString(node, "PaintInfoGameScr vecfocus")) {
                paintClass = node;
            }
            if (containsString(node, "GameCanvas listPoint")) {
                canvasClass = node;
            }
            if (canvasClass == null && countBooleanArrays(node, 30) == 3
                    && classContainsInts(node, 114, 116, 121, 117, 105)) {
                canvasClass = node;
            }
            if (containsString(node, "TabSkillsNew menu")) {
                skillsClass = node;
            }
            if (containsString(node, "TabShopNew menu3")) {
                shopClass = node;
            }

            // LUNA 4.0.2 removes the old diagnostic marker strings.  These
            // structural fallbacks identify the same five UI/controller
            // classes without relying on a localized text resource.
            if (playerClass == null && looksLikeHotbarController(node)) {
                playerClass = node;
            }
            if (playerClass == null && node.name.equals(
                    "IIllIIIllIllIIIlllIIIlllIIllIIlllIlIIIllIllIlIIlIIIlllIIlIIllIIllIlIllIIllIIIllIIlIllIIlIIlIIllIIlIlIIllIllIIlIlIIlIIIllIIlIllIllIIlllIllIIlIlllIIllIlIlllIIlllIIIlIIlIIllIlllIlIIllIIIllIlIlIIlIIllIllIlIllIIIllIIll")) {
                playerClass = node;
            }
            if (hotbarStorageClass == null && node.name.equals(
                    "IllIIllIlIlIllIIllIIIllIIIllIIIllIIIlIlIlIllIlIIllIIlllIlllIIlIIIllIIlIllIIlllIIllIllIIIlIIlIlIlllIIIlllIIlIIIllIIIllIIIllIlIIlllIIlIlIIIlllIIlIlIIIlllIIlllIIlIllIllIIlIIIllIIllIlllIIllIIllIllIIllIIllIIlIIllIllIIllIIlIIl")) {
                hotbarStorageClass = node;
            }
            if (paintClass == null && looksLikeHotbarPainter(node)) {
                paintClass = node;
            }
            if (skillsClass == null && node.name.equals(
                    "IIIllIllIlIIIlIllIIlIllIIlIlllIlIIIllIIlIIllIIIllIIIllIIlIIlIIlllIIIlllIIlllIllIIIlIlllIlIllIIlllIIlllIIlIllIIllIIIlllIIIlIIIllIIlllIIIllIllIIIlllIIllIIIllIIlIIIllIllIIllIIllIIlllIIlllIIllIIllIIIlIIIlIlIIlllIlIIIlIIIlllIIllIllIIIlllIIllIl")) {
                skillsClass = node;
            }
            if (shopClass == null && node.name.equals(
                    "IIIllIIllIIIlIllIIllIIIlIIllIlIIlIIlIIIlIIIllIlllIlIIllIIIllIIllIIIlllIIllIIIllIllIllIllIIllIIllIIlIIIlllIIIlllIllIlIIlllIIIllIIIllIIlIIlllIIlIllIllIlIIlllIIllIIlIIIllIIlIllIllIIIlIIlIIlllIlIllIlIllIIlllIIllIIlIIIlllIIlllIIIllIIl")) {
                shopClass = node;
            }
        }
        require(playerClass != null, "Cannot locate Player/hotbar class");
        require(hotbarStorageClass != null, "Cannot locate hotbar storage class");
        require(paintClass != null, "Cannot locate PaintInfoGameScr class");
        require(canvasClass != null, "Cannot locate GameCanvas class");
        require(skillsClass != null, "Cannot locate TabSkillsNew class");
        require(shopClass != null, "Cannot locate TabShopNew class");
        System.out.println("Mapped hotbar=" + playerClass.name);
        System.out.println("Mapped hotbar storage=" + hotbarStorageClass.name);
        System.out.println("Mapped canvas=" + canvasClass.name);
        System.out.println("Mapped paint=" + paintClass.name);
        System.out.println("Mapped skills=" + skillsClass.name);
        System.out.println("Mapped shop=" + shopClass.name);
    }

    private static boolean looksLikeHotbarController(ClassNode node) {
        MethodNode clinit = findOptionalMethod(node, "<clinit>", "()V");
        if (clinit == null) return false;
        boolean hasFiveSlots = false;
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == ANEWARRAY && isInt(previousReal(insn), 5)
                    && nextReal(insn) != null && nextReal(insn).getOpcode() == AASTORE) {
                hasFiveSlots = true;
                break;
            }
        }
        if (!hasFiveSlots) return false;
        for (MethodNode method : node.methods) {
            if (containsInts(method, 21, 23, 25, 27, 29)
                    && (containsSelfCall(method, "(IZ)V") || containsVirtualCall(method, "(I)V"))) return true;
        }
        return false;
    }

    private static boolean containsVirtualCall(MethodNode method, String desc) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && insn.getOpcode() == INVOKEVIRTUAL
                    && desc.equals(((MethodInsnNode) insn).desc)) return true;
        }
        return false;
    }

    private static boolean looksLikeHotbarPainter(ClassNode node) {
        MethodNode clinit = findOptionalMethod(node, "<clinit>", "()V");
        if (clinit == null) return false;
        boolean numbers = false;
        boolean letters = false;
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != PUTSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode) insn;
            if (!node.name.equals(field.owner)) continue;
            if ("[I".equals(field.desc) && priorWindowContainsInts(insn, 35, 1, 3, 5, 7, 9)) numbers = true;
            if ("[Ljava/lang/String;".equals(field.desc)
                    && priorWindowContainsStrings(insn, 35, "R", "T", "Y", "U", "I")) letters = true;
        }
        return numbers && letters;
    }

    private static MethodNode findOptionalMethod(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        return null;
    }


    private static void patchPlayer() {
        MethodNode clinit = findMethod(hotbarStorageClass, "<clinit>", "()V");
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == ANEWARRAY && isInt(previousReal(insn), 5)
                    && nextReal(insn) != null && nextReal(insn).getOpcode() == AASTORE) {
                replaceInt(clinit, previousReal(insn), 10);
                hotbarArrayPatches++;
            }
        }

        MethodNode input = null;
        for (MethodNode method : playerClass.methods) {
            if (containsInts(method, 21, 23, 25, 27, 29)
                    && (containsSelfCall(method, "(IZ)V") || containsVirtualCall(method, "(I)V"))) {
                input = method;
                break;
            }
        }
        require(input != null, "Cannot locate hotbar keyboard handler");

        FieldInsnNode firstKeyRead = null;
        for (AbstractInsnNode insn = input.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == GETSTATIC
                    && "[Z".equals(((FieldInsnNode) insn).desc)
                    && isInt(nextReal(insn), 21)) {
                firstKeyRead = (FieldInsnNode) insn;
                break;
            }
        }
        require(firstKeyRead != null, "Cannot identify game key-state array");
        keyStateOwner = firstKeyRead.owner;
        keyStateField = firstKeyRead.name;

        for (AbstractInsnNode insn = input.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (playerClass.name.equals(call.owner) && "(IZ)V".equals(call.desc)) {
                    actionMethod = call.name;
                    actionOwner = call.owner;
                    actionDesc = call.desc;
                    actionReceiverVar = 0;
                    break;
                }
            }
        }
        if (actionMethod == null) {
            for (AbstractInsnNode insn = firstKeyRead; insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() != INVOKEVIRTUAL || !"(I)V".equals(call.desc)) continue;
                AbstractInsnNode argument = previousReal(insn);
                AbstractInsnNode receiver = previousReal(argument);
                if (intValue(argument) == null || !(receiver instanceof VarInsnNode)
                        || receiver.getOpcode() != ALOAD) continue;
                actionMethod = call.name;
                actionOwner = call.owner;
                actionDesc = call.desc;
                actionReceiverVar = ((VarInsnNode) receiver).var;
                break;
            }
        }
        require(actionMethod != null, "Cannot identify Player hotbar action method");

        InsnList extraKeys = new InsnList();
        for (int i = 0; i < 5; i++) {
            LabelNode skip = new LabelNode();
            extraKeys.add(new FieldInsnNode(GETSTATIC, keyStateOwner, keyStateField, "[Z"));
            extraKeys.add(pushInt(30 + i));
            extraKeys.add(new InsnNode(BALOAD));
            extraKeys.add(new JumpInsnNode(IFEQ, skip));
            extraKeys.add(new VarInsnNode(ALOAD, actionReceiverVar));
            extraKeys.add(pushInt(5 + i));
            if ("(IZ)V".equals(actionDesc)) extraKeys.add(new InsnNode(ICONST_1));
            extraKeys.add(new MethodInsnNode(INVOKEVIRTUAL, actionOwner, actionMethod, actionDesc, false));
            extraKeys.add(skip);
        }
        input.instructions.insertBefore(firstKeyRead, extraKeys);
        actionInputPatches++;
        saveClass(playerClass);
        if (hotbarStorageClass != playerClass) saveClass(hotbarStorageClass);
    }

    private static void patchCanvas() {
        MethodNode clinit = findMethod(canvasClass, "<clinit>", "()V");
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == NEWARRAY && ((IntInsnNode) insn).operand == T_BOOLEAN
                    && isInt(previousReal(insn), 30)) {
                replaceInt(clinit, previousReal(insn), 35);
                canvasArrayPatches++;
            }
        }

        MethodNode press = null;
        MethodNode release = null;
        for (MethodNode method : canvasClass.methods) {
            if (!"(I)V".equals(method.desc)) {
                continue;
            }
            if (containsInts(method, 114, 116, 121, 117, 105) && containsField(method, keyStateOwner, keyStateField, "[Z")) {
                press = method;
            }
        }
        require(press != null, "Cannot locate keyPressed handler");

        Set<String> booleanFields = new HashSet<String>();
        for (AbstractInsnNode insn = press.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (canvasClass.name.equals(field.owner) && "[Z".equals(field.desc)) {
                    booleanFields.add(field.name);
                }
            }
        }
        require(booleanFields.contains(keyStateField), "keyPressed does not use detected key-state field");
        booleanFields.remove(keyStateField);
        require(!booleanFields.isEmpty(), "Cannot identify held-key array");

        for (String candidate : booleanFields) {
            for (MethodNode method : canvasClass.methods) {
                if (method == press || !"(I)V".equals(method.desc)) {
                    continue;
                }
                if (containsField(method, canvasClass.name, candidate, "[Z") && containsSwitch(method)) {
                    keyHeldField = candidate;
                    release = method;
                    break;
                }
            }
            if (release != null) {
                break;
            }
        }
        require(release != null, "Cannot locate keyReleased handler");

        injectLetterKeys(press, true);
        injectLetterKeys(release, false);
        keyMethodPatches += 2;

        for (MethodNode method : canvasClass.methods) {
            if ("<clinit>".equals(method.name) || !containsOwnBooleanArray(method)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (isInt(insn, 30)) {
                    AbstractInsnNode next = nextReal(insn);
                    if (next instanceof JumpInsnNode) {
                        replaceInt(method, insn, 35);
                        canvasClearPatches++;
                    }
                }
            }
        }
        saveClass(canvasClass);
    }

    private static void injectLetterKeys(MethodNode method, boolean down) {
        int[] upper = new int[]{70, 71, 72, 74, 75}; // F G H J K
        int[] lower = new int[]{102, 103, 104, 106, 107};
        int keyVar = (method.access & ACC_STATIC) != 0 ? 0 : 1;
        InsnList code = new InsnList();
        for (int i = 0; i < upper.length; i++) {
            LabelNode hit = new LabelNode();
            LabelNode skip = new LabelNode();
            code.add(new VarInsnNode(ILOAD, keyVar));
            code.add(pushInt(upper[i]));
            code.add(new JumpInsnNode(IF_ICMPEQ, hit));
            code.add(new VarInsnNode(ILOAD, keyVar));
            code.add(pushInt(lower[i]));
            code.add(new JumpInsnNode(IF_ICMPNE, skip));
            code.add(hit);
            emitBooleanStore(code, keyHeldField, 30 + i, down);
            emitBooleanStore(code, keyStateField, 30 + i, down);
            code.add(skip);
        }
        method.instructions.insert(code);
    }

    private static void emitBooleanStore(InsnList code, String field, int index, boolean value) {
        code.add(new FieldInsnNode(GETSTATIC, canvasClass.name, field, "[Z"));
        code.add(pushInt(index));
        code.add(new InsnNode(value ? ICONST_1 : ICONST_0));
        code.add(new InsnNode(BASTORE));
    }

    private static void patchPaint() {
        MethodNode clinit = findMethod(paintClass, "<clinit>", "()V");
        String numberLabels = null;
        String pcLabels = null;
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == PUTSTATIC) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (paintClass.name.equals(field.owner) && "[I".equals(field.desc)
                        && priorWindowContainsInts(insn, 35, 1, 3, 5, 7, 9)) {
                    numberLabels = field.name;
                }
                if (paintClass.name.equals(field.owner) && "[Ljava/lang/String;".equals(field.desc)
                        && priorWindowContainsStrings(insn, 35, "R", "T", "Y", "U", "I")) {
                    pcLabels = field.name;
                }
            }
        }
        require(numberLabels != null && pcLabels != null, "Cannot identify hotbar label arrays");

        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == RETURN) {
                InsnList labels = new InsnList();
                emitIntArray(labels, new int[]{1, 3, 5, 7, 9, 2, 4, 6, 8, 0});
                labels.add(new FieldInsnNode(PUTSTATIC, paintClass.name, numberLabels, "[I"));
                emitStringArray(labels, new String[]{"R", "T", "Y", "U", "I", "F", "G", "H", "J", "K"});
                labels.add(new FieldInsnNode(PUTSTATIC, paintClass.name, pcLabels, "[Ljava/lang/String;"));
                clinit.instructions.insertBefore(insn, labels);
                labelPatches++;
            }
        }

        MethodNode draw = null;
        for (MethodNode method : paintClass.methods) {
            if (containsField(method, paintClass.name, pcLabels, "[Ljava/lang/String;")) {
                draw = method;
                break;
            }
        }
        require(draw != null, "Cannot locate horizontal hotbar draw method");

        String startField = null;
        String spacingField = null;
        Map<String, Integer> pairCounts = new LinkedHashMap<String, Integer>();
        for (AbstractInsnNode insn = draw.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != GETSTATIC) {
                continue;
            }
            FieldInsnNode first = (FieldInsnNode) insn;
            AbstractInsnNode secondInsn = nextReal(nextReal(insn));
            AbstractInsnNode mul = secondInsn == null ? null : nextReal(secondInsn);
            AbstractInsnNode add = mul == null ? null : nextReal(mul);
            if (!paintClass.name.equals(first.owner) || !"I".equals(first.desc)
                    || !(secondInsn instanceof FieldInsnNode) || secondInsn.getOpcode() != GETSTATIC
                    || !paintClass.name.equals(((FieldInsnNode) secondInsn).owner) || !"I".equals(((FieldInsnNode) secondInsn).desc)
                    || mul == null || mul.getOpcode() != IMUL || add == null || add.getOpcode() != IADD) {
                continue;
            }
            FieldInsnNode second = (FieldInsnNode) secondInsn;
            String key = first.name + "\n" + second.name;
            Integer count = pairCounts.get(key);
            pairCounts.put(key, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }
        int best = 0;
        for (Map.Entry<String, Integer> entry : pairCounts.entrySet()) {
            if (entry.getValue().intValue() > best) {
                best = entry.getValue().intValue();
                String[] pair = entry.getKey().split("\\n");
                startField = pair[0];
                spacingField = pair[1];
            }
        }
        require(startField != null && spacingField != null, "Cannot identify hotbar position fields");

        FieldInsnNode screenCenter = null;
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == PUTSTATIC
                    && paintClass.name.equals(((FieldInsnNode) insn).owner)
                    && startField.equals(((FieldInsnNode) insn).name)) {
                AbstractInsnNode cursor = previousReal(insn);
                for (int i = 0; i < 8 && cursor != null; i++, cursor = previousReal(cursor)) {
                    if (cursor instanceof FieldInsnNode && cursor.getOpcode() == GETSTATIC
                            && "I".equals(((FieldInsnNode) cursor).desc)) {
                        screenCenter = (FieldInsnNode) cursor;
                        break;
                    }
                }
                if (screenCenter != null) {
                    break;
                }
            }
        }
        require(screenCenter != null, "Cannot identify screen-center field");

        for (MethodNode method : paintClass.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (isInt(insn, 5)) {
                    AbstractInsnNode fieldInsn = nextReal(insn);
                    AbstractInsnNode mul = nextReal(fieldInsn);
                    if (fieldInsn instanceof FieldInsnNode && fieldInsn.getOpcode() == GETSTATIC
                            && paintClass.name.equals(((FieldInsnNode) fieldInsn).owner)
                            && spacingField.equals(((FieldInsnNode) fieldInsn).name)
                            && mul != null && mul.getOpcode() == IMUL) {
                        replaceInt(method, insn, 10);
                        touchWidthPatches++;
                    }
                }
            }
        }

        InsnList center = new InsnList();
        center.add(new FieldInsnNode(GETSTATIC, screenCenter.owner, screenCenter.name, screenCenter.desc));
        center.add(new InsnNode(ICONST_5));
        center.add(new FieldInsnNode(GETSTATIC, paintClass.name, spacingField, "I"));
        center.add(new InsnNode(IMUL));
        center.add(new InsnNode(ISUB));
        center.add(new FieldInsnNode(PUTSTATIC, paintClass.name, startField, "I"));
        draw.instructions.insert(center);
        positionPatches++;
        saveClass(paintClass);
    }

    private static void patchSkills() {
        patchMenuLoop(skillsClass, "TabSkillsNew menu");
        MethodNode loader = null;
        for (MethodNode method : skillsClass.methods) {
            if ((method.access & ACC_STATIC) != 0 && "([B)V".equals(method.desc)) {
                loader = method;
                break;
            }
        }
        require(loader != null, "Cannot locate saved-hotbar loader");
        InsnList migration = new InsnList();
        migration.add(new VarInsnNode(ALOAD, 0));
        migration.add(new MethodInsnNode(INVOKESTATIC, SUPPORT_CLASS, "expandHotbar", "([B)[B", false));
        migration.add(new VarInsnNode(ASTORE, 0));
        loader.instructions.insert(migration);
        migrationPatches++;
        saveClass(skillsClass);
    }

    private static void patchShop() {
        patchMenuLoop(shopClass, "TabShopNew menu3");
        saveClass(shopClass);
    }

    private static void patchMenuLoop(ClassNode owner, String marker) {
        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof LdcInsnNode) || !marker.equals(((LdcInsnNode) insn).cst)) {
                    continue;
                }
                AbstractInsnNode cursor = insn;
                for (int i = 0; i < 45 && cursor != null; i++, cursor = nextReal(cursor)) {
                    if (isInt(cursor, 5) && nextReal(cursor) instanceof JumpInsnNode) {
                        int opcode = nextReal(cursor).getOpcode();
                        if (opcode == IF_ICMPGE || opcode == IF_ICMPLT) {
                            replaceInt(method, cursor, 10);
                            menuPatches++;
                            return;
                        }
                    }
                }
            }
        }
        // LUNA production builds strip the diagnostic marker strings, while the
        // actual assignment menu loop is still present.  Accept the structural
        // form only when it identifies exactly one 5-slot comparison in the
        // already-mapped skills/shop class, so an unrelated loop is never changed.
        MethodNode fallbackMethod = null;
        AbstractInsnNode fallbackLimit = null;
        int fallbackCount = 0;
        for (MethodNode method : owner.methods) {
            if (!"(II)V".equals(method.desc) || (method.access & ACC_STATIC) != 0) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!isInt(insn, 5)) {
                    continue;
                }
                AbstractInsnNode jump = nextReal(insn);
                if (jump instanceof JumpInsnNode
                        && (jump.getOpcode() == IF_ICMPGE || jump.getOpcode() == IF_ICMPLT)) {
                    fallbackMethod = method;
                    fallbackLimit = insn;
                    fallbackCount++;
                }
            }
        }
        if (fallbackCount == 1) {
            replaceInt(fallbackMethod, fallbackLimit, 10);
            menuPatches++;
            return;
        }
        throw new IllegalStateException("Cannot patch menu loop: " + marker);
    }

    private static void validatePatch() {
        require(hotbarArrayPatches == 1, "Expected exactly one Player hotbar array patch, got " + hotbarArrayPatches);
        require(canvasArrayPatches == 3, "Expected three key arrays, got " + canvasArrayPatches);
        require(canvasClearPatches >= 2, "Expected key clear-loop patches, got " + canvasClearPatches);
        require(keyMethodPatches == 2 && actionInputPatches == 1, "Keyboard patch is incomplete");
        require(labelPatches == 1 && positionPatches == 1, "Hotbar drawing patch is incomplete");
        require(touchWidthPatches >= 1, "Touch hitbox width was not patched");
        require(menuPatches == 2, "Expected both skill/item assignment menus to be patched");
        require(migrationPatches == 1, "Saved data migration was not injected");
    }

    private static void verifyMigrationHelper() {
        byte[] oldData = new byte[60];
        oldData[0] = 12;
        oldData[22] = 34;
        byte[] migrated = HSOSKY10SkillSupport.expandHotbar(oldData);
        require(migrated.length == 120, "Migration helper returned the wrong data length");
        require(migrated[0] == 12 && migrated[42] == 34, "Migration helper did not preserve old slots");
        require(migrated[22] == (byte) -1 && migrated[62] == (byte) -1,
                "Migration helper did not initialize new slots as empty");
    }

    private static void writeJar(File output) throws IOException {
        File temp = new File(output.getPath() + ".tmp");
        JarOutputStream out = new JarOutputStream(new FileOutputStream(temp));
        try {
            for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
                if (entry.getKey().startsWith("META-INF/")
                        && (entry.getKey().endsWith(".SF") || entry.getKey().endsWith(".RSA") || entry.getKey().endsWith(".DSA"))) {
                    continue;
                }
                JarEntry jarEntry = new JarEntry(entry.getKey());
                Long time = TIMES.get(entry.getKey());
                if (time != null && time.longValue() >= 0L) {
                    jarEntry.setTime(time.longValue());
                }
                out.putNextEntry(jarEntry);
                out.write(entry.getValue());
                out.closeEntry();
            }

            byte[] supportBytes = readAll(new FileInputStream(SUPPORT_CLASS + ".class"));
            ClassNode supportNode = readClass(supportBytes);
            supportNode.version = V1_6;
            ClassWriter supportWriter = new ClassWriter(0);
            supportNode.accept(supportWriter);
            byte[] support = supportWriter.toByteArray();
            JarEntry supportEntry = new JarEntry(SUPPORT_CLASS + ".class");
            out.putNextEntry(supportEntry);
            out.write(support);
            out.closeEntry();
        } finally {
            out.close();
        }
        Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void verifyOutput(File output) throws IOException {
        JarFile jar = new JarFile(output);
        try {
            require(jar.getEntry("META-INF/MANIFEST.MF") != null, "Output JAR lost its manifest");
            JarEntry supportEntry = jar.getJarEntry(SUPPORT_CLASS + ".class");
            require(supportEntry != null, "Output JAR lacks migration helper");
            byte[] supportBytes = readAll(jar.getInputStream(supportEntry));
            require(new ClassReader(supportBytes).readUnsignedShort(6) == V1_6,
                    "Migration helper must retain Java 6 bytecode compatibility");
            require(jar.size() >= ENTRIES.size(), "Output JAR unexpectedly lost entries");
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    new ClassReader(readAll(jar.getInputStream(entry)));
                }
            }
        } finally {
            jar.close();
        }
    }

    private static void verifyPatchedClasses(File output) throws Exception {
        URL[] urls = new URL[]{output.toURI().toURL(), new File("microemulator.jar").toURI().toURL()};
        URLClassLoader loader = new URLClassLoader(urls, HSOSKY10SkillPatcher.class.getClassLoader());
        try {
            String[] patched = new String[]{playerClass.name, paintClass.name, canvasClass.name,
                    skillsClass.name, shopClass.name};
            for (String internalName : patched) {
                Class.forName(internalName.replace('/', '.'), false, loader);
            }
        } finally {
            loader.close();
        }
    }

    private static void saveClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        ENTRIES.put(node.name + ".class", writer.toByteArray());
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode findMethod(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return method;
            }
        }
        throw new IllegalStateException("Cannot find " + owner.name + "." + name + desc);
    }

    private static boolean containsString(ClassNode owner, String value) {
        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String
                        && ((String) ((LdcInsnNode) insn).cst).indexOf(value) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsInts(MethodNode method, int... values) {
        Set<Integer> found = new HashSet<Integer>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            Integer value = intValue(insn);
            if (value != null) {
                found.add(value);
            }
        }
        for (int value : values) {
            if (!found.contains(Integer.valueOf(value))) {
                return false;
            }
        }
        return true;
    }

    private static boolean classContainsInts(ClassNode owner, int... values) {
        Set<Integer> found = new HashSet<Integer>();
        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                Integer value = intValue(insn);
                if (value != null) {
                    found.add(value);
                }
            }
        }
        for (int value : values) {
            if (!found.contains(Integer.valueOf(value))) {
                return false;
            }
        }
        return true;
    }

    private static int countBooleanArrays(ClassNode owner, int size) {
        int count = 0;
        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof IntInsnNode && insn.getOpcode() == NEWARRAY
                        && ((IntInsnNode) insn).operand == T_BOOLEAN && isInt(previousReal(insn), size)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean containsSelfCall(MethodNode method, String desc) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && playerClass.name.equals(((MethodInsnNode) insn).owner)
                    && desc.equals(((MethodInsnNode) insn).desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsField(MethodNode method, String owner, String name, String desc) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (owner.equals(field.owner) && name.equals(field.name) && desc.equals(field.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsOwnBooleanArray(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (canvasClass.name.equals(field.owner) && "[Z".equals(field.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsSwitch(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == TABLESWITCH || insn.getOpcode() == LOOKUPSWITCH) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNearbyBooleanAccess(AbstractInsnNode start, int distance) {
        AbstractInsnNode cursor = start;
        for (int i = 0; i < distance && cursor != null; i++, cursor = nextReal(cursor)) {
            if (cursor instanceof FieldInsnNode && "[Z".equals(((FieldInsnNode) cursor).desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean priorWindowContainsInts(AbstractInsnNode end, int distance, int... values) {
        Set<Integer> found = new HashSet<Integer>();
        AbstractInsnNode cursor = previousReal(end);
        for (int i = 0; i < distance && cursor != null; i++, cursor = previousReal(cursor)) {
            Integer value = intValue(cursor);
            if (value != null) {
                found.add(value);
            }
        }
        for (int value : values) {
            if (!found.contains(Integer.valueOf(value))) {
                return false;
            }
        }
        return true;
    }

    private static boolean priorWindowContainsStrings(AbstractInsnNode end, int distance, String... values) {
        Set<String> found = new HashSet<String>();
        AbstractInsnNode cursor = previousReal(end);
        for (int i = 0; i < distance && cursor != null; i++, cursor = previousReal(cursor)) {
            if (cursor instanceof LdcInsnNode && ((LdcInsnNode) cursor).cst instanceof String) {
                found.add((String) ((LdcInsnNode) cursor).cst);
            }
        }
        return found.containsAll(Arrays.asList(values));
    }

    private static void emitIntArray(InsnList code, int[] values) {
        code.add(pushInt(values.length));
        code.add(new IntInsnNode(NEWARRAY, T_INT));
        for (int i = 0; i < values.length; i++) {
            code.add(new InsnNode(DUP));
            code.add(pushInt(i));
            code.add(pushInt(values[i]));
            code.add(new InsnNode(IASTORE));
        }
    }

    private static void emitStringArray(InsnList code, String[] values) {
        code.add(pushInt(values.length));
        code.add(new TypeInsnNode(ANEWARRAY, "java/lang/String"));
        for (int i = 0; i < values.length; i++) {
            code.add(new InsnNode(DUP));
            code.add(pushInt(i));
            code.add(new LdcInsnNode(values[i]));
            code.add(new InsnNode(AASTORE));
        }
    }

    private static void replaceInt(MethodNode method, AbstractInsnNode oldInsn, int value) {
        method.instructions.set(oldInsn, pushInt(value));
    }

    private static AbstractInsnNode pushInt(int value) {
        if (value >= -1 && value <= 5) {
            return new InsnNode(ICONST_0 + value);
        }
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            return new IntInsnNode(BIPUSH, value);
        }
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            return new IntInsnNode(SIPUSH, value);
        }
        return new LdcInsnNode(Integer.valueOf(value));
    }

    private static boolean isInt(AbstractInsnNode insn, int expected) {
        Integer value = intValue(insn);
        return value != null && value.intValue() == expected;
    }

    private static Integer intValue(AbstractInsnNode insn) {
        if (insn == null) {
            return null;
        }
        int opcode = insn.getOpcode();
        if (opcode >= ICONST_M1 && opcode <= ICONST_5) {
            return Integer.valueOf(opcode - ICONST_0);
        }
        if (insn instanceof IntInsnNode && (opcode == BIPUSH || opcode == SIPUSH)) {
            return Integer.valueOf(((IntInsnNode) insn).operand);
        }
        if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer) {
            return (Integer) ((LdcInsnNode) insn).cst;
        }
        return null;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn == null ? null : insn.getNext();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        return cursor;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn == null ? null : insn.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getPrevious();
        }
        return cursor;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
