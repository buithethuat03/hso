# Auto đánh — báo cáo nghiệm thu 2026-10-14

Báo cáo này lưu bằng chứng lần phát hành Auto đánh ban đầu. Bản hiện hành đã
thêm delay chung Farm/Auto, ưu tiên di chuyển và X3; xem
[báo cáo mới](HUNG_BY_SON_SHARED_DELAY_X3_TESTS.md).

JAR bàn giao: [Hung_By_Son.jar](../../jar/Hung_By_Son.jar). Mở **Menu → Auto đánh >**.
Có nút bật/tắt, hai chế độ chỉ quái/quái + PK và delay từ 1 đến 1000ms, mặc định
650ms. Chọn chế độ sẽ bật chức năng; bật Farm tự dừng Auto đánh và chiều ngược
lại. Mode/delay được nhớ riêng; mở lại client bắt đầu OFF.

Backup: [bản Hung trước thay đổi](../../jar/backup/Hung_By_Son-before-hung-auto-attack-20261014-final-c.jar).
Hash phát hành, nguồn và donor được ghi trong [RELEASES.md](RELEASES.md).
[Hướng dẫn và quy tắc hành vi](HUNG_BY_SON_AUTO_ATTACK.md).

## Bằng chứng của đúng bản phát hành

Pipeline đã chạy đầy đủ trên candidate trong
[`hung-auto-attack-20261014-final-c`](../build/hung-auto-attack-20261014-final-c/).
Sau nghiệm thu, publisher backup và thay endpoint bằng atomic replacement,
đọc lại hash và đối chiếu toàn bộ các JAR còn lại cùng golden.

- [Verified stage](../build/hung-auto-attack-20261014-final-c/verified.json).
- [Biên bản thay endpoint](../build/hung-auto-attack-20261014-final-c/publish.json).
- [Kiểm archive và phạm vi thay đổi](../build/hung-auto-attack-20261014-final-c/archive-boundary.txt).
- [Linkage và 503 member references](../build/hung-auto-attack-20261014-final-c/auto-attack-jar-verifier-report.txt).

## Kết quả kiểm thử

| Nhóm | Kết quả | Phạm vi đã chạy |
|---|---:|---|
| Controller semantic | 102 kiểm tra, đạt | Mặc định, mode/toggle/HUD, 1/650/1000, hủy và nhập sai/tràn, chọn/gỡ mục tiêu, chuyển map/khu/nhân vật, pause/resume khi native lỗi tạm thời, lưu setting lỗi và chuyển quyền khác thread. |
| Auto đánh binary | 1.324 kiểm tra, đạt | Menu/callback native và ID sau ép byte; chọn mục tiêu trên danh sách raw; bridge.attack → skill native → cast thật; thiếu MP/cooldown; cast lần tiếp theo; mục tiêu phụ sống/chết/dz/ghost/người chơi; request và serializer opcode9/quái, opcode6/PK. |
| Nhịp mới | 30 tổ hợp, đạt | x1/x2/x3/x0.8/x0.9 × delay1/650/1000 × khoảng native0/1500; kiểm ngưỡng−1/ngưỡng/ngưỡng+1, frame pending, timestamp và packet gửi. |
| Chuyển quyền | Đạt trong suite binary | Farm → Auto và ngược lại; UI bật Farm phải chờ request đang giữ lock tới enqueue; sau chuyển/hủy, callback cũ không gửi tiếp; snapshot cấu hình hai bên giữ nguyên. HP/MP helper vẫn chạy theo cài đặt cũ. |
| Phím/touch | 24 kiểm tra, đạt | MIDlet và Canvas thật; keyPressed/keyReleased và pointerPressed/pointerReleased → poll/frame popup native → callback; root menu, cả hai mode, master và delay ở cả hai kênh; editor mở và Cancel trả về Canvas. |
| RMS/vòng đời | Đạt trong suite binary | Memory RMS mặc định/hỏng và record ID thay đổi; hai tiến trình File RMS ghi/nạp mode+delay, fresh client OFF; gọi cleanup đăng nhập native, giữ byte của Auto/Farm/vị trí Farm/tài khoản giả/EXP và xóa cache probe. |
| HUD/EXP/thông báo | Đạt | Status native font ở bề rộng240/450 và PK240, OFF không vẽ; vị trí hook tuân info toggle và thêm sau dòng native. EXP semantic38.735, native131, File RMS/CLDC và vị trí thông báo native giữ nguyên. |
| Cache/quái đông | Đạt | Cache217, stale host99/native16, selection host46/native popup và vectors, crowded45.044/native27, recovery36. |
| Nhịp và animation cũ | Đạt | Timing650ms cũ2.545, tempo94, remote-animation477; request manual/Farm không nhận delay mới. |
| Tính năng khác | Đạt | Noclip menu24/collision349, FullOffline94, đập đồ13/NPC17, đi buôn, Auto Login242 và năm suite semantic/controller/route/AutoCombat/LoginMaterial. |
| JAR | Đạt | CRC, inverse hook, 258 class linkage với -Xverify:all, CLDC constant-pool; 7 class đổi, 9 thêm; 778 non-class entries, manifest và các class ngoài allow-list giữ nguyên. |
| Startup giả lập | Đạt | 12 giây, memory RMS riêng, tiến trình sống và stderr rỗng; không đăng nhập tài khoản. |

Log chính: [Auto binary](../build/hung-auto-attack-20261014-final-c/auto-attack-binary.txt),
[input native](../build/hung-auto-attack-20261014-final-c/native-input-events.txt),
[timing cũ](../build/hung-auto-attack-20261014-final-c/real650-preserved-timing.txt),
[startup](../build/hung-auto-attack-20261014-final-c/single-emulator-smoke/).
Các log hồi quy khác nằm cùng thư mục stage và được liệt kê trong verified.json.

Ảnh đã kiểm bằng mắt:
[chỉ quái240](../build/hung-auto-attack-20261014-final-c/screenshots/auto-attack-hud-240.png),
[quái + PK240](../build/hung-auto-attack-20261014-final-c/screenshots/auto-attack-hud-pk-240.png),
[chỉ quái450](../build/hung-auto-attack-20261014-final-c/screenshots/auto-attack-hud-450.png),
[EXP/thông báo240](../build/hung-auto-attack-20261014-final-c/screenshots/native-hud-notices-slot-0-240.png),
[EXP/thông báo450](../build/hung-auto-attack-20261014-final-c/screenshots/native-hud-notices-slot-0-450.png).

## Giới hạn của bằng chứng

Nghiệm thu là binary và MicroEmulator với world/clock/transport kiểm thử; packet
được serialize thật rồi thu tại biên transport, không gửi lên server. Bài input
dùng Canvas/popup/callback thật với world chưa sẵn sàng để kiểm input riêng.
Ảnh status kiểm glyph, độ rộng và trạng thái ON/OFF; vị trí trong renderer toàn
game được kiểm qua hook native và các bài HUD/notice.

Fixture stale cũ cố ý tạo actor qua Unsafe mà không có controller/assets
animation. Native receiver ghi HP hợp lệ trước khi in trace `di is null`; trace
này cũng có trong log các candidate từ bản nền trước thay đổi. Suite đó chỉ
nghiệm thu parser/cache/stale-target. Kiểm barrier/release animation được chạy
riêng bằng 477 kiểm tra; startup và input mới không có lỗi runtime như vậy.

Chưa đăng nhập/chơi trên server thật, chưa kiểm renderer của một trận đông
người thật hoặc xác nhận server chấp nhận các request. Không thêm API mạng,
tự đăng nhập hoặc tự hồi sinh cho Auto đánh mới.
