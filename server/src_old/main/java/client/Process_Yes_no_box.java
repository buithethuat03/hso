package client;

import core.GameSrc;
import java.io.IOException;
import core.Manager;
import core.Service;
import io.Message;
import io.Session;
import map.Dungeon;
import map.MapService;
import template.EffTemplate;
import template.Item3;
import template.Item47;
import template.Option;

public class Process_Yes_no_box {

    public static void process(Session conn, Message m) throws IOException {
        short id = m.reader().readShort(); // id
        if (id != conn.p.id) {
            return;
        }
        byte type = m.reader().readByte(); // type
        byte value = m.reader().readByte(); // value
        if (value != 1) {
            switch (type) {
                case 115: {
                    conn.p.id_remove_time_use = -1;
                    break;
                }
                case 120: {
                    Service.send_notice_box(conn, "Cần 20k ngọc!");
                    break;
                }
                case 126: {
                    conn.p.id_buffer_126 = -1;
                    conn.p.id_index_126 = -1;
                    break;
                }
                case 114: {
                    conn.p.id_wing_split = -1;
                    break;
                }
            }
        } else {
            switch (type) {
                case 114: {
                    Item3 item = null;
                    int count = 0;
                    for (int i = 0; i < conn.p.item.bag3.length; i++) {
                        Item3 it = conn.p.item.bag3[i];
                        if (it != null && it.type == 7 && it.tier > 0) {
                            if (count == conn.p.id_wing_split) {
                                item = it;
                                break;
                            }
                            count++;
                        }
                    }
                    if (item != null) {

                        int quant1 = 40;
                        int quant2 = 10;
                        int quant3 = 50;
                        for (int i = 0; i < item.tier; i++) {
                            quant1 += GameSrc.wing_upgrade_material_long_khuc_xuong[i];
                            quant2 += GameSrc.wing_upgrade_material_kim_loai[i];
                            quant3 += GameSrc.wing_upgrade_material_da_cuong_hoa[i];
                            if ((i + 1) == 10 || (i + 1) == 20 || (i + 1) == 30) {
                                item.part--;
                            }
                        }
                        if (item.tier > 15) {
                            quant1 /= 2;
                            quant2 /= 2;
                            quant3 /= 2;
                        } else {
                            quant1 /= 3;
                            quant2 /= 3;
                            quant3 /= 3;
                        }
                        short[] id_ = new short[]{8, 9, 10, 11, 3, 0};
                        int[] quant_ = new int[]{quant1, quant1, quant1, quant1, quant2, quant3};
                        for (int i = 0; i < id_.length; i++) {
                            Item47 it = new Item47();
                            it.category = 7;
                            it.id = id_[i];
                            it.quantity = (short) quant_[i];
                            conn.p.item.add_item_bag47(7, it);
                        }
                        //
//                                                item = null;
                        count = 0;
                        for (int i = 0; i < conn.p.item.bag3.length; i++) {

                            Item3 it = conn.p.item.bag3[i];
                            if (it != null && it.type == 7 && it.tier > 0) {
                                if (count == conn.p.id_wing_split) {
                                    conn.p.item.bag3[i] = null;
                                    break;
                                }
                                count++;
                            }
                        }
                        conn.p.id_wing_split = -1;
//                                                for (int i = 0; i < item.op.size(); i++) {
//                                                if ((item.op.get(i).id > 26 || item.op.get(i).id < 23)
//							      && (item.op.get(i).id != 41 && item.op.get(i).id != 42)) {
//                                                    item.op.remove(i--);
//                                                }
//                                            }
                        //
                        conn.p.item.char_inventory(4);
                        conn.p.item.char_inventory(7);
                        conn.p.item.char_inventory(3);
                        Service.send_notice_box(conn, "Thành công");
                    } else {
                        Service.send_notice_box(conn, "Có lỗi xảy ra, hãy thử lại!");
                        conn.p.id_wing_split = -1;
                    }
                    break;
                }
                case 9: {
                    if (conn.p.get_ngoc() >= 5) {
                        conn.p.isdie = false;
                        conn.p.hp = conn.p.body.get_max_hp();
                        conn.p.mp = conn.p.body.get_max_mp();
                        conn.p.update_ngoc(-5);
                        conn.p.item.char_inventory(5);
                        Service.send_char_main_in4(conn.p);
                        // chest in4
                        Service.send_combo(conn);
                        Service.usepotion(conn.p, 0, conn.p.body.get_max_hp());
                        Service.usepotion(conn.p, 1, conn.p.body.get_max_mp());
                    } else {
                        Service.send_notice_box(conn, "Không đủ ngọc để thực hiện");
                    }
                    break;
                }
                case 86: {
                    Manager.gI().vxmm.send_in4(conn.p);
                    break;
                }
                case 115: {
                    if (conn.p.id_remove_time_use != -1) {
                        Item3 it = conn.p.item.bag3[conn.p.id_remove_time_use];
                        if (it != null && it.time_use > 0) {
                            int ngoc_ = conn.p.get_ngoc();
                            if (ngoc_ > 4) {
                                long price = it.time_use - System.currentTimeMillis();
                                price /= 30_600_000;
                                price = (price > 4) ? (price + 1) : 5;
                                boolean ch = false;
                                if (ngoc_ >= price) {
                                    ch = true;
                                } else {
                                    price = ngoc_;
                                }
                                it.time_use -= (price * 30_600_000);
                                conn.p.update_ngoc(-price);
                                conn.p.item.char_inventory(4);
                                conn.p.item.char_inventory(7);
                                conn.p.item.char_inventory(3);
                                conn.p.id_remove_time_use = -1;
                                if (ch) {
                                    Service.send_notice_box(conn, "Nhận được " + it.name + " +" + it.tier + "!");
                                }
                            } else {
                                Service.send_notice_box(conn, "Tối thiểu 5 ngọc!");
                            }
                        }
                    }
                    break;
                }
                case 116: {
                    if (conn.p.myclan != null && conn.p.myclan.mems.get(0).name.equals(conn.p.name)) {
                        conn.p.myclan.remove_all_mem();
                        conn.p.myclan.remove_mem(conn.p.name);
                        conn.p.myclan = null;
                        MapService.update_in4_2_other_inside(conn.p.map, conn.p);
                        MapService.send_in4_other_char(conn.p.map, conn.p, conn.p);
                        Service.send_char_main_in4(conn.p);
                        Service.send_notice_box(conn, "Hủy bang thành công");
                    }
                    break;
                }
                case 117: {
                    if (conn.p.myclan != null) {
                        conn.p.myclan.remove_mem(conn.p.name);
                        conn.p.myclan = null;
                        MapService.update_in4_2_other_inside(conn.p.map, conn.p);
                        MapService.send_in4_other_char(conn.p.map, conn.p, conn.p);
                        Service.send_char_main_in4(conn.p);
                        Service.send_notice_box(conn, "Rời bang thành công");
                    }
                    break;
                }
                case 118: {
                    if (conn.p.myclan.mems.get(0).name.equals(conn.p.name)) {
                        if ((Clan.vang_upgrade[1] * conn.p.myclan.level) > conn.p.myclan.get_vang()) {
                            Service.send_notice_box(conn, "Không đủ vàng để thực hiện");
                            return;
                        }
                        if ((Clan.ngoc_upgrade[1] * conn.p.myclan.level) > conn.p.myclan.get_ngoc()) {
                            Service.send_notice_box(conn, "Không đủ ngọc để thực hiện");
                            return;
                        }
                        conn.p.myclan.update_vang(- Clan.vang_upgrade[1] * conn.p.myclan.level);
                        conn.p.myclan.update_ngoc(- Clan.ngoc_upgrade[1] * conn.p.myclan.level);
                        conn.p.myclan.level++;
                        conn.p.myclan.exp = 0;
                        if (conn.p.myclan.level % 5 == 0) {
                            conn.p.myclan.max_mem += 5;
                        }
                        Service.send_notice_box(conn, "Nâng bang lên cấp " + conn.p.myclan.level + " thành công");
                    }
                    break;
                }
                case 119: {
                    if (conn.p.dungeon == null) {
                        conn.p.dungeon = new Dungeon(48);
                        conn.p.dungeon.setMode(0);
                        conn.p.goto_dungeon(conn.p, 48);
                        conn.p.point_active[0]--;
                        String name = conn.p.name;
                        if (conn.p.party != null) {
                            for (int i = 1; i < conn.p.party.get_mems().size(); i++) {
                                Player p0 = conn.p.party.get_mems().get(i);
                                name += ", " + p0.name;
                                if (p0.dungeon == null) {
                                    p0.dungeon = conn.p.dungeon;
                                    p0.goto_dungeon(p0, 48);
                                    p0.point_active[0]--;
                                }
                            }
                        }
                        conn.p.dungeon.name_party = name;
                    }
                    break;
                }
                case 120: {
                    if (conn.p.get_ngoc() < 20000) {
                        Service.send_notice_box(conn, "20k ngọc còn không có thì đéo xứng đáng làm anh hùng!");
                        return;
                    }
                    Message m12 = new Message(-53);
                    m12.writer().writeShort(0);
                    String[] txt = new String[]{"Tên (4-20 ký tự) :", "Tên viết tắt (3 ký tự) :"};
                    m12.writer().writeByte(txt.length);
                    for (String string : txt) {
                        m12.writer().writeUTF(string);
                    }
                    m12.writer().writeUTF("Đăng ký bang");
                    m12.writer().writeUTF("Bang hội");
                    conn.addmsg(m12);
                    m12.cleanup();
                    break;
                }
                case 121: {
                    if (conn.p.get_ngoc() < 500) {
                        Service.send_notice_box(conn, "Không đủ 500 ngọc!");
                        return;
                    }
                    conn.p.update_ngoc(-500);
                    conn.p.item.char_inventory(5);
                    EffTemplate ef = conn.p.get_eff(-126);
                    if (ef != null) {
                        long time_extra = (ef.time - System.currentTimeMillis()) + (1000 * 60 * 60 * 2);
                        if (time_extra > (1000 * 60 * 60 * 24 * 3)) {
                            time_extra = 1000 * 60 * 60 * 24 * 3;
                        }
                        conn.p.add_eff(-126, 99, (int) time_extra);
                    } else {
                        conn.p.add_eff(-126, 99, (1000 * 60 * 60 * 2));
                    }
                    Service.send_notice_box(conn, "Đăng ký thành công");
                    break;
                }
                case 122: {
                    int fee = 100 * conn.p.item.bag3[conn.p.item_replace].tier;
                    if (conn.p.get_ngoc() < fee) {
                        Service.send_notice_box(conn, "Không đủ " + fee + " ngọc!");
                        return;
                    }
                    conn.p.item.bag3[conn.p.item_replace2].tier = conn.p.item.bag3[conn.p.item_replace].tier;
                    conn.p.item.bag3[conn.p.item_replace].tier = 0;
                    if (conn.p.item.bag3[conn.p.item_replace2].type == 5
                            && conn.p.item.bag3[conn.p.item_replace2].tier >= 9) {
                        for (Option op_ : conn.p.item.bag3[conn.p.item_replace2].op) {
                            if (op_.id == 37 && op_.getParam(conn.p.item.bag3[conn.p.item_replace2].tier) > 1) {
                                op_.setParam(op_.getParam(conn.p.item.bag3[conn.p.item_replace2].tier));
                            }
                        }
                    }
                    conn.p.update_ngoc(-fee);
                    conn.p.item.char_inventory(3);
                    Service.send_notice_box(conn, "Chuyển hóa thành công!");
                    //
                    Message m3 = new Message(73);
                    m3.writer().writeByte(0);
                    m3.writer().writeShort(conn.p.item_replace2);
                    m3.writer().writeByte(0);
                    conn.addmsg(m3);
                    m3.cleanup();
                    //
                    m3 = new Message(73);
                    m3.writer().writeByte(0);
                    m3.writer().writeShort(conn.p.item_replace);
                    m3.writer().writeByte(1);
                    conn.addmsg(m3);
                    m3.cleanup();
                    //
                    break;
                }
                case 123: {
                    break;
                }
                case 124: {
                    conn.p.rest_skill_point();
                    conn.p.item.remove(4, 7, 1);
                    conn.p.item.char_inventory(4);
                    Service.send_notice_box(conn, "Tẩy điểm kỹ năng thành công");
                    break;
                }
                case 125: {
                    conn.p.rest_potential_point();
                    conn.p.item.remove(4, 6, 1);
                    conn.p.item.char_inventory(4);
                    Service.send_notice_box(conn, "Tẩy điểm tiềm năng thành công");
                    break;
                }
                case 126: {
                    if (conn.p.id_buffer_126 != -1) {
                        Item3 temp3 = conn.p.item.bag3[conn.p.id_buffer_126];
                        temp3.islock = true;
                        switch (temp3.type) {
                            case 0: // coat
                            case 1: // pant
                            case 2: // crown
                            case 3: // grove
                            case 4: // ring
                            case 5: // chain
                            case 6: // shoes
                            case 7: // wing
                            case 15:
                            case 8:
                            case 9:
                            case 10:
                            case 16:
                            case 11: { // weapon
                                conn.p.player_wear(conn, temp3, conn.p.id_buffer_126, conn.p.id_index_126);
                                break;
                            }
                            default: {
                                Service.send_notice_nobox_white(conn, "3Chưa có chức năng này");
                                break;
                            }
                        }
                    }
                    conn.p.id_buffer_126 = -1;
                    conn.p.id_index_126 = -1;
                    break;
                }
            }
        }
    }
}
