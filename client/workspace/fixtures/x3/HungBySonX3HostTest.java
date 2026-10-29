import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import javax.microedition.rms.RecordStore;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.util.MemoryRecordStoreManager;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Packed namespace, lifecycle, timing and persistent-data isolation checks. */
public final class HungBySonX3HostTest {
    static final String[] SLOTS={"","b.","c."};static int checks;
    static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    static byte[] canonical(byte[] data){ClassNode n=new ClassNode();new ClassReader(data).accept(n,0);ClassWriter w=new ClassWriter(0);n.accept(w);return w.toByteArray();}
    public static void main(String[] args)throws Exception{
        check(args.length==3||args.length==4,"x3.jar single.jar timing.properties [HSOCHILD]");
        boolean child=args.length==4;check(!child||"HSOCHILD".equals(args[3]),"Unsupported suite");
        String dataPrefix=child?"HSOCHILD_":"inv_",cacheStore=child?"mc_v1":"mc_inv_v1",selectionStore=child?"ms_v1":"ms_inv_v1";
        Common emulator=new Common(null);MemoryRecordStoreManager manager=new MemoryRecordStoreManager();manager.init(emulator);emulator.setRecordStoreManager(manager);MIDletBridge.setMicroEmulator(emulator);
        Map<String,byte[]> packed=HsoChildX3Packer.readJar(new File(args[0])),single=HsoChildX3Packer.readJar(new File(args[1]));
        Properties timing=new Properties();InputStream config=new FileInputStream(args[2]);try{timing.load(config);}finally{config.close();}
        if(timing.getProperty("output.sha256")!=null)check(timing.getProperty("output.sha256").equalsIgnoreCase(HsoChildX3Packer.hash(new File(args[0]))),
                "X3 validation catalog is bound to this candidate JAR");
        if(timing.getProperty("input.sha256")!=null)check(timing.getProperty("input.sha256").equalsIgnoreCase(HsoChildX3Packer.hash(new File(args[1]))),
                "X3 validation catalog is bound to this verified single source");
        int expectedMinimum=Integer.parseInt(timing.getProperty("auto.delay.after.ms","650"));
        int expectedManualMinimum=Integer.parseInt(timing.getProperty("request.minimum.ms","650"));
        int sourceClasses=0;for(String name:single.keySet())if(name.endsWith(".class"))sourceClasses++;
        String sharedExp="luna/generated/ExpEstimateSharedRmsStore";
        boolean hasSharedExp=single.containsKey(sharedExp+".class");
        boolean hasAutoAttack=single.containsKey("luna/features/AutoAttackRuntime.class");
        int historicalCount=sourceClasses==242?751:(sourceClasses==249?770:(sourceClasses==258?797:-1));
        int expectedAttackDelay=Integer.parseInt(timing.getProperty("auto.attack.delay.default.ms",
                "650"));
        boolean sharedFarmDelay=hasAutoAttack&&"true".equals(timing.getProperty("delay.shared.farm","false"));
        if(sharedFarmDelay)check(expectedAttackDelay>=1&&expectedAttackDelay<=1000&&expectedManualMinimum==650,
                "Shared Farm/Auto delay stays in 1..1000ms while manual requests retain 650ms");
        int expectedLinked=Integer.parseInt(timing.getProperty("x3.class.count",String.valueOf(historicalCount)));
        check(expectedLinked>0,"X3 class count is explicitly catalogued or matches a maintained historical source size");
        JarFile jar=new JarFile(args[0]);Attributes manifest=jar.getManifest().getMainAttributes();
        check((child?"HSOCHILD X3":"Hung_By_Son X3").equals(manifest.getValue("MIDlet-Name"))&&(child?"HSOCHILD_X3":"Hung_By_Son_X3").equals(manifest.getValue("MIDlet-Vendor")),"Distinct X3 suite");
        check(manifest.getValue("MIDlet-1").endsWith(",MrQuyet.MrQuyet")&&manifest.getValue("MIDlet-2")==null,"One native launcher");
        URLClassLoader loader=new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()},HungBySonX3HostTest.class.getClassLoader());
        int linked=0;for(String name:packed.keySet())if(name.endsWith(".class")){
            Class<?> type=Class.forName(name.substring(0,name.length()-6).replace('/','.'),false,loader);
            type.getDeclaredMethods();type.getDeclaredFields();type.getDeclaredConstructors();linked++;
        }
        check(linked==expectedLinked,"All "+expectedLinked+" classes link with JVM verification; found "+linked);
        if(hasSharedExp){
            check(packed.containsKey(sharedExp+".class"),"One suite-wide shared EXP backend exists in the root namespace");
            check(Arrays.equals(single.get(sharedExp+".class"),packed.get(sharedExp+".class")),
                    "Root shared EXP backend remains byte-identical to the verified single source");
            check(!packed.containsKey("b/"+sharedExp+".class")&&!packed.containsKey("c/"+sharedExp+".class"),
                    "X3 does not create per-slot copies of the shared EXP backend");
        }
        for(String name:single.keySet())if(!name.endsWith(".class")&&!name.equals("META-INF/MANIFEST.MF"))check(Arrays.equals(single.get(name),packed.get(name)),"Shared source resource "+name);
        for(int slot=1;slot<3;slot++){
            String prefix=SLOTS[slot].replace('.','/');Map<String,String> reverse=new LinkedHashMap<String,String>();
            for(String name:packed.keySet())if(name.startsWith(prefix)&&name.endsWith(".class"))reverse.put(name.substring(0,name.length()-6),name.substring(prefix.length(),name.length()-6));
            for(String name:reverse.keySet()){
                byte[] root=packed.get(reverse.get(name)+".class");check(root!=null,"Root peer "+name);
                ClassNode n=new ClassNode();new ClassReader(packed.get(name+".class")).accept(n,0);
                for(MethodNode m:n.methods)for(AbstractInsnNode insn:m.instructions.toArray()){
                    if(insn instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)insn;
                        if(call.owner.equals("lib/RecordStore")&&(call.name.equals("bpenRecordStore")||call.name.equals("cpenRecordStore")||call.name.equals("bistRecordStores")||call.name.equals("cistRecordStores")||call.name.equals("beleteRecordStore")||call.name.equals("celeteRecordStore")))call.name="a"+call.name.substring(1);
                    }else if(insn instanceof LdcInsnNode){LdcInsnNode literal=(LdcInsnNode)insn;if(((slot==1?"b":"c")+dataPrefix+"hsosky_cfg").equals(literal.cst))literal.cst="a"+dataPrefix+"hsosky_cfg";}
                }
                ClassWriter w=new ClassWriter(0);n.accept(w);byte[] normalized=HsoChildX3Packer.transform(w.toByteArray(),reverse,0,true,new int[4]);
                check(Arrays.equals(canonical(root),canonical(normalized)),"Complete slot parity "+name);
            }
        }
        if(hasSharedExp){
            byte[] sharedAdapter=packed.get("luna/generated/ExpEstimateRmsStore.class");
            check(sharedAdapter!=null,"Root EXP adapter exists");
            for(int slot=0;slot<3;slot++){
                String namespace=SLOTS[slot].replace('.','/');
                ClassNode adapter=new ClassNode();new ClassReader(packed.get(namespace+"luna/generated/ExpEstimateRmsStore.class")).accept(adapter,0);
                boolean delegatesShared=false;
                for(MethodNode method:adapter.methods)for(AbstractInsnNode instruction:method.instructions.toArray())
                    if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;
                        if(call.owner.equals(sharedExp))delegatesShared=true;
                    }
                check(delegatesShared,"Slot "+slot+" EXP adapter delegates to the one root shared backend");
            }
        }
        if(hasAutoAttack)check(Class.forName("javax.microedition.lcdui.Alert",false,loader)!=null,
                "MIDP Alert API referenced by the native Auto Attack input bridge links");
        Class<?> stores=Class.forName("lib.RecordStore",true,loader);
        RecordStore original=RecordStore.openRecordStore(dataPrefix+"login",true);original.addRecord(new byte[]{99},0,1);original.closeRecordStore();
        if(child){RecordStore old=RecordStore.openRecordStore("inv_login",true);old.addRecord(new byte[]{88},0,1);old.closeRecordStore();}
        Class<?>[] storage=new Class<?>[3],cache=new Class<?>[3],attackRmsType=new Class<?>[3];int[][] cacheMapIds=new int[3][];
        Object[] accesses=new Object[3],attackRms=new Object[3];
        for(int slot=0;slot<3;slot++){
            final int index=slot;String p=SLOTS[slot];
            Class<?> midlet=Class.forName(p+"TemMidlet",true,loader);check(midlet.getSuperclass().getName().equals("lib.MIDlet"),"Virtual client "+slot);
            check(manifest.getValue(String.valueOf(slot)).contains(","+p+"TemMidlet,"),"Virtual manifest "+slot);
            Method save=midlet.getMethod("a",String.class,byte[].class),load=midlet.getMethod("a",String.class);
            save.invoke(null,"login",new byte[]{(byte)(slot+1)});check(((byte[])load.invoke(null,"login"))[0]==slot+1,"Native account storage "+slot);
            storage[slot]=Class.forName(p+"luna.generated.InvestigateStorage",true,loader);
            storage[slot].getMethod("saveSelection",byte[].class).invoke(null,(Object)new byte[]{(byte)(20+slot)});
            cache[slot]=Class.forName(p+"luna.features.MonsterCacheSupport",true,loader);
            final Class<?> st=storage[slot];final Object monster=new Object();final int[] activeMap=new int[]{1},cacheLoads=new int[1],cacheSaves=new int[1];cacheMapIds[slot]=activeMap;
            Class<?> api=Class.forName(p+"luna.api.MonsterCacheAccess",true,loader);
            accesses[slot]=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{api},new InvocationHandler(){
                public Object invoke(Object proxy,Method method,Object[] a)throws Throwable{
                    String n=method.getName();if(n.equals("now"))return Long.valueOf(1000L);
                    if(n.equals("serverKey"))return "x3-test:19129";if(n.equals("mapId"))return Integer.valueOf(activeMap[0]);if(n.equals("zoneId"))return Integer.valueOf(0);
                    if(n.equals("ready"))return Boolean.TRUE;if(n.equals("autoEnabled")||n.equals("dead")||n.equals("hidden")||n.equals("retiring"))return Boolean.FALSE;
                    if(n.equals("find"))return monster;if(n.equals("type")||n.equals("hp"))return Integer.valueOf(1);if(n.equals("id"))return Integer.valueOf(42+index);
                    if(n.equals("load")){cacheLoads[0]++;return st.getMethod("load").invoke(null);}if(n.equals("save")){cacheSaves[0]++;return st.getMethod("save",byte[].class).invoke(null,a[0]);}
                    if(method.getReturnType()==Void.TYPE)return null;throw new AssertionError(n);
                }
            });
            activeMap[0]=48;cache[slot].getMethod("onMap",api).invoke(null,accesses[slot]);
            cache[slot].getMethod("onObject",api,int.class,int.class,int.class,int.class,int.class,int.class).invoke(null,accesses[slot],1,148+slot,48+slot,10,20,1);
            cache[slot].getMethod("onDetail",api,int.class,int.class,int.class,int.class,int.class,int.class).invoke(null,accesses[slot],48+slot,18+slot,10,20,1,2);
            cache[slot].getMethod("flushNow",api).invoke(null,accesses[slot]);
            check(cacheLoads[0]==0&&cacheSaves[0]==0,"First map 48 never reads or creates slot "+slot+" cache RMS");
            check(((int[])cache[slot].getMethod("remembered",String.class,int.class).invoke(null,"x3-test:19129",48)).length==0,"First map 48 has no remembered monsters in slot "+slot);
            activeMap[0]=1;
            cache[slot].getMethod("onMap",api).invoke(null,accesses[slot]);
            cache[slot].getMethod("onObject",api,int.class,int.class,int.class,int.class,int.class,int.class).invoke(null,accesses[slot],1,101+slot,42+slot,10,20,1);
            cache[slot].getMethod("onDetail",api,int.class,int.class,int.class,int.class,int.class,int.class).invoke(null,accesses[slot],42+slot,11+slot,10,20,1,2);
            cache[slot].getMethod("flushNow",api).invoke(null,accesses[slot]);
            byte[] permittedMapBytes=(byte[])st.getMethod("load").invoke(null);int loadsBefore48=cacheLoads[0],savesBefore48=cacheSaves[0];activeMap[0]=48;
            cache[slot].getMethod("onMap",api).invoke(null,accesses[slot]);
            cache[slot].getMethod("onObject",api,int.class,int.class,int.class,int.class,int.class,int.class).invoke(null,accesses[slot],1,148+slot,48+slot,10,20,1);
            cache[slot].getMethod("onDetail",api,int.class,int.class,int.class,int.class,int.class,int.class).invoke(null,accesses[slot],48+slot,18+slot,10,20,1,2);
            cache[slot].getMethod("flushNow",api).invoke(null,accesses[slot]);
            check(cacheLoads[0]==loadsBefore48,"Map 48 does not load the cache RMS in slot "+slot);
            check(cacheSaves[0]==savesBefore48&&Arrays.equals(permittedMapBytes,(byte[])st.getMethod("load").invoke(null)),"Map 48 leaves slot "+slot+" cache bytes untouched");
            check(((int[])cache[slot].getMethod("remembered",String.class,int.class).invoke(null,"x3-test:19129",48)).length==0,"Map 48 never exposes cached monsters in slot "+slot);
            Class<?> cast=Class.forName(p+timing.getProperty("auto.delay.owner").replace('/','.'),true,loader);
            Field gate=cast.getDeclaredField(timing.getProperty("auto.delay.field"));gate.setAccessible(true);check(gate.getInt(null)==expectedMinimum,"Initialized "+expectedMinimum+"ms gate "+slot);
            if(hasAutoAttack){
                Class<?> alertInput=Class.forName(p+"luna.generated.HungAutoAttackInput",false,loader);
                alertInput.getDeclaredMethods();alertInput.getDeclaredConstructors();
                byte[] inputClass=packed.get((p.replace('.','/')+"luna/generated/HungAutoAttackInput.class"));
                String inputConstants=inputClass==null?"":new String(inputClass,"ISO-8859-1");
                check(inputClass!=null&&inputConstants.contains("lib/Alert")&&inputConstants.contains("lib/AlertType")
                                &&packed.containsKey("lib/Alert.class")&&packed.containsKey("lib/AlertType.class"),
                        "Slot "+slot+" native delay bridge links to the packed MIDP Alert wrappers");
                Class<?> attackRuntime=Class.forName(p+"luna.features.AutoAttackRuntime",true,loader);
                check(!((Boolean)attackRuntime.getMethod("enabled").invoke(null)).booleanValue()
                                && !((Boolean)attackRuntime.getMethod("playersEnabled").invoke(null)).booleanValue()
                                && ((Integer)attackRuntime.getMethod("delayMs").invoke(null)).intValue()==expectedAttackDelay,
                        "Slot "+slot+" Auto Attack loads mode 7/delay "+expectedAttackDelay+" but starts OFF");
                attackRmsType[slot]=Class.forName(p+"luna.features.AutoAttackRmsStore",true,loader);
                attackRms[slot]=attackRmsType[slot].getDeclaredConstructor().newInstance();
                int[] autoSettings=new int[]{-1,-1};
                attackRmsType[slot].getMethod("load",int[].class).invoke(attackRms[slot],(Object)autoSettings);
                check(autoSettings[0]==0&&autoSettings[1]==expectedAttackDelay,
                        "Slot "+slot+" has isolated default Auto Attack RMS settings");
                if(sharedFarmDelay)check(farmAutoMinimum(timing,loader,slot)==expectedAttackDelay,
                        "Slot "+slot+" native Farm gate reads the same configured Auto Attack delay");
            }
        }
        if(hasAutoAttack){
            attackRmsType[0].getMethod("save",Boolean.TYPE,Integer.TYPE).invoke(attackRms[0],Boolean.TRUE,Integer.valueOf(333));
            refreshRuntimeFromRms(loader,0);
            check(autoAttackDelay(loader,0)==333,"Slot 1 Auto Attack controller reloads its saved delay");
            if(sharedFarmDelay)check(farmAutoMinimum(timing,loader,0)==333,
                    "Slot 1 Farm and Auto Attack immediately share the saved 333ms delay");
            for(int slot=1;slot<3;slot++){
                int[] autoSettings=new int[]{-1,-1};
                attackRmsType[slot].getMethod("load",int[].class).invoke(attackRms[slot],(Object)autoSettings);
                check(autoSettings[0]==0&&autoSettings[1]==expectedAttackDelay,
                        "Slot "+slot+" remains unchanged after another client saves Auto Attack settings");
            }
            attackRmsType[1].getMethod("save",Boolean.TYPE,Integer.TYPE).invoke(attackRms[1],Boolean.FALSE,Integer.valueOf(444));
            refreshRuntimeFromRms(loader,1);
            check(autoAttackDelay(loader,1)==444&&autoAttackDelay(loader,0)==333
                            &&autoAttackDelay(loader,2)==expectedAttackDelay,
                    "Auto Attack controllers load only their own slot's persisted delay");
            if(sharedFarmDelay)check(farmAutoMinimum(timing,loader,1)==444&&farmAutoMinimum(timing,loader,0)==333
                            &&farmAutoMinimum(timing,loader,2)==expectedAttackDelay,
                    "Per-slot Farm and Auto Attack delays remain independent across the three clients");
            int[] slotZeroSettings=new int[]{-1,-1};
            attackRmsType[0].getMethod("load",int[].class).invoke(attackRms[0],(Object)slotZeroSettings);
            check(slotZeroSettings[0]==1&&slotZeroSettings[1]==333,
                    "Auto Attack RMS updates in slot 2 do not overwrite slot 1");
        }
        for(int slot=0;slot<3;slot++){
            check(((byte[])storage[slot].getMethod("loadSelection").invoke(null))[0]==20+slot,"Selection isolation "+slot);
            int[] remembered=(int[])cache[slot].getMethod("remembered",String.class,int.class).invoke(null,"x3-test:19129",1);
            check(Arrays.equals(remembered,new int[]{101+slot,11+slot}),"Cache/static context isolation "+slot);


            String[] names=(String[])stores.getMethod((char)(97+slot)+"istRecordStores").invoke(null);
            Set<String> expectedStores=new HashSet<String>(Arrays.asList(dataPrefix+"login",dataPrefix+cacheStore,dataPrefix+selectionStore));
            if(hasAutoAttack&&slot<2)expectedStores.add("hbs_auto_attack_v1");
            check(new HashSet<String>(Arrays.asList(names)).equals(expectedStores),"Scoped enumeration "+slot+" names="+Arrays.toString(names));
            if(child){RecordStore physical=RecordStore.openRecordStore((char)('a'+slot)+dataPrefix+"login",false);check(physical.getNumRecords()==1,"Physical HSOCHILD slot prefix "+slot);physical.closeRecordStore();}
            String resource=(String)Class.forName(SLOTS[slot]+"X3SlotSupport",true,loader).getMethod("absoluteResource",String.class).invoke(null,"mfont/tahoma_14b");
            InputStream asset=storage[slot].getResourceAsStream(resource);check(asset!=null,"Shared font loads "+slot);asset.close();
        }
        storage[0].getMethod("deleteRecordStore",String.class).invoke(null,"login");
        original=RecordStore.openRecordStore(dataPrefix+"login",false);check(original.getRecord(1)[0]==99,"Original single RMS untouched");original.closeRecordStore();
        if(child){RecordStore old=RecordStore.openRecordStore("inv_login",false);check(old.getRecord(1)[0]==88,"Investigate RMS untouched");old.closeRecordStore();}
        for(int slot=1;slot<3;slot++)check(((byte[])Class.forName(SLOTS[slot]+"TemMidlet",true,loader).getMethod("a",String.class).invoke(null,"login"))[0]==slot+1,"Delete remains in client 1");
        Object life=Class.forName("Static",true,loader).getConstructor().newInstance();life.getClass().getMethod("clear").invoke(life);life.getClass().getMethod("cinitclone").invoke(life);
        cacheMapIds[0][0]=1;Class<?> api=Class.forName("luna.api.MonsterCacheAccess",true,loader);cache[0].getMethod("onMap",api).invoke(null,accesses[0]);
        for(int slot=0;slot<3;slot++)check(Arrays.equals((int[])cache[slot].getMethod("remembered",String.class,int.class).invoke(null,"x3-test:19129",1),new int[]{101+slot,11+slot}),"Static restart preserves persisted cache and other clients "+slot);
        jar.close();loader.close();System.out.println("MAP48_CACHE_DISABLED_VERIFIED=true");System.out.println("HungBySonX3HostTest PASS ("+checks+" checks); "+expectedLinked+" linked classes, full three-slot parity, "+(hasSharedExp?"one root shared EXP backend":"native RMS")+", isolated Farm/cache/selection/restart state"+(hasAutoAttack?", isolated Auto Attack RMS and "+expectedAttackDelay+"ms delay":"")+", "+expectedMinimum+"ms native gates");
    }

    private static int farmAutoMinimum(Properties timing,URLClassLoader loader,int slot)throws Exception{
        String p=SLOTS[slot].replace('.','/');
        String farmOwner=timing.getProperty("native.farm.enabled.owner");
        String farmField=timing.getProperty("native.farm.enabled.field");
        String bridgeOwner=timing.getProperty("native.farmAutoMinimum.owner");
        String bridgeMethod=timing.getProperty("native.farmAutoMinimum.method");
        check(farmOwner!=null&&farmField!=null&&bridgeOwner!=null&&bridgeMethod!=null,
                "Shared Farm delay symbols are present in the verified single catalog");
        Class<?> farm=Class.forName((p+farmOwner).replace('/','.'),true,loader);
        Field enabled=farm.getDeclaredField(farmField);enabled.setAccessible(true);
        boolean wasEnabled=enabled.getBoolean(null);
        Class<?> bridge=Class.forName((p+bridgeOwner).replace('/','.'),true,loader);
        Method minimum=bridge.getDeclaredMethod(bridgeMethod,new Class<?>[0]);minimum.setAccessible(true);
        try{
            enabled.setBoolean(null,true);
            return ((Integer)minimum.invoke(null)).intValue();
        }finally{enabled.setBoolean(null,wasEnabled);}
    }

    private static int autoAttackDelay(URLClassLoader loader,int slot)throws Exception{
        String runtimeName=SLOTS[slot]+"luna.features.AutoAttackRuntime";
        return ((Integer)Class.forName(runtimeName,true,loader).getMethod("delayMs",new Class<?>[0]).invoke(null)).intValue();
    }

    private static void refreshRuntimeFromRms(URLClassLoader loader,int slot)throws Exception{
        String p=SLOTS[slot].replace('.','/');
        Class<?> access=Class.forName((p+"luna.api.AutoAttackAccess").replace('/','.'),true,loader);
        Class<?> settings=Class.forName((p+"luna.api.AutoAttackSettings").replace('/','.'),true,loader);
        Class<?> clock=Class.forName((p+"luna.api.LunaClock").replace('/','.'),true,loader);
        Object accessStub=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{access},new InvocationHandler(){
            public Object invoke(Object proxy,Method method,Object[] args){
                Class<?> result=method.getReturnType();
                if(result==Boolean.TYPE)return Boolean.FALSE;
                if(result==Integer.TYPE)return Integer.valueOf(0);
                if(result==Long.TYPE)return Long.valueOf(0L);
                return null;
            }
        });
        Object clockStub=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{clock},new InvocationHandler(){
            public Object invoke(Object proxy,Method method,Object[] args){return Long.valueOf(0L);}
        });
        Object store=Class.forName((p+"luna.features.AutoAttackRmsStore").replace('/','.'),true,loader).getDeclaredConstructor().newInstance();
        Class<?> runtime=Class.forName((p+"luna.features.AutoAttackRuntime").replace('/','.'),true,loader);
        runtime.getMethod("installForTest",access,settings,clock).invoke(null,accessStub,store,clockStub);
    }
}
