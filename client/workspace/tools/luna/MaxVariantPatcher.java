import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Pinned CHIBI baseline with the MAX donor's endpoint and opcode-1 contract. */
public final class MaxVariantPatcher implements Opcodes {
    static final String INPUT = "A5C8FC9C5659518E2E62AB702381FD4E781B3C55ABE82F87EA4FF63A0D142DFB";
    static final String DONOR = "B46D256F4304FA36566CB5B4DE4882A15E99069B27CBAD82B217EED254B41BC6";
    static final String HOST = "sv.hsomax.com", LABEL = "HsoMax", SUITE = "HSO MAX";
    static final String KEY = "TUOITHO_SECRET_KEY_2026", VERSION = "2.5.8";
    static final String STORAGE = "luna/core/MaxRecordStores", FACTORY = "maxOnlyServer";
    static final String LOGIN = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IB)V";
    static final int PORT = 19129;
    static Properties report = new Properties();

    public static void main(String[] args) throws Exception {
        require(args.length == 5, "input.jar donor.jar output.jar compiled-support-dir report.properties");
        File input = new File(args[0]), donor = new File(args[1]), output = new File(args[2]);
        require(INPUT.equals(hash(input)) && DONOR.equals(hash(donor)), "Pinned input/donor mismatch");
        require(!output.exists(), "Output already exists");
        Map<String,byte[]> before = read(input), after = new LinkedHashMap<String,byte[]>(before);
        Map<String,ClassNode> nodes = new LinkedHashMap<String,ClassNode>();
        for (Map.Entry<String,byte[]> e : before.entrySet()) if (e.getKey().endsWith(".class")) {
            ClassNode c = node(e.getValue()); nodes.put(c.name,c);
        }
        Map<String,byte[]> reference = read(donor);
        ClassNode donorList = node(reference.get("dw.class"));
        require(has(method(donorList,"<clinit>","()V"),HOST) && has(method(donorList,"<clinit>","()V"),LABEL), "Donor endpoint changed");
        MethodNode donorLogin = method(node(reference.get("q.class")),"a",LOGIN);
        require(utf(donorLogin).size() == 7 && has(method(node(reference.get("k.class")),"<clinit>","()V"),VERSION), "Donor login changed");
        ClassNode list = null, sender = null, socket = null; MethodNode login = null, dial = null;
        for (ClassNode c : nodes.values()) for (MethodNode m : c.methods) {
            if (m.name.equals("<clinit>") && has(m,"HSO CHIBI")) { require(list==null,"Multiple table owners");list=c; }
            if (m.desc.equals(LOGIN)) { require(login==null,"Multiple serializers");sender=c;login=m; }
            if (m.name.equals("<init>") && m.desc.equals("(Ljava/lang/String;I)V") && has(m,"socket://")) { require(dial==null,"Multiple sockets");socket=c;dial=m; }
        }
        require(list!=null && login!=null && dial!=null,"Endpoint/login symbols absent");
        Set<String> changed = new LinkedHashSet<String>();
        int plain=0, encoded=0, labels=0;
        for (ClassNode c : nodes.values()) for (MethodNode m:c.methods) for(AbstractInsnNode n:m.instructions.toArray()) if(n instanceof LdcInsnNode) {
            LdcInsnNode value=(LdcInsnNode)n;
            if ("103.77.173.232".equals(value.cst)) {value.cst=HOST;plain++;changed.add(c.name);}
            else if (xor("103.77.173.232").equals(value.cst)) {value.cst=xor(HOST);encoded++;changed.add(c.name);}
            else if ("HSO CHIBI".equals(value.cst)) {value.cst=LABEL;labels++;changed.add(c.name);}
        }
        require(plain==5 && encoded==1 && labels==2,"Unexpected endpoint constants: "+plain+"/"+encoded+"/"+labels);
        Set<String> tables=new LinkedHashSet<String>();for(FieldNode f:list.fields)if(f.desc.equals("[[Ljava/lang/String;")&&(f.access&ACC_STATIC)!=0)tables.add(f.name);
        require(tables.size()==2,"Native table count");
        MethodNode factory=new MethodNode(ACC_PUBLIC|ACC_STATIC,FACTORY,"()[[Ljava/lang/String;",null,null);
        InsnList b=factory.instructions;
        b.add(new InsnNode(ICONST_1));b.add(new TypeInsnNode(ANEWARRAY,"[Ljava/lang/String;"));b.add(new InsnNode(DUP));b.add(new InsnNode(ICONST_0));b.add(new InsnNode(ICONST_2));b.add(new TypeInsnNode(ANEWARRAY,"java/lang/String"));
        b.add(new InsnNode(DUP));b.add(new InsnNode(ICONST_0));b.add(new LdcInsnNode(LABEL));b.add(new InsnNode(AASTORE));b.add(new InsnNode(DUP));b.add(new InsnNode(ICONST_1));b.add(new LdcInsnNode(HOST));b.add(new InsnNode(AASTORE));b.add(new InsnNode(AASTORE));b.add(new InsnNode(ARETURN));list.methods.add(factory);
        int tableWrites=0;
        for(ClassNode c:nodes.values())for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTSTATIC){FieldInsnNode f=(FieldInsnNode)n;
            if(f.owner.equals(list.name)&&tables.contains(f.name)&&f.desc.equals("[[Ljava/lang/String;")){
                m.instructions.insertBefore(n,new InsnNode(POP));m.instructions.insertBefore(n,new MethodInsnNode(INVOKESTATIC,list.name,FACTORY,"()[[Ljava/lang/String;",false));changed.add(c.name);tableWrites++;
            }
        }
        // Derive the native selected-server byte from the actual table dial path.
        FieldInsnNode selected=null;
        for(ClassNode c:nodes.values())for(MethodNode m:c.methods)if(m.desc.equals("()V")&&hasInt(m,PORT)) {
            boolean readsTable=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode&&((FieldInsnNode)n).owner.equals(list.name)&&((FieldInsnNode)n).desc.equals("[[Ljava/lang/String;"))readsTable=true;
            if(readsTable)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==GETSTATIC){FieldInsnNode f=(FieldInsnNode)n;if(f.owner.equals(c.name)&&f.desc.equals("B")){if(selected!=null)require(selected.owner.equals(f.owner)&&selected.name.equals(f.name),"Ambiguous selected index");selected=f;}}
        }
        require(selected!=null,"Selected-server symbol missing");int selectorWrites=0;
        for(ClassNode c:nodes.values())for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTSTATIC){FieldInsnNode f=(FieldInsnNode)n;if(f.owner.equals(selected.owner)&&f.name.equals(selected.name)&&f.desc.equals("B")){m.instructions.insertBefore(n,new InsnNode(POP));m.instructions.insertBefore(n,new InsnNode(ICONST_0));changed.add(c.name);selectorWrites++;}}
        // Enforce the donor endpoint at the final socket boundary as well.
        InsnList endpoint=new InsnList();endpoint.add(new LdcInsnNode(HOST));endpoint.add(new VarInsnNode(ASTORE,1));endpoint.add(new IntInsnNode(SIPUSH,PORT));endpoint.add(new VarInsnNode(ISTORE,2));dial.instructions.insert(endpoint);changed.add(socket.name);
        List<MethodInsnNode> writes=utf(login);require(writes.size()==8 && has(login,"LOCK"),"Baseline UTF count/tail");
        remove(login,writes.get(6).getNext(),writes.get(7).getNext());
        AbstractInsnNode start=writes.get(4).getNext(),end=writes.get(5);
        remove(login,start,end);
        // Reuse the exact native stream access preceding UTF5, then raw argument6.
        Map<LabelNode,LabelNode> empty=new IdentityHashMap<LabelNode,LabelNode>();
        AbstractInsnNode accessStart=writes.get(3).getNext(),accessEnd=previous(writes.get(4));
        for(AbstractInsnNode n=accessStart;n!=accessEnd;n=n.getNext())if(n.getOpcode()>=0)login.instructions.insertBefore(end,n.clone(empty));
        login.instructions.insertBefore(end,new VarInsnNode(ALOAD,6));
        InsnList params=new InsnList();params.add(new LdcInsnNode(VERSION));params.add(new VarInsnNode(ASTORE,3));for(int i=4;i<=6;i++){params.add(new LdcInsnNode(KEY));params.add(new VarInsnNode(ASTORE,i));}login.instructions.insert(params);
        // MAX explicitly initializes these two login-tail values, independently of TCP port.
        List<FieldInsnNode> tail=new ArrayList<FieldInsnNode>();boolean afterSixth=false;
        for(AbstractInsnNode n:login.instructions.toArray()){if(n==writes.get(5))afterSixth=true;else if(afterSixth&&n instanceof FieldInsnNode&&n.getOpcode()==GETSTATIC)tail.add((FieldInsnNode)n);}
        require(tail.size()>=7 && tail.get(2).desc.equals("B") && tail.get(5).desc.equals("S"),"Login device layout");
        login.instructions.set(tail.get(2),new IntInsnNode(BIPUSH,80));login.instructions.set(tail.get(5),new IntInsnNode(SIPUSH,2389));changed.add(sender.name);
        int rms=0;
        for(ClassNode c:nodes.values())for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)n;if(call.owner.equals("javax/microedition/rms/RecordStore")&&call.getOpcode()==INVOKESTATIC&&Arrays.asList("openRecordStore","deleteRecordStore","listRecordStores").contains(call.name)){call.owner=STORAGE;changed.add(c.name);rms++;}}
        require(rms==4,"Unexpected RMS boundary count "+rms);
        for(String name:changed){ClassWriter writer=new ClassWriter(new ClassReader(before.get(name+".class")),ClassWriter.COMPUTE_MAXS);nodes.get(name).accept(writer);after.put(name+".class",writer.toByteArray());}
        require(!before.containsKey(STORAGE+".class"),"Storage helper collision");after.put(STORAGE+".class",Files.readAllBytes(new File(args[3],STORAGE+".class").toPath()));
        Manifest manifest=new Manifest(new ByteArrayInputStream(before.get("META-INF/MANIFEST.MF")));Attributes a=manifest.getMainAttributes();String entry=a.getValue("MIDlet-1");
        a.putValue("MIDlet-Name",SUITE);a.putValue("MIDlet-Vendor",SUITE);a.putValue("MIDlet-1",SUITE+entry.substring(entry.indexOf(',')));a.putValue("Implementation-Title",SUITE+" Full Map Pickup");a.putValue("Implementation-Vendor",SUITE);a.putValue("HSO-Server-IP",HOST);
        ByteArrayOutputStream mb=new ByteArrayOutputStream();manifest.write(mb);after.put("META-INF/MANIFEST.MF",mb.toByteArray());
        JarFile original=new JarFile(input);JarOutputStream out=new JarOutputStream(new FileOutputStream(output));
        try {for(Enumeration<JarEntry> e=original.entries();e.hasMoreElements();){JarEntry old=e.nextElement(),fresh=new JarEntry(old.getName());fresh.setTime(old.getTime());out.putNextEntry(fresh);out.write(after.get(old.getName()));out.closeEntry();}JarEntry extra=new JarEntry(STORAGE+".class");extra.setTime(0);out.putNextEntry(extra);out.write(after.get(STORAGE+".class"));out.closeEntry();}finally{out.close();original.close();}
        report.setProperty("input.sha256",INPUT);report.setProperty("donor.sha256",DONOR);report.setProperty("output.sha256",hash(output));report.setProperty("server.host",HOST);report.setProperty("server.label",LABEL);report.setProperty("server.port",String.valueOf(PORT));report.setProperty("server.list.owner",list.name);report.setProperty("server.factory",FACTORY);report.setProperty("server.table.writes",String.valueOf(tableWrites));report.setProperty("server.selector.writes",String.valueOf(selectorWrites));report.setProperty("selector.owner",selected.owner);report.setProperty("selector.name",selected.name);report.setProperty("socket.owner",socket.name);report.setProperty("login.owner",sender.name);report.setProperty("login.method",login.name);report.setProperty("login.desc",LOGIN);report.setProperty("changed.classes",join(changed));report.setProperty("added.classes",STORAGE);report.setProperty("rms.prefix","HSO_MAX_");
        Writer rw=new OutputStreamWriter(new FileOutputStream(args[4]),"UTF-8");try{report.store(rw,"Pinned CHIBI to MAX endpoint and login");}finally{rw.close();}
        System.out.println("MAX_PATCH=PASS; endpoint="+HOST+":"+PORT+"; name="+LABEL+"; suite="+SUITE+"; tableWrites="+tableWrites+"; selectorWrites="+selectorWrites+"; changedClasses="+changed.size()+"; SHA256="+hash(output));
    }
    static List<MethodInsnNode> utf(MethodNode m){List<MethodInsnNode> r=new ArrayList<MethodInsnNode>();for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)n;if(c.owner.equals("java/io/DataOutputStream")&&c.name.equals("writeUTF"))r.add(c);}return r;}
    static boolean has(MethodNode m,Object value){for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof LdcInsnNode&&value.equals(((LdcInsnNode)n).cst))return true;return false;}
    static boolean hasInt(MethodNode m,int value){for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof IntInsnNode&&((IntInsnNode)n).operand==value)return true;return false;}
    static AbstractInsnNode previous(AbstractInsnNode n){do{n=n.getPrevious();}while(n!=null&&n.getOpcode()<0);return n;}
    static void remove(MethodNode m,AbstractInsnNode from,AbstractInsnNode end){for(AbstractInsnNode n=from;n!=end;){require(n!=null,"Invalid bytecode range");AbstractInsnNode next=n.getNext();m.instructions.remove(n);n=next;}}
    static MethodNode method(ClassNode c,String name,String desc){for(MethodNode m:c.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;throw new IllegalStateException("Missing method "+name+desc);}
    static ClassNode node(byte[] b){require(b!=null,"Missing class");ClassNode c=new ClassNode();new ClassReader(b).accept(c,0);return c;}
    static Map<String,byte[]> read(File f)throws Exception{Map<String,byte[]> r=new LinkedHashMap<String,byte[]>();JarFile j=new JarFile(f);try{for(Enumeration<JarEntry> e=j.entries();e.hasMoreElements();){JarEntry z=e.nextElement();InputStream in=j.getInputStream(z);ByteArrayOutputStream b=new ByteArrayOutputStream();try{byte[] block=new byte[8192];for(int n;(n=in.read(block))!=-1;)b.write(block,0,n);}finally{in.close();}require(!r.containsKey(z.getName()),"Duplicate ZIP entry");r.put(z.getName(),b.toByteArray());}}finally{j.close();}return r;}
    static String hash(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");InputStream in=new FileInputStream(f);try{byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)d.update(b,0,n);}finally{in.close();}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format("%02X",b&255));return s.toString();}
    static String xor(String s){StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i++)b.append((char)(s.charAt(i)^5));return b.toString();}
    static String join(Collection<String> values){StringBuilder b=new StringBuilder();for(String s:values){if(b.length()>0)b.append(',');b.append(s);}return b.toString();}
    static void require(boolean okay,String message){if(!okay)throw new IllegalStateException(message);}
}
