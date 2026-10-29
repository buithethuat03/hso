# Đóng gói xN nhanh từ JAR x1

Đây là đường mặc định khi người dùng chỉ yêu cầu “tạo bản x3/x5/…”: chạy script, lấy file output, trả đường dẫn. Không điều tra lại login, decompile, spawn agent, chạy test semantic/controller/route/symbol hoặc mở emulator. Chỉ làm thêm khi script báo lỗi cụ thể hoặc người dùng yêu cầu sửa hành vi/kiểm thử. Quy tắc này theo yêu cầu trực tiếp của người dùng ngày 2026-10-28 để tiết kiệm thời gian và quota.

Chạy từ `client/workspace/`:

```powershell
.\tools\luna\build-xn.ps1 -InputJar '..\jar\chibi_final.jar' -Count 3
.\tools\luna\build-xn.ps1 -InputJar '..\jar\chibi_final.jar' -Count 5
.\tools\luna\build-xn.ps1 -InputJar '..\jar\luna_final (1).jar' -Count 5
```

Output mặc định nằm cạnh input: `<tên-x1>_xN.jar`; hậu tố `_x1`/`-x1` được bỏ trước khi thêm `_xN`. Có thể truyền `-OutputJar '...jar'` và `-Name 'HSO CHIBI X5'`. Script tự publish sau kiểm tra nhanh, backup bản output cũ nếu có; khi xuất vào `client/jar/` thì thêm bản ghi vào `docs/RELEASES.md`. Không cần bước `-Publish` riêng. Giữ nguyên x1 và golden baseline.

Máy cần JDK 17+ với `java`/`javac` trên PATH. Chỉ cần hai dependency ở ngoài repo: `asm-9.7.jar`, `asm-tree-9.7.jar`; không cần MicroEmulator. Lần đầu có thể truyền `-DependencyDir 'C:\path\to\luna-deps'` hoặc đặt `LUNA_DEPS`; script cũng tìm các cache `hso-*-deps-*` trực tiếp trong TEMP. Đường dẫn dependency lưu trong `build/xn-cache/dependencies.json`; công cụ biên dịch được cache theo hash source/dependency và đường dẫn compiler. Lần sau chạy cùng một lệnh, không cần đọc lại mã hoặc biên dịch lại nếu cache còn nguyên. Không tải mạng.

Đã chạy đóng gói Chibi Final x5 (khoảng 6 giây gồm compile) và x3 dùng cache (khoảng 3 giây) ngày 2026-10-28; đã xác nhận input xN bị từ chối và không publish. Artifact kiểm tra ở `build/xn-script-check/`, không thay bản x3 đã phát hành. Đây là bằng chứng kiểm tra script một lần; các hội thoại sau không cần lặp lại.

## Phạm vi hỗ trợ

- x2 đến x26, theo launcher và RMS prefix a..z hiện có. Mỗi slot nhận toàn bộ class game riêng, namespace `a/`, `b/`, …; không chia sẻ static state/EXP backend giữa các slot.
- Nhận các JAR J2ME x1 tương thích của họ client HSO, không khóa hash/tên/count class của input. MIDlet đầu vào trực tiếp kế thừa native MIDlet, có một entry; chưa ghép xN, chưa ký JAR. Không phải bộ chuyển mọi Java JAR bất kỳ thành multi-client.
- Giữ nguyên resource và logic game x1; thêm launcher, lifecycle và ranh giới RMS. Launcher lấy từ `client/jar/backup/autotrade_v27_revive05_horse_x3.jar`, cố định SHA-256 trong packer; không dùng class game cũ của template.
- RMS open/list/delete trực tiếp được đổi theo slot; reflective RMS `HSOSKYPetFeedSupport.loadRMS/saveRMS` với `hsosky_cfg` dùng cơ chế đã có. Reflection RMS ngoài mẫu này, bytecode/metadata không hỗ trợ và input đã ghép sẽ báo lỗi, không publish. Reflection động tự xây tên class/API hoặc vendor API khác cần xử lý riêng nếu gặp; không khẳng định tương thích tuyệt đối.
- Kiểm tra nhẹ trong packer: manifest/input, checksum template, tên class/namespace, va chạm resource, round-trip toàn bộ archive, input không đổi. Script kiểm tra hash output, backup khi thay file và lưu report. Không tự đăng nhập tài khoản, không tự mở emulator, không chạy các bộ test cũ.
- Đóng gói thành công không khẳng định server cho phép đăng nhập đồng thời N tài khoản hoặc sửa được lỗi vốn có trong x1. Không ghi “đã vào game” nếu chưa thử thật.

Nếu lỗi: đọc dòng lỗi và `build/xn-*/pack-report.txt` nếu có; sửa đúng chỗ hoặc báo giới hạn. Không tự mở rộng thành kiểm toán toàn bộ dự án. Các script `build-chibi-final-x3.ps1`/pipeline legacy vẫn phục vụ điều tra hoặc kiểm thử chuyên sâu khi được yêu cầu; không dùng cho yêu cầu đóng gói thông thường.
