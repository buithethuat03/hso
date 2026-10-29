import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Three isolated copies of a verified Hung-derived binary in the virtual launcher. */
public final class HungBySonX3Packer {
    static final String TEMPLATE_SHA="5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC";
    static final String[] SLOTS={"","b/","c/"};
    static final String MANIFEST="META-INF/MANIFEST.MF";
    static final String INI="hung-by-son-x3.ini";
    static final String SUPPORT="X3SlotSupport";
    static final String HUNG_SHA="BCF304BB6245CDBB6F48D5888404F8186E4E0895E2DCDAF608C68D53FEADA6E2";
    static final String HUNG_CLASS_SET="C45CD4C5A19064B43C8FEFC64828234D0E653228D97C4434FBFA5CC9C7902D85";
    static final String HUNG_INFO_SHA="3CB0DBA1EC67F0687057AEB1533C025A7858D85DC11B2A246097A029C053FCCF";
    static final String HUNG_INFO_CLASS_SET="25BFD0EFF35AE54332AB8B72EC87CC6431A635574CC11060FFC1995A862E450F";
    // Replace these pins only after the x1 Battlefield candidate passes its
    // binary and class-link checks. Four expected additions: support, API,
    // generated bridge, and MonsterCacheTemplateNames.
    static final String BATTLEFIELD_SHA="DA429E64940D87852F212854AF793C992D99DDF039DED0B85CAD1747FD950646";
    static final String BATTLEFIELD_CLASS_SET="A76E622E950124424BE66263C6D9E5D64FD646DF206A6EFC6A1B62FB51A527B7";
    static final int BATTLEFIELD_CLASS_COUNT=267;
    // Later Battlefield-profile x1: Special Attack + Farm spot (two more API interfaces).
    static final String FARM_SPOT_SHA="588B9F9370AFB4E3589E7BB3D794DA9E2564383F10B57351515E3E984A18EE0E";
    static final String FARM_SPOT_CLASS_SET="AC4499AC707DF03473F04DE8C8BA57F140386DF1D95610B0062DAF2CE917A95E";
    static final int FARM_SPOT_CLASS_COUNT=269;
    // ModUpLv port x1 (normal monsters, Lửa Thiêng, Phó bản / Mê cung): one more default-package class.
    static final String MODULV_SHA="193BA880410CE79C1398C2074FE43239A1F238A2C96D78D9530F9E19869162D9";
    static final String MODULV_CLASS_SET="95F659D49ED2349D76B79998447CB6289DD130D7823EA9C058E0D80443457DD1";
    static final int MODULV_CLASS_COUNT=270;
    // Lock-pacing x1 with opcode-40 buffs (ModUpLv attack loop, skill tabs, server lock): HungBySonSkillStats + SkillCastStats.
    static final String LOCK_SHA="EA213F3915D6000262A1C8CB8D7EF05DCCCCCAEE4A87DA7696735F5B1CDFFF6B";
    static final String LOCK_CLASS_SET="3E9309ABB9D750AEF4FF8EBE98AA585F3802CEC1E19169AB887CB7864763D58E";
    static final int LOCK_CLASS_COUNT=272;
    // Maze-exclusive x1 with the stationary dungeon and folded Phó chỉ huy dialogs: MovementArbiter + two controllers.
    static final String MAZE_SHA="F07535B4B0A7B71A896F9F5DBE9C9871C846B92827EA0273910CEC704EBBF600";
    static final String MAZE_CLASS_SET="1682E5B1EC41E73E0C92536149B19BA9F698DA62EDF79B7B65CD34BD1B75A7B9";
    static final int MAZE_CLASS_COUNT=275;
    // Auto PK x1 (365px, target lines, Battlefield Xà nữ priority) with equipment filters, sale scroll and map objects.
    static final String PK_SHA="896F3B6919C168A3D92FD5455F5DDEA3F0C32D0FA88B3E1A477A2DC55FCF79D8";
    /** Same PK class set; only HungBySonModUpLv.pickupFrame follows the Auto nhặt đồ menu. */
    static final String LOOT_MENU_SHA="2EB9258B2F68805E7F90B74AEAF88DA3CAF691707500B3DF48280FCE10A33C7E";
    /** Same PK class set; map reset race (et + world) and Auto Thú cưỡi mount guard on top of LOOT_MENU_SHA. */
    static final String MOUNT_GUARD_SHA="4A83A53621FAFE241A34C1A396C52D5FF7F36294BC3DF19D3366985C905730D6";
    /** Same PK class set; Mrs Oda row 15 + sale gate (HungBySonModUpLv) and route hold for Làng Phủ Sương on top of MOUNT_GUARD_SHA. */
    static final String FOG_ROUTE_SHA="C9E5073D6B0978873D6A712C803027951D7E3CE8EDA289AF3803DFC51246C9F7";
    static final String PK_CLASS_SET="8A5CF6D0822CA131E7F7460C31E709D6D8CC1BF441C29E7FFFEA3C2A396BD567";
    static final int PK_CLASS_COUNT=282;
    // PK x1 plus Đập đồ (HungBySonEnhance), Auto phục hồi thể lực (HungBySonStamina) and Auto thay đồ khi BUFF (HungBySonBuffSwap).
    static final String BUFF_SWAP_SHA="A215437818175041B179C5CA841BA36F0EC7ADBFA6A4EB7533CB490931E0EF2F";
    static final String BUFF_SWAP_CLASS_SET="53561D6F56A64A1BB5D458348658D446EB0440A2CA4D3BDDDB3BED378659C87F";
    static final int BUFF_SWAP_CLASS_COUNT=285;
    /** Same buff-swap class set; Auto PK Battlefield lower HP first inside each group (policy + HungBySonModUpLv). */
    static final String HP_ORDER_SHA="E52366BDB974761D106955F3E051346EA513A5D73ABC410D3649ACB5297CDE97";
    /** Same buff-swap class set; HP order plus the Battlefield target lock (HungBySonModUpLv only). */
    static final String TARGET_LOCK_SHA="B597B3B7588D5E5692B688A456E2B11B3B7A0A0A4B82EAF0DBAEEFAE0E2B4DBC";
    /** Same class set; verified object-cache exclusions and orange Battlefield player focus. */
    static final String CACHE_FOCUS_SHA="5E16E5EAFC1BE81DE19318894F1F4F94787B405635431962964AF09844766E1F";
    static final String SELECTION_OBJECTS_SHA="9C45CB4C061E1A358FFA2A4C5A8BD90E055A075B70BE791F2C97190600CAEDE2";
    static final String ROLLING_FINE_EXP_SHA="AA16DCCA0DA0A48DB0C0F39088D402D40390FCC15B779C50142BCF731B10A03C";
    // Verified adaptive rates, wallet tab and three-state inventory HUD (2026-10-29).
    static final String ECONOMY_SHA="855A7F66891DFBC0F6A64ABC00A148BB2BBE90D57EF9C416D1C3D85EB1F13A58";
    static final String GOLD_IDENTITY_SHA="CA7B14135F358CEA72D0EE6A6761C35BB8465AE0584E3064CA23706B0F2EC3EC";
    // Gold identity + Khoảng chờ lock modes 600/650/700ms (2026-10-29): only SkillCastStats differs.
    static final String LOCK_700_SHA="46C6C77F798F3C33A770DA16822C716AA8711029ED9A1FD5DB3594B27AB97F04";
    // Answer-anchored Đáp 400/425/450ms pacing, default Đáp 400ms (2026-10-29): SkillCastStats + three port methods.
    static final String ANSWER_400_SHA="56454EF0A5BB0812B72E949D96D7105F1318FBA9AFA915D7C3F21F5151F5E127";
    // Đáp 400+ default (Đáp 400ms + đòn 0's target moved behind for the next skill, 2026-10-29).
    static final String ROTATE_400_SHA="879B7F69E69B24C425D1D71D6D26D331FB3DCA3757B5BBB04DD290E85AF4D494";
    // Same code, default Khóa 575ms again (user request, 2026-10-29): only SkillCastStats differs.
    static final String DEFAULT_575_SHA="C33DF6F29581F3FB235C19FF1A4345B49CB6BA4BB4252E11D7BE54132501FDF9";
    // Auto v2 (learned send gap G + 300ms answer guard + target rotation) as default; Đáp modes removed (2026-10-29).
    static final String AUTO2_SHA="42120E2AA98F26A1205DB7C179D8FE918666400E1CBF408BDE038BBFA6353320";
    static final String ECONOMY_CLASS_SET="F946E6C442A415153CBA052A03343084C3246D01050DDB26E944ED275C59EF2C";
    static final int ECONOMY_CLASS_COUNT=287;
    static final String CHIBI_SHA="34BCFBF74DB570954DFA563E931D98F84DB9796EE018FB75570EC2EC2472E500";
    static final String SHARED="luna/generated/ExpEstimateSharedRmsStore";
    static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    public static void main(String[] args)throws Exception{
        check(args.length==6||args.length==7||args.length==8,"source.jar template.jar new.jar X3SlotSupport.class report.txt expected-source-sha [HSOCHILD|CHIBI|BATTLEFIELD [class-set-sha]]");
        boolean child=args.length==7&&"HSOCHILD".equals(args[6]);
        boolean chibi=args.length==7&&"CHIBI".equals(args[6]);
        boolean battlefield=args.length==8&&"BATTLEFIELD".equals(args[6]);
        check(args.length==6||child||chibi||battlefield,"Unsupported variant identity");
        boolean economy=battlefield&&ECONOMY_CLASS_SET.equals(args[7]);
        boolean swap=battlefield&&(BUFF_SWAP_CLASS_SET.equals(args[7])||economy);
        boolean pk=battlefield&&(PK_CLASS_SET.equals(args[7])||swap);
        boolean maze=battlefield&&(MAZE_CLASS_SET.equals(args[7])||pk);
        boolean lock=battlefield&&(LOCK_CLASS_SET.equals(args[7])||maze);
        boolean modulv=battlefield&&(MODULV_CLASS_SET.equals(args[7])||lock);
        boolean farmSpot=battlefield&&(FARM_SPOT_CLASS_SET.equals(args[7])||modulv);
        if(battlefield)check(farmSpot||BATTLEFIELD_CLASS_SET.equals(args[7]),"Unexpected pinned Battlefield class-set digest");
        String suite=chibi?"HSO CHIBI X3":child?"HSOCHILD X3":"Hung_By_Son X3",vendor=chibi?"HSO_CHIBI_X3":child?"HSOCHILD_X3":"Hung_By_Son_X3";
        String row=chibi?"HSO CHIBI":child?"HSOCHILD":"Hung By Son",ini=chibi?"chibi-up-x3.ini":child?"hsochild-update-x3.ini":INI;
        File input=new File(args[0]),template=new File(args[1]),output=new File(args[2]);
        String sourceHash=HsoChildX3Packer.hash(input),templateHash=HsoChildX3Packer.hash(template);
        check(sourceHash.equals(args[5]),"Unexpected single-client source");
        boolean hungInfo=!child&&!chibi&&!battlefield&&sourceHash.equals(HUNG_INFO_SHA);
        if(battlefield)check(economy?(sourceHash.equals(ECONOMY_SHA)||sourceHash.equals(GOLD_IDENTITY_SHA)||sourceHash.equals(LOCK_700_SHA)||sourceHash.equals(ANSWER_400_SHA)||sourceHash.equals(ROTATE_400_SHA)||sourceHash.equals(DEFAULT_575_SHA)||sourceHash.equals(AUTO2_SHA)):swap?sourceHash.equals(BUFF_SWAP_SHA)||sourceHash.equals(HP_ORDER_SHA)||sourceHash.equals(TARGET_LOCK_SHA)||sourceHash.equals(CACHE_FOCUS_SHA)||sourceHash.equals(SELECTION_OBJECTS_SHA)||sourceHash.equals(ROLLING_FINE_EXP_SHA):pk&&(sourceHash.equals(LOOT_MENU_SHA)||sourceHash.equals(MOUNT_GUARD_SHA)||sourceHash.equals(FOG_ROUTE_SHA))||sourceHash.equals(pk?PK_SHA:maze?MAZE_SHA:lock?LOCK_SHA:modulv?MODULV_SHA:farmSpot?FARM_SPOT_SHA:BATTLEFIELD_SHA),"Source must be the pinned verified Battlefield x1 candidate");
        check(child||sourceHash.equals(chibi?CHIBI_SHA:HUNG_SHA)||hungInfo||battlefield,"Source must be the verified endpoint release");
        check(templateHash.equals(TEMPLATE_SHA),"Unexpected launcher template");check(!output.exists(),"Output already exists");
        Map<String,byte[]> source=HsoChildX3Packer.readJar(input),wrapper=HsoChildX3Packer.readJar(template);
        Manifest original=new Manifest(new ByteArrayInputStream(source.get(MANIFEST)));
        check(original.getMainAttributes().getValue("MIDlet-1").endsWith(",com.silverknight.TemMidlet"),"Unexpected Hung MIDlet");
        check(!child||"HSOCHILD".equals(original.getMainAttributes().getValue("MIDlet-Vendor")),"Expected isolated HSOCHILD source");
        check(!chibi||"HSO CHIBI".equals(original.getMainAttributes().getValue("MIDlet-Vendor")),"Expected isolated CHIBI source");
        Map<String,String> normalize=new LinkedHashMap<String,String>();normalize.put("com/silverknight/TemMidlet","TemMidlet");
        Map<String,byte[]> classes=new LinkedHashMap<String,byte[]>();
        for(Map.Entry<String,byte[]> e:source.entrySet())if(e.getKey().endsWith(".class")){
            String name=e.getKey().substring(0,e.getKey().length()-6);
            // MIDlet's data/methods are public except its own private helper and
            // protected lifecycle overrides; moving it to the template root is safe.
            byte[] normalized=HsoChildX3Packer.transform(e.getValue(),normalize,0,true,new int[4]);
            classes.put(RepackSupportX3.cls(name,normalize),normalized);
        }
        int expectedSourceClasses=chibi?259:child?242:hungInfo?263:economy?ECONOMY_CLASS_COUNT:swap?BUFF_SWAP_CLASS_COUNT:pk?PK_CLASS_COUNT:maze?MAZE_CLASS_COUNT:lock?LOCK_CLASS_COUNT:modulv?MODULV_CLASS_COUNT:farmSpot?FARM_SPOT_CLASS_COUNT:battlefield?BATTLEFIELD_CLASS_COUNT:258;
        check(classes.size()==expectedSourceClasses&&!classes.containsKey("Static"),"Unexpected source class set");
        if(!child&&!chibi){
            String expectedClassSet=economy?ECONOMY_CLASS_SET:swap?BUFF_SWAP_CLASS_SET:pk?PK_CLASS_SET:maze?MAZE_CLASS_SET:lock?LOCK_CLASS_SET:modulv?MODULV_CLASS_SET:farmSpot?FARM_SPOT_CLASS_SET:battlefield?BATTLEFIELD_CLASS_SET:hungInfo?HUNG_INFO_CLASS_SET:HUNG_CLASS_SET;
            check(classSetHash(source).equals(expectedClassSet),"Unexpected Hung class-set digest");
        }
        Map<String,byte[]> localClasses=new LinkedHashMap<String,byte[]>(classes);
        if(!child)check(localClasses.remove(SHARED)!=null,"Missing shared EXP backend");
        Map<String,byte[]> result=new LinkedHashMap<String,byte[]>();
        Manifest manifest=new Manifest(new ByteArrayInputStream(source.get(MANIFEST)));Attributes a=manifest.getMainAttributes();
        a.putValue("MIDlet-Name",suite);a.putValue("MIDlet-Vendor",vendor);
        a.putValue("MIDlet-1",suite+",/MrQuyet/mid.png,MrQuyet.MrQuyet");
        a.remove(new Attributes.Name("MIDlet-2"));a.remove(new Attributes.Name("MIDlet-3"));
        for(int slot=0;slot<3;slot++)a.putValue(String.valueOf(slot),row+" "+(slot+1)+",/MrQuyet/mid.png,"+SLOTS[slot].replace('/','.')+"TemMidlet,/"+ini);
        a.putValue("HSO-X3-Source-SHA-256",sourceHash);a.putValue("HSO-X3-Template-SHA-256",templateHash);a.putValue("HSO-X3-RMS-Prefixes","a,b,c");
        ByteArrayOutputStream mf=new ByteArrayOutputStream();manifest.write(mf);result.put(MANIFEST,mf.toByteArray());
        for(Map.Entry<String,byte[]> e:source.entrySet())if(!e.getKey().endsWith(".class")&&!e.getKey().equals(MANIFEST))result.put(e.getKey(),e.getValue());
        int wrappers=0;for(Map.Entry<String,byte[]> e:wrapper.entrySet())if((e.getKey().startsWith("lib/")||e.getKey().startsWith("MrQuyet/"))&&e.getKey().endsWith(".class")){result.put(e.getKey(),e.getValue());wrappers++;}
        check(wrappers==19,"Wrapper count");result.put("MrQuyet/mid.png",wrapper.get("MrQuyet/mid.png"));result.put(ini,source.get(MANIFEST));
        byte[] helper=HsoChildX3Packer.read(new FileInputStream(args[3]));
        if(!child)result.put(SHARED+".class",source.get(SHARED+".class"));
        PrintWriter report=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[4]),"UTF-8"));
        for(int slot=0;slot<3;slot++){
            Map<String,String> map=new LinkedHashMap<String,String>();for(String name:classes.keySet())map.put(name,SLOTS[slot]+name);
            if(!child)map.put(SHARED,SHARED);
            map.put("Static",SLOTS[slot]+"Static");map.put(SUPPORT,SLOTS[slot]+SUPPORT);HsoChildX3Packer.addWrapperTypes(map);
            map.put("javax/microedition/lcdui/Alert","lib/Alert");map.put("javax/microedition/lcdui/AlertType","lib/AlertType");
            int[] counts=new int[4];int id=0,reflective=0;
            for(Map.Entry<String,byte[]> e:localClasses.entrySet()){
                byte[] lifecycle=HsoChildX3Packer.addLifecycle(e.getValue(),id++);ClassNode node=new ClassNode();new ClassReader(lifecycle).accept(node,0);
                // Root Static must access reset hooks on package-private semantic nested classes.
                node.access|=Opcodes.ACC_PUBLIC;
                for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof LdcInsnNode){
                    LdcInsnNode literal=(LdcInsnNode)insn;
                    if("hsosky_cfg".equals(literal.cst)||"inv_hsosky_cfg".equals(literal.cst)){
                        check(!chibi,"CHIBI source unexpectedly contains legacy reflective RMS literals");
                        literal.cst=HsoChildX3Packer.PREFIXES[slot]+(child?"HSOCHILD_hsosky_cfg":"inv_hsosky_cfg");reflective++;
                    }
                }
                ClassWriter writer=new ClassWriter(0);node.accept(writer);
                result.put(map.get(e.getKey())+".class",HsoChildX3Packer.transform(writer.toByteArray(),map,slot,false,counts));
            }
            Map<String,String> helperMap=new LinkedHashMap<String,String>(map);helperMap.put("java/lang/StringBuilder","java/lang/StringBuffer");
            result.put(map.get(SUPPORT)+".class",HsoChildX3Packer.transform(helper,helperMap,slot,true,new int[4]));
            result.put(SLOTS[slot]+"Static.class",HsoChildX3Packer.makeStatic(localClasses,SLOTS[slot]));
            // PK x1 adds EquipmentFilterSettings load/save opens (hbs_equipment_filters_v1), prefixed per slot like the others.
            // Inventory mode adds two direct opens (load/save), both scoped to this slot.
            check(Arrays.equals(counts,new int[]{child||chibi?1:economy?7:pk?5:3,1,1,0}),"Unexpected scoped RMS boundaries "+Arrays.toString(counts));
            report.println("slot="+(slot+1)+" game.classes="+classes.size()+" rms="+Arrays.toString(counts)+" reflective="+reflective);
        }
        if(child)HsoChildX3Packer.checkReferences(result,classes.keySet());else{checkSharedReferences(result);checkAutoBindings(result,chibi);}
        output.getAbsoluteFile().getParentFile().mkdirs();JarOutputStream out=new JarOutputStream(new FileOutputStream(output));
        try{for(Map.Entry<String,byte[]> e:result.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);out.putNextEntry(z);out.write(e.getValue());out.closeEntry();}}finally{out.close();}
        Map<String,byte[]> actual=HsoChildX3Packer.readJar(output);check(actual.keySet().equals(result.keySet()),"Archive entries");
        for(String name:result.keySet())check(Arrays.equals(result.get(name),actual.get(name)),"Saved bytes "+name);
        for(String name:source.keySet())if(!name.endsWith(".class")&&!name.equals(MANIFEST))check(Arrays.equals(source.get(name),actual.get(name)),"Source resource changed "+name);
        for(String name:wrapper.keySet())if((name.startsWith("lib/")||name.startsWith("MrQuyet/"))&&name.endsWith(".class"))check(Arrays.equals(wrapper.get(name),actual.get(name)),"Wrapper changed "+name);
        check(sourceHash.equals(HsoChildX3Packer.hash(input))&&templateHash.equals(HsoChildX3Packer.hash(template)),"Inputs changed");
        report.println("source.sha256="+sourceHash);report.println("template.sha256="+templateHash);report.println("output.sha256="+HsoChildX3Packer.hash(output));
        report.println("suite="+suite+"; vendor="+vendor+"; ini="+ini+"; rms="+(chibi?"aHSO_CHIBI_,bHSO_CHIBI_,cHSO_CHIBI_":child?"aHSOCHILD_,bHSOCHILD_,cHSOCHILD_":"ainv_,binv_,cinv_"));
        int classCount=0;for(String path:result.keySet())if(path.endsWith(".class"))classCount++;
        report.println("classes="+classCount+"; namespaces=default,b/,c/; shared.wrappers=19; source.resources=unchanged; cross.slot.references=shared-exp-only");
        if(battlefield)report.println("profile=BATTLEFIELD");
        report.println("source.classset.sha256="+classSetHash(source));report.println("shared.exp.owner="+SHARED+"; lifecycle=none; bytes=unchanged");report.close();
        Properties catalog=new Properties();catalog.setProperty("source.sha256",sourceHash);catalog.setProperty("template.sha256",templateHash);catalog.setProperty("output.sha256",HsoChildX3Packer.hash(output));catalog.setProperty("source.classset.sha256",classSetHash(source));catalog.setProperty("source.classes",String.valueOf(classes.size()));catalog.setProperty("output.classes",String.valueOf(classCount));catalog.setProperty("cross.slot.references","shared-exp-only");catalog.setProperty("cross.slot.unapproved.references","none");catalog.setProperty("shared.exp.owner",SHARED);catalog.setProperty("shared.exp.store","hbs_exp_levels_v2");catalog.setProperty("auto.rms.physical.names","ahbs_auto_attack_v1,bhbs_auto_attack_v1,chbs_auto_attack_v1");catalog.setProperty("auto.rms.logical.name","hbs_auto_attack_v1");
        if(battlefield)catalog.setProperty("profile","BATTLEFIELD");
        if(farmSpot)catalog.setProperty("farm.spot","true");
        if(modulv)catalog.setProperty("modulv.port","true");
        if(lock)catalog.setProperty("lock.pacing","true");
        if(swap)catalog.setProperty("buff.swap","true");
        if(chibi){catalog.setProperty("auto.rms.physical.names","aHSO_CHIBI_hbs_auto_attack_v1,bHSO_CHIBI_hbs_auto_attack_v1,cHSO_CHIBI_hbs_auto_attack_v1");catalog.setProperty("shared.exp.physical.store","aHSO_CHIBI_hbs_exp_levels_v2");catalog.setProperty("monster.cache.enabled","false");}
        FileOutputStream cat=new FileOutputStream(args[4]+".properties");try{catalog.store(cat,"Strict X3 pack binding");}finally{cat.close();}
        System.out.println((battlefield?"BATTLEFIELD_X3":chibi?"CHIBI_X3":"HUNG_X3")+"=PASS; "+classCount+" classes; SHA256="+HsoChildX3Packer.hash(output));
    }
    static String classSetHash(Map<String,byte[]> entries)throws Exception{
        TreeSet<String> names=new TreeSet<String>();for(String name:entries.keySet())if(name.endsWith(".class"))names.add(name);
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");for(String name:names)digest.update((name+"\n").getBytes("UTF-8"));
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format("%02X",b&255));return hex.toString();
    }
    static void checkAutoBindings(Map<String,byte[]> entries,boolean chibi){
        for(int slot=0;slot<3;slot++){
            ClassNode runtime=new ClassNode();new ClassReader(entries.get(SLOTS[slot]+"luna/features/AutoAttackRuntime.class")).accept(runtime,0);int names=0;
            for(MethodNode method:runtime.methods)for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof LdcInsnNode&&((LdcInsnNode)insn).cst.equals((SLOTS[slot]+"luna/generated/HungAutoAttackAccess").replace('/','.')))names++;
            check(names==1,"Exact per-slot reflective Auto attack bridge "+slot);
            ClassNode store=new ClassNode();new ClassReader(entries.get(SLOTS[slot]+"luna/features/AutoAttackRmsStore.class")).accept(store,0);int opens=0;
            for(FieldNode field:store.fields)if(field.name.equals("NAME"))check("hbs_auto_attack_v1".equals(field.value),"Logical Auto RMS name must not be double-prefixed");
            for(MethodNode method:store.methods)for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)insn;if(call.owner.equals(chibi?SLOTS[slot]+"luna/generated/InvestigateStorage":"lib/RecordStore")&&call.name.equals(chibi?"openRecordStore":HsoChildX3Packer.PREFIXES[slot]+"penRecordStore"))opens++;}
            check(opens==2,"Exact isolated Auto RMS read/write aliases "+slot);
            check(!entries.containsKey("b/"+SHARED+".class")&&!entries.containsKey("c/"+SHARED+".class"),"Only one shared EXP backend");
        }
    }
    static void checkSharedReferences(Map<String,byte[]> entries){
        for(String path:entries.keySet())if(path.endsWith(".class")&&!path.startsWith("lib/")&&!path.startsWith("MrQuyet/")){
            String slot=path.startsWith("b/")?"b/":path.startsWith("c/")?"c/":"";
            ClassNode node=new ClassNode();new ClassReader(entries.get(path)).accept(node,0);
            checkRef(node.superName,slot,entries);for(String type:node.interfaces)checkRef(type,slot,entries);
            for(FieldNode field:node.fields)checkRefDesc(field.desc,slot,entries);
            for(MethodNode method:node.methods){checkRefDesc(method.desc,slot,entries);for(AbstractInsnNode insn:method.instructions.toArray()){
                if(insn instanceof MethodInsnNode){MethodInsnNode m=(MethodInsnNode)insn;checkRef(m.owner,slot,entries);checkRefDesc(m.desc,slot,entries);}
                else if(insn instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)insn;checkRef(f.owner,slot,entries);checkRefDesc(f.desc,slot,entries);}
                else if(insn instanceof TypeInsnNode)checkRef(((TypeInsnNode)insn).desc,slot,entries);
                else if(insn instanceof MultiANewArrayInsnNode)checkRefDesc(((MultiANewArrayInsnNode)insn).desc,slot,entries);
                else if(insn instanceof LdcInsnNode&&((LdcInsnNode)insn).cst instanceof Type)checkRefDesc(((Type)((LdcInsnNode)insn).cst).getDescriptor(),slot,entries);
            }}
        }
    }
    static void checkRefDesc(String desc,String slot,Map<String,byte[]> entries){for(int i=0;i<desc.length();i++)if(desc.charAt(i)=='L'){int end=desc.indexOf(';',i);check(end>=0,"Bad descriptor");checkRef(desc.substring(i+1,end),slot,entries);i=end;}}
    static void checkRef(String name,String slot,Map<String,byte[]> entries){if(name==null)return;if(name.startsWith("[")){checkRefDesc(name,slot,entries);return;}if(name.startsWith("java/")||name.startsWith("javax/"))return;check(entries.containsKey(name+".class"),"Missing class "+name);if(name.startsWith("lib/")||name.startsWith("MrQuyet/")||name.equals(SHARED))return;String target=name.startsWith("b/")?"b/":name.startsWith("c/")?"c/":"";check(slot.equals(target),"Unexpected cross-slot reference "+name);}
}
