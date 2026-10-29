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

/** Exercises Auto Sell's return-scroll branch against the patched HBS candidate jar. */
public final class HungSaleReturnScrollBinaryTest implements Opcodes {
    static final String FILTER = "luna.generated.HungEquipmentFilterBridge";
    static final String SCROLL = "luna.generated.HungSaleReturnScrollBridge";
    static final String SETTINGS = "luna.features.EquipmentFilterSettings";
    static final String CONTROLLER = "luna.features.SaleReturnScrollController";
    static final String SALE = "~lliiilll";
    static final String FARM = "~lliiiiil";
    static final String SERVICE = "~lilillll";
    static ClassLoader loader;
    static sun.misc.Unsafe unsafe;
    static int checks;
    static int routeCalls;
    static final List<String> packets = new ArrayList<String>();
    static final List<String> notices = new ArrayList<String>();
    static final List<Short> saleIds = new ArrayList<Short>();

    static String n(String suffix) {
        StringBuilder value = new StringBuilder();
        for (int i = suffix.length(); i < 100; i++) value.append('i');
        return value.append(suffix).toString();
    }

    static Class<?> type(String shortName) throws Exception {
        String full = shortName.startsWith("~") ? n(shortName.substring(1)) : shortName;
        return Class.forName(full, false, loader);
    }

    static Field field(Class<?> owner, String shortName) throws Exception {
        String name = shortName.startsWith("~") ? n(shortName.substring(1)) : shortName;
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(shortName + " on " + owner.getName());
    }

    static Object get(Object target, String owner, String name) throws Exception {
        return field(type(owner), name).get(target);
    }

    static void set(Object target, String owner, String name, Object value) throws Exception {
        field(type(owner), name).set(target, value);
    }

    static Object alloc(String name) throws Exception {
        return unsafe.allocateInstance(type(name));
    }

    static Method method(Class<?> owner, String name, Class<?>... args) throws Exception {
        Method m = owner.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    static Object invokeStatic(String owner, String name, Class<?>[] signature, Object... args) throws Exception {
        return method(type(owner), name, signature).invoke(null, args);
    }

    public static void capture(Object message) throws Exception {
        byte opcode = field(message.getClass(), "a").getByte(message);
        Method payloadMethod = message.getClass().getMethod(n("lliilil"));
        byte[] payload = (byte[])payloadMethod.invoke(message);
        packets.add(opcode + ":" + Arrays.toString(payload));
    }
    public static void captureSale(byte itemType, short itemId, byte action) {
        if (itemType == 3 && action == 1) saleIds.add(Short.valueOf(itemId));
    }
    public static void captureNotice(String text) { notices.add(text); }
    public static void captureRoute() { routeCalls++; }

    /** Sinks only outbound actions and route movement; all decision bytecode stays from the jar. */
    static final class ScrollSinkLoader extends URLClassLoader {
        ScrollSinkLoader(File jar) throws Exception {
            super(new URL[]{jar.toURI().toURL()}, HungSaleReturnScrollBinaryTest.class.getClassLoader());
        }

        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String filterName = FILTER.replace('.', '/');
            String scrollName = SCROLL.replace('.', '/');
            String saleName = n("lliiilll");
            String modName = n("lillllll");
            String serviceName = n("lilillll");
            String packetQueueName = n("lililili");
            String farmName = n("lliiiiil");
            if (!name.equals(filterName.replace('/', '.')) && !name.equals(scrollName.replace('/', '.'))
                    && !name.equals(saleName.replace('/', '.')) && !name.equals(modName.replace('/', '.'))
                    && !name.equals(serviceName.replace('/', '.')) && !name.equals(packetQueueName.replace('/', '.'))
                    && !name.equals(farmName.replace('/', '.'))) {
                return super.findClass(name);
            }
            InputStream in = getResourceAsStream(name.replace('.', '/') + ".class");
            if (in == null) throw new ClassNotFoundException(name + " bytes missing");
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] block = new byte[8192];
                for (int count; (count = in.read(block)) != -1;) bytes.write(block, 0, count);
                ClassNode c = new ClassNode(ASM9);
                new ClassReader(bytes.toByteArray()).accept(c, 0);
                int sinks = 0;
                for (MethodNode m : c.methods) {
                    if (name.equals(filterName.replace('/', '.')) && m.name.equals("showGroups") && m.desc.equals("(Z)V")) {
                        m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
                        m.instructions.add(new InsnNode(RETURN)); sinks++;
                    } else if (name.equals(filterName.replace('/', '.'))) {
                        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; ) {
                            AbstractInsnNode next = insn.getNext();
                            if (insn instanceof FieldInsnNode) {
                                FieldInsnNode f = (FieldInsnNode)insn;
                                if (f.getOpcode() == GETSTATIC && f.owner.equals(n("liilllii"))
                                        && f.name.equals(n("liiiliiiiil"))) m.instructions.set(insn, new InsnNode(ACONST_NULL));
                            }
                            insn = next;
                        }
                    } else if (name.equals(saleName.replace('/', '.')) && m.name.equals(n("lilliiil")) && m.desc.equals("()V")) {
                        m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
                        m.instructions.add(new InsnNode(RETURN)); sinks++;
                    } else if (name.equals(modName.replace('/', '.')) && m.name.equals(n("lilli")) && m.desc.equals("(Ljava/lang/String;)V")) {
                        m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
                        m.instructions.add(new VarInsnNode(ALOAD, 0));
                        m.instructions.add(new MethodInsnNode(INVOKESTATIC, "HungSaleReturnScrollBinaryTest", "captureNotice", "(Ljava/lang/String;)V", false));
                        m.instructions.add(new InsnNode(RETURN)); sinks++;
                    } else if (name.equals(serviceName.replace('/', '.')) && m.name.equals(n("")) && m.desc.equals("(BSB)V")) {
                        m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
                        m.instructions.add(new VarInsnNode(ILOAD, 1)); m.instructions.add(new VarInsnNode(ILOAD, 2)); m.instructions.add(new VarInsnNode(ILOAD, 3));
                        m.instructions.add(new MethodInsnNode(INVOKESTATIC, "HungSaleReturnScrollBinaryTest", "captureSale", "(BSB)V", false));
                        m.instructions.add(new InsnNode(RETURN)); sinks++;
                    } else if (name.equals(packetQueueName.replace('/', '.')) && m.name.equals(n("lliiili"))
                            && m.desc.equals("(L" + n("lllllil") + ";)V")) {
                        m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
                        m.instructions.add(new VarInsnNode(ALOAD, 1));
                        m.instructions.add(new MethodInsnNode(INVOKESTATIC, "HungSaleReturnScrollBinaryTest", "capture", "(Ljava/lang/Object;)V", false));
                        m.instructions.add(new InsnNode(RETURN)); sinks++;
                    } else if (name.equals(farmName.replace('/', '.')) && m.name.equals(n("lillllll")) && m.desc.equals("()V")) {
                        m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
                        m.instructions.add(new MethodInsnNode(INVOKESTATIC, "HungSaleReturnScrollBinaryTest", "captureRoute", "()V", false));
                        m.instructions.add(new InsnNode(RETURN)); sinks++;
                    }
                }
                if ((name.equals(filterName.replace('/', '.')) || name.equals(saleName.replace('/', '.'))
                        || name.equals(modName.replace('/', '.')) || name.equals(farmName.replace('/', '.'))
                        || name.equals(packetQueueName.replace('/', '.'))) && sinks != 1)
                    throw new AssertionError("Expected one presentation/movement sink in " + name + ", got " + sinks);
                if (name.equals(serviceName.replace('/', '.')) && sinks != 1)
                    throw new AssertionError("Expected one native sale request sink, got " + sinks);
                ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                c.accept(out);
                byte[] transformed = out.toByteArray();
                return defineClass(name, transformed, 0, transformed.length);
            } catch (Throwable ex) {
                throw new ClassNotFoundException(name, ex);
            } finally {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
    }

    static ClassNode classNode(JarFile jar, String shortName) throws Exception {
        String suffix = (shortName.startsWith("~") ? shortName.substring(1) : shortName.replace('.', '/')) + ".class";
        JarEntry match = null;
        for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
            JarEntry entry = e.nextElement();
            if (entry.getName().endsWith(suffix)) { match = entry; break; }
        }
        if (match == null) throw new FileNotFoundException(suffix);
        InputStream in = jar.getInputStream(match);
        try { ClassNode c = new ClassNode(ASM9); new ClassReader(in).accept(c, 0); return c; }
        finally { in.close(); }
    }

    static MethodNode findMethod(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        return null;
    }

    static boolean hasCall(ClassNode c, String method, String desc, String target) {
        MethodNode m = findMethod(c, method, desc);
        if (m == null) return false;
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext())
            if (insn instanceof MethodInsnNode && ((MethodInsnNode)insn).owner.equals(SCROLL.replace('.', '/'))
                    && ((MethodInsnNode)insn).name.equals(target)) return true;
        return false;
    }

    static boolean usePacketSerializer(ClassNode service) {
        MethodNode use = findMethod(service, n("llili"), "(S)V");
        if (use == null) return false;
        boolean opcode32 = false, writeShort = false;
        for (AbstractInsnNode insn = use.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof IntInsnNode && ((IntInsnNode)insn).operand == 32) opcode32 = true;
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)insn;
                if (call.owner.equals("java/io/DataOutputStream") && call.name.equals("writeShort") && call.desc.equals("(I)V")) writeShort = true;
            }
        }
        return opcode32 && writeShort;
    }

    static void check(String name, boolean okay) {
        checks++;
        if (!okay) throw new AssertionError(name);
        System.out.println("PASS " + name);
    }

    static List<String> usePackets() {
        List<String> uses = new ArrayList<String>();
        for (String packet : packets) if (packet.startsWith("32:")) uses.add(packet);
        return uses;
    }

    static void eq(String name, Object actual, Object expected) {
        checks++;
        if (!String.valueOf(actual).equals(String.valueOf(expected)))
            throw new AssertionError(name + ": " + actual + " != " + expected);
        System.out.println("PASS " + name);
    }

    static Object vector(Object... values) throws Exception {
        Object result = type("et").getConstructor().newInstance();
        Method add = type("et").getMethod(n("l"), Object.class);
        for (Object value : values) add.invoke(result, value);
        return result;
    }

    static int vectorSize(Object value) throws Exception {
        return ((Integer)type("et").getMethod("c").invoke(value)).intValue();
    }

    static Object item(int id, int kind, int quantity, int quality, int slot, int enhancement) throws Exception {
        Object result = alloc("~lililiii");
        set(result, "~llilli", "O", Integer.valueOf(id));
        set(result, "~llilli", "~liiill", Integer.valueOf(kind));
        set(result, "~llilli", "~llllii", Integer.valueOf(quantity));
        set(result, "~llilli", "~liiiiliii", id == 142 ? "Bùa về làng" : "test item " + id);
        set(result, "~llilli", "N", Integer.valueOf(quality));
        set(result, "~llilli", "~llliiili", Integer.valueOf(slot));
        set(result, "~llilli", "M", Integer.valueOf(-1));
        set(result, "~llilli", "~liiiliiii", Byte.valueOf((byte)0));
        set(result, "~llilli", "~llilil", Byte.valueOf((byte)enhancement));
        return result;
    }

    static Object scroll(int id, int kind, int quantity) throws Exception {
        Object result = alloc("~llilli");
        set(result, "~llilli", "O", Integer.valueOf(id));
        set(result, "~llilli", "~liiill", Integer.valueOf(kind));
        set(result, "~llilli", "~llllii", Integer.valueOf(quantity));
        set(result, "~llilli", "~liiiiliii", "Bùa về làng");
        return result;
    }

    static Object fullBag(Object goodItem, Object... additional) throws Exception {
        Object[] values = new Object[27];
        values[0] = goodItem;
        for (int i = 0; i < additional.length && i < 26; i++) values[i + 1] = additional[i];
        Object filler = item(990, 3, 1, 2, 8, 0);
        for (int i = 1 + additional.length; i < values.length; i++) values[i] = filler;
        return vector(values);
    }

    static void prepare(File jar) throws Exception {
        Common emulator = new Common(null);
        MemoryRecordStoreManager rms = new MemoryRecordStoreManager();
        rms.init(emulator); emulator.setRecordStoreManager(rms); MIDletBridge.setMicroEmulator(emulator);
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true); unsafe = (sun.misc.Unsafe)uf.get(null);
        loader = new ScrollSinkLoader(jar);
        Class.forName(FILTER, true, loader); Class.forName(SCROLL, true, loader); Class.forName(SETTINGS, true, loader);
        type(SALE).getDeclaredMethods(); type("~llilli").getDeclaredFields(); type("~liiliii").getDeclaredFields();
        // The service getter returns its native singleton; install an inert instance if clinit left it empty.
        Class<?> service = type(SERVICE);
        Object singleton = null;
        for (Field f : service.getDeclaredFields()) if (Modifier.isStatic(f.getModifiers()) && f.getType() == service) {
            f.setAccessible(true); singleton = f.get(null);
            if (singleton == null) { singleton = unsafe.allocateInstance(service); f.set(null, singleton); }
        }
        // The unchanged service creates a native message per call; sink only its outgoing queue.
        set(singleton, "~lllilii", "~lliliii", alloc("~lililili"));
    }

    static void setSaleGroupsToAll() throws Exception {
        Class<?> settings = type(SETTINGS);
        field(settings, "loaded").setBoolean(null, true);
        field(settings, "saleMask").setInt(null, 15);
        field(settings, "lockedOnly").setBoolean(null, false);
    }

    static void setMap(int map) throws Exception {
        Object world = get(null, "~liilllii", "~liiililiiii");
        if (world == null) { world = alloc("cs"); set(world, "cs", "d", Integer.valueOf(map)); set(null, "~liilllii", "~liiililiiii", world); }
        else set(world, "cs", "d", Integer.valueOf(map));
        Object player = alloc("~lliiil"); set(player, "~lliiil", "cG", Integer.valueOf(0));
        set(null, "~liiliii", "~llilliill", player);
    }

    static int map() throws Exception { return ((Integer)invokeStatic(SCROLL, "map", new Class<?>[0])).intValue(); }
    static boolean waiting() throws Exception { return ((Boolean)invokeStatic(CONTROLLER, "waiting", new Class<?>[0])).booleanValue(); }
    static int fallbackDestination() throws Exception { return ((Integer)invokeStatic(CONTROLLER, "fallbackDestination", new Class<?>[0])).intValue(); }
    static void cancelWait() throws Exception { invokeStatic(CONTROLLER, "cancel", new Class<?>[0]); }

    static void reset(int currentMap, Object bag) throws Exception {
        cancelWait(); setMap(currentMap);
        set(null, "~llilli", "~liiillili", bag);
        set(null, "~llilli", "~liiillill", vector());
        set(null, "~lliiil", "~lliliili", Short.valueOf((short)30));
        set(null, FARM, "~lililiilill", Boolean.TRUE);
        set(null, FARM, "~lililiillii", Boolean.TRUE);
        set(null, FARM, "~lililiillil", Boolean.FALSE);
        set(null, "~lliilili", "~lillilliiii", Boolean.FALSE);
        set(null, "~lliilili", "~lillillilil", Boolean.FALSE);
        set(null, SALE, "~lilliiiilii", Integer.valueOf(2));
        set(null, SALE, "~lilliiiiill", Boolean.TRUE);
        set(null, SALE, "~lilliiiilil", Integer.valueOf(0));
        set(null, SALE, "~lilliiiilli", Integer.valueOf(0));
        set(null, "~lillllll", "~liliililiil", Boolean.FALSE);
        set(null, "~lliiiiii", "~liliillilli", Integer.valueOf(41));
        set(null, "~lliiiiii", "~liliillilil", Boolean.TRUE);
        set(null, "~lliiiiii", "~liliiiilill", Integer.valueOf(15));
        setSaleGroupsToAll(); packets.clear(); saleIds.clear(); notices.clear(); routeCalls = 0;
    }

    static void nativeUpdate() throws Exception {
        Object sale = alloc(SALE);
        method(type(SALE), "update").invoke(sale);
    }

    static Object good() throws Exception { return item(501, 3, 1, 1, 8, 0); }

    static void verifyHooks(File jarFile) throws Exception {
        JarFile jar = new JarFile(jarFile);
        try {
            ClassNode sale = classNode(jar, SALE);
            ClassNode bridge = classNode(jar, SCROLL);
            ClassNode service = classNode(jar, SERVICE);
            check("native Auto Sell update calls scroll poll", hasCall(sale, "update", "()V", "poll"));
            check("native state-one transition calls scroll start", hasCall(sale, "update", "()V", "start"));
            check("bridge scans bag and exposes a native scroll match", findMethod(bridge, "findScroll", "()L" + n("llilli") + ";") != null);
            check("native item-use serializes opcode 32 plus short item ID", usePacketSerializer(service));
        } finally { jar.close(); }
    }

    static void testIdentificationAndFallback() throws Exception {
        Object candidate = good();
        Object wrongName = scroll(142, 7, 1);
        set(wrongName, "~llilli", "~liiiiliii", "Vật phẩm khác");
        Object missingName = scroll(142, 7, 1);
        set(missingName, "~llilli", "~liiiiliii", null);
        Object[] notFull = new Object[26]; Arrays.fill(notFull, candidate); notFull[1] = scroll(142, 7, 1);
        reset(50, vector(notFull)); nativeUpdate();
        eq("four free slots do not consume a scroll", packets, Collections.emptyList());
        eq("four free slots leave sale idle", get(null, SALE, "~lilliiiilil"), Integer.valueOf(0));
        reset(50, fullBag(candidate, wrongName, missingName));
        check("finder ignores missing/nonmatching names even if ID is 142", invokeStatic(SCROLL, "findScroll", new Class<?>[0]) == null);

        // A matching scroll in the chest is absent from the player bag scan.
        reset(50, fullBag(candidate));
        set(null, "~llilli", "~liiillill", vector(scroll(142, 7, 1)));
        check("finder ignores a return scroll outside the player bag", invokeStatic(SCROLL, "findScroll", new Class<?>[0]) == null);

        // Near-full eligible bag with no usable return scroll keeps the existing native route.
        reset(50, fullBag(candidate, wrongName, missingName));
        eq("near-full bag size meets native trigger", Integer.valueOf(vectorSize((Object)get(null, "~llilli", "~liiillili"))), Integer.valueOf(27));
        nativeUpdate();
        eq("missing usable scroll sends no use packet", packets, Collections.emptyList());
        check("missing scroll keeps native route enabled", ((Boolean)get(null, "~lliiiiii", "~liliillilil")).booleanValue());
        eq("native route retains nearest eligible village target", get(null, "~lliiiiii", "~liliillilli"), Integer.valueOf(33));
        check("missing scroll falls through to native route", routeCalls > 0);

        // Full but ineligible inventory is stopped by the existing preflight before any travel.
        Object junk = item(777, 3, 1, 2, 8, 0);
        reset(50, fullBag(junk, scroll(142, 7, 1)));
        nativeUpdate();
        eq("no eligible sale item turns Auto Sell off", get(null, SALE, "~lilliiiiill"), Boolean.FALSE);
        eq("no eligible item does not use the return scroll", packets, Collections.emptyList());
        eq("no eligible item does not start native movement", Integer.valueOf(routeCalls), Integer.valueOf(0));
    }

    static String usePacket(int id) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeShort(id);
        return "32:" + Arrays.toString(bytes.toByteArray());
    }

    static void testSameScrollAsNativeFarm() throws Exception {
        int[][] metadata = {{141,4,1},{450,7,3},{142,4,1},{607,7,0}};
        for (int[] values : metadata) {
            Object charm = scroll(values[0], values[1], values[2]);
            set(charm, "~llilli", "~liiiiliii", "Bùa về làng [Khóa]");
            reset(50, fullBag(good(), charm));
            // This is the actual helper called by Farm's no-attack counter, with only queue/movement sinks.
            invokeStatic(FARM, n("lliiiili"), new Class<?>[0]);
            eq("native Farm uses named scroll ID " + values[0], usePackets(), Collections.singletonList(usePacket(values[0])));
            check("sale finder selects the same item as native Farm ID " + values[0],
                    invokeStatic(SCROLL, "findScroll", new Class<?>[0]) == charm);
            reset(50, fullBag(good(), charm)); nativeUpdate();
            eq("native sale sends the matched item ID " + values[0], usePackets(), Collections.singletonList(usePacket(values[0])));
            nativeUpdate();
            eq("variant scroll remains one use per sale trip " + values[0], usePackets(), Collections.singletonList(usePacket(values[0])));
        }
        Object wrong = scroll(142,7,1); set(wrong,"~llilli","~liiiiliii","Bùa đi nơi khác");
        reset(50,fullBag(good(),wrong)); invokeStatic(FARM,n("lliiiili"),new Class<?>[0]);
        eq("native Farm rejects nonmatching name despite ID 142",usePackets(),Collections.emptyList());
        check("sale finder also rejects nonmatching name despite ID 142",invokeStatic(SCROLL,"findScroll",new Class<?>[0])==null);
    }

    static void testUseWaitArrivalAndTimeout() throws Exception {
        Object candidate = good(); Object valid = scroll(142, 7, 1);
        reset(50, fullBag(candidate, valid));
        Object found = invokeStatic(SCROLL, "findScroll", new Class<?>[0]);
        check("finder returns exact in-bag ID 142 kind 7 item", found == valid);
        nativeUpdate();
        eq("near-full eligible bag emits opcode-32 short item payload", usePackets(), Collections.singletonList("32:[0, -114]"));
        check("scroll wait becomes active after use", waiting());
        eq("scroll routes toward Làng Sói Trắng map 1", get(null, "~lliiiiii", "~liliillilli"), Integer.valueOf(1));
        eq("scroll suspends native route while waiting", get(null, "~lliiiiii", "~liliillilil"), Boolean.FALSE);
        eq("use keeps original map for the return-to-Farm trip", get(null, SALE, "~lilliiiilll"), Integer.valueOf(50));
        eq("native server owns scroll stack consumption", get(valid, "~llilli", "~llllii"), Integer.valueOf(1));
        eq("native Farm mode snapshot is preserved", get(null, SALE, "~lilliiiliil"), Boolean.TRUE);
        int oldRouteCalls = routeCalls;
        nativeUpdate();
        eq("waiting update does not resend scroll", usePackets(), Collections.singletonList("32:[0, -114]"));
        eq("waiting update holds native movement", Integer.valueOf(routeCalls), Integer.valueOf(oldRouteCalls));
        check("waiting state remains active before teleport arrival", waiting());

        setMap(1);
        nativeUpdate();
        eq("arrival leaves one use packet total", usePackets(), Collections.singletonList("32:[0, -114]"));
        check("arrival clears scroll wait", !waiting());
        eq("arrival sets native target to current village map", get(null, "~lliiiiii", "~liliillilli"), Integer.valueOf(1));
        eq("arrival stops native route", get(null, "~lliiiiii", "~liliillilil"), Boolean.FALSE);
        eq("native sale enters state 2 after village arrival", get(null, SALE, "~lilliiiilil"), Integer.valueOf(2));
        eq("village arrival preserves original Farm map", get(null, SALE, "~lilliiiilll"), Integer.valueOf(50));

        reset(50, fullBag(candidate, scroll(142, 7, 1)));
        nativeUpdate();
        int originalTarget = fallbackDestination();
        Field requestedAt = field(type(CONTROLLER), "requestedAt");
        requestedAt.setLong(null, System.currentTimeMillis() - 10001L);
        nativeUpdate();
        eq("timeout preserves single scroll request", usePackets(), Collections.singletonList("32:[0, -114]"));
        check("timeout clears scroll wait", !waiting());
        eq("timeout restores native original destination", get(null, "~lliiiiii", "~liliillilli"), Integer.valueOf(originalTarget));
        eq("timeout restores native route", get(null, "~lliiiiii", "~liliillilil"), Boolean.TRUE);
        eq("timeout restores native route delay", get(null, "~lliiiiii", "~liliiiilill"), Integer.valueOf(15));
        nativeUpdate();
        eq("fallback update never resends the scroll", usePackets(), Collections.singletonList("32:[0, -114]"));

        reset(50, fullBag(candidate, scroll(142, 7, 1)));
        Object service = invokeStatic(SERVICE, n("liililli"), new Class<?>[0]);
        Object sender = get(service, "~lllilii", "~lliliii");
        set(service, "~lllilii", "~lliliii", null);
        try { nativeUpdate(); } finally { set(service, "~lllilii", "~lliliii", sender); }
        check("send failure cancels the scroll wait", !waiting());
        eq("send failure immediately restores native destination", get(null, "~lliiiiii", "~liliillilli"), Integer.valueOf(33));
        eq("send failure immediately restores native travel", get(null, "~lliiiiii", "~liliillilil"), Boolean.TRUE);
    }

    static void testCancellationAndVillageGuard() throws Exception {
        Object candidate = good();
        reset(50, fullBag(candidate, scroll(142, 7, 1)));
        nativeUpdate();
        set(null, SALE, "~lilliiiiill", Boolean.FALSE);
        nativeUpdate();
        check("turning Auto Sell off cancels scroll wait", !waiting());
        eq("off cancellation sends no second use packet", usePackets(), Collections.singletonList("32:[0, -114]"));
        eq("off cancellation stops the temporary route", get(null, "~lliiiiii", "~liliillilil"), Boolean.FALSE);

        reset(50, fullBag(candidate, scroll(142, 7, 1)));
        nativeUpdate();
        set(null, SALE, "~lilliiiilil", Integer.valueOf(0));
        invokeStatic(SCROLL, "poll", new Class<?>[0]);
        check("native sale state cancellation clears wait", !waiting());
        eq("state cancellation does not resend use packet", usePackets(), Collections.singletonList("32:[0, -114]"));

        reset(50, fullBag(candidate, scroll(142, 7, 1))); nativeUpdate();
        set(null, FARM, "~lililiilill", Boolean.FALSE); nativeUpdate();
        check("turning Farm off pauses the pending trip", waiting());
        eq("Farm-off pause stops the temporary route", get(null, "~lliiiiii", "~liliillilil"), Boolean.FALSE);
        setMap(1); set(null, FARM, "~lililiilill", Boolean.TRUE); nativeUpdate();
        check("Farm-on resumes village arrival without a stuck trip", !waiting());
        eq("resumed Farm continues native selling", get(null, SALE, "~lilliiiilil"), Integer.valueOf(2));
        eq("Farm off/on never resends the scroll", usePackets(), Collections.singletonList("32:[0, -114]"));

        int[] villages = {1, 33, 70};
        for (int village : villages) {
            reset(village, fullBag(candidate, scroll(142, 7, 1)));
            nativeUpdate();
            eq("already in village " + village + " does not use scroll", packets, Collections.emptyList());
            check("already in village " + village + " has no scroll wait", !waiting());
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: HungSaleReturnScrollBinaryTest <candidate.jar>");
        File jar = new File(args[0]);
        prepare(jar);
        verifyHooks(jar);
        testSameScrollAsNativeFarm();
        testIdentificationAndFallback();
        testUseWaitArrivalAndTimeout();
        testCancellationAndVillageGuard();
        System.out.println("ALL PASS checks=" + checks + " scroll_packets=" + packets.size() + " route_calls=" + routeCalls);
        ((URLClassLoader)loader).close();
    }
}
