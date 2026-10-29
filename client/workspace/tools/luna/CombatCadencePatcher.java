package luna.tools;

import java.io.File;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Narrow, version-checked access to the native cast-request timing boundary. */
public final class CombatCadencePatcher implements Opcodes {
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !"--inspect".equals(args[1])) {
            throw new IllegalArgumentException("Usage: CombatCadencePatcher <input.jar> --inspect");
        }
        JarFile jar = new JarFile(new File(args[0]));
        try {
            ClassNode cast = discover(jar);
            System.out.println("cast.owner=" + cast.name);
            for (FieldNode field : cast.fields) {
                System.out.println("field " + shortName(field.name) + " " + field.desc + " access=" + field.access);
            }
            for (MethodNode method : cast.methods) {
                System.out.println("method " + shortName(method.name) + method.desc);
                dump(method);
            }
        } finally { jar.close(); }
    }

    static ClassNode discover(JarFile jar) throws Exception {
        List<ClassNode> candidates = new ArrayList<ClassNode>();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.getName().endsWith(".class")) continue;
            ClassNode node = readClass(readAll(jar.getInputStream(entry)));
            boolean init = false, clockGate = false;
            for (MethodNode method : node.methods) {
                Type[] params = Type.getArgumentTypes(method.desc);
                if ((method.access & ACC_STATIC) == 0 && Type.VOID_TYPE.equals(Type.getReturnType(method.desc))
                        && params.length == 4 && params[0].getSort() == Type.OBJECT
                        && params[1].getSort() == Type.OBJECT && params[2].getSort() == Type.INT
                        && params[3].getSort() == Type.BYTE) init = true;
                if ("()Z".equals(method.desc)) {
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode) insn;
                            if ("java/lang/System".equals(call.owner) && "currentTimeMillis".equals(call.name)
                                    && "()J".equals(call.desc)) clockGate = true;
                        }
                    }
                }
            }
            if (init && clockGate) candidates.add(node);
        }
        require(candidates.size() == 1, "Expected one cast controller, got " + candidates.size());
        return candidates.get(0);
    }

    static void dump(MethodNode method) {
        int index = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() < 0) continue;
            String details = "";
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                details = shortName(field.owner) + "." + shortName(field.name) + " " + field.desc;
            } else if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                details = shortName(call.owner) + "." + shortName(call.name) + call.desc;
            } else if (insn instanceof IntInsnNode) details = String.valueOf(((IntInsnNode) insn).operand);
            else if (insn instanceof LdcInsnNode) details = String.valueOf(((LdcInsnNode) insn).cst);
            else if (insn instanceof VarInsnNode) details = String.valueOf(((VarInsnNode) insn).var);
            System.out.println("  " + index++ + ": " + insn.getOpcode() + " " + details);
        }
    }

    static String shortName(String name) { return name.length() > 28 ? "~" + name.substring(name.length() - 24) : name; }
    static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node;
    }
    static byte[] readAll(InputStream input) throws Exception {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        } finally { input.close(); }
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
