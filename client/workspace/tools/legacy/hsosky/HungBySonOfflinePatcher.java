import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Enables the client-side mod state locally and removes both license requests.
 */
public final class HungBySonOfflinePatcher {
    // Brain's parser requires three pipe-separated fields: status, cache TTL, payload.
    private static final String LOCAL_RESPONSE = "OK|300000|";

    private static final class EntryData {
        final String name;
        final long time;
        final String comment;
        final byte[] extra;
        byte[] data;

        EntryData(JarEntry entry, byte[] data) {
            this.name = entry.getName();
            this.time = entry.getTime();
            this.comment = entry.getComment();
            this.extra = entry.getExtra();
            this.data = data;
        }
    }

    private static final class PatchInfo {
        final String owner;
        final String method;

        PatchInfo(String owner, String method) {
            this.owner = owner;
            this.method = method;
        }
    }

    private HungBySonOfflinePatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: HungBySonOfflinePatcher input.jar output.jar");
        }

        File input = new File(args[0]).getCanonicalFile();
        File output = new File(args[1]).getCanonicalFile();
        if (input.equals(output)) {
            throw new IllegalArgumentException("Input and output must be different files");
        }
        if (output.exists()) {
            throw new IOException("Refusing to overwrite existing output: " + output);
        }

        List<EntryData> entries = readEntries(input);
        PatchInfo patch = patchWorker(entries);
        PatchInfo activationPatch = patchActivation(entries);
        if (patch == null || activationPatch == null) {
            throw new IllegalStateException("Could not find exactly one Brain HTTP POST helper");
        }

        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create output directory: " + parent);
        }
        writeEntries(output, entries);
        verifyOutput(output, patch);
        verifyActivation(output, activationPatch);

        System.out.println("PATCHED_CLASS=" + patch.owner);
        System.out.println("PATCHED_METHOD=" + patch.method);
        System.out.println("ACTIVATION_CLASS=" + activationPatch.owner);
        System.out.println("LOCAL_RESPONSE=" + LOCAL_RESPONSE);
        System.out.println("ENTRY_COUNT=" + entries.size());
        System.out.println("OUTPUT=" + output.getPath());
    }

    private static List<EntryData> readEntries(File input) throws IOException {
        List<EntryData> entries = new ArrayList<EntryData>();
        JarFile jar = new JarFile(input);
        try {
            Enumeration<JarEntry> enumeration = jar.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                java.io.InputStream stream = jar.getInputStream(entry);
                try {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = stream.read(buffer)) != -1) {
                        bytes.write(buffer, 0, count);
                    }
                } finally {
                    stream.close();
                }
                entries.add(new EntryData(entry, bytes.toByteArray()));
            }
        } finally {
            jar.close();
        }
        return entries;
    }

    private static PatchInfo patchWorker(List<EntryData> entries) {
        PatchInfo found = null;
        for (EntryData entry : entries) {
            if (!entry.name.endsWith(".class")) {
                continue;
            }

            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(entry.data).accept(node, 0);
            if (!node.interfaces.contains("java/lang/Runnable")) {
                continue;
            }

            for (MethodNode method : node.methods) {
                if (!"(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;".equals(method.desc)) {
                    continue;
                }
                boolean opensConnection = false;
                boolean setsPost = false;
                boolean writesBody = false;
                boolean readsResponse = false;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null;
                     instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if ("javax/microedition/io/Connector".equals(call.owner)
                            && "open".equals(call.name)) {
                        opensConnection = true;
                    } else if ("javax/microedition/io/HttpConnection".equals(call.owner)
                            && "setRequestMethod".equals(call.name)) {
                        setsPost = true;
                    } else if ("java/io/OutputStream".equals(call.owner)
                            && "write".equals(call.name)
                            && "([B)V".equals(call.desc)) {
                        writesBody = true;
                    } else if ("javax/microedition/io/HttpConnection".equals(call.owner)
                            && "getResponseCode".equals(call.name)) {
                        readsResponse = true;
                    }
                }

                if (!(opensConnection && setsPost && writesBody && readsResponse)) {
                    continue;
                }
                if (found != null) {
                    throw new IllegalStateException("Found multiple matching HTTP POST helpers");
                }

                method.instructions.clear();
                method.tryCatchBlocks.clear();
                method.localVariables = Collections.emptyList();
                method.visibleLocalVariableAnnotations = null;
                method.invisibleLocalVariableAnnotations = null;
                method.attrs = null;
                method.instructions.add(new LdcInsnNode(LOCAL_RESPONSE));
                method.instructions.add(new InsnNode(Opcodes.ARETURN));
                method.maxStack = 1;
                method.maxLocals = 3;

                ClassWriter writer = new ClassWriter(0);
                node.accept(writer);
                entry.data = writer.toByteArray();
                found = new PatchInfo(node.name, method.name + method.desc);
            }
        }
        return found;
    }

    private static PatchInfo patchActivation(List<EntryData> entries) {
        PatchInfo found = null;
        for (EntryData entry : entries) {
            if (!entry.name.endsWith(".class")) continue;
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(entry.data).accept(node, 0);
            if (!node.interfaces.contains("java/lang/Runnable")) continue;

            MethodNode update = null, post = null, submit = null, overlay = null, initializer = null;
            for (MethodNode method : node.methods) {
                if ("update".equals(method.name) && "()V".equals(method.desc)
                        && (method.access & Opcodes.ACC_STATIC) != 0) update = method;
                if ("<clinit>".equals(method.name)) initializer = method;
                if ("()V".equals(method.desc) && (method.access & Opcodes.ACC_PRIVATE) != 0
                        && (method.access & Opcodes.ACC_STATIC) == 0 && calls(method,
                        "javax/microedition/io/Connector", "open")) post = method;
                if ("(Ljava/lang/String;)V".equals(method.desc)
                        && (method.access & Opcodes.ACC_PUBLIC) != 0
                        && (method.access & Opcodes.ACC_STATIC) != 0
                        && calls(method, "java/util/Random", "nextLong")) submit = method;
                if ("(Ljava/lang/Object;)V".equals(method.desc)
                        && (method.access & Opcodes.ACC_PUBLIC) != 0
                        && (method.access & Opcodes.ACC_STATIC) != 0
                        && calls(method, "javax/microedition/lcdui/Graphics", "drawString")) overlay = method;
            }
            if (update == null || post == null || submit == null || overlay == null || initializer == null) continue;
            if (found != null) throw new IllegalStateException("Found multiple activation workers");

            String lock = field(node, "ililliililli", "Z");
            String key = field(node, "ililliiliili", "Ljava/lang/String;");
            String label = field(node, "ililliliiiil", "Ljava/lang/String;");
            String status = field(node, "ililliililii", "Ljava/lang/String;");
            String activated = field(node, "ilillililili", "Z");
            String newlyActivated = field(node, "ilillililill", "Z");
            String[] features = {
                    field(node, "ililliililll", "Z"), field(node, "ililliilliii", "Z"),
                    field(node, "ililliilliil", "Z"), field(node, "ililliillili", "Z"),
                    field(node, "ililliillill", "Z"), field(node, "ililliilllii", "Z"),
                    field(node, "ililliilllil", "Z")
            };

            String helper = "codexLocalActivate";
            for (MethodNode method : node.methods) {
                if (helper.equals(method.name)) throw new IllegalStateException("Local helper already exists");
            }
            MethodNode local = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                    helper, "()V", null, null);
            local.instructions.add(new LdcInsnNode("LOCAL"));
            local.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, key, "Ljava/lang/String;"));
            local.instructions.add(new LdcInsnNode("LOCAL"));
            local.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, label, "Ljava/lang/String;"));
            local.instructions.add(new LdcInsnNode(""));
            local.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, status, "Ljava/lang/String;"));
            local.instructions.add(new InsnNode(Opcodes.ICONST_0));
            local.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, lock, "Z"));
            for (String feature : features) {
                local.instructions.add(new InsnNode(Opcodes.ICONST_1));
                local.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, feature, "Z"));
            }
            local.instructions.add(new InsnNode(Opcodes.ICONST_1));
            local.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, activated, "Z"));
            local.instructions.add(new InsnNode(Opcodes.ICONST_1));
            local.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, newlyActivated, "Z"));
            local.instructions.add(new InsnNode(Opcodes.RETURN));
            local.maxStack = 1;
            local.maxLocals = 0;
            node.methods.add(local);

            replaceWithCall(update, node.name, helper, 0);
            replaceWithCall(submit, node.name, helper, 1);
            replaceWithReturn(post, 1);
            replaceWithReturn(overlay, 1);
            for (AbstractInsnNode instruction = initializer.instructions.getLast(); instruction != null;
                 instruction = instruction.getPrevious()) {
                if (instruction.getOpcode() == Opcodes.RETURN) {
                    initializer.instructions.insertBefore(instruction,
                            new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, helper, "()V", false));
                    break;
                }
            }
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            entry.data = writer.toByteArray();
            found = new PatchInfo(node.name, helper + "()V");
        }
        return found;
    }

    private static String field(ClassNode node, String suffix, String descriptor) {
        String found = null;
        for (FieldNode candidate : node.fields) {
            if (candidate.name.endsWith(suffix) && candidate.desc.equals(descriptor)) {
                if (found != null) throw new IllegalStateException("Ambiguous field: " + suffix);
                found = candidate.name;
            }
        }
        if (found == null) throw new IllegalStateException("Missing field: " + suffix);
        return found;
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(owner) && call.name.equals(name)) return true;
            }
        }
        return false;
    }

    private static void replaceWithCall(MethodNode method, String owner, String helper, int locals) {
        clearMethod(method);
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, helper, "()V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 0;
        method.maxLocals = locals;
    }

    private static void replaceWithReturn(MethodNode method, int locals) {
        clearMethod(method);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 0;
        method.maxLocals = locals;
    }

    private static void clearMethod(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = Collections.emptyList();
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
        method.attrs = null;
    }

    private static void writeEntries(File output, List<EntryData> entries) throws IOException {
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try {
            for (EntryData source : entries) {
                JarEntry entry = new JarEntry(source.name);
                if (source.time >= 0) {
                    entry.setTime(source.time);
                }
                if (source.comment != null) {
                    entry.setComment(source.comment);
                }
                if (source.extra != null) {
                    entry.setExtra(source.extra);
                }
                jar.putNextEntry(entry);
                jar.write(source.data);
                jar.closeEntry();
            }
        } finally {
            jar.close();
        }
    }

    private static void verifyOutput(File output, PatchInfo expected) throws Exception {
        JarFile jar = new JarFile(output);
        try {
            JarEntry entry = jar.getJarEntry(expected.owner + ".class");
            if (entry == null) {
                throw new IllegalStateException("Patched class entry is missing");
            }
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(jar.getInputStream(entry)).accept(node, 0);
            boolean found = false;
            for (MethodNode method : node.methods) {
                if (!(method.name + method.desc).equals(expected.method)) {
                    continue;
                }
                boolean hasLocalResponse = false;
                boolean opensConnection = false;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null;
                     instruction = instruction.getNext()) {
                    if (instruction instanceof LdcInsnNode
                            && LOCAL_RESPONSE.equals(((LdcInsnNode) instruction).cst)) {
                        hasLocalResponse = true;
                    }
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if ("javax/microedition/io/Connector".equals(call.owner)
                                && "open".equals(call.name)) {
                            opensConnection = true;
                        }
                    }
                }
                if (!hasLocalResponse || opensConnection) {
                    throw new IllegalStateException("Patched method did not pass verification");
                }
                found = true;
            }
            if (!found) {
                throw new IllegalStateException("Patched method is missing from output class");
            }
        } finally {
            jar.close();
        }
    }

    private static void verifyActivation(File output, PatchInfo expected) throws Exception {
        JarFile jar = new JarFile(output);
        try {
            JarEntry entry = jar.getJarEntry(expected.owner + ".class");
            if (entry == null) throw new IllegalStateException("Activation class missing");
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(jar.getInputStream(entry)).accept(node, 0);
            boolean updateCallsLocal = false;
            boolean submitCallsLocal = false;
            boolean noPost = false;
            boolean noOverlay = false;
            boolean startsActive = false;
            for (MethodNode method : node.methods) {
                if ("<clinit>".equals(method.name)) {
                    startsActive = calls(method, node.name, "codexLocalActivate");
                }
                if ("update".equals(method.name) && "()V".equals(method.desc)) {
                    updateCallsLocal = calls(method, node.name, "codexLocalActivate")
                            && !calls(method, "java/lang/Thread", "start");
                }
                if ("(Ljava/lang/String;)V".equals(method.desc)
                        && (method.access & Opcodes.ACC_PUBLIC) != 0
                        && calls(method, node.name, "codexLocalActivate")) submitCallsLocal = true;
                if ("()V".equals(method.desc)
                        && (method.access & Opcodes.ACC_PRIVATE) != 0
                        && (method.access & Opcodes.ACC_STATIC) == 0
                        && method.instructions.size() == 1
                        && method.instructions.getFirst().getOpcode() == Opcodes.RETURN) noPost = true;
                if ("(Ljava/lang/Object;)V".equals(method.desc)
                        && (method.access & Opcodes.ACC_PUBLIC) != 0
                        && method.instructions.size() == 1
                        && method.instructions.getFirst().getOpcode() == Opcodes.RETURN) noOverlay = true;
            }
            if (!(startsActive && updateCallsLocal && submitCallsLocal && noPost && noOverlay)) {
                throw new IllegalStateException("Local activation verification failed");
            }
        } finally {
            jar.close();
        }
    }
}
