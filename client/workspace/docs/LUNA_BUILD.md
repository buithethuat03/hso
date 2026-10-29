# Luna — build và xác minh

Đường dẫn/lệnh tính từ `client/workspace/`. Script chuẩn là `tools/luna/build-source.ps1`; đọc script trước khi chạy nếu bố trí release thay đổi.

## Giới hạn hiện tại (đối chiếu source 2026-09-24)

Script từ chối mọi thư mục trong `client/jar/` (kể cả `backup/`), chuyển **tất cả** `*.jar` sang `inputs/releases/`, rồi yêu cầu chỉ còn một JAR mới. Điều này chưa đáp ứng quy tắc một JAR **mỗi endpoint** khi Luna và HSO CHILD cùng tồn tại.
Trước khi dùng script để phát hành, sửa/kiểm tra bước publish để chỉ thay đúng endpoint và chấp nhận thư mục backup; không dọn JAR endpoint khác để vượt kiểm tra.
Đây là hạn chế đã quan sát, chưa được sửa trong lần tối ưu tài liệu này.

## Pipeline chuẩn

```powershell
.\tools\luna\build-source.ps1 -DependencyDir C:\path\to\luna-deps
```

- Có thể dùng `LUNA_DEPS`; thư mục dependency ngoài repo chứa ASM 9.7, ASM Tree 9.7 và MicroEmulator.
- Input: `inputs/luna_final-golden-baseline.jar`; output mới có timestamp trong `build/releases/` trước khi publish. `-EnterOnly` hiện là no-op chẩn đoán tương thích cũ.
- Script biên dịch semantic source/fixtures, chạy `LunaSemanticHostTest`, `LunaControllerHostTest`, `LunaRouteHostTest`, kiểm catalog, sinh bridge/façade, patch bytecode và XP, kiểm JAR rồi kiểm legacy route.
- Kiểm `owner + member + descriptor`, số hook, class version, entry/resource diff, `-Xverify:all` và class loading trước publish. Verifier lỗi phải dừng.
- Manifest kỳ vọng của pipeline đã ghi nhận: một hook façade, 23 semantic classes, `legacy.bridge=true`, maximum class major 51, 730 resources (728 baseline + 2 XP resources), class-load pass. Nếu thay đổi có chủ đích, cập nhật verifier và bằng chứng tương ứng.
- XP resource allow-list: `x1/mfont/tahoma_14b_green.png`, `mfont/tahoma_14b`.
- Host controller tests bao phủ first-open khi world settling, stable-screen re-arm, logout/login và bật lại sau disable; không thay thế kiểm runtime trên server thật.
- Dùng lệnh các stage trong script để chẩn đoán. Cookbook cũ trong bản lưu có classpath/input lỗi thời, không chạy nguyên khối.

## Runtime và phát hành

### Pipeline thử nghiệm đã rollback: ba nâng cấp Bạch Hổ (2026-10-03)

Người dùng loại bản này ngày 2026-10-04. Lệnh dưới đây chỉ giữ để điều tra;
không dùng làm pipeline phát hành hiện hành. JAR đã khôi phục được ghi trong
[RELEASES.md](RELEASES.md); pipeline Luna chuẩn dùng lại profile Noclip ON.

```powershell
.\tools\luna\build-bachho-upgrades.ps1 `
  -InputJar inputs\releases\bachho_final-before-three-upgrades-20261003.jar `
  -DependencyDir C:\path\to\luna-deps
```

Script kiểm đúng hash input của bản trước nâng cấp, tạo thư mục build mới,
patch Noclip/lifecycle rồi sinh bridge và ghép hai controller. Nó chạy host,
RMS/binary, Noclip, symbol, resource và initialized legacy route checks; chỉ
staging JAR, không thay release hoặc dọn endpoint khác. Đối với bản OFF,
`TestAutoTradeRoute` dùng `--noclip-off`; profile lịch sử mặc định của fixture
vẫn kiểm ON. Trước khi publish, chạy emulator với home/RMS riêng và kiểm cả
stdout/stderr. Hồ sơ kiểm v3 và kiểm rebuild ở `build/bachho-features-20261003/`.

- Khi sửa XP/CLDC, kiểm trực tiếp popup type 1 với MicroEmulator để bắt lỗi quyền truy cập field và render/network loop; startup smoke đơn thuần chưa đủ.
- `javac --release 7` không đảm bảo API tương thích CLDC: phép nối chuỗi có thể sinh `StringBuilder`. Pipeline EXP Hung By Son kiểm constant pool của các class mới, dùng `StringBuffer`/MIDP trực tiếp, và kiểm cả lỗi thiếu class lẫn File RMS qua hai tiến trình. Phải chạy bước dọn RMS login native; chỉ nạp lại adapter trong bộ nhớ không phát hiện cache bị native xóa.
- Có thể mở emulator thủ công bằng `java -cp "<dependency-dir>/microemulator.jar;<output.jar>" org.microemu.app.Main <output.jar>`.
- Backup release cũ đúng endpoint trước khi thay; giữ golden và JAR endpoint khác. Không dùng lại tên output có sẵn.
- Ghi filename, SHA-256, manifest, kiểm tra đã chạy và kiểm tra còn thiếu trong [RELEASES.md](RELEASES.md); cập nhật [LUNA_BEHAVIOR.md](LUNA_BEHAVIOR.md) nếu hành vi đổi.
- Input mất hoặc hash lệch: xác minh nguồn gốc trước khi patch; không tự lấy một JAR lịch sử làm thay thế.
