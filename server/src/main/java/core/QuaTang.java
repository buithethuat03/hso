package core;

import client.Player;
import java.io.IOException;
import java.util.ArrayList;
import template.Item3;
import template.Item47;
import template.ItemTemplate3;

/**
 * Quản lý nhận thưởng các mốc nạp game
 * Đã fix toàn bộ: Chống dupe vô hạn mốc 100k/200k, fix nạp 200đ, chuẩn hóa coin web, kiểm tra túi đầy
 */
public class QuaTang {

    public static void get_qua(Player p, byte select) throws IOException {
        if (p.item.get_bag_able() < 2) {
            Service.send_notice_box(p.conn, "Hành trang cần ít nhất 2 ô trống để nhận quà!");
            return;
        }

        int tongnap = p.get_tongnap();

        switch (select) {
            case 0: { // Mốc 100k
                if (p.check_moc_nap(100_000)) {
                    Service.send_notice_box(p.conn, "Bạn đã nhận mốc nạp 100.000đ rồi!");
                    return;
                }
                if (tongnap < 100_000) {
                    Service.send_notice_box(p.conn, "Tổng nạp của bạn chưa đủ 100.000đ!\nHiện tại: " + Util.number_format(tongnap) + "đ");
                    return;
                }

                p.update_id_name(0);
                Service.send_char_main_in4(p);

                // Thưởng Cải trang Trộm (ID 4616)
                ItemTemplate3 item = ItemTemplate3.item.get(4616);
                if (item != null) {
                    Item3 itbag = new Item3();
                    itbag.id = item.getId();
                    itbag.name = item.getName();
                    itbag.clazz = item.getClazz();
                    itbag.type = item.getType();
                    itbag.level = 10;
                    itbag.icon = item.getIcon();
                    itbag.op = new ArrayList<>();
                    itbag.op.addAll(item.getOp());
                    itbag.color = item.getColor();
                    itbag.part = item.getPart();
                    itbag.tier = 0;
                    itbag.islock = true;
                    itbag.time_use = 0;
                    p.item.add_item_bag3(itbag);
                }

                p.update_vang(5_000_000);
                p.update_ngoc(500);
                p.item.char_inventory(3);
                p.item.char_inventory(5);
                p.conn.moc_nap.add("100000");
                p.conn.save_moc_nap();
                Service.send_notice_box(p.conn, "Nhận thành công mốc 100.000đ!\n+ 1 Cải trang Trộm\n+ 5.000.000 Vàng\n+ 500 Ngọc\n(Thoát game vào lại để cập nhật đầy đủ)");
                break;
            }
            case 1: { // Mốc 200k
                if (p.check_moc_nap(200_000)) {
                    Service.send_notice_box(p.conn, "Bạn đã nhận mốc nạp 200.000đ rồi!");
                    return;
                }
                if (tongnap < 200_000) {
                    Service.send_notice_box(p.conn, "Tổng nạp của bạn chưa đủ 200.000đ!\nHiện tại: " + Util.number_format(tongnap) + "đ");
                    return;
                }

                // Thưởng Cải trang Cướp 200 (ID 3594)
                ItemTemplate3 item = ItemTemplate3.item.get(3594);
                if (item != null) {
                    Item3 itbag = new Item3();
                    itbag.id = item.getId();
                    itbag.name = item.getName();
                    itbag.clazz = item.getClazz();
                    itbag.type = item.getType();
                    itbag.level = 10;
                    itbag.icon = item.getIcon();
                    itbag.op = new ArrayList<>();
                    itbag.op.addAll(item.getOp());
                    itbag.color = item.getColor();
                    itbag.part = item.getPart();
                    itbag.tier = 0;
                    itbag.islock = true;
                    itbag.time_use = 0;
                    p.item.add_item_bag3(itbag);
                }

                Item47 it = new Item47();
                it.category = 7;
                it.id = 14;
                it.quantity = 10;
                p.item.add_item_bag47(7, it);

                p.update_vang(10_000_000);
                p.update_ngoc(1_500);
                p.item.char_inventory(3);
                p.item.char_inventory(7);
                p.item.char_inventory(5);
                p.conn.moc_nap.add("200000");
                p.conn.save_moc_nap();
                Service.send_notice_box(p.conn, "Nhận thành công mốc 200.000đ!\n+ 1 Cải trang Cướp 200\n+ 10 Đá May Mắn\n+ 10.000.000 Vàng\n+ 1.500 Ngọc\n(Thoát game vào lại để cập nhật đầy đủ)");
                break;
            }
            case 2: { // Mốc 500k
                if (p.check_moc_nap(500_000)) {
                    Service.send_notice_box(p.conn, "Bạn đã nhận mốc nạp 500.000đ rồi!");
                    return;
                }
                if (tongnap < 500_000) {
                    Service.send_notice_box(p.conn, "Tổng nạp của bạn chưa đủ 500.000đ!\nHiện tại: " + Util.number_format(tongnap) + "đ");
                    return;
                }

                // Thưởng Cải trang Cướp 500 (ID 4789)
                ItemTemplate3 item = ItemTemplate3.item.get(4789);
                if (item != null) {
                    Item3 itbag = new Item3();
                    itbag.id = item.getId();
                    itbag.name = item.getName();
                    itbag.clazz = item.getClazz();
                    itbag.type = item.getType();
                    itbag.level = 10;
                    itbag.icon = item.getIcon();
                    itbag.op = new ArrayList<>();
                    itbag.op.addAll(item.getOp());
                    itbag.color = item.getColor();
                    itbag.part = item.getPart();
                    itbag.tier = 0;
                    itbag.islock = true;
                    itbag.time_use = 0;
                    p.item.add_item_bag3(itbag);
                }

                Item47 it = new Item47();
                it.category = 7;
                it.id = 14;
                it.quantity = 20;
                p.item.add_item_bag47(7, it);

                p.update_vang(20_000_000);
                p.update_ngoc(2_000);
                p.update_coin(50_000);
                p.item.char_inventory(3);
                p.item.char_inventory(7);
                p.item.char_inventory(5);
                p.conn.moc_nap.add("500000");
                p.conn.save_moc_nap();
                Service.send_notice_box(p.conn, "Nhận thành công mốc 500.000đ!\n+ 1 Cải trang Cướp 500\n+ 20 Đá May Mắn\n+ 20.000.000 Vàng\n+ 2.000 Ngọc\n+ 50.000 Coin Web");
                break;
            }
            case 3: { // Mốc 1M
                if (p.check_moc_nap(1_000_000)) {
                    Service.send_notice_box(p.conn, "Bạn đã nhận mốc nạp 1.000.000đ rồi!");
                    return;
                }
                if (tongnap < 1_000_000) {
                    Service.send_notice_box(p.conn, "Tổng nạp của bạn chưa đủ 1.000.000đ!\nHiện tại: " + Util.number_format(tongnap) + "đ");
                    return;
                }

                // Thưởng Cải trang Tướng Cướp (ID 3595)
                ItemTemplate3 item = ItemTemplate3.item.get(3595);
                if (item != null) {
                    Item3 itbag = new Item3();
                    itbag.id = item.getId();
                    itbag.name = item.getName();
                    itbag.clazz = item.getClazz();
                    itbag.type = item.getType();
                    itbag.level = 10;
                    itbag.icon = item.getIcon();
                    itbag.op = new ArrayList<>();
                    itbag.op.addAll(item.getOp());
                    itbag.color = item.getColor();
                    itbag.part = item.getPart();
                    itbag.tier = 0;
                    itbag.islock = true;
                    itbag.time_use = 0;
                    p.item.add_item_bag3(itbag);
                }

                Item47 it = new Item47();
                it.category = 7;
                it.id = 14;
                it.quantity = 50;
                p.item.add_item_bag47(7, it);

                p.update_vang(50_000_000);
                p.update_ngoc(2_500);
                p.update_coin(100_000);
                p.item.char_inventory(3);
                p.item.char_inventory(7);
                p.item.char_inventory(5);
                p.conn.moc_nap.add("1000000");
                p.conn.save_moc_nap();
                Service.send_notice_box(p.conn, "Nhận thành công mốc 1.000.000đ!\n+ 1 Cải trang Tướng Cướp\n+ 50 Đá May Mắn\n+ 50.000.000 Vàng\n+ 2.500 Ngọc\n+ 100.000 Coin Web");
                break;
            }
            case 4: { // Mốc 1M5
                if (p.check_moc_nap(1_500_000)) {
                    Service.send_notice_box(p.conn, "Bạn đã nhận mốc nạp 1.500.000đ rồi!");
                    return;
                }
                if (tongnap < 1_500_000) {
                    Service.send_notice_box(p.conn, "Tổng nạp của bạn chưa đủ 1.500.000đ!\nHiện tại: " + Util.number_format(tongnap) + "đ");
                    return;
                }

                // Thưởng Cải trang Giáp Sừng (ID 4741)
                ItemTemplate3 item = ItemTemplate3.item.get(4741);
                if (item != null) {
                    Item3 itbag = new Item3();
                    itbag.id = item.getId();
                    itbag.name = item.getName();
                    itbag.clazz = item.getClazz();
                    itbag.type = item.getType();
                    itbag.level = 10;
                    itbag.icon = item.getIcon();
                    itbag.op = new ArrayList<>();
                    itbag.op.addAll(item.getOp());
                    itbag.color = item.getColor();
                    itbag.part = item.getPart();
                    itbag.tier = 0;
                    itbag.islock = true;
                    itbag.time_use = 0;
                    p.item.add_item_bag3(itbag);
                }

                Item47 it = new Item47();
                it.category = 7;
                it.id = 14;
                it.quantity = 80;
                p.item.add_item_bag47(7, it);

                p.update_vang(100_000_000);
                p.update_ngoc(5_000);
                p.update_coin(200_000);
                p.item.char_inventory(3);
                p.item.char_inventory(7);
                p.item.char_inventory(5);
                p.conn.moc_nap.add("1500000");
                p.conn.save_moc_nap();
                Service.send_notice_box(p.conn, "Nhận thành công mốc 1.500.000đ!\n+ 1 Cải trang Giáp Sừng\n+ 80 Đá May Mắn\n+ 100.000.000 Vàng\n+ 5.000 Ngọc\n+ 200.000 Coin Web");
                break;
            }
            case 5: { // Mốc 2M
                if (p.check_moc_nap(2_000_000)) {
                    Service.send_notice_box(p.conn, "Bạn đã nhận mốc nạp 2.000.000đ rồi!");
                    return;
                }
                if (tongnap < 2_000_000) {
                    Service.send_notice_box(p.conn, "Tổng nạp của bạn chưa đủ 2.000.000đ!\nHiện tại: " + Util.number_format(tongnap) + "đ");
                    return;
                }

                // Thưởng Cải trang Giáp Deathwing (ID 4760)
                ItemTemplate3 item = ItemTemplate3.item.get(4760);
                if (item != null) {
                    Item3 itbag = new Item3();
                    itbag.id = item.getId();
                    itbag.name = item.getName();
                    itbag.clazz = item.getClazz();
                    itbag.type = item.getType();
                    itbag.level = 10;
                    itbag.icon = item.getIcon();
                    itbag.op = new ArrayList<>();
                    itbag.op.addAll(item.getOp());
                    itbag.color = item.getColor();
                    itbag.part = item.getPart();
                    itbag.tier = 0;
                    itbag.islock = true;
                    itbag.time_use = 0;
                    p.item.add_item_bag3(itbag);
                }

                Item47 it = new Item47();
                it.category = 7;
                it.id = 14;
                it.quantity = 120;
                p.item.add_item_bag47(7, it);

                p.update_vang(200_000_000);
                p.update_ngoc(7_500);
                p.update_coin(300_000);
                p.item.char_inventory(3);
                p.item.char_inventory(7);
                p.item.char_inventory(5);
                p.conn.moc_nap.add("2000000");
                p.conn.save_moc_nap();
                Service.send_notice_box(p.conn, "Nhận thành công mốc 2.000.000đ!\n+ 1 Cải trang Giáp Deathwing\n+ 120 Đá May Mắn\n+ 200.000.000 Vàng\n+ 7.500 Ngọc\n+ 300.000 Coin Web");
                break;
            }
            case 6: { // Mốc 3M
                if (p.check_moc_nap(3_000_000)) {
                    Service.send_notice_box(p.conn, "Bạn đã nhận mốc nạp 3.000.000đ rồi!");
                    return;
                }
                if (tongnap < 3_000_000) {
                    Service.send_notice_box(p.conn, "Tổng nạp của bạn chưa đủ 3.000.000đ!\nHiện tại: " + Util.number_format(tongnap) + "đ");
                    return;
                }

                // Thưởng Cải trang Giáp Hellwing (ID 4759)
                ItemTemplate3 item = ItemTemplate3.item.get(4759);
                if (item != null) {
                    Item3 itbag = new Item3();
                    itbag.id = item.getId();
                    itbag.name = item.getName();
                    itbag.clazz = item.getClazz();
                    itbag.type = item.getType();
                    itbag.level = 10;
                    itbag.icon = item.getIcon();
                    itbag.op = new ArrayList<>();
                    itbag.op.addAll(item.getOp());
                    itbag.color = item.getColor();
                    itbag.part = item.getPart();
                    itbag.tier = 0;
                    itbag.islock = true;
                    itbag.time_use = 0;
                    p.item.add_item_bag3(itbag);
                }

                Item47 it = new Item47();
                it.category = 7;
                it.id = 14;
                it.quantity = 200;
                p.item.add_item_bag47(7, it);

                p.update_vang(500_000_000);
                p.update_ngoc(10_000);
                p.update_coin(500_000);
                p.item.char_inventory(3);
                p.item.char_inventory(7);
                p.item.char_inventory(5);
                p.conn.moc_nap.add("3000000");
                p.conn.save_moc_nap();
                Service.send_notice_box(p.conn, "Nhận thành công mốc 3.000.000đ!\n+ 1 Cải trang Giáp Hellwing\n+ 200 Đá May Mắn\n+ 500.000.000 Vàng\n+ 10.000 Ngọc\n+ 500.000 Coin Web");
                break;
            }
            default: {
                Service.send_notice_box(p.conn, "Chưa có mốc này!");
                break;
            }
        }
    }
}
