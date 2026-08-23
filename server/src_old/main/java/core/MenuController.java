package core;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import client.Clan;
import client.Pet;
import io.Message;
import io.Session;
import java.util.ArrayList;
import java.util.List;
import map.Dungeon;
import map.Map;
import map.MapService;
import map.Mob_in_map;
import map.Vgo;
import template.EffTemplate;
import template.Item3;
import template.Item47;
import template.Medal_Material;
import template.Option_pet;
import template.Part_fashion;

public class MenuController {

    public static void request_menu(Session conn, Message m) throws IOException {
        byte idnpc = m.reader().readByte();
        if (idnpc == -43 || idnpc == -45 || idnpc == -48 || idnpc == -46) {
            Menu_ChangeZone(conn);
            return;
        }
        // create menu per id npc
        String[] menu;
        switch (idnpc) {
            case -3: { // Lisa
                menu = new String[]{"Mua bán", "Mở ly", "Thuế", "Tiền cống nạp", "Nhận quà chiến trường",
                    "Nhận quà chiếm thành", "Tặng ngọc", "Hướng dẫn tặng ngọc", "Nhận nguyên liệu"};
                break;
            }
            case -77: // Alisama
            case -5: { // Hammer
                menu = new String[]{"Chiến Binh", "Sát Thủ", "Pháp Sư", "Xạ Thủ"};
                break;
            } 
            case -4: {// Doubar
                menu = new String[]{"Chiến Binh", "Sát Thủ", "Pháp Sư", "Xạ Thủ","Thông Tin Boss"};
                break;
            }  
            case -33: { // da dich chuyen
                menu = new String[]{"Thành Phố Cảng", "Thành Phố Kho Báu", "Khu Mua Bán", "Sa Mạc", "Vực Lún", "Nghĩa Địa Cát", "Suối Ma", "Hầm Mộ Tầng 1",
                    "Hầm Mộ Tầng 3", "Rừng Cao Nguyên", "Vách Đá Cheo Leo", "Lối Lên Thượng Giới", "Đường Xuống Lòng Đất",
                    "Cổng Vào Hạ Giới", "Khu Vườn"};
                break;
            }
            case -55: { // da dich chuyen
                menu = new String[]{"Thành Phố Cảng", "Khu Mua Bán", "Mê Cung", "Mê Cung Tầng 3", "Thị Trấn Mùa Đông", "Thung lũng băng giá", "Chân núi tuyết", "Đèo băng giá", "Vực thẳm sương mù", "Trạm núi tuyết", "Thành Phố Kho Báu"};
                break;
            }
            case -10: { // da dich chuyen
                menu = new String[]{"Làng Sói Trắng", "Thành Phố Kho Báu", "Khu Mua Bán", "Hang Lửa",
                    "Rừng Ảo Giác", "Thung Lũng Kỳ Bí", "Hồ Kí Ức", "Bờ Biển", "Vực Đá", "Rặng Đá Ngầm", "Đầm Lầy",
                    "Đền Cổ", "Hang Dơi"};
                break;
            }
            case -8: {
                if (conn.p.type_exp == 1) {
                    menu = new String[]{"Cửa Hàng Tóc", "Điểm danh hằng ngày", "Đổi coin sang ngọc",
                        "Đăng ký treo chống pk", "Tgian còn lại", "Tắt nhận exp"};
                } else {
                    menu = new String[]{"Cửa Hàng Tóc", "Điểm danh hằng ngày", "Đổi coin sang ngọc",
                        "Đăng ký treo chống pk", "Tgian còn lại", "Bật nhận exp"};
                }
                break;
            }
            case -36: {
                menu = new String[]{"Cường Hóa Trang Bị", "Shop Nguyên Liệu", "Chuyển hóa", "Hợp nguyên liệu mề đay",
                    "Mề đay chiến binh", "Mề đay pháp sư", "Mề đay sát thủ", "Mề đay xạ thủ", "Nâng cấp mề đay",
                    "Đổi dòng sát thương", "Đổi dòng % sát thương"};
                break;
            }
            case -44: {
                menu = new String[]{"Nhận GiftCode", "Tháo cải trang", "Tháo cánh"};
                break;
            }
            case -32: {
                menu = new String[]{"Xem BXH Level"};
                break;
            }
            case -21: { // blackeye
                menu = new String[]{"Chiến Binh", "Sát Thủ", "Pháp Sư", "Xạ Thủ"};
                break;
            }
            case -7: {
                if (conn.user.contains("knightauto_hsr_")) {
                    menu = new String[]{"Rương giữ đồ", "Mở thêm ô hành trang", "Đăng ký tài khoản"};
                } else {
                    menu = new String[]{"Rương giữ đồ", "Mở thêm ô hành trang", "Mật khẩu rương"};
                }
                break;
            }
            case -34: { // cuop bien
                menu = new String[]{"Vòng xoay Vàng", "Vòng xoay ngọc", "Lịch sử"};
                break;
            }
            case -2: { // zoro
                if (conn.p.myclan != null) {
                    if (conn.p.myclan.mems.get(0).name.equals(conn.p.name)) {
                        menu = new String[]{"Quản lý bang", "Shop Icon","Shop Bang"};
                    } else {
                        menu = new String[]{"Kho Bang", "Góp Vàng", "Góp Ngọc", "Rời bang"};
                    }
                } else {
                    menu = new String[]{"Đăng ký bang", "Thông tin"};
                }
                break;
            }
            case -85: { // mr edgar
                menu = new String[]{"Báo Thù", "Thông tin"};
                break;
            }
            case -42: { // pet
                menu = new String[]{"Chuồng thú", "Shop thức ăn", "Shop trứng", "Tháo pet"};
                break;
            }
            case -37: {
                menu = new String[]{"Vào Ngã Tư Tử Thần", "Giới thiệu", "BXH phó bản", "Đăng ký chiếm thành", "Hướng dẫn",
                    "Xem Điểm Hiện Tại", "Nhận phần thưởng"};
                break;
            }
            case -38:
            case -40: {
                if (conn.p.dungeon != null && conn.p.dungeon.getWave() == 20) {
                    menu = new String[]{"Tiếp tục chinh phục", "Bỏ cuộc", "Hướng dẫn"};
                } else {
                    menu = new String[]{"Coming soon", "hiepsirom.com"};
                }
                break;
            }
            case -41: {
                menu = new String[]{"Tạo cánh", "Nâng cấp cánh", "Kích hoạt cánh","Tách cánh"};
                break;
            }
            case -49: {
                menu = new String[]{"Chúc Mừng"};
                break;
            }
            case -82: {
                menu = new String[]{"Về Làng"};
                break;
            }case -81: {
                menu = new String[]{"Điểm Pk"};
                break;
            }
            default: {
                menu = new String[]{"Coming soon", "hiepsirom.com"};
                break;
            }
        }
        //
        send_menu_select(conn, idnpc, menu);
    }

    public static void processmenu(Session conn, Message m) throws IOException {
        short idnpc = m.reader().readShort();
        @SuppressWarnings("unused")
        byte idmenu = m.reader().readByte();
        byte index = m.reader().readByte();
        switch (idnpc) {
            case 121: {
				Menu_TachCanh(conn, index);
				break;
			}
            case -3: { // Lisa
                Menu_Lisa(conn, index);
                break;
            }
            case -4: {
                Menu_Doubar(conn, index);
                break;
            }
            case -5: {
                Menu_Hammer(conn, index);
                break;
            }
            case -33: {
                Menu_DaDichChuyen33(conn, index);
                break;
            }
            case -55: {
                Menu_DaDichChuyen55(conn, index);
                break;
            }
            case -10: {
                Menu_DaDichChuyen10(conn, index);
                break;
            }
            case -77: {
                Menu_Alisama(conn, index);
                break;
            }
            case -8: {
                Menu_Zulu(conn, index);
                break;
            }
            case 126: {
                Menu_Admin(conn, index);
                break;
            }
            case -36: {
                Menu_PhapSu(conn, index);
                break;
            }
            case -44: {
                Menu_Miss_Anna(conn, index);
                break;
            }
            case -32: {
                Menu_BXH(conn, index);
                break;
            }
            case -21: {
                Menu_Black_Eye(conn, index);
                break;
            }
            case -7: {
                Menu_Aman(conn, index);
                break;
            }
            case -34: {
                Menu_CuopBien(conn, index);
                break;
            }
            case 125: { // vxmm
                Menu_VXMM(conn, index);
                break;
            }
            case -2: { // vxmm
                Menu_Zoro(conn, index);
                break;
            }
            case -85: { //
                Menu_Mr_Edgar(conn, index);
                break;
            }
            case 124: {
                Service.revenge(conn, index);
                break;
            }
            case 123: {
                Menu_Dungeon_Mode_Selection(conn, index);
                break;
            }
            case 122: {
                Menu_Clan_Manager(conn, index);
                break;
            }
            case -42: {
                Menu_Pet_Manager(conn, index);
                break;
            }
            case -37: {
                Menu_PhoChiHuy(conn, index);
                break;
            }
            case -38:
            case -40: {
                Menu_LinhCanh(conn, index);
                break;
            }
            case -41: {
                Menu_TienCanh(conn, index);
                break;
            }
            case -49: {
                Menu_top(conn, index);
                break;
            }
            case -82: {
                Menu_MissAnwen(conn, index);
                break;
            }case -81: {
                Menu_diempk(conn, index);
                break;
            }
            default: {
                Service.send_notice_box(conn, "Đã xảy ra lỗi");
                break;
            }
        }
    }
    private static void Menu_diempk(Session conn, byte index) throws IOException {
       switch (index) {
            case 0: {
               Service.send_notice_box(conn, "Bạn đang có "+ conn.p.hieuchien +" Điểm Pk.");
                break;
            }
           default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }
private static void Menu_MissAnwen(Session conn, byte index) throws IOException {
        Vgo vgo = null;
        switch (index) {
            case 0: {
                vgo = new Vgo();
                vgo.id_map_go = 1;
                vgo.x_new = 432;
                vgo.y_new = 354;
                conn.p.change_map(conn.p, vgo);
                break;
            }
           default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }
    private static void Menu_top(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                if (conn.p.chucphuc == 1) {
                    conn.p.chucphuc = 0;
                    int ngoc_ = Util.random(10, 500);
                    int vang_ = Util.random(1000, 10000);
                    conn.p.update_ngoc(ngoc_);
                    conn.p.update_vang(vang_);
                    conn.p.item.char_inventory(5);
                    Service.send_notice_box(conn, "Bạn đã Chúc Mừng Thành Công Nhận được " + ngoc_ + " ngọc," + vang_ + "Vàng.");
                } else {
                    Service.send_notice_box(conn, "Bạn đã Chúc Mừng hôm nay rồi");
                }
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }
    
    private static void Menu_TachCanh(Session conn, byte index) throws IOException {
		Item3 item = null;
		int count = 0;
		for (int i = 0; i < conn.p.item.bag3.length; i++) {
			Item3 it = conn.p.item.bag3[i];
			if (it != null && it.type == 7 && it.tier > 0) {
				if (count == index) {
					item = it;
					break;
				}
				count++;
			}
		}
		if (item != null) {
			conn.p.id_wing_split = index;
			int quant1 = 40;
						int quant2 = 10;
						int quant3 = 50;
			for (int i = 0; i < item.tier; i++) {
				quant1 += GameSrc.wing_upgrade_material_long_khuc_xuong[i];
				quant2 += GameSrc.wing_upgrade_material_kim_loai[i];
				quant3 += GameSrc.wing_upgrade_material_da_cuong_hoa[i];
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
			Service.send_box_input_yesno(conn, 114, "Bạn có muốn tách cánh này và nhận được: " + quant1
			      + " lông và khúc xương, " + quant2 + " kim loại, " + quant3 + " đá cường hóa?");
		}
	}

    private static void Menu_TienCanh(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                // Service.send_box_UI(conn, index);
                Service.send_msg_data(conn, 23, "create_wings");
                break;
            }
            case 1: {
                Message m2 = new Message(77);
                m2.writer().writeByte(6);
                conn.addmsg(m2);
                m2.cleanup();
                //
                m2 = new Message(77);
                m2.writer().writeByte(1);
                m2.writer().writeUTF("Nâng cấp cánh");
                conn.addmsg(m2);
                m2.cleanup();
                conn.p.is_create_wing = false;
                break;
            }
            case 3: {
				conn.p.id_wing_split = -1;
				List<String> list = new ArrayList<>();
				for (int i = 0; i < conn.p.item.bag3.length; i++) {
					Item3 it = conn.p.item.bag3[i];
					if (it != null && it.type == 7 && it.tier > 0) {
						list.add((it.name + " +" + it.tier));
					}
				}
				if (list.size() > 0) {
					String[] list_2 = new String[list.size()];
					for (int i = 0; i < list_2.length; i++) {
						list_2[i] = list.get(i);
					}
					send_menu_select(conn, 121, list_2);
				} else {
					Service.send_notice_box(conn, "Làm đéo gì có cánh mà đòi tách?");
				}
				break;
			}
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_Clan_Manager(Session conn, byte index) throws IOException {
        if (conn.p.myclan.mems.get(0).name.equals(conn.p.name)) {
            switch (index) {
                case 0: {
                    conn.p.myclan.open_box_clan(conn);
                    break;
                }
                case 1: {
                    if (conn.p.myclan.get_percent_level() >= 100) {
                        Service.send_box_input_yesno(conn, 118,
                                "Bạn có muốn nâng cấp bang lên level " + (conn.p.myclan.level + 1) + " với "
                                + (Clan.vang_upgrade[1] * conn.p.myclan.level) + " vàng và " + (conn.p.myclan.level + 1)
                                + " với " + (Clan.ngoc_upgrade[1] * conn.p.myclan.level) + " ngọc không?");
                    } else {
                        Service.send_notice_box(conn, "Chưa đủ exp để nâng cấp!");
                    }
                    break;
                }
                case 2: {
                    Service.send_box_input_yesno(conn, 116,
                            "Hãy xác nhận việc hủy bang, đừng hối hận khóc lóc xin admin khôi phục lại nhá!");
                    break;
                }
                default: {
                    Service.send_notice_box(conn, "Chưa có chức năng");
                    break;
                }
            }
        }
    }

    private static void Menu_Dungeon_Mode_Selection(Session conn, byte index) throws IOException {
        if (conn.p.dungeon != null && conn.p.dungeon.getWave() == 20) {
            if (index != 2 && conn.p.party != null && conn.p.party.get_mems().get(0).id != conn.p.id) {
                Service.send_notice_box(conn, "Chỉ có đội trưởng mới có quyền quyết định!");
                return;
            }
            conn.p.dungeon.setMode(index);
            if (conn.p.dungeon != null) {
                conn.p.dungeon.setWave(21);
                conn.p.dungeon.state = 1;
            } else {
                Service.send_notice_box(conn, "Có lỗi xảy ra, hãy thử chọn lại!");
            }
        }
    }

    private static void Menu_LinhCanh(Session conn, byte index) throws IOException {
        if (conn.p.dungeon != null && conn.p.dungeon.getWave() == 20) {
            if (index != 2 && conn.p.party != null && conn.p.party.get_mems().get(0).id != conn.p.id) {
                Service.send_notice_box(conn, "Chỉ có đội trưởng mới có quyền quyết định!");
                return;
            }
            switch (index) {
                case 0: {
                    send_menu_select(conn, 123, new String[]{"Easy", "Normal", "Hard", "Nightmare", "Hell"});
                    break;
                }
                case 1: {
                    if (conn.p.dungeon != null) {
                        conn.p.dungeon.state = 6;
                    } else {
                        Service.send_notice_box(conn, "Có lỗi xảy ra, hãy thử chọn lại!");
                    }
                    Service.send_notice_box(conn, "gà thật, éo dám đi tiếp à");
                    break;
                }
                case 2: {
                    Service.send_notice_box(conn,
                            "Đến được đây quả là có cố gắng, hãy nói chuyện với phó chỉ huy để nhận thưởng hoàn thành phó bản, hoặc chọn tiếp tục chinh phục để có thể nhận được nhiều phần thưởng hơn");
                    break;
                }
                default: {
                    Service.send_notice_box(conn, "Chưa có chức năng");
                    break;
                }
            }
        }
    }

    private static void Menu_PhoChiHuy(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                // if (conn.p.level < 30) {
                // Service.send_notice_box(conn, "Đạt level 30 mới có thể vào phó bản");
                // return;
                // }
                // if (conn.p.point_active[0] < 1) {
                // Service.send_notice_box(conn, "Đã hết lượt đi, hãy quay lại vào ngày mai");
                // return;
                // }
                // String notice = "Danh sách người chơi vào phó bản :\n1) " + conn.p.name + " : level " +
                // conn.p.level;
                // if (conn.p.party != null && conn.p.party.get_mems().size() > 1) {
                // if (conn.p.party.get_mems().get(0).id != conn.p.id) {
                // Service.send_notice_box(conn, "Chỉ đội trưởng mới có thể mở cửa");
                // return;
                // }
                // for (int i = 1; i < conn.p.party.get_mems().size(); i++) {
                // Player p0 = conn.p.party.get_mems().get(i);
                // if (p0.level < 30) {
                // Service.send_notice_box(conn, "Người chơi " + p0.name + " không đủ điều kiện vào phó bản");
                // return;
                // }
                // if (p0.point_active[0] < 1) {
                // Service.send_notice_box(conn,
                // "Người chơi " + p0.name + " đã hết lượt đi, hãy quay lại vào ngày mai");
                // return;
                // }
                // notice += "\n" + (i + 1) + ") " + p0.name + " : level " + p0.level;
                // p0.dungeon = null;
                // }
                // }
                // notice += "\nĐộ khó: ??? \nHãy xác nhận!";
                // conn.p.dungeon = null;
                // Service.send_box_input_yesno(conn, 119, notice);
                Service.send_notice_box(conn, "Đang bảo trì!");
                break;
            }
            case 1: {
                Service.send_notice_box(conn,
                        "Ngã tư tử thần nâng cấp:\nSau khi vượt qua 20 ải đầu sẽ nhận phần thưởng hoàn thành phó bản, sau đó hãy nói chuyện với npc trong phó bản để quyết định độ khó, cuối cùng là tiếp tục chinh phục phó bản.\n Càng tích nhiều điểm càng được nhiều phần thưởng nhé, điểm sẽ được tính dựa trên số ải vượt qua và lượng dame gây ra.\nLưu ý mỗi ngày đi tối đa 3 lần.");
                break;
            }
            case 2: {
                synchronized (Dungeon.bxh_time_complete) {
                    String notice;
                    if (Dungeon.bxh_time_complete.size() > 0) {
                        notice = "BXH thời gian hoàn thành:\n";
                        int dem = 1;
                        for (Dungeon.BXH_Dungeon_Finished set : Dungeon.bxh_time_complete) {
                            notice += (dem++) + ". " + set.name + " : " + set.time + "s\n";
                        }
                    } else {
                        notice = "Chưa có thông tin";
                    }
                    Service.send_notice_box(conn, notice);
                }
                break;
            }
            case 5: {
                Service.send_notice_box(conn, "Thông tin:\nSố lần còn lại " + conn.p.point_active[0]
                        + ": lần\nTổng điểm hôm nay : " + conn.p.point_active[1]);
                break;
            }
            case 6: {
                if (conn.p.point_active[1] < 1) {
                    Service.send_notice_box(conn, "Hôm nay chưa làm gì cả, không làm mà đòi có ăn à con??");
                    return;
                }
                if (conn.p.item.get_bag_able() < 3) {
                    Service.send_notice_box(conn, "Hành trang không đủ chỗ!");
                    return;
                }
                while (conn.p.point_active[1] > 0) {
                    conn.p.point_active[1]--;
                    short id_ = Medal_Material.m_blue[Util.random(0, 10)];
                    if (25 > Util.random(0, 100)
                            && ((conn.p.item.get_bag_able() > 0) || (conn.p.item.total_item_by_id(7, id_) > 0))) {
                        Item47 itbag = new Item47();
                        itbag.id = id_;
                        itbag.quantity = (short) Util.random(0, 2);
                        itbag.category = 7;
                        conn.p.item.add_item_bag47(7, itbag);
                    }
                    //
                    conn.p.update_vang(Util.random(10, 50));
                }
                conn.p.item.char_inventory(7);
                Service.send_notice_box(conn, "Nhận thành công");
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_Pet_Manager(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                Service.send_box_UI(conn, 21);
                break;
            }
            case 1: {
                Service.send_box_UI(conn, 22);
                break;
            }
            case 2: {
                Service.send_box_UI(conn, 23);
                break;
            }
            case 3: {
                if (conn.p.pet_follow) {
                    for (Pet temp : conn.p.mypet) {
                        if (temp.is_follow) {
                            temp.is_follow = false;
                            Message m = new Message(44);
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
                    conn.p.pet_follow = false;
                    Service.send_wear(conn.p);
                    Service.send_char_main_in4(conn.p);
                } else {
                    Service.send_notice_box(conn, "Đã đeo pet đéo đâu mà đòi tháo??");
                }
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_Mr_Edgar(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                if (conn.p.list_enemies.size() > 0) {
                    String[] name = new String[conn.p.list_enemies.size()];
                    for (int i = 0; i < name.length; i++) {
                        name[i] = conn.p.list_enemies.get(name.length - i - 1);
                    }
                    send_menu_select(conn, 124, name);
                } else {
                    Service.send_notice_box(conn, "Danh sách chưa có ai, hãy đi cà khịa để tạo thêm!");
                }
                break;
            }
            case 1: {
                Service.send_notice_box(conn,
                        "Bị người chơi khác pk thì sẽ được lưu vào danh sách, "
                        + "mỗi lần trả thù sẽ được đưa tới nơi kẻ thù đang đứng với chi phí chỉ vỏn vẹn 2 ngọc.\n"
                        + "Sau khi được đưa tới nơi, tên kẻ thù sẽ được loại ra khỏi danh sách");
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_Zoro(Session conn, byte index) throws IOException {
        if (conn.p.myclan != null) {
            if (conn.p.myclan.mems.get(0).name.equals(conn.p.name)) {
                switch (index) {
                    case 0: {
                        send_menu_select(conn, 122,
                                new String[]{"Kho bang", "Nâng cấp bang", "Hủy bang hội", "Chuyển thủ lĩnh"});
                        break;
                    }
                    case 1: { //
                        Service.send_box_UI(conn, 29);
                        break;
                    }
                    case 2: {
                        Service.send_box_UI(conn, 30);
                        break;
                    }
                    default: {
                        Service.send_notice_box(conn, "Chưa có chức năng");
                        break;
                    }
                }
            } else {
                switch (index) {
                    case 0: {
                        conn.p.myclan.open_box_clan(conn);
                        break;
                    }
                    case 1: {
                        Service.send_box_input_text(conn, 8, "Góp vàng", new String[]{"Số lượng :"});
                        break;
                    }
                    case 2: {
                        Service.send_box_input_text(conn, 9, "Góp Ngọc", new String[]{"Số lượng :"});
                        break;
                    }
                    case 3: {
                        Service.send_box_input_yesno(conn, 117,
                                "Hãy xác nhận việc rời bang, có khi đi rồi quay lại éo đc đâu nhá");
                        break;
                    }
                    default: {
                        Service.send_notice_box(conn, "Chưa có chức năng");
                        break;
                    }
                }
            }
        } else {
            switch (index) {
                case 0: {
                    Service.send_box_input_yesno(conn, 120, "Bạn có muốn đăng ký tạo bang với phí là 20.000 ngọc");
                    break;
                }
                default: {
                    Service.send_notice_box(conn, "Chưa có chức năng");
                    break;
                }
            }
        }
    }

    private static void Menu_VXMM(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                Manager.gI().vxmm.send_in4(conn.p);
                break;
            }
            case 1: {
                Service.send_box_input_text(conn, 3, "Vòng xoay vàng", new String[]{"Tham gia (tối thiểu 10k) :"});
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_CuopBien(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                send_menu_select(conn, 125, new String[]{"Xem thông tin", "Tham gia"});
                break;
            }
            case 1: {
                Service.send_notice_box(conn, "Sắp ra mắt");
                break;
            }
            case 2: {
                Service.send_notice_box(conn, "Sắp ra mắt");
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    public static void send_menu_select(Session conn, int idnpc, String[] menu) throws IOException {
        if (!conn.p.isdie) {
            if (menu != null && menu.length > 0) {
                Message m2 = new Message(-30);
                m2.writer().writeShort(idnpc);
                m2.writer().writeByte(0);
                m2.writer().writeByte(menu.length);
                for (int i = 0; i < menu.length; i++) {
                    m2.writer().writeUTF(menu[i]);
                }
                m2.writer().writeUTF("MENU : " + idnpc);
                conn.addmsg(m2);
                m2.cleanup();
            }
        }
    }

    private static void Menu_Aman(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                Message m = new Message(23);
                m.writer().writeUTF("Rương đồ");
                m.writer().writeByte(3);
                m.writer().writeShort(0);
                conn.addmsg(m);
                m.cleanup();
                break;
            }
            case 1: {
                if (conn.p.maxbag >= 126) {
                    Service.send_notice_box(conn, "Đã mở rương rồi!");
                    return;
                }
                try ( Connection connection = SQL.gI().getConnection();  Statement statement = connection.createStatement();) {
                    if (statement.executeUpdate("UPDATE `player` SET `maxbag` = 126 WHERE `id` = " + conn.p.id + ";") > 0) {
                        connection.commit();
                    }
                    Service.send_notice_box(conn, "Mở thành công 126 ô, hãy thoát game vào lại để cập nhật!");
                } catch (SQLException e) {
                    e.printStackTrace();
                    Service.send_notice_box(conn, "Có lỗi xảy ra, hãy thử lại!");
                }
                break;
            }
            case 2: {
                // if (conn.p.update_coin(-25_000)) {
                // if (true) {
                // String js =
                // "[1,%s,%s,%s,%s,10,100,300,1,1,1,1,100,[[0,%s,%s],[23,%s,0],[24,%s,0],[25,%s,0],[26,%s,0]]]";
                // byte[] type = new byte[] {8, 13, 10, 11, 11, 11, 12, 12, 12, 9, 7, 7, 7, 6, 5, 5, 5, 4, 4, 4, 3,
                // 3, 3, 2,
                // 2, 2, 1, 1, 1, 0, 0, 0};
                // byte[] icon = new byte[] {26, 41, 32, 33, 34, 35, 36, 37, 38, 29, 21, 22, 23, 20, 15, 16, 17, 12,
                // 13, 14,
                // 9, 10, 11, 6, 7, 8, 0, 1, 2, 3, 4, 5};
                // int rd = Util.random(0, 32);
                // js = String.format(js, type[rd], icon[rd], 3, 0, Util.random(0, 99), Util.random(99, 999),
                // Util.random(1, 50), Util.random(1, 50), Util.random(1, 50), Util.random(1, 50));
                // if (conn.p.mypet == null) {
                // conn.p.mypet = new Pet(conn.p);
                // }
                // conn.p.mypet.setup((JSONArray) JSONValue.parse(js));
                // Service.send_wear(conn.p);
                // Service.send_char_main_in4(conn.p);
                // conn.p.map.update_in4_inside(conn.p);
                // Service.send_box_notice(conn, "Nhận thành công!");
                // // } else {
                // // Service.send_box_notice(conn, "Éo đủ coin, đi nạp đi!");
                // }
                if (conn.user.contains("knightauto_hsr_")) {
                    if (conn.p.level < 10) {
                        Service.send_notice_box(conn, "Đạt level 10 mới có thể đăng ký tài khoản");
                        return;
                    }
                    Service.send_box_input_text(conn, 6, "Nhập thông tin",
                            new String[]{"Tên đăng nhập mới", "Mật khẩu mới"});
                } else {
                    Service.send_notice_box(conn, "Chức năng đang phát triển...");
                }
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_Black_Eye(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                Service.send_box_UI(conn, 13);
                break;
            }
            case 1: {
                Service.send_box_UI(conn, 14);
                break;
            }
            case 2: {
                Service.send_box_UI(conn, 15);
                break;
            }
            case 3: {
                Service.send_box_UI(conn, 16);
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_BXH(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                BXH.send(conn, 0);
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_Miss_Anna(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                Service.send_box_input_text(conn, 0, "Nhập mã code", new String[]{"Code"});
                break;
            }
            case 1: { // thao cai trang
                if (conn.p.item.wear[11] == null) {
                    Service.send_notice_box(conn, "Mặc đéo đâu mà tháo hả thằng lol?");
                } else {
                    Item3 buffer = conn.p.item.wear[11];
                    conn.p.item.wear[11] = null;
                    conn.p.item.add_item_bag3(buffer);
                    conn.p.item.char_inventory(3);
                    conn.p.fashion = Part_fashion.get_part(conn.p);
                    Service.send_wear(conn.p);
                    Service.send_char_main_in4(conn.p);
                    MapService.update_in4_2_other_inside(conn.p.map, conn.p);
                    Service.send_notice_box(conn, "Tháo thành công");
                }
                break;
            }
            case 2: { // thao canh
                if (conn.p.item.wear[10] == null) {
                    Service.send_notice_box(conn, "Mặc đéo đâu mà tháo hả thằng lol?");
                } else {
                    Item3 buffer = conn.p.item.wear[10];
                    conn.p.item.wear[10] = null;
                    conn.p.item.add_item_bag3(buffer);
                    conn.p.item.char_inventory(3);
                    Service.send_wear(conn.p);
                    Service.send_char_main_in4(conn.p);
                    MapService.update_in4_2_other_inside(conn.p.map, conn.p);
                    Service.send_notice_box(conn, "Tháo thành công");
                }
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_PhapSu(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                conn.p.id_item_rebuild = -1;
                conn.p.is_use_mayman = false;
                conn.p.id_use_mayman = -1;
                Service.send_box_UI(conn, 18);
                break;
            }
            case 1: {
                Service.send_box_UI(conn, 17);
                break;
            }
            case 2: {
                conn.p.item_replace = -1;
                conn.p.item_replace2 = -1;
                Service.send_box_UI(conn, 19);
                break;
            }
            case 3: {
                Service.send_box_UI(conn, 24);
                break;
            }
            case 4: {
                Service.send_box_UI(conn, 25);
                conn.p.id_medal_is_created = 0;
                break;
            }
            case 5: {
                Service.send_box_UI(conn, 26);
                conn.p.id_medal_is_created = 1;
                break;
            }
            case 6: {
                Service.send_box_UI(conn, 27);
                conn.p.id_medal_is_created = 2;
                break;
            }
            case 7: {
                Service.send_box_UI(conn, 28);
                conn.p.id_medal_is_created = 3;
                break;
            }
            case 8: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
            case 9: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
            case 10: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_Admin(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
              ServerManager.gI().close();
				System.out.println("Close server is processing....");
				new Thread(new Runnable() {
					@Override
					public void run() {
						// flush clan
						Clan.flush();
						// kick player
						// while (Session.client_entrys.size() > 0) {
						// synchronized (Session.client_entrys) {
						for (int k = Session.client_entrys.size() - 1; k >= 0; k--) {
							try {
								Session.client_entrys.get(k).close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						// }
						// }
						// stop service
						Manager.gI().close();
					}
				}).start();
                break;
            }
            case 1: {
                conn.p.update_vang(1_000_000_000);
                conn.p.item.char_inventory(5);
                Service.send_notice_nobox_white(conn, "+ 1.000.000.000 vàng");
                break;
            }
            case 2: {
                conn.p.update_ngoc(1_000_000);
                conn.p.item.char_inventory(5);
                Service.send_notice_nobox_white(conn, "+ 1.000.000 ngọc");
                break;
            }
            case 3: {
                BXH.init();
                Service.send_notice_nobox_white(conn, "BXH đã đc cập nhật");
                break;
            }
            case 4: {
                Service.send_box_input_text(conn, 1, "Get Item",
                        new String[]{"Nhập loại (3,4,7) vật phẩm :", "Nhập id vật phẩm", "Nhập số lượng"});
                break;
            }
            case 5: {
                Service.send_box_input_text(conn, 2, "Plus Level", new String[]{"Nhập level :"});
                break;
            }
            case 6: {
                Service.send_box_input_text(conn, 4, "Set Xp", new String[]{"Nhập mức x :"});
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_Zulu(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                switch (conn.p.clazz) {
                    case 0: {
                        Service.send_msg_data(conn, 23, "tocchienbinh");
                        break;
                    }
                    case 1: {
                        Service.send_msg_data(conn, 23, "tocsatthu");
                        break;
                    }
                    case 2:
                    case 3: {
                        Service.send_msg_data(conn, 23, "tocphapsu");
                        break;
                    }
                }
                break;
            }
            case 1: {
                if (conn.p.diemdanh == 1) {
                    conn.p.diemdanh = 0;
                    int ngoc_ = Util.random(100, 1000);
                    conn.p.update_ngoc(ngoc_);
                    Log.gI().add_log(conn.p.name, "Điểm danh ngày được " + Util.number_format(ngoc_) + " ngọc");
                    conn.p.item.char_inventory(5);
                    Service.send_notice_box(conn, "Bạn đã điểm danh thành công, được " + ngoc_ + " ngọc");
                } else {
                    Service.send_notice_box(conn, "Bạn đã điểm danh hôm nay rồi");
                }
                break;
            }
            case 2: {
                 Service.send_box_input_text(conn, 5, "Đổi coin sang ngọc", new String[] {"Tỷ lệ 1000 coin = 250 ngọc"});
                break;
            }
//            case 3: {
//               Service.send_box_input_yesno(conn, 121, "500 ngọc cho 2h, hãy xác nhận");
//                break;
//            }
            case 4: {
                EffTemplate ef = conn.p.get_eff(-126);
                if (ef != null && ef.time > System.currentTimeMillis()) {
                    Service.send_notice_box(conn,
                            "Thời gian còn lại : " + Util.getTime((int) (ef.time - System.currentTimeMillis()) / 1000));
                } else {
                    Service.send_notice_box(conn, "Chưa đăng ký kiểm tra cái gì?");
                }
                break;
            }
            case 5: {
                if (conn.p.type_exp == 0) {
                    conn.p.type_exp = 1;
                    Service.send_notice_box(conn, "Đã bật nhận exp");
                } else {
                    conn.p.type_exp = 0;
                    Service.send_notice_box(conn, "Đã tắt nhận exp");
                }
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_ChangeZone(Session conn) throws IOException {
        Map[] map = Map.get_map_by_id(conn.p.map.map_id);
        if (map != null) {
            Message m = new Message(54);
            m.writer().writeByte(conn.p.map.maxzone);
            //
            for (int i = 0; i < conn.p.map.maxzone; i++) {
                if (map[i].players.size() > (map[i].maxplayer - 2)) {
                    m.writer().writeByte(2); // redzone
                } else if (map[i].players.size() >= (map[i].maxplayer / 2)) {
                    m.writer().writeByte(1); // yellow zone
                } else {
                    m.writer().writeByte(0); // green zone
                }
                m.writer().writeByte(0);
            }
            for (int i = 0; i < conn.p.map.maxzone; i++) {
                m.writer().writeUTF(
                        "Khu " + (map[i].zone_id + 1) + " (" + map[i].players.size() + "/" + map[i].maxplayer + ")");
            }
            //
            conn.addmsg(m);
            m.cleanup();
        }
    }

    private static void Menu_Alisama(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                Service.send_box_UI(conn, 9);
                break;
            }
            case 1: {
                Service.send_box_UI(conn, 10);
                break;
            }
            case 2: {
                Service.send_box_UI(conn, 11);
                break;
            }
            case 3: {
                Service.send_box_UI(conn, 12);
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_DaDichChuyen10(Session conn, byte index) throws IOException {
        Vgo vgo = null;
        switch (index) {
            case 0: {
                vgo = new Vgo();
                vgo.id_map_go = 1;
                vgo.x_new = 432;
                vgo.y_new = 354;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 1: {
                vgo = new Vgo();
                vgo.id_map_go = 33;
                vgo.x_new = 432;
                vgo.y_new = 480;
                conn.p.change_map(conn.p, vgo);
                break;
            }

            case 2: {
                // vgo = new Vgo();
                // vgo.idmapgo = 82;
                // vgo.xnew = 432;
                // vgo.ynew = 354;
                // conn.p.changemap(conn.p, vgo);
                Service.send_notice_box(conn, "Đang bảo trì khu vực này");
                break;
            }
            case 3: {
                vgo = new Vgo();
                vgo.id_map_go = 4;
                vgo.x_new = 888;
                vgo.y_new = 672;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 4: {
                vgo = new Vgo();
                vgo.id_map_go = 5;
                vgo.x_new = 1056;
                vgo.y_new = 864;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 5: {
                vgo = new Vgo();
                vgo.id_map_go = 8;
                vgo.x_new = 576;
                vgo.y_new = 222;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 6: {
                vgo = new Vgo();
                vgo.id_map_go = 9;
                vgo.x_new = 1243;
                vgo.y_new = 876;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 7: {
                vgo = new Vgo();
                vgo.id_map_go = 11;
                vgo.x_new = 286;
                vgo.y_new = 708;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 8: {
                vgo = new Vgo();
                vgo.id_map_go = 12;
                vgo.x_new = 240;
                vgo.y_new = 732;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 9: {
                vgo = new Vgo();
                vgo.id_map_go = 13;
                vgo.x_new = 150;
                vgo.y_new = 979;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 10: {
                vgo = new Vgo();
                vgo.id_map_go = 15;
                vgo.x_new = 469;
                vgo.y_new = 1099;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 11: {
                vgo = new Vgo();
                vgo.id_map_go = 16;
                vgo.x_new = 673;
                vgo.y_new = 1093;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 12: {
                vgo = new Vgo();
                vgo.id_map_go = 17;
                vgo.x_new = 660;
                vgo.y_new = 612;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_DaDichChuyen33(Session conn, byte index) throws IOException {
        Vgo vgo = null;
        switch (index) {
            case 0: {
                vgo = new Vgo();
                vgo.id_map_go = 67;
                vgo.x_new = 576;
                vgo.y_new = 222;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 1: {
                vgo = new Vgo();
                vgo.id_map_go = 33;
                vgo.x_new = 432;
                vgo.y_new = 480;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 2: {
                // vgo = new Vgo();
                // vgo.idmapgo = 82;
                // vgo.xnew = 432;
                // vgo.ynew = 354;
                // conn.p.changemap(conn.p, vgo);
                Service.send_notice_box(conn, "Đang bảo trì khu vực này");
                break;
            }
            case 3: {
                vgo = new Vgo();
                vgo.id_map_go = 20;
                vgo.x_new = 787;
                vgo.y_new = 966;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 4: {
                vgo = new Vgo();
                vgo.id_map_go = 22;
                vgo.x_new = 120;
                vgo.y_new = 678;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 5: {
                vgo = new Vgo();
                vgo.id_map_go = 24;
                vgo.x_new = 576;
                vgo.y_new = 222;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 6: {
                vgo = new Vgo();
                vgo.id_map_go = 26;
                vgo.x_new = 576;
                vgo.y_new = 222;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 7: {
                vgo = new Vgo();
                vgo.id_map_go = 29;
                vgo.x_new = 576;
                vgo.y_new = 222;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 8: {
                vgo = new Vgo();
                vgo.id_map_go = 31;
                vgo.x_new = 360;
                vgo.y_new = 624;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 9: {
                vgo = new Vgo();
                vgo.id_map_go = 37;
                vgo.x_new = 150;
                vgo.y_new = 674;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 10: {
                vgo = new Vgo();
                vgo.id_map_go = 39;
                vgo.x_new = 199;
                vgo.y_new = 882;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 11: {
                vgo = new Vgo();
                vgo.id_map_go = 41;
                vgo.x_new = 187;
                vgo.y_new = 462;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 12: {
                vgo = new Vgo();
                vgo.id_map_go = 43;
                vgo.x_new = 228;
                vgo.y_new = 43;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 13: {
                vgo = new Vgo();
                vgo.id_map_go = 45;
                vgo.x_new = 576;
                vgo.y_new = 222;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 14: {
                vgo = new Vgo();
                vgo.id_map_go = 50;
                vgo.x_new = 300;
                vgo.y_new = 300;
                conn.p.change_map(conn.p, vgo);
                break;
            }

            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_DaDichChuyen55(Session conn, byte index) throws IOException {
        Vgo vgo = null;
        switch (index) {
            case 0: {
                vgo = new Vgo();
                vgo.id_map_go = 67;
                vgo.x_new = 576;
                vgo.y_new = 222;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 1: {
                // vgo = new Vgo();
                // vgo.idmapgo = 82;
                // vgo.xnew = 432;
                // vgo.ynew = 354;
                // conn.p.changemap(conn.p, vgo);
                Service.send_notice_box(conn, "Đang bảo trì khu vực này");
                break;
            }
            case 2: {
                vgo = new Vgo();
                vgo.id_map_go = 74;
                vgo.x_new = 258;
                vgo.y_new = 354;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 3: {
                vgo = new Vgo();
                vgo.id_map_go = 77;
                vgo.x_new = 576;
                vgo.y_new = 222;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 4: {
                vgo = new Vgo();
                vgo.id_map_go = 93;
                vgo.x_new = 462;
                vgo.y_new = 342;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 5: {
                vgo = new Vgo();
                vgo.id_map_go = 94;
                vgo.x_new = 306;
                vgo.y_new = 240;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 6: {
                vgo = new Vgo();
                vgo.id_map_go = 95;
                vgo.x_new = 390;
                vgo.y_new = 162;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 7: {
                vgo = new Vgo();
                vgo.id_map_go = 96;
                vgo.x_new = 198;
                vgo.y_new = 666;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 8: {
                vgo = new Vgo();
                vgo.id_map_go = 97;
                vgo.x_new = 432;
                vgo.y_new = 168;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 9: {
                vgo = new Vgo();
                vgo.id_map_go = 98;
                vgo.x_new = 270;
                vgo.y_new = 132;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            case 10: {
                vgo = new Vgo();
                vgo.id_map_go = 33;
                vgo.x_new = 432;
                vgo.y_new = 480;
                conn.p.change_map(conn.p, vgo);
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_Hammer(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: {
                Service.send_box_UI(conn, 5);
                break;
            }
            case 1: {
                Service.send_box_UI(conn, 6);
                break;
            }
            case 2: {
                Service.send_box_UI(conn, 7);
                break;
            }
            case 3: {
                Service.send_box_UI(conn, 8);
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_Doubar(Session conn, byte index) throws IOException {
        // cua hang item wear
        switch (index) {
            case 0: {
                Service.send_box_UI(conn, 1);
                break;
            }
            case 1: {
                Service.send_box_UI(conn, 2);
                break;
            }
            case 2: {
                Service.send_box_UI(conn, 3);
                break;
            }
            case 3: {
                Service.send_box_UI(conn, 4);
                break;
            }
            case 4 :{
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
                        Service.send_notice_box(conn,boss_in4);
                        break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }

    private static void Menu_Lisa(Session conn, byte index) throws IOException {
        switch (index) {
            case 0: { // cua hang potion
                Service.send_box_UI(conn, 0);
                break;
            }
            default: {
                Service.send_notice_box(conn, "Chưa có chức năng");
                break;
            }
        }
    }
}
