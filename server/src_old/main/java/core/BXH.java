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
import map.Map;
import template.Item3;
import template.Level;
import template.Part_player;

public class BXH {
	public static final List<Memin4> BXH_level = new ArrayList<>();

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

	public static void init() {
		try (Connection conn = SQL.gI().getConnection();
		      Statement ps = conn.createStatement();
		      ResultSet rs = ps.executeQuery(
		            "SELECT `id`, `level`, `exp`, `name`, `body`, `itemwear` FROM `player` WHERE `level` > 10 ORDER BY `level` DESC, exp DESC LIMIT 20;");) {
			BXH.BXH_level.clear();
			while (rs.next()) {
				Memin4 temp = new Memin4();
				temp.level = rs.getShort("level");
				temp.exp = rs.getLong("exp");
				temp.name = rs.getString("name");
				JSONArray jsar = (JSONArray) JSONValue.parse(rs.getString("body"));
				if (jsar == null) {
					return;
				}
				temp.head = Byte.parseByte(jsar.get(0).toString());
				temp.hair = Byte.parseByte(jsar.get(2).toString());
				temp.eye = Byte.parseByte(jsar.get(1).toString());
				jsar.clear();
				jsar = (JSONArray) JSONValue.parse(rs.getString("itemwear"));
				if (jsar == null) {
					return;
				}
				temp.itemwear = new ArrayList<>();
				for (int i3 = 0; i3 < jsar.size(); i3++) {
					JSONArray jsar2 = (JSONArray) JSONValue.parse(jsar.get(i3).toString());
					byte index_wear = Byte.parseByte(jsar2.get(9).toString());
					if (index_wear != 0 && index_wear != 1 && index_wear != 6 && index_wear != 7 && index_wear != 10) {
						continue;
					}
					Part_player temp2 = new Part_player();
					temp2.type = Byte.parseByte(jsar2.get(2).toString());
					temp2.part = Byte.parseByte(jsar2.get(6).toString());
					temp.itemwear.add(temp2);
				}
				temp.clan = Clan.get_clan_of_player(temp.name);
				String percent =
				      String.format("%.1f", (((float) temp.exp * 1000) / Level.entrys.get(temp.level - 1).exp) / 10f);
				temp.info = "Level : " + (temp.level) + "\t-\t" + percent + "%";
				BXH.BXH_level.add(temp);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private static class Memin4 {
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
