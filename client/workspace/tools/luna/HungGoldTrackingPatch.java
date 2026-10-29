import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Adds the exact native wallet receive hook and the generated EXP-dialog gold ABI. */
public final class HungGoldTrackingPatch implements Opcodes {
    private static final String GOLD = "luna/features/GoldRateSupport";
    private static final String BRIDGE_DEFAULT = "luna/generated/ExpEstimateBridge";
    private static final String CACHE_DEFAULT = "luna/generated/HungMonsterCacheAccess";
    private static final String API_DEFAULT = "luna/api/MonsterCacheAccess";
    private static final String SUPPORT_DEFAULT = "luna/features/ExpEstimateSupport";
    private static final String READY = "goldReady";
    private static final String READY_ID = "goldReadyId";
    private static final String READY_SERVER = "goldReadyServer";
    private static final String PENDING = "goldPending";
    private static final String PENDING_VALUE = "goldPendingValue";

    /** Repair the old sign checks without changing any other bridge instructions. */
    public static void fixSignedIds(Map<String,byte[]> entries, Properties catalog) {
        String owner = catalog.getProperty("gold.bridge.owner", BRIDGE_DEFAULT);
        ClassNode bridge = node(entries.get(owner + ".class"));
        int removed = 0;
        for (String name : new String[] { "goldSnapshot", "goldBalance" }) {
            MethodNode method = method(bridge, name, "()V");
            int count = 0;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != IFLE) continue;
                AbstractInsnNode load = instruction.getPrevious();
                while (load != null && load.getOpcode() < 0) load = load.getPrevious();
                check(load instanceof VarInsnNode && load.getOpcode() == ILOAD
                        && ((VarInsnNode)load).var == 1, "Exact old character ID sign guard");
                method.instructions.remove(load);
                method.instructions.remove(instruction);
                count++;
            }
            check(count == 1, "One old ID sign guard in " + name);
            removed += count;
        }
        check(removed == 2, "Exactly two ID sign guards removed");
        entries.put(owner + ".class", bytes(bridge));
        catalog.setProperty("gold.identity.signed.ids", "true");
        catalog.setProperty("gold.identity.readiness", "explicit character snapshot, not ID sign");
    }

    private HungGoldTrackingPatch() { }

    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }

    private static String n(String suffix) {
        StringBuilder value = new StringBuilder();
        while (value.length() + suffix.length() < 100) value.append('i');
        return value.append(suffix).toString();
    }

    private static ClassNode node(byte[] bytes) {
        check(bytes != null, "Missing class bytes");
        ClassNode result = new ClassNode();
        new ClassReader(bytes).accept(result, 0);
        return result;
    }

    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String required(Properties catalog, String key) {
        String value = catalog.getProperty(key);
        check(value != null && value.length() != 0, "Missing symbol " + key);
        return value;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        for (MethodNode method : owner.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        }
        throw new IllegalStateException("Missing method " + owner.name + "." + name + descriptor);
    }

    private static FieldNode field(ClassNode owner, String name, String descriptor) {
        for (FieldNode field : owner.fields) {
            if (field.name.equals(name) && field.desc.equals(descriptor)) return field;
        }
        throw new IllegalStateException("Missing field " + owner.name + "." + name + ":" + descriptor);
    }

    /** Mutates the current single-client archive map and enriches its symbol catalog. */
    public static void apply(Map<String, byte[]> entries, Properties catalog) {
        check(entries != null && catalog != null, "Archive map and catalog are required");
        check("1".equals(catalog.getProperty("slots", "1")), "Gold tracker is pinned to the single-client profile");
        check(entries.containsKey(GOLD + ".class"), "GoldRateSupport must be in the archive before native patching");

        String bridge = catalog.getProperty("slot.0.bridge.owner", BRIDGE_DEFAULT);
        String mainOwner = required(catalog, "slot.0.main.owner");
        String mainField = required(catalog, "slot.0.main.field");
        String mainDesc = required(catalog, "slot.0.main.descriptor");
        String playerOwner = required(catalog, "slot.0.entity.owner");
        String idField = required(catalog, "slot.0.id.field");
        String receiverOwner = n("lllllll");
        String receiverMethod = n("");
        String packetOwner = n("lllllil");
        String receiverDesc = "(L" + packetOwner + ";Let;B)V";
        String walletField = "bD";
        String walletDesc = "J";
        String cache = CACHE_DEFAULT;
        String api = API_DEFAULT;
        String expSupport = SUPPORT_DEFAULT;

        check(entries.containsKey(bridge + ".class"), "Generated EXP bridge missing: " + bridge);
        check(entries.containsKey(receiverOwner + ".class"), "Native wallet receiver missing");
        check(entries.containsKey(mainOwner + ".class"), "Native player singleton owner missing");
        check(entries.containsKey(cache + ".class"), "Generated server-key accessor missing");
        check(entries.containsKey(api + ".class"), "Stable server-key API missing");
        check(entries.containsKey(expSupport + ".class"), "EXP support missing");

        Type mainType = Type.getType(mainDesc);
        check(mainType.getSort() == Type.OBJECT
                && inherits(entries, mainType.getInternalName(), playerOwner),
                "EXP main object does not inherit the gold wallet/identity owner");
        ClassNode player = node(entries.get(playerOwner + ".class"));
        check(player.name.equals(playerOwner), "Player class entry mismatch");
        field(player, idField, "I");
        field(player, walletField, walletDesc);
        ClassNode mainClass = node(entries.get(mainOwner + ".class"));
        field(mainClass, mainField, mainDesc);

        ClassNode receiver = node(entries.get(receiverOwner + ".class"));
        MethodNode walletHandler = method(receiver, receiverMethod, receiverDesc);
        check((walletHandler.access & ACC_STATIC) == 0, "Native wallet handler must remain an instance method");
        Type[] args = Type.getArgumentTypes(walletHandler.desc);
        check(args.length == 3 && args[0].getSort() == Type.OBJECT
                && args[1].getSort() == Type.OBJECT && args[2].getSort() == Type.BYTE,
                "Native wallet packet descriptor changed");
        int readLongLocal6 = 0;
        int readIntLocal8 = 0;
        int readLongCalls = 0;
        int readIntCalls = 0;
        int walletWrites = 0;
        int realIndex = 0;
        int readLongIndex = -1;
        int longStoreIndex = -1;
        int readIntIndex = -1;
        int intStoreIndex = -1;
        int walletWriteIndex = -1;
        FieldInsnNode walletWrite = null;
        for (AbstractInsnNode insn : walletHandler.instructions.toArray()) {
            int thisIndex = insn.getOpcode() < 0 ? -1 : realIndex++;
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.owner.equals("java/io/DataInputStream") && call.name.equals("readLong")
                        && call.desc.equals("()J")) {
                    readLongCalls++;
                    AbstractInsnNode store = nextReal(insn);
                    if (store instanceof VarInsnNode && store.getOpcode() == LSTORE
                            && ((VarInsnNode) store).var == 6) {
                        readLongLocal6++;
                        readLongIndex = thisIndex;
                        longStoreIndex = realInstructionIndex(walletHandler, store);
                    }
                }
                if (call.owner.equals("java/io/DataInputStream") && call.name.equals("readInt")
                        && call.desc.equals("()I")) {
                    readIntCalls++;
                    AbstractInsnNode store = nextReal(insn);
                    if (store instanceof VarInsnNode && store.getOpcode() == ISTORE
                            && ((VarInsnNode) store).var == 8) {
                        readIntLocal8++;
                        readIntIndex = thisIndex;
                        intStoreIndex = realInstructionIndex(walletHandler, store);
                    }
                }
            }
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode write = (FieldInsnNode) insn;
                if (write.getOpcode() == PUTFIELD && write.owner.equals(playerOwner)
                        && write.name.equals(walletField) && write.desc.equals(walletDesc)) {
                    walletWrites++;
                    walletWrite = write;
                    walletWriteIndex = thisIndex;
                }
            }
        }
        check(readLongCalls == 1
                && readLongLocal6 == 1 && readIntLocal8 == 1
                && readLongIndex >= 0 && longStoreIndex > readLongIndex
                && readIntIndex > longStoreIndex && intStoreIndex > readIntIndex
                && walletWriteIndex > intStoreIndex,
                "Native wallet balance/secondary value locals changed (calls="
                + readLongCalls + "/" + readIntCalls + ", stores=" + readLongLocal6 + "/"
                + readIntLocal8 + ", order=" + readLongIndex + "/" + longStoreIndex + "/"
                + readIntIndex + "/" + intStoreIndex + "/" + walletWriteIndex + ")");
        check(walletWrites == 1 && walletWrite != null,
                "Expected one native PUTFIELD for the player's gold balance");

        int archiveWalletWrites = 0;
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode owner = node(entry.getValue());
            for (MethodNode method : owner.methods) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode) {
                        FieldInsnNode write = (FieldInsnNode) insn;
                        if (write.getOpcode() == PUTFIELD && write.owner.equals(playerOwner)
                                && write.name.equals(walletField) && write.desc.equals(walletDesc)) {
                            archiveWalletWrites++;
                            check(owner.name.equals(receiverOwner)
                                    && method.name.equals(receiverMethod)
                                    && method.desc.equals(receiverDesc),
                                    "Unexpected second native gold write");
                        }
                    }
                }
            }
        }
        check(archiveWalletWrites == 1, "Archive wallet write cardinality changed");
        check(nextReal(walletWrite) != null && !(nextReal(walletWrite) instanceof MethodInsnNode
                && isGoldBalance((MethodInsnNode) nextReal(walletWrite), bridge)),
                "Native wallet hook is already present");

        ClassNode bridgeClass = node(entries.get(bridge + ".class"));
        check(bridgeClass.version <= V1_4, "Generated bridge class version changed");
        check(!hasMethod(bridgeClass, "goldInfo", "()Ljava/lang/String;"), "goldInfo already exists");
        check(!hasMethod(bridgeClass, "goldBalance", "()V"), "goldBalance already exists");
        check(!hasField(bridgeClass, READY, "Z") && !hasField(bridgeClass, PENDING_VALUE, "J"),
                "Gold bridge fields already exist");

        MethodNode snapshot = method(bridgeClass, "snapshot", "()V");
        MethodNode disconnect = method(bridgeClass, "disconnect", "()V");
        int expSnapshotCalls = 0;
        for (AbstractInsnNode insn : snapshot.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == INVOKESTATIC && call.owner.equals(expSupport)
                        && call.name.equals("snapshot")
                        && call.desc.equals("(Ljava/lang/String;III)V")) {
                    snapshot.instructions.insert(call, new MethodInsnNode(INVOKESTATIC,
                            bridge, "goldSnapshot", "()V", false));
                    expSnapshotCalls++;
                }
            }
        }
        check(expSnapshotCalls == 1, "Expected one generated EXP session snapshot call");
        int expDisconnectCalls = 0;
        for (AbstractInsnNode insn : disconnect.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == INVOKESTATIC && call.owner.equals(expSupport)
                        && call.name.equals("disconnect") && call.desc.equals("()V")) {
                    disconnect.instructions.insertBefore(call, new MethodInsnNode(INVOKESTATIC,
                            bridge, "goldDisconnect", "()V", false));
                    expDisconnectCalls++;
                }
            }
        }
        check(expDisconnectCalls == 1, "Expected one generated EXP disconnect call");

        addField(bridgeClass, ACC_PRIVATE | ACC_STATIC, READY, "Z");
        addField(bridgeClass, ACC_PRIVATE | ACC_STATIC, READY_ID, "I");
        addField(bridgeClass, ACC_PRIVATE | ACC_STATIC, READY_SERVER, "Ljava/lang/String;");
        addField(bridgeClass, ACC_PRIVATE | ACC_STATIC, PENDING, "Z");
        addField(bridgeClass, ACC_PRIVATE | ACC_STATIC, PENDING_VALUE, "J");
        addGoldSnapshot(bridgeClass, bridge, mainOwner, mainField, mainDesc,
                playerOwner, idField, cache, api);
        addGoldBalance(bridgeClass, bridge, mainOwner, mainField, mainDesc,
                playerOwner, idField, walletField, walletDesc, cache, api);
        addGoldDisconnect(bridgeClass, bridge);
        addGoldInfo(bridgeClass);

        InsnList hook = new InsnList();
        hook.add(new MethodInsnNode(INVOKESTATIC, bridge, "goldBalance", "()V", false));
        walletHandler.instructions.insert(walletWrite, hook);
        entries.put(receiverOwner + ".class", bytes(receiver));
        entries.put(bridge + ".class", bytes(bridgeClass));

        catalog.setProperty("gold.receiver.owner", receiverOwner);
        catalog.setProperty("gold.receiver.method", receiverMethod);
        catalog.setProperty("gold.receiver.descriptor", receiverDesc);
        catalog.setProperty("gold.receiver.wallet.hooks", "1");
        catalog.setProperty("gold.receiver.wallet.value.source", "DataInputStream.readLong->local6");
        catalog.setProperty("gold.wallet.owner", playerOwner);
        catalog.setProperty("gold.wallet.field", walletField);
        catalog.setProperty("gold.wallet.descriptor", walletDesc);
        catalog.setProperty("gold.wallet.write.count", String.valueOf(archiveWalletWrites));
        catalog.setProperty("gold.main.owner", mainOwner);
        catalog.setProperty("gold.main.field", mainField);
        catalog.setProperty("gold.character.id.field", idField);
        catalog.setProperty("gold.bridge.owner", bridge);
        catalog.setProperty("gold.bridge.methods", "goldBalance()V,goldInfo()Ljava/lang/String;");
        catalog.setProperty("gold.identity.snapshot.hook", "ExpEstimateBridge.snapshot()V");
        catalog.setProperty("gold.disconnect.hook", "ExpEstimateBridge.disconnect()V");
        catalog.setProperty("gold.pending.initial.wallet", "true");
        catalog.setProperty("gold.tracking.changed.classes",
                receiverOwner + ".class," + bridge + ".class");
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }

    private static int realInstructionIndex(MethodNode method, AbstractInsnNode target) {
        int index = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn == target) return insn.getOpcode() < 0 ? -1 : index;
            if (insn.getOpcode() >= 0) index++;
        }
        return -1;
    }

    private static boolean inherits(Map<String, byte[]> entries, String child, String parent) {
        String current = child;
        Set<String> seen = new HashSet<String>();
        while (current != null && seen.add(current)) {
            if (current.equals(parent)) return true;
            byte[] bytes = entries.get(current + ".class");
            if (bytes == null) return false;
            current = node(bytes).superName;
        }
        return false;
    }

    private static boolean isGoldBalance(MethodInsnNode call, String bridge) {
        return call.getOpcode() == INVOKESTATIC && call.owner.equals(bridge)
                && call.name.equals("goldBalance") && call.desc.equals("()V");
    }

    private static boolean hasMethod(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) if (method.name.equals(name) && method.desc.equals(desc)) return true;
        return false;
    }

    private static boolean hasField(ClassNode owner, String name, String desc) {
        for (FieldNode field : owner.fields) if (field.name.equals(name) && field.desc.equals(desc)) return true;
        return false;
    }

    private static void addField(ClassNode owner, int access, String name, String desc) {
        owner.fields.add(new FieldNode(access, name, desc, null, null));
    }

    private static MethodNode method(ClassNode owner, int access, String name, String desc) {
        MethodNode method = new MethodNode(access, name, desc, null, null);
        owner.methods.add(method);
        return method;
    }

    private static void addGoldSnapshot(ClassNode c, String bridge, String mainOwner,
            String mainField, String mainDesc, String playerOwner, String idField,
            String cache, String api) {
        MethodNode m = method(c, ACC_PUBLIC | ACC_STATIC, "goldSnapshot", "()V");
        Label start = new Label(), end = new Label(), handler = new Label(), ready = new Label();
        m.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
        m.visitCode();
        m.visitLabel(start);
        m.visitFieldInsn(GETSTATIC, mainOwner, mainField, mainDesc);
        m.visitJumpInsn(IFNULL, ready);
        m.visitFieldInsn(GETSTATIC, cache, "INSTANCE", "L" + api + ";");
        m.visitMethodInsn(INVOKEINTERFACE, api, "serverKey", "()Ljava/lang/String;", true);
        m.visitVarInsn(ASTORE, 0);
        m.visitVarInsn(ALOAD, 0);
        m.visitJumpInsn(IFNULL, ready);
        m.visitFieldInsn(GETSTATIC, mainOwner, mainField, mainDesc);
        m.visitFieldInsn(GETFIELD, playerOwner, idField, "I");
        m.visitVarInsn(ISTORE, 1);
        m.visitVarInsn(ALOAD, 0);
        m.visitVarInsn(ILOAD, 1);
        m.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        m.visitMethodInsn(INVOKESTATIC, GOLD, "snapshot", "(Ljava/lang/String;IJ)V", false);
        m.visitInsn(ICONST_1);
        m.visitFieldInsn(PUTSTATIC, bridge, READY, "Z");
        m.visitVarInsn(ILOAD, 1);
        m.visitFieldInsn(PUTSTATIC, bridge, READY_ID, "I");
        m.visitVarInsn(ALOAD, 0);
        m.visitFieldInsn(PUTSTATIC, bridge, READY_SERVER, "Ljava/lang/String;");
        Label noPending = new Label();
        m.visitFieldInsn(GETSTATIC, bridge, PENDING, "Z");
        m.visitJumpInsn(IFEQ, noPending);
        m.visitVarInsn(ALOAD, 0);
        m.visitVarInsn(ILOAD, 1);
        m.visitFieldInsn(GETSTATIC, bridge, PENDING_VALUE, "J");
        m.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        m.visitMethodInsn(INVOKESTATIC, GOLD, "balance", "(Ljava/lang/String;IJJ)V", false);
        m.visitInsn(ICONST_0);
        m.visitFieldInsn(PUTSTATIC, bridge, PENDING, "Z");
        m.visitLabel(noPending);
        m.visitLabel(end);
        m.visitInsn(RETURN);
        m.visitLabel(ready);
        m.visitInsn(ICONST_0);
        m.visitFieldInsn(PUTSTATIC, bridge, READY, "Z");
        m.visitInsn(RETURN);
        m.visitLabel(handler);
        m.visitInsn(POP);
        m.visitInsn(ICONST_0);
        m.visitFieldInsn(PUTSTATIC, bridge, READY, "Z");
        m.visitInsn(RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    private static void addGoldBalance(ClassNode c, String bridge, String mainOwner,
            String mainField, String mainDesc, String playerOwner, String idField,
            String walletField, String walletDesc, String cache, String api) {
        MethodNode m = method(c, ACC_PUBLIC | ACC_STATIC, "goldBalance", "()V");
        Label start = new Label(), end = new Label(), handler = new Label();
        Label capture = new Label(), finish = new Label(), matched = new Label();
        m.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
        m.visitCode();
        m.visitLabel(start);
        m.visitFieldInsn(GETSTATIC, mainOwner, mainField, mainDesc);
        m.visitJumpInsn(IFNULL, finish);
        m.visitFieldInsn(GETSTATIC, cache, "INSTANCE", "L" + api + ";");
        m.visitMethodInsn(INVOKEINTERFACE, api, "serverKey", "()Ljava/lang/String;", true);
        m.visitVarInsn(ASTORE, 0);
        m.visitFieldInsn(GETSTATIC, mainOwner, mainField, mainDesc);
        m.visitFieldInsn(GETFIELD, playerOwner, idField, "I");
        m.visitVarInsn(ISTORE, 1);
        m.visitFieldInsn(GETSTATIC, mainOwner, mainField, mainDesc);
        m.visitFieldInsn(GETFIELD, playerOwner, walletField, walletDesc);
        m.visitVarInsn(LSTORE, 2);

        m.visitVarInsn(ALOAD, 0);
        m.visitJumpInsn(IFNULL, capture);
        m.visitFieldInsn(GETSTATIC, bridge, READY, "Z");
        m.visitJumpInsn(IFEQ, capture);
        m.visitFieldInsn(GETSTATIC, bridge, READY_ID, "I");
        m.visitVarInsn(ILOAD, 1);
        m.visitJumpInsn(IF_ICMPNE, capture);
        m.visitFieldInsn(GETSTATIC, bridge, READY_SERVER, "Ljava/lang/String;");
        m.visitJumpInsn(IFNULL, capture);
        m.visitFieldInsn(GETSTATIC, bridge, READY_SERVER, "Ljava/lang/String;");
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFNE, matched);
        m.visitJumpInsn(GOTO, capture);

        m.visitLabel(matched);
        m.visitVarInsn(ALOAD, 0);
        m.visitVarInsn(ILOAD, 1);
        m.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        m.visitMethodInsn(INVOKESTATIC, GOLD, "snapshot", "(Ljava/lang/String;IJ)V", false);
        m.visitVarInsn(ALOAD, 0);
        m.visitVarInsn(ILOAD, 1);
        m.visitVarInsn(LLOAD, 2);
        m.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        m.visitMethodInsn(INVOKESTATIC, GOLD, "balance", "(Ljava/lang/String;IJJ)V", false);
        m.visitJumpInsn(GOTO, finish);

        m.visitLabel(capture);
        m.visitInsn(ICONST_0);
        m.visitFieldInsn(PUTSTATIC, bridge, READY, "Z");
        m.visitVarInsn(LLOAD, 2);
        m.visitFieldInsn(PUTSTATIC, bridge, PENDING_VALUE, "J");
        m.visitInsn(ICONST_1);
        m.visitFieldInsn(PUTSTATIC, bridge, PENDING, "Z");
        m.visitLabel(finish);
        m.visitInsn(RETURN);
        m.visitLabel(end);
        m.visitLabel(handler);
        m.visitInsn(POP);
        m.visitInsn(RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    private static void addGoldDisconnect(ClassNode c, String bridge) {
        MethodNode m = method(c, ACC_PUBLIC | ACC_STATIC, "goldDisconnect", "()V");
        Label start = new Label(), end = new Label(), handler = new Label();
        m.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
        m.visitCode();
        m.visitInsn(ICONST_0);
        m.visitFieldInsn(PUTSTATIC, bridge, READY, "Z");
        m.visitInsn(ICONST_0);
        m.visitFieldInsn(PUTSTATIC, bridge, READY_ID, "I");
        m.visitInsn(ACONST_NULL);
        m.visitFieldInsn(PUTSTATIC, bridge, READY_SERVER, "Ljava/lang/String;");
        m.visitInsn(ICONST_0);
        m.visitFieldInsn(PUTSTATIC, bridge, PENDING, "Z");
        m.visitInsn(LCONST_0);
        m.visitFieldInsn(PUTSTATIC, bridge, PENDING_VALUE, "J");
        m.visitLabel(start);
        m.visitMethodInsn(INVOKESTATIC, GOLD, "disconnect", "()V", false);
        m.visitLabel(end);
        m.visitInsn(RETURN);
        m.visitLabel(handler);
        m.visitInsn(POP);
        m.visitInsn(RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    private static void addGoldInfo(ClassNode c) {
        MethodNode m = method(c, ACC_PUBLIC | ACC_STATIC, "goldInfo", "()Ljava/lang/String;");
        m.visitCode();
        m.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        m.visitMethodInsn(INVOKESTATIC, GOLD, "info", "(J)Ljava/lang/String;", false);
        m.visitInsn(ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }
}
