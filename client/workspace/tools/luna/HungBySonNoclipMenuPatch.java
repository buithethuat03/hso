import java.util.Properties;

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
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Adds an OFF-by-default Noclip tile filter and native root-menu toggle. */
public final class HungBySonNoclipMenuPatch implements Opcodes {
    private static final String ENABLED = "codexNoclipEnabled";
    private static final String TILE = "codexNoclipTile";
    private static final String TEXT = "codexNoclipText";
    private static final String ITEM = "bt";
    private static final String LIST = "et";
    private static final int ACTION = 106;

    private HungBySonNoclipMenuPatch() { }

    public static void patch(ClassNode menu, Properties tempo) {
        String owner = required(tempo, "candidate.menu.owner");
        require(owner.equals(menu.name), "Noclip patch received the wrong menu class");
        String actionSignature = required(tempo, "candidate.menu.action");
        int actionOpen = actionSignature.indexOf('(');
        require(actionOpen > 0, "candidate.menu.action must include a method descriptor");
        MethodNode handler = method(menu, actionSignature.substring(0, actionOpen),
                actionSignature.substring(actionOpen));
        require(handler != null && "(II)Z".equals(handler.desc), "Native menu action handler is missing");

        String refreshSignature = required(tempo, "candidate.menu.refresh");
        Member refresh = member(refreshSignature);
        require(owner.equals(refresh.owner) && "()V".equals(refresh.descriptor),
                "candidate.menu.refresh must name this menu's static root builder");
        MethodNode rootBuilder = method(menu, refresh.name, refresh.descriptor);
        require(rootBuilder != null && (rootBuilder.access & ACC_STATIC) != 0,
                "Native root menu builder is missing or not static");

        String displaySignature = required(tempo, "candidate.menu.display");
        Member display = member(displaySignature);
        require(hasInvocation(rootBuilder, display), "Native display call is missing from the root menu builder");
        int rootAction = Integer.parseInt(required(tempo, "candidate.menu.rootAction"));
        RootItem root = findRootItem(rootBuilder, rootAction);
        require(!hasActionBranch(handler, ACTION), "Native menu action 106 is already in use");
        require(field(menu, ENABLED, "Z") == null, "Noclip enabled field already exists");
        require(method(menu, TILE, "(I)I") == null && method(menu, TEXT, "()Ljava/lang/String;") == null,
                "Noclip helpers already exist");

        FieldNode state = new FieldNode(ACC_PUBLIC | ACC_STATIC, ENABLED, "Z", null, null);
        menu.fields.add(state);
        menu.methods.add(tileMethod(menu.name));
        menu.methods.add(textMethod(menu.name));
        addRootToggle(rootBuilder, root, menu.name);
        addAction(handler, menu.name, refresh.name);
        tempo.setProperty("candidate.noclip.action", Integer.toString(ACTION));
        tempo.setProperty("candidate.noclip.enabled", ENABLED);
        tempo.setProperty("candidate.noclip.tile", TILE + "(I)I");
        tempo.setProperty("candidate.noclip.text", TEXT + "()Ljava/lang/String;");
    }

    private static MethodNode tileMethod(String owner) {
        MethodNode method = new MethodNode(ASM9, ACC_PUBLIC | ACC_STATIC, TILE, "(I)I", null, null);
        LabelNode keep = new LabelNode();
        LabelNode passthrough = new LabelNode();
        method.instructions.add(new FieldInsnNode(GETSTATIC, owner, ENABLED, "Z"));
        method.instructions.add(new JumpInsnNode(IFEQ, passthrough));
        method.instructions.add(new VarInsnNode(ILOAD, 0));
        method.instructions.add(new InsnNode(ICONST_1));
        method.instructions.add(new JumpInsnNode(IF_ICMPEQ, keep));
        method.instructions.add(new VarInsnNode(ILOAD, 0));
        method.instructions.add(new InsnNode(ICONST_M1));
        method.instructions.add(new JumpInsnNode(IF_ICMPNE, passthrough));
        method.instructions.add(keep);
        method.instructions.add(new InsnNode(ICONST_0));
        method.instructions.add(new InsnNode(IRETURN));
        method.instructions.add(passthrough);
        method.instructions.add(new VarInsnNode(ILOAD, 0));
        method.instructions.add(new InsnNode(IRETURN));
        return method;
    }

    private static MethodNode textMethod(String owner) {
        MethodNode method = new MethodNode(ASM9, ACC_PUBLIC | ACC_STATIC, TEXT,
                "()Ljava/lang/String;", null, null);
        LabelNode enabled = new LabelNode();
        method.instructions.add(new FieldInsnNode(GETSTATIC, owner, ENABLED, "Z"));
        method.instructions.add(new JumpInsnNode(IFNE, enabled));
        method.instructions.add(new LdcInsnNode("Noclip: [OFF]"));
        method.instructions.add(new InsnNode(ARETURN));
        method.instructions.add(enabled);
        method.instructions.add(new LdcInsnNode("Noclip: [ON]"));
        method.instructions.add(new InsnNode(ARETURN));
        return method;
    }

    private static void addRootToggle(MethodNode builder, RootItem root, String owner) {
        InsnList row = new InsnList();
        row.add(new VarInsnNode(ALOAD, root.listLocal));
        row.add(new TypeInsnNode(NEW, ITEM));
        row.add(new InsnNode(DUP));
        row.add(new MethodInsnNode(INVOKESTATIC, owner, TEXT, "()Ljava/lang/String;", false));
        row.add(push(ACTION));
        row.add(new FieldInsnNode(GETSTATIC, root.callback.owner, root.callback.name, root.callback.desc));
        row.add(new MethodInsnNode(INVOKESPECIAL, ITEM, "<init>", root.constructor, false));
        row.add(new MethodInsnNode(INVOKEVIRTUAL, root.add.owner, root.add.name, root.add.desc, false));
        builder.instructions.insert(root.add, row);
    }

    private static void addAction(MethodNode handler, String owner, String refreshMethod) {
        AbstractInsnNode first = firstReal(handler.instructions.getFirst());
        require(first != null, "Native menu action handler is empty");
        LabelNode next = new LabelNode();
        InsnList branch = new InsnList();
        branch.add(new VarInsnNode(ILOAD, 0));
        branch.add(push(ACTION));
        branch.add(new JumpInsnNode(IF_ICMPNE, next));
        branch.add(new FieldInsnNode(GETSTATIC, owner, ENABLED, "Z"));
        branch.add(new InsnNode(ICONST_1));
        branch.add(new InsnNode(IXOR));
        branch.add(new FieldInsnNode(PUTSTATIC, owner, ENABLED, "Z"));
        branch.add(new MethodInsnNode(INVOKESTATIC, owner, refreshMethod, "()V", false));
        branch.add(new InsnNode(ICONST_1));
        branch.add(new InsnNode(IRETURN));
        branch.add(next);
        handler.instructions.insertBefore(first, branch);
    }

    private static RootItem findRootItem(MethodNode builder, int rootAction) {
        RootItem found = null;
        for (AbstractInsnNode instruction = builder.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!isInt(instruction, rootAction)) continue;
            AbstractInsnNode callbackNode = nextReal(instruction);
            if (!(callbackNode instanceof FieldInsnNode) || callbackNode.getOpcode() != GETSTATIC) continue;
            FieldInsnNode callback = (FieldInsnNode)callbackNode;
            if (!callback.desc.startsWith("L") || !callback.desc.endsWith(";")) continue;
            AbstractInsnNode ctorNode = nextReal(callbackNode);
            if (!(ctorNode instanceof MethodInsnNode) || ctorNode.getOpcode() != INVOKESPECIAL) continue;
            MethodInsnNode ctor = (MethodInsnNode)ctorNode;
            if (!ITEM.equals(ctor.owner) || !"<init>".equals(ctor.name)
                    || !ctor.desc.startsWith("(Ljava/lang/String;I") || !ctor.desc.endsWith(")V")) continue;

            TypeInsnNode allocation = null;
            for (AbstractInsnNode before = ctorNode.getPrevious(); before != null; before = before.getPrevious()) {
                if (before instanceof TypeInsnNode && before.getOpcode() == NEW
                        && ITEM.equals(((TypeInsnNode)before).desc)) {
                    allocation = (TypeInsnNode)before;
                    break;
                }
            }
            require(allocation != null, "Native root row allocation missing");
            AbstractInsnNode listLoad = previousReal(allocation);
            require(listLoad instanceof VarInsnNode && listLoad.getOpcode() == ALOAD,
                    "Cannot identify the native root menu list local");
            AbstractInsnNode addNode = nextReal(ctorNode);
            require(addNode instanceof MethodInsnNode && addNode.getOpcode() == INVOKEVIRTUAL
                    && LIST.equals(((MethodInsnNode)addNode).owner)
                    && "(Ljava/lang/Object;)V".equals(((MethodInsnNode)addNode).desc),
                    "Native root menu add call missing after tempo row");
            RootItem candidate = new RootItem();
            candidate.callback = callback;
            candidate.constructor = ctor.desc;
            candidate.listLocal = ((VarInsnNode)listLoad).var;
            candidate.add = (MethodInsnNode)addNode;
            if (found != null) throw new IllegalStateException("Multiple native root rows use action " + rootAction);
            found = candidate;
        }
        require(found != null, "Native tempo root row missing");
        return found;
    }

    private static boolean hasActionBranch(MethodNode method, int action) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!isInt(instruction, action)) continue;
            AbstractInsnNode before = previousReal(instruction);
            AbstractInsnNode after = nextReal(instruction);
            if (before instanceof VarInsnNode && before.getOpcode() == ILOAD
                    && ((VarInsnNode)before).var == 0 && after instanceof JumpInsnNode
                    && isIntegerCompare(after.getOpcode())) return true;
        }
        return false;
    }

    private static boolean isIntegerCompare(int opcode) {
        return opcode == IF_ICMPEQ || opcode == IF_ICMPNE || opcode == IF_ICMPLT
                || opcode == IF_ICMPGE || opcode == IF_ICMPGT || opcode == IF_ICMPLE;
    }

    private static boolean hasInvocation(MethodNode method, Member target) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.owner.equals(target.owner) && call.name.equals(target.name)
                    && call.desc.equals(target.descriptor)) return true;
        }
        return false;
    }

    private static Member member(String signature) {
        int descriptor = signature.indexOf('(');
        int dot = signature.lastIndexOf('.', descriptor);
        require(descriptor > 0 && dot > 0 && dot < descriptor - 1,
                "Invalid owner.method descriptor: " + signature);
        return new Member(signature.substring(0, dot), signature.substring(dot + 1, descriptor),
                signature.substring(descriptor));
    }

    private static FieldNode field(ClassNode owner, String name, String descriptor) {
        for (FieldNode field : owner.fields)
            if (name.equals(field.name) && descriptor.equals(field.desc)) return field;
        return null;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        for (MethodNode method : owner.methods)
            if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        return null;
    }

    private static AbstractInsnNode firstReal(AbstractInsnNode instruction) {
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
        return instruction;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode instruction) {
        return firstReal(instruction == null ? null : instruction.getNext());
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode instruction) {
        instruction = instruction == null ? null : instruction.getPrevious();
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getPrevious();
        return instruction;
    }

    private static boolean isInt(AbstractInsnNode instruction, int value) {
        int opcode = instruction.getOpcode();
        if (opcode >= ICONST_M1 && opcode <= ICONST_5) return opcode - ICONST_0 == value;
        if (instruction instanceof IntInsnNode) return ((IntInsnNode)instruction).operand == value;
        return instruction instanceof LdcInsnNode && ((LdcInsnNode)instruction).cst instanceof Integer
                && ((Integer)((LdcInsnNode)instruction).cst).intValue() == value;
    }

    private static AbstractInsnNode push(int value) {
        if (value >= -1 && value <= 5) return new InsnNode(ICONST_0 + value);
        return new IntInsnNode(value >= -128 && value <= 127 ? BIPUSH : SIPUSH, value);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        require(value != null && value.trim().length() > 0, "Missing tempo catalog entry " + key);
        return value.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Member {
        final String owner, name, descriptor;
        Member(String owner, String name, String descriptor) {
            this.owner = owner; this.name = name; this.descriptor = descriptor;
        }
    }

    private static final class RootItem {
        FieldInsnNode callback;
        MethodInsnNode add;
        String constructor;
        int listLocal;
    }
}
