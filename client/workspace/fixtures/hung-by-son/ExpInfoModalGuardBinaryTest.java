import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import javax.microedition.midlet.MIDlet;
import org.microemu.MIDletBridge;

/** Executes native pointer/key/Farm paths against the actual patched archive. */
public final class ExpInfoModalGuardBinaryTest {
    static int checks;
    static String n(String t){StringBuilder b=new StringBuilder();for(int i=t.length();i<100;i++)b.append('i');return b.append(t).toString();}
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
    static Object helper(String name,Class<?>[] types,Object... args)throws Exception{Method m=HungBySonExpInfoBinaryTest.class.getDeclaredMethod(name,types);m.setAccessible(true);try{return m.invoke(null,args);}catch(InvocationTargetException e){Throwable t=e.getCause();if(t instanceof Exception)throw (Exception)t;throw e;}}
    static Field field(Class<?> c,String n)throws Exception{while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
    static Object get(Class<?> c,String f)throws Exception{return field(c,f).get(null);}
    static void set(Class<?> c,String f,Object v)throws Exception{field(c,f).set(null,v);}
    static Class<?> cls(ClassLoader l,String c)throws Exception{return Class.forName(c.replace('/','.'),true,l);}
    static Object allocate(Class<?> c)throws Exception{return helper("constructOrAllocate",new Class<?>[]{Class.class},c);}
    static void invoke(Method m,Object target,Object...args)throws Exception{try{m.invoke(target,args);}catch(InvocationTargetException e){Throwable t=e.getCause();if(t instanceof Exception)throw (Exception)t;throw e;}}
    public static void main(String[] a)throws Exception{
        check(a.length==4,"candidate.jar catalog.properties microemulator.jar screenshot-directory");
        File host=new File(ExpInfoModalGuardBinaryTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File java=new File(new File(System.getProperty("java.home"),"bin"),System.getProperty("os.name").contains("Windows")?"java.exe":"java");
        List<String> command=new ArrayList<String>();Collections.addAll(command,java.getAbsolutePath(),"-Xverify:all","-Djava.awt.headless=true","-Dsun.reflect.inflationThreshold=10000","-cp",host.getAbsolutePath()+File.pathSeparator+new File(a[2]).getAbsolutePath(),"ExpInfoModalGuardBinaryTest$Worker");Collections.addAll(command,a);
        int status=new ProcessBuilder(command).inheritIO().start().waitFor();check(status==0,"Native modal guard worker passed");System.out.println("ExpInfoModalGuardBinaryTest PASS");
    }
    public static final class Worker {
        public static void main(String[] a){try{run(a);System.out.println("ModalGuard native PASS checks="+checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
        static void run(String[] a)throws Exception{
            Properties p=new Properties();InputStream s=new FileInputStream(a[1]);try{p.load(s);}finally{s.close();}
            File screens=new File(a[3]);screens.mkdirs();helper("startEmulator",new Class<?>[]{File.class},new File(a[0]));MIDlet midlet=MIDletBridge.getCurrentMIDlet();check(midlet!=null,"Candidate MIDlet started");ClassLoader l=midlet.getClass().getClassLoader();
            Class<?> engine=cls(l,p.getProperty("pointer.press.owner")),ui=cls(l,"luna/generated/ExpInfoBridge"),guard=cls(l,"luna/generated/ExpInfoModalGuard"),world=cls(l,p.getProperty("slot.0.main.owner"));
            Object canvas=get(cls(l,"com/silverknight/a"),"c");check(canvas!=null,"Actual native engine receives key callbacks");Object game=get(engine,n("liiiliiiiil"));if(game==null){game=allocate(field(engine,n("liiiliiiiil")).getType());set(engine,n("liiiliiiiil"),game);}set(engine,n("liiiillllli"),game);
            Object player=get(world,p.getProperty("slot.0.main.field"));if(player==null){player=allocate(field(world,p.getProperty("slot.0.main.field")).getType());set(world,p.getProperty("slot.0.main.field"),player);}
            field(player.getClass(),p.getProperty("slot.0.id.field")).set(player,Integer.valueOf(321));field(player.getClass(),p.getProperty("slot.0.level.field")).set(player,Short.valueOf((short)70));field(player.getClass(),p.getProperty("slot.0.progress.field")).set(player,Short.valueOf((short)500));field(player.getClass(),"cG").set(player,Integer.valueOf(0));
            set(world,n("lliilliil"),Boolean.FALSE);set(engine,n("liiililiili"),null);set(engine,n("liiililiill"),null);set(cls(l,n("llilil")),n("liiillli"),Boolean.FALSE);
            Object menu=get(engine,n("liiiliillll"));if(menu!=null)field(menu.getClass(),n("liiillli")).set(menu,Boolean.FALSE);
            check(((Boolean)ui.getMethod("ready").invoke(null)).booleanValue(),"Ready in native world");ui.getMethod("open").invoke(null);Object dialog=get(engine,n("liiililiili"));check(dialog!=null,"Owned info modal opened");check(((Boolean)guard.getMethod("owned").invoke(null)).booleanValue(),"Exact modal is owned");
            Method keyDown=engine.getMethod(p.getProperty("modal.guard.keys.press"),Integer.TYPE),keyUp=engine.getMethod(p.getProperty("modal.guard.keys.release"),Integer.TYPE);Method command=dialog.getClass().getMethod("b",Integer.TYPE,Integer.TYPE);Method keys=dialog.getClass().getMethod("d");
            for(int k:new int[]{-5,10,13,-6,-7}){invoke(keyDown,canvas,Integer.valueOf(k));invoke(keys,dialog);invoke(keyUp,canvas,Integer.valueOf(k));invoke(command,dialog,Integer.valueOf(-1),Integer.valueOf(0));check(get(engine,n("liiililiili"))==dialog,"ENTER/fire/back command cannot dismiss "+k);}
            // Reproduce the actual native Farm branch which previously assigned activeDialog=null every tick.
            Class<?> farm=cls(l,p.getProperty("modal.guard.farm.b")),farmSwitch=cls(l,n("lliiiiil"));Object farmInstance=allocate(farm);set(farmSwitch,n("lililiilill"),Boolean.TRUE);set(farm,n("lililllllii"),Boolean.TRUE);set(farm,n("lilliiiiili"),Long.valueOf(System.currentTimeMillis()));Method update=farm.getMethod("update");set(farm,n("lilliiiiiii"),Integer.valueOf(100));
            long until=System.currentTimeMillis()+2200L;int updates=0;while(System.currentTimeMillis()<until){invoke(update,farmInstance);invoke(keyDown,canvas,Integer.valueOf(-5));invoke(keys,dialog);invoke(keyUp,canvas,Integer.valueOf(-5));check(get(engine,n("liiililiili"))==dialog,"Real Farm tick and ENTER preserve owned modal");updates++;Thread.sleep(50L);}check(updates>=20,"Owned modal survived more than two seconds of real native updates");check(((Integer)get(farm,n("lilliiiiiii"))).intValue()<100,"Real Farm updater reached its countdown and following native null-clear branch");set(farmSwitch,n("lililiilill"),Boolean.FALSE);set(farm,n("lililllllii"),Boolean.FALSE);
            Method press=engine.getMethod(p.getProperty("pointer.press.method"),Integer.TYPE,Integer.TYPE),release=engine.getMethod(p.getProperty("pointer.release.method"),Integer.TYPE,Integer.TYPE),drag=engine.getMethod(p.getProperty("pointer.drag.method"),Integer.TYPE,Integer.TYPE);
            int scale=((Integer)helper("graphicsScale",new Class<?>[]{ClassLoader.class,Properties.class},l,p)).intValue();int[] center=(int[])helper("confirmationCenter",new Class<?>[]{Object.class,Properties.class,Integer.TYPE},dialog,p,Integer.valueOf(scale));
            invoke(press,null,Integer.valueOf(0),Integer.valueOf(0));invoke(release,null,Integer.valueOf(0),Integer.valueOf(0));check(get(engine,n("liiililiili"))==dialog,"Pointer outside confirmation leaves modal open");
            invoke(press,null,Integer.valueOf(center[0]),Integer.valueOf(center[1]));invoke(drag,null,Integer.valueOf(0),Integer.valueOf(0));invoke(release,null,Integer.valueOf(center[0]),Integer.valueOf(center[1]));check(get(engine,n("liiililiili"))==dialog,"Drag out cancels confirmation");
            invoke(press,null,Integer.valueOf(center[0]),Integer.valueOf(center[1]));invoke(release,null,Integer.valueOf(center[0]),Integer.valueOf(center[1]));check(get(engine,n("liiililiili"))==null,"Real native confirmation pointer closes once");check(!((Boolean)guard.getMethod("owned").invoke(null)).booleanValue(),"Ownership cleared after confirmation");check(get(engine,n("liiiillllli"))==game,"Confirmation returns to same native world");
            // Another native dialog is unaffected by both command and Farm behavior.
            engine.getMethod("a",String.class).invoke(null,"Foreign native dialog");Object foreign=get(engine,n("liiililiili"));check(foreign!=null&&foreign!=dialog,"Foreign dialog opened");check(!((Boolean)guard.getMethod("blockCommand",Object.class).invoke(null,foreign)).booleanValue(),"Foreign dialog command is not blocked");invoke(foreign.getClass().getMethod("b",Integer.TYPE,Integer.TYPE),foreign,Integer.valueOf(-1),Integer.valueOf(0));check(get(engine,n("liiililiili"))==null,"Foreign native close command remains functional");
            ui.getMethod("open").invoke(null);Object finalDialog=get(engine,n("liiililiili"));check(finalDialog!=null,"Reopened info modal for lifecycle test");Object login=get(engine,n("liiiliiiiii"));check(login!=null,"Native login screen exists for lifecycle transition");set(engine,n("liiiillllli"),login);check(!((Boolean)guard.getMethod("owned").invoke(null)).booleanValue(),"Leaving world invalidates ownership without reopening");invoke(finalDialog.getClass().getMethod("b",Integer.TYPE,Integer.TYPE),finalDialog,Integer.valueOf(-1),Integer.valueOf(0));check(get(engine,n("liiililiili"))==null,"Native lifecycle cleanup is allowed after leaving world");set(engine,n("liiiillllli"),game);
        }
    }
}
