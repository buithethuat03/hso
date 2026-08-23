import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Read-only diagnostic for every writer of the client's global selected-target field. */
public final class HSOSKYTargetAssignmentAnalyzer implements Opcodes {
    private static final String INPUT =
            "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_REMOTEZONE_SERVERCD_LOOTTP_NOCLIP_VIHELP_DEFAULT37.jar";
    private static final String SUPPORT = "HSOSKYPetFeedSupport";

    public static void main(String[] args) throws Exception {
        ClassNode player = null;
        try (JarFile jar = new JarFile(new File(INPUT))) {
            Enumeration<JarEntry> all = jar.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = read(readAll(jar.getInputStream(entry)));
                if (contains(node, "Player vec7")) player = node;
            }
            require(player != null, "player missing");
            String base = player.superName;
            MethodNode scan = null;
            for (MethodNode method : player.methods) {
                if (calls(method, SUPPORT, "isAutoPlayersEnabled", "()Z")) scan = method;
            }
            require(scan != null, "scan missing");
            MethodNode update = null;
            for (MethodNode method : player.methods) {
                if (calls(method, player.name, scan.name, scan.desc)) update = method;
            }
            require(update != null, "update missing");

            FieldInsnNode selected = null;
            for (AbstractInsnNode instruction = update.instructions.getFirst(); instruction != null;
                    instruction = instruction.getNext()) {
                if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != GETSTATIC) continue;
                FieldInsnNode field = (FieldInsnNode) instruction;
                if (!("L" + base + ";").equals(field.desc)) continue;
                AbstractInsnNode next = nextReal(instruction);
                if (next instanceof FieldInsnNode && next.getOpcode() == GETFIELD
                        && base.equals(((FieldInsnNode) next).owner) && "B".equals(((FieldInsnNode) next).desc)) {
                    selected = field;
                    break;
                }
            }
            require(selected != null, "selected target missing");
            System.out.println("PLAYER=" + player.name);
            System.out.println("BASE=" + base);
            System.out.println("TARGET=" + selected.owner + "." + selected.name + " " + selected.desc);

            for (MethodNode method : player.methods) {
                int reads = 0;
                int writesInPlayer = 0;
                for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                        instruction = instruction.getNext()) {
                    if (!(instruction instanceof FieldInsnNode)) continue;
                    FieldInsnNode field = (FieldInsnNode) instruction;
                    if (!selected.owner.equals(field.owner) || !selected.name.equals(field.name)
                            || !selected.desc.equals(field.desc)) continue;
                    if (instruction.getOpcode() == GETSTATIC) reads++;
                    if (instruction.getOpcode() == PUTSTATIC) writesInPlayer++;
                }
                if (reads > 0 || writesInPlayer > 0) {
                    System.out.println("PLAYER_TARGET_ACCESS reads=" + reads + " writes=" + writesInPlayer
                            + " " + method.name + method.desc);
                }
            }

            all = jar.entries();
            int writes = 0;
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = read(readAll(jar.getInputStream(entry)));
                for (MethodNode method : node.methods) {
                    int index = 0;
                    for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                            instruction = instruction.getNext(), index++) {
                        if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != PUTSTATIC) continue;
                        FieldInsnNode field = (FieldInsnNode) instruction;
                        if (!selected.owner.equals(field.owner) || !selected.name.equals(field.name)
                                || !selected.desc.equals(field.desc)) continue;
                        writes++;
                        System.out.println("\nWRITE " + writes + " " + node.name + "." + method.name + method.desc
                                + " index=" + index);
                        printContext(method, instruction, 10);
                    }
                }
            }
            System.out.println("\nTOTAL_WRITES=" + writes);
        }
    }

    private static void printContext(MethodNode method, AbstractInsnNode center, int radius) {
        AbstractInsnNode start = center;
        for (int i = 0; i < radius && start.getPrevious() != null; i++) start = start.getPrevious();
        AbstractInsnNode instruction = start;
        for (int i = -radius; instruction != null && i <= radius; i++, instruction = instruction.getNext()) {
            System.out.println((instruction == center ? "  >> " : "     ") + text(instruction));
        }
    }

    private static String text(AbstractInsnNode instruction) {
        if (instruction.getOpcode() < 0) return instruction.getClass().getSimpleName();
        if (instruction instanceof FieldInsnNode) {
            FieldInsnNode field = (FieldInsnNode) instruction;
            return "FIELD op=" + field.getOpcode() + " " + field.owner + "." + field.name + " " + field.desc;
        }
        if (instruction instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode) instruction;
            return "CALL op=" + call.getOpcode() + " " + call.owner + "." + call.name + call.desc;
        }
        if (instruction instanceof VarInsnNode) {
            VarInsnNode var = (VarInsnNode) instruction;
            return "VAR op=" + var.getOpcode() + " local=" + var.var;
        }
        if (instruction instanceof JumpInsnNode) return "JUMP op=" + instruction.getOpcode();
        if (instruction instanceof LdcInsnNode) return "LDC " + ((LdcInsnNode) instruction).cst;
        if (instruction instanceof IntInsnNode) return "INT op=" + instruction.getOpcode() + " " + ((IntInsnNode) instruction).operand;
        if (instruction instanceof TypeInsnNode) return "TYPE op=" + instruction.getOpcode() + " " + ((TypeInsnNode) instruction).desc;
        if (instruction instanceof InsnNode) return "INSN op=" + instruction.getOpcode();
        return instruction.getClass().getSimpleName() + " op=" + instruction.getOpcode();
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] readAll(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }

    private static boolean contains(ClassNode node, String value) {
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                    instruction = instruction.getNext()) {
                if (instruction instanceof LdcInsnNode && ((LdcInsnNode) instruction).cst instanceof String
                        && ((String) ((LdcInsnNode) instruction).cst).contains(value)) return true;
            }
        }
        return false;
    }

    private static boolean calls(MethodNode method, String owner, String name, String desc) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) return true;
            }
        }
        return false;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode instruction) {
        instruction = instruction == null ? null : instruction.getNext();
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
        return instruction;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
