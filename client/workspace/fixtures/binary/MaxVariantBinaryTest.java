import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.CRC32;
import javax.microedition.io.*;
import javax.microedition.rms.*;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.util.MemoryRecordStoreManager;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Executes donor/candidate serializers and actual native storage/socket boundaries. */
public final class MaxVariantBinaryTest implements Opcodes {
    static final String SELF="MaxVariantBinaryTest";
    public static DataOutputStream out;
    public static int deviceInt,deviceByte;
    public static boolean deviceA,deviceB;
    public static String deviceInfo,uri;
    static int opcode,checks;
    static final class Loader extends ClassLoader {
        Loader(){super(MaxVariantBinaryTest.class.getClassLoader());}
        Class<?> define(String n,byte[] b){return defineClass(n,b,0,b.length);}
    }
    public static DataOutputStream stream(Object ignored){return out;}
    public static void begin(Object ignored,byte value){opcode=value;}
    public static Connection open(String value){uri=value;return (Connection)java.lang.reflect.Proxy.newProxyInstance(MaxVariantBinaryTest.class.getClassLoader(),new Class<?>[]{SocketConnection.class},new InvocationHandler(){public Object invoke(Object p,Method m,Object[] a){Class<?> t=m.getReturnType();if(t==boolean.class)return Boolean.FALSE;if(t==int.class)return Integer.valueOf(0);return null;}});}

    public static void main(String[] args)throws Exception {
        check(args.length==4,"baseline donor candidate catalog");
        Map<String,byte[]> before=MaxVariantPatcher.read(new File(args[0])),donor=MaxVariantPatcher.read(new File(args[1])),after=MaxVariantPatcher.read(new File(args[2]));
        Properties p=new Properties();Reader r=new InputStreamReader(new FileInputStream(args[3]),"UTF-8");try{p.load(r);}finally{r.close();}
        Class<?> old=serializer(MaxVariantPatcher.node(donor.get("q.class")),"a",false,"MaxDonorPacketProbe");
        Class<?> current=serializer(MaxVariantPatcher.node(after.get(p.getProperty("login.owner")+".class")),p.getProperty("login.method"),true,"MaxCandidatePacketProbe");
        for(int i=0;i<5;i++){
            deviceInt=i==0?1:250+i;deviceByte=i==0?-1:7-i;deviceA=(i&1)==0;deviceB=(i&2)==0;deviceInfo=i==2?"tail-\u96ea":"device-"+i;
            String user=i==2?"\u7528\u6237-\u96ea":"fixture-user",pass=i==2?"\u043f\u0430\u0440\u043e\u043b\u044c":"fixture-pass";int selected=i==0?-1:i*17;byte flag=(byte)(i-1);
            byte[] expected=packet(old,user,pass,MaxVariantPatcher.VERSION,MaxVariantPatcher.KEY,selected,flag);
            byte[] actual=packet(current,user,pass,"wrong-caller-version","wrong-caller-key",selected,flag);
            check(Arrays.equals(expected,actual),"Full donor/candidate opcode-1 packet differs case "+i);
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(actual));
            check(in.readUTF().equals(user)&&in.readUTF().equals(pass),"Raw credential ordering");check(in.readUTF().equals("2.5.8"),"Donor version");
            for(int k=0;k<3;k++)check(in.readUTF().equals(MaxVariantPatcher.KEY),"Donor key triplet without suffix");
            check(in.readUnsignedByte()==(deviceInt&255)&&in.readByte()==0&&in.readInt()==selected&&in.readByte()==flag,"Selector and flags");
            check(in.readByte()==(deviceA?1:0)&&in.readByte()==80&&in.readByte()==deviceByte&&in.readByte()==(deviceB?1:0)&&in.readShort()==2389,"Donor device tail");
            check(in.readUTF().equals(deviceInfo)&&in.available()==0,"Device UTF ends payload without old LOCK");check(opcode==1,"Donor opcode");
        }
        check(after.size()==before.size()+1,"One storage helper added");int unchanged=0,resources=0;
        Set<String> changed=new LinkedHashSet<String>(Arrays.asList(p.getProperty("changed.classes").split(",")));
        for(String name:before.keySet()){
            check(after.containsKey(name),"Lost archive entry "+name);
            if(name.endsWith(".class")){
                byte[] a=before.get(name),b=after.get(name);check(a[6]==b[6]&&a[7]==b[7],"Original class version "+name);
                if(!changed.contains(name.substring(0,name.length()-6))){check(Arrays.equals(a,b),"Unexpected class change "+name);unchanged++;}
            }else if(!name.equals("META-INF/MANIFEST.MF")){check(Arrays.equals(before.get(name),after.get(name)),"Resource change "+name);resources++;}
        }
        for(String name:after.keySet())check(before.containsKey(name)||name.equals(MaxVariantPatcher.STORAGE+".class"),"Unexpected entry "+name);
        JarFile jar=new JarFile(args[2]);try{for(Enumeration<JarEntry> e=jar.entries();e.hasMoreElements();){JarEntry z=e.nextElement();CRC32 crc=new CRC32();crc.update(after.get(z.getName()));check(crc.getValue()==z.getCrc(),"ZIP CRC "+z.getName());}Attributes a=jar.getManifest().getMainAttributes();check("HSO MAX".equals(a.getValue("MIDlet-Name"))&&"HSO MAX".equals(a.getValue("MIDlet-Vendor"))&&"HSO MAX,,TemMidlet".equals(a.getValue("MIDlet-1")),"Suite identity");check(MaxVariantPatcher.HOST.equals(a.getValue("HSO-Server-IP")),"Endpoint metadata");}finally{jar.close();}
        ClassNode tables=MaxVariantPatcher.node(after.get(p.getProperty("server.list.owner")+".class"));int writes=0,selectors=0,rms=0;
        for(String name:after.keySet())if(name.endsWith(".class")){ClassNode c=MaxVariantPatcher.node(after.get(name));check(c.version<=V1_7,"Unsupported class version");for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray()){
            if(n instanceof FieldInsnNode&&n.getOpcode()==PUTSTATIC){FieldInsnNode f=(FieldInsnNode)n;
                if(f.owner.equals(tables.name)&&f.desc.equals("[[Ljava/lang/String;")){AbstractInsnNode prev=MaxVariantPatcher.previous(n);check(prev instanceof MethodInsnNode&&((MethodInsnNode)prev).name.equals(MaxVariantPatcher.FACTORY),"Unprotected server table write");writes++;}
                if(f.owner.equals(p.getProperty("selector.owner"))&&f.name.equals(p.getProperty("selector.name"))&&f.desc.equals("B")){check(MaxVariantPatcher.previous(n).getOpcode()==ICONST_0,"Nonzero selected server");selectors++;}
            }
            if(n instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)n;if(call.owner.equals(MaxVariantPatcher.STORAGE)&&!c.name.equals(MaxVariantPatcher.STORAGE))rms++;if(call.owner.equals("javax/microedition/rms/RecordStore")&&call.getOpcode()==INVOKESTATIC)check(c.name.equals(MaxVariantPatcher.STORAGE),"Unscoped RMS access");}
        }}
        check(writes==5&&selectors==12&&rms==4,"Server/RMS boundary counts: "+writes+"/"+selectors+"/"+rms);
        ClassNode socket=MaxVariantPatcher.node(after.get(p.getProperty("socket.owner")+".class"));socketProbe(socket);
        Common emulator=new Common(null);MemoryRecordStoreManager manager=new MemoryRecordStoreManager();manager.init(emulator);emulator.setRecordStoreManager(manager);MIDletBridge.setMicroEmulator(emulator);
        URLClassLoader loader=new URLClassLoader(new URL[]{new File(args[2]).toURI().toURL()},MaxVariantBinaryTest.class.getClassLoader());int links=0;
        try{
            for(String name:after.keySet())if(name.endsWith(".class")){Class<?> c=Class.forName(name.substring(0,name.length()-6).replace('/','.'),false,loader);c.getDeclaredMethods();c.getDeclaredFields();c.getDeclaredConstructors();links++;}
            Class<?> tableClass=Class.forName(tables.name,true,loader);String[][] rows=(String[][])tableClass.getMethod(MaxVariantPatcher.FACTORY).invoke(null);check(rows.length==1&&rows[0][0].equals("HsoMax")&&rows[0][1].equals(MaxVariantPatcher.HOST),"Actual singleton server factory");
            write("login",91);write("HSO_CHIBI_login",92);write("unrelated_settings",93);
            Class<?> storage=Class.forName(MaxVariantPatcher.STORAGE.replace('/','.'),true,loader);
            check(storage.getMethod("listRecordStores").invoke(null)==null,"Fresh MAX data namespace");
            Class<?> midlet=Class.forName("TemMidlet",true,loader);Method save=null,load=null;
            for(Method m:midlet.getDeclaredMethods())if(Modifier.isStatic(m.getModifiers())){if(Arrays.equals(m.getParameterTypes(),new Class<?>[]{String.class,byte[].class}))save=m;if(Arrays.equals(m.getParameterTypes(),new Class<?>[]{String.class})&&m.getReturnType()==byte[].class)load=m;}
            check(save!=null&&load!=null,"Native account codec symbols");save.setAccessible(true);load.setAccessible(true);
            check(load.invoke(null,"login")==null,"Native account does not import old endpoint");byte[] data={3,5,7,11};save.invoke(null,"login",data.clone());check(Arrays.equals(data,(byte[])load.invoke(null,"login")),"Actual native account roundtrip");
            check(read("login")==91&&read("HSO_CHIBI_login")==92,"Old account stores untouched");
            String[] names=(String[])storage.getMethod("listRecordStores").invoke(null);check(Arrays.equals(names,new String[]{"login"}),"Scoped listing exposes logical names");
            RecordStore prefixed=(RecordStore)storage.getMethod("openRecordStore",String.class,boolean.class).invoke(null,"HSO_MAX_login",false);prefixed.closeRecordStore();
            storage.getMethod("deleteRecordStore",String.class).invoke(null,"login");check(read("login")==91&&read("HSO_CHIBI_login")==92&&read("unrelated_settings")==93,"Scoped delete preserves other suites");
        }finally{loader.close();}
        System.out.println("MAX_BINARY=PASS; checks="+checks+"; fullLoginPackets=5; linkedClasses="+links+"; unchangedClasses="+unchanged+"; unchangedResources="+resources+"; tableWrites="+writes+"; selectorWrites="+selectors+"; actualSocketURI="+uri+"; nativeAccountRMS=isolated");
    }
    static Class<?> serializer(ClassNode source,String name,boolean candidate,String probeName){
        MethodNode original=MaxVariantPatcher.method(source,name,MaxVariantPatcher.LOGIN);check(MaxVariantPatcher.utf(original).size()==7,"Seven donor-compatible UTF writes");
        ClassNode probe=new ClassNode();probe.version=V1_3;probe.access=ACC_PUBLIC;probe.name=probeName;probe.superName="java/lang/Object";
        MethodNode m=new MethodNode(ACC_PUBLIC|ACC_STATIC,"packet","(Ljava/lang/Object;"+MaxVariantPatcher.LOGIN.substring(1),null,null);Map<LabelNode,LabelNode> labels=new IdentityHashMap<LabelNode,LabelNode>();for(AbstractInsnNode n:original.instructions.toArray())if(n instanceof LabelNode)labels.put((LabelNode)n,new LabelNode());int utf=0,z=0;
        for(AbstractInsnNode n:original.instructions.toArray()){
            if(n instanceof FrameNode)continue;
            if(n instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)n;
                if(f.getOpcode()==GETFIELD){m.instructions.add(new InsnNode(POP));if(f.desc.equals("Ljava/io/DataOutputStream;"))m.instructions.add(new FieldInsnNode(GETSTATIC,SELF,"out",f.desc));else m.instructions.add(new InsnNode(ACONST_NULL));}
                else if(f.getOpcode()==GETSTATIC){String mapped;
                    if(!candidate&&f.owner.equals("ft")&&f.name.equals("G")){m.instructions.add(new IntInsnNode(BIPUSH,80));continue;}
                    if(!candidate&&f.owner.equals("ft")&&f.name.equals("I")){m.instructions.add(new IntInsnNode(SIPUSH,2389));continue;}
                    if(f.desc.equals("I"))mapped="deviceInt";else if(f.desc.equals("B"))mapped="deviceByte";else if(f.desc.equals("Z"))mapped=z++==0?"deviceA":"deviceB";else if(f.desc.equals("Ljava/lang/String;"))mapped="deviceInfo";else throw new IllegalStateException("Unexpected device "+f.desc);
                    m.instructions.add(new FieldInsnNode(GETSTATIC,SELF,mapped,f.desc.equals("B")?"I":f.desc));
                }else throw new IllegalStateException("Unexpected serializer field");
            }else if(n instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)n;
                if(c.desc.equals("(B)V")){m.instructions.add(new MethodInsnNode(INVOKESTATIC,SELF,"begin","(Ljava/lang/Object;B)V",false));}
                else if(c.desc.equals("()Ljava/io/DataOutputStream;"))m.instructions.add(new MethodInsnNode(INVOKESTATIC,SELF,"stream","(Ljava/lang/Object;)Ljava/io/DataOutputStream;",false));
                else {check(c.owner.startsWith("java/"),"Unexpected serializer call "+c.owner);m.instructions.add(n.clone(labels));if(c.owner.equals("java/io/DataOutputStream")&&c.name.equals("writeUTF")&&++utf==7)break;}
            }else m.instructions.add(n.clone(labels));
        }
        m.instructions.add(new InsnNode(RETURN));probe.methods.add(m);ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);probe.accept(w);return new Loader().define(probeName,w.toByteArray());
    }
    static byte[] packet(Class<?> probe,String user,String pass,String version,String key,int selector,byte flag)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();out=new DataOutputStream(bytes);probe.getMethod("packet",Object.class,String.class,String.class,String.class,String.class,String.class,String.class,int.class,byte.class).invoke(null,new Object(),user,pass,version,key,key,key,selector,flag);out.flush();return bytes.toByteArray();}
    static void socketProbe(ClassNode source)throws Exception{String name="MaxSocketProbe";ClassNode p=new ClassNode();p.version=V1_3;p.access=ACC_PUBLIC;p.name=name;p.superName="java/lang/Object";for(FieldNode f:source.fields)p.fields.add(new FieldNode(f.access,f.name,f.desc,null,f.value));MethodNode m=MaxVariantPatcher.method(source,"<init>","(Ljava/lang/String;I)V");for(AbstractInsnNode n:m.instructions.toArray()){if(n instanceof FieldInsnNode&&((FieldInsnNode)n).owner.equals(source.name))((FieldInsnNode)n).owner=name;if(n instanceof MethodInsnNode&&((MethodInsnNode)n).owner.equals("javax/microedition/io/Connector")){MethodInsnNode c=(MethodInsnNode)n;c.owner=SELF;c.name="open";}}p.methods.add(m);ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);p.accept(w);Class<?> c=new Loader().define(name,w.toByteArray());c.getConstructor(String.class,int.class).newInstance("old.invalid",1);check("socket://sv.hsomax.com:19129".equals(uri),"Actual socket constructor endpoint");}
    static void write(String name,int value)throws Exception{RecordStore s=RecordStore.openRecordStore(name,true);try{s.addRecord(new byte[]{(byte)value},0,1);}finally{s.closeRecordStore();}}
    static int read(String name)throws Exception{RecordStore s=RecordStore.openRecordStore(name,false);try{return s.getRecord(1)[0]&255;}finally{s.closeRecordStore();}}
    static void check(boolean value,String message){checks++;if(!value)throw new IllegalStateException(message);}
}
