import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

public final class HSOSKYAutoEnhancePatcher implements Opcodes {
    private static final File INPUT = new File("hsosky_mod.jar");
    private static final File OUTPUT = new File("HSOSKY_MOD_AUTO_ENHANCE_GOLD_SERVER_GATED_NOFREEZE_V8.jar");
    private static final String SUPPORT = "HSOSKYAutoEnhanceSupport";
    private static final String SUPPORT_ENTRY = SUPPORT + ".class";

    /** Debug string left in the enhancement result packet branch by the game authors. */
    private static final String RESULT_MARKER = "thanh cong hay that bai=";

    private static final String LEVEL_OWNER =
            "IIIlIlIIllIIlIIllIllIlllIlIIlllIllIIIllIIlIIllIlllIlIIlIIllIllIIIlIIllIllIIllIlIlIIllIIlllIIllIllIIlIllIIllIlllIIIlllIIllIIlllIIlllIIlIlllIlllIlllIIIllIllIIllIllIlIlllIIllIIlllIIlIIllIIllIIlIlllIIlIIIllIIllIIllIIlIllIll";
    private static final String LEVEL_FIELD =
            "IlIllIIlllIIlllIIlllIIIllIIIllIIlllIlIIllIllIIIllIlIIlllIlIIlIIllIllIlIIlIllIIIllIIllIIIllIllIlIIIlllIIIlIIlllIIlIIIlllIIIllIIIllIIlllIIllIIlIIIllIIllIIlllIIllIlIllIIllIIllIllIIIllIIllIIlIIIlIIlllIIIlIIlllIIlIIIllIlIlllIlIllIIlIIIll";
    private static final String RECEIVER_HINT =
            "IIIllIIllIIllIIIlIlIlllIIllIIllIIlIIlllIIIllIllIllIIlIllIIllIIIllIIIlllIlIIIlllIllIllIIIlIIlIIllIIIllIIlllIllIIllIIIllIIIllIlIllIlllIlIIIlllIllIIIlllIIIlIIlllIIllIIlllIIlllIIIllIllIIlllIIllIIllIllIlllIIllIIllIIllIIlllIIllIllIllIllIllIIl";

    private static final Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
    private static final Map<String, Long> times = new LinkedHashMap<String, Long>();

    private static String screenName;
    private static String vectorField;
    private static String vectorDesc;
    private static String vectorOwner;
    private static String vectorClear;
    private static String vectorAdd;
    private static String commandOwner;
    private static String commandCtorDesc;
    private static String callbackName;
    private static String handlerName;
    private static String rebuildName;
    private static String selectedField;
    private static String selectedDesc;
    private static String animationField;
    private static String enhanceStageField;
    private static String enhanceBusyField;
    private static String resultTimerField;
    private static String enhanceUpdateName;
    private static String resultInitName;
    private static int resultInitOpcode;
    private static String inventoryLookupName;
    private static int inventoryLookupOpcode;
    private static String inventoryLookupDesc;
    private static String inventoryIdOwner;
    private static String inventoryIdField;
    private static String inventoryKeyOwner;
    private static String inventoryKeyField;
    private static String resultPopupWrapperDesc;
    private static String receiverName;
    private static String netName;
    private static String singletonOwner;
    private static String singletonName;
    private static String singletonDesc;
    private static int singletonOpcode;
    private static String sendOwner;
    private static String sendName;
    private static int sendOpcode;
    private static String popupVectorOwner;
    private static String popupVectorCtorDesc;
    private static int popupVectorLocal;
    private static String popupCommandCtorDesc;
    private static String popupVectorAdd;
    private static String popupOpenOwner;
    private static String popupOpenName;
    private static String popupOpenDesc;
    private static int popupOpenOpcode;
    private static String popupOpenReceiverFieldOwner;
    private static String popupOpenReceiverFieldName;
    private static String popupOpenReceiverFieldDesc;
    private static String popupCallbackFieldOwner;
    private static String popupCallbackFieldName;
    private static String popupCallbackFieldDesc;
    private static String popupCloseFieldOwner;
    private static String popupCloseFieldName;
    private static String popupCloseFieldDesc;
    private static String popupCloseOwner;
    private static String popupCloseName;
    private static int popupCloseOpcode;

    private HSOSKYAutoEnhancePatcher() {
    }

    public static void main(String[] args) throws Exception {
        require(INPUT.isFile(), "Missing checkpoint: " + INPUT.getAbsolutePath());
        require(!OUTPUT.exists(), "Refusing to overwrite existing output: " + OUTPUT.getAbsolutePath());
        require(new File(SUPPORT_ENTRY).isFile(), "Compile " + SUPPORT_ENTRY + " first");

        readJar(INPUT);
        Map<String, byte[]> original = new LinkedHashMap<String, byte[]>(entries);
        ClassNode screen = findScreen();
        discoverScreen(screen);
        ClassNode receiver = findReceiver();
        discoverEnhancementState(screen);
        validateLevelField();

        ClassNode net = findResponseClass();

        patchScreen(screen);
        patchResponseSite(net);
        save(screen);
        save(net);
        entries.put(SUPPORT_ENTRY, Files.readAllBytes(new File(SUPPORT_ENTRY).toPath()));
        times.put(SUPPORT_ENTRY, Long.valueOf(System.currentTimeMillis()));

        File temporary = new File(OUTPUT.getPath() + ".tmp");
        require(!temporary.exists(), "Temporary output already exists: " + temporary.getAbsolutePath());
        try {
            writeJar(temporary);
            verify(temporary, original);
            Files.move(temporary.toPath(), OUTPUT.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            if (temporary.exists()) Files.delete(temporary.toPath());
        }

        System.out.println("Built: " + OUTPUT.getAbsolutePath());
        System.out.println("Screen: " + screenName);
        System.out.println("Receiver: " + receiverName);
        System.out.println("Result packet: " + netName);
        System.out.println("Every request waits for the real server result packet and always uses gold (payment byte 0).");
    }

    private static ClassNode findScreen() {
        ClassNode found = null;
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode node = readClass(entry.getValue());
            if (!containsString(node, "TabRebuildItem vecListCmd")) continue;
            require(found == null, "Ambiguous enhancement screen");
            found = node;
        }
        require(found != null, "Enhancement screen marker was not found");
        screenName = found.name;
        return found;
    }

    private static void discoverScreen(ClassNode screen) {
        discoverVector(screen);
        Map<String, Integer> objectVectorCalls = new LinkedHashMap<String, Integer>();
        for (MethodNode method : screen.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (vectorOwner.equals(call.owner) && "(Ljava/lang/Object;)V".equals(call.desc)) {
                    Integer old = objectVectorCalls.get(call.name);
                    objectVectorCalls.put(call.name, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
                }
            }
        }
        int bestObjectCalls = 0;
        for (Map.Entry<String, Integer> candidate : objectVectorCalls.entrySet()) {
            if (candidate.getValue().intValue() > bestObjectCalls) {
                vectorAdd = candidate.getKey();
                bestObjectCalls = candidate.getValue().intValue();
            }
        }
        require(vectorAdd != null && bestObjectCalls >= 2, "Could not identify vector add(Object)");
        MethodNode rebuild = null;
        int bestAdds = -1;
        for (MethodNode method : screen.methods) {
            int adds = callCount(method, vectorOwner, vectorAdd, "(Ljava/lang/Object;)V");
            if ("()V".equals(method.desc) && !"<init>".equals(method.name) && adds > bestAdds) {
                rebuild = method;
                bestAdds = adds;
            }
        }
        require(rebuild != null && bestAdds >= 2, "Could not identify enhancement menu rebuild method");
        rebuildName = rebuild.name;

        MethodNode handler = null;
        for (MethodNode method : screen.methods) {
            if (!"(II)V".equals(method.desc) || !hasSwitch(method)) continue;
            require(handler == null, "Ambiguous enhancement command handler");
            handler = method;
        }
        require(handler != null, "Could not identify enhancement command handler");
        handlerName = handler.name;

        discoverVectorMethods(rebuild);
        discoverCommand(screen);
        discoverNetwork(handler);
        discoverPopup(handler);
    }

    private static void discoverVector(ClassNode screen) {
        for (MethodNode method : screen.methods) {
            if (!"<init>".equals(method.name)) continue;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof LdcInsnNode)
                        || !"TabRebuildItem vecListCmd".equals(((LdcInsnNode) insn).cst)) continue;
                for (AbstractInsnNode cursor = insn.getNext(); cursor != null; cursor = cursor.getNext()) {
                    if (!(cursor instanceof FieldInsnNode) || cursor.getOpcode() != PUTFIELD) continue;
                    FieldInsnNode field = (FieldInsnNode) cursor;
                    if (!screen.name.equals(field.owner)) continue;
                    vectorField = field.name;
                    vectorDesc = field.desc;
                    vectorOwner = Type.getType(field.desc).getInternalName();
                    return;
                }
            }
        }
        throw new IllegalStateException("Could not identify enhancement command vector");
    }

    private static void discoverVectorMethods(MethodNode rebuild) {
        for (AbstractInsnNode insn = rebuild.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (!vectorOwner.equals(call.owner)) continue;
            if ("(Ljava/lang/Object;)V".equals(call.desc)) vectorAdd = call.name;
            if ("()V".equals(call.desc) && vectorClear == null) vectorClear = call.name;
        }
        require(vectorAdd != null, "Could not identify vector add(Object)");
        require(vectorClear != null, "Could not identify vector clear()");
    }

    private static void discoverCommand(ClassNode screen) {
        for (MethodNode method : screen.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode) || insn.getOpcode() != INVOKESPECIAL) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                Type[] arguments = Type.getArgumentTypes(call.desc);
                if (!"<init>".equals(call.name) || arguments.length != 3) continue;
                if (arguments[0].getSort() != Type.OBJECT
                        || !"java/lang/String".equals(arguments[0].getInternalName())
                        || arguments[1].getSort() != Type.INT
                        || arguments[2].getSort() != Type.OBJECT) continue;
                commandOwner = call.owner;
                commandCtorDesc = call.desc;
                callbackName = arguments[2].getInternalName();
                return;
            }
        }
        throw new IllegalStateException("Could not identify (String,int,callback) command constructor");
    }

    private static void discoverNetwork(MethodNode handler) {
        MethodInsnNode send = null;
        for (AbstractInsnNode insn = handler.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (!"(BSB)V".equals(call.desc)) continue;
            boolean hasTwo = false;
            boolean hasZero = false;
            for (AbstractInsnNode cursor = previousReal(insn), stop = previousRealN(insn, 10);
                    cursor != null && cursor != stop; cursor = previousReal(cursor)) {
                Integer value = integerValue(cursor);
                if (value != null && value.intValue() == 2) hasTwo = true;
                if (value != null && value.intValue() == 0) hasZero = true;
            }
            if (hasTwo && hasZero) {
                require(send == null, "Ambiguous gold/gem network request");
                send = call;
            }
        }
        require(send != null, "Could not identify enhancement network request (byte,short,byte)");
        sendOwner = send.owner;
        sendName = send.name;
        sendOpcode = send.getOpcode();

        for (AbstractInsnNode cursor = previousReal(send); cursor != null; cursor = previousReal(cursor)) {
            if (!(cursor instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) cursor;
            if (!("()L" + sendOwner + ";").equals(call.desc)) continue;
            singletonOwner = call.owner;
            singletonName = call.name;
            singletonDesc = call.desc;
            singletonOpcode = call.getOpcode();
            break;
        }
        require(singletonOwner != null, "Could not identify network singleton accessor");

        for (AbstractInsnNode cursor = previousReal(send); cursor != null; cursor = previousReal(cursor)) {
            if (!(cursor instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) cursor;
            if (!"()V".equals(call.desc) || call.getOpcode() == INVOKESTATIC) continue;
            AbstractInsnNode receiver = previousReal(cursor);
            if (!(receiver instanceof FieldInsnNode) || receiver.getOpcode() != GETSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode) receiver;
            popupCloseFieldOwner = field.owner;
            popupCloseFieldName = field.name;
            popupCloseFieldDesc = field.desc;
            popupCloseOwner = call.owner;
            popupCloseName = call.name;
            popupCloseOpcode = call.getOpcode();
            break;
        }
        require(popupCloseOwner != null, "Could not identify confirmation-popup close call");
    }

    private static void discoverPopup(MethodNode handler) {
        AbstractInsnNode marker = null;
        for (AbstractInsnNode insn = handler.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode && "TabRebuildItem menu".equals(((LdcInsnNode) insn).cst)) {
                marker = insn;
                break;
            }
        }
        require(marker != null, "Could not identify enhancement confirmation button vector");
        AbstractInsnNode creation = previousReal(previousReal(marker));
        require(creation instanceof TypeInsnNode && creation.getOpcode() == NEW,
                "Unexpected confirmation vector construction");
        popupVectorOwner = ((TypeInsnNode) creation).desc;
        AbstractInsnNode ctorInsn = nextReal(marker);
        require(ctorInsn instanceof MethodInsnNode && ctorInsn.getOpcode() == INVOKESPECIAL,
                "Unexpected confirmation vector constructor");
        MethodInsnNode vectorCtor = (MethodInsnNode) ctorInsn;
        require(popupVectorOwner.equals(vectorCtor.owner) && "<init>".equals(vectorCtor.name),
                "Unexpected confirmation vector constructor owner");
        popupVectorCtorDesc = vectorCtor.desc;
        AbstractInsnNode store = nextReal(ctorInsn);
        require(store instanceof VarInsnNode && store.getOpcode() == ASTORE,
                "Confirmation vector local was not found");
        popupVectorLocal = ((VarInsnNode) store).var;

        for (AbstractInsnNode insn = marker; insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            Type[] arguments = Type.getArgumentTypes(call.desc);
            if (popupCommandCtorDesc == null && call.getOpcode() == INVOKESPECIAL
                    && commandOwner.equals(call.owner) && "<init>".equals(call.name)
                    && arguments.length == 4
                    && arguments[0].getSort() == Type.OBJECT
                    && "java/lang/String".equals(arguments[0].getInternalName())
                    && arguments[1].getSort() == Type.INT
                    && arguments[2].getSort() == Type.INT
                    && arguments[3].getSort() == Type.OBJECT) {
                popupCommandCtorDesc = call.desc;
            }
            if (popupVectorOwner.equals(call.owner) && "(Ljava/lang/Object;)V".equals(call.desc)) {
                popupVectorAdd = call.name;
            }
            if (arguments.length == 6
                    && arguments[0].getSort() == Type.OBJECT
                    && popupVectorOwner.equals(arguments[0].getInternalName())
                    && arguments[1].getSort() == Type.OBJECT
                    && "java/lang/String".equals(arguments[1].getInternalName())) {
                popupOpenOwner = call.owner;
                popupOpenName = call.name;
                popupOpenDesc = call.desc;
                popupOpenOpcode = call.getOpcode();
                String callbackDesc = arguments[2].getDescriptor();
                for (AbstractInsnNode cursor = previousReal(insn), stop = previousRealN(insn, 30);
                        cursor != null && cursor != stop; cursor = previousReal(cursor)) {
                    if (!(cursor instanceof FieldInsnNode) || cursor.getOpcode() != GETSTATIC) continue;
                    FieldInsnNode field = (FieldInsnNode) cursor;
                    if (!callbackDesc.equals(field.desc) && popupOpenReceiverFieldOwner == null) {
                        popupOpenReceiverFieldOwner = field.owner;
                        popupOpenReceiverFieldName = field.name;
                        popupOpenReceiverFieldDesc = field.desc;
                    }
                    if (callbackDesc.equals(field.desc) && popupCallbackFieldOwner == null) {
                        popupCallbackFieldOwner = field.owner;
                        popupCallbackFieldName = field.name;
                        popupCallbackFieldDesc = field.desc;
                    }
                    if (popupCallbackFieldOwner != null && popupOpenReceiverFieldOwner != null) break;
                }
                break;
            }
        }
        require(popupCommandCtorDesc != null, "Could not identify confirmation button constructor");
        require(popupVectorAdd != null, "Could not identify confirmation popup vector add(Object)");
        require(popupOpenOwner != null && popupCallbackFieldOwner != null,
                "Could not identify confirmation popup opener/callback");
        require(popupOpenOpcode == INVOKESTATIC || popupOpenReceiverFieldOwner != null,
                "Could not identify confirmation popup receiver");
    }

    private static ClassNode findReceiver() {
        ClassNode found = null;
        String foundSelected = null;
        String foundSelectedDesc = null;
        String foundAnimation = null;
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode node = readClass(entry.getValue());
            if (!RECEIVER_HINT.equals(node.name)) continue;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != PUTSTATIC) continue;
                    FieldInsnNode selected = (FieldInsnNode) insn;
                    if (!screenName.equals(selected.owner) || !selected.desc.startsWith("L")) continue;
                    AbstractInsnNode one = nextReal(insn);
                    AbstractInsnNode animationInsn = nextReal(one);
                    if (integerValue(one) == null || integerValue(one).intValue() != 1
                            || !(animationInsn instanceof FieldInsnNode)
                            || animationInsn.getOpcode() != PUTSTATIC) continue;
                    FieldInsnNode animation = (FieldInsnNode) animationInsn;
                    if (!screenName.equals(animation.owner) || !"B".equals(animation.desc)) continue;
                    require(found == null || found.name.equals(node.name), "Ambiguous enhancement response receiver");
                    found = node;
                    foundSelected = selected.name;
                    foundSelectedDesc = selected.desc;
                    foundAnimation = animation.name;
                }
            }
        }
        require(found != null, "Could not identify server enhancement response hook");
        receiverName = found.name;
        selectedField = foundSelected;
        selectedDesc = foundSelectedDesc;
        animationField = foundAnimation;
        return found;
    }

    /** Locates the packet handler that starts the enhancement result animation. */
    private static ClassNode findResponseClass() {
        ClassNode found = null;
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode node = readClass(entry.getValue());
            if (!containsString(node, RESULT_MARKER)) continue;
            require(found == null, "Ambiguous enhancement result packet handler");
            found = node;
        }
        require(found != null, "Enhancement result packet handler was not found");
        require(!found.name.equals(screenName), "Result packet handler collided with the screen");
        netName = found.name;
        return found;
    }

    /**
     * Notifies the auto-enhancer from the packet handler itself. Polling the inventory on a timer
     * used to declare a request finished before the server answered, which queued dozens of extra
     * requests; their late responses then drove the vanilla result animation with a cleared item
     * and wedged the screen.
     */
    private static void patchResponseSite(ClassNode net) {
        int hooks = 0;
        for (MethodNode method : net.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof LdcInsnNode) || !RESULT_MARKER.equals(((LdcInsnNode) insn).cst)) continue;
                FieldInsnNode message = null;
                for (AbstractInsnNode cursor = previousReal(insn); cursor != null; cursor = previousReal(cursor)) {
                    if (!(cursor instanceof FieldInsnNode) || cursor.getOpcode() != PUTSTATIC) continue;
                    FieldInsnNode field = (FieldInsnNode) cursor;
                    require(screenName.equals(field.owner) && "Ljava/lang/String;".equals(field.desc),
                            "Unexpected enhancement result packet layout");
                    message = field;
                    break;
                }
                require(message != null, "Enhancement result message store was not found");
                require(findStaticWriteBefore(message, enhanceStageField, 1, 14) != null,
                        "Enhancement result packet does not start the result animation");
                method.instructions.insert(message,
                        new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoOnServerResult", "()V", false));
                hooks++;
            }
        }
        require(hooks == 1, "Expected one enhancement result hook, found " + hooks);
    }

    private static void validateLevelField() {
        ClassNode levelClass = readClass(entries.get(LEVEL_OWNER + ".class"));
        require(levelClass != null, "Item level owner class is missing");
        for (FieldNode field : levelClass.fields) {
            if (LEVEL_FIELD.equals(field.name) && "B".equals(field.desc)) return;
        }
        throw new IllegalStateException("Item enhancement-level byte field was not found");
    }

    private static void discoverEnhancementState(ClassNode screen) {
        Map<String, Integer> fives = new LinkedHashMap<String, Integer>();
        Map<String, Integer> sixes = new LinkedHashMap<String, Integer>();
        for (MethodNode method : screen.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != GETSTATIC) continue;
                FieldInsnNode field = (FieldInsnNode) insn;
                if (!screenName.equals(field.owner) || !"B".equals(field.desc)) continue;
                Integer value = integerValue(nextReal(insn));
                if (value == null || (value.intValue() != 5 && value.intValue() != 6)) continue;
                Map<String, Integer> counts = value.intValue() == 5 ? fives : sixes;
                Integer old = counts.get(field.name);
                counts.put(field.name, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
            }
        }
        int best = 0;
        for (Map.Entry<String, Integer> item : fives.entrySet()) {
            Integer six = sixes.get(item.getKey());
            if (six == null) continue;
            int score = item.getValue().intValue() + six.intValue();
            if (score > best) {
                best = score;
                enhanceStageField = item.getKey();
            }
        }
        require(enhanceStageField != null && best >= 4, "Could not identify enhancement animation stage");

        Map<String, Integer> timers = new LinkedHashMap<String, Integer>();
        for (MethodNode method : screen.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != GETSTATIC) continue;
                FieldInsnNode field = (FieldInsnNode) insn;
                if (!screenName.equals(field.owner) || !enhanceStageField.equals(field.name)) continue;
                Integer value = integerValue(nextReal(insn));
                if (value == null || value.intValue() != 5) continue;
                AbstractInsnNode cursor = insn;
                for (int checked = 0; checked < 30 && cursor != null; checked++) {
                    cursor = nextReal(cursor);
                    if (!(cursor instanceof FieldInsnNode) || cursor.getOpcode() != PUTFIELD) continue;
                    FieldInsnNode timer = (FieldInsnNode) cursor;
                    if (!screenName.equals(timer.owner) || !"J".equals(timer.desc)) continue;
                    Integer old = timers.get(timer.name);
                    timers.put(timer.name, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
                    break;
                }
            }
        }
        int timerHits = 0;
        for (Map.Entry<String, Integer> item : timers.entrySet()) {
            if (item.getValue().intValue() > timerHits) {
                timerHits = item.getValue().intValue();
                resultTimerField = item.getKey();
            }
        }
        require(resultTimerField != null && timerHits >= 2, "Could not identify enhancement result timer");

        int bestUpdateRefs = 0;
        MethodNode update = null;
        for (MethodNode method : screen.methods) {
            if (!"()V".equals(method.desc)) continue;
            int refs = 0;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != GETSTATIC) continue;
                FieldInsnNode field = (FieldInsnNode) insn;
                if (screenName.equals(field.owner) && enhanceStageField.equals(field.name)) refs++;
            }
            if (refs > bestUpdateRefs) {
                bestUpdateRefs = refs;
                update = method;
            }
        }
        require(update != null && bestUpdateRefs >= 6, "Could not identify enhancement update loop");
        enhanceUpdateName = update.name;

        Map<String, Integer> busyScores = new LinkedHashMap<String, Integer>();
        for (AbstractInsnNode insn = update.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != GETSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode) insn;
            if (!screenName.equals(field.owner) || !"Z".equals(field.desc)) continue;
            AbstractInsnNode cursor = insn;
            for (int checked = 0; checked < 8 && cursor != null; checked++) {
                cursor = nextReal(cursor);
                if (!(cursor instanceof FieldInsnNode) || cursor.getOpcode() != GETSTATIC) continue;
                FieldInsnNode candidateStage = (FieldInsnNode) cursor;
                if (!screenName.equals(candidateStage.owner)
                        || !enhanceStageField.equals(candidateStage.name)) continue;
                Integer stage = integerValue(nextReal(cursor));
                if (stage != null && stage.intValue() == 2) {
                    Integer old = busyScores.get(field.name);
                    busyScores.put(field.name, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
                }
                break;
            }
        }
        int bestBusyScore = 0;
        for (Map.Entry<String, Integer> item : busyScores.entrySet()) {
            if (item.getValue().intValue() > bestBusyScore) {
                bestBusyScore = item.getValue().intValue();
                enhanceBusyField = item.getKey();
            }
        }
        require(enhanceBusyField != null && bestBusyScore >= 1,
                "Could not identify enhancement input-lock flag");

        Map<String, Integer> initCalls = new LinkedHashMap<String, Integer>();
        Map<String, Integer> initOpcodes = new LinkedHashMap<String, Integer>();
        for (AbstractInsnNode insn = update.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (!screenName.equals(call.owner) || !"()V".equals(call.desc)
                    || previousReal(insn) == null || previousReal(insn).getOpcode() != ALOAD) continue;
            boolean guardedByAnimation = false;
            AbstractInsnNode cursor = insn;
            for (int checked = 0; checked < 10 && cursor != null; checked++) {
                cursor = previousReal(cursor);
                if (!(cursor instanceof FieldInsnNode) || cursor.getOpcode() != GETSTATIC) continue;
                FieldInsnNode field = (FieldInsnNode) cursor;
                if (screenName.equals(field.owner) && animationField.equals(field.name)) {
                    guardedByAnimation = true;
                    break;
                }
            }
            if (!guardedByAnimation) continue;
            Integer old = initCalls.get(call.name);
            initCalls.put(call.name, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
            initOpcodes.put(call.name, Integer.valueOf(call.getOpcode()));
        }
        int initHits = 0;
        for (Map.Entry<String, Integer> item : initCalls.entrySet()) {
            if (item.getValue().intValue() > initHits) {
                initHits = item.getValue().intValue();
                resultInitName = item.getKey();
                resultInitOpcode = initOpcodes.get(item.getKey()).intValue();
            }
        }
        require(resultInitName != null && initHits >= 2, "Could not identify enhancement result initializer");

        int lookupHits = 0;
        for (AbstractInsnNode insn = update.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            Type[] arguments = Type.getArgumentTypes(call.desc);
            Type returnType = Type.getReturnType(call.desc);
            if (!LEVEL_OWNER.equals(call.owner) || call.getOpcode() != INVOKESTATIC
                    || arguments.length != 2 || arguments[0].getSort() != Type.INT
                    || arguments[1].getSort() != Type.SHORT || returnType.getSort() != Type.OBJECT
                    || !LEVEL_OWNER.equals(returnType.getInternalName())) continue;
            AbstractInsnNode narrowing = previousReal(insn);
            AbstractInsnNode key = previousReal(narrowing);
            AbstractInsnNode keyObject = previousReal(key);
            AbstractInsnNode id = previousReal(keyObject);
            AbstractInsnNode idObject = previousReal(id);
            if (!(key instanceof FieldInsnNode) || key.getOpcode() != GETFIELD
                    || !(id instanceof FieldInsnNode) || id.getOpcode() != GETFIELD
                    || !(keyObject instanceof FieldInsnNode) || keyObject.getOpcode() != GETSTATIC
                    || !(idObject instanceof FieldInsnNode) || idObject.getOpcode() != GETSTATIC
                    || narrowing == null || narrowing.getOpcode() != I2S) continue;
            FieldInsnNode keyField = (FieldInsnNode) key;
            FieldInsnNode idField = (FieldInsnNode) id;
            FieldInsnNode keySelected = (FieldInsnNode) keyObject;
            FieldInsnNode idSelected = (FieldInsnNode) idObject;
            if (!"I".equals(keyField.desc) || !"I".equals(idField.desc)
                    || !screenName.equals(keySelected.owner) || !selectedField.equals(keySelected.name)
                    || !screenName.equals(idSelected.owner) || !selectedField.equals(idSelected.name)) continue;
            if (lookupHits == 0) {
                inventoryLookupName = call.name;
                inventoryLookupOpcode = call.getOpcode();
                inventoryLookupDesc = call.desc;
                inventoryIdOwner = idField.owner;
                inventoryIdField = idField.name;
                inventoryKeyOwner = keyField.owner;
                inventoryKeyField = keyField.name;
            } else {
                require(inventoryLookupName.equals(call.name) && inventoryIdField.equals(idField.name)
                                && inventoryKeyField.equals(keyField.name),
                        "Ambiguous inventory lookup in enhancement result flow");
            }
            lookupHits++;
        }
        require(inventoryLookupName != null && lookupHits >= 2,
                "Could not identify selected equipment lookup in inventory");
    }

    private static void patchScreen(ClassNode screen) {
        require(findMethod(screen, "hsoskyAutoTickGate", "()V") == null, "Checkpoint is already auto-enhance patched");
        screen.fields.add(new FieldNode(ACC_PRIVATE | ACC_STATIC, "hsoskyAutoScreen", "L" + screenName + ";", null, null));
        screen.fields.add(new FieldNode(ACC_PRIVATE | ACC_STATIC, "hsoskyAutoLastLevel", "I", null, null));
        addSendGoldMethod(screen);
        addFindInventoryMethod(screen);
        addCaptureBaselineMethod(screen);
        addShowTargetsMethod(screen);
        addHandleMethod(screen);
        addRestoreUiMethod(screen);
        addResponseMethod(screen);
        addServerResultMethod(screen);
        addTickGateMethod(screen);
        patchUpdateGate(screen);

        MethodNode handler = findMethod(screen, handlerName, "(II)V");
        AbstractInsnNode confirmationMarker = null;
        for (AbstractInsnNode insn = handler.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode && "TabRebuildItem menu".equals(((LdcInsnNode) insn).cst)) {
                confirmationMarker = insn;
                break;
            }
        }
        require(confirmationMarker != null, "Confirmation popup marker disappeared before patching");
        MethodInsnNode popupOpen = null;
        MethodInsnNode gemButtonCtor = null;
        for (AbstractInsnNode insn = confirmationMarker; insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (call.getOpcode() == INVOKESPECIAL && commandOwner.equals(call.owner)
                    && "<init>".equals(call.name) && popupCommandCtorDesc.equals(call.desc)) {
                AbstractInsnNode callback = previousReal(call);
                AbstractInsnNode payment = previousReal(callback);
                AbstractInsnNode action = previousReal(payment);
                if (integerValue(payment) != null && integerValue(payment).intValue() == 1
                        && integerValue(action) != null && integerValue(action).intValue() == 2) {
                    gemButtonCtor = call;
                }
            }
            if (popupOpenOwner.equals(call.owner) && popupOpenName.equals(call.name)
                    && popupOpenDesc.equals(call.desc)) {
                popupOpen = call;
                break;
            }
        }
        require(popupOpen != null, "Confirmation popup call disappeared before patching");
        require(gemButtonCtor != null, "Gem confirmation button was not found");
        AbstractInsnNode callback = previousReal(gemButtonCtor);
        AbstractInsnNode payment = previousReal(callback);
        AbstractInsnNode action = previousReal(payment);
        AbstractInsnNode label = previousReal(action);
        handler.instructions.set(label, new LdcInsnNode("Auto đập đồ"));
        handler.instructions.set(action, new IntInsnNode(BIPUSH, 90));

        InsnList guard = new InsnList();
        LabelNode original = new LabelNode();
        guard.add(new VarInsnNode(ALOAD, 0));
        guard.add(new VarInsnNode(ILOAD, 1));
        guard.add(new VarInsnNode(ILOAD, 2));
        guard.add(new MethodInsnNode(INVOKESPECIAL, screenName, "hsoskyAutoHandle", "(II)Z", false));
        guard.add(new JumpInsnNode(IFEQ, original));
        guard.add(new InsnNode(RETURN));
        guard.add(original);
        handler.instructions.insert(guard);
    }

    private static void addShowTargetsMethod(ClassNode screen) {
        MethodNode method = new MethodNode(ACC_PRIVATE, "hsoskyAutoShowTargets", "()V", null, null);
        InsnList out = method.instructions;
        out.add(new TypeInsnNode(NEW, popupVectorOwner));
        out.add(new InsnNode(DUP));
        out.add(new LdcInsnNode("HSOSKY auto enhance targets"));
        out.add(new MethodInsnNode(INVOKESPECIAL, popupVectorOwner, "<init>", popupVectorCtorDesc, false));
        out.add(new VarInsnNode(ASTORE, 1));
        addPopupCommand(out, "Đồ +15", 91);
        addPopupCommand(out, "Đồ +14", 92);
        addPopupCommand(out, "Đồ +13", 93);
        if (popupOpenOpcode != INVOKESTATIC) {
            out.add(new FieldInsnNode(GETSTATIC, popupOpenReceiverFieldOwner, popupOpenReceiverFieldName,
                    popupOpenReceiverFieldDesc));
        }
        out.add(new VarInsnNode(ALOAD, 1));
        out.add(new LdcInsnNode("Chọn cấp cường hóa tự động:"));
        out.add(new FieldInsnNode(GETSTATIC, popupCallbackFieldOwner, popupCallbackFieldName,
                popupCallbackFieldDesc));
        out.add(new InsnNode(ICONST_2));
        out.add(new InsnNode(ICONST_0));
        out.add(new InsnNode(ICONST_0));
        out.add(new MethodInsnNode(popupOpenOpcode, popupOpenOwner, popupOpenName, popupOpenDesc,
                popupOpenOpcode == INVOKEINTERFACE));
        out.add(new InsnNode(RETURN));
        screen.methods.add(method);
    }

    private static void addPopupCommand(InsnList out, String label, int action) {
        out.add(new VarInsnNode(ALOAD, 1));
        out.add(new TypeInsnNode(NEW, commandOwner));
        out.add(new InsnNode(DUP));
        out.add(new LdcInsnNode(label));
        pushInt(out, action);
        out.add(new InsnNode(ICONST_0));
        out.add(new VarInsnNode(ALOAD, 0));
        out.add(new MethodInsnNode(INVOKESPECIAL, commandOwner, "<init>", popupCommandCtorDesc, false));
        out.add(new MethodInsnNode(INVOKEVIRTUAL, popupVectorOwner, popupVectorAdd,
                "(Ljava/lang/Object;)V", false));
    }

    private static void addClosePopup(InsnList out) {
        out.add(new FieldInsnNode(GETSTATIC, popupCloseFieldOwner, popupCloseFieldName, popupCloseFieldDesc));
        out.add(new MethodInsnNode(popupCloseOpcode, popupCloseOwner, popupCloseName, "()V",
                popupCloseOpcode == INVOKEINTERFACE));
    }

    private static void addRestoreUiMethod(ClassNode screen) {
        MethodNode method = new MethodNode(ACC_PRIVATE | ACC_STATIC, "hsoskyAutoRestoreUi", "()V", null, null);
        InsnList out = method.instructions;
        addClosePopup(out);
        out.add(new InsnNode(ICONST_0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, enhanceStageField, "B"));
        out.add(new InsnNode(ICONST_0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, animationField, "B"));
        out.add(new InsnNode(ICONST_0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, enhanceBusyField, "Z"));
        out.add(new InsnNode(ACONST_NULL));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, "hsoskyAutoScreen", "L" + screenName + ";"));
        out.add(new InsnNode(RETURN));
        screen.methods.add(method);
    }

    private static void addSendGoldMethod(ClassNode screen) {
        MethodNode method = new MethodNode(ACC_PRIVATE, "hsoskyAutoSendGold", "()V", null, null);
        method.instructions.add(new MethodInsnNode(singletonOpcode, singletonOwner, singletonName, singletonDesc,
                singletonOpcode == INVOKEINTERFACE));
        method.instructions.add(new InsnNode(ICONST_2));
        method.instructions.add(new InsnNode(ICONST_0));
        method.instructions.add(new InsnNode(ICONST_0));
        method.instructions.add(new MethodInsnNode(sendOpcode, sendOwner, sendName, "(BSB)V",
                sendOpcode == INVOKEINTERFACE));
        method.instructions.add(new InsnNode(RETURN));
        screen.methods.add(method);
    }

    private static void addFindInventoryMethod(ClassNode screen) {
        MethodNode method = new MethodNode(ACC_PRIVATE | ACC_STATIC, "hsoskyAutoFindInventoryItem",
                "()" + selectedDesc, null, null);
        InsnList out = method.instructions;
        out.add(new FieldInsnNode(GETSTATIC, screenName, selectedField, selectedDesc));
        out.add(new FieldInsnNode(GETFIELD, inventoryIdOwner, inventoryIdField, "I"));
        out.add(new FieldInsnNode(GETSTATIC, screenName, selectedField, selectedDesc));
        out.add(new FieldInsnNode(GETFIELD, inventoryKeyOwner, inventoryKeyField, "I"));
        out.add(new InsnNode(I2S));
        out.add(new MethodInsnNode(inventoryLookupOpcode, LEVEL_OWNER, inventoryLookupName,
                inventoryLookupDesc, inventoryLookupOpcode == INVOKEINTERFACE));
        out.add(new TypeInsnNode(CHECKCAST, Type.getType(selectedDesc).getInternalName()));
        out.add(new InsnNode(ARETURN));
        screen.methods.add(method);
    }

    private static void addCaptureBaselineMethod(ClassNode screen) {
        MethodNode method = new MethodNode(ACC_PRIVATE, "hsoskyAutoCaptureBaseline", "()V", null, null);
        InsnList out = method.instructions;
        LabelNode noItem = new LabelNode();
        LabelNode done = new LabelNode();
        out.add(new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoFindInventoryItem",
                "()" + selectedDesc, false));
        out.add(new InsnNode(DUP));
        out.add(new JumpInsnNode(IFNULL, noItem));
        out.add(new FieldInsnNode(GETFIELD, LEVEL_OWNER, LEVEL_FIELD, "B"));
        pushInt(out, 255);
        out.add(new InsnNode(IAND));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, "hsoskyAutoLastLevel", "I"));
        out.add(new JumpInsnNode(GOTO, done));
        out.add(noItem);
        out.add(new InsnNode(POP));
        out.add(new InsnNode(ICONST_M1));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, "hsoskyAutoLastLevel", "I"));
        out.add(done);
        out.add(new InsnNode(RETURN));
        screen.methods.add(method);
    }

    private static void addRefreshMenuMethod(ClassNode screen) {
        MethodNode method = new MethodNode(ACC_PRIVATE, "hsoskyAutoRefreshMenu", "()V", null, null);
        InsnList out = method.instructions;
        LabelNode notMenu = new LabelNode();
        LabelNode inactive = new LabelNode();

        out.add(new VarInsnNode(ALOAD, 0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, "hsoskyAutoScreen", "L" + screenName + ";"));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isMenuOpen", "()Z", false));
        out.add(new JumpInsnNode(IFEQ, notMenu));
        addClear(out);
        addCommand(out, "Đồ +15", 91);
        addCommand(out, "Đồ +14", 92);
        addCommand(out, "Đồ +13", 93);
        addCommand(out, "Quay lại", 94);
        out.add(new InsnNode(RETURN));

        out.add(notMenu);
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isActive", "()Z", false));
        out.add(new JumpInsnNode(IFEQ, inactive));
        addClear(out);
        addCommand(out, "Dừng auto đập đồ", 95);
        out.add(new InsnNode(RETURN));

        out.add(inactive);
        addCommand(out, "Auto đập đồ", 90);
        out.add(new InsnNode(RETURN));
        screen.methods.add(method);
    }

    private static void addClear(InsnList out) {
        out.add(new VarInsnNode(ALOAD, 0));
        out.add(new FieldInsnNode(GETFIELD, screenName, vectorField, vectorDesc));
        out.add(new MethodInsnNode(INVOKEVIRTUAL, vectorOwner, vectorClear, "()V", false));
    }

    private static void addCommand(InsnList out, String label, int action) {
        out.add(new VarInsnNode(ALOAD, 0));
        out.add(new FieldInsnNode(GETFIELD, screenName, vectorField, vectorDesc));
        out.add(new TypeInsnNode(NEW, commandOwner));
        out.add(new InsnNode(DUP));
        out.add(new LdcInsnNode(label));
        pushInt(out, action);
        out.add(new VarInsnNode(ALOAD, 0));
        out.add(new MethodInsnNode(INVOKESPECIAL, commandOwner, "<init>", commandCtorDesc, false));
        out.add(new MethodInsnNode(INVOKEVIRTUAL, vectorOwner, vectorAdd, "(Ljava/lang/Object;)V", false));
    }

    private static void addHandleMethod(ClassNode screen) {
        MethodNode method = new MethodNode(ACC_PRIVATE, "hsoskyAutoHandle", "(II)Z", null, null);
        InsnList out = method.instructions;
        LabelNode notClose = new LabelNode();
        LabelNode open = new LabelNode();
        LabelNode target15 = new LabelNode();
        LabelNode target14 = new LabelNode();
        LabelNode target13 = new LabelNode();
        LabelNode back = new LabelNode();
        LabelNode stop = new LabelNode();
        LabelNode unhandled = new LabelNode();
        LabelNode haveItem = new LabelNode();
        LabelNode belowTarget = new LabelNode();
        LabelNode start = new LabelNode();

        out.add(new VarInsnNode(ILOAD, 1));
        out.add(new InsnNode(ICONST_M1));
        out.add(new JumpInsnNode(IF_ICMPNE, notClose));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "stop", "()V", false));
        out.add(new InsnNode(ACONST_NULL));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, "hsoskyAutoScreen", "L" + screenName + ";"));
        out.add(new InsnNode(ICONST_0));
        out.add(new InsnNode(IRETURN));

        out.add(notClose);
        out.add(new VarInsnNode(ILOAD, 1)); pushInt(out, 90); out.add(new JumpInsnNode(IF_ICMPEQ, open));
        out.add(new VarInsnNode(ILOAD, 1)); pushInt(out, 91); out.add(new JumpInsnNode(IF_ICMPEQ, target15));
        out.add(new VarInsnNode(ILOAD, 1)); pushInt(out, 92); out.add(new JumpInsnNode(IF_ICMPEQ, target14));
        out.add(new VarInsnNode(ILOAD, 1)); pushInt(out, 93); out.add(new JumpInsnNode(IF_ICMPEQ, target13));
        out.add(new VarInsnNode(ILOAD, 1)); pushInt(out, 94); out.add(new JumpInsnNode(IF_ICMPEQ, back));
        out.add(new VarInsnNode(ILOAD, 1)); pushInt(out, 95); out.add(new JumpInsnNode(IF_ICMPEQ, stop));
        out.add(new JumpInsnNode(GOTO, unhandled));

        out.add(open);
        addClosePopup(out);
        out.add(new VarInsnNode(ALOAD, 0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, "hsoskyAutoScreen", "L" + screenName + ";"));
        out.add(new VarInsnNode(ALOAD, 0));
        out.add(new MethodInsnNode(INVOKESPECIAL, screenName, "hsoskyAutoShowTargets", "()V", false));
        out.add(new InsnNode(ICONST_1));
        out.add(new InsnNode(IRETURN));

        out.add(target15); pushInt(out, 15); out.add(new VarInsnNode(ISTORE, 3)); out.add(new JumpInsnNode(GOTO, start));
        out.add(target14); pushInt(out, 14); out.add(new VarInsnNode(ISTORE, 3)); out.add(new JumpInsnNode(GOTO, start));
        out.add(target13); pushInt(out, 13); out.add(new VarInsnNode(ISTORE, 3));

        out.add(start);
        addClosePopup(out);
        out.add(new VarInsnNode(ALOAD, 0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, "hsoskyAutoScreen", "L" + screenName + ";"));
        out.add(new FieldInsnNode(GETSTATIC, screenName, selectedField, selectedDesc));
        out.add(new JumpInsnNode(IFNONNULL, haveItem));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "stop", "()V", false));
        out.add(new InsnNode(ICONST_1));
        out.add(new InsnNode(IRETURN));

        out.add(haveItem);
        out.add(new FieldInsnNode(GETSTATIC, screenName, selectedField, selectedDesc));
        out.add(new FieldInsnNode(GETFIELD, LEVEL_OWNER, LEVEL_FIELD, "B"));
        pushInt(out, 255);
        out.add(new InsnNode(IAND));
        out.add(new VarInsnNode(ILOAD, 3));
        out.add(new JumpInsnNode(IF_ICMPLT, belowTarget));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "stop", "()V", false));
        out.add(new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoRestoreUi", "()V", false));
        out.add(new InsnNode(ICONST_1));
        out.add(new InsnNode(IRETURN));

        out.add(belowTarget);
        out.add(new VarInsnNode(ILOAD, 3));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "start", "(I)V", false));
        out.add(new InsnNode(ICONST_0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, animationField, "B"));
        out.add(new VarInsnNode(ALOAD, 0));
        out.add(new MethodInsnNode(INVOKESPECIAL, screenName, "hsoskyAutoCaptureBaseline", "()V", false));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "markWaiting", "()V", false));
        out.add(new VarInsnNode(ALOAD, 0));
        out.add(new MethodInsnNode(INVOKESPECIAL, screenName, "hsoskyAutoSendGold", "()V", false));
        out.add(new InsnNode(ICONST_1));
        out.add(new InsnNode(IRETURN));

        out.add(back);
        addClosePopup(out);
        out.add(new InsnNode(ICONST_1));
        out.add(new InsnNode(IRETURN));

        out.add(stop);
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "stop", "()V", false));
        out.add(new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoRestoreUi", "()V", false));
        out.add(new InsnNode(ICONST_1));
        out.add(new InsnNode(IRETURN));

        out.add(unhandled);
        out.add(new InsnNode(ICONST_0));
        out.add(new InsnNode(IRETURN));
        screen.methods.add(method);
    }

    private static void addResponseMethod(ClassNode screen) {
        MethodNode method = new MethodNode(ACC_PUBLIC | ACC_STATIC, "hsoskyAutoOnResponse", "()V", null, null);
        InsnList out = method.instructions;
        LabelNode active = new LabelNode();
        LabelNode waiting = new LabelNode();
        LabelNode haveItem = new LabelNode();
        LabelNode continueAuto = new LabelNode();
        LabelNode haveScreenToContinue = new LabelNode();
        LabelNode done = new LabelNode();

        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isActive", "()Z", false));
        out.add(new JumpInsnNode(IFNE, active));
        out.add(new InsnNode(RETURN));
        out.add(active);
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isWaiting", "()Z", false));
        out.add(new JumpInsnNode(IFNE, waiting));
        out.add(new InsnNode(RETURN));
        out.add(waiting);
        out.add(new InsnNode(ICONST_0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, animationField, "B"));
        out.add(new FieldInsnNode(GETSTATIC, screenName, selectedField, selectedDesc));
        out.add(new JumpInsnNode(IFNONNULL, haveItem));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "stop", "()V", false));
        out.add(new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoRestoreUi", "()V", false));
        out.add(new JumpInsnNode(GOTO, done));

        out.add(haveItem);
        out.add(new FieldInsnNode(GETSTATIC, screenName, selectedField, selectedDesc));
        out.add(new FieldInsnNode(GETFIELD, LEVEL_OWNER, LEVEL_FIELD, "B"));
        pushInt(out, 255);
        out.add(new InsnNode(IAND));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "completeResponse", "(I)Z", false));
        out.add(new JumpInsnNode(IFNE, continueAuto));
        out.add(new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoRestoreUi", "()V", false));
        out.add(new JumpInsnNode(GOTO, done));

        out.add(continueAuto);
        out.add(new FieldInsnNode(GETSTATIC, screenName, "hsoskyAutoScreen", "L" + screenName + ";"));
        out.add(new InsnNode(DUP));
        out.add(new JumpInsnNode(IFNONNULL, haveScreenToContinue));
        out.add(new InsnNode(POP));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "stop", "()V", false));
        out.add(new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoRestoreUi", "()V", false));
        out.add(new JumpInsnNode(GOTO, done));
        out.add(haveScreenToContinue);
        out.add(new VarInsnNode(ASTORE, 0));
        out.add(new VarInsnNode(ALOAD, 0));
        out.add(new MethodInsnNode(INVOKESPECIAL, screenName, "hsoskyAutoCaptureBaseline", "()V", false));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "markWaiting", "()V", false));
        out.add(new VarInsnNode(ALOAD, 0));
        out.add(new MethodInsnNode(INVOKESPECIAL, screenName, "hsoskyAutoSendGold", "()V", false));

        out.add(done);
        out.add(new InsnNode(RETURN));
        screen.methods.add(method);
    }

    /** Receiver-side hook: mark that the server has produced a result. */
    private static void addBeginResultMethod(ClassNode screen) {
        MethodNode method = new MethodNode(ACC_PUBLIC | ACC_STATIC, "hsoskyAutoBeginResult", "()V", null, null);
        InsnList out = method.instructions;
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "markResultReady", "()V", false));
        out.add(new InsnNode(RETURN));
        screen.methods.add(method);
    }

    /** Packet-thread hook: the server has answered the outstanding enhancement request. */
    private static void addServerResultMethod(ClassNode screen) {
        MethodNode method = new MethodNode(ACC_PUBLIC | ACC_STATIC, "hsoskyAutoOnServerResult", "()V", null, null);
        InsnList out = method.instructions;
        LabelNode done = new LabelNode();

        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "markResultReady", "()V", false));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isActive", "()Z", false));
        out.add(new JumpInsnNode(IFEQ, done));
        out.add(new InsnNode(ICONST_0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, enhanceStageField, "B"));
        out.add(new InsnNode(ICONST_0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, animationField, "B"));
        out.add(done);
        out.add(new InsnNode(RETURN));
        screen.methods.add(method);
    }

    /** Runs before every enhancement-screen update and prevents every auto animation frame. */
    private static void addTickGateMethod(ClassNode screen) {
        MethodNode method = new MethodNode(ACC_PRIVATE, "hsoskyAutoTickGate", "()V", null, null);
        InsnList out = method.instructions;
        LabelNode guarded = new LabelNode();
        LabelNode done = new LabelNode();
        LabelNode haveItem = new LabelNode();
        LabelNode answered = new LabelNode();
        LabelNode itemFound = new LabelNode();
        LabelNode resolved = new LabelNode();

        // Stages 5 and 6 dereference the selected item without a null check. Reaching either of
        // them with no selection is the vanilla crash that leaves the screen wedged, because the
        // stage never returns to 0 and every input path requires stage 0.
        out.add(new FieldInsnNode(GETSTATIC, screenName, selectedField, selectedDesc));
        out.add(new JumpInsnNode(IFNONNULL, guarded));
        out.add(new FieldInsnNode(GETSTATIC, screenName, enhanceStageField, "B"));
        pushInt(out, 5);
        out.add(new JumpInsnNode(IF_ICMPLT, guarded));
        out.add(new FieldInsnNode(GETSTATIC, screenName, enhanceStageField, "B"));
        pushInt(out, 6);
        out.add(new JumpInsnNode(IF_ICMPGT, guarded));
        out.add(new InsnNode(ICONST_0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, enhanceStageField, "B"));
        out.add(new InsnNode(ICONST_0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, enhanceBusyField, "Z"));

        out.add(guarded);
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isActive", "()Z", false));
        out.add(new JumpInsnNode(IFEQ, done));
        out.add(new InsnNode(ICONST_0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, enhanceStageField, "B"));
        out.add(new InsnNode(ICONST_0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, animationField, "B"));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isWaiting", "()Z", false));
        out.add(new JumpInsnNode(IFEQ, done));
        out.add(new FieldInsnNode(GETSTATIC, screenName, selectedField, selectedDesc));
        out.add(new JumpInsnNode(IFNONNULL, haveItem));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "stop", "()V", false));
        out.add(new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoRestoreUi", "()V", false));
        out.add(new JumpInsnNode(GOTO, done));

        // No optimistic timeout: only a real result packet advances the loop, so exactly one
        // request is ever in flight and nothing is left over once the target level is reached.
        out.add(haveItem);
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isResultReady", "()Z", false));
        out.add(new JumpInsnNode(IFNE, answered));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isRequestStalled", "()Z", false));
        out.add(new JumpInsnNode(IFEQ, done));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "stop", "()V", false));
        out.add(new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoRestoreUi", "()V", false));
        out.add(new JumpInsnNode(GOTO, done));

        out.add(answered);
        out.add(new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoFindInventoryItem",
                "()" + selectedDesc, false));
        out.add(new VarInsnNode(ASTORE, 1));
        out.add(new VarInsnNode(ALOAD, 1));
        out.add(new JumpInsnNode(IFNONNULL, itemFound));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isResultSettled", "()Z", false));
        out.add(new JumpInsnNode(IFEQ, done));
        out.add(new InsnNode(ACONST_NULL));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, selectedField, selectedDesc));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "stop", "()V", false));
        out.add(new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoRestoreUi", "()V", false));
        out.add(new JumpInsnNode(GOTO, done));

        out.add(itemFound);
        out.add(new VarInsnNode(ALOAD, 1));
        out.add(new FieldInsnNode(GETFIELD, LEVEL_OWNER, LEVEL_FIELD, "B"));
        pushInt(out, 255);
        out.add(new InsnNode(IAND));
        out.add(new FieldInsnNode(GETSTATIC, screenName, "hsoskyAutoLastLevel", "I"));
        out.add(new JumpInsnNode(IF_ICMPNE, resolved));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isResultSettled", "()Z", false));
        out.add(new JumpInsnNode(IFEQ, done));

        out.add(resolved);
        out.add(new VarInsnNode(ALOAD, 1));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, selectedField, selectedDesc));
        out.add(new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoOnResponse", "()V", false));
        out.add(new InsnNode(ICONST_0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, enhanceStageField, "B"));
        out.add(new InsnNode(ICONST_0));
        out.add(new FieldInsnNode(PUTSTATIC, screenName, animationField, "B"));
        out.add(done);
        out.add(new InsnNode(RETURN));
        screen.methods.add(method);
    }

    private static void patchUpdateGate(ClassNode screen) {
        MethodNode update = findMethod(screen, enhanceUpdateName, "()V");
        require(update != null, "Enhancement update loop disappeared before patching");
        InsnList gate = new InsnList();
        gate.add(new VarInsnNode(ALOAD, 0));
        gate.add(new MethodInsnNode(INVOKESPECIAL, screenName, "hsoskyAutoTickGate", "()V", false));
        update.instructions.insert(gate);
    }

    /** Replaces only the result popup; manual enhancement still calls the original popup verbatim. */
    private static void addResultPopupWrapper(ClassNode screen) {
        Type[] originalArgs = Type.getArgumentTypes(popupOpenDesc);
        boolean originalStatic = popupOpenOpcode == INVOKESTATIC;
        Type[] wrapperArgs = new Type[originalArgs.length + (originalStatic ? 0 : 1)];
        int offset = 0;
        if (!originalStatic) {
            wrapperArgs[0] = Type.getObjectType(popupOpenOwner);
            offset = 1;
        }
        System.arraycopy(originalArgs, 0, wrapperArgs, offset, originalArgs.length);
        resultPopupWrapperDesc = Type.getMethodDescriptor(Type.VOID_TYPE, wrapperArgs);
        MethodNode method = new MethodNode(ACC_PRIVATE | ACC_STATIC, "hsoskyAutoResultPopup",
                resultPopupWrapperDesc, null, null);
        InsnList out = method.instructions;
        LabelNode manual = new LabelNode();

        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isActive", "()Z", false));
        out.add(new JumpInsnNode(IFEQ, manual));
        out.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "isResultReady", "()Z", false));
        out.add(new JumpInsnNode(IFEQ, manual));
        out.add(new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoOnResponse", "()V", false));
        out.add(new InsnNode(RETURN));

        out.add(manual);
        int local = 0;
        if (!originalStatic) {
            out.add(new VarInsnNode(ALOAD, local));
            local++;
        }
        for (Type argument : originalArgs) {
            out.add(new VarInsnNode(argument.getOpcode(ILOAD), local));
            local += argument.getSize();
        }
        out.add(new MethodInsnNode(popupOpenOpcode, popupOpenOwner, popupOpenName, popupOpenDesc,
                popupOpenOpcode == INVOKEINTERFACE));
        out.add(new InsnNode(RETURN));
        screen.methods.add(method);
    }

    private static void patchResultPopups(ClassNode screen) {
        int patched = 0;
        for (MethodNode method : screen.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                if (!(insn instanceof MethodInsnNode)) {
                    insn = next;
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) insn;
                if (!popupOpenOwner.equals(call.owner) || !popupOpenName.equals(call.name)
                        || !popupOpenDesc.equals(call.desc) || !hasStageSixAfter(call, 20)) {
                    insn = next;
                    continue;
                }

                call.setOpcode(INVOKESTATIC);
                call.owner = screenName;
                call.name = "hsoskyAutoResultPopup";
                call.desc = resultPopupWrapperDesc;
                call.itf = false;
                patched++;
                insn = next;
            }
        }
        require(patched >= 3, "Expected enhancement result popups, found " + patched);
    }

    private static boolean hasStageSixAfter(AbstractInsnNode start, int limit) {
        return findStaticWriteAfter(start, enhanceStageField, 6, limit) != null;
    }

    private static FieldInsnNode findStaticWriteBefore(AbstractInsnNode start, String fieldName,
            int value, int limit) {
        AbstractInsnNode cursor = start;
        for (int checked = 0; checked < limit && cursor != null; checked++) {
            cursor = previousReal(cursor);
            if (!(cursor instanceof FieldInsnNode) || cursor.getOpcode() != PUTSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode) cursor;
            Integer written = integerValue(previousReal(cursor));
            if (screenName.equals(field.owner) && fieldName.equals(field.name)
                    && written != null && written.intValue() == value) return field;
        }
        return null;
    }

    private static FieldInsnNode findStaticWriteAfter(AbstractInsnNode start, String fieldName,
            int value, int limit) {
        AbstractInsnNode cursor = start;
        for (int checked = 0; checked < limit && cursor != null; checked++) {
            cursor = nextReal(cursor);
            if (!(cursor instanceof FieldInsnNode) || cursor.getOpcode() != PUTSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode) cursor;
            Integer written = integerValue(previousReal(cursor));
            if (screenName.equals(field.owner) && fieldName.equals(field.name)
                    && written != null && written.intValue() == value) return field;
        }
        return null;
    }

    private static void patchReceiver(ClassNode receiver) {
        int hooks = 0;
        for (MethodNode method : receiver.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                if (insn instanceof FieldInsnNode && insn.getOpcode() == PUTSTATIC) {
                    FieldInsnNode field = (FieldInsnNode) insn;
                    if (screenName.equals(field.owner) && animationField.equals(field.name) && "B".equals(field.desc)) {
                        AbstractInsnNode previous = previousReal(insn);
                        if (integerValue(previous) != null && integerValue(previous).intValue() == 1) {
                            method.instructions.insert(insn,
                                    new MethodInsnNode(INVOKESTATIC, screenName, "hsoskyAutoBeginResult", "()V", false));
                            hooks++;
                        }
                    }
                }
                insn = next;
            }
        }
        require(hooks == 1, "Expected one enhancement response hook, found " + hooks);
    }

    private static void verify(File output, Map<String, byte[]> original) throws Exception {
        try (JarFile jar = new JarFile(output)) {
            require(jar.getManifest() != null, "Output manifest is unreadable");
            require(jar.getJarEntry(SUPPORT_ENTRY) != null, "Support class is missing");
            require(jar.size() == original.size() + 1, "Unexpected output entry count: " + jar.size());
            for (Map.Entry<String, byte[]> item : original.entrySet()) {
                if (item.getKey().equals(screenName + ".class")) continue;
                if (item.getKey().equals(netName + ".class")) continue;
                JarEntry entry = jar.getJarEntry(item.getKey());
                require(entry != null, "Output lost entry: " + item.getKey());
                require(java.util.Arrays.equals(item.getValue(), readAll(jar.getInputStream(entry))),
                        "Unrelated entry changed: " + item.getKey());
            }
            ClassNode patchedScreen = readClass(readAll(jar.getInputStream(jar.getJarEntry(screenName + ".class"))));
            require(containsString(patchedScreen, "Auto đập đồ"), "Auto menu label was not persisted");
            require(containsString(patchedScreen, "Đồ +15")
                    && containsString(patchedScreen, "Đồ +14")
                    && containsString(patchedScreen, "Đồ +13"), "Target options were not persisted");
            require(callCount(patchedScreen, screenName, "hsoskyAutoTickGate", "()V") == 1,
                    "Per-tick animation gate was not persisted");
            require(callCount(patchedScreen, screenName, "hsoskyAutoFindInventoryItem", "()" + selectedDesc) >= 2,
                    "Inventory synchronization lookup was not persisted");
            require(callCount(patchedScreen, screenName, "hsoskyAutoCaptureBaseline", "()V") >= 2,
                    "Inventory baseline capture was not persisted");
            require(callCount(patchedScreen, SUPPORT, "isRequestStalled", "()Z") == 1,
                    "Unanswered-request abort was not persisted");
            require(callCount(patchedScreen, SUPPORT, "isResultSettled", "()Z") == 2,
                    "Inventory settle window was not persisted");
            require(callCount(patchedScreen, SUPPORT, "isRequestExpired", "()Z") == 0,
                    "Optimistic request timeout is still wired in");
            require(callCount(patchedScreen, SUPPORT, "markWaiting", "()V") >= 2,
                    "Response-gating state machine was not persisted");
            require(findMethod(patchedScreen, "hsoskyAutoOnServerResult", "()V") != null,
                    "Server result hook is missing from the screen");

            ClassNode patchedNet = readClass(readAll(jar.getInputStream(jar.getJarEntry(netName + ".class"))));
            require(callCount(patchedNet, screenName, "hsoskyAutoOnServerResult", "()V") == 1,
                    "Enhancement result packet does not notify the auto-enhancer");
            MethodNode tickGuard = findMethod(patchedScreen, "hsoskyAutoTickGate", "()V");
            require(tickGuard != null && fieldWriteCount(tickGuard, screenName, enhanceStageField, "B") == 3,
                    "Auto-enhance tick gate lost a stage reset");
            MethodNode restoreUi = findMethod(patchedScreen, "hsoskyAutoRestoreUi", "()V");
            require(restoreUi != null, "Auto-enhance UI restore method is missing");
            require(fieldWriteCount(restoreUi, screenName, enhanceStageField, "B") == 1
                            && fieldWriteCount(restoreUi, screenName, animationField, "B") == 1
                            && fieldWriteCount(restoreUi, screenName, enhanceBusyField, "Z") == 1,
                    "Auto-enhance UI restore does not clear every enhancement lock");
            require(callCount(patchedScreen, screenName, "hsoskyAutoRestoreUi", "()V") >= 5,
                    "Auto-enhance completion paths do not restore the UI");
            MethodNode tickGate = findMethod(patchedScreen, "hsoskyAutoTickGate", "()V");
            require(tickGate != null, "Auto-enhance tick gate is missing");
            require(callCount(tickGate, screenName, enhanceUpdateName, "()V") == 0,
                    "Auto-enhance tick gate recursively calls the enhancement update loop");
        }
    }

    private static int vectorCallCount(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && vectorOwner != null
                    && vectorOwner.equals(((MethodInsnNode) insn).owner)) count++;
        }
        return count;
    }

    private static boolean hasSwitch(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == TABLESWITCH || insn.getOpcode() == LOOKUPSWITCH) return true;
        }
        return false;
    }

    private static void pushInt(InsnList out, int value) {
        if (value >= -1 && value <= 5) out.add(new InsnNode(ICONST_0 + value));
        else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) out.add(new IntInsnNode(BIPUSH, value));
        else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) out.add(new IntInsnNode(SIPUSH, value));
        else out.add(new LdcInsnNode(Integer.valueOf(value)));
    }

    private static Integer integerValue(AbstractInsnNode insn) {
        if (insn == null) return null;
        int opcode = insn.getOpcode();
        if (opcode >= ICONST_M1 && opcode <= ICONST_5) return Integer.valueOf(opcode - ICONST_0);
        if (insn instanceof IntInsnNode) return Integer.valueOf(((IntInsnNode) insn).operand);
        if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer)
            return (Integer) ((LdcInsnNode) insn).cst;
        return null;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        insn = insn == null ? null : insn.getNext();
        while (insn != null && insn.getOpcode() < 0) insn = insn.getNext();
        return insn;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        insn = insn == null ? null : insn.getPrevious();
        while (insn != null && insn.getOpcode() < 0) insn = insn.getPrevious();
        return insn;
    }

    private static AbstractInsnNode previousRealN(AbstractInsnNode insn, int count) {
        while (count-- > 0 && insn != null) insn = previousReal(insn);
        return insn;
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        return null;
    }

    private static int callCount(ClassNode node, String owner, String name, String desc) {
        int count = 0;
        for (MethodNode method : node.methods) {
            count += callCount(method, owner, name, desc);
        }
        return count;
    }

    private static int callCount(MethodNode method, String owner, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) count++;
        }
        return count;
    }

    private static int fieldWriteCount(MethodNode method, String owner, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != PUTSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode) insn;
            if (owner.equals(field.owner) && name.equals(field.name) && desc.equals(field.desc)) count++;
        }
        return count;
    }

    private static boolean containsString(ClassNode node, String value) {
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode && value.equals(((LdcInsnNode) insn).cst)) return true;
            }
        }
        return false;
    }

    private static void readJar(File file) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> all = jar.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                entries.put(entry.getName(), readAll(jar.getInputStream(entry)));
                times.put(entry.getName(), Long.valueOf(entry.getTime()));
            }
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }

    private static ClassNode readClass(byte[] bytes) {
        if (bytes == null) return null;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static void save(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        entries.put(node.name + ".class", writer.toByteArray());
    }

    private static void writeJar(File file) throws Exception {
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, byte[]> item : entries.entrySet()) {
                JarEntry entry = new JarEntry(item.getKey());
                Long time = times.get(item.getKey());
                if (time != null && time.longValue() >= 0L) entry.setTime(time.longValue());
                out.putNextEntry(entry);
                if (!item.getKey().endsWith("/")) out.write(item.getValue());
                out.closeEntry();
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
