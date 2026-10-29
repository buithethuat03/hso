package luna.tools;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Read-only cross-reference of the verified native mount field, including inheritance. */
public final class NativeHorseStateAnalyzer implements Opcodes {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: <input.jar> <mount-catalog>");
        Properties symbols = new Properties(); InputStream input = new FileInputStream(args[1]);
        try { symbols.load(input); } finally { input.close(); }
        String owner = symbols.getProperty("mount.owner"), name = symbols.getProperty("mount.field");
        if (owner == null || name == null || !"B".equals(symbols.getProperty("mount.descriptor")))
            throw new IllegalArgumentException("Missing exact mount symbol");
        Map<String, ClassNode> classes = new LinkedHashMap<String, ClassNode>();
        JarFile jar = new JarFile(args[0]);
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = CombatCadencePatcher.readClass(CombatCadencePatcher.readAll(jar.getInputStream(entry)));
                classes.put(node.name, node);
            }
        } finally { jar.close(); }
        if (!owner.equals(declaringOwner(classes, owner, name, "B")))
            throw new IllegalArgumentException("Mount declaration does not match catalog");
        int writers = 0, supportWriters = 0;
        for (ClassNode node : classes.values()) for (MethodNode method : node.methods)
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != PUTFIELD) continue;
                FieldInsnNode field = (FieldInsnNode)insn;
                if (!name.equals(field.name) || !"B".equals(field.desc)
                        || !owner.equals(declaringOwner(classes, field.owner, name, "B"))) continue;
                writers++;
                if (node.name.equals(symbols.getProperty("support.owner"))) supportWriters++;
                System.out.println("writer." + writers + "=" + node.name + "." + method.name + method.desc);
            }
        System.out.println("mount.writers=" + writers);
        System.out.println("support.mount.writers=" + supportWriters);
    }
    private static String declaringOwner(Map<String, ClassNode> classes, String owner, String name, String descriptor) {
        while (owner != null) {
            ClassNode node = classes.get(owner);
            if (node == null) return null;
            for (FieldNode field : node.fields) if (name.equals(field.name) && descriptor.equals(field.desc)) return owner;
            owner = node.superName;
        }
        return null;
    }
}
