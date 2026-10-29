package map;

import event.EventManager;
import event.Event_1;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import client.Player;
import core.Manager;
import core.Util;
import io.Message;
import template.EffTemplate;
import template.ItemTemplate3;
import template.ItemTemplate4;
import template.ItemTemplate7;
import template.Medal_Material;
import template.Option;

public class LeaveItemMap {
	public static List<Short> item0x = new ArrayList<>();
	public static List<Short> item1x = new ArrayList<>();
	public static List<Short> item2x = new ArrayList<>();
	public static List<Short> item3x = new ArrayList<>();
	public static List<Short> item4x = new ArrayList<>();
	public static List<Short> item5x = new ArrayList<>();
	public static List<Short> item6x = new ArrayList<>();
	public static List<Short> item7x = new ArrayList<>();
	public static List<Short> item8x = new ArrayList<>();
	public static List<Short> item9x = new ArrayList<>();
	public static List<Short> item10x = new ArrayList<>();
	public static List<Short> item11x = new ArrayList<>();
	public static List<Short> item12x = new ArrayList<>();
	public static List<Short> item13x = new ArrayList<>();
	public static List<Short> item14x = new ArrayList<>();
	public static List<Short> item15x = new ArrayList<>();

	public static void leave_vang(Map map, Mob_in_map mob, Player p) throws IOException {
		if (mob != null) {
			int index_item_map = map.get_item_map_index_able();
			if (index_item_map > -1) {
				//
				map.item_map[index_item_map] = new ItemMap();
				map.item_map[index_item_map].id_item = -1;
				map.item_map[index_item_map].color = 0;
				int vang_drop = Util.random(mob.level * 15, mob.level * 45);
				EffTemplate ef = p.get_eff(52);
				if (ef != null) {
					vang_drop += (vang_drop * (ef.param / 100)) / 100;
				}
				map.item_map[index_item_map].quantity = (short) vang_drop;
				map.item_map[index_item_map].category = 4;
				map.item_map[index_item_map].idmaster = (short) p.id;
				map.item_map[index_item_map].time_exist = System.currentTimeMillis() + 60_000L;
				map.item_map[index_item_map].time_pick = System.currentTimeMillis() + 1_500L;
				String name = "vàng *" + map.item_map[index_item_map].quantity;
				// add in4 game scr
				Message mi = new Message(19);
				mi.writer().writeByte(4);
				mi.writer().writeShort(mob.index); // index mob die
				mi.writer().writeShort(0); // id icon (0 : vang)
				mi.writer().writeShort(index_item_map); //
				mi.writer().writeUTF(name);
				mi.writer().writeByte(0); // color
				mi.writer().writeShort(-1); // id player
				MapService.send_msg_player_inside(map, p, mi, true);
				mi.cleanup();
			}
		}
	}

	public static void leave_item_3(Map map, Mob_in_map mob, Player p) throws IOException {
		if (mob != null) {
			short id_item_can_drop = 0;
			byte color_ = 0;
			int rand_color = Util.random(0, 200);
			if (rand_color < 20) {
				color_ = 0; // Trắng (20/200 = 10%)
			} else if (rand_color < 60) {
				color_ = 1; // Xanh lá (40/200 = 20%)
			} else if (rand_color < 100) {
				color_ = 2; // Xanh lam (40/200 = 20%)
			} else if (rand_color < 160) {
				color_ = 3; // Tím (60/200 = 30%)
			} else {
				color_ = 4; // Cam (40/200 = 20%)
			}
			if (mob.color_name != 0) {
				color_ = 3;
			}
			if (mob.level >= 1 && mob.level < 10 && item0x.size() > 0) {
				id_item_can_drop = item0x.get(Util.random(0, item0x.size()));
			} else if (mob.level >= 10 && mob.level < 20 && item1x.size() > 0) {
				id_item_can_drop = item1x.get(Util.random(0, item1x.size()));
			} else if (mob.level >= 20 && mob.level < 30 && item2x.size() > 0) {
				id_item_can_drop = item2x.get(Util.random(0, item2x.size()));
			} else if (mob.level >= 30 && mob.level < 40 && item3x.size() > 0) {
				id_item_can_drop = item3x.get(Util.random(0, item3x.size()));
			} else if (mob.level >= 40 && mob.level < 50 && item4x.size() > 0) {
				id_item_can_drop = item4x.get(Util.random(0, item4x.size()));
			} else if (mob.level >= 50 && mob.level < 60 && item5x.size() > 0) {
				id_item_can_drop = item5x.get(Util.random(0, item5x.size()));
			} else if (mob.level >= 60 && mob.level < 70 && item6x.size() > 0) {
				id_item_can_drop = item6x.get(Util.random(0, item6x.size()));
			} else if (mob.level >= 70 && mob.level < 80 && item7x.size() > 0) {
				id_item_can_drop = item7x.get(Util.random(0, item7x.size()));
			} else if (mob.level >= 80 && mob.level < 90 && item8x.size() > 0) {
				id_item_can_drop = item8x.get(Util.random(0, item8x.size()));
			} else if (mob.level >= 90 && mob.level < 100 && item9x.size() > 0) {
				id_item_can_drop = item9x.get(Util.random(0, item9x.size()));
			} else if (mob.level >= 100 && mob.level < 110 && item10x.size() > 0) {
				id_item_can_drop = item10x.get(Util.random(0, item10x.size()));
			} else if (mob.level >= 110 && mob.level < 120 && item11x.size() > 0) {
				id_item_can_drop = item11x.get(Util.random(0, item11x.size()));
			} else if (mob.level >= 120 && mob.level < 130 && item12x.size() > 0) {
				id_item_can_drop = item12x.get(Util.random(0, item12x.size()));
			} else if (mob.level >= 130 && item13x.size() > 0) {
				id_item_can_drop = item13x.get(Util.random(0, item13x.size()));
			}
			String name = ItemTemplate3.item.get(id_item_can_drop).getName();
			short index_real = 0;
			if (id_item_can_drop < 20) {
				for (int i = 0; i < 20; i++) {
					if (ItemTemplate3.item.get(i).getName().equals(name) && ItemTemplate3.item.get(i).getColor() == color_) {
						index_real = (short) i;
						break;
					}
				}
			} else {
				for (int i = id_item_can_drop - 5; i < id_item_can_drop + 5; i++) {
					if (ItemTemplate3.item.get(i).getName().equals(name) && ItemTemplate3.item.get(i).getColor() == color_) {
						index_real = (short) i;
						break;
					}
				}
			}
			//
			leave_item_by_type3(map, index_real, color_, p, name, mob.index);
		}
	}

	private static void leave_item_by_type3(Map map, int index_real, int color_, Player p_master, String name, int index)
	      throws IOException {
		int index_item_map = map.get_item_map_index_able();
		if (index_item_map > -1) {
			//
			map.item_map[index_item_map] = new ItemMap();
			map.item_map[index_item_map].id_item = (short) index_real;
			map.item_map[index_item_map].color = (byte) color_;
			map.item_map[index_item_map].quantity = 1;
			map.item_map[index_item_map].category = 3;
			map.item_map[index_item_map].idmaster = (short) p_master.id;
			List<Option> opnew = new ArrayList<Option>();
			ItemTemplate3 it_template = ItemTemplate3.item.get(index_real);
			int item_level = (it_template != null) ? it_template.getLevel() : 1;
			for (Option op_old : it_template.getOp()) {
				Option temp = new Option(1, 1);
				temp.id = op_old.id;
				int base_val = op_old.getParam(0);
				if (temp.id != 37 && temp.id != 38 && temp.id != 71 && temp.id != 62) {
					if (base_val <= 0) {
						temp.setParam(0);
					} else {
						// Dao động trong khoảng 90% đồ mở rương -> max chỉ số template
						double min_val = (base_val * 90.0) / 100.0;
						double max_val = (double) base_val;
						double raw_val = min_val + (Util.random(0, 10000) / 10000.0) * (max_val - min_val);
						int final_val = round_option_drop(temp.id, raw_val, base_val, item_level);
						if (final_val < 1 && base_val > 0) {
							final_val = 1;
						}
						temp.setParam(final_val);
					}
				} else {
					temp.setParam(base_val);
				}
				opnew.add(temp);
			}
			map.item_map[index_item_map].op = new ArrayList<>();
			map.item_map[index_item_map].op.addAll(opnew);
			map.item_map[index_item_map].time_exist = System.currentTimeMillis() + 60_000L;
			map.item_map[index_item_map].time_pick = System.currentTimeMillis() + 1_500L;
			// add in4 game scr
			Message mi = new Message(19);
			mi.writer().writeByte(3);
			mi.writer().writeShort(index); // index mob die
			mi.writer().writeShort(ItemTemplate3.item.get(map.item_map[index_item_map].id_item).getIcon());
			mi.writer().writeShort(index_item_map); //
			mi.writer().writeUTF(name);
			mi.writer().writeByte(color_); // color
			mi.writer().writeShort(-1); // id player
			MapService.send_msg_player_inside(map, p_master, mi, true);
			mi.cleanup();
		}
	}

	private static int round_option_drop(byte op_id, double raw_val, int base_val, int level) {
		// Category D: Special Options
		if (op_id == 37 || op_id == 38 || op_id == 71 || op_id == 62) {
			return base_val;
		}

		// Category A1: Potential stats (+1 điểm mỗi cấp tier: Op 23..26)
		if (op_id >= 23 && op_id <= 26) {
			return (int) Math.round(raw_val);
		}

		// Category A2: Percent stats (+1% mỗi cấp tier: Op 7..13, Op 15, Op 27, Op 28)
		// Trong server, 1% = 100 điểm -> làm tròn đến hàng đơn vị % (bội số 100)
		if ((op_id >= 7 && op_id <= 13) || op_id == 15 || op_id == 27 || op_id == 28) {
			return (int) (Math.round(raw_val / 100.0) * 100);
		}

		// Category B: Percent stats (+0.2% mỗi cấp tier: Op 16..22, Op 29..36, Op 41)
		if ((op_id >= 16 && op_id <= 22) || (op_id >= 29 && op_id <= 36) || op_id == 41) {
			// Đồ Level 11x (có phân vị .4 -> 5.4% = 540 điểm)
			if (level >= 110 && level < 120) {
				int base_hundred = ((int) raw_val / 100) * 100;
				double remainder = raw_val - base_hundred;
				if (base_val % 100 == 40 || base_val % 100 == 50) {
					// Ngưỡng phân vị .2 (ví dụ 5.25 -> 5.4, 5.15 -> 5.0)
					if (remainder >= 20.0) {
						return base_hundred + 40;
					} else {
						return base_hundred;
					}
				} else {
					return (int) (Math.round(raw_val / 100.0) * 100);
				}
			}
			// Đồ Level 12x (có phân vị .6 -> 5.6% = 560 điểm)
			else if (level >= 120 && level < 130) {
				int base_hundred = ((int) raw_val / 100) * 100;
				double remainder = raw_val - base_hundred;
				if (base_val % 100 == 60 || base_val % 100 == 75) {
					// Ngưỡng phân vị .3 (ví dụ 5.35 -> 5.6, 5.25 -> 5.0)
					if (remainder >= 30.0) {
						return base_hundred + 60;
					} else {
						return base_hundred;
					}
				} else {
					return (int) (Math.round(raw_val / 100.0) * 100);
				}
			}
			// Các level khác: làm tròn đến hàng đơn vị % (bội số 100)
			else {
				return (int) (Math.round(raw_val / 100.0) * 100);
			}
		}

		// Category C: Base points (Op 0..6: Sát thương, Op 14: Giáp, Op 40: Sát thương cơ bản)
		// Làm tròn đến hàng đơn vị nguyên
		if ((op_id >= 0 && op_id <= 6) || op_id == 14 || op_id == 40) {
			return (int) Math.round(raw_val);
		}

		return (int) Math.round(raw_val);
	}

	public static void leave_item_4(Map map, Mob_in_map mob, Player p) throws IOException {
		if (mob != null) {
			short index_real = (short) Util.random(0, 6);
                        if (mob.level > 100 && 25 > Util.random(250)) {
                            index_real = 320;
                        }
                        if (mob.level > 100 && 25 > Util.random(250)) {
                            index_real = 318;
                        }
                        if (mob.level > 100 && 25 > Util.random(250)) {
                            index_real = 319;
                        }
			//
			leave_item_by_type4(map, index_real, p, mob.index);
		}
	}

	private static void leave_item_by_type4(Map map, short index_real, Player p_master, int index) throws IOException {
		int index_item_map = map.get_item_map_index_able();
		if (index_item_map > -1) {
			//
			map.item_map[index_item_map] = new ItemMap();
			map.item_map[index_item_map].id_item = index_real;
			map.item_map[index_item_map].color = 0;
			map.item_map[index_item_map].quantity = 1;
			map.item_map[index_item_map].category = 4;
			map.item_map[index_item_map].idmaster = (short) p_master.id;
			map.item_map[index_item_map].time_exist = System.currentTimeMillis() + 60_000L;
			map.item_map[index_item_map].time_pick = System.currentTimeMillis() + 1_500L;
			// add in4 game scr
			Message mi = new Message(19);
			mi.writer().writeByte(4);
			mi.writer().writeShort(index); // id mob die
			mi.writer().writeShort(ItemTemplate4.item.get(map.item_map[index_item_map].id_item).getIcon());
			mi.writer().writeShort(index_item_map); //
			mi.writer().writeUTF(ItemTemplate4.item.get(map.item_map[index_item_map].id_item).getName());
			mi.writer().writeByte(0); // color
			mi.writer().writeShort(-1); // id player
			MapService.send_msg_player_inside(map, p_master, mi, true);
			mi.cleanup();
		}
	}

	public static void leave_item_7(Map map, Mob_in_map mob, Player p) throws IOException {
		if (mob != null) {
			short index_real = (short) Util.random(0, 4);
			//
			leave_item_by_type7(map, index_real, p, mob.index);
		}
	}

	private static void leave_item_by_type7(Map map, short index_real, Player p_master, int index) throws IOException {
		int index_item_map = map.get_item_map_index_able();
		if (index_item_map > -1) {
			//
			map.item_map[index_item_map] = new ItemMap();
			map.item_map[index_item_map].id_item = index_real;
			if (ItemTemplate7.item.get(map.item_map[index_item_map].id_item).getColor() == 21) {
				map.item_map[index_item_map].color = 1;
			} else {
				map.item_map[index_item_map].color = 0;
			}
			map.item_map[index_item_map].quantity = 1;
			map.item_map[index_item_map].category = 7;
			map.item_map[index_item_map].idmaster = (short) p_master.id;
			map.item_map[index_item_map].time_exist = System.currentTimeMillis() + 60_000L;
			map.item_map[index_item_map].time_pick = System.currentTimeMillis() + 1_500L;
			// add in4 game scr
			Message mi = new Message(19);
			mi.writer().writeByte(7);
			mi.writer().writeShort(index); // id mob die
			mi.writer().writeShort(ItemTemplate7.item.get(map.item_map[index_item_map].id_item).getIcon());
			mi.writer().writeShort(index_item_map); //
			mi.writer().writeUTF(ItemTemplate7.item.get(map.item_map[index_item_map].id_item).getName());
			mi.writer().writeByte(map.item_map[index_item_map].color); // color
			mi.writer().writeShort(-1); // id player
			MapService.send_msg_player_inside(map, p_master, mi, true);
			mi.cleanup();
		}
	}

	public static void leave_item_boss(Map map, Mob_in_map mob, Player p) throws IOException {
		if (mob != null && mob.is_boss) {
			// roi do boss co dinh
			short[] id_item_leave3 = new short[] {};
			short[] id_item_leave4 = new short[] {};
			short[] id_item_leave7 = new short[] {};
			short id_medal_material = -1;
			switch (mob.template.mob_id) {
				case 101: { // xa nu
					id_item_leave4 = new short[] {-1, -1, -1, -1, -1, -1, 53};
					id_item_leave7 = new short[] {0, 4, 14, 0, 4, 12, 2, 2, 1, 1, 10, 10};
					id_medal_material = 138;
                                        id_medal_material = 138;
                                        id_medal_material = 138;
                                        
					break;
				}
				case 84: { // de vang
					id_item_leave4 = new short[] {-1, -1, -1, -1, -1, -1, 54, 27, 27};
					id_item_leave7 = new short[] {8, 9, 10, 13, 14, 0, 4, 0, 4};
					id_medal_material = 139;
                                        id_medal_material = 139;
                                        id_medal_material = 139;
                                       
					break;
				}
				case 83: { // de bac
					id_item_leave4 = new short[] {-1, -1, -1, -1, -1, -1, 53};
					id_item_leave7 = new short[] {0, 4, 0, 4, 11, 12, 14, 2, 3, 2, 3};
					id_medal_material = 141;
                                        id_medal_material = 141;
                                        id_medal_material = 141;
					break;
				}
				case 103: { // bo cap chua
					id_item_leave4 =
					      new short[] {-1, -1, -1, -1, -1, -1, 48, 50, 18, 9, 48, 50, 18, 9, 2, 5, 2, 5, 2, 5, 2, 5};
					id_item_leave7 = new short[] {0, 0, 0};
					id_medal_material = 136;
                                        id_medal_material = 136;
                                        id_medal_material = 136;
					break;
				}
				case 104: { // quy 1 mat
					id_item_leave4 = new short[] {-1, -1, -1, -1, -1, -1, 10, 10};
					id_item_leave7 = new short[] {2, 3, 2, 3, 12, 12, 8, 9, 10, 8, 9, 10};
					id_medal_material = 137;
                                        id_medal_material = 137;
                                        id_medal_material = 137;
					break;
				}
				case 105: { // quy dau bo
					id_item_leave4 = new short[] {-1, -1, -1, -1, -1, -1, 54, 49, 49};
					id_item_leave7 = new short[] {11, 0, 4, 0, 4, 13, 14, 2, 3, 2, 3};
					id_medal_material = 140;
                                        id_medal_material = 140;
                                        id_medal_material = 140;
					break;
				}
				case 106: { // ky sy dia nguc
					id_item_leave4 = new short[] {-1, -1, -1, -1, -1, -1, 54, 53, 18};
					id_item_leave7 = new short[] {11, 13, 2, 3, 2, 3, 14};
					id_item_leave3 = new short[] {(short) Util.random(4577, 4585)};
					id_medal_material = (short) Util.random(142, 144);
                                        id_medal_material = (short) Util.random(142, 144);
                                        id_medal_material = (short) Util.random(142, 144);
					break;
				}
				case 149: { // nhen chua
					id_item_leave4 = new short[] {-1, -1, -1, -1, -1, -1, 54, 53, 18};
					id_item_leave7 = new short[] {11, 13, 2, 3, 2, 3, 14};
                                        id_item_leave3 = new short[] {(short) Util.random(4577, 4585)};
					id_medal_material = (short) Util.random(144, 146);
                                        id_medal_material = (short) Util.random(144, 146);
                                        id_medal_material = (short) Util.random(144, 146);
					break;
				}
				case 155: { // giant skeleton
					id_item_leave4 = new short[] {-1, -1, -1, -1, -1, -1, 54, 53, 18};
					id_item_leave7 = new short[] {11, 13, 2, 3, 2, 3, 14};
                                        id_item_leave3 = new short[] {(short) Util.random(4577, 4585)};
					id_medal_material = (short) Util.random(136, 146);
                                        id_medal_material = (short) Util.random(136, 146);
                                        id_medal_material = (short) Util.random(136, 146);
					break;
				}
                                case 174: { // cho 3 dau
					id_item_leave4 = new short[] {-1, -1, -1, -1, -1, -1, 54, 53, 18};
					id_item_leave7 = new short[] {11, 13, 2, 3, 2, 3, 14};
                                        id_item_leave3 = new short[] {(short) Util.random(4577, 4585)};
					id_medal_material = (short) Util.random(136, 146);
                                        id_medal_material = (short) Util.random(136, 146);
                                        id_medal_material = (short) Util.random(136, 146);
					break;
				}
			}
			for (short id : id_item_leave3) {
				ItemTemplate3 temp = ItemTemplate3.item.get(id);
				leave_item_by_type3(map, id, temp.getColor(), p, temp.getName(), mob.index);
			}
			for (short id : id_item_leave4) {
				if (id == -1) {
					leave_vang(map, mob, p);
				} else {
					leave_item_by_type4(map, id, p, mob.index);
				}
			}
			for (short id : id_item_leave7) {
				leave_item_by_type7(map, id, p, mob.index);
			}
			leave_item_by_type7(map, id_medal_material, p, mob.index);
                        if (50>Util.random(120)) {
                            leave_item_by_type4(map,(short) 319, p, mob.index);
                        }
			//
			// roi do boss ngau nhien
			// id_item_leave3 = new short[] {1};
			// ItemTemplate3 temp = ItemTemplate3.item.get(id_item_leave3[Util.random(0,
			// id_item_leave3.length)]);
			// leave_item_by_type3(map, temp.getId(), temp.getColor(), p, temp.getName(), index);
			// //
			// id_item_leave4 = new short[] {1};
			// leave_item_by_type4(map, id_item_leave4[Util.random(0, id_item_leave4.length)], p, index);
			// id_item_leave7 = new short[] {1};
			// leave_item_by_type7(map, id_item_leave7[Util.random(0, id_item_leave7.length)], p, index);
		}
	}

	public static void leave_material(Map map, Mob_in_map mob, Player p) throws IOException {
		if (mob != null) {
			short index_real = -1;
			switch (mob.level) {
				case 1:
				case 2:
				case 3:
				case 4: {
					index_real = Medal_Material.m_white[0][Util.random(0, 3)];
					break;
				}
				case 5: {
					index_real = Medal_Material.m_white[0][Util.random(3, 6)];
					break;
				}
				case 7:
				case 8:
				case 9:
				case 10:
				case 11:
				case 12:
				case 13:
				case 14:
				case 15:
				case 16:
				case 17: {
					index_real = Medal_Material.m_white[0][Util.random(6, 10)];
					break;
				}
				case 19: {
					index_real = Medal_Material.m_white[1][Util.random(0, 3)];
					break;
				}
				case 21:
				case 22:
				case 23:
				case 24:
				case 25:
				case 26: {
					index_real = Medal_Material.m_white[1][Util.random(3, 6)];
					break;
				}
				case 28:
				case 29: {
					index_real = Medal_Material.m_white[1][Util.random(6, 9)];
					break;
				}
				case 31:
				case 32:
				case 33:
				case 34:
				case 35: {
					index_real = Medal_Material.m_white[1][9];
					break;
				}
				case 37: {
					index_real = Medal_Material.m_white[2][Util.random(0, 3)];
					break;
				}
				case 39:
				case 40:
				case 41:
				case 42:
				case 43: {
					index_real = Medal_Material.m_white[2][Util.random(3, 6)];
					break;
				}
				case 44:
				case 45:
				case 46:
				case 47:
				case 48:
				case 49:
				case 50:
				case 51:
				case 52:
				case 53:
				case 54: {
					index_real = Medal_Material.m_white[2][Util.random(6, 10)];
					break;
				}
				case 55: {
					index_real = Medal_Material.m_white[3][Util.random(0, 3)];
					break;
				}
				case 57:
				case 58:
				case 59: {
					index_real = Medal_Material.m_white[3][Util.random(3, 6)];
					break;
				}
				case 61:
				case 62:
				case 63:
				case 64:
				case 65:
				case 66:
				case 67:
				case 68:
				case 69: {
					index_real = Medal_Material.m_white[3][Util.random(6, 10)];
					break;
				}
				case 71:
				case 72:
				case 73:
				case 74: {
					index_real = Medal_Material.m_white[4][Util.random(0, 3)];
					break;
				}
				case 76:
				case 77: {
					index_real = Medal_Material.m_white[4][Util.random(3, 6)];
					break;
				}
				case 78:
				case 79:
				case 80:
				case 81:
				case 82:
				case 83:
				case 84:
				case 85:
				case 86:
				case 87:
				case 88:
				case 89: {
					index_real = Medal_Material.m_white[4][Util.random(6, 10)];
					break;
				}
				case 90:
				case 91:
				case 92: {
					index_real = Medal_Material.m_white[5][Util.random(0, 3)];
					break;
				}
				case 93:
				case 94:
				case 95: {
					index_real = Medal_Material.m_white[5][Util.random(3, 6)];
					break;
				}
				case 96:
				case 97:
				case 98:
				case 99:
				case 100:
				case 101:
				case 102:
				case 103:
				case 104:
				case 105:
				case 106:
				case 107: {
					index_real = Medal_Material.m_white[5][Util.random(6, 10)];
					break;
				}
				case 109:
				case 110: {
					index_real = Medal_Material.m_white[6][Util.random(0, 3)];
					break;
				}
				case 112:
				case 113: {
					index_real = Medal_Material.m_white[6][Util.random(3, 6)];
					break;
				}
				case 114:
				case 115:
				case 116:
				case 117:
				case 118:
				case 119:
				case 120:
				case 121:
				case 122:
				case 123: {
					index_real = Medal_Material.m_white[6][Util.random(6, 10)];
					break;
				}
				case 125:
				case 126:
				case 127: {
					index_real = Medal_Material.m_white[0][Util.random(6, 10)];
					break;
				}
			}
			//
			if (index_real > -1) {
				//
				leave_item_by_type7(map, index_real, p, mob.index);
			}
			if (25 > Util.random(0, 100)) {
				index_real = Medal_Material.m_yellow[Util.random(0, 10)];
				//
				leave_item_by_type7(map, index_real, p, mob.index);
			}
			if (25 > Util.random(0, 100)) {
				index_real = (short) ((15 > Util.random(0, 120)) ? 11
				      : ((35 > Util.random(0, 120)) ? 10 : ((50 > Util.random(0, 120)) ? 9 : 8)));
				//
				leave_item_by_type7(map, index_real, p, mob.index);
			}
		}
	}

  public static void leave_item_event(Map map, Mob_in_map mob, Player p) throws IOException {
		if (mob != null) {
			switch (Manager.gI().event) {
				case 1: {
					short index_real = EventManager.item_leave[Manager.gI().event - 1][Util
					      .random(EventManager.item_leave[Manager.gI().event - 1].length)];
					if (!Event_1.check(p.name) && index_real >= 153 && index_real <= 156) {
						return;
					}
					//
					leave_item_by_type4(map, index_real, p, mob.index);
					break;
				}
				case 2: {
					if (10 > Util.random(120)) {
						short index_real = (short) Util.random(137, 140);
						leave_item_by_type4(map, index_real, p, mob.index);
					}
					break;
				}
			}
		}
	}
    public static void leave_material_ngockham(Map map, Mob_in_map mob, Player p) throws IOException {
		if (mob != null) {
			short index_real = -1;
			//
			if (25 > Util.random(120)) {
				switch (mob.template.mob_id) {
					case 167: {
						index_real = 362;
						break;
					}
					case 168: {
						index_real = 372;
						break;
					}
					case 169: {
						index_real = 367;
						break;
					}
					case 170: {
						index_real = 357;
						break;
					}
					case 171: {
						index_real = 377;
						break;
					}
					case 172: {
						index_real = 352;
						break;
					}
				}
			}
			if (index_real != -1) {
				leave_item_by_type7(map, index_real, p, mob.index);
			}
		}
	}
}
