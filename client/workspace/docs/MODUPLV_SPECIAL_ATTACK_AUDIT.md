# ModUpLv: tấn công đặc biệt so với Delay của Hung

Kiểm tra bytecode trực tiếp ngày 2026-10-23, không sửa JAR và chưa thử server thật.

| JAR | SHA-256 |
|---|---|
| ModUpLv.jar | E282B0AAD121205F9A0CFAF14104C9A9ED281B6838AE22D7FEDD4004E83F997C |
| Hung_By_Son.jar | DA429E64940D87852F212854AF793C992D99DDF039DED0B85CAD1747FD950646 |

## ModUpLv

Tên class/member bị obfuscate rất dài. Các alias Cnnn dưới đây được thay thế
nhất quán trong bản javap; bảng tên gốc nằm ở
[classes.json](../build/moduplv-special-audit-20261023/classes.json).

- Menu `Tan cong / muc tieu` tạo action 17/subaction 0. Handler `C110.C080(int)`
  đảo cờ `C028.C110:Z`. Khi bật: bật lọc quái thường, tắt lọc danh sách/tên riêng
  và tắt `tự đánh khi đứng yên`. Xem [menu bytecode](../build/moduplv-special-audit-20261023/menu.txt).
- Update manager gọi `C028.C110()`, rồi `C028.C020()` mỗi lần update.
  [Manager](../build/moduplv-special-audit-20261023/C133.txt).
- Nếu danh sách skill trống, lấy các skill đã học trong ID 0..20; duyệt danh
  sách từ cuối về đầu. Kiểm tra đủ MP, timer hồi chiêu <= 0, skill có số mục
  tiêu > 0. Không chọn theo hotbar như luồng dùng chiêu native.
- Số mục tiêu đọc từ skill sau đó bị ghi đè cứng bằng **20**. Chọn các mục tiêu
  hợp lệ gần nhất, không lặp, cùng loại với mục tiêu đầu. Chỉ xét object đã có
  trong danh sách world của client; loại bản thân, HP <= 0 và trạng thái chết 4.
  Mặc định lọc loại 1 (quái), có thể đổi sang lọc tên/danh sách qua menu.
- Ngưỡng khoảng cách là **20.000.000**, thay cho tầm chiêu thông thường.
  Hàm khoảng cách dùng căn bậc hai của tổng bình phương tọa độ. Vì thế bộ chọn
  này thực tế cho phép xét object rất xa trong map đã tải; không có bước chạy
  tới tầm chiêu trong hàm gửi này.
- Khi có mục tiêu, gọi thẳng serializer: loại 0 -> opcode 6, loại khác ->
  opcode 9; payload gồm skill byte, target count byte và danh sách ID short.
  Không đi qua bước khởi tạo cast/animation native trong đường này.
- Sau gửi: đặt timer cooldown từ thông số skill (hoặc override của nhân vật),
  cập nhật mốc thời gian, trừ MP dự đoán và kết thúc lượt update. Tối đa một
  skill được gửi trong mỗi lần chạy hàm này.

Bằng chứng chính: [C028](../build/moduplv-special-audit-20261023/C028.txt),
[serializer C076](../build/moduplv-special-audit-20261023/C076.txt),
[cooldown C161](../build/moduplv-special-audit-20261023/C161.txt).

ModUpLv còn có gate delay riêng: mặc định **600ms**, nhưng hàm `C195.C110(int)`
chỉ chặn skill ID **7, 8, 19, 20** khi thời gian từ lần gửi gần nhất <= delay.
Mốc gần nhất được cập nhật sau mọi skill gửi thành công qua đường đặc biệt.
Skill khác không chịu gate này, vẫn chịu hồi chiêu và MP.
[Bytecode C195](../build/moduplv-special-audit-20261023/C195.txt).

## Hung_By_Son

Delay nhận **1..1000ms**, mặc định **650ms**, lưu RMS. Dùng chung giá trị cho
Auto đánh và gate Auto Farm. Bản thân đổi Delay không bật chế độ đánh.

Controller dùng `elapsed > delay`; request Auto đánh/Farm dùng
`elapsed > max(delay, nativeSkillInterval)`. Giữ đường chọn chiêu, cast,
animation và serializer native. Cooldown, mana và tick vẫn có thể kéo dài nhịp.
Đánh tay/actor từ xa giữ minimum 650ms. Đòn Auto đánh đầu sau di chuyển dùng
minimum 100ms, những đòn tiếp theo trở lại Delay đã đặt.

Tham chiếu [hướng dẫn hiện hành](HUNG_BY_SON_AUTO_ATTACK.md),
[controller](../src/luna/features/AutoAttackController.java),
[patcher](../tools/luna/HungBySonAutoAttackPatcher.java).
Hash JAR khớp candidate trong
[verified.json](../build/hung-battlefield-20261023-final-f/verified.json);
controller và bridge trong JAR hiện tại đã được đối chiếu bytecode.

## Giới hạn kết luận

Bổ sung đối chiếu thứ tự: `C028.C178()` tạo list chiêu đã học ID 0..20 tăng dần;
`C028.C020()` duyệt list từ cuối về đầu. Kiểm MP/cooldown/số mục tiêu trước gate
600ms. Nếu chiêu ưu tiên còn bị gate, nhánh offset 136 nhảy đến 544 và **return
cả lượt**, không thử chiêu tiếp theo. Hung đặc biệt bản đầu giữ xoay vòng hotbar
và coi gate đóng là chiêu không sẵn sàng, nên chiêu khác có thể liên tục cập
nhật đồng hồ, làm chiêu ưu tiên đến muộn. Bản sửa Farm/ưu tiên dùng thứ tự ID
giảm dần và giữ chỗ cho chiêu đang chờ gate, trong bộ chiêu hotbar hiện tại.

Đây là khác biệt về cách client chọn mục tiêu và gửi yêu cầu. Tối đa 20 ID
trong packet, tầm chọn rất xa hoặc gửi bỏ qua animation không chứng minh server
chấp nhận 20 mục tiêu, đánh xa hay tăng sát thương/tốc độ thực tế. Cần thử hoặc
log server tương ứng để kết luận hiệu quả gameplay.

## Tốc độ game và nhịp gửi đòn đặc biệt (2026-10-24)

Đối chiếu decompile `ModUpLv.jar` (E282B0AA…) và `Hung_By_Son.jar` 588B9F93…;
chưa thử server thật.

- **ModUpLv không có tốc độ game.** Canvas `run()` gọi đúng một update mỗi khung
  rồi vẽ, ngủ `28 - cost` ms: chu kỳ ≥ 28ms, không bao giờ chạy hai update liền
  nhau. Chuỗi "Tăng tốc" chỉ là lệnh thú cưỡi. Hàm đặc biệt `C028.C020()` chạy một
  lần mỗi update và gửi tối đa một chiêu → khoảng cách giữa hai packet ≥ 28ms.
- **Hung dùng vòng tempo của Bạch Hổ:** cộng `2 × elapsed × tốc độ` vào bộ tích lũy,
  mỗi 40 được một update, tối đa ba update mỗi lượt vẽ. x1 = 50 update/giây (20ms),
  x0.8 = 40 (25ms), x2 = 100, x3 = 150. Hung gốc trước tempo chạy 40ms/khung
  (~25 update/giây), nên "x1" hiện tại đã gấp đôi nhịp gốc của client.
  Khi một lượt vẽ chậm, phần tích lũy chạy bù 2–3 update liền nhau, cách nhau
  gần 0ms; ở x2/x3 việc này xảy ra gần như mọi lượt.
- **Không bị tốc độ tác động:** hồi chiêu (`b -= now - mốc` theo
  `System.currentTimeMillis()`, override `D` của nhân vật giống ModUpLv), gate
  600ms cho 7/8/19/20, mana. Hai bản cùng công thức.
- **Bị tốc độ tác động:** số lần chạy bộ chọn chiêu đặc biệt. Hung (Auto đánh lẫn
  Farm tại bãi) chọn và gửi mỗi update, nên khi nhiều chiêu cùng sẵn sàng, chúng
  được gửi dồn: 20ms/chiêu ở x1, 25ms ở x0.8, 10ms hoặc 0ms ở x2/x3 và lúc chạy bù.
  ModUpLv không bao giờ gửi hai đòn cách nhau dưới 28ms. Hiện tượng "x≥1 hụt
  nhiều, x0.8 ít hơn nhưng vẫn hụt, ModUpLv không hụt" khớp với giả thuyết server
  trả sát thương 0 cho đòn đến quá sát đòn trước. Client hiện chữ "né đòn" khi
  server trả sát thương 0 cho mục tiêu của chính mình (handler damage opcode của
  người tấn công local).
- Khác biệt không phụ thuộc tốc độ: ModUpLv gom tới 20 mục tiêu gần nhất không giới
  hạn tầm; Hung dùng bộ dựng mục tiêu native (mục tiêu chính + số mục tiêu/AoE của
  chiêu) và kiểm tầm client cho chiêu loại `e == 0`.

Đã làm ngày 2026-10-24 với 28ms (xem RELEASES.md). Đề xuất ban đầu: đặt khoảng cách thực tối thiểu giữa hai lần gửi đặc biệt bất
kỳ, độc lập tốc độ game và số update chạy bù, mặc định bằng chu kỳ tối thiểu của
ModUpLv (28ms) hoặc nhịp gốc Hung (40ms). Ngưỡng server thật chưa đo được; cần thử
thực tế để chốt giá trị.

## Tốc độ di chuyển nhân vật (2026-10-24)

Đối chiếu decompile `ModUpLv.jar` (E282B0AA…) và `Hung_By_Son.jar` 4842A7CA…;
chưa thử server thật.

- **Cùng công thức bước.** Cả hai client đặt vận tốc nhân vật = `±(6 + thưởng thú
  cưỡi)`: base 6 trong constructor nhân vật, thưởng lấy từ bảng byte theo loại thú
  cưỡi do server gửi (`-1` = không cưỡi → 0). Khi còn thiếu ít hơn một bước thì
  vận tốc là đúng phần còn lại. Nút "Tăng tốc" của ModUpLv thuộc màn hình chờ tải,
  không phải thú cưỡi.
- **Khác ở số lần cộng vận tốc mỗi giây.** Hàm cập nhật entity gốc cộng `aZ += bc`,
  `ba += bd`:
  - ModUpLv: mọi update; mỗi khung một update, khung ≥28ms → ~35,7 bước/s
    (~214 px/s đi bộ).
  - Hung: chỉ khi `(109 >> (hsoMoveTick & 7)) & 1` (5/8 tick) trên vòng tempo
    50 update/s × tốc độ → x0.8 25, x0.9 28,1, x1 31,3, x2 62,5, x3 93,8 bước/s.
- **Thử nghiệm đã rollback:** bản x1 `BF37EE0A…` cho nhân vật local bước theo thời gian
  thực 28ms/bước (quái/người khác giữ mặt nạ tempo). Người dùng thử và báo không
  đạt nên đã khôi phục `4842A7CA…`; chi tiết trong RELEASES.md.
