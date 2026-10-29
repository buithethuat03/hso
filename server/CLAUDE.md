# Server — Hiệp Sĩ Free Online (HSO_Re_2)

## Phạm vi và tra cứu
- Java 17, Maven, MySQL/HikariCP; entrypoint `core.Start`, cổng 19129; source ở `src/main/java/`.
- Độc lập với client mod, khác version: không dùng protocol client làm căn cứ cho server.
- Trước khi sửa, đọc mục liên quan trong [SERVER_REPORT.html](SERVER_REPORT.html) (kiến trúc/opcode/DB/vận hành).
- Tài liệu tính năng theo chủ đề: [docs/index.html](docs/index.html); kiểm tra lại source vì báo cáo có thể cũ.

## Build và chạy (cwd bắt buộc: server/)
- Build: `mvn package`; chạy: `java -server -jar target/HSO_Re_2-1.0-jar-with-dependencies.jar`.
- Dùng JAR `-with-dependencies`; `hso.conf`, `data/`, `log/` là đường dẫn tương đối.
- MySQL theo `hso.conf`, DB mặc định `hsoodtest`, schema/dump ở `hsoodtest.sql`.
- Container: copy `.env.example` sang `.env`, đồng bộ password với `hso.conf`, đặt `mysql-host: db`, `mysql-port: 3306`; `docker compose up -d --build`.
- Giữ shutdown hook lưu dữ liệu (`core/Start.java`); dừng container bằng `docker compose stop`.
- Nếu lỗi Surefire, đọc lỗi để xác định cache/dependency hay test/source; không mặc định bỏ test.

## Chủ ý của chủ server — giữ nguyên
- Mật khẩu lưu dạng thô; không tự chuyển sang hash.
- Anti-teleport đang comment trong `map/MapService.java`; không tự bật lại.

## Ràng buộc khi sửa
- Dùng `PreparedStatement`; giữ kiểm tra character thuộc `conn.list_char` khi login, không khôi phục bypass cũ.
- Admin qua `Session.isAdmin()` đối chiếu `admin_user` (mặc định `admin`), không có cột quyền DB; mọi thao tác admin phải chốt quyền.
- Chat admin: `admin`, `xem`, `ok`, `bosstg`; `Set Xp` là hệ số EXP toàn server.
- Không tin index client: dùng `Util.validIndex`; trade/player store kiểm tra túi trống trước khi trừ tiền/chuyển đồ.
- Item/túi trong `player` là text tuần tự hoá; thay cấu trúc phải có migration.
- SQL/save dùng try-with-resources; giữ `saveExecutor` tái sử dụng, log lỗi xử lý packet/tick/đường tiền.
- Giữ `TCP_NODELAY`; không đặt `SO_TIMEOUT` gây đá người AFK; giữ config MySQL port, pool và `json-simple` nhúng.
- Nhiều thread map và 2 thread/kết nối; `map.players` là `ArrayList`, phải xem đồng bộ trước khi sửa truy cập chung.
- Source/I/O dùng UTF-8; `core/Log.java` có `FileWriter` phụ thuộc locale. Code comment có thể là phần dở dang, không chỉ tìm TODO.

## Dữ liệu và xác minh
- `hsoodtest.sql` chứa thông tin thật, mật khẩu và mã thẻ; không công khai. Làm sạch dữ liệu người chơi trước go-live, giữ template.
- Các bảng cần làm sạch trong bản triển khai: `account/player/clan/naptien/giftcode/wedding`; không tự xoá DB đang dùng.
- Tài liệu cũ ghi chưa có test; kiểm tra `src/test` hiện tại, chạy `mvn package` và kiểm thử phù hợp phần sửa.
- Có thể kiểm thử tích hợp bằng MySQL + server qua Docker Compose; build thành công không chứng minh gameplay đúng.
- Chi tiết bản vá/admin cũ: [bản lưu server](../docs/instruction-history/2026-09-24/server.md), chỉ đọc khi cần, đường dẫn tính từ `server/`.
