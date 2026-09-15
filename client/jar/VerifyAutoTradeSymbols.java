import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Doc-only. Kiem tra tung dong trong autotrade-symbols.properties co that su ton tai
 * trong jar dich, dung kind (class/field/method) va dung descriptor.
 *
 * Ten obfuscate bi tai su dung cho ca class, field lan method, nen moi phep kiem tra
 * deu phai gan owner + descriptor - khong bao gio chi doi chieu ten.
 *
 * javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" VerifyAutoTradeSymbols.java
 * java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" VerifyAutoTradeSymbols [jar] [properties]
 */
public final class VerifyAutoTradeSymbols {

    static final Map<String, ClassNode> CLASSES = new LinkedHashMap<String, ClassNode>();
    static final List<String> PROBLEMS = new ArrayList<String>();
    static Properties SYM;

    public static void main(String[] args) throws Exception {
        String jarPath = args.length > 0 ? args[0] : "HSOLUNA_TRUONGBK_PERFECT.jar";
        String propPath = args.length > 1 ? args[1] : "autotrade-symbols.properties";
        load(jarPath);
        SYM = new Properties();
        InputStream in = new FileInputStream(propPath);
        try {
            SYM.load(in);
        } finally {
            in.close();
        }
        System.out.println("Jar        : " + jarPath + " (" + CLASSES.size() + " class)");
        System.out.println("Symbols    : " + propPath + " (" + SYM.size() + " dong)");
        System.out.println();

        // --- classes ---
        String[] classKeys = {"ENGINE", "LISTENER", "SENDER", "CANVAS", "CHARS", "ENTITY",
                "PLAYER", "ITEM", "VEC", "CMD", "TILEMAP", "WAYPOINT", "GRAPHICS"};
        for (String k : classKeys) klass("class." + k);

        // --- engine ---
        field("engine.field.INSTANCE", "class.ENGINE", "L" + get("class.ENGINE") + ";", true);
        field("engine.field.pendingMenu", "class.ENGINE", "I", true);
        String[] engineFlags = {"autoAttack", "autoHpMp", "autoHorse", "petFeed", "autoNextMap", "autoSell"};
        for (String k : engineFlags) field("engine.field." + k, "class.ENGINE", "Z", true);
        field("engine.field.reviveMode", "class.ENGINE", "I", true);
        field("engine.field.autoLoginChar", "class.ENGINE", "I", true);

        method("engine.method.showMainMenu", "class.ENGINE", "()V", true);
        method("engine.method.perform", "class.ENGINE", "(II)V", false);
        method("engine.method.update", "class.ENGINE", "()V", true);
        method("engine.method.paintHud", "class.ENGINE", "(L" + get("class.GRAPHICS") + ";)V", true);
        method("engine.method.paintArc", "class.ENGINE", "(L" + get("class.GRAPHICS") + ";)V", true);
        method("engine.method.onNpcMenuOpened", "class.ENGINE", "(SBL" + get("class.VEC") + ";)Z", true);
        method("engine.method.onNpcMenuLabel", "class.ENGINE", "(SLjava/lang/String;)Z", true);

        // --- sender: opcode duoc kiem chung rieng o duoi ---
        method("sender.gI", "class.SENDER", "()L" + get("class.SENDER") + ";", true);
        method("sender.move", "class.SENDER", "(SS)V", false);
        method("sender.buy", "class.SENDER", "(BSS)V", false);
        method("sender.useItem4", "class.SENDER", "(S)V", false);
        method("sender.npcMenuSelect", "class.SENDER", "(SBB)V", false);
        method("sender.requestMobInfo", "class.SENDER", "(S)V", false);
        method("sender.requestNpcMenu", "class.SENDER", "(B)V", false);

        // --- canvas ---
        field("canvas.currentScreen", "class.CANVAS", null, true);
        field("canvas.mapHolder", "class.CANVAS", null, true);
        field("canvas.menu", "class.CANVAS", null, true);
        field("canvas.keyHeld", "class.CANVAS", "[Z", true);
        field("canvas.keyPressed", "class.CANVAS", "[Z", true);
        method("canvas.showDialog", "class.CANVAS", "(Ljava/lang/String;)V", true);

        // --- chars / player / entity ---
        field("chars.player", "class.CHARS", "L" + get("class.PLAYER") + ";", true);
        field("chars.target", "class.CHARS", "L" + get("class.ENTITY") + ";", true);
        field("chars.entities", "class.CHARS", "L" + get("class.VEC") + ";", true);

        field("player.x", "class.PLAYER", "I", false);
        field("player.y", "class.PLAYER", "I", false);
        field("player.path", "class.PLAYER", "[S", false);
        field("player.stepDestX", "class.PLAYER", "I", false);
        field("player.stepDestY", "class.PLAYER", "I", false);
        field("player.hp", "class.PLAYER", "I", false);
        field("player.hpMax", "class.PLAYER", "I", false);
        method("player.startMove", "class.PLAYER", "()V", false);
        field("player.pathCursor", "class.PLAYER", "I", false);
        method("screen.findPath", "class.CHARS", "(IIIII)[S", false);

        field("entity.x", "class.ENTITY", "I", false);
        field("entity.y", "class.ENTITY", "I", false);
        field("entity.kind", "class.ENTITY", null, false);
        field("entity.name", "class.ENTITY", "Ljava/lang/String;", false);
        field("entity.npcId", "class.ENTITY", null, false);

        // --- mob: con bo di buon la MOB chu khong phai player ---
        klass("class.MOB");
        checkMobIsEntitySubclass();
        field("mob.name", "class.MOB", "Ljava/lang/String;", false);
        field("mob.template", "class.MOB", "I", false);
        checkMobShadowsEntity("mob.name", "Ljava/lang/String;");
        checkMobShadowsEntity("mob.template", "I");

        // --- item ---
        field("item.bag", "class.ITEM", "L" + get("class.VEC") + ";", true);
        field("item.names", "class.ITEM", "[Ljava/lang/String;", true);
        field("item.name", "class.ITEM", "Ljava/lang/String;", false);
        field("item.id", "class.ITEM", "I", false);
        field("item.category", "class.ITEM", "I", false);
        field("item.colour", "class.ITEM", null, false);

        // --- vector / cmd / map / waypoint ---
        method("vec.size", "class.VEC", "()I", false);
        method("vec.get", "class.VEC", "(I)Ljava/lang/Object;", false);
        method("vec.add", "class.VEC", "(Ljava/lang/Object;)V", false);
        field("cmd.label", "class.CMD", "Ljava/lang/String;", false);
        field("tilemap.waypoints", "class.TILEMAP", "L" + get("class.VEC") + ";", true);
        field("tilemap.tileSize", "class.TILEMAP", "I", true);
        field("waypoint.destName", "class.WAYPOINT", "Ljava/lang/String;", false);
        field("waypoint.x", "class.WAYPOINT", "I", false);
        field("waypoint.y", "class.WAYPOINT", "I", false);
        field("map.mapId", null, "I", false); // owner = kieu cua canvas.mapHolder, kiem rieng

        klass("class.MENUBOX");
        method("menubox.show", "class.MENUBOX",
                "(L" + get("class.VEC") + ";ILjava/lang/String;ZL" + get("class.VEC") + ";)V", false);

        method("canvas.tickAll", "class.CANVAS", "()V", true);

        // --- cua hang: id ngoc va loai cua hang phai doc tu day, khong hardcode ---
        klass("class.SHOP");
        field("shop.type", "class.SHOP", "I", false);
        checkShopItems();
        checkScreenClose();

        field("canvas.dialogA", "class.CANVAS", "L" + get("class.DIALOG") + ";", true);
        field("canvas.dialogB", "class.CANVAS", "L" + get("class.DIALOG") + ";", true);
        klass("class.DIALOG");
        field("dialog.lines", "class.DIALOG", "[Ljava/lang/String;", false);
        klass("class.INFOQ");
        klass("class.INFOBOX");
        field("infoq.holder", "class.INFOQ", "L" + get("class.INFOBOX") + ";", true);
        field("infobox.text1", "class.INFOBOX", "Ljava/lang/String;", false);
        field("infobox.text2", "class.INFOBOX", "Ljava/lang/String;", false);
        field("infobox.text3", "class.INFOBOX", "Ljava/lang/String;", false);

        klass("class.FONTS");
        field("fonts.small", "class.FONTS", null, true);
        checkFontDraw();

        klass("class.CHATBOX");
        klass("class.CHATTAB");
        method("chatbox.addLine", "class.CHATBOX",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;BZ)V", false);
        field("chatbox.instance", "class.CANVAS", "L" + get("class.CHATBOX") + ";", true);
        field("chatbox.tabs", "class.CHATBOX", "L" + get("class.VEC") + ";", true);
        field("chattab.name", "class.CHATTAB", "Ljava/lang/String;", false);

        checkMapHolder();
        checkSenderOpcodes();
        checkCmdCtor();

        System.out.println();
        if (PROBLEMS.isEmpty()) {
            System.out.println("OK - tat ca " + SYM.size() + " ky hieu deu khop jar.");
        } else {
            System.out.println("CO " + PROBLEMS.size() + " VAN DE:");
            for (String p : PROBLEMS) System.out.println("  - " + p);
            System.exit(1);
        }
    }

    // ------------------------------------------------------------- kiem tra

    /** MOB phai la lop con truc tiep cua ENTITY - cow() dua vao instanceof de loc player. */
    static void checkMobIsEntitySubclass() {
        ClassNode mob = CLASSES.get(get("class.MOB"));
        if (mob == null) return;
        if (!get("class.ENTITY").equals(mob.superName)) {
            PROBLEMS.add("class.MOB: super khong phai class.ENTITY");
            return;
        }
        ok("class.MOB", "ke thua truc tiep ENTITY");
    }

    /**
     * Canh bay: ten obfuscate cua mob.name / mob.template bi dung lai cho hai field khac
     * cua ENTITY, voi kieu khac. Khang dinh MOB tu khai bao chung (nen doc qua bien kieu
     * MOB la lay dung), va neu ENTITY cung co ten do thi kieu phai khac.
     */
    static void checkMobShadowsEntity(String key, String desc) {
        ClassNode mob = CLASSES.get(get("class.MOB"));
        ClassNode ent = CLASSES.get(get("class.ENTITY"));
        String name = get(key);
        if (mob == null || ent == null || name == null) return;
        boolean onMob = false;
        for (FieldNode f : mob.fields) if (f.name.equals(name) && desc.equals(f.desc)) onMob = true;
        if (!onMob) {
            PROBLEMS.add(key + ": MOB khong tu khai bao field nay, se lay nham field cua ENTITY");
            return;
        }
        String shadowed = null;
        for (FieldNode f : ent.fields) if (f.name.equals(name)) shadowed = f.desc;
        ok(key, shadowed == null ? "chi MOB co" : "MOB che field " + shorten(shadowed) + " cung ten cua ENTITY");
    }

    /** shop.items la danh sach hang: kieu phai la VEC hoac lop con cua VEC. */
    static void checkShopItems() {
        ClassNode shop = CLASSES.get(get("class.SHOP"));
        String name = get("shop.items");
        if (shop == null || name == null) { PROBLEMS.add("shop.items: thieu"); return; }
        for (FieldNode f : shop.fields) {
            if (!f.name.equals(name) || f.desc.charAt(0) != 'L') continue;
            String t = f.desc.substring(1, f.desc.length() - 1);
            for (String c = t; c != null; ) {
                if (c.equals(get("class.VEC"))) {
                    ok("shop.items", t.equals(c) ? "Vector" : "lop con cua Vector (len " + t.length() + ")");
                    return;
                }
                ClassNode cn = CLASSES.get(c);
                c = cn == null ? null : cn.superName;
            }
        }
        PROBLEMS.add("shop.items: khong phai Vector hoac lop con cua Vector");
    }

    /** screen.close nam tren lop CHA cua SHOP - goi no la dong cua hang y nhu bam thoat. */
    static void checkScreenClose() {
        ClassNode shop = CLASSES.get(get("class.SHOP"));
        String name = get("screen.close");
        if (shop == null || name == null) return;
        ClassNode parent = CLASSES.get(shop.superName);
        if (parent == null) { PROBLEMS.add("screen.close: khong tim thay lop cha cua SHOP"); return; }
        for (MethodNode m : parent.methods) {
            if (!m.name.equals(name) || !"()V".equals(m.desc)) continue;
            if ((m.access & Opcodes.ACC_STATIC) != 0) {
                PROBLEMS.add("screen.close: phai la method instance");
                return;
            }
            ok("screen.close", "()V tren lop cha cua SHOP (len " + parent.name.length() + ")");
            return;
        }
        PROBLEMS.add("screen.close: lop cha cua SHOP khong co method nay");
    }

    static void klass(String key) {
        String v = get(key);
        if (v == null) { PROBLEMS.add(key + ": thieu trong properties"); return; }
        if (!CLASSES.containsKey(v)) { PROBLEMS.add(key + ": class khong co trong jar"); return; }
        ok(key, "class len=" + v.length());
    }

    static void field(String key, String ownerKey, String expectDesc, boolean mustBeStatic) {
        String name = get(key);
        if (name == null) { PROBLEMS.add(key + ": thieu trong properties"); return; }
        if (ownerKey == null) return; // kiem rieng
        ClassNode cn = CLASSES.get(get(ownerKey));
        if (cn == null) { PROBLEMS.add(key + ": khong tim thay owner " + ownerKey); return; }
        FieldNode found = null;
        String declaredOn = null;
        for (ClassNode c = cn; c != null; c = CLASSES.get(c.superName)) {
            for (FieldNode f : c.fields) {
                if (!f.name.equals(name)) continue;
                if (expectDesc != null && !expectDesc.equals(f.desc)) continue;
                found = f;
                declaredOn = c.name;
                break;
            }
            if (found != null) break;
        }
        if (found == null) {
            PROBLEMS.add(key + ": khong co field " + shorten(name)
                    + (expectDesc == null ? "" : " : " + shorten(expectDesc)) + " tren " + ownerKey);
            return;
        }
        boolean isStatic = (found.access & Opcodes.ACC_STATIC) != 0;
        if (isStatic != mustBeStatic) {
            PROBLEMS.add(key + ": mong doi " + (mustBeStatic ? "static" : "instance") + " nhung nguoc lai");
            return;
        }
        ok(key, (isStatic ? "static " : "") + "field : " + shorten(found.desc)
                + (declaredOn.equals(cn.name) ? "" : "   (ke thua tu " + shorten(declaredOn) + ")"));
    }

    static void method(String key, String ownerKey, String desc, boolean mustBeStatic) {
        String name = get(key);
        if (name == null) { PROBLEMS.add(key + ": thieu trong properties"); return; }
        ClassNode cn = CLASSES.get(get(ownerKey));
        if (cn == null) { PROBLEMS.add(key + ": khong tim thay owner " + ownerKey); return; }
        MethodNode found = null;
        for (MethodNode m : cn.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) { found = m; break; }
        }
        if (found == null) {
            PROBLEMS.add(key + ": khong co method " + shorten(name) + shorten(desc) + " tren " + ownerKey);
            return;
        }
        boolean isStatic = (found.access & Opcodes.ACC_STATIC) != 0;
        if (isStatic != mustBeStatic) {
            PROBLEMS.add(key + ": mong doi " + (mustBeStatic ? "static" : "instance") + " nhung nguoc lai");
            return;
        }
        ok(key, (isStatic ? "static " : "") + "method " + shorten(desc));
    }

    /** font.draw(Graphics, String, int, int, int) tren kieu cua fonts.small */
    static void checkFontDraw() {
        ClassNode fonts = CLASSES.get(get("class.FONTS"));
        if (fonts == null) return;
        String small = get("fonts.small");
        String desc = null;
        for (FieldNode f : fonts.fields) if (f.name.equals(small)) { desc = f.desc; break; }
        if (desc == null || !desc.startsWith("L")) { PROBLEMS.add("fonts.small: khong phai object"); return; }
        ClassNode fontCn = CLASSES.get(desc.substring(1, desc.length() - 1));
        if (fontCn == null) { PROBLEMS.add("fonts.small: kieu khong co trong jar"); return; }
        String want = "(L" + get("class.GRAPHICS") + ";Ljava/lang/String;III)V";
        String draw = get("font.draw");
        for (MethodNode m : fontCn.methods) {
            if (m.name.equals(draw) && m.desc.equals(want)) { ok("font.draw", "(Graphics,String,int,int,int)"); return; }
        }
        PROBLEMS.add("font.draw: khong co method " + shorten(draw) + shorten(want));
    }

    /** canvas.mapHolder tro toi mot object co field map.mapId : I */
    static void checkMapHolder() {
        ClassNode canvas = CLASSES.get(get("class.CANVAS"));
        if (canvas == null) return;
        String holder = get("canvas.mapHolder");
        String desc = null;
        for (FieldNode f : canvas.fields) if (f.name.equals(holder)) { desc = f.desc; break; }
        if (desc == null || !desc.startsWith("L")) {
            PROBLEMS.add("canvas.mapHolder: khong phai kieu object");
            return;
        }
        ClassNode mapCn = CLASSES.get(desc.substring(1, desc.length() - 1));
        if (mapCn == null) { PROBLEMS.add("canvas.mapHolder: kieu khong co trong jar"); return; }
        String mapId = get("map.mapId");
        for (FieldNode f : mapCn.fields) {
            if (f.name.equals(mapId) && "I".equals(f.desc)) {
                ok("map.mapId", "field : I tren kieu cua canvas.mapHolder");
                return;
            }
        }
        PROBLEMS.add("map.mapId: khong co field int " + shorten(mapId) + " tren kieu cua canvas.mapHolder");
    }

    /**
     * Doi chieu opcode that su gui di cho tung method sender - day moi la bang chung
     * rang buy/move/useItem4/... dung la goi tin ta nghi.
     */
    static void checkSenderOpcodes() {
        System.out.println();
        System.out.println("-- Opcode goi tin cua tung method sender --");
        ClassNode sender = CLASSES.get(get("class.SENDER"));
        if (sender == null) return;
        Map<String, Integer> ops = SenderOpcodeReader.read(sender);
        Object[][] expect = {
                {"sender.move", "(SS)V", Integer.valueOf(4)},
                {"sender.buy", "(BSS)V", Integer.valueOf(24)},
                {"sender.useItem4", "(S)V", Integer.valueOf(32)},
                {"sender.npcMenuSelect", "(SBB)V", Integer.valueOf(-30)},
                {"sender.requestMobInfo", "(S)V", Integer.valueOf(7)},
                {"sender.requestNpcMenu", "(B)V", Integer.valueOf(23)},
        };
        for (Object[] e : expect) {
            String key = (String) e[0];
            String desc = (String) e[1];
            int want = ((Integer) e[2]).intValue();
            Integer got = ops.get(get(key) + desc);
            if (got == null) {
                PROBLEMS.add(key + ": khong doc duoc opcode");
            } else if (got.intValue() != want) {
                PROBLEMS.add(key + ": opcode la " + got + " chu khong phai " + want);
            } else {
                System.out.println("   OK  " + pad(key, 26) + "op " + want);
            }
        }
    }

    /** Ctor cua Cmd dung de chen dong menu moi. */
    static void checkCmdCtor() {
        ClassNode cmd = CLASSES.get(get("class.CMD"));
        if (cmd == null) return;
        String want = "(Ljava/lang/String;IL" + get("class.LISTENER") + ";)V";
        for (MethodNode m : cmd.methods) {
            if ("<init>".equals(m.name) && want.equals(m.desc)) {
                ok("cmd.<init>", "(String,int,LISTENER)");
                return;
            }
        }
        PROBLEMS.add("cmd.<init>: khong co ctor (String,int,LISTENER)");
    }

    // ----------------------------------------------------------------- utils

    static String get(String key) {
        return SYM.getProperty(key);
    }

    static void ok(String key, String what) {
        System.out.println("   OK  " + pad(key, 26) + what);
    }

    static String shorten(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c == 'I' || c == 'l') && s.length() - i > 18) {
                int j = i;
                while (j < s.length() && (s.charAt(j) == 'I' || s.charAt(j) == 'l')) j++;
                if (j - i > 18) { b.append("<").append(j - i).append("ch>"); i = j; continue; }
            }
            b.append(c);
            i++;
        }
        return b.toString();
    }

    static String pad(String s, int w) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < w) b.append(' ');
        return b.toString();
    }

    static void load(String jarPath) throws IOException {
        JarFile jar = new JarFile(jarPath);
        try {
            Enumeration<JarEntry> it = jar.entries();
            while (it.hasMoreElements()) {
                JarEntry e = it.nextElement();
                if (!e.getName().endsWith(".class")) continue;
                ClassNode cn = new ClassNode();
                InputStream in = jar.getInputStream(e);
                try {
                    new ClassReader(in).accept(cn, 0);
                } finally {
                    in.close();
                }
                CLASSES.put(cn.name, cn);
            }
        } finally {
            jar.close();
        }
    }
}
