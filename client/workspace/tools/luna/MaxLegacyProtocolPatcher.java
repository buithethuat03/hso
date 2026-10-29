import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Reads MAX's legacy appearance bytes into the current native short arrays. */
public final class MaxLegacyProtocolPatcher implements Opcodes {
    static final String INPUT="F66EE6FA7DCB998704348B4FC60A04C468CA5F9FB759770180A23A602E9D4DE8";
    static final String RECEIVER="IIIlIIllIIIlIlIIlllIIlIlllIIIllIlllIlIIIllIllIlIIIlllIllIllIIIllIllIIllIIlIIllIIllIIlllIIIlllIIllIIllIIlllIIIlIIllIIlIIllIIlllIIIllIIlIllIllIllIIIllIlIIlllIIIllIIIllIIIlllIIIllIlllIlIIllIIlIlllIllIIllIIllIlllIIlIIIllIlllIlllIllIllIIl";
    static final int[] OPCODES={3,5,15};
    static final int[] METHODS={3,6,11};
    public static void main(String[] args)throws Exception{
        MaxVariantPatcher.require(args.length==5,"base.jar donor.jar output.jar input-catalog output-catalog");
        File input=new File(args[0]),donor=new File(args[1]),output=new File(args[2]);
        MaxVariantPatcher.require(INPUT.equals(MaxVariantPatcher.hash(input))&&MaxVariantPatcher.DONOR.equals(MaxVariantPatcher.hash(donor)),"Protocol source/reference changed");
        MaxVariantPatcher.require(!output.exists(),"Output already exists");
        Map<String,byte[]> before=MaxVariantPatcher.read(input),reference=MaxVariantPatcher.read(donor),after=new LinkedHashMap<String,byte[]>(before);
        Properties symbols=new Properties();InputStream sin=new FileInputStream("symbols/autotrade-symbols.properties");try{symbols.load(sin);}finally{sin.close();}
        ClassNode entity=MaxVariantPatcher.node(before.get(symbols.getProperty("class.ENTITY")+".class"));
        FieldNode appearance=entity.fields.get(160);MaxVariantPatcher.require(appearance.desc.equals("[S"),"Current appearance field shape");
        ClassNode receiver=MaxVariantPatcher.node(before.get(RECEIVER+".class")),original=MaxVariantPatcher.node(reference.get("eq.class"));
        String[] donorNames={"b","e","j"};
        for(int i=0;i<METHODS.length;i++){
            MethodNode m=receiver.methods.get(METHODS[i]);
            MaxVariantPatcher.require(m.desc.startsWith("(L")&&m.desc.endsWith(";)V")&&(m.access&ACC_STATIC)!=0,"Receiver method shape");
            FieldNode oldAppearance=MaxVariantPatcher.node(reference.get("ez.class")).fields.get(160);
            MethodNode old=MaxVariantPatcher.method(original,donorNames[i],"(Leo;)V");
            MaxVariantPatcher.require(arrayRead(old,"ez",oldAppearance.name,"[B").name.equals("readByte"),"Donor appearance reader changed");
            MethodInsnNode read=arrayRead(m,entity.name,appearance.name,"[S");
            MaxVariantPatcher.require(read.name.equals("readShort")&&read.desc.equals("()S"),"Current appearance reader changed");
            read.name="readByte";read.desc="()B";
            System.out.println("MAX_LEGACY_APPEARANCE opcode="+OPCODES[i]+"; methodIndex="+METHODS[i]+"; one byte per part; native short[] retained");
        }
        int uses=0;for(MethodNode m:receiver.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTFIELD){FieldInsnNode f=(FieldInsnNode)n;if(f.owner.equals(entity.name)&&f.name.equals(appearance.name)&&f.desc.equals("[S"))uses++;}
        MaxVariantPatcher.require(uses==3,"Unmapped appearance parser");
        ClassWriter writer=new ClassWriter(new ClassReader(before.get(RECEIVER+".class")),ClassWriter.COMPUTE_MAXS);receiver.accept(writer);after.put(RECEIVER+".class",writer.toByteArray());
        JarFile source=new JarFile(input);JarOutputStream out=new JarOutputStream(new FileOutputStream(output));try{for(Enumeration<JarEntry> e=source.entries();e.hasMoreElements();){JarEntry old=e.nextElement(),fresh=new JarEntry(old.getName());fresh.setTime(old.getTime());out.putNextEntry(fresh);out.write(after.get(old.getName()));out.closeEntry();}}finally{out.close();source.close();}
        Properties report=new Properties();Reader r=new InputStreamReader(new FileInputStream(args[3]),"UTF-8");try{report.load(r);}finally{r.close();}
        report.setProperty("protocol.base.sha256",INPUT);report.setProperty("protocol.receiver",RECEIVER);report.setProperty("protocol.appearance.owner",entity.name);report.setProperty("protocol.appearance.field",appearance.name);report.setProperty("protocol.appearance.opcodes","3,5,15");report.setProperty("protocol.appearance.wire.type","signed-byte");report.setProperty("protocol.appearance.native.type","short-array");report.setProperty("protocol.appearance.readers","3");
        report.setProperty("output.sha256",MaxVariantPatcher.hash(output));report.setProperty("changed.classes",report.getProperty("changed.classes")+","+RECEIVER);
        Writer rw=new OutputStreamWriter(new FileOutputStream(args[4]),"UTF-8");try{report.store(rw,"MAX legacy appearance packet alignment");}finally{rw.close();}
        System.out.println("MAX_LEGACY_PROTOCOL=PASS; changedClasses=1; appearanceReaders=3; SHA256="+MaxVariantPatcher.hash(output));
    }
    static MethodInsnNode arrayRead(MethodNode m,String owner,String field,String desc){
        FieldInsnNode target=null;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTFIELD){FieldInsnNode f=(FieldInsnNode)n;if(f.owner.equals(owner)&&f.name.equals(field)&&f.desc.equals(desc)){MaxVariantPatcher.require(target==null,"Multiple array assignments");target=f;}}
        MaxVariantPatcher.require(target!=null,"Missing appearance assignment");
        for(AbstractInsnNode n=target.getPrevious();n!=null;n=n.getPrevious())if(n instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)n;if(c.owner.equals("java/io/DataInputStream")&&(c.name.equals("readShort")||c.name.equals("readByte")))return c;}
        throw new IllegalStateException("Missing appearance element read");
    }
}
