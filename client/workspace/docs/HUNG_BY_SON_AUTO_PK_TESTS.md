# Auto PK — nghiệm thu bản đơn 2026-10-25

## Bán kính hiện hành 400px, 2026-10-26

Theo yêu cầu tiếp theo, đã giảm cả vòng vẽ và bộ lọc PK thực tế từ 500 xuống 400px.
[Hồ sơ phát hành](../build/hung-auto-pk-radius400-20261026-a/verified.json),
[phạm vi patch](../build/hung-auto-pk-radius400-20261026-a/auto-pk-report.txt).
Policy và painter được sinh lại đồng thời để không sót hằng số đã inline; các method còn lại,
menu/hook world/nhịp chiêu và tài nguyên giữ nguyên. Bản đơn đã thay, backup và hash ở RELEASES.md.

- [18.408 kiểm policy](../build/hung-auto-pk-radius400-20261026-a/AutoPkTargetPolicyHostTest.txt):
  biên 399/400/401, đường chéo 240/320, loại 283/283 và tọa độ lớn/âm.
- [PK binary 54 ca](../build/hung-auto-pk-radius400-20261026-a/auto-pk-binary.txt): gói opcode 6
  thực sự chứa người ở 400px và loại người ở 401px; level 40 ngoài vùng không vượt bộ lọc.
- [Ảnh vòng 44 + launcher 6](../build/hung-auto-pk-radius400-20261026-a/auto-pk-range-ui.txt):
  [scale 1](../build/hung-auto-pk-radius400-20261026-a/pk-range-screenshots/pk-range-radius-400-scale-1.png),
  [scale 2](../build/hung-auto-pk-radius400-20261026-a/pk-range-screenshots/pk-range-radius-400-scale-2.png)
  có đúng đường kính 800 world pixel; vẫn xem trước khi chọn PK với cả hai công tắc OFF.
- [Camera native 15](../build/hung-auto-pk-radius400-20261026-a/auto-pk-world-range.txt),
  menu 75, A/B nhịp quái 787, toàn bộ hồi quy và 282 class linkage/startup 12s đều đạt.

Các mục phía dưới là bằng chứng lịch sử khi bán kính còn 500px.

## Nhãn checked và vòng xem trước, 2026-10-26

Đã phát hành bản sửa nhãn đang chọn còn sót `Auto quái + PK [x]`; nhãn đúng là `Auto PK [x]`.
Theo lựa chọn người dùng, vòng nay là xem trước khi chọn mode PK, kể cả master và đặc biệt đều OFF.
Chỉ `showMenu` / `paintPkRange` thay đổi; bộ lọc/combat và hook tâm đã sửa giữ nguyên byte.

[Hồ sơ phát hành](../build/hung-auto-pk-preview-20261026-b/verified.json),
[phạm vi patch](../build/hung-auto-pk-preview-20261026-b/auto-pk-report.txt),
[menu native 75 ca](../build/hung-auto-pk-preview-20261026-b/auto-pk-menu-states.txt),
[đối chứng nhãn sai trên D2 cũ](../build/hung-auto-pk-preview-20261026-b/menu-old-negative.txt).
Menu kiểm cả master ON/OFF × mode quái/PK và callback thật; một con quái gần hơn người hợp lệ
vẫn bị mode PK loại. Lỗi trước đây do test chỉ xem nhãn chưa chọn nên bỏ sót nhánh literal `[x]`.

[UI worker 43 + launcher 6](../build/hung-auto-pk-preview-20261026-b/auto-pk-range-ui.txt)
kiểm có vòng ngay khi PK được chọn với cả hai công tắc OFF;
[ảnh xem trước](../build/hung-auto-pk-preview-20261026-b/pk-range-screenshots/pk-range-selected-master-off-special-off.png).
Chọn quái vẫn không có vòng. Bán kính thật giữ 500px; trên màn hình nhỏ, đường viền có thể nằm
hoàn toàn ngoài viewport dù painter đang chạy, không thu nhỏ vòng để tạo cảm giác nhìn thấy được.

Toàn bộ hồi quy, world camera 15, PK 54, A/B nhịp 787, linkage và startup 12s đã đạt lại ở stage
preview. 16 endpoint khác/golden giữ nguyên; chưa thử lại gameplay online.

## Sửa tâm vòng, hoàn tất 2026-10-26

Bản hiện hành đã sửa hook bị đặt nhầm sau reset camera, ở đoạn dịch minimap. Callback mới
chạy một lần ở cuối lớp world trước reset, nên nhận cùng camera, scale và bu/bv với actor.
Chỉ class renderer world thay đổi; toàn bộ logic combat/target và dữ liệu khác giữ nguyên byte.

[Hồ sơ nghiệm thu và phát hành bản sửa](../build/hung-auto-pk-center-20261025-c/verified.json),
[kiểm giới hạn patch](../build/hung-auto-pk-center-20261025-c/auto-pk-report.txt),
[đối chứng lỗi và camera native](../build/hung-auto-pk-center-20261025-c/auto-pk-world-range.txt).
15 kiểm tra mới xác nhận JAR `3AE3D812…` cũ có hai hook sau reset và thất bại điều kiện mới;
candidate có đúng một hook trước reset, trước nhánh minimap. Pixel worker gọi wrapper dịch camera,
focus và reset native để so biên vòng với tâm actor được chiếu sang màn hình;
[ảnh bản sửa](../build/hung-auto-pk-center-20261025-c/pk-world-range-screenshots/pk-world-range-centered.png).
Worker này kiểm các phép biến đổi native và vị trí callsite, không vẽ sprite nhân vật hoặc chạy
trọn phiên world đã đăng nhập. Fixture UI trực tiếp trước đây chỉ kiểm raster/clip đã không đủ
để phát hiện lỗi đặt hook; không dùng kết quả đó làm bằng chứng vị trí hook nữa.

Toàn bộ suite trong báo cáo bên dưới đã chạy lại ở stage sửa, gồm A/B nhịp 787 kiểm,
PK 54, policy 19.402, linkage 282 class và startup 12s stderr trống. Đã backup và thay endpoint;
16 JAR khác/golden giữ nguyên. Chưa thử lại gameplay server thật.

## Nghiệm thu nâng cấp Auto PK ban đầu

Đã triển khai và thay `client/jar/Hung_By_Son.jar`. Hash, backup và mốc phát hành được ghi tại
[RELEASES.md](RELEASES.md). [Hướng dẫn hành vi](HUNG_BY_SON_AUTO_PK.md).

Stage nghiệm thu: [`hung-auto-pk-20261025-g`](../build/hung-auto-pk-20261025-g/).
[Hồ sơ build/phát hành](../build/hung-auto-pk-20261025-g/verified.json).
Publisher backup nguồn, thay đúng bản đơn bằng atomic replacement, đọc lại hash và kiểm 16 endpoint
khác cùng golden giữ nguyên. X3 chưa nâng cấp Auto PK.

## Chọn đúng mục tiêu và mode

- [Policy: 19.402 kiểm tra](../build/hung-auto-pk-20261025-g/AutoPkTargetPolicyHostTest.txt):
  top 20 so với full-sort tham chiếu trên 1.000 thế giới; ưu tiên level 40, ID trùng, reset,
  biên 499/500/501, đường chéo, tọa độ âm và tránh tràn số.
- [PK binary: 54 kiểm tra](../build/hung-auto-pk-20261025-g/auto-pk-binary.txt):
  0/1/20/>20 người; loại quái, bản thân, HP0, chết/removed, không được PK; FIFO chiến trường thật;
  ưu tiên đúng level 40 và thứ tự khoảng cách; server-position ngoài→trong và trong→ngoài 500px.
- Bắt payload tại native send queue: gói opcode 6 chứa đúng 20 người level 40 gần nhất, bỏ người
  thường dù gần hơn; đổi Quái→PK→Quái cho đúng opcode 9→6→9. MP, cooldown và thống kê đúng một lần;
  buff opcode 40. Đặc biệt vẫn gửi khi master OFF theo lựa chọn của người dùng.
- Test hai thread: lệnh đổi mode phải đợi đòn đang enqueue giữ controller lock, không gửi vector
  cũ bằng opcode mới. Đường thường special OFF cũng chỉ PK, giữ cast hợp lệ đang bận, xếp lại sau cast.

`x()` của player trong JAR ghim trả false; fixture không tạo một “cờ x” giả để tuyên bố kiểm native.
Bộ lọc native chứa lời gọi `x()` được giữ nguyên qua patch.

## Giữ nhịp đánh quái

[A/B cadence: 787 kiểm tra](../build/hung-auto-pk-20261025-g/auto-pk-cadence.txt) chạy JAR nguồn
và candidate trong hai classloader độc lập với cùng thế giới, bộ chiêu 0/1/2/7/8, cooldown mô phỏng
và MP đủ cho cả phiên. Mỗi chế độ chạy đủ 60 giây, thời gian gọi frame thay đổi 10/16/28/40/80ms.

| Cấu hình | Số gói mỗi bản | Kết quả |
|---|---:|---|
| Canh nhịp Tắt, retry OFF | 229 | Timestamp, skill, target, mọi trạng thái MP/cooldown khớp chính xác |
| Khóa 575ms, retry ON | 149 | Timestamp, skill, target, mọi trạng thái MP/cooldown khớp chính xác |

Nhánh `nearestMonsters`, các phương thức canh nhịp ngoài phạm vi cho phép, class stats và serializer
được đối chiếu giữ nguyên. PK dùng mảng top 20 tái sử dụng, chỉ dựng một danh sách người mỗi frame.

Đây là kiểm chứng không đổi nhịp enqueue/cooldown trong fixture, không phải benchmark FPS trên mọi
thiết bị hoặc phép đo số đòn trúng trên server. Không suy ra cam kết 137 trúng/phút khi PK.

## Vòng 500px và hồi quy

[Range UI: worker 39 + launcher 6 kiểm tra](../build/hung-auto-pk-20261025-g/auto-pk-range-ui-final.txt)
dùng actual candidate MIDlet/MicroEmulator và MIDP Graphics:

- [Vòng đầy đủ scale 1](../build/hung-auto-pk-20261025-g/pk-range-screenshots/pk-range-radius-500-scale-1.png),
  [scale 2](../build/hung-auto-pk-20261025-g/pk-range-screenshots/pk-range-radius-500-scale-2.png):
  bán kính world 500 và tâm đúng, không tô kín.
- [Camera/clip 240×320](../build/hung-auto-pk-20261025-g/pk-range-screenshots/pk-range-camera-clip-240x320.png):
  giữ phần đường tròn cắt viewport; giữ color, translation và clip của caller.
- Trong nghiệm thu ban đầu, PK standalone master OFF có vòng, cả hai OFF hoặc Auto chỉ quái không có vòng.
  Điều kiện cả hai OFF đã đổi theo yêu cầu mới ở mục xem trước phía trên.

Lượt rà log phát hiện fixture ban đầu dựng partial world khi MIDlet còn khởi tạo, khiến renderer
native ghi exception dù các pixel assert đạt. Đã sửa riêng thứ tự khởi tạo fixture: chờ startup
hoàn tất và chuyển sang canvas rỗng trước khi dựng world giả. Chạy lại cùng candidate đã phát hành
đạt 39 + 6 kiểm tra, log cuối không có exception; JAR không thay đổi. Pipeline hiện cũng từ chối
range UI log chứa exception để không bỏ sót lỗi kiểu này ở lần chạy sau.

Hook native world render ban đầu được kiểm inverse nhưng đã xác định nhầm hai callsite minimap
là reset camera; xem bản sửa phía trên. Method/class ngoài allow-list và manifest/resources giữ nguyên;
[báo cáo patch](../build/hung-auto-pk-20261025-g/auto-pk-report.txt).

Đã đạt lại các suite Auto đánh native, Special/Farm, CopySpecial, ModUpLv/phó bản, chiến trường,
PK corpse, mê cung, bùa/bán đồ, menu đồ, EXP, cường hóa/NPC, đi buôn và semantic/controller/route.
Liên kết `-Xverify:all` đủ 282 class;
[startup độc lập 12 giây](../build/hung-auto-pk-20261025-g/startup/emulator-run.json) đạt, stderr trống.

## Phần còn cần kiểm trên server thật

Chưa đăng nhập server để thử nâng cấp này. Cần đo riêng PK về số người server nhận trong một đòn,
tầm gây sát thương, khóa sau chiêu/Trọng kiếm, retry, trúng/phút, và gameplay đông người/chiến trường.
Đây là giới hạn kiểm chứng còn lại; không phải công việc client đã bị bỏ dở.
