import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.util.MemoryRecordStoreManager;

/** Exercises the candidate release's native pet and auto-sell filter hooks. */
public final class HungEquipmentFiltersBinaryTest implements Opcodes {
    static ClassLoader loader;
    static sun.misc.Unsafe unsafe;
    static int checks;
    static final List<String> notices = new ArrayList<String>();
    static final List<Short> saleIds = new ArrayList<Short>();
    static final String BRIDGE = "luna.generated.HungEquipmentFilterBridge";
    static final String SETTINGS = "luna.features.EquipmentFilterSettings";
    static final int[] GROUP_BITS = {1, 2, 4, 8};
    static final int[] GROUP_ACTIONS_PET = {-119, -120, -121, -122};
    static final int[] GROUP_ACTIONS_SALE = {-125, -126, -127, -128};

    static String n(String suffix) {
        StringBuilder s = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) s.append('i');
        return s.append(suffix).toString();
    }

    static Class<?> type(String shortName) throws Exception {
        return Class.forName(shortName.startsWith("~") ? n(shortName.substring(1)) : shortName, false, loader);
    }

    static Field field(Class<?> c, String shortName) throws Exception {
        String name = shortName.startsWith("~") ? n(shortName.substring(1)) : shortName;
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(shortName);
    }

    static void set(Object target, String owner, String name, Object value) throws Exception {
        field(type(owner), name).set(target, value);
    }

    static Object get(Object target, String owner, String name) throws Exception {
        return field(type(owner), name).get(target);
    }

    static Object alloc(String shortName) throws Exception {
        return unsafe.allocateInstance(type(shortName));
    }

    public static void captureNotice(String message) {
        notices.add(message);
    }

    public static void captureSale(byte itemType, short itemId, byte action) {
        if (itemType == 3 && action == 1) saleIds.add(Short.valueOf(itemId));
    }

    /** Keeps the production filter/action bytecode but sinks native UI presentation. */
    static final class UiSinkLoader extends URLClassLoader {
        UiSinkLoader(File jar) throws Exception { super(new URL[]{jar.toURI().toURL()}, HungEquipmentFiltersBinaryTest.class.getClassLoader()); }

        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String bridgeName = BRIDGE.replace('.', '/');
            String saleName = n("lliiilll");
            String modName = n("lillllll");
            String serviceName = n("lilillll");
            if (!name.equals(bridgeName.replace('/', '.')) && !name.equals(saleName.replace('/', '.'))
                    && !name.equals(modName.replace('/', '.')) && !name.equals(serviceName.replace('/', '.'))) {
                return super.findClass(name);
            }
            String resource = name.replace('.', '/') + ".class";
            InputStream in = getResourceAsStream(resource);
            if (in == null) throw new ClassNotFoundException(name + " bytes missing");
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] block = new byte[8192];
                for (int count; (count = in.read(block)) != -1;) bytes.write(block, 0, count);
                ClassNode c = new ClassNode(ASM9);
                new ClassReader(bytes.toByteArray()).accept(c, 0);
                int patched = 0;
                for (MethodNode m : c.methods) {
                    if (name.equals(bridgeName.replace('/', '.')) && m.name.equals("showGroups") && m.desc.equals("(Z)V")) {
                        m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
                        m.instructions.add(new InsnNode(RETURN)); patched++;
                    } else if (name.equals(bridgeName.replace('/', '.'))) {
                        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; ) {
                            AbstractInsnNode next = insn.getNext();
                            if (insn instanceof FieldInsnNode) {
                                FieldInsnNode field = (FieldInsnNode)insn;
                                if (field.getOpcode() == GETSTATIC && field.owner.equals(n("liilllii"))
                                        && field.name.equals(n("liiiliiiiil"))) {
                                    // Keep the real row construction and action IDs, with no live game listener.
                                    m.instructions.set(insn, new InsnNode(ACONST_NULL));
                                }
                            }
                            insn = next;
                        }
                    } else if (name.equals(saleName.replace('/', '.')) && m.name.equals(n("lilliiil")) && m.desc.equals("()V")) {
                        m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
                        m.instructions.add(new InsnNode(RETURN)); patched++;
                    } else if (name.equals(modName.replace('/', '.')) && m.name.equals(n("lilli")) && m.desc.equals("(Ljava/lang/String;)V")) {
                        m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
                        m.instructions.add(new VarInsnNode(ALOAD, 0));
                        m.instructions.add(new MethodInsnNode(INVOKESTATIC, "HungEquipmentFiltersBinaryTest", "captureNotice", "(Ljava/lang/String;)V", false));
                        m.instructions.add(new InsnNode(RETURN)); patched++;
                    } else if (name.equals(serviceName.replace('/', '.')) && m.name.equals(n("")) && m.desc.equals("(BSB)V")) {
                        m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
                        m.instructions.add(new VarInsnNode(ILOAD, 1));
                        m.instructions.add(new VarInsnNode(ILOAD, 2));
                        m.instructions.add(new VarInsnNode(ILOAD, 3));
                        m.instructions.add(new MethodInsnNode(INVOKESTATIC, "HungEquipmentFiltersBinaryTest", "captureSale", "(BSB)V", false));
                        m.instructions.add(new InsnNode(RETURN)); patched++;
                    }
                }
                if (patched != 1) throw new AssertionError("Expected one UI sink in " + name + ", got " + patched);
                ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                c.accept(out);
                byte[] transformed = out.toByteArray();
                return defineClass(name, transformed, 0, transformed.length);
            } catch (Exception ex) {
                throw new ClassNotFoundException(name, ex);
            } finally {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
    }

    static Method method(Class<?> owner, String name, int parameterCount) throws Exception {
        for (Method m : owner.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == parameterCount) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name + "/" + parameterCount);
    }

    static Object invoke(String name, Object... args) throws Exception {
        return method(type(BRIDGE), name, args.length).invoke(null, args);
    }

    static boolean bool(String name, Object... args) throws Exception {
        return ((Boolean)invoke(name, args)).booleanValue();
    }

    static Object setting(String name, Object... args) throws Exception {
        return method(type(SETTINGS), name, args.length).invoke(null, args);
    }

    static int mask(boolean sale) throws Exception {
        return ((Integer)setting("mask", Boolean.valueOf(sale))).intValue();
    }

    static void setGroup(boolean sale, int bit, boolean enabled) throws Exception {
        if (((mask(sale) & bit) != 0) != enabled) {
            setting("toggleGroup", Boolean.valueOf(sale), Integer.valueOf(bit));
        }
        if (((mask(sale) & bit) != 0) != enabled) {
            throw new AssertionError("group mask did not change for sale=" + sale + " bit=" + bit);
        }
    }

    static void allowOnlyGroup(boolean sale, int bit) throws Exception {
        for (int i = 0; i < GROUP_BITS.length; i++) setGroup(sale, GROUP_BITS[i], GROUP_BITS[i] == bit);
    }

    static Object vector(Object... values) throws Exception {
        Object v = type("et").getConstructor().newInstance();
        Method add = type("et").getMethod(n("l"), Object.class);
        for (Object value : values) add.invoke(v, value);
        return v;
    }

    static Object fullInventory(Object... ineligibleItems) throws Exception {
        Object[] items = new Object[27];
        for (int i = 0; i < items.length; i++) items[i] = ineligibleItems[i % ineligibleItems.length];
        return vector(items);
    }

    static int vectorSize(Object v) throws Exception {
        return ((Integer)type("et").getMethod("c").invoke(v)).intValue();
    }

    static Object vectorAt(Object v, int index) throws Exception {
        return type("et").getMethod("a", Integer.TYPE).invoke(v, Integer.valueOf(index));
    }

    static boolean rowHasId(Object row, int id) throws Exception {
        if (id < Byte.MIN_VALUE || id > Byte.MAX_VALUE) return false;
        return field(row.getClass(), "e").getByte(row) == (byte)id;
    }

    static boolean vectorHasId(Object rows, int id) throws Exception {
        for (int i = 0; i < vectorSize(rows); i++) if (rowHasId(vectorAt(rows, i), id)) return true;
        return false;
    }

    static Object item(int id, int quality, boolean locked, int enhancement, int slot, int job) throws Exception {
        Object item = alloc("~lililiii");
        set(item, "~llilli", "O", Integer.valueOf(id));
        set(item, "~llilli", "~liiill", Integer.valueOf(3));
        set(item, "~llilli", "~llliiili", Integer.valueOf(slot));
        set(item, "~llilli", "M", Integer.valueOf(job));
        set(item, "~llilli", "N", Integer.valueOf(quality));
        set(item, "~llilli", "~liiiliiii", Byte.valueOf((byte)(locked ? 1 : 0)));
        set(item, "~llilli", "~liiiiliii", "binary-filter-item-" + id);
        setEnhancement(item, enhancement);
        return item;
    }

    static void setEnhancement(Object item, int level) throws Exception {
        set(item, "~llilli", "~llilil", Byte.valueOf((byte)level));
    }

    static void check(String name, boolean value) {
        checks++;
        if (!value) throw new AssertionError(name);
        System.out.println("PASS " + name);
    }

    static void eq(String name, Object actual, Object expected) {
        checks++;
        if (!String.valueOf(expected).equals(String.valueOf(actual))) {
            throw new AssertionError(name + ": " + actual + " != " + expected);
        }
        System.out.println("PASS " + name);
    }

    static ClassNode classNode(JarFile jar, String shortName) throws Exception {
        String suffix = (shortName.startsWith("~") ? shortName.substring(1) : shortName) + ".class";
        JarEntry match = null;
        for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
            JarEntry entry = e.nextElement();
            if (entry.getName().endsWith(suffix)) { match = entry; break; }
        }
        if (match == null) throw new FileNotFoundException(suffix);
        ClassNode c = new ClassNode(ASM9);
        InputStream in = jar.getInputStream(match);
        try { new ClassReader(in).accept(c, 0); } finally { in.close(); }
        return c;
    }

    static boolean calls(ClassNode c, String methodName, String desc, String targetName) {
        for (MethodNode m : c.methods) {
            if (!m.name.equals(methodName) || !m.desc.equals(desc)) continue;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode)n;
                    if (call.owner.equals(BRIDGE.replace('.', '/')) && call.name.equals(targetName)) return true;
                }
            }
        }
        return false;
    }

    static void verifyHooks(File jarFile) throws Exception {
        JarFile jar = new JarFile(jarFile);
        try {
            ClassNode pet = classNode(jar, "~lliiilli");
            ClassNode sale = classNode(jar, "~lliiilll");
            check("pet update calls candidate bridge", calls(pet, "update", "()V", "acceptsPet"));
            check("sell update calls candidate bridge", calls(sale, "update", "()V", "acceptsSale"));
            check("sell preflight runs before native busy flag", callBeforeField(sale, "update", "preflightSale", n("lillllll"), n("liliililiil")));
            check("pet action handler is wired", calls(classNode(jar, "~lliiilli"), "onAction", "(II)Z", "handlePetAction"));
            check("sell action handler is wired", calls(classNode(jar, "~lliiilll"), "onAction", "(II)Z", "handleSaleAction"));
            check("pet settings row hook is wired", calls(classNode(jar, "~lliiilli"), n("lilliiil"), "()V", "addPetRows"));
            check("sell settings row hook is wired", calls(classNode(jar, "~lliiilll"), n("lilliiil"), "()V", "addSaleRows"));
            check("sell full-scan hook is wired", calls(sale, "update", "()V", "afterSaleScan"));
            check("sell cycle completion hook is wired", calls(sale, "update", "()V", "completeSaleCycle"));
            check("deferred sell disable follows native idle-state reset", callAfterField(sale, "update", "completeSaleCycle", n("lliiilll"), n("lilliiiilil")));
            ClassNode bridge = classNode(jar, BRIDGE.replace('.', '/'));
            check("candidate bridge contains full sale predicate", hasMethod(bridge, "saleEligible", "(L" + typeName("~llilli") + ";)Z"));
            check("candidate bridge contains full-candidate scan", hasMethod(bridge, "hasSaleCandidates", "()Z"));
            check("candidate bridge contains empty-sale preflight", hasMethod(bridge, "preflightSale", "()Z"));
        } finally { jar.close(); }
    }

    static boolean hasMethod(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return true;
        return false;
    }

    static String typeName(String shortName) {
        return shortName.startsWith("~") ? n(shortName.substring(1)) : shortName;
    }

    static boolean callBeforeField(ClassNode c, String methodName, String targetCall, String fieldOwner, String fieldName) {
        for (MethodNode m : c.methods) {
            if (!m.name.equals(methodName) || !m.desc.equals("()V")) continue;
            int callAt = Integer.MAX_VALUE, fieldAt = Integer.MAX_VALUE, index = 0;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext(), index++) {
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode)n;
                    if (call.owner.equals(BRIDGE.replace('.', '/')) && call.name.equals(targetCall)) callAt = index;
                }
                if (n instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode)n;
                    if (field.getOpcode() == PUTSTATIC && field.owner.equals(fieldOwner) && field.name.equals(fieldName)) fieldAt = index;
                }
            }
            return callAt != Integer.MAX_VALUE && fieldAt != Integer.MAX_VALUE && callAt < fieldAt;
        }
        return false;
    }

    static boolean callAfterField(ClassNode c, String methodName, String targetCall, String fieldOwner, String fieldName) {
        for (MethodNode m : c.methods) {
            if (!m.name.equals(methodName) || !m.desc.equals("()V")) continue;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!(n instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode)n;
                if (!call.owner.equals(BRIDGE.replace('.', '/')) || !call.name.equals(targetCall)) continue;
                AbstractInsnNode previous = n.getPrevious();
                while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
                if (previous instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode)previous;
                    if (field.getOpcode() == PUTSTATIC && field.owner.equals(fieldOwner) && field.name.equals(fieldName)) return true;
                }
            }
        }
        return false;
    }

    static Map<String, Boolean> farmFlags() throws Exception {
        Class<?> farm = type("~lliiiiil");
        Map<String, Boolean> values = new TreeMap<String, Boolean>();
        for (Field f : farm.getDeclaredFields()) {
            if (f.getType() != Boolean.TYPE || !Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            values.put(f.getName(), Boolean.valueOf(f.getBoolean(null)));
        }
        return values;
    }

    static void checkFarmFlags(Map<String, Boolean> before, String name) throws Exception {
        eq(name, farmFlags(), before);
    }

    static void nativeUpdate() throws Exception {
        Object sale = alloc("~lliiilll");
        method(type("~lliiilll"), "update", 0).invoke(sale);
    }

    static void testSettingsReload() throws Exception {
        int expectedPet = mask(false);
        int expectedSale = mask(true);
        boolean expectedLocked = ((Boolean)setting("lockedOnly")).booleanValue();
        Class<?> settings = type(SETTINGS);
        field(settings, "loaded").setBoolean(null, false);
        field(settings, "petMask").setInt(null, 15);
        field(settings, "saleMask").setInt(null, 15);
        field(settings, "lockedOnly").setBoolean(null, false);
        eq("saved pet group mask reloads from RMS", Integer.valueOf(mask(false)), Integer.valueOf(expectedPet));
        eq("saved sale group mask reloads from RMS", Integer.valueOf(mask(true)), Integer.valueOf(expectedSale));
        eq("saved locked-only choice reloads from RMS", setting("lockedOnly"), Boolean.valueOf(expectedLocked));
    }

    static void prepare(File jar) throws Exception {
        Common emulator = new Common(null);
        MemoryRecordStoreManager rms = new MemoryRecordStoreManager();
        rms.init(emulator);
        emulator.setRecordStoreManager(rms);
        MIDletBridge.setMicroEmulator(emulator);
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        unsafe = (sun.misc.Unsafe)uf.get(null);
        loader = new UiSinkLoader(jar);
        Class.forName(BRIDGE, true, loader);
        Class.forName(SETTINGS, true, loader);
        type("~lliiilli").getDeclaredMethods();
        type("~lliiilll").getDeclaredMethods();
        type("~llilli").getDeclaredFields();
    }

    static void testRowsAndActions() throws Exception {
        Object petRows = vector();
        invoke("addPetRows", petRows);
        eq("pet settings row appended", vectorSize(petRows), Integer.valueOf(1));
        check("pet settings entry has byte action -118", vectorHasId(petRows, -118));

        Object saleRows = vector();
        invoke("addSaleRows", saleRows);
        eq("sale settings rows appended", vectorSize(saleRows), Integer.valueOf(2));
        check("sale locked row has byte action -123", vectorHasId(saleRows, -123));
        check("sale groups row has byte action -124", vectorHasId(saleRows, -124));

        check("pet submenu action handled", bool("handlePetAction", Integer.valueOf(-118)));
        check("sale submenu action handled", bool("handleSaleAction", Integer.valueOf(-124)));
        for (int i = 0; i < GROUP_ACTIONS_PET.length; i++) {
            int before = mask(false);
            check("pet group action handled " + GROUP_ACTIONS_PET[i], bool("handlePetAction", Integer.valueOf(GROUP_ACTIONS_PET[i])));
            check("pet group action toggles mask " + i, mask(false) != before);
        }
        for (int i = 0; i < GROUP_ACTIONS_SALE.length; i++) {
            int before = mask(true);
            check("sale group action handled " + GROUP_ACTIONS_SALE[i], bool("handleSaleAction", Integer.valueOf(GROUP_ACTIONS_SALE[i])));
            check("sale group action toggles mask " + i, mask(true) != before);
        }

        boolean lockedBefore = ((Boolean)setting("lockedOnly")).booleanValue();
        check("sale locked filter action handled", bool("handleSaleAction", Integer.valueOf(-123)));
        eq("sale locked filter toggles", setting("lockedOnly"), Boolean.valueOf(!lockedBefore));
        check("pet parent back action remains native", !bool("handlePetAction", Integer.valueOf(-70)));
        check("sale parent back action remains native", !bool("handleSaleAction", Integer.valueOf(-74)));
    }

    static void testFiltersAndPreflight() throws Exception {
        // These slot/job pairs are the native item data classified by EquipmentFilterPolicy.
        final int groupBit = 1;
        final int slotForGroup = 8;
        final int jobForGroup = 0;
        allowOnlyGroup(false, groupBit);
        allowOnlyGroup(true, groupBit);
        if (((Boolean)setting("lockedOnly")).booleanValue()) setting("toggleLockedOnly");

        Object petLowGrade = item(101, 0, false, 0, slotForGroup, jobForGroup);
        check("pet stat group accepts matching item regardless of native grade", bool("acceptsPet", petLowGrade));
        allowOnlyGroup(false, 2);
        check("pet stat group rejects nonmatching item", !bool("acceptsPet", petLowGrade));
        int[] sampleTypes = {8, 4, 0, 10};
        for (int i = 0; i < sampleTypes.length; i++) {
            allowOnlyGroup(false, GROUP_BITS[i]);
            Object sample = item(110 + i, 0, false, 0, sampleTypes[i], -1);
            check("pet stat group maps equipment type " + sampleTypes[i], bool("acceptsPet", sample));
        }
        allowOnlyGroup(false, groupBit);

        Object saleGood = item(201, 1, false, 0, slotForGroup, jobForGroup);
        check("sale candidate passes native quality, lock and stat-group filters", bool("acceptsSale", saleGood));
        allowOnlyGroup(true, 2);
        check("sale group is conjunctive with native filters", !bool("acceptsSale", saleGood));
        check("full sale eligibility is conjunctive with stat groups", !bool("saleEligible", saleGood));
        allowOnlyGroup(true, groupBit);

        set(null, "~lliiilll", "~lilliiiilii", Integer.valueOf(2));
        Object atThreshold = item(202, 2, false, 0, slotForGroup, jobForGroup);
        check("sale rejects item at native quality threshold", !bool("saleEligible", atThreshold));
        Object negativeQuality = item(203, -1, false, 0, slotForGroup, jobForGroup);
        check("sale rejects negative native quality", !bool("saleEligible", negativeQuality));
        Object enhanced = item(204, 1, false, 1, slotForGroup, jobForGroup);
        check("sale rejects enhanced item", !bool("saleEligible", enhanced));

        Object unlocked = item(205, 1, false, 0, slotForGroup, jobForGroup);
        Object locked = item(206, 1, true, 0, slotForGroup, jobForGroup);
        setting("toggleLockedOnly");
        check("locked-only filter rejects unlocked item", !bool("acceptsSale", unlocked));
        check("sale eligibility applies locked-only filter", !bool("saleEligible", unlocked));
        check("locked-only filter accepts locked matching item", bool("acceptsSale", locked));
        check("sale eligibility accepts locked matching candidate", bool("saleEligible", locked));
        setting("toggleLockedOnly");

        setting("toggleLockedOnly");
        Object wrongGroup = item(207, 1, false, 0, 4, 0);
        setting("toggleLockedOnly");
        check("sale group rejects a nonmatching equipment type", !bool("acceptsSale", wrongGroup));
        check("sale predicate rejects a nonmatching equipment type", !bool("saleEligible", wrongGroup));
        setting("toggleLockedOnly");
        Object wrongGroupLocked = item(208, 1, true, 0, 4, 0);
        check("locked item still fails a nonmatching stat group", !bool("saleEligible", wrongGroupLocked));
        Object noCandidates = fullInventory(unlocked, atThreshold, negativeQuality, enhanced, wrongGroupLocked);
        set(null, "~llilli", "~liiillili", noCandidates);
        eq("native full inventory reaches capacity minus three", Integer.valueOf(vectorSize(noCandidates)), Integer.valueOf(27));
        check("no valid sale candidate", !bool("hasSaleCandidates"));
        set(null, "~lliiilll", "~lilliiiiill", Boolean.TRUE);
        set(null, "~lillllll", "~liliililiil", Boolean.FALSE);
        notices.clear();
        set(null, "~lliiil", "~lliliili", Short.valueOf((short)30));
        set(null, "~lliiilll", "~lilliiiilil", Integer.valueOf(0));
        set(null, "~lliiilll", "~lilliiiilli", Integer.valueOf(0));
        set(null, "~lliiiiil", "~lililiilill", Boolean.TRUE);
        set(null, "~lliiiiil", "~lililiillii", Boolean.TRUE);
        set(null, "~lliiiiil", "~lililiillil", Boolean.FALSE);
        set(null, "~lliilili", "~lillilliiii", Boolean.FALSE);
        set(null, "~lliilili", "~lillillilil", Boolean.FALSE);
        Map<String, Boolean> beforeFarm = farmFlags();
        check("native Farm master and run-attack mode are active", get(null, "~lliiiiil", "~lililiilill").equals(Boolean.TRUE)
                && get(null, "~lliiiiil", "~lililiillii").equals(Boolean.TRUE));
        check("native Auto Sell is active before full-bag update", get(null, "~lliiilll", "~lilliiiiill").equals(Boolean.TRUE));
        nativeUpdate();
        check("native full-bag update reached empty-sale preflight", notices.size() == 1);
        eq("empty sale preflight disables Auto Sell", get(null, "~lliiilll", "~lilliiiiill"), Boolean.FALSE);
        eq("empty sale preflight leaves global busy clear", get(null, "~lillllll", "~liliililiil"), Boolean.FALSE);
        eq("empty sale preflight leaves sell state idle", get(null, "~lliiilll", "~lilliiiilil"), Integer.valueOf(0));
        check("empty sale preflight reports the filter result", notices.size() == 1 && notices.get(0).contains("Auto bán rác"));
        checkFarmFlags(beforeFarm, "empty sale preflight preserves Farm flags");

        setting("toggleLockedOnly");
        Object candidates = vector(saleGood);
        set(null, "~llilli", "~liiillili", candidates);
        set(null, "~lliiilll", "~lilliiiiill", Boolean.TRUE);
        check("candidate scan finds a valid item", bool("hasSaleCandidates"));
        int saleMaskBefore = mask(true);
        boolean lockedOnlyBefore = ((Boolean)setting("lockedOnly")).booleanValue();
        beforeFarm = farmFlags();
        check("valid sale preflight proceeds", bool("preflightSale"));
        eq("valid preflight leaves Auto Sell enabled", get(null, "~lliiilll", "~lilliiiiill"), Boolean.TRUE);
        eq("valid preflight leaves global busy clear", get(null, "~lillllll", "~liliililiil"), Boolean.FALSE);
        eq("valid preflight leaves sale groups unchanged", Integer.valueOf(mask(true)), Integer.valueOf(saleMaskBefore));
        eq("valid preflight leaves locked-only choice unchanged", setting("lockedOnly"), Boolean.valueOf(lockedOnlyBefore));
        checkFarmFlags(beforeFarm, "valid sale preflight preserves Farm flags");

        set(null, "~llilli", "~liiillili", noCandidates);
        set(null, "~lliiilll", "~lilliiiiill", Boolean.TRUE);
        set(null, BRIDGE, "stopAfterCycle", Boolean.FALSE);
        notices.clear();
        invoke("afterSaleScan");
        eq("full invalid scan defers Auto Sell cleanup", get(null, "~lliiilll", "~lilliiiiill"), Boolean.TRUE);
        eq("full invalid scan marks cycle stop", get(null, BRIDGE, "stopAfterCycle"), Boolean.TRUE);
        eq("full invalid scan does not announce early cleanup", Integer.valueOf(notices.size()), Integer.valueOf(0));
        invoke("completeSaleCycle");
        eq("cycle completion disables Auto Sell after native restore", get(null, "~lliiilll", "~lilliiiiill"), Boolean.FALSE);
        eq("cycle completion clears deferred stop", get(null, BRIDGE, "stopAfterCycle"), Boolean.FALSE);
        eq("cycle completion reports once", Integer.valueOf(notices.size()), Integer.valueOf(1));

        set(null, "~llilli", "~liiillili", vector(atThreshold));
        set(null, "~lliiilll", "~lilliiiiill", Boolean.TRUE);
        notices.clear();
        invoke("afterSaleScan");
        invoke("completeSaleCycle");
        eq("below-capacity invalid scan keeps Auto Sell enabled", get(null, "~lliiilll", "~lilliiiiill"), Boolean.TRUE);
        eq("below-capacity scan emits no stop notice", Integer.valueOf(notices.size()), Integer.valueOf(0));
    }

    static void prepareSaleWorld() throws Exception {
        Object world = alloc("cs");
        set(world, "cs", "d", Integer.valueOf(1));
        set(null, "~liilllii", "~liiililiiii", world);
    }

    static void runNativeState3Sale(Object... items) throws Exception {
        set(null, "~llilli", "~liiillili", vector(items));
        set(null, "~lliiilll", "~lilliiiilii", Integer.valueOf(4));
        set(null, "~lliiilll", "~lilliiiiill", Boolean.TRUE);
        set(null, "~lliiilll", "~lilliiiilil", Integer.valueOf(3));
        set(null, "~lliiilll", "~lilliiiilli", Integer.valueOf(0));
        set(null, "~lillllll", "~liliililiil", Boolean.FALSE);
        nativeUpdate();
    }

    static void testNativeEquipmentTypeSales() throws Exception {
        // Indexed by native equipment type: 0=no group, 1=strength, 2=dexterity, 4=vitality, 8=spirit.
        int[] expectedGroup = {4, 4, 4, 4, 2, 2, 4, 0, 1, 1, 8, 8};
        int[] masks = {1, 2, 4, 8};
        if (((Boolean)setting("lockedOnly")).booleanValue()) setting("toggleLockedOnly");
        prepareSaleWorld();
        set(null, "~lliiiiil", "~lililiilill", Boolean.TRUE);
        set(null, "~lliiiiil", "~lililiillii", Boolean.TRUE);
        set(null, "~lliiiiil", "~lililiillil", Boolean.FALSE);
        set(null, "~lliilili", "~lillilliiii", Boolean.FALSE);
        set(null, "~lliilili", "~lillillilil", Boolean.FALSE);

        Object necklace = item(501, 1, false, 0, 5, -1);
        Object boots = item(502, 1, false, 0, 6, -1);
        allowOnlyGroup(false, 4);
        allowOnlyGroup(true, 4);
        check("pet feed accepts boots type 6 in the vitality group", bool("acceptsPet", boots));
        check("sale predicate accepts boots type 6 in the vitality group", bool("saleEligible", boots));
        check("sale predicate rejects necklace type 5 from vitality", !bool("saleEligible", necklace));
        allowOnlyGroup(true, 2);
        check("sale predicate accepts necklace type 5 in the dexterity group", bool("saleEligible", necklace));
        check("sale predicate rejects boots type 6 from dexterity", !bool("saleEligible", boots));
        allowOnlyGroup(true, 4);
        saleIds.clear();
        runNativeState3Sale(necklace, boots);
        eq("native state-3 scan skips necklace and sells matching boots", saleIds, Collections.singletonList(Short.valueOf((short)502)));

        for (int mask : masks) {
            allowOnlyGroup(false, mask);
            allowOnlyGroup(true, mask);
            for (int type = 0; type < expectedGroup.length; type++) {
                int id = 500 + type * 10 + mask;
                Object item = item(id, 1, false, 0, type, -1);
                boolean accepted = expectedGroup[type] == mask;
                check("pet type " + type + " maps to mask " + mask, bool("acceptsPet", item) == accepted);
                check("sale predicate type " + type + " maps to mask " + mask, bool("saleEligible", item) == accepted);

                saleIds.clear();
                runNativeState3Sale(item);
                List<Short> expectedIds = accepted ? Collections.singletonList(Short.valueOf((short)id)) : Collections.<Short>emptyList();
                eq("native state-3 sale type " + type + " mask " + mask + " sends only matching item", saleIds, expectedIds);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: HungEquipmentFiltersBinaryTest <candidate.jar>");
        File jar = new File(args[0]);
        prepare(jar);
        verifyHooks(jar);
        testRowsAndActions();
        testFiltersAndPreflight();
        testNativeEquipmentTypeSales();
        testSettingsReload();
        System.out.println("ALL PASS checks=" + checks);
        ((URLClassLoader)loader).close();
    }
}
