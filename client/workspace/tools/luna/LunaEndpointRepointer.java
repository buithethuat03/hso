import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Repoints a Luna 4.0.2 JAR to another endpoint and server label without ASM.
 * Only exact constant-pool Utf8 literals are replaced; every expected literal must be found
 * exactly once in its owning class, otherwise nothing is written.
 *
 * Usage: java LunaEndpointRepointer in.jar out.jar host label
 */
public final class LunaEndpointRepointer {
    /** The default LUNA host is stored XOR 5 and decoded at runtime. */
    static final String LUNA_HOST_ENCODED = xor5("103.178.235.83");
    static final String LUNA_LABEL = "LUNA";
    /** Hidden foreign server entries; repointed too so no list entry leaves the target host. */
    static final String[] FOREIGN_HOSTS = {"46.137.254.172", "54.254.156.202", "54.151.177.35"};

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("in.jar out.jar host label");
        File in = new File(args[0]), out = new File(args[1]);
        String host = args[2], label = args[3];
        if (out.exists()) throw new IllegalStateException("Output exists: " + out);

        Map<String, String> literals = new HashMap<String, String>();
        literals.put(LUNA_HOST_ENCODED, xor5(host));
        literals.put(LUNA_LABEL, label);
        for (String h : FOREIGN_HOSTS) literals.put(h, host);
        Map<String, Integer> hits = new TreeMap<String, Integer>();

        ZipFile zip = new ZipFile(in);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(buffer);
        int changedClasses = 0;
        try {
            for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements();) {
                ZipEntry src = en.nextElement();
                byte[] data = read(zip.getInputStream(src));
                byte[] patched = data;
                if (src.getName().equals("META-INF/MANIFEST.MF")) {
                    patched = manifest(data, label);
                } else if (src.getName().endsWith(".class")) {
                    patched = constants(data, literals, hits);
                    if (patched != data) changedClasses++;
                }
                ZipEntry dst = new ZipEntry(src.getName());
                dst.setTime(src.getTime());
                dst.setMethod(src.getMethod());
                if (src.getMethod() == ZipEntry.STORED) {
                    CRC32 crc = new CRC32();
                    crc.update(patched);
                    dst.setSize(patched.length);
                    dst.setCompressedSize(patched.length);
                    dst.setCrc(crc.getValue());
                }
                zos.putNextEntry(dst);
                zos.write(patched);
                zos.closeEntry();
            }
            zos.close();
        } finally {
            zip.close();
        }

        check(count(hits, LUNA_HOST_ENCODED) == 1, "LUNA encoded host count " + hits);
        check(count(hits, LUNA_LABEL) == 1, "LUNA label count " + hits);
        for (String h : FOREIGN_HOSTS) check(count(hits, h) >= 1, "Foreign host " + h + " " + hits);
        FileOutputStream fos = new FileOutputStream(out);
        try { fos.write(buffer.toByteArray()); } finally { fos.close(); }
        System.out.println("REPOINT=PASS; host=" + host + "; label=" + label + "; classes=" + changedClasses + "; literals=" + hits);
    }

    static byte[] manifest(byte[] data, String label) throws IOException {
        String text = new String(data, "UTF-8");
        String patched = text.replace("MIDlet-1: HSOLUNA,", "MIDlet-1: " + label + ",")
                .replace("MIDlet-Name: HSOLUNA", "MIDlet-Name: " + label);
        check(patched.indexOf("HSOLUNA") < 0 && !patched.equals(text), "Manifest MIDlet name");
        return patched.getBytes("UTF-8");
    }

    /** Rewrites matching CONSTANT_Utf8 entries; returns the input array when nothing matched. */
    static byte[] constants(byte[] b, Map<String, String> literals, Map<String, Integer> hits) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
        if (in.readInt() != 0xCAFEBABE) return b;
        ByteArrayOutputStream bos = new ByteArrayOutputStream(b.length + 64);
        DataOutputStream out = new DataOutputStream(bos);
        out.writeInt(0xCAFEBABE);
        out.writeShort(in.readUnsignedShort());
        out.writeShort(in.readUnsignedShort());
        int count = in.readUnsignedShort();
        out.writeShort(count);
        boolean changed = false;
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            out.writeByte(tag);
            switch (tag) {
                case 1: {
                    String s = in.readUTF();
                    String r = literals.get(s);
                    if (r != null) {
                        Integer n = hits.get(s);
                        hits.put(s, n == null ? 1 : n + 1);
                        s = r;
                        changed = true;
                    }
                    out.writeUTF(s);
                    break;
                }
                case 3: case 4: copy(in, out, 4); break;
                case 5: case 6: copy(in, out, 8); i++; break;
                case 7: case 8: case 16: case 19: case 20: copy(in, out, 2); break;
                case 9: case 10: case 11: case 12: case 17: case 18: copy(in, out, 4); break;
                case 15: copy(in, out, 3); break;
                default: throw new IOException("Constant tag " + tag);
            }
        }
        if (!changed) return b;
        byte[] rest = read(in);
        out.write(rest);
        out.flush();
        return bos.toByteArray();
    }

    static String xor5(String s) {
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) c[i] = (char) (c[i] ^ 5);
        return new String(c);
    }

    static int count(Map<String, Integer> hits, String key) {
        Integer n = hits.get(key);
        return n == null ? 0 : n;
    }

    static void copy(DataInputStream in, DataOutputStream out, int n) throws IOException {
        byte[] b = new byte[n];
        in.readFully(b);
        out.write(b);
    }

    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int r; (r = in.read(buf)) > 0;) bos.write(buf, 0, r);
        in.close();
        return bos.toByteArray();
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("Check failed: " + what);
    }
}
