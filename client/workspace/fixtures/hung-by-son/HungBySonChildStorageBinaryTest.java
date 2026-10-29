import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import javax.microedition.rms.RecordStore;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.util.MemoryRecordStoreManager;

/** Shared-RMS fixture proves new suite reads/writes/lists/deletes only its own data. */
public final class HungBySonChildStorageBinaryTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        File beforeFile=new File(args[0]),candidate=new File(args[1]);
        Map<String,byte[]> before=HungBySonChildLoginPatcher.read(beforeFile),after=HungBySonChildLoginPatcher.read(candidate);
        check(before.keySet().equals(after.keySet()),"Entries changed");int changed=0,classes=0,rms=0;
        JarFile zip=new JarFile(candidate);
        try{for(String name:before.keySet()) {
            if(!Arrays.equals(before.get(name),after.get(name))) {check(name.equals("META-INF/MANIFEST.MF")||name.equals(HungBySonChildStoragePatcher.STORAGE),"Unexpected changed entry: "+name);changed++;}
            CRC32 crc=new CRC32();crc.update(after.get(name));check(crc.getValue()==zip.getJarEntry(name).getCrc(),"ZIP CRC");
            if(name.endsWith(".class")) {
                classes++;ClassNode c=new ClassNode();new ClassReader(after.get(name)).accept(c,0);
                check(c.version==new ClassReader(before.get(name)).readUnsignedShort(6)&&c.version<=51,"Class version changed");
                for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)n;
                    if(!name.equals(HungBySonChildStoragePatcher.STORAGE)&&call.owner.equals("luna/generated/InvestigateStorage")&&Arrays.asList("openRecordStore","deleteRecordStore","listRecordStores").contains(call.name))rms++;
                    if(call.owner.equals("javax/microedition/rms/RecordStore")&&n.getOpcode()==Opcodes.INVOKESTATIC)check(name.equals(HungBySonChildStoragePatcher.STORAGE),"Unscoped RMS access");
                }
            }
        }}finally{zip.close();}
        check(changed==2&&classes==242&&rms==16,"Archive/RMS boundary counts");
        Attributes attrs=new Manifest(new ByteArrayInputStream(after.get("META-INF/MANIFEST.MF"))).getMainAttributes();
        check("HSOCHILD".equals(attrs.getValue("MIDlet-Name"))&&"HSOCHILD".equals(attrs.getValue("MIDlet-Vendor"))&&attrs.getValue("MIDlet-1").startsWith("HSOCHILD,"),"Suite identity");
        URLClassLoader loader=new URLClassLoader(new URL[]{candidate.toURI().toURL()},HungBySonChildStorageBinaryTest.class.getClassLoader());
        try{for(String name:after.keySet())if(name.endsWith(".class")) {Class<?> c=Class.forName(name.substring(0,name.length()-6).replace('/','.'),false,loader);c.getDeclaredFields();c.getDeclaredMethods();c.getDeclaredConstructors();}}finally{loader.close();}
        Common emulator=new Common(null);MemoryRecordStoreManager manager=new MemoryRecordStoreManager();manager.init(emulator);emulator.setRecordStoreManager(manager);MIDletBridge.setMicroEmulator(emulator);
        write("login",99);write("inv_login",88);write("inv_mc_inv_v1",77);write("inv_ms_inv_v1",66);
        loader=new URLClassLoader(new URL[]{candidate.toURI().toURL()},HungBySonChildStorageBinaryTest.class.getClassLoader());
        try {
            Class<?> storage=Class.forName("luna.generated.InvestigateStorage",true,loader);
            check(storage.getMethod("load").invoke(null)==null&&storage.getMethod("loadSelection").invoke(null)==null,"Old data imported");
            RecordStore own=(RecordStore)storage.getMethod("openRecordStore",String.class,boolean.class).invoke(null,"login",true);own.addRecord(new byte[]{11},0,1);own.closeRecordStore();
            check(read("HSOCHILD_login")==11&&read("inv_login")==88&&read("login")==99,"Account isolation");
            own=(RecordStore)storage.getMethod("openRecordStore",String.class,boolean.class).invoke(null,"HSOCHILD_login",false);check(own.getRecord(1)[0]==11,"Prefix idempotence");own.closeRecordStore();
            String[] list=(String[])storage.getMethod("listRecordStores").invoke(null);check(new HashSet<String>(Arrays.asList(list)).equals(Collections.singleton("login")),"List leaks old stores or strips wrong prefix");
            check(Boolean.TRUE.equals(storage.getMethod("save",byte[].class).invoke(null,(Object)new byte[]{21})),"Cache save");
            check(Boolean.TRUE.equals(storage.getMethod("saveSelection",byte[].class).invoke(null,(Object)new byte[]{31})),"Selection save");
            check(read("HSOCHILD_mc_v1")==21&&read("HSOCHILD_ms_v1")==31&&read("inv_mc_inv_v1")==77&&read("inv_ms_inv_v1")==66,"Mod-store isolation");
            storage.getMethod("deleteRecordStore",String.class).invoke(null,"login");check(read("inv_login")==88&&read("login")==99,"Delete touches old data");
            Set<String> all=new HashSet<String>(Arrays.asList(RecordStore.listRecordStores()));check(!all.contains("HSOCHILD_login")&&!all.contains("HSOCHILD_HSOCHILD_login"),"Delete/double-prefix mismatch");
        }finally{loader.close();}
        loader=new URLClassLoader(new URL[]{candidate.toURI().toURL()},HungBySonChildStorageBinaryTest.class.getClassLoader());
        try{Class<?> s=Class.forName("luna.generated.InvestigateStorage",true,loader);check(((byte[])s.getMethod("load").invoke(null))[0]==21&&((byte[])s.getMethod("loadSelection").invoke(null))[0]==31,"Fresh-loader persistence");}finally{loader.close();}
        System.out.println("STORAGE_BINARY=PASS; "+checks+" assertions; 242 linked classes; 16 scoped RMS calls; shared-data isolation, listing/deletion, cache/selection and restart");
    }
    private static void write(String name,int value)throws Exception{RecordStore s=RecordStore.openRecordStore(name,true);try{s.addRecord(new byte[]{(byte)value},0,1);}finally{s.closeRecordStore();}}
    private static int read(String name)throws Exception{RecordStore s=RecordStore.openRecordStore(name,false);try{return s.getRecord(1)[0]&255;}finally{s.closeRecordStore();}}
    private static void check(boolean okay,String message){checks++;if(!okay)throw new IllegalStateException(message);}
}
