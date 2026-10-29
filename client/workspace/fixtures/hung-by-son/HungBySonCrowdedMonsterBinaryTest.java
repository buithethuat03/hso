import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/** Actual candidate HP bridge and extracted world-removal loop. Rendering,
 * transport and the clock use the existing isolated recovery harness.
 */
public final class HungBySonCrowdedMonsterBinaryTest {
    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static DataInputStream combat(int id, int hp) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(777); out.writeByte(0); out.writeByte(1);
        out.writeShort(id); out.writeInt(13); out.writeInt(hp); out.writeByte(0);
        out.writeInt(1234); out.writeInt(567); out.writeByte(0); out.writeInt(0);
        out.flush();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static void snapshot(HungBySonMonsterRecoveryBinaryTest.Harness h, int id, int hp)
            throws Exception {
        DataInputStream stream = combat(id, hp);
        int before = stream.available();
        h.call("onCombatSnapshot", new Class<?>[]{h.api, DataInputStream.class}, h.access, stream);
        require(stream.available() == before, "observer preserves native stream position");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("candidate.jar catalog.properties microemulator.jar");
        Properties properties = new Properties();
        InputStream input = new FileInputStream(args[1]);
        try { properties.load(input); } finally { input.close(); }
        HungBySonMonsterRecoveryBinaryTest.Harness h = new HungBySonMonsterRecoveryBinaryTest.Harness(
                HungBySonMonsterRecoveryBinaryTest.read(new File(args[0])), properties);
        Object monster = h.make(41);
        h.learn(41);
        for (String field : new String[]{"bg", "bh"})
            HungBySonMonsterRecoveryBinaryTest.set(monster, field, 7);
        h.detail(41, 0);
        h.worldFrame();
        require(((String)h.call("status", new Class<?>[0])).contains("K:1"), "actual native corpse is held");
        snapshot(h, 41, 101);
        require(HungBySonMonsterRecoveryBinaryTest.get(monster, "bs") == 0,
                "invalid HP cannot release native corpse");
        HungBySonMonsterRecoveryBinaryTest.set(monster, "cM", true);
        HungBySonMonsterRecoveryBinaryTest.set(monster, "bT", 4);
        HungBySonMonsterRecoveryBinaryTest.set(monster, "a", 599);
        snapshot(h, 41, 37);
        require(HungBySonMonsterRecoveryBinaryTest.get(monster, "bs") == 37,
                "positive combat applies actual native HP");
        for (String flag : new String[]{"dk", "cL", "cM", "dz"})
            require(!HungBySonMonsterRecoveryBinaryTest.bool(monster, flag),
                    "combat revival clears native flag " + flag);
        for (String field : new String[]{"cG", "cK", "bb", "bc", "bd", "dW", "bT", "a"})
            require(HungBySonMonsterRecoveryBinaryTest.get(monster, field) == 0,
                    "combat revival clears native counter " + field);
        require(HungBySonMonsterRecoveryBinaryTest.get(monster, "aZ") == 30
                && HungBySonMonsterRecoveryBinaryTest.get(monster, "ba") == 40
                && HungBySonMonsterRecoveryBinaryTest.get(monster, "bg") == 7
                && HungBySonMonsterRecoveryBinaryTest.get(monster, "bh") == 7,
                "combat revival preserves current and server position fields");
        h.worldFrame(); h.worldFrame();
        require(HungBySonMonsterRecoveryBinaryTest.get(monster, "bs") == 37
                && ((String)h.call("status", new Class<?>[0])).contains("K:0"),
                "actual world hook cannot latch the revived monster back to HP zero");

        HungBySonMonsterRecoveryBinaryTest.set(monster, "cG", 2);
        HungBySonMonsterRecoveryBinaryTest.set(monster, "cK", 7);
        HungBySonMonsterRecoveryBinaryTest.set(monster, "bb", 3);
        snapshot(h, 41, 25);
        require(HungBySonMonsterRecoveryBinaryTest.get(monster, "bs") == 25
                && HungBySonMonsterRecoveryBinaryTest.get(monster, "cG") == 2
                && HungBySonMonsterRecoveryBinaryTest.get(monster, "cK") == 7
                && HungBySonMonsterRecoveryBinaryTest.get(monster, "bb") == 3,
                "positive hit on a live monster preserves its native animation");
        snapshot(h, 41, 0); h.worldFrame();
        require(HungBySonMonsterRecoveryBinaryTest.get(monster, "bs") == 0
                && ((String)h.call("status", new Class<?>[0])).contains("K:1"),
                "a subsequent real death is retained normally");
        snapshot(h, 41, 37); h.worldFrame();
        require(HungBySonMonsterRecoveryBinaryTest.get(monster, "bs") == 37
                && ((String)h.call("status", new Class<?>[0])).contains("K:0"),
                "repeated same-identity death/combat revival releases again");

        Object replacement = h.make(41);
        snapshot(h, 41, 19);
        require(HungBySonMonsterRecoveryBinaryTest.get(replacement, "bs") == 50,
                "combat snapshot does not promote an unconfirmed replacement identity");
        System.out.println("CROWDED_MONSTER_BINARY=PASS; checks=" + checks
                + "; actual HP bridge/world loop, fixed positions and live-animation preservation");
    }
}
