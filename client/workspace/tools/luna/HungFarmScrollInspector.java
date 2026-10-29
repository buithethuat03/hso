import java.io.*;
import java.net.*;

/** Decodes the existing Farm return-scroll name marker without starting the game. */
public final class HungFarmScrollInspector {
    public static void main(String[] a)throws Exception {
        URLClassLoader loader=new URLClassLoader(new URL[]{new File(a[0]).toURI().toURL()},null);
        byte[] bytes={104,-23,-109,75,10,92,-53,-111,-85,10,70,-23,-118,68,77};
        try { System.out.println(Class.forName("key",true,loader).getMethod("d",byte[].class).invoke(null,(Object)bytes)); }
        finally { loader.close(); }
    }
}
