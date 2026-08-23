# Project HSO

Hai phần độc lập của cùng dòng game Hiệp Sĩ: mod client J2ME, và mã nguồn server.
**Cùng game nhưng khác version** — đừng dùng tài liệu bên này làm căn cứ cho bên kia.

| Thư mục | Nội dung |
|---|---|
| `client/jar/` | Mod client J2ME: patcher ASM, support class, jar gốc + jar đã mod, source decompile. Xem `client/jar/CLAUDE.md` — **lệnh build phải chạy từ trong thư mục đó**. |
| `server/` | Server Java 17 + Maven + MySQL — game "Hiệp Sĩ Free Online" (một server khác client trên). Đã vá lỗi + thêm công cụ admin + file container (23/08/2026). Xem `server/CLAUDE.md` và `server/SERVER_REPORT.html`. **Lệnh build phải chạy từ trong `server/`**. |

Repo là git (khởi tạo 23/08/2026). `.gitignore` loại `target/`, `log/`, `*.class`, `*.mdmp` và
các thư mục decompile — những thứ đó sinh lại được.

## Tài liệu

| File | Nội dung |
|---|---|
| `server/SERVER_REPORT.html` | Khảo sát mã nguồn server: kiến trúc, 55 opcode, lược đồ DB, danh sách lỗi theo mức ưu tiên kèm `file:line`, runbook vận hành, lộ trình nâng cấp. Đọc trước khi sửa server. |
| `client/jar/PROTOCOL.md` | Protocol mạng của **client** đã mod: frame, handshake, keystream, opcode, kỹ thuật mod đã verify. Của version server khác. |
| `client/jar/HSOSKY_COMBAT_ANALYSIS.md` | Phân tích luồng combat phía client. |
