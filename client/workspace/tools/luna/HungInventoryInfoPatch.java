import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Exact native menu/HUD hooks for the Hung three-state information display. */
public final class HungInventoryInfoPatch implements Opcodes {
    private static final String SUPPORT = "HungInventoryInfo";
    private static final String FONT = n("lilillil");
    private static final String GRAPHICS = n("llilll");
    private static final int ACTION = -77;

    private HungInventoryInfoPatch() { }

    private static String n(String suffix) {
        StringBuilder value = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) value.append('i');
        return value.append(suffix).toString();
    }

    private static void check(boolean ok, String why) {
        if (!ok) throw new IllegalStateException(why);
    }

    private static ClassNode node(byte[] bytes) {
        check(bytes != null, "Missing class bytes");
        ClassNode value = new ClassNode();
        new ClassReader(bytes).accept(value, 0);
        return value;
    }

    private static byte[] write(ClassNode value, byte[] original) {
        ClassWriter writer = new ClassWriter(new ClassReader(original), ClassWriter.COMPUTE_MAXS);
        value.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        MethodNode found = null;
        for (MethodNode candidate : owner.methods) {
            if (candidate.name.equals(name) && candidate.desc.equals(descriptor)) {
                check(found == null, "Duplicate method " + owner.name + "." + name + descriptor);
                found = candidate;
            }
        }
        check(found != null, "Missing method " + owner.name + "." + name + descriptor);
        return found;
    }

    private static int integer(AbstractInsnNode instruction) {
        if (instruction instanceof IntInsnNode) return ((IntInsnNode)instruction).operand;
        if (instruction instanceof InsnNode) {
            int opcode = instruction.getOpcode();
            if (opcode >= ICONST_M1 && opcode <= ICONST_5) return opcode - ICONST_0;
        }
        if (instruction instanceof LdcInsnNode && ((LdcInsnNode)instruction).cst instanceof Integer)
            return ((Integer)((LdcInsnNode)instruction).cst).intValue();
        return Integer.MIN_VALUE;
    }

    private static boolean call(AbstractInsnNode instruction, int opcode, String owner, String name, String descriptor) {
        if (!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != opcode) return false;
        MethodInsnNode call = (MethodInsnNode)instruction;
        return call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor);
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode instruction) {
        for (AbstractInsnNode value = instruction == null ? null : instruction.getNext(); value != null; value = value.getNext())
            if (value.getOpcode() >= 0) return value;
        return null;
    }

    private static String required(Properties catalog, String key) {
        String value = catalog.getProperty(key);
        check(value != null && value.length() > 0, "Missing catalog property " + key);
        return value;
    }

    private static void patchMenu(ClassNode info, Properties catalog) {
        MethodNode addMenu = method(info, "addMenu", "(Let;)V");
        int rows = 0, labels = 0;
        for (AbstractInsnNode instruction : addMenu.instructions.toArray()) {
            if (integer(instruction) == ACTION) rows++;
            if (call(instruction, INVOKESTATIC, "key", "d", "([B)Ljava/lang/String;")) {
                addMenu.instructions.insertBefore(instruction, new InsnNode(POP));
                addMenu.instructions.set(instruction, new MethodInsnNode(INVOKESTATIC, SUPPORT,
                        "menuLabel", "()Ljava/lang/String;", false));
                labels++;
            }
        }
        check(rows == 1 && labels == 1, "Expected one native -77 information row and label decoder");
        catalog.setProperty("inventory.info.menu.owner", info.name);
        catalog.setProperty("inventory.info.menu.method", addMenu.name);
        catalog.setProperty("inventory.info.menu.descriptor", addMenu.desc);
        catalog.setProperty("inventory.info.menu.action", String.valueOf(ACTION));
        catalog.setProperty("inventory.info.menu.label", "HungInventoryInfo.menuLabel()Ljava/lang/String;");
    }

    private static void patchAction(ClassNode info, Properties catalog) {
        MethodNode action = method(info, "onAction", "(II)Z");
        boolean nativeAction = false;
        for (AbstractInsnNode instruction : action.instructions.toArray()) {
            AbstractInsnNode following = nextReal(instruction);
            if (integer(instruction) == ACTION && following instanceof JumpInsnNode
                    && following.getOpcode() == IF_ICMPNE) nativeAction = true;
        }
        check(nativeAction, "Native information action -77 branch changed");
        LabelNode continueNative = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ILOAD, 1));
        hook.add(new IntInsnNode(BIPUSH, ACTION));
        hook.add(new JumpInsnNode(IF_ICMPNE, continueNative));
        hook.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "cycle", "()V", false));
        hook.add(new InsnNode(ICONST_1));
        hook.add(new InsnNode(IRETURN));
        hook.add(continueNative);
        action.instructions.insertBefore(action.instructions.getFirst(), hook);
        catalog.setProperty("inventory.info.action.owner", info.name);
        catalog.setProperty("inventory.info.action.method", action.name);
        catalog.setProperty("inventory.info.action.descriptor", action.desc);
        catalog.setProperty("inventory.info.action.branch", "-77 cycles OFF->ON->COMPACT->OFF and returns handled");
    }

    private static void patchHud(Map<String,byte[]> entries, Properties catalog) {
        String owner = required(catalog, "hook.hud.owner");
        String name = required(catalog, "hook.hud.method");
        String descriptor = required(catalog, "hook.hud.descriptor");
        String infoOwner = required(catalog, "hud.info.owner");
        String bridgeOwner = required(catalog, "bridge.owner");
        check(owner.equals(infoOwner), "HUD render and native information module owner differ");
        check(descriptor.equals("(L" + GRAPHICS + ";)V"), "Native HUD graphics descriptor changed");
        byte[] original = entries.get(owner + ".class");
        ClassNode hud = node(original);
        MethodNode render = method(hud, name, descriptor);
        InsnList initializeMode = new InsnList();
        initializeMode.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "mode", "()I", false));
        initializeMode.add(new InsnNode(POP));
        initializeMode.add(new VarInsnNode(ALOAD, 0));
        initializeMode.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "draw", "(L" + GRAPHICS + ";)V", false));
        render.instructions.insertBefore(render.instructions.getFirst(), initializeMode);
        int hooks = 0;
        for (AbstractInsnNode instruction : render.instructions.toArray()) {
            if (call(instruction, INVOKESTATIC, bridgeOwner, "hud", "(Ljava/lang/Object;II)V")) {
                hooks++;
            }
        }
        check(hooks == 1, "Expected one existing native HUD bridge call");
        entries.put(owner + ".class", write(hud, original));
        catalog.setProperty("inventory.info.hud.owner", owner);
        catalog.setProperty("inventory.info.hud.method", name);
        catalog.setProperty("inventory.info.hud.descriptor", descriptor);
        catalog.setProperty("inventory.info.hud.draw", SUPPORT + ".draw(L" + GRAPHICS + ";)V");
        catalog.setProperty("inventory.info.hud.position.logical", "6,110");
        catalog.setProperty("inventory.info.hud.font", FONT + "." + n("liililillii"));
        catalog.setProperty("inventory.info.hud.style", "native gold bold font; draw flag true");
    }

    private static void checkField(Map<String,byte[]> entries, String owner, String name, String descriptor,
                                   boolean isStatic, String role) {
        ClassNode type = node(entries.get(owner + ".class"));
        FieldNode found = null;
        for (FieldNode field : type.fields)
            if (field.name.equals(name) && field.desc.equals(descriptor)) {
                check(found == null, "Duplicate " + role + " field");
                found = field;
            }
        check(found != null && (found.access & ACC_PUBLIC) != 0
                && ((found.access & ACC_STATIC) != 0) == isStatic, "Invalid exact " + role + " field " + owner + "." + name + descriptor);
    }

    private static void verifyNativeSymbols(Map<String,byte[]> entries, Properties catalog) {
        String infoOwner = required(catalog, "hud.info.owner");
        String infoField = required(catalog, "hud.info.field");
        checkField(entries, infoOwner, infoField, "Z", true, "native HUD info flag");
        checkField(entries, n("llilli"), n("liiillili"), "Let;", true, "inventory vector");
        checkField(entries, n("lliiil"), n("lliliili"), "S", true, "inventory capacity");
        ClassNode vector = node(entries.get("et.class"));
        method(vector, "c", "()I");
        ClassNode font = node(entries.get(FONT + ".class"));
        boolean yellow = false;
        for (FieldNode field : font.fields)
            if (field.name.equals(n("liililillii")) && field.desc.equals("L" + FONT + ";")
                    && (field.access & (ACC_PUBLIC | ACC_STATIC)) == (ACC_PUBLIC | ACC_STATIC)) yellow = true;
        check(yellow, "Native bold/gold HUD font field changed");
        method(font, n(""), "(L" + GRAPHICS + ";Ljava/lang/String;IIIZ)V");
    }

    /** Patches the native -77 MOD row/action and adds the compiled single-class support. */
    public static void apply(Map<String,byte[]> entries, byte[] compiledInventorySupport, Properties catalog) {
        check(entries != null && compiledInventorySupport != null && catalog != null, "Null patch input");
        Map<String,byte[]> originalEntries = new LinkedHashMap<String,byte[]>(entries);
        String infoOwner = required(catalog, "hud.info.owner");
        String infoField = required(catalog, "hud.info.field");
        check("Z".equals(catalog.getProperty("hud.info.descriptor", "Z")), "Native HUD info flag must be boolean");
        check(!entries.containsKey(SUPPORT + ".class"), "Support class already exists");
        ClassNode helper = node(compiledInventorySupport);
        check(SUPPORT.equals(helper.name), "Compiled support class name changed");
        check((helper.version & 65535) <= 51, "Support class exceeds Java 7 bytecode");
        check(helper.innerClasses == null || helper.innerClasses.isEmpty(), "Support must remain a single class");
        method(helper, "mode", "()I");
        method(helper, "menuLabel", "()Ljava/lang/String;");
        method(helper, "cycle", "()V");
        method(helper, "inventoryLine", "()Ljava/lang/String;");
        method(helper, "draw", "(L" + GRAPHICS + ";)V");
        verifyNativeSymbols(entries, catalog);

        byte[] infoBytes = entries.get(infoOwner + ".class");
        check(infoBytes != null, "Missing native info module");
        ClassNode info = node(infoBytes);
        boolean nativeFlag = false;
        for (FieldNode field : info.fields) if (field.name.equals(infoField) && "Z".equals(field.desc)
                && (field.access & (ACC_PUBLIC | ACC_STATIC)) == (ACC_PUBLIC | ACC_STATIC)) nativeFlag = true;
        check(nativeFlag, "Catalog native HUD flag is not public static boolean");

        patchMenu(info, catalog);
        patchAction(info, catalog);
        entries.put(infoOwner + ".class", write(info, infoBytes));
        patchHud(entries, catalog);
        entries.put(SUPPORT + ".class", compiledInventorySupport);

        catalog.setProperty("inventory.info.changed.classes", infoOwner + ".class");
        catalog.setProperty("inventory.info.support.class", SUPPORT);
        catalog.setProperty("inventory.info.state.values", "0=OFF,1=ON,2=COMPACT");
        catalog.setProperty("inventory.info.default", "ON");
        catalog.setProperty("inventory.info.rms.name", "hbs_inventory_info_v1");
        catalog.setProperty("inventory.info.native.flag.owner", infoOwner);
        catalog.setProperty("inventory.info.native.flag.field", infoField);
        catalog.setProperty("inventory.info.native.flag.descriptor", "Z");
        catalog.setProperty("inventory.info.inventory.vector.owner", n("llilli"));
        catalog.setProperty("inventory.info.inventory.vector.field", n("liiillili"));
        catalog.setProperty("inventory.info.inventory.vector.descriptor", "Let;");
        catalog.setProperty("inventory.info.inventory.capacity.owner", n("lliiil"));
        catalog.setProperty("inventory.info.inventory.capacity.field", n("lliliili"));
        catalog.setProperty("inventory.info.inventory.capacity.descriptor", "S");
        catalog.setProperty("inventory.info.inventory.size.method", "et.c()I");
        catalog.setProperty("inventory.info.inventory.validity", "world/player ready; non-null bag vector; capacity > 0; no fallback capacity");
        catalog.setProperty("inventory.info.resources.unchanged", "true");

        for (Map.Entry<String,byte[]> original : originalEntries.entrySet()) {
            String entry = original.getKey();
            if (entry.equals(infoOwner + ".class"))
                check(!Arrays.equals(original.getValue(), entries.get(entry)), "Native info/HUD class was not patched");
            else check(Arrays.equals(original.getValue(), entries.get(entry)), "Entry changed outside the native info class: " + entry);
        }
        for (String entry : entries.keySet())
            check(originalEntries.containsKey(entry) || entry.equals(SUPPORT + ".class"), "Unexpected archive addition " + entry);
    }
}
