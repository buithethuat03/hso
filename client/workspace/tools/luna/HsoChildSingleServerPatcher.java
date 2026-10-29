import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Collapses every native server-table assignment and persisted selector to CHILD. */
public final class HsoChildSingleServerPatcher implements Opcodes {
    public static final String EXPECTED="DABB9D4AD55D46DFC00D6E18441C1D15AE83A101344E0335F1649C55A9E82E90";
    public static final String FACTORY="hsoChildOnlyServer";
    public static final String LABEL="Server l\u1eadu";
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("input.jar login-symbols.properties output.jar new-symbols.properties");
        File input=new File(args[0]),output=new File(args[2]);
        check(EXPECTED.equals(HungBySonChildLoginPatcher.hash(input)),"Unexpected input");check(!output.exists(),"Output exists");
        Properties p=new Properties();Reader r=new InputStreamReader(new FileInputStream(args[1]),"UTF-8");try{p.load(r);}finally{r.close();}
        Map<String,byte[]> before=HungBySonChildLoginPatcher.read(input),after=new LinkedHashMap<String,byte[]>(before);
        Map<String,ClassNode> nodes=new LinkedHashMap<String,ClassNode>();for(Map.Entry<String,byte[]> e:before.entrySet())if(e.getKey().endsWith(".class")){ClassNode c=new ClassNode();new ClassReader(e.getValue()).accept(c,0);nodes.put(c.name,c);}
        String listOwner=p.getProperty("server.list.owner"),selectorOwner=p.getProperty("selector.owner"),selectorName=p.getProperty("selector.name");
        ClassNode list=nodes.get(listOwner),config=nodes.get(selectorOwner),login=nodes.get(p.getProperty("login.owner"));
        check(list!=null&&config!=null&&login!=null,"Missing exact native symbols");
        Set<String> tables=new LinkedHashSet<String>();for(FieldNode f:list.fields)if((f.access&ACC_STATIC)!=0&&"[[Ljava/lang/String;".equals(f.desc))tables.add(f.name);
        check(tables.size()==2,"Expected two server tables");
        for(MethodNode m:list.methods)check(!m.name.equals(FACTORY),"Already patched");
        MethodNode factory=new MethodNode(ACC_PUBLIC|ACC_STATIC,FACTORY,"()[[Ljava/lang/String;",null,null);
        singleton(factory.instructions);factory.instructions.add(new InsnNode(ARETURN));list.methods.add(factory);
        Set<String> changed=new LinkedHashSet<String>();changed.add(listOwner);
        int refreshParsers=0;
        for(ClassNode c:nodes.values())for(MethodNode m:c.methods)if(m.desc.equals("(Ljava/lang/String;Z)V")&&HungBySonChildLoginPatcher.hasConstant(m,"listServer")) {
            boolean writesTable=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTSTATIC){FieldInsnNode f=(FieldInsnNode)n;if(f.owner.equals(listOwner)&&tables.contains(f.name))writesTable=true;}
            if(!writesTable)continue;
            // The original parser already returns for the reachable selector 0.
            // Remove the cached/remote multi-row mutation path altogether.
            m.instructions.clear();m.instructions.add(new InsnNode(RETURN));m.tryCatchBlocks.clear();
            if(m.localVariables!=null)m.localVariables.clear();m.maxStack=0;m.maxLocals=2;
            p.setProperty("server.refresh.owner",c.name);p.setProperty("server.refresh.method",m.name);p.setProperty("server.refresh.descriptor",m.desc);
            changed.add(c.name);refreshParsers++;
        }
        check(refreshParsers==1,"Expected unique cached/remote list parser");
        int listFetches=0;
        for(ClassNode c:nodes.values()) {
            boolean listScreen=c.name.equals(p.getProperty("server.refresh.owner"));
            for(MethodNode m:c.methods)if(HungBySonChildLoginPatcher.hasConstant(m,"List_Ip_Server_Screen vecchoice"))listScreen=true;
            if(!listScreen)continue;
            for(MethodNode m:c.methods)if(m.name.equals("<init>"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)n;
                if(call.owner.equals("com/silverknight/TemMidlet")&&call.name.equals("c")&&call.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                    InsnNode discard=new InsnNode(POP);m.instructions.set(n,discard);m.instructions.insert(discard,new InsnNode(ACONST_NULL));changed.add(c.name);listFetches++;
                }
            }
        }
        check(listFetches==2,"Expected two list-only HTTP fetch sites");
        int tableWrites=0,selectorWrites=0,initialTables=0;
        for(ClassNode c:nodes.values())for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTSTATIC) {
            FieldInsnNode f=(FieldInsnNode)n;
            if(f.owner.equals(listOwner)&&tables.contains(f.name)&&f.desc.equals("[[Ljava/lang/String;")) {
                if(c.name.equals(listOwner)&&m.name.equals("<clinit>")) {
                    AbstractInsnNode allocation=prev(n);
                    while(allocation!=null&&!(allocation instanceof TypeInsnNode&&allocation.getOpcode()==ANEWARRAY&&((TypeInsnNode)allocation).desc.equals("[Ljava/lang/String;")))allocation=prev(allocation);
                    check(allocation!=null&&integer(prev(allocation))>1,"Native table initializer changed");
                    AbstractInsnNode start=prev(allocation);
                    for(AbstractInsnNode cursor=start;cursor!=n;) {check(!(cursor instanceof JumpInsnNode),"Branch in server initializer");AbstractInsnNode following=cursor.getNext();m.instructions.remove(cursor);cursor=following;}
                    m.instructions.insertBefore(n,new MethodInsnNode(INVOKESTATIC,listOwner,FACTORY,"()[[Ljava/lang/String;",false));initialTables++;
                }else {
                    InsnList only=new InsnList();only.add(new InsnNode(POP));only.add(new MethodInsnNode(INVOKESTATIC,listOwner,FACTORY,"()[[Ljava/lang/String;",false));m.instructions.insertBefore(n,only);
                }
                tableWrites++;changed.add(c.name);
            }
            if(f.owner.equals(selectorOwner)&&f.name.equals(selectorName)&&f.desc.equals("B")) {
                InsnList reset=new InsnList();reset.add(new InsnNode(POP));reset.add(new InsnNode(ICONST_0));m.instructions.insertBefore(n,reset);selectorWrites++;changed.add(c.name);
            }
        }
        check(initialTables==2&&tableWrites>=4&&selectorWrites>=4,"Native assignment topology changed");
        // Port/language arrays retain exactly the old custom-server metadata at row 0.
        int metadata=0;
        MethodNode init=method(config,"<clinit>","()V");
        for(AbstractInsnNode n:init.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTSTATIC&&((FieldInsnNode)n).desc.equals("[I")) {
            AbstractInsnNode allocation=prev(n);while(allocation!=null&&allocation.getOpcode()!=NEWARRAY&&allocation.getOpcode()!=PUTSTATIC)allocation=prev(allocation);
            if(!(allocation instanceof IntInsnNode)||((IntInsnNode)allocation).operand!=T_INT||integer(prev(allocation))!=10)continue;
            int value=0;AbstractInsnNode cursor=next(allocation);
            while(cursor!=n) {check(cursor.getOpcode()==DUP,"Metadata shape");AbstractInsnNode index=next(cursor),item=next(index),store=next(item);check(store.getOpcode()==IASTORE,"Metadata value store");if(integer(index)==8)value=integer(item);cursor=next(store);}
            AbstractInsnNode start=prev(allocation);for(cursor=start;cursor!=n;) {AbstractInsnNode following=cursor.getNext();init.instructions.remove(cursor);cursor=following;}
            InsnList one=new InsnList();one.add(new InsnNode(ICONST_1));one.add(new IntInsnNode(NEWARRAY,T_INT));one.add(new InsnNode(DUP));one.add(new InsnNode(ICONST_0));one.add(new IntInsnNode(SIPUSH,value));one.add(new InsnNode(IASTORE));init.instructions.insertBefore(n,one);metadata++;
        }
        check(metadata==2,"Expected two metadata arrays");
        MethodNode connect=method(config,p.getProperty("connection.method"),"()V");
        check(reindexGuard(connect,selectorOwner,selectorName)==1,"Missing direct socket selector guard");
        MethodNode serialize=method(login,p.getProperty("login.method"),p.getProperty("login.desc"));
        check(reindexGuard(serialize,selectorOwner,selectorName)==1,"Missing login key selector guard");changed.add(config.name);changed.add(login.name);
        // The old isIndexServer record may still contain 8. Normalize its interpretation
        // before login code compares it, while preserving credential/config record bytes.
        ClassNode midlet=nodes.get("com/silverknight/TemMidlet");MethodNode load=method(midlet,"a","(Ljava/lang/String;)[B");
        InsnList saved=new InsnList();LabelNode zero=new LabelNode(),original=new LabelNode();
        saved.add(new LdcInsnNode("local_server"));saved.add(new VarInsnNode(ALOAD,0));saved.add(new MethodInsnNode(INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));saved.add(new JumpInsnNode(IFNE,zero));
        saved.add(new LdcInsnNode("isIndexServer"));saved.add(new VarInsnNode(ALOAD,0));saved.add(new MethodInsnNode(INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));saved.add(new JumpInsnNode(IFEQ,original));
        saved.add(zero);saved.add(new InsnNode(ICONST_1));saved.add(new IntInsnNode(NEWARRAY,T_BYTE));saved.add(new InsnNode(ARETURN));saved.add(original);load.instructions.insert(saved);changed.add(midlet.name);
        for(String owner:changed) {ClassWriter w=new ClassWriter(new ClassReader(before.get(owner+".class")),ClassWriter.COMPUTE_MAXS);nodes.get(owner).accept(w);after.put(owner+".class",w.toByteArray());}
        write(input,output,after);
        p.setProperty("input.sha256",EXPECTED);p.setProperty("output.sha256",HungBySonChildLoginPatcher.hash(output));p.setProperty("server.index","0");p.setProperty("server.label",LABEL);p.setProperty("server.count","1");
        p.setProperty("server.list.factory",FACTORY);p.setProperty("server.list.fields",join(tables));p.setProperty("server.table.writes",String.valueOf(tableWrites));p.setProperty("server.selector.writes",String.valueOf(selectorWrites));p.setProperty("server.refresh.disabled","true");p.setProperty("server.list.http.disabled",String.valueOf(listFetches));p.setProperty("changed.classes",join(changed));
        Writer out=new OutputStreamWriter(new FileOutputStream(args[3]),"UTF-8");try{p.store(out,"Single HSOCHILD server + compatible saved selector interpretation");}finally{out.close();}
        System.out.println("SINGLE_SERVER=PASS; row0="+LABEL+"; table assignments="+tableWrites+"; selector assignments="+selectorWrites+"; metadata=2; legacy saved index normalized; classes="+changed.size());
        System.out.println("SHA256="+p.getProperty("output.sha256"));
    }
    public static int reindexGuard(MethodNode m,String owner,String name) {int count=0;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof JumpInsnNode&&n.getOpcode()==IF_ICMPNE&&integer(prev(n))==8&&prev(prev(n)) instanceof FieldInsnNode) {FieldInsnNode f=(FieldInsnNode)prev(prev(n));if(f.owner.equals(owner)&&f.name.equals(name)){m.instructions.set(prev(n),new InsnNode(ICONST_0));count++;}}return count;}
    public static void singleton(InsnList b) {b.add(new InsnNode(ICONST_1));b.add(new TypeInsnNode(ANEWARRAY,"[Ljava/lang/String;"));b.add(new InsnNode(DUP));b.add(new InsnNode(ICONST_0));b.add(new InsnNode(ICONST_2));b.add(new TypeInsnNode(ANEWARRAY,"java/lang/String"));b.add(new InsnNode(DUP));b.add(new InsnNode(ICONST_0));b.add(new LdcInsnNode(LABEL));b.add(new InsnNode(AASTORE));b.add(new InsnNode(DUP));b.add(new InsnNode(ICONST_1));b.add(new LdcInsnNode(HungBySonChildLoginPatcher.HOST));b.add(new InsnNode(AASTORE));b.add(new InsnNode(AASTORE));}
    public static MethodNode method(ClassNode c,String name,String desc){check(c!=null,"Owner missing");for(MethodNode m:c.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;throw new IllegalStateException("Missing "+name+desc);}
    public static AbstractInsnNode prev(AbstractInsnNode n){return HungBySonChildLoginPatcher.previous(n);}public static AbstractInsnNode next(AbstractInsnNode n){return HungBySonChildLoginPatcher.next(n);}
    public static int integer(AbstractInsnNode n){return HungBySonChildLoginPatcher.intValue(n);}
    public static String join(Collection<String> values){StringBuilder b=new StringBuilder();for(String v:values){if(b.length()>0)b.append(',');b.append(v);}return b.toString();}
    public static void write(File input,File output,Map<String,byte[]> after)throws Exception{JarFile jar=new JarFile(input);JarOutputStream out=new JarOutputStream(new FileOutputStream(output));try{for(Enumeration<JarEntry> e=jar.entries();e.hasMoreElements();){JarEntry old=e.nextElement(),entry=new JarEntry(old.getName());entry.setTime(old.getTime());out.putNextEntry(entry);out.write(after.get(old.getName()));out.closeEntry();}}finally{out.close();jar.close();}}
    public static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
