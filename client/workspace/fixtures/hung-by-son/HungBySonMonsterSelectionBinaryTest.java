import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import javax.microedition.rms.RecordStore;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.util.MemoryRecordStoreManager;

/** Exercise actual generated callbacks and native target-vector branches without game init/network. */
public final class HungBySonMonsterSelectionBinaryTest implements Opcodes {
    static final String API="luna/api/MonsterSelectionAccess", SUPPORT="luna/features/MonsterSelectionSupport", BRIDGE="luna/generated/HungMonsterSelectionAccess", COMMANDS="luna/generated/HungMonsterSelectionCommands";
    public static Object popup; public static String title;
    public static void capture(Object list,String text){popup=list;title=text;}
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    static String n(String suffix){StringBuilder b=new StringBuilder();while(b.length()+suffix.length()<100)b.append('i');return b.append(suffix).toString();}
    static Object unsafe;static Method allocate;static ClassLoader loader;static Map<String,byte[]> jar;static Properties props;
    static Class<?> type(String name)throws Exception{return Class.forName(name.replace('/','.'),true,loader);}
    static Object object(String name)throws Exception{return allocate.invoke(unsafe,type(name));}
    static Field field(Class<?> c,String name)throws Exception{for(Class<?> x=c;x!=null;x=x.getSuperclass())try{Field f=x.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static void set(Object o,String name,Object value)throws Exception{field(o.getClass(),name).set(o,value);}
    static void stat(String owner,String name,Object value)throws Exception{field(type(owner),name).set(null,value);}
    static Object stat(String owner,String name)throws Exception{return field(type(owner),name).get(null);}
    static ClassNode node(String owner){return HungBySonInvestigateBinaryTest.node(jar.get(owner+".class"));}
    static List<AbstractInsnNode> real(MethodNode m){return HungBySonInvestigateBinaryTest.real(m);}
    static MethodNode nativeMethod(String key){String pre="hook."+key;return HungBySonInvestigateBinaryTest.method(node(props.getProperty(pre+".owner")),props.getProperty(pre+".method"),props.getProperty(pre+".descriptor"));}
    static void empty(MethodNode m){m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();m.instructions.add(new InsnNode(RETURN));m.maxStack=0;}
    public static void main(String[] args)throws Exception{
        check(args.length==3,"candidate.jar catalog.properties micro.jar (micro also on host classpath)");
        jar=read(new File(args[0]));props=new Properties();InputStream in=new FileInputStream(args[1]);try{props.load(in);}finally{in.close();}
        Class<?> uc=Class.forName("sun.misc.Unsafe");Field uf=uc.getDeclaredField("theUnsafe");uf.setAccessible(true);unsafe=uf.get(null);allocate=uc.getMethod("allocateInstance",Class.class);
        Common em=new Common(null);MemoryRecordStoreManager manager=new MemoryRecordStoreManager();manager.init(em);em.setRecordStoreManager(manager);MIDletBridge.setMicroEmulator(em);
        final MethodNode menu=nativeMethod("selectionMenu"),gather=nativeMethod("selectionGather");
        String popupOwner=null,popupMethod=null,popupDesc=null;for(AbstractInsnNode i:real(menu))if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if(x.desc.equals("(Let;ILjava/lang/String;ZLet;)V")){popupOwner=x.owner;popupMethod=x.name;popupDesc=x.desc;}}
        check(popupOwner!=null,"Native popup display ABI");final String po=popupOwner,pm=popupMethod,pd=popupDesc;
        final Set<String> cleanup=new HashSet<String>();for(AbstractInsnNode i:real(HungBySonInvestigateBinaryTest.method(node("bt"),"a","()V")))if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if(x.owner.equals(n("liilllii"))&&x.desc.equals("()V"))cleanup.add(x.name);}
        // Only suppress native class initialization, popup rendering and input cleanup; callbacks stay native.
        loader=new ClassLoader(HungBySonMonsterSelectionBinaryTest.class.getClassLoader()){
            protected Class<?> findClass(String name)throws ClassNotFoundException{String path=name.replace('.','/');byte[] raw=jar.get(path+".class");if(raw==null)throw new ClassNotFoundException(name);ClassNode c=HungBySonInvestigateBinaryTest.node(raw);
                if(!path.startsWith("luna/")){for(Iterator<MethodNode> it=c.methods.iterator();it.hasNext();)if(it.next().name.equals("<clinit>"))it.remove();}
                for(MethodNode m:c.methods){if(path.equals(po)&&m.name.equals(pm)&&m.desc.equals(pd)){empty(m);m.instructions.clear();m.instructions.add(new VarInsnNode(ALOAD,1));m.instructions.add(new VarInsnNode(ALOAD,3));m.instructions.add(new MethodInsnNode(INVOKESTATIC,"HungBySonMonsterSelectionBinaryTest","capture","(Ljava/lang/Object;Ljava/lang/String;)V",false));m.instructions.add(new InsnNode(RETURN));m.maxStack=2;}
                    if(path.equals(props.getProperty("hook.selectionMenu.owner"))&&m.name.equals(menu.name)&&m.desc.equals(menu.desc))m.tryCatchBlocks.clear();
                    if(path.equals(n("liilllii"))&&cleanup.contains(m.name)&&m.desc.equals("()V"))empty(m);
                }
                ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);byte[] b=w.toByteArray();return defineClass(name,b,0,b.length);
            }};
        String entity=props.getProperty("entity.owner"),world=props.getProperty("world.owner");Object map=object("cs");set(map,"d",Integer.valueOf(41));stat(props.getProperty("map.owner"),props.getProperty("map.field"),map);stat(props.getProperty("ready.owner"),props.getProperty("ready.field"),Boolean.TRUE);
        Object entities=type("et").newInstance(),catalog=type("et").newInstance();Method add=null;for(Method m:type("et").getMethods())if(m.getParameterTypes().length==1&&m.getParameterTypes()[0]==Object.class&&m.getReturnType()==void.class){ClassNode c=node("et");MethodNode mn=HungBySonInvestigateBinaryTest.method(c,m.getName(),"(Ljava/lang/Object;)V");boolean adding=false;for(AbstractInsnNode i:real(mn))if(i instanceof MethodInsnNode&&((MethodInsnNode)i).name.equals("addElement"))adding=true;if(adding)add=m;}
        check(add!=null,"Native entity add");add.invoke(catalog,new Object());stat(props.getProperty("mob.owner"),"L",catalog);stat(props.getProperty("list.owner"),props.getProperty("list.field"),entities);
        Object chosen=monster(entity,101,"Slime",10),same=monster(entity,102,"Slime",10),other=monster(entity,103,"Wolf",10),level=monster(entity,104,"Slime",11),pk=monster(entity,105,"Player",10);set(pk,"cv",Byte.valueOf((byte)0));for(Object e:new Object[]{chosen,same,other,level,pk})add.invoke(entities,e);
        type("luna/generated/HungMonsterCacheAccess").getMethod("select",String.class,int.class).invoke(null,"selection.test",19129);
        // Native command's first lifecycle branch needs its singleton, but its false flag skips rendering.
        MethodNode click=HungBySonInvestigateBinaryTest.method(node("bt"),"a","()V");for(AbstractInsnNode i:real(click))if(i instanceof FieldInsnNode&&i.getOpcode()==GETSTATIC){FieldInsnNode f=(FieldInsnNode)i;if(f.desc.startsWith("L")){stat(f.owner,f.name,object(org.objectweb.asm.Type.getType(f.desc).getInternalName()));break;}}
        FieldInsnNode popupField=null;for(AbstractInsnNode i:real(menu))if(i instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)i;if(f.getOpcode()==GETSTATIC&&f.desc.equals("L"+po+";"))popupField=f;}check(popupField!=null,"Popup receiver");stat(popupField.owner,popupField.name,object(po));
        checkMenuHooks(menu,gather);checkPopupDispatch(po);
        // Native default range is -1; stripped initialization would incorrectly select a formatting branch.
        stat(n("lliiiiil"),n("liliiiliiii"),Integer.valueOf(-1));
        type(props.getProperty("hook.selectionMenu.owner")).getMethod(menu.name).invoke(null);Object list=popup;check(list!=null,"Actual Auto submenu displayed");Object entry=null;for(Object row:rows(list))if(label(row).equals("Chọn quái"))entry=row;check(entry!=null,"Native Auto submenu contains Chọn quái");click(entry);
        check(title!=null&&title.contains("quái"),"Selection popup title");List<Object> choices=rows(popup);check(choices.size()==5,"Grouped name+level rows plus all/done");int selected=-1;for(int k=0;k<choices.size();k++)if(label(choices.get(k)).contains("Slime")&&label(choices.get(k)).contains("10"))selected=k;check(selected>0,"Group row exists");
        stat(n("lliiiiil"),n("lililiillii"),Boolean.TRUE);stat(world,n("llillilil"),other);
        click(choices.get(selected));check(label(rows(popup).get(selected)).contains("[x]"),"Selection callback toggles group: "+title+" / "+type(SUPPORT).getMethod("status").invoke(null));check(stat(world,n("llillilil"))==null,"Selection clears rejected retained target");
        Method allow=type(COMMANDS).getMethod("allow",Object.class);check(Boolean.TRUE.equals(allow.invoke(null,chosen))&&Boolean.TRUE.equals(allow.invoke(null,same)),"Grouped instances allowed");check(Boolean.FALSE.equals(allow.invoke(null,other))&&Boolean.FALSE.equals(allow.invoke(null,level)),"Exact name and level restriction");check(Boolean.TRUE.equals(allow.invoke(null,pk)),"PK preserved");check(label(rows(popup).get(selected)).contains("[x]"),"Immediate native multi-choice marker");
        int second=-1;for(int k=0;k<rows(popup).size();k++)if(label(rows(popup).get(k)).contains("Slime")&&label(rows(popup).get(k)).contains("11"))second=k;check(second>0,"Second level group exists");
        click(rows(popup).get(second));check(Boolean.TRUE.equals(allow.invoke(null,chosen))&&Boolean.TRUE.equals(allow.invoke(null,level))&&Boolean.FALSE.equals(allow.invoke(null,other)),"Real popup dispatch supports selecting a second group");check(label(rows(popup).get(selected)).contains("[x]")&&label(rows(popup).get(second)).contains("[x]"),"Both native selection markers survive redraw");
        click(rows(popup).get(second));check(Boolean.TRUE.equals(allow.invoke(null,chosen))&&Boolean.FALSE.equals(allow.invoke(null,level))&&Boolean.FALSE.equals(allow.invoke(null,other)),"Real popup dispatch unchecks second group while retaining first");check(label(rows(popup).get(selected)).contains("[x]")&&!label(rows(popup).get(second)).contains("[x]"),"Unchecked marker updates without losing first group");
        set(map,"d",Integer.valueOf(42));check(Boolean.TRUE.equals(allow.invoke(null,other)),"Other maps unchanged");set(map,"d",Integer.valueOf(41));check(Boolean.FALSE.equals(allow.invoke(null,other)),"Map selection restores");stat(n("lliiiiil"),n("lililiillii"),Boolean.FALSE);check(Boolean.TRUE.equals(allow.invoke(null,other)),"Auto OFF/manual preserved");stat(n("lliiiiil"),n("lililiillii"),Boolean.TRUE);
        Object player=object(props.getProperty("hook.selectionGather.owner"));Object result=type(props.getProperty("hook.selectionGather.owner")).getMethod(gather.name,int.class,type(entity)).invoke(player,0,other);check(rows(result).isEmpty(),"Rejected native primary vector empty before skill access");
        // Both secondary gates must precede DTO allocation, rather than filtering already counted slots.
        secondaryBranches(gather,chosen,other,pk,allow);
        actualSecondaryVectors(gather,player,entity,chosen,same,other,level,pk,add,map);
        byte[] cacheBytes=new byte[]{1,2,3},selectionBytes=new byte[]{8,9};Class<?> storage=type("luna/generated/InvestigateStorage");
        check(Boolean.TRUE.equals(storage.getMethod("save",byte[].class).invoke(null,(Object)cacheBytes)),"Actual cache RMS write");
        check(Boolean.TRUE.equals(storage.getMethod("saveSelection",byte[].class).invoke(null,(Object)selectionBytes)),"Actual selection RMS write");
        check(Arrays.equals(cacheBytes,(byte[])storage.getMethod("load").invoke(null))&&Arrays.equals(selectionBytes,(byte[])storage.getMethod("loadSelection").invoke(null)),"Cache and selection RMS are independent");
        click(rows(popup).get(0));check(Boolean.TRUE.equals(allow.invoke(null,other)),"All monsters reset via actual callback");click(rows(popup).get(rows(popup).size()-1));check(title.equals("TÙY CHỌN TỰ ĐÁNH"),"Done returns to original Auto submenu");
        Object many=type("et").newInstance();for(int i=0;i<128;i++)add.invoke(many,monster(entity,200+i,"Group "+i,10));stat(props.getProperty("list.owner"),props.getProperty("list.field"),many);
        click(entry);check(rows(popup).size()==130,"128 groups plus all/done produce 130 native rows");
        Object high=rows(popup).get(128);check(((Byte)field(high.getClass(),"e").get(high)).byteValue()==43,"Native group row action 43");check((((Byte)field(high.getClass(),"f").get(high)).byteValue()&255)==128,"Choice index 128 survives unsigned native byte");
        click(high);check(label(rows(popup).get(128)).contains("[x]"),"Actual native dispatcher toggles group row 128");
        Object last=rows(popup).get(129);check((((Byte)field(last.getClass(),"f").get(last)).byteValue()&255)==129,"Done index 129 survives unsigned native byte");click(last);check(title.equals("TÙY CHỌN TỰ ĐÁNH"),"Actual native dispatcher routes Done row 129");
        System.out.println("MONSTER_SELECTION_BINARY=PASS; actual native popup dispatcher, keyboard/touch dispatch sites, grouping, retained clearing, actual primary/two secondary vectors, autoOFF/PK/map isolation, RMS independence, unsigned rows 128/129");
    }
    static Object monster(String entity,int id,String name,int lvl)throws Exception{Object e=object(entity);set(e,"cu",Integer.valueOf(id));set(e,"cv",Byte.valueOf((byte)1));set(e,"cC",name);set(e,"bz",Short.valueOf((short)lvl));set(e,"bs",Integer.valueOf(37));set(e,"cX",Boolean.TRUE);return e;}
    static String label(Object cmd)throws Exception{ClassNode c=node("bt");MethodNode ctor=null;for(MethodNode m:c.methods)if(m.name.equals("<init>")&&m.desc.startsWith("(Ljava/lang/String;I")){ctor=m;break;}for(AbstractInsnNode i:real(ctor))if(i instanceof FieldInsnNode&&i.getOpcode()==PUTFIELD&&((FieldInsnNode)i).desc.equals("Ljava/lang/String;")){FieldInsnNode f=(FieldInsnNode)i;String value=(String)field(cmd.getClass(),f.name).get(cmd);if(value!=null&&!value.isEmpty())return value;}for(Field f:cmd.getClass().getDeclaredFields())if(f.getType()==String.class){String value=(String)f.get(cmd);if(value!=null&&!value.isEmpty())return value;}return "";}
    static List<Object> rows(Object list)throws Exception{check(list!=null,"Popup/vector exists");Field vf=null;for(Field f:list.getClass().getFields())if(f.getType()==Vector.class)vf=f;check(vf!=null,"Native Vector field");return new ArrayList<Object>((Vector<?>)vf.get(list));}
    static void click(Object cmd)throws Exception{type(n("liilliil")).getMethod(n(""),type("bt")).invoke(null,cmd);}
    static void checkPopupDispatch(String owner){
        ClassNode c=node(owner);MethodNode dispatch=HungBySonInvestigateBinaryTest.method(c,n(""),"(Lbt;)V");int integerCallbacks=0,objectCallbacks=0;
        for(AbstractInsnNode i:real(dispatch))if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if(x.owner.equals(n("liiiiil"))&&x.name.equals("a")){if(x.desc.equals("(II)V"))integerCallbacks++;if(x.desc.equals("(ILjava/lang/Object;)V"))objectCallbacks++;}}
        check(integerCallbacks==1&&objectCallbacks==0,"Real native popup dispatcher uses byte integer callback and ignores payload");
        MethodNode keys=HungBySonInvestigateBinaryTest.method(c,n("liiiiiil"),"()V"),touch=HungBySonInvestigateBinaryTest.method(c,n("liiiiill"),"()V");int keySites=0,touchSites=0;boolean countdown=false;
        for(AbstractInsnNode i:real(keys))if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if(x.owner.equals(owner)&&x.name.equals(dispatch.name)&&x.desc.equals(dispatch.desc))keySites++;}
        for(AbstractInsnNode i:real(touch)){if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if(x.owner.equals(owner)&&x.name.equals(dispatch.name)&&x.desc.equals(dispatch.desc))touchSites++;}if(i instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)i;if(f.owner.equals(owner)&&f.name.equals(n("llllillill"))&&f.desc.equals("I"))countdown=true;}}
        check(keySites==2&&touchSites==1&&countdown,"Native keyboard confirm and touch countdown share the exercised dispatcher");
    }
    static void checkMenuHooks(MethodNode menu,MethodNode gather){int append=0,guards=0;for(AbstractInsnNode i:real(menu))if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals(COMMANDS)&&((MethodInsnNode)i).name.equals("append")){check(HungBySonInvestigateBinaryTest.priorReal(i) instanceof VarInsnNode&&((VarInsnNode)HungBySonInvestigateBinaryTest.priorReal(i)).var==0,"Append native local0");append++;}for(AbstractInsnNode i:real(gather))if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals(COMMANDS)&&((MethodInsnNode)i).name.equals("allow"))guards++;check(append==1&&guards==3,"One menu and three gather guards");}
    static void secondaryBranches(MethodNode gather,Object selected,Object rejected,Object pk,Method allow)throws Exception{int secondary=0;for(AbstractInsnNode i:real(gather))if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals(COMMANDS)&&((MethodInsnNode)i).name.equals("allow")){AbstractInsnNode prev=HungBySonInvestigateBinaryTest.priorReal(i),next=HungBySonInvestigateBinaryTest.nextReal(i);if(prev instanceof VarInsnNode&&((VarInsnNode)prev).var==4){secondary++;check(next instanceof JumpInsnNode&&next.getOpcode()==IFEQ,"Secondary skip branch");check(HungBySonInvestigateBinaryTest.nextReal(next).getOpcode()==NEW,"Gate immediately before DTO allocation");check(Boolean.TRUE.equals(allow.invoke(null,selected))&&Boolean.FALSE.equals(allow.invoke(null,rejected))&&Boolean.TRUE.equals(allow.invoke(null,pk)),"Secondary gate policy");}}check(secondary==2,"Both native secondary branches covered");}
    static void actualSecondaryVectors(MethodNode gather,Object player,String entity,Object chosen,Object same,Object other,Object level,Object pk,Method add,Object map)throws Exception{
        MethodInsnNode lookup=null;FieldInsnNode levels=null,bonus=null,tiers=null,range=null;
        for(AbstractInsnNode i:real(gather)){
            if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if(x.getOpcode()==INVOKESTATIC&&x.desc.startsWith("(I)L"))lookup=x;}
            if(i instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)i;if(f.getOpcode()==GETSTATIC&&f.desc.equals("[I")){if(levels==null)levels=f;else if(!levels.name.equals(f.name))bonus=f;}
                if(f.getOpcode()==GETFIELD&&f.desc.startsWith("[L"))tiers=f;
                if(f.getOpcode()==GETFIELD&&f.desc.equals("S"))range=f;
            }
        }
        check(lookup!=null&&levels!=null&&bonus!=null&&tiers!=null&&range!=null,"Native skill metadata discovered");
        String skillType=org.objectweb.asm.Type.getReturnType(lookup.desc).getInternalName();Object skill=object(skillType),tier=object(range.owner);set(skill,"b",0);set(skill,"g",(byte)3);set(tier,"j",(byte)2);set(tier,range.name,(short)32767);
        Object tierArray=Array.newInstance(type(range.owner),1);Array.set(tierArray,0,tier);set(skill,tiers.name,tierArray);
        MethodNode getter=HungBySonInvestigateBinaryTest.method(node(lookup.owner),lookup.name,lookup.desc);FieldInsnNode catalogField=null;for(AbstractInsnNode i:real(getter))if(i instanceof FieldInsnNode&&i.getOpcode()==GETSTATIC&&((FieldInsnNode)i).desc.equals("Let;"))catalogField=(FieldInsnNode)i;
        check(catalogField!=null,"Actual native skill catalog");Object catalog=type("et").newInstance();add.invoke(catalog,skill);stat(catalogField.owner,catalogField.name,catalog);stat(levels.owner,levels.name,new int[]{1});stat(bonus.owner,bonus.name,new int[]{0});
        Field state=field(player.getClass(),"di");state.set(player,object(state.getType().getName()));
        Object ordered=type("et").newInstance();for(Object e:new Object[]{other,level,chosen,same,pk})add.invoke(ordered,e);stat(props.getProperty("list.owner"),props.getProperty("list.field"),ordered);
        Method call=player.getClass().getMethod(gather.name,int.class,type(entity));
        for(byte mode:new byte[]{1,0}){set(skill,"e",mode);Object result=call.invoke(player,0,chosen);check(dtoIds(result).equals(Arrays.asList(101,102)),"Actual secondary mode "+mode+" skips rejected groups without consuming target slots");}
        set(tier,"j",(byte)10);stat(n("lliiiiil"),n("lililiillii"),Boolean.FALSE);check(dtoIds(call.invoke(player,0,other)).equals(Arrays.asList(103,104,101,102)),"Actual auto OFF vector retains native monster behavior");stat(n("lliiiiil"),n("lililiillii"),Boolean.TRUE);
        set(map,"d",42);check(dtoIds(call.invoke(player,0,chosen)).equals(Arrays.asList(101,103,104,102)),"Actual vector allows all in an unconfigured map");set(map,"d",41);
        stat(levels.owner,levels.name,new int[]{0});check(dtoIds(call.invoke(player,0,pk)).equals(Arrays.asList(105)),"Actual PK primary passes selection");stat(levels.owner,levels.name,new int[]{1});
    }
    static List<Integer> dtoIds(Object vector)throws Exception{List<Integer> ids=new ArrayList<Integer>();for(Object dto:rows(vector)){Field id=null;for(Field f:dto.getClass().getFields())if(f.getType()==short.class)id=f;check(id!=null,"Native target DTO short ID");ids.add(((Short)id.get(dto)).intValue());}return ids;}
    static Map<String,byte[]> read(File file)throws Exception{Map<String,byte[]> result=new LinkedHashMap<String,byte[]>();java.util.jar.JarFile z=new java.util.jar.JarFile(file);try{for(Enumeration<java.util.jar.JarEntry> e=z.entries();e.hasMoreElements();){java.util.jar.JarEntry x=e.nextElement();InputStream in=z.getInputStream(x);ByteArrayOutputStream out=new ByteArrayOutputStream();try{byte[] b=new byte[8192];int k;while((k=in.read(b))!=-1)out.write(b,0,k);}finally{in.close();}result.put(x.getName(),out.toByteArray());}}finally{z.close();}return result;}
}
