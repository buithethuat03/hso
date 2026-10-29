import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Generates an isolated JVM diagnostic copy; never a release artifact. */
public final class HungAutoAttackNativeTrace implements Opcodes {
    public static void main(String[] a)throws Exception {
        if(a.length!=4)throw new IllegalArgumentException("candidate.jar catalog.properties diagnostic.jar diagnostic.properties");
        File out=new File(a[2]),config=new File(a[3]);if(out.exists()||config.exists())throw new IllegalStateException("diagnostic outputs must be new");
        Properties p=new Properties();InputStream in=new FileInputStream(a[1]);try{p.load(in);}finally{in.close();}
        String owner=p.getProperty("native.choose.owner");JarFile j=new JarFile(a[0]);JarOutputStream w=new JarOutputStream(new FileOutputStream(out));
        try{for(Enumeration<JarEntry>es=j.entries();es.hasMoreElements();){JarEntry e=es.nextElement();byte[]b=HungAutoAttackNativeBuilder.read(j.getInputStream(e));if(e.getName().equals(owner+".class")){ClassNode c=HungAutoAttackNativeBuilder.parse(b);MethodNode m=HungAutoAttackNativeBuilder.method(c,p.getProperty("native.choose.method"),"(IZ)V");List<AbstractInsnNode>code=HungAutoAttackNativeBuilder.real(m);for(int pc:new int[]{0,25,88,107,183,243,284,322,348,359,363,394,400,419,469,502}){InsnList trace=new InsnList();trace.add(new FieldInsnNode(GETSTATIC,"java/lang/System","err","Ljava/io/PrintStream;"));trace.add(new LdcInsnNode("DIAGNOSTIC CHOOSE pc="+pc));trace.add(new MethodInsnNode(INVOKEVIRTUAL,"java/io/PrintStream","println","(Ljava/lang/String;)V",false));if(pc==322){trace.add(new FieldInsnNode(GETSTATIC,"java/lang/System","err","Ljava/io/PrintStream;"));trace.add(new VarInsnNode(ILOAD,1));trace.add(new MethodInsnNode(INVOKEVIRTUAL,"java/io/PrintStream","println","(I)V",false));trace.add(new FieldInsnNode(GETSTATIC,"java/lang/System","err","Ljava/io/PrintStream;"));trace.add(new VarInsnNode(ALOAD,2));trace.add(new FieldInsnNode(GETFIELD,HungAutoAttackNativeBuilder.n("lilii"),"b","B"));trace.add(new MethodInsnNode(INVOKEVIRTUAL,"java/io/PrintStream","println","(I)V",false));}m.instructions.insertBefore(code.get(pc),trace);}InsnList result=new InsnList();result.add(new InsnNode(DUP));result.add(new FieldInsnNode(GETSTATIC,"java/lang/System","err","Ljava/io/PrintStream;"));result.add(new InsnNode(SWAP));result.add(new MethodInsnNode(INVOKEVIRTUAL,"java/io/PrintStream","println","(Z)V",false));m.instructions.insert(code.get(329),result);InsnList kind=new InsnList();kind.add(new FieldInsnNode(GETSTATIC,"java/lang/System","err","Ljava/io/PrintStream;"));kind.add(new VarInsnNode(ALOAD,3));kind.add(new FieldInsnNode(GETFIELD,HungAutoAttackNativeBuilder.n("liiiiill"),"e","B"));kind.add(new MethodInsnNode(INVOKEVIRTUAL,"java/io/PrintStream","println","(I)V",false));m.instructions.insertBefore(code.get(336),kind);b=HungAutoAttackNativeBuilder.bytes(c);}JarEntry z=new JarEntry(e.getName());z.setTime(e.getTime());w.putNextEntry(z);w.write(b);w.closeEntry();}}finally{w.close();j.close();}
        byte[]d=MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(out.toPath()));StringBuilder s=new StringBuilder();for(byte x:d)s.append(String.format("%02X",x&255));p.setProperty("output.sha256",s.toString());p.setProperty("diagnostic.only","true");OutputStream props=new FileOutputStream(config);try{p.store(props,"DIAGNOSTIC ONLY: native choose branch trace, not release proof");}finally{props.close();}System.out.println("DIAGNOSTIC_ONLY="+out);
    }
}

