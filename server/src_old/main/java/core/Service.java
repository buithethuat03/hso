package core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import client.Clan;
import client.Pet;
import client.Player;
import io.Message;
import io.Session;
import map.Map;
import map.MapService;
import map.Mob_in_map;
import template.Clan_mems;
import template.EffTemplate;
import template.Item3;
import template.Item47;
import template.ItemSell3;
import template.ItemTemplate3;
import template.ItemTemplate4;
import template.ItemTemplate7;
import template.Option;
import template.OptionItem;
import template.Option_pet;

public class Service {
	public static void send_msg_data(Session conn, int cmd, String name) throws IOException {
		Message m = new Message(cmd);
		m.writer().write(Util.loadfile("data/msg/" + name));
		conn.addmsg(m);
		m.cleanup();
	}

	public static void send_msg_data(Session conn, int cmd, byte[] data) throws IOException {
		Message m = new Message(cmd);
		m.writer().write(data);
		conn.addmsg(m);
		m.cleanup();
	}

	public static void send_item_template(Session conn) throws IOException {
		Message m = new Message(25);
		m.writer().writeShort(ItemTemplate4.item.size());
		for (ItemTemplate4 temp : ItemTemplate4.item) {
			m.writer().writeShort(temp.getId());
			m.writer().writeShort(temp.getIcon());
			m.writer().writeLong(temp.getPrice());
			m.writer().writeUTF(temp.getName());
			m.writer().writeUTF(temp.getContent());
			m.writer().writeByte(temp.getType());
			m.writer().writeByte(temp.getPricetype());
			m.writer().writeByte(temp.getSell());
			m.writer().writeShort(temp.getValue());
			m.writer().writeBoolean(temp.getTrade() == 1);
		}
		//
		m.writer().writeByte(OptionItem.entrys.size());
		for (OptionItem temp : OptionItem.entrys) {
			m.writer().writeUTF(temp.getName());
			m.writer().writeByte(temp.getColor());
			m.writer().writeByte(temp.getIspercent());
		}
		//
		if (conn.zoomlv > 1) {
			m.writer().writeShort(ItemTemplate7.item.size());
			for (ItemTemplate7 temp : ItemTemplate7.item) {
				m.writer().writeShort(temp.getId());
				m.writer().writeShort(temp.getIcon());
				m.writer().writeLong(temp.getPrice());
				m.writer().writeUTF(temp.getName());
				m.writer().writeUTF(temp.getContent());
				m.writer().writeByte(temp.getType());
				m.writer().writeByte(temp.getPricetype());
				m.writer().writeByte(temp.getSell());
				m.writer().writeShort(temp.getValue());
				m.writer().writeByte(temp.getTrade());
				m.writer().writeByte(temp.getColor());
			}
		} else {
			m.writer().writeShort(0);
		}
		m.writer().write(Manager.gI().msg_25_new);
		conn.addmsg(m);
		m.cleanup();
	}

	public static void send_notice_box(Session conn, String s) throws IOException {
		Message m2 = new Message(37);
		m2.writer().writeUTF(s);
		m2.writer().writeUTF("");
		m2.writer().writeByte(15);
		conn.addmsg(m2);
		m2.cleanup();
	}

	public static void send_quest(Session conn) throws IOException {
		Message m = new Message(52);
		m.writer().writeByte(10);
		m.writer().writeByte(10);
		m.writer().writeByte(10);
		conn.addmsg(m);
		m.cleanup();
	}

	public static void send_auto_atk(Session conn) throws IOException {
		Message m = new Message(-108);
		m.writer().writeByte(5);
		m.writer().writeByte(0);
		conn.addmsg(m);
		m.cleanup();
	}

	public static void send_char_main_in4(Player p) throws IOException {
		if (p.hp > p.body.get_max_hp()) {
			p.hp = p.body.get_max_hp();
		}
		if (p.mp > p.body.get_max_mp()) {
			p.mp = p.body.get_max_mp();
		}
		Message m = new Message(3);
		m.writer().writeShort(p.id);
		m.writer().writeUTF(p.name);
		m.writer().writeInt(p.hp);
		m.writer().writeInt(p.body.get_max_hp());
		m.writer().writeInt(p.mp);
		m.writer().writeInt(p.body.get_max_mp());
		m.writer().writeByte(p.head);
		m.writer().writeByte(p.clazz);
		m.writer().writeByte(p.eye);
		m.writer().writeByte(p.hair);
		//
		byte[] i1 = new byte[] {0, 1, 2, 3, 4, 53, 54, 55, 7, 8, 9, 10, 11, 14, 15, 16, 17, 18, 19, 20, 27, 28, 33, 34,
		      35, 36, 40, 112, -75, -74, -73};
		m.writer().writeByte(i1.length);
		for (int i = 0; i < i1.length; i++) {
			m.writer().writeByte(i1[i]);
			m.writer().writeInt(p.body.get_param_view_in4(i1[i]));
		}
		///
		m.writer().writeShort(p.level); // lv
		m.writer().writeShort(p.getlevelpercent()); // lv percent
		m.writer().writeShort(p.tiemnang); // tiem nang
		m.writer().writeShort(p.kynang); // ky nang
		///
		m.writer().writeShort(p.point1); // tiem nang goc
		m.writer().writeShort(p.point2);
		m.writer().writeShort(p.point3);
		m.writer().writeShort(p.point4);
		///
		m.writer().writeShort(p.body.get_plus_point(23)); // tiem nang them
		m.writer().writeShort(p.body.get_plus_point(24));
		m.writer().writeShort(p.body.get_plus_point(25));
		m.writer().writeShort(p.body.get_plus_point(26));
		///// skill point
		for (int i = 0; i < 21; i++) {
			m.writer().writeByte(p.skill_point[i]);
		}
		// skill plus point
		for (int i = 0; i < 21; i++) {
			m.writer().writeByte(p.body.get_skill_point_plus(i));
		}
		m.writer().writeByte(p.typepk);
		m.writer().writeShort(p.pointpk);
		m.writer().writeByte(p.maxbag); // max bag
		if (p.myclan != null) {
			m.writer().writeShort(p.myclan.icon);
			m.writer().writeInt(Clan.get_id_clan(p.myclan));
			m.writer().writeUTF(p.myclan.name_clan_shorted);
			m.writer().writeByte(p.myclan.get_mem_type(p.name));
		} else {
			m.writer().writeShort(-1); // clan
		}
		m.writer().writeUTF("k2: ");
		m.writer().writeLong(0);
		m.writer().writeByte(p.fashion.length);
		for (int i = 0; i < p.fashion.length; i++) {
			m.writer().writeByte(p.fashion[i]);
		}
		m.writer().writeByte(3); // nap tien?
		m.writer().writeShort(-1); // id mat na
		m.writer().writeByte(1); // paint mat na trc sau
		m.writer().writeShort(-1); // phi phong
		m.writer().writeShort(-1); // id weapon
		m.writer().writeShort(p.id_horse);
		m.writer().writeShort(-1); // idHair
		m.writer().writeShort(-1); // idWing
		m.writer().writeShort(-1); // idName
		m.writer().writeShort(-1); // idBody
		// m.writer().writeShort(-1); // idLeg
		// m.writer().writeShort(-1); // idBienhinh
		//
		p.conn.addmsg(m);
		m.cleanup();
	}

	public static void send_skill(Player p) throws IOException {
		Message m = new Message(29);
		switch (p.clazz) {
			case 0: { // chien binh
				m.writer().write(Util.loadfile("data/msg/msg_29_chienbinh"));
				break;
			}
			case 1: { // sat thu
				m.writer().write(Util.loadfile("data/msg/msg_29_satthu"));
				break;
			}
			case 2: { // phap su
				m.writer().write(Util.loadfile("data/msg/msg_29_phapsu"));
				break;
			}
			case 3: { // xa thu
				m.writer().write(Util.loadfile("data/msg/msg_29_xathu"));
				break;
			}
		}
		p.conn.addmsg(m);
		m.cleanup();
	}

	public static void send_login_rms(Session conn) throws IOException {
		// id 1
		Message m = new Message(55);
		m.writer().writeByte(1);
		m.writer().writeShort(2);
		m.writer().writeByte(-1);
		m.writer().writeByte(0);
		conn.addmsg(m);
		m.cleanup();
		// id 2
		m = new Message(55);
		m.writer().writeByte(2);
		if (conn.p.map.map_id == 0 && conn.p.level < 2) { // is new begin
			m.writer().writeShort(0);
		} else {
			m.writer().writeShort(1);
			m.writer().writeByte(0);
		}
		conn.addmsg(m);
		m.cleanup();
		//
		if (conn.p.rms_save[0].length > 0) {
			m = new Message(55);
			m.writer().writeByte(0);
			m.writer().writeShort(conn.p.rms_save[0].length);
			m.writer().write(conn.p.rms_save[0]);
			conn.addmsg(m);
			m.cleanup();
		}
		if (conn.p.rms_save[1].length > 0) {
			m = new Message(55);
			m.writer().writeByte(3);
			m.writer().writeShort(conn.p.rms_save[1].length);
			m.writer().write(conn.p.rms_save[1]);
			conn.addmsg(m);
			m.cleanup();
		}
	}

	public static void send_notice_nobox_yellow(Session conn, String s) throws IOException {
		Message m = new Message(53);
		m.writer().writeUTF(s);
		m.writer().writeByte(1);
		conn.addmsg(m);
		m.cleanup();
	}

	public static void send_combo(Session conn) throws IOException {
		Message m = new Message(-108);
		m.writer().writeByte(3);
		m.writer().writeInt(0);
		conn.addmsg(m);
		m.cleanup();
	}

	public static void send_point_pk(Player p) throws IOException {
		Message m = new Message(59);
		m.writer().writeInt(p.suckhoe);
		m.writer().writeInt(p.pointarena);
		p.conn.addmsg(m);
		m.cleanup();
	}

	public static void send_health(Player p) throws IOException {
		Message m = new Message(59);
		m.writer().writeInt(p.suckhoe);
		m.writer().writeInt(p.pointarena);
		p.conn.addmsg(m);
		m.cleanup();
	}

	public static void send_wear(Player p) throws IOException {
		Message m = new Message(15);
		m.writer().writeShort(p.id);
		m.writer().writeByte(p.item.wear.length);
		for (int i = 0; i < p.item.wear.length; i++) {
			Item3 temp = p.item.wear[i];
			if (temp != null) {
				m.writer().writeByte(i);
				m.writer().writeUTF(temp.name);
				m.writer().writeByte(temp.clazz);
				m.writer().writeByte(temp.type);
				m.writer().writeShort(temp.icon);
				m.writer().writeByte(temp.part);
				m.writer().writeByte(temp.tier); // plus item (tier)
				m.writer().writeShort(temp.level);
				m.writer().writeByte(temp.color);
				m.writer().writeByte(temp.op.size());
				for (int j = 0; j < temp.op.size(); j++) {
					m.writer().writeByte(temp.op.get(j).id);
					m.writer().writeInt(temp.op.get(j).getParam(temp.tier));
				}
				m.writer().writeByte(1); // islock
			} else {
				m.writer().writeByte(-1);
			}
		}
		if (p.pet_follow) {
			for (Pet temp : p.mypet) {
				if (temp.is_follow) {
					m.writer().writeByte(5);
					m.writer().writeUTF(temp.name);
					m.writer().writeByte(4);
					m.writer().writeShort(temp.level);
					m.writer().writeShort(temp.getlevelpercent());
					m.writer().writeByte(temp.type);
					m.writer().writeByte(temp.icon);
					m.writer().writeByte(temp.nframe);
					m.writer().writeByte(temp.color);
					m.writer().writeInt(temp.get_age());
					m.writer().writeShort(temp.grown);
					m.writer().writeShort(temp.maxgrown);
					m.writer().writeShort(temp.point1);
					m.writer().writeShort(temp.point2);
					m.writer().writeShort(temp.point3);
					m.writer().writeShort(temp.point4);
					m.writer().writeShort(temp.maxpoint);
					m.writer().writeByte(temp.op.size());
					for (int i12 = 0; i12 < temp.op.size(); i12++) {
						m.writer().writeByte(temp.op.get(i12).id);
						m.writer().writeInt(temp.op.get(i12).param);
						m.writer().writeInt(temp.op.get(i12).maxdam);
					}
					m.writer().writeByte(0);
					break;
				}
			}
		} else {
			m.writer().writeByte(-1); // pet
		}
		m.writer().writeByte(p.fashion.length);
		for (int i = 0; i < p.fashion.length; i++) {
			m.writer().writeByte(p.fashion[i]);
		}
		p.conn.addmsg(m);
		m.cleanup();
	}

	public static void save_rms(Session conn, Message m) throws IOException {
		m.reader().readByte();
		byte id = m.reader().readByte();
		byte[] num = null;
		try {
			num = new byte[m.reader().readShort()];
			for (int i = 0; i < num.length; i++) {
				num[i] = m.reader().readByte();
			}
		} catch (IOException e) {
		}
		if (num != null && num.length > 0) {
			if (id == 0) {
				conn.p.rms_save[0] = new byte[num.length];
				conn.p.rms_save[0] = num;
			} else if (id == 3 && num.length == 11) {
				conn.p.rms_save[1] = new byte[num.length];
				conn.p.rms_save[1] = num;
				conn.p.load_in4_autoplayer(num);
			}
		}
	}

	public static void send_icon(Session conn, Message m) throws IOException {
		short id0 = 0;
		short id = m.reader().readShort();
		id0 = id;
		try {
			Message m2 = new Message(-51);
			m2.writer().writeShort(id0);
			m2.writer().write(Util.loadfile("data/icon/x" + conn.zoomlv + "/" + id0 + ".png"));
			conn.addmsg(m2);
			m2.cleanup();
		} catch (IOException e) {
			System.err.println("Icon " + id0 + " not found!");
		}
	}

	public static void mob_in4(Player p, int n) throws IOException {
		Mob_in_map temp = MapService.get_mob_by_index(p.map, n);
		if (temp != null) {
			Message m = new Message(7);
			m.writer().writeShort(n);
			m.writer().writeByte((byte)temp.level);
			m.writer().writeShort(temp.x);
			m.writer().writeShort(temp.y);
			m.writer().writeInt(temp.hp);
			m.writer().writeInt(temp.hpmax);
			m.writer().writeByte(20); // id skill monster (Spec: 32, ...)
			m.writer().writeInt(Mob_in_map.time_refresh);
			m.writer().writeShort(-1); // clan monster
			m.writer().writeByte(0);
			m.writer().writeByte(2); // speed
			m.writer().writeByte(0);
			m.writer().writeUTF("");
			m.writer().writeLong(-11111);
			m.writer().writeByte(temp.color_name); // color name 1: blue, 2: yellow
			p.conn.addmsg(m);
			m.cleanup();
		}
	}

	public static void send_notice_nobox_white(Session conn, String s) throws IOException {
		Message m = new Message(53);
		m.writer().writeUTF(s);
		m.writer().writeByte(0);
		conn.addmsg(m);
		m.cleanup();
	}

	public static void send_box_input_yesno(Session conn, int type, String s) throws IOException {
		Message m2 = new Message(-32);
		m2.writer().writeShort(conn.p.id);
		m2.writer().writeByte(type);
		m2.writer().writeUTF(s);
		conn.addmsg(m2);
		m2.cleanup();
	}

	public static void usepotion(Player p, int type, int param) throws IOException {
		if (p.isdie) {
			return;
		}
		Message m = new Message(32);
		switch (type) {
			case 0: { // use hp potion
				p.hp += param;
				if (p.hp > p.body.get_max_hp()) {
					p.hp = p.body.get_max_hp();
				}
				m.writer().writeByte(0);
				m.writer().writeShort(p.id);
				m.writer().writeShort(-1); // id potion in bag
				m.writer().writeByte(type);
				m.writer().writeInt(p.body.get_max_hp()); // max hp
				m.writer().writeInt(p.hp); // hp
				m.writer().writeInt(param); // param use
				break;
			}
			case 1: { // use mp potion
				p.mp += param;
				if (p.mp > p.body.get_max_mp()) {
					p.mp = p.body.get_max_mp();
				}
				m.writer().writeByte(0);
				m.writer().writeShort(p.id);
				m.writer().writeShort(-1); // id potion in bag
				m.writer().writeByte(type);
				m.writer().writeInt(p.body.get_max_mp()); // max hp
				m.writer().writeInt(p.mp); // hp
				m.writer().writeInt(param); // param use
				break;
			}
		}
		MapService.send_msg_player_inside(p.map, p, m, true);
		m.cleanup();
	}

	public static void chat_KTG(Session conn, Message m2) throws IOException {
		if (conn.p.get_ngoc() < 5) {
			send_notice_box(conn, "Không đủ ngọc để thực hiện");
			return;
		}
		// if (!conn.user.equals("ad1") && conn.p.time_chat_ktg > System.currentTimeMillis()) {
		// send_box_notice(conn, "Sau " + (conn.p.time_chat_ktg - System.currentTimeMillis()) / 1000
		// + "s nữa mới có thể tiếp tục chat KTG");
		// return;
		// }
		// conn.p.time_chat_ktg = System.currentTimeMillis() + 1000L * 60 * 5;
		conn.p.update_ngoc(-5);
		conn.p.item.char_inventory(5);
		String text = m2.reader().readUTF();
		Manager.gI().chatKTGprocess("@" + conn.p.name + " : " + text);
	}

	public static void send_view_other_player_in4(Session conn, Message m) throws IOException {
		String name = m.reader().readUTF();
		byte type = m.reader().readByte();
		if (type == 0) { // xem thong tin other
			Player p0 = null;
			for (Map[] map : Map.entrys) {
				for (Map map0 : map) {
					for (int i = 0; i < map0.players.size(); i++) {
						Player p01 = map0.players.get(i);
						if (p01.name.equals(name)) {
							p0 = p01;
							break;
						}
					}
				}
			}
			if (p0 != null) {
				send_notice_nobox_white(p0.conn, conn.p.name + " đang dòm ngó đồ đạc của bạn");
				Message m2 = new Message(49);
				m2.writer().writeShort(p0.id);
				m2.writer().writeUTF(name);
				m2.writer().writeByte(p0.clazz);
				m2.writer().writeByte(p0.head);
				m2.writer().writeByte(p0.eye);
				m2.writer().writeByte(p0.hair);
				m2.writer().writeShort(p0.level);
				m2.writer().writeInt(p0.hp);
				m2.writer().writeInt(p0.body.get_max_hp());
				m2.writer().writeByte(p0.typepk);
				m2.writer().writeShort(p0.pointpk);
				m2.writer().writeByte(p0.item.wear.length);
				for (int i = 0; i < p0.item.wear.length; i++) {
					Item3 temp = p0.item.wear[i];
					if (temp != null) {
						m2.writer().writeByte(i);
						m2.writer().writeUTF(temp.name);
						m2.writer().writeByte(temp.clazz);
						m2.writer().writeByte(temp.type);
						m2.writer().writeShort(temp.icon);
						m2.writer().writeByte(temp.part); // show part char
						m2.writer().writeByte(temp.tier); // plus item = tier
						m2.writer().writeShort(temp.level);
						m2.writer().writeByte(temp.color);
						m2.writer().writeByte(temp.op.size());
						for (int j = 0; j < temp.op.size(); j++) {
							m2.writer().writeByte(temp.op.get(j).id);
							m2.writer().writeInt(temp.op.get(j).getParam(temp.tier));
						}
						m2.writer().writeByte(0); // can sell
					} else {
						m2.writer().writeByte(-1);
					}
				}
				if (p0.myclan != null) {
					m2.writer().writeShort(p0.myclan.icon);
					m2.writer().writeUTF(p0.myclan.name_clan_shorted);
					m2.writer().writeByte(p0.myclan.get_mem_type(p0.name));
					m2.writer().writeUTF(p0.myclan.name_clan);
				} else {
					m2.writer().writeShort(-1); // clan
				}
				if (p0.pet_follow) {
					for (Pet temp : p0.mypet) {
						if (temp.is_follow) {
							m2.writer().writeByte(5);
							m2.writer().writeUTF(temp.name);
							m2.writer().writeByte(4);
							m2.writer().writeShort(temp.level);
							m2.writer().writeShort(temp.getlevelpercent());
							m2.writer().writeByte(temp.type);
							m2.writer().writeByte(temp.icon);
							m2.writer().writeByte(temp.nframe);
							m2.writer().writeByte(temp.color);
							m2.writer().writeInt(temp.get_age());
							m2.writer().writeShort(temp.grown);
							m2.writer().writeShort(temp.maxgrown);
							m2.writer().writeShort(temp.point1);
							m2.writer().writeShort(temp.point2);
							m2.writer().writeShort(temp.point3);
							m2.writer().writeShort(temp.point4);
							m2.writer().writeShort(temp.maxpoint);
							m2.writer().writeByte(temp.op.size());
							for (int i12 = 0; i12 < temp.op.size(); i12++) {
								m2.writer().writeByte(temp.op.get(i12).id);
								m2.writer().writeInt(temp.op.get(i12).param);
								m2.writer().writeInt(temp.op.get(i12).maxdam);
							}
							// m.writer().writeByte(0);
							break;
						}
					}
				} else {
					m2.writer().writeByte(-1); // pet
				}
				m2.writer().writeByte(0);
				conn.addmsg(m2);
				m2.cleanup();
			} else {
				send_notice_nobox_white(conn, ("người chơi " + name + " đã offline"));
			}
		}
	}

	public static void send_param_item_wear(Session conn, Message m2) throws IOException {
		@SuppressWarnings("unused")
		byte invenid = m2.reader().readByte();
		byte id = m2.reader().readByte();
		Item3 temp = conn.p.item.bag3[id];
		if (temp != null) {
			Message m = new Message(21);
			m.writer().writeByte(temp.op.size());
			for (int i = 0; i < temp.op.size(); i++) {
				m.writer().writeByte(temp.op.get(i).id);
				m.writer().writeInt(temp.op.get(i).getParam(temp.tier));
			}
			conn.addmsg(m);
			m.cleanup();
		}
	}

	public static void send_box_UI(Session conn, int type) throws IOException {
		Message m = new Message(23);
		switch (type) {
			case 0: { // cua hang poition
				m.writer().writeUTF("Cửa hàng Poition");
				m.writer().writeByte(0);
				m.writer().writeShort(Manager.gI().itempoitionsell.length);
				for (int i = 0; i < Manager.gI().itempoitionsell.length; i++) {
					m.writer().writeShort(Manager.gI().itempoitionsell[i]);
				}
				break;
			}
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
			case 16: {
				m.writer().writeUTF("Cửa hàng trang bị");
				m.writer().writeByte(1);
				m.writer().writeShort(Manager.gI().itemsellTB.get(type - 1).length);
				for (int i = 0; i < Manager.gI().itemsellTB.get(type - 1).length; i++) {
					ItemSell3 temp = Manager.gI().itemsellTB.get(type - 1)[i];
					m.writer().writeShort(temp.id);
					m.writer().writeUTF(ItemTemplate3.item.get(temp.id).getName());
					m.writer().writeByte(temp.clazz);
					m.writer().writeByte(temp.type);
					m.writer().writeShort(ItemTemplate3.item.get(temp.id).getIcon());
					m.writer().writeLong(temp.price);
					m.writer().writeShort(temp.level);
					m.writer().writeByte(temp.color);
					m.writer().writeByte(temp.option.size());
					for (int j = 0; j < temp.option.size(); j++) {
						m.writer().writeByte(temp.option.get(j).id);
						m.writer().writeInt(temp.option.get(j).getParam(0));
					}
					m.writer().writeByte(temp.pricetype);
				}
				break;
			}
			case 18: {
				m.writer().writeUTF("Cường hóa trang bị");
				m.writer().writeByte(5);
				m.writer().writeShort(0);
				break;
			}
			case 17: {
				m.writer().writeUTF("Đá nguyên liệu");
				m.writer().writeByte(4);
				m.writer().writeShort(Manager.gI().item7sell.length);
				for (int i = 0; i < Manager.gI().item7sell.length; i++) {
					m.writer().writeShort(Manager.gI().item7sell[i]);
				}
				break;
			}
			case 19: {
				m.writer().writeUTF("Chuyển hóa trang bị");
				m.writer().writeByte(9);
				m.writer().writeShort(0);
				break;
			}
			case 20: {
				m.writer().writeUTF("Icon Clan");
				m.writer().writeByte(6);
				m.writer().writeShort(31); // 31 in team server
				for (int i = 0; i < 31; i++) {
					m.writer().writeShort(i);
				}
				break;
			}
			case 21: {
				m.writer().writeUTF("Thú cưng");
				m.writer().writeByte(11);
				m.writer().writeShort(0);
				break;
			}
			case 22: {
				m.writer().writeUTF("Thức ăn thú cưng");
				m.writer().writeByte(0);
				m.writer().writeShort(4);
				for (int i = 48; i < 52; i++) {
					m.writer().writeShort(i);
				}
				break;
			}
			case 23: {
				m.writer().writeUTF("Shop trứng");
				m.writer().writeByte(1);
				short[] id_egg = new short[] {2943, 2944};
				long[] price_egg = new long[] {150, 150};
				m.writer().writeShort(id_egg.length);
				for (int i = 0; i < id_egg.length; i++) {
					ItemTemplate3 temp = ItemTemplate3.item.get(id_egg[i]);
					m.writer().writeShort(temp.getId());
					m.writer().writeUTF(temp.getName());
					m.writer().writeByte(temp.getClazz());
					m.writer().writeByte(temp.getType());
					m.writer().writeShort(temp.getIcon());
					m.writer().writeLong(price_egg[i]); // 150 ngoc
					m.writer().writeShort(temp.getLevel());
					m.writer().writeByte(temp.getColor());
					m.writer().writeByte(0); // op size
					m.writer().writeByte(1); // pricetype
				}
				break;
			}
			case 24: {
				m.writer().writeUTF("Hợp nguyên liệu mề đay");
				m.writer().writeByte(18);
				m.writer().writeShort(0);
				break;
			}
			case 25:
			case 26:
			case 27:
			case 28: {
				m.writer().writeUTF("Tạo mề đay");
				m.writer().writeByte(19);
				m.writer().writeShort(0);
				m.writer().writeByte(5);
				//
				m.writer().writeShort(conn.p.medal_create_material[0 + 5 * (type - 25)]);
				m.writer().writeByte(1);
				m.writer().writeShort(conn.p.medal_create_material[1 + 5 * (type - 25)]);
				m.writer().writeByte(1);
				m.writer().writeShort(conn.p.medal_create_material[2 + 5 * (type - 25)]);
				m.writer().writeByte(1);
				m.writer().writeShort(conn.p.medal_create_material[3 + 5 * (type - 25)]);
				m.writer().writeByte(1);
				m.writer().writeShort(conn.p.medal_create_material[4 + 5 * (type - 25)]);
				m.writer().writeByte(1);
				break;
			}
			case 29: {
				m.writer().writeUTF("Icon Clan");
				m.writer().writeByte(6);
				m.writer().writeShort(358); // 31 in team server
				for (int i = 0; i < 31; i++) {
					m.writer().writeShort(i);
				}
				for (int i = 500; i < 827; i++) {
					m.writer().writeShort(i);
				}
				break;
			}
                        case 30: { // cua hang shop bang
				m.writer().writeUTF("Shop Bang");
				m.writer().writeByte(8);
				m.writer().writeShort(Clan.item_shop.length);
				for (int i = 0; i < Clan.item_shop.length; i++) {
					m.writer().writeShort(Clan.item_shop[i]);
				}
				break;
			}
			default: {
				send_notice_box(conn, "Lỗi, hãy thử lại sau");
				break;
			}
		}
		conn.addmsg(m);
		m.cleanup();
	}

	public static void revenge(Session conn, byte index) throws IOException {
		if (conn.p.get_ngoc() < 2) {
			send_notice_box(conn, "2 ngọc còn không có nổi thì có mà báo đời à???");
			return;
		}
		String name = conn.p.list_enemies.get(conn.p.list_enemies.size() - index - 1);
		Player p0 = null;
		for (Map[] map : Map.entrys) {
			for (Map map0 : map) {
				for (int i = 0; i < map0.players.size(); i++) {
					Player p2 = map0.players.get(i);
					if (p2.name.equals(name)) {
						p0 = p2;
						break;
					}
				}
				if (p0 != null) {
					break;
				}
			}
			if (p0 != null) {
				break;
			}
		}
		if (p0 == null) {
			send_notice_box(conn, "Kẻ thù đang offline");
		} else {
			EffTemplate ef = p0.get_eff(-125);
			if (ef == null && p0.map.map_id != 0 && !p0.map.ismaplang) {
				conn.p.update_ngoc(-2);
				conn.p.item.char_inventory(5);
				conn.p.is_changemap = false;
				Map mbuffer2 = p0.map;
				if (mbuffer2 != null) {
					if (conn.p.isdie) {
						return;
					}
					MapService.leave(conn.p.map, conn.p);
					conn.p.map = mbuffer2;
					conn.p.x = p0.x;
					conn.p.y = p0.y;
					MapService.enter(conn.p.map, conn.p);
					Message m = new Message(4);
					m.writer().writeByte(0);
					m.writer().writeShort(0);
					m.writer().writeShort(p0.id);
					m.writer().writeShort(p0.x);
					m.writer().writeShort(p0.y);
					m.writer().writeByte(-1);
					conn.addmsg(m);
					m.cleanup();
					//
					m = new Message(4);
					m.writer().writeByte(0);
					m.writer().writeShort(0);
					m.writer().writeShort(conn.p.id);
					m.writer().writeShort(conn.p.x);
					m.writer().writeShort(conn.p.y);
					m.writer().writeByte(-1);
					p0.conn.addmsg(m);
					m.cleanup();
				} else {
					send_notice_box(conn, "Có lỗi xảy ra khi chuyển map");
				}
			} else {
				send_notice_box(conn, "Kẻ thù đang trong thời gian chống pk");
			}
		}
	}

	public static void send_box_input_text(Session conn, int type, String text, String[] in4) throws IOException {
		Message m = new Message(-31);
		m.writer().writeShort(type);
		m.writer().writeByte(0);
		m.writer().writeUTF(text);
		m.writer().writeByte(in4.length);
		for (int i = 0; i < in4.length; i++) {
			m.writer().writeUTF(in4[i]);
			m.writer().writeByte(0);
		}
		for (int i = 0; i < in4.length; i++) {
			m.writer().writeUTF("");
			m.writer().writeByte(0);
		}
		conn.addmsg(m);
		m.cleanup();
	}

	public static void send_in4_item(Session conn, Message m) throws IOException {
		short id = m.reader().readShort();
		// for (int i = 0; i < conn.p.item.bag3.length; i++) {
		// Item3 temp = conn.p.item.bag3[i];
		// if (temp != null && temp.id == 9) {
		Message m2 = new Message(28);
		m2.writer().writeShort(id); // index?
		m2.writer().writeUTF("Vật phẩm hiển thị lỗi, hãy thoát game vào lại để reset");
		m2.writer().writeByte(8); // type item
		m2.writer().writeByte(0); // id part
		m2.writer().writeByte(0); // class item
		m2.writer().writeShort(0); // icon id
		m2.writer().writeByte(1);// size
		for (int i2 = 0; i2 < 1; i2++) {
			m2.writer().writeByte(69);
			m2.writer().writeInt(99);
		}
		conn.addmsg(m2);
		m2.cleanup();
		// }
		// }
	}

	public static void send_item7_template(Player p, Message m2) throws IOException {
		short id = m2.reader().readShort();
		ItemTemplate7 it7 = ItemTemplate7.item.get(id);
		Message m = new Message(-106);
		m.writer().writeShort(it7.getId());
		m.writer().writeShort(it7.getIcon());
		m.writer().writeLong(it7.getPrice());
		m.writer().writeUTF(it7.getName());
		m.writer().writeUTF(it7.getContent());
		m.writer().writeByte(it7.getType());
		m.writer().writeByte(it7.getPricetype());
		m.writer().writeByte(it7.getSell());
		m.writer().writeShort(it7.getValue());
		m.writer().writeByte(it7.getTrade());
		m.writer().writeByte(it7.getColor());
		p.conn.addmsg(m);
		m.cleanup();
	}

	public static void chat_tab(Session conn, Message m2) throws IOException {
		String name = m2.reader().readUTF();
		String chat = m2.reader().readUTF();
		if (name.equals("Đội nhóm") && conn.p.party != null && conn.p.party.get_mems().contains(conn.p)) {
			Message m = new Message(34);
			m.writer().writeUTF("Đội nhóm");
			m.writer().writeUTF("@" + conn.p.name + " : " + chat);
			for (int i = 0; i < conn.p.party.get_mems().size(); i++) {
				if (conn.p.party.get_mems().get(i).id != conn.p.id) {
					conn.p.party.get_mems().get(i).conn.addmsg(m);
				}
			}
			m.cleanup();
		} else if (name.equals("Bang Hội") && conn.p.myclan != null) {
			Message m = new Message(34);
			m.writer().writeUTF("Bang Hội");
			m.writer().writeUTF("@" + conn.p.name + " : " + chat);
			for (int i = 0; i < conn.p.myclan.mems.size(); i++) {
				String name2 = conn.p.myclan.mems.get(i).name;
				if (!name2.equals(conn.p.name)) {
					for (Map[] map : Map.entrys) {
						for (Map map2 : map) {
							synchronized (map2) {
								for (Player p0 : map2.players) {
									if (p0.name.equals(name2)) {
										p0.conn.addmsg(m);
									}
								}
							}
						}
					}
				}
			}
			m.cleanup();
		} else {
			Player p0 = null;
			for (Map[] map : Map.entrys) {
				for (Map map0 : map) {
					for (int i = 0; i < map0.players.size(); i++) {
						if (map0.players.get(i).name.equals(name)) {
							p0 = map0.players.get(i);
						}
					}
				}
			}
			if (p0 == null) {
				send_notice_box(conn, "Người chơi đã offline!");
			} else {
				if (p0.name.equals(conn.p.name)) {
					send_notice_box(conn, "mày bị khùng hay sao mà tự nói với chính mình vậy?????");
					return;
				}
				Message m = new Message(34);
				m.writer().writeUTF(conn.p.name);
				m.writer().writeUTF(chat);
				p0.conn.addmsg(m);
				m.cleanup();
			}
		}
	}

	public static void pet_eat(Session conn, Message m2) throws IOException {
		short id_pet = m2.reader().readShort();
		short id_it = m2.reader().readShort();
		byte cat = m2.reader().readByte();
		byte type = m2.reader().readByte();
		// System.out.println(id_pet);
		// System.out.println(id_it);
		// System.out.println(cat);
		// System.out.println(type);
		if (cat != 3 && (cat == 4 && id_it != 48 && id_it != 49 && id_it != 50 && id_it != 51)) {
			send_notice_box(conn, "Không thể cho ăn vật phẩm này!");
			return;
		}
		int index_pet = id_pet;
		if (type == 1) {
			for (int i = 0; i < conn.p.mypet.size(); i++) {
				if (conn.p.mypet.get(i).is_follow) {
					index_pet = i;
					break;
				}
			}
		}
		if (cat == 4) {
			if (index_pet > -1) {
				if (conn.p.mypet.get(index_pet).grown >= conn.p.mypet.get(index_pet).maxgrown) {
					send_notice_box(conn, "bụng tao to thế rồi mà mày cứ nhét vào nữa à? - pet của bạn nói!");
					return;
				}
				conn.p.mypet.get(index_pet).update_exp(250);
				switch (id_it) {
					case 51: {
						conn.p.mypet.get(index_pet).grown += 20;
						conn.p.mypet.get(index_pet).point1 += 10;
						send_notice_box(conn, "+20 điểm cho ăn, +10 điểm vào nhóm sức mạnh");
						break;
					}
					case 49: {
						conn.p.mypet.get(index_pet).grown += 20;
						conn.p.mypet.get(index_pet).point4 += 10;
						send_notice_box(conn, "+20 điểm cho ăn, +10 điểm vào nhóm tinh thần");
						break;
					}
					case 50: {
						conn.p.mypet.get(index_pet).grown += 20;
						conn.p.mypet.get(index_pet).point3 += 10;
						send_notice_box(conn, "+20 điểm cho ăn, +10 điểm vào nhóm thể lực");
						break;
					}
					case 48: {
						conn.p.mypet.get(index_pet).grown += 20;
						conn.p.mypet.get(index_pet).point2 += 10;
						send_notice_box(conn, "+20 điểm cho ăn, +10 điểm vào nhóm khéo léo");
						break;
					}
				}
				conn.p.item.remove(4, id_it, 1);
				conn.p.item.char_inventory(4);
			} else {
				send_notice_box(conn, "Có lỗi xảy ra!");
				return;
			}
		} else if (cat == 3) {
			if (conn.p.item.bag3[id_it] != null && index_pet > -1) {
				if (conn.p.mypet.get(index_pet).grown >= conn.p.mypet.get(index_pet).maxgrown) {
					send_notice_box(conn, "bụng tao to thế rồi mà mày cứ nhét vào nữa à? - pet của bạn nói!");
					return;
				}
				conn.p.mypet.get(index_pet).update_exp(250);
				int type_ = conn.p.item.bag3[id_it].type;
				if (type_ == 8 || type_ == 9) {
					conn.p.mypet.get(index_pet).grown += 20;
					conn.p.mypet.get(index_pet).point1 += 10;
					send_notice_box(conn, "+20 điểm cho ăn, +10 điểm vào nhóm sức mạnh");
				} else if (type_ == 10 || type_ == 11) {
					conn.p.mypet.get(index_pet).grown += 20;
					conn.p.mypet.get(index_pet).point4 += 10;
					send_notice_box(conn, "+20 điểm cho ăn, +10 điểm vào nhóm tinh thần");
				} else if (type_ == 0 || type_ == 1 || type_ == 2 || type_ == 3 || type_ == 6) {
					conn.p.mypet.get(index_pet).grown += 20;
					conn.p.mypet.get(index_pet).point3 += 10;
					send_notice_box(conn, "+20 điểm cho ăn, +10 điểm vào nhóm thể lực");
				} else if (type_ == 4 || type_ == 5) {
					conn.p.mypet.get(index_pet).grown += 20;
					conn.p.mypet.get(index_pet).point2 += 10;
					send_notice_box(conn, "+20 điểm cho ăn, +10 điểm vào nhóm khéo léo");
				}
				conn.p.item.bag3[id_it] = null;
				conn.p.item.char_inventory(3);
			} else {
				send_notice_box(conn, "Có lỗi xảy ra!");
				return;
			}
		}
		if (type == 1) {
			send_wear(conn.p);
			send_char_main_in4(conn.p);
		} else if (type == 0) {
			Message m = new Message(44);
			m = new Message(44);
			m.writer().writeByte(28);
			m.writer().writeByte(2);
			m.writer().writeByte(9);
			m.writer().writeByte(9);
			m.writer().writeShort(index_pet);
			conn.addmsg(m);
			m.cleanup();
			//
			m = new Message(44);
			m.writer().writeByte(28);
			m.writer().writeByte(1);
			m.writer().writeByte(9);
			m.writer().writeByte(9);
			m.writer().writeUTF(conn.p.mypet.get(index_pet).name);
			m.writer().writeByte(conn.p.mypet.get(index_pet).type);
			m.writer().writeShort(index_pet); // id
			m.writer().writeShort(conn.p.mypet.get(index_pet).level);
			m.writer().writeShort(conn.p.mypet.get(index_pet).getlevelpercent()); // exp
			m.writer().writeByte(conn.p.mypet.get(index_pet).type);
			m.writer().writeByte(conn.p.mypet.get(index_pet).icon);
			m.writer().writeByte(conn.p.mypet.get(index_pet).nframe);
			m.writer().writeByte(conn.p.mypet.get(index_pet).color);
			m.writer().writeInt(conn.p.mypet.get(index_pet).get_age());
			m.writer().writeShort(conn.p.mypet.get(index_pet).grown);
			m.writer().writeShort(conn.p.mypet.get(index_pet).maxgrown);
			m.writer().writeShort(conn.p.mypet.get(index_pet).point1);
			m.writer().writeShort(conn.p.mypet.get(index_pet).point2);
			m.writer().writeShort(conn.p.mypet.get(index_pet).point3);
			m.writer().writeShort(conn.p.mypet.get(index_pet).point4);
			m.writer().writeShort(conn.p.mypet.get(index_pet).maxpoint);
			m.writer().writeByte(conn.p.mypet.get(index_pet).op.size());
			for (int i2 = 0; i2 < conn.p.mypet.get(index_pet).op.size(); i2++) {
				Option_pet temp2 = conn.p.mypet.get(index_pet).op.get(i2);
				m.writer().writeByte(temp2.id);
				m.writer().writeInt(temp2.param);
				m.writer().writeInt(temp2.maxdam);
			}
			conn.addmsg(m);
			m.cleanup();
		}
	}

	public static void pet_process(Session conn, Message m2) throws IOException {
		byte type = m2.reader().readByte();
		short id = m2.reader().readShort();
		// System.out.println(type);
		// System.out.println(id);
		if (type == 1) {
			boolean duplicated = false;
			for (Pet temp : conn.p.mypet) {
				if (temp.time_born > System.currentTimeMillis()) {
					duplicated = true;
					break;
				}
			}
			if (duplicated) {
				send_notice_box(conn, "trong chuồng đã có trứng đang ấp, hãy thử lại sau");
				return;
			}
			Item3 it = conn.p.item.bag3[id];
			if (it != null) {
				Pet temp = Pet.get_pet(it.id);
				if (temp == null) {
					send_notice_box(conn, "có lỗi xảy ra, hãy thử lại sau");
					return;
				}
				conn.p.mypet.add(temp);
				//
				Message m = new Message(44);
				m.writer().writeByte(28);
				m.writer().writeByte(1);
				m.writer().writeByte(3);
				m.writer().writeByte(3);
				m.writer().writeUTF(it.name);
				m.writer().writeByte(it.clazz);
				m.writer().writeShort(it.id);
				m.writer().writeByte(it.type);
				m.writer().writeShort(it.icon);
				m.writer().writeByte(it.tier);
				m.writer().writeShort(it.level);
				m.writer().writeByte(it.color);
				it.islock = false;
				m.writer().writeByte(it.islock ? 0 : 1);
				m.writer().writeByte(it.islock ? 0 : 1);
				m.writer().writeByte(0); // op size
				m.writer().writeInt((int) ((temp.time_born - System.currentTimeMillis()) / 60000));
				m.writer().writeByte(it.islock ? 1 : 0);
				conn.addmsg(m);
				m.cleanup();
				conn.p.item.bag3[id] = null;
				conn.p.item.char_inventory(3);
			}
		} else if (type == 0) {
			Message m = null;
			if (conn.p.pet_follow) {
				for (Pet temp : conn.p.mypet) {
					if (temp.is_follow) {
						temp.is_follow = false;
						m = new Message(44);
						m.writer().writeByte(28);
						m.writer().writeByte(1);
						m.writer().writeByte(9);
						m.writer().writeByte(9);
						m.writer().writeUTF(temp.name);
						m.writer().writeByte(temp.type);
						m.writer().writeShort(conn.p.mypet.indexOf(temp)); // id
						m.writer().writeShort(temp.level);
						m.writer().writeShort(temp.getlevelpercent()); // exp
						m.writer().writeByte(temp.type);
						m.writer().writeByte(temp.icon);
						m.writer().writeByte(temp.nframe);
						m.writer().writeByte(temp.color);
						m.writer().writeInt(temp.get_age());
						m.writer().writeShort(temp.grown);
						m.writer().writeShort(temp.maxgrown);
						m.writer().writeShort(temp.point1);
						m.writer().writeShort(temp.point2);
						m.writer().writeShort(temp.point3);
						m.writer().writeShort(temp.point4);
						m.writer().writeShort(temp.maxpoint);
						m.writer().writeByte(temp.op.size());
						for (int i2 = 0; i2 < temp.op.size(); i2++) {
							Option_pet temp2 = temp.op.get(i2);
							m.writer().writeByte(temp2.id);
							m.writer().writeInt(temp2.param);
							m.writer().writeInt(temp2.maxdam);
						}
						conn.p.conn.addmsg(m);
						m.cleanup();
						break;
					}
				}
			}
			conn.p.mypet.get(id).is_follow = true;
			conn.p.pet_follow = true;
			m = new Message(44);
			m.writer().writeByte(28);
			m.writer().writeByte(2);
			m.writer().writeByte(9);
			m.writer().writeByte(9);
			m.writer().writeShort(id);
			conn.addmsg(m);
			m.cleanup();
			//
			Service.send_wear(conn.p);
			Service.send_char_main_in4(conn.p);
		}
	}

	public static void sell_item(Session conn, Message m2) throws IOException {
		if (conn.p.isdie) {
			return;
		}
		byte type = m2.reader().readByte();
		short id = m2.reader().readShort();
		byte typedel = m2.reader().readByte();
		switch (typedel) {
			case 0: { // drop 2 map
				conn.p.map.drop_item(conn.p, type, id);
				break;
			}
			case 1: { // sell in shop
				switch (type) {
					case 3:
					case 4:
					case 7: {
						int quant = conn.p.item.total_item_by_id(type, id);
						conn.p.update_vang(quant * 5);
						conn.p.item.remove(type, id, quant);
						conn.p.item.char_inventory(4);
						conn.p.item.char_inventory(7);
						conn.p.item.char_inventory(3);
						break;
					}
					default: {
						Service.send_notice_box(conn, "Chưa hỗ trợ bán vật phẩm này");
						break;
					}
				}
				break;
			}
		}
	}

	public static void buy_item(Player p, Message m) throws IOException {
		byte type = m.reader().readByte();
		short idbuy = m.reader().readShort();
		int quanity = Short.toUnsignedInt(m.reader().readShort());
		// System.out.println(type);
		// System.out.println(idbuy);
		// System.out.println(quanity);
		if (idbuy < 0 || quanity <= 0 || quanity > 32000) {
			return;
		}
		if (p.item.get_bag_able() < 1) {
			send_notice_nobox_white(p.conn, "Hành trang đầy!!");
			return;
		}
		switch (type) {
			case 0: { // cua hang potion
				if (idbuy > (ItemTemplate4.item.size() - 1)) {
					return;
				}
				long price = ItemTemplate4.item.get(idbuy).getPrice() * quanity;
				if (ItemTemplate4.item.get(idbuy).getPricetype() == 0) {
					if (p.get_vang() < price) {
						send_notice_box(p.conn, "Không đủ " + price + " vàng");
						return;
					}
					p.update_vang(-price);
				} else {
					if (p.get_ngoc() < price) {
						send_notice_box(p.conn, "Không đủ " + price + " ngọc");
						return;
					}
					p.update_ngoc(-price);
				}
				int quant_add_bag = quanity + p.item.total_item_by_id(4, idbuy);
				if (quant_add_bag > 32000) {
					send_notice_box(p.conn, "không thể mua thêm");
					return;
				}
				Item47 itbag = new Item47();
				itbag.id = idbuy;
				itbag.quantity = (short) quanity;
				itbag.category = 4;
				p.item.add_item_bag47(4, itbag);
				p.item.char_inventory(4);
				p.item.char_inventory(7);
				p.item.char_inventory(3);
				break;
			}
			case 1: {
				if (idbuy > (ItemTemplate3.item.size() - 1)) {
					return;
				}
				if (idbuy == 2943 || idbuy == 2944) {
					if (p.get_ngoc() < 150) {
						send_notice_box(p.conn, "Không đủ 150 ngọc");
						return;
					}
					p.update_ngoc(-150);
					//
					Item3 itbag = new Item3();
					itbag.id = idbuy;
					itbag.clazz = ItemTemplate3.item.get(idbuy).getClazz();
					itbag.type = ItemTemplate3.item.get(idbuy).getType();
					itbag.level = ItemTemplate3.item.get(idbuy).getLevel();
					itbag.icon = ItemTemplate3.item.get(idbuy).getIcon();
					itbag.color = ItemTemplate3.item.get(idbuy).getColor();
					itbag.part = ItemTemplate3.item.get(idbuy).getPart();
					itbag.islock = true;
					itbag.name = ItemTemplate3.item.get(idbuy).getName();
					itbag.tier = 0;
					itbag.op = new ArrayList<>();
					itbag.time_use = 0;
					p.item.add_item_bag3(itbag);
				} else {
					long price = 0;
					ItemSell3 buffer = null;
					for (ItemSell3[] itsell_3 : Manager.gI().itemsellTB) {
						for (ItemSell3 itsell3 : itsell_3) {
							if (itsell3.id == idbuy) {
								buffer = itsell3;
								price = quanity * itsell3.price;
								break;
							}
						}
					}
					if (price == 0) {
						return;
					}
					if (buffer.pricetype == 0) {
						if (p.get_vang() < price) {
							send_notice_box(p.conn, "Không đủ " + price + " vàng");
							return;
						}
						p.update_vang(-price);
					} else {
						if (p.get_ngoc() < price) {
							send_notice_box(p.conn, "Không đủ " + price + " ngọc");
							return;
						}
						p.update_ngoc(-price);
					}
					Item3 itbag = new Item3();
					itbag.id = idbuy;
					itbag.clazz = buffer.clazz;
					itbag.type = buffer.type;
					itbag.level = buffer.level;
					itbag.icon = ItemTemplate3.item.get(idbuy).getIcon();
					itbag.color = buffer.color;
					itbag.part = ItemTemplate3.item.get(idbuy).getPart();
					itbag.islock = false;
					itbag.name = ItemTemplate3.item.get(idbuy).getName();
					itbag.tier = 0;
					List<Option> opnew = new ArrayList<>();
					for (Option op_old : buffer.option) {
						Option temp = new Option(1, 1);
						temp.id = op_old.id;
						if (temp.id != 37 && temp.id != 38) {
							if (op_old.getParam(0) < 10) {
								temp.setParam(Util.random(0, 10));
							} else {
								temp.setParam(Util.random((9 * op_old.getParam(0)) / 10, op_old.getParam(0)));
							}
						} else {
							temp.setParam(1);
						}
						opnew.add(temp);
					}
					itbag.op = new ArrayList<>();
					itbag.op.addAll(opnew);
					p.item.add_item_bag3(itbag);
				}
				p.item.char_inventory(4);
				p.item.char_inventory(7);
				p.item.char_inventory(3);
				break;
			}
			case 2: {
				p.hair = (byte) idbuy;
				for (int i = 0; i < p.map.players.size(); i++) {
					Player p0 = p.map.players.get(i);
					if (p0.id != p.id && Math.abs(p0.x - p.x) < 200 && Math.abs(p0.y - p.y) < 200) {
						MapService.send_in4_other_char(p0.map, p0, p);
					}
				}
				Service.send_char_main_in4(p);
				break;
			}
			case 4: {
				if (idbuy > (ItemTemplate7.item.size() - 1)) {
					return;
				}
				long price = ItemTemplate7.item.get(idbuy).getPrice() * quanity;
				if (ItemTemplate7.item.get(idbuy).getPricetype() == 0) {
					if (p.get_vang() < price) {
						send_notice_box(p.conn, "Không đủ " + price + " vàng");
						return;
					}
					p.update_vang(-price);
					Log.gI().add_log(p.name, "mua " + quanity + " item " + ItemTemplate7.item.get(idbuy).getName() + " hết"
					      + Util.number_format(price) + " vàng");
				} else {
					if (p.get_ngoc() < price) {
						send_notice_box(p.conn, "Không đủ " + price + " ngọc");
						return;
					}
					p.update_ngoc(-price);
					Log.gI().add_log(p.name, "mua " + quanity + " item " + ItemTemplate7.item.get(idbuy).getName() + " hết"
					      + Util.number_format(price) + " ngọc");
				}
				int quant_add_bag = quanity + p.item.total_item_by_id(7, idbuy);
				if (quant_add_bag > 32000) {
					send_notice_box(p.conn, "không thể mua thêm");
					return;
				}
				Item47 itbag = new Item47();
				itbag.id = idbuy;
				itbag.quantity = (short) quanity;
				itbag.category = 7;
				p.item.add_item_bag47(7, itbag);
				p.item.char_inventory(4);
				p.item.char_inventory(7);
				p.item.char_inventory(3);
				break;
			}
			case 6: {
				int value = 200;
				if (idbuy >= 500 && idbuy <= 816) {
					value = 500;
				} else if (idbuy >= 817 && idbuy <= 826) {
					value = 1000;
				}
				if (p.myclan.icon == 0) {
					if (value > 200 && p.get_ngoc() < (value - 200)) {
						Service.send_notice_box(p.conn, "Không đủ " + (value - 200) + " ngọc!");
						return;
					}
					p.update_ngoc(-(value - 200));
					send_notice_box(p.conn,
					      "Chúc mừng bạn đã đăng ký thành công bang\nhội. Vui lòng vào Menu>Chức Năng>Bang hội\nđể xem thông tin bang");
				} else {
					if (p.get_ngoc() < value) {
						Service.send_notice_box(p.conn, "Không đủ " + value + " ngọc!");
						return;
					}
					p.update_ngoc(-value);
					send_notice_box(p.conn, "Thay đổi icon thành công");
				}
				p.item.char_inventory(5);
				p.myclan.icon = idbuy;
				MapService.update_in4_2_other_inside(p.map, p);
				Service.send_char_main_in4(p);
				for (Clan_mems mem : p.myclan.mems) {
					Player p0 = Map.get_player_by_name(mem.name);
					if (p0 != null) {
						MapService.update_in4_2_other_inside(p0.map, p0);
						Service.send_char_main_in4(p0);
					}
				}
				break;
			}
			case 10: {
				Message m2 = new Message(77);
				m2.writer().writeByte(6);
				p.conn.addmsg(m2);
				m2.cleanup();
				//
				ItemTemplate3 it = ItemTemplate3.item.get(idbuy);
				m2 = new Message(77);
				m2.writer().writeByte(0);
				m2.writer().writeInt(it.getId());
				m2.writer().writeUTF("Chế tạo cánh");
				m2.writer().writeInt(200_000);
				m2.writer().writeShort(60);
				m2.writer().writeInt(0);
				m2.writer().writeByte(6);
				m2.writer().writeShort(8);
				m2.writer().writeShort(80);
				m2.writer().writeShort(9);
				m2.writer().writeShort(60);
				m2.writer().writeShort(10);
				m2.writer().writeShort(40);
				m2.writer().writeShort(11);
				m2.writer().writeShort(20);
				m2.writer().writeShort(0);
				m2.writer().writeShort(100);
				m2.writer().writeShort(3);
				m2.writer().writeShort(20);
				p.conn.addmsg(m2);
				m2.cleanup();
				//
				m2 = new Message(77);
				m2.writer().writeByte(1);
				m2.writer().writeUTF(it.getName());
				p.conn.addmsg(m2);
				m2.cleanup();
				p.is_create_wing = true;
				break;
			}
                        case 8: {
				if (p.myclan != null && p.myclan.mems.get(0).name.equals(p.name)) {
					long price = ItemTemplate4.item.get(idbuy).getPrice() * quanity;
					if (ItemTemplate4.item.get(idbuy).getPricetype() == 0) {
						if (p.myclan.get_vang() < price) {
							send_notice_box(p.conn, "Không đủ " + price + " vàng");
							return;
						}
						p.myclan.update_vang(-price);
					} else {
						if (p.myclan.get_ngoc() < price) {
							send_notice_box(p.conn, "Không đủ " + price + " ngọc");
							return;
						}
						p.myclan.update_ngoc(-((int) price));
					}
					Item47 itbag = new Item47();
					itbag.id = idbuy;
					itbag.quantity = (short) 1;
					itbag.category = 4;
					p.myclan.item_clan.add(itbag);
				}
				break;
			}
		}
		if (type == 6 || type == 10) {
		} else {
			send_notice_box(p.conn, "Mua Thành Công");
		}
	}

	public static void remove_time_use_item(Session conn, Message m2) throws IOException {
		byte type = m2.reader().readByte();
		byte cat = m2.reader().readByte();
		short iditem = m2.reader().readShort();
		// System.out.println(type);
		// System.out.println(cat);
		// System.out.println(iditem);
		if (type == 6) {
			switch (cat) {
				case 3: {
					Item3 it = conn.p.item.bag3[iditem];
					if (it != null && it.time_use > 0) {
						int ngoc_ = conn.p.get_ngoc();
						if (ngoc_ > 4) {
							long price = it.time_use - System.currentTimeMillis();
							price /= 3_600_000;
							price = (price > 4) ? (price + 1) : 5;
							if (ngoc_ >= price) {
								send_box_input_yesno(conn, 115, "Đồng ý dùng " + price + " ngọc để mở khóa thời gian sử dụng?");
							} else {
								send_box_input_yesno(conn, 115, "Đồng ý dùng " + ngoc_ + " ngọc để mở khóa " + ngoc_ + "h");
							}
							conn.p.id_remove_time_use = iditem;
						} else {
							send_notice_box(conn, "Tối thiểu 5 ngọc!");
						}
					}
					break;
				}
			}
		}
	}
}
