import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Corrects byte-sized menu actions and adds exact 8/10 and 9/10 elapsed scaling. */
public final class HungBySonTempoMenuFixPatcher implements Opcodes {
    static final String INPUT = "5878D588F1CED165EE7045AAB656EBECD043FAA64556CC4CD317387D44C4727C";
    static final String CANVAS = "com/silverknight/a", CARRY = "codexTempoFraction", LAST = "codexTempoFractionSpeed";
    static final int[] CODES = {1, 2, 3, -8, -9};
    static void check(boolean okay, String text) { if (!okay) throw new IllegalStateException(text); }
    static byte[] bytes(InputStream in) throws Exception { try { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; for(int n;(n=in.read(b))>=0;)out.write(b,0,n);return out.toByteArray(); } finally { in.close(); } }
    static ClassNode cls(byte[] b) { ClassNode c=new ClassNode(ASM9);new ClassReader(b).accept(c,0);return c; }
    static MethodNode method(ClassNode c,String name,String desc) { for(MethodNode m:c.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;throw new IllegalStateException("Missing method "+name+desc); }
    static AbstractInsnNode next(AbstractInsnNode i) { i=i.getNext();while(i!=null&&i.getOpcode()<0)i=i.getNext();return i; }
    static AbstractInsnNode previous(AbstractInsnNode i) { i=i.getPrevious();while(i!=null&&i.getOpcode()<0)i=i.getPrevious();return i; }
    static int integer(AbstractInsnNode i) { int op=i.getOpcode();if(op>=ICONST_M1&&op<=ICONST_5)return op-ICONST_0;if(i instanceof IntInsnNode)return ((IntInsnNode)i).operand;if(i instanceof LdcInsnNode&&((LdcInsnNode)i).cst instanceof Integer)return ((Integer)((LdcInsnNode)i).cst).intValue();throw new IllegalStateException("Expected integer"); }
    static AbstractInsnNode push(int n) { return n>=-1&&n<=5?new InsnNode(ICONST_0+n):new IntInsnNode(n>=-128&&n<=127?BIPUSH:SIPUSH,n); }
    static byte[] write(ClassNode c) { ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray(); }
    static String hash(byte[] bytes)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format("%02X",b&255));return s.toString();}
    static boolean speed(AbstractInsnNode i,String owner,String field,int opcode) { return i instanceof FieldInsnNode&&i.getOpcode()==opcode&&((FieldInsnNode)i).owner.equals(owner)&&((FieldInsnNode)i).name.equals(field)&&((FieldInsnNode)i).desc.equals("I"); }
    static MethodNode formatter(String owner) {
        MethodNode m=new MethodNode(ASM9,ACC_PUBLIC|ACC_STATIC,"codexSpeedText","(I)Ljava/lang/String;",null,null);
        String[] labels={"1","2","3","0.8","0.9"};
        for(int i=0;i<CODES.length;i++){LabelNode no=new LabelNode();m.instructions.add(new VarInsnNode(ILOAD,0));m.instructions.add(push(CODES[i]));m.instructions.add(new JumpInsnNode(IF_ICMPNE,no));m.instructions.add(new LdcInsnNode(labels[i]));m.instructions.add(new InsnNode(ARETURN));m.instructions.add(no);}
        m.instructions.add(new LdcInsnNode("1"));m.instructions.add(new InsnNode(ARETURN));return m;
    }
    static MethodNode scaler(String speedOwner,String speedField) {
        MethodNode m=new MethodNode(ASM9,ACC_PRIVATE,"codexScaleElapsed","(J)J",null,null);InsnList a=m.instructions;
        LabelNode unchanged=new LabelNode(),fraction=new LabelNode();
        a.add(new FieldInsnNode(GETSTATIC,speedOwner,speedField,"I"));a.add(new VarInsnNode(ISTORE,3));
        a.add(new VarInsnNode(ALOAD,0));a.add(new FieldInsnNode(GETFIELD,CANVAS,LAST,"I"));a.add(new VarInsnNode(ILOAD,3));a.add(new JumpInsnNode(IF_ICMPEQ,unchanged));
        a.add(new VarInsnNode(ALOAD,0));a.add(new InsnNode(LCONST_0));a.add(new FieldInsnNode(PUTFIELD,CANVAS,CARRY,"J"));
        a.add(new VarInsnNode(ALOAD,0));a.add(new VarInsnNode(ILOAD,3));a.add(new FieldInsnNode(PUTFIELD,CANVAS,LAST,"I"));a.add(unchanged);
        a.add(new VarInsnNode(ILOAD,3));a.add(new JumpInsnNode(IFLT,fraction));
        a.add(new VarInsnNode(LLOAD,1));a.add(new VarInsnNode(ILOAD,3));a.add(new InsnNode(I2L));a.add(new InsnNode(LMUL));a.add(new InsnNode(LRETURN));a.add(fraction);
        a.add(new VarInsnNode(LLOAD,1));a.add(new VarInsnNode(ILOAD,3));a.add(new InsnNode(INEG));a.add(new InsnNode(I2L));a.add(new InsnNode(LMUL));
        a.add(new VarInsnNode(ALOAD,0));a.add(new FieldInsnNode(GETFIELD,CANVAS,CARRY,"J"));a.add(new InsnNode(LADD));a.add(new VarInsnNode(LSTORE,4));
        a.add(new VarInsnNode(ALOAD,0));a.add(new VarInsnNode(LLOAD,4));a.add(new LdcInsnNode(Long.valueOf(10)));a.add(new InsnNode(LREM));a.add(new FieldInsnNode(PUTFIELD,CANVAS,CARRY,"J"));
        a.add(new VarInsnNode(LLOAD,4));a.add(new LdcInsnNode(Long.valueOf(10)));a.add(new InsnNode(LDIV));a.add(new InsnNode(LRETURN));return m;
    }
    public static void main(String[] args)throws Exception {
        check(args.length==5,"input.jar tempo.properties output.jar catalog.properties report.txt");
        File input=new File(args[0]),output=new File(args[2]);check(!output.exists(),"Output must be new");
        check(hash(bytes(new FileInputStream(input))).equals(INPUT),"Unexpected input hash");
        Properties p=new Properties();p.load(new FileInputStream(args[1]));
        String menuOwner=p.getProperty("candidate.menu.owner"),speedOwner=p.getProperty("candidate.speed.owner"),speedField=p.getProperty("candidate.speed.field");
        LinkedHashMap<String,byte[]> entries=new LinkedHashMap<String,byte[]>();JarFile jar=new JarFile(input);
        for(Enumeration<JarEntry> e=jar.entries();e.hasMoreElements();){JarEntry z=e.nextElement();entries.put(z.getName(),bytes(jar.getInputStream(z)));}
        ClassNode menu=cls(entries.get(menuOwner+".class")),canvas=cls(entries.get(CANVAS+".class"));
        String action=p.getProperty("candidate.menu.action");action=action.substring(0,action.indexOf('('));
        MethodNode handler=method(menu,action,"(II)Z"),helper=method(menu,"codexBuildTempoMenu","()V");
        int changedIds=0,changedValues=0,markers=0,labels=0;
        for(MethodNode m:new MethodNode[]{handler,helper})for(AbstractInsnNode i:m.instructions.toArray()) {
            if(i instanceof IntInsnNode){int n=((IntInsnNode)i).operand;if(n<=-3101&&n>=-3105){int index=-3101-n;m.instructions.set(i,push(101+index));changedIds++;}}
            if(m==handler&&speed(i,speedOwner,speedField,PUTSTATIC)){AbstractInsnNode value=previous(i);int old=integer(value);if(old>=4&&old<=5){m.instructions.set(value,push(CODES[old-1]));changedValues++;}}
            if(m==helper&&speed(i,speedOwner,speedField,GETSTATIC)){AbstractInsnNode value=next(i);int old=integer(value);if(old>=4&&old<=5){m.instructions.set(value,push(CODES[old-1]));markers++;}}
            if(m==helper&&i instanceof LdcInsnNode){LdcInsnNode text=(LdcInsnNode)i;if("Tốc độ x4".equals(text.cst)){text.cst="Tốc độ x0.8";labels++;}if("Tốc độ x5".equals(text.cst)){text.cst="Tốc độ x0.9";labels++;}}
        }
        check(changedIds==10&&changedValues==2&&markers==2&&labels==2,"Menu patch count mismatch");
        int rootLabels=0;
        for(MethodNode root:menu.methods)for(AbstractInsnNode i:root.instructions.toArray())if(speed(i,speedOwner,speedField,GETSTATIC)) {
            AbstractInsnNode append=next(i);if(!(append instanceof MethodInsnNode)||!((MethodInsnNode)append).desc.equals("(I)Ljava/lang/StringBuilder;"))continue;
            root.instructions.insert(i,new MethodInsnNode(INVOKESTATIC,menuOwner,"codexSpeedText","(I)Ljava/lang/String;",false));
            ((MethodInsnNode)append).desc="(Ljava/lang/String;)Ljava/lang/StringBuilder;";rootLabels++;
        }
        check(rootLabels==1,"Root speed label count");menu.methods.add(formatter(menuOwner));
        MethodNode run=method(canvas,"run","()V");int scaleSites=0;
        for(AbstractInsnNode i:run.instructions.toArray())if(speed(i,speedOwner,speedField,GETSTATIC)) {
            AbstractInsnNode one=next(i),jump=next(one);if(integer(one)!=1||!(jump instanceof JumpInsnNode)||jump.getOpcode()!=IF_ICMPLE)continue;
            LabelNode end=((JumpInsnNode)jump).label;InsnList replacement=new InsnList();replacement.add(new VarInsnNode(ALOAD,0));replacement.add(new VarInsnNode(LLOAD,7));replacement.add(new MethodInsnNode(INVOKESPECIAL,CANVAS,"codexScaleElapsed","(J)J",false));replacement.add(new VarInsnNode(LSTORE,7));run.instructions.insertBefore(i,replacement);
            for(AbstractInsnNode old=i;old!=end;){AbstractInsnNode following=old.getNext();if(old.getOpcode()>=0)run.instructions.remove(old);old=following;}scaleSites++;break;
        }
        check(scaleSites==1,"Elapsed scale site count");
        canvas.fields.add(new FieldNode(ACC_PRIVATE,CARRY,"J",null,null));canvas.fields.add(new FieldNode(ACC_PRIVATE,LAST,"I",null,null));canvas.methods.add(scaler(speedOwner,speedField));
        entries.put(menuOwner+".class",write(menu));entries.put(CANVAS+".class",write(canvas));
        output.getAbsoluteFile().getParentFile().mkdirs();JarOutputStream out=new JarOutputStream(new FileOutputStream(output));
        try{for(Map.Entry<String,byte[]>e:entries.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(jar.getJarEntry(e.getKey()).getTime());out.putNextEntry(z);out.write(e.getValue());out.closeEntry();}}finally{out.close();jar.close();}
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",hash(bytes(new FileInputStream(output))));p.setProperty("changed.classes",CANVAS+","+menuOwner);
        p.setProperty("candidate.menu.childActions","101,102,103,104,105");p.setProperty("candidate.speed.codes","1,2,3,-8,-9");p.setProperty("candidate.speed.labels","1,2,3,0.8,0.9");
        p.setProperty("candidate.scale.owner",CANVAS);p.setProperty("candidate.scale.method","codexScaleElapsed");p.setProperty("candidate.scale.descriptor","(J)J");
        OutputStream catalog=new FileOutputStream(args[3]);try{p.store(catalog,"Byte-safe native menu actions and fractional tempo");}finally{catalog.close();}
        PrintWriter report=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[4]),"UTF-8"));report.println("FIX=bt.e is a signed byte: -3101..-3105 formerly wrapped to -29..-33");report.println("ACTIONS=101..105; CHOICES=x1,x2,x3,x0.8,x0.9; CODES=1,2,3,-8,-9");report.println("FRACTION=elapsed*numerator/10 with carried remainder; cleared when speed changes");report.println("changed.classes="+CANVAS+","+menuOwner);report.println("output.sha256="+p.getProperty("output.sha256"));report.close();System.out.println("MENU_FIX=BUILT "+output);
    }
}
