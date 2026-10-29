import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Host regression for the staged Bạch Hổ Noclip defaults and login lifecycle. */
public final class BachHoNoclipDefaultHostTest implements Opcodes {
    private static final String HOOK = "LunaGeneratedLegacyHooks";
    private static final String RUNTIME = "HSOSKYRuntimeSupport";
    private static final String SEMANTIC = "luna/core/LunaSemanticHooks";
    private static final String CONTROLLER = "luna/features/AutoTradeController";
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(args.length == 1, "Usage: BachHoNoclipDefaultHostTest <patched.jar>");
        File jar = new File(args[0]);
        check(jar.isFile(), "Patched JAR exists");
        verifyBytecode(jar);
        verifySemanticController(jar);
        verifyRuntimeChoice(jar);
        System.out.println("OK: " + checks + " Noclip default, login boundary, semantic reset and map-persistence checks");
    }

    private static void verifyBytecode(File jarFile) throws Exception {
        JarFile jar = new JarFile(jarFile);
        try {
            ClassNode hook = read(jar, HOOK + ".class");
            ClassNode runtime = read(jar, RUNTIME + ".class");
            ClassNode controller = read(jar, CONTROLLER + ".class");
            ClassNode semantic = read(jar, SEMANTIC + ".class");
            check(initializerValue(hook, "noclipOn", "Z") == ICONST_0,
                    "Legacy hook Noclip default is OFF");
            check(initializerValue(runtime, "noClipEnabled", "Z") == ICONST_0,
                    "Runtime current Noclip default is OFF");
            check(initializerValue(runtime, "requestedNoClipEnabled", "Z") == ICONST_0,
                    "Runtime requested Noclip default is OFF");
            check(instanceInitializerValue(controller, "noclipEnabled", "Z") == ICONST_0,
                    "Semantic AutoTrade Noclip default is OFF");

            MethodNode reset = method(hook, "resetNoClipForFreshLogin", "()V");
            check(hasField(reset, GETSTATIC, "HSOSKYPetFeedSupport", "isDisconnected", "Z"),
                    "Fresh-login reset checks native disconnect state");
            check(hasCall(reset, INVOKESTATIC, HOOK, "onLoginScreen", "()Z"),
                    "Fresh-login reset checks the native login screen");
            check(hasField(reset, GETSTATIC, HOOK, "noclipLoginResetDone", "Z")
                    && hasField(reset, PUTSTATIC, HOOK, "noclipLoginResetDone", "Z"),
                    "Fresh-login reset is one-shot until in-game");
            check(hasField(reset, PUTSTATIC, HOOK, "n4SavedNoclip", "Z"),
                    "N4 base preference resets on a fresh login");
            check(hasCall(reset, INVOKESTATIC, RUNTIME, "resetNoClipForFreshLogin", "(Z)V"),
                    "Fresh-login reset updates runtime current/requested state");
            check(hasCall(reset, INVOKESTATIC, SEMANTIC, "resetNoClipForFreshLogin", "(Z)V"),
                    "Fresh-login reset updates semantic AutoTrade state");
            MethodNode onWorld = method(runtime, "onWorld", "(II)V");
            check(hasField(onWorld, GETSTATIC, RUNTIME, "requestedNoClipEnabled", "Z")
                    && hasField(onWorld, PUTSTATIC, RUNTIME, "noClipEnabled", "Z"),
                    "Map transitions restore the requested session choice");
            MethodNode n4Stop = method(hook, "n4Stop", "(Ljava/lang/String;)V");
            check(hasField(n4Stop, GETSTATIC, HOOK, "n4SavedNoclip", "Z")
                    && hasField(n4Stop, PUTSTATIC, HOOK, "noclipOn", "Z")
                    && hasCall(n4Stop, INVOKESTATIC, RUNTIME, "setNoClipEnabled", "(Z)V"),
                    "N4 still restores its saved base preference");
            MethodNode n4Want = method(hook, "n4WantNoclip", "(Z)V");
            check(hasCall(n4Want, INVOKESTATIC, RUNTIME, "setNoClipEnabled", "(Z)V"),
                    "N4 can still request temporary Noclip states");

            MethodNode observe = method(hook, "attackRecoveryObserve", "()V");
            List<AbstractInsnNode> code = real(observe);
            int resetCall = callIndex(code, HOOK, "resetNoClipForFreshLogin", "()V");
            check(resetCall >= 2 && code.get(resetCall - 1) instanceof JumpInsnNode
                    && code.get(resetCall - 1).getOpcode() == IFNE
                    && code.get(resetCall - 2).getOpcode() == ILOAD
                    && ((VarInsnNode) code.get(resetCall - 2)).var == 0,
                    "Lifecycle reset runs only in the not-in-game branch");
            check(rearmAtInGameEntry(code), "Login-reset guard re-arms only after the in-game branch");
            check(callbackHasOneShotGuard(code), "Login callback is gated and consumed once at world entry");
            MethodNode callback = method(hook, "onFreshLoginWorldEntry", "()V");
            List<AbstractInsnNode> callbackCode = real(callback);
            check((callbackCode.size() == 1 && callbackCode.get(0).getOpcode() == RETURN)
                    || (callbackCode.size() == 2 && callbackCode.get(1).getOpcode() == RETURN
                    && hasCall(callback, INVOKESTATIC, "luna/core/BachHoClientHooks", "onLoginEntry", "()V")),
                    "World-entry callback is empty or calls the verified automation extension once");
        } finally { jar.close(); }
    }

    private static void verifySemanticController(File jar) throws Exception {
        URLClassLoader loader = loader(jar);
        try {
            Class<?> bridge = loader.loadClass("luna.api.LunaBridgeApi");
            Class<?> clock = loader.loadClass("luna.api.LunaClock");
            Class<?> movementType = loader.loadClass("luna.core.MovementArbiter");
            Object movement = movementType.getConstructor().newInstance();
            Class<?> controller = loader.loadClass("luna.features.AutoTradeController");
            Object instance = controller.getConstructor(bridge, clock, movementType)
                    .newInstance(null, null, movement);
            Method getter = controller.getMethod("isNoclipEnabled");
            Method toggle = controller.getMethod("handleMenuAction", Integer.TYPE);
            Method reset = controller.getMethod("resetNoClipForFreshLogin", Boolean.TYPE);
            check(Boolean.FALSE.equals(getter.invoke(instance)), "New AutoTrade controller starts OFF");
            toggle.invoke(instance, Integer.valueOf(-24));
            check(Boolean.TRUE.equals(getter.invoke(instance)), "Explicit menu action enables semantic Noclip");
            reset.invoke(instance, Boolean.FALSE);
            check(Boolean.TRUE.equals(getter.invoke(instance)), "Non-login map gap preserves semantic choice");
            reset.invoke(instance, Boolean.TRUE);
            check(Boolean.FALSE.equals(getter.invoke(instance)), "Fresh login resets semantic choice to OFF");

            Class<?> semantic = loader.loadClass("luna.core.LunaSemanticHooks");
            Method semanticAction = semantic.getMethod("handleMenuAction", Integer.TYPE, Integer.TYPE);
            Field runtimeField = semantic.getDeclaredField("RUNTIME");
            runtimeField.setAccessible(true);
            Object runtime = runtimeField.get(null);
            Object shared = runtime.getClass().getMethod("autoTrade").invoke(runtime);
            check(Boolean.FALSE.equals(getter.invoke(shared)), "Shared semantic controller starts OFF");
            semanticAction.invoke(null, Integer.valueOf(-24), Integer.valueOf(0));
            check(Boolean.TRUE.equals(getter.invoke(shared)), "Generated menu bridge toggles semantic Noclip");
            semantic.getMethod("resetNoClipForFreshLogin", Boolean.TYPE)
                    .invoke(null, Boolean.FALSE);
            check(Boolean.TRUE.equals(getter.invoke(shared)), "Semantic bridge preserves the map-session choice");
            semantic.getMethod("resetNoClipForFreshLogin", Boolean.TYPE)
                    .invoke(null, Boolean.TRUE);
            check(Boolean.FALSE.equals(getter.invoke(shared)), "Semantic bridge clears the previous session");
        } finally { loader.close(); }
    }

    private static void verifyRuntimeChoice(File jar) throws Exception {
        URLClassLoader loader = loader(jar);
        try {
            Class<?> runtime = loader.loadClass(RUNTIME);
            Method set = runtime.getMethod("setNoClipEnabled", Boolean.TYPE);
            Method reset = runtime.getMethod("resetNoClipForFreshLogin", Boolean.TYPE);
            Method current = runtime.getMethod("isNoClipEnabled");
            Method world = runtime.getMethod("onWorld", Integer.TYPE, Integer.TYPE);
            Field requested = runtime.getDeclaredField("requestedNoClipEnabled");
            requested.setAccessible(true);

            set.invoke(null, Boolean.TRUE);
            reset.invoke(null, Boolean.FALSE);
            world.invoke(null, Integer.valueOf(201), Integer.valueOf(0));
            check(Boolean.TRUE.equals(current.invoke(null)) && Boolean.TRUE.equals(requested.get(null)),
                    "Map transition preserves the enabled runtime/requested choice");
            reset.invoke(null, Boolean.TRUE);
            check(Boolean.FALSE.equals(current.invoke(null)) && Boolean.FALSE.equals(requested.get(null)),
                    "Fresh login clears both runtime states");
            world.invoke(null, Integer.valueOf(202), Integer.valueOf(0));
            check(Boolean.FALSE.equals(current.invoke(null)), "Next map remains OFF after the fresh-login reset");
        } finally { loader.close(); }
    }

    private static URLClassLoader loader(File jar) throws Exception {
        return new URLClassLoader(new URL[] { jar.toURI().toURL() }, null);
    }

    private static ClassNode read(JarFile jar, String entry) throws Exception {
        JarEntry value = jar.getJarEntry(entry);
        check(value != null, "Archive contains " + entry);
        InputStream in = jar.getInputStream(value);
        try {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        } finally { in.close(); }
    }

    private static int initializerValue(ClassNode node, String fieldName, String desc) {
        MethodNode clinit = method(node, "<clinit>", "()V");
        int found = 0;
        int value = -1;
        for (AbstractInsnNode insn : clinit.instructions.toArray()) {
            if (!field(insn, PUTSTATIC, node.name, fieldName, desc)) continue;
            AbstractInsnNode previous = previousReal(insn);
            check(previous != null && (previous.getOpcode() == ICONST_0 || previous.getOpcode() == ICONST_1),
                    "Boolean static initializer is a constant");
            value = previous.getOpcode();
            found++;
        }
        check(found == 1, "Exactly one static initializer for " + node.name + "." + fieldName);
        return value;
    }

    private static int instanceInitializerValue(ClassNode node, String fieldName, String desc) {
        int found = 0;
        int value = -1;
        for (MethodNode method : node.methods) {
            if (!"<init>".equals(method.name)) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!field(insn, PUTFIELD, node.name, fieldName, desc)) continue;
                AbstractInsnNode previous = previousReal(insn);
                check(previous != null && (previous.getOpcode() == ICONST_0 || previous.getOpcode() == ICONST_1),
                        "Boolean instance initializer is a constant");
                value = previous.getOpcode();
                found++;
            }
        }
        check(found == 1, "Exactly one instance initializer for " + node.name + "." + fieldName);
        return value;
    }

    private static boolean rearmAtInGameEntry(List<AbstractInsnNode> code) {
        for (AbstractInsnNode insn : code) {
            if (!(insn instanceof JumpInsnNode) || insn.getOpcode() != IFNE) continue;
            AbstractInsnNode first = nextReal(((JumpInsnNode) insn).label);
            AbstractInsnNode second = first == null ? null : nextReal(first);
            if (first != null && first.getOpcode() == ICONST_0
                    && field(second, PUTSTATIC, HOOK, "noclipLoginResetDone", "Z")) return true;
        }
        return false;
    }

    private static boolean callbackHasOneShotGuard(List<AbstractInsnNode> code) {
        for (int i = 0; i + 4 < code.size(); i++) {
            if (!field(code.get(i), GETSTATIC, HOOK, "loginEntryAwaitingWorld", "Z")
                    || !(code.get(i + 1) instanceof JumpInsnNode)
                    || code.get(i + 1).getOpcode() != IFEQ
                    || code.get(i + 2).getOpcode() != ICONST_0
                    || !field(code.get(i + 3), PUTSTATIC, HOOK, "loginEntryAwaitingWorld", "Z")
                    || !call(code.get(i + 4), INVOKESTATIC, HOOK, "onFreshLoginWorldEntry", "()V")) continue;
            return true;
        }
        return false;
    }

    private static int callIndex(List<AbstractInsnNode> code, String owner, String name, String desc) {
        int result = -1;
        for (int i = 0; i < code.size(); i++) {
            if (!call(code.get(i), INVOKESTATIC, owner, name, desc)) continue;
            check(result < 0, "Unique call site for " + owner + "." + name + desc);
            result = i;
        }
        return result;
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        MethodNode found = null;
        for (MethodNode method : node.methods) {
            if (!name.equals(method.name) || !desc.equals(method.desc)) continue;
            check(found == null, "Unique method " + node.name + "." + name + desc);
            found = method;
        }
        check(found != null, "Method exists: " + node.name + "." + name + desc);
        return found;
    }

    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() >= 0) result.add(insn);
        return result;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        AbstractInsnNode value = insn.getPrevious();
        while (value != null && value.getOpcode() < 0) value = value.getPrevious();
        return value;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode value = insn.getNext();
        while (value != null && value.getOpcode() < 0) value = value.getNext();
        return value;
    }

    private static boolean hasField(MethodNode method, int opcode, String owner, String name, String desc) {
        for (AbstractInsnNode insn : method.instructions.toArray())
            if (field(insn, opcode, owner, name, desc)) return true;
        return false;
    }

    private static boolean hasCall(MethodNode method, int opcode, String owner, String name, String desc) {
        for (AbstractInsnNode insn : method.instructions.toArray())
            if (call(insn, opcode, owner, name, desc)) return true;
        return false;
    }

    private static boolean field(AbstractInsnNode insn, int opcode, String owner, String name, String desc) {
        if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != opcode) return false;
        FieldInsnNode value = (FieldInsnNode) insn;
        return owner.equals(value.owner) && name.equals(value.name) && desc.equals(value.desc);
    }

    private static boolean call(AbstractInsnNode insn, int opcode, String owner, String name, String desc) {
        if (!(insn instanceof MethodInsnNode) || insn.getOpcode() != opcode) return false;
        MethodInsnNode value = (MethodInsnNode) insn;
        return owner.equals(value.owner) && name.equals(value.name) && desc.equals(value.desc);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        checks++;
    }
}
