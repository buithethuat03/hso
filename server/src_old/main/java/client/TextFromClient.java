package client;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import core.Log;
import core.Manager;
import core.SQL;
import core.Service;
import core.Util;
import io.Message;
import io.Session;
import map.MapService;
import template.Item3;
import template.Item47;
import template.ItemTemplate3;
import template.ItemTemplate4;
import template.ItemTemplate7;
import template.Level;

public class TextFromClient {

    public static void process(Session conn, Message m2) throws IOException {
        short idnpc = m2.reader().readShort();
        short idmenu = m2.reader().readShort();
        byte size = m2.reader().readByte();
        if (idmenu != 0) {
            return;
        }
        switch (idnpc) {
            case 0: {
                if (size != 1) {
                    return;
                }
                if (conn.status != 0) {
                    Service.send_notice_box(conn, "Tài khoản chưa được kích hoạt, hãy kích hoạt");
                    return;
                }
                String text = m2.reader().readUTF();
                text = text.toLowerCase();
                Pattern p = Pattern.compile("^[a-zA-Z0-9]{1,15}$");
                if (!p.matcher(text).matches()) {
                    Service.send_notice_box(conn, "Đã xảy ra lỗi");
                    return;
                }
                for (String txt : conn.p.giftcode) {
                    txt = txt.toLowerCase();
                    if (txt.equals((text))) {
                        Service.send_notice_box(conn, "Bạn đã nhập giftcode này rồi");
                        return;
                    }
                }
                try ( Connection connection = SQL.gI().getConnection();  Statement st = connection.createStatement();  ResultSet rs = st.executeQuery("SELECT * FROM `giftcode` WHERE `giftname` = '" + text + "';")) {
                    if (!rs.next()) {
                        Service.send_notice_box(conn, "Giftcode đã được nhập hoặc không tồn tại");
                    } else {
                        int limit = rs.getInt("limit");
                        if (limit < 1) {
                            Service.send_notice_box(conn, "Đã hết lượt dùng giftcode này");
                        } else if (conn.p.item.get_bag_able() > 2) {
                            conn.p.giftcode.add(text);
                            JSONArray jsar = (JSONArray) JSONValue.parse(rs.getString("item3"));
                            for (int i = 0; i < jsar.size(); i++) {
                                JSONArray jsar2 = (JSONArray) JSONValue.parse(jsar.get(i).toString());
                                Item3 itbag = new Item3();
                                short it = Short.parseShort(jsar2.get(0).toString());
                                itbag.id = it;
                                itbag.name = ItemTemplate3.item.get(it).getName();
                                itbag.clazz = ItemTemplate3.item.get(it).getClazz();
                                itbag.type = ItemTemplate3.item.get(it).getType();
                                itbag.level = ItemTemplate3.item.get(it).getLevel();
                                itbag.icon = ItemTemplate3.item.get(it).getIcon();
                                itbag.op = ItemTemplate3.item.get(it).getOp();
                                itbag.color = ItemTemplate3.item.get(it).getColor();
                                itbag.part = ItemTemplate3.item.get(it).getPart();
                                itbag.tier = 0;
                                itbag.time_use = 0;
                                itbag.islock = false;
                                conn.p.item.add_item_bag3(itbag);
                            }
                            jsar.clear();
                            //
                            jsar = (JSONArray) JSONValue.parse(rs.getString("item4"));
                            for (int i = 0; i < jsar.size(); i++) {
                                JSONArray jsar2 = (JSONArray) JSONValue.parse(jsar.get(i).toString());
                                Item47 itbag = new Item47();
                                itbag.id = Short.parseShort(jsar2.get(0).toString());
                                itbag.quantity = Short.parseShort(jsar2.get(1).toString());
                                itbag.category = 4;
                                conn.p.item.add_item_bag47(4, itbag);
                            }
                            jsar.clear();
                            //
                            jsar = (JSONArray) JSONValue.parse(rs.getString("item7"));
                            for (int i = 0; i < jsar.size(); i++) {
                                JSONArray jsar2 = (JSONArray) JSONValue.parse(jsar.get(i).toString());
                                Item47 itbag = new Item47();
                                itbag.id = Short.parseShort(jsar2.get(0).toString());
                                itbag.quantity = Short.parseShort(jsar2.get(1).toString());
                                itbag.category = 7;
                                conn.p.item.add_item_bag47(7, itbag);
                                
                            }
                            jsar.clear();
                            //
                            int vang_up = rs.getInt("vang");
                            int ngoc_up = rs.getInt("ngoc");
                            conn.p.update_vang(vang_up);
                            conn.p.update_ngoc(ngoc_up);
                            Log.gI().add_log(conn.p.name,
                                    "Nhận giftcode " + text + " : " + Util.number_format(vang_up) + " vàng");
                            Log.gI().add_log(conn.p.name,
                                    "Nhận giftcode " + text + " : " + Util.number_format(ngoc_up) + " ngọc");
                            conn.p.item.char_inventory(3);
                            conn.p.item.char_inventory(4);
                            conn.p.item.char_inventory(7);
                            
                            Service.send_notice_box(conn, "Nhận thành công giftcode");
                        } else {
                            Service.send_notice_box(conn, "Hành trang phải trống 3 ô trở lên!");
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                break;
            }
            case 1: {
                if (conn.user.equals("admin")) {
                    if (size != 3) {
                        return;
                    }
                    String type = m2.reader().readUTF();
                    String id = m2.reader().readUTF();
                    String quantity = m2.reader().readUTF();
                    if (!(Util.isnumber(id) && Util.isnumber(quantity))) {
                        Service.send_notice_box(conn, "Dữ liệu nhập không phải số!!");
                        return;
                    }
                    if (conn.p.item.get_bag_able() > 0) {
                        switch (type) {
                            case "3": {
                                short iditem = (short) Integer.parseInt(id);
                                if (iditem > (ItemTemplate3.item.size() - 1) || iditem < 0) {
                                    return;
                                }
                                Item3 itbag = new Item3();
                                itbag.id = iditem;
                                itbag.name = ItemTemplate3.item.get(iditem).getName();
                                itbag.clazz = ItemTemplate3.item.get(iditem).getClazz();
                                itbag.type = ItemTemplate3.item.get(iditem).getType();
                                itbag.level = ItemTemplate3.item.get(iditem).getLevel();
                                itbag.icon = ItemTemplate3.item.get(iditem).getIcon();
                                itbag.op = ItemTemplate3.item.get(iditem).getOp();
                                itbag.color = ItemTemplate3.item.get(iditem).getColor();
                                itbag.part = ItemTemplate3.item.get(iditem).getPart();
                                itbag.tier = 0;
                                itbag.islock = false;
                                itbag.time_use = 0;
                                conn.p.item.add_item_bag3(itbag);
                                conn.p.item.char_inventory(3);
                                break;
                            }
                            case "4": {
                                short iditem = (short) Integer.parseInt(id);
                                if (iditem > (ItemTemplate4.item.size() - 1) || iditem < 0) {
                                    return;
                                }
                                Item47 itbag = new Item47();
                                itbag.id = iditem;
                                itbag.quantity = Short.parseShort(quantity);
                                itbag.category = 4;
                                conn.p.item.add_item_bag47(4, itbag);
                                conn.p.item.char_inventory(4);
                                break;
                            }
                            case "7": {
                                short iditem = (short) Integer.parseInt(id);
                                if (iditem > (ItemTemplate7.item.size() - 1) || iditem < 0) {
                                    return;
                                }
                                Item47 itbag = new Item47();
                                itbag.id = iditem;
                                itbag.quantity = Short.parseShort(quantity);
                                itbag.category = 7;
                                conn.p.item.add_item_bag47(7, itbag);
                                conn.p.item.char_inventory(7);
                                break;
                            }
                        }
                        Service.send_notice_box(conn, "Nhận Item thành công");
                    }
                }
                break;
            }
            case 2: {
                if (conn.user.equals("admin")) {
                    if (size != 1) {
                        return;
                    }
                    String level = m2.reader().readUTF();
                    if (!(Util.isnumber(level))) {
                        Service.send_notice_box(conn, "Dữ liệu nhập không phải số!!");
                        return;
                    }
                    int levelchange = Integer.parseInt(level);
                    if (levelchange < 2) {
                        levelchange = 2;
                    }
                    if (levelchange > Manager.gI().lvmax) {
                        levelchange = Manager.gI().lvmax;
                    }
                    conn.p.level = (short) (levelchange - 1);
                    conn.p.exp = Level.entrys.get(levelchange - 2).exp - 1;
                    conn.p.tiemnang = (short) (1 + Level.get_tiemnang_by_level(conn.p.level - 1));
                    conn.p.kynang = (short) (1 + Level.get_kynang_by_level(conn.p.level - 1));
                    conn.p.point1 = (short) (4 + conn.p.level);
                    conn.p.point2 = (short) (4 + conn.p.level);
                    conn.p.point3 = (short) (4 + conn.p.level);
                    conn.p.point4 = (short) (4 + conn.p.level);
                    conn.p.update_Exp(1, false);
                    Service.send_char_main_in4(conn.p);
		for (int i = 0; i < conn.p.map.players.size(); i++) {
			Player p0 = conn.p.map.players.get(i);
			if (p0.id != conn.p.id && (Math.abs(p0.x - conn.p.x) < 200) && (Math.abs(p0.y - conn.p.y) < 200)) {
				MapService.send_in4_other_char(p0.map, p0, conn.p);
			}
		}
                    Service.send_notice_box(conn, "Up level thành công");
                }
                break;
            }
            case 3: {
                if (size != 1) {
                    return;
                }
                String vang_join = m2.reader().readUTF();
                if (!(Util.isnumber(vang_join))) {
                    Service.send_notice_box(conn, "Dữ liệu nhập không phải số!!");
                    return;
                }
                int vang_join_vxmm = Integer.parseInt(vang_join);
                if (vang_join_vxmm < 10000 || conn.p.get_vang() < vang_join_vxmm) {
                    Service.send_notice_box(conn, "vàng không đủ!");
                    return;
                }
                if (vang_join_vxmm > 200_000_000) {
                    Service.send_notice_box(conn, "tối đa 200tr vàng!");
                    return;
                }
                Manager.gI().vxmm.join_vxmm(conn.p, vang_join_vxmm);
                break;
            }
            case 4: {
                if (size != 1) {
                    return;
                }
                String xp = m2.reader().readUTF();
                if (!(Util.isnumber(xp))) {
                    Service.send_notice_box(conn, "Dữ liệu nhập không phải số!!");
                    return;
                }
                int xp_ = Integer.parseInt(xp);
                if (xp_ > 1) {
                    Manager.gI().exp = xp_;
                }
                Service.send_notice_box(conn, "Thay đổi xp thành công x" + Util.number_format(xp_));
                break;
            }
            case 5: {
                if (size != 1) {
                    return;
                }
                String value = m2.reader().readUTF();
                if (!(Util.isnumber(value))) {
                    Service.send_notice_box(conn, "Dữ liệu nhập không phải số!!");
                    return;
                }
                int coin_exchange = Integer.parseInt(value);
                if (coin_exchange <= 0 || coin_exchange > 1_000_000_000) {
                    Service.send_notice_box(conn, "Số nhập không hợp lệ, hãy thử lại");
                    return;
                }
                if (conn.p.update_coin(-coin_exchange)) {
                    conn.p.update_ngoc(coin_exchange / 4);
                    conn.p.item.char_inventory(5);
                    Service.send_notice_box(conn, "Đổi thành công");
                }
                break;
            }
            case 6: {
                if (size != 2) {
                    return;
                }
                String value1 = m2.reader().readUTF();
                String value2 = m2.reader().readUTF();
                Pattern p = Pattern.compile("^[a-zA-Z0-9]{5,15}$");
                if (!p.matcher(value1).matches() || !p.matcher(value2).matches()) {
                    Service.send_notice_box(conn, "Ký tự không hợp lệ, hãy thử lại");
                    return;
                }
                //
                // try (Connection connnect = SQL.gI().getConnection();
                // PreparedStatement ps = connnect.prepareStatement(
                // "INSERT INTO `sm_hso2`.`account` (`user`, `pass`, `char`, `status`, `lock`, `coin`) VALUES ('"
                // + value1 + "', '" + value2 + "', '[]', 0, 0, 0)")) {
                // if (!ps.execute()) {
                // connnect.commit();
                // }
                // } catch (SQLException e) {
                // e.printStackTrace();
                // return;
                // }
                String query = "UPDATE `account` SET `user` = '" + value1 + "', `pass` = '" + value2 + "' WHERE `user` = '"
                        + conn.user + "' LIMIT 1";
                try ( Connection connnect = SQL.gI().getConnection();  Statement statement = connnect.createStatement();) {
                    if (statement.executeUpdate(query) > 0) {
                        connnect.commit();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    Service.send_notice_box(conn, "Có lỗi xảy ra hoặc tên đã được sử dụng, hãy thử lại");
                    return;
                }
                Message md = new Message(31);
                md.writer().writeUTF(value1);
                md.writer().writeUTF(value2);
                conn.addmsg(md);
                md.cleanup();
                conn.user = value1;
                conn.pass = value2;
                Service.send_notice_box(conn,
                        "Đăng ký thành công tài khoản :\n Tên đăng nhập : " + value1 + "\nMật khẩu : " + value2);
                break;
            }
            case 7: {
                if (size != 1 || conn.p.fusion_material_medal_id == -1) {
                    return;
                }
                String value = m2.reader().readUTF();
                if (!(Util.isnumber(value))) {
                    Service.send_notice_box(conn, "Dữ liệu nhập không phải số!!");
                    return;
                }
                int quant = Integer.parseInt(value);
                if (quant > 32000) {
                    Service.send_notice_box(conn, "Số lượng quá lớn!");
                    return;
                }
                int quant_inbag = conn.p.item.total_item_by_id(7, conn.p.fusion_material_medal_id);
                int quant_real = quant_inbag / 5;
                short id_next_material = (short) (conn.p.fusion_material_medal_id + 100);
                String name_next_material = ItemTemplate7.item.get(id_next_material).getName();
                if ((quant_real - quant) >= 0) {
                    if ((quant * 5000) > conn.p.get_vang()) {
                        Service.send_notice_box(conn, "Vàng không đủ!");
                        return;
                    }
                    conn.p.update_vang(-(quant * 5000));
                    conn.p.item.remove(7, conn.p.fusion_material_medal_id, (quant * 5));
                    Item47 it = new Item47();
                    it.id = id_next_material;
                    it.quantity = (short) quant;
                    conn.p.item.add_item_bag47(7, it);
                    conn.p.item.char_inventory(7);
                    //
                    Message m = new Message(-105);
                    m.writer().writeByte(2);
                    m.writer().writeByte(3);
                    m.writer().writeUTF("Chúc mừng bạn nhận được " + quant + " " + name_next_material);
                    m.writer().writeShort(id_next_material);
                    m.writer().writeByte(7);
                    conn.addmsg(m);
                    m.cleanup();
                } else {
                    Service.send_notice_box(conn, "Chỉ có thể hợp thành tối đa " + quant_real + " " + name_next_material);
                }
                conn.p.fusion_material_medal_id = -1;
                break;
            }
            case 8:
            case 9: {
                if (size != 1) {
                    return;
                }
                String value = m2.reader().readUTF();
                if (!(Util.isnumber(value))) {
                    Service.send_notice_box(conn, "Dữ liệu nhập không phải số!!");
                    return;
                }
                int quant = Integer.parseInt(value);
                if (quant > 2_000_000_000 || quant <= 0) {
                    Service.send_notice_box(conn, "Số lượng không hợp lệ!");
                    return;
                }
                if (idnpc == 8) {
                    if (quant > conn.p.get_vang()) {
                        Service.send_notice_box(conn, "Vàng không đủ!");
                        return;
                    }
                    conn.p.myclan.member_contribute_vang(conn, quant);
                } else {
                    if (quant > conn.p.get_ngoc()) {
                        Service.send_notice_box(conn, "Ngọc không đủ!");
                        return;
                    }
                    conn.p.myclan.member_contribute_ngoc(conn, quant);
                }
                break;
            }
            default: {
                Service.send_notice_box(conn, "Đã xảy ra lỗi");
                break;
            }
        }
    }
}
