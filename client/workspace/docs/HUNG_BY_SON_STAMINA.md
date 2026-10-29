# Hung By Son x1 — Auto phục hồi thể lực

Menu mod → Cài đặt cấu hình → **Auto Phục Hồi Thể Lực: [TẮT]/[BẬT]** (action −21, nằm sau
Auto Lửa Thiêng, trước Reset Cấu Hình). Bấm để bật/tắt, menu cấu hình mở lại. Không lưu RMS:
mỗi lần mở game là TẮT.

## Thể lực trong client

- Số cạnh trái tim ở tab Trang Bị là static `int e` của class người chơi `~lliiil`.
- Chỉ hai chỗ ghi biến này: `<clinit>` (đặt bằng 0) và hàm xử lý gói nhận **opcode 59**
  `~lllllll.~llilii` (`readInt` → `e`, `readInt` → `~lliiil.i`; chưa rõ `i` là gì).
- Client xin opcode 59 bằng gói rỗng `~lilillll.~llll((byte)59)` ở ba chỗ: chạm ô HP/MP trên HUD
  (action 1, cũng mở bảng nhân vật), cuối hàm xử lý gói nhận opcode 3, và `~lllilli.a()` ngay
  sau khi gửi opcode 12. Người dùng xác nhận chạm HUD làm server gửi opcode 59.
- Native: `e <= 0` thì client báo "Không đủ sức khỏe" khi định đánh, và auto gốc tự tắt.

## Hành vi

Chỉ chạy khi world sẵn sàng (`farmWorldReady()`: bỏ qua kiểm hộp thoại/màn hình).

1. Mỗi 1000ms gửi một gói opcode 59 rỗng. Không mở màn hình hay hộp thoại nào.
2. Khi nhận một phản hồi 59 **sau lúc bật** và thể lực < 500, nhân vật còn sống (`cG != 4`):
   dùng món đầu tiên trong túi có tên bỏ dấu dạng `com … ga` ("Cơm gà", "Cơm đùi gà") bằng
   op 32 (như dùng bình máu gốc), rồi gửi ngay một gói 59. Giá trị cũ còn lại từ trước khi bật
   không được dùng để ăn.
3. Phản hồi nào sau bữa ăn có thể lực ≥ 500: coi như đã hồi, chữ trên đầu báo "Đã ăn …, thể
   lực: N", tiếp tục theo dõi. Người dùng đề xuất ngưỡng > 29000; bản này dùng ≥ 500 để không kẹt
   khi giá trị sau ăn nằm giữa hai ngưỡng.
4. Nếu sau bữa ăn có ít nhất hai phản hồi vẫn < 500 và đã qua 2s: TẮT, mở hộp thoại Ok "Đã ăn
   … nhưng thể lực vẫn dưới 500 (lời server). Đã tắt Auto phục hồi thể lực". Không có phản hồi
   nào trong 10s: TẮT với "Server không trả lời thể lực…".
5. Không có Cơm gà trong túi: TẮT với hộp thoại "Không còn Cơm gà trong hành trang!…".

**Đóng thông báo:** trong 5s sau khi ăn, tối đa hai hộp thoại native bị chặn ngay trước khi mở
(móc đầu `~lillllll.~lilll(String)Z`, trước bộ lọc của ModUpLv), ví dụ "Sức khỏe đã được phục
hồi" hoặc "Một ngày chỉ có thể ăn được 1 vật phẩm sức khỏe". Lời server được giữ để ghi vào
hộp thoại báo dừng. Thông báo có chữ "kết nối" không bao giờ bị chặn. Ngoài 5s đó, hộp thoại
hiện bình thường (kể cả khi người chơi tự ăn).

## Source và build

- Module: `tools/luna/HungBySonStamina.java.in` (marker `@~suffix@`, biên dịch `--release 7`,
  lưu class 48).
- Patcher: `tools/luna/HungStaminaPatcher.java`, input ghim `95D1C5CA…`. Thêm
  `HungBySonStamina`; sửa bốn class, mỗi chỗ có kiểm nghịch đảo: menu cấu hình gọi
  `addConfigRows` ngay sau `HungBySonModUpLv.addConfigRows`, bộ lọc hộp thoại gọi `onDialog`,
  handler opcode 59 gọi `onStamina` trước `return`, loader `register()` trước ModUpLv.
- Build/publish: `build-hung-auto-pk.ps1 -Stamina`, `publish-hung-auto-pk.ps1 -Stamina`.
- Fixture: `fixtures/hung-by-son/HungBySonStaminaBinaryTest.java` chạy class thật, đưa gói 59 dựng
  sẵn vào handler native, bắt gói ở hàng đợi gửi native.

Hash, backup và kết quả kiểm: [RELEASES.md](RELEASES.md).
