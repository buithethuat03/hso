import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Executes candidate instruction slices with only transport/UI dependencies replaced. */
public final class HungBySonChildLoginBinaryTest implements Opcodes {
    private static ByteArrayOutputStream payload;
    private static DataOutputStream stream;
    private static int opcode, port, connects, checks;
    private static String host;
    private static boolean connected;
    public static void begin(Object receiver, int code) { opcode=code;payload=new ByteArrayOutputStream();stream=new DataOutputStream(payload); }
    public static DataOutputStream stream() { return stream; }
    public static Object session() { return new Object(); }
    public static boolean connected(Object receiver) { return connected; }
    public static void connect(Object receiver,String value,int number) { host=value;port=number;connects++; }
    private static final String TEST="HungBySonChildLoginBinaryTest";
    private static final class ProbeLoader extends ClassLoader {
        ProbeLoader(){super(HungBySonChildLoginBinaryTest.class.getClassLoader());}
        Class<?> define(String name,byte[] bytes){return defineClass(name,bytes,0,bytes.length);}
    }
    public static void main(String[] args)throws Exception {
        Map<String,byte[]> before=HungBySonChildLoginPatcher.read(new File(args[0]));
        Map<String,byte[]> after=HungBySonChildLoginPatcher.read(new File(args[1]));
        Properties map=new Properties();Reader reader=new InputStreamReader(new FileInputStream(args[2]),"UTF-8");try{map.load(reader);}finally{reader.close();}
        int serverIndex=Integer.parseInt(map.getProperty("server.index","8"));
        int serverCount=Integer.parseInt(map.getProperty("server.count","10"));
        Properties candidateMap=new Properties();candidateMap.putAll(map);
        if(args.length>3)for(String key:new String[]{"login.owner","selector.owner","connection.owner","server.list.owner"})candidateMap.setProperty(key,args[3]+map.getProperty(key));
        Class<?> baseline=makeLoginProbe(before,map,"BaselineLoginProbe");
        Class<?> candidate=makeLoginProbe(after,candidateMap,"CandidateLoginProbe");
        int[] selectors={-128,-1,0,1,2,3,4,5,6,7,8,9,10,127};
        String[] inputs={"fixture-user","fixture-password","4.0.2","0","0","0"};
        for(int selected:selectors) {
            byte[] old=packet(baseline,selected,inputs),actual=packet(candidate,selected,inputs);
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(actual));
            for(int field=0;field<6;field++) {
                String expected=selected==serverIndex&&field>=3?HungBySonChildLoginPatcher.KEY:inputs[field];
                check(expected.equals(in.readUTF()),"Incorrect UTF field "+(field+1)+" for selector "+selected);
            }
            check(in.available()==0,"Extra header fields");check(opcode==1,"Opcode changed");
            check(selected==serverIndex?actual.length==old.length+66:Arrays.equals(actual,old),"Unexpected payload delta");
        }
        String[] arbitrary={"fixture-user","fixture-password","2.5.8","four","five","six"};
        check(Arrays.equals(packet(baseline,9,arbitrary),packet(candidate,9,arbitrary)),"Other servers lose caller extension fields");
        byte[] keyed=packet(candidate,serverIndex,arbitrary);
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(keyed));
        check(in.readUTF().equals(arbitrary[0])&&in.readUTF().equals(arbitrary[1])&&in.readUTF().equals(arbitrary[2]),"Caller username/password/version changed");
        for(int i=0;i<3;i++)check(HungBySonChildLoginPatcher.KEY.equals(in.readUTF()),"Key missing for nonzero input");
        URLClassLoader loader=new URLClassLoader(new URL[]{new File(args[1]).toURI().toURL()},HungBySonChildLoginBinaryTest.class.getClassLoader());
        String[][] list=null;
        try {
            Class<?> owner=Class.forName(candidateMap.getProperty("server.list.owner").replace('/','.'),true,loader);
            for(Field f:owner.getDeclaredFields())if(f.getType()==String[][].class) {
                String[][] values=(String[][])f.get(null);
                if(values!=null&&values.length>serverIndex&&map.getProperty("server.label","Server L\u1eadu").equals(values[serverIndex][0]))list=values;
            }
        }finally{loader.close();}
        check(list!=null&&list.length==serverCount,"Server list changed");check(HungBySonChildLoginPatcher.HOST.equals(list[serverIndex][1]),"Server Lau list IP not patched");
        Class<?> oldConnect=makeConnectProbe(before,map,"BaselineConnectProbe");
        Class<?> newConnect=makeConnectProbe(after,candidateMap,"CandidateConnectProbe");
        for(boolean locale:new boolean[]{false,true})for(int selected=0;selected<serverCount;selected++) {
            Object[] oldResult=connection(oldConnect,selected,locale,list,false);
            Object[] newResult=connection(newConnect,selected,locale,list,false);
            if(selected==serverIndex) {
                check(HungBySonChildLoginPatcher.HOST.equals(newResult[0]),"Direct connection IP differs from list");
                check(Integer.valueOf(19129).equals(newResult[1]),"Wrong CHILD port");
                check("4.0.2".equals(newResult[2]),"Empty special-endpoint version");
            }else check(Arrays.equals(oldResult,newResult),"Other connection changed: "+selected);
            check(Integer.valueOf(1).equals(newResult[3]),"Connect count changed");
        }
        Object[] active=connection(newConnect,serverIndex,false,list,true);
        check(Integer.valueOf(0).equals(active[3]),"Already-connected guard removed");
        System.out.println("LOGIN_ENDPOINT_BINARY=PASS; "+checks+" assertions; actual six-UTF writer, +66 bytes, other-server parity, native direct socket branch and active-session guard");
    }
    private static Class<?> makeLoginProbe(Map<String,byte[]> jar,Properties map,String name)throws Exception {
        ClassNode original=node(jar.get(map.getProperty("login.owner")+".class"));
        MethodNode source=method(original,map.getProperty("login.method"),HungBySonChildLoginPatcher.LOGIN_DESC);
        MethodNode copy=new MethodNode(ACC_PUBLIC,"login",source.desc,null,null);
        Map<LabelNode,LabelNode> labels=labels(source);
        int writes=0,fields=0;
        for(AbstractInsnNode n:source.instructions.toArray()) {
            if(n instanceof FrameNode)continue;
            if(n instanceof FieldInsnNode) {
                FieldInsnNode f=(FieldInsnNode)n;
                if(f.getOpcode()==GETSTATIC&&f.owner.equals(map.getProperty("selector.owner"))&&f.name.equals(map.getProperty("selector.name")))copy.instructions.add(new FieldInsnNode(GETSTATIC,name,"selected","B"));
                else {check(f.getOpcode()==GETFIELD,"Unexpected dependency before header");copy.instructions.add(new InsnNode(POP));fields++;}
            }else if(n instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)n;
                if(call.owner.equals("java/io/DataOutputStream")) {
                    check(call.name.equals("writeUTF"),"Non-UTF header operation");copy.instructions.add(n.clone(labels));writes++;
                }else if(call.desc.equals("(B)V"))copy.instructions.add(new MethodInsnNode(INVOKESTATIC,TEST,"begin","(Ljava/lang/Object;I)V",false));
                else {check(call.desc.equals("()Ljava/io/DataOutputStream;"),"Unknown packet producer");copy.instructions.add(new MethodInsnNode(INVOKESTATIC,TEST,"stream",call.desc,false));}
            }else copy.instructions.add(n.clone(labels));
            if(writes==6)break;
        }
        check(writes==6&&fields==6,"Header slice shape changed");copy.instructions.add(new InsnNode(RETURN));
        ClassNode probe=probe(name);probe.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC,"selected","B",null,null));probe.methods.add(copy);
        return define(probe);
    }
    private static Class<?> makeConnectProbe(Map<String,byte[]> jar,Properties map,String name)throws Exception {
        ClassNode original=node(jar.get(map.getProperty("connection.owner")+".class"));
        MethodNode source=method(original,map.getProperty("connection.method"),"()V");
        MethodNode copy=new MethodNode(ACC_PUBLIC|ACC_STATIC,"connect","()V",null,null);Map<LabelNode,LabelNode> labels=labels(source);
        int strings=0;
        for(AbstractInsnNode n:source.instructions.toArray()) {
            if(n instanceof FrameNode)continue;
            if(n instanceof FieldInsnNode) {
                FieldInsnNode f=(FieldInsnNode)n;
                if(f.getOpcode()==PUTSTATIC) {
                    if(f.desc.equals("Ljava/lang/String;"))copy.instructions.add(new FieldInsnNode(PUTSTATIC,name,"version",f.desc));
                    else copy.instructions.add(new InsnNode(POP));
                }else if(f.desc.equals("B"))copy.instructions.add(new FieldInsnNode(GETSTATIC,name,"selected","B"));
                else if(f.desc.equals("Z"))copy.instructions.add(new FieldInsnNode(GETSTATIC,name,"locale","Z"));
                else if(f.desc.equals("[[Ljava/lang/String;"))copy.instructions.add(new FieldInsnNode(GETSTATIC,name,"list",f.desc));
                else if(f.desc.equals("Ljava/lang/String;")) {copy.instructions.add(new LdcInsnNode(""));strings++;}
                else {check(f.desc.equals("I"),"Unknown connect field");copy.instructions.add(new InsnNode(ICONST_0));}
            }else if(n instanceof MethodInsnNode) {
                MethodInsnNode m=(MethodInsnNode)n;
                if(m.getOpcode()==INVOKESTATIC)copy.instructions.add(new MethodInsnNode(INVOKESTATIC,TEST,"session","()Ljava/lang/Object;",false));
                else if(m.desc.equals("()Z"))copy.instructions.add(new MethodInsnNode(INVOKESTATIC,TEST,"connected","(Ljava/lang/Object;)Z",false));
                else {check(m.desc.equals("(Ljava/lang/String;I)V"),"Unexpected native connection call");copy.instructions.add(new MethodInsnNode(INVOKESTATIC,TEST,"connect","(Ljava/lang/Object;Ljava/lang/String;I)V",false));}
            }else copy.instructions.add(n.clone(labels));
        }
        ClassNode probe=probe(name);
        for(String[] f:new String[][]{{"selected","B"},{"locale","Z"},{"list","[[Ljava/lang/String;"},{"version","Ljava/lang/String;"}})probe.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC,f[0],f[1],null,null));
        probe.methods.add(copy);return define(probe);
    }
    private static byte[] packet(Class<?> probe,int selected,String[] fields)throws Exception {
        probe.getField("selected").setByte(null,(byte)selected);Object receiver=probe.getConstructor().newInstance();
        probe.getMethod("login",String.class,String.class,String.class,String.class,String.class,String.class,int.class,byte.class).invoke(receiver,fields[0],fields[1],fields[2],fields[3],fields[4],fields[5],-1,(byte)-1);
        stream.flush();return payload.toByteArray();
    }
    private static Object[] connection(Class<?> probe,int selected,boolean locale,String[][] list,boolean active)throws Exception {
        probe.getField("selected").setByte(null,(byte)selected);probe.getField("locale").setBoolean(null,locale);probe.getField("list").set(null,list);probe.getField("version").set(null,"unchanged");
        host=null;port=0;connects=0;connected=active;probe.getMethod("connect").invoke(null);
        return new Object[]{host,Integer.valueOf(port),probe.getField("version").get(null),Integer.valueOf(connects)};
    }
    private static ClassNode probe(String name) {
        ClassNode c=new ClassNode();c.version=V1_3;c.access=ACC_PUBLIC;c.name=name;c.superName="java/lang/Object";
        MethodNode ctor=new MethodNode(ACC_PUBLIC,"<init>","()V",null,null);ctor.instructions.add(new VarInsnNode(ALOAD,0));ctor.instructions.add(new MethodInsnNode(INVOKESPECIAL,"java/lang/Object","<init>","()V",false));ctor.instructions.add(new InsnNode(RETURN));c.methods.add(ctor);return c;
    }
    private static Class<?> define(ClassNode c){ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);return new ProbeLoader().define(c.name,writer.toByteArray());}
    private static ClassNode node(byte[] bytes){ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);return c;}
    private static MethodNode method(ClassNode c,String name,String desc){for(MethodNode m:c.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;throw new IllegalStateException("Method missing");}
    private static Map<LabelNode,LabelNode> labels(MethodNode m){Map<LabelNode,LabelNode> map=new IdentityHashMap<LabelNode,LabelNode>();for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof LabelNode)map.put((LabelNode)n,new LabelNode());return map;}
    private static void check(boolean okay,String message){checks++;if(!okay)throw new IllegalStateException(message);}
}
