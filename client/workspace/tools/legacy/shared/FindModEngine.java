import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Doc-only. Tim lop chua mot hang String cho truoc, in ra ten lop + so method/field.
 *
 * Dung de xac dinh lop engine cua ban mod trong mot jar la: khong the tra theo ten, vi
 * cung mot chuoi obfuscate duoc dung lai cho ca class, field va method.
 *
 *   java -cp ".;asm-9.7.jar;asm-tree-9.7.jar" FindModEngine LunaX3.jar "Auto Nuoi Pet"
 */
public final class FindModEngine {

    public static void main(String[] args) throws Exception {
        String jarPath = args[0];
        List<String> needles = new ArrayList<String>();
        for (int i = 1; i < args.length; i++) needles.add(args[i].toLowerCase());

        JarFile jf = new JarFile(jarPath);
        Enumeration<JarEntry> en = jf.entries();
        while (en.hasMoreElements()) {
            JarEntry e = en.nextElement();
            if (!e.getName().endsWith(".class")) continue;
            ClassNode cn = new ClassNode();
            InputStream is = jf.getInputStream(e);
            new ClassReader(is).accept(cn, 0);
            is.close();

            List<String> hits = new ArrayList<String>();
            for (int i = 0; i < cn.methods.size(); i++) {
                MethodNode m = (MethodNode) cn.methods.get(i);
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (!(n instanceof LdcInsnNode)) continue;
                    Object c = ((LdcInsnNode) n).cst;
                    if (!(c instanceof String)) continue;
                    String low = ((String) c).toLowerCase();
                    for (int k = 0; k < needles.size(); k++) {
                        if (low.indexOf(needles.get(k)) >= 0 && !hits.contains((String) c)) {
                            hits.add((String) c);
                        }
                    }
                }
            }
            if (hits.isEmpty()) continue;
            System.out.println("class len=" + cn.name.length() + "  pkg="
                    + (cn.name.indexOf('/') < 0 ? "(mac dinh)" : cn.name.substring(0, cn.name.indexOf('/')))
                    + "  method=" + cn.methods.size() + "  field=" + cn.fields.size());
            System.out.println("    " + cn.name);
            for (int i = 0; i < hits.size() && i < 6; i++) {
                System.out.println("      \"" + hits.get(i) + "\"");
            }
        }
        jf.close();
    }
}
