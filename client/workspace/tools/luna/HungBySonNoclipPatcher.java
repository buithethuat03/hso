import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Adds a reversible collision toggle to the pinned Hung release. */
public final class HungBySonNoclipPatcher implements Opcodes {
    static final String INPUT = "97DB01AD7E5550D4B8B5D747C7D0F870CF21972F03E9ABBEAB757D2DC3AEDACC";
    static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    static byte[] read(InputStream input) throws Exception {
        try { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] block = new byte[8192];
            for (int n; (n = input.read(block)) != -1;) out.write(block, 0, n); return out.toByteArray();
        } finally { input.close(); }
    }
    static String hash(byte[] value) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(value)) out.append(String.format("%02X", b & 255));
        return out.toString();
    }
    static ClassNode node(byte[] value) { require(value != null, "Missing class"); ClassNode c = new ClassNode(ASM9); new ClassReader(value).accept(c, 0); return c; }
    static MethodNode method(ClassNode c, String name, String descriptor) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(descriptor)) return m;
        throw new IllegalStateException("Missing method " + c.name + "." + name + descriptor);
    }
    static String property(Properties p, String key) { String value = p.getProperty(key); require(value != null && value.length() > 0, "Missing " + key); return value; }
    static byte[] write(ClassNode c) { ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(w); return w.toByteArray(); }
    static AbstractInsnNode next(AbstractInsnNode i) { do { i = i.getNext(); } while (i != null && i.getOpcode() < 0); return i; }
    static boolean field(AbstractInsnNode i, int op, String owner, String name, String desc) {
        if (!(i instanceof FieldInsnNode) || i.getOpcode() != op) return false;
        FieldInsnNode f = (FieldInsnNode)i; return f.owner.equals(owner) && f.name.equals(name) && f.desc.equals(desc);
    }
    static void addHook(MethodNode m, AbstractInsnNode i, String menu) {
        m.instructions.insert(i, new MethodInsnNode(INVOKESTATIC, menu, "codexNoclipTile", "(I)I", false));
    }
    public static void main(String[] args) throws Exception {
        require(args.length == 5, "input.jar tempo.properties output.jar symbols.properties report.txt");
        File input = new File(args[0]), output = new File(args[2]);
        require(hash(read(new FileInputStream(input))).equals(INPUT), "Unexpected input SHA-256");
        require(!output.exists(), "Output must be new");
        Properties p = new Properties(); InputStream properties = new FileInputStream(args[1]);
        try { p.load(properties); } finally { properties.close(); }
        String menuOwner = property(p, "candidate.menu.owner");
        String worldOwner = null, pathName = null;
        LinkedHashMap<String, byte[]> data = new LinkedHashMap<String, byte[]>();
        JarFile jar = new JarFile(input);
        try {
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) { JarEntry entry = e.nextElement(); data.put(entry.getName(), read(jar.getInputStream(entry))); }
            ClassNode map = node(data.get("cs.class")), menu = node(data.get(menuOwner + ".class"));
            MethodNode tile = method(map, "a", "(II)I");
            int tileHooks = 0, boundaryReturns = 0;
            for (AbstractInsnNode i : tile.instructions.toArray()) {
                if (i.getOpcode() == IRETURN) boundaryReturns++;
                if (field(i, GETFIELD, "cs", "n", "[I")) {
                    AbstractInsnNode local = next(i), load = next(local);
                    require(local instanceof VarInsnNode && local.getOpcode() == ILOAD && load.getOpcode() == IALOAD,
                            "Tile query collision-array shape changed");
                    require(next(load).getOpcode() == IRETURN, "Tile query must preserve boundary return");
                    addHook(tile, load, menuOwner); tileHooks++;
                }
            }
            require(tileHooks == 1 && boundaryReturns == 2, "Expected one array lookup and separate boundary return");
            ClassNode world = null; MethodNode path = null; int found = 0;
            for (Map.Entry<String, byte[]> e : data.entrySet()) if (e.getKey().endsWith(".class")) {
                ClassNode c = node(e.getValue());
                for (MethodNode m : c.methods) if (m.desc.equals("(IIIII)[S")) {
                    int rawReads = 0;
                    for (AbstractInsnNode i : m.instructions.toArray()) if (field(i, GETFIELD, "cs", "n", "[I")) rawReads++;
                    if (rawReads == 3) { world = c; path = m; found++; }
                }
            }
            require(found == 1 && world != null, "Unique native collision pathfinder not found");
            worldOwner = world.name; pathName = path.name; int pathHooks = 0;
            for (AbstractInsnNode i : path.instructions.toArray()) if (field(i, GETFIELD, "cs", "n", "[I")) {
                AbstractInsnNode local = next(i);
                if (local.getOpcode() == ARRAYLENGTH) continue;
                require(local instanceof VarInsnNode && local.getOpcode() == ILOAD, "Path collision index changed");
                AbstractInsnNode load = next(local); require(load.getOpcode() == IALOAD, "Path collision load changed");
                AbstractInsnNode value = next(load), jump = next(value);
                require((value.getOpcode() == ICONST_1 && jump.getOpcode() == IF_ICMPEQ)
                        || (value.getOpcode() == ICONST_M1 && jump.getOpcode() == IF_ICMPNE), "Path wall test changed");
                addHook(path, load, menuOwner); pathHooks++;
            }
            require(pathHooks == 2, "Expected both native path collision reads");
            HungBySonNoclipMenuPatch.patch(menu, p);
            data.put("cs.class", write(map)); data.put(worldOwner + ".class", write(world)); data.put(menuOwner + ".class", write(menu));
            output.getAbsoluteFile().getParentFile().mkdirs();
            JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
            try { for (Map.Entry<String, byte[]> e : data.entrySet()) {
                JarEntry entry = new JarEntry(e.getKey()); entry.setTime(jar.getJarEntry(e.getKey()).getTime());
                out.putNextEntry(entry); out.write(e.getValue()); out.closeEntry();
            } } finally { out.close(); }
            p.setProperty("noclip.input.sha256", INPUT); p.setProperty("noclip.output.sha256", hash(read(new FileInputStream(output))));
            // Retain the tempo regression's input provenance, but validate clicks against these output bytes.
            p.setProperty("output.sha256", p.getProperty("noclip.output.sha256")); p.setProperty("validation.inputsha", INPUT);
            p.setProperty("noclip.menu.owner", menuOwner); p.setProperty("noclip.state.field", "codexNoclipEnabled");
            p.setProperty("noclip.state.descriptor", "Z"); p.setProperty("noclip.default", "false"); p.setProperty("noclip.persisted", "false");
            p.setProperty("noclip.tile.owner", "cs"); p.setProperty("noclip.tile.method", "a"); p.setProperty("noclip.tile.descriptor", "(II)I");
            p.setProperty("noclip.path.owner", worldOwner); p.setProperty("noclip.path.method", pathName); p.setProperty("noclip.path.descriptor", "(IIIII)[S");
            p.setProperty("noclip.changed.classes", "cs," + worldOwner + "," + menuOwner);
            OutputStream catalog = new FileOutputStream(args[3]); try { p.store(catalog, "Hung By Son Noclip OFF by default"); } finally { catalog.close(); }
            PrintWriter report = new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[4]), "UTF-8"));
            try {
                report.println("INPUT_SHA256=" + INPUT); report.println("OUTPUT_SHA256=" + p.getProperty("noclip.output.sha256"));
                report.println("DEFAULT=OFF; PERSISTENCE=none; RAW_COLLISION_ARRAY=unchanged; MAP_BOUNDARIES=native");
                report.println("TILE_HOOKS=" + tileHooks); report.println("PATH_HOOKS=" + pathHooks);
                report.println("CHANGED_CLASSES=" + p.getProperty("noclip.changed.classes"));
                report.println("LIVE_SERVER=not exercised");
            } finally { report.close(); }
            System.out.println("NOCLIP_PATCH=BUILT " + output);
        } finally { jar.close(); }
    }
}
