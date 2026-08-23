package map;

import java.io.IOException;
import client.Clan;
import client.Pet;
import client.Player;
import core.Manager;
import core.MenuController;
import core.Service;
import core.Util;
import io.Message;
import io.Session;
import template.EffTemplate;
import template.Item3;
import template.LvSkill;
import template.Option;
import template.Option_pet;

public class MapService {
    public static void enter(Map map, Player p) throws IOException {
		synchronized (map) {
			map.players.add(p);
		}
		p.change_new_date();
		//
		if (map.map_id != 48) {
			map.send_map_data(p);
			Service.send_char_main_in4(p);
			Service.send_combo(p.conn);
			Service.send_point_pk(p);
			Service.send_health(p);
			Service.send_wear(p);
		}
		if (p.party != null) {
			p.party.sendin4();
		}
	}

	public static void leave(Map map, Player p) throws IOException {
		synchronized (map) {
			map.players.remove(p);
		}
		Message m = new Message(8);
		m.writer().writeShort(p.id);
		send_msg_player_inside(map, p, m, false);
		m.cleanup();
		p.other_player_inside.clear();
		p.other_mob_inside.clear();
		p.other_mob_inside_update.clear();
	}

	public static void send_msg_player_inside(Map map, Player p, Message m, boolean included) {
		for (int i = 0; i < map.players.size(); i++) {
			Player p0 = map.players.get(i);
			if ((Math.abs(p0.x - p.x) < 200) && (Math.abs(p0.y - p.y) < 200) && (included || (p.id != p0.id))) {
				p0.conn.addmsg(m);
			}
		}
	}

	public static void update_in4_2_other_inside(Map map, Player p) throws IOException {
		for (int i = 0; i < map.players.size(); i++) {
			Player p0 = map.players.get(i);
			if (p0.id != p.id && (Math.abs(p0.x - p.x) < 200) && (Math.abs(p0.y - p.y) < 200)) {
				MapService.send_in4_other_char(map, p0, p);
			}
		}
	}

	public static void send_move(Map map, Player p, Message m2) throws IOException {
		p.x = m2.reader().readShort();
		p.y = m2.reader().readShort();
		if (p.is_changemap) {
			for (Vgo vgo : map.vgos) {
				if (Math.abs(vgo.x_old - p.x) < 40 && Math.abs(vgo.y_old - p.y) < 40) {
					p.change_map(p, vgo);
					return;
				}
			}
		} else if (!(Math.abs(p.x_old - p.x) < 45 && Math.abs(p.y_old - p.y) < 45)) {
			p.is_changemap = true;
		}
		//
		if (map.map_id != 50) {
			Message m = new Message(4);
			m.writer().writeByte(0);
			m.writer().writeShort(0);
			m.writer().writeShort(p.id);
			m.writer().writeShort(p.x);
			m.writer().writeShort(p.y);
			m.writer().writeByte(-1);
			//
			MapService.update_inside_player(map, m, p);
			//
			m.cleanup();
		}
	}

	private static void update_inside_player(Map map, Message m, Player p) throws IOException {
		Message m4 = new Message(4);
		for (Mob_in_map temp : map.mobs) {
			if (temp.isdie) {
				continue;
			}
			if ((Math.abs(temp.x - p.x) < 200) && (Math.abs(temp.y - p.y) < 200)) {
				synchronized (map) {
					if (!temp.list_fight.contains(p)
					      && (temp.is_boss || (Math.abs(temp.x - p.x) < 50) && (Math.abs(temp.y - p.y) < 50))) {
						temp.list_fight.add(p);
					}
				}
				if (!p.other_mob_inside.containsKey(temp.index)) {
					p.other_mob_inside.put(temp.index, true);
				}
				if (!p.other_mob_inside_update.containsKey(temp.index)) {
					p.other_mob_inside_update.put(temp.index, false);
				}
				if (p.other_mob_inside.get(temp.index)) {
					m4.writer().writeByte(1);
					m4.writer().writeShort(temp.template.mob_id);
					m4.writer().writeShort(temp.index);
					m4.writer().writeShort(temp.x);
					m4.writer().writeShort(temp.y);
					m4.writer().writeByte(-1);
					p.other_mob_inside.replace(temp.index, true, false);
				} else if (p.other_mob_inside_update.get(temp.index)) {
					//
					Service.mob_in4(p, temp.index);
					p.other_mob_inside_update.replace(temp.index, true, false);
				}
			} else if (p.other_mob_inside_update.containsKey(temp.index) && !p.other_mob_inside_update.get(temp.index)) {
				p.other_mob_inside_update.replace(temp.index, false, true);
			}
		}
		//
		for (int i = 0; i < map.players.size(); i++) {
			Player p0 = map.players.get(i);
			if (p0.id == p.id) {
				continue;
			}
			if ((Math.abs(p0.x - p.x) < 200) && (Math.abs(p0.y - p.y) < 200)) {
				if (!p.other_player_inside.containsKey(p0.id)) {
					p.other_player_inside.put(p0.id, true);
				}
				p0.conn.addmsg(m);
				if (p.other_player_inside.get(p0.id)) {
					m4.writer().writeByte(0);
					m4.writer().writeShort(0);
					m4.writer().writeShort(p0.id);
					m4.writer().writeShort(p0.x);
					m4.writer().writeShort(p0.y);
					m4.writer().writeByte(-1);
					//
					p.other_player_inside.replace(p0.id, true, false);
				}
			} else if (p.other_player_inside.containsKey(p0.id)) {
				Message m3 = new Message(8);
				m3.writer().writeShort(p.id);
				p0.conn.addmsg(m3);
				m3.cleanup();
				m3 = new Message(8);
				m3.writer().writeShort(p0.id);
				p.conn.addmsg(m3);
				m3.cleanup();
				p.other_player_inside.remove(p0.id);
			}
		}
		if (m4.writer().size() > 0) {
			p.conn.addmsg(m4);
		}
		m4.cleanup();
	}

	public static Mob_in_map get_mob_by_index(Map map, int n) {
		if (map != null) {
			for (Mob_in_map m : map.mobs) {
				if (m.index == n) {
					return m;
				}
			}
		}
		return null;
	}

	public static void mob_fire(Map map, Mob_in_map mob, Player p_target) throws IOException {
		if (!mob.isdie && p_target != null) {
			// dame
			int dmob = Util.random(mob.level * 75, mob.level * 80);
			if (mob.level > 30 && mob.level <= 50) {
				dmob = (dmob * 13) / 10;
			} else if (mob.level > 50 && mob.level <= 70) {
				dmob = (dmob * 16) / 10;
			} else if (mob.level > 70 && mob.level <= 100) {
				dmob = (dmob * 19) / 10;
			} else if (mob.level > 100 && mob.level <= 600) {
				dmob = (dmob * 21) / 10;
			}
			if (dmob > 10) {
				dmob -= Util.random(0, dmob / 10);
			}
			// def enemy
			int def = p_target.body.get_def();
			EffTemplate ef = p_target.get_eff(15);
			if (ef != null) {
				def += (def * (ef.param / 100)) / 100;
			}
			dmob -= def;
			if (dmob <= 0) {
				dmob = 1;
			}
			if (mob.color_name != 0) {
				dmob *= 2;
			}
			if (p_target.getlevelpercent() < 0) {
				dmob *= 2;
			}
			int percent_stun = 0;
			int time_stun = 0;
			if (mob.is_boss) {
				switch (p_target.level - mob.level) {
					case 5:
					case 4:
					case 3:
					case 2:
					case 1:
					case 0: {
						percent_stun = 15;
						time_stun = 2;
						break;
					}
					default: {
						dmob *= 50;
						percent_stun = 50;
						time_stun = 5;
						break;
					}
				}
			}
			p_target.hp -= dmob;
			if (p_target.hp <= 0) {
				MapService.die_by_mob(map, p_target, mob);
			}
			Message m = new Message(10);
			m.writer().writeByte(1);
			m.writer().writeShort(mob.index);
			m.writer().writeInt(mob.hp);
			m.writer().writeByte(0);
			m.writer().writeByte(1);
			m.writer().writeShort(p_target.id);
			m.writer().writeInt(dmob); // dame mob
			m.writer().writeInt(p_target.hp);
			m.writer().writeByte(2); // id skill mob
			m.writer().writeByte(0);
			MapService.send_msg_player_inside(map, p_target, m, true);
			m.cleanup();
			// boss skill
			if (mob.is_boss && (percent_stun > Util.random(0, 100))) {
				int[] list = new int[] {7, 4, 5, 6};
				EffTemplate ef1 = p_target.get_eff(-121);
				EffTemplate ef2 = p_target.get_eff(-122);
				EffTemplate ef3 = p_target.get_eff(-123);
				EffTemplate ef4 = p_target.get_eff(-124);
				if (ef1 == null && ef2 == null && ef3 == null && ef4 == null) {
					MapService.add_eff_stun(map, p_target, null, time_stun, list[Util.random(0, list.length)], mob.index);
				}
			}
		}
	}

	private static void add_eff_stun(Map map, Player p, Player p2, int time, int type, int index_mob)
	      throws IOException {
		if (p2 == null) {
			p2 = p;
		}
		Message m = new Message(75);
		m.writer().writeByte(type);
		m.writer().writeByte(0);
		m.writer().writeShort(p2.id);
		m.writer().writeShort(time);
		m.writer().writeByte(0);
		m.writer().writeShort(p.id);
		MapService.send_msg_player_inside(map, p, m, true);
		m.cleanup();
		//
		int time_ = 1000 * time;
		switch (type) {
			case 7: {
				p2.add_eff(-124, 1000, time_);
				break;
			}
			case 4: {
				p2.add_eff(-123, 1000, time_);
				break;
			}
			case 5: {
				p2.add_eff(-122, 1000, time_);
				break;
			}
			case 6: {
				p2.add_eff(-121, 1000, time_);
				break;
			}
		}
	}

	private static void die_by_mob(Map map, Player p_target, Mob_in_map mob) throws IOException {
		p_target.dame_affect_special_sk = 0;
		p_target.hp = 0;
		p_target.isdie = true;
		MapService.change_flag(map, p_target, -1);
		//
		Message m = new Message(41);
		m.writer().writeShort(p_target.id);
		m.writer().writeShort(mob.index);
		m.writer().writeShort(-1); // point pk
		m.writer().writeByte(1); // type main object
		MapService.send_msg_player_inside(map, p_target, m, true);
		m.cleanup();
		p_target.type_use_mount = -1;
	}

	public static void change_flag(Map map, Player p_target, int type) throws IOException {
		Message m = new Message(42);
		m.writer().writeShort(p_target.id);
		m.writer().writeByte(type);
		p_target.typepk = (byte) type;
		MapService.send_msg_player_inside(map, p_target, m, true);
		m.cleanup();
	}

	@SuppressWarnings("unused")
	public static void buff_skill(Map map, Session conn, Message m2) throws IOException {
		byte type = m2.reader().readByte();
		byte tem = m2.reader().readByte();
		byte size_buff = m2.reader().readByte();
		// System.out.println(type);
		// System.out.println(tem);
		// System.out.println(size_buff);
		MapService.add_eff_skill(map, conn.p, null, type);
		for (int i = 0; i < size_buff; i++) {
			int id = Short.toUnsignedInt(m2.reader().readShort());
			// System.out.println(id);
		}
	}

	private static void add_eff_skill(Map map, Player p, Player p2, byte index_skill) throws IOException {
		int sk_point = p.body.get_skill_point(index_skill);
		if (sk_point < 1) {
			return;
		}
		int time_buff = p.skills[index_skill].mLvSkill[sk_point - 1].timeBuff;
		int range = p.skills[index_skill].mLvSkill[sk_point - 1].range_lan;
		int n_target = p.skills[index_skill].mLvSkill[sk_point - 1].nTarget - 1;
		switch (p.clazz) {
			case 0: {
				if (index_skill == 18) {
					p.add_eff(23, 1000, 60_000);
					Message m = new Message(75);
					m.writer().writeByte(12);
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					m.writer().writeShort(60);
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					p.conn.addmsg(m);
					m.cleanup();
					MapService.send_eff_other(p.map, p, 23);
					Service.send_char_main_in4(p);
				} else if (index_skill == 13) {
					byte[] id_sk = new byte[] {15, 35};
					int[] param_sk = new int[] {p.get_pramskill_byid(index_skill, id_sk[0]),
					      p.get_pramskill_byid(index_skill, id_sk[1])};
					for (int i = 0; i < p.map.players.size(); i++) {
						if (n_target < 1) {
							continue;
						}
						Player p0 = p.map.players.get(i);
						if (p0 != null && p0.id != p.id && !p0.isdie && Math.abs(p0.x - p.x) < range
						      && Math.abs(p0.y - p.y) < range && p0.typepk != 0 && p.typepk == p0.typepk) {
							for (int j = 0; j < id_sk.length; j++) {
								p0.add_eff(id_sk[j], param_sk[j], time_buff);
							}
							MapService.add_eff_skill_msg(map, p, p0, index_skill, time_buff, id_sk, param_sk);
							n_target--;
						}
					}
					for (int j = 0; j < id_sk.length; j++) {
						p.add_eff(id_sk[j], param_sk[j], time_buff);
					}
					MapService.add_eff_skill_msg(map, p, p, index_skill, time_buff, id_sk, param_sk);
				} else if (index_skill == 14) {
					byte[] id_sk = new byte[] {33, 9, 7};
					int[] param_sk = new int[] {p.get_pramskill_byid(index_skill, id_sk[0]),
					      p.get_pramskill_byid(index_skill, id_sk[1]), p.get_pramskill_byid(index_skill, id_sk[2])};
					for (int i = 0; i < p.map.players.size(); i++) {
						if (n_target < 1) {
							continue;
						}
						Player p0 = p.map.players.get(i);
						if (p0 != null && p0.id != p.id && !p0.isdie && Math.abs(p0.x - p.x) < range
						      && Math.abs(p0.y - p.y) < range && p0.typepk != 0 && p.typepk == p0.typepk) {
							for (int j = 0; j < id_sk.length; j++) {
								p0.add_eff(id_sk[j], param_sk[j], time_buff);
							}
							MapService.add_eff_skill_msg(map, p, p0, index_skill, time_buff, id_sk, param_sk);
							n_target--;
						}
					}
					for (int j = 0; j < id_sk.length; j++) {
						p.add_eff(id_sk[j], param_sk[j], time_buff);
					}
					MapService.add_eff_skill_msg(map, p, p, index_skill, time_buff, id_sk, param_sk);
				} else if (index_skill == 17 && p2 != null) {
					MapService.add_eff_stun(map, p, p2, 5, 7, -1);
				}
				break;
			}
			case 1: {
				if (index_skill == 18) {
					p.add_eff(24, 1000, 60_000);
					Message m = new Message(75);
					m.writer().writeByte(12);
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					m.writer().writeShort(60);
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					p.conn.addmsg(m);
					m.cleanup();
					MapService.send_eff_other(map, p, 24);
					Service.send_char_main_in4(p);
				} else if (index_skill == 13) {
					byte[] id_sk = new byte[] {15, 34};
					int[] param_sk = new int[] {p.get_pramskill_byid(index_skill, id_sk[0]),
					      p.get_pramskill_byid(index_skill, id_sk[1])};
					for (int i = 0; i < p.map.players.size(); i++) {
						if (n_target < 1) {
							continue;
						}
						Player p0 = p.map.players.get(i);
						if (p0 != null && p0.id != p.id && !p0.isdie && Math.abs(p0.x - p.x) < range
						      && Math.abs(p0.y - p.y) < range && p0.typepk != 0 && p.typepk == p0.typepk) {
							for (int j = 0; j < id_sk.length; j++) {
								p0.add_eff(id_sk[j], param_sk[j], time_buff);
							}
							MapService.add_eff_skill_msg(map, p, p0, index_skill, time_buff, id_sk, param_sk);
							n_target--;
						}
					}
					for (int j = 0; j < id_sk.length; j++) {
						p.add_eff(id_sk[j], param_sk[j], time_buff);
					}
					MapService.add_eff_skill_msg(map, p, p, index_skill, time_buff, id_sk, param_sk);
				} else if (index_skill == 14) {
					byte[] id_sk = new byte[] {33, 11, 7};
					int[] param_sk = new int[] {p.get_pramskill_byid(index_skill, id_sk[0]),
					      p.get_pramskill_byid(index_skill, id_sk[1]), p.get_pramskill_byid(index_skill, id_sk[2])};
					for (int i = 0; i < p.map.players.size(); i++) {
						if (n_target < 1) {
							continue;
						}
						Player p0 = p.map.players.get(i);
						if (p0 != null && p0.id != p.id && !p0.isdie && Math.abs(p0.x - p.x) < range
						      && Math.abs(p0.y - p.y) < range && p0.typepk != 0 && p.typepk == p0.typepk) {
							for (int j = 0; j < id_sk.length; j++) {
								p0.add_eff(id_sk[j], param_sk[j], time_buff);
							}
							MapService.add_eff_skill_msg(map, p, p0, index_skill, time_buff, id_sk, param_sk);
							n_target--;
						}
					}
					for (int j = 0; j < id_sk.length; j++) {
						p.add_eff(id_sk[j], param_sk[j], time_buff);
					}
					MapService.add_eff_skill_msg(map, p, p, index_skill, time_buff, id_sk, param_sk);
				} else if (index_skill == 17 && p2 != null) {
					MapService.add_eff_stun(map, p, p2, 10, 4, -1);
				}
				break;
			}
			case 2: {
				if (index_skill == 18) {
					p.add_eff(52, 1000, 60_000);
					Message m = new Message(75);
					m.writer().writeByte(12);
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					m.writer().writeShort(60);
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					p.conn.addmsg(m);
					m.cleanup();
					MapService.send_eff_other(map, p, 52);
				} else if (index_skill == 13) {
					byte[] id_sk = new byte[] {15, 35};
					int[] param_sk = new int[] {p.get_pramskill_byid(index_skill, id_sk[0]),
					      p.get_pramskill_byid(index_skill, id_sk[1])};
					for (int i = 0; i < p.map.players.size(); i++) {
						if (n_target < 1) {
							continue;
						}
						Player p0 = p.map.players.get(i);
						if (p0 != null && p0.id != p.id && !p0.isdie && Math.abs(p0.x - p.x) < range
						      && Math.abs(p0.y - p.y) < range && p0.typepk != 0 && p.typepk == p0.typepk) {
							for (int j = 0; j < id_sk.length; j++) {
								p0.add_eff(id_sk[j], param_sk[j], time_buff);
							}
							MapService.add_eff_skill_msg(map, p, p0, index_skill, time_buff, id_sk, param_sk);
							n_target--;
						}
					}
					for (int j = 0; j < id_sk.length; j++) {
						p.add_eff(id_sk[j], param_sk[j], time_buff);
					}
					MapService.add_eff_skill_msg(map, p, p, index_skill, time_buff, id_sk, param_sk);
				} else if (index_skill == 14) {
					byte[] id_sk = new byte[] {36, 8, 7};
					int[] param_sk = new int[] {p.get_pramskill_byid(index_skill, id_sk[0]),
					      p.get_pramskill_byid(index_skill, id_sk[1]), p.get_pramskill_byid(index_skill, id_sk[2])};
					for (int i = 0; i < p.map.players.size(); i++) {
						if (n_target < 1) {
							continue;
						}
						Player p0 = p.map.players.get(i);
						if (p0 != null && p0.id != p.id && !p0.isdie && Math.abs(p0.x - p.x) < range
						      && Math.abs(p0.y - p.y) < range && p0.typepk != 0 && p.typepk == p0.typepk) {
							for (int j = 0; j < id_sk.length; j++) {
								p0.add_eff(id_sk[j], param_sk[j], time_buff);
							}
							MapService.add_eff_skill_msg(map, p, p0, index_skill, time_buff, id_sk, param_sk);
							n_target--;
						}
					}
					for (int j = 0; j < id_sk.length; j++) {
						p.add_eff(id_sk[j], param_sk[j], time_buff);
					}
					MapService.add_eff_skill_msg(map, p, p, index_skill, time_buff, id_sk, param_sk);
				} else if (index_skill == 17 && p2 != null) {
					MapService.add_eff_stun(map, p, p2, 10, 5, -1);
				}
				break;
			}
			case 3: {
				if (index_skill == 18) {
					p.add_eff(53, 1000, 60_000);
					Message m = new Message(75);
					m.writer().writeByte(12);
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					m.writer().writeShort(60);
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					p.conn.addmsg(m);
					m.cleanup();
					MapService.send_eff_other(map, p, 53);
				} else if (index_skill == 13) {
					byte[] id_sk = new byte[] {15, 34};
					int[] param_sk = new int[] {p.get_pramskill_byid(index_skill, id_sk[0]),
					      p.get_pramskill_byid(index_skill, id_sk[1])};
					for (int i = 0; i < p.map.players.size(); i++) {
						if (n_target < 1) {
							continue;
						}
						Player p0 = p.map.players.get(i);
						if (p0 != null && p0.id != p.id && !p0.isdie && Math.abs(p0.x - p.x) < range
						      && Math.abs(p0.y - p.y) < range && p0.typepk != 0 && p.typepk == p0.typepk) {
							for (int j = 0; j < id_sk.length; j++) {
								p0.add_eff(id_sk[j], param_sk[j], time_buff);
							}
							MapService.add_eff_skill_msg(map, p, p0, index_skill, time_buff, id_sk, param_sk);
							n_target--;
						}
					}
					for (int j = 0; j < id_sk.length; j++) {
						p.add_eff(id_sk[j], param_sk[j], time_buff);
					}
					MapService.add_eff_skill_msg(map, p, p, index_skill, time_buff, id_sk, param_sk);
				} else if (index_skill == 14) {
					byte[] id_sk = new byte[] {36, 10, 7};
					int[] param_sk = new int[] {p.get_pramskill_byid(index_skill, id_sk[0]),
					      p.get_pramskill_byid(index_skill, id_sk[1]), p.get_pramskill_byid(index_skill, id_sk[2])};
					for (int i = 0; i < p.map.players.size(); i++) {
						if (n_target < 1) {
							continue;
						}
						Player p0 = p.map.players.get(i);
						if (p0 != null && p0.id != p.id && !p0.isdie && Math.abs(p0.x - p.x) < range
						      && Math.abs(p0.y - p.y) < range && p0.typepk != 0 && p.typepk == p0.typepk) {
							for (int j = 0; j < id_sk.length; j++) {
								p0.add_eff(id_sk[j], param_sk[j], time_buff);
							}
							MapService.add_eff_skill_msg(map, p, p0, index_skill, time_buff, id_sk, param_sk);
							n_target--;
						}
					}
					for (int j = 0; j < id_sk.length; j++) {
						p.add_eff(id_sk[j], param_sk[j], time_buff);
					}
					MapService.add_eff_skill_msg(map, p, p, index_skill, time_buff, id_sk, param_sk);
				} else if (index_skill == 17 && p2 != null) {
					MapService.add_eff_stun(map, p, p2, 10, 6, -1);
				}
				break;
			}
		}
	}

	private static void add_eff_skill_msg(Map map, Player p, Player p0, byte index_skill, int time_buff, byte[] id_sk,
	      int[] param_sk) throws IOException {
		int index_skill2 = 0;
		switch (p.clazz) {
			case 0: {
				index_skill2 = index_skill;
				break;
			}
			case 1: {
				if (index_skill == 13) {
					index_skill2 = 30;
				} else {
					index_skill2 = 31;
				}
				break;
			}
			case 2: {
				index_skill2 = index_skill;
				break;
			}
			case 3: {
				if (index_skill == 13) {
					index_skill2 = 30;
				} else {
					index_skill2 = 31;
				}
				break;
			}
		}
		Message m = new Message(40);
		m.writer().writeByte(1);
		m.writer().writeByte(1);
		m.writer().writeShort(p.id);
		m.writer().writeByte(index_skill);
		m.writer().writeInt(time_buff);
		m.writer().writeShort(p0.id);
		m.writer().writeByte(0);
		m.writer().writeByte(index_skill2);
		if (index_skill == 13) {
			int index = -1;
			m.writer().writeByte(id_sk.length + 1);
			for (int i = 0; i < id_sk.length; i++) {
				m.writer().writeByte(id_sk[i]);
				m.writer().writeInt(param_sk[i]);
				if (id_sk[i] == 15) {
					index = i;
				}
			}
			int param;
			if (index == -1) {
				param = 0;
			} else {
				param = param_sk[index];
			}
			m.writer().writeByte(14);
			m.writer().writeInt(p0.body.get_def() * (param / 100) / 100);
		} else if (index_skill == 14) {
			int index = -1;
			int index1 = -1;
			int index2 = -1;
			int index3 = -1;
			int index4 = -1;
			m.writer().writeByte(id_sk.length + 5);
			for (int i = 0; i < id_sk.length; i++) {
				m.writer().writeByte(id_sk[i]);
				m.writer().writeInt(param_sk[i]);
				if (id_sk[i] == 7) {
					index = i;
				}
				if (id_sk[i] == 8) {
					index1 = i;
				}
				if (id_sk[i] == 9) {
					index2 = i;
				}
				if (id_sk[i] == 10) {
					index3 = i;
				}
				if (id_sk[i] == 11) {
					index4 = i;
				}
			}
			int pr0, pr1, pr2, pr3, pr4;
			if (index == -1) {
				pr0 = 0;
			} else {
				pr0 = param_sk[index];
			}
			if (index1 == -1) {
				pr1 = 0;
			} else {
				pr1 = param_sk[index1];
			}
			if (index2 == -1) {
				pr2 = 0;
			} else {
				pr2 = param_sk[index2];
			}
			if (index3 == -1) {
				pr3 = 0;
			} else {
				pr3 = param_sk[index3];
			}
			if (index4 == -1) {
				pr4 = 0;
			} else {
				pr4 = param_sk[index4];
			}
			m.writer().writeByte(0);
			m.writer().writeInt((p0.body.get_dame_physical() * (pr0 / 100)) / 100);
			m.writer().writeByte(1);
			m.writer().writeInt((p0.body.get_dame_prop(1) * (pr1 / 100)) / 100);
			m.writer().writeByte(2);
			m.writer().writeInt((p0.body.get_dame_prop(2) * (pr2 / 100)) / 100);
			m.writer().writeByte(3);
			m.writer().writeInt((p0.body.get_dame_prop(3) * (pr3 / 100)) / 100);
			m.writer().writeByte(4);
			m.writer().writeInt((p0.body.get_dame_prop(4) * (pr4 / 100)) / 100);
		} else {
			m.writer().writeByte(0);
		}
		p0.conn.addmsg(m);
		m.cleanup();
		if (p0.id != p.id) {
			m = new Message(40);
			m.writer().writeByte(1);
			m.writer().writeByte(1);
			m.writer().writeShort(p.id);
			m.writer().writeByte(index_skill);
			m.writer().writeInt(time_buff);
			m.writer().writeShort(p0.id);
			m.writer().writeByte(0);
			m.writer().writeByte(index_skill2);
			m.writer().writeByte(0);
			p.conn.addmsg(m);
			m.cleanup();
		}
		//
		m = new Message(40);
		m.writer().writeByte(0);
		m.writer().writeByte(1);
		m.writer().writeShort(p.id);
		m.writer().writeByte(index_skill);
		m.writer().writeInt(time_buff);
		m.writer().writeShort(p0.id);
		m.writer().writeByte(0);
		m.writer().writeByte(index_skill2);
		if (index_skill == 13) {
			int index = -1;
			m.writer().writeByte(id_sk.length + 1);
			for (int i = 0; i < id_sk.length; i++) {
				m.writer().writeByte(id_sk[i]);
				m.writer().writeInt(param_sk[i]);
				if (id_sk[i] == 15) {
					index = i;
				}
			}
			int param;
			if (index == -1) {
				param = 0;
			} else {
				param = param_sk[index];
			}
			m.writer().writeByte(14);
			m.writer().writeInt(p0.body.get_def() * (param / 100) / 100);
		} else if (index_skill == 14) {
			int index = -1;
			int index1 = -1;
			int index2 = -1;
			int index3 = -1;
			int index4 = -1;
			m.writer().writeByte(id_sk.length + 5);
			for (int i = 0; i < id_sk.length; i++) {
				m.writer().writeByte(id_sk[i]);
				m.writer().writeInt(param_sk[i]);
				if (id_sk[i] == 7) {
					index = i;
				}
				if (id_sk[i] == 8) {
					index1 = i;
				}
				if (id_sk[i] == 9) {
					index2 = i;
				}
				if (id_sk[i] == 10) {
					index3 = i;
				}
				if (id_sk[i] == 11) {
					index4 = i;
				}
			}
			int pr0, pr1, pr2, pr3, pr4;
			if (index == -1) {
				pr0 = 0;
			} else {
				pr0 = param_sk[index];
			}
			if (index1 == -1) {
				pr1 = 0;
			} else {
				pr1 = param_sk[index1];
			}
			if (index2 == -1) {
				pr2 = 0;
			} else {
				pr2 = param_sk[index2];
			}
			if (index3 == -1) {
				pr3 = 0;
			} else {
				pr3 = param_sk[index3];
			}
			if (index4 == -1) {
				pr4 = 0;
			} else {
				pr4 = param_sk[index4];
			}
			m.writer().writeByte(0);
			m.writer().writeInt((p0.body.get_dame_physical() * (pr0 / 100)) / 100);
			m.writer().writeByte(1);
			m.writer().writeInt((p0.body.get_dame_prop(1) * (pr1 / 100)) / 100);
			m.writer().writeByte(2);
			m.writer().writeInt((p0.body.get_dame_prop(2) * (pr2 / 100)) / 100);
			m.writer().writeByte(3);
			m.writer().writeInt((p0.body.get_dame_prop(3) * (pr3 / 100)) / 100);
			m.writer().writeByte(4);
			m.writer().writeInt((p0.body.get_dame_prop(4) * (pr4 / 100)) / 100);
		} else {
			m.writer().writeByte(0);
		}
		p0.conn.addmsg(m);
		m.cleanup();
	}

	private static void send_eff_other(Map map, Player p, int id) throws IOException {
		EffTemplate temp = p.get_eff(id);
		if (temp != null) {
			switch (id) {
				case -121: {
					Message m = new Message(75);
					m.writer().writeByte(6);
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					long time_exist = temp.time - System.currentTimeMillis();
					if (time_exist < 1000) {
						return;
					}
					m.writer().writeShort((short) (time_exist / 1000));
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					MapService.send_msg_player_inside(map, p, m, false);
					m.cleanup();
					break;
				}
				case -122: {
					Message m = new Message(75);
					m.writer().writeByte(5);
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					long time_exist = temp.time - System.currentTimeMillis();
					if (time_exist < 1000) {
						return;
					}
					m.writer().writeShort((short) (time_exist / 1000));
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					MapService.send_msg_player_inside(map, p, m, false);
					m.cleanup();
					break;
				}
				case -123: {
					Message m = new Message(75);
					m.writer().writeByte(4);
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					long time_exist = temp.time - System.currentTimeMillis();
					if (time_exist < 1000) {
						return;
					}
					m.writer().writeShort((short) (time_exist / 1000));
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					MapService.send_msg_player_inside(map, p, m, false);
					m.cleanup();
					break;
				}
				case -124: {
					Message m = new Message(75);
					m.writer().writeByte(7);
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					long time_exist = temp.time - System.currentTimeMillis();
					if (time_exist < 1000) {
						return;
					}
					m.writer().writeShort((short) (time_exist / 1000));
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					MapService.send_msg_player_inside(map, p, m, false);
					m.cleanup();
					break;
				}
				case 23:
				case 24:
				case 52:
				case 53: {
					Message m = new Message(75);
					m.writer().writeByte(12);
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					long time_exist = temp.time - System.currentTimeMillis();
					if (time_exist < 1000) {
						return;
					}
					m.writer().writeShort((short) (time_exist / 1000));
					m.writer().writeByte(0);
					m.writer().writeShort(p.id);
					MapService.send_msg_player_inside(map, p, m, false);
					m.cleanup();
					break;
				}
			}
		}
	}

	public static void send_chat(Map map, Session conn, Message m2) throws IOException {
		String chat = m2.reader().readUTF();
		if (conn.user.equals("admin") && chat.equals("admin")) {
			MenuController.send_menu_select(conn, 126, new String[] {"Bảo trì", "Cộng vàng x1.000.000.000",
			      "Cộng ngọc x1.000.000", "Update BXH", "Lấy item", "Up level", "Set Xp"});
		} else if (conn.user.equals("admin") && chat.equals("xem")) {
			int num = 0;
			for (Map[] maps : Map.entrys) {
				for (Map map_ : maps) {
					num += map_.players.size();
				}
			}
			String boss_in4 = "\nBoss:\n";
			for (Mob_in_map m : Mob_in_map.list_boss) {
				if (m.is_boss_active) {
					boss_in4 += m.template.name + " - Map " + Map.get_map_by_id(m.map_id)[0].name + " - Khu "
					      + (m.zone_id + 1) + "\n";
				}
			}
			Service.send_notice_box(conn,
			      "Vị Trí " + conn.p.x + " - " + conn.p.y + "\n Map id : " + map.map_id + "\n Zone : " + map.zone_id
			            + "\n Số Người kết nối : " + Session.client_entrys.size() + "\n Số Người online : " + num
			            + boss_in4);
		} else if (chat.equals("thoatket")) {
			Vgo vgo = new Vgo();
			vgo.id_map_go = 1;
			vgo.x_new = 432;
			vgo.y_new = 354;
			conn.p.change_map(conn.p, vgo);
			// } else if (chat.substring(0, 1).equals("t")) {
			// int n = Integer.parseInt(chat.substring(1, chat.length()));
			// conn.p.type_use_mount = (byte) n;
			// this.send_mount(conn.p);
			// Message m = new Message(-49);
			// m.writer().writeByte(2);
			// m.writer().writeShort(0);
			// m.writer().writeByte(0);
			// m.writer().writeByte(0);
			// m.writer().writeByte(n);
			// m.writer().writeShort(conn.p.id);
			// m.writer().writeByte(0);
			// m.writer().writeByte(0);
			// m.writer().writeInt(3000);
			// send_msg_player_inside(conn.p, m, true);
			// m.cleanup();
		}else if (chat.equals("pk")) {
			Service.send_notice_box(conn, "Bạn đang có "+ conn.p.hieuchien +" Điểm Pk.");
                }else {
			Message m = new Message(27);
			m.writer().writeShort(conn.p.id);
			m.writer().writeByte(0);
			m.writer().writeUTF(chat);
			MapService.send_msg_player_inside(map, conn.p, m, false);
			m.cleanup();
		}
	}

	public static void send_in4_other_char(Map map, Player p, Player p0) throws IOException {
		synchronized (map) {
			int dem = 0;
			for (int i = 0; i < 11; i++) {
				if (i != 0 && i != 1 && i != 6 && i != 7 && i != 10) {
					continue;
				}
				if (p0.item.wear[i] != null) {
					dem++;
				}
			}
			Message m = new Message(5);
			m.writer().writeShort(p0.id);
			m.writer().writeUTF(p0.name);
			m.writer().writeShort(p0.x);
			m.writer().writeShort(p0.y);
			m.writer().writeByte(p0.clazz);
			m.writer().writeByte(-1);
			m.writer().writeByte(p0.head);
			m.writer().writeByte(p0.eye);
			m.writer().writeByte(p0.hair);
			m.writer().writeShort(p0.level);
			m.writer().writeInt(p0.hp);
			m.writer().writeInt(p0.body.get_max_hp());
			m.writer().writeByte(p0.typepk);
			m.writer().writeShort(p0.pointpk);
			m.writer().writeByte(dem);
			//
			for (int i = 0; i < p0.item.wear.length; i++) {
				if (i != 0 && i != 1 && i != 6 && i != 7 && i != 10) {
					continue;
				}
				Item3 temp = p0.item.wear[i];
				if (temp != null) {
					m.writer().writeByte(temp.type);
					m.writer().writeByte(temp.part);
					m.writer().writeByte(3);
					m.writer().writeShort(-1);
					m.writer().writeShort(-1);
					m.writer().writeShort(-1);
					m.writer().writeShort(-1); // eff
				}
			}
			//
			if (p0.myclan != null) {
				m.writer().writeShort(p0.myclan.icon);
				m.writer().writeInt(Clan.get_id_clan(p0.myclan));
				m.writer().writeUTF(p0.myclan.name_clan_shorted);
				m.writer().writeByte(p0.myclan.get_mem_type(p0.name));
			} else {
				m.writer().writeShort(-1); // clan
			}
			if (p0.pet_follow) {
				for (Pet temp : p0.mypet) {
					if (temp.is_follow) {
						m.writer().writeByte(temp.type); // type
						m.writer().writeByte(temp.icon); // icon
						m.writer().writeByte(temp.nframe); // nframe
						break;
					}
				}
			} else {
				m.writer().writeByte(-1); // pet
			}
			m.writer().writeByte(p0.fashion.length);
			for (int i = 0; i < p0.fashion.length; i++) {
				m.writer().writeByte(p0.fashion[i]);
			}
			//
			m.writer().writeShort(-1);
			m.writer().writeByte(p0.type_use_mount);
			m.writer().writeBoolean(false);
			m.writer().writeByte(1);
			m.writer().writeByte(0);
			m.writer().writeShort(-1); // mat na
			m.writer().writeByte(1); // paint mat na trc sau
			m.writer().writeShort(-1); // phi phong
			m.writer().writeShort(-1); // weapon
			m.writer().writeShort(p0.id_horse);
			m.writer().writeShort(-1); // hair
			m.writer().writeShort(-1); // wing
			m.writer().writeShort(-1); // body
			m.writer().writeShort(-1); // leg
			m.writer().writeShort(-1); // bienhinh
			p.conn.addmsg(m);
			m.cleanup();
			for (int i = 0; i < p0.list_eff.size(); i++) {
				EffTemplate temp = p0.list_eff.get(i);
				if (temp.id == 52 || temp.id == 53 || temp.id == 23 || temp.id == 24 || temp.id == -124 || temp.id == -123
				      || temp.id == -122 || temp.id == -121) {
					// this.send_eff_other(p0, temp.id);
				}
			}
		}
	}

	public static void request_livefromdie(Map map, Session conn, Message m) throws IOException {
		byte type = m.reader().readByte();
		if (type == 1) { // hsl
			Service.send_box_input_yesno(conn, 9, "Cần 5 ngọc cho mỗi lần hồi sinh tại chỗ ok?");
		} else if (type == 0) { // ve lang
			conn.p.isdie = false;
			conn.p.hp = conn.p.body.get_max_hp();
			conn.p.mp = conn.p.body.get_max_mp();
			Vgo vgo = new Vgo();
			vgo.id_map_go = 1;
			vgo.x_new = (short) 528;
			vgo.y_new = (short) 480;
			conn.p.change_map(conn.p, vgo);
			Service.usepotion(conn.p, 0, conn.p.body.get_max_hp());
			Service.usepotion(conn.p, 1, conn.p.body.get_max_mp());
		}
	}

	public static void use_skill(Map map, Session conn, Message m, int type_atk) throws IOException {
		synchronized (map) {
			byte index_skill = m.reader().readByte();
			int n = m.reader().readByte();
			int sk_point = conn.p.body.get_skill_point(index_skill);
			if (sk_point < 1) {
				return;
			}
			if (conn.p.item.wear[0] == null) {
				Service.send_notice_nobox_white(conn, "Chưa có vũ khí");
				return;
			}
			LvSkill temp = conn.p.skills[index_skill].mLvSkill[sk_point - 1];
			while (sk_point > 1 && temp.LvRe > (conn.p.level + 1)) {
				sk_point--;
				temp = conn.p.skills[index_skill].mLvSkill[sk_point - 1];
			}
			if (temp.LvRe > (conn.p.level + 1)) {
				return;
			}
			if (conn.p.mp - temp.mpLost < 0) {
				Service.send_notice_nobox_white(conn, "Không đủ mp");
				return;
			}
			conn.p.mp -= temp.mpLost;
			if (conn.p.time_delay_skill[index_skill] > System.currentTimeMillis()) {
				if (++conn.p.enough_time_disconnect > 5) {
					conn.close();
				}
			} else {
				n = (temp.nTarget < n) ? temp.nTarget : n;
				
				conn.p.time_delay_skill[index_skill] = System.currentTimeMillis() + ((temp.delay * 199) / 200);
				conn.p.enough_time_disconnect = 0;
				if (conn.p.get_eff(-124) != null || conn.p.get_eff(-122) != null || conn.p.get_eff(-121) != null) {
					return;
				}
				EffTemplate ef;
				int dame = conn.p.body.total_item_param(40);
				byte type = 0;
				if (index_skill == 2 || index_skill == 4 || index_skill == 6 || index_skill == 8 || index_skill == 19
				      || index_skill == 20) {
					type = index_skill == 20 && (conn.p.clazz == 2 || conn.p.clazz == 1)
					      || index_skill == 19 && (conn.p.clazz == 0 || conn.p.clazz == 3) ? (byte) 0 : 1;
				}
				if (index_skill == 0) {
					type = 2;
				}
				if (type == 0) {
					dame += conn.p.body.get_dame_physical();
					ef = conn.p.get_eff(7);
					if (ef != null) {
						dame += dame * (ef.param / 100) / 100;
					}
				} else if (type == 1) {
					switch (conn.p.clazz) {
						case 0: {
							dame += conn.p.body.get_dame_prop(2);
							ef = conn.p.get_eff(9);
							if (ef != null) {
								dame += dame * (ef.param / 100) / 100;
							}
							break;
						}
						case 1: {
							dame += conn.p.body.get_dame_prop(4);
							ef = conn.p.get_eff(11);
							if (ef != null) {
								dame += dame * (ef.param / 100) / 100;
							}
							break;
						}
						case 2: {
							dame += conn.p.body.get_dame_prop(1);
							ef = conn.p.get_eff(8);
							if (ef != null) {
								dame += dame * (ef.param / 100) / 100;
							}
							break;
						}
						case 3: {
							dame += conn.p.body.get_dame_prop(3);
							ef = conn.p.get_eff(10);
							if (ef != null) {
								dame += dame * (ef.param / 100) / 100;
							}
							break;
						}
					}
				} else {
					dame += conn.p.body.get_dame_physical() / 2;
				}
				if (index_skill == 19 && conn.p.clazz == 1) {
					for (Option op : temp.minfo) {
						if (op.id == 4) {
							dame += op.getParam(0);
						}
						if (op.id == 11) {
							dame += dame * (op.getParam(0) / 100) / 100;
						}
					}
				} else {
					for (int i = temp.minfo.length - 1; i >= 0; i--) {
						Option op = temp.minfo[i];
						if (type == 0) {
							if (op.id == 0) {
								dame += op.getParam(0);
							}
							if (op.id == 7) {
								dame += dame * (op.getParam(0) / 100) / 100;
							}
						} else {
							if (op.id == 1 || op.id == 2 || op.id == 3 || op.id == 4) {
								dame += op.getParam(0);
							}
							if (op.id == 9 || op.id == 10 || op.id == 11 || op.id == 8) {
								dame += dame * (op.getParam(0) / 100) / 100;
							}
						}
					}
				}
				if (conn.p.type_use_mount == 3) {
					dame = (dame * 12) / 10;
				} else if (conn.p.type_use_mount == 5) {
					dame = (dame * 14) / 10;
				} else if (conn.p.type_use_mount == 11 || conn.p.type_use_mount == 12) {
					dame = (dame * 11) / 10;
				} else if ((conn.p.type_use_mount == 20 && conn.p.id_horse == 114)
				      || (conn.p.type_use_mount == 22 && conn.p.id_horse == 117)) {
					dame = (dame * 115) / 100;
				} else if ((conn.p.type_use_mount == 20 && conn.p.id_horse == 116)) {
					dame = (dame * 135) / 100;
				}
				ef = conn.p.get_eff(53);
				if (ef != null && conn.p.hp < conn.p.body.get_max_hp()) {
					int param = conn.p.body.get_max_hp() / 100;
					if (conn.p.hp + param > conn.p.body.get_max_hp()) {
						param = conn.p.body.get_max_hp() - conn.p.hp;
					}
					Service.usepotion(conn.p, 0, param);
				}
				ef = conn.p.get_eff(3);
				if (ef != null) {
					dame = (dame / 10) * 8;
				}
				if (conn.p.getlevelpercent() < 0) {
					dame /= 2;
				}
				if (type_atk == 0) {
					for (int i = 0; i < n; ++i) {
						int n2 = Short.toUnsignedInt(m.reader().readShort());
						Mob_in_map mob_target = MapService.get_mob_by_index(map, n2);
						if (mob_target != null) {
							MapService.fire_mob(map, mob_target, conn.p, index_skill, dame);
						}
					}
				} else if (type_atk == 1) {
					for (int i = 0; i < n; ++i) {
						int n2 = Short.toUnsignedInt(m.reader().readShort());
						Player p_target = MapService.get_player_by_id(map, n2);
						if (p_target != null) {
							MapService.fire_player(map, p_target, conn.p, index_skill, dame, type);
						}
					}
				}
			}
		}
	}

	private static Player get_player_by_id(Map map, int n2) {
		for (Player p0 : map.players) {
			if (p0.id == n2) {
				return p0;
			}
		}
		return null;
	}

	private static void fire_player(Map map, Player p0, Player p, byte indexskill, int dame_atk, byte type_atk)
	      throws IOException {
		if (p0 == null || p0.id == p.id || p.map.ismaplang || p.level < 11 || p0.level < 11
		      || (p.typepk != 0 && p.typepk == p0.typepk) || p.hieuchien > 1000) {
			return;
		}
		if (p0.isdie) {
			MapService.die_by_player(map, p0, p);
			return;
		}
               
		//
		int dame = Util.random((dame_atk * 9) / 10, dame_atk);
		Message m = new Message(6);
		m.writer().writeShort(p.id);
		m.writer().writeByte(indexskill);
		m.writer().writeByte(1);
		m.writer().writeShort(p0.id);
		if (indexskill == 17) {
			MapService.add_eff_skill(map, p, p0, indexskill);
		}
		//
		if (p0.get_eff(-126) != null || p.get_eff(-126) != null) {
			return;
		}
		EffTemplate ef = null;
		int cr = p.body.get_crit();
		ef = p.get_eff(33);
		if (ef != null) {
			cr += ef.param;
		}
		boolean crit = cr > Util.random(0, 15000);
		boolean pierce = false;
		int rc_dame = p0.body.get_react_dame();
		ef = p0.get_eff(35);
		if (ef != null) {
			rc_dame += ef.param;
		}
		boolean react_dame = rc_dame > Util.random(0, 15000);
		if (crit) {
			dame *= 2;
		}
		if (!crit) {
			int pier = p.body.get_pierce();
			ef = p.get_eff(36);
			if (ef != null) {
				pier += ef.param;
			}
			if (pier > Util.random(0, 15000)) {
				pierce = true;
			}
		}
		if (!pierce) {
			int def = p0.body.get_def();
			ef = p0.get_eff(15);
			if (ef != null) {
				def += (def * (ef.param / 100)) / 100;
			}
			dame -= def;
			if (dame <= 0) {
				dame = 1;
			}
		}
		dame -= (dame * (p0.body.get_resist_dame(type_atk, p.clazz, p0) / 100)) / 100; // khang vat ly
		int miss = p0.body.get_miss();
		ef = p0.get_eff(34);
		if (ef != null) {
			miss += ef.param;
		}
		if (miss > Util.random(0, 15000)) {
			dame = 0;
			pierce = false;
			react_dame = false;
			crit = false;
		}
		if (dame < 0) {
			dame = 0;
		}
		//
		boolean affect_special_skill = 5 > Util.random(0, 100);
		if (affect_special_skill && p.clazz == 2 && type_atk == 1) {
			p0.hp_restore = (p0.hp * 2) / 10;
			p0.hp -= p0.hp_restore;
		}
		//
		p0.hp -= dame;
		          if (p0.hp <= 0) {

                p0.hp = 0;
                if (!p0.isdie) {
                    if (p0.typepk == -1) {
                        if (!p0.list_enemies.contains(p.name)) {
                            p0.list_enemies.add(p.name);
                            if (p0.list_enemies.size() > 20) {
                                p0.list_enemies.remove(0);
                            }
                        }
                    }
                    if (p.list_enemies.contains(p0.name)) {
                        p.list_enemies.remove(p0.name);
                        Service.send_notice_nobox_white(p.conn, "Báo thù thành công kkk");
                    }
                    //chet khi bat do sat
                    if (p.typepk == 0) {
                        p.update_vang(-500*p.level);
                        p.update_Exp(-500*p.level, true);
                        p.item.char_inventory(5);
                        p.hieuchien += 100;
                    }
                    MapService.die_by_player(map, p0, p);
                }
            }
		//
		m.writer().writeInt(dame); // dame
		m.writer().writeInt(p0.hp); // hp after
		//
		int dame_react = 0;
		//
		if (dame > 0 && crit) {
			if (react_dame) {
				m.writer().writeByte(2); // size color show
				//
				m.writer().writeByte(4); // 1: xuyen giap, 2:hut hp, 3: hut mp, 4: chi mang, 5: phan don
				m.writer().writeInt(dame); // par
				//
				m.writer().writeByte(5);
				m.writer().writeInt(dame);
			} else {
				m.writer().writeByte(1); // size color show
				//
				m.writer().writeByte(4); // 1: xuyen giap, 2:hut hp, 3: hut mp, 4: chi mang, 5: phan don
				m.writer().writeInt(dame); // par
				//
			}
		} else if (dame > 0 && pierce) {
			if (react_dame) {
				m.writer().writeByte(2); // size color show
				//
				m.writer().writeByte(1); // 1: xuyen giap, 2:hut hp, 3: hut mp, 4: chi mang, 5: phan don
				m.writer().writeInt(dame); // par
				//
				m.writer().writeByte(5);
				m.writer().writeInt(dame);
			} else {
				m.writer().writeByte(1); // size color show
				//
				m.writer().writeByte(1); // 1: xuyen giap, 2:hut hp, 3: hut mp, 4: chi mang, 5: phan don
				m.writer().writeInt(dame); // par
				//
			}
		} else {
			if (react_dame) {
				dame_react = dame;
				int def2 = p.body.get_def();
				ef = p.get_eff(15);
				if (ef != null) {
					def2 += (def2 * (ef.param / 100)) / 100;
				}
				long dameresist2 = (3L * def2);
				dameresist2 /= 1000L;
				dame_react -= (dame_react * (int) dameresist2) / 100;
				dame_react -= (dame_react * (p.body.get_resist_dame(type_atk, p.clazz, p) / 100)) / 100; // khang vat ly
				//
				m.writer().writeByte(2);
				m.writer().writeByte(0);
				m.writer().writeInt(dame);
				m.writer().writeByte(5);
				m.writer().writeInt(dame_react);
			} else {
				m.writer().writeByte(0);
			}
		}
		if (react_dame && dame_react > 0) {
			p.hp -= dame_react;
			if (p.hp <= 0) {
				MapService.die_by_player(map, p, p0);
			}
		}
		m.writer().writeInt(p.hp);
		m.writer().writeInt(p.mp);
		m.writer().writeByte(11);
		m.writer().writeInt(0);
		MapService.send_msg_player_inside(map, p, m, true);
		m.cleanup();
		//
		// affect_special_skill
		if (affect_special_skill) {
			int time_affect = 5000 + (p.level / 10) * 1000;
			if (type_atk == 0) { // st vl
				p0.add_eff(4, 99, time_affect);
				Eff_special_skill.send_eff(p0, 4, time_affect);
			} else if (type_atk == 1) { // st he
				if (p.clazz == 1) {
					p0.dame_affect_special_sk = (dame * 8) / 100;
				}
				p0.add_eff(p.clazz, 99, time_affect);
				Eff_special_skill.send_eff(p0, p.clazz, time_affect);
			}
			Service.send_char_main_in4(p0);
			for (int i = 0; i < map.players.size(); i++) {
				Player p2 = map.players.get(i);
				if (p2 != null && p2.id != p0.id) {
					MapService.send_in4_other_char(map, p2, p0);
				}
			}
		}
		// exp_pk
		// if (p.typepk == 0 && p0.typepk != 0) {
		// if (p0.isdie) {
		// p.update_Exp(-(Level.entrys.get(p.level - 1).exp / 5), false);
		// } else {
		// p.update_Exp(-(dame * 10), false);
		// }
		// }
	}

	public static void die_by_player(Map map, Player p, Player p0) throws IOException {
		p.dame_affect_special_sk = 0;
		p.hp = 0;
		p.isdie = true;
		MapService.change_flag(map, p, -1);
		Message m = new Message(41);
		m.writer().writeShort(p.id);
		m.writer().writeShort(p0.id);
		m.writer().writeShort(-1); // point pk
		m.writer().writeByte(1); // type main object
		MapService.send_msg_player_inside(map, p, m, true);
		m.cleanup();
		p.type_use_mount = -1;
	}

	private static void fire_mob(Map map, Mob_in_map mob, Player p, byte index_skill, int dame_atk) throws IOException {
		if (mob.isdie) {
			Message m2 = new Message(17);
			m2.writer().writeShort(p.id);
			m2.writer().writeShort(mob.index);
			p.conn.addmsg(m2);
			m2.cleanup();
			return;
		}
		//
		int dame = Util.random((dame_atk * 9) / 10, dame_atk);
		//
		EffTemplate ef = null;
		int cr = p.body.get_crit();
		ef = p.get_eff(33);
		if (ef != null) {
			cr += ef.param;
		}
		boolean crit = cr > Util.random(0, 15000);
		boolean pierce = false;
		if (crit) {
			dame *= 2;
		}
		if (!crit) {
			int pier = p.body.get_pierce();
			ef = p.get_eff(36);
			if (ef != null) {
				pier += ef.param;
			}
			if (pier > Util.random(0, 15000)) {
				pierce = true;
			}
		}
		if (!pierce) {
			long dameresist = (mob.level * mob.level / 2);
			if (mob.is_boss) {
				dameresist *= 2;
			}
			dame -= dameresist;
			if (dame <= 0) {
				dame = 1;
			}
		}
		if (mob.color_name != 0) {
			dame = (dame * 8) / 10;
			switch (mob.color_name) {
				case 1: {
					if (p.clazz == 2) {
						dame /= 2;
					}
					break;
				}
				case 2: {
					if (p.clazz == 3) {
						dame /= 2;
					}
					break;
				}
				case 4: {
					if (p.clazz == 0) {
						dame /= 2;
					}
					break;
				}
				case 5: {
					if (p.clazz == 1) {
						dame /= 2;
					}
					break;
				}
			}
		}
		if (mob.template.mob_id == 167 || mob.template.mob_id == 168 || mob.template.mob_id == 169
		      || mob.template.mob_id == 170 || mob.template.mob_id == 171 || mob.template.mob_id == 172) {
			dame = 1;
		}
		if (5 > Util.random(0, 100)) { // mob get miss
			dame = 0;
		}
		if (dame < 0 || (mob.is_boss && (Math.abs(mob.level - p.level) > 5))) {
			dame = 0;
		}
		mob.hp -= dame;
		// if mob die
		if (mob.hp <= 0) {
			mob.hp = 0;
			// mob die
			if (!mob.isdie) {
				mob.isdie = true;
				mob.time_back = System.currentTimeMillis() + (Mob_in_map.time_refresh * 1000) - 1000L;
				if (mob.is_boss) {
					mob.is_boss_active = false;
					Manager.gI().chatKTGprocess("@Server : " + p.name + " đã tiêu diệt " + mob.template.name);
					LeaveItemMap.leave_item_boss(map, mob, p);
					Mob_in_map.num_boss--;
				} else {
					// item drop
					if (Math.abs(mob.level - p.level) <= 10) {
						if (10 > Util.random(0, 300) || mob.color_name != 0) {
							LeaveItemMap.leave_item_3(map, mob, p);
						}
						if (20 > Util.random(0, 300)) {
							LeaveItemMap.leave_item_4(map, mob, p);
						}
						if (20 > Util.random(0, 300)) {
							LeaveItemMap.leave_item_7(map, mob, p);
						}
						if (30 > Util.random(0, 100)) {
							LeaveItemMap.leave_vang(map, mob, p);
						}
						if (30 > Util.random(0, 100)) {
							LeaveItemMap.leave_material(map, mob, p);
						}
					}
					if (mob.color_name != 0) {
						map.num_mob_super--;
					}
				}
				// send p outside
				Message m2 = new Message(17);
				m2.writer().writeShort(p.id);
				m2.writer().writeShort(mob.index);
				for (int i = 0; i < map.players.size(); i++) {
					Player p2 = map.players.get(i);
					if (!((Math.abs(p2.x - p.x) < 200) && (Math.abs(p2.y - p.y) < 200))) {
						p2.conn.addmsg(m2);
					}
				}
				m2.cleanup();
			}
		}
		// attached in4
		Message m = new Message(9);
		m.writer().writeShort(p.id);
		m.writer().writeByte(index_skill);
		m.writer().writeByte(1);
		m.writer().writeShort(mob.index);
		m.writer().writeInt(dame); // dame
		m.writer().writeInt(mob.hp); // hp mob after
		if (dame > 0 && crit) {
			m.writer().writeByte(1); // size color show
			//
			m.writer().writeByte(4); // 1: xuyen giap, 2:hut hp, 3: hut mp, 4: chi mang, 5: phan don
			m.writer().writeInt(dame); // par
			//
		} else if (dame > 0 && pierce) {
			m.writer().writeByte(1); // size color show
			//
			m.writer().writeByte(1); // 1: xuyen giap, 2:hut hp, 3: hut mp, 4: chi mang, 5: phan don
			m.writer().writeInt(dame); // par
			//
		} else {
			m.writer().writeByte(0);
		}
		if (!mob.list_fight.contains(p)) {
			mob.list_fight.add(p);
		}
		m.writer().writeInt(p.hp);
		m.writer().writeInt(p.mp);
		m.writer().writeByte(11); // 1: green, 5: small white 9: big white, 10: st dien, 11: st bang
		m.writer().writeInt(0); // dame plus
		MapService.send_msg_player_inside(map, p, m, true);
		m.cleanup();
		// exp
		int expup = 0;
		expup = dame; // tinh exp
                if (p.level <= 10) {
                expup = expup*3 ;
            }
                if (Math.abs(mob.level - p.level) == 0) {
                expup = expup ;
            }else if (Math.abs(mob.level - p.level) > 1) {
                expup = (expup * 11) / 10;
            }else if (Math.abs(mob.level - p.level) > 2) {
                expup = (expup * 12) / 10;
            }else if (Math.abs(mob.level - p.level) > 3) {
                expup = (expup * 13) / 10;
            }else if (Math.abs(mob.level - p.level) > 4) {
                expup = (expup * 14) / 10;
            }else if (Math.abs(mob.level - p.level) > 5) {
                expup = (expup * 15) / 10;
            }else if (Math.abs(p.level- mob.level) > 1) {
                expup = (expup * 9) / 10;
            }else if (Math.abs(p.level- mob.level) > 2) {
                expup = (expup * 8) / 10;
            }else if (Math.abs(p.level- mob.level) > 3) {
                expup = (expup * 7) / 10;
            }else if (Math.abs(p.level- mob.level) > 4) {
                expup = (expup * 6) / 10;
            }else if (Math.abs(p.level- mob.level) > 5) {
                expup = (expup * 5) / 10;
            }
                if (p.hieuchien > 0) {
                    expup/=2;
                }
		if (Math.abs(mob.level - p.level) <= 10 && expup > 0) {
			if (p.party != null) {
				for (int i = 0; i < p.party.get_mems().size(); i++) {
					Player pm = p.party.get_mems().get(i);
					if (pm.id != p.id && (Math.abs(pm.level - p.level) < 10) && pm.map.map_id == p.map.map_id
					      && pm.map.zone_id == p.map.zone_id) {
						pm.update_Exp((expup / 10), true);
					}
				}
			}
			ef = p.get_eff(-125);
			if (ef != null) {
				expup += (expup * (ef.param / 100)) / 100;
			}
			p.update_Exp(expup, true);
		} else if (expup > 0) {
			p.update_Exp(2, false);
		}
		// exp clan
		if (p.myclan != null) {
			int exp_clan = dame / 10_000;
			if (exp_clan < 1 || exp_clan > 50) {
				exp_clan = 1;
			}
			p.myclan.update_exp(exp_clan);
		}
		// pet attack
		if (!mob.isdie && p.pet_follow) {
			Pet my_pet = null;
			for (Pet pett : p.mypet) {
				if (pett.is_follow) {
					my_pet = pett;
					break;
				}
			}
			if (my_pet != null && my_pet.grown > 0) {
				int a1 = 0;
				int a2 = 1;
				for (Option_pet temp : my_pet.op) {
					if (temp.maxdam > 0) {
						a1 = temp.param;
						a2 = temp.maxdam;
						break;
					}
				}
				int dame_pet = Util.random(a1, a2 + 1) - (mob.level * 15);
				if (dame_pet <= 0) {
					dame_pet = 1;
				}
				if (((mob.hp - dame_pet) > 0) && (p.pet_atk_speed < System.currentTimeMillis()) && (a2 > 1)) {
					p.pet_atk_speed = System.currentTimeMillis() + 1500L;
					m = new Message(84);
					m.writer().writeByte(2);
					m.writer().writeShort(p.id);
					m.writer().writeByte(1);
					m.writer().writeByte(1);
					m.writer().writeShort(mob.index);
					m.writer().writeInt(dame_pet);
					mob.hp -= dame_pet;
					m.writer().writeInt(mob.hp);
					m.writer().writeInt(p.hp);
					m.writer().writeInt(p.body.get_max_hp());
					p.conn.addmsg(m);
					m.cleanup();
				}
			}
		}
	}
}
