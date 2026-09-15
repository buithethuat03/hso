import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashSet;
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
 *   javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AutoTradePatcher.java
 *   java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AutoTradePatcher [input.jar] [output.jar]
 *
 * Mac dinh HSOLUNA_TRUONGBK_PERFECT.jar -> HSOLUNA_TRUONGBK_AUTOTRADE.jar.
 * Tu choi ghi de output da ton tai. Tu doc lai jar vua ghi de verify truoc khi bao thanh cong.
 *
 * Nam moc duoc chen, tat ca deu nho:
 *   1. ENGINE.showMainMenu   them dong menu "Auto Di Buon" ngay duoi "Auto Nuoi Pet"
 *   2. ENGINE.perform        prologue: HSOAutoTradeSupport.handleMenuAction -> return neu true
 *   3. CANVAS.tickAll        truoc moi RETURN: HSOAutoTradeSupport.tick()
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
 * Khong dung toi HSOSKYPetFeedSupport.class va cac class da patch toc do / cooldown /
 * noclip - chung duoc chep nguyen xi va duoc kiem lai sau khi ghi.
 */
public final class AutoTradePatcher implements Opcodes {

    static final String SUPPORT = "HSOAutoTradeSupport";
    static final String RUNTIME_SUPPORT = "HSOSKYRuntimeSupport";
    static final String MENU_LABEL = "Auto Đi Buôn";
    static final String ANCHOR_LABEL = "Auto Nuôi Pet";
    static final int ACT_SUBMENU = 9;

    /** Nhip tu dang nhap lai cua CHINH CLIENT sau khi rot mang. */
    static final long OLD_RETRY_MS = 30000L;
    static final long NEW_RETRY_MS = 15000L;
    static final int EXPECT_RETRY_SITES = 3;

    /** Class version cao nhat duoc phep co trong jar (51 = Java 7). */
    static final int MAX_CLASS_VERSION = 51;

    static Properties SYM;

    public static void main(String[] args) throws Exception {
        String inPath = args.length > 0 ? args[0] : "HSOLUNA_TRUONGBK_PERFECT.jar";
        String outPath = args.length > 1 ? args[1] : "HSOLUNA_TRUONGBK_AUTOTRADE.jar";

        File outFile = new File(outPath);
        if (outFile.exists()) {
            throw new RuntimeException("Output da ton tai, xoa hoac doi ten khac: " + outPath);
        }
        File supportFile = new File(SUPPORT + ".class");
        if (!supportFile.exists()) {
            throw new RuntimeException("Thieu " + SUPPORT + ".class - chay GenAutoTradeSupport"
                    + " roi javac truoc.");
        }
        /*
         * Javac that bai thi .class cu van con nam do, va patcher se lang le dong goi ban cu
         * vao jar moi - da mat mot lan build nhu vay. Nen chot: .class phai moi hon ca .java
         * sinh ra no lan .in la ban goc de sua.
         */
        requireFresh(supportFile, new File(SUPPORT + ".java"));
        requireFresh(supportFile, new File(SUPPORT + ".java.in"));
        File runtimeFile = new File(RUNTIME_SUPPORT + ".class");
        if (!runtimeFile.exists()) {
            throw new RuntimeException("Thieu " + RUNTIME_SUPPORT + ".class - chay javac truoc.");
        }
        requireFresh(runtimeFile, new File(RUNTIME_SUPPORT + ".java"));

        SYM = new Properties();
        InputStream sin = new FileInputStream("autotrade-symbols.properties");
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
        if (petFeedBefore == null) {
            throw new RuntimeException("Jar goc khong co HSOSKYPetFeedSupport.class - sai jar?");
        }

        String engineName = sym("class.ENGINE");
        byte[] engineBytes = entries.get(engineName + ".class");
        if (engineBytes == null) throw new RuntimeException("Khong tim thay class engine trong jar");

        ClassNode engine = new ClassNode();
        new ClassReader(engineBytes).accept(engine, 0);

        patchMainMenu(engine);
        patchPerform(engine);
        patchPaintHud(engine);
        patchNpcMenu(engine);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        engine.accept((ClassVisitor) cw);
        entries.put(engineName + ".class", cw.toByteArray());

        patchCanvasTick(entries);
        patchShop(entries);
        patchReconnectDelay(entries);
        lowerClassVersions(entries);

        entries.put(SUPPORT + ".class", readAll(supportFile));
        entries.put(RUNTIME_SUPPORT + ".class", readAll(runtimeFile));
        System.out.println("[7] Nhung " + SUPPORT + ".class va " + RUNTIME_SUPPORT + ".class vao jar");

        writeJar(outPath, entries, times);
        System.out.println("[8] Ghi " + outPath + " (" + outFile.length() + " byte)");

        verify(outPath, engineName, petFeedBefore);

        System.out.println();
        System.out.println("=================================================");
        System.out.println("XONG: " + outPath);
        System.out.println("=================================================");
    }

    // -------------------------------------------------------------- cac moc

    /** Chen mot dong menu moi ngay sau dong "Auto Nuoi Pet". */
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

        String cmdName = sym("class.CMD");
        String cmdCtor = "(Ljava/lang/String;IL" + sym("class.LISTENER") + ";)V";
        InsnList add = new InsnList();
        add.add(new VarInsnNode(ALOAD, 0));                       // Vector cuc bo
        add.add(new TypeInsnNode(NEW, cmdName));
        add.add(new InsnNode(DUP));
        add.add(new LdcInsnNode(MENU_LABEL));
        add.add(new IntInsnNode(BIPUSH, ACT_SUBMENU));
        add.add(new FieldInsnNode(GETSTATIC, engine.name, sym("engine.field.INSTANCE"),
                "L" + engine.name + ";"));
        add.add(new MethodInsnNode(INVOKESPECIAL, cmdName, "<init>", cmdCtor, false));
        add.add(new MethodInsnNode(INVOKEVIRTUAL, vecName, vecAdd, "(Ljava/lang/Object;)V", false));
        m.instructions.insert(after, add);
        System.out.println("[2] Them dong menu \"" + MENU_LABEL + "\" (action " + ACT_SUBMENU + ")");
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
     * Bat lay doi tuong man hinh cua hang ngay khi no duoc dung xong, va mo quyen doc hai
     * field ma support class can (loai cua hang + danh sach hang, ca hai deu private).
     * MakeAutoTradeCompileJar mo dung hai field nay tren jar dung de bien dich, nen luc
     * chay va luc bien dich khop nhau.
     */
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
     * Chi doi DUNG hai byte version, khong dung toi mot byte nao khac cua class - day khong
     * phai bien dich lai, nen quy tac "khong duoc build lai HSOSKYPetFeedSupport tu source
     * tren dia" van duoc giu.
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

    // ------------------------------------------------------------- verify

    static void verify(String outPath, String engineName, byte[] petFeedBefore) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        readJar(outPath, entries, new LinkedHashMap<String, Long>());

        require(entries.containsKey(SUPPORT + ".class"), "thieu " + SUPPORT + ".class");
        byte[] sup = entries.get(SUPPORT + ".class");
        int ver = ((sup[6] & 0xff) << 8) | (sup[7] & 0xff);
        require(ver == 51, "class version cua support la " + ver + ", mong doi 51 (Java 7)");

        /*
         * HSOSKYPetFeedSupport chi duoc phep khac dung 4 byte version (xem
         * lowerClassVersions). Moi byte con lai phai y nguyen - day la cach chung minh no
         * khong bi bien dich lai tu source cu tren dia.
         */
        byte[] petFeedAfter = entries.get("HSOSKYPetFeedSupport.class");
        require(petFeedAfter != null, "mat HSOSKYPetFeedSupport.class");
        require(sameExceptVersion(petFeedBefore, petFeedAfter),
                "HSOSKYPetFeedSupport.class bi doi ngoai hai byte version - khong duoc phep");

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

        require(hasLdc(method(engine, sym("engine.method.showMainMenu"), "()V"), MENU_LABEL),
                "menu chinh khong co dong \"" + MENU_LABEL + "\"");
        require(hasLdc(method(engine, sym("engine.method.showMainMenu"), "()V"), ANCHOR_LABEL),
                "menu chinh mat dong \"" + ANCHOR_LABEL + "\"");
        require(callsSupport(method(engine, sym("engine.method.perform"), "(II)V"), "handleMenuAction"),
                "perform khong goi handleMenuAction");
        require(callsSupport(method(engine, sym("engine.method.paintHud"),
                        "(L" + sym("class.GRAPHICS") + ";)V"), "paintHud"),
                "paintHud khong goi paintHud cua support");
        require(callsSupport(method(engine, sym("engine.method.onNpcMenuOpened"),
                        "(SBL" + sym("class.VEC") + ";)Z"), "onNpcMenu"),
                "onNpcMenuOpened khong goi onNpcMenu");

        ClassNode canvas = new ClassNode();
        new ClassReader(entries.get(sym("class.CANVAS") + ".class")).accept(canvas, 0);
        require(callsSupport(method(canvas, sym("canvas.tickAll"), "()V"), "tick"),
                "CANVAS khong goi tick");

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
        ClassNode support = new ClassNode();
        new ClassReader(entries.get(SUPPORT + ".class")).accept(support, 0);
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
                if ("HSOAutoTradeSupport".equals(c.owner) && "standStill".equals(c.name)
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
         * Ca GhostMod lan chinh engine goc deu tinh ca duong roi giao han cho nhan vat.
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
        require(callsSupport(planned, "approachPath"),
                "plannedPath() khong co duong do quanh o dich - cong nam tren o chan thi"
                + " tat Noclip la khong bao gio toi noi");

        /*
         * Nham goc de ne cong khac chi dung khi dang di xuyen dia hinh: diem goc neo vao
         * toa do hien tai cua nhan vat, ma di duong that thi toa do do doi ca hai truc moi
         * buoc - goc truot theo chan, duoi mai khong kip.
         */
        MethodNode rt = null;
        for (MethodNode m : support.methods) if ("routeTowards".equals(m.name)) rt = m;
        require(rt != null, "support khong con ham routeTowards()");
        require(readsField(rt, "HSOAutoTradeSupport", "noclipOn"),
                "routeTowards() khong xet noclipOn - se nham goc ne cong ca khi dang di"
                + " duong that, va goc do truot theo chan nhan vat");

        // Cac mod cu phai con nguyen.
        require(anyClassCalls(entries, "HSOSKYRuntimeSupport", "adjustTile"),
                "mat noclip (adjustTile) trong jar");
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
            if (owner.equals(c.owner) && name.equals(c.name)) return true;
        }
        return false;
    }

    /** Method nay co doc mot field cho truoc khong. */
    static boolean readsField(MethodNode m, String owner, String name) {
        if (m.instructions == null) return false;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof FieldInsnNode)) continue;
            FieldInsnNode f = (FieldInsnNode) n;
            if (owner.equals(f.owner) && name.equals(f.name)) return true;
        }
        return false;
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

    static boolean callsSupport(MethodNode m, String name) {
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode c = (MethodInsnNode) n;
            if (SUPPORT.equals(c.owner) && name.equals(c.name)) return true;
        }
        return false;
    }

    static boolean hasLdc(MethodNode m, String s) {
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof LdcInsnNode && s.equals(((LdcInsnNode) n).cst)) return true;
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
