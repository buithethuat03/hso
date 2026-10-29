import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ClassNode;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reads the packet opcode passed to a sender method's start-message helper. */
public final class SenderOpcodeReader {

    private SenderOpcodeReader() { }

    public static Map<String, Integer> read(ClassNode sender) {
        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        if (sender == null || sender.methods == null) return result;
        for (MethodNode method : sender.methods) {
            if (method.instructions == null) continue;
            Integer opcode = firstByteConstantBeforeStartMessage(method);
            if (opcode != null) result.put(method.name + method.desc, opcode);
        }
        return result;
    }

    /**
     * The obfuscated sender consistently starts a packet with a call taking
     * one byte. The opcode is the integer constant immediately before that
     * call; packet payload writes follow it.
     */
    private static Integer firstByteConstantBeforeStartMessage(MethodNode method) {
        for (AbstractInsnNode n = method.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) n;
            if (!"(B)V".equals(call.desc)) continue;
            AbstractInsnNode previous = n.getPrevious();
            while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
            Integer value = integerConstant(previous);
            if (value != null && value.intValue() >= -128 && value.intValue() <= 255) {
                return value;
            }
        }
        return null;
    }

    private static Integer integerConstant(AbstractInsnNode node) {
        if (node == null) return null;
        if (node instanceof IntInsnNode) return Integer.valueOf(((IntInsnNode) node).operand);
        if (node instanceof LdcInsnNode && ((LdcInsnNode) node).cst instanceof Integer) {
            return (Integer) ((LdcInsnNode) node).cst;
        }
        if (!(node instanceof InsnNode)) return null;
        switch (node.getOpcode()) {
            case Opcodes.ICONST_M1: return Integer.valueOf(-1);
            case Opcodes.ICONST_0: return Integer.valueOf(0);
            case Opcodes.ICONST_1: return Integer.valueOf(1);
            case Opcodes.ICONST_2: return Integer.valueOf(2);
            case Opcodes.ICONST_3: return Integer.valueOf(3);
            case Opcodes.ICONST_4: return Integer.valueOf(4);
            case Opcodes.ICONST_5: return Integer.valueOf(5);
            default: return null;
        }
    }
}
