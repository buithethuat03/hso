import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Auto PK Battlefield priority Xà nữ > level 40 > others. Port: the PK selector, mode gate, painter and
 * special send switch to the opcode-9 monster serializer for a Xà nữ list. Controller/API: integer PK
 * ranks for the Special OFF path. Bridge: priorityTarget(Z) becomes priorityRank(I).
 */
public final class HungAutoPkSerpentPatcher {
    static final String INPUT="A698173091FE7424B86652B59641551869F676AAD5B103AE2E512C38F4B6D3EB";
    static final String PORT="HungBySonModUpLv",CTRL="luna/features/AutoAttackController",API="luna/api/AutoPkPriorityAccess",
            BRIDGE="luna/generated/HungAutoAttackAccess";
    static final List<String> REPLACED=Arrays.asList("attackFrame","validPkMode","paintPkRange","nearestPlayers");
    static final List<String> ADDED=Arrays.asList("priorityRank","serpentName","serpentTarget");
    static void check(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static ClassNode node(Map<String,byte[]> map,String name){return HungBySonExpInfoPack.node(map.get(name+".class"));}
    static ClassNode support(File dir,String name)throws IOException{return HungBySonExpInfoPack.node(Files.readAllBytes(new File(dir,name+".class").toPath()));}
    static MethodNode find(ClassNode c,String name){
        MethodNode found=null;for(MethodNode m:c.methods)if(m.name.equals(name)){check(found==null,"Unique "+c.name+"."+name);found=m;}
        return found;
    }
    static String canon(MethodNode m){return HungBySonExpInfoPack.canonical(m);}
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),support=new File(a[1]),work=new File(a[3]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned lines365 input");check(!output.exists(),"Fresh output");check(work.isDirectory()||work.mkdirs(),"Work directory");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-auto-pk-lines365-20261026-a/auto-pk.properties");
        try{p.load(cfg);}finally{cfg.close();}check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");
        StringBuilder report=new StringBuilder();

        // Port: transplant the intended methods from the fresh template compile; every other method must already match it.
        Map<String,byte[]> compiled=HungBySonCopySpecialPatcher.compile(new File[]{new File("tools/luna/HungBySonModUpLv.java.in"),new File("tools/luna/HungBySonSkillStats.java.in")},work,input,support,a[2],false);
        ClassNode port=node(before,PORT),fresh=HungBySonExpInfoPack.node(compiled.get(PORT+".class"));
        int same=0;
        for(MethodNode m:port.methods){
            if(REPLACED.contains(m.name)||m.name.equals("priorityPlayer")||m.name.equals("sendPlayers"))continue;
            MethodNode f=HungBySonCopySpecialPatcher.method(fresh,m.name,m.desc);
            check(canon(m).equals(canon(f)),"Template already matches released port method "+m.name+m.desc);same++;
        }
        check(find(port,"priorityPlayer")!=null&&find(fresh,"priorityPlayer")==null,"priorityPlayer is replaced by priorityRank");
        port.methods.remove(find(port,"priorityPlayer"));
        for(String name:REPLACED){MethodNode old=find(port,name),now=find(fresh,name);check(old!=null&&now!=null&&old.desc.equals(now.desc),"Replace "+name);port.methods.set(port.methods.indexOf(old),now);}
        for(String name:ADDED){check(find(port,name)==null,"New "+name);port.methods.add(find(fresh,name));}
        FieldNode flag=null;for(FieldNode f:fresh.fields)if(f.name.equals("pkSerpents"))flag=f;
        check(flag!=null&&flag.desc.equals("Z")&&(flag.access&Opcodes.ACC_STATIC)!=0,"pkSerpents flag");
        for(FieldNode f:port.fields)check(!f.name.equals("pkSerpents"),"pkSerpents is new");port.fields.add(flag);
        check(port.fields.size()==fresh.fields.size(),"Port fields match the template");
        MethodNode attack=find(port,"attackFrame"),paint=find(port,"paintPkRange"),select=find(port,"nearestPlayers");
        check(HungAutoPkLinesPatcher.calls(paint,"drawLine","(IIII)V")&&HungAutoPkLinesPatcher.calls(paint,"serpentTarget","(L"+HungBySonCopySpecialPatcher.n("liiiliii")+";)Z"),"Painter draws Xà nữ lines");
        check(HungAutoPkLinesPatcher.calls(select,"serpentTarget","(L"+HungBySonCopySpecialPatcher.n("liiiliii")+";)Z")&&HungAutoPkLinesPatcher.calls(attack,"sendPlayers","(Let;I)V"),"Selector and send split");
        out.put(PORT+".class",HungBySonCopySpecialPatcher.write(port));
        report.append("PORT unchanged methods verified against template: ").append(same).append('\n');

        // Controller: only findTarget changes; the rest of the released class must equal the support compile.
        ClassNode ctrl=node(before,CTRL),ctrlNew=support(support,CTRL);
        check(ctrl.methods.size()==ctrlNew.methods.size()&&ctrl.fields.size()==ctrlNew.fields.size(),"Controller topology");
        for(MethodNode m:ctrl.methods){
            MethodNode f=HungBySonCopySpecialPatcher.method(ctrlNew,m.name,m.desc);
            if(m.name.equals("findTarget"))continue;
            check(canon(m).equals(canon(f)),"Released controller matches source "+m.name+m.desc);
        }
        MethodNode findOld=HungBySonCopySpecialPatcher.method(ctrl,"findTarget","()Ljava/lang/Object;"),findNew=HungBySonCopySpecialPatcher.method(ctrlNew,"findTarget","()Ljava/lang/Object;");
        check(HungAutoPkLinesPatcher.calls(findOld,"priorityTarget","(Ljava/lang/Object;)Z")&&HungAutoPkLinesPatcher.calls(findNew,"priorityRank","(Ljava/lang/Object;)I"),"Controller ranks PK targets");
        ctrl.methods.set(ctrl.methods.indexOf(findOld),findNew);out.put(CTRL+".class",HungBySonCopySpecialPatcher.write(ctrl));

        ClassNode api=support(support,API);api.version=node(before,API).version;
        check(api.methods.size()==1&&api.methods.get(0).name.equals("priorityRank")&&api.methods.get(0).desc.equals("(Ljava/lang/Object;)I"),"API is one rank method");
        out.put(API+".class",HungBySonCopySpecialPatcher.write(api));

        ClassNode bridge=node(before,BRIDGE);check(bridge.interfaces.contains(API),"Bridge implements the PK API");
        MethodNode oldPriority=HungBySonCopySpecialPatcher.method(bridge,"priorityTarget","(Ljava/lang/Object;)Z");bridge.methods.remove(oldPriority);
        check(find(bridge,"priorityRank")==null,"Bridge rank is new");
        MethodNode rank=new MethodNode(Opcodes.ACC_PUBLIC,"priorityRank","(Ljava/lang/Object;)I",null,null);
        rank.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));rank.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,PORT,"priorityRank","(Ljava/lang/Object;)I",false));
        rank.instructions.add(new InsnNode(Opcodes.IRETURN));bridge.methods.add(rank);
        out.put(BRIDGE+".class",HungBySonCopySpecialPatcher.write(bridge));

        // No other class may still call the removed members.
        for(Map.Entry<String,byte[]> e:out.entrySet())if(e.getKey().endsWith(".class")){
            ClassNode c=HungBySonExpInfoPack.node(e.getValue());
            for(MethodNode m:c.methods)for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())if(x instanceof MethodInsnNode){
                MethodInsnNode call=(MethodInsnNode)x;
                check(!(call.name.equals("priorityPlayer")&&call.owner.equals(PORT))&&!(call.name.equals("priorityTarget")&&call.desc.equals("(Ljava/lang/Object;)Z")),"Stale priority call in "+c.name+"."+m.name);
            }
        }
        for(String name:new String[]{PORT,CTRL,API,BRIDGE})HungBySonExpInfoPack.screen(name,out.get(name+".class"));
        int classes=0,resources=0,changes=0;List<String> changed=new ArrayList<String>();
        for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;else resources++;
            if(!Arrays.equals(before.get(name),out.get(name))){changed.add(name);changes++;}
        }
        check(changes==4&&changed.containsAll(Arrays.asList(PORT+".class",CTRL+".class",API+".class",BRIDGE+".class")),"Four intended class edits: "+changed);
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("auto.pk.radius","365");
        p.setProperty("auto.pk.lines","true");p.setProperty("auto.pk.serpent","true");
        p.setProperty("auto.pk.class.count",String.valueOf(classes));
        StringBuilder list=new StringBuilder();for(String c:changed){if(list.length()>0)list.append(',');list.append(c);}
        p.setProperty("auto.pk.changed.classes",list.toString());
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Auto PK Battlefield priority Xa nu > level 40 > others");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{log.print(report);log.println("PASS pinned input; port selector/gate/painter/send, controller findTarget, rank API and bridge only; resources/manifest unchanged");log.println("CHANGED="+list);log.println("CLASSES="+classes+" RESOURCES="+resources);log.println("SHA256="+p.getProperty("output.sha256"));}finally{log.close();}
        System.out.println("PASS Auto PK serpent "+p.getProperty("output.sha256"));
    }
}
