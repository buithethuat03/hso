import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import javax.imageio.ImageIO;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
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

/**
 * Ports the HSO Luna XP popup (tools/luna/XpPopupPatcher) to Hung_By_Son: packet-30 type-1 XP text uses a
 * 2x bold-green font and the Luna motion profile (Luna's native launch/lifetime for type 1, scaled by
 * 4/9 speed and 9/4 lifetime, gravity on 4 of every 9 frames). Other popup types keep this client's behaviour.
 * Symbols are located structurally; the patcher stops when the expected shape is not found exactly.
 */
public final class HungBySonXpPopupPatcher implements Opcodes {
    private static final String NEW_FONT_FIELD = "hbsXp2xBoldGreen";
    private static final String NEW_PHASE_FIELD = "hbsXpFallPhase";
    private static final String FONT_IMAGE_INPUT = "x1/mfont/tahoma_7b_green.png";
    private static final String FONT_METRICS_INPUT = "mfont/tahoma_7b";
    private static final String FONT_IMAGE_OUTPUT = "x1/mfont/tahoma_14b_green.png";
    private static final String FONT_METRICS_OUTPUT = "mfont/tahoma_14b";
    private static final String FONT_IMAGE_PATH = "/mfont/tahoma_14b_green.png";
    private static final String FONT_METRICS_PATH = "/mfont/tahoma_14b";
    private static final String SOURCE_IMAGE_PATH = "/mfont/tahoma_7b_green.png";
    private static final String XP_TYPE_SOURCE_IMAGE_PATH = "/mfont/tahoma_7_green.png";

    // Luna native type-1 launch (vx = -rand(1,3), vy = -rand(8,12), life = rand(20,25)) and the
    // XpPopupPatcher profile on top of it (speed 4/9, lifetime 9/4).
    private static final int VX_MIN = 1, VX_MAX = 3;
    private static final int VY_MIN = 8, VY_MAX = 12;
    private static final int LIFE_MIN = 20, LIFE_MAX = 25;
    private static final int SPEED_NUM = 4, SPEED_DEN = 9;
    private static final int LIFE_NUM = 9, LIFE_DEN = 4;

    private static final class EntryData {
        final JarEntry source;
        byte[] bytes;
        EntryData(JarEntry source, byte[] bytes) { this.source = source; this.bytes = bytes; }
    }

    private HungBySonXpPopupPatcher() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.jar output.jar");
        File input = new File(args[0]).getCanonicalFile();
        File output = new File(args[1]).getCanonicalFile();
        if (!input.isFile() || input.equals(output) || output.exists()) {
            throw new IllegalArgumentException("Input missing, equal to output, or output exists");
        }
        List<EntryData> entries = read(input);
        require(find(entries, FONT_IMAGE_OUTPUT) == null && find(entries, FONT_METRICS_OUTPUT) == null, "already patched");

        // Popup class: constructors (String,III)V and (String,IIII)V.
        EntryData popupEntry = null;
        ClassNode popup = null;
        for (EntryData e : entries) {
            if (!e.source.getName().endsWith(".class")) continue;
            ClassNode n = parse(e.bytes);
            if (findMethod(n, "<init>", "(Ljava/lang/String;III)V") != null
                    && findMethod(n, "<init>", "(Ljava/lang/String;IIII)V") != null) {
                require(popup == null, "single popup class");
                popup = n; popupEntry = e;
            }
        }
        require(popup != null, "popup class");
        require(findField(popup, NEW_PHASE_FIELD) == null, "phase field absent");
        EntryData baseEntry = entry(entries, popup.superName + ".class");
        ClassNode base = parse(baseEntry.bytes);
        MethodNode ctor = findMethod(popup, "<init>", "(Ljava/lang/String;III)V");

        // Constructor fields.
        String typeField = putFieldAfterLoad(ctor, popup.name, ILOAD, 4, "I");
        TableSwitchInsnNode sw = firstTableSwitch(ctor);
        require(sw.min == 1, "type switch starts at 1");
        AbstractInsnNode case1 = next(sw.labels.get(0));
        require(case1 instanceof VarInsnNode && case1.getOpcode() == ALOAD, "type-1 case");
        AbstractInsnNode xpFontGet = next(case1);
        AbstractInsnNode xpFontPut = next(xpFontGet);
        require(xpFontGet instanceof FieldInsnNode && xpFontGet.getOpcode() == GETSTATIC
                && xpFontPut instanceof FieldInsnNode && xpFontPut.getOpcode() == PUTFIELD, "type-1 font selection");
        FieldInsnNode xpFont = (FieldInsnNode) xpFontGet;
        String fontClass = Type.getType(xpFont.desc).getInternalName();
        String fontDesc = xpFont.desc;

        MethodInsnNode rand = null;
        String lifetimeField = null;
        String velocityYField = null;
        for (AbstractInsnNode i = ctor.instructions.getFirst(); i != null; i = i.getNext()) {
            if (isInt(i, LIFE_MIN) && isInt(next(i), LIFE_MAX) && next(next(i)) instanceof MethodInsnNode) {
                MethodInsnNode r = (MethodInsnNode) next(next(i));
                AbstractInsnNode put = next(r);
                require(r.getOpcode() == INVOKESTATIC && "(II)I".equals(r.desc)
                        && put instanceof FieldInsnNode && put.getOpcode() == PUTFIELD, "lifetime rand");
                if (rand == null) { rand = r; lifetimeField = ((FieldInsnNode) put).name; }
                else require(lifetimeField.equals(((FieldInsnNode) put).name), "single lifetime field");
            }
        }
        require(rand != null && lifetimeField != null, "lifetime field");
        // velocityY: every "-rand(a,b)" store in the constructor targets the same base field.
        for (AbstractInsnNode i = ctor.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode && i.getOpcode() == INVOKESTATIC && ((MethodInsnNode) i).name.equals(rand.name)
                    && ((MethodInsnNode) i).owner.equals(rand.owner) && next(i) != null && next(i).getOpcode() == INEG) {
                AbstractInsnNode put = next(next(i));
                require(put instanceof FieldInsnNode && put.getOpcode() == PUTFIELD
                        && ((FieldInsnNode) put).owner.equals(base.name), "vy store on base");
                require(velocityYField == null || velocityYField.equals(((FieldInsnNode) put).name), "single vy field");
                velocityYField = ((FieldInsnNode) put).name;
            }
        }
        require(velocityYField != null, "velocityY field");

        // Base update: x += vx; y += vy.
        MethodNode baseUpdate = findMethod(base, "a", "()V");
        String velocityXField = null;
        {
            List<FieldInsnNode> gets = new ArrayList<FieldInsnNode>();
            for (AbstractInsnNode i = baseUpdate.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof FieldInsnNode && i.getOpcode() == GETFIELD) gets.add((FieldInsnNode) i);
            }
            require(gets.size() == 4 && gets.get(3).name.equals(velocityYField), "base update x+=vx; y+=vy");
            velocityXField = gets.get(1).name;
        }

        // Popup update: age++ ... super.update() ... if (age >= lifetime) dead = true.
        MethodNode update = findMethod(popup, baseUpdate.name, baseUpdate.desc);
        require(update != null, "popup update");
        String ageField = null, deadField = null, boldField = null;
        for (AbstractInsnNode i = update.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode) i;
                if (ageField == null && f.getOpcode() == GETFIELD && "I".equals(f.desc)) ageField = f.name;
                if (boldField == null && f.getOpcode() == GETFIELD && "Z".equals(f.desc) && f.owner.equals(popup.name)) boldField = f.name;
                if (f.getOpcode() == PUTFIELD && "Z".equals(f.desc)) deadField = f.name;
            }
        }
        require(ageField != null && deadField != null && boldField != null, "update fields");
        require(hasMethodCall(update, INVOKESPECIAL, base.name, baseUpdate.name), "update calls base");

        // Font class: add the 2x field and build it inside the method that builds the regular XP font.
        EntryData fontEntry = entry(entries, fontClass + ".class");
        ClassNode font = parse(fontEntry.bytes);
        require(findField(font, NEW_FONT_FIELD) == null, "font field absent");
        MethodNode fontInit = null;
        MethodInsnNode fontCtor = null;
        FieldInsnNode charsField = null;
        AbstractInsnNode fontIntArg = null;
        for (MethodNode m : font.methods) {
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof FieldInsnNode && i.getOpcode() == PUTSTATIC && ((FieldInsnNode) i).name.equals(xpFont.name)) {
                    MethodInsnNode c = (MethodInsnNode) previous(i);
                    require(c.name.equals("<init>") && c.owner.equals(fontClass), "xp font construction");
                    fontInit = m; fontCtor = c;
                }
            }
        }
        require(fontInit != null, "font init method");
        require(fontCtor.desc.equals("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V"), "font ctor desc");
        // Take the chars field and int argument from the bold-green construction (the 2x source).
        for (AbstractInsnNode i = fontInit.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof LdcInsnNode && SOURCE_IMAGE_PATH.equals(((LdcInsnNode) i).cst)) {
                AbstractInsnNode chars = previous(i);
                AbstractInsnNode metrics = next(i);
                AbstractInsnNode arg = next(metrics);
                AbstractInsnNode call = next(arg);
                require(chars instanceof FieldInsnNode && chars.getOpcode() == GETSTATIC
                        && metrics instanceof LdcInsnNode && ("/" + FONT_METRICS_INPUT).equals(((LdcInsnNode) metrics).cst)
                        && intOf(arg) != null && call instanceof MethodInsnNode
                        && ((MethodInsnNode) call).name.equals("<init>")
                        && ((MethodInsnNode) call).desc.equals(fontCtor.desc), "bold green construction");
                charsField = (FieldInsnNode) chars;
                fontIntArg = arg;
            }
        }
        require(charsField != null, "bold-green font construction");
        require(hasLdc(fontInit, XP_TYPE_SOURCE_IMAGE_PATH), "type-1 font is the regular green font");
        String chars = staticString(font, charsField.name);

        FontAssets xp = buildCompactXpFont(entry(entries, FONT_IMAGE_INPUT).bytes, entry(entries, FONT_METRICS_INPUT).bytes, chars);

        // --- Font class patch ---
        font.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, NEW_FONT_FIELD, fontDesc, null, null));
        InsnList mk = new InsnList();
        mk.add(new TypeInsnNode(NEW, fontClass));
        mk.add(new InsnNode(DUP));
        mk.add(new FieldInsnNode(GETSTATIC, charsField.owner, charsField.name, charsField.desc));
        mk.add(new LdcInsnNode(FONT_IMAGE_PATH));
        mk.add(new LdcInsnNode(FONT_METRICS_PATH));
        mk.add(new IntInsnNode(BIPUSH, intOf(fontIntArg).intValue()));
        mk.add(new MethodInsnNode(INVOKESPECIAL, fontClass, "<init>", fontCtor.desc, false));
        mk.add(new FieldInsnNode(PUTSTATIC, fontClass, NEW_FONT_FIELD, fontDesc));
        int fontReturns = 0;
        for (AbstractInsnNode i = fontInit.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i.getOpcode() == RETURN) { fontInit.instructions.insertBefore(i, clone(mk)); fontReturns++; }
        }
        require(fontReturns == 1, "font init has one return");
        fontEntry.bytes = write(font);

        // --- Popup patch ---
        xpFont.owner = fontClass;
        xpFont.name = NEW_FONT_FIELD;
        popup.fields.add(new FieldNode(ACC_PRIVATE, NEW_PHASE_FIELD, "I", null, null));

        InsnList launch = new InsnList();
        LabelNode notXp = new LabelNode();
        launch.add(new VarInsnNode(ALOAD, 0));
        launch.add(new FieldInsnNode(GETFIELD, popup.name, typeField, "I"));
        launch.add(new InsnNode(ICONST_1));
        launch.add(new JumpInsnNode(IF_ICMPNE, notXp));
        // vx = -rand(1,3)  (Luna's sign helper is called with (0,1) and always yields the negative branch)
        launch.add(new VarInsnNode(ALOAD, 0));
        launch.add(randCall(rand, VX_MIN, VX_MAX));
        launch.add(new InsnNode(INEG));
        launch.add(new FieldInsnNode(PUTFIELD, base.name, velocityXField, "I"));
        // vy = -rand(8,12) * 4 / 9
        launch.add(new VarInsnNode(ALOAD, 0));
        launch.add(randCall(rand, VY_MIN, VY_MAX));
        launch.add(new InsnNode(INEG));
        launch.add(new IntInsnNode(BIPUSH, SPEED_NUM));
        launch.add(new InsnNode(IMUL));
        launch.add(new IntInsnNode(BIPUSH, SPEED_DEN));
        launch.add(new InsnNode(IDIV));
        launch.add(new FieldInsnNode(PUTFIELD, base.name, velocityYField, "I"));
        // lifetime = rand(20,25) * 9 / 4
        launch.add(new VarInsnNode(ALOAD, 0));
        launch.add(randCall(rand, LIFE_MIN, LIFE_MAX));
        launch.add(new IntInsnNode(BIPUSH, LIFE_NUM));
        launch.add(new InsnNode(IMUL));
        launch.add(new IntInsnNode(BIPUSH, LIFE_DEN));
        launch.add(new InsnNode(IDIV));
        launch.add(new FieldInsnNode(PUTFIELD, popup.name, lifetimeField, "I"));
        launch.add(notXp);
        int ctorReturns = 0;
        for (AbstractInsnNode i = ctor.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i.getOpcode() == RETURN) { ctor.instructions.insertBefore(i, clone(launch)); ctorReturns++; }
        }
        require(ctorReturns == 2, "popup constructor returns");

        rewriteUpdate(update, popup.name, base.name, baseUpdate, typeField, boldField, ageField,
                velocityYField, lifetimeField, deadField);
        popupEntry.bytes = write(popup);

        entries.add(new EntryData(new JarEntry(FONT_IMAGE_OUTPUT), xp.image));
        entries.add(new EntryData(new JarEntry(FONT_METRICS_OUTPUT), xp.metrics));

        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot make output directory");
        writeJar(output, entries);
        verify(output, popup.name, fontClass, fontDesc, entries.size());

        System.out.println("POPUP=" + popup.name + " base=" + base.name);
        System.out.println("FIELDS type=" + typeField + " vx=" + velocityXField + " vy=" + velocityYField
                + " life=" + lifetimeField + " age=" + ageField + " dead=" + deadField + " bold=" + boldField);
        System.out.println("FONT=" + fontClass + " xpFontWas=" + "regular green" + " new=" + NEW_FONT_FIELD
                + " intArg=" + intOf(fontIntArg));
        System.out.println("XP_FONT_PNG=" + FONT_IMAGE_OUTPUT + " bytes=" + xp.image.length);
        System.out.println("ENTRIES=" + entries.size());
    }

    /** age++; type 1: +1 gravity on 4 of 9 frames; other bold popups: +1 gravity (native); base update; expire. */
    private static void rewriteUpdate(MethodNode update, String popup, String base, MethodNode baseUpdate,
            String typeField, String boldField, String ageField, String vy, String life, String dead) {
        LabelNode notXp = new LabelNode(), phaseReset = new LabelNode(), afterGravity = new LabelNode(), end = new LabelNode();
        InsnList c = new InsnList();
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new InsnNode(DUP));
        c.add(new FieldInsnNode(GETFIELD, base, ageField, "I"));
        c.add(new InsnNode(ICONST_1));
        c.add(new InsnNode(IADD));
        c.add(new FieldInsnNode(PUTFIELD, base, ageField, "I"));

        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new FieldInsnNode(GETFIELD, popup, typeField, "I"));
        c.add(new InsnNode(ICONST_1));
        c.add(new JumpInsnNode(IF_ICMPNE, notXp));
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new InsnNode(DUP));
        c.add(new FieldInsnNode(GETFIELD, popup, NEW_PHASE_FIELD, "I"));
        c.add(new InsnNode(ICONST_1));
        c.add(new InsnNode(IADD));
        c.add(new FieldInsnNode(PUTFIELD, popup, NEW_PHASE_FIELD, "I"));
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new FieldInsnNode(GETFIELD, popup, NEW_PHASE_FIELD, "I"));
        c.add(new InsnNode(ICONST_5));
        c.add(new JumpInsnNode(IF_ICMPGE, phaseReset));
        addGravity(c, base, vy);
        c.add(new JumpInsnNode(GOTO, afterGravity));
        c.add(phaseReset);
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new FieldInsnNode(GETFIELD, popup, NEW_PHASE_FIELD, "I"));
        c.add(new IntInsnNode(BIPUSH, 9));
        c.add(new JumpInsnNode(IF_ICMPLT, afterGravity));
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new InsnNode(ICONST_0));
        c.add(new FieldInsnNode(PUTFIELD, popup, NEW_PHASE_FIELD, "I"));
        c.add(new JumpInsnNode(GOTO, afterGravity));

        c.add(notXp);
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new FieldInsnNode(GETFIELD, popup, boldField, "Z"));
        c.add(new JumpInsnNode(IFEQ, afterGravity));
        addGravity(c, base, vy);

        c.add(afterGravity);
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new MethodInsnNode(INVOKESPECIAL, base, baseUpdate.name, baseUpdate.desc, false));
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new FieldInsnNode(GETFIELD, base, ageField, "I"));
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new FieldInsnNode(GETFIELD, popup, life, "I"));
        c.add(new JumpInsnNode(IF_ICMPLT, end));
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new InsnNode(ICONST_1));
        c.add(new FieldInsnNode(PUTFIELD, base, dead, "Z"));
        c.add(end);
        c.add(new InsnNode(RETURN));
        update.instructions.clear();
        update.tryCatchBlocks.clear();
        if (update.localVariables != null) update.localVariables.clear();
        update.instructions.add(c);
    }

    private static void addGravity(InsnList c, String base, String vy) {
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new InsnNode(DUP));
        c.add(new FieldInsnNode(GETFIELD, base, vy, "I"));
        c.add(new InsnNode(ICONST_1));
        c.add(new InsnNode(IADD));
        c.add(new FieldInsnNode(PUTFIELD, base, vy, "I"));
    }

    private static InsnList randCall(MethodInsnNode rand, int min, int max) {
        InsnList l = new InsnList();
        l.add(new IntInsnNode(BIPUSH, min));
        l.add(new IntInsnNode(BIPUSH, max));
        l.add(new MethodInsnNode(INVOKESTATIC, rand.owner, rand.name, rand.desc, false));
        return l;
    }

    private static void verify(File output, String popup, String font, String fontDesc, int expected) throws Exception {
        JarFile jar = new JarFile(output);
        try {
            require(jar.size() == expected, "entry count");
            ClassNode p = parse(readAll(jar.getInputStream(jar.getJarEntry(popup + ".class"))));
            ClassNode f = parse(readAll(jar.getInputStream(jar.getJarEntry(font + ".class"))));
            FieldNode nf = findField(f, NEW_FONT_FIELD);
            require(nf != null && (nf.access & ACC_PUBLIC) != 0 && fontDesc.equals(nf.desc), "public 2x font field");
            require(findField(p, NEW_PHASE_FIELD) != null, "phase field");
            int uses = 0;
            for (MethodNode m : p.methods) {
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i instanceof FieldInsnNode && ((FieldInsnNode) i).name.equals(NEW_FONT_FIELD)) uses++;
                }
            }
            require(uses == 1, "one 2x font use in popup");
            require(jar.getJarEntry(FONT_IMAGE_OUTPUT) != null && jar.getJarEntry(FONT_METRICS_OUTPUT) != null, "font resources");
            require(p.version == V1_4 && f.version == V1_4, "class versions unchanged");
        } finally { jar.close(); }
    }

    // ---- compact 2x XP font (same method as tools/luna/XpPopupPatcher) ----

    private static final class FontAssets {
        final byte[] image, metrics;
        FontAssets(byte[] image, byte[] metrics) { this.image = image; this.metrics = metrics; }
    }

    private static boolean isXpCharacter(char c) {
        return c == ' ' || c == '+' || c == '-' || c == 'x' || c == 'p' || (c >= '0' && c <= '9');
    }

    private static FontAssets buildCompactXpFont(byte[] imageBytes, byte[] metricBytes, String chars) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(metricBytes));
        int count = in.readUnsignedShort();
        int[][] v = new int[count][4];
        for (int i = 0; i < count; i++) for (int j = 0; j < 4; j++) v[i][j] = in.readShort();
        require(in.read() < 0, "unexpected trailing font metric data");
        require(chars.length() == count, "font character table does not match metric count");

        BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
        require(src != null && src.getColorModel() instanceof IndexColorModel, "indexed source font PNG");
        int width = 1, height = 0;
        int[] oy = new int[count], ow = new int[count], oh = new int[count];
        for (int i = 0; i < count; i++) {
            if (!isXpCharacter(chars.charAt(i))) continue;
            require(v[i][0] >= 0 && v[i][1] >= 0 && v[i][0] + v[i][2] <= src.getWidth()
                    && v[i][1] + v[i][3] <= src.getHeight(), "glyph inside source image");
            oy[i] = height;
            ow[i] = v[i][2] * 2;
            oh[i] = v[i][3] * 2;
            width = Math.max(width, ow[i]);
            height += Math.max(1, oh[i]);
        }
        require(height > 0, "XP glyphs present");
        IndexColorModel cm = (IndexColorModel) src.getColorModel();
        WritableRaster out = cm.createCompatibleWritableRaster(width, height);
        BufferedImage dst = new BufferedImage(cm, out, cm.isAlphaPremultiplied(), null);
        Raster r = src.getRaster();
        for (int i = 0; i < count; i++) {
            if (!isXpCharacter(chars.charAt(i))) continue;
            for (int y = 0; y < v[i][3]; y++) {
                for (int x = 0; x < v[i][2]; x++) {
                    Object px = r.getDataElements(v[i][0] + x, v[i][1] + y, null);
                    int dx = x * 2, dy = oy[i] + y * 2;
                    out.setDataElements(dx, dy, px);
                    out.setDataElements(dx + 1, dy, px);
                    out.setDataElements(dx, dy + 1, px);
                    out.setDataElements(dx + 1, dy + 1, px);
                }
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        require(ImageIO.write(dst, "png", png), "encode 2x PNG");
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        DataOutputStream m = new DataOutputStream(raw);
        m.writeShort(count);
        for (int i = 0; i < count; i++) {
            m.writeShort(0);
            m.writeShort(oy[i]);
            m.writeShort(ow[i]);
            m.writeShort(oh[i]);
        }
        m.close();
        return new FontAssets(png.toByteArray(), raw.toByteArray());
    }

    private static String staticString(ClassNode node, String field) {
        for (MethodNode m : node.methods) {
            String pending = null;
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof LdcInsnNode && ((LdcInsnNode) i).cst instanceof String) { pending = (String) ((LdcInsnNode) i).cst; continue; }
                if (i instanceof FieldInsnNode && i.getOpcode() == PUTSTATIC && field.equals(((FieldInsnNode) i).name) && pending != null) return pending;
                if (i.getOpcode() >= 0) pending = null;
            }
        }
        throw new IllegalStateException("font character string not found");
    }

    // ---- helpers ----

    private static String putFieldAfterLoad(MethodNode m, String owner, int loadOp, int var, String desc) {
        String result = null;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof VarInsnNode && i.getOpcode() == loadOp && ((VarInsnNode) i).var == var) {
                AbstractInsnNode put = next(i);
                if (put instanceof FieldInsnNode && put.getOpcode() == PUTFIELD && ((FieldInsnNode) put).owner.equals(owner)
                        && desc.equals(((FieldInsnNode) put).desc)) {
                    require(result == null || result.equals(((FieldInsnNode) put).name), "single type field");
                    result = ((FieldInsnNode) put).name;
                }
            }
        }
        require(result != null, "type field");
        return result;
    }
    private static TableSwitchInsnNode firstTableSwitch(MethodNode m) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof TableSwitchInsnNode) return (TableSwitchInsnNode) i;
        }
        throw new IllegalStateException("type switch missing");
    }
    private static boolean hasMethodCall(MethodNode m, int op, String owner, String name) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode && i.getOpcode() == op && ((MethodInsnNode) i).owner.equals(owner)
                    && ((MethodInsnNode) i).name.equals(name)) return true;
        }
        return false;
    }
    private static boolean hasLdc(MethodNode m, String s) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof LdcInsnNode && s.equals(((LdcInsnNode) i).cst)) return true;
        }
        return false;
    }
    private static boolean isInt(AbstractInsnNode i, int v) { Integer x = intOf(i); return x != null && x.intValue() == v; }
    private static Integer intOf(AbstractInsnNode i) {
        if (i == null) return null;
        int op = i.getOpcode();
        if (op >= ICONST_M1 && op <= ICONST_5) return Integer.valueOf(op - ICONST_0);
        if (op == BIPUSH || op == SIPUSH) return Integer.valueOf(((IntInsnNode) i).operand);
        return null;
    }
    private static InsnList clone(InsnList src) {
        HashMap<LabelNode, LabelNode> labels = new HashMap<LabelNode, LabelNode>();
        for (AbstractInsnNode i = src.getFirst(); i != null; i = i.getNext()) if (i instanceof LabelNode) labels.put((LabelNode) i, new LabelNode());
        InsnList out = new InsnList();
        for (AbstractInsnNode i = src.getFirst(); i != null; i = i.getNext()) out.add(i.clone(labels));
        return out;
    }
    private static MethodNode findMethod(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        return null;
    }
    private static FieldNode findField(ClassNode c, String name) {
        for (FieldNode f : c.fields) if (f.name.equals(name)) return f;
        return null;
    }
    private static AbstractInsnNode next(AbstractInsnNode i) {
        AbstractInsnNode n = i == null ? null : i.getNext();
        while (n != null && n.getOpcode() < 0) n = n.getNext();
        return n;
    }
    private static AbstractInsnNode previous(AbstractInsnNode i) {
        AbstractInsnNode n = i == null ? null : i.getPrevious();
        while (n != null && n.getOpcode() < 0) n = n.getPrevious();
        return n;
    }
    private static void require(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("Unexpected structure: " + what);
    }
    private static ClassNode parse(byte[] b) { ClassNode n = new ClassNode(ASM9); new ClassReader(b).accept(n, 0); return n; }
    private static byte[] write(ClassNode n) {
        require(n.version == V1_4, "class version 48 (no stack map frames)");
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        n.accept(w);
        return w.toByteArray();
    }
    private static EntryData find(List<EntryData> entries, String name) {
        for (EntryData e : entries) if (e.source.getName().equals(name)) return e;
        return null;
    }
    private static EntryData entry(List<EntryData> entries, String name) {
        EntryData e = find(entries, name);
        if (e == null) throw new IllegalStateException("Missing entry " + name);
        return e;
    }
    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) >= 0;) out.write(b, 0, n);
            return out.toByteArray();
        } finally { in.close(); }
    }
    private static List<EntryData> read(File input) throws IOException {
        List<EntryData> result = new ArrayList<EntryData>();
        JarFile jar = new JarFile(input);
        try {
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                JarEntry entry = e.nextElement();
                result.add(new EntryData(entry, readAll(jar.getInputStream(entry))));
            }
        } finally { jar.close(); }
        return result;
    }
    private static void writeJar(File output, List<EntryData> entries) throws IOException {
        JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
        try {
            for (EntryData d : entries) {
                JarEntry e = new JarEntry(d.source.getName());
                if (d.source.getTime() >= 0) e.setTime(d.source.getTime());
                out.putNextEntry(e);
                out.write(d.bytes);
                out.closeEntry();
            }
        } finally { out.close(); }
    }
}
