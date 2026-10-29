import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Runtime differential probes over the supplied Hung and Bạch Hổ JAR bytes. */
public final class HungBySonBachHoParityHostTest implements Opcodes {
    private static final String HUNG_SHA = "EA401D771635723AD2EE2BF3EB5FE9A7D5F490559DE6F97983BD1F107A5659AC";
    private static final String BACH_HO_SHA = "2BE9C4DB203EBAD94DB20B21D1F3DD0F4B611C90756D738DF44272BF2C2CF840";
    public static long now;
    public static long timestamp;
    public static int interval;
    private static int checks;

    private HungBySonBachHoParityHostTest() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("<reference.jar> <candidate.jar> <dependencyDir> <combat.properties> <tempo.properties>");
        }
        File reference = new File(args[0]);
        File candidate = new File(args[1]);
        File dependencyDir = new File(args[2]);
        require(reference.isFile() && candidate.isFile(), "both runtime JARs exist");
        require(dependencyDir.isDirectory(), "dependency directory exists");
        expect(hash(reference).equals(BACH_HO_SHA), "pinned Bạch Hổ reference hash");
        Properties combat = load(new File(args[3]));
        Properties tempo = load(new File(args[4]));

        // The root-menu dispatcher and initialized speed state are native code in both JARs.
        testSpeedMenu(reference, candidate, tempo);

        // The cadence probes execute the real initialized gate methods with only the clock read
        // redirected to this host. They cover strict equality on both sides of the 400ms gate.
        testAutoGate(reference, candidate, combat);
        testRequestGate(reference, candidate, combat);

        // The selector and fast cast are invoked from the two isolated JAR class loaders. The
        // combat catalog supplies the obfuscated owners/fields; no source implementation is used.
        testTargetSelection(reference, candidate, combat);
        testFastCast(reference, candidate, combat);

        // Canvas timing is executed one outer pass at a time with a scripted clock and a counted
        // native update call. The speed field is changed through the actual option dispatcher.
        testFixedLoop(reference, candidate, tempo);
        testMovement(reference, candidate, tempo);
        if (tempo.getProperty("candidate.speed.codes") != null) testFractionalTempo(reference, candidate, tempo);
        System.out.println("HungBySonBachHoParityHostTest PASS (" + checks + " checks)");
    }

    private static void testSpeedMenu(File reference, File candidate, Properties symbols) throws Exception {
        int[] ref = speedSequence(reference, symbols, "reference");
        int[] cand = speedSequence(candidate, symbols, "candidate");
        if (symbols.getProperty("candidate.speed.codes") == null) {
            expect(Arrays.equals(ref, cand), "runtime speed menu choices match donor: " + Arrays.toString(ref));
        } else {
            expect(Arrays.equals(cand, new int[]{1,1,2,3,-8,-9}), "runtime updated speed menu choices: " + Arrays.toString(cand));
            expect(ref[0] == cand[0] && ref[1] == cand[1] && ref[2] == cand[2] && ref[3] == cand[3],
                    "default and x1/x2/x3 match the donor");
        }
        expect(ref.length == 6 && ref[0] == 1 && ref[1] == 1 && ref[2] == 2
                && ref[3] == 3 && ref[4] == 4 && ref[5] == 5,
                "default x1 and menu actions select x1..x5 in order");
    }

    private static int[] speedSequence(File jar, Properties symbols, String side) throws Exception {
        String owner = propertyAny(symbols, side, new String[]{"speed.owner", "engine.speed.owner", "engine.owner"});
        String fieldName = propertyAny(symbols, side, new String[]{"speed.field", "engine.speed.field"});
        String actionOwner = optionalPropertyAny(symbols, side, new String[]{"menu.owner", "engine.action.owner"}, owner);
        String action = property(symbols, side, "menu.action");
        String actionName = action.substring(0, action.indexOf('('));
        String actionDesc = action.substring(action.indexOf('('));
        int optionArg = integerProperty(symbols, side, "engine.action.option.arg", side.equals("reference") ? 1 : 0);
        if (side.equals("reference")) {
            MethodNode nativeAction = method(parse(readClass(jar, actionOwner)), actionName, actionDesc);
            List<AbstractInsnNode> code = realInstructions(nativeAction);
            boolean found = false;
            for (int i = 0; i < code.size(); i++) if (code.get(i) instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)code.get(i);
                if (field.getOpcode() != PUTSTATIC || !field.owner.equals(owner) || !field.name.equals(fieldName)) continue;
                for (int k = i - 1; k >= Math.max(0, i - 4); k--) if (code.get(k) instanceof VarInsnNode
                        && code.get(k).getOpcode() == ILOAD) {
                    optionArg = ((VarInsnNode)code.get(k)).var - ((nativeAction.access & ACC_STATIC) == 0 ? 1 : 0);
                    found = true; break;
                }
            }
            require(found && optionArg >= 0 && optionArg < 2, "reference option ID parameter discovered from its speed assignment");
        }
        int fixedValue = integerProperty(symbols, side, "engine.action.fixed.arg", 0);
        String idsText = propertyAny(symbols, side, new String[]{"menu.optionActions", "menu.childActions", "engine.action.option.ids"});
        String[] idParts = idsText.split(",");
        require(idParts.length == 5, side + " exposes five explicit speed menu action IDs");
        String binary = owner.replace('/', '.');
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()},
                HungBySonBachHoParityHostTest.class.getClassLoader())) {
            Class<?> engine = Class.forName(binary, true, loader);
            Class<?> actionClass = Class.forName(actionOwner.replace('/', '.'), true, loader);
            Field speed = declaredField(engine, fieldName);
            Method actionMethod = declaredMethod(actionClass, actionName, actionDesc);
            require(Type.getArgumentTypes(actionDesc).length == 2
                    && (Type.VOID_TYPE.equals(Type.getReturnType(actionDesc))
                    || Type.BOOLEAN_TYPE.equals(Type.getReturnType(actionDesc))),
                    "speed action is the mapped two-int dispatcher");
            Object receiver = Modifier.isStatic(actionMethod.getModifiers()) ? null : unsafe().allocateInstance(actionClass);
            List<Integer> values = new ArrayList<Integer>();
            values.add(Integer.valueOf(speed.getInt(null)));
            for (String part : idParts) {
                int id = Integer.parseInt(part.trim());
                Object[] arguments = new Object[]{Integer.valueOf(fixedValue), Integer.valueOf(fixedValue)};
                arguments[optionArg] = Integer.valueOf(id);
                Object handled = invoke(actionMethod, receiver, arguments);
                if (Type.BOOLEAN_TYPE.equals(Type.getReturnType(actionDesc))) {
                    expect(((Boolean)handled).booleanValue(), side + " speed menu action handled " + id);
                }
                values.add(Integer.valueOf(speed.getInt(null)));
            }
            return ints(values);
        }
    }

    private static void testAutoGate(File reference, File candidate, Properties symbols) throws Exception {
        String[] outcomes = new String[2];
        File[] jars = new File[]{reference, candidate};
        String[] sides = new String[]{"reference", "candidate"};
        for (int j = 0; j < jars.length; j++) outcomes[j] = autoGate(jars[j], symbols, sides[j]);
        expect(outcomes[0].equals(outcomes[1]), "runtime auto gate trace matches: " + outcomes[0]);
        expect(outcomes[0].contains("400:false") && outcomes[0].contains("401:true"),
                "native auto gate remains strict at the 400ms equality boundary");
    }

    private static String autoGate(File jar, Properties symbols, String side) throws Exception {
        String owner = propertyAny(symbols, side, new String[]{"auto.gate.owner", "cast.owner"});
        String delayOwner = propertyAny(symbols, side, new String[]{"auto.delay.owner", "cast.owner"});
        String timestampOwner = propertyAny(symbols, side, new String[]{"auto.timestamp.owner", "cast.owner"});
        String gateName = property(symbols, side, "auto.gate.method");
        String delayName = property(symbols, side, "auto.delay.field");
        String timestampName = property(symbols, side, "auto.timestamp.field");
        byte[] bytes = readClass(jar, owner);
        ClassNode node = parse(bytes);
        MethodNode gate = method(node, gateName, "()Z");
        int clockReads = replaceClock(gate);
        require(clockReads == 1, side + " auto gate has one real clock read");
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        final byte[] patched = writer.toByteArray();
        final String binaryName = owner.replace('/', '.');
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()},
                HungBySonBachHoParityHostTest.class.getClassLoader()) {
            protected Class<?> findClass(String requested) throws ClassNotFoundException {
                if (requested.equals(binaryName)) return defineClass(requested, patched, 0, patched.length);
                return super.findClass(requested);
            }
        }) {
            Class<?> cast = Class.forName(binaryName, true, loader);
            Field delay = declaredField(type(loader, delayOwner), delayName);
            int intervalMs = delay.getInt(null);
            expect(intervalMs == 400, side + " initialized auto interval is 400ms");
            Field previous = declaredField(type(loader, timestampOwner), timestampName);
            Method ready = declaredMethod(cast, gateName, "()Z");
            StringBuilder result = new StringBuilder();
            for (long elapsed : new long[]{399L, 400L, 401L}) {
                setNumeric(previous, null, 100000L);
                now = 100000L + elapsed;
                boolean value = ((Boolean)invoke(ready, null)).booleanValue();
                result.append(elapsed).append(':').append(value).append(';');
            }
            return result.toString();
        }
    }

    private static void testRequestGate(File reference, File candidate, Properties symbols) throws Exception {
        // request.gate.* entries point at the comparison inside the real request method. The
        // actual instruction window is extracted with its three live values, not an asserted delay.
        String ref = requestGate(reference, symbols, "reference");
        String cand = requestGate(candidate, symbols, "candidate");
        expect(ref.equals(cand), "runtime request interval gate trace matches donor: " + ref);
    }

    private static String requestGate(File jar, Properties symbols, String side) throws Exception {
        String owner = property(symbols, side, "request.owner");
        String methodName = property(symbols, side, "request.method");
        String descriptor = property(symbols, side, "request.descriptor");
        String extra = property(symbols, side, "request.extra.ms");
        String delayField = property(symbols, side, "request.delay.field");
        String delayOwner = propertyAny(symbols, side, new String[]{"request.delay.owner", "cast.owner"});
        String clockOwner = property(symbols, side, "request.now.owner");
        String clockField = property(symbols, side, "request.now.field");
        String timestampOwner = property(symbols, side, "request.timestamp.owner");
        String timestampField = property(symbols, side, "request.timestamp.field");
        ClassNode node = parse(readClass(jar, owner));
        MethodNode source = method(node, methodName, descriptor);
        List<AbstractInsnNode> real = realInstructions(source);
        int clockIndex = findStaticField(real, clockOwner, clockField);
        int timestampIndex = findStaticField(real, timestampOwner, timestampField);
        int delayIndex = findStaticField(real, delayOwner, delayField);
        require(clockIndex >= 0 && timestampIndex > clockIndex && delayIndex > timestampIndex,
                side + " native request method reads current time, previous time, then interval");
        int branchIndex = delayIndex;
        while (branchIndex < real.size() && real.get(branchIndex).getOpcode() != IFLE
                && real.get(branchIndex).getOpcode() != IF_ICMPLE && real.get(branchIndex).getOpcode() != IF_ICMPLT) branchIndex++;
        require(branchIndex < real.size(), side + " request gate has a strict comparison branch");
        String probeName = "HungBySonNativeRequestGateProbe" + ("reference".equals(side) ? "R" : "C");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_4, ACC_PUBLIC, probeName, null, "java/lang/Object", null);
        MethodVisitor code = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "ready", "()Z", null, null);
        code.visitCode();
        for (int cursor = clockIndex; cursor < branchIndex; cursor++) {
            AbstractInsnNode instruction = real.get(cursor);
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (field.owner.equals(clockOwner) && field.name.equals(clockField))
                    code.visitFieldInsn(GETSTATIC, "HungBySonBachHoParityHostTest", "now", "J");
                else if (field.owner.equals(timestampOwner) && field.name.equals(timestampField))
                    code.visitFieldInsn(GETSTATIC, "HungBySonBachHoParityHostTest", "timestamp", "J");
                else if (field.owner.equals(delayOwner) && field.name.equals(delayField))
                    code.visitFieldInsn(GETSTATIC, "HungBySonBachHoParityHostTest", "interval", "I");
                else instruction.accept(code);
            } else if (instruction instanceof MethodInsnNode
                    && "java/lang/System".equals(((MethodInsnNode)instruction).owner)
                    && "currentTimeMillis".equals(((MethodInsnNode)instruction).name)) {
                code.visitFieldInsn(GETSTATIC, "HungBySonBachHoParityHostTest", "now", "J");
            } else instruction.accept(code);
        }
        int branch = real.get(branchIndex).getOpcode();
        Label no = new Label();
        if (branch == IFLE) code.visitJumpInsn(IFLE, no);
        else if (branch == IF_ICMPLE) code.visitJumpInsn(IF_ICMPLE, no);
        else if (branch == IF_ICMPLT) code.visitJumpInsn(IF_ICMPLT, no);
        else throw new AssertionError(side + " request gate uses a supported strict boundary branch");
        code.visitInsn(ICONST_1); code.visitInsn(IRETURN);
        code.visitLabel(no); code.visitInsn(ICONST_0); code.visitInsn(IRETURN);
        code.visitMaxs(0, 0); code.visitEnd(); writer.visitEnd();
        final byte[] bytes = writer.toByteArray();
        Class<?> probe = new ClassLoader(HungBySonBachHoParityHostTest.class.getClassLoader()) {
            Class<?> loadProbe() { return defineClass(probeName, bytes, 0, bytes.length); }
        }.loadProbe();
        Method ready = probe.getMethod("ready");
        long baseline = 100000L;
        timestamp = baseline;
        int extraMs = Integer.parseInt(extra);
        StringBuilder trace = new StringBuilder();
        for (int nativeInterval : new int[]{0, 100, 600, 32767}) {
            int threshold = nativeInterval + extraMs;
            interval = nativeInterval;
            for (int elapsed : new int[]{Math.max(0, threshold - 1), threshold, threshold + 1}) {
                now = baseline + elapsed;
                boolean value = ((Boolean)ready.invoke(null)).booleanValue();
                require(value == (elapsed > threshold), side + " native request gate strict boundary "
                        + elapsed + " > " + threshold);
                trace.append(nativeInterval).append('/').append(elapsed).append(':').append(value).append(';');
            }
        }
        return trace.toString();
    }

    private static int findStaticField(List<AbstractInsnNode> real, String owner, String fieldName) {
        for (int i = 0; i < real.size(); i++) {
            AbstractInsnNode instruction = real.get(i);
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == GETSTATIC) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (owner.equals(field.owner) && fieldName.equals(field.name)) return i;
            }
        }
        return -1;
    }

    private static void testTargetSelection(File reference, File candidate, Properties symbols) throws Exception {
        // Implemented against the paired scan and entity-list entries in combat.properties. Each
        // scenario starts in a fresh loader, so current-target state cannot leak across cases.
        String[] cases = new String[]{"near", "far", "dead", "self", "npc", "player", "tie"};
        for (String name : cases) {
            String ref = selectTarget(reference, symbols, "reference", name);
            String cand = selectTarget(candidate, symbols, "candidate", name);
            expect(ref.equals(cand), "actual scan branch " + name + " matches donor: " + ref);
        }
    }

    private static String selectTarget(File jar, Properties symbols, String side, String scenario) throws Exception {
        activeCombatSymbols = symbols;
        // Scene construction is deliberately driven by symbol values discovered from each exact
        // JAR. This method is filled from the closed combat catalog before the parity build runs.
        String owner = propertyAny(symbols, side, new String[]{"player.owner", "target.player.owner"});
        String scanName = propertyAny(symbols, side, new String[]{"scan.method", "target.scan.method"});
        String scanDesc = propertyAny(symbols, side, new String[]{"scan.descriptor", "target.scan.descriptor"});
        String entityOwner = propertyAny(symbols, side, new String[]{"entity.owner", "target.entity.owner"});
        String listOwner = propertyAny(symbols, side, new String[]{"list.owner", "target.list.owner"});
        String listFieldOwner;
        String listFieldName;
        listFieldOwner = propertyAny(symbols, side, new String[]{"entity.list.owner", "target.entity.list.owner"});
        listFieldName = propertyAny(symbols, side, new String[]{"entity.list.field", "target.entity.list.field"});
        String selectedOwner = propertyAny(symbols, side, new String[]{"target.owner", "target.selected.owner"});
        String selectedField = propertyAny(symbols, side, new String[]{"target.field", "target.selected.field"});
        String currentOwner = propertyAny(symbols, side, new String[]{"player.singleton.owner", "target.current.owner"});
        String currentField = propertyAny(symbols, side, new String[]{"player.singleton.field", "target.current.field"});
        String addName = propertyAny(symbols, side, new String[]{"list.add.method", "target.list.add.method"});
        String xField = propertyAny(symbols, side, new String[]{"entity.x.field", "target.entity.x.field"});
        String yField = propertyAny(symbols, side, new String[]{"entity.y.field", "target.entity.y.field"});
        String typeField = propertyAny(symbols, side, new String[]{"entity.type.field", "target.entity.type.field"});
        String markedField = propertyAny(symbols, side, new String[]{"entity.exclude.field", "entity.marked.field", "target.entity.marked.field"});
        String rangeField = propertyAny(symbols, side, new String[]{"entity.range.field", "target.entity.range.field"});
        String stateField = propertyAny(symbols, side, new String[]{"entity.state.field", "target.entity.state.field"});
        String rejectMethod = propertyAny(symbols, side, new String[]{"entity.reject.method", "target.entity.reject.method"});
        // Actual scene setup and scan invocation are intentionally a hard failure until every
        // owner/field/descriptor needed by the paired catalog is present.
        return executeTargetCase(jar, side, owner, scanName, scanDesc, entityOwner, listOwner,
                listFieldOwner, listFieldName, selectedOwner, selectedField, currentOwner,
                currentField, addName, xField, yField, typeField, markedField, rangeField,
                stateField, rejectMethod, scenario);
    }

    private static String executeTargetCase(File jar, String side, String owner, String scanName,
            String scanDesc, String entityOwner, String listOwner, String listFieldOwner,
            String listFieldName, String selectedOwner, String selectedField, String currentOwner,
            String currentField, String addName, String xField, String yField, String typeField,
            String markedField, String rangeField, String stateField, String rejectMethod,
            String scenario) throws Exception {
        String scanOwner = propertyAny(activeCombatSymbols, side, new String[]{"scan.owner", "target.scan.owner"});
        String listSizeName = propertyAny(activeCombatSymbols, side, new String[]{"list.size.method", "target.list.size.method"});
        String listGetName = propertyAny(activeCombatSymbols, side, new String[]{"list.get.method", "target.list.get.method"});
        String singletonDescriptor = propertyAny(activeCombatSymbols, side, new String[]{"player.singleton.descriptor", "target.current.descriptor"});
        int oldRange = integerProperty(activeCombatSymbols, side, "target.native.range", 240);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()},
                HungBySonBachHoParityHostTest.class.getClassLoader())) {
            Class<?> playerType = type(loader, owner);
            Class<?> entityType = type(loader, entityOwner);
            Class<?> vectorType = type(loader, listOwner);
            Object player = unsafe().allocateInstance(playerType);
            Object vector = vectorType.getDeclaredConstructor().newInstance();
            Method add = oneArgumentMethod(vectorType, addName, entityType);
            Method scan = declaredMethod(type(loader, scanOwner), scanName, scanDesc);
            Field listField = declaredField(type(loader, listFieldOwner), listFieldName);
            Field singletonField = declaredField(type(loader, currentOwner), currentField);
            Field targetField = declaredField(type(loader, selectedOwner), selectedField);
            setStaticOrInstance(listField, null, vector);
            setStaticOrInstance(singletonField, null, player);
            setStaticOrInstance(targetField, Modifier.isStatic(targetField.getModifiers()) ? null : player, null);
            setNumeric(declaredField(entityType, xField), player, 0L);
            setNumeric(declaredField(entityType, yField), player, 0L);
            setNumeric(declaredField(playerType, rangeField), player, oldRange);
            enableLeadingBooleanGate(jar, loader, scanOwner, scanName, scanDesc);

            StateValues states = discoverStates(entityType, stateField, rejectMethod);
            long excludedState = scanExcludedState(jar, scanOwner, scanName, scanDesc, entityOwner, stateField);
            List<Object> entities = new ArrayList<Object>();
            List<String> tags = new ArrayList<String>();
            if ("near".equals(scenario)) {
                addEntity(loader, entityType, vector, add, entities, tags, "M0", xField, yField,
                        typeField, markedField, stateField, states.alive, 100, 0, (byte)1, false);
            } else if ("far".equals(scenario)) {
                addEntity(loader, entityType, vector, add, entities, tags, "M0", xField, yField,
                        typeField, markedField, stateField, states.alive, 1000, 0, (byte)1, false);
            } else if ("dead".equals(scenario)) {
                addEntity(loader, entityType, vector, add, entities, tags, "M0", xField, yField,
                        typeField, markedField, stateField, excludedState, 100, 0, (byte)1, false);
            } else if ("self".equals(scenario)) {
                addEntityObject(vector, add, entities, tags, player, "SELF");
            } else if ("npc".equals(scenario)) {
                addEntity(loader, entityType, vector, add, entities, tags, "NPC", xField, yField,
                        typeField, markedField, stateField, states.alive, 100, 0, (byte)2, false);
            } else if ("player".equals(scenario)) {
                addEntity(loader, entityType, vector, add, entities, tags, "PLAYER", xField, yField,
                        typeField, markedField, stateField, states.alive, 100, 0, (byte)0, false);
            } else if ("tie".equals(scenario)) {
                addEntity(loader, entityType, vector, add, entities, tags, "M0", xField, yField,
                        typeField, markedField, stateField, states.alive, 300, 0, (byte)1, false);
                addEntity(loader, entityType, vector, add, entities, tags, "M1", xField, yField,
                        typeField, markedField, stateField, states.alive, -300, 0, (byte)1, false);
            } else throw new IllegalArgumentException("unknown selector scenario " + scenario);

            require(listSizeName.length() > 0 && listGetName.length() > 0 && singletonDescriptor.length() > 0,
                    side + " complete entity list/current-player catalog");
            Method scanner = scan;
            invoke(scanner, Modifier.isStatic(scanner.getModifiers()) ? null : player);
            Object selected = targetField.get(Modifier.isStatic(targetField.getModifiers()) ? null : player);
            String result = "none";
            for (int i = 0; i < entities.size(); i++) if (entities.get(i) == selected) result = tags.get(i);
            if ("near".equals(scenario) || "far".equals(scenario)) {
                expect("M0".equals(result), side + " runtime " + scenario + " monster chosen within donor full-map radius");
            }
            if ("dead".equals(scenario) || "self".equals(scenario) || "npc".equals(scenario) || "player".equals(scenario)) {
                expect("none".equals(result), side + " runtime " + scenario + " is excluded from monster auto-target");
            }
            if ("tie".equals(scenario)) require("M0".equals(result) || "M1".equals(result), side + " runtime tie resolves to a listed monster");
            return result;
        }
    }

    private static Properties activeCombatSymbols;

    private static final class StateValues {
        final long alive, rejected; final boolean hasRejected;
        StateValues(long alive, long rejected, boolean hasRejected) { this.alive = alive; this.rejected = rejected; this.hasRejected = hasRejected; }
    }

    private static StateValues discoverStates(Class<?> entityType, String stateFieldName, String rejectName) throws Exception {
        Object probe = unsafe().allocateInstance(entityType);
        Field state = declaredField(entityType, stateFieldName);
        Method reject = null;
        for (Method method : entityType.getDeclaredMethods()) {
            if (method.getName().equals(rejectName) && method.getParameterTypes().length == 0
                    && method.getReturnType() == boolean.class) { method.setAccessible(true); reject = method; break; }
        }
        if (reject == null) throw new NoSuchMethodException(entityType.getName() + "." + rejectName + "()Z");
        Long alive = null, dead = null;
        for (int i = -2; i <= 32; i++) {
            setNumeric(state, probe, i);
            boolean rejected = ((Boolean)invoke(reject, Modifier.isStatic(reject.getModifiers()) ? null : probe)).booleanValue();
            if (!rejected && alive == null) alive = Long.valueOf(i);
            if (rejected && dead == null) dead = Long.valueOf(i);
        }
        if (alive == null) throw new AssertionError("could not create an eligible entity state from actual "+rejectName+" bytecode");
        return new StateValues(alive.longValue(), dead == null ? 0L : dead.longValue(), dead != null);
    }

    private static long scanExcludedState(File jar, String owner, String methodName, String descriptor,
            String entityOwner, String stateField) throws Exception {
        ClassNode node = parse(readClass(jar, owner));
        MethodNode scan = method(node, methodName, descriptor);
        for (AbstractInsnNode instruction = scan.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != GETFIELD) continue;
            FieldInsnNode field = (FieldInsnNode)instruction;
            if (!field.name.equals(stateField) || !field.owner.equals(entityOwner)) continue;
            AbstractInsnNode next = nextReal(instruction);
            Long value = intConstant(next);
            if (value != null) return value.longValue();
        }
        throw new IllegalStateException("scan bytecode does not expose the filtered entity state value");
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode instruction) {
        for (AbstractInsnNode next = instruction == null ? null : instruction.getNext(); next != null; next = next.getNext())
            if (next.getOpcode() >= 0) return next;
        return null;
    }

    private static Long intConstant(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        int opcode = instruction.getOpcode();
        if (opcode >= ICONST_M1 && opcode <= ICONST_5) return Long.valueOf(opcode - ICONST_0);
        if (instruction instanceof IntInsnNode) return Long.valueOf(((IntInsnNode)instruction).operand);
        if (instruction instanceof LdcInsnNode && ((LdcInsnNode)instruction).cst instanceof Integer)
            return Long.valueOf(((Integer)((LdcInsnNode)instruction).cst).intValue());
        return null;
    }

    private static void enableLeadingBooleanGate(File jar, ClassLoader loader, String owner,
            String methodName, String descriptor) throws Exception {
        ClassNode node = parse(readClass(jar, owner));
        MethodNode scan = method(node, methodName, descriptor);
        int seen = 0;
        for (AbstractInsnNode instruction = scan.instructions.getFirst(); instruction != null && seen < 96;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() >= 0) seen++;
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != GETSTATIC) continue;
            FieldInsnNode field = (FieldInsnNode)instruction;
            if (!"Z".equals(field.desc)) continue;
            Field gate = declaredField(type(loader, field.owner), field.name);
            gate.setBoolean(null, true);
            checks++;
            System.out.println("SETUP " + owner + "." + methodName + " enables leading native scan flag " + field.name);
            return;
        }
        throw new IllegalStateException("no leading native scan-enabled boolean in " + owner + "." + methodName);
    }

    private static void addEntity(ClassLoader loader, Class<?> entityType, Object vector, Method add,
            List<Object> entities, List<String> tags, String tag, String xField, String yField,
            String typeField, String markedField, String stateField, long state, int x, int y,
            byte kind, boolean marked) throws Exception {
        Object entity = unsafe().allocateInstance(entityType);
        setNumeric(declaredField(entityType, xField), entity, x);
        setNumeric(declaredField(entityType, yField), entity, y);
        setNumeric(declaredField(entityType, typeField), entity, kind);
        setNumeric(declaredField(entityType, stateField), entity, state);
        Field markedMember = declaredField(entityType, markedField);
        if (markedMember.getType() == boolean.class) markedMember.setBoolean(entity, marked);
        else setNumeric(markedMember, entity, marked ? 1 : 0);
        addEntityObject(vector, add, entities, tags, entity, tag);
    }

    private static void addEntityObject(Object vector, Method add, List<Object> entities,
            List<String> tags, Object entity, String tag) throws Exception {
        invoke(add, vector, entity); entities.add(entity); tags.add(tag);
    }

    private static void testFastCast(File reference, File candidate, Properties symbols) throws Exception {
        String ref = invokeFastCast(reference, symbols, "reference");
        String cand = invokeFastCast(candidate, symbols, "candidate");
        expect(ref.equals(cand), "zero-delay fast cast reaches native request sender: " + ref);
        expect(ref.endsWith("senderCalls=1"), "fast cast emits one request synchronously on the cast path");
    }

    private static String invokeFastCast(File jar, Properties symbols, String side) throws Exception {
        activeCombatSymbols = symbols;
        String owner = property(symbols, side, "cast.owner");
        String methodName = propertyAny(symbols, side, new String[]{"cast.helper.method", "cast.fast.method"});
        String descriptor = propertyAny(symbols, side, new String[]{"cast.helper.descriptor", "cast.fast.descriptor"});
        String packetOwner = propertyAny(symbols, side, new String[]{"cast.sender.owner", "cast.owner"});
        String packetMethod = property(symbols, side, "cast.sender.method");
        String packetDescriptor = optionalPropertyAny(symbols, side,
                new String[]{"cast.sender.descriptor", "packet.sender.descriptor"}, "()V");
        PacketLoader loader = new PacketLoader(jar, side, packetOwner, packetMethod, packetDescriptor);
        try {
            String playerOwner = propertyAny(symbols, side, new String[]{"player.owner", "target.player.owner"});
            String scanOwner = propertyAny(symbols, side, new String[]{"scan.owner", "target.scan.owner"});
            String scanName = propertyAny(symbols, side, new String[]{"scan.method", "target.scan.method"});
            String scanDesc = propertyAny(symbols, side, new String[]{"scan.descriptor", "target.scan.descriptor"});
            String entityOwner = propertyAny(symbols, side, new String[]{"entity.owner", "target.entity.owner"});
            String listOwner = propertyAny(symbols, side, new String[]{"list.owner", "target.list.owner"});
            String listFieldOwner = propertyAny(symbols, side, new String[]{"entity.list.owner", "target.entity.list.owner"});
            String listFieldName = propertyAny(symbols, side, new String[]{"entity.list.field", "target.entity.list.field"});
            String singletonOwner = propertyAny(symbols, side, new String[]{"player.singleton.owner", "target.current.owner"});
            String singletonField = propertyAny(symbols, side, new String[]{"player.singleton.field", "target.current.field"});
            String targetOwner = propertyAny(symbols, side, new String[]{"target.owner", "target.selected.owner"});
            String targetName = propertyAny(symbols, side, new String[]{"target.field", "target.selected.field"});
            String listAdd = propertyAny(symbols, side, new String[]{"list.add.method", "target.list.add.method"});
            String xName = propertyAny(symbols, side, new String[]{"entity.x.field", "target.entity.x.field"});
            String yName = propertyAny(symbols, side, new String[]{"entity.y.field", "target.entity.y.field"});
            String typeName = propertyAny(symbols, side, new String[]{"entity.type.field", "target.entity.type.field"});
            String markName = propertyAny(symbols, side, new String[]{"entity.exclude.field", "entity.marked.field", "target.entity.marked.field"});
            String rangeName = propertyAny(symbols, side, new String[]{"entity.range.field", "target.entity.range.field"});
            String stateName = propertyAny(symbols, side, new String[]{"entity.state.field", "target.entity.state.field"});
            String rejectName = propertyAny(symbols, side, new String[]{"entity.reject.method", "target.entity.reject.method"});

            Class<?> playerType = type(loader, playerOwner);
            Class<?> entityType = type(loader, entityOwner);
            Class<?> vectorType = type(loader, listOwner);
            Object player = unsafe().allocateInstance(playerType);
            Object vector = vectorType.getDeclaredConstructor().newInstance();
            Method add = oneArgumentMethod(vectorType, listAdd, entityType);
            Method scan = declaredMethod(type(loader, scanOwner), scanName, scanDesc);
            Object far = unsafe().allocateInstance(entityType);
            setNumeric(declaredField(entityType, xName), far, 1000L);
            setNumeric(declaredField(entityType, yName), far, 0L);
            setNumeric(declaredField(entityType, typeName), far, 1L);
            setNumeric(declaredField(entityType, stateName), far,
                    discoverStates(entityType, stateName, rejectName).alive);
            Field mark = declaredField(entityType, markName);
            if (mark.getType() == boolean.class) mark.setBoolean(far, false); else setNumeric(mark, far, 0L);
            invoke(add, vector, far);
            setNumeric(declaredField(entityType, xName), player, 0L);
            setNumeric(declaredField(entityType, yName), player, 0L);
            setNumeric(declaredField(playerType, rangeName), player, 240L);
            setStaticOrInstance(declaredField(type(loader, listFieldOwner), listFieldName), null, vector);
            setStaticOrInstance(declaredField(type(loader, singletonOwner), singletonField), null, player);
            Field selected = declaredField(type(loader, targetOwner), targetName);
            setStaticOrInstance(selected, Modifier.isStatic(selected.getModifiers()) ? null : player, null);
            enableLeadingBooleanGate(jar, loader, scanOwner, scanName, scanDesc);
            invoke(scan, Modifier.isStatic(scan.getModifiers()) ? null : player);
            Object chosen = selected.get(Modifier.isStatic(selected.getModifiers()) ? null : player);
            require(chosen == far, side + " full-map scan selects the far monster before casting");

            Class<?> cast = type(loader, owner);
            Object instance = unsafe().allocateInstance(cast);
            Object castTargets = vectorType.getDeclaredConstructor().newInstance();
            setObjectMember(loader, symbols, side, instance, new String[]{"cast.actor.owner"},
                    new String[]{"cast.actor.field"}, player);
            setObjectMember(loader, symbols, side, instance, new String[]{"cast.target.owner"},
                    new String[]{"cast.target.field"}, far);
            setObjectMember(loader, symbols, side, instance, new String[]{"cast.targets.owner"},
                    new String[]{"cast.targets.field"}, castTargets);
            setNumericMember(loader, symbols, side, instance, new String[]{"cast.owner"},
                    new String[]{"cast.skill.field"}, 0L);
            setNumericMember(loader, symbols, side, instance, new String[]{"cast.owner"},
                    new String[]{"cast.animation.field"}, 0L);
            setNumericMember(loader, symbols, side, instance, new String[]{"cast.owner"},
                    new String[]{"cast.request.frame.field"}, 0L);
            MethodNode castPath = method(parse(readClass(jar, owner)), methodName, descriptor);
            for (AbstractInsnNode instruction : castPath.instructions.toArray()) {
                if (!(instruction instanceof FieldInsnNode)) continue;
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (field.getOpcode() == GETFIELD && field.owner.equals(entityOwner) && field.desc.startsWith("L")) {
                    Field animation = declaredField(entityType, field.name);
                    if (animation.get(player) == null) animation.set(player,
                            unsafe().allocateInstance(type(loader, Type.getType(field.desc).getInternalName())));
                }
            }
            Method method = declaredMethod(cast, methodName, descriptor);
            Object[] values = defaultArguments(Type.getArgumentTypes(descriptor));
            packetCount = 0;
            now = 100000L;
            invoke(method, instance, values);
            return "selected=far,senderCalls=" + packetCount;
        } finally { loader.close(); }
    }

    private static void setObjectMember(ClassLoader loader, Properties symbols, String side,
            Object instance, String[] ownerKeys, String[] fieldKeys, Object value) throws Exception {
        String owner = propertyAny(symbols, side, ownerKeys);
        String name = propertyAny(symbols, side, fieldKeys);
        Field field = declaredField(type(loader, owner), name);
        setStaticOrInstance(field, Modifier.isStatic(field.getModifiers()) ? null : instance, value);
    }

    private static void setNumericMember(ClassLoader loader, Properties symbols, String side,
            Object instance, String[] ownerKeys, String[] fieldKeys, long value) throws Exception {
        String owner = propertyAny(symbols, side, ownerKeys);
        String name = propertyAny(symbols, side, fieldKeys);
        Field field = declaredField(type(loader, owner), name);
        setNumeric(field, Modifier.isStatic(field.getModifiers()) ? null : instance, value);
    }

    private static void testFixedLoop(File reference, File candidate, Properties symbols) throws Exception {
        // Replays are added after the tempo catalog exposes the native update call and the loop
        // backedge. The method invocation is fail-closed if those entries are absent.
        for (int speed = 1; speed <= (symbols.getProperty("candidate.speed.codes") == null ? 5 : 3); speed++) {
            String ref = loopPass(reference, symbols, "reference", speed, 16L);
            String cand = loopPass(candidate, symbols, "candidate", speed, 16L);
            expect(ref.equals(cand), "fixed-loop update trace parity at x" + speed + ": ref=" + ref + "; candidate=" + cand);
        }
        String refCap = loopPass(reference, symbols, "reference", 5, 200L);
        String candCap = loopPass(candidate, symbols, "candidate", 5, 200L);
        expect(refCap.equals(candCap), "fixed-loop max-three-update cap parity: " + refCap);
        int count = 0;
        for (int speed = 1; speed <= (symbols.getProperty("candidate.speed.codes") == null ? 5 : 3); speed++) for (long delta : new long[]{0, 1, 19, 20, 21, 40, 60, 61, 200})
            for (long paint : new long[]{0, 15, 16, 40}) for (long update : new long[]{0, 17}) {
                probePaintCost = paint; probeUpdateCost = update;
                String expected = loopPass(reference, symbols, "reference", speed, delta);
                String actual = loopPass(candidate, symbols, "candidate", speed, delta);
                require(expected.equals(actual), "loop delta=" + delta + " speed=" + speed + " paint=" + paint
                        + " update=" + update + ": " + expected + " != " + actual);
                count++;
            }
        probePaintCost = 0; probeUpdateCost = 0;
        System.out.println("PASS " + count + " differential loop cases: threshold, backlog, per-tick clock and slow-frame pacing");
    }

    private static void testMovement(File reference, File candidate, Properties symbols) throws Exception {
        String expected = movementTrace(reference, symbols, "reference");
        String actual = movementTrace(candidate, symbols, "candidate");
        expect(expected.equals(actual), "actual 16-tick movement paths match: " + expected);
    }

    private static String movementTrace(File jar, Properties symbols, String side) throws Exception {
        String owner = property(symbols, side, "entity.owner");
        String movement = property(symbols, side, "entity.movement");
        MethodNode nativeMove = method(parse(readClass(jar, owner)), movement.substring(0, movement.indexOf('(')), "()V");
        ClassNode probe = new ClassNode(ASM9); probe.version = V1_4; probe.access = ACC_PUBLIC;
        probe.name = "HungMovementProbe" + (++probeSerial); probe.superName = "java/lang/Object";
        MethodNode move = new MethodNode(ASM9, ACC_PUBLIC, "move", "()V", null, null);
        java.util.Map<org.objectweb.asm.tree.LabelNode, org.objectweb.asm.tree.LabelNode> labels = new java.util.HashMap<org.objectweb.asm.tree.LabelNode, org.objectweb.asm.tree.LabelNode>();
        for (AbstractInsnNode i : nativeMove.instructions.toArray()) if (i instanceof org.objectweb.asm.tree.LabelNode)
            labels.put((org.objectweb.asm.tree.LabelNode)i, new org.objectweb.asm.tree.LabelNode());
        java.util.Set<String> fields = new java.util.LinkedHashSet<String>();
        List<String> coordinates = new ArrayList<String>();
        org.objectweb.asm.tree.LabelNode done = new org.objectweb.asm.tree.LabelNode();
        for (AbstractInsnNode original : nativeMove.instructions.toArray()) {
            if (original instanceof org.objectweb.asm.tree.FrameNode || original instanceof org.objectweb.asm.tree.LineNumberNode) continue;
            AbstractInsnNode i = original.clone(labels); move.instructions.add(i);
            if (i instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)i;
                if (field.name.equals("hsoMoveTick")) { field.owner = "HungBySonBachHoParityHostTest"; field.name = "probeTicks"; }
                else if (field.owner.equals(owner) && field.desc.equals("I")) {
                    field.owner = probe.name; fields.add(field.name);
                    if (field.getOpcode() == PUTFIELD) coordinates.add(field.name);
                } else throw new AssertionError("unexpected native movement prefix field: " + field.owner + "." + field.name);
            }
            if (coordinates.size() == 2) break;
        }
        require(coordinates.size() == 2, side + " two actual coordinate writes extracted");
        for (AbstractInsnNode i : move.instructions.toArray()) if (i instanceof org.objectweb.asm.tree.JumpInsnNode)
            ((org.objectweb.asm.tree.JumpInsnNode)i).label = done;
        move.instructions.add(done); move.instructions.add(new InsnNode(RETURN));
        for (String name : fields) probe.fields.add(new org.objectweb.asm.tree.FieldNode(ACC_PUBLIC, name, "I", null, null));
        probe.methods.add(move); ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); probe.accept(writer);
        final byte[] bytes = writer.toByteArray();
        Class<?> runtime = new ClassLoader(HungBySonBachHoParityHostTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        Object instance = unsafe().allocateInstance(runtime);
        for (String name : fields) runtime.getField(name).setInt(instance, coordinates.contains(name) ? 100 : 1);
        StringBuilder trace = new StringBuilder();
        for (int tick = 0; tick < 16; tick++) {
            probeTicks = tick; runtime.getMethod("move").invoke(instance);
            trace.append(runtime.getField(coordinates.get(0)).getInt(instance)).append(',')
                    .append(runtime.getField(coordinates.get(1)).getInt(instance)).append(';');
        }
        return trace.toString();
    }

    private static String loopPass(File jar, Properties symbols, String side, int speed, long delta)
            throws Exception {
        String canvasOwner = property(symbols, side, "canvas.owner");
        String update = property(symbols, side, "canvas.update");
        String speedOwner = property(symbols, side, "speed.owner");
        String speedField = property(symbols, side, "speed.field");
        ClassNode source = parse(readClass(jar, canvasOwner));
        MethodNode run = method(source, "run", "()V");
        String probeName = "HungTempoLoopProbe" + (++probeSerial);
        ClassNode probe = new ClassNode(ASM9);
        probe.version = V1_4; probe.access = ACC_PUBLIC; probe.name = probeName; probe.superName = "java/lang/Object";
        for (org.objectweb.asm.tree.FieldNode field : source.fields) if (field.desc.equals("J") || field.desc.equals("I"))
            probe.fields.add(new org.objectweb.asm.tree.FieldNode(field.access, field.name, field.desc, null, field.value));
        String accumulator = null;
        int updateSites = 0, sleepSites = 0;
        for (AbstractInsnNode instruction : run.instructions.toArray()) {
            if (instruction instanceof org.objectweb.asm.tree.FrameNode) { run.instructions.remove(instruction); continue; }
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (field.getOpcode() == GETSTATIC && field.desc.startsWith("L")) {
                    run.instructions.set(field, new InsnNode(ACONST_NULL));
                    continue;
                }
                if (field.owner.equals(canvasOwner)) {
                    if (field.getOpcode() == GETSTATIC && field.desc.equals("J")) {
                        run.instructions.set(field, new org.objectweb.asm.tree.LdcInsnNode(Long.valueOf(16L)));
                        continue;
                    }
                    if (field.getOpcode() == GETFIELD && field.desc.equals("J")) {
                        AbstractInsnNode next = field.getNext(); while (next != null && next.getOpcode() < 0) next = next.getNext();
                        if (next instanceof org.objectweb.asm.tree.LdcInsnNode
                                && Long.valueOf(40L).equals(((org.objectweb.asm.tree.LdcInsnNode)next).cst)) accumulator = field.name;
                    }
                    field.owner = probeName;
                } else if (field.owner.equals(speedOwner) && field.name.equals(speedField)) {
                    field.owner = "HungBySonBachHoParityHostTest"; field.name = "probeSpeed";
                } else if (field.name.equals("hsoMoveTick")) {
                    field.owner = "HungBySonBachHoParityHostTest"; field.name = "probeTicks";
                } else if (field.getOpcode() == PUTSTATIC && field.desc.equals("J")) {
                    field.owner = "HungBySonBachHoParityHostTest"; field.name = "probeCached";
                } else if (field.getOpcode() == GETSTATIC && field.desc.equals("J")) {
                    run.instructions.set(field, new org.objectweb.asm.tree.LdcInsnNode(Long.valueOf(16L)));
                } else if (field.getOpcode() == GETSTATIC && field.desc.startsWith("L")) {
                    run.instructions.set(field, new InsnNode(ACONST_NULL));
                }
            } else if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.owner.equals(canvasOwner) && call.name.equals("codexScaleElapsed")) {
                    call.owner = probeName;
                } else if ((call.owner + "." + call.name + call.desc).equals(update)) {
                    if (call.getOpcode() != INVOKESTATIC) run.instructions.insertBefore(call, new InsnNode(POP));
                    run.instructions.set(call, new MethodInsnNode(INVOKESTATIC, "HungBySonBachHoParityHostTest", "probeUpdate", "()V", false));
                    updateSites++;
                } else if (call.desc.equals("()J") && call.getOpcode() == INVOKESTATIC) {
                    run.instructions.set(call, new MethodInsnNode(INVOKESTATIC, "HungBySonBachHoParityHostTest", "probeTime", "()J", false));
                } else if (call.name.equals("repaint") || call.name.equals("serviceRepaints")) {
                    run.instructions.insertBefore(call, new InsnNode(POP));
                    if (call.name.equals("serviceRepaints")) run.instructions.set(call,
                            new MethodInsnNode(INVOKESTATIC, "HungBySonBachHoParityHostTest", "probePaint", "()V", false));
                    else run.instructions.remove(call);
                } else if (call.owner.equals("java/lang/Thread") && call.name.equals("sleep")) {
                    run.instructions.set(call, new MethodInsnNode(INVOKESTATIC, "HungBySonBachHoParityHostTest", "probeSleep", "(J)V", false));
                    // End the supplied native loop at its first sleep, preserving the actual pacing computation.
                    for (AbstractInsnNode i : run.instructions.toArray()) if (i instanceof MethodInsnNode
                            && ((MethodInsnNode)i).name.equals("probeSleep") && (i.getNext() == null || i.getNext().getOpcode() != RETURN))
                        run.instructions.insert(i, new InsnNode(RETURN));
                    sleepSites++;
                }
            }
        }
        require(updateSites == 1 && sleepSites == 2 && accumulator != null, side + " exact native loop sites");
        for (MethodNode helper : source.methods) if (helper.name.equals("codexScaleElapsed") && helper.desc.equals("(J)J")) {
            for (AbstractInsnNode i : helper.instructions.toArray()) if (i instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)i;
                if (field.owner.equals(canvasOwner)) field.owner = probeName;
                else if (field.owner.equals(speedOwner) && field.name.equals(speedField)) {
                    field.owner = "HungBySonBachHoParityHostTest"; field.name = "probeSpeed";
                }
            }
            probe.methods.add(helper);
        }
        probe.methods.add(run);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); probe.accept(writer);
        final byte[] bytes = writer.toByteArray();
        Class<?> runtime = new ClassLoader(HungBySonBachHoParityHostTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        Object instance = unsafe().allocateInstance(runtime);
        probeSpeed = speed; probeTicks = 0; probeUpdates = 0; probeReads = 0;
        probeClock = 100000L; probeDelta = delta; probeCached = 100000L; probeSleeping = -1L;
        probeClockTrace = new ArrayList<Long>();
        invoke(runtime.getDeclaredMethod("run"), instance);
        return "updates=" + probeUpdates + ",ticks=" + probeTicks + ",carry="
                + declaredField(runtime, accumulator).getLong(instance) + ",sleep=" + probeSleeping
                + ",cached=" + probeCached + ",clock=" + probeClockTrace;
    }

    private static int probeSerial;
    private static void testFractionalTempo(File reference, File candidate, Properties symbols) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[]{candidate.toURI().toURL()},
                HungBySonBachHoParityHostTest.class.getClassLoader())) {
            Class<?> canvas = type(loader, property(symbols, "candidate", "canvas.owner"));
            Class<?> settings = type(loader, property(symbols, "candidate", "speed.owner"));
            Field speed = declaredField(settings, property(symbols, "candidate", "speed.field"));
            Method scale = canvas.getDeclaredMethod("codexScaleElapsed", Long.TYPE); scale.setAccessible(true);
            int cases = 0;
            for (int numerator : new int[]{8,9}) for (long[] pattern : new long[][]{{16},{1,19,3,16,0,40,27}}) {
                Object clock = unsafe().allocateInstance(canvas); speed.setInt(null, -numerator);
                long input = 0, output = 0;
                for (int i = 0; i < 1000; i++) {
                    long elapsed = pattern[i % pattern.length]; input += elapsed;
                    output += ((Long)scale.invoke(clock, Long.valueOf(elapsed))).longValue();
                    require(output == input * numerator / 10, "fractional cumulative elapsed remains exact at " + numerator + "/10 tick=" + i);
                    cases++;
                }
            }
            Object clock = unsafe().allocateInstance(canvas);
            speed.setInt(null,-8); expect(Long.valueOf(0).equals(scale.invoke(clock,Long.valueOf(1))),"x0.8 carries sub-millisecond remainder");
            speed.setInt(null,-9); expect(Long.valueOf(0).equals(scale.invoke(clock,Long.valueOf(1))),"changing fractional speed clears previous remainder");
            speed.setInt(null,1); expect(Long.valueOf(16).equals(scale.invoke(clock,Long.valueOf(16))),"returning to x1 preserves native elapsed");
            System.out.println("PASS " + cases + " cumulative fractional elapsed checks over uniform and irregular frames");
        }
        for (int numerator : new int[]{8,9}) for (long delta : new long[]{1,16,19,20,40,200}) {
            String expected = loopPass(reference,symbols,"reference",1,delta * numerator / 10);
            String actual = loopPass(candidate,symbols,"candidate",-numerator,delta);
            require(expected.substring(0,expected.indexOf(",cached=")).equals(actual.substring(0,actual.indexOf(",cached="))),
                    "fractional native loop matches donor using scaled elapsed " + numerator + "/10 delta=" + delta);
            long cached = probeUpdates == 0 ? 100000L : 100000L + delta;
            require(probeCached == cached,"fractional game speed retains real wall clock for skill waits");
        }
        System.out.println("PASS fractional loop integration and unscaled combat clock");
    }

    public static int probeSpeed, probeTicks, probeUpdates, probeReads;
    public static long probeClock, probeDelta, probeCached, probeSleeping;
    public static long probeUpdateCost, probePaintCost;
    public static List<Long> probeClockTrace;
    public static long probeTime() { if (probeReads++ == 1) probeClock += probeDelta; return probeClock; }
    public static void probeUpdate() { probeUpdates++; probeClockTrace.add(Long.valueOf(probeCached)); probeClock += probeUpdateCost; }
    public static void probePaint() { probeClock += probePaintCost; }
    public static void probeSleep(long duration) { probeSleeping = duration; }

    private static int replaceClock(MethodNode method) {
        int reads = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.getOpcode() == INVOKESTATIC && "java/lang/System".equals(call.owner)
                        && "currentTimeMillis".equals(call.name) && "()J".equals(call.desc)) {
                    method.instructions.set(instruction, new FieldInsnNode(GETSTATIC,
                            "HungBySonBachHoParityHostTest", "now", "J"));
                    reads++;
                }
            }
            instruction = next;
        }
        return reads;
    }

    public static void capturePacket() { packetCount++; }
    public static void capturePacket(Object message) { packetCount++; }
    private static int packetCount;

    private static final class PacketLoader extends URLClassLoader {
        private final JarFile jar;
        private final String owner, methodName, descriptor;
        PacketLoader(File source, String side, String owner, String methodName, String descriptor) throws Exception {
            super(new URL[]{source.toURI().toURL()}, HungBySonBachHoParityHostTest.class.getClassLoader());
            this.jar = new JarFile(source); this.owner = owner; this.methodName = methodName; this.descriptor = descriptor;
        }
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String internal = name.replace('.', '/');
            JarEntry entry = jar.getJarEntry(internal + ".class");
            if (entry == null) return super.findClass(name);
            try {
                byte[] original = readAll(jar.getInputStream(entry));
                ClassNode node = parse(original);
                for (MethodNode method : node.methods) {
                    for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
                        AbstractInsnNode next = insn.getNext();
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode)insn;
                            if ("java/lang/System".equals(call.owner) && "currentTimeMillis".equals(call.name)
                                    && "()J".equals(call.desc) && call.getOpcode() == INVOKESTATIC) {
                                method.instructions.set(insn, new FieldInsnNode(GETSTATIC,
                                        "HungBySonBachHoParityHostTest", "now", "J"));
                            }
                        }
                        insn = next;
                    }
                }
                ClassNode target = node.name.equals(owner) ? node : null;
                if (target != null) {
                    int matches = 0;
                    for (MethodNode method : target.methods) {
                        if (!method.name.equals(methodName) || !method.desc.equals(descriptor)) continue;
                        method.instructions.clear(); method.tryCatchBlocks.clear(); method.localVariables = null;
                        Type[] args = Type.getArgumentTypes(descriptor);
                        int local = (method.access & ACC_STATIC) == 0 ? 1 : 0;
                        int messageLocal = -1;
                        for (Type arg : args) {
                            if (arg.getSort() == Type.OBJECT || arg.getSort() == Type.ARRAY) { messageLocal = local; break; }
                            local += arg.getSize();
                        }
                        if (messageLocal >= 0) {
                            method.instructions.add(new VarInsnNode(ALOAD, messageLocal));
                            method.instructions.add(new MethodInsnNode(INVOKESTATIC,
                                    "HungBySonBachHoParityHostTest", "capturePacket", "(Ljava/lang/Object;)V", false));
                        } else {
                            method.instructions.add(new MethodInsnNode(INVOKESTATIC,
                                    "HungBySonBachHoParityHostTest", "capturePacket", "()V", false));
                        }
                        method.instructions.add(new InsnNode(RETURN)); matches++;
                    }
                    if (matches != 1) throw new AssertionError("expected one native packet sink method, found " + matches);
                }
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
                byte[] bytes = writer.toByteArray(); return defineClass(name, bytes, 0, bytes.length);
            } catch (Throwable ex) { throw new ClassNotFoundException(name, ex); }
        }
        public void close() throws java.io.IOException { try { jar.close(); } finally { super.close(); } }
    }

    private static UnsafeHolder unsafeHolder;
    private static sun.misc.Unsafe unsafe() throws Exception {
        if (unsafeHolder == null) {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
            unsafeHolder = new UnsafeHolder((sun.misc.Unsafe)f.get(null));
        }
        return unsafeHolder.value;
    }
    private static final class UnsafeHolder { final sun.misc.Unsafe value; UnsafeHolder(sun.misc.Unsafe value) { this.value = value; } }

    private static Object[] defaultArguments(Type[] types) {
        Object[] result = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            switch (types[i].getSort()) {
                case Type.BOOLEAN: result[i] = Boolean.FALSE; break;
                case Type.BYTE: result[i] = Byte.valueOf((byte)0); break;
                case Type.CHAR: result[i] = Character.valueOf((char)0); break;
                case Type.SHORT: result[i] = Short.valueOf((short)0); break;
                case Type.INT: result[i] = Integer.valueOf(0); break;
                case Type.FLOAT: result[i] = Float.valueOf(0); break;
                case Type.LONG: result[i] = Long.valueOf(0); break;
                case Type.DOUBLE: result[i] = Double.valueOf(0); break;
                default: result[i] = null;
            }
        }
        return result;
    }

    private static Properties load(File file) throws Exception {
        require(file.isFile(), "catalog exists: " + file.getName());
        Properties properties = new Properties();
        try (InputStream in = new FileInputStream(file)) { properties.load(in); }
        return properties;
    }
    private static String property(Properties properties, String side, String key) {
        String[] prefixes = new String[]{side + ".", ("reference".equals(side) ? "ref." : "hung."), ""};
        for (String prefix : prefixes) {
            String value = properties.getProperty(prefix + key);
            if (value != null) return value.trim();
        }
        throw new IllegalStateException("catalog entry missing: " + side + "." + key);
    }
    private static String propertyAny(Properties properties, String side, String[] keys) {
        for (String key : keys) {
            String[] prefixes = new String[]{side + ".", ("reference".equals(side) ? "ref." : "hung."), ""};
            for (String prefix : prefixes) {
                String value = properties.getProperty(prefix + key);
                if (value != null) return value.trim();
            }
        }
        throw new IllegalStateException("catalog entries missing for " + side + ": " + Arrays.toString(keys));
    }
    private static String optionalPropertyAny(Properties properties, String side, String[] keys, String fallback) {
        for (String key : keys) {
            String[] prefixes = new String[]{side + ".", ("reference".equals(side) ? "ref." : "hung."), ""};
            for (String prefix : prefixes) {
                String value = properties.getProperty(prefix + key);
                if (value != null) return value.trim();
            }
        }
        return fallback;
    }
    private static int integerProperty(Properties properties, String side, String key, int fallback) {
        String[] prefixes = new String[]{side + ".", ("reference".equals(side) ? "ref." : "hung."), ""};
        for (String prefix : prefixes) {
            String value = properties.getProperty(prefix + key);
            if (value != null) return Integer.parseInt(value.trim());
        }
        return fallback;
    }
    private static byte[] readClass(File jarFile, String internalName) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry(internalName + ".class");
            if (entry == null) throw new IllegalStateException("class missing from JAR: " + internalName);
            return readAll(jar.getInputStream(entry));
        }
    }
    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }
    private static ClassNode parse(byte[] bytes) { ClassNode node = new ClassNode(ASM9); new ClassReader(bytes).accept(node, 0); return node; }
    private static MethodNode method(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        throw new IllegalStateException("method missing in bytecode: " + node.name + "." + name + descriptor);
    }
    private static List<AbstractInsnNode> realInstructions(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }
    private static Class<?> type(ClassLoader loader, String owner) throws Exception { return Class.forName(owner.replace('/', '.'), true, loader); }
    private static Field declaredField(Class<?> owner, String name) throws Exception {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }
    private static void setStaticOrInstance(Field field, Object receiver, Object value) throws Exception {
        if (Modifier.isStatic(field.getModifiers())) field.set(null, value);
        else {
            require(receiver != null, "instance receiver for " + field.getName());
            field.set(receiver, value);
        }
    }
    private static Method oneArgumentMethod(Class<?> owner, String name, Class<?> valueType) throws Exception {
        Method result = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == 1
                    && !method.getParameterTypes()[0].isPrimitive()
                    && method.getParameterTypes()[0].isAssignableFrom(valueType)) {
                if (result != null) throw new IllegalStateException("ambiguous one-argument list method " + name);
                method.setAccessible(true); result = method;
            }
        }
        if (result == null) throw new NoSuchMethodException(owner.getName() + "." + name + "(one arg)");
        return result;
    }
    private static Method declaredMethod(Class<?> owner, String name, String descriptor) throws Exception {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor)) {
                method.setAccessible(true); return method;
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name + descriptor);
    }
    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try { return method.invoke(receiver, args); }
        catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new RuntimeException(cause);
        }
    }
    private static void setNumeric(Field field, Object receiver, long value) throws Exception {
        Class<?> type = field.getType();
        if (type == boolean.class || type == Boolean.class) { field.setBoolean(receiver, value != 0L); return; }
        if (type == long.class || type == Long.class) field.setLong(receiver, value);
        else if (type == int.class || type == Integer.class) field.setInt(receiver, (int)value);
        else if (type == short.class || type == Short.class) field.setShort(receiver, (short)value);
        else if (type == byte.class || type == Byte.class) field.setByte(receiver, (byte)value);
        else throw new IllegalStateException("unsupported numeric field " + field);
    }
    private static int[] ints(List<Integer> values) { int[] result = new int[values.size()]; for (int i = 0; i < result.length; i++) result[i] = values.get(i).intValue(); return result; }
    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) >= 0) digest.update(b, 0, n); }
        StringBuilder result = new StringBuilder(); for (byte b : digest.digest()) result.append(String.format("%02X", Byte.valueOf(b)));
        return result.toString();
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static void expect(boolean value, String message) { require(value, message); System.out.println("PASS " + message); }
}
