package core;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import client.Clan;
import client.Player;
import io.Message;
import io.Session;
import java.util.Collections;
import java.util.Comparator;
import map.Map;
import template.Item3;
import template.Level;
import template.Part_player;

public class BXH {

    public static final List<Memin4> BXH_level = new ArrayList<>();
    public static final List<Clan> BXH_clan = new ArrayList<>();

    public static void send(Session conn, int b) throws IOException {
        switch (b) {
            case 0: {
                Message m = new Message(56);
                m.writer().writeByte(1);
                m.writer().writeUTF("BXH LEVEL");
                m.writer().writeByte(99); // page
                m.writer().writeInt(0); // my index in bxh
                m.writer().writeByte(BXH.BXH_level.size()); // num2
                for (int i = 0; i < BXH.BXH_level.size(); i++) {
                    Memin4 temp = BXH.BXH_level.get(i);
                    Player p0 = Map.get_player_by_name(temp.name);
                    if (p0 != null) {
                        temp.head = p0.head;
                        temp.eye = p0.eye;
                        temp.hair = p0.hair;
                        temp.level = p0.level;
                        temp.itemwear.clear();
                        for (int i1 = 0; i1 < p0.item.wear.length; i1++) {
                            Item3 it = p0.item.wear[i1];
                            if (it != null && (i1 == 0 || i1 == 1 || i1 == 6 || i1 == 7 || i1 == 10)) {
                                Part_player part = new Part_player();
                                part.type = it.type;
                                part.part = it.part;
                                temp.itemwear.add(part);
                            }
                        }
                        temp.clan = p0.myclan;
                    }
                    m.writer().writeUTF(temp.name);
                    m.writer().writeByte(temp.head);
                    m.writer().writeByte(temp.eye);
                    m.writer().writeByte(temp.hair);
                    m.writer().writeShort(temp.level);
                    m.writer().writeByte(temp.itemwear.size());
                    for (Part_player it : temp.itemwear) {
                        m.writer().writeByte(it.part);
                        m.writer().writeByte(it.type);
                    }
                    m.writer().writeByte((p0 != null) ? (byte) 1 : (byte) 0); // type online
                    m.writer().writeUTF(temp.info);
                    if (temp.clan != null) {
                        m.writer().writeShort(temp.clan.icon);
                        m.writer().writeUTF(temp.clan.name_clan_shorted);
                        m.writer().writeByte(temp.clan.get_mem_type(temp.name));
                    } else {
                        m.writer().writeShort(-1);
                    }
                }
                conn.addmsg(m);
                m.cleanup();
                break;
            }
        }
    }

    public static class Memin4 {

        public short level;
        public long exp;
        public String name;
        public byte head;
        public byte eye;
        public byte hair;
        public List<Part_player> itemwear;
        public Clan clan;
        public String info;
    }
}
