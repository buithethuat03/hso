import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
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
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Archive, three-slot, CHIBI RMS and no-cache checks for the packed launcher. */
public final class ChibiX3HostTest implements Opcodes {
    private static final String SHARED = "luna/generated/ExpEstimateSharedRmsStore";
    private static final String AUTH = "luna/generated/ChibiLoginAuth";
    private static final String MANIFEST = "META-INF/MANIFEST.MF";
    private static final String PREFIX = "HSO_CHIBI_";
    private static final String[] SLOTS = {"", "b/", "c/"};
    private static int checks;

    private ChibiX3HostTest() { }

    public static void main(String[] args) throws Exception {
        check(args.length == 3, "candidate-x3.jar chibi_up.jar launcher-template.jar");
        File candidate = new File(args[0]), single = new File(args[1]), template = new File(args[2]);
        check(candidate.isFile() && single.isFile() && template.isFile(), "All three JAR inputs exist");
        check(HungBySonX3Packer.CHIBI_SHA.equals(HsoChildX3Packer.hash(single)),
                "Single input is the pinned no-cache CHIBI release");
        check(HungBySonX3Packer.TEMPLATE_SHA.equals(HsoChildX3Packer.hash(template)),
                "Launcher template is the pinned X3 template");

        Map<String, byte[]> source = HsoChildX3Packer.readJar(single);
        Map<String, byte[]> wrapper = HsoChildX3Packer.readJar(template);
        Map<String, byte[]> packed = HsoChildX3Packer.readJar(candidate);
        verifyArchive(source, wrapper, packed, single, template);
        verifySlotParity(source, packed);
        verifyLinkage(packed, candidate);
        verifyRuntime(candidate);
        System.out.println("ChibiX3HostTest PASS (" + checks
                + " checks); 800 linked classes, endpoint/auth parity, 3 isolated CHIBI RMS slots, "
                + "shared EXP and disabled monster cache");
    }

    private static void verifyArchive(Map<String, byte[]> source, Map<String, byte[]> wrapper,
            Map<String, byte[]> packed, File single, File template) throws Exception {
        check(source.get(AUTH + ".class") != null, "Source contains the CHIBI login-auth helper");
        check(classCount(source) == 259, "Pinned CHIBI source has 259 classes");
        check(hasMethod(source.get(AUTH + ".class"), "II", "(Ljava/lang/String;[B)Ljava/lang/String;"),
                "CHIBI login-auth helper keeps its username/session-key ABI");
        Attributes original = manifest(source.get(MANIFEST));
        check("HSO CHIBI".equals(original.getValue("MIDlet-Name"))
                && "HSO CHIBI".equals(original.getValue("MIDlet-Vendor")), "Single source CHIBI identity");
        check(original.getValue("MIDlet-1") != null
                && original.getValue("MIDlet-1").endsWith(",com.silverknight.TemMidlet"),
                "Single source entrypoint is TemMidlet");

        Attributes manifest = manifest(packed.get(MANIFEST));
        check("HSO CHIBI X3".equals(manifest.getValue("MIDlet-Name"))
                && "HSO_CHIBI_X3".equals(manifest.getValue("MIDlet-Vendor")), "Separate CHIBI X3 identity");
        check(manifest.getValue("MIDlet-1") != null
                && manifest.getValue("MIDlet-1").endsWith(",MrQuyet.MrQuyet")
                && manifest.getValue("MIDlet-2") == null, "One native launcher MIDlet");
        check("a,b,c".equals(manifest.getValue("HSO-X3-RMS-Prefixes")), "Three virtual RMS aliases");
        check(HungBySonX3Packer.CHIBI_SHA.equalsIgnoreCase(
                manifest.getValue("HSO-X3-Source-SHA-256")), "Manifest binds the CHIBI source hash");
        check(HungBySonX3Packer.TEMPLATE_SHA.equalsIgnoreCase(
                manifest.getValue("HSO-X3-Template-SHA-256")), "Manifest binds the launcher template hash");
        for (int slot = 0; slot < 3; slot++) {
            String midlet = slot == 0 ? "TemMidlet" : slot == 1 ? "b.TemMidlet" : "c.TemMidlet";
            String row = manifest.getValue(String.valueOf(slot));
            check(row != null && row.contains("," + midlet + ",/chibi-up-x3.ini"),
                    "Virtual manifest entry " + slot + " points to its slot MIDlet and CHIBI INI");
        }
        check(Arrays.equals(source.get(MANIFEST), packed.get("chibi-up-x3.ini")),
                "Virtual launcher INI is the source manifest byte-for-byte");

        Set<String> expectedEntries = expectedEntries(source, wrapper);
        check(expectedEntries.equals(packed.keySet()), "Candidate archive topology matches source, wrappers and X3 slots");
        int wrapperClasses = 0;
        for (Map.Entry<String, byte[]> entry : source.entrySet()) {
            String name = entry.getKey();
            if (!name.endsWith(".class") && !MANIFEST.equals(name))
                check(Arrays.equals(entry.getValue(), packed.get(name)), "Source resource unchanged: " + name);
        }
        for (Map.Entry<String, byte[]> entry : wrapper.entrySet()) {
            String name = entry.getKey();
            if ((name.startsWith("lib/") || name.startsWith("MrQuyet/")) && name.endsWith(".class")) {
                wrapperClasses++;
                check(Arrays.equals(entry.getValue(), packed.get(name)), "Launcher wrapper unchanged: " + name);
            }
        }
        check(wrapperClasses == 19, "Exactly 19 launcher wrapper classes are retained");
        check(wrapper.get("MrQuyet/mid.png") != null
                && Arrays.equals(wrapper.get("MrQuyet/mid.png"), packed.get("MrQuyet/mid.png")),
                "Launcher icon is byte-identical to the template");
        check(classCount(packed) == 800, "X3 archive has exactly 800 classes");
        check(classCount(packed) == 3 * (classCount(source) - 1) + 3 + 3 + 1 + 19,
                "Class count is three local copies, three Static/support pairs, one shared EXP and wrappers");

        check(packed.containsKey(AUTH + ".class") && packed.containsKey("b/" + AUTH + ".class")
                && packed.containsKey("c/" + AUTH + ".class"), "CHIBI auth helper exists in all three clients");
        for (String slot : SLOTS) {
            check(containsClassString(packed, slot, "103.77.173.232"),
                    "CHIBI endpoint table is present in slot " + slot);
            check(containsClassInteger(packed, slot, 19129),
                    "CHIBI direct endpoint port is present in slot " + slot);
            check(containsClassString(packed, slot, "HSO CHIBI"),
                    "CHIBI server label is present in slot " + slot);
        }

        check(Arrays.equals(source.get(SHARED + ".class"), packed.get(SHARED + ".class")),
                "Shared EXP backend stays byte-identical to the CHIBI source in the root namespace");
        check(!packed.containsKey("b/" + SHARED + ".class") && !packed.containsKey("c/" + SHARED + ".class"),
                "Only one root shared EXP backend exists");
        check(HungBySonX3Packer.CHIBI_SHA.equals(HsoChildX3Packer.hash(single))
                && HungBySonX3Packer.TEMPLATE_SHA.equals(HsoChildX3Packer.hash(template)),
                "Source and template inputs remained unchanged while inspecting the candidate");
    }

    private static Set<String> expectedEntries(Map<String, byte[]> source, Map<String, byte[]> wrapper) {
        Set<String> expected = new LinkedHashSet<String>();
        Map<String, String> normalize = normalizeMap();
        for (String name : source.keySet()) {
            if (name.endsWith(".class")) {
                String cls = RepackSupportX3.cls(name.substring(0, name.length() - 6), normalize);
                if (SHARED.equals(cls)) expected.add(cls + ".class");
                else {
                    expected.add(cls + ".class");
                    expected.add("b/" + cls + ".class");
                    expected.add("c/" + cls + ".class");
                }
            } else if (!MANIFEST.equals(name)) expected.add(name);
        }
        for (String extra : new String[] {"Static.class", "b/Static.class", "c/Static.class",
                "X3SlotSupport.class", "b/X3SlotSupport.class", "c/X3SlotSupport.class",
                MANIFEST, "MrQuyet/mid.png", "chibi-up-x3.ini"}) expected.add(extra);
        for (String name : wrapper.keySet())
            if ((name.startsWith("lib/") || name.startsWith("MrQuyet/")) && name.endsWith(".class")) expected.add(name);
        return expected;
    }

    private static void verifySlotParity(Map<String, byte[]> source, Map<String, byte[]> packed) throws Exception {
        Map<String, String> normalize = normalizeMap();
        Map<String, byte[]> classes = new LinkedHashMap<String, byte[]>();
        for (Map.Entry<String, byte[]> entry : source.entrySet()) if (entry.getKey().endsWith(".class")) {
            String name = entry.getKey().substring(0, entry.getKey().length() - 6);
            byte[] bytes = HsoChildX3Packer.transform(entry.getValue(), normalize, 0, true, new int[4]);
            String normalizedName = RepackSupportX3.cls(name, normalize);
            check(!classes.containsKey(normalizedName), "Source normalization has no class collision: " + normalizedName);
            classes.put(normalizedName, bytes);
        }
        check(classes.containsKey(SHARED), "Source-normalized class map contains the shared EXP backend");
        Map<String, byte[]> local = new LinkedHashMap<String, byte[]>(classes);
        local.remove(SHARED);

        Map<String, String> rootMap = new LinkedHashMap<String, String>();
        for (String name : classes.keySet()) rootMap.put(name, name);
        rootMap.put("Static", "Static");
        rootMap.put(HungBySonX3Packer.SUPPORT, HungBySonX3Packer.SUPPORT);
        HsoChildX3Packer.addWrapperTypes(rootMap);
        rootMap.put("javax/microedition/lcdui/Alert", "lib/Alert");
        rootMap.put("javax/microedition/lcdui/AlertType", "lib/AlertType");

        int[] counts = new int[4];
        int id = 0;
        for (Map.Entry<String, byte[]> entry : local.entrySet()) {
            byte[] lifecycle = HsoChildX3Packer.addLifecycle(entry.getValue(), id++);
            ClassNode node = new ClassNode();
            new ClassReader(lifecycle).accept(node, 0);
            node.access |= ACC_PUBLIC;
            for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray())
                if (insn instanceof LdcInsnNode) {
                    Object constant = ((LdcInsnNode)insn).cst;
                    check(!"hsosky_cfg".equals(constant) && !"inv_hsosky_cfg".equals(constant),
                            "CHIBI source has no legacy reflective RMS alias requiring slot rewriting");
                }
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            byte[] expected = HsoChildX3Packer.transform(writer.toByteArray(), rootMap, 0, false, counts);
            check(Arrays.equals(expected, packed.get(entry.getKey() + ".class")),
                    "Root class matches deterministic source lifecycle/RMS transform: " + entry.getKey());
        }
        check(Arrays.equals(counts, new int[] {1, 1, 1, 0}),
                "Root CHIBI storage redirects one open/list/delete boundary and no reflective alias");
        check(Arrays.equals(HsoChildX3Packer.makeStatic(local, ""), packed.get("Static.class")),
                "Root Static lifecycle is generated from the current source classes");

        for (int slot = 1; slot < 3; slot++) {
            String prefix = SLOTS[slot];
            Map<String, String> reverse = new LinkedHashMap<String, String>();
            for (String path : packed.keySet()) if (path.startsWith(prefix) && path.endsWith(".class")) {
                String relocated = path.substring(0, path.length() - 6);
                reverse.put(relocated, relocated.substring(prefix.length()));
            }
            check(reverse.size() == classCountForSlot(packed, prefix), "Slot class namespace is complete: " + prefix);
            for (String relocated : reverse.keySet()) {
                String rootName = reverse.get(relocated);
                byte[] root = packed.get(rootName + ".class");
                check(root != null, "Root peer exists for " + relocated);
                ClassNode node = new ClassNode();
                new ClassReader(packed.get(relocated + ".class")).accept(node, 0);
                normalizeRmsAliasNames(node, slot);
                ClassWriter writer = new ClassWriter(0);
                node.accept(writer);
                byte[] normalized = HsoChildX3Packer.transform(writer.toByteArray(), reverse, 0, true, new int[4]);
                check(Arrays.equals(canonical(root), canonical(normalized)),
                        "Complete slot bytecode parity without literal rewriting: " + relocated);
            }
            check(Arrays.equals(HsoChildX3Packer.makeStatic(local, prefix), packed.get(prefix + "Static.class")),
                    "Slot Static lifecycle matches source classes: " + prefix);
            check(packed.containsKey(prefix + HungBySonX3Packer.SUPPORT + ".class"),
                    "Slot resource helper exists: " + prefix);
        }
        check(sharedAdaptersUseRoot(packed), "All slot EXP adapters delegate to the one root backend");
    }

    private static boolean sharedAdaptersUseRoot(Map<String, byte[]> packed) {
        for (String slot : SLOTS) {
            byte[] bytes = packed.get(slot + "luna/generated/ExpEstimateRmsStore.class");
            if (bytes == null) return false;
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            boolean found = false;
            for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray())
                if (insn instanceof MethodInsnNode && ((MethodInsnNode)insn).owner.equals(SHARED)) found = true;
            if (!found) return false;
        }
        return true;
    }

    private static void normalizeRmsAliasNames(ClassNode node, int slot) {
        String alias = slot == 1 ? "b" : "c";
        for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray())
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)insn;
                if (call.owner.equals("lib/RecordStore") && call.name.length() > 1
                        && call.name.charAt(0) == alias.charAt(0)) {
                    String suffix = call.name.substring(1);
                    if (suffix.equals("penRecordStore") || suffix.equals("istRecordStores")
                            || suffix.equals("eleteRecordStore")) call.name = "a" + suffix;
                }
            }
    }

    private static void verifyLinkage(Map<String, byte[]> packed, File candidate) throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[] {candidate.toURI().toURL()},
                ChibiX3HostTest.class.getClassLoader());
        int linked = 0;
        try {
            for (Map.Entry<String, byte[]> entry : packed.entrySet()) if (entry.getKey().endsWith(".class")) {
                ClassNode node = new ClassNode();
                new ClassReader(entry.getValue()).accept(node, 0);
                check((node.version & 0xffff) <= 51, "CLDC-compatible class version: " + entry.getKey());
                String name = entry.getKey().substring(0, entry.getKey().length() - 6).replace('/', '.');
                Class<?> type = Class.forName(name, false, loader);
                type.getDeclaredFields();
                type.getDeclaredMethods();
                type.getDeclaredConstructors();
                linked++;
            }
        } finally { loader.close(); }
        check(linked == 800, "All 800 candidate classes link under JVM verification");
    }

    private static void verifyRuntime(File candidate) throws Exception {
        Common emulator = new Common(null);
        MemoryRecordStoreManager manager = new MemoryRecordStoreManager();
        manager.init(emulator);
        emulator.setRecordStoreManager(manager);
        MIDletBridge.setMicroEmulator(emulator);
        seedOldSingleRecords();

        URLClassLoader loader = new URLClassLoader(new URL[] {candidate.toURI().toURL()},
                ChibiX3HostTest.class.getClassLoader());
        try {
            Class<?>[] storage = new Class<?>[3];
            Class<?>[] midlet = new Class<?>[3];
            Class<?>[] attack = new Class<?>[3];
            byte[][] accounts = new byte[3][];
            byte[][] selections = new byte[3][];
            boolean[] players = {false, true, false};
            int[] delays = {211, 322, 433};

            for (int slot = 0; slot < 3; slot++) {
                String ns = SLOTS[slot].replace('/', '.');
                storage[slot] = Class.forName(ns + "luna.generated.InvestigateStorage", true, loader);
                midlet[slot] = Class.forName(ns + "TemMidlet", true, loader);
                attack[slot] = Class.forName(ns + "luna.features.AutoAttackRmsStore", true, loader);
                Class<?> cache = Class.forName(ns + "luna.features.MonsterCacheSupport", true, loader);

                verifyNoCacheApi(cache, loader, slot);
                accounts[slot] = new byte[] {(byte)(31 + slot), (byte)(47 + slot), (byte)(59 + slot)};
                selections[slot] = new byte[] {(byte)(71 + slot), (byte)(83 + slot)};

                Method open = storage[slot].getMethod("openRecordStore", String.class, Boolean.TYPE);
                expectMissing(open, "login", "CHIBI slot starts without importing single-client login data " + slot);
                Method saveAccount = midlet[slot].getMethod("a", String.class, byte[].class);
                Method loadAccount = midlet[slot].getMethod("a", String.class);
                saveAccount.invoke(null, "login", ((byte[])accounts[slot].clone()));
                check(Arrays.equals(accounts[slot], (byte[])loadAccount.invoke(null, "login")),
                        "Native account read/write is isolated in slot " + slot);
                check(readRecord(slotRms(slot, "login")) != null, "Native account physical name has slot + CHIBI prefix " + slot);

                Method saveSelection = storage[slot].getMethod("saveSelection", byte[].class);
                Method loadSelection = storage[slot].getMethod("loadSelection");
                check(((Boolean)saveSelection.invoke(null, (Object)selections[slot])).booleanValue(),
                        "Selection saves in slot " + slot);
                check(Arrays.equals(selections[slot], (byte[])loadSelection.invoke(null)),
                        "Selection reloads in slot " + slot);

                Object attackStore = attack[slot].getDeclaredConstructor().newInstance();
                int[] settings = new int[] {-1, -1};
                attack[slot].getMethod("load", int[].class).invoke(attackStore, (Object)settings);
                check(settings[0] == 0 && settings[1] == 650,
                        "Auto Attack does not import the old single-client record in slot " + slot);
                attack[slot].getMethod("save", Boolean.TYPE, Integer.TYPE)
                        .invoke(attackStore, Boolean.valueOf(players[slot]), Integer.valueOf(delays[slot]));
                Arrays.fill(settings, -1);
                attack[slot].getMethod("load", int[].class).invoke(attackStore, (Object)settings);
                check(settings[0] == (players[slot] ? 1 : 0) && settings[1] == delays[slot],
                        "Auto Attack settings persist only within slot " + slot);
                byte[] expectedAttack = new byte[] {1, (byte)(players[slot] ? 1 : 0),
                        (byte)(delays[slot] >>> 8), (byte)delays[slot]};
                check(Arrays.equals(expectedAttack, readRecord(slotRms(slot, "hbs_auto_attack_v1"))),
                        "Auto Attack physical RMS name has slot + CHIBI prefix " + slot);

                createProbe(open, "delete_probe", (byte)(90 + slot));
                check(!contains((String[])storage[slot].getMethod("listRecordStores").invoke(null), "mc_inv_v1")
                        && !contains((String[])storage[slot].getMethod("listRecordStores").invoke(null), "mc_v1"),
                        "Disabled cache has no slot RMS record " + slot);
            }

            verifyOldSingleRecordsUnchanged();
            verifySharedExp(loader, storage);
            verifyLists(storage);
            verifyDeleteIsolation(storage);
            verifyRestartPersistence(loader, storage, midlet, attack, accounts, selections, players, delays);
            verifyOldSingleRecordsUnchanged();
        } finally { loader.close(); }
    }

    private static void verifyNoCacheApi(Class<?> support, ClassLoader loader, int slot) throws Exception {
        Class<?> api = Class.forName(SLOTS[slot].replace('/', '.') + "luna.api.MonsterCacheAccess", true, loader);
        final int[] calls = {0};
        Object access = Proxy.newProxyInstance(loader, new Class<?>[] {api}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                calls[0]++;
                throw new AssertionError("Disabled cache called access." + method.getName());
            }
        });
        Method[] methods = support.getDeclaredMethods();
        int publicApis = 0;
        for (Method method : methods) if (Modifier.isPublic(method.getModifiers())
                && Modifier.isStatic(method.getModifiers())
                && !"cinitclone".equals(method.getName()) && !"clears".equals(method.getName())) {
            publicApis++;
            Class<?>[] types = method.getParameterTypes();
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                if (types[i] == api) args[i] = access;
                else if (types[i] == int.class) args[i] = Integer.valueOf(41);
                else if (types[i] == Object.class) args[i] = new Object();
                else if (types[i] == String.class) args[i] = "fixture-server";
                else if (types[i] == DataInputStream.class)
                    args[i] = new DataInputStream(new ByteArrayInputStream(new byte[0]));
                else throw new AssertionError("Unexpected CHIBI cache API parameter " + types[i]);
            }
            Object result;
            try { result = method.invoke(null, args); }
            catch (InvocationTargetException e) { throw new AssertionError("Cache API failed: " + method.getName(), e.getCause()); }
            Class<?> returnType = method.getReturnType();
            if (returnType == Boolean.TYPE) check(Boolean.FALSE.equals(result), "Cache API returns false: " + method.getName());
            else if (returnType == String.class) check("".equals(result), "Cache status is empty");
            else if (returnType == int[].class) check(((int[])result).length == 0, "Remembered cache is empty");
            else check(returnType == Void.TYPE, "Cache API returns no value: " + method.getName());
        }
        check(publicApis == 15, "All 15 public CHIBI cache API entrypoints were invoked in slot " + slot);
        check(calls[0] == 0, "No CHIBI cache entrypoint calls through its access bridge in slot " + slot);
    }

    private static void verifyLists(Class<?>[] storage) throws Exception {
        for (int slot = 0; slot < 3; slot++) {
            String[] names = (String[])storage[slot].getMethod("listRecordStores").invoke(null);
            Set<String> actual = names == null ? new HashSet<String>() : new HashSet<String>(Arrays.asList(names));
            for (String required : new String[] {"login", "ms_inv_v1", "hbs_auto_attack_v1", "delete_probe"})
                check(actual.contains(required), "Slot " + slot + " scoped listing includes " + required);
            check(!actual.contains("old_only_probe") && !actual.contains("mc_inv_v1") && !actual.contains("mc_v1"),
                    "Slot " + slot + " listing hides old single data and disabled cache stores");
            check(actual.contains("hbs_exp_levels_v2") == (slot == 0),
                    "Shared EXP appears only through its root/a RMS backend in slot " + slot);
        }
    }

    private static void verifyDeleteIsolation(Class<?>[] storage) throws Exception {
        Method delete = storage[0].getMethod("deleteRecordStore", String.class);
        delete.invoke(null, "delete_probe");
        check(!exists(slotRms(0, "delete_probe")), "Slot a delete removes only its CHIBI record");
        check(exists(slotRms(1, "delete_probe")) && exists(slotRms(2, "delete_probe")),
                "Slot a delete leaves b/c records intact");
        check(Arrays.equals(new byte[] {5, 7}, readRecord(PREFIX + "delete_probe")),
                "Slot delete cannot touch the old single-client record");
        for (int slot = 1; slot < 3; slot++) {
            String[] names = (String[])storage[slot].getMethod("listRecordStores").invoke(null);
            check(contains(names, "delete_probe"), "Delete remains isolated from slot " + slot);
        }
        String[] rootNames = (String[])storage[0].getMethod("listRecordStores").invoke(null);
        check(!contains(rootNames, "delete_probe"), "Deleted slot a record leaves its logical listing");
    }

    private static void verifySharedExp(ClassLoader loader, Class<?>[] storage) throws Exception {
        Class<?> shared = Class.forName(SHARED.replace('/', '.'), true, loader);
        Method load = shared.getMethod("load");
        Method save = shared.getMethod("save", byte[].class);
        check(load.invoke(null) == null, "Shared EXP starts fresh instead of importing old single data");
        check(!exists(slotRms(0, "hbs_exp_levels_v2")), "Fresh EXP read does not create a store");
        byte[] level = versionTwo(9901, 765432100L, 765432100L, 20, 30);
        check(((Boolean)save.invoke(null, (Object)level)).booleanValue(), "Shared EXP accepts a valid new level");
        check(exists(slotRms(0, "hbs_exp_levels_v2")), "Shared EXP is stored at aHSO_CHIBI_hbs_exp_levels_v2");
        check(!exists(slotRms(1, "hbs_exp_levels_v2")) && !exists(slotRms(2, "hbs_exp_levels_v2")),
                "One root shared EXP backend uses only the a slot RMS alias");
        check(containsLevel(readRecord(slotRms(0, "hbs_exp_levels_v2")), 9901),
                "Root EXP store retains the new level through the source backend");
        String[] names = (String[])storage[0].getMethod("listRecordStores").invoke(null);
        check(contains(names, "hbs_exp_levels_v2"), "Root InvestigateStorage lists the shared EXP table");
    }

    private static void verifyRestartPersistence(ClassLoader loader, Class<?>[] storage, Class<?>[] midlet,
            Class<?>[] attack, byte[][] accounts, byte[][] selections, boolean[] players, int[] delays)
            throws Exception {
        for (int slot = 0; slot < 3; slot++) {
            Object life = Class.forName(SLOTS[slot].replace('/', '.') + "Static", true, loader)
                    .getDeclaredConstructor().newInstance();
            life.getClass().getMethod("clear").invoke(life);
            life.getClass().getMethod("cinitclone").invoke(life);
        }
        for (int slot = 0; slot < 3; slot++) {
            Method loadSelection = storage[slot].getMethod("loadSelection");
            check(Arrays.equals(selections[slot], (byte[])loadSelection.invoke(null)),
                    "Static clear/restart preserves slot " + slot + " selection");
            check(Arrays.equals(accounts[slot], (byte[])midlet[slot].getMethod("a", String.class)
                    .invoke(null, "login")), "Static clear/restart preserves slot " + slot + " account");
            Object store = attack[slot].getDeclaredConstructor().newInstance();
            int[] settings = new int[2];
            attack[slot].getMethod("load", int[].class).invoke(store, (Object)settings);
            check(settings[0] == (players[slot] ? 1 : 0) && settings[1] == delays[slot],
                    "Static clear/restart preserves slot " + slot + " Auto Attack settings");
        }
        Class<?> shared = Class.forName(SHARED.replace('/', '.'), true, loader);
        byte[] levels = (byte[])shared.getMethod("load").invoke(null);
        check(containsLevel(levels, 9901), "Static clear/restart preserves the root shared EXP level");
    }

    private static void createProbe(Method open, String name, byte marker) throws Exception {
        RecordStore store = (RecordStore)open.invoke(null, name, Boolean.TRUE);
        try { store.addRecord(new byte[] {marker}, 0, 1); }
        finally { store.closeRecordStore(); }
    }

    private static void expectMissing(Method open, String name, String text) throws Exception {
        boolean missing = false;
        try { open.invoke(null, name, Boolean.FALSE); }
        catch (InvocationTargetException e) { missing = e.getCause() instanceof RecordStoreNotFoundException; }
        check(missing, text);
    }

    private static void seedOldSingleRecords() throws Exception {
        seed(PREFIX + "login", new byte[] {1, 3, 5});
        seed(PREFIX + "ms_inv_v1", new byte[] {2, 4, 6});
        seed(PREFIX + "hbs_auto_attack_v1", new byte[] {1, 1, 2, (byte)138});
        seed(PREFIX + "hbs_exp_levels_v2", versionTwo(8700, 500000000L, 500000000L, 20, 30));
        seed(PREFIX + "delete_probe", new byte[] {5, 7});
        seed(PREFIX + "old_only_probe", new byte[] {8, 9});
    }

    private static void verifyOldSingleRecordsUnchanged() throws Exception {
        check(Arrays.equals(new byte[] {1, 3, 5}, readRecord(PREFIX + "login")), "Old single account record stays untouched");
        check(Arrays.equals(new byte[] {2, 4, 6}, readRecord(PREFIX + "ms_inv_v1")), "Old single selection stays untouched");
        check(Arrays.equals(new byte[] {1, 1, 2, (byte)138}, readRecord(PREFIX + "hbs_auto_attack_v1")),
                "Old single Auto Attack record stays untouched");
        check(containsLevel(readRecord(PREFIX + "hbs_exp_levels_v2"), 8700), "Old single EXP record stays untouched");
        check(Arrays.equals(new byte[] {5, 7}, readRecord(PREFIX + "delete_probe")), "Old single delete target stays untouched");
        check(Arrays.equals(new byte[] {8, 9}, readRecord(PREFIX + "old_only_probe")), "Old single-only record stays untouched");
    }

    private static String slotRms(int slot, String name) {
        return String.valueOf((char)('a' + slot)) + PREFIX + name;
    }

    private static void seed(String name, byte[] data) throws Exception {
        RecordStore store = RecordStore.openRecordStore(name, true);
        try { store.addRecord(data, 0, data.length); }
        finally { store.closeRecordStore(); }
    }

    private static byte[] readRecord(String name) throws Exception {
        RecordStore store = RecordStore.openRecordStore(name, false);
        try { return store.getRecord(1); }
        finally { store.closeRecordStore(); }
    }

    private static boolean exists(String name) throws Exception {
        RecordStore store = null;
        try { store = RecordStore.openRecordStore(name, false); return true; }
        catch (RecordStoreNotFoundException missing) { return false; }
        finally { if (store != null) store.closeRecordStore(); }
    }

    private static byte[] versionTwo(int level, long low, long high, int samples, int span) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x45585031); out.writeByte(2); out.writeByte(1); out.writeInt(level);
        out.writeLong(low); out.writeLong(high); out.writeShort(samples); out.writeShort(span); out.flush();
        return bytes.toByteArray();
    }

    private static boolean containsLevel(byte[] encoded, int expected) throws Exception {
        if (encoded == null || encoded.length < 6) return false;
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
        try {
            if (in.readInt() != 0x45585031 || in.readUnsignedByte() != 2) return false;
            int count = in.readUnsignedByte();
            for (int i = 0; i < count; i++) {
                int level = in.readInt(); in.readLong(); in.readLong(); in.readUnsignedShort(); in.readUnsignedShort();
                if (level == expected) return true;
            }
            return false;
        } finally { in.close(); }
    }

    private static boolean contains(String[] values, String expected) {
        if (values != null) for (String value : values) if (expected.equals(value)) return true;
        return false;
    }

    private static boolean containsClassString(Map<String, byte[]> entries, String prefix, String value) {
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            if (prefix.length() == 0) {
                if (entry.getKey().startsWith("b/") || entry.getKey().startsWith("c/")
                        || entry.getKey().startsWith("lib/") || entry.getKey().startsWith("MrQuyet/")) continue;
            } else if (!entry.getKey().startsWith(prefix)) continue;
            ClassNode node = new ClassNode();
            new ClassReader(entry.getValue()).accept(node, 0);
            for (FieldNode field : node.fields) if (value.equals(field.value)) return true;
            for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray())
                if (insn instanceof LdcInsnNode && value.equals(((LdcInsnNode)insn).cst)) return true;
        }
        return false;
    }

    private static boolean containsClassInteger(Map<String, byte[]> entries, String prefix, int value) {
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            if (prefix.length() == 0) {
                if (entry.getKey().startsWith("b/") || entry.getKey().startsWith("c/")
                        || entry.getKey().startsWith("lib/") || entry.getKey().startsWith("MrQuyet/")) continue;
            } else if (!entry.getKey().startsWith(prefix)) continue;
            ClassNode node = new ClassNode();
            new ClassReader(entry.getValue()).accept(node, 0);
            for (FieldNode field : node.fields) if (Integer.valueOf(value).equals(field.value)) return true;
            for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof IntInsnNode && ((IntInsnNode)insn).operand == value) return true;
                if (insn instanceof LdcInsnNode && Integer.valueOf(value).equals(((LdcInsnNode)insn).cst)) return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(byte[] bytes, String name, String descriptor) {
        if (bytes == null) return false;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods)
            if (name.equals(method.name) && descriptor.equals(method.desc)) return true;
        return false;
    }

    private static int classCount(Map<String, byte[]> entries) {
        int count = 0;
        for (String name : entries.keySet()) if (name.endsWith(".class")) count++;
        return count;
    }

    private static int classCountForSlot(Map<String, byte[]> entries, String prefix) {
        int count = 0;
        for (String name : entries.keySet()) if (name.startsWith(prefix) && name.endsWith(".class")) count++;
        return count;
    }

    private static Attributes manifest(byte[] bytes) throws Exception {
        if (bytes == null) throw new AssertionError("Manifest missing");
        return new Manifest(new ByteArrayInputStream(bytes)).getMainAttributes();
    }

    private static Map<String, String> normalizeMap() {
        Map<String, String> normalize = new LinkedHashMap<String, String>();
        normalize.put("com/silverknight/TemMidlet", "TemMidlet");
        return normalize;
    }

    private static byte[] canonical(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
}
