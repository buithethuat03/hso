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
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HSOSKYPetFeedPatcher implements Opcodes {
    private static final String INPUT_JAR = "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_TEST.jar";
    private static final String OUTPUT_JAR = "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP.jar";
    private static final String SUPPORT_CLASS = "HSOSKYPetFeedSupport";

    private static final Map<String, byte[]> ENTRIES = new LinkedHashMap<String, byte[]>();
    private static final Map<String, Long> TIMES = new LinkedHashMap<String, Long>();

    private static ClassNode itemClass;
    private static ClassNode vectorClass;
    private static ClassNode networkClass;
    private static ClassNode midletClass;
    private static ClassNode canvasClass;
    private static ClassNode playerClass;
    private static String inventoryField;
    private static String petContainerField;
    private static String itemIdField;
    private static String itemTypeField;
    private static String itemGradeField;
    private static String itemClassField;
    private static String vectorSizeMethod;
    private static String vectorGetMethod;
    private static String networkSingletonMethod;
    private static String networkInteractMethod;
    private static String networkFeedMethod;
    private static String networkChatMethod;
    private static String firstDialogField;
    private static String secondDialogField;
    private static String dialogDesc;
    private static String keyStateField;
    private static String keyHeldField;
    private static String showMessageMethod;
    private static String targetBaseClass;
    private static String targetTypeField;
    private static String hostilityMethod;
    private static MethodNode autoTargetMethod;
    private static MethodNode playerUpdateMethod;
    private static int startPatches;
    private static int chatPatches;
    private static int autoTargetPatches;
    private static int playerLoopPatches;

    private HSOSKYPetFeedPatcher() {
    }

    public static void main(String[] args) throws Exception {
        require(!new File(OUTPUT_JAR).exists(), "Refusing to overwrite existing output: " + OUTPUT_JAR);
        readJar(new File(INPUT_JAR));
        discoverItemAndVector();
        discoverItemFields();
        discoverNetwork();
        discoverMidlet();
        discoverDialogs();
        discoverAutoCombat();
        byte[] supportBytes = remapSupport(readAll(new FileInputStream(SUPPORT_CLASS + ".class")));
        validateMidletStart();
        patchChatCommand();
        patchAutoCombat();
        ENTRIES.put(SUPPORT_CLASS + ".class", supportBytes);
        validateSupport(supportBytes);
        require(startPatches == 1, "Expected one MIDlet scheduler-start patch, got " + startPatches);
        require(chatPatches == 1, "Expected one chat-command hook, got " + chatPatches);
        require(autoTargetPatches == 1, "Expected one auto-target filter patch, got " + autoTargetPatches);
        require(playerLoopPatches == 2, "Expected two player-loop target patches, got " + playerLoopPatches);
        writeJar(new File(OUTPUT_JAR));
        verifyOutput(new File(OUTPUT_JAR));

        System.out.println("[+] Built " + OUTPUT_JAR);
        System.out.println("    scan interval: 60000 ms");
        System.out.println("    feedable grades: 2 (yellow), 3 (purple), 4 (orange); green is always excluded");
        System.out.println("    feed policy: all matching items per scan (500 ms between items)");
        System.out.println("    notification: wait 1000 ms, then press Enter for 150 ms");
        System.out.println("    modes: 3=yellow/purple, 4=add orange except Gunner gear, 5=feed every yellow/purple/orange item");
        System.out.println("    auto combat: 6=monsters + hostile players, 7=monsters only (default)");
        System.out.println("    help: show all client commands in game");
        System.out.println("    inventory/pet fields: " + shortName(inventoryField) + "/" + shortName(petContainerField));
        System.out.println("    item fields id/type/grade/class: " + shortName(itemIdField) + "/" + shortName(itemTypeField) + "/" + shortName(itemGradeField) + "/" + shortName(itemClassField));
    }

    private static void readJar(File file) throws IOException {
        require(file.isFile(), "Missing input JAR: " + file.getPath());
        JarFile jar = new JarFile(file);
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    ENTRIES.put(entry.getName(), readAll(jar.getInputStream(entry)));
                    TIMES.put(entry.getName(), Long.valueOf(entry.getTime()));
                }
            }
        } finally {
            jar.close();
        }
    }

    private static void discoverItemAndVector() {
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode candidate = readClass(entry.getValue());
            MarkerField inventory = findMarkerField(candidate, "Item VecInvetoryPlayer");
            MarkerField pets = findMarkerField(candidate, "Item VecPetContainer");
            if (inventory != null && pets != null && inventory.desc.equals(pets.desc)) {
                itemClass = candidate;
                inventoryField = inventory.name;
                petContainerField = pets.name;
                String vectorName = Type.getType(inventory.desc).getInternalName();
                vectorClass = readClass(ENTRIES.get(vectorName + ".class"));
                break;
            }
        }
        require(itemClass != null && vectorClass != null, "Cannot locate Item inventory/pet-container classes");

        for (MethodNode method : vectorClass.methods) {
            if ("()I".equals(method.desc) && calls(method, "java/util/Vector", "size", "()I")) {
                vectorSizeMethod = method.name;
            }
            if ("(I)Ljava/lang/Object;".equals(method.desc)
                    && calls(method, "java/util/Vector", "elementAt", "(I)Ljava/lang/Object;")) {
                vectorGetMethod = method.name;
            }
        }
        require(vectorSizeMethod != null && vectorGetMethod != null, "Cannot identify vector size/get methods");
    }

    private static MarkerField findMarkerField(ClassNode owner, String marker) {
        MethodNode clinit = findMethod(owner, "<clinit>", "()V");
        if (clinit == null) {
            return null;
        }
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof LdcInsnNode) || !marker.equals(((LdcInsnNode) insn).cst)) {
                continue;
            }
            AbstractInsnNode cursor = insn;
            for (int i = 0; i < 10 && cursor != null; i++, cursor = nextReal(cursor)) {
                if (cursor instanceof FieldInsnNode && cursor.getOpcode() == PUTSTATIC) {
                    FieldInsnNode field = (FieldInsnNode) cursor;
                    if (owner.name.equals(field.owner) && field.desc.startsWith("L")) {
                        return new MarkerField(field.name, field.desc);
                    }
                }
            }
        }
        return null;
    }

    private static void discoverItemFields() {
        List<ClassNode> candidates = new ArrayList<ClassNode>();
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode candidate = readClass(entry.getValue());
            if (itemClass.name.equals(candidate.superName)) {
                candidates.add(candidate);
            }
        }

        for (ClassNode candidate : candidates) {
            for (MethodNode method : candidate.methods) {
                if (!"<init>".equals(method.name)) {
                    continue;
                }
                String type = assignedIntField(method, 9, -1);
                if (type == null) {
                    continue;
                }
                String id = assignedIntField(method, Integer.MIN_VALUE, 1);
                String grade = assignedIntField(method, Integer.MIN_VALUE, 4);
                String itemClass = assignedIntField(method, Integer.MIN_VALUE, 5);
                if (id != null && grade != null && itemClass != null
                        && !id.equals(grade) && !id.equals(itemClass) && !grade.equals(itemClass)
                        && !type.equals(id) && !type.equals(grade) && !type.equals(itemClass)) {
                    itemTypeField = type;
                    itemIdField = id;
                    itemGradeField = grade;
                    itemClassField = itemClass;
                    return;
                }
            }
        }
        throw new IllegalStateException("Cannot identify item id/type/grade/class fields from pet item constructor");
    }

    private static String assignedIntField(MethodNode method, int constant, int local) {
        String found = null;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != PUTFIELD) {
                continue;
            }
            FieldInsnNode field = (FieldInsnNode) insn;
            if (!itemClass.name.equals(field.owner) || !"I".equals(field.desc)) {
                continue;
            }
            AbstractInsnNode value = previousReal(insn);
            boolean match = local >= 0
                    ? value != null && value.getOpcode() == ILOAD && ((org.objectweb.asm.tree.VarInsnNode) value).var == local
                    : isInt(value, constant);
            if (match) {
                if (found != null && !found.equals(field.name)) {
                    return null;
                }
                found = field.name;
            }
        }
        return found;
    }

    private static void discoverNetwork() {
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode candidate = readClass(entry.getValue());
            MethodNode feed = null;
            MethodNode interact = null;
            for (MethodNode method : candidate.methods) {
                if ((method.access & ACC_STATIC) == 0 && "(SSBB)V".equals(method.desc) && containsInt(method, 45)) {
                    feed = method;
                }
                if ((method.access & ACC_STATIC) == 0 && "(BS)V".equals(method.desc) && containsInt(method, 44)) {
                    interact = method;
                }
            }
            if (feed == null || interact == null) {
                continue;
            }
            for (MethodNode method : candidate.methods) {
                if ((method.access & ACC_STATIC) != 0 && ("()L" + candidate.name + ";").equals(method.desc)) {
                    MethodNode chat = null;
                    for (MethodNode candidateMethod : candidate.methods) {
                        if ((candidateMethod.access & ACC_STATIC) == 0
                                && "(Ljava/lang/String;)V".equals(candidateMethod.desc)
                                && containsInt(candidateMethod, 27)) {
                            require(chat == null, "Multiple opcode 27 chat senders found in " + candidate.name);
                            chat = candidateMethod;
                        }
                    }
                    require(chat != null, "Cannot locate opcode 27 chat sender in " + candidate.name);
                    networkClass = candidate;
                    networkFeedMethod = feed.name;
                    networkInteractMethod = interact.name;
                    networkSingletonMethod = method.name;
                    networkChatMethod = chat.name;
                    return;
                }
            }
        }
        throw new IllegalStateException("Cannot locate opcode 44/45 network sender");
    }

    private static void discoverMidlet() {
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode candidate = readClass(entry.getValue());
            if ("javax/microedition/midlet/MIDlet".equals(candidate.superName)
                    && findMethod(candidate, "startApp", "()V") != null) {
                midletClass = candidate;
                return;
            }
        }
        throw new IllegalStateException("Cannot locate MIDlet.startApp");
    }

    private static void discoverDialogs() {
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode candidate = readClass(entry.getValue());
            if (containsString(candidate, "listPoint")) {
                canvasClass = candidate;
            }
            if (containsString(candidate, "Player vec7")) {
                playerClass = candidate;
            }
        }
        require(canvasClass != null, "Cannot locate GameCanvas for dialog dismissal");
        require(playerClass != null, "Cannot locate Player for keyboard mapping");

        int firstInstanceObject = -1;
        for (int i = 0; i < canvasClass.fields.size(); i++) {
            FieldNode field = canvasClass.fields.get(i);
            if ((field.access & ACC_STATIC) == 0 && field.desc.startsWith("L")) {
                firstInstanceObject = i;
                break;
            }
        }
        require(firstInstanceObject >= 0, "Cannot locate GameCanvas instance renderer field");

        List<FieldNode> followingStaticObjects = new ArrayList<FieldNode>();
        for (int i = firstInstanceObject + 1; i < canvasClass.fields.size() && followingStaticObjects.size() < 5; i++) {
            FieldNode field = canvasClass.fields.get(i);
            if ((field.access & ACC_STATIC) != 0 && field.desc.startsWith("L")) {
                followingStaticObjects.add(field);
            }
        }
        require(followingStaticObjects.size() == 5, "Cannot map GameCanvas dialog field positions");
        FieldNode first = followingStaticObjects.get(3);
        FieldNode second = followingStaticObjects.get(4);
        require(first.desc.equals(second.desc), "Mapped GameCanvas dialog fields have different types");
        firstDialogField = first.name;
        secondDialogField = second.name;
        dialogDesc = first.desc;

        MethodNode hotbarInput = null;
        for (MethodNode method : playerClass.methods) {
            if (containsInts(method, 21, 23, 25, 27, 29)) {
                hotbarInput = method;
                break;
            }
        }
        require(hotbarInput != null, "Cannot locate Player hotbar input method");

        for (AbstractInsnNode insn = hotbarInput.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == GETSTATIC
                    && "[Z".equals(((FieldInsnNode) insn).desc)
                    && isInt(nextReal(insn), 21)) {
                FieldInsnNode field = (FieldInsnNode) insn;
                require(canvasClass.name.equals(field.owner), "Hotbar key-state owner is not GameCanvas");
                keyStateField = field.name;
                break;
            }
        }
        require(keyStateField != null, "Cannot map GameCanvas key-state array");

        MethodNode press = null;
        for (MethodNode method : canvasClass.methods) {
            if ("(I)V".equals(method.desc)
                    && containsInts(method, 114, 116, 121, 117, 105)
                    && containsField(method, canvasClass.name, keyStateField, "[Z")) {
                press = method;
                break;
            }
        }
        require(press != null, "Cannot locate GameCanvas keyPressed handler");

        Set<String> candidates = new HashSet<String>();
        for (AbstractInsnNode insn = press.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (canvasClass.name.equals(field.owner) && "[Z".equals(field.desc)) {
                    candidates.add(field.name);
                }
            }
        }
        candidates.remove(keyStateField);
        for (String candidate : candidates) {
            for (MethodNode method : canvasClass.methods) {
                if (method != press && "(I)V".equals(method.desc)
                        && containsField(method, canvasClass.name, candidate, "[Z")
                        && containsSwitch(method)) {
                    keyHeldField = candidate;
                    break;
                }
            }
            if (keyHeldField != null) break;
        }
        require(keyHeldField != null, "Cannot map GameCanvas held-key array");

        for (MethodNode method : canvasClass.methods) {
            if ((method.access & ACC_STATIC) != 0 && "(Ljava/lang/String;)V".equals(method.desc)
                    && containsString(method, "Ok")
                    && containsField(method, canvasClass.name, firstDialogField, dialogDesc)) {
                require(showMessageMethod == null, "Multiple GameCanvas message methods found");
                showMessageMethod = method.name;
            }
        }
        require(showMessageMethod != null, "Cannot map GameCanvas message method");
    }

    private static void discoverAutoCombat() {
        targetBaseClass = playerClass.superName;
        require(targetBaseClass != null, "Player controller has no target superclass");

        for (MethodNode method : playerClass.methods) {
            if ((method.access & ACC_STATIC) == 0
                    && ("(L" + targetBaseClass + ";)Z").equals(method.desc)) {
                require(hostilityMethod == null, "Multiple player-hostility methods found");
                hostilityMethod = method.name;
            }
        }
        require(hostilityMethod != null, "Cannot map player-hostility method");

        for (MethodNode method : playerClass.methods) {
            if ((method.access & ACC_STATIC) != 0 || !"()V".equals(method.desc) || !containsRangeScale(method)) {
                continue;
            }
            FieldInsnNode typeCheck = findMonsterOnlyTypeCheck(method);
            if (typeCheck != null) {
                require(autoTargetMethod == null, "Multiple auto-target scan methods found");
                autoTargetMethod = method;
                targetTypeField = typeCheck.name;
            }
        }
        require(autoTargetMethod != null && targetTypeField != null, "Cannot map monster-only auto-target scan");
    }

    private static boolean containsRangeScale(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == GETFIELD) {
                FieldInsnNode field = (FieldInsnNode) insn;
                AbstractInsnNode a = nextReal(insn);
                AbstractInsnNode b = nextReal(a);
                AbstractInsnNode c = nextReal(b);
                AbstractInsnNode d = nextReal(c);
                if (targetBaseClass.equals(field.owner) && "I".equals(field.desc)
                        && isInt(a, 3) && b != null && b.getOpcode() == IMUL
                        && isInt(c, 2) && d != null && d.getOpcode() == IDIV) return true;
            }
        }
        return false;
    }

    private static FieldInsnNode findMonsterOnlyTypeCheck(MethodNode method) {
        FieldInsnNode found = null;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != GETFIELD) continue;
            FieldInsnNode field = (FieldInsnNode) insn;
            AbstractInsnNode one = nextReal(insn);
            AbstractInsnNode jump = nextReal(one);
            AbstractInsnNode targetLoad = previousReal(insn);
            AbstractInsnNode afterJump = nextReal(jump);
            AbstractInsnNode targetStateCall = nextReal(afterJump);
            if (targetBaseClass.equals(field.owner) && "B".equals(field.desc)
                    && isInt(one, 1) && jump instanceof JumpInsnNode
                    && jump.getOpcode() == IF_ICMPNE
                    && targetLoad instanceof VarInsnNode && targetLoad.getOpcode() == ALOAD
                    && afterJump instanceof VarInsnNode && afterJump.getOpcode() == ALOAD
                    && ((VarInsnNode) targetLoad).var == ((VarInsnNode) afterJump).var
                    && targetStateCall instanceof MethodInsnNode
                    && targetStateCall.getOpcode() == INVOKEVIRTUAL
                    && targetBaseClass.equals(((MethodInsnNode) targetStateCall).owner)
                    && "()Z".equals(((MethodInsnNode) targetStateCall).desc)) {
                require(found == null, "Multiple monster-type checks in auto-target scan");
                found = field;
            }
        }
        return found;
    }

    private static void patchAutoCombat() {
        patchAutoTargetScan();
        patchPlayerCombatLoopChecks();
        saveClass(playerClass);
    }

    private static void patchAutoTargetScan() {
        FieldInsnNode field = findMonsterOnlyTypeCheck(autoTargetMethod);
        require(field != null, "Cannot reopen auto-target type check");
        AbstractInsnNode targetLoad = previousReal(field);
        AbstractInsnNode one = nextReal(field);
        JumpInsnNode rejectJump = (JumpInsnNode) nextReal(one);
        require(targetLoad instanceof VarInsnNode && targetLoad.getOpcode() == ALOAD,
                "Auto-target candidate is not held in a local variable");
        int targetVar = ((VarInsnNode) targetLoad).var;

        LabelNode accepted = new LabelNode();
        InsnList replacement = new InsnList();
        replacement.add(new VarInsnNode(ALOAD, targetVar));
        replacement.add(new FieldInsnNode(GETFIELD, targetBaseClass, targetTypeField, "B"));
        replacement.add(new InsnNode(ICONST_1));
        replacement.add(new JumpInsnNode(IF_ICMPEQ, accepted));
        replacement.add(new VarInsnNode(ALOAD, targetVar));
        replacement.add(new FieldInsnNode(GETFIELD, targetBaseClass, targetTypeField, "B"));
        replacement.add(new JumpInsnNode(IFNE, rejectJump.label));
        replacement.add(new MethodInsnNode(INVOKESTATIC, SUPPORT_CLASS, "isAutoPlayersEnabled", "()Z", false));
        replacement.add(new JumpInsnNode(IFEQ, rejectJump.label));
        replacement.add(new VarInsnNode(ALOAD, 0));
        replacement.add(new VarInsnNode(ALOAD, targetVar));
        replacement.add(new MethodInsnNode(INVOKEVIRTUAL, playerClass.name, hostilityMethod,
                "(L" + targetBaseClass + ";)Z", false));
        replacement.add(new JumpInsnNode(IFEQ, rejectJump.label));
        replacement.add(accepted);

        autoTargetMethod.instructions.insertBefore(targetLoad, replacement);
        autoTargetMethod.instructions.remove(targetLoad);
        autoTargetMethod.instructions.remove(field);
        autoTargetMethod.instructions.remove(one);
        autoTargetMethod.instructions.remove(rejectJump);
        autoTargetPatches++;
    }

    private static void patchPlayerCombatLoopChecks() {
        for (MethodNode method : playerClass.methods) {
            if (method == autoTargetMethod) continue;
            List<FieldInsnNode> candidates = new ArrayList<FieldInsnNode>();
            boolean hasLocalResetCheck = false;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof FieldInsnNode && insn.getOpcode() == GETFIELD) {
                    FieldInsnNode field = (FieldInsnNode) insn;
                    AbstractInsnNode one = nextReal(field);
                    AbstractInsnNode jump = nextReal(one);
                    if (targetBaseClass.equals(field.owner) && targetTypeField.equals(field.name)
                            && "B".equals(field.desc) && isInt(one, 1)
                            && jump instanceof JumpInsnNode && jump.getOpcode() == IF_ICMPNE
                            && isCombatLoopComparison((JumpInsnNode) jump)) {
                        candidates.add(field);
                        if (isLocalBooleanFallthrough((JumpInsnNode) jump)) hasLocalResetCheck = true;
                    }
                }
            }
            if (!hasLocalResetCheck) continue;
            for (FieldInsnNode field : candidates) {
                AbstractInsnNode one = nextReal(field);
                JumpInsnNode oldJump = (JumpInsnNode) nextReal(one);
                method.instructions.set(one,
                        new MethodInsnNode(INVOKESTATIC, SUPPORT_CLASS, "isAutoTargetType", "(I)Z", false));
                method.instructions.set(oldJump, new JumpInsnNode(IFEQ, oldJump.label));
                playerLoopPatches++;
            }
        }
    }

    private static boolean isCombatLoopComparison(JumpInsnNode jump) {
        AbstractInsnNode first = nextReal(jump);
        AbstractInsnNode second = nextReal(first);
        AbstractInsnNode third = nextReal(second);
        if (first instanceof VarInsnNode && first.getOpcode() == ALOAD
                && second instanceof FieldInsnNode && second.getOpcode() == GETFIELD) {
            FieldInsnNode field = (FieldInsnNode) second;
            if (targetBaseClass.equals(field.owner) && "Z".equals(field.desc)) return true;
        }
        if (first instanceof FieldInsnNode && first.getOpcode() == GETSTATIC
                && second instanceof FieldInsnNode && second.getOpcode() == GETFIELD) {
            FieldInsnNode field = (FieldInsnNode) second;
            return targetBaseClass.equals(field.owner) && "B".equals(field.desc)
                    && !targetTypeField.equals(field.name) && isInt(third, 2);
        }
        return false;
    }

    private static boolean isLocalBooleanFallthrough(JumpInsnNode jump) {
        AbstractInsnNode first = nextReal(jump);
        AbstractInsnNode second = nextReal(first);
        return first instanceof VarInsnNode && first.getOpcode() == ALOAD
                && second instanceof FieldInsnNode && "Z".equals(((FieldInsnNode) second).desc);
    }

    private static byte[] remapSupport(byte[] templateBytes) {
        ClassNode support = readClass(templateBytes);
        support.version = V1_6;
        for (MethodNode method : support.methods) {
            method.desc = remapDesc(method.desc);
            if (method.localVariables != null) {
                for (org.objectweb.asm.tree.LocalVariableNode local : method.localVariables) {
                    local.desc = remapDesc(local.desc);
                    if (local.signature != null) {
                        local.signature = remapDesc(local.signature);
                    }
                }
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) insn;
                    if ("bw".equals(field.owner)) {
                        field.owner = itemClass.name;
                        if ("V".equals(field.name)) field.name = inventoryField;
                        else if ("X".equals(field.name)) field.name = petContainerField;
                        else if ("O".equals(field.name)) field.name = itemIdField;
                        else if ("u".equals(field.name)) field.name = itemTypeField;
                        else if ("N".equals(field.name)) field.name = itemGradeField;
                        else if ("M".equals(field.name)) field.name = itemClassField;
                    } else if ("fu".equals(field.owner)) {
                        field.owner = canvasClass.name;
                        if ("s".equals(field.name)) {
                            field.name = firstDialogField;
                            field.desc = dialogDesc;
                        } else if ("t".equals(field.name)) {
                            field.name = secondDialogField;
                            field.desc = dialogDesc;
                        } else if ("am".equals(field.name)) {
                            field.name = keyHeldField;
                            field.desc = "[Z";
                        } else if ("al".equals(field.name)) {
                            field.name = keyStateField;
                            field.desc = "[Z";
                        }
                        continue;
                    }
                    field.desc = remapDesc(field.desc);
                } else if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if ("et".equals(call.owner)) {
                        call.owner = vectorClass.name;
                        if ("c".equals(call.name) && "()I".equals(call.desc)) call.name = vectorSizeMethod;
                        else if ("a".equals(call.name) && "(I)Ljava/lang/Object;".equals(call.desc)) call.name = vectorGetMethod;
                    } else if ("q".equals(call.owner)) {
                        call.owner = networkClass.name;
                        if ("a".equals(call.name) && "()Lq;".equals(call.desc)) call.name = networkSingletonMethod;
                        else if ("c".equals(call.name) && "(BS)V".equals(call.desc)) call.name = networkInteractMethod;
                        else if ("a".equals(call.name) && "(SSBB)V".equals(call.desc)) call.name = networkFeedMethod;
                    } else if ("fu".equals(call.owner) && "a".equals(call.name)
                            && "(Ljava/lang/String;)V".equals(call.desc)) {
                        call.owner = canvasClass.name;
                        call.name = showMessageMethod;
                    }
                    call.desc = remapDesc(call.desc);
                } else if (insn instanceof TypeInsnNode) {
                    TypeInsnNode type = (TypeInsnNode) insn;
                    type.desc = remapInternal(type.desc);
                } else if (insn instanceof FrameNode) {
                    remapFrame((FrameNode) insn);
                }
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        support.accept(writer);
        return writer.toByteArray();
    }

    private static void remapFrame(FrameNode frame) {
        remapFrameEntries(frame.local);
        remapFrameEntries(frame.stack);
    }

    private static void remapFrameEntries(List<Object> values) {
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof String) {
                values.set(i, remapInternal((String) value));
            }
        }
    }

    private static String remapDesc(String desc) {
        if (desc == null) return null;
        return desc.replace("Lbw;", "L" + itemClass.name + ";")
                .replace("Let;", "L" + vectorClass.name + ";")
                .replace("Lq;", "L" + networkClass.name + ";");
    }

    private static String remapInternal(String name) {
        if (name != null && name.startsWith("[")) return remapDesc(name);
        if ("bw".equals(name)) return itemClass.name;
        if ("et".equals(name)) return vectorClass.name;
        if ("q".equals(name)) return networkClass.name;
        return name;
    }

    private static void validateMidletStart() {
        MethodNode start = findMethod(midletClass, "startApp", "()V");
        for (AbstractInsnNode insn = start.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == INVOKESTATIC && SUPPORT_CLASS.equals(call.owner)
                        && "start".equals(call.name) && "()V".equals(call.desc)) {
                    startPatches++;
                }
            }
        }
    }

    private static void patchChatCommand() {
        MethodNode chat = findMethod(networkClass, networkChatMethod, "(Ljava/lang/String;)V");
        require(chat != null, "Cannot reopen opcode 27 chat sender");

        for (AbstractInsnNode insn = chat.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == INVOKESTATIC && SUPPORT_CLASS.equals(call.owner)
                        && "handleChat".equals(call.name) && "(Ljava/lang/String;)Z".equals(call.desc)) {
                    chatPatches++;
                    return;
                }
            }
        }

        LabelNode sendNormally = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, 1));
        hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT_CLASS, "handleChat", "(Ljava/lang/String;)Z", false));
        hook.add(new JumpInsnNode(IFEQ, sendNormally));
        hook.add(new InsnNode(RETURN));
        hook.add(sendNormally);
        chat.instructions.insert(hook);
        chatPatches++;
        saveClass(networkClass);
    }

    private static void validateSupport(byte[] supportBytes) {
        ClassNode support = readClass(supportBytes);
        require(support.version == V1_6, "Support class must use Java 6 bytecode");
        MethodNode chatHandler = findMethod(support, "handleChat", "(Ljava/lang/String;)Z");
        require(chatHandler != null && (chatHandler.access & ACC_STATIC) != 0,
                "Support lacks static chat grade handler");
        MethodNode autoPlayers = findMethod(support, "isAutoPlayersEnabled", "()Z");
        MethodNode autoType = findMethod(support, "isAutoTargetType", "(I)Z");
        require(autoPlayers != null && autoType != null
                        && (autoPlayers.access & ACC_STATIC) != 0 && (autoType.access & ACC_STATIC) != 0,
                "Support lacks auto-combat command state");
        int inventoryRefs = 0;
        int petRefs = 0;
        int itemClassRefs = 0;
        int feedCalls = 0;
        int interactCalls = 0;
        int dialogClears = 0;
        int keyArrayRefs = 0;
        int showMessageCalls = 0;
        for (MethodNode method : support.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) insn;
                    require(!"bw".equals(field.owner), "Unmapped bw field reference remains");
                    if (itemClass.name.equals(field.owner) && inventoryField.equals(field.name)) inventoryRefs++;
                    if (itemClass.name.equals(field.owner) && petContainerField.equals(field.name)) petRefs++;
                    if (itemClass.name.equals(field.owner) && itemClassField.equals(field.name)
                            && field.getOpcode() == GETFIELD) itemClassRefs++;
                    if (canvasClass.name.equals(field.owner)
                            && (firstDialogField.equals(field.name) || secondDialogField.equals(field.name))
                            && field.getOpcode() == PUTSTATIC) dialogClears++;
                    if (canvasClass.name.equals(field.owner)
                            && (keyHeldField.equals(field.name) || keyStateField.equals(field.name))
                            && "[Z".equals(field.desc) && field.getOpcode() == GETSTATIC) keyArrayRefs++;
                } else if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    require(!"et".equals(call.owner) && !"q".equals(call.owner), "Unmapped method owner remains");
                    if (networkClass.name.equals(call.owner) && networkFeedMethod.equals(call.name) && "(SSBB)V".equals(call.desc)) feedCalls++;
                    if (networkClass.name.equals(call.owner) && networkInteractMethod.equals(call.name) && "(BS)V".equals(call.desc)) interactCalls++;
                    if (canvasClass.name.equals(call.owner) && showMessageMethod.equals(call.name)
                            && "(Ljava/lang/String;)V".equals(call.desc)) showMessageCalls++;
                } else if (insn instanceof FrameNode) {
                    require(!frameContainsLegacyType((FrameNode) insn), "Unmapped bw/et/q frame type remains");
                }
            }
        }
        require(inventoryRefs == 1 && petRefs == 1, "Support inventory mappings are incomplete");
        require(itemClassRefs == 1, "Support item-class mapping is incomplete: " + itemClassRefs);
        require(feedCalls == 1 && interactCalls == 1, "Support opcode 44/45 mappings are incomplete");
        require(dialogClears == 0, "Support must dismiss dialogs through a key press, not direct field clearing");
        require(keyArrayRefs == 4, "Support Enter-key press/release mappings are incomplete: " + keyArrayRefs);
        require(showMessageCalls == 1, "Support help dialog mapping is incomplete: " + showMessageCalls);
    }

    private static boolean frameContainsLegacyType(FrameNode frame) {
        return frameValuesContainLegacyType(frame.local) || frameValuesContainLegacyType(frame.stack);
    }

    private static boolean frameValuesContainLegacyType(List<Object> values) {
        if (values == null) return false;
        for (Object value : values) {
            if (value instanceof String) {
                String type = (String) value;
                if ("bw".equals(type) || "et".equals(type) || "q".equals(type)
                        || type.indexOf("Lbw;") >= 0 || type.indexOf("Let;") >= 0 || type.indexOf("Lq;") >= 0) return true;
            }
        }
        return false;
    }

    private static void writeJar(File output) throws IOException {
        File temp = new File(output.getPath() + ".tmp");
        JarOutputStream out = new JarOutputStream(new FileOutputStream(temp));
        try {
            for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
                String name = entry.getKey();
                if (name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"))) {
                    continue;
                }
                JarEntry jarEntry = new JarEntry(name);
                Long time = TIMES.get(name);
                if (time != null && time.longValue() >= 0L) jarEntry.setTime(time.longValue());
                out.putNextEntry(jarEntry);
                out.write(entry.getValue());
                out.closeEntry();
            }
        } finally {
            out.close();
        }
        Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void verifyOutput(File output) throws Exception {
        JarFile jar = new JarFile(output);
        try {
            require(jar.getEntry("META-INF/MANIFEST.MF") != null, "Output JAR lost its manifest");
            require(jar.getEntry(SUPPORT_CLASS + ".class") != null, "Output JAR lacks pet-feed support");
            require(jar.size() == ENTRIES.size(), "Output JAR unexpectedly changed entry count");
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

        URL[] urls = new URL[]{output.toURI().toURL(), new File("microemulator.jar").toURI().toURL()};
        URLClassLoader loader = new URLClassLoader(urls, HSOSKYPetFeedPatcher.class.getClassLoader());
        try {
            Class<?> supportType = Class.forName(SUPPORT_CLASS, true, loader);
            supportType.getDeclaredMethods();
            java.lang.reflect.Method handleChat = supportType.getMethod("handleChat", String.class);
            require(Boolean.TRUE.equals(handleChat.invoke(null, "4")), "Chat command 4 was not consumed");
            require(Boolean.TRUE.equals(handleChat.invoke(null, "5")), "Chat command 5 was not consumed");
            require(Boolean.TRUE.equals(handleChat.invoke(null, "6")), "Chat command 6 was not consumed");
            java.lang.reflect.Method isAutoTargetType = supportType.getMethod("isAutoTargetType", Integer.TYPE);
            require(Boolean.TRUE.equals(isAutoTargetType.invoke(null, Integer.valueOf(0))),
                    "Command 6 did not enable player targets");
            require(Boolean.TRUE.equals(handleChat.invoke(null, "3")), "Chat command 3 was not consumed");
            require(Boolean.TRUE.equals(handleChat.invoke(null, "7")), "Chat command 7 was not consumed");
            require(Boolean.FALSE.equals(isAutoTargetType.invoke(null, Integer.valueOf(0))),
                    "Command 7 did not disable player targets");
            require(Boolean.TRUE.equals(isAutoTargetType.invoke(null, Integer.valueOf(1))),
                    "Monster targets must remain enabled");
            require(Boolean.FALSE.equals(handleChat.invoke(null, "hello")), "Normal chat was unexpectedly consumed");
            Class.forName(midletClass.name.replace('/', '.'), false, loader);
            Class.forName(itemClass.name.replace('/', '.'), false, loader);
            Class.forName(vectorClass.name.replace('/', '.'), false, loader);
            Class.forName(networkClass.name.replace('/', '.'), false, loader);
            Class.forName(canvasClass.name.replace('/', '.'), false, loader);
            Class.forName(playerClass.name.replace('/', '.'), false, loader);
        } finally {
            loader.close();
        }
    }

    private static void saveClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        ENTRIES.put(node.name + ".class", writer.toByteArray());
    }

    private static MethodNode findMethod(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        return null;
    }

    private static boolean calls(MethodNode method, String owner, String name, String desc) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) return true;
            }
        }
        return false;
    }

    private static boolean containsInt(MethodNode method, int expected) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (isInt(insn, expected)) return true;
        }
        return false;
    }

    private static boolean containsInts(MethodNode method, int... expected) {
        Set<Integer> found = new HashSet<Integer>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            for (int value : expected) {
                if (isInt(insn, value)) found.add(Integer.valueOf(value));
            }
        }
        return found.size() == expected.length;
    }

    private static boolean containsField(MethodNode method, String owner, String name, String desc) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (owner.equals(field.owner) && name.equals(field.name) && desc.equals(field.desc)) return true;
            }
        }
        return false;
    }

    private static boolean containsSwitch(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == TABLESWITCH || insn.getOpcode() == LOOKUPSWITCH) return true;
        }
        return false;
    }

    private static boolean containsString(ClassNode owner, String expectedPart) {
        for (MethodNode method : owner.methods) {
            if (containsString(method, expectedPart)) return true;
        }
        return false;
    }

    private static boolean containsString(MethodNode method, String expectedPart) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String
                    && ((String) ((LdcInsnNode) insn).cst).indexOf(expectedPart) >= 0) return true;
        }
        return false;
    }

    private static boolean isInt(AbstractInsnNode insn, int expected) {
        if (insn == null) return false;
        int opcode = insn.getOpcode();
        if (opcode >= ICONST_M1 && opcode <= ICONST_5) return opcode - ICONST_0 == expected;
        if (insn instanceof org.objectweb.asm.tree.IntInsnNode && (opcode == BIPUSH || opcode == SIPUSH)) {
            return ((org.objectweb.asm.tree.IntInsnNode) insn).operand == expected;
        }
        return insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer
                && ((Integer) ((LdcInsnNode) insn).cst).intValue() == expected;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn == null ? null : insn.getNext();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getNext();
        return cursor;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn == null ? null : insn.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getPrevious();
        return cursor;
    }

    private static ClassNode readClass(byte[] bytes) {
        if (bytes == null) return null;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static String shortName(String value) {
        if (value == null || value.length() <= 18) return value;
        return value.substring(0, 8) + "..." + value.substring(value.length() - 7);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class MarkerField {
        final String name;
        final String desc;

        MarkerField(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }
    }
}
