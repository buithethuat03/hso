# Hung By Son — menu thao tác item thu nhỏ

**Đã rollback ngày 19/10/2026 theo yêu cầu người dùng.** Nội dung dưới đây chỉ
lưu thiết kế đã bị loại. Hai JAR hiện hành đã trở về bản trước thay đổi; source,
tooling, fixture và catalogs được lưu ở
`build/hung-compact-menu-rollback-20261019-a/source-rejected/`.

Menu thao tác item trong hành trang giữ nguyên các chức năng và tên nút, kể cả
`Hành Trang`, `Cất vào`, `Khác`, `Thêm vào DS`, `Thêm vào S1/S2/S3` khi chúng
có trong menu của món đang chọn.

- Mỗi nút có chiều ngang và chiều cao bằng một nửa kích thước native. Cả nền,
  viền và chữ tiếng Việt được thu từ hình native, không thay font toàn game.
- Cụm nút giữ bố cục hai cột và dòng cuối ở giữa, thu về góc dưới bên phải
  của vùng nút cũ. Phần chiều cao dành cho nút giảm một nửa để chừa chỗ mô tả.
- Vùng nhận bấm theo hình chữ nhật mới, bỏ phần mở rộng 5px của nút native
  để các nút nhỏ cạnh nhau không nhận nhầm thao tác.
- Chỉ command của menu item được đánh dấu thu nhỏ. Menu MOD, đăng nhập,
  HUD, túi đồ và các command ngoài popup này tiếp tục dùng cách vẽ native.
  Native layout xóa dấu thu nhỏ khi tái sử dụng command ở menu thường;
  popup item đánh dấu lại sau khi tạo hoặc thay danh sách nút sau thao tác.
- Hai trạng thái nút được cache trên từng command, không tạo lại ảnh nút
  trong mỗi lần vẽ. Khi game tái sử dụng command cho món khác, layout native
  được thu lại một lần; không thu chồng và không giữ tọa độ của món trước.

Source bitmap: `src/luna/ui/CompactItemMenuSupport.java`. Native boundary được
sinh từ `tools/luna/HungCompactItemMenu.java.in`; patcher và build nằm trong
`tools/luna/HungCompactItemMenuPatcher.java`, `build-hung-compact-menu.ps1`.
Fixture: `fixtures/hung-by-son/HungBySonCompactItemMenuTest.java`.

Pipeline nhận đúng preimage BCF304 (single) và 4997CF (X3). Sau phát hành, dùng
backup tương ứng làm `-SingleInputJar`/`-X3InputJar` để tái tạo. Build luôn ra
stage mới; không tự thay release. Các test hồi quy native/semantic sử dụng
fixture đã biên dịch trong hai stage map48/inventory ngày 14/10, với proof
single đã được kiểm tra; callback/font/graphics mới được chạy trên candidate.

X3 được áp dụng cùng hook ở cả default, `b/`, `c/` từ đúng preimage đang dùng.
Wrapper, Static lifecycle, RMS và logic game còn lại giữ byte cũ. Hai helper
mới không có trạng thái static cần reset; cache nằm trên instance command
trong từng client. Manifest X3 cập nhật hash nguồn single mới.

Hash, backup và bằng chứng kiểm tra của bản phát hành nằm tại
[RELEASES.md](RELEASES.md). Ảnh fixture dùng item/mô tả mô phỏng cùng nút và
font native; chúng không thay thế kiểm tra chơi trên server thật.
