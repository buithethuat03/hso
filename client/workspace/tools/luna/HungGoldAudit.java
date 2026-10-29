import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Prints wallet fields and session-hook callers from the actual candidate archive. */
public final class HungGoldAudit {
    static String shortName(String s) { String t=s.replaceAll("i{40,}", "~"); return t.length()>200?t.substring(0,20)+"..."+t.substring(t.length()-20):t; }
    public static void main(String[] args) throws Exception {
        JarFile jar = new JarFile(args[0]);
        try {
            for (Enumeration<JarEntry> entries=jar.entries(); entries.hasMoreElements();) {
                JarEntry entry=entries.nextElement(); if(!entry.getName().endsWith(".class"))continue;
                InputStream input=jar.getInputStream(entry); ClassNode type=new ClassNode();
                try { new ClassReader(input).accept(type,ClassReader.SKIP_FRAMES); } finally { input.close(); }
                for(MethodNode method:type.methods) {
                    List<String> hits=new ArrayList<String>(); int index=0;
                    for(AbstractInsnNode insn:method.instructions.toArray()) {
                        if(insn instanceof FieldInsnNode) {
                            FieldInsnNode f=(FieldInsnNode)insn;
                            if(((f.name.matches("b[C-DM-P]")||(args.length>1&&f.name.startsWith(args[1])))&&f.desc.equals("J"))
                                    ||(f.desc.equals("J")&&f.owner.endsWith("liiliii")))
                                hits.add(index+" opcode="+f.getOpcode()+" "+shortName(f.owner)+"."+f.name+f.desc);
                        }
                        if(insn instanceof LdcInsnNode && ((LdcInsnNode)insn).cst instanceof String) {
                            String value=(String)((LdcInsnNode)insn).cst;
                            if(value.toLowerCase().matches(".*(vàng|vang|gold).*"))hits.add("TEXT="+value);
                        }
                        if(insn instanceof MethodInsnNode) {
                            MethodInsnNode call=(MethodInsnNode)insn;
                            if(call.owner.endsWith("/ExpEstimateBridge")&&(call.name.equals("snapshot")||call.name.equals("snapshotObject")||call.name.startsWith("gold")))
                                hits.add(index+" call "+shortName(call.owner)+"."+call.name+call.desc);
                        }
                        if(insn.getOpcode()>=0)index++;
                    }
                    if(!hits.isEmpty())System.out.println(shortName(type.name)+"."+shortName(method.name)+shortName(method.desc)+" "+hits);
                }
            }
        } finally { jar.close(); }
    }
}
