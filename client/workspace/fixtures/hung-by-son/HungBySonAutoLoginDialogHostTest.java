import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Offline behavioral regression for the native Auto Login modal fallback. */
public final class HungBySonAutoLoginDialogHostTest implements Opcodes {
    static final String AUTO = HungBySonAutoLoginDialogPatcher.AUTO;
    static final String ENGINE = HungBySonAutoLoginDialogPatcher.ENGINE;
    static final String DIALOG = HungBySonAutoLoginDialogPatcher.DIALOG;
    static final String LOGIN = HungBySonAutoLoginDialogPatcher.LOGIN;
    static final String TABS = HungBySonAutoLoginDialogPatcher.TABS;
    static final String CONNECTION = HungBySonAutoLoginDialogPatcher.CONNECTION;
    static final String MENU = HungBySonAutoLoginDialogPatcher.n("lillllll");
    static final String MENU_PRECHECK = HungBySonAutoLoginDialogPatcher.n("lilll");
    static final String LAST_NOTICE = HungBySonAutoLoginDialogPatcher.n("liliililill");
    static final String MENU_BUTTONS = HungBySonAutoLoginDialogPatcher.n("llliii");
    static final String BUTTON_LABEL = HungBySonAutoLoginDialogPatcher.n("lliiilli");
    static final String EVENT_OWNER = Type.getInternalName(HungBySonAutoLoginDialogHostTest.class);

    static sun.misc.Unsafe unsafe;
    static int checks;
    static int dialogActions;
    static int loginShows;
    static int buttonActions;
    static int loginAuthActions;
    static int selectAuthActions;
    static final List<String> events = new ArrayList<String>();

    static String n(String suffix) { return HungBySonAutoLoginDialogPatcher.n(suffix); }
    static void require(boolean okay, String message) {
        checks++;
        if (!okay) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }
    static void eq(String name, Object actual, Object expected) {
        require(Objects.equals(actual, expected), name + " expected=" + expected + " actual=" + actual);
    }
    static byte[] read(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] block = new byte[8192];
            for (int size; (size = input.read(block)) != -1;) out.write(block, 0, size);
            return out.toByteArray();
        } finally { input.close(); }
    }
    static byte[] entry(JarFile jar, String name) throws IOException {
        JarEntry item = jar.getJarEntry(name);
        if (item == null) return null;
        return read(jar.getInputStream(item));
    }
    static ClassNode node(byte[] bytes) {
        ClassNode result = new ClassNode(ASM9);
        new ClassReader(bytes).accept(result, 0);
        return result;
    }
    static String methodCode(MethodNode method) {
        IdentityHashMap<LabelNode, Integer> labels = new IdentityHashMap<LabelNode, Integer>();
        for (AbstractInsnNode instruction : method.instructions.toArray())
            if (instruction instanceof LabelNode) labels.put((LabelNode)instruction, Integer.valueOf(labels.size()));
        StringBuilder out = new StringBuilder(method.name).append(method.desc).append('#').append(method.access).append('|');
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof LabelNode) {
                out.append('L').append(labels.get((LabelNode)instruction)).append(';');
                continue;
            }
            int opcode = instruction.getOpcode();
            if (opcode < 0) continue;
            out.append(opcode).append(':');
            if (instruction instanceof IntInsnNode) out.append(((IntInsnNode)instruction).operand);
            else if (instruction instanceof VarInsnNode) out.append(((VarInsnNode)instruction).var);
            else if (instruction instanceof TypeInsnNode) out.append(((TypeInsnNode)instruction).desc);
            else if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                out.append(field.owner).append('.').append(field.name).append(field.desc);
            } else if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                out.append(call.owner).append('.').append(call.name).append(call.desc).append(call.itf);
            } else if (instruction instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode call = (InvokeDynamicInsnNode)instruction;
                out.append(call.name).append(call.desc).append(call.bsm).append(Arrays.deepToString(call.bsmArgs));
            } else if (instruction instanceof JumpInsnNode) out.append(labels.get(((JumpInsnNode)instruction).label));
            else if (instruction instanceof LdcInsnNode) out.append(String.valueOf(((LdcInsnNode)instruction).cst));
            else if (instruction instanceof IincInsnNode) {
                IincInsnNode increment = (IincInsnNode)instruction;
                out.append(increment.var).append(':').append(increment.incr);
            } else if (instruction instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode table = (TableSwitchInsnNode)instruction;
                out.append(table.min).append(':').append(table.max).append(':').append(labels.get(table.dflt));
                for (LabelNode label : table.labels) out.append(':').append(labels.get(label));
            } else if (instruction instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode lookup = (LookupSwitchInsnNode)instruction;
                out.append(labels.get(lookup.dflt)).append(lookup.keys);
                for (LabelNode label : lookup.labels) out.append(':').append(labels.get(label));
            } else if (instruction instanceof MultiANewArrayInsnNode) {
                MultiANewArrayInsnNode array = (MultiANewArrayInsnNode)instruction;
                out.append(array.desc).append(':').append(array.dims);
            }
            out.append(';');
        }
        for (TryCatchBlockNode trap : method.tryCatchBlocks)
            out.append("try:").append(labels.get(trap.start)).append(':').append(labels.get(trap.end)).append(':')
                    .append(labels.get(trap.handler)).append(':').append(trap.type).append(';');
        return out.toString();
    }
    static void compareUnchangedAutoMethods(byte[] before, byte[] candidate) {
        ClassNode left = node(before), right = node(candidate);
        Map<String, String> oldMethods = new TreeMap<String, String>(), newMethods = new TreeMap<String, String>();
        List<String> oldFields = new ArrayList<String>(), newFields = new ArrayList<String>();
        for (MethodNode method : left.methods)
            if (!method.name.equals(HungBySonAutoLoginDialogPatcher.TICK)) oldMethods.put(method.name + method.desc, methodCode(method));
        for (MethodNode method : right.methods)
            if (!method.name.equals(HungBySonAutoLoginDialogPatcher.TICK)) newMethods.put(method.name + method.desc, methodCode(method));
        for (FieldNode field : left.fields) oldFields.add(field.name + field.desc + '#' + field.access + '#' + field.signature + '#' + field.value);
        for (FieldNode field : right.fields) newFields.add(field.name + field.desc + '#' + field.access + '#' + field.signature + '#' + field.value);
        Collections.sort(oldFields);
        Collections.sort(newFields);
        require(oldMethods.equals(newMethods), "all Auto Login methods except tick retain identical bytecode");
        require(oldFields.equals(newFields), "all Auto Login fields retain their names and descriptors");
    }
    static String hash(byte[] value) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte item : MessageDigest.getInstance("SHA-256").digest(value))
            result.append(String.format("%02X", item & 255));
        return result.toString();
    }
    static void compareArchives(File before, File candidate) throws Exception {
        try (JarFile left = new JarFile(before); JarFile right = new JarFile(candidate)) {
            Map<String, byte[]> a = new TreeMap<String, byte[]>(), b = new TreeMap<String, byte[]>();
            for (Enumeration<JarEntry> it = left.entries(); it.hasMoreElements();) {
                JarEntry e = it.nextElement(); a.put(e.getName(), entry(left, e.getName()));
            }
            for (Enumeration<JarEntry> it = right.entries(); it.hasMoreElements();) {
                JarEntry e = it.nextElement(); b.put(e.getName(), entry(right, e.getName()));
            }
            require(a.keySet().equals(b.keySet()), "candidate keeps the same archive entries");
            int changed = 0;
            for (String name : a.keySet()) if (!Arrays.equals(a.get(name), b.get(name))) {
                changed++;
                require(name.equals(AUTO + ".class"), "only native Auto Login bytes change (" + name + ")");
            }
            eq("one changed class", changed, 1);
            require(!Arrays.equals(a.get(AUTO + ".class"), b.get(AUTO + ".class")), "candidate changes the Auto Login class");
            compareUnchangedAutoMethods(a.get(AUTO + ".class"), b.get(AUTO + ".class"));
            eq("native connection accessor bytes unchanged", hash(a.get(CONNECTION + ".class")), hash(b.get(CONNECTION + ".class")));
        }
    }

    static void compareCurrentAutoLoginClass(File before, File candidate) throws Exception {
        try (JarFile left = new JarFile(before); JarFile right = new JarFile(candidate)) {
            byte[] oldAuto = entry(left, AUTO + ".class");
            byte[] newAuto = entry(right, AUTO + ".class");
            require(oldAuto != null && newAuto != null, "current input and candidate retain the native Auto Login class");
            require(Arrays.equals(oldAuto, newAuto), "Auto Attack candidate leaves the current Auto Login class byte-identical");
            compareUnchangedAutoMethods(oldAuto, newAuto);
            eq("native connection accessor bytes unchanged", hash(entry(left, CONNECTION + ".class")),
                    hash(entry(right, CONNECTION + ".class")));
        }
    }

    static void resetEvents() {
        dialogActions = 0;
        loginShows = 0;
        buttonActions = 0;
        loginAuthActions = 0;
        selectAuthActions = 0;
        events.clear();
    }
    public static void nativeDialogAction(Object dialog, int first, int second) {
        dialogActions++;
        events.add("dialog:" + first + ":" + second);
    }
    public static void nativeLoginShown(Object login) throws Exception {
        loginShows++;
        events.add("login-show");
        ClassLoader loader = login == null ? Thread.currentThread().getContextClassLoader() : login.getClass().getClassLoader();
        Field current = field(loader, ENGINE, HungBySonAutoLoginDialogPatcher.CURRENT_SCREEN);
        Object screen = field(loader, ENGINE, HungBySonAutoLoginDialogPatcher.LOGIN_SCREEN).get(null);
        current.set(null, screen);
    }
    public static void nativeButtonAction(Object button) {
        buttonActions++;
        events.add("button:" + String.valueOf(safeStringField(button, BUTTON_LABEL)));
    }
    public static void nativeLoginAuth(Object screen, int first, int second) {
        loginAuthActions++;
        events.add("auth-login:" + first + ":" + second);
    }
    public static void nativeSelectAuth(Object screen, int first, int second) {
        selectAuthActions++;
        events.add("auth-select:" + first + ":" + second);
    }
    static String safeStringField(Object value, String name) {
        try { return String.valueOf(field(value.getClass(), name).get(value)); }
        catch (Exception ignored) { return "?"; }
    }

    static Field field(ClassLoader loader, String owner, String name) throws Exception {
        return field(Class.forName(owner, true, loader), name);
    }
    static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field result = current.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    static void put(ClassLoader loader, String owner, String name, Object value) throws Exception {
        field(loader, owner, name).set(null, value);
    }
    static Object get(ClassLoader loader, String owner, String name) throws Exception {
        return field(loader, owner, name).get(null);
    }
    static Object alloc(ClassLoader loader, String name) throws Exception {
        return unsafe.allocateInstance(Class.forName(name, true, loader));
    }
    static Object allocFieldType(ClassLoader loader, String owner, String name) throws Exception {
        return unsafe.allocateInstance(field(loader, owner, name).getType());
    }
    static Method method(ClassLoader loader, String owner, String name, Class<?>... parameters) throws Exception {
        Class<?> type = Class.forName(owner, true, loader);
        Method result = type.getDeclaredMethod(name, parameters);
        result.setAccessible(true);
        return result;
    }
    static Object newMenu(ClassLoader loader, String label) throws Exception {
        Object menu = Class.forName("et", true, loader).getConstructor().newInstance();
        Object button = alloc(loader, "bt");
        field(button.getClass(), BUTTON_LABEL).set(button, label);
        method(loader, "et", n("l"), Object.class).invoke(menu, button);
        return menu;
    }
    static void preflight(ClassLoader loader, String text) throws Exception {
        method(loader, MENU, MENU_PRECHECK, String.class).invoke(null, text);
    }

    static final class IsolatedLoader extends URLClassLoader {
        IsolatedLoader(File release, File microemulator) throws Exception {
            super(new URL[]{release.toURI().toURL(), microemulator.toURI().toURL()},
                    HungBySonAutoLoginDialogHostTest.class.getClassLoader());
        }
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resource = name.replace('.', '/') + ".class";
            URL source = findResource(resource);
            if (source == null) throw new ClassNotFoundException(name);
            try {
                byte[] original = read(source.openStream());
                byte[] transformed = transform(original);
                return defineClass(name, transformed, 0, transformed.length);
            } catch (Throwable failure) {
                throw new ClassNotFoundException(name, failure);
            }
        }
        byte[] transform(byte[] original) {
            ClassNode type = new ClassNode(ASM9);
            new ClassReader(original).accept(type, 0);
            boolean utilityVector = type.name.equals("et");
            for (MethodNode method : type.methods) {
                if (method.name.equals("<clinit>")) {
                    method.instructions.clear();
                    method.tryCatchBlocks.clear();
                    method.localVariables = null;
                    method.instructions.add(new InsnNode(RETURN));
                    continue;
                }
                if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0 || method.name.equals("<init>")) continue;
                if (utilityVector) continue;
                if (type.name.equals(AUTO)) continue;
                if (type.name.equals(DIALOG) && method.name.equals("b") && method.desc.equals("(II)V")) {
                    InsnList tap = new InsnList();
                    tap.add(new VarInsnNode(ALOAD, 0));
                    tap.add(new VarInsnNode(ILOAD, 1));
                    tap.add(new VarInsnNode(ILOAD, 2));
                    tap.add(new MethodInsnNode(INVOKESTATIC, EVENT_OWNER, "nativeDialogAction", "(Ljava/lang/Object;II)V", false));
                    method.instructions.insert(tap);
                    continue;
                }
                if (type.name.equals(LOGIN) && method.name.equals("b") && method.desc.equals("(II)V")) {
                    mockAuth(method, "nativeLoginAuth");
                    continue;
                }
                if (type.name.equals(HungBySonAutoLoginDialogPatcher.SELECT)
                        && method.name.equals("b") && method.desc.equals("(II)V")) {
                    mockAuth(method, "nativeSelectAuth");
                    continue;
                }
                if (type.name.equals(LOGIN) && method.name.equals(HungBySonAutoLoginDialogPatcher.NATIVE_SHOW)
                        && Type.getReturnType(method.desc).getSort() == Type.VOID
                        && Type.getArgumentTypes(method.desc).length == 0) {
                    method.instructions.clear();
                    method.tryCatchBlocks.clear();
                    method.localVariables = null;
                    if ((method.access & ACC_STATIC) == 0) method.instructions.add(new VarInsnNode(ALOAD, 0));
                    else method.instructions.add(new InsnNode(ACONST_NULL));
                    method.instructions.add(new MethodInsnNode(INVOKESTATIC, EVENT_OWNER, "nativeLoginShown", "(Ljava/lang/Object;)V", false));
                    method.instructions.add(new InsnNode(RETURN));
                    continue;
                }
                if (type.name.equals("bt") && method.name.equals("a") && method.desc.equals("()V")) {
                    InsnList tap = new InsnList();
                    tap.add(new VarInsnNode(ALOAD, 0));
                    tap.add(new MethodInsnNode(INVOKESTATIC, EVENT_OWNER, "nativeButtonAction", "(Ljava/lang/Object;)V", false));
                    method.instructions.insert(tap);
                    continue;
                }
                if (type.name.equals(CONNECTION) && method.name.equals(HungBySonAutoLoginDialogPatcher.CONNECTED_GETTER)
                        && method.desc.equals("()Z")) continue;
                if (type.name.equals(HungBySonAutoLoginDialogPatcher.NORMALIZER)
                        && method.name.equals(HungBySonAutoLoginDialogPatcher.NORMALIZE)
                        && method.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) continue;
                if (type.name.equals(MENU) && method.name.equals(MENU_PRECHECK)
                        && method.desc.equals("(Ljava/lang/String;)Z")) continue;
                if (type.name.equals("key") && method.name.equals("d") && method.desc.equals("([B)Ljava/lang/String;")) continue;
                stub(method);
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            type.accept(writer);
            return writer.toByteArray();
        }
        static void stub(MethodNode method) {
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            method.localVariables = null;
            Type result = Type.getReturnType(method.desc);
            switch (result.getSort()) {
                case Type.VOID: method.instructions.add(new InsnNode(RETURN)); break;
                case Type.LONG: method.instructions.add(new InsnNode(LCONST_0)); method.instructions.add(new InsnNode(LRETURN)); break;
                case Type.FLOAT: method.instructions.add(new InsnNode(FCONST_0)); method.instructions.add(new InsnNode(FRETURN)); break;
                case Type.DOUBLE: method.instructions.add(new InsnNode(DCONST_0)); method.instructions.add(new InsnNode(DRETURN)); break;
                case Type.ARRAY:
                case Type.OBJECT: method.instructions.add(new InsnNode(ACONST_NULL)); method.instructions.add(new InsnNode(ARETURN)); break;
                default: method.instructions.add(new InsnNode(ICONST_0)); method.instructions.add(new InsnNode(IRETURN)); break;
            }
        }
        static void mockAuth(MethodNode method, String tap) {
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            method.localVariables = null;
            method.instructions.add(new VarInsnNode(ALOAD, 0));
            method.instructions.add(new VarInsnNode(ILOAD, 1));
            method.instructions.add(new VarInsnNode(ILOAD, 2));
            method.instructions.add(new MethodInsnNode(INVOKESTATIC, EVENT_OWNER, tap, "(Ljava/lang/Object;II)V", false));
            method.instructions.add(new InsnNode(RETURN));
        }
    }

    static final class Result {
        int dialogs, logins, buttons, counter, attempts;
        int authLogins, authSelects;
        long deadline;
        boolean connected;
        String screen;
        List<String> events;
    }
    static Result run(File jar, File microemulator, String scenario, int ticks) throws Exception {
        resetEvents();
        try (IsolatedLoader loader = new IsolatedLoader(jar, microemulator)) {
            Class<?> auto = Class.forName(AUTO, true, loader);
            Class<?> engine = Class.forName(ENGINE, true, loader);
            Object tabs = alloc(loader, TABS);
            require(Class.forName(HungBySonAutoLoginDialogPatcher.SCREEN_BASE, true, loader).isInstance(tabs),
                    "active inventory screen uses the native TabScreenNew wrapper");
            Object game = allocFieldType(loader, ENGINE, HungBySonAutoLoginDialogPatcher.GAME_SCREEN);
            Object login = allocFieldType(loader, ENGINE, HungBySonAutoLoginDialogPatcher.LOGIN_SCREEN);
            Object loading = allocFieldType(loader, ENGINE, HungBySonAutoLoginDialogPatcher.LOADING_SCREEN);
            Object selectScreen = allocFieldType(loader, ENGINE, HungBySonAutoLoginDialogPatcher.SELECT_SCREEN);
            Object nativeDialog = alloc(loader, DIALOG);
            Object connection = alloc(loader, CONNECTION);

            put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.GAME_SCREEN, game);
            put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.LOGIN_SCREEN, login);
            put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.LOADING_SCREEN, loading);
            put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.SELECT_SCREEN, selectScreen);
            put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.CURRENT_SCREEN, tabs);
            put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.ACTIVE_DIALOG, nativeDialog);
            field(loader, DIALOG, HungBySonAutoLoginDialogPatcher.DIALOG_LINES).set(nativeDialog, new String[]{"Use this item?"});
            field(loader, DIALOG, MENU_BUTTONS).set(nativeDialog, newMenu(loader, "Cancel"));

            put(loader, CONNECTION, HungBySonAutoLoginDialogPatcher.CONNECTION_INSTANCE, connection);
            field(loader, CONNECTION, HungBySonAutoLoginDialogPatcher.CONNECTED_FIELD).setBoolean(connection, true);
            put(loader, AUTO, HungBySonAutoLoginDialogPatcher.AUTO_ENABLED, Boolean.TRUE);
            put(loader, AUTO, HungBySonAutoLoginDialogPatcher.SCAN_COUNTER, Integer.valueOf(0));
            put(loader, AUTO, HungBySonAutoLoginDialogPatcher.RETRY_ATTEMPTS, Integer.valueOf(0));
            put(loader, AUTO, HungBySonAutoLoginDialogPatcher.RETRY_DEADLINE, Long.valueOf(0L));
            put(loader, AUTO, HungBySonAutoLoginDialogPatcher.CAPTURE_USER, null);
            put(loader, HungBySonAutoLoginDialogPatcher.COMBAT, HungBySonAutoLoginDialogPatcher.COMBAT_ENABLED, Boolean.TRUE);
            put(loader, HungBySonAutoLoginDialogPatcher.FARM, HungBySonAutoLoginDialogPatcher.FARM_ENABLED, Boolean.TRUE);
            put(loader, HungBySonAutoLoginDialogPatcher.SELECT, HungBySonAutoLoginDialogPatcher.WORLD_ENTERED, Boolean.TRUE);
            put(loader, HungBySonAutoLoginDialogPatcher.WAIT_OWNER, HungBySonAutoLoginDialogPatcher.WAIT_FLAG, Boolean.FALSE);

            if (scenario.equals("generic") || scenario.equals("item-plus10")) {
                // This is the real menu preflight used by the native dialog factory; it replaces
                // stale message text before the generic inventory confirmation becomes active.
                String prompt = scenario.equals("item-plus10") ? "+10" : "Use this item?";
                field(loader, DIALOG, HungBySonAutoLoginDialogPatcher.DIALOG_LINES).set(nativeDialog, new String[]{prompt});
                put(loader, MENU, LAST_NOTICE, "mat ket noi");
                preflight(loader, prompt);
                eq(scenario + " preflight refreshes stale loss cache", get(loader, MENU, LAST_NOTICE), prompt.toLowerCase(Locale.ROOT));
            } else if (scenario.equals("connected-loss") || scenario.equals("disconnected-loss")) {
                field(loader, DIALOG, HungBySonAutoLoginDialogPatcher.DIALOG_LINES).set(nativeDialog, new String[]{"mat ket noi"});
                preflight(loader, "mat ket noi");
                put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.CURRENT_SCREEN, game);
                field(loader, CONNECTION, HungBySonAutoLoginDialogPatcher.CONNECTED_FIELD).setBoolean(connection, scenario.equals("connected-loss"));
            } else if (scenario.equals("cached-loss")) {
                put(loader, MENU, LAST_NOTICE, "mat ket noi");
            } else if (scenario.equals("native-popup")) {
                field(loader, DIALOG, HungBySonAutoLoginDialogPatcher.DIALOG_LINES).set(nativeDialog, new String[]{"nguyen lieu me day"});
                field(loader, DIALOG, MENU_BUTTONS).set(nativeDialog, newMenu(loader, "OK"));
                preflight(loader, "nguyen lieu me day");
                put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.CURRENT_SCREEN, game);
                boolean recognized = (Boolean)method(loader, AUTO, n("lliillil")).invoke(null);
                require(recognized, "native post-world popup is recognized by the original helper");
            } else if (scenario.equals("game-screen")) {
                put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.CURRENT_SCREEN, game);
            } else if (scenario.equals("login-retry")) {
                put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.CURRENT_SCREEN, login);
                put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.ACTIVE_DIALOG, null);
                Object usernameInput = allocFieldType(loader, LOGIN, n("llllilii"));
                field(loader, LOGIN, n("llllilii")).set(null, usernameInput);
            } else if (scenario.equals("auto-off")) {
                put(loader, AUTO, HungBySonAutoLoginDialogPatcher.AUTO_ENABLED, Boolean.FALSE);
            } else if (scenario.equals("combat-off")) {
                put(loader, HungBySonAutoLoginDialogPatcher.COMBAT, HungBySonAutoLoginDialogPatcher.COMBAT_ENABLED, Boolean.FALSE);
                put(loader, HungBySonAutoLoginDialogPatcher.FARM, HungBySonAutoLoginDialogPatcher.FARM_ENABLED, Boolean.FALSE);
            } else if (scenario.equals("stale-counter")) {
                put(loader, AUTO, HungBySonAutoLoginDialogPatcher.SCAN_COUNTER, Integer.valueOf(37));
                put(loader, AUTO, HungBySonAutoLoginDialogPatcher.RETRY_ATTEMPTS, Integer.valueOf(3));
            } else if (scenario.equals("pending-deadline")) {
                put(loader, AUTO, HungBySonAutoLoginDialogPatcher.RETRY_DEADLINE,
                        Long.valueOf(System.currentTimeMillis() + 60000L));
                put(loader, AUTO, HungBySonAutoLoginDialogPatcher.SCAN_COUNTER, Integer.valueOf(37));
                put(loader, AUTO, HungBySonAutoLoginDialogPatcher.RETRY_ATTEMPTS, Integer.valueOf(3));
            } else if (scenario.equals("char-select")) {
                put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.CURRENT_SCREEN, selectScreen);
                put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.ACTIVE_DIALOG, null);
                put(loader, HungBySonAutoLoginDialogPatcher.SELECT, HungBySonAutoLoginDialogPatcher.WORLD_ENTERED, Boolean.TRUE);
            } else if (scenario.equals("login-modal")) {
                put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.CURRENT_SCREEN, login);
            } else if (scenario.equals("loading-modal")) {
                put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.CURRENT_SCREEN, loading);
            } else if (scenario.equals("select-modal")) {
                put(loader, ENGINE, HungBySonAutoLoginDialogPatcher.CURRENT_SCREEN, selectScreen);
                put(loader, HungBySonAutoLoginDialogPatcher.SELECT, HungBySonAutoLoginDialogPatcher.WORLD_ENTERED, Boolean.TRUE);
            }

            Method connected = method(loader, CONNECTION, HungBySonAutoLoginDialogPatcher.CONNECTED_GETTER);
            eq("native connected getter reflects isolated connection state", connected.invoke(connection), Boolean.valueOf(scenario.equals("disconnected-loss") ? false : true));
            Method tick = method(loader, AUTO, HungBySonAutoLoginDialogPatcher.TICK);
            for (int i = 0; i < ticks; i++) tick.invoke(null);

            Result result = new Result();
            result.dialogs = dialogActions;
            result.logins = loginShows;
            result.buttons = buttonActions;
            result.authLogins = loginAuthActions;
            result.authSelects = selectAuthActions;
            result.counter = ((Integer)get(loader, AUTO, HungBySonAutoLoginDialogPatcher.SCAN_COUNTER)).intValue();
            result.attempts = ((Integer)get(loader, AUTO, HungBySonAutoLoginDialogPatcher.RETRY_ATTEMPTS)).intValue();
            result.deadline = ((Long)get(loader, AUTO, HungBySonAutoLoginDialogPatcher.RETRY_DEADLINE)).longValue();
            result.connected = ((Boolean)connected.invoke(connection)).booleanValue();
            Object current = get(loader, ENGINE, HungBySonAutoLoginDialogPatcher.CURRENT_SCREEN);
            result.screen = current == login ? "login" : current == loading ? "loading" : current == game ? "game" : current == selectScreen ? "select" : current == tabs ? "tabs" : "other";
            result.events = new ArrayList<String>(events);
            return result;
        }
    }
    static void sameResult(String scenario, Result before, Result candidate) {
        eq(scenario + " dialog calls preserved", candidate.dialogs, before.dialogs);
        eq(scenario + " login calls preserved", candidate.logins, before.logins);
        eq(scenario + " native button actions preserved", candidate.buttons, before.buttons);
        eq(scenario + " Login auth actions preserved", candidate.authLogins, before.authLogins);
        eq(scenario + " CharSelect auth actions preserved", candidate.authSelects, before.authSelects);
        eq(scenario + " popup scan counter preserved", candidate.counter, before.counter);
        eq(scenario + " unknown-retry streak preserved", candidate.attempts, before.attempts);
        eq(scenario + " screen outcome preserved", candidate.screen, before.screen);
        eq(scenario + " retry-deadline behavior preserved", candidate.deadline > 0L, before.deadline > 0L);
        eq(scenario + " event sequence preserved", candidate.events, before.events);
    }
    static boolean hasChosenSelectIndex(List<String> actions) {
        return actions.contains("auth-select:0:0");
    }

    public static void main(String[] args) throws Exception {
        boolean currentBaseline = args.length == 5 && "--current-baseline".equals(args[4]);
        if (args.length != 4 && !currentBaseline)
            throw new IllegalArgumentException("before.jar candidate.jar microemulator.jar report.txt [--current-baseline]");
        File before = new File(args[0]), candidate = new File(args[1]), microemulator = new File(args[2]);
        if (!before.isFile() || !candidate.isFile() || !microemulator.isFile())
            throw new IllegalArgumentException("Baseline, candidate and microemulator JARs must exist");
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        unsafe = (sun.misc.Unsafe)unsafeField.get(null);
        try (JarFile jar = new JarFile(before)) {
            if (!currentBaseline) {
                eq("input baseline SHA-256", hash(read(new FileInputStream(before))), HungBySonAutoLoginDialogPatcher.INPUT_SHA256);
            }
            require(entry(jar, AUTO + ".class") != null, "native Auto Login class exists");
        }
        if (currentBaseline) compareCurrentAutoLoginClass(before, candidate);
        else compareArchives(before, candidate);

        Result originalBug;
        Result fixedBug;
        if (currentBaseline) {
            originalBug = run(before, microemulator, "generic", 1000);
            fixedBug = run(candidate, microemulator, "generic", 1000);
            sameResult("current generic connected inventory dialog", originalBug, fixedBug);
            eq("current input keeps generic confirmation on gameplay tab", originalBug.screen, "tabs");
            eq("current input does not invoke generic confirmation button", originalBug.dialogs, 0);
            eq("current input does not show Login Screen for a generic confirmation", originalBug.logins, 0);
            eq("candidate keeps active gameplay tab", fixedBug.screen, "tabs");
            eq("candidate clears stale scan counter", fixedBug.counter, 0);
            eq("candidate clears generic retry streak", fixedBug.attempts, 0);
            eq("candidate leaves retry deadline intact", fixedBug.deadline, 0L);
        } else {
            originalBug = run(before, microemulator, "generic", 41);
            eq("baseline item confirmation invokes native dialog at tick 41", originalBug.dialogs, 1);
            eq("baseline item confirmation shows Login Screen twice", originalBug.logins, 2);
            eq("baseline item confirmation switches to Login Screen", originalBug.screen, "login");

            fixedBug = run(candidate, microemulator, "generic", 1000);
            eq("candidate generic connected inventory dialog never invokes native button", fixedBug.dialogs, 0);
            eq("candidate generic connected inventory dialog never invokes Login Screen", fixedBug.logins, 0);
            eq("candidate keeps active gameplay tab", fixedBug.screen, "tabs");
            eq("candidate clears stale scan counter", fixedBug.counter, 0);
            eq("candidate clears generic retry streak", fixedBug.attempts, 0);
            eq("candidate leaves retry deadline intact", fixedBug.deadline, 0L);
        }

        sameResult("connected explicit loss notice", run(before, microemulator, "connected-loss", 21), run(candidate, microemulator, "connected-loss", 21));
        require(run(candidate, microemulator, "connected-loss", 21).dialogs > 0,
                "connected native loss notice remains actionable after generic-modal guard");
        sameResult("disconnected native loss flow", run(before, microemulator, "disconnected-loss", 21), run(candidate, microemulator, "disconnected-loss", 21));
        Result retryBefore = run(before, microemulator, "login-retry", 65);
        Result retryCandidate = run(candidate, microemulator, "login-retry", 65);
        sameResult("Login Screen retry path", retryBefore, retryCandidate);
        require(retryCandidate.authLogins > 0 && retryCandidate.events.contains("auth-login:0:0"),
                "connected Login Screen reaches its native login action with ready username field");
        Result selectBefore = run(before, microemulator, "char-select", 25);
        Result selectCandidate = run(candidate, microemulator, "char-select", 25);
        sameResult("character-select path", selectBefore, selectCandidate);
        require(selectCandidate.authSelects > 0 && hasChosenSelectIndex(selectCandidate.events),
                "post-world character selection reaches native select action with a chosen index");
        for (String excludedScreen : new String[]{"login-modal", "loading-modal", "select-modal"}) {
            Result nativeBefore = run(before, microemulator, excludedScreen, 41);
            Result nativeCandidate = run(candidate, microemulator, excludedScreen, 41);
            sameResult(excludedScreen + " connected modal exclusion", nativeBefore, nativeCandidate);
            require(nativeCandidate.dialogs > 0, excludedScreen + " preserves the native modal action");
        }
        Result plus10Before = run(before, microemulator, "item-plus10", 41);
        Result plus10Candidate = run(candidate, microemulator, "item-plus10", 1000);
        if (currentBaseline) {
            sameResult("current +10 item notice remains on inventory tab", plus10Before, plus10Candidate);
            eq("current +10 input keeps inventory tab", plus10Before.screen, "tabs");
            eq("current +10 input does not show Login Screen", plus10Before.logins, 0);
            eq("current +10 input does not invoke native dialog", plus10Before.dialogs, 0);
        } else {
            eq("baseline +10 item notice reaches Login Screen", plus10Before.logins, 2);
            eq("baseline +10 item notice invokes native dialog", plus10Before.dialogs, 1);
        }
        eq("candidate +10 item notice remains on inventory tab", plus10Candidate.screen, "tabs");
        eq("candidate +10 item notice does not show Login Screen", plus10Candidate.logins, 0);
        eq("candidate +10 item notice does not invoke native dialog", plus10Candidate.dialogs, 0);
        Result popupBefore = run(before, microemulator, "native-popup", 16);
        Result popupCandidate = run(candidate, microemulator, "native-popup", 16);
        sameResult("native post-world modal path", popupBefore, popupCandidate);
        require(popupCandidate.buttons > 0, "native post-world modal still selects its original OK button");
        sameResult("GameScreen modal path", run(before, microemulator, "game-screen", 45), run(candidate, microemulator, "game-screen", 45));
        Result autoOff = run(candidate, microemulator, "auto-off", 45);
        sameResult("Auto Login OFF control", run(before, microemulator, "auto-off", 45), autoOff);
        eq("Auto Login OFF suppresses modal action", autoOff.dialogs, 0);
        eq("Auto Login OFF does not show Login Screen", autoOff.logins, 0);
        Result combatOff = run(candidate, microemulator, "combat-off", 45);
        sameResult("combat and farm OFF control", run(before, microemulator, "combat-off", 45), combatOff);
        eq("combat and farm OFF suppress generic modal action", combatOff.dialogs, 0);
        Result staleCounter = run(candidate, microemulator, "stale-counter", 1);
        eq("candidate stale popup counter reset", staleCounter.counter, 0);
        eq("candidate stale unknown-retry reset", staleCounter.attempts, 0);
        Result pendingDeadline = run(candidate, microemulator, "pending-deadline", 1);
        eq("future pending deadline is not overwritten", pendingDeadline.deadline > System.currentTimeMillis(), true);
        eq("future deadline preserves popup scan counter", pendingDeadline.counter, 37);
        eq("future deadline preserves retry streak", pendingDeadline.attempts, 3);

        // A deliberately stale cache is not a production inventory-factory path: the audited
        // native preflight above overwrites it. Keep this observation explicit for review.
        Result staleCacheBefore = run(before, microemulator, "cached-loss", 21);
        Result staleCacheCandidate = run(candidate, microemulator, "cached-loss", 21);
        sameResult("stale cached loss classification", staleCacheBefore, staleCacheCandidate);

        try (PrintWriter report = new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[3]), "UTF-8"))) {
            report.println("baseline.sha256=" + hash(read(new FileInputStream(before))));
            report.println("candidate.sha256=" + hash(read(new FileInputStream(candidate))));
            report.println("baseline.generic@41=dialogB:" + originalBug.dialogs + ",loginShow:" + originalBug.logins + ",screen:" + originalBug.screen);
            report.println("candidate.generic@1000=dialogB:" + fixedBug.dialogs + ",loginShow:" + fixedBug.logins + ",screen:" + fixedBug.screen);
            report.println("candidate.genericCounters=scan:" + fixedBug.counter + ",retries:" + fixedBug.attempts + ",deadline:" + fixedBug.deadline);
            report.println("connectedLossNotice=preserved");
            report.println("disconnectedNativeFlow=preserved");
            report.println("loginRetry=preserved");
            report.println("charSelect=preserved");
            report.println("nativePostWorldModal=preserved");
            report.println("loginAuthAction=" + retryCandidate.events);
            report.println("charSelectAuthAction=" + selectCandidate.events);
            report.println("plus10.baseline=dialogB:" + plus10Before.dialogs + ",loginShow:" + plus10Before.logins + ",screen:" + plus10Before.screen);
            report.println("plus10.candidate=dialogB:" + plus10Candidate.dialogs + ",loginShow:" + plus10Candidate.logins + ",screen:" + plus10Candidate.screen);
            report.println("futureDeadline=counters-preserved");
            report.println("staleLossCache=generic factory preflight overwrites old loss text; direct stale-state trace=" + staleCacheCandidate.events);
            report.println("network=none; RMS=none; liveAccount=none");
            report.println("baseline.mode=" + (currentBaseline ? "current-input differential" : "pinned historical pre-fix"));
            report.println("ALL PASS checks=" + checks);
        }
        System.out.println("ALL PASS checks=" + checks + " network=none RMS=none live-account=none");
    }
}
