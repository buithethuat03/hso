import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class GhostModPatcher {
    // Legacy helper value only; dh/dt are no longer patched into the output JAR.
    private static final int FASTCAST_DELAY_MS = 300;

    public static void main(String[] args) throws Exception {
        String inputJarPath = "KnightOnline_402.jar";
        String classToPatch = "er.class";
        String ghostModClassFile = "GhostMod.class";
        String outputJarPath = "KnightOnline_402_Modded.jar";
        String stagingJarPath = outputJarPath + ".building";

        System.out.println("[*] Tao file tam " + stagingJarPath);
        ZipFile zipFile = new ZipFile(inputJarPath);
        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(stagingJarPath));

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith("GhostMod") && name.endsWith(".class")) {
                continue;
            }
            InputStream in = zipFile.getInputStream(entry);
            
            zout.putNextEntry(new ZipEntry(name));

            if (name.equals("er.class")) {
                System.out.println("[+] Patching er.class (Login/Captcha/Multi-Hook)");
                zout.write(patchErClass(in));
            } else if (name.equals("fu.class")) {
                System.out.println("[+] Patching fu.class (Canvas Init)");
                zout.write(patchFuClass(in));
            } else if (name.equals("q.class")) {
                System.out.println("[+] Patching q.class (Chat Command Hook)");
                zout.write(patchQClass(in));
            } else if (name.equals("cn.class")) {
                System.out.println("[+] Patching cn.class (Screen Command Trace)");
                zout.write(patchScreenCommandClass(in, "cn"));
            } else if (name.equals("ah.class")) {
                System.out.println("[+] Patching ah.class (Dialog Command Trace)");
                zout.write(patchScreenCommandClass(in, "ah"));
            } else if (name.equals("ef.class")) {
                System.out.println("[+] Patching ef.class (Packet Hook)");
                zout.write(patchEfClass(in));
            } else if (name.equals("bq.class")) {
                System.out.println("[+] Patching bq.class (10-slot Hotbar)");
                zout.write(patchPlayerHotbar(in));
            } else if (name.equals("cf.class")) {
                System.out.println("[+] Patching cf.class (10-slot Hotbar UI)");
                zout.write(patchHotbarPaint(in));
            } else if (name.equals("y.class")) {
                System.out.println("[+] Patching y.class (10-slot Skill Assignment/Migration)");
                zout.write(patchHotbarMenu(in, "TabSkillsNew menu", true));
            } else if (name.equals("fo.class")) {
                System.out.println("[+] Patching fo.class (10-slot Item Assignment)");
                zout.write(patchHotbarMenu(in, "TabShopNew menu3", false));
            } else if (name.equals("com/silverknight/a.class")) {
                System.out.println("[+] Patching com/silverknight/a.class (2x game loop)");
                zout.write(patchGameLoopSpeed(in));
            } else if (name.equals("fa.class")) {
                System.out.println("[+] Patching fa.class (keep local movement at 1x)");
                zout.write(patchLocalPlayerMovement(in));
            } else {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    zout.write(buffer, 0, len);
                }
            }
            zout.closeEntry();
        }
        
        System.out.println("[+] Them cac file lop GhostMod vao JAR moi...");
        File currentDir = new File(System.getProperty("ghostmod.classes.dir", "."));
        File[] ghostClasses = currentDir.listFiles((dir, name) -> name.startsWith("GhostMod") && name.endsWith(".class") && !name.equals("GhostModPatcher.class"));
        if (ghostClasses != null) {
            for (File gc : ghostClasses) {
                System.out.println("  -> Them " + gc.getName());
                zout.putNextEntry(new ZipEntry(gc.getName()));
                try (FileInputStream fis = new FileInputStream(gc)) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zout.write(buffer, 0, len);
                    }
                }
                zout.closeEntry();
            }
        }
        
        zout.close();
        zipFile.close();

        verifyOutput(stagingJarPath);
        String publishedJarPath = publishOutput(stagingJarPath, outputJarPath);
        verifyOutput(publishedJarPath);
        
        System.out.println("[+] Thanh cong! File mod: " + publishedJarPath);
    }

    private static String publishOutput(String stagingJarPath, String outputJarPath) throws IOException {
        File staging = new File(stagingJarPath);
        File output = new File(outputJarPath);
        require(staging.isFile() && staging.length() > 0L, "Staging JAR is missing or empty");
        try {
            moveVerifiedJar(staging, output);
            System.out.println("[+] Published verified JAR atomically: " + outputJarPath);
            return outputJarPath;
        } catch (IOException targetLocked) {
            String fallbackPath = outputJarPath.substring(0, outputJarPath.length() - 4)
                    + "_NoFastcast.jar";
            File fallback = new File(fallbackPath);
            try {
                moveVerifiedJar(staging, fallback);
            } catch (IOException fallbackFailure) {
                fallbackFailure.addSuppressed(targetLocked);
                throw fallbackFailure;
            }
            System.out.println("[!] File dich dang bi VPS-Micro khoa; da xuat ban moi: " + fallbackPath);
            return fallbackPath;
        }
    }

    private static void moveVerifiedJar(File source, File destination) throws IOException {
        try {
            Files.move(source.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] patchErClass(InputStream in) throws IOException {
        ClassReader cr = new ClassReader(in);
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        cr.accept(classNode, 0);

        for (MethodNode mn : classNode.methods) {
            // Captcha Patch
            if (mn.name.equals("n") && mn.desc.equals("(Lep;)V")) {
                InsnList insns = mn.instructions;
                for (int i = 0; i < insns.size(); i++) {
                    AbstractInsnNode node = insns.get(i);
                    if (node instanceof IntInsnNode && node.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode)node).operand == 9999) {
                        for (int j = i; j < i + 30; j++) {
                            AbstractInsnNode sub = insns.get(j);
                            if (sub instanceof FieldInsnNode && sub.getOpcode() == Opcodes.PUTSTATIC) {
                                FieldInsnNode fn = (FieldInsnNode) sub;
                                if (fn.owner.equals("fa") && fn.name.equals("df")) {
                                    InsnList inject = new InsnList();
                                    inject.add(new FieldInsnNode(Opcodes.GETSTATIC, "fa", "df", "Laq;"));
                                    inject.add(new FieldInsnNode(Opcodes.GETFIELD, "aq", "a", "Ljavax/microedition/lcdui/Image;"));
                                    inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "GhostMod", "checkGhost", "(Ljavax/microedition/lcdui/Image;)V", false));
                                    insns.insert(sub, inject);
                                    System.out.println("  -> Captcha patch OK");
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            // ... (Ghost Captcha logic remains) ...
        }
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        classNode.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchFuClass(InputStream in) throws IOException {
        ClassReader cr = new ClassReader(in);
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        cr.accept(classNode, 0);

        int resizedArrays = 0;
        int resizedClearLoops = 0;
        MethodNode clinit = findMethod(classNode, "<clinit>", "()V");
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof IntInsnNode && insn.getOpcode() == Opcodes.NEWARRAY
                    && ((IntInsnNode) insn).operand == Opcodes.T_BOOLEAN
                    && isInt(previousReal(insn), 30)) {
                replaceInt(clinit, previousReal(insn), 35);
                resizedArrays++;
            }
        }
        for (MethodNode method : classNode.methods) {
            if (!containsOwnBooleanArray(method, "fu")) continue;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (isInt(insn, 30) && nextReal(insn) instanceof JumpInsnNode) {
                    replaceInt(method, insn, 35);
                    resizedClearLoops++;
                }
            }
        }
        require(resizedArrays == 3, "Expected three fu key arrays to resize, got " + resizedArrays);
        require(resizedClearLoops >= 3, "Expected fu key clear loops to resize, got " + resizedClearLoops);
        System.out.println("  -> 10-slot key arrays/clear loops OK: " + resizedArrays + "/" + resizedClearLoops);

        for (MethodNode mn : classNode.methods) {
            // Startup Init Patch - Insert at START
            if (mn.name.equals("<init>") && mn.desc.equals("()V")) {
                mn.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "GhostMod", "initMod", "()V", false));
                System.out.println("  -> Startup Init patch OK AT START");
            }
            if (mn.name.equals("a") && mn.desc.equals("(I)V")) {
                AbstractInsnNode first = mn.instructions.getFirst();
                InsnList inject = new InsnList();
                inject.add(new LdcInsnNode("down"));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 1));
                inject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "GhostMod",
                        "hookKeyEvent",
                        "(Ljava/lang/String;I)V",
                        false
                ));
                mn.instructions.insertBefore(first, inject);
                System.out.println("  -> Key down trace hook OK");
            }
            if (mn.name.equals("b") && mn.desc.equals("(I)V")) {
                AbstractInsnNode first = mn.instructions.getFirst();
                InsnList inject = new InsnList();
                inject.add(new LdcInsnNode("up"));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 1));
                inject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "GhostMod",
                        "hookKeyEvent",
                        "(Ljava/lang/String;I)V",
                        false
                ));
                mn.instructions.insertBefore(first, inject);
                System.out.println("  -> Key up trace hook OK");
            }
            if (mn.name.equals("a") && mn.desc.equals("(II)V")) {
                AbstractInsnNode first = mn.instructions.getFirst();
                InsnList inject = new InsnList();
                inject.add(new LdcInsnNode("move"));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 0));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 1));
                inject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "GhostMod",
                        "hookPointerEvent",
                        "(Ljava/lang/String;II)V",
                        false
                ));
                mn.instructions.insertBefore(first, inject);
                System.out.println("  -> Pointer move trace hook OK");
            }
            if (mn.name.equals("b") && mn.desc.equals("(II)V")) {
                AbstractInsnNode first = mn.instructions.getFirst();
                InsnList inject = new InsnList();
                inject.add(new LdcInsnNode("down"));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 0));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 1));
                inject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "GhostMod",
                        "hookPointerEvent",
                        "(Ljava/lang/String;II)V",
                        false
                ));
                mn.instructions.insertBefore(first, inject);
                System.out.println("  -> Pointer down trace hook OK");
            }
            if (mn.name.equals("c") && mn.desc.equals("(II)V")) {
                AbstractInsnNode first = mn.instructions.getFirst();
                InsnList inject = new InsnList();
                inject.add(new LdcInsnNode("up"));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 0));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 1));
                inject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "GhostMod",
                        "hookPointerEvent",
                        "(Ljava/lang/String;II)V",
                        false
                ));
                mn.instructions.insertBefore(first, inject);
                System.out.println("  -> Pointer up trace hook OK");
            }
        }
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        classNode.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchScreenCommandClass(InputStream in, String className) throws IOException {
        ClassReader cr = new ClassReader(in);
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        cr.accept(classNode, 0);
        for (MethodNode mn : classNode.methods) {
            if ((mn.access & Opcodes.ACC_STATIC) == 0
                    && mn.desc.equals("(II)V")
                    && (mn.name.equals("a") || mn.name.equals("a_") || mn.name.equals("b"))) {
                AbstractInsnNode first = mn.instructions.getFirst();
                InsnList inject = new InsnList();
                inject.add(new LdcInsnNode(className + "." + mn.name));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 1));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 2));
                inject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "GhostMod",
                        "hookScreenCommand",
                        "(Ljava/lang/String;II)V",
                        false
                ));
                mn.instructions.insertBefore(first, inject);
                System.out.println("  -> Screen command trace hook OK " + className + "." + mn.name + mn.desc);
            }
        }
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        classNode.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchQClass(InputStream in) throws IOException {
        ClassReader cr = new ClassReader(in);
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        cr.accept(classNode, 0);
        for (MethodNode mn : classNode.methods) {
            if (mn.name.equals("a") && mn.desc.equals("(Ljava/lang/String;)V")) {
                AbstractInsnNode first = mn.instructions.getFirst();
                LabelNode continueLabel = new LabelNode();
                InsnList inject = new InsnList();
                inject.add(new VarInsnNode(Opcodes.ALOAD, 1));
                inject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "GhostMod",
                        "interceptChatCommand",
                        "(Ljava/lang/String;)Z",
                        false
                ));
                inject.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
                inject.add(new InsnNode(Opcodes.RETURN));
                inject.add(continueLabel);
                mn.instructions.insertBefore(first, inject);
                System.out.println("  -> Chat command hook OK");
            }
            if (mn.name.equals("a") && mn.desc.equals("(SSBB)V")) {
                AbstractInsnNode first = mn.instructions.getFirst();
                InsnList inject = new InsnList();
                inject.add(new VarInsnNode(Opcodes.ILOAD, 1));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 2));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 3));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 4));
                inject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "GhostMod",
                        "hookFeedPacket",
                        "(SSBB)V",
                        false
                ));
                mn.instructions.insertBefore(first, inject);
                System.out.println("  -> Feed packet hook OK");
            }
            if (mn.name.equals("b") && mn.desc.equals("(SBB)V")) {
                AbstractInsnNode first = mn.instructions.getFirst();
                InsnList inject = new InsnList();
                inject.add(new VarInsnNode(Opcodes.ILOAD, 1));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 2));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 3));
                inject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "GhostMod",
                        "hookMenuCommand",
                        "(SBB)V",
                        false
                ));
                mn.instructions.insertBefore(first, inject);
                System.out.println("  -> Menu command packet hook OK");
            }
            if (mn.name.equals("a") && mn.desc.equals("(BB[B)V")) {
                AbstractInsnNode first = mn.instructions.getFirst();
                InsnList inject = new InsnList();
                inject.add(new VarInsnNode(Opcodes.ILOAD, 1));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 2));
                inject.add(new VarInsnNode(Opcodes.ALOAD, 3));
                inject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "GhostMod",
                        "hookOpcode55",
                        "(BB[B)V",
                        false
                ));
                mn.instructions.insertBefore(first, inject);
                System.out.println("  -> Opcode 55 payload hook OK");
            }
            if (mn.name.equals("c") && mn.desc.equals("(BS)V")) {
                AbstractInsnNode first = mn.instructions.getFirst();
                InsnList inject = new InsnList();
                inject.add(new VarInsnNode(Opcodes.ILOAD, 1));
                inject.add(new VarInsnNode(Opcodes.ILOAD, 2));
                inject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "GhostMod",
                        "hookOpcode44",
                        "(BS)V",
                        false
                ));
                mn.instructions.insertBefore(first, inject);
                System.out.println("  -> Opcode 44 hook OK");
            }
        }
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        classNode.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchEfClass(InputStream in) throws IOException {
        ClassReader cr = new ClassReader(in);
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        cr.accept(classNode, 0);
        for (MethodNode mn : classNode.methods) {
            if (mn.name.equals("o") && mn.desc.equals("(B)V")) {
                InsnList inject = new InsnList();
                inject.add(new VarInsnNode(Opcodes.ILOAD, 1));
                inject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "GhostMod",
                        "logOutgoingPacket",
                        "(B)V",
                        false
                ));
                mn.instructions.insert(mn.instructions.getFirst(), inject);
                System.out.println("  -> Outgoing packet log hook OK");
            }
        }
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        classNode.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchPlayerHotbar(InputStream in) throws IOException {
        ClassReader cr = new ClassReader(in);
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        cr.accept(owner, 0);

        int arrayPatches = 0;
        MethodNode clinit = findMethod(owner, "<clinit>", "()V");
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.ANEWARRAY && insn instanceof TypeInsnNode
                    && "ao".equals(((TypeInsnNode) insn).desc)
                    && isInt(previousReal(insn), 5)
                    && nextReal(insn) != null && nextReal(insn).getOpcode() == Opcodes.AASTORE) {
                replaceInt(clinit, previousReal(insn), 10);
                arrayPatches++;
            }
        }

        MethodNode input = null;
        String actionMethod = null;
        for (MethodNode method : owner.methods) {
            if (!containsInts(method, 21, 23, 25, 27, 29)) continue;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if ("bq".equals(call.owner) && "(IZ)V".equals(call.desc)) {
                        input = method;
                        actionMethod = call.name;
                        break;
                    }
                }
            }
            if (input != null) break;
        }
        require(input != null && actionMethod != null, "Cannot locate bq hotbar keyboard handler");

        InsnList extra = new InsnList();
        for (int i = 0; i < 5; i++) {
            LabelNode skip = new LabelNode();
            extra.add(new FieldInsnNode(Opcodes.GETSTATIC, "fu", "al", "[Z"));
            extra.add(pushInt(30 + i));
            extra.add(new InsnNode(Opcodes.BALOAD));
            extra.add(new JumpInsnNode(Opcodes.IFEQ, skip));
            extra.add(new VarInsnNode(Opcodes.ALOAD, 0));
            extra.add(pushInt(5 + i));
            extra.add(new InsnNode(Opcodes.ICONST_1));
            extra.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "bq", actionMethod, "(IZ)V", false));
            extra.add(skip);
        }
        input.instructions.insert(extra);
        require(arrayPatches == 1, "Expected one bq hotbar array patch, got " + arrayPatches);
        System.out.println("  -> Hotbar storage/input OK (3x10, slots 6-10 on F/G/H/J/K)");

        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        owner.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchGameLoopSpeed(InputStream in) throws IOException {
        ClassReader cr = new ClassReader(in);
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        cr.accept(owner, 0);

        MethodNode run = findMethod(owner, "run", "()V");
        int frameConstants = 0;
        for (AbstractInsnNode insn = run.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode && Long.valueOf(40L).equals(((LdcInsnNode) insn).cst)) {
                ((LdcInsnNode) insn).cst = Long.valueOf(20L);
                frameConstants++;
            }
        }
        require(frameConstants == 2, "Expected two 40ms frame constants, got " + frameConstants);
        System.out.println("  -> Main update/render interval 40ms -> 20ms (25 FPS -> 50 FPS)");

        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        owner.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchLocalPlayerMovement(InputStream in) throws IOException {
        ClassReader cr = new ClassReader(in);
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        cr.accept(owner, 0);

        MethodNode update = findMethod(owner, "a", "()V");
        AbstractInsnNode first = update.instructions.getFirst();
        while (first != null && first.getOpcode() < 0) first = first.getNext();
        require(first != null && first.getOpcode() == Opcodes.ALOAD,
                "Cannot locate the beginning of fa.a() movement update");

        AbstractInsnNode cursor = first;
        int coordinateWrites = 0;
        AbstractInsnNode afterMovement = null;
        while (cursor != null && coordinateWrites < 2) {
            if (cursor instanceof FieldInsnNode && cursor.getOpcode() == Opcodes.PUTFIELD) {
                FieldInsnNode field = (FieldInsnNode) cursor;
                if ("fa".equals(field.owner)
                        && ((coordinateWrites == 0 && "aZ".equals(field.name))
                        || (coordinateWrites == 1 && "ba".equals(field.name)))) {
                    coordinateWrites++;
                    afterMovement = cursor.getNext();
                }
            }
            cursor = cursor.getNext();
        }
        require(coordinateWrites == 2 && afterMovement != null,
                "Cannot locate the initial fa.aZ/fa.ba displacement writes");

        LabelNode applyMovement = new LabelNode();
        LabelNode movementDone = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new FieldInsnNode(Opcodes.GETSTATIC, "cn", "g", "Lbq;"));
        guard.add(new JumpInsnNode(Opcodes.IF_ACMPNE, applyMovement));
        guard.add(new FieldInsnNode(Opcodes.GETSTATIC, "fu", "aj", "I"));
        guard.add(new InsnNode(Opcodes.ICONST_1));
        guard.add(new InsnNode(Opcodes.IAND));
        guard.add(new JumpInsnNode(Opcodes.IFNE, movementDone));
        guard.add(applyMovement);
        update.instructions.insertBefore(first, guard);
        update.instructions.insertBefore(afterMovement, movementDone);

        System.out.println("  -> Local player aZ/ba displacement runs every second 50 FPS tick (same travel speed)");
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        owner.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchHotbarPaint(InputStream in) throws IOException {
        ClassReader cr = new ClassReader(in);
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        cr.accept(owner, 0);
        MethodNode clinit = findMethod(owner, "<clinit>", "()V");

        int labels = 0;
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.RETURN) {
                InsnList code = new InsnList();
                emitIntArray(code, new int[]{1, 3, 5, 7, 9, 2, 4, 6, 8, 0});
                code.add(new FieldInsnNode(Opcodes.PUTSTATIC, "cf", "z", "[I"));
                emitStringArray(code, new String[]{"R", "T", "Y", "U", "I", "F", "G", "H", "J", "K"});
                code.add(new FieldInsnNode(Opcodes.PUTSTATIC, "cf", "A", "[Ljava/lang/String;"));
                clinit.instructions.insertBefore(insn, code);
                labels++;
            }
        }

        int touchPatches = 0;
        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!isInt(insn, 5)) continue;
                AbstractInsnNode fieldInsn = nextReal(insn);
                AbstractInsnNode mul = nextReal(fieldInsn);
                if (fieldInsn instanceof FieldInsnNode && fieldInsn.getOpcode() == Opcodes.GETSTATIC
                        && "cf".equals(((FieldInsnNode) fieldInsn).owner)
                        && "az".equals(((FieldInsnNode) fieldInsn).name)
                        && mul != null && mul.getOpcode() == Opcodes.IMUL) {
                    replaceInt(method, insn, 10);
                    touchPatches++;
                }
            }
        }

        MethodNode draw = null;
        for (MethodNode method : owner.methods) {
            if ("(Lbx;)V".equals(method.desc) && containsField(method, "cf", "A", "[Ljava/lang/String;")) {
                draw = method;
                break;
            }
        }
        require(draw != null, "Cannot locate cf horizontal hotbar renderer");
        InsnList center = new InsnList();
        center.add(new FieldInsnNode(Opcodes.GETSTATIC, "fu", "Z", "I"));
        center.add(new InsnNode(Opcodes.ICONST_5));
        center.add(new FieldInsnNode(Opcodes.GETSTATIC, "cf", "az", "I"));
        center.add(new InsnNode(Opcodes.IMUL));
        center.add(new InsnNode(Opcodes.ISUB));
        center.add(new FieldInsnNode(Opcodes.PUTSTATIC, "cf", "aD", "I"));
        draw.instructions.insert(center);

        require(labels == 1, "Expected one cf label patch, got " + labels);
        require(touchPatches >= 1, "Expected cf hotbar touch-width patch");
        System.out.println("  -> Hotbar labels/layout/touch OK: 10 centered slots");

        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        owner.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchHotbarMenu(InputStream in, String marker, boolean migrate) throws IOException {
        ClassReader cr = new ClassReader(in);
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        cr.accept(owner, 0);
        int menuPatches = 0;

        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof LdcInsnNode) || !marker.equals(((LdcInsnNode) insn).cst)) continue;
                AbstractInsnNode cursor = insn;
                for (int i = 0; i < 180 && cursor != null; i++, cursor = nextReal(cursor)) {
                    if (isInt(cursor, 5) && nextReal(cursor) instanceof JumpInsnNode) {
                        int opcode = nextReal(cursor).getOpcode();
                        if (opcode == Opcodes.IF_ICMPGE || opcode == Opcodes.IF_ICMPLT) {
                            replaceInt(method, cursor, 10);
                            menuPatches++;
                            break;
                        }
                    }
                }
            }
        }
        require(menuPatches == 1, "Expected one assignment-menu patch for " + marker + ", got " + menuPatches);

        if (migrate) {
            MethodNode loader = findMethod(owner, "a", "([B)V");
            require(loader != null && (loader.access & Opcodes.ACC_STATIC) != 0, "Cannot locate y saved-hotbar loader");
            InsnList migration = new InsnList();
            migration.add(new VarInsnNode(Opcodes.ALOAD, 0));
            migration.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "GhostMod", "expandHotbar", "([B)[B", false));
            migration.add(new VarInsnNode(Opcodes.ASTORE, 0));
            loader.instructions.insert(migration);
            System.out.println("  -> Skill assignment menu + 5-to-10 save migration OK");
        } else {
            System.out.println("  -> Item assignment menu expanded to 10 slots");
        }

        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        owner.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchFastCastController(InputStream in) throws IOException {
        ClassReader cr = new ClassReader(in);
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        cr.accept(owner, 0);
        MethodNode start = findMethod(owner, "a", "(Lfa;Let;IB)V");
        require(start != null, "Cannot locate dh attack-start method");

        int framePatches = 0;
        for (AbstractInsnNode insn = start.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.PUTFIELD
                    && "dh".equals(((FieldInsnNode) insn).owner)
                    && "m".equals(((FieldInsnNode) insn).name)
                    && "I".equals(((FieldInsnNode) insn).desc)) {
                LabelNode normal = new LabelNode();
                InsnList code = new InsnList();
                code.add(new VarInsnNode(Opcodes.ALOAD, 1));
                code.add(new FieldInsnNode(Opcodes.GETSTATIC, "cn", "g", "Lbq;"));
                code.add(new JumpInsnNode(Opcodes.IF_ACMPNE, normal));
                code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                code.add(new InsnNode(Opcodes.ICONST_1));
                code.add(new FieldInsnNode(Opcodes.PUTFIELD, "dh", "m", "I"));
                code.add(normal);
                start.instructions.insert(insn, code);
                framePatches++;
                break;
            }
        }

        int requestPatches = 0;
        for (MethodNode method : owner.methods) {
            if ("<clinit>".equals(method.name)) continue;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.PUTSTATIC
                        && "dh".equals(((FieldInsnNode) insn).owner)
                        && "p".equals(((FieldInsnNode) insn).name)
                        && "S".equals(((FieldInsnNode) insn).desc)) {
                    InsnList clamp = new InsnList();
                    clamp.add(new InsnNode(Opcodes.POP));
                    clamp.add(pushInt(FASTCAST_DELAY_MS));
                    method.instructions.insertBefore(insn, clamp);
                    requestPatches++;
                }
            }
        }

        int gatePatches = 0;
        MethodNode clinit = findMethod(owner, "<clinit>", "()V");
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.PUTSTATIC
                    && "dh".equals(((FieldInsnNode) insn).owner)
                    && "h".equals(((FieldInsnNode) insn).name)
                    && "I".equals(((FieldInsnNode) insn).desc)
                    && isInt(previousReal(insn), 600)) {
                replaceInt(clinit, previousReal(insn), FASTCAST_DELAY_MS);
                gatePatches++;
            }
        }
        require(framePatches == 1 && requestPatches == 1 && gatePatches == 1,
                "FASTCAST dh patch incomplete: frame/request/gate=" + framePatches + "/" + requestPatches + "/" + gatePatches);
        System.out.println("  -> FASTCAST packet frame=1, request/auto gate=" + FASTCAST_DELAY_MS + "ms");

        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        owner.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchFastCastRecovery(InputStream in) throws IOException {
        ClassReader cr = new ClassReader(in);
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        cr.accept(owner, 0);
        MethodNode update = findMethod(owner, "a", "(Lfa;)V");
        require(update != null, "Cannot locate dt animation-update method");

        LabelNode normal = new LabelNode();
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, "cn", "g", "Lbq;"));
        code.add(new JumpInsnNode(Opcodes.IF_ACMPNE, normal));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, "fa", "cG", "I"));
        code.add(new InsnNode(Opcodes.ICONST_2));
        code.add(new JumpInsnNode(Opcodes.IF_ICMPNE, normal));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, "fa", "cQ", "I"));
        code.add(new InsnNode(Opcodes.ICONST_2));
        code.add(new JumpInsnNode(Opcodes.IF_ICMPLT, normal));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new FieldInsnNode(Opcodes.PUTFIELD, "fa", "cG", "I"));
        code.add(new InsnNode(Opcodes.RETURN));
        code.add(normal);
        update.instructions.insert(code);
        System.out.println("  -> FASTCAST local recovery ends at frame 2");

        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        owner.accept(cw);
        return cw.toByteArray();
    }

    private static MethodNode findMethod(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        return null;
    }

    private static boolean containsOwnBooleanArray(MethodNode method, String owner) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (owner.equals(field.owner) && "[Z".equals(field.desc)) return true;
            }
        }
        return false;
    }

    private static boolean containsField(MethodNode method, String owner, String name, String desc) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (owner.equals(field.owner) && name.equals(field.name) && desc.equals(field.desc)) return true;
            }
        }
        return false;
    }

    private static boolean containsInts(MethodNode method, int... values) {
        for (int value : values) {
            boolean found = false;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (isInt(insn, value)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static void emitIntArray(InsnList code, int[] values) {
        code.add(pushInt(values.length));
        code.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT));
        for (int i = 0; i < values.length; i++) {
            code.add(new InsnNode(Opcodes.DUP));
            code.add(pushInt(i));
            code.add(pushInt(values[i]));
            code.add(new InsnNode(Opcodes.IASTORE));
        }
    }

    private static void emitStringArray(InsnList code, String[] values) {
        code.add(pushInt(values.length));
        code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
        for (int i = 0; i < values.length; i++) {
            code.add(new InsnNode(Opcodes.DUP));
            code.add(pushInt(i));
            code.add(new LdcInsnNode(values[i]));
            code.add(new InsnNode(Opcodes.AASTORE));
        }
    }

    private static AbstractInsnNode pushInt(int value) {
        if (value >= -1 && value <= 5) return new InsnNode(Opcodes.ICONST_0 + value);
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) return new IntInsnNode(Opcodes.BIPUSH, value);
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) return new IntInsnNode(Opcodes.SIPUSH, value);
        return new LdcInsnNode(Integer.valueOf(value));
    }

    private static void replaceInt(MethodNode method, AbstractInsnNode oldInsn, int value) {
        method.instructions.set(oldInsn, pushInt(value));
    }

    private static boolean isInt(AbstractInsnNode insn, int expected) {
        if (insn == null) return false;
        int opcode = insn.getOpcode();
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) return opcode - Opcodes.ICONST_0 == expected;
        if (insn instanceof IntInsnNode && (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH)) {
            return ((IntInsnNode) insn).operand == expected;
        }
        return insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer
                && ((Integer) ((LdcInsnNode) insn).cst).intValue() == expected;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn == null ? null : insn.getNext();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getNext();
        return cursor;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn == null ? null : insn.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getPrevious();
        return cursor;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void verifyOutput(String outputJarPath) throws Exception {
        ZipFile jar = new ZipFile(outputJarPath);
        int classCount = 0;
        int entryCount = 0;
        try {
            require(jar.getEntry("META-INF/MANIFEST.MF") != null, "Output JAR lost its manifest");
            require(jar.getEntry("GhostMod.class") != null, "Output JAR lacks GhostMod.class");
            Enumeration<? extends ZipEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                InputStream entryIn = jar.getInputStream(entry);
                try {
                    if (entry.getName().endsWith(".class")) {
                        new ClassReader(entryIn);
                        classCount++;
                    } else {
                        byte[] buffer = new byte[4096];
                        while (entryIn.read(buffer) != -1) {
                            // Consume every entry so bad local headers/CRC data fail before publish.
                        }
                    }
                    entryCount++;
                } finally {
                    entryIn.close();
                }
            }
        } finally {
            jar.close();
        }

        JarFile manifestJar = new JarFile(outputJarPath);
        try {
            require(manifestJar.getManifest() != null, "Output JAR manifest cannot be parsed");
        } finally {
            manifestJar.close();
        }

        URL[] urls = new URL[]{new File(outputJarPath).toURI().toURL(), new File("microemulator.jar").toURI().toURL()};
        URLClassLoader loader = new URLClassLoader(urls, null);
        try {
            String[] patchedClasses = new String[]{
                    "GhostMod", "bq", "cf", "fu", "y", "fo", "fa", "com.silverknight.a"
            };
            for (String className : patchedClasses) {
                Class.forName(className, false, loader);
            }
            Class<?> mod = Class.forName("GhostMod", true, loader);
            byte[] oldData = new byte[60];
            oldData[0] = 12;
            oldData[22] = 34;
            byte[] expanded = (byte[]) mod.getMethod("expandHotbar", byte[].class).invoke(null, new Object[]{oldData});
            require(expanded.length == 120, "Hotbar migration returned " + expanded.length + " bytes instead of 120");
            require(expanded[0] == 12 && expanded[42] == 34, "Hotbar migration did not preserve old slots");
            require(expanded[22] == (byte)-1 && expanded[62] == (byte)-1,
                    "Hotbar migration did not initialize new slots as empty");
        } finally {
            loader.close();
        }
        System.out.println("[+] Verify OK: " + entryCount + " entries read, manifest parsed, "
                + classCount + " classes parsed/loaded, migration 60->120 bytes");
    }
}
