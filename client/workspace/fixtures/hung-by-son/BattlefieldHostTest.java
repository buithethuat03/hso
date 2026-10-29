import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;

import luna.api.BattlefieldAccess;
import luna.features.BattlefieldSupport;

/** Host checks for final-hit attribution and the session-only FIFO. */
public final class BattlefieldHostTest {
    private static int checks;

    private BattlefieldHostTest() { }

    public static void main(String[] args) throws Exception {
        BattlefieldSupport.resetForTest();
        FakeAccess access = new FakeAccess();
        access.player = new Entity(700, 0, 35, 100, "Local");
        Entity alpha = access.add(new Entity(101, 0, 39, 80, "Alpha"));
        Entity beta = access.add(new Entity(102, 0, 38, 70, "Beta"));
        DataInputStream input;

        require(!BattlefieldSupport.enabled()
                && "Mode chiến trường: [TẮT]".equals(BattlefieldSupport.label()),
                "mode defaults off");
        input = packet(700, new Victim(101, 20, 0));
        int original = input.available();
        BattlefieldSupport.onCombat(access, input, 0);
        require(BattlefieldSupport.count() == 0 && !BattlefieldSupport.excluded("Alpha"),
                "off mode ignores kills");
        require(input.available() == original, "receiver input is restored while disabled");

        BattlefieldSupport.setEnabled(true);
        require("Mode chiến trường: [BẬT]".equals(BattlefieldSupport.label()),
                "label follows enabled state");
        input = packet(700, new Victim(101, 20, 0));
        original = input.available();
        BattlefieldSupport.onCombat(access, input, 0);
        require(input.available() == original && input.readUnsignedShort() == 700,
                "full packet is marked and reset");
        require(BattlefieldSupport.count() == 1 && "Alpha".equals(BattlefieldSupport.nameAt(0)),
                "local player's positive-damage final hit records player");
        require(BattlefieldSupport.excluded(" Alpha ") && !BattlefieldSupport.excluded("alpha"),
                "membership trims whitespace and compares exact names");

        access.add(new Entity(103, 1, 20, 90, "Monster"));
        access.add(new Entity(104, 0, 40, 90, "Level40"));
        access.add(new Entity(105, 0, 39, 90, " XÀ NỮ "));
        input = packet(700, new Victim(103, 20, 0), new Victim(104, 20, 0),
                new Victim(105, 20, 0));
        BattlefieldSupport.onCombat(access, input, 0);
        require(BattlefieldSupport.count() == 1, "cv nonzero, level 40, and Xà nữ are excluded");

        input = packet(701, new Victim(102, 20, 0));
        BattlefieldSupport.onCombat(access, input, 0);
        input = packet(700, new Victim(102, 20, 0));
        BattlefieldSupport.onCombat(access, input, 1);
        require(BattlefieldSupport.count() == 1, "foreign attacker and monster target type do not count");

        Entity deadBefore = access.add(new Entity(106, 0, 30, 0, "DeadBefore"));
        Entity aliveAfter = access.add(new Entity(107, 0, 30, 20, "StillAlive"));
        Entity zeroDamage = access.add(new Entity(108, 0, 30, 20, "NoDamage"));
        input = packet(700, new Victim(106, 20, 0), new Victim(107, 20, 1),
                new Victim(108, 0, 0));
        BattlefieldSupport.onCombat(access, input, 0);
        require(BattlefieldSupport.count() == 1,
                "pre-dead targets, positive resulting HP, and zero damage do not count");
        BattlefieldSupport.onCombat(access, packet(700, new Victim(999, 20, 0)), 0);
        require(BattlefieldSupport.count() == 1, "unresolved victim IDs do not count");

        input = packet(700, new Victim(102, 20, 0, 2), new Victim(101, 20, 0));
        BattlefieldSupport.onCombat(access, input, 0);
        require(BattlefieldSupport.count() == 2 && "Alpha".equals(BattlefieldSupport.nameAt(0))
                && "Beta".equals(BattlefieldSupport.nameAt(1)),
                "repeated kills keep original order and add distinct names");

        access.add(new Entity(109, 0, 31, 10, "Gamma"));
        input = packet(700, new Victim(109, 1, 0));
        BattlefieldSupport.onCombat(access, input, 0);
        access.add(new Entity(110, 0, 31, 10, "Delta"));
        BattlefieldSupport.onCombat(access, packet(700, new Victim(110, 1, 0)), 0);
        access.add(new Entity(111, 0, 31, 10, "Epsilon"));
        BattlefieldSupport.onCombat(access, packet(700, new Victim(111, 1, 0)), 0);
        access.add(new Entity(112, 0, 31, 10, "Zeta"));
        BattlefieldSupport.onCombat(access, packet(700, new Victim(112, 1, 0)), 0);
        access.add(new Entity(113, 0, 31, 10, "Eta"));
        BattlefieldSupport.onCombat(access, packet(700, new Victim(113, 1, 0)), 0);
        access.add(new Entity(114, 0, 31, 10, "Theta"));
        BattlefieldSupport.onCombat(access, packet(700, new Victim(114, 1, 0)), 0);
        access.add(new Entity(115, 0, 31, 10, "Iota"));
        BattlefieldSupport.onCombat(access, packet(700, new Victim(115, 1, 0)), 0);
        access.add(new Entity(116, 0, 31, 10, "Kappa"));
        BattlefieldSupport.onCombat(access, packet(700, new Victim(116, 1, 0)), 0);
        access.add(new Entity(118, 0, 31, 10, "Lambda"));
        BattlefieldSupport.onCombat(access, packet(700, new Victim(118, 1, 0)), 0);
        require(BattlefieldSupport.count() == 10
                && "Beta".equals(BattlefieldSupport.nameAt(0))
                && "Lambda".equals(BattlefieldSupport.nameAt(9)),
                "FIFO is bounded to ten and evicts the oldest unique name");

        // A frame must be fully decoded before any of its victim names are added.
        BattlefieldSupport.setEnabled(false);
        BattlefieldSupport.setEnabled(true);
        input = truncatedTwoVictimPacket(700, 101, 102);
        original = input.available();
        BattlefieldSupport.onCombat(access, input, 0);
        require(BattlefieldSupport.count() == 0 && input.available() == original,
                "truncated multi-victim packet changes nothing and restores stream");
        input = packetWithTrailingByte(700, new Victim(101, 20, 0));
        BattlefieldSupport.onCombat(access, input, 0);
        require(BattlefieldSupport.count() == 0, "unexpected trailing data rejects entire frame");

        BattlefieldSupport.onCombat(access, packet(700, new Victim(101, 20, 0)), 0);
        require(BattlefieldSupport.count() == 1, "valid frame can record after rejected frames");
        BattlefieldSupport.setEnabled(false);
        require(BattlefieldSupport.count() == 0 && !BattlefieldSupport.excluded("Alpha"),
                "turning mode off clears FIFO and disables filtering");

        BattlefieldSupport.setEnabled(true);
        access.player = new Entity(900, 0, 36, 100, "OtherLocal");
        access.add(new Entity(117, 0, 33, 15, "NewSessionKill"));
        BattlefieldSupport.onPlayerSession(access);
        require(BattlefieldSupport.count() == 0, "new player identity clears old session FIFO");
        BattlefieldSupport.onCombat(access, packet(900, new Victim(117, 5, 0)), 0);
        require(BattlefieldSupport.count() == 1
                && "NewSessionKill".equals(BattlefieldSupport.nameAt(0)),
                "new session records under its own local attacker ID");
        access.player = new Entity(900, 0, 36, 100, "OtherLocal"); // map replacement, same identity
        BattlefieldSupport.onPlayerSession(access);
        require(BattlefieldSupport.count() == 1, "map object replacement preserves same player session");
        BattlefieldSupport.onSessionExit();
        require(!BattlefieldSupport.enabled() && BattlefieldSupport.count() == 0,
                "game exit resets mode and FIFO");

        BattlefieldSupport.resetForTest();
        System.out.println("BattlefieldHostTest PASS (" + checks + " checks)");
    }

    private static DataInputStream packet(int attacker, Victim... victims) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(attacker);
        out.writeByte(19);
        out.writeByte(victims.length);
        for (Victim victim : victims) {
            out.writeShort(victim.id);
            out.writeInt(victim.damage);
            out.writeInt(victim.hp);
            out.writeByte(victim.effects);
            for (int i = 0; i < victim.effects; i++) {
                out.writeByte(3 + i);
                out.writeInt(10 + i);
            }
        }
        out.writeInt(100); out.writeInt(50); out.writeByte(0); out.writeInt(0);
        out.flush();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static DataInputStream truncatedTwoVictimPacket(int attacker, int first, int second)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(attacker); out.writeByte(19); out.writeByte(2);
        out.writeShort(first); out.writeInt(10); out.writeInt(0); out.writeByte(0);
        out.writeShort(second); out.writeInt(10); // missing HP, effects, and footer
        out.flush();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static DataInputStream packetWithTrailingByte(int attacker, Victim victim)
            throws Exception {
        DataInputStream valid = packet(attacker, victim);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] payload = new byte[valid.available()];
        valid.readFully(payload);
        bytes.write(payload); bytes.write(99);
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static final class Victim {
        final int id;
        final int damage;
        final int hp;
        final int effects;
        Victim(int id, int damage, int hp) { this(id, damage, hp, 0); }
        Victim(int id, int damage, int hp, int effects) {
            this.id = id; this.damage = damage; this.hp = hp; this.effects = effects;
        }
    }

    private static final class Entity {
        final int id;
        final int type;
        final int level;
        int hp;
        final String name;
        Entity(int id, int type, int level, int hp, String name) {
            this.id = id; this.type = type; this.level = level; this.hp = hp; this.name = name;
        }
    }

    private static final class FakeAccess implements BattlefieldAccess {
        Entity player;
        final Map<String, Entity> entities = new HashMap<String, Entity>();
        Entity add(Entity entity) {
            entities.put(key(entity.id, entity.type), entity);
            return entity;
        }
        public Object player() { return player; }
        public Object find(int id, int type) { return entities.get(key(id, type)); }
        public int id(Object entity) { return ((Entity)entity).id; }
        public int type(Object entity) { return ((Entity)entity).type; }
        public int level(Object entity) { return ((Entity)entity).level; }
        public int hp(Object entity) { return ((Entity)entity).hp; }
        public String name(Object entity) { return ((Entity)entity).name; }
        private static String key(int id, int type) { return (id & 65535) + ":" + type; }
    }
}
