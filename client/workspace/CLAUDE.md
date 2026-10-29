# Client J2ME — Luna / HSOSKY / KnightOnline

## Bắt đầu
- **Chỉ đóng gói xN:** đọc [BUILD_XN.md](docs/BUILD_XN.md), chạy `.\tools\luna\build-xn.ps1 -InputJar <x1.jar> -Count <N>`, trả output. Không cần đọc lại kiến trúc/feature/release history, decompile hoặc spawn agent. Theo yêu cầu người dùng, không chạy test semantic/controller/route/symbol, emulator hay live login cho tác vụ này; dùng kiểm tra archive/input tích hợp. Quy tắc này ưu tiên hơn các yêu cầu build/xác minh chung bên dưới khi không sửa hành vi. Chỉ điều tra thêm lỗi cụ thể hoặc khi được yêu cầu.
- Áp dụng quy tắc ở [hướng dẫn gốc](../../CLAUDE.md); mọi lệnh dưới đây chạy từ `client/workspace/`.
- Trước khi sửa: đọc mục liên quan trong [kiến trúc](docs/LUNA_ARCHITECTURE.md), module sở hữu và fixture tương ứng.
- Trước khi build/patch: đọc [release record](docs/RELEASES.md); đối chiếu hash thực tế, không tin nhãn “latest” trong lịch sử.
- Baseline bất biến: `inputs/luna_final-golden-baseline.jar`; không dùng làm output.
- Tra `symbols/luna-4.0.2.properties` trước; không decompile lại nếu catalog đã bao phủ symbol.

## Sửa đúng chỗ
- Logic mới ở `src/luna/`; test/trace ở `fixtures/luna/`; công cụ duy trì ở `tools/luna/`.
- Đi buôn → `AutoTradeController`, `PathPlanner`; Ngã 4/combat → `N4Controller`, `MovementArbiter`.
- Hồi sinh/ngựa → `ReviveController`, `PersistentHorseController`; menu/HUD → `src/luna/ui`; shop/notice → `LunaBridgeApi`, `NoticeReader`.
- Trước khi sửa tính năng trên, login hoặc XP: đọc mục tương ứng trong [LUNA_BEHAVIOR.md](docs/LUNA_BEHAVIOR.md).
- Sửa packet: đọc [PROTOCOL.md](PROTOCOL.md); combat: [HSOSKY_COMBAT_ANALYSIS.md](HSOSKY_COMBAT_ANALYSIS.md).
- Sửa `stepHaul`/`stepToward`/`routeTowards`: đọc [AUTOTRADE_HAUL.html](AUTOTRADE_HAUL.html) (local, không publish); bridge đi buôn: [AUTOTRADE_NOTES.md](AUTOTRADE_NOTES.md).
- Chỉ sửa hook/ASM khi cần binary access; tra đúng `owner + member + descriptor`, thiếu symbol thì bổ sung catalog/bridge/verifier, không đoán tên.
- Feature source/tài liệu kiến trúc không dùng tên obfuscate; không mở private field làm feature API, dùng generated bridge/accessor.
- `HSOAutoTradeSupport` chỉ là ABI façade; giữ tên/descriptor hook. Template ở `tools/legacy/luna/{HSOAutoTradeSupport.java.in,legacy-hook.template}`.
- Không sửa tay `build/`, generated maps/reports, compile JAR, manifests hay class; sửa source/template rồi sinh lại.
- Không biên dịch đè `HSOSKYPetFeedSupport`/`HSOSKYAutoEnhanceSupport` từ source legacy cũ: có thể mất hook/RMS hiện có.

## Ràng buộc luôn giữ
- MIDP 2.1 / CLDC 1.1; mọi `javac` dùng `-encoding UTF-8`, support nhúng JAR dùng `--release 7`.
- Không Java 8 API/lambda/stream hay cấp phát lớn mỗi frame; không đổi resource ngoài allow-list.
- Chỉ một bên ghi path qua `MovementArbiter`; không kết luận mất pet khi map/dialog còn settling.
- Auto Horse: world ổn định, không dialog/connection wait; giữ throttle native 2500ms và retry hữu hạn, không dùng item mỗi frame.
- Giữ menu native `Tự động`; ENTER cleanup một lần sau world entry; custom reconnect chỉ khi đã chọn Auto Login, không kích hoạt khi login tay.
- Không khôi phục GhostMod/captcha của Luna; Revive và menu chọn ngựa độc lập. Menu chính chỉ có một dòng `Auto Đi Buôn`.
- Auto attack mặc định mode 7; giữ `Auto chỉ quái`, `Auto quái + PK`, hành vi chat 7/6 và toggle OFF.
- XP chỉ popup packet 30/type 1; xem giới hạn font, lifetime và CLDC trong tài liệu hành vi trước khi sửa.

## Build và xác minh
- Lệnh chuẩn: `.\tools\luna\build-source.ps1 -DependencyDir C:\path\to\luna-deps` (hoặc `LUNA_DEPS`).
- Dependency ngoài repo: `asm-9.7.jar`, `asm-tree-9.7.jar`, `microemulator.jar`; không lấy input từ `Downloads/`/JAR lịch sử đã xoá.
- Đọc [LUNA_BUILD.md](docs/LUNA_BUILD.md) trước khi chạy: script hiện còn giả định release root chỉ có một JAR, xung đột bố trí nhiều endpoint/backup.
- Mỗi build dùng tên mới; không ghi đè release/baseline. Verifier symbol lỗi thì dừng.
- Bắt buộc semantic/controller/route tests, symbol/bridge/hook checks, class version, resource diff/allow-list, `-Xverify:all`, class loading và legacy route regression.
- Sửa hành vi phải có fixture/binary verification phù hợp; kiểm emulator khi liên quan runtime, ghi rõ phần chưa thử trên server thật.
- Publish sau khi đạt kiểm tra; backup đúng endpoint, giữ release endpoint khác. Cập nhật [RELEASES.md](docs/RELEASES.md) và feature/invariant liên quan.

## Tra cứu ít dùng
- Legacy/x3/RMS/repoint: tìm đúng mục trong [bản lưu client](../../docs/instruction-history/2026-09-24/client.md), đường dẫn lịch sử tính từ workspace này.
- x3/x5/… thông thường: dùng [BUILD_XN.md](docs/BUILD_XN.md). Chỉ đọc [HSO_CHILD_X3.md](docs/HSO_CHILD_X3.md) khi sửa pipeline/điều tra bản legacy; các bản cũ dùng default/`b/`/`c/`, script xN mới dùng `a/`/`b/`/`c/`/… . Bản song song phải tách MIDlet name/vendor và prefix mọi RMS store.
- Không nạp toàn bộ bản lưu vào context mặc định; không coi lệnh hoặc nhãn release lịch sử là hiện hành.
