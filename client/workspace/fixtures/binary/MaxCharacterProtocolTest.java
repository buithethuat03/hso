import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Executes each original native array loop against legacy wire data and a tail sentinel. */
public final class MaxCharacterProtocolTest implements Opcodes {
    static final String SELF="MaxCharacterProtocolTest",STATE="MaxCharacterProtocolTest$State";
    public static final class State {
        public byte[] bytes; public short[] shorts;
        public short part0,part1,part2,part3,part4,part5,part6,part7,part8,part9;
        public boolean costume;
    }
    public static State state;
    public static int worldFlag;
    static DataInputStream in;
    static int checks;
    public static DataInputStream stream(Object ignored){return in;}
    static final class Loader extends ClassLoader{Loader(){super(MaxCharacterProtocolTest.class.getClassLoader());}Class<?> define(String name,byte[] bytes){return defineClass(name,bytes,0,bytes.length);}}
    public static void main(String[] args)throws Exception{
        check(args.length==3,"original-donor broken-base fixed-candidate");
        Map<String,byte[]> donor=MaxVariantPatcher.read(new File(args[0])),before=MaxVariantPatcher.read(new File(args[1])),after=MaxVariantPatcher.read(new File(args[2]));
        Properties p=new Properties();InputStream pin=new FileInputStream("symbols/autotrade-symbols.properties");try{p.load(pin);}finally{pin.close();}
        ClassNode entity=MaxVariantPatcher.node(before.get(p.getProperty("class.ENTITY")+".class"));String field=entity.fields.get(160).name;
        String oldField=MaxVariantPatcher.node(donor.get("ez.class")).fields.get(160).name;
        ClassNode nativeDonor=MaxVariantPatcher.node(donor.get("eq.class")),nativeBefore=MaxVariantPatcher.node(before.get(MaxLegacyProtocolPatcher.RECEIVER+".class")),nativeAfter=MaxVariantPatcher.node(after.get(MaxLegacyProtocolPatcher.RECEIVER+".class"));
        String[] names={"b","e","j"};int[] indexes={3,6,11};int cases=0,oldFailures=0;
        for(int k=0;k<3;k++){
            Method donorProbe=probe(MaxVariantPatcher.method(nativeDonor,names[k],"(Leo;)V"),"ez",oldField,"[B","DonorAppearance"+k);
            Method oldProbe=probe(nativeBefore.methods.get(indexes[k]),entity.name,field,"[S","BrokenAppearance"+k);
            Method fixedProbe=probe(nativeAfter.methods.get(indexes[k]),entity.name,field,"[S","FixedAppearance"+k);
            for(byte[] values:new byte[][]{new byte[0],new byte[]{0},new byte[]{-1,0,12,127},new byte[]{1,2,3,4,5,6},new byte[]{-1,2,-1,4,-1,6,7,8,9,10,11,12}}){
                byte[] payload=wire(values);check(Arrays.equals(values,decode(donorProbe,payload,false)),"Donor wire interpretation");
                boolean oldFailed=false;try{byte[] result=decode(oldProbe,payload,true);oldFailed=!Arrays.equals(values,result)||in.readUnsignedShort()!=0x5A3C||in.available()!=0;}catch(Exception e){oldFailed=true;}
                if(values.length>0){check(oldFailed,"Reproduce pre-fix stream displacement");oldFailures++;}
                check(Arrays.equals(values,decode(fixedProbe,payload,true)),"Fixed native appearance values opcode "+MaxLegacyProtocolPatcher.OPCODES[k]);
                check(in.readUnsignedShort()==0x5A3C&&in.available()==0,"Adjacent body/stat tail remains aligned opcode "+MaxLegacyProtocolPatcher.OPCODES[k]);cases++;
            }
        }
        ClassNode donorEntity=MaxVariantPatcher.node(donor.get("ez.class"));
        Method donorTail=probe(MaxVariantPatcher.method(nativeDonor,"b","(Leo;)V"),"ez",oldField,"[B","DonorCompleteTail",donorEntity);
        Method oldTail=probe(nativeBefore.methods.get(3),entity.name,field,"[S","BrokenCompleteTail",entity);
        Method fixedTail=probe(nativeAfter.methods.get(3),entity.name,field,"[S","FixedCompleteTail",entity);
        byte[] parts={-1,4,6,8,12,16};ByteArrayOutputStream tb=new ByteArrayOutputStream();DataOutputStream tout=new DataOutputStream(tb);
        tout.writeByte(parts.length);tout.write(parts);tout.writeByte(5);tout.writeShort(101);tout.writeByte(1);for(int i=1;i<10;i++)tout.writeShort(101+i*100);
        byte[] complete=tb.toByteArray();decode(donorTail,complete,false);State expected=state;
        decode(fixedTail,complete,true);State actual=state;
        check(in.available()==0&&worldFlag==5&&actual.costume,"Complete native body-tail consumption");
        for(int i=0;i<10;i++)check(State.class.getField("part"+i).getShort(expected)==101+i*100&&State.class.getField("part"+i).getShort(actual)==101+i*100,"Native head/body/equipment part "+i);
        boolean rejected=false;try{decode(oldTail,complete,true);rejected=in.available()!=0||state.part0!=101;}catch(Exception ex){rejected=true;}check(rejected,"Original full tail reproduces missing/invalid parts");
        Set<String> changed=new LinkedHashSet<String>();for(String name:before.keySet()){check(after.containsKey(name),"Entry lost");if(!Arrays.equals(before.get(name),after.get(name)))changed.add(name);}
        check(before.keySet().equals(after.keySet())&&changed.equals(Collections.singleton(MaxLegacyProtocolPatcher.RECEIVER+".class")),"Only native receiver changes");
        int expectedMethods=0;for(int i=0;i<nativeBefore.methods.size();i++){
            MethodNode old=nativeBefore.methods.get(i),next=nativeAfter.methods.get(i);List<MethodInsnNode> differences=new ArrayList<MethodInsnNode>();AbstractInsnNode[] a=old.instructions.toArray(),b=next.instructions.toArray();
            check(a.length==b.length,"Method topology unchanged");
            for(int j=0;j<a.length;j++)if(a[j] instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)a[j],y=(MethodInsnNode)b[j];if(!x.owner.equals(y.owner)||!x.name.equals(y.name)||!x.desc.equals(y.desc)){check(x.owner.equals("java/io/DataInputStream")&&y.owner.equals(x.owner)&&x.name.equals("readShort")&&x.desc.equals("()S")&&y.name.equals("readByte")&&y.desc.equals("()B"),"Only element read width changes");differences.add(y);}}
            if(!differences.isEmpty()){check(differences.size()==1&&(i==3||i==6||i==11),"Only three appearance readers change");expectedMethods++;}
        }
        check(expectedMethods==3,"Complete appearance-reader fix");
        System.out.println("MAX_CHARACTER_PROTOCOL=PASS; checks="+checks+"; nativeLoopCases="+cases+"; reproducedBrokenCases="+oldFailures+"; opcodes=3,5,15; complete self body-tail preserves all ten native part IDs");
    }
    static Method probe(MethodNode source,String owner,String field,String desc,String name)throws Exception{
        return probe(source,owner,field,desc,name,null);
    }
    static Method probe(MethodNode source,String owner,String field,String desc,String name,ClassNode completeEntity)throws Exception{
        FieldInsnNode target=null;for(AbstractInsnNode n:source.instructions.toArray())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTFIELD){FieldInsnNode f=(FieldInsnNode)n;if(f.owner.equals(owner)&&f.name.equals(field)&&f.desc.equals(desc))target=f;}
        check(target!=null,"Appearance array destination");AbstractInsnNode allocation=target.getPrevious();while(allocation!=null&&allocation.getOpcode()!=NEWARRAY)allocation=allocation.getPrevious();check(allocation!=null,"Appearance allocation");
        AbstractInsnNode end=target.getNext();
        if(completeEntity!=null){for(AbstractInsnNode n=end;n!=null;n=n.getNext())if(n instanceof FieldInsnNode&&n.getOpcode()==PUTFIELD){FieldInsnNode f=(FieldInsnNode)n;if(f.owner.equals(owner)&&f.name.equals(completeEntity.fields.get(9).name)&&f.desc.equals("S")){end=n.getNext();break;}}}
        AbstractInsnNode count=allocation.getPrevious();while(count!=null&&!(count instanceof MethodInsnNode&&((MethodInsnNode)count).owner.equals("java/io/DataInputStream")&&((MethodInsnNode)count).name.equals("readByte")))count=count.getPrevious();check(count!=null,"Native count read");
        int packetLocal=(source.access&ACC_STATIC)!=0?0:1;
        AbstractInsnNode start=count.getPrevious();while(!(start instanceof VarInsnNode&&start.getOpcode()==ALOAD&&((VarInsnNode)start).var==packetLocal))start=start.getPrevious();
        AbstractInsnNode receiverLoad=MaxVariantPatcher.previous(MaxVariantPatcher.previous(target));
        check(receiverLoad.getOpcode()==ALOAD||receiverLoad.getOpcode()==GETSTATIC,"Native array receiver load");
        Map<LabelNode,LabelNode> labels=new IdentityHashMap<LabelNode,LabelNode>();for(AbstractInsnNode n:source.instructions.toArray())if(n instanceof LabelNode)labels.put((LabelNode)n,new LabelNode());
        MethodNode m=new MethodNode(ACC_PUBLIC|ACC_STATIC,"decode","(Ljava/lang/Object;Ljava/lang/Object;)V",null,null);
        for(AbstractInsnNode n=start;n!=end;n=n.getNext()){
            if(n instanceof FrameNode)continue;
            if(n==receiverLoad){m.instructions.add(new FieldInsnNode(GETSTATIC,SELF,"state","L"+STATE+";"));continue;}
            if(n instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)n;
                if(f.getOpcode()==GETFIELD&&f.desc.equals("Ljava/io/DataInputStream;"))m.instructions.add(new MethodInsnNode(INVOKESTATIC,SELF,"stream","(Ljava/lang/Object;)Ljava/io/DataInputStream;",false));
                else if(f.getOpcode()==GETSTATIC)m.instructions.add(new FieldInsnNode(GETSTATIC,SELF,"state","L"+STATE+";"));
                else if(f==target)m.instructions.add(new FieldInsnNode(PUTFIELD,STATE,desc.equals("[B")?"bytes":"shorts",desc));
                else if(completeEntity!=null&&f.getOpcode()==PUTSTATIC&&f.desc.equals("I"))m.instructions.add(new FieldInsnNode(PUTSTATIC,SELF,"worldFlag","I"));
                else if(completeEntity!=null&&f.getOpcode()==PUTFIELD&&f.owner.equals(owner)){
                    String mapped=null;for(int i=0;i<10;i++)if(f.name.equals(completeEntity.fields.get(i).name)&&f.desc.equals("S"))mapped="part"+i;
                    if(f.name.equals(completeEntity.fields.get(194).name)&&f.desc.equals("Z"))mapped="costume";
                    check(mapped!=null,"Known native body-tail field");m.instructions.add(new FieldInsnNode(PUTFIELD,STATE,mapped,f.desc));
                }
                else throw new IllegalStateException("Unexpected native field in loop");
            }else if(n instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)n;if(c.desc.equals("()Ljava/io/DataInputStream;"))m.instructions.add(new MethodInsnNode(INVOKESTATIC,SELF,"stream","(Ljava/lang/Object;)Ljava/io/DataInputStream;",false));else {check(c.owner.equals("java/io/DataInputStream")||completeEntity!=null&&c.name.equals("printStackTrace"),"Only wire reads in extracted loop");m.instructions.add(n.clone(labels));}}
            else m.instructions.add(n.clone(labels));
        }
        m.instructions.add(new InsnNode(RETURN));ClassNode probe=new ClassNode();probe.version=V1_3;probe.access=ACC_PUBLIC;probe.name=name;probe.superName="java/lang/Object";probe.methods.add(m);ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);probe.accept(w);return new Loader().define(name,w.toByteArray()).getMethod("decode",Object.class,Object.class);
    }
    static byte[] wire(byte[] values)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);out.writeByte(values.length);out.write(values);out.writeShort(0x5A3C);return b.toByteArray();}
    static byte[] decode(Method probe,byte[] payload,boolean current)throws Exception{state=new State();in=new DataInputStream(new ByteArrayInputStream(payload));try{probe.invoke(null,new Object(),new Object());}catch(InvocationTargetException e){throw new IOException("Native loop",e.getCause());}if(!current)return state.bytes;byte[] out=new byte[state.shorts.length];for(int i=0;i<out.length;i++){check(state.shorts[i]>=-128&&state.shorts[i]<=127,"Signed byte widened without truncation");out[i]=(byte)state.shorts[i];}return out;}
    static void check(boolean okay,String message){checks++;if(!okay)throw new IllegalStateException(message);}
}
