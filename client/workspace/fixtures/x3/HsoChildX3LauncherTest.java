import java.io.File;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import org.microemu.*;
import org.microemu.app.*;
import org.microemu.app.util.DeviceEntry;
import org.microemu.device.j2se.*;

/** Exercises the supplied launcher's real menu, long-press *, and live clients. */
public final class HsoChildX3LauncherTest {
    static void check(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    static Object[] virtuals(MIDlet launcher) throws Exception {
        for (Field f:launcher.getClass().getDeclaredFields()) {
            if (f.getType().isArray() && f.getType().getComponentType().getName().equals("lib.MIDlet")) {
                f.setAccessible(true); return (Object[])f.get(null);
            }
        }
        throw new AssertionError("Missing virtual clients");
    }
    static void snapshot(Common emulator,File file) throws Exception {
        J2SEDeviceDisplay display=(J2SEDeviceDisplay)emulator.getDevice().getDeviceDisplay();
        BufferedImage image=new BufferedImage(display.getFullWidth(),display.getFullHeight(),BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics graphics=image.getGraphics();
        display.paintDisplayable(graphics,0,0,image.getWidth(),image.getHeight()); graphics.dispose();
        ImageIO.write(image,"png",file);
    }
    public static void main(String[] args) {
        try { run(args); System.out.println("PASS: one native launcher; three live virtual clients; held-* menu and 1 -> 2 -> 3 -> 1 switch preserve instances and live game loops"); System.exit(0); }
        catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    static void run(String[] args) throws Exception {
        Headless headless=new Headless();
        Field ef=Headless.class.getDeclaredField("emulator"); ef.setAccessible(true);
        Common emulator=(Common)ef.get(headless);
        ArrayList<String> options=new ArrayList<String>();
        options.add("--rms"); options.add("memory"); options.add(new File(args[0]).getAbsolutePath());
        DeviceEntry device=new DeviceEntry("Default device",null,"org/microemu/device/default/device.xml",true,false);
        check(emulator.initParams(options,device,J2SEDevice.class),"Emulator init"); emulator.initMIDlet(true);
        Thread.sleep(400);
        MIDlet launcher=MIDletBridge.getCurrentMIDlet();
        check(launcher!=null && launcher.getClass().getName().equals("MrQuyet.MrQuyet"),"Wrong native launcher");
        ClassLoader loader=launcher.getClass().getClassLoader();
        Class<?> displayClass=Class.forName("lib.Display",false,loader);
        Display display=(Display)displayClass.getField("d").get(null);
        check(display!=null,"Launcher display missing");
        check(display.getCurrent() instanceof List,"Launcher must show client menu");
        List menu=(List)display.getCurrent();
        check(menu.size()==3,"Expected exactly three client rows");
        String label=args.length>2?args[2]:"HSO CHILD";
        for (int i=0;i<3;i++) { System.out.println("Menu row "+i+": "+menu.getString(i)); check(menu.getString(i).startsWith(label+" "+(i+1)),"Wrong client label"); }
        File screenshots=new File(args[1]); screenshots.mkdirs(); snapshot(emulator,new File(screenshots,"launcher.png"));
        Method select=launcher.getClass().getMethod("commandAction",Command.class,Displayable.class);
        Object[] initial=new Object[3]; Displayable[] canvases=new Displayable[3];
        for (int visit=0;visit<4;visit++) {
            int slot=visit%3;
            menu.setSelectedIndex(slot,true);
            select.invoke(launcher,new Command("Select",Command.OK,0),menu);
            long until=System.currentTimeMillis()+4000;
            while ((virtuals(launcher)[slot]==null || display.getCurrent() instanceof List) && System.currentTimeMillis()<until) Thread.sleep(50);
            Object current=virtuals(launcher)[slot]; check(current!=null,"Client "+slot+" not started");
            if (visit<3) initial[slot]=current; else check(current==initial[0],"Switching restarted client 1");
            Displayable canvas=display.getCurrent(); check(canvas instanceof Canvas,"Client canvas missing for "+slot);
            if (visit<3) canvases[slot]=canvas; else check(canvas==canvases[0],"Switching replaced client 1 canvas");
            Thread.sleep(args.length>2?1800:500);
            snapshot(emulator,new File(screenshots,"client-"+(slot+1)+"-visit-"+visit+".png"));
            if (visit<3) {
                // Use the existing wrapper's 500ms held-* gesture, not pause() directly.
                canvas.getClass().getMethod("keyPressed",int.class).invoke(canvas,42);
                until=System.currentTimeMillis()+2000;
                while (display.getCurrent()!=menu && System.currentTimeMillis()<until) Thread.sleep(25);
                check(display.getCurrent()==menu,"Held-* did not return to client menu");
                canvas.getClass().getMethod("keyReleased",int.class).invoke(canvas,42);
            }
        }
        Object[] running=virtuals(launcher);
        for (int i=0;i<3;i++) {
            check(running[i]==initial[i],"Client instance replaced: "+i);
            boolean alive=false;
            for (Field f:canvases[i].getClass().getDeclaredFields()) if (Thread.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true); Thread loop=(Thread)f.get(canvases[i]); if (loop!=null && loop.isAlive()) alive=true;
            }
            // Hung starts Thread(this) without retaining a Thread field on its canvas.
            if (!alive) for (java.util.Map.Entry<Thread,StackTraceElement[]> thread:Thread.getAllStackTraces().entrySet()) {
                if (!thread.getKey().isAlive()) continue;
                for (StackTraceElement frame:thread.getValue()) if (frame.getClassName().equals(canvases[i].getClass().getName())&&frame.getMethodName().equals("run")) alive=true;
            }
            check(alive,"Game loop stopped: "+i);
            String expected=i==0 ? "TemMidlet" : (i==1 ? "b.TemMidlet" : "c.TemMidlet");
            check(running[i].getClass().getName().equals(expected),"Wrong virtual namespace");
        }
    }
}
