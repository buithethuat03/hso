import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import javax.microedition.rms.RecordStore;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.util.MemoryRecordStoreManager;

/** Verify the actual packed binary against a shared in-memory RMS backend. */
public final class HsoChildX3HostTest {
    static final String[] PACKAGES = {"", "b.", "c."};
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Common emulator = new Common(null);
        MemoryRecordStoreManager manager = new MemoryRecordStoreManager();
        manager.init(emulator); emulator.setRecordStoreManager(manager);
        MIDletBridge.setMicroEmulator(emulator);
        URLClassLoader loader = new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()}, HsoChildX3HostTest.class.getClassLoader());
        JarFile jar = new JarFile(args[0]);
        int count = 0;
        for (Enumeration<JarEntry> es=jar.entries(); es.hasMoreElements();) {
            JarEntry e=es.nextElement(); if (!e.getName().endsWith(".class")) continue;
            byte[] raw = bytes(jar.getInputStream(e));
            check(((raw[6]&255)<<8 | raw[7]&255)<=51, "Class version exceeds Java 7");
            Class<?> type = Class.forName(e.getName().substring(0,e.getName().length()-6).replace('/','.'), false, loader);
            type.getDeclaredFields(); type.getDeclaredMethods(); type.getDeclaredConstructors(); count++;
        }
        check(count==673,"Expected 673 classes");
        Attributes a=jar.getManifest().getMainAttributes();
        check("HSO CHILD X3".equals(a.getValue("MIDlet-Name")),"Suite identity");
        check("HSO_X3".equals(a.getValue("MIDlet-Vendor")),"Vendor identity");
        check(a.getValue("MIDlet-1").endsWith(",MrQuyet.MrQuyet"),"Single launcher entry");
        check(a.getValue("MIDlet-2")==null && a.getValue("MIDlet-3")==null,"Unexpected extra native MIDlets");
        for (int i=0;i<3;i++) {
            check(a.getValue(String.valueOf(i)).contains(","+PACKAGES[i]+"TemMidlet,"),"Virtual client entry "+i);
            check(Class.forName(PACKAGES[i]+"TemMidlet",false,loader).getSuperclass().getName().equals("lib.MIDlet"),"Virtual MIDlet inheritance");
        }
        RecordStore original=RecordStore.openRecordStore("login",true);
        original.addRecord(new byte[]{99},0,1); original.closeRecordStore();
        Class<?>[] support=new Class<?>[3]; Class<?>[] pet=new Class<?>[3];
        Class<?> stores=Class.forName("lib.RecordStore",true,loader);
        for (int i=0;i<3;i++) {
            support[i]=Class.forName(PACKAGES[i]+"X3SlotSupport",true,loader);
            pet[i]=Class.forName(PACKAGES[i]+"HSOSKYPetFeedSupport",true,loader);
            RecordStore own=(RecordStore)stores.getMethod((char)('a'+i)+"penRecordStore",String.class,boolean.class).invoke(null,"login",true);
            own.addRecord(new byte[]{(byte)(i+1)},0,1); own.closeRecordStore();
            pet[i].getField("selectedAutoLoginChar").setInt(null,i);
            pet[i].getMethod("saveRMS").invoke(null);
        }
        for (int i=0;i<3;i++) {
            RecordStore own=(RecordStore)stores.getMethod((char)('a'+i)+"penRecordStore",String.class,boolean.class).invoke(null,"login",false);
            check(own.getRecord(1)[0]==i+1,"Cross-slot login overwrite"); own.closeRecordStore();
            String[] names=(String[])stores.getMethod((char)('a'+i)+"istRecordStores").invoke(null);
            check(names.length==2 && new HashSet<String>(Arrays.asList(names)).equals(new HashSet<String>(Arrays.asList("login","hsosky_cfg"))),"RMS enumeration escaped slot");
            pet[i].getField("selectedAutoLoginChar").setInt(null,-1);
            pet[i].getMethod("loadRMS").invoke(null);
            check(pet[i].getField("selectedAutoLoginChar").getInt(null)==i,"Reflective config isolation");
            String resource=(String)support[i].getMethod("absoluteResource",String.class).invoke(null,"mfont/tahoma_14b");
            check("/mfont/tahoma_14b".equals(resource),"Relative resource path");
            InputStream stream=support[i].getResourceAsStream(resource); check(stream!=null,"Resource missing after relocation"); stream.close();
            Class<?> midlet=Class.forName(PACKAGES[i]+"TemMidlet",false,loader);
            Method save=null,load=null;
            for (Method m:midlet.getDeclaredMethods()) {
                if (Arrays.equals(m.getParameterTypes(),new Class[]{String.class,byte[].class}) && m.getReturnType()==void.class) save=m;
                if (Arrays.equals(m.getParameterTypes(),new Class[]{String.class}) && m.getReturnType()==byte[].class) load=m;
            }
            check(save!=null && load!=null,"Native login save/load ABI");
            save.invoke(null,"account",new byte[]{(byte)(i+10)});
            check(((byte[])load.invoke(null,"account"))[0]==i+10,"Native login save/load");
        }
        stores.getMethod("aeleteRecordStore",String.class).invoke(null,"login");
        for (int i=1;i<3;i++) {
            RecordStore own=(RecordStore)stores.getMethod((char)('a'+i)+"penRecordStore",String.class,boolean.class).invoke(null,"login",false);
            check(own.getRecord(1)[0]==i+1,"Delete affected another slot"); own.closeRecordStore();
        }
        original=RecordStore.openRecordStore("login",false); check(original.getRecord(1)[0]==99,"Original suite RMS overwritten"); original.closeRecordStore();
        // Restart the loaded state of slot 1 using the supplied lib.Stack API.
        Object lifecycle=Class.forName("Static",true,loader).newInstance();
        lifecycle.getClass().getMethod("clear").invoke(lifecycle);
        check(pet[0].getField("selectedAutoLoginChar").getInt(null)==0,"Static clear did not reset slot 1");
        lifecycle.getClass().getMethod("cinitclone").invoke(lifecycle);
        check(pet[0].getField("selectedAutoLoginChar").getInt(null)==-1,"Static restart did not restore initial defaults");
        pet[0].getMethod("loadRMS").invoke(null);
        check(pet[0].getField("selectedAutoLoginChar").getInt(null)==0,"Restart lost saved slot 1 config");
        check(pet[1].getField("selectedAutoLoginChar").getInt(null)==1 && pet[2].getField("selectedAutoLoginChar").getInt(null)==2,"Restart changed another slot's config");
        jar.close(); loader.close();
        System.out.println("OK: 673 JVM-verified classes; 1 launcher with 3 virtual clients; isolated native login, reflective config, list/delete; static clear/restart; shared resources");
    }
    static byte[] bytes(InputStream in) throws IOException {
        try { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n;
            while ((n=in.read(b))!=-1) out.write(b,0,n); return out.toByteArray();
        } finally { in.close(); }
    }
}
