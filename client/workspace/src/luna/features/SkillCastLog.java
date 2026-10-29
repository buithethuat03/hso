package luna.features;

import java.io.File;
import java.io.FileOutputStream;

/**
 * File log of the skill casts (SkillCastStats CSV lines): one file per client run,
 * <hso.log.dir or user.home/hso_logs>/skill-gap-<epoch ms>.csv, appended once a second
 * by a background thread. Needs a desktop/Android Java runtime (KEmulator, MicroEmulator,
 * J2ME Loader); on a plain CLDC phone start() fails quietly and error() says why.
 */
public final class SkillCastLog implements Runnable {
    public static final long FLUSH_MS = 1000L;
    private static String path, error;
    private static Thread thread;

    private SkillCastLog() { }

    /** Opens the log on the first counted cast; later calls do nothing. */
    public static synchronized void start() {
        if (thread != null || error != null) return;
        try {
            String dir = System.getProperty("hso.log.dir");
            if (dir == null || dir.length() == 0) {
                String home = System.getProperty("user.home");
                dir = home == null || home.length() == 0 ? "hso_logs" : new StringBuffer(home).append(File.separator).append("hso_logs").toString();
            }
            File folder = new File(dir);
            if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException(new StringBuffer("không tạo được thư mục ").append(dir).toString());
            path = new File(folder, new StringBuffer("skill-gap-").append(System.currentTimeMillis()).append(".csv").toString()).getAbsolutePath();
            SkillCastStats.attachLog(new StringBuffer("# Hung_By_Son skill gap log, started ").append(System.currentTimeMillis()).toString());
            write(SkillCastStats.drainLog());
            Thread t = new Thread(new SkillCastLog());
            try { t.setDaemon(true); } catch (Throwable ignored) { }
            t.start();
            thread = t;
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    public void run() {
        while (error == null) {
            try {
                Thread.sleep(FLUSH_MS);
                flush();
            } catch (Throwable failure) {
                fail(failure);
            }
        }
    }

    /** Appends the collected lines now. */
    public static synchronized void flush() {
        if (path == null || error != null) return;
        try { write(SkillCastStats.drainLog()); } catch (Throwable failure) { fail(failure); }
    }

    private static void write(String text) throws Exception {
        if (text.length() == 0) return;
        FileOutputStream out = new FileOutputStream(path, true);
        try { out.write(text.getBytes("UTF-8")); } finally { out.close(); }
    }

    private static synchronized void fail(Throwable failure) {
        String why = failure.getMessage();
        error = why == null || why.length() == 0 ? failure.getClass().getName() : why;
        SkillCastStats.detachLog();
    }

    /** The log file's full path, or null while no log runs. */
    public static synchronized String path() { return error == null ? path : null; }

    public static synchronized String error() { return error; }
}
