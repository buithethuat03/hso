import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.JarFile;
import javax.microedition.lcdui.*;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.app.Headless;
import org.microemu.app.util.DeviceEntry;
import org.microemu.device.j2se.J2SEDevice;
import org.microemu.util.MemoryRecordStoreManager;

/** Actual candidate Canvas input -> native popup selection -> native callback. */
public final class HungBySonAutoAttackInputBinaryTest {
    static int checks;
    static Properties symbols;
    static ClassLoader loader;
    static Class<?> runtime, engineType, popupType;
    static Object canvas, bridge, popup;
    static final java.util.List<Integer> handled = new ArrayList<Integer>();
    static String n(String tail){StringBuilder s=new StringBuilder();while(s.length()+tail.length()<100)s.append('i');return s.append(tail).toString();}
    static void require(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    static Field field(Class<?> c,String name)throws Exception{for(Class<?> t=c;t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static Method method(Class<?> c,String name,Class<?>...args)throws Exception{for(Class<?> t=c;t!=null;t=t.getSuperclass())try{Method m=t.getDeclaredMethod(name,args);m.setAccessible(true);return m;}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}
    static Object invoke(Method m,Object o,Object...args)throws Exception{try{return m.invoke(o,args);}catch(InvocationTargetException e){Throwable t=e.getCause();if(t instanceof Exception)throw(Exception)t;if(t instanceof Error)throw(Error)t;throw e;}}
    static Class<?> cls(String s)throws Exception{return Class.forName(s.replace('/','.'),true,loader);}
    static String prop(String s){String x=symbols.getProperty(s);if(x==null)throw new AssertionError("Missing "+s);return x;}
    static boolean enabled()throws Exception{return((Boolean)invoke(method(runtime,"enabled"),null)).booleanValue();}
    static boolean pk()throws Exception{return((Boolean)invoke(method(runtime,"playersEnabled"),null)).booleanValue();}
    static void poll()throws Exception{invoke(method(popupType,n("liiiiiil")),popup);}
    static void frame()throws Exception{invoke(method(popupType,n("liiiiill")),popup);}
    static Object items()throws Exception{return field(popupType,n("lllliliiii")).get(popup);}
    static int rows()throws Exception{return((Integer)invoke(method(cls("et"),"c"),items())).intValue();}
    static int index()throws Exception{return field(popupType,"h").getInt(popup);}
    static void clearInput()throws Exception{invoke(method(engineType,n("liilili")),null);field(popupType,n("llliiiiill")).setBoolean(null,true);}
    static void nativeDisplay(Object list,String title)throws Exception{
        Class<?> nativeList=cls("et");
        invoke(method(popupType,prop("native.menu.display.method"),nativeList,Integer.TYPE,String.class,Boolean.TYPE,nativeList),popup,list,Integer.valueOf(2),title,Boolean.FALSE,null);
        field(popupType,n("llliiiiill")).setBoolean(null,true);
        require(field(popupType,n("liiillli")).getBoolean(popup),"Actual native popup display initialized the shown state");
    }
    static void key(int code)throws Exception{
        invoke(method(canvas.getClass(),"keyPressed",Integer.TYPE),canvas,Integer.valueOf(code));poll();
        invoke(method(canvas.getClass(),"keyReleased",Integer.TYPE),canvas,Integer.valueOf(code));
    }
    static void keyboardSelect(int row)throws Exception{
        clearInput();if(index()<0)key(-2);int start=index();
        for(int i=0;i<((row-start+rows())%rows());i++)key(-2);
        require(index()==row,"Actual native Down key selects popup row "+row+" actual="+index()+" start="+start);
        key(-5);
    }
    static void touchSelect(int row)throws Exception{
        clearInput();
        int x=field(popupType,"j").getInt(popup)+field(popupType,"O").getInt(popup)/2;
        int y=field(popupType,"k").getInt(popup)+row*field(popupType,"l").getInt(popup)+field(popupType,"l").getInt(popup)/2;
        Class<?> c=canvas.getClass();
        invoke(method(c,"pointerPressed",Integer.TYPE,Integer.TYPE),canvas,Integer.valueOf(x),Integer.valueOf(y));poll();
        invoke(method(c,"pointerReleased",Integer.TYPE,Integer.TYPE),canvas,Integer.valueOf(x),Integer.valueOf(y));poll();
        for(int i=0;i<6;i++)frame();
    }
    public static void main(String[]args)throws Exception{
        if(args.length==5&&"--worker".equals(args[0])){int exit=0;try{worker(new File(args[1]),new File(args[2]),new File(args[4]));}catch(Throwable failure){failure.printStackTrace();exit=1;}System.exit(exit);}
        require(args.length==4,"candidate.jar catalog.properties microemulator.jar reportDir");
        File report=new File(args[3]);report.mkdirs();
        String java=new File(new File(System.getProperty("java.home"),"bin"),System.getProperty("os.name").toLowerCase().contains("win")?"java.exe":"java").getAbsolutePath();
        java.util.List<String> command=new ArrayList<String>();Collections.addAll(command,java,"-Xverify:all","-Djava.awt.headless=true","-Dsun.reflect.inflationThreshold=10000","-cp",System.getProperty("java.class.path"),HungBySonAutoAttackInputBinaryTest.class.getName(),"--worker",new File(args[0]).getAbsolutePath(),new File(args[1]).getAbsolutePath(),new File(args[2]).getAbsolutePath(),report.getAbsolutePath());
        Process process=new ProcessBuilder(command).inheritIO().start();require(process.waitFor()==0,"Native input event worker passed");
        require(new File(report,"native-input-events.properties").isFile(),"Native event report exists");System.out.println("NATIVE_INPUT_EVENTS_VERIFIED=true");
    }
    static void worker(File jar,File catalog,File report)throws Exception{
        symbols=new Properties();InputStream in=new FileInputStream(catalog);try{symbols.load(in);}finally{in.close();}byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(jar.toPath()));StringBuilder hash=new StringBuilder();for(byte value:digest)hash.append(String.format("%02X",value&255));require(hash.toString().equals(prop("output.sha256")),"Native input candidate hash matches its catalog");
        Headless headless=new Headless();Field ef=Headless.class.getDeclaredField("emulator");ef.setAccessible(true);Common emulator=(Common)ef.get(headless);
        MemoryRecordStoreManager rms=new MemoryRecordStoreManager();rms.init(emulator);emulator.setRecordStoreManager(rms);MIDletBridge.setMicroEmulator(emulator);
        java.util.List<String> options=new ArrayList<String>();options.add("--rms");options.add("memory");options.add(jar.getAbsolutePath());
        require(emulator.initParams(options,new DeviceEntry("Default device",null,"org/microemu/device/default/device.xml",true,false),J2SEDevice.class),"Memory RMS emulator initialized");emulator.initMIDlet(true);Thread.sleep(700L);
        javax.microedition.midlet.MIDlet active=MIDletBridge.getCurrentMIDlet();require(active!=null,"Actual candidate MIDlet starts");loader=active.getClass().getClassLoader();
        runtime=cls("luna.features.AutoAttackRuntime");engineType=cls(n("liilllii"));popupType=cls(prop("native.popup.descriptor").substring(1,prop("native.popup.descriptor").length()-1));
        canvas=field(active.getClass(),"a").get(null);require(canvas!=null,"Actual native Canvas exists");field(canvas.getClass(),"b").setInt(null,1);
        popup=field(cls(prop("native.popup.owner")),prop("native.popup.field")).get(null);require(popup!=null,"Actual native popup singleton exists");
        bridge=cls(prop("bridge.owner")).getConstructor().newInstance();final Object realBridge=bridge;
        Class<?> api=cls("luna.api.AutoAttackAccess"),settings=cls("luna.api.AutoAttackSettings"),clock=cls("luna.api.LunaClock");
        Object access=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{api},new InvocationHandler(){public Object invoke(Object p,Method m,Object[]a)throws Throwable{
            String name=m.getName();if(name.equals("ready"))return Boolean.FALSE;if(name.equals("player"))return null;if(name.equals("stopFarm")){handled.add(-40);return null;}
            if(name.equals("showMenu")||name.equals("showDelayInput")||name.equals("notice"))return HungBySonAutoAttackInputBinaryTest.invoke(method(realBridge.getClass(),name,m.getParameterTypes()),realBridge,a);
            if(m.getReturnType()==Boolean.TYPE)return Boolean.FALSE;if(m.getReturnType()==Integer.TYPE)return Integer.valueOf(0);return null;
        }});
        Object config=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{settings},new InvocationHandler(){public Object invoke(Object p,Method m,Object[]a){if(m.getName().equals("load")){int[]v=(int[])a[0];v[0]=0;v[1]=650;}return null;}});
        Object time=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{clock},new InvocationHandler(){public Object invoke(Object p,Method m,Object[]a){return Long.valueOf(0);}});
        invoke(method(runtime,"installForTest",api,settings,clock),null,access,config,time);
        Object root=cls("et").getConstructor(String.class).newInstance("Input fixture root");invoke(method(bridge.getClass(),"addMenu",cls("et")),bridge,root);
        nativeDisplay(root,"MOD");require(rows()==1,"One native root row");keyboardSelect(0);
        JarFile candidateArchive=new JarFile(jar);boolean battlefieldProfile;
        try{battlefieldProfile=candidateArchive.getEntry("luna/features/BattlefieldSupport.class")!=null;}finally{candidateArchive.close();}
        boolean specialProfile=symbols.getProperty("special.menu.action")!=null;
        int expectedChildRows=(battlefieldProfile?5:4)+(specialProfile?1:0)+(symbols.getProperty("normal.monsters.action")!=null?1:0);
        require(rows()==expectedChildRows,"Keyboard root activation opens all "+expectedChildRows+" native child rows for "+(battlefieldProfile?"Battlefield":"standard")+" profile");
        keyboardSelect(1);require(enabled()&&!pk(),"Keyboard native monster row enables mode 7");keyboardSelect(2);require(enabled()&&pk(),"Keyboard native PK row enables mode 6");
        keyboardSelect(0);require(!enabled(),"Keyboard native master row toggles OFF");
        touchSelect(2);require(enabled()&&pk(),"Touch native PK row enables mode 6");touchSelect(1);require(enabled()&&!pk(),"Touch native monster row enables mode 7");
        touchSelect(0);require(!enabled(),"Touch native master row toggles OFF");
        keyboardSelect(3);require(awaitEditor(active),"Keyboard native delay row opens numeric editor");
        cancelEditor(active);nativeDisplay(items(),"AUTO ĐÁNH");touchSelect(3);require(awaitEditor(active),"Touch native delay row opens numeric editor");cancelEditor(active);
        Properties result=new Properties();result.setProperty("checks",String.valueOf(checks));result.setProperty("keyboard","Canvas keyPressed/keyReleased -> native popup poll -> native callback PASS");result.setProperty("touch","Canvas pointerPressed/pointerReleased -> native popup poll/frame -> native callback PASS");OutputStream out=new FileOutputStream(new File(report,"native-input-events.properties"));try{result.store(out,"Actual candidate native input routing");}finally{out.close();}System.out.println("INPUT_WORKER=PASS checks="+checks);
    }
    static boolean awaitEditor(javax.microedition.midlet.MIDlet active)throws Exception{for(int i=0;i<100;i++){if(Display.getDisplay(active).getCurrent() instanceof TextBox)return true;Thread.sleep(20);}return false;}
    static void cancelEditor(javax.microedition.midlet.MIDlet active)throws Exception{Displayable editor=Display.getDisplay(active).getCurrent();Field cancel=field(editor.getClass(),"cancel");invoke(method(editor.getClass(),"commandAction",Command.class,Displayable.class),editor,cancel.get(editor),editor);for(int i=0;i<100&&Display.getDisplay(active).getCurrent()!=canvas;i++)Thread.sleep(20);require(Display.getDisplay(active).getCurrent()==canvas,"Cancel restores native Canvas");}
}
