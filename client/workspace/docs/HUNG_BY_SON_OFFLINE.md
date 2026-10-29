# Hung_By_Son — các tính năng MOD chạy cục bộ

Rà soát ngày 2026-10-02 trên JAR hiện hành. “Offline” ở đây là không cần máy chủ
MOD hoặc dịch vụ bản quyền; nhân vật vẫn kết nối server game để chơi, mua bán và
cường hóa. Kết quả, nguyên liệu và chi phí do server game quyết định.

## Danh sách tính năng

| Tính năng từng phụ thuộc dịch vụ MOD | Phụ thuộc cũ | Xử lý hiện tại |
|---|---|---|
| Kích hoạt, key, thời hạn và quyền dùng MOD | RPC bản quyền Supabase | Kích hoạt và quyền dùng tính năng được cấp trong client. Worker bản quyền không chạy. |
| Auto Farm và Auto Khu 2 | Quyền Auto Farm từ RPC | Dùng logic/cấu hình hiện có trong client với quyền cục bộ. |
| Auto Phó Bản | Quyền Phó Bản từ RPC | Dùng lịch, số lượt và đường đi hiện có trong client với quyền cục bộ. |
| Auto Cường Hóa | Quyền dùng và 5 chỉ dẫn từ Brain `/secret` | Tự chọn trang bị, tìm đúng cỏ trong túi, chọn cỏ, đập bằng vàng và áp dụng quy tắc Thông minh trong client. |
| Auto Đi Buôn V3 | Quyền dùng và 5 chỉ dẫn từ Brain `/secret` | Dùng thú trong túi; đọc cửa hàng đá quý, mua, bán và hồi sinh bằng luồng cục bộ đã có. |
| Thay đồ, Set 1/2/3 | Quyền thay set từ RPC | Dùng logic chọn/lưu/mặc set trong client với quyền cục bộ. |
| Tự Buff, Tự Đánh PvP, Tự Áp Sát PvP | Quyền Auto PK từ RPC | Dùng các lựa chọn và logic sẵn có trong client với quyền cục bộ. |
| Hướng dẫn sử dụng hiện sau đăng nhập | Nội dung hướng dẫn từ RPC | Tạo nội dung hướng dẫn trong client, bao gồm cách thêm trang bị vào hàng chờ. |

Rà soát tìm được bảy cờ quyền; một cờ không có nơi đọc trong JAR này. Không có
tính năng ẩn mới được tạo từ cờ đó. Các module Thu Mỏ, di chuyển, nhặt đồ, đổi khu,
hồi sinh thông thường và ghost captcha không có lời gọi dịch vụ chỉ dẫn MOD.

## Auto Cường Hóa

> **Đã thay từ x1 sau `C9E5073D…` (2026-10-27):** nút **Thêm vào DS** và dòng
> **Auto Cường Hóa >** giờ dẫn tới **Đập đồ** của ModUpLv, chạy ở mọi nơi, không
> qua pháp sư. Xem [HUNG_BY_SON_ENHANCE.md](HUNG_BY_SON_ENHANCE.md). Mục dưới đây là
> lịch sử của bản cũ và của X3 (chưa cập nhật).

1. Để trang bị trong túi. Chọn trang bị → mở menu của món đó → **Thêm vào DS**.
2. Mở **MOD HungDZ → Auto Cường Hóa >**.
3. Chọn cấp muốn đạt: mặc định +10, bấm để tăng từ +1 tới +15.
4. Chọn vật phẩm may mắn. Chế độ **Thông minh** theo yêu cầu người dùng:
   **chỉ dùng cỏ 4 lá khi cấp hiện tại là +8, +9 hoặc +10; mọi cấp khác không dùng cỏ**.
   Các lựa chọn thủ công Không dùng/Cỏ 3 lá/Cỏ 4 lá vẫn theo lựa chọn của người chơi.
5. Tắt Auto Phó Bản rồi chuyển **Cường hóa: [TẮT]** sang **[BẬT]**.

Client dùng đường đi và vị trí cường hóa sẵn có. Đạt cấp mục tiêu sẽ chuyển sang
món tiếp theo. Hàng chờ hết thì dừng và khôi phục trạng thái farm cũ. Chuyển về
[TẮT] để dừng, hoặc chọn **Xóa hàng chờ** để bỏ danh sách đã chọn.

Khi đến pháp sư, client đồng bộ vị trí rồi mở menu NPC, chọn dòng cường hóa
trang bị theo tên và chờ màn hình cường hóa native được mở. Sau đó mới chọn đồ
và gửi lệnh đập. Client dùng ID NPC/menu/hàng thực tế; không cố định số thứ tự
dòng và bỏ qua mục cường hóa thú/cánh. Một món đang được chọn từ thao tác trước
không được coi là xác nhận mới của server.

Nếu gói cường hóa trả về một thông báo dừng, client giữ nội dung game gửi thay
vì thay bằng timeout. Thông báo game chung nhận trong lúc chờ cũng được đưa
vào lỗi timeout nếu chưa có gói kết quả đầy đủ. Không gửi lại lệnh đập chỉ vì
không nhận được kết quả.

Client tìm ID cỏ từ dữ liệu túi mà server game đã gửi, kiểm cả loại vật phẩm và
tên cỏ. Thiếu loại cỏ đang yêu cầu sẽ dừng; không tự chuyển sang cỏ khác. Nếu có
cỏ đang chọn nhưng bước tiếp theo yêu cầu Không dùng, client bỏ chọn cỏ trước.

Các thao tác cách nhau ít nhất 500 ms, không phụ thuộc FPS. Sau một lần gửi đập,
client chờ gói kết quả rồi mới làm tiếp. Nếu tab cường hóa đang mở, animation
native chạy bình thường. Nếu auto chạy nền, client kết thúc trạng thái animation
500 ms sau khi đã nhận kết quả thật; tránh bị kẹt khi tab không được cập nhật.
Nếu server game không trả kết quả/xác nhận chọn đồ hoặc cỏ sau 12 giây, client
dừng với lý do cụ thể. Thời gian chờ bình thường không còn bị đếm thành lỗi
“không nhận được chỉ dẫn cường hóa”. Rời vị trí pháp sư thì auto quay lại bước
đi đến vị trí cường hóa.

## Những kết quả dịch vụ đã được tái tạo

| Khóa chỉ dẫn | Kết quả cục bộ |
|---|---|
| `ch.chon_do` | Gọi sender native opcode 67, chọn trang bị trong túi. |
| `ch.chon_bua` | Gọi sender native opcode 67, chọn cỏ đúng loại/ID đang có. |
| `ch.nang` | Gọi sender native opcode 67, cường hóa bằng vàng. |
| `ch.ma_bua` | Tra ID cỏ 3/4 lá trong dữ liệu túi; thiếu thì dừng. |
| `ch.bua_theo_cap` | Trả loại 2 (cỏ 4 lá) ở +8..+10; loại 0 ở mọi mức khác. |
| `buon_tay.thu_len` | Đồng bộ vị trí rồi dùng vật phẩm thú đã chọn. |
| `buon_tay.thu_xuong` | Dùng lại vật phẩm thú như luồng đi buôn đã có. |
| `buon.mua_gem` | Đọc loại cửa hàng/ID đá từ NPC, mua với nhịp tối thiểu 2 giây. |
| `buon.ban_gem` | Dùng menu bán đá quý của V3 và quyền kiểm tra cục bộ. |
| `buon.hoi_sinh` | Có vé thì hồi sinh tại chỗ; không có thì về làng. |

Bộ lấy chỉ dẫn không còn mở worker, lấy URL Brain hay chờ cache trả lời. Khóa
không được hỗ trợ không tạo kết nối MOD. Các trạng thái “bận”, “chờ máy chủ MOD”
và “máy chủ MOD từ chối key” không cản trở các luồng đã thay thế.

Hai nơi còn gọi `Connector.open` thuộc HTTP tải danh sách server game và socket
kết nối game. Rà soát nơi gọi HTTP cho thấy chỉ hai màn hình server-list native
dùng helper đó. WebSocket/CORS bridge thuộc kết nối game trên bản web.

## Kiểm chứng và giới hạn

- JAR có 999 entries/221 classes: bốn class thay đổi, thêm một support class.
  Resource, manifest và version của các class cũ giữ nguyên; support là major 48.
- Kiểm ZIP CRC, đối chiếu entries và kiểm lời gọi dịch vụ MOD đạt.
- JVM `-Xverify:all` liên kết toàn bộ 221 class nguyên bản đạt.
- Fixture chạy các class thực của release và bắt byte gói gửi trong bộ nhớ.
  93 kiểm tra đạt: quyền/guide cục bộ, quy tắc +8..+10, ID cỏ, vị trí NPC, packet
  chọn đồ/cỏ/đập/bỏ cỏ, nhịp 500 ms, đợi kết quả, animation, timeout và dừng.
- Regression Auto Đi Buôn V3 đạt: thú, cửa hàng, ID đá, nhịp mua, bán và hồi sinh.
- Regression vòng cường hóa đạt thêm 13 kiểm tra: tái hiện bản cũ dừng với lý do
  rỗng, chờ xác nhận không làm tắt auto, timeout có lý do, đập xong tiếp tục chọn
  đồ ở lần sau khi chạy nền, và giữ animation khi tab native đang mở.
- Bản bổ sung phiên NPC đạt 17 kiểm tra: đồng bộ vị trí, mở menu/dữ liệu NPC,
  chọn đúng dòng trang bị, chờ tab native, yêu cầu xác nhận chọn đồ mới, giữ
  thông báo cường hóa loại 3 và nội dung thông báo game loại 37. Bộ kiểm tra
  offline hiện có 94 kiểm tra; tổng ba fixture là 124, cộng regression đi buôn.
- Bạch Hổ vẫn ở cuối danh sách server. Chưa thử chọn server/đập đồ/đi buôn trong
  phiên game thật sau thay đổi này. Chưa xác minh tên cỏ và phản hồi của server
  game thực tế; fixture dùng dữ liệu túi và gói phản hồi mô phỏng.

Nguồn build: `tools/legacy/hsosky/HungBySonFullOfflinePatcher.java` và
`HungBySonOfflineLocal.java.in`. Fixture nằm ở `fixtures/hung-by-son/`.
Hash, backup và đường dẫn artifact được quản lý trong [RELEASES.md](RELEASES.md).
