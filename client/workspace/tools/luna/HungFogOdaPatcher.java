import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import javax.tools.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Tự động vào Làng Phủ Sương reached Mrs Oda on map 1 and then nothing happened: the port kept
 * ModUpLv's menu row 16, but Mrs Oda's menu on this server has 16 rows and "Làng phủ sương" is the
 * last one (0-based row 15, same numbering as the Phó chỉ huy row 0). The server ignored row 16.
 *
 * fogTick now sends op -30 (-81, 0, 15) like the Phó chỉ huy entry, and leaves Mrs Oda alone while
 * an Auto bán rác trip is in progress (sale ON, Farm ON, sale state != 0). Otherwise the fog walk and
 * the -30 send would teleport the player to map 135 before selling, and the sale would abandon its
 * trip without restoring Farm. Serena (map 135) is unchanged. Only HungBySonModUpLv changes.
 */
public final class HungFogOdaPatcher implements Opcodes {
    static final String INPUT="4A83A53621FAFE241A34C1A396C52D5FF7F36294BC3DF19D3366985C905730D6";
    static final String PORT="HungBySonModUpLv",SALE=HungBySonCopySpecialPatcher.n("lliiilll"),FARM=HungBySonCopySpecialPatcher.n("lliiiiil"),
            SALE_ENABLED=HungBySonCopySpecialPatcher.n("lilliiiiill"),SALE_STATE=HungBySonCopySpecialPatcher.n("lilliiiilil"),
            FARM_ENABLED=HungBySonCopySpecialPatcher.n("lililiilill");
    static final int ODA=-81,OLD_ROW=16,ROW=15;
    static final String HELPER="CodexFogOda";

    static void check(boolean b,String why){if(!b)throw new IllegalStateException(why);}

    static String source(){
        return "final class "+HELPER+" {\n"+
            "    /** An Auto bán rác trip owns the village walk until it restores Farm (state back to 0). */\n"+
            "    static boolean codexFogSaleBusy() {\n"+
            "        return "+SALE+"."+SALE_ENABLED+" && "+FARM+"."+FARM_ENABLED+" && "+SALE+"."+SALE_STATE+" != 0;\n"+
            "    }\n"+
            "}\n";
    }

    static ClassNode helper(File work,File input)throws Exception{
        File src=new File(work,HELPER+".java"),out=new File(work,"fog-helper");check(out.isDirectory()||out.mkdirs(),"Helper directory");
        Files.write(src.toPath(),source().getBytes("UTF-8"));
        JavaCompiler javac=ToolProvider.getSystemJavaCompiler();check(javac!=null,"javac (JDK) required");
        check(javac.run(null,null,null,"-encoding","UTF-8","--release","7","-nowarn","-Xlint:-options","-g:none","-cp",input.getPath(),"-d",out.getPath(),src.getPath())==0,"Helper compile");
        return HungBySonExpInfoPack.node(Files.readAllBytes(new File(out,HELPER+".class").toPath()));
    }

    static boolean push(AbstractInsnNode x,int value){
        if(x==null)return false;
        int op=x.getOpcode();
        if(op>=ICONST_M1&&op<=ICONST_5)return op-ICONST_0==value;
        return (op==BIPUSH||op==SIPUSH)&&((IntInsnNode)x).operand==value;
    }

    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned mount-guard input");check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work directory");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-mount-guard-20261026-a/auto-pk.properties");
        try{p.load(cfg);}finally{cfg.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");

        ClassNode port=HungBySonExpInfoPack.node(before.get(PORT+".class"));
        check(port.interfaces.contains("IModModule"),"ModUpLv port is a mod module");
        MethodNode fog=HungBySonCopySpecialPatcher.method(port,"fogTick","(L"+HungBySonCopySpecialPatcher.n("lliiil")+";I)V");
        Map<String,String> unchanged=new HashMap<String,String>();
        for(MethodNode m:port.methods)if(m!=fog)unchanged.put(m.name+m.desc,HungBySonExpInfoPack.canonical(m));
        int fields=port.fields.size();

        // aload_0; ldc "Mrs Oda"; invokestatic nearestNpc  ->  gate before it
        int gates=0,rows=0,serena=0;
        for(AbstractInsnNode x=fog.instructions.getFirst();x!=null;x=x.getNext()){
            if(x instanceof LdcInsnNode&&"Mrs Oda".equals(((LdcInsnNode)x).cst)){
                AbstractInsnNode self=x.getPrevious();while(self!=null&&self.getOpcode()<0)self=self.getPrevious();
                check(self instanceof VarInsnNode&&self.getOpcode()==ALOAD&&((VarInsnNode)self).var==0,"Mrs Oda lookup shape");
                AbstractInsnNode find=HungBySonCopySpecialPatcher.real(x.getNext());
                check(find instanceof MethodInsnNode&&((MethodInsnNode)find).name.equals("nearestNpc"),"Mrs Oda nearestNpc");
                LabelNode free=new LabelNode();InsnList gate=new InsnList();
                gate.add(new MethodInsnNode(INVOKESTATIC,PORT,"codexFogSaleBusy","()Z",false));gate.add(new JumpInsnNode(IFEQ,free));gate.add(new InsnNode(RETURN));gate.add(free);
                fog.instructions.insertBefore(self,gate);gates++;
            }
            if(x instanceof MethodInsnNode&&((MethodInsnNode)x).owner.equals(PORT)&&((MethodInsnNode)x).name.equals("npc")&&((MethodInsnNode)x).desc.equals("(III)V")){
                AbstractInsnNode row=x.getPrevious();while(row.getOpcode()<0)row=row.getPrevious();
                AbstractInsnNode menu=row.getPrevious();while(menu.getOpcode()<0)menu=menu.getPrevious();
                AbstractInsnNode id=menu.getPrevious();while(id.getOpcode()<0)id=id.getPrevious();
                check(push(menu,0),"NPC menu id 0");
                if(push(id,ODA)){
                    check(push(row,OLD_ROW),"Mrs Oda row 16 in input");
                    fog.instructions.set(row,new IntInsnNode(BIPUSH,ROW));rows++;
                }else{
                    check(push(id,-104)&&push(row,1),"Only Serena besides Mrs Oda");serena++;
                }
            }
        }
        check(gates==1&&rows==1&&serena==1,"One Mrs Oda gate, one Mrs Oda row, Serena untouched: "+gates+"/"+rows+"/"+serena);

        ClassNode h=helper(work,input);
        int added=0;
        for(MethodNode m:h.methods){
            if(!m.name.startsWith("codexFog"))continue;
            m.access=ACC_PUBLIC|ACC_STATIC;m.localVariables=null;
            for(Iterator<AbstractInsnNode> i=m.instructions.iterator();i.hasNext();){
                AbstractInsnNode x=i.next();
                if(x instanceof FrameNode||x instanceof LineNumberNode){i.remove();continue;}
                if(x instanceof FieldInsnNode)check(((FieldInsnNode)x).owner.equals(SALE)||((FieldInsnNode)x).owner.equals(FARM),"Helper reads only sale/Farm state");
                if(x instanceof MethodInsnNode)check(!((MethodInsnNode)x).owner.equals(HELPER),"Helper is self-contained");
            }
            check(HungBySonCopySpecialPatcher.find(port,m.name,m.desc)==null,"New method "+m.name);port.methods.add(m);added++;
        }
        check(added==1&&port.fields.size()==fields,"One gate method, no new fields");
        for(MethodNode m:port.methods)if(unchanged.containsKey(m.name+m.desc))check(unchanged.get(m.name+m.desc).equals(HungBySonExpInfoPack.canonical(m)),"Unchanged "+m.name);
        byte[] bytes=HungBySonCopySpecialPatcher.write(port);
        check(HungBySonExpInfoPack.node(bytes).version==port.version,"Class version kept");
        String pool=new String(bytes,"ISO-8859-1"),prior=new String(before.get(PORT+".class"),"ISO-8859-1");
        for(String ref:new String[]{"java/lang/invoke/","java/util/","java/nio/","java/time/","java/awt/","java/lang/reflect/","addSuppressed"})
            check(pool.indexOf(ref)<0||prior.indexOf(ref)>=0,"New non-CLDC reference "+ref);
        out.put(PORT+".class",bytes);

        int classes=0,resources=0;List<String> changed=new ArrayList<String>();
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(before.get(name),out.get(name)))changed.add(name);
        }
        check(changed.equals(Collections.singletonList(PORT+".class")),"One intended class edit: "+changed);
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("fog.oda","true");
        p.setProperty("auto.pk.class.count",String.valueOf(classes));p.setProperty("auto.pk.changed.classes",PORT+".class");
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Mrs Oda row 15, waits for Auto ban rac");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{log.println("PASS pinned input; fogTick Mrs Oda row 16->15 and gated by codexFogSaleBusy; Serena and other port methods unchanged");log.println("CHANGED="+PORT+".class");log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS fog oda "+p.getProperty("output.sha256"));
    }
}
