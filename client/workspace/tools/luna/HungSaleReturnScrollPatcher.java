import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Uses the existing native return-scroll serializer once per eligible near-full sale trip. */
public final class HungSaleReturnScrollPatcher implements Opcodes {
    static final String INPUT="6F1E025C754DDE82F9D479DB1E623C9B680B419A64084C2ED24B983FF9D00819";
    static final String B="luna/generated/HungSaleReturnScrollBridge", R="luna/features/SaleReturnScrollController";
    static final String ITEM=n("llilli"), SALE=n("lliiilll"), FARM=n("lliiiiil"), ROUTE=n("lliiiiii"),
            MAIN=n("liilllii"), WORLD=n("liiliii"), PLAYER=n("lliiil"), ENTITY=n("liiiliii"), SERVICE=n("lilillll");
    static final String STATE=n("lilliiiilil"), ENABLED=n("lilliiiiill"), TARGET=n("liliillilli"), ROUTING=n("liliillilil"), DELAY=n("liliiiilill");
    static String scrollName="Bùa về làng";
    static String n(String t){return HungAutoAttackNativeBuilder.n(t);}
    static void check(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    static void call(InsnList l,String o,String name,String desc){l.add(new MethodInsnNode(INVOKESTATIC,o,name,desc,false));}
    static void get(InsnList l,String o,String name,String desc){l.add(new FieldInsnNode(GETSTATIC,o,name,desc));}
    static void put(InsnList l,String o,String name,String desc){l.add(new FieldInsnNode(PUTSTATIC,o,name,desc));}
    static void v(InsnList l,int op,int i){l.add(new VarInsnNode(op,i));}
    static void val(InsnList l,int i){l.add(HungAutoAttackNativeBuilder.push(i));}
    static MethodNode add(ClassNode c,String name,String desc){return HungAutoAttackNativeBuilder.create(c,ACC_PUBLIC|ACC_STATIC,name,desc);}
    static void nativeField(Map<String,byte[]> data,String owner,String field,String desc,boolean stat){
        HungEquipmentFiltersPatcher.requireField(data,owner,field,desc,stat);
    }
    static String farmScrollName(File input,Map<String,byte[]> data)throws Exception{
        ClassNode farm=HungBySonExpInfoPack.node(data.get(FARM+".class"));
        MethodNode use=HungAutoAttackNativeBuilder.method(farm,n("lliiiili"),"()V");
        List<AbstractInsnNode> r=HungAutoAttackNativeBuilder.real(use);byte[] marker=null;
        for(int i=0;i<r.size()-1;i++)if(HungAutoAttackNativeBuilder.call(r.get(i),"key","d","([B)Ljava/lang/String;")
                &&HungAutoAttackNativeBuilder.call(r.get(i+1),"java/lang/String","indexOf","(Ljava/lang/String;)I")){
            int array=i-1;while(array>=1&&r.get(array).getOpcode()!=NEWARRAY)array--;
            check(array>=1&&((IntInsnNode)r.get(array)).operand==T_BYTE,"Farm marker byte array");
            int length=HungAutoAttackNativeBuilder.integer(r.get(array-1));check(length>0&&i-array-1==length*4,"Farm marker length");
            marker=new byte[length];for(int k=0;k<length;k++){
                int at=array+1+k*4;check(r.get(at).getOpcode()==DUP&&HungAutoAttackNativeBuilder.integer(r.get(at+1))==k&&r.get(at+3).getOpcode()==BASTORE,"Farm marker literal");
                marker[k]=(byte)HungAutoAttackNativeBuilder.integer(r.get(at+2));
            }
        }
        check(marker!=null,"Native Farm name search");
        java.net.URLClassLoader loader=new java.net.URLClassLoader(new java.net.URL[]{input.toURI().toURL()},null);
        try{String text=(String)Class.forName("key",true,loader).getMethod("d",byte[].class).invoke(null,(Object)marker);check(text.length()>0,"Decoded Farm marker");return text;}
        finally{loader.close();}
    }
    static ClassNode bridge(){
        ClassNode c=HungAutoAttackNativeBuilder.create(B);
        InsnList l=add(c,"map","()I").instructions;LabelNode none=new LabelNode();
        get(l,MAIN,n("liiililiiii"),"Lcs;");l.add(new JumpInsnNode(IFNULL,none));
        get(l,MAIN,n("liiililiiii"),"Lcs;");l.add(new FieldInsnNode(GETFIELD,"cs","d","I"));l.add(new InsnNode(IRETURN));l.add(none);val(l,-1);l.add(new InsnNode(IRETURN));

        l=add(c,"findScroll","()L"+ITEM+";").instructions;none=new LabelNode();LabelNode loop=new LabelNode(),next=new LabelNode();
        get(l,ITEM,n("liiillili"),"Let;");l.add(new JumpInsnNode(IFNULL,none));val(l,0);v(l,ISTORE,0);l.add(loop);
        v(l,ILOAD,0);get(l,ITEM,n("liiillili"),"Let;");l.add(new MethodInsnNode(INVOKEVIRTUAL,"et","c","()I",false));l.add(new JumpInsnNode(IF_ICMPGE,none));
        get(l,ITEM,n("liiillili"),"Let;");v(l,ILOAD,0);l.add(new MethodInsnNode(INVOKEVIRTUAL,"et","a","(I)Ljava/lang/Object;",false));v(l,ASTORE,1);
        v(l,ALOAD,1);l.add(new TypeInsnNode(INSTANCEOF,ITEM));l.add(new JumpInsnNode(IFEQ,next));v(l,ALOAD,1);l.add(new TypeInsnNode(CHECKCAST,ITEM));v(l,ASTORE,2);
        v(l,ALOAD,2);l.add(new FieldInsnNode(GETFIELD,ITEM,n("liiiiliii"),"Ljava/lang/String;"));l.add(new JumpInsnNode(IFNULL,next));
        v(l,ALOAD,2);l.add(new FieldInsnNode(GETFIELD,ITEM,n("liiiiliii"),"Ljava/lang/String;"));l.add(new LdcInsnNode(scrollName));
        l.add(new MethodInsnNode(INVOKEVIRTUAL,"java/lang/String","indexOf","(Ljava/lang/String;)I",false));val(l,-1);l.add(new JumpInsnNode(IF_ICMPEQ,next));v(l,ALOAD,2);l.add(new InsnNode(ARETURN));
        l.add(next);l.add(new IincInsnNode(0,1));l.add(new JumpInsnNode(GOTO,loop));l.add(none);l.add(new InsnNode(ACONST_NULL));l.add(new InsnNode(ARETURN));

        l=add(c,"fallback","()V").instructions;call(l,R,"fallbackDestination","()I");put(l,ROUTE,TARGET,"I");
        val(l,1);put(l,ROUTE,ROUTING,"Z");val(l,15);put(l,ROUTE,DELAY,"I");call(l,R,"cancel","()V");l.add(new InsnNode(RETURN));

        MethodNode start=add(c,"start","()V");l=start.instructions;LabelNode done=new LabelNode(),tryStart=new LabelNode(),tryEnd=new LabelNode(),catcher=new LabelNode();
        call(l,R,"cancel","()V");call(l,B,"map","()I");call(l,R,"isSaleVillage","(I)Z");l.add(new JumpInsnNode(IFNE,done));
        get(l,WORLD,n("llilliill"),"L"+PLAYER+";");l.add(new JumpInsnNode(IFNULL,done));
        get(l,WORLD,n("llilliill"),"L"+PLAYER+";");l.add(new FieldInsnNode(GETFIELD,ENTITY,"cG","I"));val(l,4);l.add(new JumpInsnNode(IF_ICMPEQ,done));
        call(l,B,"findScroll","()L"+ITEM+";");v(l,ASTORE,0);v(l,ALOAD,0);l.add(new JumpInsnNode(IFNULL,done));
        call(l,"java/lang/System","currentTimeMillis","()J");get(l,ROUTE,TARGET,"I");call(l,R,"begin","(JI)V");
        val(l,0);put(l,ROUTE,ROUTING,"Z");val(l,1);put(l,ROUTE,TARGET,"I");
        l.add(tryStart);call(l,SERVICE,n("liililli"),"()L"+SERVICE+";");v(l,ALOAD,0);l.add(new FieldInsnNode(GETFIELD,ITEM,"O","I"));l.add(new InsnNode(I2S));
        l.add(new MethodInsnNode(INVOKEVIRTUAL,SERVICE,n("llili"),"(S)V",false));l.add(tryEnd);l.add(new JumpInsnNode(GOTO,done));
        l.add(catcher);l.add(new InsnNode(POP));call(l,B,"fallback","()V");l.add(done);l.add(new InsnNode(RETURN));start.tryCatchBlocks.add(new TryCatchBlockNode(tryStart,tryEnd,catcher,"java/lang/Throwable"));

        l=add(c,"poll","()V").instructions;done=new LabelNode();LabelNode inactive=new LabelNode(),active=new LabelNode(),fallback=new LabelNode(),arrived=new LabelNode();
        call(l,R,"waiting","()Z");l.add(new JumpInsnNode(IFEQ,done));
        // Farm OFF pauses an in-flight trip. Keep its context so Farm ON can resume arrival/fallback.
        LabelNode poll=new LabelNode();get(l,FARM,n("lililiilill"),"Z");l.add(new JumpInsnNode(IFNE,poll));
        get(l,SALE,ENABLED,"Z");l.add(new JumpInsnNode(IFEQ,poll));get(l,SALE,STATE,"I");val(l,1);l.add(new JumpInsnNode(IF_ICMPNE,poll));
        val(l,0);put(l,ROUTE,ROUTING,"Z");l.add(new JumpInsnNode(GOTO,done));l.add(poll);
        call(l,"java/lang/System","currentTimeMillis","()J");call(l,B,"map","()I");
        get(l,FARM,n("lililiilill"),"Z");l.add(new JumpInsnNode(IFEQ,inactive));get(l,SALE,ENABLED,"Z");l.add(new JumpInsnNode(IFEQ,inactive));val(l,1);l.add(new JumpInsnNode(GOTO,active));l.add(inactive);val(l,0);l.add(active);
        get(l,SALE,STATE,"I");call(l,R,"poll","(JIZI)I");v(l,ISTORE,0);
        v(l,ILOAD,0);val(l,3);l.add(new JumpInsnNode(IF_ICMPEQ,fallback));v(l,ILOAD,0);val(l,2);l.add(new JumpInsnNode(IF_ICMPEQ,arrived));
        val(l,0);put(l,ROUTE,ROUTING,"Z");l.add(new JumpInsnNode(GOTO,done));
        l.add(fallback);call(l,B,"fallback","()V");l.add(new JumpInsnNode(GOTO,done));
        l.add(arrived);call(l,B,"map","()I");put(l,ROUTE,TARGET,"I");val(l,0);put(l,ROUTE,ROUTING,"Z");l.add(done);l.add(new InsnNode(RETURN));
        return c;
    }
    public static void main(String[] a)throws Exception{
        check(a.length==4,"input.jar supportDir output.jar report.properties");File input=new File(a[0]),output=new File(a[2]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned footwear-fix release");check(!output.exists()&&!new File(a[3]).exists(),"Fresh outputs");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        scrollName=farmScrollName(input,before);
        for(String[] f:new String[][]{{"O","I"},{n("liiiiliii"),"Ljava/lang/String;"}})nativeField(before,ITEM,f[0],f[1],false);
        nativeField(before,ITEM,n("liiillili"),"Let;",true);nativeField(before,MAIN,n("liiililiiii"),"Lcs;",true);
        nativeField(before,ROUTE,TARGET,"I",true);nativeField(before,ROUTE,ROUTING,"Z",true);nativeField(before,ROUTE,DELAY,"I",true);
        nativeField(before,SALE,STATE,"I",true);nativeField(before,SALE,ENABLED,"Z",true);nativeField(before,FARM,n("lililiilill"),"Z",true);
        nativeField(before,WORLD,n("llilliill"),"L"+PLAYER+";",true);nativeField(before,ENTITY,"cG","I",false);
        ClassNode service=HungBySonExpInfoPack.node(before.get(SERVICE+".class"));HungAutoAttackNativeBuilder.method(service,n("liililli"),"()L"+SERVICE+";");
        MethodNode use=HungAutoAttackNativeBuilder.method(service,n("llili"),"(S)V");List<AbstractInsnNode> useCode=HungAutoAttackNativeBuilder.real(use);
        check(useCode.size()>5&&HungAutoAttackNativeBuilder.integer(useCode.get(1))==32,"Native item-use opcode32");
        boolean shortId=false;for(AbstractInsnNode x:useCode)if(HungAutoAttackNativeBuilder.call(x,"java/io/DataOutputStream","writeShort","(I)V"))shortId=true;check(shortId,"Native use short itemID");
        ClassNode sale=HungBySonExpInfoPack.node(before.get(SALE+".class"));MethodNode update=HungAutoAttackNativeBuilder.method(sale,"update","()V");List<AbstractInsnNode> r=HungAutoAttackNativeBuilder.real(update);int starts=0;
        for(int i=1;i<r.size();i++)if(HungAutoAttackNativeBuilder.field(r.get(i),PUTSTATIC,SALE,STATE,"I")&&HungAutoAttackNativeBuilder.integer(r.get(i-1))==1){
            InsnList hook=new InsnList();call(hook,B,"start","()V");update.instructions.insert(r.get(i),hook);starts++;
        }
        check(starts==1,"Single start after native origin snapshot/pause/route planning");InsnList pollHook=new InsnList();call(pollHook,B,"poll","()V");update.instructions.insertBefore(r.get(0),pollHook);
        byte[] saleBytes=HungAutoAttackNativeBuilder.bytes(sale);HungBySonExpInfoPack.verifyClass(before.get(SALE+".class"),saleBytes,Collections.singleton("update()V"),Collections.<String>emptySet());out.put(SALE+".class",saleBytes);
        ClassNode inverse=HungBySonExpInfoPack.node(saleBytes);MethodNode reverted=HungAutoAttackNativeBuilder.method(inverse,"update","()V");int removed=0;
        for(AbstractInsnNode x:reverted.instructions.toArray())if(x instanceof MethodInsnNode&&((MethodInsnNode)x).owner.equals(B)){reverted.instructions.remove(x);removed++;}
        check(removed==2,"Exactly two native hooks");
        MethodNode original=HungAutoAttackNativeBuilder.method(HungBySonExpInfoPack.node(before.get(SALE+".class")),"update","()V");
        check(HungBySonExpInfoPack.canonical(reverted).equals(HungBySonExpInfoPack.canonical(original)),"Native update inverse is bytecode-identical");
        ClassNode b=bridge();byte[] bBytes=HungAutoAttackNativeBuilder.bytes(b);HungBySonExpInfoPack.screen(B,bBytes);check(!before.containsKey(B+".class"),"New bridge");out.put(B+".class",bBytes);
        byte[] support=Files.readAllBytes(new File(a[1],R+".class").toPath());HungBySonExpInfoPack.screen(R,support);check(!before.containsKey(R+".class"),"New support");out.put(R+".class",support);
        for(String key:before.keySet())if(!key.equals(SALE+".class"))check(Arrays.equals(before.get(key),out.get(key)),"Unrelated class/resource "+key);
        check(out.size()==before.size()+2,"Only two new classes");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        Properties p=new Properties();p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));p.setProperty("classes","280");
        p.setProperty("changed.native",SALE+".update()V");p.setProperty("hooks","poll before native guards; start after state1");p.setProperty("scroll.identification","same name substring as native Farm");p.setProperty("scroll.name",scrollName);p.setProperty("scroll.id","actual matched item O");p.setProperty("native.use",SERVICE+"."+n("llili")+"(S)V");p.setProperty("native.use.packet","32 + short actual itemID");p.setProperty("wait.ms","10000");p.setProperty("resources","byte-identical");
        OutputStream cfg=new FileOutputStream(a[3]);try{p.store(cfg,"Auto Sell one native return scroll per eligible trip; native travel fallback");}finally{cfg.close();}
        System.out.println("SALE_RETURN_SCROLL_PATCH=PASS SHA256="+p.getProperty("output.sha256"));
    }
}
