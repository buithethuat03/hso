import java.io.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Focused receiver inventory and instruction listing for the MAX protocol port. */
public final class MaxProtocolInspect implements Opcodes {
    static Map<String,ClassNode> nodes=new LinkedHashMap<String,ClassNode>();
    static Map<String,String> aliases=new LinkedHashMap<String,String>();
    static String alias(String value){String a=aliases.get(value);return a==null?value:a;}
    static String descriptor(String value){for(String name:aliases.keySet())value=value.replace("L"+name+";","L"+aliases.get(name)+";");return value;}
    public static void main(String[] args)throws Exception{
        Map<String,byte[]> jar=MaxVariantPatcher.read(new File(args[0]));
        Properties symbols=new Properties();InputStream stream=new FileInputStream("symbols/autotrade-symbols.properties");try{symbols.load(stream);}finally{stream.close();}
        for(String k:symbols.stringPropertyNames())if(k.startsWith("class."))aliases.put(symbols.getProperty(k),k.substring(6));
        for(byte[] b:jar.values())if(b.length>4&&b[0]==(byte)0xca){ClassNode c=MaxVariantPatcher.node(b);nodes.put(c.name,c);}
        int index=0;for(String n:nodes.keySet())if(!aliases.containsKey(n))aliases.put(n,n.length()>50?"C"+(index++):n);
        if(args.length==1){List<String[]> rows=new ArrayList<String[]>();for(ClassNode c:nodes.values())for(MethodNode m:c.methods){int reads=0;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode&&((MethodInsnNode)n).owner.equals("java/io/DataInputStream"))reads++;if(reads>2)rows.add(new String[]{c.name,m.name,m.desc,String.valueOf(reads)});}Collections.sort(rows,new Comparator<String[]>(){public int compare(String[] a,String[] b){return Integer.parseInt(b[3])-Integer.parseInt(a[3]);}});for(String[] r:rows)System.out.println("READS="+r[3]+" "+alias(r[0])+" OWNER="+r[0]+" METHOD="+r[1]+r[2]);return;}
        ClassNode selected=nodes.get(args[1]);if(selected==null)for(ClassNode c:nodes.values())if(alias(c.name).equals(args[1]))selected=c;
        if(selected==null)throw new IllegalArgumentException("Unknown owner");
        System.out.println("CLASS "+alias(selected.name)+"="+selected.name+" extends "+alias(selected.superName));
        for(ClassNode c:nodes.values())if(c==selected)for(FieldNode f:c.fields)System.out.println("FIELD "+f.access+" "+f.name+" "+descriptor(f.desc));
        int mi=0;for(MethodNode m:selected.methods){String methodAlias=m.name.length()>60?"m"+mi:m.name;mi++;if(args.length>2&&!m.name.equals(args[2])&&!methodAlias.equals(args[2]))continue;if(args.length>3&&!m.desc.equals(args[3]))continue;
            System.out.println("METHOD "+methodAlias+"="+m.name+descriptor(m.desc)+" access="+m.access);
            Map<LabelNode,String> labels=new IdentityHashMap<LabelNode,String>();int li=0;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof LabelNode)labels.put((LabelNode)n,"L"+li++);
            int i=0;for(AbstractInsnNode n:m.instructions.toArray()){
                if(n instanceof LabelNode){System.out.println(" "+labels.get(n)+":");continue;}if(n.getOpcode()<0)continue;String v="";
                if(n instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)n;ClassNode owner=nodes.get(f.owner);String fn=f.name;if(owner!=null)for(int k=0;k<owner.fields.size();k++)if(owner.fields.get(k).name.equals(fn)&&owner.fields.get(k).desc.equals(f.desc)){fn="f"+k;break;}v=alias(f.owner)+"."+fn+":"+descriptor(f.desc);}
                else if(n instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)n;ClassNode owner=nodes.get(call.owner);String mn=call.name;if(owner!=null)for(int k=0;k<owner.methods.size();k++)if(owner.methods.get(k).name.equals(mn)&&owner.methods.get(k).desc.equals(call.desc)){mn=mn.length()>60?"m"+k:mn;break;}v=alias(call.owner)+"."+mn+descriptor(call.desc);}
                else if(n instanceof VarInsnNode)v="local="+((VarInsnNode)n).var;
                else if(n instanceof IntInsnNode)v=String.valueOf(((IntInsnNode)n).operand);
                else if(n instanceof LdcInsnNode)v=String.valueOf(((LdcInsnNode)n).cst);
                else if(n instanceof TypeInsnNode)v=alias(((TypeInsnNode)n).desc);
                else if(n instanceof JumpInsnNode)v=labels.get(((JumpInsnNode)n).label);
                else if(n instanceof TableSwitchInsnNode){TableSwitchInsnNode s=(TableSwitchInsnNode)n;StringBuilder b=new StringBuilder("default="+labels.get(s.dflt));for(int k=0;k<s.labels.size();k++)b.append(" ").append(s.min+k).append(":").append(labels.get(s.labels.get(k)));v=b.toString();}
                else if(n instanceof LookupSwitchInsnNode){LookupSwitchInsnNode s=(LookupSwitchInsnNode)n;StringBuilder b=new StringBuilder("default="+labels.get(s.dflt));for(int k=0;k<s.labels.size();k++)b.append(" ").append(s.keys.get(k)).append(":").append(labels.get(s.labels.get(k)));v=b.toString();}
                else if(n instanceof IincInsnNode)v="local="+((IincInsnNode)n).var+" delta="+((IincInsnNode)n).incr;
                System.out.println(" "+i+++" OP="+n.getOpcode()+" "+v);
            }
            for(TryCatchBlockNode t:m.tryCatchBlocks)System.out.println(" CATCH "+labels.get(t.start)+" "+labels.get(t.end)+" -> "+labels.get(t.handler)+" "+t.type);
        }
    }
}
