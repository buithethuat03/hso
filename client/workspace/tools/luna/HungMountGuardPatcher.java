import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import javax.tools.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Auto Thú cưỡi used the first bag item named like the chosen mount (request 32) every 900 game
 * ticks while Farm ran, without reading the mount state. Each use spends a horse, so a rider burned
 * through every horse in the bag. The native mount menu only offers horses when player.ef == -1.
 *
 * update() now asks codexMountMay() before the send and calls codexMountSent() after it:
 * send only while unmounted, wait 15s of real time for the server's mount state, and switch the
 * feature off after two unconfirmed uses. Only the Auto Thú cưỡi module class changes.
 */
public final class HungMountGuardPatcher implements Opcodes {
    static final String INPUT="BF1BB40C7433733FE8416326018B4821D1526BB6BE1B5CA7B992020CE1170647";
    static final String MOUNT=HungBySonCopySpecialPatcher.n("lliiilil"),SERVICE=HungBySonCopySpecialPatcher.n("lilillll"),
            ITEM=HungBySonCopySpecialPatcher.n("llilli"),PLAYER=HungBySonCopySpecialPatcher.n("lliiil"),WORLD=HungBySonCopySpecialPatcher.n("liiliii"),
            FARM=HungBySonCopySpecialPatcher.n("lliiiiil"),MOD=HungBySonCopySpecialPatcher.n("lillllll"),
            LOCAL=HungBySonCopySpecialPatcher.n("llilliill"),ENABLED=HungBySonCopySpecialPatcher.n("lililllilll"),
            SAVE=HungBySonCopySpecialPatcher.n("lliiilli"),TOAST=HungBySonCopySpecialPatcher.n("lilli"),
            INSTANCE=HungBySonCopySpecialPatcher.n("liililli"),USE=HungBySonCopySpecialPatcher.n("llili");
    static final String HELPER="CodexMountGuard";

    static void check(boolean b,String why){if(!b)throw new IllegalStateException(why);}

    static String source(){
        return "final class "+HELPER+" {\n"+
            "    static int codexMountUnconfirmed;\n"+
            "    static long codexMountSentAt;\n"+
            "    /** Native mount menu rule: ride only while unmounted (ef == -1). */\n"+
            "    static boolean codexMountMay() {\n"+
            "        "+PLAYER+" player = "+WORLD+"."+LOCAL+";\n"+
            "        if (player == null) return false;\n"+
            "        if (player.ef != -1) { codexMountUnconfirmed = 0; codexMountSentAt = 0L; return false; }\n"+
            "        long now = System.currentTimeMillis();\n"+
            "        if (codexMountSentAt != 0L && now - codexMountSentAt >= 0L && now - codexMountSentAt < 15000L) return false;\n"+
            "        if (codexMountUnconfirmed >= 2) {\n"+
            "            codexMountUnconfirmed = 0; codexMountSentAt = 0L;\n"+
            "            "+MOUNT+"."+ENABLED+" = false;\n"+
            "            "+FARM+"."+SAVE+"();\n"+
            "            "+MOD+"."+TOAST+"(\"Auto Th\\u00fa c\\u01b0\\u1ee1i: \\u0110\\u00e3 T\\u1eaeT (server kh\\u00f4ng x\\u00e1c nh\\u1eadn l\\u00ean ng\\u1ef1a)\");\n"+
            "            return false;\n"+
            "        }\n"+
            "        return true;\n"+
            "    }\n"+
            "    static void codexMountSent() { codexMountUnconfirmed++; codexMountSentAt = System.currentTimeMillis(); }\n"+
            "}\n";
    }

    static ClassNode helper(File work,File input)throws Exception{
        File src=new File(work,HELPER+".java"),out=new File(work,"mount-helper");check(out.isDirectory()||out.mkdirs(),"Helper directory");
        Files.write(src.toPath(),source().getBytes("UTF-8"));
        JavaCompiler javac=ToolProvider.getSystemJavaCompiler();check(javac!=null,"javac (JDK) required");
        check(javac.run(null,null,null,"-encoding","UTF-8","--release","7","-nowarn","-Xlint:-options","-g:none","-cp",input.getPath(),"-d",out.getPath(),src.getPath())==0,"Helper compile");
        return HungBySonExpInfoPack.node(Files.readAllBytes(new File(out,HELPER+".class").toPath()));
    }

    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned map-reset-race input");check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work directory");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-map-reset-race-20261026-a/auto-pk.properties");
        try{p.load(cfg);}finally{cfg.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");

        ClassNode mount=HungBySonExpInfoPack.node(before.get(MOUNT+".class"));
        check(mount.interfaces.contains("IModModule"),"Auto Thu cuoi is a mod module");
        MethodNode update=HungBySonCopySpecialPatcher.method(mount,"update","()V");
        Map<String,String> unchanged=new HashMap<String,String>();
        for(MethodNode m:mount.methods)if(m!=update)unchanged.put(m.name+m.desc,HungBySonExpInfoPack.canonical(m));
        int fields=mount.fields.size();

        // invokestatic service.instance(); aload_2; getfield item.O; i2s; invokevirtual service.use(S); return
        int sends=0;
        for(AbstractInsnNode x=update.instructions.getFirst();x!=null;x=x.getNext()){
            if(!HungBySonCopySpecialPatcher.call(x,INVOKESTATIC,SERVICE,INSTANCE,"()L"+SERVICE+";"))continue;
            AbstractInsnNode item=HungBySonCopySpecialPatcher.real(x.getNext()),id=HungBySonCopySpecialPatcher.real(item.getNext()),narrow=HungBySonCopySpecialPatcher.real(id.getNext()),
                    use=HungBySonCopySpecialPatcher.real(narrow.getNext()),ret=HungBySonCopySpecialPatcher.real(use.getNext());
            check(item instanceof VarInsnNode&&item.getOpcode()==ALOAD&&id instanceof FieldInsnNode&&((FieldInsnNode)id).owner.equals(ITEM)&&((FieldInsnNode)id).name.equals("O")
                    &&narrow.getOpcode()==I2S&&HungBySonCopySpecialPatcher.call(use,INVOKEVIRTUAL,SERVICE,USE,"(S)V")&&ret.getOpcode()==RETURN,"Mount send shape");
            LabelNode allowed=new LabelNode();InsnList gate=new InsnList();
            gate.add(new MethodInsnNode(INVOKESTATIC,MOUNT,"codexMountMay","()Z",false));gate.add(new JumpInsnNode(IFNE,allowed));gate.add(new InsnNode(RETURN));gate.add(allowed);
            update.instructions.insertBefore(x,gate);
            update.instructions.insert(use,new MethodInsnNode(INVOKESTATIC,MOUNT,"codexMountSent","()V",false));
            sends++;x=ret;
        }
        check(sends==1,"Exactly one mount send in update: "+sends);

        ClassNode h=helper(work,input);
        for(FieldNode f:h.fields){check(HungBySonCopySpecialPatcher.find(mount,f.name,f.desc)==null,"New field "+f.name);f.access=ACC_PRIVATE|ACC_STATIC;mount.fields.add(f);}
        int added=0;
        for(MethodNode m:h.methods){
            if(!m.name.startsWith("codexMount"))continue;
            m.access=ACC_PUBLIC|ACC_STATIC;m.localVariables=null;
            for(Iterator<AbstractInsnNode> i=m.instructions.iterator();i.hasNext();){
                AbstractInsnNode x=i.next();
                if(x instanceof FrameNode||x instanceof LineNumberNode){i.remove();continue;}
                if(x instanceof FieldInsnNode&&((FieldInsnNode)x).owner.equals(HELPER))((FieldInsnNode)x).owner=MOUNT;
                if(x instanceof MethodInsnNode)check(!((MethodInsnNode)x).owner.equals(HELPER),"Helper is self-contained");
            }
            check(HungBySonCopySpecialPatcher.find(mount,m.name,m.desc)==null,"New method "+m.name);mount.methods.add(m);added++;
        }
        check(added==2&&mount.fields.size()==fields+2,"Two guard methods and two fields");
        for(MethodNode m:mount.methods)if(unchanged.containsKey(m.name+m.desc))check(unchanged.get(m.name+m.desc).equals(HungBySonExpInfoPack.canonical(m)),"Unchanged "+m.name);
        byte[] bytes=HungBySonCopySpecialPatcher.write(mount);
        check(HungBySonExpInfoPack.node(bytes).version==mount.version,"Class version kept");
        String pool=new String(bytes,"ISO-8859-1"),prior=new String(before.get(MOUNT+".class"),"ISO-8859-1");
        for(String ref:new String[]{"java/lang/invoke/","java/util/","java/nio/","java/time/","java/awt/","java/lang/reflect/","addSuppressed"})
            check(pool.indexOf(ref)<0||prior.indexOf(ref)>=0,"New non-CLDC reference "+ref);
        out.put(MOUNT+".class",bytes);

        int classes=0,resources=0;List<String> changed=new ArrayList<String>();
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(before.get(name),out.get(name)))changed.add(name);
        }
        check(changed.equals(Collections.singletonList(MOUNT+".class")),"One intended class edit: "+changed);
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("mount.guard","true");
        p.setProperty("auto.pk.class.count",String.valueOf(classes));p.setProperty("auto.pk.changed.classes",MOUNT+".class");
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Auto Thu cuoi rides only while unmounted");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{log.println("PASS pinned input; Auto Thu cuoi update gated by codexMountMay/codexMountSent; other module methods unchanged");log.println("CHANGED="+MOUNT+".class");log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS mount guard "+p.getProperty("output.sha256"));
    }
}
