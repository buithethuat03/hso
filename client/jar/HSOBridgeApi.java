/**
 * Cầu nối giữa một class mod (nằm ngoài jar game, viết bằng Java bình thường) và
 * các hàm bên trong jar game đã bị obfuscate.
 *
 * Class mod không thể gọi trực tiếp `a.b.c(int, int)` vì tên đó đổi theo từng bản
 * jar. Cách làm: patcher dùng ASM tự sinh một class implement interface này ngay
 * trong jar (ví dụ `HSOReviveBridge`), mỗi hàm ở đây được sinh thành lời gọi tới
 * đúng method/field đã dò được của bản jar đang patch. Class mod chỉ cần:
 *
 *     bridge = (HSOBridgeApi) Class.forName("HSOReviveBridge").newInstance();
 *
 * rồi gọi bằng tên có nghĩa. Đổi bản jam game thì chỉ patcher phải dò lại, code mod
 * không phải sửa.
 *
 * Xem HSOReviveNorthPatcher.java để biết cách sinh class bridge bằng ASM.
 */
public interface HSOBridgeApi {

    /** Trả lời menu server gửi xuống (menu id, mục chọn, tham số). */
    void menuReply(int menuId, int index, int arg);

    /** Gửi packet di chuyển tới (x, y) — chỉ báo vị trí, không tự làm nhân vật đi. */
    void move(int x, int y);

    /** Đặt thẳng toạ độ nhân vật ở phía client. Phải làm trước move() mới đi thật. */
    void setPosition(int x, int y);

    /** Hồi sinh: gửi opcode 31 payload 00 (về làng, không tốn ngọc). */
    void revive();

    /** Toạ độ x hiện tại của nhân vật. */
    int px();

    /** Toạ độ y hiện tại của nhân vật. */
    int py();

    /** HP hiện tại của nhân vật. */
    int hp();

    /** Giả lập chạm màn hình tại (x, y). */
    void tap(int x, int y);

    int screenWidth();

    int screenHeight();

    /** Toàn bộ field int của nhân vật, để dò index khi port sang bản jar mới (dumpFields=1). */
    int[] ints();
}
