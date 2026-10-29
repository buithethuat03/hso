package luna.tools;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import javax.imageio.ImageIO;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Applies the XP popup presentation patch to a newly generated Luna JAR.
 *
 * The patch is deliberately driven by the versioned symbol catalog.  It does
 * not guess an obfuscated name and it fails closed when the baseline shape has
 * changed.  The native client still owns packet handling and popup creation;
 * this tool only changes the type-1 XP popup renderer and its motion profile.
 */
public final class XpPopupPatcher implements Opcodes {

    private static final String NEW_FONT_FIELD = "lunaXp2xBoldGreen";
    private static final String NEW_PHASE_FIELD = "lunaXpFallPhase";
    private static final String FONT_IMAGE_INPUT = "x1/mfont/tahoma_7b_green.png";
    private static final String FONT_METRICS_INPUT = "mfont/tahoma_7b";
    private static final String FONT_IMAGE_OUTPUT = "x1/mfont/tahoma_14b_green.png";
    private static final String FONT_METRICS_OUTPUT = "mfont/tahoma_14b";

    // The previous release already applied 2/3 speed and 1.5x lifetime.
    // Apply the requested additional 2/3 and 1.5x from that release here.
    private static final int XP_SPEED_NUMERATOR = 4;
    private static final int XP_SPEED_DENOMINATOR = 9;
    private static final int XP_LIFETIME_NUMERATOR = 9;
    private static final int XP_LIFETIME_DENOMINATOR = 4;

    private XpPopupPatcher() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: java -cp build/tools;asm*.jar luna.tools.XpPopupPatcher <input.jar> <output.jar>");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        if (!input.isFile()) throw new IllegalArgumentException("input JAR not found: " + input);
        if (output.exists()) throw new IllegalArgumentException("refusing to overwrite: " + output);

        String symbolPath = System.getProperty("luna.symbols", "symbols/autotrade-symbols.properties");
        Properties symbols = load(new File(symbolPath));
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        Map<String, Long> times = new LinkedHashMap<String, Long>();
        readJar(input, entries, times);

        String fontClass = required(symbols, "xp.font.class");
        String popupClass = required(symbols, "xp.popup.class");
        String popupBase = required(symbols, "xp.popup.base.class");
        String popupUpdate = required(symbols, "xp.popup.update.name");
        String regularGreen = required(symbols, "xp.font.green.field");
        String boldGreen = required(symbols, "xp.font.bold.green.field");
        String charsField = required(symbols, "xp.font.chars.field");
        String typeField = required(symbols, "xp.popup.type.field");
        String ageField = required(symbols, "xp.popup.age.field");
        String velocityYField = required(symbols, "xp.popup.velocityY.field");
        String aliveField = required(symbols, "xp.popup.alive.field");
        String lifetimeField = required(symbols, "xp.popup.lifetime.field");
        String constructorDesc = required(symbols, "xp.popup.constructor.desc");
        String updateDesc = required(symbols, "xp.popup.update.desc");
        String fontConstructorDesc = required(symbols, "xp.font.constructor.desc");

        String fontDesc = "L" + fontClass + ";";
        require(entries.containsKey(fontClass + ".class"), "font class missing from input JAR");
        require(entries.containsKey(popupClass + ".class"), "XP popup class missing from input JAR");
        require(entries.containsKey(popupBase + ".class"), "XP popup base class missing from input JAR");

        byte[] sourceImage = requiredEntry(entries, FONT_IMAGE_INPUT);
        byte[] sourceMetrics = requiredEntry(entries, FONT_METRICS_INPUT);
        require(!entries.containsKey(FONT_IMAGE_OUTPUT), "scaled XP font image already exists");
        require(!entries.containsKey(FONT_METRICS_OUTPUT), "scaled XP font metrics already exists");
        ClassNode originalFont = readClass(entries.get(fontClass + ".class"));
        String fontChars = staticString(originalFont, charsField);
        FontAssets xpAssets = buildCompactXpFont(sourceImage, sourceMetrics, fontChars);
        entries.put(FONT_IMAGE_OUTPUT, xpAssets.image);
        entries.put(FONT_METRICS_OUTPUT, xpAssets.metrics);
        long assetTime = times.containsKey(FONT_IMAGE_INPUT)
                ? times.get(FONT_IMAGE_INPUT).longValue() : System.currentTimeMillis();
        times.put(FONT_IMAGE_OUTPUT, Long.valueOf(assetTime));
        times.put(FONT_METRICS_OUTPUT, Long.valueOf(
                times.containsKey(FONT_METRICS_INPUT)
                        ? times.get(FONT_METRICS_INPUT).longValue() : assetTime));

        byte[] fontBytes = patchFontClass(entries.get(fontClass + ".class"), fontClass,
                charsField, regularGreen, boldGreen, fontDesc, fontConstructorDesc);
        entries.put(fontClass + ".class", fontBytes);

        byte[] popupBytes = patchPopupClass(entries.get(popupClass + ".class"), popupClass,
                popupBase, popupUpdate, updateDesc, constructorDesc, typeField, ageField,
                velocityYField, aliveField, lifetimeField, fontClass, regularGreen, fontDesc);
        entries.put(popupClass + ".class", popupBytes);

        writeJar(output, entries, times);
        verifyPatchedJar(output, fontClass, popupClass, fontDesc, popupBase,
                popupUpdate, updateDesc, constructorDesc);
        System.out.println("xpPopup=OK");
        System.out.println("xpFontImage=" + FONT_IMAGE_OUTPUT + " (2x nearest-neighbor)");
        System.out.println("xpFontMetrics=" + FONT_METRICS_OUTPUT + " (2x glyph metrics)");
        System.out.println("xpLifetimeScale=2.25 (additional 1.5x)");
        System.out.println("xpFallScale=2.25 (additional 1.5x slower)");
    }

    private static byte[] patchFontClass(byte[] bytes, String fontClass,
            String charsField, String regularGreen, String boldGreen,
            String fontDesc, String constructorDesc) {
        ClassNode node = readClass(bytes);
        require(fontClass.equals(node.name), "font symbol points to a different class");
        require(findField(node, regularGreen, fontDesc) != null,
                "regular-green font symbol is not a field of the font class");
        require(findField(node, boldGreen, fontDesc) != null,
                "bold-green font symbol is not a field of the font class");
        require(findField(node, NEW_FONT_FIELD, fontDesc) == null,
                "font patch field already exists in input class");
        // The popup class is a separate obfuscated class.  CLDC enforces
        // access checks at the call site, so this generated bridge field
        // cannot be private even though it is implementation-only.
        node.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
                NEW_FONT_FIELD, fontDesc, null, null));

        MethodNode clinit = method(node, "<clinit>", "()V");
        AbstractInsnNode returnInsn = null;
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (insn.getOpcode() == RETURN) returnInsn = insn;
        }
        require(returnInsn != null, "font class has no class initializer return");

        InsnList init = new InsnList();
        init.add(new TypeInsnNode(NEW, fontClass));
        init.add(new InsnNode(DUP));
        init.add(new FieldInsnNode(GETSTATIC, fontClass, charsField, "Ljava/lang/String;"));
        init.add(new org.objectweb.asm.tree.LdcInsnNode("/mfont/tahoma_14b_green.png"));
        init.add(new org.objectweb.asm.tree.LdcInsnNode("/mfont/tahoma_14b"));
        init.add(new MethodInsnNode(INVOKESPECIAL, fontClass, "<init>", constructorDesc, false));
        init.add(new FieldInsnNode(PUTSTATIC, fontClass, NEW_FONT_FIELD, fontDesc));
        clinit.instructions.insertBefore(returnInsn, init);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] patchPopupClass(byte[] bytes, String popupClass, String popupBase,
            String popupUpdate, String updateDesc, String constructorDesc, String typeField,
            String ageField, String velocityYField, String aliveField, String lifetimeField,
            String fontClass, String regularGreen, String fontDesc) {
        ClassNode node = readClass(bytes);
        require(popupClass.equals(node.name), "popup symbol points to a different class");
        require(popupBase.equals(node.superName), "XP popup superclass changed");
        require(findField(node, NEW_PHASE_FIELD, "I") == null,
                "popup phase field already exists in input class");
        node.fields.add(new FieldNode(ACC_PRIVATE, NEW_PHASE_FIELD, "I", null, null));

        MethodNode constructor = method(node, "<init>", constructorDesc);
        int fontReplacements = 0;
        for (AbstractInsnNode insn = constructor.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode)) continue;
            FieldInsnNode field = (FieldInsnNode) insn;
            if (field.getOpcode() == GETSTATIC && fontClass.equals(field.owner)
                    && regularGreen.equals(field.name) && fontDesc.equals(field.desc)) {
                field.name = NEW_FONT_FIELD;
                fontReplacements++;
            }
        }
        require(fontReplacements == 1,
                "expected exactly one regular-green XP font selection, found " + fontReplacements);

        InsnList scale = new InsnList();
        LabelNode notXp = new LabelNode();
        scale.add(new VarInsnNode(ALOAD, 0));
        scale.add(new FieldInsnNode(GETFIELD, popupClass, typeField, "I"));
        scale.add(new InsnNode(ICONST_1));
        scale.add(new JumpInsnNode(IF_ICMPNE, notXp));
        // Apply the additional 2/3 speed requested for the current release.
        // Combined with the previous 2/3 patch this is 4/9 of the baseline.
        scale.add(new VarInsnNode(ALOAD, 0));
        scale.add(new VarInsnNode(ALOAD, 0));
        scale.add(new FieldInsnNode(GETFIELD, popupBase, velocityYField, "I"));
        scale.add(new IntInsnNode(BIPUSH, XP_SPEED_NUMERATOR));
        scale.add(new InsnNode(IMUL));
        scale.add(new IntInsnNode(BIPUSH, XP_SPEED_DENOMINATOR));
        scale.add(new InsnNode(IDIV));
        scale.add(new FieldInsnNode(PUTFIELD, popupBase, velocityYField, "I"));
        // The age counter remains one tick per frame; apply another 1.5x to
        // the already extended lifetime (2.25x the baseline in total).
        scale.add(new VarInsnNode(ALOAD, 0));
        scale.add(new VarInsnNode(ALOAD, 0));
        scale.add(new FieldInsnNode(GETFIELD, popupClass, lifetimeField, "I"));
        scale.add(new IntInsnNode(BIPUSH, XP_LIFETIME_NUMERATOR));
        scale.add(new InsnNode(IMUL));
        scale.add(new IntInsnNode(BIPUSH, XP_LIFETIME_DENOMINATOR));
        scale.add(new InsnNode(IDIV));
        scale.add(new FieldInsnNode(PUTFIELD, popupClass, lifetimeField, "I"));
        scale.add(notXp);

        int returns = 0;
        for (AbstractInsnNode insn = constructor.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (insn.getOpcode() == RETURN) {
                constructor.instructions.insertBefore(insn, clone(scale));
                returns++;
            }
        }
        require(returns == 2, "unexpected XP popup constructor shape; returns=" + returns);

        MethodNode update = method(node, popupUpdate, updateDesc);
        update.instructions.clear();
        LabelNode normalGravity = new LabelNode();
        LabelNode phaseReset = new LabelNode();
        LabelNode afterGravity = new LabelNode();
        LabelNode end = new LabelNode();
        InsnList code = update.instructions;

        // age++
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new InsnNode(DUP));
        code.add(new FieldInsnNode(GETFIELD, popupBase, ageField, "I"));
        code.add(new InsnNode(ICONST_1));
        code.add(new InsnNode(IADD));
        code.add(new FieldInsnNode(PUTFIELD, popupBase, ageField, "I"));

        // Slow the current 2/3 acceleration cadence by another 2/3. The
        // resulting profile adds +1 on 4 of every 9 frames (~4/9 baseline),
        // avoiding a large one-frame jump while keeping integer CLDC math.
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETFIELD, popupClass, typeField, "I"));
        code.add(new InsnNode(ICONST_1));
        code.add(new JumpInsnNode(IF_ICMPNE, normalGravity));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new InsnNode(DUP));
        code.add(new FieldInsnNode(GETFIELD, popupClass, NEW_PHASE_FIELD, "I"));
        code.add(new InsnNode(ICONST_1));
        code.add(new InsnNode(IADD));
        code.add(new FieldInsnNode(PUTFIELD, popupClass, NEW_PHASE_FIELD, "I"));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETFIELD, popupClass, NEW_PHASE_FIELD, "I"));
        code.add(new InsnNode(ICONST_5));
        code.add(new JumpInsnNode(IF_ICMPGE, phaseReset));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETFIELD, popupBase, velocityYField, "I"));
        code.add(new InsnNode(ICONST_1));
        code.add(new InsnNode(IADD));
        code.add(new FieldInsnNode(PUTFIELD, popupBase, velocityYField, "I"));
        code.add(new JumpInsnNode(GOTO, afterGravity));

        // Reset the 9-frame phase after the no-gravity portion.
        code.add(phaseReset);
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETFIELD, popupClass, NEW_PHASE_FIELD, "I"));
        code.add(new IntInsnNode(BIPUSH, 9));
        code.add(new JumpInsnNode(IF_ICMPLT, afterGravity));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new InsnNode(ICONST_0));
        code.add(new FieldInsnNode(PUTFIELD, popupClass, NEW_PHASE_FIELD, "I"));
        code.add(new JumpInsnNode(GOTO, afterGravity));

        code.add(normalGravity);
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETFIELD, popupBase, velocityYField, "I"));
        code.add(new InsnNode(ICONST_1));
        code.add(new InsnNode(IADD));
        code.add(new FieldInsnNode(PUTFIELD, popupBase, velocityYField, "I"));

        code.add(afterGravity);
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new MethodInsnNode(INVOKESPECIAL, popupBase, popupUpdate, updateDesc, false));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETFIELD, popupBase, ageField, "I"));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETFIELD, popupClass, lifetimeField, "I"));
        code.add(new JumpInsnNode(IF_ICMPLT, end));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new InsnNode(ICONST_1));
        code.add(new FieldInsnNode(PUTFIELD, popupBase, aliveField, "Z"));
        code.add(end);
        code.add(new InsnNode(RETURN));
        update.maxStack = 4;
        update.maxLocals = 1;

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static InsnList clone(InsnList source) {
        // The list contains labels, so use a label map rather than reusing
        // nodes across the two constructor return sites.
        java.util.HashMap<LabelNode, LabelNode> labels =
                new java.util.HashMap<LabelNode, LabelNode>();
        InsnList result = new InsnList();
        for (AbstractInsnNode insn = source.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LabelNode) {
                labels.put((LabelNode) insn, new LabelNode());
            }
        }
        for (AbstractInsnNode insn = source.getFirst(); insn != null; insn = insn.getNext()) {
            result.add(insn.clone(labels));
        }
        return result;
    }

    private static void verifyPatchedJar(File output, String fontClass, String popupClass,
            String fontDesc, String popupBase, String popupUpdate, String updateDesc,
            String constructorDesc) throws Exception {
        JarFile jar = new JarFile(output);
        try {
            ClassNode font = readClass(readEntry(jar, fontClass + ".class"));
            ClassNode popup = readClass(readEntry(jar, popupClass + ".class"));
            FieldNode generatedFont = findField(font, NEW_FONT_FIELD, fontDesc);
            require(generatedFont != null, "generated 2x font field missing");
            require((generatedFont.access & ACC_PUBLIC) != 0
                    && (generatedFont.access & ACC_PRIVATE) == 0,
                    "generated 2x font field must be public for CLDC cross-class access");
            require(findField(popup, NEW_PHASE_FIELD, "I") != null,
                    "generated XP phase field missing");
            require(method(popup, "<init>", constructorDesc) != null,
                    "XP constructor missing after patch");
            require(method(popup, popupUpdate, updateDesc) != null,
                    "XP update method missing after patch");
            require(readEntry(jar, FONT_IMAGE_OUTPUT) != null,
                    "generated 2x PNG missing");
            require(readEntry(jar, FONT_METRICS_OUTPUT) != null,
                    "generated 2x metrics missing");
        } finally {
            jar.close();
        }
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode result = new ClassNode();
        new ClassReader(bytes).accept(result, 0);
        return result;
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        for (Object raw : node.methods) {
            MethodNode method = (MethodNode) raw;
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        throw new IllegalStateException("method not found: " + node.name + "." + name + desc);
    }

    private static FieldNode findField(ClassNode node, String name, String desc) {
        for (Object raw : node.fields) {
            FieldNode field = (FieldNode) raw;
            if (name.equals(field.name) && desc.equals(field.desc)) return field;
        }
        return null;
    }

    private static Properties load(File file) throws Exception {
        Properties result = new Properties();
        InputStream in = new FileInputStream(file);
        try {
            result.load(in);
        } finally {
            in.close();
        }
        return result;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().length() == 0) {
            throw new IllegalStateException("missing symbol: " + key);
        }
        return value.trim();
    }

    private static byte[] requiredEntry(Map<String, byte[]> entries, String name) {
        byte[] value = entries.get(name);
        if (value == null) throw new IllegalStateException("missing JAR resource: " + name);
        return value;
    }

    /*
     * Do not scale the complete 224-glyph sheet.  On a J2ME device that
     * turns a small indexed 12x2454 image into a large decoded ARGB surface
     * and can starve the network/render loop.  Packet 30 creates text in the
     * form "<integer>xp", so keep only those glyphs plus the space/signs.
     * The source is kept indexed (PNG color type 3) for the same reason.
     */
    private static FontAssets buildCompactXpFont(byte[] imageBytes, byte[] metricBytes,
            String chars) throws Exception {
        Metrics source = readMetrics(metricBytes);
        require(chars != null && chars.length() == source.values.length,
                "font character table does not match metric count");
        source.chars = chars;

        BufferedImage input = ImageIO.read(new ByteArrayInputStream(imageBytes));
        require(input != null, "cannot decode XP font PNG");
        require(input.getColorModel() instanceof IndexColorModel,
                "XP source font must remain an indexed PNG");

        int width = 1;
        int height = 0;
        int[] outputY = new int[source.values.length];
        int[] outputWidth = new int[source.values.length];
        int[] outputHeight = new int[source.values.length];
        for (int i = 0; i < source.values.length; i++) {
            char character = chars.charAt(i);
            if (!isXpCharacter(character)) continue;
            int x = source.values[i][0];
            int y = source.values[i][1];
            int glyphWidth = source.values[i][2];
            int glyphHeight = source.values[i][3];
            require(x >= 0 && y >= 0 && glyphWidth >= 0 && glyphHeight >= 0,
                    "invalid source XP glyph metric");
            require(x + glyphWidth <= input.getWidth() && y + glyphHeight <= input.getHeight(),
                    "source XP glyph metric is outside the image");
            outputY[i] = height;
            outputWidth[i] = glyphWidth * 2;
            outputHeight[i] = glyphHeight * 2;
            width = Math.max(width, outputWidth[i]);
            height += Math.max(1, outputHeight[i]);
        }
        require(height > 0, "XP font character table has no supported glyphs");

        IndexColorModel colorModel = (IndexColorModel) input.getColorModel();
        WritableRaster outputRaster = colorModel.createCompatibleWritableRaster(width, height);
        BufferedImage output = new BufferedImage(colorModel, outputRaster,
                colorModel.isAlphaPremultiplied(), null);
        Raster inputRaster = input.getRaster();
        for (int i = 0; i < source.values.length; i++) {
            if (!isXpCharacter(chars.charAt(i))) continue;
            int sourceX = source.values[i][0];
            int sourceY = source.values[i][1];
            int glyphWidth = source.values[i][2];
            int glyphHeight = source.values[i][3];
            for (int y = 0; y < glyphHeight; y++) {
                for (int x = 0; x < glyphWidth; x++) {
                    Object pixel = inputRaster.getDataElements(sourceX + x, sourceY + y, null);
                    int dx = x * 2;
                    int dy = outputY[i] + y * 2;
                    outputRaster.setDataElements(dx, dy, pixel);
                    outputRaster.setDataElements(dx + 1, dy, pixel);
                    outputRaster.setDataElements(dx, dy + 1, pixel);
                    outputRaster.setDataElements(dx + 1, dy + 1, pixel);
                }
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        require(ImageIO.write(output, "png", bytes), "cannot encode scaled XP font PNG");
        return new FontAssets(bytes.toByteArray(), writeMetrics(source, outputY,
                outputWidth, outputHeight));
    }

    private static boolean isXpCharacter(char character) {
        return character == ' ' || character == '+' || character == '-'
                || character == 'x' || character == 'p'
                || (character >= '0' && character <= '9');
    }

    private static byte[] writeMetrics(Metrics source, int[] outputY,
            int[] outputWidth, int[] outputHeight) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(source.bytes.length);
        java.io.DataOutputStream output = new java.io.DataOutputStream(raw);
        output.writeShort(source.values.length);
        for (int i = 0; i < source.values.length; i++) {
            output.writeShort(isXpCharacter(source.chars.charAt(i)) ? 0 : 0);
            output.writeShort(outputY[i]);
            output.writeShort(outputWidth[i]);
            output.writeShort(outputHeight[i]);
        }
        output.close();
        return raw.toByteArray();
    }

    private static Metrics readMetrics(byte[] source) throws Exception {
        java.io.DataInputStream input = new java.io.DataInputStream(
                new ByteArrayInputStream(source));
        int count = input.readUnsignedShort();
        int[][] values = new int[count][4];
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < 4; j++) {
                values[i][j] = input.readShort();
            }
        }
        require(input.read() < 0, "unexpected trailing font metric data");
        input.close();
        return new Metrics(source, values);
    }

    private static String staticString(ClassNode node, String fieldName) {
        MethodNode clinit = method(node, "<clinit>", "()V");
        String pending = null;
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode
                    && ((LdcInsnNode) insn).cst instanceof String) {
                pending = (String) ((LdcInsnNode) insn).cst;
                continue;
            }
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (field.getOpcode() == PUTSTATIC && fieldName.equals(field.name)) {
                    require(pending != null, "font character field is not initialized from a string");
                    return pending;
                }
            }
            if (insn.getOpcode() != -1) pending = null;
        }
        throw new IllegalStateException("font character field initializer not found: " + fieldName);
    }

    private static final class Metrics {
        final byte[] bytes;
        final int[][] values;
        String chars;

        Metrics(byte[] bytes, int[][] values) {
            this.bytes = bytes;
            this.values = values;
        }
    }

    private static final class FontAssets {
        final byte[] image;
        final byte[] metrics;

        FontAssets(byte[] image, byte[] metrics) {
            this.image = image;
            this.metrics = metrics;
        }
    }

    private static void readJar(File file, Map<String, byte[]> entries,
            Map<String, Long> times) throws Exception {
        JarFile jar = new JarFile(file);
        try {
            Enumeration<JarEntry> all = jar.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                if (entry.isDirectory()) continue;
                entries.put(entry.getName(), readAll(jar.getInputStream(entry)));
                times.put(entry.getName(), Long.valueOf(entry.getTime()));
            }
        } finally {
            jar.close();
        }
    }

    private static byte[] readEntry(JarFile jar, String name) throws Exception {
        JarEntry entry = jar.getJarEntry(name);
        if (entry == null) return null;
        return readAll(jar.getInputStream(entry));
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) >= 0) output.write(buffer, 0, n);
        input.close();
        return output.toByteArray();
    }

    private static void writeJar(File file, Map<String, byte[]> entries,
            Map<String, Long> times) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("cannot create output directory: " + parent);
        }
        JarOutputStream output = new JarOutputStream(new FileOutputStream(file));
        try {
            for (Map.Entry<String, byte[]> item : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(item.getKey());
                Long time = times.get(item.getKey());
                if (time != null && time.longValue() >= 0) entry.setTime(time.longValue());
                output.putNextEntry(entry);
                output.write(item.getValue());
                output.closeEntry();
            }
        } finally {
            output.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
