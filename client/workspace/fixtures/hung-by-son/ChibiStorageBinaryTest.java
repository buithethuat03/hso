import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreNotFoundException;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.util.MemoryRecordStoreManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Proves HSO CHIBI starts with a separate RMS namespace and cannot import,
 * overwrite, list, or delete records from the Hung_By_Son Investigate suite.
 * Run with baseline.jar candidate.jar report.properties microemulator.jar;
 * ASM and MicroEmulator must also be on the host classpath.
 */
public final class ChibiStorageBinaryTest implements Opcodes {
    private static final String BASELINE_SHA = "BCF304BB6245CDBB6F48D5888404F8186E4E0895E2DCDAF608C68D53FEADA6E2";
    private static final String PREFIX = "HSO_CHIBI_";
    private static final String STORAGE = "luna/generated/InvestigateStorage";
    private static final String AUTO = "luna/features/AutoAttackRmsStore";
    private static final String SHARED = "luna/generated/ExpEstimateSharedRmsStore";
    private static final String SUPPORT = "luna/features/ExpEstimateSupport";
    private static final String RMS = "javax/microedition/rms/RecordStore";
    private static int checks;
    private static final Map<String, byte[]> OLD_RECORDS = new LinkedHashMap<String, byte[]>();

    private ChibiStorageBinaryTest() { }

    public static void main(String[] args) throws Exception {
        require(args.length == 4, "baseline.jar candidate.jar report.properties microemulator.jar");
        File baseline = new File(args[0]);
        File candidate = new File(args[1]);
        File reportFile = new File(args[2]);
        File micro = new File(args[3]);
        require(baseline.isFile() && candidate.isFile() && micro.isFile(),
                "Baseline, candidate, and MicroEmulator JARs exist");
        require(BASELINE_SHA.equals(hash(baseline)), "Baseline bytes match the inspected Hung_By_Son input");
        require(!BASELINE_SHA.equals(hash(candidate)), "Candidate is a distinct artifact");
        verifyIdentity(baseline, candidate);
        Properties bytecode = verifyBytecode(candidate);

        Common emulator = new Common(null);
        MemoryRecordStoreManager manager = new MemoryRecordStoreManager();
        manager.init(emulator);
        emulator.setRecordStoreManager(manager);
        MIDletBridge.setMicroEmulator(emulator);

        seedLegacyModRecords();
        byte[] account = new byte[] { 12, 34, 56, 78 };
        seedNativeStore(baseline, account);
        check(Arrays.equals(account, readRecord("inv_native_fixture_probe")),
                "Baseline native storage wrapper writes the inv_ physical name");
        OLD_RECORDS.put("inv_native_fixture_probe", readRecord("inv_native_fixture_probe"));

        URLClassLoader first = new URLClassLoader(new URL[] { candidate.toURI().toURL() },
                ChibiStorageBinaryTest.class.getClassLoader());
        byte[] replacementAccount = new byte[] { 31, 37, 41, 43 };
        int[] savedAttack = new int[] { 1, 650 };
        byte[] freshExp = versionTwo(9901, 765432100L, 765432100L, 20, 30);
        try {
            verifyOldPayloadsAreValid(first);
            verifyNativeStoreStartsFresh(first, replacementAccount);
            verifyWrapperIsolation(first);
            verifyAutoAttackIsolation(first, savedAttack);
            verifySharedExpIsolation(first, freshExp, 9901);
            verifyOldRecordsUntouched();
        } finally {
            first.close();
        }

        URLClassLoader restarted = new URLClassLoader(new URL[] { candidate.toURI().toURL() },
                ChibiStorageBinaryTest.class.getClassLoader());
        try {
            verifyFreshLoaderPersistence(restarted, replacementAccount, savedAttack, 9901);
            verifyOldRecordsUntouched();
            verifyVisibleNames(restarted);
        } finally {
            restarted.close();
        }
        verifyCandidateCreatedNames();

        Properties report = new Properties();
        report.putAll(bytecode);
        report.setProperty("input.sha256", hash(baseline));
        report.setProperty("candidate.sha256", hash(candidate));
        report.setProperty("suite.name", "HSO CHIBI");
        report.setProperty("rms.prefix", PREFIX);
        report.setProperty("old.native.store", "unread by candidate; original bytes retained");
        report.setProperty("old.auto.attack", "unread by candidate; original bytes retained");
        report.setProperty("old.exp.current", "unread by candidate; original bytes retained");
        report.setProperty("old.exp.legacy", "valid v1 payloads not imported; original bytes retained");
        report.setProperty("fresh.loader.persistence", "PASS");
        report.setProperty("wrapper.list.delete", "PASS");
        report.setProperty("checks", String.valueOf(checks));
        File parent = reportFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IllegalStateException("Cannot create report directory " + parent);
        FileOutputStream out = new FileOutputStream(reportFile);
        try { report.store(out, "HSO CHIBI fresh RMS isolation"); }
        finally { out.close(); }
        System.out.println("CHIBI_STORAGE_BINARY=PASS; checks=" + checks
                + "; native, Auto Attack, EXP, list/delete and fresh-loader persistence isolated");
    }

    private static void verifyIdentity(File baseline, File candidate) throws Exception {
        Attributes old = manifest(baseline).getMainAttributes();
        Attributes now = manifest(candidate).getMainAttributes();
        require("Hung_By_Son Investigate".equals(old.getValue("MIDlet-Name"))
                        && "Hung_By_Son_Investigate".equals(old.getValue("MIDlet-Vendor")),
                "Baseline suite identity is the known Investigate suite");
        require("HSO CHIBI".equals(now.getValue("MIDlet-Name")),
                "Candidate MIDlet name is HSO CHIBI");
        require(now.getValue("MIDlet-Vendor") != null
                        && !"Hung_By_Son_Investigate".equals(now.getValue("MIDlet-Vendor")),
                "Candidate vendor identity is separate from Investigate");
        String midlet = now.getValue("MIDlet-1");
        require(midlet != null && midlet.startsWith("HSO CHIBI,"),
                "Displayed MIDlet entry uses HSO CHIBI");
    }

    private static Properties verifyBytecode(File candidate) throws Exception {
        JarFile jar = new JarFile(candidate);
        Properties result = new Properties();
        int rawOpens = 0, rawDeletes = 0, rawLists = 0;
        int autoRedirects = 0, sharedRedirects = 0, reflectiveNames = 0;
        int nativeWrapperOpens = 0, nativeWrapperLists = 0;
        try {
            ClassNode storage = classNode(jar, STORAGE);
            require(PREFIX.equals(stringField(storage, "PREFIX")),
                    "InvestigateStorage owns the HSO_CHIBI_ physical prefix");
            require("mc_inv_v1".equals(stringField(storage, "CACHE"))
                            && "ms_inv_v1".equals(stringField(storage, "SELECTION")),
                    "Native cache logical RMS names remain unchanged behind the wrapper");
            require(method(storage, "openRecordStore", "(Ljava/lang/String;Z)Ljavax/microedition/rms/RecordStore;") != null
                            && method(storage, "deleteRecordStore", "(Ljava/lang/String;)V") != null
                            && method(storage, "listRecordStores", "()[Ljava/lang/String;") != null,
                    "Wrapper keeps its open, delete, and filtered-list entry points");

            ClassNode attack = classNode(jar, AUTO);
            require("hbs_auto_attack_v1".equals(stringField(attack, "NAME")),
                    "Auto Attack logical name is preserved");
            ClassNode shared = classNode(jar, SHARED);
            require("hbs_exp_levels_v2".equals(stringField(shared, "STORE_NAME")),
                    "Shared EXP logical name is preserved");
            MethodNode migrate = method(shared, "migrateLegacy", "()V");
            require(realOpcodes(migrate).length == 1 && realOpcodes(migrate)[0] == RETURN,
                    "Fresh suite migration entry point returns without reading legacy data");

            boolean[] legacyNames = new boolean[] { false, false, false, false };
            String[] expectedLegacy = { "inv_exp_approx_v1", "ainv_exp_approx_v1",
                    "binv_exp_approx_v1", "cinv_exp_approx_v1" };
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                InputStream in = jar.getInputStream(entry);
                ClassNode node = new ClassNode();
                try { new ClassReader(in).accept(node, 0); }
                finally { in.close(); }
                for (MethodNode m : node.methods) for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof LdcInsnNode && ((LdcInsnNode)insn).cst instanceof String) {
                        String value = (String)((LdcInsnNode)insn).cst;
                        if (node.name.equals(SHARED)) for (int i = 0; i < expectedLegacy.length; i++)
                            if (expectedLegacy[i].equals(value)) legacyNames[i] = true;
                        if ("openRecordStore".equals(value) || "deleteRecordStore".equals(value)
                                || "listRecordStores".equals(value)) reflectiveNames++;
                    }
                    if (!(insn instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode)insn;
                    if (RMS.equals(call.owner) && call.getOpcode() == INVOKESTATIC) {
                        if ("openRecordStore".equals(call.name)) {
                            rawOpens++;
                            require(STORAGE.equals(node.name) && "openRecordStore".equals(m.name),
                                    "Only InvestigateStorage may call MIDP openRecordStore directly");
                        } else if ("deleteRecordStore".equals(call.name)) {
                            rawDeletes++;
                            require(STORAGE.equals(node.name) && "deleteRecordStore".equals(m.name),
                                    "Only InvestigateStorage may call MIDP deleteRecordStore directly");
                        } else if ("listRecordStores".equals(call.name)) {
                            rawLists++;
                            require(STORAGE.equals(node.name) && "listRecordStores".equals(m.name),
                                    "Only InvestigateStorage may call MIDP listRecordStores directly");
                        }
                    }
                    if (AUTO.equals(node.name) && STORAGE.equals(call.owner)
                            && "openRecordStore".equals(call.name)) autoRedirects++;
                    if (SHARED.equals(node.name) && STORAGE.equals(call.owner)
                            && "openRecordStore".equals(call.name)) sharedRedirects++;
                    if ("com/silverknight/TemMidlet".equals(node.name)
                            && STORAGE.equals(call.owner) && "openRecordStore".equals(call.name)) nativeWrapperOpens++;
                    if ("com/silverknight/TemMidlet".equals(node.name)
                            && STORAGE.equals(call.owner) && "listRecordStores".equals(call.name)) nativeWrapperLists++;
                }
            }
            for (int i = 0; i < legacyNames.length; i++)
                require(legacyNames[i], "Shared EXP retains logical legacy name " + expectedLegacy[i]);
            require(rawOpens == 1 && rawDeletes == 1 && rawLists == 1,
                    "All static RMS open/delete/list calls are centralized in the wrapper");
            require(autoRedirects >= 2 && sharedRedirects >= 2,
                    "Auto Attack and shared EXP read/write through InvestigateStorage");
            require(nativeWrapperOpens >= 2 && nativeWrapperLists >= 1,
                    "Native account read/write and cleanup listing remain routed through InvestigateStorage");
            require(reflectiveNames == 0, "No reflective raw RMS method-name bypass exists");
        } finally { jar.close(); }
        result.setProperty("rms.direct.open.count", String.valueOf(rawOpens));
        result.setProperty("rms.direct.delete.count", String.valueOf(rawDeletes));
        result.setProperty("rms.direct.list.count", String.valueOf(rawLists));
        result.setProperty("rms.auto.wrapper.calls", String.valueOf(autoRedirects));
        result.setProperty("rms.exp.wrapper.calls", String.valueOf(sharedRedirects));
        result.setProperty("rms.native.wrapper.open.calls", String.valueOf(nativeWrapperOpens));
        result.setProperty("rms.native.wrapper.list.calls", String.valueOf(nativeWrapperLists));
        result.setProperty("rms.reflective.raw.calls", String.valueOf(reflectiveNames));
        check(true, "Candidate storage bytecode has one scoped RMS boundary");
        return result;
    }

    private static void seedLegacyModRecords() throws Exception {
        seed("inv_sys_auto_cfg", new byte[] { 3, 5, 8, 13 });
        seed("inv_sys_farm_loc", new byte[] { 0x46, 0x4c, 0x4f, 0x43, 0, 7, 1 });
        seed("hbs_auto_attack_v1", new byte[] { 1, 1, 2, (byte)138 });
        seed("hbs_exp_levels_v2", versionTwo(8700, 500000000L, 500000000L, 20, 30));
        String[] legacyNames = { "inv_exp_approx_v1", "ainv_exp_approx_v1",
                "binv_exp_approx_v1", "cinv_exp_approx_v1" };
        for (int i = 0; i < legacyNames.length; i++)
            seed(legacyNames[i], versionOne("chibi-legacy-proof", 7700 + i,
                    8801 + i, 500000000L + i, 500000000L + i, 20, 30));
        seed("inv_fresh_probe", new byte[] { 10, 20, 30 });
        seed("inv_delete_probe", new byte[] { 40, 50, 60 });
        seed("inv_only_old_probe", new byte[] { 70, 80, 90 });
    }

    private static void seedNativeStore(File baseline, byte[] account) throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[] { baseline.toURI().toURL() },
                ChibiStorageBinaryTest.class.getClassLoader());
        try {
            Class<?> storage = Class.forName(STORAGE.replace('/', '.'), true, loader);
            Method open = storage.getMethod("openRecordStore", String.class, Boolean.TYPE);
            RecordStore store = (RecordStore)open.invoke(null, "native_fixture_probe", Boolean.TRUE);
            try { store.addRecord(account, 0, account.length); }
            finally { store.closeRecordStore(); }
        } finally { loader.close(); }
    }

    private static void verifyOldPayloadsAreValid(ClassLoader loader) throws Exception {
        Class<?> support = Class.forName(SUPPORT.replace('/', '.'), true, loader);
        Method merge = support.getMethod("mergeStored", byte[].class, byte[].class);
        String[] names = { "hbs_exp_levels_v2", "inv_exp_approx_v1", "ainv_exp_approx_v1",
                "binv_exp_approx_v1", "cinv_exp_approx_v1" };
        int[] levels = { 8700, 8801, 8802, 8803, 8804 };
        for (int i = 0; i < names.length; i++) {
            byte[] raw = OLD_RECORDS.get(names[i]);
            byte[] decoded = (byte[])merge.invoke(null, new Object[] { null, raw });
            require(decoded != null && containsLevel(decoded, levels[i]),
                    "Seeded old EXP fixture is valid and contains level " + levels[i]);
        }
    }

    private static void verifyNativeStoreStartsFresh(ClassLoader loader, byte[] replacement) throws Exception {
        Class<?> storage = Class.forName(STORAGE.replace('/', '.'), true, loader);
        Method open = storage.getMethod("openRecordStore", String.class, Boolean.TYPE);
        expectMissing(open, "native_fixture_probe");
        require(Arrays.equals(OLD_RECORDS.get("inv_native_fixture_probe"), readRecord("inv_native_fixture_probe")),
                "Candidate native read leaves old native bytes unchanged");
        RecordStore fresh = (RecordStore)open.invoke(null, "native_fixture_probe", Boolean.TRUE);
        try { fresh.addRecord(replacement, 0, replacement.length); }
        finally { fresh.closeRecordStore(); }
        require(Arrays.equals(replacement, readRecord(PREFIX + "native_fixture_probe")),
                "Candidate native wrapper writes the logical record under HSO_CHIBI_");
        require(Arrays.equals(OLD_RECORDS.get("inv_native_fixture_probe"), readRecord("inv_native_fixture_probe")),
                "Candidate native account save does not overwrite the old store");
    }

    private static void verifyWrapperIsolation(ClassLoader loader) throws Exception {
        Class<?> storage = Class.forName(STORAGE.replace('/', '.'), true, loader);
        Method open = storage.getMethod("openRecordStore", String.class, Boolean.TYPE);
        Method delete = storage.getMethod("deleteRecordStore", String.class);
        Method list = storage.getMethod("listRecordStores");

        expectMissing(open, "fresh_probe");
        RecordStore old = RecordStore.openRecordStore("inv_fresh_probe", false);
        try { require(Arrays.equals(new byte[] { 10, 20, 30 }, old.getRecord(1)),
                "Old native probe remains readable outside the candidate wrapper"); }
        finally { old.closeRecordStore(); }

        RecordStore fresh = (RecordStore)open.invoke(null, "scope_probe", Boolean.TRUE);
        try { fresh.addRecord(new byte[] { 91, 92 }, 0, 2); }
        finally { fresh.closeRecordStore(); }
        require(Arrays.equals(new byte[] { 91, 92 }, readRecord(PREFIX + "scope_probe")),
                "Wrapper open creates exactly one HSO_CHIBI_ scoped physical name");
        require(!exists(PREFIX + PREFIX + "scope_probe"), "Wrapper does not duplicate its prefix");

        RecordStore alreadyScoped = (RecordStore)open.invoke(null, PREFIX + "already_scoped", Boolean.TRUE);
        try { alreadyScoped.addRecord(new byte[] { 3 }, 0, 1); }
        finally { alreadyScoped.closeRecordStore(); }
        require(exists(PREFIX + "already_scoped") && !exists(PREFIX + PREFIX + "already_scoped"),
                "Wrapper preserves idempotent prefixing for an already scoped name");

        RecordStore deleteTarget = (RecordStore)open.invoke(null, "delete_probe", Boolean.TRUE);
        try { deleteTarget.addRecord(new byte[] { 1, 2, 3 }, 0, 3); }
        finally { deleteTarget.closeRecordStore(); }
        delete.invoke(null, "delete_probe");
        require(!exists(PREFIX + "delete_probe"), "Wrapper delete removes its own scoped store");
        require(Arrays.equals(OLD_RECORDS.get("inv_delete_probe"), readRecord("inv_delete_probe")),
                "Wrapper delete leaves the old native store untouched");

        boolean absent = false;
        try { delete.invoke(null, "only_old_probe"); }
        catch (InvocationTargetException expected) {
            absent = expected.getCause() instanceof RecordStoreNotFoundException;
        }
        require(absent && Arrays.equals(OLD_RECORDS.get("inv_only_old_probe"),
                        readRecord("inv_only_old_probe")),
                "Deleting a logical name that exists only in old RMS cannot delete the old record");
        String[] logical = (String[])list.invoke(null);
        require(has(logical, "scope_probe") && has(logical, "already_scoped")
                        && !has(logical, "inv_fresh_probe") && !has(logical, "delete_probe"),
                "Wrapper list exposes only scoped logical names and excludes old physical stores");
    }

    private static void verifyAutoAttackIsolation(ClassLoader loader, int[] saved) throws Exception {
        Class<?> type = Class.forName(AUTO.replace('/', '.'), true, loader);
        Object store = type.newInstance();
        Method load = type.getMethod("load", int[].class);
        Method save = type.getMethod("save", Boolean.TYPE, Integer.TYPE);
        int[] settings = new int[] { 9, 9 };
        load.invoke(store, (Object)settings);
        require(settings[0] == 0 && settings[1] == 650,
                "Auto Attack starts at defaults rather than importing the old hbs_auto_attack_v1 record");
        require(Arrays.equals(OLD_RECORDS.get("hbs_auto_attack_v1"), readRecord("hbs_auto_attack_v1")),
                "Auto Attack default load leaves its old direct RMS record unchanged");
        save.invoke(store, Boolean.valueOf(saved[0] != 0), Integer.valueOf(saved[1]));
        require(Arrays.equals(new byte[] { 1, 1, 2, (byte)138 },
                        readRecord(PREFIX + "hbs_auto_attack_v1")),
                "Auto Attack writes its unchanged logical record beneath HSO_CHIBI_");
        settings[0] = 0; settings[1] = 0;
        load.invoke(store, (Object)settings);
        require(settings[0] == saved[0] && settings[1] == saved[1],
                "Auto Attack reloads the new suite's saved mode and delay");
        require(Arrays.equals(OLD_RECORDS.get("hbs_auto_attack_v1"), readRecord("hbs_auto_attack_v1")),
                "Auto Attack save never changes the old suite's record");
    }

    private static void verifySharedExpIsolation(ClassLoader loader, byte[] freshExp,
            int newLevel) throws Exception {
        Class<?> shared = Class.forName(SHARED.replace('/', '.'), true, loader);
        Method load = shared.getMethod("load");
        Method save = shared.getMethod("save", byte[].class);
        require(load.invoke(null) == null,
                "Shared EXP load returns empty for a new suite despite valid old current and legacy payloads");
        require(!exists(PREFIX + "hbs_exp_levels_v2"),
                "Read-only fresh EXP load does not create a new table as a side effect");
        require(Arrays.equals(OLD_RECORDS.get("hbs_exp_levels_v2"), readRecord("hbs_exp_levels_v2")),
                "Fresh EXP load leaves old shared v2 table byte-for-byte unchanged");
        for (String name : legacyNames()) require(Arrays.equals(OLD_RECORDS.get(name), readRecord(name)),
                "Fresh EXP load does not rewrite valid old legacy payload " + name);

        require(((Boolean)save.invoke(null, new Object[] { freshExp })).booleanValue(),
                "Fresh suite accepts a valid new EXP level update");
        byte[] written = readRecord(PREFIX + "hbs_exp_levels_v2");
        require(containsLevel(written, newLevel), "New EXP update is written under the suite prefix");
        int[] oldLevels = { 8700, 8801, 8802, 8803, 8804 };
        for (int oldLevel : oldLevels) require(!containsLevel(written, oldLevel),
                "New suite EXP table contains no migrated old level " + oldLevel);
    }

    private static void verifyFreshLoaderPersistence(ClassLoader loader, byte[] account,
            int[] attack, int newExpLevel) throws Exception {
        Class<?> storage = Class.forName(STORAGE.replace('/', '.'), true, loader);
        Method open = storage.getMethod("openRecordStore", String.class, Boolean.TYPE);
        RecordStore nativeStore = (RecordStore)open.invoke(null, "native_fixture_probe", Boolean.FALSE);
        try { require(Arrays.equals(account, nativeStore.getRecord(1)),
                "Fresh loader reads its own native record through the scoped wrapper"); }
        finally { nativeStore.closeRecordStore(); }

        Class<?> auto = Class.forName(AUTO.replace('/', '.'), true, loader);
        Object autoStore = auto.newInstance();
        int[] settings = new int[2];
        auto.getMethod("load", int[].class).invoke(autoStore, (Object)settings);
        require(settings[0] == attack[0] && settings[1] == attack[1],
                "Fresh loader reads its own Auto Attack settings");

        Class<?> shared = Class.forName(SHARED.replace('/', '.'), true, loader);
        byte[] levels = (byte[])shared.getMethod("load").invoke(null);
        require(containsLevel(levels, newExpLevel), "Fresh loader reads its own shared EXP level table");
        Class<?> adapter = Class.forName("luna.generated.ExpEstimateRmsStore", true, loader);
        byte[] throughAdapter = (byte[])adapter.getMethod("load").invoke(adapter.newInstance());
        require(containsLevel(throughAdapter, newExpLevel),
                "Fresh loader's feature-facing EXP adapter reaches the shared scoped store");
    }

    private static void verifyVisibleNames(ClassLoader loader) throws Exception {
        Class<?> storage = Class.forName(STORAGE.replace('/', '.'), true, loader);
        String[] visible = (String[])storage.getMethod("listRecordStores").invoke(null);
        require(has(visible, "native_fixture_probe") && has(visible, "hbs_auto_attack_v1")
                        && has(visible, "hbs_exp_levels_v2"),
                "Fresh loader lists its own native, Auto Attack, and EXP stores");
        require(!has(visible, "inv_native_fixture_probe") && !has(visible, "inv_sys_auto_cfg")
                        && !has(visible, "inv_sys_farm_loc")
                        && !has(visible, "inv_exp_approx_v1")
                        && !has(visible, "ainv_exp_approx_v1"),
                "Fresh loader list cannot expose native or legacy stores from the old suite");
    }

    private static void verifyOldRecordsUntouched() throws Exception {
        for (Map.Entry<String, byte[]> entry : OLD_RECORDS.entrySet())
            require(Arrays.equals(entry.getValue(), readRecord(entry.getKey())),
                    "Old physical record remains byte-for-byte unchanged: " + entry.getKey());
    }

    private static void verifyCandidateCreatedNames() throws Exception {
        String[] names = RecordStore.listRecordStores();
        require(names != null, "Candidate operations created suite-scoped RMS records");
        for (String name : names) {
            if (OLD_RECORDS.containsKey(name)) continue;
            require(name.startsWith(PREFIX), "New physical store uses HSO_CHIBI_ prefix: " + name);
        }
    }

    private static void expectMissing(Method open, String name) throws Exception {
        boolean missing = false;
        try { open.invoke(null, name, Boolean.FALSE); }
        catch (InvocationTargetException expected) {
            missing = expected.getCause() instanceof RecordStoreNotFoundException;
        }
        require(missing, "Scoped wrapper does not fall through to old store " + name);
    }

    private static String[] legacyNames() {
        return new String[] { "inv_exp_approx_v1", "ainv_exp_approx_v1",
                "binv_exp_approx_v1", "cinv_exp_approx_v1" };
    }

    private static void seed(String name, byte[] data) throws Exception {
        writeRecord(name, data);
        OLD_RECORDS.put(name, readRecord(name));
    }

    private static void writeRecord(String name, byte[] data) throws Exception {
        RecordStore store = RecordStore.openRecordStore(name, true);
        try {
            if (store.getNumRecords() == 0) store.addRecord(data, 0, data.length);
            else store.setRecord(1, data, 0, data.length);
        } finally { store.closeRecordStore(); }
    }

    private static byte[] readRecord(String name) throws Exception {
        RecordStore store = RecordStore.openRecordStore(name, false);
        try { return store.getRecord(1); }
        finally { store.closeRecordStore(); }
    }

    private static boolean exists(String name) {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(name, false);
            return true;
        } catch (RecordStoreNotFoundException missing) { return false; }
        catch (Exception failure) { throw new IllegalStateException(failure); }
        finally { if (store != null) try { store.closeRecordStore(); } catch (Exception ignored) { } }
    }

    private static byte[] versionOne(String server, int id, int level,
            long low, long high, int samples, int span) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x45585031); out.writeByte(1); out.writeByte(1); out.writeUTF(server);
        out.writeInt(id); out.writeInt(level); out.writeLong(low); out.writeLong(high);
        out.writeShort(samples); out.writeShort(span); out.flush();
        return bytes.toByteArray();
    }

    private static byte[] versionTwo(int level, long low, long high,
            int samples, int span) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x45585031); out.writeByte(2); out.writeByte(1); out.writeInt(level);
        out.writeLong(low); out.writeLong(high); out.writeShort(samples); out.writeShort(span);
        out.flush();
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

    private static boolean has(String[] values, String expected) {
        if (values != null) for (String value : values) if (expected.equals(value)) return true;
        return false;
    }

    private static Manifest manifest(File file) throws Exception {
        JarFile jar = new JarFile(file);
        try { return jar.getManifest(); }
        finally { jar.close(); }
    }

    private static ClassNode classNode(JarFile jar, String name) throws Exception {
        JarEntry entry = jar.getJarEntry(name + ".class");
        require(entry != null, "Candidate contains " + name);
        InputStream in = jar.getInputStream(entry);
        ClassNode node = new ClassNode();
        try { new ClassReader(in).accept(node, 0); }
        finally { in.close(); }
        return node;
    }

    private static String stringField(ClassNode node, String name) {
        for (FieldNode field : node.fields)
            if (name.equals(field.name) && field.value instanceof String) return (String)field.value;
        throw new IllegalStateException("Missing string field " + node.name + "." + name);
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods)
            if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        return null;
    }

    private static int[] realOpcodes(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray())
            if (instruction.getOpcode() >= 0) count++;
        int[] result = new int[count]; int index = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray())
            if (instruction.getOpcode() >= 0) result[index++] = instruction.getOpcode();
        return result;
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(file);
        try { byte[] buffer = new byte[8192]; for (int n; (n = in.read(buffer)) >= 0;) digest.update(buffer, 0, n); }
        finally { in.close(); }
        StringBuilder out = new StringBuilder();
        for (byte value : digest.digest()) out.append(String.format("%02X", value & 255));
        return out.toString();
    }

    private static void check(boolean okay, String message) {
        checks++;
        if (!okay) throw new IllegalStateException(message);
    }

    private static void require(boolean okay, String message) { check(okay, message); }
}
