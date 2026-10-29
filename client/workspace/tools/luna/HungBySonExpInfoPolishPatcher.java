import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Incremental x1 icon/format/modal repair; leaves existing HUD and EXP arithmetic intact. */
public final class HungBySonExpInfoPolishPatcher implements Opcodes {
    static final String INPUT="1842971A980464F294152E9AE1903A828B681BE8ED54757A1B1DF6F510135704";
    static final String UI="luna/generated/ExpInfoBridge",RATE="luna/features/ExpRateSupport",ICON="luna/ui/ExpInfoIcon";
    static final String ART="luna/ui/exp-info.png";
    static String n(String tail){StringBuilder s=new StringBuilder();while(s.length()+tail.length()<100)s.append('i');return s.append(tail).toString();}
    static final String GRAPHICS=n("llilll"),IMAGE=n("lilli");
    static void check(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    static MethodNode method(ClassNode c,String name,String desc){for(MethodNode m:c.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;throw new IllegalStateException("Missing "+c.name+"."+name+desc);}
    static byte[] write(ClassNode c,byte[] before){ClassWriter w=new ClassWriter(new ClassReader(before),ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();}
    static void get(MethodVisitor m,String owner,String field,String desc){m.visitFieldInsn(GETSTATIC,owner,field,desc);}
    static void put(MethodVisitor m,String field,String desc){m.visitFieldInsn(PUTSTATIC,UI,field,desc);}
    static void number(MethodVisitor m,int i){m.visitLdcInsn(Integer.valueOf(i));}
    static void patchIcon(Map<String,byte[]> entries,Properties p){
        byte[] before=entries.get(UI+".class");ClassNode c=HungBySonExpInfoPack.node(before);
        check(c.fields.size()==5,"Expected original icon state fields");
        c.fields.add(new FieldNode(ACC_PRIVATE|ACC_STATIC,"icon","L"+IMAGE+";",null,null));
        c.fields.add(new FieldNode(ACC_PRIVATE|ACC_STATIC,"iconScale","I",null,null));
        String desc="(L"+GRAPHICS+";L"+n("lilillil")+";II)V";
        MethodNode old=method(c,"draw",desc),m=new MethodNode(ACC_PUBLIC|ACC_STATIC,"draw",desc,null,null);
        m.visitCode();m.visitInsn(ICONST_0);put(m,"painted","Z");
        m.visitMethodInsn(INVOKESTATIC,UI,"ready","()Z",false);Label done=new Label();m.visitJumpInsn(IFEQ,done);
        // The A status column is absolute; the panel renderer has its own origin.
        get(m,n("liilllii"),n("liliiliil"),"Z");Label normal=new Label(),position=new Label();m.visitJumpInsn(IFEQ,normal);
        number(m,84);number(m,44);m.visitJumpInsn(GOTO,position);m.visitLabel(normal);number(m,96);number(m,45);
        m.visitLabel(position);put(m,"y","I");put(m,"x","I");
        Label load=new Label(),loaded=new Label();get(m,UI,"icon","L"+IMAGE+";");m.visitJumpInsn(IFNULL,load);
        get(m,UI,"iconScale","I");get(m,GRAPHICS,"b","I");m.visitJumpInsn(IF_ICMPEQ,loaded);
        m.visitLabel(load);m.visitTypeInsn(NEW,IMAGE);m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,IMAGE,"<init>","()V",false);
        m.visitInsn(DUP);get(m,GRAPHICS,"b","I");m.visitMethodInsn(INVOKESTATIC,ICON,"image","(I)Ljavax/microedition/lcdui/Image;",false);
        m.visitFieldInsn(PUTFIELD,IMAGE,n("llliiil"),"Ljavax/microedition/lcdui/Image;");put(m,"icon","L"+IMAGE+";");get(m,GRAPHICS,"b","I");put(m,"iconScale","I");
        m.visitLabel(loaded);get(m,UI,"icon","L"+IMAGE+";");m.visitFieldInsn(GETFIELD,IMAGE,n("llliiil"),"Ljavax/microedition/lcdui/Image;");m.visitJumpInsn(IFNULL,done);
        m.visitVarInsn(ALOAD,0);get(m,UI,"icon","L"+IMAGE+";");get(m,UI,"x","I");get(m,UI,"y","I");m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKEVIRTUAL,GRAPHICS,n(""),"(L"+IMAGE+";III)V",false);m.visitInsn(ICONST_1);put(m,"painted","Z");
        m.visitLabel(done);m.visitInsn(RETURN);m.visitMaxs(8,4);m.visitEnd();c.methods.set(c.methods.indexOf(old),m);
        int bounds=0;MethodNode hit=method(c,"hit","(II)Z");
        for(AbstractInsnNode ins:hit.instructions.toArray())if(ins instanceof LdcInsnNode&&Integer.valueOf(12).equals(((LdcInsnNode)ins).cst)){((LdcInsnNode)ins).cst=Integer.valueOf(14);bounds++;}
        check(bounds==2,"Expected two 12px hitbox limits");
        for(MethodNode unchanged:c.methods)if(unchanged!=m&&unchanged!=hit){MethodNode original=method(HungBySonExpInfoPack.node(before),unchanged.name,unchanged.desc);check(HungBySonExpInfoPack.canonical(original).equals(HungBySonExpInfoPack.canonical(unchanged)),"Unapproved icon method change");}
        entries.put(UI+".class",write(c,before));p.setProperty("icon.bounds.normal","96,45,14,14");p.setProperty("icon.bounds.compact","84,44,14,14");
        p.setProperty("icon.size","14");p.setProperty("icon.resource",ART);p.setProperty("icon.position.absolute","true");p.setProperty("icon.art.custom","true");
    }
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input.jar compiledSupportDir final.jar baseCatalog outputCatalog report");
        File input=new File(a[0]),compiled=new File(a[1]),out=new File(a[2]),catalogIn=new File(a[3]),catalogOut=new File(a[4]),report=new File(a[5]);
        check(!out.exists()&&!catalogOut.exists()&&!report.exists(),"Fresh output paths required");check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Unexpected x1 source");
        Properties p=new Properties();InputStream pin=new FileInputStream(catalogIn);try{p.load(pin);}finally{pin.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Catalog input hash mismatch");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),entries=new LinkedHashMap<String,byte[]>(before);
        byte[] newRate=Files.readAllBytes(new File(compiled,RATE+".class").toPath());
        HungBySonExpInfoPack.verifyClass(before.get(RATE+".class"),newRate,
            new HashSet<String>(Arrays.asList("formatLong(J)Ljava/lang/String;","formatRate(J)Ljava/lang/String;","formatTotal(J)Ljava/lang/String;")),
            Collections.singleton("formatWholeNumber(J)Ljava/lang/String;"));
        HungBySonExpInfoPack.screen(RATE,newRate);entries.put(RATE+".class",newRate);
        byte[] helper=Files.readAllBytes(new File(compiled,ICON+".class").toPath());HungBySonExpInfoPack.screen(ICON,helper);entries.put(ICON+".class",helper);
        byte[] art=Files.readAllBytes(new File("src/luna/ui/icons/exp-info.png").toPath());BufferedImage sprite=ImageIO.read(new ByteArrayInputStream(art));check(sprite.getWidth()==14&&sprite.getHeight()==14,"Custom badge dimensions");entries.put(ART,art);
        patchIcon(entries,p);
        HungBySonExpInfoModalGuard.patch(entries,p);
        Set<String> allowed=new LinkedHashSet<String>(Arrays.asList(UI+".class",RATE+".class"));
        String guard=p.getProperty("modal.guard.changed.classes","");for(String name:guard.split(","))if(name.length()>0)allowed.add(name);
        Set<String> added=new LinkedHashSet<String>(Arrays.asList(ICON+".class",ART));
        for(String name:p.getProperty("modal.guard.added.classes","").split(","))if(name.length()>0)added.add(name);
        Set<String> changed=new LinkedHashSet<String>();
        for(String name:before.keySet()){
            check(entries.containsKey(name),"Entry removed "+name);
            if(!Arrays.equals(before.get(name),entries.get(name))){check(allowed.contains(name),"Unapproved payload change "+name);changed.add(name);}
            if(name.endsWith(".class"))check(HungBySonExpInfoPack.node(before.get(name)).version==HungBySonExpInfoPack.node(entries.get(name)).version,"Class version changed "+name);
        }
        for(String name:entries.keySet())if(!before.containsKey(name)){check(added.contains(name),"Unapproved addition "+name);if(name.endsWith(".class"))HungBySonExpInfoPack.screen(name,entries.get(name));}
        check(changed.contains(UI+".class")&&changed.contains(RATE+".class"),"Requested icon and grouped numbers did not change");
        check(Arrays.equals(before.get(p.getProperty("slot.0.hud.owner")+".class"),entries.get(p.getProperty("slot.0.hud.owner")+".class")),"Native HUD changed");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(out));try{for(Map.Entry<String,byte[]>e:entries.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0L);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        Map<String,byte[]> checkData=HungBySonExpInfoPack.read(out);check(entries.keySet().equals(checkData.keySet()),"Archive topology");for(String name:entries.keySet())check(Arrays.equals(entries.get(name),checkData.get(name)),"Archive roundtrip "+name);
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(out));p.setProperty("changed.classes",String.join(",",changed));p.setProperty("added.classes",String.join(",",added));p.setProperty("number.grouping",".");
        OutputStream po=new FileOutputStream(catalogOut);try{p.store(po,"Verified custom info icon, grouped EXP and pointer-only confirmation");}finally{po.close();}
        PrintWriter r=new PrintWriter(new OutputStreamWriter(new FileOutputStream(report),"UTF-8"));r.println("EXP_INFO_POLISH=PASS; pinned source, narrow archive allow-list, unchanged HUD/resources, canonical EXP arithmetic");r.println("CHANGED="+changed);r.println("ADDED="+added);r.println("NATIVE_HUD_BYTE_IDENTICAL=true");r.println("SHA256="+p.getProperty("output.sha256"));r.close();
        System.out.println("EXP_INFO_POLISH=PASS SHA256="+p.getProperty("output.sha256"));
    }
}
