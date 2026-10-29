import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreNotFoundException;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.util.MemoryRecordStoreManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Independent archive, source-parity and in-memory RMS regression for Chibi Final X3. */
public final class ChibiFinalX3HostTest implements Opcodes {
    private static final String AUTH = ChibiFinalX3Packer.AUTH;
    private static final String MANIFEST = ChibiFinalX3Packer.MANIFEST;
    private static final String LOGIN_DESC =
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
            + "Ljava/lang/String;Ljava/lang/String;IB)V";
    private static final String[] LIST_MARKERS = {
        "size item quest=", "size material=", "size item="
    };
    private static final String[] SLOTS = ChibiFinalX3Packer.SLOTS;
    private static int checks;

    private ChibiFinalX3HostTest() { }

    public static void main(String[] args) throws Exception {
        check(args.length == 3, "candidate-x3.jar chibi_final.jar launcher-template.jar");
        File candidate = new File(args[0]), sourceFile = new File(args[1]), templateFile = new File(args[2]);
        check(candidate.isFile() && sourceFile.isFile() && templateFile.isFile(), "All three JAR inputs exist");
        check(ChibiFinalX3Packer.SOURCE_SHA.equals(HsoChildX3Packer.hash(sourceFile)), "Pinned Chibi Final source hash");
        check(ChibiFinalX3Packer.TEMPLATE_SHA.equals(HsoChildX3Packer.hash(templateFile)), "Pinned launcher template hash");

        Map<String, byte[]> source = HsoChildX3Packer.readJar(sourceFile);
        Map<String, byte[]> template = HsoChildX3Packer.readJar(templateFile);
        Map<String, byte[]> packed = HsoChildX3Packer.readJar(candidate);
        Map<String, byte[]> sourceClasses = classes(source);
        verifyArchive(source, template, packed, sourceClasses);
        CriticalMethods critical = verifyCriticalSource(sourceClasses);
        verifySourceReflection(sourceClasses);
        verifySlotParity(sourceClasses, packed, critical);
        verifyLinkage(packed, candidate);
        verifyRuntime(candidate, sourceFile);
        check(ChibiFinalX3Packer.SOURCE_SHA.equals(HsoChildX3Packer.hash(sourceFile))
                && ChibiFinalX3Packer.TEMPLATE_SHA.equals(HsoChildX3Packer.hash(templateFile)),
                "Pinned source and template inputs remained unchanged");
        System.out.println("ChibiFinalX3HostTest PASS (" + checks + " checks); 676 linked classes; "
                + "all 217 source classes preserve login, inventory and self-init methods in default/b/c; "
                + "3 isolated RMS/config slots; synthetic memory data only");
    }

    private static void verifyArchive(Map<String, byte[]> source, Map<String, byte[]> template,
            Map<String, byte[]> packed, Map<String, byte[]> sourceClasses) throws Exception {
        check(sourceClasses.size() == ChibiFinalX3Packer.SOURCE_CLASSES, "Pinned source has 217 game classes");
        check(sourceClasses.containsKey("TemMidlet") && sourceClasses.containsKey(AUTH), "Source MIDlet and auth helper exist");
        check(hasMethod(sourceClasses.get(AUTH), "II", "(Ljava/lang/String;[B)Ljava/lang/String;"),
                "Source authentication helper keeps its username/session-key ABI");

        Attributes original = manifest(source.get(MANIFEST));
        check("HSO CHIBI".equals(original.getValue("MIDlet-Name"))
                && "HSO".equals(original.getValue("MIDlet-Vendor")), "Pinned single-client identity");
        check(original.getValue("MIDlet-1") != null && original.getValue("MIDlet-1").endsWith(",TemMidlet"),
                "Source entrypoint remains TemMidlet");

        Attributes attributes = manifest(packed.get(MANIFEST));
        check(ChibiFinalX3Packer.SUITE.equals(attributes.getValue("MIDlet-Name"))
                && ChibiFinalX3Packer.VENDOR.equals(attributes.getValue("MIDlet-Vendor")), "Separate X3 suite identity");
        check(attributes.getValue("MIDlet-1") != null && attributes.getValue("MIDlet-1").endsWith(",MrQuyet.MrQuyet")
                && attributes.getValue("MIDlet-2") == null && attributes.getValue("MIDlet-3") == null,
                "Only the template launcher is a native MIDlet");
        check("a,b,c".equals(attributes.getValue("HSO-X3-RMS-Prefixes")), "Manifest declares all isolated RMS prefixes");
        check(ChibiFinalX3Packer.SOURCE_SHA.equalsIgnoreCase(attributes.getValue("HSO-X3-Source-SHA-256")),
                "Manifest binds the source SHA");
        check(ChibiFinalX3Packer.TEMPLATE_SHA.equalsIgnoreCase(attributes.getValue("HSO-X3-Template-SHA-256")),
                "Manifest binds the launcher template SHA");
        for (int slot = 0; slot < 3; slot++) {
            String midlet = slot == 0 ? "TemMidlet" : slot == 1 ? "b.TemMidlet" : "c.TemMidlet";
            String row = attributes.getValue(String.valueOf(slot));
            check(row != null && row.contains(ChibiFinalX3Packer.ROW + " " + (slot + 1)
                    + ",/MrQuyet/mid.png," + midlet + ",/" + ChibiFinalX3Packer.INI), "Virtual client row " + slot);
        }
        check(Arrays.equals(source.get(MANIFEST), packed.get(ChibiFinalX3Packer.INI)),
                "Virtual client INI is the source manifest byte-for-byte");

        Set<String> expected = expectedEntries(source, template, sourceClasses.keySet());
        check(expected.equals(packed.keySet()), "Candidate archive contains exactly source resources, X3 classes and wrappers");
        for (String name : source.keySet()) {
            if (!name.endsWith(".class") && !MANIFEST.equals(name))
                check(Arrays.equals(source.get(name), packed.get(name)), "Source resource unchanged: " + name);
        }
        int wrappers = 0;
        for (String name : template.keySet()) {
            if ((name.startsWith("lib/") || name.startsWith("MrQuyet/")) && name.endsWith(".class")) {
                wrappers++;
                check(Arrays.equals(template.get(name), packed.get(name)), "Template wrapper byte-identical: " + name);
            }
        }
        check(wrappers == 19, "Exactly 19 launcher wrapper classes are retained");
        check(template.get("MrQuyet/mid.png") != null
                && Arrays.equals(template.get("MrQuyet/mid.png"), packed.get("MrQuyet/mid.png")),
                "Launcher icon byte-identical to template");
        check(classCount(packed) == ChibiFinalX3Packer.PACKED_CLASSES, "X3 archive has exactly 676 classes");
        check(packed.containsKey(AUTH + ".class") && packed.containsKey("b/" + AUTH + ".class")
                && packed.containsKey("c/" + AUTH + ".class"), "Authentication helper exists in all three slots");
        for (String slot : SLOTS)
            check(hasMethod(packed.get(slot + AUTH + ".class"), "II", "(Ljava/lang/String;[B)Ljava/lang/String;"),
                    "Authentication helper ABI exists in slot " + slot);
    }

    private static Set<String> expectedEntries(Map<String, byte[]> source, Map<String, byte[]> template,
            Set<String> classNames) {
        Set<String> expected = new LinkedHashSet<String>();
        for (String name : source.keySet()) {
            if (name.endsWith(".class")) {
                for (int slot = 0; slot < 3; slot++) expected.add(SLOTS[slot] + name);
            } else if (!MANIFEST.equals(name)) expected.add(name);
        }
        for (int slot = 0; slot < 3; slot++) {
            expected.add(SLOTS[slot] + "Static.class");
            expected.add(SLOTS[slot] + "X3SlotSupport.class");
        }
        expected.add(MANIFEST);
        expected.add(ChibiFinalX3Packer.INI);
        expected.add("MrQuyet/mid.png");
        for (String name : template.keySet())
            if ((name.startsWith("lib/") || name.startsWith("MrQuyet/")) && name.endsWith(".class")) expected.add(name);
        check(classNames.size() == ChibiFinalX3Packer.SOURCE_CLASSES, "Expected-entry source class set");
        return expected;
    }

    private static CriticalMethods verifyCriticalSource(Map<String, byte[]> sourceClasses) {
        MethodRef login = null, inventory = null;
        for (Map.Entry<String, byte[]> entry : sourceClasses.entrySet()) {
            ClassNode node = node(entry.getValue());
            for (MethodNode method : node.methods) {
                if (LOGIN_DESC.equals(method.desc)) {
                    check(login == null, "Login serializer is unique");
                    login = new MethodRef(entry.getKey(), method.name, method.desc);
                    verifyLoginMethod(node.name, method);
                }
                if (hasString(method, LIST_MARKERS[0]) && hasString(method, LIST_MARKERS[1])
                        && hasString(method, LIST_MARKERS[2])) {
                    check(inventory == null, "Shared inventory reader is unique");
                    inventory = new MethodRef(entry.getKey(), method.name, method.desc);
                    verifyInventoryMethod(method);
                }
            }
        }
        check(login != null && inventory != null, "Current login and inventory fixes are present in the source");
        MethodRef selfRef = null;
        MethodNode self = null;
        SelfSite site = null;
        for (Map.Entry<String, byte[]> entry : sourceClasses.entrySet()) {
            ClassNode type = node(entry.getValue());
            for (MethodNode method : type.methods) {
                SelfSite candidate = findSelfSite(method);
                if (candidate != null) {
                    check(self == null, "Self-initialization capacity/page site is unique");
                    self = method; site = candidate;
                    selfRef = new MethodRef(entry.getKey(), method.name, method.desc);
                }
            }
        }
        check(self != null && site != null, "Fixed self-initialization reader site is present");
        check((self.access & ACC_STATIC) != 0 && Type.getArgumentTypes(self.desc).length == 1
                && Type.getReturnType(self.desc).getSort() == Type.VOID, "Self-initialization receiver signature");
        check("readShort".equals(site.reader.name) && "S".equals(site.capacity.desc) && "B".equals(site.pages.desc),
                "Self initialization keeps signed-short capacity and byte page fields");
        verifySelfPageClamp(self, site.pages);
        return new CriticalMethods(login, inventory, selfRef);
    }

    private static void verifyLoginMethod(String owner, MethodNode method) {
        int utf = 0, authCalls = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)insn;
            if (call.owner.equals("java/io/DataOutputStream") && call.name.equals("writeUTF")) utf++;
            if (call.owner.equals(AUTH) && call.name.equals("II")
                    && call.desc.equals("(Ljava/lang/String;[B)Ljava/lang/String;")) authCalls++;
        }
        check(utf == 8 && authCalls == 1 && hasString(method, "LOCK"),
                "Fixed login serializer keeps eight UTF writes, helper call and LOCK tail: " + owner + "." + method.name);
    }

    private static void verifyInventoryMethod(MethodNode method) {
        String[] prefix = {"readShort", "readByte", "readByte", "readLong", "readInt", "readByte", "readShort"};
        int[] reads = dataReads(method);
        check(reads.length >= prefix.length + 3, "Inventory receiver keeps its complete native read prefix");
        String[] names = dataReadNames(method);
        for (int i = 0; i < prefix.length; i++) check(prefix[i].equals(names[i]), "Fixed inventory prefix reader " + i);
        int markerReaders = 0;
        for (String marker : LIST_MARKERS) {
            MethodInsnNode reader = readBeforeString(method, marker);
            check("readShort".equals(reader.name) && "()S".equals(reader.desc), "Inventory list count remains short-wide: " + marker);
            markerReaders++;
        }
        check(markerReaders == 3, "All three inventory list counts are short-wide");
    }

    private static void verifySelfPageClamp(MethodNode method, FieldInsnNode pages) {
        boolean clamp = false;
        AbstractInsnNode[] insns = method.instructions.toArray();
        for (AbstractInsnNode insn : insns) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == GETSTATIC
                    && sameField((FieldInsnNode)insn, pages)) {
                AbstractInsnNode one = nextReal(insn), branch = nextReal(one), putOne = nextReal(branch), put = nextReal(putOne);
                if (one != null && one.getOpcode() == ICONST_1
                    && branch instanceof org.objectweb.asm.tree.JumpInsnNode
                    && putOne != null && putOne.getOpcode() == ICONST_1
                    && put instanceof FieldInsnNode && put.getOpcode() == PUTSTATIC
                    && sameField((FieldInsnNode)put, pages)) {
                clamp = true;
                break;
                }
            }
        }
        check(clamp, "Self initialization clamps page count to at least one");
    }

    private static SelfSite findSelfSite(MethodNode method) {
        SelfSite found = null;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode reader = (MethodInsnNode)insn;
            if (!reader.owner.equals("java/io/DataInputStream") || !reader.name.equals("readShort")) continue;
            AbstractInsnNode n = nextReal(reader);
            if (n != null && n.getOpcode() == I2S) n = nextReal(n);
            boolean retainedCapacity = n != null && n.getOpcode() == DUP;
            if (retainedCapacity) n = nextReal(n);
            if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTSTATIC
                    || !"S".equals(((FieldInsnNode)n).desc)) continue;
            FieldInsnNode capacity = (FieldInsnNode)n;
            n = nextReal(n);
            if (!retainedCapacity) {
                if (!(n instanceof FieldInsnNode) || n.getOpcode() != GETSTATIC
                        || !sameField(capacity, (FieldInsnNode)n)) continue;
                n = nextReal(n);
            }
            if (integer(n) != 41 || nextReal(n) == null || nextReal(n).getOpcode() != IADD) continue;
            n = nextReal(nextReal(n));
            if (integer(n) != 42) continue;
            AbstractInsnNode divisor = n;
            n = nextReal(n);
            if (n == null || n.getOpcode() != IDIV) continue;
            n = nextReal(n);
            if (n == null || n.getOpcode() != I2B) continue;
            n = nextReal(n);
            if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTSTATIC
                    || !"B".equals(((FieldInsnNode)n).desc)) continue;
            if (found != null) throw new AssertionError("Ambiguous self capacity/page site in " + method.name + method.desc);
            found = new SelfSite(); found.reader = reader; found.capacity = capacity; found.pages = (FieldInsnNode)n; found.divisor = divisor;
        }
        return found;
    }

    private static int integer(AbstractInsnNode insn) {
        if (insn instanceof org.objectweb.asm.tree.IntInsnNode) return ((org.objectweb.asm.tree.IntInsnNode)insn).operand;
        if (insn instanceof LdcInsnNode && ((LdcInsnNode)insn).cst instanceof Integer)
            return ((Integer)((LdcInsnNode)insn).cst).intValue();
        if (insn == null) return Integer.MIN_VALUE;
        switch (insn.getOpcode()) {
            case ICONST_M1: return -1;
            case ICONST_0: return 0;
            case ICONST_1: return 1;
            case ICONST_2: return 2;
            case ICONST_3: return 3;
            case ICONST_4: return 4;
            case ICONST_5: return 5;
            default: return Integer.MIN_VALUE;
        }
    }

    private static void verifySourceReflection(Map<String, byte[]> sourceClasses) {
        Set<String> names = new HashSet<String>();
        for (String name : sourceClasses.keySet()) {
            names.add(name);
            names.add(name.replace('/', '.'));
        }
        int reflectionCalls = 0;
        for (Map.Entry<String, byte[]> entry : sourceClasses.entrySet()) {
            ClassNode type = node(entry.getValue());
            for (MethodNode method : type.methods) {
                AbstractInsnNode[] insns = method.instructions.toArray();
                for (int i = 0; i < insns.length; i++) if (insns[i] instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode)insns[i];
                    if (!call.owner.equals("java/lang/Class") || !(call.name.equals("getField")
                            || call.name.equals("getDeclaredField") || call.name.equals("getMethod")
                            || call.name.equals("getDeclaredMethod"))) continue;
                    reflectionCalls++;
                    for (int j = Math.max(0, i - 8); j < i; j++) if (insns[j] instanceof LdcInsnNode) {
                        Object value = ((LdcInsnNode)insns[j]).cst;
                        check(!(value instanceof String) || !names.contains(value),
                                "Reflection member-name literal collides with a game class name: "
                                        + entry.getKey() + "." + method.name + " -> " + value);
                    }
                }
            }
        }
        check(reflectionCalls > 0, "Source reflection calls were scanned for class/member literal collisions");
    }

    private static void verifySlotParity(Map<String, byte[]> sourceClasses, Map<String, byte[]> packed,
            CriticalMethods critical) throws Exception {
        for (int slot = 0; slot < 3; slot++) {
            Map<String, String> map = ChibiFinalX3Packer.slotMap(sourceClasses.keySet(), slot);
            int[] counts = new int[4];
            int id = 0;
            for (Map.Entry<String, byte[]> entry : sourceClasses.entrySet()) {
                String outputName = map.get(entry.getKey());
                byte[] expected = HsoChildX3Packer.transform(
                        ChibiFinalX3Packer.prepareLifecycle(entry.getValue(), id++), map, slot, false, counts);
                check(Arrays.equals(expected, packed.get(outputName + ".class")),
                        "Complete current-source method/field parity in slot " + slot + ": " + entry.getKey());
            }
            check(Arrays.equals(counts, new int[] {2, 1, 1, 2}), "Source transform RMS boundary counts slot " + slot);
            check(Arrays.equals(HsoChildX3Packer.makeStatic(sourceClasses, SLOTS[slot]),
                    packed.get(SLOTS[slot] + "Static.class")), "Lifecycle registry follows current source slot " + slot);
            check(packed.containsKey(SLOTS[slot] + "X3SlotSupport.class"), "Resource helper exists slot " + slot);
            verifyRmsBindings(packed, slot);
            verifyCriticalSlot(packed, map, slot, critical);
        }
        HsoChildX3Packer.checkReferences(packed, sourceClasses.keySet());
        check(true, "All candidate references stay within their namespace");
    }

    private static void verifyRmsBindings(Map<String, byte[]> packed, int slot) {
        String prefix = SLOTS[slot];
        char alias = (char)('a' + slot);
        int[] direct = new int[3];
        int reflective = 0;
        for (Map.Entry<String, byte[]> entry : packed.entrySet()) {
            if (!entry.getKey().endsWith(".class") || !entry.getKey().startsWith(prefix)
                    || entry.getKey().startsWith("lib/") || entry.getKey().startsWith("MrQuyet/")) continue;
            if (slot == 0 && (entry.getKey().startsWith("b/") || entry.getKey().startsWith("c/"))) continue;
            ClassNode type = node(entry.getValue());
            for (MethodNode method : type.methods) for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode)insn;
                    if (call.owner.equals("javax/microedition/rms/RecordStore")
                            && (call.name.equals("openRecordStore") || call.name.equals("listRecordStores")
                            || call.name.equals("deleteRecordStore")))
                        fail("Unscoped RMS boundary remains in " + entry.getKey() + "." + method.name);
                    if (call.owner.equals("lib/RecordStore")) {
                        String suffix = call.name.length() > 1 ? call.name.substring(1) : "";
                        if (call.name.charAt(0) == alias && suffix.equals("penRecordStore")) direct[0]++;
                        else if (call.name.charAt(0) == alias && suffix.equals("istRecordStores")) direct[1]++;
                        else if (call.name.charAt(0) == alias && suffix.equals("eleteRecordStore")) direct[2]++;
                    }
                } else if (insn instanceof LdcInsnNode && ((LdcInsnNode)insn).cst instanceof String) {
                    String value = (String)((LdcInsnNode)insn).cst;
                    if (value.equals(alias + "hsosky_cfg")) reflective++;
                    check(!value.equals("hsosky_cfg"), "Unprefixed reflective config name remains in " + entry.getKey());
                }
            }
        }
        check(Arrays.equals(direct, new int[] {2, 1, 1}), "Two open, one list and one delete call use RMS alias " + alias);
        check(reflective == 2, "Both reflective config names use RMS prefix " + alias);
    }

    private static void verifyCriticalSlot(Map<String, byte[]> packed, Map<String, String> map,
            int slot, CriticalMethods critical) {
        String loginOwner = map.get(critical.login.owner);
        ClassNode loginClass = node(packed.get(loginOwner + ".class"));
        MethodNode login = method(loginClass, critical.login.name, critical.login.desc);
        String authOwner = SLOTS[slot] + AUTH;
        int utf = 0, authCalls = 0;
        for (AbstractInsnNode insn : login.instructions.toArray()) if (insn instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)insn;
            if (call.owner.equals("java/io/DataOutputStream") && call.name.equals("writeUTF")) utf++;
            if (call.owner.equals(authOwner) && call.name.equals("II")) authCalls++;
        }
        check(utf == 8 && authCalls == 1, "Namespaced login calls the local auth helper in slot " + slot);

        String inventoryOwner = map.get(critical.inventory.owner);
        MethodNode inventory = method(node(packed.get(inventoryOwner + ".class")),
                critical.inventory.name, RepackSupportX3.desc(critical.inventory.desc, map));
        verifyInventoryMethod(inventory);
        String selfOwner = map.get(critical.self.owner);
        MethodNode self = method(node(packed.get(selfOwner + ".class")), critical.self.name,
                RepackSupportX3.desc(critical.self.desc, map));
        SelfSite site = findSelfSite(self);
        check(site != null, "Namespaced self-init capacity/page site exists slot " + slot);
        check("S".equals(site.capacity.desc) && "B".equals(site.pages.desc),
                "Namespaced self-init keeps capacity/page ABI slot " + slot);
    }

    private static void verifyLinkage(Map<String, byte[]> packed, File candidate) throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[] {candidate.toURI().toURL()},
                ChibiFinalX3HostTest.class.getClassLoader());
        int linked = 0;
        try {
            for (Map.Entry<String, byte[]> entry : packed.entrySet()) if (entry.getKey().endsWith(".class")) {
                ClassNode type = node(entry.getValue());
                check((type.version & 0xffff) <= 51, "CLDC class version: " + entry.getKey());
                String name = entry.getKey().substring(0, entry.getKey().length() - 6).replace('/', '.');
                Class<?> linkedType = Class.forName(name, false, loader);
                linkedType.getDeclaredFields(); linkedType.getDeclaredMethods(); linkedType.getDeclaredConstructors();
                linked++;
            }
            Attributes attributes = manifest(HsoChildX3Packer.readJar(candidate).get(MANIFEST));
            for (int slot = 0; slot < 3; slot++) {
                String name = slot == 0 ? "TemMidlet" : slot == 1 ? "b.TemMidlet" : "c.TemMidlet";
                Class<?> virtualMidlet = Class.forName(name, false, loader);
                check("lib.MIDlet".equals(virtualMidlet.getSuperclass().getName()), "Virtual MIDlet uses template ABI " + slot);
                check(attributes.getValue(String.valueOf(slot)).contains("," + name + ","), "Manifest binds virtual MIDlet " + slot);
            }
        } finally { loader.close(); }
        check(linked == ChibiFinalX3Packer.PACKED_CLASSES, "All 676 candidate classes link under JVM verification");
    }

    private static void verifyRuntime(File candidate, File sourceFile) throws Exception {
        Common emulator = new Common(null);
        MemoryRecordStoreManager manager = new MemoryRecordStoreManager();
        manager.init(emulator); emulator.setRecordStoreManager(manager); MIDletBridge.setMicroEmulator(emulator);
        seed("user_pass", new byte[] {90, 91, 92});
        URLClassLoader loader = new URLClassLoader(new URL[] {candidate.toURI().toURL()},
                ChibiFinalX3HostTest.class.getClassLoader());
        try {
            Class<?> stores = Class.forName("lib.RecordStore", true, loader);
            Class<?>[] midlets = new Class<?>[3], pet = new Class<?>[3], lifecycle = new Class<?>[3];
            Method[] loginSave = new Method[3], loginLoad = new Method[3];
            for (int slot = 0; slot < 3; slot++) {
                String ns = SLOTS[slot].replace('/', '.');
                midlets[slot] = Class.forName(ns + "TemMidlet", true, loader);
                pet[slot] = Class.forName(ns + "HSOSKYPetFeedSupport", true, loader);
                lifecycle[slot] = Class.forName(ns + "Static", true, loader);
                loginSave[slot] = findNativeSave(midlets[slot]);
                loginLoad[slot] = findNativeLoad(midlets[slot]);
                check(loginSave[slot] != null && loginLoad[slot] != null, "Native account store ABI in slot " + slot);
                check(loginLoad[slot].invoke(null, "user_pass") == null,
                        "Slot does not import the source single-client user_pass store " + slot);

                byte[] account = new byte[] {(byte)(11 + slot), (byte)(21 + slot), (byte)(31 + slot)};
                byte[] expectedAccount = (byte[])account.clone();
                loginSave[slot].invoke(null, "account", account);
                byte[] loadedAccount = (byte[])loginLoad[slot].invoke(null, "account");
                check(Arrays.equals(expectedAccount, loadedAccount), "Native account save/load isolated in slot " + slot);
                check(exists(String.valueOf((char)('a' + slot)) + "account"),
                        "Native account RMS physical name has the slot prefix " + slot);

                Field selected = pet[slot].getField("selectedAutoLoginChar");
                Method saveConfig = pet[slot].getMethod("saveRMS"), loadConfig = pet[slot].getMethod("loadRMS");
                selected.setInt(null, 4 + slot); saveConfig.invoke(null);
                check(exists(String.valueOf((char)('a' + slot)) + "hsosky_cfg"),
                        "Reflective config physical RMS name has prefix slot " + slot);
                selected.setInt(null, -1); loadConfig.invoke(null);
                check(selected.getInt(null) == 4 + slot, "Reflective config reload is isolated in slot " + slot);

                Method open = stores.getMethod((char)('a' + slot) + "penRecordStore", String.class, Boolean.TYPE);
                RecordStore probe = (RecordStore)open.invoke(null, "slot_probe", Boolean.TRUE);
                try { probe.addRecord(new byte[] {(byte)(61 + slot)}, 0, 1); }
                finally { probe.closeRecordStore(); }
                String[] names = (String[])stores.getMethod((char)('a' + slot) + "istRecordStores").invoke(null);
                check(contains(names, "slot_probe") && count(names, "slot_probe") == 1
                        && contains(names, "account") && contains(names, "hsosky_cfg"),
                        "Namespaced listing includes only logical records owned by slot " + slot);
            }
            verifyAuthRuntime(sourceFile, loader);
            verifyDeleteIsolation(stores);
            verifySingleRecordUnchanged();
            verifyRestart(lifecycle, pet, midlets, loginLoad);
            verifySingleRecordUnchanged();
        } finally { loader.close(); }
    }

    private static void verifyAuthRuntime(File sourceFile, ClassLoader candidateLoader) throws Exception {
        URL sourceUrl = sourceFile.toURI().toURL();
        URLClassLoader sourceLoader = new URLClassLoader(new URL[] {sourceUrl},
                ChibiFinalX3HostTest.class.getClassLoader());
        try {
            Class<?> original = Class.forName(AUTH.replace('/', '.'), true, sourceLoader);
            String[] users = {"fixture-user", "Unicode-\u7528\u6237-\u96ea", "alternate-user"};
            byte[][] keys = {new byte[] {1, 2, 3, 4}, null, new byte[] {(byte)0xff, 0, 7}};
            for (int slot = 0; slot < 3; slot++) {
                Class<?> replica = Class.forName((SLOTS[slot] + AUTH).replace('/', '.'), true, candidateLoader);
                Method sourceHash = original.getMethod("II", String.class, byte[].class);
                Method slotHash = replica.getMethod("II", String.class, byte[].class);
                for (int i = 0; i < users.length; i++)
                    check(sourceHash.invoke(null, users[i], keys[i]).equals(slotHash.invoke(null, users[i], keys[i])),
                            "Authentication output matches current source in slot " + slot + ", case " + i);
            }
        } finally { sourceLoader.close(); }
    }

    private static void verifyDeleteIsolation(Class<?> stores) throws Exception {
        for (int slot = 0; slot < 3; slot++) {
            Method open = stores.getMethod((char)('a' + slot) + "penRecordStore", String.class, Boolean.TYPE);
            RecordStore store = (RecordStore)open.invoke(null, "delete_probe", Boolean.TRUE);
            try { store.addRecord(new byte[] {(byte)(101 + slot)}, 0, 1); }
            finally { store.closeRecordStore(); }
        }
        stores.getMethod("aeleteRecordStore", String.class).invoke(null, "delete_probe");
        check(!exists("adelete_probe") && exists("bdelete_probe") && exists("cdelete_probe"),
                "Delete alias removes only slot a record");
        check(Arrays.equals(new byte[] {90, 91, 92}, readRecord("user_pass")), "Delete cannot touch single-client store");
    }

    private static void verifyRestart(Class<?>[] lifecycle, Class<?>[] pet, Class<?>[] midlets,
            Method[] loginLoad) throws Exception {
        for (int slot = 0; slot < 3; slot++) {
            Object state = lifecycle[slot].getDeclaredConstructor().newInstance();
            state.getClass().getMethod("clear").invoke(state);
            state.getClass().getMethod("cinitclone").invoke(state);
        }
        for (int slot = 0; slot < 3; slot++) {
            Field selected = pet[slot].getField("selectedAutoLoginChar");
            check(selected.getInt(null) == -1, "Static restart restores config default in slot " + slot);
            pet[slot].getMethod("loadRMS").invoke(null);
            check(selected.getInt(null) == 4 + slot, "Static restart retains reflective config in slot " + slot);
            byte[] account = (byte[])loginLoad[slot].invoke(null, "account");
            check(Arrays.equals(account, new byte[] {(byte)(11 + slot), (byte)(21 + slot), (byte)(31 + slot)}),
                    "Static restart retains native account in slot " + slot);
        }
    }

    private static Method findNativeSave(Class<?> midlet) {
        for (Method method : midlet.getDeclaredMethods())
            if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == Void.TYPE
                    && Arrays.equals(method.getParameterTypes(), new Class<?>[] {String.class, byte[].class})) return method;
        return null;
    }

    private static Method findNativeLoad(Class<?> midlet) {
        for (Method method : midlet.getDeclaredMethods())
            if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == byte[].class
                    && Arrays.equals(method.getParameterTypes(), new Class<?>[] {String.class})) return method;
        return null;
    }

    private static void seed(String name, byte[] value) throws Exception {
        RecordStore store = RecordStore.openRecordStore(name, true);
        try { store.addRecord(value, 0, value.length); } finally { store.closeRecordStore(); }
    }

    private static byte[] readRecord(String name) throws Exception {
        RecordStore store = RecordStore.openRecordStore(name, false);
        try { return store.getRecord(1); } finally { store.closeRecordStore(); }
    }

    private static boolean exists(String name) throws Exception {
        RecordStore store = null;
        try { store = RecordStore.openRecordStore(name, false); return true; }
        catch (RecordStoreNotFoundException absent) { return false; }
        finally { if (store != null) store.closeRecordStore(); }
    }

    private static void verifySingleRecordUnchanged() throws Exception {
        check(Arrays.equals(new byte[] {90, 91, 92}, readRecord("user_pass")), "Single-client RMS sentinel remains unchanged");
    }

    private static int[] dataReads(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray())
            if (insn instanceof MethodInsnNode && ((MethodInsnNode)insn).owner.equals("java/io/DataInputStream")) count++;
        int[] result = new int[count];
        int at = 0;
        for (AbstractInsnNode insn : method.instructions.toArray())
            if (insn instanceof MethodInsnNode && ((MethodInsnNode)insn).owner.equals("java/io/DataInputStream")) result[at++] = insn.getOpcode();
        return result;
    }

    private static String[] dataReadNames(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray())
            if (insn instanceof MethodInsnNode && ((MethodInsnNode)insn).owner.equals("java/io/DataInputStream")) count++;
        String[] result = new String[count]; int at = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)insn;
            if (call.owner.equals("java/io/DataInputStream")) result[at++] = call.name;
        }
        return result;
    }

    private static MethodInsnNode readBeforeString(MethodNode method, String marker) {
        AbstractInsnNode constant = null;
        for (AbstractInsnNode insn : method.instructions.toArray())
            if (insn instanceof LdcInsnNode && marker.equals(((LdcInsnNode)insn).cst)) {
                check(constant == null, "Unique inventory marker: " + marker); constant = insn;
            }
        check(constant != null, "Inventory marker exists: " + marker);
        for (AbstractInsnNode insn = constant.getPrevious(); insn != null; insn = insn.getPrevious())
            if (insn instanceof MethodInsnNode && ((MethodInsnNode)insn).owner.equals("java/io/DataInputStream"))
                return (MethodInsnNode)insn;
        throw new AssertionError("No count reader before " + marker);
    }

    private static MethodNode method(ClassNode type, String name, String desc) {
        for (MethodNode method : type.methods) if (name.equals(method.name) && desc.equals(method.desc)) return method;
        throw new AssertionError("Missing method " + type.name + "." + name + desc);
    }

    private static boolean hasMethod(byte[] bytes, String name, String desc) {
        if (bytes == null) return false;
        ClassNode type = node(bytes);
        for (MethodNode method : type.methods) if (name.equals(method.name) && desc.equals(method.desc)) return true;
        return false;
    }

    private static boolean hasString(MethodNode method, String value) {
        for (AbstractInsnNode insn : method.instructions.toArray())
            if (insn instanceof LdcInsnNode && value.equals(((LdcInsnNode)insn).cst)) return true;
        return false;
    }

    private static boolean sameField(FieldInsnNode a, FieldInsnNode b) {
        return a.owner.equals(b.owner) && a.name.equals(b.name) && a.desc.equals(b.desc);
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        if (insn == null) return null;
        AbstractInsnNode next = insn.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }

    private static boolean contains(String[] values, String expected) {
        if (values != null) for (String value : values) if (expected.equals(value)) return true;
        return false;
    }

    private static int count(String[] values, String expected) {
        int result = 0;
        if (values != null) for (String value : values) if (expected.equals(value)) result++;
        return result;
    }

    private static Map<String, byte[]> classes(Map<String, byte[]> entries) {
        Map<String, byte[]> result = new LinkedHashMap<String, byte[]>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) if (entry.getKey().endsWith(".class"))
            result.put(entry.getKey().substring(0, entry.getKey().length() - 6), entry.getValue());
        return result;
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode result = new ClassNode();
        new ClassReader(bytes).accept(result, 0);
        return result;
    }

    private static int classCount(Map<String, byte[]> entries) {
        int count = 0; for (String name : entries.keySet()) if (name.endsWith(".class")) count++;
        return count;
    }

    private static Attributes manifest(byte[] bytes) throws Exception {
        if (bytes == null) throw new AssertionError("Manifest missing");
        return new Manifest(new ByteArrayInputStream(bytes)).getMainAttributes();
    }

    private static void check(boolean ok, String message) {
        checks++; if (!ok) throw new AssertionError(message);
    }

    private static void fail(String message) { throw new AssertionError(message); }

    private static final class MethodRef {
        final String owner, name, desc;
        MethodRef(String owner, String name, String desc) { this.owner = owner; this.name = name; this.desc = desc; }
    }

    private static final class CriticalMethods {
        final MethodRef login, inventory, self;
        CriticalMethods(MethodRef login, MethodRef inventory, MethodRef self) {
            this.login = login; this.inventory = inventory; this.self = self;
        }
    }

    private static final class SelfSite {
        MethodInsnNode reader;
        FieldInsnNode capacity, pages;
        AbstractInsnNode divisor;
    }
}
