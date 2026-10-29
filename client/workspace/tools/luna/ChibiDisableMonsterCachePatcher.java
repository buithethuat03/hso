import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** CHIBI-only native-lifecycle profile; other endpoints keep their cache implementation. */
public final class ChibiDisableMonsterCachePatcher implements Opcodes {
    public static final String INPUT="8B0F9551165EA4A48BBC96C6ED82B9C6F56424113B3EDC51584B49BA28FCCE5E";
    public static final String SUPPORT="luna/features/MonsterCacheSupport";
    private static final String ACCESS="Lluna/api/MonsterCacheAccess;";
    public static Map<String,String> entries() {
        Map<String,String> methods=new LinkedHashMap<String,String>();
        for(String name:new String[]{"onMap","tick","flushNow"})methods.put(name,"("+ACCESS+")V");
        for(String name:new String[]{"onObject","onDetail"})methods.put(name,"("+ACCESS+"IIIIII)V");
        methods.put("paint","(Ljava/lang/Object;)V");
        methods.put("verifyTarget","("+ACCESS+"ILjava/lang/Object;)Z");
        for(String name:new String[]{"onCombatSnapshot","onUnseenCombat"})methods.put(name,"("+ACCESS+"Ljava/io/DataInputStream;)V");
        methods.put("onEntityUpdate","("+ACCESS+"Ljava/lang/Object;)V");
        methods.put("advanceCorpse","(Ljava/lang/Object;)Z");methods.put("hold","(Ljava/lang/Object;)Z");
        methods.put("status","()Ljava/lang/String;");methods.put("remembered","(Ljava/lang/String;I)[I");methods.put("resetForTest","()V");
        return methods;
    }
    public static void main(String[] args)throws Exception {
        check(args.length==5,"cached.jar base.properties output.jar output.properties report.txt");
        File input=new File(args[0]),output=new File(args[2]);
        check(INPUT.equals(HungBySonChildLoginPatcher.hash(input)),"Unexpected cached CHIBI input");check(!output.exists(),"Output exists");
        Map<String,byte[]> before=HungBySonChildLoginPatcher.read(input),after=new LinkedHashMap<String,byte[]>(before);
        ClassNode c=node(before.get(SUPPORT+".class"));Map<String,String> expected=entries();Set<String> replaced=new LinkedHashSet<String>();
        int initializer=0;
        for(MethodNode m:c.methods) {
            boolean entry=(m.access&(ACC_PUBLIC|ACC_STATIC))==(ACC_PUBLIC|ACC_STATIC);
            if(entry){check(expected.containsKey(m.name)&&expected.get(m.name).equals(m.desc),"Unknown public cache ABI "+m.name+m.desc);check(replaced.add(m.name),"Duplicate ABI");}
            else if(!m.name.equals("<clinit>"))continue;
            else initializer++;
            m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
            m.maxStack=0;
            if(m.name.equals("status")){m.instructions.add(new LdcInsnNode(""));m.instructions.add(new InsnNode(ARETURN));}
            else if(m.name.equals("remembered")){m.instructions.add(new InsnNode(ICONST_0));m.instructions.add(new IntInsnNode(NEWARRAY,T_INT));m.instructions.add(new InsnNode(ARETURN));}
            else if(Type.getReturnType(m.desc).getSort()==Type.BOOLEAN){m.instructions.add(new InsnNode(ICONST_0));m.instructions.add(new InsnNode(IRETURN));}
            else {check(Type.getReturnType(m.desc).getSort()==Type.VOID,"Unexpected return ABI");m.instructions.add(new InsnNode(RETURN));}
        }
        check(replaced.equals(expected.keySet())&&initializer==1,"Cache ABI/initializer count");
        ClassWriter writer=new ClassWriter(new ClassReader(before.get(SUPPORT+".class")),ClassWriter.COMPUTE_MAXS);c.accept(writer);after.put(SUPPORT+".class",writer.toByteArray());
        verifyInverse(before.get(SUPPORT+".class"),after.get(SUPPORT+".class"),expected);
        JarFile jar=new JarFile(input);JarOutputStream out=new JarOutputStream(new FileOutputStream(output));
        try{for(Enumeration<JarEntry> e=jar.entries();e.hasMoreElements();){JarEntry old=e.nextElement(),entry=new JarEntry(old.getName());entry.setTime(old.getTime());out.putNextEntry(entry);out.write(after.get(old.getName()));out.closeEntry();}}finally{out.close();jar.close();}
        Properties p=new Properties();Reader in=new InputStreamReader(new FileInputStream(args[1]),"UTF-8");try{p.load(in);}finally{in.close();}
        check(INPUT.equals(p.getProperty("output.sha256")),"Base catalog hash mismatch");
        Set<String> changed=new LinkedHashSet<String>(Arrays.asList(p.getProperty("changed.classes").split(",")));changed.add(SUPPORT);
        p.setProperty("changed.classes",HsoChildSingleServerPatcher.join(changed));p.setProperty("output.sha256",HungBySonChildLoginPatcher.hash(output));
        p.setProperty("monster.cache.enabled","false");p.setProperty("monster.cache.read.write","disabled");p.setProperty("monster.cache.restore.query","disabled");
        p.setProperty("monster.cache.corpse.retention","disabled");p.setProperty("monster.lifecycle","native");p.setProperty("monster.cache.base.sha256",INPUT);
        Writer config=new OutputStreamWriter(new FileOutputStream(args[3]),"UTF-8");try{p.store(config,"HSO CHIBI native monster lifecycle without monster cache");}finally{config.close();}
        PrintWriter report=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[4]),"UTF-8"));
        try{report.println("INPUT_SHA256="+INPUT);report.println("OUTPUT_SHA256="+p.getProperty("output.sha256"));report.println("CHANGED="+SUPPORT+".class");report.println("PUBLIC_ABI_DISABLED="+replaced.size());report.println("INITIALIZER=empty; no cache buffer allocation");report.println("INVERSE=PASS; all other members unchanged");report.println("BOUNDARY=PASS; all other class/resource/manifest bytes unchanged");report.println("NATIVE_LIFECYCLE=hold=false; advanceCorpse=false; observations/requests/restores/persistence=none");}finally{report.close();}
        System.out.println("CHIBI_NO_MONSTER_CACHE=PASS; 15 public entries disabled; native death/removal/respawn; SHA256="+p.getProperty("output.sha256"));
    }
    private static void verifyInverse(byte[] original,byte[] candidate,Map<String,String> expected){
        ClassNode old=node(original),now=node(candidate);check(old.methods.size()==now.methods.size(),"Method topology");
        for(int i=0;i<old.methods.size();i++){MethodNode a=old.methods.get(i),b=now.methods.get(i);check(a.name.equals(b.name)&&a.desc.equals(b.desc)&&a.access==b.access,"Member ABI");if(expected.containsKey(a.name)||a.name.equals("<clinit>"))now.methods.set(i,a);}
        ClassWriter a=new ClassWriter(0),b=new ClassWriter(0);old.accept(a);now.accept(b);check(Arrays.equals(a.toByteArray(),b.toByteArray()),"Inverse changed unrelated members");
    }
    private static ClassNode node(byte[] data){check(data!=null,"Cache class absent");ClassNode c=new ClassNode();new ClassReader(data).accept(c,0);return c;}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
