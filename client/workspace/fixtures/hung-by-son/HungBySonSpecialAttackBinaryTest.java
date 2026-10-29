import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Candidate-level check for the optional synchronous special-attack route. */
public final class HungBySonSpecialAttackBinaryTest {
    private static final String HOST = "HungBySonAutoAttackBinaryTest";
    private static final int TOGGLE = -111, MONSTERS = -112, PK = -113, SPECIAL = -116;
    private static int checks;

    private HungBySonSpecialAttackBinaryTest() { }

    public static void main(String[] args) throws Exception {
        require(args.length == 3, "candidate.jar auto-attack.properties special-attack.properties");
        File candidate = new File(args[0]);
        Properties symbols = load(new File(args[1]));
        symbols.putAll(load(new File(args[2])));
        require(candidate.isFile(), "Special-attack candidate exists");
        require(HungBySonSpecialAttackBinaryTest.hash(candidate).equalsIgnoreCase(required(symbols, "output.sha256")),
                "Candidate bytes match the special catalog");
        require("-116".equals(required(symbols, "special.menu.action")), "Catalog reserves native action -116");

        verifyGeneratedDirectRoute(candidate, symbols);
        try (Fixture fixture = new Fixture(candidate, symbols)) {
            verifyMenuAndRuntime(fixture, symbols);
            verifyNativeSpecialSend(fixture, symbols);
            verifyMovementPkAndInvalidTarget(fixture, symbols);
        }
        try (Fixture fixture = new Fixture(candidate, symbols)) {
            fixture.installReadyWorld();
            verifyPkAndInvalidTarget(fixture, symbols);
        }
        System.out.println("HungBySonSpecialAttackBinaryTest PASS checks=" + checks
                + "; immediate opcode-6/9 sends, native target builder, cooldown/MP, 600ms gate, movement and PK");
    }

    private static void verifyGeneratedDirectRoute(File candidate, Properties p) throws Exception {
        String owner = required(p, "special.cast.owner");
        JarFile jar = new JarFile(candidate);
        ClassNode node = new ClassNode();
        try {
            java.util.jar.JarEntry entry = jar.getJarEntry(owner + ".class");
            require(entry != null, "Catalog cast owner exists");
            new ClassReader(jar.getInputStream(entry)).accept(node, 0);
        } finally { jar.close(); }
        MethodNode direct = null;
        for (MethodNode method : node.methods)
            if (required(p, "special.send.method").equals(method.name) && "()V".equals(method.desc)) direct = method;
        require(direct != null, "Cast exposes the synchronous direct-send method");
        int targetBuilder = 0, cooldown = 0, nativeSerializer = 0, requestCall = 0;
        java.util.HashSet<String> serializerNames = new java.util.HashSet<String>();
        for (org.objectweb.asm.tree.AbstractInsnNode instruction : direct.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.owner.equals(required(p, "native.skill.owner"))
                    && call.name.equals(required(p, "special.targets.method"))
                    && call.desc.equals("(IL" + required(p, "native.entity.owner") + ";)Let;"))
                targetBuilder++;
            // ModUpLv port: the same builder is reached through the map-wide target wrapper.
            if (p.getProperty("modulv.port.owner") != null && call.owner.equals(p.getProperty("modulv.port.owner"))
                    && call.name.equals("specialTargets")
                    && call.desc.equals("(L" + required(p, "native.skill.owner") + ";IL" + required(p, "native.entity.owner") + ";)Let;"))
                targetBuilder++;
            if (call.owner.equals(required(p, "native.skill.owner"))
                    && call.name.equals(required(p, "special.cooldown.method")) && "(II)V".equals(call.desc))
                cooldown++;
            if ("(Let;B)V".equals(call.desc)) { nativeSerializer++; serializerNames.add(call.name); }
            if (call.owner.equals(owner) && call.name.equals(required(p, "request.method"))) requestCall++;
        }
        require(targetBuilder == 1, "Direct send reuses the native target fanout builder once");
        require(cooldown == 1, "Direct send reuses native cooldown and mana bookkeeping once");
        require(nativeSerializer == 2 && serializerNames.size() == 2,
                "Direct send retains the distinct native opcode-6 and opcode-9 serializers");
        require(requestCall == 0, "Direct send does not enqueue through the animation-timed request callback");
    }

    private static void verifyMenuAndRuntime(Fixture f, Properties p) throws Exception {
        f.installReadyWorld();
        require(!bool(f.runtime, "specialEnabled") && !bool(f.runtime, "enabled"),
                "Special and Auto Attack both start OFF");
        require(!bool(f.runtime, "specialActive"), "specialActive is false outside a starting attack");
        require(String.valueOf(call(f.runtime, "specialLabel")).contains("Tấn công đặc biệt"),
                "Runtime supplies the special menu label");

        invokeAction(f.runtime, SPECIAL);
        require(bool(f.runtime, "specialEnabled") && !bool(f.runtime, "enabled"),
                "Special toggles independently and never enables Auto Attack");
        f.bridgeType.getMethod("showMenu", Boolean.TYPE, Boolean.TYPE, Integer.TYPE)
                .invoke(f.bridge, Boolean.FALSE, Boolean.FALSE, Integer.valueOf(1000));
        Object popup = call(f.probe, "lastPopup");
        Object items = field(popup.getClass(), "items").get(popup);
        int count = ((Integer)host("listSize", new Class<?>[]{Object.class}, items)).intValue();
        int expected = p.getProperty("normal.monsters.action") == null ? 6 : 7;
        require(count == expected, "Battlefield submenu adds a sixth special-attack row (seventh: normal monsters); rows=" + count);
        Object specialRow = null;
        for (int i = 0; i < count; i++) {
            Object row = host("listGet", new Class<?>[]{Object.class, Integer.TYPE}, items, Integer.valueOf(i));
            if (action(row) == SPECIAL) specialRow = row;
        }
        require(specialRow != null && rowLabel(specialRow).contains("Tấn công đặc biệt"),
                "Native submenu contains the special toggle with action -116");
        Method click = (Method)host("findNativeClick", new Class<?>[]{Class.class}, specialRow.getClass());
        click.setAccessible(true);
        click.invoke(specialRow);
        require(!bool(f.runtime, "specialEnabled") && !bool(f.runtime, "enabled"),
                "The real native row callback toggles Special OFF without enabling Auto Attack");
        click.invoke(specialRow);
        require(bool(f.runtime, "specialEnabled") && !bool(f.runtime, "enabled"),
                "The real native row callback toggles Special ON without enabling Auto Attack");
        require(f.specialActionObserved(SPECIAL), "Native callback receives signed action -116");
    }

    private static void verifyNativeSpecialSend(Fixture f, Properties p) throws Exception {
        Class<?> playerType = Class.forName(required(p, "native.player.owner").replace('/', '.'), true, f.loader);
        Object definition = host("seedNativeSkill", new Class<?>[]{f.probeType, Class.class, Properties.class},
                f.probe, playerType, p);
        configureSpecialSkill(f, p, playerType, definition, 7, 10);

        Object primary = f.entity(100, (byte)1, 2, 0, 0, 20, 30);
        Object secondary = f.entity(101, (byte)1, 2, 0, 0, 23, 30);
        Object dead = f.entity(0, (byte)1, 2, 0, 0, 24, 30);
        Object excluded = f.entity(90, (byte)1, 2, 1, 0, 25, 30);
        Object corpse = f.entity(90, (byte)1, 4, 0, 0, 26, 30);
        Object ghost = f.entity(90, (byte)1, 2, 0, 0, 27, 30);
        Object friendly = f.entity(90, (byte)0, 2, 0, 0, 28, 30);
        Object hostile = f.entity(90, (byte)0, 2, 0, 1, 29, 30);
        set(primary, primary.getClass(), "cu", Integer.valueOf(100));
        set(secondary, secondary.getClass(), "cu", Integer.valueOf(101));
        set(dead, dead.getClass(), "cu", Integer.valueOf(102));
        set(excluded, excluded.getClass(), "cu", Integer.valueOf(103));
        set(corpse, corpse.getClass(), "cu", Integer.valueOf(104));
        set(ghost, ghost.getClass(), "cu", Integer.valueOf(105));
        set(friendly, friendly.getClass(), "cu", Integer.valueOf(106));
        set(hostile, hostile.getClass(), "cu", Integer.valueOf(107));
        Field phase = field(ghost.getClass(), required(p, "native.entity.phase.field"));
        if (phase.getType() == Byte.TYPE) phase.setByte(ghost, (byte)2);
        else if (phase.getType() == Integer.TYPE) phase.setInt(ghost, 2);
        else if (phase.getType() == Boolean.TYPE) phase.setBoolean(ghost, true);
        f.replaceEntities(new Object[]{primary, secondary, dead, excluded, corpse, ghost, friendly, hostile});

        set(f.worldPlayer(), playerType, "cA", Integer.valueOf(-1));
        set(f.worldPlayer(), playerType, "cG", Integer.valueOf(0));
        set(f.worldPlayer(), playerType, "ef", Byte.valueOf((byte)1));
        set(f.worldPlayer(), playerType, "cw", Byte.valueOf((byte)1));
        set(f.worldPlayer(), playerType, "bu", Integer.valueOf(100));
        setStatic(playerType, "e", Integer.valueOf(1));
        Class<?> mapperType = f.loader.loadClass(f.encoded("liillil"));
        Method mappedSkill = method(mapperType, f.encoded("liili"), "(I)" + typeDescriptor(definition.getClass()));
        require(invoke(mappedSkill, null, Integer.valueOf(7)) == definition,
                "Native skill registry resolves hotbar skill 7 to its real definition");
        Method eligibility = method(playerType, required(p, "special.eligibility.method"), "(II)Z");
        require(Boolean.TRUE.equals(invoke(eligibility, f.worldPlayer(), Integer.valueOf(7), Integer.valueOf(1))),
                "Native hotbar eligibility accepts the learned special skill with enough MP and no cooldown");

        // The configured Auto Attack delay is 1000ms and the native request clock is
        // deliberately inside a 5000ms native interval. Special still sends from cast init.
        long now = 1L;
        f.clockNow = now;
        long oldRequestTimestamp = System.currentTimeMillis() + 60000L;
        f.setNativeRequestClock(oldRequestTimestamp, (short)5000);
        invokeAction(f.runtime, MONSTERS);
        if (!bool(f.runtime, "specialEnabled")) invokeAction(f.runtime, SPECIAL);
        set(f.world, f.world.getClass(), "runNativeSkill", Boolean.TRUE);
        require(integer(f.runtime, "delayMs") == 1000, "Special test retains a configured 1000ms delay");
        require(bool(f.runtime, "enabled") && bool(f.runtime, "specialEnabled"),
                "Master and special mode are independently active");

        int before = f.attackPacketCount();
        call(f.runtime, "tick"); // enabling already observed the world; Special bypasses 1000ms here
        require(f.attackObserved && f.specialActiveObserved,
                "Special is active only around the controller-owned attack call");
        require(f.worldAttackCalls() == 1 && Boolean.TRUE.equals(f.worldSkillResult()),
                "Native hotbar/chooser starts one eligible special skill; calls=" + f.worldAttackCalls()
                + " result=" + f.worldSkillResult() + " failure=" + f.worldSkillFailure()
                + " target=" + (call(f.runtime, "target") == primary) + " enabled=" + bool(f.runtime, "enabled")
                + " active=" + f.specialActiveObserved + " slot=" + field(playerType, "codexAutoAttackSlot").getByte(null)
                + " castN=" + field(f.nativeCast(p).getClass(), "n").getInt(f.nativeCast(p))
                + " castK=" + field(f.nativeCast(p).getClass(), "k").getInt(f.nativeCast(p)));
        require(f.attackPacketCount() == before + 1 && f.packetHasOpcode(9),
                "Cast init synchronously sends exactly one monster opcode-9 packet before any animation request");
        require(f.specialSkillChecks != null && f.specialSkillChecks[0] == 1,
                "The allowed native hotbar skill ID 7 reaches direct send");

        Object cast = f.nativeCast(p);
        Class<?> castType = cast.getClass();
        require(field(castType, required(p, "special.sent.field")).getBoolean(cast),
                "Successful direct send marks the native cast as already sent");
        require(field(castType, required(p, "request.tag.field")).getInt(cast) == -1,
                "Successful direct send cancels its animation callback generation");
        require(field(playerType, "cG").getInt(f.worldPlayer()) == 0
                        && field(playerType, "cA").getInt(f.worldPlayer()) == -1,
                "Direct send releases native busy state before writing the cast animation frame");

        String packets = f.nativePacketSnapshot();
        require(packets.contains("9:[7, 2, 0, 100, 0, 101]"),
                "Direct packet uses the unchanged native fanout for primary plus one eligible secondary; " + packets);
        require(!packets.contains("102") && !packets.contains("103") && !packets.contains("104")
                        && !packets.contains("105") && !packets.contains("106") && !packets.contains("107"),
                "Native fanout excludes dead, excluded, corpse, ghost, and PK-mismatched entities");

        require(field(playerType, "bu").getInt(f.worldPlayer()) == 90,
                "Native cooldown/MP bookkeeping spends the configured 10 MP; MP="
                + field(playerType, "bu").getInt(f.worldPlayer()) + " cast.k="
                + field(castType, "k").getInt(cast) + " cast.n=" + field(castType, "n").getInt(cast));
        Object[] cooldowns = (Object[])field(playerType, f.encoded("lliillli")).get(null);
        require(cooldowns.length > 7 && field(cooldowns[7].getClass(), "d").getBoolean(cooldowns[7]),
                "Native cooldown bookkeeping marks skill 7 as used");

        int afterDirect = f.attackPacketCount();
        Method callback = method(castType, required(p, "request.method"), "()V");
        invoke(callback, cast);
        require(f.attackPacketCount() == afterDirect,
                "The later native send callback cannot duplicate a successful direct packet");
        require(f.nativeRequestTimestamp() != oldRequestTimestamp,
                "Direct send updates the native request clock for later native casts");

        // Verify the exact strict 600ms gate while specialActive is true. The callback
        // samples all relevant IDs at the top of each controller-owned attack attempt.
        f.clockNow = now + 600L;
        call(f.runtime, "tick");
        require(f.specialSkillChecks != null && f.specialSkillChecks[1] == 0 && f.specialSkillChecks[2] == 0
                        && f.specialSkillChecks[3] == 0 && f.specialSkillChecks[4] == 0
                        && f.specialSkillChecks[0] == 1 && f.specialSkillChecks[5] == 1,
                "At exactly 600ms only IDs 7/8/19/20 remain gated");
        int at600 = f.attackPacketCount();
        f.clockNow = now + 601L;
        call(f.runtime, "tick");
        require(f.specialSkillChecks[1] == 1 && f.specialSkillChecks[2] == 1
                        && f.specialSkillChecks[3] == 1 && f.specialSkillChecks[4] == 1,
                "After 600ms the four special IDs are eligible again");
        require(f.attackPacketCount() >= at600,
                "The 600ms special cadence does not regress native packet counting");
    }

    private static void verifyMovementPkAndInvalidTarget(Fixture f, Properties p) throws Exception {
        int before = f.attackPacketCount();
        int generation = integer(f.runtime, "generation");
        call(f.runtime, "manualMove");
        require(!boolArg(f.runtime, "allowRequest", generation, call(f.runtime, "target")),
                "Movement invalidates a pending special request");
        call(f.runtime, "tick");
        int calls = f.worldAttackCalls();
        call(f.runtime, "tick");
        require(f.worldAttackCalls() >= calls && f.attackPacketCount() == before,
                "Movement and the 100ms resume transition cannot emit an old special packet");

    }

    private static void verifyPkAndInvalidTarget(Fixture f, Properties p) throws Exception {
        // PK mode keeps the current native hostile-player predicate and serializer.
        Class<?> playerType = Class.forName(required(p, "native.player.owner").replace('/', '.'), true, f.loader);
        Object definition = host("seedNativeSkill", new Class<?>[]{f.probeType, Class.class, Properties.class},
                f.probe, playerType, p);
        configureSpecialSkill(f, p, playerType, definition, 7, 10);
        Object hostile = f.entity(100, (byte)0, 2, 0, 1, 20, 30);
        Object friendly = f.entity(100, (byte)0, 2, 0, 0, 18, 30);
        Object corpse = f.entity(0, (byte)1, 2, 0, 0, 20, 30);
        set(hostile, hostile.getClass(), "cu", Integer.valueOf(210));
        set(friendly, friendly.getClass(), "cu", Integer.valueOf(211));
        set(corpse, corpse.getClass(), "cu", Integer.valueOf(220));
        f.replaceEntities(new Object[]{hostile, friendly});
        set(f.worldPlayer(), playerType, "cA", Integer.valueOf(-1));
        set(f.worldPlayer(), playerType, "cG", Integer.valueOf(0));
        set(f.worldPlayer(), playerType, "ef", Byte.valueOf((byte)1));
        set(f.worldPlayer(), playerType, "cw", Byte.valueOf((byte)1));
        setStatic(playerType, "e", Integer.valueOf(1));
        f.restoreNativePkGate();
        invokeAction(f.runtime, PK);
        if (!bool(f.runtime, "specialEnabled")) invokeAction(f.runtime, SPECIAL);
        set(f.world, f.world.getClass(), "runNativeSkill", Boolean.TRUE);
        set(f.worldPlayer(), playerType, "bu", Integer.valueOf(100));
        host("seedNativeFontMetrics", new Class<?>[]{f.probeType}, f.probe);
        f.clockNow = 1L;
        Method valid = f.bridgeType.getMethod("validTarget", Object.class, Boolean.TYPE);
        Object hostileValid = valid.invoke(f.bridge, hostile, Boolean.TRUE);
        Object friendlyValid = valid.invoke(f.bridge, friendly, Boolean.TRUE);
        require(Boolean.TRUE.equals(hostileValid) && Boolean.FALSE.equals(friendlyValid),
                "Native PK validity admits the hostile player and rejects the friendly player; hostile="
                + hostileValid + " friendly=" + friendlyValid + " hostility="
                + field(hostile.getClass(), "dL").getBoolean(hostile) + "/"
                + field(friendly.getClass(), "dL").getBoolean(friendly) + " localLevel="
                + field(playerType, "e").getInt(null));
        call(f.runtime, "tick");
        require(call(f.runtime, "target") == hostile,
                "PK mode retains the native hostile-player target filter; target=" + call(f.runtime, "target")
                + " hostile=" + hostile + " enabled=" + bool(f.runtime, "enabled")
                + " players=" + bool(f.runtime, "playersEnabled") + " worldCalls=" + f.worldAttackCalls()
                + " valid=" + f.bridgeType.getMethod("validTarget", Object.class, Boolean.TYPE)
                    .invoke(f.bridge, hostile, Boolean.TRUE));
        require(f.attackPacketCount() == 1 && f.packetHasOpcode(6),
                "A valid hostile player uses the native opcode-6 serializer; packets=" + f.nativePacketSnapshot()
                + " calls=" + f.worldAttackCalls() + " result=" + f.worldSkillResult() + " failure="
                + f.worldSkillFailure() + " target=" + (call(f.runtime, "target") == hostile) + " enabled="
                + bool(f.runtime, "enabled") + " slot=" + field(playerType, "codexAutoAttackSlot").getByte(null));

        // A corpse cannot receive a fresh direct cast even while Special remains ON.
        f.replaceEntities(new Object[]{corpse});
        require(Boolean.FALSE.equals(valid.invoke(f.bridge, corpse, Boolean.TRUE)),
                "The native valid-target gate rejects a dead entity in PK mode");
        int afterPk = f.attackPacketCount();
        call(f.runtime, "tick");
        require(f.attackPacketCount() == afterPk,
                "The native valid-target gate blocks a dead target from direct send");
    }

    private static void configureSpecialSkill(Fixture f, Properties p, Class<?> playerType,
            Object definition, int skillId, int mpCost) throws Exception {
        set(definition, definition.getClass(), "b", Integer.valueOf(skillId));
        Field hotbarField = field(playerType, f.encoded("lliliiil"));
        Object hotbar = hotbarField.get(null);
        Object row = Array.get(hotbar, 0);
        Object cell = Array.get(row, 1);
        set(cell, cell.getClass(), f.encoded("lilllli"), Short.valueOf((short)skillId));

        int[] learned = (int[])field(playerType, f.encoded("llilllii")).get(null);
        if (learned.length <= skillId) learned = Arrays.copyOf(learned, skillId + 1);
        learned[skillId] = 1;
        setStatic(playerType, f.encoded("llilllii"), learned);
        int[] levels = (int[])field(playerType, f.encoded("llilllil")).get(null);
        if (levels.length <= skillId) levels = Arrays.copyOf(levels, skillId + 1);
        levels[skillId] = 0;
        setStatic(playerType, f.encoded("llilllil"), levels);

        // This native table maps the skill ID in n to the active skill token in k.
        // Skill 7 uses native token 42 on this pinned JAR; seeding it lets the actual
        // cooldown helper exercise its regular success branch in the fixture.
        String cooldownMapName = f.encoded("llillill");
        int[] cooldownMap = (int[])field(playerType, cooldownMapName).get(null);
        if (cooldownMap.length <= skillId) cooldownMap = Arrays.copyOf(cooldownMap, skillId + 1);
        cooldownMap[skillId] = 42;
        setStatic(playerType, cooldownMapName, cooldownMap);

        Field cooldownsField = field(playerType, f.encoded("lliillli"));
        Object cooldowns = cooldownsField.get(null);
        int oldLength = cooldowns == null ? 0 : Array.getLength(cooldowns);
        if (oldLength <= skillId) {
            Object larger = Array.newInstance(cooldownsField.getType().getComponentType(), skillId + 1);
            if (cooldowns != null) System.arraycopy(cooldowns, 0, larger, 0, oldLength);
            cooldowns = larger;
            setStatic(playerType, cooldownsField.getName(), cooldowns);
        }
        Class<?> cooldownType = cooldownsField.getType().getComponentType();
        for (int i = 0; i <= skillId; i++) if (Array.get(cooldowns, i) == null)
            Array.set(cooldowns, i, host("unsafeAllocate", new Class<?>[]{Class.class}, cooldownType));
        setNumber(field(Array.get(cooldowns, skillId).getClass(), "b"), Array.get(cooldowns, skillId), 0);

        Field costsField = field(definition.getClass(), f.encoded("lllillllil"));
        Object costs = costsField.get(definition);
        Object cost = costs.getClass().isArray() ? Array.get(costs, 0) : costs;
        set(cost, cost.getClass(), f.encoded("lilllli"), Short.valueOf((short)mpCost));
        set(cost, cost.getClass(), "j", Byte.valueOf((byte)4));
        set(cost, cost.getClass(), f.encoded("lllliiliil"), Short.valueOf((short)2000));
        // The one real cost record now represents the selected special skill.
        require(skillId == 7, "Fixture uses a special hotbar skill ID");
    }

    private static void setNumber(Field field, Object target, int value) throws Exception {
        if (field.getType() == Integer.TYPE) field.setInt(target, value);
        else if (field.getType() == Short.TYPE) field.setShort(target, (short)value);
        else if (field.getType() == Byte.TYPE) field.setByte(target, (byte)value);
        else if (field.getType() == Long.TYPE) field.setLong(target, value);
        else throw new AssertionError("Not a numeric field: " + field);
    }

    private static boolean boolArg(Class<?> type, String name, int tag, Object target) throws Exception {
        return ((Boolean)type.getMethod(name, Integer.TYPE, Object.class)
                .invoke(null, Integer.valueOf(tag), target)).booleanValue();
    }

    private static void invokeAction(Class<?> runtime, int action) throws Exception {
        require(Boolean.TRUE.equals(runtime.getMethod("handleAction", Integer.TYPE)
                .invoke(null, Integer.valueOf(action))), "Runtime handles menu action " + action);
    }

    private static Object call(Class<?> owner, String name, Object... args) throws Exception {
        Class<?>[] types = types(args);
        try { return owner.getMethod(name, types).invoke(null, args); }
        catch (java.lang.reflect.InvocationTargetException error) { throw cause(error); }
    }

    private static Object call(Object receiver, String name, Object... args) throws Exception {
        Class<?>[] types = types(args);
        Method method = null;
        for (Class<?> type = receiver.getClass(); type != null && method == null; type = type.getSuperclass())
            try { method = type.getDeclaredMethod(name, types); } catch (NoSuchMethodException ignored) { }
        if (method == null) throw new NoSuchMethodException(receiver.getClass().getName() + "." + name);
        method.setAccessible(true);
        try { return method.invoke(receiver, args); }
        catch (java.lang.reflect.InvocationTargetException error) { throw cause(error); }
    }

    private static Class<?>[] types(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++)
            types[i] = args[i] instanceof Integer ? Integer.TYPE
                    : args[i] instanceof Boolean ? Boolean.TYPE
                    : args[i] instanceof Byte ? Byte.TYPE
                    : args[i] instanceof Short ? Short.TYPE
                    : args[i] instanceof Long ? Long.TYPE : args[i].getClass();
        return types;
    }

    private static Object host(String name, Class<?>[] types, Object... args) throws Exception {
        return invoke(hostMethod(name, types), null, args);
    }

    private static Method hostMethod(String name, Class<?>... types) throws Exception {
        Method method = Class.forName(HOST).getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try { return method.invoke(receiver, args); }
        catch (java.lang.reflect.InvocationTargetException error) { throw cause(error); }
    }

    private static Exception cause(java.lang.reflect.InvocationTargetException error) throws Exception {
        Throwable cause = error.getCause();
        if (cause instanceof Exception) return (Exception)cause;
        if (cause instanceof Error) throw (Error)cause;
        return error;
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) try {
            Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static Method method(Class<?> owner, String name, String descriptor) throws Exception {
        for (Method candidate : owner.getDeclaredMethods()) {
            if (!candidate.getName().equals(name)) continue;
            StringBuilder desc = new StringBuilder("(");
            for (Class<?> parameter : candidate.getParameterTypes()) desc.append(typeDescriptor(parameter));
            desc.append(')').append(typeDescriptor(candidate.getReturnType()));
            if (descriptor.equals(desc.toString())) { candidate.setAccessible(true); return candidate; }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name + descriptor);
    }

    private static String typeDescriptor(Class<?> type) {
        if (type == Void.TYPE) return "V"; if (type == Boolean.TYPE) return "Z"; if (type == Byte.TYPE) return "B";
        if (type == Short.TYPE) return "S"; if (type == Integer.TYPE) return "I"; if (type == Long.TYPE) return "J";
        if (type == Float.TYPE) return "F"; if (type == Double.TYPE) return "D"; if (type == Character.TYPE) return "C";
        return type.isArray() ? type.getName().replace('.', '/') : "L" + type.getName().replace('.', '/') + ";";
    }

    private static void set(Object object, Class<?> owner, String name, Object value) throws Exception {
        Field target = field(owner, name);
        if (target.getType() == Boolean.TYPE) target.setBoolean(object, ((Boolean)value).booleanValue());
        else if (target.getType() == Byte.TYPE) target.setByte(object, ((Byte)value).byteValue());
        else if (target.getType() == Short.TYPE) target.setShort(object, ((Short)value).shortValue());
        else if (target.getType() == Integer.TYPE) target.setInt(object, ((Integer)value).intValue());
        else if (target.getType() == Long.TYPE) target.setLong(object, ((Long)value).longValue());
        else target.set(object, value);
    }

    private static void setStatic(Class<?> owner, String name, Object value) throws Exception {
        set(null, owner, name, value);
    }

    private static int action(Object row) throws Exception {
        for (Field field : allFields(row.getClass())) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            int value;
            if (field.getType() == Integer.TYPE) value = field.getInt(row);
            else if (field.getType() == Short.TYPE) value = field.getShort(row);
            else if (field.getType() == Byte.TYPE) value = field.getByte(row);
            else continue;
            if (value == SPECIAL) return value;
        }
        return Integer.MAX_VALUE;
    }

    private static String rowLabel(Object row) throws Exception {
        for (Field field : allFields(row.getClass())) if (field.getType() == String.class) {
            field.setAccessible(true); Object value = field.get(row);
            if (value instanceof String && ((String)value).contains("Tấn công đặc biệt")) return (String)value;
        }
        return "";
    }

    private static Field[] allFields(Class<?> owner) {
        java.util.ArrayList<Field> fields = new java.util.ArrayList<Field>();
        for (Class<?> type = owner; type != null; type = type.getSuperclass())
            fields.addAll(Arrays.asList(type.getDeclaredFields()));
        return fields.toArray(new Field[fields.size()]);
    }

    private static boolean bool(Class<?> runtime, String name) throws Exception {
        return ((Boolean)call(runtime, name)).booleanValue();
    }

    private static int integer(Class<?> runtime, String name) throws Exception {
        return ((Integer)call(runtime, name)).intValue();
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) throw new AssertionError("Missing catalog property " + key);
        return value;
    }

    private static Properties load(File file) throws Exception {
        Properties p = new Properties();
        FileInputStream in = new FileInputStream(file);
        try { p.load(in); } finally { in.close(); }
        return p;
    }

    private static String hash(File file) throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder();
        for (byte item : digest) value.append(String.format("%02X", item & 255));
        return value.toString();
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture implements AutoCloseable {
        final File candidate;
        final Properties catalog;
        final Class<?> probeType, bridgeType, runtime, accessType;
        final Object probe, bridge, world;
        final ClassLoader loader;
        volatile long clockNow;
        volatile int[] specialSkillChecks;
        volatile boolean attackObserved, specialActiveObserved;
        private final Field nativeActions;

        Fixture(File candidate, Properties catalog) throws Exception {
            this.candidate = candidate;
            this.catalog = catalog;
            probeType = Class.forName(HOST + "$NativeProbeLoader");
            Constructor<?> probeCtor = probeType.getDeclaredConstructor(File.class, Properties.class);
            probeCtor.setAccessible(true);
            probe = probeCtor.newInstance(candidate, catalog);
            loader = (ClassLoader)probe;
            bridgeType = loader.loadClass(required(catalog, "bridge.owner").replace('/', '.'));
            bridge = bridgeType.getDeclaredConstructor().newInstance();
            runtime = loader.loadClass("luna.features.AutoAttackRuntime");
            accessType = loader.loadClass("luna.api.AutoAttackAccess");
            Class<?> settingsType = loader.loadClass("luna.api.AutoAttackSettings");
            Class<?> clockType = loader.loadClass("luna.api.LunaClock");

            Class<?> nativeAccessType = Class.forName(HOST + "$NativeAccess");
            Constructor<?> accessCtor = nativeAccessType.getDeclaredConstructor(ClassLoader.class, Object.class, Properties.class);
            accessCtor.setAccessible(true);
            world = accessCtor.newInstance(loader, bridge, catalog);
            nativeActions = field(Class.forName(HOST), "nativeActions");
            nativeActions.setAccessible(true);

            Object proxyAccess = Proxy.newProxyInstance(loader, new Class<?>[]{accessType}, new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if ("attack".equals(method.getName())) {
                        attackObserved = true;
                        specialActiveObserved = bool(runtime, "specialActive");
                        int[] ids = new int[]{6, 7, 8, 19, 20, 21};
                        specialSkillChecks = new int[ids.length];
                        for (int i = 0; i < ids.length; i++)
                            specialSkillChecks[i] = ((Boolean)runtime.getMethod("specialSkillAllowed", Integer.TYPE)
                                    .invoke(null, Integer.valueOf(ids[i]))).booleanValue() ? 1 : 0;
                    }
                    return ((InvocationHandler)world).invoke(proxy, method, args);
                }
            });
            Object settings = Proxy.newProxyInstance(loader, new Class<?>[]{settingsType}, new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    if ("load".equals(method.getName())) {
                        int[] out = (int[])args[0]; out[0] = 0; out[1] = 1000;
                    }
                    return null;
                }
            });
            Object clock = Proxy.newProxyInstance(loader, new Class<?>[]{clockType}, new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    return "nowMillis".equals(method.getName()) ? Long.valueOf(clockNow) : defaultValue(method.getReturnType());
                }
            });
            runtime.getMethod("installForTest", accessType, settingsType, clockType)
                    .invoke(null, proxyAccess, settings, clock);
            // The Auto đánh/Farm Special path under test runs with Tấn công quái thường OFF
            // (ON by default since the ModUpLv attack loop).
            try {
                if (((String)runtime.getMethod("normalLabel").invoke(null)).endsWith("[BẬT]"))
                    runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-117));
            } catch (NoSuchMethodException older) { }
        }

        public void close() throws Exception { ((java.io.Closeable)probe).close(); }
        boolean specialActionObserved(int action) throws Exception {
            return ((List<?>)nativeActions.get(null)).contains(Integer.valueOf(action));
        }
        Object entity(int hp, byte type, int state, int excluded, int hostility, int x, int y) throws Exception {
            Method m = world.getClass().getDeclaredMethod("entity", Integer.TYPE, Byte.TYPE, Integer.TYPE,
                    Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
            m.setAccessible(true);
            return invoke(m, world, Integer.valueOf(hp), Byte.valueOf(type), Integer.valueOf(state),
                    Integer.valueOf(excluded), Integer.valueOf(hostility), Integer.valueOf(x), Integer.valueOf(y));
        }
        void replaceEntities(Object[] values) throws Exception {
            Method m = world.getClass().getDeclaredMethod("replaceEntities", Object[].class); m.setAccessible(true);
            invoke(m, world, (Object)values);
        }
        Object worldPlayer() throws Exception { return field(world.getClass(), "player").get(world); }
        int worldAttackCalls() throws Exception { return field(world.getClass(), "attackCalls").getInt(world); }
        Object worldSkillResult() throws Exception { return field(world.getClass(), "nativeSkillResult").get(world); }
        Object worldSkillFailure() throws Exception { return field(world.getClass(), "nativeSkillFailure").get(world); }
        Object nativeCast(Properties p) throws Exception {
            return field(worldPlayer().getClass(), required(p, "request.controller.field")).get(worldPlayer());
        }
        int attackPacketCount() throws Exception { return ((Integer)host("attackPacketCount", new Class<?>[0])).intValue(); }
        boolean packetHasOpcode(int opcode) throws Exception {
            return ((Boolean)host("packetHasOpcode", new Class<?>[]{Integer.TYPE}, Integer.valueOf(opcode))).booleanValue();
        }
        String nativePacketSnapshot() throws Exception { return (String)host("nativePacketSnapshot", new Class<?>[0]); }
        String encoded(String suffix) throws Exception { return (String)host("encoded", new Class<?>[]{String.class}, suffix); }
        void installReadyWorld() throws Exception { call(world, "installReadyWorld"); }
        void restoreNativePkGate() throws Exception {
            Method m = world.getClass().getDeclaredMethod("setNativePkGate"); m.setAccessible(true);
            invoke(m, world);
        }
        void setNativeRequestClock(long timestamp, short interval) throws Exception {
            host("setNativeRequestClock", new Class<?>[]{ClassLoader.class, Properties.class, Long.TYPE, Short.TYPE},
                    loader, catalog, Long.valueOf(timestamp), Short.valueOf(interval));
        }
        long nativeRequestTimestamp() throws Exception {
            Class<?> owner = loader.loadClass(required(catalog, "request.timestamp.owner").replace('/', '.'));
            return field(owner, required(catalog, "request.timestamp.field")).getLong(null);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Boolean.TYPE) return Boolean.FALSE; if (type == Byte.TYPE) return Byte.valueOf((byte)0);
        if (type == Short.TYPE) return Short.valueOf((short)0); if (type == Integer.TYPE) return Integer.valueOf(0);
        if (type == Long.TYPE) return Long.valueOf(0L); if (type == Float.TYPE) return Float.valueOf(0f);
        if (type == Double.TYPE) return Double.valueOf(0d); if (type == Character.TYPE) return Character.valueOf((char)0);
        return null;
    }
}
