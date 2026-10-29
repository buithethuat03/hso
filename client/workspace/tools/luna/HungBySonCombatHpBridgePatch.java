import java.util.Arrays;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Scoped native lifecycle release after a validated positive combat HP write.
 * No position fields, live animation state, API descriptors or packet formats
 * change. Also shared by the fresh investigation bridge generator.
 */
public final class HungBySonCombatHpBridgePatch implements Opcodes {
    private HungBySonCombatHpBridgePatch() { }

    static byte[] canonical(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    public static void apply(ClassNode bridge, String entity, String monster) {
        if (!"luna/generated/HungMonsterCacheAccess".equals(bridge.name))
            throw new IllegalStateException("Unexpected cache bridge");
        MethodNode target = null;
        for (MethodNode method : bridge.methods)
            if (method.name.equals("applyHp") && method.desc.equals("(Ljava/lang/Object;I)Z")) {
                if (target != null) throw new IllegalStateException("Duplicate applyHp");
                target = method;
            }
        if (target == null) throw new IllegalStateException("Missing applyHp");
        FieldInsnNode write = null;
        for (AbstractInsnNode instruction : target.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == PUTFIELD) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (!field.owner.equals(entity) || !field.name.equals("bs") || !field.desc.equals("I")
                        || write != null) throw new IllegalStateException("Unexpected HP write boundary");
                write = field;
            }
            if (instruction instanceof TypeInsnNode && instruction.getOpcode() == CHECKCAST
                    && !((TypeInsnNode)instruction).desc.equals(entity))
                throw new IllegalStateException("Unexpected applyHp native type");
        }
        if (write == null || write.getNext().getOpcode() != ICONST_1
                || write.getNext().getNext().getOpcode() != IRETURN)
            throw new IllegalStateException("Unexpected applyHp success boundary");
        byte[] before = canonical(bridge);
        MethodNode release = new MethodNode();
        emitPositiveRelease(release, entity, monster);
        AbstractInsnNode[] inserted = release.instructions.toArray();
        int oldMaxLocals = target.maxLocals;
        target.instructions.insert(write, release.instructions);
        target.maxLocals = Math.max(target.maxLocals, 4);
        byte[] after = canonical(bridge);
        for (AbstractInsnNode instruction : inserted) target.instructions.remove(instruction);
        target.maxLocals = oldMaxLocals;
        if (!Arrays.equals(before, canonical(bridge)))
            throw new IllegalStateException("Inverse bridge scope check failed");
        // Reinsert the verified block; max stack is recomputed by the caller.
        release = new MethodNode();
        emitPositiveRelease(release, entity, monster);
        target.instructions.insert(write, release.instructions);
        target.maxLocals = Math.max(target.maxLocals, 4);
        if (Arrays.equals(before, after)) throw new IllegalStateException("No lifecycle change");
    }

    public static void emitPositiveRelease(MethodVisitor method, String entity, String monster) {
        org.objectweb.asm.Label release = new org.objectweb.asm.Label();
        org.objectweb.asm.Label done = new org.objectweb.asm.Label();
        method.visitVarInsn(ILOAD, 2);
        method.visitJumpInsn(IFLE, done);
        method.visitVarInsn(ALOAD, 1);
        method.visitTypeInsn(CHECKCAST, entity);
        method.visitVarInsn(ASTORE, 3);
        method.visitVarInsn(ALOAD, 3);
        method.visitFieldInsn(GETFIELD, entity, "cG", "I");
        method.visitInsn(ICONST_4);
        method.visitJumpInsn(IF_ICMPEQ, release);
        for (String flag : new String[]{"dk", "cM", "dz"}) {
            method.visitVarInsn(ALOAD, 3);
            method.visitFieldInsn(GETFIELD, entity, flag, "Z");
            method.visitJumpInsn(IFNE, release);
        }
        method.visitJumpInsn(GOTO, done);
        method.visitLabel(release);
        for (String counter : new String[]{"cG", "cK", "bb", "bc", "bd", "dW", "bT"}) {
            method.visitVarInsn(ALOAD, 3);
            method.visitInsn(ICONST_0);
            method.visitFieldInsn(PUTFIELD, entity, counter, "I");
        }
        for (String flag : new String[]{"dk", "cL", "cM", "dz"}) {
            method.visitVarInsn(ALOAD, 3);
            method.visitInsn(ICONST_0);
            method.visitFieldInsn(PUTFIELD, entity, flag, "Z");
        }
        method.visitVarInsn(ALOAD, 3);
        method.visitInsn(ICONST_1);
        method.visitFieldInsn(PUTFIELD, entity, "cX", "Z");
        org.objectweb.asm.Label notMonster = new org.objectweb.asm.Label();
        method.visitVarInsn(ALOAD, 3);
        method.visitTypeInsn(INSTANCEOF, monster);
        method.visitJumpInsn(IFEQ, notMonster);
        method.visitVarInsn(ALOAD, 3);
        method.visitTypeInsn(CHECKCAST, monster);
        method.visitInsn(ICONST_0);
        method.visitFieldInsn(PUTFIELD, monster, "a", "I");
        method.visitLabel(notMonster);
        method.visitLabel(done);
    }
}
