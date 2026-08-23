package map;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import client.Player;
import core.MenuController;
import core.Service;
import core.Util;
import io.Message;
import io.Session;
import template.EffTemplate;
import template.LvSkill;
import template.Mob;
import template.Mob_Dungeon;
import template.Option;

public class Dungeon implements Runnable {
	public static List<BXH_Dungeon_Finished> bxh_time_complete = new ArrayList<>();
	public Thread mapthread;
	public boolean running;
	public Map template;
	public List<Mob_Dungeon> mobs;
	public List<Player> players;
	public List<Vgo> vgos;
	public ItemMap[] item_map;
	public long time_live;
	public int state;
	private int mob_speed;
	private int wave;
	private int num_mob;
	private int num_mob_max;
	private int dame_buff;
	private int id_mob_in_wave;
	public int index_mob;
	private int hp;
	public String name_party;
	private byte mode_now;

	public Dungeon(int id) {
		template = Map.get_map_dungeon(id);
		this.item_map = new ItemMap[100];
		mapthread = new Thread(this);
		mapthread.start();
		this.players = new ArrayList<>();
		this.vgos = new ArrayList<>();
		time_live = System.currentTimeMillis();
		mobs = new ArrayList<>();
		state = -1;
		wave = 1;
		// num_boss = 0;
		// index_mob = Mob_in_map.mob_in_map.size();
		name_party = "";
	}

	public void finish_dungeon() {
		running = false;
		mapthread.interrupt();
	}

	@Override
	public void run() {
		running = true;
		long time1 = 0;
		long time2 = 0;
		long time3 = 0;
		while (running) {
			try {
				time1 = System.currentTimeMillis();
				update();
				time2 = System.currentTimeMillis();
				time3 = (1000L - (time2 - time1));
				if (time3 > 0) {
					Thread.sleep(time3);
				}
			} catch (InterruptedException e) {
			}
		}
	}

	private void update() {
		if (this.index_mob > 65_000) {
			this.state = 7;
		}
		int time_now = getTime_live();
		if (time_now > 30 && players.size() < 1) {
			running = false;
		} else {
			// System.out.println(this.state);
			try {
				if (this.state == -1) {
					if (time_now == 4) {
						this.state = 0;
						notice_all_player_in_dungeon("Hiệp 1 sẽ bắt đầu sau 10s");
					}
				} else if (this.state == 0) { // wait to begin
					if (time_now == 13) {
						this.state = 1;
					}
				} else if (this.state == 1) { // wait at wave 20
					if (wave != 20) {
						int dem = 0;
						if (mobs.size() > 0) {
							for (Mob_Dungeon mob_temp : mobs) {
								for (Player p0 : this.players) {
									Message m2 = new Message(17);
									m2.writer().writeShort(p0.id);
									m2.writer().writeShort(mob_temp.index_mob);
									p0.conn.addmsg(m2);
									m2.cleanup();
									dem++;
									if (dem > 10_000) {
										break;
									}
								}
								if (dem > 10_000) {
									break;
								}
							}
						}
						this.state = 2;
						notice_all_player_in_dungeon("Hiệp " + (wave++) + " bắt đầu");
						this.num_mob = num_mob_max;
						mobs.clear();
						if (wave <= 198) {
							id_mob_in_wave = wave - 1;
						} else {
							id_mob_in_wave = Util.random(0, 198);
						}
					} else {
						notice_all_player_in_dungeon("Vượt thành công, xin chúc mừng các đại hiệp");
						this.num_mob = num_mob_max;
						mobs.clear();
						this.state = 8;
						// cal prime
						synchronized (Dungeon.bxh_time_complete) {
							BXH_Dungeon_Finished bxh = new BXH_Dungeon_Finished(this.name_party, this.getTime_live());
							Dungeon.bxh_time_complete.add(bxh);
							//
							List<BXH_Dungeon_Finished> buffer = new ArrayList<>();
							List<Integer> list_sorted = new ArrayList<>();
							for (BXH_Dungeon_Finished temp : Dungeon.bxh_time_complete) {
								list_sorted.add(Integer.valueOf(temp.time));
							}
							Collections.sort(list_sorted);
							Set<String> check = new HashSet<>();
							for (int i = 0; i < list_sorted.size(); i++) {
								for (int i1 = 0; i1 < Dungeon.bxh_time_complete.size(); i1++) {
									BXH_Dungeon_Finished temp = Dungeon.bxh_time_complete.get(i1);
									if (temp.time == list_sorted.get(i)) {
										if (!check.contains(temp.name)) {
											buffer.add(temp);
											check.add(temp.name);
										}
										Dungeon.bxh_time_complete.remove(temp);
										break;
									}
								}
								if (buffer.size() > 9) {
									break;
								}
							}
							Dungeon.bxh_time_complete.clear();
							Dungeon.bxh_time_complete.addAll(buffer);
							buffer.clear();
							check.clear();
						}
					}
				} else if (this.state == 2) { // fight
					if (mobs.size() < num_mob_max) {
						Mob_Dungeon mob = new Mob_Dungeon(this.index_mob++, Mob.entrys.get(id_mob_in_wave));
						mob.x = 1308;
						mob.y = 672 + Util.random(-25, 25);
						mob.from_gate = 2;
						if (this.wave >= 21) {
							mob.hpmax = mob.template.hpmax * (this.wave / 10);
						} else {
							mob.hpmax = mob.template.hpmax;
						}
						mob.hp = mob.hpmax;
						mobs.add(mob);
						Message m = new Message(4);
						m.writer().writeByte(1);
						m.writer().writeShort(mob.template.mob_id);
						m.writer().writeShort(mob.index_mob); // index
						m.writer().writeShort(mob.x);
						m.writer().writeShort(mob.y);
						m.writer().writeByte(-1);
						for (int i = 0; i < players.size(); i++) {
							Player p0 = players.get(i);
							p0.conn.addmsg(m);
						}
						m.cleanup();
					}
					update_mob();
				} else if (this.state == 4) {
					// this.state = 6;
				} else if (this.state == 5) {
					for (int i = 0; i < mobs.size(); i++) {
						Mob_Dungeon mob = mobs.get(i);
						Message m2 = new Message(17);
						m2.writer().writeShort(-1);
						m2.writer().writeShort(mob.index_mob);
						for (int i2 = 0; i2 < players.size(); i2++) {
							Player p0 = players.get(i2);
							p0.conn.addmsg(m2);
						}
						m2.cleanup();
					}
					mobs.clear();
					for (int i2 = 0; i2 < players.size(); i2++) {
						Player p0 = players.get(i2);
						Service.send_notice_box(p0.conn,
						      "Thất bại:\n Kết quả : thời gian vượt ải : " + getTime_live() + "s\nTự thoát sau 5s");
					}
					notice_all_player_in_dungeon("Thiên thạch đã vỡ, vượt phó bản thất bại!");
					this.state = 6;
				} else if (this.state == 6) {
					this.state = 7;
					//
					try {
						Thread.sleep(5000L);
					} catch (InterruptedException e) {
						e.printStackTrace();
						running = false;
					}
				} else if (this.state == 7) {
					Vgo vgo = new Vgo();
					vgo.id_map_go = 1;
					vgo.x_new = 432;
					vgo.y_new = 354;
					for (int i = 0; i < players.size(); i++) {
						Player p0 = players.get(i);
						leave_map(p0);
						p0.change_map(p0, vgo);
						p0.dungeon = null;
					}
				} else if (this.state >= 8 && this.state <= 38) {
					if (this.state == 38) {
						this.state = -2;
					}
					this.state++;
				} else if (this.state == -2) {
					this.state = 7;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void notice_all_player_in_dungeon(String s) throws IOException {
		Message m = new Message(53);
		m.writer().writeUTF(s);
		m.writer().writeByte(0);
		for (int i = 0; i < players.size(); i++) {
			Player p0 = players.get(i);
			p0.conn.addmsg(m);
		}
		m.cleanup();
	}

	private void update_mob() throws IOException {
		for (int i = 0; i < mobs.size(); i++) {
			Mob_Dungeon mob = mobs.get(i);
			if (!mob.isdie) {
				mob_act(mob);
			}
		}
	}

	private void mob_act(Mob_Dungeon mob) throws IOException {
		int distance = Util.random(1, mob_speed);
		boolean is_atk = true;
		switch (mob.from_gate) {
			case 1: {
				if (!(Math.abs((mob.y + distance) - 672) < mob_speed)) {
					mob.y += distance;
					is_atk = false;
				}
				break;
			}
			case 2: {
				if (!(Math.abs((mob.x - distance) - 684) < mob_speed)) {
					mob.x -= distance;
					is_atk = false;
				}
				break;
			}
			case 3: {
				if (!(Math.abs((mob.y - distance) - 672) < mob_speed)) {
					mob.y -= distance;
					is_atk = false;
				}
				break;
			}
			case 4: {
				if (!(Math.abs((mob.x + distance) - 684) < mob_speed)) {
					mob.x += distance;
					is_atk = false;
				}
				break;
			}
		}
		if (is_atk) {
			int dame = (mob.hpmax / 100) * (mob.template.level / 2);
			dame = Util.random((dame * 9) / 10, (dame + 1));
			dame += this.wave * 10;
			dame *= dame_buff;
			Message m = new Message(10);
			m.writer().writeByte(1);
			m.writer().writeShort(mob.index_mob);
			m.writer().writeInt(mob.hp);
			m.writer().writeByte(2);
			m.writer().writeByte(1);
			m.writer().writeShort(32001); // index thien thach
			m.writer().writeInt(dame); // dame mob
			this.hp -= dame;
			if (this.hp <= 0) {
				this.hp = 0;
				this.state = 5;
			}
			m.writer().writeInt(this.hp);
			m.writer().writeByte(6); // id skill mob
			m.writer().writeByte(0);
			for (int i = 0; i < players.size(); i++) {
				Player p0 = players.get(i);
				p0.conn.addmsg(m);
			}
			m.cleanup();
		} else {
			Message m = new Message(4);
			m.writer().writeByte(1);
			m.writer().writeShort(mob.template.mob_id);
			m.writer().writeShort(mob.index_mob); // index
			m.writer().writeShort(mob.x);
			m.writer().writeShort(mob.y);
			m.writer().writeByte(-1);
			for (int i = 0; i < players.size(); i++) {
				Player p0 = players.get(i);
				p0.conn.addmsg(m);
			}
			m.cleanup();
		}
	}

	public int getTime_live() {
		return (int) ((System.currentTimeMillis() - time_live + 2) / 1000);
	}

	public void enter_map(Player p) throws IOException {
		synchronized (this.players) {
			this.players.add(p);
		}
		// if (!Util.is_same_day(Date.from(Instant.now()), p.date)) {
		// // diem danh
		// p.diemdanh = 1;
		// //
		// p.date = Date.from(Instant.now());
		// }
		//
		send_map_data(p);
		Service.send_char_main_in4(p);
		Service.send_combo(p.conn);
		Service.send_point_pk(p);
		Service.send_health(p);
		Service.send_wear(p);
		if (p.party != null) {
			p.party.sendin4();
		}
	}

	private void send_map_data(Player p) throws IOException {
		Message m = new Message(12);
		m.writer().writeShort(this.template.map_id);
		m.writer().writeShort((short) (p.x / 24));
		m.writer().writeShort((short) (p.y / 24));
		m.writer().write(Util.loadfile("data/mapnew/" + this.template.map_id));
		m.writer().writeByte(this.template.zone_id); // zone
		m.writer().writeByte(this.template.typemap);
		m.writer().writeBoolean(this.template.ismaplang);
		m.writer().writeBoolean(this.template.showhs);
		p.conn.addmsg(m);
		m.cleanup();
		// send npc;
		for (int i = 0; i < this.template.npc_name_data.length; i++) {
			m = new Message(-50);
			m.writer().write(Util.loadfile("data/npc/" + this.template.npc_name_data[i]));
			p.conn.addmsg(m);
			m.cleanup();
		}
		// weather
		m = new Message(76);
		m.writer().writeByte(4);
		m.writer().writeShort(-1);
		m.writer().writeShort(-1);
		m.writer().writeByte(4);
		p.conn.addmsg(m);
		m.cleanup();
		// thien thach
		m = new Message(4);
		m.writer().writeByte(2);
		m.writer().writeShort(0);
		m.writer().writeShort(32001); // index
		m.writer().writeShort(684);
		m.writer().writeShort(672);
		m.writer().writeByte(-1);
		p.conn.addmsg(m);
		m.cleanup();
	}

	public void leave_map(Player p) throws IOException {
		Message m = new Message(8);
		m.writer().writeShort(p.id);
		for (int i = 0; i < players.size(); i++) {
			Player p0 = players.get(i);
			if (p0.id != p.id) {
				p0.conn.addmsg(m);
			}
		}
		m.cleanup();
		synchronized (this.players) {
			this.players.remove(p);
		}
		p.other_player_inside.clear();
		p.other_mob_inside.clear();
		p.other_mob_inside_update.clear();
	}

	public void send_move(Player p, Message m2) throws IOException {
		p.x = m2.reader().readShort();
		p.y = m2.reader().readShort();
		//
		Message m = new Message(4);
		m.writer().writeByte(0);
		m.writer().writeShort(0);
		m.writer().writeShort(p.id);
		m.writer().writeShort(p.x);
		m.writer().writeShort(p.y);
		m.writer().writeByte(-1);
		//
		// update_inside_player(m, p);
		for (int i = 0; i < players.size(); i++) {
			Player p0 = players.get(i);
			if (p.id != p0.id) {
				p0.conn.addmsg(m);
			}
		}
		//
		m.cleanup();
	}

	public Player get_player_by_name(String name) {
		for (int i = 0; i < players.size(); i++) {
			Player p0 = players.get(i);
			if (p0.name.equals(name)) {
				return p0;
			}
		}
		return null;
	}

	public void request_livefromdie(Session conn, Message m) throws IOException {
		byte type = m.reader().readByte();
		if (type == 1) { // hsl
			if (conn.p.get_ngoc() >= 5) {
				conn.p.update_ngoc(-5);
				conn.p.item.char_inventory(5);
			} else {
				Service.send_notice_box(conn, "Không đủ ngọc để thực hiện");
				return;
			}
		}
		conn.p.isdie = false;
		conn.p.hp = conn.p.body.get_max_hp();
		conn.p.mp = conn.p.body.get_max_mp();
		Service.send_char_main_in4(conn.p);
		// chest in4
		Service.send_combo(conn);
		Service.usepotion(conn.p, 0, conn.p.body.get_max_hp());
		Service.usepotion(conn.p, 1, conn.p.body.get_max_mp());
	}

	public void send_chat(Session conn, Message m2) throws IOException {
		String chat = m2.reader().readUTF();
		if (conn.user.equals("admin") && chat.equals("admin")) {
			MenuController.send_menu_select(conn, 126, new String[] {"Bảo trì", "Cộng vàng x1.000.000.000",
			      "Cộng ngọc x1.000.000", "Update BXH", "Lấy item", "Up level", "Set Xp"});
		} else if (conn.user.equals("admin") && chat.equals("xem")) {
			int num = 0;
			for (Map[] maps : Map.entrys) {
				for (Map map : maps) {
					num += map.players.size();
				}
			}
			Service.send_notice_box(conn,
			      "Vị Trí " + conn.p.x + " - " + conn.p.y + "\n Map id : " + this.template.map_id + "\n Zone : "
			            + this.template.zone_id + "\n Số Người kết nối : " + Session.client_entrys.size()
			            + "\n Số Người online : " + num);
		} else {
			Message m = new Message(27);
			m.writer().writeShort(conn.p.id);
			m.writer().writeByte(0);
			m.writer().writeUTF(chat);
			for (int i = 0; i < players.size(); i++) {
				Player p0 = players.get(i);
				if (conn.p.id != p0.id) {
					p0.conn.addmsg(m);
				}
			}
			m.cleanup();
		}
	}

	public void send_in4_npc(Session conn, Message m2) throws IOException {
		short type = m2.reader().readShort();
		if (type == 32001) {
			Message m = new Message(-44);
			m.writer().writeShort(32001); // index
			m.writer().writeUTF("Thiên thạch");
			m.writer().writeInt(this.hp);
			m.writer().writeInt(this.hp);
			m.writer().writeShort(0);
			m.writer().writeShort(684);
			m.writer().writeShort(672);
			m.writer().writeByte(2);
			m.writer().writeByte(2);
			m.writer().writeUTF("");
			m.writer().writeShort(25);
			m.writer().writeByte(1);
			m.writer().writeShort(17);
			conn.addmsg(m);
			m.cleanup();
		}
	}

	public void send_mob_in4(Session conn, int index) throws IOException {
		int index_ = -1;
		for (int i = 0; i < mobs.size(); i++) {
			Mob_Dungeon mob = mobs.get(i);
			if (mob.index_mob == index) {
				index_ = i;
				break;
			}
		}
		if (index_ > -1) {
			Mob_Dungeon temp = mobs.get(index_);
			Message m = new Message(7);
			m.writer().writeShort(index);
			m.writer().writeByte(temp.template.level);
			m.writer().writeShort(temp.x);
			m.writer().writeShort(temp.y);
			m.writer().writeInt(temp.hp);
			m.writer().writeInt(temp.hpmax);
			m.writer().writeByte(20); // id skill monster (Spec: 32, ...)
			m.writer().writeInt(-4);
			m.writer().writeShort(temp.x);
			m.writer().writeShort(temp.y);
			m.writer().writeShort(-1); // clan monster
			m.writer().writeByte(1);
			m.writer().writeByte(this.mob_speed / 25); // speed
			m.writer().writeByte(0);
			m.writer().writeUTF("");
			m.writer().writeLong(-11111);
			m.writer().writeByte(0); // color name 1: blue, 2: yellow
			conn.addmsg(m);
			m.cleanup();
		}
	}

	public synchronized void use_skill(Session conn, Message m) throws IOException {
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
		if (conn.p.mp - temp.mpLost < 0) {
			Service.send_notice_nobox_white(conn, "Không đủ mp");
			return;
		}
		conn.p.mp -= temp.mpLost;
		if (conn.p.time_delay_skill[index_skill] > System.currentTimeMillis()) {
			if (++conn.p.enough_time_disconnect > 5) {
				synchronized (Session.client_entrys) {
					// conn.close();
				}
			}
		} else {
			if (temp.nTarget < n || temp.LvRe > conn.p.level + 1) {
				return;
			}
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
				dame = (dame / 10) * 12;
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
			for (int i = 0; i < n; ++i) {
				int n2 = Short.toUnsignedInt(m.reader().readShort());
				fire_mob(n2, conn.p, index_skill, dame);
			}
		}
	}

	private void fire_mob(int index, Player p, byte index_skill, int dame_atk) throws IOException {
		int index_ = -1;
		for (int i = 0; i < mobs.size(); i++) {
			Mob_Dungeon mob = mobs.get(i);
			if (mob.index_mob == index) {
				index_ = i;
				break;
			}
		}
		if (index_ > -1) {
			Mob_Dungeon mob = mobs.get(index_);
			if (mob.isdie) {
				Message m2 = new Message(17);
				m2.writer().writeShort(p.id);
				m2.writer().writeShort(index);
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
			boolean crit = cr > Util.random(0, 10000);
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
				if (pier > Util.random(0, 10000)) {
					pierce = true;
				}
			}
			if (!pierce) {
				long dameresist = (mob.template.level * mob.template.level / 2);
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
			if (5 > Util.random(0, 100)) { // mob get miss
				dame = 0;
			}
			if (dame < 0) {
				dame = 0;
			}
			mob.hp -= dame;
			// if mob die
			if (mob.hp <= 0) {
				mob.hp = 0;
				// mob die
				if (!mob.isdie) {
					mob.isdie = true;
					if (--this.num_mob == 0) {
						this.state = 1;
						for (int i = 0; i < this.players.size(); i++) {
							this.players.get(i).point_active[1] += ((this.mode_now + 1) * (this.mode_now + 1));
						}
					}
					Message m2 = new Message(17);
					m2.writer().writeShort(p.id);
					m2.writer().writeShort(index);
					p.conn.addmsg(m2);
					m2.cleanup();
				}
			}
			// attached in4
			Message m = new Message(9);
			m.writer().writeShort(p.id);
			m.writer().writeByte(index_skill);
			m.writer().writeByte(1);
			m.writer().writeShort(index);
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
			m.writer().writeInt(p.hp);
			m.writer().writeInt(p.mp);
			m.writer().writeByte(11); // 1: green, 5: small white 9: big white, 10: st dien, 11: st bang
			m.writer().writeInt(0); // dame plus
			for (int i = 0; i < players.size(); i++) {
				Player p0 = players.get(i);
				p0.conn.addmsg(m);
			}
			m.cleanup();
			// exp
			int expup = 0;
			expup = (dame * mob.template.level) / 100; // tinh exp
			if (expup > 0) {
				if (p.party != null) {
					for (int i = 0; i < p.party.get_mems().size(); i++) {
						Player pm = p.party.get_mems().get(i);
						pm.update_Exp((expup / 10), true);
					}
				}
				ef = p.get_eff(-125);
				if (ef != null) {
					expup += (expup * (ef.param / 100)) / 100;
				}
				p.update_Exp(expup, true);
			}
		}
	}

	public class BXH_Dungeon_Finished {
		public String name;
		public int time;

		public BXH_Dungeon_Finished(String name, int time_live) {
			this.name = name;
			this.time = time_live;
		}
	}

	public int getWave() {
		return wave;
	}

	public void setWave(int i) {
		wave = i;
	}

	public void setMode(int mode) throws IOException {
		String name = "";
		this.mode_now = (byte) mode;
		switch (mode) {
			case 0: {
				hp = 50_000;
				mob_speed = 50;
				num_mob_max = 20;
				num_mob = num_mob_max;
				dame_buff = 1;
				name = "Easy";
				break;
			}
			case 1: {
				hp = 50_000;
				mob_speed = 75;
				num_mob_max = 30;
				num_mob = num_mob_max;
				dame_buff = 2;
				name = "Normal";
				break;
			}
			case 2: {
				hp = 50_000;
				mob_speed = 100;
				num_mob_max = 50;
				num_mob = num_mob_max;
				dame_buff = 4;
				name = "Hard";
				break;
			}
			case 3: {
				hp = 50_000;
				mob_speed = 150;
				num_mob_max = 75;
				num_mob = num_mob_max;
				dame_buff = 8;
				name = "Nightmare";
				break;
			}
			case 4: {
				hp = 50_000;
				mob_speed = 200;
				num_mob_max = 100;
				num_mob = num_mob_max;
				dame_buff = 16;
				name = "Hell";
				break;
			}
		}
		if (this.wave == 20) {
			notice_all_player_in_dungeon("Đội trưởng đã chọn độ khó " + name);
		}
	}
}
