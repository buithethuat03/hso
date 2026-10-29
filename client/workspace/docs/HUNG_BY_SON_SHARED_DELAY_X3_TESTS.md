# Hung By Son — delay chung, ưu tiên di chuyển và X3

Báo cáo này ghi lần phát hành trước. Bản hiện hành bổ sung map48 không cache,
100ms cho đòn đầu sau di chuyển và tiếp tục đánh trong hành trang; xem
[báo cáo mới](HUNG_BY_SON_MAP48_INVENTORY_TESTS.md).

Đã bàn giao [bản đơn](../../jar/Hung_By_Son.jar) và
[bản X3](../../jar/Hung_By_Son_x3.jar). Hash đầy đủ và lịch sử tại
[RELEASES.md](RELEASES.md). [Hướng dẫn menu](HUNG_BY_SON_AUTO_ATTACK.md).

**Delay (ms)** vẫn mặc định650, nhận1–1000. Giá trị này dùng chung cho Auto Farm
và Auto đánh trong cùng client: nhập1000 áp dụng cho cả hai. Di chuyển tay
được ưu tiên hơn lượt Auto đánh; chức năng giữ ON, bỏ request cũ, chờ tới khi
dừng rồi đợi delay trước khi đánh lại. Trong X3, setting/trạng thái riêng từng
slot; bảng EXP theo level vẫn chung như bản X3 trước.

X3 chọn `Hung By Son 1/2/3`, giữ `*` khoảng500ms để về launcher rồi chọn slot
khác. Client đã mở vẫn giữ instance và vòng lặp.

## Bằng chứng phát hành

| Bản | Nghiệm thu | Thay endpoint | Backup |
|---|---|---|---|
| Single | [verified.json](../build/hung-auto-shared-delay-move-20261014-a/verified.json) | [publish.json](../build/hung-auto-shared-delay-move-20261014-a/publish.json) | [Bản36BAC trước sửa](../../jar/backup/Hung_By_Son-before-hung-auto-shared-delay-move-20261014-a.jar) |
| X3 | [verified.json](../build/hung-auto-attack-x3-20261014-c/verified.json) | [publish.json](../build/hung-auto-attack-x3-20261014-c/publish.json) | [X3 cũ trước thay](../../jar/backup/Hung_By_Son_x3-before-hung-auto-attack-x3-20261014-c.jar) |

Publisher kiểm hash ứng viên, nguồn đã nghiệm thu, snapshot các endpoint và
golden; backup đúng endpoint trước atomic replacement và đọc lại hash sau thay.

## Kết quả

| Nhóm | Kết quả và phạm vi |
|---|---|
| Single Auto/Farm/movement | 1.663 kiểm tra: menu native, skill/cast/packet thực, provenance Farm/đánh tay/remote, gate/request1/650/1000, interval0/1500 và năm tốc độ, mode-switch giữ delay RMS, hủy cast/pending khi có lệnh đi, giữ ON và chờ đủ delay sau khi dừng. |
| Single semantic/input/timing | 111 semantic; 24 native Canvas phím/touch; 2.565 timing/animation/serializer, manual/remote giữ650 và các native cooldown/interval. |
| Single hồi quy | Cache217, stale99+16, selection46+native, crowded45.044+27, recovery36, remote477, EXP38.735/native132/File RMS/CLDC, tempo94, Noclip24/349, FullOffline94, enhancement13/NPC17, đi buôn, Auto Login242 và năm suite semantic/controller/route/combat/material. |
| Single JAR/startup | 258 class /586 member refs, native hook inverse, unchanged resources/manifest/classes ngoài allow-list; startup12s với memory RMS riêng và stderr rỗng. |
| X3 code/data | 797 class linkage; 1.839 host kiểm parity đủ ba slot, alias RMS, reflection đúng bridge, startup OFF, shared-delay và root EXP chung. 19 wrapper nguyên byte; chỉ cross-slot tới backend EXP được cho phép. |
| X3 Auto/UI/RMS/lifecycle | 190 kiểm tra: UI worker72, write82, read29 và parent checks. Ba client virtual thật mở TextBox, Save/Cancel, Alert sai0 quay về cùng editor rồi trả đúng Canvas; 3 RMS4byte độc lập, 2 JVM nạp lại mode/delay OFF; Farm/manual floors; phím native hủy cast rồi vào walking; callback cũ bị chặn; clear một runtime giữ slot khác và EXP chung. |
| X3 EXP/crowded | EXP364 với HUD/notice đủ3slot, cleanup namespace, hai tiến trình File RMS dùng chung bảng level và CLDC; crowded74. |
| X3 launcher | Giữ* qua callback thật, chuyển1→2→3→1, cùng instance/Canvas, đủ ba loop sống; stderr rỗng. |

Log chính:
[single binary](../build/hung-auto-shared-delay-move-20261014-a/auto-attack-binary.txt),
[single timing](../build/hung-auto-shared-delay-move-20261014-a/real650-preserved-timing.txt),
[X3 host](../build/hung-auto-attack-x3-20261014-c/x3-host.txt),
[X3 Auto/UI](../build/hung-auto-attack-x3-20261014-c/auto-attack-x3-host.txt),
[X3 EXP](../build/hung-auto-attack-x3-20261014-c/exp-native-x3.txt),
[launcher](../build/hung-auto-attack-x3-20261014-c/launcher.out.log).

Ảnh đã kiểm:
[HUD single240](../build/hung-auto-shared-delay-move-20261014-a/screenshots/auto-attack-hud-240.png),
[PK240](../build/hung-auto-shared-delay-move-20261014-a/screenshots/auto-attack-hud-pk-240.png),
[launcher X3](../build/hung-auto-attack-x3-20261014-c/screenshots/launcher/launcher.png),
[slot1 sau quay lại](../build/hung-auto-attack-x3-20261014-c/screenshots/launcher/client-1-visit-3.png),
[slot2](../build/hung-auto-attack-x3-20261014-c/screenshots/launcher/client-2-visit-1.png),
[slot3](../build/hung-auto-attack-x3-20261014-c/screenshots/launcher/client-3-visit-2.png),
[EXP/notice slot3](../build/hung-auto-attack-x3-20261014-c/screenshots/exp-x3/native-hud-notices-slot-2-240.png).

## Phạm vi

Đã kiểm binary và MicroEmulator; transport combat thu packet thực ở biên mạng,
không gửi server. Bài X3 kiểm các boundary mới/virtual UI và chuyển slot thực;
logic skill/packet đầy đủ dựa trên đúng source single đã nghiệm thu và parity
ba namespace. Hook nhận lệnh đi trên đất/cửa map được kiểm cấu trúc ở đúng
nhánh native và chạy helper hủy cast; phím đi thực đã chạy tới state walking.

Fixture stale cũ có trace actor thiếu `di` do cấp phát Unsafe không có assets
animation; trace đã tồn tại trước sửa và suite đó chỉ nghiệm thu parser/cache.
Barrier/release animation được kiểm riêng bằng477 bài; startup/launcher stderr
rỗng. Chưa chơi hoặc kiểm server chấp nhận request trong trận thực tế.
