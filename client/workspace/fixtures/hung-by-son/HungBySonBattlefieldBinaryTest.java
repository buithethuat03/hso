import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Candidate-level checks for native Battlefield hooks and their semantic policy. */
public final class HungBySonBattlefieldBinaryTest {
    private static final String SUPPORT = "luna/features/BattlefieldSupport";
    private static final String BRIDGE = "luna/generated/BattlefieldBridge";
    private static final String API = "luna/api/BattlefieldAccess";
    private static final String CACHE_SUPPORT = "luna/features/MonsterCacheSupport";
    private static final String NATIVE_HELPER = "HungBySonAutoAttackBinaryTest";
    private static int checks;

    private HungBySonBattlefieldBinaryTest() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4)
            throw new IllegalArgumentException("candidate.jar auto-attack.properties battlefield.properties [root|a|b|c]");
        File candidate = new File(args[0]);
        Properties auto = load(new File(args[1]));
        Properties battlefield = load(new File(args[2]));
        String namespace = args.length == 4 ? args[3] : "root";
        if (!candidate.isFile()) throw new IllegalArgumentException("Missing candidate JAR: " + candidate);
        if (!"root".equals(namespace) && !"a".equals(namespace)
                && !"b".equals(namespace) && !"c".equals(namespace))
            throw new IllegalArgumentException("Namespace must be root, a, b, or c");

        verifyCandidateHash(candidate, battlefield, namespace);
        verifyAllClassLinkage(candidate);
        verifyAllNativeMovementHooks(candidate, battlefield, namespace);
        verifyFeatureBridge(candidate, battlefield, namespace);
        verifyNativeBehavior(candidate, auto, battlefield, namespace);
        System.out.println("BATTLEFIELD_BINARY=PASS checks=" + checks
                + "; all-class linkage, packet4/7 position capture, local/OFF behavior, "
                + "actual menu toggle, final-hit receive, target gate, and red-bold name font");
    }

    private static void verifyCandidateHash(File candidate, Properties battlefield, String namespace)
            throws Exception {
        String expected = battlefield.getProperty("output.sha256");
        if (expected != null && "root".equals(namespace))
            require(expected.equalsIgnoreCase(hash(candidate)), "Candidate hash matches battlefield catalog");
    }

    private static void verifyAllClassLinkage(File candidate) throws Exception {
        File micro = new File(new File(System.getProperty("user.dir"), ".."), "..\\.codex-deps\\microemulator.jar");
        URL[] urls = micro.isFile()
                ? new URL[]{candidate.toURI().toURL(), micro.getCanonicalFile().toURI().toURL()}
                : new URL[]{candidate.toURI().toURL()};
        URLClassLoader loader = new URLClassLoader(urls,
                HungBySonBattlefieldBinaryTest.class.getClassLoader());
        JarFile jar = new JarFile(candidate);
        int linked = 0;
        try {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                String path = entry.getName();
                if (!path.endsWith(".class") || path.startsWith("META-INF/versions/")) continue;
                String name = path.substring(0, path.length() - 6).replace('/', '.');
                Class<?> type = Class.forName(name, false, loader);
                type.getDeclaredFields();
                type.getDeclaredMethods();
                type.getDeclaredConstructors();
                linked++;
            }
        } finally {
            try { jar.close(); } finally { loader.close(); }
        }
        require(linked > 0, "Candidate classes link with MicroEmulator APIs");
        require(hasEntry(candidate, BRIDGE + ".class") || hasEntry(candidate, "a/" + BRIDGE + ".class")
                || hasEntry(candidate, "b/" + BRIDGE + ".class") || hasEntry(candidate, "c/" + BRIDGE + ".class"),
                "Candidate contains the generated Battlefield bridge");
    }

    private static void verifyAllNativeMovementHooks(File candidate, Properties catalog,
            String namespace) throws Exception {
        String bridgeOwner = qualify(namespace, BRIDGE);
        String entityOwner = required(catalog, "entity.owner");
        int hooked = 0;
        List<String> hookOwners = new ArrayList<String>();
        JarFile jar = new JarFile(candidate);
        try {
            List<ClassNode> classes = new ArrayList<ClassNode>();
            Map<String, String> superclasses = new HashMap<String, String>();
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode owner = new ClassNode();
                new ClassReader(jar.getInputStream(entry)).accept(owner, 0);
                classes.add(owner);
                superclasses.put(owner.name, owner.superName);
            }
            for (ClassNode owner : classes) {
                if (!isEntitySubclass(owner.name, entityOwner, superclasses)) continue;
                for (MethodNode method : owner.methods) {
                    if (!"a".equals(method.name) || !"()V".equals(method.desc)) continue;
                    boolean containsAlign = false;
                    for (AbstractInsnNode instruction : method.instructions.toArray())
                        if (instruction instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode)instruction;
                            if (call.getOpcode() == Opcodes.INVOKESTATIC
                                    && bridgeOwner.equals(call.owner) && "align".equals(call.name)
                                    && call.desc.startsWith("(L") && call.desc.endsWith(";)Z"))
                                containsAlign = true;
                        }
                    if (containsAlign) { hooked++; hookOwners.add(owner.name); }
                }
            }
        } finally { jar.close(); }
        int expected = Integer.parseInt(required(catalog, "position.subclass.updates")) + 1;
        require(hooked == expected, "All " + expected + " native entity a()V movement methods contain "
                + "Battlefield alignment (16 subclass updates plus the base entity; found "
                + hooked + ": " + hookOwners + ")");
    }

    private static boolean isEntitySubclass(String name, String entityOwner,
            Map<String, String> superclasses) {
        String current = name;
        while (current != null) {
            if (entityOwner.equals(current)) return true;
            current = superclasses.get(current);
        }
        return false;
    }

    private static void verifyFeatureBridge(File candidate, Properties catalog, String namespace)
            throws Exception {
        try (NativeProbe probe = new NativeProbe(candidate,
                load(new File("symbols/hung-auto-attack-20261014.properties")))) {
            ClassLoader loader = probe.loader;
            String bridgeName = qualify(namespace, BRIDGE).replace('/', '.');
            Class<?> bridge = loader.loadClass(bridgeName);
            Class<?> entity = loader.loadClass(required(catalog, "entity.owner").replace('/', '.'));
            Class<?> font = loader.loadClass(required(catalog, "font.owner").replace('/', '.'));
            require(Modifier.isStatic(bridge.getMethod("capturePosition", entity).getModifiers()),
                    "Generated bridge exposes static capturePosition(Entity)");
            require(Modifier.isStatic(bridge.getMethod("captureTarget", entity).getModifiers()),
                    "Generated bridge exposes static captureTarget(Entity)");
            require(bridge.getMethod("align", entity).getReturnType() == Boolean.TYPE,
                    "Generated bridge exposes align(Entity):boolean");
            require(Modifier.isStatic(bridge.getMethod("blocked", Object.class).getModifiers()),
                    "Generated bridge exposes static blocked(Object)");
            require(bridge.getMethod("nameFont", font, entity).getReturnType() == font,
                    "Generated bridge exposes nameFont(Font,Entity):Font");
            require(Modifier.isStatic(bridge.getMethod("nameFont", font, entity).getModifiers()),
                    "Name-font selector is static");
        }
    }

    private static void verifyNativeBehavior(File candidate, Properties auto, Properties catalog,
            String namespace) throws Exception {
        NativeProbe probe = new NativeProbe(candidate, auto);
        try {
            ClassLoader loader = probe.loader;
            Class<?> helper = Class.forName(NATIVE_HELPER);
            String bridgeName = qualify(namespace, BRIDGE).replace('/', '.');
            String apiName = qualify(namespace, API).replace('/', '.');
            String supportName = qualify(namespace, SUPPORT).replace('/', '.');
            Class<?> battlefieldBridge = loader.loadClass(bridgeName);
            Class<?> battlefieldApi = loader.loadClass(apiName);
            Class<?> battlefieldSupport = loader.loadClass(supportName);
            Object battlefieldAccess = battlefieldBridge.getField("INSTANCE").get(null);
            Class<?> entityType = loader.loadClass(required(catalog, "entity.owner").replace('/', '.'));
            Class<?> fontType = loader.loadClass(required(catalog, "font.owner").replace('/', '.'));
            verifyFeatureMethods(battlefieldSupport, battlefieldApi);

            Class<?> autoBridgeType = loader.loadClass(qualify(namespace,
                    auto.getProperty("bridge.owner", auto.getProperty("native.menu.owner"))).replace('/', '.'));
            Object autoBridge = autoBridgeType.getDeclaredConstructor().newInstance();
            Class<?> nativeAccessType = Class.forName(NATIVE_HELPER + "$NativeAccess");
            Object nativeAccess = construct(nativeAccessType,
                    new Class<?>[]{ClassLoader.class, Object.class, Properties.class}, loader, autoBridge, auto);
            call(nativeAccess, "installReadyWorld", new Class<?>[0]);

            Object local = field(nativeAccessType, "player").get(nativeAccess);
            Field idField = nativeField(candidate, BRIDGE, "id", "(Ljava/lang/Object;)I", entityType);
            Field typeField = nativeField(candidate, BRIDGE, "type", "(Ljava/lang/Object;)I", entityType);
            Field levelField = nativeField(candidate, BRIDGE, "level", "(Ljava/lang/Object;)I", entityType);
            Field hpField = nativeField(candidate, BRIDGE, "hp", "(Ljava/lang/Object;)I", entityType);
            Field nameField = nativeField(candidate, BRIDGE, "name", "(Ljava/lang/Object;)Ljava/lang/String;", entityType);
            setNumber(idField, local, 700);
            setNumber(typeField, local, 0);
            setNumber(levelField, local, 35);
            setNumber(hpField, local, 100);
            nameField.set(local, "Local");

            Object remotePlayer = nativeEntity(nativeAccess, nativeAccessType,
                    100, (byte)0, 701, "Remote", 32, 70, 80, idField, typeField,
                    levelField, hpField, nameField);
            Object remoteMonster = nativeEntity(nativeAccess, nativeAccessType,
                    100, (byte)1, 801, "Monster", 25, 20, 30, idField, typeField,
                    levelField, hpField, nameField);
            // Skip the native one-shot movement effect branch in packet 4 so the
            // actual coordinate writes run in this headless receiver harness.
            field(entityType, "dP").setByte(remotePlayer, (byte)1);
            field(entityType, "cA").setInt(remotePlayer, -1);
            field(entityType, "cA").setInt(remoteMonster, -1);
            field(entityType, "f").set(remotePlayer, new byte[]{0});
            field(entityType, "f").set(remoteMonster, new byte[]{0});
            call(nativeAccess, "replaceEntities", new Class<?>[]{Object[].class},
                    (Object)new Object[]{local, remotePlayer, remoteMonster});
            require(battlefieldBridge.getMethod("find", Integer.TYPE, Integer.TYPE)
                    .invoke(battlefieldAccess, Integer.valueOf(701), Integer.valueOf(0)) == remotePlayer,
                    "Native bridge finds the preinstalled remote player by ID/type");
            setPacketReadyFlag(loader, candidate, catalog);

            battlefieldSupport.getMethod("resetForTest").invoke(null);
            verifyMenuToggle(probe, auto, namespace, battlefieldSupport);
            require(Boolean.TRUE.equals(battlefieldSupport.getMethod("enabled").invoke(null)),
                    "Actual native row 5 callback enables Battlefield mode");

            PacketHandlers handlers = findPacketHandlers(candidate, loader);
            Method object4 = handlers.object4;
            Method detail7 = handlers.detail7;
            Class<?> messageType = handlers.messageType;
            object4.invoke(null, message(messageType, packet4(0, 701, 75, 85, 0, 1)));
            require(field(entityType, "battlefieldPositionKnown").getBoolean(remotePlayer),
                    "Actual packet-4 receiver marks the remote player position as known (current="
                    + field(entityType, "aZ").getInt(remotePlayer) + "," + field(entityType, "ba").getInt(remotePlayer)
                    + " target=" + field(entityType, "bg").getInt(remotePlayer) + "," + field(entityType, "bh").getInt(remotePlayer)
                    + ", dP=" + field(entityType, "dP").getByte(remotePlayer) + ")");
            int playerX = field(entityType, "battlefieldX").getInt(remotePlayer);
            int playerY = field(entityType, "battlefieldY").getInt(remotePlayer);
            require(playerX == 75 && playerY == 85,
                    "Packet-4 observer captures the exact remote-player coordinates");

            object4.invoke(null, message(messageType, packet4(1, 801, 35, 45, 17, 1)));
            require(field(entityType, "battlefieldPositionKnown").getBoolean(remoteMonster),
                    "Actual packet-4 receiver marks the remote monster position as known");
            detail7.invoke(null, message(messageType, (byte)7,
                    packet7(801, 25, 40, 50, 40, 100, 0, 0)));
            int monsterX = field(entityType, "battlefieldX").getInt(remoteMonster);
            int monsterY = field(entityType, "battlefieldY").getInt(remoteMonster);
            require(monsterX == 40 && monsterY == 50,
                    "Actual packet-7 receiver captures the remote monster target coordinates");

            Method align = battlefieldBridge.getMethod("align", entityType);
            verifyMovementHookChain(candidate, namespace, entityType, remotePlayer,
                    "Remote player native a()V update calls Battlefield align");
            verifyMovementHookChain(candidate, namespace, entityType, remoteMonster,
                    "Remote monster native a()V update calls Battlefield align");
            setPosition(entityType, remotePlayer, 900, 901, 902, 903);
            require(Boolean.TRUE.equals(align.invoke(null, remotePlayer))
                    && Boolean.TRUE.equals(align.invoke(null, remotePlayer)),
                    "Repeated remote-player alignment is stable");
            requirePosition(entityType, remotePlayer, playerX, playerY,
                    "Remote player aligns to packet-4 position without drift");
            setPosition(entityType, remoteMonster, 910, 911, 912, 913);
            require(Boolean.TRUE.equals(align.invoke(null, remoteMonster))
                    && Boolean.TRUE.equals(align.invoke(null, remoteMonster)),
                    "Repeated remote-monster alignment is stable");
            requirePosition(entityType, remoteMonster, monsterX, monsterY,
                    "Remote monster aligns to packet-7 position without drift");

            int localX = field(entityType, "aZ").getInt(local);
            int localY = field(entityType, "ba").getInt(local);
            require(Boolean.FALSE.equals(align.invoke(null, local))
                    && field(entityType, "aZ").getInt(local) == localX
                    && field(entityType, "ba").getInt(local) == localY,
                    "Local player is left to native movement");

            if ("true".equals(catalog.getProperty("battlefield.static.objects.native"))) {
                // NPCs (e.g. the zone-change sign "Khu", id -43) and dropped items are built through
                // the base no-arg constructor and positioned afterwards; they have no server
                // position packet and must stay where native code put them.
                Object base = entityType.getDeclaredConstructor().newInstance();
                require(!field(entityType, "battlefieldPositionKnown").getBoolean(base),
                        "The no-arg entity constructor does not mark a (0,0) position as known");
                setPosition(entityType, base, 500, 260, 500, 260);
                require(Boolean.FALSE.equals(align.invoke(null, base))
                                && field(entityType, "aZ").getInt(base) == 500 && field(entityType, "ba").getInt(base) == 260,
                        "A static object keeps its native position while Battlefield mode is ON");
                Object map = field(loader.loadClass(required(auto, "native.map.owner").replace('/', '.')),
                        required(auto, "native.map.field")).get(null);
                setNumber(field(map.getClass(), "g"), map, 4000);
                setNumber(field(map.getClass(), "h"), map, 4000);
                Class<?> itemType = loader.loadClass(String.format("%100s", "liilllli").replace(' ', 'i'));
                Object item = itemType.getConstructor(Integer.TYPE, Byte.TYPE, String.class, Integer.TYPE,
                        Integer.TYPE, Short.TYPE, Byte.TYPE).newInstance(Integer.valueOf(55), Byte.valueOf((byte)3),
                        "drop", Integer.valueOf(600), Integer.valueOf(300), Short.valueOf((short)1), Byte.valueOf((byte)0));
                require(!field(entityType, "battlefieldPositionKnown").getBoolean(item)
                                && field(entityType, "aZ").getInt(item) == 600 && field(entityType, "ba").getInt(item) == 300,
                        "A real dropped item is created at its native position");
                itemType.getMethod("a").invoke(item);
                require(Math.abs(field(entityType, "aZ").getInt(item) - 600) < 20
                                && Math.abs(field(entityType, "ba").getInt(item) - 300) < 20,
                        "The real item update with Battlefield ON no longer snaps it to (0,0); at="
                                + field(entityType, "aZ").getInt(item) + "," + field(entityType, "ba").getInt(item));
            }

            verifyFinalHitAndFont(probe, loader, battlefieldBridge, battlefieldSupport,
                    battlefieldAccess, battlefieldApi, entityType, fontType,
                    local, remotePlayer, remoteMonster, idField, typeField, levelField,
                    hpField, nameField, auto, catalog, namespace);

            verifyMenuToggle(probe, auto, namespace, battlefieldSupport);
            require(Boolean.FALSE.equals(battlefieldSupport.getMethod("enabled").invoke(null))
                    && ((Integer)battlefieldSupport.getMethod("count").invoke(null)).intValue() == 0,
                    "Actual native row 5 callback disables mode and clears its session FIFO");
            setPosition(entityType, remotePlayer, 930, 931, 932, 933);
            require(Boolean.FALSE.equals(align.invoke(null, remotePlayer))
                    && field(entityType, "aZ").getInt(remotePlayer) == 930
                    && field(entityType, "ba").getInt(remotePlayer) == 931,
                    "Mode OFF leaves the native remote movement path untouched");
        } finally {
            probe.close();
        }
    }

    private static void verifyFeatureMethods(Class<?> support, Class<?> api) throws Exception {
        require(support.getMethod("setEnabled", Boolean.TYPE) != null,
                "Semantic API exposes setEnabled(boolean)");
        require(support.getMethod("enabled").getReturnType() == Boolean.TYPE,
                "Semantic API exposes enabled()");
        require(support.getMethod("excluded", String.class).getReturnType() == Boolean.TYPE,
                "Semantic API exposes excluded(String)");
        require(support.getMethod("onCombat", api, java.io.DataInputStream.class, Integer.TYPE) != null,
                "Semantic API exposes the native packet observer");
        require(support.getMethod("count").getReturnType() == Integer.TYPE
                && support.getMethod("nameAt", Integer.TYPE).getReturnType() == String.class,
                "Semantic API exposes FIFO inspection methods");
    }

    private static void verifyMenuToggle(NativeProbe probe, Properties auto, String namespace,
            Class<?> support) throws Exception {
        ClassLoader loader = probe.loader;
        Class<?> autoBridge = loader.loadClass(qualify(namespace,
                auto.getProperty("bridge.owner", auto.getProperty("native.menu.owner"))).replace('/', '.'));
        Object bridge = autoBridge.getDeclaredConstructor().newInstance();
        autoBridge.getMethod("showMenu", Boolean.TYPE, Boolean.TYPE, Integer.TYPE)
                .invoke(bridge, Boolean.FALSE, Boolean.FALSE, Integer.valueOf(650));
        Object popup = call(probe.nativeProbe, "lastPopup", new Class<?>[0]);
        Field itemsField = field(popup.getClass(), "items");
        Object items = itemsField.get(popup);
        Method listSize = staticHelper("listSize", Object.class);
        Method listGet = staticHelper("listGet", Object.class, Integer.TYPE);
        int size = ((Integer)listSize.invoke(null, items)).intValue();
        int expectedRows = (auto.getProperty("special.menu.action") == null ? 5 : 6)
                + (auto.getProperty("normal.monsters.action") == null ? 0 : 1);
        require(size == expectedRows, "Native auto menu contains Battlefield and optional special row; rows=" + size);
        Object row = listGet.invoke(null, items, Integer.valueOf(4));
        require(nativeActionValue(row) == -115,
                "Battlefield row retains native action -115");
        String label = rowLabel(row);
        require(label.startsWith("Mode chiến trường:"), "Row 5 uses the Battlefield mode label");
        Method clickFinder = NATIVE_HELPER_CLASS().getDeclaredMethod("findNativeClick", Class.class);
        clickFinder.setAccessible(true);
        Method click = (Method)clickFinder.invoke(null, row.getClass());
        click.setAccessible(true);
        click.invoke(row);
        require(((Boolean)support.getMethod("enabled").invoke(null)).booleanValue()
                == label.contains("[TẮT]"), "Actual native row 5 callback toggles the mode");
        checks++;
    }

    private static void verifyFinalHitAndFont(NativeProbe probe, ClassLoader loader,
            Class<?> bridge, Class<?> support, Object access, Class<?> api,
            Class<?> entityType, Class<?> fontType, Object local, Object victim, Object monster,
            Field idField, Field typeField, Field levelField, Field hpField, Field nameField,
            Properties auto, Properties battlefield, String namespace) throws Exception {
        seedNativeFontMetrics(probe.loader);
        Method receive = nativeCombatReceiver(probe.candidate, loader, battlefield, namespace);
        Class<?> messageType = receive.getParameterTypes()[0];
        Object kill = message(messageType, (byte)6, combatPacket(700, 701, 21, 0));
        invokeReceiver(receive, kill, 0);
        require(((Integer)support.getMethod("count").invoke(null)).intValue() == 1
                && Boolean.TRUE.equals(support.getMethod("excluded", String.class).invoke(null, "Remote")),
                "Actual native receive packet records the local player's final hit");
        require(Boolean.TRUE.equals(bridge.getMethod("blocked", Object.class).invoke(null, victim)),
                "Generated target gate blocks the recorded remote player");
        String autoBridgeName = qualify(namespace,
                auto.getProperty("bridge.owner", auto.getProperty("native.menu.owner"))).replace('/', '.');
        Class<?> autoBridgeType = loader.loadClass(autoBridgeName);
        Object autoBridge = autoBridgeType.getDeclaredConstructor().newInstance();
        Method validTarget = autoBridgeType.getMethod("validTarget", Object.class, Boolean.TYPE);
        require(Boolean.FALSE.equals(validTarget.invoke(autoBridge, victim, Boolean.TRUE)),
                "Actual AutoAttackAccess.validTarget rejects the Battlefield-owned player");
        require(Boolean.FALSE.equals(bridge.getMethod("blocked", Object.class).invoke(null, monster)),
                "Generated target gate leaves native monsters eligible");

        Field redField = field(fontType, required(battlefield, "font.red.field"));
        Object red = redField.get(null);
        require(red != null, "Native red-bold font field is initialized");
        Method nameFont = bridge.getMethod("nameFont", fontType, entityType);
        require(nameFont.invoke(null, null, victim) == red,
                "Blocked player names use the native red-bold sprite font");
        Object nonFont = nameFont.invoke(null, null, monster);
        require(nonFont == null, "Ordinary monster names retain the supplied native font");

        Object repeated = message(messageType, (byte)6, combatPacket(700, 701, 21, 0));
        invokeReceiver(receive, repeated, 0);
        require(((Integer)support.getMethod("count").invoke(null)).intValue() == 1,
                "Repeated final-hit packets do not duplicate a recorded player name");
        require(api.isInstance(access), "Generated native bridge implements BattlefieldAccess");
        require(idField.getInt(victim) == 701 && typeField.getByte(victim) == 0
                && levelField.getShort(victim) == 32 && hpField.getInt(local) > 0
                && nameField.get(victim).equals("Remote"),
                "Native bridge reads the expected local and remote entity fields");
    }

    private static void seedNativeFontMetrics(ClassLoader loader) throws Exception {
        Class<?> helper = Class.forName(NATIVE_HELPER);
        Class<?> probeType = Class.forName(NATIVE_HELPER + "$NativeProbeLoader");
        Method seed = helper.getDeclaredMethod("seedNativeFontMetrics", probeType);
        seed.setAccessible(true);
        seed.invoke(null, loader);
    }

    private static void invokeReceiver(Method receive, Object message, int targetType) throws Exception {
        Class<?>[] parameters = receive.getParameterTypes();
        Object receiver = Modifier.isStatic(receive.getModifiers()) ? null : allocate(receive.getDeclaringClass());
        Object[] args = new Object[parameters.length];
        args[0] = message;
        for (int i = 1; i < parameters.length; i++) args[i] = Byte.valueOf((byte)(i == parameters.length - 1 ? targetType : 0));
        try { receive.invoke(receiver, args); }
        catch (java.lang.reflect.InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new RuntimeException(cause);
        }
    }

    private static PacketHandlers findPacketHandlers(File candidate, ClassLoader loader) throws Exception {
        MethodInfo object = findSupportCall(candidate, "onObject");
        MethodInfo detail = findSupportCall(candidate, "onDetail");
        Method object4 = reflectedMethod(loader, object);
        Method detail7 = reflectedMethod(loader, detail);
        require(object4.getParameterTypes().length == 1 && detail7.getParameterTypes().length == 1,
                "Native packet 4/7 handlers each receive one message object");
        return new PacketHandlers(object4, detail7, object4.getParameterTypes()[0]);
    }

    private static MethodInfo findSupportCall(File candidate, String name) throws Exception {
        JarFile jar = new JarFile(candidate);
        MethodInfo found = null;
        try {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode owner = new ClassNode();
                new ClassReader(jar.getInputStream(entry)).accept(owner, 0);
                for (MethodNode method : owner.methods) for (AbstractInsnNode instruction : method.instructions.toArray())
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode)instruction;
                        if (CACHE_SUPPORT.equals(call.owner) && name.equals(call.name)) {
                            if (found != null) throw new AssertionError("Ambiguous native hook " + name);
                            found = new MethodInfo(owner.name, method.name, method.desc);
                        }
                    }
            }
        } finally { jar.close(); }
        if (found == null) throw new AssertionError("Missing native packet hook " + name);
        return found;
    }

    private static Method reflectedMethod(ClassLoader loader, MethodInfo info) throws Exception {
        Class<?> owner = loader.loadClass(info.owner.replace('/', '.'));
        Type[] types = Type.getArgumentTypes(info.descriptor);
        Class<?>[] args = new Class<?>[types.length];
        for (int i = 0; i < types.length; i++) args[i] = resolveType(types[i], loader);
        Method method = owner.getDeclaredMethod(info.name, args);
        method.setAccessible(true);
        return method;
    }

    private static Class<?> resolveType(Type type, ClassLoader loader) throws Exception {
        switch (type.getSort()) {
            case Type.VOID: return Void.TYPE;
            case Type.BOOLEAN: return Boolean.TYPE;
            case Type.BYTE: return Byte.TYPE;
            case Type.CHAR: return Character.TYPE;
            case Type.SHORT: return Short.TYPE;
            case Type.INT: return Integer.TYPE;
            case Type.FLOAT: return Float.TYPE;
            case Type.LONG: return Long.TYPE;
            case Type.DOUBLE: return Double.TYPE;
            case Type.ARRAY: return Class.forName(type.getDescriptor().replace('/', '.'), false, loader);
            case Type.OBJECT: return loader.loadClass(type.getClassName());
            default: throw new IllegalArgumentException(type.toString());
        }
    }

    private static Object nativeEntity(Object world, Class<?> worldType, int hp, byte kind,
            int id, String name, int level, int x, int y, Field idField, Field typeField,
            Field levelField, Field hpField, Field nameField) throws Exception {
        Object entity = call(world, "entity", new Class<?>[]{Integer.TYPE, Byte.TYPE,
                Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE},
                Integer.valueOf(hp), Byte.valueOf(kind), Integer.valueOf(0), Integer.valueOf(0),
                Integer.valueOf(0), Integer.valueOf(x), Integer.valueOf(y));
        setNumber(idField, entity, id);
        setNumber(typeField, entity, kind);
        setNumber(levelField, entity, level);
        setNumber(hpField, entity, hp);
        nameField.set(entity, name);
        field(entity.getClass(), "bt").setInt(entity, hp);
        setPosition(entity.getClass(), entity, x, y, x, y);
        return entity;
    }

    private static void setPacketReadyFlag(ClassLoader loader, File candidate, Properties catalog)
            throws Exception {
        MethodInfo object4 = findSupportCall(candidate, "onObject");
        JarFile jar = new JarFile(candidate);
        try {
            JarEntry entry = jar.getJarEntry(object4.owner + ".class");
            ClassNode owner = new ClassNode(); new ClassReader(jar.getInputStream(entry)).accept(owner, 0);
            for (MethodNode method : owner.methods) if (method.name.equals(object4.name)
                    && method.desc.equals(object4.descriptor)) {
                for (AbstractInsnNode instruction : method.instructions.toArray())
                    if (instruction instanceof FieldInsnNode && instruction.getOpcode() == Opcodes.GETSTATIC
                            && "Z".equals(((FieldInsnNode)instruction).desc)) {
                        FieldInsnNode flag = (FieldInsnNode)instruction;
                        field(loader.loadClass(flag.owner.replace('/', '.')), flag.name).setBoolean(null, true);
                        return;
                    }
            }
        } finally { jar.close(); }
        throw new AssertionError("Missing native object-handler ready flag");
    }

    private static Method nativeCombatReceiver(File candidate, ClassLoader loader,
            Properties battlefield, String namespace) throws Exception {
        String ownerName = required(battlefield, "combat.owner");
        String methodName = required(battlefield, "combat.method");
        String descriptor = required(battlefield, "combat.descriptor");
        Class<?> owner = loader.loadClass(ownerName.replace('/', '.'));
        Type[] types = Type.getArgumentTypes(descriptor);
        Class<?>[] args = new Class<?>[types.length];
        for (int i = 0; i < types.length; i++) args[i] = resolveType(types[i], loader);
        Method method = owner.getDeclaredMethod(methodName, args);
        method.setAccessible(true);
        return method;
    }

    private static void verifyMenuToggle(NativeProbe probe, Properties auto, String namespace,
            Class<?> support, boolean expected) throws Exception {
        verifyMenuToggle(probe, auto, namespace, support);
        require(((Boolean)support.getMethod("enabled").invoke(null)).booleanValue() == expected,
                "Native callback reaches requested Battlefield mode state");
    }

    private static void setPosition(Class<?> entityType, Object entity, int x, int y,
            int targetX, int targetY) throws Exception {
        field(entityType, "aZ").setInt(entity, x);
        field(entityType, "ba").setInt(entity, y);
        field(entityType, "bg").setInt(entity, targetX);
        field(entityType, "bh").setInt(entity, targetY);
    }

    private static void verifyMovementHookChain(File candidate, String namespace,
            Class<?> entityType, Object entity, String message) throws Exception {
        String bridgeOwner = qualify(namespace, BRIDGE);
        String entityOwner = Type.getInternalName(entityType);
        boolean checked = false;
        JarFile jar = new JarFile(candidate);
        try {
            for (Class<?> cursor = entity.getClass(); cursor != null
                    && entityType.isAssignableFrom(cursor); cursor = cursor.getSuperclass()) {
                Method update;
                try { update = cursor.getDeclaredMethod("a"); }
                catch (NoSuchMethodException absent) { continue; }
                JarEntry entry = jar.getJarEntry(cursor.getName().replace('.', '/') + ".class");
                require(entry != null, "Native movement owner is present in candidate: " + cursor.getName());
                ClassNode owner = new ClassNode();
                new ClassReader(jar.getInputStream(entry)).accept(owner, 0);
                boolean hooked = false;
                for (MethodNode method : owner.methods) {
                    if (!method.name.equals(update.getName()) || !method.desc.equals("()V")) continue;
                    for (AbstractInsnNode instruction : method.instructions.toArray())
                        if (instruction instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode)instruction;
                            if (call.getOpcode() == Opcodes.INVOKESTATIC
                                    && call.owner.equals(bridgeOwner) && call.name.equals("align")
                                    && call.desc.equals("(L" + entityOwner + ";)Z")) hooked = true;
                        }
                }
                require(hooked, message + " in " + cursor.getName());
                checked = true;
            }
        } finally { jar.close(); }
        require(checked, message + " is declared in the native entity hierarchy");
    }

    private static void requirePosition(Class<?> entityType, Object entity, int x, int y,
            String message) throws Exception {
        require(field(entityType, "aZ").getInt(entity) == x
                && field(entityType, "ba").getInt(entity) == y
                && field(entityType, "bg").getInt(entity) == x
                && field(entityType, "bh").getInt(entity) == y, message);
    }

    private static Object message(Class<?> messageType, byte[] payload) throws Exception {
        return message(messageType, (byte)4, payload);
    }

    private static Object message(Class<?> messageType, byte opcode, byte[] payload) throws Exception {
        return messageType.getConstructor(Byte.TYPE, byte[].class).newInstance(Byte.valueOf(opcode), payload);
    }

    private static byte[] packet4(int type, int id, int x, int y, int template, int state)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(type); out.writeShort(template); out.writeShort(id);
        out.writeShort(x); out.writeShort(y); out.writeByte(state); out.flush();
        return bytes.toByteArray();
    }

    private static byte[] packet7(int id, int level, int x, int y, int hp, int maxHp,
            int marker, int trailing) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(id); out.writeByte(level); out.writeShort(x); out.writeShort(y);
        out.writeInt(hp); out.writeInt(maxHp); out.writeByte(marker); out.writeInt(trailing);
        // Supply the receiver's neutral metadata tail after its onDetail hook:
        // UTF, short/int pair, UTF + byte, UTF + three bytes, UTF, long + byte.
        out.writeUTF(""); out.writeShort(0); out.writeInt(0);
        out.writeUTF(""); out.writeByte(0);
        out.writeUTF(""); out.writeByte(0); out.writeByte(0); out.writeByte(0);
        out.writeUTF(""); out.writeLong(0L); out.writeByte(0);
        out.flush();
        return bytes.toByteArray();
    }

    private static byte[] combatPacket(int attacker, int victim, int damage, int hp)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(attacker); out.writeByte(19); out.writeByte(1);
        out.writeShort(victim); out.writeInt(damage); out.writeInt(hp); out.writeByte(0);
        out.writeInt(100); out.writeInt(50); out.writeByte(0); out.writeInt(0);
        out.flush();
        return bytes.toByteArray();
    }

    private static Field nativeField(File candidate, String bridgeName, String methodName,
            String descriptor, Class<?> entityType) throws Exception {
        JarFile jar = new JarFile(candidate);
        try {
            String entryName = bridgeName + ".class";
            if (jar.getJarEntry(entryName) == null) {
                for (String slot : new String[]{"a/", "b/", "c/"})
                    if (jar.getJarEntry(slot + entryName) != null) { entryName = slot + entryName; break; }
            }
            JarEntry entry = jar.getJarEntry(entryName);
            ClassNode node = new ClassNode(); new ClassReader(jar.getInputStream(entry)).accept(node, 0);
            for (MethodNode method : node.methods) if (method.name.equals(methodName)
                    && method.desc.equals(descriptor)) for (AbstractInsnNode instruction : method.instructions.toArray())
                if (instruction instanceof FieldInsnNode && instruction.getOpcode() == Opcodes.GETFIELD) {
                    FieldInsnNode access = (FieldInsnNode)instruction;
                    if (access.owner.equals(entityType.getName().replace('.', '/')))
                        return field(entityType, access.name);
                }
        } finally { jar.close(); }
        throw new NoSuchFieldException("Native access field for " + methodName);
    }

    private static Object construct(Class<?> type, Class<?>[] parameters, Object... args) throws Exception {
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(parameters);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type);
    }

    private static Object call(Object receiver, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method method = receiver.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        try { return method.invoke(receiver, args); }
        catch (java.lang.reflect.InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new RuntimeException(cause);
        }
    }

    private static Method staticHelper(String name, Class<?>... args) throws Exception {
        Method method = NATIVE_HELPER_CLASS().getDeclaredMethod(name, args);
        method.setAccessible(true); return method;
    }

    private static int nativeActionValue(Object item) throws Exception {
        for (Field field : allFields(item.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if (field.getType() == Byte.TYPE && field.getByte(item) == (byte)-115) return -115;
            if (field.getType() == Short.TYPE && field.getShort(item) == (short)-115) return -115;
            if (field.getType() == Integer.TYPE && field.getInt(item) == -115) return -115;
        }
        throw new AssertionError("Native Battlefield row does not retain action -115");
    }

    private static String rowLabel(Object item) throws Exception {
        for (Field field : allFields(item.getClass())) {
            if (field.getType() != String.class) continue;
            field.setAccessible(true);
            Object value = field.get(item);
            if (value instanceof String && ((String)value).startsWith("Mode chiến trường:"))
                return (String)value;
        }
        throw new AssertionError("Native Battlefield row has no mode label");
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> current = type; current != null; current = current.getSuperclass())
            for (Field field : current.getDeclaredFields()) fields.add(field);
        return fields;
    }

    private static Class<?> NATIVE_HELPER_CLASS() throws ClassNotFoundException {
        return Class.forName(NATIVE_HELPER);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static void setNumber(Field field, Object target, int value) throws Exception {
        Class<?> type = field.getType();
        if (type == Byte.TYPE) field.setByte(target, (byte)value);
        else if (type == Short.TYPE) field.setShort(target, (short)value);
        else if (type == Integer.TYPE) field.setInt(target, value);
        else if (type == Long.TYPE) field.setLong(target, value);
        else throw new IllegalArgumentException("Unsupported numeric field " + field);
    }

    private static Properties load(File path) throws Exception {
        Properties properties = new Properties(); FileInputStream input = new FileInputStream(path);
        try { properties.load(input); } finally { input.close(); }
        return properties;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.length() == 0) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private static String qualify(String namespace, String internalName) {
        if (internalName == null) throw new IllegalArgumentException("Missing owner");
        if (internalName.indexOf('.') >= 0) return internalName;
        if (internalName.startsWith("a/") || internalName.startsWith("b/") || internalName.startsWith("c/"))
            return internalName;
        if (!"root".equals(namespace) && internalName.startsWith("luna/"))
            return namespace + "." + internalName;
        return internalName;
    }

    private static boolean hasEntry(File file, String name) throws Exception {
        JarFile jar = new JarFile(file); try { return jar.getJarEntry(name) != null; }
        finally { jar.close(); }
    }

    private static String hash(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        java.io.FileInputStream input = new java.io.FileInputStream(file);
        try {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
        } finally { input.close(); }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02X", value & 255));
        return result.toString();
    }

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static final class NativeProbe implements AutoCloseable {
        final File candidate;
        final URLClassLoader loader;
        final Object nativeProbe;
        NativeProbe(File candidate, Properties auto) throws Exception {
            this.candidate = candidate;
            Class<?> helper = NATIVE_HELPER_CLASS();
            Class<?> type = Class.forName(NATIVE_HELPER + "$NativeProbeLoader");
            nativeProbe = construct(type, new Class<?>[]{File.class, Properties.class}, candidate, auto);
            loader = (URLClassLoader)nativeProbe;
        }
        public void close() throws Exception { loader.close(); }
    }

    private static final class MethodInfo {
        final String owner, name, descriptor;
        MethodInfo(String owner, String name, String descriptor) {
            this.owner = owner; this.name = name; this.descriptor = descriptor;
        }
    }

    private static final class PacketHandlers {
        final Method object4, detail7;
        final Class<?> messageType;
        PacketHandlers(Method object4, Method detail7, Class<?> messageType) {
            this.object4 = object4; this.detail7 = detail7; this.messageType = messageType;
        }
    }
}
