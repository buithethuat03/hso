import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
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
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Thêm vào client master: chết thì về làng ngay rồi tự đi lên phía bắc, bật tắt
 * bằng cách chat "0" và "1".
 *
 * Hai hook vào game:
 *   1. hàm gửi chat  -> HSOReviveSupport.onChat, trả true thì chặn luôn không gửi
 *   2. ctor packet nhận -> HSOReviveSupport.onRecv, để soi menu server gửi xuống
 *
 * Mọi lời gọi ngược vào game đi qua HSOReviveBridge, một class do patcher tự
 * sinh bằng ASM vì tên method trong jar bị obfuscate nên support không viết cứng
 * được. Support gọi bridge qua reflection.
 *
 * Chạy: java -cp ".;asm-9.7.jar;asm-tree-9.7.jar" HSOReviveNorthPatcher <input.jar> <output.jar>
 */
public final class HSOReviveNorthPatcher implements Opcodes {
    private static final String SUPPORT = "HSOReviveSupport";
    private static final String BRIDGE = "HSOReviveBridge";
    private static final String BRIDGE_API = "HSOBridgeApi";
    private static final String INTS_METHOD = "hsoInts";
    private static final char NEWLINE = 10;

    private static final Map<String, byte[]> ENTRIES = new LinkedHashMap<String, byte[]>();
    private static final Map<String, Long> TIMES = new LinkedHashMap<String, Long>();

    private static ClassNode sender;
    private static MethodNode chatMethod;
    private static MethodNode menuReplyMethod;
    private static MethodNode moveMethod;
    private static MethodNode reviveMethod;
    private static MethodNode senderGetter;

    private static ClassNode playerClass;
    private static ClassNode baseClass;
    private static String holderOwner;
    private static String holderField;
    private static String xField;
    private static String yField;
    private static String hpField;

    private static ClassNode canvasClass;
    private static String controllerOwner;
    private static MethodInsnNode pointerPressCall;
    private static MethodInsnNode pointerReleaseCall;
    private static List<String> intFields;
    private static String widthField;
    private static String heightField;

    private static ClassNode packetClass;
    private static String opcodeField;
    private static MethodNode packetCtor;

    private static int supportClasses;
    private static int patches;

    private HSOReviveNorthPatcher() {
    }

    public static void main(String[] args) throws Exception {
        require(args.length >= 2, "Cần: <input.jar> <output.jar>");
        File output = new File(args[1]);
        require(!output.exists(), "Không ghi đè file đã có: " + output.getPath());

        readJar(new File(args[0]));
        discoverSender();
        discoverPlayer();
        discoverPacket();
        discoverCanvas();

        addIntsAccessor();
        patchChat();
        patchPacketCtor();
        ENTRIES.put(BRIDGE + ".class", buildBridge());
        addSupportClasses();

        require(patches == 2, "Cần 2 hook, đã áp " + patches);
        writeJar(output);
        verifyOutput(output);
        writeMap(new File("hso_revive_map.txt"));

        System.out.println("[+] Đã tạo " + args[1] + " từ " + args[0]);
        System.out.println("    sender      : " + shortName(sender.name));
        System.out.println("      chat      : " + shortName(chatMethod.name) + chatMethod.desc);
        System.out.println("      menu reply: " + shortName(menuReplyMethod.name) + menuReplyMethod.desc);
        System.out.println("      di chuyển : " + shortName(moveMethod.name) + moveMethod.desc);
        System.out.println("    player      : " + shortName(playerClass.name)
                + "  x=" + shortName(xField) + " y=" + shortName(yField)
                + " hp=" + shortName(hpField));
        System.out.println("    canvas      : " + shortName(canvasClass.name)
                + "  w=" + shortName(widthField) + " h=" + shortName(heightField));
        System.out.println("      về làng  : " + shortName(reviveMethod.name) + "(0)  tức opcode 31 payload 00");
        System.out.println("    biến tĩnh   : " + shortName(holderOwner) + "." + shortName(holderField));
        System.out.println("    support     : " + supportClasses + " class + bridge");
        System.out.println();
        System.out.println("    Trong game: chat \"0\" để bật, \"1\" để tắt. Log ở hso_revive.log");
    }

    // ------------------------------------------------------------------ tìm kiếm

    /** Ba packet cần dùng, nhận diện bằng chữ ký cộng với hằng opcode trong thân hàm. */
    private static void discoverSender() {
        List<ClassNode> owners = new ArrayList<ClassNode>();
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode owner = readClass(entry.getValue());
            MethodNode chat = null;
            MethodNode reply = null;
            MethodNode move = null;
            for (MethodNode method : owner.methods) {
                if ("(Ljava/lang/String;)V".equals(method.desc) && containsInt(method, 27)) chat = method;
                if ("(SBB)V".equals(method.desc) && containsInt(method, -30)) reply = method;
                if ("(SS)V".equals(method.desc) && containsInt(method, 4)) move = method;
            }
            if (chat != null && reply != null && move != null) {
                owners.add(owner);
                chatMethod = chat;
                menuReplyMethod = reply;
                moveMethod = move;
            }
        }
        require(owners.size() == 1, "Cần đúng một class gửi packet, thấy " + owners.size());
        sender = owners.get(0);
        require((chatMethod.access & ACC_STATIC) == 0, "Hàm chat phải là method thường");

        List<MethodNode> getters = new ArrayList<MethodNode>();
        for (MethodNode method : sender.methods) {
            if ((method.access & ACC_STATIC) != 0 && ("()L" + sender.name + ";").equals(method.desc)) {
                getters.add(method);
            }
        }
        List<MethodNode> revives = new ArrayList<MethodNode>();
        for (MethodNode method : sender.methods) {
            if ("(B)V".equals(method.desc) && containsInt(method, 31)) revives.add(method);
        }
        require(revives.size() == 1, "Cần đúng một hàm gửi opcode 31, thấy " + revives.size());
        reviveMethod = revives.get(0);

        require(!getters.isEmpty(), "Không thấy hàm lấy singleton của sender");
        senderGetter = getters.get(0);
        for (MethodNode method : getters) {
            if (method.instructions.size() > senderGetter.instructions.size()) {
                senderGetter = method;
            }
        }
    }

    /** Nhân vật của mình, cùng hai field toạ độ nằm trên class cha. */
    private static void discoverPlayer() {
        Map<String, ClassNode> all = new LinkedHashMap<String, ClassNode>();
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode node = readClass(entry.getValue());
            all.put(node.name, node);
        }
        for (ClassNode node : all.values()) {
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof LdcInsnNode)) continue;
                    Object constant = ((LdcInsnNode) insn).cst;
                    if (constant instanceof String && ((String) constant).indexOf("java.game.Player vec") >= 0) {
                        playerClass = node;
                    }
                }
            }
        }
        require(playerClass != null, "Không thấy class nhân vật");
        baseClass = all.get(playerClass.superName);
        require(baseClass != null, "Không nạp được class cha của nhân vật");

        List<String> holders = new ArrayList<String>();
        for (ClassNode node : all.values()) {
            for (FieldNode field : node.fields) {
                if ((field.access & ACC_STATIC) != 0 && ("L" + playerClass.name + ";").equals(field.desc)) {
                    holders.add(node.name + " " + field.name);
                }
            }
        }
        require(holders.size() == 1, "Cần đúng một biến tĩnh giữ nhân vật, thấy " + holders.size());
        holderOwner = holders.get(0).split(" ")[0];
        holderField = holders.get(0).split(" ")[1];

        // Vật phẩm rơi dùng chung class cha; constructor của nó gán x, y từ tham số 4 và 5.
        MethodNode ctor = null;
        for (ClassNode node : all.values()) {
            if (!baseClass.name.equals(node.superName)) continue;
            MethodNode candidate = findMethod(node, "<init>", "(IBLjava/lang/String;IISB)V");
            if (candidate != null) {
                require(ctor == null, "Nhiều class vật phẩm rơi");
                ctor = candidate;
            }
        }
        require(ctor != null, "Không thấy class vật phẩm rơi để map x/y");
        for (AbstractInsnNode insn = ctor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != PUTFIELD) continue;
            FieldInsnNode field = (FieldInsnNode) insn;
            if (!baseClass.name.equals(field.owner) || !"I".equals(field.desc)) continue;
            AbstractInsnNode previous = previousReal(insn);
            if (!(previous instanceof VarInsnNode) || previous.getOpcode() != ILOAD) continue;
            int local = ((VarInsnNode) previous).var;
            if (local == 4) xField = field.name;
            if (local == 5) yField = field.name;
        }
        require(xField != null && yField != null, "Không map được x/y: " + xField + "/" + yField);
        discoverHp(all);

        intFields = new ArrayList<String>();
        for (FieldNode field : baseClass.fields) {
            if ("I".equals(field.desc) && (field.access & ACC_STATIC) == 0) {
                intFields.add(field.name);
            }
        }
    }

    /**
     * Chèn vào chính class cha một hàm đọc mọi field int của nó. Phải nằm trong
     * class đó vì nhiều field là private, class sinh ngoài không có quyền đọc.
     */
    private static void addIntsAccessor() {
        ClassNode base = readClass(ENTRIES.get(baseClass.name + ".class"));
        require(findMethod(base, INTS_METHOD, "()[I") == null, "Class cha đã có " + INTS_METHOD);
        MethodNode method = new MethodNode(ACC_PUBLIC, INTS_METHOD, "()[I", null, null);
        InsnList body = method.instructions;
        body.add(new LdcInsnNode(Integer.valueOf(intFields.size())));
        body.add(new IntInsnNode(NEWARRAY, T_INT));
        body.add(new VarInsnNode(ASTORE, 1));
        for (int i = 0; i < intFields.size(); i++) {
            body.add(new VarInsnNode(ALOAD, 1));
            body.add(new LdcInsnNode(Integer.valueOf(i)));
            body.add(new VarInsnNode(ALOAD, 0));
            body.add(new FieldInsnNode(GETFIELD, baseClass.name, intFields.get(i), "I"));
            body.add(new InsnNode(IASTORE));
        }
        body.add(new VarInsnNode(ALOAD, 1));
        body.add(new InsnNode(ARETURN));
        method.maxStack = 4;
        method.maxLocals = 2;
        base.methods.add(method);
        ENTRIES.put(baseClass.name + ".class", write(base));
    }

    /**
     * HP hiện tại: field int trên class cha vừa được ghi từ readInt() của packet,
     * vừa bị so với 0 rất nhiều lần vì client kiểm tra chết ở khắp nơi.
     */
    private static void discoverHp(Map<String, ClassNode> all) {
        Map<String, Integer> fromPacket = new LinkedHashMap<String, Integer>();
        Map<String, Integer> zeroChecks = new LinkedHashMap<String, Integer>();
        for (ClassNode node : all.values()) {
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof FieldInsnNode)) continue;
                    FieldInsnNode field = (FieldInsnNode) insn;
                    if (!baseClass.name.equals(field.owner) || !"I".equals(field.desc)) continue;
                    if (insn.getOpcode() == PUTFIELD) {
                        AbstractInsnNode previous = previousReal(insn);
                        if (previous instanceof MethodInsnNode
                                && "readInt".equals(((MethodInsnNode) previous).name)) {
                            bump(fromPacket, field.name);
                        }
                    } else if (insn.getOpcode() == GETFIELD) {
                        AbstractInsnNode next = insn.getNext();
                        while (next != null && next.getOpcode() < 0) next = next.getNext();
                        if (next != null && (next.getOpcode() == IFLE || next.getOpcode() == IFGT
                                || next.getOpcode() == IFNE || next.getOpcode() == IFEQ)) {
                            bump(zeroChecks, field.name);
                        }
                    }
                }
            }
        }
        String best = null;
        int bestScore = 0;
        int runnerUp = 0;
        for (String name : fromPacket.keySet()) {
            Integer checks = zeroChecks.get(name);
            int score = checks == null ? 0 : checks.intValue();
            if (score > bestScore) {
                runnerUp = bestScore;
                bestScore = score;
                best = name;
            } else if (score > runnerUp) {
                runnerUp = score;
            }
        }
        require(best != null && bestScore >= 10 && bestScore >= runnerUp * 3,
                "Không chắc field HP: điểm " + bestScore + " so với " + runnerUp);
        hpField = best;
    }

    private static void bump(Map<String, Integer> counter, String key) {
        Integer current = counter.get(key);
        counter.put(key, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
    }

    /**
     * Canvas của game: từ pointerPressed và pointerReleased lấy ra hai hàm static
     * xử lý chạm, và từ constructor lấy hai biến tĩnh chiều rộng, chiều cao.
     */
    private static void discoverCanvas() {
        List<ClassNode> found = new ArrayList<ClassNode>();
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode owner = readClass(entry.getValue());
            if ("javax/microedition/lcdui/Canvas".equals(owner.superName)) found.add(owner);
        }
        require(found.size() == 1, "Cần đúng một class Canvas, thấy " + found.size());
        canvasClass = found.get(0);

        pointerPressCall = soleStaticCall(canvasClass, "pointerPressed", "(II)V");
        pointerReleaseCall = soleStaticCall(canvasClass, "pointerReleased", "(II)V");
        require(pointerPressCall != null && pointerReleaseCall != null,
                "Không thấy hai hàm static xử lý chạm");
        controllerOwner = pointerPressCall.owner;
        require(controllerOwner.equals(pointerReleaseCall.owner),
                "Hai handler chạm không cùng một class");

        MethodNode ctor = findMethod(canvasClass, "<init>", "()V");
        require(ctor != null, "Canvas không có constructor rỗng");
        for (AbstractInsnNode insn = ctor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            String name = ((MethodInsnNode) insn).name;
            if (!"getHeight".equals(name) && !"getWidth".equals(name)) continue;
            AbstractInsnNode next = insn.getNext();
            while (next != null && next.getOpcode() < 0) next = next.getNext();
            if (!(next instanceof FieldInsnNode) || next.getOpcode() != PUTSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode) next;
            if (!canvasClass.name.equals(field.owner) || !"I".equals(field.desc)) continue;
            if ("getHeight".equals(name)) heightField = field.name;
            if ("getWidth".equals(name)) widthField = field.name;
        }
        require(widthField != null && heightField != null,
                "Không map được chiều rộng, chiều cao: " + widthField + "/" + heightField);
    }

    /** Lời gọi static duy nhất trong thân một method của canvas. */
    private static MethodInsnNode soleStaticCall(ClassNode owner, String name, String desc) {
        MethodNode method = findMethod(owner, name, desc);
        if (method == null) return null;
        MethodInsnNode found = null;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != INVOKESTATIC) continue;
            if (found != null) return null;
            found = (MethodInsnNode) insn;
        }
        return found;
    }

    /** Packet nhận: constructor (opcode, payload) là chỗ duy nhất reader dựng packet. */
    private static void discoverPacket() {
        List<ClassNode> found = new ArrayList<ClassNode>();
        for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode owner = readClass(entry.getValue());
            if (findMethod(owner, "<init>", "(B[B)V") == null) continue;
            if (findMethod(owner, "<init>", "(B)V") == null) continue;
            MethodNode payload = soleMethod(owner, "()[B");
            if (payload == null || !calls(payload, "java/io/ByteArrayOutputStream", "toByteArray", "()[B")) continue;
            found.add(owner);
        }
        require(found.size() == 1, "Cần đúng một class packet, thấy " + found.size());
        packetClass = found.get(0);
        packetCtor = findMethod(packetClass, "<init>", "(B[B)V");

        List<String> opcodes = new ArrayList<String>();
        for (FieldNode field : packetClass.fields) {
            if ("B".equals(field.desc) && (field.access & ACC_STATIC) == 0) opcodes.add(field.name);
        }
        require(opcodes.size() == 1, "Cần đúng một field opcode, thấy " + opcodes.size());
        opcodeField = opcodes.get(0);
    }

    // -------------------------------------------------------------------- vá code

    /** Chat "0" hoặc "1" thành lệnh: xử lý xong thì return luôn, không gửi lên server. */
    private static void patchChat() {
        LabelNode carryOn = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, 1));
        hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "onChat", "(Ljava/lang/String;)Z", false));
        hook.add(new JumpInsnNode(IFEQ, carryOn));
        hook.add(new InsnNode(RETURN));
        hook.add(carryOn);
        chatMethod.instructions.insert(hook);
        replaceMethod(sender, chatMethod);
        ++patches;
    }

    private static void patchPacketCtor() {
        List<AbstractInsnNode> returns = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn = packetCtor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == RETURN) returns.add(insn);
        }
        require(!returns.isEmpty(), "Ctor packet không có return");
        for (AbstractInsnNode ret : returns) {
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(ALOAD, 0));
            hook.add(new FieldInsnNode(GETFIELD, packetClass.name, opcodeField, "B"));
            hook.add(new VarInsnNode(ALOAD, 2));
            hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "onRecv", "(I[B)V", false));
            packetCtor.instructions.insertBefore(ret, hook);
        }
        replaceMethod(packetClass, packetCtor);
        ++patches;
    }

    /**
     * Sinh HSOReviveBridge: bốn hàm tĩnh gọi thẳng vào game, vì tên method trong
     * jar bị obfuscate nên không thể viết sẵn trong file nguồn.
     */
    private static byte[] buildBridge() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_6, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, BRIDGE, null, "java/lang/Object",
                new String[] { BRIDGE_API });

        MethodVisitor ctor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor reply = writer.visitMethod(ACC_PUBLIC, "menuReply", "(III)V", null, null);
        reply.visitCode();
        reply.visitMethodInsn(INVOKESTATIC, sender.name, senderGetter.name, senderGetter.desc, false);
        reply.visitVarInsn(ILOAD, 1);
        reply.visitInsn(I2S);
        reply.visitVarInsn(ILOAD, 2);
        reply.visitInsn(I2B);
        reply.visitVarInsn(ILOAD, 3);
        reply.visitInsn(I2B);
        reply.visitMethodInsn(INVOKEVIRTUAL, sender.name, menuReplyMethod.name, menuReplyMethod.desc, false);
        reply.visitInsn(RETURN);
        reply.visitMaxs(0, 0);
        reply.visitEnd();

        MethodVisitor move = writer.visitMethod(ACC_PUBLIC, "move", "(II)V", null, null);
        move.visitCode();
        move.visitMethodInsn(INVOKESTATIC, sender.name, senderGetter.name, senderGetter.desc, false);
        move.visitVarInsn(ILOAD, 1);
        move.visitInsn(I2S);
        move.visitVarInsn(ILOAD, 2);
        move.visitInsn(I2S);
        move.visitMethodInsn(INVOKEVIRTUAL, sender.name, moveMethod.name, moveMethod.desc, false);
        move.visitInsn(RETURN);
        move.visitMaxs(0, 0);
        move.visitEnd();

        MethodVisitor revive = writer.visitMethod(ACC_PUBLIC, "revive", "()V", null, null);
        revive.visitCode();
        revive.visitMethodInsn(INVOKESTATIC, sender.name, senderGetter.name, senderGetter.desc, false);
        revive.visitInsn(ICONST_0);
        revive.visitMethodInsn(INVOKEVIRTUAL, sender.name, reviveMethod.name, reviveMethod.desc, false);
        revive.visitInsn(RETURN);
        revive.visitMaxs(0, 0);
        revive.visitEnd();

        MethodVisitor tap = writer.visitMethod(ACC_PUBLIC, "tap", "(II)V", null, null);
        tap.visitCode();
        tap.visitVarInsn(ILOAD, 1);
        tap.visitVarInsn(ILOAD, 2);
        tap.visitMethodInsn(INVOKESTATIC, controllerOwner, pointerPressCall.name, pointerPressCall.desc, false);
        tap.visitVarInsn(ILOAD, 1);
        tap.visitVarInsn(ILOAD, 2);
        tap.visitMethodInsn(INVOKESTATIC, controllerOwner, pointerReleaseCall.name, pointerReleaseCall.desc, false);
        tap.visitInsn(RETURN);
        tap.visitMaxs(0, 0);
        tap.visitEnd();

        screenSize(writer, "screenWidth", widthField);
        screenSize(writer, "screenHeight", heightField);

        MethodVisitor place = writer.visitMethod(ACC_PUBLIC, "setPosition", "(II)V", null, null);
        org.objectweb.asm.Label none = new org.objectweb.asm.Label();
        place.visitCode();
        place.visitFieldInsn(GETSTATIC, holderOwner, holderField, "L" + playerClass.name + ";");
        place.visitVarInsn(ASTORE, 3);
        place.visitVarInsn(ALOAD, 3);
        place.visitJumpInsn(IFNULL, none);
        place.visitVarInsn(ALOAD, 3);
        place.visitVarInsn(ILOAD, 1);
        place.visitFieldInsn(PUTFIELD, baseClass.name, xField, "I");
        place.visitVarInsn(ALOAD, 3);
        place.visitVarInsn(ILOAD, 2);
        place.visitFieldInsn(PUTFIELD, baseClass.name, yField, "I");
        place.visitLabel(none);
        place.visitInsn(RETURN);
        place.visitMaxs(0, 0);
        place.visitEnd();

        buildInts(writer);

        coordinate(writer, "px", xField);
        coordinate(writer, "py", yField);
        coordinate(writer, "hp", hpField);

        writer.visitEnd();
        return writer.toByteArray();
    }

    /** int[] ints(): gọi hàm đã chèn vào class cha, nên đọc được cả field private. */
    private static void buildInts(ClassWriter writer) {
        MethodVisitor visitor = writer.visitMethod(ACC_PUBLIC, "ints", "()[I", null, null);
        org.objectweb.asm.Label absent = new org.objectweb.asm.Label();
        visitor.visitCode();
        visitor.visitFieldInsn(GETSTATIC, holderOwner, holderField, "L" + playerClass.name + ";");
        visitor.visitVarInsn(ASTORE, 1);
        visitor.visitVarInsn(ALOAD, 1);
        visitor.visitJumpInsn(IFNULL, absent);
        visitor.visitVarInsn(ALOAD, 1);
        visitor.visitMethodInsn(INVOKEVIRTUAL, baseClass.name, INTS_METHOD, "()[I", false);
        visitor.visitInsn(ARETURN);
        visitor.visitLabel(absent);
        visitor.visitInsn(ICONST_0);
        visitor.visitIntInsn(NEWARRAY, T_INT);
        visitor.visitInsn(ARETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    private static void screenSize(ClassWriter writer, String name, String field) {
        MethodVisitor visitor = writer.visitMethod(ACC_PUBLIC, name, "()I", null, null);
        visitor.visitCode();
        visitor.visitFieldInsn(GETSTATIC, canvasClass.name, field, "I");
        visitor.visitInsn(IRETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    /** Đọc toạ độ nhân vật, trả Integer.MIN_VALUE khi chưa vào game. */
    private static void coordinate(ClassWriter writer, String name, String field) {
        MethodVisitor visitor = writer.visitMethod(ACC_PUBLIC, name, "()I", null, null);
        org.objectweb.asm.Label absent = new org.objectweb.asm.Label();
        visitor.visitCode();
        visitor.visitFieldInsn(GETSTATIC, holderOwner, holderField, "L" + playerClass.name + ";");
        visitor.visitVarInsn(ASTORE, 1);
        visitor.visitVarInsn(ALOAD, 1);
        visitor.visitJumpInsn(IFNULL, absent);
        visitor.visitVarInsn(ALOAD, 1);
        visitor.visitFieldInsn(GETFIELD, baseClass.name, field, "I");
        visitor.visitInsn(IRETURN);
        visitor.visitLabel(absent);
        visitor.visitLdcInsn(Integer.valueOf(Integer.MIN_VALUE));
        visitor.visitInsn(IRETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    private static void addSupportClasses() throws IOException {
        byte[] main = readAll(new FileInputStream(SUPPORT + ".class"));
        ClassNode node = readClass(main);
        require(SUPPORT.equals(node.name), "Support phải ở package mặc định");
        require(findMethod(node, "onChat", "(Ljava/lang/String;)Z") != null, "Support thiếu onChat");
        require(findMethod(node, "onRecv", "(I[B)V") != null, "Support thiếu onRecv");
        require(node.version <= 51, "Support biên dịch bằng bản Java quá mới: " + node.version);
        ENTRIES.put(SUPPORT + ".class", main);
        supportClasses = 1;
        ENTRIES.put(BRIDGE_API + ".class", readAll(new FileInputStream(BRIDGE_API + ".class")));

        int nested = 0;
        if (node.innerClasses != null) {
            for (Object entry : node.innerClasses) {
                String name = ((org.objectweb.asm.tree.InnerClassNode) entry).name;
                if (name.startsWith(SUPPORT + "$")) ++nested;
            }
        }
        File[] files = new File(".").listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (!name.startsWith(SUPPORT + "$") || !name.endsWith(".class")) continue;
                ENTRIES.put(name, readAll(new FileInputStream(file)));
                ++supportClasses;
            }
        }
        require(supportClasses == nested + 1, "Thấy " + supportClasses
                + " file support nhưng class chính khai báo " + nested + " class con");
    }

    /** Tên các class và field đã tìm được, để test và công cụ khác dùng lại. */
    private static void writeMap(File file) throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("player.class=").append(playerClass.name).append(NEWLINE);
        text.append("player.base=").append(baseClass.name).append(NEWLINE);
        text.append("player.holderOwner=").append(holderOwner).append(NEWLINE);
        text.append("player.holderField=").append(holderField).append(NEWLINE);
        text.append("player.xField=").append(xField).append(NEWLINE);
        text.append("player.yField=").append(yField).append(NEWLINE);
        text.append("player.hpField=").append(hpField).append(NEWLINE);
        text.append("sender.class=").append(sender.name).append(NEWLINE);
        text.append("sender.revive=").append(reviveMethod.name).append(NEWLINE);
        text.append("canvas.class=").append(canvasClass.name).append(NEWLINE);
        text.append("canvas.width=").append(widthField).append(NEWLINE);
        text.append("canvas.height=").append(heightField).append(NEWLINE);
        text.append("controller.class=").append(controllerOwner).append(NEWLINE);
        for (int i = 0; i < intFields.size(); i++) {
            text.append("int.").append(i).append('=').append(intFields.get(i)).append(NEWLINE);
        }
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(text.toString().getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    // ------------------------------------------------------------------- tiện ích

    private static void replaceMethod(ClassNode owner, MethodNode method) {
        ClassNode fresh = readClass(ENTRIES.get(owner.name + ".class"));
        for (int i = 0; i < fresh.methods.size(); i++) {
            MethodNode existing = fresh.methods.get(i);
            if (existing.name.equals(method.name) && existing.desc.equals(method.desc)) {
                fresh.methods.set(i, method);
                ENTRIES.put(owner.name + ".class", write(fresh));
                return;
            }
        }
        throw new IllegalStateException("Mất method " + method.name + method.desc);
    }

    private static void readJar(File file) throws IOException {
        require(file.isFile(), "Không thấy jar đầu vào: " + file.getPath());
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

    private static void writeJar(File output) throws IOException {
        File temp = new File(output.getPath() + ".tmp");
        JarOutputStream out = new JarOutputStream(new FileOutputStream(temp));
        try {
            for (Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
                String name = entry.getKey();
                if (name.startsWith("META-INF/")
                        && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"))) continue;
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

    private static void verifyOutput(File output) throws IOException {
        JarFile jar = new JarFile(output);
        try {
            require(jar.getEntry("META-INF/MANIFEST.MF") != null, "Jar ra mất manifest");
            require(jar.getEntry(BRIDGE + ".class") != null, "Jar ra thiếu bridge");
            require(jar.getEntry(BRIDGE_API + ".class") != null, "Jar ra thiếu interface bridge");
            int onChat = 0;
            int onRecv = 0;
            int inJar = 0;
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(SUPPORT) && name.endsWith(".class")) ++inJar;
                if (!name.endsWith(".class")) continue;
                byte[] bytes = readAll(jar.getInputStream(entry));
                new ClassReader(bytes);
                ClassNode owner = readClass(bytes);
                for (MethodNode method : owner.methods) {
                    for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (!(insn instanceof MethodInsnNode)) continue;
                        MethodInsnNode call = (MethodInsnNode) insn;
                        if (!SUPPORT.equals(call.owner)) continue;
                        if ("onChat".equals(call.name)) ++onChat;
                        if ("onRecv".equals(call.name)) ++onRecv;
                    }
                }
            }
            require(onChat == 1, "Số hook onChat là " + onChat);
            require(onRecv >= 1, "Số hook onRecv là " + onRecv);
            require(inJar == supportClasses, "Jar ra có " + inJar + " support class, cần " + supportClasses);
        } finally {
            jar.close();
        }
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode findMethod(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        return null;
    }

    private static MethodNode soleMethod(ClassNode owner, String desc) {
        MethodNode found = null;
        for (MethodNode method : owner.methods) {
            if (!desc.equals(method.desc) || "<init>".equals(method.name)) continue;
            if (found != null) return null;
            found = method;
        }
        return found;
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
            if (expected >= -1 && expected <= 5) {
                if (insn.getOpcode() == ICONST_0 + expected) return true;
            }
            if (insn instanceof IntInsnNode && ((IntInsnNode) insn).operand == expected) return true;
            if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer
                    && ((Integer) ((LdcInsnNode) insn).cst).intValue() == expected) return true;
        }
        return false;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getPrevious();
        return cursor;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static String shortName(String value) {
        return value.length() <= 16 ? value : value.substring(0, 7) + "…" + value.substring(value.length() - 7);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
