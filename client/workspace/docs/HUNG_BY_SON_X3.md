# Hung By Son 650ms và x3

Bản đơn và cả ba slot hiện có dòng EXP ước lượng lưu theo level:
`Caculating...` khi chưa đủ mẫu, hoặc `(37.4M/100M)` khi đã học/load được.
Khung chữ nhật giữ chiều rộng 96px như bản gốc, thêm chiều cao cho dòng EXP. Bảng EXP được dùng chung giữa các nhân vật/server và cả ba slot; phần lấy mẫu mỗi phiên, tài khoản và các dữ liệu quái vẫn riêng từng slot.
Các thông báo giữ vị trí native ban đầu vì khung đã trở về chiều rộng gốc.
Chi tiết: [HUNG_BY_SON_EXP_APPROX.md](HUNG_BY_SON_EXP_APPROX.md).

Artifact: `client/jar/Hung_By_Son.jar` và `client/jar/Hung_By_Son_x3.jar`.
Hash/trạng thái phát hành ở [RELEASES.md](RELEASES.md).

Bản economy ngày 2026-10-29 tính EXP/giờ và vàng/giờ sau 5 phút đầu, tăng
thời gian đo tới 15 phút rồi trượt theo 15 phút gần nhất. Số dư, tổng vàng nhận,
cửa sổ đo và chế độ thông tin BẬT/GIẢN LƯỢC/TẮT riêng từng client. Cả ba slot
có tab `Vàng / phút` và dòng hành trang vàng đậm dưới SET1/2/3. Số EXP K/M dưới thanh
dùng cùng phần trăm nội suy, cập nhật trong cùng bước 0,1% native. Chi tiết:
[thông tin EXP](HUNG_BY_SON_EXP_INFO.md), [nội suy EXP](HUNG_BY_SON_EXP_APPROX.md).

Ngày 2026-10-28, cả x1 và ba slot x3 đã loại `Lửa thiêng`, `con Ma` khỏi cache
xác quái (cùng `Xà nữ`), kể cả dữ liệu cũ. Đường focus người chơi khi bật Mode
chiến trường có màu cam, Xà nữ vẫn đỏ. X3 giữ identity/RMS và EXP dùng chung
như bản trước. Chi tiết: [Mode chiến trường](HUNG_BY_SON_BATTLEFIELD.md).

X3 hiện hành ngày 11/10/2026 lấy bản ứng viên đã kiểm thử ở
`build/hung-crowded-monsters-20261011-e/Hung_By_Son_crowded_fix.jar`. Cả ba client
có đầy đủ sửa HP dương/gỡ xác chết, nhận chi tiết đến sớm/muộn, giảm lookup và
gỡ rào cản animation do mục tiêu còn mang cờ chết; HUD thêm P/V. Chi tiết và
giới hạn server thật: [HUNG_BY_SON_CROWDED_FIX.md](HUNG_BY_SON_CROWDED_FIX.md).
Suite/RMS của X3 giữ nguyên nên tiếp tục các store của X3 cũ. Bản đơn phát hành
`Hung_By_Son.jar` nay lấy đúng nền crowded fix này, thêm EXP và sửa thông báo.

Nguồn là Investigate đã sửa giữ xác chết, cờ xóa, nhận HP độc lập với
animation và tăng thời gian bỏ mục tiêu HP cũ. Dùng cơ chế timing của Updated:
gate auto strict >650ms thực; request strict >max(650ms, khoảng riêng của skill).
Giữ frame gửi khi chưa đủ thời gian và đọc `System.currentTimeMillis()` trực
tiếp, nên menu tốc độ không rút ngắn ngưỡng. Skill cooldown vẫn có hiệu lực.
650ms là ngưỡng tối thiểu, không cam kết mọi packet cách nhau đúng 650ms.

Bản đơn giữ tên/vendor/RMS của nguồn Investigate để tiếp tục dữ liệu của
nguồn đó; chỉ tên file đổi thành `Hung_By_Son.jar` theo yêu cầu. Bản 500ms
trước khi thay được giữ ở backup; script Investigate dùng backup 500ms này
làm input pinned cho pipeline feature, không dùng release 650ms mới.

## Dùng x3

Một launcher `MrQuyet.MrQuyet` mở ba virtual client. Chọn dòng để mở client,
giữ `*` khoảng 500ms để về menu, rồi chọn dòng khác. Client đã mở giữ instance
và vòng lặp game; không phải ba native MIDlet khởi chạy độc lập.

| Dòng | Virtual MIDlet | Namespace | RMS |
|---|---|---|---|
| Hung By Son 1 | TemMidlet | default | a + inv_ + tên store |
| Hung By Son 2 | b.TemMidlet | b/ | b + inv_ + tên store |
| Hung By Son 3 | c.TemMidlet | c/ | c + inv_ + tên store |

X3 có suite `Hung_By_Son X3`, vendor `Hung_By_Son_X3`, tách khỏi bản đơn.
Đăng nhập, chọn quái, cache, menu mod và trạng thái runtime riêng từng client.
Không tự nhập dữ liệu RMS của bản đơn vào x3. Bản đơn và ba client đều giữ
các sửa quái trong [HUNG_BY_SON_INVESTIGATE.md](HUNG_BY_SON_INVESTIGATE.md).

## Đóng gói và kiểm tra

```powershell
.\tools\luna\build-hung-crowded-x3.ps1 -DependencyDir <dependencies> -BuildTag hung-crowded-x3-new
```

Script tạo stage mới, không tự publish. Dependencies gồm ASM 9.7, ASM Tree và
MicroEmulator, lấy từ tham số hoặc `LUNA_DEPS`. Input pinned là ứng viên sửa
quái ngày 11/10 đã đạt các kiểm tra source; template x3 pinned là
`../jar/backup/autotrade_v27_revive05_horse_x3.jar`. Script sao chép binary nguồn
đã kiểm thử, không thay đổi timing hoặc compile lại support cũ vào JAR.

`HungBySonX3Packer` dùng remapper/lifecycle của pipeline HSO CHILD x3, giữ 19
class wrapper và icon của template byte-identical. MIDlet Hung được chuyển
từ package game về `TemMidlet` để phù hợp launcher/Static. Các class game,
API/bridge và support đều nhân đủ ba namespace. Nested class được cho phép
public ở ranh giới đóng gói để `Static` gọi hook reset, không mở private field.
Native canvas chuyển callback sang `PAINT/leyPressed/leyReleased`; paths
resource được đưa về root và các store đi qua alias riêng của wrapper.

751 class x3 đã link với `-Xverify:all`; 1.741 kiểm tra xác nhận parity đầy đủ
giữa ba namespace, tài nguyên, gate khởi tạo 650ms, save/load/list/delete,
cache/selection và clear/restart riêng. Có thêm 74 kiểm cache mới trên cả ba slot
trong cùng classloader, gồm K/W, P/V riêng, nhận chi tiết sớm và chặn placeholder
chưa xác nhận. Source đơn qua 45.044 kiểm cache mới/6 giờ mô phỏng và 2.502 kiểm
timing ở năm tốc độ, native HP/world/animation guard và các hồi quy khác.
Launcher đã mở ba client,
giữ `*`, chuyển 1 → 2 → 3 → 1 và kiểm canvas/instance/vòng lặp còn sống; ảnh
màn hình đăng nhập đã kiểm. Hung tạo thread mà không giữ field Thread trên
canvas, nên fixture kiểm thêm stack `run` của đúng class từng namespace.

Các kiểm tra dùng memory RMS và chưa đăng nhập/chơi ba tài khoản trên server
thật. Sau thay JAR cần thoát hẳn phiên cũ và mở lại file mới.
