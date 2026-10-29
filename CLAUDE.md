# HSO — hướng dẫn dự án

## Phạm vi
- Chỉ tạo x3/x5/… từ JAR x1: đi thẳng [BUILD_XN.md](client/workspace/docs/BUILD_XN.md), chạy `client/workspace/tools/luna/build-xn.ps1 -InputJar <x1.jar> -Count <N>`. Đây là ngoại lệ đóng gói nhanh: không mặc định decompile, spawn agent, chạy bộ test dài hay emulator; không sửa logic game. Chỉ kiểm tra archive/input tích hợp trong script, trả output và phần chưa thử thật.
- Client J2ME và server Java là hai pipeline độc lập, cùng game nhưng khác version/protocol.
- Sửa client: đọc [client/workspace/CLAUDE.md](client/workspace/CLAUDE.md); chạy lệnh từ `client/workspace/`.
- Sửa server: đọc [server/CLAUDE.md](server/CLAUDE.md); chạy lệnh từ `server/`.
- Chỉ đọc tài liệu của phần đang sửa; không lấy protocol, phiên bản hay build của bên kia làm căn cứ.
- Giữ nguyên thay đổi đang có của người dùng; không reset/clean/ghi đè để làm sạch workspace.

## Bố trí và phát hành
- `client/jar/`: tối đa một JAR phát hành cho mỗi endpoint; nhiều endpoint được cùng tồn tại.
- Không đặt source, tài liệu, manifest, dependency hay build tạm trong release root.
- Trước khi thay JAR của một endpoint, backup vào `client/jar/backup/`; pipeline Luna lưu vào `client/workspace/inputs/releases/`.
- Build ra tên mới, kiểm tra rồi mới thay release; giữ nguyên byte baseline `client/workspace/inputs/luna_final-golden-baseline.jar`.
- Source/công cụ/fixtures/symbols nằm trong `client/workspace/{src,tools,fixtures,symbols}`; generated output nằm trong `build/`.
- Dependency ASM/MicroEmulator đặt ngoài repo, truyền qua `-DependencyDir` hoặc `LUNA_DEPS`.
- Không phục hồi hàng loạt JAR lịch sử/decompile; không tạo probe tại root hoặc phụ thuộc `Downloads/`.
- Giữ `client/workspace/fixtures/luna/runtime-trace.txt` và `server/data/msg.txt`; dump `.txt` ở root không phải source.

## Tra cứu theo tác vụ
- Release, hash, manifest, phần chưa kiểm chứng: [RELEASES.md](client/workspace/docs/RELEASES.md) — một nơi cập nhật duy nhất.
- Kiến trúc/build/hành vi client: các liên kết trong hướng dẫn client; sửa packet phải đọc `PROTOCOL.md` của client.
- Kiến trúc, DB và vận hành server: [SERVER_REPORT.html](server/SERVER_REPORT.html), đọc mục liên quan trước khi sửa.
- Lịch sử trước khi rút gọn: [chỉ mục lưu trữ](docs/instruction-history/2026-09-24/README.md); chỉ tìm đoạn cần thiết khi điều tra bản cũ.

## Duy trì tài liệu
- `CLAUDE.md` chỉ giữ quy tắc bắt buộc, lệnh chính và chỉ dẫn đọc theo tác vụ; giữ ở mức vài chục dòng.
- Chi tiết tính năng, cookbook, hash và changelog đặt trong tài liệu chuyên biệt, không chép lặp vào file cha/con.
- Sau phát hành, cập nhật release record và tài liệu hành vi bị ảnh hưởng bằng bằng chứng thực tế.
- Phân biệt build/bytecode/emulator đã đạt với thử nghiệm server thật; không suy diễn việc chơi được từ build thành công.
