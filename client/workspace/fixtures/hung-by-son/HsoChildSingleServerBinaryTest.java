import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.util.MemoryRecordStoreManager;

/** Checks all table/selector writers and interprets old account/server data safely. */
public final class HsoChildSingleServerBinaryTest implements Opcodes {
    private static int checks;
    public static void main(String[] args)throws Exception {
        Map<String,byte[]> jar=HungBySonChildLoginPatcher.read(new File(args[1]));
        Properties p=new Properties();Reader r=new InputStreamReader(new FileInputStream(args[2]),"UTF-8");try{p.load(r);}finally{r.close();}
        String namespace=args.length>3&&!args[3].equals("root")?args[3]:"",listOwner=namespace+p.getProperty("server.list.owner"),selectorOwner=namespace+p.getProperty("selector.owner"),selectorName=p.getProperty("selector.name");
        Set<String> tables=new HashSet<String>(Arrays.asList(p.getProperty("server.list.fields").split(",")));
        int tableWrites=0,selectorWrites=0;
        for(Map.Entry<String,byte[]> entry:jar.entrySet())if(entry.getKey().endsWith(".class")) {
            ClassNode c=new ClassNode();new ClassReader(entry.getValue()).accept(c,0);
            for(MethodNode m:c.methods)if(!m.name.equals("clears"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTSTATIC) {
                FieldInsnNode f=(FieldInsnNode)n;
                if(f.owner.equals(listOwner)&&tables.contains(f.name)) {
                    AbstractInsnNode previous=HsoChildSingleServerPatcher.prev(n);check(previous instanceof MethodInsnNode,"Server-table bypass");
                    MethodInsnNode call=(MethodInsnNode)previous;check(call.getOpcode()==INVOKESTATIC&&call.owner.equals(listOwner)&&call.name.equals(HsoChildSingleServerPatcher.FACTORY),"Server list can be repopulated");tableWrites++;
                }
                if(f.owner.equals(selectorOwner)&&f.name.equals(selectorName)) {
                    check(HsoChildSingleServerPatcher.prev(n).getOpcode()==ICONST_0&&HsoChildSingleServerPatcher.prev(HsoChildSingleServerPatcher.prev(n)).getOpcode()==POP,"Selector assignment can exceed singleton bounds");selectorWrites++;
                }
            }
        }
        check(tableWrites==Integer.parseInt(p.getProperty("server.table.writes"))&&selectorWrites==Integer.parseInt(p.getProperty("server.selector.writes")),"Writer topology differs");
        Common emulator=new Common(null);MemoryRecordStoreManager manager=new MemoryRecordStoreManager();manager.init(emulator);emulator.setRecordStoreManager(manager);MIDletBridge.setMicroEmulator(emulator);
        URLClassLoader baseline=new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()},HsoChildSingleServerBinaryTest.class.getClassLoader());
        byte[] account={12,34,56,78};
        try {
            Class<?> original=Class.forName("com.silverknight.TemMidlet",true,baseline);
            Method save=original.getMethod("a",String.class,byte[].class);
            save.invoke(null,"user_pass",account.clone());save.invoke(null,"local_server",new byte[]{8});save.invoke(null,"isIndexServer",new byte[]{8});
            check(Arrays.equals((byte[])original.getMethod("a",String.class).invoke(null,"isIndexServer"),new byte[]{8}),"Old fixture not persisted");
        }finally{baseline.close();}
        URLClassLoader loader=new URLClassLoader(new URL[]{new File(args[1]).toURI().toURL()},HsoChildSingleServerBinaryTest.class.getClassLoader());
        try {
            Class<?> list=Class.forName(listOwner.replace('/','.'),true,loader);
            Class<?> refresh=Class.forName((namespace+p.getProperty("server.refresh.owner")).replace('/','.'),true,loader);
            refresh.getMethod(p.getProperty("server.refresh.method"),String.class,boolean.class).invoke(null,"Old:127.0.0.1:19129:0,Another:127.0.0.2:19129:0,",true);
            for(String name:tables) {
                String[][] values=(String[][])list.getField(name).get(null);
                check(values.length==1&&values[0].length==2&&HsoChildSingleServerPatcher.LABEL.equals(values[0][0])&&HungBySonChildLoginPatcher.HOST.equals(values[0][1]),"Wrong sole server row");
            }
            Class<?> config=Class.forName(selectorOwner.replace('/','.'),true,loader);
            check(config.getField(selectorName).getByte(null)==0,"Wrong startup selector");
            int metadata=0;for(Field f:config.getDeclaredFields())if(f.getType()==int[].class&&(f.getModifiers()&Modifier.STATIC)!=0) {
                f.setAccessible(true);int[] a=(int[])f.get(null);if(a!=null&&a.length==1&&(a[0]==19129||a[0]==0))metadata++;
            }
            check(metadata==2,"Port/language metadata not collapsed");
            String midlet=namespace.isEmpty()?"TemMidlet":namespace+"TemMidlet";
            if(args.length<=3)midlet="com.silverknight.TemMidlet";
            Class<?> current=Class.forName(midlet.replace('/','.'),true,loader);Method load=current.getMethod("a",String.class);
            check(((byte[])load.invoke(null,"local_server"))[0]==0&&((byte[])load.invoke(null,"isIndexServer"))[0]==0,"Old selected index is not normalized");
            if(args.length<=3)check(Arrays.equals((byte[])load.invoke(null,"user_pass"),account),"Existing account changed");
        }finally{loader.close();}
        System.out.println("SINGLE_SERVER_BINARY=PASS; "+checks+" checks; "+tableWrites+" table writers, "+selectorWrites+" selector writers, ignored remote multi-row update, sole row0, metadata and saved-index compatibility; namespace="+(namespace.isEmpty()?"root":namespace));
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new IllegalStateException(message);}
}
