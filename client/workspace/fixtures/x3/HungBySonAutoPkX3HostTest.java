import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Auto PK in every X3 slot: 365px policy, painter lines (red Xà nữ / sky-blue level 40 / white),
 * Xà nữ selector and rank bridge, each bound to its own slot's classes. Behaviour is covered on the
 * x1 candidate by HungBySonAutoPkBinaryTest; the packer only renames per slot.
 */
public final class HungBySonAutoPkX3HostTest {
    static final String[] SLOTS = {"", "b/", "c/"};
    static int checks;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
        System.out.println("PASS " + what);
    }

    static ClassNode node(JarFile jar, String name) throws Exception {
        JarEntry entry = jar.getJarEntry(name + ".class");
        check(entry != null, "Class present " + name);
        InputStream in = jar.getInputStream(entry);
        try {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        } finally { in.close(); }
    }

    static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new AssertionError("Missing " + c.name + "." + name + desc);
    }

    static boolean calls(MethodNode m, String owner, String name, String desc) {
        for (AbstractInsnNode x = m.instructions.getFirst(); x != null; x = x.getNext())
            if (x instanceof MethodInsnNode) {
                MethodInsnNode c = (MethodInsnNode)x;
                if (c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(desc)) return true;
            }
        return false;
    }

    static boolean drawsLine(MethodNode m) {
        for (AbstractInsnNode x = m.instructions.getFirst(); x != null; x = x.getNext())
            if (x instanceof MethodInsnNode && ((MethodInsnNode)x).owner.endsWith("Graphics")
                    && ((MethodInsnNode)x).name.equals("drawLine") && ((MethodInsnNode)x).desc.equals("(IIII)V")) return true;
        return false;
    }

    static boolean constant(MethodNode m, Object value) {
        for (AbstractInsnNode x = m.instructions.getFirst(); x != null; x = x.getNext())
            if (x instanceof LdcInsnNode && value.equals(((LdcInsnNode)x).cst)) return true;
        return false;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 && !(args.length >= 2 && args.length <= 3 && args[1].equals("--cache-focus")
                && (args.length == 2 || args[2].equals("--selection-objects")))) throw new IllegalArgumentException("candidate-x3.jar [--cache-focus [--selection-objects]]");
        boolean cacheFocus = args.length >= 2, selectionObjects = args.length == 3;
        File candidate = new File(args[0]);
        JarFile jar = new JarFile(candidate);
        URLClassLoader loader = new URLClassLoader(new URL[]{candidate.toURI().toURL()},
                HungBySonAutoPkX3HostTest.class.getClassLoader());
        try {
            for (String p : SLOTS) {
                String slot = p.length() == 0 ? "slot 1" : "slot " + (p.charAt(0) - 'a' + 1);
                String port = p + "HungBySonModUpLv", policy = p + "luna/features/AutoPkTargetPolicy";
                String entity = null;
                ClassNode policyNode = node(jar, policy);
                Object radius = null;
                for (FieldNode f : policyNode.fields) if (f.name.equals("RADIUS")) radius = f.value;
                check(Integer.valueOf(365).equals(radius), slot + ": AutoPkTargetPolicy.RADIUS is 365");
                Class<?> policyClass = Class.forName(policy.replace('/', '.'), true, loader);
                Method distance = policyClass.getMethod("distanceSquared", Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
                check(((Long)distance.invoke(null, 0, 0, 365, 0)).longValue() == 133225L
                        && ((Long)distance.invoke(null, 0, 0, 366, 0)).longValue() == -1L
                        && ((Long)distance.invoke(null, 0, 0, 219, 292)).longValue() == 133225L,
                        slot + ": loaded policy includes 365px, excludes 366px, Euclidean border");

                ClassNode portNode = node(jar, port);
                MethodNode rank = method(portNode, "priorityRank", "(Ljava/lang/Object;)I");
                for (MethodNode m : portNode.methods)
                    if (m.name.equals("serpentTarget")) entity = entityOf(m.desc);
                check(entity != null && (p.length() == 0 ? entity.indexOf('/') < 0 : entity.startsWith(p)),
                        slot + ": Xà nữ predicate takes this slot's entity class " + entity);
                String serpentDesc = "(L" + entity + ";)Z";
                MethodNode name = method(portNode, "serpentName", serpentDesc);
                check(constant(name, "Xà nữ"), slot + ": Xà nữ name literal kept intact by the packer");
                check(calls(rank, port, "serpentName", serpentDesc), slot + ": priorityRank ranks Xà nữ through its own slot");
                MethodNode paint = null;
                for (MethodNode m : portNode.methods) if (m.name.equals("paintPkRange")) paint = m;
                check(paint != null && drawsLine(paint)
                        && calls(paint, port, "serpentTarget", serpentDesc)
                        && constant(paint, Integer.valueOf(0xFF0000)) && constant(paint, Integer.valueOf(0x00BFFF))
                        && constant(paint, Integer.valueOf(0xFFFFFF)),
                        slot + ": painter draws red Xà nữ, sky-blue level-40 and white player lines");
                if(cacheFocus){
                    check(constant(paint,Integer.valueOf(0xFFA500))
                            && calls(paint,p+"luna/features/AutoAttackRuntime","enabled","()Z")
                            && calls(paint,p+"luna/features/AutoAttackRuntime","normalMonstersActive","()Z"),
                            slot+": orange focus follows this slot's active attack path");
                    boolean ownLock=false,ownFocus=false;
                    for(AbstractInsnNode x:paint.instructions.toArray())if(x instanceof FieldInsnNode){
                        FieldInsnNode f=(FieldInsnNode)x;
                        if(f.owner.equals(port)&&f.name.equals("pkLock")&&f.desc.equals("L"+entity+";"))ownLock=true;
                        if(!f.owner.equals(port)&&f.desc.equals("L"+entity+";")&&f.getOpcode()==Opcodes.GETSTATIC)ownFocus=true;
                    }
                    check(ownLock&&ownFocus,slot+": painter reads its own special lock and native focus");
                    ClassNode cache=node(jar,p+"luna/features/MonsterCacheSupport");
                    MethodNode exclude=method(cache,"isExcludedTemplate","(L"+p+"luna/api/MonsterCacheAccess;I)Z");
                    check(constant(exclude,"Xà nữ")&&constant(exclude,"Lửa thiêng")&&constant(exclude,"con Ma"),
                            slot+": cache excludes all three names with original Vietnamese literals");
                }
                if(selectionObjects){
                    String selection=p+"luna/features/MonsterSelectionSupport";
                    ClassNode selectionNode=node(jar,selection);
                    MethodNode exclude=method(selectionNode,"excludedObjectName","(Ljava/lang/String;)Z");
                    check(constant(exclude,"Lửa thiêng")&&constant(exclude,"con Ma")
                            && calls(exclude,"java/lang/String","trim","()Ljava/lang/String;")
                            && calls(exclude,"java/lang/String","equalsIgnoreCase","(Ljava/lang/String;)Z"),
                            slot+": selection excludes exact object names with trim and case-insensitive matching");
                    for(String entry:new String[]{"addRow","addGroup"}){
                        MethodNode boundary=null;for(MethodNode m:selectionNode.methods)if(m.name.equals(entry))boundary=m;
                        check(boundary!=null&&calls(boundary,selection,"excludedObjectName","(Ljava/lang/String;)Z"),
                                slot+": "+entry+" filters this slot's live/remembered/saved object groups");
                    }
                }
                MethodNode select = null, attack = null;
                for (MethodNode m : portNode.methods) {
                    if (m.name.equals("nearestPlayers")) select = m;
                    if (m.name.equals("attackFrame")) attack = m;
                }
                check(select != null && calls(select, port, "serpentTarget", serpentDesc), slot + ": PK selector puts Xà nữ first");
                boolean flag = false;
                for (AbstractInsnNode x = attack.instructions.getFirst(); x != null; x = x.getNext())
                    if (x instanceof FieldInsnNode && ((FieldInsnNode)x).owner.equals(port) && ((FieldInsnNode)x).name.equals("pkSerpents")) flag = true;
                check(flag, slot + ": special send switches serializer on its own slot's Xà nữ flag");

                ClassNode bridge = node(jar, p + "luna/generated/HungAutoAttackAccess");
                check(bridge.interfaces.contains(p + "luna/api/AutoPkPriorityAccess")
                        && calls(method(bridge, "priorityRank", "(Ljava/lang/Object;)I"), port, "priorityRank", "(Ljava/lang/Object;)I"),
                        slot + ": generated bridge ranks through this slot's port");
                boolean stale = false;
                for (MethodNode m : bridge.methods) if (m.name.equals("priorityTarget")) stale = true;
                check(!stale, slot + ": old boolean priority bridge removed");
                ClassNode controller = node(jar, p + "luna/features/AutoAttackController");
                check(calls(method(controller, "findTarget", "()Ljava/lang/Object;"), p + "luna/api/AutoPkPriorityAccess",
                        "priorityRank", "(Ljava/lang/Object;)I"), slot + ": Special OFF controller uses integer PK ranks");
            }
        } finally {
            loader.close();
            jar.close();
        }
        System.out.println("HungBySonAutoPkX3HostTest PASS checks=" + checks);
    }

    /** Internal name of the single object parameter of "(Lname;)Z". */
    static String entityOf(String desc) {
        if (!desc.startsWith("(L") || !desc.endsWith(";)Z")) return null;
        return desc.substring(2, desc.length() - 3);
    }
}
