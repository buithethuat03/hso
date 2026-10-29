import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

/** Keeps modern diagnostic ASM separate from MicroEmulator's bundled ASM 2 API. */
public final class EntryTraceBootstrap {
    private static Class<?> implementation;
    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        ArrayList<URL> urls = new ArrayList<URL>();
        urls.add(EntryTraceBootstrap.class.getProtectionDomain().getCodeSource().getLocation());
        String dependencies = System.getProperty("entry.asm");
        if (dependencies == null) throw new IllegalArgumentException("entry.asm is required");
        for (String path : dependencies.split(java.util.regex.Pattern.quote(File.pathSeparator))) urls.add(new File(path).toURI().toURL());
        URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), null);
        implementation = Class.forName("EntryTraceAgent", true, loader);
        implementation.getMethod("premain", String.class, Instrumentation.class).invoke(null, args, instrumentation);
    }
    public static void received(int opcode, byte[] payload) { call("received", new Class<?>[]{int.class,byte[].class}, new Object[]{Integer.valueOf(opcode),payload}); }
    public static void sent(int opcode) { call("sent", new Class<?>[]{int.class}, new Object[]{Integer.valueOf(opcode)}); }
    public static void entered(String label) { call("entered", new Class<?>[]{String.class}, new Object[]{label}); }
    public static void failure(String label, Throwable failure) { call("failure", new Class<?>[]{String.class,Throwable.class}, new Object[]{label,failure}); }
    public static void state(String text) { call("state", new Class<?>[]{String.class}, new Object[]{text}); }
    public static void summary() { call("summary", new Class<?>[0], new Object[0]); }
    private static void call(String name, Class<?>[] types, Object[] values) {
        if (implementation == null) return;
        try { Method method = implementation.getMethod(name, types); method.invoke(null, values); }
        catch (Throwable ignored) { }
    }
}
