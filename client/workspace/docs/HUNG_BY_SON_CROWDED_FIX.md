# Hung By Son — bản sửa quái kẹt xác chết, 11/10/2026

Đây là nền đã kiểm chứng trong `build/hung-crowded-monsters-20261011-e/`.
Ngày 13/10/2026, bản đơn phát hành lấy đúng nền này, thêm EXP ước lượng và sửa
vị trí thông báo, mang tên `client/jar/Hung_By_Son.jar`. Chưa thử đăng nhập trên
server thật. Hash và bằng chứng kiểm tra được ghi tại [RELEASES.md](RELEASES.md).

Bản x3 từ ứng viên này đã phát hành thành `client/jar/Hung_By_Son_x3.jar`, giữ
650ms và có đủ bản sửa trong cả ba client. Cách dùng và kiểm tra namespace/RMS:
[HUNG_BY_SON_X3.md](HUNG_BY_SON_X3.md).

## Kết quả điều tra

Ảnh báo lỗi có `C:55 K:52 W:45`: 52 trong 55 bản ghi đang được giữ thành xác
chết, 45 mục chờ xác minh. `OK` gồm cả phản hồi HP bằng 0, nên tăng OK không có
nghĩa quái đã sống lại. Cache RMS không lưu HP hoặc cờ sống/chết qua phiên.

Hai lỗi được tái hiện trên bản đầu vào thực tế:

- Gói đánh mang HP dương cập nhật HP, nhưng không gỡ `corpseHeld`. Hook cập nhật
  world sau đó gọi bridge giữ xác và ghi HP về 0. Tái hiện: HP 37 → HP 0.
- Quái đã xác nhận, đang được mượn để kiểm tra hồi sinh, không nhận được phản hồi
  chi tiết hợp lệ nếu phản hồi đến trước lượt gửi đầu tiên của hàng đợi.

Ngoài ra, packet chi tiết native ghi HP trước observer. Một phản hồi bị từ chối
có thể để lại HP sai trên object, nhất là query của bộ theo dõi mục tiêu hoặc
object vừa được packet danh sách nạp lần đầu.

Người dùng còn báo người chơi xung quanh mất animation đánh khi khu đông khoảng
15 người, trong khi nhân vật của mình vẫn đánh/nhặt đồ/cập nhật HP, MP. Chưa tìm
thấy điều kiện cứng 15 người trong receiver đánh, lookup object, vòng world hoặc
helper animation đã kiểm tra. Native lookup quét toàn bộ vector object; các hook
cũ còn lặp lại lookup cho từng mục tiêu và từng lần cập nhật quái sống. Chi phí
này tăng theo mật độ object và gói đánh, nhưng chưa có trace thật để chứng minh
đó là nguyên nhân duy nhất của hiện tượng mất animation.

Có một liên hệ native đã xác minh: vector mục tiêu vẫn nhận quái giữ xác, nhưng
helper animation trả về sớm và dọn trạng thái đánh của người đánh nếu người đó
hoặc mục tiêu đầu tiên còn ở trạng thái chết. Native cập nhật HP sau lời gọi
helper. Bản cũ chỉ ghi HP ở observer, để lại cờ animation chết; bản ứng viên gỡ
cờ trước khi helper chạy. Điều này loại bỏ được rào cản animation cho mục tiêu
đã xác nhận và có HP combat dương hợp lệ, nhưng chưa chứng minh mọi lần người
xung quanh đứng yên trên server thật đều do cùng nguyên nhân.

## Thay đổi của ứng viên

- HP combat dương, hợp lệ và thuộc đúng object đã xác nhận trong context hiện
  tại gỡ trạng thái xác, retry/backoff và lượt recovery dư thừa ngay. Không dùng
  HP tối đa làm HP hiện tại, không xác nhận object chưa có bằng chứng identity.
- Bridge chỉ dọn cờ/counter chết, chờ xóa và quarantine khi object còn mang các
  cờ này. Không đổi vị trí theo tọa độ chi tiết cũ; không reset animation của
  object đã sống bình thường.
- Chi tiết đến trước lượt gửi được chấp nhận cho identity đã xác nhận, đang mượn
  object hiện có. Placeholder cache sở hữu, chưa được request hoặc packet danh
  sách xác nhận vẫn giữ quarantine.
- Phản hồi chi tiết sai được quarantine trên đúng object hiện tại và tiếp tục
  retry; timeout watchdog đơn thuần không tự ép quái sống thành chết.
- HP 0 liên tục không hủy recovery đang chờ và không dời deadline hỏi lại vô hạn.
  Giữ request cách nhau ít nhất 400ms, tối đa hai lần/lượt và backoff 20/40/60s.
- Khi quái sống lại với HP bằng đúng baseline cũ, gỡ cả lượt chờ/exclusion của
  bộ theo dõi mục tiêu ngay; HP dương không đổi của quái đang sống vẫn giữ query.
- Quái sống không cần quét vector để xử lý xác. Victim chưa được xác nhận cũng
  không gây lookup world. Watch chỉ lookup thêm khi HP thuộc mục tiêu đang theo
  dõi. Kiểm thử 55 victim hợp lệ dùng tối đa 55 lookup; victim không biết dùng 0.
- Không đổi thời gian đánh 650ms, packet gửi, PK, resource, manifest, RMS hay các
  class native game. Chỉ thay hai họ support và method HP trong bridge hiện có.

HUD giữ các chỉ số cũ và thêm hai số đặt lại khi bắt đầu phiên map/khu:

| Chỉ số | Ý nghĩa |
|---|---|
| `P` | Số gói đánh quái đi vào hook nhận trước khi parser kiểm tra, không phải số đòn đánh riêng của người khác. |
| `V` | Số lượt gỡ trạng thái chết/chờ recovery nhờ HP combat dương hợp lệ; một quái có thể tăng nhiều lần. |

## Kiểm tra

Fixture mới mô phỏng 55 quái qua 720 chu kỳ trong sáu giờ, 43.200 tick cách 500ms,
chi tiết bị mất/đến muộn, packet thiếu/sai, cùng ID thay object/đổi context, HP 0
liên tục 65 giây, phục hồi qua chi tiết mà không đổi map và watchdog trùng HP
baseline. 45.044 kiểm tra đạt.
Kiểm thử bytecode chạy bridge HP và nhánh xóa world trích từ JAR: 27 kiểm tra
đạt, gồm giữ tọa độ, giữ animation quái sống, gỡ counter và không nhận replacement
chưa xác nhận. Đây là mô phỏng boundary, không thay thế gameplay server thật.

Thêm 321 kiểm tra chạy đoạn guard/cleanup được sao chép từ bytecode helper
animation thực tế, giữ nguyên nhánh và field đã xác minh: bản ứng viên gỡ được
rào cản khi quái đã xác nhận nhận HP 37; người đánh chết, replacement hoặc quái
chưa xác nhận vẫn bị chặn. Đối chứng dùng support/bridge gốc từ JAR đầu vào:
HP đã lên 37 nhưng animation chết còn nguyên nên guard vẫn chặn. Kiểm tra này
chạy nhánh native được trích, không render trọn animation; log bổ sung là
`remote-animation-barrier.txt` trong thư mục build ứng viên.

Các hồi quy cache, watchdog, chọn quái/HUD/native menu, death/recovery native,
combat thật 650ms, semantic/controller/route/combat/material và startup emulator
được chạy trong pipeline. Xem các log và `verified.json` ở thư mục build ứng viên.
Verifier giữ 1.020 entries, 242 classes, 731 resources; major 48/51, byte resource
và manifest nguyên vẹn, 242 class link với `-Xverify:all`. Chỉ tám class thay đổi.

Build lại từ bản phát hành đầu vào đã pin:

```powershell
.\tools\luna\build-hung-crowded-monsters.ps1 -DependencyDir <dependency-directory> -BuildTag <new-tag>
```

## Kiểm chứng trên server thật

Test bản ứng viên ở khu ít người và đông người, đứng xa bãi quái đã học, qua
nhiều chu kỳ rồi vài giờ. Khi gói đánh có HP dương tới, `V` tăng, `K` giảm và
quái phải thoát animation chết; `W` không được tích lũy vì các lượt recovery đã
được combat xác minh.

Nếu người xung quanh vẫn đứng yên, có thể tắt Auto của nhân vật mình khoảng
10 giây và theo dõi `P`, sau đó bật lại. `P` tăng cho thấy hook vẫn nhận gói đánh
quái; nó chưa chứng minh native đã vẽ animation. `P` đứng yên chỉ nói hook này
không nhận thêm gói tương ứng, không đủ để quy kết server hoặc mạng. Ghi số người,
P/V/K/W trước và sau, vị trí gần/xa và thời gian từ lúc vào khu để xác định đường
xử lý còn thiếu.

Không thể bảo đảm biết thời điểm hồi sinh nếu server không gửi HP mới hợp lệ và
mọi request chi tiết đều mất phản hồi. Ứng viên tiếp tục retry hữu hạn theo nhịp
giới hạn; phần mất animation của người khác cần bằng chứng runtime riêng trước
khi khẳng định đã sửa hoàn toàn.
