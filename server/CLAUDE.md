# Server game "Hiệp Sĩ Free Online" (HSO_Re_2)

Java 17 · Maven · MySQL + HikariCP · main class `core.Start` · cổng 19129.
89 file / ~31k dòng trong `src/main/java`. Không framework, không DI — chuỗi gọi tuần tự.

Tên game đã đổi từ "Hiệp Sĩ Ong Đất Online" → **"Hiệp Sĩ Free Online"** (chuỗi text phía server).
Đây là server **cùng game khác version** với client mod ở `../client/jar/` — độc lập, đừng lấy
`../client/jar/PROTOCOL.md` làm căn cứ.

**Đọc [SERVER_REPORT.html](SERVER_REPORT.html) để có toàn cảnh** (kiến trúc, 55 opcode, lược đồ DB,
danh sách lỗi kèm file:line, admin, vận hành, container).

## Build & chạy

```powershell
mvn package            # -> target/HSO_Re_2-1.0-jar-with-dependencies.jar (bản có -with-dependencies mới chạy được)
java -jar -server target/HSO_Re_2-1.0-jar-with-dependencies.jar
```

- **Thư mục làm việc bắt buộc là `server/`** — `hso.conf`, `data/`, `log/` là đường dẫn tương đối.
- Cần MySQL với DB theo `hso.conf` (mặc định `hsoodtest`, nạp từ `hsoodtest.sql`).
- Nếu `mvn package` lỗi plugin surefire: cache Maven hỏng, không phải lỗi source — xoá
  `~/.m2/repository/org/apache/maven/plugins/maven-surefire-plugin` rồi build lại (hoặc `-DskipTests`).

## Container (deploy EC2)

```bash
cp .env.example .env      # sửa mật khẩu; phải trùng mysql-password trong hso.conf
# hso.conf: đặt mysql-host: db, mysql-port: 3306
docker compose up -d --build
```

`docker compose stop` an toàn (shutdown hook tự lưu dữ liệu). Chi tiết trong `SERVER_REPORT.html` mục 8/9.

## HAI ĐIỀU CỐ Ý GIỮ NGUYÊN — ĐỪNG "VÁ"

Chủ server chủ ý giữ, các phiên sau không được đổi:
- **Mật khẩu lưu dạng thô** (không hash). Login so mật khẩu trong DB.
- **Chống teleport bị comment** ở `map/MapService.java:141-147` (toạ độ client được tin).

## Đã vá / thêm trong phiên 23/08/2026 (đừng làm lại)

Bảo mật P0:
- SQL injection đăng nhập → `PreparedStatement` + bỏ bypass `knightauto_hod_` (`io/Session.java`).
- Chiếm nhân vật qua ID login → kiểm tra id thuộc `conn.list_char` (`client/MessageHandler.java`).
- SQL injection tạo bang → `PreparedStatement` (`client/Clan.java`).
- Menu admin ai cũng tắt được server → chốt quyền trong `Menu_Admin` (`core/MenuController.java`).
- Rò rỉ connection khi save → try-with-resources (`core/SaveData.java`).
- Mất đồ khi trade túi đầy → kiểm tra chỗ trống trước (`core/GameSrc.java` trade).
- Mất tiền+đồ ở gian hàng người chơi túi đầy → kiểm tra `get_bag_able` (`core/GameSrc.java` player_store).
- `process_msg` chỉ bắt IOException → bắt `Throwable` + log (`io/Session.java`).

P1 + tối ưu:
- Kiểm tra biên index từ client → helper `Util.validIndex(idx, len)`, áp cho rebuild/replace/medal/
  wings/kham/pet/buy/skill/pick-drop/wear.
- Log cho catch rỗng đường tiền (TaiXiu `update_coin`) + vòng tick bản đồ (`map/Map.java`).
- SaveData mỗi phút: `new Thread` → `saveExecutor` dùng lại (`core/ServerManager.java`).
- `core/SQL.java`: cổng MySQL cấu hình được (`mysql-port`), `useSSL=false&serverTimezone=UTC`,
  bật leak detection + prepared-statement cache.
- `io/Session.java`: `setTcpNoDelay(true)` (KHÔNG đặt SO_TIMEOUT — sẽ đá nhầm người AFK).
- Shutdown hook lưu dữ liệu trước khi thoát (`core/Start.java`) → `docker stop`/SIGTERM an toàn.
- `pom.xml`: HikariCP 5.1.0, `com.mysql:mysql-connector-j` 8.4.0, commons-lang3 3.14.0.
  **Giữ json-simple** (nhúng 10 file). Config: dung thứ boolean sai + cảnh báo (`debug: fale`).

## Công cụ admin (đã bật + bảo mật)

Admin = tài khoản có tên khớp config `admin_user` (mặc định `admin`), kiểm bằng `Session.isAdmin()`.
Không có cột quyền trong DB. Tạo admin: `INSERT INTO account (user,pass,char,status,lock,...) VALUES ('admin','<pass>','[]',0,0,...)`.

Trong game — chat: `admin` (mở menu), `xem`, `ok`, `bosstg`. Menu admin (chat "admin"):
Bảo trì (tắt server), Cộng vàng 1 tỷ, Cộng ngọc 1 triệu, Update data, Lấy item, Up level, Set Xp
(Set Xp = đặt hệ số exp toàn server). Tất cả đã chốt quyền admin.

## Bẫy khi sửa code

- **Đừng tin số client gửi** — dùng `Util.validIndex` trước khi index mảng.
- **Không có TODO/FIXME** — phần dở dang đánh dấu bằng code bị comment.
- **Túi đồ lưu dạng text tuần tự hoá** trong `player` — đổi cấu trúc item phải viết migration.
- **760 thread bản đồ** + 2 thread/kết nối (blocking I/O).
- `map.players` là `ArrayList` không thread-safe, khoá không đồng đều.
- Ghi file phải khai charset (`core/Log.java` vẫn dùng `FileWriter` không charset → cần locale UTF-8).

## Cảnh báo `hsoodtest.sql`

Chứa **dữ liệu thật người chơi cũ** (mật khẩu thô, email, SĐT, IP) + **mã thẻ nạp thật** (`naptien`).
Không đưa lên nơi công khai; làm sạch trước go-live (giữ bảng template, xoá `account/player/clan/naptien/giftcode/wedding`).

## Kiểm thử

Chưa có test (`src/test` rỗng). Có thể dựng MySQL + server bằng `docker compose` để test thật.
