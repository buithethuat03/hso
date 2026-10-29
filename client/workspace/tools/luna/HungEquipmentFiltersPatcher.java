import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Adds equipment filters without recompiling any existing native/support class. */
public final class HungEquipmentFiltersPatcher implements Opcodes {
    static final String INPUT = "F07535B4B0A7B71A896F9F5DBE9C9871C846B92827EA0273910CEC704EBBF600";
    static final String ITEM=n("llilli"), PET=n("lliiilli"), SALE=n("lliiilll"), FARM=n("lliiiiil"),
            MAIN=n("liilllii"), WORLD=n("liiliii"), LISTENER=n("liiiiil"), MENU=n("liilliil"), MOD=n("lillllll"), PLAYER=n("lliiil");
    static final String B="luna/generated/HungEquipmentFilterBridge", S="luna/features/EquipmentFilterSettings";
    static final String ID="L"+ITEM+";", VECTOR="et", ITEM_ADD=n("l");
    static final String INV=n("liiillili"), KIND=n("liiill"), SLOT=n("llliiili"), JOB="M", LOCK=n("liiiliiii"),
            GRADE="N", LEVEL=n("llilil"), ENABLED=n("lilliiiiill"), THRESHOLD=n("lilliiiilii"), STATE=n("lilliiiilil");
    static Properties report=new Properties();
    static String n(String tail){return HungAutoAttackNativeBuilder.n(tail);}
    static void check(boolean value,String why){if(!value)throw new IllegalStateException(why);}
    static MethodNode method(ClassNode c,String name,String desc){return HungAutoAttackNativeBuilder.method(c,name,desc);}
    static MethodNode add(ClassNode c,String name,String desc){return HungAutoAttackNativeBuilder.create(c,ACC_PUBLIC|ACC_STATIC,name,desc);}
    static void call(InsnList l,String owner,String name,String desc){l.add(new MethodInsnNode(INVOKESTATIC,owner,name,desc,false));}
    static void get(InsnList l,String owner,String name,String desc){l.add(new FieldInsnNode(GETSTATIC,owner,name,desc));}
    static void put(InsnList l,String owner,String name,String desc){l.add(new FieldInsnNode(PUTSTATIC,owner,name,desc));}
    static void value(InsnList l,int v){l.add(HungAutoAttackNativeBuilder.push(v));}
    static void field(InsnList l,String name,String desc){l.add(new FieldInsnNode(GETFIELD,ITEM,name,desc));}
    static void var(InsnList l,int op,int v){l.add(new VarInsnNode(op,v));}
    static List<AbstractInsnNode> real(MethodNode m){return HungAutoAttackNativeBuilder.real(m);}
    static void requireField(Map<String,byte[]> data,String owner,String name,String desc,boolean stat){
        ClassNode c=HungBySonExpInfoPack.node(data.get(owner+".class")); boolean found=false;
        for(FieldNode f:c.fields)if(f.name.equals(name)&&f.desc.equals(desc)){check((f.access&ACC_PUBLIC)!=0,"Public boundary "+name);check(((f.access&ACC_STATIC)!=0)==stat,"Field static "+name);found=true;}
        check(found,"Missing exact field "+owner+"."+name+":"+desc);
        report.setProperty("field."+owner+"."+name,desc);
    }
    static void verifyBoundary(Map<String,byte[]> data){
        for(String[] f:new String[][]{{KIND,"I"},{SLOT,"I"},{JOB,"I"},{LOCK,"B"},{GRADE,"I"},{LEVEL,"B"}})requireField(data,ITEM,f[0],f[1],false);
        requireField(data,ITEM,INV,"Let;",true);
        requireField(data,SALE,ENABLED,"Z",true);requireField(data,SALE,THRESHOLD,"I",true);requireField(data,SALE,STATE,"I",true);
        requireField(data,PLAYER,n("lliliili"),"S",true);
        requireField(data,MOD,n("liliililiil"),"Z",true);
        ClassNode farm=HungBySonExpInfoPack.node(data.get(FARM+".class"));method(farm,n("lliiilli"),"()V");
        ClassNode menu=HungBySonExpInfoPack.node(data.get(MENU+".class"));method(menu,n(""),"(Let;ILjava/lang/String;ZLet;)V");
        // Native bt narrows the command to byte. Reserve the remaining eleven negative IDs.
        for(byte[] bytes:data.values())if(bytes.length>4&&bytes[0]==(byte)0xca&&bytes[1]==(byte)0xfe){
            ClassNode c=HungBySonExpInfoPack.node(bytes);for(MethodNode m:c.methods){List<AbstractInsnNode> r=real(m);for(int i=0;i<r.size();i++){
                AbstractInsnNode x=r.get(i);
                if(HungAutoAttackNativeBuilder.call(x,"bt","<init>","(Ljava/lang/String;IL"+LISTENER+";)V")&&i>=2){
                    int v=HungAutoAttackNativeBuilder.integer(r.get(i-2));check(v < -128 || v > -118,"Menu action id already used "+v);
                }
                if(m.name.equals("onAction")||m.name.equals("handleAction")){
                    int v=HungAutoAttackNativeBuilder.integer(x);
                    if(i>0&&i+1<r.size()&&r.get(i-1).getOpcode()==ILOAD&&r.get(i+1) instanceof JumpInsnNode)
                        check(v < -128 || v > -118,"Handled action id already used "+v);
                }
            }
            }
        }
    }
    static void addRow(InsnList l,int listLocal,String text,int action){
        var(l,ALOAD,listLocal);l.add(new TypeInsnNode(NEW,"bt"));l.add(new InsnNode(DUP));l.add(new LdcInsnNode(text));rowTail(l,action);
    }
    static void rowTail(InsnList l,int action){
        value(l,action);get(l,MAIN,n("liiiliiiiil"),"L"+WORLD+";");
        l.add(new MethodInsnNode(INVOKESPECIAL,"bt","<init>","(Ljava/lang/String;IL"+LISTENER+";)V",false));
        l.add(new MethodInsnNode(INVOKEVIRTUAL,VECTOR,ITEM_ADD,"(Ljava/lang/Object;)V",false));
    }
    static ClassNode bridge(){
        ClassNode c=HungAutoAttackNativeBuilder.create(B);
        c.fields.add(new FieldNode(ACC_PRIVATE|ACC_STATIC,"stopAfterCycle","Z",null,null));
        for(boolean sale:new boolean[]{false,true}){
            String name=sale?"acceptsSale":"acceptsPet";InsnList l=add(c,name,"("+ID+")Z").instructions;LabelNode no=new LabelNode();
            var(l,ALOAD,0);l.add(new JumpInsnNode(IFNULL,no));var(l,ALOAD,0);field(l,KIND,"I");value(l,3);l.add(new JumpInsnNode(IF_ICMPNE,no));
            if(sale){var(l,ALOAD,0);field(l,LOCK,"B");}
            var(l,ALOAD,0);field(l,SLOT,"I");var(l,ALOAD,0);field(l,JOB,"I");
            call(l,S,name,sale?"(III)Z":"(II)Z");l.add(new InsnNode(IRETURN));l.add(no);value(l,0);l.add(new InsnNode(IRETURN));
        }
        InsnList l=add(c,"saleEligible","("+ID+")Z").instructions;LabelNode no=new LabelNode();
        var(l,ALOAD,0);l.add(new JumpInsnNode(IFNULL,no));
        var(l,ALOAD,0);field(l,KIND,"I");value(l,3);l.add(new JumpInsnNode(IF_ICMPNE,no));
        var(l,ALOAD,0);field(l,LEVEL,"B");l.add(new JumpInsnNode(IFGT,no));
        var(l,ALOAD,0);field(l,GRADE,"I");l.add(new JumpInsnNode(IFLT,no));
        var(l,ALOAD,0);field(l,GRADE,"I");get(l,SALE,THRESHOLD,"I");l.add(new JumpInsnNode(IF_ICMPGE,no));
        var(l,ALOAD,0);call(l,B,"acceptsSale","("+ID+")Z");l.add(new InsnNode(IRETURN));
        l.add(no);value(l,0);l.add(new InsnNode(IRETURN));

        l=add(c,"hasSaleCandidates","()Z").instructions;no=new LabelNode();LabelNode loop=new LabelNode(),next=new LabelNode();
        get(l,ITEM,INV,"Let;");l.add(new JumpInsnNode(IFNULL,no));value(l,0);var(l,ISTORE,0);l.add(loop);
        var(l,ILOAD,0);get(l,ITEM,INV,"Let;");l.add(new MethodInsnNode(INVOKEVIRTUAL,VECTOR,"c","()I",false));l.add(new JumpInsnNode(IF_ICMPGE,no));
        get(l,ITEM,INV,"Let;");var(l,ILOAD,0);l.add(new MethodInsnNode(INVOKEVIRTUAL,VECTOR,"a","(I)Ljava/lang/Object;",false));var(l,ASTORE,1);
        var(l,ALOAD,1);l.add(new TypeInsnNode(INSTANCEOF,ITEM));l.add(new JumpInsnNode(IFEQ,next));
        var(l,ALOAD,1);l.add(new TypeInsnNode(CHECKCAST,ITEM));call(l,B,"saleEligible","("+ID+")Z");l.add(new JumpInsnNode(IFEQ,next));value(l,1);l.add(new InsnNode(IRETURN));
        l.add(next);l.add(new IincInsnNode(0,1));l.add(new JumpInsnNode(GOTO,loop));l.add(no);value(l,0);l.add(new InsnNode(IRETURN));

        l=add(c,"disableSale","()V").instructions;
        value(l,0);put(l,SALE,ENABLED,"Z");call(l,FARM,n("lliiilli"),"()V");
        l.add(new LdcInsnNode("Túi gần đầy nhưng không có đồ phù hợp bộ lọc. Đã tắt Auto bán rác."));call(l,MOD,n("lilli"),"(Ljava/lang/String;)V");l.add(new InsnNode(RETURN));
        l=add(c,"preflightSale","()Z").instructions;LabelNode yes=new LabelNode();
        value(l,0);put(l,B,"stopAfterCycle","Z");call(l,B,"hasSaleCandidates","()Z");l.add(new JumpInsnNode(IFNE,yes));
        call(l,B,"disableSale","()V");value(l,0);l.add(new InsnNode(IRETURN));l.add(yes);value(l,1);l.add(new InsnNode(IRETURN));
        l=add(c,"afterSaleScan","()V").instructions;LabelNode done=new LabelNode();
        get(l,ITEM,INV,"Let;");l.add(new JumpInsnNode(IFNULL,done));get(l,PLAYER,n("lliliili"),"S");var(l,ISTORE,0);
        var(l,ILOAD,0);LabelNode cap=new LabelNode();l.add(new JumpInsnNode(IFGT,cap));value(l,30);var(l,ISTORE,0);l.add(cap);
        get(l,ITEM,INV,"Let;");l.add(new MethodInsnNode(INVOKEVIRTUAL,VECTOR,"c","()I",false));var(l,ILOAD,0);value(l,3);l.add(new InsnNode(ISUB));l.add(new JumpInsnNode(IF_ICMPLT,done));
        value(l,1);put(l,B,"stopAfterCycle","Z");l.add(done);l.add(new InsnNode(RETURN));
        l=add(c,"completeSaleCycle","()V").instructions;done=new LabelNode();get(l,B,"stopAfterCycle","Z");l.add(new JumpInsnNode(IFEQ,done));
        value(l,0);put(l,B,"stopAfterCycle","Z");call(l,B,"disableSale","()V");l.add(done);l.add(new InsnNode(RETURN));

        l=add(c,"addPetRows","(Let;)V").instructions;addRow(l,0,"Ăn trang bị thuộc loại >",-118);l.add(new InsnNode(RETURN));
        l=add(c,"addSaleRows","(Let;)V").instructions;
        var(l,ALOAD,0);l.add(new TypeInsnNode(NEW,"bt"));l.add(new InsnNode(DUP));call(l,S,"lockedLabel","()Ljava/lang/String;");rowTail(l,-123);
        addRow(l,0,"Bán rác thuộc loại >",-124);l.add(new InsnNode(RETURN));
        l=add(c,"showGroups","(Z)V").instructions;l.add(new TypeInsnNode(NEW,VECTOR));l.add(new InsnNode(DUP));l.add(new LdcInsnNode("Equipment stat groups"));
        l.add(new MethodInsnNode(INVOKESPECIAL,VECTOR,"<init>","(Ljava/lang/String;)V",false));var(l,ASTORE,1);
        for(int i=0;i<4;i++){
            var(l,ALOAD,1);l.add(new TypeInsnNode(NEW,"bt"));l.add(new InsnNode(DUP));var(l,ILOAD,0);value(l,1<<i);call(l,S,"groupLabel","(ZI)Ljava/lang/String;");
            var(l,ILOAD,0);LabelNode pet=new LabelNode(),action=new LabelNode();l.add(new JumpInsnNode(IFEQ,pet));value(l,-125-i);l.add(new JumpInsnNode(GOTO,action));l.add(pet);value(l,-119-i);l.add(action);
            get(l,MAIN,n("liiiliiiiil"),"L"+WORLD+";");l.add(new MethodInsnNode(INVOKESPECIAL,"bt","<init>","(Ljava/lang/String;IL"+LISTENER+";)V",false));l.add(new MethodInsnNode(INVOKEVIRTUAL,VECTOR,ITEM_ADD,"(Ljava/lang/Object;)V",false));
        }
        var(l,ALOAD,1);l.add(new TypeInsnNode(NEW,"bt"));l.add(new InsnNode(DUP));l.add(new LdcInsnNode("<< Quay Lại"));
        var(l,ILOAD,0);LabelNode petBack=new LabelNode(),back=new LabelNode();l.add(new JumpInsnNode(IFEQ,petBack));value(l,-74);l.add(new JumpInsnNode(GOTO,back));l.add(petBack);value(l,-70);l.add(back);
        get(l,MAIN,n("liiiliiiiil"),"L"+WORLD+";");l.add(new MethodInsnNode(INVOKESPECIAL,"bt","<init>","(Ljava/lang/String;IL"+LISTENER+";)V",false));l.add(new MethodInsnNode(INVOKEVIRTUAL,VECTOR,ITEM_ADD,"(Ljava/lang/Object;)V",false));
        get(l,MAIN,n("liiiliillll"),"L"+MENU+";");var(l,ALOAD,1);value(l,2);var(l,ILOAD,0);
        LabelNode petTitle=new LabelNode(),title=new LabelNode();l.add(new JumpInsnNode(IFEQ,petTitle));l.add(new LdcInsnNode("LOẠI ĐỒ BÁN RÁC"));l.add(new JumpInsnNode(GOTO,title));l.add(petTitle);l.add(new LdcInsnNode("LOẠI ĐỒ CHO PET"));l.add(title);
        value(l,0);l.add(new InsnNode(ACONST_NULL));l.add(new MethodInsnNode(INVOKEVIRTUAL,MENU,n(""),"(Let;ILjava/lang/String;ZLet;)V",false));l.add(new InsnNode(RETURN));
        for(boolean sale:new boolean[]{false,true}) actionHandler(c,sale);
        return c;
    }
    static void actionHandler(ClassNode c,boolean sale){
        InsnList l=add(c,sale?"handleSaleAction":"handlePetAction","(I)Z").instructions;
        int open=sale?-124:-118,first=sale?-125:-119;LabelNode next=new LabelNode();
        var(l,ILOAD,0);value(l,open);l.add(new JumpInsnNode(IF_ICMPNE,next));value(l,sale?1:0);call(l,B,"showGroups","(Z)V");value(l,1);l.add(new InsnNode(IRETURN));l.add(next);
        if(sale){next=new LabelNode();var(l,ILOAD,0);value(l,-123);l.add(new JumpInsnNode(IF_ICMPNE,next));call(l,S,"toggleLockedOnly","()Z");saveNotice(l);call(l,SALE,n("lilliiil"),"()V");value(l,1);l.add(new InsnNode(IRETURN));l.add(next);}
        for(int i=0;i<4;i++){next=new LabelNode();var(l,ILOAD,0);value(l,first-i);l.add(new JumpInsnNode(IF_ICMPNE,next));value(l,sale?1:0);value(l,1<<i);call(l,S,"toggleGroup","(ZI)Z");saveNotice(l);value(l,sale?1:0);call(l,B,"showGroups","(Z)V");value(l,1);l.add(new InsnNode(IRETURN));l.add(next);}
        value(l,0);l.add(new InsnNode(IRETURN));
    }
    static void saveNotice(InsnList l){LabelNode ok=new LabelNode();l.add(new JumpInsnNode(IFNE,ok));l.add(new LdcInsnNode("Không lưu được bộ lọc. Lựa chọn vẫn dùng trong phiên này."));call(l,MOD,n("lilli"),"(Ljava/lang/String;)V");l.add(ok);}
    static void menuHook(ClassNode c,boolean sale){
        MethodNode m=method(c,n("lilliiil"),"()V");List<AbstractInsnNode> r=real(m);int end=-1;
        for(int i=0;i<r.size();i++)if(HungAutoAttackNativeBuilder.call(r.get(i),"bt","<init>","(Ljava/lang/String;IL"+LISTENER+";)V")){
            if(HungAutoAttackNativeBuilder.integer(r.get(i-2))==-39)end=i;
        }
        check(end>0,"Exactly native back row");int start=end;
        // The final row starts with ALOAD 0; NEW bt; DUP before its encrypted label.
        while(start>1 && !(r.get(start).getOpcode()==ALOAD&&((VarInsnNode)r.get(start)).var==0&&r.get(start+1).getOpcode()==NEW&&((TypeInsnNode)r.get(start+1)).desc.equals("bt")))start--;
        check(start>1,"Back row boundary");InsnList hook=new InsnList();var(hook,ALOAD,0);call(hook,B,sale?"addSaleRows":"addPetRows","(Let;)V");m.instructions.insertBefore(r.get(start),hook);
        MethodNode action=method(c,"onAction","(II)Z");hook=new InsnList();var(hook,ILOAD,1);call(hook,B,sale?"handleSaleAction":"handlePetAction","(I)Z");LabelNode original=new LabelNode();hook.add(new JumpInsnNode(IFEQ,original));value(hook,1);hook.add(new InsnNode(IRETURN));hook.add(original);action.instructions.insert(hook);
    }
    static void petHook(ClassNode c){
        MethodNode m=method(c,"update","()V");List<AbstractInsnNode> r=real(m);int count=0;
        for(int i=0;i<r.size()-2;i++)if(HungAutoAttackNativeBuilder.field(r.get(i),GETSTATIC,PET,n("lililllllil"),"I") && r.get(i+1).getOpcode()==IF_ICMPGT){
            InsnList hook=new InsnList();var(hook,ALOAD,1);hook.add(new TypeInsnNode(CHECKCAST,ITEM));call(hook,B,"acceptsPet","("+ID+")Z");hook.add(new JumpInsnNode(IFEQ,((JumpInsnNode)r.get(i+1)).label));m.instructions.insert(r.get(i+1),hook);count++;
        }check(count==1,"Single native pet equipment grade acceptance");
    }
    static void saleHooks(ClassNode c){
        MethodNode m=method(c,"update","()V");List<AbstractInsnNode> r=real(m);int accept=0,preflight=0,exhausted=0,complete=0;
        for(int i=0;i<r.size();i++){
            AbstractInsnNode x=r.get(i);
            if(HungAutoAttackNativeBuilder.field(x,GETSTATIC,SALE,THRESHOLD,"I")&&r.get(i+1).getOpcode()==IF_ICMPGE){
                InsnList h=new InsnList();var(h,ALOAD,2);call(h,B,"acceptsSale","("+ID+")Z");h.add(new JumpInsnNode(IFEQ,((JumpInsnNode)r.get(i+1)).label));m.instructions.insert(r.get(i+1),h);accept++;
            }
            if(HungAutoAttackNativeBuilder.field(x,PUTSTATIC,MOD,n("liliililiil"),"Z")&&i>1&&HungAutoAttackNativeBuilder.integer(r.get(i-1))==1){
                InsnList h=new InsnList();call(h,B,"preflightSale","()Z");LabelNode go=new LabelNode();h.add(new JumpInsnNode(IFNE,go));h.add(new InsnNode(RETURN));h.add(go);m.instructions.insertBefore(r.get(i-1),h);preflight++;
            }
            if(HungAutoAttackNativeBuilder.call(x,MAIN,n("lili"),"()V")&&i+2<r.size()&&HungAutoAttackNativeBuilder.integer(r.get(i+1))==6&&HungAutoAttackNativeBuilder.field(r.get(i+2),PUTSTATIC,SALE,STATE,"I")){
                InsnList h=new InsnList();call(h,B,"afterSaleScan","()V");m.instructions.insertBefore(x,h);exhausted++;
            }
            if(HungAutoAttackNativeBuilder.field(x,PUTSTATIC,SALE,STATE,"I")&&i>1&&HungAutoAttackNativeBuilder.integer(r.get(i-1))==0&&i>r.size()-20){
                InsnList h=new InsnList();call(h,B,"completeSaleCycle","()V");m.instructions.insert(x,h);complete++;
            }
        }
        check(accept==1&&preflight==1&&exhausted==1&&complete==1,"Sale hooks "+accept+"/"+preflight+"/"+exhausted+"/"+complete);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==4,"input.jar supportDir output.jar report.properties");File input=new File(args[0]),support=new File(args[1]),outFile=new File(args[2]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned current Hung x1");check(!outFile.exists()&&!new File(args[3]).exists(),"Fresh outputs");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);verifyBoundary(before);
        ClassNode pet=HungBySonExpInfoPack.node(before.get(PET+".class")),sale=HungBySonExpInfoPack.node(before.get(SALE+".class"));menuHook(pet,false);menuHook(sale,true);petHook(pet);saleHooks(sale);
        Set<String> changed=new LinkedHashSet<String>();
        for(ClassNode c:new ClassNode[]{pet,sale}){
            byte[] data=HungAutoAttackNativeBuilder.bytes(c);HungBySonExpInfoPack.verifyClass(before.get(c.name+".class"),data,new HashSet<String>(Arrays.asList(n("lilliiil")+"()V","onAction(II)Z","update()V")),Collections.<String>emptySet());out.put(c.name+".class",data);changed.add(c.name+".class");
        }
        ClassNode bridge=bridge();byte[] bridgeBytes=HungAutoAttackNativeBuilder.bytes(bridge);HungBySonExpInfoPack.screen(B+".class",bridgeBytes);out.put(B+".class",bridgeBytes);
        for(String name:new String[]{"luna/features/EquipmentFilterPolicy","luna/features/EquipmentFilterSettings"}){
            byte[] data=Files.readAllBytes(new File(support,name+".class").toPath());check(!before.containsKey(name+".class"),"New feature class");HungBySonExpInfoPack.screen(name,data);out.put(name+".class",data);
        }
        for(String name:before.keySet())if(!changed.contains(name))check(Arrays.equals(before.get(name),out.get(name)),"Unrelated native/resource changed "+name);
        check(out.size()==before.size()+3,"Only three new feature classes");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(outFile));try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        report.setProperty("input.sha256",INPUT);report.setProperty("output.sha256",HungBySonExpInfoPack.hash(outFile));report.setProperty("changed.native",String.join(",",changed));report.setProperty("added.classes","3");report.setProperty("classes","278");report.setProperty("native.other.methods","bytecode-identical");report.setProperty("resources","byte-identical");
        report.setProperty("item.owner",ITEM);report.setProperty("item.slot",SLOT);report.setProperty("item.job",JOB);report.setProperty("item.lock",LOCK);report.setProperty("sale.quality.comparison","0 <= quality < native threshold");report.setProperty("full.trigger","inventory >= capacity - 3; native fallback capacity 30");
        OutputStream cfg=new FileOutputStream(args[3]);try{report.store(cfg,"Pinned equipment filter native boundary and build result");}finally{cfg.close();}
        System.out.println("EQUIPMENT_FILTERS_PATCH=PASS SHA256="+report.getProperty("output.sha256"));
    }
}
