package core;

import client.Player;
import java.io.IOException;
import java.util.ArrayList;
import template.Item3;
import template.Item47;
import template.ItemTemplate3;

/**
 *
 * @author lovu1
 */
class QuaTang {

    public static void get_qua(Player p, byte select) throws IOException {
        switch (select) {
            case 0: {
                if (p.get_tongnap() >= 100_000 ) {
                    
                    p.update_id_name(0);
                    Service.send_char_main_in4(p);
                    
                    
                    ItemTemplate3 item = ItemTemplate3.item.get(4616);
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

                    p.update_vang(5_000_000);
                    p.update_ngoc(500);
                    p.item.char_inventory(5);
                    p.conn.moc_nap.add("100000");
                    p.conn.save_moc_nap();
                    Service.send_notice_box(p.conn, "Nhận thành công mốc 100k"
                            + "\n+ Uot game vào lại để thấy hết quà ");
                } else {
                    Service.send_notice_box(p.conn, "Nạp chưa đủ");
                }
                break;
            }
            case 1: { 
                if (p.get_tongnap() >= 200 ) {
                    //ađc ct Cướp
                    ItemTemplate3 item = ItemTemplate3.item.get(3594);
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
                    Item47 it = new Item47();
                    it.category = 7;
                    it.id = 14;
                    it.quantity = (short) (10);
                    p.item.add_item_bag47(7, it);

                    p.update_vang(10_000_000);
                    p.update_ngoc(1_500);
                    p.item.char_inventory(5);
                    p.conn.moc_nap.add("200000");
                    p.conn.save_moc_nap();
                } else {
                    Service.send_notice_box(p.conn, "Nạp chưa đủ");
                }
                break;
            }
            case 2: { // mốc 500k
                if (p.get_tongnap() >= 500_000 && !p.check_moc_nap(500_000)) {
                    //ađc ct Cướp
                    ItemTemplate3 item = ItemTemplate3.item.get(4789);
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
                    Item47 it = new Item47();
                    it.category = 7;
                    it.id = 14;
                    it.quantity = (short) (20);
                    p.item.add_item_bag47(7, it);

                    p.update_vang(20_000_000);
                    p.update_ngoc(2_000);
                    p.update_coin(50_000);
                    p.item.char_inventory(5);
                    p.conn.moc_nap.add("500000");
                    p.conn.save_moc_nap();
                    Service.send_notice_box(p.conn, "Nhận thành công mốc 500k"
                            + "\n+ Uot game vào lại để thấy hết quà ");
                } else {
                    Service.send_notice_box(p.conn, "Nạp chưa đủ");
                }
                break;
            }
            case 3: { // mốc 1m
                if (p.get_tongnap() >= 1_000_000 && !p.check_moc_nap(1_000_000)) {
                    //ađc ct Tướng Cướp
                    ItemTemplate3 item = ItemTemplate3.item.get(3595);
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
                    Item47 it = new Item47();
                    it.category = 7;
                    it.id = 14;
                    it.quantity = (short) (50);
                    p.item.add_item_bag47(7, it);

                    p.update_vang(50_000_000);
                    p.update_ngoc(2_500);
                    p.update_coin(100_000);
                    p.item.char_inventory(5);
                    p.conn.moc_nap.add("1000000");
                    p.conn.save_moc_nap();
                    Service.send_notice_box(p.conn, "Nhận thành công mốc 1tr"
                            + "\n+ Uot game vào lại để thấy hết quà ");
                } else {
                    Service.send_notice_box(p.conn, "Nạp chưa đủ");
                }
                break;
            }
            case 4: { // mốc 1m5
                if (p.get_tongnap() >= 1_500_000 && !p.check_moc_nap(1_500_000)) {
                    //ađc ct Giáp Sừng
                    ItemTemplate3 item = ItemTemplate3.item.get(4741);
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
                    Item47 it = new Item47();
                    it.category = 7;
                    it.id = 14;
                    it.quantity = (short) (80);
                    p.item.add_item_bag47(7, it);

                    p.update_vang(100_000_000);
                    p.update_ngoc(5_000);
                    p.update_coin(200_0000);
                    p.item.char_inventory(5);
                    p.conn.moc_nap.add("1500000");
                    p.conn.save_moc_nap();
                    Service.send_notice_box(p.conn, "Nhận thành công mốc 1tr5"
                            + "\n+ Uot game vào lại để thấy hết quà ");
                } else {
                    Service.send_notice_box(p.conn, "Nạp chưa đủ");
                }
                break;
            }
            case 5: { // mốc 2m
                if (p.get_tongnap() >= 2_000_000 && !p.check_moc_nap(2_000_000)) {
                    //ađc ct  Giáp kỵ sĩ rồng
                    ItemTemplate3 item = ItemTemplate3.item.get(4760);
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
                    Item47 it = new Item47();
                    it.category = 7;
                    it.id = 14;
                    it.quantity = (short) (120);
                    p.item.add_item_bag47(7, it);

                    p.update_vang(200_000_000);
                    p.update_ngoc(7_500);
                    p.update_coin(300_0000);
                    p.item.char_inventory(5);
                    p.conn.moc_nap.add("2000000");
                    p.conn.save_moc_nap();
                    Service.send_notice_box(p.conn, "Nhận thành công mốc 2tr"
                            + "\n+ Uot game vào lại để thấy hết quà ");
                } else {
                    Service.send_notice_box(p.conn, "Nạp chưa đủ");
                }
                break;
            }
            case 6: { // mốc 3m
                if (p.get_tongnap() >= 3_000_000 && !p.check_moc_nap(3_000_000)) {
                    //ađc ct Giáp kỵ sĩ rồng cam
                    ItemTemplate3 item = ItemTemplate3.item.get(4759);
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
                    Item47 it = new Item47();
                    it.category = 7;
                    it.id = 14;
                    it.quantity = (short) (200);
                    p.item.add_item_bag47(7, it);

                    p.update_vang(500_000_000);
                    p.update_ngoc(10_000);
                    p.update_coin(300_000);
                    p.item.char_inventory(5);
                    p.conn.moc_nap.add("3000000");
                    p.conn.save_moc_nap();
                    Service.send_notice_box(p.conn, "Nhận thành công mốc 3tr"
                            + "\n+ Uot game vào lại để thấy hết quà ");
                } else {
                    Service.send_notice_box(p.conn, "Nạp chưa đủ");
                }
                break;
            }

        }

    }
}
