import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Them tinh nang "Auto Di Buon" vao jar mod TruongBk.
 *
 *   javac -encoding UTF-8 -cp "<asm-cp>" -d build/tools tools/legacy/luna/AutoTradePatcher.java
 *   java  -cp "build/tools;<asm-cp>" AutoTradePatcher [input.jar] [output.jar] [hide-trade-menu]
 *
 * Mac dinh HSOLUNA_TRUONGBK_PERFECT.jar -> HSOLUNA_TRUONGBK_AUTOTRADE.jar.
 * Tu choi ghi de output da ton tai. Tu doc lai jar vua ghi de verify truoc khi bao thanh cong.
 *
 * Nam moc duoc chen, tat ca deu nho:
 *   1. ENGINE.showMainMenu   them dong menu "Auto Di Buon" ngay duoi "Auto Nuoi Pet"
 *   2. ENGINE.perform        prologue: HSOAutoTradeSupport.handleMenuAction -> return neu true
 *   3. HSOSKYPetFeedSupport.onCanvasUpdate gates its native horse retry;
 *      CANVAS.tickAll truoc moi RETURN: HSOAutoTradeSupport.tick()
 *   4. ENGINE.paintHud       prologue: HSOAutoTradeSupport.paintHud(g)
 *   5. ENGINE.onNpcMenuOpened prologue: HSOAutoTradeSupport.onNpcMenu -> return true neu true
 *   6. SHOP.<init>           truoc moi RETURN: HSOAutoTradeSupport.onShopOpened(this)
 *   7. lop xu ly dut ket noi rut nhip tu dang nhap lai 30 giay -> 15 giay
 *   8. ha class version cua class nao lo bi bien dich cho JRE moi
 *
 * Moc 3 nam trong CANVAS chu KHONG trong ENGINE.update(): method do chi duoc goi tu man
 * hinh game, nen mo cua hang cua Dylan la tick() ngung chay. Con moc 6 la cach biet cua
 * hang da mo va lay duoc chinh doi tuong cua hang de doc loai cua hang + id ngoc.
 *
 * HSOSKYPetFeedSupport.class keeps its golden implementation; the single native
 * horse retry call is routed through a verified facade gate. Other classes patched
 * for speed / cooldown / noclip are copied and checked after writing.
 */
public final class AutoTradePatcher implements Opcodes {

    static final String SUPPORT = "HSOAutoTradeSupport";
    static final String RUNTIME_SUPPORT = "HSOSKYRuntimeSupport";
    static final String LEGACY_SUPPORT = "LunaGeneratedLegacyHooks";
    /** Compile jar da chua cac class src/luna va cac field binary mo quyen de javac. */
    static final String COMPILE_JAR = "build/generated/.autotrade-compile.jar";
    static final String MENU_LABEL = "Auto Đi Buôn";
    static final String MENU_LABEL_N4 = "Auto Ngã 4";
    static final String ANCHOR_LABEL = "Auto Nuôi Pet";
    static final String AUTO_MODE_ANCHOR = "Auto Tự Đánh: [";
    static final String AUTO_MODE_MONSTERS_LABEL = "Auto chỉ quái";
    static final String AUTO_MODE_MONSTERS_AND_PLAYERS_LABEL = "Auto quái + PK";
    static final int ACT_SUBMENU = 9;
    static final int ACT_N4_SUBMENU = 10;
    static final int ACT_REVIVE_SUBMENU = 11;
    static final int ACT_AUTO_MONSTERS = -26;
    static final int ACT_AUTO_MONSTERS_AND_PLAYERS = -27;

    /** Nhip tu dang nhap lai cua CHINH CLIENT sau khi rot mang. */
    static final long OLD_RETRY_MS = 30000L;
    static final long NEW_RETRY_MS = 15000L;
    static final int EXPECT_RETRY_SITES = 3;

    /** Chu ky vong lap game: 8ms la 125 FPS, 16ms la muc 60 FPS danh nghia. */
    static final long OLD_FRAME_MS = 8L;
    static final long NEW_FRAME_MS = 16L;
    static final int EXPECT_FRAME_SITES = 1;

    /** Nhip engine goc tu gui yeu cau hoi sinh sau khi nhan vat chet. */
    static final long OLD_REVIVE_MS = 2000L;
    static final long NEW_REVIVE_MS = 500L;
    static final int EXPECT_REVIVE_SITES = 1;

    /** Class version cao nhat duoc phep co trong jar (51 = Java 7). */
    static final int MAX_CLASS_VERSION = 51;

    static Properties SYM;
    static boolean hideTradeMenu;

    public static void main(String[] args) throws Exception {
        String inPath = args.length > 0 ? args[0] : "HSOLUNA_TRUONGBK_PERFECT.jar";
        String outPath = args.length > 1 ? args[1] : "HSOLUNA_TRUONGBK_AUTOTRADE.jar";
        hideTradeMenu = args.length > 2 && "hide-trade-menu".equalsIgnoreCase(args[2]);

        File generatedDir = new File(System.getProperty("luna.generated.dir", "build/generated"));
        File supportSource = new File(System.getProperty(
                "luna.support.source", "build/generated/HSOAutoTradeSupport.java"));
        File supportTemplate = new File(System.getProperty(
                "luna.support.template", "tools/legacy/luna/HSOAutoTradeSupport.java.in"));
        File runtimeSource = new File(System.getProperty(
                "luna.runtime.source", "tools/legacy/luna/HSOSKYRuntimeSupport.java"));
        String symbolPath = System.getProperty(
                "luna.symbols", "symbols/autotrade-symbols.properties");

        File outFile = new File(outPath);
        if (outFile.exists()) {
            throw new RuntimeException("Output da ton tai, xoa hoac doi ten khac: " + outPath);
        }
        File supportFile = new File(generatedDir, SUPPORT + ".class");
        if (!supportFile.exists()) {
            throw new RuntimeException("Thieu " + SUPPORT + ".class - chay GenAutoTradeSupport"
                    + " roi javac truoc.");
        }
        /*
         * Javac that bai thi .class cu van con nam do, va patcher se lang le dong goi ban cu
         * vao jar moi - da mat mot lan build nhu vay. Nen chot: .class phai moi hon ca .java
         * sinh ra no lan .in la ban goc de sua.
         */
        requireFresh(supportFile, supportSource);
        requireFresh(supportFile, supportTemplate);
        File runtimeFile = new File(generatedDir, RUNTIME_SUPPORT + ".class");
        if (!runtimeFile.exists()) {
            throw new RuntimeException("Thieu " + RUNTIME_SUPPORT + ".class - chay javac truoc.");
        }
        requireFresh(runtimeFile, runtimeSource);
        File legacyFile = new File(generatedDir, LEGACY_SUPPORT + ".class");
        if (!legacyFile.exists()) {
            throw new RuntimeException("Thieu " + legacyFile.getPath()
                    + " - chay GenAutoTradeSupport va compile legacy bridge truoc.");
        }
        requireFresh(legacyFile, new File(generatedDir, LEGACY_SUPPORT + ".java"));
        File compileJarFile = new File(COMPILE_JAR);
        if (!compileJarFile.exists()) {
            throw new RuntimeException("Thieu " + COMPILE_JAR
                    + " - chay MakeAutoTradeCompileJar sau khi compile src/luna.");
        }

        SYM = new Properties();
        InputStream sin = new FileInputStream(symbolPath);
        try {
            SYM.load(sin);
        } finally {
            sin.close();
        }

        System.out.println("=================================================");
        System.out.println("AUTO DI BUON: " + inPath + " -> " + outPath);
        System.out.println("=================================================");

        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        Map<String, Long> times = new LinkedHashMap<String, Long>();
        readJar(inPath, entries, times);
        System.out.println("[1] Doc jar goc: " + entries.size() + " entry");

        byte[] petFeedBefore = entries.get("HSOSKYPetFeedSupport.class");
        byte[] nativeMenuBefore = entries.get(sym("class.MENUBOX") + ".class");
        if (petFeedBefore == null) {
            throw new RuntimeException("Jar goc khong co HSOSKYPetFeedSupport.class - sai jar?");
        }
        if (nativeMenuBefore == null) {
            throw new RuntimeException("Jar goc khong co MENUBOX.class - sai jar?");
        }

        String engineName = sym("class.ENGINE");
        byte[] engineBytes = entries.get(engineName + ".class");
        if (engineBytes == null) throw new RuntimeException("Khong tim thay class engine trong jar");

        ClassNode engine = new ClassNode();
        new ClassReader(engineBytes).accept(engine, 0);

        patchMainMenu(engine);
        patchAutoAttackModeMenu(engine);
        patchPerform(engine);
        patchPaintHud(engine);
        patchNpcMenu(engine);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        engine.accept((ClassVisitor) cw);
        entries.put(engineName + ".class", cw.toByteArray());

        patchNativeReviveDelay(entries);
        patchNativeHorseMountGate(entries);
        patchNativeLoginWorldGuard(entries);
        patchHorseDialogGate(entries);
        patchCanvasTick(entries);
        patchShop(entries);
        patchAutoTarget(entries);
        patchAutoAttackPath(entries);
        patchReconnectDelay(entries);
        patchFrameRate(entries);
        lowerClassVersions(entries);

        int semanticCount = addSemanticClasses(entries, times, compileJarFile);
        require(semanticCount > 0, "compile jar khong co class src/luna");
        require(addGeneratedLegacy(entries, times, compileJarFile) == 1,
                "compile jar khong co generated legacy bridge");
        patchLoginSurfaceDirect(entries);
        patchLegacyTransportReconnect(entries);
        patchLoginCooldownPacketHandler(entries);
        patchLoginRequestObserver(entries);
        patchLoginEnterObserver(entries);
        patchLoginHandshakeReady(entries);
        patchIncomingOpcodeObserver(entries);
        patchAllIncomingUtfObservers(entries);
        entries.put(SUPPORT + ".class", readAll(supportFile));
        entries.put(RUNTIME_SUPPORT + ".class", readAll(runtimeFile));
        System.out.println("[7] Nhung " + semanticCount + " class src/luna, " + LEGACY_SUPPORT
                + ".class, " + SUPPORT + ".class va " + RUNTIME_SUPPORT + ".class vao jar");

        writeJar(outPath, entries, times);
        System.out.println("[8] Ghi " + outPath + " (" + outFile.length() + " byte)");

        verify(outPath, engineName, petFeedBefore, nativeMenuBefore);

        System.out.println();
        System.out.println("=================================================");
        System.out.println("XONG: " + outPath);
        System.out.println("=================================================");
    }

    // -------------------------------------------------------------- cac moc

    /** Reuse the baseline Auto Di Buon row instead of adding a duplicate. */
    static void patchMainMenu(ClassNode engine) {
        MethodNode m = method(engine, sym("engine.method.showMainMenu"), "()V");
        AbstractInsnNode anchor = null;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof LdcInsnNode && ANCHOR_LABEL.equals(((LdcInsnNode) n).cst)) {
                anchor = n;
                break;
            }
        }
        if (anchor == null) throw new RuntimeException("Khong thay dong menu \"" + ANCHOR_LABEL + "\"");
        int reviveRows = replaceActionAfterLabel(m, "Sinh", 3, ACT_REVIVE_SUBMENU);
        require(reviveRows == 1, "khong doi duoc dong Auto Hoi Sinh sang submenu");

        if (hideTradeMenu) {
            require(removeMenuRowAfterLabel(m, MENU_LABEL) == 1,
                    "khong an duoc dong menu native Auto Di Buon");
        } else {
            require(replaceMenuActionAfterLabel(m, MENU_LABEL, ACT_SUBMENU) == 1,
                    "khong tai su dung duoc dong menu native Auto Di Buon");
        }

        // Di toi lenh addElement ket thuc dong menu do.
        String vecName = sym("class.VEC");
        String vecAdd = sym("vec.add");
        AbstractInsnNode after = null;
        for (AbstractInsnNode n = anchor; n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode c = (MethodInsnNode) n;
            if (vecName.equals(c.owner) && vecAdd.equals(c.name)
                    && "(Ljava/lang/Object;)V".equals(c.desc)) {
                after = n;
                break;
            }
        }
        if (after == null) throw new RuntimeException("Khong thay addElement sau \"" + ANCHOR_LABEL + "\"");

        AbstractInsnNode cursor = after;
        cursor = addMenuRow(m, cursor, engine, MENU_LABEL_N4, ACT_N4_SUBMENU);
        if (hideTradeMenu) {
            System.out.println("[2] An dong menu native \"" + MENU_LABEL + "\"; them \""
                    + MENU_LABEL_N4 + "\" (action " + ACT_N4_SUBMENU + ")");
        } else {
            System.out.println("[2] Tai su dung dong menu \"" + MENU_LABEL + "\" (action "
                    + ACT_SUBMENU + "); them \"" + MENU_LABEL_N4 + "\" (action "
                    + ACT_N4_SUBMENU + ")");
        }
    }

    /** Chen mot dong menu ngay sau {@code after}; tra ve lenh cuoi cung vua chen. */
    static AbstractInsnNode addMenuRow(MethodNode m, AbstractInsnNode after, ClassNode engine,
            String label, int action) {
        String vecName = sym("class.VEC");
        String vecAdd = sym("vec.add");
        String cmdName = sym("class.CMD");
        String cmdCtor = "(Ljava/lang/String;IL" + sym("class.LISTENER") + ";)V";
        InsnList add = new InsnList();
        add.add(new VarInsnNode(ALOAD, 0));                       // Vector cuc bo
        add.add(new TypeInsnNode(NEW, cmdName));
        add.add(new InsnNode(DUP));
        add.add(new LdcInsnNode(label));
        add.add(new IntInsnNode(BIPUSH, action));
        add.add(new FieldInsnNode(GETSTATIC, engine.name, sym("engine.field.INSTANCE"),
                "L" + engine.name + ";"));
        add.add(new MethodInsnNode(INVOKESPECIAL, cmdName, "<init>", cmdCtor, false));
        AbstractInsnNode last = new MethodInsnNode(INVOKEVIRTUAL, vecName, vecAdd,
                "(Ljava/lang/Object;)V", false);
        add.add(last);
        m.instructions.insert(after, add);
        return last;
    }

    /** Them hai lua chon mode Auto Danh vao menu "CHON CHE DO" cua client goc. */
    static void patchAutoAttackModeMenu(ClassNode engine) {
        MethodNode m = method(engine, sym("engine.method.update"), "()V");
        AbstractInsnNode anchor = null;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof LdcInsnNode && AUTO_MODE_ANCHOR.equals(((LdcInsnNode) n).cst)) {
                anchor = n;
                break;
            }
        }
        if (anchor == null) {
            throw new RuntimeException("Khong thay dong menu \"" + AUTO_MODE_ANCHOR + "\"");
        }

        AbstractInsnNode firstRow = findVecAddAfter(anchor);
        if (firstRow == null) {
            throw new RuntimeException("Khong thay vector.add cua dong Auto Tự Đánh");
        }
        AbstractInsnNode cursor = addMenuRow(m, firstRow, engine,
                AUTO_MODE_MONSTERS_LABEL, ACT_AUTO_MONSTERS);
        addMenuRow(m, cursor, engine,
                AUTO_MODE_MONSTERS_AND_PLAYERS_LABEL, ACT_AUTO_MONSTERS_AND_PLAYERS);
        System.out.println("[2a] Them \"" + AUTO_MODE_MONSTERS_LABEL + "\" (action "
                + ACT_AUTO_MONSTERS + ") va \"" + AUTO_MODE_MONSTERS_AND_PLAYERS_LABEL
                + "\" (action " + ACT_AUTO_MONSTERS_AND_PLAYERS + ") vao menu Auto Danh");
    }

    static AbstractInsnNode findVecAddAfter(AbstractInsnNode anchor) {
        String vecName = sym("class.VEC");
        String vecAdd = sym("vec.add");
        for (AbstractInsnNode n = anchor; n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) n;
            if (vecName.equals(call.owner) && vecAdd.equals(call.name)
                    && "(Ljava/lang/Object;)V".equals(call.desc)) {
                return n;
            }
        }
        return null;
    }

    /** Doi action cua dong menu native, khong phu thu encoding dau tieng Viet. */
    static int replaceActionAfterLabel(MethodNode m, String labelFragment, int oldAction,
            int newAction) {
        int changed = 0;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof LdcInsnNode) || !(((LdcInsnNode) n).cst instanceof String)) continue;
            String text = (String) ((LdcInsnNode) n).cst;
            if (text.indexOf(labelFragment) < 0) continue;
            int seen = 0;
            for (AbstractInsnNode x = n; x != null && seen++ < 20; x = x.getNext()) {
                if (x instanceof MethodInsnNode && "<init>".equals(((MethodInsnNode) x).name)) break;
                Integer value = intConstant(x);
                if (value == null || value.intValue() != oldAction) continue;
                m.instructions.set(x, new IntInsnNode(BIPUSH, newAction));
                changed++;
                break;
            }
        }
        return changed;
    }

    /** Replace an exact menu row action when the baseline action is version-specific. */
    static int replaceMenuActionAfterLabel(MethodNode m, String label, int newAction) {
        int changed = 0;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof LdcInsnNode) || !label.equals(((LdcInsnNode) n).cst)) continue;
            for (AbstractInsnNode x = n.getNext(); x != null; x = x.getNext()) {
                if (x instanceof MethodInsnNode && "<init>".equals(((MethodInsnNode) x).name)) break;
                if (intConstant(x) == null) continue;
                m.instructions.set(x, new IntInsnNode(BIPUSH, newAction));
                changed++;
                break;
            }
        }
        return changed;
    }

    /** Remove an exact vector.add(new Cmd(label,...)) row from the menu builder. */
    static int removeMenuRowAfterLabel(MethodNode m, String label) {
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof LdcInsnNode) || !label.equals(((LdcInsnNode) n).cst)) continue;

            AbstractInsnNode start = null;
            for (AbstractInsnNode p = n.getPrevious(); p != null; p = p.getPrevious()) {
                if (p instanceof VarInsnNode && p.getOpcode() == ALOAD
                        && ((VarInsnNode) p).var == 0
                        && p.getNext() instanceof TypeInsnNode
                        && p.getNext().getOpcode() == NEW) {
                    start = p;
                    break;
                }
            }

            AbstractInsnNode end = null;
            String vecName = sym("class.VEC");
            String vecAdd = sym("vec.add");
            for (AbstractInsnNode x = n; x != null; x = x.getNext()) {
                if (!(x instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) x;
                if (vecName.equals(call.owner) && vecAdd.equals(call.name)
                        && "(Ljava/lang/Object;)V".equals(call.desc)) {
                    end = x;
                    break;
                }
            }
            if (start == null || end == null) return 0;

            AbstractInsnNode x = start;
            while (x != null) {
                AbstractInsnNode next = x.getNext();
                m.instructions.remove(x);
                if (x == end) break;
                x = next;
            }
            return 1;
        }
        return 0;
    }

    static Integer intConstant(AbstractInsnNode n) {
        if (n == null) return null;
        if (n instanceof IntInsnNode) {
            IntInsnNode i = (IntInsnNode) n;
            if (i.getOpcode() == BIPUSH || i.getOpcode() == SIPUSH) return Integer.valueOf(i.operand);
        }
        if (n instanceof LdcInsnNode && ((LdcInsnNode) n).cst instanceof Integer) {
            return (Integer) ((LdcInsnNode) n).cst;
        }
        switch (n.getOpcode()) {
            case ICONST_M1: return Integer.valueOf(-1);
            case ICONST_0: return Integer.valueOf(0);
            case ICONST_1: return Integer.valueOf(1);
            case ICONST_2: return Integer.valueOf(2);
            case ICONST_3: return Integer.valueOf(3);
            case ICONST_4: return Integer.valueOf(4);
            case ICONST_5: return Integer.valueOf(5);
            default: return null;
        }
    }

    /** perform(int,int) la method instance -> tham so nam o local 1 va 2. */
    static void patchPerform(ClassNode engine) {
        MethodNode m = method(engine, sym("engine.method.perform"), "(II)V");
        if ((m.access & ACC_STATIC) != 0) throw new RuntimeException("perform khong nen la static");
        LabelNode cont = new LabelNode();
        InsnList pre = new InsnList();
        pre.add(new VarInsnNode(ILOAD, 1));
        pre.add(new VarInsnNode(ILOAD, 2));
        pre.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "handleMenuAction", "(II)Z", false));
        pre.add(new JumpInsnNode(IFEQ, cont));
        pre.add(new InsnNode(RETURN));
        pre.add(cont);
        m.instructions.insert(pre);
        System.out.println("[3] Hook handleMenuAction vao perform(int,int)");
    }

    /**
     * tick() chay moi frame o MOI man hinh.
     *
     * Dat trong CANVAS (canh moc onCanvasUpdate cua HSOSKYPetFeedSupport) chu khong trong
     * ENGINE.update(): ENGINE.update() chi duoc goi tu man hinh game, nen neu dat o do thi
     * mo cua hang cua Dylan la auto dung im giua cua hang.
     */
    static void patchCanvasTick(Map<String, byte[]> entries) {
        String canvasName = sym("class.CANVAS");
        ClassNode canvas = new ClassNode();
        new ClassReader(entries.get(canvasName + ".class")).accept(canvas, 0);
        MethodNode m = method(canvas, sym("canvas.tickAll"), "()V");
        if ((m.access & ACC_STATIC) == 0) throw new RuntimeException("canvas.tickAll phai la static");
        int hooks = insertBeforeReturns(m, null, "tick", "()V");
        if (hooks == 0) throw new RuntimeException("canvas.tickAll khong co RETURN nao de hook");
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        canvas.accept((ClassVisitor) cw);
        entries.put(canvasName + ".class", cw.toByteArray());
        System.out.println("[4] Hook tick() vao CANVAS (chay moi man hinh, " + hooks + " diem return)");
    }

    /**
     * Observe a manual soft-key open at MENUBOX.show without blocking the
     * native menu. The one-shot login ENTER remains active for an automatic
     * Tự động popup, but cannot arrive one frame later and close a menu the
     * player just opened with ENTER.
     */
    static void patchNativeMenuOpenObserver(Map<String, byte[]> entries) {
        String menuName = sym("class.MENUBOX");
        String showName = sym("menubox.show");
        String showDesc = "(L" + sym("class.VEC")
                + ";ILjava/lang/String;ZL" + sym("class.VEC") + ";)V";
        byte[] before = entries.get(menuName + ".class");
        require(before != null, "Thieu MENUBOX de hook manual auto menu");
        ClassNode menu = new ClassNode();
        new ClassReader(before).accept(menu, 0);
        MethodNode show = method(menu, showName, showDesc);
        InsnList hook = new InsnList();
        // MENUBOX.show(Vector, int, String title, boolean, Vector): title=3.
        hook.add(new VarInsnNode(ALOAD, 3));
        hook.add(new MethodInsnNode(INVOKESTATIC, LEGACY_SUPPORT,
                "observeNativeMenuOpen", "(Ljava/lang/String;)V", false));
        show.instructions.insert(hook);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        menu.accept((ClassVisitor) cw);
        entries.put(menuName + ".class", cw.toByteArray());
        System.out.println("[4b-menu] Observe manual Tự động open; native MENUBOX.show remains enabled");
    }

    /**
     * The golden native support retries horse item use forever once its
     * persistent flag is true. Route that one call through the generated
     * facade so lifecycle code can make a bounded request instead.
     */
    static void patchNativeHorseMountGate(Map<String, byte[]> entries) {
        String name = "HSOSKYPetFeedSupport";
        ClassNode pet = new ClassNode();
        byte[] before = entries.get(name + ".class");
        if (before == null) throw new RuntimeException("Thieu " + name + ".class");
        new ClassReader(before).accept(pet, 0);
        MethodNode update = method(pet, "onCanvasUpdate", "()V");
        int changed = 0;
        for (AbstractInsnNode n = update.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) n;
            if (name.equals(call.owner) && "checkAndMountHorse".equals(call.name)
                    && "()V".equals(call.desc)) {
                call.owner = SUPPORT;
                call.name = "nativeHorseMountTick";
                call.itf = false;
                changed++;
            }
        }
        require(changed == 1, "onCanvasUpdate phai co dung mot lan goi checkAndMountHorse");
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        pet.accept((ClassVisitor) cw);
        entries.put(name + ".class", cw.toByteArray());
        System.out.println("[3h] Gate native auto-horse retry qua " + SUPPORT);
    }

    /**
     * The golden native auto-login loop uses a permissive isPlayerInGame().
     * During the logo/loading transition the old player object can still be
     * non-null, so that call clears isDisconnected and prevents the bridge
     * from observing the disconnect. Route only this first onCanvasUpdate()
     * probe through the strict world-screen check in the generated facade.
     */
    static void patchNativeLoginWorldGuard(Map<String, byte[]> entries) {
        String name = "HSOSKYPetFeedSupport";
        ClassNode pet = new ClassNode();
        byte[] before = entries.get(name + ".class");
        if (before == null) throw new RuntimeException("Thieu " + name + ".class");
        new ClassReader(before).accept(pet, 0);
        MethodNode update = method(pet, "onCanvasUpdate", "()V");
        int changed = 0;
        for (AbstractInsnNode n = update.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) n;
            if (name.equals(call.owner) && "isPlayerInGame".equals(call.name)
                    && "()Z".equals(call.desc)) {
                if (changed != 0) {
                    throw new RuntimeException("onCanvasUpdate co nhieu probe isPlayerInGame");
                }
                call.owner = SUPPORT;
                call.name = "nativeWorldReadyForLogin";
                call.itf = false;
                changed++;
            }
        }
        require(changed == 1,
                "khong thay probe isPlayerInGame dau tien trong onCanvasUpdate");
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        pet.accept((ClassVisitor) cw);
        entries.put(name + ".class", cw.toByteArray());
        System.out.println("[3h-login] Native login chi coi la vao game khi screen/map san sang");
    }

    /**
     * Find the native no-arg static entry point that really starts the socket
     * worker.  HSOSKYPetFeedSupport.onDisconnected() only updates lifecycle
     * flags; it does not call Connector.open(), so replacing that call with a
     * UI/disconnect handler can leave the client spinning on a dead sender.
     *
     * The native entry point is located structurally:
     *   socket ctor -> Connector.open("socket://...")
     *   worker.run() -> socket ctor
     *   static ()V factory -> worker ctor + Thread.start()
     * This keeps the patch independent of the obfuscated class names while
     * proving that the generated bridge invokes the actual transport path.
     */
    static void patchLegacyTransportReconnect(Map<String, byte[]> entries) {
        NativeTransportStarter starter = findNativeTransportStarter(entries);
        NativeTransportCloser closer = findNativeTransportCloser(entries, starter);

        ClassNode legacy = new ClassNode();
        String legacyEntry = LEGACY_SUPPORT + ".class";
        byte[] legacyBytes = entries.get(legacyEntry);
        if (legacyBytes == null) throw new RuntimeException("Thieu " + legacyEntry);
        new ClassReader(legacyBytes).accept(legacy, 0);
        MethodNode kick = method(legacy, "forceTransportReconnectBeforeLogin", "()V");
        int changed = 0;
        for (AbstractInsnNode n = kick.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) n;
            if (!"HSOSKYPetFeedSupport".equals(call.owner)
                    || !"onDisconnected".equals(call.name)
                    || !"()V".equals(call.desc)) continue;
            // Keep the support lifecycle reset, close both streams owned by
            // the old transport, then start one genuinely fresh native
            // socket/reader thread. Without the close, old and new readers
            // can race over the same singleton state on live servers.
            InsnList reconnect = new InsnList();
            reconnect.add(new MethodInsnNode(INVOKESTATIC, closer.owner,
                    closer.getterName, closer.getterDesc, false));
            reconnect.add(new MethodInsnNode(INVOKEVIRTUAL, closer.owner,
                    closer.closeName, closer.closeDesc, false));
            reconnect.add(new MethodInsnNode(INVOKESTATIC,
                    starter.owner, starter.name, starter.desc, false));
            kick.instructions.insert(n, reconnect);
            changed++;
        }
        require(changed == 1,
                "forceTransportReconnectBeforeLogin() thieu reset disconnect");
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        legacy.accept((ClassVisitor) cw);
        entries.put(legacyEntry, cw.toByteArray());
        System.out.println("[7a-login] Delayed login retry starts native socket factory "
                + shorten(starter.owner) + "." + shorten(starter.name)
                + " after closing the previous transport");
    }

    static final class NativeTransportStarter {
        final String owner;
        final String name;
        final String desc;
        final String transportOwner;

        NativeTransportStarter(String owner, String name, String desc,
                String transportOwner) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.transportOwner = transportOwner;
        }
    }

    static final class NativeTransportCloser {
        final String owner;
        final String getterName;
        final String getterDesc;
        final String closeName;
        final String closeDesc;

        NativeTransportCloser(String owner, String getterName, String getterDesc,
                String closeName, String closeDesc) {
            this.owner = owner;
            this.getterName = getterName;
            this.getterDesc = getterDesc;
            this.closeName = closeName;
            this.closeDesc = closeDesc;
        }
    }

    /** Native login-screen method called by triggerLogin(4, 0) after the socket factory. */
    static final class NativeLoginDispatch {
        final String owner;
        final String name;
        final String desc;
        final int opcode;

        NativeLoginDispatch(String owner, String name, String desc, int opcode) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.opcode = opcode;
        }
    }

    /**
     * Locate the stateful login method used by the unmodified client.  Calling
     * SENDER's six-string serializer directly writes a packet, but skips the
     * native login-screen state transitions that precede it.  The native
     * triggerLogin(4, 0) switch is a stable structural anchor: case 4 calls
     * the login-screen (String,String)V method after starting the transport.
     */
    static NativeLoginDispatch findNativeLoginDispatch(Map<String, byte[]> entries) {
        String supportEntry = "HSOSKYPetFeedSupport.class";
        byte[] supportBytes = entries.get(supportEntry);
        require(supportBytes != null, "Thieu " + supportEntry + " de tim native login dispatch");
        ClassNode support = new ClassNode();
        new ClassReader(supportBytes).accept(support, 0);
        MethodNode trigger = method(support, "triggerLogin", "()V");
        String loginOwner = null;
        for (AbstractInsnNode n = trigger.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) n;
            if ("(II)V".equals(call.desc) && call.getOpcode() == INVOKEVIRTUAL) {
                if (loginOwner != null && !loginOwner.equals(call.owner)) {
                    throw new RuntimeException("triggerLogin co nhieu login-screen owner");
                }
                loginOwner = call.owner;
            }
        }
        require(loginOwner != null,
                "triggerLogin khong goi method login-screen (II)V");

        byte[] loginBytes = entries.get(loginOwner + ".class");
        require(loginBytes != null, "Thieu class login-screen " + loginOwner);
        ClassNode login = new ClassNode();
        new ClassReader(loginBytes).accept(login, 0);
        String requestDesc = "(Ljava/lang/String;Ljava/lang/String;)V";
        NativeLoginDispatch found = null;
        for (MethodNode m : login.methods) {
            if (!"(II)V".equals(m.desc) || m.instructions == null) continue;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!(n instanceof TableSwitchInsnNode)) continue;
                TableSwitchInsnNode sw = (TableSwitchInsnNode) n;
                if (sw.min > 4 || sw.max < 4) continue;
                LabelNode caseFour = sw.labels.get(4 - sw.min);
                int steps = 0;
                for (AbstractInsnNode x = caseFour; x != null && steps++ < 260; x = x.getNext()) {
                    if (!(x instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) x;
                    if (!loginOwner.equals(call.owner) || !requestDesc.equals(call.desc)
                            || (call.getOpcode() != INVOKEVIRTUAL
                                && call.getOpcode() != INVOKESTATIC)) continue;
                    if (found != null && (!found.name.equals(call.name)
                            || !found.owner.equals(call.owner))) {
                        throw new RuntimeException("Nhieu login-screen request method cho case 4");
                    }
                    found = new NativeLoginDispatch(call.owner, call.name, call.desc,
                            call.getOpcode());
                    break;
                }
            }
        }
        require(found != null,
                "case triggerLogin(4,0) khong goi login-screen (String,String)V");
        return found;
    }

    static NativeTransportStarter findNativeTransportStarter(Map<String, byte[]> entries) {
        String socketOwner = null;
        String socketCtorDesc = null;
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            for (MethodNode m : cn.methods) {
                if (!"<init>".equals(m.name)
                        || !"(Ljava/lang/String;I)V".equals(m.desc)) continue;
                if (!callsConnectorSocketOpen(m)) continue;
                if (socketOwner != null && !socketOwner.equals(cn.name)) {
                    throw new RuntimeException("Nhieu socket wrapper co Connector.open");
                }
                socketOwner = cn.name;
                socketCtorDesc = m.desc;
            }
        }
        require(socketOwner != null,
                "khong tim thay socket wrapper ctor goi Connector.open");

        String workerOwner = null;
        String workerCtorDesc = null;
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            for (MethodNode m : cn.methods) {
                if (!"run".equals(m.name) || !"()V".equals(m.desc)) continue;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (!(n instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) n;
                    if (!socketOwner.equals(call.owner)
                            || !"<init>".equals(call.name)
                            || !socketCtorDesc.equals(call.desc)) continue;
                    if (workerOwner != null && !workerOwner.equals(cn.name)) {
                        throw new RuntimeException("Nhieu native socket worker class");
                    }
                    workerOwner = cn.name;
                    workerCtorDesc = findConstructorDesc(cn);
                }
            }
        }
        require(workerOwner != null,
                "khong tim thay worker.run() tao socket wrapper");

        NativeTransportStarter found = null;
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            for (MethodNode m : cn.methods) {
                if (!"()V".equals(m.desc) || (m.access & ACC_STATIC) == 0) continue;
                if (!callsMethod(m, workerOwner, "<init>", workerCtorDesc)
                        || !callsOwnerName(m, "java/lang/Thread", "start")) continue;
                if (found != null) {
                    throw new RuntimeException("Nhieu native transport factory ()V");
                }
                    found = new NativeTransportStarter(cn.name, m.name, m.desc,
                            firstObjectArgument(workerCtorDesc));
            }
        }
        require(found != null,
                "khong tim thay native transport factory tao worker va start thread");
        return found;
    }

    static String firstObjectArgument(String desc) {
        if (desc == null || !desc.startsWith("(L")) {
            throw new RuntimeException("worker ctor khong co parent object: " + desc);
        }
        int end = desc.indexOf(';', 2);
        if (end < 0) throw new RuntimeException("worker ctor parent descriptor loi: " + desc);
        return desc.substring(2, end);
    }

    static NativeTransportCloser findNativeTransportCloser(
            Map<String, byte[]> entries, NativeTransportStarter starter) {
        String owner = starter.transportOwner;
        byte[] before = entries.get(owner + ".class");
        require(before != null, "Thieu native transport parent " + shorten(owner));
        ClassNode parent = new ClassNode();
        new ClassReader(before).accept(parent, 0);

        MethodNode getter = null;
        MethodNode closer = null;
        String getterDesc = "()L" + owner + ";";
        for (MethodNode m : parent.methods) {
            if ((m.access & ACC_STATIC) != 0 && getterDesc.equals(m.desc)) {
                if (getter != null) throw new RuntimeException(
                        "Nhieu singleton getter cua native transport parent");
                getter = m;
            }
            if ((m.access & ACC_STATIC) == 0 && "()V".equals(m.desc)
                    && callsOwnerName(m, "java/io/DataOutputStream", "close")
                    && callsOwnerName(m, "java/io/DataInputStream", "close")) {
                if (closer != null) throw new RuntimeException(
                        "Nhieu close method cua native transport parent");
                closer = m;
            }
        }
        require(getter != null, "Khong tim thay singleton getter cua transport parent");
        require(closer != null, "Khong tim thay close DataInput/DataOutput cua transport parent");
        return new NativeTransportCloser(owner, getter.name, getterDesc,
                closer.name, closer.desc);
    }

    static String findConstructorDesc(ClassNode cn) {
        for (MethodNode m : cn.methods) {
            if ("<init>".equals(m.name)
                    && m.desc.endsWith(";Ljava/lang/String;I)V")) {
                return m.desc;
            }
        }
        throw new RuntimeException("worker khong co ctor nhan parent: " + shorten(cn.name));
    }

    static boolean callsConnectorSocketOpen(MethodNode m) {
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) n;
            if ("javax/microedition/io/Connector".equals(call.owner)
                    && "open".equals(call.name)
                    && "(Ljava/lang/String;)Ljavax/microedition/io/Connection;".equals(call.desc)) {
                return true;
            }
        }
        return false;
    }

    static boolean callsMethod(MethodNode m, String owner, String name, String desc) {
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) n;
            if (owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) {
                return true;
            }
        }
        return false;
    }

    static boolean callsOwnerName(MethodNode m, String owner, String name) {
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) n;
            if (owner.equals(call.owner) && name.equals(call.name)) return true;
        }
        return false;
    }

    /**
     * The original HSOSKYPetFeedSupport.showLoginScreen() is not a pure screen
     * setter: the obfuscated login object also performs the local_server
     * bootstrap and reads a stream. Re-entering that method from a reconnect
     * watchdog can block forever when the bootstrap endpoint is unavailable.
     * Rewrite the generated placeholder to select the already-created login
     * object directly through the versioned CANVAS.currentScreen field.
     */
    static void patchLoginSurfaceDirect(Map<String, byte[]> entries) {
        String canvasName = sym("class.CANVAS");
        String screenName = sym("canvas.currentScreen");
        byte[] canvasBytes = entries.get(canvasName + ".class");
        require(canvasBytes != null, "Thieu class CANVAS de patch login surface");
        ClassNode canvas = new ClassNode();
        new ClassReader(canvasBytes).accept(canvas, 0);
        FieldNode screen = null;
        for (FieldNode field : canvas.fields) {
            if (screenName.equals(field.name) && (field.access & ACC_STATIC) != 0) {
                screen = field;
                break;
            }
        }
        require(screen != null && screen.desc.startsWith("L") && screen.desc.endsWith(";"),
                "canvas.currentScreen khong phai static reference");

        String legacyEntry = LEGACY_SUPPORT + ".class";
        byte[] legacyBytes = entries.get(legacyEntry);
        require(legacyBytes != null, "Thieu " + legacyEntry + " de patch login surface");
        ClassNode legacy = new ClassNode();
        new ClassReader(legacyBytes).accept(legacy, 0);
        MethodNode surface = method(legacy, "forceLoginSurface", "()V");
        surface.instructions.clear();
        surface.instructions.add(new MethodInsnNode(INVOKESTATIC,
                "HSOSKYPetFeedSupport", "getLoginScreen", "()Ljava/lang/Object;", false));
        surface.instructions.add(new TypeInsnNode(CHECKCAST,
                screen.desc.substring(1, screen.desc.length() - 1)));
        surface.instructions.add(new FieldInsnNode(PUTSTATIC, canvasName,
                screen.name, screen.desc));
        surface.instructions.add(new InsnNode(RETURN));

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        legacy.accept((ClassVisitor) cw);
        entries.put(legacyEntry, cw.toByteArray());

        // HSOSKYPetFeedSupport.onCanvasUpdate() also calls its own
        // showLoginScreen() after a disconnect. Leaving that method native
        // would re-enter the local_server bootstrap after the bridge had
        // already selected the login object directly, recreating the live
        // logo/spinner. Keep the timestamp reset, but make this support-owned
        // entry point a pure surface assignment too.
        String petEntry = "HSOSKYPetFeedSupport.class";
        byte[] petBytes = entries.get(petEntry);
        require(petBytes != null, "Thieu " + petEntry + " de patch login surface");
        ClassNode pet = new ClassNode();
        new ClassReader(petBytes).accept(pet, 0);
        MethodNode petSurface = method(pet, "showLoginScreen", "()V");
        petSurface.instructions.clear();
        petSurface.instructions.add(new InsnNode(LCONST_0));
        petSurface.instructions.add(new FieldInsnNode(PUTSTATIC, "HSOSKYPetFeedSupport",
                "loginScreenEnterTime", "J"));
        petSurface.instructions.add(new MethodInsnNode(INVOKESTATIC,
                "HSOSKYPetFeedSupport", "getLoginScreen", "()Ljava/lang/Object;", false));
        petSurface.instructions.add(new TypeInsnNode(CHECKCAST,
                screen.desc.substring(1, screen.desc.length() - 1)));
        petSurface.instructions.add(new FieldInsnNode(PUTSTATIC, canvasName,
                screen.name, screen.desc));
        petSurface.instructions.add(new InsnNode(RETURN));
        ClassWriter petWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        pet.accept((ClassVisitor) petWriter);
        entries.put(petEntry, petWriter.toByteArray());
        System.out.println("[7a-login] Retry selects existing login screen without local_server bootstrap");
    }

    /**
     * The server's login cooldown is an incoming opcode 2 packet.  Its native
     * handler consumes the text in an internal dialog object, so the text does
     * not pass through CANVAS.showDialog().  Observe the actual packet before
     * the native String-only presenter consumes it; otherwise the ten-second
     * countdown is visible but the generated retry state is never armed.
     */
    static void patchLoginCooldownPacketHandler(Map<String, byte[]> entries) {
        ClassNode foundClass = null;
        MethodNode foundMethod = null;
        MethodInsnNode foundPresenter = null;

        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            if ("HSOSKYPetFeedSupport".equals(cn.name)
                    || LEGACY_SUPPORT.equals(cn.name)) continue;
            for (MethodNode m : cn.methods) {
                if (m.instructions == null || (m.access & ACC_STATIC) == 0
                        || (m.access & ACC_ABSTRACT) != 0
                        || !m.desc.startsWith("(L") || !m.desc.endsWith(")V")) {
                    continue;
                }
                MethodInsnNode firstReadUtf = null;
                MethodInsnNode presenter = null;
                boolean hasReadByte = false;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (!(n instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) n;
                    if ("java/io/DataInputStream".equals(call.owner)
                            && "readUTF".equals(call.name)
                            && "()Ljava/lang/String;".equals(call.desc)
                            && firstReadUtf == null) {
                        firstReadUtf = call;
                        continue;
                    }
                    if ("java/io/DataInputStream".equals(call.owner)
                            && "readByte".equals(call.name)
                            && "()B".equals(call.desc)) {
                        hasReadByte = true;
                    }
                }
                if (firstReadUtf == null || !hasReadByte) continue;

                AbstractInsnNode next = firstReadUtf.getNext();
                while (next != null && next.getOpcode() < 0) next = next.getNext();
                if (!(next instanceof MethodInsnNode)) continue;
                MethodInsnNode candidate = (MethodInsnNode) next;
                if (candidate.getOpcode() != INVOKESTATIC
                        || !"(Ljava/lang/String;)V".equals(candidate.desc)) continue;
                if (foundMethod != null) {
                    throw new RuntimeException("Nhieu handler opcode 2 co dang readUTF + presenter");
                }
                foundClass = cn;
                foundMethod = m;
                foundPresenter = candidate;
            }
        }

        require(foundMethod != null,
                "khong tim thay handler thong bao login opcode 2 (readUTF + presenter)");
        InsnList hook = new InsnList();
        hook.add(new InsnNode(DUP));
        hook.add(new MethodInsnNode(INVOKESTATIC, LEGACY_SUPPORT,
                "observeLoginCooldownFromNetwork", "(Ljava/lang/String;)V", false));
        foundMethod.instructions.insertBefore(foundPresenter, hook);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        foundClass.accept((ClassVisitor) cw);
        entries.put(foundClass.name + ".class", cw.toByteArray());
        System.out.println("[7b-login] Hook thong bao cooldown truc tiep tai handler opcode 2");
    }

    /**
     * Observe the native method that serializes and queues opcode 1 itself.
     * The live server is not required to use the reference server's cooldown
     * response, so a loading watchdog must also be armed from the outbound
     * login request rather than from one particular incoming opcode. Capture
     * arguments at method entry, but confirm the retry only immediately before
     * a normal RETURN. A closed DataOutputStream can throw after entry; that
     * case must remain eligible for the captured serializer fallback.
     */
    static void patchLoginRequestObserver(Map<String, byte[]> entries) {
        final String loginDesc = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
                + "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IB)V";
        ClassNode foundClass = null;
        MethodNode foundMethod = null;

        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            if ("HSOSKYPetFeedSupport".equals(cn.name)
                    || LEGACY_SUPPORT.equals(cn.name)) continue;
            for (MethodNode m : cn.methods) {
                if (!loginDesc.equals(m.desc) || (m.access & ACC_STATIC) != 0
                        || m.instructions == null) continue;
                int utfWrites = 0;
                boolean startsOpcodeOnePacket = false;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (!(n instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) n;
                    if ("java/io/DataOutputStream".equals(call.owner)
                            && "writeUTF".equals(call.name)
                            && "(Ljava/lang/String;)V".equals(call.desc)) {
                        utfWrites++;
                    }
                    if (call.getOpcode() == INVOKEVIRTUAL && "(B)V".equals(call.desc)
                            && call.name.length() > 0) {
                        startsOpcodeOnePacket = true;
                    }
                }
                if (utfWrites < 6 || !startsOpcodeOnePacket) continue;
                if (foundMethod != null) {
                    throw new RuntimeException("Nhieu method serialize login opcode 1");
                }
                foundClass = cn;
                foundMethod = m;
            }
        }

        require(foundMethod != null,
                "khong tim thay method serialize login opcode 1 de arm watchdog");
        InsnList capture = new InsnList();
        capture.add(new VarInsnNode(ALOAD, 0));
        capture.add(new VarInsnNode(ALOAD, 1));
        capture.add(new VarInsnNode(ALOAD, 2));
        capture.add(new VarInsnNode(ALOAD, 3));
        capture.add(new VarInsnNode(ALOAD, 4));
        capture.add(new VarInsnNode(ALOAD, 5));
        capture.add(new VarInsnNode(ALOAD, 6));
        capture.add(new VarInsnNode(ILOAD, 7));
        capture.add(new VarInsnNode(ILOAD, 8));
        capture.add(new MethodInsnNode(INVOKESTATIC, LEGACY_SUPPORT,
                "captureLoginRequest",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
                        + "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IB)V", false));
        capture.add(new MethodInsnNode(INVOKESTATIC, LEGACY_SUPPORT,
                "noteLoginRequestFromNetwork", "()V", false));
        foundMethod.instructions.insert(capture);

        int returns = 0;
        for (AbstractInsnNode n = foundMethod.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() != RETURN) continue;
            InsnList completed = new InsnList();
            completed.add(new MethodInsnNode(INVOKESTATIC, LEGACY_SUPPORT,
                    "noteLoginRequestWriteCompleted", "()V", false));
            foundMethod.instructions.insertBefore(n, completed);
            returns++;
        }
        require(returns > 0,
                "method serialize login opcode 1 khong co RETURN de xac nhan ghi xong");

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        foundClass.accept((ClassVisitor) cw);
        entries.put(foundClass.name + ".class", cw.toByteArray());
        patchLoginRequestReplay(entries, foundClass.name, foundMethod.name, foundMethod.desc);
        System.out.println("[7c-login] Hook outbound opcode-1 login request watchdog");
    }

    /**
     * Replace the generated bridge placeholder with a direct call to the
     * obfuscated serializer found above.  Reusing the captured sender keeps a
     * same-transport retry at the exact packet layer; triggerLogin() remains
     * the fallback when a new transport must be created.
     */
    static void patchLoginRequestReplay(Map<String, byte[]> entries,
            String loginOwner, String loginName, String loginDesc) {
        NativeLoginDispatch nativeLogin = findNativeLoginDispatch(entries);
        String legacyEntry = LEGACY_SUPPORT + ".class";
        byte[] legacyBytes = entries.get(legacyEntry);
        require(legacyBytes != null, "Thieu " + legacyEntry + " de patch replay login");
        ClassNode legacy = new ClassNode();
        new ClassReader(legacyBytes).accept(legacy, 0);
        MethodNode replay = method(legacy, "replayCapturedLoginRequest", "()Z");
        replay.instructions.clear();

        LabelNode directSerializer = new LabelNode();
        LabelNode noReplay = new LabelNode();
        replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                "loginRequestCaptured", "Z"));
        replay.instructions.add(new JumpInsnNode(IFEQ, directSerializer));

        // Re-enter the native stateful login method first. It is the method
        // reached by triggerLogin(4, 0) after the socket factory; unlike a
        // direct SENDER serializer call, it restores the native login flags,
        // screen state, and packet sequencing before writing opcode 1.
        if (nativeLogin.opcode == INVOKESTATIC) {
            // The current client keeps this stateful request helper static;
            // triggerLogin(4,0) calls it after the native factory.
            replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                    "loginRequestUser", "Ljava/lang/String;"));
            replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                    "loginRequestPassword", "Ljava/lang/String;"));
            replay.instructions.add(new MethodInsnNode(INVOKESTATIC, nativeLogin.owner,
                    nativeLogin.name, nativeLogin.desc, false));
        } else {
            replay.instructions.add(new MethodInsnNode(INVOKESTATIC,
                    "HSOSKYPetFeedSupport", "getLoginScreen", "()Ljava/lang/Object;", false));
            replay.instructions.add(new JumpInsnNode(IFNULL, directSerializer));
            replay.instructions.add(new MethodInsnNode(INVOKESTATIC,
                    "HSOSKYPetFeedSupport", "getLoginScreen", "()Ljava/lang/Object;", false));
            replay.instructions.add(new TypeInsnNode(CHECKCAST, nativeLogin.owner));
            replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                    "loginRequestUser", "Ljava/lang/String;"));
            replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                    "loginRequestPassword", "Ljava/lang/String;"));
            replay.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, nativeLogin.owner,
                    nativeLogin.name, nativeLogin.desc, false));
        }
        // The stateful helper is void and can silently refuse to serialize
        // when the live login screen still has a stale internal state. The
        // The post-return outbound opcode-1 hook flips this flag only after
        // the serializer completed all writes. If the old stream throws after
        // the entry hook, continue to the exact captured serializer below.
        replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                "loginRetryLoginPacketSent", "Z"));
        replay.instructions.add(new JumpInsnNode(IFNE, directSerializer));
        replay.instructions.add(new InsnNode(ICONST_1));
        replay.instructions.add(new InsnNode(IRETURN));

        // Conservative fallback for a partially initialized login surface.
        // Resolve SENDER.gI() at replay time for both same-session and rebuilt
        // transports, because the captured object may be pre-logout.
        replay.instructions.add(directSerializer);
        replay.instructions.add(new FrameNode(F_SAME, 0, null, 0, null));
        replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                "loginRequestCaptured", "Z"));
        replay.instructions.add(new JumpInsnNode(IFEQ, noReplay));
        replay.instructions.add(new MethodInsnNode(INVOKESTATIC, loginOwner,
                sym("sender.gI"), "()L" + loginOwner + ";", false));
        replay.instructions.add(new JumpInsnNode(IFNULL, noReplay));
        replay.instructions.add(new MethodInsnNode(INVOKESTATIC, loginOwner,
                sym("sender.gI"), "()L" + loginOwner + ";", false));
        replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                "loginRequestUser", "Ljava/lang/String;"));
        replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                "loginRequestPassword", "Ljava/lang/String;"));
        replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                "loginRequestVersion", "Ljava/lang/String;"));
        replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                "loginRequestRegion", "Ljava/lang/String;"));
        replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                "loginRequestDevice", "Ljava/lang/String;"));
        replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                "loginRequestExtra", "Ljava/lang/String;"));
        replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                "loginRequestSelector", "I"));
        replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                "loginRequestFlags", "B"));
        replay.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, loginOwner,
                loginName, loginDesc, false));
        replay.instructions.add(new InsnNode(ICONST_1));
        replay.instructions.add(new InsnNode(IRETURN));
        replay.instructions.add(noReplay);
        replay.instructions.add(new FrameNode(F_SAME, 0, null, 0, null));
        replay.instructions.add(new InsnNode(ICONST_0));
        replay.instructions.add(new InsnNode(IRETURN));

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        legacy.accept((ClassVisitor) cw);
        entries.put(legacyEntry, cw.toByteArray());
        System.out.println("[7e-login] Replay captured opcode-1 on current sender");
    }

    /**
     * Observe the native opcode-13 character-entry serializer. The client
     * protocol uses opcode 1 to request/select the character list and a
     * separate opcode 13 carrying charId to actually enter the world. A
     * cooldown after logout can reject the latter, so replaying opcode 1
     * alone is insufficient.
     */
    static void patchLoginEnterObserver(Map<String, byte[]> entries) {
        String senderName = sym("class.SENDER");
        ClassNode sender = new ClassNode();
        byte[] before = entries.get(senderName + ".class");
        require(before != null, "Thieu class SENDER de hook opcode-13 login");
        new ClassReader(before).accept(sender, 0);
        MethodNode found = null;
        for (MethodNode m : sender.methods) {
            if ((m.access & ACC_STATIC) != 0 || !"(I)V".equals(m.desc)
                    || m.instructions == null) continue;
            boolean hasOpcode13 = false;
            boolean hasWriteInt = false;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof IntInsnNode && n.getOpcode() == BIPUSH
                        && ((IntInsnNode) n).operand == 13) {
                    hasOpcode13 = true;
                }
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) n;
                    if ("java/io/DataOutputStream".equals(call.owner)
                            && "writeInt".equals(call.name)
                            && "(I)V".equals(call.desc)) {
                        hasWriteInt = true;
                    }
                }
            }
            if (!hasOpcode13 || !hasWriteInt) continue;
            if (found != null) throw new RuntimeException(
                    "Nhieu method serialize opcode-13 charId");
            found = m;
        }
        require(found != null,
                "khong tim thay method serialize opcode-13 vao game");

        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, 0));
        hook.add(new VarInsnNode(ILOAD, 1));
        hook.add(new MethodInsnNode(INVOKESTATIC, LEGACY_SUPPORT,
                "captureLoginEnterRequest", "(Ljava/lang/Object;I)V", false));
        found.instructions.insert(hook);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        sender.accept((ClassVisitor) cw);
        entries.put(senderName + ".class", cw.toByteArray());
        patchLoginEnterReplay(entries, senderName, found.name, found.desc);
        System.out.println("[7g-login] Hook outbound opcode-13 enter-world request");
    }

    /** Rewrite the generated bridge to replay char-entry on the current sender. */
    static void patchLoginEnterReplay(Map<String, byte[]> entries,
            String senderOwner, String senderName, String senderDesc) {
        String legacyEntry = LEGACY_SUPPORT + ".class";
        byte[] legacyBytes = entries.get(legacyEntry);
        require(legacyBytes != null, "Thieu " + legacyEntry + " de patch replay opcode-13");
        ClassNode legacy = new ClassNode();
        new ClassReader(legacyBytes).accept(legacy, 0);
        MethodNode replay = method(legacy, "replayCapturedLoginEnterRequest", "()Z");
        replay.instructions.clear();

        LabelNode noReplay = new LabelNode();
        replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                "loginEnterCaptured", "Z"));
        replay.instructions.add(new JumpInsnNode(IFEQ, noReplay));
        // The char id is captured from the previous native request, but the
        // sender object must be resolved again after a fresh socket factory.
        // SENDER.gI() is the client's current singleton; using the captured
        // object here would send opcode 13 to the pre-logout transport.
        replay.instructions.add(new MethodInsnNode(INVOKESTATIC, senderOwner,
                sym("sender.gI"), "()L" + senderOwner + ";", false));
        replay.instructions.add(new FieldInsnNode(GETSTATIC, LEGACY_SUPPORT,
                "loginEnterCharId", "I"));
        replay.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, senderOwner,
                senderName, senderDesc, false));
        replay.instructions.add(new InsnNode(ICONST_1));
        replay.instructions.add(new InsnNode(IRETURN));
        replay.instructions.add(noReplay);
        replay.instructions.add(new FrameNode(F_SAME, 0, null, 0, null));
        replay.instructions.add(new InsnNode(ICONST_0));
        replay.instructions.add(new InsnNode(IRETURN));

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        legacy.accept((ClassVisitor) cw);
        entries.put(legacyEntry, cw.toByteArray());
        System.out.println("[7h-login] Replay opcode-13 char-entry on current sender");
    }

    /**
     * Mark a rebuilt transport ready only after the native reader has consumed
     * handshake -40, folded the key, and enabled encrypted frames. Sending the
     * login packet immediately after starting the socket thread races this step.
     */
    static void patchLoginHandshakeReady(Map<String, byte[]> entries) {
        ClassNode foundClass = null;
        MethodNode foundMethod = null;
        AbstractInsnNode foundEnable = null;

        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            if ("HSOSKYPetFeedSupport".equals(cn.name)
                    || LEGACY_SUPPORT.equals(cn.name)) continue;
            for (MethodNode m : cn.methods) {
                if (m.instructions == null) continue;
                boolean hasHandshakeSwitch = false;
                boolean hasReadByte = false;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n instanceof TableSwitchInsnNode) {
                        TableSwitchInsnNode sw = (TableSwitchInsnNode) n;
                        if (sw.min <= -40 && sw.max >= -40) hasHandshakeSwitch = true;
                    }
                    if (!(n instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) n;
                    if ("java/io/DataInputStream".equals(call.owner)
                            && "readByte".equals(call.name)
                            && "()B".equals(call.desc)) {
                        hasReadByte = true;
                    }
                }
                if (!hasHandshakeSwitch || !hasReadByte) continue;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTFIELD) continue;
                    FieldInsnNode field = (FieldInsnNode) n;
                    if (!"Z".equals(field.desc)) continue;
                    AbstractInsnNode prev = previousReal(n);
                    AbstractInsnNode next = nextReal(n);
                    if (prev == null || prev.getOpcode() != ICONST_1
                            || !(next instanceof JumpInsnNode)
                            || next.getOpcode() != GOTO) continue;
                    if (foundEnable != null) {
                        throw new RuntimeException("Nhieu diem bat handshake -40 trong reader");
                    }
                    foundClass = cn;
                    foundMethod = m;
                    foundEnable = n;
                }
            }
        }

        require(foundEnable != null,
                "khong tim thay diem native bat encryption sau handshake -40");
        InsnList hook = new InsnList();
        hook.add(new MethodInsnNode(INVOKESTATIC, LEGACY_SUPPORT,
                "onTransportHandshakeReady", "()V", false));
        foundMethod.instructions.insert(foundEnable, hook);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        foundClass.accept((ClassVisitor) cw);
        entries.put(foundClass.name + ".class", cw.toByteArray());
        System.out.println("[7d-login] Hook native handshake -40 readiness");
    }

    /**
     * Observe the first inbound opcode before the native dispatch switch.
     * Opcode 2 is the server-side post-logout cooldown in the protocol used
     * by the live client. This hook is deliberately independent of the UTF
     * presenter so an encoding/handler variation cannot leave the retry
     * unarmed while the native countdown is already visible.
     */
    static void patchIncomingOpcodeObserver(Map<String, byte[]> entries) {
        ClassNode foundClass = null;
        MethodNode foundMethod = null;
        VarInsnNode foundStore = null;

        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            if ("HSOSKYPetFeedSupport".equals(cn.name)
                    || LEGACY_SUPPORT.equals(cn.name)) continue;
            for (MethodNode m : cn.methods) {
                if (m.instructions == null) continue;
                boolean hasHandshakeSwitch = false;
                boolean hasReadByte = false;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n instanceof TableSwitchInsnNode) {
                        TableSwitchInsnNode sw = (TableSwitchInsnNode) n;
                        if (sw.min <= -40 && sw.max >= -40) hasHandshakeSwitch = true;
                    }
                    if (n instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) n;
                        if ("java/io/DataInputStream".equals(call.owner)
                                && "readByte".equals(call.name)
                                && "()B".equals(call.desc)) {
                            hasReadByte = true;
                        }
                    }
                }
                if (!hasHandshakeSwitch || !hasReadByte) continue;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (!(n instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) n;
                    if (!"java/io/DataInputStream".equals(call.owner)
                            || !"readByte".equals(call.name)
                            || !"()B".equals(call.desc)) continue;
                    AbstractInsnNode store = nextReal(n);
                    if (!(store instanceof VarInsnNode) || store.getOpcode() != ISTORE) continue;
                    if (foundStore != null) {
                        throw new RuntimeException("Nhieu reader inbound opcode - phai xem lai");
                    }
                    foundClass = cn;
                    foundMethod = m;
                    foundStore = (VarInsnNode) store;
                    break;
                }
            }
        }

        require(foundStore != null,
                "khong tim thay diem luu inbound opcode dau tien trong reader");
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ILOAD, foundStore.var));
        hook.add(new MethodInsnNode(INVOKESTATIC, LEGACY_SUPPORT,
                "observeIncomingOpcode", "(I)V", false));
        foundMethod.instructions.insert(foundStore, hook);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        foundClass.accept((ClassVisitor) cw);
        entries.put(foundClass.name + ".class", cw.toByteArray());
        System.out.println("[7e-login] Observe inbound opcode before native dispatch");
    }

    /**
     * Observe every inbound UTF field as a server-independent fallback. The
     * reference build puts the cooldown in opcode 2, but a live server can
     * route the same text through another packet handler. NoticeReader filters
     * ordinary names/labels, so observing all UTF payloads is safe and avoids
     * making the retry depend on one opcode number.
     */
    static void patchAllIncomingUtfObservers(Map<String, byte[]> entries) {
        int hooks = 0;
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            if ("HSOSKYPetFeedSupport".equals(cn.name)
                    || LEGACY_SUPPORT.equals(cn.name)) continue;

            int here = 0;
            for (MethodNode m : cn.methods) {
                if (m.instructions == null) continue;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (!(n instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) n;
                    if (!"java/io/DataInputStream".equals(call.owner)
                            || !"readUTF".equals(call.name)
                            || !"()Ljava/lang/String;".equals(call.desc)) continue;
                    if (hasCooldownObserverAfter(n)) continue;
                    InsnList hook = new InsnList();
                    hook.add(new InsnNode(DUP));
                    hook.add(new MethodInsnNode(INVOKESTATIC, LEGACY_SUPPORT,
                            "observeLoginCooldownFromNetwork", "(Ljava/lang/String;)V", false));
                    m.instructions.insert(n, hook);
                    here++;
                }
            }
            if (here == 0) continue;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept((ClassVisitor) cw);
            e.setValue(cw.toByteArray());
            hooks += here;
        }
        require(hooks > 0, "khong tim thay readUTF inbound de observe cooldown tong quat");
        System.out.println("[7f-login] Hook tong quat " + hooks
                + " inbound readUTF cho cooldown server");
    }

    static boolean hasCooldownObserverAfter(AbstractInsnNode readUtf) {
        AbstractInsnNode next = nextReal(readUtf);
        if (next == null || next.getOpcode() != DUP) return false;
        next = nextReal(next);
        if (!(next instanceof MethodInsnNode)) return false;
        MethodInsnNode call = (MethodInsnNode) next;
        return LEGACY_SUPPORT.equals(call.owner)
                && "observeLoginCooldownFromNetwork".equals(call.name)
                && "(Ljava/lang/String;)V".equals(call.desc);
    }

    /** Prevent only the exact auto-horse failure dialogs from stealing focus. */
    static void patchHorseDialogGate(Map<String, byte[]> entries) {
        String canvasName = sym("class.CANVAS");
        ClassNode canvas = new ClassNode();
        new ClassReader(entries.get(canvasName + ".class")).accept(canvas, 0);
        MethodNode showDialog = method(canvas, sym("canvas.showDialog"),
                "(Ljava/lang/String;)V");
        if ((showDialog.access & ACC_STATIC) == 0) {
            throw new RuntimeException("canvas.showDialog phai la static");
        }
        LabelNode continueLabel = new LabelNode();
        InsnList pre = new InsnList();
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new MethodInsnNode(INVOKESTATIC, SUPPORT,
                "shouldSuppressHorseDialog", "(Ljava/lang/String;)Z", false));
        pre.add(new JumpInsnNode(IFEQ, continueLabel));
        pre.add(new InsnNode(RETURN));
        pre.add(continueLabel);
        showDialog.instructions.insert(pre);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        canvas.accept((ClassVisitor) cw);
        entries.put(canvasName + ".class", cw.toByteArray());
        System.out.println("[3i] Gate horse error dialogs at CANVAS.showDialog");
    }

    /**
     * Bat lay doi tuong man hinh cua hang ngay khi no duoc dung xong, va mo quyen doc hai
     * field ma support class can (loai cua hang + danh sach hang, ca hai deu private).
     * MakeAutoTradeCompileJar mo dung hai field nay tren jar dung de bien dich, nen luc
     * chay va luc bien dich khop nhau.
     */
    /**
     * Noi bo loc muc tieu cua Auto Tu Danh qua ham cua minh.
     *
     * Ban mod de Auto Tu Danh chon muc tieu tren CA BAN DO. Ngoai pho ban thi tien, nhung
     * trong Nga tu tu than thi hong: quai vao tung dot tu mot cua o ria ban do, nen no khoa
     * ngay vao con o tan dang xa va nhan lai "Muc tieu o qua xa", trong khi con ngay canh
     * thi khong danh.
     *
     * Khong sua HSOSKYPetFeedSupport (nguon tren dia cu hon class trong jar), ma doi CHO GOI
     * trong lop PLAYER sang ham cua mod nay. Ham do goi lai dung ham cu, chi them mot dieu
     * kien tam danh va CHI khi Auto Nga 4 dang chay - moi thu khac giu nguyen hanh vi.
     */
    /**
      * Chan Auto Tu Danh ghi duong di vao player.path.
      *
      * Hai tinh nang cung ghi player.path thi nhan vat chay vong vong, khong di het duong
      * nao. Quet ca client: chi co DUNG MOT cho gan duong di that (ket qua findPath) vao
      * player.path, nam trong vong auto danh; moi cho khac chi gan null de xoa duong. Chen
      * mot loi goi loc ngay truoc lenh gan do - dung mot cho, khong dung gi khac.
      *
      * player.path khai bao tren ENTITY (lop cha) chu khong phai PLAYER, nen phai tim theo
      * ten + kieu chu khong theo lop so huu.
      */
    static void patchAutoAttackPath(Map<String, byte[]> entries) {
        String pathField = sym("player.path");
        String pathOwner = sym("class.ENTITY");     // path khai bao tren lop cha
        String findPath = sym("screen.findPath");
        int hooks = 0;
        /*
         * CHI trong lop PLAYER. Cac thuc the khac (quai, pet) cung duoc gan duong tu findPath
         * vao cung field do - toan jar co 9 cho - nhung do la viec cua chung, khong dinh gi
         * toi quyen di chuyen cua nguoi choi.
         */
        String playerName = sym("class.PLAYER");
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().equals(playerName + ".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            boolean touched = false;
            for (MethodNode m : cn.methods) {
                if (m.instructions == null) continue;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n.getOpcode() != PUTFIELD) continue;
                    FieldInsnNode f = (FieldInsnNode) n;
                    // Ten obfuscate bi dung lai tren nhieu lop -> phai xet ca lop so huu.
                    if (!pathField.equals(f.name) || !"[S".equals(f.desc)
                            || !pathOwner.equals(f.owner)) continue;
                    AbstractInsnNode prev = n.getPrevious();
                    while (prev != null && prev.getOpcode() < 0) prev = prev.getPrevious();
                    if (!(prev instanceof MethodInsnNode)) continue;   // gan null -> bo qua
                    MethodInsnNode call = (MethodInsnNode) prev;
                    if (!findPath.equals(call.name) || !"(IIIII)[S".equals(call.desc)) continue;
                    m.instructions.insertBefore(n, new MethodInsnNode(
                            INVOKESTATIC, SUPPORT, "autoAttackPath", "([S)[S", false));
                    hooks++;
                    touched = true;
                }
            }
            if (!touched) continue;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept((ClassVisitor) cw);
            e.setValue(cw.toByteArray());
        }
        require(hooks == 1, "mong doi DUNG MOT cho auto danh gan duong di vao player.path,"
                + " tim thay " + hooks + " - cau truc jar da khac, dung doan");
        System.out.println("[6g] Chan Auto Tu Danh gianh quyen di chuyen (" + hooks + " cho gan)");
    }

    static void patchAutoTarget(Map<String, byte[]> entries) {
        String playerName = sym("class.PLAYER");
        ClassNode player = new ClassNode();
        new ClassReader(entries.get(playerName + ".class")).accept(player, 0);
        int hooks = 0;
        for (MethodNode m : player.methods) {
            if (m.instructions == null) continue;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n.getOpcode() != INVOKESTATIC) continue;
                MethodInsnNode c = (MethodInsnNode) n;
                if (!"HSOSKYPetFeedSupport".equals(c.owner)) continue;
                if (!"isValidAutoTarget".equals(c.name)) continue;
                if (!"(Ljava/lang/Object;Ljava/lang/Object;)Z".equals(c.desc)) continue;
                c.owner = SUPPORT;
                c.name = "autoTargetGate";
                hooks++;
            }
        }
        require(hooks > 0, "khong tim thay cho goi isValidAutoTarget trong PLAYER");
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        player.accept((ClassVisitor) cw);
        entries.put(playerName + ".class", cw.toByteArray());
        System.out.println("[6e] Noi bo loc muc tieu Auto Tu Danh qua autoTargetGate ("
                + hooks + " cho goi)");
    }

    static void patchShop(Map<String, byte[]> entries) {
        String shopName = sym("class.SHOP");
        ClassNode shop = new ClassNode();
        new ClassReader(entries.get(shopName + ".class")).accept(shop, 0);
        int opened = 0;
        for (FieldNode f : shop.fields) {
            if (!f.name.equals(sym("shop.type")) && !f.name.equals(sym("shop.items"))) continue;
            f.access = (f.access & ~(ACC_PRIVATE | ACC_PROTECTED)) | ACC_PUBLIC;
            opened++;
        }
        if (opened != 2) throw new RuntimeException("Mong doi mo quyen 2 field cua SHOP, duoc " + opened);
        int hooks = 0;
        int ctors = 0;
        for (MethodNode m : shop.methods) {
            if (!"<init>".equals(m.name)) continue;
            ctors++;
            hooks += insertBeforeReturns(m, Integer.valueOf(0), "onShopOpened", "(L" + shopName + ";)V");
        }
        if (ctors == 0) throw new RuntimeException("SHOP khong co <init> nao");
        if (hooks == 0) throw new RuntimeException("SHOP.<init> khong co RETURN nao de hook");
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        shop.accept((ClassVisitor) cw);
        entries.put(shopName + ".class", cw.toByteArray());
        System.out.println("[6b] Hook onShopOpened vao SHOP.<init> (" + ctors + " ctor, "
                + hooks + " diem return), mo quyen " + opened + " field");
    }

    /**
     * Ha dau version cua class nao lo duoc bien dich cho JRE moi.
     *
     * HSOSKYPetFeedSupport.class trong chuoi jar nay mang version 61 (Java 17) tu mot lan
     * build cu, nghia la jar CHI chay duoc tren JRE >= 17. Chay gia lap bang Java 8 la
     * "UnsupportedClassVersionError ... only recognizes class file versions up to 52.0" va
     * khong vao duoc game. Cac class con lai trong jar deu la 47/51/52.
     *
     * Chi doi DUNG hai byte version va mot call-site gate da duoc verify trong
     * onCanvasUpdate(), khong bien dich lai tu source tren dia.
     *
     * Va chi lam khi da kiem rang class do khong dung thu gi cua Java 9+: invokedynamic,
     * constant-dynamic, nest/record/sealed, goi static/special len interface. Ha version
     * cua mot class co nhung thu do la hong that, nen thay la bao loi chu khong lam bua.
     */
    static void lowerClassVersions(Map<String, byte[]> entries) {
        Map<String, byte[]> changed = new LinkedHashMap<String, byte[]>();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            byte[] b = e.getValue();
            int major = ((b[6] & 0xff) << 8) | (b[7] & 0xff);
            if (major <= MAX_CLASS_VERSION) continue;
            requireJava7Safe(e.getKey(), b, major);
            byte[] out = new byte[b.length];
            System.arraycopy(b, 0, out, 0, b.length);
            out[4] = 0;                                   // minor = 0
            out[5] = 0;
            out[6] = (byte) ((MAX_CLASS_VERSION >> 8) & 0xff);
            out[7] = (byte) (MAX_CLASS_VERSION & 0xff);
            changed.put(e.getKey(), out);
            System.out.println("[6d] Ha class version " + e.getKey() + ": " + major
                    + " -> " + MAX_CLASS_VERSION + " (jar chay duoc tren JRE cu)");
        }
        entries.putAll(changed);
    }

    /** Nem loi neu class dung thu ma ha version xuong se hong. */
    static void requireJava7Safe(String name, byte[] bytes, int major) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        require(cn.nestHostClass == null && cn.nestMembers == null,
                name + " (version " + major + ") dung nestmate - khong ha version duoc");
        require(cn.recordComponents == null && cn.permittedSubclasses == null,
                name + " (version " + major + ") la record/sealed - khong ha version duoc");
        for (MethodNode m : cn.methods) {
            if (m.instructions == null) continue;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                require(!(n instanceof InvokeDynamicInsnNode),
                        name + " dung invokedynamic (lambda / noi chuoi kieu moi)"
                        + " - khong ha version duoc");
                require(!(n instanceof LdcInsnNode
                                && ((LdcInsnNode) n).cst instanceof ConstantDynamic),
                        name + " dung constant-dynamic - khong ha version duoc");
                if (!(n instanceof MethodInsnNode)) continue;
                MethodInsnNode c = (MethodInsnNode) n;
                require(!c.itf || n.getOpcode() == INVOKEINTERFACE,
                        name + " goi static/default tren interface - can version >= 52");
            }
        }
    }

    /**
     * Rut nhip tu dang nhap lai cua client tu 30 giay xuong 15 giay.
     *
     * Trong jar co HAI co che khac nhau: auto login cua ban mod (HSOSKYPetFeedSupport) da
     * la 15 giay san, con day la vong cua chinh client, chay khi auto login khong bat. Doi
     * not cai nay de ca hai luong deu 15 giay.
     *
     * Nhan dien theo CAU TRUC chu khong theo hang so: lop xu ly dut ket noi = lop goi
     * HSOSKYPetFeedSupport.onDisconnected(); lich hen thu lai = field long static cua chinh
     * lop do, ghi bang mau [LDC 30000L, LADD, PUTSTATIC]. Trong jar con mot cho khac cung
     * ghi "now + hang so" (60 giay, field khac) nen bat buoc chot theo owner + ten field,
     * khong duoc doi bua moi hang so 30000.
     */
    static void patchReconnectDelay(Map<String, byte[]> entries) {
        int alreadyNew = countRetrySites(entries, NEW_RETRY_MS);
        int stillOld = countRetrySites(entries, OLD_RETRY_MS);
        if (stillOld == 0 && alreadyNew == EXPECT_RETRY_SITES) {
            System.out.println("[6c] Nhip client tu dang nhap lai da la " + NEW_RETRY_MS
                    + "ms (" + alreadyNew + " cho), giu nguyen");
            return;
        }
        String disc = null;
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            if ("HSOSKYPetFeedSupport".equals(cn.name)) continue;
            for (MethodNode m : cn.methods) {
                if (m.instructions == null) continue;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (!(n instanceof MethodInsnNode)) continue;
                    MethodInsnNode c = (MethodInsnNode) n;
                    if (!"HSOSKYPetFeedSupport".equals(c.owner)) continue;
                    if (!"onDisconnected".equals(c.name)) continue;
                    if (disc != null && !disc.equals(cn.name)) {
                        throw new RuntimeException("Nhieu lop cung goi onDisconnected - phai xem lai");
                    }
                    disc = cn.name;
                }
            }
        }
        if (disc == null) throw new RuntimeException("Khong tim thay lop xu ly dut ket noi");

        String field = null;
        int done = 0;
        Map<String, byte[]> changed = new LinkedHashMap<String, byte[]>();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            int here = 0;
            for (MethodNode m : cn.methods) {
                if (m.instructions == null) continue;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (!(n instanceof LdcInsnNode)) continue;
                    Object cst = ((LdcInsnNode) n).cst;
                    if (!(cst instanceof Long) || ((Long) cst).longValue() != OLD_RETRY_MS) continue;
                    AbstractInsnNode add = skip(n);
                    if (add == null || add.getOpcode() != LADD) continue;
                    AbstractInsnNode put = skip(add);
                    if (put == null || put.getOpcode() != PUTSTATIC) continue;
                    FieldInsnNode f = (FieldInsnNode) put;
                    if (!disc.equals(f.owner) || !"J".equals(f.desc)) continue;
                    if (field == null) {
                        field = f.name;
                    } else if (!field.equals(f.name)) {
                        throw new RuntimeException("Hai field hen gio khac nhau - phai xem lai");
                    }
                    ((LdcInsnNode) n).cst = Long.valueOf(NEW_RETRY_MS);
                    here++;
                }
            }
            if (here == 0) continue;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept((ClassVisitor) cw);
            changed.put(e.getKey(), cw.toByteArray());
            done += here;
        }
        if (done != EXPECT_RETRY_SITES) {
            throw new RuntimeException("Mong doi " + EXPECT_RETRY_SITES
                    + " cho hen ket noi lai, doi duoc " + done);
        }
        entries.putAll(changed);
        System.out.println("[6c] Nhip client tu dang nhap lai " + OLD_RETRY_MS + "ms -> "
                + NEW_RETRY_MS + "ms (" + done + " cho)");
    }

    /** Dem so cho hen ket noi lai dang mang gia tri nay. */
    static int countRetrySites(Map<String, byte[]> entries, long value) {
        int found = 0;
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            for (MethodNode m : cn.methods) {
                if (m.instructions == null) continue;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (!(n instanceof LdcInsnNode)) continue;
                    Object cst = ((LdcInsnNode) n).cst;
                    if (!(cst instanceof Long) || ((Long) cst).longValue() != value) continue;
                    AbstractInsnNode add = skip(n);
                    if (add == null || add.getOpcode() != LADD) continue;
                    AbstractInsnNode put = skip(add);
                    if (put == null || put.getOpcode() != PUTSTATIC) continue;
                    if (!"J".equals(((FieldInsnNode) put).desc)) continue;
                    found++;
                }
            }
        }
        return found;
    }

    /**
     * Doi rieng chu ky cua vong lap Canvas chinh. Khong thay hang so 8ms toan jar vi
     * nhieu hang so khac la cooldown / timeout. Moc cau truc la class Canvas implements
     * Runnable, run() doc mot field long static va goi Thread.sleep(), con <clinit> cua
     * class do gan field nay bang chu ky cu.
     */
    static void patchFrameRate(Map<String, byte[]> entries) {
        int candidates = 0;
        int changed = 0;
        Map<String, byte[]> rewritten = new LinkedHashMap<String, byte[]>();

        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            if (!"javax/microedition/lcdui/Canvas".equals(cn.superName)) continue;
            if (cn.interfaces == null || !cn.interfaces.contains("java/lang/Runnable")) continue;

            MethodNode run = null;
            for (MethodNode m : cn.methods) {
                if ("run".equals(m.name) && "()V".equals(m.desc)) {
                    run = m;
                    break;
                }
            }
            if (run == null || !callsThreadSleep(run)) continue;

            Set<String> frameFields = new HashSet<String>();
            for (AbstractInsnNode n = run.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!(n instanceof FieldInsnNode) || n.getOpcode() != GETSTATIC) continue;
                FieldInsnNode f = (FieldInsnNode) n;
                if ("J".equals(f.desc)) frameFields.add(fieldKey(f.owner, f.name, f.desc));
            }
            if (frameFields.isEmpty()) continue;
            candidates++;

            int here = 0;
            for (MethodNode m : cn.methods) {
                if (!"<clinit>".equals(m.name) || m.instructions == null) continue;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTSTATIC) continue;
                    FieldInsnNode f = (FieldInsnNode) n;
                    if (!"J".equals(f.desc)
                            || !frameFields.contains(fieldKey(f.owner, f.name, f.desc))) continue;
                    Long value = previousLongConstant(n);
                    if (value == null) continue;
                    if (value.longValue() == OLD_FRAME_MS) {
                        AbstractInsnNode constant = previousMeaningful(n);
                        ((LdcInsnNode) constant).cst = Long.valueOf(NEW_FRAME_MS);
                        here++;
                    } else if (value.longValue() == NEW_FRAME_MS) {
                        here++;
                    }
                }
            }
            if (here == 0) continue;

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept((ClassVisitor) cw);
            rewritten.put(e.getKey(), cw.toByteArray());
            changed += here;
        }

        require(candidates == EXPECT_FRAME_SITES,
                "tim thay " + candidates + " class Canvas Runnable co moc FPS, mong doi "
                        + EXPECT_FRAME_SITES);
        require(changed == EXPECT_FRAME_SITES,
                "khong patch du moc FPS " + OLD_FRAME_MS + "ms -> " + NEW_FRAME_MS
                        + "ms, da xu ly " + changed);
        entries.putAll(rewritten);
        System.out.println("[6f] FPS vong lap game " + OLD_FRAME_MS + "ms -> "
                + NEW_FRAME_MS + "ms (muc 60 FPS, " + changed + " moc)");
    }

    static boolean callsThreadSleep(MethodNode m) {
        if (m.instructions == null) return false;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode c = (MethodInsnNode) n;
            if ("java/lang/Thread".equals(c.owner) && "sleep".equals(c.name)
                    && "(J)V".equals(c.desc)) return true;
        }
        return false;
    }

    static String fieldKey(String owner, String name, String desc) {
        return owner + "#" + name + "#" + desc;
    }

    static Long previousLongConstant(AbstractInsnNode put) {
        AbstractInsnNode constant = previousMeaningful(put);
        if (!(constant instanceof LdcInsnNode)) return null;
        Object value = ((LdcInsnNode) constant).cst;
        return value instanceof Long ? (Long) value : null;
    }

    static AbstractInsnNode previousMeaningful(AbstractInsnNode n) {
        AbstractInsnNode x = n.getPrevious();
        while (x != null && x.getOpcode() < 0) x = x.getPrevious();
        return x;
    }

    /** Nem loi neu file dich cu hon file nguon - tuc la lan bien dich vua roi da hong. */
    static void requireFresh(File built, File source) {
        if (!source.exists()) return;
        if (built.lastModified() >= source.lastModified()) return;
        throw new RuntimeException(built.getName() + " cu hon " + source.getName()
                + " - lan javac vua roi hong, dung dong goi ban cu vao jar."
                + " Chay lai GenAutoTradeSupport + javac roi thu lai.");
    }

    /** Lenh thuc su ke tiep, bo qua label / line / frame. */
    static AbstractInsnNode skip(AbstractInsnNode n) {
        AbstractInsnNode x = n.getNext();
        while (x != null && x.getOpcode() < 0) x = x.getNext();
        return x;
    }

    /**
     * Chen mot loi goi support truoc moi RETURN. aloadVar khac null thi day bien do lam
     * tham so (dung cho this trong constructor).
     */
    static int insertBeforeReturns(MethodNode m, Integer aloadVar, String name, String desc) {
        int hooks = 0;
        AbstractInsnNode n = m.instructions.getFirst();
        while (n != null) {
            AbstractInsnNode next = n.getNext();
            if (n.getOpcode() == RETURN) {
                InsnList call = new InsnList();
                if (aloadVar != null) call.add(new VarInsnNode(ALOAD, aloadVar.intValue()));
                call.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, name, desc, false));
                m.instructions.insertBefore(n, call);
                hooks++;
            }
            n = next;
        }
        return hooks;
    }

    static void patchPaintHud(ClassNode engine) {
        String gfx = sym("class.GRAPHICS");
        MethodNode m = method(engine, sym("engine.method.paintHud"), "(L" + gfx + ";)V");
        if ((m.access & ACC_STATIC) == 0) throw new RuntimeException("paintHud phai la static");
        InsnList pre = new InsnList();
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "paintHud", "(L" + gfx + ";)V", false));
        m.instructions.insert(pre);
        System.out.println("[5] Hook paintHud(Graphics) vao HUD cua engine");
    }

    static void patchNpcMenu(ClassNode engine) {
        String vec = sym("class.VEC");
        String desc = "(SBL" + vec + ";)Z";
        MethodNode m = method(engine, sym("engine.method.onNpcMenuOpened"), desc);
        if ((m.access & ACC_STATIC) == 0) throw new RuntimeException("onNpcMenuOpened phai la static");
        LabelNode cont = new LabelNode();
        InsnList pre = new InsnList();
        pre.add(new VarInsnNode(ILOAD, 0));
        pre.add(new VarInsnNode(ILOAD, 1));
        pre.add(new VarInsnNode(ALOAD, 2));
        pre.add(new MethodInsnNode(INVOKESTATIC, SUPPORT, "onNpcMenu", desc, false));
        pre.add(new JumpInsnNode(IFEQ, cont));
        pre.add(new InsnNode(ICONST_1));
        pre.add(new InsnNode(IRETURN));
        pre.add(cont);
        m.instructions.insert(pre);
        System.out.println("[6] Hook onNpcMenu vao onNpcMenuOpened(short,byte,Vector)");
    }

    /**
     * Doi nhip hoi sinh cua engine goc tu 2 giay xuong 0,5 giay.
     *
     * Khong thay the moi hang so 2000 trong jar: trong engine con nhieu timer khac. Moc
     * nay phai la method co doc reviveMode, goi reviveRequest(byte), va doc
     * System.currentTimeMillis() truoc hang so.
     */
    static void patchNativeReviveDelay(Map<String, byte[]> entries) {
        String engineName = sym("class.ENGINE");
        byte[] before = entries.get(engineName + ".class");
        if (before == null) throw new RuntimeException("Khong tim thay class engine de patch revive");

        ClassNode engine = new ClassNode();
        new ClassReader(before).accept(engine, 0);
        String reviveField = sym("engine.field.reviveMode");
        MethodNode target = null;
        AbstractInsnNode targetConstant = null;
        long targetValue = Long.MIN_VALUE;

        for (MethodNode m : engine.methods) {
            if (m.instructions == null) continue;
            boolean readsReviveMode = false;
            boolean callsByteRequest = false;
            boolean readsClock = false;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof FieldInsnNode && n.getOpcode() == GETSTATIC) {
                    FieldInsnNode f = (FieldInsnNode) n;
                    if (engineName.equals(f.owner) && reviveField.equals(f.name) && "I".equals(f.desc)) {
                        readsReviveMode = true;
                    }
                }
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode c = (MethodInsnNode) n;
                    if ("java/lang/System".equals(c.owner)
                            && "currentTimeMillis".equals(c.name)
                            && "()J".equals(c.desc)) readsClock = true;
                    if ("(B)V".equals(c.desc)) callsByteRequest = true;
                }
            }
            if (!readsReviveMode || !callsByteRequest || !readsClock) continue;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!(n instanceof LdcInsnNode) || !(((LdcInsnNode) n).cst instanceof Long)) continue;
                long value = ((Long) ((LdcInsnNode) n).cst).longValue();
                if (value != OLD_REVIVE_MS && value != NEW_REVIVE_MS) continue;
                if (target != null) throw new RuntimeException("Co nhieu moc native revive");
                target = m;
                targetConstant = n;
                targetValue = value;
            }
        }

        require(target != null,
                "Khong tim thay moc native revive " + OLD_REVIVE_MS + "ms/"
                        + NEW_REVIVE_MS + "ms");
        if (targetValue == NEW_REVIVE_MS) {
            System.out.println("[4a] Nhip native hoi sinh da la " + NEW_REVIVE_MS + "ms");
            return;
        }
        ((LdcInsnNode) targetConstant).cst = Long.valueOf(NEW_REVIVE_MS);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        engine.accept((ClassVisitor) cw);
        entries.put(engineName + ".class", cw.toByteArray());
        System.out.println("[4a] Nhip native hoi sinh " + OLD_REVIVE_MS + "ms -> "
                + NEW_REVIVE_MS + "ms");
    }

    // ------------------------------------------------------------- verify

    static void verify(String outPath, String engineName, byte[] petFeedBefore,
            byte[] nativeMenuBefore) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        readJar(outPath, entries, new LinkedHashMap<String, Long>());

        require(entries.containsKey(SUPPORT + ".class"), "thieu " + SUPPORT + ".class");
        require(entries.containsKey(LEGACY_SUPPORT + ".class"),
                "thieu " + LEGACY_SUPPORT + ".class");
        byte[] sup = entries.get(SUPPORT + ".class");
        int ver = ((sup[6] & 0xff) << 8) | (sup[7] & 0xff);
        require(ver == 51, "class version cua support la " + ver + ", mong doi 51 (Java 7)");
        byte[] legacy = entries.get(LEGACY_SUPPORT + ".class");
        int legacyVer = ((legacy[6] & 0xff) << 8) | (legacy[7] & 0xff);
        require(legacyVer == 51, "class version cua legacy bridge la " + legacyVer
                + ", mong doi 51 (Java 7)");

        /*
         * HSOSKYPetFeedSupport duoc phep ha version va thay dung mot call-site
         * checkAndMountHorse trong onCanvasUpdate() bang facade gate. Verifier ben duoi
         * khang dinh dung mot thay doi co chu dich, khong phai compile lai class tu source.
         */
        byte[] petFeedAfter = entries.get("HSOSKYPetFeedSupport.class");
        require(petFeedAfter != null, "mat HSOSKYPetFeedSupport.class");
        verifyNativeHorseMountGate(petFeedBefore, petFeedAfter);
        ClassNode petLogin = new ClassNode();
        new ClassReader(petFeedAfter).accept(petLogin, 0);
        MethodNode loginSurface = method(petLogin, "showLoginScreen", "()V");
        require(callsOwner(loginSurface, "HSOSKYPetFeedSupport", "getLoginScreen")
                        && writesStaticField(loginSurface, sym("class.CANVAS"),
                                sym("canvas.currentScreen")),
                "showLoginScreen van bootstrap local_server thay vi chi chon login surface");

        // Khong con class nao doi JRE moi hon Java 7.
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            byte[] b = e.getValue();
            int major = ((b[6] & 0xff) << 8) | (b[7] & 0xff);
            require(major <= MAX_CLASS_VERSION, e.getKey() + " co class version " + major
                    + " > " + MAX_CLASS_VERSION + " - JRE cu se khong chay duoc jar");
        }

        ClassNode engine = new ClassNode();
        new ClassReader(entries.get(engineName + ".class")).accept(engine, 0);

        ClassNode canvas = new ClassNode();
        new ClassReader(entries.get(sym("class.CANVAS") + ".class")).accept(canvas, 0);
        MethodNode horseDialogGate = method(canvas, sym("canvas.showDialog"),
                "(Ljava/lang/String;)V");
        require(callsSupport(horseDialogGate, "shouldSuppressHorseDialog"),
                "CANVAS.showDialog thieu gate loc loi auto horse");

        ClassNode legacyNode = new ClassNode();
        new ClassReader(entries.get(LEGACY_SUPPORT + ".class")).accept(legacyNode, 0);
        MethodNode horseRestore = method(legacyNode, "restorePersistentHorse", "()V");
        require(callsOwner(horseRestore, "HSOSKYPetFeedSupport", "loadRMS")
                        && callsOwner(horseRestore, "HSOSKYPetFeedSupport", "checkAndMountHorse"),
                "persistent horse bridge does not load RMS and mount on lifecycle edges");
        require(callsOwner(horseRestore, LEGACY_SUPPORT, "onGameScreen")
                        && callsOwner(horseRestore, LEGACY_SUPPORT, "dialogOpen")
                        && callsOwner(horseRestore, "HSOSKYPetFeedSupport", "hasCurrentDialog")
                        && readsOwnerField(horseRestore, "HSOSKYPetFeedSupport",
                                "lastHorseMountAttemptTime"),
                "persistent horse bridge lost stable-screen/dialog/throttle guard");
        MethodNode horseScreen = method(legacyNode, "onGameScreen", "()Z");
        require(callsOwner(horseScreen, "HSOSKYPetFeedSupport", "getCurrentScreen")
                        && callsOwner(horseScreen, "HSOSKYPetFeedSupport", "getGameScreen"),
                "persistent horse bridge lost game-screen readiness check");
        MethodNode horseDialog = method(legacyNode, "dialogOpen", "()Z");
        require(readsOwnerField(horseDialog, sym("class.CANVAS"), sym("canvas.dialogA"))
                        && readsOwnerField(horseDialog, sym("class.CANVAS"), sym("canvas.dialogB")),
                "persistent horse bridge lost dialog readiness check");

        MethodNode mainMenu = method(engine, sym("engine.method.showMainMenu"), "()V");
        int tradeRows = countLdc(mainMenu, MENU_LABEL);
        require(tradeRows == (hideTradeMenu ? 0 : 1),
                "so dong menu Auto Di Buon khong dung: " + tradeRows);
        if (!hideTradeMenu) {
            require(hasMenuActionAfterLabel(mainMenu, MENU_LABEL, ACT_SUBMENU),
                    "dong Auto Di Buon khong tro vao action semantic");
        }
        MethodNode autoMenu = method(engine, sym("engine.method.update"), "()V");
        require(countLdc(autoMenu, AUTO_MODE_MONSTERS_LABEL) == 1
                        && countLdc(autoMenu, AUTO_MODE_MONSTERS_AND_PLAYERS_LABEL) == 1,
                "menu Auto Danh thieu dung mot trong hai mode semantic");
        require(hasMenuActionAfterLabel(autoMenu, AUTO_MODE_MONSTERS_LABEL,
                        ACT_AUTO_MONSTERS)
                        && hasMenuActionAfterLabel(autoMenu, AUTO_MODE_MONSTERS_AND_PLAYERS_LABEL,
                                ACT_AUTO_MONSTERS_AND_PLAYERS),
                "menu Auto Danh gan sai action mode 6/7");
        MethodNode autoMode = method(legacyNode, "selectAutoAttackMode", "(Z)V");
        require(callsOwner(autoMode, "HSOSKYPetFeedSupport", "setAutoPlayersEnabled")
                        && callsOwner(autoMode, "HSOSKYPetFeedSupport", "setModAutoAttack")
                        && callsOwner(autoMode, "HSOSKYPetFeedSupport", "reengageAutoAttack")
                        && callsOwner(autoMode, "HSOSKYPetFeedSupport", "saveRMS"),
                "selectAutoAttackMode() khong giu du hanh vi chat 6/7");
        require(hasLdc(mainMenu, MENU_LABEL_N4),
                "menu chinh khong co dong \"" + MENU_LABEL_N4 + "\"");
        require(hasLdc(mainMenu, ANCHOR_LABEL),
                "menu chinh mat dong \"" + ANCHOR_LABEL + "\"");
        require(callsSupport(method(engine, sym("engine.method.perform"), "(II)V"), "handleMenuAction"),
                "perform khong goi handleMenuAction");
        require(callsSupport(method(engine, sym("engine.method.paintHud"),
                        "(L" + sym("class.GRAPHICS") + ";)V"), "paintHud"),
                "paintHud khong goi paintHud cua support");
        require(callsSupport(method(engine, sym("engine.method.onNpcMenuOpened"),
                        "(SBL" + sym("class.VEC") + ";)Z"), "onNpcMenu"),
                "onNpcMenuOpened khong goi onNpcMenu");

        // Keep the whole native menu class byte-identical to the golden input.
        // The one-shot ENTER cleanup is implemented in the canvas tick; any
        // MENUBOX.show hook can affect both the manual Auto menu and the
        // automatic login popup because both use the same native entry point.
        require(Arrays.equals(nativeMenuBefore, entries.get(sym("class.MENUBOX") + ".class")),
                "MENUBOX da bi chen hook; phai giu nguyen menu native");

        MethodNode canvasTick = method(canvas, sym("canvas.tickAll"), "()V");
        require(callsSupport(canvasTick, "tick"), "CANVAS khong goi tick");
        MethodNode bridgeTick = method(legacyNode, "tick", "()V");
        MethodNode retryLogin = method(legacyNode, "retryLoginAfterCooldown", "()V");
        MethodNode retryEnter = method(legacyNode, "retryLoginEnterAfterCharacterList", "()V");
        MethodNode transportKick = method(legacyNode,
                "forceTransportReconnectBeforeLogin", "()V");
        NativeTransportStarter transportStarter = findNativeTransportStarter(entries);
        NativeTransportCloser transportCloser = findNativeTransportCloser(entries, transportStarter);
        require(callsOwner(transportKick, "HSOSKYPetFeedSupport", "onDisconnected")
                        && callsMethod(transportKick, transportStarter.owner,
                                transportStarter.name, transportStarter.desc)
                        && callsMethod(transportKick, transportCloser.owner,
                                transportCloser.closeName, transportCloser.closeDesc),
                "forceTransportReconnectBeforeLogin() chua dong transport cu va mo socket native");
        require(callsOwner(retryLogin, LEGACY_SUPPORT, "replayCapturedLoginRequest")
                        && callsOwner(retryLogin, "HSOSKYPetFeedSupport", "triggerLogin")
                        && !callsOwner(retryLogin, LEGACY_SUPPORT,
                                "replayCapturedLoginEnterRequest"),
                "retry login phai gui lai opcode-1 truoc, khong duoc gui opcode-13 som");
        require(callsOwner(retryEnter, LEGACY_SUPPORT,
                        "replayCapturedLoginEnterRequest"),
                "retry enter-world khong replay opcode-13 sau character list");
        MethodNode postLoginEnter = method(legacyNode, "postLoginEnterTick", "()V");
        require(callsSupport(postLoginEnter, "pressOk"),
                "postLoginEnterTick() khong phat phim ENTER");
        require(callsSupport(bridgeTick, "postLoginEnterTick"),
                "tick() khong goi postLoginEnterTick()");
        require(!anyClassCalls(entries, SUPPORT, "checkGhost"),
                "captcha hook van con trong output");
        require(anyClassCalls(entries, "HSOSKYPetFeedSupport", "loadRMS")
                        && anyClassCalls(entries, "HSOSKYPetFeedSupport", "checkAndMountHorse"),
                "legacy bridge chua co lifecycle re-arm auto ngua");

        ClassNode shop = new ClassNode();
        new ClassReader(entries.get(sym("class.SHOP") + ".class")).accept(shop, 0);
        boolean shopHooked = false;
        for (MethodNode m : shop.methods) {
            if ("<init>".equals(m.name) && callsSupport(m, "onShopOpened")) shopHooked = true;
        }
        require(shopHooked, "SHOP.<init> khong goi onShopOpened");
        int publicFields = 0;
        for (FieldNode f : shop.fields) {
            if (!f.name.equals(sym("shop.type")) && !f.name.equals(sym("shop.items"))) continue;
            if ((f.access & ACC_PUBLIC) != 0) publicFields++;
        }
        require(publicFields == 2, "SHOP chua mo quyen doc loai cua hang / danh sach hang");

        /*
         * Chot lai bai hoc da mat mot lan chay thu: offline() khong duoc dung
         * onGameScreen(). Mo cua hang cua Dylan la doi man hinh that, nen lay "khong o man
         * hinh game" lam dau hieu mat ket noi se treo auto ngay o buoc toi cho Dylan.
         */
        // Internal behavioral invariants now belong to the generated bridge. The public
        // HSOAutoTradeSupport class is intentionally only the stable hook facade.
        ClassNode support = new ClassNode();
        new ClassReader(entries.get(LEGACY_SUPPORT + ".class")).accept(support, 0);
        MethodNode off = null;
        for (MethodNode m : support.methods) if ("offline".equals(m.name)) off = m;
        require(off != null, "support khong con ham offline()");
        require(!callsSupport(off, "onGameScreen"),
                "offline() goi onGameScreen() - mo cua hang se bi hieu nham la mat ket noi");

        /*
         * Chot bai hoc thu hai: client co BA kenh thong bao tam thoi, dong "da ban het" di
         * vao kenh ngan. Doc thieu kenh la auto khong bao gio thay bao het hang va bam mua
         * mai mot loai da het - da mat mot lan chay thu vi cai nay.
         */
        MethodNode poll = null;
        for (MethodNode m : support.methods) if ("pollInfo".equals(m.name)) poll = m;
        require(poll != null, "support khong con ham pollInfo()");
        Set<String> channels = new HashSet<String>();
        for (AbstractInsnNode n = poll.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() != GETFIELD) continue;
            FieldInsnNode f = (FieldInsnNode) n;
            if (f.owner.equals(sym("class.INFOBOX")) && "Ljava/lang/String;".equals(f.desc)) {
                channels.add(f.name);
            }
        }
        require(channels.size() == 3,
                "pollInfo() doc " + channels.size() + " kenh thong bao, phai doc du 3");

        /*
         * Chot bai hoc thu ba: khong buoc nao duoc tat auto ngay khi mot khung hinh doc
         * khong ra con bo. Danh sach thuc the co luc trong mot nhip, va da co lan auto bao
         * "Da goi duoc bo vang" xong la bao "Mat con bo" ngay dong sau.
         */
        MethodNode vanished = null;
        for (MethodNode m : support.methods) if ("cowVanished".equals(m.name)) vanished = m;
        require(vanished != null, "support khong con ham cowVanished()");
        require(callsSupport(vanished, "cowReallyGone"),
                "cowVanished() khong cho cowReallyGone() - se ket luan mat bo ngay lap tuc");
        String[] patient = {"stepTalkDylan", "stepBoostPet"};
        for (int i = 0; i < patient.length; i++) {
            MethodNode step = null;
            for (MethodNode m : support.methods) if (patient[i].equals(m.name)) step = m;
            require(step != null, "support khong con ham " + patient[i] + "()");
            require(!callsSupport(step, "stop"),
                    patient[i] + "() goi thang stop() - mat bo mot nhip la tat auto");
            require(callsSupport(step, "cowVanished"),
                    patient[i] + "() khong di qua cowVanished()");
        }

        /*
         * Chot bai hoc thu tu: khong duoc bam Bo vang trong luc nhan vat con dang di.
         *
         * Mod giao ca mot duong di cho engine roi de engine tu di het, nen "thoi dieu khien"
         * khong phai la "dung lai" - nhan vat van troi tiep theo huong cu. Bam vat pham giua
         * luc do la con bo duoc tao lech han ra khoi cho dinh dat no. standStill() dung ca
         * ba tang (duong di, buoc dang do, vi tri bao cho server) roi moi cho bam.
         */
        String[] mustStand = {"stepSpawnPet", "stepDismissCow"};
        for (int i = 0; i < mustStand.length; i++) {
            MethodNode step = null;
            for (MethodNode m : support.methods) if (mustStand[i].equals(m.name)) step = m;
            require(step != null, "support khong con ham " + mustStand[i] + "()");
            int stand = -1;
            int use = -1;
            int at = 0;
            for (AbstractInsnNode n = step.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!(n instanceof MethodInsnNode)) continue;
                at++;
                MethodInsnNode c = (MethodInsnNode) n;
                if (ownerMatches(SUPPORT, c.owner) && "standStill".equals(c.name)
                        && stand < 0) stand = at;
                if (sym("class.SENDER").equals(c.owner) && sym("sender.useItem4").equals(c.name)
                        && use < 0) use = at;
            }
            require(stand > 0, mustStand[i] + "() khong goi standStill() - se bam Bo vang"
                    + " giua luc dang di, con bo bi tao lech cho");
            require(use > 0, mustStand[i] + "() khong con cho dung Bo vang");
            require(stand < use, mustStand[i] + "() goi standStill() SAU khi dung Bo vang"
                    + " - dung han lai luc do thi da muon");
        }

        /*
         * Chot bai hoc thu nam: chang dat bo KHONG duoc co duong "dung cho".
         *
         * Da co mot ban chen co che dung-yen-cho-bo-bam-kip vao ca stepToward lan chot cong,
         * voi ly do server keo bo ve moi giay nen cho mot nhip la du. Ly do khong sai, nhung
         * moi duong cho la them mot duong return dat NGAY TRUOC cho phat lenh di. Ghep voi
         * mot ham don cung co duong khong phat lenh, ket qua la ca nguoi lan bo dung im cho
         * toi khi nguoi choi tu bam mot phim - dung loi da kiem chung.
         *
         * Quay dau nhieu con hon dung im: ban v11 quay dau nhieu ma chuyen nao cung xong.
         */
        String[] noWait = {"stepToward", "routeTowards", "goFetchCow"};
        for (int i = 0; i < noWait.length; i++) {
            MethodNode step = null;
            for (MethodNode m : support.methods) if (noWait[i].equals(m.name)) step = m;
            require(step != null, "support khong con ham " + noWait[i] + "()");
            require(!callsSupport(step, "waitForCow"),
                    noWait[i] + "() goi waitForCow() - moi duong cho la mot duong return"
                    + " truoc cho phat lenh di, ghep lai thanh trang thai dung im");
        }
        for (MethodNode m : support.methods) {
            require(!"waitForCow".equals(m.name),
                    "support van con ham waitForCow() - da bo han o ban v22");
            require(!"rejoinPx".equals(m.name),
                    "support van con ham rejoinPx() - da bo han o ban v22");
        }

        /*
         * Chot bai hoc thu bay: di don bo phai di HET quang toi con bo.
         *
         * Ban v21 cho goFetchCow() tu di tung doan ngan va dung lai khi con cach con bo
         * rejoinPx(). Nhung chot cong doi bo trong 70px, ma rejoinPx() luc tang toc la
         * 100px - the la o dai 70..100px, chot cong bao "chua dat" con ham don bao "du gan
         * roi, khong di nua". Khong ai phat lenh di. Nhan vat ket han o cong, chi go duoc
         * bang cach nguoi choi tu bam phim.
         *
         * Bai hoc chung: dieu kien DUNG di phai luon chat hon moi dieu kien BAT DAU di.
         * Cach chac nhat la don bo di het quang, dung qua stepToward - dung hinh ban v11.
         */
        MethodNode fetch = null;
        for (MethodNode m : support.methods) if ("goFetchCow".equals(m.name)) fetch = m;
        require(fetch != null, "support khong con ham goFetchCow()");
        require(callsSupport(fetch, "stepToward"),
                "goFetchCow() khong di qua stepToward() - tu di lay thi de ho ra dai khoang"
                + " cach ma khong ben nao chiu phat lenh di");

        /*
         * Chot bai hoc thu sau: TEN CHU la quyen cao nhat khi nhan dang con bo.
         *
         * Loi da kiem chung ngoai thuc dia: hai nguoi cung dat bo qua cong mot luc, ca hai
         * con deu chua kip mang ten (doi map la client dung lai danh sach thuc the roi moi
         * hoi lai goi 7 cho tung mob), minh vo nham con cua nguoi ta. Cai chot doi tuong
         * hoi do duoc xet TRUOC ten, nen ten ve cung khong go duoc - toi luc ho dat bo di
         * la minh dung im ca dem.
         *
         * Hai loi thoat bat buoc phai con:
         *   cow()        - ten chu khac ten minh thi buong ngay (dropCow)
         *   goFetchCow() - don toi lan thu MAX_FETCH_RUNS ma van khong bam theo thi buong,
         *                  vi bo cua minh duoc server keo ve moi giay, don mot lan la xong
         */
        String[] mustDrop = {"cow", "goFetchCow"};
        for (int i = 0; i < mustDrop.length; i++) {
            MethodNode step = null;
            for (MethodNode m : support.methods) if (mustDrop[i].equals(m.name)) step = m;
            require(step != null, "support khong con ham " + mustDrop[i] + "()");
            require(callsSupport(step, "dropCow"),
                    mustDrop[i] + "() khong co duong buong con bo vo nham - se dat bo nguoi"
                    + " khac cho toi khi ho dat di roi dung im");
        }
        MethodNode drop = null;
        for (MethodNode m : support.methods) if ("dropCow".equals(m.name)) drop = m;
        require(drop != null, "support khong con ham dropCow()");
        require(callsSupport(drop, "markAlien"),
                "dropCow() khong danh dau con vua buong - vong sau se vo lai dung con do");

        /*
         * Chot bai hoc thu tam: tat Noclip thi phai tinh duong MOT LAN roi di theo.
         *
         * Loi da kiem chung: vua tat Noclip la nhan vat chay vong vo ngay o buoc dau. Mot
         * nhip chi giao cho engine mot doan ngan roi tinh lai tu vi tri moi - duong thang
         * thi tinh lai van ra dung duong do, nhung duong di THAT thi vong qua chuong ngai,
         * va tu hai vi tri gan nhau ham tim duong co the tra ve hai loi vong khac han nhau.
         * Nhip nay re trai, nhip sau re phai, roi lai trai.
         *
         * Ca bridge semantic lan engine goc deu tinh ca duong roi giao han cho nhan vat.
         */
        MethodNode real = null;
        MethodNode planned = null;
        for (MethodNode m : support.methods) {
            if ("realPath".equals(m.name)) real = m;
            if ("plannedPath".equals(m.name)) planned = m;
        }
        require(real != null, "support khong con ham realPath()");
        require(planned != null, "support khong con ham plannedPath() - ke hoach duong di"
                + " bi go, se tinh lai moi nhip va chay vong vo khi tat Noclip");
        require(callsSupport(real, "plannedPath"),
                "realPath() khong di qua plannedPath() - dang tinh duong lai tung nhip");
        require(!callsOwner(real, sym("class.CHARS"), sym("screen.findPath")),
                "realPath() goi thang findPath - phai di qua ke hoach, khong thi moi nhip"
                + " lai ra mot loi vong khac va nhan vat chay vong vo");
        /*
         * Sat dich ma khong co duong that thi phai buoc THANG toi dich, dung cach engine
         * goc lam khi findPath tra ve null.
         *
         * Da thu thay bang viec do vong quanh o dich lay cho dung thay the, va hong: moi
         * nhip lai chon mot o thay the khac nen nhan vat nhay qua nhay lai quanh cong, va
         * o thay the thi nam ngoai 40px ma server doi de mo cong - "di den gan cong thi tu
         * nhien chay ra cho khac, khong chiu sang map".
         */
        MethodNode walk = null;
        for (MethodNode m : support.methods) if ("walkTo".equals(m.name)) walk = m;
        require(walk != null, "support khong con ham walkTo()");
        require(callsSupport(walk, "realPath") && callsSupport(walk, "straightPath"),
                "walkTo() thieu mot trong hai duong di: duong that va duong thang");
        /*
         * Mieng quan trong nhat, va la mieng da thieu qua ba ban: ap sat cong thi phai EP
         * buoc thang len o cong, khong tim duong nua.
         *
         * Cong ghi bang toa do pixel, o duoi chan no thuong khong phai o di duoc. Tat Noclip
         * ma van di tim duong len o do thi khong bao gio dat chan len duoc, va nhan vat cu
         * lan quan quanh cong. Bridge vao trong mot o la goi forcePortalStep: giao duong
         * DUY NHAT MOT O, con tro 0.
         */
        require(callsSupport(walk, "forceStep"),
                "walkTo() khong co buoc ep vao o dich - ap sat cong roi ma van tim duong thi"
                + " khong bao gio vao duoc cong khi da tat Noclip");
        MethodNode force = null;
        for (MethodNode m : support.methods) if ("forceStep".equals(m.name)) force = m;
        require(force != null, "support khong con ham forceStep()");
        require(!callsSupport(force, "realPath") && !callsSupport(force, "straightPath")
                        && !callsSupport(force, "plannedPath"),
                "forceStep() lai di qua ham tim duong - the thi no khong con la buoc ep nua");
        /*
         * Chot bai hoc thu chin: phai co ham TU tim duong tren ban do o that.
         *
         * Loi da kiem chung: tat Noclip thi map lon khong bao gio tim duoc duong di, map
         * nho thi toi gan cong lai quay dau va chay vong vo. Ham tim duong cua engine chi
         * lam viec trong tam ngan - di xa la no bo cuoc - va truoc day bo cuoc dong nghia
         * voi "khong co duong". Bridge co hai tang: hoi engine truoc, engine chiu thua
         * thi tu do rong (BFS) tren mang o that. Tang do rong la thu mod nay thieu tu dau.
         */
        MethodNode grid = null;
        MethodNode walkable = null;
        MethodNode nearest = null;
        for (MethodNode m : support.methods) {
            if ("gridPath".equals(m.name)) grid = m;
            if ("walkableTile".equals(m.name)) walkable = m;
            if ("nearestOnPath".equals(m.name)) nearest = m;
        }
        require(grid != null, "support khong con ham gridPath() - mat tang tu tim duong,"
                + " map lon se lai khong tim duoc duong khi tat Noclip");
        require(walkable != null, "support khong con ham walkableTile()");
        require(nearest != null, "support khong con ham nearestOnPath()");
        require(callsSupport(planned, "gridPath"),
                "plannedPath() khong co tang tu tim duong - engine chiu thua la coi nhu"
                + " khong co duong, dung loi map lon khong di duoc");
        require(!callsOwner(grid, sym("class.CHARS"), sym("screen.findPath")),
                "gridPath() lai goi engine - the thi no khong con la tang du phong nua");
        require(readsOwnerField(grid, sym("class.TILEMAP"), sym("tilemap.tiles"))
                        || callsSupport(grid, "tileGrid"),
                "gridPath() khong doc mang o that - dang do rong tren cai gi?");
        require(callsSupport(real, "nearestOnPath") && callsSupport(planned, "nearestOnPath"),
                "khong con bam ke hoach theo o gan nhat - doi khop tuyet doi thi chi lech"
                + " nua o la vut ca ke hoach, tinh lai ra loi vong khac va quay dau");
        for (MethodNode m : support.methods) {
            require(!"approachPath".equals(m.name),
                    "support con approachPath() - cach do vong quanh o dich da bi bo o v25");
            require(!"neighbourPath".equals(m.name),
                    "support con neighbourPath() - gridPath() da lam ca viec do, va lam"
                    + " mot lan chu khong doi y moi nhip");
        }

        /*
         * Nham goc de ne cong khac chi dung khi dang di xuyen dia hinh: diem goc neo vao
         * toa do hien tai cua nhan vat, ma di duong that thi toa do do doi ca hai truc moi
         * buoc - goc truot theo chan, duoi mai khong kip.
         */
        /*
         * Auto Nga 4 phai bat Auto Tu Danh o dung che do 7 "Chỉ Quái".
         *
         * Che do 7 la autoPlayersEnabled = false. De nham sang che do 6 thi trong pho ban
         * nhan vat se di danh ca nguoi choi khac, va quan trong hon la doi muc tieu lien
         * tuc thay vi don quai dang lao vao thien thach.
         */
        require(!anyClassCalls(entries, "HSOSKYPetFeedSupport", "isValidAutoTarget")
                        || anyClassCalls(entries, SUPPORT, "autoTargetGate"),
                "bo loc muc tieu chua duoc noi qua autoTargetGate - trong pho ban se khoa vao"
                + " con quai o tan dang xa va bao \"Muc tieu o qua xa\"");
        MethodNode gate = null;
        for (MethodNode m : support.methods) if ("autoTargetGate".equals(m.name)) gate = m;
        require(gate != null, "support khong con ham autoTargetGate()");
        require(callsOwner(gate, "HSOSKYPetFeedSupport", "isValidAutoTarget"),
                "autoTargetGate() khong goi lai bo loc goc - moi che do khac Auto Nga 4 se"
                + " doi hanh vi chon muc tieu, khong chi rieng pho ban");
        MethodNode apath = null;
        for (MethodNode m : support.methods) if ("autoAttackPath".equals(m.name)) apath = m;
        require(apath != null, "support khong con ham autoAttackPath()");
        require(anyClassCalls(entries, SUPPORT, "autoAttackPath"),
                "chua chan Auto Tu Danh ghi player.path - hai ben cung ghi thi nhan vat chay"
                + " vong vong, khong di het duong nao");
        require(readsField(gate, "HSOAutoTradeSupport", "n4InDungeon"),
                "autoTargetGate() khong xet n4InDungeon - se siet tam chon muc tieu ca khi khong"
                + " o trong pho ban");
        /*
         * Trong pho ban tam chon muc tieu PHAI la vong tron - nguoi choi chot. Ket qua cua
         * n4InRange() phai la gia tri tra ve, khong duoc bo di (POP).
         */
        require(callsOwner(gate, "HSOAutoTradeSupport", "n4InRange")
                        && !resultDiscarded(gate, "HSOAutoTradeSupport", "n4InRange"),
                "autoTargetGate() khong dung ket qua n4InRange() - trong pho ban auto se khoa"
                + " vao con quai o tan dang xa va danh hut, con o ngay canh thi khong dung");

        MethodNode atk = null;
        MethodNode n4tick = null;
        MethodNode n4menu = null;
        for (MethodNode m : support.methods) {
            if ("n4StartAttack".equals(m.name)) atk = m;
            if ("n4Tick".equals(m.name)) n4tick = m;
            if ("n4OnNpcMenu".equals(m.name)) n4menu = m;
        }
        require(atk != null, "support khong con ham n4StartAttack()");
        require(n4tick != null, "support khong con ham n4Tick()");
        require(n4menu != null, "support khong con ham n4OnNpcMenu()");
        require(callsOwner(atk, "HSOSKYPetFeedSupport", "setAutoPlayersEnabled"),
                "n4StartAttack() khong dat che do danh - phai tat autoPlayersEnabled de ve"
                + " dung che do 7 \"Chỉ Quái\"");
        require((anyClassCalls(entries, SUPPORT, "n4PauseAttackForMovement")
                        || anyClassCalls(entries, LEGACY_SUPPORT, "n4PauseAttackForMovement"))
                        && (anyClassCalls(entries, SUPPORT, "n4ResumeAttackAfterMovement")
                        || anyClassCalls(entries, LEGACY_SUPPORT, "n4ResumeAttackAfterMovement")),
                "N4 chua tam dung va khoi phuc native Auto Tu Danh khi tranh ghi de player.path");
        require(hasLdc(n4menu, "horae"),
                "n4OnNpcMenu() khong con nhan \"horae\" - se tieu ngoc trong khi con ve");

        MethodNode rt = null;
        for (MethodNode m : support.methods) if ("routeTowards".equals(m.name)) rt = m;
        require(rt != null, "support khong con ham routeTowards()");
        require(readsField(rt, "HSOAutoTradeSupport", "noclipOn"),
                "routeTowards() khong xet noclipOn - se nham goc ne cong ca khi dang di"
                + " duong that, va goc do truot theo chan nhan vat");

        // Cac mod cu phai con nguyen.
        require(anyClassCalls(entries, "HSOSKYRuntimeSupport", "adjustTile"),
                "mat noclip (adjustTile) trong jar");
        require(anyClassCalls(entries, SUPPORT, "nativeHorseMountTick"),
                "native horse retry gate khong duoc noi vao facade");
        require(anyClassCalls(entries, "HSOSKYPetFeedSupport", "onCanvasUpdate"),
                "mat hook onCanvasUpdate cua HSOSKYPetFeedSupport");

        /*
         * Nhip ket noi lai: khong con cho nao 30 giay, va dung so cho da doi thanh 15.
         * Cho 60 giay cua mot field khac phai con nguyen - dem theo mau hen gio nen no
         * khong lot vao hai con so duoi day.
         */
        require(countRetrySites(entries, OLD_RETRY_MS) == 0,
                "van con cho hen ket noi lai 30 giay");
        require(countRetrySites(entries, NEW_RETRY_MS) == EXPECT_RETRY_SITES,
                "so cho hen ket noi lai 15 giay khong dung " + EXPECT_RETRY_SITES);

        System.out.println("[9] Verify jar vua ghi: OK");
    }

    /** Method nay co goi ham cua mot lop khac khong (khop ca owner lan ten). */
    static boolean callsOwner(MethodNode m, String owner, String name) {
        if (m.instructions == null) return false;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode c = (MethodInsnNode) n;
            if (ownerMatches(owner, c.owner) && name.equals(c.name)) return true;
        }
        return false;
    }

    /** Method nay co doc mot field cua lop khac khong (khop ca owner lan ten). */
    /**
     * Ham nay co duoc goi trong method do va ket qua bi bo di ngay (POP) khong.
     *
     * Dung de khang dinh mot lan goi chi de lam viec phu (ghi moc thoi gian), khong tham
     * gia vao gia tri tra ve. Khong co lan goi nao cung tinh la dat - luc do no khong the
     * anh huong gi ca.
     */
    static boolean resultDiscarded(MethodNode m, String owner, String name) {
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) n;
            if (!ownerMatches(owner, call.owner) || !name.equals(call.name)) continue;
            AbstractInsnNode next = n.getNext();
            while (next != null && next.getOpcode() < 0) next = next.getNext();
            if (next == null || next.getOpcode() != Opcodes.POP) return false;
        }
        return true;
    }

    static boolean readsOwnerField(MethodNode m, String owner, String name) {
        if (m.instructions == null) return false;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof FieldInsnNode)) continue;
            FieldInsnNode f = (FieldInsnNode) n;
            if (ownerMatches(owner, f.owner) && name.equals(f.name)) return true;
        }
        return false;
    }

    /** Method nay co doc mot field cho truoc khong. */
    static boolean readsField(MethodNode m, String owner, String name) {
        if (m.instructions == null) return false;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof FieldInsnNode)) continue;
            FieldInsnNode f = (FieldInsnNode) n;
            if (ownerMatches(owner, f.owner) && name.equals(f.name)) return true;
        }
        return false;
    }

    static boolean writesStaticField(MethodNode m, String owner, String name) {
        if (m.instructions == null) return false;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTSTATIC) continue;
            FieldInsnNode f = (FieldInsnNode) n;
            if (ownerMatches(owner, f.owner) && name.equals(f.name)) return true;
        }
        return false;
    }

    static AbstractInsnNode previousReal(AbstractInsnNode n) {
        AbstractInsnNode p = n == null ? null : n.getPrevious();
        while (p != null && p.getOpcode() < 0) p = p.getPrevious();
        return p;
    }

    static AbstractInsnNode nextReal(AbstractInsnNode n) {
        AbstractInsnNode p = n == null ? null : n.getNext();
        while (p != null && p.getOpcode() < 0) p = p.getNext();
        return p;
    }

    static boolean anyClassCalls(Map<String, byte[]> entries, String owner, String name) {
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            for (MethodNode m : cn.methods) {
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (!(n instanceof MethodInsnNode)) continue;
                    MethodInsnNode c = (MethodInsnNode) n;
                    if (owner.equals(c.owner) && name.equals(c.name)) return true;
                }
            }
        }
        return false;
    }

    /** Hai class file giong het nhau tru bon byte version (minor + major). */
    static boolean sameExceptVersion(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (i >= 4 && i <= 7) continue;
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    static void verifyNativeHorseMountGate(byte[] before, byte[] after) {
        ClassNode oldNode = new ClassNode();
        ClassNode newNode = new ClassNode();
        new ClassReader(before).accept(oldNode, 0);
        new ClassReader(after).accept(newNode, 0);
        MethodNode oldUpdate = method(oldNode, "onCanvasUpdate", "()V");
        MethodNode newUpdate = method(newNode, "onCanvasUpdate", "()V");
        int oldNative = countCalls(oldUpdate, "HSOSKYPetFeedSupport", "checkAndMountHorse");
        int newNative = countCalls(newUpdate, "HSOSKYPetFeedSupport", "checkAndMountHorse");
        int facade = countCalls(newUpdate, SUPPORT, "nativeHorseMountTick");
        require(oldNative == 1, "golden onCanvasUpdate khong co dung mot lan goi checkAndMountHorse");
        require(newNative == 0 && facade == 1,
                "native auto-horse retry gate khong dung: native=" + newNative
                        + ", facade=" + facade);
        require(after.length > 8 && ((after[6] & 0xff) << 8 | (after[7] & 0xff)) <= MAX_CLASS_VERSION,
                "HSOSKYPetFeedSupport class version khong hop le");
    }

    static int countCalls(MethodNode m, String owner, String name) {
        int count = 0;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) n;
            if (owner.equals(call.owner) && name.equals(call.name)) count++;
        }
        return count;
    }

    static boolean callsSupport(MethodNode m, String name) {
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode c = (MethodInsnNode) n;
            if ((SUPPORT.equals(c.owner) || LEGACY_SUPPORT.equals(c.owner))
                    && name.equals(c.name)) return true;
        }
        return false;
    }

    /** Compatibility checks accept either the public hook facade or its generated bridge. */
    static boolean ownerMatches(String expected, String actual) {
        if (expected.equals(actual)) return true;
        return SUPPORT.equals(expected) && LEGACY_SUPPORT.equals(actual);
    }

    static boolean hasLdc(MethodNode m, String s) {
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof LdcInsnNode && s.equals(((LdcInsnNode) n).cst)) return true;
        }
        return false;
    }

    static int countLdc(MethodNode m, String s) {
        int count = 0;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof LdcInsnNode && s.equals(((LdcInsnNode) n).cst)) count++;
        }
        return count;
    }

    static boolean hasMenuActionAfterLabel(MethodNode m, String label, int action) {
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof LdcInsnNode) || !label.equals(((LdcInsnNode) n).cst)) continue;
            for (AbstractInsnNode x = n.getNext(); x != null; x = x.getNext()) {
                if (x instanceof MethodInsnNode && "<init>".equals(((MethodInsnNode) x).name)) break;
                Integer value = intConstant(x);
                if (value != null) return value.intValue() == action;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------- utils

    static String sym(String key) {
        String v = SYM.getProperty(key);
        if (v == null) throw new RuntimeException("Thieu ky hieu " + key + " trong properties");
        return v;
    }

    static MethodNode method(ClassNode cn, String name, String desc) {
        for (MethodNode m : cn.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) return m;
        }
        throw new RuntimeException("Khong tim thay method " + desc + " tren " + shorten(cn.name));
    }

    static void require(boolean cond, String message) {
        if (!cond) throw new RuntimeException("VERIFY THAT BAI: " + message);
    }

    static boolean sameBytes(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    static String shorten(String s) {
        return s.length() > 20 ? s.substring(0, 16) + "~" + s.length() : s;
    }

    static void readJar(String path, Map<String, byte[]> entries, Map<String, Long> times)
            throws Exception {
        JarFile jar = new JarFile(path);
        try {
            Enumeration<JarEntry> it = jar.entries();
            while (it.hasMoreElements()) {
                JarEntry e = it.nextElement();
                InputStream in = jar.getInputStream(e);
                try {
                    entries.put(e.getName(), readAll(in));
                } finally {
                    in.close();
                }
                times.put(e.getName(), Long.valueOf(e.getTime()));
            }
        } finally {
            jar.close();
        }
    }

    /** Copy only semantic class artifacts from the compile jar into the patched output. */
    static int addSemanticClasses(Map<String, byte[]> entries, Map<String, Long> times,
            File compileJarFile) throws Exception {
        int count = 0;
        JarFile jar = new JarFile(compileJarFile);
        try {
            Enumeration<JarEntry> it = jar.entries();
            while (it.hasMoreElements()) {
                JarEntry entry = it.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith("luna/")
                        || !name.endsWith(".class")) continue;
                InputStream in = jar.getInputStream(entry);
                try {
                    entries.put(name, readAll(in));
                } finally {
                    in.close();
                }
                times.put(name, Long.valueOf(entry.getTime()));
                count++;
            }
        } finally {
            jar.close();
        }
        return count;
    }

    static int addGeneratedLegacy(Map<String, byte[]> entries, Map<String, Long> times,
            File compileJarFile) throws Exception {
        String name = LEGACY_SUPPORT + ".class";
        JarFile jar = new JarFile(compileJarFile);
        try {
            JarEntry entry = jar.getJarEntry(name);
            if (entry == null) return 0;
            InputStream in = jar.getInputStream(entry);
            try {
                entries.put(name, readAll(in));
            } finally {
                in.close();
            }
            times.put(name, Long.valueOf(entry.getTime()));
            return 1;
        } finally {
            jar.close();
        }
    }

    static void writeJar(String path, Map<String, byte[]> entries, Map<String, Long> times)
            throws Exception {
        JarOutputStream out = new JarOutputStream(new FileOutputStream(path));
        try {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                JarEntry je = new JarEntry(e.getKey());
                Long t = times.get(e.getKey());
                if (t != null && t.longValue() > 0L) ((ZipEntry) je).setTime(t.longValue());
                out.putNextEntry(je);
                out.write(e.getValue());
                out.closeEntry();
            }
        } finally {
            out.close();
        }
    }

    static byte[] readAll(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        try {
            return readAll(in);
        } finally {
            in.close();
        }
    }

    static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
