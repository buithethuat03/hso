import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Runs the independent combat fixture while the tempo stage is being assembled. */
public final class HungBySonCombatParityProbe {
    public static void main(String[] args) throws Exception {
        Properties symbols = new Properties();
        InputStream in = new FileInputStream(args[2]);
        try { symbols.load(in); } finally { in.close(); }
        Class<?> fixture = Class.forName("HungBySonBachHoParityHostTest");
        for (String name : new String[]{"testAutoGate", "testRequestGate", "testTargetSelection", "testFastCast"}) {
            Method method = fixture.getDeclaredMethod(name, File.class, File.class, Properties.class);
            method.setAccessible(true);
            try { method.invoke(null, new File(args[0]), new File(args[1]), symbols); }
            catch (InvocationTargetException error) { throw new RuntimeException(name, error.getCause()); }
        }
        System.out.println("COMBAT_DIFFERENTIAL=PASS");
    }
}
