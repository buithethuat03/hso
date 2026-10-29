import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Draws the PK overlay in world camera space, before the common native screen reset. */
public final class HungAutoPkRangeHook implements Opcodes {
    private static final String PORT = "HungBySonModUpLv";

    private HungAutoPkRangeHook() { }

    private static void check(boolean condition, String why) {
        if (!condition) throw new IllegalStateException(why);
    }

    private static String n(String tail) {
        StringBuilder value = new StringBuilder();
        while (value.length() + tail.length() < 100) value.append('i');
        return value.append(tail).toString();
    }

    private static ClassNode node(byte[] bytes) {
        check(bytes != null, "Missing world-render class bytes");
        ClassNode result = new ClassNode(ASM9);
        new ClassReader(bytes).accept(result, 0);
        return result;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        MethodNode result = null;
        for (MethodNode candidate : owner.methods) {
            if (candidate.name.equals(name) && candidate.desc.equals(descriptor)) {
                check(result == null, "Duplicate method " + owner.name + "." + name + descriptor);
                result = candidate;
            }
        }
        check(result != null, "Missing method " + owner.name + "." + name + descriptor);
        return result;
    }

    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction : method.instructions.toArray())
            if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode instruction) {
        for (AbstractInsnNode next = instruction.getNext(); next != null; next = next.getNext())
            if (next.getOpcode() >= 0) return next;
        return null;
    }

    private static int targetIndex(List<AbstractInsnNode> instructions, LabelNode label) {
        AbstractInsnNode target = label;
        while (target != null && target.getOpcode() < 0) target = target.getNext();
        return target == null ? -1 : instructions.indexOf(target);
    }

    private static boolean call(AbstractInsnNode instruction, String owner, String name, String descriptor) {
        if (!(instruction instanceof MethodInsnNode)) return false;
        MethodInsnNode candidate = (MethodInsnNode)instruction;
        return candidate.getOpcode() == INVOKEVIRTUAL && candidate.owner.equals(owner)
                && candidate.name.equals(name) && candidate.desc.equals(descriptor);
    }

    private static byte[] write(ClassNode owner) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        owner.accept(writer);
        return writer.toByteArray();
    }

    /** The last two translate calls are MINIMAP positioning, not world-camera reset. */
    public static void apply(Map<String, byte[]> before, Map<String, byte[]> out, Properties catalog) {
        check(before != null && out != null && catalog != null, "Hook arguments");
        String world = n("liiliii");
        String entry = world + ".class";
        byte[] baselineBytes = before.get(entry);
        byte[] priorBytes = out.get(entry);
        if (priorBytes == null) priorBytes = baselineBytes;
        check(baselineBytes != null && priorBytes != null, "Pinned world renderer bytes");

        ClassNode baseline = node(baselineBytes);
        ClassNode current = node(priorBytes);
        check(world.equals(baseline.name) && world.equals(current.name), "World renderer owner");
        check(baseline.version == current.version, "World renderer class version");
        check(baseline.fields.size() == current.fields.size(), "World renderer fields");
        check(baseline.methods.size() == current.methods.size(), "World renderer methods");
        for (int i = 0; i < baseline.methods.size(); i++) {
            MethodNode original = baseline.methods.get(i);
            MethodNode live = current.methods.get(i);
            check(original.name.equals(live.name) && original.desc.equals(live.desc), "World method topology");
            check(HungBySonExpInfoPack.canonical(original).equals(HungBySonExpInfoPack.canonical(live)),
                    "Unexpected prior world edit " + original.name + original.desc);
        }

        // The pinned Auto Attack catalog does not carry the battlefield renderer
        // symbol. The owner is independently pinned by the native world-render
        // descriptor and battlefield catalog: n("llilll").
        String graphics = catalog.getProperty("graphics.owner");
        if (graphics == null || graphics.length() == 0) graphics = n("llilll");
        check(graphics.equals(n("llilll")), "Pinned native graphics owner");
        String renderDescriptor = "(L" + graphics + ";)V";
        MethodNode render = method(current, "a", renderDescriptor);
        check((render.access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) == 0, "World render instance method");
        String translate = n("illii");
        List<MethodInsnNode> translations = new ArrayList<MethodInsnNode>();
        int preexistingHooks = 0;
        String paintDescriptor = "(L" + graphics + ";)V";
        for (AbstractInsnNode instruction : render.instructions.toArray()) {
            if (call(instruction, graphics, translate, "(II)V")) translations.add((MethodInsnNode)instruction);
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode candidate = (MethodInsnNode)instruction;
                if (candidate.getOpcode() == INVOKESTATIC && candidate.owner.equals(PORT)
                        && candidate.name.equals("paintPkRange") && candidate.desc.equals(paintDescriptor))
                    preexistingHooks++;
            }
        }
        check(preexistingHooks == 0 || preexistingHooks == 2, "Known old overlay topology");
        // The first Auto PK release had two calls before minimap translation, after the
        // world matrix had already been cleared. Normalize those exact hook pairs away.
        int oldHooks = stripHooks(render, paintDescriptor);
        check(oldHooks == preexistingHooks, "Old overlay removed exactly");
        check(translations.size() == 4, "Pinned world/camera translation count " + translations.size());
        MethodInsnNode reset=null;
        for(AbstractInsnNode instruction:render.instructions.toArray())if(instruction instanceof MethodInsnNode){
            MethodInsnNode c=(MethodInsnNode)instruction;
            if(c.getOpcode()==INVOKESTATIC&&c.owner.equals(n("liilllii"))&&c.name.equals("a")&&c.desc.equals(paintDescriptor)){
                AbstractInsnNode load=previousReal(c),prior=load==null?null:previousReal(load);
                if(prior instanceof MethodInsnNode&&prior.getOpcode()==INVOKESTATIC&&((MethodInsnNode)prior).owner.equals(n("liiiliii"))
                        &&((MethodInsnNode)prior).name.equals("i")&&((MethodInsnNode)prior).desc.equals(paintDescriptor)){
                    check(reset==null,"Unique focus-pass world-to-screen reset");reset=c;
                }
            }
        }
        check(reset!=null,"Native world-to-screen reset");
        AbstractInsnNode resetLoad=previousReal(reset);
        check(resetLoad instanceof VarInsnNode&&resetLoad.getOpcode()==ALOAD&&((VarInsnNode)resetLoad).var==1,"Reset uses world graphics");
        AbstractInsnNode focus=previousReal(resetLoad);
        check(focus instanceof MethodInsnNode&&focus.getOpcode()==INVOKESTATIC&&((MethodInsnNode)focus).owner.equals(n("liiiliii"))
                &&((MethodInsnNode)focus).name.equals("i")&&((MethodInsnNode)focus).desc.equals(paintDescriptor),"End of world focus/entity pass");
        List<AbstractInsnNode> instructions=real(render);
        check(instructions.indexOf(translations.get(1))<instructions.indexOf(focus)
                &&instructions.indexOf(reset)<instructions.indexOf(translations.get(2)),"Camera, world focus, native reset, minimap order");
        String originalRender = HungBySonExpInfoPack.canonical(render);
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, 1));
        hook.add(new MethodInsnNode(INVOKESTATIC, PORT, "paintPkRange", paintDescriptor, false));
        render.instructions.insertBefore(resetLoad, hook);

        byte[] patchedBytes = write(current);
        ClassNode patched = node(patchedBytes);
        check(patched.version == baseline.version, "World renderer class version preserved");
        check(patched.fields.size() == baseline.fields.size()
                && patched.methods.size() == baseline.methods.size(), "World renderer ABI preserved");
        for (MethodNode original : baseline.methods) {
            MethodNode after = method(patched, original.name, original.desc);
            if (original.name.equals("a") && original.desc.equals(renderDescriptor)) continue;
            check(HungBySonExpInfoPack.canonical(original).equals(HungBySonExpInfoPack.canonical(after)),
                    "Unchanged world method " + original.name + original.desc);
        }

        MethodNode inverse = method(patched, "a", renderDescriptor);
        int removed = stripHooks(inverse,paintDescriptor);
        check(removed == 1, "One PK range call per world render");
        check(originalRender.equals(HungBySonExpInfoPack.canonical(inverse)), "World render inverse");

        out.put(entry, patchedBytes);
        catalog.setProperty("auto.pk.range.world.owner", world);
        catalog.setProperty("auto.pk.range.world.method", "a");
        catalog.setProperty("auto.pk.range.world.descriptor", renderDescriptor);
        catalog.setProperty("auto.pk.range.world.sites", "one call after native entity focus, before Main.a graphics reset");
        catalog.setProperty("auto.pk.range.center.fix", "true");
        catalog.setProperty("auto.pk.range.graphics.owner", graphics);
        catalog.setProperty("auto.pk.range.graphics.field", n("lliiilii"));
        catalog.setProperty("auto.pk.range.graphics.scale.field", "b");
        catalog.setProperty("auto.pk.range.camera.owner", n("ililillli"));
        catalog.setProperty("auto.pk.range.camera.field", n("illlliiiili"));
        catalog.setProperty("auto.pk.range.camera.xy", "a,b");
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode instruction){
        AbstractInsnNode x=instruction.getPrevious();while(x!=null&&x.getOpcode()<0)x=x.getPrevious();return x;
    }

    private static int stripHooks(MethodNode render,String descriptor){
        int count=0;
        for(AbstractInsnNode x:render.instructions.toArray())if(x instanceof MethodInsnNode){
            MethodInsnNode c=(MethodInsnNode)x;
            if(c.getOpcode()!=INVOKESTATIC||!c.owner.equals(PORT)||!c.name.equals("paintPkRange")||!c.desc.equals(descriptor))continue;
            AbstractInsnNode load=previousReal(x);
            check(load instanceof VarInsnNode&&load.getOpcode()==ALOAD&&((VarInsnNode)load).var==1,"Overlay graphics local");
            render.instructions.remove(load);render.instructions.remove(x);count++;
        }
        return count;
    }
}
