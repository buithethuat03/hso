import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Đập đồ messages as ModUpLv shows them (docs/HUNG_BY_SON_ENHANCE.md). ModUpLv's C133.C110(text, 3)
 * opens the native dialog with an Ok button (C091.C110) for the item-queue results and the auto's
 * stop/skip reasons; only the menu confirmations use the overhead text (C091.C178). The first port
 * sent everything to Hung's overhead text, so the character appeared to chat them. Hung's dialog is
 * ~liilllii.a(String) (same "Ok" button, -1). Only HungBySonEnhance is recompiled and replaced.
 */
public final class HungEnhancePopupPatcher implements Opcodes {
    static final String INPUT = "BC8A910363F65FB1785F7D5A2995ECB9301E3B02615C1E9CD11F9A9E5E94D9D2";
    static final String CATALOG = "build/hung-enhance-port-20261027-b/auto-pk.properties";
    static final String PORT = HungEnhancePortPatcher.PORT, DIALOG = HungEnhancePortPatcher.n("liilllii");

    static void check(boolean b, String why) { if (!b) throw new IllegalStateException(why); }

    public static void main(String[] a) throws Exception {
        check(a.length == 6, "input support micro work output catalogPrefix");
        File input = new File(a[0]), support = new File(a[1]), work = new File(a[3]), output = new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)), "Pinned enhance-port input");
        check(!output.exists(), "Fresh output"); check(work.isDirectory() || work.mkdirs(), "Work directory");
        Properties p = new Properties(); InputStream cfg = new FileInputStream(CATALOG);
        try { p.load(cfg); } finally { cfg.close(); }
        check(INPUT.equals(p.getProperty("output.sha256")) && "true".equals(p.getProperty("enhance.port")), "Pinned source catalog");
        Map<String,byte[]> before = HungBySonExpInfoPack.read(input), out = new LinkedHashMap<String,byte[]>(before);
        check(before.containsKey(PORT + ".class"), "Port present");

        byte[] port = HungEnhancePortPatcher.compilePort(work, input, support, a[2]);
        ClassNode now = HungBySonExpInfoPack.node(port), old = HungBySonExpInfoPack.node(before.get(PORT + ".class"));
        check(now.version == old.version, "Class version kept");
        // Same public surface the native hooks call.
        for (String[] m : new String[][]{{"itemMenu", "(Let;Ljava/lang/Object;)Let;"}, {"add", "(L" + HungEnhancePortPatcher.ITEM + ";)V"},
                {"showMenu", "()V"}, {"onReply", "(Ljava/lang/String;I)V"}, {"register", "()V"}})
            check(HungEnhancePortPatcher.find(now, m[0], m[1]) != null && HungEnhancePortPatcher.find(old, m[0], m[1]) != null, "Hook target " + m[0]);
        int dialogs = 0;
        for (AbstractInsnNode x : HungEnhancePortPatcher.method(now, "popup", "(Ljava/lang/String;)V").instructions)
            if (HungEnhancePortPatcher.call(x, INVOKESTATIC, DIALOG, "a", "(Ljava/lang/String;)V")) dialogs++;
        check(dialogs == 1, "popup opens the native Ok dialog");
        out.put(PORT + ".class", port);

        List<String> changed = new ArrayList<String>(); int classes = 0;
        for (String name : out.keySet()) {
            if (name.endsWith(".class")) classes++;
            if (!Arrays.equals(before.get(name), out.get(name))) changed.add(name);
        }
        check(changed.equals(Collections.singletonList(PORT + ".class")), "One intended class edit: " + changed);
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try { for (Map.Entry<String,byte[]> e : out.entrySet()) { JarEntry z = new JarEntry(e.getKey()); z.setTime(0); jar.putNextEntry(z); jar.write(e.getValue()); jar.closeEntry(); } }
        finally { jar.close(); }
        p.setProperty("input.sha256", INPUT); p.setProperty("output.sha256", HungBySonExpInfoPack.hash(output)); p.setProperty("enhance.popup", "true");
        p.setProperty("auto.pk.class.count", String.valueOf(classes)); p.setProperty("auto.pk.changed.classes", PORT + ".class");
        OutputStream cat = new FileOutputStream(a[5] + ".properties");
        try { p.store(cat, "Dap do messages use the native Ok dialog like ModUpLv"); } finally { cat.close(); }
        PrintWriter log = new PrintWriter(a[5] + "-report.txt", "UTF-8");
        try {
            log.println("PASS pinned input; " + PORT + " recompiled (class 48, CLDC screen); popup -> " + DIALOG + ".a(String)");
            log.println("CHANGED=" + PORT + ".class"); log.println("CLASSES=" + classes); log.println("SHA256=" + p.getProperty("output.sha256"));
        } finally { log.close(); }
        System.out.println("PASS enhance popup " + p.getProperty("output.sha256"));
    }
}
