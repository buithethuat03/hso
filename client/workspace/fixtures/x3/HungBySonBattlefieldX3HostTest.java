import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Per-slot Battlefield bridge, snapshot, mode, font and FIFO checks for Hung X3. */
public final class HungBySonBattlefieldX3HostTest {
    private static final String[] PREFIXES={"","b.","c."};
    private static final String[] PATHS={"","b/","c/"};
    private static final String SUPPORT="luna/features/BattlefieldSupport";
    private static final String API="luna/api/BattlefieldAccess";
    private static final String BRIDGE="luna/generated/BattlefieldBridge";
    private static final String AUTO_BRIDGE="luna/generated/HungAutoAttackAccess";
    private static final String SHARED_EXP="luna/generated/ExpEstimateSharedRmsStore";
    private static int checks;

    private HungBySonBattlefieldX3HostTest() { }

    public static void main(String[] args) throws Exception {
        check(args.length==5,"x3.jar single.jar auto.properties battlefield.properties pack-report[.properties]");
        File candidate=new File(args[0]),single=new File(args[1]);
        Properties auto=load(new File(args[2]));
        Properties battlefield=load(new File(args[3]));
        File packFile=new File(args[4]);
        if(!packFile.getName().endsWith(".properties"))packFile=new File(packFile.getPath()+".properties");
        Properties pack=load(packFile);
        String candidateHash=hash(candidate),singleHash=hash(single);
        check("BATTLEFIELD".equals(pack.getProperty("profile")),"X3 pack report selects the pinned Battlefield profile");
        check(candidateHash.equalsIgnoreCase(pack.getProperty("output.sha256")),"Packed X3 candidate matches strict pack report");
        check(singleHash.equalsIgnoreCase(pack.getProperty("source.sha256")),"Single source matches strict pack report");
        check(singleHash.equalsIgnoreCase(battlefield.getProperty("output.sha256")),"Battlefield catalog is bound to the exact single source");
        check("shared-exp-only".equals(pack.getProperty("cross.slot.references"))
                && "none".equals(pack.getProperty("cross.slot.unapproved.references")),
                "Only the pinned shared EXP backend crosses X3 slots");

        URLClassLoader loader=new URLClassLoader(new URL[]{candidate.toURI().toURL()},
                HungBySonBattlefieldX3HostTest.class.getClassLoader());
        JarFile jar=new JarFile(candidate);
        try {
            verifyArchiveProfile(jar,pack);
            verifyHookRemapping(jar,battlefield);
            Slot[] slots=new Slot[3];
            for(int i=0;i<3;i++) {
                slots[i]=new Slot(i,loader,auto,battlefield);
                check(!slots[i].enabled()&&slots[i].count()==0,
                        "Slot "+i+" Battlefield starts OFF with an empty FIFO");
            }
            verifyIndependentSessions(slots);
            for(int i=0;i<3;i++)slots[i].verifySnapshotAndFont();
        } finally {
            try { jar.close(); } finally { loader.close(); }
        }
        System.out.println("BATTLEFIELD_X3_VERIFIED=true");
        System.out.println("HungBySonBattlefieldX3HostTest PASS checks="+checks
                +"; 3 slot-local bridges, receiver/valid/name remaps, native lookup, snapshot alignment, red-bold selection, independent OFF/mode/FIFO state");
    }

    private static void verifyArchiveProfile(JarFile jar,Properties pack)throws Exception {
        int classes=0;
        for(Enumeration<JarEntry> entries=jar.entries();entries.hasMoreElements();) {
            JarEntry entry=entries.nextElement();
            if(entry.getName().endsWith(".class"))classes++;
        }
        check(String.valueOf(classes).equals(pack.getProperty("output.classes")),
                "Actual X3 archive class count matches pack report");
        check(jar.getJarEntry(SHARED_EXP+".class")!=null
                && jar.getJarEntry("b/"+SHARED_EXP+".class")==null
                && jar.getJarEntry("c/"+SHARED_EXP+".class")==null,
                "Single root EXP backend is the only shared Battlefield dependency");
        for(String path:PATHS) {
            check(jar.getJarEntry(path+SUPPORT+".class")!=null
                    && jar.getJarEntry(path+API+".class")!=null
                    && jar.getJarEntry(path+BRIDGE+".class")!=null,
                    "Slot "+path+" contains local Battlefield support/API/bridge classes");
        }
    }

    private static void verifyHookRemapping(JarFile jar,Properties catalog)throws Exception {
        String combatOwner=required(catalog,"combat.owner");
        String combatMethod=required(catalog,"combat.method");
        String combatDescriptor=required(catalog,"combat.descriptor");
        String entityOwner=required(catalog,"entity.owner");
        String fontOwner=required(catalog,"font.owner");
        String redField=required(catalog,"font.red.field");
        for(int slot=0;slot<3;slot++) {
            String root=PATHS[slot];
            String localBridge=root+BRIDGE;
            String localSupport=root+SUPPORT;
            String localApi=root+API;
            String descriptor=remapDescriptor(combatDescriptor,root);
            ClassNode receiver=node(jar,root+combatOwner+".class");
            MethodNode combat=method(receiver,combatMethod,descriptor);
            int instance=0,observer=0;
            for(AbstractInsnNode instruction:combat.instructions.toArray()) {
                if(instruction instanceof FieldInsnNode) {
                    FieldInsnNode field=(FieldInsnNode)instruction;
                    if(field.getOpcode()==Opcodes.GETSTATIC&&field.owner.equals(localBridge)
                            && field.name.equals("INSTANCE")&&field.desc.equals("L"+localApi+";"))instance++;
                } else if(instruction instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)instruction;
                    if(call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals(localSupport)
                            &&call.name.equals("onCombat")
                            &&call.desc.equals("(L"+localApi+";Ljava/io/DataInputStream;I)V"))observer++;
                }
            }
            check(instance==1&&observer==1,"Combat receiver in slot "+slot+" calls only its own Battlefield observer");

            ClassNode access=node(jar,root+AUTO_BRIDGE+".class");
            MethodNode valid=method(access,"validTarget","(Ljava/lang/Object;Z)Z");
            int blocked=0;
            for(AbstractInsnNode instruction:valid.instructions.toArray())if(instruction instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)instruction;
                if(call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals(localBridge)
                        &&call.name.equals("blocked")&&call.desc.equals("(Ljava/lang/Object;)Z"))blocked++;
            }
            check(blocked==1,"Auto-target validator in slot "+slot+" calls its own Battlefield gate");

            ClassNode entity=node(jar,root+entityOwner+".class");
            String expectedFontDesc="(L"+root+fontOwner+";L"+root+entityOwner+";)L"+root+fontOwner+";";
            int fontCalls=0;
            for(MethodNode method:entity.methods)for(AbstractInsnNode instruction:method.instructions.toArray())
                if(instruction instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)instruction;
                    if(call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals(localBridge)
                            &&call.name.equals("nameFont")&&call.desc.equals(expectedFontDesc))fontCalls++;
                }
            check(fontCalls==2,"Both native name render hooks in slot "+slot+" use the local font selector");

            ClassNode bridge=node(jar,root+BRIDGE+".class");
            MethodNode find=method(bridge,"find","(II)Ljava/lang/Object;");
            int nativeFind=0;
            for(AbstractInsnNode instruction:find.instructions.toArray())if(instruction instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)instruction;
                if(call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals(root+entityOwner)
                        &&call.name.equals("a")&&call.desc.equals("(IB)L"+root+entityOwner+";"))nativeFind++;
            }
            check(nativeFind==1,"Native find bridge in slot "+slot+" resolves slot-local entities");

            MethodNode nameFont=method(bridge,"nameFont",expectedFontDesc);
            boolean usesLocalGate=false,usesLocalRedFont=false;
            for(AbstractInsnNode instruction:nameFont.instructions.toArray()) {
                if(instruction instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)instruction;
                    if(call.owner.equals(localBridge)&&call.name.equals("blocked")
                            &&call.desc.equals("(Ljava/lang/Object;)Z"))usesLocalGate=true;
                } else if(instruction instanceof FieldInsnNode) {
                    FieldInsnNode field=(FieldInsnNode)instruction;
                    if(field.getOpcode()==Opcodes.GETSTATIC&&field.owner.equals(root+fontOwner)
                            &&field.name.equals(redField))usesLocalRedFont=true;
                }
            }
            check(usesLocalGate&&usesLocalRedFont,
                    "Name-font bridge in slot "+slot+" uses its own kill gate and red-bold font");
        }
    }

    private static void verifyIndependentSessions(Slot[] slots)throws Exception {
        slots[0].enable();
        check(slots[0].enabled()&&!slots[1].enabled()&&!slots[2].enabled(),
                "Enabling slot 0 does not enable slots 1 or 2");
        slots[0].observeKill("Victim-0");
        check(slots[0].count()==1&&slots[0].excluded("Victim-0")
                &&slots[1].count()==0&&slots[2].count()==0,
                "Slot 0 stores its own native-bridge kill without sharing FIFO state");

        slots[1].enable();
        check(slots[0].enabled()&&slots[1].enabled()&&!slots[2].enabled(),
                "Slot 1 mode changes independently of slots 0 and 2");
        slots[1].observeKill("Victim-1");
        check(slots[0].count()==1&&slots[1].count()==1&&slots[2].count()==0
                &&slots[0].excluded("Victim-0")&&!slots[0].excluded("Victim-1")
                &&slots[1].excluded("Victim-1")&&!slots[1].excluded("Victim-0"),
                "Slots 0 and 1 keep distinct semantic FIFOs");
        slots[1].verifyIgnoredPackets();

        slots[2].enable();
        slots[2].observeKill("Victim-2");
        check(slots[0].count()==1&&slots[1].count()==1&&slots[2].count()==1
                &&slots[0].excluded("Victim-0")&&slots[1].excluded("Victim-1")
                &&slots[2].excluded("Victim-2"),
                "All three namespace FIFOs retain only their own victim names");

        slots[1].disable();
        check(!slots[1].enabled()&&slots[1].count()==0
                &&slots[0].enabled()&&slots[0].count()==1
                &&slots[2].enabled()&&slots[2].count()==1,
                "Disabling slot 1 clears only its FIFO and mode");
        slots[1].enable();
        slots[1].observeKill("Victim-1");
        check(slots[1].count()==1&&slots[1].excluded("Victim-1")
                &&slots[0].count()==1&&slots[2].count()==1,
                "Re-enabled slot 1 has a fresh FIFO and leaves peer sessions untouched");
    }

    private static final class Slot {
        final int index;
        final String root;
        final URLClassLoader loader;
        final Properties auto,battlefield;
        final Class<?> support,api,bridgeType,entityType,playerType,worldType,listType,fontType;
        final Object bridge,local,remote,monster,list,redFont,ordinaryFont;
        final Field redFontField;
        final Method onCombat;
        final int localId,victimId,monsterId;

        Slot(int index,URLClassLoader loader,Properties auto,Properties battlefield)throws Exception {
            this.index=index;this.root=PATHS[index];this.loader=loader;this.auto=auto;this.battlefield=battlefield;
            support=type(SUPPORT);api=type(API);bridgeType=type(BRIDGE);
            entityType=type(required(battlefield,"entity.owner"));
            playerType=type(required(battlefield,"player.owner"));
            worldType=type(required(battlefield,"singleton.owner"));
            listType=type("et");
            fontType=type(required(battlefield,"font.owner"));
            bridge=bridgeType.getField("INSTANCE").get(null);
            check(bridgeType.getInterfaces().length==1&&bridgeType.getInterfaces()[0]==api,
                    "Slot "+index+" Battlefield bridge implements its namespace API");
            check(bridgeType.getMethod("find",Integer.TYPE,Integer.TYPE).getReturnType()==Object.class,
                    "Slot "+index+" bridge exposes the native find method");
            localId=700+index;victimId=800+index;monsterId=900+index;
            local=allocate(playerType);remote=allocate(entityType);monster=allocate(entityType);
            setEntity(local,localId,0,35,100,"Local-"+index);
            setEntity(remote,victimId,0,32,90,"Victim-"+index);
            setEntity(monster,monsterId,1,25,60,"Monster-"+index);
            list=listType.getConstructor().newInstance();
            Method add=listType.getMethod(required(auto,"native.list.add.method"),Object.class);
            add.invoke(list,local);add.invoke(list,remote);add.invoke(list,monster);
            field(worldType,required(auto,"native.list.field")).set(null,list);
            field(worldType,required(battlefield,"singleton.field")).set(null,local);
            check(bridgeType.getMethod("player").invoke(bridge)==local,
                    "Slot "+index+" native bridge returns its own local player object");
            check(bridgeType.getMethod("find",Integer.TYPE,Integer.TYPE).invoke(bridge,
                    Integer.valueOf(victimId),Integer.valueOf(0))==remote,
                    "Slot "+index+" native find resolves the inserted remote player");
            check(bridgeType.getMethod("find",Integer.TYPE,Integer.TYPE).invoke(bridge,
                    Integer.valueOf(monsterId),Integer.valueOf(1))==monster,
                    "Slot "+index+" native find resolves the inserted monster");
            verifyEntityAccess(remote,victimId,0,32,90,"Victim-"+index);
            verifyEntityAccess(monster,monsterId,1,25,60,"Monster-"+index);

            redFontField=field(fontType,required(battlefield,"font.red.field"));
            redFont=allocate(fontType);ordinaryFont=allocate(fontType);
            redFontField.set(null,redFont);
            onCombat=support.getMethod("onCombat",api,DataInputStream.class,Integer.TYPE);
            call(support,"resetForTest",new Class<?>[0]);
        }

        Class<?> type(String internal)throws Exception {
            return Class.forName(PREFIXES[index]+internal.replace('/','.'),true,loader);
        }

        void verifyEntityAccess(Object entity,int id,int kind,int level,int hp,String name)throws Exception {
            check(((Integer)bridgeType.getMethod("id",Object.class).invoke(bridge,entity)).intValue()==id
                    &&((Integer)bridgeType.getMethod("type",Object.class).invoke(bridge,entity)).intValue()==kind
                    &&((Integer)bridgeType.getMethod("level",Object.class).invoke(bridge,entity)).intValue()==level
                    &&((Integer)bridgeType.getMethod("hp",Object.class).invoke(bridge,entity)).intValue()==hp
                    &&name.equals(bridgeType.getMethod("name",Object.class).invoke(bridge,entity)),
                    "Slot "+index+" native id/type/level/hp/name methods read its own entity");
        }

        boolean enabled()throws Exception{return ((Boolean)call(support,"enabled",new Class<?>[0])).booleanValue();}
        int count()throws Exception{return ((Integer)call(support,"count",new Class<?>[0])).intValue();}
        boolean excluded(String name)throws Exception{return ((Boolean)call(support,"excluded",
                new Class<?>[]{String.class},name)).booleanValue();}
        void enable()throws Exception{call(support,"setEnabled",new Class<?>[]{Boolean.TYPE},Boolean.TRUE);}
        void disable()throws Exception{call(support,"setEnabled",new Class<?>[]{Boolean.TYPE},Boolean.FALSE);}

        void observeKill(String name)throws Exception {
            int victimId=9000+index*100+count();
            Object victim=allocate(entityType);setEntity(victim,victimId,0,31,75,name);
            listType.getMethod(required(auto,"native.list.add.method"),Object.class).invoke(list,victim);
            DataInputStream packet=packet(localId,victimId,25,0);
            int before=packet.available();
            invoke(onCombat,null,bridge,packet,Integer.valueOf(0));
            check(packet.available()==before&&packet.readUnsignedShort()==localId,
                    "Slot "+index+" semantic observer restores the native combat stream");
            check(count()==1&&name.equals(call(support,"nameAt",new Class<?>[]{Integer.TYPE},Integer.valueOf(0))),
                    "Slot "+index+" records the local player's final hit in its own FIFO");
        }

        void verifyIgnoredPackets()throws Exception {
            int before=count();
            DataInputStream remoteAttack=packet(localId+500,victimId,20,0);
            invoke(onCombat,null,bridge,remoteAttack,Integer.valueOf(0));
            check(count()==before,"Slot "+index+" ignores a remote player's final hit");
            DataInputStream selfHit=packet(localId,localId,20,0);
            invoke(onCombat,null,bridge,selfHit,Integer.valueOf(0));
            check(count()==before,"Slot "+index+" never queues its own character name");
            DataInputStream monsterCombat=packet(localId,victimId,20,0);
            invoke(onCombat,null,bridge,monsterCombat,Integer.valueOf(1));
            check(count()==before,"Slot "+index+" ignores monster combat packets");
        }

        void verifySnapshotAndFont()throws Exception {
            Method capturePosition=bridgeType.getMethod("capturePosition",entityType);
            Method captureTarget=bridgeType.getMethod("captureTarget",entityType);
            Method align=bridgeType.getMethod("align",entityType);
            Method blocked=bridgeType.getMethod("blocked",Object.class);
            Method nameFont=bridgeType.getMethod("nameFont",fontType,entityType);
            check(Modifier.isStatic(capturePosition.getModifiers())&&Modifier.isStatic(captureTarget.getModifiers())
                    &&Modifier.isStatic(align.getModifiers()),"Slot "+index+" snapshots and alignment are static native hooks");

            setInt(remote,"aZ",100+index);setInt(remote,"ba",200+index);
            capturePosition.invoke(null,remote);
            int playerX=getInt(remote,"battlefieldX"),playerY=getInt(remote,"battlefieldY");
            check(getBoolean(remote,"battlefieldPositionKnown")&&playerX==100+index&&playerY==200+index,
                    "Slot "+index+" captures the server position of a remote player");
            scramble(remote,index);
            check(((Boolean)align.invoke(null,remote)).booleanValue(),
                    "Slot "+index+" aligns a remote player to its captured position");
            assertSnapped(remote,playerX,playerY,"remote player");

            setInt(monster,"bg",300+index);setInt(monster,"bh",400+index);
            captureTarget.invoke(null,monster);
            int monsterX=getInt(monster,"battlefieldX"),monsterY=getInt(monster,"battlefieldY");
            check(getBoolean(monster,"battlefieldPositionKnown")&&monsterX==300+index&&monsterY==400+index,
                    "Slot "+index+" captures the packet target position of a monster");
            scramble(monster,index+10);
            check(((Boolean)align.invoke(null,monster)).booleanValue(),
                    "Slot "+index+" aligns a remote monster to its captured target");
            assertSnapped(monster,monsterX,monsterY,"remote monster");

            setInt(local,"aZ",500+index);setInt(local,"ba",600+index);
            setInt(local,"bg",700+index);setInt(local,"bh",800+index);
            setInt(local,"battlefieldX",-4);setInt(local,"battlefieldY",-5);
            setBoolean(local,"battlefieldPositionKnown",true);
            check(!((Boolean)align.invoke(null,local)).booleanValue()
                    &&getInt(local,"aZ")==500+index&&getInt(local,"ba")==600+index
                    &&getInt(local,"bg")==700+index&&getInt(local,"bh")==800+index,
                    "Slot "+index+" leaves its own local player position to native movement");

            int before=count();
            boolean hasVictim=before>0;
            if(hasVictim) {
                check(((Boolean)blocked.invoke(null,remote)).booleanValue(),
                        "Slot "+index+" blocks a recorded remote player target");
                check(nameFont.invoke(null,ordinaryFont,remote)==redFont,
                        "Slot "+index+" selects the red-bold font for a recorded remote player");
            }
            check(!((Boolean)blocked.invoke(null,monster)).booleanValue()
                    &&nameFont.invoke(null,ordinaryFont,monster)==ordinaryFont,
                    "Slot "+index+" leaves monster names in the ordinary font");

            disable();
            check(!((Boolean)align.invoke(null,remote)).booleanValue()
                    &&!((Boolean)blocked.invoke(null,remote)).booleanValue()
                    &&nameFont.invoke(null,ordinaryFont,remote)==ordinaryFont
                    &&count()==0,
                    "Slot "+index+" mode OFF disables alignment/filter/font and clears only its FIFO");
            for(int i=0;i<3;i++)if(i!=index) {
                Class<?> otherSupport=typeAt(loader,i,SUPPORT);
                check(((Boolean)call(otherSupport,"enabled",new Class<?>[0])).booleanValue(),
                        "Disabling slot "+index+" leaves peer slot "+i+" enabled");
                check(((Integer)call(otherSupport,"count",new Class<?>[0])).intValue()==1,
                        "Disabling slot "+index+" leaves peer slot "+i+" FIFO intact");
            }
            enable();
            observeKill("Restored-"+index);
        }

        private void scramble(Object entity,int salt)throws Exception {
            setInt(entity,"aZ",900+salt);setInt(entity,"bg",910+salt);
            setInt(entity,"ba",920+salt);setInt(entity,"bh",930+salt);
            setInt(entity,"bc",41);setInt(entity,"bd",42);
        }

        private void assertSnapped(Object entity,int x,int y,String kind)throws Exception {
            check(getInt(entity,"aZ")==x&&getInt(entity,"bg")==x
                    &&getInt(entity,"ba")==y&&getInt(entity,"bh")==y
                    &&getInt(entity,"bc")==0&&getInt(entity,"bd")==0,
                    "Slot "+index+" snaps "+kind+" coordinates and clears velocity");
        }
    }

    private static void setEntity(Object entity,int id,int kind,int level,int hp,String name)throws Exception {
        setInt(entity,"cu",id);setByte(entity,"cv",kind);setShort(entity,"bz",level);
        setInt(entity,"bs",hp);field(entity.getClass(),"cC").set(entity,name);
    }

    private static DataInputStream packet(int attacker,int victim,int damage,int health)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeShort(attacker);out.writeByte(19);out.writeByte(1);
        out.writeShort(victim);out.writeInt(damage);out.writeInt(health);out.writeByte(0);
        out.writeInt(100);out.writeInt(50);out.writeByte(0);out.writeInt(0);out.flush();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static Class<?> typeAt(URLClassLoader loader,int slot,String internal)throws Exception {
        return Class.forName(PREFIXES[slot]+internal.replace('/','.'),true,loader);
    }

    private static void setInt(Object target,String name,int value)throws Exception {
        Field field=field(target.getClass(),name);
        if(field.getType()==Integer.TYPE)field.setInt(target,value);
        else if(field.getType()==Short.TYPE)field.setShort(target,(short)value);
        else throw new IllegalArgumentException("Not an int/short field: "+field);
    }
    private static void setByte(Object target,String name,int value)throws Exception {field(target.getClass(),name).setByte(target,(byte)value);}
    private static void setShort(Object target,String name,int value)throws Exception {field(target.getClass(),name).setShort(target,(short)value);}
    private static void setBoolean(Object target,String name,boolean value)throws Exception {field(target.getClass(),name).setBoolean(target,value);}
    private static int getInt(Object target,String name)throws Exception {
        Field field=field(target.getClass(),name);
        if(field.getType()==Integer.TYPE)return field.getInt(target);
        if(field.getType()==Short.TYPE)return field.getShort(target);
        throw new IllegalArgumentException("Not an int/short field: "+field);
    }
    private static boolean getBoolean(Object target,String name)throws Exception {return field(target.getClass(),name).getBoolean(target);}

    private static Object allocate(Class<?> type)throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe");
        Field instance=unsafe.getDeclaredField("theUnsafe");instance.setAccessible(true);
        return unsafe.getMethod("allocateInstance",Class.class).invoke(instance.get(null),type);
    }
    private static Field field(Class<?> type,String name)throws Exception {
        for(Class<?> current=type;current!=null;current=current.getSuperclass())try {
            Field result=current.getDeclaredField(name);result.setAccessible(true);return result;
        } catch(NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName()+"."+name);
    }

    private static Object call(Class<?> type,String name,Class<?>[] parameters,Object...args)throws Exception {
        Method method=type.getMethod(name,parameters);
        try{return method.invoke(null,args);}catch(InvocationTargetException e){throwCause(e);return null;}
    }
    private static Object invoke(Method method,Object receiver,Object...args)throws Exception {
        try{return method.invoke(receiver,args);}catch(InvocationTargetException e){throwCause(e);return null;}
    }
    private static void throwCause(InvocationTargetException e)throws Exception {
        Throwable cause=e.getCause();if(cause instanceof Exception)throw(Exception)cause;
        if(cause instanceof Error)throw(Error)cause;throw e;
    }

    private static ClassNode node(JarFile jar,String entry)throws Exception {
        JarEntry jarEntry=jar.getJarEntry(entry);
        check(jarEntry!=null,"Archive contains "+entry);
        InputStream input=jar.getInputStream(jarEntry);
        try {ClassNode node=new ClassNode();new ClassReader(input).accept(node,0);return node;}
        finally {input.close();}
    }
    private static MethodNode method(ClassNode node,String name,String descriptor) {
        for(MethodNode method:node.methods)if(method.name.equals(name)&&method.desc.equals(descriptor))return method;
        throw new AssertionError("Missing "+node.name+"."+name+descriptor);
    }
    private static String remapDescriptor(String descriptor,String root) {
        StringBuilder result=new StringBuilder(descriptor.length()+16);
        for(int i=0;i<descriptor.length();i++) {
            char ch=descriptor.charAt(i);result.append(ch);
            if(ch=='L') {
                int end=descriptor.indexOf(';',i+1);
                if(end<0)throw new IllegalArgumentException("Malformed descriptor "+descriptor);
                String name=descriptor.substring(i+1,end);
                if(!root.isEmpty()&&!name.startsWith("java/")&&!name.startsWith("javax/"))result.append(root);
                result.append(name);i=end;result.append(';');
            }
        }
        return result.toString();
    }

    private static Properties load(File file)throws Exception {
        Properties result=new Properties();FileInputStream input=new FileInputStream(file);
        try {result.load(input);} finally {input.close();}return result;
    }
    private static String required(Properties values,String key) {
        String value=values.getProperty(key);
        if(value==null||value.length()==0)throw new IllegalArgumentException("Missing catalog property "+key);
        return value;
    }
    private static String hash(File file)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");FileInputStream input=new FileInputStream(file);
        try {byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}
        finally {input.close();}
        StringBuilder result=new StringBuilder();for(byte value:digest.digest())result.append(String.format("%02X",value&255));
        return result.toString();
    }
    private static void check(boolean condition,String message) {
        checks++;if(!condition)throw new AssertionError(message);
    }
}
