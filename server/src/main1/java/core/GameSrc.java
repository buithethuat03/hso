package core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import client.Player;
import io.Message;
import io.Session;
import map.Map;
import template.Item3;
import template.ItemTemplate3;
import template.ItemTemplate7;
import template.Medal_Material;
import template.Option;

public class GameSrc {
	public static byte[] percent = new byte[] {95, 90, 80, 70, 60, 50, 45, 35, 25, 15, 10, 5, 4, 3, 2, 1};
	public static short[] wing_upgrade_material_long_khuc_xuong = new short[] {10, 12, 15, 19, 24, 30, 37, 45, 54, 64,
	      75, 87, 100, 114, 129, 145, 162, 180, 199, 219, 240, 262, 285, 309, 334, 360, 387, 415, 444, 474};
	public static short[] wing_upgrade_material_kim_loai = new short[] {20, 22, 25, 29, 34, 40, 47, 55, 64, 74, 85, 97,
	      110, 124, 139, 155, 172, 190, 209, 229, 250, 272, 295, 319, 344, 370, 397, 425, 454, 484};
	public static short[] wing_upgrade_material_da_cuong_hoa = new short[] {50, 54, 60, 68, 78, 90, 104, 120, 138, 158,
	      180, 204, 230, 258, 288, 320, 354, 390, 428, 468, 510, 554, 600, 648, 698, 750, 804, 860, 918, 978};
	public static int[] wing_upgrade_material_gold = new int[] {50000, 52000, 55000, 59000, 64000, 70000, 77000, 85000,
	      94000, 104000, 115000, 127000, 140000, 154000, 169000, 185000, 202000, 220000, 239000, 259000, 280000, 302000,
	      325000, 349000, 374000, 400000, 427000, 455000, 484000, 514000};
	public static int[] wing_upgrade_material_time = new int[] {2, 4, 6, 8, 12, 15, 20, 30, 45, 60, 100, 130, 150,
	      180, 220, 242, 286, 322, 340, 380, 422, 456, 502, 550, 600, 652, 706, 742, 770, 830};

	public static void rebuild_item(Session conn, Message m2) throws IOException {
		byte type = m2.reader().readByte();
		short id = m2.reader().readShort();
		byte tem = m2.reader().readByte(); // type item insert
		// System.out.println(type);
		// System.out.println(id);
		// System.out.println(tem);
		if (tem == 7 && conn.p.id_item_rebuild == -1) {
			Service.send_notice_nobox_white(conn, "Bỏ đồ đập vào trước nhá");
			return;
		}
		if (tem == 7) {
			if (id != 13 && id != 14 && id != 4) {
				Service.send_notice_box(conn, "Chỉ nên dùng cỏ 4 lá hoặc đá 3 màu");
				return;
			}
			Message m = new Message(67);
			m.writer().writeByte(type);
			int tier = conn.p.item.bag3[conn.p.id_item_rebuild].tier;
			switch (type) {
				case 0: {
					m.writer().writeShort(id);
					conn.p.id_use_mayman = id;
					m.writer().writeByte(7);
					m.writer().writeUTF(GameSrc.percent[tier] + "% + 30%");
					conn.p.is_use_mayman = true;
					break;
				}
				case 1: {
					m.writer().writeByte(7);
					m.writer().writeUTF(GameSrc.percent[tier] + "%");
					conn.p.is_use_mayman = false;
					conn.p.id_use_mayman = -1;
					break;
				}
				default: {
					Service.send_notice_box(conn, "Chưa có chức năng");
					return;
				}
			}
			conn.addmsg(m);
			m.cleanup();
		} else {
			Message m = new Message(67);
			Message m12 = null;
			if (type == 2 && !checkmaterial(conn, conn.p.id_item_rebuild, tem)) {
				m.writer().writeByte(3);
				if (conn.p.get_vang() < 10_000 || conn.p.get_ngoc() < 10) {
					m.writer().writeUTF("Vàng trên 10k và ngọc trên 10 mới có thể tiếp tục kk");
				} else {
					m.writer().writeUTF("Mi còn thiếu nguyên liệu...");
				}
			} else {
				m.writer().writeByte(type);
				switch (type) {
					case 0: {
						Item3 it = conn.p.item.bag3[id];
						if (it == null || !check_item_can_rebuild(it.type)) {
							Service.send_notice_box(conn, "Không hợp lệ!");
							return;
						}
						m.writer().writeShort(id);
						conn.p.id_item_rebuild = id;
						m.writer().writeByte(3);
						m.writer().writeUTF(GameSrc.percent[it.tier] + "%");
						// remove item may man
						Message m1212 = new Message(67);
						m1212.writer().writeByte(1);
						m1212.writer().writeByte(7);
						m1212.writer().writeUTF(GameSrc.percent[it.tier] + "%");
						conn.addmsg(m1212);
						m1212.cleanup();
						conn.p.is_use_mayman = false;
						conn.p.id_use_mayman = -1;
						//
						break;
					}
					case 1: {
						m.writer().writeByte(3);
						m.writer().writeUTF("HSO"); // 0,01%
						conn.p.id_item_rebuild = -1;
						break;
					}
					case 2: {
						if (conn.p.time_speed_rebuild > System.currentTimeMillis()) {
							if (++conn.p.enough_time_disconnect > 2) {
								conn.close();
							}
							return;
						}
						conn.p.time_speed_rebuild = System.currentTimeMillis() + 4500L;
						conn.p.enough_time_disconnect = 0;
						Item3 it_upgrade = conn.p.item.bag3[conn.p.id_item_rebuild];
						if (it_upgrade == null) {
							return;
						}
						if (it_upgrade.tier > 14) {
							return;
						}
						boolean percent;
						int get_percent = GameSrc.percent[it_upgrade.tier];
						if (it_upgrade.tier < 7) {
							get_percent += 30;
						}
						if (conn.p.is_use_mayman && (conn.p.id_use_mayman == 14 || conn.p.id_use_mayman == 13)) {
							percent = (get_percent + 15) > Util.random(0, 120);
						} else {
							percent = get_percent > Util.random(0, 120);
						}
						if (percent) {
							conn.p.item.bag3[conn.p.id_item_rebuild].tier++;
							if (it_upgrade.tier >= 9 && conn.p.item.bag3[conn.p.id_item_rebuild].type == 5) {
								for (int i = 0; i < conn.p.item.bag3[conn.p.id_item_rebuild].op.size(); i++) {
									if (conn.p.item.bag3[conn.p.id_item_rebuild].op.get(i).id == 37 || 
									      conn.p.item.bag3[conn.p.id_item_rebuild].op.get(i).id == 38) {
										conn.p.item.bag3[conn.p.id_item_rebuild].op.get(i).setParam(2);
									}
								}
							}
						} else {
							if (conn.p.is_use_mayman && (conn.p.id_use_mayman == 14 || conn.p.id_use_mayman == 13)) {
								if (conn.p.id_use_mayman == 13) {
									if (it_upgrade.tier > 6 && it_upgrade.tier != 11) {
										it_upgrade.tier -= 1;
									}
								}
							} else {
								if (it_upgrade.tier >= 11) {
									it_upgrade.tier = 11;
								} else if (it_upgrade.tier >= 6) {
									it_upgrade.tier = 6;
								} else if (it_upgrade.tier >= 1) {
									it_upgrade.tier = 1;
								}
							}
						}
						if (conn.p.is_use_mayman
						      && (conn.p.id_use_mayman == 14 || conn.p.id_use_mayman == 13 || conn.p.id_use_mayman == 4)) {
							conn.p.item.remove(7, conn.p.id_use_mayman, 1);
							conn.p.item.char_inventory(7);
							if (conn.p.item.total_item_by_id(7, conn.p.id_use_mayman) < 1) {
								conn.p.is_use_mayman = false;
								conn.p.id_use_mayman = -1;
							}
						}
						conn.p.item.char_inventory(4);
						conn.p.item.char_inventory(7);
						conn.p.item.char_inventory(3);
						String per = GameSrc.percent[it_upgrade.tier] + "%";
						if (tem == 0) { // vang
							if (!percent) {
								m.writer().writeByte(4);
								m.writer().writeUTF("Ta rất tiếc, quá trình cường hóa đã thất bại.");
							} else {
								m.writer().writeByte(3);
								m.writer().writeUTF("Mi đã cường hóa thành công " + it_upgrade.name + "!");
							}
						} else if (tem == 1) { // ngoc
							if (!percent) {
								m.writer().writeByte(4);
								m.writer().writeUTF("Ta rất tiếc, quá trình cường hóa đã thất bại.");
							} else {
								m.writer().writeByte(3);
								m.writer().writeUTF("Mi đã cường hóa thành công " + it_upgrade.name + "!");
							}
						}
						if (it_upgrade.tier < 15) {
							m12 = new Message(67);
							m12.writer().writeByte(0);
							m12.writer().writeShort(conn.p.id_item_rebuild);
							m12.writer().writeByte(3);
							m12.writer().writeUTF(per);
						}
						break;
					}
					default: {
						Service.send_notice_box(conn, "Chưa có chức năng");
						return;
					}
				}
			}
			conn.addmsg(m);
			m.cleanup();
			if (m12 != null) {
				conn.addmsg(m12);
				m12.cleanup();
			}
		}
	}

	private static boolean check_item_can_rebuild(byte type) {
		switch (type) {
			case 0: // coat
			case 1: // pant
			case 2: // crown
			case 3: // grove
			case 4: // ring
			case 5: // chain
			case 6: // shoes
				// case 7: // wing
			case 15: // fashion
			case 8:
			case 9:
			case 10:
			case 11: {
				return true;
			}
		}
		return false;
	}

	private static boolean checkmaterial(Session conn, short id, byte tem) throws IOException {
		if (conn.p.get_vang() < 10_000 || conn.p.get_ngoc() < 10) {
			return false;
		}
		if (!conn.p.item.bag3[id].islock) {
			conn.p.item.bag3[id].islock = true;
			conn.p.item.bag3[id].name = ItemTemplate3.item.get(conn.p.item.bag3[id].id).getName() + " [Khóa]";
		}
		Item3 it3 = conn.p.item.bag3[id];
		switch (it3.tier) {
			case 0:
			case 1:
			case 2: {
				if (tem == 0) {
					conn.p.update_vang(-3000);
				} else {
					conn.p.update_ngoc(-4);
				}
				if (conn.p.item.total_item_by_id(7, 0) > 0 && conn.p.item.total_item_by_id(7, 1) > 0) {
					conn.p.item.remove(7, 0, 1);
					conn.p.item.remove(7, 1, 1);
					updatematerial(conn, 7, new short[] {0, 1});
					return true;
				}
				break;
			}
			case 3:
			case 4:
			case 5: {
				if (tem == 0) {
					conn.p.update_vang(-4000);
				} else {
					conn.p.update_ngoc(-5);
				}
				if (conn.p.item.total_item_by_id(7, 0) > 0 && conn.p.item.total_item_by_id(7, 1) > 0) {
					conn.p.item.remove(7, 0, 1);
					conn.p.item.remove(7, 1, 1);
					updatematerial(conn, 7, new short[] {0, 1});
					return true;
				}
				break;
			}
			case 6:
			case 7:
			case 8: {
				if (tem == 0) {
					conn.p.update_vang(-6000);
				} else {
					conn.p.update_ngoc(-6);
				}
				if (conn.p.item.total_item_by_id(7, 0) > 0 && conn.p.item.total_item_by_id(7, 1) > 0
				      && conn.p.item.total_item_by_id(7, 2) > 0) {
					conn.p.item.remove(7, 0, 1);
					conn.p.item.remove(7, 1, 1);
					conn.p.item.remove(7, 2, 1);
					updatematerial(conn, 7, new short[] {0, 1, 2});
					return true;
				}
				break;
			}
			case 9:
			case 10: {
				if (tem == 0) {
					conn.p.update_vang(-8000);
				} else {
					conn.p.update_ngoc(-7);
				}
				if (conn.p.item.total_item_by_id(7, 0) > 0 && conn.p.item.total_item_by_id(7, 1) > 0
				      && conn.p.item.total_item_by_id(7, 2) > 0) {
					conn.p.item.remove(7, 0, 1);
					conn.p.item.remove(7, 1, 1);
					conn.p.item.remove(7, 2, 1);
					updatematerial(conn, 7, new short[] {0, 1, 2});
					return true;
				}
				break;
			}
			case 11:
			case 12:
			case 13:
			case 14: {
				if (tem == 0) {
					conn.p.update_vang(-8000);
				} else {
					conn.p.update_ngoc(-7);
				}
				if (conn.p.item.total_item_by_id(7, 0) > 0 && conn.p.item.total_item_by_id(7, 1) > 0
				      && conn.p.item.total_item_by_id(7, 2) > 0 && conn.p.item.total_item_by_id(7, 3) > 0) {
					conn.p.item.remove(7, 0, 1);
					conn.p.item.remove(7, 1, 1);
					conn.p.item.remove(7, 2, 1);
					conn.p.item.remove(7, 3, 1);
					updatematerial(conn, 7, new short[] {0, 1, 2, 3});
					return true;
				}
				break;
			}
		}
		return false;
	}

	private static void updatematerial(Session conn, int b, short[] id) throws IOException {
		conn.p.item.char_inventory(7);
		Message m = new Message(16);
		for (int i = 0; i < id.length; i++) {
			m.writer().writeByte(1);
			m.writer().writeByte(b);
			m.writer().writeLong(conn.p.get_vang());
			m.writer().writeInt(conn.p.get_ngoc());
			m.writer().writeByte(b);
			m.writer().writeShort(id[i]);
			m.writer().writeShort(conn.p.item.total_item_by_id(b, id[i]));
			m.writer().writeByte(1);
			m.writer().writeByte(0);
			conn.addmsg(m);
			m.cleanup();
		}
	}

	public synchronized static void trade_process(Session conn, Message m2) throws IOException {
		byte type = m2.reader().readByte();
		if (type == 0) {
			// if (conn.p.time_trade > System.currentTimeMillis()) {
			// Service.send_notice_nobox_white(conn,
			// "Chờ " + (conn.p.time_trade - System.currentTimeMillis()) / 1000 + "s nữa");
			// return;
			// }
			// conn.p.time_trade = System.currentTimeMillis() + 1000L * 15;
		}
		switch (type) {
			case 0: {
				Player p0 = Map.get_player_by_name(m2.reader().readUTF());
				if (p0 != null) {
					if (p0.list_item_trade == null) {
						Message m = new Message(36);
						m.writer().writeByte(0);
						m.writer().writeUTF(conn.p.name);
						p0.conn.addmsg(m);
						m.cleanup();
					} else {
						Service.send_notice_box(conn, "Đối phương đang có giao dịch");
					}
				} else {
					Service.send_notice_box(conn, "Xảy ra lỗi");
				}
				break;
			}
			case 1: {
				Message m = new Message(36);
				m.writer().writeByte(1);
				Player p0 = Map.get_player_by_name(m2.reader().readUTF());
				if (p0 == null) {
					return;
				}
				m.writer().writeUTF(p0.name);
				conn.p.name_trade = p0.name;
				p0.name_trade = conn.p.name;
				conn.addmsg(m);
				m.cleanup();
				//
				m = new Message(36);
				m.writer().writeByte(1);
				m.writer().writeUTF(conn.p.name);
				p0.conn.addmsg(m);
				m.cleanup();
				p0.list_item_trade = new short[9];
				conn.p.list_item_trade = new short[9];
				for (int i = 0; i < conn.p.list_item_trade.length; i++) {
					conn.p.list_item_trade[i] = -1;
					p0.list_item_trade[i] = -1;
				}
				break;
			}
			case 2: {
				Player p0 = Map.get_player_by_name(conn.p.name_trade);
				if (p0 == null) {
					return;
				}
				int index_item_trade = -1;
				for (int i = 0; i < conn.p.list_item_trade.length; i++) {
					if (conn.p.list_item_trade[i] == -1) {
						index_item_trade = i;
						break;
					}
				}
				if (index_item_trade == -1) {
					Service.send_notice_box(conn, "Không thể thêm đồ");
					return;
				}
				byte typeit = m2.reader().readByte();
				short idit = m2.reader().readShort();
				switch (typeit) {
					case 3: {
						conn.p.list_item_trade[index_item_trade] = idit;
						Message m = new Message(36);
						m.writer().writeByte(2);
						m.writer().writeByte(1); // index
						m.writer().writeByte(typeit);
						m.writer().writeShort(-1);
						Item3 it3 = conn.p.item.bag3[idit];
						m.writer().writeShort(it3.icon);
						m.writer().writeUTF(it3.name);
						m.writer().writeByte(it3.color);
						m.writer().writeByte(conn.p.clazz);
						m.writer().writeShort(it3.level);
						m.writer().writeByte(it3.tier); // tier
						m.writer().writeByte(it3.op.size());
						for (int i = 0; i < it3.op.size(); i++) {
							m.writer().writeByte(it3.op.get(i).id);
							m.writer().writeInt(it3.op.get(i).getParam(it3.tier));
						}
						p0.conn.addmsg(m);
						m.cleanup();
						break;
					}
				}
				break;
			}
			case 3: {
				byte typeit = m2.reader().readByte();
				short idit = m2.reader().readShort();
				switch (typeit) {
					case 3: {
						for (Short itbuffer : conn.p.list_item_trade) {
							if (itbuffer > -1) {
								Message m = new Message(36);
								m.writer().writeByte(3);
								m.writer().writeByte(1); // index
								m.writer().writeByte(typeit);
								m.writer().writeShort(-1);
								Item3 it3 = conn.p.item.bag3[itbuffer];
								m.writer().writeShort(it3.icon);
								m.writer().writeUTF(it3.name);
								m.writer().writeByte(it3.color);
								m.writer().writeByte(conn.p.clazz);
								m.writer().writeShort(it3.level);
								m.writer().writeByte(it3.tier); // tier
								m.writer().writeByte(it3.op.size());
								for (int i = 0; i < it3.op.size(); i++) {
									m.writer().writeByte(it3.op.get(i).id);
									m.writer().writeInt(it3.op.get(i).getParam(it3.tier));
								}
								Player p0 = Map.get_player_by_name(conn.p.name_trade);
								if (p0 == null) {
									return;
								}
								p0.conn.addmsg(m);
								m.cleanup();
							}
						}
						for (int i = 0; i < conn.p.list_item_trade.length; i++) {
							if (conn.p.list_item_trade[i] == idit) {
								conn.p.list_item_trade[i] = -1;
								break;
							}
						}
						for (Short itbuffer : conn.p.list_item_trade) {
							if (itbuffer > -1) {
								Message m = new Message(36);
								m.writer().writeByte(2);
								m.writer().writeByte(1); // index
								m.writer().writeByte(typeit);
								m.writer().writeShort(-1);
								Item3 it3 = conn.p.item.bag3[itbuffer];
								m.writer().writeShort(it3.icon);
								m.writer().writeUTF(it3.name);
								m.writer().writeByte(it3.color);
								m.writer().writeByte(conn.p.clazz);
								m.writer().writeShort(it3.level);
								m.writer().writeByte(it3.tier); // tier
								m.writer().writeByte(it3.op.size());
								for (int i = 0; i < it3.op.size(); i++) {
									m.writer().writeByte(it3.op.get(i).id);
									m.writer().writeInt(it3.op.get(i).getParam(it3.tier));
								}
								Player p0 = Map.get_player_by_name(conn.p.name_trade);
								if (p0 == null) {
									return;
								}
								p0.conn.addmsg(m);
								m.cleanup();
							}
						}
						break;
					}
				}
				break;
			}
			case 4: {
				conn.p.lock_trade = true;
				Player p0 = Map.get_player_by_name(conn.p.name_trade);
				if (p0 == null) {
					return;
				}
				Service.send_notice_nobox_white(p0.conn, (conn.p.name + " khóa giao dịch!"));
				if (conn.p.lock_trade && p0.lock_trade) {
					Message m = new Message(36);
					m.writer().writeByte(4);
					m.writer().writeByte(2);
					p0.conn.addmsg(m);
					conn.addmsg(m);
					m.cleanup();
				}
				break;
			}
			case 5: {
				conn.p.accept_trade = true;
				Player p0 = Map.get_player_by_name(conn.p.name_trade);
				if (p0 == null) {
					return;
				}
				Service.send_notice_nobox_white(p0.conn, (conn.p.name + " xác nhận giao dịch!"));
				if (conn.p.accept_trade && p0.accept_trade) {
					//
					if (conn.p.money_trade > 0) {
						conn.p.update_vang(-conn.p.money_trade);
						p0.update_vang(conn.p.money_trade);
						Log.gI().add_log(p0.name,
						      "giao dịch với " + conn.p.name + " nhận " + Util.number_format(conn.p.money_trade) + " vàng");
						conn.p.money_trade = 0;
					}
					if (p0.money_trade > 0) {
						conn.p.update_vang(p0.money_trade);
						p0.update_vang(-p0.money_trade);
						Log.gI().add_log(conn.p.name,
						      "giao dịch với " + p0.name + " nhận " + Util.number_format(p0.money_trade) + " vàng");
						p0.money_trade = 0;
					}
					// trade item p1
					List<Item3> itembag3buffer = new ArrayList<>();
					for (short itbuffer : conn.p.list_item_trade) {
						if (itbuffer > -1) {
							itembag3buffer.add(conn.p.item.bag3[itbuffer]);
						}
					}
					for (Item3 it : itembag3buffer) {
						for (int i = 0; i < p0.item.bag3.length; i++) {
							if (p0.item.bag3[i] == null) {
								p0.item.bag3[i] = it;
								break;
							}
						}
					}
					for (short itbuffer : conn.p.list_item_trade) {
						if (itbuffer > -1) {
							conn.p.item.bag3[itbuffer] = null;
						}
					}
					// trade item p2
					itembag3buffer.clear();
					itembag3buffer = new ArrayList<>();
					for (short itbuffer : p0.list_item_trade) {
						if (itbuffer > -1) {
							itembag3buffer.add(p0.item.bag3[itbuffer]);
						}
					}
					for (Item3 it : itembag3buffer) {
						for (int i = 0; i < conn.p.item.bag3.length; i++) {
							if (conn.p.item.bag3[i] == null) {
								conn.p.item.bag3[i] = it;
								break;
							}
						}
					}
					for (short itbuffer : p0.list_item_trade) {
						if (itbuffer > -1) {
							p0.item.bag3[itbuffer] = null;
						}
					}
					p0.item.char_inventory(4);
					conn.p.item.char_inventory(4);
					p0.item.char_inventory(7);
					conn.p.item.char_inventory(7);
					p0.item.char_inventory(3);
					conn.p.item.char_inventory(3);
					//
					Message m = new Message(36);
					m.writer().writeByte(5);
					m.writer().writeByte(2);
					p0.conn.addmsg(m);
					conn.addmsg(m);
					m.cleanup();
					//
					conn.p.name_trade = "";
					p0.name_trade = "";
					conn.p.lock_trade = false;
					p0.lock_trade = false;
					conn.p.money_trade = 0;
					p0.money_trade = 0;
					conn.p.accept_trade = false;
					p0.accept_trade = false;
					conn.p.list_item_trade = null;
					p0.list_item_trade = null;
				} else {
					Message m = new Message(36);
					m.writer().writeByte(5);
					m.writer().writeByte(1);
					p0.conn.addmsg(m);
					m.cleanup();
					m = new Message(36);
					m.writer().writeByte(5);
					m.writer().writeByte(0);
					conn.addmsg(m);
					m.cleanup();
				}
				break;
			}
			case 6: {
				Message m = new Message(36);
				m.writer().writeByte(6);
				conn.addmsg(m);
				Player p0 = Map.get_player_by_name(conn.p.name_trade);
				if (p0 == null) {
					return;
				}
				p0.conn.addmsg(m);
				m.cleanup();
				conn.p.name_trade = "";
				p0.name_trade = "";
				conn.p.lock_trade = false;
				p0.lock_trade = false;
				conn.p.money_trade = 0;
				p0.money_trade = 0;
				conn.p.accept_trade = false;
				p0.accept_trade = false;
				conn.p.list_item_trade = null;
				p0.list_item_trade = null;
				break;
			}
			case 7: {
				int money = m2.reader().readInt();
				if (money < 0) {
					money = 0;
				}
				if (money > conn.p.get_vang()) {
					money = (int) conn.p.get_vang();
				}
				if (money > 1_000_000_000) {
					money = 1_000_000_000;
				}
				conn.p.money_trade = money;
				Message m = new Message(36);
				m.writer().writeByte(7);
				m.writer().writeInt(money);
				Player p0 = Map.get_player_by_name(conn.p.name_trade);
				if (p0 == null) {
					return;
				}
				p0.conn.addmsg(m);
				m.cleanup();
				break;
			}
			case 9: {
				String text = m2.reader().readUTF();
				Message m = new Message(36);
				m.writer().writeByte(9);
				m.writer().writeUTF(text);
				Player p0 = Map.get_player_by_name(conn.p.name_trade);
				if (p0 == null) {
					return;
				}
				p0.conn.addmsg(m);
				m.cleanup();
				break;
			}
			default: {
				Service.send_notice_box(conn, "Chưa có chức năng");
				break;
			}
		}
	}

	public static void replace_item_process(Player p, Message m2) throws IOException {
		byte type = m2.reader().readByte();
		short id = m2.reader().readShort();
		if (type == 0) {
			if (p.item.bag3[id] != null && !check_item_can_rebuild(p.item.bag3[id].type)) {
				Service.send_notice_box(p.conn, "Không hợp lệ!");
				return;
			}
			if (p.item_replace == -1) {
				if (p.item.bag3[id] != null && p.item.bag3[id].tier < 6) {
					Service.send_notice_box(p.conn, "Bỏ vật phẩm trên +6 vào trước");
					return;
				}
				p.item_replace = id;
			} else if (p.item_replace2 == -1) {
				if (p.item.bag3[id].tier > 0) {
					Service.send_notice_box(p.conn, "Chỉ có thể bỏ vật phẩm +0 vào");
					return;
				}
				if (p.item.bag3[p.item_replace].type != p.item.bag3[id].type) {
					Service.send_notice_box(p.conn, "Chỉ có thể chuyển hóa trang bị cùng loại");
					return;
				}
				if (p.item.bag3[p.item_replace].level > p.item.bag3[id].level) {
					Service.send_notice_box(p.conn, "Chỉ có thể chuyển hóa trang bị level cao hơn");
					return;
				}
				p.item_replace2 = id;
			} else {
				Service.send_notice_box(p.conn, "Lỗi, hãy thử lại");
				return;
			}
			Message m = new Message(73);
			m.writer().writeByte(0);
			m.writer().writeShort(id);
			if (p.item_replace2 == -1) {
				m.writer().writeByte(1);
			} else {
				m.writer().writeByte(0);
			}
			p.conn.addmsg(m);
			m.cleanup();
		} else if (type == 1) {
			if (p.item.bag3[p.item_replace].tier < p.item.bag3[p.item_replace2].tier) {
				Service.send_notice_box(p.conn, "Đã chuyển hóa xong rồi!!! ấn nữa muốn về +0 hết không?");
				return;
			}
			if (!p.item.bag3[p.item_replace2].islock) {
				p.item.bag3[p.item_replace2].islock = true;
				p.item.bag3[p.item_replace2].name =
				      ItemTemplate3.item.get(p.item.bag3[p.item_replace2].id).getName() + " [Khóa]";
				p.item.char_inventory(4);
				p.item.char_inventory(7);
				p.item.char_inventory(3);
			}
			int fee = 100 * p.item.bag3[p.item_replace].tier;
			Service.send_box_input_yesno(p.conn, 122,
			      "Chuyển hóa sẽ mất phí " + fee + " ngọc, bạn có chắc chắn muốn chuyển hóa?");
		}
	}

	public static void Create_Medal(Session conn, Message m2) throws IOException {
		byte type = m2.reader().readByte();
		short id = -1;
		@SuppressWarnings("unused")
		byte tem = -1;
		try {
			id = m2.reader().readShort();
			tem = m2.reader().readByte();
		} catch (IOException e) {
		}
		// System.out.println(type);
		// System.out.println(id);
		// System.out.println(tem);
		if (id != -1) {
			byte type_item = ItemTemplate7.item.get(id).getType();
			if (type_item != 54) {
				Service.send_notice_box(conn, "Vật phẩm không hợp lệ!");
				return;
			}
		}
		switch (type) {
			case 0: { // fusion
				if (id >= 246 && id < 346) {
					Service.send_notice_box(conn, "Cấp vật phẩm đã đạt tối đa!");
					return;
				}
				int quant = conn.p.item.total_item_by_id(7, id);
				if (quant < 5) {
					Service.send_notice_box(conn, "Chưa đủ nguyên liệu để hợp thành tối thiểu 1 vật phẩm!");
				} else {
					Message m = new Message(-105);
					m.writer().writeByte(0);
					m.writer().writeByte(5);
					m.writer().writeShort(id);
					conn.addmsg(m);
					m.cleanup();
				}
				break;
			}
			case 2: {
				if (id >= 246 && id < 346) {
					Service.send_notice_box(conn, "Cấp vật phẩm đã đạt tối đa!");
					return;
				}
				String name = ItemTemplate7.item.get(id).getName();
				Service.send_box_input_text(conn, 7, name, new String[] {"Số lượng (5000 vàng/1)"});
				if (conn.p.fusion_material_medal_id == -1) {
					conn.p.fusion_material_medal_id = id;
				}
				break;
			}
			case 3: { // create
				if (conn.p.id_medal_is_created != -1) {
					if (conn.p.item.total_item_by_id(7,
					      conn.p.medal_create_material[0 + 5 * conn.p.id_medal_is_created]) < 1) {
						Service.send_notice_box(conn, "Chưa đủ nguyên liệu!");
						return;
					} else if (conn.p.item.total_item_by_id(7,
					      conn.p.medal_create_material[1 + 5 * conn.p.id_medal_is_created]) < 1) {
						Service.send_notice_box(conn, "Chưa đủ nguyên liệu!");
						return;
					} else if (conn.p.item.total_item_by_id(7,
					      conn.p.medal_create_material[2 + 5 * conn.p.id_medal_is_created]) < 1) {
						Service.send_notice_box(conn, "Chưa đủ nguyên liệu!");
						return;
					} else if (conn.p.item.total_item_by_id(7,
					      conn.p.medal_create_material[3 + 5 * conn.p.id_medal_is_created]) < 1) {
						Service.send_notice_box(conn, "Chưa đủ nguyên liệu!");
						return;
					} else if (conn.p.item.total_item_by_id(7,
					      conn.p.medal_create_material[4 + 5 * conn.p.id_medal_is_created]) < 1) {
						Service.send_notice_box(conn, "Chưa đủ nguyên liệu!");
						return;
					}
					int bound1 = 0 + 5 * conn.p.id_medal_is_created;
					int bound2 = 5 + 5 * conn.p.id_medal_is_created;
					for (int i = bound1; i < bound2; i++) {
						conn.p.item.remove(7, conn.p.medal_create_material[i], 1);
					}
					// change material
					int material_type_1st = Util.random(0, 7);
					int material_type_2nd = Util.random(0, 7);
					while (material_type_1st == material_type_2nd) {
						material_type_2nd = Util.random(0, 7);
					}
					conn.p.medal_create_material[bound1] =
					      (short) (Medal_Material.m_white[material_type_1st][Util.random(0, 10)] + 200);
					conn.p.medal_create_material[bound1 + 1] =
					      (short) (Medal_Material.m_white[material_type_2nd][Util.random(0, 10)] + 200);
					conn.p.medal_create_material[bound1 + 2] = (short) (Medal_Material.m_blue[Util.random(0, 10)] + 200);
					conn.p.medal_create_material[bound1 + 3] = (short) (Medal_Material.m_yellow[Util.random(0, 10)] + 200);
					conn.p.medal_create_material[bound1 + 4] = (short) (Medal_Material.m_violet[Util.random(0, 10)] + 200);
					// for (int i = (bound1 + 1); i < bound2; i++) {
					// boolean check = true;
					// do {
					// conn.p.medal_create_material[i] = (short) Util.random(246, 346);
					// for (int j = (i - 1); j >= bound1; j--) {
					// if (conn.p.medal_create_material[i] == conn.p.medal_create_material[j]) {
					// check = false;
					// break;
					// }
					// }
					// } while (!check);
					// }
					//
					Message m = new Message(-105);
					m.writer().writeByte(3);
					m.writer().writeByte(3);
					ItemTemplate3 temp = ItemTemplate3.item.get(conn.p.id_medal_is_created + 4587);
					byte color_ = (byte) Util.random(0, 5);
					m.writer().writeUTF("Chúc mừng bạn nhận được " + temp.getName());
					m.writer().writeByte(3);
					m.writer().writeUTF(temp.getName());
					m.writer().writeByte(temp.getClazz());
					m.writer().writeShort(temp.getId());
					m.writer().writeByte(temp.getType());
					m.writer().writeShort(temp.getIcon());
					m.writer().writeByte(0); // tier
					m.writer().writeShort(1); // level required
					m.writer().writeByte(color_); // color
					m.writer().writeByte(0); // can sell
					m.writer().writeByte(0); // can trade
					m.writer().writeByte(temp.getOp().size());
					for (int i = 0; i < temp.getOp().size(); i++) {
						m.writer().writeByte(temp.getOp().get(i).id);
						m.writer().writeInt(temp.getOp().get(i).getParam(0));
					}
					m.writer().writeInt(0); // time use
					m.writer().writeByte(0);
					m.writer().writeByte(0);
					m.writer().writeByte(0);
					conn.addmsg(m);
					m.cleanup();
					//
					Item3 itbag = new Item3();
					itbag.id = temp.getId();
					itbag.clazz = temp.getClazz();
					itbag.type = temp.getType();
					itbag.level = 1; // level required
					itbag.icon = temp.getIcon();
					itbag.color = color_;
					itbag.part = temp.getPart();
					itbag.islock = false;
					itbag.name = temp.getName();
					itbag.tier = 0;
					//
					List<Option> opnew = new ArrayList<>();
					Option op = new Option(1, 1);
					op.id = 96;
					if (color_ == 0) {
						op.setParam(Util.random(1, 4));
					} else if (color_ == 1) {
						op.setParam(Util.random(1, 4));
					} else if (color_ == 2) {
						op.setParam(Util.random(4, 6));
					} else if (color_ == 3) {
						op.setParam(Util.random(6, 8));
					} else if (color_ == 4) {
						op.setParam(Util.random(8, 10));
					}
					opnew.add(op);
					//
					itbag.op = new ArrayList<>();
					itbag.op.addAll(opnew);
					itbag.time_use = 0;
					conn.p.item.add_item_bag3(itbag);
					conn.p.item.char_inventory(4);
					conn.p.item.char_inventory(7);
					conn.p.item.char_inventory(3);
					conn.p.id_medal_is_created = -1;
				} else {
					Service.send_notice_box(conn, "Đã tạo thành công rồi còn ấn nữa muốn bị mất đi k?");
				}
				break;
			}
		}
	}

	public static void Wings_Process(Session conn, Message m2) throws IOException {
		// Service.send_notice_box(conn, "Đang phát triển");
		byte type = m2.reader().readByte();
		int wing = m2.reader().readInt();
		short id = m2.reader().readShort();
		// System.out.println(type);
		// System.out.println(wing);
		// System.out.println(id);
		if (type == 1) { // create
			if (conn.p.item.get_bag_able() < 1) {
				Service.send_notice_box(conn, "Hành trang không đủ chỗ!");
				return;
			}
			if (conn.p.item.total_item_by_id(7, 8) < 80) {
				Service.send_notice_box(conn, "Không đủ " + ItemTemplate7.item.get(8).getName() + "!");
				return;
			} else if (conn.p.item.total_item_by_id(7, 9) < 60) {
				Service.send_notice_box(conn, "Không đủ " + ItemTemplate7.item.get(9).getName() + "!");
				return;
			} else if (conn.p.item.total_item_by_id(7, 10) < 40) {
				Service.send_notice_box(conn, "Không đủ " + ItemTemplate7.item.get(10).getName() + "!");
				return;
			} else if (conn.p.item.total_item_by_id(7, 11) < 20) {
				Service.send_notice_box(conn, "Không đủ " + ItemTemplate7.item.get(11).getName() + "!");
				return;
			} else if (conn.p.item.total_item_by_id(7, 0) < 100) {
				Service.send_notice_box(conn, "Không đủ " + ItemTemplate7.item.get(0).getName() + "!");
				return;
			} else if (conn.p.item.total_item_by_id(7, 3) < 20) {
				Service.send_notice_box(conn, "Không đủ " + ItemTemplate7.item.get(3).getName() + "!");
				return;
			}
			if (conn.p.get_vang() < 200_000) {
				Service.send_notice_box(conn, "Không đủ vàng!");
				return;
			}
			//
			conn.p.update_vang(-200_000);
			conn.p.item.remove(7, 8, 80);
			conn.p.item.remove(7, 9, 60);
			conn.p.item.remove(7, 10, 40);
			conn.p.item.remove(7, 11, 20);
			conn.p.item.remove(7, 0, 100);
			conn.p.item.remove(7, 3, 20);
			conn.p.item.char_inventory(7);
			//
			ItemTemplate3 temp = ItemTemplate3.item.get(wing);
			Item3 itbag = new Item3();
			itbag.id = (short) wing;
			itbag.name = temp.getName();
			itbag.clazz = temp.getClazz();
			itbag.type = temp.getType();
			itbag.level = 60;
			itbag.icon = temp.getIcon();
			//
			itbag.op = new ArrayList<>();
			List<Option> op_new = new ArrayList<>();
			switch (wing) {
				case 2880: { // canh chien than
					op_new.add(new Option(25, Util.random(1, 7)));
					break;
				}
				case 2887: { // canh rong
					op_new.add(new Option(23, Util.random(1, 6)));
					break;
				}
				case 2894: { // canh quai thu
					op_new.add(new Option(23, Util.random(1, 6)));
					break;
				}
				case 2901: { // canh yeu tinh
					op_new.add(new Option(24, Util.random(1, 7)));
					break;
				}
				case 2908: { // canh phuong hoang
					op_new.add(new Option(26, Util.random(1, 6)));
					break;
				}
				case 2915: { // canh bang tuyet
					op_new.add(new Option(25, Util.random(1, 7)));
					break;
				}
				case 2922: { // canh hong hac
					op_new.add(new Option(24, Util.random(1, 7)));
					break;
				}
				case 2929: { // canh chuon chuon
					op_new.add(new Option(26, Util.random(1, 6)));
					break;
				}
			}
			op_new.add(new Option(41,
			      ((3 > Util.random(0, 150)) ? 4 : ((10 > Util.random(0, 150)) ? 3 : ((45 > Util.random(0, 120)) ? 2 : 1)))
			            * 100));
			op_new.add(new Option(42,
			      ((3 > Util.random(0, 150)) ? 8 : ((10 > Util.random(0, 150)) ? 7 : ((45 > Util.random(0, 120)) ? 6 : 5)))
			            * 1000));
			//
			itbag.op.addAll(op_new);
			//
			itbag.color = 5;
			itbag.part = temp.getPart();
			itbag.tier = 0;
			itbag.islock = true;
			itbag.time_use = 0;
			conn.p.item.add_item_bag3(itbag);
			conn.p.item.char_inventory(4);
			conn.p.item.char_inventory(7);
			conn.p.item.char_inventory(3);
			for (int i = (conn.p.item.bag3.length - 1); i >= 0; i--) {
				if (conn.p.item.bag3[i] != null && conn.p.item.bag3[i].id == wing) {
					Message m = new Message(77);
					m.writer().writeByte(5);
					m.writer().writeUTF("Chúc mừng bạn nhận được " + temp.getName());
					m.writer().writeShort(i);
					conn.addmsg(m);
					m.cleanup();
					break;
				}
			}
                        Log.gI().add_log(conn.p.name, "Tạo cánh " + temp.getName());
		} else if (type == 2) {
			if (!conn.p.is_create_wing) {
				Item3 it = conn.p.item.bag3[id];
				if (it != null) {
					if (it.tier > 29) {
						Service.send_notice_box(conn, "Đã nâng cấp tối đa!");
						return;
					}
					if (it.time_use > 0) {
						long time_ = it.time_use - System.currentTimeMillis();
						time_ /= 60_000;
						Service.send_notice_nobox_white(conn, "Sử dụng sau " + ((time_ > 0) ? time_ : 1) + "p nữa");
						return;
					}
					Message m = new Message(77);
					m.writer().writeByte(2);
					m.writer().writeShort(id);
					conn.addmsg(m);
					m.cleanup();
					//
					m = new Message(77);
					m.writer().writeByte(0);
					m.writer().writeInt(it.id);
					m.writer().writeUTF((it.name + " +" + (it.tier + 1)));
					m.writer().writeInt(GameSrc.wing_upgrade_material_gold[it.tier]);
					m.writer().writeShort(60);
					m.writer().writeInt(GameSrc.wing_upgrade_material_time[it.tier] * 60);
					m.writer().writeByte(6);
					m.writer().writeShort(8);
					m.writer().writeShort(GameSrc.wing_upgrade_material_long_khuc_xuong[it.tier]);
					m.writer().writeShort(9);
					m.writer().writeShort(GameSrc.wing_upgrade_material_long_khuc_xuong[it.tier]);
					m.writer().writeShort(10);
					m.writer().writeShort(GameSrc.wing_upgrade_material_long_khuc_xuong[it.tier]);
					m.writer().writeShort(11);
					m.writer().writeShort(GameSrc.wing_upgrade_material_long_khuc_xuong[it.tier]);
					m.writer().writeShort(0);
					m.writer().writeShort(GameSrc.wing_upgrade_material_da_cuong_hoa[it.tier]);
					m.writer().writeShort(3);
					m.writer().writeShort(GameSrc.wing_upgrade_material_kim_loai[it.tier]);
					conn.addmsg(m);
					m.cleanup();
				}
			} else {
				Service.send_notice_box(conn, "Bạn đang tính tạo cánh mới, quên à?");
			}
		} else if (type == 3) {
			if (!conn.p.is_create_wing) {
				Item3 it = conn.p.item.bag3[id];
				if (it != null) {
					if (it.time_use != 0) {
						return;
					}
					// check material
					if (conn.p.item.total_item_by_id(7, 8) < GameSrc.wing_upgrade_material_long_khuc_xuong[it.tier]) {
						Service.send_notice_box(conn, "Không đủ " + ItemTemplate7.item.get(8).getName() + "!");
						return;
					} else if (conn.p.item.total_item_by_id(7, 9) < GameSrc.wing_upgrade_material_long_khuc_xuong[it.tier]) {
						Service.send_notice_box(conn, "Không đủ " + ItemTemplate7.item.get(9).getName() + "!");
						return;
					} else if (conn.p.item.total_item_by_id(7,
					      10) < GameSrc.wing_upgrade_material_long_khuc_xuong[it.tier]) {
						Service.send_notice_box(conn, "Không đủ " + ItemTemplate7.item.get(10).getName() + "!");
						return;
					} else if (conn.p.item.total_item_by_id(7,
					      11) < GameSrc.wing_upgrade_material_long_khuc_xuong[it.tier]) {
						Service.send_notice_box(conn, "Không đủ " + ItemTemplate7.item.get(11).getName() + "!");
						return;
					} else if (conn.p.item.total_item_by_id(7, 0) < GameSrc.wing_upgrade_material_da_cuong_hoa[it.tier]) {
						Service.send_notice_box(conn, "Không đủ " + ItemTemplate7.item.get(0).getName() + "!");
						return;
					} else if (conn.p.item.total_item_by_id(7, 3) < GameSrc.wing_upgrade_material_kim_loai[it.tier]) {
						Service.send_notice_box(conn, "Không đủ " + ItemTemplate7.item.get(3).getName() + "!");
						return;
					}
					if (conn.p.get_vang() < GameSrc.wing_upgrade_material_gold[it.tier]) {
						Service.send_notice_box(conn, "Không đủ vàng!");
						return;
					}
					//
					conn.p.update_vang(-GameSrc.wing_upgrade_material_gold[it.tier]);
					conn.p.item.remove(7, 8, GameSrc.wing_upgrade_material_long_khuc_xuong[it.tier]);
					conn.p.item.remove(7, 9, GameSrc.wing_upgrade_material_long_khuc_xuong[it.tier]);
					conn.p.item.remove(7, 10, GameSrc.wing_upgrade_material_long_khuc_xuong[it.tier]);
					conn.p.item.remove(7, 11, GameSrc.wing_upgrade_material_long_khuc_xuong[it.tier]);
					conn.p.item.remove(7, 0, GameSrc.wing_upgrade_material_da_cuong_hoa[it.tier]);
					conn.p.item.remove(7, 3, GameSrc.wing_upgrade_material_kim_loai[it.tier]);
					conn.p.item.char_inventory(7);
					//
					it.tier++;
					Option op_new;
					switch (it.tier) {
						case 5: {
							it.op.add(new Option(14, Util.random(70, 100)));
							break;
						}
						case 10: {
							it.part++;
							op_new = new Option(1, 1);
							switch (it.id) {
								case 2894:
								case 2929:
								case 2908:
								case 2880: {
									op_new.id = 7;
									break;
								}
								case 2887: {
									op_new.id = 9;
									break;
								}
								case 2901: {
									op_new.id = 11;
									break;
								}
								case 2915: {
									op_new.id = 8;
									break;
								}
								case 2922: {
									op_new.id = 10;
									break;
								}
							}
							op_new.setParam(Util.random(100, 301));
							it.op.add(op_new);
							break;
						}
						case 15: {
							op_new = new Option(1, 1);
							switch (it.id) {
								case 2894:
								case 2929:
								case 2908:
								case 2887: {
									op_new.id = 28;
									break;
								}
								case 2901:
								case 2922:
								case 2880:
								case 2915: {
									op_new.id = 27;
									break;
								}
							}
							op_new.setParam(Util.random(100, 301));
							it.op.add(op_new);
							break;
						}
						case 20: {
							it.part++;
							it.op.add(new Option(15, Util.random(100, 301)));
							break;
						}
						case 25: {
							op_new = new Option(1, 1);
							switch (it.id) {
								case 2894:
								case 2887: {
									op_new.id = 33;
									break;
								}
								case 2929:
								case 2908: {
									op_new.id = 36;
									break;
								}
								case 2901:
								case 2922: {
									op_new.id = 34;
									break;
								}
								case 2880:
								case 2915: {
									op_new.id = 35;
									break;
								}
							}
							op_new.setParam(Util.random(100, 201));
							it.op.add(op_new);
							break;
						}
						case 30: {
							it.part++;
							op_new = new Option(1, 1);
							switch (it.id) {
								case 2894:
								case 2929:
								case 2908:
								case 2887: {
									op_new.id = 37;
									break;
								}
								case 2901:
								case 2922:
								case 2880:
								case 2915: {
									op_new.id = 38;
									break;
								}
							}
							op_new.setParam(1);
							it.op.add(op_new);
							break;
						}
					}
					it.time_use = System.currentTimeMillis()
					      + (((long) GameSrc.wing_upgrade_material_time[it.tier - 1]) * 3_600_000L);
					conn.p.item.char_inventory(4);
					conn.p.item.char_inventory(7);
					conn.p.item.char_inventory(3);
					Message m = new Message(77);
					m.writer().writeByte(5);
					m.writer().writeUTF(("Đã nâng cấp thành công " + it.name + " lên +" + it.tier));
					m.writer().writeShort(id);
					conn.addmsg(m);
					m.cleanup();
				}
			} else {
				Service.send_notice_box(conn, "Bạn đang tính tạo cánh mới, quên à?");
			}
		}
	}
}
